# 41. Blue tang (Paracanthurus hepatus) breeding and larval rearing

Research note for the simulation's blue hippo tang breeding and fry system. Scope: spawning, eggs, larval timeline,
rearing protocol, juvenile growth, a numbers table for the sim, and sources.

**Tag key used everywhere below**

- `[M]` measured in a primary or semi-primary rearing study (the value is in the source's own text or data)
- `[S]` secondary report (trade press, encyclopedia, company or retailer page); not checked against a primary paper
- `[A]` analogue: yellow tang *Zebrasoma flavescens* (Oceanic Institute) or another acanthurid, used where no blue tang value exists
- `[E]` estimate (my inference from the values above; not in any source)
- `[X]` source found but contents not readable (fetch failed or PDF was binary)

**Access status of the primary paper.** The key source is DiMaggio et al. (2017), University of Florida Tropical
Aquaculture Laboratory. Its abstract and summary were readable via the WAS meeting page. The full PDF on pelagicos.net
came back as an unreadable binary, so the day-by-day lengths, egg diameter and settlement data below are **not** verified
from the paper. Where the only source is a snippet or secondary page, it is tagged `[S]`.

---

## 1. Spawning behaviour

| Item | Value | Tag | Source |
|---|---|---|---|
| Mating system | Paired or group spawning in acanthurids. Group spawning is the most common pattern. Pairs form within a school or at the reef edge. | `[A]` (family-level) | American Samoa surgeonfish study (scholarspace), search snippet |
| Time of day (P. hepatus) | Late afternoon and evening, near outer reef slopes | `[S]` | Wikipedia, Paracanthurus |
| Time of day (other acanthurids, American Samoa) | *A. triostegus* dusk; *A. lineatus* dawn; *T. quinquevittatum* and *T. hardwickii* daytime. Spawning in or next to the reef channel. | `[A]` | American Samoa study |
| Season | Year-round, peaking April to September | `[S]` | Wikipedia |
| Trigger | Males chase females within the school. Colour shifts from dark blue to pale blue when spawning starts. Spawning rush is upward toward the surface. | `[S]` | Earth.com (blue-shift cue); upward rush `[A]` |
| Lunar cue (yellow tang) | Natural monthly spawning with peak egg production around the full moon. | `[A]` | Callan, Oceanic Institute abstract (WAS 41890) |
| Spawning frequency (yellow tang) | Monthly, lunar-linked. Blue tang frequency not reported. | `[A]` | Callan abstract |
| Broodstock conditions (yellow tang) | 20 m3 tanks, 6 to 8 turnovers per day, 26-27 C, 31-33 ppt | `[A]` | Callan abstract |
| Broodstock diet (blue tang) | High fat, high protein "Fertility Frenzy" feed for about 6 months before spawning | `[S]` | Reef Builders 2016 (search snippet) |
| Broodstock in UF trial | 1 male and 2 females | `[M]` | DiMaggio et al. 2017 (WAS 46071) |
| Eggs per spawn (blue tang) | 50,000 eggs collected over 3 days in May 2016 from the 1M + 2F group | `[M]` | DiMaggio et al. 2017 |
| Fecundity per female per spawn | Not found | n/a | Gap |
| Age at sexual maturity | 9 to 12 months | `[S]` | Earth.com |
| Length at first maturity | 149.2 mm | `[S]` | Wikipedia (conflicts with the age figure; no conversion) |
| Maximum size | 31.0 cm total length (males or unsexed) | `[S]` | FishBase, citing Ref 2334 |
| Sex | Protogynous or sex-changing: not confirmed in any source here. Do not model this without a source. | n/a | Gap |

**How home aquarists see tangs spawn.** Spawning in aquaria is rarely reported. The Oceanic Institute and UF
programmes used large (1,000 to 20,000 L) tanks with natural lunar and photoperiod cues. Home tanks almost never
produce spawns for blue tangs. Acanthurid eggs and larvae are not expected to survive in a standard display tank, so
any sim version should use a rare event probability and a separate larval setup (like the clownfish system).

---

## 2. Eggs

| Item | Value | Tag | Source |
|---|---|---|---|
| Egg diameter | About 0.8 mm | `[S]` | Wikipedia; Earth.com (same figure) |
| Buoyancy | Pelagic. One oil droplet for flotation. | `[S]` | Wikipedia |
| Hatch time, P. hepatus | About 24 h (general statement, no temperature given) | `[S]` | Wikipedia |
| Hatch time, P. hepatus, Taiwan claim | About 26 h | `[S]` (weak) | subdiversion.es, low-authority |
| Hatch time, *Z. flavescens* | About 20 h at 26 C | `[A]` | Callan abstract (OI) |
| Egg number per spawn, *Z. flavescens* | Not reported | n/a | Gap |
| Stocking density (blue tang UF) | 50 eggs/L in a 1,000 L tank | `[M]` | DiMaggio et al. 2017 |
| Stocking density (yellow tang OI) | 40 eggs/L in 1,000 L fiberglass | `[A]` | Callan abstract |
| Aeration | Two submerged airstones near the tank centre creating an upwelling | `[A]` | Callan abstract |

**Hatch timing to temperature.** Only one hatch time is tied to a temperature (about 20 h at 26 C for yellow tang).
Use this as the reference point and apply a Q10 style scaling (`[E]`, Q10 about 2 to 2.5 for marine fish eggs; not
checked against a source): time scales roughly with 1/rate, so 24 C would be about 24 h and 28 C about 17 h.

---

## 3. Larvae: day-by-day timeline

Day 0 is hatch. Values are for blue tang unless marked otherwise.

| Day (DPH) | Event | Length | Tag | Source |
|---|---|---|---|---|
| 0 | Hatch. Very primitive larvae. Rudimentary caudal spines and translucent body with silvery abdomen. | Not measured in verified source | `[S]` / `[A]` | Wikipedia; Callan |
| 0 to 3 | Yolk absorption. Yolk-sac phase (no feeding). | n/a | `[A]` | Callan, first feeding at 3 DPH |
| 3 | First feeding. Copepod nauplii of *Parvocalanus crassirostris*, 3 times daily, 5.1 +/- 2.3 mL/day. | n/a | `[M]` | DiMaggio et al. 2017 |
| 5 | Notochord length about 3 mm (UF imagery). Yolk absorbed, mouth and gut developed. | About 3 mm NL | `[S]` (search snippet) | Rising Tide / UF |
| 5 to 7 | Heavy mortality just after first feeding. About 20% of yellow tang starters survived. | n/a | `[A]` | Callan abstract |
| 7 | Major die-off, attributed to starvation (blue tang UF trial). | n/a | `[M]` | DiMaggio et al. 2017 |
| 8 | Reported: no larvae survived past 8 DPH after ciliate feeding (Euplotes). | n/a | `[S]` | Breeders Registry 2017 update |
| 10 to 30 | Survivors persist with little attrition (yellow tang). | n/a | `[A]` | Callan abstract |
| 12 | Enriched rotifers (*Brachionus plicatilis*) added, 6.8 +/- 3.2 mL/day. | n/a | `[M]` | DiMaggio et al. 2017 |
| 14 | Large dorsal and ventral spines present. Persist through flexion. | n/a | `[A]` | Callan abstract |
| 20 to 21 | Powdered feed and first-instar *Artemia* nauplii added (1.1 +/- 0.7 mL/day). Major die-off attributed to flexion at 20 DPH. | n/a | `[M]` | DiMaggio et al. 2017 |
| 26 | Flexion (yellow tang). Spines still present. | n/a | `[A]` | Callan abstract |
| 30 | Later-stage nauplii (N3 to N5), copepodites and *Artemia* added (yellow tang). | n/a | `[A]` | Callan abstract |
| 30 to 60 | Slow development. Author attributes to unsuitable live prey and possibly missing cues. Daily mortality of the largest larvae. | n/a | `[A]` | Callan abstract |
| 41 | Behaviour change: most survivors stay near the tank bottom. | n/a | `[M]` | DiMaggio et al. 2017 |
| 50 | First blue pigmentation: start of metamorphosis. | n/a | `[M]` | DiMaggio et al. 2017 |
| 60 | Spines start receding (yellow tang). | n/a | `[A]` | Callan abstract |
| 70 | Milestone for yellow tang larvae (forum summary, not OI paper). | n/a | `[S]` | MASA forum post |
| 78 | Surviving larvae completed metamorphosis with adult colour. | n/a | `[S]` | Breeders Registry 2017 update |
| 83 | Yellow tang reared to 83 DPH, no settlement observed. | n/a | `[A]` | Callan abstract |

**Settlement stage (acronurus).** Not measured in any readable source. Two reports:

- Rising Tide: pelagic larval duration (PLD) estimated at 50 to 90 days. `[S]`
- Reef Builders 2016 (search snippet): about three weeks to settle after the pelagic stage. `[S]` (anecdotal, unverified)

Neither source gives settlement length or a settlement-day value. The sim should not assume a settlement day from
these numbers until the DiMaggio PDF is read in full.

**Pelagic larval duration (PLD) for acanthurids.** No peer-reviewed PLD for Acanthuridae was found in this search. The
subdiversion.es claim of about 2.5 months (`[S]` weak) agrees with the 50 to 90 day estimate above, which is the best
available range.

**Length at settlement and juvenile colour.** Not found. The UF juveniles that metamorphosed showed pale blue
pigmentation at the start (day 50) and adult colour at 78 DPH (`[S]`). Juvenile yellow colour for the blue tang was not
confirmed in the sources read. Juvenile *Z. flavescens* are yellow from settlement (`[A]`, general knowledge,
not checked in this search).

---

## 4. Rearing protocol (UF blue tang, 2016)

| Parameter | Value | Tag | Source |
|---|---|---|---|
| Tank volume | 1,000 L | `[M]` | DiMaggio et al. 2017 |
| Stocking | 50,000 eggs, 50 eggs/L | `[M]` | DiMaggio et al. 2017 |
| Egg collection | 3 days, May 2016 | `[M]` | DiMaggio et al. 2017 |
| Live algae | Daily, about 3:1 *Tetraselmis chuii* and *Symbiodinium microadriaticum* | `[M]` | DiMaggio et al. 2017 |
| Feeding frequency | 3 times daily for copepod nauplii | `[M]` | DiMaggio et al. 2017 |
| Feeding ramp | Copepods (3 DPH), rotifers (12 DPH), Artemia and powdered feed (20 to 21 DPH) | `[M]` | DiMaggio et al. 2017 |
| Enrichment (rotifers) | Ori-One, then Algamac 3050 | `[S]` | Breeders Registry 2017 update |
| Ciliate feeding | Euplotes sp. led to no survivors past 8 DPH | `[S]` | Breeders Registry 2017 update |
| Survival to day 4 | 80% or better (UF summary) | `[S]` | UF summary (search snippet) |
| Final juveniles | 27 from 50,000 eggs = 0.054% | `[M]` | DiMaggio et al. 2017 |
| Temperature | Not verified. Critical per UF lab director; larvae can die within hours of a drop. | `[S]` | Reef Builders 2016 |
| Salinity, light | Not verified for blue tang | n/a | Gap |

**Yellow tang analogue (OI, Callan abstract, `[A]`).**

- Water: 8 to 10 daily turnovers of 1 micron filtered UV-treated seawater.
- Light: 24 L:0 D, 1,500 lux at surface.
- No background algae.
- Feed: *Parvocalanus crassirostris* nauplii, 1 to 5 mL/day twice daily from first feeding.
- Stock: 40 eggs/L in 1,000 L.
- Survival: about 20% through the first-feeding window (5 to 7 DPH). About 1% to settlement reported in 2020
  (Aquaculture America abstract, search snippet only, `[A]`).

**Critical mortality points (blue tang and yellow tang).**

1. Days 5 to 8: starvation and first-feeding failure. Largest losses in both species.
2. Days 14 to 26: flexion and spine problems (blue tang day 20, yellow tang day 26).
3. Days 30 to 60: slow growth and live-prey mismatch (yellow tang). Blue tang day 41 behaviour change.
4. Metamorphosis (blue tang day 50 onward): first pigmentation and settlement losses.

**Water quality warnings.** Temperature and water quality must be held steady. Larvae can die within hours of an
error (UF lab director, `[S]`). Ciliates and unsuitable prey kill larvae (`[S]`, `[A]`).

---

## 5. Juvenile growth to tank-safe size

| Item | Value | Tag | Source |
|---|---|---|---|
| Growth to 25 mm | Not found | n/a | Gap |
| Growth to 50 mm | Not found | n/a | Gap |
| Typical retail size of captive-bred juvenile | About 8 inches (about 20 cm) adult size noted in a retailer listing; not a juvenile size | `[S]` (weak) | Biota Group retailer listing |
| Maximum size | 31.0 cm TL | `[S]` | FishBase |
| Size at maturity | 149.2 mm (first maturity, Wikipedia) | `[S]` | Wikipedia |
| Age at maturity | 9 to 12 months | `[S]` | Earth.com |

No growth curve for captive or wild juvenile blue tangs was found. Any sim growth rate for juveniles must be marked
`[E]` until a source is read.

---

## 6. Numbers table for the sim

Use these values with the tags shown. `[E]` items are estimates for the game and should be replaced by a primary value
when one is read.

| Sim parameter | Suggested value | Tag | Basis |
|---|---|---|---|
| Mating unit | 1 male + 2 females (per group) | `[M]` | UF broodstock |
| Eggs per spawn (per group) | 50,000 in a single collection (3 days) | `[M]` | UF |
| Egg diameter | 0.8 mm | `[S]` | Wikipedia |
| Egg buoyancy | Pelagic, with oil droplet | `[S]` | Wikipedia |
| Spawning time | Late afternoon / evening | `[S]` | Wikipedia |
| Spawning season | Year-round, peak April to September | `[S]` | Wikipedia |
| Spawn trigger | Blue shift + chase | `[S]` | Earth.com |
| Lunar cue | Full moon peak | `[A]` | Yellow tang OI |
| Hatch time at 26 C | 20 to 24 h | `[A]` / `[S]` | OI, Wikipedia |
| Yolk-sac phase | 3 days (first feeding at 3 DPH) | `[A]` / `[M]` | OI, UF |
| First feed | Copepod nauplii (*P. crassirostris*) | `[M]` | UF |
| Rotifer feed start | Day 12 | `[M]` | UF |
| Artemia start | Day 20 to 21 | `[M]` | UF |
| Major mortality days | 5 to 8, then 20 to 26 | `[M]` / `[A]` | UF, OI |
| Spine development | Large spines by 14 DPH, persist to flexion (26 DPH) | `[A]` | OI |
| Blue pigmentation start | Day 50 | `[M]` | UF |
| Metamorphosis complete | 78 DPH | `[S]` | Breeders Registry |
| Yellow tang rearing max | 83 DPH, no settlement | `[A]` | OI |
| Settlement (PLD) | 50 to 90 days (not measured) | `[S]` | Rising Tide |
| Settlement survival | About 1% of starting larvae (yellow tang) | `[A]` | Aquaculture America 2020 |
| Survival to day 4 | 80% or more | `[S]` | UF summary |
| Survival to day 5 to 7 | About 20% (yellow tang) | `[A]` | OI |
| Final juvenile yield | 0.054% of eggs | `[M]` | UF |
| Juvenile length at settlement | Not known | n/a | Gap |
| Juvenile length at 25 mm / 50 mm | Not known | n/a | Gap |
| Age at maturity | 9 to 12 months | `[S]` | Earth.com |
| Length at maturity | 149.2 mm | `[S]` | Wikipedia |
| Max length | 310 mm | `[S]` | FishBase |
| Fecundity per female | Not known | n/a | Gap |
| Spawning frequency per pair | Not known for blue tang; monthly, lunar-linked for yellow tang | `[A]` | OI |
| Sim egg batch size (suggested) | 50,000 eggs per spawn event, with a low hatch-to-juvenile chance (about 0.05%) | `[E]` | UF yield |
| Sim larval stages | Hatch, first feed, rotifer, Artemia, flexion, pigmentation, settlement | `[E]` | UF timeline |
| Sim juvenile growth | Not sourced; treat as `[E]` | `[E]` | none |

**Sim use notes.**

- The UF yield (27 juveniles from 50,000 eggs) is too low for a game. Use a boosted survival rate and say so in
  the sim documentation. This is a game choice, not a biological value.
- The day-50 pigmentation and day-78 metamorphosis are the only blue tang milestone days with measured or
  semi-measured support. Use them as the anchor for the juvenile phase.

---

## 7. Gaps and conflicts

1. **Egg diameter and hatch time for blue tang** rest on Wikipedia and a low-authority site. The primary paper was
   not readable.
2. **Larval length at hatch and day-by-day length** is not verified. The 3 mm at 5 DPH figure is from a search snippet.
3. **Settlement day and length** are not available. The 50 to 90 day PLD and the three-week claim conflict.
4. **Juvenile growth** (to 25 mm or 50 mm) was not found for any tang.
5. **Fecundity** was not found for any blue tang or yellow tang source.
6. **Sex change and sexual system** were not confirmed. Do not model protogyny without a source.
7. **Atlantis Aquarium, Ocean Rider and Bali Aquarich** did not show any captive-bred blue tang sales or results
   in searches. Bali Aquarich's tang result is from 2019 and is about purple tang (*Zebrasoma xanthurum*). A 2025
   CORAL listing says The Biota Group announced commercial-scale captive-bred Pacific blue tang production after
   five years of work. This is a company announcement, not a peer-reviewed result.
8. **Wild acanthurid larval duration** (*Acanthurus* spp.) was not found. The search returned no data.

---

## 8. Sources

1. DiMaggio, M.A., Cassiano, E.J., Barden, K.P., Ramee, S.W., Ohs, C.L., Watson, C.A. (2017). Captive larval
   culture of Pacific blue tang. Abstract and summary read from WAS meeting page.
   https://www.was.org/Meetings/ShowAbstract.aspx?Id=46071 (`[M]` for numbers quoted above)
2. DiMaggio et al. 2017 PDF (full text not readable; binary returned).
   http://www.pelagicos.net/MARS6910_fall2017/References/DiMaggio_et_al._2017.pdf (`[X]`)
3. Rising Tide Conservation, UF IFAS Tropical Aquaculture Lab closes Pacific blue tang life cycle (page content not
   returned on fetch; values from search snippet). https://www.risingtideconservation.org/uf-ifas-tropical-aquaculture-lab-successfully-closes-the-pacific-blue-tang-paracanthurus-hepatus-life-cycle/ (`[X]` / `[S]`)
4. Reef Builders, Captive bred blue tangs coming soon (2016).
   https://reefbuilders.com/2016/10/26/captive-bred-blue-tangs-coming-soon (`[S]`, search snippet)
5. Reef Builders, Aquaculturing Dory (2016), search result only, not fetched.
   https://reefbuilders.com/2016/07/20/aquaculturing-dory (`[S]`, not verified)
6. Breeders Registry, First record of captive larval culture and metamorphosis of Pacific blue tang (2017 update).
   https://breedersregistry.org/br-blog/paracanthurus-hepatus-spawned-in-captivity (`[S]`)
7. Callan, C.K., Oceanic Institute of Hawaii Pacific University, Advances in yellow tang *Zebrasoma flavescens* culture
   (WAS abstract). https://www.was.org/meetings/ShowAbstract.aspx?Id=41890 (`[A]`)
8. Aquaculture America 2020, yellow tang abstract (survival to settlement about 1%; 25% at 7 DPH).
   https://was.org/Meeting/Program/PaperDetail/156524 (`[A]`, search snippet only)
9. Coral Magazine, Yellow tang research at the Oceanic Institute (2014), background only, not fetched.
   https://www.coralmagazine.com/2014/02/24/yellow-tang-research-at-the-oceanic-institute-making-exciting-progress/
10. Wikipedia, Paracanthurus. https://en.wikipedia.org/wiki/Paracanthurus (`[S]` for egg size, spawning time, season,
    maturity length)
11. FishBase, Paracanthurus hepatus, species summary 6017.
    https://fishbase.se/summary/SpeciesSummary.php?id=6017 (`[S]` for maximum size 31.0 cm)
12. Earth.com, Paracanthurus hepatus encyclopedia entry (maturity age, colour shift cue).
    https://earth.com/animal-encyclopedia/Chordata/Acanthuridae/paracanthurus-hepatus/en/ (`[S]`)
13. American Samoa surgeonfish and wrasse spawning study (acanthurid spawning times, pair and group spawning).
    https://scholarspace.manoa.hawaii.edu/items/ccc44513-bd64-4d62-a064-cce0e6443585 (`[A]`, search snippet)
14. subdiversion.es, general fish page (hatch 26 h, about 2.5 months larval period). Low authority.
    https://www.subdiversion.es/?p=6092 (`[S]` weak)
15. CORAL Magazine listing, Biota Group captive-bred Pacific blue tang production announcement (Jan 2025).
    https://coralmagazine.com/tag/marine-ornamental-aquaculture (`[S]`)
16. Retailer listing, Biota captive-bred blue tang (adult size about 8 in).
    Search result only; not fetched. (`[S]` weak)

**Not used or unreadable:** the UF Rising Tide page (empty on fetch, snippet used); DiMaggio PDF (binary).
