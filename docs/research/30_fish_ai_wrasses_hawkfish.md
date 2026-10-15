# Fish AI research: bluestreak cleaner wrasse, blue star leopard wrasse, falco hawkfish

Research pass 2026-10-15. Adds to `10_fish_behavior_detail.md` (sections 4 and 5), `15_master_livestock_research.md`, and the video notes in `docs/research/video/fish_*.md`. Existing [L] numbers are not repeated unless a new source changes them.

**Tags:** [SOURCED] = number or statement from the cited paper or FishBase page. [SOURCED-snippet] = seen in search-result text, primary page not opened. [ANALOGY: other species] = congener or family proxy. [OBSERVATION: aquarium hobby report] = husbandry write-up, not measured. [UNRESOLVED] = searched, no usable number found.

**Access notes.** Several primary papers (Bshary & Grutter 2002, Grutter et al. 2002 full text, Wainwright 2002 PDF) were blocked (HTTP 429) or returned no readable text. Those rows are marked accordingly. The Springer full text of the Japanese *Cirrhitichthys falco* field paper also could not be opened (login redirect).

**Corrections to existing notes.** (1) The hawkfish file says "5-20 hops per hour" is unsupported. The congener data below (perch changes 46-55 per hour) are a different measure, but they are the first quantitative field rate found. (2) The cleaner-wrasse file's "pecks at 2-3 Hz per client" has no source here. The only measured quantities are inspection episode counts and contact durations (below). (3) The leopard-wrasse "10-20 picks/min" has no source here. It stays [UNRESOLVED].

---

## 1. Bluestreak cleaner wrasse: *Labroides dimidiatus* ("Shakira")

### Facts

- **Time budget at one station.** Cleaners spent about 26% of their time inspecting clients. In 15-minute observations they inspected 59.5 ± 5.1 fish each. This is one site (Noumea, New Caledonia, 1999). Counts may include brief passes that did not end in a cleaning. [SOURCED] (CSIRO Mar. Freshw. Res. 1999, https://publish.csiro.au/MF/MF97078, [SOURCED-snippet]).
- **Repeat cleanings of one client.** The thick-lipped wrasse *Hemigymnus melapterus* was cleaned 2.5 ± 0.5 times per 30 min. Each cleaning contact lasted 17.5 ± 2.5 s. Same source. [SOURCED-snippet]. Gnathiid isopods made up most of the diet, and 88% of *H. melapterus* were infested. Same source.
- **Interaction length scales with parasite load.** Cleaners spent more time and took more bites per time unit on the parasite-infected side of the client surgeonfish *Ctenochaetus striatus*. Interaction duration also depended on parasite load. [SOURCED] (Bshary & Grutter 2002, MEPS 235:217-222, https://marineinfo.org/doc/publication/36197, fetched).
- **Tactile stimulation is a separate behaviour.** Cleaners touch the client's dorsal-fin area with pelvic and pectoral fins. The cleaner's head points away from the client's body, so this is not foraging. Field data (12 cleaners, 112 h) show cleaners use it to lengthen client stays and to stop clients fleeing or chasing after a bite. Predatory clients got more tactile stimulation than non-predatory ones. [SOURCED-snippet] (Bshary & Würth 2001, Proc. R. Soc. B 268:1495-1501, doi:10.1098/rspb.2001.1495).
- **Diel pattern.** Inspection duration and frequency did not change across the day. Client foraging dropped at midday and in the afternoon, which the authors attribute to cleaning costs. [SOURCED] (Grutter, McCallum & Lester 2002, MEPS 234:257-264, https://marineinfo.org/doc/publication/37041, fetched). The cleaner's own rhythm is therefore flat; the client's is not.
- **Home range is small.** The congener *L. bicolor* has much larger home ranges than *L. dimidiatus* at Moorea. The authors link this to cheating control: a cleaner that roves widely is harder to monitor. No m² figure was found. [SOURCED-snippet] (Oates, Manica & Bshary 2010, Ethology; no URL retrieved). Station-holding is consistent with this.
- **Satiation lowers service.** Field and lab work found satiated cleaners reduce service quality to clients. [SOURCED-snippet] (Triki et al. 2025, Behav. Ecol., title only; no URL retrieved). Model: service effort drops when the cleaner's hunger is low.
- **Daily volume (secondary, unverified).** A University of Queensland summary says one cleaner may inspect up to 2,300 fish and remove about 1,200 parasites in a day. This is a single-cleaner daily figure from a popular summary. [SOURCED-snippet, low confidence] (about.uq.edu.au; primary not checked).
- **Clients served.** 132 client species are served (Grutter & Poulin 1998, as cited in a 2019 review). [SOURCED-snippet]
- **[UNRESOLVED]** Visits per hour, peck rate, greeting-dance timing, and jolt probability per visit. The searches found no measurements. The existing file's "2-3 Hz pecks" and "3-5 body length leash" remain unsourced here.

### AI parameters: cleaner wrasse

| Parameter | Value / range | Unit | Evidence |
|---|---|---|---|
| Fraction of time inspecting clients (at station) | 0.26 | fraction | [SOURCED] (Noumea 1999, one site) |
| Inspections counted per station | ~60 | per 15 min (~4 per min) | [SOURCED] (same; count may include brief passes) |
| Cleanings per client (one client, active) | 2.5 ± 0.5 | per 30 min | [SOURCED] (same) |
| Duration of one cleaning contact | 17.5 ± 2.5 | s | [SOURCED] (same) |
| Bite rate on infected vs clean client | higher on infected side | qualitative | [SOURCED] (Bshary & Grutter 2002) |
| Interaction length vs parasite load | increases with load | qualitative | [SOURCED] |
| Tactile stimulation role | extends stay; suppresses chase after jolt | qualitative | [SOURCED-snippet] (Bshary & Würth 2001) |
| Client-foraging dip, midday-afternoon | client-side; cleaner rate flat | qualitative | [SOURCED] (Grutter et al. 2002) |
| Satiation effect on service | lower service when satiated | qualitative | [SOURCED-snippet] (Triki et al. 2025) |
| Territory vs congener | smaller than *L. bicolor* | qualitative | [SOURCED-snippet] (Oates et al. 2010) |
| Peck rate per client | none found | per s | [UNRESOLVED] |
| Visits per hour | none found | per h | [UNRESOLVED] |

---

## 2. Blue star leopard wrasse: *Macropharyngodon bipartitus* (female, "Marina")

### Facts

- **Size and depth.** Maximum 13.0 cm TL; depth to about 30 m; lagoons and sheltered seaward reefs. [SOURCED] (FishBase, https://www.fishbase.se/summary/Macropharyngodon-bipartitus.html, fetched).
- **Feeding mode.** Picks invertebrates from the substratum. [SOURCED] (FishBase, same page). Congener *M. meleagris* eats mainly gastropods, hard-shelled prey and forams (FishBase summary/4984, [SOURCED-snippet]).
- **Social.** Occurs singly or in pairs; females often in small groups. No harem is described. [SOURCED] (FishBase). Hobby sources say several females can be kept together but multiple males clash [OBSERVATION: aquarium hobby report] (ultimatereef, qualitymarine, [SOURCED-snippet] only; no harem data).
- **Burial as a startle response.** Burrows into sand "at the sign of danger." [SOURCED] (FishBase). Hobby sources say it dives on sudden light changes, chasing, or startle, and sometimes by day if threatened [OBSERVATION: aquarium hobby report]. Timing of burial mechanics belongs to the burial team; only the trigger and duration are noted here.
- **Activity pattern.** Described as very active by day, moving methodically across sand and rock for small crustaceans and snails. [OBSERVATION: aquarium hobby report] (topshelfaquatics, liveaquaria, [SOURCED-snippet]). No swim-speed or bout data found.
- **Post-introduction burial.** Some fish stay buried for days after introduction. Hobby sources attribute this partly to circadian resetting after shipping. [OBSERVATION: aquarium hobby report] (already in TFH and LiveAquaria notes; not repeated).
- **[UNRESOLVED]** Pecking rate, route shape, stop frequency, sand-patch dwell time, prey-capture rate. No peer-reviewed study of *Macropharyngodon* foraging was found. The Halichoeres trimaculatus light-entrainment study is [ANALOGY: other species] for timing and is already in the video notes.

### AI parameters: leopard wrasse

| Parameter | Value / range | Unit | Evidence |
|---|---|---|---|
| Max length | 13.0 | cm TL | [SOURCED] (FishBase) |
| Depth range in the wild | to ~30 | m | [SOURCED] (FishBase) |
| Burial trigger | startle, chase, lights-out | event | [SOURCED] (FishBase: sign of danger); [OBSERVATION: aquarium hobby report] (lights-out) |
| Social grouping | 1 per tank strongly preferred; several females workable | count | [SOURCED] (FishBase: single/pairs); [OBSERVATION: aquarium hobby report] (female groups, male clashes) |
| Active-day movement pattern | methodical, continuous; not perch-based | qualitative | [OBSERVATION: aquarium hobby report] |
| Pecks per minute while foraging | none found | per min | [UNRESOLVED] (existing 10-20/min unsupported) |
| Route / stop frequency | none found | per min | [UNRESOLVED] |
| Swim speed / bout length | none found | BL/s, s | [UNRESOLVED] (labriform proxy in existing file) |

---

## 3. Falco hawkfish: *Cirrhitichthys falco* ("Hawk tuah")

Most quantitative perch and movement data available are for the congener *Paracirrhites forsteri* (Red Sea field study). Use those as [ANALOGY: other species] for falco. The falco-specific facts come from FishBase and one Japanese field study whose full text was not accessible.

### Facts

- **Congener perch behaviour.** In 10-min focal observations at 5-15 m, *P. forsteri* changed perch 7.6 ± 0.8 to 9.1 ± 0.6 times per 10 min, depending on colour morph. That is one perch change every 66-79 s. [ANALOGY: other species] (Coker, Chaidez & Berumen 2017, PLoS ONE 12(1):e0169079, https://pmc.ncbi.nlm.nih.gov/articles/PMC5268474/, fetched). The existing "5-20 hops per hour" figure is therefore too low for this congener, though the measure is "perch change", not identical hops.
- **Perch substrate.** Congener spent 62-70% of time perched on live hard coral (Acropora 17-23%, Pocilloporidae 35-47%) and 22-30% on dead coral. About 90% of time on complex habitat. [ANALOGY: other species] (same).
- **Movement distance.** Distance moved in 10 min ranged 0.2-20 m across morphs. Fish under 130 mm TL mostly stayed within 5 m. Morph 3 movement was capped at about 5 m regardless of size. [ANALOGY: other species] (same; the paper's Results and Discussion disagree slightly on the cap, so treat 3-5 m as the range).
- **Refuge when threatened.** Congener retreated to Acropora (36-50%) or Pocillopora (19-43%), and to dead coral (14-50%) depending on morph. [ANALOGY: other species] (same).
- **Density and depth.** Highest densities on exposed reef sides; up to 5.7 fish per 4 x 30 m transect at 17 m depth; absent from inshore reefs. [ANALOGY: other species] (same).
- **Falco depth and resting.** Depth 4-46 m, usually 10-20 m. Typically rests at the bases of coral heads. [SOURCED] (FishBase, https://www.fishbase.org/summary/5445, fetched).
- **Falco social system.** FishBase: haremic and spawns nightly (ref. 37816); occasionally in pairs; sometimes solitary. Spawning ascents 0.2-0.6 m. [SOURCED] (same). A Japanese field study at Kuchierabu-jima (Env. Biol. Fish. 2011, doi:10.1007/s10641-011-9817-z) reports adult territories defended against same-sex rivals, and a male territory covering 2-3 females. [SOURCED-snippet] (full text not accessed). Smaller juveniles can live inside a female's territory. Diet by size: smallest fish mainly copepods, larger mainly decapods. [SOURCED-snippet]
- **Strike pattern.** Ambush from a perch: waits almost motionless, then lunges in a short burst and returns to cover. Also described as more active than most ambush predators. [OBSERVATION: aquarium hobby report] (Top Shelf, Reef Builders, field-guide summaries, [SOURCED-snippet]). No strike-distance measurements found.
- **Pectoral "landing gear".** No swim bladder; thickened lower pectoral rays used to rest on coral heads. [SOURCED-snippet] (Reef Builders; FishBase).
- **[UNRESOLVED]** Falco-specific perch durations, hop lengths, head-scan rate, strike distance and speed, favourite-perch count. Cirrhitid strike mechanics were searched; no quantitative paper was found.

### AI parameters: falco hawkfish

| Parameter | Value / range | Unit | Evidence |
|---|---|---|---|
| Perch changes per 10 min (congener) | 7.6-9.1 | count per 10 min | [ANALOGY: other species] (*P. forsteri*) |
| Mean interval between perch changes | 66-79 | s | [ANALOGY: other species] (derived from the above) |
| Time on live hard coral while perched | 62-70 | % | [ANALOGY: other species] |
| Time on dead coral | 22-30 | % | [ANALOGY: other species] |
| Movement distance per 10 min | 0.2-20 (small fish ~within 5) | m | [ANALOGY: other species] |
| Movement cap for one morph | ~5 (3-5) | m | [ANALOGY: other species] |
| Refuge: Acropora/Pocillopora share when threatened | 36-50 (Acropora), 19-43 (Pocillopora) | % | [ANALOGY: other species] |
| Falco typical depth | 10-20 (4-46 range) | m | [SOURCED] (FishBase) |
| Territory defence vs same-sex rivals | yes; male range covers 2-3 females | qualitative/count | [SOURCED-snippet] (Kuchierabu 2011) |
| Spawning ascent height | 0.2-0.6 | m | [SOURCED] (FishBase; not used for solitary fish) |
| Strike burst | short lunge, return to cover | qualitative | [OBSERVATION: aquarium hobby report] |
| Strike distance / speed | none found | cm, BL/s | [UNRESOLVED] |
| Falco perch duration | none found | s | [UNRESOLVED] |

---

## Sources

- Grutter, McCallum & Lester 2002, MEPS 234:257-264. https://marineinfo.org/doc/publication/37041 (fetched; abstract qualitative only for *L. dimidiatus*).
- Bshary & Grutter 2002, MEPS 235:217-222. https://marineinfo.org/doc/publication/36197 (fetched).
- CSIRO Marine & Freshwater Research 1999, Noumea cleaner-wrasse time budget. https://publish.csiro.au/MF/MF97078 (search snippet only).
- Bshary & Würth 2001, Proc. R. Soc. B 268:1495-1501, doi:10.1098/rspb.2001.1495 (search snippet only).
- Oates, Manica & Bshary 2010, Ethology (*L. bicolor* vs *L. dimidiatus* home range) (search snippet only; no URL retrieved).
- Triki et al. 2025, Behav. Ecol., satiation and cleaner service (title only).
- Coker, Chaidez & Berumen 2017, PLoS ONE 12(1):e0169079. https://pmc.ncbi.nlm.nih.gov/articles/PMC5268474/ (fetched).
- FishBase, *Macropharyngodon bipartitus*. https://www.fishbase.se/summary/Macropharyngodon-bipartitus.html (fetched).
- FishBase, *Cirrhitichthys falco* (summary/5445). https://www.fishbase.org/summary/5445 (fetched).
- Kuchierabu-jima field study, *C. falco*, Env. Biol. Fish. 2011, doi:10.1007/s10641-011-9817-z (search snippet; full text not accessed).
- UQ summary, *L. dimidiatus* daily inspection count (about.uq.edu.au; secondary, low confidence).
- Wainwright, Bellwood & Westneat 2002 (labriform rowing vs flapping). PDF not readable; the existing file's proxy values are kept as unverified here.
