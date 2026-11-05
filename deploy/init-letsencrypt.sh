#!/usr/bin/env bash
# First start on a new server: gets the Let's Encrypt certificate for API_DOMAIN and brings the
# stack up. nginx cannot start without a certificate, so a throwaway one is used for the minute it
# takes certbot to answer the HTTP challenge.
#
#   ./deploy/init-letsencrypt.sh             # real certificate
#   STAGING=1 ./deploy/init-letsencrypt.sh   # test against Let's Encrypt staging (no rate limits)
#   FORCE=1 ./deploy/init-letsencrypt.sh     # replace an existing certificate
set -euo pipefail
cd "$(dirname "$0")/.."

ENV_FILE=deploy/.env
[ -f "$ENV_FILE" ] || { echo "Create $ENV_FILE first (cp deploy/.env.example deploy/.env)" >&2; exit 1; }
set -a; . "$ENV_FILE"; set +a
: "${API_DOMAIN:?set API_DOMAIN in $ENV_FILE}"
: "${LETSENCRYPT_EMAIL:?set LETSENCRYPT_EMAIL in $ENV_FILE}"
[ -f deploy/secrets/jwk.pem ] || { echo "Create deploy/secrets/jwk.pem first (see deploy/DEPLOY.md)" >&2; exit 1; }

COMPOSE=(docker compose -f deploy/docker-compose.prod.yml --env-file "$ENV_FILE")
LIVE="/etc/letsencrypt/live/$API_DOMAIN"

if [ "${FORCE:-0}" != "1" ] && "${COMPOSE[@]}" run --rm --no-deps --entrypoint sh certbot \
    -c "test -f /etc/letsencrypt/renewal/$API_DOMAIN.conf"; then
  echo "A certificate for $API_DOMAIN already exists. Starting the stack."
  "${COMPOSE[@]}" up -d --build
  exit 0
fi

echo "==> Building and starting the database and API"
"${COMPOSE[@]}" up -d --build postgres app

echo "==> Temporary certificate so nginx can start"
"${COMPOSE[@]}" run --rm --no-deps --entrypoint sh certbot -c "
  mkdir -p '$LIVE' &&
  openssl req -x509 -nodes -newkey rsa:2048 -days 1 \
    -keyout '$LIVE/privkey.pem' -out '$LIVE/fullchain.pem' -subj '/CN=localhost'"

echo "==> Starting nginx"
"${COMPOSE[@]}" up -d nginx

echo "==> Requesting the Let's Encrypt certificate for $API_DOMAIN"
"${COMPOSE[@]}" run --rm --no-deps --entrypoint sh certbot -c "
  rm -rf '$LIVE' '/etc/letsencrypt/archive/$API_DOMAIN' '/etc/letsencrypt/renewal/$API_DOMAIN.conf'"
STAGING_FLAG=""
[ "${STAGING:-0}" = "1" ] && STAGING_FLAG="--staging"
"${COMPOSE[@]}" run --rm --no-deps --entrypoint certbot certbot certonly \
  --webroot -w /var/www/certbot \
  -d "$API_DOMAIN" \
  --email "$LETSENCRYPT_EMAIL" --agree-tos --no-eff-email \
  --rsa-key-size 2048 --non-interactive $STAGING_FLAG

echo "==> Loading the certificate and starting renewals"
"${COMPOSE[@]}" exec nginx nginx -s reload
"${COMPOSE[@]}" up -d certbot

echo
echo "Done. Check: curl -fsS https://$API_DOMAIN/actuator/health"
