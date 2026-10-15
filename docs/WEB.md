# ReefLive on the web (iPhone, iPad, any browser)

The web app is the same simulation and the same 3D renderer as the Android app, compiled to JavaScript.
There is no second copy of the sim, the shaders or the models.

| Piece | Source of truth | How the web build uses it |
|---|---|---|
| Simulation, AI, chemistry, species | `core/src/main/kotlin` | compiled unchanged by `:web` (Kotlin/JS) |
| 3D renderer, camera, gestures | `app/src/main/java/com/reeflive/app/render` | compiled unchanged; `android.opengl.GLES30` runs on WebGL 2 |
| Shaders, GLB models, textures, rock SDF | `app/src/main/assets` | copied to `assets/` and loaded on demand |
| Species / interactions JSON | `core/src/main/resources` | copied to `res/` |
| Sandbox table, tips, sound mix, fish-card state | `app/.../ui/live/SandboxParams.kt`, `LiveTips.kt`, `LiveUiState.kt`, `app/.../audio/TankSoundMix.kt` | compiled unchanged (plain Kotlin) |
| Tank sounds | `app/src/main/res/raw/*.ogg` | copied to `sounds/`, plus `.m4a` copies for Safari when ffmpeg is present |
| Page UI (live screen, tracker) | `web/site` (`live.js`, `app.js`) | plain HTML/CSS/JS |

## How it works

- `web/src/jsMain/kotlin/shims` and `java/`, `android/`, `javax/` declare the handful of JVM and Android APIs the shared
  code calls (`Math`, `String.format`, `java.nio` buffers, `GLES30`, `Bitmap`, `Handler`, `MotionEvent`…) on top of
  browser APIs. They are compiled with `-Xallow-kotlin-package` so `kotlin.Math` etc. are default-imported like
  `java.lang.*` is on the JVM. Images decode asynchronously: a texture shows grey for a moment, then the picture.
- `com.reeflive.web.ReefLive` is the host: it builds the sim from `DefaultStock`, steps it at 20 Hz, draws with
  `TankRenderer` every animation frame and turns pointer events into `MotionEvent`s for the shared `TouchController`.
- `com.reeflive.web.ReefSandbox` plays the Android `LiveTankViewModel` + `SimulationHolder`: Watch shows the real tank
  at 1x (faster time plays on a copy), Sandbox forks it into a pretend tank where every lever works and Reset tank
  restores the fork. The page mirrors the Android live screen: Watch / Sandbox, clock, Tank happy meter, Hermie, the
  water strip, the menu rail (Watch, Supplies, Add life, Tank, Trouble, More with the grown-up sliders), the 3D supply
  shelf, fish card and follow (with the "Thinking…" lines), More → Thought bubbles (`ReefSandbox.bubbles`, the
  shared `ThoughtLines.kt` picker drawn as HTML bubbles), auto tour, lamp looks, torch, clean glass, microscope with the bacteria panel,
  time-lapse with per-species verdicts, and the tank sounds. When an Android live-screen action is added, add it to
  `ReefSandbox.act` and a tile in `web/site/live.js`. The time-lapse setup tables and the supply list are small copies
  of `TimelapseSheet.kt` / `SupplyShelf.kt` (Compose files); keep them in step.
- Excluded from the web build: `core/care`, `core/extras`, `core/io` (they use `java.time`; the web tracker keeps its own
  simple log) and the Android-only views (`TankGlView`, `TankView`, `DebugHud`).
- Updates: every push to `main` (the same pushes that release the APK) rebuilds and republishes the web app; the
  service worker loads new code on the next launch.
- Graphics default to HIGH (bloom, light shafts, 4x MSAA, full device pixel ratio up to 3x); Speed → Picture quality lowers it.
- Parameter readings are stored in the browser (`localStorage`) and can be exported/imported as JSON. New readings are
  pushed into the live tank with the same `RealityInit` mapping the Android app uses.
- It is a PWA: `manifest.webmanifest` + `sw.js` let Safari "Add to Home Screen" and run it full screen and offline.

## Build and run

```
./gradlew -PcoreOnly :web:site          # → web/build/site
cd web/build/site && python3 -m http.server 8765   # open http://localhost:8765
```

CI (`.github/workflows/web.yml`) builds the site on every PR and publishes `main` to the `gh-pages` branch, served at
https://tdprinting.github.io/Live-Fish-tank-tracking-app/ once Settings → Pages uses the `gh-pages` branch.

## Keeping it compiling

Shared code that starts using a new JVM-only or Android API breaks the web build (CI's "Web app" job shows it).
Prefer `kotlin.math` / Kotlin stdlib in `:core`; if a JVM API is needed, add a small shim next to the others.

## What's new popup

When the page opens it shows the updates merged to `main` since this browser last opened it (`reeflive.newsSeen` in
localStorage), and More → ✨ What's new lists the latest ones any time. The list is `whats-new.json`, written by the
`:web:whatsNew` task from `git log --first-parent` on main, so every thread's merged PR appears with its title.
To reword a title in plain language, or hide a behind-the-scenes change with `""`, add it by PR number to
`docs/whats-new.json`.

## Look and icons

The live screen uses the Android "Sea-glass Arcade" live theme (UI_STYLE §10.1): navy outlines, candy-coloured
bubbles and tiles per category, teal glass sheets, Outfit for labels (`web/site/style.css`, last block). Icons come
from the app's own sets: `web/site/glyphs.js` is generated from `ReefGlyphs.kt` (plus a few web-only glyphs) and
`web/site/food/*.png` from the FoodArt drawings by `python3 tools/graphics/web_glyphs.py`; species pictures are copied
from `app/src/main/res/drawable-nodpi` at build time. New tiles take a glyph name, `food:KIND` or `species:id:glyph`.
