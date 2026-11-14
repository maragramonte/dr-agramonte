#!/usr/bin/env bash
# Prueba de scripts/iconos-comprobar.mjs: pasa con el repositorio y falla si una
# página usa un icono que no está en el recorte.
#
# Uso: bash scripts/tests/iconos-comprobar.test.sh   (necesita node)
set -euo pipefail

RAIZ=$(cd "$(dirname "$0")/../.." && pwd)
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
fallo() { echo "FALLO: $*" >&2; exit 1; }

node "$RAIZ/scripts/iconos-comprobar.mjs" "$RAIZ" >/dev/null || fallo "el repositorio no pasa"
echo "✓ el repositorio pasa"

mkdir -p "$TMP/frontend/vendor/fontawesome/css" "$TMP/frontend/js" "$TMP/plantillas" "$TMP/demo"
cp "$RAIZ/frontend/vendor/fontawesome/css/iconos.min.css" "$TMP/frontend/vendor/fontawesome/css/"
echo '<i class="fas fa-calendar-plus"></i><i class="fas fa-icono-que-no-existe"></i>' > "$TMP/frontend/index.html"
: > "$TMP/demo/demo-api.js"
if salida=$(node "$RAIZ/scripts/iconos-comprobar.mjs" "$TMP" 2>&1); then fallo "no detecta el icono que falta"; fi
grep -q "fa-icono-que-no-existe" <<<"$salida" || fallo "no dice qué icono falta"
grep -q "fa-calendar-plus" <<<"$salida" && fallo "da por malo un icono que sí está"
echo "✓ detecta un icono que no está en el recorte"
