# Mushroom coral (Discosoma / Rhodactis)

Sim hook: `CoralKind.MUSHROOM` · Adult size: disc 5–15 cm across [L] · Tank count: 2–5 discs [L]

## Sources
| # | Video / page | URL | What it shows |
|---|---|---|---|
| V1 | Top Shelf Aquatics Discosoma page | https://topshelfaquatics.com/pages/corals/tsa-disco-mushroom-coral | Low to moderate light and gentle flow |
| V2 | Reefbuilders overview of Discosoma | https://reefbuilders.com/2024/07/05/panic-at-the-discosoma-an-overview-of-discosoma-spp-corallimorphs | Corallimorph overview (care) |
| V3 | Bongaerts et al. 2012 (mushroom corals overcome burial) | https://repository.naturalis.nl/pub/801784/Bongaerts-2012-Mushroom-corals-overcome-live-burial-A.pdf | Time-lapse of inflation/deflation pulses, 10–20 min apart, up to 5x size. Fungiid stony mushroom corals, not corallimorphs; analogy only |
| V4 | Science News article on the same topic | https://www.sciencenews.org/?p=3150171 | Mushroom corals 'walk' by pulsing polyps |
| V5 | YouTube: Mushroom Coral Time-lapse | https://www.youtube.com/watch?v=t-dz_p-4YLI | Title only, not viewed [M] |
| V6 | YouTube: Mushroom coral moving | https://www.youtube.com/watch?v=SWi5OXMzs10 | Title only; may show creep [M] |
| V7 | YouTube: Coral behavior in time lapse, 4K | https://www.youtube.com/watch?v=LueqlKAa0UQ | Snippet: unclear species; not confirmed as mushroom [M] |

Video search (youtube.com only) found mushroom timelapse clips (V5, V6), but none confirmed as Discosoma or Rhodactis. Not viewed; claims are from snippets.

## Animation spec (for the renderer)
| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Polyp/tissue motion | Disc inflates and deflates in pulses (V3, fungiid analogy) | Inflate 1–3 min, deflate 3–5 min, pulse every 10–20 min | V3 [M] (analogy) |
| Sway in flow | Gentle flow preferred (V1); no sway data | Small disc tilt, ~0.05 Hz [L] | V1 [M] / [L] |
| Open/close vs lights | No source | Disc spreads over ~30 min after lights on; contracts over ~30 min after lights off | [L] |
| Retraction on touch | No source | Disc contracts in ~5–10 s; polyps pull in | [L] |
| Recovery after touch | No source | Re-open over ~10–30 min | [L] |
| Feeding response | No source | Disc rises and tentacles curl slightly | [L] |
| Deflation / shedding | Deflation pulses (V3) | Slow inflate-deflate cycle as above | V3 [M] |
| Colour under blue light | No source | Colour flat under blue light | [L] |
| Growth / spread | Discs multiply by budding (general) | Slow spread across rock | [L] |
| Unhappy look | No source | Disc stays deflated, pale, not pulsing | [L] |

## Behaviour spec (for the AI)
| Aspect | Observation | Suggested rule / numbers | Cite |
|---|---|---|---|
| Zone & territory | Carpets rock surfaces | Low, on rock or sand | V1 [M] |
| Day / night routine | No source | Open by day, partly closed at night | [L] |
| Feeding | No source | Passive; disc lifts slightly | [L] |
| Social / interactions | Spreads and can overgrow neighbours | Slow creep | V2 [M] / [L] |
| Reaction to parameters | Low to moderate light, gentle flow (V1) | Strong light or flow = closed/deflated | V1 [M] |
| Distinctive tells | Flat disc with many small polyps | Pulse-like inflate and deflate | V3 [M] (analogy) |

## Priority fixes for Opus
1. Animate the disc as a slow inflate-deflate pulse on a 10–20 min cycle.
2. Show a gentle creep across rock, not a static disc.
3. Add a closed, pale state for strong light or flow.
