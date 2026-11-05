#!/usr/bin/env bash
# Dump the production database to deploy/backups/ and keep the last KEEP_DAYS days.
#   ./deploy/backup.sh
# Cron (daily 02:30):  30 2 * * * cd /opt/chicktrack && ./deploy/backup.sh >> deploy/backups/backup.log 2>&1
set -euo pipefail
cd "$(dirname "$0")/.."

COMPOSE=(docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env)
KEEP_DAYS="${KEEP_DAYS:-14}"
DIR=deploy/backups
mkdir -p "$DIR"

FILE="$DIR/chicktrack-$(date +%Y%m%d-%H%M%S).dump"
"${COMPOSE[@]}" exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$FILE.partial"
mv "$FILE.partial" "$FILE"
find "$DIR" -name 'chicktrack-*.dump' -mtime +"$KEEP_DAYS" -delete
echo "$(date -Is) backup written: $FILE ($(du -h "$FILE" | cut -f1))"
