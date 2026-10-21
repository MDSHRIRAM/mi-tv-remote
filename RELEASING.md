# Releasing a new version

1. In `app/build.gradle.kts` raise `versionCode` by 1 and set the new `versionName` (e.g. `1.1.0`).
2. Make sure `keystore.properties` is in the project root. It points to the release key, which is kept **outside** this repo (`D:\mi-tv-remote-keys\`). Never commit it.
3. Build: `gradlew.bat assembleRelease`
4. Copy `app/build/outputs/apk/release/app-release.apk` to `MiTVRemote-v<version>.apk`.
5. Commit, tag `v<version>`, push, and create a GitHub Release with that tag. Attach the APK.

Every release must be signed with the same key. If the key is lost, phones cannot install updates over the old app (the app must be uninstalled first).
