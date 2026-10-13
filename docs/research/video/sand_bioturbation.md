# Reef-tank sand bed and bioturbation (ReefLive dynamic sand)

Tags: [M] = from a cited source; [L] = estimate / sim choice. No video watched. Quantitative peer-reviewed data on aquarium bioturbation is sparse; most species numbers are [L].

## Sources
| # | Page | URL |
|---|---|---|
| S1 | Palaios 34(3), bioresuspension by V. puellaris (abstract only) | https://complete.bioone.org/journals/palaios/volume-34/issue-3/palo.2018.040/BIORESUSPENSION-BEHAVIORS-OF-THE-GOBIID-VALENCIENNEA-PUELLARIS-AND-THE-BIOGENIC/10.2110/palo.2018.040.full |
| S2 | Diamond goby fish profile | https://fishprofiles.com/profiles/marine/Blennoid_Gobioid/Valenciennea_puellaris |
| S3 | Diamond watchman care guide | https://gensou.sg/?p=41054 |
| S4 | Sleeper gobies reduce meiofauna 31-46% in 4 d (V. sexguttata, V. strigata) | https://par.nsf.gov/biblio/10369702 |
| S5 | Sleeper goby sifting description | https://reefbuilders.com/2013/11/08/valenciennea-strigata-yellowheaded-sleeper-goby-sand-sifter |
| S6 | Ferguson and Church 2004, J. Sed. Res. 74(6):933-937 (via secondary summaries) | https://nbviewer.jupyter.org/github/zsylvester/notebooks/blob/master/grain_settling.ipynb |
| S7 | Nassarius retail/care descriptions | https://www.liveaquaria.com/products/nassarius-snail |
| S8 | Brittle star care | https://www.saltwateraquariumblog.com/brittle-starfish-care-ophiuroidea/ |
| S9 | Brittle star burrowing and O2 flux (Amphiura, lab) | https://epic.awi.de/id/eprint/8528/ |
| S10 | Substrate grade vendor/forum (aragonite 0.5-2 mm, crushed coral 3-5.5 mm) | https://www.nano-reef.com/forums/topic/310774-which-sandand-why/ |
| S11 | Engineer goby (Shedd) | https://www.sheddaquarium.org/about-shedd/press-releases/engineer-gobies-masters-of-spring-cleaning |
| S12 | Van Rijn threshold of sand/mud (Shields) | https://www.leovanrijn-sediment.com/papers/Threshsandmud2020.pdf |

## 1. Diamond sand-sifting goby (Valenciennea puellaris)
NOT in the tank today (Diamond/orange-spot watchman). Candidate new species; needs a new BrainKind, model and sand interaction.

| Aspect | Value | Tag |
|---|---|---|
| Sift cycle | swim to bare sand, nose down, scoop mouthful, hold/chew to strip fauna, eject sand out the gill openings as a cloud, repeat | [M] S5 (sleeper gobies), S1 |
| Rate | ~9 mouthfuls/min while feeding/digging, ~0.18 cm3 per mouthful (~1.6 cm3/min) | [M] S1 |
| Grain handling | moves fine and coarse sand; gravel too big for gills, carried piece by piece | [M] S1 |
| Selectivity | separates meiofauna from sand by size/density; 4 d of sifting cut meiofauna 31-46% | [M] S4 |
| Scoop depth | mouth gape ~0.5-1 BL/10 so ~1-2 cm pit depth, 2-3 cm wide, leaves shallow crater | [L] |
| Plume | ejected from gills: puff 2-4 cm high, fine grains (<0.25 mm) hang 3-8 s; coarse grains drop in <1 s into a mound beside the pit | [L] from Ferguson-Church below |
| Daily turnover | no measured value. Hobbyist claims range from "whole bed in a day" to "whole bed in 2-3 wk (200 L)" S2/S3. At 9/min x 0.18 cm3 x ~6 h feeding = ~580 cm3/day (~0.6 L) | [L] |
| Burrow digging | digs burrows under flat rocks, can undermine rockwork S3 [M]; entrance ~1.5x body width, mound of ejected sand at lip, rebuilt after collapse | [M]/[L] |
| Pair behaviour | forms monogamous pairs, share burrow, forage side by side, one often guards | [L] general sleeper-goby knowledge, unverified |
| Size/shape | 10-15 cm (adults to ~15), elongate torpedo, long tapering tail, body depth ~12-15% of length; hobbyist descriptions | [L] |
| Colour | white/silver-cream body, orange-gold spots on head and fore body, blue-tinged dorsal/caudal fin edges, dark eye with orange bars on cheek; light sand colours | [L] |
| Diet note | needs supplemental feeding, can't live on sand fauna alone | [M] S2 |

## 2. Other sand movers already in the tank
| Animal | Sand effect | Size / timing | Tag |
|---|---|---|---|
| Engineer goby (Pholidichthys) | digs tunnels under rock; carries mouthfuls out and spits them; mound at entrance; repeated spring-clean | carry ~1 BL, drop, return every 20-40 s (see fish_engineer_goby.md); each load ~0.5-2 cm3; mounds 2-5 cm | [M] S11 qualitative, numbers [L] |
| Nassarius snail | rests buried with only siphon exposed; food in water makes bed "boil" as snails emerge; mostly nocturnal; creates 1-2 cm bumps and trails | shell 1.5-2.5 cm; speed ~1-2 cm/s when foraging (fast for a snail); emerges within 20-60 s of food | [M] S7 behaviour; speeds [L] |
| Hermit crabs | scuttle, shell drag leaves wide shallow groove; pick at sand; occasional digging for shell swap | shell 1-3 cm; trail 1-2 cm wide, <2 mm deep; no plume | [L] |
| Brittle star | arms reach out of rock; moves sand/rubble while roaming, nocturnal; feeding arms sweep detritus up; burrowing species ventilate sediment | disk 1-3 cm, arms 5-15 cm; tiny puffs from arm tips; faint arm-tip tracks | [M] S8,S9 qualitative; geometry [L] |
| Cleaner/banded shrimp | walk over sand; occasional pick at surface; negligible displacement; banded shrimp may excavate small pit under rock | trails invisible; micro puffs on claw flick | [L] |
| Astrea snail | grazes rock/glass, rarely sand; if on sand leaves a faint 3-5 mm wide smeared trail, removes film | 1-2 cm shell, ~0.2 cm/s | [L] |
| Pistol shrimp | NOT in tank. If added: excavates burrow, ejects sand in repeated puffs, mound with a hole, often with watchman goby partner | puffs 2-3 cm, every 10-30 s | [L] |

## 3. Sand physics for rendering
**Grain sizes.** Reef aquarium aragonite sand spans fine "sugar" (~0.1-0.5 mm), medium/special grade (~0.5-2 mm) to crushed coral (~3-5.5 mm) [M partially S10: vendor ranges 0.5-1.5, 1-2, 3-5.5 mm]. Forum consensus: sugar-fine sand blows around in high flow; crushed coral stays put but traps detritus and suits sifters badly [M] S10. Suggest default D50 ~0.5 mm (sim: 0.4-0.8 mm).

**Settling velocity** (Ferguson and Church 2004: w = R g D^2 / (C1 nu + sqrt(0.75 C2 R g D^3)); C1=18, C2=1 smooth spheres; C1=20, C2=1.1 natural sand [M] S6). My calc for aragonite (rho 2.93, R=1.86), seawater nu=0.95e-6 m2/s, C1=20, C2=1.1 [L, computed]:

| D (mm) | w (cm/s) | fall through 10 cm |
|---|---|---|
| 0.1 | 0.8 | 12 s |
| 0.25 | 3.3 | 3 s |
| 0.5 | 7.3 | 1.4 s |
| 1 | 12.9 | 0.8 s |
| 2 | 19.9 | 0.5 s |

Quartz would be ~15% slower (R=1.65). Real plumes also contain silt/organic fines (<0.06 mm, w<0.3 cm/s) that hang for minutes: that is the visible haze. Gobies' plumes from reef sand are mostly the fine fraction. Plume behaviour: a gill puff is a turbulent jet that decelerates in ~0.5 s, then grains settle at w while advected by flow [L].

**Flow and scour.** Motion starts when bed shear stress exceeds the critical Shields value (~0.03-0.05 for fine sand, per standard Shields curve, see S12) [M standard theory, not retrieved for 0.2 mm]. Rough estimate [L]: 0.25 mm sand starts moving at near-bed speed of roughly 20-25 cm/s; 0.5 mm ~25-35 cm/s; 2 mm ~45+ cm/s. Powerhead jets exceed this within ~10-30 cm of the outlet, so sugar sand scours a pit with ripple fringe and a downstream mound; the rest of the bed stays still. Aquarium consequence: bare spots with fine sand [M] S10.

**Slope / angle of repose.** Dry sand ~30-35 deg; underwater slightly lower (~28-32 deg) for angular carbonate; wet cohesive piles with fines hold steeper briefly [L; search did not return carbonate-specific values]. Avalanche when slope exceeds ~32 deg: move material downslope until <= ~28 deg.

**Surface films.** Diatoms (brown) bloom on new sand in weeks 2-6, then green/cyano mats on lit, low-flow patches; sifting and grazing expose clean pale sand under torn film [L, common hobby knowledge].

## Sim model suggestions
Height-field grid, 1 cell = 5 mm (tank 1 m -> 200 cells) for fine detail near animals; 1 cm acceptable if cost matters. Store: height h (mm), grain class (fine/medium), film amount f (0-1).
| Event | Dig | Deposit | Plume | Settle |
|---|---|---|---|---|
| Diamond goby sift | 1.5 cm radius pit, -10 mm, per bite (~9/min) | +70% of volume as a mound 2 cm downstream; rest to plume | 8-12 puff particles/bite, size 1-2 px, 2-4 cm rise | fine 3-8 s, coarse 1 s; haze particles 30-90 s |
| Goby burrow dig | -15 mm over 20 cells under rock overhang, per 10 s while digging | mound at entrance +10 mm | 6 particles per load | as above |
| Engineer goby load | 1 cell pit -8 mm per load | drop 1 BL away, +8 mm | 4-6 particles | 2-6 s |
| Nassarius emerge | -5 mm cell, bulge +3 mm around | rebury on return | 3 puffs | 1-3 s |
| Hermit | groove 3 cells wide, -1 mm per step | none | none | n/a |
| Brittle star | arm-tip nibble -1 mm | none | 1-2 faint particles | 1-2 s |
| Powerhead scour | bed speed > threshold: erosion rate ~0.5 mm/s at 2x threshold | deposit downstream where speed < threshold | stream-advected particles, 20-60 total | w table above |

Relaxation: each tick, for each cell, if slope vs neighbour > tan(32 deg) transfer excess to bring to tan(28 deg). Film: f decays by 0.5 per dig cell, regrows 0.01/min in light. Cap plume particle count at ~300 live; pool and recycle. Camera: draw plume as soft billboard sprites with alpha 0.3-0.6 fading over life.

## Priority fixes for Opus
1. Implement the height-field sand with repose relaxation and per-event dig/deposit (table above); wire existing engineer goby carry-and-spit loop to it first.
2. Add pooled plume particles with grain-class-dependent settle times (fine 3-8 s, haze 30-90 s).
3. Add Nassarius buried/emerge state with siphon and feeding-triggered emergence; hermit trails; brittle star arm nibbles.
4. Add powerhead scour using a near-bed velocity field, with fine-sand bare spots.
5. Decide whether to add Valenciennea puellaris (new model: 12 cm, white, orange-spotted head, burrow under rock, paired); guard with a feature flag.
6. Surface film layer (diatom/cyano) that sifting cleans.
