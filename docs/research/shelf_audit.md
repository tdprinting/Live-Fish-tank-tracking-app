# Supply shelf audit: what each prop does in the sim (2026-10)

Owner request (Tee): every shelf product should work properly in the simulation with its own look and properties.
This traces each of the 28 props from `SupplyItem.use()` / `onCarryDrop` (ui/live/SupplyShelf.kt, ui/livetank/LiveTankScreen.kt)
through `LiveTankViewModel` into the core `Command` it sends, and records what was missing and what changed.
Tests: core `ShelfEffectsTest`, app `SupplyEffectsTest` (+ the existing `SupplyShelfTest`).

## Before (audit findings)

- **Foods** were already the best part: 13 `FoodKind`s with their own particle shape and motion (`FoodVisuals`), sink /
  float rates, life time and diet wiring (`SimSpecies.dietMatch / foodPreference`). Gaps: every food carried the same
  N:P and DOC per gram (only `nutrientFactor` differed), so krill, Reef-Roids and nori loaded phosphate alike; a prawn
  chunk ("for the snails and crabs") was eaten only by the Nassarius (the hermits and brittle star ignored it); the
  Hermie line was one of five generic tool lines ("Shake shake! X for everyone."), and every frozen cube plopped the
  same white bubbles with no thaw cloud.
- **Big vs small water change**: identical apart from the fraction, both with the same generic `FRESH_MIX`; the
  products on the props (Red Sea Coral Pro vs Instant Ocean) did not matter.
- **Doses**: chemistry was right (rate-limited `Command.Dose`, Ca × alk × Mg precipitation, coral alk-shock stress), but
  nothing told the user when a dose was a second one that day, too high, or precipitating: the line was always the same.
- **New sock / fresh carbon**: `WaterSim.maintain` only reset the age; the sock and carbon are **off** by default
  (`TOGGLE_DEFAULTS`), so both props did nothing at all in the default tank while the banner claimed an effect.
- **Trim algae**: worked on the display macro, but said "Snip snip!" even with nothing to trim.
- **Zap pest**: `AiptasiaModel.remove` with no polyps did nothing, yet played "Zap! One pest anemone gone."
- **Test kit**: showed the HUD strip (temp, salinity, NO3, PO4, light, flow) with the sim's exact numbers: not what
  the API Reef Master + Salifert kit on the shelf measures, and no test rounding or error.
- **Microscope**: opened the pod view with no line. **Settle in, glass cleaner, knocker, move rocks**: distinct and working.
- **Carried prop**: every prop already had its own carried picture (`FoodPicture` per food, its own glyph per tool).
- No prop had a "what it changes and by how much" line.

## After

| Prop | Before | After (sim effect · in-tank picture · Hermie) |
|---|---|---|
| Flakes | float 45 s, sink 0.6; generic shaker line | same physics; properties line from the sim's numbers; own line |
| Pellets | pinch by the return | unchanged effect; own line and properties |
| Floating pellets | float 5 min; same splash as flakes | lighter splash; "clownfish come up" line |
| Algae wafer | sinks 3.5 cm/s, lasts 3 h; splash | heavy plop; PO4 ×0.8, DOC ×0.8; grazer line |
| Mysis | frozen cube plop | plop + pale thaw cloud; reference chemistry (×1) |
| Brine | frozen cube plop | plop + orange thaw cloud; PO4 ×1.2, DOC ×1.3 |
| Krill | frozen cube plop | plop + red thaw cloud; PO4 ×1.5, DOC ×1.2; hawkfish line |
| Prawn | Nassarius only | hermits and brittle star scavenge it too (0.8); PO4 ×1.4, DOC ×1.5; pink cloud |
| Cyclops | frozen cube plop | plop + red cloud; PO4 ×1.2, DOC ×1.2 |
| Nori | clip | PO4 ×0.5, DOC ×0.6; tang and blennies line |
| Coral food | small splash | pink powder haze; PO4 ×1.6, DOC ×1.4 (Reef-Roids drive nutrients) |
| Phytoplankton | small splash | green haze; PO4 ×0.6, DOC ×1.5 |
| Live pods | small splash | hopping pods burst; reference chemistry |
| Big water change | 25 % generic mix | 25 % **Coral Pro** (alk 12, Ca 445, Mg 1370, 25 °C): NO3 −25 %, alk ≈ +0.9 dKH (a "noticeable" step for coral alk shock), 0.1 °C cooler; banner previews the numbers; cool shimmer at the return |
| Small water change | 10 % generic mix | 10 % **Instant Ocean** (alk 10.5, Ca 400, Mg 1280): NO3 −10 %, minerals barely move; gentler line |
| Alkalinity | +1 dKH, same line always | `DoseCheck`: > 1 dKH/day → swing warning (corals retract: alk shock), above range → too-high warning, Ca × alk over the Mg-set threshold → "white snow" + salt-grain burst |
| Calcium | +20 ppm | same checks (25 ppm/day, 470 ppm) |
| Magnesium | +50 ppm | same checks (100 ppm/day); never warns of crust (Mg holds it off) |
| New sock | did nothing (no sock fitted) | fits a sock if none (`filterSock`): captures detritus (~30 % in 12 h from 1 ppm), rots back after 2-5 days; brown puff + suction; sandbox switch follows |
| Fresh carbon | did nothing (no carbon fitted) | fits carbon if none: DOC 6 → < 1 in a day, clears yellow (clarity), strips allelochemicals and slowly **iodine** (new, τ ≈ 10 d); black dust puff then clarity |
| Trim algae | always "Snip snip!" | nothing to cut → says so, no command; else cuts macro by ⅓ / chaeto by ½ and states the nitrate exported (macro yield 14 ppm per unit); red macro bits drift |
| Zap pest | always "One gone!" | counts pest anemones in the snapshot: none → "Nothing to zap", no command; more → "N more to go" |
| Settle in | acclimation reset | unchanged (distinct); properties line |
| Glass cleaner | clean-glass mode | unchanged; properties line |
| Test kit | exact HUD numbers | **TestKit**: Salifert KH (0.2 dKH steps ±1), API calcium (20 ppm drops ±1), API NO3 / PO4 colour cards (nearest swatch, sometimes the next one); 0.04 PO4 reads 0 like a real hobby card; Hermie names the worst reading |
| Microscope | pod view, no line | own Hermie line |
| Knocker | tap glass | unchanged (distinct); properties line |
| Move rocks | territory reset (sandbox) | unchanged; properties line |

Every prop now has a `SupplyItem.properties` line (foods: `FoodKind.properties`, built from the sim's own sink /
float / eaters / waste numbers) shown under the blurb in the pick label.

## Constraints kept

- No new assets: all new pictures reuse the burst sprites (`TankBursts` HAZE / SALT / COOL / PODS / PLOP) with tints.
- Core changes are additive: new `FoodKind` getters, `WaterSim.addFeed` factors defaulting to 1, `maintain` fitting
  equipment, carbon iodine uptake (only while carbon runs), two salt mixes, `SupplyChemistry.kt`. The autofeed foods
  (flake, mysis, pods) and pellets keep factor 1, so the healthy-day runs (RealismAcceptanceTest c03) see no change.
  The new prawn eaters only matter when prawn is fed.
- Not done (sim thread): hermits / brittle star could get MEATY in their species sheets; the sock's own bacteria
  are modelled only as its rot release.
