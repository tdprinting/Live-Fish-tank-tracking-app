# Fish AI research: ocellaris clownfish, juvenile blue tang, pajama cardinalfish

Scope: movement, space use, social, foraging, daily routine, startle/personality. Adds to 10_fish_behavior_detail.md,
15_master_livestock_research.md and docs/research/video/fish_{ocellaris_clownfish,blue_tang,pajama_cardinalfish}.md.
Tags: **[SOURCED]** number or statement read in a source (abstract or summary); **[M]** secondary: seen only in a
search snippet or press summary, full text not read; **[ANALOGY: species]** from a related species; **[OBSERVATION: hobby]**
aquarium report, not peer-reviewed; **[L]** my design suggestion, not from a source.

Paywalled or redirected items (Fulton 2007, Buston 2003, Hussey & LaPlante 2024, Marnane & Bellwood full text,
Frontiers Mar Sci 2023 feeding paper) were read only as abstracts or search snippets. Numbers from them are tagged [M].

## Cross-species facts that change the model

1. **Sustained speed of ordinary reef fish, 2.0-4.5 TL/s** [SOURCED: Fulton, Johansen & Steffensen 2013, PLoS ONE 8(1):e54033,
   https://pmc.ncbi.nlm.nih.gov/articles/PMC3541231, citing "other fishes of similar body size"]. The wing-fin bluelined
   wrasse reached 7.7 TL/s optimum, "more than 70% faster". Implication: 6-10 BL/s is a 0.3-1 s dart, not a cruise. Sustained
   travel should cap near 2-4 BL/s for these three.
2. **Field speed is a fraction of lab maximum:** pectoral (labriform) swimmers about 70% of lab max, body-caudal about 50%
   [M: Fulton 2007, Coral Reefs, doi 10.1007/s00338-007-0195-0, 117 species; abstract via search only]. Use 0.7 x lab max for
   sustained cruise of the pectoral fish here.
3. **Gait switch:** bluegill switched from pectoral to caudal propulsion above about 12 cm/s (about 1 BL/s) [M: Gibb et al. 1994,
   J Exp Biol; sunfish, not reef fish]. Fin beat frequency rose from 1.2 to 2.1 Hz as speed went 0.3 to 1.0 TL/s [M, same].
   Implication: below about 1 BL/s pure pectoral rowing; above it, add tail.
4. **Hovering is expensive, about 2x resting** metabolic rate, range 158-351 mg O2/kg/h across 13 near-neutrally buoyant species
   [SOURCED: Di Santo / Akanyeti et al. 2026, PNAS, summary at https://www.aber.ac.uk/en/news/archive/2026/02/title-286028-en.html].
   Deep bodies and pectorals set further back hover more cheaply, with less fin and tail correction [SOURCED same].
   Pajama cardinalfish (body depth 0.50-0.56 SL) and clowns (deep, short) should hover with few corrections; long thin fish should not.
5. **Flow-related:** the tang's habitat is "clear, current-swept terraces of seaward reefs" [SOURCED: FishBase,
   https://www.fishbase.se/summary/Paracanthurus-hepatus.html]. Flow-riding behavior itself was not found in any source.
6. **Distractor warning:** a popular article claimed reef fish 10 cm long "cruise all day at 3.6 km/h" [M: Australian Geographic
   2013, https://www.australiangeographic.com.au/nature-wildlife/water/2013/11/tiny-reef-fish-is-surprise-speedster/].
   3.6 km/h is 1 m/s, which is 10 BL/s for a 10 cm fish. That conflicts with item 1. **Do not use it.**
7. **Personality is consistent:** juvenile Ambon damselfish (*Pomacentrus amboinensis*) showed boldness and activity traits
   that were moderately to highly repeatable over minutes, hours and 3 days in lab and field. Aggression was the exception
   [SOURCED: PeerJ 961, https://peerj.com/articles/961]. Implication: give each fish a fixed boldness/activity draw at spawn
   with small noise; do not reroll per encounter.

---

## 1. Ocellaris clownfish (*Amphiprion ocellaris*)

Facts:
- **Diurnal.** Juveniles were most active in the morning, less at midday, and "barely move at night" [M: Nguyen et al. 2019,
  J Fish Biol 94(6):937-947, DOI 10.1111/jfb.13910; https://bora.uib.no/bora-xmlui/handle/1956/23253].
  Use dawn peak and midday dip; night is near-still hovering, not a sleep-to-zero state.
- **Host use lowers roaming.** Juveniles housed with their natural host traveled shorter distances than those with unnatural hosts,
  and those with no host traveled most [M: same]. Captive clowns with no host should roam more, not less.
- **Post-settlement fish are sedentary**; interhost moves are rare in *A. ocellaris* [M: anemone-mobility study, cited in
  search summary]. A. frenatus adults moved between hosts after a bleaching die-off: about a quarter of adults, mean 42 m in 3 weeks
  [M, same search]. Implication: with the host in place, leash to the host; move only if the host is removed.
- **Host choice:** newly settled juveniles show little preference; post-settlement juveniles associate with the most common wild
  host within days [M: Nguyen et al. 2019]. Soft coral here is a substitute host, not a native one [L, species-level].
- **Dominance and social ladder.** The largest is the breeding female, the next the breeding male; if the female is lost the male
  changes sex and a non-breeder takes the male role [SOURCED Florida Museum species profile, cited in 10_fish_behavior_detail.md].
  In *A. percula*, subordinates adjust growth to stay a fixed size below the fish above them; removing the top female caused the
  survivors to grow and move up [M: Buston 2003, Nature 424:145-146, https://www.nature.com/articles/424145a].
  Press summary: a subordinate stays about 80% of the dominant's size [M; press, not verified in the paper].
- **Dominant does not always hold the host more.** In captive groups at one host, high-ranked fish used the anemone less than
  low-ranked fish; mid-ranked fish did not differ [M: Bristol student study 2026, via search summary].
- **Aggression vs maintenance trade-off.** In three captive fish at one host, aggressive and maintenance behavior were strongly
  negatively correlated in every fish. The melanistic fish showed a stronger trade-off than orange fish; orange fish did more
  "leave" moves [SOURCED abstract: Hussey & LaPlante 2024, BIOS 95(1):36-43, https://doi.org/10.1893/BIOS-D-22-00004].
  Implication: a fish picks either to attack or to tend the host (fan, clean), not both at once. Rates were not in the abstract.
- **Lateral head-shaking (quiver)** is a subordinate signal in *A. frenatus*; no ocellaris rate [SOURCED-analogy: Colleye &
  Parmentier 2012, https://pmc.ncbi.nlm.nih.gov/articles/PMC3492317/, already in the video notes].
- **Excursions.** Clownfish vary in how far they range: "adventurous" fish cover long distances at low cost; "sedentary" ones stay
  in the host [M: Mercader & Frédérich 2025, summary at https://ecomagazine.com/news/fisheries-aquaculture/clownfish-are-not-as-homebound-as-we-thought/].
  An anemonefish's time at each distance from the anemone depends on tide, sex, status and eggs, not a fixed radius
  [M: A. mccullochi study, cited in search summary]. No metre radius for *A. ocellaris* was found.
- **Planktivorous feeding is risky.** Away from the host, a clown trades host protection for plankton; that cost drives excursions
  [M: same search summary].

Gaps: no numeric excursion radius, strike rate, cruise speed, turn rate, pause duration or quiver rate found for this species.

### AI parameters: ocellaris clownfish

| Parameter | Value / range | Unit | Evidence |
|---|---|---|---|
| Sustained cruise speed (pectoral) | 0.5-1.0 | BL/s | [L] anchored to 2-4.5 TL/s sustained [SOURCED] |
| Pectoral-to-tail gait switch | about 1.0 | BL/s | [M] bluegill (Gibb 1994) |
| Pectoral beat rate, hover/slow | 4-6 | Hz | [L] (kept from existing notes) |
| Burst dart | 6-8 for 0.3 s | BL/s | [L] short dart, above sustained [SOURCED limit] |
| Activity by hour | dawn peak, midday dip, near-still night | pattern | [M] Nguyen 2019 |
| Host leash (native host) | 1-3 | BL | [L] |
| Host leash (no host, roams) | 4-6 | BL | [ANALOGY] juveniles without host travel farther [M] |
| Interhost move | rare; only if host removed | event | [M] sedentary post-settlement |
| Chase vs tend | mutually exclusive; sample one per bout | state | [SOURCED] Hussey & LaPlante 2024 (3 fish) |
| Dominant time at host | lower than subordinates | relative | [M] Bristol 2026 student study |
| Growth lock to dominant | keep size about 80% of the one above | ratio | [M] Buston 2003 (press figure for 80%) |
| Quiver (subordinate lateral shake) | no rate | per min | not found; [L] 2-4 per min while yielding |
| Hover pitch | -5 to -15 | deg (nose down) | [L] (kept) |
| Hover cost | about 2x rest | ratio | [SOURCED] PNAS 2026 (13 species, not clown) |

---

## 2. Juvenile blue tang (*Paracanthurus hepatus*)

Facts:
- **Habitat and depth.** Clear, current-swept terraces of seaward reefs [SOURCED FishBase]. Listed depth 2-40 m, usually 10-40 m
  [SOURCED FishBase]. Scale note: "1-2 m above the bottom" is absolute height; in a 16 in (41 cm) tank that is not a usable
  metre figure. Scale it to the tank: mid-column, not floor [L].
- **Schooling.** Adults occur in "loose aggregations 1 or 2 metres above the bottom" [SOURCED FishBase]. A single juvenile
  should have weak cohesion only [L].
- **Refuge.** Juveniles and subadults stay near isolated coral heads and, when alarmed, "hide themselves tightly among the
  branches" [SOURCED FishBase]. This is a refuge behavior, separate from the rock-hiding and play-dead behavior that
  another team covers; here it is only swimming to a branching-coral refuge.
- **Diet and feeding position.** Zooplankton, occasionally algae [SOURCED FishBase]. Juveniles are described as planktivores and
  adults as omnivores [M: search summary]. Juveniles pick plankton in the water column, so the sim should not be a surface grazer
  while small.
- **Speed.** Sustained cruise should sit in the 2-4.5 TL/s band for "similar body size" fish, but grazing and zooplankton picking
  are slow: pick a low cruise (about 1-2 BL/s) with fast bursts (6-8 BL/s, under 1 s) [L].
- **Habitat fit.** A pectoral swimmer, which Fulton's field-to-lab ratio puts at about 70% of lab max [M] when it cruises.

Gaps: no cruise, burst, turn rate or flow-riding figure for *P. hepatus*. Do not use the 8-10 BL/s burst in the existing file
as cruise. The existing file's "plays dead" rule has contradicting evidence in its own video notes; this file does not
rely on it.

### AI parameters: juvenile blue tang

| Parameter | Value / range | Unit | Evidence |
|---|---|---|---|
| Habitat current | flow-exposed sites | qualitative | [SOURCED] FishBase |
| Adult depth range | 2-40 (usually 10-40) | m | [SOURCED] FishBase (wild, not tank) |
| Preferred zone in tank | mid-column, away from floor | qualitative | [L] scaled from "1-2 m above bottom" |
| Cohesion (lone juvenile) | weak, 2-4 | BL | [L] adult loose aggregations [SOURCED] |
| Refuge | nearest branching coral head | event | [SOURCED] FishBase (alarm hide) |
| Sustained cruise | 1.0-2.0 | BL/s | [L] anchored to 2-4.5 TL/s upper bound |
| Burst | 6-8 for under 1 s | BL/s | [L] |
| Pectoral beat rate | 3-5 | Hz | [L] (existing file) |
| Diet source | water column zooplankton, algae occasionally | ratio | [SOURCED] FishBase |
| Diet shift | planktivore juvenile, omnivore adult | stage | [M] |

---

## 3. Pajama cardinalfish (*Sphaeramia nematoptera*), single fish

Facts:
- **Diel split.** Across seven apogonid species at One Tree Reef, fish shared restricted resting habitats by day and spread into a
  range of habitats at night, horizontally and vertically [SOURCED: Marnane & Bellwood 2002, MEPS 231:261-268,
  https://int-res.com/abstracts/meps/v231/p261-268]. Dawn-collected fish had far more stomach contents than dusk-collected fish, so
  feeding is nocturnal [SOURCED same]. The study does not name *S. nematoptera*; apply to this species as [ANALOGY].
- **Shelter.** Wild individuals shelter by day under ledges or in crevices, and in branching coral [M: search summary; FishBase
  via 10_fish_behavior_detail.md].
- **Groups.** Hobbyist sources say groups of 5 or more hover near caves or branching coral by day; at night they split and hunt alone
  [OBSERVATION: hobby]. This fish is alone in the tank, so the group rules do not apply; the fish hovers alone near cover.
- **Captive daytime feeding.** Captive fish often become diurnal once food regularly arrives in daylight [OBSERVATION: hobby]. This
  is the only source for daytime feeding; treat it as an optional rule, not a default.
- **Diet.** Small crustaceans, hunted at night in the wild [M, same search summary].
- **Hover energy.** Pajama cardinals are deep-bodied and compressed (0.50-0.56 SL) (existing file). The PNAS 2026 result implies
  they should hover cheaply, with little fin and tail correction [ANALOGY from item 4 above].

Gaps: no numeric shade preference, dusk emergence time, cruise speed, or hover pitch distribution found. The existing
file's values (cruise 0.3-0.7 BL/s, burst 5 BL/s, shelter within 2 BL) remain [L].

### AI parameters: pajama cardinalfish

| Parameter | Value / range | Unit | Evidence |
|---|---|---|---|
| Day behavior | rest near cover, low activity | state | [SOURCED-analogy] Marnane & Bellwood 2002 |
| Night behavior | leave cover, forage over the tank | state | [SOURCED-analogy] same |
| Dusk emergence ramp | 20-60 | min | [L] (no source) |
| Cover distance (alone) | 1-2 | BL | [L] |
| Cruise speed | 0.3-0.7 | BL/s | [L] (existing file) |
| Burst into cover | 4-5 | BL/s, under 0.5 s | [L] (existing file) |
| Hover pitch | level, within 10 | deg | [L] (existing file) |
| Hover energy | low; deep body | qualitative | [ANALOGY] PNAS 2026 |
| Daytime feeding | optional, only if food arrives by day | rule | [OBSERVATION: hobby] |
| Night prey | small crustaceans, drifting over bottom | target | [M] |

---

## Startle, curiosity, personality (all three)

- **Flight initiation.** Economic escape theory predicts flight when predation risk equals escape cost; more complex habitat
  lets fish approach closer [M: stegastes study cited in search snippet, Blumstein-lab candidate, not verified]. A barber surgeonfish
  study reported FID differences by size but no clear size trend and no link to shelter distance [M, same search]. No numeric FID
  for these three species was found.
- **Re-emergence.** An anemone study found hiding time depends on how many fish return and how fast they return [SOURCED: Lim et
  al. 2016, Royal Soc Open Sci 3:160169, https://pmc.ncbi.nlm.nih.gov/articles/PMC5108943/]. That is about the anemone, not the
  fish. For fish, [ANALOGY]: a fish near a conspecific returns sooner; a solo fish waits longer. No minutes value was given.
- **Personality.** See cross-species item 7. Assign boldness, activity and aggression per fish at spawn.
- **Tank observer.** No peer-reviewed source on glass-curiosity for these species was found [gap]. Treat as [L].

## Priority changes for the sim (what this changes)

1. Cap sustained cruise at about 2 BL/s for all three; keep 6-8 BL/s only for darts under 1 s. (Fulton 2013 item 1.)
2. Hover costs about 2x rest and is cheaper for deep bodies; give hover low fin use and let cardinal and clown hover longer.
3. Clown: when the host is present, leash to it and do not roam. Without a host, roam farther. Pair: the dominant is not
   always at the host. Chase and tend are exclusive per bout.
4. Tang: forage in the water column, not on the rock. Only a branching-coral refuge when startled. Scale depth to the tank.
5. Cardinal alone: cover-bound and low-activity by day, dusk ramp to night foraging, no group rules.
6. Give each fish a persistent personality draw at spawn with small noise.

## Source list

- Fulton, Johansen & Steffensen 2013, PLoS ONE 8(1):e54033: https://pmc.ncbi.nlm.nih.gov/articles/PMC3541231
- Fulton et al. 2007, Coral Reefs (abstract only, paywall): https://link.springer.com/10.1007/s00338-007-0195-0
- Gibb et al. 1994, J Exp Biol (bluegill, via search): https://sites.harvard.edu/glauder/files/2022/03/Gibb.et_.alPect.fin1994.pdf
- Di Santo / Akanyeti et al. 2026, PNAS (news summary): https://www.aber.ac.uk/en/news/archive/2026/02/title-286028-en.html
- Nguyen et al. 2019, J Fish Biol 94(6):937-947: https://bora.uib.no/bora-xmlui/handle/1956/23253
- Hussey & LaPlante 2024, BIOS 95(1):36-43: https://doi.org/10.1893/BIOS-D-22-00004
- Buston 2003, Nature 424:145-146: https://www.nature.com/articles/424145a
- Mercader & Frédérich 2025 (press summary): https://ecomagazine.com/news/fisheries-aquaculture/clownfish-are-not-as-homebound-as-we-thought/
- Marnane & Bellwood 2002, MEPS 231:261-268: https://int-res.com/abstracts/meps/v231/p261-268
- Lim, De La Guerra & Blumstein 2016, Royal Soc Open Sci 3:160169: https://pmc.ncbi.nlm.nih.gov/articles/PMC5108943/
- PeerJ 961 (Ambon damselfish personality): https://peerj.com/articles/961
- FishBase, *P. hepatus*: https://www.fishbase.se/summary/Paracanthurus-hepatus.html
- Colleye & Parmentier 2012: https://pmc.ncbi.nlm.nih.gov/articles/PMC3492317/
- Australian Geographic 2013 (flagged, not used): https://www.australiangeographic.com.au/nature-wildlife/water/2013/11/tiny-reef-fish-is-surprise-speedster/

Not read in full (flagged in text): Fulton 2007; Gibb 1994; Buston 2003; Hussey & LaPlante 2024; Marnane & Bellwood full text; Frontiers Mar Sci 10.3389/fmars.2023.1327581 (PDF returned binary only; strike-rate values unavailable); the Stegastes FID study and the barber surgeonfish FID study (search snippets only).
