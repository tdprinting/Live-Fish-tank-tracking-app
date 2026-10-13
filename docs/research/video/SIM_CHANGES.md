# Sim change list from the 2026-10-13 research pass

Reviewed against `main` at 6d6bd06. Each item names the evidence and where it lands in code. Ordered by what a
viewer would notice first. Evidence tags as in [README](README.md); **[H]** items come from Tee's own tank footage.

## Footage status
YouTube refuses video playback to this server (bot check), and Wikimedia Commons rate-limited the server (HTTP 429)
after the first few searches, so no third-party footage was watched. The only [H] evidence is Tee's 4 night clips
([tee_tank_night.md](tee_tank_night.md)). Candidate Commons clips found for a later pass are named in each file's NOTE.
Tail-beat Hz, cruise and burst speeds for the fish are therefore still [L] estimates; no species-specific
measurements were found in the literature either.

## 1. Host and night poses (from Tee's footage) [H]
| # | Change | Where | Evidence |
|---|---|---|---|
| 1.1 | Clown pair hosts a cluster of green hairy mushrooms (Rhodactis) on the lower-left purple rock, not the Kenya tree. Add a `HOST_SHROOM` anchor there and point both clowns' `placement`/`hostAnchor` at it | `sim/RockData.kt:136` (anchors), `sim/Presets.kt:10-11`, `ai/FishBrains.kt:27` default | Tee, [coral_hairy_mushroom.md](coral_hairy_mushroom.md) |
| 1.2 | Clown night state: both hover 1-3 cm over the host about 1 BL apart, ~0.1 BL/s, no colour change | `ai/FishBrains.kt` clown night behaviour | [T84 0:00-0:03] |
| 1.3 | Dark-morph clown texture: orange face and pectorals, three white bars on black | clown model/texture | [T84 0:01] |
| 1.4 | Hawkfish night rest may be on open sand within 10 cm of rock, pitch +15-20 deg, eye/head tracking only; a torch does not trigger flight | hawkfish brain night state | [T89 0:01-0:08] |
| 1.5 | Hawkfish texture: two brown-red mesh saddles (nape, mid-back), orange-red spot field on rear flank, red eye ring, white cirri on dorsal spine tips | hawkfish model/texture | [T89 0:02-0:06] |
| 1.6 | Hairy mushroom discs stay open at night (slight 10-15 % deflation only) | `CoralKind.MUSHROOM` night state | [T84] |
| 1.7 | Hair algae tufts on upper ledges stream strongly in flow at night (flow is not reduced at night) | `sim/AlgaeModel.kt` HAIR render sway | [T84 0:04-0:11], [T86] |

## 2. Speeds and timings that are wrong today [M]
| # | Change | Current value | Evidence |
|---|---|---|---|
| 2.1 | Nassarius crawl toward food at 1-3 cm/min (10-30 min to cross 30 cm) | `nassarius_sp.json` cruiseBL 0.05 (~4.5 cm/min, about 2x fast), burstBL 1.0 (~90 cm/min, 30-90x fast if used for food approach) | [invert_nassarius.md](invert_nassarius.md) |
| 2.2 | Hermit crabs: congener measured at ~0.7 cm/min while foraging | `clibanarius_tricolor.json` cruiseBL 0.15 | [invert_hermit.md](invert_hermit.md) |
| 2.3 | Coral banded shrimp territory ~1-2 m in the wild; widen home range | `stenopus_hispidus.json` homeRadiusCm 20, territorialRadius 10 (activity NOCTURNAL already matches) | [invert_banded_shrimp.md](invert_banded_shrimp.md) |
| 2.4 | Copepod escape jumps are 2-10 ms snaps (>100 BL/s) then a stall; render as a 2-3 frame jump, not a smooth dart | `sim/MicrofaunaModel.kt` pod motion | [micro_fauna.md](micro_fauna.md) S22-S23 |
| 2.5 | Organ pipe retraction spreads as a wave from the touch point, not all at once | `CoralKind` organ pipe touch response | [coral_organ_pipe.md](coral_organ_pipe.md) |
| 2.6 | Cleaner shrimp rock side-to-side before cleaning, mainly toward predator clients (51 % vs 12 %), and clean mostly at night in captivity | `ai/InvertBrains.kt` cleaner shrimp | [invert_cleaner_shrimp.md](invert_cleaner_shrimp.md) |
| 2.7 | Aiptasia contract/expand on a 24 h cycle | aiptasia render | [coral_aiptasia.md](coral_aiptasia.md) |

## 3. Model gaps [M]
| # | Change | Where | Evidence |
|---|---|---|---|
| 3.1 | Dinoflagellates should bloom under ultra-low nutrients (NO3 < 1 ppm, PO4 < 0.02 ppm) and retreat at night | `sim/AlgaeModel.kt` DINOS growth | [algae_growth.md](algae_growth.md) |
| 3.2 | Cyano driven by low N:P and flow < 5 cm/s; bubbles by day, mat shrinks at night | AlgaeModel CYANO | algae_growth.md |
| 3.3 | New-tank succession: brown film, diatoms, green film/hair, cyano, turf, coralline | AlgaeModel scheduler | algae_growth.md |
| 3.4 | Optional new algae groups: BRYOPSIS (Mg-sensitive), TURF, MACRO | `AlgaeGroup` | algae_growth.md |
| 3.5 | "Tigger pod" label: the 0.3 mm CALANOID is *Parvocalanus*; Tigger pods (*Tigriopus*, ~1 mm) are harpacticoids | `MicroGroup.CALANOID` label | [microfauna_catalog.md](microfauna_catalog.md) |
| 3.6 | Optional new micro kinds: vermetid snails, spirorbids, hydroids, Convolutriloba, pycnogonids, micro brittle stars | `MicroGroup` | microfauna_catalog.md |
| 3.7 | Dynamic sand: height-field grid (~5 mm cells), repose ~30 deg, per-species dig/deposit, pooled plume particles (fine grains settle 3-8 s), powerhead scour. Wire the engineer goby's carry-and-spit first, then nassarius burial/emergence | new sand system | [sand_bioturbation.md](sand_bioturbation.md) |
| 3.8 | Diamond goby (*Valenciennea puellaris*) is not in the sim or the tank; ~9 mouthfuls/min of ~0.18 cm3 if added later | species catalog (optional) | sand_bioturbation.md |

## Still open
- Fish tail-beat Hz, cruise and burst speeds: only a family-level labrid range (routine 1-3 BL/s, Ucrit 3-5+ BL/s; Fulton et al. 2005) was found. The clown, tang and wrasse cruise values (1.2-1.6 BL/s) sit inside it, so leave them until footage can be watched.
- Retry the named Commons clips once the rate limit clears (lawnmower blenny, clownfish, blue tang, cleaner wrasse, Aiptasia acontia, copepods).
