#!/usr/bin/env bash
# Runs ON the server (deploy/remote-deploy.sh calls it). Idempotent: installs what is missing,
# never overwrites existing secrets, backs up the database before an update.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${API_DOMAIN:?}" "${LETSENCRYPT_EMAIL:?}"

say() { printf '\n==> %s\n' "$*"; }

say "Docker"
if ! command -v docker >/dev/null 2>&1; then
  curl -fsSL https://get.docker.com | sudo sh
fi
SUDO=""
if ! docker info >/dev/null 2>&1; then
  SUDO="sudo"
  sudo usermod -aG docker "$USER" 2>/dev/null || true
fi
COMPOSE=($SUDO docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env)

say "Firewall"
if command -v ufw >/dev/null 2>&1; then
  for port in $(sudo sshd -T 2>/dev/null | awk '/^port /{print $2}'); do sudo ufw allow "$port/tcp" >/dev/null; done
  sudo ufw allow OpenSSH >/dev/null
  sudo ufw allow 80/tcp >/dev/null
  sudo ufw allow 443/tcp >/dev/null
  sudo ufw --force enable >/dev/null
  sudo ufw status | sed -n '1,12p'
else
  echo "ufw not installed; make sure ports 80 and 443 are open in your provider's firewall."
fi

say "Secrets"
if [ ! -f deploy/.env ]; then
  cp deploy/.env.example deploy/.env
  OWNER_PASSWORD=$(openssl rand -hex 12)
  sed -i \
    -e "s|^API_DOMAIN=.*|API_DOMAIN=$API_DOMAIN|" \
    -e "s|^LETSENCRYPT_EMAIL=.*|LETSENCRYPT_EMAIL=$LETSENCRYPT_EMAIL|" \
    -e "s|^POSTGRES_PASSWORD=.*|POSTGRES_PASSWORD=$(openssl rand -hex 24)|" \
    -e "s|^OAUTH_ADMIN_PASSWORD=.*|OAUTH_ADMIN_PASSWORD=$OWNER_PASSWORD|" \
    deploy/.env
  chmod 600 deploy/.env
  echo "Created deploy/.env with generated passwords."
else
  echo "deploy/.env exists; keeping it."
fi
if [ ! -f deploy/secrets/jwk.pem ]; then
  mkdir -p deploy/secrets
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out deploy/secrets/jwk.pem 2>/dev/null
  sudo chown 10001:10001 deploy/secrets/jwk.pem
  sudo chmod 400 deploy/secrets/jwk.pem
  echo "Created the JWT signing key. Copy deploy/secrets/jwk.pem somewhere safe off this server."
else
  echo "Signing key exists; keeping it."
fi

if "${COMPOSE[@]}" ps --status running --services 2>/dev/null | grep -qx postgres; then
  say "Backing up the database before the update"
  $SUDO ./deploy/backup.sh
fi

say "Certificate and start"
$SUDO env STAGING="${STAGING:-0}" FORCE="${FORCE:-0}" ./deploy/init-letsencrypt.sh
"${COMPOSE[@]}" up -d --build --remove-orphans
$SUDO docker image prune -f >/dev/null

say "Waiting for the API"
CURL=(curl -fsS --max-time 5)
[ "${STAGING:-0}" = "1" ] && CURL+=(-k)
for _ in $(seq 1 36); do
  if "${CURL[@]}" "https://$API_DOMAIN/actuator/health" 2>/dev/null | grep -q UP; then
    echo "https://$API_DOMAIN/actuator/health is UP"
    "${COMPOSE[@]}" ps
    if [ -n "${OWNER_PASSWORD:-}" ]; then
      printf '\nOwner sign-in (shown once, change it after signing in):\n  username: %s\n  password: %s\n' \
        "$(grep '^OAUTH_ADMIN_USERNAME=' deploy/.env | cut -d= -f2)" "$OWNER_PASSWORD"
    fi
    [ "${STAGING:-0}" = "1" ] && echo "Staging certificate in use. Run again with FORCE=1 for the real one."
    exit 0
  fi
  sleep 5
done
echo "The API did not answer in 3 minutes. Last log lines:" >&2
"${COMPOSE[@]}" logs --tail 80 app >&2
exit 1
