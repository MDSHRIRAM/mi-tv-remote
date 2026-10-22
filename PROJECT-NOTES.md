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
2b. **v1.2.0 — look like the official remote.** Pairing advertises as "MI RC" (phone Bluetooth name switched for the pairing window, restored after; original kept in prefs) with manufacturer field FF 00 00. Power with the TV not linked: 10 s non-connectable "power press" burst (MI RC + HID UUID + FF 00 01 + TX power), plus a "knock" on the TV's address (SDP query + LE connect, the Mi Box trick), plus 45 s reconnect advertising.
3. Correct Xiaomi IR codes (current codes are LG NEC codes and will not work on Mi TV) + a "find my TV" screen to try code sets.
4. Test on other Android TVs, final release.

## Facts found
- The current Bluetooth path is Classic Bluetooth HID (`BluetoothHidDevice`, Android 9+). It cannot wake a TV from standby.
- Release key lives outside the repo in `D:\mi-tv-remote-keys\` (back it up!). `keystore.properties` (gitignored) points to it.
- Building from Claude's shell needs `TEMP`/`TMP` set to a plain folder (e.g. `D:\gtmp`), otherwise Gradle fails with "Unable to establish loopback connection".

## Research: how the official remote wakes the TV (2026-10-22)
- ssw112/xiaomi-tv-ble-wake (Mi TV P1, MediaTek MT7668, Android 9; one person, repo 2 days old, unverified elsewhere): in standby the BT chip runs "wake over BLE". It wakes the TV only for an advertisement that (1) comes from an address in `persist.bluetooth.wakeuprcaddr` (the paired remote's address) and (2) matches a content filter. The remote's power-press packet: `02 01 05 | 03 FF 00 01 | 06 08 "MI RC" | 03 02 12 18 | 04 0D 04 05 00 | 02 0A 00 | 04 FE <last 3 bytes of the TV address, reversed>`. Replaying it from an ESP32 using the remote's address wakes the TV from standby (~3 s) and after a power cut (~30 s).
- frlequ/mibox_socket: Mi Box S wakes from deep sleep when another device starts pairing with its MAC (Raspberry Pi + bluetoothctl). Not confirmed on Mi TVs.
- shammysha/esphome-ble-mi-remote issues #2, #4, #8: emulated BLE remotes paired via Add accessory control the TV but cannot switch it on.
- Android limits (checked in SDK 37 source): `AdvertisingSetParameters.Builder.setOwnAddressType` is @SystemApi and needs BLUETOOTH_PRIVILEGED, so a normal app cannot advertise from a fixed or copied address. AdvertiseData cannot carry raw AD types 0x0D (class of device) or 0xFE. So the exact remote packet cannot be sent from a stock phone; v1.2.0 sends the closest possible version.
- Sure ways to switch on: IR (phone with IR emitter + correct Xiaomi codes), or a small ESP32 near the TV that uses the remote's address (needs that address and packet; the owner's remote is broken).
