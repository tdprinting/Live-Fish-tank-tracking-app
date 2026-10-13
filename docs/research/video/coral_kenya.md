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
| V8 | WetWebMedia / hobbyist report (via search) | https://wetwebmedia.com/nephbehfaqs.htm | Nephtheid behaviour FAQ: colony opened daily in quarantine, stayed closed in high flow after move (anecdote) |
| V9 | PLOS ONE, Favia fragum circadian study | https://www.ncbi.nlm.nih.gov/pmc/articles/PMC3089635/ | Stony coral on light:dark cycle shows daily tentacle expansion/contraction; rhythm weakens in constant dark (analogy only) |

**NOTE:** No footage was watched. Wikimedia Commons has no usable clip of this species, and the Commons/upload hosts rate-limited this server (HTTP 429, retry-after 600 s) so the nearest generic clips could not be downloaded. YouTube rows stay queued for hand review. This pass upgraded [M] sourcing only; nothing here is [H].

## Animation spec (for the renderer)
| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Polyp/tissue motion | Sources conflict: non-retractable (V2, V3) vs retracts when agitated (V4) | Slow tissue swell and partial pull-in, not full polyp vanish | V2–V4 [M] |
| Sway in flow | Natural habitat is deeper slopes with stronger currents (V2); newly moved colony stayed closed in high flow (V8) | Branch sway small amplitude, ~0.1–0.3 Hz; closed/limp if flow too hard | V2 [M], V8 [M] / [L] |
| Open/close vs lights | Diel tentacle expansion/contraction is documented in other corals (V9); none for Capnella | Gradual change over 30–60 min after lights on/off | V9 [M] (analogy) / [L] |
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

## [H] First-hand: Tee's own Kenya tree (clip, 2026-10-13)

**Source:** `reference/kenya_clip/kenya_tree_tee.mov` (Tee's tank, 12.9 s, 480×360 rotated to 360×480 portrait, 24 fps, handheld with pan and zoom) plus `contact_1fps.png` / `contact_6fps.png`. Frames pulled with ffmpeg; motion tracked by patch matching (SSD, 24 px templates) on stalk, crown and rock points over the steadiest 4.6 s (frames 0–110); colours are medians of crown pixels at 0.5 s and 9.0 s. Everything in this section is **[H]**, first-hand, Tee's own footage.

### Form
| Aspect | Measured / seen | Model (`kenya.glb` v3) |
|---|---|---|
| Overall | One bushy colony, upright-to-dome, about as wide as tall: crown ≈ 295 px wide × ≈ 250 px tall at 0.5 s (W:H ≈ 1.1–1.2 incl. the short stalk) | 26 × 25 cm crown, top y 48 (same footprint as v2) |
| Silhouette | Width at 35 / 55 / 75 / 90 % of height ≈ 100 / 85 / 61 / 37 % of the max (front view) | 26 / 19 / 17 / 9 cm (seed 7 chosen to match) |
| Stalk | Short, thick, pale cream / translucent; ≈ 8–10 % of the crown width across, ≈ 15 % of the height; trunks fan from just above the rock pocket | 2.4 cm stalk (×1.4 after the height fit), r 2.2 cm with a flared foot; trunks leave its top third |
| Trunks | Several stout trunks: a near-upright central cluster plus a ring leaning out ≈ 50–75° from vertical | 3 central (14–30°) + 5 ring (50–72°) |
| Branching | Trunks divide repeatedly (≈ 4 more forks) into slender branches; forks ≈ 25–40°; centre arches up into a rounded top cluster; lower / outer branches arch outward and sag at the tips | forks 24–40° to depth 5; arching-up bias on near-upright branches, tip sag on the ones leaving > ~30° (capped ≈ 20° below horizontal) |
| Fringe | The whole crown is covered in fine feathery pinnate branchlets / polyp fringes; short spiky side twigs on every outer branch | ≈ 510 alternate-side twig tubes (55° off the branch, every ≈ 0.95 cm) + ≈ 2075 alpha-tested pinnate polyp sprigs |
| Setting | Grows out of a rock pocket, dark cave under it | unchanged anchor `coral_kenya_main` |

### Colour (sRGB medians from the clip, tank light included)
| Region | Median | Bright 10 % | Dark 25 % |
|---|---|---|---|
| Crown top | #977B78 | #CCB2B9 | #6D5855 |
| Crown middle | #977770 | #D0B1B5 | #745650 |
| Stalk | #856762 | #BB9FA6 | #70544B |
| Close-up (9 s) | #8C747A | #C6B0C8 | #6F5A60 |

Read: warm pinkish tan / beige-brown body, pale cream-white tips, a pink-lilac cast in the shade. Model textures (no app-side tint now): stalk #C9B3A8 → body #A27D70 with #98808A lilac-grey mottling → tips #D8C4BC; sprigs #A6806F → #DCC8C0 with #EFE4DE tips.

### Motion
| Aspect | Measured | Caveat |
|---|---|---|
| Trunk | No visible motion of the stalk / trunk bases relative to the crown | — |
| Tip sway amplitude | Detrended tip-minus-stalk motion: std 2.3–4.9 px, peak-to-peak 10–22 px on a ≈ 250 px crown → **≤ ±3 % of colony height** | Rock-vs-rock control gives the same scatter (std 5.4 px): handheld shake and zoom set the noise floor, so this is an upper bound |
| Tip sway period | Autocorrelation peaks of the tip motion at **1.25–2.2 s** (top-right 1.5 s, top 1.9 s, left 1.25/2.25 s) | Same noise caveat; a slower lean of the whole crown (≥ 6–8 s) cannot be separated from the camera drift in a 4.6 s steady window |
| Phase | Tips move later than mid-branch; neighbouring branches move together, separate trunks drift apart | Qualitative (6 fps contact sheet) |
| Fringe | Fine sprig / polyp flutter faster than the branch sway (≈ 1 s) | Qualitative; below tracking resolution |
| Fish passing | A wrasse (≈ 2–5 s) and the tang (≈ 9 s) brush the crown: brief local ripple of the branches they pass, the rest of the colony carries on | Qualitative |
| Polyps | Open all through the clip; no retraction to the passing fish | — |

### Changes made (2026-10-13, branch `claude/kenya-tree`)
- **Model** (`tools/blender/corals.py` `kenya()`, rebuilt `kenya.glb` 13.8k tris + 2075 sprigs × 6, `kenya_lod1.glb` 5.0k + 1100 sprigs; budgets: High 218.6k / 220k, Medium 139.0k / 140k): the form table above. Body, Tufts and Root carry `extras.swayMode = 1`. Before/after: `docs/preview/kenya_before_after.png` (clip frame / v2 / v3 / v3 LOD1).
- **Animation** (`common.glsl` `treeSway`, `static.vert`, `polyp.vert`, `TankRenderer`): for swayMode 1 only — slow whole-crown lean at 0.9 rad/s (period ≈ 7 s, 65 % of the amplitude) plus tip sway at 3.14 rad/s (**period 2.0 s**, 35 %) with a phase lag of 1.6 rad from trunk to tip and a spatial phase so nearby branches move together; weight = smoothstep(0.2, 1.0, distance from base / height)^1.5, so the stalk and trunk bases stay still. Tip amplitude `TREE_SWAY` = 0.035 × height × flow/12 (≈ ±0.6 cm, ≈ 2.3 % of height at the tank's usual flow 8; was 0.08 × height, ≈ 5 %). The polyp sprigs now ride their branch (same `treeSway` at the instance base, plus the body's extension shrink) and flutter on their own at 5.7 rad/s (≈ 1.1 s), `TREE_FLUTTER` 0.12 cm × flow/12. **Fish ripple**: the nearest organism head within 6 cm of the colony bounds pushes the branches near it outward (≈ 5.5 cm radius, ≤ 0.7 cm, 7 rad/s ripple), scaled by its speed (full at 10 cm/s), so a passing fish ripples the crown briefly and a clownfish resting in it barely does. The other corals keep the old sway.
- Kenya's `CORAL_TINT` entries removed (the textures now carry the clip colour).
