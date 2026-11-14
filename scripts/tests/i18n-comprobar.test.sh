#!/usr/bin/env bash
# Prueba de scripts/i18n-comprobar.mjs sobre una copia del frontend:
#   1. con el repositorio tal cual, pasa;
#   2. si falta una traducción al inglés, falla y dice cuál;
#   3. si una traducción pierde un enlace del castellano, falla;
#   4. una clave nueva en el HTML sin traducir hace fallar la comprobación.
#
# Uso: bash scripts/tests/i18n-comprobar.test.sh   (necesita node)
set -euo pipefail

RAIZ=$(cd "$(dirname "$0")/../.." && pwd)
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

fallo() { echo "FALLO: $*" >&2; exit 1; }
ejecutar() { FRONTEND_RAIZ="$TMP" node "$RAIZ/scripts/i18n-comprobar.mjs"; }
TEXTOS="$TMP/frontend/js/modules/i18n-textos.js"

cp -r "$RAIZ/frontend" "$TMP/frontend"
cp -r "$RAIZ/plantillas" "$TMP/plantillas"

# 1
ejecutar >/dev/null || fallo "el repositorio tiene traducciones incompletas"
echo "✓ todas las claves tienen catalán e inglés"

# 2
cp "$TEXTOS" "$TMP/textos.bak"
python3 - "$TEXTOS" <<'PY'
import sys
p = sys.argv[1]; s = open(p, encoding='utf-8').read()
i = s.index('    en: {')
j = s.index('"home.hero.title"', i)
fin = s.index('\n', j)
open(p, 'w', encoding='utf-8').write(s[:j - 8] + s[fin + 1:])
PY
if salida=$(ejecutar 2>&1); then fallo "no detecta una traducción al inglés que falta"; fi
grep -q 'en: falta «home.hero.title»' <<<"$salida" || fallo "no dice qué clave falta"
cp "$TMP/textos.bak" "$TEXTOS"
echo "✓ detecta una traducción que falta y dice cuál"

# 3
sed -i 's|<a href=\\"contacto.html\\">Escrigui a la consulta</a>|Escrigui a la consulta|' "$TEXTOS"
if ejecutar >/dev/null 2>&1; then fallo "no detecta un enlace perdido al traducir"; fi
cp "$TMP/textos.bak" "$TEXTOS"
echo "✓ detecta un enlace que se pierde al traducir"

# 4
sed -i 's|<h1 id="hero-title"|<p data-i18n="clave.nueva.sin.traducir">Nuevo</p><h1 id="hero-title"|' "$TMP/frontend/index.html"
if ejecutar >/dev/null 2>&1; then fallo "una clave nueva sin traducir pasa la comprobación"; fi
echo "✓ una clave nueva sin traducir da error"
