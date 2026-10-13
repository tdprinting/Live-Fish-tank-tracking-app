# Reef-tank algae: growth drivers, rates, grazers, die-back (ReefLive)

Companion to `algae_visual.md` (looks/motion) and `core/.../sim/AlgaeModel.kt` (current `AlgaeGroup`: DIATOMS, FILM, HAIR, CYANO, DINOS, BUBBLE, CORALLINE). This note covers drivers and numbers.

**Evidence tags.** [M] = stated by a cited source I read as a search result this session. [L] = estimate or recalled hobby/literature consensus, not verified here. [H] = seen in footage: **none**. Wikimedia Commons API rate-limited every request after the first batch (HTTP "too many requests"), and the first batch returned no usable algae footage (only dinoflagellate microscopy, kelp, an aquarium tour). YouTube was not attempted (blocked). No media downloaded, nothing to delete. Peer-reviewed algae growth numbers for reef-tank conditions are scarce; most rates below are [L].

## Sources
- S1 Chaetomorpha study snippets (Menendez 2005, 0.13 /d at NO3 6->100 uM; UK green-tide lab study, fastest growth 10-20 C): https://researchportal.port.ac.uk/en/publications/preliminary-studies-on-the-growth-of-selected-green-tide-algae-in/ and https://www.degruyterbrill.com/document/doi/10.1515/BOT.2001.042/html
- S2 Gensou refugium guide (chaeto PAR 80-120 at algae, hobby): https://gensou.sg/reef-refugium-tuning-guide/
- S3 Bryopsis magnesium (hobby; Mg 1600-2000 ppm, +100 ppm/day, hold 2-4 wk; Holmes-Farley: keep ~1285, <=100 ppm/day; mixed results, regrowth after ~6 mo): https://www.buildyouraquarium.com/bryopsis-algae/ , https://www.utahreefs.com/FORUM/printer_friendly_posts.asp?TID=54505 , https://archive.reefcentral.com/forums/showthread.php?p=11194642
- S4 Dinoflagellate guides (ULN link; 5-10 ppm NO3 / 0.05-0.1 ppm PO4 as retreat targets; Amphidinium on sand, Ostreopsis/Prorocentrum in water column and respond to UV; poor flow/detritus): https://www.podyourreef.com/blogs/care/dinoflagellates-in-reef-tanks-identify-eliminate-ostreopsis-amphidinium-prorocentrum , https://gensou.sg/dinoflagellates-reef-tank-complete-guide/ , https://www.reef2reef.com/threads/help-with-dinoflagellates.1158873/post-14387959
- S5 Valonia (Reef Builders: persistent pest of mature tanks, emerald crab, sailfin tang, well-fed crabs ignore it; no growth rate found): https://reefbuilders.com/2023/05/08/how-to-deal-with-valonia-bubble-algae , https://reefs.com/pest/green-bubble-algae/
- S6 Project file: /mnt/project-files/reeftank/research/05_parameter_effects.md (NO3 10-30 ppm accelerates algae; PO4 0.1-0.3 bloom; N:P ~10-15:1; photoperiod >12 h raises algae; flow <5 cm/s favors cyano/hair; all "est.").
- S7 Existing sim-side sources A1-A17 in `algae_visual.md`.

## Cross-cutting drivers (reef ranges)
| Driver | Value | Tag |
|---|---|---|
| Nitrate | 0-2 ppm (ULN) starves hair/diatoms but favors dinos and cyano; 5-10 ppm typical balanced; >20-30 ppm hair/bubble/turf thrive | S6 [M est.], S4 [M hobby] |
| Phosphate | <0.02 ppm ULN; 0.03-0.1 balanced; >0.1 hair/cyano bloom; >0.3 strong blooms, coralline slowed | S6 [M est.] |
| N:P (molar Redfield 16:1) | Strongly off-Redfield (very low N vs P) favors cyano (N fixers); very low both favors dinos | S6 [M est.], [L] |
| Photoperiod / PAR | Keep 8-10 h; algae saturate ~100-250 PAR (macroalgae ~50-150), reef-coral tanks 100-300 PAR at rock top, so light is rarely limiting for turf/hair | [L] |
| Temperature | Most nuisance green/red algae grow best 24-28 C in tank; hair/cyano accelerate at >27 C | [L]; S1 [M] lab optimum for temperate Chaetomorpha is 10-20 C (not tropical) |
| Flow | <5 cm/s favors cyano, detritus; high flow thins boundary layer and speeds hair/turf nutrient uptake but tears off film/cyano | S6 [M est.], [L] |
| Silicate | Diatoms need Si; new tank or RO/DI exhausted, sand/rock leach; Si >0.5 ppm Si fuels, <0.1 starves | [L] |

## Per-algae notes
Growth rate is specific growth mu (per day); doubling time = ln2/mu.

**1. Diatoms (brown dust).** Brown/golden matte dust on glass, sand, rock; wipes off in flow; no night change. Drivers: silicate source (new sand/rock, tap water), any N/P; peaks weeks 2-8 of a new tank, gone by ~60 days [M, from A6 in algae_visual]. Rate: mu 0.5-1.5 /d in bloom, doubling 0.5-1.5 d [L]. Eaten by: trochus/astrea/turbo, nassarius (sand), lawnmower blenny, hermits, copepods. Die-back: Si exhaustion -> film turns pale, sloughs in a few days [L].

**2. Brown film.** Mixed diatom/bacterial biofilm; tan-to-brown slime, slightly sticky, trails in flow; appears first in a cycled tank (week 1-3) before diatoms. Driven by ammonia/organics and light. Treat as DIATOMS with a slimy sheen [L]. Same grazers; bacteria-driven, collapses when ammonia goes to zero [L].

**3. Green film / green spot algae (GSA).** Film: soft green coat on glass/rock, wipes off. GSA (Coleochaete-like): hard round 1-3 mm bright-green dots on glass, needs a scraper/razor; favors PO4 <0.02 ppm with moderate NO3 and strong light, and low-phosphate "ULN" tanks [L; widely hobby-reported]. Rate: film mu 0.2-0.4 /d; GSA slow, dots grow ~0.1-0.2 mm/d, new spots weekly [L]. Eaten by: astrea/trochus/turbo and nerites on glass (not GSA, too hard; scrape), lawnmower blenny (film), tailspot blenny. Die-back: nutrient starvation/photoperiod cut makes film pale in 1-2 wk; GSA does not die back, persists when dead [L].

**4. Green hair algae (Derbesia, Cladophora-type, Chaetomorpha-like; filamentous).** Bright green, soft filaments 1-10 cm, sway and streamline in flow; grows on rock top and in flow; night: no visible change. Drivers: NO3 >5-10 ppm, PO4 >0.05-0.1 ppm, PAR >100 [S6 M est.]. Rate: mu 0.15-0.35 /d (doubling 2-5 d), length 0.5-2 cm/day at high nutrients [L]; compare Chaetomorpha linum mu 0.13 /d (doubling ~5.3 d) at NO3 6-100 uM in a lagoon [S1 M]. Eaten by: tangs (blue/yellow/sailfin), lawnmower blenny, sally lightfoot/emerald crabs, turbo/astrea partly, hermits, urchins, Mexican turbo [L]. Die-back: starvation (PO4/NO3 cut) turns it pale and flaccid in 1-3 wk; blackout (3-5 d) browns it [L].

**5. Bryopsis.** Feather-like fern fronds 1-5 cm, dark emerald, frond tips flutter; pest grows from dispersed propagules and regrows from fragments when broken by cleaning. Driver: not nutrient-driven alone; hobby claim that Mg ~1285 ppm is normal and Bryopsis persists, with Mg 1600-2000 ppm (+<=100 ppm/day, hold 2-4 wk) killing it in some tanks but failing in others [S3 M, mixed evidence]. Rate: mu 0.2-0.5 /d, can double weekly [L]. Eaten by: few; lawnmower blenny, some sea hares/Lysmata, tangs poorly [L]. Die-back: high-Mg exposure makes it go white/translucent within 1-2 wk [S3 M hobby]; regrowth possible after months [S3 M].

**6. Cyanobacteria (red/slime).** Dark red/maroon to black-green sheet mats, slimy; lifts in gas bubbles in light (O2), smells earthy; trails and streams in flow; mats shrink at night [S7 M]. Drivers: PO4 >0.03 ppm with low NO3 (N:P well below 16), dead spots with flow <5 cm/s, detritus/DOC, ammonia uptake [S6 M est.]. Rate: mu 0.4-1.2 /d (doubling 14-40 h), covers a sandbed patch in 2-5 d [L]. Eaten by: almost nothing (sally lightfoot, some hermits/sea hares lightly) [L; sim has 0.3/0.1 small prefs]. Die-back: antibiotic/blackout (3 d) -> mat peels, releases nutrients and consumes O2; fades in 5-10 d when PO4 and dead spots fixed [L].

**7. Dinoflagellates (Ostreopsis, Prorocentrum, Amphidinium).** Brown to tan slimy strings and mats with bubbles; Amphidinium dusts sand; Ostreopsis cycles in place in the water column, Prorocentrum and Amphidinium swim straight [S4 M]. Retreat at night / reform in light (shake test) [S7 M]. Drivers: ultra-low nutrients (NO3 ~0, PO4 <0.02) is the hobby-reported trigger [S4 M hobby]; poor flow and old detritus [S4 M]; lit, warm water. Recovery targets 5-10 ppm NO3 and 0.05-0.1 ppm PO4 [S4 M hobby]. Rate: mu 0.3-0.8 /d for Ostreopsis, doubling 1-2 d [L]. Eaten by: nothing meaningful in the sim set; nassarius sift Amphidinium in sand weakly [L]. Die-back: UV on column species, feeding nutrients up, blackout partly; crash release toxins (Ostreopsis palytoxin risk to hobbyists) [L].

**8. Bubble algae (Valonia, Ventricaria).** Shiny green glass-bead vesicles 2-30 mm, single cells on rock crevices, immobile in flow; no day/night change. Grows in mature tanks, slowly spreading; rate: new vesicles in weeks, cluster expands ~1-3 mm/d [L; no rate found, S5]. Drivers: any nutrient level, "persistent pest of mature tanks" [S5 M]; breaking releases spores/gametes per forum, disputed [S5 M]. Eaten by: emerald crab, sailfin tang, some hermits, Mithrax [S5 M]; well-fed crabs ignore it [S5 M]. Die-back: none unless removed; dies after months of starvation [L].

**9. Turf algae.** Short (2-10 mm) brown/green-brown carpet on rock, turf stays upright, ripples in flow; nutrient-seeking; first colonizer of bare rock. Rate: ~1-3 mm/day extension, mu 0.1-0.3 /d [L]. Drivers: NO3 5-15, PO4 0.03-0.1, high flow. Eaten by: tangs, lawnmower blenny, tailspot, urchins [L]. Die-back: shrinks under grazing + low nutrients; coralline outcompetes it when PO4 <0.03 [L].

**10. Coralline (CCA).** Pink/purple/red crusts, smooth to bumpy, 0.5-5 mm thick; no motion. Grows 0.1-1 mm/d horizontally in a tank with Ca 400-450 ppm, alk 7-9 dKH, Mg 1250-1350, PO4 <0.03 ppm [L]; project file says low pH and PO4 0.1-0.3 slow it [S6 M est.]. Eaten by: parrotfish, chitons, urchins, some snails (little in our tank) [L]. Die-back: bleaches white with low light + PO4, high alk swings, or dead from low Ca/Alk [L].

**11. Macroalgae (Chaeto, Gracilaria/red ogo, Caulerpa).** Chaeto: bright green tangled spaghetti ball, tumbles in flow, grows in a refugium; ~PAR 80-120 [S2 M hobby]; mu 0.1-0.2 /d, doubling ~5 d [S1 M]. Gracilaria: red/brown-pink branching, tumbles; mu 0.1-0.2 /d [L]. Caulerpa: feathery green stolons runs 1-2 cm/day in good light, mu 0.1-0.3 /d [L]; "sexual crash" (sudden whiten & melting, O2 crash) at nutrient stress, a known sim-worthy event [L]. Uptake: removes NO3/PO4 in proportion to growth; Chaeto is not palatable to fish or inverts [A15 in algae_visual M snippet]. Gracilaria is eaten by tangs and some urchins. Die-back: starve to pale, sloughing to detritus in days when N or P run out.

**12. Succession after setup (new tank).** Typical hobby timeline [L]: week 0-2 bacterial bloom/cloudy, brown film; week 2-8 diatoms (silicate); weeks 4-12 green film and GSA, hair algae and Bryopsis on rock as NH3/NO2 turn to NO3; cyano on sandbed dead spots weeks 3-10; weeks 8-24 turf, coralline begins when PO4 <0.05 and Ca/Alk stable; mature tank (>6 mo): bubble algae, dinos if ULN. Diatom peak decays ~30-60 d [M, A6].

## Grazers in our tank (preference ranking per algae) [L]
Lawnmower blenny: diatoms > film > hair; ignores cyano/dinos/bubble. Tailspot (peeker) blenny: film/diatoms, little hair. Blue tang: hair >> film > bubble (small); ignores cyano. Astrea/Trochus/Turbo: glass and rock film and diatoms, partial hair. Nassarius: sand diatoms/detritus, cyano trace. Hermits: film, hair, a bit of bubble. Asterina: light film. Emerald/Sally crab: bubble, hair, cyano trace. All as already encoded in `GrazerType` (matches; check bubble pref for emerald crab).

## Sim mapping
| Algae | AlgaeGroup | Suggested growth params (mu_max /d, nutrient, notes) |
|---|---|---|
| Diatoms | DIATOMS | mu 1.0, Si-limited, K_Si 0.2 ppm, wipe off at flow >15 cm/s |
| Brown film | DIATOMS (variant "slime") | mu 0.8 first 3 wk, driven by NH3/DOC; cap at ~30 d |
| Green film | FILM | mu 0.3, light-limited, low N/P ok |
| GSA | FILM sub-type or NEW GSA | mu 0.1, hard, only scraper removes; favors PO4 <0.02 |
| Hair (Derbesia/Cladophora) | HAIR | mu 0.25, N/P Monod K_N 3 ppm K_P 0.05 ppm; length 0.5-2 cm/d |
| Bryopsis | NEW (BRYOPSIS) | mu 0.3, nutrient-weak; Mg>1600 ppm for 14 d kills; fragment regrowth |
| Cyano | CYANO | mu 0.9, low N:P, flow <5 cm/s, grazer pref ~0 |
| Dinos | DINOS | mu 0.5, favored when NO3<1 and PO4<0.02; retreat at night; UV |
| Bubble | BUBBLE | mu 0.05, nutrient-weak, immobile; grazers emerald crab/tang |
| Turf | NEW (TURF) or HAIR short | mu 0.2, K_N 5, K_P 0.05; short upright |
| Coralline | CORALLINE | mu 0.01, needs Ca/Alk/Mg and PO4<0.03; dies at PO4>0.3 |
| Chaeto/Gracilaria/Caulerpa | NEW (MACRO) | mu 0.15, refugium PAR 100, uptake ∝ growth, Caulerpa crash event |

## Priority fixes for Opus
1. Add NEW groups BRYOPSIS (with Mg trigger/threshold), TURF, MACRO (refugium; Caulerpa crash), and GSA subtype; hobby claim S3 about Mg is mixed, so make the kill probabilistic.
2. Make DINOS grow under ULN (NO3<1, PO4<0.02) rather than nutrient-rich; add nighttime retreat and flow/detritus bonus [S4].
3. Succession scheduler for new tanks: brown film (0-3 wk) -> diatoms (Si, to ~60 d) -> GSA/hair -> cyano on dead spots -> turf -> coralline; use `docs`' timeline in section 12.
4. CYANO low-N:P and flow<5 cm/s driver; day bubbling, mat shrink at night; blackout and dead-pool O2 drop on die-back.
5. Verify every [L] mu against real data before tuning; no footage ([H]) exists, so visual speeds in `algae_visual.md` still unverified.
