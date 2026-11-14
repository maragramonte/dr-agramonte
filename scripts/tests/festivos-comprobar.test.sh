#!/usr/bin/env bash
# Prueba de scripts/festivos-comprobar.mjs sobre una copia de las listas:
#   1. con el repositorio tal cual, pasa;
#   2. si se quita un festivo de la web, falla y dice cuál;
#   3. si se añade uno solo a la migración de MySQL, falla y dice en qué perfil.
#
# Uso: bash scripts/tests/festivos-comprobar.test.sh   (necesita node)
set -euo pipefail

RAIZ=$(cd "$(dirname "$0")/../.." && pwd)
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

fallo() { echo "FALLO: $*" >&2; exit 1; }
preparar() {
    rm -rf "$TMP/copia"
    mkdir -p "$TMP/copia/frontend/js/modules" "$TMP/copia/backend/src/main/resources/db"
    cp "$RAIZ/frontend/js/modules/festivos.js" "$TMP/copia/frontend/js/modules/"
    cp -r "$RAIZ/backend/src/main/resources/db/migration" "$TMP/copia/backend/src/main/resources/db/"
}
comprobar() { node "$RAIZ/scripts/festivos-comprobar.mjs" "$TMP/copia"; }

# 1
preparar
comprobar >/dev/null || fallo "el repositorio tiene listas distintas"
echo "✓ las listas del repositorio coinciden"

# 2
preparar
sed -i "/'2027-03-01'/d" "$TMP/copia/frontend/js/modules/festivos.js"
if salida=$(comprobar 2>&1); then fallo "no detecta un festivo que falta en la web"; fi
grep -q "2027-03-01" <<<"$salida" || fallo "no dice qué festivo falta"
echo "✓ detecta un festivo que falta en la web"

# 3
preparar
cat > "$TMP/copia/backend/src/main/resources/db/migration/mysql/V99__prueba.sql" <<'SQL'
INSERT IGNORE INTO festivos (fecha, nombre) VALUES ('2028-01-01', 'Año Nuevo');
SQL
if salida=$(comprobar 2>&1); then fallo "no detecta un festivo que solo está en MySQL"; fi
grep -q "2028-01-01.*mysql" <<<"$salida" || fallo "no dice en qué perfil está la diferencia"
echo "✓ detecta un festivo que solo está en una de las migraciones"
