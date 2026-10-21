#!/usr/bin/env bash
# Step E: save the box's package lists, settings and properties to backups/<date>/.
# Run this before changing anything. Backups stay on your computer (git-ignored).
. "$(dirname "$0")/lib.sh"
ensure_connected

out="$BACKUP_DIR/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$out"
say "Backing up to $out"
box pm list packages -f -u        > "$out/packages-all-with-paths.txt"
box pm list packages -e | sort    > "$out/packages-enabled.txt"
box pm list packages -d | sort    > "$out/packages-disabled.txt"
box pm list packages -3 | sort    > "$out/packages-user-installed.txt"
box settings list secure          > "$out/settings-secure.txt"
box settings list global          > "$out/settings-global.txt"
box settings list system          > "$out/settings-system.txt"
box getprop                       > "$out/getprop.txt"
current_home                      > "$out/home-package.txt"
wc -l "$out"/*.txt | sed 's/^/  /'
ok "backup complete"
