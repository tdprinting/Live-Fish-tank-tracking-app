# 42. Peppermint shrimp (Lysmata) and flame scallop (Ctenoides) research note

Purpose: numbers and behaviour for two aquarium invertebrates in the ReefLive simulation.
Date of research: 2026-10-20. Web sources only (no YouTube).

## Tags

- **[S]** sourced: traced to a named paper, book or institution (via the source page).
- **[M]** multiple hobby sources: two or more independent vendor, forum or hobby pages agree.
- **[E]** estimate: my number, or a single unverified claim, or a value derived from a proxy species. Treat as a tuning default.

## Corrections to the brief

- **Rhyne, Lin & Deal 2004 is not confirmed.** The peer-reviewed paper I could verify is **Rhyne, A.L. & Lin, J. (2006) "A western Atlantic peppermint shrimp complex," Bulletin of Marine Science 79(1): 165-204.** Full PDF: https://research.nhm.org/pdfs/27306/27306.pdf. It redescribes *L. wurdemanni* (neotype from Key West) and describes new species including *L. ankeri*, *L. boggessi* and *L. pederseni*. A fourth new name is disputed across sources (*L. bahia* is the likeliest). A "Rhyne et al. 2004" paper is cited only second-hand by a UK vendor (https://www.greenwave-aquatics.co.uk/pages/identification-of-true-peppermint-shrimp, page returned 403, so this is from a search snippet). I could not retrieve it, so no 2004 numbers are used here.
- **Calado et al.** The Aiptasia feeding data I could find is **Calado & Narciso (2005), Helgoland Marine Research, DOI 10.1007/s10152-004-0210-6** (https://hmr.biomedcentral.com/articles/10.1007/s10152-004-0210-6). It tests **L. seticaudata** (Monaco shrimp), not *L. boggessi* or *L. wurdemanni*.
- **Dougherty et al. 2014 is in J. R. Soc. Interface, not J. Exp. Biol.** Correct citation: Dougherty, L.F., Johnsen, S., Caldwell, R.L. & Marshall, N.J. (2014), "A dynamic broadband reflector built from microscopic silica spheres in the 'disco' clam *Ctenoides ales*," *Journal of the Royal Society Interface* 11(98): 20140407. Coverage used: UC Berkeley news (https://news.berkeley.edu/2014/06/24/young-researcher-discovers-source-of-disco-clams-light-show/?nb=1) and Royal Society news (https://royalsociety.org/news/2014/dazzling-display-of-the-disco-clam/). The paper itself was not retrieved.
- **Flame scallop byssus.** The brief assumes byssus attachment in crevices. The sources I read do not confirm it. Wikipedia (https://en.wikipedia.org/wiki/Ctenoides_scaber) does not mention byssus. Reefbuilders (Kurtz 2014) describes wandering into hard-to-reach areas, not byssal anchoring. One search snippet claimed "often attach with byssal threads," but it had no source. Treat byssus as **unverified**, and do not use it in the sim.

---

## Part 1. Peppermint shrimp: *Lysmata* species

### 1.1 Which species is which

| Name in trade | Taxonomic status | Notes |
|---|---|---|
| "Peppermint shrimp" (generic US label) | Mostly ***L. wurdemanni* complex** [M] | Aquatics Unlimited lists "peppermint shrimp (*Lysmata wurdemanni* complex)" at US$19.99 (https://aquaticsunlimited.com/product-category/saltwater-livestock/saltwater-invertebrates/shrimp/). Look-alikes are often sold under the same name. |
| "True peppermint" / "Aiptasia-eating peppermint" | ***L. boggessi* Rhyne & Lin 2006** [M] | Hobbyists and UK captive-bred sellers use this name (greenwave-aquatics.co.uk, algaebarn.com/?p=41485). |
| *L. rathbunae* | Redescribed by Rhyne & Lin 2006; type locality restricted to Florida and Yucatán (abstract, https://research.nhm.org/pdfs/27306/27306.pdf) | Not a common US retail name. A "variety" of Chace's was split off into a new species. |
| *L. ankeri*, *L. pederseni* | New species, Rhyne & Lin 2006 [S] | Sold as peppermint shrimp in some cases [M]. |
| *L. bahia* | Likely fourth new species, Rhyne & Lin 2006 [E] | One source lists *L. seticaudata* in that slot, which looks garbled. Verify against the PDF. |

Taxonomy caveat: Baeza & Prakash (2019, *Zoological Journal of the Linnean Society* 185(4): 1018-1038) argue that *L. wurdemanni* is a cryptic complex of at least 5 (possibly 9) morphologically identical species (cited on https://en.wikipedia.org/wiki/Lysmata_wurdemanni). So a retail "peppermint" can be any of these.

### 1.2 Which one is sold in US stores and which eats Aiptasia

- **Sold as "peppermint shrimp" in US stores:** mainly the *L. wurdemanni* complex, often unidentified [M].
- **Best-known Aiptasia eater:** *L. boggessi* is the species most often named as the reliable one (Wikipedia calls it "the most consistent and reliable predator of Aiptasia," https://en.wikipedia.org/wiki/Lysmata_boggessi, attributing this to Rhyne & Lin 2006 and Calado 2008). This is [M] from hobby and secondary sources, and it is **not backed by a peer-reviewed predation trial that I could find.**
- **Conflicting view:** a Singapore care guide (https://gensou.sg/peppermint-shrimp-care-guide-reef/) says *L. wurdemanni* is the species most often reported to eat aiptasia, and that *L. boggessi* and *L. rathbunae* show little interest. Other hobby reports say individuals vary and some ignore aiptasia entirely [M].
- **Do not assume a given store animal eats aiptasia.** Identity is uncertain, and individual variation is large [M].

### 1.3 Morphology and colour

| Trait | Value | Source / tag |
|---|---|---|
| Adult length, *L. wurdemanni* | Up to 7 cm (2.8 in) | [S] Wikipedia citing Ruppert & Fox 1988 |
| Adult length, *L. boggessi* | 3-4 cm (1.2-1.6 in) | [M] Reeflex (https://reeflex.net/tiere/858_Lysmata_boggessi.htm); one primary source only |
| Juvenile *L. boggessi* | Larvae 2-3 mm at hatch; 5-8 mm at day 14; 10-12 mm at day 21; metamorphosed juveniles ~12 mm | [M] Reeflex |
| Body colour | Translucent body with bright red stripes (wurdemanni) | [S] Wikipedia (Rhyne & Lin 2006 is the diagnosis) |
| Red stripes, boggessi | Red longitudinal stripes; in some animals red horizontal stripes and short dark tail stripes | [M] Reeflex (user report), weak |
| Eyes | Not reliably described in sources | [E] |
| Antennae | Not described | [E] |
| Legs and tail | Tail stripes in some animals (see above) | [E] |
| Eggs | Bright green (wurdemanni, Wikipedia); females carry yellow eggs (boggessi, Reeflex) | [S]/[M] |

Identification in practice: species share a red-and-white striped look. Morphology is hard without a specimen, and colour pattern varies with collection site (EnviroLiteracy, reefs.com; reefs.com page returned 403, so this is from a search snippet). Colour is a poor ID key.

### 1.4 Life history and behaviour

| Trait | Value | Source / tag |
|---|---|---|
| Lifespan | Up to ~2 years in aquaria | [E] single aquarium guide, not confirmed |
| Molt interval | Not measured in sources. Anecdote: one post-purchase molt within a week (2009 forum). Fire shrimp anecdote: ~2 weeks. | [E] adult molt every 1-3 weeks |
| Post-molt behaviour | Mating happens immediately post-moult, when the female's cuticle is soft (Wikipedia citing Ruppert & Fox 1988) | [S] |
| Activity | Mostly nocturnal; spends most of the day in live rock; forages at dusk or under blue/moon light; bright light raises stress | [M] (multiple care guides) |
| Hiding | Hides under overhangs and in crevices; less hidden than emperor shrimp (Reeflex) | [M] |
| Group living | Often kept in pairs. One user kept 4 *L. boggessi* together and two were fighting-eliminated. Avoid multiple *Lysmata* in one small tank. | [M] / [E] |
| Hermaphroditism | Protandric simultaneous hermaphrodite. Starts male, may become hermaphrodite after ~4 male moults (Wikipedia, citing Baeza et al. 2009). Some males never change. | [S] |
| Sex and temperature | Larval sex ratio affected by temperature (general gonochoristic statement, Reeflex) | [E] for *Lysmata* |
| Breeding | Easy to breed; larval metamorphosis 33-38 days, temperature-dependent (Reeflex) | [M] |

### 1.5 Diet and Aiptasia control

| Item | Value | Source / tag |
|---|---|---|
| Natural diet | Parasites, necrotic tissue, detritus (Ruppert & Fox 1988 via Wikipedia). Described as a cleaner shrimp. | [S] |
| Aquarium diet | Frozen food, flake, pellets, brine shrimp, food tablets, Aiptasia (Reeflex; care guides) | [M] |
| Aiptasia consumption, *L. boggessi* | No polyps-per-day figure found in any source | [E] |
| Aiptasia consumption, *L. seticaudata* (proxy) | Starved shrimp ate 86-89% of anemones offered; shrimp with alternative food ate 27-36% (Calado & Narciso 2005) | [S] proxy |
| Effect of food | Starved shrimp eat significantly more aiptasia than fed ones (multiple guides; the Rhyne 2004 claim is second-hand) | [M] |
| Size preference | Larger anemones and alternative food reduce feeding (Calado & Narciso 2005, proxy species) | [S] proxy |
| Does a well-fed shrimp ignore aiptasia? | Some claim yes if frozen food is available (Reeflex). Others say individuals vary and some never eat aiptasia at all. | [M] |

### 1.6 Coral and other behaviour

- **Coral nipping:** A user reported *L. boggessi* nibbling *Stylophora* and shredding five *Acanthastrea* colonies; corals on a reef deck were spared. Another user reported one shrimp eating the foot of a *Condylactis gigantea* (Reeflex). These are anecdotes, so nipping risk is **moderate for SPS/LPS and low for most zoanthids** [E]. Keep the rate low in the sim.
- **Cleaning:** Called "cleaner shrimp" (Wikipedia, Reeflex). Cleaning is weak and occasional [E].
- **Predators:** Groupers, dottybacks, hawkfish and wrasses eat them (aquarium guides, [M]). Larvae are eaten by many fishes and invertebrates [M]. Coral banded shrimp (*Stenopus hispidus*) is widely said to eat smaller shrimp, but I did not verify this in these searches, so it is [E] only.

### 1.7 Water parameters

| Parameter | Value | Source / tag |
|---|---|---|
| Temperature | 23-28 °C (73.4-82.4 °F) for *L. boggessi* (Reeflex) | [M] (single site, consistent with general hobby guidance) |
| Salinity | 1.023-1.026 SG | [E] |
| pH | 8.1-8.4 | [E] |
| Nitrate | Keep low; "water quality should always be good" (Reeflex) | [M] qualitative |
| Copper | Highly sensitive; treat as zero tolerance | [M] general hobby consensus, [E] for threshold |
| Iodine/iodide | Dose cautiously; crustaceans sensitive | [E] |
| Minimum tank | About 50 L (11 gal) for *L. boggessi* (Reeflex) | [M] single source |
| Care level | "Easy" (Reeflex calls it average difficulty; greenwave calls it very easy) | [M] |

### 1.8 Price (US, USD)

- *L. wurdemanni* complex at US$19.99 (Aquatics Unlimited, current listing, [S] retailer).
- Historical hobby list: US$3.50-4 each for 10+ (Marine Colorado forum, undated, [E] historical only).
- UK captive-bred *L. boggessi*: GBP 44.99 for two (not US, but shows price tier for true boggessi).
- **Estimate for US retail, generic peppermint: US$10-20 each** [E]. Captive-bred *boggessi* probably costs more.

---

## Part 2. Flame scallop: *Ctenoides* species

### 2.1 Which is which

| Common name | Species | Range | Notes |
|---|---|---|---|
| "Flame scallop", "rough fileclam" | ***Ctenoides scaber*** (formerly *Lima scabra*) | Caribbean, Bahamas, Florida, Gulf of Mexico | [S] Reefbuilders, Kurtz 2014 (https://reefbuilders.com/2014/11/14/dont-get-burned-by-flame-scallops); Wikipedia |
| "Electric flame scallop", "disco clam" | ***Ctenoides ales*** (formerly *Lima ales*) | Indo-Pacific, Indonesia to Palau and New Caledonia | [S] Kurtz 2014; Wikipedia (https://en.wikipedia.org/wiki/Ctenoides_ales) |

Sold as "flame scallop": *C. scaber* (also labelled *Lima scabra*) [M]. Sold as "electric flame scallop" or "disco clam": *C. ales* [M].

The flashing species is **C. ales** (see 2.5 below). *C. scaber* does not flash.

### 2.2 C. scaber: morphology

| Trait | Value | Source / tag |
|---|---|---|
| Shell size | Max 7.5 cm (SeaLifeBase, ref 83435, https://www.sealifebase.se/summary/48060). Wikipedia says "up to 3 inches" (~7.6 cm). Kurtz 2014 gives 3-4 inches diameter, which conflicts. | [S] for ~7.5 cm; the diameter figure is probably not the same measure |
| Shell colour | White (Kurtz 2014) | [S] single source |
| Shell texture | Rough, hence "rough fileclam" | [S] Wikipedia |
| Mantle colour | Red to orange-red, from carotenoids (Wikipedia, Kurtz 2014) | [M] |
| Tentacles | Long, tapering, red or sometimes white, extending from the mantle edge (Kurtz 2014). Wikipedia: red and white tentacles along the mantle edge. | [M] |
| Tentacle length | Not stated | [E] |
| Tentacle count | Not found in any source | [E] |
| Depth | Wiki/SeaLifeBase give 0-227 m (SeaLifeBase ref), other sources 1-40 m. Conflict. | [E] use 1-40 m for the sim |

### 2.3 C. scaber: movement

- **Swimming:** It claps its valves together to propel itself away from a predator (Wikipedia; Kurtz 2014 says it swims "not particularly graceful"). [M]
- **Jet distance and speed:** No numbers in any source. [E] Estimate 0.2-0.5 m per burst, 1-3 bursts when disturbed.
- **Valve gape rhythm during clapping:** Not measured. [E] 1 clap per second in escape; slow gape-and-close (2-4 s) at rest.
- **Move to new crevice:** Wanders into hard-to-reach areas (Kurtz 2014) [M]. No rate given. [E] Assume a few moves per day at most when hungry, and rarely when fed.
- **Nests:** Rests in self-made nests of small coral pieces and rocks (Wikipedia). [S]
- **Byssus:** Not confirmed (see Corrections). Do not use.

### 2.4 C. scaber: diet and captive survival

| Trait | Value | Source / tag |
|---|---|---|
| Feeding mode | Azooxanthellate; non-photosynthetic filter feeder. Eats phytoplankton and zooplankton/larvae. Uses palps to sort particles before ingestion (Wikipedia). | [S]/[M] |
| Particle size | Very small (phytoplankton, invertebrate larvae). No numeric size given. | [M] qualitative |
| Feeding need | Preserved and/or live phytoplankton at least once a day (one care guide); a mature reef with refugium is unlikely to supply enough (Kurtz 2014). | [M] |
| Captive survival | "Most specimens starve to death within a matter of months in captivity" (Kurtz 2014). Tridacnids have a much better record. | [S] single authoritative hobby source |
| Captive lifespan | 1-3 years (one care guide, optimal conditions); up to 4 years (another); "2-3 years in wild" (unnamed). | [E] |
| Natural predators | Crabs and shrimp (Wikipedia, citing a Caribbean reef source) | [S] secondary |
| Light | Does not depend on light; prefers dim, sheltered, shaded spots (Kurtz 2014; several hobby sources) | [M] |

### 2.5 C. ales (electric flame scallop): flash mechanism

- **Mechanism:** The flashes are reflected light, not bioluminescence. Silica spheres on the inner side of the mantle lip reflect light. The outer side has no spheres and absorbs light, appearing red. [S] Dougherty et al. 2014, J. R. Soc. Interface.
- **Sphere diameter:** 340 nm (Dougherty et al. 2014, quoted in Berkeley/Royal Society coverage). [S]
- **Flash rate:** The lips roll up and unfurl in concert "several times a second" (news coverage). [M] Simulation default 2-4 Hz [E].
- **Purpose:** Untested. Dougherty's 2016 UC Berkeley PhD argues predator deterrence is the likeliest function (as summarised in Wikipedia's *C. ales* article). Attracting prey or mates are alternatives. [S]/[E]
- **Aquarium status:** Sold, and photographed in aquaria (Wikipedia caption). Survival data not found. [E]

### 2.6 Water parameters

Neither *C. scaber* nor *C. ales* has a sourced parameter table in my searches. Use reef-standard values with a caution flag:

| Parameter | Sim default | Tag |
|---|---|---|
| Temperature | 24-26 °C | [E] |
| Salinity | 1.025-1.026 | [E] |
| pH | 8.1-8.4 | [E] |
| Light | Low to shaded; avoid direct high-PAR | [M] qualitative |
| Flow | Moderate; enough to deliver plankton, not so much it drags the animal | [E] |
| Tank | At least 150 L (about 33 gal, Reeflex) for an advanced-level setup | [M] |
| Care level | Advanced (Reeflex, Kurtz 2014) | [M] |

### 2.7 Price (US, USD)

- No US retail price for *C. scaber* or *C. ales* was found. [unknown]
- Sim placeholder: US$40-80 per animal [E] (a rough guess based on expected price tier for a rare, hard-to-keep bivalve).

---

## Sim numbers

Two columns: the sim default for peppermint (*L. boggessi* representative, with *L. wurdemanni* in brackets where different), and the sim default for flame scallop (*C. scaber*; *C. ales* in brackets where different). All numbers are sim defaults. Tags say how well-supported each one is.

| Parameter | Peppermint shrimp (*L. boggessi*) | Flame scallop (*C. scaber*) | Tag |
|---|---|---|---|
| Adult length | 3.5 cm [wurdemanni: 5-7 cm] | 6-7.5 cm | Peppermint: [M]/[S]; scallop: [S] |
| Body/mantle colour | Translucent, red stripes | Red mantle, white shell | [M]/[S] |
| Lifespan | 1.5-2 yr | 6-18 months in captivity (wild unknown) | [E] |
| Molt interval (adult) | 7-21 days | n/a | [E] |
| Post-molt hide | 24-48 h hiding, no feeding on aiptasia | n/a | [E] |
| Activity | Nocturnal; ~80% of time hidden by day; feeding peaks after lights-off | Crypto, mostly sessile; burst-swims when disturbed | [M] |
| Group size | 1-2 (avoid ≥3 in one small tank) | 1 | [M]/[E] |
| Diet, daily | Frozen food 1x/day plus aiptasia | Plankton feed 1x/day (must be supplied) | [M] |
| Aiptasia, starved | 0.5-1.0 polyps/day (small polyps <1 cm preferred) | n/a | [E] |
| Aiptasia, fed | 0-0.2 polyps/day | n/a | [E] |
| Aiptasia, size preference | Ignore polyps >2 cm | n/a | [E] |
| Coral nip rate | 0.01/day per shrimp on SPS/LPS | n/a | [E] |
| Cleaning events | 0.05/h | n/a | [E] |
| Predation risk (fish) | Medium (dottyback, hawkfish, wrasse) | Medium (crabs, shrimp) | [M] |
| Escape jet | n/a | 0.2-0.5 m per burst, 1-3 bursts, 1 clap/s | [E] |
| Gape cycle at rest | n/a | 2-4 s between closures | [E] |
| Flash (C. ales only) | n/a | 2-4 Hz, reflective silica, 340 nm spheres | [S]/[E] |
| Temperature | 24-27 °C (range 23-28) | 24-26 °C | [M]/[E] |
| Salinity | 1.023-1.026 | 1.025-1.026 | [E] |
| pH | 8.1-8.4 | 8.1-8.4 | [E] |
| Copper tolerance | 0 (lethal at low dose) | 0 | [M] qualitative |
| Light | Low, nocturnal | Low, shaded | [M] |
| Price US (USD) | 10-20 per shrimp | 40-80 per animal | [S] for 19.99 listing; rest [E] |
| Care level | Easy (beginner) | Advanced | [M] |

---

## Sources (inline references above; full list)

1. Rhyne, A.L. & Lin, J. (2006). A western Atlantic peppermint shrimp complex. *Bulletin of Marine Science* 79(1): 165-204. https://research.nhm.org/pdfs/27306/27306.pdf
2. Wikipedia, *Lysmata wurdemanni* (citing Ruppert & Fox 1988; Baeza & Prakash 2019; Baeza et al. 2009; Rhyne & Lin 2006). https://en.wikipedia.org/wiki/Lysmata_wurdemanni
3. Wikipedia, *Lysmata boggessi* (citing Rhyne & Lin 2006; Calado 2008, *Marine Ornamental Shrimp*, Wiley-Blackwell). https://en.wikipedia.org/wiki/Lysmata_boggessi
4. Calado, R. & Narciso, L. (2005). Aiptasia feeding by *L. seticaudata* (Monaco shrimp). *Helgoland Marine Research*, DOI 10.1007/s10152-004-0210-6. https://hmr.biomedcentral.com/articles/10.1007/s10152-004-0210-6
5. Reeflex, *Lysmata boggessi* (species page). https://reeflex.net/tiere/858_Lysmata_boggessi.htm
6. Greenwave Aquatics, identification page (cites Rhyne et al. 2004, second-hand; page returned HTTP 403, read from search snippet). https://www.greenwave-aquatics.co.uk/pages/identification-of-true-peppermint-shrimp
7. Algae Barn, *Peppermint Shrimp (Lysmata boggessi)*. https://www.algaebarn.com/?p=41485
8. Aquatics Unlimited, shrimp category (US$19.99 listing). https://aquaticsunlimited.com/product-category/saltwater-livestock/saltwater-invertebrates/shrimp/
9. Marine Colorado forum, undated price list. https://marinecolorado.org/goto/post?id=325985
10. Gensou (Singapore), *Peppermint Shrimp Care Guide*. https://gensou.sg/peppermint-shrimp-care-guide-reef/
11. Reefs.com, *Peppermint Shrimp* (HTTP 403, read from search snippet). https://reefs.com/invertebrates/peppermint-shrimp/
12. EnviroLiteracy, *What is the name of the peppermint shrimp?* https://enviroliteracy.org/animals/what-is-the-name-of-the-peppermint-shrimp/
13. Dougherty, L.F., Johnsen, S., Caldwell, R.L. & Marshall, N.J. (2014). A dynamic broadband reflector built from microscopic silica spheres in the 'disco' clam *Ctenoides ales*. *Journal of the Royal Society Interface* 11(98): 20140407. Paper not retrieved; coverage at https://news.berkeley.edu/2014/06/24/young-researcher-discovers-source-of-disco-clams-light-show/?nb=1 and https://royalsociety.org/news/2014/dazzling-display-of-the-disco-clam/
14. Dougherty, L. (2016). *Flashing in the 'Disco' Clam Ctenoides ales*. PhD, UC Berkeley (as summarised in Wikipedia). https://en.wikipedia.org/wiki/Ctenoides_ales
15. Kurtz, J. (2014). Don't Get Burned by Flame Scallops! *Reef Builders*, 14 Nov 2014. https://reefbuilders.com/2014/11/14/dont-get-burned-by-flame-scallops
16. Wikipedia, *Ctenoides scaber* (citing Born 1778; Rosenberg 2014 via WoRMS; ITIS; Turgeon et al. 2009). https://en.wikipedia.org/wiki/Ctenoides_scaber
17. SeaLifeBase, *Ctenoides scabra* (rough fileclam), ref 83435 for size and depth. https://www.sealifebase.se/summary/48060
18. Reeflex, *Ctenoides scaber* (advanced level; 150 L tank). https://www.reeflex.net/tiere/787_Ctenoides_scaber.htm
19. Reefapp, *Ctenoides scaber* encyclopedia entry (search snippet only). https://reefapp.net/encyclopedia/ctenoides-scaber
20. Top Shelf Aquatics, peppermint shrimp (2-pack) and flame scallop product pages (search snippet only). https://topshelfaquatics.com/products/flame-scallop
21. Baeza, J.A. & Prakash, S. (2019). Cryptic species complex in *L. wurdemanni*. *Zoological Journal of the Linnean Society* 185(4): 1018-1038 (cited via Wikipedia).
22. Baeza, J.A. et al. (2009). Protandric simultaneous hermaphroditism in *Lysmata* (cited via Wikipedia).
23. Ruppert, E.E. & Fox, R.S. (1988). *Seashore Animals of the Southeast* (cited via Wikipedia for diet and size).

## Open gaps (not resolved)

- No polyps-per-day or per-week Aiptasia consumption rate for *L. boggessi* or *L. wurdemanni* in any source I could read. The sim numbers are estimates.
- Rhyne et al. 2004 (cited second-hand): not retrieved.
- Flame scallop tentacle count, byssus, valve-gape timing, jet distance, US price: not found.
- Captive lifespan for *C. scaber* conflicts (months vs 1-4 years). Use months as the conservative default.
- Coral banded shrimp predation on peppermints: stated as widely reported, not verified.
- Peer-reviewed sources for *C. scaber* behaviour: none found. Most data is from hobby sites.
