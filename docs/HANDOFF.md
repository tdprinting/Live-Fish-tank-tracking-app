# ReefLive handoff (2026-10-12, session 3)

## Start here (next AI)
1. Read this file, then `docs/WORKLIST.md` (owner-reported bugs) and `docs/research/coverage_gaps.md`.
2. `git fetch origin` and check **Status** below. Open items come first, in the order listed there.
3. Use one branch per item, open a PR, and merge when CI is green.
4. The owner tests on a phone (APK auto-updates from `main`) and reports bugs with screen recordings. To review a video, use ffmpeg to make a 1 fps contact sheet: `ffmpeg -i in.mov -vf "fps=1,scale=640:-1,tile=4x3" -frames:v 1 sheet.jpg`.

Repo: https://github.com/tdprinting/Live-Fish-tank-tracking-app (public). Every push to `main` builds an APK release and the app updates itself, so only merge green, compiling work.

## Working rules (from Tee)
- Model routing: **Fable** designs/researches, **Opus** implements, **Sonnet** debugs/helps, **Haiku** small tasks.
- One branch per piece of work. Open a PR, merge when CI is green.
- Be token-efficient: delegate, read big docs in sections, don't poll.
- Merge base branches in (no rebase/force-push on shared branches).

## Environment gotchas (cloud container)
- No Android SDK. **CI is the app build.** Gradle dependency downloads are often blocked (403/429), so `./gradlew -PcoreOnly :core:test` may only work `--offline` after a cache exists.
- `gh` CLI is not authenticated. Use the GitHub MCP tools: `actions_run_trigger` (dispatch `android.yml` on a ref), `actions_get`, `actions_list`, `get_job_logs`.
- android.yml has concurrency `android-${ref}` with cancel-in-progress: one run per branch at a time.
- CI artifact downloads (blob storage) are blocked by the proxy. `app/schemas/1.json` must be fetched by Tee from a run's artifact page and committed before any Room v2 migration.
- Blender: `pip install bpy` works (bpy 5.2.x). Run generators with `python3 tools/blender/<module>.py <asset>`. Validate with `tools/validate_glb.py` and `tools/check_tank_spec.py`.
- WebFetch can't reach hosts; research relies on WebSearch excerpts (noted as caveats in 08/09).
- Known CI trap: branch runs use a read-only Gradle cache. When a branch doesn't change app Kotlin sources, KSP is restored from cache and Room never writes `app/schemas/.../1.json`, so `DatabaseUpgradeSafetyTest.schemaJsonForEveryVersion` fails. The fix in 9975c3c (`./gradlew :app:kspDebugKotlin --rerun` before the app tests) is on `main`. Committing `app/schemas/.../1.json` is the permanent fix.

## Docs
- `docs/` DESIGN, ARCHITECTURE, SIMULATION (§10 heat, §11 algae, §12 time-lapse), ASSETS, PLAN, UI_STYLE, WORKLIST (live bug/feature list).
- `docs/research/01–05` (original), `08_extreme_heat.md`, `09_algae_and_extremes.md`, `coverage_gaps.md` (56-row audit of 01–05 + tank spec against code).
- Research 06 (interactions) and 07 (gap sources) are **not yet in the repo**. Code cites 06 (`PairMatrix`, `interactions.json`). They live in Tee's `ReefLive_research_compendium.md` (lines 3057–3905). Ask Tee to upload it, then commit them as `docs/research/06_interactions_analysis.md` and `07_gap_sources.md`.

## Status
Everything below is on `main` (PRs #10–#19).

**Done**
- Inline sandbox dock, ReefSlider, extreme ranges and presets (#10). Higher-detail tank models and CI Room schema fix (#12).
- Realism (research 06): two-time-constant `StressModel` channels, `PairMatrix` interactions, cleaner station (#13).
- Time-lapse engine and change test with a per-species report (#14).
- Catastrophic heat: agitation, gasping, lethargy on the bottom, loss of equilibrium, death by species, recovery, delayed mortality (#15; research 08, SIMULATION §10).
- Algae growth model: seven groups on four surfaces, nutrient and light limits, grazers (#16; research 09 Part A, SIMULATION §11).
- Owner bug fixes: critically damped follow-camera yaw, fish body-bend limits so bodies don't fold through themselves, colour fixes (Kenya tree, zoanthids, glass reflections, palette) (#17).
- Per-surface algae cover rendered on glass, sand, rock tops and shaded rock (#18).
- Equipment re-mounted on the rim (pucks on goosenecks, HOB filter and skimmer hung on the rim, spill tray and intake), Flipper seated flush on the glass (#19).

**Open** (in priority order)
1. **Microfauna and a micro zoom view**: research 10 in progress.
2. **Species behaviours** (spawning, shells, lifespan): in progress.
3. **Coral growth, pests and chemistry**: in progress.
4. **Tank sounds**: PR open, not yet merged.
5. **Light and flow sliders** still use % instead of PAR (µmol m⁻² s⁻¹) and turnover. The sim caps flow at 2× normal, and the research 09 range goes higher.
6. **Heat-death spasm animation** is missing. No spasm code exists in `core` or `app` (SIMULATION §10).
7. **Realism acceptance checks**: 15 checks in `RealismAcceptanceTest` are still `@Ignore`d (WIP). Per-check notes are below. The file takes about 5.5 min; shorten the slow checks (c20, c06, c12, c08) too.

## Next steps
1. **Wire realism sandbox commands into the new sandbox UI.** `RampParam`, `ReleaseParam`, `Schedule`, `PowerheadFail`, `RearrangeRock`, `SetAcclimation`, `Photoperiod`, `pump.<name>`, `spectrum.<channel>`. The UI hooks are marked `TODO(sim/realism)`: `SandboxControlMode` in `SandboxParams.kt` (enable Ramp/Schedule and the selector appears), the per-mode branch in the `LiveTankScreen.kt` slider handler, and the disabled photoperiod/pump/spectrum rows. Also `runPreset` "missed water changes" (disable routine changes) and "salinity crash" (ramp, not step).
2. **Algae follow-ups:** check that `Simulation.rebuildWater()` carries algae state across a water rebuild. Algae spec fixes: research 09 check 2 expects PO4 to fall 0.3–0.4 ppm from a 0.2 ppm start, which is impossible. Assert instead that PO4 reaches its floor and is reported as the limiting nutrient.
3. **Owner-reported bugs** (full list in `docs/WORKLIST.md`): fish snap to fixed spots and ignore time-speed changes; cleaner shrimp animation; fish nearly invisible; NO3/PO4 red at sandbox start; day/night label vs photoperiod.
4. **Coverage gaps** (`docs/research/coverage_gaps.md`), top items: coral growth/spread, real-time shadows, disease visuals beyond ich, clownfish spawning / cardinal mouthbrooding, coral-vs-coral warfare, Aiptasia spread + predators, dinos/bubble algae, tracker cycle/acclimation/pest log.

## sim/realism: per-check tuning notes (from the implementing agent)
- c04/c06: the wrasse doesn't reach every client, and baseline clown chases hit the hourly cap. Give overdue clients top priority, apply tactile damping to the cap, re-roll the target at most once per minute.
- c20: measure territory holders only; don't clear intrusion memory on `RearrangeRock`.
- c02: bouts per entry not increasing yet. c03: tang cleaning totals 1.6 min per 10 h (target 2–10). c05: trust settles at 0.42 (target < 0.4).
- c08: sample at day 7. c09: use a per-roll probability. c11: re-check the 2–10 min hawk perch times. c15: run past the 02:00 roll. c16: move approach handling into the shrimp's per-tick update.
- c22: null pointer, probably a fish dying during the step run. c24: tap stress peak too small and too early. c25: test StressModel directly (fish die after 60 d at 31 °C). c27/c18: use `ChemFns.freeNh3` for the free NH3 fraction.
- Not implemented from 06: feeding displacement by rank (§3.2), tang `blueDeepen`, CBS/hermit claw display, hermit shell fights, coral sign hooks, territory re-anchoring after `RearrangeRock`.
- Deviations from spec, kept deliberately: goby night patrol about 5 % (07 gap 3); banded-on-skunk shrimp kills behind `crossSpeciesShrimpKills`, default off (07 gap 4); only a hungry Sally Lightfoot rolls for prey; DO_crit floors at its 29 °C value with onset 1.4 × DO_crit (needed for #18 and #23 together); crowding gain 8; cardinal hover radius base 12 cm.
