# Functions executed on the Debian/Ubuntu host. Do not run this file directly.
# shellcheck shell=bash

log() { printf '==> %s\n' "$*" >&2; }
die() { printf 'error: %s\n' "$*" >&2; exit 1; }

require_root() {
  if [[ "$(id -u)" -ne 0 ]]; then
    die "remote commands must run as root. Configure passwordless sudo for ${SUDO_USER:-the SSH user}."
  fi
}

APT_UPDATED=0
apt_install() {
  export DEBIAN_FRONTEND=noninteractive
  command -v apt-get >/dev/null 2>&1 || die "this deploy supports Debian/Ubuntu only (apt-get not found)"
  if [[ "$APT_UPDATED" -eq 0 ]]; then
    apt-get update
    APT_UPDATED=1
  fi
  apt-get install -y "$@"
}

ensure_base_packages() {
  apt_install ca-certificates curl gnupg rsync nginx ufw
}

java21() {
  local candidate
  for candidate in /usr/lib/jvm/java-21-openjdk-amd64/bin/java /usr/lib/jvm/java-21-openjdk-arm64/bin/java; do
    if [[ -x "$candidate" ]]; then
      printf '%s' "$candidate"
      return 0
    fi
  done
  if command -v java >/dev/null 2>&1; then
    command -v java
    return 0
  fi
  return 1
}

ensure_java21() {
  if java21 >/dev/null 2>&1 && "$(java21)" -version 2>&1 | grep -q 'version "21'; then
    return 0
  fi
  apt_install openjdk-21-jre-headless
  "$(java21)" -version 2>&1 | grep -q 'version "21' || die "Java 21 is required"
}

write_if_changed() {
  local dest=$1
  local mode=$2
  local tmp
  tmp=$(mktemp)
  cat > "$tmp"
  if [[ -f "$dest" ]] && cmp -s "$tmp" "$dest"; then
    rm -f "$tmp"
    printf 'unchanged'
    return 0
  fi
  install -d "$(dirname "$dest")"
  install -m "$mode" "$tmp" "$dest"
  rm -f "$tmp"
  printf 'changed'
}

traefik_arch() {
  case "$(uname -m)" in
    x86_64) printf 'amd64' ;;
    aarch64|arm64) printf 'arm64' ;;
    *) die "unsupported architecture: $(uname -m)" ;;
  esac
}

ensure_nginx_upgrade_map() {
  write_if_changed /etc/nginx/conf.d/saigonquest-upgrade.conf 644 <<'EOF'
map $http_upgrade $connection_upgrade {
    default upgrade;
    ''      close;
}
EOF
}

free_edge_ports() {
  if [[ -e /etc/nginx/sites-enabled/default ]] && grep -Eq 'listen[[:space:]]+[^;]*\b(80|443)\b' /etc/nginx/sites-enabled/default; then
    log "Disabling the nginx default site so Traefik can bind 80 and 443"
    rm -f /etc/nginx/sites-enabled/default
  fi
}

reload_nginx() {
  nginx -t
  systemctl enable nginx
  if systemctl is-active --quiet nginx; then
    systemctl reload nginx
  else
    systemctl restart nginx
  fi
}

ensure_traefik() {
  local version=${TRAEFIK_VERSION:-v3.7.13}
  local dynamic_dir=${TRAEFIK_DYNAMIC_DIR:-/etc/traefik/dynamic}
  local managed=/etc/traefik/.saigonquest-managed
  local version_number=${version#v}
  local arch static_changed unit_changed

  if command -v traefik >/dev/null 2>&1 && [[ ! -f "$managed" ]]; then
    if [[ -n "${TRAEFIK_DYNAMIC_DIR:-}" || -d /etc/traefik/dynamic ]]; then
      install -d -m 755 "$dynamic_dir"
      log "Using the existing Traefik. Routes will be written to ${dynamic_dir} and Traefik will not be restarted."
      printf 'unmanaged'
      return 0
    fi
    die "Traefik is already installed, but no file-provider directory was found. Set TRAEFIK_DYNAMIC_DIR to the directory it watches."
  fi

  arch=$(traefik_arch)
  if ! /usr/local/bin/traefik version 2>/dev/null | grep -q "Version:[[:space:]]*${version_number}"; then
    log "Installing Traefik ${version}"
    local tmp
    tmp=$(mktemp -d)
    curl -fsSL -o "$tmp/traefik.tgz" \
      "https://github.com/traefik/traefik/releases/download/${version}/traefik_${version}_linux_${arch}.tar.gz"
    tar -C "$tmp" -xzf "$tmp/traefik.tgz" traefik
    install -m 755 "$tmp/traefik" /usr/local/bin/traefik
    rm -rf "$tmp"
  else
    log "Traefik ${version} already installed"
  fi

  install -d -m 755 /etc/traefik "$dynamic_dir"
  if [[ ! -f /etc/traefik/acme.json ]]; then
    touch /etc/traefik/acme.json
  fi
  chmod 600 /etc/traefik/acme.json
  touch "$managed"
  [[ -n "${ACME_EMAIL:-}" ]] || die "ACME_EMAIL is required to install Traefik"

  static_changed=$(write_if_changed /etc/traefik/traefik.yml 644 <<EOF
entryPoints:
  web:
    address: ":80"
    http:
      redirections:
        entryPoint:
          to: websecure
          scheme: https
  websecure:
    address: ":443"
    transport:
      respondingTimeouts:
        readTimeout: 0s
        writeTimeout: 0s
        idleTimeout: 3600s

providers:
  file:
    directory: ${dynamic_dir}
    watch: true

certificatesResolvers:
  letsencrypt:
    acme:
      email: ${ACME_EMAIL}
      storage: /etc/traefik/acme.json
      tlsChallenge: {}

log:
  level: INFO
EOF
)
  unit_changed=$(write_if_changed /etc/systemd/system/traefik.service 644 <<'UNIT'
[Unit]
Description=Traefik edge proxy
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
ExecStart=/usr/local/bin/traefik --configFile=/etc/traefik/traefik.yml
Restart=on-failure
RestartSec=3
LimitNOFILE=1048576

[Install]
WantedBy=multi-user.target
UNIT
)
  if [[ "$static_changed" == changed || "$unit_changed" == changed ]]; then
    printf 'changed'
  else
    printf 'unchanged'
  fi
}

start_traefik() {
  local state=$1
  if [[ "$state" == unmanaged ]]; then
    return 0
  fi
  systemctl daemon-reload
  systemctl enable traefik
  if [[ "$state" == changed ]] || ! systemctl is-active --quiet traefik; then
    systemctl restart traefik
  fi
}

write_traefik_route() {
  local name=$1
  local domain=$2
  local upstream=$3
  local dynamic_dir=${TRAEFIK_DYNAMIC_DIR:-/etc/traefik/dynamic}
  install -d -m 755 "$dynamic_dir"
  write_if_changed "${dynamic_dir}/saigonquest-${name}.yml" 644 <<EOF
http:
  routers:
    saigonquest-${name}:
      rule: Host(\`${domain}\`)
      entryPoints:
        - websecure
      service: saigonquest-${name}
      tls:
        certResolver: letsencrypt
  services:
    saigonquest-${name}:
      loadBalancer:
        responseForwarding:
          flushInterval: 100ms
        servers:
          - url: ${upstream}
EOF
}

open_edge_ports() {
  local ssh_port=${SSH_PORT:-22}
  ufw allow "${ssh_port}/tcp" >/dev/null
  ufw allow 80/tcp >/dev/null
  ufw allow 443/tcp >/dev/null
  ufw --force enable >/dev/null
  log "Opened ${ssh_port}/tcp, 80/tcp, and 443/tcp. WebSocket and SSE use 443."
}

proxy_location() {
  local upstream=$1
  local host_header=$2
  local ssl=$3
  cat <<EOF
        proxy_pass ${upstream};
        proxy_http_version 1.1;
        proxy_set_header Host ${host_header};
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection \$connection_upgrade;
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
        proxy_set_header X-Accel-Buffering no;
        ${ssl}
EOF
}

append_env_if_missing() {
  local file=$1
  local key=$2
  local value=$3
  [[ -n "$value" ]] || return 0
  if grep -q "^${key}=" "$file"; then
    return 0
  fi
  printf '%s=%s\n' "$key" "$value" >> "$file"
}

json_quote() {
  local s=$1
  s=${s//\\/\\\\}
  s=${s//\"/\\\"}
  s=${s//$'\n'/\\n}
  s=${s//$'\r'/\\r}
  printf '"%s"' "$s"
}
