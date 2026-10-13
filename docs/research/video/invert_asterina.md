# Asterina starfish (Aquilonastra)

Sim hook: `BrainKind.ASTERINA` · Adult size: 1-1.5 cm · Tank count: 10-30

## Sources
| # | Video / page | URL | What it shows |
|---|---|---|---|
| V1 | Little white Starfish in saltwater aquarium (YouTube) | https://www.youtube.com/watch?v=XLAfIWchgz4 | on glass - from search snippet, not watched; for hand review |
| V2 | Harlequin Shrimp Asterina Starfish Eating Reef Tank Assassin! (YouTube) | https://www.youtube.com/watch?v=9IeDamP3Pss | harlequin predation - from search snippet, not watched; for hand review |
| V3 | [PART 3] Harlequin Shrimp Asteria's Starfish Scared and On the Run! (YouTube) | https://www.youtube.com/watch?v=otYPhgaH1yI | stars climb glass when shrimp added [M] - from search snippet, not watched; for hand review |
| S1 | Aquanerd | https://aquanerd.com/?p=9664 | timelapse clip of asterina grazing on glass (not watched) |
| S2 | Reef Builders intro | https://reefbuilders.com/2016/03/30/asterina-starfish-an-introduction-to-this-common-reef-aquarium-c | hitchhiker |
| S3 | A. stellifera crawling paper | https://revistas.ucr.ac.cr/index.php/rbt/article/download/46392/46133?inline=1 | crawling speed, current |

No YouTube pages surfaced by WebSearch (not fetched); no [H] claims. Numbers marked [L] are estimates from general crustacean/echinoderm knowledge.

## Animation spec (for the renderer)
| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Locomotion mode | tube-foot gliding, no arm rowing | | [L] |
| Cruise speed | similar to comparable-size stars | 0.1-0.3 cm/s (~6-18 cm/min) | [S3][M] number [L] |
| Burst | none | | |
| Posture | flat on glass, 5 short arms | | [L] |
| Idle | grips corners | | [S2][M] |
| Turning | wander; no turn | path wobble | [L] |
| Colour | salmon/tan | | [S2][M] |

## Behaviour spec (for the AI)
| Aspect | Observation | Suggested rule / numbers | Cite |
|---|---|---|---|
| Zone | glass and rock crevice; hide by day among corals | | [S2][M] |
| Day / night | more visible at night | | [L] |
| Feeding | grazes film algae, especially dirty glass | | [S2][M] |
| Social | none; population grows | | [L] |
| Parameters | current makes crawl faster and straighter | | [S3][M] |
| Tells | tiny star, slowly roams glass | | [L] |

## Priority fixes for Opus
1. Very slow glass glide (about 0.2 cm/s).
2. Many tiny stars clustered at corners/crevices.
3. Dirty glass attracts them.
