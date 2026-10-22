# Project notes (decisions and plan)

## Owner's goal (2026-10-22)
- Phone app that replaces the broken Mi TV remote. Owner's TV: Mi TV bought 2018-19 (exact model still unknown). Owner's phone: OnePlus 15R.
- Must work on all Android phones. Phones with an IR emitter get both **Bluetooth** and **IR** modes; others get Bluetooth only.
- Main wish: work over **Bluetooth like the official remote** — pair once automatically (official remote: hold two buttons near the TV), and **switch the TV on from standby by Bluetooth**.
- Should work on most Mi TVs and, if possible, any Android TV with Bluetooth.
- APK on GitHub Releases, a new version number for every update. Owner gave a standing yes to push and publish releases of this repo.

## Phases
1. **Done — v1.0.0.** Build fixed, Bluetooth / IR mode switch, Power sends one signal only, release signing, public repo + release.
2. **Built — v1.1.0, waiting for the owner's TV test.** BLE HID remote (`BleHidPeripheral.kt`, HID over GATT). Direct Connect advertises for 2 min; the TV pairs it once from Add accessory; afterwards it reconnects when the app advertises (on app start/resume, on key press). Power with the TV in standby advertises for 45 s and sends System Wake when the TV reconnects. Classic HID stays as fallback ("TV वर फोन दिसत नाही?" makes the phone discoverable). Keys go over BLE first, classic second.
   - Not verified on a real TV yet. Emulator: pairing advertising starts fine, no crash.
   - If the TV lists the phone but refuses it, or does not wake: next ideas are adding the Appearance "remote control" via a different advertising API, and copying the official remote's advertising format (needs the TV model).
3. Correct Xiaomi IR codes (current codes are LG NEC codes and will not work on Mi TV) + a "find my TV" screen to try code sets.
4. Test on other Android TVs, final release.

## Facts found
- The current Bluetooth path is Classic Bluetooth HID (`BluetoothHidDevice`, Android 9+). It cannot wake a TV from standby.
- Release key lives outside the repo in `D:\mi-tv-remote-keys\` (back it up!). `keystore.properties` (gitignored) points to it.
- Building from Claude's shell needs `TEMP`/`TMP` set to a plain folder (e.g. `D:\gtmp`), otherwise Gradle fails with "Unable to establish loopback connection".
