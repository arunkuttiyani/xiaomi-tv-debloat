#!/usr/bin/env bash
# Step F: read-only privacy audit. Shows Xiaomi packages, what is talking to the internet right
# now, per-app traffic since boot, and whether NetGuard is firewalling the system user.
. "$(dirname "$0")/lib.sh"
ensure_connected

say "Xiaomi / partner packages and their state"
for p in mitv.service com.xiaomi.mitv.res com.mitv.milinkservice com.xm.webcontent tv.alphonso.alphonso_eula \
         android.autoinstalls.config.xiaomi.mibox3 android.autoinstalls.config.xioami.mibox3; do
  if ! box pm list packages -u | grep -qx "package:$p"; then state="not present"
  elif is_disabled "$p"; then state="disabled"
  elif is_installed "$p"; then state="ENABLED"
  else state="removed for user"; fi
  printf '  %-45s %s\n' "$p" "$state"
done

# Map UIDs to package names so the network tables below are readable.
uidmap="$(box pm list packages -U | sed 's/^package://' | awk '{split($2, u, ":"); print u[2], $1}')"
name_of() { echo "$uidmap" | awk -v u="$1" '$1==u {printf "%s%s", sep, $2; sep=","} END {if (!NR) print ""}'; }
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
box 'cat /proc/net/tcp /proc/net/tcp6' | awk 'NR>1 && $4=="01" {print $3, $8}' | sort -u | while read -r rem uid; do
  ip="$(hex_ip "${rem%%:*}")"; port=$((16#${rem##*:}))
  case "$ip" in 127.*|0.0.0.0) continue;; esac
  printf '  %-18s port %-6s %s\n' "$ip" "$port" "$(label_uid "$uid")"
done

say "Traffic since boot, by app (received / sent)"
box cat /proc/net/xt_qtaguid/stats 2>/dev/null \
  | awk 'NR>1 {rx[$4]+=$6; tx[$4]+=$8} END {for (u in rx) if (rx[u]+tx[u]>0) print u, rx[u], tx[u]}' \
  | sort -k2 -n -r | while read -r uid rx tx; do
      printf '  %10s KB / %8s KB  %s\n' "$((rx/1024))" "$((tx/1024))" "$(label_uid "$uid")"
    done

say "NetGuard firewall"
if box pidof eu.faircode.netguard >/dev/null; then
  uids="$(box dumpsys connectivity | grep -oE 'Uids: <[^>]*>' | head -1)"
  ok "NetGuard is running. UIDs routed into its block list: ${uids:-none}"
  echo "$uids" | grep -q "1000-1000" && ok "system user (mitv.service) is blocked" \
    || warn "system user (1000) is NOT blocked. See docs/GUIDE.md step N."
else
  warn "NetGuard is not running. mitv.service can reach Xiaomi's servers. See docs/GUIDE.md step N."
fi
