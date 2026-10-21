#!/usr/bin/env bash
# Step D: connect to the box over Wi-Fi ADB and confirm it is a Mi Box 4. Changes nothing.
. "$(dirname "$0")/lib.sh"

say "Connecting to $DEV"
ensure_connected
ok "connected and authorised"

model="$(box getprop ro.product.model)"
say "Device"
echo "  model:          $model"
echo "  brand:          $(box getprop ro.product.brand)"
echo "  android:        $(box getprop ro.build.version.release) (SDK $(box getprop ro.build.version.sdk))"
echo "  build:          $(box getprop ro.build.fingerprint)"
echo "  security patch: $(box getprop ro.build.version.security_patch)"
echo "  home screen:    $(current_home)"
echo "  free storage:   $(box df -h /data | tail -1 | awk '{print $4}')"

if [ "$model" != "MIBOX4" ]; then
  warn "This guide was tested on the Mi Box 4 (MIBOX4). Yours reports '$model'."
  warn "Package names and safe lists may differ. Read docs/GUIDE.md before going further."
fi
