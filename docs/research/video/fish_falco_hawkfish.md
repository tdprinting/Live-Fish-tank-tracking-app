# Falco hawkfish (Cirrhitichthys falco)

Sim hook: `BrainKind.HAWK` · Adult size: ~5-7 cm · Tank count: 1

## Sources
| # | Video / page | URL | What it shows |
|---|---|---|---|
| S1 | Top Shelf Aquatics | https://topshelfaquatics.com/pages/saltwater-fish/falco-hawkfish | perch; ambush |
| S2 | Reef Builders overview | https://reefbuilders.com/2013/08/12/falcos-hawkfish-dwarf-cirrhitichthys-falco-overview | no swim bladder; short bursts |
| S3 | Fishes of Australia | https://fishesofaustralia.net.au/Home/species/2426 | species profile |

NOTE: Video links (V1-V3) found via search listings only; titles/snippets seen, footage not viewed, so listed for hand review. Page-based claims are [M]; numbers are [L] estimates.

## Animation spec (for the renderer)
| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Locomotion | short hops between perches on thick lower pectoral rays; no swim bladder | | [S2] [M] |
| Cruise | hop 2-4 BL then stop | burst 3-5 BL/s, 0.3 s | [L] |
| Fin beat | pecs; tail flick on launch | | [L] |
| Posture | propped on pectorals, head up ~10-20 deg | | [S1] [M] |
| Idle | motionless perch, eyes scanning | hold 5-30 s | [S1] [M] |
| Turning | pivot on perch | | [L] |
| Colour | red-cross-hatch, stable | | |

## Behaviour spec (for the AI)
| Aspect | Observation | Suggested rule / numbers | Cite |
|---|---|---|---|
| Zone | high perches on rock/coral | pick elevated perch | [S1] [M] |
| Day/night | active all day; night wedge in rock | | [L] |
| Feeding | ambush swoop at crustaceans then return | | [S1] [M] |
| Social | chases encroachers; harem in wild | territory 5 BL | [S1] [M] |
| V1 | Dwarf Hawkfish, Cirrhitichthys Falco. Flees Into Coral Reef (YouTube) | https://www.youtube.com/watch?v=GM9VsqpgABM | title: wild, flees into coral [M] |
| V2 | Feeding my Falco Hawkfish (YouTube) | https://www.youtube.com/watch?v=tTvaEABGVj0 | aquarium feeding (gel food) [M] |
| V3 | Falco Hawkfish Cirrhitichthys falco (YouTube) | https://www.youtube.com/watch?v=z-p60bwxMk4 | aquarium footage [M] |
| Tells | perch-and-pounce | | |

## Priority fixes for Opus
1. Perch pose on pectorals with long idles.
2. Hop-between-perches locomotion, never continuous cruise.
3. Pounce-and-return feeding.
