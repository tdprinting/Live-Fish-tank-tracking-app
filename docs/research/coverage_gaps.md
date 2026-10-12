# ReefLive coverage audit: research 01-05 and tank design spec vs code

Scope: compendium sections 01 (L32-417), 02 (L418-1268), 03 (L1269-1586), 04 (L1587-2691), 05 (L2692-3056), tank design spec (L3906-4145). 06 and 07 are excluded (branch `sim/realism`).
Compared against `origin/main` (da20357) and `origin/assets/tank-hq` (2058a03, branched before PR 8/9, so it lacks main's newest changes). Read-only audit, nothing edited.
Size: S = under a day, M = 1-3 days, L = more than 3 days.
Line refs are compendium lines. "Code" paths are repo-relative; `core/` = `core/src/main/kotlin/com/reeflive/core`.

## Headline: what is already done (so it is not re-listed)

- Tracker (01): all 18 parameters with units, targets, rate-of-change and danger alerts, NH3 (Emerson), O2 solubility, dosing/water-change/salt/evaporation/convert calculators, consumption insight, NO3:PO4 and Mg:Ca ratio, task schedule (sock, skimmer cup, glass, carbon, GFO, pumps, lenses, ICP), livestock with future-size warnings, QT/treatment cases, feeding, supplies, spending, ICP import, PAR log, coral growth log, emergency guides, 8-channel AI Prime schedule with moon, CSV/backup export, widget, auto-update.
- Water sim (05): all 15 parameter-effect sections have a model (temp/DHW, salinity shock, pH/CO2, NH3/NO2/NO3/PO4, DO, alk/Ca/Mg, die-off cascade, copper, light/photoperiod/shock, flow loss, pump strength), rate-of-change acclimation lag, group tolerance profiles, nitrification bacteria, hair algae/cyano/diatoms/coralline.
- Species (02, 04): every listed species has a brain and a GLB (clowns x2, tang, cardinal, wrasse, hawkfish, both blennies, dottyback, banded + skunk shrimp, both hermits, brittle star, duster, Nassarius, Astrea, Asterina, organ pipe, engineer goby instead of eel by owner correction, Sally Lightfoot crab). Cocoon sleep, cleaning dance, station, tail flip, molt hide, flipped Astrea, buried Nassarius, duster food flinch, goby excavation all exist.
- Rendering (03 F/G): per-channel AI Prime lights, caustics, shafts, Snell/TIR water surface, glass Fresnel + room env, bloom, particles, fish iridescence, bleach/brown/fluorescence shaders, LODs, ich spots, eye cloud.
- Tank design: `tank.py`, `rockscape.py`, `corals.py`, `equipment.py` on main already carry every spec node (Glass x5, Seams, Backing, RimTop/Brace/Bottom, Sand, WaterVolume/Surface, Stand, R0-R9, ARCH, all 16 equipment nodes, all 45 spec anchors present in `anchors.json`), and `tools/check_tank_spec.py` + `validate_glb.py` exist.

## Highest visible impact (top of list, in order)

1. W-UI-1  Sandbox buttons that do nothing: photoperiod, algae seed, per-pump, spectrum (no-ops); flow slider only toggles on/off; add/remove fish, size, water change, dose, heater failure, predation toggle have ViewModel methods but no UI.
2. R-1  Merge `assets/tank-hq` (higher-detail rock, tank, Kenya, zoa, normals) onto main; branch is WIP and behind main.
3. C-1  Corals never grow or spread (GSP mat, Kenya, clove, hair algae overtaking).
4. R-2  No real-time shadows / contact AO / surface rim shadow.
5. R-3  Only ich has a fish visual; velvet, bacterial, HLLE, mucus have none.
6. B-1  No clown spawning/egg tending, no cardinal mouthbrooding, no sex change.
7. C-2  No coral-vs-coral chemical warfare / overgrowth (Kenya, zoa/paly, GSP).
8. P-1  Aiptasia/pest dynamics (spread, controllers) are static decor.
9. W-2  No dinoflagellates or bubble algae; no Ca/alk precipitation when Mg is low.
10. T-1  Tracker lacks cycle tracker, acclimation timer, pest/algae/disease log.

---

## 1. Rendering and assets (what the user sees)

| ID | Research ref | Specified | Status | Where | Size |
|---|---|---|---|---|---|
| R-1 | Design L3987-4030 (rock), L3926-3986 (shell), L4131 (verify); handoff L4156-4160 | High-detail rockscape (<=45k tris spec, HQ branch up to ~220k with LODs), tank shell, Kenya/zoa remake from photos, 4k normals, `rockscape_lod1`, `kenya_lod1` | partial: all on `origin/assets/tank-hq` only. Main has the spec geometry and older LOD-less rock. Branch is WIP (may not compile), Kenya LOD1 root name fixed in last commit, validators not rerun, branched before main's shaders/species JSON (diff shows them "deleted") so needs rebase | `app/src/main/assets/models/*.glb`, `textures/*_normal.png`, `tools/blender/{rockscape,corals,tank,textures}.py`, `tools/validate_glb.py`, `tools/check_tank_spec.py` | L |
| R-2 | 03 G L1559 (Shadows row); Design L4122-4130 (exposure, AO crevice `#2B2A1F`); 1.7 (rim shadow 2.8 cm at surface) | Cheap directional or baked shadow; crevice AO; surface darkened near walls | partial: baked `rock_rough_ao.png` only. No shadow map (git grep "shadow" finds nothing in shaders), no rim shadow, no fish/coral shadows on rock | `app/src/main/assets/shaders/{lighting.glsl,rock.frag,water.frag}`, `render/TankRenderer.kt` | M-L |
| R-3 | 01 §4.3 L316-328; 05 §4 NH3, §1 | Visual disease states: ich spots, velvet gold dust, Brooklynella mucus, HLLE pits, clamped fins, pale/mucus | partial: ich spots, clamped fins, gill rate, desat, eye cloud exist; `Disease` enum has VELVET/HLLE/BACTERIAL but `fish.frag` draws only `uIchSpots`, `uEyeCloud` | `shaders/fish.frag`, `render/FishRig.kt`, `core/sim/SimTypes.kt` (Symptom) | M |
| R-4 | 03 C3 L1396-1404 | Slime/mucus glossy strand overlay, ragged melting edges, hair-algae mesh on corals | partial: bleach, brown, tissue passed to shader; no mucus gloss, no melt geometry/alpha, algaeCover only tints | `shaders/{softcoral,polyp}.frag`, `core/sim/CoralSim.kt`, `render/TankRenderer.kt` | M |
| R-5 | 03 B L1340-1358 | GSP gentle expand/contract pulse, 10-30 s period | missing: only light/flow/disturb-driven extension | `core/sim/CoralSim.kt` (extension), `shaders/polyp.vert` | S |
| R-6 | Design 1.7 L3967-3970 | Meniscus fillet strip (optional), water volume absorption `#2E8A78` | partial: absorption via Beer-Lambert done; meniscus missing | `tools/blender/tank.py`, `shaders/water.frag` | S |
| R-7 | Design 1.6 L3957-3966 | Sand: coral rubble pebbles 3-8 mm denser near rock feet, front algae line | partial: algae film done (COLOR_0.r); textures.py has specks but no pebbles | `tools/blender/textures.py` | S |
| R-8 | Design §4 L4074-4121, §6 "every anchor within 2 cm of a surface" | Anchors snapped to surface | partial: `PERCH_CORALLINE_LOW` ends 6.6 cm (main) / 6.9 cm (hq) from spec position, `coral_gsp_lower_right` 5-6 cm; rest 1-3 cm (expected snap). Run `tools/check_tank_spec.py` to see which exceed 2 cm | `app/src/main/assets/sim/anchors.json` | S |
| R-9 | Design 3.2 L4060-4073 | Equipment extras (jar, ATO LED, skimmer, HOB) | done (all 16 nodes present); live-tank visibility of ATO/skimmer state not tied to sim | `tools/blender/equipment.py`, `render/Assets.kt` | S |
| R-10 | 03 F5 L1530-1545, F4 | Dawn/dusk spectrum shifts, moon | done (LightChannel, LightingState) | - | - |

## 2. Species behavior (sim/ai)

| ID | Research ref | Specified | Status | Where | Size |
|---|---|---|---|---|---|
| B-1 | 02 §1C L505-520, 1F L540-548 | Clown pair: spawn, tend/fan eggs, female dominates, protandrous sex change | done: `sim/Reproduction.kt` (spawn readiness, site clean, lay, 8-10 d night hatch, larvae vanish, untended loss, protandrous change over 28 d); `ReproBehaviors.kt` (clean site, lay, fan); `SimSnapshot.eggClutches` for the renderer (egg drawing still TODO in app) | `core/ai/FishBrains.kt` (ClownBrain), `core/sim/Simulation.kt`, new egg renderer | M |
| B-2 | 02 §3C L693 | Cardinal male mouthbrooding, eggs hatch ~1 week | done: `Reproduction.cardinalBreeding` (dusk spawn of a fed pair, male broods 6-8 d, no feeding, hunger capped, released at night, stressed male abandons); `mouthbrooding` flag on `OrganismStats`/`OrganismPose` (renderer throat TODO). Inert in the default tank (one cardinal) | `core/ai/FishBrains.kt` (CardinalBrain), `FishRig` mouth pose | S-M |
| B-3 | 02 §4C L773-774 | Wrasse harem sex change (largest becomes male) | done (minimal): `Reproduction.wrasseHarem`; with 2+ wrasses the largest becomes male over 14 d, next largest on loss; inert with the single default wrasse; sex shown in `OrganismStats.sex` | `FishBrains.kt` (WrasseBrain) | S |
| B-4 | 04 §5-6 (L1971-2123), §15 L2630-2643 item 1 | Hermits swap shells, spare shells, shell fights over Astrea/Nassarius | done (sim side): `InvertEcology` gives every hermit a shell size that it outgrows (~0.2-0.6 %/day), spare-shell inventory + sandbox `Command.AddSpareShells`, swaps (~10 min), shell fights (blue-leg often, scarlet rarely) and, with no spare, Astrea/Nassarius shell theft at moderate risk (predation off in Reality: NearMiss). `ShellSwapBehavior` shows the swap. Hermit shell GLB variants still missing; no UI button yet (Command only); `InvertEcologyTest` | `core/ai/InvertBrains.kt` (HermitBrain), `core/sim/SimTypes.kt`, hermit GLB shell variants | M |
| B-5 | 04 §11 L2429; §14 matrix L2607 | Asterina eats organ pipe coral (M risk); harlequin removal rule, population cap | done (harlequin removal rule still not modeled): at `Simulation.ASTERINA_CAP` stars or more, `InvertEcology.asterinaStep` strips organ pipe tissue (0.000015/star/min, ~17 days to death at 4 stars) and sets `Coral.disturb`; other corals untouched; `AsterinaOrganPipeTest` | `core/sim/Simulation.kt`, `core/sim/CoralSim.kt`, `species/interactions.json` | S |
| B-6 | 04 §15 item 4 L2639 | Duster crown regrows 1-2 months after retract/loss | done: `InvertEcology` crown state per duster; ~6 h net retracted (or poor health) sheds the crown, which regrows over 30-60 days only while healthy (stalls/shrinks when ill); `FeatherDusterBrain.Filter` caps the fan at the regrown size, abstract plankton feeding needs a crown; `DusterCrownTest` | `InvertBrains.kt` (FeatherDusterBrain) | S |
| B-7 | 04 §15 item 9 L2642 | Foods per species: raw prawn (Nassarius), plankton/phyto (duster), mysis (cleaner shrimp) | done: FoodKind PRAWN + PLANKTON (suspended, drifts like PODS), per-species `foodPreference` (Nassarius prawn x1.5, cleaner shrimp mysis x1.3, duster plankton x1.2); `InvertFoodTest`. Feed UI is a single Feed button (not data-driven from FoodKind), so no new buttons; renderer FOOD_COLORS extended | `core/sim/SimSpecies.kt` (FoodKind), `FoodSystem.kt`, brains | S |
| B-8 | 02 §10 items 1,7 L1205-1216; 02 each "1F Lifespan" | Lifespan, aging, chronic-stress lifespan penalty for tang/eel | done: `LifeCycle.kt`; per-organism age (sim time only), senescence, chronic/cramped-tank ageing penalty, "old age" death (tang growth stunt unchanged) | `core/sim/Organism.kt`, `Simulation.kt` | S-M |
| B-9 | 02 §5 L821-901 (snowflake moray) | Moray ethogram | partial/intentional: `EelBrain` kept, owner "eel" is an engineer goby; moray sheet exists only in `core/src/test/resources/sim-fixtures`, not in `core/src/main/resources/species` | `core/ai/FishBrains.kt`, `species/*.json` | S |
| B-10 | 04 §15 item 2 L2635; 02 §10 item 2 | One-blenny cap; second blenny chronically stressed | partial: second blenny gets +0.10 stress target (`HealthModel.BLENNY_EXCESS_STRESS`) in tanks under 400 L; core `compatibilityWarnings()` added (`Compatibility.kt`); sandbox UI not wired (app not compilable here) | `HealthModel.kt`, sandbox add UI | S |
| B-11 | 02 each "1D"; 05 §17 L2994-3015 | Pairwise tolerance/aggression | done (16-entry matrix + `interactions.json`); the 06 rewrite on `sim/realism` supersedes | `core/ai/Interactions.kt` | - |
| B-12 | 02 §2B L592 | Tang cramped, more turning | done (`TangBrain`) | - | - |
| B-13 | 02 §4E, §10 item 7 | Wrasse starvation 2-8 weeks, needs prepared food | done (`clientsAvailable`, stress) | `HealthModel.kt` | - |

## 3. Coral, algae and pests

| ID | Research ref | Specified | Status | Where | Size |
|---|---|---|---|---|---|
| C-1 | 03 C2 L1383-1395 | GSP spreads several cm/month and covers a 30 cm face in 3-4 months, Kenya grows fast and sheds branches, clove slow, GSP stops at 5 cm sand gap | done (sim) / partial (render): `CoralGrowth` + `SurfacePatch`: encrusting mats (GSP ~0.1 cm/day edge, a 30 cm face in ~3.5 months; zoa, clove slow, mushroom) spread along the rock surface and stop at a sand gap > 5 cm; Kenya grows ~0.6 %/day and drops branches that root on rock below (lost on sand); growth needs health, light, NO3/PO4 in range, no algae on the colony, Sr/Ca. `Coral.size`/`cover`/`radiusCm`, exposed as `CoralPose.size`/`cover` (defaults 1/0). Biomass/demand scale with size. Not wired in `TankRenderer` (polyp parts use an identity model matrix, so instance scale is not a one-line change); `corals.py` density variants not made. Tests: `CoralGrowthTest` | `core/sim/CoralSim.kt` (new `size`/`cover`), `CoralPose` in `SimTypes.kt`, `render/TankRenderer.kt` (instance scale/density), `corals.py` density variants | L |
| C-2 | 03 D L1405-1426 | Kenya allelopathy near neighbors, zoa/paly toxin, GSP overgrowth, 10 cm gap | done: `CoralGrowth.compete`: Kenya allelopathy within ~10 cm edge to edge (cut 60 % by fresh carbon), zoa/paly and aiptasia sting within 3 cm, GSP smothers what its mat reaches (geodesic along the rock); tank-wide allelochemicals (`WaterState.allelo`, removed by carbon, skimmer, water changes) stress other corals above 1. Neighbour stress and overgrowth cost tissue and extension. Colonies loaded together keep their starting pressure as an acclimated baseline; growth, frags and new placements add to it. Tests: `CoralGrowthTest` | `CoralSim.kt` (pairwise stress by distance and chemical level), `Simulation.kt` coral loop | M |
| C-3 | 03 D row "Blue tang", "Cleaner shrimp" L1418-1420 | Tang nips zoas/softies, grazes hair algae; clown sits in Kenya/GSP | done: grazing + clown host; `ai/TangNip.kt` `NipCorals` (daytime sessions of 2-5 bites on zoa/clove/Kenya/GSP every 30-90 min, `Coral.disturb` 0.3, no tissue loss, mushrooms spared) | `FishBrains.kt` (TangBrain), `CoralSim.kt` disturb | S |
| C-4 | 01 §4.4 L329-338; 03 C2 L1395; 03 D | Aiptasia: spreads by pedal laceration, peppermint shrimp / Berghia / fish control, spreads when disturbed | done (sim): `AiptasiaModel` (`PestModel.kt`): each polyp is a Coral; mature polyps bud lacerates by pedal laceration (~1 per 2 weeks when fed, est.), disturbance (shadow/contact, rock moves) and scraping or chemical removal leave fragments (S3); peppermint shrimp eat small polyps and mostly ignore established ones, Berghia eat any, breed while fed, starve when they are gone and are eaten by shrimp/wrasses. Commands `AddAiptasia`, `RemoveAiptasia(chemical)`, `AddPestControl(PEPPERMINT_SHRIMP / BERGHIA)`. No sandbox button yet (app untouched); extra polyps beyond the 3 aiptasia GLBs are not drawn. Tests: `AiptasiaTest` | `CoralSim.kt`, `Simulation.kt`, `RockData.kt` anchors, sandbox add-pest button | M |
| C-5 | 01 §4.5 L339-351 | Dinoflagellates: low NO3/PO4, unstable light; blackout treatment | missing (no `dino` anywhere) | `WaterState.kt`, `WaterSim.kt`, `rock.frag`/`softcoral.frag` visual, `EmergencyGuides` already text | M |
| C-6 | 01 §4.5 | Bubble algae (Valonia) | missing (mentioned only in crab sheet) | `WaterSim.kt`, new card/GLB, grazer rules | M |
| C-7 | 03 A1 red macro row; 01 §3.5 (chaeto harvest 1-2 months) | Red macroalgae growth, nutrient export, harvest, light bleaching | done: `WaterSim.macroalgae`: display red macro (`WaterState.macro`) and refugium chaeto (`chaeto`) grow logistically on light and the scarcer of NO3/PO4; uptake (N:P 30:1 molar) is the export; over-bright light (> 1.0) bleaches the macro; harvest/trim removes biomass (refugium auto every 45 d, display trimmed above 0.6). Macro colony size follows the biomass (C-1). Tests: `EquipmentExportTest` | `WaterSim.kt` (export), `CoralSim.kt` | S-M |
| C-8 | 03 C1 "Low/unstable Ca/Mg" row L1370 | Low Ca/Mg stress soft corals mildly | done: `CoralSim` adds Ca (opt ≥ 380, tol 330) and Mg (opt ≥ 1200, tol 1050) stress at weight 0.3 for soft corals and 0.8 for the calcifying organ pipe; low Ca also slows growth slightly. Tests: `CoralGrowthTest` | `CoralSim.kt` | S |

## 4. Water chemistry / treatments

| ID | Research ref | Specified | Status | Where | Size |
|---|---|---|---|---|---|
| W-1 | 01 §2.1 L145-151; 05 §10 L2896-2911 | Low Mg causes Ca/alk precipitation; Ca consumption ratio to alk | done: `WaterSim.carbonate` couples Ca/alk/Mg: coral uptake and abiotic precipitation remove Ca at 7.14 ppm per dKH; precipitation threshold set by Mg (stable at 1300, normal water precipitates below ~1200; alk > 12 / Ca > 480 risk); calcification slows at low Ca/alk; Mg co-precipitates. Tests: `ReefChemistryTest` | `core/sim/WaterSim.kt` | S-M |
| W-2 | 05 §13 L2935-2942; 01 §1.1 K/Sr/I rows | Iodine, potassium, strontium effects | done: K tolerance (01 §1.1 360-440, danger < 340 / > 460) for inverts (`HealthModel.POTASSIUM`) and corals; Sr taken up with the skeleton (Sr/Ca 0.02 by mass), low Sr (< 6) adds mild coral stress; K and Sr sandbox sliders (`SandboxParams`). Research gives no numeric K/Sr dose-response, so lethal bounds are est. Iodine unchanged (already drives molts). Tests: `ReefChemistryTest` | `HealthModel.kt`, `WaterSim.kt`, `LiveUiState.kt` SandboxSpecs | S |
| W-3 | 05 §12 L2920-2934; 04 §15 item 8 | Copper kill switch for inverts; dose copper in sandbox; QT treatment scenarios | done: the sandbox already has a `copper` row (`SandboxParams`, SetParam `copper`, 0-2 ppm) and the `COPPER_OVERDOSE` preset; invert/coral copper damage existed. Added the QT scenario as `Command.StartTreatment(COPPER)` (0.20 ppm held 14-28 d, then carbon strips it); in the display it kills every invert and coral (05 §12). No app change needed. Tests: `TreatmentTest` | `LiveUiState.kt` SandboxSpecs, `LiveGlue.kt` scenarios | S |
| W-4 | 01 §3.8 L271-276, §4.3; 05 §1 L2702 (ich life cycle) | Treatments: hyposalinity ramp 0.002/day, tank transfer, UV, copper course acting on infection | done: `Treatments.kt` + `Command.StartTreatment / StopTreatment / Infect`. Hyposalinity ramps SG down 0.002/day to 1.010, holds (default 5 weeks), ramps back; it clears ich, not velvet; fish SG tolerance follows the hypo band while it runs. Copper course (0.20 ppm, 2-day ramp) clears ich, velvet, Uronema, not Brooklynella. UV cuts fish-to-fish spread (theronts) but cures nothing. Tank transfer (4 × 72 h) clears ich on the named fish. Formalin (velvet, Brooklynella, Uronema) and praziquantel (flukes) added. New `Disease` values BROOKLYNELLA, URONEMA, FLUKES that spread only from an infected tankmate (they come in with new fish or `Infect`), faster in low-immunity fish and, for velvet, after a fast temperature swing (05 §1), research-based speed (Brooklynella/Uronema fast, flukes slow) and signs (new `Symptom.MUCUS`). No tomont pool / 72-day fallow; no fish visuals for the new diseases (R-3). Tests: `TreatmentTest` | `HealthModel.kt` ich section, `SimTypes.kt` Disease, `Command` | M |
| W-5 | 01 §3.9 L277-293 | New-tank cycling: NH3 then NO2 peak, 4-8 weeks | partial: AOB/NOB bacteria and nitrification exist; no "new tank" preset/scenario or cycle checkpoints in sandbox | `core/sim/Presets.kt`, `LiveGlue.kt` scenarios | S |
| W-6 | 01 §3.5 L253-258 | Skimmer, sock, carbon, GFO, refugium effects on nutrients | done: `WaterSim` equipment toggles via `SetParam` ids `skimmer`, `sock`, `carbon`, `refugium`, `uv` and `Command.Maintain(CHANGE_SOCK / REPLACE_CARBON / HARVEST_MACRO)`. New `WaterState.doc` (DOC from food and exudate; removed by bacteria, skimmer, carbon), sock captures detritus and rots after ~2-5 days if not changed (auto every 3 d), carbon exhausts over ~3 weeks and strips DOC, allelochemicals and some copper, skimmer off cuts aeration. GFO unchanged. Tests: `EquipmentExportTest` | `WaterSim.kt`, `SimTypes.kt` Command/equipment | M |
| W-7 | 05 §14 L2943-2961 | Ramp light 1-2 weeks, spectrum | done (acclLight 3-week lag); spectrum affects fluorescence only | - | - |
| W-8 | 05 §1-§16 dose-response rows | All dose-response/onset/recovery rows | done (`ParameterEffectsTest`, tuned constants) | `HealthModel.kt`, `CoralSim.kt` | - |

## 5. Live-tank UI (sandbox, follow, readouts)

| ID | Research ref | Specified | Status | Where | Size |
|---|---|---|---|---|---|
| W-UI-1 | 01 §6 L385-403 (light schedule); 05 §14-16 (photoperiod, flow, pumps); handoff L4163-4166 | Sandbox: photoperiod, algae seed, per-pump strength, spectrum, tap-glass button, flow strength | missing/no-op: `LiveTankScreen.kt` L87 `"photoperiod","algae" -> Unit`; `onSetPump`, `onSetSpectrum` not passed; `"flow"` calls `setPowerheads(v>0.05)` (on/off only); `SandboxState.pumps/spectrum` never populated from sim. Handoff says `sim/realism` will wire these; coordinate | `app/.../ui/livetank/LiveTankScreen.kt`, `LiveTankViewModel.kt`, `core/live/LiveGlue.kt`, `core/sim/Simulation.kt` (Command) | M |
| W-UI-2 | 05 §1-§12 (what-if tests) | Sandbox water change, dosing, heater failure/stuck-on, predation toggle, add/remove fish, resize | missing in UI: `LiveTankViewModel.dose/waterChange/setHeater/setPredation/setSize/addOrganism/removeOrganism` exist (L100-110) but no caller in `ui/live` | `ui/live/SandboxPanel.kt`, `ui/livetank/LiveTankScreen.kt` | M |
| W-UI-3 | 05 §13, §12 | Sliders for copper, K, Sr, I, silicate, CO2/aeration | missing | `LiveUiState.kt` SandboxSpecs | S |

## 6. Tracker app features (01 §5, L352-384)

| ID | Research ref | Specified | Status | Where | Size |
|---|---|---|---|---|---|
| T-1 | 01 §3.9, §5 L277, L372 | Cycle tracker with 4 checkpoints, "cycled after 3 days 0/0" | missing | new `core/extras/` logic + `ui/extras/CycleScreen.kt`, `ExtrasRepository` | M |
| T-2 | 01 §4.2 L311-315, §5 | Acclimation tracker: float 15-20 min, drip 1-2 drops/s 60-90 min | missing (QT/treatment only) | `core/extras/ReefExtras.kt`, `ui/extras/TreatmentsScreen.kt` | S-M |
| T-3 | 01 §4.3 L316-328 | Disease log: symptom, treatment, dose, start/stop, outcome | partial: `HealthLog` on livestock + `TreatmentCase` (kinds copper, prazi, hypo, transfer); no symptom-to-diagnosis helper or outcome field | `TreatmentsScreen.kt`, `LivestockScreens.kt` | S |
| T-4 | 01 §4.4-§4.5 L329-351 | Pest and algae log with cause/fix cards | missing (only emergency text + insights) | `ui/extras/` new screen, `core/care/EmergencyGuides.kt` | S-M |
| T-5 | 01 §3.2, §5 L213-217, L366 | Salt mix batch tracker (batch SG/Ca/Mg/alk, premix consistency) | partial: salt-mix calculator only; no stored batches | `ui/care/CalculatorScreens.kt`, `ExtrasRepository` | S-M |
| T-6 | 01 §3.4 L248-252, §5 L367 | Top-off/evaporation log with salinity drift | partial: evaporation calculator; no daily top-off log or ATO volume history | `CalculatorScreens.kt`, `ExtrasRepository` | S |
| T-7 | 01 §2.5 L179-185 | Percent O2 saturation, shown from logged DO | partial: `Oxygen.percentSaturation` in core, not surfaced in `ParameterDetail` | `ui/log/ParameterDetailRoute.kt` | S |
| T-8 | 01 §5 L379 | Export CSV/PDF | partial: CSV + JSON backup done; PDF missing | `data/export/BackupManager.kt`, `ui/more/ExportScreen.kt` | S-M |
| T-9 | 01 §5 L381-382 | Multiple tanks | partial: DB has `tankId` everywhere; UI is single tank (`More > Tank`) | `TankRepository.kt`, `ui/nav`, `ui/more/` | M |
| T-10 | 01 §5 L380 | Offline-first with sync | partial: offline done; no sync | new backend/Drive sync | L |
| T-11 | 01 §3.8 | Hyposalinity ramp schedule (0.002 SG/day down and back) in the treatment course | partial: kind and default days; no ramp table | `ReefExtras.kt`, `TreatmentsScreen.kt` | S |
| T-12 | 01 §4.1 L294-310 | Stocking/bioload advice beyond per-species adult size | partial: future-size warnings done; no tank-level stocking summary or cap warning (blenny/dottyback slots) | `ui/livestock/LivestockModels.kt` | S |
| T-13 | 01 §6 L398-403 | Correlate light changes with coral growth/bleaching | partial: PAR log, light schedule, growth log exist; no correlated chart | `ui/extras/CoralsScreen.kt`, `ParScreen.kt`, `TimeSeriesChart` overlay | S |
| T-14 | 01 §1, §3, §5 (rest) | Parameter log, trends with overlay, bands, alerts, reminders, calculators, equipment, photos, ICP, costs, feeding, timeline, widget | done | - | - |

---

## Counts (computed from the table rows)

| Area | missing | partial | done |
|---|---|---|---|
| Rendering and assets | 1 | 7 | 2 |
| Species behavior | 3 | 7 | 3 |
| Coral, algae, pests | 4 | 4 | 0 |
| Water chemistry / treatments | 1 | 5 | 2 |
| Live-tank UI (sandbox) | 3 | 0 | 0 |
| Tracker app | 3 | 10 | 1 |
| **Total (56 rows)** | **15** | **33** | **8** |

## Notes and caveats

- Many research values are tagged (est.) in the compendium; "done" means a model with those magnitudes exists, not that it was validated.
- `tank-hq` is behind main by PR 6-9 (it shows main's shaders, `04/05` research docs and species generator as deletions). Rebase before merge; also rerun `tools/validate_glb.py` and `tools/check_tank_spec.py` (handoff L4161).
- Not verified: actual rendered anchors vs 2 cm rule (run checker), rubble pebbles in sand texture (grep found specks only), whether `FoodKind` has additional kinds beyond those shown.
- Overlaps with `sim/realism` (excluded by instruction but touches these files): sandbox no-op wiring (W-UI-1), interaction matrix (B-11), stress model (HealthModel). Expect merge conflicts in `Simulation.kt`, `HealthModel.kt`, `LiveGlue.kt`.
