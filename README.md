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
2. Under **Assets**, tap **`reeflive.apk`** to download it.
   (`reeflive-debug.apk` is the same app with debugging turned on; you only need `reeflive.apk`.)
3. Open the downloaded file (from the download notification or the **Files / Downloads** app).
4. Android will say the browser/Files app is *not allowed to install unknown apps*. Tap
   **Settings**, turn on **Allow from this source**, then go back.
   (On some phones this is under *Settings > Apps > Special app access > Install unknown apps*.)
5. Tap **Install**. If Play Protect warns about an unrecognised developer, tap
   **More details > Install anyway**.
6. Open **ReefLive**.

**Updating (automatic):** ReefLive checks GitHub for a new release when it opens (at most every
6 hours) and shows an **Update available** sheet with what's new. Tap **Download**, then **Install**.
The first time, Android asks you to allow ReefLive to *install unknown apps*: turn on **Allow from this
source** and go back, and the install continues. You can also check by hand in
**More > Settings > Updates > Check for updates**, or turn the automatic check off there.

Every update installs over the old version and keeps all your data: each build has a higher version
number, the same app id (`com.reeflive`) and the same signing key, and the database only ever migrates
forward (it never wipes). You never need to uninstall first. Do not mix in builds signed by anyone
else, or Android will refuse the update. (Builds made before the updater existed have version 1; install
the newest `reeflive.apk` by hand once, and updates come through the app after that.)

The releases are public, so no account or token is needed. *Settings > Updates > Advanced* has an
optional GitHub access token field, only for a private fork: create a fine-grained token limited to
the repository with **Contents: Read-only**.

## Live Tank (coming in the next release)

A 3D recreation of your own tank, planned for the next release:

- **Watch mode**: follow the tank as it runs.
- **Sandbox**: adjust water-parameter controls and try scenarios.
- **Close-up follow mode**: track a single fish or coral up close.
- **Time-lapse test**: fast-forward the tank to see how it changes over time.

## Reef-keeping extras (More tab)

- **Insights**: tank health score (parameters in range, 30-day stability, testing recency, tasks on time),
  stability per parameter, most stable week, alkalinity swing, NO3:PO4 and Mg:Ca ratios.
- **Emergency**: offline step-by-step guides (power outage, ammonia spike, overheating, cold, bleaching,
  salinity, missing fish) and a sick-fish symptom checker.
- **Feeding**: one-tap feeding log per food, daily plan and streak.
- **Supplies**: salt, food, RO/DI, filter media, reagents and meds with low-stock and expiry warnings; restocks
  with a price go to Spending.
- **Quarantine & meds**: courses (copper, prazi, hyposalinity, tank transfer…) with livestock, dose, day X of N
  and a dose log.
- **Coral log**: growth photos and sizes per coral over time, plus a frag log.
- **ICP tests**: paste or type lab results; trace elements flagged against reference ranges and compared with
  your home kits.
- **PAR map**: PAR readings per spot with coral light bands.
- **Spending**: expenses by month, year and category, including livestock prices.
- **Home-screen widget**: health score, key parameters and the next care task.
- Daily reminders for low supplies and due treatment doses (with the daily parameter check).

These are stored in `files/extras.json` (versioned JSON, written atomically) rather than new Room tables, so the
Room schema stays at version 1 and every update stays upgrade-safe. They are included in JSON backups and
removed by "Clear all data".

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
`:core:test`, `:app:testDebugUnitTest`, then builds the debug and release APKs and uploads them as
workflow artifacts (`reeflive.apk` = release, `reeflive-debug.apk`, plus the exported Room schemas).
Versions come from the run: `versionCode = run number + 100`, `versionName = 0.1.<yyyymmdd>.<run>`
(passed as `-PversionCode` / `-PversionName`; local builds default to `1` / `0.1.0`). On pushes to
`main` it also creates a GitHub Release tagged `v0.1.<yyyymmdd>.<run number>` with both APKs and a
`versionCode: N` line in the notes, which the in-app updater compares with the installed version.

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
