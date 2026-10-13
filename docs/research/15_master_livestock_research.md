# ReefLive master livestock research

Research snapshot: 2026-10-12. Scope is the **26 entries in `core/src/main/resources/species/index.json`**: nine fish, ten non-coral invertebrates, six coral/corallimorph entries, and one macroalga. This is a research document for the tank and its simulation. It does not assert that the app currently implements any behavior proposed below. The snowflake moray in older notes/test fixtures is **not in the current main species index**, so it is outside this master roster. See the [source directory](14_astra_source_pack.md), [fish detail](10_fish_behavior_detail.md), [invertebrate detail](11_invertebrate_behavior_detail.md), and [shrimp detail](12_cleaner_shrimp_detail.md) for further sources and qualifications.

## How to use the profiles

**Direct** means a study of the named taxon. **Account** means a museum, breeder, or curated species description. **Analogy** means a different species or only the genus/family was studied. **Observation needed** marks a question that cannot be settled from the available literature or an uncertain tank identification. A proposed simulation behavior is an evidence-informed design idea, **not** a measured probability, timer, or lethal threshold. Maintain separate records for wild reefs, controlled experiments, captive husbandry, and this individual tank. The aquarium's actual stock count, size, age, and identification should be checked against owner records; a catalog entry alone does not prove that an animal is currently alive in the physical tank.

## Fish (9 entries)

### 1. Ocellaris clownfish — *Amphiprion ocellaris*

**Research.** [Florida Museum's account](https://www.floridamuseum.ufl.edu/discover-fish/species-profiles/clown-anemonefish/) describes a size-ranked social group, with the largest breeding female and a male that may change sex after loss of the female. A [juvenile host-choice experiment](https://pmc.ncbi.nlm.nih.gov/articles/PMC6850181/) shows host identity and development matter. A [juvenile salinity experiment](https://doi.org/10.3390/fishes9010030) measured survival under particular rearing salinities; it is not a mixed-reef target range. **Simulation research use:** individual pair hierarchy, host preference, yielding, nest tending only when observed, and separate food/shelter access. **Observation needed:** which fish dominates, chosen host or territory, and whether an apparent quiver is social signaling. Orange and black color forms should not be given different behavior solely because of color. More in [fish detail](10_fish_behavior_detail.md#1-ocellaris-clownfish--amphiprion-ocellaris).

### 2. Palette/blue tang — *Paracanthurus hepatus*

**Research.** [FishBase](https://www.fishbase.se/summary/Paracanthurus-hepatus.html) describes reef-terrace use, loose aggregations, juvenile refuge in branching corals, and a natural diet including zooplankton and some algae. A [cleaner-model experiment](https://www.nature.com/articles/ncomms1547) measured effects in another surgeonfish, *Ctenochaetus striatus*; any stress effect for this tang is analogy. **Simulation research use:** open-water food interception, occasional picking, alarmed retreat, and growth-dependent room to turn and hide. **Observation needed:** its actual food preference, refuge, response to cleaner animals, and body size over time. Do not treat all tangs as identical algae mowers or use a database maximum length as an aquarium growth cap.

### 3. Pajama cardinalfish — *Sphaeramia nematoptera*

**Research.** [FishBase](https://www.fishbase.se/summary/sphaeramia-nematoptera) records daytime association with branching coral, nighttime dispersal to feed near the bottom, pairing, and mouthbrooding. **Simulation research use:** a light-responsive shelter/foraging rhythm and distinct courtship and brooding states, with actual transitions depending on cover and neighbors. **Observation needed:** whether the individual feeds by day, which shelter it uses, and whether a swollen mouth represents brooding. Do not import Banggai cardinalfish brooding durations or make daytime immobility absolute. See [fish detail](10_fish_behavior_detail.md#3-pajama-cardinalfish--sphaeramia-nematoptera).

### 4. Bluestreak cleaner wrasse — *Labroides dimidiatus*

**Research.** [FishBase](https://www.fishbase.se/summary/Labroides-dimidiatus.html) documents cleaning stations and a diet that can include parasites and client mucus. A [direct experiment](https://pubmed.ncbi.nlm.nih.gov/28827634/) found service behavior varies with social setting, while [interaction research](https://pmc.ncbi.nlm.nih.gov/articles/PMC9119974/) recorded tactile behavior. **Simulation research use:** model each cleaning attempt as advertisement, client acceptance, inspection/contact, possible jolt/withdrawal, and future re-approach; distinguish feeding from prepared foods. **Observation needed:** accepted visits per approach, foods actually eaten, and repeated client-specific outcomes. The papers do not establish a mandatory visit frequency or tank-wide calming effect.

### 5. Engineer/convict fish — *Pholidichthys leucotaenia*

**Research.** The [Australian Museum](https://publications.australian.museum/blog-archive/science/bbc-life-convict-fish-footage/) describes juvenile appearance and excavation of substantial tunnel systems. A [phylogenomic study](https://www.sciencedirect.com/science/article/pii/S1055790326000515) discusses the unusual burrow-associated life history but leaves aspects of reproduction unresolved. Despite trade names, it is neither a true goby nor a blenny. **Simulation research use:** substrate pickup, transport and release; juvenile versus adult shelter behavior; feeding near burrow entrances. **Observation needed:** actual animal size, entrances and food-taking. A juvenile-to-adult transition length or feeding radius is not established by these sources.

### 6. Falco hawkfish — *Cirrhitichthys falco*

**Research.** [FishBase](https://www.fishbase.org/summary/5445) describes perching at coral-head bases and specialized lower pectoral rays; it also records group reproductive behavior in nature. **Simulation research use:** visible perch selection, scanning, short departures, food strikes, and landing. **Observation needed:** preferred perches, interaction with shrimp, whether any approach becomes a strike, and molt status of nearby shrimp. Do not give it flame hawkfish-specific behavior or a fixed small-animal capture probability. More in [fish detail](10_fish_behavior_detail.md#5-falco-hawkfish--cirrhitichthys-falco).

### 7. Lawnmower blenny — *Salarias fasciatus*

**Research.** A [feeding study](https://www.researchgate.net/publication/226757000_Are_the_oral_teeth_of_combtooth_bennies_Salariini_Teleostei_merely_combs) found that grazing removed photosynthetic and organic material from an experimental surface film; food is more complex than visible hair algae. A [field study](https://research.usc.edu.au/esploro/outputs/journalArticle/The-ecological-significance-of-the-combtoothed/99548308102621) observed territorial responses to intruders in a particular reef setting. **Simulation research use:** surface-patch quality, bite bouts, travel between patches, and aggression conditional on an intrusion. **Observation needed:** actual swallowed food, grazed surfaces, shelter, and specific intruders. Field percentages are not aquarium per-tick probabilities.

### 8. Tailspot blenny — *Ecsenius stigmatura*

**Research.** [FishBase](https://www.fishbase.se/summary/Ecsenius-stigmatura.html) gives species identification cues, habitat, small adult size, and substrate-attached eggs. **Simulation research use:** head-out shelter inspection, short feeding excursion, return, and retreat, with cover as a distinct resource. **Observation needed:** identity confirmation, chosen hole, frequency of exposure, and whether the larger lawnmower blenny displaces it. Evidence here does not quantify a tailspot-versus-lawnmower conflict rate or justify copying the latter's diet experiments to this genus.

### 9. Indigo dottyback — *Pseudochromis fridmani* × *P. sankeyi* (working hybrid identity)

**Research.** [ORA's breeder account](https://www.orafarm.com/product/electric-indigo-dottyback/) identifies its Electric Indigo hybrid line and describes color variation and comparatively mild behavior among dottybacks. This is breeder experience, not a controlled tankmate trial; `pseudochromis_indigo` is an app identifier, not a valid species name. **Simulation research use:** cave ownership, patrol, context-dependent approach and retreat, and individual history with each neighbor. **Observation needed:** purchase identity, shelter layout, and encounters with cardinalfish, blennies, and shrimp. No fixed attack count or one-week aggression decay is scientifically established.

## Mobile and tube-dwelling invertebrates (10 entries)

### 10. Banded coral shrimp — *Stenopus hispidus*

**Research.** The direct studies assembled in [shrimp detail](12_cleaner_shrimp_detail.md#banded-coral-shrimp--stenopus-hispidus) distinguish conspicuous station behavior, mating/reproductive context, and cleaner interactions from the skunk cleaner shrimp. **Simulation research use:** shelter/station position, antennal signaling, approach and acceptance rather than automatic cleaning, and a separate molting vulnerability state. **Observation needed:** whether this individual actually cleans fish, pair status, molt status, and any contact with the skunk cleaner or hawkfish. Its behavior must not be copied wholesale from *Lysmata*.

### 11. Skunk cleaner shrimp — *Lysmata amboinensis*

**Research.** [Direct study notes](12_cleaner_shrimp_detail.md#skunk-cleaner-shrimp--lysmata-amboinensis) describe signaling, client acceptance, and cleaning as a sequence, with reproductive and molt details treated separately. **Simulation research use:** station attendance; display, client approach, inspection and possible food capture; a molting/hiding state. **Observation needed:** actual client species and acceptance, prepared-food feeding, and whether a shell-like remnant is a molt while the shrimp is still alive. Neither every fish's approach nor a visible exoskeleton is proof of a completed cleaning visit or death.

### 12. Blue-leg hermit — *Clibanarius tricolor*

**Research.** [Hazlett's direct field/laboratory study](https://repository.naturalis.nl/pub/506197) records grouping, chemical orientation, and structured shell contests in *C. tricolor*; size and recent molt can affect contest outcomes. **Simulation research use:** inspection of empty or occupied shells, withdrawal, contest, transfer, and normal scavenging as separate events. **Observation needed:** shell fit, empty shell availability, crab size, and whether a transfer or injury occurred. The study does not establish a daily shell-change schedule or a snail-kill probability for this tank.

### 13. Scarlet hermit — *Paguristes cadenati*

**Research.** A [curated natural-history account](https://doris.ffessm.fr/Especes/Paguristes-cadenati-Bernard-l-ermite-rouge-de-recif-4381) describes use of gastropod shells, debris feeding, and greater nighttime activity. A [multi-arthropod substrate study](https://researchportal.port.ac.uk/en/publications/neoichnology-of-amphibious-arthropods-effects-of-subaqueous-and-s/) included this species but does not establish aquarium consumption. **Simulation research use:** nocturnal scavenging, shell carriage, and distinct movement on sand/rock. **Observation needed:** confirmed identification and its own shell/food interactions. Do not reuse blue-leg hermit fight or feeding rates.

### 14. Sally Lightfoot crab — *Percnon gibbesi* (working identity)

**Research.** [Stomach-content work](https://www.researchgate.net/publication/225304425_Feeding_Habits_of_Percnon_gibbesi_H_Milne_Edwards_1853_in_the_Sicily_Strait), a [seasonal trophic study](https://www.sciencedirect.com/science/article/pii/S0141113621002695), and a [macroalgal feeding experiment](https://onlinelibrary.wiley.com/doi/10.1002/ece3.71686) support substantial grazing, with diet affected by place and available food. **Simulation research use:** choose specific algal resources, add scavenging only as a distinct behavior, and distinguish live predation from handling a dead animal. **Observation needed:** species identification and direct evidence of any attack on live tankmates. Common-name reputation does not yield a fish-kill rate.

### 15. Black brittle star — *Ophiocoma* sp. (unresolved)

**Research.** [*O. echinata* locomotion experiments](https://blogs.uakron.edu/astleylab/wp-content/uploads/sites/1471/2018/10/Astley-2012-Getting-around-when-youre-round.pdf) show coordinated arm roles; [multi-species *Ophiocoma* observations](https://micronesica.org/sites/default/files/habitat_and_feeding_observations_on_species_of_ophiocoma_micronesica_vol._19_no._1-2_dec._1983.pdf) show deposit and suspension-feeding differences. Both are analogies until the dark tank animal is identified. **Simulation research use:** disc sheltering, coordinated travel, and arm extension for feeding. **Observation needed:** disc and arm photographs, food transport, and whether an arm movement accompanies feeding or relocation. Do not infer a predatory species from color alone.

### 16. Brown feather duster — *Sabellastarte* sp. (unresolved)

**Research.** A [*S. spectabilis* developmental study](https://scientiamarina.revistas.csic.es/index.php/scientiamarina/article/view/132) concerns larvae, not the owner's adult crown-regrowth time. [Invertebrate detail](11_invertebrate_behavior_detail.md#8-brown-feather-duster--sabellastarte-sp) records the identity limitation. **Simulation research use:** tube-fixed adult, crown extension, particle interception, rapid withdrawal, and later emergence, with crown loss modeled separately. **Observation needed:** identification, normal response to flow and routine food, and whether an absent crown retracts, has been shed, or indicates loss of the worm. No fixed regeneration clock is established.

### 17. Nassarius snail — *Nassarius* sp. (unresolved)

**Research.** A [direct *N. vibex* study](https://periodicos.ufc.br/arquivosdecienciadomar/article/view/134) measured bait response and feeding bouts; use only as a conditional analogy until species is confirmed. **Simulation research use:** buried/resting state, chemical food-cue response, travel, proboscis contact, feeding, and reburying. **Observation needed:** species identification, food type, distance and flow direction, successful ingestion. A response to a food cue is not the same as eating, and a scavenger should not be assigned an algae-grazing function.

### 18. Astrea/turban snail — *Lithopoma tectum* (working identity)

**Research.** [WoRMS records](https://www.marinespecies.org/aphia.php?p=taxlist&tName=Lithopoma) can resolve scientific naming; “Astrea” in trade is ambiguous. A [comparison of other aquarium grazing snails](https://pmc.ncbi.nlm.nih.gov/articles/PMC6023205/) shows species differ, but does not measure this one's grazing or righting ability. **Simulation research use:** attached crawling, surface grazing, overturning and attempted recovery as distinct states. **Observation needed:** species and shell verification, actual grazing tracks, righting on different substrates. No unconditional death timer after inversion is supported.

### 19. Asterina-type small sea star — `asterina_sp` (unresolved asterinid)

**Research.** [*Aquilonastra anomala* tracking](https://digitalcommons.chapman.edu/sees_articles/270/) found correlated rather than memoryless movement and shorter travel when food was present. [Asterinid life-history analysis](https://doi.org/10.1086/716913) shows reproductive modes vary. Both are analogies until identification. **Simulation research use:** slow persistent-direction travel, local food response, and optional fission only for confirmed fissiparous taxa. **Observation needed:** photographs of arm shape, repeated contact with corals, and actual tissue loss before assigning corallivory. Presence on a polyp is not proof of predation.

## Corals and corallimorph (6 entries)

### 20. Kenya tree — *Capnella* sp.

**Research.** The tank entry is at genus level. A [soft-coral productivity study](https://pmc.ncbi.nlm.nih.gov/articles/PMC6882883/) discusses different contributions of symbiont photosynthesis and heterotrophy among octocorals and cites *Capnella* nutrition work; it is not a direct parameter response for this colony. Earlier [coral notes](03_corals_rock_light.md) describe tree-like branches and octocoral polyps, but several geometry and growth values there are visual estimates. **Simulation research use:** branch sway, polyp extension, light/food-dependent condition, and fragment settlement only when observed. **Observation needed:** exact identity, expansion by time/light/flow, branch drop, attachment, and whether nearby animals use it as shelter. No fixed daily growth or bleaching threshold is sourced.

### 21. Green star polyps — *Briareum* sp.

**Research.** A [study of *Briareum asbestinum*](https://repository.si.edu/items/b15eddb1-1328-4990-ba3e-117fca0c90fe) measured clonal colony growth in Caribbean reefs. It supports space-occupation concepts but is a related-species analogy until the tank mat is identified. Octocoral polyps have eight tentacles; a closed colony can expose its mat while the living tissue remains. **Simulation research use:** mat expansion over available hard surface, polyp opening/retraction, and neighbor contact. **Observation needed:** growth edge, contact outcomes, flow-related extension, and whether closure is transient. Reef growth rates are not aquarium constants.

### 22. Zoanthids — *Zoanthus* sp.

**Research.** A [direct competition experiment involving *Zoanthus*](https://pmc.ncbi.nlm.nih.gov/articles/PMC9682307/) found that contact with a zoanthid competitor can affect a stony coral's calcification more than the tested acidification treatment; species and conditions matter. A [zoantharian/macroalgal experiment](https://riull.ull.es/xmlui/bitstream/handle/915/40754/Effects_of_macroalgae_and_sea_urchin_grazing_pressure_on_zoantharians_growth_under_laboratory_conditions_.pdf?sequence=1) tested another *Zoanthus* species. **Simulation research use:** connected polyp colony, local expansion and competition for hard surface, opening state and neighbor effects. **Observation needed:** exact species, actual adjacent coral, contact and tissue outcome. Do not assign the measured result to every zoanthid colony.

### 23. Clove polyps — *Clavularia* sp.

**Research.** A [stoloniferan octocoral fluid-circulation study](https://www.journals.uchicago.edu/doi/full/10.1086/BBLv219n2p112) demonstrates that linked polyps and stolons can share internal flow; apply only as a related-taxon mechanism without a confirmed match to this colony. [Earlier tank notes](03_corals_rock_light.md) describe a networked tuft form, with many dimensions explicitly estimated. **Simulation research use:** connected stolon growth, individual polyp extension and retraction, and flow-driven sway. **Observation needed:** exact taxon, whether new polyps appear at advancing stolons, routine day/night expansion, and responses to neighbors. No colony-wide instant-closing rule or quantitative growth rate is established.

### 24. Mushroom corallimorph — *Discosoma* sp.

**Research.** A [light-penetration study](https://pmc.ncbi.nlm.nih.gov/articles/PMC5524488/) used *Discosoma* tissue and symbionts as a model for internal light distribution; it does not define a safe aquarium PAR range. A [propagation report](https://fjdigitallib.usp.ac.fj/greenstone3/aquatic/collection/aquatic/document/HASHe8e5d1170df7c6d3c97705%3Bjsessionid%3DF718FF4CC27C67FC5101D0615B06FEAC?ed=1) concerns *Discosoma* sp. **Simulation research use:** fleshy disc expansion/contraction, substrate attachment and possible clonal spread. **Observation needed:** species, disc diameter over time, light/flow position, and actual daughter formation. Do not equate mushroom corallimorph behavior with a stony “chalice” coral merely because an early visual note grouped them.

### 25. Organ pipe coral — *Tubipora musica*

**Research.** [Coral Trait Database](https://www.coraltraits.org/species/2128) records measured traits; a [field survey](https://esj-journals.onlinelibrary.wiley.com/doi/10.1007/s11284-005-0064-7) studied live and dead colonies along the Egyptian Red Sea. This is an octocoral with red tubular skeleton and connected polyps, not a typical hard coral. **Simulation research use:** rigid tubes with individual soft-polyps opening and closing, colony surface occupancy and local tissue loss. **Observation needed:** identity, polyp extension, new tube formation, and direct evidence before assigning grazing or attack by an unidentified sea star. The field survey does not provide a tank growth rate or survival threshold.

## Macroalga (1 entry)

### 26. Red macroalga — *Gracilaria* sp.

**Research.** A [*Gracilaria* nutrient-uptake experiment](https://www.sciencedirect.com/science/article/abs/pii/S0304377018301906) and [related *Gracilaria/Gracilariopsis* research](https://www.frontiersin.org/journals/marine-science/articles/10.3389/fmars.2024.1453569/full) show that light and nutrient supply interact in red-seaweed growth. Species identity matters, and this entry is only genus-level. **Simulation research use:** branch biomass, light exposure, nutrient uptake, and loss or fragmentation; treat uptake as a finite process tied to growth, not automatic removal of all nitrate/phosphate. **Observation needed:** confirmation that the tank organism is *Gracilaria* rather than another red alga or gorgonian; record frond morphology, attachment, actual growth, and grazing.

## Shared environmental questions

The species cards intentionally avoid invented lethal numbers. Research on [NOAA ocean acidification](https://oceanacidification.noaa.gov/), [reef carbonate chemistry](https://coralreef.noaa.gov/topics/national-coral-reef-monitoring-program/protocols), and [aquarium water chemistry](https://www.reef2reef.com/threads/optimal-parameters-for-a-coral-reef-aquarium-by-randy-holmes-farley.173563/) points to coupled drivers: temperature, salinity, pH/alkalinity, dissolved oxygen, nitrogen and phosphorus, light, flow, food, and time of exposure. Each outcome should retain species, life stage, acclimation, and experimental setting. Wild bleaching [degree-heating-week](https://www.coralreefwatch.noaa.gov/product/5km/tutorial/crw10a_dhw_product.php) markers describe reef-scale satellite risk, not a mortality threshold for a coral frag in a 60-gallon tank. Shared parameters can influence multiple organisms differently; verify species-specific evidence before tuning any rule.

### Additional studies reviewed for this roster

| Study | What it measured | Research implication and limit |
| --- | --- | --- |
| [Ocellaris juvenile salinity experiment](https://doi.org/10.3390/fishes9010030) | Forty-day-old juveniles reared 60 days at 5, 15, 25, or 35‰; survival was at least 95% at 15–35‰, while all at 5‰ died within six days. | Responses depend on exposure duration and life stage. These are not safe target salinities for corals or a mixed reef. |
| [Ocellaris thermal experiment](https://doi.org/10.1007/s40071-019-0228-7) | Hatchery juveniles acclimated for 21 days across 20–35°C; metabolic scope was lower at 20 and 35°C than at intermediate treatments. | Distinguish performance from acute critical-temperature endpoints. Neither a short-term endpoint nor a preferred temperature is a universal husbandry target. |
| [Cleaner wrasse removal study](https://pubmed.ncbi.nlm.nih.gov/28307499/) and [longer-term client study](https://pubmed.ncbi.nlm.nih.gov/21733872/) | Different field studies found no detectable short-term whole-reef community effect after cleaner removal, but cleaner exclusion over eight years affected parasite burden and growth in a particular damselfish client. | Preserve both findings. A cleaner's effect can be client-specific and slow; neither paper supports instant tank-wide benefit or loss. |
| [Lawnmower blenny grazing comparison](https://doi.org/10.1007/s00338-024-02612-7) | A 2025 short aquarium experiment found low bite rates and no net total fouling reduction for *S. fasciatus* in its setup, and possible damage to very young coral recruits. | Complements the older film-removal experiment: substrate and coral life stage matter. Do not transfer recruit mortality to established tank colonies. |
| [Four cleaner-shrimp species and parasite challenges](https://doi.org/10.1038/s41598-018-32293-6) | *Lysmata amboinensis* and *Stenopus hispidus* differed by parasite and day/night conditions. | Keep shrimp species, parasite identity, and light state separate. Results do not establish clearance of every aquarium parasite. |
| [Zoanthus/Palythoa symbiosis experiment](https://doi.org/10.1111/maec.12291) | Tested particular host/symbiont pairings under heat and carbonate changes; responses differed. | A genus-level `zoanthus_sp` entry cannot inherit one universal bleaching or growth threshold. Publisher abstract was reviewed; full methods were not available in this pass. |
| [Zoanthus and Discosoma isotope study](https://doi.org/10.1186/s40645-024-00680-5) | Aquarium specimens were used to infer differences in photosynthate transfer among cnidarian groups. | Light supplies energy through symbionts; one pooled inferred percentage should not be assigned to all colonies or used to dismiss heterotrophic feeding. |
| [Discosoma tissue-optics experiment](https://doi.org/10.1098/rspb.2017.0320) | Used *Discosoma* tissue to study how wavelengths penetrate photosymbiotic tissue. | Spectral quality and tissue optics matter; this does not define a Discosoma PAR care range. |
| [Gracilaria cornea flow experiment](https://doi.org/10.1073/pnas.0912348107) | Water movement enhanced net photosynthesis and affected internal oxygen in a tested red alga. | Flow and gas exchange can affect algal productivity. The current and response are not an optimum for the unidentified tank *Gracilaria*. |

### Shared mechanisms that need their own evidence labels

- [A public saltwater aquarium microbiome time series](https://doi.org/10.3390/ani15101446) and [new aquarium live-rock conditioning study](https://doi.org/10.1128/msphere.00043-19) show that microbial communities change during startup and loading. They support an evolving biofilter concept, but neither supplies a universal cycle-completion day or a species-specific ammonia tolerance for this roster.
- [PyCO2SYS methods](https://gmd.copernicus.org/articles/15/15/2022/) explain how alkalinity, dissolved inorganic carbon, temperature, and salinity can yield internally consistent carbonate-system variables. This is a chemistry method, not a coral growth law. Dosed calcium or unusual ionic composition needs explicit treatment.
- A [stony-coral nutrient-imbalance experiment](https://www.nature.com/articles/nclimate1661) and [oxygen/feeding/light experiment](https://journals.plos.org/plosone/article?id=10.1371/journal.pone.0052702) demonstrate interacting stressors in **species absent from this roster**. They motivate further research, but their numerical effects must not be copied onto *Capnella*, *Tubipora*, *Discosoma*, or the other listed animals.

## Research and simulation evidence ledger

For every future claim or rule, record: **catalog ID; confirmed scientific name or uncertainty; source URL/DOI; evidence type (direct, account, analogy, owner observation, design assumption); environment (wild, laboratory, aquarium); life stage and sample size; measured outcome and units; time scale; transfer caveat; proposed simulation state or interaction; and what tank observation could test it.** Store contradictory claims together until resolved. Do not turn a study mean into a deterministic individual behavior. The detailed files linked at the top remain the working citation trail; this master is the roster-aligned entry point.
