#!/usr/bin/env bash
# Step P: undo. Re-enables every package from packages/debloat.txt and packages/optional.txt plus
# the stock launcher, and puts the screensaver back.
#   scripts/restore.sh                    # keep Lite Home installed (stock launcher becomes home again)
#   scripts/restore.sh --remove-launcher  # also uninstall Lite Home
# A factory reset (Settings > Device Preferences > Reset) also undoes everything in this repo.
. "$(dirname "$0")/lib.sh"
ensure_connected

say "Re-enabling packages"
for p in $(all_in_list "$REPO_ROOT/packages/debloat.txt"; all_in_list "$REPO_ROOT/packages/optional.txt"; echo com.google.android.tvlauncher); do
  if is_disabled "$p"; then box pm enable "$p" | sed 's/^/  /'; fi
done

say "Screensaver"
prev="$BACKUP_DIR/tweaks-previous.txt"
if [ -f "$prev" ]; then
  sc="$(grep '^screensaver_components=' "$prev" | cut -d= -f2-)"
  sd="$(grep '^screensaver_default_component=' "$prev" | cut -d= -f2-)"
  [ -n "$sc" ] && [ "$sc" != "null" ] && box settings put secure screensaver_components "$sc"
  [ -n "$sd" ] && [ "$sd" != "null" ] && box settings put secure screensaver_default_component "$sd"
else
  box settings put secure screensaver_components com.google.android.backdrop/.Backdrop
  box settings put secure screensaver_default_component com.google.android.backdrop/.Backdrop
fi
ok "screensaver: $(box settings get secure screensaver_components)"

if [ "${1:-}" = "--remove-launcher" ]; then
  say "Removing Lite Home"
  box pm uninstall dev.lite.home | sed 's/^/  /' || true
fi
box input keyevent KEYCODE_HOME
ok "home screen: $(current_home)"
