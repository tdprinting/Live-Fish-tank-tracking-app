# Aiptasia (Exaiptasia diaphana)

Sim hook: `CoralKind.AIPTASIA` · Adult size: polyp 1–5 cm across [L] · Tank count: 3–10 polyps [L]

## Sources
| # | Video / page | URL | What it shows |
|---|---|---|---|
| V1 | PeerJ: Exaiptasia pedal laceration development | https://peerj.com/articles/12770 | Lacerates develop tentacles; symbiotic lacerates end with more tentacles by day 20 |
| V2 | Wikipedia: Pedal laceration | https://en.wikipedia.org/wiki/Pedal_laceration | Pedal laceration as asexual reproduction; tearing and constriction |
| V3 | PodYourReef aiptasia care blog | https://www.podyourreef.com/blogs/care/aiptasia-anemones-the-invasive-pest-of-reef-aquariums | Attempts to remove them can trigger laceration; pest overview |

Video search (youtube.com only) found no Aiptasia laceration timelapse. No footage was viewed. Lacerate timing (about a week) is from a search summary of the PeerJ study, not verified in full text.

## Animation spec (for the renderer)
| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Polyp/tissue motion | Tentacles and column; pedal disc attaches to rock | Tentacles wave slowly, column expands and contracts | [L] |
| Sway in flow | No sway data | Tentacles lean with flow, ~0.2–0.5 Hz [L] | [L] |
| Open/close vs lights | Aiptasia with symbionts expand in light (general) | Expanded by day, contracted at night, over ~30 min [L] | [L] |
| Retraction on touch | Not sourced | Column contracts and tentacles pull in in ~2–5 s | [L] |
| Recovery after touch | Not sourced | Re-expand over ~10–30 min | [L] |
| Feeding response | Not sourced | Tentacles curl to mouth | [L] |
| Colour under blue light | Not sourced | Colour stays; no fluorescence modelled | [L] |
| Growth / spread | Pedal laceration: small pieces detach and become new polyps (V1, V2) | New polyps appear around the base, about a week after laceration (V1 summary) | V1, V2 [M] |
| Unhappy look | Not sourced | Shrunk column, tentacles pulled in | [L] |

## Behaviour spec (for the AI)
| Aspect | Observation | Suggested rule / numbers | Cite |
|---|---|---|---|
| Zone & territory | Pedal disc on rock or glass | Low, fixed | [L] |
| Day / night routine | Not sourced | Expanded by day, contracted at night | [L] |
| Feeding | Not sourced | Passive | [L] |
| Social / interactions | Aiptasia spreads by laceration and budding; pests (V3) | Spreads in clumps | V1, V3 [M] |
| Reaction to parameters | Not sourced | None modelled | [L] |
| Distinctive tells | Small anemone with many thin tentacles | Spreads by laceration, not by moving | V1 [M] |

## Priority fixes for Opus
1. Add pedal laceration as a visible spread: small pieces break off and grow into new polyps over about a week.
2. Keep the polyp fixed to the rock; spread only by new polyps appearing at the base.
3. Keep tentacles animated but mostly still, with a slow expand and contract cycle.
