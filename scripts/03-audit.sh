#!/usr/bin/env bash
# Step F: read-only privacy audit.
#  - finds every Xiaomi / partner package on the device and says which list covers it
#  - shows Xiaomi processes, open internet connections and per-app traffic
#  - checks whether NetGuard is firewalling the system user
. "$(dirname "$0")/lib.sh"
ensure_connected
detect_device
echo "$DEV_BRAND $DEV_MODEL ($DEV_CODENAME), Android $DEV_ANDROID, $DEV_FAMILY, $DEV_CHIP"

# Which list names each package (wildcards expanded against this device once, up front).
LIST_common="$(all_in_list "$PKG_DIR/common.txt" | expand_on_device --all)"
LIST_patchwall="$(all_in_list "$PKG_DIR/patchwall.txt" | expand_on_device --all)"
LIST_optional="$(all_in_list "$PKG_DIR/optional.txt" | expand_on_device --all)"
LIST_keep="$(read_list "$PKG_DIR/keep.txt" | expand_on_device --all)"
listed_in() {
  echo "$LIST_common"    | grep -qx "$1" && { echo "common.txt"; return; }
  echo "$LIST_patchwall" | grep -qx "$1" && { echo "patchwall.txt"; return; }
  echo "$LIST_optional"  | grep -qx "$1" && { echo "optional.txt"; return; }
  echo "$LIST_keep"      | grep -qx "$1" && { echo "keep (protected)"; return; }
  echo "UNREVIEWED"
}

say "Xiaomi, Mi, partner and ad packages on this device"
pattern='^(com\.xiaomi|com\.mitv|com\.miui|mitv\.|com\.xm\.|com\.duokan|com\.mi\.|tv\.alphonso|android\.autoinstalls|com\.baidu|com\.tencent)'
found="$(box pm list packages -u | sed 's/^package://' | grep -E "$pattern" | sort)"
unreviewed=0
for p in $found; do
  if is_disabled "$p"; then st=disabled; elif is_installed "$p"; then st=ENABLED; else st="removed"; fi
  where="$(listed_in "$p")"
  [ "$where" = UNREVIEWED ] && unreviewed=$((unreviewed + 1))
  printf '  %-48s %-9s %s\n' "$p" "$st" "$where"
done
[ $unreviewed -gt 0 ] && warn "$unreviewed package(s) aren't on any list yet. Look them up before disabling, and please report them (docs/DEVICES.md)."

say "Xiaomi processes running"
box ps -A -o NAME | grep -iE 'xiaomi|mitv|miui|alphonso' | sed 's/^/  /' || echo "  none"

# Map UIDs to package names so the network tables are readable.
uidmap="$(box pm list packages -U | sed 's/^package://' | awk '{split($2, u, ":"); print u[2], $1}')"
name_of() { echo "$uidmap" | awk -v u="$1" '$1==u {printf "%s%s", sep, $2; sep=","}'; }
label_uid() {
  case "$1" in
    0) echo "root";; 1000) echo "system (incl. mitv.service)";; 1020) echo "mdnsd";; 1051) echo "dns";; 2000) echo "adb shell";;
    *) n="$(name_of "$1")"; echo "${n:-uid $1}";;
  esac
}
hex_ip() { # little-endian hex from /proc/net/tcp -> dotted quad (IPv4 or IPv4-mapped IPv6)
  local h="${1: -8}"
  printf '%d.%d.%d.%d' "0x${h:6:2}" "0x${h:4:2}" "0x${h:2:2}" "0x${h:0:2}"
}

say "Open internet connections right now"
box 'cat /proc/net/tcp /proc/net/tcp6' 2>/dev/null | awk 'NR>1 && $4=="01" {print $3, $8}' | sort -u | while read -r rem uid; do
  ip="$(hex_ip "${rem%%:*}")"; port=$((16#${rem##*:}))
  case "$ip" in 127.*|0.0.0.0) continue;; esac
  printf '  %-18s port %-6s %s\n' "$ip" "$port" "$(label_uid "$uid")"
done

say "Traffic since boot, by app (received / sent)"
if box test -r /proc/net/xt_qtaguid/stats; then
  box cat /proc/net/xt_qtaguid/stats \
    | awk 'NR>1 {rx[$4]+=$6; tx[$4]+=$8} END {for (u in rx) if (rx[u]+tx[u]>0) print u, rx[u], tx[u]}' \
    | sort -k2 -n -r | while read -r uid rx tx; do
        printf '  %10s KB / %8s KB  %s\n' "$((rx/1024))" "$((tx/1024))" "$(label_uid "$uid")"
      done
else
  echo "  not available on Android $DEV_ANDROID (Android 10+ removed these counters)."
  echo "  NetGuard's own traffic log (step N) shows the same information."
fi

say "NetGuard firewall"
if box pidof eu.faircode.netguard >/dev/null; then
  uids="$(box dumpsys connectivity | grep -oE 'Uids: <[^>]*>' | head -1)"
  ok "NetGuard is running. UIDs routed into its block list: ${uids:-none}"
  echo "$uids" | grep -q "1000-1000" && ok "system user (mitv.service) is blocked" \
    || warn "system user (1000) is NOT blocked. See docs/GUIDE.md step N."
else
  warn "NetGuard is not running. Xiaomi's system services can reach their servers. See docs/GUIDE.md step N."
fi
