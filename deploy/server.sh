#!/usr/bin/env bash
# Build the server jar locally and publish it behind nginx + Traefik.
# Safe to re-run. Does not drop ArangoDB data, certificates, or uploads.
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
# shellcheck source=deploy/lib.sh
source "$ROOT/deploy/lib.sh"

usage() {
  cat <<'EOF'
Usage: deploy/server.sh [--skip-init]

Builds the server fat jar locally, uploads it with .env.prod, and serves it
with nginx behind Traefik TLS. WebSocket and SSE stay on port 443.

  --skip-init   Skip package install, firewall, nginx/Traefik setup, and
                ArangoDB user/database creation. Still uploads the jar and
                .env, restarts the service, and runs the health check.

Config: deploy/server.env and .env.prod
EOF
}

SKIP_INIT=0
for arg in "$@"; do
  case "$arg" in
    --skip-init) SKIP_INIT=1 ;;
    -h|--help) usage; exit 0 ;;
    *) die "unknown argument: $arg" ;;
  esac
done

command -v curl >/dev/null 2>&1 || die "curl is required locally"
command -v rsync >/dev/null 2>&1 || die "rsync is required locally"

cd "$ROOT"
load_env_file "$ROOT/deploy/server.env"
load_env_file "$ROOT/.env.prod"
require_var SSH_HOST
require_var SSH_USER
require_var SERVER_DOMAIN
require_var ARANGO_HOST
require_var ARANGO_PORT
require_var ARANGO_USER
require_var ARANGO_PASSWORD
require_var ARANGO_DATABASE
API_MODE=$(printf '%s' "${API_MODE:-proxy}" | tr '[:upper:]' '[:lower:]')
APP_PORT=${PORT:-8080}
API_NGINX_BIND=${API_NGINX_BIND:-127.0.0.1:8088}
WEB_DOMAIN=${WEB_DOMAIN:-}
SSH_PORT=${SSH_PORT:-22}
validate_domain "$SERVER_DOMAIN"
[[ -n "$WEB_DOMAIN" ]] && validate_domain "$WEB_DOMAIN"
[[ "$API_NGINX_BIND" == *:* ]] || die "API_NGINX_BIND must be host:port"
[[ "$APP_PORT" =~ ^[0-9]+$ ]] || die "PORT must be a number"
case "$API_MODE" in
  proxy|direct) ;;
  *) die "API_MODE must be proxy or direct" ;;
esac
reject_newlines ARANGO_PASSWORD
reject_newlines ARANGO_USER
reject_newlines ARANGO_DATABASE
if [[ "$SKIP_INIT" -eq 0 ]]; then
  require_var ACME_EMAIL
  require_var ARANGO_ROOT_PASSWORD
  reject_newlines ARANGO_ROOT_PASSWORD
fi
build_ssh

log "Building the server jar"
./gradlew clean :server:shadowJar
jar=$(find "$ROOT/server/build/libs" -maxdepth 1 -type f -name '*-all.jar' | sort | tail -n 1)
if [[ -z "$jar" ]]; then
  jar=$(find "$ROOT/server/build/libs" -maxdepth 1 -type f -name '*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' -printf '%s %p\n' | sort -n | tail -n 1 | cut -d' ' -f2-)
fi
[[ -n "$jar" && -f "$jar" ]] || die "server fat jar not found"
log "Using $(basename "$jar")"

if [[ "$SKIP_INIT" -eq 0 ]]; then
  log "Configuring nginx, Traefik, and ArangoDB on ${SSH_TARGET}"
  TRAEFIK_VERSION=${TRAEFIK_VERSION:-v3.7.13}
  stage_traefik_tarball
  send_remote "$ROOT" \
    "SERVER_DOMAIN=$SERVER_DOMAIN" \
    "WEB_DOMAIN=$WEB_DOMAIN" \
    "APP_PORT=$APP_PORT" \
    "API_NGINX_BIND=$API_NGINX_BIND" \
    "ACME_EMAIL=${ACME_EMAIL:-}" \
    "SSH_PORT=$SSH_PORT" \
    "TRAEFIK_VERSION=$TRAEFIK_VERSION" \
    "TRAEFIK_TARBALL=${TRAEFIK_TARBALL:-}" \
    "TRAEFIK_DYNAMIC_DIR=${TRAEFIK_DYNAMIC_DIR:-}" \
    "ARANGO_HOST=$ARANGO_HOST" \
    "ARANGO_PORT=$ARANGO_PORT" \
    "ARANGO_USER=$ARANGO_USER" \
    "ARANGO_PASSWORD=$ARANGO_PASSWORD" \
    "ARANGO_DATABASE=$ARANGO_DATABASE" \
    "ARANGO_ROOT_PASSWORD=$ARANGO_ROOT_PASSWORD" <<'REMOTE'
set -euo pipefail
require_root
: "${SERVER_DOMAIN:?}"
: "${APP_PORT:?}"
: "${API_NGINX_BIND:?}"
: "${ARANGO_HOST:?}"
: "${ARANGO_PORT:?}"
: "${ARANGO_USER:?}"
: "${ARANGO_PASSWORD:?}"
: "${ARANGO_DATABASE:?}"
: "${ARANGO_ROOT_PASSWORD:?}"

ensure_base_packages
ensure_java21
free_edge_ports
ensure_nginx_upgrade_map >/dev/null
traefik_state=$(ensure_traefik) || die "Traefik setup failed"

if ! id saigonquest >/dev/null 2>&1; then
  useradd --system --home-dir /opt/saigonquest --create-home --shell /usr/sbin/nologin saigonquest
fi
install -d -o saigonquest -g saigonquest -m 750 /opt/saigonquest /opt/saigonquest/uploads

install_arango_repo() {
  install -d -m 755 /usr/share/keyrings
  local asc key opts
  asc=$(mktemp)
  key=$(mktemp)
  curl_fetch https://download.arangodb.com/arangodb312/DEBIAN/Release.key "$asc" \
    || die "failed to download the ArangoDB signing key"
  dearmor_key "$asc" "$key"
  install -m 644 "$key" /usr/share/keyrings/arangodb.gpg
  if gpg_key_expired "$asc"; then
    # The published 3.12 key expired on 2026-03-24 and still signs the repo.
    # signed-by makes apt reject it; trusted=yes keeps the official HTTPS repo usable.
    log "ArangoDB signing key is expired and no replacement is published. Trusting the HTTPS repository without signature checks."
    opts="trusted=yes"
  else
    opts="signed-by=/usr/share/keyrings/arangodb.gpg"
  fi
  rm -f "$asc" "$key"
  printf 'deb [%s] https://download.arangodb.com/arangodb312/DEBIAN/ /\n' "$opts" \
    > /etc/apt/sources.list.d/arangodb.list
  APT_UPDATED=0
}

local_db=0
case "$ARANGO_HOST" in
  127.0.0.1|localhost|::1) local_db=1 ;;
esac
if [[ "$local_db" -eq 1 ]] && dpkg -s arangodb3 >/dev/null 2>&1; then
  log "ArangoDB already installed; not reinitializing data or the root password"
elif [[ "$local_db" -eq 1 ]]; then
  log "Installing ArangoDB 3.12"
  install_arango_repo
  printf 'arangodb3 arangodb3/password password %s\n' "$ARANGO_ROOT_PASSWORD" | debconf-set-selections
  printf 'arangodb3 arangodb3/password_again password %s\n' "$ARANGO_ROOT_PASSWORD" | debconf-set-selections
  printf 'arangodb3 arangodb3/storage_engine select auto\n' | debconf-set-selections
  printf 'arangodb3 arangodb3/upgrade boolean true\n' | debconf-set-selections
  printf 'arangodb3 arangodb3/backup boolean false\n' | debconf-set-selections
  apt_install arangodb3
elif ! command -v arangosh >/dev/null 2>&1; then
  log "Installing arangosh for ${ARANGO_HOST}"
  install_arango_repo
  apt_install arangodb3-client
fi
if [[ "$local_db" -eq 1 ]]; then
  systemctl enable arangodb3
  if ! systemctl is-active --quiet arangodb3; then
    systemctl start arangodb3
  fi
fi

log "Waiting for ArangoDB"
ready=0
for _ in $(seq 1 30); do
  if arangosh \
    --server.endpoint "tcp://${ARANGO_HOST}:${ARANGO_PORT}" \
    --server.username root \
    --server.password "$ARANGO_ROOT_PASSWORD" \
    --server.authentication true \
    --javascript.execute-string 'quit(0)' >/dev/null 2>&1; then
    ready=1
    break
  fi
  sleep 2
done
[[ "$ready" -eq 1 ]] || die "ArangoDB did not accept the root password at ${ARANGO_HOST}:${ARANGO_PORT}"

js=$(mktemp)
trap 'rm -f "$js"' RETURN
chmod 600 "$js"
cat > "$js" <<EOF
const users = require("@arangodb/users");
const user = $(json_quote "$ARANGO_USER");
const password = $(json_quote "$ARANGO_PASSWORD");
const database = $(json_quote "$ARANGO_DATABASE");
const names = db._databases();
if (names.indexOf(database) === -1) {
  db._createDatabase(database);
  print("created database " + database);
} else {
  print("database exists: " + database);
}
if (user === "root") {
  print("app user is root; root password left unchanged");
} else if (!users.exists(user)) {
  users.save(user, password, true);
  users.grantDatabase(user, database, "rw");
  print("created user " + user);
} else {
  users.update(user, password, true);
  users.grantDatabase(user, database, "rw");
  print("updated user " + user);
}
EOF
arangosh \
  --server.endpoint "tcp://${ARANGO_HOST}:${ARANGO_PORT}" \
  --server.username root \
  --server.password "$ARANGO_ROOT_PASSWORD" \
  --server.authentication true \
  --javascript.execute "$js"
rm -f "$js"
trap - RETURN

java_bin=$(java21)
write_if_changed /etc/systemd/system/saigonquest.service 644 >/dev/null <<EOF
[Unit]
Description=Saigon.Town API
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=saigonquest
Group=saigonquest
WorkingDirectory=/opt/saigonquest
EnvironmentFile=/opt/saigonquest/.env
ExecStart=${java_bin} -jar /opt/saigonquest/saigonquest-server.jar
Restart=on-failure
RestartSec=5
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
EOF

write_if_changed /etc/nginx/sites-available/saigonquest-api 644 >/dev/null <<EOF
server {
    listen ${API_NGINX_BIND};
    server_name ${SERVER_DOMAIN};
    client_max_body_size 20m;

    # WebSocket upgrades and SSE streams are not buffered and are not timed out.
    location / {
$(proxy_location "http://127.0.0.1:${APP_PORT}" '$host' "")
    }
}
EOF
ln -sfn /etc/nginx/sites-available/saigonquest-api /etc/nginx/sites-enabled/saigonquest-api
write_traefik_route api "$SERVER_DOMAIN" "http://${API_NGINX_BIND}" >/dev/null
reload_nginx
start_traefik "$traefik_state"
open_edge_ports
systemctl daemon-reload
systemctl enable saigonquest
REMOTE
else
  log "Skipping server init"
fi

log "Uploading the jar and .env"
jar_tmp="/tmp/saigonquest-server-$$.jar"
env_tmp="/tmp/saigonquest-env-$$"
rsync -az -e "$(rsync_rsh)" "$jar" "${SSH_TARGET}:${jar_tmp}"
rsync -az -e "$(rsync_rsh)" "$ROOT/.env.prod" "${SSH_TARGET}:${env_tmp}"
same_site="Lax"
if [[ "$API_MODE" == "direct" ]]; then
  same_site="None"
fi
cors_hosts=""
if [[ -n "$WEB_DOMAIN" ]]; then
  cors_hosts="https://${WEB_DOMAIN},localhost:3000,127.0.0.1:3000"
fi
send_remote "$ROOT" \
  "JAR_TMP=$jar_tmp" \
  "ENV_TMP=$env_tmp" \
  "APP_PORT=$APP_PORT" \
  "WEB_DOMAIN=$WEB_DOMAIN" \
  "API_MODE=$API_MODE" \
  "SAME_SITE=$same_site" \
  "CORS_HOSTS=$cors_hosts" <<'REMOTE'
set -euo pipefail
require_root
: "${JAR_TMP:?}"
: "${ENV_TMP:?}"
: "${APP_PORT:?}"
if ! id saigonquest >/dev/null 2>&1; then
  die "user saigonquest does not exist. Run without --skip-init."
fi
if [[ ! -f /etc/systemd/system/saigonquest.service ]]; then
  die "saigonquest.service is not installed. Run without --skip-init."
fi
install -d -o saigonquest -g saigonquest -m 750 /opt/saigonquest /opt/saigonquest/uploads
install -o saigonquest -g saigonquest -m 640 "$JAR_TMP" /opt/saigonquest/saigonquest-server.jar
install -o saigonquest -g saigonquest -m 640 "$ENV_TMP" /opt/saigonquest/.env
rm -f "$JAR_TMP" "$ENV_TMP"

fcm_env_key=""
fcm_env_val=""
for candidate_key in FCM_SERVICE_ACCOUNT_JSON GOOGLE_APPLICATION_CREDENTIALS; do
  raw_line=$(grep -E "^${candidate_key}=" /opt/saigonquest/.env | tail -n 1 || true)
  if [[ -n "$raw_line" ]]; then
    val=${raw_line#*=}
    val=${val#\"}
    val=${val%\"}
    val=${val#\'}
    val=${val%\'}
    if [[ -n "$val" ]]; then
      fcm_env_key="$candidate_key"
      fcm_env_val="$val"
      break
    fi
  fi
done
if [[ -n "$fcm_env_val" && "$fcm_env_val" != \{* ]]; then
  fcm_name=$(basename "$fcm_env_val")
  fcm_dest="/opt/saigonquest/${fcm_name}"
  if [[ -f "/root/${fcm_name}" ]]; then
    install -o saigonquest -g saigonquest -m 640 "/root/${fcm_name}" "$fcm_dest"
    if [[ "$fcm_env_val" == /root/* ]]; then
      tmp_env=$(mktemp)
      grep -v -E "^${fcm_env_key}=" /opt/saigonquest/.env > "$tmp_env" || true
      printf '%s=%s\n' "$fcm_env_key" "$fcm_dest" >> "$tmp_env"
      install -o saigonquest -g saigonquest -m 640 "$tmp_env" /opt/saigonquest/.env
      rm -f "$tmp_env"
    fi
    log "Installed FCM service account JSON to ${fcm_dest}"
  elif [[ -f "$fcm_env_val" ]]; then
    if [[ "$fcm_env_val" == /opt/saigonquest/* ]]; then
      chown saigonquest:saigonquest "$fcm_env_val"
      chmod 640 "$fcm_env_val"
    fi
  else
    log "warning: FCM credentials file not found at ${fcm_env_val} or /root/${fcm_name}; FCM push will be skipped"
  fi
fi

append_env_if_missing /opt/saigonquest/.env PORT "$APP_PORT"
append_env_if_missing /opt/saigonquest/.env BIND_HOST 127.0.0.1
append_env_if_missing /opt/saigonquest/.env COOKIE_SECURE true
append_env_if_missing /opt/saigonquest/.env TRUST_PROXY true
append_env_if_missing /opt/saigonquest/.env UPLOAD_DIR /opt/saigonquest/uploads
append_env_if_missing /opt/saigonquest/.env CORS_ALLOWED_HOSTS "$CORS_HOSTS"
if [[ "$API_MODE" == "direct" ]]; then
  append_env_if_missing /opt/saigonquest/.env COOKIE_SAMESITE "$SAME_SITE"
  if grep -q '^COOKIE_SAMESITE=Lax$' /opt/saigonquest/.env; then
    log "API_MODE=direct but COOKIE_SAMESITE=Lax, so cross-origin session cookies will not be sent"
  fi
fi
chown saigonquest:saigonquest /opt/saigonquest/.env
chmod 640 /opt/saigonquest/.env
systemctl daemon-reload
systemctl enable saigonquest
systemctl restart saigonquest

ready=0
for _ in $(seq 1 45); do
  code=$(curl -sS -m 5 -o /tmp/saigonquest-health.json -w '%{http_code}' "http://127.0.0.1:${APP_PORT}/api/health" || true)
  if [[ "$code" == "200" ]]; then
    ready=1
    break
  fi
  sleep 1
done
if [[ "$ready" -ne 1 ]]; then
  journalctl -u saigonquest -n 50 --no-pager >&2 || true
  die "API did not become healthy on 127.0.0.1:${APP_PORT}"
fi
log "Local health check passed"
cat /tmp/saigonquest-health.json
rm -f /tmp/saigonquest-health.json
REMOTE

health_check "https://${SERVER_DOMAIN}/api/health" "Server"
