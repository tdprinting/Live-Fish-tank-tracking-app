# Pajama cardinalfish (Sphaeramia nematoptera)

Sim hook: `BrainKind.CARDINAL` · Adult size: up to 8.5 cm (FishBase via search) · Tank count: group (sim)

## Sources
| # | Video / page | URL | What it shows |
|---|---|---|---|
| S1 | Oregon Coast Aquarium | https://aquarium.org/?p=23624 | nocturnal; hides in coral by day; groups 2-25 |
| S2 | Saltwater Aquarium Blog | https://www.saltwateraquariumblog.com/pajama-cardinalfish/ | slow methodical swimmer near cover |
| S3 | FishBase | https://nrm-fishbaselinux01.nrm.se/summary/5778 | max length 8.5 cm |

NOTE: No footage was watched. Wikimedia Commons video downloads (candidates found: clownfish CAS-Aquarium-Clownfish&Coral.ogv, Aquarium of Cattolica - Clownfishes.webm; Paracanthurus - August 14 2021.webm; Labroides dimidiatus cleaning Acanthurus mata - Gijon Aquarium) were blocked by HTTP 429 rate limiting from this server, and no usable Commons clip was found for the other species. YouTube rows V1-V3 remain queued for hand review. No rows upgraded to [H]; numbers stay [L] unless a source is cited.

## Animation spec (for the renderer)
| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Locomotion | body-caudal, slow, deep body | | [S2] [M] |
| Cruise speed | slow, hovering | 0.3-0.7 BL/s | [L] |
| Burst | short dart into cover | 5 BL/s | [L] |
| Tail / fin | gentle tail, fanning pectorals, 2nd dorsal flutter | tail 1.5 Hz | [L] |
| Posture | level, hovering, often facing into current | | [L] |
| Idle | hangs motionless in loose clump | | [S1] [M] |
| Turning | smooth, wide | | [L] |
| Colour | spotted pattern constant; paler at night | | [L] |

## Behaviour spec (for the AI)
| Aspect | Observation | Suggested rule / numbers | Cite |
|---|---|---|---|
| Zone | mid-water just above/among coral or rock | stay within 2 BL of cover | [S2] [M] |
| Day / night | hides by day, feeds at night | day: loose clump near cover, low activity; dusk: spread out | [S1] [M] |
| Feeding | carnivore: mysis, brine, picks mid-water | slow approach, gulp | [S1] [M] |
| Social | loose shoal, 2-25 | cohesion radius 3-4 BL, weak alignment | [S1] [M] |
| V1 | Virtual Visit: Pajama Cardinalfish + Aquarium After Dark (YouTube) | https://www.youtube.com/watch?v=O7qmeHVUqV0 | title: pajama cardinal and night behaviour [M] |
| V2 | PAJAMA CARDINAL FISH IN MY REEF TANK! (YouTube) | https://www.youtube.com/watch?v=SUp2MIbEiAw | reef-tank footage [M] |
| V3 | Discover The Pajama Cardinalfish care guide (YouTube) | https://www.youtube.com/watch?v=LDVDq8DxIDY | care guide [M] |
| Mouthbrooding | male carries eggs, doesn't feed | optional: one male with swollen throat | [M] |
| Tells | slow hover in a clump, little fuss | | |

## Priority fixes for Opus
1. Loose clump hover by cover, very low speed in daytime.
2. Day/night flip: more active at dusk/night.
3. Weak-cohesion shoal (not tight school).

## Look and behaviour pass, 2026-10-14 (critters thread)
Sources read this pass: [FishBase morphology](https://www.fishbase.se/physiology/Sphaeramia_nematoptera) (F1),
[Fishes of Australia](https://fishesofaustralia.net.au/home/species/4237) (F2), Tee's own fish in
`/mnt/project-files/reeftank/reference/b_04.jpg` (T-b04, **[H]**) and `ref_cardinal.jpg` (aquarium photo, [M]).
YouTube is blocked from this network; no footage was watched.

| Trait | Finding | Conf. | Sim / model now |
|---|---|---|---|
| Body depth | "Greatest body depth 1.8-2.0 in SL" = 0.50-0.56 SL; compressed | [M] F1 | profile depth 0.535 SL at the first dorsal (was 0.46) |
| Head | "more or less straight dorsal head profile", terminal mouth; head long (opercle ~0.33 SL on T-b04) | [M] F1, [H] | straight forehead line snout -> first dorsal; eye at 0.15 SL; gill mask moved to 0.31 |
| Fins | D VII + I,9 (2nd dorsal tip elongate), A II,9-10 | [M] F1 | tall yellow first dorsal (0.27 SL), 2nd dorsal front rays 0.36 SL, anal 0.25 SL, forked caudal 0.30 SL |
| Bar | dark brown bar on the anterior body, running onto the first dorsal; on Tee's fish it leans back slightly toward the belly and is ~0.12 SL wide | [M] F1, [H] | bar at u 0.385-0.505 with a 0.035 lean, scale net; leading first-dorsal spines dark |
| Head colour | yellow head (F1, F2); Tee's is lemon-yellow with a paler throat and silvery cheek | [M]/[H] | lemon yellow `#E2CB3A` -> `#EFE6A6`, cream cheek |
| Rear spots | "purple"/red-brown spots on the rear body (F1, F2); Tee's are orange-brown, round, ~0.035 SL across, smaller toward the tail | [M]/[H] | 80 spots, r 0.012-0.016 front, 0.009-0.012 on the peduncle, `#B65A38` |
| Pelvics | F1/F2 say dark; Tee's fish and the reference photo show long yellow pelvics with an orange-red rim | [H] wins | long (0.30 SL), broad base, yellow with orange-red rim and dark leading spine |
| Iris | "red bar through the iris" (F2) | [M] | red-orange iris ring kept |
| Size | 9 cm TL max (F2); 8.5 cm (FishBase summary) | [M] | model default 7 cm (Tee's ~6 cm) |
| Habit | "Groups hover near branching corals in sheltered bays" (F2); hides by day, feeds at night (S1) | [M] | level hover (pitch <= 10 deg), quarter-cruise station changes every 8-20 s, faces into the flow so a group lines up; night forage at 0.8 cruise |
| Mouthbrooding | males brood (F2) | [M] | unchanged (`mouthbrooding` flag) |

Deviations found in the sim before this pass: the day hover pitched nose-up/down to the 35 deg clamp at almost
every sample (it steered straight at random 3-D points), and at night it hovered for hours against settled food or
pods wedged at the rock base (unreachable, no give-up in Night foraging). Both fixed (CardinalBrain HoverCover,
NocturnalForage, `SteerIntent.maxPitch`).
