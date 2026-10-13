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
