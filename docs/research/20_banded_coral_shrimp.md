# Banded coral shrimp (Stenopus hispidus): behaviour and locomotion for the 3D reef sim

Scope: walking, foraging, fish/shrimp interactions, antennae and cleaning, daily rhythm, territory and dens.
Sources: peer-reviewed or reference literature (Limbaugh et al. 1961; Colin 1978; Humann 1992; Esaka et al. 2016 *Pacific Science*), Animal Diversity Web (ADW), Wikipedia, and retailer care guides where noted. Retailer claims are marked RETAILER. Anything not found in a source is marked DESIGN ESTIMATE.

Not used: YouTube (blocked; not accessed). Wikimedia Commons was not fetched, since no image was needed for these numbers.

---

## 0. Body facts (anatomy used by the sim)

- Size: "reaches a total length of 60 mm" (Wikipedia); "grows up to 6.2 cm" (ADW, citing Williams 1984). Other sources give about 5 cm; RETAILER pages say up to 10 cm. Use **5 cm** (design value).
- Colour: transparent ground colour, with red-and-white bands on the carapace, abdomen and the large third pereiopod (Wikipedia).
- Body spines: abdomen, carapace and third pereiopods are spiny (Naturalis Caribbean Diving, via search).
- Legs: pereiopods 1–3 are chelate. Pereiopod 3 is by far the largest and carries the big claw. Pereiopods 4–5 are slender ambulatory legs. Walking legs are translucent (Humann 1992). Source: Oxford chapter on Stenopodidea (search snippet).
- Antennae: two pairs of long, white, hair-like antennae, longer than the body (Limbaugh et al. 1961; Humann 1992). The first pair is uniramous.
- Mouthparts: three pairs of maxillipeds (biramous), which together with the chelipeds clean and pick food (crustacea.net; cephalopodpage).

---

## 1. Walking on rock

**Walking legs.** Locomotion on the substrate uses pereiopods 4 and 5 (the slender ambulatory pair). The Oxford chapter describes the Stenopodidea as having the first three pereiopods chelate and the fourth and fifth as the ambulatory pair. Sources on the walking function of pereiopods 4–5 are in the chapter; I did not get a direct gait study for *S. hispidus*.

**Chelipeds are not for walking.** Pereiopod 3 (the big cheliped) is used for feeding, cleaning and defence, not for locomotion. Glossary definitions and the Stenopodidea morphology agree (search snippets, general crustacean terms).

**Body posture.** The pleomere pleura overlap the next segment, which the Oxford chapter describes as reptant (lobster-like), not caridean. So the body is held low and crawls, unlike a swimming caridean. (Oxford chapter, search snippet.)

**Gait.** No source gives gait pattern, stride frequency or leg-phase data for *S. hispidus*. DESIGN ESTIMATE: alternating tripod-like walking of pereiopods 4 and 5 on each side, with a slow pace.

**Speed.** No measured speed found. DESIGN ESTIMATE: crawl 1–2 cm/s on a flat rock, bursts of about 5–8 cm/s when fleeing. Walking is slow; hobbyist accounts describe it as "scampering" in search of food, but no measurement is given (RETAILER / hobbyist, anecdotal).

**Inverted hanging.** Sources repeatedly describe the shrimp hanging under overhangs and inside crevices, with only the antennae out of the hole (search snippets citing hobbyist and retailer pages; Top Shelf care guide: "they spend much of the day tucked away"). It hangs upside-down under ledges. DESIGN ESTIMATE: the sim should allow the shrimp to attach to an overhang surface with the legs up, with the body held inverted.

**Leg use when at rest.** The shrimp often sits at the entrance of its shelter with the large claws held open and extended ("boxing" posture; RETAILER, Top Shelf: "perched at the entrance of their chosen shelter with their large claws extended").

---

## 2. Foraging

**Diet.** ADW lists aquatic worms, small crustaceans, other marine invertebrates and zooplankton as food. Its main ecological role is cleaning: it eats parasites, injured tissue and food particles from cooperating fish (Limbaugh et al. 1961; ADW). Scavenging on the reef and in the tank is common (RETAILER: "may scavenge for leftover food"; "they are more predatory than cleaner shrimp and will actively seize passing food items").

**Using the chelipeds for food.** The large third cheliped (pereiopod 3) and the small chelae of pereiopods 1–2 probe crevices and bring food to the maxillipeds, which pass it to the mouth. Sources describe the mechanics only in general: "large chelipeds and three sets of smaller claws called maxillipeds to scrape and pick off food and parasites" (cephalopodpage). Captive-feeding notes say they search the tank floor and crevices and need food placed close to them (hobbyist, anecdotal).

**Stroke rates.** None found. DESIGN ESTIMATE: 1–2 probing strokes per second when feeding on a crevice.

**Carry to mouth.** Food is passed from the claw to the maxillipeds and then to the mouth. No timing data.

**Scavenging vs cleaning.** Both. Cleaning is the primary ecological role; scavenging and food capture (grabbing passing food) happen at night and in tanks (RETAILER).

---

## 3. Aggression and defence toward fish and shrimp

**Fish.** Most sources, including the peer-reviewed cleaning literature, describe the species as generally tolerant of fish. ADW says it can "enter the mouth and gill cavities" of host fish without being eaten (Limbaugh et al. 1961). RETAILER: "generally peaceful toward fish but can be aggressive toward other shrimp."

**Grabbing small fish.** RETAILER (Top Shelf) says it "may prey on very small fish or slow, bottom-resting fish at night, particularly in cramped systems," and "slow-moving, very small, or weakened fish that rest near the shrimp's cave may be grabbed, especially at night." Top Shelf also says it "does not actively hunt healthy fish." Only retailer sources back this up. Treat it as an opportunistic, low-probability event. DESIGN ESTIMATE: grab only when a fish of body length under about 2 cm is within 3 cm of the shrimp's claw and the shrimp is in the night phase.

**Threat display.** The raised, open-claw "boxing" posture is described as a natural defensive display (Top Shelf; hobbyist sources). ADW says the claws can break off when the animal feels threatened (Colin 1978). No measured display frequency or angle. DESIGN ESTIMATE: when a fish approaches within about 4–6 cm of the shrimp's den entrance, the shrimp raises pereiopod 3 to a wide stance (about 60–90° open) for 2–5 s; it lunges (grabs at the fish) on about 10–20% of approaches to a small fish, and never at a large active fish.

**Shrimp.** Aggression toward other shrimp is the main documented case. Lone individuals are normal; pairs are monogamous and territorial, and fight outsiders (RETAILER, Wikipedia: "monogamous pair works together to drive away intruders"). Adults can recognise individuals of their own species, probably by chemical signals (Wikipedia). DESIGN ESTIMATE: same-species encounters within 0.5 m of a den trigger a claw display and then a lunge within about 2 s.

**Claw autotomy.** The claws can break off when the animal feels threatened (Colin 1978, ADW). They regenerate, often leaving unequal sizes (Colin 1978). DESIGN ESTIMATE: a lost cheliped is smaller and regrows over several moults; the sim should keep a regrowing claw of reduced size.

---

## 4. Antennae, cleaning and the advertisement

**Advertisement.** Adults perch near the entrance of their shelter and wave the long white antennae slowly to attract passing fish (Humann 1992; Wikipedia; ADW). This is the cleaning signal.

**Dance.** Wikipedia says it performs a dance near its home, whipping the antenna side to side as it moves, first observed in the lab. DESIGN ESTIMATE: an antenna sweep of about 1 Hz side-to-side when a fish is in view.

**Cleaning.** The shrimp uses its three pairs of claws to remove parasites, fungi and damaged tissue from fish (Wikipedia). Known cleaned species: morays, tangs, grunts and groupers (Limbaugh et al. 1961). The fish holds still with the mouth or gill cavity open, and the shrimp enters the mouth and gill cavities. No cleaning-bout duration found. DESIGN ESTIMATE: a cleaning bout of 10–30 s per client fish.

**Antennae in general.** Used for scanning the surroundings and for chemical contact (Wikipedia; search snippet).

---

## 5. Daily rhythm, territory and dens

**Activity.** Esaka et al. 2016, *Pacific Science* 70(2): 191–200 (abstract only; full text not accessed): under a 12 h light/12 h dark cycle, "shrimp under LD were active during scotophase and inactive during photophase." So the shrimp is **nocturnal**. Under constant darkness the rhythm is weak. Under weak irradiance, the day–night difference shrinks and the shrimp is active in daylight. Blue or green LED light gives day-inactive, night-active rhythms; red light does not. The authors suggest the weak clock lets it clean in dim light.

**Cleaning vs night rhythm.** Cleaning is done in the day in dim or twilight conditions, and the shrimp is otherwise night-active (Esaka et al. 2016).

**Territory.** Wikipedia: a territory of 1–2 m in diameter. ADW: "has never been observed to move a distance greater than half a meter unless disturbed" (Limbaugh et al. 1961). Adults "remain in the same area for days, months or even years" (Colin 1978). The two sources differ; use a radius of 0.5 m as the normal range and 1 m when disturbed (design value).

**Dens.** Shrimp live in coral ledges, rock crevices, under overhangs, in Thalassia mats, and in discarded objects (ADW; Limbaugh et al. 1961). RETAILER: "establish a permanent home within a cave, overhang, or rock crevice."

**Moulting.** ADW (Debelius and Baensch 1997): adults moult every 3–8 weeks depending on diet and temperature. RETAILER: during and just after moulting they are soft and "more likely to hide"; "frequently mistaken for deceased animals." DESIGN ESTIMATE: a moult every 3–8 weeks, with 1–3 days of hiding in a den.

**Sex and pairs.** Monogamous pairs, females larger than males (Wikipedia; ADW). The pair shares one den.

---

## Sim parameters

Every line gives a value and its source. "Source" means a cited reference above; "DESIGN ESTIMATE" means not measured.

| Parameter | Value | Tag |
|---|---|---|
| Body length | 5 cm (range 6 cm max) | Source: Williams 1984 via ADW, Wikipedia 60 mm |
| Walking legs | pereiopods 4 and 5 | Source: Oxford chapter (Stenopodidea) |
| Big claw | pereiopod 3, spiny, banded | Source: Wikipedia, Oxford chapter |
| Walking speed | 1–2 cm/s | DESIGN ESTIMATE |
| Flee burst | 5–8 cm/s, under 1 s | DESIGN ESTIMATE |
| Gait | slow alternating (pereiopods 4 and 5) | DESIGN ESTIMATE |
| Rest posture | at the den entrance, claws out, antennae out | Source: Top Shelf (RETAILER) |
| Overhang hanging | inverted, legs up, antennae out of hole | Source: hobbyist/RETAILER; ADW |
| Probing rate | 1–2 strokes/s on crevice food | DESIGN ESTIMATE |
| Feeding | claw to maxillipeds to mouth | Source: cephalopodpage (general description) |
| Territory radius | 0.5 m normal, 1 m when disturbed | Source: ADW (0.5 m), Wikipedia (1–2 m diameter); radii DESIGN ESTIMATE |
| Den | ledges, crevices, overhangs | Source: ADW, Limbaugh 1961 |
| Activity | nocturnal (dark-phase active, light-phase inactive) | Source: Esaka et al. 2016 |
| Dim-light activity | active in dim light and twilight; weak clock | Source: Esaka et al. 2016 |
| Dusk/night feeding | active night feeding and scavenging | Source: RETAILER (Top Shelf) and Esaka 2016 |
| Antenna advertisement | slow wave to attract fish, from den mouth | Source: Humann 1992; Wikipedia |
| Antenna dance | about 1 Hz side-to-side sweep while a fish is in view | DESIGN ESTIMATE (Wikipedia describes the dance, not the rate) |
| Cleaning bout | 10–30 s per client fish | DESIGN ESTIMATE |
| Cleaned species | morays, tangs, grunts, groupers | Source: Limbaugh et al. 1961 |
| Fish approach trigger | fish within 4–6 cm of den entrance | DESIGN ESTIMATE |
| Threat display | pereiopod 3 raised 60–90° for 2–5 s | DESIGN ESTIMATE (Top Shelf describes posture only) |
| Lunge at small fish | 10–20% of approaches, fish under 2 cm, night | DESIGN ESTIMATE (predation claim: RETAILER only) |
| Lunge at large fish | 0% | RETAILER: "typically ignore most ... larger and active" |
| Same-species trigger | encounter within 0.5 m of den → display, lunge within 2 s | DESIGN ESTIMATE (aggression: RETAILER) |
| Claw autotomy | can break when threatened; regrows to smaller size | Source: Colin 1978 via ADW |
| Moult interval | 3–8 weeks; 1–3 days in den after | Source: Debelius and Baensch 1997 via ADW; RETAILER for hiding |
| Pairing | monogamous; shared den; females larger | Source: Wikipedia, ADW |

---

## Gaps still open

- No measured walking speed, gait or leg-phase data for *S. hispidus*.
- No observational study of threat display toward fish or of lunges at fish.
- No measured stroke rate for food handling.
- Full text of Esaka et al. 2016 (sample sizes, activity percentages) not accessed.
- The 1–2 m vs 0.5 m territory difference needs a primary source.
