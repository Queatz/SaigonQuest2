#!/usr/bin/env bash
# Tail production API logs from the systemd journal.
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
# shellcheck source=deploy/lib.sh
source "$ROOT/deploy/lib.sh"

usage() {
  cat <<'EOF'
Usage: deploy/logs.sh [-n LINES]

Follows saigonquest journal logs on the production host.

  -n LINES   Print the latest LINES lines and exit instead of following.
  -h, --help Show this help.

Config: deploy/server.env
EOF
}

LINES=
FOLLOW=1
while [[ $# -gt 0 ]]; do
  case "$1" in
    -n)
      [[ $# -ge 2 ]] || die "-n requires a line count"
      LINES=$2
      FOLLOW=0
      shift 2
      ;;
    -n*)
      LINES=${1#-n}
      FOLLOW=0
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      die "unknown argument: $1"
      ;;
  esac
done

if [[ -n "$LINES" ]]; then
  [[ "$LINES" =~ ^[1-9][0-9]*$ ]] || die "-n must be a positive integer"
fi

cd "$ROOT"
load_env_file "$ROOT/deploy/server.env"
require_var SSH_HOST
require_var SSH_USER
build_ssh

remote_journal() {
  local -a cmd=(journalctl -u saigonquest --no-pager "$@")
  local -a ssh_cmd=("${SSH_OPTS[@]}")
  if [[ "$FOLLOW" -eq 1 ]]; then
    ssh_cmd+=(-t)
  fi
  if [[ "$SSH_USER" == "root" ]]; then
    ssh "${ssh_cmd[@]}" "$SSH_TARGET" "${cmd[@]}"
  else
    ssh "${ssh_cmd[@]}" "$SSH_TARGET" sudo -n "${cmd[@]}"
  fi
}

if [[ "$FOLLOW" -eq 1 ]]; then
  remote_journal -f
else
  remote_journal -n "$LINES"
fi
