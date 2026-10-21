# Research note 42: Red fairy wrasse, yellow tang, purple tang

Scope: realistic-behavior and pricing inputs for the simulation and shop. Confidence tags: H = multiple consistent sources or a primary/reference record; M = one reputable source or consistent retailer data; L = single forum/retailer claim, or conflicting sources. Prices are US retail snapshots from the search date (2026-10-20 session); none of the price sources is dated 2026, so re-check before shipping prices.

## 0. Species pick: "Red fairy wrasse"

**Pick: *Cirrhilabrus rubripinnis* (Redfin fairy wrasse), H for identity, M for trade match.**

Why: the trade name "Redfin"/"Red fin" maps directly to the species epithet (*rubripinnis*, "red fin"). FishBase ties the name to red dorsal, anal and pelvic fins in males [S1]. The species is widely sold under that name by US retailers [S3][S4][S5].

Alternatives and why they lose:
- *C. solorensis* ("red-head solon"): a different trade name, with a red head rather than red fins. Not the best match for "Red fairy wrasse" [S8].
- *C. rubriventralis* (Longfin fairy wrasse): trade name carries "longfin", so it is a different listing [S4].
- *C. rubrimarginatus*: no usable aquarium-trade source found in this pass.

Caveat: a LiveAquaria listing titled "Red-Fin Fairy Wrasse" names the species *C. adornatus* [S3]. Trade labels are unreliable; the sim should key the species on *C. rubripinnis* and treat the listing as a retail mislabel.

## 1. Red fairy wrasse (*Cirrhilabrus rubripinnis*)

| Item | Value | Conf | Source |
|---|---|---|---|
| Max length | 9.1 cm TL (male/unsexed) | H | [S1] |
| Retailer max size | about 4 in (about 10 cm) | M | [S2] (conflict, see note) |
| Depth range | 2-40 m | H | [S1] |
| Habitat | Philippines, Indonesia; rubble or low patch reefs in areas of current | H | [S1] |
| Diet (wild) | zooplankton in the water column (trophic level 3.1) | H | [S1] |
| Min tank | 90 gal | M | [S2] |
| Social structure | one male with several females; "small groups, each with a single male dominating" | H | [S1] |
| Price (USD) | $59.99 (LiveAquaria, sold out), $69.99 (That Pet Place, in-store only), $104.99 (Aquatics Unlimited) | M | [S3][S4][S5] |
| Price by sex | not published separately; sellers price one fish and describe sex in the listing | M | [S3][S5] |

Note on size: FishBase gives 9.1 cm TL; retailers say about 4 in. The sim should use 9-10 cm.

**Male vs female coloration**
- Male: much larger, bright red dorsal and anal fins that spread during display; red pelvic fins with very long filaments [S1]. Confidence H for the body/fin description, M for the fin-by-fin split.
- Female: more yellow/subdued; the sources say female and juvenile coloration is "more subdued" [S5][S4]. No source gives a full female fin-by-fin description (gap). Pelvic-fin coloring in females is not stated [S1]. Confidence L.

**Male flashing display**
- Males brighten and flash rapidly during displays; retailers place this mostly at dusk, but the sources disagree on timing. One source calls the display diurnal [S9][S6]. Confidence L on timing, M on the existence of the display.
- Behavior: fins erect, colors intensify, rapid looping swims [S9]. Confidence M.

**Sex change (protogyny)**
- *Cirrhilabrus* are protogynous. Females start female; the dominant female can become male when the male is lost [S10][S7]. Confidence M.
- Wild timing: after a dominant male disappears, the largest female "may change sex within a few weeks" in protogynous haremic fish generally [S30]. This is not a *Cirrhilabrus* study. Confidence L for the species.
- Aquarium anecdote: a lone female "may take months" to change [S11]. Hobbyist opinion, not data. Confidence L.
- Staged change: the dominant female starts behaving as male within hours of the male's loss [S30]. Confidence L.
- Size/social trigger: in a related wrasse (*Pseudolabrus sieboldi*), the largest of six females changed; with two females no change occurred [S10]. Confidence M for the mechanism.
- Reversibility: a different angelfish (*Centropyge ferrugata*) took 47-89 days to reverse gonadal sex [S30]. Not a wrasse. Confidence L.

Sim recommendation: a lone female should not switch in a short session. Use a multi-week timer (about 3-6 weeks) if modeled at all, with an explicit L label.

**Feeding**
- Wild: midwater zooplankton, picked from current [S1]. Confidence H.
- Aquarium: meaty foods (brine, mysis) plus algae-containing foods such as spirulina [S2]. Confidence M.
- Sim: midwater feeder, orients to current, does not graze the rock.

**Sleep**
- Sleeps in rock crevices, not in sand. Some sources describe a mucus cocoon that masks scent from predators [S7][S8]. Confidence M for the cocoon existence, L for the exact timing and build duration (no timed source found; build time not verified).
- Forum report: a startled fish may leave its cocoon and not rebuild it [S11]. Confidence L.
- Sim: night state = in-crevice; optional cocoon animation only.

**Jumping**: retailers say they are jumpers and need a tight-fitting lid [S2][S5][S8]. Confidence M. No measured frequency found; do not model a jump rate without a source (use a per-hour low-probability spook under disturbance only, labeled L).

**Aggression**
- Peaceful and reef-safe; may pick at small crustaceans [S2]. Confidence M.
- Males compete with each other; one male per group in the wild and in tanks [S1][S2]. Confidence H for the single-male group; M for male-male aggression in captivity.

**Swimming**: labriform (pectoral-fin driven). Not verified in a dedicated source in this pass. Confidence L. The modeling choice follows from the fish's hovering/current-holding habit [S1].

**Water**: temperature and specific-gravity ranges not verified from a reliable page (the reefs.com fetch returned 403; LiveAquaria fetch returned 404). Use a standard reef range (about 24-27 C, SG 1.024-1.026) as an L-confidence placeholder and flag it.

## 2. Yellow tang (*Zebrasoma flavescens*)

| Item | Value | Conf | Source |
|---|---|---|---|
| Max length | 20.0 cm TL (male/unsexed) | H | [S12] |
| Retailer max size | 8 in | M | [S13] |
| Depth range | 2-46 m; adults from below the surge zone to about 46 m | H | [S12] |
| Diet | mainly filamentous algae (small numerous pharyngeal teeth, trophic level 2.0) | H | [S12] |
| Lifespan | oldest collected individual 41 yr (Hawaiian aquarium fishery study) | M | [S19] |
| Min tank | 100 gal (LiveAquaria, Fish Tank Advisor, Reefs.com); outliers 80 gal and 125 gal | M | [S13][S14][S15] |
| Temperament | semi-aggressive; keep one per tank | M | [S13] |
| Water | 72-78 F, SG 1.020-1.025, pH 8.1-8.4, dKH 8-12 | M | [S13] |
| IUCN | Least Concern (assessed 2010) | H | [S12] |
| Price (USD) | captive-bred Biota about $149-199 (2021); no 2026 price found | M (dated) | [S16][S17] |
| Price (wild) | not verified; pre-ban wild was much lower (about $40 cited) | L | [S19] |

**Price context**: Hawaii's wild-collection restriction (reported 2021) pushed prices up; the captive-bred Oceanic Institute program is commercial [S17][S19]. Confidence M for direction, L for magnitude.

**Grazing**
- Constant algae picking on rock/herbivore grazing; needs regular algae (dried seaweed clip, at least 3 feedings/week) [S13]. Confidence M.
- Bites per minute: not found. Perevolotsky et al. (2020) measured bite performance per bout in *Zebrasoma* on the reef, not per minute, and did not confirm *Z. flavescens* in the abstract [S28]. Confidence L. Sim should not claim a bite rate from this source.

**Night behavior**
- Night color changes from bright yellow to a darker/grayer yellow with a white lateral stripe [S18]. Another source says brown night color [S18]. Confidence L on the exact shade; M that color shifts at night.
- Night shelter: seeks shelter in deeper reef parts at night [S18]. Confidence M. Direct source for "sleeps wedged in crevice": not found. Confidence L.

**Aggression**
- Territorial toward its own kind and other tangs; keep one per tank [S13]. Confidence M.
- Tail spine: sharp, scalpel-like spine on each side of the caudal peduncle, used in threats [S22][S24]. Confidence M (purple tang sources; applies to the genus).
- Tail-slapping and shimmy/shimmying-up displays: described in purple tang sources as aggressive signals [S22]. Confidence L for yellow tang specifically.
- Blue tang (*Paracanthurus hepatus*) interaction: not found in this pass. Confidence none. Do not model without a source.

**Disease**
- Ich: general tang warning that low swim space increases ich risk [S21][S22]. A yellow-vs-blue ich comparison was not found. Confidence L on the relative claim.
- HLLE: vendor sources say tangs fed mainly meat over long periods are more likely to get HLLE [S24]. Confidence L (vendor).

## 3. Purple tang (*Zebrasoma xanthurum*)

| Item | Value | Conf | Source |
|---|---|---|---|
| Max length | 36.7 cm TL (FishBase, male/unsexed); common length 10.0 cm | H (FishBase) | [S20] |
| Conflict | Jungledragon says about 25 cm (unsourced); UltimateReef about 37 cm | L | [S31] |
| Depth range | 2-20 m (usually) | H | [S20] |
| Range | Western Indian Ocean, Red Sea to Persian Gulf; Maldives | H | [S20] |
| Diet | mainly filamentous algae (trophic level 2.0) | H | [S20] |
| Min tank | 125 gal (Reef Builders, Reef Chasers); 100 gal minimum by expert guides; 130 gal for adults (vendor claim) | M | [S21][S22] |
| Temperament | semi-aggressive; should not be housed with other Zebrasoma; usually peaceful with non-Zebrasoma after pecking order | M | [S22][S24] |
| Price (USD) | captive-bred Surge Marine Life $249 shipped (Apr 2025); Frags2Fishes "Red Sea" $179.99; Aquatics Unlimited $279.99-399.99 (mixed); wild often $200+ | M | [S23][S26][S25] |
| IUCN | Least Concern (assessed 2010) | H | [S20] |

Colors (purple body, yellow tail, dark spots on head): not verified against a source in this pass. Use the species-typical description from the trade pages; confidence L.

**Aggression**
- Pugnacious toward conspecifics, congeners and similarly shaped fish [S22]. Confidence M.
- Introduce last; acclimation box 2-7 days so residents see but cannot attack [S22]. Confidence M.
- Tail spine on each side of the caudal peduncle [S22]. Confidence M.
- Frequent seaweed feeding reduces aggression (retailer claim) [S22]. Confidence L.

**Water/husbandry**: high oxygen and turbulent circulation are "critical," plus frequent water changes and skimming [S21]. Confidence M.

**Disease**: HLLE susceptibility is reported when long-term care needs are not met [S26][S29]. Confidence L (vendor sources).

## 4. Blue tang vs Zebrasoma, and add order

- Zebrasoma vs Zebrasoma: keep one per tank; sources agree (M).
- Purple tang vs yellow tang: purple is the more aggressive; sources say purple tangs should not be housed with other Zebrasoma [S22][S24] (M).
- Blue tang (*Paracanthurus hepatus*): no source found in this pass on blue-tang temperament, spine behavior or interaction with Zebrasoma. Gap. Do not model a blue-vs-Zebrasoma rule without a new source.
- Add order advice: add Zebrasoma last, after the other fish, with an acclimation box for purple tangs [S22] (M).

## 5. Gaps still open

1. Female red fairy wrasse coloration (pelvic fins, body): not found in a source beyond the red dorsal/anal fins.
2. Sex-change timeline for *Cirrhilabrus* in aquaria: no controlled data.
3. Cocoon-building duration: no timed source.
4. Bites per minute for yellow/purple tang: not found.
5. Blue tang interactions: not found.
6. Red fairy wrasse water parameters: not verified from a reliable source.
7. 2026 retail prices: none found; all prices are 2021-2025 snapshots.
8. Purple tang color description: not verified.

## Sources

- [S1] FishBase, *Cirrhilabrus rubripinnis* summary (Randall & Carpenter 1980): https://fishbase.se/summary/SpeciesSummary.php?id=13042
- [S2] Reefs.com, Redfin wrasse: https://reefs.com/fish/redfin-wrasse/ (fetch returned 403; claims from search snippet)
- [S3] LiveAquaria, Red Fin Fairy Wrasse: https://www.liveaquaria.com/products/red-fin-fairy-wrasse (price and species-name note; fetch returned 404 on the product URL)
- [S4] That Pet Place, Redfin Fairy Wrasse (adult): https://www.thatpetplace.com/Redfin-Fairy-Wrasse-Cirrhilabrus-rubripinnis-216706
- [S5] Aquatics Unlimited, Wrasse Fairy Redfin: https://aquaticsunlimited.com/product/wrasse-fairy-redfin-cirrhilabrus-rubripinnis/
- [S6] A-Z Animals, Labouts fairy wrasse (timing conflict): https://a-z-animals.com/animals/labouts-fairy-wrasse-2/
- [S7] TFH Magazine, Cirrhilabrus: The Fairy Wrasses: https://www.tfhmagazine.com/articles/saltwater/cirrhilabrus-the-fairy-wrasses-full-article
- [S8] Practical Fishkeeping, Keeping the red-headed fairy wrasse: https://www.practicalfishkeeping.co.uk/features/keeping-the-red-headed-fairy-wrasse/
- [S9] Reef Builders, Reef Nuggets 3: fairies and flashers: https://reefbuilders.com/2014/10/20/reef-nuggets-3-fairies-flashers-notes-wild
- [S10] Frontiers in Marine Science 2022, sexual plasticity in protogynous wrasse: https://frontiersin.org/journals/marine-science/articles/10.3389/fmars.2022.1048506/text
- [S11] Reef2Reef forum, hobbyist anecdote on protogyny and cocoons (opinion, L): https://www.reef2reef.com/threads/lineatus-wrasse.145100/post-1689153
- [S12] FishBase, *Zebrasoma flavescens* summary: https://www.fishbase.se/summary/SpeciesSummary.php?id=6018
- [S13] LiveAquaria, Yellow Tang Hawaii: https://www.liveaquaria.com/products/yellow-tang-hawaii
- [S14] Fish Tank Advisor, How to Properly Care for Yellow Tang: https://fishtankadvisor.com/?p=3507
- [S15] Reefs.com, Yellow Tang: https://reefs.com/fish/yellow-tang/
- [S16] Reefs.com, Biota captive-bred yellow tang: https://reefs.com/fish/biota-captive-bred-yellow-tang/
- [S17] Reef Builders, Captive-bred yellow tangs are now a much more reasonable price (2021): https://reefbuilders.com/2021/09/29/captive-bred-yellow-tangs-are-now-a-much-more-reasonable-price
- [S18] Animal Diversity Web, *Zebrasoma flavescens*: https://animaldiversity.org/accounts/Zebrasoma_flavescens (night color; snippet only)
- [S19] Oceanic Institute / HPU, yellow tang program: https://hpu.edu/oi/yellow-tang.html
- [S20] FishBase, *Zebrasoma xanthurum* summary: https://www.fishbase.se/summary/12023
- [S21] Reef Builders, Purple tang (*Zebrasoma xanthurum*) overview (2013): https://reefbuilders.com/2013/04/20/purple-tang-zebrasoma-xanthurum-overview
- [S22] Reef Chasers, Purple tang care guide: https://reefchasers.com/blogs/reef-chasers-marine-fish-guides/purple-tang-care-guide
- [S23] CORAL Magazine, Surge Marine Life purple tang breeder (2025): https://www.coralmagazine.com/2025/04/19/surge-marine-life-becomes-newest-purple-tang-breeder/
- [S24] That Pet Place, Purple tang: https://www.thatpetplace.com/Purple-Tang-Zebrasoma-xanthurum-213756
- [S25] Aquatics Unlimited, Tang purple: https://aquaticsunlimited.com/?p=9794
- [S26] Frags2Fishes, Purple tang (Red Sea price): https://frags2fishes.com/product/purple-tang/
- [S28] Perevolotsky et al. 2020, *Proc. R. Soc. B*, feeding biomechanics of wild *Zebrasoma*: https://par.nsf.gov/biblio/10276352-work-body-fin-body-movements-determine-herbivore-feeding-performance-within-natural-reef-environment
- [S29] Seascape Studio, purple tang: https://seascapestudio.net/reference/fish.php?id=25
- [S30] Reef Builders, Sequential hermaphrodites (protandrous/protogynous): https://reefbuilders.com/2014/11/13/sequential-hermaphrodites-protandrous-protogynous
- [S31] Jungledragon, Purple tang: https://www.jungledragon.com/specie/5523/purple-tang.html (size conflict; unsourced claim)

## Game prices (My tank economy, docs/ECONOMY.md scale: about 1 sand dollar per US$)

| Shop item (stock key) | Level | Price | Why |
|---|---|---|---|
| Red fairy wrasse (female), `cirrhilabrus_rubripinnis@female` | 4 | 55 | US retail $60-105 for one fish; females sell for less than colored-up males |
| Red fairy wrasse (male), `cirrhilabrus_rubripinnis@male` | 5 | 85 | the colored-up male, sold grown |
| Yellow tang, `zebrasoma_flavescens` | 5 | 150 | captive-bred $149-199 since the Hawaii collection stop |
| Purple tang, `zebrasoma_xanthurum` | 6 | 250 | $180-400 Red Sea / captive-bred; Deep Reef Imports item |

A male bought as a male is added with role MALE at adult size (`Stocking.sizeFor`), so he shows male colors and fins at
once. Tank sizes: yellow tang 380 L (100 gal), purple tang 475 L (125 gal), fairy wrasse 200 L (`LifeCycle.minVolumeL`,
`Compatibility.FAIRY_WRASSE_MIN_L`).
