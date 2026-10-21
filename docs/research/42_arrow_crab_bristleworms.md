# 42. Arrow crab and bristleworms

How the sim models them, and how sure each number is. The raw research brief (with its sources) follows as the appendix.
Tags: [W] reference source, [S] several hobby sources agree, [1] single or forum source, (est.) a design estimate.

## A. Arrow crab (Stenorhynchus seticornis)

### A.1 Body and model
- Carapace up to 6 cm, legs to about 10 cm span [W]. Sim: maxLengthCm 6, typical 4.5.
- Triangular carapace widest at the rear, a long serrated rostrum, golden body with fine dark lines, reddish legs,
  violet-blue claws [W]. Model: `tools/blender/arrow_crab.py` (4.3k tris).

### A.2 Legs
- Eight thin walking legs plus chelipeds [W]. Sim: the shared crab leg rig (CrawlerSeat + CrabModel.ARROW), each foot
  planted on rock, sand or glass; a slow gait (CrabLegs.arrow: 0.5-2 Hz) for its about 1 cm/s walk (est.).

### A.3 Day and night
- Activity pattern not found in sources (est.: nocturnal, as most inachid spider crabs). Sim: stands still under a ledge
  by day (Perch), stalks over the rock at night.

### A.4 Diet and aggression
- Opportunist: small feather duster worms and other invertebrates, scavenges [W][S]. Takes bristleworms it can catch
  but cannot clear worms deep in the rock [1]. Sim: HuntWorm (exposed worms), Probe (reaches into crevices; success
  rises with worm numbers, PROBE_K 70 est.), scavenges food.
- Attacks shrimp (notably banded coral shrimp), may take small slow fish when underfed, nibbles feather dusters [S].
  Sim: a hungry crab pokes at hermits and soft, freshly molted shrimp at night (Harass); kills only through the
  nightly matrix rolls (interactions.json arrow_*), and only when hungry.
- Territorial, one per tank [S].

### A.5 Molting
- Molt interval not found. Sim (est.): juveniles under 75 % of max size molt every 25-45 days; adults have a terminal
  molt, as spider crabs (Majoidea) do.

## B. Bristleworms (Eurythoe-type amphinomids)

### B.1 Arrival
- Hitchhike on live rock, frags and sand (general hobby knowledge). Sim: `Hitchhikers` (shared with the aiptasia
  pest): per newcomer 90 % for live rock, 15 % for a coral frag, 3 % for a fish bag, 0 for dry rock; a stocked tank
  also gets a background arrival at 1/60 per day (est.). Only My Tank has hitchhikers turned on.

### B.2 Behaviour
- Mostly nocturnal, hide in crevices by day, come out to scavenge leftovers and detritus [S][W]. Sim: `WormField`
  shows up to 8 worms (from the population times its night exposure), plus extra ones drawn out by food on the bottom
  at any hour. Each glides out of its hole along the rock, eats settled food, and backs into its hole at dawn, when a
  fish noses at it, or when full. Speeds: 0.8 cm/s out, 3 cm/s back (est.).

### B.3 Breeding and booms
- Population growth under overfeeding not measured [U]; hobbyists report fast breeding when overfed [1]. Sim:
  the population's carrying capacity rises up to 4x with leftover food and detritus (WORM_BOOM_*, est.), and a
  "booming" note shows at twice the lean-tank capacity.

### B.4 Cleanup benefit
- They eat leftover food and detritus (est. 1 mg of nitrogen turned over per adult per day, WORM_SCAV_TAN_MG): the sim
  removes that detritus and returns half as ammonia, half exported. A large population also takes a share of leftover
  food in fast-forward.

### B.5 Predators
- Arrow crab: yes, small exposed worms [1]. Hawkfish, dottybacks and wrasses: no tested source [U]; hobby reports name
  wrasses [1]. Sim (est.): the hawkfish, dottyback and leopard wrasse snap up a worm that is out within reach (WormSnap,
  35-50 % grip); the hawkfish's pod diet weight for worms is 0.4.

## Appendix: research brief


Confidence key: [W] Wikipedia fetch, [S] several consistent sources, [1] single or forum source, [U] not found in research.

## 1. Arrow crab, Stenorhynchus seticornis (yellowline arrow crab)

**Size**
- Carapace up to 6 cm (2.4 in) long [W]. Aquarium guides give about 1-2 in carapace, 4-6 in total with legs [S].
- Leg span up to 10 cm (3.9 in) [W]. Long, thin legs.
- Males are larger than females [S].

**Colouration and form**
- Body golden, yellow or cream, with brown, black or iridescent-blue lines and fine dark lines on the carapace [W].
- Legs reddish or yellow [W]. Leg colour bands: [U].
- Claws blue or violet, often violet-tipped [W].
- Eye colour: [U].
- Body triangular. Rostrum a long point with serrated edges [W].

**Walking legs and movement**
- 8 walking legs (4 pairs), long and thin, plus chelipeds (claws). Gait, speed and climbing method: [U]. Long legs can catch in pump intakes, so screened intakes are advised [S].

**Activity and diet**
- Opportunistic. Natural diet is small feather duster worms and other reef invertebrates [W]. Scavenges dead material [S].
- Aquarium diet: shrimp, mollusks, flakes, pellets, bristleworms [S].
- Eats bristleworms it can catch, but cannot clear worms hidden deep in rock and sand [1].
- Nocturnal or diel pattern: [U].
- Picking method on worms in crevices: [U] (no source described it).

**Aggression and reef safety**
- Rated reef-safe with caution [S]. May nibble polyps, some soft corals, feather dusters and small shrimp [S].
- Attacks crustaceans such as banded coral shrimp [S]. May attack small, slow fish, especially when underfed [S].
- Very territorial. Keep one per tank unless the tank is large with many hiding spots [S].
- Hermit crabs, snails, other crabs: [U] in the research.
- Association with anemones: in the wild, often with *Lebrunia danae*, in groups of 1-6 in SE Brazil [W].

**Molting, lifespan, cost**
- Molting frequency: [U]. Molting is normal, the empty shell is the old exoskeleton, and it is the most vulnerable period. Lost legs or claws regrow after a molt [S].
- Lifespan: about 5 years in captivity per most sources; one UK retailer says 2-4 years [1].
- Price: one retailer guide about USD 15 plus shipping (possibly dated). Listings of USD 23-25 were for the Panamic arrow crab (S. debilis), not S. seticornis [1].

**Tank and water**
- Minimum tank: at least 30 gallons in most guides; one retailer says 20 gallons per crab minimum [S/1].
- Temperature 72-79 F (about 22-26 C), pH 8.1-8.4, dKH 8-12, SG 1.023-1.026 [S].
- Needs an established tank with rockwork, cracks and caves [S].
- Invertebrates should not receive copper medication [S].

**Caveat:** The Wikipedia fetch describes this species without any mention of colour patterning on the legs, which suggests the leg colour bands in hobby literature are not confirmed by a reference source.

## 2. Bristleworms (Eurythoe complanata, Hermodice carunculata, others)

**Eurythoe complanata (iridescent fireworm)**
- Size: up to 6 in (150 mm) [W]. Juvenile size and growth: one hobbyist reports growth from about 1 in to several in within months [1].
- Colour: grey, green and pink [W]. Aposematic white bristles and red gill bands [S].
- Segments and parapodia: [U] in the research.
- Chaetae (bristles): carry the toxin complanine, a quaternary ammonium compound, used for defence and to stun prey. Bristles sting [W].
- Gills (branchiae): tufted, starting on the second segment and running along the body [W].
- Mouth: expands its lip to 4-5 times its length to swallow prey whole [W].
- Diet: carnivorous, eats sponges, coral, molluscs and other worms [W]. Scavenges leftover food, dead tissue and detritus at night [S].
- Behaviour: mostly nocturnal, hides in rock crevices by day [S/W]. Speed: [U].
- Sand turnover: burrowing aerates sand, but this is a general bristleworm claim, not species-specific [1].
- Reproduction: sexual reproduction by broadcast spawning, with captive spawning recorded about one week after the full moon in September [1]. Asexual reproduction by regeneration [W]. Transverse fragmentation is reported in one reference, but the Wikipedia summary does not describe fission [1].
- Regeneration: fragments can rebuild head and tail; a blastema forms about one week after autotomy [1].
- Growth rate and population growth with overfeeding: [U].
- Aquarium status: disputed. ReefApp calls it the most common species in home aquariums and beneficial [1]. A German database says not suitable for aquaria [1]. Hobbyists report it breeds quickly and is hard to remove [1].
- Identification: a 2010 study questioned whether all worms classed as E. complanata are one species [1].

**Hermodice carunculata (fireworm)**
- Size: conflicting. One source says 1-4 cm, usually 1-1.5 cm (likely an outlier). Another says 5-10 cm, up to 35 cm. A Caribbean field guide says up to 25 cm, mostly up to 15 cm. A taxonomic entry gives up to 300 mm with 60-150 segments [S/1].
- Chaetae: hollow, venom-filled, penetrate skin and break off when handled. Research on notochaetae suggests they may carry toxins; whether irritation is mechanical or chemical is unresolved [1].
- Diet: corals, especially Acropora, sponges and sea anemones [S]. Coral predation is a documented threat in the wild [S].
- Reef aquarium status: one species profile lists it as not reef safe and rare in home aquaria, and says it preys on soft corals and anemones [1].
- Segment and parapodia detail, speed, reproduction, and aquarium growth rate: [U].

**Other amphinomids and eunicids:** [U] in this research.

**Arrival:** hitchhikes on live rock, frags and sand [general aquarium knowledge, not verified in the research].

**Predators in aquaria**

| Predator | Eats bristleworms? | Evidence |
|---|---|---|
| Arrow crab | Yes, small worms it can catch | [1] Hobbyist and retailer claims; cannot eradicate worms in rock and sand |
| Coral banded shrimp | Not verified | [U] |
| Sixline wrasse | Not verified | [U] (search returned nothing) |
| Melanurus wrasse | Not verified | [U] |
| Hawkfish | Not verified | [U] (search returned nothing on hawkfish) |
| Dottyback | Not verified | [U] |
| Coris wrasses | Not verified | [U] |
| Pufferfish | Not verified | [U] |
| Blue-head wrasse | Anecdotal: "I've seen blue-head wrasse eat small ones" | [1] Coral-List 2010 |
| Hogfish, porgies | Porgies eat fireworms in the field; lab porgies spat out introduced fireworms | [1] Coral-List 2010 |
| Cone snail (Conus regius) | Preys on H. carunculata in the wild; more dangerous than the worm | [1] |

Summary: no source gives a tested per-species predation rate on aquarium bristleworms. Bristleworms hide deep in rock and sand, so predators usually reduce but do not eliminate them.

**Traps:** [U] in this research. Common hobby practice (not verified here): baited traps, lights off to draw worms out at night.

## Gaps for the simulation
- Arrow crab: molt interval, gait and speed, climbing, prey-picking method, diel activity, hermit/snail/crab aggression.
- Bristleworm: segment counts, speed, growth rate, population doubling time under overfeeding, parapodia detail, reproduction rates in tanks.
- Predation rates on bristleworms for each fish and shrimp species.
- Hermodice size and aquarium behaviour.

## Sources
- https://en.wikipedia.org/wiki/Stenorhynchus_seticornis
- https://en.wikipedia.org/wiki/Eurythoe_complanata
- https://www.aquariumsource.com/arrow-crab
- https://www.saltwateraquariumblog.com/arrow-crab-care-in-a-reef-aquarium/
- https://www.aquariadise.com/arrow-crab/
- https://reefs.com/invertebrates/arrow-crab/
- https://www.buildyouraquarium.com/arrow-crab/
- https://www.liveaquaria.com/products/panamic-arrow-crab
- https://reefexclusive.com/products/arrow-crab
- https://reefapp.net/encyclopedia/eurythoe-spp
- https://reefapp.net/encyclopedia/hermodice-carunculata
- https://www.reeflex.net/tiere/6852_Eurythoe_complanata.htm
- https://www.nano-reef.com/forums/topic/9159-fire-bristle-worm-i-have/
- https://help.algaebarn.com/portal/en/kb/articles/the-great-bristle-worm-debate-the-truth-about-bristle-worms-in-saltwater-aquariums-27-10-2025
- https://coral.aoml.noaa.gov/pipermail/coral-list/2010-February/008013.html
- https://coral.aoml.noaa.gov/pipermail/coral-list/2010-February/008018.html
- https://ultimatereef.net/goto/post?id=5678710
- https://www.sealifebase.ca/summary/SpeciesSummary.php?id=38154
- https://iris.unimore.it/handle/11380/1127010
