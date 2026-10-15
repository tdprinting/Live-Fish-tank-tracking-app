# 40. Bubble-tip anemone (Entacmaea quadricolor, rainbow / RBTA morph)

Research for the ReefLive simulation. Every number carries a citation key (see References). Where a number
comes from a retailer, forum, or secondhand snippet, it is marked **[weak]**. Where no source was found, it is
marked **[assumption]** and the sim value is a tuning choice, not a measurement.

Source quality note: the peer-reviewed base for this species is small. The strongest data are Scott et al. 2014
(propagation, recovery), Hill & Scott 2012 and Hill et al. 2014 (heat and light), Pryor et al. 2021 (warming),
Roopin 2007 (ammonia), and Scott & Harrison 2008/2009 (spawning and settlement). Movement, feeding kinetics, the
daily deflation cycle, predation by tank mates, and the bulb-tip mechanism have no controlled source found.

---

## 1. Morphology

### 1.1 Size
- Oral disc diameter (ODD) of wild-collected adults used in propagation work: **98-235 mm** (Scott 2014, Exp. 1
  109-235 mm; Exp. 2 98-215 mm). Pedal disc diameter (PDD) of the same animals: **45-108 mm**. Wet weight 32-274 g.
- Maximum diameter quoted as **up to about 30 cm** (Wikipedia, no primary citation). CORAL's care sheet gives
  **12 in (30 cm)** maximum and a **30 gal (114 L)** minimum tank (CORAL 2019).
- Retail and forum sources repeat "about a foot across" and "30 gallon minimum" [weak].

### 1.2 Tentacles
- Young individuals: **about 20 tentacles**, count rises with age (Wikipedia, photo caption citing Scott & Harrison
  2008). No adult tentacle count found in a primary source. **[assumption]** for adult count (see Section 10).
- Tentacle length: no measured values found for the tentacles at rest or extended. Retail sources describe
  "sweeper" tentacles that extend to catch drifting food, which is the only length cue available. **[weak]**

### 1.3 Bulb (bubble) tip vs stringy tip
- Young animals show the bulbous tips the species is named for. Large adults have streaming or stringy tentacles
  (Wikipedia; no primary citation).
- Bubbles form under strong light according to hobby sources, but the mechanism is not established. A Delbeek
  article in Advanced Aquarist (2002, "The Effects of Lighting on Bulb-tip Development") proposed light intensity as
  a factor. Its text was not retrieved in this research. A Reef Central survey post says there is "not much data"
  on this and that the effort is preliminary (Reef Central, undated). **[uncertain]**
- Hobbyist anecdotes disagree. One report says raising lights from 70% to 85% restored bubbles in some cases but
  "doesn't work in every case." Another says a large animal under about 500 PAR lost its bubbles with no change in
  light. **[weak]**
- Feeding-linked extension: hungry animals extend "sweeper" tentacles and the bulbs retract at rest (retail
  sources). **[weak]**
- Stress and flow causes of stringy tips: **not sourced**. Treat as unknown. The sim should not hard-wire a cause.

### 1.4 Color morphs
- Standard green, rose, orange, and red forms are listed, with green and rose most common in aquaria (Wikipedia,
  no primary citation). "Rainbow" and "RBTA" are trade names for color selections of this species. **[weak]**
- The "four-colored" name refers to color variation within the species, and color varies widely between
  individuals (Wikipedia; reefs and retail sources). **[weak]**
- No source found for the green fluorescent oral disc or pale mouth. Those are standard hobby descriptions that
  were not verified. **[weak]**
- Color is produced by host pigments and symbiont density. Bleaching lowers symbiont density and removes color
  (Section 8).

---

## 2. Movement

### 2.1 Pedal glide speed
- **No measured pedal-glide rate found** in cm/hour or cm/day for E. quadricolor. The only movement data found is
  qualitative: mature individuals mostly stay attached but can move and detach quickly when disturbed (taxonomy
  source in search results, not verified). **[no data]**
- Escape from burial: the animal inflates the pedal disc and column alternately to emerge from sediment. This is
  an escape response, not a steady glide (taxonomy source). **[qualitative]**
- Hobbyist reports: an animal moved around the tank for about **one week** before settling in a rock crevice
  (forum). **[weak]**

### 2.2 Settling time and wandering
- Retail sources: anemones "walk" until they find preferred flow and light. Reef Chasers describes them as going for
  walks. **[weak]**
- Hobbyist reports: a few days to a week of wandering after introduction. **[weak]**

### 2.3 Triggers for wandering
- Light too weak or too strong: retail sources list 150-350 PAR; Reef Chasers recommends moderate-high 200-250 PAR
  and says blasting at 350 PAR "hasn't always gotten the best results" (Reef Chasers). **[weak]**
- Flow: moderate flow recommended; animals get pulled into unguarded powerheads (Reef Chasers). **[weak]**
- Water quality: a Reef2Reef poster links deflation to unhappiness and says watch parameters in new tanks.
  **[weak]**
- Mechanism-level evidence for any of these triggers: **none found**.

### 2.4 Substrate and light preference
- Foot hidden in a crevice, oral disc toward the light: retail sources recommend a rock crevice where the foot can
  grip (Reef Chasers; Vivid guide). **[weak]**
- Field: large adults are usually in deeper, dimmer water, while smaller young are shallow in bright sun
  (Wikipedia, no citation). **[weak]**
- Field collection depth in the propagation study: **12-18 m** (Scott 2014, both experiments). Scott 2014 holding
  tanks were outdoors under shade cloth, and light did not exceed **400 umol photons m-2 s-1** (Scott 2014).
  This is the only measured light value in the set. It is not an aquarium PAR target, but it is a usable ceiling.

### 2.5 Risks
- Powerhead intakes: suction and wandering into pumps and overflows are repeatedly reported (Reef Chasers; Vivid).
  **[weak]**
- Detachment: described only under disturbance, not as a regular behavior (taxonomy source in search results,
  not verified). **[qualitative]**

---

## 3. Daily rhythms

- No controlled source found for daily expansion or contraction timing in this species.
- Hobbyist consensus (Reef2Reef, Reef Central): many animals **fully deflate** after feeding or during the dark or
  moonlight phase and **reinflate over several hours** when full light returns. One poster says the deflation is
  to wring out waste, and another says it is most often linked to post-feed deflation. **[weak, anecdotal]**
- Deflation is treated as normal if occasional. Daily full deflation, a gaping mouth, or deflation lasting several
  days are treated as warning signs (Reef2Reef, Reef Central). **[weak]**

Sim use: a nightly partial contraction and a post-feed deflation of **hours** duration. Exact durations are
**[assumption]** (Section 10).

---

## 4. Feeding

### 4.1 Capture
- Hungry animals extend sweeper tentacles to catch drifting food. Tentacles retract and the bulbs return
  (retail sources). **[weak]**
- Stinging cells (nematocysts) stun prey; one consumer guide describes a toxin on the tentacles (retail). **[weak]**
- Field diet: small invertebrates and zooplankton (museum fact sheet, cited via search). **[weak]**
- Autotrophic input: most energy from zooxanthellae photosynthesis (Wikipedia, Aquarium of the Pacific). **[weak]**
- Clownfish feeding: anemonefish feed the anemone with their excretion and with food scraps (Roopin 2007;
  Wikipedia). See Section 7.

### 4.2 Capture and transfer times
- **No source found** for capture success rate, handling time, or the time from tentacle capture to mouth
  ingestion. **[no data]**
- Sim value must be an assumption. See Section 10.

### 4.3 Meal size and frequency
- Scott et al. 2014 fed prawn **weekly from day 7** (Exp. 1) and fed half of each treatment **weekly to satiation**
  (Exp. 2). Fed anemones grew more ODD than unfed (chi-square = 9.5, p = 0.002) and lost less wet weight
  (chi-square = 5.3, p = 0.022) (Scott 2014). This is the only controlled feeding frequency in the set.
- Retail and forum advice: 1-3 times per week, using mysis, krill, shrimp, or marine fish flesh dropped on the disc.
  Reef Chasers warns that overfeeding can hurt the animal. **[weak]**
- Field: no feeding frequency found.

### 4.4 Rejection and spit-out
- No source found for rejection of food in this species. **[no data]**

### 4.5 Mouth gaping
- An open mouth is treated as a warning sign in hobby sources (Reef2Reef, Reef Central). **[weak]**

---

## 5. Reproduction

### 5.1 Sexual spawning
- Broadcast spawning. In eastern Australia spawning occurs **January to April** (Scott & Harrison 2009, via
  Wikipedia). Spawning of both sexes is mostly synchronous and restricted to a **few nights per year** during the
  austral summer and autumn (Scott & Harrison 2007, via search).
- Planulae settle after a free-swimming phase. Settlement was **mean 50.3%** of 350 larvae in replicate tanks for
  E. quadricolor (Scott & Harrison 2008). Peak settlement is reported at about day 10, and larvae can survive up to
  **59 days** (Chan et al. 2020, via Wikipedia). The Chan 2020 paper was not located in this research. **[unverified]**
- Captive sexual reproduction is not reliably documented. Delbeek 2002 included observations on sexual reproduction
  in the title, but its text was not retrieved. **[uncertain]**

### 5.2 Asexual fission (longitudinal)
- Wikipedia: asexual reproduction by longitudinal fission occurs "on rare occasions" in the wild.
- Chan et al. 2020 (via Wikipedia): fission is rare, and it spreads successful genotypes but reduces dispersal.
  **[unverified]**
- Clonal aggregations through binary fission are reported in the wild (ZooKeys paper, cited via search summary,
  not verified). **[secondary]**
- Hobby guides describe fission as common in aquaria and say stress can trigger it. **[weak]**

### 5.3 Fission mechanics and recovery (the best primary data)
Scott, Hardefeldt & Hall 2014, PLoS ONE 9(10): e109566:

- Fission method: anemones were cut into halves or quarters. Pulling in opposite directions and tearing is the
  natural split. (PLoS ONE paper, via search).
- Exp. 1 (35 days, n = 24): controls 8/8 survived (100%); halves 15/16 (93.8%); quarters 20/32 (62.5%); treatment
  effect significant (Fisher chi-square = 10.6, df = 2, p = 0.005).
- Exp. 2 (105 days, n = 42): controls 100%; halves 89.3% (25 fragments); quarters 80.4% (45 fragments); no
  significant survival difference from controls (p = 0.124).
- ODD growth: quarters grew less than halves or controls (p = 0.007). Feeding increased ODD growth (p = 0.002).
- PDD growth: controls and halves grew more than quarters (p = 0.002).
- Recovery timeline (Exp. 2):
  - 6 h, attached to tub: controls 93%, halves 96%, quarters 89%.
  - 6 h, re-expanded: controls 93%, halves 73%, quarters 32%. Most had open cuts.
  - Day 7: all accepted food.
  - Day 28: scar tissue over the cut in **96%** of cut animals.
  - Day 56: off-center mouths visible in all cut animals except one quarter.
  - Day 84: **68%** of halves had an almost central mouth. No quarter reached that stage.
- Data caveat: the Scott 2014 abstract reverses the halves survival figures compared with the Results section (the
  abstract gives 89.3% for Exp. 1 and 93.8% for Exp. 2; the Results give the reverse). This table uses the Results.
- Starting size: wet weight 61-274 g (Exp. 1) and 32-171 g (Exp. 2).

Sim use: halves are viable (about 90% survival over 105 days). Quarters are viable but weaker (about 60-80% survival,
and slow to re-expand). Recovery to a central mouth takes about **84 days** for halves.

---

## 6. Stinging and neighbors

- Stinging cells on the tentacles harm nearby corals and animals. One source notes the anemone may sting a nearby
  coral to hold territory (retail / frags2fishes). **[weak]**
- Reach: **no measured sweeper reach in cm found**. The sim should pick a reach value as an assumption (Section 10).
- Soft vs stony: no comparative data found.
- Clownfish are insensitive to the sting (Australian Museum; Fautin 1991). See Section 7.
- Fish that are stung or eaten: a hobbyist report of a tang found head-first in a 2 in bubble tip. The
  forum replies argued the fish died first and was scavenged. A 2-inch anemone cannot eat a 3-inch tang. **[weak,
  likely scavenging]**
- Cleaner shrimp and hermit crabs: no source found showing these attack healthy anemones. Wikipedia's reef-safe
  article lists small hermit crabs as a general concern, without giving a reason. **[weak]**

---

## 7. Clownfish symbiosis (Amphiprion ocellaris)

### 7.1 Natural host status
- Entacmaea quadricolor hosts **14 anemonefish species**, about half of all anemonefish, making it the most
  generalist host (Ollerton et al. 2007, via Wikipedia). The species list (Fautin & Allen 1997, via Wikipedia)
  includes A. akindynos, A. allardi, A. barberi, A. bicinctus, A. chrysopterus, A. clarkii, A. ephippium,
  A. frenatus, A. mccullochi, A. melanopus (clustered form), A. omanensis, A. rubrocinctus, A. tricinctus, and
  Premnas biaculeatus (solitary form). **A. ocellaris is not in this list.**
- Genomic work: E. quadricolor is a cryptic species complex, with at least five species diverged within 5 Mya. Some
  lineages host the generalist A. clarkii and others host the specialist A. frenatus (biorxiv 2024 preprint; Dryad
  data). **[preprint]**
- Direct test: a 2008 Philippine bachelor's thesis found A. ocellaris acclimated to E. quadricolor and H. crispa
  even though neither is its natural host. The author suggested trade overexploitation as a reason. **[hypothesis,
  small study]**

### 7.2 Acclimation to the anemone
- Madhu et al. 2009 (J. Mar. Biol. Assoc. India; hatchery-raised A. ocellaris, Heteractis magnifica):
  - Surrogate anemone alone: mean ~6,472 min to acclimate to a natural host.
  - Natural host alone: mean ~102 min.
  - Surrogate first, then natural host: mean ~19 min.
  - Prior exposure to a surrogate anemone shortened acclimation to a natural anemone.
  - The PDF returned HTTP 503 during this research, so these numbers are from the search snippet. **[secondhand,
    verify]**
- Mucus: Fautin 1991 (Symbiosis 10) states the source of anemonefish protection is unresolved and the evidence on
  acclimation behavior conflicts. Data from one species pair may not generalize. **[review]**
- Hobbyist reports for ocellaris and BTA: from **days to months** (Reef Central, mixed). One reports a week or two.
  Another reports months of avoidance. Clowns sometimes ignore the BTA and then host a different anemone. **[weak]**
- Feeding helps: hobbyists add chopped krill or mysis into the anemone to encourage hosting; a retailer says the
  clownfish feeds the anemone scraps, so pairing speeds acclimation. **[weak]**

### 7.3 Nestling, sleeping, and carrying food
- Nestling, sleeping in the tentacles, and carrying food are standard hobby descriptions. No primary source was
  checked. **[unverified]**
- Roopin 2007 and a 2025 Scientific Reports study (A. clarkii) found that anemonefish selectively keep small animal
  food and small macroalgae and give larger animal food to the host. **[secondary]**

### 7.4 Defense
- Hobbyist observations: pairs defend the anemone against intruders, including other clowns. No measured aggression
  rates found. **[weak]**

### 7.5 Benefits to the anemone
- Roopin 2007 (Auburn thesis, A. bicinctus with E. quadricolor): anemonefish excretion "can supply more than 100%
  of the nitrogen requirements" of the anemone. Ammonia enrichment increased zooxanthellae density and reduced
  tissue loss in starvation experiments. **No numeric values are given in the abstract.** The starvation duration
  is not given. **[primary, numbers missing]**
- Scott et al. 2014 found feeding increased growth, and the anemonefish waste is a plausible nutrient source.
  **[inference]**

### 7.6 Mushroom-to-BTA switching
- No study found on the switch timeline from Rhodactis or Ricordea mushrooms to a BTA in ocellaris.
- Hobby guide: the BTA is a common substitute for ocellaris and percula, and clowns usually adopt it but not always.
  **[weak]**
- Forum replies: common clowns often host mushrooms, zoas, and soft corals, so a mushroom host is not a problem by
  itself. **[weak]**
- Likely cause of a delay: clowns that already host another anemone or coral may not adopt a new host quickly.
  **[inference]**

---

## 8. Water parameters and health

### 8.1 Temperature
- Field: Scott 2014 collection and holding temperatures were **20.5-24.5 C** (Exp. 1) and **21-24 C** (Exp. 2).
- Heat limit: Hill & Scott 2012 (Coral Reefs 31(1): 273-284, DOI 10.1007/s00338-011-0848-x) held anemones at
  **23, 25, 27, and 29 C** under high or low light for **6 days**.
  - At 27 C: bleaching by symbiont expulsion.
  - At 29 C: additional loss of photosynthetic pigments, and in some cases host death.
  - High light increased susceptibility. Photosystem II efficiency fell at temperatures above the summer average.
  - Resident symbionts are clade C.
- Hill & Scott 2012 also state the Solitary Islands animals live about **1 C below their upper thermal maximum**.
- Pryor et al. 2021 (Coral Reefs; "in press" as cited): 16 days of exposure to current and near-future
  temperature and CO2. Higher temperature reduced symbiont density and chlorophyll per cell. CO2 had no effect.
  Bleaching was visible from **day 4**. (Ocean Acidification information center news item, citing the paper.)
- Symbiont shuffling: Hill et al. 2014 held anemones at **28.5 C for 42 days**, compared with controls at 22.9 C,
  then returned all to 22.9 C for 75 days. By day 47 bleaching by expulsion was greater at high temperature. No
  significant density recovery was seen, but clade C3.25 reappeared at day 117 (Marine Biology).
- Conflicting result: Deschaseaux et al. 2018 saw visible bleaching by day 3 at +3.2 and +6.3 C, with no density
  change. Visible bleaching can appear before a measurable density loss. (Deschaseaux 2018, via search).
- Singapore 2024 (Regional Environmental Change): all surveyed E. quadricolor bleached in the 2016 event, and
  bleached animals shrank in the months afterward. (Springer; full text not retrieved because of a login redirect.)
  **[abstract only]**

### 8.2 Salinity and alkalinity (retail)
- Specific gravity 1.023-1.025 and dKH 8-12 (retail care guides). **[weak]**
- pH 8.1-8.4 (retail). **[weak]**

### 8.3 Nutrients (retail)
- Nitrate 10-25 ppm and phosphate 0.01-0.1 ppm (a retailer's listed targets). **[weak; nitrate target is high
  relative to typical reef practice and should not be used as a hard limit]**

### 8.4 Light (retail vs measured)
- Retail: 150-350 PAR, 200-250 PAR recommended by Reef Chasers. **[weak]**
- Measured: propagation holding tanks had light under 400 umol m-2 s-1 (Scott 2014). **[measured, not aquarium PAR]**
- Heat plus high light increases bleaching (Hill & Scott 2012). Light and heat interact.

### 8.5 Maturity
- Retail advice: wait until the tank is at least **six months to one year** old and stable before adding a bubble
  tip. Reef Chasers: new tanks lack the stability these anemones need. **[weak]**
- No primary source found for a maturity threshold.

### 8.6 Signs of decline
- Shrinking (post-bleaching shrinking reported in the 2024 Singapore study). **[abstract]**
- Open mouth, gaping, or fully inverted posture. Hobby sources treat these as warning signs. **[weak]**
- Bleaching: loss of symbiont color, going white or translucent. Hill & Scott 2012; Pryor 2021. **[measured in lab]**
- Losing grip, drifting, and "shrinking" beyond bleaching: no source found. **[unverified]**

### 8.7 Death and ammonia
- Hobby sources say a dead BTA can spike ammonia. No measured value found. **[unverified]**
- Roopin 2007 shows fish-derived ammonia supports the anemone, which suggests dead tissue is a nitrogen source too.
  **[inference]**

---

## 9. Tank mates

| Group | Known issue | Source strength |
|---|---|---|
| Tangs | Anecdote of a tang found dead in a BTA; likely scavenged after dying. | [weak] |
| Wrasses | Some wrasses are reef-safe, some are not. None named as anemone predators in search. | [weak] |
| Blennies, gobies | No source found. | [no data] |
| Hermit crabs | Wikipedia reef-safe article lists small hermit crabs as a concern. No source for direct anemone predation. | [weak] |
| Snails | No source found. | [no data] |
| Coral banded shrimp | No source found. | [no data] |
| Cleaner shrimp | No source found showing attacks on healthy BTAs. | [no data] |
| Hawkfish | No source found. | [no data] |
| Peppermint shrimp | No source found. | [no data] |

The sim should not add predation by tank mates without a source. Only the tang anecdote supports a
"weakened anemone is scavenged" rule.

---

## 10. Sim parameters

Values come from the cited sources where possible. Rows marked **[assumption]** have no source and are tuning
choices. Each assumption should be validated against the sim tuning runner before it is relied on.

| Parameter | Value | Source | Strength |
|---|---|---|---|
| Adult ODD range (sim) | 10-30 cm, max 30 cm | Scott 2014 (98-235 mm); CORAL 2019 (12 in / 30 cm) | Measured + retail |
| Young tentacle count | ~20 | Wikipedia caption (Scott & Harrison 2008) | Secondary |
| Adult tentacle count | **[assumption]** scale from 20 at small size to ~100 at max size | No source | Assumption |
| Sweeper reach | **[assumption]** 1.5-2x tentacle length | No source | Assumption |
| Tank minimum | 114 L (30 gal) | CORAL 2019 | Retail |
| Temperature comfort band | 21-25 C | Scott 2014 field range 20.5-24.5 C | Measured |
| Bleaching onset temperature | 27 C sustained for ~6 days | Hill & Scott 2012 | Lab measured |
| Heat mortality threshold | 29 C | Hill & Scott 2012 | Lab measured |
| Bleaching visible by day | day 3-4 at elevated temperature | Deschaseaux 2018; Pryor 2021 | Lab measured |
| Upper thermal margin | ~1 C below upper max in the wild | Hill & Scott 2012 | Measured |
| Light ceiling in holding | 400 umol m-2 s-1 | Scott 2014 | Measured (not aquarium PAR) |
| Aquarium PAR range | 200-250 target, 150-350 band | Reef Chasers; retail | Weak |
| Salinity | SG 1.023-1.025 | Retail | Weak |
| pH | 8.1-8.4 | Retail | Weak |
| Alkalinity | dKH 8-12 | Retail | Weak |
| Nitrate target | 10-25 ppm (do not use as a hard limit) | Retail | Weak |
| Phosphate target | 0.01-0.1 ppm | Retail | Weak |
| Feeding frequency | weekly (fed anemones grow more) | Scott 2014 | Measured |
| Feeding frequency (hobby range) | 1-3 per week | Reef Chasers; retail | Weak |
| Halves survival (105 d) | 0.89 | Scott 2014 Exp. 2 | Measured |
| Quarters survival (105 d) | 0.80 | Scott 2014 Exp. 2 | Measured |
| Quarters survival (35 d, Exp. 1) | 0.625 | Scott 2014 Exp. 1 | Measured |
| Recovery: re-expansion at 6 h | controls 0.93, halves 0.73, quarters 0.32 | Scott 2014 Exp. 2 | Measured |
| Recovery: scar cover | 0.96 at day 28 | Scott 2014 Exp. 2 | Measured |
| Recovery: central mouth | 84 days for halves (68%); quarters never in 84 d | Scott 2014 Exp. 2 | Measured |
| Fission rate in wild | rare | Wikipedia; Chan 2020 (unverified) | Secondary |
| Fission rate in aquaria | **[assumption]** occasional, after feeding / size threshold | Hobby guides | Weak |
| Spawning window | January-April (austral) | Scott & Harrison 2009 | Measured |
| Larval settlement (mean) | 0.503 | Scott & Harrison 2008 | Measured |
| Clownfish acclimation (surrogate first) | ~19 min | Madhu 2009 (snippet) | Secondhand, verify |
| Clownfish acclimation (natural host alone) | ~102 min | Madhu 2009 (snippet) | Secondhand, verify |
| Clownfish adoption in aquaria | days to months | Hobby forums | Weak |
| Nightly partial deflation | **[assumption]** partial, hours | Reef2Reef, Reef Central | Anecdotal |
| Post-feed deflation duration | **[assumption]** 2-6 h | Hobby forums | Anecdotal |
| Settling after introduction | **[assumption]** 3-7 days wandering | Hobby forums (one week) | Anecdotal |
| Pedal glide speed | **[assumption]** no source; use a slow drift | No source | Assumption |
| Capture-to-mouth time | **[assumption]** 30-120 s per prey | No source | Assumption |
| Maturity before adding | 6 months (retail advice) | Retail | Weak |
| Bleaching loss rate at 27 C | **[assumption]** symbiont density down ~50% over 2 weeks | Pryor 2021 gives direction, not rate | Assumption |
| Heat mortality | 29 C sustained for ~6 days | Hill & Scott 2012 | Measured (lab) |
| Ammonia from fish | anemonefish can supply >100% of host N requirement | Roopin 2007 (A. bicinctus) | Primary, no absolute values |

Notes for implementation:
- Every "assumption" row needs a tuning run before it is trusted.
- Bleaching threshold near 27 C, with light as a multiplier. Fission is a rare event with a size threshold.
  Clownfish acclimation is a per-day probability, since the sources disagree.

---

## References

Primary and peer-reviewed:
- Scott A, Hardefeldt JM, Hall KC (2014). Asexual propagation of sea anemones that host anemonefishes: implications
  for the marine ornamental aquarium trade and restocking programs. PLoS ONE 9(10): e109566.
  https://pmc.ncbi.nlm.nih.gov/articles/PMC4196916
- Hill R, Scott A (2012). The influence of irradiance on the severity of thermal bleaching in sea anemones that host
  anemonefish. Coral Reefs 31(1): 273-284. DOI 10.1007/s00338-011-0848-x.
  https://opus.lib.uts.edu.au/handle/10453/18070
- Hill R, et al. (2014). Symbiont shuffling during thermal bleaching and recovery in the sea anemone Entacmaea
  quadricolor. Marine Biology. https://researchers.mq.edu.au/en/publications/symbiont-shuffling-during-thermal-bleaching-and-recovery-in-the-s/
- Pryor et al. (2021). Ocean temperature, but not acidification, causes sea anemone bleaching under a near-future
  climate scenario. Coral Reefs ("in press" as cited). https://news-oceanacidification-icc.org/2021/03/11/ocean-temperature-but-not-acidification-causes-sea-anemone-bleaching-under-a-near-future-climate-scenario/
- Deschaseaux et al. (2018). Bleaching study, E. quadricolor, +3.2 and +6.3 C, 9 days. (Cited via search; full
  citation not retrieved.)
- Scott A, Harrison PL (2008). Larval settlement and juvenile development of sea anemones that provide habitat for
  anemonefish. Marine Biology. (Cited via search.)
- Scott A, Harrison PL (2009). Gametogenic and reproductive cycles of the sea anemone Entacmaea quadricolor.
  Marine Biology. https://researchportal.scu.edu.au/esploro/outputs/journalArticle/Gametogenic-and-reproductive-cycles-of-the/991012821918102368
- Scott A, Harrison PL (2007). Spawning behaviour of E. quadricolor. (Cited via search.)
- Roopin M (2007). Symbiotic benefits to sea anemones from the metabolic byproducts of anemonefish. Auburn University
  thesis. https://etd.auburn.edu/handle/10415/1331?show=full
- Madhu K et al. (2009). Acclimation and growth of hatchery-produced A. ocellaris to natural and surrogate anemones.
  J. Mar. Biol. Assoc. India. https://www.mbai.org.in/uploads/manuscripts/205-210-K. Madhu.pdf (HTTP 503 at time of
  fetch; numbers from search snippet.)
- Fautin DG (1991). Symbiosis 10. (Cited via search; review on anemonefish acclimation.)
- Fautin DG, Allen GR (1997). Anemonefishes and their host sea anemones. (Species list cited via Wikipedia.)
- Ollerton J et al. (2007). Host-anemone generalism in anemonefish. (Cited via Wikipedia; 14 hosts.)
- Chan et al. (2020). ZooKeys, E. quadricolor clonality and dispersal. Not located in this research. Values cited
  via Wikipedia and marked unverified.
- Genomic preprint (2024). "Nemo knows: clownfishes differentiate cryptic host species...". bioRxiv
  10.1101/2024.11.15.623784. https://www.biorxiv.org/content/10.1101/2024.11.15.623784.full.pdf
- Singapore (2024). Regional Environmental Change, s10113-024-02237-0. Abstract only (login redirect).
  https://link.springer.com/10.1007/s10113-024-02237-0
- Scientific Reports (2025). Anemonefish selective feeding (A. clarkii). https://doaj.org/article/47d31fb41fa04dbf83169c7e9845f76b

Secondary and hobby:
- Wikipedia, "Bubble-tip anemone". https://en.wikipedia.org/wiki/Bubble-tip_anemone
- CORAL Magazine (2019). Best aquarium invertebrates: bubbletip anemone. https://coralmagazine.com/2019/04/25/best-aquarium-invertebrates-bubbletip-anemone
- Advanced Aquarist (2002). Delbeek C., "The Effects of Lighting on Bulb-tip Development." Not retrieved.
- Reef Chasers. Bubble tip anemone care overview. https://reefchasers.com/blogs/reef-chasers-coral-care-guide/bubble-tip-anemones
- Reef2Reef. Bubble tip help (deflation). https://www.reef2reef.com/threads/bubble-tip-help.963806/
- Reef Central, deflation and light threads. https://forums.reefcentral.com/threads/gbta-questions.1160415 ;
  https://www.marinecolorado.org/threads/bubble-tip-no-more.21916
- reefs.com, Delbeek 2002 context. https://www.reefs.com/forum/goto/post?id=1629466
