# ReefLive handoff (refreshed 2026-10-27, main past #210)

## Start here
1. Read this file, then `docs/WORKLIST.md` (owner-facing open items) and `docs/research/coverage_gaps.md` (short status table).
2. `git fetch origin`, check **Status** and **Open** below. Work Open items top-down, one branch per item.
3. The **web app (`web/`) is the shipping product** (Kotlin/JS plus `web/site/`). It shares `core/` and the `app/ui/live` tables (SandboxParams, LiveTips, TankSoundMix) and `app/render` code. The `:app` Android module still exists but is **not built in CI** (the APK build was removed in #195).
4. The owner tests on a phone browser and sends screen recordings. Review a video with a 1 fps contact sheet: `ffmpeg -i in.mov -vf "fps=1,scale=640:-1,tile=4x3" -frames:v 1 sheet.jpg`.

Repo: https://github.com/tdprinting/Live-Fish-tank-tracking-app (public).

## Working rules
- Model routing: **Fable** designs/researches, **Opus** implements, **Sonnet** debugs/helps, **Haiku** small tasks.
- One branch per item. Merge a PR when CI is green.
- Be token-efficient: delegate, read big docs in sections, don't poll.
- Merge base branches in; never rebase or force-push shared branches.

## Environment gotchas (cloud container)
- CI: `android.yml` (named "Core tests", runs `core` tests) and `web.yml` (web build). Dispatch with the GitHub MCP `actions_run_trigger` on a ref; inspect with `actions_get`, `actions_list`, `get_job_logs`. `gh` is not authenticated.
- Each workflow has per-ref concurrency with cancel-in-progress: one run per branch at a time.
- No Android SDK, so `:app` cannot be compiled here or in CI. Gradle downloads are often blocked (403/429); `./gradlew -PcoreOnly :core:test` may only work `--offline` once a cache exists.
- CI artifact downloads (blob storage) are blocked by the proxy.
- Blender: `pip install bpy` works. Run `python3 tools/blender/<module>.py <asset>`; validate with `tools/validate_glb.py` and `tools/check_tank_spec.py`.
- WebFetch can't reach hosts; research relies on WebSearch excerpts.
- `RealismAcceptanceTest` (28 checks, ~5.5 min) has seed-dependent wrasse checks (c03-c05); any RNG-sequence change can tip them. Per-check notes: SIMULATION §9.10.
- Known deviations kept on purpose: goby night patrol ~5 %; cross-species shrimp kills behind `crossSpeciesShrimpKills` (off); DO_crit floor at its 29 C value; crowding gain 8. No path-finding: fish can still get trapped in rock pockets in general steering.
- Research 06 and 07 live only in Tee's compendium (06 now exists as `docs/research/06_interactions_analysis.md`; check for 07 before citing it).

## Docs
`docs/` DESIGN, ARCHITECTURE, SIMULATION (§10 heat, §11 algae, §12 time-lapse), ASSETS, PLAN, UI_STYLE, WORKLIST; `docs/research/` numbered notes plus `coverage_gaps.md`.

## Status (all on main)
- **Core sim and realism:** StressModel, PairMatrix, cleaner station (#13); time-lapse engine (#14); catastrophic heat sim (#15); algae growth model (#16, #18, #38); fish glitch and high-speed behaviour fixes (#82, #134, #170); 28 realism acceptance checks.
- **Sandbox:** inline dock, extreme ranges, presets (#10, #16); realism commands (ramps, schedules, photoperiod, powerhead fail, acclimation, rock rearrange, pumps, spectrum) wired in web Sandbox.kt; real units for light as PAR 0-1500, flow as turnover 0-100x/h, and a "Skip water changes" switch (#209).
- **Corals, pests, chemistry:** growth and spread (#25, #27, #155), bleaching and death (#155), Aiptasia/berghia (#151), many coral species (#145, #147, #161, #162, #191, #201).
- **Algae outbreaks:** brown dino strands, red slime cyano, green bubble algae (#210).
- **Fauna:** microfauna and microscope view (#26, #41, #103, #179); breeding: clownfish spawning (#118), cardinal mouthbrooding (#164), others (#22, #144); hermit shell fights (InvertEcology); about 30 species PRs; cleaner shrimp animation (#40, #108, #173).
- **Rendering:** lighting, rim, outlines, fish lit correctly (#52, #72, #95); real-time shadows (#52, #95, #174); algae cover on glass/sand/rock (#18).
- **Sound:** tank sounds (#20, #64, #102, #196), radio (#169, #186).
- **Web UI:** web app (#48, #63), bigger tank and iPhone look (#90, #101), menus (#39, #140), My tank / room / economy (#107, #140, #141, #157, #168, #178, #184, #192, #199).
- **Performance:** earlier passes (#94, #132, #193, #202); graphics performance with render scale, Auto quality and relief levels, frame cap, culling, settings and FPS overlay (#206); sim performance with SimBenchmark, allocation cuts and the `poseDetail` hook (#207); pose LOD wired in web (#208).
- **Pending (feat/fish-health-visuals, about to merge):** heat-stage and disease visuals: gasping, lethargy, loss of equilibrium, spasms, dead fish sink then float, velvet, fin rot, HLLE, Brooklynella.

## Open (ranked by value)
1. **Verify fish-health visuals in motion** once merged (unverified; owner or screen-recording check on a phone).
2. **Device numbers for #206 thresholds:** the Auto-quality/FPS thresholds are untested on a device. Ask the owner for `?stats` overlay numbers (FPS, frame time) on their phone and tune.
3. **PAR bleaching is too fast:** about 1-2 days at 800 PAR versus 3-7 days in research (09/coral docs). Retune `CoralGrowth`/`CoralSim` bleach timing.
4. **Remaining behaviours (checked by grep):** feeding displacement by rank (06 §3.2) and tang `blueDeepen` are not implemented. Also not done: CBS/hermit claw display, coral sign hooks, territory re-anchoring after `RearrangeRock`. Coral-vs-coral warfare is partial (sweeper tentacles #162, allelopathy in interactions.json; no full overgrowth model). Hermit shell fights are done.
5. **Algae follow-ups:** confirm `Simulation.rebuildWater()` carries algae state; fix the research 09 check 2 PO4 spec (assert PO4 reaches its floor and is reported as limiting, not a 0.3-0.4 ppm drop from 0.2).
6. **NO3/PO4 sandbox start values** (79 / 0.22 seen once): defaults are 5.0 / 0.04; root cause (logged-value unit mix, NO3 vs NO3-N) not proven. Check logged rows on device.
7. **High-speed time-lapse** behaviour runs at 0.5 s steps and can look coarse; check on a phone at max speed.
8. **Tracker gaps:** cycle tracker, acclimation timer and pest/algae/disease log are on branch `feat/tracker-gaps` (web Parameters page sub-tabs, `web/site/tracker.js`, core `tracker/`). Remaining: check background notifications on a real iPhone/Android (browsers pause backgrounded pages).
9. **Android-only leftovers (low priority, `:app` is not built):** `:app` not compiled in CI; pose LOD (`PoseLod.kt` exists, `poseDetail` hook) not wired in the Android renderer path; Android `LiveTankViewModel` lacks the three new algae presets (dinos, cyano, bubble algae) and still carries a `TODO(sim/realism)` skip-water-changes preset.
10. Housekeeping: 15 harmless `.NNN` mesh names in 8 GLBs.
