# What the audit found

Results from auditing a Mi Box 4 (Android TV 9, build 3933, security patch July 2021),
running `scripts/03-audit.sh` and inspecting the Xiaomi system code. Your results may differ by firmware.

## Summary

- **No connections to servers in China were seen.** Every open connection went to Google, to
  Netflix (on Amazon AWS), or to devices on the local network.
- **No hidden native programs.** Every background program outside the apps was standard Android or
  Amlogic hardware support: audio, Wi-Fi, HDMI-CEC, DRM and so on.
- **Xiaomi's core service `mitv.service` does track usage and can upload logs.** It can't be safely
  disabled, so it is firewalled instead (GUIDE step N).
- **Alphonso** shipped as a system app. It's an ad-tech company whose software identifies what's on
  screen. It's disabled by step G.

## `mitv.service` (Xiaomi TV service)

- **Location:** `/system/app/TvService`, compiled code in `oat/arm/TvService.vdex`.
- **Runs as:** the `system` user (UID 1000), **persistent**: Android restarts it if it stops.
- **Permissions:** `INTERNET`, Wi-Fi state, `CHANGE_WIFI_STATE`, `MANAGE_USERS`, and a Xiaomi location permission.

Server addresses found in its code:

| Address | Likely purpose |
|---|---|
| `https://other.tv.global.mi.com/tvservice/log/upload?client_ts=` | **uploads logs to Xiaomi** |
| `https://misc.tv.global.mi.com/api/v1/sn/key` | serial-number / device key |
| `https://misc.tv.global.mi.com/api/v1/nf/key`, `.../nf/verify` | Netflix device-key provisioning (the log shows "already write netflix key finish") |
| `http://pandora.mi.com/systime` | gets the time from Xiaomi |

Other telling strings in the same code:

- `BaseDataReportor`, `sendEventLog`, `anonymizedReporting`, `adIdTracking`, `locationInfo`,
  `com.xiaomi.mitv.action.uploadlog`, `getForegroundApplicationPollTime`.
- At run time it logs `current foreground packagename is <app>` every time you switch apps, and
  runs `runSameWorkForNetworkOk` every time the network comes up.

**Why not just disable it?** It also handles remote keys (`RemoteKeyManger`), power and shutdown
timers (`ShutdownReceiver`, `AlarmService`), and the display settings. If disabling a persistent
system service makes the system crash at boot, ADB can't fix it, because the package manager isn't
running yet. The only way out would be a factory reset. Cutting its internet with NetGuard gives the
same privacy without that risk.

**Verified blocked:** with NetGuard blocking the system user, connections from that user to
`*.tv.global.mi.com` (and everything else) fail. Over a whole boot it sent about 2 KB, before
NetGuard started.

The Xiaomi middleware library (`/system/framework/mitvmiddlewareimpl.jar`) also contains a list of
Chinese sites (qq.com, sohu.com, taobao.com, speedtest.cn, ...). It's most likely a network speed or
connectivity test. No traffic to them was observed.

## Other Xiaomi / partner packages

| Package | What | Action |
|---|---|---|
| `tv.alphonso.alphonso_eula` | Alphonso automatic content recognition (ads) | disabled |
| `com.xm.webcontent` | Xiaomi web/promo content | disabled |
| `com.mitv.milinkservice` | Xiaomi casting, runs as system | disabled |
| `android.autoinstalls.config.*` | silently installs partner apps | disabled |
| `com.xiaomi.mitv.res` | resources only, no internet permission | kept |

## Display findings

- The box sometimes picks the wrong output mode. On the test box it sent **720p** to a 1080p
  monitor, so everything was scaled down and back up. GUIDE step M fixes this.
- The output tops out at 60 Hz. 4K60, HDR10 and HDMI-CEC work when the screen supports them.
- The developer option "Don't keep activities" was found switched **on**. It makes every app reload
  from scratch, and GUIDE step J turns it off.
