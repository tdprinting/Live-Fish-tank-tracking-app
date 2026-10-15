# Evidence ledger: simulation behaviour constants per species

Research snapshot: 2026-10-13. Companion to the [master livestock research](15_master_livestock_research.md) and the [fish](10_fish_behavior_detail.md), [invertebrate](11_invertebrate_behavior_detail.md) and [cleaner shrimp](12_cleaner_shrimp_detail.md) notes. Scope: the 26 entries in `core/src/main/resources/species/index.json`. This is a record of where each key constant comes from. It does not change any simulation value.

**Paths.** `ai/` = `core/src/main/kotlin/com/reeflive/core/ai/`; `sim/` = `core/src/main/kotlin/com/reeflive/core/sim/`; `species/` = `core/src/main/resources/species/`.

**Labels.**
- **SOURCED**: the value rests on a direct study of that species (the master doc's "Direct").
- **ANALOGY**: the value rests on a different species or genus, or was inherited from another species' brain.
- **DESIGN-ESTIMATE**: no usable source for the value. This includes species-account (FishBase, museum, breeder) evidence that supports only the qualitative behaviour, not the number. Code comments that cite a key (e.g. `S:Caves19`, `D:Grutter96`) that does not resolve to a link in `docs/research/` are also DESIGN-ESTIMATE, with the key noted.
- A "(no code constant)" entry records a sourced finding that the simulation does not yet implement.

**Scope limits.** Rows are the key constants only (about 3 to 5 per species). Coral and algae rows come from `sim/` because the coral and algae models live there, not in `ai/`. A row with a blank tank test ("none") means no realistic in-tank test exists; do not induce harm to test it.

## Fish

| species id | constant (file:symbol) | value | label | source link or "none" | tank observation that could test it |
|---|---|---|---|---|---|
| amphiprion_ocellaris | `ai/FishBrains.kt:ClownBrain.HoverHost` waypoint | 0.5 to 1.6 BL around host, new waypoint every 3 to 7 s | DESIGN-ESTIMATE | none (10 §1: no host radius established) | time within 1 to 3 BL of chosen host per fish per hour |
| amphiprion_ocellaris | `ai/Interactions.kt:CLOWN` to `CLOWN` drive | 0.3 | DESIGN-ESTIMATE | none for value; hierarchy from [Florida Museum account](https://www.floridamuseum.ufl.edu/discover-fish/species-profiles/clown-anemonefish/) (account) | chases and yields per pair per hour; who yields |
| amphiprion_ocellaris | `ai/FishBrains.kt:ClownBrain.DefendHost` drive (comment: female 0.6, male 0.3) | 0.6 F / 0.3 M | DESIGN-ESTIMATE | none; [Colleye & Parmentier 2012](https://pmc.ncbi.nlm.nih.gov/articles/PMC3492317/) is *A. frenatus* (ANALOGY for threat vs. yield, not this value) | chase vs. yield events by sex and size |
| amphiprion_ocellaris | `ai/Interactions.kt:CHASE_INTERVAL_S` (all pairs) | 900 s mean at drive 1 | DESIGN-ESTIMATE | none | intervals between chases for one pair |
| amphiprion_ocellaris | `species/amphiprion_ocellaris.json:starvationDays` | 21 | DESIGN-ESTIMATE | none (10 §1 and master doc: no starvation deadlines) | do not starve to test; log days between feeds against body condition only |
| paracanthurus_hepatus | `ai/FishBrains.kt:TangBrain.Patrol` score | 0.15 + 0.4 × activity | DESIGN-ESTIMATE | none; [FishBase account](https://www.fishbase.se/summary/Paracanthurus-hepatus.html) supports open-water feeding only | share of daylight time patrolling vs. resting in a refuge |
| paracanthurus_hepatus | `ai/FishBrains.kt:TangBrain.Patrol` surface flick | chance 0.4 per 2 s check, 3 to 8 s at surface | DESIGN-ESTIMATE | none (10 §2: no surfacing rate) | surface flicks per hour |
| paracanthurus_hepatus | `ai/Interactions.kt:TANG` to `TANG` drive | 0.6 | DESIGN-ESTIMATE | none (no tang-tang aggression data) | chases per hour, only if two tangs are present |
| paracanthurus_hepatus | cleaner-stress effect (no code constant) | not implemented | ANALOGY | [Soares et al. 2011](https://www.nature.com/articles/ncomms1547) (*Ctenochaetus striatus* cortisol after mechanical cleaners) | approach to cleaner station, pose, and jolt, recorded as separate events |
| sphaeramia_nematoptera | `ai/FishBrains.kt:CardinalBrain.BASE_RADIUS` | 12 cm daytime hover around shade pocket | DESIGN-ESTIMATE | none; [FishBase account](https://www.fishbase.se/summary/sphaeramia-nematoptera) gives daytime association qualitatively (10 §3 says no numerical shade preference) | distance from cover across the lighting ramp, not only at a clock label |
| sphaeramia_nematoptera | `ai/FishBrains.kt:CardinalBrain.FORAGE_LIGHT` | 0.3 light threshold for night foraging | DESIGN-ESTIMATE | none | light level at which the fish leaves cover |
| sphaeramia_nematoptera | `ai/FishBrains.kt:CardinalBrain.dayRadius` newcomer factor | 0.6 for 7 days, rising to 1.0 by day 21 | DESIGN-ESTIMATE | none (10 §3 evidence gap) | hover distance on day 1 vs. day 21 for the same fish |
| sphaeramia_nematoptera | `ai/Interactions.kt:CARDINAL` to `CARDINAL` drive | 0.1 | DESIGN-ESTIMATE | none | intraschool chases per hour |
| cirrhitichthys_falco | `ai/FishBrains.kt:HawkBrain.PerchHop` dwell | 120 to 600 s per perch | DESIGN-ESTIMATE | none; code cites 06 §5.6 "5 to 20 hops per hour", which 10 §5 says is not established | perch changes per hour; count perch changes separately from strikes |
| cirrhitichthys_falco | `ai/Brain.kt:Predation.threatRadius`, hawk to cardinal | 1.5 × L when prey L < 0.5 × hawk L, else 0 | DESIGN-ESTIMATE | none; 10 §5 says the half-length prey cutoff is not established | prey-to-hawk size ratio at each strike, with outcome |
| cirrhitichthys_falco | `ai/Brain.kt:Predation.threatRadius`, hawk to cleaner shrimp / hermit | 8 cm / 5 cm | DESIGN-ESTIMATE | none | shrimp approach vs. strike vs. contact, kept separate (10 §5) |
| cirrhitichthys_falco | `ai/Interactions.kt:HAWK` to `HAWK` 0.6 and `HAWK` to `DOTTYBACK` 0.25 | 0.6 / 0.25 | DESIGN-ESTIMATE | none; [FishBase 5445](https://www.fishbase.org/summary/5445) gives haremic organisation qualitatively | perch displacement events between two hawks, or hawk vs. dottyback |
| labroides_dimidiatus | `ai/FishBrains.kt:WrasseBrain.CLIENT_COOLDOWN_S` | 60 s per client | DESIGN-ESTIMATE | none in docs; code cites D:Grutter96, not linked in `docs/research/` | time between successive visits to the same client |
| labroides_dimidiatus | `ai/FishBrains.kt:WrasseBrain.Work` inspection length | lognormal, mean 6.7 s, sd 4 s, clamped 2 to 20 s | DESIGN-ESTIMATE | none in docs; code cites D:Grutter96 | inspection durations logged per client, from video or timed notes |
| labroides_dimidiatus | `ai/FishBrains.kt:WrasseBrain.OVERDUE_S` | 45 min since last accepted visit | DESIGN-ESTIMATE | none (10 §4: no 90-minute or quota guarantee) | interval between accepted visits for each client |
| labroides_dimidiatus | `ai/FishBrains.kt:WrasseBrain.Work` acceptance chance | 0.3 + 0.6 × trust | DESIGN-ESTIMATE | [PubMed 28827634](https://pubmed.ncbi.nlm.nih.gov/28827634/) (qualitative: service varies with social setting, no formula) | accepted vs. declined approaches, per approach |
| labroides_dimidiatus | cleaner exclusion effects (no code constant) | not implemented | SOURCED | [PubMed 28307499](https://pubmed.ncbi.nlm.nih.gov/28307499/) (no detectable short-term community effect); [PubMed 21733872](https://pubmed.ncbi.nlm.nih.gov/21733872/) (eight-year exclusion changed parasites and growth in one damselfish client) | do not test by removal; record client-specific outcomes over weeks |
| pholidichthys_leucotaenia | `ai/GobyBrain.kt:ADULT_CM` | 15 cm adult threshold | DESIGN-ESTIMATE | none (10 §9: 15 cm transition not validated) | body length at which the fish stops hovering by day |
| pholidichthys_leucotaenia | `ai/GobyBrain.kt:ADULT_NIGHT_PATROL_P` | 0.05 per night for adults | DESIGN-ESTIMATE | none (10 §9: 5% not validated) | nights outside the burrow per adult |
| pholidichthys_leucotaenia | `ai/GobyBrain.kt:Feed` radius | 18 cm | DESIGN-ESTIMATE | none (10 §9: 18 cm radius not validated) | food taken near the burrow mouth vs. drifting out of reach |
| pholidichthys_leucotaenia | `ai/GobyBrain.kt:GOBY_MOUTHFUL_CM3` | 0.6 cm³ per sand mouthful | DESIGN-ESTIMATE | none | volume of sand spat out per hour, from pile size |
| salarias_fasciatus | `ai/FishBrains.kt:LawnmowerBlennyBrain.RockGraze` state | 8 s per patch; patch radius 5 to 15 cm | DESIGN-ESTIMATE | none (10 §6: field percentages are not per-tick rates) | rasp bouts per patch and seconds per bout |
| salarias_fasciatus | `ai/Interactions.kt:LAWNMOWER_BLENNY` to `LAWNMOWER_BLENNY` drive | 0.6 | DESIGN-ESTIMATE | value none; [Heron Island field study](https://research.usc.edu.au/esploro/outputs/journalArticle/The-ecological-significance-of-the-combtoothed/99548308102621) is combtooth blennies generally, not confirmed *S. fasciatus* | count intruders first, then responses, with distance and identity |
| salarias_fasciatus | `ai/Interactions.kt:LAWNMOWER_BLENNY` to `PEEKER_BLENNY` drive | 0.25 | DESIGN-ESTIMATE | none (10 §7: no tailspot vs. lawnmower conflict rate) | intrusions and displacements between the two blennies |
| salarias_fasciatus | grazing film removal (no code constant) | 57% photosynthetic and 38.5% organic removal in experiment | SOURCED | [Christiansen, Kemp & Tibbetts 2010](https://www.researchgate.net/publication/226757000_Are_the_oral_teeth_of_combtooth_bennies_Salariini_Teleostei_merely_combs) (experimental film, not an aquarium rate) | visible film cover on a test patch over weeks, without sampling |
| ecsenius_stigmatura | `ai/FishBrains.kt:PeekerBlennyBrain.MOVE_AWAY_CM` | 50 cm displacement | DESIGN-ESTIMATE | none (10 §7: explicitly not established) | distance moved after a larger blenny is present |
| ecsenius_stigmatura | `ai/PairMatrix.kt` `timidUntil` after a hit (cardinal and peeker) | 600 s | DESIGN-ESTIMATE | none | time spent hidden after an approach |
| ecsenius_stigmatura | `ai/Interactions.kt:PEEKER_BLENNY` to `PEEKER_BLENNY` drive | 0.5 | DESIGN-ESTIMATE | none | encounters between two tailspots, if two are kept |
| ecsenius_stigmatura | `ai/Interactions.kt:PEEKER_BLENNY` to `LAWNMOWER_BLENNY` drive | 0.2 | DESIGN-ESTIMATE | none | hole use when a lawnmower is in view |
| pseudochromis_indigo | `ai/Interactions.kt:DOTTYBACK` to `DOTTYBACK` drive | 0.7 | DESIGN-ESTIMATE | none; [ORA breeder account](https://www.orafarm.com/product/electric-indigo-dottyback/) calls the line "relatively peaceful", which conflicts with 0.7 | encounters per hour between two dottybacks, cave entrance vs. open water |
| pseudochromis_indigo | `ai/FishBrains.kt:DottybackBrain.Territorial` drive | 0.5 + 0.4 × aggression, when a fish is within 18 cm of the cave | DESIGN-ESTIMATE | none (10 §8: no fixed attack count) | chases toward blennies and cardinals, with distance to cave |
| pseudochromis_indigo | `ai/DottyPatrol.kt:CavePatrol` stop pause / cave pause / stops | 2 to 8 s / 5 to 20 s / 4 stops, one swapped per 6 rounds | DESIGN-ESTIMATE | none (10 §8 asks to record the patrol route; no numbers) | pause length at a patrol waypoint; crevices visited per round |
| pseudochromis_indigo | `ai/Interactions.kt:DOTTYBACK` to `CLOWN` 0.2 and to `HAWK` 0.25 | 0.2 / 0.25 | DESIGN-ESTIMATE | none | challenges near the clownfish host |

## Mobile and tube-dwelling invertebrates

| species id | constant (file:symbol) | value | label | source link or "none" | tank observation that could test it |
|---|---|---|---|---|---|
| stenopus_hispidus | `ai/InvertBrains.kt:BandedShrimpBrain.KEEP_APART_CM` | 30 cm | DESIGN-ESTIMATE | none | distance kept from a conspecific, only if two are present |
| stenopus_hispidus | `ai/InvertBrains.kt:BandedShrimpBrain.Hang` shuffle | every 5 to 10 s | DESIGN-ESTIMATE | none | hang position changes per hour |
| stenopus_hispidus | `ai/Interactions.kt:BANDED_SHRIMP` to `BANDED_SHRIMP` drive | 0.8 | DESIGN-ESTIMATE | none (12: no fixed kill chance) | pair encounters; record outcome categories, not a kill rate |
| stenopus_hispidus | `ai/InvertBrains.kt:MoltHide` (molting state) | hidden 24 to 48 h (code comment) | DESIGN-ESTIMATE | none for hide time; captive intermolt 25 to 26 d is a different quantity (see next row) | hours hidden after a molt shell appears |
| stenopus_hispidus | breeding timings (no code constant) | egg incubation 22.8 d; adult intermolt about 25 to 26 d; 12 pairs at 26 °C, 35 ‰ | SOURCED | [Johnson 1977](https://www.sciencedirect.com/science/article/pii/0003347277900173); [2010 reproductive-cycle study](https://www.sciencedirect.com/science/article/pii/S0044848610003182) | egg-bearing duration after a female molt, if a pair forms |
| stenopus_hispidus | cleaning frequency (no code constant) | cleaning at 5.6% of fish visits, 0.1% of visiting time, four stations | SOURCED | [NTOU field thesis](https://ndltd.ncl.edu.tw/cgi-bin/gs32/gsweb.cgi/login?o=dnclcdr&s=id%3D%22110NTOU5270011%22.&searchmode=basic) (site-specific, not universal) | fraction of passing fish that stop at the station |
| lysmata_amboinensis | `ai/InvertBrains.kt:CleanerShrimpBrain.APPROACH_CM` | 10 cm station zone | DESIGN-ESTIMATE | none | fish entering the zone per hour |
| lysmata_amboinensis | `ai/InvertBrains.kt:CleanerShrimpBrain.approaches`, leg-rock chance | predator 0.51, other 0.12 (code cites S:Caves19 "51% vs 12%") | DESIGN-ESTIMATE | citation unverified: [Caves et al. 2019](https://pmc.ncbi.nlm.nih.gov/articles/PMC6769148/) gives only qualitative direction in doc 12 | leg-rock events per approach, split by client type |
| lysmata_amboinensis | `ai/InvertBrains.kt:CleanerShrimpBrain.approaches`, station offer chance | predator 0.28, other 0.51 (code cites 06 §5.3) | DESIGN-ESTIMATE | none in docs | fraction of approaching fish offered a cleaning, by client type |
| lysmata_amboinensis | `ai/InvertBrains.kt:CleanerShrimpBrain.Climb` cleaning duration | 8 to 20 s | DESIGN-ESTIMATE | none | timed cleaning events |
| lysmata_amboinensis | client-type cleaning rates (no code constant) | 241 interactions at two stations; predatory clients cleaned in a smaller fraction | SOURCED | [Caves, Chen & Johnsen 2019](https://pmc.ncbi.nlm.nih.gov/articles/PMC6769148/) | direct count of visits by client type |
| lysmata_amboinensis | parasite removal (no code constant) | reduced a monogenean in day and night trials | SOURCED | [Vaughan et al. 2018](https://www.nature.com/articles/s41598-018-32293-6) (controlled grouper trials; direction only) | none in-tank without parasite challenge |
| lysmata_amboinensis | `ai/Interactions.kt:BANDED_SHRIMP` to `CLEANER_SHRIMP` drive | 0.35 | DESIGN-ESTIMATE | none | contact between the two shrimp species near the station |
| clibanarius_tricolor | `ai/InvertBrains.kt:HermitBrain.CrawlWander` radius and weight | 25 cm; weight 0.35 | DESIGN-ESTIMATE | none (the class is shared with *Paguristes*) | distance from home shell over a day |
| clibanarius_tricolor | `ai/InvertBrains.kt:HermitBrain.Pick` duration | 20 to 60 s | DESIGN-ESTIMATE | none | pick bout length |
| clibanarius_tricolor | `ai/InvertBrains.kt:HermitBrain.Retreat` duration | 10 to 30 s in shell | DESIGN-ESTIMATE | none | retreat length after a startle |
| clibanarius_tricolor | shell contest pattern (no code constant) | smaller attackers rarely displace larger defenders; recent molts more vulnerable | SOURCED | [Hazlett, direct field/lab study](https://repository.naturalis.nl/pub/506197) | contests vs. inspections, recorded with shell fit and molt state |
| paguristes_cadenati | `ai/InvertBrains.kt:HermitBrain` shared constants (radius 25 cm, pick 20 to 60 s, retreat 10 to 30 s) | inherited from the *C. tricolor* hermit brain | ANALOGY | [Hazlett direct field/lab study](https://repository.naturalis.nl/pub/506197) (*C. tricolor*, not this species; master doc says do not reuse these rates) | separate the scarlet hermit's own shell and feeding events from the blue-leg values |
| paguristes_cadenati | nocturnal scavenging (no code constant) | greater nighttime activity; gastropod shell use | ANALOGY | [DORIS account](https://doris.ffessm.fr/Especes/Paguristes-cadenati-Bernard-l-ermite-rouge-de-recif-4381) is an account, so this is only support for a qualitative night-activity rule | night vs. day activity on sand and rock |
| percnon_gibbesi | `ai/InvertBrains.kt:SallyCrabBrain.GRAB_TRY_PER_HOUR` | 0.15 | DESIGN-ESTIMATE | none (11 §3: capture rate not validated) | live-prey events per night (expect none in a reef tank) |
| percnon_gibbesi | `ai/InvertBrains.kt:SallyCrabBrain.GRAB_SUCCESS` | 0.3 | DESIGN-ESTIMATE | none | success per grab attempt |
| percnon_gibbesi | `ai/InvertBrains.kt:SallyCrabBrain.GRAB_HUNGER` and `GRAB_MIN_CM` | 0.85 hunger; 5 cm minimum prey | DESIGN-ESTIMATE | none | only if prey is present; do not introduce live prey to test |
| percnon_gibbesi | `ai/InvertBrains.kt:SallyCrabBrain.Graze` duration | 15 to 45 s | DESIGN-ESTIMATE | none | grazing bout length on hair algae |
| percnon_gibbesi | herbivory (no code constant) | sampled crabs herbivorous (19 specimens) | SOURCED | [Puccio et al. 2006](https://www.researchgate.net/publication/225304425_Feeding_Habits_of_Percnon_gibbesi_H_Milne_Edwards_1853_in_the_Sicily_Strait) (Sicily; diet varies by place) | grazing on algae vs. ignoring it |
| ophiocoma_sp | `ai/InvertBrains.kt:BrittleStarBrain.CrawlWander` radius and weight | 40 cm; weight 0.55 × activity | DESIGN-ESTIMATE | none | roaming distance from home disc position |
| ophiocoma_sp | `ai/InvertBrains.kt:BrittleStarBrain.RaceToFood` radius | 50 cm | DESIGN-ESTIMATE | none | latency to reach food dropped at distance |
| ophiocoma_sp | coordinated arm roles (no code constant) | arm roles change with direction | ANALOGY | [Astley 2012](https://blogs.uakron.edu/astleylab/wp-content/uploads/sites/1471/2018/10/Astley-2012-Getting-around-when-youre-round.pdf) (*O. echinata*, identity unconfirmed here) | arm roles in short clips, with disc movement |
| ophiocoma_sp | deposit vs. suspension feeding (no code constant) | arm extension in current | ANALOGY | [Micronesica observations](https://micronesica.org/sites/default/files/habitat_and_feeding_observations_on_species_of_ophiocoma_micronesica_vol._19_no._1-2_dec._1983.pdf) (several *Ophiocoma* species) | arm extension timed against current direction |
| sabellastarte_sp | `ai/InvertBrains.kt:FeatherDusterBrain.Snap` retraction | 20 to 60 s | DESIGN-ESTIMATE | none (11 §8: no normal re-emergence delay) | retraction length after a shadow or disturbance |
| sabellastarte_sp | `sim/InvertEcology.kt:REGROW_DAYS_MIN` and `REGROW_DAYS_MAX` (crown regrowth) | 30 to 60 days | DESIGN-ESTIMATE | code cites research 04 §6 ([TFH-SI](04_more_species.md)), which gives no regrowth figure; 11 §8 says no regeneration clock is established | do not induce crown loss; record re-fan time only if a crown is lost naturally |
| sabellastarte_sp | larval development (no code constant) | developmental study of *S. spectabilis* larvae | ANALOGY | [Bybee et al.](https://scientiamarina.revistas.csic.es/index.php/scientiamarina/article/view/132) (larvae, not adult crown regrowth) | none until identity is confirmed |
| nassarius_sp | `ai/InvertBrains.kt:NassariusBrain.Erupt` scavenge radius | 40 cm | DESIGN-ESTIMATE | none | latency to reach bait vs. distance |
| nassarius_sp | `ai/InvertBrains.kt:NassariusBrain.CrawlWander` radius and weight | 20 cm; weight 0.15 × activity + 0.05 | DESIGN-ESTIMATE | none | roaming distance over sand |
| nassarius_sp | `ai/InvertBrains.kt:NassariusBrain.Buried` sink | 3 s to bury | DESIGN-ESTIMATE | none | time from contact to fully buried |
| nassarius_sp | feeding bout length (no code constant) | field bouts averaged about 6 min; 76% at most 8 min | SOURCED | [Lucena et al.](https://periodicos.ufc.br/arquivosdecienciadomar/article/view/134) (*N. vibex*; identity still unconfirmed here) | time from food contact to end of feeding bout |
| nassarius_sp | hunger and fear (no code constant) | fasting changes avoidance of damaged-conspecific cues | ANALOGY | [*N. festivus* 1995](https://academic.oup.com/mollus/article-abstract/61/1/55/998602) | do fasted snails approach bait faster? |
| lithopoma_tectum | `ai/InvertBrains.kt:AstreaBrain.Flipped` chance | 0.3 per hour on sand | DESIGN-ESTIMATE | none | falls from glass per hour |
| lithopoma_tectum | `ai/InvertBrains.kt:AstreaBrain.Flipped` duration | 2 to 6 h upside down | DESIGN-ESTIMATE | none (11 §6: no unconditional death timer after inversion) | righting time when a snail is found inverted |
| lithopoma_tectum | `ai/InvertBrains.kt:GlassGraze` wait | 60 to 240 s | DESIGN-ESTIMATE | none (shared with `asterina_sp`) | glass grazing bout length |
| lithopoma_tectum | grazing performance among snails (no code constant) | species differ in grazing, survival and growth | ANALOGY | [PMC6023205 (2018)](https://pmc.ncbi.nlm.nih.gov/articles/PMC6023205/) (other aquarium snails) | compare grazing tracks with another snail, if present |
| asterina_sp | `ai/InvertBrains.kt:GlassGraze` wait (shared) | 60 to 240 s | DESIGN-ESTIMATE | none | glass grazing bout length |
| asterina_sp | `ai/InvertBrains.kt:AsterinaBrain` scavenge radius | 6 cm | DESIGN-ESTIMATE | none | contact with food within the radius |
| asterina_sp | correlated random walk (no code constant) | less distance when food is present | ANALOGY | [Lohmann et al. 2016](https://digitalcommons.chapman.edu/sees_articles/270/) (*Aquilonastra anomala*; identity unconfirmed) | displacement with and without food in view |
| asterina_sp | fission (no code constant; no fission path found in code) | fission only in named taxa | ANALOGY | [Ebert 2021](https://doi.org/10.1086/716913) | none until identified |

## Corals and macroalga

| species id | constant (file:symbol) | value | label | source link or "none" | tank observation that could test it |
|---|---|---|---|---|---|
| capnella_sp | `sim/CoralGrowth.kt:CoralKind.KENYA` growth | 0.6% per day tree-size fraction, r0 5, maxSize 2.5 | DESIGN-ESTIMATE | none; code comment cites 03 "grows very quickly (no number)" | branch length change per month from photographs |
| capnella_sp | `sim/CoralGrowth.kt:KENYA_SHED_PER_DAY` and `KENYA_SHED_SIZE` | 1/20 per day; size 1.4 | DESIGN-ESTIMATE | none | branch drops per week |
| capnella_sp | `sim/CoralGrowth.kt:KENYA_RANGE_CM` and `KENYA_MAX_COLONIES` | 10 cm; up to 6 colonies | DESIGN-ESTIMATE | none (master doc entry 20: fragment settlement only when observed) | fragment settlement, if it happens naturally |
| capnella_sp | `sim/SimSpecies.kt:CoralKind.KENYA` alkalinity demand and biomass | 0.004 dKH per day; biomass 0.30 | DESIGN-ESTIMATE | none (code comment: soft corals take little carbonate) | alkalinity drift with and without the Kenya tree |
| briareum_sp | growth (`species/briareum_sp.json:growthCmPerYear`, read at `sim/Simulation.kt`) | 20 cm per year | DESIGN-ESTIMATE | none for value; [Briareum asbestinum growth](https://repository.si.edu/items/b15eddb1-1328-4990-ba3e-117fca0c90fe) is a related-species analogy for concept only | mat edge advance per month |
| briareum_sp | `sim/CoralGrowth.kt:CoralKind.GSP` mat edge | 0.10 cm per day; r0 6; rMax 30 | DESIGN-ESTIMATE | none; code cites 03 C2 ("several cm per month") | mat edge advance on a fixed rock face |
| briareum_sp | `sim/SimSpecies.kt:CoralKind.GSP` alkalinity demand | 0.006 dKH per day | DESIGN-ESTIMATE | none | alkalinity drift with GSP present |
| zoanthus_sp | `sim/CoralGrowth.kt:CoralKind.ZOA` mat edge | 0.04 cm per day; r0 5; rMax 20 (est.) | DESIGN-ESTIMATE | none (code comment: est.) | colony edge advance per month |
| zoanthus_sp | contact competition (no code constant) | zoanthid contact affected a stony coral's calcification | ANALOGY | [PMC9682307](https://pmc.ncbi.nlm.nih.gov/articles/PMC9682307/) (direct experiment, genus-level for this tank) | record contact and tissue outcome with any adjacent coral |
| zoanthus_sp | `sim/SimSpecies.kt:CoralKind.ZOA` alkalinity demand | 0.003 dKH per day | DESIGN-ESTIMATE | none | alkalinity drift with zoas present |
| clavularia_sp | `sim/CoralGrowth.kt:CoralKind.CLOVE` mat edge | 0.02 cm per day; r0 4; rMax 15 | DESIGN-ESTIMATE | none (code cites 03 C2: "disappointingly slow") | mat expansion per month |
| clavularia_sp | stolon circulation (no code constant) | linked polyps share internal flow | ANALOGY | [stoloniferan octocoral study](https://www.journals.uchicago.edu/doi/full/10.1086/BBLv219n2p112) (related taxon) | new polyps at advancing stolons |
| discosoma_sp | `sim/CoralGrowth.kt:CoralKind.MUSHROOM` mat edge | 0.015 cm per day; r0 4; rMax 12 (est.) | DESIGN-ESTIMATE | none | disc diameter over time |
| discosoma_sp | `sim/SimSpecies.kt:CoralKind.MUSHROOM` alkalinity demand | 0.001 dKH per day | DESIGN-ESTIMATE | none | alkalinity drift with mushrooms present |
| discosoma_sp | tissue optics (no code constant) | wavelength penetration in photosymbiotic tissue | ANALOGY | [PMC5524488](https://pmc.ncbi.nlm.nih.gov/articles/PMC5524488/) (*Discosoma* tissue; no PAR range) | disc opening vs. light position |
| tubipora_musica | `sim/CoralGrowth.kt:CoralKind.ORGAN_PIPE` growth | 0.001 per day; maxSize 1.6 (est.) | DESIGN-ESTIMATE | none (field survey gives no tank growth rate) | tube length added per month |
| tubipora_musica | `sim/SimSpecies.kt:CoralKind.ORGAN_PIPE` demand and biomass | 0.008 dKH per day; biomass 0.08 | DESIGN-ESTIMATE | none (comment: builds calcite skeleton) | alkalinity drift with organ pipe present |
| tubipora_musica | live vs. dead tube proportion (no code constant) | colonies surveyed live and dead | SOURCED | [Egyptian Red Sea field survey](https://esj-journals.onlinelibrary.wiley.com/doi/10.1007/s11284-005-0064-7) | proportion of live tubes over time |
| gracilaria_sp | `sim/SimSpecies.kt:CoralKind.MACRO` biomass | 0.15; demand 0.0 | DESIGN-ESTIMATE | none | biomass from frond weight |
| gracilaria_sp | `sim/AlgaeModel.kt:GRAZE_CAP_G_M2_D` | 3.0 g per m² per day (generic cap, all algae groups) | DESIGN-ESTIMATE | none (code comment: est.) | frond biomass removal per week |
| gracilaria_sp | flow and photosynthesis (no code constant) | water movement raised net photosynthesis in a tested red alga | ANALOGY | [PNAS 0912348107](https://doi.org/10.1073/pnas.0912348107) (*Gracilaria cornea*) | frond growth at high vs. low flow |
| gracilaria_sp | nutrient uptake (no code constant) | uptake depends on light and supply | ANALOGY | [ScienceDirect S0304377018301906](https://www.sciencedirect.com/science/article/abs/pii/S0304377018301906) (*Gracilaria* experiment, species not this tank) | nitrate and phosphate change with and without the macroalga |

## Perception, memory, mood and quirks (realism_gaps, SIMULATION §21)

Keys are from the realism gap analysis (`realism_gaps.md`, 2026-10-14, section 8). Constants carry `@Ev` tags in code.

| species id | constant (file:symbol) | value | label | source link or "none" | tank observation that could test it |
|---|---|---|---|---|---|
| all fish | `sim/Senses.kt:SensesSpec.darkVision` | 0.12 diurnal, 0.5 nocturnal or crepuscular | DESIGN-ESTIMATE | [MRL23](https://pmc.ncbi.nlm.nih.gov/articles/PMC10688437/) (title only: rod layers in nocturnal reef fish) | distance at which a fish reacts to food dropped after lights-out |
| all fish | `sim/Senses.kt:SensesSpec.lateralBL` | 1.5 BL | ANALOGY | [Pohlmann et al. 2004](https://cob.silverchair.com/jeb/article/207/17/2971/14844/) (catfish; capture 0.66 → 0.17 without lateral line) | night strikes at food within a body length |
| all fish | `ai/Perceive.kt:SCENT_D`, `PLUME_SPREAD`, `PLUME_MAX_CM` | 1.5 cm²/s, tan 0.25, 90 cm | DESIGN-ESTIMATE | direction from [Dixson & Hay 2012](https://www.sciencedaily.com/releases/2012/11/121108142740.htm) (gobies follow a chemical cue) | arrival order of fish up- and downstream of a night feed |
| all fish | `ai/Mind.kt:FeedClock.GAIN`, `EXTINCT`, `LEAD_MIN` | 0.15 per feed, ×0.8 per missed day, 60 min | ANALOGY | [Reebs 2002](https://gambusia.bsp.ncsu.edu/readings/Reebs%202002.pdf) (goldfish, golden shiner: up to 3 feeding times and places, anticipation 3 to 10 days after food withheld) | activity at the feeding spot in the hour before the usual feed; days it lasts when a feed is skipped |
| all fish | `ai/Mind.kt:Mind.KEEPER_GAIN`, `KEEPER_BEG` | 0.06 per meal, beg at 0.35 | ANALOGY | [Tomasek et al. 2025](https://www.mpg.de/24151953/wild-fish-can-recognize-individual-divers) (sea bream learned the diver who fed them over 12 days) | fish at the glass when the keeper's hand appears, before any food |
| all fish | `ai/Mind.kt:Mood.FEAR_TAU_S`, `Mind.DRIFT_CAP` | 29 min, ±0.15 | DESIGN-ESTIMATE | WIL94 (title only: shy-bold trait) | latency to feed after a disturbance, compared across weeks |
| all fish | `ai/Mind.kt:PlaceMemory.halfLife` | refuge 7 d, threat 6 h | DESIGN-ESTIMATE | none (R10 §2, §7 describe returning to a favoured shelter) | which crevice a startled fish uses over several startles |
| paracanthurus_hepatus | `ai/Mind.kt:Mind.TACTILE_CALM`, `TACTILE_STRESS` | fear ×0.5, social stress ×0.8 after a cleaner's dance | ANALOGY | [Soares et al. 2011](https://www.nature.com/articles/ncomms1547) (*Ctenochaetus* cortisol after mechanical contact) | time to resume feeding after a chase, with and without a cleaner visit |
| cirrhitichthys_falco | `ai/HawkNight.kt:SandRest.PITCH`, `ROCK_GAP_CM` | 17.5°, ≤ 10 cm from rock | SOURCED | owner footage `video/tee_tank_night.md` T89 [H]; FishBase "rests at coral-head bases" (10 §5) | night position and pitch over several nights |
| cirrhitichthys_falco | `ai/HawkNight.kt:HawkHop`, `SandRest.PAUSE_S` | ≤ 3 BL hops at 3 BL/s, 1.5 s pauses | DESIGN-ESTIMATE | `video/fish_falco_hawkfish.md` "hop 2-4 BL then stop" [L] | hops and pauses on the way down at dusk |
| ophiocoma_sp | `ai/BrittleLight.kt:LightWithdraw.RISE`, `HOLD_MIN_S`/`HOLD_MAX_S` | light step 0.08; freeze 3 to 8 s | ANALOGY | [Aizenberg et al. 2001](https://www.nature.com/articles/35090683) (*O. wendtii* calcite lenses; the tank's star is not this species) | arm retraction when the room light comes on or a hand passes over |
| ecsenius_stigmatura, salarias_fasciatus | `ai/HoleEntry.kt:TailFirstHole.TURN_RATE`, `BACK_BL_S` | 6 rad/s, 0.8 BL/s | DESIGN-ESTIMATE | none (owner direction; 10 §7 head-out inspection) | entry direction and speed on video |
| ecsenius_stigmatura | `GlassCurious` and `ComeToKeeper` on the tailspot | fear-gated | SOURCED (behaviour only) | `video/fish_tailspot_blenny.md` S2 [M] | approaches to the glass with the keeper near |
| percnon_gibbesi | `sim/Simulation.kt:unsourcedPredation` | off by default: crab snail rolls and grabs, moray kill roll | DESIGN-ESTIMATE | realism_gaps X7; 11 §3 (Puccio 2006, Pizarro-Borrull 2025 support grazing, not kills) | none (do not induce predation to test) |

## Findings to resolve before any value is tuned

1. **Two coral growth values disagree.** `species/*.json:growthCmPerYear` (for example Kenya 10 and GSP 20 cm per year) and `sim/CoralGrowth.kt` rates (Kenya 0.6% per day, GSP 0.10 cm per day) are different units and models. The sim path that reads the JSON field (`sim/Simulation.kt`) was not traced further here; check which one drives each colony.
2. **Feather duster crown regrowth.** Code uses 30 to 60 days. Research 11 §8 says no regeneration clock is established for this worm, and research 04 gives no number.
3. **Hawk prey cutoff.** `Predation.threatRadius` implements a half-length prey rule for hawkfish, which research 10 §5 says is not established.
4. **Scarlet hermit inherits blue-leg hermit values.** Both use `HermitBrain`. The master doc says not to reuse blue-leg rates.
5. **Dottyback aggression.** The 0.7 intraspecies drive conflicts with the breeder's "relatively peaceful" description. The breeder account is not a controlled trial, so neither value is confirmed.
6. **Unresolved citation keys.** Code comments cite `S:Caves19`, `D:Grutter96`, `S:Grutter99`, `06 §5.3`, and `[I]`. None of these resolve to a link in `docs/research/`, so their values are DESIGN-ESTIMATE here until a link is added.
7. **Hawk perch dwell.** The 5 to 20 hops per hour basis is not established by research 10 §5.
