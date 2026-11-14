#!/usr/bin/env bash
# Prueba de scripts/plantillas.mjs sobre una copia del frontend:
#   1. con el repositorio tal cual, --comprobar pasa;
#   2. si alguien edita el pie a mano en una página, --comprobar falla y dice cuál;
#   3. al regenerar, esa página vuelve a coincidir y las demás no se tocan;
#   4. un cambio en plantillas/ llega a todas las páginas;
#   5. una página sin marcas es un error, no se salta en silencio.
#
# Uso: bash scripts/tests/plantillas.test.sh   (necesita node)
set -euo pipefail

RAIZ=$(cd "$(dirname "$0")/../.." && pwd)
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

fallo() { echo "FALLO: $*" >&2; exit 1; }
ejecutar() { PLANTILLAS_RAIZ="$TMP" node "$RAIZ/scripts/plantillas.mjs" "$@"; }

cp -r "$RAIZ/plantillas" "$TMP/plantillas"
mkdir "$TMP/frontend"
cp "$RAIZ"/frontend/*.html "$TMP/frontend/"
PAGINAS=$(ls "$TMP"/frontend/*.html | wc -l)

# 1
ejecutar --comprobar >/dev/null || fallo "el repositorio no está al día con plantillas/"
echo "✓ las $PAGINAS páginas coinciden con las plantillas"

# 2
sed -i 's/Enlaces<\/h3>/Enlaces editados a mano<\/h3>/' "$TMP/frontend/contacto.html"
if salida=$(ejecutar --comprobar 2>&1); then fallo "--comprobar no detecta un pie editado a mano"; fi
grep -q "contacto.html" <<<"$salida" || fallo "--comprobar no dice qué página está desfasada"
echo "✓ --comprobar detecta una página editada a mano"

# 3
cp "$TMP/frontend/index.html" "$TMP/index.antes"
ejecutar >/dev/null
grep -q "editados a mano" "$TMP/frontend/contacto.html" && fallo "la regeneración no corrige contacto.html"
cmp -s "$TMP/index.antes" "$TMP/frontend/index.html" || fallo "la regeneración ha cambiado una página que ya estaba bien"
echo "✓ regenerar corrige la página y deja las demás igual"

# 4
sed -i 's/Política de Privacidad/Política de privacidad/' "$TMP/plantillas/pie.html"
ejecutar >/dev/null
[ "$(grep -l 'Política de privacidad</a>' "$TMP"/frontend/*.html | wc -l)" -eq "$PAGINAS" ] \
    || fallo "un cambio en plantillas/pie.html no ha llegado a todas las páginas"
echo "✓ un cambio en la plantilla llega a las $PAGINAS páginas"

# 5
echo '<html><body>sin marcas</body></html>' > "$TMP/frontend/nueva.html"
if ejecutar --comprobar >/dev/null 2>&1; then fallo "una página sin marcas pasa la comprobación"; fi
echo "✓ una página sin marcas da error"
