# Falco hawkfish (Cirrhitichthys falco)

Sim hook: `BrainKind.HAWK` · Adult size: ~5-7 cm · Tank count: 1

## Sources
| # | Video / page | URL | What it shows |
|---|---|---|---|
| S1 | Top Shelf Aquatics | https://topshelfaquatics.com/pages/saltwater-fish/falco-hawkfish | perch; ambush |
| S2 | Reef Builders overview | https://reefbuilders.com/2013/08/12/falcos-hawkfish-dwarf-cirrhitichthys-falco-overview | no swim bladder; short bursts |
| S3 | Fishes of Australia | https://fishesofaustralia.net.au/Home/species/2426 | species profile |
| S4 | Wikipedia C. falco | https://en.wikipedia.org/wiki/Cirrhitichthys_falco | Maldives to Caroline Is./Samoa; aquarium trade |

NOTE: 2026-10-13 footage pass: no rows upgraded to [H]. Wikimedia Commons search found no usable clip for this species (or only a related one), and Commons/upload.wikimedia.org returned HTTP 429 (Retry-After 600 s, still blocked after ~25 min), so nothing was downloaded or watched. YouTube V# rows remain queued for hand review. Page-based claims are [M]; numbers are [L] estimates. Commons has no C. falco video (only Paracirrhites forsteri and Oxycirrhites typus hawkfish clips, not fetched): next pass could use P. forsteri as a family proxy.

## Animation spec (for the renderer)
| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Locomotion | short hops between perches on thick lower pectoral rays; no swim bladder | | [S2] [M] |
| Cruise | hop 2-4 BL then stop | burst 3-5 BL/s, 0.3 s | [L] |
| Fin beat | pecs; tail flick on launch | | [L] |
| Posture | propped on pectorals, head up ~10-20 deg | | [S1] [M] |
| Idle | motionless perch, eyes scanning | hold 5-30 s | [S1] [M] |
| Turning | pivot on perch | | [L] |
| Colour | Tee's fish: white body; two brown-red reticulated (mesh) saddles over nape and mid-back; scattered orange-red spots on rear flank fading toward belly; red eye ring; white cirri tufts on dorsal spine tips; clear fins. Same at night as by day | texture: 2 dorsal mesh saddles + spot field; cirri billboards on spine tips | [T89 0:02-0:06] [H] (tee_tank_night.md) |

## Behaviour spec (for the AI)
| Aspect | Observation | Suggested rule / numbers | Cite |
|---|---|---|---|
| Zone | high perches on rock/coral | pick elevated perch | [S1] [M] |
| Day/night | active all day; at night Tee's fish rested on open sand at the rock base, propped on pelvic fins, head up ~15-20 deg, tracking with eyes, ignored a torch | night: sand perch within 10 cm of rock, pitch +15-20 deg, no flight from light | [T89 0:01-0:08] [H] (tee_tank_night.md) |
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
