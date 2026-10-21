# Mi TV Remote

Android phone app that works as a remote for Xiaomi Mi TV and other Android TVs.

- **Bluetooth mode** — the phone acts as a Bluetooth remote/keyboard. Works on any phone with Android 9 or newer whose maker has not disabled the Bluetooth HID feature.
- **IR Blaster mode** — only on phones with a built-in IR emitter. The Bluetooth / IR switch appears automatically on those phones.

## Download

Latest APK: **[Releases page](https://github.com/MDSHRIRAM/mi-tv-remote/releases/latest)**

Each version has its own file name, e.g. `MiTVRemote-v1.0.0.apk`. A newer version installs over the old one.

## First use (Bluetooth)

1. Install the APK and open it. Allow the Bluetooth permission.
2. On the TV: **Settings → Remotes & Accessories → Add accessory**.
3. In the app tap **Direct Connect**. When the TV shows the phone, pair it.
4. After that, the app connects to the TV by itself.

## Status

| Version | What it does |
|---|---|
| 1.0.0 | Bluetooth remote, Bluetooth / IR mode switch, Power sends one signal only |

Planned: Bluetooth Low Energy remote (auto-pair like the official remote, Power-on from standby), correct Xiaomi IR codes, testing on other Android TVs.

## Building

See [RELEASING.md](RELEASING.md).
