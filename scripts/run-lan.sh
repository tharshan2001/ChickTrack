#!/usr/bin/env bash
# Start the backend so a phone on the same Wi-Fi can use it (Expo Go or a development build).
#
#   ./scripts/run-lan.sh                 # finds your Mac's Wi-Fi address
#   LAN_IP=192.168.1.20 ./scripts/run-lan.sh
#
# Loads .env, then overrides the two settings that must use the LAN address:
#   OAUTH_ISSUER               tokens are only accepted when the issuer matches the URL the app calls
#   OAUTH_MOBILE_REDIRECT_URI  the app's sign-in redirect; Expo Go uses exp://<ip>:8081/--/oauth2/callback
set -euo pipefail
cd "$(dirname "$0")/.."

IP="${LAN_IP:-$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || true)}"
if [ -z "$IP" ]; then
  echo "Couldn't find your LAN IP. Run it as: LAN_IP=192.168.x.x $0" >&2
  exit 1
fi

if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi
export OAUTH_ISSUER="http://$IP:8080"
export OAUTH_MOBILE_REDIRECT_URI="chicktrack://oauth2/callback,exp://$IP:8081/--/oauth2/callback"

# Use a Postgres that is already running on the port (Homebrew, Postgres.app); otherwise start the Docker one.
PG_PORT="${POSTGRES_PORT:-5432}"
if lsof -nP -iTCP:"$PG_PORT" -sTCP:LISTEN >/dev/null 2>&1; then
  echo "Postgres is already listening on port $PG_PORT; not starting Docker."
elif docker info >/dev/null 2>&1; then
  docker compose up -d postgres
else
  echo "Nothing is listening on port $PG_PORT and Docker isn't running. Start Docker Desktop or a local Postgres." >&2
  exit 1
fi

cat <<INFO

  API for the phone : $OAUTH_ISSUER
  Sign-in redirects : $OAUTH_MOBILE_REDIRECT_URI
  Owner login       : ${OAUTH_ADMIN_USERNAME:-admin} / ${OAUTH_ADMIN_PASSWORD:-change-me}
                      (created only on the first start against an empty database)

INFO
exec ./mvnw spring-boot:run
