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
| M1 | Esaka et al. 2016, Pacific Science 70(2) (abstract read) | https://complete.bioone.org/journals/pacific-science/volume-70/issue-2/70.2.5/Effect-of-Light-Intensity-and-Wavelength-on-Diurnal-Activity-of/10.2984/70.2.5.full | S. hispidus nocturnal under 12:12 LD |
| M2 | Wikipedia Stenopus hispidus (read) | https://en.wikipedia.org/wiki/Stenopus_hispidus | size, bands, territory, pairing, dance |

NOTE (2026-10-13 footage pass): no footage watched, no [H] rows; [M] sourcing pass done afterwards (M# rows). Wikimedia Commons API/upload hosts returned HTTP 429 (Retry-After 600) for this server during the pass, and YouTube is blocked; candidate Commons clips found but not downloaded (cleaner shrimp: File:Pacific cleaner shrimp and leopard moray eel.webm; Nassarius: File:Gevlochten fuikhoorn kruipend over de zandbodem-4931664.webm). YouTube V# rows stay queued for hand review. Numbers marked [L] are estimates from general crustacean/echinoderm knowledge. Cite as [S1].

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

## Sourced values ([M] pass, 2026-10-13; supersede [L] rows above where they conflict)
- Active in DARK phase, inactive in light under 12:12 LD; weak circadian rhythm persists in constant dark; day/night gap shrinks under weak light; blue/green LED keep the pattern, red does not [M1]. CORRECTS the 'active day' row: default to nocturnal forager, resting in crevice by day.
- Total length to ~60 mm, females larger [M2]. Colour: ground transparent; carapace, abdomen and large 3rd pereiopods banded red/white; antennae and other legs white; body and claws spined [M2]. Texture note: translucent base with opaque red bands.
- Territory ~1-2 m diameter around home, monogamous pairs [M2] (sim's 15 cm radius is too small; use ~50 cm roam radius, [L] for sim). Advertises by slowly waving white antennae and a side-to-side dance near home [M2]; no Hz given.
- No walking speed, pleopod Hz or molt interval was found in a source I read; those rows stay [L].

## Priority fixes for Opus
1. Long white antennae sweeping from a ledge perch.
2. Hide in cave 1-2 days after molt, spawn exuvia.
3. Aggression toward other shrimp of any kind in territory.
