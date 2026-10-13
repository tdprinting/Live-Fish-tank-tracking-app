# Microfauna morphology, colour and motion spec (research 16)

Date: 2026-10-13. This file complements `10_microfauna.md` (ecology, population model, sim parameters) and `video/micro_fauna.md` (sim mapping). It does not repeat ecology. It covers anatomy, proportions, colour in life, diagnostic marks, visible motion (gait, beat rates, speeds in body lengths per second), and naked-eye visibility. Read the source tags as follows: **[S]** = source found and read or quoted; **[S-snip]** = figure taken from a search snippet, not opened; **[K]** = general textbook knowledge (no page cited for the exact number); **[E]** = estimate for animation or art, not measured. Hex colours are art estimates from written descriptions unless marked otherwise. No photograph or video was viewed in this pass.

Sources cited in full in section 15.

---

## 0. Conventions for the artist and programmer

- Body length (BL) = base of antenna 1 to base of telson for amphipods and mysids; anterior rostrum tip to caudal ramus tip for copepods; anterior margin to posterior tip for ciliates and rotifers (lorica included where stated).
- "BL/s" = body lengths per second. Convert with the sim's `sizeMm` before drawing.
- Scale reality check for a microscope view: at 40x a 1 mm copepod fills about a third of a 2.5 mm field; at 100x the field is about 0.9 mm wide, so a 0.5 mm copepod crosses it in about 1 s at cruise speed.
- Most reef microfauna are translucent or pale. Diagnostic colour is almost always carried by the gut (food, pigment), by carotenoid, by eye spots, or by symbionts, not by the cuticle.

---

## 1. Harpacticoid copepods (Tisbe, Tigriopus californicus)

### 1.1 Body plan
- Order Harpacticoida. Body is cylindrical or dorsoventrally flattened, not laterally flattened like many calanoids. [K, Boxshall & Halsey 2004]
- Cephalothorax (prosome) with 5 free segments in the adult: cephalosome fused with pedigerous somite 1, then 3 further thoracic segments and a fifth "genital" pedigerous segment. Urosome: 4 to 5 segments including the genital somite in females. [K]
- Caudal rami: two short, paired furcal rami at the posterior end, each with terminal and lateral setae. In harpacticoids the rami are typically at least twice as long as wide, and can be fused to the anal somite. [K; the generic Banyuls description of harpacticoids was not retrieved in full]
- Antennule (A1): short, usually 6 to 9 segments in harpacticoids. Clearly shorter than the prosome in most harpacticoids; it is the first appendage you see when a copepod pauses. [K]
- Antenna (A2): biramous, with an exopod and endopod, used in crawling. Mandibles and maxillipeds in the mouth.
- Swimming legs: 4 pairs of biramous thoracic swimming legs (P1 to P4) plus a reduced P5. P5 is a flat plate in many females.
- Egg sacs: Tigriopus carries one egg sac on each side of the genital somite, shaped as paired rounded sacs ("pockets"), not as long strings. [K]
- Tisbe: Tisbe is a genus of roughly 60 described species, mainly shallow marine benthic. Identification is hard from adult morphology alone: a 2009 phylogenetic study says the differences among tisbids are comparatively small morphologically, and copepodite stages separate species better than adults. [S: Zoological Studies 48(6), 2009, https://zoolstud.sinica.edu.tw/Journals/48.6/780.html]

### 1.2 Proportions (estimates unless marked)
- Tigriopus californicus adult female length: roughly 0.8 to 1.2 mm. [K, widely quoted; a hobbyist source gave "a hundred-something microns to a millimetre"; no primary value fetched]. Use **1.0 mm** as the default adult length [E].
- Prosome:urosome length about 2.5 to 3:1, prosome:width about 2.5:1 for a harpacticoid of this size [E from genus descriptions; not verified for Tigriopus].
- Antenna 1: about 0.3 to 0.4 of prosome length [E].
- Tisbe: a similar cylindrical body, length typically 0.4 to 0.8 mm for adults in aquarium cultures [E; uncertain].

### 1.3 Colour in life
- Tigriopus adults: bright orange-red. The colour is the keto-carotenoid astaxanthin, concentrated along the gut lining and less in the exoskeleton. Under a dissecting scope the gut lining is brightest. [S: bioRxiv 174300, https://www.biorxiv.org/content/10.1101/174300.full.pdf]
- A thesis fed a carotenoid-free yeast diet found the body colour faded to trace levels, but a deep orange-red remained in the eyespot region, and the copepods still completed their life cycle. [S, same source]
- Nauplius eye spot: a single red spot on the dorsal anterior cephalosome that persists in adults. A separate study reports this red is structural, not astaxanthin, because it disappeared in ethyl cinnamate. [S, same source]
- Tigriopus hex estimates: body carapace translucent pale orange `#E8A07A` at 40%-60% opacity; gut lining saturated orange-red `#D9542B`; eye spot `#B3121C`.
- Tisbe: pale, nearly transparent, with a dark gut or food-filled gut appearing grey-green to brown `#7A8A6A` in fed animals [E].
- Gut contents are the main visible diagnostic in aquaria: diatoms and detritus make the gut look dark brown-green; freshly fed animals look darker.

### 1.4 Diagnostic marks
- Orange-red colour and red eye spot (Tigriopus). Tisbe: no red colour, nearly transparent.
- Flattened body in the crawl pose on glass: A1 held forward, A2 used to anchor.

### 1.5 Motion
- Mode: benthic crawl on glass, rock and film algae. The body flexes and the legs alternate. Short upward darts are used to escape or reposition. [S, Pod Your Reef blurb, cited in 10_microfauna and video notes]
- Crawl speed: 1 to 3 mm/s, which is 1 to 3 BL/s for a 1 mm animal [E; 10_microfauna.md gives the same estimate].
- Escape dart: the copepod escape literature gives jump speeds of order 10^2 to 10^3 BL/s over 2 to 10 ms for calanoids and cyclopoids. Harpacticoid escape speed was not measured in the sources retrieved. For animation, a 5 to 30 mm/s dart over 0.2 s is a safe art value [E].
- Gait: a crawl cycle of about 2 to 4 Hz leg alternation in pale copepods, with antennae tapping at 1 to 3 Hz [E].
- Pausing: 0.5 to 3 s stops are typical between bouts [E].

### 1.6 Naked-eye visibility
- Tigriopus at 1 mm is visible as a red speck on glass or rock. At night, under a torch, it reads as a moving orange dot. Tisbe is hard to see unless it moves.
- Red colour is the day-time tell. At 1 mm with a red gut, a dense population is visible on glass as a faint rust tint.

### 1.7 Sources (harpacticoid)
- Zoological Studies 48(6) 2009, Tisbe phylogeny: https://zoolstud.sinica.edu.tw/Journals/48.6/780.html
- bioRxiv 174300, Tigriopus carotenoid bioconversion: https://www.biorxiv.org/content/10.1101/174300.full.pdf
- Boxshall GA, Halsey SH (2004) An Introduction to Copepod Diversity. Ray Society. [K]

---

## 2. Calanoid copepods (Parvocalanus crassirostris)

### 2.1 Body plan
- Order Calanoida, family Paracalanidae. Prosome 5 segments, urosome 5 segments (genital somite + 4 abdominal). [S: Banyuls copepod database, https://copepodes.obs-banyuls.fr/en/fichesp.php?sp=1090]
- Rostrum short and blunt in both sexes (Tanaka, Vidjak et al. in Banyuls database). A short, rounded head, no long rostral filaments. Male has rostral filaments replaced by a small conical process. [S]
- Antenna 1 (A1): 24-segmented in the female, long, reaching about the distal end of the caudal rami (Tanaka) or the second urosomite (Vidjak). Mulyadi: nearly as long as the body, segments 1 and 2 fused. [S]
- Caudal rami: about 2.4 times longer than wide, with the innermost seta more than twice the ramus length (Mulyadi, Tanaka). [S]
- Terminal spines on distal segment of female P5. Absence of a dorsal hump in male. [S: Bradley 2009 thesis, https://www.soest.hawaii.edu/oceanography/masters/2009-Bradley.PDF; search summary]
- Egg sac: calanoid females carry the eggs as free clutches attached to the genital somite, not in a sac. Parvocalanus follows this pattern [K; Banyuls and the search results did not give Parvocalanus egg-sac detail].

### 2.2 Proportions [S]
- Mulyadi (Indonesian seas): prosome 3.2 times urosome length. Urosomal somites plus caudal ramus lengths 29 : 13 : 13 : 20 : 25.
- Wellershaus (Cochin backwater, female): thorax:abdomen = 3.1.
- Tanaka: cephalothorax:abdomen = 76:24.
- Vidjak et al. (Sibenik harbour): prosome about 2.5 times longer than wide, and 3 times prosome:urosome including the caudal rami.
- Body length of lab-cultured adults: 0.53 +/- 0.08 mm (arXiv 2603.03178 snippet [S-snip], https://arxiv.org/pdf/2603.03178). Use **0.5 mm** as the sim adult size. Prosome about 0.38 mm, urosome about 0.12 mm [E from ratios].

### 2.3 Colour in life
- Lab-reared and wild animals are largely translucent, pale grey-white.
- Gurney (Suez canal) noted "a patch of purple colour between the somites of P2 and P3" in many specimens. This is a diagnostic for this species, though not seen in all animals. [S]
- Hex estimates: body `#E6E8E0` at 35%-50% opacity; gut dark green-brown `#5A6B3C` when fed on algae; purple patch `#7A3A7E` between P2 and P3 [E from one description].
- Eye: single naupliar eye spot in the copepodite and adult. Colour not reported; use dark red `#8B1A1A` [E].

### 2.4 Motion
- Cruise: 1.24 +/- 1.08 mm/s lab average (arXiv 2603.03178 [S-snip]). Scaled: 2 to 3 BL/s [E].
- Bradley 2009: normal swimming speed scales negatively with body length, from 20 BL/s in early nauplii down to 2 BL/s in late copepodites. The adult male does not follow the trend. [S-snip]
- Escape: all stages maintain maximum speeds of 200 to 500 BL/s in the escape response (Bradley 2009 [S-snip]). This is the calanoid escape jump: a single rapid power stroke of 2 to 10 ms, then a passive sink.
- Gait: hop-and-sink. The 1 to 3 mm hop is driven by the antennae and swimming legs, followed by a passive sink of 0.5 to 1 s [10_microfauna.md, S2, S21, S22]. Escape jumps last 4 to 20 ms per beat [10_microfauna.md; Kiorboe escape-jump summary, S-snip].
- Feeding posture: hovering with a feeding current, body horizontal, A1 spread in a V [K].

### 2.5 Naked-eye visibility
- Visible only as small swarming specks in open water in strong light; invisible in a typical reef display. More visible at night under a torch, as a drifting cloud of small specks [E, consistent with 10_microfauna.md].

### 2.6 Sources
- Banyuls copepod database, Parvocalanus crassirostris: https://copepodes.obs-banyuls.fr/en/fichesp.php?sp=1090
- Bradley (2009) master's thesis, University of Hawai'i: https://www.soest.hawaii.edu/oceanography/masters/2009-Bradley.PDF
- arXiv 2603.03178, biogenic hydrodynamic transport of cruising copepods: https://arxiv.org/pdf/2603.03178

---

## 3. Cyclopoid copepods (Oithona, Apocyclops)

### 3.1 Body plan
- Order Cyclopoida. Body elongated, cylindrical, prosome more or less oval. Prosome 5 segments, urosome 4 (in the female: genital somite and 3 abdominal). Caudal rami short and bristle-tipped. [K; Oithona: Banyuls sp. 2254]
- Oithona parvula: urosome relatively long, about 38% of total length (Rosendorn 1917 plate, in Banyuls database [S]). Urosomal segment proportions 4:10:4.5:6:5 (caption not labelled per segment; the final value is presumed to be the caudal ramus) [S with caveat].
- Oithona similis: antennae longer than the prosome, shorter than total length. Urosome about 40 to 50% of prosome length [S: freshwaterbiodiversity.org.bd snippet; S-snip].
- Antenna 1 (A1): short in Oithona, usually 6 segments in females (search summary); in Apocyclops females A1 has 11 segments and in males 14 segments with symmetrical antennules. [S-snip: Apocyclops dengizicus redescription, via search]
- Egg sacs: paired and carried ventrally on the genital somite, typically in cyclopoids. Apocyclops egg sacs are elongated and hold 4 to 29 eggs in A. dengizicus. [S-snip]

### 3.2 Proportions
- Oithona parvula female: 0.45 to 0.52 mm total length [S, Banyuls sp. 2254].
- Oithona similis: 0.6 to 1.2 mm [S-snip].
- Apocyclops panamensis female: about 0.8 to 1.1 mm total length, tear-drop prosome, narrower urosome [S-snip, Wikipedia Apocyclops panamensis, https://en.wikipedia.org/wiki/Apocyclops_panamensis].
- Apocyclops dengizicus female 0.95 to 1.35 mm; male 0.78 to 1.08 mm [S-snip].
- For a generic aquarium cyclopoid: body 0.6 to 0.9 mm. Egg sacs add 20% to 40% to the visible width of a gravid female [E].

### 3.3 Colour in life
- Mostly translucent, pale, slightly grey. Gut contents show. Hex estimates: body `#D7DDD2` at 35%-50% opacity; gravid egg sacs pale green-ochre `#B9B98A`; gut darkening with diatom food `#6B6E4A` [E].
- Eyes: single median naupliar eye in the adult is a small red-black dot. [K; cyclopoids]
- No species-specific reef colour source found. Apocyclops colour was not documented in the retrieved sources [gap].

### 3.4 Motion
- Hop-and-sink. The hop lasts about 17 ms, then the animal sinks passively. [S-snip: PMC10762428 snippet, 2023 reply, high-speed Schlieren; in 10_microfauna.md]
- Cyclopoid escapes reach >500 BL/s over milliseconds [S22 snippet in 10_microfauna.md]. Oithona max escape 198 mm/s (about 620 BL/s) [10_microfauna.md, KIO10].
- Pauses: sit motionless 1 to 5 s, then 2 to 3 quick hops [10_microfauna.md; E].
- Crawl on rock or glass in an S-shaped path with A1 flicked forward [E].

### 3.5 Naked-eye visibility
- Not individually visible in reef display except as glints at 0.5 to 1 mm when moving. Gravid Apocyclops with egg sacs are slightly more visible as a pale oval.

### 3.6 Sources
- Banyuls copepod database, Oithona parvula: https://copepodes.obs-banyuls.fr/en/fichesp.php?sp=2254
- Wikipedia, Apocyclops panamensis: https://en.wikipedia.org/wiki/Apocyclops_panamensis
- PMC10762428 (cyclopoid hop, 2023 reply): https://www.ncbi.nlm.nih.gov/pmc/articles/PMC10762428/ [S-snip]

---

## 4. Gammarid amphipods (Elasmopus, Gammaropsis, Ampithoe)

### 4.1 Body plan (general gammarid, [K] Ruppert, Fox & Barnes 2004; Barnard & Karaman terminology)
- Head fused with pereonite 1. Pereon: 7 pereonites (pereonites 1 to 7). Pleon: 3 pleonites. Urosome: 3 urosomites. Telson at the end (often cleft or entire).
- Antennae: antenna 1 long, usually 0.3 to 0.5 BL (Elasmopus: E. karlae about 1/3 BL; E. oaxaquensis about 1/2 BL; E. lumbiniger about 0.55 BL). [S: plazi treatments, search summary]
- Antenna 2: longer or shorter than antenna 1 depending on genus. [K]
- Gnathopods: 2 pairs (gnathopod 1 and gnathopod 2) from pereonites 1 and 2. Gnathopod 2 is the larger, the sexually dimorphic one, with a palm: in Elasmopus karlae the propodus is about as long as the dactyl; in E. oaxaquensis and E. marcelae the propodus is about 1/4 longer than the dactyl. [S]
- Pereopods 3 to 7: walking legs. Pereopods 3 to 5 have a broadened basis in some genera (E. oaxaquensis: a nearly semicircular basis that deepens in serration from P3 to P5). [S]
- Pleopods: 3 pairs on pleonites 1 to 3, used for swimming. Uropods 1 to 3 on urosomites 1 to 3 (uropod 3 biramous in Ampithoe). [S: Ampithoe dalli, A. kava; K for general]
- Gills on the bases of pereopods (coxal plates on pereonites 2 to 7 in gammarids). Coxal plates are the visible lateral flanges.
- Lateral compression: body is laterally flattened in most gammarids, bent into a C in resting position (Gammaropsis, Ampithoe). Elasmopus: compressed, reasonably elongated. [K; S for Ampithoe]

### 4.2 Proportions
- Length: Elasmopus adults 7 to 10 mm (E. oaxaquensis female paratypes 7 and 8 mm; E. lumbiniger male 9.9 mm) [S].
- Ampithoe: A. rubricata to about 20 mm; A. dalli males 18 mm, females 20 mm [S].
- Gammaropsis: typically 6 to 10 mm in aquarium material [E].
- Height to length: about 1:4 to 1:5 for Elasmopus-type gammarids. Dorsal hump and curvature typical [E].

### 4.3 Colour in life
- Ampithoe rubricata: "red to green with dark markings, often with white spots on the pereion" [S, Ampithoe rubricata, linnaeus]. This is a good diagnostic for reef amphipods: pale body with discrete white pereon spots and dark bands.
- Elasmopus: pale green to tan body, with pale gut contents. Hex estimates: body `#A9B98F` at 40%-60% opacity; spots `#F4F1E6`; gut dark green `#4E5E3A` [E].
- Amphipods taken from macroalgae are often green; those from rock are grey-brown.
- Eyes: compound, black, large, often the only dark feature visible in a small individual. Hex `#1A1A1A`.

### 4.4 Motion
- Lateral scuttle on rock and glass with 7 pereopods; sudden 1 BL hops [10_microfauna.md, S4]. Walking speed estimate 5 to 20 mm/s, 1 to 3 BL/s [10_microfauna.md, est.].
- Side-swimming: body tilted about 90 degrees onto one side, pleopods beating rapidly; this is the signature gammarid swim, typically 30 to 80 mm/s (5 to 10 BL/s) [10_microfauna.md, est.]. Gammarus pulex swimming was measured by automated tracking (2023) with high inter-individual variation, but pleopod beat rates were not reported in the source retrieved [S: uhasselt documentserver, https://documentserver.uhasselt.be/bitstream/1942/39956/1/Among-individual%20variation%20in%20the%20swimming%20behaviour%20of%20the%20amphipod%20Gammarus%20pulex%20under%20dark%20and%20light%20conditions.pdf].
- Pleopod beat rate: [gap] Not retrieved for gammarids. For art use 8 to 15 Hz pleopod beat in a swim burst [E; compare mysid pleopods, section 7].
- Tail-flick escape: 100 to 200 mm/s for 1 to 2 frames [E].
- Hiding: burrows under coral encrustation or rock crevices by day [S, 10_microfauna.md, S5].

### 4.5 Naked-eye visibility
- Visible by day as 5 to 10 mm moving specks on rock and glass, especially when disturbed. Most visible at night under a torch, when swarms scatter from light [S: Reef Builders flashlight article, S3, in 10_microfauna.md].

### 4.6 Sources
- Plazi treatments of Elasmopus (E. karlae, E. oaxaquensis, E. lumbiniger 2025): https://treatment.plazi.org/id/A6AB1033-296A-5F52-9BB1-FAE054DAA466
- Plazi Ampithoe kava: https://tb.plazi.org/GgServer/html/038E87C89D1BFF9FFF6ACE7E6F4FFE84
- Ampithoe dalli, Wallawalla invertebrate pages: https://inverts.wallawalla.edu/Arthropoda/Crustacea/Malacostraca/Eumalacostraca/Peracarida/Amphipoda/Gammaridea/Ampithoidae/Ampithoe_dalli.html
- Ruppert EE, Fox RS, Barnes RD (2004) Invertebrate Zoology, 7th ed. [K]

---

## 5. Caprellid skeleton shrimp (Caprella mutica and related)

### 5.1 Body plan [S: Wikipedia Caprella mutica, https://en.wikipedia.org/wiki/Caprella_mutica]
- Slender, stick-like amphipod. Head fused with pereonite 1. Pereon has 7 pereonites, making up most of the body. Abdomen tiny and vestigial.
- Gnathopod 1 and gnathopod 2 on pereonites 1 and 2, with gnathopod 2 the largest. Used for defence, feeding and gripping.
- Pereopods 5 to 7 are smaller, used for clinging. Pereopods 3 and 4 may be reduced or absent in many species.
- Gills on pereonites 3 and 4, rarely on 2. Brood plates on pereonites 3 and 4 in females.
- Legs very long relative to the body: legs can be 2 to 3 times the body length in extended posture [E].

### 5.2 Proportions [S]
- Caprella mutica: up to 50 mm, males much larger than females [S].
- Caprella kennerlyi male: 3.5 cm total length. Caprella laeviuscula male: about 1.5 cm. Caprella angusta: about 4 cm. [S]
- For aquarium caprellids, a typical adult 8 to 15 mm, body width 1 mm, height 1 mm (the body is nearly circular in section) [E].
- Gnathopod 2 length: about 1/3 to 1/2 of body length [E].

### 5.3 Colour in life
- Many caprellids are mottled brown, grey-green or beige to match algae or hydroids. Pale with dark bands on the pereon. Hex estimates: body `#B7A88A` at 80%-90% opacity; dark bands `#4A3E2C`; gills pale `#E6E0D0` [E].
- Caprellids pick up the colour of the algae they live on: green (macroalgae), pink (coralline), brown (hydroids) [E].
- Eyes: two large dark compound eyes on the head, reddish-black `#2B1A1A` [E].

### 5.4 Motion
- Typical posture: upright, body swaying in the current, gnathopod 2 held ready in a mantis-like pose ("praying") [S: Caprella mutica, marlin.ac.uk; Wikipedia]. Swaying is a filter-feeding behaviour, with the antennae acting as sieves; high-speed video found the antennae operating at Re about 3 [S: 2007 abstract, epic.awi.de].
- Walking: "inchworm" looping movement is the standard description but no kinematic source was retrieved [gap]. For art: alternate front (gnathopods) and rear (pereopods 5 to 7) grip, with body arching by about 30 to 60 degrees per step. Step rate about 0.5 to 1 Hz [E].
- Speed: slow, under 1 BL/s [S: "slow-moving", no measurement].
- Swim: rare, short, with legs held trailing [E].
- Threat response: freezes in the praying pose, or drops and curls [E].

### 5.5 Visibility
- Visible to the naked eye on hydroids, algae and rock as 5 to 15 mm sticks. Day and night.

### 5.6 Sources
- Wikipedia, Caprella mutica: https://en.wikipedia.org/wiki/Caprella_mutica
- MarLIN, Caprella mutica: https://www.marlin.ac.uk/species/detail/2141
- AWI 2007 abstract on Caprella mutica feeding and antennae: https://epic2-clone.awi.de/18033/1/Mic2007l.pdf
- Wallawalla, Caprella kennerlyi: https://inverts.wallawalla.edu/Arthropoda/Crustacea/Malacostraca/Eumalacostraca/Peracarida/Amphipoda/Caprellidea/Caprellidae/Caprella_kennerlyi.html

---

## 6. Ostracods (podocopid and myodocopid)

### 6.1 Body plan
- Bivalved crustacean: the body is enclosed in a calcified or chitinous bivalved carapace (valves). The carapace is the main visible structure; the limbs protrude from the gap between the valves. [K, Ruppert et al. 2004]
- Antennae: antenna 1 (antennule) and antenna 2 (large, used for swimming in myodocopids). Podocopids crawl with antennae and thoracopods protruding; myodocopids swim with their second antennae as oars. [S: marine-ostracods Linnaeus; Gigantocypris Wikipedia]
- Myodocopid family Cypridinidae: carapace length 1 to 32 mm across the family; subfamily Cypridininae has variable carapace shape with weak sexual dimorphism [S: Linnaeus database snippet].
- Podocopid (benthic) ostracods are typically 0.5 to 1.5 mm, with the carapace oval, smooth, often translucent. Length to height about 2:1 [E].

### 6.2 Proportions
- Body length 0.5 to 1.5 mm (podocopid, reef aquaria). Height 0.5 to 0.8 of length [E].
- Antenna 2 in myodocopids: about the length of the carapace; featherlike with long setae [S: Gigantocypris second antennae with nine long setae each].

### 6.3 Colour in life
- Podocopids: pale, translucent, with a visible gut (often brown or dark green from algae). Hex: carapace `#E6DDC8` at 30%-50% opacity; gut `#6E5A3C`; eyes (if present) black `#1A1A1A` [E].
- Myodocopids (Cypridina / Vargula type) are often highly iridescent; a cypridinid video note mentions "highly iridescent" colour [S: Cambridge JMBA mating video, resolve.cambridge.org]. Not typical in reef aquaria.

### 6.4 Motion
- Podocopid: crawl with antennae poking out of the valves; short bursts of swimming. Antennae show constant movement [S: UWM Field Station, from 10_microfauna.md S9].
- Speed: 1 to 3 mm/s, est. [10_microfauna.md]. Limb-tip speed in freshwater Cypridopsis: about 30 mm/s for antennule, 50 mm/s for antenna [S-snip; Cypridopsis vidua].
- Swim: "tumbling seed" look, short bursts with antenna strokes at 2 to 5 Hz [E].
- Myodocopid swimming: antennal exopod sweeping motions propel the animal; the swimming is jerky [S: marine myodocopid kinematics summary].

### 6.5 Visibility
- Podocopids visible as small pale specks crawling on glass or rock by day. At night, a torch shows them as moving "seed" shapes. Hard to identify without a magnifier.

### 6.6 Sources
- Linnaeus marine ostracods, Cypridinidae: https://marine-ostracods.linnaeus.naturalis.nl/linnaeus_ng/app/views/species/taxon.php?id=78624
- Wikipedia, Gigantocypris: https://en.wikipedia.org/wiki/Gigantocypris
- UWM Field Station, ostracod video: https://uwm.edu/field-station/?p=1296

---

## 7. Mysid shrimp (Americamysis bahia and similar)

### 7.1 Body plan [S: Linnaeus Ordo Mysidacea; crustacea.net]
- Carapace shield-like, fused dorsally to the head and up to 4 anterior thoracic somites. The last 2 or 3 thoracic somites may be exposed in some families.
- Thorax: 8 pairs of thoracopods (pereopods), each with an endopod and exopod. Pereopods 1 and 2 often modified as gnathopods. All exopods are natatory (swimming).
- Abdomen: 6 somites; last longer than the others. Pleopods (swimming) on the first 5 abdominal segments; the sixth lacks them. Pleopods have a flagelliform exopod and endopod.
- Tail fan: telson and paired uropods. Statocyst (balance organ) in the endopod of the uropods (used as a family diagnostic).
- Females carry a brood pouch of fewer than seven pairs of lamellae (oostegites).
- Eyes: stalked, large, often the dominant feature of the head. [K]

### 7.2 Proportions [E from family description; not fully measured]
- Americamysis bahia adult: 5 to 8 mm total length (aquaculture standard, [K]). Mysid at 7 mm in sim is reasonable.
- Length to height about 4:1; carapace covers about the front half of the thorax [E].
- Pleopods long, about 1/3 to 1/2 of body length in adults [E].

### 7.3 Colour in life
- Mostly translucent with a visible gut (food is green-brown, bright when fed algae). Carapace pale. Eyes dark. Hex: body `#D9E2D0` at 30%-45% opacity; gut `#6B7A3E`; eyes `#1A1A1A` [E].
- Brood pouch in females pale yellow-green `#C9C68C` [E].

### 7.4 Motion
- Pleopod swimming in Americamysis bahia gives fast forward swimming, speeds above 2 BL/s, up to about 12 BL/s [S: APS DFD 2021 abstract, https://archive.aps.org/dfd/2021/a13/4]. The mysid uses pleopods for bursts and mostly thoracic appendages for hovering [S-snip].
- Pleopod beat pattern: pleopods on each segment beat independently, forming two five-paddle metachronal cycles 180 degrees out of phase [S: Ruszczyk, Webster & Yen 2021, Integrative and Comparative Biology, DOI 10.1093/icb/icab119; search summary]. This is the fact to animate: the two sides alternate, and the wave runs posterior to anterior along the abdomen.
- Beat rate: not retrieved for A. bahia. Pleopod beat frequency for crustaceans in general 3 to 13 Hz for larger shrimp (mantis shrimp pleopods 3.6 to 13 Hz [S-snip, par.nsf.gov]). For a 7 mm mysid, use **15 to 25 Hz** in a burst and **5 to 8 Hz** in a hover [E; flag as the most important unverified value].
- Hover: body near horizontal, small corrections; school shifts together [10_microfauna.md; E].
- Backward tail-flip escape: 200 to 300 mm/s [10_microfauna.md, est.].
- Diel: benthic by day, rising into a swarm at night [S: UVM Epscor Mysis, https://epscor.w3.uvm.edu/2/node/4084, lake Mysis, not reef].

### 7.5 Visibility
- Day: hidden on rock or sand, or at the substrate. Night: visible as a hovering swarm in torch light, a few mm specks with large dark eyes.

### 7.6 Sources
- Linnaeus, Ordo Mysidacea: https://sat-zooplankton.linnaeus.naturalis.nl/linnaeus_ng/app/views/species/nsr_taxon.php?id=23956
- Crustacea.net, Mysidacea families: https://crustacea.net/crustace/mysidacea/www/mysimen.htm
- APS DFD 2021 abstract A13.4 on Americamysis swimming: https://archive.aps.org/dfd/2021/a13/4
- Ruszczyk, Webster & Yen (2021), ICB, DOI 10.1093/icb/icab119 (cited from search summary; not opened)

---

## 8. Isopods (reef sphaeromatid and cirolanid)

### 8.1 Body plan [S: isopods.nhm.org Cirolana pdfs; K for general]
- Dorsoventrally flattened (sphaeromatids are often more convex). Head (cephalon) with 1 pair of antennae and 1 pair of antennules, compound eyes present in most cirolanids (some lose them).
- Pereon: 7 free pereonites, each with a pair of walking legs (pereopods 1 to 7). Pleon fused into the pleotelson with 5 pleonites visible as fused segments.
- Pleopods: 5 pairs on pleonites 1 to 5, plumose, used for swimming and ventilation. Cirolana pleopods: the first has 5 coupling hooks; pleopod 2 has a copulatory stylet on the endopod [S].
- Telson / pleotelson: apex broad and rounded, with inconspicuous spines posteriorly. Uropods on the sides of the pleotelson [S].
- Respiratory branchiae on pleopods: absent in normal Cirolana (present in the giant Bathynomus) [S].

### 8.2 Proportions
- Normal adult cirolanid 3 to 30 mm; small species Cirolana erodiae 2 to 3.5 mm [S].
- Length to width about 2.5:1; flattened dorsoventrally [E].

### 8.3 Colour in life
- Cirolanids: translucent pale brown or grey, with dark eyes. Hex body `#A18A6C` at 70%-90% opacity; eyes `#1A1A1A` [E].
- Sphaeromatids (reef species): often pale grey with darker marbling; colour varies by species. [gap: no reef sphaeromatid colour source found].

### 8.4 Motion
- Crawl on rubble and rock, cling to surfaces; can roll into a ball when disturbed [K].
- Fast swimming: cirolanids swim 50 to 100 mm/s straight to a target [10_microfauna.md, est.]. Pleopod beat rate unknown here [gap].
- Night activity: cirolanid isopods show circadian night-swimming in the wild, from an AWI beach study [S: epic.awi.de/5934, from 10_microfauna.md S17].
- Gait: crawl with 7 pairs of legs in alternating-tripod pattern, at 2 to 4 Hz step rate [E].

### 8.5 Visibility
- Visible day on rock and under rubble as 3 to 6 mm grey-brown animals; very visible at night with a torch.

### 8.6 Sources
- NHM isopod PDFs (Cirolana): https://isopods.nhm.org/pdfs/2259/2259-003.pdf
- AWI EPIC 5934 (cirolanid night swimming): https://epic.awi.de/5934

---

## 9. Rotifers (Brachionus plicatilis)

### 9.1 Body plan [S: Wikipedia Rotifer; FAO aquaculture manual]
- Body divided into head, trunk and foot. Typically somewhat cylindrical; most are 0.1 to 0.5 mm.
- Brachionus plicatilis is enclosed in a lorica (a shell) with a flat or rounded shape, not an open body. Lorica length 100 to 340 µm (FAO, citing Fukusho 1989) [S]. Lorica ends in 6 occipital spines [S]. Caspian Sea populations: 164 to 353 µm length, 123 to 254 µm width [S].
- Corona: ciliated ring (two wheel-like ciliated discs) on the head. The corona creates currents to sweep food and to pull the animal through the water [S: Wikipedia].
- Foot: extends from the rear of the trunk, narrower than the trunk, ends in 1 to 4 toes; adhesive glands in sessile species [S].
- Mastax: chewing jaws (trophi) are fixed after hatching; body size grows during preadult stages [S: search result on trophi].
- Eye: a single red eye spot on the head (common in rotifers) [K, Ruppert et al. 2004].

### 9.2 Proportions
- Brachionus plicatilis: length 210 µm, width 90 µm (arXiv 2407.00439 methods, [S]). Lorica width about 0.4 to 0.6 of length [E].
- Foot length: about 0.3 of body length, extends as a tail with 2 toes [E].
- Corona width: about 0.6 to 0.8 of lorica width [E].

### 9.3 Colour in life
- Lorica pale, translucent, often greenish or yellow-tinged. Gut contents (algae, yeast in culture) make the body look green to dark. Red eye spot visible at the anterior end [K]. Hex: lorica `#D7DDC5` at 30%-40% opacity; gut `#5A7A3C`; eye `#A3121C` [E].
- Gut colour follows the diet strongly. The source did not describe lorica transparency; treat as translucent [gap].

### 9.4 Motion
- Swimming: corona cilia beat metachronally, creating a rotating look (wheel illusion). Cilia are about 50 µm long and beat at 20 to 30 Hz [S: arXiv 2407.00439]. Metachronal wave passes around the corona about 20 to 30 times per second [S, same].
- Speed: 200 to 400 µm/s (0.2 to 0.4 mm/s) [S: arXiv 2407.00439]. A Springer study reports 500 µm/s in recently hatched fed females to 1500 µm/s in non-ovigerous adult females without algae [S-snip]. An aquaculture study gives egg-carrying females 40 to 45 mm/min (0.67 to 0.75 mm/s) [S-snip]. Use **0.3 to 0.8 mm/s** for a typical adult in an aquarium, which is 1.5 to 4 BL/s [E].
- Temperature: swimming speed falls by about 26% per 10 °C drop [S-snip].
- Wheel illusion: animate the corona as a ring of metachronal cilia rotating at the wave frequency; the body itself does not spin (Wikipedia) [S].
- Foot-anchoring pivot: foot can attach, and the body pivots in a slow circle [S: Wikipedia, foot adhesive glands].

### 9.5 Visibility
- Not visible to the naked eye individually. Dense populations appear as a faint cloud or murkiness in culture water; in a display they are invisible. Under a 10x loupe, a 200 µm lorica is a small pale oval.

### 9.6 Sources
- Wikipedia, Rotifer: https://en.wikipedia.org/wiki/Rotifer
- FAO live food manual, rotifers (Brachionus lorica 100 to 340 µm): https://eclass.upatras.gr/modules/document/file.php/TAY122/%CE%95%CE%A1%CE%93%CE%91%CE%A3%CE%99%CE%95%CE%A3%20%CE%A6%CE%9F%CE%99%CE%A4%CE%97%CE%A4%CE%A9%CE%9D-%CE%98%CE%95%CE%9C%CE%91%CE%A4%CE%91%20%CE%93%CE%99%CE%91%20%CE%95%CE%A0%CE%95%CE%9E%CE%95%CE%A1%CE%93%CE%91%CE%A3%CE%99%CE%91%20%CE%9A%CE%91%CE%99%20%CE%A0%CE%91%CE%A1%CE%9F%CE%A5%CE%A3%CE%99%CE%91%CE%A3%CE%97/FAO-LIVE%20FOOD-PART%206-ROTIFERS-1.pdf
- arXiv 2407.00439, feeders and expellers (cilia data): https://arxiv.org/pdf/2407.00439
- Caspian Sea Brachionus morphometry (search summary): https://www.mbai.org.in/uploads/manuscripts/Chapter_02%20(06-13)1261742853.pdf

---

## 10. Ciliates (Euplotes, Vorticella)

### 10.1 Euplotes (Euplotes eurystomus as the reef-relevant example)
- Body: ellipsoid, almost parallel-sided. Length 140 to 180 µm, width 95 to 135 µm [S: hobbyist ID page, realmicrolife.com; cross-check with taxonomy before using].
- Ventral surface flattened, with cirri: 6 frontal, 1 buccal, 2 frontoventral, 5 transverse (between 6 ridges), 2 caudal, 2 left marginal (per the same ID page) [S].
- Oral field large and triangular, with S-shaped adoral zone reaching the posterior third [S].
- Locomotion: walks on cirri on surfaces, and also swims freely; the ID author notes these specimens tended to swim freely [S-snip].
- Colour: colourless to pale, with a dark nucleus (macronucleus) visible in the centre under brightfield. Food vacuoles can be green from algae [K].
- Speed: no measured speed found for E. eurystomus. Universal microswimmer range is about 2 to 20 BL/s; Paramecium about 1 mm/s [K; 10_microfauna.md].

### 10.2 Vorticella (stalked peritrich)
- Zooid bell: inverted bell shape, 30 to 40 µm diameter when contracted. Stalk 3 to 4 µm diameter, about 100 µm long (review, PMC6189993 search summary). Another paper: stalk 100 to 200 µm long, 2 to 3 µm diameter [S-snip, PMC2877353 or similar].
- Colour: colourless; the cytoplasm is pale, with a visible macronucleus (horseshoe shaped) [K].

### 10.3 Motion
- Vorticella: spasmoneme contracts in a few ms, coiling the stalk. The stalk shortens to 20% to 40% of its length in under 10 ms; another study reports down to 14% [S-snip, 2017 review and 2010 study]. Zooid moves at 15 to 90 mm/s during contraction; maximum contraction velocity about 5 to 10 cm/s [S-snip].
- Animate: a snap-back of 10 to 30 ms, then re-extension over 2 to 5 s.
- Euplotes: crawl on cirri in jerks, spinning while swimming; zig-zag with helical rotation [10_microfauna.md; S-snip].

### 10.4 Visibility
- Not visible to naked eye. A Vorticella colony on a glass surface looks like a faint white fuzz at 10x or more; a colony under a loupe can be seen as a grey fringe. Euplotes is invisible without a microscope.

### 10.5 Sources
- Euplotes ID page, realmicrolife.com: https://realmicrolife.com/euplotes-eurystomus/?pdf=56998
- NIES protozoa morphology, Euplotes: https://web.nies.go.jp/chiiki1/protoz/morpho/ciliopho/euplotes.htm
- Vorticella contraction review (PMC): https://pmc.ncbi.nlm.nih.gov/articles/PMC6189993 (search summary, not opened)

---

## 11. Phytoplankton (Tetraselmis, Nannochloropsis)

### 11.1 Tetraselmis (green flagellate, cultured as Tetraselmis suecica)
- Cell shape: round, ovoid, elliptical or flattened. Genus-wide cell size 3.5 to 25 µm (side length) [S: Wikipedia Tetraselmis, algaebarn.com]. T. suecica mean cell diameter about 8 µm [S-snip, from a French study; OCR garbled, treat with caution]. Typical members 10 to 20 µm [S-snip]. For a feed-grade T. suecica use **8 to 12 µm**.
- Flagella: 4, equal in length, shorter than the cell, from an apical depression. Two basal bodies in T. suecica give two pairs [S].
- Theca: scaly, calcium-carbonate encrusted wall in T. suecica [S-snip].
- Chloroplast: single large cup-shaped chloroplast with one pyrenoid; eyespot present in many [S].
- Colour: bright green `#5FA04A` at 100% opacity; in dense cultures a strong yellow-green. Hex for the cell: `#4E9A3E` with the chloroplast highlight `#8CC65A` [E].
- Motion: helical or straight swim, reversible runs and abrupt turns. Speed: no absolute value found; an abstract says T. suecica is up to 4 times faster than Dunaliella tertiolecta [S-snip]. Estimate 0.1 to 0.3 mm/s, 10 to 20 BL/s [10_microfauna.md, est.]. The flagella beat at about 20 to 60 Hz [E].
- Visibility: a dense bloom reads as a green haze in water; individual cells not visible to the naked eye.

### 11.2 Nannochloropsis
- Cell: coccoid, spherical, non-motile. Diameter 2 to 4 µm (typical). Range 2 to 5 µm across strains [S: algobank.unicaen.fr AC593 and other AC entries; search summary].
- No flagella. Motion only by Brownian jitter and flow [S].
- Colour: yellow-green `#A8B84A` [S: "green-yellow colour"]. Hex `#B6BE4C` at 100% opacity, slightly yellow [E].
- Taxonomy: N. salina and N. gaditana moved to Microchloropsis (Fawley et al. 2015) [S-snip].
- Visibility: a dense culture is visible as green or yellow tint; individual cells need 400x or more.

### 11.3 Sources
- Wikipedia, Tetraselmis: https://wikipedia.com/wiki/Tetraselmis
- Algae morphology and motility (Tetraselmis, Gayana Botanica): https://revistas.udec.cl/index.php/gayana_botanica/article/download/3917/3853/8105 [S-snip]
- Algobank, Nannochloropsis: https://algobank.unicaen.fr/en/AC593.pdf

---

## 12. Bristleworm (Eurythoe complanata and similar amphinomids)

### 12.1 Body plan [S: Wikipedia Eurythoe complanata; Barroso & Paiva 2007 redescription, labpoly.biologia.ufrj.br; species-id.net]
- Long, depressed body, rectangular in cross section. Length up to 150 mm [S].
- Head: 4 eyes; elongated caruncle (the dorsal head process) extending to about chaetiger 3 in Mediterranean specimens [S].
- Branchiae: tufts of filaments starting from the second setiger, running along the body [S].
- Parapodia: biramous, with projecting rami; each has one dorsal and one ventral cirrus [S]. Two types of structurally simple parapodia: straight serrate and bristly branched [S].
- Chaetae: notochaetae simple capillary, serrated; ventral setae sub-bifid, stout. Harpoon-like bristles (spurred) are the painful ones, which extend from their parapodia in calcareous tufts [S: Mediterranean account; Barroso & Paiva 2007].
- Segment count: not confirmed in the retrieved sources. Use **about 30 to 40 chaetigers** for art [E; check against Barroso & Paiva 2007].
- Colour: family-level description: "nearly always brilliant, often showing shades of green, red, scarlet, violet, or yellow" [S: Barroso & Paiva summary]. Males whitish from sperm in coelom; females pinkish-red from oocytes [S]. Live body grey, green, pink (Wikipedia). Preserved specimens brownish.
- Chaetae colour: not reported in sources [gap]. Bristle tufts are typically pale or translucent with a dark tip; in the sim use cream bristles with dark tip `#E9E0C4` and `#4A3A2A` [E].

### 12.2 Proportions
- Length 100 to 150 mm (adult), width 8 to 12 mm, height 4 to 6 mm [E from description].
- Length to width about 10 to 14:1 [E].
- Caruncle length about 3 chaetigers [S].

### 12.3 Motion
- Crawling and gliding on substrate with parapodia. Body undulates slightly; the chaetae flare when disturbed [S-snip; 10_microfauna.md].
- Speed: 5 to 20 mm/s (for the 100 mm body, 0.05 to 0.2 BL/s) [10_microfauna.md, est.]. Retreat into a crevice at 30 to 50 mm/s [E].
- Defence posture: body coils and the chaetae flare; harpoon bristles extend from calcareous tufts on contact [S].
- Feeding: crawls to food within minutes of a feed; retreats within seconds from light [10_microfauna.md, S12, S13].

### 12.4 Visibility
- Day: visible as a red or pink worm in crevices, body partly out. Night: visible crawling on rock and glass in torch light. Bristle tufts give a bristly silhouette.

### 12.5 Sources
- Wikipedia, Eurythoe complanata: https://en.wikipedia.org/wiki/Eurythoe_complanata
- Barroso & Paiva 2007 redescription (Rocas Atoll), labpoly.biologia.ufrj.br: https://www.labpoly.biologia.ufrj.br/BarrosoPaiva2007.pdf
- species-id.net, Eurythoe complanata: https://species-id.net/wiki/Eurythoe_complanata

---

## 13. Micro-snails (Stomatella, Collonista, Cerithium juveniles)

### 13.1 Stomatella (ear snail)
- Shell: ear-shaped, polished, with a prominent spire (flattish, not coiled). Shell size: 6 to 17 mm in cited sources; CIESM says up to 25 mm in the Indo-Pacific [S].
- Foot: large, extends beyond the shell; can detach part of the foot when threatened (aquarium observation) [S-snip, reef forum].
- Colour: highly variable. Brown, green, pink, buff, grey, black or mottled. Shell surface with fine spiral grooves [S: CIESM atlas, https://www.ciesm.org/atlas/Stomatellaimpertusa.html].
- Hex estimates: shell `#7A6A52` mottled with `#C9B89A`; foot `#CFC6B0` translucent at 80%-95% [E].
- Motion: foot glides with a wave of muscular contraction; slow, about 1 to 3 mm/s [10_microfauna.md, est.]. A reef snail with a body length about 2 to 3 cm can crawl across glass at several cm/min [S-snip, forum].
- Visibility: visible on rock and glass in day, shell 6 to 15 mm. Easy to see.

### 13.2 Collonista (sp.)
- No source retrieved for Collonista in this pass [gap]. Keep as a small (2 to 4 mm), pale, conical shell in the sim until verified.

### 13.3 Cerithium juveniles (ceriths, e.g. C. caeruleum, C. egenum)
- Shell: elongated turret, many whorls; siphonal canal at the front of the aperture, short, reflected to one side (C. caeruleum: anterior canal short and strongly reflected left of the axis) [S: Wikipedia C. caeruleum; CIESM atlas].
- Adult size: up to 41.5 mm in one species; C. egenum common size 12 to 20 mm [S]. Juveniles of reef species: 2 to 5 mm; not verified.
- Colour: C. caeruleum grey-blue flecked with white, nodes black, early whorls white. C. egenum whitish with dark brown flames and mottles [S].
- Motion: slow crawl, foot creeping, antennae forward. Speed about 1 to 3 mm/s [E].
- Visibility: visible as a small spiral shell on rock or glass. Juveniles easily missed.

### 13.4 Sources
- Wikipedia, Cerithium caeruleum: https://en.wikipedia.org/wiki/Cerithium_caeruleum
- CIESM atlas, Cerithium egenum: https://www.ciesm.org/atlas/Cerithiumegenum.html
- CIESM atlas, Stomatella impertusa: https://www.ciesm.org/atlas/Stomatellaimpertusa.html

---

## 14. Spaghetti worm (Loimia medusa, terebellid), foraminifera (Homotrema rubrum, Peneroplis), red acoel (Convolutriloba retrogemma), Asterina-type star (Aquilonastra)

### 14.1 Spaghetti worm (Loimia medusa and similar terebellids)
- Body: up to 300 mm, with feeding tentacles up to roughly twice that length [S: Wikipedia Loimia medusa, wikipedia.com/wiki/Loimia_medusa].
- Tentacles: long, bluish white feeding tentacles, grooved and ciliated; used for selective deposit feeding [S]. Buccal tentacles are about as long as the notopodial region or a little longer (genus diagnosis) [S]. Tentacles often show purple bands [S: Bangladesh marine biodiversity page].
- Uncini: pectinate, a diagnostic feature of Loimia [S].
- Tube: built of shell fragments and gravel, sometimes with sand or sponge; the worm lives inside it. Pelagic larva lives in a gelatinous tube and extends its tentacles while drifting [S: post-larval development paper, Plymouth].
- Colour: body pale to pinkish, tentacles bluish white with purple bands [S].
- Motion: tentacles extend from the tube; each tentacle creeps or glides slowly by ciliary action (genus description), with particles moving along the grooves [S-snip; 10_microfauna.md, 0.1 to 0.5 mm/s est.]. Tentacles retract in 1 to 2 s when threatened [10_microfauna.md, est.]. No measured gliding speed was found [gap].
- Visibility: a bluish white tuft of tentacles from a gravel or shell tube. Visible in day; retracts on disturbance.

### 14.2 Homotrema rubrum (red foram)
- Test: five morphotypes in Bermuda: hemispherical (37.3%), globose (18.5%), knobby (17.1%), encrusting (12.3%), columnar (3.4%) [S: Bulletin of Marine Science 1996, ingentaconnect]. Up to 8 mm, typically 2 to 5 mm [S: Bermuda field guide, hoopermuseum.carleton.ca].
- Test volume: large globose about 25 mm^3, hemispherical about 8 mm^3, small columnar/knobby/encrusting about 4 mm^3 [S].
- Colour: distinct red wall; the red is attributed to degraded chlorophyll from endosymbionts [S: Bermuda guide]. Live well-preserved tests keep red colour; pink tests are weathered [S: Wallis Island taphonomy].
- Hex: test `#B0302A` (red), with pink weathered `#D68C86` [E].
- Structure: knobby forms have fragile tests with loosely organized chambers; stylae (spikes) remain after resorption of the inner test in Homotrema only [S].
- Pseudopodia: no source retrieved [gap]. Use: fine reticulopodia extending from pores, 0.5 to 1 um/s [K, textbook; treat as art estimate only].
- Motion: effectively static; grows over weeks.
- Visibility: red bumps on rock, 2 to 8 mm, visible by day.

### 14.3 Peneroplis (sp., e.g. P. pertusus)
- Test size: general foraminifera 0.1 to 1.5 mm typical [S: Wikipedia Foraminifera test]. Peneroplis-specific size not retrieved.
- Form: flat, rounded planispiral test, with multiple apertures (the perforate aperture of peneroplids). Juveniles vary in size of the proloculus and deformation [S: Analesbio 2012, P. pertusus / planatus].
- Colour: not retrieved [gap]. Use translucent pale cream `#E9E1CC` with pale-brown chambers `#B69E7A` [E].
- Motion: static; pseudopodia emerge from the aperture (not retrieved in detail).
- Visibility: 0.5 to 1 mm pale discs on algae and sand. Visible under a loupe.

### 14.4 Convolutriloba retrogemma (red acoel, "red planaria")
- Length: about 2 mm (Wikipedia); 2 to 4 mm in aquarium specimens [S].
- Body: soft, leaf-like, no true gut; three-lobed tail tip is diagnostic of the genus (separates it from similar genera) [S: aussieanimals.com].
- Colour: reddish-brown from symbiotic algae (zooxanthellae) packed in dorsal red-brown packets [S: Wikipedia, aussieanimals.com].
- Hex estimates: body `#9A4A2E` at 80%-95% opacity, with dorsal speckles `#6B2A1A` [E].
- Motion: slow glide on cilia, often on mucus; clusters on coral or glass [S]. Speed 0.5 to 1 mm/s [10_microfauna.md, est.]. Fragments: axis reversal in buds [S: SICB abstract].
- Visibility: day and night on glass, rock and coral as a rust-brown flat smear; it reads as a rust stain.

### 14.5 Aquilonastra (Asterina-type star)
- Genus: 32 species described in revisions; many undescribed. Identified by spinelets on plates. Five arms, rays merging with disc [S].
- Size: centre to tip up to 2 cm; maximum about 4 cm (A. scobinata) [S].
- Colour: A. scobinata dark brown to grey-brown to cream, dark flecking [S]. A. coronata beige, brown or grey, sometimes bright orange; mottled, near-invisible on rock [S].
- Arm shape: short and bold with rounded tips (A. coronata) [S].
- Asexual fission: unequal arm lengths; a regenerating animal is small with fewer arms [S].
- Motion: slow tube-foot locomotion, about 1 to 3 cm/min (not measured) [E].
- Visibility: 1 to 2 cm, cryptic on rock and in day; easy to miss.

### 14.6 Sources
- Wikipedia, Loimia medusa: https://wikipedia.com/wiki/Loimia_medusa
- Plymouth post-larval development, Loimia medusa: https://plymsea.ac.uk/id/eprint/576/1/The_post-larval_development_of_Loimia_medusa_Sav..pdf
- Bermuda Homotrema study (Bull Mar Sci 1996): https://www.ingentaconnect.com/content/umrsmas/bullmar/1996/00000058/00000001/art00016
- Bermuda Homotrema field guide: https://hoopermuseum.carleton.ca/Bermuda/Other/BERM12-A.HTML
- Wikipedia, Convolutriloba retrogemma: https://en.wikipedia.org/wiki/Convolutriloba_retrogemma
- aussieanimals Convolutriloba: https://aussieanimals.com/profiles/acoel-flatworm-convolutriloba-retrogemma/
- Museums Victoria, O'Loughlin asterinid revision: https://museumsvictoria.com.au/media/6051/121-132_mmv76_oloughlin_2_web.pdf
- Analesbio 2012 (Peneroplis): https://um.es/analesdebiologia/numeros/34/PDF/34_2012_07.pdf

---

## 15. Full source list (this file)

Verified in fetch or result:
1. Banyuls copepod database, Parvocalanus crassirostris: https://copepodes.obs-banyuls.fr/en/fichesp.php?sp=1090
2. Banyuls copepod database, Oithona parvula: https://copepodes.obs-banyuls.fr/en/fichesp.php?sp=2254
3. Bradley (2009), Univ. Hawai'i master's thesis, Parvocalanus: https://www.soest.hawaii.edu/oceanography/masters/2009-Bradley.PDF
4. arXiv 2603.03178, biogenic transport of cruising copepods: https://arxiv.org/pdf/2603.03178
5. Zoological Studies 48(6) 2009, Tisbe: https://zoolstud.sinica.edu.tw/Journals/48.6/780.html
6. bioRxiv 174300, Tigriopus carotenoid: https://www.biorxiv.org/content/10.1101/174300.full.pdf
7. Wikipedia, Apocyclops panamensis: https://en.wikipedia.org/wiki/Apocyclops_panamensis
8. Wikipedia, Eurythoe complanata: https://en.wikipedia.org/wiki/Eurythoe_complanata
9. Barroso & Paiva 2007 Eurythoe redescription: https://www.labpoly.biologia.ufrj.br/BarrosoPaiva2007.pdf
10. Wikipedia, Rotifer: https://en.wikipedia.org/wiki/Rotifer
11. arXiv 2407.00439, feeders and expellers (rotifer cilia): https://arxiv.org/pdf/2407.00439
12. FAO live food manual, rotifers: https://eclass.upatras.gr/modules/document/file.php/TAY122/%CE%95%CE%A1%CE%93%CE%91%CE%A3%CE%99%CE%95%CE%A3%20%CE%A6%CE%9F%CE%99%CE%A4%CE%97%CE%A4%CE%A9%CE%9D-%CE%98%CE%95%CE%9C%CE%91%CE%A4%CE%91%20%CE%93%CE%99%CE%91%20%CE%95%CE%A0%CE%95%CE%9E%CE%95%CE%A1%CE%93%CE%91%CE%A3%CE%99%CE%91%20%CE%9A%CE%91%CE%99%20%CE%A0%CE%91%CE%A1%CE%9F%CE%A5%CE%A3%CE%99%CE%91%CE%A3%CE%97/FAO-LIVE%20FOOD-PART%206-ROTIFERS-1.pdf
13. Plazi, Elasmopus lumbiniger (Yoshimura et al. 2025) and related treatments: https://treatment.plazi.org/id/A6AB1033-296A-5F52-9BB1-FAE054DAA466
14. Plazi, Ampithoe kava: https://tb.plazi.org/GgServer/html/038E87C89D1BFF9FFF6ACE7E6F4FFE84
15. Wallawalla invertebrate pages (Ampithoe dalli; Caprella kennerlyi): https://inverts.wallawalla.edu/Arthropoda/Crustacea/Malacostraca/Eumalacostraca/Peracarida/Amphipoda/Gammaridea/Ampithoidae/Ampithoe_dalli.html
16. Wikipedia, Caprella mutica: https://en.wikipedia.org/wiki/Caprella_mutica
17. MarLIN, Caprella mutica: https://www.marlin.ac.uk/species/detail/2141
18. AWI 2007 abstract, Caprella mutica feeding: https://epic2-clone.awi.de/18033/1/Mic2007l.pdf
19. Linnaeus, Ordo Mysidacea: https://sat-zooplankton.linnaeus.naturalis.nl/linnaeus_ng/app/views/species/nsr_taxon.php?id=23956
20. Crustacea.net, Mysidacea: https://crustacea.net/crustace/mysidacea/www/mysimen.htm
21. APS DFD 2021 abstract A13.4, Americamysis: https://archive.aps.org/dfd/2021/a13/4
22. Ruszczyk, Webster & Yen (2021), ICB, DOI 10.1093/icb/icab119 (search summary, not opened)
23. Linnaeus marine ostracods, Cypridinidae: https://marine-ostracods.linnaeus.naturalis.nl/linnaeus_ng/app/views/species/taxon.php?id=78624
24. Wikipedia, Gigantocypris: https://en.wikipedia.org/wiki/Gigantocypris
25. NHM isopod PDFs, Cirolana: https://isopods.nhm.org/pdfs/2259/2259-003.pdf
26. AWI EPIC 5934, cirolanid night swimming: https://epic.awi.de/5934
27. Euplotes ID page, realmicrolife: https://realmicrolife.com/euplotes-eurystomus/?pdf=56998
28. NIES protozoa morphology, Euplotes: https://web.nies.go.jp/chiiki1/protoz/morpho/ciliopho/euplotes.htm
29. Vorticella contraction review, PMC6189993 (search summary): https://pmc.ncbi.nlm.nih.gov/articles/PMC6189993
30. Wikipedia, Tetraselmis: https://wikipedia.com/wiki/Tetraselmis
31. Algobank, Nannochloropsis: https://algobank.unicaen.fr/en/AC593.pdf
32. Gammarus pulex swimming tracking study (2023): https://documentserver.uhasselt.be/bitstream/1942/39956/1/Among-individual%20variation%20in%20the%20swimming%20behaviour%20of%20the%20amphipod%20Gammarus%20pulex%20under%20dark%20and%20light%20conditions.pdf
33. CIESM atlas, Stomatella impertusa: https://www.ciesm.org/atlas/Stomatellaimpertusa.html
34. Wikipedia, Cerithium caeruleum: https://en.wikipedia.org/wiki/Cerithium_caeruleum
35. Wikipedia, Loimia medusa: https://wikipedia.com/wiki/Loimia_medusa
36. Bermuda Homotrema study, Bull Mar Sci 1996: https://www.ingentaconnect.com/content/umrsmas/bullmar/1996/00000058/00000001/art00016
37. Bermuda Homotrema field guide: https://hoopermuseum.carleton.ca/Bermuda/Other/BERM12-A.HTML
38. Wikipedia, Convolutriloba retrogemma: https://en.wikipedia.org/wiki/Convolutriloba_retrogemma
39. Museums Victoria, O'Loughlin asterinid revision: https://museumsvictoria.com.au/media/6051/121-132_mmv76_oloughlin_2_web.pdf
40. Analesbio 2012, Peneroplis: https://um.es/analesdebiologia/numeros/34/PDF/34_2012_07.pdf

Textbooks (general, [K]): Ruppert EE, Fox RS, Barnes RD (2004) Invertebrate Zoology, 7th ed. Boxshall GA, Halsey SH (2004) An Introduction to Copepod Diversity, Ray Society.

---

## 16. Gaps and what to get next

| Gap | Impact | Next step |
|---|---|---|
| Tigriopus and Tisbe adult length and width, antenna proportions | Core for harpacticoid model | Taxonomic redescriptions (Boxshall & Halsey 2004 keys), Tisbe revisions |
| Caprellid inchworm kinematics and walk speed | Caprella animation | Caprellid locomotion papers (search Caprella gait kinematics) |
| Gammarid pleopod beat rate and body-length speed | Gammarid swim animation | Gammarus / Gammarid swimming kinematics |
| Mysid pleopod beat in Hz for A. bahia | Mysid swim animation (most important) | Ruszczyk, Webster & Yen 2021 full text |
| Euplotes and Vorticella body dimensions from a primary source | Ciliate size | Foissner et al. 1991 / Foissner taxonomy |
| Apocyclops and Oithona colour in life | Aquarium colour | Reef-aquarium copepod identification pages |
| Sphaeromatid isopod colour and motion | Isopod | Reef sphaeromatid key |
| Collonista snail shell size and colour | Micro-snail | Reef micro-gastropod ID guide |
| Peneroplis and Homotrema pseudopodia | Forams | Foraminifera textbook (Hohenegger, Leutenegger) |
| Loimia tentacle gliding speed | Spaghetti worm | Terebellid tentacle biology |
| Eurythoe chaetae colour and segment count | Bristleworm | Barroso & Paiva 2007 full text |
| Convolutriloba motion speed | Red planaria | Acoel locomotion papers |
| Aquilonastra locomotion speed | Star | Asterinid locomotion (not retrieved) |

Also: the sim label issue flagged in `video/micro_fauna.md` still stands. "Tigger pod" is Tigriopus (harpacticoid), not calanoid. Parvocalanus is the calanoid.

---

## 17. Modelling notes

| Group | Key silhouette features to get right | Animation cues |
|---|---|---|
| Harpacticoid (Tigriopus, Tisbe) | Cylindrical, short urosome, 2 furcal rami, short A1, red gut and eye spot (Tigriopus) | Crawl with leg alternation 2 to 4 Hz, short dart then stall, antennae tapping |
| Calanoid (Parvocalanus) | Short blunt rostrum, A1 reaching caudal rami, purple patch between P2 and P3 | Hop 1 to 3 mm, sink 0.5 to 1 s; escape 200+ BL/s for 2 to 10 ms |
| Cyclopoid (Oithona, Apocyclops) | Elongated, long urosome in Oithona, egg sacs on genital somite in Apocyclops | Hop 17 ms, sink, pause 1 to 5 s |
| Gammarid (Elasmopus, Ampithoe) | 7 pereonites, C-shaped laterally compressed body, A1 about 1/3 to 1/2 BL, gnathopod 2 larger, white spots on pereon (Ampithoe) | Side swim, pleopod blur 8 to 15 Hz, sudden hop 1 BL |
| Caprellid | Stick-like, legs 2 to 3 times BL, pereon 7 segments, reduced abdomen, gnathopod 2 large | Praying pose, sway in current, inchworm step about 0.5 to 1 Hz |
| Mysid | Carapace over front half of thorax, 8 thoracopods, 5 pairs of pleopods, big eyes | Hover, pleopod metachronal beat (two 5-paddle cycles, 180 degrees out of phase), burst to 12 BL/s |
| Ostracod (podocopid) | Bivalved carapace, length 2:1 to height, antennae poking out | Scurry with antenna strokes, tumbling seed motion |
| Isopod (cirolanid) | Flattened, 7 pereonites, 5 pleopods, pleotelson, compound eyes | Crawl, roll into ball on disturbance, night swim 50 to 100 mm/s |
| Rotifer (Brachionus) | Lorica 100 to 340 µm with 6 occipital spines, corona ring, foot with 1 to 4 toes | Corona metachronal beat 20 to 30 Hz (wheel illusion), speed 0.3 to 0.8 mm/s |
| Ciliate (Euplotes) | Ellipsoid, cirri pattern (6 frontal, 1 buccal, 2 frontoventral, 5 transverse, 2 caudal), S-shaped oral field | Walk on cirri, jerky swim, spin |
| Vorticella | Bell 30 to 40 µm, stalk 100 µm | Snap-back in 10 ms, slow re-extension |
| Tetraselmis | Flattened ovoid cell 8 to 12 µm, 4 flagella shorter than cell | Helical swim with abrupt turns, 0.1 to 0.3 mm/s |
| Nannochloropsis | Coccoid 2 to 4 µm, yellow-green | Brownian jitter only |
| Bristleworm (Eurythoe) | Long depressed body, branchiae from setiger 2, caruncle to setiger 3, harpoon bristle tufts | Slow undulation, chaetae flare, retreat in crevice 30 to 50 mm/s |
| Stomatella | Ear-shaped polished shell, large foot | Slow glide 1 to 3 mm/s, foot wave |
| Cerithium juvenile | Turret shell, reflected siphonal canal | Slow creep 1 to 3 mm/s |
| Spaghetti worm (Loimia) | Bluish white tentacles with purple bands, pectinate uncini (not visible), tube of shell and gravel | Tentacles extend, creep, retract 1 to 2 s on threat |
| Homotrema | Red test, globose or hemispherical, 2 to 8 mm | Static; growth over weeks |
| Peneroplis | Flat planispiral, multiple apertures | Static |
| Convolutriloba | Leaf-like, three-lobed tail tip, red-brown from algae | Slow glide on mucus, 0.5 to 1 mm/s |
| Aquilonastra | Five arms with spinelets, mottled cream to brown | Slow tube-foot creep |
