# Mi TV Remote

Android phone app that works as a remote for Xiaomi Mi TV and other Android TVs.

- **Bluetooth mode** — the phone acts as a Bluetooth remote/keyboard. Works on any phone with Android 9 or newer whose maker has not disabled the Bluetooth HID feature.
- **IR Blaster mode** — only on phones with a built-in IR emitter. The Bluetooth / IR switch appears automatically on those phones.

## Download

Latest APK: **[Releases page](https://github.com/MDSHRIRAM/mi-tv-remote/releases/latest)**

Each version has its own file name, e.g. `MiTVRemote-v1.0.0.apk`. A newer version installs over the old one.

## First use (Bluetooth)

1. Install the APK and open it. Allow the Bluetooth permissions.
2. On the TV: **Settings → Remotes & Accessories → Add accessory**.
3. In the app tap **Direct Connect**. For 2 minutes the phone shows up on the TV like a Bluetooth remote (Bluetooth Low Energy, the same kind of link the official Mi remote uses). Pick it on the TV and confirm pairing on both sides.
4. This is needed only once. Afterwards the TV reconnects by itself when the app is open and you press a button.
5. If the TV does not list the phone, tap **"TV वर फोन दिसत नाही?"** in the pairing window. The phone then becomes visible to classic Bluetooth for 2 minutes.

## Power-on from standby (experimental)

With the TV in standby (mains switch on, TV off), open the app and press **Power**. The phone calls the TV over Bluetooth LE for 45 seconds, the same way the official remote does. If the TV wakes and reconnects, the app sends the wake-up key. Whether the TV accepts this from a phone depends on the TV model. On phones with an IR emitter, IR mode can always switch the TV on.

## Status

| Version | What it does |
|---|---|
| 1.1.0 | Bluetooth LE remote (pair once, auto-reconnect), Power-on from standby experiment, Marathi pairing help |
| 1.0.0 | Bluetooth remote, Bluetooth / IR mode switch, Power sends one signal only |

Planned: correct Xiaomi IR codes, testing on other Android TVs.

## Building

See [RELEASING.md](RELEASING.md).
