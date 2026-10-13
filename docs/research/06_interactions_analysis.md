# 06 — Species Interactions Analysis and Implementation Spec (60 gal ReefLive tank)

Scope: every fish and mobile invertebrate currently in Tee's 60 gal (ocellaris pair, juvenile blue tang, pajama cardinal, cleaner wrasse, engineer goby, falco hawkfish, lawnmower blenny, tailspot blenny, indigo dottyback; coral banded shrimp, skunk cleaner shrimp, hermits, Sally Lightfoot crab, black brittle star, snails, asterinas). Sessile animals (tube worm, corals, macroalgae) appear only where a mobile animal interacts with them. The snowflake eel in SIMULATION.md §4.5 is not in the current livestock list; its brain is left untouched and is referenced only where it already defines a pattern we reuse.

Companion documents (not re-summarised here): `02_fish_species.md` (species sheets, §9 risk matrix), `04_more_species.md` (blennies, dottyback, inverts, §13–14), `05_parameter_effects.md` (water-quality thresholds, §17 interaction matrix). Simulation target: `docs/SIMULATION.md` §4 (utility brain, steering, species brains). Note: the task brief refers to SIMULATION.md "§9.5–9.6 social memory and interaction matrix"; those sections do not exist in the current file (it ends at §8). The social-memory and pair-matrix design in §5 of this document is therefore written as a new addition, not as an edit.

**Evidence tags used throughout.** `[S:key]` = number or claim taken from the cited source (bibliography in §7). `[D:key]` = derived arithmetically from a sourced number (the derivation is shown). `[I]` = inferred by the author from ecology, analogous species, or hobby consensus; treat as a tunable default, not a fact. Where a number comes from a hobby/vendor page rather than peer-reviewed literature the key is prefixed `H-` so the implementer can see the evidence grade at a glance.

---

## 0. Summary of key findings

1. **The cleaner wrasse is the social hub of the tank, not a bystander.** Wild *Labroides dimidiatus* inspect 2297 ± 83 clients per day and spend 256 ± 11 min/day inspecting [S:Grutter96], i.e. roughly one inspection every 20 s during the 4.3 inspecting hours [D:Grutter96] with a mean inspection of ~6.7 s [D:Grutter96]. Cleaner presence cut predator-on-prey chases by about two-thirds in captive aquaria [S:Cheney08]. The current sim treats cleaning as a rare, client-initiated 10–30 s event with a 10 min cooldown; it should be a near-continuous, cleaner-initiated loop with short interactions, a "tactile dance" that is aimed at larger/hungry clients, and a global aggression-damping effect.
2. **Clownfish dominance is a size-ratio queue, not a vague "female is bossier."** Adjacent ranks keep a size ratio of ~1.26 (*A. percula*) [S:Buston03] to ~1.30 (*A. frenatus*) [S:Colleye12]. The subordinate signals with a head-first lateral quiver ("head shake") that travels backward along the body, accompanied by multi-pulse submissive sounds; the dominant female never produces submissive signals [S:Colleye12]. Aggression toward intruders is female-led (3.5× the male's rate toward a large intruder) and *sensitises* with repeated exposure (roughly doubles over three exposures; latency drops 60–76 %) [S:Fleming24]. Aggression toward an intruder scales with how many vertical white bars it has [S:Hayashi24], which in this tank means the two clowns treat each other as the only "3-bar" fish and ignore everyone else unless the host is approached closely.
3. **Three animals are "one per tank" territory-holders whose aggression is release-triggered, not constant:** the dottyback (attacks intruders regardless of size; newcomers attacked immediately; standoff reached with fish that own a territory elsewhere) [S:H-FC-dotty], the lawnmower blenny (chased ~90 % of intruding blennies but only ~20 % of intruding damsels; rarely chases non-competitors) [S:H-TFH-LMB], and the hawkfish (haremic, territorial toward same-sex conspecifics; tolerates small juveniles) [S:Kadota11]. All three should be modelled as **territory-radius triggers with a target filter**, not as blanket aggression scores.
4. **The juvenile blue tang is a prey-mode animal in this tank, not a bully.** Juveniles hide in branching coral and live rock when frightened, extend the caudal spine to wedge themselves in, and can "play dead"; male-male spine displays and circling fights are an adult behaviour [S:ADW-tang]. In a 227 L tank with no other surgeonfish the tang's agonism is limited to a lateral turn + spine flare toward the dottyback or blenny when cornered at a grazing patch [I]. Its real behavioural stories are grazing (acanthurids feed 30–70 % of daylight) [S:Montgomery89], cleaning visits, and ich (flashing, rapid breathing, lethargy) [S:UF-FA164].
5. **Night is a separate ecology.** The pajama cardinal is a nocturnal forager (64–93 % of apogonids have full stomachs at dawn vs 5–36 % at dusk) [S:Marnane02] and a strict daytime shade-hider that clamps fins in bright light [S:H-TSA-card]. The engineer goby's juveniles leave the burrow to feed and return at night to hang from the burrow ceiling by mucus threads; adults are burrow-bound and reclusive [S:H-FK-conv]. The skunk cleaner shrimp forages at night; the coral banded shrimp and Sally Lightfoot emerge at night. The wrasse sleeps in a mucus cocoon (cocoon costs ~2.5 % of daily energy; reduces gnathiid attack from ~95 % to ~10 % in the cited parrotfish study) [S:Grutter11]. The current sim has only "diurnal/nocturnal/crepuscular" activity curves; it needs a nocturnal foraging layer with its own predator-prey edges (hawkfish and dottyback asleep; crab, CBS and brittle star active).
6. **Shrimp politics are lethal and molt-timed.** *Lysmata amboinensis* kept in threes or fours killed the extras — always just after the victim molted — until only a pair remained, within 42 days [S:Wong11]. *Stenopus hispidus* of the same sex "usually fought to the death"; moving a shrimp to new surroundings raises its aggression [S:Johnson69]. CBS and skunk cleaner in one 60 gal is a standing risk edge that should be modelled as a low-probability molt-window kill, not a constant.
7. **Water quality shifts behaviour before it kills.** Feeding voracity drops from 1 mg/L TAN upward [S:Ronces21]; salmonid swimming activity drops at ~0.2–1 mg/L TAN [S:Ronces21 citing]; at 3 mg/L DO benthic/column fish surface or retreat to shelter [S:Yu23]; ich produces flashing, rapid breathing and hanging at the surface or bottom [S:UF-FA164]. The spec (§4) maps each parameter band in `05_parameter_effects.md` to concrete behaviour multipliers.
8. **Newcomer aggression is a priority effect that decays.** Residents only three hours ahead were more aggressive and pushed intruders away from shelter; aggression is strongest between animals adjacent in the hierarchy; size outranks residency, which outranks behavioural history [S:Poulos16]. The spec adds a per-pair `familiarity` memory that habituates aggression over days and resets when rockwork is rearranged [S:H-FC-dotty].
9. **Parameter changes act through time constants, and rate of change matters as much as level.** Fish metabolic rate re-equilibrates to a +3 °C step in ~1 week but aerobic scope needs 4–5 weeks, and cardinalfish-type species may never acclimate (> 50 % mortality) [S:Dabruzzi21]; hypoxia tolerance does not acclimate at all: +3 °C raised the critical O₂ level by 71 % (cardinalfish) and 23 % (damselfish) [S:Nilsson10]. Salinity disturbance peaks within 2 h and resolves in 24 h [S:Mancera02]; acute cortisol peaks ~4 h after a stressor and is gone by 24 h [S:Mathew23]; short heat raises aggression while chronic heat lowers it and can flip dominance [S:Warren16]; low pH makes fish *bolder*, not shyer [S:Munday14]. §4.1 specifies a two-time-constant stress model per channel per species with an acclimating setpoint, so that ramps and steps of the same size behave differently without special cases.
10. **The sandbox needs ramp-vs-step, scheduled failures, time compression, a free-ammonia readout, a DHW gauge, water changes, and a per-organism stress inspector** to run realistic change tests (§4.2). All of it is first-order filters: ~1,100 multiply-adds per tick for 25 organisms, well inside a phone budget.

---

## 1. Species ethograms

Conventions: BL = body length of the individual as it is now in the tank (sizes from 02/04 species sheets). Tank frame as in SIMULATION.md: x 0–122 cm (length), y = height above sand (water surface ≈ 55 cm), z = front-to-back. Zones: **floor** (y < 12), **low** (12–25), **mid** (25–40), **upper** (40–55). Activity budgets are for a 10 h photoperiod with a 2 % moonlight phase.

Speed baseline for all labriform (pectoral-rowing) fish — clowns, tang, wrasse, cardinal, dottyback, blennies use pectoral or pectoral-caudal gaits [S:Fulton07]. Fulton's field averages for labriform swimmers reach up to 68.8 cm/s with lab U_crit maxima of 82.4 cm/s; field speeds average about 68–70 % of lab maxima for labriform species and ~44–48 % for pectoral-caudal and body-caudal modes [S:Fulton07]. The per-species BL/s values below are inferred from those aggregates plus body size and are tagged [I] unless noted.

### 1.1 Ocellaris clownfish pair (*Amphiprion ocellaris*) — female (orange, larger), male (black)

| Item | Value | Evidence |
|---|---|---|
| Social structure | Breeding pair; in the wild a female α, male β and several non-breeders ranked by size [S:Hayashi24]. Adjacent ranks differ by a size ratio of ~1.26 (*A. percula*) [S:Buston03]; ~1.30 in *A. frenatus* [S:Colleye12]. | sourced |
| Rank maintenance | Subordinates regulate growth to stay below the threshold; a subordinate that grows too close is evicted/chased out by the dominant [S:Hayashi24, S:Buston03]. | sourced |
| Submissive display | "Head shaking": lateral quiver that starts at the head and moves backward, always accompanied by submissive sounds (2–6 pulses per sound, 2–9 sounds per series, sound period ≈ 197 ms, i.e. a series lasts ~0.4–1.8 s) [S:Colleye12]. The dominant female never produces it [S:Colleye12]. | sourced |
| Aggressive display | Charge/chase of smaller group members, with single-pulse aggressive sounds (pulse period ≈ 76 ms) [S:Colleye12]; "stare" and "circle" toward caged intruders [S:Hayashi24]; bites, strikes, chases and displays in intruder assays [S:Fleming24]. | sourced |
| Who attacks what | Aggression toward an intruder scales with its vertical bar count: 3-bar ≫ 2-bar > 1/0-bar; plain discs with painted bars elicit the same response [S:Hayashi24]. Female 3.5× more aggressive than male toward a large (damselfish) intruder, 1.5× toward a hermit crab; sexes similar toward a male conspecific; female bites 6.5×/chases 4.1× more than the male toward a female conspecific [S:Fleming24]. | sourced |
| Sensitisation | Over three repeated intruder presentations aggressive acts roughly doubled, chases rose 2.5×, latency to respond fell 60–76 % [S:Fleming24]. | sourced |
| Defended radius | Wild anemonefish ignore divers at 2.0 m, respond at 1.0 m (*A. frenatus*, *A. clarkii* females) or 0.3 m (*A. perideraion*) [S:Nakamura25]. Scaled to a 7–8 cm tank clown and a soft-coral "host", use 15–25 cm (≈ 2–3 BL) as the defended radius [I]. | sourced → scaled |
| Host attachment | Rarely stray more than a few metres from the host in the wild [S:Fleming24]; in the tank, within 3 BL of the host 95 % of the time is already the acceptance test (SIMULATION §8.6). | sourced |
| Activity budget (day) | Host-hover/waddle 55 %, short excursions/wander 20 %, feeding 10 %, pair interaction (quiver, nudge, nip) 5 %, host nestling/dart-in 10 % [I]. | inferred |
| Night | Both nestle at the host; the female takes the centre; nearly motionless, fins fanning [I, consistent with damselfish sleeping motionless in crevices, S:Wiki-sleep]. Arousal threshold higher at night (zebrafish ~2× harder to arouse) [S:Wiki-sleep]. | inferred/analogue |
| Gait | Labriform (pectoral) [S:Fulton07]; characteristic lateral "waddle" at ~1.3 Hz already in SIMULATION §4.5 [I]. Cruise 1.0–1.5 BL/s, burst 4–5 BL/s for ≤ 1 s [I]. | inferred |
| Territory | Host plus 2–3 BL halo [I]. | inferred |

### 1.2 Blue tang, juvenile (*Paracanthurus hepatus*, 6–8 cm)

| Item | Value | Evidence |
|---|---|---|
| Social | Pairs or small groups in the wild; aggregates with other acanthurids; schools for protection [S:ADW-tang]. Single in this tank. | sourced |
| Agonism (adult) | Males circle, display the caudal spine, blue deepens as the encounter intensifies; slash until one is injured; dominance gained by "flashing" the spine [S:ADW-tang]. A full acanthurid ethogram exists (Baer 1980 PhD, 276 pp.) but the text was not accessible [S:Baer80]. | sourced |
| Fear | Frightened fish, "particularly juveniles", hide behind live rock or within branching coral; a frightened fish extends the spine into coral to anchor itself; may "play dead" lying on its side, recovering within days in aquaria [S:ADW-tang]. | sourced |
| Colour signal | Stress deepens blue and fades black markings [S:ADW-tang]; night paling is normal (02 §2). | sourced |
| Feeding | Acanthurids spend 30 % (*Ctenochaetus*) to 55–70 % (*A. nigrofuscus*, *Z. xanthurum*) of daytime actively feeding; a food bolus is retained in the gut overnight [S:Montgomery89]. | sourced (congeners) |
| Territory | Territorial acanthurids hold 12.8–17.0 m² (*A. sohal*) [S:Sohal]; *P. hepatus* home range is defined mainly during breeding [S:ADW-tang]. In a 0.5 m² tank the tang holds no territory; it has a **patrol lane** (front open water) and a **sleep crevice** [I]. | sourced → inferred |
| Activity budget (day) | Graze/forage 45 %, patrol/cruise 30 %, hover near cover 10 %, cleaning visits 3 %, feed events 5 %, startle/hide 7 % [I; grazing share set at the low end of Montgomery89 because prepared food is also offered]. | inferred |
| Night | Wedged in a crevice (`SLEEP_TANG`), tilted, pale [I; consistent with crevice-sleeping diurnal reef fish, S:Wiki-sleep]. | inferred |
| Gait | Labriform cruise with caudal burst [S:Fulton07]. Cruise 1.5–2 BL/s, patrol sprints 3 BL/s, flight burst 6–8 BL/s for ≤ 0.7 s [I]. | inferred |
| Disease behaviour | Ich: flashing (scratching), rapid breathing, lethargy, hanging at the surface or bottom [S:UF-FA164]. | sourced |

### 1.3 Pajama cardinalfish (*Sphaeramia nematoptera*)

| Item | Value | Evidence |
|---|---|---|
| Diel pattern | Nocturnal forager: across 7 apogonid species 64–93 % collected at dawn had food in the stomach vs 5–36 % at dusk; restricted daytime resting habitats, wider night habitats, species spatially segregated at night [S:Marnane02]. Pajamas are "more strictly nocturnal than other cardinalfish", prefer deeper shade and tighter caves, show clamped fins and dull colour in bright light without cover, and emerge in the dim period before lights-out [S:H-TSA-card]. | sourced |
| Social | Site-attached: pairs stay at one site > 4 months; 78 % re-pair within 2 weeks after mate loss; only 1 of 24 followed a translocated partner [S:Rueger18]. Socially monogamous; strict hierarchy in groups without aggression [S:Wiki-card]. | sourced |
| Interactions received | Dottybacks, hawkfish, damsels and angels push cardinals into long-term hiding; faster tankmates monopolise food [S:H-TSA-card]. | sourced (hobby) |
| Activity budget | Day: hover in shade 85 %, feed 5 % (only if food drifts to it), relocate 10 %. Dusk/night: forage drifting plankton/mysis 50 %, hover 50 %, radius grows from ~10 cm to ~40 cm [I, built on Marnane02]. | inferred |
| Gait | Hover with pectoral sculling; short labriform moves. Cruise 0.5–1 BL/s, flight 3–4 BL/s [I]. | inferred |
| Sleep | Does not "sleep" at night; rests by day in shade with reduced responsiveness [I from Marnane02]. | inferred |

### 1.4 Bluestreak cleaner wrasse (*Labroides dimidiatus*)

| Item | Value | Evidence |
|---|---|---|
| Daily workload (wild) | Inspects 2297 ± 83 clients/day, 256 ± 11 min/day inspecting, eats 1218 ± 118 parasites/day, 0.5 ± 0.05 parasites per inspection [S:Grutter96]. | sourced |
| Derived | 256 min / 2297 ≈ **6.7 s mean inspection**; 2297 / 256 min ≈ **9 inspections per minute** while working; inspection frequency did not vary across diel periods [S:Grutter02]. | derived |
| Client-side load | Inspection rate and duration scale with client surface area and ectoparasite load [S:Grutter95]; a heavily cleaned client (*Siganus doliatus*) spent ~32 min/day being cleaned [S:Grutter94]. | sourced |
| Tactile dance | Cleaner oscillates its rear body up and down within ~15 cm of the client, mostly in place, often touching it; given more to hungry clients and to predators [S:Grutter04, S:Bshary01]. ~50 % of a cheating cleaner's interactions with small residents were tactile-only [S:Pinto11]. Tactile touching of predators ≥ 3× that of non-predators; predators chased prey ~⅔ less with a cleaner present [S:Cheney08]. | sourced |
| Client posing | Fins extended, mouth open [S:Grutter94]; drift motionless in response to tactile stimulation [S:Bshary01]; posing clients flare opercula/fins [S:Caves19]. | sourced |
| Cheating/jolts | Non-predatory clients jolt 2–6 times per 100 s with honest cleaners; 13–21 with biting cleaners [S:Pinto11]. Residents punish by chasing the cleaner; clients with options switch stations; predators may eat it [S:Pinto11, S:Bshary01]. | sourced |
| Night | Mucus cocoon (wrasses and parrotfish); secreted nightly, costs ~2.5 % of daily energy, cuts gnathiid attack rate from ~95 % to ~10 % [S:Grutter11]. Sleeping wrasses can be picked up by hand [S:Wiki-sleep]. | sourced |
| Activity budget (day) | Inspect/clean 40 %, station-hover & advertise 25 %, rock-face "surf" foraging 25 %, prepared-food feeding 5 %, flee/avoid 5 % [I, scaled from the 4.3 inspecting hours in Grutter96 to a 10 h photoperiod]. | derived/inferred |
| Gait | Labriform, constant motion; cruise 1.5–2 BL/s; flight 5 BL/s [I]. | inferred |
| Territory | Owns a station (`STATION_CLEANER`) plus a working range covering the whole tank [I]. | inferred |

### 1.5 Engineer goby / convict blenny (*Pholidichthys leucotaenia*)

| Item | Value | Evidence |
|---|---|---|
| Life stages | Juveniles black with white stripes, mimic the venomous striped catfish; stripes break into spots at 49–108 mm; adults eel-like, dark/yellow-mottled, > 10 cm (to ~35 cm in captivity) [S:MBA-eng, S:H-QM-eng]. | sourced |
| Burrowing | Moves mouthfuls of sand and gravel, builds tunnels for shelter [S:MBA-eng]. Adults stay hidden in burrows and are "very reclusive" [S:H-FK-conv]. | sourced |
| Juvenile behaviour | Small juveniles form swarms on shallow reefs; larger juveniles move to rocky areas [S:MBA-eng]. Juveniles leave the burrow to feed in swarms and at night return to hang from the burrow ceiling by mucus threads [S:H-FK-conv]. | sourced |
| Temperament | Generally peaceful; can become territorial toward similar species; meaty foods [S:H-QM-eng]. Diet: plankton and small crustaceans [S:MBA-eng]. | sourced |
| Activity budget | Juvenile phase (if < 10 cm): day — column swimming near rock 40 %, burrow 50 %, feed 10 %. Adult phase: day — in burrow with head out 85 %, burrow maintenance (sand carrying) 10 %, feed 5 %; dusk/night — out of burrow patrolling the sand perimeter 40 % [I; night activity inferred from "reclusive by day" plus hobby reports]. | inferred |
| Gait | Anguilliform/sub-carangiform; cruise 0.5–1 BL/s, burst 3 BL/s [I]. Reuse the eel spline and `den_path` mechanism (SIMULATION §4.5, §4.8). | inferred |
| Territory | Burrow system under the rock (new anchor `BURROW_ENG`, 2–3 exits); defends burrow mouths only [I]. | inferred |
| Hazard | Tunnelling undermines rock; sand-carrying raises detritus suspension [I]. | inferred |

### 1.6 Falco (dwarf) hawkfish (*Cirrhitichthys falco*, ≤ 7 cm)

| Item | Value | Evidence |
|---|---|---|
| Social | Haremic: a male's home range overlaps those of 2–3 females (up to 7 reported); each sex defends its range against same-sex conspecifics at its edges; large juveniles hold their own ranges outside female territories, small juveniles are tolerated inside [S:Kadota11, S:FoA-falco]. Protogynous. Spawns in the water column around dusk [S:FoA-falco, S:H-RG-hawk]. | sourced |
| Diet | Smallest size class mainly copepods; larger classes mainly decapods (shrimps/crabs) [S:Kadota11]; also small fish [S:H-RG-hawk]. | sourced |
| Locomotion | No swim bladder; perches on thickened pectoral rays on coral/rock; stays on a perch much longer than it swims; darts between perches; can shuffle short distances [S:H-RG-hawk]. | sourced |
| Activity budget (day) | Perch (still, head turns) 80 %, hop between perches 7 %, strike/feed 5 %, chase intruders near perch 3 %, hide 5 % [I]. | inferred |
| Night | On the lowest perch under an overhang, motionless [I]. | inferred |
| Gait | Burst-only: hop 3 BL/s for 0.3–0.6 s, strike 6–8 BL/s for ≤ 0.4 s [I]. | inferred |
| Territory | Perch cluster (5 `PERCH` anchors) with a 1 BL exclusion bubble around the occupied perch [I]. | inferred |

### 1.7 Lawnmower blenny (*Salarias fasciatus*, ~10 cm)

| Item | Value | Evidence |
|---|---|---|
| Feeding | ~3000 bites at the substrate per day, > 2× the sympatric damsel; feeds continuously through the day with a midday dip and a 14:00–16:00 rise (peak foraging 12:00 or 15:00 in two studies); gut full by 07:00 [S:H-TFH-LMB, citing Heron Island studies]. Derived: 3000 bites / 10 h ≈ **5 bites/min** average [D:H-TFH-LMB]. | sourced (secondary) |
| Territory & aggression | Territorial. Chased ~90 % of intruding blennies (conspecifics, *Atrosalarias*, *Ecsenius*), most aggressive toward blennies its own size or smaller; chased ~20 % of intruding damsels; rarely chases other herbivores; ignores non-trophic competitors; typically ignores the damsel whose territory it lives inside [S:H-TFH-LMB]. In tanks it chases and nips other blennies "especially smaller ones like *Ecsenius*" until they hide or sit in corners; may bite tangs/rabbitfish [S:H-TFH-LMB]. | sourced (secondary) |
| Rest/hide | Spends most of its time resting on hard substrate; dives into a crack when threatened, often leaving the head protruding [S:H-TFH-LMB]. | sourced |
| Analogue numbers | Territorial blenny *Ophioblennius trinitatis*: territory 0.79 ± 0.09 m², 10.7 ± 0.7 bites/min, ~8.8 % of time feeding, > 85 % of attacks on conspecifics and trophic competitors, agonism lower in more complex habitat [S:Mendes14]. | sourced (analogue) |
| Activity budget (day) | Perch/rest on rock 55 %, graze 30 % (rasp bouts), move between patches 8 %, in hole 5 %, chase 2 % [I; blenny time-feeding of ~9 % in Mendes14 is for a 10.7 bites/min species, the lawnmower's 3000 bites/day implies ~2–3× that share]. | derived/inferred |
| Night | Fully in its hole [S:H-TFH-LMB implies; I]. | inferred |
| Gait | Pectoral-perching hops; cruise 1 BL/s, dash 4 BL/s ≤ 0.6 s [I]. | inferred |
| Territory | ~¼ of the rock surface, centred on `HOLE_BLENNY` or a second hole (new anchor `HOLE_LMB`); defended radius 20–30 cm (2–3 BL) against blennies, 10 cm against grazing competitors [I from H-TFH-LMB percentages and Mendes14 0.79 m² scaled to tank]. | inferred |

### 1.8 Tailspot blenny (*Ecsenius stigmatura*, ~5–6 cm)

| Item | Value | Evidence |
|---|---|---|
| Behaviour | Small *Ecsenius*: perches, peeks from holes, short grazing dashes (04 §2). The lawnmower chases *Ecsenius* "until they leave, hide or stay in corners" [S:H-TFH-LMB]. | sourced (secondary) |
| Activity budget (day) | Peek from hole 40 %, perch outside 30 %, graze dashes 20 %, flee 10 % — the flee share is high because of the lawnmower and dottyback [I]. | inferred |
| Territory | 1 hole + 10–15 cm (2–3 BL) radius; yields to the lawnmower everywhere outside that core [I]. | inferred |
| Gait | Like 1.7, scaled; dash 4–5 BL/s [I]. | inferred |

### 1.9 Indigo dottyback (*Pseudochromis fridmani × sankeyi*, ~6–7 cm)

| Item | Value | Evidence |
|---|---|---|
| Territory | Claims one cave/crevice/overhang and defends it; "intruders are attacked regardless of their size"; fish passing briefly through avoid sustained attack; fish with their own territory elsewhere reach a standoff; one dottyback may control one cave complex in a 100 gal [S:H-FC-dotty]. | sourced (hobby) |
| Newcomers | Fish added after an established dottyback "face immediate attack, which can sometimes be fatal"; deaths come from stress and exhaustion rather than injury; rearranging rock resets boundaries [S:H-FC-dotty]. | sourced (hobby) |
| Targets | Timid, slow, small fish (gobies, firefish) and small ornamental shrimp; tolerates larger robust fish (tangs, big wrasses) [S:H-FC-dotty]. Orchid parent relatively mild; *sankeyi* parent similar [S:H-FC-dotty, 04 §3]. | sourced (hobby) |
| Display | Healthy animals "patrol and display their territory boldly" [S:H-FC-dotty]. Courtship in *P. fridmani*: raised dorsal fin, side-to-side head and tail shaking repeated many times; males chase females out of the pipe and chase them when they approach the eggs [S:Wittenrich23]. Lateral display with erect dorsal is therefore in the species' repertoire [S:Wittenrich23 → I for agonistic use]. | sourced → inferred |
| Activity budget (day) | Hover-dart along ledges within 20 cm of the cave 50 %, inside cave/peek 25 %, patrol beyond core 15 %, feed 5 %, chase/display 5 % [I]. | inferred |
| Night | In cave, motionless [I]. | inferred |
| Gait | Hover-dart: cruise 1.5 BL/s with 0.5 s stops; chase burst 5–6 BL/s ≤ 1 s [I]. | inferred |
| Territory | Cave + 20–25 cm (3–4 BL) halo; the whole adjacent ledge system at first, shrinking as neighbours establish [I from "standoff" in H-FC-dotty]. | inferred |

### 1.10 Mobile invertebrates (brief)

| Animal | Ethogram essentials | Evidence |
|---|---|---|
| Skunk cleaner shrimp (*Lysmata amboinensis*) | Station on rock/coral head, antennae waving; **leg rocking** (rapid back-and-forth of the white forelegs, sometimes antenna lashing) in 51 % of interactions with predatory clients vs 12 % with non-predators, triggered in the lab by dark stimuli and drops in light; cleaned 28 % of predatory and 51 % of non-predatory clients; 44 % of posing predators left uncleaned; client jolts in 10–22 % of interactions; interactions coded ≤ 1 min [S:Caves19]. In groups of 3–4, the pair kills the extras just after they molt, all tanks reduced to a pair within 42 days; survivors then molt more often [S:Wong11]. Molt every 3–6 weeks (04 §4). Night scavenging [I]. | sourced |
| Coral banded shrimp (*Stenopus hispidus*) | Lives in male–female pairs; an "incidental" cleaner; unfamiliar same-sex animals usually fight to the death, no appeasement behaviour; opposite-sex fight briefly then court; moving to new surroundings raises aggression; recognises individuals [S:Johnson69]. Cave-anchored, nocturnal scavenger (02 §8). | sourced |
| Sally Lightfoot crab (*Percnon gibbesi*) | Semi-aggressive, "becomes more aggressive as it grows"; once large "may catch and eat small invertebrates and fish"; omnivore (detritus, algae, meaty foods); hides in crevices, prefers strong current; max 7.6 cm [S:H-LA-sally]. Nocturnal/crepuscular emergence [I]. | sourced (vendor) |
| Hermit crabs | Shell fights with each other and may kill snails for shells (04 §5–6, §14). Diurnal + nocturnal grazing/scavenging. | 04 |
| Black brittle star | Nocturnal scavenger under rock; arms emerge toward food; harmless to fish at this size (04 §7). | 04 |
| Nassarius / astrea snails, asterinas | Nassarius buried, erupts on food; astrea diurnal grazer; asterinas slow, may nip zoas (04 §9–11). | 04 |

---

## 2. Pairwise interaction analysis

Each entry: **type** · **trigger & distance** · **display sequence** · **escalation / de-escalation** · **learning**. Distances are tank-scaled; "BL" is the actor's body length. Pairs not listed are `indifference` (co-occupy space with normal separation steering only).

### 2.1 Clown female ↔ clown male (pair hierarchy)
- **Type:** dominance (stable size-based hierarchy, mated pair).
- **Trigger:** proximity < 0.5 BL, feeding, host-position competition, or the male's size ratio to the female falling below ~1.26 [S:Buston03].
- **Sequence:** female approaches or turns broadside → male performs head-first lateral quiver ("head shake", ~0.5–1.5 s, 2–9 sound pulses in series) [S:Colleye12] and yields 0.5–2 BL → female resumes. If the male does not yield: female charge (burst 0.3 s) → nip → male flees 3–5 BL, returns within 10–30 s [I].
- **Escalation:** rare in a bonded pair; the female's aggression toward a same-sex rival is 6.5× (bites) the male's, with a 0.9-bite reduction per cm of size difference [S:Fleming24] — a second female or a male that outgrows the ratio would be chased out (eviction) [S:Hayashi24].
- **De-escalation:** the quiver ends the bout; the dominant never signals submission [S:Colleye12].
- **Learning:** none needed; the hierarchy is fixed by size. Expose a `pairBond` that increases with days together; the quiver frequency drops from ~6/h (new pair) to ~2/h (established) [I].

### 2.2 Clowns ↔ any fish near the host (host defence)
- **Type:** aggression (territorial), female-led.
- **Trigger:** non-partner fish inside the host halo (2–3 BL ≈ 15–25 cm) [I, scaled from Nakamura25 response distances]. Weight by the intruder's resemblance: in this tank nothing has 3 vertical bars, so aggression stays at the "0–1 bar" baseline [S:Hayashi24]. Female score × 3.5 vs male toward large intruders [S:Fleming24].
- **Sequence:** stare/orient (0.5 s) → lateral display with fins erect (1 s) → charge to 0.5 BL → nip. The tang (> 1.8× clown length rule in SIMULATION) and the wrasse are chased only to the halo edge; the cardinal, tailspot blenny and dottyback are chased 1–2 BL beyond it [I].
- **Escalation:** repeated intrusions sensitise: aggression per bout ≈ ×1.3 per exposure up to ×2 over three exposures, latency −60 % [S:Fleming24]; this decays back over ~24 h [I].
- **De-escalation:** intruder leaves the halo → return to host within 3 s. The cleaner wrasse is a special case: when it arrives in `Clean` intent (tactile approach), the clown poses instead of attacking [S:Bshary01; I for clowns].
- **Learning:** `intrusions[intruder]` counter with 24 h half-life drives the sensitisation multiplier.

### 2.3 Dottyback ↔ clown pair
- **Type:** mutual territorial standoff with occasional aggression.
- **Trigger:** the dottyback's cave halo (3–4 BL) overlapping the host halo; hobby reports of *P. fridmani* attacking a black ocellaris pair exist [S:H-reefs-fridclown], so when the halos overlap, weight 0.3 toward the dottyback as aggressor and 0.5 toward the female clown as aggressor [I].
- **Sequence (dottyback):** hover-stop facing the clown (0.5 s) → lateral display, dorsal raised, head/tail side-to-side shake [S:Wittenrich23 for the display form] → dart to 0.5 BL → veer off. **Clown reply:** female lateral display → charge; the dottyback retreats to the cave (burst 5 BL/s).
- **Escalation / de-escalation:** "fish that hold their own territory elsewhere reach a standoff" [S:H-FC-dotty] → after `familiarity` > 0.6 (≈ 5–7 days) bouts drop to display-only and the halos effectively partition the rock [I].
- **Learning:** symmetric `familiarity` growth; reset on rock rearrangement.

### 2.4 Dottyback ↔ tailspot blenny, cardinal, cleaner wrasse (small/timid targets)
- **Type:** aggression / chase; cardinal and tailspot are the high-risk receivers [S:H-FC-dotty, 04 §13A].
- **Trigger:** target within 3–4 BL of the cave, or target newly introduced (`familiarity` = 0): newcomers "face immediate attack" [S:H-FC-dotty].
- **Sequence:** orient → lateral display 0.5–1 s (skipped when `familiarity` < 0.2) → chase at 5–6 BL/s for up to 1.5 s or 40 cm → nip if within 0.3 BL → return to cave. Chase bout rate: 4–8/h in week 1, 0.5–2/h after establishment [I].
- **Escalation:** repeated chases raise the target's `S_social` stress; death "from stress and exhaustion rather than direct injury" [S:H-FC-dotty] → model as chronic stress, not HP damage.
- **De-escalation:** the target retreats out of the halo; the cardinal responds by long-term hiding (reduced day/dusk activity) [S:H-TSA-card]. The wrasse is chased only if it is not in `Clean` intent; cleaners are generally tolerated by their clients [S:Bshary01 → I].
- **Learning:** `familiarity` grows 0.15/day of co-residence without a fatal outcome; chase probability = base × (1 − 0.75 × familiarity) [I].

### 2.5 Dottyback ↔ lawnmower blenny / hawkfish (peer territory-holders)
- **Type:** standoff; low-rate mutual displays.
- **Trigger:** halo overlap. The lawnmower "typically ignores non-trophic competitors" [S:H-TFH-LMB] and the hawkfish defends only its perch bubble [S:Kadota11 → I].
- **Sequence:** both lateral-display 1 s, no chase 80 % of the time; 20 % the dottyback darts, the blenny dives into its hole with head out [S:H-TFH-LMB]; the hawkfish holds its perch and gapes (short charge if the dottyback lands within 1 BL of the perch) [I].
- **Learning:** `familiarity` as in 2.4.

### 2.6 Dottyback ↔ juvenile tang
- **Type:** mostly tolerance ("larger, robust fish such as tangs are tolerated") [S:H-FC-dotty]; at the current 6–8 cm the tang is not yet large, so give 20 % of the 2.4 chase rate for the first month, decaying to 0 once tang length > 1.5 × dottyback length [I].
- **Tang reply:** when chased into a corner the tang turns broadside, flares the caudal spine (`spineFlare` posture, 0.5 s) and holds; the dottyback veers [S:ADW-tang for the spine display → I for this pair].

### 2.7 Lawnmower blenny ↔ tailspot blenny
- **Type:** aggression (same-guild, same-shape) — the highest-probability conflict in this stock list [S:H-TFH-LMB; 04 §14 "H"].
- **Trigger:** tailspot inside the lawnmower's defended radius (20–30 cm) or on a grazing patch the lawnmower is using. Base probability of a chase per intrusion 0.9 [S:H-TFH-LMB "about 90 % of blennies that entered its territory were chased"].
- **Sequence:** lawnmower raises dorsal, turns to face (0.3 s) → hop-charge 4 BL/s up to 25 cm → nip if within 0.3 BL → returns to perch. Tailspot: flees to its hole (dash 4–5 BL/s), peeks for 20–60 s [I].
- **Escalation:** chronic; the loser ends up "hiding or staying in corners" [S:H-TFH-LMB]. The tailspot's `homeRadius` collapses to ≤ 10 cm around a hole on the far side of the tank from the lawnmower's hole.
- **De-escalation:** the lawnmower is less aggressive when well fed and the tank is large [S:H-TFH-LMB]; chase probability × (0.5 + 0.5 × hunger) and × (1 − 0.3 × algaeHair) [I].
- **Learning:** partial habituation only: `familiarity` lowers chase probability to a floor of 0.4, never to 0 [I; "until they leave, hide or stay in corners" implies persistence].

### 2.8 Lawnmower blenny ↔ tang (grazing competitors)
- **Type:** feeding competition, low aggression. The lawnmower "rarely chases other herbivores" but may bite tangs/rabbitfish [S:H-TFH-LMB]; wild feeding dips when surgeonfish invade its territory [S:H-TFH-LMB].
- **Trigger:** both targeting the same `GRAZE_PATCH` within 10 cm.
- **Sequence:** the blenny faces and raises its dorsal; 85 % of the time it simply keeps rasping and the tang shifts to the next patch; 15 % the blenny nips (hop 1 BL) and the tang startles 1–2 BL and flares its spine briefly [I]. Patch-level resource competition: hair density on a patch drops faster with both grazing (already in `Graze`).
- **Learning:** none; patch choice includes a −0.3 weight if the blenny is on it [I].

### 2.9 Hawkfish ↔ small fish and shrimp (ambush/predation risk)
- **Type:** predation risk + perch defence. Diet shifts from copepods to decapods with hawkfish size; small fish taken occasionally [S:Kadota11, S:H-RG-hawk]. *C. falco* max 7 cm [S:FoA-falco] — in this tank only the tailspot blenny (5–6 cm) is near the size threshold and even it is too large; strikes on fish should be **threat displays that never connect** unless a fish is < 0.5 × hawk length (none at present).
- **Trigger:** target within 15 cm and in the hawk's forward 120° while it is perched; shrimp within 10 cm (CBS is too large and armed; the skunk cleaner is at moderate-high risk, 04 §13B) [I].
- **Sequence:** head turn → body tilt toward target (0.3 s) → burst strike 6–8 BL/s → return to perch; wrasse/blenny within 1 BL of the occupied perch get a 1 s charge [I, already in SIMULATION §4.5].
- **Skunk shrimp outcome:** per strike kill probability 0.05 by day, 0.15 at dusk when the shrimp is out foraging [I]; hobby sources report both coexistence and overnight "changes of mind" (04 §13B).
- **Learning:** shrimp `threatMemory[hawk]` rises on each strike, biasing it to station on rock faces not visible from the occupied perch (line-of-sight check); hawk habituates to the wrasse's station passes after ~3 days (charge probability 0.5 → 0.1) [I].

### 2.10 Hawkfish ↔ cardinal
- **Type:** intimidation (the cardinal is a daytime hider; hawkfish push cardinals into long-term hiding) [S:H-TSA-card].
- **Trigger:** cardinal drifting within 20 cm of the occupied perch by day.
- **Sequence:** hawk orients and makes a 1 BL hop toward it; the cardinal retreats behind the pillar for 30–120 s and its daytime hover radius shrinks by 30 % while the hawk is on a perch with line of sight [I].

### 2.11 Cleaner wrasse ↔ every fish (cleaning mutualism)
- **Type:** cleaning mutualism, cleaner-initiated most of the time. Wild rate ~9 inspections/min while working [D:Grutter96]; in a 9-fish tank with no gnathiid supply the sustainable rate is far lower, but the *motivation* is constant — captive cleaners keep inspecting and switch to mucus/scales (the "cheating" of Bshary01) when parasites are absent.
- **Client priority:** inspection frequency ∝ client surface area and parasite load [S:Grutter95] → tang > clowns > dottyback > lawnmower > hawkfish > cardinal > blennies/goby. Ich stage on the tang multiplies its weight ×3 [I].
- **Trigger & distance:** client passing within 35 cm of the station, or the wrasse encountering a client anywhere during `Surf` [I]; advertise with the up-down dance within 15 cm [S:Grutter04].
- **Sequence:** (1) approach to 1 BL, (2) tactile dance 1–2 s (rear-body oscillation, pitch ±20°, 2 Hz; already in SIMULATION) — given more often to hungry clients and to larger/"predatory" clients (here the dottyback and hawkfish) [S:Grutter04, S:Cheney08]; (3) client poses: fins extended, mouth open, opercula flared, drifting motionless [S:Grutter94, S:Caves19]; (4) inspection 3–12 s (mean ~6.7 s) [D:Grutter96], nibbling 1.5 Hz along the flank, gills, fins; (5) termination by the client swimming off, or by a **jolt** (client twitch) when the cleaner bites mucus: 2–6 jolts per 100 s baseline [S:Pinto11]; after a jolt the client swims off (70 %) or chases the cleaner 1–2 BL (30 %, residents) [S:Pinto11, S:Bshary01].
- **Escalation:** a cleaner with hunger > 0.6 cheats more (jolt rate 13–21/100 s) [S:Pinto11]; the client's `cleanerTrust` drops; the dottyback/hawkfish/lawnmower may chase. Clients with no alternative station (all of them here, apart from the skunk shrimp) keep returning [S:Pinto11].
- **De-escalation:** tactile dance resets client aggression (predators chase prey ~⅔ less with cleaner present) [S:Cheney08]; in the sim, every completed tactile bout applies a global `aggressionDamp` 0.35 for 20 min to the client [D:Cheney08 — "two-thirds less"].
- **Learning:** per client `cleanerTrust` ∈ [0,1], +0.05 per honest interaction, −0.2 per jolt; posing probability = 0.3 + 0.6 × trust [I]. The wrasse learns client preferences: visit weight × (1 + 0.5 × recentPoses) [I; cleaners recognise familiar clients, S:Tebbich02].

### 2.12 Cleaner wrasse ↔ skunk cleaner shrimp (two stations)
- **Type:** service competition, no documented aggression (04 §13C).
- **Rule:** clients choose the nearer active station weighted by cleaner fitness; the shrimp's station is preferred by the hawkfish and dottyback only when the shrimp performs leg-rocking (the signal is aimed at predators) [S:Caves19] and by fish lying near the ledge; the wrasse takes mobile clients [I].
- **Wrasse ↔ shrimp direct:** the wrasse may pick at shrimp antennae (04 §13C, est.); probability 0.02 per station pass, shrimp retreats 5 cm [I].

### 2.13 Skunk cleaner shrimp ↔ fish clients
- **Type:** cleaning mutualism, client-initiated (fish approach the ledge).
- **Sequence:** client approaches within 5 cm of the ledge and poses (rests near substrate, flares opercula/fins) [S:Caves19] → shrimp antennae-taps the body → climbs on and picks for 10–60 s (interactions in the field coded up to 1 min) [S:Caves19]. For predatory-looking clients (dottyback, hawkfish): leg rocking first in ~51 % of interactions, then cleaning in only 28 % of interactions; 44 % of posing predators leave uncleaned [S:Caves19]. Non-predators (tang, clowns, goby): cleaned in 51 % [S:Caves19]. Jolts in 10–22 % of interactions → client leaves [S:Caves19].
- **Light trigger:** leg rocking is also triggered by sudden darkness/shadow [S:Caves19] → use it as the shrimp's reaction to a fish casting shadow over it and to lights-off.

### 2.14 Coral banded shrimp ↔ skunk cleaner shrimp, hermits, snails
- **Type:** aggression / predation risk at molt. Same-sex *Stenopus* fight to the death [S:Johnson69]; *Lysmata* pairs kill freshly molted conspecifics [S:Wong11] — cross-species kills are not documented but hobby reports exist (04 §14 L (i)).
- **Rule:** CBS and skunk shrimp maintain ≥ 25 cm separation (mutual avoidance, the smaller yields); if the skunk shrimp has molted within the last 24 h and comes within 8 cm of the CBS at night, kill probability 0.05 per encounter [I, bounded by Wong11's molt-timing pattern]. CBS picks at hermits/small snails that walk into its cave: hermit retreats into shell for 30–60 s [I].
- **New-surroundings rule:** a freshly added CBS has aggression × 2 for 7 days [S:Johnson69 "moving to new surroundings appeared to raise aggression" → I for the number].

### 2.15 Sally Lightfoot crab ↔ fish, snails, shrimp
- **Type:** predation risk that scales with crab size [S:H-LA-sally].
- **Rule:** by day it hides in crevices in high flow; at night it patrols rock. If crab carapace > 5 cm: per-night probability of catching a sleeping cardinal or tailspot blenny that is resting within 5 cm of its path 0.01; snails within reach 0.05 (flipping and picking) [I]. Below 5 cm: harmless scavenger. Fish react to it approaching within 1 BL at night by shifting 2 BL (a sleeping-fish arousal check with the night threshold ×2) [S:Wiki-sleep → I].

### 2.16 Engineer goby ↔ everyone
- **Type:** indifference by day (in burrow); mild territoriality at burrow mouths toward "similar species" (none here) [S:H-QM-eng]; at night it is a column/sand patroller that startles sleeping fish it brushes past (same arousal rule as 2.15) [I].
- **Burrow effects:** sand ejection buries nassarius/asterina temporarily (cosmetic) and raises local detritus [I]. Hermits and the brittle star use burrow mouths as shelter — allow 2 inverts to share `BURROW_ENG` exits [I].
- **Juvenile swarm phase:** if the goby is < 10 cm it hovers in a tight oscillating ribbon near the rock by day, returns to the burrow at dusk and "hangs" from the ceiling [S:H-FK-conv]; the hawkfish shows interest (orient, no strike) [I].

### 2.17 Tang ↔ clowns, cardinal, wrasse
- Clowns: tang ignored outside the host halo; inside, chased to the halo edge (2.2). Cardinal: indifference, but the tang's patrol lane crosses the cardinal's hover zone; the cardinal shifts 5–10 cm sideways when the tang passes within 1 tang-BL (separation steering only) [I]. Wrasse: 2.11, with the tang as the priority client; a posing tang pitches head-up ~25° with pectorals spread (already in SIMULATION).

### 2.18 Hermits ↔ snails and each other
- Shell fights between hermits (lateral rap, shell rocking, 30–120 s) and hermit predation on astrea for shells (04 §5–6, §14): per-day probability that a shell-less-or-outgrown hermit attacks a snail of suitable shell size 0.02 [I]. Nassarius is not attacked while buried.

### 2.19 Night-time predator map (who threatens whom after lights-out)
| Actor (night-active) | Potential victims | Mechanism | p/night [I] |
|---|---|---|---|
| Sally Lightfoot (> 5 cm) | sleeping cardinal, tailspot, molting shrimp, snails | opportunistic grab | 0.01 fish, 0.05 snail |
| CBS | freshly molted skunk shrimp, small hermits | cave ambush | 0.05 (molt window only) |
| Brittle star | none at this size | — | 0 |
| Engineer goby (adult) | none (plankton/crustacean feeder) [S:MBA-eng]; startles sleepers | — | 0 |
| Hawkfish, dottyback, blennies, clowns, tang | asleep; no night aggression | — | 0 |

---

## 3. Group-level dynamics

### 3.1 Dominance hierarchy in this 60 gal
There is no single linear hierarchy; there are **three territorial nuclei** plus **free-rangers**:

| Nucleus | Holder | Core radius | Who is excluded | Evidence |
|---|---|---|---|---|
| Host corals (Kenya tree / GSP) | clown pair (female dominant) | 2–3 BL | anything that lingers | S:Fleming24, S:Hayashi24 |
| Cave complex (left/centre) | dottyback | 3–4 BL | small/timid fish; newcomers | S:H-FC-dotty |
| Rock surface / hole | lawnmower blenny | 2–3 BL | other blennies (90 %), grazers (20 %) | S:H-TFH-LMB |
| Perch cluster | hawkfish | 1 BL around the occupied perch | wrasse, blenny, dottyback when they land on it | S:Kadota11 → I |
| Burrow | engineer goby | burrow mouths | "similar species" only | S:H-QM-eng |

Free-rangers: tang (patrol lane + sleep crevice), cleaner wrasse (station + whole tank), cardinal (shade pocket), tailspot (the hole farthest from the lawnmower).

**Rank ordering for feeding and chase outcomes** (who yields to whom at a food particle or in a face-off), highest first [I, informed by size, the "size > residency > history" result in S:Poulos16 and the aggression ratings in 02/04]: clown ♀ (at host) > dottyback (at cave) > lawnmower (on rock) > tang (grows past everyone within 6–12 months) > hawkfish > clown ♂ > cleaner wrasse (never chased sustainedly because of the cleaning role, S:Cheney08) > engineer goby > tailspot > cardinal. Outside its nucleus a holder drops two places.

### 3.2 Feeding competition
- The "frenzy" rush exists already (SIMULATION §4.6). Add **displacement**: when two fish target the same particle within 2 cm, the lower-ranked yields 70 % of the time; the dottyback "may dominate feeding areas" [S:H-FC-dotty]; faster tankmates leave the cardinal underfed [S:H-TSA-card] → the cardinal's `Feed` score by day is ×0.4 unless food drifts within 10 cm of its hover point, and ×1.5 at dusk/night when it forages naturally [S:Marnane02 → I].
- The lawnmower's foraging dips when larger herbivores invade [S:H-TFH-LMB] → on a shared patch its bite rate ×0.7.
- The cleaner wrasse's hunger is serviced mainly by inspections (0.5 parasites per inspection wild, S:Grutter96; in the tank each honest inspection yields hunger −0.01, each ich-laden inspection −0.08, prepared food as before).
- Surface feeding: clowns and tang rise first; the hawkfish strikes drifting particles passing within 15 cm of its perch; the dottyback darts from the cave to particles sinking past the ledge; blennies take settled food; the goby takes particles settling near burrow exits [I].

### 3.3 Newcomer aggression (priority effects)
- Residents only 3 h ahead were more aggressive and pushed intruders away from shelter; aggression is strongest between animals adjacent in the hierarchy; low-condition intruders die more [S:Poulos16]. The dottyback attacks newcomers immediately [S:H-FC-dotty].
- Spec: every organism has `arrivalTime`. For a pair (resident, newcomer) with `familiarity` = 0 the resident's aggression score is ×2.0 (dottyback ×3.0) for the first 24 h, decaying as `familiarity` grows 0.15/day (so baseline by ~day 7) [I; the 3 h result in Poulos16 implies the effect is fast to arise]. A newcomer with `health` < 0.6 has `S_social` gain ×1.5.
- Rock rearrangement (`RearrangeRock` command) resets all territory anchors and sets every `familiarity` to 0.5 (not 0: individuals still recognise each other, S:Johnson69/S:Tebbich02 for recognition) [I].

### 3.4 Crowding in a 60 gal
- 9 fish in ~227 L (~0.5 m² footprint) with three territory nuclei whose natural scales are m² (blenny 0.79 m² [S:Mendes14]; surgeonfish 12–17 m² [S:Sohal]) means territories overlap by construction. Agonism is lower in structurally complex habitat [S:Mendes14]; crowding aggression in an aquarium is driven by "limited space and mixed species" [S:Cheney08].
- Spec: `crowding = Σ_i (BL_i)² / footprint` (dimensionless). Baseline ≈ (2×7² + 7² + 6² + 9² + 10² + 6² + 10² + 5² + 7²) / 5600 cm² ≈ 0.11 at current sizes [D]. Aggression scores × (1 + 2 × (crowding − 0.11)) and `coverScore` requirement for hiding relaxed as crowding rises; when the tang reaches 15 cm the term alone adds ~+0.03 → +30 % aggression tank-wide [D]. Below is the sandbox "grow fish" story: cramped tang → more turning, more flashing at the glass, more cleaner visits.
- Chronic `S_social` in the two most-chased fish (tailspot, cardinal) should push health down over weeks, matching the "stress and exhaustion" mortality route [S:H-FC-dotty].

### 3.5 Cleaner as social buffer
With the wrasse active and healthy, tank-wide chase counts should be ~⅓ of the counts without it [S:Cheney08]. This is the single largest emergent effect and is a dedicated acceptance test (§5.6).

---

## 4. Water-quality stress and behaviour (links to 05_parameter_effects.md)

Mapping from the 05 bands to behaviour multipliers. Where 05 gives only physiology, the behavioural leg is inferred and marked.

| Parameter (05 §) | Band | Behavioural effect | Sim hook | Evidence |
|---|---|---|---|---|
| Ammonia (05 §4) | TAN ≥ 1 mg/L (NH₃ ≥ ~0.05) | Feeding voracity and satiety fall from the lowest tested level (1 mg/L TAN); swimming activity reduced at ~0.2–1 mg/L TAN in salmonids; refuge time increases in pre-exposed fish | `Feed` score ×0.5, `activity` ×0.7, `Hide` +0.2; aggression ×0.6 (lethargy) | S:Ronces21 (+ cited literature); aggression leg [I] |
| Ammonia | NH₃ ≥ 0.5 mg/L | gasping, mortality 1–3 d (05 §18) | `SurfaceGasp`, all social behaviours off | 05 |
| Nitrite (05 §5) | ≥ 0.5 mg/L NO₂-N | methemoglobin → hypoxia-like behaviour | treat as DO − 2 mg/L equivalent | 05 → [I] |
| Dissolved O₂ (05 §8) | 3 mg/L | column fish swim to the surface frequently; benthic fish stay in shelter/lose mobility; avoidance of 1 mg/L by all | DO 3–4: `SurfaceGasp` for clowns/tang/wrasse 0.4; blennies/hawk/dottyback `Hide` 0.6, chases off; DO < 2: gasp 0.95 | S:Yu23; thresholds "hypoxia < 2, sublethal to 4" [S:Yu23] |
| Temperature (05 §1) | 30–31 °C | reduced appetite, gasping | `Feed` ×0.6, tang ich onset ↑, aggression ×1.2 (warm-water irritability) | 05; aggression leg [I] |
| Temperature | ≥ 32 °C | acute stress | all territorial behaviours off; everyone hides or gasps | 05 |
| Salinity swing (05 §2) | SG drop 0.004/h | stress | `S_env` ↑; dottyback/blenny patrol radius ×0.5 for 24 h; cardinal stays hidden | 05 → [I] |
| pH (05 §3) | < 7.7 | stress over weeks | wrasse inspection rate ×0.7; coral retraction reduces clown host hover (clowns wander more) | 05 → [I] |
| Nitrate/phosphate (05 §6–7) | NO₃ > 30, PO₄ > 0.3 | hair-algae bloom | grazers (tang, lawnmower, astrea) spend +30 % time grazing; lawnmower–tang patch conflicts ↑; blenny chase probability ×0.7 (well fed) | 05 + S:H-TFH-LMB |
| Light (05 §14) | lights-on step | diurnal flinch; cardinal retreats deeper; cleaner shrimp leg-rocks on sudden darkness | existing `LightsChanged`; add shrimp leg-rock | S:Caves19 |
| Flow loss (05 §15–16) | powerheads off | tang loses surfing spots; hawkfish shuffles to a sheltered perch less; Sally Lightfoot hides (prefers strong current) | zone biases | 05, S:H-LA-sally |
| Copper (05 §12) | ≥ 0.1 mg/L | invert death cascade; cleaner stations vanish → wrasse hunger ↑ → cheating ↑ → clients chase it | invert kill switch already; add station removal | 05 → chain [I] |
| Disease: ich (05 §1, 02 §2) | stage > 0.1 | flashing, rapid breathing, lethargy, hanging at surface/bottom; cycle 1–2 weeks, trophont 3–7 d | `Flash`, gill rate ×1.5, `VisitCleaner` ×3, `Patrol` ×0.5; neighbours' cleaner demand rises as theronts spread | S:UF-FA164 |

General rule: any `S_env` > 0.5 halves every *initiated* aggressive behaviour (lethargy) but raises *received* stress gains ×1.5 (a stressed fish is harmed more by harassment) [I]. This keeps "sick tank" from looking like "angry tank".

### 4.1 Per-species parameter-change response model (for live "change tests")

This subsection turns the dose tables in 05 into **response curves with time constants** that the sim can integrate every behaviour tick on a phone. It builds on 05 and does not restate its thresholds; where 05 gives a level, this gives *how fast* the animal gets there, *what you see*, *how it recovers*, and *how it changes who it is in the tank*.

#### 4.1.1 Core model (cheap enough for a phone)

Every organism carries two scalar stress states per parameter channel plus an acclimated setpoint. All are first-order exponential filters, so the cost is a handful of multiply-adds per organism per tick (≈ 25 organisms × ~45 floats; negligible next to steering).

```
for each parameter p in (temp, sal, pH, alk, NO3, PO4, NH3, NO2, DO, light, flow):
  dev_p   = (perceived value_p − A_p) / (w_p × sens_p[species])       // A_p = acclimated setpoint; w_p = 05 band half-width
  drive_p = smoothstep(0, 1, |dev_p|) ^ k_p                           // k_p = 2 for most channels, 1 for DO and NH3 (steeper)
  S_fast_p += (drive_p − S_fast_p) × dt / τ_rise_p   while drive_p > S_fast_p   // acute, cortisol-like
  S_fast_p += (0 − S_fast_p)       × dt / τ_fall_p   otherwise
  S_slow_p += (S_fast_p − S_slow_p) × dt / τ_slow_p                     // chronic load / damage proxy
  A_p      += (value_p − A_p) × dt / τ_accl_p, clamped to the species' acclimation limits
S_env = 1 − Π_p (1 − max(S_fast_p, S_slow_p))                            // combine as independent risks, not a sum
```

Time constants (sourced where possible, else inferred; all species unless a row says otherwise):

| Channel | τ_rise (acute) | τ_fall | τ_slow (chronic) | τ_accl (setpoint) | Acclimation limit | Evidence |
|---|---|---|---|---|---|---|
| Temperature | 1 h | 12 h | 3 d | 7 d (metabolic: SMR back to control after ~1 week) plus a 30 d filter for full acclimation (aerobic scope plateaus at 4–5 weeks) | partial to +3 °C in resilient species; cardinalfish-type species fail (> 50 % mortality at +3 °C over 5 weeks) [S:Dabruzzi21]; hypoxia tolerance does **not** acclimate in 7–22 d [S:Nilsson10] | S:Dabruzzi21, S:Nilsson10 |
| Salinity | 2 h (plasma osmolality disturbed within 2 h) | 24 h (back within 24 h) | 8 d (new steady state ~8 d; cortisol back at 24 h for moderate, 192 h for extreme steps) | 8 d | ≤ 0.001 SG/day tolerated (05 §2) | S:Mancera02 |
| pH / CO₂ | 4 h | 24 h | 14 d (2–3 weeks at 7.7 for measurable harm, 05 §3) | 14 d | none for behaviour: CO₂ behavioural impairment persisted under continuous exposure [S:Munday14] | 05, S:Munday14 |
| Alk, Ca, Mg | — (fish) | — | 7 d (corals, snails) | 14 d | — | 05 §9–10 |
| NO₃, PO₄ | — direct | — | indirect via algae (weeks) | — | — | 05 §6–7 |
| NH₃ (free) | 30 min | 48 h | 7 d (gill damage heals over weeks, 05 §4) | none | none | 05 §4, S:Ronces21 |
| NO₂ | 2 h | 72 h (methaemoglobin reverses over days) | 7 d | none | none | 05 §5 |
| DO | 5 min | 1 h ("minutes to hours", 05 §8) | 24 h | none | none | 05 §8 |
| Light (fish) | 1 min flinch | 10 min | — | 2 d (photoperiod shift) | — | SIMULATION §4.7 |
| Light (coral) | rETRmax shifts within 1 day; Ek by ~3 weeks; symbiont density ~6 weeks (cited) | — | 21 d | 21 d | ramp over 1–2 weeks avoids shock (05 §14) | S:Wangpraseurt19 |
| Flow | 15 min (station loss) | 1 h | 24 h (detritus, DO dips) | — | — | 05 §15 |

**Acute cortisol envelope** for handling-type stressors (net chase, lights-on, glass tap, being chased): plasma cortisol is elevated from ~10 min, maximal at ~4 h, back to baseline by 24 h [S:Mathew23]; after a salinity transfer the first sample at 2 h was already ~6× baseline and fell by 4 h [S:Mancera02]. Use τ_rise 1 h / τ_fall 8 h for the `S_social` and `startle` channels so repeated chases within a day accumulate (the "stress and exhaustion" death route in §2.4) [D from the above].

**Ramp vs step falls out of the model.** `drive_p` is computed against the acclimated setpoint `A_p`, so a ramp slower than τ_accl produces little `S_fast` and simply moves the setpoint, while a step of the same size saturates `S_fast` at once. This one mechanism reproduces the 05 rules "keep temperature swings under 1 °C/h", "salinity under 0.001 SG/day", "ramp light over 1–2 weeks" without special cases.

#### 4.1.2 Species sensitivity multipliers

`sens_p` scales the 05 band half-width per species (1.0 = the band as written; > 1 = more sensitive). Values [I] unless noted; they encode the hardiness ranks in 02/04 §0.

| Species | temp | sal | pH | NH₃ | NO₂ | DO | light | flow | notes |
|---|---|---|---|---|---|---|---|---|---|
| clowns | 0.9 | 0.9 | 0.9 | 1.0 (maroon LC50 proxy, 05 §4) | 0.9 | 0.9 | 0.8 | 0.8 | hardy; ammonia histology sourced |
| tang (juv) | 1.3 | 1.1 | 1.0 | 1.2 | 1.1 | 1.3 | 1.0 | 1.1 | stops feeding > 28 °C (02 §2); ich-prone; "stress in tangs and cardinals" at DO 4–5 (05 §8) |
| cardinal | 1.3 | 1.0 | 1.0 | 1.2 | 1.1 | 1.4 | 1.5 (bright light is itself a stressor) | 0.9 | apogonid Pcrit +71 % at +3 °C [S:Nilsson10]; cardinalfish > 50 % mortality at +3 °C [S:Dabruzzi21] |
| cleaner wrasse | 1.2 | 1.1 | 1.1 | 1.2 | 1.0 | 1.2 | 0.9 | 0.9 | low reserves; starvation compounds (02 §4E) |
| engineer goby | 0.8 | 0.9 | 0.9 | 1.0 | 1.0 | 1.1 (burrow water lags tank DO by ~30 min) [I] | 0.7 | 0.8 | hardy |
| hawkfish | 1.0 | 1.0 | 1.0 | 1.0 | 1.0 | 1.0 | 0.9 | 0.9 | perched, low activity |
| lawnmower blenny | 0.9 | 0.9 | 0.9 | 1.0 | 1.0 | 1.1 | 0.8 | 0.9 | hardy |
| tailspot blenny | 0.9 | 0.9 | 0.9 | 1.0 | 1.0 | 1.1 | 0.8 | 0.9 | hardy, small |
| dottyback | 0.9 | 0.9 | 0.9 | 1.0 | 1.0 | 1.0 | 0.8 | 0.9 | lethargic < 20 °C (05 §1) |
| skunk shrimp, CBS | 1.3 | 1.6 (0.002 SG/h, 05 §2) | 1.3 | 1.3 | 1.3 | 1.4 | 0.6 | 1.0 | molt failure is the chronic channel |
| hermits, Sally Lightfoot | 1.1 | 1.4 | 1.2 | 1.2 | 1.2 | 1.3 | 0.6 | 0.8 | — |
| snails, asterinas | 1.1 | 1.2 | 1.4 (shell) | 1.2 | 1.1 | 1.2 | 0.6 | 0.7 | soft shell < pH 7.8 (05 §3) |
| brittle star | 1.1 | 1.4 | 1.2 | 1.2 | 1.1 | 1.3 | 0.5 | 0.8 | — |
| soft corals (Kenya, GSP, clove, organ pipe, mushrooms) | 1.0 (DHW model) | 1.2 | 0.9 | 0.7 | 0.7 | 0.8 | 1.0 | 1.2 | organ pipe 25–28 °C (04 §12); xeniid pulsing fell 17/26/32 % at pH 8.0/7.8/7.6 [S:Xenia23] |
| zoas | 1.0 | 1.1 | 0.9 | 0.7 | 0.7 | 0.8 | 1.1 | 1.1 | close under stress; hobby-grade [I] |

#### 4.1.3 Visible signs per level, per group

Signs are driven by the `S_fast`/`S_slow` of the responsible channel so that the *cause* is readable from the *symptom mix*: a heat-stressed tank breathes fast first, an ammonia tank clamps and hides first, a dark tank just sleeps.

| Sign (render / behaviour hook) | Driver | Onset | Mapping | Evidence |
|---|---|---|---|---|
| Breathing rate (`gill` uniform Hz) | temperature (Q10 ≈ 2–3, 05 §1), DO, NH₃, NO₂, ich | minutes | `gillHz = 1.0 × Q10^((T−26)/10) × (1 + 1.5·S_fast_DO + 1.0·S_fast_NH3 + 0.5·ich)` with Q10 = 2.5; base 1.0 Hz [I]; ich "rapid breathing" [S:UF-FA164] | 05, S:UF-FA164 |
| Surface gasping (`SurfaceGasp`) | DO below `DO_crit(T)` (4.1.4), NH₃ ≥ 0.25 | minutes | column fish (clowns, tang, wrasse, cardinal at night): score 0.4 at S_fast_DO 0.5, 0.95 at DO < 2; benthic fish (blennies, hawk, dottyback, goby) instead `Hide` 0.6 — at 3 mg/L a filefish surfaced repeatedly while rockfish stayed in shelter [S:Yu23] | S:Yu23, 05 §8 |
| Hiding / reduced patrol radius | NH₃, NO₂, salinity step, cold, any S_env > 0.5 | 30 min–2 h | `homeRadius × (1 − 0.6·S_env)`, `Hide` += 0.5·S_env; ammonia-history fish spent more time in refuge [S:Ronces21] | S:Ronces21 |
| Appetite loss | NH₃ ≥ 1 mg/L TAN, temp > 28–30, DO < 5, salinity step | 1–24 h | `Feed` × (1 − 0.7·S_fast_NH3)(1 − 0.5·S_fast_temp)(1 − 0.5·S_fast_DO); voracity fell from 1 mg/L TAN [S:Ronces21]; tang stops feeding > 28 °C (02 §2) | S:Ronces21, 02 |
| Clamped fins (`finClamp` uniform) | NH₃, NO₂, cold, ich, cardinal in bright light | hours | `finClamp = clamp(0.8·S_slow_NH3 + 0.6·S_fast_cold + 0.5·ich + [cardinal]·0.6·(light>0.6 ∧ cover<0.3))`; clamped fins at NH₃ 0.25–0.5 (05 §4), ich, cardinal light stress [S:H-TSA-card] | 05, S:H-TSA-card |
| Colour loss / darkening | chronic stress, night, ich | hours–days | fish `desat += 0.4·S_slow`; tang `blueDeepen` on acute social stress (deeper blue, black fades) [S:ADW-tang]; dottyback "faded colour" as a warning sign [S:H-FC-dotty] | S:ADW-tang, S:H-FC-dotty |
| Flashing / scratching (`Flash`) | ich; high NH₃ (gill irritation) | hours | 2–10/h at ich stage 0.3; 1–3/h at S_fast_NH3 > 0.5 [I]; "flash (scratch)" [S:UF-FA164] | S:UF-FA164 |
| Lying on the bottom / tilted | DO < DO_crit, NH₃ ≥ 0.5, salinity shock | hours | `Rest` with pitch/roll 20–40°, amp 0.1 (05 §2, §4) | 05 |
| Erratic swimming | NH₃ late stage, nitrite | hours–days | wander jitter ×3, random 0.3 s bursts (05 §4) | 05 |
| Polyp retraction (soft corals, feather duster) | flow loss, temp > 30, salinity step, light step, NH₃, food dosed over them, contact | minutes (mechanical, food, contact) to hours (chemical) | `extension = 1 − max(0.9·S_fast_flow, 0.7·S_fast_temp, 0.8·S_fast_sal, 0.6·S_fast_light, 0.5·S_fast_NH3, contact)`; recovers with τ_fall; feather duster retracts on food (04 §15) | 05 §14–16, 04 |
| Pulsing rate (clove polyps, Kenya tree tips) | pH | days | ×0.83 at pH 8.0, ×0.74 at 7.8, ×0.68 at 7.6 relative to 8.3 [S:Xenia23] | S:Xenia23 |
| Bleaching (`paleness`) | DHW accumulator (05 §1), light step, salinity step | days–weeks | `paleness = clamp(DHW/8) + 0.3·S_slow_light`; recovery weeks–months; tissue loss permanent above 8 DHW (05 §1) | S:NOAA-DHW via 05 |
| Zoa / mushroom closure | light step, flow loss, nutrient spike, being walked on (hermits, Sally, snails) | minutes | closed while contact or S_fast_light > 0.3; reopen 10–60 min after [I] | hobby |
| Shrimp lethargy, failed molt | salinity, NH₃, copper, pH | hours (lethargy) / next molt (failure) | molt success p = 1 − 0.8·max(S_slow_sal, S_slow_NH3, S_slow_pH, S_slow_Cu); failed molt = death (05 §2, §4, §12) | 05 |
| Snail retraction / fall-off | copper, pH, salinity, NH₃ | minutes–hours | retracted while S_fast > 0.4; a retracted astrea on glass drops to the sand (hermits then attack it, §2.18) | 05 §12 |
| Leg-rocking (skunk shrimp) | sudden darkness / shadow | seconds | p 0.5 per lights-off or shadow event [S:Caves19] | S:Caves19 |

#### 4.1.4 Interaction effects (multiplicative, not additive)

1. **Heat × low O₂ (the dominant one).** Warming raises O₂ demand, lowers solubility (05 §1, §8) *and* raises the critical O₂ level: +3 °C raised Pcrit by 71 % in a cardinalfish and 23 % in a damselfish, with no acclimation over 7–22 days [S:Nilsson10]. Spec: `DO_crit(T) = DO_crit(29) × (1 + k·(T − 29))` with k = 0.24/°C for the cardinal and tang (sensitive) and 0.08/°C for clowns, blennies, dottyback, hawk, goby [D:Nilsson10]; `DO_crit(29)` = 3.0 mg/L fish, 4.0 mg/L shrimp/hermits (05 §8). The DO drive is computed against `DO_crit(T)`, not a fixed value, so a 31 °C night with the powerheads off kills the cardinal first. Night dip: `DO_night = DO_day − 0.3 × (coral + algae biomass index)` mg/L (05 §8 "several mg/L" in heavy tanks) [I].
2. **Heat × ammonia × pH.** NH₃ fraction of TAN rises ~20× from pH 7.4/26 °C to pH 8.8/28 °C (05 §4, Boyd). The behavioural NH₃ channel must read *free* NH₃ (SIMULATION §2.9), so a heater spike during a cycle hiccup is worse than either alone.
3. **Heat × ich.** The parasite cycle shortens (7 d at 27 °C, 05 §1); trophont feeding 3–7 d, theront release 4–8 d after tomont formation [S:UF-FA164]; heat also lowers immunity (HealthModel §3.4). At 30 °C the tang's ich stage grows ~1.5× faster [I] and its `Flash`/`VisitCleaner` rise accordingly; neighbours' cleaner demand rises as theronts spread.
4. **Heat × aggression (biphasic).** 4 days at +1–2 °C tended to *raise* damselfish aggression; after 90 days aggression was *lower* than at 4 days (a non-adaptive decline) and interspecific dominance partially flipped toward the more heat-tolerant species [S:Warren16]. Spec: aggression multiplier `× (1 + 0.3·S_fast_temp) × (1 − 0.5·S_slow_temp)` — a short heat spike makes the dottyback, lawnmower and clown female more irritable; chronic heat makes them lethargic and shrinks rank gaps.
5. **Low flow × heat × night.** Flow loss removes surface gas exchange exactly when solubility is lowest; the 30 min / several-hour thresholds in 05 §15 should act through the DO integrator, not as separate stress events.
6. **Salinity step × molt.** Shrimp within 48 h of a molt (soft) take ×2 salinity/NH₃ drive [I; 05 §2 "shrimp may fail to molt"].
7. **CO₂ / low pH × behaviour.** Fish at CO₂ seeps were bolder and attracted to predator odour, with no metabolic change, and continuous exposure did not reduce the effect [S:Munday14]. Spec: at pH < 7.8 sustained, `skittishness × 0.6` and `Flee` radius × 0.7 for all fish (they wander farther from cover; the cardinal hovers more exposed). This is the one channel where "stress" shows as *more* exposure, not less.
8. **Starvation × cleaning.** A hungry wrasse cheats more (§2.11) and the tank-wide aggression damping fades; a hungry lawnmower chases more (§2.7). Both are hunger-driven already, so water quality that suppresses feeding has a second-order social effect for free.

#### 4.1.5 Stress and social behaviour (rank, cleaning, hiding)

- **Subordinates carry higher basal cortisol** (meta-analysis of 72 studies, 22 species), with the gap larger in dyads and in larger-bodied species [S:Bessa21]. This is why chronic `S_social` is a health input and why the two bottom-rank fish (tailspot, cardinal) are the first to show `finClamp`/`desat` in a crowded or newly rearranged tank.
- **Effective rank** `rank_eff = rank − 2·S_env − 1·S_slow_social` (continuous). Chase outcomes (§3.1) use `rank_eff`, so a sick dottyback loses standoffs to the clown female and a heat-stressed lawnmower stops winning grazing patches. Rank recovers as stress decays; no permanent loss unless the animal dies or a growth change crosses the clown 1.26 ratio.
- **Initiated aggression** falls with `S_env` (lethargy, §4 general rule) except for the biphasic heat case; **received stress** rises ×1.5 [I].
- **Cleaning demand rises with parasite load**: inspection frequency and duration scale with ectoparasite load [S:Grutter95]; ich-infected clients get ×3 client weight (§5.5) and self-initiate `VisitCleaner` at `0.15 + 0.6·ich` (existing). Add: a fish with `S_slow_NH3` > 0.4 (gill irritation) raises its visit score by +0.2 [I].
- **Shelter competition under stress**: when DO or NH₃ stress pushes benthic fish into cover [S:Yu23], the dottyback's cave and blenny holes become contested; apply the priority effect (§3.3): the holder keeps it, the intruder is displaced at the normal chase rate with `stressToTarget` ×1.5.
- **Stress-driven use of the cleaner shrimp**: fish resting near substrate under hypoxia pose at the ledge more (skunk shrimp cleaning opportunities ×1.5 while DO stress > 0.3) [I; posing = resting near substrate in S:Caves19].

#### 4.1.6 Recovery and acclimation summary (what the user sees after fixing the problem)

| Event fixed | Visible recovery | Time course | Evidence |
|---|---|---|---|
| DO restored > 5 mg/L | gasping stops, breathing slows, feeding resumes | minutes–1 h | 05 §8 |
| NH₃ spike < 0.5 ended (water change) | hiding and clamping ease in 1–2 d; appetite in 2–3 d; `S_slow` (gill) lingers 1–2 weeks; invert molting resumes next cycle | days–weeks | 05 §4 |
| Temperature back to 26 °C after a 30–31 °C day | breathing normal in hours; `S_slow_temp` decays over 3 d; ich already seeded continues its 7-day cycle | hours / 1 week | 05 §1, S:UF-FA164 |
| Temperature back after a 2–5 week excursion | metabolic setpoint re-acclimates over ~1 week; aerobic scope over 4–5 weeks; corals regain colour over weeks–months | 1–5 weeks | S:Dabruzzi21, 05 §1 |
| Salinity corrected after a 0.008 SG step | osmolality normal in 24 h; cortisol-type stress gone in 24 h (moderate) to 8 d (extreme) | 1–8 d | S:Mancera02 |
| pH restored from 7.7 | fish 1–2 d; coral calcification days–weeks; soft-coral pulsing over ~1 week [I] | days–weeks | 05 §3 |
| Light step reversed | polyps re-extend in hours; photochemistry in 1 day; pigment in weeks | hours–weeks | S:Wangpraseurt19 |
| Flow restored | fish re-station in minutes; polyps re-extend within 1 h; detritus/cyano clear over days–weeks | minutes–weeks | 05 §15 |
| Harassment stopped (rock rearranged or aggressor removed) | victim's hover radius and colour recover over 1–3 weeks; `S_slow_social` τ 7 d | 1–3 weeks | [I], S:H-FC-dotty |

### 4.2 Sandbox controls the sim is missing for realistic change tests

The current `Commands` (SIMULATION §1.3) and presets (§7) allow setting values and a few scripted failures. For the response model above to be testable in a live control the following are needed. All are cheap: they are scheduler entries that write to existing parameter setters.

| Control | Why | Spec |
|---|---|---|
| **Ramp vs step** per parameter | the whole acclimation model hinges on rate of change | `SetParam(p, target, rampDuration)`; `rampDuration = 0` is a step; UI shows the rate in units/h against the 05 safe-rate guideline |
| **Hold-then-revert schedules** | recovery curves need a defined return | `Schedule(p, [(t0, v0, ramp), (t1, v1, ramp), …])`; presets "1-hour spike", "3-day excursion", "4-week drift" |
| **Time compression with a fidelity flag** | a 4-week acclimation at 1× is unwatchable | `timeScale` 1×–1000×; above ~60× the behaviour tick runs at 1 Hz sim-time (two-rate stepping already exists, §1.2), the social `Bout` machine is frozen but stress/acclimation integrators continue; the UI labels "fast-forward: behaviour summarised" |
| **Night DO dip toggle** and **flow failure at a chosen clock time** | the heat × O₂ × night interaction only shows when the failure can be timed | `PowerheadFail(at = 23:00, duration = 6 h)`; `nightDipEnabled` |
| **Free-ammonia readout** (not just TAN) | the behavioural channel reads NH₃ | derived display beside pH/temp so the user sees the ~20× pH effect |
| **DHW accumulator gauge** | coral bleaching is cumulative and otherwise invisible | per-colony DHW bar; decays during cool-down |
| **Water change** command | the main real-world recovery action | `WaterChange(fraction, newWaterParams)` mixes chemistry linearly and applies a small salinity/temperature step from any mismatch |
| **Per-organism stress inspector** | the user must see `S_fast / S_slow / A_p` per channel to understand what they are watching | tap-to-inspect panel: 11 channel bars + `rank_eff` + `cleanerTrust` + `familiarity` row |
| **Parameter lock / isolate** | tests one variable at a time | freeze all other chemistry (no nitrogen-cycle drift during a heat test) |
| **Rock rearrange**, **remove / add organism** | newcomer and territory tests (§5.6 tests 6, 7, 20) | `RearrangeRock`, `RemoveOrganism`, `AddOrganism(species, size)` with `arrivalTime` |
| **Seeded A/B replay** | compare runs | expose the xorshift seed (§4.9); run the same seed with one schedule changed, side by side or overlaid counters |
| **Observation-window metrics** | acceptance checks need counts | rolling 1 h counters: chases, poses, jolts, flashes, gasps, feed completions, time-in-cover per organism; CSV export from the sandbox |
| **Acclimated-setpoint reset** | "this fish has lived at 27.5 °C for a month" | `SetAcclimation(organism, p, value)` or "acclimate all to current" |
| **Reality guard** | Reality mode never kills Tee's animals (§5.3 rule) | lethal rolls → `NearMiss` toast; sandbox shows real outcomes |

#### Phone-CPU budget
- Stress integrators: 25 organisms × 11 channels × 4 floats, updated on the behaviour tick (4–10 Hz): ~1,100 multiply-adds per tick.
- Pair matrix: evaluate only pairs returned by the existing 15 cm spatial hash plus territory-anchor checks for the three holders; typically < 40 pair checks per tick.
- Bouts are scripted 1–3 s sequences reusing `seek`/`arrive`, no path-finding; at most 2–3 run concurrently.
- Social memory: 25 × 25 × 5 floats, decayed lazily on read using `lastInteractionAt`.
- Corals: one `extension`, `paleness`, `pulseRate` scalar per colony at 1 Hz.
- Fast-forward: freeze bouts and render-only signs, keep integrators; 1000× playback stays O(organisms × channels) per sim-second.

---

## 5. Implementation spec for the utility-AI simulation

All names are proposals against SIMULATION.md §4. Units: cm, s, BL, probabilities per event or per hour as stated. Every default is tagged with its evidence grade in the last column.

### 5.1 New shared state

```
Organism:
  rank: Int                      // 3.1 ordering; computed from species table + nucleus occupancy
  arrivalTime: SimTime
  territory: Territory?          // {anchorId, coreRadius, haloRadius, targetFilter}
  socialMemory: Map<OrgId, Memory>
Memory:
  familiarity ∈ [0,1]            // grows 0.15/day co-resident, see 3.3            [I]
  threat ∈ [0,1]                 // +0.3 per chase received, half-life 6 h          [I]
  intrusions: Float              // +1 per halo intrusion, half-life 24 h (2.2)     [S:Fleming24 → I]
  cleanerTrust ∈ [0,1]           // clients only; +0.05 honest, −0.2 jolt (2.11)     [S:Pinto11 → I]
  lastInteractionAt: SimTime
Tank:
  crowding: Float                // 3.4
  aggressionDamp: Map<OrgId, Expiry>  // set by tactile cleaning, 0.35 for 20 min  [D:Cheney08]
```

### 5.2 Per-species parameter table

| Species | cruise BL/s | burst BL/s | burst max s | zone (y cm) | home radius cm | territory core / halo cm | target filter | aggression base | skittishness | sleep anchor | evidence |
|---|---|---|---|---|---|---|---|---|---|---|---|
| clown ♀ | 1.2 | 4.5 | 1.0 | 20–45 | 20 | 10 / 22 (host) | any non-partner fish lingering > 3 s | 0.6 | 0.3 | HOST_KENYA | speeds [I]; aggression ratio S:Fleming24 |
| clown ♂ | 1.2 | 4.5 | 1.0 | 20–45 | 22 | 10 / 22 (host) | same | 0.2 (= ♀ / 3.5) | 0.35 | HOST_KENYA | S:Fleming24 (3.5×) |
| tang (juv) | 1.8 | 7 | 0.7 | 25–50 | — (patrol lane x 15–105) | none | none; `spineFlare` when cornered | 0.1 | 0.5 | SLEEP_TANG | S:ADW-tang; speeds [I] |
| cardinal | 0.7 | 3.5 | 0.6 | day 25–40 / night 15–45 | day 10, dusk–night 40 | none | none | 0.0 | 0.7 | shade pocket (CAVE_SMALL) | S:Marnane02, S:H-TSA-card |
| cleaner wrasse | 1.8 | 5 | 0.8 | 10–40 | whole tank, station bias | station 15 / — | none | 0.05 | 0.4 | CAVE_SMALL (cocoon) | S:Grutter11; speeds [I] |
| engineer goby | 0.8 | 3 | 0.8 | 0–12 (night 0–25) | burrow 15 | burrow mouths 6 / 10 | "similar species" | 0.1 | 0.6 | BURROW_ENG | S:H-QM-eng, S:H-FK-conv |
| hawkfish | hop 3 | 7 | 0.4 | perches | perch cluster | 6 (1 BL) / 15 | fish/shrimp within strike cone | 0.35 | 0.4 | lowest PERCH | S:Kadota11, S:H-RG-hawk |
| lawnmower | 1.0 | 4 | 0.6 | rock surface | 30 | 10 / 25 | blennies 0.9, grazers 0.2, others 0.05 | 0.5 | 0.5 | HOLE_LMB | S:H-TFH-LMB |
| tailspot | 1.0 | 4.5 | 0.5 | rock surface | 12 | 5 / 12 | none (defends hole only) | 0.1 | 0.8 | HOLE_BLENNY | 04 §2, [I] |
| dottyback | 1.5 | 6 | 1.0 | 8–30 ledges | 20 | 10 / 25 | all intruders; ×2 for small/timid, ×0.3 for tang/wrasse-in-Clean | 0.7 | 0.3 | CAVE_DOTTY | S:H-FC-dotty |
| skunk shrimp | walk 0.15 | tail-flip 8 | 0.4 | SHRIMP_LEDGE | 10 (night 30) | — | — | 0 | 0.6 | ledge | 04 §4, S:Caves19 |
| CBS | walk 0.15 | tail-flip 8 | 0.5 | cave | 8 (night 25) | cave 5 / 10 | shrimp/hermit at cave | 0.4 | 0.5 | cave | S:Johnson69 |
| Sally Lightfoot | walk 0.5 | 2 | 1.0 | rock, high-flow faces | 30 (night) | none | small fish/snails if > 5 cm | 0.2 (× size) | 0.7 | crevice | S:H-LA-sally |

`skittishness` and sleep anchors not already in `anchors.json` need new anchors: `HOLE_LMB`, `CAVE_DOTTY`, `BURROW_ENG` (with `den_path` polyline of 6–8 points and 2–3 exits), `SHADE_CARD`.

### 5.3 Pair-interaction matrix (`sim/interactions.json`)

Columns: `type`, `triggerRadius` (cm, from the actor's anchor or body as stated), `p_event` (probability an eligible trigger produces a bout), `maxRate` (bouts/h cap after habituation), `bout` (display sequence id, §5.4), `chaseLen` cm, `chaseSpeed` BL/s, `stressToTarget` (S_social per bout), `habituation` (fraction of p_event removed at familiarity = 1), `newcomerMult`. All values [I] unless a source column entry says otherwise.

| actor → target | type | triggerRadius | p_event | maxRate | bout | chaseLen | chaseSpeed | stressToTarget | habituation | newcomerMult | source notes |
|---|---|---|---|---|---|---|---|---|---|---|---|
| clown♀ → clown♂ | dominance | 0.5 BL body | 0.3 | 6/h new → 2/h | `QUIVER_YIELD` | 10 | 4.5 | 0.01 | 0.6 | 1 | S:Colleye12 display form |
| clown♀ → any fish in halo | aggression | 22 (host) | 0.5 × sensitisation(1→2) | 20/h | `STARE_DISPLAY_CHARGE_NIP` | 15 beyond halo (5 for tang/wrasse) | 4.5 | 0.03 | 0.3 | 1.5 | S:Fleming24, S:Hayashi24 |
| clown♂ → same | aggression | 22 | 0.15 | 8/h | same | 10 | 4.5 | 0.02 | 0.3 | 1.5 | S:Fleming24 (÷3.5) |
| dottyback → tailspot/cardinal/goby-juv | aggression | 25 (cave) | 0.6 | 8/h → 1/h | `LATERAL_DISPLAY_CHASE_NIP` | 40 | 6 | 0.06 | 0.75 | 3 | S:H-FC-dotty |
| dottyback → wrasse (not in Clean) | aggression | 25 | 0.3 | 4/h | same | 25 | 6 | 0.03 | 0.75 | 2 | S:H-FC-dotty, S:Bshary01 |
| dottyback → clowns | standoff | halo overlap | 0.3 | 3/h → 0.5/h | `LATERAL_DISPLAY_DART` | 15 | 6 | 0.02 | 0.8 | 2 | S:H-reefs-fridclown |
| dottyback → lawnmower / hawk | standoff | halo overlap | 0.2 | 2/h | `MUTUAL_DISPLAY` | 10 | 5 | 0.01 | 0.8 | 1.5 | S:H-FC-dotty |
| dottyback → tang | aggression→tolerance | 25 | 0.12 × max(0, 1.5 − L_tang/L_dotty) | 2/h | `LATERAL_DISPLAY_CHASE` | 25 | 6 | 0.03 | 0.9 | 2 | S:H-FC-dotty |
| lawnmower → tailspot | aggression | 25 (hole) or shared patch | 0.9 × (0.5+0.5·hunger) | 12/h → 4/h (floor) | `DORSAL_RAISE_HOPCHARGE_NIP` | 25 | 4 | 0.05 | 0.55 | 1.5 | S:H-TFH-LMB (90 %) |
| lawnmower → tang on patch | competition | 10 (patch) | 0.15 | 3/h | `DORSAL_RAISE_NIP` | 5 | 3 | 0.02 | 0 | 1 | S:H-TFH-LMB (20 % damsel analogue) |
| lawnmower → dottyback/hawk/clowns | ignore | — | 0.05 | 1/h | `DORSAL_RAISE` | 0 | — | 0.005 | 0 | 1 | S:H-TFH-LMB "ignores non-trophic competitors" |
| hawk → wrasse/blenny/dotty on perch | perch defence | 6 (1 BL) | 0.5 → 0.1 | 6/h | `GAPE_CHARGE` | 8 | 7 | 0.02 | 0.8 | 1 | SIMULATION §4.5, [I] |
| hawk → cardinal | intimidation | 20 LOS | 0.3 | 4/h | `ORIENT_HOP` | 6 | 3 | 0.03 | 0.5 | 1 | S:H-TSA-card |
| hawk → skunk shrimp | predation risk | 10 strike cone | strike 0.2/h when visible; kill 0.05 day / 0.15 dusk | — | `STRIKE` | 15 | 7 | 0.1 | 0 | 1 | 04 §13B, S:Kadota11 |
| hawk → any fish < 0.5 L_hawk | predation | 15 | 0.3/h | — | `STRIKE` | 15 | 7 | lethal p 0.3 | 0 | 1 | S:H-RG-hawk; none qualify now |
| wrasse → any fish | cleaning | 35 (station) or 1 BL encounter | see §5.5 | ~20–60/h by day | `DANCE_POSE_INSPECT` | — | — | −0.02 (relief) | — | — | S:Grutter96/04, S:Pinto11 |
| skunk shrimp → posing fish | cleaning | 5 (ledge) | predators 0.28, others 0.51 | 6/h | `ROCK_TAP_CLIMB` | — | — | −0.02 | — | — | S:Caves19 |
| CBS → skunk shrimp (molt window) | predation risk | 8 (cave, night) | 0.05 | 1/night | `CLAW_GRAB` | — | — | lethal | 0 | 2 (7 d) | S:Wong11, S:Johnson69 |
| CBS → hermit | aggression | 5 (cave) | 0.3 | 2/h night | `CLAW_RAISE` | 3 | — | hermit hides 30–60 s | 0 | 1 | [I] |
| Sally (> 5 cm) → sleeping cardinal/tailspot | predation risk | 5 path | 0.01/night | — | `GRAB` | — | — | lethal | 0 | 1 | S:H-LA-sally |
| Sally → snails | predation | 3 | 0.05/night | — | `FLIP_PICK` | — | — | lethal | 0 | 1 | S:H-LA-sally |
| hermit → astrea | shell predation | 2 | 0.02/day if needsShell | — | `SHELL_FIGHT` | — | — | lethal | 0 | 1 | 04 §5–6 |
| goby (adult, night) → sleepers | disturbance | 1 BL | 0.5 | — | `BRUSH` | — | — | sleeper shifts 2 BL | 0 | 1 | [I] |
| tang → dottyback/blenny when cornered | defensive display | 1 BL | 0.8 | — | `SPINE_FLARE` | 0 | — | actor veers | 0 | 1 | S:ADW-tang |

Reality-mode rule (SIMULATION §4.5 eel precedent): lethal outcomes are **sandbox-only**; in Reality mode a "lethal" roll produces a `NearMiss` event and a stress spike instead, so Tee's real inventory is never contradicted.

### 5.4 Display sequences (new `Bout` state machine, run inside the existing behaviours)

Each bout is a short scripted sequence that the actor's behaviour runs when `p_event` fires; the target gets a corresponding reaction. Durations [I] unless marked.

```
QUIVER_YIELD (clown ♂):        quiver 0.5–1.5 s (lateral sinusoid 8 Hz, amp 0.05 BL, head-first wave travelling tailward; S:Colleye12 for form)
                               → back away 0.5–2 BL at 1 BL/s → resume HoverHost
STARE_DISPLAY_CHARGE_NIP:      orient 0.5 s → lateral display 1 s (fins erect, slight roll toward target)
                               → charge to 0.5 BL at burst → nip 0.1 s if ≤ 0.3 BL → return to anchor
LATERAL_DISPLAY_CHASE_NIP (dottyback): hover-stop 0.5 s facing target → lateral turn, dorsal raised, head/tail shake 0.5–1 s (S:Wittenrich23 for form)
                               → chase ≤ chaseLen at 6 BL/s (≤ 1.5 s) → nip if ≤ 0.3 BL → return to cave
                               familiarity < 0.2: skip the display step (immediate attack; S:H-FC-dotty)
LATERAL_DISPLAY_DART / MUTUAL_DISPLAY: both parties lateral-display 1 s; 20 % dart 2 BL; no nip
DORSAL_RAISE_HOPCHARGE_NIP (lawnmower): dorsal up 0.3 s → hop-charge ≤ 25 cm at 4 BL/s → nip → hop back to perch
GAPE_CHARGE (hawk): head turn 0.2 s → 1 s charge ≤ 8 cm → settle 0.3 s
ORIENT_HOP: head turn → 1 BL hop toward target → settle
STRIKE: tilt 0.3 s → burst ≤ 15 cm at 7 BL/s (≤ 0.4 s) → capsule hit test → return to perch
SPINE_FLARE (tang): broadside turn 0.3 s, caudal spine extended (new posture uniform `spineFlare` 0→1 in 0.1 s), colour `blueDeepen` +0.3 (S:ADW-tang), hold 0.5–1 s
DANCE_POSE_INSPECT (wrasse): see §5.5
ROCK_TAP_CLIMB (skunk shrimp): leg-rock 1–3 s (forelegs 4 Hz) [predators only, p 0.51] → antenna tap → climb, pick 10–60 s → dismount
FLASH (sick tang): dart to rock, roll 40°, scrape 0.5 s (existing)
```

Target reactions: `Flee` impulse scaled by (actor rank − target rank) and `threat` memory; `Hide` score += stressToTarget × 5 for 60 s; cardinal/tailspot additionally set `hoverRadius ×0.7` for 10 min.

### 5.5 Cleaning loop (replaces the `VisitCleaner`/`RunStation` pair)

```
WrasseBrain.Work:
  candidate = argmax over fish within 40 cm of  w = area(BL²) × (1 + 3·ich + 0.5·recentPoses) × (1 − 0.5·threat[fish→wrasse])     [S:Grutter95 for area/parasite scaling]
  approach to 1 BL (1.8 BL/s)
  if candidate.rank ≥ hawk/dotty/lawnmower or candidate.hunger > 0.6:  TACTILE dance 1–2 s (pitch ±20°, 2 Hz, within 15 cm)   [S:Grutter04, S:Cheney08]
     → candidate.aggressionDamp = 0.35 for 20 min                                                                                   [D:Cheney08]
  candidate poses with p = 0.3 + 0.6·cleanerTrust (fins spread, mouth open, pitch +25°, drift)                                       [S:Grutter94, I]
  if posed: INSPECT for t ~ lognormal(mean 6.7 s, sd 4 s), clamp 2–20 s; nibble 1.5 Hz along a 4-point spine path                  [D:Grutter96]
     per second: honest (p = 1 − cheat) → wrasse.hunger −0.002 (−0.015 if ich); cheat → client JOLT
     cheat rate: 0.04/s baseline (= 2–6 per 100 s) rising to 0.17/s when wrasse.hunger > 0.6                                         [S:Pinto11]
     on JOLT: client leaves (p 0.7) or chases 1–2 BL (p 0.3; residents); cleanerTrust −0.2                                           [S:Pinto11, S:Bshary01]
  cooldown per client: 60 s (not 10 min); the same client may be inspected many times per hour                                      [D:Grutter96, S:Grutter94 (32 min/day)]
  station return: after each bout, 50 % return to STATION_CLEANER, 50 % continue Surf                                                [I]
  ich effect: each inspection of a fish with ich stage s reduces s by 0.02 (existing)                                                  existing
Night: SleepCocoon as existing; arousal threshold ×2.                                                                                [S:Wiki-sleep]
```

Expected emergent rates in this tank by day: 20–60 inspections/h total; tang 1–3 full poses/h plus several brief passes; clowns 1–2/h; dottyback/hawk mostly tactile-only [D from the weights; the acceptance band in §5.6 is deliberately wide].

### 5.6 Acceptance checks (deterministic seeds, 10 h sim day, healthy water unless stated)

1. **Clown pair hierarchy.** Male performs `QUIVER_YIELD` 1–6 times/h; the female never does; male is within 0.5–2 BL of the female ≥ 70 % of day samples; 95 % of both within 3 BL of the host (existing test retained).
2. **Host defence sensitisation.** Script the dottyback to enter the host halo 3× in 10 min: the female's bout latency on the third entry is ≤ 40 % of the first (S:Fleming24 60–76 %); her bouts per entry ≥ 1.5× the first.
3. **Cleaner services the tang.** By day, the tang receives ≥ 1 and ≤ 3 posed inspections per hour on average over 10 h; mean inspection length 4–10 s; total tang cleaning time 2–10 min/day (wild heavy client: 32 min/day, S:Grutter94; tank should be lower).
4. **Cleaner cooldown.** No client goes > 90 min by day without at least a tactile pass.
5. **Cheating responds to hunger.** With `acceptsPrepared=false` and no ich in the tank, wrasse hunger climbs past 0.6 within 3 days and the jolt rate across clients rises ≥ 3× versus day 1 (S:Pinto11 ~5×); clients' `cleanerTrust` falls below 0.4 and posing probability drops accordingly.
6. **Cleaner as buffer.** Run 10 h with and without the wrasse (sandbox remove): total chase bouts without the wrasse ≥ 2× those with it (S:Cheney08 "two-thirds less"); tolerance band 1.6–4×.
7. **Dottyback newcomer attack.** Add the cardinal at t=0 with a resident dottyback: ≥ 3 chases in the first hour; after 7 sim days ≤ 1 chase/h; the cardinal's daytime hover radius is ≤ 10 cm in week 1 and recovers to its baseline ± 20 % by week 3 only if the dottyback's cave is > 40 cm from the cardinal's shade pocket.
8. **Dottyback standoff.** Dottyback–clown bouts fall from ≥ 2/h on day 1 to ≤ 0.5/h by day 7, and ≥ 80 % of day-7 bouts are display-only (no nip).
9. **Blenny war.** Lawnmower chases the tailspot on ≥ 60 % of tailspot entries into the lawnmower's halo on day 1 and still ≥ 35 % on day 14 (floor 0.4 × 0.9); the tailspot's home hole ends ≥ 50 cm from the lawnmower's hole by day 3; tailspot time-in-hole ≥ 40 % of daylight.
10. **Well-fed blenny is calmer.** With hair-algae cover ≥ 0.6 and hunger < 0.3, lawnmower chase probability per entry is ≤ 0.6× that at hunger 0.8.
11. **Hawkfish economy.** Hawk is perched ≥ 75 % of daylight samples; hops 5–20/h; never strikes a fish ≥ 0.5 × its length; charges the wrasse at its perch ≤ 1/h after day 3.
12. **Tang never bullies.** Tang initiates zero chases over 10 h; performs `SPINE_FLARE` only when a bout targets it; with ich stage 0.3 its `Flash` rate is 2–10/h and its cleaner visits ≥ 2× the healthy baseline (S:UF-FA164 signs).
13. **Cardinal diel.** Daytime: cardinal within 15 cm of its shade pocket ≥ 85 % of samples, clamped-fin posture when light > 0.6 and coverScore < 0.3. Dusk/night: forage radius ≥ 30 cm; ≥ 70 % of its daily particle intake happens at light < 0.3 (S:Marnane02 64–93 % dawn fullness).
14. **Engineer goby diel.** Adult: in burrow ≥ 80 % of daylight samples with head visible ≥ 50 % of those; out of burrow ≥ 30 % of night samples; sand particles ejected (burrow maintenance events) 5–20/day. Juvenile mode (< 10 cm): ribbon swimming near rock by day, hangs in burrow at night.
15. **Shrimp politics.** Skunk shrimp and CBS mean separation ≥ 25 cm over 24 h; a scripted skunk molt at night with the CBS within 8 cm yields a `NearMiss` (Reality) or `Eaten` (Sandbox) in ~5 % of 200 seeds.
16. **Skunk cleaner signalling.** Leg-rocking occurs in ≥ 40 % of hawk/dottyback approaches to the ledge and ≤ 20 % of tang/clown approaches (S:Caves19 51 % vs 12 %); the shrimp cleans ≤ 35 % of predator approaches and ≥ 40 % of non-predator approaches.
17. **Night predator map.** Sally Lightfoot at carapace 6 cm over 100 sim nights: 0–3 fish `NearMiss` events; at 3 cm: 0.
18. **Water-quality lethargy.** At NH₃ 0.1 mg/L (TAN ≈ 2 mg/L) for 24 h: tank-wide chase bouts ≤ 0.6× baseline and Feed completions ≤ 0.5× baseline (S:Ronces21); at DO 3 mg/L clowns/tang/wrasse spend ≥ 20 % of samples in the top 5 cm and blennies/hawk/dottyback ≥ 70 % in cover (S:Yu23).
19. **Crowding.** Sandbox-grow the tang to 15 cm: tank-wide aggression bouts rise 20–40 % versus the 7 cm baseline (D §3.4) and the tang's waypoint spacing shrinks (existing rule).
20. **Rock rearrangement.** `RearrangeRock` command: every territory re-anchors within 30 min sim time, bouts/h in the following 6 h ≥ 2× the pre-command rate, decaying to ≤ 1.2× by 72 h.
21. **No rock penetration / determinism.** Existing tests 7 and 9 continue to pass with all new bouts active.
22. **Ramp vs step (§4.1.1).** Raise temperature 26 → 29 °C as a 3 h step and as a 7-day ramp: peak `S_fast_temp` under the step ≥ 0.7 for the tang and cardinal, under the ramp ≤ 0.2; after 14 days both runs have `A_temp` within 0.3 °C of 29 and `S_slow_temp` ≤ 0.1 for clowns/blennies but ≥ 0.3 for the cardinal (fails to acclimate, S:Dabruzzi21).
23. **Heat × O₂ interaction (§4.1.4).** At 29 °C with DO 4.0 mg/L no fish gasps; at 32 °C with the same DO 4.0 the cardinal and tang gasp (`SurfaceGasp` > 0.4) within 30 min while clowns do not (Pcrit +71 % vs +23 %, S:Nilsson10). Add a timed powerhead failure 23:00–05:00 at 31 °C: the cardinal is the first `NearMiss`/`Died` in ≥ 80 % of seeds.
24. **Acute stress envelope.** After a single glass tap, `startle`/`S_social` of the tapped fish peaks within 1–4 h of sim time and is < 10 % of peak by 24 h (S:Mathew23); three taps an hour apart stack to ≥ 2× a single tap's peak.
25. **Biphasic heat aggression (§4.1.4 #4).** At 31 °C, tank-wide chase bouts in days 1–4 are ≥ 1.2× the 26 °C baseline; by day 60 they are ≤ 0.7× baseline (S:Warren16).
26. **Low pH boldness (§4.1.4 #7).** At pH 7.6 sustained 14 days, mean distance-to-cover of the cardinal by day rises ≥ 30 % and `Flee` triggers per tap fall ≥ 30 % (S:Munday14).
27. **Recovery ordering (§4.1.6).** After a 6 h NH₃ 0.3 mg/L spike and a 50 % water change: gasping ends within 1 h, `Feed` completions return to ≥ 80 % of baseline within 3 days, `finClamp` < 0.2 within 2 days, `S_slow_NH3` < 0.1 only after ≥ 7 days.
28. **Phone budget.** With 25 organisms and all stress channels active, the behaviour tick's stress + pair-matrix work is ≤ 5 % of the tick's wall time on the reference mid-range phone (measured, not estimated).

### 5.7 Behaviours and states to add (summary list for the implementer)

- `Bout` sub-state machine + `interactions.json` loader; `socialMemory` with decay; `rank` and `crowding` computation.
- New behaviours: `DefendTerritory` (dottyback, lawnmower, hawk-perch; replaces ad-hoc chase code), `Standoff` (mutual display), `SpineFlare` (tang posture), `NocturnalForage` (cardinal, goby, shrimps, crab), `BurrowMaintain` (goby, sand ejection events), `JuvenileRibbon` (goby < 10 cm), `LegRock` + `ClimbClean` (skunk shrimp), `ShellFight` (hermits), `Cocoon` arousal threshold.
- Wrasse: replace `RunStation`/`VisitCleaner` with `Work`/`Pose` per §5.5; client-side `Pose` becomes a reaction, not a self-initiated behaviour (self-initiated visits remain for ich at ×3 weight).
- Rendering hooks: `spineFlare`, `dorsalRaise`, `quiver` (head-first travelling wave), `blueDeepen`, `clampedFins` (cardinal), `legRock` phase, `cocoon` (existing).
- Events: `Chase(actor, target, bout)`, `Pose`, `Jolt`, `NearMiss`, `Molted` (existing), `TerritoryReset`.
- Commands: `RearrangeRock`, `RemoveOrganism` (sandbox, for test 6).
- Stress model (§4.1): per-organism `S_fast[p]`, `S_slow[p]`, `A[p]` filters with the species `sens` table; `DO_crit(T)`; `rank_eff`; sign hooks `gillHz`, `finClamp`, `desat`, `blueDeepen`, coral `extension`/`paleness`/`pulseRate`.
- Sandbox controls (§4.2): `SetParam(p, target, ramp)`, `Schedule`, `timeScale`, `PowerheadFail(at, duration)`, `WaterChange`, `SetAcclimation`, free-NH₃ readout, DHW gauge, stress inspector, A/B seeded replay, rolling counters.

---

## 6. Open gaps (numbers that remain inferred)

- No peer-reviewed rates exist for dottyback or *Salarias* chase frequency per hour in aquaria; the per-hour caps in §5.3 are inferred from the percentage-of-intrusions data (S:H-TFH-LMB) and the qualitative newcomer accounts (S:H-FC-dotty). Baer 1980 (acanthurid agonistic ethogram) and the full text of Grutter 1994 ch. 5 (per-client inspection rates) would tighten the tang display sequence and the per-client cleaning rates if retrieved.
- Swimming speeds per species in BL/s are inferred from Fulton 2007's family-level aggregates; no species-level field speeds were retrieved for these particular species.
- Engineer goby adult diel activity is inferred (sources only say "reclusive" and describe juvenile night roosting).
- Cross-species shrimp kills (CBS × *Lysmata*) are hobby-reported only; the molt-window mechanism is borrowed from the conspecific study (S:Wong11).
- Sally Lightfoot predation probabilities are vendor-grade statements scaled to a number.

---

## 7. Sources

Peer-reviewed / primary
- **Grutter96** — Grutter AS (1996) Parasite removal rates by the cleaner wrasse *Labroides dimidiatus*. MEPS 130:61–70. https://int-res.com/abstracts/meps/v130/p61-70
- **Grutter95** — Grutter AS (1995) Relationship between cleaning rates and ectoparasite loads in coral reef fishes. MEPS 118:51–58. https://researchonline.jcu.edu.au/27485/
- **Grutter94** — Grutter AS (1994) PhD thesis, JCU: Parasites in the cleaning interactions between *Labroides dimidiatus* and fish. https://researchonline.jcu.edu.au/27392/1/27392-grutter-1994-thesis.pdf
- **Grutter02** — Grutter AS (2002) Cleaning rates and gnathiid diel patterns. MEPS 234:257–264. https://int-res.com/abstracts/meps/v234/p257-264
- **Grutter04** — Grutter AS (2004) Cleaner fish use tactile dancing behavior as a preconflict management strategy. Current Biology 14:1080–1083; summary via UQ News. https://news.uq.edu.au/2004-07-27-dancing-their-way-out-fishy-situation
- **Grutter11** — Grutter AS et al. (2011) Fish mucous cocoons: the "mosquito nets" of the sea. Biology Letters 7:292–294; summary via Deep Sea News. https://deepseanews.com/2011/03/a-blanket-of-mucus/
- **Bshary01** — Bshary R, Würth M (2001) Cleaner fish *Labroides dimidiatus* manipulate client reef fish by providing tactile stimulation. Proc R Soc B 268:1495–1501. https://libra.unine.ch/bitstreams/0350612c-9688-4db8-aae0-6001aeeb7317/download
- **Pinto11** — Pinto A, Oates J, Grutter A, Bshary R (2011) Cleaner wrasses *Labroides dimidiatus* are more cooperative in the presence of an audience. Current Biology 21:1140–1144. https://libra.unine.ch/bitstreams/5387cc2e-a2ac-4de8-8118-ef5b660d536a/download
- **Cheney08** — Cheney KL, Bshary R, Grutter AS (2008) Cleaner fish cause predators to reduce aggression toward bystanders at cleaning stations. Behavioral Ecology 19:1063–1067; summarised at https://www.saltwateraquariumblog.com/cleaner-fish-reduce-aggression-in-captive-aquaria/
- **Tebbich02** — Tebbich S, Bshary R, Grutter AS (2002) Cleaner fish *Labroides dimidiatus* recognise familiar clients. Animal Cognition 5:139–145. https://research-portal.st-andrews.ac.uk/en/publications/cleaner-fish-labroides-dimidiatus-recognise-familiar-clients/
- **Buston03** — Buston P (2003) Size and growth modification in clownfish. Nature 424:145–146. https://www.nature.com/articles/424145a
- **Colleye12** — Colleye O, Parmentier E (2012) Overview on the diversity of sounds produced by clownfishes: … size-related variation and dominance. PLoS ONE 7:e49179. https://pmc.ncbi.nlm.nih.gov/articles/PMC3492317/
- **Hayashi24** — Hayashi K, Locke SC, Laudet V (2024) Counting Nemo: anemonefish *Amphiprion ocellaris* identify species by number of white bars. J Exp Biol 227:jeb246357; OIST summary. https://oist.jp/node/40237
- **Fleming24** — (preprint) Novel behavioral assays reveal sex-specific behavioral syndromes in anemonefish (*A. ocellaris*). bioRxiv 2024.06.07.597983. https://www.biorxiv.org/content/10.1101/2024.06.07.597983.full.pdf
- **Nakamura25** — (MEPS prepress m12931) Agonistic responses of four anemonefish species to approaching divers, Okinawa. https://www.int-res.com/prepress/m12931.html
- **Fulton07** — Fulton CJ (2007) Swimming speed performance in coral reef fishes: field validations reveal distinct functional groups. Coral Reefs 26:217–228. https://link.springer.com/article/10.1007/s00338-007-0195-0
- **Montgomery89** — Montgomery WL, Myrberg AA, Fishelson L (1989) Feeding ecology of surgeonfishes (Acanthuridae) in the northern Red Sea. J Exp Mar Biol Ecol 132:179–207. https://experts.nau.edu/en/publications/feeding-ecology-of-surgeonfishes-acanthuridae-in-the-northern-red/
- **Sohal** — Territoriality of *Acanthurus sohal* on reefs in Ras Mohamed National Park, Red Sea. Egyptian J Aquat Biol Fish. https://journals.ekb.eg/article_18436.html
- **Baer80** — Baer LJ (1980) Intraspecific and interspecific agonistic communication and behavior among surgeonfishes. PhD thesis, Univ. Hawaii (record only). https://scholarspace.manoa.hawaii.edu/items/b8d7214b-f379-4832-ace4-fea29f23f16f
- **Mendes14** — Mendes TC et al. (2014) Territorial behaviour of *Ophioblennius trinitatis*. Scientia Marina 78. https://scientiamarina.revistas.csic.es/index.php/scientiamarina/article/download/1523/1697/1864
- **Kadota11** — Kadota T, Osato J, Hashimoto H, Sakai Y (2011) Harem structure and female territoriality in the dwarf hawkfish *Cirrhitichthys falco*. Environ Biol Fish 92:79–88. https://doi.org/10.1007/S10641-011-9817-Z
- **Marnane02** — Marnane MJ, Bellwood DR (2002) Diet and nocturnal foraging in cardinalfishes (Apogonidae) at One Tree Reef. MEPS 231:261–268. https://int-res.com/abstracts/meps/v231/p261-268
- **Rueger18** — Rueger T, Harrison HB, Gardiner NM, Berumen ML, Jones GP (2018) Site fidelity facilitates pair formation in aggregations of coral reef cardinalfish. https://researchonline.jcu.edu.au/52288/
- **Wittenrich23** — Wittenrich ML et al. (2023) Orchid dottyback *Pseudochromis fridmani* broodstock spawning and filial cannibalism. Fishes 8:451. https://www.mdpi.com/2410-3888/8/9/451
- **Caves19** — Caves EM, Chen C, Johnsen S (2019) The cleaner shrimp *Lysmata amboinensis* adjusts its behaviour towards predatory versus non-predatory clients. Biology Letters 15:20190534. https://pmc.ncbi.nlm.nih.gov/articles/PMC6769148
- **Wong11** — Wong JWY, Michiels NK (2011) Control of social monogamy through aggression in a hermaphroditic shrimp. Frontiers in Zoology 8:30; summarised at https://reefs.com/?p=175085
- **Johnson69** — Johnson VR (1969/1977) Behavior associated with pair formation in the banded shrimp *Stenopus hispidus*. Pacific Science. https://scholarspace.manoa.hawaii.edu/items/44fa1883-1822-40c0-8354-a945dba18401
- **Poulos16** — Poulos DE (2016) Priority effects and the dynamics of coral reef fish assemblages. PhD thesis, JCU. https://researchonline.jcu.edu.au/46889/
- **Ronces21** — Roncés et al. (2021) Improving water quality does not guarantee fish health: effects of ammonia pollution on the behaviour of wild-caught pre-exposed fish. PLoS ONE / bioRxiv. https://www.biorxiv.org/content/10.1101/2020.11.23.393868.full.pdf
- **Yu23** — Yu et al. (2023) Habitat use and avoidance of juvenile reef species under hypoxia. Frontiers in Marine Science 10:1109523. https://www.frontiersin.org/journals/marine-science/articles/10.3389/fmars.2023.1109523/full
- **UF-FA164** — Yanong RPE, Cryptocaryon irritans infections (marine white spot disease) in fish. UF/IFAS EDIS FA164. https://ask.ifas.ufl.edu/publication/FA164/pdf
- **MBA-eng** — Monterey Bay Aquarium, Engineer goby *Pholidichthys leucotaenia*. https://aquarium.org/animals/engineer-goby/
- **FoA-falco** — Fishes of Australia, Dwarf hawkfish *Cirrhitichthys falco*. https://fishesofaustralia.net.au/Home/species/2426
- **ADW-tang** — Animal Diversity Web, *Paracanthurus hepatus*. https://animaldiversity.org/accounts/Paracanthurus_hepatus
- **Wiki-sleep** — Wikipedia, Sleep in fish (secondary; cites primary sleep studies). https://en.wikipedia.org/wiki/Sleep_in_fish
- **Wiki-card** — Wikipedia, *Sphaeramia nematoptera* (secondary; cites Rueger et al. 2016/2019). https://en.wikipedia.org/wiki/Sphaeramia_nematoptera
- **Nilsson10** — Nilsson GE, Östlund-Nilsson S, Munday PL (2010) Effects of elevated temperature on coral reef fishes: loss of hypoxia tolerance and inability to acclimate. Comp Biochem Physiol A 156:389–393. https://researchonline.jcu.edu.au/16140/
- **Dabruzzi21** — Dabruzzi TF et al. (2021) Thermal acclimation of tropical coral reef fishes to global heat waves. eLife 9:e59162 (JCU repository copy). https://researchonline.jcu.edu.au/67005/1/67005.pdf
- **Warren16** — Warren DT, Donelson JM, McCormick MI, Ferrari MCO, Munday PL (2016) Duration of exposure to elevated temperature affects competitive interactions in juvenile reef fishes. PLoS ONE 11:e0164505. https://pmc.ncbi.nlm.nih.gov/articles/PMC5063334/
- **Mancera02** — Laiz-Carrión R, Mancera JM et al. (2002/2005) Plasma osmolality and cortisol after salinity transfer in gilthead sea bream *Sparus auratus*. https://bibrepo.uca.es/articuloscientificos/31775962.pdf
- **Mathew23** — (preprint) Dynamics of endogenous and water cortisol release in Asian seabass *Lates calcarifer* after acute stress in a farm-scale RAS. bioRxiv 2023.09.20.558587. https://www.biorxiv.org/content/10.1101/2023.09.20.558587.full.pdf
- **Bessa21** — Bessa E, Sadoul B, Mckenzie DJ, Geffroy B (2021) Group size, temperature and body size modulate the effects of social hierarchy on basal cortisol levels in fishes. Hormones and Behavior 136:105077. https://archimer.ifremer.fr/doc/00729/84093
- **Munday14** — Munday PL et al. (2014) Behavioural impairment in reef fishes caused by ocean acidification at CO₂ seeps. Nature Climate Change 4:487–492. https://researchonline.jcu.edu.au/38099/
- **Wangpraseurt19** — Resolving coral photoacclimation dynamics through coupled stable isotope and metabolite analysis (*Acropora muricata*, 21-day light step). J Exp Biol 222:jeb195982. https://cob.silverchair.com/jeb/article/222/8/jeb195982/34051/
- **Xenia23** — Effects of ocean acidification on the pulsating soft coral *Xenia umbellata* (pulsation −17/−26/−32 % at pH 8.0/7.8/7.6). PLoS ONE 2023. https://pmc.ncbi.nlm.nih.gov/articles/PMC10651030

Hobby / vendor (evidence grade H)
- **H-TFH-LMB** — Michael SW, "Uber algae eaters: the lawnmower blennies", TFH Magazine (summarises Heron Island field studies of *Salarias fasciatus*). https://tfhmagazine.com/articles/saltwater/uber-algae-eaters-the-lawnmower-blennies-full-article
- **H-FC-dotty** — Furry Critter, Dottybacks (Pseudochromidae) care and behaviour. https://www.furrycritter.com/pages/articles/fish/dottyback.htm
- **H-TFH-dotty** — TFH Magazine, *Pictichromis paccagnellae*. https://www.tfhmagazine.com/articles/saltwater/pictichromis-paccagnellae
- **H-reefs-fridclown** — reefs.com forum thread, *P. fridmani* attacking a pair of black ocellaris. https://reefs.com/forum/fish/64295-pseudo-chromis-fridmani-attacking-pair-black-occelaris-clowns-1.html
- **H-TSA-card** — Top Shelf Aquatics, Why cardinalfish hide during the day. https://topshelfaquatics.com/blogs/news/why-cardinalfish-hide-during-the-day-natural-behavior-vs-tank-issues
- **H-QM-eng** — Quality Marine, Engineer goby. https://www.qualitymarine.com/quality-marine/fish/gobies/pholidichthys/engineer-18516/
- **H-FK-conv** — Maidenhead Aquatics / fishkeeper.co.uk, "Crafty convicts" (convict blenny natural history incl. BBC Life footage). https://www.fishkeeper.co.uk/stories/crafty-convicts
- **H-RG-hawk** — Roundglass Sustain, Hawkfish hues. https://roundglasssustain.com/species/hawkfish
- **H-LA-sally** — LiveAquaria, Sally Lightfoot crab. https://www.liveaquaria.com/products/sally-lightfoot-crab
