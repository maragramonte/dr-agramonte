#!/usr/bin/env bash
# Prueba de scripts/seo-comprobar.mjs sobre una copia del frontend:
#   1. tal cual, pasa;
#   2. un título demasiado largo falla;
#   3. una página con noindex metida en el sitemap falla.
#
# Uso: bash scripts/tests/seo-comprobar.test.sh   (necesita node)
set -euo pipefail

RAIZ=$(cd "$(dirname "$0")/../.." && pwd)
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

fallo() { echo "FALLO: $*" >&2; exit 1; }
preparar() {
    rm -rf "$TMP/f" && mkdir -p "$TMP/f"
    cp "$RAIZ"/frontend/*.html "$RAIZ/frontend/sitemap.xml" "$TMP/f/"
}
comprobar() { node "$RAIZ/scripts/seo-comprobar.mjs" "$TMP/f"; }

preparar
comprobar >/dev/null || fallo "el frontend del repositorio no pasa"
echo "✓ el frontend del repositorio pasa"

preparar
sed -i 's#<title data-i18n="\([^"]*\)">[^<]*</title>#<title data-i18n="\1">Un título larguísimo que nadie va a leer entero en los resultados de Google</title>#' "$TMP/f/servicios.html"
if salida=$(comprobar 2>&1); then fallo "no detecta un título largo"; fi
grep -q "servicios.html: título" <<<"$salida" || fallo "no dice qué página tiene el título largo"
echo "✓ detecta un título demasiado largo"

preparar
sed -i 's#</urlset>#  <url><loc>https://maragramonte.github.io/dr-agramonte/app/testimonios.html</loc></url>\n</urlset>#' "$TMP/f/sitemap.xml"
if salida=$(comprobar 2>&1); then fallo "no detecta una página noindex en el sitemap"; fi
grep -q "testimonios.html: lleva noindex" <<<"$salida" || fallo "no dice qué página sobra en el sitemap"
echo "✓ detecta una página con noindex en el sitemap"
