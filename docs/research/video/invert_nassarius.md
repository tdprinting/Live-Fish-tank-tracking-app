# Nassarius snail (Nassarius sp.)

Sim hook: `BrainKind.NASSARIUS` · Adult size: 2-3 cm shell · Tank count: 3-6

## Sources
| # | Video / page | URL | What it shows |
|---|---|---|---|
| V1 | Nassarius Snail Bursts Out of Sand to Eat (YouTube) | https://www.youtube.com/watch?v=SwHctATeIZk | emergence on food - from search snippet, not watched; for hand review |
| V2 | our Large Nassarius snail coming up from the sandbed during feeding (YouTube) | https://www.youtube.com/watch?v=LkSQzIOkVbU | emerging when mysis sprayed - from search snippet, not watched; for hand review |
| V3 | Nassarius snail feeding (YouTube) | https://www.youtube.com/watch?v=0jXiG4BXzvQ | feeding - from search snippet, not watched; for hand review |
| S1 | LiveAquaria Super Tongan | https://www.liveaquaria.com/products/super-tongan-nassarius-snail | feeding rush |
| S2 | Wikipedia Nassarius | https://en.wikipedia.org/wiki/Nassarius | biology |
| S3 | Reef Exclusive | https://reefexclusive.com/products/nassarius-snail | burrow/siphon |

No YouTube pages surfaced by WebSearch (not fetched); no [H] claims. Numbers marked [L] are estimates from general crustacean/echinoderm knowledge.

## Animation spec (for the renderer)
| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Locomotion mode | muscular foot gliding; plows under sand | | [L] |
| Cruise speed | slow | 0.5-1 cm/s | [L] |
| Food rush | emerge and race toward scent | 3-5 cm/s, "sand boils" | [S1][M] numbers [L] |
| Burial | burrow within ~10 s | | [L] |
| Posture | buried with only siphon tube protruding upward | shell hidden | [S3][M] |
| Idle | siphon swivels toward scent | | [L] |

## Behaviour spec (for the AI)
| Aspect | Observation | Suggested rule / numbers | Cite |
|---|---|---|---|
| Zone | sand bed | | [S3][M] |
| Day / night | buried most of time | | [S3][M] |
| Feeding | detects food and emerges together | all in ~30 cm emerge within 10-60 s | [S1][M] |
| Social | hermits attack/steal shells | | [S1][M] |
| Parameters | sensitive to nitrate, copper | | [S1][M] |
| Tells | lone siphon in sand; sudden group emergence | | [M] |

## Priority fixes for Opus
1. Buried with siphon visible.
2. Mass emergence race at feeding.
3. Sand disturbance as they move.
