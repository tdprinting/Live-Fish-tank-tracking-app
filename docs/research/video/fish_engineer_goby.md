# Engineer goby / convict blenny (Pholidichthys leucotaenia)

Sim hook: `BrainKind.ENGINEER_GOBY` · Adult size: ~30 cm; juveniles few cm · Tank count: 1+

## Sources
| # | Video / page | URL | What it shows |
|---|---|---|---|
| S1 | Shedd Aquarium | https://www.sheddaquarium.org/about-shedd/press-releases/engineer-gobies-masters-of-spring-cleaning | clear gravel from burrows; peek from burrows |
| S2 | Saltwater Aquarium Blog | https://www.saltwateraquariumblog.com/engineer-goby-cichlid-of-the-sea/ | tunnel building |
| S3 | Aquarium of the Pacific | https://aquarium.org/animals/engineer-goby/ | juvenile schooling, catfish mimicry |

NOTE: No footage was watched. Wikimedia Commons video downloads (candidates found: clownfish CAS-Aquarium-Clownfish&Coral.ogv, Aquarium of Cattolica - Clownfishes.webm; Paracanthurus - August 14 2021.webm; Labroides dimidiatus cleaning Acanthurus mata - Gijon Aquarium) were blocked by HTTP 429 rate limiting from this server, and no usable Commons clip was found for the other species. YouTube rows V1-V3 remain queued for hand review. No rows upgraded to [H]; numbers stay [L] unless a source is cited.

## Animation spec (for the renderer)
| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Locomotion | elongate; anguilliform-ish wriggle, subcarangiform in school | | [L] |
| Cruise | juveniles schooling in tight line | 1-2 BL/s | [L] |
| Burst | tail-first/head-first dive into burrow | 8 BL/s | [L] |
| Fin beat | body wave ~3 Hz | | [L] |
| Posture | juv. black w/ white stripe; adult head poking out of hole | | [S3] [M] |
| Idle | head out of burrow | | [S1] [M] |
| Digging | mouthfuls of sand carried away and spat out | carry 1 BL, drop, return; every 20-40 s | [S2] [M] |
| Turning | snake-like | | [L] |

## Behaviour spec (for the AI)
| Aspect | Observation | Suggested rule / numbers | Cite |
|---|---|---|---|
| Zone | bottom, burrow under rock | burrow entrance anchor | [S2] [M] |
| Day/night | juveniles roam by day, return to burrow at dusk | | [S3] [M] |
| Feeding | picks plankton/benthos | | [L] |
| Social | juveniles school; adults territorial near burrow | | [S3] [M] |
| V1 | Engineer Gobies Building Tunnels (YouTube) | https://www.youtube.com/watch?v=vlHOjGLOyOM | title: tunnel building [M] |
| V2 | Engineer gobies at Shedd Aquarium (YouTube) | https://www.youtube.com/watch?v=paY1QGACWMY | public aquarium footage [M] |
| V3 | Facts: The Engineer Goby (Convict Blenny) (YouTube) | https://www.youtube.com/watch?v=_XL8ys2O_tA | species facts [M] |
| Tells | sand-moving, mouth-full carry, peeking | | |

## Priority fixes for Opus
1. Digging/carry-and-spit loop near burrow.
2. Peeking head-out idle.
3. Juvenile line school with stripe pattern.
