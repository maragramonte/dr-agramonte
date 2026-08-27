#!/usr/bin/env bash
# Volcado de la base de datos de producción, comprimido y con rotación.
#
# Uso manual:      ./scripts/backup-db.sh
# Uso desde cron:  ver docs/DESPLIEGUE-VPS.md (apartado "Copias de seguridad")
#
# Deja el fichero en backups/dr_agramonte_AAAAMMDD_HHMMSS.sql.gz y conserva
# los RETENER últimos. La carpeta backups/ está en .gitignore: los volcados
# llevan datos de pacientes y no deben subirse al repositorio.
set -euo pipefail

RETENER=${RETENER:-14}

cd "$(dirname "$0")/.."

if [ ! -f .env.prod ]; then
  echo "ERROR: no encuentro .env.prod en $(pwd)" >&2
  exit 1
fi

# Cargamos las variables para conocer usuario y nombre de la base de datos.
set -a
# shellcheck disable=SC1091
. ./.env.prod
set +a

DB=${POSTGRES_DB:-dr_agramonte}
USER=${POSTGRES_USER:-agramonte}
SELLO=$(date +%Y%m%d_%H%M%S)
DESTINO="backups/dr_agramonte_${SELLO}.sql.gz"

mkdir -p backups

docker compose --env-file .env.prod -f docker-compose.prod.yml \
  exec -T postgres pg_dump -U "$USER" "$DB" | gzip > "$DESTINO"

# Un volcado vacío o mínimo significa que pg_dump falló: no lo damos por bueno.
if [ ! -s "$DESTINO" ] || [ "$(stat -c%s "$DESTINO")" -lt 1000 ]; then
  echo "ERROR: el volcado $DESTINO está vacío o es sospechosamente pequeño" >&2
  rm -f "$DESTINO"
  exit 1
fi

# Rotación: borra los más antiguos y deja los $RETENER últimos.
ls -1t backups/dr_agramonte_*.sql.gz 2>/dev/null | tail -n "+$((RETENER + 1))" | xargs -r rm --

echo "Copia creada: $DESTINO ($(du -h "$DESTINO" | cut -f1)), se conservan las $RETENER últimas."
