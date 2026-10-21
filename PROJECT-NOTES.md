# Project notes (decisions and plan)

## Owner's goal (2026-10-22)
- Phone app that replaces the broken Mi TV remote. Owner's TV: Mi TV bought 2018-19 (exact model still unknown). Owner's phone: OnePlus 15R.
- Must work on all Android phones. Phones with an IR emitter get both **Bluetooth** and **IR** modes; others get Bluetooth only.
- Main wish: work over **Bluetooth like the official remote** — pair once automatically (official remote: hold two buttons near the TV), and **switch the TV on from standby by Bluetooth**.
- Should work on most Mi TVs and, if possible, any Android TV with Bluetooth.
- APK on GitHub Releases, a new version number for every update. Owner gave a standing yes to push and publish releases of this repo.

## Phases
1. **Done — v1.0.0.** Build fixed, Bluetooth / IR mode switch, Power sends one signal only, release signing, public repo + release.
2. **Next.** Turn the app into a **Bluetooth Low Energy (BLE) HID remote** like the official one (HID over GATT). Add "Connect to TV" that advertises like a remote so the TV can pair it once. Then test Power-on from standby. Experimental: depends on whether the TV accepts the phone like its own remote.
3. Correct Xiaomi IR codes (current codes are LG NEC codes and will not work on Mi TV) + a "find my TV" screen to try code sets.
4. Test on other Android TVs, final release.

## Facts found
- The current Bluetooth path is Classic Bluetooth HID (`BluetoothHidDevice`, Android 9+). It cannot wake a TV from standby.
- Release key lives outside the repo in `D:\mi-tv-remote-keys\` (back it up!). `keystore.properties` (gitignored) points to it.
- Building from Claude's shell needs `TEMP`/`TMP` set to a plain folder (e.g. `D:\gtmp`), otherwise Gradle fails with "Unable to establish loopback connection".
