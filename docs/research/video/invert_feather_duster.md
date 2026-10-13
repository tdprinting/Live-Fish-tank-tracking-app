# Brown tube worm / feather duster (Sabellastarte)

Sim hook: `BrainKind.FEATHER_DUSTER` · Adult size: crown 5-10 cm, tube 10-20 cm · Tank count: 1-3

## Sources
| # | Video / page | URL | What it shows |
|---|---|---|---|
| V1 | FeatherDuster Worm Retracts and Expands HIMB_MH (YouTube) | https://www.youtube.com/watch?v=9QUFmX5aaaY | S. spectabilis retract/expand timing study (number not seen) - from search snippet, not watched; for hand review |
| V2 | Feather duster worm pops its head out of the tube (YouTube) | https://www.youtube.com/watch?v=PJRkyawUbow | emergence, no timing - from search snippet, not watched; for hand review |
| V3 | Feather Duster Worms (Sabellastarte sp.) (YouTube) | https://www.youtube.com/watch?v=5TubSMtMhI0 | species footage - from search snippet, not watched; for hand review |
| S1 | TFH Sabellastarte fallax | https://www.tfhmagazine.com/articles/saltwater/sabellastarte-fallax | care |
| S2 | Fishkeeper giant feather duster | https://www.fishkeeper.co.uk/fish/marine/miscellaneous-invertebrates/giant-feather-duster-worm | care |
| S3 | Reefs.com featherdusters | https://reefs.com/?p=209095 | startle/feeding |

No YouTube pages surfaced by WebSearch (not fetched); no [H] claims. Numbers marked [L] are estimates from general crustacean/echinoderm knowledge.

## Animation spec (for the renderer)
| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Locomotion mode | sessile in tube | none | |
| Startle | crown retracts instantly into tube on disturbance/shadow | 0.1-0.2 s retract | [S1][S3][M], time [L] |
| Reopen | slow unfurl | 20-60 s after calm (shy: minutes) | [L] |
| Crown | spiral/fan of radioles, gently swaying | sway 0.1-0.2 Hz, small | [L] |
| Posture | tube upright, crown 1-2 cm above tube mouth | | [L] |
| Colour | brown/banded | | [L] |

## Behaviour spec (for the AI)
| Aspect | Observation | Suggested rule / numbers | Cite |
|---|---|---|---|
| Zone | low, bottom/near substrate where particles collect | | [S3][M] |
| Feeding | suspension feeder; food blasted onto crown triggers retract | feed gently under crown | [S3][M] |
| Social | harassing fish (triggers, wrasses, angels, butterflies) cause retraction | retract when fish within 5 cm moving fast | [S1][M] |
| Parameters | poor water, frequent disturbance, hunger: crown lost, regrown 1-2 months | | [S1][M] |
| Tells | instant vanish, slow return | | [M] |

## Priority fixes for Opus
1. Instant retract on fish dart or shadow, slow reopening.
2. Gentle crown sway.
3. Crown loss and regrowth under stress.
