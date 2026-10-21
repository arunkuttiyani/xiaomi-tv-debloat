#!/usr/bin/env bash
# Step A: check this computer has what the other scripts need. Changes nothing.
set -u
missing=0
check() { if command -v "$1" >/dev/null 2>&1; then printf '  OK    %-10s %s\n' "$1" "$(command -v "$1")"; else printf '  MISS  %-10s %s\n' "$1" "$2"; missing=1; fi; }

echo "Needed for every step:"
check adb "macOS: brew install android-platform-tools | Debian/Ubuntu: sudo apt install adb"

echo "Needed only to build the launcher (step K):"
check java  "JDK 17 or newer. macOS: brew install --cask temurin | Debian/Ubuntu: sudo apt install openjdk-17-jdk"
check javac "comes with the JDK"
check zip   "Debian/Ubuntu: sudo apt install zip"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
for guess in "$SDK" /opt/homebrew/share/android-commandlinetools /usr/local/share/android-commandlinetools \
             "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
  [ -n "$guess" ] && [ -d "$guess/build-tools/34.0.0" ] && SDK="$guess" && break
done
if [ -n "$SDK" ] && [ -d "$SDK/build-tools/34.0.0" ] && [ -f "$SDK/platforms/android-34/android.jar" ]; then
  echo "  OK    android-sdk $SDK"
else
  echo "  MISS  android-sdk  build-tools 34.0.0 + platform 34. See docs/GUIDE.md step A."
  missing=1
fi

if command -v java >/dev/null 2>&1; then
  v="$(java -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/')"
  [ "${v:-0}" -ge 17 ] 2>/dev/null || { echo "  WARN  Java $v found; JDK 17+ is needed to build."; missing=1; }
fi

[ $missing = 0 ] && echo "All set." || echo "Install the missing items above, then run this again."
exit $missing
