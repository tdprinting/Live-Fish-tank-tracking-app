# 43. Cleanup crew pack: emerald crab, tuxedo urchin, fighting conch, trochus snail

Added 2026-10-20 (thread "Popular invertebrates pack"). Tee asked for the most popular reef invertebrates the game did
not have yet, built with the same research-backed care as the rest of the tank. Sections 1-4 below are the source
notes; this first section says what the sim takes from them and what is a design estimate.

## 0. What the sim uses

| Species | Sheet | Brain / rig | From the sources | Design estimates (no data found) |
|---|---|---|---|---|
| Trochus snail | `tectus_fenestratus` | `TrochusBrain`, `SnailRig` (fish.vert mode 8) | trade size 2.5-4 cm (max 7, SeaLifeBase); film and diatom grazer on glass and rock; copper-sensitive; spawns at dusk (FAO, *T. niloticus*) | glide speed; righting odds (0.55 with rock or glass in reach, 0.25 on open sand, attempts every 3-12 min: keepers say trochus right themselves and astreas often cannot, but no controlled data); withdraw time |
| Fighting conch | `strombus_alatus` | `ConchBrain`, `SnailRig` (mode 8, vault) | herbivore of diatoms and epiphytes on sand (FWC, SeaLifeBase); sickle operculum and leaping foot (SeaLifeBase; Berg 1974 cited second hand); one per 30 gal (seller) | leap length 0.35 shell lengths, 0.8 s reach + 0.4 s vault, 1.5-7 s pauses; buried bouts 1-6 h by day when sated (hobby anecdote only); escape leaping |
| Tuxedo urchin | `mespilia_globulus` | `UrchinBrain`, `UrchinRig` (mode 9) | test 5-8 cm; grazes film and coralline; covering reaction in other urchins tracks UV more than PAR (Adams 2001, Sigg 2007) | cover curve with light (0.15 dark to 0.9 bright, one piece per 2-8 min); crawl speed; no Mespilia-specific covering study exists |
| Emerald crab | `mithraculus_sculptus` | next PR | ~4 cm, nocturnal, scavenger that also eats algae; pellets taken over mysis in choice tests (Figueiredo 2008); nips polyps only when underfed (LiveAquaria) | bubble algae rate; aggression odds |

Grazing rows (`GrazerType.CONCH`, `GrazerType.URCHIN`, AlgaeModel) and the coralline loss to the urchin are design
estimates sized against the existing research 09 rows. Prices are set by the economy thread from the retail points in
each section.


Compiled 2026-10-20. Rule applied: every number carries a source number in brackets [n] (see the source list at the end). Numbers with no source found are marked **no data found**. Colours marked "estimate" are my visual estimates, not sourced.

Access note: several primary or key pages were unavailable during the session. SeaLifeBase species pages for *Mithraculus sculptus* and *Mespilia globulus* returned HTTP 503 or 403, so their figures come only from search snippets. The Berg (1974) paper and the Journal of Marine Biological Association full text could not be retrieved. Some retailer pages returned HTTP 404 or 429.

---

## 1. Emerald crab, *Mithraculus sculptus* (Majidae, Mithracinae)

### Size, lifespan, habitat, activity
- Adult size: about 4 cm long [6, from Colin 1978 cited therein]. Carapace is longer than it is wide, flat, shiny green, finely sculpted [6]. Carapace width: **no data found** in sourced form.
- Lifespan: **no data found**.
- Depth: down to about 54 m (177 ft) [6, Colin 1978]. SeaLifeBase depth figure for this species: **no data found** (page unavailable).
- Habitat: Bahamas and southern Florida to northern Brazil, Caribbean and Gulf of Mexico [6]. Often among branches of *Porites furcata*; "may be most abundant in back reef environments" [6].
- Day/night pattern: "largely nocturnal and hides in caves, crevices, and under rocks by day" [6]. This sentence is uncited in the source, so treat it as lower confidence.

### Diet
- Wild: "principally a scavenger but it also feeds on algae" [7, LiveAquaria]. Feeds on organisms encrusting turtle-grass leaf blades [6, Colin 1978].
- Aquarium: eats bubble algae and leftover meaty food. If underfed it may eat coral polyps or small fish [7].
- Bubble algae (*Ventricaria ventricosa*, syn. *Valonia ventricosa*) control, Figueiredo et al. 2008 [1,2]: when offered alternatives, crabs first chose pellets 77% of the time and frozen mysids 69% of the time. Algal consumption dropped significantly only when pellets were provided. Algae-alone and algae-plus-mysid consumption were similar. Medium and large crabs ate more algae than small ones. Handling time fell with size. The abstract gives **no grams or area per day** and no crab sizes; full text is paywalled. The authors conclude the crab "might not be such an efficient bio-controller" [1].
- Grazing rate (g/day or area/day): **no data found**.
- Coral effects: LiveAquaria says it nips polyps when underfed [7]. Quantitative rates: **no data found**. A conference abstract (Figueiredo et al. 2006, via bepress) says the algae's liquid may release spores when torn [2, unverified summary].

### Locomotion
- Speed: **no data found**. Gait: clings to substrate with legs and tolerates strong currents [8, Reeftopia, uncited in this summary].

### Behaviour
- Hiding: by day in crevices (see above) [6, 8].
- Territoriality and aggression: crowding and too few caves trigger attacks on other crabs, shrimp, snails and fish [9, retailer/care-guide]. Stocking advice conflicts: "at least 20 gallons" per crab [9] vs one crab per 10 gallons reported to work [9]. Males may be more combative [9].
- Predation: may prey on small snails, hermit crabs and small shrimp [10, EWASH]. Larger hermits may compete with or prey on it [10].
- Hides under extended sun-anemone tentacles when threatened [6, Colin 1978].
- Molting: every 4 to 8 weeks depending on size and feeding [9]. Freshly molted crabs are soft and vulnerable; leave the exuvia in the tank [9].

### Water tolerance
- Temperature 75–82 °F; specific gravity 1.024–1.026 [1-2 search summary, TFH Magazine or care-guide source, not verified in full text].
- Iodine: appears only as a generic supplement [7]. No crab-specific iodine or molting requirement found.
- Copper, nitrate, low pH: **no data found**.
- Calcium/alkalinity: not applicable (decapod, not a calcifier of its shell in the same way). **no data found**.

### Predators and threats (aquarium)
- Hungry crab: attacks coral polyps and small fish [7].
- Larger hermit crabs compete or prey on it [10].
- Freshly molted crabs are vulnerable to any tankmate [9].

### Aquarium care
- Tank: about 20 gallons per crab recommended [9]; some keepers stock higher. Reef safe: **qualified** (nips when hungry) [7].
- Difficulty: **no data found** in a source.

### US retail price (2025–2026)
- LiveAquaria: $11.99 regular, sometimes $9.59 sale; sold out in several cached versions [11].
- Reef Exclusive: $12.00, sold out [12].
- Aquarium Depot (Canada): $19.99, currency not stated [13].
- Note: the LiveAquaria product page returned 404 when fetched directly, so prices come from search snippets. No dated 2025–2026 price found for Saltwaterfish.com or Blue Zoo.

### Appearance for 3D modelling
- Carapace: green, flat, shiny, finely sculpted; longer than wide [6].
- Chelae: spoon-shaped, green, tipped white [6].
- Walking legs: paler, hairy, often encrusted [6].
- Estimated hex: carapace #2E8B57 (estimate), claw tips #F5F5F0 (estimate).
- Leg count and spine rows: **no data found** in sourced form (decapod default: 5 pairs of walking legs, a pair of chelipeds). This is general biology, not sourced here.

---

## 2. Tuxedo urchin, *Mespilia globulus* (Temnopleuridae)

### Size, lifespan, habitat, activity
- Test diameter: SeaLifeBase max length 5.0 cm [14, search snippet; full page 403]. Retailer guide: 5–7 cm [15]. Solitary Islands Underwater Research Group: about 80 mm [16]. Retailer: captive specimens rarely exceed 8 cm [17]. LiveAquaria: max size 3 inches [18]. Sources disagree.
- Lifespan: **no data found**.
- Depth: SeaLifeBase header 0–80 m; habitat text 0–60 m (internal conflict) [14]. Solitary Islands group: down to 151 m [16]. Marinelifephotography.com: "common on reefs and rubble at any depth" [19].
- Habitat: Indo-Pacific. Shallow reef, coral rubble, seagrass beds [14]. Indonesia, Philippines, parts of Australia [16].
- Day/night activity: **no data found**.

### Diet
- Algae grazer per retailers [18, 20]. Quantitative diet or grazing rate: **no data found**.
- Coralline algae and film: **no data found** (no sourced study).
- Coral effects: can damage delicate corals if it crawls over them [20]. Reef Builders article "Don't let urchins carry corals around your tank" (2020) is a source for this theme [21]; full text not read.

### Locomotion
- Tube feet speed (cm/s or cm/min): **no data found**.

### Behaviour: covering / masking
- **No Mespilia-specific masking study was found.** Retailers say it carries shells, rubble or frags on its tube feet as camouflage [17, 19] (care guides, not controlled studies).
- Comparative sea-urchin literature:
  - Adams 2001 (*Strongylocentrotus droebachiensis*, MEPS 213): covering tracked UVB and UVA irradiance, not PAR, so the urchin shelters from UV damage [22].
  - Sigg, Lloyd, Knight & Boal 2007 (JMBA): urchins choose objects non-randomly. Under UV they chose intermediate-sized items as a trade-off between carrying cost and UV exposure [23].
  - Lytechinus variegatus (uncited in the snippet): during UV exposure urchins used significantly more shells by number, area and mass [24].
  - Dumont et al. 2007 (cited in 2014 review): covers may also protect from desiccation or act as ballast in turbulent water [24, secondhand].
  - Verling et al. 2002 (*Paracentrotus lividus*): light versus covering, cited secondhand [24].
  - A 2014 paper reports that light and most shallow-water hypotheses fail to explain covering in deep-sea echinoids [25].
  - Millott (1975) explanation was oversimplified per Adams 2001 [22, secondhand].
- IntechOpen chapter reviews covering across 15 genera including *Mespilia* and lists four hypotheses: predator defence, protection from mechanical damage, food, shielding from bright light [26, secondhand].
- Kehas 2005: **not found**.
- Tube-foot response to light: **no data found** for Mespilia.

### Water tolerance
- Calcium and alkalinity: **no data found** from a source for this species.
- Copper, nitrate, pH, temperature: **no data found**.

### Predators / threats
- **no data found** for fish or invertebrate predators. Aquarium hobby sources say it is reef-compatible [18] and that it can dislodge frags [20].

### Aquarium care
- Reef compatible: yes [18]. Tank size, stocking density, difficulty: **no data found**.

### US retail price (2025–2026)
- LiveAquaria: $27.99, sold out; purchase size 1"–3" [18].
- Aquarium Depot (Canada): blue variant $49.99 sale; red $29.99 (regular $49.99); shipping extra [27].
- Reefchasers: Royal (Tuxedo) Urchin $39.99 sale, 1/2" size [28].
- Reeftopia (Australia): $45.00, currency unclear [29].
- That Fish Place: $34.99 and $44.99, store-only [30].
- No dated 2025 or 2026 price confirmed.

### Appearance for 3D modelling
- Bare ambulacral bands blue or black, alternating with spiny interambulacral tracts; short orange or red-tipped spines [17, 27]. Visual description from retailer material. Exact band count (10 bare + 10 spiny suggested by user): **no data found** in sources.
- Estimated hex: bare band #1B2A4A (estimate), spine tip #E0572A (estimate), test #101820 (estimate).

---

## 3. Florida fighting conch, *Strombus alatus* (Strombidae)

### Size, lifespan, habitat, activity
- Shell length: FWC says up to 4 in (100 mm) [31]. SeaLifeBase: max length 11.2 cm (Ref 83435), common length 13.0 cm (Ref 355), with a noted 1967 error (117 mm should be 112 mm) [32]. Record specimen 118.8 mm (Jacksonville-area collectors) [33, secondhand]. Sources conflict; use 10–13 cm as the range.
- Lifespan: **no data found**.
- Depth: SeaLifeBase 0–183 m (Ref 83435) [32]. Winter deep (≥10 m), summer shallow (2 m) to spawn [33, SeaLifeBase snippet].
- Habitat: seagrass beds, shallow reefs, sand and rubble [32, 33]. Florida waters; mass wash-ups on Gulf beaches after storms [31].
- Day/night pattern: **no data found** from a peer-reviewed source. Aquarium forum reports sand burial for hours to days [34, anecdotal].

### Diet
- FWC: "herbivore (plant eater)" [31].
- SeaLifeBase: feeds on diatoms and other epiphytic algae [33].
- Detritus and microalgae: a study of *S. turturella* (dog conch, Indonesia) found detritus and microalgae including diatoms [35]. Different species, indirect evidence only.
- Aquarium: needs a large amount of plant matter and may starve if out-competed [36, seller].
- Grazing rate: **no data found**.
- Coral effects: none reported; described as reef-safe [36].

### Locomotion
- Operculum: sickle-shaped [32, no ref]. Elongate foot used for leaping [32, no ref].
- Berg 1974: **not verified**. Secondhand citations only: "leaping surface locomotion of the Strombidae (Berg 1974)" [37] and operculum serrated and functional in righting and locomotion (Berg 1974) [37, secondhand]. The 1974 paper itself was not retrieved.
- Berg 1971 (Hawaiian *S. maculatus*): leaps, rights itself by kicking the substrate with the operculum [38, secondhand].
- Parker (1922) account of *Titanostrombus galeatus*: plants operculum tip in sand, extends foot, throws shell forward [39, secondhand].
- Leap frequency, distance per leap, speed (cm/s): **no data found**.

### Behaviour
- Burying: captive hobby observations of hours to days in sand [34, anecdotal]. Day/night burial timing: **no data found** for this species. *S. gibberulus* hobby guide claims burial at night and for days [40, care guide].
- Eyestalks and proboscis: males fight with proboscis in sparring contests [32, Refs 105242, 105282].
- Predators: avoid predatory fish and hermit crabs; large openings are vulnerable [36, seller].
- Breeding: Florida fighting conch captive breeding at Harbor Branch from 2000 [41, Duke thesis]. Gillette & Shawl 2006 (GCFI): adult diet and sex ratio (1:1 vs 2:1) effects on reproductive output [42].

### Water tolerance
- Temperature, salinity, copper, pH, calcium: **no data found** for this species.

### Aquarium care
- Tank: one conch per 30 gallons, territorial males [36, seller].
- Growth in aquarium: up to about 5 in (127 mm) [36, seller]. Caution with loosening rockwork [36].
- Difficulty: **no data found**.
- Wild collection: Florida has no specific law for this species, but FWC-linked guide advises leaving them in place [36]. Queen conch is protected [36].

### US retail price (2025–2026)
- Reef Exclusive: $12.00, 2-inch, listed as *Strombus* spp. [43].
- Reef by Steele: $20.00 [44].
- That Fish Place: $35.99 medium/large, store-only [45].
- No dated 2025–2026 Saltwaterfish.com, LiveAquaria, Reef Cleaners or Blue Zoo price found.

### Strawberry conch, *Conomurex luhuanus*
- **Not researched in this batch.** No data found.

### Appearance for 3D modelling
- Flared outer lip, often dark [31]. Spines on the spire top [31]. Smooth body whorl [31].
- Eyestalks and ringed eyes: **no data found**.
- Operculum: sickle-shaped [32].
- Estimated shell colour: pale cream to brown (estimate #C8A97E); lip dark (estimate #5A3A22).

---

## 4. Trochus snail (as sold: *Trochus* spp., *Tectus fenestratus*, *Trochus histrio*)

### Size, lifespan, habitat, activity
- *Tectus fenestratus*: max shell height 7.0 cm; common shell length 5.0 cm (males or unsexed) [46, SeaLifeBase; Ref 349 Poutiers 1998 FAO]. Retailer: 2.5 in (about 6.4 cm) max in captivity [47]. European seller: 4 cm [47].
- *Trochus niloticus* (aquarium "banded/red banded" trochus): one retailer says specimens lived over 15 years [48, unsourced claim].
- Depth: 0–5 m, benthic [46, Ref 101210].
- Habitat: rocky shores, muddy areas [46]. Tropical Indo-West Pacific [47].
- Day/night pattern: **no data found**.

### Diet
- Herbivore, scrapes algae; prefers green algae; takes diatoms and cyanobacteria in lesser amounts; detritus also eaten [47, retailer].
- Trochus: prefers diatoms, film algae, biofilm; does not eat *Chaetomorpha* or *Caulerpa* [48, retailer].
- Supplements: dried seaweed or nori; algae wafer [47, 48].
- Grazing rate: **no data found**.
- Coral effects: none; reef safe [48].

### Locomotion
- Speed: **no data found**.

### Behaviour: righting
- Retailers say trochus can right themselves when flipped and that this reduces predation [49, marketing claim]. Not tested in a controlled study.
- Aquarist reports: they land on their backs repeatedly; on sand they sometimes cannot right [50, anecdotal].
- Turban snail (*Tectus*) righting: **not confirmed** by sources.
- Turbo/astrea comparison: **no data found**.
- Scientific righting time for *T. niloticus*: **no data found**. A 2025 classroom sheet defines righting and says delayed righting indicates stress [51, not trochus data].

### Spawning and breeding
- Retailer on Banded Trochus: easy to breed in a home tank; gametes released as cloudy white in the water column; spawning triggered by light or water changes [52]. Larvae settle and take months to mature [52]. Sex cannot be determined by sight [52].
- FAO (commercial *Trochus niloticus*): stop feeding shortly before spawning; more time at waterline; spawn early evening (about 6 p.m.–10 p.m.); males initiate [53]. Broadcast spawner [46].
- A European seller says breeding *Tectus* in the aquarium is "rather impossible" [47]. Conflicts with the retailer claim above.
- Babies on the glass: **no data found** in a source.

### Water tolerance
- Temperature 72–78 °F, pH 8.1–8.4, specific gravity 1.023–1.025 (retailer, unnamed in the summary) [47].
- Very sensitive to copper medications; prefers low nitrate [47].
- Calcium/alkalinity: **no data found** for trochus.
- Iodine, low pH tolerance: **no data found**.

### Predators / threats
- Hermit crabs and some fish can kill snails for shells: **no data found** in these sources for trochus specifically.
- Tuxedo and emerald crab threats to snails: see sections 1–2 [10].

### Aquarium care
- Min tank size 10+ gallons; 1–5 per gallon depending on algae [48, retailer].
- Difficulty: hardy and long-lived [48]; breeding claims conflict [47, 52].

### US retail price (2025–2026)
- Bulk Reef Supply (ORA trochus): 5-pack $41.99 and 25-pack $149.99 in one cached version; newer cache 5-pack $47.88, 10-pack $89.88, 25-pack $169.88 [54]. Captive bred. Flat shipping $39.99 under $179 per partner [54].
- Queen City Corals: Red Banded Trochus 1, 3 or 5 packs, $4.99–$19.99 (date unclear) [55].
- World Wide Corals: 10-lot of *Trochus* sp., sale $68.50 [56].
- Reef Exclusive: single Banded Trochus $5.00, sold out [57].
- Reeftopia: $10.00–$75.00, currency not stated [58].

### Appearance for 3D modelling
- Conical shell, striped colour [46, 47 descriptions]. Height-to-width ratio: **no data found** in sources.
- SeaLifeBase height 7.0 cm max [46] gives a height reference.
- Estimated colour: cream and brown spiral stripes (estimate #D9C7A0 and #6B4E2E); operculum dark (estimate #2B2B2B).

---

## Cross-species notes
- Coral risk ranking from sources: emerald crab (when hungry) [7] > tuxedo urchin (crawling frags) [20] > conch and trochus (none reported) [36, 48].
- Source conflicts to resolve before coding: *S. alatus* size (FWC 100 mm vs SeaLifeBase 112–130 mm); *M. globulus* depth (SeaLifeBase 60 m vs 80 m); *T. fenestratus* captive size (6.4 cm vs 4 cm); trochus breeding in home tanks (claimed easy vs "impossible").

---

## Source list

1. Figueiredo, J., Narciso, L., Turingan, R., Lin, J. (2008). Efficiency of using emerald crabs *Mithraculus sculptus* to control bubble alga *Ventricaria ventricosa* (syn. *Valonia ventricosa*) in aquaria habitats. *J. Mar. Biol. Assoc. U.K.* 88(1):95–101. DOI 10.1017/S0025315408000192. https://www.cambridge.org/core/journals/journal-of-the-marine-biological-association-of-the-united-kingdom/article/efficiency-of-using-emerald-crabs-mithraculus-sculptus-to-control-bubble-alga-ventricaria-ventricosa-syn-valonia-ventricosa-in-aquaria-habitats/639F6330E0A684340BB80510279C03E9
2. Figueiredo et al. (2006) conference abstract, via bepress. https://works.bepress.com/joana-figueiredo/17 and https://works.bepress.com/joana-figueiredo/23 (summary only)
3. Wikipedia, *Mithraculus sculptus*. https://en.wikipedia.org/wiki/Mithraculus_sculptus
4. NSU Works, Marine & Environmental Sciences Faculty Articles. https://nsuworks.nova.edu/occ_facarticles/404 (listing only)
5. TFH Magazine, *Mithraculus (Mithrax) sculptus*. https://www.tfhmagazine.com/articles/saltwater/mithraculus-mithrax-sculptus
6. Colin, P.L. (1978). *Caribbean Reef Invertebrates and Plants* pp. 367–370, as cited in Wikipedia [3] (secondhand).
7. LiveAquaria, emerald crab product information (via search summary; product page returned 404). https://www.liveaquaria.com/products/emerald-crab
8. Reeftopia, emerald crab (uncited, search summary). https://reefexclusive.com/products/emerald-crab (note: Reeftopia listed as cited source in summary; URL not verified)
9. Aquarium Source, "Emerald Crab 101: Care, Molting & Reef Safe Advice." https://www.aquariumsource.com/emerald-crab/ ; Aquariadise, "Emerald Crabs: Beautiful and Fascinating Tank Guests." https://www.aquariadise.com/emerald-crab/
10. EWASH, "What animals eat emerald crabs?" https://www.ewash.org/?p=322398 ; "Do emerald crabs get along?" https://www.ewash.org/?p=446643
11. LiveAquaria, emerald crab listing. https://www.liveaquaria.com/product/628/
12. Reef Exclusive, emerald crab. https://reefexclusive.com/products/emerald-crab
13. Aquarium Depot (Canada), emerald crab. https://aquariumdepot.ca/products/emerald-crab-mithraculus-sculptus
14. SeaLifeBase, *Mespilia globulus* (search snippet; page returned 403). https://sealifebase.org/summary/86764
15. Singapore aquarium retailer guide, Tuxedo Urchin Mespilia (gensou.sg). https://gensou.sg/?p=30652 ; https://gensou.sg/?p=34019
16. Solitary Islands Underwater Research Group, *Mespilia globulus*. https://www.surg.org.au/species/mespilia-globulus-linnaeus-1758/
17. Top Shelf Aquatics, Tuxedo Urchin. https://topshelfaquatics.com/pages/saltwater-invertebrates/tuxedo-urchin (429 on fetch; summary only)
18. LiveAquaria, tuxedo urchin. https://www.liveaquaria.com/products/tuxedo-urchin ; https://www.liveaquaria.com/product/595/
19. Marine Life Photography, *Mespilia globulus*. https://www.marinelifephotography.com/marine/echinoderms/urchins/mespila-globulus.htm
20. Reef Builders (2020), "Don't let urchins carry corals around your tank." https://reefbuilders.com/2020/01/06/dont-let-urchins-carry-corals-around-your-tank
21. Reef Builders article above (theme only; full text not read)
22. Adams, N.M. (2001). Covering behaviour in *Strongylocentrotus droebachiensis*. *Mar. Ecol. Prog. Ser.* 213:87 et seq. https://int-res.com/articles/meps2001/213/m213p087.pdf
23. Sigg, D., Lloyd, A., Knight, J., Boal, J. (2007). *J. Mar. Biol. Ecol.* https://www.millersville.edu/biology/faculty/boal-pdf/sigg-lloyd-knight-boal-jmba-2007.pdf
24. Summary of Lytechinus variegatus and Dumont et al. 2007 via 2014 review (search results). https://link.springer.com/article/10.1007/s12526-014-0202-y (review, includes Verling et al. 2002 summary) — journal details not verified
25. Deep-sea covering paper, 2014 (same Springer link as [24])
26. IntechOpen chapter on cryptic urchin behaviour (15 genera). https://www.intechopen.com/chapters/54868
27. Aquarium Depot (Canada), blue and red tuxedo urchin. https://aquariumdepot.ca/products/blue-tuxedo-urchinmespilia-globulus ; https://aquariumdepot.ca/products/red-tuxedo-urchin-mespilia-globulus
28. Reefchasers, Royal (Tuxedo) Urchin. https://reefchasers.com/products/royal-urchin
29. Reeftopia (Australia), tuxedo urchin. https://reeftopia.com.au/?p=11106
30. That Fish Place, Tuxedo Urchin - Mespilia globulus. https://www.thatpetplace.com/Tuxedo-Urchin-Mespilia-globulus-254005 ; https://www.thatpetplace.com/Mespilia-globulus-tuxedo-urchin-206609
31. FWC, Sea snails (Florida fighting conch). https://myfwc.com/research/saltwater/mollusc/other-molluscs/sea-snails/
32. SeaLifeBase, *Strombus alatus* (Ref 83435, 355, 105242, 105282). https://sealifebase.se/summary/Strombus-alatus
33. SeaLifeBase search snippet, *Strombus alatus* habitat and depth. https://www.sealifebase.org/summary/2797
34. Reef Central forum, *Strombus alatus* behaviour (anecdotal). https://archive.reefcentral.com/forums/archive/index.php/t-380938.html
35. Universitas Islam Negeri Malang, dog conch *Strombus turturella* feeding study. https://ejournal.uin-malang.ac.id/index.php/bio/article/download/4069/5549
36. Reef by Steele, Fighting Conch care notes (search summary). https://acceptance.reach.dog/shop/reef-by-steele/products/fighting-conch
37. Review citing Berg 1974 (Wikipedia, Strombidae and Operculum). https://en.wikipedia.org/wiki/Strombidae ; https://en.wikipedia.org/wiki/Operculum_(gastropod)
38. Hawaiian species account citing Berg 1971 (*S. maculatus*). Source URL not captured in session; treat as unverified.
39. Wikipedia, *Titanostrombus galeatus* (Parker 1922 account). https://en.wikipedia.org/wiki/Titanostrombus_galeatus
40. Care guide on *Strombus gibberulus* burial (hobby). Source URL not captured; unverified.
41. Duke University thesis, "Closing the Cycle: Captive Breeding for the Gastropod Strombus." https://dukespace.lib.duke.edu/items/f0f19276-bf64-48cf-ac39-7e5004f8e85c
42. Gillette & Shawl (2006), GCFI proceedings, "Effects of diet and sex ratio on the reproductive output of the Florida fighting conch, Strombus alatus." https://fau.isle.flvc.org/node/42277
43. Reef Exclusive, fighting conch. https://reefexclusive.com/products/fighting-conch
44. Reef by Steele, fighting conch. https://acceptance.reach.dog/shop/reef-by-steele/products/fighting-conch
45. That Fish Place, Fighting Conch - Strombus alatus. https://www.thatpetplace.com/Strombus-alatus-fighting-conch-242720
46. SeaLifeBase, *Tectus fenestratus* (Refs 349 Poutiers 1998; 101210; 75835; 128042). https://sealifebase.se/Summary/SpeciesSummary.php?ID=86804
47. Aquarium retailer summaries for turban snail (*Tectus fenestratus*): LiveAquaria https://www.liveaquaria.com/products/turban-snail ; Garnelio https://www.garnelio.de/en/tectus-algae-snail-tectus-fenestratum ; Quality Marine https://qualitymarine.com/quality-marine/invertebrates/shell-snails/tectus/turban-57019 ; Kraken Corals https://krakencorals.co.uk/turban-snail.html
48. Bulk Reef Supply, "How To Care For Trochus Snails." https://www.bulkreefsupply.com/content/post/how-to-care-for-trochus-snails
49. Retailers' righting claims (search summary): Reef Exclusive https://reefexclusive.com/products/trochus-snail ; Top Shelf https://topshelfaquatics.com/products/trochus-snail
50. Reef Central and Ultimate Reef threads on trochus: https://forums.reefcentral.com/threads/trochus-snails.2018492 ; https://www.ultimatereef.net/threads/trochus-snails.825921/
51. Science in School, classroom snail righting activity (Nov 2025). https://scienceinschool.org/wp-content/uploads/2025/11/Activity-2-answers-sheet-1.pdf
52. LiveAquaria, Banded Trochus Snail. https://www.liveaquaria.com/products/banded-trochus-snail
53. FAO, *Trochus niloticus* culture guide (spawning behaviour). https://www.fao.org/4/ac902e/ac902e11.htm
54. Bulk Reef Supply, ORA trochus snail listings. https://www.bulkreefsupply.com/ora-trochus-snail-5-pack-ora.html ; https://www.bulkreefsupply.com/ora-trochus-snail-25-pack-ora.html
55. Queen City Corals, Red Banded Trochus. https://www.queencitycorals.com/?p=60729
56. World Wide Corals, Trochus Snails. https://worldwidecorals.com/products/trochus-snail
57. Reef Exclusive, trochus snail. https://reefexclusive.com/products/trochus-snail
58. Reeftopia (Australia), trochus snail. https://reeftopia.com.au/shop/invertebrate/snails/trochus-snail

## Unfinished items (for the next pass)
- Full-text Berg (1974) and Berg (1971) papers for conch leaping speed and frequency.
- SeaLifeBase full pages for *Mithraculus sculptus* and *Mespilia globulus* (diet and depth).
- Peer-reviewed masking study for *M. globulus*; tube-foot speed.
- Trochus righting: Tectus and Trochus controlled data.
- Strawberry conch (*Conomurex luhuanus*): not researched.
- Current 2025–2026 prices from Saltwaterfish.com, Blue Zoo, Reef Cleaners and LiveAquaria (product pages were unavailable).
