# Black brittle star (Ophiocoma sp.)

Sim hook: `BrainKind.BRITTLE_STAR` · Adult size: disc 2-3 cm, arms 10-15 cm · Tank count: 1-2

## Sources
| # | Video / page | URL | What it shows |
|---|---|---|---|
| V1 | Brittle Star eats Shrimp in Reef Aquarium Time Lapse HD (YouTube) | https://www.youtube.com/watch?v=p8pIrBx5M6g | pulls shrimp to mouth - from search snippet, not watched; for hand review |
| V2 | Live rock hitchhiker: filter feeding brittle stars (YouTube) | https://www.youtube.com/watch?v=IvzGQJnwzn4 | arms raised in current catching particles - from search snippet, not watched; for hand review |
| V3 | Feeding our big brittle star with a bit of pilchard (YouTube) | https://www.youtube.com/watch?v=62uBdEL8zsc | feeding - from search snippet, not watched; for hand review |
| S1 | LiveAquaria echinoderm article | https://www.liveaquaria.com/article/190/?aid=190 | brittle star care |
| S2 | Fishkeeper | https://www.fishkeeper.co.uk/fish/marine/miscellaneous-invertebrates/brittle-star | care |
| S3 | Frontiers in Zoology 2023 | https://frontiersinzoology.biomedcentral.com/articles/10.1186/s12983-023-00495-y | arm biomechanics |

No YouTube pages surfaced by WebSearch (not fetched); no [H] claims. Numbers marked [L] are estimates from general crustacean/echinoderm knowledge.

## Animation spec (for the renderer)
| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Locomotion mode | arm rowing: lead arm(s) forward, two side arms push, S-curve, disc rides low | 5 arms, 3 working | [S1][S2][M], phase [L] |
| Cruise speed | - | 2-5 cm/s; stroke cycle 0.5-1 Hz | [L] |
| Burst | fast rowing on food scent | up to 8 cm/s | [L] |
| Arm motion | bends sideways not upward (ball-socket plates) | arms laterally flexed, horizontal | [S3][M] |
| Posture | disc down, arm tips raised/waving from rock hole | | [L] |
| Idle | one or two arms extended from crevice, searching | | [L] |
| Turning | change lead arm; no rotation | | [L] |
| Feeding | grasps food with arm tip and rolls it to mouth | | [S1][M] |

## Behaviour spec (for the AI)
| Aspect | Observation | Suggested rule / numbers | Cite |
|---|---|---|---|
| Zone | hides in rock crevice by day, arms out | day: hidden except tips | [L] |
| Day / night | nocturnal scavenger | emerge after lights-off or food scent | [L] |
| Feeding | scavenges leftovers, meaty food; rushes food | rush at 5-8 cm/s toward scent | [S2][M] |
| Social | some species trap fish (green brittle) | black: scavenger | [S1][M] |
| Tells | snaking arms, tips probing from hole | | [S3][M] |

## Priority fixes for Opus
1. Rowing arm gait, not rigid legs.
2. Day-hide with arm tips waving out.
3. Food rush then arm-to-mouth rolling.
