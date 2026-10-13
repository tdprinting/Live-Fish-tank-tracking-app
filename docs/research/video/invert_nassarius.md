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
| M1 | Starvation and foraging in N. conoidalis / N. siquijorensis (abstract read) | https://scholars.hkbu.edu.hk/en/publications/effects-of-starvation-on-the-physiology-and-foraging-behaviour-of-3/ | 1-3 cm/min toward food |

NOTE (2026-10-13 footage pass): no footage watched, no [H] rows; [M] sourcing pass done afterwards (M# rows). Wikimedia Commons API/upload hosts returned HTTP 429 (Retry-After 600) for this server during the pass, and YouTube is blocked; candidate Commons clips found but not downloaded (cleaner shrimp: File:Pacific cleaner shrimp and leopard moray eel.webm; Nassarius: File:Gevlochten fuikhoorn kruipend over de zandbodem-4931664.webm). YouTube V# rows stay queued for hand review. Numbers marked [L] are estimates from general crustacean/echinoderm knowledge.

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

## Sourced values ([M] pass, 2026-10-13; supersede [L] rows above where they conflict)
- Food-directed crawl speed 1-3 cm/min (0.02-0.05 cm/s); starved snails 0.18-1.30 cm/min faster; nearer bait gives more arrivals [M1]. CORRECTS 'food rush 3-5 cm/s' which is ~100x too fast; use ~0.03 cm/s (up to ~0.07 cm/s when starved). A 30 cm approach then takes ~10-30 min, not 10-60 s. Burial time stays [L].

## Priority fixes for Opus
1. Buried with siphon visible.
2. Mass emergence race at feeding.
3. Sand disturbance as they move.
