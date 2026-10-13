# ReefLive on the web (iPhone, iPad, any browser)

The web app is the same simulation and the same 3D renderer as the Android app, compiled to JavaScript.
There is no second copy of the sim, the shaders or the models.

| Piece | Source of truth | How the web build uses it |
|---|---|---|
| Simulation, AI, chemistry, species | `core/src/main/kotlin` | compiled unchanged by `:web` (Kotlin/JS) |
| 3D renderer, camera, gestures | `app/src/main/java/com/reeflive/app/render` | compiled unchanged; `android.opengl.GLES30` runs on WebGL 2 |
| Shaders, GLB models, textures, rock SDF | `app/src/main/assets` | copied to `assets/` and loaded on demand |
| Species / interactions JSON | `core/src/main/resources` | copied to `res/` |
| Page UI (tabs, sandbox buttons, tracker) | `web/site` | plain HTML/CSS/JS |

## How it works

- `web/src/jsMain/kotlin/shims` and `java/`, `android/`, `javax/` declare the handful of JVM and Android APIs the shared
  code calls (`Math`, `String.format`, `java.nio` buffers, `GLES30`, `Bitmap`, `Handler`, `MotionEvent`…) on top of
  browser APIs. They are compiled with `-Xallow-kotlin-package` so `kotlin.Math` etc. are default-imported like
  `java.lang.*` is on the JVM. Images decode asynchronously: a texture shows grey for a moment, then the picture.
- `com.reeflive.web.ReefLive` is the host: it builds the sim from `DefaultStock`, steps it at 20 Hz, draws with
  `TankRenderer` every animation frame and turns pointer events into `MotionEvent`s for the shared `TouchController`.
- Excluded from the web build: `core/care`, `core/extras`, `core/io` (they use `java.time`; the web tracker keeps its own
  simple log) and the Android-only views (`TankGlView`, `TankView`, `DebugHud`).
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
