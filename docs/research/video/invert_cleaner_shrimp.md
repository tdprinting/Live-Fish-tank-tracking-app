# Skunk cleaner shrimp (Lysmata amboinensis)

Sim hook: `BrainKind.CLEANER_SHRIMP` · Adult size: 5-6 cm · Tank count: 1-2 (pair)

## Sources
| # | Video / page | URL | What it shows |
|---|---|---|---|
| V1 | Scarlet Skunk Cleaner Shrimp Cleaning Yellow Tang (YouTube) | https://www.youtube.com/watch?v=hG65UhqbY48 | cleaning a tang - from search snippet, not watched; for hand review |
| V2 | Skunk Cleaner Shrimp Cleaning Mated Pair of Clownfish (YouTube) | https://www.youtube.com/watch?v=1hYvqBAUZUY | cleaning clownfish - from search snippet, not watched; for hand review |
| V3 | Scarlet Skunk Cleaner Shrimp cleaning a fish (YouTube) | https://www.youtube.com/watch?v=oV3Zx0emsUA | cleaning incl. angelfish mouth per snippet - from search snippet, not watched; for hand review |
| S1 | Sydney Zoo fact sheet | https://sydneyzoo.com/animals/skunk-cleaner-shrimp | antenna sway + rocking dance |
| S2 | Biology Letters 2019 (PMC) | https://pmc.ncbi.nlm.nih.gov/articles/PMC6769148 | risk-adjusted cleaning of predators |
| S3 | Reef Builders spotlight | https://reefbuilders.com/2016/12/22/invertebrate-spotlight-the-skunk-cleaner-shrimp | care/behaviour |
| M1 | Caves, Chen & Johnsen 2019, Biol. Lett. (PMC6769148, read) | https://pmc.ncbi.nlm.nih.gov/articles/PMC6769148 | 241 field interactions, leg rocking stats |
| M2 | Wikipedia Lysmata amboinensis (read) | https://en.wikipedia.org/wiki/Lysmata_amboinensis | size, colour, molt, night cleaning |

NOTE (2026-10-13 footage pass): no footage watched, no [H] rows; [M] sourcing pass done afterwards (M# rows). Wikimedia Commons API/upload hosts returned HTTP 429 (Retry-After 600) for this server during the pass, and YouTube is blocked; candidate Commons clips found but not downloaded (cleaner shrimp: File:Pacific cleaner shrimp and leopard moray eel.webm; Nassarius: File:Gevlochten fuikhoorn kruipend over de zandbodem-4931664.webm). YouTube V# rows stay queued for hand review. Numbers marked [L] are estimates from general crustacean/echinoderm knowledge.

## Animation spec (for the renderer)
| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Locomotion mode | walking legs on rock; pleopod swim short hops | walk | [L] |
| Cruise speed | slow crawl | 1-2 cm/s | [L] |
| Burst / startle | tail-flip | 20-30 cm/s, 0.3 s | [L] |
| Tail / fin beat | pleopods fan constantly | 6-8 Hz | [L] |
| Body posture & pitch | raised on legs at station, body angled up | pitch +15 deg | [L] |
| Idle / hover | at station swaying antennae and rocking side to side | rock ~1 Hz, 3 s bursts | [S1][M] numbers [L] |
| Turning | pivot | 0.5 s | [L] |
| Colour | red with white dorsal stripe | fixed | [M] |

## Behaviour spec (for the AI)
| Aspect | Observation | Suggested rule / numbers | Cite |
|---|---|---|---|
| Zone & territory | fixed cleaning station on coral head / cave mouth | stay within 10 cm of station | [S3][M] |
| Day / night routine | cave/crevice, often pairs | rests in cave at night | [L] |
| Feeding | picks parasites/mucus/dead skin, even from predator mouths | clean cycle 5-20 s per fish | [S2][M], duration [L] |
| Social / interactions | fish queue; will climb onto fish; assesses risk with predators | invite dance when fish within 30 cm; hop on if fish still | [S1][S2][M] |
| Reaction to parameters | - | - | |
| Distinctive tells | antenna wave + side-rocking dance; climbs on fish; cleans still hand | | [S1][M] |

## Sourced values ([M] pass, 2026-10-13; supersede [L] rows above where they conflict)
- Leg rocking occurs in 51% of interactions with predatory clients vs 12% non-predatory; rocking followed by cleaning in 45% vs 5% [M1]. Client was cleaned in 28% (predatory) vs 51% (non-predatory) of interactions; ~25-41% of clients pose then get cleaned, 25-44% pose then leave uncleaned [M1]. Occasional 'jolts' (0-3 per interaction) [M1]. Paper gives no cleaning-bout seconds or rocking Hz; those stay [L]. Shrimp watch clients on screen >5 s; interactions scored up to 1 min.
- Colour: pale amber body/legs, central white carapace band flanked by wider scarlet bands, red tail with symmetrical white spots, long white antennae (front pair forked); 5-6 cm [M2].
- Pairs in caves/ledges, 5-40 m; in captivity parasite removal mainly at night [M2] (sim's 'rests in cave at night' is doubtful). Post-larval molt every 3-8 weeks; mates shortly after a molt, pair alternate molt timing [M2].

## Priority fixes for Opus
1. Dance: antennae wave + rock at station when fish near.
2. Shrimp climbs onto stationary fish and picks.
3. Fixed station site, not roaming.
