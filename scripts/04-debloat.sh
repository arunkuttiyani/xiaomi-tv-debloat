#!/usr/bin/env bash
# Steps G and H: disable every package in a list, one at a time, health-checking after each.
# Stops at the first package that causes a problem (and re-enables that one).
#   scripts/04-debloat.sh                          # packages/debloat.txt (recommended set)
#   scripts/04-debloat.sh packages/optional.txt    # after editing it to the apps you don't use
. "$(dirname "$0")/lib.sh"
LIST="${1:-$REPO_ROOT/packages/debloat.txt}"
ensure_connected

pkgs="$(read_list "$LIST")"
[ -n "$pkgs" ] || { echo "No packages listed in $LIST (are they all commented out?)."; exit 0; }
say "Disabling $(echo "$pkgs" | wc -l | tr -d ' ') packages from $LIST"
for p in $pkgs; do
  "$REPO_ROOT/scripts/debloat-step.sh" "$p" || { echo; echo "Stopped at $p. Everything before it is done."; exit 1; }
done
say "Done. Undo any of them with scripts/restore.sh, or one with: adb -s $DEV shell pm enable <package>"
