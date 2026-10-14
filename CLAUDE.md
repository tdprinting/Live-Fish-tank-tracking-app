# ReefLive: agent quick map

Read this first; open the big docs (`docs/SIMULATION.md` 138 KB, `ASSETS.md` 78 KB, `ARCHITECTURE.md` 48 KB) only
for the section you need (`grep -n "^#" docs/X.md`, then read that range).

## Modules
- `:core`: pure Kotlin sim, AI, chemistry, species (`core/src/main/kotlin/com/reeflive/core`). Also compiled to JS by
  `:web`, so no JVM-only APIs in `core/` or `app/.../render/` (no `java.time`, reflection, threads).
- `:app`: Android (Compose UI, Room). `render/` is the GLES3 renderer, shared unchanged with the web build.
- `:web`: Kotlin/JS host (`web/src/jsMain`) + page UI (`web/site/*.js`); shims map GLES30/Android APIs to WebGL2.

## Commands
- Sim tests: `./gradlew -PcoreOnly :core:test` (one class: `--tests '*SandBedTest'`).
- Web site: `./gradlew -PcoreOnly :web:site` → `web/build/site` (also type-checks the shared renderer).
- `:app` needs the Android SDK and Google Maven: CI only (`.github/workflows/android.yml`).
- Headless sim tuning: `./gradlew -PcoreOnly :core:simRunner`.

## Hot paths (keep allocation-free and cheap)
- Sim: `Simulation.stepWall` at 20 Hz → `advanceInternal` + `snapshot()` (poses for every organism each tick).
- Render: `TankRenderer.onDrawFrame` every frame. Use `ShaderProgram.set*` (it skips unchanged values and redundant
  `use()`), parse GLB extras at load (`DrawPart`/`GpuModel` fields), never build strings or lists per draw.
  `Gl.check(op, arg)` only builds its label when GL debugging is on.
- UI: `LiveTankViewModel.hud` (4 Hz) recomposes the whole live screen; read full-rate `snapshots` only outside
  composition.

## Conventions
- One feature per PR; every merge to `main` publishes an APK and the web site.
- Asset sources: `tools/blender/*.py` → committed GLBs in `app/src/main/assets/models`.
