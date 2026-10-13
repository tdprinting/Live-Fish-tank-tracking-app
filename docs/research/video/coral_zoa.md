# Zoanthids / palythoa (Zoanthus, Palythoa)

Sim hook: `CoralKind.ZOA` · Adult size: colony 5–15 cm across [L] · Tank count: 1–3 colonies [L]

## Sources
| # | Video / page | URL | What it shows |
|---|---|---|---|
| V1 | Queen City Corals care guide | https://www.queencitycorals.com/?p=95131881 | Healthy zoas open fully during photoperiod; closed or washed out colour suggests too much light |
| V2 | SG Reef Club thread | https://sgreefclub.com/forum/topic/78453-how-to-increase-zoas-poylps | Anecdote: polyps begin opening about an hour after lights out; flow may inhibit extension |
| V3 | ReefCentral thread | https://forums.reefcentral.com/threads/zoas-are-closed.499497 | Closed causes: pests, nutrition, water parameters, flow/light, irritation |
| V4 | Photobehaviour study (stony corals) | https://cob.silverchair.com/jeb/article/206/22/4041/14111/Photobehavior-of-stony-corals-responses-to-light | Light and food alter tentacle expansion; analogy only |
| V5 | YouTube: Nano reef zoanthid time lapse | https://www.youtube.com/watch?v=pK2d2CfeBro | Title only, not viewed [M] |
| V6 | YouTube: Zoa corals closed? | https://www.youtube.com/watch?v=8WSJBOG2Y_U | Snippet: closure attributed to aeolid nudibranchs (cut off) [M] |
| V7 | YouTube: Zoanthids not opening, possible reasons found | https://www.youtube.com/watch?v=3-gE8xMZURk | Title only; snippet gives strong flow as one cause, mapping to this video unconfirmed [M] |
| V8 | Sorek 2018 / Favia analogy | https://www.ncbi.nlm.nih.gov/pmc/articles/PMC3089635/ | No zoanthid-specific rhythm or fluorescence study found; Favia fragum shows light-entrained daily tentacle expansion (analogy only) |
| V9 | Photobehaviour of corals, 400–520 nm | https://cob.silverchair.com/jeb/article/206/22/4041/14111/Photobehavior-of-stony-corals-responses-to-light | Blue 400–520 nm most effective at triggering full tentacle contraction in some stony corals (analogy) |

**NOTE:** No footage was watched. Wikimedia Commons has no usable clip of this species, and the Commons/upload hosts rate-limited this server (HTTP 429, retry-after 600 s) so the nearest generic clips could not be downloaded. YouTube rows stay queued for hand review. This pass upgraded [M] sourcing only; nothing here is [H].

## Animation spec (for the renderer)
| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Polyp/tissue motion | Polyps open as a full disc; closed polyps are rounded | Each polyp open/close as a disc of ~5–10 mm [L] | V1 [M] |
| Sway in flow | Flow can inhibit extension (V2) | Polyps sway a little in strong flow, ~0.2–0.5 Hz [L] | V2 [M] / [L] |
| Open/close vs lights | Open during photoperiod (V1); opening about 1 h after lights out (V2) | Open ~30–60 min after lights on; open again ~60 min after lights off (V2 anecdote) | V1, V2 [M] |
| Retraction on touch | No source | Polyps close over ~1–3 s | [L] |
| Recovery after touch | No source | Re-open over ~10–30 min | [L] |
| Feeding response | Polyps can open for food (general) | Partly open, tips lift | [L] |
| Colour under blue light | No zoanthid fluorescence study found; hobby morphs vary (green/orange/pink) | Keep base morph colour; mild glow on disc and tentacle ring | V9 [M] (analogy) / [L] |
| Growth / spread | Colony spreads by budding; carpets (general) | Slow sideways creep | [L] |
| Unhappy look | Closed for days, washed out colour, polyps reabsorbing (V3) | Keep closed, pale; slow shrink | V3 [M] |

## Behaviour spec (for the AI)
| Aspect | Observation | Suggested rule / numbers | Cite |
|---|---|---|---|
| Zone & territory | Encrusting colony on rock | Low to mid tank | [L] |
| Day / night routine | Open in photoperiod; open again about 1 h after lights out (anecdote) | Open ~30–60 min after lights on, reopen ~60 min after off | V1, V2 [M] |
| Feeding | Not sourced | Partial opening for food | [L] |
| Social / interactions | Nudibranchs, aiptasia, and starfish can keep colonies closed (V3) | Closed colonies near pests | V3 [M] |
| Reaction to parameters | Excess light: closed or pale (V1); flow may inhibit (V2) | Mild light/flow stress shows as partly closed | V1, V2 [M] |
| Distinctive tells | Carpet of small polyps with a rubbery look | Tight, rounded polyps when closed | [L] |

## Priority fixes for Opus
1. Make open/close linked to lights with a 30–60 min lag on lights on, and an evening reopen around 1 h after lights off.
2. Show closed polyps as tight, rounded, and pale rather than just hidden.
3. Animate a slow budding creep across the carpet, not a static texture.
