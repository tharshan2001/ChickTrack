# Deploying ChickTrack (one Linux server with Docker)

The stack is four containers: **PostgreSQL** (not reachable from outside), the **API**, **nginx**, which serves the API over HTTPS at `api.cloudbox.my`, and **certbot**, which gets the Let's Encrypt certificate and renews it.

```
phone ──https──▶ nginx :443 ──▶ app :8090 ──▶ postgres :5432
                   ▲
       certbot ────┘ (certificate, renewed every 60–90 days)
```

The examples use `api.cloudbox.my`. To serve the API on the bare domain instead, set `API_DOMAIN=cloudbox.my` and point that record at the server.

## Quick deploy from your Mac

With the DNS record in place (section 1), one command does sections 2 to 4 and every later update:

```bash
./deploy/remote-deploy.sh <user>@<server-ip>              # STAGING=1 first for a test certificate, FORCE=1 to replace it
```

It uploads the last commit (not uncommitted changes), installs Docker, opens the firewall, creates `deploy/.env` with generated passwords and the signing key if they don't exist, backs up the database before an update, gets the certificate, starts the stack and waits for `https://api.cloudbox.my/actuator/health`. On the first run it prints the owner's password once. You type your SSH and sudo passwords in your own terminal.

The manual steps below do the same thing by hand.

## 1. Before you start

- A server running Ubuntu 22.04 or 24.04 with at least 2 GB RAM (1 GB works with `JAVA_OPTS=-Xmx512m`).
- At your DNS provider for `cloudbox.my`, an **A record** for `api` (that is, `api.cloudbox.my`) pointing at the server's public IP. Wait until `dig +short api.cloudbox.my` returns that IP before you start: Let's Encrypt checks it.
- Tests passing on your machine: `./mvnw test`. The image build skips the tests.

## 2. Prepare the server (once)

```bash
# Docker
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER   # log out and back in

# Firewall: SSH, HTTP (certificate challenge, redirect), HTTPS
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable

# Code
sudo mkdir -p /opt/chicktrack && sudo chown $USER /opt/chicktrack
git clone <your-repo-url> /opt/chicktrack
cd /opt/chicktrack
```

## 3. Secrets (once)

```bash
cp deploy/.env.example deploy/.env
nano deploy/.env        # API_DOMAIN, LETSENCRYPT_EMAIL, POSTGRES_PASSWORD, OAUTH_ADMIN_PASSWORD
chmod 600 deploy/.env
```

Generate strong values with `openssl rand -base64 24` (avoid `$`, quotes and spaces in values). Passwords shorter than 12 characters, or defaults like `change-me`, stop the app at startup on purpose.

Create the JWT signing key. Tokens stay valid across restarts only while this file is unchanged, so back it up somewhere safe outside the server:

```bash
mkdir -p deploy/secrets
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out deploy/secrets/jwk.pem
sudo chown 10001:10001 deploy/secrets/jwk.pem   # the app runs as uid 10001
sudo chmod 400 deploy/secrets/jwk.pem
```

`deploy/.env`, `deploy/secrets/` and `deploy/backups/` are git-ignored.

## 4. First start and certificate

```bash
STAGING=1 ./deploy/init-letsencrypt.sh   # optional dry run against Let's Encrypt staging (browsers won't trust it)
FORCE=1 ./deploy/init-letsencrypt.sh     # real certificate (FORCE replaces the staging one; leave it out otherwise)
```

The script builds the image, starts the database and the API, starts nginx with a throwaway certificate, asks Let's Encrypt for the real one through `/.well-known/acme-challenge/`, reloads nginx and starts certbot's renewal loop. Let's Encrypt allows only a few failed attempts per hour, so if the DNS isn't ready yet, use `STAGING=1` until it works.

On first start, Flyway creates the schema and the owner is seeded from `OAUTH_ADMIN_USERNAME` / `OAUTH_ADMIN_PASSWORD`. Follow the API log with:

```bash
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env logs -f app   # Ctrl+C to stop following
```

Check:

```bash
curl -fsS https://api.cloudbox.my/actuator/health        # {"status":"UP"}
curl -fsS https://api.cloudbox.my/.well-known/openid-configuration | head -c 200   # issuer is https://api.cloudbox.my
curl -sI http://api.cloudbox.my | head -1                # 301 to https
```

Then:

1. In the mobile app, set the API base URL to `https://api.cloudbox.my` and rebuild it.
2. Sign in as the owner and change the password (`POST /api/v1/me/password`). After that, `OAUTH_ADMIN_PASSWORD` is only used if the database is ever empty again.
3. Create staff users. Each one gets a temporary password and must change it at first sign-in.

## 5. Backups

```bash
./deploy/backup.sh                       # writes deploy/backups/chicktrack-YYYYMMDD-HHMMSS.dump
crontab -e                               # daily at 02:30, keeps 14 days:
30 2 * * * cd /opt/chicktrack && ./deploy/backup.sh >> deploy/backups/backup.log 2>&1
```

Backups on the same server don't survive losing the server: copy `deploy/backups/` off it regularly (for example with `rclone` or `scp` to another machine or cloud storage).

Restore (stops the API, replaces all data):

```bash
./deploy/restore.sh deploy/backups/chicktrack-20261105-023000.dump
```

## 6. Certificates

certbot checks twice a day and renews when the certificate is within 30 days of expiry; nginx reloads every 6 hours to pick it up. Nothing to do. To check:

```bash
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env run --rm --entrypoint certbot certbot certificates
```

## 7. Updating

```bash
cd /opt/chicktrack
./deploy/backup.sh                       # always: migrations only go forward
git pull
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env up -d --build app
docker image prune -f
```

The API is down for the few seconds the new container takes to start. New Flyway migrations run automatically at startup. To roll back, check out the previous commit, rebuild, and restore the backup taken before the update if the update ran a migration.

## 8. Day to day

| Task | Command (from `/opt/chicktrack`) |
|---|---|
| Status | `docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env ps` |
| API logs | `... logs --tail 200 app` |
| Restart the API | `... restart app` |
| nginx logs / reload | `... logs --tail 200 nginx` / `... exec nginx nginx -s reload` |
| Database shell | `... exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'` |
| Disk use | `docker system df` |

Logs rotate at 10 MB × 5 files per container. Set `SENTRY_DSN` in `deploy/.env` to get errors reported to Sentry.

## What the production profile changes

`SPRING_PROFILES_ACTIVE=prod` (set by the compose file) loads `application-prod.properties`:

- Database, issuer, owner password and signing key have no defaults. Startup fails if any is missing, if the issuer is not `https://`, or if a password is weak or a default (`ProductionSafetyCheck`).
- No generated signing key, so tokens survive restarts.
- Only `/actuator/health` is exposed, without details. Swagger UI and `/v3/api-docs` are off.
- The app trusts `X-Forwarded-*` from nginx, so the sign-in page and redirects use `https`. nginx serves only `/actuator/health` from the actuator, redirects HTTP to HTTPS, sets HSTS and limits request bodies to 5 MB.
- Graceful shutdown: requests in progress get up to 20 seconds to finish on restart.
