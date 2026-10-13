# Organ pipe coral (Tubipora musica)

Sim hook: `CoralKind.ORGAN_PIPE` · Adult size: colony 30–60 cm [L] · Tank count: 1 colony [L]

## Sources
| # | Video / page | URL | What it shows |
|---|---|---|---|
| V1 | Fact sheet (gbri.org.au student page) | https://gbri.org.au/Classes/2012/Tubiporamusica%7CHelenadeOlivera | Tentacles extended when feeding, retracted when disturbed |
| V2 | Fishkeeper UK care page | https://www.fishkeeper.co.uk/fish/marine/soft-corals/organ-pipe-coral | Usually extended by day; swift withdrawal when disturbed |
| V3 | Animal World care page | https://animal-world.com/organ-pipe-coral | Filter-feeds small particles; hosts symbionts |
| V4 | Archived circadian paper (T. musica) | https://archive.org/download/biostor-9372/biostor-9372.pdf | Expansion/contraction tracked in light, then 60 h darkness, with zooplankton added. Results not read: verify |
| V5 | YouTube: Organpipe coral extending polyps (Heron Island, 300x) | https://www.youtube.com/watch?v=bmRIJ_rrrZQ | Snippet: 2 h timelapse sped up 300x; polyp extension [M] |
| V6 | YouTube: Organ Pipe Coral care tips | https://www.youtube.com/watch?v=AHWmIX-CSOg | Care overview; flow and feeding [M] |
| V7 | Wikipedia, Organ pipe coral | https://en.wikipedia.org/wiki/Organ_pipe_coral | Tentacles extended by day, swiftly withdrawn on disturbance; retraction spreads as a wave across the colony from the touched point (electrical and mechanical stimulation) |

**NOTE:** No footage was watched. Wikimedia Commons has no usable clip of this species, and the Commons/upload hosts rate-limited this server (HTTP 429, retry-after 600 s) so the nearest generic clips could not be downloaded. YouTube rows stay queued for hand review. This pass upgraded [M] sourcing only; nothing here is [H].

## Animation spec (for the renderer)
| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Polyp/tissue motion | Tentacles extended by day and when feeding; withdrawn when disturbed (V7) | Tentacles fan out from tubes; tubes stay still | V1, V7 [M] |
| Sway in flow | Some sources say steady one-way flow stresses it (general) | Tentacles sway a little, ~0.2–0.5 Hz [L] | [L] |
| Open/close vs lights | Usually extended by day (V2) | Extend over ~20–40 min after lights on; retract over ~20–40 min after lights off [L] | V2 [M] / [L] |
| Retraction on touch | Swift withdrawal; wave of retraction spreads away from the disturbed spot (V7); latency in seconds not found | Retract in ~1–2 s at touch point, wave spreads across colony over several s | V7 [M] / [L] |
| Recovery after touch | No source | Re-extend over ~5–15 min | [L] |
| Feeding response | Extended when feeding (V1, V3) | Tentacles spread wide when feeding | V1, V3 [M] |
| Colour under blue light | No source | White-pale retracted tips (V1 photo site description) | [L] |
| Growth / spread | Colony grows by new tubes (general) | Slow tube additions over months | [L] |
| Unhappy look | No source | Retracted and not re-extending | [L] |

## Behaviour spec (for the AI)
| Aspect | Observation | Suggested rule / numbers | Cite |
|---|---|---|---|
| Zone & territory | Tubes on reef flats and slopes | Mid to low, fixed in place | [L] |
| Day / night routine | Extended by day (V2) | Extended in light, mostly retracted at night | V2 [M] / [L] |
| Feeding | Filter-feeds; extends for food (V1, V3) | Extended when feeding | V1, V3 [M] |
| Social / interactions | No source | Keep away from stinging neighbours [L] | [L] |
| Reaction to parameters | Steady one-way flow may stress (general) | Strong steady flow = fewer extended polyps | [L] |
| Distinctive tells | Red-brown tubes with eight feathery tentacles | Tentacles visible only when open | V1 [M] |

## Priority fixes for Opus
1. Keep tubes fixed; animate only tentacles fanning out and in.
2. Use a day-extended, night-retracted rhythm with a 20–40 min lag.
3. Snap retraction on touch (about 1 s), then re-extend slowly.
