#!/usr/bin/env python3
"""
Recorta Font Awesome a los iconos que usa la web.

La hoja completa (all.min.css) pesa 100 KB y bloquea el primer pintado de todas
las páginas, y la fuente sólida otros 150 KB, para unos sesenta iconos. Este
script busca qué clases fa-* aparecen en el frontend y genera:

  frontend/vendor/fontawesome/css/iconos.min.css      (solo esas reglas)
  frontend/vendor/fontawesome/webfonts/fa-solid-900-recorte.woff2
  frontend/vendor/fontawesome/webfonts/fa-brands-400-recorte.woff2

Los originales se quedan en vendor/ como fuente para volver a recortar.

Cuándo ejecutarlo: al usar un icono nuevo. El CI lo recuerda: si una página usa
un fa-* que no está en iconos.min.css, scripts/iconos-comprobar.mjs falla.

Uso:  pip install fonttools brotli
      python3 scripts/iconos-recortar.py
"""
import re
import sys
from pathlib import Path

from fontTools import subset

RAIZ = Path(__file__).resolve().parent.parent
# Clases de familia, que acompañan a cualquier icono: siempre hacen falta.
ESTILOS = {"solid", "regular", "brands", "classic", "sharp"}
FA = RAIZ / "frontend/vendor/fontawesome"
ORIGEN_CSS = FA / "css/all.min.css"
DESTINO_CSS = FA / "css/iconos.min.css"

# Dónde se buscan los iconos: las páginas, los scripts propios, las plantillas
# y la API simulada de la demo (pinta algún aviso con icono).
FUENTES = (
    list((RAIZ / "frontend").glob("*.html"))
    + [p for p in (RAIZ / "frontend/js").rglob("*.js") if "vendor" not in p.parts]
    + list((RAIZ / "plantillas").glob("*.html"))
    + [RAIZ / "demo/demo-api.js"]
)


def iconos_usados():
    usados = set()
    for f in FUENTES:
        usados |= set(re.findall(r"\bfa-([a-z0-9-]+)", f.read_text(encoding="utf-8")))
    return usados


def reglas(css):
    """Divide el CSS minificado en reglas de primer nivel (las @ incluidas)."""
    out, nivel, inicio = [], 0, 0
    for i, c in enumerate(css):
        if c == "{":
            nivel += 1
        elif c == "}":
            nivel -= 1
            if nivel == 0:
                out.append(css[inicio:i + 1].strip())
                inicio = i + 1
    return [r for r in out if r]


def main():
    css = re.sub(r"/\*.*?\*/", "", ORIGEN_CSS.read_text(encoding="utf-8"), flags=re.S)
    usados = iconos_usados()

    salida, solidos, marcas = [], set(), set()
    icono = re.compile(r"^\.fa-([a-z0-9-]+):before$")
    for regla in reglas(css):
        selectores, cuerpo = regla.split("{", 1)
        partes = [s.strip() for s in selectores.split(",")]

        if all(icono.match(s) for s in partes):
            quedan = [s for s in partes if icono.match(s).group(1) in usados]
            if not quedan:
                continue
            codigo = re.search(r'content:"\\([0-9a-f]+)"', cuerpo).group(1)
            salida.append(",".join(quedan) + "{" + cuerpo)
            # Sólida o de marca: lo decide la clase con que se usa, pero el código es
            # único, así que basta con meterlo en las dos fuentes si existe en ellas.
            solidos.add(int(codigo, 16))
            marcas.add(int(codigo, 16))
            continue

        if regla.startswith("@font-face"):
            # Solo la sólida (900) y la de marcas de la versión 6. La regular, la de
            # compatibilidad con la v4 y los alias de la v5 no se usan. Se deja
            # font-display:block, como en el original: con swap, durante un instante
            # saldría un cuadrado en lugar de cada icono.
            if '"Font Awesome 6 Free"' in regla and "font-weight:900" in regla:
                salida.append(regla.replace("fa-solid-900.woff2", "fa-solid-900-recorte.woff2"))
            elif '"Font Awesome 6 Brands"' in regla:
                salida.append(regla.replace("fa-brands-400.woff2", "fa-brands-400-recorte.woff2"))
            continue

        if regla.startswith("@keyframes"):
            if re.match(r"@keyframes fa-([a-z0-9-]+)", regla).group(1) in usados:
                salida.append(regla)
            continue

        if regla.startswith("@"):
            salida.append(regla)  # @media (prefers-reduced-motion) y similares
            continue

        # El resto es la base: .fa/.fas/.fab, tamaños, animaciones, .sr-only… Se
        # queda cada selector cuyas clases fa-* se usen (o que no tenga ninguna).
        def sirve(selector):
            clases = re.findall(r"\.fa-([a-z0-9-]+)", selector)
            return all(c in usados or c in ESTILOS for c in clases)

        quedan = [s for s in partes if sirve(s)]
        if quedan:
            salida.append(",".join(quedan) + "{" + cuerpo)

    cabecera = (
        "/*! Font Awesome Free 6.4.0 by @fontawesome - https://fontawesome.com\n"
        " * License - https://fontawesome.com/license/free (Icons: CC BY 4.0, Fonts: SIL OFL 1.1, Code: MIT License)\n"
        " * Copyright 2023 Fonticons, Inc.\n"
        " * Recorte generado por scripts/iconos-recortar.py: no editar a mano. */\n"
    )
    DESTINO_CSS.write_text(cabecera + "".join(salida) + "\n", encoding="utf-8")

    for origen, codigos in (("fa-solid-900", solidos), ("fa-brands-400", marcas)):
        opciones = subset.Options()
        opciones.flavor = "woff2"
        opciones.layout_features = ["*"]
        fuente = subset.load_font(str(FA / f"webfonts/{origen}.woff2"), opciones)
        recortador = subset.Subsetter(opciones)
        disponibles = set(fuente.getBestCmap())
        recortador.populate(unicodes=sorted(codigos & disponibles))
        recortador.subset(fuente)
        subset.save_font(fuente, str(FA / f"webfonts/{origen}-recorte.woff2"), opciones)

    print(f"✓ {DESTINO_CSS.relative_to(RAIZ)}: {DESTINO_CSS.stat().st_size // 1024} KB")
    for nombre in ("fa-solid-900-recorte.woff2", "fa-brands-400-recorte.woff2"):
        p = FA / "webfonts" / nombre
        print(f"✓ {p.relative_to(RAIZ)}: {p.stat().st_size // 1024} KB")


if __name__ == "__main__":
    sys.exit(main())
