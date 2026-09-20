#!/usr/bin/env bash
#
# Бекапить продакшн БД protaxo напряму з Docker-контейнера (docker exec
# pg_dump -Fc, з проміжним docker cp) — той самий підхід, що
# scripts/backup-db.ps1 використовує для локальної розробки на Windows.
# Призначено для щоденного запуску через cron на VPS.
#
# Дампи пишуться в $BACKUP_DIR, ротація файлів старших за $RETENTION_DAYS.
# Це лише локальна на VPS копія — copy поза сервером (3-2-1) робиться
# окремо, див. docs/Варіанти розгортання (сервер).md.
set -euo pipefail

CONTAINER_NAME="${CONTAINER_NAME:-protaxo_postgres}"
PG_USER="${PG_USER:-postgres}"
PG_DATABASE="${PG_DATABASE:-protaxo}"
BACKUP_DIR="${BACKUP_DIR:-/opt/protaxo/backups}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"

if ! docker ps --filter "name=^${CONTAINER_NAME}$" --filter "status=running" --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    echo "Container '${CONTAINER_NAME}' is not running." >&2
    exit 1
fi

mkdir -p "$BACKUP_DIR"

timestamp="$(date +%Y%m%d_%H%M%S)"
out_file="${BACKUP_DIR}/protaxo_${timestamp}.dump"
tmp_in_container="/tmp/protaxo_backup_${timestamp}.dump"

docker exec "$CONTAINER_NAME" pg_dump -U "$PG_USER" -d "$PG_DATABASE" -Fc -f "$tmp_in_container"
docker cp "${CONTAINER_NAME}:${tmp_in_container}" "$out_file"
docker exec "$CONTAINER_NAME" rm -f "$tmp_in_container"

if [ ! -s "$out_file" ]; then
    echo "docker cp failed or produced an empty file" >&2
    rm -f "$out_file"
    exit 1
fi

echo "Backup written to $out_file"

# Ротація старих бекапів
find "$BACKUP_DIR" -maxdepth 1 -name 'protaxo_*.dump' -type f -mtime +"$RETENTION_DAYS" -print -delete
