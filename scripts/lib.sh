# Shared helpers, sourced by every script. Works with macOS bash 3.2 and Linux bash.
#
# Required:  export MIBOX_IP=192.168.x.y     (the device's IP, see docs/GUIDE.md step C)
# Optional:  export MIBOX_PORT=5555

set -u

MIBOX_IP="${MIBOX_IP:-}"
MIBOX_PORT="${MIBOX_PORT:-5555}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKUP_DIR="$REPO_ROOT/backups"
PKG_DIR="$REPO_ROOT/packages"

if [ -z "$MIBOX_IP" ]; then
  echo "MIBOX_IP is not set. Example:  export MIBOX_IP=192.168.1.50" >&2
  exit 1
fi
DEV="$MIBOX_IP:$MIBOX_PORT"

# Stock home screens across Xiaomi devices. Lite Home replaces whichever of these is active.
KNOWN_LAUNCHERS="com.google.android.tvlauncher com.google.android.apps.tv.launcherx com.mitv.tvhome.atv com.google.android.leanbacklauncher"

# Run a shell command on the device.
# Returns the command's exit status on the device, so 'box pidof x' and 'box test -d y' work as
# conditions. stdin is closed so adb never swallows input meant for a surrounding loop.
box() {
  local out rc
  out="$(adb -s "$DEV" shell "$@" </dev/null)"; rc=$?
  [ -n "$out" ] && printf '%s\n' "$out" | tr -d '\r'
  return $rc
}

# Connect over Wi-Fi ADB (retrying, because Wi-Fi ADB drops now and then) and make sure the device
# has authorised this computer.
ensure_connected() {
  local state="" i
  for i in 1 2 3; do
    adb connect "$DEV" >/dev/null 2>&1 || true
    state="$(adb -s "$DEV" get-state 2>/dev/null || true)"
    [ "$state" = "device" ] && return 0
    sleep 2
  done
  echo "Cannot use the device at $DEV (state: ${state:-not found})." >&2
  echo "Check the IP, that USB/ADB debugging is on, and accept the 'Allow USB debugging' prompt on the TV." >&2
  exit 1
}

# ---- device detection ---------------------------------------------------------------
# Sets: DEV_BRAND DEV_MODEL DEV_CODENAME DEV_SDK DEV_ANDROID DEV_CHIP DEV_FAMILY DEV_HAS_PATCHWALL
#   DEV_FAMILY: android-tv | google-tv
#   DEV_CHIP:   amlogic | mediatek | other
detect_device() {
  DEV_BRAND="$(box getprop ro.product.brand)"
  DEV_MODEL="$(box getprop ro.product.model)"
  DEV_CODENAME="$(box getprop ro.product.device)"
  DEV_SDK="$(box getprop ro.build.version.sdk)"
  DEV_ANDROID="$(box getprop ro.build.version.release)"
  local hw; hw="$(box getprop ro.hardware) $(box getprop ro.board.platform)"
  case "$hw" in
    *amlogic*|*meson*|*gx*|*g12*|*sm1*|*sc2*|*s4*) DEV_CHIP=amlogic;;
    *mt*|*mediatek*|*m7*|*m8*)                     DEV_CHIP=mediatek;;
    *) if box test -d /sys/class/amhdmitx; then DEV_CHIP=amlogic; else DEV_CHIP=other; fi;;
  esac
  local all; all="$(box pm list packages)"   # installed for this user (enabled or disabled)
  if echo "$all" | grep -qx "package:com.google.android.apps.tv.launcherx"; then DEV_FAMILY=google-tv; else DEV_FAMILY=android-tv; fi
  if echo "$all" | grep -q "^package:com.mitv.tvhome"; then DEV_HAS_PATCHWALL=yes; else DEV_HAS_PATCHWALL=no; fi
}

is_xiaomi() { echo "$DEV_BRAND" | grep -qiE 'xiaomi|redmi|mi$'; }

# Package lists that apply to this device, in order (file names inside packages/).
lists_for_device() {
  echo common.txt
  [ "$DEV_HAS_PATCHWALL" = yes ] && echo patchwall.txt
  return 0
}

# ---- package lists --------------------------------------------------------------------
# Format: one package per line, '#' starts a comment, '*' is a wildcard (e.g. android.autoinstalls.config.*).

# Active (uncommented) entries of a list, wildcards left as-is.
read_list() { sed -e 's/#.*//' -e 's/[[:space:]]//g' "$1" | grep -v '^$' || true; }

# Every entry named in a list, including commented-out ones (used to undo).
all_in_list() {
  sed -e 's/^#[[:space:]]*//' "$1" | awk '{print $1}' | grep -E '^[a-z][a-z0-9_]*(\.[a-z0-9_*]+)+$' || true
}

# Expand entries (read from stdin) against the packages installed for this user (enabled or
# disabled): wildcards become every matching package, plain names are kept only if present.
# --all also matches packages that were removed for the user (still on the system partition).
expand_on_device() {
  local all entry re flag=""
  [ "${1:-}" = "--all" ] && flag="-u"
  all="$(box pm list packages $flag | sed 's/^package://')"
  while read -r entry; do
    [ -n "$entry" ] || continue
    re="^$(echo "$entry" | sed -e 's/\./\\./g' -e 's/\*/.*/g')\$"
    echo "$all" | grep -E "$re" || true
  done | sort -u
}

is_installed() { box pm list packages | grep -qx "package:$1"; }
is_disabled()  { box pm list packages -d | grep -qx "package:$1"; }

# Package that currently answers the HOME button.
current_home() {
  box cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME \
    | tail -1 | cut -d/ -f1
}

# Every package offering a HOME screen, except Lite Home and Android's settings fallback.
home_candidates() {
  box cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME \
    | grep -oE '^ +[a-z][a-zA-Z0-9_.]+/' | tr -d ' /' \
    | grep -vE '^(dev\.lite\.home|com\.android\.tv\.settings|com\.android\.settings)$' | sort -u || true
}

# Core processes that are running right now. Health checks compare this before and after a change,
# so they work on any model: whatever was running must still be running.
CORE_PROCESSES="com.android.systemui xiaomi.tvservice mitv.service com.android.bluetooth com.droidlogic com.google.android.tv.remote.service"
core_running() {
  local p
  for p in $CORE_PROCESSES; do box pidof "$p" >/dev/null 2>&1 && echo "$p"; done
  return 0
}

# Ask before doing something. Skipped (answers yes) when ASSUME_YES=1 or --yes was given.
ASSUME_YES="${ASSUME_YES:-0}"
for _a in "$@"; do [ "$_a" = "--yes" ] && ASSUME_YES=1; done
confirm() {
  [ "$ASSUME_YES" = 1 ] && return 0
  printf '%s [y/N] ' "$1"
  local reply; read -r reply
  case "$reply" in y|Y|yes|YES) return 0;; *) return 1;; esac
}

say()  { printf '\n\033[1m%s\033[0m\n' "$*"; }
ok()   { printf '  \033[32mOK\033[0m   %s\n' "$*"; }
bad()  { printf '  \033[31mFAIL\033[0m %s\n' "$*"; }
warn() { printf '  \033[33mWARN\033[0m %s\n' "$*"; }
