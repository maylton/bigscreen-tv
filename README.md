# Big Screen for Android TV

A light home screen (launcher) for Android TV and Google TV, the Android
version of [Caelestia Big Screen](https://github.com/maylton/caelestia-bigscreen).
It is a simpler, faster alternative to the stock Google TV home.

- Rows for recent games, favorites, apps and games, with the apps' TV banners
- Shortcuts to the Android TV settings: Wi-Fi, remotes and accessories,
  display and sound, apps, storage and more
- Search, favorites and hidden apps; reorder apps and favorites
- Updates itself from the GitHub releases
- Works with the remote and with gamepads
- Android 9 or newer, about 400 KB
- English and Brazilian Portuguese

## Install

Download the APK from the releases page and install it with adb (or any
sideloading app):

```sh
adb connect TV_IP
adb install big-screen.apk
```

## Make it the home screen

Google TV does not let you pick another home screen in its settings. The
easiest way is to turn on **Big Screen** in Settings → System → Accessibility:
the Home button then opens Big Screen. On Android 13 and newer, if the switch is
greyed out, first open App info for Big Screen and choose "Allow restricted
settings".

Or, with adb, replace the Google TV home for good:

```sh
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
```

The second command may answer "Unknown package" on devices without it; that is
fine. To bring the Google TV home back, run the same commands with
`pm enable` instead of `pm disable-user --user 0`.

## Build

Needs JDK 17+ and the Android SDK:

```sh
./gradlew assembleRelease
```

The release build is signed when `bigscreen.keystore` and
`bigscreen.keystore.password` are set in `~/.gradle/gradle.properties`.

## License

[GPL-3.0](LICENSE)
