# Astrea snail (Lithopoma / Astraea)

Sim hook: `BrainKind.ASTREA` · Adult size: 2-3 cm · Tank count: 4-8

## Sources
| # | Video / page | URL | What it shows |
|---|---|---|---|
| V1 | Reef Cleanup Crew MVP Astraea Snails (YouTube) | https://www.youtube.com/watch?v=dMd5HswCTmI | glass/rock algae cleanup crew - from search snippet, not watched; for hand review |
| S1 | Top Shelf Aquatics | https://topshelfaquatics.com/pages/saltwater-invertebrates/astrea-snail | care, flipping |
| S2 | Reef Central astrea turnover | https://forums.reefcentral.com/threads/astrea-snail.695733 | righting anecdotes |
| S3 | Reef Central snails falling | https://archive.reefcentral.com/forums/archive/index.php/t-681003.html | falls off glass |

No YouTube pages surfaced by WebSearch (not fetched); no [H] claims. Numbers marked [L] are estimates from general crustacean/echinoderm knowledge.

## Animation spec (for the renderer)
| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Locomotion mode | foot gliding with radula scraping | | [L] |
| Cruise speed | slow | 0.3-0.8 cm/s on glass | [L] |
| Grazing | head swings side to side scraping | 0.5 Hz | [L] |
| Posture | shell upright on glass, foot extended | | [L] |
| Upside-down | struggles, often stuck; reportedly can right on bare glass/rock, not sand | | [S1][S2][M] |
| Idle | clamps shell to surface, hours | | [L] |

## Behaviour spec (for the AI)
| Aspect | Observation | Suggested rule / numbers | Cite |
|---|---|---|---|
| Zone | glass and rock | | [S2][M] |
| Feeding | good glass/rock algae grazer | | [S2][M] |
| Social | hermits take shell of fallen one | | [S2][M] |
| Self-right | unreliable on sand; stays flipped unless helped | if fall on sand: 80% fail; stuck, dies in days | [S1][S2][M] numbers [L] |
| Tells | sometimes falls from glass | | [S3][M] |

## Priority fixes for Opus
1. Slow glass grazing with head sweep.
2. Falls onto sand then stuck upside down.
3. Hermits attacking flipped ones.
