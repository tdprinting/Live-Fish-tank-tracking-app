# Indigo dottyback (Pictichromis / Pseudochromis)

Sim hook: `BrainKind.DOTTYBACK` · Adult size: ~6-7 cm (diadem, FishBase) · Tank count: 1

## Sources
| # | Video / page | URL | What it shows |
|---|---|---|---|
| S1 | Wikipedia Pictichromis diadema | https://en.wikipedia.org/wiki/Pictichromis_diadema | territorial, solitary in aquaria |
| S2 | Top Shelf Aquatics diadem | https://topshelfaquatics.com/pages/saltwater-fish/diadem-dottyback | defends rockwork zone |
| S3 | That Pet Place dottybacks | https://www.thatpetplace.com/articles/dottybacks-article | bully smaller fish; shy |

NOTE: Video links (V1-V3) found via search listings only; titles/snippets seen, footage not viewed, so listed for hand review. Page-based claims are [M]; numbers are [L] estimates.
No "indigo" source found; data are for congeneric Pictichromis.

## Animation spec (for the renderer)
| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Locomotion | body-caudal, darting | | [L] |
| Cruise | 1-1.5 BL/s | | [L] |
| Burst | 8 BL/s | | [L] |
| Fin beat | tail 3-4 Hz | | [L] |
| Posture | level, alert | | [L] |
| Idle | hover at cave mouth | | [L] |
| Turning | sharp | | [L] |
| Colour | brighter when dominant | | [L] |

## Behaviour spec (for the AI)
| Aspect | Observation | Suggested rule / numbers | Cite |
|---|---|---|---|
| Zone | rockwork/cave, defended zone | radius 8 BL | [S2] [M] |
| Day/night | diurnal | | [L] |
| Feeding | mysis, brine | | [S3] [M] |
| Social | bullies small passive fish | chase small fish | [S3] [M] |
| V1 | Neon Dottyback KILLED my Watchman Goby! (YouTube) | https://www.youtube.com/watch?v=J2P7u2c11ew | title: dottyback aggression (neon, not indigo) [M] |
| V2 | All About The Orchid Dottyback / Fridman's Pseudochromis (YouTube) | https://www.youtube.com/watch?v=pWah0DP2Qfo | snippet: finds a cave to live in [M] |
| V3 | All About The Purple Pseudochromis or Purple Dottyback (YouTube) | https://www.youtube.com/watch?v=VRIrDYcbqwU | snippet: hardy, easy to feed [M] |
| Tells | shy yet aggressive; cave-mouth darts | | [S3] [M] |

## Priority fixes for Opus
1. Cave-mouth anchor with territorial zone.
2. Dart-chase on small fish then return.
3. Brighter colour when dominant.
