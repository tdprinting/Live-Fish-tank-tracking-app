# ReefLive

An Android app for tracking a reef aquarium (water tests, care tasks, livestock) plus a live 3D
recreation of the tank. Design docs live in [`docs/`](docs/): start with
[`DESIGN.md`](docs/DESIGN.md), [`PLAN.md`](docs/PLAN.md) and [`ARCHITECTURE.md`](docs/ARCHITECTURE.md).

Status: **M0** (repository skeleton, CI, placeholder "Hello reef" screen).

## Install on your phone (for Tee)

ReefLive is not on the Play Store. Every push to `main` builds a new APK and publishes it on the
GitHub **Releases** page.

1. On your Android phone (Android 8.0 or newer), open
   **https://github.com/tdprinting/live-fish-tank-tracking-app/releases/latest**.
2. Under **Assets**, tap **`app-release.apk`** to download it.
   (`app-debug.apk` is the same app with debugging turned on; you only need the release one.)
3. Open the downloaded file (from the download notification or the **Files / Downloads** app).
4. Android will say the browser/Files app is *not allowed to install unknown apps*. Tap
   **Settings**, turn on **Allow from this source**, then go back.
   (On some phones this is under *Settings > Apps > Special app access > Install unknown apps*.)
5. Tap **Install**. If Play Protect warns about an unrecognised developer, tap
   **More details > Install anyway**.
6. Open **ReefLive**. You should see the dark ocean "Hello reef" screen.

**Updating:** download the newest `app-release.apk` from Releases and install it the same way. It
installs over the old version and keeps your data, because every build is signed with the same key.
Do not mix in builds signed by anyone else, or Android will refuse the update.

## Build it yourself

Requirements: JDK 17 or newer. The Android app also needs the Android SDK (platform 35).

```bash
# Core logic only: no Android SDK and no Google Maven needed
./gradlew -PcoreOnly :core:test

# Full build (what CI runs)
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
```

APKs land in `app/build/outputs/apk/{debug,release}/`.

### Modules

| Module | What it is |
|---|---|
| `:core` | Pure Kotlin/JVM: chemistry, status engine, species, simulation. Builds and tests without Android. |
| `:app` | Android app: Jetpack Compose UI, storage, notifications, 3D renderer. Depends on `:core`. |

`settings.gradle.kts` includes `:app` only when `-PcoreOnly` is absent, and the root
`build.gradle.kts` adds the Android Gradle Plugin to the classpath only in that case too, so the
core-only build never contacts Google Maven.

## CI and releases

[`.github/workflows/android.yml`](.github/workflows/android.yml) runs on every push and pull request:
`:core:test`, `:app:testDebugUnitTest`, then builds `app-debug.apk` and `app-release.apk` and uploads
them as workflow artifacts. On pushes to `main` it also creates a GitHub Release tagged
`v0.1.<yyyymmdd>.<run number>` with both APKs attached.

## Signing (hobby sideload key, intentionally public)

Both debug and release builds are signed with `app/keystore/reeflive-debug.jks`, which is committed
to this repository on purpose:

- store password `reeflive`, key alias `reeflive`, key password `reeflive` (also in `app/build.gradle.kts`)
- PKCS12, RSA 2048, valid 10,000 days

This is **not a secret**. ReefLive is a personal app installed by sideloading, and committing the key
means every CI build is signed identically, so updates install over the previous version. Anyone
with this repository can sign an APK that Android accepts as an update to ReefLive, so only install
APKs from this repository's Releases page. If the app is ever published on a store, generate a new
private key kept in CI secrets instead.
