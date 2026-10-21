#!/usr/bin/env bash
# Step D: connect over Wi-Fi ADB and identify the device. Changes nothing.
. "$(dirname "$0")/lib.sh"

say "Connecting to $DEV"
ensure_connected
ok "connected and authorised"
detect_device

say "Device"
echo "  brand / model:  $DEV_BRAND $DEV_MODEL ($DEV_CODENAME)"
echo "  android:        $DEV_ANDROID (SDK $DEV_SDK)"
echo "  type:           $DEV_FAMILY$( [ "$DEV_HAS_PATCHWALL" = yes ] && echo ' + PatchWall')"
echo "  chip:           $DEV_CHIP"
echo "  build:          $(box getprop ro.build.fingerprint)"
echo "  security patch: $(box getprop ro.build.version.security_patch)"
echo "  home screen:    $(current_home)"
echo "  free storage:   $(box df -h /data | tail -1 | awk '{print $4}')"

say "Package lists that apply"
for f in $(lists_for_device); do echo "  packages/$f"; done
echo "  packages/optional.txt  (only what you uncomment)"

if ! is_xiaomi; then
  warn "This is not a Xiaomi device. The lists are written for Xiaomi; read docs/DEVICES.md first."
fi
if [ "$DEV_FAMILY" = google-tv ]; then
  warn "Google TV: replacing the home screen (step L) is untested here. See docs/DEVICES.md."
fi
grep -q "| $DEV_MODEL " "$REPO_ROOT/docs/DEVICES.md" 2>/dev/null \
  || warn "$DEV_MODEL isn't in the tested-device table yet. Please report your results (docs/DEVICES.md)."
