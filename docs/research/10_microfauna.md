# Microfauna: Pods, Plankton and the Zoomed-In View (60 gal, ~227 L mixed soft-coral reef)

Scope: the small animals and cells that live on the rock, sand, glass and in the water column of this tank: copepods (harpacticoid, calanoid "Tigger pods", cyclopoid), amphipods (gammarid, caprellid), mysids, ostracods, isopods, rotifers, ciliates, phytoplankton, bristleworms, micro-snails, spaghetti worms, forams and pest flatworms. Part 1 gives the per-group biology for rendering and behaviour. Part 2 gives a population model that plugs into the algae/detritus model in 09 and the fish and coral models in 02 and 04. Part 3 gives the design guidance for a microscope ("zoom-in") view. Part 4 is the implementable spec with acceptance checks.

Method note. As in 08 and 09, full-text fetches were blocked for most journal hosts, so sourced numbers come from abstracts, repository records, culture manuals and vendor sheets of the paper named. Numbers tagged **est.** are my derivations or hobby-consensus values and are tunable. Hobby-vendor claims are tagged [vendor]. Raw facts gathered in parallel by the coordinator's helper are in 10_microfauna_sources.md (same folder); its numbers are cited here with the tags below and the two files agree on every shared value.

**Source legend.**
[TISB] Mass culture and offspring production of Tisbe biminiensis (AGRIS record 65decead63b8185d9ca9a2d1): 28,000 nauplii+copepodites L-1 d-1 from 5 L cultures at 28-32 C, 35 ppt, fed diatoms.
[BERG81] Bergmans 1981, Tisbe furcata life table at 18 C, J. Mar. Biol. Ass. UK 61:691 (via marineinfo 3705, in 10_microfauna_sources.md): rm 0.233 d-1, R0 94, minimum generation time 14.9 d, mean 16.4 d, nauplii 80 h.
[DIAL] Apocyclops panamensis culture at 24/28/32 C (Dialnet 7768379, in sources file): life cycle 6 d at 28-32 C, 11,600 ind./L reached in a 1,000 L tank.
[THAI] Parvocalanus culture densities (Thai J. Agric., tci-thaijo 266841, in sources file): peaks 3.3-5.7 ind./mL, best at 28 ppt.
[FAO] FAO manual on live feeds, rotifer chapter (w3732e0h, in sources file): Brachionus life table at 15/20/25 C and culture densities.
[MEES99] Mees et al. 1999, Sphaeramia orbicularis diet, Kenya (marineinfo 4024, in sources file): gammarid amphipods and tanaids dominant, harpacticoids in smaller fish.
[REDAL] Mysidopsis californica culture (redalyc 44918833011, in sources file).
[MAB20] Malaysian Appl. Biol. 49(1):23, ciliate culture: mixed protozoa 30,000-40,000 cells/mL peak, Euplotes < 100 um.
[SKKU] Tigriopus japonicus Cu 96 h LC50 32.75 mg/L (Mar. Pollut. Bull. 85:526, in sources file).
[TISH] Tisbe holothuriae diet and demography, Helgolander Meeresunters. (BF02365524): rm = 0.304 d-1 on the best diet.
[SAL22] J. Mar. Sci. Eng. 10(11):1663, 2022: Tigriopus and Tisbe holothuriae salinity tolerance and reproduction.
[PARV] Parvocalanus crassirostris diet trials, JCU repository 28560 (Alajmi/Zeng): 6.2-6.7 d to adult, 121 eggs per female lifetime, >84 % female.
[REED] Reed Mariculture Parvocalanus culture sheet [vendor]: adults 200-400 um, 24-26 C, 30-35 ppt, max 3 adults/mL, positively phototactic.
[ACAR-D] Leandro et al., Acartia tonsa development vs temperature (ria.ua.pt 10773/11256): 40.3 d at 10 C, 8.9 d at 22 C.
[ACAR-T] Acartia tonsa CTmax vs acclimation and stage (Mugla Univ. repository 20.500.12809/9626).
[KIO10] Kiorboe et al. 2010, J. R. Soc. Interface, escape jumps in Oithona, Acartia, Calanus.
[APOR] Apocyclops royi productivity vs temperature, salinity, diet (KoreaScience JAKO200531935931915).
[APO22] Apocyclops royi egg/nauplius production model, Front. Mar. Sci. 9:863244 (2022).
[APOP] Apocyclops panamensis development for aquaculture, WAS abstract 45965: N1 nauplius 70 x 100 um.
[OITH] Oithona nana salinity trial (AGRIS 675aa2d40ce2cede71cc6bc0): optimum 20 ppt at 27 C.
[HEID04] Heidelberg et al. 2004, nocturnal near-bottom zooplankton, Discovery Bay, Jamaica (USC Dornsife PDF).
[EAM] Cryptofauna of the epilithic algal matrix, inshore GBR (JCU 141206): harpacticoids 2025 +/- 132 per 100 cm2, 0.48 g m-2 wet.
[HARP-G] Harpacticoid selective grazing on intertidal diatoms, Mar. Biol. (s00227-005-0086-2): 50-200 ug wet biomass cm-2 d-1, 3-11 % of microalgal biomass per day.
[HYPX] Sub-lethal effects of hypoxia on harpacticoid reproduction, UT Austin thesis (ETD-UT-2010-05-1318).
[MYS] US EPA, Culturing Mysidopsis (Americamysis) bahia, culture manual.
[GAM] Sexton and Matthews, Notes on the life history of Gammarus chevreuxi (Plymouth MBA repository 319).
[AOKI] Aoki 1992, life histories of nine Caprella species, Amakusa (Kyushu Univ. 22381).
[CAP-W] Wikipedia Caprellidae (feeding modes, predators).
[OSTR] Wikipedia Ostracod (size, deposit feeding, 30 d to maturity in pool species).
[MYSW] Wikipedia Mysida (5-25 mm, diel migration, maturity 12-20 d).
[APOG] Marnane and Bellwood 2002, cardinalfish diet and nocturnal foraging, One Tree Reef, MEPS 231:261 (JCU 133510).
[GRU00] Grutter 2000, ontogenetic diet of Labroides dimidiatus (JCU 12943).
[HAWK] Cirrhitid diet and territoriality, Environ. Biol. Fish. (s10641-011-9817-z).
[AMPH] Anemonefish trophic study, JCU 84582.
[SAP23] Saper et al. 2023, capture and ingestion of live feeds by three scleractinians, Coral Reefs.
[GALX] Galaxea external feeding, reported in reefs.com "Zooplankton feeding by corals underestimated".
[ZOA] Reimer, Feeding behaviour in Hawaiian Palythoa and Zoanthus (ScholarSpace 10125/4286).
[CU-AMP] Australian and NZ amphipod Cu/Zn sensitivities (UTS 10453/625): Melita spp. juveniles 96 h LC50 120 ug Cu/L.
[CU-ACA] Monteiro et al. 2013, Acartia tonsa Cu LC50 vs salinity and DOM, ETC 32:1412.
[CU-HYA] Hyale crassipes acute metal toxicity, Black Sea (Sinop Univ.): Cu > Cd > Zn.
[CONV] Convolutriloba retrogemma asexual reproduction rates (IWU scholars): one clone per 4-5 d at 25 C.
[CONV-W] Wikipedia Convolutriloba retrogemma.
[EURY] Animal Diversity Web, Eurythoe complanata.
[FORAM] reefs.com "Aquarium forams".
[COLL] Top Shelf Aquatics Collonista page [vendor].
[TSA-M] Top Shelf Aquatics mandarin pod guide [vendor].
[NANNO] Nannochloropsis oculata growth at four culture scales, J. Aquac. Sci. (Unair): peak 5-15 x 10^6 cells/mL in 6 d at 25-30 C.
[TETRA] Wikipedia Tetraselmis.
[EUPL] realmicrolife.com Euplotes patella: 90-120 um.
[ROT-V] Larsen et al., temperature and viscosity vs swimming velocity of Artemia, Brachionus, Acartia (DTU Orbit).
[CIRO] Bruce, Cirolana mercuryi description (NHM isopods PDF 9977): 2.5 mm, coral rubble.
[09] docs/research/09_algae_and_extremes.md. [05] 05_parameter_effects.md. [08] 08_extreme_heat.md. [02]/[04] fish and invert files.

Unit notes. 1 body length per second = BL/s. Densities are given per cm2 of surface for benthic groups and per litre for planktonic groups. Tank surfaces (est. from a 48 x 18 x 24 in tank with ~40 lb rock): glass 1.4 m2, sand 0.55 m2, rock outer surface 1.5 m2 plus 3-5x that in internal pore surface, water 227 L. Chaeto refuge if a refugium or in-tank clump exists: treat 1 L of loose Chaetomorpha as ~0.3 m2 of filament surface (est.).

---

## 1. Groups, habitat class and overview

| Group | Habitat class | Where in this tank | Adult size (mm) | Shape and colour for rendering |
|---|---|---|---|---|
| Harpacticoid copepods (Tisbe, Tigriopus, Laophontidae) | benthic; brief swims | rock film, glass, sand surface, chaeto, detritus pockets | 0.5-1.2 (Tisbe female ~0.8, est.) | cylindrical, no neck; tan to pinkish; two egg sacs or one ventral sac; crawl with body flat on the surface [TISB; EAM] |
| Calanoid copepods ("Tigger pods" = Parvocalanus crassirostris; also Acartia) | planktonic | water column, drift to the lowest-flow pockets; concentrate at a light source | Parvocalanus 0.2-0.4 [REED]; Acartia 0.8-1.5 (Wikipedia) | teardrop with long first antennae held out; clear with a dark gut when fed [REED]; Acartia transparent with red-orange eyespot |
| Cyclopoid copepods (Apocyclops, Oithona) | moves between: hover near surfaces, swim at night | water column near rock, sand surface, sump | 0.5-1.0 (Apocyclops female ~0.9, est.; N1 0.07 x 0.10 [APOP]) | pear-shaped with narrow urosome, antennae shorter than calanoids; translucent grey; paired egg sacs trailing |
| Gammarid amphipods (Elasmopus, Hyale, Melita, Gammarus-like) | benthic; swims at night | rock crevices, under rubble, chaeto, filter floss, overflow | 3-10 (tank species; up to 15, est.) | laterally compressed comma, 7 leg pairs, big antennae; grey-brown, olive, orange or translucent; females carry a ventral brood pouch |
| Caprellid amphipods (skeleton shrimp) | benthic, clings | hydroids, macroalgae, Kenya tree and GSP mat, chaeto, powerhead cages | 5-20 (C. stella ~10, reeflex; newborns 1.1-1.7 [AOKI]) | stick-like, praying-mantis pose, raptorial gnathopods; clear, tan or pink; cling with hind legs and sway |
| Mysids | moves between: hover at the sand/rock by day, column at night | over the sand bed, under overhangs, sump | 4.4-9.4 (A. bahia [MYS]); 5-25 (order [MYSW]) | shrimp-like, transparent, two large stalked eyes, statocysts in tail look like dots; hover in schools, bodies horizontal |
| Ostracods (seed shrimp) | benthic; some swim briefly | sand, detritus, rock film | 0.5-1.0 (typical 1 [OSTR]) | bivalved bean, opaque white to brown; antennae flick out of the shell |
| Isopods: harmless Sphaeromatid/Munna and harmful Cirolanids | benthic; cirolanids swim to fish at night | rubble, sand, under rock | harmless 1-3; cirolanids 2.5-10 (C. mercuryi 2.5 [CIRO]; aquarium pests to ~10, hobby) | dorso-ventrally flattened "pill bug"; cirolanids pale tan, torpedo-shaped, fast swimmers |
| Rotifers (Brachionus plicatilis if dosed; small bdelloids on film) | planktonic (Brachionus) or film (bdelloids) | water column after a dose, otherwise film, filter socks, chaeto | 0.13-0.34 (Brachionus; est. from culture literature) | vase with a ciliated corona ("wheel"), lorica, forked foot; glassy |
| Ciliates and protozoa (Euplotes, Vorticella, Strombidium) | film and column | all surfaces, detritus, new-tank water column | 0.03-0.3 (Euplotes 0.09-0.12 [EUPL]) | ovoid or bell; transparent; visible only above ~20x |
| Phytoplankton: Nannochloropsis, Tetraselmis, Isochrysis | planktonic | water column after a dose; otherwise 10-1000 cells/mL background | Nanno 0.002-0.003 [NANNO/Wikipedia]; Tetraselmis 0.010 x 0.014 [TETRA] | Nanno: non-motile green dot; Tetraselmis: green ovoid, 4 flagella, "runs and abrupt turns" [TETRA] |
| Bristleworms (Eurythoe, Linopherus, small Syllids) | benthic; nocturnal | sand, rock pores, under frags | adults 20-100, up to 350 [EURY]; syllids 2-10 | segmented, pink-orange with white bristle tufts and red gills [EURY]; glide with parapodial waves |
| Stomatella and Collonista micro-snails | benthic; nocturnal | rock and glass at night, rock shade by day | Collonista 1-3 (est.); Stomatella 5-20 (hobby) | Collonista: round white/pink spiral; Stomatella: flat ear-shaped shell, mottled foot much larger than shell, fast for a snail |
| Spaghetti worms (Terebellidae) | sessile, benthic | rock crevices, under sand surface | body 10-50 in tank; tentacles stretched 5-20 cm | hidden body; dozens of thin white or translucent tentacles creeping over sand, each a slow conveyor of particles |
| Foraminifera (Homotrema, Marginopora, Sorites, Gypsina) | benthic, sessile or very slow | under rock, rubble, sand, glass | 0.5-10 (Marginopora to ~10 [FORAM]) | Homotrema: red encrusting blobs with branches; Marginopora/Sorites: beige flat discs; Gypsina: white sheet [FORAM] |
| Pest flatworms (Convolutriloba retrogemma) | benthic | rock and glass in bright, low-flow spots; over zoas | 2-6 (2 typical [CONV-W]) | flat, rust-red to orange with three posterior lobes; glide; carry algal symbionts |

Habitat class drives both the model (which surface K applies) and the view (which "slide" shows them). Groups that move between classes get a time-of-day split (section 2.2).

---

## 2. Per-group biology

### 2.1 Locomotion and speed

| Group | Style | Cruise speed | Burst or escape | Notes for animation |
|---|---|---|---|---|
| Harpacticoid | crawl on surface with body flexing; short swims of 1-5 mm when disturbed | 1-3 mm/s crawl, est.; cruise regression gives 2.4 mm/s for a 1 mm copepod [KIO10] | 50-150 mm/s hop, est. (smaller than calanoid) | crawl path is a wander with stops of 0.5-3 s; antennae tap the surface |
| Calanoid (Parvocalanus, Acartia) | hop-and-sink: antennal strokes give a 1-3 mm hop, then passive sink 0.5-1 s; feeding bouts hover with feeding current | 2-5 mm/s averaged, hops 10-30 mm/s | Acartia max 378 mm/s (~500 BL/s), beat 4.4 ms; Oithona 198 mm/s (~620 BL/s) [KIO10]; escape regression ~380 mm/s at 1 mm length | escape is a straight 5-20 mm dash of a few beats, each beat ~7-20 ms [KIO10]; strongly phototactic, swarm at a torch [REED] |
| Cyclopoid | jerky hop with longer pauses than calanoids; ambush posture | 1-3 mm/s | ~200 mm/s (Oithona) [KIO10] | sit motionless 1-5 s, then 2-3 quick hops |
| Gammarid amphipod | scuttle on 7 legs; swim on its side with pleopod beats, body curled; flicks | walk 5-20 mm/s (1-3 BL/s, est.); swim 30-80 mm/s (5-10 BL/s, est.) | tail-flick 100-200 mm/s, est. | side-swimming is the signature: body tilted 90 degrees, rapid leg blur, lands and darts under cover |
| Caprellid | cling with hind legs, body upright, sways; inchworm when moving | 1-5 mm/s inchworm, est. | none; drops and curls | sway at 0.5-1 Hz in flow; gnathopods snap at passing particles [CAP-W] |
| Mysid | hover with pleopod beat, body horizontal, small corrections; schools | 5-20 mm/s (1-3 BL/s, est.) | tail-flip backward 200-300 mm/s, est. | station-hold against flow; whole school shifts together; vertical rise at night [MYSW] |
| Ostracod | scurry with antennae poking out of valves; some brief swims | 1-3 mm/s, est. | 10-20 mm/s, est. | looks like a tumbling seed |
| Isopod (harmless) | crawl, cling to rubble | 2-5 mm/s, est. | roll up | cirolanids: fast swimming 50-100 mm/s, straight to a target, est. |
| Rotifer | corona cilia pull it forward while it spins slowly about its axis; foot can anchor and the body pivots | 0.3-1 mm/s (2-4 BL/s, est.; speed falls 26 % per 10 C [ROT-V]) | none | wheel illusion: metachronal cilia at ~20-40 Hz, render as a rotating ring highlight |
| Ciliate (Euplotes) | crawls on cirri in jerks, spins while swimming | 0.2-1 mm/s (universal microswimmer mode ~2-20 BL/s; Paramecium ~1 mm/s, text-book) | reverse jerk | zig-zag with helical rotation; Vorticella: stalk contracts in <10 ms when touched |
| Tetraselmis | helical swim with 4 flagella, long runs and abrupt turns [TETRA] | 0.1-0.3 mm/s (10-20 BL/s, est.) | none | Nannochloropsis never moves except by Brownian jitter and flow |
| Bristleworm | parapodial gliding, body undulates slightly | 5-20 mm/s, est.; "moves quickly" [EURY] | retreats into crevice at 30-50 mm/s | emerges within minutes of food hitting the sand; retreats from light within seconds |
| Stomatella | fast glide for a snail | 1-3 mm/s, est. (several cm/min) | none; autotomises tail when grabbed (hobby) | Collonista 0.3-1 mm/s |
| Spaghetti worm | sessile; tentacles extend by cilia, each tentacle creeps at 0.1-0.5 mm/s and retracts in 1-2 s | - | retract all tentacles in 1-2 s when a fish bites | tentacles follow surface texture; particles move along the groove |
| Foram | pseudopodia extend 0.5-1 um/s (textbook reticulopodia); whole-animal crawl 1-10 mm/day | - | none | effectively static in any view; show pseudopod net only at 40x |
| Flatworm | ciliary glide, body flat, edges ripple | 0.5-1 mm/s, est. | detaches and drifts | aggregate in bright, still spots by late afternoon |

### 2.2 Diel behaviour

Reef field data: near-bottom zooplankton at night was 2,853-3,491 ind./m3 at Discovery Bay; 88-91 % copepods, of which Oithona (cyclopoid) was 47-55 % and nauplii 23 %; demersal amphipods 2-5 /m3, mysids 6, ostracods 11-64, isopods 13-25 [HEID04]. Paired day/night counts from other reefs in the same paper's Table 1: Lizard Island surface 60 vs 493 /m3 (x8), Davies Reef 37 vs 84 (x2.3), Eilat 850 vs 1,750 (x2) [HEID04]. Cardinalfish stomachs held identifiable food in 64-93 % of fish at dawn versus 5-36 % at dusk, confirming night foraging on this emergent plankton [APOG].

| Group | Day | Night | Sim rule (fraction of population exposed on the surface or in the column) |
|---|---|---|---|
| Harpacticoid | in film, under detritus, in rock pores | emerge onto open rock, glass and into the column within 1-2 h of lights-out | exposed fraction 0.15 day, 0.6 night (est.); column fraction 0.01 day, 0.05 night |
| Calanoid | spread in column; gather at bright spots | spread; sink toward the bottom | column fraction 1.0 always; near-light clustering by day |
| Cyclopoid | hover near rock, in pores | swim up into the column | column fraction 0.2 day, 0.6 night (est.; Oithona dominates the night catch [HEID04]) |
| Gammarid | hidden in crevices, chaeto, under rubble | out on rock faces, glass and sand; some swim | exposed 0.05 day, 0.5 night (est.); column 0.005 day, 0.05 night |
| Caprellid | clinging, visible but still | more active | exposed 0.6 both, activity x2 at night |
| Mysid | hovering under overhangs and near the sand | up in the column, spread out | column exposure 0.3 day, 0.9 night [MYSW] |
| Ostracod | in sand and detritus | some crawl onto glass | exposed 0.05 day, 0.3 night |
| Cirolanid | buried in sand | swim out to attack sleeping fish | attack events only at night |
| Rotifer, ciliate, phyto | no cycle except that phyto photosynthesises only in light | - | 1.0 |
| Bristleworm | hidden | foraging on sand and rock within minutes of lights-out, retreat at lights-on [EURY] | exposed 0.02 day (unless food added), 0.5 night |
| Stomatella, Collonista | under rock | graze glass and rock [COLL] | exposed 0.1 day, 0.7 night |
| Spaghetti worm | tentacles out all day, partly | fully out | tentacle extent 0.6 day, 1.0 night |
| Flatworm | gather in light, highest at late afternoon | spread | exposed 1.0, clustering by light |

Rule of thumb for the view: at lights-out +1 h the number of visible crustaceans on an open rock face is 3-5x the daytime number (field x2-x8 [HEID04]; est. for a tank), and the owner's torch test ("dozens, hopefully hundreds" of pods on glass an hour after lights out [TSA-M]) is the hobby benchmark for a healthy population.

### 2.3 Diet and food links

| Group | Main food | Link to the 09 algae/detritus pools | Measured rate |
|---|---|---|---|
| Harpacticoid | diatom film, bacteria on detritus, settled phyto | grazes Diatom film and GSA film pools; also a Detritus pool | community grazing 50-200 ug wet cm-2 d-1, removing 3-11 % of microalgal biomass per day; preferred diatoms grazed at 10-30 %/d and up to 55-228 %/d [HARP-G]; guts held 222-604 diatom cells [HARP-G] |
| Calanoid | suspended phytoplankton 2-20 um; Parvocalanus needs Isochrysis/Tisochrysis or diatoms and does not thrive on Nannochloropsis [REED]; Acartia also eats ciliates and rotifers (Wikipedia) | Phyto pool; Ciliate pool | clearance est. 1-5 mL/ind/d for a 0.3 mm copepod (est. from literature scaling) |
| Cyclopoid (Apocyclops) | phyto including concentrates, bacteria, small nauplii; survives low-PUFA diets [APO22; APOP] | Phyto, Detritus, own nauplii | nauplii per female per day 13.4 at 32 C [APOR] implies strong food demand |
| Gammarid | detritus, film, macroalgae fragments, carrion, dead pods | Detritus, Diatom film, GSA, Hair algae (10 % of the snail rate, est.) | est. 5-10 % body wet weight per day |
| Caprellid | diatoms, detritus, protozoa, smaller amphipods and larvae; filter or ambush [CAP-W] | Detritus, Ciliates, pod nauplii | est. 10 % body weight per day |
| Mysid | phyto, detritus, zooplankton; cannibal [MYSW] | Phyto, Detritus, Rotifer and Nauplius pools | 150 Artemia nauplii per mysid per day in culture [MYS] = est. 300-500 copepod nauplii or 50-100 adult harpacticoids per day if available |
| Ostracod | detritus deposit feeding [OSTR] | Detritus | est. 2 % body weight per day |
| Rotifer | phyto 2-10 um, bacteria | Phyto, Bacteria/DOC proxy | clearance 5-20 uL/ind/h est.; starves within 2-3 days without phyto (culture practice) |
| Ciliate | bacteria, small flagellates | Bacteria proxy (DOC index from 09) | - |
| Phyto | NO3, PO4, light | draws from the same N and P pools as 09 algae with Ks of 1 uM N and 0.1 uM P (est.) | mu 0.5-1.0 d-1 at 25-30 C: cultures peak at 5-15 x 10^6 cells/mL in 6 d [NANNO] |
| Bristleworm | carrion, uneaten food, detritus, dead animals [EURY] | Detritus (largest single detritus sink), die-off events in 05/09 | est. 5-10 % body weight per day |
| Micro-snails | film algae and biofilm [COLL] | Diatom film, GSA film | Collonista est. 0.5 mg DW film per snail per day; Stomatella 5 mg (scale from Trochus 0.18 g DW/d in 09 by foot area) |
| Spaghetti worm | surface detritus and settled particles | Detritus on sand within tentacle reach | est. 1-3 cm2 of sand surface swept per tentacle per hour |
| Foram | detritus, bacteria; soritids photosynthesise [FORAM] | Detritus; light | negligible |
| Flatworm | symbiont photosynthesis plus copepods and rotifers [CONV-W] | Light; small pods | one flatworm est. 1-3 nauplii per day |

### 2.4 Reproduction and generation time versus temperature

| Group | Mode | Sourced timing | Sim generation time G(T) (days) and max intrinsic rate r_max (d-1) |
|---|---|---|---|
| Harpacticoid (Tisbe) | female carries one ventral egg sac, 20-60 nauplii, 6 naupliar + 5 copepodid stages | T. holothuriae rm 0.304 d-1 on best diet [TISH]; T. biminiensis yields 28,000 young L-1 d-1 at 28-32 C, population recovers 4 d after harvest [TISB]; max 2.7 egg sacs per female mean, most offspring at 32 ppt [SAL22] | T. furcata at 18 C: rm 0.233 d-1, minimum generation 14.9 d, mean 16.4 d, nauplii 80 h [BERG81]. Scaling with Q10 2.5 gives G = 10 d at 27 C, 8 d at 30 C, 15 d at 22 C; r_max 0.30 at 26-30 C [TISH] |
| Calanoid (Parvocalanus) | free-spawned eggs, 121 per female lifetime, >84 % female [PARV] | 6.2-6.7 d egg to adult [PARV] at the 24-26 C culture window [REED] | G = 7 d at 26 C; r_max 0.45 (est. from lifetime fecundity and 7 d generation) |
| Calanoid (Acartia) | free eggs, 10-40 per female per day (culture literature) | 8.9 d to adult at 22 C, 40.3 d at 10 C [ACAR-D]; weight growth ln g = -2.962 + 0.130 T (nauplii) [ACAR-D] | G = 6-7 d at 26 C; r_max 0.4 |
| Cyclopoid (Apocyclops) | paired egg sacs, 12-24 eggs per clutch, ~8 clutches, 74.5 % hatch, female lives 14.5 d at 28 C [APO22]; 13.4 nauplii per female per day at 32 C [APOR] | 7.2 d nauplius to first eggs at 32 C [APOR]; A. panamensis completes its life cycle in 6 d at 28-32 C [DIAL]; production peaks 26-28 C and drops 10 % at 32 C [APO22] | G = 7 d at 28 C; r_max 0.40 (A. panamensis cultures grew 3.4x in 14 d = 0.09 d-1 realised at high density [DIAL]) |
| Gammarid | ventral brood pouch, 5-44 eggs (mean ~30), new brood every ~14 d; hatch at 12-14 d, mature at 36 d, generation ~50 d at 10-16 C [GAM] | tropical tank species mature faster (est.) | G = 30 d at 26 C, 45 d at 22 C; r_max 0.08 |
| Caprellid | brood pouch, 12-539 young per brood, lifetime 130-360, maternal care 3-33 d; generation 40-70 d, lifespan <110 d at 13-28 C [AOKI] | | G = 40 d at 26 C; r_max 0.06 |
| Mysid | marsupium, 5-7 young (to 20) every 4-6 d; brood pouch at 12-16 d; mature ~20 d at 26 C [MYS]; M. californica ~10 young per 7 mm female, marsupium 12-17 d at 20-23 C, 78-88 % survival [REDAL] | | G = 25 d; r_max 0.10 |
| Ostracod | eggs glued to surfaces, direct development, ~30 d to maturity [OSTR] | | G = 35 d; r_max 0.05 |
| Isopod | brood pouch, direct | hobby: slow | G = 45 d; r_max 0.04 |
| Rotifer (Brachionus) | parthenogenetic, 1 egg carried at a time | life table at 15/20/25 C: egg development 1.3/1.0/0.6 d, first spawning at 3.0/1.9/1.3 d, spawning interval 7/5.3/4 h, lifespan 15/10/7 d, 20-23 eggs per female lifetime [FAO]; cultures go 50 to 200 per mL in 3 d (r 0.46 d-1) and 2 to 200 per mL in 7 d (r 0.66) [FAO] | G = 1.3 d at 25 C; r_max 0.7 |
| Ciliate | binary fission | 0.5-2 divisions per day (textbook) | G = 0.7 d; r_max 1.4 |
| Phyto | division | mu 0.5-1.0 d-1 [NANNO] | r_max 0.8 at 26 C, light-saturated |
| Bristleworm | fragmentation (each piece regrows; fragmentation takes 10-15 min, blastema in ~1 week, larvae develop in 88 h, SeaLifeBase via sources file) and broadcast spawning [EURY] | | G = 60 d (fragment to feeding adult); r_max 0.03 |
| Micro-snails | Collonista reproduce readily in tanks [COLL]; Stomatella broadcast spawns, planktonic larvae rarely survive the skimmer (hobby) | | Collonista G = 60 d, r_max 0.03; Stomatella spawning event every 30-60 d, 1-5 % larval survival (est.) |
| Spaghetti worm | spawn or fragment | | G = 90 d; r_max 0.01 |
| Foram | multiple fission | | G = 90 d; r_max 0.01 |
| Flatworm (Convolutriloba) | budding, one clone every 4-5 d per adult at 25 C [CONV] | | G = 4.5 d; r_max = ln(2)/4.5 = 0.15 (doubling 4-5 d), plus a 1.3x bonus above 300 PAR (est.) |

Temperature scaling: for every crustacean group use r(T) = r_max * exp(-((T - Topt)/w)^2) with Topt 27 C and w 6 C for tropical tank strains (Tisbe cultured at 28-32 C [TISB]; Apocyclops optimum 28-32 C [APOR, APO22]; Parvocalanus 24-26 C [REED]), and a hard cut from section 4. Generation time G(T) = G(27) * 2.5^((27 - T)/10) (Q10 2.5, consistent with Acartia 8.9 d at 22 C vs 40 d at 10 C [ACAR-D]).

### 2.5 Densities in tanks and refugiums

| Group | Field reference | Tank density, healthy mature system | Refugium / chaeto |
|---|---|---|---|
| Harpacticoid | 20 per cm2 in GBR algal turf (2025 per 100 cm2) [EAM]; 0.48 g m-2 wet | 2-20 per cm2 of rock film, 0.5-5 per cm2 of glass at night, 1-10 per cm2 of sand (est. around the field value) | 20-100 per cm2 of chaeto surface, or 5,000-30,000 per litre of chaeto (est.; culture yields of 28,000 L-1 d-1 show the upper bound [TISB]) |
| Calanoid | 50-500 per m3 by day on reefs [HEID04] = 0.05-0.5 per L; culture peaks 3.3-5.7 per mL [THAI], vendor limit 3 per mL [REED] | 0.1-2 per L resident; 10-50 per L for 1-3 d after a bottle dose (a 2,000-pod bottle in 227 L = 9 per L) | not refuge-dependent |
| Cyclopoid | 1,300-1,900 per m3 Oithona at night near the bottom [HEID04] = 1.3-1.9 per L; culture 11,600 per L [DIAL] | 0.5-5 per L at night | 2-20 per L in a sump with phyto |
| Gammarid | 2-5 per m3 in the column at night [HEID04]; crevice densities of 10-100 per 100 cm2 rubble (est.) | 0.1-1 per cm2 of rock surface equivalent, i.e. 2,000-15,000 in this tank's rock (est., hobby "hundreds to thousands") | 5-50 per litre of chaeto |
| Caprellid | | 0 to 50 per tank; patchy, booms on hydroids | 10-100 per litre of chaeto |
| Mysid | 6 per m3 night [HEID04] | 0 to 200 per tank; rarely self-sustaining with these fish | 20 per L is the culture limit [MYS] |
| Ostracod | 11-64 per m3 night [HEID04] | 0.5-5 per cm2 sand | 1-10 per cm2 |
| Rotifer | culture 200-300 per mL harvest density, egg ratio 30 % at 150 per mL falling to 10 % at 2,000 [FAO] | <0.1 per mL resident; 5-50 per mL after a dose for 1-2 d | 0.5-5 per mL with phyto |
| Ciliate | mixed protozoa cultures peak 30,000-40,000 cells/mL on day 4, Euplotes monoculture 5,000-6,000 [MAB20] | 1-100 per mL; 10^3-10^4 per mL in a new tank "bacterial bloom" | |
| Phyto | | 10-1,000 cells/mL resident; a 50 mL dose of 10^9 cells/mL into 227 L gives 2 x 10^5 cells/mL, cleared in 1-2 d | |
| Bristleworm | | 20-300 per tank of mixed size (hobby) | |
| Collonista / Stomatella | | 50-500 Collonista, 5-30 Stomatella per tank (hobby) | |
| Forams | | 1-10 per cm2 of shaded rock | |
| Flatworm | | 0 (absent) or 1-10 per cm2 on infested rock within 6-10 weeks | |

### 2.6 Predators in this tank

Prey preference weights (0-1) by predator; rate is pods per day at saturation, est. unless cited. Column: H harpacticoid, C calanoid, Y cyclopoid, G gammarid, K caprellid, M mysid, O ostracod, R rotifer, B bristleworm, S micro-snail, F flatworm.

| Predator | H | C | Y | G | K | M | O | R | B | S | F | Saturation rate and notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Pajama cardinal (night, demersal plankton eater [APOG]) | 0.8 | 0.6 | 1.0 | 0.9 | 0.3 | 1.0 | 0.3 | 0 | 0 | 0 | 0 | 200-400 pods or 20-40 amphipods per night, est.; the congener S. orbicularis eats mainly gammarid amphipods and tanaids, with harpacticoids added by smaller fish [MEES99]; only lights-off hours |
| Cleaner wrasse | 0.7 | 0.3 | 0.5 | 0.3 | 0.3 | 0.2 | 0.3 | 0 | 0 | 0.2 | 0.3 | adults eat ~1,200 gnathiids per day in the wild; juveniles take non-parasitic copepods [GRU00]; in a tank with few parasites substitute 300-800 pods per day (est.); day only |
| Falco hawkfish | 0.3 | 0.1 | 0.2 | 1.0 | 0.6 | 0.8 | 0.3 | 0 | 0.1 | 0.3 | 0 | small hawkfish eat mainly copepods, larger ones decapods [HAWK]; 20-60 amphipods per day, est.; perch-and-pounce, day |
| Indigo dottyback | 0.3 | 0.1 | 0.2 | 1.0 | 0.5 | 0.7 | 0.3 | 0 | 0.3 | 0.3 | 0 | 20-50 amphipods or bristleworm fragments per day, est.; picks crevices, day and dusk |
| Engineer goby | 0.2 | 0 | 0.1 | 0.6 | 0.1 | 0.3 | 0.6 | 0 | 0.2 | 0 | 0 | sifts sand; 20-40 benthic items per day, est. |
| Tailspot and lawnmower blennies | 0.3 | 0 | 0 | 0.1 | 0 | 0 | 0.2 | 0 | 0 | 0 | 0 | incidental with film grazing: 50-100 harpacticoids per day via the film they scrape (est.) |
| Ocellaris clowns (x2) | 0.4 | 0.6 | 0.4 | 0.1 | 0 | 0.3 | 0 | 0.3 | 0 | 0 | 0 | wild diet: copepods, larvae, algae, trophic level 2.98 [AMPH]; 50-150 column pods per fish per day when pellets are short, est. |
| Juvenile blue tang | 0.1 | 0.1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | incidental only |
| Coral banded shrimp | 0.1 | 0 | 0 | 0.5 | 0.3 | 0.3 | 0 | 0 | 1.0 | 0.1 | 0 | nocturnal; bristleworms are its hobby-reputed main prey; 1 worm or 5-10 amphipods per night, est. |
| Skunk cleaner shrimp | 0.1 | 0 | 0 | 0.3 | 0.2 | 0.2 | 0 | 0 | 0.2 | 0 | 0 | opportunist; 5 items per day, est. |
| Hermits, Sally Lightfoot | 0.1 | 0 | 0 | 0.3 | 0.1 | 0 | 0.1 | 0 | 0.3 | 0.5 (shells) | 0 | Sally Lightfoot nocturnal, takes amphipods and worms opportunistically; hermits kill Collonista for shells [COLL] |
| Brittle star | 0 | 0 | 0 | 0.2 | 0 | 0 | 0.1 | 0 | 0.1 | 0 | 0 | detritus mostly |
| Nassarius, Astrea, tube worm, asterina | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | tube worm filters phyto, rotifers and nauplii: est. 20 mL/h clearance for a 5 cm feather duster |
| GSP, clove polyps, Kenya tree, organ pipe (small-polyp octocorals) | 0 | 0.3 | 0.3 | 0 | 0 | 0 | 0 | 0.6 | 0 | 0 | 0 | small polyps take nauplii, rotifers and phyto-sized particles; stony-coral capture benchmarks 0.3-1.2 Artemia per polyp per hour for 1-2 mm polyps at 1-4 prey/mL [SAP23]; scale by prey density (section 3.4) |
| Zoas / palys | 0 | 0.2 | 0.2 | 0 | 0 | 0 | 0 | 0.3 | 0 | 0 | 0 | Zoanthus not seen feeding in nature and responded only to fish flesh; Palythoa captures crustacean zooplankton, optimum 200 Artemia/m3 [ZOA] |
| Mushrooms | 0 | 0.1 | 0.1 | 0.1 | 0 | 0.1 | 0 | 0.1 | 0 | 0 | 0 | occasional engulfment of a trapped pod or mysid |
| Mysids (as predators) | 0.5 nauplii | 0.5 nauplii | 0.5 nauplii | 0 | 0 | 0.2 cannibal | 0 | 1.0 | 0 | 0 | 0 | 150 Artemia-sized items per day [MYS] |
| Caprellids, flatworms (as predators) | 0.2 nauplii | 0.2 | 0.2 | 0 | 0 | 0 | 0 | 0.5 | 0 | 0 | 0 | small |
| Cirolanid isopod (if present) | attacks fish, not pods | | | | | | | | | | | one bite event per night per isopod on a sleeping fish: wound and stress flag |

Total predation budget in this stock list (est. day-night sum at saturation): about 1,000-2,000 copepods and 50-150 amphipods per day. For comparison a mandarin is credited with "hundreds, if not thousands" per day [TSA-M]; the cardinal plus cleaner wrasse plus two clowns together approach one mandarin.

---

## 3. Population dynamics model

### 3.1 State and equations

State per group g and habitat compartment h (rock film, rock interior, sand, glass, chaeto, column): density N_g,h (ind. per cm2 for benthic, per L for planktonic). Keep a juvenile fraction J_g (nauplii/young) as a second density with the same equations but higher mortality and lower per-capita predation weight, because mysids, corals and flatworms eat juveniles while fish take adults.

```
dN/dt = N * r_g(T, S, Cu, DO, pH) * f_food * (1 - N/K_g,h) - P_g,h - m_g * N + migration + dosing
f_food = F/(Ks_g + F)            # F is the relevant 09 pool: film g DW m-2, detritus g m-2, phyto cells/mL
K_g,h  = K_base_g,h * refuge_h * (0.3 + 0.7 * f_food)   # carrying capacity scales with food and shelter
P_g,h  = sum over predators p of  rate_p * w_p,g * a_p,h * E_g,h(t) * N / (N_half + N)   # Holling II on EXPOSED density
E_g,h(t) = diel exposure fraction from 2.2
m_g    = background mortality 0.02-0.05 d-1 (crustaceans), 0.2 (rotifers), 0.3 (phyto sinking/skimmer)
```

refuge_h: rock interior 1.0, chaeto 1.0, rock film 0.5, sand 0.4, glass 0.1, column 0 (K for column groups is set by food, not refuge). a_p,h is predator access: fish 1.0 on glass and open rock, 0.3 on sand (gobies 1.0), 0.05 in rock interior, 0.02 in chaeto in a sump (only shrimp and no fish reach a sump).

Food feedback into 09: harpacticoid grazing removes G = N_H * 0.005 mg DW film per pod per day (est. from 3-11 % of microalgal biomass per day at 20 per cm2 [HARP-G]); amphipods and micro-snails remove detritus and film at their 2.3 rates; bristleworms are the main detritus sink (set bristleworm detritus consumption so that 100 worms clear 0.5 g wet uneaten food per night, est.). Pods are themselves a detritus source on death (dead biomass to the detritus pool) and an ammonia source: 10,000 copepods at 2 ug DW each is 20 mg DW, negligible for N; only mass die-offs (section 4) matter chemically.

### 3.2 Carrying capacities and starting values

| Group | K_base and compartment | Starting value, mature tank | Ks (food) |
|---|---|---|---|
| Harpacticoid | 30 per cm2 rock film and chaeto; 10 per cm2 sand; 5 per cm2 glass; 50 per cm2 rock interior pore surface (est. around 20 per cm2 field [EAM]) | 30 % of K | 1 g DW m-2 of film + detritus |
| Calanoid | 5 per L column when phyto >= 10^4 cells/mL, 0.3 per L at background | 0.2 per L | 5,000 cells/mL [REED: 50,000 cells/mL recommended in culture; tank is leaner] |
| Cyclopoid | 5 per L column; 10 per cm2 rock pores | 0.5 per L | 2,000 cells/mL phyto or 0.5 g m-2 detritus |
| Gammarid | 1 per cm2 rock interior, 0.3 per cm2 rock face, 20 per L chaeto | 30 % of K | 2 g m-2 detritus + film |
| Caprellid | 0.2 per cm2 of hydroid/soft-coral/chaeto surface | 0 unless seeded | 1 per L nauplii + detritus |
| Mysid | 0.5 per L column with pod nauplii > 2 per L; else 0.05 | 0 unless seeded | 2 nauplii per L |
| Ostracod | 5 per cm2 sand | 1 per cm2 | 1 g m-2 detritus |
| Rotifer | 50 per mL with phyto >= 10^5 cells/mL; 0.05 per mL background | 0.01 per mL | 2 x 10^4 cells/mL |
| Ciliate | 100 per mL; 10^4 per mL during a bacterial bloom | 10 per mL | DOC index from 09 |
| Phyto | self-limited by N, P, light; chemostat form | 100 cells/mL | NO3 Ks 1 uM, PO4 Ks 0.1 uM |
| Bristleworm | 0.02 per cm2 sand + rock = 400 for this tank | 100 | 1 g m-2 detritus |
| Collonista | 0.05 per cm2 rock face | 100 | film 1 g DW m-2 |
| Flatworm | 20 per cm2 rock in light | 0 (seeded by a frag event) | light > 100 PAR |

### 3.3 Booms and crashes

| Scenario | Driver | Expected trajectory (sim target) |
|---|---|---|
| New tank, dry rock, no seeding | no colonists except ciliates and bacteria | ciliates to 10^3-10^4 per mL in week 1-3 with the ammonia peak, then crash to 10 per mL by week 6 as bacteria stabilise; copepods and amphipods stay at 0 until live rock, a frag or a bottle introduces them |
| New tank, live rock | harpacticoids and amphipods arrive at 1-5 % of K | harpacticoids reach 60 % of K in 4-6 weeks at 26 C (r 0.3 d-1 with food), feeding on the diatom pulse of 09 A2; amphipods take 3-5 months (r 0.08) |
| Bottle dose of Tisbe (2,000-5,000 pods) | adds 2-5 per cm2 to glass and rock face immediately | 50-80 % eaten or dispersed into pores in 48 h with this fish list; if dosed at lights-out with pumps off for 1 h [TSA-M] the first-night loss falls to 20-30 % (est.) |
| Tigger pod dose with phyto | +9 per L calanoids plus 2 x 10^5 cells/mL phyto | boom: phyto cleared in 1-2 d by pods, rotifers, tube worm and skimmer; pods peak at 1.5-2x dose on day 4-7 (one generation, r 0.45 with saturating food), then decline at m + predation to <1 per L by day 14-21 unless phyto is redosed every 2-3 days |
| Phyto dosed without pods | phyto | cleared by the skimmer and filter at 0.3 d-1 plus the tube worm; a 3x/week dose holds 10^3-10^4 cells/mL background |
| Predator overgrazing | add a heavy pod eater (a mandarin at 1,000-3,000 per day) to a tank whose harpacticoid production is below that | exposed pods on glass fall to near zero within 1-2 weeks; rock-interior pods persist (access 0.05) and keep seeding at ~5 % of K; when the predator leaves, recovery to 60 % of K takes 3-4 weeks |
| Chaeto harvest or refugium removal | K drops by the chaeto term | amphipod and pod counts fall 30-60 % over the following 2 generations |
| Heat spike to 34 C for a day | section 4 | calanoids die; harpacticoids survive, egg production pauses; mysids die |
| Copper treatment | section 4 | every crustacean, worm and snail group goes to zero within 1-3 days, recolonisation only by re-seeding |
| Antibiotic or dino-blackout | ciliates and bacteria crash | pods lose their bacterial food for a week; harpacticoid r drops 50 % for 7 days |
| Flatworm infestation | one frag carries 5 flatworms | at r 0.15 d-1 and K 20 per cm2 on lit rock the visible stage (1 per cm2) is reached in 5-6 weeks, saturation in 8-10 weeks; sudden death of a saturated population (treatment) dumps toxin: apply the 09 die-off cascade with a toxicity multiplier on fish stress (hobby: fish losses after mass flatworm kills) |

### 3.4 Coupling to corals

Octocorals and zoanthids in this tank are mostly photosynthetic; heterotrophy is a bonus. Model each polyp as a Holling II capturer: captured per polyp per hour = c_max * D/(D_half + D) with D the exposed prey density in ind./mL. Benchmarks from stony corals: Acropora 0.31, 0.57 and 1.22 per polyp per hour at 1, 2 and 4 prey/mL [SAP23]; Galaxea (large polyps) 16-48 [SAP23]. For 1-2 mm octocoral polyps use c_max 1 per polyp per hour and D_half 2 per mL for nauplii and rotifers; for adult copepods multiply by 0.3 (they escape at 300-500 BL/s [KIO10]). Zoas get c_max 0.3 and only respond to prey >= 0.1 mm or to dosed food [ZOA]. At resident densities (0.001-0.005 per mL) capture is about 0.0005-0.0025 per polyp per hour, so a 500-polyp GSP mat takes 6-30 pods per day: real but minor. During a 10-50 per mL dose capture rises 1,000x for a day or two; feed this as a small growth bonus (+5 % growth rate for 3 days per well-fed day, est.) in the coral model and show the polyps "snapping" in the zoomed view.

Harm the other way: amphipods are blamed in hobby reports for nipping zoas and GSP; keep a nuisance flag when gammarids exceed 0.5 per cm2 and food is scarce (f_food < 0.3), causing a 5 % polyp-retraction chance per day on zoas.

---

## 4. Water parameters and the heat model

| Parameter | Effect | Sourced anchors | Sim rule |
|---|---|---|---|
| Temperature | r(T) hump (2.4); hard limits | Acartia adult CTmax 30.3, 33.9 and 35.9 C when acclimated to 18, 23 and 28 C; nauplii 26.2, 29.5 and 34.1 C [ACAR-T]. Apocyclops produces up to 36 C, best 32 C [APOR]. Tisbe cultured at 28-32 C [TISB]. Mysids cultured at 26 +/- 1 C [MYS]; Caprella field 13.5-29 C [AOKI] | Acclimated to 26-27 C: calanoid adults die above 34 C (nauplii above 32), cyclopoids and harpacticoids above 36-37 C (est. from Apocyclops at 36), gammarids and caprellids above 33-34 C (est.), mysids above 32 C (est.). Use the 08 heat timeline: at 33 C mysids and calanoid nauplii decline 20 %/d; at 35 C calanoids and mysids lose 50 %/d and amphipods 20 %/d; at 37 C all crustacean microfauna lose 50 %/d; rotifers and ciliates survive to 38-40 C; phyto Nanno grows to 35 C. Below 20 C all r fall below 0.3 r_max and G doubles (Acartia 40 d at 10 C [ACAR-D]) |
| Salinity | survival window and fecundity | Tisbe holothuriae 24 h LC50 15 ppt low / 93 ppt high, zero mortality from 20 ppt up, most offspring at 32 ppt, fewest at 60 [SAL22]; Tigriopus 1-132 ppt [SAL22]; Oithona nana optimum 20 ppt [OITH]; Apocyclops royi best at 10-21 ppt, 7 PSU off optimum costs 22-25 % of nauplii [APO22, APOR]; Parvocalanus kept at 30-35 ppt [REED]; mysids 20-30 ppt [MYS] | between 28 and 38 ppt no effect; 20-28 ppt r x 0.7 for harpacticoids and amphipods, x 1.0 for cyclopoids; below 20 ppt harpacticoids lose 50 %/d (LC50 15), amphipods and mysids 30 %/d; above 40 ppt r x 0.5, above 50 ppt mortality 20 %/d. A 10-20 % water change with 2 ppt mismatch does nothing (vendor consensus [podyourreef], consistent with [SAL22]) |
| Nitrate | indirect via food | culture systems run at tens of ppm NO3 without harm (culture manuals) | no direct effect to 100 ppm; above 100 ppm r x 0.8; pods benefit when NO3 feeds film and phyto |
| Phosphate | indirect | | none direct |
| Copper | lethal | marine amphipods (Melita) 96 h LC50 120 ug/L for juveniles, adults 1.2-1.5x tolerant [CU-AMP]; Hyale: Cu the most toxic metal tested [CU-HYA]; Acartia Cu LC50s depend on salinity and DOM, higher salinity protective [CU-ACA]; Tisbe battagliai is a standard Cu test organism (48 h LC50 in the tens of ug/L range in the ISO-type tests; value not retrieved). Outlier: Tigriopus japonicus 96 h LC50 32.75 mg/L [SKKU] is a rock-pool extremophile and must not be used as a tank limit | Cu 0.02 ppm (20 ug/L): copepod r = 0, 10 %/d mortality; 0.05 ppm: 50 %/d for copepods, rotifers, 30 %/d amphipods, worms, snails; 0.15 ppm (fish treatment level): everything but ciliates and phyto gone in 3 d; recolonisation needs re-seeding; copper leached from rock keeps r x 0.5 for 30 d after treatment |
| Dissolved O2 | tolerance high in harpacticoids | harpacticoids survive near-anoxia > 120 h but egg mass formation falls and stops when NH4 is also 10 uM [HYPX]; rotifer culture minimum 2 mg/L [FAO] | DO < 2 mg/L: calanoids and mysids 30 %/d, amphipods 20 %/d, harpacticoids r x 0.2, no deaths for 5 d; DO < 0.5 mg/L: amphipods 50 %/d, harpacticoids 10 %/d. Pods do not drive DO themselves |
| pH | mild | copepod pH tolerance broad; low pH mainly slows calcifiers (ostracods, forams, Collonista) | pH 7.6-8.5 no effect; below 7.6 ostracod, foram and snail r x 0.5; above 8.6 copepod r x 0.7 (09 Part B [COP]) |
| Ammonia | culture nuisance | 10 uM NH4 (0.14 ppm NH4-N) with hypoxia stopped harpacticoid egg clutches [HYPX] | NH3-N > 0.1 ppm: r x 0.5 all crustacean groups; > 0.5 ppm: 20 %/d mortality (less sensitive than fish, 05) |
| Flow | distribution | | calanoids and rotifers are swept into the overflow: loss 0.1 d-1 at 20x turnover with a skimmer, 0.3 d-1 with filter socks; harpacticoids prefer 3-15 cm/s surfaces; mysids hold under overhangs when v > 15 cm/s |
| Light | phyto and flatworm driver; pod phototaxis | Parvocalanus swims toward light [REED]; flatworm symbionts | calanoid local density near a point light x5 within 5 cm (torch test); flatworm r bonus above 300 PAR |
| Medications (fenbendazole, praziquantel, Flatworm Exit, fluconazole) | | hobby reports: fenbendazole kills bristleworms, snails and some pods; praziquantel mostly safe for pods; flatworm treatments kill flatworms within 1 h and are mildly toxic to pods | fenbendazole: worms 90 %/d, snails 50 %/d, pods 20 %/d; prazi: pods 5 %/d, flatworms 50 %/d; levamisole-type flatworm killer: flatworms 100 % in 1 h with the toxin dump, pods 20 % |

---

## 5. Microscope view design

### 5.1 What is visible at each magnification

| Magnification | Field of view (long edge) | On rock | On glass | On sand | In the water column |
|---|---|---|---|---|---|
| 1x (naked eye, torch at night) | 20-40 cm | amphipods darting, bristleworm heads, Stomatella, caprellids swaying, mysid schools of 5-30 under ledges | pods as moving white specks 0.5-1 mm, flatworm patches as rust spots, Collonista dots | ostracod and worm trails, spaghetti worm tentacles, cirolanid tracks | mysids only; a dosed tank is faintly green or cloudy |
| 3-10x (hand lens, macro camera) | 2-8 cm | harpacticoids crawling in the film, egg sacs visible as paired lobes, amphipod legs resolved, caprellid gnathopods, foram discs, flatworm lobes | copepod hop-and-sink, Tisbe crawling tracks, diatom film texture, rotifers as moving dust only at 10x | ostracod valves, nematode-like syllid worms, pod nauplii as jittering dots | calanoid hops and antennae, mysid pleopods, rotifers as motes |
| 40x (low-power microscope) | 0.5 cm | diatom chains, Euplotes crawling on cirri, Vorticella bells on stalks, foram pseudopod net, nauplii with three limb pairs, copepod gut colour | same plus biofilm bacteria haze | sand grains with film, forams, harpacticoid mandibles working | Tetraselmis cells tumbling, Nannochloropsis as motionless dots at the limit, rotifer corona beating, ciliate spiral paths, nauplii |

Rule: resolve a group only when its size exceeds 2 pixels at the chosen scale. With a 1080 px wide field: at 1x (30 cm) one pixel is 0.28 mm, so adults over 0.6 mm render; at 10x (3 cm) one pixel is 28 um, so nauplii (0.07-0.1 mm) and rotifers render but phyto does not; at 40x (0.75 cm) one pixel is 7 um, so Tetraselmis (10 um) renders as a 1-2 px green dot and Nannochloropsis (2-3 um) stays sub-pixel, shown only as a green haze whose opacity tracks cells/mL.

### 5.2 Counts per field of view

Count = density x field area (benthic) or density x field volume (column, with a depth of field of 1 mm at 40x, 5 mm at 10x, 50 mm at 1x). Multiply by the diel exposure fraction E (2.2).

| Scene | Density assumed | 1x (30 x 17 cm, 510 cm2) | 10x (3 x 1.7 cm, 5 cm2) | 40x (0.75 x 0.42 cm, 0.3 cm2) |
|---|---|---|---|---|
| Rock face, day, harpacticoids 10 per cm2 (E 0.15) | 1.5 exposed per cm2 | 750 specks, show the 100-200 nearest the focus plane | 7-8 | 0-1 |
| Rock face, night (E 0.6) | 6 per cm2 | 3,000 (render 300 and a shimmering texture) | 30 | 2 |
| Glass, night, 2 per cm2 | 1.4 exposed | ~700 | 7 | 0-1 |
| Glass, day | 0.3 | 150 | 1-2 | 0 |
| Amphipods, rock face, night, 0.3 per cm2 x E 0.5 | 0.15 | 75 | 1 | 0 |
| Sand, ostracods 2 per cm2, E 0.3 | 0.6 | 300 | 3 | 1 |
| Column, resident calanoids 0.5 per L | 0.0005 per mL | field volume 2.5 L: 1-2 | 25 mL: 0 (occasional) | 0.3 mL: 0 |
| Column, after a Tigger dose, 20 per L | 0.02 per mL | 50 | 0.5 | 0 |
| Column, dosed phyto 2 x 10^5 cells/mL | | green tint | haze | 60 cells of Tetraselmis-size; Nanno as haze |
| Column, rotifer dose 20 per mL | | cloud | 500 (show as motes) | 6 |
| Ciliates in a new-tank bloom, 5,000 per mL | | cloudy water | motes | 1,500 (show 50-100 and a swarm texture) |
| Flatworm infested rock, 5 per cm2 | | 2,500 (a rust sheet) | 25 | 2 |

Implementation: at 1x the view is the existing tank renderer; at 10x and 40x spawn individuals by Poisson sampling from these expectations, capped at 300 sprites, and render any remainder as an animated texture layer (shimmer density proportional to the uncapped count).

### 5.3 Motion patterns to animate

| Group | Animation primitive | Parameters |
|---|---|---|
| Calanoid | hop (impulse) then exponential velocity decay; sink at 0.5-1 mm/s between hops; antennae flare on hop | hop interval 0.5-2 s; hop distance 1-3 BL; escape on predator or torch proximity: 10-20 BL in 50 ms with 2-4 beats [KIO10]; attraction vector toward the brightest point |
| Cyclopoid | same with longer pauses (1-5 s) and shorter hops | egg sacs trail and lag the body |
| Harpacticoid | surface-constrained random walk; body flexes in a sine of 2-4 Hz when moving; stops to feed 0.5-3 s | crawl 1-3 mm/s; 5 % chance per second of a 2-5 mm swim-hop when a shadow passes |
| Gammarid | two states: scuttle (surface, legs cycling 5-8 Hz, 5-20 mm/s) and side-swim (free, body rolled 90 degrees, pleopod blur, 30-80 mm/s, slight curve to the path); flee into cover when a fish sprite is within 5 cm | swim bouts 0.5-3 s |
| Caprellid | anchored node; body sways as a pendulum at 0.5-1 Hz with flow; gnathopods snap every 2-10 s; occasional inchworm relocation of 1-2 BL | |
| Mysid | hover with 1-2 mm positional jitter at 2-3 Hz; heading into flow; school cohesion (Boids) with 1-2 BL spacing; tail-flip backward 5-10 BL when startled | 5-20 mm/s drift |
| Ostracod | tumbling bean; antennae appear and vanish; short straight runs | 1-3 mm/s |
| Rotifer | forward glide with a slow roll (1 turn per 1-2 s); corona drawn as two rotating rings of highlights (the "wheel" illusion); periodic foot-anchor and body pivot | 0.3-1 mm/s |
| Euplotes | jerky crawl on the surface in 0.1-0.5 mm steps with 90-degree turns; when swimming, helical path | step every 0.3-1 s |
| Vorticella | fixed stalk, bell with a cilia shimmer; stalk coils to 1/5 length in 10 ms on disturbance, re-extends in 1-2 s | |
| Tetraselmis | straight run 0.5-2 s then abrupt random re-orientation [TETRA]; slow body spin | 0.1-0.3 mm/s |
| Nannochloropsis | Brownian jitter only; drift with flow | |
| Nauplius (all copepods) | tiny triangle, three limb pairs flicker; hop-and-sink at 1 BL per hop | hop every 0.3-1 s |
| Bristleworm | head-first glide with parapodial metachronal wave travelling backward at 1-2 Hz; bristles flare when touched; reverses into a hole in 0.5 s | 5-20 mm/s |
| Stomatella | smooth glide, mantle ripples, tentacles sweep; speed 2-5x other snails | 1-3 mm/s |
| Spaghetti worm | tentacles extend along the surface as growing splines at 0.1-0.5 mm/s; particles move along each tentacle toward the base; full retraction in 1-2 s on contact | |
| Foram | static; at 40x a faint radiating pseudopod net with particles creeping inward at 1 um/s | |
| Flatworm | ciliary glide 0.5-1 mm/s with edge ripples; cluster toward the brightest spot in the field; detach and drift when flow spikes | |
| Coral polyps (GSP, clove) at 10x | tentacles wave at 0.3-0.5 Hz; on a pod sprite entering the tentacle radius, a 0.3 s close and 5-10 s hold, probability from 3.4 | |

### 5.4 Lighting and colour at scale

- Night torch scene (1x): render pods as bright white-blue specks against dark rock, strong specular from the beam, calanoids drifting toward the beam within 10-30 s, amphipods as fast grey streaks with a moving shadow, mysid eyes as paired reflective points.
- Macro 10x under daylight LEDs: diatom film is golden-brown with a granular texture, GSA is bright green dust; pods are tan to amber with a dark gut; amphipods olive or grey-brown with a clear exoskeleton edge; caprellids match their perch; flatworms rust-red with a lighter rim; forams pink-red (Homotrema) or beige discs.
- 40x: use transmitted-light look (bright field): bodies translucent with a dark outline, internal gut visible as a brown or green band depending on diet (green after a phyto dose), egg sacs grey-brown granular, diatoms golden with bilateral symmetry, Tetraselmis grass-green, ciliates glassy with a faint cilia shimmer, bacteria as a grey haze whose density tracks the 09 DOC index.
- Under blue (actinic) light at any scale: fluorescence off; pods appear dim grey, GSP and zoas fluoresce, flatworms are dark patches on fluorescing zoas.

---

## 6. Implementable spec

### 6.1 Per-group parameter table

| Group | Class | Size (mm) | Cruise / escape (mm/s) | r_max (d-1) at 27 C | G (d) at 27 C | Topt / T_lethal (C) | K | Food link (09 pool, Ks) | Predator links (weight from 2.6) | Diel exposure day / night | Column fraction day / night |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Harpacticoid | benthic | 0.8 | 2 / 100 | 0.30 | 10 | 28 / 37 | 30 cm-2 rock film, 10 sand, 5 glass, 50 pores, 30 chaeto | film + detritus, Ks 1 g DW m-2 | cardinal 0.8, wrasse 0.7, clowns 0.4, blennies 0.3 (film), hawk 0.3, dotty 0.3, goby 0.2 | 0.15 / 0.6 | 0.01 / 0.05 |
| Calanoid (Tigger) | planktonic | 0.3 | 3 / 300 | 0.45 | 7 | 26 / 34 (nauplii 32) | 5 L-1 with phyto, 0.3 background | phyto, Ks 5,000 cells/mL; Nanno x0.2 value [REED] | clowns 0.6, cardinal 0.6, wrasse 0.3, corals 0.3, tube worm (nauplii) | 1 / 1 | 1 / 1 |
| Cyclopoid | mixed | 0.9 | 2 / 200 | 0.40 | 7 | 28 / 36 | 5 L-1 column, 10 cm-2 pores | phyto 2,000 cells/mL or detritus 0.5 g m-2 | cardinal 1.0, wrasse 0.5, clowns 0.4, corals 0.3 | 0.3 / 0.8 | 0.2 / 0.6 |
| Gammarid | benthic | 6 | 10 / 150 | 0.08 | 30 | 27 / 34 | 1 cm-2 pores, 0.3 rock face, 20 L-1 chaeto | detritus + film 2 g m-2; hair algae x0.1 | hawk 1.0, dotty 1.0, goby 0.6, cardinal 0.5, CBS 0.5, SLF crab 0.3 | 0.05 / 0.5 | 0.005 / 0.05 |
| Caprellid | benthic | 10 | 2 / - | 0.06 | 40 | 25 / 33 | 0.2 cm-2 of hydroid, soft-coral and chaeto surface | detritus + nauplii | hawk 0.6, dotty 0.5, wrasse 0.3 | 0.6 / 0.6 | 0 |
| Mysid | mixed | 7 | 10 / 250 | 0.10 | 25 | 26 / 32 | 0.5 L-1 with nauplii > 2 L-1 | nauplii + rotifers, 150 items d-1 | cardinal 1.0, hawk 0.8, dotty 0.7, clowns 0.3 | 0.3 / 0.9 | 0.3 / 0.9 |
| Ostracod | benthic | 0.8 | 2 / 15 | 0.05 | 35 | 27 / 36 | 5 cm-2 sand | detritus 1 g m-2 | goby 0.6, cardinal 0.3 | 0.05 / 0.3 | 0 |
| Isopod (harmless) | benthic | 2 | 3 / - | 0.04 | 45 | 27 / 35 | 0.1 cm-2 rubble | detritus | hawk 0.3, dotty 0.3 | 0.1 / 0.4 | 0 |
| Cirolanid | benthic pest | 5 | 5 / 80 | 0.03 | 60 | 27 / 36 | 0 unless seeded; 0.01 cm-2 sand | fish blood (bite events) | none effective | 0 / attack | - |
| Rotifer | planktonic | 0.2 | 0.5 / - | 0.70 | 1.3 | 28 / 40 | 50 mL-1 with phyto >= 10^5 cells/mL | phyto 2 x 10^4 cells/mL | mysids 1.0, corals 0.6, tube worm, clowns 0.3; skimmer 0.3 d-1 | 1 / 1 | 1 / 1 |
| Ciliate | film + column | 0.1 | 0.5 / - | 1.4 | 0.7 | 28 / 40 | 100 mL-1; 10^4 in a bloom | DOC index | calanoids, rotifers, mysids | 1 / 1 | 0.5 / 0.5 |
| Phyto (Nanno / Tetra) | planktonic | 0.003 / 0.012 | 0 / 0.2 | 0.8 (light-saturated) | 0.9 | 27 / 36 | N, P limited | NO3 Ks 1 uM, PO4 0.1 uM, Ik 100 PAR | all filter feeders; skimmer + socks 0.3-0.5 d-1 | 1 / 1 | 1 / 1 |
| Bristleworm | benthic | 50 | 10 / 50 | 0.03 | 60 | 27 / 36 | 0.02 cm-2 sand + rock | detritus 1 g m-2 (primary sink) | CBS 1.0, dotty 0.3, SLF 0.3 | 0.02 / 0.5 | 0 |
| Collonista / Stomatella | benthic | 2 / 12 | 0.5 / 2 | 0.03 / event | 60 / - | 27 / 35 | 0.05 cm-2 rock face / 30 per tank | film 1 g DW m-2 | hermits 0.5, wrasse 0.2, hawk 0.3, dotty 0.3 | 0.1 / 0.7 | 0 |
| Spaghetti worm | sessile | 30 (+150 tentacles) | 0 | 0.01 | 90 | 27 / 36 | 1 per 50 cm2 rock-sand junction | sand detritus within reach | fish nip tentacles (regrow) | 0.6 / 1.0 | 0 |
| Foram | sessile | 1-10 | 0 | 0.01 | 90 | 27 / 35 | 10 cm-2 shaded rock | detritus; light (soritids) | none | 1 / 1 | 0 |
| Flatworm | benthic pest | 3 | 0.7 / - | 0.15 (+30 % above 300 PAR) | 4.5 | 26 / 32 | 20 cm-2 lit rock | light; nauplii | wrasse 0.3 (reluctant), none others | 1 / 1 | 0 |

T_lethal is the temperature at which the 50 %/d mortality tier starts for 26-27 C acclimated animals; the 20 %/d tier starts 2 C lower. Everything slides with acclimation by about 0.4 C per 1 C of recent mean temperature (Acartia: +5.6 C CTmax for +10 C acclimation [ACAR-T]).

### 6.2 Aggregate-plus-sampled-individuals scheme

1. Simulate every group as densities per compartment with the 3.1 equations at a 1 h step (10 min during dosing or heat events). Keep juvenile and adult sub-pools for the five crustacean groups.
2. Derive "visible expectation" per scene: lambda = density x exposure E(t) x field area (or volume x depth of field) from 5.2.
3. When the user opens the zoom view on a surface or a water sample, draw n ~ Poisson(lambda) per group, capped at 300 sprites total with a texture layer for the remainder, and give each sprite a position, heading and a behaviour state machine from 5.3. Individuals are ephemeral: they are not tracked when the view closes. Persist only a random seed per view location so re-opening the same spot shows a consistent-looking population for the same hour.
4. Predation events in the view are cosmetic draws from the same hourly aggregate kill count (fish sprite in frame takes P_g,h x field share), so what the user sees matches the model's numbers.
5. Dosing: a bottle adds N to the relevant compartment at once; a phyto dose adds cells/mL to the column pool and raises the green haze alpha linearly with log10(cells/mL) from 10^3 (invisible) to 10^6 (opaque green).
6. Expose to the owner: per-group counts (tank total and per cm2 or per L), trend arrows, the night torch estimate (pods per 100 cm2 of glass at lights-out + 1 h), and a "pod budget" showing daily production versus predation.

### 6.3 Acceptance checks

1. **Heavy feeder crash.** Mature tank at 60 % harpacticoid K (about 10 per cm2 rock film), no refugium. Add a mandarin-class predator at 2,000 pods per day. Exposed glass density falls below 0.2 per cm2 within 10-14 days; total tank harpacticoids fall below 20 % of K by day 21 but never below 3 % (rock pores). Remove the predator: recovery to 60 % of K in 21-35 days at 26 C.
2. **Tigger pod boom with phyto.** Dose 2,000 Parvocalanus (9 per L) plus phyto to 2 x 10^5 cells/mL at lights-out. Phyto falls below 10^4 cells/mL within 48 h. Calanoids peak at 1.5-2x the dose between day 4 and 7, then fall below 1 per L by day 21 without further phyto. With phyto redosed every 2 days to 5 x 10^4 cells/mL the population holds at 3-5 per L indefinitely minus predation.
3. **Night emergence ratio.** At any mature state, the count of visible crustaceans on an open rock face at lights-out + 1 h is 3-5x the count at midday, and the column copepod density is 2-4x. Cardinalfish consumption occurs only in the dark hours and accounts for at least 40 % of total pod predation in this stock list.
4. **Harpacticoid colonisation from live rock.** New tank with live rock seeded at 2 % of K and a diatom film of 3 g DW m-2 at 26 C: harpacticoids reach 50 % of K in 28-42 days; with no film (f_food 0) they do not exceed 10 % of K in 60 days.
5. **Amphipod slowness.** From the same start, gammarids take 90-150 days to reach 50 % of K; adding a 2 L chaeto clump raises the 180-day total amphipod count by at least 50 %.
6. **Copper wipe-out.** Cu held at 0.15 ppm for 5 days: all crustacean, worm and snail microfauna below 1 % of their pre-treatment values by day 3; ciliates and phyto unaffected; no recovery in 60 days without re-seeding; with a re-seed on day 30, harpacticoids reach only 50 % of their expected growth over the next 30 days because of residual copper (r x 0.5).
7. **Heat tolerance ordering.** Ramp from 27 C to 35 C over 6 h and hold 24 h, then return: mysids and calanoids lose more than 70 %, amphipods 30-60 %, harpacticoids and cyclopoids under 30 %, rotifers and ciliates under 10 %. Egg-sac carrying harpacticoid fraction drops to near zero during the hold and recovers within 5 days.
8. **Salinity shock.** A 20 % water change with new water at 1.021 (29 ppt) into a 1.026 tank changes pod counts by less than 5 % over the next week. A drop of the whole tank to 1.015 (20 ppt) for 24 h kills 30-50 % of harpacticoids and most mysids.
9. **Coral capture is minor at resident density but visible in a dose.** With GSP at 500 polyps, resident calanoids 0.5 per L and cyclopoids 1 per L at night: coral capture is 5-40 pods per day (under 5 % of total pod mortality). During a 20 per L dose it exceeds 500 per day for 24 h and the coral model receives a feeding bonus.
10. **Flatworm infestation timeline.** Five Convolutriloba on a frag in a lit, low-flow rock zone at 26 C: visible density of 1 per cm2 on that rock in 35-45 days, saturation (20 per cm2) by day 60-75; a 100 % kill at saturation on a 1,500 cm2 infested area triggers the 09 die-off cascade scaled to 30,000 individuals (about 3 g wet) with a fish-stress multiplier of 2 for 48 h.
11. **View count consistency.** Opening the 10x glass view at night in the state of check 1 before the predator shows 5-10 harpacticoids per field; 14 days after adding the predator it shows 0-2; the 40x view shows nauplii only when the juvenile pool exceeds 20 % of adults.

---

## 7. Gaps and uncertainties

- No measured consumption rate of copepods or amphipods by Sphaeramia nematoptera, Labroides dimidiatus in captivity, Cirrhitichthys falco, Pseudochromis fridmani or Pholidichthys leucotaenia was found; the per-fish rates in 2.6 are scaled from body size and the mandarin benchmark ("hundreds to thousands per day" [TSA-M]), and the cardinal's share rests on apogonid night-foraging evidence [APOG] rather than a feeding rate.
- Tisbe generation time versus temperature rests on one 18 C life table [BERG81] and rm 0.304 for a different species on its best diet [TISH], scaled with the Acartia Q10 [ACAR-D]; no tropical-temperature Tisbe life table was found. Tisbe battagliai copper LC50 values were not retrieved (abstract only).
- Tank densities per cm2 for harpacticoids and amphipods are anchored on one field value (20 per cm2 in GBR turf [EAM]) and hobby language; no published census of a reef aquarium or refugium was found. The BRS refugium video gives no counts.
- Soft coral (GSP, clove, Kenya tree) zooplankton capture rates do not exist in the sources reached; the stony-coral per-polyp rates [SAP23] are a proxy and may overstate capture by small octocoral polyps that mostly feed on finer particles.
- Swim speeds for amphipods, mysids, ostracods, rotifers and ciliates are textbook or est.; only copepod escape and cruise speeds [KIO10] are measured here.
- Rotifer and Nannochloropsis growth rates versus temperature are from culture practice and one scale study [NANNO]; Tetraselmis swimming speed was not found.
- The 'spaghetti worm' is treated as a generic terebellid; the hobby species is not identified, and no feeding rate exists.
- Cirolanid isopod attack rates and the aquarium species identity are hobby anecdote; the 2.5 mm Cirolana mercuryi [CIRO] is a reef-rubble species, and the larger fish-biting forms are undetermined here.
- Flatworm toxicity on mass death is hobby consensus with a Wikipedia note [CONV-W]; no dose-response exists.
- Medication effects on pods (fenbendazole, praziquantel, flatworm treatments) are hobby reports only.
- Phototaxis strength (the torch effect) is qualitative [REED]; the x5 local density is an est. for the night scene.
