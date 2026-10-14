# ReefLive on a TV (Fire TV / Android TV)

The normal `reeflive.apk` also installs on Fire TV and Android TV. On a TV it adds:

- **TV app** (`tv/TvTankActivity`): a ReefLive tile in the TV launcher that opens the live tank full screen.
  The camera sways slowly across the tank and every ~90 s glides in to follow one animal for ~35 s.
  Remote: ◀ ▶ turn, ▲ ▼ zoom (manual moves pause the auto camera for 30 s), OK toggles the fish tour,
  ☰ / play-pause cycles the lamp look (real clock, day, dusk, night, moonlight), Back exits.
- **Screensaver** (`tv/TankDream`, an Android DreamService) showing the same view.

Both share `tv/TankWatch` (plain views, no Compose, so the dream can host it). Rendering starts on HIGH and
steps down to MEDIUM, then LOW, if the TV's GPU stays under 24 fps. A fresh TV install seeds Tee's tank.

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
