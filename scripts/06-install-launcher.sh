#!/usr/bin/env bash
# Steps K and L: build Lite Home, install it, and make it the home screen.
# Every other home screen (Android TV, Google TV or PatchWall launcher) is disabled, not removed,
# because Android always prefers the built-in one while it's enabled. The ones disabled are
# recorded in backups/launchers-disabled.txt. Undo: scripts/restore.sh [--remove-launcher]
. "$(dirname "$0")/lib.sh"
ensure_connected
detect_device

if [ "$DEV_FAMILY" = google-tv ]; then
  warn "Google TV device: replacing its home screen hasn't been tested on hardware by this project."
  warn "Lite Home will be tried first, and the Google TV home is only disabled if Lite Home starts cleanly."
  confirm "Continue?" || exit 0
fi

say "Building Lite Home"
"$REPO_ROOT/launcher/build.sh"
APK="$REPO_ROOT/launcher/LiteHome.apk"

say "Installing"
adb -s "$DEV" install -r "$APK" | tail -1
ensure_connected   # Wi-Fi ADB sometimes drops right after an install

say "Opening it as a normal app first"
box logcat -b crash -c
box am start -W -n dev.lite.home/.HomeActivity | grep -E "Status|TotalTime" | sed 's/^/  /'
sleep 3
if box logcat -b crash -d | grep -q "FATAL"; then
  bad "Lite Home crashed on start; leaving the current home screen in place"; exit 1
fi
ok "Lite Home opens"

say "Making it the home screen"
others="$(home_candidates)"
mkdir -p "$BACKUP_DIR"
for p in $others; do
  if is_disabled "$p"; then continue; fi
  echo "  disabling home screen $p"
  box pm disable-user --user 0 "$p" | sed 's/^/    /'
  grep -qx "$p" "$BACKUP_DIR/launchers-disabled.txt" 2>/dev/null || echo "$p" >> "$BACKUP_DIR/launchers-disabled.txt"
done
box input keyevent KEYCODE_HOME; sleep 3
home="$(current_home)"
if [ "$home" = "dev.lite.home" ]; then
  ok "HOME button opens Lite Home"
else
  bad "home is still '$home'. Re-enabling the previous home screens."
  for p in $others; do box pm enable "$p" >/dev/null; done
  exit 1
fi

say "Pre-compiling it for the fastest start"
box cmd package compile -m speed -f dev.lite.home | sed 's/^/  /' || true
ensure_connected
