# 11. Look review: ReefLive vs Tee's 60 gal (art direction)

Scope: the web build in `web/site` (qa copy: `scratchpad/qa/site`), renders from headless Chromium with SwiftShader at
**picture LOW** (no shadow map, no sand normal map, single caustic read, rock "lumps only"). Photos:
`docs/reference/tank_full.jpg`, `tank_left.jpg`, `tank_right.jpg`, `blenny.jpg`. Downscaled copies of everything cited are
in `scratchpad/research/look_ds/`. Colour samples below are box averages of the downscaled images (sRGB hex, then hue /
saturation / lightness). Shader constants quoted from `assets/shaders/*.glsl|frag`, `render/LightingState.kt`,
`render/Material.kt` (FrameUniforms), `render/Camera.kt`, `tools/blender/corals.py` on `origin/main`.

Caveat on the photos: a phone camera white-balances hard toward neutral. The photos are the owner's *memory* of the tank,
so matching them is the right target, but the strong blue on the sand (#aebedd) and the violet rim at the water line
(#666c8c) survive the camera's WB and tell us the LED mix is blue-violet, not cyan.

## 0. Where the look is today, in one paragraph

The render (`qa/01_default.png`, `01_default_b.png`) is a single-hue picture: open water #264a5e, rock #3f5765 / #334b55,
arch #29485b, Kenya tree #5c6d7a, sand #829fa3, surface band #5490a9. Every one of those sits at hue 187–206°. The photo
(`tank_full.jpg`) is three distinct materials: warm tan rock (#564d45, #5b5447, #72674e, hue 25–42°), cool bright sand
(#bfcfd7, #aebedd, L 0.77–0.80) and a dark, nearly neutral water column (#433b3e, #4b4952, #4e464e, S 0.05) in front of a
*dark* back pane. The real tank is "dark box, bright sand bar, coloured accents"; the render is "cyan aquarium on a cream
wall". The corals are then painted at full saturation on top (lime #3c8134, violet #7b418d coralline, scarlet mushrooms),
which is the owner's "oversaturated colours" / "Kenya looks bleached" / "zoas look like speckle" / "fish nearly invisible"
list in one image.

Two mechanisms cause most of it and both are constants:

1. The light. Midday channels (LightSchedule DEFAULT_POINTS 10:30–18:00: white .6, blue 1, royal 1, red .4, green .4,
   violet .8, uv .6) through `LightingState.CHANNEL_RGB` give key = (0.253, 0.423, 0.738) linear, tint (0.34, 0.57, 1.0).
   That is a **cyan** light, because the blue diode is modelled as (0.12, 0.60, 1.0): 60 % green for a 460 nm LED. The
   tonemap then divides by `adapt = mix(1, tint, 0.6)` = (0.61, 0.74, 1.0), which neutralises most of it but leaves the
   residual green-cyan on everything, and the hemispherical ambient (`HEMI_TOP` #203852, `uAmbientB = key x 0.18`) fills
   every shadow with the same blue. Nothing warm survives.
2. The background. `uFog` at midday = (0.023, 0.128, 0.325) linear = **#2e6499**, a saturated blue, and the back glass
   transmits the cream room wall (#9a9891 at `water_back_right`). The photo's back is #453d3e / #686d77: dark.

## 1. Ranked changes (visual impact per GPU cost)

| # | Change | Impact | Cost | Where |
|---|--------|--------|------|-------|
| 1 | Dark back pane: back glass transmits #101418 (lin .0023,.0037,.0055), not the room | very high | free (constant / one dark quad) | glass.frag back pane, or tank.glb back wall material |
| 2 | Fix the LED hue: blue (0.12,0.60,1.0) -> (0.08,0.40,1.0), royal (0.14,0.34,1.0) -> (0.10,0.20,1.0); WB 0.6 -> 0.8 | very high | free | LightingState.CHANNEL_RGB, lighting.glsl tonemap |
| 3 | Neutral-warm ambient: HEMI_TOP #203852 -> #2C3A46 (lin .021,.0385,.058), HEMI_BOTTOM to lin (.004,.005,.007); ambient = key x 0.18 -> mix(key, grey(lum key), 0.5) x 0.18 | high | free | lighting.glsl, LightingState.from |
| 4 | Caustic net: CAUSTIC_STRENGTH 0.85 -> 0.45, CAUSTIC_SCALE 0.03 -> 0.055, `sum*8 + sum^2*24` -> `sum*8 + sum^2*10` | high | free | Material.kt FrameUniforms, lighting.glsl causticColor |
| 5 | Sand: albedo up to lin ~(0.69,0.68,0.61) (#D8D6CC), green-film blend 0.55 -> 0.25 | high | free | rock.frag SAND branch / sand material base colour |
| 6 | Fog: FOG_TINT #0E3B5A -> #1C2F3A, FOG_DENSITY 0.005 -> 0.0085, SCATTER 0.0004 -> 0.0010 | high | free | LightingState, Material.kt |
| 7 | Desaturate the paint: coralline PINK/PURPLE, GSP/mushroom emissive x0.4, red sand mushrooms, zoa yellow, tonemap saturation 1.06 -> 1.0 | high | free (constants) + texture regen | rock.frag, softcoral/polyp.frag, corals.py, details.py |
| 8 | Rock: warm it (albedo x (1.12,1.0,0.82) before lighting), olive film 0.34 -> 0.5 on up-faces, roughness floor 0.85, ridge brightening 0.2 -> 0.1 | high | free | rock.frag |
| 9 | Default camera: DEFAULT_DISTANCE 150 -> 118, DEFAULT_PITCH 0.08 -> 0.035, target y 19 -> 17.5; dim the room x0.55 | medium-high | free | Camera.kt, room.frag / room env |
| 10 | Kenya tree: body #C9B3A8 -> #A8838A, trunk #6E5A58, sprig cards x1.5 size at same count, polyp sheet tips #DCC4C4 | medium-high | texture regen; card overdraw unchanged | corals.py kenya() |
| 11 | Surface band from above: `uFog.rgb*0.15` -> 0.05, alpha floor 0.12 -> 0.06, env x0.4 -> x0.25 | medium | free | water.frag above-water branch |
| 12 | Fish: fill 0.10 -> 0.16, scale relief 0.006 -> 0.003, tang albedo keep but add +0.04 blue fill, dottyback clear coat 0.55 -> 0.3 | medium | free | fish.frag, fish_species.py |
| 13 | Glass: faint permanent film (uAlgaeGlass.x floor 0.08), dark silicone seams, powerhead roughness 0.4 | low-medium | free | glass.frag, equipment material |
| 14 | UI: stats HUD on a `.chip` backdrop; top pills keep navy border; rail unchanged | low | CSS | style.css |

Everything in the table is a constant, a material value or a texture regenerated by `tools/blender`. None adds a pass, a
texture read per pixel, or geometry. The two things to **not** do are listed in §12.

## 2. Water colour and clarity

**What's off.** `qa/01_default.png` open water (behind the arch) #264a5e, hue 201°, S 0.42; behind the right rocks the
room wall shows through at #9a9891. `hv/shots/after_HLLE_Dori_b.png` (in-water) water_top #5ba2be. Photo `tank_full.jpg`
water_open_mid #433b3e, `tank_left.jpg` #4b4952, `tank_right.jpg` #4e464e: dark, S 0.05, slightly mauve, and the back of
the tank is dark (#453d3e). `tank_left.jpg` also shows the real water is *not* crystal: a yellow-green haze (DOC "tea") and
suspended particles soften everything beyond ~30 cm (rock at the back #615856 vs the front tower #524338).

**Target.** Water column seen from the front: a smoky slate, sRGB #1C2F3A (lin 0.0078, 0.0242, 0.0385) at the back glass
with the dark pane behind it; the back glass itself #101418. Visible haze: at the back pane (45 cm of water) about 30 %
fog, i.e. density 0.0085 /cm (now 0.005 -> 20 %). Keep absorption as is but stop it pushing blue: ABSORB
(0.0072, 0.0016, 0.0010) -> (0.0062, 0.0018, 0.0020); SCATTER 0.0004 -> 0.0010.

**Cheapest implementation.**
- `LightingState.FOG_TINT` #0E3B5A -> #1C2F3A, `FOG_FLOOR` #02060C -> #04070A. Fog hue still follows `lightTint()`; with the
  §3 LED fix that lands on a blue-slate, not cyan.
- `FrameUniforms.FOG_DENSITY` 0.005 -> 0.0085, `SCATTER` 0.0010. Beer-Lambert is already per pixel; no new cost.
- Back pane: in `glass.frag` the back pane's transmitted term is the room env. Replace with a constant #101418 (or set the
  back pane material to opaque near-black in `tank.glb`); keep the Fresnel reflection. The photos' back looks like a dark
  gap / unlit wall, so this is also *more* faithful. One quad, zero cost. This single change gives the corals and the fish
  the dark field they need and is the highest-impact item in this review.

## 3. Light colour temperature and shimmer

**What's off.** Light: see §0 — a cyan key (0.34, 0.57, 1.0). The photos' blue is violet-leaning: sand #aac6ed / #aebedd
(hue 215–220°), surface rim #666c8c (231°), tank_full's top-of-frame glow is lilac. Shimmer: the render sand carries a
hard, high-contrast "cracked ice" net with 8–12 cm cells and near-white lines (`01_default.png`, `03_crop_equip.png`,
`mine/report/camera_after.png` bottom row where the sand is 60 % white lines). In every photo the caustics are barely
there: soft 1–3 cm mottling on the sand (`tank_left.jpg` sand_mid #bfcfd7 vs sand_shadow #aac6ed is the whole range), and
invisible on rock faces. The render also throws the net onto vertical rock faces and the Kenya tree at 42 % (`side` in
`causticColor`), which is why the rocks look wet-plastic.

**Target.** Key light tint ~ (0.31, 0.46, 1.0) linear (sRGB #9bb4ff-ish before WB), i.e. a 16–18 K AI Prime "soft coral"
look. After WB the sand should land cool-white #c8d4e0 and rock warm #8c7a5e. Caustics: peak line brightness no more than
+35 % over the local sand tone, cell size 3–5 cm, 20 % on side faces, none on fish bodies above 15 cm from the sand.

**Cheapest implementation.**
- `LightingState.CHANNEL_RGB`: blue (0.12, 0.60, 1.0) -> (0.08, 0.40, 1.0); royal (0.14, 0.34, 1.0) -> (0.10, 0.20, 1.0).
  Midday tint becomes (0.315, 0.458, 1.0). Also moon (0.0255, 0.0345, 0.0645) is fine.
- `tonemap()`: `wb = 0.6 * ...` -> 0.8. The eye adapts almost fully; keep the actinic exemption as is.
- `FrameUniforms.CAUSTIC_STRENGTH` 0.85 -> 0.45; `CAUSTIC_SCALE` 0.03 -> 0.055 (finer net). In `causticColor`:
  `sum * 8.0 + sum * sum * 24.0` -> `sum * 8.0 + sum * sum * 10.0`, and `side` 0.42 -> 0.22 on Medium/High. `gather`
  (0.6 x strength) scales down automatically.
- `surfaceFlicker` range 0.74..1.31 -> 0.85..1.15 (`0.74 + 0.26*` -> `0.85 + 0.15*`): the real beam barely pulses.
- Light shafts (`shafts.frag`) are fine in `01_default_b.png` (soft, blue), keep.

## 4. Rock colour and texture

**What's off.** Render rock is blue-grey clay: `01_default.png` rock_left #3f5765, rock_mid #334b55 (hue ~200, S 0.25),
`after_HLLE_Dori_b.png` rock #5b7f82 with glossy highlights and the caustic net wrapped round it. Up close
(`hv/shots/after_close_0.png`, `after_HLLE_Dori_b.png`) the rock is smooth lumps with large soft pits and flat violet
coralline facets; at LOW there is no grain at all. Photo rock: `tank_full.jpg` left tower #564d45, mid tower #5b5447,
right top #70726c; `tank_left.jpg` #524338 / #7c6d51; `tank_right.jpg` #72674e, sun-bleached top #5e5754. Hue 25–42°,
S 0.1–0.2, matte, with a fine olive-yellow turf (`tank_full` green_fuzz_left #6b723a, `tank_left` green_macro #8e8c45)
and cream skeleton showing on edges. The baked albedo (`ROCK_BLEACH` lin (0.50, 0.42, 0.28) = #baac8f) is the right
material; the lighting (§3) and the blue ambient turn it cyan, and the coralline pass (§5) paints over it.

**Target.** Lit up-face under the (fixed) light after WB: sRGB #8C7A5E (lin 0.267, 0.198, 0.111). Shade: #4A4440.
Olive film on up-faces: #6B7238 (lin 0.148, 0.170, 0.036) at 40–50 % mix. Roughness 0.85–0.95; coralline crust 0.6.

**Cheapest implementation.** All in `rock.frag`, ROCK branch:
- After `albedo = toLinear(tex) * uBaseColor`, `albedo *= vec3(1.12, 1.0, 0.82);` (one multiply).
- `ROCK_FILM` (0.17, 0.19, 0.07) -> (0.148, 0.170, 0.036) and `film * 0.34` -> `film * 0.5`.
- `ridge * 0.2` -> `ridge * 0.1` (the bright worn ridges are what reads as "wet plastic" at distance).
- `rough = mix(rough, 0.42, ...)` -> `mix(rough, 0.6, ...)`, and set the rock material roughness in `rockscape.py` to 0.9.
- `shadeLit(..., wrap 0.25)` is fine; the f0 0.04 is fine once roughness is up.
- The real fix for the "clay" silhouette is just quality: the renders are LOW (`fpL = 100` disables pores/vugs/grit). On a
  phone at Medium the relief octaves come back. Do not add octaves; `rock.frag` is already the heaviest shader (fbm3 x3,
  voids x3, rockLife).

## 5. Coralline and algae tones

**What's off.** `mine/report/algae_after_overview.png` and `algae_after_c1.png`: coralline is large vivid violet / magenta
blobs (`ALG_CORAL_PURPLE` lin (0.20, 0.05, 0.27) = #7b418d; `ALG_CORAL_PINK` (0.52, 0.11, 0.19) = #bd5d78) covering
30–40 % of every rock face, with hard-edged flat facets in close-ups (`hv/shots/after_HLLE_Dori_b.png`, `after_VELVET_Nemo_b.png`
left rock). The cyano preset (`mine/report/cyano_after_wide.png`) is a sheet of saturated crimson (#8a1030-ish) over half
the sand and rock. Photo coralline (`tank_left.jpg` coralline_pink #796b71, rock_top_algae #736769; `tank_full.jpg`
mid tower pinks) is dusty mauve, S 0.06, in patches of 2–6 cm mostly on *shaded* faces and the undersides, with brighter
pink-red only at the left rock's base. Green: photo turf / film is yellow-olive (#6b723a, #8e8c45), the render's
`ALG_HAIR` is a dark forest green (#3d5c31) and the baseline "green algae" on rock in `algae_after_overview.png` reads
grey-brown (#817d72), i.e. absent.

**Target.** Coralline pink #9A6472 (lin 0.330, 0.128, 0.170), purple #5E4462 (lin 0.111, 0.055, 0.122), cover density
capped at 0.7, patch scale 2x finer, 25 % of it on up-faces at most. Hair / turf #6B7238 (lin 0.148, 0.170, 0.036).
Cyano: keep `ALG_CYANO` (0.16, 0.012, 0.016) but cap the mat opacity at 0.75 and keep it to the low-flow sand corners.
Diatoms #a07a40 is fine. Dinos khaki is fine.

**Cheapest implementation.** `rock.frag` constants: `ALG_CORAL_PINK` -> (0.33, 0.128, 0.17), `ALG_CORAL_PURPLE` ->
(0.111, 0.055, 0.122), `ALG_HAIR` -> (0.148, 0.17, 0.036). `algaeDensity()` `mix(0.45, 0.92, ...)` -> `mix(0.4, 0.75, ...)`.
Coralline patch noise `rp = op * 0.09` -> `op * 0.18`. In the baseline block `cb * 0.3` -> `cb * 0.22`. The "pull the
blue out of purple" line already exists; with these constants it can stay. For the sandbox cyano preset, `slimeMats` sheet
blend cap 0.75. Zero cost.

## 6. Coral colours and shapes per species

Photos: `tank_right.jpg` is the reference for the Kenya tree, clove/zoa patch and GSP; `tank_full.jpg` for the lime
mushroom mound (lower right, it *is* bright lime, #6dbf3f-ish, so that one is close) and the red macro.

| Species | Render (file, colour) | Photo | Target (sRGB / linear) | Fix |
|---|---|---|---|---|
| Kenya tree (Capnella) | `01_default.png` #5c6d7a; `after_HLLE_Dori_b.png` #6f7c89: pale grey-lavender *sticks* with sparse feather sprigs. Reads bleached / dead gorgonian | `tank_right.jpg` kenya_tree #8e7177, base #564643: a dense, fluffy, rounded pink-mauve cauliflower 26x20 cm on a thick brown trunk | body #A8838A (lin .399,.231,.259), trunk #6E5A58 (lin .157,.101,.096), polyp sheet #B48A90 with tips #DCC4C4 (lin .723,.561,.561) | `corals.py kenya()`: `cv.vgrad("#C9B3A8","#A27D70")` -> `("#B08A90","#8E6A70")`, trunk darker; `feather_canvas` base #A27D70 -> #B48A90, pinnules 8 -> 10, `tip_white` #EFE4DE -> #DCC4C4; scale the sprig card size x1.5 **at the same count** (fuller crown, same overdraw). The `swayMode=1` motion is good, keep. |
| Zoanthids | `01_default.png`, `02_watch_default.png`: yellow confetti dots #f0c030 on the front sand; `camera_after.png` hermit frames: a ring of yellow discs on a purple mat ("speckle") | `tank_right.jpg` zoa_patch #9d8175: a tight orange-brown mat 6 cm across with green-centred discs; a lime-green zoa cluster beside it | polyp rim #C8702E (lin .586,.164,.023), centre #4F8F3A (lin .076,.280,.039), mat #5A4034 (lin .101,.048,.030) | `corals.py zoa()`: `mA` rim "#E0792A" ok, drop the "#F09A4A" rays to "#C8702E"; `mB` yellow "#D9C238" -> "#8A9A3A"; disc radius x1.4 and pack edge-to-edge; mat between polyps `#7A5A3A` -> `#5A4034`. Also `polyp.frag` tip highlight `vec3(0.95,0.12,0.5)*vTip*0.85` is a magenta tip on every polyp: 0.85 -> 0.35. |
| GSP (Briareum) / mushroom mound | `01_default.png` gsp_right #3c8134 S 0.43, plus `gsp_mat` tips `#7DFF4F` and emissive `#8CE050`/`#A8F060`: fluorescent lime | `tank_right.jpg` gsp_green #8e957b, `tank_full.jpg` gsp_right #929971: muted olive-green, S 0.11–0.16; the big lower-right mound in `tank_full.jpg` is lime but matte | polyps #8E9A6E (lin .276,.330,.157), base #4E6A3C (lin .074,.145,.042); fluorescence a third of today | `corals.py`: gsp polyp_hex "#59D64A" -> "#7E9A5E", tip "#7DFF4F" -> "#A8C070"; mushroom_mound emissive "#8CE050" -> "#4E7A30", polyp emissive "#A8F060" -> "#6A9A40". `softcoral.frag` fluorescence `* 1.5` -> `* 0.8`. |
| Mushrooms (Discosoma, green) | `camera_after.png` cam2/goby frames: flat green discs with a saturated violet eye-centre (cartoon eyes) | `tank_full.jpg` right: green discs, centres only slightly darker | disc "#4FAE58" -> "#5E9A5A", centre "#2A7266" -> "#3E6A58", warts "#C8B48A" ok | `corals.py mushroom()` shroom_tex args; `fluorescent=0.5` -> 0.3. |
| Red mushrooms on sand (details.py) | `01_default.png`: three scarlet blobs #c02020-ish on the front sand, the most saturated thing in frame | `tank_full.jpg` bottom-left: one small orange-rust clump (#c06030) | #8A3A2A (lin .259,.039,.019), emissive #6A2A1C | `details.py mushrooms()` emissive "#C04A2A" -> "#6A2A1C", base tone -20 % saturation. |
| Red macro (Gracilaria) | `01_default.png` magenta branches on the arch top (#9a2a5a) | `tank_left.jpg` top-right pink-red tufts (#c06070) | #A05060 | base hex -25 % saturation in `corals.py macro_red`. |
| Clove polyps / organ pipe | small, fine | fine | — | — |

General: `tonemap()` ends with `mix(vec3(l), c, 1.06 + 0.5*actinic)`; set the daytime term to 1.0. Saturation should
come from the materials, not a global boost, now that fish readability has its own rim/fill.

## 7. Fish readability and colour accuracy

**What's off.**
- Distance: `qa/01_default.png` (first frame) shows no fish; `03_default_sheet.png` shows them at ~20 px. At
  DEFAULT_DISTANCE 150 cm and fov 50° a 7 cm clown is 1.9 % of frame height. The owner's "fish nearly invisible" is a
  framing problem first (§10) and a contrast problem second: orange on cyan water in front of a cream wall has no dark
  field behind it; §2's dark pane fixes most of this.
- Clownfish: close (`hv/shots/after_close_0.png`) #e8601a is right (`fish_species.py` #F6893B/#E8601A); the heavy black
  bar edging is right for ocellaris; but `after_close_3.png` / `after_BACTERIAL_Marlin_b.png` show the scale-relief
  moire (hatching) on the tail, and the whole body has a wet clear-coat glare. Photo `tank_right.jpg` clown_orange
  #a46958 at 40 cm: orange survives, white bars read pink-white (#b5818a) under the blue light.
- Blue tang: render `after_HLLE_Dori_b.png` #2a4372 (navy, L 0.31) vs photo `tank_left.jpg` #3c5cbb (L 0.48). The
  texture (#3A6EE6) is right; the blue key light plus the fog make the body darker than the water around it.
- Dottyback (`camera_after.png` dottyback_1..3): glossy blue-white plastic, clear coat too strong; the fish is a matte
  indigo in life.
- Wrasse / goby: fine; the diamond goby reads well.
- Blenny (`blenny.jpg`): brown-olive with a dark lateral stripe and blue-rimmed eye; the render blenny was not in any
  shot. Check its `uTone` mottling isn't defaulting to the pale state.

**Target.** Fish are the brightest, most saturated things in frame after the lime mound. Tang mid-body #3C5CBB
(lin .042,.106,.505) as seen through 30 cm of water; clown orange keeps #E8601A up close and #B86A40 at the back.

**Cheapest implementation.** `fish.frag`: `col += albedo * 0.10 * (0.15 + lightIntensity())` -> 0.16; scale relief
`bumpH(N, vWorld, scale * vis * 0.006)` -> 0.003 and fade `vis` at `smoothstep(0.12, 0.35, ...)`; `wetCoat(Ng, V, vWorld,
0.14, 0.55 * specScale)` -> 0.35. Per-species: dottyback material roughness 0.5 -> 0.7 and `uIridStrength` x0.5. Tang: add
`+ vec3(0.0, 0.01, 0.04)` to the fill for blue-dominant albedo (one `mix` on `albedo.b - max(r,g)`), or just regenerate
the tang texture at #4A7CF0. Fish rim (`rim * (0.40 * lightSum ...)`) is good; keep.

## 8. Sand

**What's off.** `01_default.png` sand_front #829fa3 (L 0.57, hue 187°, S 0.15) with the white caustic net on top;
`after_close_3.png` sand #536362 in shade. Photos: `tank_left.jpg` sand_mid #bfcfd7, shadow #aac6ed; `tank_right.jpg`
#aebedd; `tank_full.jpg` front mid #8f96a4 (the darkest it gets, at the front edge). The real bed is 0.2 L brighter,
cooler (blue-white, the one place the blue light should show), with soft mounds against rock, a dark damp band under the
rocks and a brown detritus strip against the front glass (`tank_left.jpg` bottom). The render bed is flat, teal and
covered by the net; its green film (`vColor.r * 0.55`) adds the teal.

**Target.** Lit bed after WB: #C8D4E0 (lin .58,.66,.76 as *seen*), from an albedo of ~#D8D6CC (lin .694,.680,.612) under
the blue-violet key. Shade under rock #7A8898. Caustic lines at most +35 %.

**Cheapest implementation.** Sand material `uBaseColor` x1.25 (or regenerate the sand albedo at #D8D6CC in
`tools/blender/textures.py`); `rock.frag` SAND: `mix(albedo, vec3(0.27,0.35,0.12), vColor.r * 0.55 ...)` -> `0.25`; `bank`
darkening `0.22` -> `0.30` so the mounds read; keep the grain / ripple / glint code (Medium+ only, cheap). §3 caustic
constants do the rest. Flag: the sand already reads `uSandMap` 5 times when `uSandOn`; do not add reads.

## 9. Equipment and glass

**What's off.** Equipment placement matches the real tank well (`03_crop_equip.png`, `tank_right.jpg`: the black
powerhead on the sand front-centre-right is real, the side powerheads are where the photos have them). Issues:
- The side powerheads and the sand powerhead are flat black with no specular (`01_default.png`), so they read as holes;
  in `tank_right.jpg` powerhead_black #201014 has a plastic sheen and a grey suction-cup bracket.
- The "FLIPPER FLOAT" label (pixel font, black, ~1/3 of the tank height) is the most legible text in the picture and sits
  outside the glass. Real Flipper is a small grey/black magnet (~6 cm) *inside* the pane at the top-left in `tank_full.jpg`.
- Front glass: a flat cyan plane; no reflection, no edge. `tank_full.jpg` shows a dark rim, dark silicone seams at the
  corners, salt creep / film along the top 2 cm, and a faint green film overall (`tank_left.jpg` glass_back_dark #686d77).
- From below the surface (`mine/report/surface_from_below_before_top_after_bottom.png`, `after_HLLE_Dori_b.png` top) the
  surface is a bright cyan sheet; from the front at pitch 0.08 it is a bright band 1/8 of the tank high (`01_default.png`
  surface_band #5490a9). In the photos at eye level the surface is a thin dark line with a lilac rim (#312c2d / #666c8c).

**Target.** Powerheads: black plastic, roughness 0.4, f0 0.04, faint caustic flicker on top. Glass: 2 % neutral-green
film (#0d1a10 at 8 % mix), dark seams 1.5 cm at the corners, surface band from the front no brighter than the water
below it.

**Cheapest implementation.** `equipment.glb` powerhead material roughness 0.4 (it goes through `generic.frag`, which has
the GGX lobe). Flipper: scale the label mesh to 6 cm and move it inside the pane (model edit in `equipment.py`), or hide
the label text. `glass.frag`: `film = uAlgaeGlass.x * keep` -> `max(uAlgaeGlass.x, 0.08) * keep`; add `col *= 1.0 -
0.6 * smoothstep(1.5, 0.0, min(edgeX, edgeY))` for seams. `water.frag` above-water branch: `uFog.rgb * 0.15` -> 0.05,
`alpha = clamp(F + 0.12 ...)` -> `F + 0.06`, `env ... * 0.4` -> 0.25 (and a darker room env, §10). Below-water branch
`refl = uFog.rgb * 1.6` -> 1.1.

## 10. Overall composition and default camera framing

**What's off.** `01_default.png`: camera at eye level (pitch 0.08 rad), 150 cm back, fov 50°; the tank is 84 % of the
frame width, with a bright cream room (#bcb6ab, L 0.70), two picture frames, a cabinet and the black rim band taking
~45 % of the pixels. Inside the tank the eye goes to the cyan surface strip and the white caustic net, not to the reef.
`tank_full.jpg`: the room is dim (#5a4a50-ish, the camera exposed for the tank), the tank fills the width, the sand bar
is the brightest thing, and the reef silhouette (left tower, arch, right rocks) reads as a dark mass with coloured
accents. `mine/report/camera_before.png` vs `camera_after.png` show the follow-camera work; the default view is the
problem.

**Target.** Default: tank fills ~92 % width on a 16:9 phone (landscape) and the sand line sits at ~72 % of frame height;
surface seen edge-on; room 45 % as bright as now; the reef's value range: sand L 0.75, water L 0.2, rock L 0.4.

**Cheapest implementation.** `Camera.kt`: `DEFAULT_DISTANCE` 150 -> 118 (keep `fitDistance` logic; `MIN_FIT_DISTANCE`
80 is fine), `DEFAULT_PITCH` 0.08 -> 0.035, `DEFAULT_TARGET[1]` 19 -> 17.5. Room: `room.frag` / room env albedo x0.55
and the `uEnv` (`room_env.png`) exposure in `water.frag`/`glass.frag` x0.6 (they read the same texture; or just darken the
PNG). No cost. Bloom (`PostFx` 0.8, threshold 0.8–1.0) can stay; after §3 fewer sand pixels cross the threshold, so
bloom becomes what it should be: the lamps' glow on the surface and the fluorescent corals.

## 11. UI overlay legibility on the scene

Checked against `qa/01_ui.png` (what's-new sheet over the scene) and `03_default_sheet.png` / `camera_after.png`.
- `?stats` HUD: 11 px white monospace, no backdrop, over the cream wall and the black rim: unreadable on the wall. Wrap
  it in the existing `.chip` style (`rgba(6,19,39,.62)` + 1 px glass border); CSS only.
- Top row (`.top`: happy face + clock pill #E9F3FF on navy border, "..." pill): fine on both the current cream room and
  the darker scene proposed here; the navy border (`--navy #1B1340`, 2.5 px) does the work. Keep.
- Bottom rail (`.rail`, `rgba(6,19,39,.55)` glass, white 12 px labels with text-shadow): sits over the cabinet (white /
  black); fine. After §10 (darker room) it still has the 3 px navy border; fine.
- Tap bubbles (`.tbubble` #F2F6FF / #1B1340): fine.
- The what's-new / onboarding panel (`--panel rgba(12,88,102,.92)` teal): on the current cyan scene the panel and the
  scene are the same hue and the panel edge vanishes (`01_ui.png`). With a slate water + dark pane it separates. If the
  look stays cyan, move the panel to `rgba(16,34,60,.92)`.
- Follow-mode lens (`composite.frag` vignette + chromatic aberration) darkens the corners where the rail and the top pills
  sit; `uLens.y` vignette strength should stay <= 0.35 so the white labels keep contrast.
- Nothing in the UI uses the scene's colours for state (good/caution/danger are `--good/--caution/--danger` on a navy
  chip), so the §6 desaturation does not collide with the status colours.

## 12. Performance notes (what would hurt, and what is free)

Free (constants / materials / CSS): everything in §1 except Kenya texture regen (offline) and the Flipper model edit
(offline). The fog density increase is the same exp() already evaluated. The dark back pane removes a `uEnv` read on that
pane if done as an opaque material.

Would hurt, so not proposed:
- More Kenya sprig cards or more zoa discs: alpha-tested card overdraw is the one thing phones pay for; scale cards, do not
  add them. `KENYA_SPRIGS_LOD1` 650 stays.
- Any extra octave or texture read in `rock.frag` (already fbm3 x3 + voids x3 + rockLife at Medium). Rock warmth is a
  multiply.
- Screen-space refraction / real reflection on the front glass, SSAO, a blur-based "haze": fog + scatter does the haze.
- Higher caustic strength with dispersion on High: it is the opposite of what the photos show.
- Coralline / algae as geometry (bubbles, tufts): keep them in albedo; `algae_tufts.glb` already exists for the sandbox.
- Raising `MAX_CASTERS` or shadow-map taps for more fish shadows: not needed for the look; the dark pane does more.

Verification: the renders here are LOW picture on SwiftShader at 960x540. Re-shoot `01_default` at Medium (shadow map on,
sand normal map on, two caustic reads) after the constant changes before judging rock relief or sand grain, and sample the
same boxes (`look_ds` crops: water_open_mid, sand_front, rock_left, kenya, gsp_right, surface_band) against the targets in
§2–§8. The pass condition is simple: rock hue 25–45°, sand L >= 0.72, water S <= 0.25, nothing in frame above S 0.5
except fish and the lime mound.
