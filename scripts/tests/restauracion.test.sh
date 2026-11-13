#!/usr/bin/env bash
# Prueba de punta a punta de una copia: volcado real de un PostgreSQL, cifrado con
# age y restaurado con scripts/probar-restauracion.sh. Necesita Docker y age.
#
# Uso: bash scripts/tests/restauracion.test.sh
set -euo pipefail

RAIZ=$(cd "$(dirname "$0")/../.." && pwd)
TMP=$(mktemp -d)
ORIGEN="origen-copia-$$"
trap 'docker rm -f "$ORIGEN" > /dev/null 2>&1 || true; rm -rf "$TMP"' EXIT

fallo() { echo "FALLO: $*" >&2; exit 1; }

docker run -d --rm --name "$ORIGEN" \
  -e POSTGRES_USER=agramonte -e POSTGRES_PASSWORD=prueba -e POSTGRES_DB=dr_agramonte \
  postgres:15 > /dev/null
for _ in $(seq 1 60); do
  docker exec "$ORIGEN" pg_isready -q -U agramonte -d dr_agramonte && break
  sleep 1
done

docker exec -i "$ORIGEN" psql -q -v ON_ERROR_STOP=1 -U agramonte -d dr_agramonte <<'SQL'
CREATE TABLE usuarios (id bigint PRIMARY KEY, email text NOT NULL);
CREATE TABLE citas (id bigint PRIMARY KEY, usuario_id bigint REFERENCES usuarios(id));
INSERT INTO usuarios VALUES (1, 'paciente@example.com');
INSERT INTO citas VALUES (1, 1), (2, 1);
SQL

age-keygen -o "$TMP/clave.txt" 2> /dev/null
docker exec "$ORIGEN" pg_dump -U agramonte dr_agramonte | gzip \
  | age -r "$(age-keygen -y "$TMP/clave.txt")" > "$TMP/copia.sql.gz.age"

salida=$(bash "$RAIZ/scripts/probar-restauracion.sh" "$TMP/copia.sql.gz.age" "$TMP/clave.txt")
echo "$salida"
echo "$salida" | grep -q "usuarios: 1" || fallo "no se han restaurado los usuarios"
echo "$salida" | grep -q "citas: 2" || fallo "no se han restaurado las citas"

# Sin la clave privada, la copia cifrada no se puede abrir.
if bash "$RAIZ/scripts/probar-restauracion.sh" "$TMP/copia.sql.gz.age" > /dev/null 2>&1; then
  fallo "ha aceptado una copia cifrada sin clave"
fi

echo "OK: la copia cifrada se restaura en un PostgreSQL limpio"
