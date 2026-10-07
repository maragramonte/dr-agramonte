# -*- coding: utf-8 -*-
"""Cliente minimo del protocolo de DevTools, solo con la libreria estandar.

Existe por una razon concreta: la emulacion de dispositivo. Por linea de ordenes
Chrome no baja de unos 500 px de ancho de ventana (se le piden 390 y da 504) y
luego recorta el PNG, lo que simula desbordes que no existen. La unica forma de
tener un viewport de movil de verdad es Emulation.setDeviceMetricsOverride, que
va por el protocolo. De paso, Page.captureScreenshot recorta por el elemento y
captura mas alla del viewport, asi que no hace falta ni ventana gigante ni
recorte posterior.

No cubre el protocolo entero: abre una pestanya, emula el dispositivo, navega,
espera a un mensaje de consola y hace la captura. Nada mas.
"""
import base64
import json
import os
import secrets
import shutil
import socket
import struct
import subprocess
import tempfile
import time
import urllib.request


class SocketWeb:
    """Lo imprescindible de RFC 6455 para hablar con Chrome en local."""

    def __init__(self, url, espera=60):
        resto = url.split("://", 1)[1]
        anfitrion, _, camino = resto.partition("/")
        maquina, _, puerto = anfitrion.partition(":")
        self.sock = socket.create_connection((maquina, int(puerto)), timeout=espera)
        self.sock.settimeout(espera)
        clave = base64.b64encode(secrets.token_bytes(16)).decode()
        self.sock.sendall((
            "GET /{0} HTTP/1.1\r\n"
            "Host: {1}\r\n"
            "Upgrade: websocket\r\n"
            "Connection: Upgrade\r\n"
            "Sec-WebSocket-Key: {2}\r\n"
            "Sec-WebSocket-Version: 13\r\n\r\n"
        ).format(camino, anfitrion, clave).encode())
        datos = b""
        while b"\r\n\r\n" not in datos:
            trozo = self.sock.recv(4096)
            if not trozo:
                raise ConnectionError("Chrome cerro la conexion al negociar")
            datos += trozo
        cabecera, _, self.pendiente = datos.partition(b"\r\n\r\n")
        if b" 101 " not in cabecera.split(b"\r\n")[0]:
            raise ConnectionError("Chrome no acepto el websocket: " + cabecera[:80].decode("latin1"))

    def enviar(self, mensaje):
        carga = json.dumps(mensaje).encode()
        marco = bytearray([0x81])
        n = len(carga)
        if n < 126:
            marco.append(0x80 | n)
        elif n < 65536:
            marco.append(0x80 | 126)
            marco += struct.pack(">H", n)
        else:
            marco.append(0x80 | 127)
            marco += struct.pack(">Q", n)
        mascara = secrets.token_bytes(4)
        marco += mascara
        marco += bytes(b ^ mascara[i % 4] for i, b in enumerate(carga))
        self.sock.sendall(bytes(marco))

    def _leer(self, n):
        while len(self.pendiente) < n:
            trozo = self.sock.recv(1 << 20)
            if not trozo:
                raise ConnectionError("Chrome cerro la conexion")
            self.pendiente += trozo
        salida, self.pendiente = self.pendiente[:n], self.pendiente[n:]
        return salida

    def recibir(self):
        """Un mensaje completo, juntando los marcos de continuacion."""
        partes = []
        while True:
            cab = self._leer(2)
            fin, codigo = cab[0] & 0x80, cab[0] & 0x0F
            n = cab[1] & 0x7F
            if n == 126:
                n = struct.unpack(">H", self._leer(2))[0]
            elif n == 127:
                n = struct.unpack(">Q", self._leer(8))[0]
            carga = self._leer(n)  # el servidor no enmascara
            if codigo == 0x8:
                raise ConnectionError("Chrome cerro la pestanya")
            if codigo in (0x9, 0xA):  # ping / pong: a lo nuestro
                continue
            partes.append(carga)
            if fin:
                return json.loads(b"".join(partes).decode("utf-8"))

    def cerrar(self):
        try:
            self.sock.close()
        except OSError:
            pass


class Navegador:
    """Chrome headless con el puerto de depuracion abierto, para un solo uso."""

    def __init__(self, chrome, extras=()):
        self.perfil = tempfile.mkdtemp(prefix="capturas-cdp-")
        orden = [
            chrome, "--headless=new", "--disable-gpu", "--no-sandbox", "--hide-scrollbars",
            "--force-prefers-reduced-motion", "--remote-debugging-port=0",
            "--user-data-dir=" + self.perfil, "about:blank",
        ]
        orden[-1:-1] = list(extras)
        self.proceso = subprocess.Popen(
            orden, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        self.ws = SocketWeb(self._url_pestanya())
        self.siguiente = 0
        self.consola = []

    def _url_pestanya(self, espera=30):
        archivo = os.path.join(self.perfil, "DevToolsActivePort")
        limite = time.time() + espera
        puerto = None
        while time.time() < limite:
            if os.path.exists(archivo):
                try:
                    with open(archivo, encoding="utf-8") as f:
                        puerto = f.read().split("\n")[0].strip()
                    if puerto:
                        break
                except OSError:
                    pass
            time.sleep(0.1)
        if not puerto:
            raise RuntimeError("Chrome no publico su puerto de depuracion")
        while time.time() < limite:
            try:
                with urllib.request.urlopen("http://127.0.0.1:{0}/json/list".format(puerto), timeout=5) as r:
                    objetivos = json.loads(r.read().decode("utf-8"))
                for o in objetivos:
                    if o.get("type") == "page" and o.get("webSocketDebuggerUrl"):
                        return o["webSocketDebuggerUrl"]
            except OSError:
                pass
            time.sleep(0.1)
        raise RuntimeError("Chrome no abrio ninguna pestanya")

    def mandar(self, metodo, **parametros):
        self.siguiente += 1
        ident = self.siguiente
        self.ws.enviar({"id": ident, "method": metodo, "params": parametros})
        while True:
            msg = self.ws.recibir()
            if msg.get("id") == ident:
                if "error" in msg:
                    raise RuntimeError("{0}: {1}".format(metodo, msg["error"].get("message")))
                return msg.get("result", {})
            self._anotar(msg)

    def _anotar(self, msg):
        """Guarda lo que escriba la pagina en consola, que es como el arnes avisa."""
        if msg.get("method") == "Runtime.consoleAPICalled":
            for arg in msg["params"].get("args", []):
                if "value" in arg:
                    self.consola.append(str(arg["value"]))

    def emular(self, ancho, alto, densidad=1, movil=False):
        """Fija el viewport. Con movil=True es emulacion de dispositivo de las de
        DevTools; en escritorio sirve para tener una medida exacta y estable."""
        self.mandar("Emulation.setDeviceMetricsOverride",
                    width=ancho, height=alto, deviceScaleFactor=densidad, mobile=movil)

    def abrir(self, url, marca="CAP-OK", espera=45):
        """Navega y espera a que el arnes diga que la pantalla esta montada."""
        self.consola = []
        self.mandar("Runtime.enable")
        self.mandar("Page.enable")
        self.mandar("Page.navigate", url=url)
        limite = time.time() + espera
        while time.time() < limite:
            self.ws.sock.settimeout(max(1, limite - time.time()))
            try:
                self._anotar(self.ws.recibir())
            except socket.timeout:
                break
            if any(l.startswith(marca) or l.startswith("CAP-FAIL") for l in self.consola):
                return self.consola
        return self.consola

    def alto_pagina(self):
        """Altura total del documento, en pixeles CSS."""
        m = self.mandar("Page.getLayoutMetrics")
        tamanyo = m.get("cssContentSize") or m.get("contentSize") or {}
        return int(tamanyo.get("height", 0))

    def reposar(self, segundos=0.5):
        time.sleep(segundos)

    def capturar(self, destino, recorte=None, densidad=1):
        """densidad es la escala del recorte, que se MULTIPLICA por la del
        dispositivo emulado: con el dispositivo ya a 2x, aqui va 1."""
        parametros = {"format": "png", "captureBeyondViewport": True}
        if recorte:
            x, y, ancho, alto = recorte
            parametros["clip"] = {"x": x, "y": y, "width": ancho, "height": alto, "scale": densidad}
        datos = self.mandar("Page.captureScreenshot", **parametros)
        with open(destino, "wb") as f:
            f.write(base64.b64decode(datos["data"]))

    def cerrar(self):
        try:
            self.ws.cerrar()
        finally:
            self.proceso.terminate()
            try:
                self.proceso.wait(timeout=15)
            except subprocess.TimeoutExpired:
                self.proceso.kill()
            shutil.rmtree(self.perfil, ignore_errors=True)
