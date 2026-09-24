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
