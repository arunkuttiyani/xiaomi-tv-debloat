# xiaomi-tv-debloat

Debloat a **Xiaomi TV box, stick or TV** (Mi Box, Mi TV Stick, Mi / Redmi TVs; Android TV or Google TV),
cut off its telemetry, and replace the home screen with **Lite Home**: a tiny, fast launcher with no
internet access. No root, nothing flashed, and every step can be undone.

The scripts detect your device: Android TV or Google TV, whether PatchWall is installed, and which
chip it uses. They then pick the right package lists. **Fully tested on the Mi Box S / Mi Box 4**;
other models are expected or experimental. See [docs/DEVICES.md](docs/DEVICES.md).

![Lite Home](docs/images/home.png)

| Control Center | Launcher settings |
|---|---|
| ![Control Center](docs/images/control-center.png) | ![Settings](docs/images/settings.png) |

## What you get

- **Bloat and ads gone:**
  - Alphonso's what's-on-screen ad tracking;
  - Xiaomi promo and casting services;
  - silent partner-app installers;
  - Google's sponsored rows, screensaver and bug reporter.

  PatchWall devices also lose PatchWall's channel rows and preloaded Mi apps. Everything is
  disabled one at a time, with health checks and automatic rollback after each.
- **Xiaomi telemetry contained:**
  - The audit found Xiaomi's core TV service has a log uploader and tracks which app you use. Details are in [docs/FINDINGS.md](docs/FINDINGS.md).
  - It can't be removed safely, so the guide firewalls it with NetGuard.
- **Lite Home launcher:**
  - About 55 KB, no tracking, no third-party code. Its only network use is the optional weather.
  - A clean grid: the selected app is bright and slightly larger, the rest softly dimmed.
  - A compact, tvOS-style **Control Center**: Wi-Fi, Bluetooth, display, storage and memory.
  - **Hidden launcher settings:** apps per row, tile size, app names, animation level, clock and more.
  - **Weather beside the clock**, located from the TV's time zone. After a minute idle it unfolds
    to the right ("Partly cloudy · 17° / 11°"). HTTPS only, and one switch turns it off.
  - Runs at about 97% on-time frames on a Mi Box 4. Works on Android 5+ at any screen size.
  - New apps appear automatically. Hold OK on a tile to move, hide or uninstall it.
- **Fixes:**
  - the right output resolution (the box can pick 720p on a 1080p screen);
  - "Don't keep activities" off;
  - a working screensaver;
  - about 200 MB of cache cleared.

## Quick start

```bash
git clone https://github.com/arunkuttiyani/xiaomi-tv-debloat.git && cd xiaomi-tv-debloat
scripts/00-check-prereqs.sh                # A. tools on your computer
# B. on the box: enable Developer options + USB debugging (see guide)
export MIBOX_IP=192.168.1.50               # C. your box's IP
scripts/01-connect.sh                      # D. connect (accept the prompt on the TV)
scripts/02-backup.sh                       # E. back up
scripts/03-audit.sh                        # F. see what's phoning home (read-only)
scripts/04-debloat.sh                      # G. shows the plan for your device, then disables one at a time
scripts/05-tweaks.sh                       # I+J. screensaver, speed, storage
scripts/06-install-launcher.sh             # K+L. build and install Lite Home
#                                            M. resolution, N. NetGuard: see guide
scripts/07-verify.sh --reboot              # O. reboot and check everything
```

Undo everything: `scripts/restore.sh` (add `--remove-launcher` to remove Lite Home), or a factory reset.

**Read [docs/GUIDE.md](docs/GUIDE.md) first.** It explains every step from A to Z: what each one
changes, why, and how to undo it.

## Repository layout

```
docs/GUIDE.md             the A–Z instructions
docs/DEVICES.md           supported Xiaomi devices, what differs, how to report yours
docs/FINDINGS.md          what the privacy audit found (Mi Box 4)
docs/TROUBLESHOOTING.md   known problems and fixes
packages/common.txt       disabled on every Xiaomi TV device, with reasons
packages/patchwall.txt    added automatically when PatchWall is installed
packages/optional.txt     apps to disable only if you don't use them
packages/keep.txt         packages that are never touched, with reasons (enforced)
scripts/                  one script per step (bash; macOS and Linux)
launcher/                 Lite Home source (plain Java, no libraries) and build script
```

## Requirements

- Mac or Linux (Windows via WSL), on the same network as the box.
- `adb`.
- To build the launcher: JDK 17+ and Android SDK build-tools 34 + platform 34. No Android Studio
  or Gradle needed. Details in [GUIDE step A](docs/GUIDE.md#a-install-the-tools-on-your-computer).

## Devices

| Status | Devices |
|---|---|
| **Tested** | Mi Box S (1st gen) / Mi Box 4 (`MIBOX4`), Android TV 9 |
| Expected | Mi Box 3, Mi TV Stick, Xiaomi TV Stick 4K, Mi / Redmi TV sets with PatchWall |
| Experimental | Google TV models: Xiaomi TV Box S 2nd gen, Google TV sets (launcher swap untested) |

Tried it on another model? Please report it; [docs/DEVICES.md](docs/DEVICES.md) says how.

## Disclaimer

Use at your own risk. Everything here uses standard ADB commands, and a factory reset undoes all
of it. Still, it's your device. This project isn't affiliated with Xiaomi, Google, Amlogic or MediaTek.

## License

MIT. See [LICENSE](LICENSE). The launcher's icons are from Google's Material Design icons
(Apache 2.0); see [NOTICE](NOTICE).
