# mibox4-debloat

Debloat a **Xiaomi Mi Box 4** (Android TV 9), cut off its telemetry, and replace the home screen
with **Lite Home**: a tiny, fast launcher with no internet access. No root, nothing flashed, and every
step can be undone.

![Lite Home](docs/images/home.png)

## What you get

- **Bloat and ads gone:**
  - Alphonso's what's-on-screen ad tracking;
  - Xiaomi promo and casting services;
  - silent partner-app installers;
  - Google's sponsored rows, screensaver and bug reporter.

  All 12 are disabled one at a time, with health checks after each.
- **Xiaomi telemetry contained:**
  - The audit found Xiaomi's core TV service has a log uploader and tracks which app you use. Details are in [docs/FINDINGS.md](docs/FINDINGS.md).
  - It can't be removed safely, so the guide firewalls it with NetGuard.
- **Lite Home launcher:**
  - About 30 KB and 16 MB of RAM (the stock launcher uses ~70 MB).
  - Opens in under 0.4 s and runs at 60 fps.
  - Has a live Wi-Fi indicator.
  - New apps appear automatically.
  - Long-press a tile to reorder, hide or uninstall.
  - It has **no internet permission**.
- **Fixes:**
  - the right output resolution (the box can pick 720p on a 1080p screen);
  - "Don't keep activities" off;
  - a working screensaver;
  - about 200 MB of cache cleared.

## Quick start

```bash
git clone <this repo> && cd mibox4-debloat
scripts/00-check-prereqs.sh                # A. tools on your computer
# B. on the box: enable Developer options + USB debugging (see guide)
export MIBOX_IP=192.168.1.50               # C. your box's IP
scripts/01-connect.sh                      # D. connect (accept the prompt on the TV)
scripts/02-backup.sh                       # E. back up
scripts/03-audit.sh                        # F. see what's phoning home (read-only)
scripts/04-debloat.sh                      # G. disable bloatware, one at a time
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
docs/FINDINGS.md          what the privacy audit found
docs/TROUBLESHOOTING.md   known problems and fixes
packages/debloat.txt      packages disabled by step G, with reasons
packages/optional.txt     apps to disable only if you don't use them
packages/keep.txt         packages that must NOT be touched, with reasons
scripts/                  one script per step (bash; macOS and Linux)
launcher/                 Lite Home source (plain Java, no libraries) and build script
```

## Requirements

- Mac or Linux (Windows via WSL), on the same network as the box.
- `adb`.
- To build the launcher: JDK 17+ and Android SDK build-tools 34 + platform 34. No Android Studio
  or Gradle needed. Details in [GUIDE step A](docs/GUIDE.md#a-install-the-tools-on-your-computer).

## Tested on

Xiaomi Mi Box 4 (`MIBOX4` / `oneday`), Android TV 9, build 3933, security patch 2021-07-05.
Other Mi Box models may need different package lists. Run `scripts/03-audit.sh` and compare first.

## Disclaimer

Use at your own risk. Everything here uses standard ADB commands, and a factory reset undoes all
of it. Still, it's your device. This project isn't affiliated with Xiaomi, Google or Amlogic.

## License

MIT. See [LICENSE](LICENSE). The Settings gear icon is from Google's Material Design icons
(Apache 2.0); see [NOTICE](NOTICE).
