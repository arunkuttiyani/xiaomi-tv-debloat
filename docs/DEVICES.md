# Supported devices

The scripts work out what kind of Xiaomi device they're talking to (`scripts/01-connect.sh`
prints it) and choose the package lists to match:

| Detected | Lists used by `04-debloat.sh` | Home screen replaced by step L |
|---|---|---|
| Android TV (any Xiaomi) | `common.txt` | `com.google.android.tvlauncher` |
| …with PatchWall installed | `common.txt` + `patchwall.txt` | also `com.mitv.tvhome.atv` |
| Google TV | `common.txt` (+ `patchwall.txt` if present) | `com.google.android.apps.tv.launcherx` (experimental) |

Packages a device doesn't have are skipped. Anything in `packages/keep.txt` is never touched, even
if another list names it. Every package is still disabled one at a time, with health checks and
automatic rollback.

## Status

**Tested** = run end to end on real hardware by this project: debloat, launcher, reboot,
restore, and re-apply. **Expected** = same platform as a tested device, with package names from
community lists; not run here. **Experimental** = parts untested; read the notes first.

| Model string | Device | Android | Status | Notes |
|---|---|---|---|---|
| MIBOX4 | Mi Box S (1st gen) / Mi Box 4, codename `oneday` | TV 9 | **Tested** | build PI.3933. Everything in this repo. |
| MIBOX3 | Mi Box 3 | TV 8–9 | Expected | same Amlogic platform and Xiaomi service design |
| — | Mi TV Stick (1080p) | TV 9 | Expected | Amlogic; often ships PatchWall |
| — | Xiaomi TV Stick 4K | TV 11 | Expected | per-app traffic in the audit isn't available on Android 10+ |
| — | Mi / Redmi TV sets (4A, 4S, P1, Q1, A2…) | TV 9–11 | Expected | mostly MediaTek; PatchWall is the home screen. Keep everything TV-tuner related (see keep.txt) |
| — | Xiaomi TV Box S 2nd gen (MDZ-28) | Google TV 12 | Experimental | replacing the Google TV home is untested; debloat lists apply |
| — | Xiaomi / Redmi Google TV sets | Google TV | Experimental | as above, plus TV-set caveats |

Not Xiaomi? The launcher (step K/L) works on most Android TV devices, but the package lists won't
match. `01-connect.sh` warns you.

## What differs between devices

- **Process and package names:** Xiaomi's core TV service is `mitv.service` (process
  `xiaomi.tvservice`) on the boxes. The audit lists every Xiaomi-looking package it finds and marks the
  ones no list covers yet as `UNREVIEWED`. Look those up before disabling anything.
- **Chip:** the HDMI readout in `07-verify.sh` uses Amlogic's driver files. On MediaTek TV sets it
  shows the Android display mode instead.
- **Android 10+:** the per-app traffic counters the audit reads were removed. Use NetGuard's log instead.
- **TV sets:** never disable Live Channels (`com.google.android.tv`) or anything from
  `com.mediatek`, `com.mstar` or `com.droidlogic`. That's how the TV shows its tuner and HDMI inputs.
- **The remote's shortcut buttons** go through `com.xiaomi.android.tvsetup.partnercustomizer`.
  Some community lists remove it, but it's protected here.

## Report your device

Run the scripts, then open an issue on GitHub with:

```bash
scripts/01-connect.sh                     # device line
scripts/03-audit.sh                       # especially any UNREVIEWED packages
scripts/07-verify.sh --reboot             # health after a reboot
```

Say what worked and what didn't (the remote buttons, voice, casting, HDMI-CEC, apps). Once a device
is confirmed, it moves to **Tested** in the table above.

## Sources

The package lists for untested models were cross-checked against these community lists. Package
names are facts about Xiaomi's firmware; the descriptions and risk calls here are this project's own.

- Gathin23/cleanyourtv examples (MIT): Mi Box S and Mi TV 4A lists, via tvdebloat.com
- alexander-danilenko's gist "Debloat Xiaomi Smart TV Box S (2nd Gen)"
- tweakradje's Xiaomi Mi TV Stick notes
- "Make your Android TV faster" (Mi TV disabled-package list), Medium
