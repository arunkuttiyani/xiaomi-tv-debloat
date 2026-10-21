#!/usr/bin/env bash
# Steps I and J: small settings fixes. Previous values are saved to backups/tweaks-previous.txt.
#   - screensaver: point it at Android's basic "Colors" screensaver (Backdrop is disabled in step G)
#   - "Don't keep activities" OFF: if it is on, every app is destroyed when you leave it
#   - trim app caches to free storage
. "$(dirname "$0")/lib.sh"
ensure_connected
mkdir -p "$BACKUP_DIR"
prev="$BACKUP_DIR/tweaks-previous.txt"
if [ ! -f "$prev" ]; then
  {
    echo "screensaver_components=$(box settings get secure screensaver_components)"
    echo "screensaver_default_component=$(box settings get secure screensaver_default_component)"
    echo "always_finish_activities=$(box settings get global always_finish_activities)"
  } > "$prev"
fi

say "Screensaver"
box settings put secure screensaver_components com.android.dreams.basic/.Colors
box settings put secure screensaver_default_component com.android.dreams.basic/.Colors
ok "screensaver: $(box settings get secure screensaver_components)"

say "Don't keep activities"
box settings put global always_finish_activities 0
ok "always_finish_activities=$(box settings get global always_finish_activities)"

say "Clearing app caches"
before="$(box df /data | tail -1 | awk '{print $4}')"
box pm trim-caches 64G
after="$(box df /data | tail -1 | awk '{print $4}')"
ok "freed about $(( (after - before) / 1024 )) MB (free now: $(box df -h /data | tail -1 | awk '{print $4}'))"
