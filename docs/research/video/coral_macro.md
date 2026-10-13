# Red macroalgae (Gracilaria / Botryocladia)

Sim hook: `CoralKind.MACRO` · Adult size: clump 10–30 cm [L] · Tank count: 1–3 clumps [L]

## Sources
| # | Video / page | URL | What it shows |
|---|---|---|---|
| V1 | Live-plants.com Botryocladia page | https://www.live-plants.com/botryocladia.htm | Medium growth; modest light and nutrients; grapes shed under stress and regrow |
| V2 | Algae Barn Gracilaria overview | https://algaebarn.com/blog/macro-algae/red-alga-gracilaria-overview | Gracilaria: moderate-to-slow growth; forms thick beds over time |
| V3 | Reefbuilders: propagating Gracilaria | https://reefbuilders.com/2009/10/17/propagating-gracilaria-for-fun-and-food | Fragments regrow; refugium use |
| V4 | YouTube: Botryocladia Red Grape marine macro algae | https://www.youtube.com/watch?v=6xN0JAOokh0 | Product listing; no timelapse or growth data [M] |
| V5 | Marine Agronomy, Gracilaria in Hawaiian fishponds | https://marineagronomy.org/node/144 | Gracilaria growth tied to ammonia (2–10 µM) at field sites; water motion among factors tested |
| V6 | Algae Barn Gracilaria overview | https://algaebarn.com/blog/macro-algae/red-alga-gracilaria-overview | Hobby guidance: moderate turbulent flow, palatable to herbivores |

**NOTE:** No footage was watched. Wikimedia Commons has no usable clip of this species, and the Commons/upload hosts rate-limited this server (HTTP 429, retry-after 600 s) so the nearest generic clips could not be downloaded. YouTube rows stay queued for hand review. This pass upgraded [M] sourcing only; nothing here is [H].

## Animation spec (for the renderer)
| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Polyp/tissue motion | No polyps; frond and grape motion only | Fronds sway with flow | [L] |
| Sway in flow | Hobby guidance: moderate, tumbling flow suits Gracilaria (V6); no measured sway Hz | Fronds sway ~0.1–0.3 Hz, small amplitude | V6 [M] / [L] |
| Open/close vs lights | No source; algae does not open and close | No open/close; slight colour change only | [L] |
| Retraction on touch | Grapes shed under stress (V1) | Grapes drop off, then regrow | V1 [M] |
| Recovery after touch | Fragments regrow (V1, V3) | Regrow over weeks | V1, V3 [M] |
| Feeding response | None | None | [L] |
| Colour under blue light | No source | Colour stays red-brown | [L] |
| Growth / spread | Botryocladia slow to medium; Gracilaria forms thick beds (V1, V2) | Slow bed growth over weeks to months | V1, V2 [M] |
| Unhappy look | Grapes shed, bleaching (V1) | Shed fronds, pale patches | V1 [M] |

## Behaviour spec (for the AI)
| Aspect | Observation | Suggested rule / numbers | Cite |
|---|---|---|---|
| Zone & territory | Attaches to rock; Gulf of Mexico offshore ledges for Botryocladia (V1) | Rock or refugium, low to mid | V1 [M] |
| Day / night routine | No source | None modelled | [L] |
| Feeding | Not applicable | None | [L] |
| Social / interactions | Gracilaria can overgrow tank in beds (V2) | Slow spread | V2 [M] |
| Reaction to parameters | Modest light and nutrients; stressed = shed grapes (V1) | Low nutrients = pale, sparse | V1 [M] |
| Distinctive tells | Red fronds or grape-like clusters | Grapes and fronds sway with flow | V1 [M] |

## Priority fixes for Opus
1. Animate fronds swaying with flow, not a static red texture.
2. Show grape shedding and regrowth under stress.
3. Keep growth slow (weeks to months); no visible open or close cycle.
