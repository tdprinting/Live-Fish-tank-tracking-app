# Coral banded shrimp (Stenopus hispidus)

Sim hook: `BrainKind.BANDED_SHRIMP` · Adult size: 5-6 cm body, antennae 2x longer · Tank count: 1 (or mated pair)

## Sources
| # | Video / page | URL | What it shows |
|---|---|---|---|
| V1 | Feeding Coral Banded Shrimp! 13.5 Nano Saltwater Setup day 17 (YouTube) | https://www.youtube.com/watch?v=HsxA9i2urGs | feeding (snippet only) - from search snippet, not watched; for hand review |
| V2 | Coral banded shrimp eating bristle worms! (YouTube) | https://www.youtube.com/watch?v=Yuxr4EQmmsY | eats bristle worms - from search snippet, not watched; for hand review |
| V3 | Banded Coral Shrimp Feeding Frenzy (YouTube) | https://www.youtube.com/watch?v=r7-t1GwQVzw | feeding (snippet only) - from search snippet, not watched; for hand review |
| S1 | Reefs.com care page | https://reefs.com/invertebrates/coral-banded-shrimp/ | care/behaviour text |
| S2 | Top Shelf Aquatics | https://topshelfaquatics.com/pages/saltwater-invertebrates/coral-banded-shrimp | territoriality, active forager |
| S3 | Pacific Science 2016 light/activity study | https://muse.jhu.edu/article/633289/ | daily activity vs light |

No YouTube pages surfaced by WebSearch (not fetched); no [H] claims. Numbers marked [L] are estimates from general crustacean/echinoderm knowledge. Cite as [S1].

## Animation spec (for the renderer)
| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Locomotion mode | Walking legs (pereiopods), pleopod swimming rare | alternating-leg walk, tripod-ish gait | [L] |
| Cruise speed | "scampers" foraging | 1-3 cm/s walk, pauses every 1-2 s | [S2][M] speed [L] |
| Burst / startle | tail-flip backward | 25-40 cm/s for 0.3 s, then hide | [L] |
| Tail / fin beat | pleopods flutter in place | 5-8 Hz when still | [L] |
| Body posture & pitch | perched on rock, body raised, long white antennae sweeping forward, claws held out | pitch +10 deg | [L] |
| Idle / hover | stationary at ledge entrance, antennae sweeping slowly | antenna sweep ~0.5 Hz | [L] |
| Turning | pivot in place, smooth | 90 deg/0.5 s | [L] |
| Colour / pattern changes | red/white bands; molt = pale exuvia | n/a | [M] |

## Behaviour spec (for the AI)
| Aspect | Observation | Suggested rule / numbers | Cite |
|---|---|---|---|
| Zone & territory | under ledges/crevices, fights own species | home cave, 15 cm radius; aggressive to conspecifics/small shrimp | [S1][S2][M] |
| Day / night routine | less nocturnal than most decapods | active day with ledge-perching; 1-2 day hide when molting | [S3][M] |
| Feeding | scavenger/forager | roam rocks, grab food with claws to mouth | [S2][M] |
| Social / interactions | pairs, male follows female | cleaner-station posture for fish | [S1][M] |
| Reaction to parameters | molting vulnerable | hide 1-2 days | [S1][M] |
| Distinctive tells | long white antennae, red-white bands, big white claws held forward | | [M] |

## Priority fixes for Opus
1. Long white antennae sweeping from a ledge perch.
2. Hide in cave 1-2 days after molt, spawn exuvia.
3. Aggression toward other shrimp of any kind in territory.
