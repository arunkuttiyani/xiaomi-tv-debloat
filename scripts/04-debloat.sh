#!/usr/bin/env bash
# Steps G and H: disable packages one at a time, health-checking after each.
# Stops at the first package that causes a problem (and re-enables that one).
#   scripts/04-debloat.sh                          # the lists for this device (common + PatchWall if present)
#   scripts/04-debloat.sh packages/optional.txt    # after uncommenting the apps you don't use
#   add --yes to skip the confirmation prompt
. "$(dirname "$0")/lib.sh"
ensure_connected
detect_device

# List files, one per line (paths may contain spaces).
lists=""
for a in "$@"; do [ "$a" != "--yes" ] && lists="$lists$a"$'\n'; done
[ -n "$lists" ] || lists="$(lists_for_device | sed "s|^|$PKG_DIR/|")"
names="$(echo "$lists" | while IFS= read -r f; do [ -n "$f" ] && printf ' %s' "$(basename "$f")"; done)"

keep="$(read_list "$PKG_DIR/keep.txt" | expand_on_device)"
plan="$(echo "$lists" | while IFS= read -r f; do [ -n "$f" ] && read_list "$f"; done | expand_on_device)"
todo=""; skipped=""
for p in $plan; do
  if echo "$keep" | grep -qx "$p"; then skipped="$skipped $p"; continue; fi
  is_disabled "$p" || todo="$todo $p"
done

say "$DEV_BRAND $DEV_MODEL: plan from$names"
if [ -z "$todo" ]; then ok "nothing to do: everything listed is already disabled or not on this device"; exit 0; fi
for p in $todo; do echo "  disable  $p"; done
for p in $skipped; do warn "keeping $p (it's in packages/keep.txt)"; done
is_xiaomi || warn "Not a Xiaomi device: double-check every package above."
confirm "Disable these $(echo $todo | wc -w | tr -d ' ') packages one at a time?" || { echo "Nothing changed."; exit 0; }

for p in $todo; do
  "$REPO_ROOT/scripts/debloat-step.sh" "$p" || { echo; echo "Stopped at $p. Everything before it is done."; exit 1; }
done
say "Done. Undo with scripts/restore.sh, or one package with: adb -s $DEV shell pm enable <package>"
