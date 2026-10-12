# ReefLive — Implementation Plan

Three parallel tracks with explicit interfaces. Every milestone has acceptance criteria that a reviewer can check without reading code. Nothing is "done" until CI is green on `main` and the APK installs on a phone.

Tracks:
- **A — Core + Tracker app** (Kotlin/JVM `:core`, Android `:app` UI/data).
- **B — Blender assets** (`tools/blender/*.py`, committed GLB/PNG, SDF/anchors).
- **C — Renderer + simulation integration** (`app/render`, `core/sim`, `core/ai`, Live Tank UI).

Interfaces frozen at M0 (contracts):
1. `core.model.*` data classes and `ParameterTable` (ARCHITECTURE §4.2).
2. `SimSnapshot`/`OrganismPose`/`CoralPose`/`Command` (ARCHITECTURE §8.8, SIMULATION §1.3).
3. Asset contract: GLB feature subset, `extras.part`, vertex-color channels, unit-length fish facing +X, anchors.json schema, SDF file format (ASSETS §1, §3.4, SIMULATION §5.2).
4. Species JSON schema (SIMULATION §3.2).

---

## M0 — Repository skeleton and CI (Track A lead; 1 day)
Deliverables: `settings.gradle.kts` with conditional `:app`, version catalog, wrapper 8.11.1 (jar committed), `:core` with one test, `:app` with a Compose "Hello reef" screen, `.github/workflows/android.yml`, committed keystore, `.gitattributes`, README with install steps for Tee (download APK from Releases → enable unknown sources → install).
Acceptance:
- `./gradlew -PcoreOnly :core:test` passes locally (no Android SDK).
- CI run on `main` produces `app-debug.apk` + `app-release.apk` artifacts and a GitHub Release tagged `v0.1.<date>.<run>`.
- APK installs and shows the placeholder screen.

## M1 — Core domain (Track A; 3 days)
- `units`, `params` (all 4 reef-type tables), `status` (StatusEngine, Trend, InsightEngine), `chem` (Ammonia, Oxygen, Dosing, WaterChange, SaltMix, Evaporation), `io` (CSV, JSON backup), `seed` (Tee's tank JSON + models), `species` (all 17 species JSON sheets).
Acceptance (unit tests, all in `:core`):
- Conversion round trips within 1e-9; SG 1.025 → 33.3 ppt; 8 dKH → 2.856 meq/L → 143.2 ppm.
- StatusEngine table test: 24 cases incl. fast-change and stale.
- Ammonia worked example (f = 0.0825 ± 0.001 at 25 °C/pH 8.2).
- Dosing: 60 gal +10 ppm Ca → 6.3 g CaCl2 anhydrous ± 0.1; +1 dKH → 6.8 g NaHCO3 ± 0.1.
- Seed parses; species DB loads 17 sheets with tolerance tables validated (optLo ≤ optHi ≤ tolHi ≤ lethalHi).

## M2 — Data layer + theme + navigation (Track A; 3 days)
- Room entities/DAOs/migrations (schema v1 exported to `app/schemas`), repositories, DataStore settings, `SeedInstaller`, manual DI `AppGraph`.
- Theme: all tokens, fonts bundled (Outfit, Inter), `GlassCard`, `CausticBackground`, `StatusPill`, bottom bar, NavGraph with 5 empty tabs.
Acceptance:
- Fresh install → onboarding → Dashboard shows the caustic background and glass cards in dark theme; light theme switches live.
- Seed installs: Livestock list shows 17 entries, Equipment 8, Tasks 12.
- Reduce-motion toggle freezes the background.

## M3 — Logging, dashboard, charts, alerts (Track A; 5 days)
- QuickLogSheet, ParametersScreen, ParameterDetail with `TimeSeriesChart` (bands, markers, scrub, zoom, overlay), `Sparkline`, ParameterTile/HeroTile, AlertEvaluator + notifications + daily worker, export/import.
Acceptance:
- Stopwatch test: log Alk+Ca from a cold open in < 10 s.
- Chart shows ideal/acceptable/danger bands exactly at the table values; scrub tooltip shows kit brand.
- Enter Alk 8.6 then 7.2 the next day → "Danger: Alk fell 1.4 dKH/day" alert + notification.
- Export CSV, wipe, import → identical row count and values.
- No frame drops > 16 ms while scrolling the Dashboard with 8 tiles (Macrobenchmark optional; use GPU profiler bars).

## M4 — Care, calculators, livestock, equipment, journal, lights, settings (Track A; 5 days)
Acceptance:
- Task completion reschedules the reminder; the "Done" notification action works with the app closed.
- Each calculator matches the unit tests' numbers; "Log this dose" creates an event visible as a chart marker.
- Livestock detail shows the tank-size warning for the tang and the eel; compatibility chips match the interaction matrix.
- Light schedule editor saves points and the swatch strip updates.
- Journal photo picked from the gallery is stored EXIF-stripped in app files.

## B1 — Asset pipeline bootstrap (Track B; 2 days, starts at M0)
- `fishlib.py`, `paintlib.py`, `export_glb` helper, `build_all.py` skeleton, `tank.py` (glass, sand, rim), `caustics.py`, water normal maps, `irid_lut`, `room_env`.
Acceptance:
- `python tools/blender/build_all.py --only tank` writes `tank.glb` with nodes named per ASSETS §5 and `COLOR_0` present; file < 1 MB.
- A tiny Python validator `tools/validate_glb.py` (uses only json/struct) asserts: GLB magic, required attributes, `extras.part` on every mesh node, triangle budgets.

## B2 — Fish and shrimp (Track B; 5 days)
All 8 fish GLBs + LOD1 + textures, shrimp, species card renders.
Acceptance:
- Validator passes; each body ≤ budget; `COLOR_0.a` runs 0→1 along +X (checked by the validator by sampling min/max x vertices).
- `preview.py --fish all` renders a contact sheet `docs/preview/fish.png`; reviewer compares with reference photos: clown bars at the specified u positions, cardinal's black band + spots, wrasse stripe, eel blotches with yellow dots, blenny eye lines, hawkfish bars.

## B3 — Rockscape, corals, equipment, SDF, anchors (Track B; 5 days)
Acceptance:
- `docs/preview/scene.png` next to `tank_full.jpg`: reviewer checks the 8 elements' positions within ±5% of tank length and the cave/arch openings exist.
- `rock_sdf.bin` size as specified; `anchors.json` has every name listed in ASSETS §3.4 with snapped positions where `sdf ≈ 0`.
- Polyp instance tables present in extras; total rendered triangle estimate printed ≤ 140k at High.

## C1 — Simulation core (Track C; 6 days, starts after M0; uses analytic `RockData.ofSpheres` until B3)
- `geom` (Vec3, Mat4, Spline, SpatialHash, SdfGrid + analytic), `FlowField`, `WaterSim`, `HealthModel`, `CoralSim`, `FoodSystem`, `LightSchedule`, `Simulation` loop + commands + snapshots, `Steering`, `UtilityBrain`, common behaviors, all species brains.
Acceptance: the 10 tests in SIMULATION §8 pass; a headless `SimRunner` CLI (`core/src/test/.../SimRunnerMain.kt`) prints a 24-h log of water state and organism states for a scenario, used to tune constants.

## C2 — Renderer (Track C; 7 days; can start with `tank.glb` from B1 and placeholder capsules for fish)
- `TankGlView`, `GlbLoader`, `Mesh/Texture/ShaderProgram/Material`, all shaders, `Camera`/`TouchController`/`Picker`, `Effects`, `LightingState`, `FishRig`/`CoralRig`, quality levels, `DebugHud`.
Acceptance:
- Loads every GLB from B2/B3 (or placeholders) without GL errors (`glGetError` checked in debug).
- Caustics visible on sand/rock and animated; fog deepens with distance; water surface shows TIR when looking up; glass Fresnel on the front pane.
- Fish deform along a scripted spline (test harness moves a spline in a circle) with no tearing; fins flutter; ich spots appear with `uIchSpots = 1`.
- 1080p Medium on a mid-range phone ≥ 30 fps with all assets; High on a flagship ≥ 55 fps.

## C3 — Live Tank integration (Track C + A; 4 days)
- `LiveTankScreen`, overlay HUD, sandbox panel, fish card, `SimulationHolder` wiring to repositories (reality init from the latest measurements; light schedule from the DB), follow camera, tap glass, food drop, scenarios, events toasts, Dashboard peek thumbnail capture.
Acceptance:
- Reality mode reflects a just-logged temperature change within 2 s of returning to the tab.
- Sandbox: set temp 31 °C at 240x → within ~2 sim days the tang shows white spots; at 1000x for 3 weeks → tang dies and floats; "Reset" restores.
- Feed → frenzy: clowns and tang reach the surface within 3 s, hawkfish pounces from a perch, shrimp picks settled food later.
- Night: clowns nestle in the Kenya, tang wedges in the crevice, eel roams; dawn ramp colors match the light schedule editor swatch.
- Tap a fish → camera follows and the card shows live bars; tap glass → nearby fish flinch.

## M5 — Polish, accessibility, performance (all; 3 days)
Acceptance: TalkBack reads tile statuses; font scale 1.3 does not clip; cold start < 1.5 s; APK ≤ 60 MB; no ANR with 10k measurements imported; Live Tank pauses when backgrounded and resumes correctly.

## M6 — Release candidate (1 day)
Acceptance: version 1.0.0 tagged; Release notes list features; Tee installs from GitHub Releases over the previous build without uninstalling (same keystore).

---

## Parallelism and hand-offs
```
Week 1: M0 → M1 (A)           B1 (B)                 C1 start (C, analytic rock)
Week 2: M2, M3 (A)            B2 (B)                 C1 finish, C2 start (C)
Week 3: M4 (A)                B3 (B)                 C2 (C)
Week 4: M5 prep (A)           B fixes from C feedback C3 (C + A)
Week 5: M5, M6
```
Hand-off artifacts: B → C: GLBs + `validate_glb.py` green + `anchors.json` + `rock_sdf.bin`. A → C: `MeasurementRepository.latestValues()`, `LightScheduleRepository.active()`, `LivestockRepository.alive()` Flows. C → A: `SimSnapshot` for the Dashboard peek and the Live tab.

## Definition of done (every PR)
- `./gradlew -PcoreOnly :core:test` green locally; CI green.
- No new raw hex colors in `ui/` outside `theme/`.
- New constants documented in the relevant docs file (keep docs as the source of truth; update them when tuning).
- Screens have a Compose `@Preview` in both themes.

## Risks and mitigations
| Risk | Mitigation |
|---|---|
| Maven Central rate limits locally | Gradle offline cache; retry; CI is the build of record |
| GLB loader edge cases from Blender exporter | validator + fixed exporter settings; loader ignores unknown fields |
| Fish AI looks robotic | utility hysteresis + wander jitter + per-individual temperament noise (±15% on sheet values seeded per organism) |
| Performance on low-end devices | quality presets; instance counts halve at Medium, quarter at Low; LOD1 beyond 60 cm |
| Species IDs wrong (hawkfish, blenny) | `verified=false` badge; species switchable in livestock edit; models for flame hawkfish/midas blenny can be added later as new sheets + GLBs without code changes |
| Tee's tank differs from the assumed 48x13x24 | all dimensions in seed/config; the rockscape scales by tank length/height but the layout is fixed to this tank; an "unknown tank" would use the same scape |
