# 44. Shrimp pack: fire, harlequin, sexy and tiger pistol shrimp

Added 2026-10-21 (thread "Popular invertebrates pack"). The source notes from the research pass start at "Reef shrimp
research note" below. This first section says what the sim takes from them and what is a design estimate.

## 0. What the sim uses

| Species | Sheet | Brain / rig | From the sources | Design estimates (no data found) |
|---|---|---|---|---|
| Fire shrimp | `lysmata_debelius` | `FireShrimpBrain` (a shy `CleanerShrimpBrain`), `ShrimpLimbs` | red with white dots, white antennae and legs (WP); nocturnal, shy, facultative cleaner "not as earnest"; tolerates only its mate (Reefs.com) | cleaning share 0.4; night foray 30 cm; molt 21-42 d |
| Sexy shrimp | `thor_amboinensis` | `SexyShrimpBrain`, `ShrimpLimbs` | 13-20 mm; olive-brown with blue-edged white patches; abdomen curved up, tail held over the head, swayed while walking (WP, JD); groups on anemones, corals as a fallback | wag 1.2-2.2 Hz; day-active; host pick rate |
| Harlequin shrimp | `hymenocera_picta` | `HarlequinShrimpBrain`, `ShrimpLimbs` | starfish only; pairs flip stars and feed together (WP); tube feet first, 30-60 min lifting phase (Prakash & Kumar 2013); one small asterina per 5-7 days (care guide) | meal 1.5-3 h; hunger threshold 0.4 |
| Tiger pistol shrimp | `alpheus_bellulus` | `PistolShrimpBrain`, `ShrimpLimbs` | partner of *Cryptocentrus cinctus*; continual sediment removal; antennal contact; retreats at the goby's flicks; right claw larger; snap up to 218 dB (WP, Versluis 2000) | load size 0.4 cm³ every 20-120 s; snap distance 4 cm and gap 1-5 min |

Prices (economy rows, real USD): sexy $19, fire $60, pistol $45, harlequin $65, from the retail points below.

---

# Reef shrimp research note (batch 2)

Scope: fire/blood shrimp (Lysmata debelius), harlequin shrimp (Hymenocera picta), sexy shrimp (Thor amboinensis), tiger/candy pistol shrimp (Alpheus randalli and the related A. bellulus). Sources are Wikipedia, peer-reviewed or institutional papers where found, and retailer or hobbyist pages. Retailer and forum claims are flagged. Items marked "est." are estimates or inferences, and "GAP" marks facts I could not source.

Source shorthand used below:
- [WP-LD] https://en.wikipedia.org/wiki/Lysmata_debelius
- [WP-HP] https://en.wikipedia.org/wiki/Hymenocera_picta
- [WP-TA] https://en.wikipedia.org/wiki/Thor_amboinensis
- [WP-AR] https://en.wikipedia.org/wiki/Alpheus_randalli
- [WP-TPS] https://en.wikipedia.org/wiki/Tiger_pistol_shrimp (covers Alpheus bellulus)
- [JoTT-HP] https://threatenedtaxa.org/JoTT/article/view/1519 (Prakash and Kumar 2013, harlequin feeding in captivity)
- [Sci-Versluis] Versluis et al. 2000, "How Snapping Shrimp Snap: Through Cavitating Bubbles", Science 289(5487):2114-2117 (no stable URL retrieved; DOI not fetched)
- [JASA-Schmitz] Schmitz, Versluis, von der Heydt, Lohse 2000, J. Acoust. Soc. Am. (no stable URL retrieved)
- [NOAA-LD] https://repository.library.noaa.gov/view/noaa/12917 (captive-rearing protocol)
- [TAR-LD] https://tar.sljol.info/articles/8185 (Tropical Agricultural Research, 2016, Sri Lanka)
- [RB-LD] https://reefbuilders.com/2017/09/29/fire-shrimp-successfully-captive-bred-by-coral-creatures-in-australia
- [JCU-LD] https://portfolio.jcu.edu.au/Projects/JCU10863
- [Reefs-LD] https://reefs.com/?p=209449 (hobbyist account)
- [Caves-2019] Caves, Chen, Johnsen 2019, Biology Letters, cleaner shrimp predation (L. amboinensis, not L. debelius); PMC copy listed at https://pmc.ncbi.nlm.nih.gov/articles/PMC6769148
- [LA-Pistol] https://www.liveaquaria.com/blogs/stocking-your-aquarium/pistol-shrimp-gobies-a-safe-alliance (retailer article, search snippet only)
- [GENSOU-HP] https://gensou.sg/?p=32432 (harlequin care guide, search snippet)
- [SWA-TA] https://www.saltwateraquariumblog.com/sexy-shrimp-care-thor-amboinensis/ (sexy shrimp care, search snippet)
- [JD-TA] https://www.jungledragon.com/specie/7671/sexy-shrimp.html (sexy shrimp species page, search snippet)
- [FK-TA] https://www.fishkeeper.co.uk/fish/marine/crabs-shrimp/sexy-shrimp (search snippet)
- [BA-LD] https://www.buildyouraquarium.com/blood-red-fire-shrimp/ (fire shrimp care, search snippet)
- [RE-LD] https://reefexclusive.com/products/lysmata-debelius (price, search snippet)

---

## 1. Fire / blood shrimp: Lysmata debelius

**Size.** WP-LD: "Grows up to 3 centimetres (1.2 in) long." Retailer and care pages give 2 to 4 in (conflicting, [BA-LD] and search summaries). Use 3 cm for the sim.

**Colour and pattern.** WP-LD: "It has a red body." White dots on the cephalothorax and legs. Antennae are "conspicuous white antennae," with white tips on the third to fifth pereiopods. Suggested hex (est.): body #C4161C to #B0122A, white dots #F5F5F5, antenna shafts #FFFFFF.

**Anatomy.** Long white antennae, roughly 1.5 to 2 times body length (est.; not quantified in a source). Hermaphrodite, see section 6.

**Dimorphism.** WP-LD: "Males and functional hermaphrodites look similar, and any two individuals may mate." Not strongly dimorphic. A retailer summary says juveniles first show male characters, then female (search summary, unverified against a primary page).

**Behaviour.**
- Mostly nocturnal and shy (retailer summaries, [BA-LD]). Needs shaded hides and rockwork.
- Facultative cleaner: grooms fish for parasites and loose scales, but "not as earnest" as other cleaner shrimp (search summary of care pages, [BA-LD] and related). WP-LD confirms the cleaner role: "role as a cleaner shrimp in reef ecosystems."
- Scavenges leftover food (search summary).
- Locomotion: walks on the bottom; swim/tail-flip speed not sourced. GAP.

**Diet in aquaria.** Accepts most sinking prepared and frozen foods, plus leftover food (search summary of [BA-LD]). Wild diet not covered in WP-LD.

**Interactions.**
- Territorial with its own kind. [Reefs-LD]: "very aggressive toward other individuals and tolerates only its mate in all but the largest aquaria." Retailer summaries say it may chase or kill other shrimp and advise single or paired keeping.
- Poor tank mates: large fish that eat shrimp, lionfish, hawkfish and triggerfish (search summary of [BA-LD] and similar). Not individually sourced by species for predation.
- Predation on cleaner shrimp generally: [Caves-2019] found "client fish predation on cleaner shrimp appears rare," and predatory clients were cleaned in a "significantly lower proportion of interactions." This study is on L. amboinensis, not L. debelius. Leg rocking may signal to clients.

**Water needs.**
- Retailer target (search summary of care pages, not fully sourced): pH 8.1 to 8.4, SG 1.023 to 1.026.
- Intolerant of copper, ammonia, nitrite, and elevated nitrate (search summary of care pages).
- Temperature: GAP (no reliable number retrieved). Est. 24 to 26 C (75 to 79 F) typical reef.
- Molting interval: GAP for L. debelius specifically. Copper harms molting frequency and survival in shrimp generally (search summary of a shrimp toxicity paper, unnamed). Retailers note iodine supports molting.
- Acclimation: drip acclimation for over an hour (search summary of care pages).

**Reproduction.**
- Hermaphrodite, any two individuals may pair [WP-LD], [Reefs-LD].
- Captive rearing achieved but larval survival and duration are the bottleneck:
  - [TAR-LD]: 2016 Sri Lankan protocol, 108 L glass larval tanks, larval survival 12.7 to 18.35%, juveniles 100% survival.
  - [NOAA-LD]: 168 shrimp metamorphosed to juveniles between 75 and 158 days after hatching.
  - [RB-LD]: Coral Creatures (Australia) success after a larval period of nearly 80 days.
  - [JCU-LD]: ongoing project (May 2026 to May 2027) on broodstock nutrition and larval feeding.
- Egg carrying: GAP for L. debelius (not reported in sources retrieved).

**Price, US retail (2025 to 26).**
- [RE-LD] $55.00 (single, Reef Exclusive; date not shown).
- Search-listed [TPP-LD] $69.99 (in-store only) and $79.99 large (out of stock).
- Aquatic Sunlimited $64.99 to $69.99 (search summary).
- [LA-Pair] LiveAquaria "Blood Red Fire Shrimp, Pair" $129.99 (undated; check live page).
- Historical [RB-LD]: about $30 to $40 in North America (older figure).

**Minimum tank.** Sources conflict: 10+ gallons vs 30 gallons (search summary of care pages). Use 30 gal for a pair (est.), 10 gal for a single (est.).

---

## 2. Harlequin shrimp: Hymenocera picta

**Size.** WP-HP: "Up to about 5 cm (2 in)." Males slightly smaller than females.

**Colour and pattern.** WP-HP: "Usually cream or white with spots. Pacific shrimp often have red spots, while Indian Ocean shrimp typically have purple spots. Hawaiian individuals have both purple and red spots." Suggested hex (est.): body #F2EEE6, red spots #C8102E, purple spots #6B2D8F.

**Anatomy.**
- Claws: WP-HP says the claws "appear to look flattened and thin." Not described as paddle-shaped in the article. The petal-like claws are a hobbyist description (GAP for a measured source).
- Two walking legs on each side per WP-HP (text: "Each side has two walking legs").
- Antennae length vs body: GAP.

**Dimorphism.** Males slightly smaller than females [WP-HP]. Females carry the eggs (see reproduction).

**Behaviour.**
- Activity: WP-HP: "They move slowly, in waves, and their claws move almost constantly. A pair usually hunts together, flipping a starfish onto its back to feed." Field observation ([underwaterkwaj], search summary): usually stays hidden under rocks or in caves at Kwajalein. Specimens in Hawaii and Bali sit in the open.
- Locomotion speed: GAP (slow per WP-HP).
- Starfish handling: [JoTT-HP] (Prakash and Kumar 2013) documents captive feeding on Linckia laevigata (blue star), described as the shrimp's "favorite prey." Feeding starts with "removing the ambulacral feet" (tube feet) and removing "gonadal tissues." The lifting phase "continues for 30-60 minutes." Days-long feeding on one starfish: GAP (not confirmed by any source; [JoTT-HP] describes a 30 to 60 min lift phase, not days).
- Flip-over and carry behaviour: partly confirmed. WP-HP describes flipping the starfish onto its back. Carrying is not confirmed.

**Diet in aquaria.**
- Starfish only. WP-HP: "Starfish only, including crown-of-thorns starfish. They prefer smaller, more sedentary starfish. They rarely eat sea urchins, and only when starfish are unavailable."
- Reeflex (search summary): the shrimp cannot be kept otherwise and eventually starves.
- Asterina: one care guide [GENSOU-HP] says "one small Asterina per shrimp every 5 to 7 days, or one leg of Linckia every two weeks."
- Forum estimates (search summary, unverified): one starfish per week for a pair; one per 2 weeks for a single; one large chocolate chip star every other week. Some say 2 to 3 weeks between meals is possible. Keepers note one chocolate chip star leg gives more meat than many asterina.
- Asterina per week: no source gives a reliable count (GAP). Practical answer: the common approach is a reproducing asterina population or rotating chocolate chip stars.

**Cleaning of fish.** Not a cleaner shrimp. GAP. Do not count it as a cleaner in the sim.

**Interactions.**
- Reef and invert safe except around starfish (search summary of one guide). Fromia nodosa and whip stars are not eaten (Reeflex, search summary).
- Pairs: "live in pairs... often seen together in the wild" [WP-HP].
- Wild prey range includes Acanthaster planci (crown-of-thorns) per a 2016 Mexican Pacific report (search summary).

**Water needs.**
- Temperature: "Preferred temperature is 72 to 82 F" [WP-HP].
- Sensitive to rapid water chemistry changes, temperature, and low oxygen (stagnant systems). Does not tolerate very high nitrate (Reeflex, search summary).
- Copper: GAP (not sourced for this species).
- Molting interval: GAP.
- SG and pH: GAP for this species specifically.

**Reproduction.**
- Pair bonding: "males and females are often seen together in the wild, working together to reproduce and forage" [WP-HP].
- Fecundity: "females produce 100 to 5,000 eggs per season, depending on environmental factors" [WP-HP].
- Egg carrying: WP-HP does not mention it. GAP. (est.: females carry eggs under abdomen, like most Caridea.)

**Price, US retail (2025 to 26).**
- LiveAquaria listing: $59.99 regular, another version $54.99 (undated; check live page). Listing sold out in one version.
- Diver's Den listing: $79.99 for a 1.25 in specimen.
- Fishyhub $87.49, Top Shelf Aquatics $52.50 to $75.00, Reef Chasers $69.99 sale (undated, search summary).
- Note: LiveAquaria lists these single shrimp; pair pricing not confirmed.

**Minimum tank.** 10 gallons for a pair [Reeflex, search summary of saltwateraquarium guide]. Est. 20 gal for comfort.

---

## 3. Sexy shrimp: Thor amboinensis

**Size.** WP-TA: "About 13 mm (0.5 in) long." Fishkeeper gives max about 2 cm [FK-TA]. Use 13 mm body, 20 mm max in sim.

**Colour and pattern.** WP-TA: "Olive-brown, with evenly spaced white patches outlined in thin blue lines." Suggested hex (est.): body #6B6A3A to #7A6A3F, white patches #F4F4F0, blue outline #4A6FA5.

**Anatomy.**
- Abdomen: "curved upward with its tail fan held above the head" [JD-TA]. At rest the abdomen and tail arch toward the head [FK-TA].
- Antennae vs body: GAP.
- Claws: small; not a feature. GAP for measurements.

**Dimorphism.** GAP. Females carry fertilized eggs under the abdomen [JD-TA].

**Behaviour.**
- Tail-waving / dance: "The name comes from the swaying and vibrating of the abdomen while walking." Sources say the tail is arched up and shaken, possibly as a signal to conspecifics or to predators [search summary, WP-TA, FK-TA]. Leg visibility while dancing is described in search summary.
- Tail-waving rhythm frequency: GAP. No source gave a Hz or per-second number. Do not invent; use an est. 1 to 3 Hz for a sim (est., unsourced).
- Activity: GAP for day/night. Anemone-dwelling shrimp are typically active in the day (est.).
- Locomotion: walks, sways, does not swim much (est., search summary implies walking).
- Hiding: lives among anemone tentacles for protection [WP-TA].

**Diet in aquaria.**
- Omnivore. Feed meaty foods like mysis or brine shrimp a few times a week, target-fed with a pipette if fish compete [SWA-TA].
- In the wild: "feeds on tentacle tissue and on plankton trapped in the host's mucus" [WP-TA]. In aquaria: scavenges host anemone meals [BA-LD] and feeds on mucus if present. Some sexy shrimp nip the anemone's protective mucus (search summary).
- Picks at corals: GAP for direct coral picking. Corals listed as hosts: bubble coral, Euphyllia, Zoanthus (search summary, not sourced).

**Cleaning of fish.** Not a cleaner shrimp in the literature. GAP.

**Interactions.**
- Anemone hosts (WP-TA): Stichodactyla haddoni (carpet anemone), Lebrunia danae (stinging anemone), Cryptodendrum adhaesivum (adhesive anemone). Also uses some corals when no anemone is available [WP-TA].
- Carpet and bubble anemones: carpet anemone (Stichodactyla haddoni) is a documented host [WP-TA]. Bubble tip anemone: search summary says bubble coral and Euphyllia are used. Bubble tip anemone not individually sourced.
- Gregarious: "often kept in groups of three or more" [WP-TA].
- Predators: GAP. No source found on hawkfish, dottyback, or wrasse predation on sexy shrimp.

**Water needs.**
- Temp: 24 to 26.5 C (75 to 80 F) [SWA-TA].
- SG: 1.024 to 1.026 [SWA-TA].
- pH: 8.1 to 8.4 [SWA-TA].
- Copper: "do not tolerate copper medications" [SWA-TA] and "copper medications are harmful... vulnerable during molts" [Top Shelf Aquatics, search summary].
- Iodine and magnesium: "linked to molting, so supplementing them is commonly advised" [SWA-TA]. Retailer claim, unverified.
- Molting interval: "roughly monthly" from retailer pages (search summary). Mark as est.
- Acclimation: drip, because small and sensitive [SWA-TA].

**Reproduction.**
- Females carry eggs under the abdomen [JD-TA].
- Larvae: "several stages" before metamorphosis, no count [WP-TA].
- Larvae settle near potential host anemones, and accept several species, sometimes favouring the parent's host [WP-TA].
- Captive breeding: GAP.

**Price, US retail (2025 to 26).**
- Aquatic Sunlimited Sexy Anemone Shrimp $18.99 (sold out; search summary).
- LiveAquaria: no price retrieved.
- Other: GAP. Use est. $15 to $25 each.

**Minimum tank.** About 5 gallons [SWA-TA, one source]. Use 10 gal est. for a group of three with rockwork.

---

## 4. Tiger / candy pistol shrimp: Alpheus randalli (and A. bellulus)

**Species note.** The Wikipedia tiger pistol article covers A. bellulus. The candy pistol (A. randalli) is covered in WP-AR. Behaviour details below mix both; flagged where it applies only to one species.

**Size.**
- A. randalli: "growing to about 3 centimetres (1.2 in) long" [WP-AR].
- A. bellulus: "Up to 4 to 5 cm (1.6 to 2.0 in), excluding antennae" [WP-TPS].

**Colour and pattern.**
- A. randalli [WP-AR]: "White or transparent, with red bands and spots on the rostrum and both large and small chelipeds. Further white and red bands are on the abdomen, the uropod tips are red, and the thoracic appendage tips are yellowish-green." Suggested hex (est.): body #F8F8F4 translucent, red bands #C81E2D, tips #9ACD32.

**Anatomy and claws.**
- Chelipeds are asymmetric: "one carrying a massive snapping claw" [WP-AR]. The side is not stated in WP-AR.
- A. bellulus: "The right cheliped is the larger one" [WP-TPS]. For A. randalli, assume right (est.; confirm).
- Antennae vs body: GAP.

**Dimorphism.** GAP.

**Snap mechanism and sound.**
- A. bellulus [WP-TPS]: claw "closes at high speed and expels an air bubble toward prey at over 100 km/h (62 mph)." Water is drawn into a chamber in the claw's bend. A plunger forces it out as a high-speed bubble on closure.
- Sound: [WP-TPS] "up to 218 decibels, with temperatures reaching 4,800 C." Primary: [JASA-Schmitz] measured up to 220 dB re 1 uPa at 1 m, pulses of 500 ns duration with frequencies beyond 200 kHz.
- Mechanism: [Sci-Versluis] (search summary) the sound comes from the bubble collapse, not the claw strike. Water jet about 25 m/s, cavitation bubble collapses.
- Snap rate per second: GAP. No source retrieved. Do not invent a number; sim default est. 1 to 3 snaps/s when active (est.).

**Activity.**
- Nocturnal, poor eyesight, seldom seen by day [LA-Pistol, search summary of retailer pages].
- Burrows: "Burrows are reinforced with small pieces of shell and coral to prevent collapse. The shrimp continually removes sediment." Early burrow building: shrimp shows no interest in feeding or resting during early construction [WP-TPS].
- Locomotion: walks along the bottom; speed GAP.
- Depth: up to 18 m for randalli [WP-AR] or 20 m for bellulus [WP-TPS].

**Digging and bulldozing.**
- A. randalli: shrimp "excavates a burrow shared with the goby in sandy or silty seabed. It has one or more openings whose positions are constantly changing as the shrimp engages in burrowing activities" [WP-AR].
- Burrows are sediment-based and shrimp removes sediment continuously [WP-TPS].
- Who digs: the shrimp digs; goby watches and hangs out at the entrance (search summary).
- Burrow size and shape: GAP for measurements. Male and female shrimp "build passageways between adjoining burrows" and "do not share burrows" [search summary of a source, unverified].

**Goby symbiosis.**
- Goby partners:
  - A. randalli: Amblyeleotris randalli (Randall's prawn goby), per WP-AR. Pre-description name was an undescribed goby.
  - A. bellulus: Cryptocentrus cinctus, Amblyeleotris guttata, Stonogobiops yasha [WP-TPS]. Japanese populations: Amblyeleotris japonica.
- Antenna contact: the shrimp keeps contact with the goby through its antennae and retreats into the burrow after noticeable movement from the fish [WP-TPS]. Also: "the shrimp's antennae, kept in constant contact with the goby when outside the burrow" (search summary).
- Goby tail flick warning: rapid fin flicks, transmitted through the shrimp's antennae, cause retreat (film analysis of Cryptocentrus steinitzi and Alpheus purpurilenticularis, 1979; search summary). More flicks = more danger. Small fish do not trigger flicks; medium to large do (search summary, unsourced primary).
- A. randalli goby-tail-flick specifically: GAP (WP-AR does not mention it).
- Diet: WP-AR says the shrimp is "mainly a detritus feeder." Goby picks invertebrates from disturbed sediment and takes mouthfuls of sediment.
- Pairing: shrimp about half the goby's length pairs more reliably (Singapore guide, search summary).

**Water needs (retailer, search summary).**
- Temp: 72 to 78 F.
- dKH: 8 to 12.
- pH: 8.1 to 8.4.
- SG: 1.023 to 1.025.
- Copper and molting: GAP for this species.

**Tank and substrate (retailer + forum).**
- LiveAquaria [LA-Pistol]: at least 30 gallons, live rock, at least 2 in of fine live sand or gravel. Deeper sand where burrows are wanted. Rubble helps burrow stability (forum).
- Smaller tanks: mixed reports (search summary of nano-reef thread; not a primary source).

**Feeding.** Live Aquaria suggests vitamin-enriched mysis and brine shrimp several times per day for the pair (search summary). The shrimp itself is primarily a detritus feeder per WP-AR.

**Reproduction.** GAP.

**Price, US retail (2025 to 26).**
- A. bellulus (tiger pistol): $39.99 to $49.99 (Aquatic Sunlimited, search summary; undated).
- A. randalli (candy pistol): listed on Aquatic Sunlimited, price not retrieved.
- Goby pair: GAP.

**Minimum tank.** 30 gal with 2 in of sand [LA-Pistol]. Est. 40 gal for a sand-burrowing pair.

---

## Gaps (could not source; do not use as fact)

- Sexy shrimp tail-wave rhythm frequency (Hz) and leg-dance timing.
- Pistol shrimp snap rate per second, and whether A. randalli's enlarged claw is the right side.
- Pistol shrimp locomotion speed and burrow dimensions.
- Fire shrimp swim speed and tail-flip escape speed.
- Fire shrimp egg-carrying and temperature.
- Harlequin claw measurements, antenna length, and sexual size ratios beyond "slightly smaller."
- Harlequin starfish flip-over and multi-day carry sequences (only the 30 to 60 min lifting phase is sourced).
- Sexy shrimp activity pattern (day/night).
- Predation of any of the four species by hawkfish, dottybacks, or wrasses (not found).
- Copper, nitrate, and molt intervals for harlequin and pistol shrimp.
- Reliable Asterina consumption per week for harlequin.
- LiveAquaria 2025 to 26 price confirmations (page returned HTTP 429, not retrieved).

## Practical sim defaults (est.)

| Species | Body length | Min tank (pair/group) | Notes |
|---|---|---|---|
| Fire (L. debelius) | 3 cm | 30 gal pair | hermaphrodite, territorial |
| Harlequin (H. picta) | 5 cm | 10 gal pair (20 gal comfortable) | starfish only |
| Sexy (T. amboinensis) | 1.3 cm | 5 to 10 gal group of 3 | anemone host |
| Pistol (A. randalli) | 3 cm | 30 gal, 2 in sand | goby partner |
