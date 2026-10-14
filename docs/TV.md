# ReefLive on a TV (Fire TV / Android TV)

The normal `reeflive.apk` also installs on Fire TV and Android TV. On a TV it adds:

- **TV app** (`tv/TvTankActivity`): opening ReefLive on a TV (detected by `tv/isTv`) always goes straight to
  the live tank full screen; the phone tracker screens never show there.
  The camera sways slowly across the tank and every ~90 s glides in to follow one animal for ~35 s.
  Remote: ◀ ▶ turn, ▲ ▼ zoom (manual moves pause the auto camera for 30 s), OK opens a one-row menu
  (Install update when one is ready, Whole tank / Fish tour, Next fish, Lights, Close) whose selected button
  is solid bright with a white ring; Back closes the menu, or exits.
- **Screensaver** (`tv/TankDream`, an Android DreamService) showing the same view.

Both share `tv/TankWatch` (plain views, no Compose, so the dream can host it). TV GPUs are much weaker than
a phone's and often get a 4K surface, so the tank renders into a 720-line buffer the display scales up, on the
MEDIUM preset without MSAA, stepping down (LOW at 720, 540, then 432 lines) while the frame rate stays under 26 fps. A fresh TV install seeds Tee's tank.

## Updates
The TV app checks GitHub Releases (every 6 h while it runs), downloads a new APK on its own, then shows
"Update ready · press OK" in the top corner; OK → Install update opens the system installer (Android
never installs silently). The first time, it asks to allow "Install unknown apps" for ReefLive.

## Install on Fire TV
1. Fire TV Settings → My Fire TV → Developer options: turn on **Install unknown apps** for Downloader
   (if Developer options is hidden: My Fire TV → About → click the device name 7 times).
2. Install **Downloader** (AFTVnews) from the Amazon app store and open it.
3. Enter `https://github.com/tdprinting/Live-Fish-tank-tracking-app/releases/latest/download/reeflive.apk`, install.
4. Open ReefLive from Your Apps (hold Home → Apps to pin it to the front row).

## Use it as the Fire TV screensaver
Fire OS has no picker for third-party screensavers, so set it once over adb (ADB debugging on in
Developer options; `adb connect <tv-ip>` from a computer, or an on-TV adb app):

```
adb shell settings put secure screensaver_components com.reeflive/com.reeflive.app.tv.TankDream
```

Undo with `adb shell settings delete secure screensaver_components`. Some Fire OS updates reset it.
On Google TV / Android TV pick it in Settings → System → Ambient mode / Screen saver.
