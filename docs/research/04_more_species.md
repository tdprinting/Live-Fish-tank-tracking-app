# Reef Tank Additional Species: Sim Reference (60 gal, per-animal AI)

Scope: the 11 additional inhabitants the owner confirmed for the 60 US gal (~227 L) reef, on top of the 8 species in 02_fish_species.md. Falco hawkfish is already covered there and is not repeated. Each entry uses the same structure A-F as 02.

**Source legend.** Facts are cited to the source tags at the end. Values marked **(est.)** are estimates or general reef knowledge the sources did not state. Treat est. values as tunable parameters.

**Owner-item resolutions:**
- Item 3 "Indigo dottyback": sold under this name, the ORA/captive-bred indigo is the hybrid *Pseudochromis fridmani* x *sankeyi* (listed as "Electric Indigo" by some retailers) [LA-IDD][TPP].
- Item 4 "cleaner shrimp": assumed *Lysmata amboinensis* (skunk cleaner).
- Item 5 "hermit crabs": assumed *Clibanarius tricolor* (blue-leg) and *Paguristes cadenati* (scarlet).
- Item 6 "black brittle star": the sold species is not confirmed. Quality Marine lists *Ophiocoma echinata* under "Brittle Sea Star" [QM-OPH]. Other black-disc *Ophiocoma* are sold under the same name, so treat the species as open.
- Item 7 "brown tube worm": assumed brown feather duster, *Sabellastarte* sp. Sources cover *S. indica* [TFH-SI].
- Item 9 "astria starfish" and "astrea snails" are two separate animals (owner clarified). **Astrea snail** (*Lithopoma tectum*, trade name) is item 9a. **Asterina starfish** (*Asterina* / *Aquilonastra* sp.) is item 9b.
- Item 10 organ pipe coral: *Tubipora musica*.
- Item 2 tailspot blenny: confirmed. Its coloration is NOT described in the sources reached (see section 7 gaps). The blue/orange eye-lines in 02 remain unconfirmed.

---

## 0. Quick reference (new species)

| Species | Max size (cm) | Typical in tank (cm) | Swim/locomotion mode | Zone | Active | Hardiness | Tank min (sourced) |
|---|---|---|---|---|---|---|---|
| Lawnmower blenny (*Salarias fasciatus*) | 12-14 [LF-g] | 10-13 | Labriform, perch | Rock surface, holes | Diurnal, grazing | High | 120 L (~32 gal) single [LF-g] |
| Tailspot blenny (*Ecsenius stigmatura*) | ~6.4 (2.5 in) [SWB] | 5-6 | Labriform, perch | Rock, holes | Diurnal | High | 8 gal min, 10 gal pair [SWB] |
| Indigo dottyback (*P. fridmani* x *sankeyi*) | ~7.6 (3 in) [LA-IDD] | 6-7 | Labriform, hover-dart | Rock ledges, low-mid | Diurnal | High | Single fish recommended [LA-IDD] |
| Skunk cleaner shrimp (*Lysmata amboinensis*) | 7-8 [GS-LYS] | 6-8 | Walks, swims backward | Coral heads, rock | Diurnal cleaner, nocturnal feeding (est.) | High | Not sourced; 1 per tank (est.) |
| Blue-leg hermit (*Clibanarius tricolor*) | ~2 [FP-CLIB] | 1.5-2 | Walks | Rubble, sand | Mostly nocturnal (est.) | High | Shells needed [est.] |
| Scarlet hermit (*Paguristes cadenati*) | ~3.5 [FP-PAG] | 3-3.5 | Walks | Rock, rubble | Diurnal/nocturnal (est.) | High | Spare shells needed [FP-PAG] |
| Black brittle star (*Ophiocoma* sp., likely *O. echinata*) | arm span to ~30 cm claimed [QM-OPH] | Disc 2-4 (est.), arms 10-30 | Arm-walking, sand/rock surfaces | Floor, rock tops, nocturnal | Nocturnal (est.) | High | Not sourced |
| Brown feather duster (*Sabellastarte indica* type) | Tube to 25 cm, crown 5-10 cm [TFH-SI] | Tube 15-25 | Sessile (tube-dwelling) | Floor, rock base, sand | Crown open day/night, retracts | Medium | Not sourced |
| Nassarius snail (*N. vibex*/*distortus*) | 1-2 [GS-NAS] | 1-2 | Burrows, crawls | Sand bed, buried | Nocturnal scavenger / feeds on food input | High | 2-3 cm sand bed [GS-NAS] |
| Astrea snail (*Lithopoma tectum*) | 2-6 [AB-AST] | 3-4 | Crawls | Rock, algae surfaces | Diurnal grazer (est.) | High | Not sourced |
| Asterina starfish (*Asterina*/*Aquilonastra*) | ~1.5-2 [RB-AST] | 1-1.5 | Walks on tube feet | Rock, coral surfaces | Mostly nocturnal (est.) | High | Not sourced; grows as colony |
| Organ pipe coral (*Tubipora musica*) | Colony to 30+ cm (est.); polyp 3 mm max [FK-OPC] | Colony 15-30 | Sessile | Rock/bottom, moderate flow | Diurnal | Medium | Not sourced |

**Notes on the 60 gal tank.** The owner's list now has three species with one-per-tank territoriality: lawnmower blenny, tailspot blenny, and indigo dottyback. These are the three blenny/dottyback slots that the sim must cap. The Asterina starfish is a population risk because it reproduces by fissiparity [RB-AST]. Add a population-cap rule.

---

## 1. Lawnmower blenny (*Salarias fasciatus*), x1

### 1A. Morphology

| Property | Value | Source |
|---|---|---|
| Max length | 12-14 cm | [LF-g] |
| Body shape | Elongate, torpedo-like, continuous long dorsal fin (per 02 notes) | [LF] (earlier file) |
| Males vs females | Males slightly larger, more pronounced head crest in breeding season [LF-g] | [LF-g] |
| Dorsal fin | Continuous, runs most of body length (est. from body shape) | est. |
| Anal fin | Spines: female 2 (one longer), male 2 equal | [LF] (earlier) |
| Pectoral fins | Broad (est.) | est. |
| Caudal fin | Rounded (est.) | est. |
| Mouth | Terminal, combtooth-like (est.) | est. |
| Eye | Placed high, large (est.) | est. |
| Scales | Absent or reduced (est.) | est. |

**Coloration:**
- Source: "mottled brown-green coloration" that camouflages against rock [LF-g].
- Some sellers describe iridescent blue/spots on the face and eyes [LF] (earlier file).
- Hex (est.): base brown-green #5E6B3F to #7A6A42; mottling #3B3A22 and #8C8A5B; iridescent face spots #3A8FD8 (sparse, 6-10 spots on head).
- Positions (est.): mottling across the flank and fins; spots concentrated on the head and around the eye.

### 1B. Locomotion

- **Swim mode:** Labriform, perching. Holds station against rock and hops between perches (est.).
- **Cruise speed:** ~0.5-1 BL/s (est.)
- **Burst speed:** ~3-5 BL/s, brief (est.)
- **Characteristic motions:** Rasps rock for algae most of the day [LF] (earlier file). Head-down grazing. Quick retreat into a hole when startled.
- **Sim note:** Its grazing loop should be continuous. Feed-off times are a stress input.

### 1C. Behavior and personality

- **Preferred zone:** Rock surface and holes, bottom to low-mid.
- **Home range:** One rock territory with a hole (est.).
- **Daily cycle:** Diurnal grazer [LF-g].
- **Hiding:** Holes and crevices; retreats to hole when observer or predator approaches (est.).
- **Territoriality:** Fiercely territorial toward own kind; one per tank unless system is over 400 L [LF-g]. Also fights other blennies, including tailspot and bicolor blennies [LF-g].
- **Feeding:** Grazer. Mainly algae and biofilm. Prepared foods: nori, spirulina wafers, marine algae pellets. Many individuals refuse prepared foods. Starvation is the leading cause of death in captivity [LF-g].
- **Feeding time:** Quick grab from rock, slower than tangs.
- **Human at glass:** Watches from rock, retreats into holes when observer approaches (est.).
- **Light change:** Lights-off: retreats to hole (est.).

### 1D. Interactions with other species in this list

| Other species | Interaction | Sim rule |
|---|---|---|
| Ocellaris clowns | Peaceful [LF-g] (est.) | None |
| Blue tang | Peaceful; competes for algae [LF] (earlier) | Low competition |
| Cardinal | Peaceful (est.) | None |
| Cleaner wrasse | Peaceful (est.) | None |
| Falco hawkfish | Hawkfish and blenny compete for rock perch (inference) | Moderate |
| Eel | Eel may prey on blenny in hole (inference) | Moderate-high |
| Tailspot blenny | Fight; territorial over rock [LF-g] | High: keep to one blenny of this type per zone |
| Indigo dottyback | Dottyback may chase blenny near rock (inference) | Moderate |
| Shrimp (skunk, banded) | Peaceful (est.) | None |
| Hermits/brittle star/snails | No interaction (est.) | None |

### 1E. Water and stress physiology

| Parameter | Optimal | Tolerable | Lethal / hard limit |
|---|---|---|---|
| Temperature | 24-28 C [LF-g] | 22-30 C | >30 C for extended periods [LF-g]; <20 C (est.) |
| Specific gravity | 1.020-1.025 (est.) | 1.019-1.026 | <1.018 |
| pH | 8.1-8.4 (est.) | 7.9-8.4 | <7.8 |
| Ammonia | 0 ppm | <0.1 ppm | >0.25 ppm |
| Nitrite | 0 ppm | <0.2 ppm | >0.5 ppm |
| Nitrate | <10 ppm (est.) | <20 ppm | >40 ppm |
| O2 | Saturated | >5 mg/L | <3 mg/L |

**Stress signs:** Color dulling, refusal to graze, hiding more, clamped fins, gasping, lethargy, thin belly (starvation) [LF-g].

**Timeline to death (sim):**
- Starvation: the main captive death cause [LF-g]. Estimate 3-8 weeks without algae or accepted prepared food (est.).
- Ammonia: 1-2 weeks at moderate levels (est.).

**Susceptibility:** Hardy when fed. Starvation-prone if it refuses prepared food.

### 1F. Lifespan, diet, dimorphism

- **Lifespan:** Not stated in gensou source. Earlier file cites 3-5 years [LF] (earlier file).
- **Diet:** Algae and biofilm. Nori, spirulina, algae pellets [LF-g].
- **Sexual dimorphism:** Male larger with a head crest in breeding season [LF-g].

---

## 2. Tailspot blenny (*Ecsenius stigmatura*), x1

### 2A. Morphology

| Property | Value | Source |
|---|---|---|
| Max length | ~6.4 cm (2.5 in) | [SWB] |
| Body shape | Elongate, compressed (est.) | est. |
| Depth:length | ~0.16-0.20 (est.) | est. |
| Dorsal fin | Long, continuous (est.) | est. |
| Anal fin | Long, low (est.) | est. |
| Pectoral fins | Broad (est.) | est. |
| Caudal fin | Rounded (est.) | est. |
| Mouth | Terminal, combtooth dentition (est.) | est. |
| Eye | Large, high (est.) | est. |
| Tail spot | Name implies a marking near caudal base; the sources reached do not describe it [SWB] | [SWB] |

**Coloration:**
- Source gives a list of possible colors only: blue, yellow, orange, green, black, white, and brown [SWB]. No body color, spot, or eye-line detail.
- Sim default (est., confirm from photo): body brown-olive #6B5232 with a tail spot #1A1A1A (small, black, caudal peduncle). Add a faint blue eye-line #2E6FD8 as an optional variant.

### 2B. Locomotion

- **Swim mode:** Labriform, perching. Hops between rock perches (est.).
- **Cruise speed:** ~0.5-1 BL/s (est.)
- **Burst speed:** ~3-5 BL/s (est.)
- **Characteristic motions:** Perches on rock, peeks from holes, grazes hair algae (est.). Jumps when startled [SWB].

### 2C. Behavior and personality

- **Preferred zone:** Rock, bottom to low-mid.
- **Home range:** One rock or hole (est.).
- **Daily cycle:** Diurnal.
- **Hiding:** Holes and crevices (est.).
- **Territoriality:** Territorial toward same species and related blennies: chasing, biting, color changes [SWB]. One per tank unless a bonded pair or a very large tank [SWB].
- **Feeding:** Herbivore, grazes hair algae and microalgae/biofilm. Without enough algae, needs spirulina flakes, nori, or herbivore pellets, in several small meals per day. Nipping at corals usually means hunger [SWB].
- **Feeding time:** Quick grab from rock.
- **Human at glass:** Peeks from rock, retreats into holes when observer approaches (est.).
- **Light change:** Lights-off: holes.
- **Jumping:** Known jumper. Needs a tight lid. Jumps when startled, stressed, chased, or during acclimation [SWB].

### 2D. Interactions with other species in this list

| Other species | Interaction | Sim rule |
|---|---|---|
| Clowns | Docile to other fish [SWB] | None |
| Blue tang | Docile; may be bothered by large-mouthed fish [SWB] | Low |
| Cardinal | Docile (est.) | None |
| Cleaner wrasse | Docile (est.) | None |
| Falco hawkfish | Hawkfish may perch-compete or chase (inference) | Moderate |
| Eel | Eel may prey on blenny in holes (inference) | Moderate-high |
| Lawnmower blenny | Territorial fight [SWB, LF-g] | High |
| Indigo dottyback | Dottyback may harass blenny at rock (inference) | Moderate |
| Shrimp | Peaceful (est.) | None |

### 2E. Water and stress physiology

| Parameter | Optimal | Tolerable | Lethal / hard limit |
|---|---|---|---|
| Temperature | 25.5-27.8 C (78-82 F) [SWB] | 24-29 C | >30 C (est.) |
| Specific gravity | 1.023-1.025 (est.) | 1.020-1.026 | <1.020 |
| pH | 8.1-8.4 (est.) | 7.9-8.4 | <7.8 |
| Ammonia | 0 ppm | <0.1 ppm | >0.25 ppm |
| Nitrite | 0 ppm | <0.2 ppm | >0.5 ppm |
| Nitrate | <10 ppm (est.) | <20 ppm | >40 ppm |
| O2 | Saturated | >5 mg/L | <3 mg/L |

**Stress signs:** Color dulling, hiding, refusing algae, coral nipping (hunger), clamped fins, jumping [SWB].

**Timeline to death (sim):** Jumping risk is the main sudden-loss mode (est.). Starvation: 3-6 weeks without algae (est.).

**Susceptibility:** Medium. Jumper, territorial, algae-dependent.

### 2F. Lifespan, diet, dimorphism

- **Lifespan:** 3-5 years [SWB].
- **Diet:** Herbivore. Hair algae, microalgae, spirulina, nori, pellets. Several small meals daily [SWB].
- **Sexual dimorphism:** Not stated (est.: none visible).

---

## 3. Indigo dottyback (*Pseudochromis fridmani* x *sankeyi*), x1

### 3A. Identification

- The retailer sold as "Indigo dottyback, captive-bred" [LA-IDD] is a hybrid: *P. fridmani* (orchid dottyback) x *P. sankeyi* (striped dottyback) [LA-IDD].
- Some retailers also list the ORA/captive-bred product as "Electric Indigo Dottyback" [TPP]. The ORA product page itself was not reached, so ORA's exact name for this hybrid was not verified.
- Note: The hybrid is sometimes described as a color morph of orchid dottyback at first glance [LA-IDD].

### 3B. Morphology

| Property | Value | Source |
|---|---|---|
| Max length | 3 in (~7.6 cm) [LA-IDD] | [LA-IDD] |
| Body shape | Compact, slightly elongate, typical dottyback (est.) | est. |
| Depth:length | ~0.30-0.35 (est.) | est. |
| Dorsal fin | Single, long, continuous (est.) | est. |
| Anal fin | Long (est.) | est. |
| Pectoral fins | Rounded (est.) | est. |
| Caudal fin | Rounded (est.) | est. |
| Mouth | Terminal, upturned (est.) | est. |
| Eye | Large (est.) | est. |

**Coloration:**
- Body: "soothing indigo"; color field on listing is "Black and Blue" [LA-IDD].
- Lateral stripe: dominant lateral stripe "greatly subdued" [LA-IDD].
- Belly: dark stripe running full length of belly (striped dottyback trait) [LA-IDD].
- Fins and face: not described [LA-IDD].
- Hex (est.): body indigo #2B2F7A to #3B3FA8; face and dorsal #2A3C9A; belly stripe #0E0E14; lateral stripe (faint) #5B63C8.
- Positions (est.): dark belly stripe on the lower body from the pectoral to the caudal peduncle, dorsal fin with a dark margin.

### 3C. Locomotion

- **Swim mode:** Labriform, hover and dart (est.).
- **Cruise speed:** ~0.5-1 BL/s (est.)
- **Burst speed:** ~3-5 BL/s (est.)
- **Characteristic motions:** Holds at rock ledges, quick darts to strike food or chase rivals (est.).

### 3D. Behavior and personality

- **Preferred zone:** Rock ledges, low-mid.
- **Home range:** One territory around a rock cave (est.).
- **Daily cycle:** Diurnal (est.).
- **Hiding:** Caves and crevices in rock (est.).
- **Temperament:** Semi-aggressive. Described as mild-mannered, minimal threat to crustaceans and ornamental shrimp [LA-IDD]. Housing singly recommended unless the aquarium is very large [LA-IDD]. Listed reef compatible [LA-IDD].
- **Feeding:** Carnivore. Frozen mysis, brine, small meaty foods; pellets accepted over time (est.).
- **Human at glass:** Watches from rock, mild curiosity (est.).
- **Light change:** Lights-off: rests in rock (est.).

### 3E. Interactions with other species in this list

| Other species | Interaction | Sim rule (est.) |
|---|---|---|
| Ocellaris clowns | Clowns are larger and more territorial near host; dottyback may be chased by clowns (est.) | Low |
| Blue tang | Tang too large; dottyback keeps distance (est.) | Low |
| Pajama cardinal | Dottyback may bully small cardinals in a school (est.; no source) | Low-moderate |
| Cleaner wrasse | Peaceful (est.) | None |
| Falco hawkfish | Two perch/rock-ledge predators compete (inference) | Moderate |
| Eel | Eel is larger; dottyback avoids eel den (est.) | Low |
| Lawnmower blenny | Dottyback may chase blenny from rock territory; no source (inference) | Moderate |
| Tailspot blenny | Same as lawnmower (inference) | Moderate |
| Skunk cleaner shrimp | Mild to shrimp; "minimal threat to ornamental shrimp" [LA-IDD] | None |
| Banded coral shrimp | Same as cleaner shrimp (est.) | None |
| Hermits, snails, brittle star | Peaceful (est.); may pick at small snails | None |

**Aggression question, direct answer:** The source says mild toward shrimp and crustaceans [LA-IDD]. The sources reached give no blenny data. The most-supported rule in the sim is: moderate aggression toward the two blennies (rock-ledge competition, same zone, inference), low toward the cardinals, and none toward shrimp.

### 3F. Water and stress physiology

| Parameter | Optimal | Tolerable | Lethal / hard limit |
|---|---|---|---|
| Temperature | 24-27 C (est.) | 22-29 C | <20 C or >30 C (est.) |
| Specific gravity | 1.023-1.025 (est.) | 1.021-1.026 | <1.020 |
| pH | 8.1-8.4 (est.) | 7.9-8.4 | <7.8 |
| Ammonia | 0 ppm | <0.1 ppm | >0.25 ppm |
| Nitrite | 0 ppm | <0.1 ppm | >0.25 ppm |
| Nitrate | <20 ppm (est.) | <30 ppm | >50 ppm |
| O2 | Saturated | >5 mg/L | <3 mg/L |

**Stress signs:** Color fading (indigo to dull), clamped fins, hiding, refusing food, gasping (est.).

**Timeline to death (sim):** Hardy. Ammonia or disease death 1-3 weeks at moderate stress (est.).

**Susceptibility:** Low-moderate. Dottyback family is generally hardy (est.).

### 3G. Lifespan, diet, dimorphism

- **Lifespan:** Not stated. Estimate 4-6 years (est.).
- **Diet:** Carnivore: frozen mysis, brine, meaty foods; pellets (est.).
- **Sexual dimorphism:** Not stated (est.: none visible).

---

## 4. Skunk cleaner shrimp (*Lysmata amboinensis*), x1

### 4A. Morphology

| Property | Value | Source |
|---|---|---|
| Max length | 7-8 cm body length [GS-LYS] | [GS-LYS] |
| Body shape | Slender, shrimp-like, with extended rostrum (est.) | est. |
| Depth:length | ~0.18-0.22 (est.) | est. |
| Antennae | Long and white, extend "well beyond the body" [GS-LYS] | [GS-LYS] |
| Legs | Walking legs with small chelae (est.) | est. |
| Tail | Fan of uropods (est.) | est. |
| Carapace | Smooth, not spiny (est.) | est. |

**Coloration:**
- Two red stripes along the back, each bordered by white [GS-LYS].
- Body base: translucent to pale (est.).
- Hex (est.): red stripes #C62B2B to #D13B2F; white borders #F4F4F0; base body #E9D8C6 translucent; antennae #F8F8F8.
- Positions (est.): dorsal red stripes run from rostrum to tail, one each side of midline; white borders outside them.

### 4B. Locomotion

- **Swim mode:** Walks on legs and swims backward with tail flips (est.). Rarely swims long distances.
- **Cruise speed:** ~0.1-0.2 BL/s walking (est.)
- **Burst speed:** Tail-flip escape, brief (est.)
- **Characteristic motions:** Stands on coral heads at stations with antennae waving; cleans clients; moves slowly across the rock face (est.).

### 4C. Behavior and personality

- **Preferred zone:** Coral heads and rock surfaces, low to mid.
- **Home range:** Station-based. Stays near one coral head or station (est.).
- **Daily cycle:** Diurnal cleaning. Feeding in low light (est.).
- **Hiding:** Retreats into rock or behind coral when disturbed (est.).
- **Cleaning:** Wild shrimp set up stations on coral heads. Fish visit to have parasites, dead skin, and mucus removed [GS-LYS]. Captive shrimp still clean. Newly added fish reportedly seek out the station within hours [GS-LYS]. Shrimp will clean a hand held still near the station [GS-LYS]. The source does not mention dance or posture displays.
- **Temperament:** Peaceful. Not described as aggressive [GS-LYS].
- **Feeding:** Opportunistic omnivore. Needs supplemental feeding (frozen mysis, brine, finely chopped seafood, pellets, flake). Will intercept food meant for fish. Once-daily supplement sufficient in a well-fed reef [GS-LYS].
- **Feeding time:** Slow walk, pick at food, shares food with fish.
- **Molting:** Every 3-6 weeks [GS-LYS]. Soft and vulnerable for 24-48 hours; hides. Often eats the discarded shell. Stable Ca and Mg help the new shell; iodine may help if molts are incomplete [GS-LYS].
- **Human at glass:** Retreats into rock (est.).
- **Light change:** Lights-off: moves onto rock, feeding continues (est.).

### 4D. Interactions with other species in this list

| Other species | Interaction | Sim rule (est.) |
|---|---|---|
| Ocellaris clowns | Clowns visit station (est.) | Low; mutual cleaning |
| Blue tang | Tang visits station; shrimp cleans tang (est.) | Medium visit frequency |
| Pajama cardinal | Peaceful (est.) | None |
| Cleaner wrasse | Overlapping clientele (see section 9) | Medium competition |
| Falco hawkfish | Hawkfish treats shrimp as food [GS-LYS, PFK-HAWK] | High risk |
| Eel | Eel predation on shrimp: not in GS-LYS. 02 notes that eels eat crabs and shrimp [WW] | High (inference) |
| Indigo dottyback | Mild to shrimp [LA-IDD] | None |
| Blenny | Peaceful (est.) | None |
| Banded coral shrimp | Two shrimp on rock; both cleaners (est.); banded shrimp may bully (est.) | Low-moderate |
| Hermits | Shrimp may be eaten by hermit when molting (est.) | Low |
| Brittle star | None (est.) | None |

### 4E. Water and stress physiology

| Parameter | Optimal | Tolerable | Lethal / hard limit |
|---|---|---|---|
| Temperature | 24-27 C (est.) | 22-28 C | >30 C (est.) |
| Specific gravity | 1.023-1.025 (est.) | 1.021-1.026 | <1.020 |
| pH | 8.1-8.4 (est.) | 7.9-8.4 | <7.8 |
| Calcium / magnesium | Stable (molt support) [GS-LYS] | Stable | Drop causes failed molt |
| Iodine | Supplement if molting fails [GS-LYS] | | |
| Copper | Intolerant (est.) | 0 | >0.1 ppm (est.) |
| Ammonia | 0 ppm | <0.1 ppm | >0.25 ppm |
| Nitrite | 0 ppm | <0.1 ppm | >0.25 ppm |
| Nitrate | <20 ppm (est.) | <30 ppm | >40 ppm |
| O2 | Saturated | >5 mg/L | <3 mg/L |

**Stress signs:** Failed molt (stuck shell), refusing to clean, staying hidden, color loss, limb loss (est.), tail-flip escape more often.

**Timeline to death (sim):** Failed molt death within days to weeks (est.). Ammonia or copper death within days at high levels (est.).

**Susceptibility:** Medium. Molt-dependent. Sensitive to copper.

### 4F. Lifespan, diet, dimorphism

- **Lifespan:** Not stated. Estimate 2-3 years (est.).
- **Diet:** Omnivore; frozen mysis, brine, pellets, flake; scraps from fish (est.).
- **Sexual dimorphism:** Not stated (est.: females carry eggs, larger).

---

## 5. Blue-leg hermit (*Clibanarius tricolor*), x1 (or several; assume 1-2 in the sim)

### 5A. Morphology

| Property | Value | Source |
|---|---|---|
| Max length | ~2 cm (0.79 in) [FP-CLIB] | [FP-CLIB] |
| Body shape | Soft abdomen in shell, hard cephalothorax; males larger (per [FP-CLIB]) | [FP-CLIB] |
| Legs | Blue legs with red banding between each segment [FP-CLIB] | [FP-CLIB] |
| Chelae | Smaller, one claw slightly larger (est.) | est. |
| Antennae | Short and banded (est.) | est. |
| Eye stalks | Blue (est.) | est. |

**Coloration:**
- Legs: blue, with red band at each segment [FP-CLIB].
- Body: soft abdomen, usually pale to orange (est.), hidden in shell.
- Hex (est.): leg blue #3A6FD0; leg red bands #C8282C; cephalothorax pale orange #E0A07A.
- Positions (est.): alternating blue/red bands on each leg segment.

### 5B. Locomotion

- **Swim mode:** Walks on legs. Rarely swims (est.).
- **Cruise speed:** ~0.1-0.2 BL/s (est.)
- **Burst speed:** Quick scurry when disturbed (est.)
- **Characteristic motions:** Sifts sand for food [FP-CLIB]. Shell fights: attacker flips the rival on its back, shakes until the victim is kicked out of the shell [FP-CLIB].

### 5C. Behavior and personality

- **Preferred zone:** Rubble and sand, shallow; gather in rocky areas with ~30 cm water [FP-CLIB] (wild).
- **Home range:** Stays with the same group day to day in the wild [FP-CLIB].
- **Daily cycle:** Mostly nocturnal (est.).
- **Hiding:** Shell (est.).
- **Shell use:** Needs spare shells. Shell fights and shell swaps (est.: does not switch voluntarily by default).
- **Feeding:** Detritivore and scavenger: dead plants and animals, green hair algae, cyanobacteria, seaweed, sifts sand [FP-CLIB].
- **Feeding time:** Crawls through rubble and sand.
- **Human at glass:** Withdraws into shell (est.).
- **Light change:** Lights-off: moves more (est.).

### 5D. Interactions

| Other species | Interaction | Sim rule (est.) |
|---|---|---|
| Clowns | Peaceful (est.) | None |
| Tang | Peaceful; tang may browse near it (est.) | None |
| Cardinal | Peaceful (est.) | None |
| Cleaner wrasse | Wrasse may pick at hermit (est.) | Low |
| Falco hawkfish | Hawkfish may pick at hermit; no source (inference) | Low-moderate |
| Eel | Eel is a crab/shrimp eater [WW] (02 note) | High |
| Skunk shrimp | Peaceful (est.); hermit may take shrimp that are molting (est.) | Low |
| Other hermit | Shell fights (see 5B) [FP-CLIB] | High, conspecific |
| Snails (Astrea, Nassarius) | Hermit may flip and eat snails (inference from Astrea source) [AB-AST] | Moderate |
| Brittle star | None (est.) | None |
| Feather duster | None (est.) | None |

### 5E. Water and stress physiology

| Parameter | Optimal | Tolerable | Lethal / hard limit |
|---|---|---|---|
| Temperature | 24-27 C (est.) | 22-28 C | >30 C (est.) |
| Specific gravity | 1.023-1.025 (est.) | 1.021-1.026 | <1.020 |
| pH | 8.1-8.4 (est.) | 7.9-8.4 | <7.8 |
| Copper | Intolerant (est.) | 0 | >0.1 ppm |
| Ammonia | 0 ppm | <0.1 ppm | >0.25 ppm |
| Nitrate | <20 ppm (est.) | <30 ppm | >50 ppm |

**Stress signs:** Stays out of shell, lethargic, limbs dull, no feeding (est.).

**Timeline to death (sim):** Hardy. Death within days at high ammonia or copper (est.).

**Susceptibility:** Low to medium. Copper-sensitive.

### 5F. Lifespan, diet, dimorphism

- **Lifespan:** Not stated. Estimate 1-3 years (est.).
- **Diet:** Detritus, algae, sand sifting [FP-CLIB].
- **Sexual dimorphism:** Males larger [FP-CLIB].

---

## 6. Scarlet hermit (*Paguristes cadenati*), x1

### 6A. Morphology

| Property | Value | Source |
|---|---|---|
| Size | ~3.5 cm, males and females [FP-PAG] | [FP-PAG] |
| Body shape | Hermit form; soft abdomen in shell (est.) | est. |
| Chelae | Large right claw (est.) | est. |
| Legs | Walking legs, red (est.) | est. |

**Coloration:**
- Bright red body, yellow head [FP-PAG].
- Hex (est.): body red #D32F2F to #C62828; head yellow #F2C230.
- Positions (est.): red carapace and legs; yellow head.

### 6B. Locomotion

- **Swim mode:** Walks. Rarely swims (est.).
- **Cruise speed:** ~0.1-0.2 BL/s (est.)
- **Burst speed:** Quick scurry (est.).
- **Characteristic motions:** Scavenges rock and rubble for algae and scraps (est.).

### 6C. Behavior and personality

- **Preferred zone:** Rock and rubble.
- **Home range:** Rock area (est.).
- **Daily cycle:** Diurnal and nocturnal (est.).
- **Hiding:** Shell (est.).
- **Shell use:** Needs extra shells so crabs can upsize as they grow. Not very aggressive about taking shells from snails or other hermits [FP-PAG].
- **Feeding:** Eats almost anything: leftover fish food, plankton foods, dead animals. Known for eating hair and slime algae [FP-PAG].
- **Temperament:** Hardy reef maintenance animal [FP-PAG].
- **Light change:** Diurnal; lights-off: active (est.).

### 6D. Interactions

| Other species | Interaction | Sim rule (est.) |
|---|---|---|
| Clowns | Peaceful (est.) | None |
| Tang | Peaceful (est.) | None |
| Cardinal | Peaceful (est.) | None |
| Cleaner wrasse | Possible pick (est.) | Low |
| Falco hawkfish | Hawkfish may prey on hermits [PFK-HAWK: "crabs might be safe, at your own risk"] | Moderate |
| Eel | Crab eater [WW] | High |
| Shrimp | Peaceful (est.) | None |
| Snails | May take snails when shell is needed (est.) | Moderate |
| Other hermit | Shell competition (est.) | Low |
| Brittle star | None (est.) | None |

### 6E. Water and stress physiology

| Parameter | Optimal | Tolerable | Lethal / hard limit |
|---|---|---|---|
| Temperature | 24-27 C (est.) | 22-28 C | >30 C (est.) |
| Specific gravity | 1.023-1.025 (est.) | 1.021-1.026 | <1.020 |
| pH | 8.1-8.4 (est.) | 7.9-8.4 | <7.8 |
| Copper | Intolerant [FP-PAG] | 0 | >0.1 ppm (est.) |
| Ammonia | 0 ppm | <0.1 ppm | >0.25 ppm |
| Nitrate | <20 ppm (est.) | <30 ppm | >50 ppm |

**Stress signs:** Stays in shell, lethargy, no feeding (est.).

**Timeline to death (sim):** Hardy. Copper death within days (est.).

**Susceptibility:** Low. Copper-sensitive [FP-PAG].

### 6F. Lifespan, diet, dimorphism

- **Lifespan:** Not stated (est. 2-4 years).
- **Diet:** Omnivore scavenger; hair and slime algae [FP-PAG].
- **Sexual dimorphism:** Males slightly larger (est.; same as blue-leg in [FP-CLIB]).

---

## 7. Black brittle star (*Ophiocoma* sp.)

### 7A. Identification

- Quality Marine lists *Ophiocoma echinata* as "Brittle Sea Star" [QM-OPH].
- Black-disc *Ophiocoma* species sold in the trade are not confirmed for this tank. Treat the species as open.

### 7B. Morphology

| Property | Value | Source |
|---|---|---|
| Max length (arm span) | "may grow to be 12 inches" (~30 cm) [QM-OPH] | [QM-OPH] |
| Disc | Central disc, 2-4 cm (est.) | est. |
| Arms | Long, slender, serpentine; 5 arms (est.) | [QM-OPH] (arms), est. |
| Spines | Short spines on arms (est.) | est. |
| Mouth | Central ventral disc with five jaws (est.) | est. |

**Coloration:**
- Often brown, reddish-brown, or greenish; some have distinct patterns and markings [QM-OPH].
- Black form: sim default black disc #1A1A1A, arms black with lighter bands #3B3B3B.

### 7C. Locomotion

- **Swim mode:** Arm-walking on rock, sand, and coral surfaces. Ophiuroids can swim with arm waves in some species (est.).
- **Cruise speed:** ~0.02-0.05 BL/s (est.; very slow)
- **Burst speed:** Arm-driven flick (est.).
- **Characteristic motions:** Creeping up rock; arms extended to catch food particles from water (est.).

### 7D. Behavior and personality

- **Preferred zone:** Rock tops, sand, coral bases.
- **Home range:** Small; moves slowly across rock (est.).
- **Daily cycle:** Nocturnal (est.).
- **Hiding:** Under rock, in crevices (est.).
- **Feeding:** Scavenger and detritivore [QM-OPH]. Eats organic debris, plankton, and small particles, moving food to mouth with arms [QM-OPH]. Accepts frozen and live food per product diet tags [QM-OPH].
- **Feeding time:** Arms extended to grab food from water; slow approach.
- **Human at glass:** Not reactive (est.).
- **Light change:** Diurnal activity low, nocturnal active (est.).

### 7E. Interactions

| Other species | Interaction | Sim rule (est.) |
|---|---|---|
| Clowns | None (est.) | None |
| Tang | None (est.) | None |
| Cardinal | None (est.) | None |
| Cleaner wrasse | Wrasse may pick at arms (est.) | Low |
| Falco hawkfish | Hawkfish may pick at arms (est.); not sourced | Low |
| Eel | Eel may eat a slow ophiuroid (est.) | Low-moderate |
| Shrimp | None (est.) | None |
| Hermits | None (est.) | None |
| Snails | None (est.) | None |

Sources do not state whether fish or shrimp eat or are eaten by brittle stars [QM-OPH].

### 7F. Water and stress physiology

| Parameter | Optimal | Tolerable | Lethal / hard limit |
|---|---|---|---|
| Temperature | 24-27 C (est.) | 22-28 C | >30 C (est.) |
| Specific gravity | 1.023-1.025 (est.) | 1.021-1.026 | <1.020 |
| pH | 8.1-8.4 (est.) | 7.9-8.4 | <7.8 |
| Copper | Intolerant [QM-OPH] | 0 | >0.1 ppm |
| Ammonia | 0 ppm | <0.1 ppm | >0.25 ppm |
| Nitrate | <20 ppm (est.) | <30 ppm | >50 ppm |

**Stress signs:** Arms curled or withdrawn, disc pale, arm loss (est.), detachment and regrowth.

**Timeline to death (sim):** Copper-related death within days (est.). Arm loss is common and regrows.

**Susceptibility:** Low-medium. Copper-sensitive [QM-OPH].

### 7G. Lifespan, diet, dimorphism

- **Lifespan:** Not stated (est. 2-4 years).
- **Diet:** Detritivore and scavenger [QM-OPH].
- **Sexual dimorphism:** None (est.).

---

## 8. Brown feather duster (*Sabellastarte* sp.; *S. indica* type)

### 8A. Morphology

| Property | Value | Source |
|---|---|---|
| Tube length | Up to 25 cm (10 in) [TFH-SI] | [TFH-SI] |
| Crown diameter | ~5-10 cm (2-4 in) [TFH-SI] | [TFH-SI] |
| Crown structure | Feathery radioles (filaments) with cilia | [TFH-SI] |
| Body | Worm inside leathery tube (est.) | est. |
| Eyes | Eye-spots along crown (est.) | est. |

**Coloration:**
- Crown: multicolored, usually mottled reddish-brown and white [TFH-SI].
- Hex (est.): reddish-brown #8B3A24 to #A4532D; white #F4F0E8.
- Positions (est.): mottled banding across the crown radioles; base pale.

### 8B. Locomotion

- **Swim mode:** Sessile. Tube-dwelling. Crown extends above tube for feeding (est.).
- **Crown retraction:** Crown retracts into tube when disturbed or when food is dispensed above it [TFH-SI].
- **Characteristic motions:** Crown extends and fans. Retracts on contact or feeding solution above the crown. Regrows over 1-2 months [TFH-SI].

### 8C. Behavior and personality

- **Preferred zone:** Bottom, rock base, sand. Place near bottom [TFH-SI].
- **Home range:** Fixed to its tube (est.).
- **Daily cycle:** Crown open most of the day; retracts when disturbed (est.).
- **Hiding:** Retracts into tube (crown withdrawn) [TFH-SI].
- **Feeding:** Filter feeder. Crown traps plankton and suspended particles; cilia move them to mouth. Feed fresh or preserved zooplankton and phytoplankton at least every other day, applied near and under the crown [TFH-SI]. Feeding above the crown causes retraction and waste [TFH-SI].
- **Feeding time:** Crown extension after food is dosed.
- **Light change:** Non-photosynthetic [TFH-SI]. Lights-off: crown may stay open (est.).
- **Human at glass:** Crown retracts when observer approaches (est.).

### 8D. Interactions

| Other species | Interaction | Sim rule (est.) |
|---|---|---|
| Clowns | None (est.) | None |
| Tang | Tang may nip crown (inference) | Low-moderate |
| Cardinal | None (est.) | None |
| Cleaner wrasse | Listed as unsuitable [TFH-SI]: wrasses are unsuitable tankmates | High (crown picking, est.) |
| Falco hawkfish | Hawkfish may pick at crown (est.) | Moderate |
| Eel | Eel may strike crown (est.) | Low-moderate |
| Shrimp | Listed as unsuitable [TFH-SI]: "most shrimp" unsuitable | Moderate (est.) |
| Hermits | None (est.) | None |
| Brittle star | None (est.) | None |

### 8E. Water and stress physiology

| Parameter | Optimal | Tolerable | Lethal / hard limit |
|---|---|---|---|
| Temperature | 22-28 C [TFH-SI] | 21-29 C | >30 C (est.) |
| Specific gravity | 1.024-1.026 [TFH-SI] | 1.023-1.027 | <1.020 |
| pH | 8.1-8.2 [TFH-SI] | 8.0-8.3 | <7.9 |
| Calcium | 380-430 ppm [TFH-SI] | 380-440 | <350 |
| Alkalinity | 2.5 meq/L [TFH-SI] | 2.3-2.8 | |
| Ammonia | 0 ppm | <0.1 ppm | >0.25 ppm |
| Nitrate | <20 ppm (est.) | <30 ppm | >50 ppm |

**Stress signs:** Crown shrinks, fails to extend, tube sits empty or floats, no regrowth (est.).

**Timeline to death (sim):** Crown lost due to disturbance or poor water; regrowth 1-2 months if healthy. A shrinking regrown crown means death may follow (wait, then check tube for movement) [TFH-SI].

**Susceptibility:** Medium. Crown loss from water quality or feeding [TFH-SI].

### 8F. Lifespan, diet, dimorphism

- **Lifespan:** Not stated (est. 3-5 years).
- **Diet:** Plankton and suspended particles; feed every other day [TFH-SI].
- **Sexual dimorphism:** None (est.).

---

## 9. Nassarius snail (*N. vibex* / *N. distortus*), x1-3

### 9A. Morphology

| Property | Value | Source |
|---|---|---|
| Size | 1-2 cm shell length [GS-NAS] | [GS-NAS] |
| Shell | Small, ovoid, with fine ribs (est.) | est. |
| Siphon | Small tube-like appendage, pokes above sand [GS-NAS] | [GS-NAS] |
| Foot | Muscular, for burrowing (est.) | est. |
| Eyes | Small (est.) | est. |

**Coloration:**
- Not described in source. Typical *Nassarius* shell: pale tan to gray with darker bands (est.).
- Hex (est.): shell #C9B79A to #8D7A62; bands #3D2F22.

### 9B. Locomotion

- **Swim mode:** Crawls on sand; burrows (est.).
- **Cruise speed:** ~0.01-0.05 BL/s (est.)
- **Burst speed:** Erupts from sand toward food (est.; source says they "race toward the source" [GS-NAS]).
- **Characteristic motions:** Buried; siphon above sand; erupts from sand on food [GS-NAS].

### 9C. Behavior and personality

- **Preferred zone:** Sand bed, buried 2-3 cm [GS-NAS].
- **Home range:** Sand bed (est.).
- **Daily cycle:** Mostly buried (est.). Active at food input (est.).
- **Hiding:** Buried in sand [GS-NAS].
- **Feeding:** Carnivorous scavenger: detritus, uneaten fish food, carrion; does not eat algae [GS-NAS]. Target-feed raw prawn 1-2 times per week if the tank is lightly stocked or heavily skimmed [GS-NAS].
- **Feeding time:** Erupts from sand and races toward food [GS-NAS].
- **Human at glass:** Buried; siphon visible (est.).
- **Light change:** Buried in lights-on; more active lights-off (est.).
- **Sand role:** Constant burrowing aerates sand and prevents anaerobic dead zones [GS-NAS].

### 9D. Interactions

| Other species | Interaction | Sim rule (est.) |
|---|---|---|
| Clowns | None [GS-NAS] | None |
| Tang | None [GS-NAS] | None |
| Cardinal | None [GS-NAS] | None |
| Cleaner wrasse | Some wrasses may prey on small individuals [GS-NAS] | Moderate |
| Falco hawkfish | Hawkfish may pick at snails (est.) | Low-moderate |
| Eel | Eel may eat snail (est.) | Low |
| Shrimp | None [GS-NAS] | None |
| Hermits | Hermits may prey on small individuals [GS-NAS] | Moderate |
| Brittle star | None (est.) | None |
| Feather duster | None (est.) | None |

### 9E. Water and stress physiology

| Parameter | Optimal | Tolerable | Lethal / hard limit |
|---|---|---|---|
| Temperature | 24-27 C (est.) | 22-28 C | >30 C (est.) |
| Specific gravity | 1.023-1.025 (est.) | 1.021-1.026 | <1.020 |
| pH | 8.1-8.4 (est.) | 7.9-8.4 | <7.8 |
| Copper | Sensitive [GS-NAS] | 0 | >0.1 ppm (est.) |
| Ammonia | 0 ppm | <0.1 ppm | >0.25 ppm |
| Nitrate | <20 ppm (est.) | <30 ppm | >50 ppm |
| Sand bed | >=2-3 cm needed [GS-NAS] | | <1 cm (est.) |

**Stress signs:** Stays surfaced in sand, does not erupt for food, siphon hangs out, lethargy (est.).

**Timeline to death (sim):** Starvation and copper death in 1-3 weeks (est.). Hardy when fed.

**Susceptibility:** Low when fed. Copper-sensitive [GS-NAS].

### 9F. Lifespan, diet, dimorphism

- **Lifespan:** Not stated (est. 1-2 years).
- **Diet:** Scavenger: detritus, leftover food, carrion [GS-NAS].
- **Sexual dimorphism:** None visible (est.).

---

## 10. Astrea snail (*Lithopoma tectum*, sold as *Astraea tecta*) x2-3

### 10A. Morphology

| Property | Value | Source |
|---|---|---|
| Size | Up to 6 cm; average 2-4 cm [AB-AST] | [AB-AST] |
| Shell | Thick, elevated, broadly conical with small spiral spikes giving a star outline [AB-AST] | [AB-AST] |
| Base | Nearly flat [AB-AST] | [AB-AST] |
| Operculum | Present (trapdoor) [AB-AST] | [AB-AST] |
| Foot | Muscular (est.) | est. |

**Coloration:**
- Reddish-orange main color, marked with white and olive areas [AB-AST].
- Hex (est.): shell #C4522B to #D9733E; white patches #F2EFE6; olive patches #6E6B2E.
- Positions (est.): reddish-orange dome; white and olive patches; spiral spikes pale.

### 10B. Locomotion

- **Swim mode:** Crawls on foot (est.).
- **Cruise speed:** ~0.01-0.03 BL/s (est.)
- **Burst speed:** Not applicable (est.).
- **Characteristic motions:** Grazes across rock and glass; flips over easily, struggles to right itself [AB-AST]. A reader disagrees and says starving snails are too weak to right themselves; well-fed snails recover in minutes to overnight [AB-AST].

### 10C. Behavior and personality

- **Preferred zone:** Rock, glass, algae surfaces.
- **Home range:** Small (est.).
- **Daily cycle:** Diurnal grazer (est.).
- **Hiding:** Retracts into shell and closes operculum (est.).
- **Feeding:** Herbivore: microscopic algae, cyanobacteria, diatoms, hair, brown, green, and filamentous algae. Also eats leftover shrimp or fish food. Supplement with herbivore pellets if algae is scarce [AB-AST].
- **Feeding time:** Grazes continuously (est.).
- **Human at glass:** Crawls on glass, retracts when touched (est.).
- **Light change:** Diurnal grazing (est.).

### 10D. Interactions

| Other species | Interaction | Sim rule (est.) |
|---|---|---|
| Clowns | None (est.) | None |
| Tang | Tang competes for algae; no predation (est.) | Low |
| Cardinal | None (est.) | None |
| Cleaner wrasse | None (est.) | None |
| Falco hawkfish | Not mentioned in AB-AST (est.) | Low |
| Eel | Not mentioned in AB-AST (est.) | Low |
| Blenny | Blennies graze same algae (est.) | Low |
| Hermits | Hermit crabs are the main predator [AB-AST]. Orange claw hermit should not share tank [AB-AST]. Upside-down snails are easy food [AB-AST] | High |
| Coral banded shrimp | Listed as potentially dangerous [AB-AST] | Moderate |
| Snails (bumble bee) | Adult bumble bee snails may attack small astrea when food is short [AB-AST] | Low (not present) |
| Asterina starfish | None (est.) | None |

### 10E. Water and stress physiology

| Parameter | Optimal | Tolerable | Lethal / hard limit |
|---|---|---|---|
| Temperature | 22-24 C [AB-AST] | 22-27 C [AB-AST] | >28 C (est.) |
| Specific gravity | 1.023-1.025 (est.) | 1.021-1.026 | <1.020 |
| pH | 8.1-8.4 (est.) | 7.9-8.4 | <7.8 |
| Ammonia | 0 ppm | <0.1 ppm | >0.25 ppm |
| Nitrate | <20 ppm (est.) | <30 ppm | >50 ppm |

**Stress signs:** Stuck upside down, shell not closed, not grazing, operculum closed longer than normal (est.).

**Timeline to death (sim):** Upside-down with no food, starvation in 1-3 weeks (est.). Predation by hermits: immediate when flipped (est.).

**Susceptibility:** Low when fed. Hermit predation is the main threat [AB-AST].

### 10F. Lifespan, diet, dimorphism

- **Lifespan:** Up to 5 years; 3-5 years in captivity [AB-AST].
- **Diet:** Herbivore; algae and food scraps [AB-AST].
- **Sexual dimorphism:** None (est.).

---

## 11. Asterina starfish (*Asterina* / *Aquilonastra* sp.), hitchhiker

### 11A. Morphology

| Property | Value | Source |
|---|---|---|
| Size | Typically 6-13 mm (1/4-1/2 in); max ~2 cm (3/4 in) [RB-AST] | [RB-AST] |
| Shape | Flat, five-armed, small (est.) | est. |
| Arms | Short, broad, pale with small spines (est.) | est. |
| Tube feet | Ventral, small (est.) | est. |

**Coloration:**
- Grayish [RB-AST].
- Hex (est.): body gray #7A7A74 to #A8A59A; arm margins #C8C2B2; underside cream #E6DFC8.
- Positions (est.): uniform gray dorsal; arm tips paler.

### 11B. Locomotion

- **Swim mode:** Walks on tube feet on rock and coral (est.).
- **Cruise speed:** ~0.02-0.05 BL/s (est.)
- **Burst speed:** Not applicable (est.).
- **Characteristic motions:** Slow crawl on rock, coral bases; sits on coral and rock surfaces (est.).

### 11C. Behavior and personality

- **Preferred zone:** Rock, coral surfaces, glass.
- **Home range:** Small; colony spreads (est.).
- **Daily cycle:** Mostly nocturnal (est.).
- **Hiding:** Under rock and coral (est.).
- **Reproduction:** Fissiparity; splits and each arm can regrow a new star [RB-AST]. Outbreaks can happen. Hand removal impractical [RB-AST].
- **Feeding:** Not stated in the source (algae and detritus are common hypotheses, est.).
- **Coral risk:** Not explicitly labeled reef safe. Most species coexist without harm, but some become coral eaters. Jagged white marks at coral bases may indicate polyp feeding; remove if so [RB-AST].
- **Predator:** Harlequin shrimp is the only named predator [RB-AST].
- **Light change:** Mostly nocturnal (est.).

### 11D. Interactions

| Other species | Interaction | Sim rule (est.) |
|---|---|---|
| Clowns | None (est.) | None |
| Tang | None (est.) | None |
| Cardinal | None (est.) | None |
| Cleaner wrasse | Not mentioned (est.) | Low |
| Falco hawkfish | Not mentioned (est.) | Low |
| Eel | Not mentioned (est.) | Low |
| Shrimp | Harlequin only predator named [RB-AST]; cleaner and banded shrimp not named | Low |
| Hermits | Not mentioned (est.) | Low |
| Brittle star | Not mentioned (est.) | None |
| Snails | None (est.) | None |
| Coral | Some species eat coral (see 11C) | Moderate (sim: coral-damage rule) |

### 11E. Water and stress physiology

| Parameter | Optimal | Tolerable | Lethal / hard limit |
|---|---|---|---|
| Temperature | 24-27 C (est.) | 22-28 C | >30 C (est.) |
| Specific gravity | 1.023-1.025 (est.) | 1.021-1.026 | <1.020 |
| pH | 8.1-8.4 (est.) | 7.9-8.4 | <7.8 |
| Ammonia | 0 ppm | <0.1 ppm | >0.25 ppm |
| Nitrate | <20 ppm (est.) | <30 ppm | >50 ppm |

**Stress signs:** Stuck on rock, not moving, arms curled (est.).

**Timeline to death (sim):** Hardy; often a population grows in good conditions (est.).

**Susceptibility:** High hardiness. Population is the issue, not health.

### 11F. Lifespan, diet, dimorphism

- **Lifespan:** Not stated (est. 1-2 years).
- **Diet:** Not stated in source. Algae and detritus (est.).
- **Sexual dimorphism:** None (est.).

---

## 12. Organ pipe coral (*Tubipora musica*)

### 12A. Morphology

| Property | Value | Source |
|---|---|---|
| Genus | Monotypic: only *T. musica* [FK-OPC] | [FK-OPC] |
| Skeleton | Dark red calcite tubes, vertical, linked by stolons [FK-OPC] | [FK-OPC] |
| Tube structure | Magnesium-rich calcite (est.) | [FK-OPC] |
| Polyp size | Up to 3 mm, retractable [FK-OPC] | [FK-OPC] |
| Tentacles | 8 feathery tentacles per polyp [FK-OPC] | [FK-OPC] |
| Colony size | Not stated; 30 cm+ in the wild (est.) | est. |

**Coloration:**
- Skeleton: dark red [FK-OPC].
- Polyp tissue: green or brown or cream (est.; zooxanthellate).
- Hex (est.): skeleton #7A1F1F to #9A2A2A; polyp tissue #B8C97A (green) to #C7A16A (brown); tentacle tips #E8E0C8.
- Positions (est.): red tubes visible between extended polyps; polyps cover the top of the colony.

### 12B. Locomotion

- **Swim mode:** Sessile (colony). Polyps extend and retract (est.).
- **Characteristic motions:** Polyps extend at feeding and light periods; retract when disturbed (est.).

### 12C. Behavior and personality

- **Preferred zone:** Rock or bottom; moderate flow [FK-OPC].
- **Daily cycle:** Polyps extend in light and at feeding (est.).
- **Hiding:** Polyps retract into tubes (est.).
- **Feeding:** Baby brine nauplii and rotifers; absorbs micro-particles (possibly benefits from plankton) [FK-OPC]. Zooxanthellae provide much energy (est.).
- **Growth:** Does not spread rapidly across rock like clove polyps [FK-OPC].
- **Temperament:** Not aggressive [FK-OPC].
- **Light change:** Diurnal extension (est.).

### 12D. Interactions

| Other species | Interaction | Sim rule (est.) |
|---|---|---|
| Clowns | None (est.) | None |
| Tang | Tang nipping polyps (inference) | Low-moderate |
| Cardinal | None (est.) | None |
| Cleaner wrasse | None (est.) | None |
| Falco hawkfish | None (est.) | None |
| Eel | Eel may strike polyps (est.) | Low |
| Shrimp | None (est.) | None |
| Bristleworms | Large bristleworms may prey on it [FK-OPC] | Moderate (sim: bristleworm rule) |
| Hermits | None (est.) | None |
| Brittle star | None (est.) | None |
| Asterina starfish | Some Asterina can eat coral (see 11C) | Low-moderate |

### 12E. Water and stress physiology

| Parameter | Optimal | Tolerable | Lethal / hard limit |
|---|---|---|---|
| Temperature | 25-28 C [FK-OPC] | 24-29 C | >30 C (est.) |
| Specific gravity | 1.024-1.026 [FK-OPC] | 1.023-1.027 | <1.020 |
| pH | 8.1-8.4 [FK-OPC] | 8.0-8.4 | <7.9 |
| Light | Bright [FK-OPC] | | |
| Flow | Moderate; not batter the skeleton [FK-OPC] | | |
| Ammonia | 0 ppm | <0.1 ppm | >0.25 ppm |
| Nitrate | <20 ppm (est.) | <30 ppm | >50 ppm |

**Stress signs:** Polyps stay retracted, tissue recedes, colony pales, tubes fill with debris or algae (est.).

**Timeline to death (sim):** Tissue loss over 1-3 weeks in poor light or flow (est.). Hard to revive once tubes dry.

**Susceptibility:** Medium. Light and flow dependent.

### 12F. Lifespan, diet, dimorphism

- **Lifespan:** Not stated (est. years to decades).
- **Diet:** Micro-plankton (baby brine, rotifers) plus zooxanthellae [FK-OPC].
- **Sexual dimorphism:** None (est.).

---

## 13. Cross-species answers (owner-requested)

### 13A. Dottyback aggression toward blennies, cardinal, and shrimp

- **Blennies (lawnmower, tailspot):** No source gave a blenny rule. Dottyback is semi-aggressive [LA-IDD]. Both blennies are rock-perch territorial and fight each other [LF-g, SWB]. Inference: dottyback is moderately aggressive toward blennies sharing a rock zone (est.). Sim: moderate chase rule within ~1 BL of the dottyback's ledge.
- **Pajama cardinal:** No source. Dottyback's mild temperament and semi-aggression [LA-IDD] suggest low risk (est.). Juvenile cardinals in a shared school are the weak point (est.).
- **Skunk cleaner shrimp:** Source says mild toward shrimp and crustaceans; minimal threat to ornamental shrimp [LA-IDD]. Sim: no aggression.
- **Summary:** Blennies moderate; cardinal low; shrimp none.

### 13B. Hawkfish or eel eating cleaner shrimp, hermits, or brittle star

- **Falco hawkfish vs skunk cleaner shrimp:** Hawkfish treat shrimp as food [GS-LYS]. The longnose hawkfish article says shrimp are "generally not safe," but some keepers report coexistence with cleaner shrimp; hawkfish may change its mind overnight [PFK-HAWK]. Sim: moderate-high risk to the skunk cleaner (est.: M-H).
- **Falco hawkfish vs hermits:** Crabs "might be safe... at your own risk" for larger, mobile invertebrates [PFK-HAWK]. Sim: moderate (est.).
- **Falco hawkfish vs brittle star:** Not sourced. Sim: low (est.).
- **Eel vs skunk cleaner shrimp:** Eels eat crabs and shrimp [WW, in 02]. Eel predation on skunk cleaner is not stated in GS-LYS. Sim: high (inference).
- **Eel vs hermits:** Eels eat crabs and shrimp [WW]. Hermits are crabs. Sim: high (inference).
- **Eel vs brittle star:** Not sourced. Sim: low (est.).

### 13C. Cleaner shrimp running a cleaning station alongside the cleaner wrasse

- **Shared behavior:** Both clean fish. Shrimp stations on coral heads; fish visit to have parasites, dead skin, and mucus removed [GS-LYS]. Wrasse cleaning station in 02 is a behavior with a visit-frequency rule [02 §4].
- **Competition:** No source documents competition between shrimp and wrasse over clients. Inference: two stations in one tank split the client pool. Clients that arrive hungry or with more parasites may choose the wrasse (est.).
- **Sim rule (est.):** Each cleaner has a station position. Client fish pick the nearest station with a visit probability weighted by the cleaner's own fitness. Wrasse starvation (02 §4E) reduces its visit rate. Shrimp that molts (3-6 wk [GS-LYS]) pauses cleaning for 24-48 h.
- **No aggression:** Shrimp and wrasse are not reported as attacking each other (est.). Wrasse may pick at shrimp antennae (est.).

---

## 14. Interaction matrix (new species, plus 02 species relevant to them)

Key: H = high risk, M = moderate, L = low, N = none, (i) = inferred, not sourced.

| | Lawnmower | Tailspot | Dottyback | Skunk shrimp | Blue hermit | Scarlet hermit | Brittle star | Feather duster | Nassarius | Astrea | Asterina | Organ pipe |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Lawnmower | - | H (own territory) | M (i) | N | N | N | N | N | N | N | N | N |
| Tailspot | H | - | M (i) | N | N | N | N | N | N | N | N | N |
| Dottyback | M (i) | M (i) | - | N | N | N | N | N | N | N | N | N |
| Skunk shrimp | N | N | N | - | L (i) | L (i) | N | M (i) | N | N | N | N |
| Blue hermit | N | N | N | L (i) | - | M (own shell) | N | N | M | M | N | N |
| Scarlet hermit | N | N | N | L (i) | M (own shell) | - | N | N | M | M | N | N |
| Brittle star | N | N | N | N | N | N | - | N | N | N | N | N |
| Feather duster | N | N | N | M (i) | N | N | N | - | N | N | N | N |
| Nassarius | N | N | N | N | M | M | N | N | - | N | N | N |
| Astrea | N | N | N | N | H | H | N | N | N | - | N | N |
| Asterina | N | N | N | N | N | N | N | N | N | N | - | M (coral eater) |
| Organ pipe | N | N | N | N | N | N | N | N | N | N | M | - |

**Predators present (02 + new):** Eel is still the main predator. Falco hawkfish is the second. New threats: hermits to astrea and nassarius [AB-AST, GS-NAS]; wrasse to nassarius and feather duster (i).

---

## 15. Tank-level sim notes (additional)

1. **One-per-tank slots:** Lawnmower blenny, tailspot blenny, indigo dottyback (one each, or a blenny pair only in very large tanks). Hermits: 2-3 total for 60 gal; add spare shells [FP-PAG].
2. **Blenny cap:** Two blennies in a 60 gal tank is above what the sources recommend for the lawnmower (one per tank unless 400 L+) [LF-g]. The sim should either cap at one blenny plus the tailspot as a second that tolerates one another (inference), or mark the second as chronically stressed (est.).
3. **Population cap:** Asterina starfish can multiply by splitting [RB-AST]. Cap at 2-4 or add a harlequin-shrimp predator (not part of the tank; add a removal rule).
4. **Feather duster:** Retracts on food dosed above it [TFH-SI]. Sim: feed-trigger retraction and regrowth 1-2 months.
5. **Nassarius:** Buried; siphon only. Erupts on food. Sand bed depth must be >=2-3 cm [GS-NAS]; sim should show them buried unless food is present.
6. **Molting:** Skunk cleaner (3-6 wk) [GS-LYS] and hermits (no source). Track molting state; soft shrimp hides 24-48 h.
7. **Calcium:** Feather duster needs 380-430 ppm Ca [TFH-SI]; organ pipe 25-28 C [FK-OPC]. Both are sensitive to calcium drop.
8. **Copper:** Intolerant: skunk shrimp, both hermits, brittle star, Nassarius, feather duster (indirectly, via Ca/Alk) [GS-LYS, FP-PAG, QM-OPH, GS-NAS]. Copper dosing should be a global kill switch in the sim.
9. **Food:** Nassarius, feather duster, and cleaner shrimp need specific feeds (raw prawn, plankton, mysis) [GS-NAS, TFH-SI, GS-LYS]. The sim feed schedule must include these.

---

## 16. Sources

Source tags used in this file (URLs):

- [LF-g] Gensou, Lawnmower Blenny care guide: https://gensou.sg/lawnmower-blenny-care-guide/ (fetched; content used)
- [LF] Light.fish, lawnmower blenny (from 02): https://light.fish/blog/lawnmower-blenny
- [SWB] Saltwater Aquarium Blog, Tailspot Blenny: https://www.saltwateraquariumblog.com/tailspot-blenny-reef-safe-care-guide/ (fetched; content used)
- [LA-IDD] LiveAquaria, Indigo Dottyback captive bred: https://www.liveaquaria.com/products/indigo-dottyback-captive-bred (fetched; content used)
- [TPP] That Pet Place, Electric Indigo Dottyback (Pseudochromis sankeyi x fridmani, ORA): https://www.thatpetplace.com/Pseudochromis-sankeyi-fridmani-indigo-dottyback-tankraised-266578 (search result only; not fetched)
- [GS-LYS] Gensou, Skunk Cleaner Shrimp: https://gensou.sg/skunk-cleaner-shrimp-care-guide-marine/ (fetched; content used)
- [FP-CLIB] Wikipedia, Clibanarius tricolor: https://en.wikipedia.org/wiki/Clibanarius_tricolor (fetched; content used)
- [FP-PAG] Fishprofiles, Paguristes cadenati: https://fishprofiles.com/profiles/crustaceans/Paguristes_cadenati/ (fetched; content used)
- [QM-OPH] Quality Marine, Brittle Sea Star (Ophiocoma echinata): https://www.qualitymarine.com/quality-marine/invertebrates/stars/ophiocoma/brittle-59007/ (fetched; content used)
- [TFH-SI] TFH Magazine, Sabellastarte indica: https://www.tfhmagazine.com/articles/saltwater/sabellastarte-indica (fetched; content used)
- [GS-NAS] Gensou, Nassarius Snail care: https://gensou.sg/nassarius-snail-marine-cleanup-care/ (fetched; content used)
- [AB-AST] Aquarium Breeder, Astrea Snail detailed guide: https://aquariumbreeder.com/astrea-snail-detailed-guide-care-diet-and-breeding/ (fetched; content used)
- [RB-AST] Reef Builders, Asterina starfish: https://reefbuilders.com/2016/03/30/asterina-starfish-an-introduction-to-this-common-reef-aquarium-c (fetched; content used)
- [FK-OPC] Fishkeeper, Organ Pipe Coral: https://www.fishkeeper.co.uk/fish/marine/soft-corals/organ-pipe-coral (fetched; content used)
- [PFK-HAWK] Practical Fishkeeping, Are hawkfish suitable for your reef tank: https://www.practicalfishkeeping.co.uk/features/are-hawkfish-suitable-for-your-reef-tank/ (fetched; content used)
- [WW] Wetwebmedia, snowflake moray (from 02): https://wetwebmedia.com/snowflakemoray.htm
- Search-only (not fetched, cited for context): https://reefs.com/invertebrates/coral-banded-shrimp/ (from 02 [RC]); https://www.reefs.com/fish/algae-blenny/ (search result for lawnmower, not used)

---

## 17. Gaps and open items

- **Tailspot coloration:** No source gives body color, tail spot, or blue/orange eye-lines [SWB]. The blue/orange eye-line blenny in 02 is still unconfirmed. Tailspot may be a different blenny (the owner's listed blenny with brown/purple body is not confirmed as tailspot). Needs photo ID.
- **Lawnmower lifespan and jumping:** Not stated in LF-g; 02 cites 3-5 years [LF] (earlier file).
- **Indigo dottyback ORA name:** Retailers call the hybrid "Electric Indigo" [TPP]. ORA product page was not reached.
- **Dottyback vs blenny:** No source. Moderate inference only.
- **Brittle star species:** Black-disc species not confirmed. Quality Marine lists *O. echinata* with an unverified 12 in claim [QM-OPH].
- **Brittle star predation:** No sources on fish, shrimp, or eel predation of brittle stars [QM-OPH].
- **Feather duster species:** Only *S. indica* sourced [TFH-SI]. Brown color match is a reasonable inference, not confirmed.
- **Feather duster crown retraction triggers:** Only feeding-solution-above-crown is sourced [TFH-SI]. Shadows and touch are not sourced; 02 sim model should treat them as est.
- **Nassarius lifespan and species:** Not stated; species limited to *N. vibex* / *N. distortus* [GS-NAS].
- **Asterina species and diet:** Only "Asterina" sourced; *Aquilonastra* not in RB-AST [RB-AST]. Diet not sourced.
- **Asterina and fish/shrimp predation:** Only harlequin shrimp named [RB-AST].
- **Cleaner shrimp vs wrasse station competition:** No source. Inference only.
- **Astrea flipping:** Two sources disagree (AB-AST author vs reader comment) [AB-AST].
- **Organ pipe polyp count and sweeper behavior:** Not given [FK-OPC].
- **Hermit shell-sharing:** Paguristes shell tendency sourced; Clibanarius shell-switching not sourced [FP-CLIB, FP-PAG].
- **Lifespans:** Most new species lack sourced lifespans. Estimates only (est.).
