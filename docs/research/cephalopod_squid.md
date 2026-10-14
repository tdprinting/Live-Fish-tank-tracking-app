# A squid for the reef tank: research and recommendation

Requested by Tee: "My wife also wants a reef-compatible squid, or one as close as possible."
This note picks the species, checks it against the animals Tee keeps, and records the values the sandbox uses
(`euprymna_berryi.json`, `BobtailSquidBrain.kt`, `SquidRig.kt`; SIMULATION §20).

Tags: **[M]** is a measured or published value, with its source in §8. **[L]** is our own estimate or
inference, used where nothing is published.

---

## 1. Recommendation and verdict

**Pick: the hummingbird bobtail squid, *Euprymna berryi*.** It is a sepiolid, so not a true squid, but it is the
closest thing to a reef-tank squid that exists.

Why this one:

- It is a small, tropical Indo-Pacific animal from sandy seagrass habitats [M: MBL]. The mantle is about 3–4 cm
  and the whole animal about 6 cm [M: Jolly 2022].
- It has been bred in closed seawater systems for several generations at the Marine Biological Laboratory, MBL
  [M: Jolly 2022, MBL]. A captive-bred source is possible, at least in principle.
- It does what people picture a squid doing: it hovers on fins, shoots out its tentacles, inks and jets away.
  It also glows from a bacterial light organ and buries in the sand by day [M: §5].

**Verdict for Tee's tank: not reef-safe as stocked.** It would be a sandbox experiment, not a real addition:

- The falco hawkfish and the dottyback would hunt it or harass it.
- It would eat small or freshly molted shrimp and tiny hermits.
- It lives only about six months.
- It prefers water around 20–25 °C, cooler than a typical reef.

It suits a quiet species tank with a deep sand bed and live mysids. Nothing in this note is a reason to buy one
for the existing display tank.

**Closest alternative: the dwarf cuttlefish, *Sepia bandensis*.** It is the cephalopod the hobby actually keeps
and the one most often sold [M: Ross]. Compared with the bobtail:

- It lives longer, about a year [M: Ross].
- It needs a bigger tank: about 30 gal for one [M: Ross].
- It too eats shrimp and crabs and should not share a tank with fish [M: Ross].
- It is not a squid: it has a cuttlebone and a fin that runs the length of the mantle.

---

## 2. Candidates compared

| Species | Size (mantle) | Lifespan | Habitat / temp | Captive record | Fit for a reef tank |
|---|---|---|---|---|---|
| ***Euprymna berryi***, hummingbird bobtail | ML ♂ 32 ± 5 mm, ♀ 36 ± 4 mm; max ~5–6 cm ML; 17–23 g [M: Jolly 2022] | ~139 d life cycle, oldest 265 d [M: Jolly 2022]; ~6 mo wild [M: MBL] | Indo-Pacific (Indonesia–Japan–China), sandy seagrass [M: MBL]; reared at 20–23 °C [M: Jolly 2022] | Bred over several generations at MBL [M] | **Best small squid-like option.** Tropical, sand-burying, eats crustaceans |
| *E. scolopes*, Hawaiian bobtail | ~35 mm ML [M: Anderson] | ~80 d to 4 mo in culture [M: Hanlon 1997]; 3–10 mo [M: Anderson] | Hawaiian endemic [M: Anderson]; cultured 21–25 °C [M: Hanlon 1997] | Lab model for the *Vibrio* symbiosis [M: Nyholm 2021] | Like *E. berryi* but endemic to Hawaii, so collection is restricted [L] |
| *Sepiola* spp. | ~2–3 cm ML [L] | < 1 yr [L] | Temperate Mediterranean and NE Atlantic, cold water [L] | Lab only [L] | Needs a chiller; wrong for a tropical reef [L] |
| *Idiosepius* spp., pygmy squid | ≤ 16 mm ML (*I. paradoxus*); ~15/21 mm [M] | ~150 d captive; *I. pygmaeus* 67–79 d [M] | Seagrass; it glues itself to blades with a dorsal adhesive organ [M] | Lab only | Tiny and very short-lived; needs live copepods and mysids all day [L] |
| *Sepioteuthis lessoniana*, bigfin reef squid | 36–40 cm ML; ~1.8 kg [M: FAO 1984, SeaLifeBase] | ~6 mo–1 yr [L] | Open-water schooling squid [L] | Public aquaria and research systems | **Far too big and fast** for a 48″ tank [L] |
| *Sepia bandensis*, dwarf cuttlefish | ~4″ at 6 months [M: Ross] | ~1 yr [M: Ross] | Tropical Indo-Pacific; 78 °F [M: Ross] | The common hobby cephalopod [M: Ross] | **Best long-lived alternative**, but needs a species tank [M: Ross] |

---

## 3. Water and husbandry (E. berryi)

| Parameter | Value | Tag | Sim value |
|---|---|---|---|
| Temperature | Reared at 23 °C, then 20 °C [Jolly 2022]; *E. scolopes* cultured at 21–25 °C [Hanlon 1997] | M | opt 20–25.5 °C, tol 18–27.5, lethal 14 / 30.5 [L] |
| Salinity | 33–37 ppt [Jolly 2022] | M | SG 1.024–1.026 opt [L] |
| pH | 8.2–8.4 [Jolly 2022] | M | opt 8.0–8.4 [L] |
| Oxygen | Cephalopods have high oxygen demand [L] | L | DO opt ≥ 6.0, tol 5.0, lethal 3.5 mg/L [L] |
| Copper | "Deadly" to cephalopods [Ross, on *S. bandensis*] | M | `copperSensitive: true` |
| Sand | Needs sand deep enough to bury completely with the eyes out. Without it: skin lesions [Jolly 2022], and *E. scolopes* swam constantly [Hanlon 1997] | M | burial depth 0.32 of body depth [L] |
| Stocking | ≤ 8 mature animals per 70 L; males turn aggressive when crowded; no cannibalism reported [Jolly 2022]. *E. scolopes* tolerated high densities [Hanlon 1997] | M | `onePerTank: false`, min 20 gal [L] |
| Ink | A skimmer removes ink [Ross]. Ink in a small tank can suffocate the animal itself [L] | M/L | ink puff lasts 25 s on screen [L] |
| Intakes | Small squid get pulled onto pump intakes and overflows [L] | L | care note |

**Tee's reef runs warmer than this animal wants [L].** The top of the squid's optimum is about 25 °C. A typical
reef at 26 °C is already at the edge of that band. In the sim, every degree above 25.5 °C adds heat stress, using
HeatModel's `CEPHALOPOD` profile.

---

## 4. Diet and compatibility with Tee's livestock

**Diet.** *E. berryi* is raised on mysids and later on glass and marsh shrimp. Lab animals can be trained onto
frozen shrimp in about a week [M: Jolly 2022]. *E. scolopes*:

- prefers shrimp to fish. Fish slip from its suckers and take 2–3 min to subdue, against 10–30 s for a shrimp
  [M: Hanlon 1997];
- does best on large adult mysids [M: Hanlon 1997];
- needs feeding many times a day [M: Hanlon 1997].

In the wild it eats small benthic crustaceans [M: MBL]. In the sim:

- Pods (mysids and amphipods) come out of the microfauna model's `PodPredator.BOBTAIL_SQUID` row.
- Half the individuals learn to take thawed food [L].
- Hunger carries `starvationDays: 6` [L].

**Tankmates.** Risk ratings below are what the sim uses (`interactions.json`):

| Tankmate | Risk | What happens | Tag |
|---|---|---|---|
| **Falco hawkfish** | **High** | A perch-and-pounce predator of small crustaceans and fish. An exposed 6 cm squid at night is the size of its prey. Buried in sand, the hawk cannot find it. | L (by analogy to its diet) |
| **Dottyback** | **Medium–High** | Aggressive cave predator. Would harass or kill a small squid. | L |
| Blue tang | Medium | Not a predator, but busy and boisterous. It keeps the squid hiding and takes food first. | L |
| Cleaner wrasse | Medium | May pick at the squid's soft, scaleless skin. | L |
| Clownfish, blennies, gobies, cardinal | Low | Neither predator nor prey. They add to the "busy fish" stress. | L |
| Leopard wrasse | Medium (as busy fish) | Hunts small crustaceans in the sand at the same spots. A competitor, not a threat. | L |
| **Coral-banded shrimp** | **Medium–High (both ways)** | A molting or juvenile banded shrimp is squid food. An adult banded shrimp can grab a squid at night. | M (shrimp diet) + L |
| Skunk cleaner shrimp | Medium | Juveniles and freshly molted adults are prey. A 6.5 cm adult is left alone. | M + L |
| Blue-leg hermits | Medium | The smallest are prey (Ross on *S. bandensis*: hermits OK) [L] | L |
| Scarlet hermits, snails | Low | Too big or too armoured [Ross: hermits and snails safe with *S. bandensis*] | M (analogy) |
| Brittle star | Medium | Competes for food. It can catch a buried squid at night. | L |
| Corals | None | Squid do not eat or touch corals [Ross: corals fine with *S. bandensis*] | M (analogy) |

**Compatibility warnings** in `Compatibility.kt`, shown wherever the app shows warnings:

| Code | Severity | When it fires |
|---|---|---|
| `squid_predator_fish` | Severe | Whenever a squid and a hawkfish, dottyback or eel share a tank, in either order of adding |
| `squid_eats_crustaceans` | Warning | Shrimp, hermits or a crab are present |
| `squid_busy_fish` | Warning | Any other fish are present |
| `squid_short_life` | Info | Always |

---

## 5. Behaviour and locomotion

- **Day: buried in sand.** Bobtails spend the day under the sand with the eyes exposed and hunt at night
  [M: Hanlon 1997, MBL, Jolly 2022]. They blow a hollow with the funnel, settle in and sweep sand over the back
  with the arms. A mucus "sand coat" holds the grains on [M: Shears 1988; Anderson]. The sim takes about 8 s to
  bury [L] and sets the mantle centre 0.32 of the body depth below the sand surface [L].
- **Night: hovering on fins.** The animal hovers and manoeuvres with its round ear-shaped fins and jets for speed
  [M: general cephalopod biology; L for its rates]. In the sim:
  - The fins beat 1.5 Hz while hovering and 3 Hz at most [L].
  - Cruise speed is 0.6 BL/s and the burst is 8 BL/s [L].
- **Light organ (counter-illumination).** The ventral light organ holds *Vibrio fischeri*. Its light output tracks
  the downwelling light, so the animal casts no silhouette against moonlight [M: Jones & Nishiguchi 2004; MBL].
  - At dawn the squid vents 70–95 % of its symbionts, and they regrow by night [M: Nyholm & McFall-Ngai 2021].
  - The organ matures in about 8 weeks [M: Nyholm 2021].
  - Hanlon 1997 did not see the light organ in use in culture [M].
  - The sim vents 90 % at lights-on and regrows with a 6 h time constant [L]. Glow scales with the symbiont level and
    the night light level.
- **Hunting.** Bobtails attack fast. Paralarvae strike "at least 12 body lengths in about a second" [M: Hanlon 1997].
  The adult stalks, raises the first arm pair, shoots the two tentacles and pulls the prey to the arms [L, by
  analogy with *Sepia*: "like a lizard's tongue", Ross]. In the sim:
  - Strike reach is 0.9 BL [L].
  - A strike on a pod connects 60 % of the time [L].
  - One pod catch relieves 40 minutes of hunger [L].
- **Escape.** "Swift backwards jet escape with inking" [M: Hanlon 1997]. In the sim:
  1. The squid blanches.
  2. It releases an ink puff (a pseudomorph about one mantle across) [L].
  3. It jets mantle-first, 6–10 BL away from the threat, arms facing the threat [L].
  4. It hides dark and low.
  The ink refills in about an hour [L].
- **Colour.** Chromatophores are neurally controlled and change within a fraction of a second [M: Hanlon &
  Messenger 2018]. The sim shows:
  - darkening when alarmed or hunting;
  - blanching at the jet;
  - "passing clouds" while hovering [L, for the timing].

---

## 6. Welfare caveats

- **Sentience.** Cephalopods are treated as sentient:
  - The LSE review for the UK government concluded this [M: Birch et al. 2021].
  - The UK Animal Welfare (Sentience) Act 2022 covers them [M].
  - The EU research directive 2010/63/EU covers them [M].
- **Lifespan.** Expect about 6 months [M], ending in a single breeding and death (semelparity) [M, general sepiolid
  biology]. A household should be ready for that.
- **Food.** Many animals need live food daily. A tank without a strong mysid or amphipod population needs a
  culture or regular live shrimp [M: Hanlon 1997, Jolly 2022].
- **Sourcing.** Wild collection of *E. berryi* is unregulated and its status is IUCN Data Deficient [M: MBL].
  Prefer captive-bred animals, which are rarely available to hobbyists [L].
- **Tankmates.** In Tee's mixed reef the squid would spend most of its time stressed or hiding [L]. It would put
  the shrimp at risk and be at risk from the hawkfish.

---

## 7. How the sandbox models it

The squid is added from the stocking gallery's invertebrate group as "Hummingbird bobtail squid". It is **not** in
Tee's real-tank preset.

| Piece | Where |
|---|---|
| Species sheet: sizes, tolerances, diet, body capsule, care notes | `core/src/main/resources/species/euprymna_berryi.json` |
| Brain: Buried, Hover, Hunt, TargetFed and Jet behaviours, dawn venting, ink, the abstract-mode hunting rate | `core/.../ai/BobtailSquidBrain.kt` |
| Render signals: fins, mantle, arms, tentacles, chromatophores, glow and the ink cloud | `core/.../sim/SquidRig.kt` (motion mode 4, `uLimb[0..4]`) |
| Movement | `Gait.FIN_JET` in `MotionProfile.kt` |
| Heat stress | HeatModel `CEPHALOPOD` profile, HealthModel temperature and DO override |
| Hawk and pod prey | `Predation` threat radius and prey weight (0 when buried); `PodPredator.BOBTAIL_SQUID` |
| Warnings and risk table | `Compatibility.kt`, `interactions.json` |
| Model | `tools/blender/squid.py` builds `bobtail_squid.glb` (4242 tris) and `_lod1` (1280 tris) |
| Shaders and ink | `fish.vert` `squidPose`, `fish.frag` `uChroma`, `render/InkVisuals.kt` |

---

## 8. Sources

- **Jolly J. et al. 2022.** "Lifecycle and husbandry of the hummingbird bobtail squid *Euprymna berryi*."
  *Frontiers in Marine Science* 9:1039775. doi:10.3389/fmars.2022.1039775. Gives:
  - temperatures, salinity and pH;
  - mantle lengths, weights, life cycle and spawning;
  - diet and training onto frozen food;
  - sand requirement and lesions;
  - stocking density.
- **Marine Biological Laboratory (MBL), Cephalopod program: *Euprymna berryi* species page.** Gives size, mass,
  range, habitat, burial, light-organ function, diet and IUCN status.
- **Hanlon R.T., Claes M.F., Ashcraft S.E., Dunlap P.V. 1997.** "Laboratory culture of the sepiolid squid
  *Euprymna scolopes*: a model system for bacteria-animal symbiosis." *Biological Bulletin* 192:364–374. Gives:
  - culture temperatures and life cycle;
  - burial and behaviour without sand;
  - prey preference and handling times;
  - attack speed;
  - jet-and-ink escape.
- **Anderson R.C., The Cephalopod Page: *Euprymna scolopes*.** Size, range, lifespan, sand coat.
- **Shears J. 1988.** "The use of a sand-coat in relation to feeding and diel activity in the sepiolid squid
  *Euprymna scolopes*." *Malacologia* 29(1):121–133.
- **Jones B.W., Nishiguchi M.K. 2004.** "Counterillumination in the Hawaiian bobtail squid, *Euprymna scolopes*
  Berry (Mollusca: Cephalopoda)." *Marine Biology* 144:1151–1155.
- **Nyholm S.V., McFall-Ngai M.J. 2021.** "A lasting symbiosis: how the Hawaiian bobtail squid finds and keeps its
  bioluminescent bacterial partner." *Nature Reviews Microbiology* 19:666–679. Covers dawn venting and light-organ
  maturation.
- **Ross R., Tropical Fish Hobbyist: "Keeping the dwarf cuttlefish *Sepia bandensis*."** Gives:
  - tank size, temperature, salinity and pH;
  - diet and tankmates;
  - lifespan;
  - copper and ink.
- ***Idiosepius*:** species descriptions and culture papers. Mantle length, adhesive organ, captive lifespans.
- **FAO Species Catalogue vol. 3, Cephalopods of the World (Roper, Sweeney & Nauen 1984); SeaLifeBase:
  *Sepioteuthis lessoniana*.** Gives maximum mantle length and weight.
- **Hanlon R.T., Messenger J.B. 2018.** *Cephalopod Behaviour*, 2nd ed., Cambridge University Press. Covers the
  speed of chromatophore control.
- **Birch J., Burn C., Schnell A., Browning H., Crump A. 2021.** *Review of the Evidence of Sentience in Cephalopod
  Molluscs and Decapod Crustaceans.* LSE, for DEFRA. Also cited: UK Animal Welfare (Sentience) Act 2022;
  Directive 2010/63/EU.
