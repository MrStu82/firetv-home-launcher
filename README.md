# Fire TV Home Launcher

A minimal, gallery-style home screen for Amazon Fire TV (Fire OS 7/8). It shows a
large photograph, the time, the local weather and one row of your favourite apps,
plus an "All apps" grid and a small Settings page. No ads, no sponsored rows, no
account sign-in.

It is published as-is for others to use and fork; it is not configurable beyond the
built-in Settings page.

## Features

- One row of favourite app tiles using each app's **own** banner/icon from its
  installed APK (scaled only, never redrawn). Apps that are not installed are skipped.
- "All apps" grid of every launchable app on the device.
- Wallpaper: Bing's daily UHD photographs (≥3840×2160 only), cached on disk and
  rotated every ~3 hours, with the photo credit as a caption. Falls back to a bundled
  4K photograph when offline, or a plain background if you choose.
- Clock (12/24 h) and current weather from [Open-Meteo](https://open-meteo.com/)
  (no API key) for a chosen city.
- Settings: wallpaper source and rotation, weather city, clock format, app order and
  visibility, and "Open Fire TV home" to reach Amazon's own home screen.

## How the Home takeover works

Fire OS does not let a third-party app become the default home screen. The app
declares a `HOME` intent filter, but on Fire OS that is not enough:

- `adb shell cmd package set-home-activity …` (and the `HOME` role) is **accepted
  but inert**: the role holder changes, yet Home still resolves to Amazon's launcher
  (`com.amazon.tv.launcher`), because Fire OS gives its launcher a high intent-filter
  priority and clamps non-system apps to priority 0. There is no home-app chooser in
  Fire TV Settings either.

So the takeover is done by an optional accessibility service,
[`HomeButtonService`](app/src/main/java/com/mrstu82/tvhome/HomeButtonService.kt),
which uses two mechanisms:

1. **Home key filter.** The service requests key-event filtering
   (`flagRequestFilterKeyEvents`). When it sees `KEYCODE_HOME` it consumes the key
   and brings this launcher to the front.
2. **Amazon launcher redirect (backstop).** Fire OS key policy still starts its own
   home after the key is consumed, so the service also watches window-state changes
   (`notificationTimeout = 0`). Whenever `com.amazon.tv.launcher` comes to the
   front, it immediately starts this launcher again. This also catches the Fire home
   appearing for other reasons (e.g. after the screensaver).

"Open Fire TV home" in Settings sets a temporary bypass so you can use Amazon's home
(e.g. for system settings or the Appstore). The bypass ends when you open another
app or press Home again.

The service cannot read window content (`canRetrieveWindowContent="false"`); it only
sees the package name of the window that came to the front.

## Build

Requirements: JDK 17 or newer, the Android SDK (compileSdk 35), and Gradle 8.9 or
newer. No Gradle wrapper is included; use an installed Gradle (or run
`gradle wrapper` once to generate one).

```sh
echo "sdk.dir=/path/to/android-sdk" > local.properties

# Debug build (signed with the Android debug key) — fine for personal sideloading
gradle :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

# Release build (minified), signed with your own keystore via environment variables
export SIGNING_STORE_FILE=/path/to/your.keystore
export SIGNING_STORE_PASSWORD=…
export SIGNING_KEY_ALIAS=…
export SIGNING_KEY_PASSWORD=…
gradle :app:assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```

Keystores, `local.properties` and APKs are git-ignored. Never commit signing
material. Keep using the same key for updates: Android refuses to update an app
signed with a different key.

## Install

### Enable sideloading on the Fire TV

1. Settings → My Fire TV → About → select the device name 7 times to unlock
   **Developer options**.
2. Settings → My Fire TV → Developer options → turn on **ADB debugging** and allow
   **Apps from Unknown Sources** (on newer Fire OS this is per-app, e.g. for
   Downloader).
3. Find the TV's IP address under Settings → My Fire TV → About → Network.

### adb install

```sh
adb connect <tv-ip>:5555        # accept the prompt on the TV the first time
adb install -r app-release.apk  # -r keeps settings when updating
```

Alternatively, host the APK somewhere reachable and open it with the
**Downloader** app on the TV.

Once installed, the launcher appears as **Home** in Your Apps & Channels and can be
opened like any other app.

## Enable the Home button takeover

The accessibility service is off until you turn it on:

- **On the TV:** Settings → Accessibility → **Home button takeover** → On.
  (On some Fire OS builds this lives under Settings → Accessibility → "Other
  services".)
- **Or via adb:**

  ```sh
  adb shell settings put secure enabled_accessibility_services \
    com.mrstu82.tvhome/com.mrstu82.tvhome.HomeButtonService
  adb shell settings put secure accessibility_enabled 1
  ```

  Note this replaces any other enabled accessibility services; append with `:` if
  you use others.

Then press Home. You should land on this launcher.

## Known Fire OS quirks

- **Brief flash of Amazon's home.** Fire OS starts its own home even after the Home
  key is consumed; the redirect backstop replaces it within a fraction of a second,
  so you may briefly see it.
- **After installing or updating the APK**, Fire OS may unbind the accessibility
  service for a moment. The takeover re-engages after the service rebinds; if it
  doesn't, open an app and press Home again, or toggle the service off and on.
  Pressing Home while you are already on Amazon's home may do nothing at first.
- **Long-press Home** (the quick-settings menu) is swallowed while the service is on.
  Use Settings → "Open Fire TV home" to reach Amazon's home and its menus.
- **Fire OS updates** can turn accessibility services off. Re-enable it if Home
  starts going to Amazon's launcher again.
- **`set-home-activity` / HOME role** have no effect (see above), so there is no
  "proper" default-home route without root.
- The app is not debuggable in release builds, so `adb shell run-as` won't work;
  use `adb logcat` and `adb shell uiautomator dump` for diagnostics.

## Uninstall / restore the stock launcher

Amazon's launcher is never disabled or replaced, so removing this app restores the
stock behaviour immediately.

1. Turn the takeover off: Settings → Accessibility → Home button takeover → Off
   (or `adb shell settings delete secure enabled_accessibility_services`, which
   clears all enabled services).
2. Uninstall: Settings → Applications → Manage Installed Applications → Home →
   Uninstall, or:

   ```sh
   adb uninstall com.mrstu82.tvhome
   ```

3. Press Home. You're back on the Fire TV home screen.

## Customising (for forkers)

Everything is plain Kotlin views; there are no layout XML files.

- **Favourite app row:** the `APPS` list in
  [`Ui.kt`](app/src/main/java/com/mrstu82/tvhome/Ui.kt): package name, label,
  tint colour, and `useIcon` (use the app icon instead of its banner). Users can
  reorder or hide these in Settings.
- **Weather cities:** the `CITIES` list in the same file.
- **Settings rows:** [`SettingsActivity.kt`](app/src/main/java/com/mrstu82/tvhome/SettingsActivity.kt).
- **Home screen layout** (clock, weather, tiles, All apps / Settings buttons):
  [`HomeActivity.kt`](app/src/main/java/com/mrstu82/tvhome/HomeActivity.kt).
- **Wallpaper source:** [`Bing.kt`](app/src/main/java/com/mrstu82/tvhome/Bing.kt);
  bundled fallback `app/src/main/res/drawable-nodpi/wall_clouds.jpg`.
- If you publish your own build, change `applicationId` / `namespace` in
  `app/build.gradle.kts` so it doesn't clash with this package.

## Credits

- Fonts: [Cormorant](https://github.com/CatharsisFonts/Cormorant) and
  [Inter](https://github.com/rsms/inter), both under the SIL Open Font License 1.1.
- Bundled wallpaper: photograph from [Unsplash](https://unsplash.com/) (Unsplash
  License).
- Daily wallpapers: Bing image of the day; each photo's copyright is shown on screen.
- Weather: [Open-Meteo](https://open-meteo.com/).
- App logos shown in tiles are read from the installed apps themselves and belong to
  their respective owners. This project is not affiliated with Amazon.

## License

MIT, see [LICENSE](LICENSE).
