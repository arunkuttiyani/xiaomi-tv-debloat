# Troubleshooting

## `adb connect` fails or says "unauthorized"
- Check `MIBOX_IP` and that the box and computer are on the same network.
- Check **USB debugging** (and **Network debugging**, if your firmware has it) is on. See GUIDE step B.
- "Unauthorized": look at the TV for the **Allow USB debugging?** prompt, tick **Always allow**.
  If you dismissed it, run `adb kill-server`, then `scripts/01-connect.sh` again.

## `adb: device offline` partway through a script
Wi-Fi ADB drops now and then, especially right after installing an app or compiling one.
The scripts reconnect on their own between steps. If one stops, just run it again: every script
skips work that's already done.

## The home screen didn't change to Lite Home
Android TV always gives Google's launcher priority while it's enabled, so it must be disabled:
`adb -s $MIBOX_IP:5555 shell pm disable-user --user 0 com.google.android.tvlauncher`.
`scripts/06-install-launcher.sh` does this for you.

## Lite Home crashed and the box shows a blank "loading" screen
That's Android's fallback home. Bring back the stock launcher:
`adb -s $MIBOX_IP:5555 shell pm enable com.google.android.tvlauncher`, then press HOME.

## An app misbehaves after debloating
Re-enable the last packages from `packages/common.txt` (or `patchwall.txt`) one at a time with
`adb -s $MIBOX_IP:5555 shell pm enable <package>`, or run `scripts/restore.sh`.
Please open an issue saying which package it was.

## Never use `pm uninstall --user 0` on apps you installed
For apps from Play or F-Droid, removing the app from the only user also deletes its APK.
`cmd package install-existing` then reports "installed" but the app isn't really back, and you have
to reinstall it from the store. These scripts only ever use `pm disable-user`, which is always
reversible.

## Clock is wrong after blocking Android System in NetGuard
Automatic time sync runs as the system user. Allow **Android System** in NetGuard for a minute,
let the time sync, then block it again. Or set the time zone and time by hand.

## Building the launcher: `d8` crashes with `NullPointerException ... String.length()`
d8 from build-tools 34 crashes on some class metadata produced by JDK 21: private nested classes,
anonymous classes and inner (non-static) classes. Lite Home's source avoids all three. If you add
code, use static or top-level classes (see `Callbacks.java`), or use lambdas for interfaces.

## Building the launcher: `pm clear` hangs
`pm clear` hangs on a package that's already disabled, so `scripts/debloat-step.sh` clears data
before disabling and gives up after 15 seconds.

## macOS: `grep` errors like "empty (sub)expression"
Some setups alias `grep` to `ugrep`. The scripts run under `bash`, where the alias doesn't apply.
If you copy commands into an interactive zsh session, use `/usr/bin/grep`.

## A health check fails on a device that seems fine
Health checks compare against what was running **before** each package is disabled, so a service
that was never running isn't required. If a check still fails on a working device, run
`scripts/debloat-step.sh <package>` on its own to see which check, and report it with your
`scripts/01-connect.sh` output (see docs/DEVICES.md).

## `03-audit.sh` lists packages as UNREVIEWED
No list covers them yet, which is normal on models other than the Mi Box 4. Search the package name
before disabling it. If it's clearly bloat or telemetry, disable it with
`scripts/debloat-step.sh <package>` (health checks included) and please report it so it can be
added to the lists.

## Google TV: HOME still opens the Google TV home screen
Replacing the Google TV home is experimental. `scripts/06-install-launcher.sh` re-enables the stock
home if HOME doesn't switch. Please report your model and Android version.

## TV sets: no picture from HDMI inputs or the tuner after debloating
Re-enable Live Channels and anything chip-vendor related:
`adb -s $MIBOX_IP:5555 shell pm enable com.google.android.tv`, then run `scripts/restore.sh` if needed.
The default lists never disable these on purpose; only an edited `optional.txt` would.

## Lite Home: where are the settings?
They're hidden on purpose. Press UP from the top row to select the network pill, then **hold OK**
(or press MENU). Holding OK on the Settings tile works too.

## Lite Home feels slow on my TV
Open the launcher settings and set **Animations** to *Reduced* or *Off*. Turning off
**Background follows the selected app** saves a little more. Both apply when you go back home.

## "Set as default home screen" does nothing
Many Android TV and Google TV builds keep their own launcher in charge whatever you choose. Disable
the stock launcher with ADB (step L does this): the app shows the exact command when the request
doesn't take effect.

## Lite Home: no weather beside the clock
- Check **Weather** is On in the launcher settings (hold OK on the network pill).
- The location comes from the TV's time zone. If it is set to a generic zone such as `UTC` or
  `Etc/GMT+1`, there is no city to look up: pick your region's time zone in the TV's
  Date & time settings.
- The box needs internet access for the first lookup; until then nothing is shown.
