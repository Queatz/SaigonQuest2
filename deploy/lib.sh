# Shared local helpers for deploy/web.sh and deploy/server.sh.
# shellcheck shell=bash

log() { printf '==> %s\n' "$*"; }
die() { printf 'error: %s\n' "$*" >&2; exit 1; }

json_quote() {
  local s=$1
  s=${s//\\/\\\\}
  s=${s//\"/\\\"}
  s=${s//$'\n'/\\n}
  s=${s//$'\r'/\\r}
  s=${s//$'\t'/\\t}
  printf '"%s"' "$s"
}

require_var() {
  local name=$1
  if [[ -z "${!name:-}" ]]; then
    die "missing ${name}"
  fi
}

reject_newlines() {
  local name=$1
  local value=${!name:-}
  if [[ "$value" == *$'\n'* ]]; then
    die "${name} must not contain newlines"
  fi
}

validate_domain() {
  local domain=$1
  [[ "$domain" =~ ^[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?(\.[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$ ]] \
    || die "invalid domain: ${domain}"
}

assert_safe_dir() {
  local path=$1
  local label=$2
  [[ "$path" == /* ]] || die "${label} must be an absolute path"
  case "$path" in
    /|/bin|/etc|/home|/opt|/tmp|/usr|/var|/var/www) die "${label} is too broad: ${path}" ;;
  esac
}

load_env_file() {
  local file=$1
  [[ -f "$file" ]] || die "missing ${file}"
  set -a
  # shellcheck disable=SC1090
  source "$file"
  set +a
}

build_ssh() {
  SSH_PORT=${SSH_PORT:-22}
  SSH_KEY=${SSH_KEY:-}
  SSH_TARGET="${SSH_USER}@${SSH_HOST}"
  SSH_OPTS=(-o BatchMode=yes -o StrictHostKeyChecking=accept-new -o ConnectTimeout=20 -p "$SSH_PORT")
  if [[ -n "$SSH_KEY" ]]; then
    SSH_OPTS+=(-i "$SSH_KEY")
  fi
  ssh_check
}

ssh_check() {
  local err rc=0
  err=$(ssh "${SSH_OPTS[@]}" "$SSH_TARGET" true 2>&1) || rc=$?
  if [[ "$rc" -eq 0 ]]; then
    return 0
  fi
  printf '%s\n' "$err" >&2
  if [[ "$err" == *"IDENTIFICATION HAS CHANGED"* || "$err" == *"Host key verification failed"* ]]; then
    die "SSH host key for ${SSH_HOST} is not trusted.
If this machine was rebuilt, confirm the new fingerprint, remove the old key, and rerun:
  ssh-keygen -f \"\$HOME/.ssh/known_hosts\" -R '${SSH_HOST}'"
  fi
  die "SSH to ${SSH_TARGET} failed"
}

curl_fetch() {
  local url=$1 dest=$2
  local attempt
  for attempt in 1 2 3 4 5; do
    if curl -fsSL --http1.1 --connect-timeout 20 --max-time 300 -o "$dest" "$url" && [[ -s "$dest" ]]; then
      return 0
    fi
    rm -f "$dest"
    log "download failed (${attempt}/5): ${url}"
    sleep $((attempt * 2))
  done
  return 1
}

stage_traefik_tarball() {
  local version=${TRAEFIK_VERSION:-v3.7.13}
  local version_number=${version#v}
  local machine arch dest url sums want name remote
  [[ "$version" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || die "invalid TRAEFIK_VERSION: ${version}"
  machine=$(ssh "${SSH_OPTS[@]}" "$SSH_TARGET" uname -m) || die "could not read the remote architecture"
  case "$machine" in
    x86_64) arch=amd64 ;;
    aarch64|arm64) arch=arm64 ;;
    *) die "unsupported remote architecture: ${machine}" ;;
  esac
  if ssh "${SSH_OPTS[@]}" "$SSH_TARGET" "test -x /usr/local/bin/traefik && /usr/local/bin/traefik version 2>/dev/null | grep -q 'Version:[[:space:]]*${version_number}'"; then
    log "Traefik ${version} already installed on the host"
    TRAEFIK_TARBALL=
    return 0
  fi
  name="traefik_${version}_linux_${arch}.tar.gz"
  dest=$(mktemp)
  url="https://github.com/traefik/traefik/releases/download/${version}/${name}"
  log "Downloading Traefik ${version} (${arch}) locally"
  curl_fetch "$url" "$dest" || die "failed to download Traefik ${version}"
  sums=$(mktemp)
  if curl_fetch "https://github.com/traefik/traefik/releases/download/${version}/traefik_${version}_checksums.txt" "$sums"; then
    want=$(awk -v f="$name" '$2 == f || $2 == "*" f {print $1; exit}' "$sums")
    if [[ -n "$want" ]]; then
      printf '%s  %s\n' "$want" "$dest" | sha256sum -c - >/dev/null || die "Traefik checksum mismatch"
    else
      log "checksum file has no entry for ${name}"
    fi
  else
    log "could not download Traefik checksums; the tarball will be used without verification"
  fi
  rm -f "$sums"
  remote="/tmp/saigonquest-${name}"
  ssh "${SSH_OPTS[@]}" "$SSH_TARGET" "cat > $(printf '%q' "$remote")" < "$dest" || die "failed to upload Traefik"
  rm -f "$dest"
  TRAEFIK_TARBALL=$remote
}

rsync_rsh() {
  printf 'ssh '
  printf '%q ' "${SSH_OPTS[@]}"
}

run_remote_stdin() {
  local -a env_args=("$@")
  local -a remote
  if [[ "$SSH_USER" == "root" ]]; then
    remote=(env "${env_args[@]}" bash -s)
  else
    remote=(sudo -n env "${env_args[@]}" bash -s)
  fi
  ssh "${SSH_OPTS[@]}" "$SSH_TARGET" "${remote[@]}"
}

send_remote() {
  local root=$1
  shift
  {
    cat "$root/deploy/remote-common.sh"
    cat
  } | run_remote_stdin "$@"
}

health_check() {
  local url=$1
  local label=$2
  local attempt body code
  for attempt in $(seq 1 40); do
    body=$(curl -sS -m 20 -w $'\n%{http_code}' "$url" || true)
    code=$(printf '%s\n' "$body" | tail -n 1)
    body=$(printf '%s\n' "$body" | sed '$d')
    if [[ "$code" == "200" ]]; then
      log "${label} is up: ${url}"
      if [[ ${#body} -lt 500 ]]; then
        printf '%s\n' "$body"
      else
        printf 'HTTP 200 (%s bytes)\n' "${#body}"
      fi
      return 0
    fi
    printf 'waiting for %s (%s/40, HTTP %s)\n' "$url" "$attempt" "${code:-none}"
    sleep 3
  done
  printf 'error: %s health check failed for %s (last HTTP %s)\n' "$label" "$url" "${code:-none}" >&2
  printf '%s\n' "$body" >&2
  printf 'Check DNS, that ports 80 and 443 are reachable, and journalctl on the host.\n' >&2
  return 1
}
