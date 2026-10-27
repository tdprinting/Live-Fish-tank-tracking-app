# ReefLive coverage gaps: current status (2026-10-27, main past #210)

The original 56-row audit of research 01-05 and the tank spec is superseded; most rows were closed by #20-#210. Only items that were gaps then are listed. Full history is in git.

| Gap (old ID) | Status | Evidence |
|---|---|---|
| Real-time shadows (R-2) | Done | #52, #95, #174 |
| Disease visuals beyond ich (R-3) | In flight | feat/fish-health-visuals (velvet, fin rot, HLLE, Brooklynella); unverified in motion |
| Clownfish spawning (B-1), cardinal mouthbrooding (B-2) | Done | #118, #164 |
| Hermit shell fights (B-4) | Done | InvertEcology shellFights |
| Coral growth and spread (C-1) | Done | #25, #27, #155 |
| Coral-vs-coral warfare (C-2) | Partial | allelopathy and sweeper stings (#162); no full overgrowth model |
| Aiptasia and controllers (C-4) | Done | #151 |
| Dinoflagellates, bubble algae, cyano (C-5, C-6) | Done | #210 |
| Sandbox no-op buttons and extra controls (W-UI-1, W-UI-2) | Done on web | Sandbox.kt; PAR/turnover/skip water changes #209 |
| Sandbox sliders for copper, K, Sr, I, silicate, CO2 (W-UI-3) | Unverified | check Sandbox.kt before relying on it |
| Heat-death visuals | In flight | feat/fish-health-visuals |
| Feeding displacement by rank, tang blueDeepen (06) | Open | no code |
| Tracker: cycle tracker (T-1), acclimation timer (T-2), pest/algae log (T-4) | Done on web (feat/tracker-gaps) | `web/site/tracker.js`, core `tracker/CycleTracker.kt`, `AcclimationPlan.kt`; background notifications unverified on a phone |
| Tracker: PDF export, multi-tank UI, sync (T-8, T-9, T-10) | Open, low value | web is the shipping product |
| Merge of assets/tank-hq (R-1) | Unverified | check whether the branch was merged or superseded |
