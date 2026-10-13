# Clove polyps (Clavularia)

Sim hook: `CoralKind.CLOVE` · Adult size: colony 5–10 cm across [L] · Tank count: 1–3 colonies [L]

## Sources
| # | Video / page | URL | What it shows |
|---|---|---|---|
| V1 | Wikipedia: Clavularia crassa | https://en.wikipedia.org/wiki/Clavularia_crassa | Colony of up to ~50 polyps on a stolon; 8 feathery tentacles. Note: this is a different species from the green clove polyp sold in aquariums |
| V2 | Circadian study, Dendrophyllia ramea (time-lapse) | https://ejournals.epublishing.ekt.gr/index.php/hcmr-med-mar-sc/article/view/34250 | Polyps open and close on a circadian rhythm, influenced by flow; analogy only |
| V3 | ReefCentral thread (ID/care) | https://www.nano-reef.com/forums/topic/418699-what-did-i-get-on-this-piece-of-rock/ | Colony can open up over time after purchase; forum ID thread |
| V4 | YouTube: Reef Lapse, 4K timelapse of clove polyps opening | https://www.youtube.com/watch?v=nHLFRPOp0xY | Snippet: 4K timelapse of clove polyps opening on flats; posted years ago [M] |
| V5 | YouTube: Coral Spotlight, clove polyps | https://www.youtube.com/watch?v=TJF1kL8z2Nc | Title only; care overview [M] |

Video search (youtube.com only) found one clove polyp opening timelapse (V4). Not viewed; claims are from snippets. No Clavularia-specific timing source was found.

## Animation spec (for the renderer)
| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Polyp/tissue motion | Polyps stand on a shared stolon; can be closed or open | Each polyp a small stalk with a tuft of tentacles | V1 [M] |
| Sway in flow | Flow affects polyp activity (V2 analogy) | Polyps sway gently, ~0.3–0.8 Hz [L] | V2 [M] / [L] |
| Open/close vs lights | Circadian open/close (V2 analogy) | Open over ~20–40 min after lights on; close over ~20–40 min after lights off | V2 [M] / [L] |
| Retraction on touch | No source | Polyps pull in over ~1 s | [L] |
| Recovery after touch | No source | Re-open over ~5–10 min | [L] |
| Feeding response | No source | Tentacles spread slightly during feeding | [L] |
| Colour under blue light | No source | Colour flat under blue light; no fluorescence modelled | [L] |
| Growth / spread | Colony grows from a stolon (V1) | Slow spread along a creeping base | V1 [M] |
| Unhappy look | Closed colony that stays closed (V3) | Polyps closed and still | V3 [M] |

## Behaviour spec (for the AI)
| Aspect | Observation | Suggested rule / numbers | Cite |
|---|---|---|---|
| Zone & territory | Encrusting or stolon-based colony | Low to mid tank | [L] |
| Day / night routine | Circadian open/close pattern (analogy from V2) | Open in light, close at night | V2 [M] / [L] |
| Feeding | No source | Passive | [L] |
| Social / interactions | No source | Spreads slowly | [L] |
| Reaction to parameters | Flow influences activity (V2 analogy) | Low flow = sluggish opening | V2 [M] / [L] |
| Distinctive tells | Small colony of thin stalks with feathery tentacles | Polyps on a shared base | V1 [M] |

## Priority fixes for Opus
1. Use a shared stolon base with individual polyps on stalks, not separate blobs.
2. Open/close on a circadian curve linked to lights, with a 20–40 min lag.
3. Keep motion gentle and flow-linked, with no fast snapping.
