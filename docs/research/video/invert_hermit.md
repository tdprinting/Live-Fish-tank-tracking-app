# Hermit crabs (Clibanarius tricolor, Paguristes cadenati, Calcinus)

Sim hook: `BrainKind.HERMIT` · Adult size: 1-3 cm body, shells 1.5-4 cm · Tank count: 4-8

## Sources
| # | Video / page | URL | What it shows |
|---|---|---|---|
| V1 | Hermit crab changing shells in a reef tank (YouTube) | https://www.youtube.com/watch?v=FfahZPSqOZQ | reef-tank crab inspecting empty shell - from search snippet, not watched; for hand review |
| V2 | Hermit Crab Changing shells - CLOSE UP and in SLOW MOTION! (YouTube) | https://www.youtube.com/watch?v=24yfNJRvbKU | slow-mo swap - from search snippet, not watched; for hand review |
| V3 | Hermit Crabs LINE UP To Swap Shells! (YouTube) | https://www.youtube.com/watch?v=zpjklLt1qWk | vacancy-chain swap (wild) - from search snippet, not watched; for hand review |
| S1 | TFH Magazine | https://tfhmagazine.com/articles/saltwater/clibanarius-tricolor | shell battles |
| S2 | Fishprofiles | https://fishprofiles.com/profiles/crustaceans/Clibanarius_tricolor/ | snail attack, shell stealing |
| S3 | Wikipedia | https://en.wikipedia.org/wiki/Clibanarius_tricolor | species facts |

No YouTube pages surfaced by WebSearch (not fetched); no [H] claims. Numbers marked [L] are estimates from general crustacean/echinoderm knowledge.

## Animation spec (for the renderer)
| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Locomotion mode | walking legs 2 pairs drag shell, claws held forward | 4-leg walk with shell on abdomen | [L] |
| Cruise speed | jerky | 1-3 cm/s with stops every 1-3 s | [L] |
| Burst / startle | retract into shell | 0.2 s, stays 5-15 s | [L] |
| Tail / fin beat | none | | |
| Body posture & pitch | shell tilted, front tipped down when foraging | | [L] |
| Idle / hover | still, antennae twitching | | [L] |
| Turning | pivot with shell | slow 1 s | [L] |
| Shell swap | inspect new shell with legs/claws, fast switch ~1-2 s | 3-8 s inspection | [L] |

## Behaviour spec (for the AI)
| Aspect | Observation | Suggested rule / numbers | Cite |
|---|---|---|---|
| Zone & territory | roam all rock/sand/glass | | [L] |
| Day / night | active both | | [L] |
| Feeding | scavenge algae/detritus/meaty | | [L] |
| Social / interactions | fight each other for shells (loser damaged/dead); may attack snails to steal shells | Nassarius/Astrea snails at risk; hermit rams snail, pulls it out | [S1][S2][M] |
| Parameters | copper intolerant | | [S3][M] |
| Distinctive tells | shell-testing, shell fight, drags shell | | [S1][M] |

## Priority fixes for Opus
1. Stop-go walking with shell drag and retract-on-startle.
2. Shell swap animation with spare shells.
3. Shell-stealing from snails (visible predation).
