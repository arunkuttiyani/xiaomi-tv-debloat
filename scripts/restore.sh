#!/usr/bin/env bash
# Step P: undo. Re-enables every package named in packages/common.txt, patchwall.txt and
# optional.txt (commented or not), every home screen that step L disabled, and puts the
# screensaver back.
#   scripts/restore.sh                    # keep Lite Home installed (the stock home screen comes back)
#   scripts/restore.sh --remove-launcher  # also uninstall Lite Home
# A factory reset also undoes everything in this repo.
. "$(dirname "$0")/lib.sh"
ensure_connected

say "Re-enabling packages"
pkgs="$( { for f in common.txt patchwall.txt optional.txt; do all_in_list "$PKG_DIR/$f"; done
           echo "$KNOWN_LAUNCHERS" | tr ' ' '\n'
           cat "$BACKUP_DIR/launchers-disabled.txt" 2>/dev/null; } | expand_on_device )"
# Packages that were already disabled in the first backup (step E) were that way before this repo
# touched the device, so they stay disabled.
first="$(ls -d "$BACKUP_DIR"/*/ 2>/dev/null | sort | head -1)"
was_disabled=""
[ -n "$first" ] && [ -f "$first/packages-disabled.txt" ] && was_disabled="$(sed 's/^package://' "$first/packages-disabled.txt")"
[ -n "$first" ] || warn "no backup found; re-enabling everything named in the lists"
for p in $pkgs; do
  if echo "$was_disabled" | grep -qx "$p"; then echo "  keep disabled $p (was already disabled in $(basename "$first"))"; continue; fi
  if is_disabled "$p"; then box pm enable "$p" | sed 's/^/  /'; fi
done

say "Screensaver"
prev="$BACKUP_DIR/tweaks-previous.txt"
sc=""; sd=""
if [ -f "$prev" ]; then
  sc="$(grep '^screensaver_components=' "$prev" | cut -d= -f2-)"
  sd="$(grep '^screensaver_default_component=' "$prev" | cut -d= -f2-)"
fi
if [ -z "$sc" ] || [ "$sc" = "null" ]; then
  # no saved value: fall back to the device's stock screensaver (PatchWall's, then Google's)
  dreams="$(box cmd package query-services --brief -a android.service.dreams.DreamService | grep -oE '[a-z][a-zA-Z0-9_.]+/[a-zA-Z0-9_.$]+')"
  sc="$(echo "$dreams" | grep -m1 '^com.mitv.dream/' || echo "$dreams" | grep -m1 '^com.google.android.backdrop/' || echo com.android.dreams.basic/.Colors)"
  sd="$sc"
  warn "no saved screensaver setting; using $sc. Change it in Settings if it doesn't start."
fi
box settings put secure screensaver_components "$sc"
[ -n "$sd" ] && [ "$sd" != "null" ] && box settings put secure screensaver_default_component "$sd"
ok "screensaver: $(box settings get secure screensaver_components)"

if [ "${1:-}" = "--remove-launcher" ]; then
  say "Removing Lite Home"
  box pm uninstall dev.lite.home | sed 's/^/  /' || true
fi
box input keyevent KEYCODE_HOME
ok "home screen: $(current_home)"
