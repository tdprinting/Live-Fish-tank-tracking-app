# Reef Tank Fish Species: Sim Reference (60 gal, per-fish AI)

Scope: the 8 species/groups in the user's 60 US gal (~227 L) reef, plus the hawkfish and blenny candidates. Each section uses the same structure A-F.

**Source legend.** Facts are cited to the sources listed at the end (short tags such as [FB] = FishBase, [TB] = Tang care guide, etc.). Values marked **(est.)** are my estimates or general aquarium/reef-fish knowledge that the sources I could reach did not state. Treat est. values as tunable parameters, not measurements.

**Sources not reached:** FishBase ocellaris page (fetch failed, so ocellaris fin counts come from a fishbase.se mirror), Animal-World ocellaris page (redirected away).

---

## 0. Cross-species quick reference

| Species | Max length (cm) | Typical aquarium adult (cm) | Swim mode | Zone | Active | Hardiness | Tank min (sourced) |
|---|---|---|---|---|---|---|---|
| Ocellaris clownfish x2 | 11.0 [FB] | 8-10 | Labriform (pectoral) | Mid, anemone/soft coral base | Diurnal | High | 20 gal (est.) |
| Palette/blue tang (juv.) | 31.0 [FB] | 25-30 adult [TB] | Labriform (pectoral) + caudal burst | Mid-upper, open lane | Diurnal | Medium (ich-prone) | 450 L adult (~119 gal), 600 L ideal [TB] |
| Pajama cardinal | 8.0 [FP] | 6-8 | Labriform, hovers | Mid-water, school | Crepuscular/nocturnal | High | ~40 gal for a school (est.) |
| Bluestreak cleaner wrasse | 11.0 [FB] | 9-11 | Labriform, pectoral + body undulation | Reef face, low-mid | Diurnal | Low-medium (starvation) | ~60 gal, needs clients (est.) |
| Snowflake moray | 80 (FP) / 81 (32 in) [WW] | 60-80 | Anguilliform | Floor, rock crevice | Nocturnal hunter | High but escape-prone | 60 gal minimum (WW); 130 gal (FP) |
| Falco hawkfish | 7.0 [PFK] | 6-7 | Pectoral-walking, hopping | Perch on rock/coral | Diurnal | Medium | ~30-40 gal (est.) |
| Flame hawkfish (alt.) | 8.9 (5 in) [SAB] | 8-9 | Pectoral-walking, jumps | Perch on coral heads | Diurnal | Medium | 40 gal min [SAB] |
| Lawnmower blenny (alt.) | 12.7 (5 in) [LF] | 10-12 | Labriform, perch | Rock surface/holes | Diurnal | High | 30 gal min [LF] |
| Banded coral shrimp | 6.3 (2.5 in) [RC] | 5-6 | Walks, cave-anchored | Rock cave, mouth open | Mostly nocturnal scavenger (est.) | High | Single pair, 1 shrimp per cave (est.) |

**Note on the 60 gal tank.** The palette tang is the main sizing conflict. Its adult sources give 450 L minimum, and a 60 gal tank is ~227 L. This is documented as the main cause of stunting and ich-related loss [TB]. The snowflake moray is also at or below the minimum sourced size (60 gal is the lowest sourced figure, and 130 gal is cited elsewhere). The sim should model these two as stress-prone for their long-term growth horizon.

---

## 1. Ocellaris clownfish (Amphiprion ocellaris), x2

### 1A. Morphology

| Property | Value | Source |
|---|---|---|
| Max length | 11.0 cm TL (male/unsexed) | [FB-mirror] |
| Body shape | Fusiform, compressed cross-section | [FB-mirror] |
| Depth:length | ~0.38-0.42 (est.) | est. |
| Dorsal fin | 10-11 spines, 13-17 soft rays; continuous, notched | [FB-mirror] |
| Anal fin | 2 spines, 11-13 soft rays | [FB-mirror] |
| Pectoral fins | Rounded, relatively large, used for swimming | est. |
| Caudal fin | Rounded/truncate | est. |
| Pelvic fins | Small, ventral, under pectorals | est. |
| Dorsal fin placement | Begins just behind the head, ~ at 20% body length from snout; spines span ~20-50% | est. |
| Anal fin placement | Begins under dorsal soft-ray region (~50-55% of standard length) | est. |
| Eye | Medium, ~ 1/8 head length, placed high | est. |
| Mouth | Small, terminal, slightly upturned | est. |
| Scales | Ctenoid; visible on flank; small | est. |

**Coloration (classic orange):**

| Element | Approx color (hex) | Placement (% of body length from snout) |
|---|---|---|
| Body | #F26B1D (orange) | - |
| White bars | #F5F5F5 (off-white) | Bar 1: ~20-25% (head, behind eye); Bar 2: ~50-55% (mid-body, through dorsal base); Bar 3: ~82-90% (caudal peduncle, into tail base) (est.) |
| Black outline on bars | #141414 | 1-2 mm margin around each white bar (est.) |
| Fins (dorsal/anal/caudal) | #F26B1D to #E0501A, black margins on dorsal and caudal | (est.) |
| Eye | #FFFFFF iris rim, black pupil | - |

**Coloration (dark morph, user's observed fish).** The user describes a black/dark brown body with orange face and fins and white bars. The best match among sourced morphs is the Darwin (A. sp. Darwin) black form, which seller text describes as "jet black with white stripes" [SAS]. Darwin fish are from North Australia, selectively bred for black pigmentation, and are captive-bred commonly now [SAS]. Other sourced morphs [TCMAS]:

| Morph | Body | Bars | Notes |
|---|---|---|---|
| Classic orange (wild) | Orange, #F26B1D | 3 white bars | Baseline |
| Darwin black | #0E0E0E to #1A1A1A | White bars, narrower | Pure line, from Darwin area |
| Midnight | Black | No bars | "Naked" gene; A. ocellaris and A. sp. Darwin both |
| Nearly naked | Orange/black | Reduced bars (domino, checkspot) | Partial naked gene |
| Snowflake | Orange, dark margins | Striping, dark margins that darken with age | Dominant gene |
| Fancy white / DaVinci | Orange | Added striping | Gene dose-dependent |
| Wyoming white | Orange | Heavy striping | Two doses DaVinci |
| Frostbite | Orange/grey | Patterned | Multi-gene |

The user's fish (dark body, orange face and fins, white bars) is most consistent with a Darwin/black-line fish or a "Midnight-type" with orange head retained. The sim should use: body #101010, face and fin #F26B1D, bars #F5F5F5, and treat the exact pattern as a per-fish variable.

### 1B. Locomotion

- **Swim mode:** Labriform (pectoral-fin dominant). Clownfish use their pectorals to hover and bob, with a small caudal beat for forward thrust.
- **Cruise speed:** ~1-2 body lengths/s (BL/s) (est.)
- **Burst speed:** ~4-6 BL/s, short (est.)
- **Tail beat frequency:** Low (est. 2-4 Hz at cruise).
- **Characteristic motions:** Waddling/bobbing near the anemone or soft coral. Quick darts back into the host. Fin fanning, fanning eggs in nest. The "waddle" is pectoral-driven, with the head tilted slightly down.
- **Sim note:** Clownfish mostly hover within ~1-3 body lengths of their host.

### 1C. Behavior and personality

- **Preferred zone:** Low-mid, close to host (anemone, Kenya tree, GSP, or substitute). Within the home range of ~1-3 BL.
- **Home range:** Small. Pairs stay together around one host (est.).
- **Daily cycle:** Diurnal. Sleep in or against host, typically at dusk into dark. Lights-on drives feeding and patrolling.
- **Hiding:** Retreat into host tentacles (anemone) or dense coral branches. Without host, the fish retreat into rock crevices (est.).
- **Schooling:** No. Pairs or small groups (est.).
- **Territoriality:** High toward other fish in the home range. Pair defends host; aggression toward tank mates that come near host. Larger female (dominant) controls the territory.
- **Social hierarchy:** Protandrous hermaphrodite. Sex change at ~4.4 cm TL [FB-mirror]. Largest individual becomes female; second largest becomes male. Pairs are monogamous. Males guard and aerate eggs [FB-mirror].
- **Hosting:** Accepts bubble tip (Entacmaea quadricolor) and others. Captive-bred fish do not need anemone to survive [SAS]. In sim: host-seeking attracts to any stationary soft-coral "anemone" stand-in (Kenya tree, GSP, powerhead-driven substitute).
- **Feeding:** Pellets, flake, frozen mysis, frozen brine shrimp [SAS]. Strikes at surface/mid-water food. Grazing on algae minimal.
- **Feeding time:** Quick rise to the surface, aggressive snatching of food.
- **Human at glass:** Mildly curious, often approaches the glass nearest the observer. Retreats when hand-level motion is fast.
- **Light change:** Lights-off: retreat to host, rest in tentacles. Lights-on: resume patrol. Actinic-only: partially active.

### 1D. Interactions with other species in this list

| Other species | Interaction | Sim rule (est.) |
|---|---|---|
| Other clownfish (pair) | Mated pair shares host; male is subordinate | Pair stays together |
| Blue tang | Tang is larger and may browse near clown territory; clown harasses tang only if it enters the host zone | Clown chases tang from within ~1 BL of host |
| Cardinal | Peaceful; both mid-water-ish but clown is lower | No aggression |
| Cleaner wrasse | Clowns may visit the cleaning station if parasites present; cleaners clean clowns | Clown visits station rarely |
| Hawkfish | Hawkfish may eat small fish but clowns are generally too big; a clown may be harassed when perching nearby | Low risk |
| Eel | Eel eats clowns (fish) if near rock; clown retreats to host | Eel is the main threat to clowns |
| Coral shrimp | No interaction | None |
| Blenny | Blenny is peaceful; both use rock | None |

### 1E. Water and stress physiology

| Parameter | Optimal | Tolerable | Lethal / hard limit |
|---|---|---|---|
| Temperature | 24-27 C (est.) | 22-29 C | <18 C or >30 C |
| Specific gravity | 1.023-1.025 (est.) | 1.021-1.026 | <1.018 or >1.028 |
| pH | 8.1-8.4 (est.) | 7.9-8.5 | <7.8 |
| Ammonia | 0 ppm | <0.1 ppm | >0.5 ppm |
| Nitrite | 0 ppm | <0.2 ppm | >0.5 ppm |
| Nitrate | <20 ppm | <40 ppm | >60 ppm |
| O2 | Saturated | >5 mg/L | <3 mg/L |

**Stress signs:** Color fading (orange to paler), clamped fins, heavy breathing, loss of appetite, staying in host, surface gasping (rare).

**Timeline to death (sim):** Ammonia exposure: 2-5 days to first signs, 1-2 weeks to death at moderate levels (est.). Ich: visible white spots in 3-7 days, death in 2-3 weeks if untreated (est.).

**Susceptibility:** Moderately hardy. Ich-prone in shock (est.).

### 1F. Lifespan, diet, dimorphism

- **Lifespan:** Typically 6-10 years in captivity (est.).
- **Diet:** Omnivore. Pellets, flake, frozen mysis, brine shrimp, algae (occasionally).
- **Sexual dimorphism:** Female larger than male. Males smaller, with sex change at ~4.4 cm TL.

---

## 2. Palette / blue tang (Paracanthurus hepatus), juvenile (~2-3 in, ~5-7.5 cm)

### 2A. Morphology

| Property | Value | Source |
|---|---|---|
| Max length | 31.0 cm TL (male/unsexed) | [FB] |
| Adult in aquarium | 25-30 cm | [TB] |
| Juvenile at 10 cm | Growing; juveniles ~15 cm at 2 years, ~20 cm at 4 years [TB] |
| Body shape | Short and deep, strongly compressed; laterally disc-shaped | [FB] |
| Depth:length | ~0.50-0.55 (est.; body depth ~ half of SL) | est. |
| Dorsal fin | 9 spines; 19-20 soft rays; long, continuous | [FB] |
| Anal fin | 3 spines; 18-19 soft rays | [FB] |
| Pectoral fins | Large, paddle-like | est. |
| Caudal fin | Lunate/forked, with a caudal spine (scalpel) at base on each side | est. |
| Dorsal placement | Dorsal fin starts just behind the eye/head, ~ at 15-20% body length from snout | est. |
| Mouth | Small, terminal, slightly protrusible | est. |
| Eye | Medium, close to dorsal profile | est. |
| Scales | Ctenoid, small, can be scraped; mucus coat is thin (ich-prone) | [TB] |

**Coloration (juvenile):**
- Yellow belly, with blue dorsal and upper body
- The yellow belly fades to uniform blue by ~10 cm [TB]
- Juvenile hex (est.): body upper #2B5FD9, belly #F4D01A

**Coloration (adult):**
- Royal blue body, approx #1F4FBF to #2A62D2
- Black palette-shaped mark on the flank, centered around 30-45% body length from snout (est.); hex #0A0A0A
- Yellow tail, approx #F2C500
- Fins blue, with black margins (est.)

### 2B. Locomotion

- **Swim mode:** Labriform (pectoral-fin dominant) for cruising; caudal thrust for bursts. Strong, long swims in open water.
- **Cruise speed:** ~1-2 BL/s (est.)
- **Burst speed:** ~5-8 BL/s (est.)
- **Tail beat frequency:** Low to moderate (est. 3-6 Hz).
- **Characteristic motions:** Constant darting and "surfing" in the current. Long, sweeping lap of the lane. Quick bursts when disturbed or when a rival enters the lane.
- **Sim note:** Needs a long lane. At 60 gal (~120 cm long), the adult will feel cramped, and the sim should show more turning and fewer straight swims.

### 2C. Behavior and personality

- **Preferred zone:** Mid to upper water column, open lanes, near the front glass (est.).
- **Home range:** Large. Juveniles territorial along rock, adults defend areas (est.).
- **Daily cycle:** Diurnal. Sleeps in crevices or wedged into rock at night (est.).
- **Hiding:** Retreat into rock or behind rock at night; in stress.
- **Schooling:** Solitary; shoals loosely in the wild (est.).
- **Territoriality:** Territorial toward conspecifics and other Paracanthurus. Other tangs can be kept if introduced as juveniles and tank is 600 L+ [TB].
- **Feeding:** Grazes on algae and nori; accepts pellets, frozen mysis, brine shrimp [TB]. Feeds 4-5 times daily [TB].
- **Feeding time:** Rises rapidly, competes aggressively.
- **Human at glass:** Curious, often approaches the glass front.
- **Light change:** Lights-on: active grazing. Lights-off: rests in rock. Actinic-only: less active.
- **Temperature stress:** Stops eating above 28 C [TB].

### 2D. Interactions with other species in this list

| Other species | Interaction | Sim rule (est.) |
|---|---|---|
| Clownfish | Peaceful with clowns; clown defends host only | No aggression from tang |
| Cleaner wrasse | Cleaner visits tang for cleaning; tang holds still and gets cleaned | High frequency of cleaning station visits |
| Cardinal | Peaceful; cardinal stays in mid-water school | None |
| Hawkfish | Hawkfish may chase tang if tang swims near perches (low risk) | Low |
| Eel | Eel may attack the tang at night if the tang is near rock; tang is too large for most eels | Low risk, tang avoids eel |
| Coral shrimp | Peaceful | None |
| Blenny | Peaceful | None |

### 2E. Water and stress physiology

| Parameter | Optimal | Tolerable | Lethal / hard limit |
|---|---|---|---|
| Temperature | 24-27 C [TB] | 23-28 C | >28 C (stops feeding) [TB]; <22 C |
| Specific gravity | 1.023-1.026 (1.025) [TB] | 1.021-1.027 | <1.020 or >1.028 |
| pH | 8.0-8.4, stay >8.0 [TB] | 7.9-8.4 | <8.0 causes disease [TB] |
| Ammonia | 0 ppm | <0.1 ppm | >0.25 ppm |
| Nitrite | 0 ppm | <0.1 ppm | >0.25 ppm |
| Nitrate | <20 ppm [TB] | <30 ppm | >40 ppm |
| Phosphate | <0.08 ppm [TB] | <0.1 ppm | >0.2 ppm |
| Calcium | 420-440 ppm [TB] | 400-450 ppm | |
| dKH | 8-9 [TB] | 7-10 | |
| O2 | Saturated | >5 mg/L | <3 mg/L |

**Stress signs:** Ich (white spots, rubbing), clamped fins, color loss (blue fades), hiding, refusal to feed, HLLE (lateral line lesions) (est.), heavy breathing, and gasping at surface.

**Timeline to death (sim):**
- Ich (Cryptocaryon): spots appear in 48 hours after stress [TB]; death in 2-3 weeks untreated (est.).
- Ammonia: 1-2 weeks at moderate levels (est.).
- Stress from undersized tank: stunting, chronic disease, shortened life [TB].

**Susceptibility:** Rated extremely high for ich [TB]. Thin mucus coat and high metabolism. Heat above 28 C triggers ich in 48 h [TB].

### 2F. Lifespan, diet, dimorphism

- **Lifespan:** 15-20 years in well-kept reefs [TB].
- **Diet:** Omnivore with algae bias [TB]. Nori daily, mysis, brine, pellets. Vitamin C (Selcon or Vibrance) every third feeding helps prevent HLLE [TB].
- **Sexual dimorphism:** None visible externally (est.).

---

## 3. Pajama cardinalfish (Sphaeramia nematoptera)

### 3A. Morphology

| Property | Value | Source |
|---|---|---|
| Max length | 8.0 cm (3.1 in), males and females | [FP] |
| Body shape | Elongate, compressed (est.); mouth upturned | [FP] |
| Depth:length | ~0.28-0.32 (est.) | est. |
| Dorsal fin | Two dorsal fins typical of cardinals (est.); first spiny | est. |
| Anal fin | Spiny, short (est.) | est. |
| Pectoral fins | Long, pointed; used for hovering (est.) | est. |
| Caudal fin | Concave, forked (tail concave per source) | [FP] |
| Mouth | Upturned (proportionally large in males) | [FP] |
| Eye | Large, sized for low-light | est. |
| Scales | Ctenoid, visible | est. |

**Coloration:**
- Base: yellow, white, and brown
- Spots: small, dark brown or black, dense on body; juveniles have smaller spots and lighter body [FP]
- Adults: smudged body pattern, duller colors [FP]
- Hex (est.): body #E9C24A (yellow) to #F3EBD6 (white band); spots #6B4422; brown #7A4E2C
- Positions (est.): spotting across entire flank, denser near the mid-body; yellow body with white bands near the head and tail.

### 3B. Locomotion

- **Swim mode:** Labriform, pectoral-driven hovering. Holds position in mid-water with minimal caudal motion.
- **Cruise speed:** ~0.5-1 BL/s (est.)
- **Burst speed:** ~3-5 BL/s, short (est.)
- **Tail beat frequency:** Low (est. 1-3 Hz).
- **Characteristic motions:** Hovers in place, slow drifts in schools. Darts into crevices when startled.
- **Larval speeds (only sourced values):** Sphaeramia larvae reach ~19 BL/s sustained at end of larval phase [FBR]. Not adult.

### 3C. Behavior and personality

- **Preferred zone:** Mid-water, above rockwork, within a school (est.).
- **Home range:** Small, centered on shelter.
- **Daily cycle:** Nocturnal-leaning. By day shelters under ledges or in crevices, with more activity in dark or dim light [FP]. Without predators, more active in day [FP].
- **Hiding:** Under ledges, in rock crevices, behind rock. Sleeps in clusters.
- **Schooling:** Social; groups of at least 5 recommended [FP]. Social hierarchy but not aggressive toward each other [FP].
- **Territoriality:** None significant.
- **Hosting/breeding:** Male mouthbrooder. Male carries eggs in mouth; eggs hatch in ~1 week [FP].
- **Feeding:** Carnivorous. Wild: small crustaceans at night. Captive: meaty frozen foods, live mysis and amphipods preferred [FP].
- **Feeding time:** Calm, slow mid-water feeding, may take food from the surface.
- **Human at glass:** Shy; retreats into rock when observer approaches.
- **Light change:** Lights-off: moves to cluster in rock. Lights-on: spreads into mid-water.

### 3D. Interactions

| Other species | Interaction | Sim rule (est.) |
|---|---|---|
| Ocellaris clowns | Peaceful | None |
| Blue tang | Peaceful; tang may pass through the school | None |
| Cleaner wrasse | Peaceful | None |
| Hawkfish | Hawkfish may prey on cardinals at perch; risk to adults is low, juveniles at risk | High risk to small cardinals |
| Eel | Eel preys on cardinals at night; cardinals sheltered in rock | High risk at night |
| Coral shrimp | Peaceful; shrimp will not prey on cardinal | None |
| Blenny | Peaceful | None |

### 3E. Water and stress physiology

| Parameter | Optimal | Tolerable | Lethal / hard limit |
|---|---|---|---|
| Temperature | 24-28 C [FP] | 22-29 C | <20 C or >30 C |
| Specific gravity | 1.022-1.026 [FP] | 1.020-1.027 | <1.018 |
| pH | 8.1-8.4 (est.) | 7.9-8.4 | <7.8 |
| Ammonia | 0 ppm | <0.1 ppm | >0.25 ppm |
| Nitrite | 0 ppm | <0.1 ppm | >0.25 ppm |
| Nitrate | <20 ppm (est.) | <30 ppm | >50 ppm |
| O2 | Saturated | >5 mg/L | <3 mg/L |

**Stress signs:** Hiding or refusal to school, color fading, clamped fins, gasping, poor feeding.

**Timeline to death (sim):** Hardy. Death from ammonia or disease after 1-3 weeks at moderate stress (est.).

**Susceptibility:** Low to moderate. Hardy, handles transport well [FP].

### 3F. Lifespan, diet, dimorphism

- **Lifespan:** Not stated in sources. Estimate 3-5 years in captivity (est.).
- **Diet:** Mysis, amphipods, brine, frozen meaty foods, occasional flake or pellet [FP].
- **Sexual dimorphism:** Males likely have larger mouths [FP]. Male carries eggs (mouthbrooder).

---

## 4. Bluestreak cleaner wrasse (Labroides dimidiatus)

### 4A. Morphology

| Property | Value | Source |
|---|---|---|
| Max length | 11.0 cm (both sexes) | [FB] |
| Body shape | Elongated, streamlined; tail convex | [FB] |
| Depth:length | ~0.18-0.22 (est.) | est. |
| Dorsal fin | Long, continuous, soft-rayed in wrasses (est.) | est. |
| Anal fin | Long, soft-rayed (est.) | est. |
| Pectoral fins | Small to medium, positioned mid-body (est.) | est. |
| Caudal fin | Convex (per source) | [FB] |
| Mouth | Small, terminal, protrusible; teeth fine (est.) | est. |
| Eye | Medium, close to dorsal profile | est. |

**Coloration:**
- Yellow, blue, and black horizontal striping [FB]
- Adult: black/dark body with a bright electric blue lateral stripe (est.), hex blue #2E7FE0, black #0F0F0F, yellow #F1C40F in the upper body
- Juvenile coloration not described in sources (est.): more contrasting yellow/black

### 4B. Locomotion

- **Swim mode:** Labriform (pectoral-driven), with body undulation for darting. Constant motion, "surfing" in current [FB-aquarium text].
- **Cruise speed:** ~1-2 BL/s (est.)
- **Burst speed:** ~5-8 BL/s (est.)
- **Tail beat frequency:** Moderate (est. 4-6 Hz).
- **Characteristic motions:** Hovering at cleaning station, quick darts. "Dance" with tail movements to greet visitors (per source [FB]: greeted with dance-like tail movements for unfamiliar visitor). Dips into crevices.

### 4C. Behavior and personality

- **Preferred zone:** Reef face, low-mid, close to cleaning stations.
- **Home range:** Adults: territory with cleaning station. Juveniles: territorial along reef walls [FB-aq].
- **Daily cycle:** Diurnal. Sleeps in crevices or caves; sleeps under a mucus cocoon [FB-aq].
- **Hiding:** Caves or tunnels; sand to bury in [FB-aq].
- **Schooling:** Adults form pairs to set up stations. Juveniles work individually [FB-aq].
- **Territoriality:** Territorial, especially juveniles. Stations are held by pairs, groups of juveniles, or female/male groups. A female becomes male if the dominant male disappears [FB].
- **Social:** All start as females. Largest juvenile becomes male when the male leaves or dies [FB-aq].
- **Feeding:** Parasites (crustacean ectoparasites), mucus, and dead tissue from reef fish [FB]. Accepts some prepared foods: flake, live/frozen brine. Feed three times daily [FB-aq].
- **Cleaning behavior:** Client fish come to cleaning stations. Wrasse greets visitors with dance-like tail movements. Cleaning intensity unrelated to client size or abundance [FB].
- **Feeding time:** Takes food but may refuse prepared foods. Starvation risk (see 4E).
- **Human at glass:** Curious; surfing close to glass.
- **Light change:** Diurnal. Lights-off: retreats to caves, sleeps under mucus cocoon.

### 4D. Interactions

| Other species | Interaction | Sim rule (est.) |
|---|---|---|
| Clownfish | Clowns visit station; cleaner cleans clowns (intermittent) | Frequent but short visits |
| Blue tang | Tang visits cleaning station; wrasse cleans tang | High visit frequency |
| Cardinal | Cardinal may visit station; cleaning brief | Low frequency |
| Hawkfish | Hawkfish may attack cleaner if close to perch (inference) | Moderate risk |
| Eel | Eel is a client of cleaners in nature, but a captive eel may eventually eat cleaner [CW-myth] | High risk; long-term predator tolerance may fail [CW-myth] |
| Coral shrimp | Banded shrimp may also clean per [AD]; may compete for client fish (inference) | Low |
| Blenny | Peaceful | None |

### 4E. Water and stress physiology

| Parameter | Optimal | Tolerable | Lethal / hard limit |
|---|---|---|---|
| Temperature | 22-26 C [FB-aq] | 21-27 C | >28 C |
| Specific gravity | 1.023-1.025 (est.) | 1.021-1.026 | <1.020 |
| pH | 8.1-8.4 (est.) | 7.9-8.4 | <7.8 |
| Ammonia | 0 ppm | <0.1 ppm | >0.25 ppm |
| Nitrite | 0 ppm | <0.1 ppm | >0.25 ppm |
| Nitrate | <10 ppm (est.) | <20 ppm | >40 ppm |
| O2 | Saturated | >5 mg/L | <3 mg/L |

**Stress signs:** Refusal to eat, thin body, loss of color, poor mucus coat, lethargy, hiding continuously. Cleaner wrasses often look thin before death [CW-myth].

**Timeline to death (sim):**
- Starvation: Most wrasses entering the trade die early [CW-myth]. A wrasse that will not accept substitutes starves; some that accept substitutes still waste away [CW-myth]. The source gives no timeframe. Estimate: 2-8 weeks in a home tank with no clients (est.).
- Cleaning alone cannot sustain a home-kept wrasse [CW-myth].

**Susceptibility:** Low-medium. Starvation is the main failure mode (not disease). Long-term predator tolerance risk with eels [CW-myth].

### 4F. Lifespan, diet, dimorphism

- **Lifespan:** Maximum reported age 4 years [FB]. Captive lifespan not listed (est. 2-4 years).
- **Diet:** Omnivore; parasites and mucus in wild, flake/brine/frozen in captivity. Feed 3x daily [FB-aq].
- **Sexual dimorphism:** Sex cannot be determined externally. Largest juvenile becomes male [FB-aq].

---

## 5. Snowflake moray eel (Echidna nebulosa)

### 5A. Morphology

| Property | Value | Source |
|---|---|---|
| Max length | 80 cm (both sexes) [FP]; 81 cm / 32 in recorded max [WW]; typically up to 2 ft (~60 cm) [WW] | |
| Body shape | Anguilliform, long, cylindrical; narrowed to head | est. |
| Depth:length | ~0.04-0.05 (est.) | est. |
| Dorsal fin | Continuous, runs from behind the head to the tail (est.) | est. |
| Anal fin | Continuous, runs from vent to tail (est.) | est. |
| Pectoral fins | Absent (morays have no pectoral or pelvic fins) (est.) | est. |
| Caudal fin | Convex tail, fused with dorsal/anal (per source) [FP] | [FP] |
| Mouth | Large, gape wide, many teeth, protrudes over head | est. |
| Eye | Small, sits high | est. |
| Scales | None (skin is smooth, mucus-covered) (est.) | est. |

**Coloration:**
- White, black, yellow, with small spots [FP]
- Hex (est.): white base #F2F2EE; black #111111 (irregular speckles/spots); yellow #E8C52E (blotches)
- Pattern: small spots, reticulated, spotted across entire body; spots in relatively even distribution. Head often darker.

### 5B. Locomotion

- **Swim mode:** Anguilliform (undulatory body traveling head-to-tail). No pectoral fins for lift; swims by undulation through water.
- **Cruise speed:** <1 BL/s (est.)
- **Burst speed:** ~2-3 BL/s, brief (est.)
- **Tail beat frequency:** N/A (undulation wavelength ~ body length, est.)
- **Characteristic motions:** Head-out ambush from rock hole. Slow undulation. Rapid strike at prey. Tail anchored in crevice. Nightly exploration and foraging [WW].

### 5C. Behavior and personality

- **Preferred zone:** Floor, rock crevices, caves. Head out of rock.
- **Home range:** Establishes a den and forages out from it (est.).
- **Daily cycle:** Nocturnal hunter with diurnal retreat. Source conflicts: "quite active during daylight hours" [WW] vs "generally hide by day and are more active at night" [WW]. Use: mostly hidden by day, active at night.
- **Hiding:** Dark crevices, caves, under rock. Needs stable cover [WW].
- **Schooling:** Solitary.
- **Territoriality:** Territorial toward other morays (est.). Occasional aggression toward fish tankmates documented [WW].
- **Feeding:** Carnivore. Eats crabs, shrimp, fish. Accepts frozen/defrosted meaty foods and fish chunks [FP, WW]. Do not hand-feed (bites are nasty) [WW].
- **Feeding time:** Strikes from den. Head out, quick lunge.
- **Human at glass:** Head-out, watches, mouth opening and closing (display).
- **Light change:** Lights-on: retreat into den. Lights-off: emerge and hunt.

### 5D. Interactions

| Other species | Interaction | Sim rule (est.) |
|---|---|---|
| Ocellaris clowns | Eel may eat clowns if near rock; clowns retreat to host | Moderate risk |
| Blue tang | Eel may attack tang at night; tang larger, avoids | Low risk |
| Cleaner wrasse | Eel may eat cleaner over time; predator tolerance may fail [CW-myth] | High long-term risk |
| Hawkfish | Hawkfish perches above eel den; may be attacked if too close | Low-moderate |
| Pajama cardinal | Eel eats cardinals at night [FP: "keep away from large predators"] | High risk at night |
| Banded coral shrimp | Eel eats crabs and shrimp [WW] | High risk; shrimp in cave near eel's den |
| Blenny | Eel may prey on blenny in hole | Moderate-high (inference) |

### 5E. Water and stress physiology

| Parameter | Optimal | Tolerable | Lethal / hard limit |
|---|---|---|---|
| Temperature | 22-26 C [FP] | 20-28 C | <18 C or >30 C (est.) |
| Specific gravity | A few thousandths below 1.025 [WW] | 1.021-1.026 | <1.020 or >1.027 (est.) |
| pH | 8.1-8.4 (est.) | 7.9-8.4 | <7.8 |
| Ammonia | 0 ppm | <0.1 ppm | >0.25 ppm |
| Nitrite | 0 ppm | <0.1 ppm | >0.25 ppm |
| Nitrate | <20 ppm (est.) | <40 ppm | >60 ppm |
| O2 | Saturated, redundant aeration [WW] | >5 mg/L | <3 mg/L |

**Stress signs:** Refusal to eat, staying in den, gaping, mucus loss, lethargy, lesions.

**Timeline to death (sim):** Hardy but sensitive to copper and malachite overdose or prolonged exposure [WW]. Escape risk is the biggest sim threat [WW].

**Susceptibility:** High hardiness. Escape-prone. Sensitive to copper/malachite [WW].

### 5F. Lifespan, diet, dimorphism

- **Lifespan:** Not stated in sources (est. 10+ years in captivity).
- **Diet:** Carnivore. Crabs, shrimp, fish, frozen meaty foods [FP, WW].
- **Sexual dimorphism:** None visible externally (est.).

---

## 6. Falco hawkfish (Cirrhitichthys falco)

### 6A. Morphology

| Property | Value | Source |
|---|---|---|
| Max length | 7.0 cm (2.8 in) | [PFK] |
| Body shape | Small, compact, slightly deep (est.) | est. |
| Depth:length | ~0.38-0.42 (est.) | est. |
| Dorsal fin | Spines with distinctive cirri (tufts), tips of spines carry tufts [PFK] | [PFK] |
| Anal fin | Short (est.) | est. |
| Pectoral fins | Large, with several free rays (typical hawkfish: 3 free rays on pectoral) | est. |
| Caudal fin | Rounded/truncate (est.) | est. |
| Mouth | Terminal, moderately large (est.) | est. |
| Eye | Large, high on head (est.) | est. |

**Coloration:**
- Cream to pale pink body [PFK]
- Covered with reddish-brown bars and spots [PFK]
- Hex (est.): body #F4E3D6 (cream) to #F0C8C0 (pale pink); bars #8B3A24 (reddish-brown); spots #7A2E1C
- Positions (est.): bars run vertically across body, roughly evenly spaced at 15-25% body length intervals; spots dense on upper body

### 6B. Locomotion

- **Swim mode:** Pectoral walking and hopping. Rarely swims continuously [PFK]. Perches on corals and rock, uses pectoral rays to hop between perches.
- **Cruise speed:** ~0.3-0.5 BL/s (est.)
- **Burst speed:** ~2-4 BL/s, short (est.)
- **Tail beat frequency:** Low (est.)
- **Characteristic motions:** Motionless perch on coral or rock, watching for prey "much like birds of prey" [PFK]. Hop between vantage points. Brief quick darts to strike prey.

### 6C. Behavior and personality

- **Preferred zone:** Bottom and low water; perches on coral heads, rubble, rock outcrops.
- **Home range:** Small; a few perches (est.).
- **Daily cycle:** Diurnal.
- **Hiding:** Perch under overhangs or in coral when startled (est.).
- **Territoriality:** Semi-aggressive; can bully small or later-added tank mates [PFK].
- **Feeding:** Carnivorous. Wild: small crustaceans and zooplankton. Captive: frozen and live mysis, krill, brine shrimp, 1-2 times daily [PFK]. Pellets accepted over time [PFK].
- **Feeding time:** Strikes at food from perch, quick snaps.
- **Human at glass:** Watches observer from perch; less reactive than cardinals.
- **Light change:** Diurnal. Lights-off: rests on perch or retreats under rock.

### 6D. Interactions

| Other species | Interaction | Sim rule (est.) |
|---|---|---|
| Ocellaris clowns | Peaceful; may perch near host | Low |
| Blue tang | Tang may be chased if near perch; tang large | Low |
| Cleaner wrasse | Hawkfish may chase cleaner near perch | Moderate |
| Pajama cardinal | Hawkfish eats small cardinals [PFK: "predatory toward small shrimp and fish that fit in its mouth"] | High risk to juveniles |
| Eel | Eel may attack hawkfish; hawkfish avoids eel perch | Low-moderate |
| Banded coral shrimp | Hawkfish eats small shrimp [PFK]; banded shrimp larger, typically safe | Moderate |
| Blenny | Hawkfish and blenny may compete for rock perch; blenny peeks from holes | Moderate (inference) |

### 6E. Water and stress physiology

| Parameter | Optimal | Tolerable | Lethal / hard limit |
|---|---|---|---|
| Temperature | 24-26 C [PFK] | 23-27 C | >28 C; <22 C |
| Specific gravity | 1.023-1.025 [PFK] | 1.021-1.026 | <1.020 |
| pH | 8.1-8.4 [PFK] | 7.9-8.4 | <7.8 |
| Ammonia | 0 (undetectable) [PFK] | <0.1 ppm | >0.25 ppm |
| Nitrite | 0 (undetectable) [PFK] | <0.1 ppm | >0.25 ppm |
| Nitrate | <20 ppm (est.) | <30 ppm | >50 ppm |
| O2 | Saturated | >5 mg/L | <3 mg/L |

**Stress signs:** Clamped fins, color loss (pink fades), hiding, refusal to feed, perching lower/unmoving, gasping.

**Timeline to death (sim):** Moderate hardiness. Ammonia spike: death in 1-2 weeks (est.).

**Susceptibility:** Medium. Hawkfish can be picky and refuse food [SAB, similar for falco].

### 6F. Lifespan, diet, dimorphism

- **Lifespan:** Not stated (est. 3-5 years).
- **Diet:** Carnivore: mysis, krill, brine, pellets [PFK].
- **Sexual dimorphism:** Not stated.

---

## 6b. Flame hawkfish (alternate, Neocirrhites armatus) (brief)

| Property | Value | Source |
|---|---|---|
| Adult size | 8.9 cm (5 in) | [SAB] |
| Coloration | Bright red-orange scales, black stripe down back, black ring around eyes | [SAB] |
| Locomotion | No swim bladder; walks on pectoral fins on rock/coral; jumps when startled (often upward) | [SAB] |
| Diet | Crustacean flesh (with shell), fish flesh, mysis; vitamin/omega-3 for color; may be picky | [SAB] |
| Temperament | Territorial; chases fish near bottom; relatively peaceful with mid/upper-level fish | [SAB] |
| Tank | 40 gal minimum; 80 gal for breeding; horizontal space; tight lid | [SAB] |
| Water values | Not given in source (use falco values) | |

The user's description (red/brown-and-white barred) fits falco better than flame: falco is cream/pale pink with reddish-brown bars [PFK]. Flame is solid red-orange with a black back stripe [SAB]. Use falco as the default.

---

## 7. Blenny: brown/purple body, blue and orange lines near eye

### 7A. Candidate identification

The sources I could reach did not describe blue and orange lines near the eye for any Ecsenius species. The candidates below are by name and size only. Confirm from a photo before modeling.

| Candidate | Max size (cm) | Coloration per source | Notes |
|---|---|---|---|
| Ecsenius bicolor (Bicolor blenny) | ~10 (4 in) [WW] | Orange and purple [WW] | Strong match to the body colors. Line pattern not confirmed. |
| Ecsenius midas (Midas/Persian blenny) | ~12.7 (5 in) [WW] | Yellow, purple, brown, pink, blue; changes rapidly under stress [WW] | Strong blue/purple match. Stays away from cover (not a rock-hole peeker) [WW]. |
| Ecsenius bathi (Bath's blenny) | ~3.8 (1.5 in) [WW] | Striped and orange-striped forms [WW] | Small. Associated with sponges and tunicates [WW]. |
| Ecsenius lineatus (Linear blenny) | ~9 (3.5 in) [WW] | Name suggests lines; color not stated [WW] | Name fits "lines". Check for blue/orange lines. |
| Ecsenius stictus (Great Barrier Reef blenny) | ~5.8 [WW] | Not stated | Australian endemic. |
| Ecsenius stigmatura (Tailspot coralblenny) | ~6 [WW] | Not stated | Tail-spot marker. |
| Ecsenius trilineatus (Three-lined blenny) | ~3 [WW] | Name suggests lines | Small. |
| Salarias fasciatus (Lawnmower blenny, alt.) | ~12.7 [LF] | Tan with iridescent spots on face and eyes, banded body [LF] | Not a clean match for brown/purple. Iridescent face spots could be the blue; not confirmed. |

**Recommendation:** Default to Ecsenius bicolor (brown/purple) with blue and orange eye-line overlays for the sim. Keep E. midas as an alternate if color shifts under stress are wanted.

### 7B. Morphology (generic Ecsenius, est.; lawnmower values from LF)

| Property | Value | Source |
|---|---|---|
| Body shape | Elongate, slightly compressed (est.). Lawnmower: long, torpedo-like, continuous dorsal fin the length of the body | [LF] |
| Depth:length | ~0.14-0.18 (est.) | est. |
| Dorsal fin | Continuous, long (est.); typical blennies have a single long dorsal | est./LF |
| Anal fin | Long, low (est.) | est. |
| Pectoral fins | Broad, fan-shaped (est.) | est. |
| Caudal fin | Rounded (est.) | est. |
| Mouth | Terminal, combtooth dentition (Ecsenius = combtooth) (est.) | est. |
| Eye | Large, placed high (est.) | est. |
| Scales | Absent or reduced in blennies (est.) | est. |
| Swim bladder | Small or absent (most blennies) [SQ] | [SQ] |

**Coloration (user-described, sim default):**
- Body brown to purple: #5A3A5E (purple-brown) to #6B4A3A (brown)
- Blue longitudinal line near eye: #2E6FD8
- Orange longitudinal line near eye: #F26B1D
- Positions (est.): lines run horizontally through the eye area at ~10-15% body length from snout, one above the other.

### 7C. Locomotion

- **Swim mode:** Labriform, perch-based. Rests on rock, hops between perches (est.).
- **Cruise speed:** ~0.5-1 BL/s (est.)
- **Burst speed:** ~3-5 BL/s, brief (est.)
- **Tail beat frequency:** Low (est.)
- **Characteristic motions:** Perches on top of rock, peeks from holes, quick retreat into holes (est.). Lawnmower: rasps rock most of the day [LF].

### 7D. Behavior and personality

- **Preferred zone:** Rock surface and holes, bottom to low-mid.
- **Home range:** Small; one rock hole or perch (est.).
- **Daily cycle:** Diurnal.
- **Hiding:** Holes, crevices, barnacle shells, mollusk tubes [SQ].
- **Schooling:** Solitary (est.).
- **Territoriality:** Aggressive toward its own species [LF, SQ]. One per tank.
- **Feeding:** Herbivore/grazer (algae), accepts flake, frozen, live foods [SQ]. Lawnmower: near-constant grazing [LF].
- **Feeding time:** Quick grab from rock.
- **Human at glass:** Watches, peeks; retreats into holes when observer approaches (est.).
- **Light change:** Lights-off: retreats into hole.

### 7E. Interactions

| Other species | Interaction | Sim rule (est.) |
|---|---|---|
| Clowns | Peaceful | None |
| Tang | Peaceful; competes for algae | None |
| Cardinal | Peaceful | None |
| Cleaner wrasse | Peaceful | None |
| Hawkfish | Hawkfish and blenny compete for rock perch; hawkfish may chase blenny (inference) | Moderate |
| Eel | Eel may prey on blenny in holes (inference) | Moderate-high |
| Coral shrimp | Peaceful | None |

### 7F. Water and stress physiology

| Parameter | Optimal | Tolerable | Lethal / hard limit |
|---|---|---|---|
| Temperature | 22-26 C (LF: 72-78 F) | 21-27 C | >28 C |
| Specific gravity | 1.020-1.025 [SQ, LF] | 1.019-1.026 | <1.018 |
| pH | 8.1-8.4 [LF] | 7.9-8.4 | <7.8 |
| Ammonia | 0 ppm [LF] | <0.1 ppm | >0.25 ppm |
| Nitrite | <0.2 ppm [LF] | <0.2 ppm | >0.5 ppm |
| Nitrate | <10 ppm [LF] | <20 ppm | >40 ppm |
| dKH | 8-12 [LF] | 7-12 | |
| O2 | Saturated | >5 mg/L | <3 mg/L |

**Stress signs:** Color dulling, hiding, refusal to graze, clamped fins, gasping, jumping (blennies can jump [SQ]).

**Timeline to death (sim):** Hardy; ammonia death 1-2 weeks (est.).

**Susceptibility:** Medium. Jumpers; lid needed [SQ].

### 7G. Lifespan, diet, dimorphism

- **Lifespan:** Lawnmower 3-5 years [LF]. Ecsenius: not stated (est. 3-5).
- **Diet:** Herbivore/grazer; flake, frozen, pellets [SQ, LF].
- **Sexual dimorphism:** Lawnmower: anal fin spines. Female has 2 spines, one longer; male both equal [LF].

---

## 8. Banded coral shrimp (Stenopus hispidus)

### 8A. Morphology

| Property | Value | Source |
|---|---|---|
| Max length | 6.3 cm (2.5 in); rarely 7.6 cm (3 in) (antennae to 15 cm) | [RC] |
| Body shape | Stout, prickly carapace (spines on body) | [RC] |
| Depth:length | ~0.20-0.25 (est.) | est. |
| Rostrum | Small, with spines (est.) | est. |
| Antennae | Extra long, white; antennae to ~15 cm total (6 in with antennae in source) | [RC] |
| Walking legs | Third pair: large chelae (pincers), held erect often | [RC] |
| Tail | Fan of uropods (est.) | est. |
| Carapace | Spiny, prickly texture | [RC] |

**Coloration:**
- Red and white bands across the body
- Red: #C8281E to #D83A2C
- White: #F4F2EE
- Positions (est.): alternating red and white bands across the carapace and abdomen; 3-4 white bands (est.); long white antennae.

### 8B. Locomotion

- **Swim mode:** Walks on legs; rarely swims. Anchored in cave or crevice.
- **Cruise speed:** ~0.1-0.2 BL/s walking (est.)
- **Burst speed:** Quick tail-flip escape, short bursts (est.)
- **Tail beat frequency:** N/A.
- **Characteristic motions:** Hangs upside-down with only antennae showing from a hole [RC]. Antennae waving (antenna wave). Scampers around searching for food [RC]. Rarely moves >0.5 m unless disturbed [AD].

### 8C. Behavior and personality

- **Preferred zone:** Bottom and rock caves, depths 2-210 m, most at 2-4 m [AD].
- **Home range:** Rarely moves more than 0.5 m [AD]. Cave-based.
- **Daily cycle:** Source says the species is active and scampers for food [RC]. Nocturnal activity not described for adults. Eggs hatch at night [AD] (est. for adult activity: crepuscular/nocturnal scavenger).
- **Hiding:** Hangs in caves or crevices [RC]. Perches at cave mouth [AD].
- **Schooling:** Solitary or mated pair [RC].
- **Territoriality:** Aggressive toward other banded coral shrimp and smaller shrimp of other species. Keep alone or as a mated pair [RC].
- **Cleaning:** AD says it cleans reef fish (morays, tangs, grunts, groupers) and waves antennae to attract fish [AD]. RC does not mention cleaning. Treat cleaning as a sim feature with moderate confidence.
- **Feeding:** Scavenger in wild; accepts most flake and frozen [RC]. Filter-feeding listed in source [AD].
- **Feeding time:** Slow walk, pick at food.
- **Molting:** Adults molt every 3-8 weeks depending on diet and temperature [AD]. Hides for 1-2 days in reef rock while molting [RC]. Adequate iodine helps molting [RC].
- **Human at glass:** Retreats into cave.
- **Light change:** Lights-off: exits cave to scavenge (est.); lights-on: retreats to cave.

### 8D. Interactions

| Other species | Interaction | Sim rule (est.) |
|---|---|---|
| Clowns | Peaceful | None |
| Tang | Peaceful | None |
| Cardinal | Peaceful; banded shrimp may eat very small cardinals (est.) | Low |
| Cleaner wrasse | Competes for cleaning clientele (per AD cleaning) | Low |
| Hawkfish | Hawkfish eats small shrimp [PFK]; banded shrimp is large | Low-moderate |
| Eel | Eel eats crabs and shrimp [WW]; shrimp near eel den is at risk | High |
| Blenny | Peaceful | None |

### 8E. Water and stress physiology

| Parameter | Optimal | Tolerable | Lethal / hard limit |
|---|---|---|---|
| Temperature | 72-78 F (22-26 C) [RC] | 21-27 C | >28 C |
| Specific gravity | 1.023-1.025 [RC] | 1.021-1.026 | <1.020 |
| pH | 8.1-8.4 [RC] | 7.9-8.4 | <7.8 |
| dKH | 8-12 [RC] | 7-12 | |
| Calcium, magnesium | Supplement [RC] | | |
| Iodine | Supplement for molting [RC] | | |
| Nitrate | Low; intolerant of high nitrate [RC] | <20 ppm | >40 ppm |
| Copper | Intolerant [RC] | 0 | >0.1 ppm |
| Ammonia | 0 ppm | <0.1 ppm | >0.25 ppm |
| Nitrite | 0 ppm | <0.1 ppm | >0.25 ppm |

**Stress signs:** Failed molt (stuck exoskeleton), loss of color, refusal to leave cave, limb autotomy (est.), antenna loss.

**Timeline to death (sim):** Hardy but molting failure is common under poor conditions (est.). Death from high nitrate or copper within days to weeks (est.).

**Susceptibility:** Medium. Intolerant of high nitrate and copper [RC]. Acclimate slowly to avoid salinity or pH shock [RC].

### 8F. Lifespan, diet, dimorphism

- **Lifespan:** Not stated in sources. Estimate several years in captivity (est.).
- **Diet:** Scavenger in wild [RC]; flake, frozen, and meaty foods in captivity [RC]; filter feeds (AD).
- **Sexual dimorphism:** Males usually smaller than females [RC]. Pair is monogamous [AD]. Eggs carried on swimmerets under female abdomen [AD]. Eggs hatch in ~16 days at 28 C [AD].

---

## 9. Interaction matrix (all species)

Key: H = high risk, M = moderate, L = low, N = none, (i) = inferred from ecology, not from a source.

| | Clown | Tang | Cardinal | Cleaner | Eel | Falco HF | Blenny | Coral shrimp |
|---|---|---|---|---|---|---|---|---|
| Clown | - | N | N | L (visits) | M | L | N | N |
| Tang | N | - | N | L (cleaning visits) | L | L | N | N |
| Cardinal | N | N | - | N | H | M (juv.) | N | L |
| Cleaner | L | L | N | - | H (long term) | M (i) | N | L |
| Eel | M | L | H | H | - | L | M-H (i) | H |
| Falco HF | L | L | M (juv.) | M (i) | L | - | M (i) | M |
| Blenny | N | N | N | N | M-H (i) | M (i) | - | N |
| Coral shrimp | N | N | L | L | H | M | N | - |

Key risk notes:
- The eel is the main predator in this tank: it is the largest threat to cardinals, shrimp, blenny, and cleaner wrasse [CW-myth, WW, FP].
- The falco hawkfish is the second main predator to small fish and shrimp [PFK].
- Clowns and the tang are the least likely to be harmed by others, but the tang is the most ich-prone and the most space-demanding [TB].

---

## 10. Tank-level sim notes

1. **Tank size:** 60 gal (~227 L). The palette tang adult needs 450 L minimum [TB]. The snowflake moray at adult size is marginal (60 gal minimum; 130 gal recommended in another source [FP]). Model these two as chronically stressed; include growth-stunting and lifespan penalty.
2. **Territorial pairs:** Clown pair (one host). Single tang per tank. Single blenny per tank [LF]. Single banded shrimp or mated pair [RC].
3. **Predator zoning:** Eel controls the floor and rock crevices at night. Cardinals and cleaners avoid eel den.
4. **Feeding:** Schedule-driven feed events. Hawkfish strikes first at surface-mid; clowns and tang compete at surface; cleaner wrasse needs prepared foods; shrimp scavenges at night.
5. **Light cycle:** Diurnal fish (clown, tang, cleaner, falco, blenny) active during lights-on. Nocturnal (eel, cardinal partial, shrimp partial) active during lights-off.
6. **Disease:** Tang is the disease-risk species (ich). Temperature above 28 C triggers stop-feeding in tang [TB]. Clowns and cardinals are hardy.
7. **Starvation:** Cleaner wrasse will starve without prepared food; sim should track feeding acceptance and body condition, with death timeline of 2-8 weeks (est.).

---

## 11. Sources

FishBase / reference:
- [FB] Palette surgeonfish, FishBase summary: https://fishbase.se/summary/SpeciesSummary.php?id=6017
- [FB-mirror] Clown anemonefish morphology/summary (fishbase.se mirror): https://fishbase.se/summary/6509
- [FB] Bluestreak cleaner wrasse, FishBase: https://www.fishbase.org/summary/SpeciesSummary.php?id=5459
- [FB-aq] Bluestreak cleaner wrasse, aquarium notes (fishprofiles-style): https://fishprofiles.com/profiles/marine/Labridae/Labroides_dimidiatus (fetched; content used)
- [FB-aq] Cleaner wrasse, FishBase aquarium summary: https://www.fishbase.org/summary/SpeciesSummary.php?id=5459
- [FBR] Reef fish larval speeds (Bellwood & Fisher 2001): https://researchonline.jcu.edu.au/13327/1/13327_Bellwood_%26_Fisher_2001.pdf

Species profiles:
- [FP] Fishprofiles, Echidna nebulosa: https://fishprofiles.com/profiles/marine/OthersSW/Echidna_nebulosa
- [FP] Fishprofiles, Sphaeramia nematoptera: https://fishprofiles.com/profiles/marine/Percoids/Sphaeramia_nematoptera
- [WW] Wetwebmedia, snowflake moray husbandry: https://wetwebmedia.com/snowflakemoray.htm
- [WW] Wetwebmedia, Ecsenius blennies: https://wetwebmedia.com/ecseniusblennies1.htm

Care guides:
- [TB] Gensou, Hippo tang care: https://gensou.sg/hippo-tang-paracanthurus-care-guide/
- [PFK] Practical Fishkeeping, Falco hawkfish care: https://www.practicalfishkeeping.co.uk/features/care-guide-for-the-falco-or-dwarf-hawkfish/
- [SAB] Saltwater Aquarium Blog, flame hawkfish care: https://www.saltwateraquariumblog.com/flame-hawkfish-care/
- [LF] Light.fish, lawnmower blenny: https://light.fish/blog/lawnmower-blenny
- [SQ] Quality Marine, sailfin algae blenny: https://www.qualitymarine.com/quality-marine/fish/blennies/salarias/sailfin-algae-12518
- [RC] Reefs.com, coral banded shrimp: https://reefs.com/invertebrates/coral-banded-shrimp/
- [AD] Animal Diversity Web, Stenopus hispidus: https://animaldiversity.org/accounts/Stenopus_hispidus
- [CW-myth] Reef Builders, 5 cleaner wrasse myths: https://reefbuilders.com/2016/01/04/5-cleaner-wrasse-myths
- [SAS] Sea and Reef, Darwin clownfish: https://seaandreef.com/marine-ornamental-fish/clownfish/darwin
- [TCMAS] TCMAS forum, ocellaris color morphs: https://www.tcmas.org/forums/threads/the-many-faces-of-the-ocellaris-clownfish.61524

Search results used (no page fetched successfully beyond the above):
- Ocellaris: https://en.wikipedia.org/wiki/Ocellaris_clownfish
- Pajama cardinal: https://fishprofiles.com/profiles/marine/Percoids/Sphaeramia_nematoptera
- Falco: https://fishprofiles.com/profiles/marine/Percoids/Cirrhitichthys_falco/
- Flame hawkfish: https://reefapp.net/encyclopedia/neocirrhites-armatus

---

## 12. Gaps and open items

- **Blue/orange eye-line blenny:** no source confirmed. Needs photo ID.
- **Adult swimming speeds (BL/s, tail beat Hz) for these species:** no sourced values. The only sourced values are larval (Bellwood & Fisher) and one freshwater tetra (red-nose tetra, 1-3 BL/s sustained, from a separate paper). All adult speeds in this file are estimates.
- **Lifespans:** not stated for cardinal, falco, eel, shrimp, or cleaner (beyond "max 4 yr"). Estimates only.
- **Ocellaris fin counts:** from fishbase.se mirror. Primary FishBase page failed.
- **Clownfish bar positions:** estimated from common morph descriptions; not from a source.
- **Snowflake moray tank minimum:** conflict: 60 gal [WW] vs 130 gal [FP]. Treat 130 gal as the long-term target.
- **Banded shrimp cleaning:** conflict: [AD] yes vs [RC] not mentioned. Use moderate confidence.
- **Cleaner wrasse starvation timeframe:** not given. Estimate only.
