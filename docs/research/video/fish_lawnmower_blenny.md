# Lawnmower blenny (Salarias fasciatus)

Sim hook: `BrainKind.LAWNMOWER_BLENNY` · Adult size: ~12-14 cm [L] · Tank count: 1

## Sources
| # | Video / page | URL | What it shows |
|---|---|---|---|
| S1 | Quality Marine | https://www.qualitymarine.com/news/the-curious-case-of-the-lawnmower-blenny/ | algae only ~15% of diet |
| S2 | Aquariadise | https://aquariadise.com/lawnmower-blenny/ | hops rock to rock grazing |
| S3 | Gensou care guide | https://gensou.sg/lawnmower-blenny-care-guide/ | territories on rubble |

NOTE: Video links (V1-V3) found via search listings only; titles/snippets seen, footage not viewed, so listed for hand review. Page-based claims are [M]; numbers are [L] estimates.

## Animation spec (for the renderer)
| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Locomotion | hop/scoot, no swim bladder | | [S2] [M] |
| Cruise | hops 2-5 BL | 3 BL/s per hop | [L] |
| Burst | dart into hole | 6 BL/s | [L] |
| Fin beat | pecs prop; tail flick | | [L] |
| Posture | perched head-up on pecs/pelvics | | [L] |
| Idle | perched, scanning | | [L] |
| Turning | pivot | | [L] |
| Colour | pattern constant | | |

## Behaviour spec (for the AI)
| Aspect | Observation | Suggested rule / numbers | Cite |
|---|---|---|---|
| Zone | rock/rubble surfaces | small territory | [S3] [M] |
| Day/night | diurnal grazer, holes at night | | [L] |
| Feeding | scrapes film algae/diatoms/detritus; prefers film over hair algae | bite 1-2/s on rock surface | [S1][S2] [M] |
| Social | defends vs other herbivores | chase 3 BL | [S3] [M] |
| V1 | Lawnmower Blenny Grazing On Hair Algae (YouTube) | https://www.youtube.com/watch?v=u2nABQWKo_0 | title: grazing hair algae in reef tank [M] |
| V2 | Lawnmower Blenny eating hair algae (YouTube) | https://www.youtube.com/watch?v=ai49jy4mN54 | title: eating hair algae [M] |
| V3 | Fish Feature: Lawnmower Blenny - Salarias fasciatus (YouTube) | https://www.youtube.com/watch?v=2DLqENutRqM | species feature [M] |
| Tells | hop-graze | | |

## Priority fixes for Opus
1. Hop-and-graze along rock surfaces.
2. Perched head-up idle.
3. Territorial chase of other grazers.
