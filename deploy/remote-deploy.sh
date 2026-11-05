#!/usr/bin/env bash
# Deploy from your Mac to the production server over SSH. Ships the committed code (git HEAD),
# then runs deploy/server-setup.sh on the server: Docker, firewall, secrets, signing key,
# Let's Encrypt certificate, start, health check. Safe to run again for every update.
#
#   ./deploy/remote-deploy.sh ubuntu@203.0.113.10
#   STAGING=1 ./deploy/remote-deploy.sh ubuntu@203.0.113.10   # test certificate first
#   FORCE=1   ./deploy/remote-deploy.sh ubuntu@203.0.113.10   # replace the (staging) certificate
#
# You type your SSH and sudo passwords in your own terminal; they are never stored.
set -euo pipefail
cd "$(dirname "$0")/.."

TARGET="${1:?usage: $0 user@server}"
DOMAIN="${API_DOMAIN:-api.cloudbox.my}"
EMAIL="${LETSENCRYPT_EMAIL:-$(git config user.email || true)}"
REMOTE_DIR="${REMOTE_DIR:-/opt/chicktrack}"
[ -n "$EMAIL" ] || { echo "Set LETSENCRYPT_EMAIL=you@example.com" >&2; exit 1; }

# One SSH login for the whole run.
SOCKET="$HOME/.ssh/cm-chicktrack-%r@%h:%p"
SSH=(ssh -o ControlMaster=auto -o ControlPath="$SOCKET" -o ControlPersist=10m)
trap '"${SSH[@]}" -O exit "$TARGET" >/dev/null 2>&1 || true' EXIT

if [ -n "$(git status --porcelain)" ]; then
  echo "Note: uncommitted changes are NOT deployed; only the last commit ($(git rev-parse --short HEAD)) is."
fi

SERVER_HOST=$(ssh -G "$TARGET" | awk '/^hostname /{print $2; exit}')
SERVER_IP=$(dig +short "$SERVER_HOST" | tail -1); SERVER_IP=${SERVER_IP:-$SERVER_HOST}
DNS_IP=$(dig +short "$DOMAIN" A | tail -1)
echo "==> $DOMAIN resolves to ${DNS_IP:-nothing}; server is $SERVER_IP"
if [ "$DNS_IP" != "$SERVER_IP" ]; then
  echo "    The DNS A record for $DOMAIN must point at $SERVER_IP or the certificate request fails."
  read -r -p "    Continue anyway? [y/N] " answer
  [ "$answer" = "y" ] || exit 1
fi

echo "==> Connecting to $TARGET"
"${SSH[@]}" -t "$TARGET" "sudo mkdir -p '$REMOTE_DIR' && sudo chown \"\$(id -u):\$(id -g)\" '$REMOTE_DIR'"

echo "==> Uploading commit $(git rev-parse --short HEAD) to $REMOTE_DIR"
git archive --format=tar HEAD | "${SSH[@]}" "$TARGET" "tar -xf - -C '$REMOTE_DIR'"

echo "==> Setting up and starting on the server"
"${SSH[@]}" -t "$TARGET" "cd '$REMOTE_DIR' && API_DOMAIN='$DOMAIN' LETSENCRYPT_EMAIL='$EMAIL' STAGING='${STAGING:-0}' FORCE='${FORCE:-0}' bash deploy/server-setup.sh"
