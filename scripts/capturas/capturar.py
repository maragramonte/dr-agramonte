# -*- coding: utf-8 -*-
"""Saca las capturas de docs/capturas/ con Chrome por el protocolo de DevTools.

Lo invoca scripts/capturar-pantallas.sh, que es quien construye la demo, inyecta
el arnes (escenarios.js) y levanta el servidor local. No ejecutar suelto.

Tres cosas que no son evidentes:

- Va por el protocolo (devtools.py) y no por "chrome --screenshot". Por linea de
  ordenes no hay forma de pedir un viewport de movil -Chrome no baja de unos
  500 px de ancho de ventana, da 504 y luego recorta el PNG, simulando desbordes
  que no existen- ni de esperar a que la pantalla este pintada: el tiempo virtual
  dispara la captura cuando le toca, y de ahi salian capturas a medias.
- Antes de disparar, el viewport se estira a la altura de la pagina. Con la
  pagina mas alta que el viewport, el dia elegido del calendario se pinta sin su
  relleno. Es lo mismo que hace DevTools al capturar a tamanyo completo.
- El tema claro hay que forzarlo: headless responde "dark" a prefers-color-scheme.

El recorte lo hace el propio Chrome por el rectangulo del elemento (clip), asi
que Pillow solo interviene para el JPEG de las fotos y para reducir la del movil.
"""
import os
import re
import sys
import tempfile

from PIL import Image

from devtools import Navegador

AQUI = os.path.dirname(os.path.abspath(__file__))
RAIZ = os.path.dirname(os.path.dirname(AQUI))
DOCS = os.path.join(RAIZ, "docs")
GALERIA = os.path.join(DOCS, "capturas")

CHROME = os.environ.get("CHROME_BIN")
BASE = "http://127.0.0.1:" + os.environ.get("PUERTO", "8731")
# Relleno del dia elegido en el calendario (--teal-fill) y densidad del movil.
TEAL = (15, 118, 110)
DENSIDAD_MOVIL = 2

# nombre | pagina | query | ancho | alto | encuadre | margen | destino
#
# ancho y alto son el viewport. encuadre: selector CSS a recortar, None para
# dejar el viewport entero, "SUP:<y>" para cortar solo por arriba (asi la franja
# de la demo, que va fija abajo, sigue en cuadro), o "MOVIL:<selector>" para
# emular un dispositivo en vez de un escritorio.
SHOTS = [
    ("01-portada", "index", "cap=portada",
     1280, 1000, ".hero-section", 0, "capturas/01-portada.jpg"),
    ("02-servicios", "servicios", "cap=servicios",
     1280, 1000, "main", 0, "capturas/02-servicios.png"),
    ("03-reserva-centros", "reservar", "cap=reserva-centros",
     1280, 1000, ".reserva-grid > .card", 20, "capturas/03-reserva-centros.png"),
    ("04-reserva-huecos", "reservar", "cap=reserva-huecos",
     1280, 1000, ".reserva-grid", 20, "capturas/04-reserva-huecos.png"),
    ("05-reserva-formulario", "reservar", "cap=reserva-formulario",
     1280, 1000, ".sticky-panel", 20, "capturas/05-reserva-formulario.png"),
    ("06-mis-citas", "reservar", "cap=mis-citas&sesion=paciente",
     1280, 1000, ".appointments-panel", 20, "capturas/06-mis-citas.png"),
    ("07-telegram", "reservar", "cap=telegram&sesion=paciente&tg=1",
     1280, 1000, None, 0, "capturas/07-telegram.png"),
    ("08-cuadro-mando", "estadisticas", "cap=cuadro-mando&sesion=medico",
     1280, 1000, "#dashContent", 20, "capturas/08-cuadro-mando.png"),
    ("09-movil", "reservar", "cap=reserva-huecos",
     390, 844, "MOVIL:.reserva-grid > .card", 16, "capturas/09-movil.png"),
    ("10-modo-oscuro", "reservar", "cap=reserva-huecos&tema=dark",
     1280, 1000, ".reserva-grid", 20, "capturas/10-modo-oscuro.png"),
    ("11-catalan", "index", "cap=portada&lang=ca",
     1280, 1000, ".hero-section", 0, "capturas/11-catalan.jpg"),
    ("12-sin-conexion", "offline", "cap=offline",
     1280, 1000, ".offline-card", 28, "capturas/12-sin-conexion.png"),
    # La del README que ensenya la demo: con su franja amarilla y sin recortarla.
    ("demo-reserva", "reservar", "cap=reserva-huecos&banner=1",
     1280, 1000, "SUP:730", 0, "demo-reserva.png"),
]


def tomar(url, ancho, alto, encuadre, margen, bruta):
    """Abre la pagina, deja que el arnes monte la pantalla y captura. Devuelve
    lo que la pagina escribio en consola y la densidad del PNG resultante."""
    movil = bool(encuadre and encuadre.startswith("MOVIL:"))
    densidad = DENSIDAD_MOVIL if movil else 1
    selector = encuadre[6:] if movil else (None if not encuadre or encuadre.startswith("SUP:") else encuadre)
    if selector:
        url += "&encuadre=" + selector.replace("#", "%23").replace(" ", "%20")

    extras = [] if "tema=dark" in url else ["--blink-settings=preferredColorScheme=1"]
    nav = Navegador(CHROME, extras=extras)
    try:
        nav.emular(ancho, alto, densidad, movil)
        log = "\n".join(nav.abrir(url))
        rect = re.search(r"CAP-RECT (-?\d+) (-?\d+) (\d+) (\d+)", log)

        # El viewport se estira a toda la pagina solo si hay un dia elegido en
        # el calendario, que es lo unico que se pinta mal con scroll (ver arriba).
        # Estirarlo siempre no es gratis: Chart.js se re-dibuja al cambiar el
        # tamanyo y el cuadro de mando salia con los cinco graficos en blanco.
        completo = max(alto, nav.alto_pagina())
        if "CAP-DIA" in log:
            nav.emular(ancho, min(completo, 16000), densidad, movil)
            nav.reposar(0.8)

        recorte = None
        if movil and rect:
            # Una pantalla de telefono se mira entera, desde la cabecera.
            recorte = (0, 0, ancho, int(rect.group(2)) + int(rect.group(4)) + margen)
        elif rect:
            x, y, w, h = (int(v) for v in rect.groups())
            recorte = (max(0, x - margen), max(0, y - margen), w + 2 * margen, h + 2 * margen)
        elif encuadre and encuadre.startswith("SUP:"):
            desde = int(encuadre[4:])
            recorte = (0, desde, ancho, completo - desde)
        elif not encuadre:
            recorte = (0, 0, ancho, alto)
        nav.capturar(bruta, recorte=recorte)
        return log, densidad, recorte or (0, 0, ancho, completo)
    finally:
        nav.cerrar()


# Disparos por captura antes de darla por mala (ver el comentario de capturar()).
INTENTOS_DIA = 4


def dia_resaltado(bruta, log, densidad, recorte):
    """Comprueba que el dia elegido salio con su relleno.

    El arnes publica su rectangulo con CAP-DIA en coordenadas de la pagina; aqui
    se pasa a las del PNG restando el recorte y multiplicando por la densidad, y
    se mira el color del centro. Las capturas sin calendario no publican nada y
    se dan por buenas.
    """
    dia = re.search(r"CAP-DIA (-?\d+) (-?\d+) (\d+) (\d+)", log)
    if not dia:
        return True
    x, y, w, h = (int(v) for v in dia.groups())
    cx = (x + w // 2 - recorte[0]) * densidad
    cy = (y + h // 2 - recorte[1]) * densidad
    img = Image.open(bruta).convert("RGB")
    if not (0 <= cx < img.width and 0 <= cy < img.height):
        return True  # el dia no entra en el encuadre: nada que comprobar
    centro = img.getpixel((cx, cy))
    vale = sum(abs(a - b) for a, b in zip(centro, TEAL)) <= 24
    if not vale and os.environ.get("CAP_DEBUG"):
        print("    [debug] dia en {0}, color {1}, PNG {2}".format((cx, cy), centro, img.size))
    return vale


def capturar(nombre, pagina, query, ancho, alto, encuadre, margen, destino):
    bruta = os.path.join(TMP, nombre + ".png")
    url = "{0}/__cap-{1}.html?{2}".format(BASE, pagina, query)

    # El relleno del dia se pierde por una carrera de pintado de headless. Medido
    # sobre esta misma captura: fallaba la mitad de las veces antes de que
    # escenarios.js esperase a un par de marcos, y una de cada cinco despues. Con
    # un solo reintento una tanda completa seguia saliendo mal muy a menudo.
    for _ in range(INTENTOS_DIA):
        log, densidad, recorte = tomar(url, ancho, alto, encuadre, margen, bruta)
        if dia_resaltado(bruta, log, densidad, recorte):
            break
    else:
        print("  {0}: el dia elegido sale sin resaltar en {1} intentos, no la publico".format(
            nombre, INTENTOS_DIA))
        return False

    estado = "OK" if "CAP-OK" in log else ("FALLO" if "CAP-FAIL" in log else "¿?")
    fallo = re.search(r"CAP-FAIL ([^\n\"]+)", log)

    img = Image.open(bruta).convert("RGB")
    if densidad > 1:
        # Viene a 2x del dispositivo emulado: se reduce para que pese como las
        # demas, y de paso el texto queda mas limpio que renderizado a 1x.
        img = img.resize((img.width // densidad, img.height // densidad), Image.LANCZOS)

    ruta = os.path.join(DOCS, destino)
    os.makedirs(os.path.dirname(ruta), exist_ok=True)
    if ruta.lower().endswith(".jpg"):
        # Las dos de la portada son fotografia: en PNG pesan medio mega.
        img.save(ruta, quality=86, optimize=True, progressive=True)
    else:
        img.save(ruta, optimize=True)
    print("  {0}: {1} {2}x{3} {4} KB {5}".format(
        nombre, estado, img.width, img.height, os.path.getsize(ruta) // 1024,
        fallo.group(1).strip() if fallo else ""))
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
fallos = [s[0] for s in SHOTS if (not pedidas or s[0] in pedidas) and not capturar(*s)]

if fallos:
    sys.exit("✗ no salieron bien: " + ", ".join(fallos))
print("✓ capturas al día en docs/capturas/")
