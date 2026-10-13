# Reef microfauna and microscope footage (MicroGroup)

Sim source: `core/src/main/kotlin/com/reeflive/core/sim/MicrofaunaModel.kt` (`MicroGroup`, `sizeMm` = current sim value).

**Scope note:** No footage was watched this pass. Commons has a few copepod, ostracod and Tigriopus clips (e.g. `Tigriopus brevicornis.webm`, `Ostrácodo nadando.webm`, `Copepod Nauplius.webm`) but the Commons/upload hosts returned HTTP 429 (retry-after 600 s) so none could be downloaded; queued for hand review. YouTube rows stay queued. This pass added [M] speed values from papers. Tags: **[M]** source-stated, **[L]** estimate; nothing is **[H]**.

## Sources
| # | Page | URL | What it supports |
|---|---|---|---|
| S1 | Spanish education catalogue, copepod microscopy clip | https://procomun.intef.es/en/media/57946 | Marine harpacticoid and freshwater cyclopoid microscopy, tagged "movement" (not reef, no description read) |
| S2 | Pod Your Reef, "Clash of the Pods" (retailer) | https://podyourreef.com/blogs/care/clash-of-the-pods-tisbe-vs-tigriopus-vs-apocyclops-which-pod-is-best-for-my-reef | Tigger pod = Tigriopus (harpacticoid); Tisbe benthic; Apocyclops mixed; jerky upward swim |
| S3 | Reef Builders, flashlight after lights-out | https://reefbuilders.com/2015/08/28/the-reef-aquarium-after-hours-four-reasons-to-keep-that-flashlight-handy | Pods and amphipods out on rock/glass after dark |
| S4 | AquaNerd, "Attack of the Amphipods" | https://aquanerd.com/2011/08/attack-of-the-amphipods.html | Amphipods nibbling clam mantle, filmed by author |
| S5 | Reef2Reef, Acropora burrowing amphipods | https://www.reef2reef.com/threads/acropora-burrowing-amphipods.1097432/post-13312484 | Nocturnal amphipods burrowing under coral tissue (single anecdote) |
| S6 | nano-reef, caprellid amphipods | https://www.nano-reef.com/forums/topic/262549-caprellid-amphipods/ | Caprellids on macroalgae frags (forum snippet) |
| S7 | Reefs.com, tiny shrimp after lights out | https://reefs.com/forum/threads/tiny-shrimp-after-lights-out.255634/latest | Swarms drawn to torch beam (anecdote, species unclear) |
| S8 | UVM EPSCoR, Lake Champlain Mysis | https://epscor.w3.uvm.edu/2/node/4084 | Wild Mysis benthic by day, pelagic at night (lake, not reef) |
| S9 | UWM Field Station, ostracods | https://uwm.edu/field-station/?p=1296 | Constant antennae movement; links live-ostracod video |
| S10 | Wikipedia, *Azygocypridina lowryi* | https://en.wikipedia.org/wiki/Azygocypridina_lowryi | Marine ostracod swims jerkily, antennae as oars |
| S11 | AquaNerd, red Planaria video | https://aquanerd.com/?p=4872 | Planaria spread fast, sit still, leave white spots; video shown at 3x |
| S12 | Reef Builders, bristleworm removal | https://reefbuilders.com/2007/06/09/bristle-worm-removal-from-saltwater-aquariums | Nocturnal; retreat when light hits |
| S13 | Reefchasers, bristle worms | https://reefchasers.com/blogs/hitchhikers-guide-to-the-reef/bristle-worms | Nocturnal detritivores hide in rock by day |
| S14 | Biogeosciences 2020, foraminifera actin/pseudopodia | https://bg.copernicus.org/articles/17/995/2020/ | Granules move both ways along pseudopod threads |
| S15 | Biogeosciences 2021 preprint, benthic forams in aquaria | https://bg.copernicus.org/preprints/bg-2021-62/bg-2021-62.pdf | Ectoplasmic "trees" used to climb in the water column (lab aquaria) |
| S16 | Microscope World, ciliates in pond water | https://blog.microscopeworld.com/2014/02/ciliates-swimming-in-pond-water.html?m=1 | Ciliates swimming under brightfield (pond, not reef) |
| S17 | AWI EPIC, cirolanid isopods, Uruguay beach | https://epic.awi.de/5934 | Night swimming, circadian rhythm (beach, not reef) |
| S18 | YouTube, "Copepods under microscope" | https://www.youtube.com/watch?v=r7ONwwx3UuQ | Copepod microscope clip (title only, not watched) |
| S19 | YouTube, "Amphipods and Copepods at night in reef tank" | https://www.youtube.com/watch?v=33BcJXhnLsA | Night pod/amphipod clip in a reef tank (title/blurb only) |
| S20 | PMC, cyclopoid "hop and sink" (2023 reply, high-speed Schlieren) | https://www.ncbi.nlm.nih.gov/pmc/articles/PMC10762428/ | Cyclopoid hop lasts ~17 ms (search snippet, not opened) |
| S21 | PMC, small-aquatic-animal trajectory method | https://www.ncbi.nlm.nih.gov/pmc/articles/PMC12388259/ | Eodiaptomus: cruise ~5 mm/s, jumps ~190 mm/s, mean 9.8 mm/s (snippet, not opened) |
| S22 | Kiørboe, escape jumps in planktonic copepods (search summary) | https://orbit.dtu.dk/en/publications/unsteady-motion-escape-jumps-in-planktonic-copepods-their-kinemat/ | Acartia tonsa (0.7–1.1 mm prosome) jump speed up to ~500 mm/s, power strokes 2–10 ms; escape velocity scales with length^0.65; cyclopoid escapes >500 BL/s |
| S23 | Americamysis bahia swimming (search summary) | https://archive.org/download/biostor-10198/biostor-10198.pdf | Shallow-water mysid reaches up to 12 BL/s with pleopods; bathypelagic mysid cruises 5–6.5 cm/s (search result group) |

---

## HARPACTICOID: Harpacticoid copepod (sim 0.8 mm)
Tigger pods are harpacticoids (see CALANOID). Tisbe is also harpacticoid, benthic.

| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Movement | Crawls on glass, rock, film algae; short upward darts, then stalls | Crawl at rest; dart 1–3 body lengths, pause 0.5–2 s | S2 [M], S1 [M] |
| Speed | Copepod escape jumps are 10^2 to 10^3 BL/s over 2–10 ms (calanoid/cyclopoid); Tigriopus not measured | Cruise ~1–2 BL/s [L]; escape jump ~100+ BL/s for 2–10 ms then stall, shown as 2–3 frame dart [M]/[L] | S22 [M] (proxy) / [L] |
| Where | Benthic: glass lower band, rock crevices, film | Stay within glass/rock band, rarely open water | S2 [M] |
| Day/night | Hide in rock by day; sources do not say they are nocturnal | Visible day and night on glass; more at night on glass if lights dim [L] | [L] |
| Light/predators | Fish (mandarins, pipefish) take them; no light response reported | Scatter/hide on light change, short dash to crevice [L] | S2 [M] |
| Tell | Red-orange adult (Tigriopus); darker, flatter (Tisbe) | Colour by species; flat body in crawl pose | S2 [M] |

## CALANOID: "Tigger pod" (sim 0.3 mm, planktonic)
**Sim label is wrong.** "Tigger pod" is *Tigriopus californicus*, a harpacticoid, not a calanoid. Tisbe is also harpacticoid. Parvocalanus is the pelagic calanoid. Sim `CALANOID` should be split or relabelled. The 0.3 mm size is likely too small for Tigriopus (typically ~1 mm adult [L]; S2 says "up to 3 mm" [M], which conflicts with [L]).

| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Movement | Tigriopus swims up in a stimulating jerky motion, then sinks; benthic in practice | Upward jerk, sink, repeat; not drifting plankton | S2 [M] |
| Speed | Calanoid analogue (Eodiaptomus): cruise ~5 mm/s, jumps ~190 mm/s, each jump very short | Cruise 5 mm/s; jump impulse ~190 mm/s (Acartia up to ~500 mm/s) lasting a few frames, then sink | S21, S22 [M]; Tigriopus values not measured [L] |
| Where | Water column for Tigriopus, Parvocalanus pelagic and "gets consumed in hours" [M] | Tigriopus: mid-tank near rock; Parvocalanus: open water, sparse | S2 [M] |
| Day/night | No source | Same day/night as harpacticoid [L] | [L] |
| Light/predators | Eaten fast by fish; hard to breed at reef temperature (disputed) [M] | Dense school flees toward fish, dies off fast [L] | S2 [M] |
| Tell | Bright red adults | Red-orange, larger than other pods | S2 [M] |

## CYCLOPOID: Cyclopoid copepod (sim 0.9 mm)
Apocyclops is cyclopoid: hides among rock like Tisbe, swims in water column like Tigger pods [M, S2].

| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Movement | Mixed: crawl in rock, swim-hop in water | Hop-and-pause, longer pauses than harpacticoids [L] | S2 [M] |
| Speed | Hop lasts ~17 ms, then sink (S20); cyclopoid escapes >500 BL/s (S22) | Hop impulse ~17 ms, then sink; sink-pause 0.5–1 s [L] | S20 [M] |
| Where | Rock crevices, sometimes open water | Rock band with occasional water-column drift | S2 [M] |
| Day/night | No source | Visible on rock in night light [L] | [L] |
| Light/predators | Not reported | Flee from torch beam [L] | [L] |
| Tell | Egg sacs (not verified) | Pale oval body; egg sacs optional | [L] |

## GAMMARID: Gammarid amphipod (sim 6.0 mm)
| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Movement | Sideways scuttle on rock/glass, short hops | Sideways scuttle, sudden 1-BL hops | S4 [M] (pattern implied) |
| Speed | Not measured | ~2–4 BL/s bursts [L] → 12–24 mm/s at 6 mm | [L] |
| Where | Rock and glass, burrows under coral encrustation | Rock/glass by day; burrowing under coral | S5 [M] |
| Day/night | Hide by day; swarm rock and glass at night when lit with torch | Emerge at lights-off, scatter at light | S3 [M], S5 [M], S19 [M] (title only) |
| Light/predators | Scatter at torch; nibble clam mantle, irritate coral (anecdote) | Scatter on light; graze coral irritation | S4 [M], S5 [M] |
| Tell | Shrimp-like, flattened, slightly bent | Laterally flattened, curved body | [L] |

## CAPRELLID: Skeleton shrimp (sim 10 mm)
| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Movement | Inchworm: alternate front and back legs; weak swimmer; praying-mantis stance when waiting | Inchworm walk on hydroids/algae; rare short swim | S6 [M] |
| Speed | Slow; not measured | ~0.5–1 BL/s walk [L] | [L] |
| Where | Macroalgae, hydroids, rock; stowaway on frags | Attached to macro/hydroid stalks, not open water | S6 [M] |
| Day/night | No source | Active night and day [L] | [L] |
| Light/predators | Eaten by fish (wrasses, dottybacks) [M] | Freeze in praying pose when threatened [L] | [M] |
| Tell | Thin, long limbs, stick-like | Stick-like body, thin grasping legs | [L] |

## MYSID: Mysid shrimp (sim 7 mm)
| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Movement | Hover in swarms in water column; swim backwards and forwards | Hover in tight swarm, flicker, drift | S7 [M], S8 [M] |
| Speed | Americamysis bahia bursts to ~12 BL/s with pleopods (S23); Neomysis vertical migration ~10 mm/s | Hover ~0.5 BL/s [L]; dart ~10 BL/s (~70 mm/s at 7 mm) | S23 [M] (proxy) / [L] |
| Where | Benthic by day (rock/sand), swarm up at night (wild Mysis model) | Rock/sand by day; rise into swarms at night | S8 [M] |
| Day/night | Swarm to torch beam, anecdote; reef-specific data thin | Night swarm at light; day mostly hidden [M]/[L] | S7 [M] |
| Light/predators | Flee bright light (wild Hemimysis) | Scatter at bright light, return after [M] | S8 [M] |
| Tell | Large eyes, pale | Big dark eyes, translucent body | [L] |

## OSTRACOD: Ostracod (sim 0.8 mm)
| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Movement | Bean-shaped; swims in jerks using antennae as oars; constant antennae motion | Constant antenna wiggle, jerky push-glide | S9 [M], S10 [M] |
| Speed | Not measured | ~1–3 BL/s [L] | [L] |
| Where | Glass, rock, sand; swimming small cloud | Glass and sand, occasional swims | S9 [M] |
| Day/night | No source | Constant, visible day and night [L] | [L] |
| Light/predators | Retract legs into shell when disturbed [M] | Retract to bean shape when startled | S9 [M] |
| Tell | Bean-shaped two-valve shell, antennae out | Two-valved bean silhouette | S9 [M] |

## ISOPOD: Isopod (sim 2.0 mm)
Beach cirolanids (S17) are nocturnal swimmers, not reef isopods. Reef isopods are likely similar [L].

| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Movement | Crawl on rock, sand; swim in bursts when active | Crawl; occasional swim burst | S17 [M] (wild) |
| Speed | Not measured | ~1–2 BL/s crawl [L] | [L] |
| Where | Sand, rock, burrows | Sand and rock base | S17 [M] (wild) |
| Day/night | Night swimming, circadian rhythm in beach species | Mostly night activity | S17 [M] |
| Light/predators | Not reported | Flee to burrow on light [L] | [L] |
| Tell | Flattened, segmented body | Segmented, flat body | [L] |

## ROTIFER: Rotifer (sim 0.2 mm, planktonic)
| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Movement | Ciliated corona; swim in darting paths | Fast jitter path with pauses; visible only at micro zoom | S16 [M] |
| Speed | Not measured | ~5–10 BL/s bursts [L] | [L] |
| Where | Water column, glass | Open water, glass | S16 [M] |
| Day/night | No source | Day and night [L] | [L] |
| Light/predators | Not reported | Drift off on light [L] | [L] |
| Tell | Wheel-like corona, forked foot | Ciliated wheel at head | [L] |

## CILIATE: Ciliate (sim 0.1 mm, planktonic)
| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Movement | Rapid darting, spiralling; cilia beat visible under microscope | Fast jitter and spin, tiny; cilia shimmer | S16 [M] |
| Speed | Not measured | ~10–20 BL/s [L] | [L] |
| Where | Water column, film | Open water and film | S16 [M] |
| Day/night | No source | Day and night [L] | [L] |
| Light/predators | Not reported | Phototaxis unknown | [L] |
| Tell | Ciliate rows, oval body | Oval, cilia rows | [L] |

## PHYTO: Phytoplankton (sim 5 µm, planktonic)
No source found for this group. Treat as unsourced.

| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Movement | Drifts with flow; some flagellates swim | Passive drift with current | [L] |
| Speed | Current speed | Flow-driven | [L] |
| Where | Water column | Water column, density drifts | [L] |
| Day/night | Photosynthetic, lighting-linked | Brighter during day, sink at night [L] | [L] |
| Light/predators | Phototaxis in some | Not visible at macro zoom | [L] |
| Tell | Green or brown cloud | Speckled haze, not sprites | [L] |

## BRISTLEWORM: Bristleworm (sim 50 mm)
| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Movement | Slow crawl in rock crevices; body emerges from holes | Extend from hole, slow crawl, retract | S13 [M] |
| Speed | Not measured | ~0.1–0.3 BL/s [L] | [L] |
| Where | Rock crevices, sand | Rock crevices; retreat to holes by day | S12 [M], S13 [M] |
| Day/night | Mostly nocturnal; rarely out in daylight unless starving | Emerge after lights-off; retreat into rock on light | S12 [M], S13 [M] |
| Light/predators | Retreat rapidly when light hits; colored light tolerated [M] | Flee into rock with bristle spread | S12 [M] |
| Tell | Bristles, fine setae, detritus feeder | Long bristled body, cloud of setae | [L] |

## MICROSNAIL: Micro-snail (sim 2.0 mm)
No source found for this group. Treat as unsourced.

| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Movement | Slow glide on glass/rock | Slow glide, foot visible | [L] |
| Speed | Not measured | ~0.5 BL/s [L] | [L] |
| Where | Glass, rock, film | Glass and rock, grazing film | [L] |
| Day/night | Not stated | Active day and night [L] | [L] |
| Light/predators | Not stated | Retract into shell on touch [L] | [L] |
| Tell | Small coiled shell | Coiled shell, antennae | [L] |

## SPAGHETTI_WORM: Spaghetti worm (sim 30 mm)
Hobbyist term is loose: multi-tentacled species may be tubeworms or bristleworms [M, S12].

| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Movement | Extends long thin body from crevice; undulates | Slow sway, retract on touch | [L] |
| Speed | Not measured | ~0.1 BL/s [L] | [L] |
| Where | Rock crevices, sand | Rock crevices | [L] |
| Day/night | Hide by day (likely) | Emerge after lights-off [L] | [L] |
| Light/predators | Retract on light | Retract into rock [L] | [L] |
| Tell | Long thin tentacles / bristle tuft | Thin thread-like body | [M, S12] |

## FORAM: Foraminiferan (sim 3.0 mm)
| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Movement | Thin pseudopod threads; granules stream both ways along them | Thin granular threads with bidirectional shimmer | S14 [M], S15 [M] |
| Speed | Granule streaming rapid; overall creep slow | Streaming visible at micro zoom; body creeps [L] | S14 [M] |
| Where | Glass, rock, substrate; pseudopods reach water column | Attached to rock/glass, pseudopods into water | S15 [M] |
| Day/night | Not stated | Constant [L] | [L] |
| Light/predators | Not stated | Retract pseudopods on contact [L] | [L] |
| Tell | Chambered shell, granular pseudopods | Chambered shell, granular threads | S14 [M] |

## FLATWORM: Red flatworm (sim 3 mm)
Red Planaria are often confused with flatworms; both glide on mucus or cilia [M, S11, not opened].

| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Movement | Glide on mucus film via ventral cilia | Smooth gliding, no legs | S11 [M] |
| Speed | Not measured; reef forum says gliding like snails | ~1–3 mm/s [L] | [L] |
| Where | Glass, rock, corals (grows over corals; white spots) | Glass and rock; settles on coral and leaves white spots | S11 [M] |
| Day/night | Not stated; described as photosynthetic [M] | Same day/night [L] | S11 [M] |
| Light/predators | Not stated | Slow retreat under rock [L] | [L] |
| Tell | Red body, leaf-shaped, tapered at both ends | Red, flat, tapering both ends | S11 [M] |

---

## Priority fixes for Opus
1. **Relabel or split CALANOID.** "Tigger pod" = *Tigriopus*, a harpacticoid (S2 [M]). Parvocalanus is the calanoid. Current sim size 0.3 mm conflicts with ~1 mm adult [L]. Move Tigger pods to the harpacticoid behaviour set.
2. **Copepod motion.** Harpacticoids and Tigger pods should crawl on glass and rock, then dart upward and stall (S2 [M]). Avoid smooth drift.
3. **Night emergence and light response.** Amphipods, mysids, bristleworms and spaghetti worms should come out after lights-off, then scatter or retract when light hits (S3, S7, S12 [M]).
4. **Microscale groups (ciliate, rotifer, phyto, foram) should read as fast darting or streaming, not as sprites at macro zoom.** Foram pseudopods show bidirectional granule streaming (S14 [M]).
5. **Caprellid and micro-snail behaviour.** Caprellids should inchworm on macro and hydroids, not swim (S6 [M]). Micro-snail, phyto and cyclopoid behaviour has no source yet. Mark those as [L] and check against footage before tuning.
