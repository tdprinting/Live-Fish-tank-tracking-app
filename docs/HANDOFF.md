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
5. ~~Light and flow sliders in %~~: done. Light is absolute peak PAR 0–1500 (`SetParam("par")`), flow is turnover 0–100×/h (`SetParam("turnover")`, no 2× cap), and "Skip routine water changes" (`skip_water_changes`) replaced the two-month jump (`SandboxExtremesTest`).
6. **Heat-death spasm animation** is missing. No spasm code exists in `core` or `app` (SIMULATION §10).
7. **Realism acceptance checks**: all 28 checks in `RealismAcceptanceTest` run, none ignored (SIMULATION §9.10). The wrasse checks (c03–c05) and c04 depend on a single seed, so any change to the RNG sequence can tip them. Notes are below. The file takes about 5.5 min; the slow checks (c20, c06, c12, c08) could still be shortened.

## Next steps
1. **Wire realism sandbox commands into the new sandbox UI.** `RampParam`, `ReleaseParam`, `Schedule`, `PowerheadFail`, `RearrangeRock`, `SetAcclimation`, `Photoperiod`, `pump.<name>`, `spectrum.<channel>`. The UI hooks are marked `TODO(sim/realism)`: `SandboxControlMode` in `SandboxParams.kt` (enable Ramp/Schedule and the selector appears), the per-mode branch in the `LiveTankScreen.kt` slider handler, and the disabled photoperiod/pump/spectrum rows. Also `runPreset` "salinity crash" (ramp, not step).
2. **Algae follow-ups:** check that `Simulation.rebuildWater()` carries algae state across a water rebuild. Algae spec fixes: research 09 check 2 expects PO4 to fall 0.3–0.4 ppm from a 0.2 ppm start, which is impossible. Assert instead that PO4 reaches its floor and is reported as the limiting nutrient.
3. **Owner-reported bugs** (full list in `docs/WORKLIST.md`): fish snap to fixed spots and ignore time-speed changes; cleaner shrimp animation; fish nearly invisible; NO3/PO4 red at sandbox start; day/night label vs photoperiod.
4. **Coverage gaps** (`docs/research/coverage_gaps.md`), top items: coral growth/spread, real-time shadows, disease visuals beyond ich, clownfish spawning / cardinal mouthbrooding, coral-vs-coral warfare, Aiptasia spread + predators, dinos/bubble algae, tracker cycle/acclimation/pest log.

## sim/realism: per-check notes (all passing; details in SIMULATION §9.10)
- **c02:** a sensitised host defender re-rolls every 60 s × (latency factor)². The cap is a token bucket that holds max(1, ¼ h of the cap).
- **c03–c06:**
  - the wrasse weights clients by area, due/overdue and lapsing aggressors;
  - pose need recovers over 2000 s;
  - cheating runs at 0.025/s, or 0.17/s when hungry;
  - rest is ×(1.3 − hunger);
  - `Ctx.viaOpenWater` gets swimmers out of and into rock pockets;
  - damping scales the cap.
- **c05:** the test counts only the wrasse's own jolts.
- **c08:** past familiarity 0.6, standoff rows shrink their halo to 40 %. Damping is skipped between strangers and for the dominance quiver.
- **c09:** a new tailspot prospects the tank on day 1.
- **c15:** night rolls run every step, with one roll per actor and row per night.
- **c16:** the shrimp counts approaches in `preTick`.
- **c20:** `RearrangeRock` keeps intrusion memory, caps familiarity at 0.5, and boosts territorial bouts ×(1 + 2·e^(−t/12 h)). The test reads the reset event from the run's own events, because the eventLog rolls over.
- **c22, c25:** thermal acclimation shifts optHi/tolHi up by at most 3 °C. The tests clear spontaneous ich (`noIch()`).
- **c24, c27:** StressModel `slowGain` (NH3 0.35), a linear social envelope, and a 120 s social reservoir. c27 uses `ChemFns.freeNh3`.
- **Research 07 gaps:** `ResearchGapsTest` gap1–gap5 (lawnmower size ceiling and 0.9/0.2 odds, `SwimSpeed` bounds, goby night patrol, conspecific shrimp molt kills with minGroup 3, Sally Lightfoot one roll per night and size-gated).
- Fish can still get trapped in rock pockets in general steering (there is no path-finding). Only the wrasse's approach and VisitCleaner go round it.
- Not implemented from 06: feeding displacement by rank (§3.2), tang `blueDeepen`, CBS/hermit claw display, hermit shell fights, coral sign hooks, territory re-anchoring after `RearrangeRock`.
- Deviations from spec, kept deliberately: goby night patrol about 5 % (07 gap 3); banded-on-skunk shrimp kills behind `crossSpeciesShrimpKills`, default off (07 gap 4); only a hungry Sally Lightfoot rolls for prey; DO_crit floors at its 29 °C value with onset 1.4 × DO_crit (needed for #18 and #23 together); crowding gain 8; cardinal hover radius base 12 cm.
