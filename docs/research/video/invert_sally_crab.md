# Sally lightfoot crab (Percnon gibbesi)

Sim hook: `BrainKind.SALLY_CRAB` · Adult size: 5-6 cm carapace, 10+ cm leg span · Tank count: 1

## Sources
| # | Video / page | URL | What it shows |
|---|---|---|---|
| V1 | Saltwater Sally Lightfoot Crab (Percnon gibbesi) (YouTube) | https://www.youtube.com/watch?v=K7GZrWZmEAM | aquarium footage (snippet only) - from search snippet, not watched; for hand review |
| V2 | An Aquarium Guide to the Sally Lightfoot Crab (YouTube) | https://www.youtube.com/watch?v=XH9TKkfnFWE | behaviour/interaction in 90 gal - from search snippet, not watched; for hand review |
| V3 | Sally Lightfoot Crab Eating in Stunning Color and Detail (YouTube) | https://www.youtube.com/watch?v=hodz6SwvEpo | eating close-up (species unconfirmed) - from search snippet, not watched; for hand review |
| S1 | Top Shelf Aquatics | https://topshelfaquatics.com/pages/saltwater-invertebrates/sally-lightfoot-crab | algae eater, rock/hiding |
| S2 | Live Aquaria | https://www.liveaquaria.com/products/sally-lightfoot-crab | care |
| S3 | Kraken Corals | https://krakencorals.co.uk/livestock/sally-lightfoot-crab.html | care |
| M1 | Wikipedia Percnon gibbesi (read) | https://en.wikipedia.org/wiki/Percnon_gibbesi | size, legs, diet, habitat |

NOTE (2026-10-13 footage pass): no footage watched, no [H] rows; [M] sourcing pass done afterwards (M# rows). Wikimedia Commons API/upload hosts returned HTTP 429 (Retry-After 600) for this server during the pass, and YouTube is blocked; candidate Commons clips found but not downloaded (cleaner shrimp: File:Pacific cleaner shrimp and leopard moray eel.webm; Nassarius: File:Gevlochten fuikhoorn kruipend over de zandbodem-4931664.webm). YouTube V# rows stay queued for hand review. Numbers marked [L] are estimates from general crustacean/echinoderm knowledge.

## Animation spec (for the renderer)
| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Locomotion mode | sideways walking, 4 leg pairs, flat body | sideways crab walk | [L] |
| Cruise speed | very fast for a crab | 3-8 cm/s, bursts 20 cm/s | [L] |
| Burst / startle | scuttle sideways into crevice | 20-30 cm/s | [L] |
| Posture | flat body wedged on vertical rock, legs spread | | [L] |
| Idle | pressed in crevice, small claw movements | | [L] |
| Grazing | alternating small claws to mouth, 1-2 Hz | | [L] |
| Turning | none needed; sidesteps | | [L] |
| Colour | mottled dark red/brown (adults), juvs more orange | | [L] |

## Behaviour spec (for the AI)
| Aspect | Observation | Suggested rule / numbers | Cite |
|---|---|---|---|
| Zone | rockwork, needs many crevices | stay near rock, sometimes glass | [S1][M] |
| Day / night | - | more active at lights-out [L] | |
| Feeding | picks algae (hair algae favoured) with claws; supplement with seaweed/meaty | graze 5-10 s then move | [S1][M] |
| Social | may eat small fish/snails/inverts if underfed | | [S1][M] |
| Parameters | - | | |
| Tells | flat spidery shape, skittish sideways dash | | [L] |

## Sourced values ([M] pass, 2026-10-13; supersede [L] rows above where they conflict)
- Carapace ~30 mm wide; legs show yellow rings at joints; each walking-leg pair carries spines along the leading edge [M1]. Strictly herbivorous (grazes algae); lives among boulders [M1]. Deudero et al. 2005 (MEPS 285:151) reportedly finds day-active with dusk peak, but I could only see a search snippet (page returned 401), so treat as unverified. No speed source found; speed stays [L].

## Priority fixes for Opus
1. Sideways skittering with fast dash to crevice.
2. Two-claw alternating algae picking.
3. Rock-hugging flat posture.
