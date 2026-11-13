#!/usr/bin/env bash
# Volcado de la base de datos de producción: comprimido, cifrado, con copia fuera
# del servidor y con rotación.
#
# Uso manual:      ./scripts/backup-db.sh
# Uso desde cron:  ver docs/DESPLIEGUE-VPS.md (apartado "Copias de seguridad")
#
# Variables en .env.prod:
#   BACKUP_AGE_RECIPIENT   Clave pública de age (age1...). Con ella, en disco solo
#                          queda el volcado cifrado. La clave privada NO va en el
#                          servidor: si alguien entra, no puede leer las copias.
#   BACKUP_RCLONE_DESTINO  Remoto de rclone donde dejar la copia (p. ej.
#                          "copias:dr-agramonte"). Exige BACKUP_AGE_RECIPIENT:
#                          fuera del servidor nunca sale nada en claro.
#   RETENER                Copias que se conservan en el servidor (14).
#   RETENER_REMOTO_DIAS    Días que se conservan en el remoto (30).
#
# Los volcados quedan en backups/ (en .gitignore: llevan datos de pacientes).
set -euo pipefail

cd "$(dirname "$0")/.."

if [ ! -f .env.prod ]; then
  echo "ERROR: no encuentro .env.prod en $(pwd)" >&2
  exit 1
fi

# Cargamos las variables para conocer usuario, base de datos y destino de las copias.
set -a
# shellcheck disable=SC1091
. ./.env.prod
set +a

RETENER=${RETENER:-14}
RETENER_REMOTO_DIAS=${RETENER_REMOTO_DIAS:-30}
RECEPTOR=${BACKUP_AGE_RECIPIENT:-}
REMOTO=${BACKUP_RCLONE_DESTINO:-}

# Antes de volcar nada: subir datos de pacientes sin cifrar no es una opción.
if [ -n "$REMOTO" ] && [ -z "$RECEPTOR" ]; then
  echo "ERROR: BACKUP_RCLONE_DESTINO necesita BACKUP_AGE_RECIPIENT; no se sube nada sin cifrar" >&2
  exit 1
fi
if [ -n "$RECEPTOR" ] && ! command -v age >/dev/null; then
  echo "ERROR: BACKUP_AGE_RECIPIENT está configurado pero age no está instalado" >&2
  exit 1
fi
if [ -n "$REMOTO" ] && ! command -v rclone >/dev/null; then
  echo "ERROR: BACKUP_RCLONE_DESTINO está configurado pero rclone no está instalado" >&2
  exit 1
fi

DB=${POSTGRES_DB:-dr_agramonte}
USER=${POSTGRES_USER:-agramonte}
SELLO=$(date +%Y%m%d_%H%M%S)
VOLCADO="backups/dr_agramonte_${SELLO}.sql.gz"

mkdir -p backups
# Nada de lo que se cree aquí debe poder leerlo otro usuario del servidor.
umask 077

docker compose --env-file .env.prod -f docker-compose.prod.yml \
  exec -T postgres pg_dump -U "$USER" "$DB" | gzip > "$VOLCADO"

# Un volcado vacío o mínimo significa que pg_dump falló: no lo damos por bueno.
if [ ! -s "$VOLCADO" ] || [ "$(stat -c%s "$VOLCADO")" -lt 1000 ]; then
  echo "ERROR: el volcado $VOLCADO está vacío o es sospechosamente pequeño" >&2
  rm -f "$VOLCADO"
  exit 1
fi

DESTINO="$VOLCADO"
if [ -n "$RECEPTOR" ]; then
  age -r "$RECEPTOR" -o "$VOLCADO.age" "$VOLCADO"
  rm -f "$VOLCADO"
  DESTINO="$VOLCADO.age"
else
  echo "AVISO: copia SIN CIFRAR y solo en este servidor. Configura BACKUP_AGE_RECIPIENT" \
       "y BACKUP_RCLONE_DESTINO (docs/DESPLIEGUE-VPS.md)." >&2
fi

# Copia fuera del servidor: si cae la máquina, las copias de dentro caen con ella.
if [ -n "$REMOTO" ]; then
  rclone copy "$DESTINO" "$REMOTO"
  # Solo se borran en el remoto copias de este script, nunca otros archivos.
  rclone delete "$REMOTO" --min-age "${RETENER_REMOTO_DIAS}d" --include "dr_agramonte_*.sql.gz.age"
fi

# Rotación local: borra las más antiguas y deja las $RETENER últimas.
find backups -maxdepth 1 -type f \( -name 'dr_agramonte_*.sql.gz' -o -name 'dr_agramonte_*.sql.gz.age' \) \
  -printf '%T@ %p\n' | sort -rn | tail -n "+$((RETENER + 1))" | cut -d' ' -f2- | xargs -r rm --

echo "Copia creada: $DESTINO ($(du -h "$DESTINO" | cut -f1)), se conservan las $RETENER últimas."
if [ -n "$REMOTO" ]; then
  echo "Copiada también a $REMOTO (se conservan $RETENER_REMOTO_DIAS días)."
fi
