# Tee's tank at night (owner footage, lights off)

Top-tier evidence: Tee's own 60 gal tank, filmed 2026-10-13 with display lights off and a phone flashlight.
Clips are short (3-11 s, 1080p, 30 fps), so durations are lower bounds. Cite as `[T84 0:05]` = IMG_0084 at 5 s.
Every row here is **[H]** (seen in the footage) unless tagged otherwise.

## Sources
| # | Clip | Length | What it shows |
|---|---|---|---|
| T84 | IMG_0084.mov | 11.4 s | Pan along the front glass: clown pair sleeping low, hair algae streaming, Kenya tree, sand bed, red specks in water |
| T86 | IMG_0086.mov | 5.0 s | Top-down over the open top: surface ripple, return outlet, AI Prime fixture, green algae on rock tops |
| T87 | IMG_0087.mov | 3.2 s | Side glass close-up: magnetic cleaner, hair algae strands on glass, a dark fish blurred at the top |
| T89 | IMG_0089.mov | 8.4 s | Falco hawkfish resting on open sand at the base of the rockwork |

## Fish at night
| Species | Observation | Sim rule | Cite |
|---|---|---|---|
| Ocellaris pair | Both clowns hover **together, body-length apart**, 1-3 cm over their host, a cluster of **green hairy mushrooms (Rhodactis)** on the lower-left purple-coralline rock, beside a yellow encrusting patch and a hair-algae tuft (host ID confirmed by Tee). Still awake, slow fin sculling, no colour change. | Night state: both sleep over the mushroom cluster (see [coral_hairy_mushroom.md](coral_hairy_mushroom.md)), separation <= 1.5 BL, sway with flow, speed ~0.1 BL/s; no paling | [T84 0:00-0:03] |
| Ocellaris (dark morph) | Black body with three white bars, orange snout and pectorals; sits slightly below and beside the orange one | Model: dark morph needs orange face/pecs, not all-black | [T84 0:01] |
| Falco hawkfish | Rests on **open sand** (not a rock perch) at the rock base, propped on its pelvic fins and lower pectoral rays, head raised ~15-20 deg. Stays put under the torch for the whole clip; eyes and head make small turns; no fleeing. | Night state may perch on sand within 10 cm of rock, pitch +15-20 deg, motionless except eye/head tracking every 1-3 s; torch does not trigger flight | [T89 0:01-0:08] |
| Falco hawkfish (texture) | Night colours same as day: white body, orange-red spots scattered on rear body, dense brown-red mesh saddles over nape and mid-back (two saddles), red ring around the eye, cirri (white tufts) on each dorsal spine tip, clear fins | Texture: two dorsal saddles in brown-red reticulated pattern, spot field fading to belly, cirri as small billboards on spine tips | [T89 0:02-0:06] |
| Unknown dark fish | A dark fish moves near the surface on the side glass, too blurred to name | none | [T87 0:00] |

## Tank and environment at night
| Item | Observation | Sim / asset implication | Cite |
|---|---|---|---|
| Hair algae | Long dark brown-green strands (est. 5-10 cm) on upper rock ledges, streaming steadily in one direction and flicking; dark tufts also at rock bases | Algae model: long-filament tufts on high-flow upper rock, sway amplitude high (follow flow vector), dark brown-green under white light | [T84 0:04-0:11] |
| Flow | Surface strongly rippled from return and powerheads; particles drift fast | Night flow is unchanged (no night mode on pumps) | [T86 0:00-0:05] |
| Water-column specks | Tiny red reflective specks in the water near the surface and mid-water under the torch | Likely zooplankton eye-shine (pods/mysids rise at night) **[L]**; sim: night pods drift up into water column, render as faint red sparkles under a "torch" view | [T84 0:03, 0:09] |
| Kenya tree | Grey-purple branching colony at the base of the rock, polyps contracted | Kenya at night: polyps retracted, branches thinner | [T84 0:02-0:04] |
| Coralline | Dense pink/purple coralline on the lower-left rock | Rock material variant | [T84 0:00] |
| Glass | Green film on back glass and green hair on the side panel | Algae on glass growth is visible and patchy | [T84 0:00], [T87 0:00] |
| Equipment | Two dark powerheads (mid-left and front-right low), heater, return nozzle, AI Prime puck on top | Matches existing equipment layout | [T84], [T86] |

## Priority fixes for Opus
1. Clown pair hosts in the green hairy mushroom cluster (not the Kenya tree): sleep there at night, close together, slow scull.
2. Hawkfish night rest: allow sand perches near the rock base, head-up pitch, ignore the torch.
3. Hair algae tufts on upper ledges with large flow-driven sway.
4. Night pods: drift up into the water column with red eye-shine in close-up view.
