# Fish AI research: tailspot blenny, lawnmower blenny, indigo dottyback, engineer goby, diamond goby

Scope: movement, space use, social, foraging, time-of-day, startle/curiosity for the five ReefLive tank fish. Sand-sifting mechanics, burrow digging and blenny hole mechanics are covered by another team and only referenced here.
Tags: [SOURCED] = a cited paper, book or database number/statement. [ANALOGY: other species] = transferred from a related species. [OBSERVATION: aquarium hobby report] = care guide, retailer, forum or anecdote. "calc" marks a value I derived arithmetically from a sourced number. Where no source exists, the AI parameter is labelled as a design estimate, not evidence.

Key corrections to earlier notes are at the end. Read them before tuning.

---

## 1. Tailspot blenny, Ecsenius stigmatura (Dennis)

Facts
- [SOURCED] Max 6.0 cm TL. Depth 2-30 m, tropics 20°N to 15°S, Western Pacific (Philippines, Indonesia). Adults in coastal lagoon and sheltered reef habitats, "usually solitary or in small groups". Trophic level 2.0 (diet not itemised on FishBase). Demersal adhesive eggs, "distinct pairing", planktonic larvae. Species authority Fowler 1952; main genus reference Springer 1988, Smithsonian Contrib. Zool. 465. Source: https://www.fishbase.se/summary/Ecsenius-stigmatura.html
- [SOURCED] Identification: yellow iris, yellow line below eye, dark spot at caudal-fin base (same FishBase page).
- [OBSERVATION: aquarium hobby report] Darts in and out of crevices, perches to watch. Source: Top Shelf Aquatics, https://topshelfaquatics.com/pages/saltwater-fish/tailspot-blenny (already in the video notes).
- [OBSERVATION: aquarium hobby report] Curious, approaches the glass. Source: https://gensou.sg/?p=30499.
- [ANALOGY: other species] Midas blenny (E. midas), a congener, feeds in open water 2-3 m above the benthos on zooplankton (FishBase summary 7561, ecology entry, approximate, not a measured study). Only that species is known to do this. Tailspot shows no evidence of it. Do not give tailspots water-column feeding by default.
- [OBSERVATION: aquarium hobby report] A midas blenny hole-dwelling "front of tank" anecdote: a single individual guards the hole mouth and excludes others from the front. Source: ultimatereef forum thread (anecdote only).
- [ANALOGY: other species] Ecsenius bicolor threat display in a burrow: rising up and gaping the mouth. Source: 1960s German film catalogue abstract (summary only, frequency unknown). No frequency data exist for any Ecsenius.
- Gap: no peer-reviewed data on Ecsenius bite rate, hop distance, perch duration, home range, or flight distance were found in this pass.

AI parameters (tailspot)

| Parameter | Value / range | Unit | Evidence tag |
|---|---|---|---|
| Max body length | 6.0 | cm TL | [SOURCED] FishBase |
| Typical operating depth / tank depth band | 0-0.6 of tank height (rock zone) | fraction | [SOURCED] depth 2-30 m is wild; band is design estimate |
| Home-hole anchor radius (feeding excursion) | 0.5-1.0 | body lengths (BL) | design estimate; no source |
| Excursion duration off hole (feeding) | 5-30 | s | design estimate |
| Head-out peek interval | 5-20 | s | design estimate (video notes; not measured) |
| Retreat response on approach of a larger fish | enter hole within 1 s | s | design estimate |
| Plankton/water-column feeding | off (0) | probability | [ANALOGY: E. midas, opposite] |
| Social: chase rate vs conspecific blenny entering zone | unknown; default low (0.2) | prob. per encounter | design estimate; no data |
| Pairing | 2 fish of opposite sex may share a zone only in breeding context | flag | [SOURCED] "distinct pairing" |

---

## 2. Lawnmower blenny, Salarias fasciatus (Dallas)

Facts
- [SOURCED] Townsend K.A. & Tibbetts I.R. 2004, "The ecological significance of the combtoothed blenny in a coral reef ecosystem", J. Fish Biol. 65(1):77-90, DOI 10.1111/j.0022-1112.2004.00426.x. Heron Island, GBR. Site-level data:
  - Bites per day: 3,427 (S. fasciatus) vs 1,155 (Pomacentrus wardi, a damselfish). Source: abstract; https://research.usc.edu.au/esploro/outputs/journalArticle/The-ecological-significance-of-the-combtoothed/99548308102621
  - Epilithic algal community consumed per bite: 2.19 mg (S.f.) vs 0.14 mg (P.w.).
  - Organic carbon consumed per day: 487.31 vs 35.46 mg C m⁻² d⁻¹.
  - Biomass density 2.95 g m⁻² (S.f.) vs 2.37 g m⁻² (P.w.); mean wet mass 7.9 g vs 8.7 g.
  - Territorial chasing: about 90% of entering blennies chased out; about 20% of entering damselfish chased. Both species rarely chased non-grazers.
  - Size dependence: residents chased only Blenniidae of the same size or smaller.
  - Sample size, bout structure, and time-of-day split not in the abstract. Full text needed.
- [SOURCED] calc: 3,427 bites/day over an assumed 10-12 h active period = 4.8-5.7 bites/min averaged over the day. Bouts are not reported, so the within-bout rate is higher.
- [SOURCED] A Brazilian field study on a congener, Ophioblennius trinitatis (Fernando de Noronha; Scientia Marina, June 2014, DOI-indexed via DOAJ https://doaj.org/article/ab2ba349b7ba4187b7835580489f5bd8): bite rate higher in the afternoon and in smaller juveniles; larger individuals defended larger territories; territory size varied with local habitat quality; turf algae preferred. Note this is a different genus. Use as [ANALOGY: other species].
- [OBSERVATION: aquarium hobby report] Hops rock to rock while grazing (Aquariadise, https://aquariadise.com/lawnmower-blenny/; already in video notes). Hop length 2-5 BL is an unverified design estimate; no source for hop distance was found in this pass.
- [OBSERVATION: aquarium hobby report] Chases other blennies around the tank; one per tank is the common advice (Gensou, https://gensou.sg/lawnmower-blenny-care-guide/).
- Conflict: existing video notes give "bite 1-2/s" on rock surface. That value implies 43,000-86,000 bites per 12 h day, about 12-25 times the field total of 3,427/day. Treat 1-2/s as a within-bout burst rate at most, not a sustained rate.
- Gap: no source gives this species' time-of-day activity, shelter-retreat pattern, or flight initiation distance.

AI parameters (lawnmower blenny)

| Parameter | Value / range | Unit | Evidence tag |
|---|---|---|---|
| Daily bite total (on grazeable surface) | 3,427 (wild, ~BL 8 cm fish) | bites/day | [SOURCED] Townsend & Tibbetts 2004 |
| Mean bite rate over an active day | 4.8-5.7 | bites/min | [SOURCED] calc from above; active period assumed 10-12 h |
| Within-bout bite rate | 1-2 (upper) | bites/s | [OBSERVATION: aquarium hobby report] (existing note; conflicts with daily total) |
| Algae per bite | 2.19 | mg epilithic community / bite | [SOURCED] |
| Hop length during grazing | 2-5 | BL | design estimate; no source |
| Hop-to-hop interval while grazing | 1-3 | s | design estimate; no source |
| Territorial chase probability on entering blenny | 0.9 | per encounter | [SOURCED] Townsend & Tibbetts 2004 |
| Territorial chase probability on entering damselfish | 0.2 | per encounter | [SOURCED] Townsend & Tibbetts 2004 |
| Chase rule | chase only same-family, same-or-smaller fish | rule | [SOURCED] Townsend & Tibbetts 2004 |
| Chase distance / duration | 1-3 BL / 2-5 s | BL / s | design estimate; no source |
| Bite rate, afternoon vs morning | afternoon higher (1.2-1.5x) | ratio | [ANALOGY: Ophioblennius trinitatis] |
| Territory radius, larger fish | larger than smaller fish; scale with body length | BL | [ANALOGY: Ophioblennius trinitatis] |
| Perched idle | head up on pectorals/pelvics | state | [OBSERVATION: aquarium hobby report] |
| Night retreat | enter crevice/hole | state | design estimate; no source |

---

## 3. Indigo dottyback, Pseudochromis (Indy)

Identity note: the tank fish is a hybrid or a related Pseudochromis/Pictichromis sold as "indigo". Species-level data below come mostly from related species. Confirm the purchase label before using any species-specific value.

Facts
- [SOURCED] Pseudochromidae family-level pattern (FishBase/Wikipedia summaries): most dottybacks are "fiercely territorial", defend small home ranges, usually live solitarily or in pairs. Source: https://en.wikipedia.org/wiki/Pseudochrominae
- [OBSERVATION: aquarium hobby report] Home range "at most a couple of meters" (aquarist field note in a SeaScape-type guide, not peer-reviewed). Source: https://www.ryanphotographic.com/pseudochromidae.htm (general family page).
- [OBSERVATION: aquarium hobby report] Splendid pseudochromis "spends most of its time darting from one refuge to another in search of food". Source: SeaScape fish guide, https://seascapestudio.net/reference/fish.php?id=253 (family-level, not Indy).
- [SOURCED] Pseudochromis steenei (congener): "highly territorial and aggressive"; bites camera housings; kills aquarium tankmates. Coastal patch reefs on soft bottom slopes. Source: https://fishbase.se/summary/14478
- [SOURCED] Pictichromis porphyreus (magenta dottyback), field, Okinawa patch reefs: bidirectional sex change confirmed in the field; female-biased sex ratio; males larger; when the opposite sex was removed from an area, about half of tagged fish relocated. Springer link: https://link.springer.com/doi/10.1007/s10641-014-0265-4. Relevance: home-range shift after social removal, [ANALOGY: other species].
- [OBSERVATION: aquarium hobby report] Male courtship: swims near female, then lures her into a breeding cave he has prepared (FishBase aquarium note; the species is Pictichromis-type). Source: https://fishbase.org/summary/24439
- [OBSERVATION: aquarium hobby report] Royal dottyback (P. paccagnellae): "very territorial"; needs rock piles with caves; dottybacks can be aggressive toward same-family entrants. Not peer-reviewed.
- Gap: no field study of P. fridmani, its hybrid, or any Pseudochromis patrol frequency, dart distance or bout duration was found. Patrol bout numbers in the AI are design estimates.

AI parameters (indigo dottyback)

| Parameter | Value / range | Unit | Evidence tag |
|---|---|---|---|
| Home range (around cave) | radius 0.5-1.0 (hobby upper figure about 2 m across) | m | [OBSERVATION: aquarium hobby report] ("couple of meters") |
| Cave-to-cave dart distance | 3-10 | BL | design estimate; no source |
| Dart duration (cave to cave) | 0.3-1.0 | s | design estimate; no source |
| Patrol bout length (open-water sweep) | 5-20 | s | design estimate; no source |
| Patrol frequency away from cave | 1 bout every 20-60 | s | design estimate; no source |
| Dwell at cave mouth between bouts | 10-30 | s | design estimate; no source |
| Chase radius toward smaller fish | 1-2 | BL from cave mouth | design estimate; family pattern [OBSERVATION] |
| Chase duration before return | 2-6 | s | design estimate; no source |
| Dominance-based colour change | brighter when dominant | flag | [OBSERVATION: aquarium hobby report] (Pictichromis) |
| Sex change on social removal | not modelled | flag | [SOURCED] Pictichromis porphyreus, field (sex change only; relocation about half of tagged fish) |
| Male courtship lure | male displays near female, then leads her into cave | flag | [OBSERVATION: aquarium hobby report] |

---

## 4. Engineer goby / convict blenny, Pholidichthys leucotaenia (Ricky Bobby Sanchez)

Taxonomy: Pholidichthyidae, monotypic family (Springer & Freihofer 1976, Smithsonian Contrib. Zool. 216). Not a goby or a blenny. Source for family: https://en.wikipedia.org/wiki/Pholidichthys_leucotaenia

Facts
- [SOURCED] Juveniles: black with a single white longitudinal stripe. Juveniles mimic venomous striped catfish Plotosus lineatus (secondary-source claim, aquarium/forum sources; not tested in a peer-reviewed study here). Source: Wikipedia (above), Reefbuilders https://reefbuilders.com/2022/12/23/convict-blennies-are-the-closest-thing-to-cichlids-in-the-sea
- [SOURCED] Day/night (secondary: Wikipedia citing Kaufmann 2005 National Geographic, "Clues From a Convict"): juveniles swim out from burrows by day to feed on plankton, travelling up to ~50 m from the home burrow; all return to burrows at end of day. At night they hang from tunnel roofs by mucous threads. Adults rarely or never leave the tunnels.
- [SOURCED] Burrow: maze of tunnels and chambers totalling about 6 m (20 ft). Parents move up to 3 kg sand per day (secondary, unverified primary).
- [SOURCED] Adult length: Wikipedia cites "almost 34 cm"; Reefbuilders gives up to 60 cm; Aquarium of the Pacific (https://aquarium.org/animals/engineer-goby/) gives max 10 cm+ (lower bound). Use 30-34 cm adult, with conflict flagged.
- [SOURCED] Metamorphosis to adult body at ~15 cm (Reefbuilders, secondary). Aquarium of the Pacific: juveniles swarm in shallow reefs; larger juveniles move to rocky areas. Depth 3-30 m.
- [SOURCED] Diet: plankton and small crustaceans (Aquarium of the Pacific; Wikipedia).
- [CONFLICT] Parental feeding: Reefbuilders attributes juvenile-to-adult food-spitting to Klaus Stiefel's observations (no year, no paper). Wikipedia says adults take mouthfuls of juveniles and spit them out and adult stomachs held "only a green slime". Do not implement juvenile-to-adult provisioning as a mechanic. Same caution as research 10.
- [OBSERVATION: aquarium hobby report] Adult body described as moray-like (Reefbuilders). Analogy only; the existing note already warns against using moray behaviour.
- Gap: no peer-reviewed behaviour study, no group size, no time-by-time activity curve, and no feeding-radius measurement for adults. The 50 m juvenile roam is a secondary-source figure and cannot be shown in a 122 cm tank without scaling.

AI parameters (engineer goby)

| Parameter | Value / range | Unit | Evidence tag |
|---|---|---|---|
| Adult length | 30-34 (range to 60 flagged) | cm | [SOURCED] Wikipedia / Reefbuilders (conflict) |
| Juvenile-to-adult body change | ~15 | cm | [SOURCED] Reefbuilders (secondary) |
| Juvenile roam radius from burrow (wild) | up to 50 | m | [SOURCED] secondary: Wikipedia / NatGeo 2005 |
| Juvenile roam radius (tank, scaled) | 0.5-1.5 | BL from burrow | design estimate (scaled to 122 cm tank) |
| Juvenile daytime bout length | 20-120 | s | design estimate |
| Return to burrow | end of day / at light-off | time | [SOURCED] secondary |
| Night roost | hang from roof by mucous thread | state | [SOURCED] secondary |
| Adult time out of burrow | ~0 (rarely) | fraction of day | [SOURCED] "rarely leave" (Wikipedia, Reefbuilders); value 0.02 |
| Burrow total length | 6 | m | [SOURCED] secondary |
| Sand moved per parent per day | up to 3 | kg / day | [SOURCED] secondary |
| Schooling (juveniles) | tight school, under ledges or around coral heads | state | [SOURCED] Aquarium of Pacific / Wikipedia; group size not given |
| Adult swim | eel-like wave, low flow | state | [OBSERVATION: aquarium hobby report] |
| Juvenile feeding location | water column, plankton | state | [SOURCED] |
| Juvenile-to-adult food transfer | do not model | flag | [CONFLICT]; hypothesis only |

---

## 5. Diamond goby / orange-dash sleeper, Valenciennea puellaris (Sandy)

Facts
- [SOURCED] Myers, Gingras, Keyes, Konhauser & Zonneveld 2019, "Bioresuspension behaviours of the gobiid Valenciennea puellaris...", PALAIOS 34(3):179-189 (aquarium study, abstract only read; full methods not accessible). Feeding, digging and resting together: about 9 mouthfuls of sediment per minute, about 0.18 cm³ min⁻¹ (calc: about 0.02 cm³ per mouthful). Dug only in fine to coarse sand; gravel handled one grain at a time. Source: https://complete.bioone.org/journals/palaios/volume-34/issue-3/palo.2018.040/...
- [SOURCED] Congener aquarium trials (not V. puellaris): V. sexguttata and V. strigata reduced meiobenthic prey by 30.7% (SE 9.2) and 46.1% (SE 5.1) after 4 days of sifting. Source: summarised in the Univ. Copenhagen news, https://www1.bio.ku.dk/english/news/news/what-is-going-on-in-the-coral-food-web/
- [SOURCED] FishBase: usual depth 10-30 m; range about 2-84 m (existing note; both figures in use). Indo-Pacific, Red Sea to Samoa. Source: https://www.fishbase.se/summary/Valenciennea-puellaris.html
- [OBSERVATION: aquarium hobby report] Perches on the sand between sifting bouts; some individuals hover motionless just above the sand (the "hover goby" basis of the "sleeper goby" name). Source: fishprofiles.com (general genus description) https://fishprofiles.com/profiles/marine/Blennoid_Gobioid/Valenciennea_puellaris
- [OBSERVATION: aquarium hobby report] Pairs use shallow burrows under rubble or rock; a 200 L sand bed processed every 2-3 weeks (one guide's estimate; unverified).
- [OBSERVATION: aquarium hobby report] Needs supplemental feeding; cannot survive on sand fauna alone (multiple guides).
- [ANALOGY: other species] Gobies generally stay close to their den; figures not given for this species (general goby review quoted in the search, not retrieved).
- Gap: no peer-reviewed diel activity, home-range, pairing, or travel-distance data for V. puellaris were found. The Frontiers search link returned an unrelated economics paper.

AI parameters (diamond goby)

| Parameter | Value / range | Unit | Evidence tag |
|---|---|---|---|
| Mouthfuls per minute (sifting, incl. rest) | 9 | per min | [SOURCED] Myers et al. 2019 (aquarium) |
| Sediment per mouthful | 0.02 | cm³ | [SOURCED] calc from 0.18 cm³/min ÷ 9 |
| Field-upper bound mouthfuls | 26 | per min | [OBSERVATION: aquarium hobby report] (congener V. helsdingenii, Reef Builders) |
| Scoop duration | 0.6 | s | design estimate (existing note) |
| Chew/hover duration | 1.5-3.5 | s | design estimate (existing note) |
| Hover height above sand (resting) | 0.5-1.5 | cm | [OBSERVATION: aquarium hobby report] (hovering), value estimate |
| Travel stride between sifting stations | 1-2 | cm | design estimate (existing note) |
| Usual depth | 10-30 | m (wild) | [SOURCED] FishBase |
| Home range around burrow | 0.35 (35 cm radius) | m | design estimate (existing note), not measured |
| Burrow dart distance | 5 | BL/s burst | design estimate (existing note) |
| Digging limit | fine to coarse sand only | flag | [SOURCED] Myers et al. 2019 |
| Need for supplemental food | required | flag | [OBSERVATION: aquarium hobby report] |
| Sediment pair sharing | pairs share a burrow | flag | [OBSERVATION: aquarium hobby report] |

---

## Corrections to earlier notes (affect the tuning)

1. Diamond goby: the earlier "9 mouthfuls/min average" is now SOURCED (Myers et al. 2019, aquarium). The "26/min" figure is a congener field upper bound from a hobby piece, not a V. puellaris measurement. Keep it only as a ceiling.
2. Lawnmower blenny: the existing "bite 1-2/s" value conflicts with the 3,427/day field total (about 5 bites/min averaged over the day). Use the field total for daily budget. Reserve 1-2/s for the within-bout burst only.
3. Lawnmower blenny: the Scientia Marina "afternoon / juvenile / territory size" paper is on Ophioblennius trinitatis, a different genus. It is an analogy, not S. fasciatus data.
4. Engineer goby: the earlier 18 cm feeding radius and 15 cm transition are not sourced. The only radius in the sources is up to 50 m for juveniles (secondary). Adults rarely leave the burrow. Adult length is 30-34 cm in one source and up to 60 cm in another. Use the range.
5. Engineer goby: juvenile-to-adult food transfer is a contested anecdote (Stiefel, no paper). Do not implement it as mechanics.
6. Tailspot: the Ecsenius water-column plankton feeding (2-3 m) comes from E. midas. Do not apply it to E. stigmatura.
7. Indigo dottyback: no source gives patrol frequency, dart distance or bout duration for any Pseudochromis. The numbers in section 3 are design estimates.
8. Diamond goby FishBase depth: "usual 10-30 m" and "range 2-84 m" are both FishBase values; use the usual band for default placement.

## Unresolved gaps (need observation or full-text access)

- Blenny hop length, hop frequency, and perch duration: no source found for Salarias or Ecsenius. Pectoral-fin kinematics papers found were for mudskippers, leaping blennies (Alticus) and bluegill, not hopping blennies.
- Time-of-day activity for S. fasciatus, E. stigmatura, and P. fridmani: no source found.
- Flight initiation distance for any of the five species: no source found. A 2019 Marine Biology FID paper (Quadros et al., DOI 10.1007/s00227-019-3508-2) is on a damselfish; its PDF text could not be decoded here.
- Full text of Townsend & Tibbetts 2004 (sample size, bout structure, times of day). Abstract only was read.
- Full text of Myers et al. 2019 (Palaios). Abstract only was read.
- Stiefel's observations on Pholidichthys (no paper located).
- Two PDFs (Labra et al. 2007, uv.es; Quadros et al. 2019) returned only compressed streams; nothing from them is used here.

## Sources

- FishBase Ecsenius stigmatura: https://www.fishbase.se/summary/Ecsenius-stigmatura.html
- FishBase Ecsenius midas (summary 7561): https://www.fishbase.se/summary/7561
- FishBase Pseudochromis steenei (summary 14478): https://fishbase.se/summary/14478
- FishBase Pictichromis summary (24439): https://fishbase.org/summary/24439
- FishBase Valenciennea puellaris: https://www.fishbase.se/summary/Valenciennea-puellaris.html
- Townsend & Tibbetts 2004, J. Fish Biol. 65:77-90, DOI 10.1111/j.0022-1112.2004.00426.x; USC record: https://research.usc.edu.au/esploro/outputs/journalArticle/The-ecological-significance-of-the-combtoothed/99548308102621
- Scientia Marina 2014 (Ophioblennius trinitatis), via DOAJ: https://doaj.org/article/ab2ba349b7ba4187b7835580489f5bd8
- Springer et al., Pictichromis porphyreus field sex change, Env. Biol. Fish. (2014): https://link.springer.com/doi/10.1007/s10641-014-0265-4
- Myers et al. 2019, PALAIOS 34(3):179-189: https://complete.bioone.org/journals/palaios/volume-34/issue-3/palo.2018.040/BIORESUSPENSION-BEHAVIORS-OF-THE-GOBIID-VALENCIENNEA-PUELLARIS-AND-THE-BIOGENIC/10.2110/palo.2018.040.full
- Univ. Copenhagen (congener meiofauna drawdown): https://www1.bio.ku.dk/english/news/news/what-is-going-on-in-the-coral-food-web/
- Wikipedia Pholidichthys leucotaenia (cites Kaufmann 2005, NatGeo; Springer & Freihofer 1976): https://en.wikipedia.org/wiki/Pholidichthys_leucotaenia
- Aquarium of the Pacific, engineer goby: https://aquarium.org/animals/engineer-goby/
- Reef Builders, convict blennies: https://reefbuilders.com/2022/12/23/convict-blennies-are-the-closest-thing-to-cichlids-in-the-sea
- Wikipedia Pseudochrominae: https://en.wikipedia.org/wiki/Pseudochrominae
- Top Shelf Aquatics tailspot: https://topshelfaquatics.com/pages/saltwater-fish/tailspot-blenny
- Gensou lawnmower / tailspot guides: https://gensou.sg/lawnmower-blenny-care-guide/ , https://gensou.sg/?p=30499
- Aquariadise lawnmower: https://aquariadise.com/lawnmower-blenny/
- ultimatereef midas blenny anecdote: https://www.ultimatereef.net/threads/midas-blenny-aggressive-behaviour.368325/
- SeaScape fish guide (Splendid pseudochromis, family-level): https://seascapestudio.net/reference/fish.php?id=253
- fishprofiles.com Valenciennea: https://fishprofiles.com/profiles/marine/Blennoid_Gobioid/Valenciennea_puellaris

---

## AI parameter summary (all five)

| Species | Locomotion | Home / anchor | Daily budget | Social rule | Startle |
|---|---|---|---|---|---|
| Tailspot | short darts, hole-anchored | 0.5-1 BL around hole | not measured | low chase (0.2, design) | dart into hole within ~1 s |
| Lawnmower | hops 2-5 BL (design) | rock surface, territory scales with size | 3,427 bites/day (~5/min) | chase 0.9 blennies, 0.2 damsels | hop to crevice |
| Indigo dottyback | darts 3-10 BL cave-to-cave | cave, radius 0.5-1 m | not measured | chase smaller fish 1-2 BL | dart to cave |
| Engineer goby | eel-like; juveniles school | burrow, juveniles roam up to 50 m (wild) | adults stay in burrow | juveniles school; adults territorial (not quantified) | retreat into burrow |
| Diamond goby | hover-hop over sand | burrow under rock, 35 cm (design) | 9 mouthfuls/min (sifting) | ignores tankmates (existing note) | dart into burrow |
