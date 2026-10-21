# Shared helpers, sourced by every script. Works with macOS bash 3.2 and Linux bash.
#
# Required:  export MIBOX_IP=192.168.x.y     (the box's IP, see docs/GUIDE.md step C)
# Optional:  export MIBOX_PORT=5555

set -u

MIBOX_IP="${MIBOX_IP:-}"
MIBOX_PORT="${MIBOX_PORT:-5555}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKUP_DIR="$REPO_ROOT/backups"

if [ -z "$MIBOX_IP" ]; then
  echo "MIBOX_IP is not set. Example:  export MIBOX_IP=192.168.1.50" >&2
  exit 1
fi
DEV="$MIBOX_IP:$MIBOX_PORT"

# Run a shell command on the box.
box() { adb -s "$DEV" shell "$@" | tr -d '\r'; }

# Connect over Wi-Fi ADB (retrying, because Wi-Fi ADB drops now and then) and make sure the box
# has authorised this computer.
ensure_connected() {
  local state="" i
  for i in 1 2 3; do
    adb connect "$DEV" >/dev/null 2>&1 || true
    state="$(adb -s "$DEV" get-state 2>/dev/null || true)"
    [ "$state" = "device" ] && return 0
    sleep 2
  done
  echo "Cannot use the box at $DEV (state: ${state:-not found})." >&2
  echo "Check the IP, that network debugging is on, and accept the 'Allow USB debugging' prompt on the TV." >&2
  exit 1
}

# Print the packages in a list file: one per line, '#' starts a comment.
read_list() { sed -e 's/#.*//' -e 's/[[:space:]]//g' "$1" | grep -v '^$' || true; }

# Every package named in a list file, including commented-out ones (used to undo).
all_in_list() {
  sed -e 's/^#[[:space:]]*//' "$1" | awk '{print $1}' | grep -E '^[a-z][a-z0-9_]*(\.[a-z0-9_]+)+$' || true
}

is_installed() { box pm list packages | grep -qx "package:$1"; }
is_disabled()  { box pm list packages -d | grep -qx "package:$1"; }

# Package that currently answers the HOME button.
current_home() {
  box cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME \
    | tail -1 | cut -d/ -f1
}

say()  { printf '\n\033[1m%s\033[0m\n' "$*"; }
ok()   { printf '  \033[32mOK\033[0m   %s\n' "$*"; }
bad()  { printf '  \033[31mFAIL\033[0m %s\n' "$*"; }
warn() { printf '  \033[33mWARN\033[0m %s\n' "$*"; }
