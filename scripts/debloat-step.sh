#!/usr/bin/env bash
# Disable ONE package (for the main user only, fully reversible), then health-check the box.
# If any check fails, the package is re-enabled automatically and the script exits non-zero.
#   Usage: scripts/debloat-step.sh <package>
#   Undo:  adb -s $MIBOX_IP:5555 shell pm enable <package>
. "$(dirname "$0")/lib.sh"
PKG="${1:?usage: debloat-step.sh <package>}"
ensure_connected

if ! box pm list packages -u | grep -qx "package:$PKG"; then echo "  skip  $PKG (not on this box)"; exit 0; fi
if is_disabled "$PKG"; then echo "  skip  $PKG (already disabled)"; exit 0; fi

home_before="$(current_home)"
box logcat -b crash -c

say "Disabling $PKG"
# Clear its data first: 'pm clear' hangs on a package that is already disabled. Capped at 15 s.
box pm clear "$PKG" >/dev/null 2>&1 & cpid=$!
( sleep 15; kill $cpid 2>/dev/null ) >/dev/null 2>&1 & wait $cpid 2>/dev/null || true
box am force-stop "$PKG"
box pm disable-user --user 0 "$PKG" | sed 's/^/  /'
sleep 4

fail=0
check() { if [ -n "$2" ]; then ok "$1"; else bad "$1"; fail=1; fi; }
check "adb still responds"             "$(box echo up)"
check "Xiaomi TV service running"      "$(box pidof xiaomi.tvservice)"
check "system UI running"              "$(box pidof com.android.systemui)"
check "Settings app reachable"         "$(box cmd package resolve-activity --brief -a android.settings.SETTINGS | grep tv.settings)"
box input keyevent KEYCODE_HOME; sleep 3
home_now="$(current_home)"
focus="$(box dumpsys window windows | grep -m1 mCurrentFocus)"
check "home screen ($home_now) shows" "$(echo "$focus" | grep "$home_now")"
[ "$home_now" = "$home_before" ] || warn "home app changed from $home_before to $home_now"
crashes="$(box logcat -b crash -d | grep -E 'FATAL|Process:' | head -5)"
if [ -z "$crashes" ]; then ok "no crashes"; else bad "crashes:"; echo "$crashes"; fail=1; fi

if [ $fail = 1 ]; then
  echo "  >> problem after disabling $PKG, re-enabling it"
  box pm enable "$PKG"
  exit 1
fi
ok "$PKG disabled"
