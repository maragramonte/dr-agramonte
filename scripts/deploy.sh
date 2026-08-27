#!/usr/bin/env bash
# Despliega en el VPS la última versión de la rama actual.
#
# Uso:  ./scripts/deploy.sh
#
# Hace copia de seguridad ANTES de tocar nada (por si una migración de Flyway
# sale mal), trae los cambios, reconstruye la imagen y levanta los servicios.
set -euo pipefail

cd "$(dirname "$0")/.."

COMPOSE="docker compose --env-file .env.prod -f docker-compose.prod.yml"

# Necesitamos DOMAIN_WWW para la comprobación final de salud.
set -a
# shellcheck disable=SC1091
. ./.env.prod
set +a

echo "==> Copia de seguridad previa"
# La primera vez todavía no hay base de datos en marcha: no es motivo de aborto.
./scripts/backup-db.sh || echo "    (sin copia previa: ¿primer despliegue?)"

echo "==> Trayendo cambios"
git pull --ff-only

echo "==> Reconstruyendo y levantando"
$COMPOSE up -d --build

echo "==> Estado"
$COMPOSE ps

# Las imágenes viejas se acumulan rápido y un VPS pequeño tiene poco disco.
docker image prune -f >/dev/null

echo "==> Listo. Salud de la aplicación:"
curl -fsS "https://${DOMAIN_WWW:-www.dragramonte.com}/actuator/health" || \
  echo "    (aún no responde; mirar: $COMPOSE logs -f app)"
