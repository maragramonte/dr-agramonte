#!/usr/bin/env bash
# Restauración de prueba: carga una copia en un PostgreSQL temporal y comprueba que
# tiene datos. No toca la base de datos de producción y borra el contenedor al acabar.
#
# Una copia que nunca se ha restaurado no es una copia: es una esperanza. Pásalo una
# vez al mes, donde tengas la clave privada (normalmente tu ordenador, no el VPS).
#
# Uso:
#   ./scripts/probar-restauracion.sh copia.sql.gz.age ~/.config/age/dr-agramonte.txt
#   ./scripts/probar-restauracion.sh copia.sql.gz            (copia antigua sin cifrar)
#
# Variables opcionales:
#   USUARIO_DB        Dueño de las tablas en la copia (POSTGRES_USER de .env.prod).
#   IMAGEN_POSTGRES   La misma versión que producción (postgres:15).
set -euo pipefail

COPIA=${1:?Uso: $0 <copia.sql.gz[.age]> [clave-privada-age]}
CLAVE=${2:-}
USUARIO=${USUARIO_DB:-agramonte}
IMAGEN=${IMAGEN_POSTGRES:-postgres:15}

[ -f "$COPIA" ] || { echo "ERROR: no existe $COPIA" >&2; exit 1; }
case "$COPIA" in
  *.age)
    if [ -z "$CLAVE" ] || [ ! -f "$CLAVE" ]; then
      echo "ERROR: la copia está cifrada; pasa la clave privada de age como segundo argumento" >&2
      exit 1
    fi
    command -v age >/dev/null || { echo "ERROR: age no está instalado" >&2; exit 1; }
    ;;
esac

leer_copia() {
  case "$COPIA" in
    *.age) age -d -i "$CLAVE" "$COPIA" | gunzip ;;
    *) gunzip -c "$COPIA" ;;
  esac
}

NOMBRE="restauracion-prueba-$$"
CLAVE_DB=$(head -c 16 /dev/urandom | od -An -tx1 | tr -d ' \n')

# El mismo usuario que en producción, para que los OWNER TO del volcado no fallen.
docker run -d --rm --name "$NOMBRE" \
  -e POSTGRES_USER="$USUARIO" -e POSTGRES_PASSWORD="$CLAVE_DB" -e POSTGRES_DB=restaurada \
  "$IMAGEN" > /dev/null
trap 'docker rm -f "$NOMBRE" > /dev/null 2>&1 || true' EXIT

listo=no
for _ in $(seq 1 60); do
  if docker exec "$NOMBRE" pg_isready -q -U "$USUARIO" -d restaurada; then
    listo=si
    break
  fi
  sleep 1
done
[ "$listo" = si ] || { echo "ERROR: el PostgreSQL temporal no arranca" >&2; exit 1; }

# ON_ERROR_STOP: si una sola sentencia falla, la copia no se da por buena.
leer_copia | docker exec -i "$NOMBRE" psql -q -v ON_ERROR_STOP=1 -U "$USUARIO" -d restaurada > /dev/null

consulta() {
  docker exec "$NOMBRE" psql -tA -U "$USUARIO" -d restaurada -c "$1"
}
existe() {
  [ "$(consulta "SELECT to_regclass('public.$1') IS NOT NULL")" = t ]
}

tablas=$(consulta "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'")
if [ "$tablas" -eq 0 ]; then
  echo "ERROR: la copia se ha cargado pero no tiene tablas" >&2
  exit 1
fi

echo "Restauración correcta en un PostgreSQL temporal ($IMAGEN):"
echo "  tablas: $tablas"
for tabla in usuarios citas; do
  if existe "$tabla"; then
    echo "  $tabla: $(consulta "SELECT count(*) FROM $tabla")"
  fi
done
if existe flyway_schema_history; then
  echo "  última migración: V$(consulta "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1")"
fi
