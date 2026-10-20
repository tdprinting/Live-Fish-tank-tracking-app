# Coral health, bleaching, death and spread (My tank)

How each coral in the sim stresses, bleaches, recedes, dies, recovers and spreads, and what the Tank Health care button
gives it. Code: `core/sim/CoralHealth.kt` (per-species profile, spreading, summaries), `CoralSim.kt` (stress, bleaching,
tissue), `CoralGrowth.kt` (size, mats, Kenya branches), `CoralCare.kt` (the care button), renderer `softcoral.frag` /
`polyp.frag` (bleach tint, receding tissue) and `TankRenderer.setCoralShift` (colony size).

Evidence: aquarium timelines for soft corals have almost no controlled data (see the source notes below). The ordering
of hardiness is the hobby consensus; rates are labelled design estimates (`@Ev(DESIGN_ESTIMATE)`), tuned so a problem
shows within days to weeks and kills within weeks to a couple of months, and a fixed problem heals over 1-3 months.

## §1 Bleaching

Bleaching is the loss of the algae (zooxanthellae) living in the tissue: the colony pales to white, cream or a washed
pink and loses its glow under blue light. Triggers in the sim: heat (fast acute response above the tolerance band plus
NOAA-style degree heating weeks above 29 °C), too much light or a sudden jump in light, too little light while the
lights are on (the algae starve), and starving in ultra-clean water without food.

| Species | Heat | Light | Dark | Food need | Iodine | Recedes | Recovers | Bleached look |
|---|---|---|---|---|---|---|---|---|
| Green star polyps (Briareum) | 0.6 hardy | 0.7 | 0.02/day | low | low | 0.6 | 1.3 | grey |
| Zoanthids (Zoanthus) | 0.9 | 1.0 | 0.02 | 0.25 | low | 1.0 | 1.0 | pale pink |
| Palythoa | 0.6 hardy (Fujimura & Riegl 2017) | 0.8 | 0.015 | 0.35 | low | 0.8 | 1.2 | pale pink |
| Clove polyps (Clavularia) | 1.0 | 1.0 | 0.025 | 0.3 | 0.2 | 1.0 | 0.9 | white |
| Mushrooms (Discosoma) | 0.7 | 1.3 shade lover | 0.01 | 0.2 | low | 0.8 | 1.0 | white |
| Hairy mushroom (Rhodactis) | 0.8 | 1.2 | 0.012 | 0.25 | low | 0.8 | 1.0 | white |
| Ricordea | 1.3 | 1.4 | 0.02 | 0.3 | low | 1.2 | 0.7 | white |
| Elephant ear (Amplexidiscus) | 0.8 | 1.5 | 0.01 | 0.4 | low | 0.9 | 1.0 | white |
| Kenya tree (Capnella) | 0.8 | 0.9 | 0.02 | low | 0.4 | 0.8 | 1.1 | cream |
| Toadstool (Sarcophyton) | 1.3 bleaches readily | 1.0 | 0.02 | low | 0.35 | 1.0 | 0.9 | cream |
| Finger leather (Sinularia) | 1.1 | 1.0 | 0.02 | low | 0.35 | 0.9 | 1.0 | cream |
| Lobed leather (Lobophytum) | 1.0 | 1.0 | 0.02 | low | 0.3 | 0.9 | 1.0 | cream |
| Organ pipe (Tubipora) | 1.3 | 1.1 | 0.03 | 0.5 | 0.15 | 1.5 melts fastest | 0.6 | white |

Multipliers are × the typical rate in `CoralSim`. Example (CoralHealthTest): at a steady 31 °C a toadstool is fully
pale in about 10 days and dead in about 3 weeks; GSP is only three-quarters pale by then. Cooled after 2 weeks, the
toadstool's tissue heals in weeks and its colour comes back over 1-2 months.

## §2 Receding and death

Every stress the coral feels (temperature, salinity and salinity jumps, alkalinity level and swings, pH, ammonia,
phosphate, light, flow, copper, potassium, strontium, Ca/Mg, neighbours stinging or leaching terpenes, H2S, infection,
used-up iodine, hunger, algae growing on it) is combined into one stress; the biggest one is its **need**, shown on
the Tank Health card ("it is too dark", "a neighbour is stinging it").

Tissue is lost while the colony is bleached *and still stressed* (once the cause is fixed it mostly waits for its algae
to come back), under severe all-round stress, from toxins, from algae overgrowth and from smothering. On screen the
colony recedes: patches melt away (`uDead`), the edges of the living tissue go dull, polyps stay shut. At no tissue
left it dies, the event says why ("Toadstool died: the water is too warm"), and the remains clear away after 2 days.

Pests and fights already in the sim: aiptasia sting what they touch, zoas sting on contact, Kenya and leathers leak
terpenes (carbon helps), GSP smothers what its mat reaches, asterina may graze organ pipe.

## §3 Growth and spread

| Species | How it spreads | Sim |
|---|---|---|
| GSP | encrusting mat along the rock, several cm/month, stops at a sand gap > 5 cm | mat radius (PR #147) |
| Zoas, palys, clove | runners / stolons: the clump widens and buds new polyps at its edge | mat grows to 1.3-1.4× its size, then a bud colony 0.5-3 cm beside it about every 45-70 days at full vigour |
| Mushrooms (all) | longitudinal fission, pedal laceration, budding | a new disc 1-4 cm away about every 60-120 days |
| Leathers | budding and fission of the stalk/capitulum; 5 cm frag to ~20 cm in a year | grows to ~1.6-1.7×, then a bud every 120-160 days |
| Kenya tree | grows fast, branchlets pinch off and root where they land (on sand they are lost) | branch drop once over size 1.4 (CoralGrowth) |
| Organ pipe | slow stolon platforms | size only |
| Red macroalgae | biomass, trimmed in the refugium | water model |
| Bubble tip anemone | splits in two when well fed and settled | AnemoneModel |

A colony only spreads when it is healthy (vigour ≥ 0.4, tissue ≥ 0.8, not pale). New colonies use the parent's model
moved to the new spot, so they draw, save and reload like a coral from Add life; at most 10 of a species grow on their
own. Colonies are drawn at their grown size (0.35-1.3× the model); bigger than that they bud instead of swelling into
the rock or glass.

## §4 The care button

Tank Health → "Give corals and inverts what they need" (My tank) sends `Command.ReefCare`. It adds what is low, inside
a day's safe swing: magnesium to 1320 ppm (≤ 100 a day), calcium to 420 ppm (≤ 25), alkalinity to 8.5 dKH (≤ 1),
iodine to 0.06 ppm (≤ 0.03; natural sea water is ~0.06 ppm, Holmes-Farley, Reefkeeping 2003 "Iodine in a reef tank"),
coral food when the corals have not caught any lately, phytoplankton for a feather duster and a bit of mysis for each
anemone once a day. Never into water that would crust (DoseCheck). Each supply has a sand-dollar price
(`CoralCare.Kind.price`, 18 for everything).

## Source notes

The research pass behind this table (sources are mostly retailer and hobby care guides; peer-reviewed items flagged):

## Shared dosing targets (general guidance)

- Calcium ~420 ppm, alkalinity 7-10 dKH (8-9 is inside that range), magnesium 1250-1350 ppm. Dose small and often, test, and raise gradually. Source: https://www.bulkreefsupply.com/content/post/How-To-Dosing-Two-Part-in-Your-Reef-Tank (general dosing guide, returned in search).
- Iodine 0.06 ppm: no source found in this search. Bulk Reef Supply notes most good salt mixes supply enough iodine through water changes (see Ricordea below). Do not hard-code 0.06 without a source.
- Temperature 75-78 F (24-26 C), SG 1.024-1.026, pH 8.1-8.4 is the common range across species pages.

## Species

### Kenya tree (Capnella)
- Bleaching: no specific data found. Retailers call it hardy and tolerant of imperfect water. Treat as moderate-hardy in the sim. Source: https://fragbox.ca/coral-care/kenya-tree-coral/
- Light: 50-150 PAR cited; low-to-moderate light is the consensus. Source: https://gensou.sg/?p=29588
- Flow: not quantified in results. Bulk-retailer guidance is moderate.
- Death/recession: no specific signs found. Assume dropped branchlets are normal (see growth).
- Feeding: photosynthetic; no feeding requirement stated.
- Growth: "fast," growth visible within weeks, retailer claims only. Dropped branchlets float and attach in 1-2 weeks in low flow. Source: https://gensou.sg/?p=29588 (see also https://fragbox.ca/?p=287494)
- Spread: dropping branchlets (the natural method) plus manual rubber-band attachment to rubble. Regular pruning needed to control it.

### Green star polyps (Briareum / Pachyclavularia)
- Bleaching: not quantified in results.
- Light: sources disagree. One says "bright"; another says it tolerates less light with supplemental feeding. Sim default: moderate.
- Flow: moderate to strong, not in direct jet path. Source: https://gensou.sg/green-star-polyp-care-guide-reef/
- Copper: tolerant, but high copper still harms it. Source: https://gensou.sg/?p=30811 (retailer claim)
- Nutrients: one Singapore vendor claims high nitrate/phosphate accelerate growth. Unconfirmed; do not use in sim logic.
- Growth: fast; Briareum described as slower than Pachyclavularia by one source, the reverse by another. Spread by stolons over rock.
- Competition: aggressive; can overgrow and sting neighbouring corals. Isolate on its own rock or wall. Source: https://gensou.sg/green-star-polyp-care-guide-reef/

### Zoanthids and palythoa (Zoanthus / Palythoa)
- Bleaching (peer-reviewed, lab stress, not aquarium timelines):
  - Palythoa caribaeorum is described as a sensitive bleaching indicator. Source: https://pure.psu.edu/en/publications/a-comparison-of-the-thermal-bleaching-responses-of-the-zoanthid-p/
  - Fujimura & Riegl (2017, J. Exp. Mar. Biol. Ecol.): 48 h at 33.5 C (vs 26.5 C control). High flow (15 cm/s) reduced bleaching; small colonies were protected better. Bleaching onset was rapid, within 48 h at high temperature. Source: https://works.bepress.com/bernhard-riegl/190 (abstract text returned by search, not the full paper).
  - Brazilian bioindicator study: P. caribaeorum bleached fully after 48 h at 45 C and 72 h at 40 C. Lab extreme, not a reef-tank threshold. Source: search result only.
- Aquarium timelines (stress to visible bleach, recovery days): not found in sources. Sim should use anecdotal ranges (days to weeks) and label them as estimates.
- Light: moderate-high is typical; palythoa tolerate more light than zoas.
- Flow: moderate to strong; flow reduced bleaching in the 2017 study above.
- Zoa pox: forum-level evidence only. Furan-2 dips (15-30 min, rinse) are the common treatment; outcomes are mixed and some hobbyists report harm. Sources: https://aquanerd.com/?p=8182 ("Furan-2 ZoaPox Treatment More Harm Than Good"), https://reefs.com/coral-nubbins-zoa-pox/. Cause and spread are unestablished.
- Palytoxin: handle with gloves and eye protection. Sources: https://www.wetwebmedia.com (search result).
- Death path: polyps stop opening and tissue sloughs; not quantified.
- Growth/spread: sheet-like encrusting via stolons; no rate found.

### Clove polyps (Clavularia)
- Bleaching: not quantified.
- Light: sources disagree. Alibaba guide says low-moderate; Fishkeeper UK and Quality Marine say moderate-bright. Safer default is moderate-high, since they rely on zooxanthellae. Sources: https://www.fishkeeper.co.uk/fish/marine/soft-corals/clove-polyps and https://www.qualitymarine.com/quality-marine/coral/polyps-button-zoanthid/clavularia/clove-55503/
- Flow: decent current; weak flow lets detritus and filamentous algae build up.
- Copper: not tolerated (Quality Marine).
- Nutrients: nitrate 1-10 ppm, phosphate 0.01-0.05 ppm (Aquarium Source: https://www.aquariumsource.com/clove-polyps). Excess nutrients feed nuisance algae on fast colonies.
- Growth/spread: aggressive, colonises any hard surface including rock with other corals. Containment: a band of fine sand at least 4 inches deep (they cannot attach to sand). Source: https://www.saltwateraquariumblog.com/clove-polyps-care-guide/
- Competition: can crowd out nearby zoanthids. Source: https://www.fishkeeper.co.uk/fish/marine/soft-corals/clove-polyps

### Mushrooms

#### Discosoma (disc mushrooms)
- Bleaching/recession: too much light causes small white bumps on polyps. Lifted or drifting polyps mean flow is too strong. Source: https://fragbox.ca/coralwiki/discosoma-mushroom-coral-care/
- Light: low to moderate; they do well with no feeding if light is adequate.
- Flow: low. A polyp trapped in shade or a cave can die; move to very low flow on rubble until it reattaches.
- Reproduction: budding, longitudinal fission, pedal laceration. Fragment by cutting with a razor and placing on rubble; regrowth in about 4-6 weeks (single-retailer claim). Source: same as above.

#### Ricordea (R. yuma, R. florida)
- Bleaching: R. yuma bleaches under intense light. Common cause is excess light or light stress at the store. Source: https://reefs.com/forum/general-discussion/229079-ricordia-question.html
- Recovery: anecdotal, weeks to months. Move to low light, keep flow gentle, raise about 1 inch a week once polyps open. Sources: https://www.nano-reef.com/forums/topic/124826-bleached-rics/ and https://marinecolorado.org/threads/trouble-with-my-ricordea.12521/latest
- Death: some bleaching continues until the coral dies. Source: https://www.nano-reef.com/forums/topic/287364-ricordea-florida-bleaching-dying/
- Light: sources give 50-250 PAR; working range 75-150 PAR (sources conflict).
- Flow: low to moderate; strong flow detaches them.
- Feeding: photosynthetic; one source suggests 1-2 feedings/week of mysis or enriched brine. Source: https://fragbox.ca/product/orange-ricordea-yuma/ (retailer)
- Iodine: most good salt mixes supply enough through water changes.
- Growth: slow at first; after about a year, produces many daughter polyps by longitudinal fission (retailer claim).

#### Hairy mushroom (Rhodactis)
- Not separately researched in this pass; treat as similar to Discosoma with more flow tolerance. Gap noted below.

#### Elephant ear (Amplexidiscus fenestrafer)
- Light: moderate. Flow: low to medium. High flow pulled a hobbyist's half-mushroom up and it shrank. Source: https://sgreefclub.com/forum/topic/61097-elephant-ear
- Adult diameter ~46 cm (18 in). Grows rapidly with adequate space.
- Feeding: sources disagree. Largely photosynthetic, with occasional small meaty pieces; others say it grows faster when fed fish or crustacean pieces. Source: https://gensou.sg/?p=41059 (taxonomy caveat: this guide mixes Rhodactis and Amplexidiscus).
- Predation: it can close on prey in under 3 seconds and will eat clownfish and small fish, shrimp and crabs. Source: https://www.fishkeeper.co.uk/fish/marine/soft-corals/elephant-ear-coral
- Copper: moderate tolerance, sources disagree.

### Organ pipe (Tubipora musica)
- Bleaching: no aquarium data found. Field study (Lirang Island, Maluku, 2016 surveys, 379 colonies): 87.33% healthy, 3.69% dead, lesions attributed to mechanical damage and sedimentation rather than microbes. Source: https://scholar.ub.ac.id/en/publications/monitoring-on-health-condition-of-tubipora-musica-stolonifera-usi/
- Light: moderate to high (retailers); one fragger says low-medium. Sim default moderate-high.
- Flow: medium, intermittent; keep it clear of algae and sediment.
- Water: SG 1.023-1.025, 72-78 F, dKH 8-12. Keep phosphate and nitrate near zero (retailer). Source: https://www.qualitymarine.com/quality-marine/coral/corals-lps/tubipora/pipe-organ-52034/
- Copper: avoid.
- Feeding: photosynthetic; polyps extend to filter-feed small particles.
- Growth: tight, slow, "well behaved." Source: https://liveaquaria.com/products/pipe-organ-coral

### Leathers

#### Toadstool (Sarcophyton)
- Light: PAR 100-200 is typical; one guide says bleaching risk above PAR 300 under intense LEDs. Source: https://gensou.sg/?p=30810
- Flow: moderate, alternating; weak flow lets detritus build up on the cap.
- Growth (conflicting, treat as upper bound): about 1 inch per 4 months in ideal conditions, up to 1.5 in/month with nutrients and feeding; a 5 cm frag reaches 20 cm in a year. Source: https://www.queencitycorals.com/?p=60417
- Mature size: 30-40 cm in captivity.
- Fragmentation research (peer-reviewed or conference, Vietnamese fragment study): fragments of 0.5-2.5 cm over 90 days; 1.5 cm recommended in lab. Survival 63.6-77.8% in an Indonesian study; larger fragments grew faster (0.47 vs 0.38 cm^2/day). Sources: https://vjs.ac.vn/jmst/article/view/18638 and https://ejournal.undip.ac.id/index.php/buloma/article/view/15744

#### Finger leather (Sinularia)
- Light: 100-250 PAR (sources conflict). Sim default moderate.
- Flow: moderate to strong; sweep the branches.
- Water: alk 7-9 dKH, Ca 400-440, Mg 1300, SG 1.025 (Gensou). Nitrate 5-10 ppm and phosphate 0.05-0.1 ppm help keep polyps extended (Gensou); ORA gives nitrate 1-20 and phosphate 0.01-0.1. Source: https://gensou.sg/?p=32628
- Toxins: Sinularia can release toxins under stress; keep away from stony corals; run carbon and do water changes. Source: https://www.algaebarn.com/?p=782671
- Growth: 1-2 cm of new tip per month (single blog). Source: https://gensou.sg/?p=32628

#### Lobophytum
- Not researched in this pass. Gap noted below.

### Red macroalgae (Gracilaria / Botryocladia)
- Light: Gracilaria moderate-high; Botryocladia no reliable number.
- Flow: moderate. Source: https://live-plants.com/products/gracilaria
- Nutrient export: real only when biomass is harvested and removed. Source: https://algaebarn.com/blog/macro-algae/red-alga-gracilaria-overview
- Fragmentation: loose pieces drift into filters. Highly palatable to tangs, hermits, and conchs.
- Botryocladia: wild-collected specimens arrive pale; colour returns within days under good light and flow (vendor). Low alkalinity led to dinoflagellate and calothrix growth on one tank (anecdote). Source: https://live-plants.com/products/botryocladia
- Growth: fast in a sump refugium with nutrients; no rate found.

### Bubble tip anemone (Entacmaea quadricolor)
- Bleaching: not quantified. Vendors stress a mature, stable tank; retailers say moderate care. Source: https://www.bulkreefsupply.com/ora-acid-wash-rose-bubble-tip-anemone-ora.html
- Light: 150-350 PAR (retailer); Fishkeeper UK rates lighting as high. Source: https://www.fishkeeper.co.uk/fish/marine/invertebrates/anemones/bubble-anemone
- Temperature: 75-78 F (retailer) or 25-27 C (Fishkeeper UK).
- Flow: keep away from powerheads; anemones move on their foot.
- Feeding: no frequency or food types found; search again before setting sim feeding rates.
- Pests: Berghia nudibranch is for Aiptasia control, not Entacmaea; Aiptasia is a separate pest.
- Growth: no rate found; propagation not covered.

## Pests and general death paths (cross-species)

- Aiptasia: Berghia nudibranchs eat Aiptasia only. They starve once Aiptasia is gone unless moved, and often die if no food is found within about 48 h. Keep intakes covered. Sources: https://topshelfaquatics.com/products/berghia-nudibranch-aiptasia-destroyers and https://www.aquariumsource.com/berghia-nudibranch (vendor).
- Pyramid snails and zoa-eating nudibranchs/sea spiders: not researched in this pass. Gap noted below.
- Copper: harmful to clove polyps and Tubipora; tolerated by GSP but with caution.
- Receding or melting signs: bleaching-stage polyps stop extending; tissue sloughs; mushrooms shrink or lift and drift when flow is too strong (Discosoma, Amplexidiscus). Sources as cited per species.

## Gaps and conflicts to resolve before coding

1. No peer-reviewed aquarium bleaching timelines (days from stress to visible bleach, days to recovery) for any species. Use labelled estimates in the sim.
2. Iodine 0.06 ppm has no source here; confirm against a salt-mix or reef-chemistry reference.
3. Feeding guidance for Entacmaea and Sinularia is missing.
4. Lobophytum, Rhodactis hairy mushroom, zoa-eating pests, and pyramid snails were not researched.
5. Light and flow numbers conflict across sources for almost every species. Use the midpoint of the cited range and expose it as a tunable constant.
6. Dosing numbers are general; they are not from Advanced Aquarist or Reefkeeping as requested.
