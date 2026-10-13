# Tailspot blenny (Ecsenius stigmatura)

Sim hook: `BrainKind.PEEKER_BLENNY` · Adult size: ~6-7.5 cm · Tank count: 1

## Sources
| # | Video / page | URL | What it shows |
|---|---|---|---|
| S1 | Top Shelf Aquatics | https://topshelfaquatics.com/pages/saltwater-fish/tailspot-blenny | darts in/out of crevices, perches to observe |
| S2 | Gensou | https://gensou.sg/?p=30499 | inquisitive, comes to glass |
| S3 | Build Your Aquarium | https://www.buildyouraquarium.com/tailspot-blenny/ | care sheet; plays dead |
| S4 | Wikipedia E. stigmatura | https://en.wikipedia.org/wiki/Ecsenius_stigmatura | coppery body, blackish spot at tail base, bright pink under eye when threatened, ~6 cm |

NOTE: 2026-10-13 footage pass: no rows upgraded to [H]. Wikimedia Commons search found no usable clip for this species (or only a related one), and Commons/upload.wikimedia.org returned HTTP 429 (Retry-After 600 s, still blocked after ~25 min), so nothing was downloaded or watched. YouTube V# rows remain queued for hand review. Page-based claims are [M]; numbers are [L] estimates. Commons has no Ecsenius video.

## Animation spec (for the renderer)
| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Locomotion | hop/dart | | [S1] [M] |
| Cruise | short darts | 4 BL/s | [L] |
| Burst | into hole | 8 BL/s | [L] |
| Fin beat | pecs, tail flick | | [L] |
| Posture | head poking from hole, body in | | [L] |
| Idle | peek 5-20 s | | [L] |
| Turning | head swivel | | [L] |
| Colour | coppery body, blackish spot at tail-fin base; pink/bright colour under eye flares when threatened | | [S4] [M] |

## Behaviour spec (for the AI)
| Aspect | Observation | Suggested rule / numbers | Cite |
|---|---|---|---|
| Zone | rock with small holes | anchored to hole | [S1] [M] |
| Day/night | diurnal | | [L] |
| Feeding | grazes film/turf all day | | [S1] [M] |
| Social | curious, approaches glass; some aggression | | [S2] [M] |
| Startle | may play dead | | [S1] [M] |
| V1 | Tailspot blenny - Ecsenius stigmatura (YouTube) | https://www.youtube.com/watch?v=VyEEPG4DXZ4 | species video [M] |
| V2 | Tail Spot Blenny checking out his new neighbor (YouTube) | https://www.youtube.com/watch?v=zhjzk92t-n8 | title: interaction w/ tankmate [M] |
| V3 | Tailspot Blenny and various Corals - 25g Nano Reef (YouTube) | https://www.youtube.com/watch?v=afzf243hZ28 | nano reef footage [M] |
| Tells | peek-a-boo | | |

## Priority fixes for Opus
1. Hole-peeking idle.
2. Dart in/out of crevices.
3. Approach glass when observer near.
