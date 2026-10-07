# -*- coding: utf-8 -*-
"""Dispara Chrome headless contra dist-demo y recorta cada captura con Pillow.

Lo invoca scripts/capturar-pantallas.sh, que es quien construye la demo, inyecta
el arnes (escenarios.js) y levanta el servidor local. No ejecutar suelto.

Tres cosas que no son evidentes y explican los numeros de la tabla:

- La ventana se pide MAS ALTA que la pagina. Si la pagina queda con scroll, el dia
  elegido del calendario se pinta sin su relleno, y la captura sale enganyosa.
- Debajo de ~500 px Chrome no estrecha la ventana (pide 390, da 504) y luego
  recorta el PNG, simulando desbordes falsos: el ancho de movil se consigue
  metiendo la pagina en un iframe (modo MOVIL, ver movil.html).
- El tema claro hay que forzarlo: headless responde "dark" a prefers-color-scheme.
"""
import os
import re
import shutil
import subprocess
import sys
import tempfile

from PIL import Image

AQUI = os.path.dirname(os.path.abspath(__file__))
RAIZ = os.path.dirname(os.path.dirname(AQUI))
DOCS = os.path.join(RAIZ, "docs")
GALERIA = os.path.join(DOCS, "capturas")

CHROME = os.environ.get("CHROME_BIN")
BASE = "http://127.0.0.1:" + os.environ.get("PUERTO", "8731")

# nombre | pagina | query | ancho | alto | encuadre | margen | destino
#
# encuadre: selector CSS a recortar, None para dejar el viewport entero,
#           "MOVIL" para el iframe de 390 px, o "SUP:<y>" para cortar solo por
#           arriba (asi la franja de la demo, que va fija abajo, sigue en cuadro).
SHOTS = [
    ("01-portada", "index", "cap=portada",
     1280, 1000, ".hero-section", 0, "capturas/01-portada.jpg"),
    ("02-servicios", "servicios", "cap=servicios",
     1280, 2600, "main", 0, "capturas/02-servicios.png"),
    ("03-reserva-centros", "reservar", "cap=reserva-centros",
     1280, 2200, ".reserva-grid > .card", 20, "capturas/03-reserva-centros.png"),
    ("04-reserva-huecos", "reservar", "cap=reserva-huecos",
     1280, 2400, ".reserva-grid", 20, "capturas/04-reserva-huecos.png"),
    ("05-reserva-formulario", "reservar", "cap=reserva-formulario",
     1280, 3000, ".sticky-panel", 20, "capturas/05-reserva-formulario.png"),
    ("06-mis-citas", "reservar", "cap=mis-citas&sesion=paciente",
     1280, 3000, ".appointments-panel", 20, "capturas/06-mis-citas.png"),
    ("07-telegram", "reservar", "cap=telegram&sesion=paciente&tg=1",
     1280, 1000, None, 0, "capturas/07-telegram.png"),
    ("08-cuadro-mando", "estadisticas", "cap=cuadro-mando&sesion=medico",
     1280, 2600, "#dashContent", 20, "capturas/08-cuadro-mando.png"),
    ("09-movil", "movil",
     "src=__cap-reservar.html%3Fcap%3Dreserva-huecos%26encuadre%3D.reserva-grid%20%3E%20.card&alto=2700",
     700, 2750, "MOVIL", 0, "capturas/09-movil.png"),
    ("10-modo-oscuro", "reservar", "cap=reserva-huecos&tema=dark",
     1280, 2400, ".reserva-grid", 20, "capturas/10-modo-oscuro.png"),
    ("11-catalan", "index", "cap=portada&lang=ca",
     1280, 1000, ".hero-section", 0, "capturas/11-catalan.jpg"),
    ("12-sin-conexion", "offline", "cap=offline",
     1280, 1600, ".offline-card", 28, "capturas/12-sin-conexion.png"),
    # La del README que ensenya la demo: con su franja amarilla y sin recortarla.
    ("demo-reserva", "reservar", "cap=reserva-huecos&banner=1",
     1280, 2120, "SUP:730", 0, "demo-reserva.png"),
]


def chrome(url, ancho, alto, destino_png, tema_claro):
    perfil = tempfile.mkdtemp(prefix="capturas-chrome-")
    orden = [
        CHROME, "--headless=new", "--disable-gpu", "--no-sandbox", "--hide-scrollbars",
        "--force-prefers-reduced-motion",
        # El presupuesto de tiempo virtual corre mucho mas rapido que el reloj: si
        # se queda corto, Chrome dispara la captura (o se va sin escribir nada)
        # mientras la pagina todavia pide datos. El cuadro de mando, que ademas
        # tiene que pintar cinco graficos, es el que lo nota.
        "--virtual-time-budget=30000",
        "--run-all-compositor-stages-before-draw",
        "--user-data-dir=" + perfil,
        "--window-size={0},{1}".format(ancho, alto),
        "--screenshot=" + destino_png,
        "--enable-logging=stderr", "--v=0",
    ]
    if tema_claro:
        orden.append("--blink-settings=preferredColorScheme=1")
    orden.append(url)
    try:
        p = subprocess.run(orden, capture_output=True, text=True, errors="replace", timeout=180)
        return (p.stderr or "") + (p.stdout or "")
    finally:
        shutil.rmtree(perfil, ignore_errors=True)


def recortar_movil(img, log):
    """El iframe ocupa los 390 px de la izquierda; abajo se corta por el encuadre
    que haya medido la propia pagina, y si no, por donde acaba el contenido."""
    alto = img.height
    rect = re.search(r"CAP-RECT (-?\d+) (-?\d+) (\d+) (\d+)", log)
    if rect:
        alto = min(alto, int(rect.group(2)) + int(rect.group(4)) + 16)
    img = img.crop((0, 0, 390, alto))
    fondo = img.getpixel((5, img.height - 5))
    ultima = img.height - 1
    for y in range(img.height - 1, -1, -1):
        fila = [img.getpixel((x, y)) for x in range(0, 390, 13)]
        if any(sum(abs(a - b) for a, b in zip(p, fondo)) > 12 for p in fila):
            ultima = y
            break
    return img.crop((0, 0, 390, min(img.height, ultima + 24)))


def capturar(nombre, pagina, query, ancho, alto, encuadre, margen, destino):
    bruta = os.path.join(TMP, nombre + ".png")
    url = "{0}/__cap-{1}.html?{2}".format(BASE, pagina, query)
    if encuadre and not encuadre.startswith(("MOVIL", "SUP:")):
        url += "&encuadre=" + encuadre.replace("#", "%23").replace(" ", "%20")

    log = chrome(url, ancho, alto, bruta, "tema=dark" not in query)
    if not os.path.exists(bruta):
        # Chrome falla de vez en cuando sin escribir nada ni decir por que.
        log = chrome(url, ancho, alto, bruta, "tema=dark" not in query)
    estado = "OK" if "CAP-OK" in log else ("FALLO" if "CAP-FAIL" in log else "¿?")
    fallo = re.search(r"CAP-FAIL ([^\n\"]+)", log)
    if not os.path.exists(bruta):
        print("  {0}: sin PNG ({1}) {2}".format(nombre, estado, fallo.group(1).strip() if fallo else ""))
        return False

    img = Image.open(bruta).convert("RGB")
    if encuadre == "MOVIL":
        img = recortar_movil(img, log)
    elif encuadre and encuadre.startswith("SUP:"):
        img = img.crop((0, int(encuadre[4:]), img.width, img.height))
    elif encuadre:
        rect = re.search(r"CAP-RECT (-?\d+) (-?\d+) (\d+) (\d+)", log)
        if rect:
            x, y, w, h = (int(v) for v in rect.groups())
            caja = (max(0, x - margen), max(0, y - margen),
                    min(img.width, x + w + margen), min(img.height, y + h + margen))
            if caja[2] - caja[0] > 50 and caja[3] - caja[1] > 50:
                img = img.crop(caja)

    ruta = os.path.join(DOCS, destino)
    os.makedirs(os.path.dirname(ruta), exist_ok=True)
    if ruta.lower().endswith(".jpg"):
        # Las dos de la portada son fotografia: en PNG pesan medio mega.
        img.save(ruta, quality=86, optimize=True, progressive=True)
    else:
        img.save(ruta, optimize=True)
    print("  {0}: {1} {2}x{3} {4} KB".format(
        nombre, estado, img.width, img.height, os.path.getsize(ruta) // 1024))
    return estado == "OK"


if not CHROME:
    sys.exit("✗ falta CHROME_BIN: lanza scripts/capturar-pantallas.sh, no este guion suelto")

TMP = tempfile.mkdtemp(prefix="capturas-brutas-")
pedidas = sys.argv[1:]
conocidas = [s[0] for s in SHOTS]
for p in pedidas:
    if p not in conocidas:
        sys.exit("✗ no existe la captura «{0}». Hay: {1}".format(p, ", ".join(conocidas)))

os.makedirs(GALERIA, exist_ok=True)
try:
    fallos = [s[0] for s in SHOTS
              if (not pedidas or s[0] in pedidas) and not capturar(*s)]
finally:
    shutil.rmtree(TMP, ignore_errors=True)

if fallos:
    sys.exit("✗ no salieron bien: " + ", ".join(fallos))
print("✓ capturas al día en docs/capturas/")
