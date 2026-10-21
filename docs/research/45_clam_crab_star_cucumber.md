# 45. Popular reef invertebrates: maxima clam, porcelain crab, serpent star, tiger tail cucumber

Added 2026-10-21 (thread "Popular invertebrates pack"). Research pass on four retail-popular reef invertebrates. The
sources are retailer and hobby pages, a few papers (cited where the abstract was checked), and a few institutional
pages. Most retailer numbers are sales copy and disagree with each other, so the sim table takes the middle of the
range and says so. "est." marks design estimates or general knowledge with no source retrieved. "GAP" marks facts
I could not source in this pass.

## 0. What the sim uses

| Species | Sheet | Brain / rig | From the sources | Design estimates (no data found) |
|---|---|---|---|---|
| Maxima clam *Tridacna maxima* | `tridacna_maxima` | `GiantClamBrain` (sessile), `ClamRig` shell + mantle | shell 20-30 cm (Sims, [SIM-TM]); PAR 250-400 µmol/m²/s, 300-400 optimal ([GEN-TM], [SIM-TM]); moderate indirect flow, a direct blast can close the mantle ([SIM-TM], [GEN-TM]); mantle colour is from zooxanthellae, colour and pattern vary with light ([SIM-TM]); byssal attachment ([TSA-TM]); pickers nip the mantle ([SIM-TM]) | mantle close on shadow/touch 2-5 s then reopen (est.); PAR tolerance floor 200; pyramidal snail risk per tank (est.) |
| Anemone porcelain crab *Neopetrolisthes maculatus* / *N. ohshimai* | `neopetrolisthes_maculatus` | `PorcelainCrabBrain`, `CrabLegs.porcelain()` (3 visible walking pairs) | purchase size 1/2" to 2" ([LA-PC]); suspension feeder, fan maxillipeds sweep plankton and mucus, lives on anemone edges ([MUS-PC], [KWAJ-PC]); claws do not feed, used for defence ([MUS-PC]); autotomy under attack, 58 of 59 crabs that shed a cheliped escaped (Wasson et al. 2002, on *Petrolisthes* spp.) ([WLK-2002]) | adult carapace 2.5 cm (est.); fan beat 2-4 Hz (est., no rate found); fan feeding rest 50% of day (est.) |
| Green serpent star *Ophiarachna incrassata* | `ophiarachna_incrassata` | `SerpentStarBrain` (nocturnal hunter), `BrittleArms` | disc to ~5 cm, legs up to 23 cm (9 in) ([QM-OI]); retailer max 10-12 in ([RE-OI]); nocturnal, eats fish and shrimp after lights-out ([REEF-OI]); reef-safe only when food is short ([AQU-OI]); arm autotomy, regrows ([REEF-OI]) | fish-hunt 0.02/h at night (est.); arm regrowth 60 d (est.) |
| Common serpent star *Ophiolepis superba* (banded) and *Ophioderma* spp. | `ophiolepis_superba`, `ophioderma` | not built yet (would be `BrittleStarBrain`, `BrittleArms`) | max 20 cm (ReefApp, [RA-OS]); 200 L tank for an adult (ReefApp, [RA-OS]); nocturnal, hides in rock by day ([TSA-OS]); benthic detritus and food-particle feeder via tube feet ([GBRI-OS]); *Ophioderma* minimum tank 20 gal (single retailer, [SWA-OD]) | disc 4 cm, arm 15 cm (est.); daytime hide 90% (est.) |
| Tiger tail cucumber *Holothuria hilla* | `holothuria_hilla` | `SeaCucumberBrain`, spine-skinned body (motion mode 10, oral tentacles) | 20-25 cm in captivity ([SIM-HH] / [LA-HH]); grey-brown to chestnut body, look-alike *H. impatiens* is paler and knobbier ([LA-HH]); holothurin toxin released under stress, lethal to tankmates ([LA-HH]); pump death is rare but real, cover intakes ([REEF-HH]) | sand turnover 3-8 kg dry sediment/yr (est.; no *H. hilla* figure found, see [PAN-HH]); toxin trigger at stress > 0.7 (est.); pump-entrainment hazard radius 15 cm (est.) |

Retail prices (US, sale or regular listing, real USD, 2024-2026 listings):
- Maxima clam, cultured 1.5-2 in: $84-139 (ORA first grade to ORA Ultra Black & Color; sale prices lower). Sim: **$110** for a 2 in colour animal.
- Porcelain crab: $15-20 (sold out at the time of search). Sim: **$18**.
- Green serpent star: $30-50 (sold out at the time of search). Sim: **$35**.
- Tiger tail cucumber (generic, *Holothuria* sp.): $40 (sold out). Sim: **$40**.

Lifespan (sim field `lifespanYears`): clam 25 (est., GAP for a source), porcelain crab 3 (est.), serpent star 5 (est., ReefApp says "several years"), cucumber 5 (est.). All four are GAP for a sourced figure.

---

## 1. Maxima clam: *Tridacna maxima*

**Size.** [SIM-TM] gives 20-30 cm (8-12 in) at full size. [GEN-TM] says up to about 12 in (30 cm). Use **25 cm** for the adult shell length in the sim.

**Appearance (for the modeller).**
- Shell: two valves, thick, roughly 2:1 length to height (est.; proportions not sourced). Shell edge has rounded, ribbed growth lines. Shell colour is cream to brown with some blue or green tinge where the mantle is thin (est.).
- Mantle: the visible tissue between the valves. It carries the photosynthetic symbionts and, in cultured morphs, bright iridescent patterns in electric blue, turquoise, green, gold and violet ([SIM-TM]). Colour depends on the light; under low light the mantle goes pale and loses vibrancy ([GEN-TM]).
- Siphons: two openings on the top edge of the mantle. The inhalant siphon is the larger, fringed one; the exhalant is a smaller, rounded opening. Both sit at the mantle edge and are visible when open.
- Byssal attachment: the clam anchors to rock with byssal threads from a foot gap at the hinge. The threads should not be cut or pulled ([TSA-TM]). Model the byssal cluster as a short rope-like bundle at the hinge.
- Scutes: the outer shell has overlapping radial scutes (plate-like ridges) that vary in height (est.; general shell morphology, not from a source retrieved in this pass).

**Behaviour.**
- Mantle retraction: the mantle pulls in when shadowed or disturbed. Brief closure (about 2-5 s) is likely a normal reflex. A long closure points to too little flow, too much light, or stress (est.; no source found for the shadow trigger, see GAP).
- Closure on touch: the valves close fast on contact. The clam then reopens over several minutes.
- Mantle opening: fully open in daylight, the mantle spreads out to catch light. Under strong direct flow the mantle partly closes ([SIM-TM], [GEN-TM]).
- Light response: the Red Sea UV study ([KAUST-TM]) found that under high light plus UV-B, clams reduced exposed mantle area. The authors read it as a photoprotective response that also lowers light to the symbionts.

**Diet and feeding.** Mostly autotrophic. Symbionts supply most of the energy. The clam also filters some plankton. Juveniles and small clams benefit from target feeding of phytoplankton ([SIM-TM]).

**Light, flow and water.**
- PAR: 250-400 µmol/m²/s, with 300-400 optimal ([GEN-TM], [SIM-TM]). The sim uses **min 250, opt 350, max 500**. Placement should be high in the tank or on rock under the strongest light.
- Flow: moderate and indirect. No direct powerhead blast on the mantle ([SIM-TM]).
- Calcium 400-450 ppm, alkalinity 8-9 dKH, Mg 1280-1350, salinity 1.025 (est., standard reef values; no source retrieved).
- Stability: steady light and parameters. Clams lose colour and mantle opening under a slow decline in PAR (est.).

**Compatibility and threats.**
- Fish that pick at mantles: some angelfish and butterflyfish ([SIM-TM]). Sim: picker-fish nip chance 0.02/min when in range.
- Pyramidal snails (Pyramidellidae) feed on clam tissue and can weaken a clam over time (est.; general hobby knowledge, no source retrieved). Sim: snail event per clam 1% per week when snails are present.
- Pumps and powerheads: direct blasts close the mantle ([SIM-TM]).

---

## 2. Anemone porcelain crab: *Neopetrolisthes maculatus* and *N. ohshimai*

**Size.** Purchase size 1/2 in to 2 in ([LA-PC]). Adult carapace is about 2-3 cm across (est.; no source retrieved). Sim: **carapace 2.5 cm**, total span about 5 cm with fans.

**Appearance (for the modeller).**
- Carapace: flattened and smooth, almost square. Colour is a cream or white base with brown or red spots (*N. maculatus*) or reddish-brown with white spots (*N. ohshimai*) ([MUS-PC], [TAP-PC]).
- Claws (chelipeds): flat, broad, smooth, held forward. They are the main defensive tool and are not used for feeding ([MUS-PC]). The right claw is often larger.
- Maxillipeds (feeding appendages): two long, feather-like fans of setae at the front of the mouth. These are the main feature for the modeller. Each fan is a broad paddle covered in fine bristles that sweep plankton from the water ([MUS-PC], [KWAJ-PC]).
- Legs: three visible walking pairs. The fourth pair is reduced and hidden under the carapace (general porcellanid anatomy, est.). The walking legs are thin and sit close to the body.

**Behaviour.**
- Fan beat rate: GAP. No beat frequency was found in any source. Sim design estimate: **2-4 Hz** while feeding, with a 30-40% duty cycle (est.).
- Feeding: the crab sits at the edge of the host anemone and waves the fans through the water. It draws the fan to the mouth to clean it and pass food ([KWAJ-PC], [LA-PC]).
- Anemone host: lives on several anemone species and uses the anemone's edge as a perch. It is not a pure symbiont; it rides in the tentacles and gains protection ([KWAJ-PC]).
- Autotomy (claw shedding): under predator attack, the crab drops a cheliped and escapes. Wasson, Lyon and Knope (2002, *Behavioral Ecology* 13(4):481-486, [WLK-2002]) reported that autotomy followed about one third of all attacks, and about two thirds of attacks where the cheliped alone held the prey. 58 of 59 crabs that autotomized escaped. The study was on *Petrolisthes cinctipes* and *P. manimaculis*, not on *Neopetrolisthes*, so transfer to this genus is an inference. A follow-up ([WL-2005]) found autotomy depends on size and sex (smaller crabs and females shed more).
- Regrowth: the claw regrows over later moults ([GEN-PC]).

**Diet and feeding.** Suspension feeder: plankton, detritus and mucus from the anemone ([KWAJ-PC], [LA-PC]). Feed a small amount of frozen mysis or a reef-plankton supplement (est.; no source for the feed). Sim: food need 0.1 g/day.

**Light, flow and water.** No strong light need. Moderate to strong flow, since the crab feeds from passing water. Standard reef parameters with stable salinity (est.).

**Compatibility and threats.**
- Stable with anemones and most reef fish. Some hobbyists report that it can be taken by larger crabs and fish when the host anemone is removed (est.).
- Claw loss is a normal stress response and not usually fatal (WLK-2002).
- Pump entrainment: small risk, cover intakes.

---

## 3. Serpent stars: green serpent star and common serpent stars

Note: the green serpent star is *Ophiarachna incrassata*, a brittle star in the family Ophiocomidae. The common "serpent star" sold as a cleanup crew is *Ophiolepis superba* (banded or harlequin serpent star) or *Ophioderma* spp. These are brittle stars (class Ophiuroidea), not sea stars.

### 3a. Green serpent star: *Ophiarachna incrassata*

**Size.** Central disc to about 5 cm (2 in) ([QM-OI]). Arms up to 23 cm (9 in) ([QM-OI]). Retailers give 10-12 in maximum ([RE-OI]). SeaLifeBase gives a wild disc diameter of 5.5 cm ([SLB-OI]). Sim: **disc 5 cm, arm 20 cm**.

**Appearance.**
- Disc: round, slightly flattened, and bright green with a darker pattern on the disc and arms. Individuals vary a lot.
- Arms: long, thick, and snake-like, with rows of short spines along the sides. Each arm is a series of lateral plates (ossicles) that the star flexes.
- Tube feet: small and absent from the disc. Arms have sensory and feeding tube feet at the arm base.

**Behaviour.**
- Nocturnal: moves about after lights-out and sits hidden during the day ([REEF-OI]).
- Hunter: sources warn that this is a predator, not only a cleaner. It arches over sleeping fish and eats small fish and shrimp at night, and has been reported to take fish in "sleeping caves" ([REEF-OI]). Sim: night fish-hunt chance 0.02/h, only when prey is within 15 cm.
- Arm autotomy: stressed or harassed stars can break an arm and regrow it ([REEF-OI]).

**Diet.** Meaty food near the mouth: clam, shrimp, krill, mysis ([REEF-OI]). Also scavenges detritus and leftover food. Sim: scavenge rate 0.5 food/day, plus fish-hunt at night.

**Water and light.** 75-80 F, dKH 8-12, pH 8.1-8.4, specific gravity 1.023-1.025 ([REEF-OI]). No strong light need. Avoid copper.

**Price.** $30 (sold out at the time of search, [RE-OI]); $29.99 to $49.99 by size (Aquatic Sunlimited, [AQU-OI]). Sim: $35.

### 3b. Common serpent stars: *Ophiolepis superba* and *Ophioderma* spp.

**Size.** *O. superba*: 20 cm maximum length (ReefApp, [RA-OS]). Retailers list 1 ft 2 in, which is probably a span rather than a disc size (retailer, [TSA-OS]). Tank 200 L (53 gal) for an adult, based on medium-size animals ([RA-OS]). *Ophioderma* spp. vary widely by species: for the red *O. rubicundum* a disc of about 3 cm is typical ([OC-OD]). Sim for *Ophiolepis*: **disc 4 cm, arm 15 cm (est.)**.

**Appearance.** *O. superba* has banded arms in dark and light patterns and a small disc. *Ophioderma* has long, thin, snake-like arms. Arm length is a species-level character ([CAM-OD]).

**Behaviour.**
- Nocturnal, hides in rock during the day ([TSA-OS]). Sim: daytime hide 90% of time (est.).
- Benthic feeder: moves over the substrate with tube feet and arms and picks up detritus and small food particles ([GBRI-OS]).
- Scavenger: actively looks for uneaten food ([SWA-OD]).

**Diet.** Detritus, microalgae, and meaty foods such as mysis or krill, fed after lights-out ([RA-OS], [TSA-OS]).

**Water and light.** Same as green serpent star (est.). Some *Ophioderma* sellers recommend a 20-gallon minimum ([SWA-OD]).

**Price.** Common cleanup serpent stars: $15-30 (est.; no current listing confirmed). Sim: $20.

---

## 4. Tiger tail sea cucumber: *Holothuria hilla*

**Size.** Captive adults about 20-25 cm (8-10 in) ([LA-HH] listing, a seller page). One retailer gives 12-18 in ([REEF-HH]). Wild *H. hilla* is smaller than *H. thomasi*, which can reach 2 m ([WP-HT]). Sim: **length 25 cm, diameter 3 cm** when relaxed.

**Appearance (for the modeller).**
- Body: elongated, cylindrical, and firm, with a grey to chestnut-brown base. Dark bands or blotches are common. The look-alike *H. impatiens* is paler and knobbier ([LA-HH]). Model two colour morphs.
- Tube feet: small, dense, and mostly on the ventral (underside) surface. They are used for walking and attaching to rock and sand.
- Oral tentacles: a ring of branched, sticky tentacles around the mouth at the head end. They are used to mop up detritus, bacteria and microalgae from sand ([REEF-HH]).
- Anus: at the rear end, which the cucumber uses to expel sand after ingestion.

**Behaviour.**
- Half-hidden: sits partly in rock or sand with the tail end out, often at night ([REEF-HH]).
- Sand sifting: moves along the bottom at night, ingesting sediment and expelling it through the rear. Turnover rate for *H. hilla* is GAP. Related species: sandfish *H. scabra* reworks about 10,590 kg dry sediment per 1000 m² per year in a natural population (the Purcell-related PANGAEA dataset, [PAN-HH]), *H. atra* about 14 kg per animal per year ([PAN-HH]), *Thelenota anax* about 34 g dry sediment per hour ([PUR-TA]). Sim design estimate: **3-8 kg dry sediment per animal per year** (est.), with a sand-sift cycle of 6-10 min per cm² per day (est.).
- Toxin release: when stressed, it releases holothurin (saponin-type) toxins, which can kill tankmates ([LA-HH]). Sim: toxin release when stress > 0.7 or when a predator (hermit crab, large fish) grabs it; kills fish in 30 min at 0.5 probability.
- Evisceration: some sea cucumbers expel the gut when stressed. Not documented for *H. hilla* in these sources; it is a general holothurian trait (est.).

**Diet.** Detritus, bacteria, microalgae and organic matter in sand, collected by the oral tentacles ([REEF-HH]). Keeping more than one per tank can exhaust the food supply, so one per tank is the usual advice ([REEF-HH]). Sim: food need 1 g/day, from sand only.

**Light, flow and water.**
- Light: none needed. Shelter in rock caves.
- Flow: low to moderate. A thick sand bed (5-8 cm) and a mature tank with natural detritus ([REEF-HH]).
- Nitrate must stay low, and copper is toxic ([REEF-HH]). Parameters must be stable ([REEF-HH]).
- Acclimation: slow drip. Expert-only species in most retailer listings ([LA-HH]).

**Compatibility and threats.**
- Toxin risk to tankmates when stressed ([LA-HH]). Avoid large hermit crabs and other potential predators ([REEF-HH]).
- Pump entrainment: the cucumber can be sucked into uncovered intakes or overflow boxes. Covering intakes with a sponge or mesh is the usual fix ([REEF-HH]). Sim: pump-entrainment chance 0.001 per hour when the cucumber is within 15 cm of an uncovered intake, 0 when covered.

**Price.** $40 for a generic *Holothuria* sp. tiger tail (sold out at the time of search, [LA-HH]). Some sellers ship *H. hilla* or *H. impatiens* under the same name. Sim: $40.

---

## 5. Gaps and caveats

- GAP: fan beat rate of porcelain crabs (no source found). Use the design estimate.
- GAP: clam mantle response to shadow and touch (only general reflex knowledge). Use the design estimate.
- GAP: *H. hilla* sediment turnover (only related species). Use the design estimate.
- GAP: lifespans for all four.
- GAP: current 2025 price surveys. The prices above are product listings, several sold out, and not dated averages.
- The autotomy data are from *Petrolisthes* spp., not *Neopetrolisthes*.
- Retailer sizes disagree (for example green serpent star 10 in vs 2 in disc). The sim uses disc sizes from SeaLifeBase and Quality Marine.

## Sources

- [SIM-TM] https://simstropicalfish.co.uk/product/maxima-ultra-cultured-clam-tridacna-maxima/
- [GEN-TM] https://gensou.sg/?p=30657 (Maxima clam care: light and placement)
- [TSA-TM] https://topshelfaquatics.com/pages/saltwater-invertebrates/maxima-clam
- [KAUST-TM] https://bol.kaust.edu.sa/publications/detail/giant-clams-in-shallow-reefs-uv-resistance-mechanisms-of-tridacninae-in-the-red-sea
- [LA-PC] https://www.liveaquaria.com/products/porcelain-anemone-crab
- [REEF-PC-PRICE] https://reefexclusive.com/products/porcelain-crab
- [MUS-PC] https://museum.wa.gov.au/explore/blogs/andrew-hosie/creature-feature-neopetrolisthes-maculatus
- [KWAJ-PC] https://2fwww.underwaterkwaj.com/JJ/2016/160627/Neopetrolisthes%20maculatus_5432%20062616.htm
- [GEN-PC] https://gensou.sg/?p=30656 (porcelain crab care guide)
- [TAP-PC] https://www.thatpetplace.com/Neopetrolisthes-ohshimai-spotted-anemone-crab-208292
- [WLK-2002] Wasson, Lyon & Knope 2002, "Hair-trigger autotomy in porcelain crabs is a highly effective escape strategy", *Behavioral Ecology* 13(4):481-486. https://ideas.repec.org/a/oup/beheco/v13y2002i4p481-486.html (abstract checked)
- [WL-2005] Wasson & Lyon 2005, *Behavioral Ecology* 16(6):1037-1041. https://ideas.repec.org/a/oup/beheco/v16y2005i6p1037-1041.html (abstract listing only)
- [QM-OI] https://www.qualitymarine.com/news/the-interesting-incrassata/
- [RE-OI] https://reefexclusive.com/products/green-brittle-star
- [REEF-OI] https://reefs.com/?p=112073 and https://reefs.com/forum/threads/my-sea-monster.56481/latest (hobby forum excerpts, treat as anecdote)
- [AQU-OI] https://aquaticsunlimited.com/product/starfish-green-brittle-ophiarachna-incrassata/
- [SLB-OI] https://sealifebase.org/summary/SpeciesSummary.php?id=49635
- [RA-OS] https://reefapp.net/encyclopedia/ophiolepis-superba
- [TSA-OS] https://topshelfaquatics.com/pages/saltwater-invertebrates/harlequin-serpent-starfish
- [GBRI-OS] https://www.gbri.org.au/SpeciesList/StudentProject2016%7CAngelaUpton
- [SWA-OD] https://www.saltwateraquarium.com/serpent-starfish-ophioderma-spp-cleanup-crew/
- [OC-OD] https://oceanica.charleston.edu/CharlestonBump2003/ChasBumpInverts3/Brittle%20Stars/BRIT1page1.htm
- [CAM-OD] https://www.cambridge.org/core/journals/journal-of-the-marine-biological-association-of-the-united-kingdom/article/on-ophioderma-echinodermata-ophiuroidea-coming-of-age-morphological-variations-in-three-eastern-pacific-species/E403A3EA605CAF20047695C016810FE0
- [LA-HH] https://www.liveaquaria.com/products/sea-cucumber-tiger-tail-expert-only
- [REEF-HH] https://frags2fishes.com/?p=19007 and https://www.thatpetplace.com/Holothuria-hilla-tigertail-cucumber-206927 (retailer care notes)
- [SIM-HH] https://simstropicalfish.co.uk/?p=68814 (tiger tail, *Holothuria* sp.)
- [PAN-HH] Sediment turnover data cited in search results: PANGAEA dataset 10.1594/PANGAEA.883603 (Fiji), and the sandfish and lollyfish figures from the Heron Reef and Fiji studies (values not checked against primary papers)
- [PUR-TA] Purcell, *Thelenota anax* sediment turnover, https://independent.academia.edu/StevenPurcell4 (search snippet only)
- [WP-HT] https://en.wikipedia.org/wiki/Holothuria_thomasi
- [SLB-HH] https://www.sealifebase.ca/summary/Holothuria-hilla (IUCN Least Concern; price unknown)
- Price sources: [RE-OI], [AQU-OI], [LA-PC], [REEF-PC-PRICE], [LA-HH], and [BULK-TM] https://www.bulkreefsupply.com/ora-first-grade-maxima-clam-ora.html, [SWA-TM] https://www.saltwateraquarium.com/aquacultured-ultra-black-color-maxima-clam-2-tridacna-maxima-ora/
