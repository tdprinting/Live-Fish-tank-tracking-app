# Kenya tree coral (Capnella)

Sim hook: `CoralKind.KENYA` · Adult size: 30–60 cm colony [L] · Tank count: 1–2 colonies [L]

## Sources
| # | Video / page | URL | What it shows |
|---|---|---|---|
| V1 | Quality Marine care page | https://www.qualitymarine.com/quality-marine/coral/corals-soft/capnella/kenya-tree-51536/ | Prefers moderate–strong flow, moderate–high light |
| V2 | Cleveland Metroparks resource library | https://resourcelibrary.clevelandmetroparks.com/animals/287 | Says polyps are non-retractable |
| V3 | Clemetzoo resource library (Capnella spicata) | https://resourcelibrary.clemetzoo.com/Specie/287 | Same non-retractable claim |
| V4 | Orafarm article | https://orafarm.com/?p=4535 | Says polyps retract when colony is agitated |
| V5 | YouTube: Kenya Tree Coral (Capnella imbricata) | https://www.youtube.com/watch?v=Ucia-UzNFtc | Snippet: colony in view; hobbyist clip [M] |
| V6 | YouTube Short: Close Up Ep. 1, Kenya tree | https://www.youtube.com/shorts/rUfCPIlaTvU | Snippet: polyps seen opening and closing [M] |
| V7 | YouTube: Kenya Tree Coral in the water flow | https://www.youtube.com/watch?v=PJMnC7Y97xg | Snippet: colony in flow; title only, not viewed [M] |

Video search (youtube.com only) found hobbyist clips of polyps opening and closing (V6). No footage was viewed; claims are from snippets. Sources conflict on retraction.

## Animation spec (for the renderer)
| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Polyp/tissue motion | Sources conflict: non-retractable (V2, V3) vs retracts when agitated (V4) | Slow tissue swell and partial pull-in, not full polyp vanish | V2–V4 [M] |
| Sway in flow | Moderate–strong flow preferred; no sway measurements | Branch sway small amplitude, ~0.1–0.3 Hz | [L] |
| Open/close vs lights | No source | Gradual change over 30–60 min after lights on/off | [L] |
| Retraction on touch | Partial pull-in if V4 correct | Retract in 1–3 s | V4 [M] / [L] |
| Recovery after touch | No source | Re-extend over 5–15 min | [L] |
| Feeding response | No source | Slow polyp fan-out over 1–2 min | [L] |
| Deflation / shedding | No source found | Slow shrink of branch tips over days; treat as unknown | [L] |
| Colour under blue light | No source | Keep base colour, slight glow only at branch tips | [L] |

## Behaviour spec (for the AI)
| Aspect | Observation | Suggested rule / numbers | Cite |
|---|---|---|---|
| Zone & territory | Wild colonies on reef crests and flats | Mid-to-upper tank, bright area | V1 [M] |
| Day / night routine | No source | Polyps mostly out by day, partly closed at night | [L] |
| Feeding | No source | Passive; no grab behaviour modelled | [L] |
| Social / interactions | No source | Keep away from sweepers / aggressive neighbours | [L] |
| Reaction to parameters | Prefers moderate–strong flow, moderate–high light | Low flow or low light = sluggish, drooped | V1 [M] |
| Distinctive tells | Club- or tree-shaped branching colony; fleshy stalks | Branch tips sway with flow direction | V1 [M] / [L] |
| Unhappy look | No source | Shrunken, drooping branches; pale tips | [L] |

## Priority fixes for Opus
1. Pick one retraction behaviour and show it as a visible partial pull-in; the sources contradict each other, so keep it mild.
2. Make the colony sway with a flow vector (small amplitude, slow), not a fixed loop.
3. Add a slow photoperiod-linked open/close curve (30–60 min lag) so it does not snap open with the lights.
