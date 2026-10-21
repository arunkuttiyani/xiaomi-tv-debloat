#!/usr/bin/env bash
# Steps K and L: build Lite Home, install it, and make it the home screen.
# The stock Google launcher is disabled (not removed) because Android TV always prefers it
# over any other launcher while it is enabled. Undo: scripts/restore.sh --remove-launcher
. "$(dirname "$0")/lib.sh"
ensure_connected

say "Building Lite Home"
"$REPO_ROOT/launcher/build.sh"
APK="$REPO_ROOT/launcher/LiteHome.apk"

say "Installing"
adb -s "$DEV" install -r "$APK" | tail -1
ensure_connected   # Wi-Fi ADB sometimes drops right after an install

say "Opening it as a normal app first"
box logcat -b crash -c
box am start -W -n dev.lite.home/.HomeActivity | grep -E "Status|TotalTime" | sed 's/^/  /'
sleep 2
if box logcat -b crash -d | grep -q "FATAL"; then
  bad "Lite Home crashed on start; leaving the stock launcher in place"; exit 1
fi
ok "Lite Home opens"

say "Making it the home screen"
if is_installed com.google.android.tvlauncher && ! is_disabled com.google.android.tvlauncher; then
  box pm disable-user --user 0 com.google.android.tvlauncher | sed 's/^/  /'
fi
box input keyevent KEYCODE_HOME; sleep 3
home="$(current_home)"
[ "$home" = "dev.lite.home" ] && ok "HOME button opens Lite Home" || { bad "home is still $home"; exit 1; }

say "Pre-compiling it for the fastest start"
box cmd package compile -m speed -f dev.lite.home | sed 's/^/  /' || true
ensure_connected
