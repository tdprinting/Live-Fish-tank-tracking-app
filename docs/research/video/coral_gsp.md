# Green star polyps (Briareum)

Sim hook: `CoralKind.GSP` · Adult size: mat 5–20 cm across [L] · Tank count: 1–3 colonies [L]

## Sources
| # | Video / page | URL | What it shows |
|---|---|---|---|
| V1 | Fragbox care guide | https://fragbox.ca/?p=185895 | Polyps: 8 feathery tentacles, retract completely when disturbed; tolerates low to high light |
| V2 | ReefCentral forum thread | https://archive.reefcentral.com/forums/archive/index.php/t-210317.html | Polyps stayed partly closed for weeks; opened after moving to more light and current (anecdote) |
| V3 | Quality Marine listing | https://www.qualitymarine.com/quality-marine/coral/polyps/briareum-pachyclavularia/starburst-green-55533/ | Moderate–high light; moderate–strong flow |
| V4 | Chart of anthozoan timing (search summary) | https://cob.silverchair.com/jeb/article/206/22/4041/14111/Photobehavior-of-stony-corals-responses-to-light | Stony-coral photobehaviour: expansion timing depends on light and feeding; for analogy only |
| V5 | YouTube: Green Star Polyps morning timelapse, Fluval Evo 13.5 | https://www.youtube.com/watch?v=INtG1P0l2Ys | Snippet: polyps open in the morning, like blooms [M] |
| V6 | YouTube: Green Star Polyp colony at 1 sec intervals | https://www.youtube.com/watch?v=neuWLCX73ew | Snippet: 1 s interval timelapse of colony; sand-cloudy water [M] |
| V7 | YouTube: Green Star Polyps blooming in time-lapse | https://www.youtube.com/watch?v=1ODIu7EB_Lc | Title only, not viewed [M] |

Video search (youtube.com only) found several hobbyist timelapses (V5, V6). No footage was viewed; claims are from snippets. No study of Briareum extension timing was found.

## Animation spec (for the renderer)
| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Polyp/tissue motion | Polyps extend as small stars; full retraction when disturbed | Each polyp pops open/shut as one unit | V1 [M] |
| Sway in flow | Moderate–strong flow preferred; no sway data | Tentacles ripple with flow, ~0.5–1 Hz at moderate flow | [L] |
| Open/close vs lights | Opens in good light/current; V2 anecdote of weeks to open | Open over ~20–40 min after lights on, close over ~20–40 min after lights off | [L] |
| Retraction on touch | Full retraction when disturbed | Snap closed in ~1 s | V1 [M] |
| Recovery after touch | No source | Re-open over ~5–10 min | [L] |
| Feeding response | No source | Tentacles curl slightly toward food; mouth not visible | [L] |
| Colour under blue light | No source | Green tone only, no strong fluorescence modelled | [L] |
| Growth / spread | Forms a mat by encrusting and budding (general) | Slow edge spread over weeks | [L] |
| Unhappy look | V2: stays half-closed | Partly closed polyps, dull colour | V2 [M] |

## Behaviour spec (for the AI)
| Aspect | Observation | Suggested rule / numbers | Cite |
|---|---|---|---|
| Zone & territory | Encrusting mat on rock | Stays on rock surface, low to mid | [L] |
| Day / night routine | No source | Mostly open in light, partly closed at night | [L] |
| Feeding | No source | Passive; slight tentacle motion | [L] |
| Social / interactions | No source | Spreads and can overgrow nearby colonies slowly | [L] |
| Reaction to parameters | Tolerates wide light range; likes flow | Low flow gives half-closed polyps (V2) | V1, V2 [M] |
| Distinctive tells | Mat of small green star-shaped polyps, 8 feathery tentacles | Polyps open as stars in unison | V1 [M] |

## Priority fixes for Opus
1. Animate the mat in polyp units that open and close together, not as a static green texture.
2. Tie open/close to lights with a 20–40 min lag and show partial closure when flow is low.
3. Snap polyps shut on disturbance (about 1 s), then re-open slowly.
