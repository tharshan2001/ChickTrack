#!/usr/bin/env bash
# Restore a dump made by backup.sh. Replaces the current data: the API is stopped while it runs.
#   ./deploy/restore.sh deploy/backups/chicktrack-20261105-023000.dump
set -euo pipefail
cd "$(dirname "$0")/.."

FILE="${1:?usage: $0 <dump file>}"
[ -f "$FILE" ] || { echo "No such file: $FILE" >&2; exit 1; }
COMPOSE=(docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env)

read -r -p "This replaces the production database with $FILE. Type 'restore' to continue: " answer
[ "$answer" = "restore" ] || { echo "Cancelled."; exit 1; }

"${COMPOSE[@]}" stop app
"${COMPOSE[@]}" exec -T postgres sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --no-owner' < "$FILE"
"${COMPOSE[@]}" start app
echo "Restored $FILE. Check: curl -fsS https://\$API_DOMAIN/actuator/health"
