#!/usr/bin/env bash
# Step O: confirm the device is healthy. With --reboot, reboots first and checks it comes back.
. "$(dirname "$0")/lib.sh"
ensure_connected

if [ "${1:-}" = "--reboot" ]; then
  say "Rebooting (takes about a minute)"
  box reboot || true
  sleep 45
  for i in $(seq 1 12); do
    adb connect "$DEV" >/dev/null 2>&1
    [ "$(adb -s "$DEV" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break
    sleep 10
  done
  sleep 15
fi
ensure_connected
detect_device

say "Health: $DEV_BRAND $DEV_MODEL"
fail=0
check() { if [ -n "$2" ]; then ok "$1"; else bad "$1"; fail=1; fi; }
check "boot completed" "$(box getprop sys.boot_completed | grep 1)"
check "system UI running" "$(box pidof com.android.systemui)"
if is_installed mitv.service; then
  check "Xiaomi TV service running" "$(box ps -A -o NAME | grep -E '^(xiaomi\.tvservice|mitv\.service)$')"
fi
home="$(current_home)"
check "home screen is $home" "$(box dumpsys window windows | grep -m1 mCurrentFocus | grep "$home")"
crashes="$(box logcat -b crash -d | grep -c FATAL)"
[ "$crashes" = "0" ] && ok "no crashes since boot" || { bad "$crashes crashes since boot (adb logcat -b crash)"; fail=1; }
box pidof eu.faircode.netguard >/dev/null && ok "NetGuard firewall running" || warn "NetGuard not running"

say "Output"
if [ "$DEV_CHIP" = amlogic ] && box test -r /sys/class/amhdmitx/amhdmitx0/config; then
  echo "  HDMI mode:   $(box cat /sys/class/amhdmitx/amhdmitx0/config | head -1)   (VIC 4=720p60, 16=1080p60, 97=2160p60)"
  echo "  colour:      $(box cat /sys/class/amhdmitx/amhdmitx0/attr)"
  echo "  display:     $(box cat /sys/class/amhdmitx/amhdmitx0/edid | grep -m1 'Product Name' | sed 's/.*: //')"
  echo "  modes:       $(box cat /sys/class/amhdmitx/amhdmitx0/disp_cap | tr '\n' ' ')"
else
  echo "  $(box dumpsys display | grep -m1 -oE 'mActiveModeId=[0-9]+')  $(box dumpsys display | grep -m1 -oE '[0-9]+ x [0-9]+, modeId [0-9]+')"
  echo "  (detailed HDMI readout is only available on Amlogic devices)"
fi

say "Resources"
echo "  RAM free:     $(box cat /proc/meminfo | awk '/MemAvailable/ {printf "%d MB", $2/1024}')"
echo "  storage free: $(box df -h /data | tail -1 | awk '{print $4}')"
exit $fail
