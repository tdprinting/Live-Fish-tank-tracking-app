# ReefLive — Tank Design Spec (buildable, v2)

Source of truth for `tools/blender/tank.py`, `rockscape.py`, `equipment.py`, `corals.py`. Derived from
`reference/tank_full.jpg` (primary), `tank_left.jpg`, `tank_right.jpg`, frames `a_0*.jpg`/`b_0*.jpg`.
Supersedes ASSETS.md §3.1/§3.4/§5 where they disagree.

## 0. Frame, units, scaling

- Units **cm**. Origin = inside bottom-front-left corner of the glass box. **+X right, +Y up, +Z toward the viewer**
  (Blender Z-up scene: Blender Z = world Y, Blender -Y = world Z; export `export_yup=True`).
- Interior (**owner-measured 2026-10-13: 48 in long × 18 in front-to-back × 16 in tall**): **L 121.9 × D 45.7 × H 40.6**
  (60 gal display volume for the chemistry, 6.35 mm glass). Interior volume: x 0–121.9, y 0–40.6, z 0–45.7.
  Back glass inner face z = 0, front glass inner face z = 45.7. One source of truth: `core/.../sim/TankDims.kt`, mirrored
  by `tools/blender/common.py` (`TANK`, `WATER_Y`, `SAND_Y`, `SAND_Y_BACK`, `RIM_Y`); every script derives from those.
- **Waterline y = 38.1** (2.5 cm below the glass top; rim top y = 41.0). Sand surface y = 3.5 front → 6.0 back (see §1.6).
- **v3 resize note**: the tables in §1–§3 were written for the earlier assumed 48 × 13 × 24 in box (D 33, H 61, water 56).
  Glass, rim, water and stand are now built parametrically from `TANK`. The rock was re-laid to the photo
  (`reference/tank_full_2026-10-13.jpg`): low wide scape rising to the left (left back stack breaks the surface at x 28–34),
  a central arch/cave under the back shelf at x 47–67, the Kenya tree on the centre mound (x ≈ 67), a right shelf over a dark
  recess (x 78–100), the clowns' hairy-mushroom carpet on a low purple-coralline rock (R10) in front of that recess, under
  the shelf's front edge (`HOST_SHROOM` ≈ (81, 13, 21)), and the big lime GSP mat on the low R7 mound front right
  (`coral_gsp_lower_right` ≈ (107, 14, 32)). Host evidence: [P p14] (clowns in front of a dark folded mushroom mass under a
  pink-coralline shelf, the finger leather up and to its left), [P full] (clowns and tang right of the Kenya at x ≈ 79–86, in
  front of the dark recess; the front-right lime mound is GSP), the [P p14] note in `corals.py`, and night clip T84 ("lower-left
  purple-coralline rock" = lower left of that clip's frame, panning along the front glass, beside a yellow encrusting patch).
  Tailspot hole `HOLE_BLENNY_2` in the front of the R4 shelf's right end (62.5, 29.5, 13.3), lawnmower hole `HOLE_BLENNY` in the
  front of the R2 pillar (10, 13, 32.3): ≥ 50 cm apart (06 §5.6 #9), and the tailspot outside the clowns' 22 cm and the
  dottyback's 25 cm defence radii.
  As-built positions live in `rockscape.py` (`ROCKS`, `CARVES`, `ANCHORS`) and `equipment.py`; equipment: HOB filter back right
  (x 98.5–118.4, intake x 114.5) with its return piped to a white ring nozzle at the back middle (outflow (73, 37.4, 2.3), aimed (0, −0.4, 0.92), `HOB_OUTFLOW`; video T97: Tee hand-feeds right beside it, `Simulation.HAND_FEED` (74, 37.1, 4.5); the photo shows the pipes behind the rim at x ≈ 73–77), powerhead mid-back (77, 32), left-glass powerhead (y 28, z 18), heater on the right
  (x 119.5, y 8–34), two AI Prime pucks at x 32 / 88, lens y 69.6 (≈ 29 cm above the rim), z 20 (`LightingState.LIGHT_POS`).
- Every object below is a **closed, manifold mesh** (boxes, swept profiles, capped tubes). No single-quad walls anywhere except
  the `WaterSurface` quad, which is explicitly two-sided. Verify with `bmesh` non-manifold edge count = 0 per object.
- Rendered-view coordinates in this doc were read off `tank_full.jpg` with: x_cm = (px − 40)/15.9, y_cm = 3 + (1095 − py)·0.105 (2000 px wide display).

## 1. Tank shell (`tank.py` → `tank.glb`)

Object names in **bold** become glTF node names; `part` extras in brackets.

### 1.1 Glass (5 panes, each a solid box, material `glass`)
| Node | Box (x, y, z ranges, cm) |
|---|---|
| **GlassFront** [glass] | x −0.635…122.535, y −0.635…61.0, z 33.0…33.635 |
| **GlassBack** [backglass] | x −0.635…122.535, y −0.635…61.0, z −0.635…0 |
| **GlassLeft** [glass] | x −0.635…0, y −0.635…61.0, z 0…33.0 |
| **GlassRight** [glass] | x 121.9…122.535, y −0.635…61.0, z 0…33.0 |
| **GlassBottom** [glass] | x 0…121.9, y −0.635…0, z 0…33.0 |
Material: IOR 1.52, transmission 1.0, roughness 0.02, faint green edge tint `#DDEFE4` (thickness-driven). Back glass gets the same material; the
black background sits behind it.

### 1.2 Silicone seams (**Seams**, one joined mesh, material `silicone`)
Inside bead at all 4 vertical inner corners and the 4 bottom inner edges: a quarter-round fillet, radius 2.5 mm, extruded along the edge (8 segments),
plus a 1 mm flat film between pane edges (just make the fillet overlap the glass by 0.3 mm). Color `#C9D0CC`, alpha 0.6, roughness 0.3 — reads as the
faint grey-white lines visible at the front corners in the photo.

### 1.3 Black background (**Backing**)
Box x −1…123, y −1…62, z −1.0…−0.7 (just behind the back pane). Material `#070808`, roughness 0.9, no spec. This is what shows through every cave/arch (the photo's cave interiors are pure black).

### 1.4 Top frame (**RimTop**, one closed swept profile; material `plastic_black` `#141518`, roughness 0.45, spec 0.5)
Profile swept around the rectangle of the glass outer edge (x −0.635…122.535, z −0.635…33.635), dimensions in **mm**:
- Outer skirt: 3 mm thick, 38 mm tall, outer face sits 1 mm outside the glass, bottom at glass-top − 34 mm (y = 57.6 cm), top at y = 61.4 cm.
- Inner lip (horizontal ledge on top): from the outer skirt inward **28 mm** on the long sides, **28 mm** on the ends, 5 mm thick, top face at y = 61.4 cm (flat, slightly
  glossy; the photo shows a wide flat black ledge). Underside lip: 10 mm inward, 3 mm thick, at the skirt's bottom (holds the glass).
- Rounded outer top edge r 3 mm. Inner lip edge r 1 mm.
- **Center brace** (**RimBrace**): bar x 58.9…64.9 (60 mm wide), y 60.4…61.4, z −0.3…33.3, joined to the lip at both ends. A 60-mm-wide bar is standard on a 48 in tank; it sits over the middle of the shelf rock.
- Corner caps: the four corners get a 12 × 12 mm raised square (1 mm up) — cheap detail that matches the molded corners.
The skirt must have an inside face (double-walled box), so it is a closed extrusion of the L-shaped profile, not a single-sided strip.

### 1.5 Bottom trim (**RimBottom**, same material)
Profile swept around the glass outer edge: outer skirt 3 mm thick, **42 mm** tall, from y = −3.6 to y = +0.6 cm (covers the bottom pane edge and 6 mm of the
side panes); inner lip 12 mm at the bottom (the trim's floor), 3 mm thick; top outer edge r 2 mm. Closed profile. The trim's bottom face sits on the stand.

### 1.6 Sand (**Sand**, closed solid: heightmap top + flat bottom at y = −0.5 + skirt walls at the glass)
Grid 1 cm (122 × 34 quads), decimate to ≤ 4k tris, solidified downward to y = −0.5 so it is closed from below.
Height h(x, z) =
1. Base slope: **3.5 cm at the front glass (z 33) → 6.0 cm at the back glass (z 0)**, linear.
2. Mounds against rock feet: +2.5 cm · smoothstep(10 → 0 cm, distance to the nearest rock-footprint edge); i.e. sand banks up on every pillar/mound.
3. Lanes (bare, slightly scooped): −0.8 cm in the ellipse centered (36, –, 24) radii (9, 7) and the ellipse (58, –, 26) radii (8, 6).
4. Burrow spoil: +1.5 cm fan in front of the goby burrow, center (64, –, 28), r 6, elongated toward +z.
5. fBm 0.35 cm (scale 4 cm) + 0.08 cm grain (scale 0.6 cm).
Texture `sand_albedo` 1024² tiled ×4: off-white `#EDE7D9` / `#D5CBB4` grain, 2 % dark specks `#9A8E78`, scattered coral rubble pebbles (`#F2EEE4`, 3–8 mm, 0.6 % coverage, denser within 12 cm of rock feet).
**Front algae line**: the bottom 1.2 cm of the front glass inner face and the first 2 cm of sand along it carry a green film `#6F8A3C` at 55 % mix (photo: green band along the whole front bottom edge, strongest x 30–70 and x 0–15). Roughness 0.92.

### 1.7 Water (**WaterVolume** + **WaterSurface**)
- **WaterVolume**: closed box x 0…121.9, y 0…56.0, z 0…33.0 (material: transmission 1, IOR 1.333, volume absorption `#2E8A78` density 0.045 /cm → ~15 % greenish tint across the 33 cm depth; roughness 0).
- **WaterSurface** [water]: quad at y = 56.0, two-sided, 61 × 17 subdivisions, displaced by two scrolling normal maps (`water_n1/n2`, amplitude 0.25 cm). Rim shadow: the lip's underside should visibly darken the first 2.8 cm from each wall at the surface (it does in the photo).
- Meniscus: a 2 mm fillet strip where the surface meets the glass (`#D7E9E8`, alpha 0.5) — optional.

### 1.8 Stand, wall (lower priority, but must be closed)
- **StandTop**: dark-walnut slab `#3B2518`, roughness 0.35: x −4…126, y −7.6…−3.6, z −4…37 (overhangs 4 cm each side; the trim sits on it).
- **StandBody**: white painted box `#F1EDE6` x −2…124, y −79…−7.6, z −2…35. Front face composition (left→right): a door x −2…30 with an **X-lattice** frame
  (3 cm wide bars corner-to-corner over a wire-mesh panel `#CFCFCF`, 1 cm grid, alpha-tested card inside a 2 cm inset), a center open bay x 30…78 with a mid shelf at
  y −45 (inside walls `#E8E4DD`), a right open bay x 78…124 with a shelf at y −45. Bay contents (optional props): two clear plastic bins 35 × 25 × 20 cm (`#E9ECEE` alpha 0.5) on the center floor and right shelf. Feet: 4 × 4 × 2 cm blocks. Floor: plane y −81, `#8A6A4D` wood.
- **Wall**: box x −120…240, y −81…160, z −8…−6, color `#E8DFD0`, roughness 0.9, subtle stucco bump (noise 0.5 cm). Under the AI Primes it will wash blue-violet; no need to paint that in.
  Light switch plate at (x 18, y 90, z −6): white box 12 × 20 × 0.6 cm with two 1 × 2.5 cm toggles. A side wall on the left at x −14 (box x −16…−14, y −81…160, z −8…60, `#EADFCF`) matches the photo's left corner.

## 2. Rockscape (`rockscape.py` → `rockscape.glb`, node **Rock**, single mesh ≤ 45k tris)

Tank length in tenths: T1 = x 0–12.2, T2 = 12.2–24.4, T3 = 24.4–36.6, T4 = 36.6–48.8, T5 = 48.8–61.0, T6 = 61.0–73.1, T7 = 73.1–85.3, T8 = 85.3–97.5, T9 = 97.5–109.7, T10 = 109.7–121.9.
Build each element from the metaball list (resolution 0.8, threshold 0.6, negative balls carve), then remesh 1.0 cm, displace (Voronoi F1 scale 5 cm / 1.2 cm; fBm 1.5 cm / 0.4 cm; crackle 2 cm / −0.3 cm), boolean 180 pores r 0.5–1.4 cm, decimate. **Rock feet must penetrate the sand by 1.5 cm** (no floating rock, no visible bottom faces). Every footprint listed is the base ellipse at y = 3.

Silhouette rule from the photo (front view): the scape is an **inverted-U on the left** (pillar + back stack + shelf hanging over a black cave), an **open sand lane** in the middle-left, a **free-standing stub pillar** center, the **Kenya mound** right of center, a **tall arch** on the right whose roof slab runs to the right wall just under the surface, and a **low GSP mound** in the front-right corner. Rock touches the surface only at x 22–42 and x 90–101.

| ID | Element | Tenths | Footprint / body (cm) | Top y | Depth z | Shape and notes |
|---|---|---|---|---|---|---|
| **R0** | Left foot rock | T1 | ellipse c (10, –, 20) r (4, 5); body to y 11 | 11 | 15–25 | Small knobby rock behind the pump cable, pressed against the pillar's left foot. Olive. |
| **R2** | **Left pillar** | T2 | base c (18, –, 20) r (6, 6); shaft tapers to r 4 at y 26; crown knob ellipsoid c (15, 31, 19) r (5, 4, 4) | **34** | 13–27 | Free-standing, vertical, cauliflower surface, leans 4° left. Crown overhangs left. **Blenny hole** r 1.3, 6 cm deep, on front-right face at (22, 18, 25) pointing (0.6, 0.1, 0.8). Base widens with sand mound. 3 cm gap to R1 behind (cardinal hover). |
| **R1a** | Left back stack, lower boulder | T2–T3 | ellipsoid c (22, 15, 8) r (11, 13, 8) | 28 | 0–16 | Leans on the back glass. Front face at z 14–16. |
| **R1b** | Left back stack, upper mass | T2–T4 | ellipsoids c (26, 42, 8) r (12, 11, 8) and c (36, 46, 7) r (10, 9, 7) | **55** (touches the surface x 22–40; a 1 cm cap stays above the surface at x 28–34 — the photo shows rock breaking the surface there) | 0–16 | Big overhang: the underside at y 31–34 for x 28–44 is a ceiling over the cave. Neon GSP clump on its front-left face at (20, 40, 14). |
| **CAVE_BIG** | Left cavern (carved) | T3–T4 | negative balls c (34, 18, 6) r (10, 11, 9), c (42, 20, 5) r (8, 10, 8), c (30, 12, 10) r (6, 7, 6) | opening y 6–31 | through to z 0.5 | **Opening on the front face x 27–44, y 6–31**: pure black background visible (biggest dark area in the photo, between the two pillars). Floor = sand. Tang/eel sleeping spot. |
| **R4** | **Shelf rock** across the top-back | T4–T5 | slab: ellipsoids c (44, 46, 7) r (10, 7, 7), c (54, 45, 6) r (10, 6, 6); plus bridge c (60, 42, 5) r (7, 6, 5) | 53 | 0–15 | A horizontal slab leaning on the back glass from R1b to x 66, **underside at y 36–38 (open beneath, x 44–64, through to the black back)**. Pale bare-tan top at x 36–46 (the lightest rock in the photo, right under the brace). Pink coralline boulder face at x 50–58, y 36–44. Hawkfish perch on top at (48, 52, 10). Shrimp ledge under its lip at (40, 36, 13). |
| **R3** | **Center pillar** | T4–T5 | base c (49, –, 22) r (5.5, 5); shaft r 4.5; top knob c (50, 26, 22) r (5, 3.5, 4) leaning right | **29** | 17–28 | Pale grey-green stub, flat-ish top. Free-standing, 2 cm in front of R4's front face. Hawkfish perch top at (50, 29, 22). Green mushroom frag at its right foot (57, 4, 27). |
| **LANE** | Bare sand lane | T3 front, T5 front | x 24–44 for z 16–33; x 55–63 for z 18–33 | sand | – | Nothing but sand, a red macroalgae tuft at (38, 4, 28) and the green frag. The cleaning station is here, at the cave mouth. |
| **R5** | **Kenya mound** | T6 | ellipsoids c (71, 10, 18) r (9, 8, 8), c (74, 18, 15) r (7, 6, 6); flat cap c (70, 20, 21) r (6, 2.5, 5) (overhanging shelf toward the front) | **24** | 8–27 | Lumpy mound, its front-bottom is a flat overhanging lip at y 8–10 for x 63–72, z 24–27: **goby/eel burrow entrance** beneath it at (66, 4, 25), tunnel negative balls r 3.5 along (66,4,25)→(70,5,20)→(75,6,15)→(79,7,10)→(83,8,5). Kenya tree base at the cap's top-front (73, 24, 18). Organ pipe at (76, 22, 20), clove polyps at (75, 13, 25). |
| **R6p** | Arch back pier | T7 | box-ish ellipsoid c (78, 20, 4) r (4, 18, 4) | 38 | 0–8 | Hidden mostly behind the Kenya crown; supports the slab. |
| **ARCH** | **Right arch opening** | T7–T8 | **open: x 82–90, y 6–38, z 0–16** (negative balls c (86, 22, 6) r (5, 16, 8)) | – | to z 0.5 | The dark window right of the Kenya tree and left of the clowns. Dottyback cave at its back-left (84, 12, 3). Clown host volume in front of it. The circular powerhead sits in its upper part on the back glass (§3). |
| **R6s** | **Right arch roof slab** | T7–T8 | low ledge c (84, 41, 6) r (8, 3, 6) (x 76–92, y 38–44) + tall slab c (96, 47, 6) r (7, 8, 6) (x 89–103, y 39–55) | **55** at x 92–101 (1 cm cap above the surface at x 94–98) | 0–13 | Flat tan-green top with a pale bare crest; its front lip overhangs 3 cm (shrimp ledge underside at (95, 38, 11)). |
| **R6c** | Right column | T8 | c (95, 20, 6) r (6, 17, 6) | 38 | 0–12 | Stack of two rocks between arch and the right stack; dark olive, coralline on its left face (x 89–91, y 18–30: `#C27090`). |
| **R9** | Right back stack | T9 | c (105, 30, 6) r (6, 14, 6), c (104, 46, 5) r (5, 5, 5) | 51 | 0–12 | Greenish boulders behind the GSP mound, pale slab top at y 48–51. Perch at (104, 51, 6). |
| **R7** | **GSP mound** | T9 | dome c (107, 8, 24) r (9, 9, 9) flattened (y scale 0.9) | **25** at (106, 25, 22) | 14–33 | Low, wide dome fully carpeted by the big GSP mat on its top and front (x 99–115, y 6–25). The clown gap is between R5/ARCH and this mound: x 84–99, y 10–34, z 14–30. |
| **R10** (v3) | **Clowns' rock** | T7 | c (82, 5.5, 22) r (7, 4, 6) + top c (81.5, 9, 21) r (5, 2.5, 4.5) | 13 | 16–28 | Low purple-coralline rock (zone Z8) in front of the right recess, under the shelf's front edge; carries the hairy-mushroom host (`HOST_SHROOM`), the coralline crust and the yellow encrusting patch at its left (T84). The duster sits at its right foot. |
| **R8** | Far-right corner rock | T10 | c (115, 6, 25) r (7, 5, 7) | 12 | 18–33 | Dark maroon/purple rock pressed into the front-right corner (`#5E2E45` base, 60 % coralline). Red macroalgae tuft at (118, 7, 27). Pink-coralline knob c (109, 27, 14) r (4, 3, 4) sits on R7's back-top (the pink/yellow-tipped lump in the photo). |

Openings where the black background must be visible from the front camera: CAVE_BIG (x 27–44, y 6–31), under R4 (x 44–64, y 6–36, behind R3), ARCH (x 82–90, y 6–38), and a slit between R9 and R6s at x 101–103, y 36–46.

### 2.1 Surface color zones (bake into `rock_albedo`; coverage = fraction of zone area)
Hair algae is the dominant look: long, matted, olive-green to brown filaments that soften every edge. Model it as (a) albedo tint, (b) 0.3 cm fuzzy displacement, (c) 2–4 cm alpha-card tufts at GRAZE anchors and on every up-facing crest (≥ 60 tufts).

| Zone | Where | Hair algae `#5C6B2E`→`#3E4A22` (shadow) | Brown film `#6E5B3A` | Bare tan `#D9CBB0` / white `#ECE6D6` | Coralline pink `#C96F8F` / purple `#7A4F9A` |
|---|---|---|---|---|---|
| Z1 R2, R0, R3 pillars | all faces | 65 % | 15 % | 15 % (R3 upper half is 35 % pale) | 5 % |
| Z2 R1a/R1b left stack | front and top | 55 % | 10 % | 10 % | **25 %** concentrated at x 28–44, y 34–50 (red/pink haze in the photo; plus red macroalgae tufts §3) |
| Z3 R4 shelf | top | 35 % | 15 % | **40 %** (bare pale crest x 36–46) | 10 % (pink boulder x 50–58) |
| Z4 R5 Kenya mound | all | 50 % | 20 % | 20 % | 10 % |
| Z5 R6 arch, column, pier | all | 50 % | 15 % | 20 % (pale crest of R6s) | 15 % (left face of R6c) |
| Z6 R9 right stack | all | 60 % | 15 % | 20 % (slab top) | 5 % |
| Z7 R7 under the GSP mat | exposed rim only | 30 % | 20 % | 10 % | 40 % |
| Z8 R8 corner | all | 10 % | 20 % | 0 % | **70 %** maroon `#5E2E45`, pink `#B5546F` |
| Cave interiors (all openings, z < 8) | | 15 % | 55 % dark brown `#3A2F22` | 5 % | 25 % purple `#5A3F6B` (coralline in shade) |
Roughness 0.85 (algae) / 0.7 (coralline); AO-driven darkening of crevices to `#2B2A1F`.

## 3. Corals, macroalgae, equipment — exact placements (`corals.py`, `equipment.py`)

Positions are the base point on the rock/sand (snap to the nearest surface within 2 cm, keep the given normal).

### 3.1 Corals and algae
| Node | Position (x, y, z) | Normal | Size / look |
|---|---|---|---|
| **KenyaTree** | (73, 24, 18) | (0, 1, 0.2) | Stalk r 2, 8 cm tall, tan-pink `#C9A08E`; crown ellipsoid 26 wide × 22 tall × 20 deep, centered (73, 37, 17), top y 48. Branch color `#D8A9A0` with pale `#EBD3CC` polyp tips; dense feathery branching, a few branches lean right over the arch. The single largest soft object in the tank. |
| **GspNeon** (G1) | (20, 40, 14) on R1b's front-left | (0.3, 0.5, 0.8) | 10 × 8 cm clump, polyps **neon yellow-green `#C4FF3C`**, mat `#5A3F6B`; reads as a lime pom-pom; 25 cm below the surface. |
| **GspMat** (G2) | (107, 16, 24) draped over R7 | shrinkwrap | 16 × 19 cm mat: top + front of R7, x 99–115, y 6–25, z 14–33. Polyp color green `#59D64A`, mat purple-brown `#4E3860`; also a 5 cm spill onto the sand at the front-left (x 99–102, y 4). |
| **ZoaColony** (C4) | (66, 4, 28) small rubble rock 6 × 4 cm | (0, 1, 0) | 30 polyps orange `#E0792A` with green centers + brown `#6B4A2E`; in front of the burrow lip. |
| **PalyColony** (C4b) | (21, 4, 26) at R2's right foot | (0.2, 1, 0.3) | 20 polyps brown `#7A5A36` with tan centers, and a second 10-polyp orange patch at (24, 5, 25). |
| **ClovePolyps** (C6) | (75, 13, 25) R5 front-right | (0.3, 0.6, 0.75) | 8 × 6 cm; 40 feathery stalks 3 cm, tan-grey `#B8A38E`, 8 pinnate tentacles each. |
| **OrganPipe** (C8) | (76, 22, 20) right of the Kenya stalk | (0.2, 0.9, 0.3) | 6 × 5 × 4 cm red skeleton `#A5343A` with grey-green polyps `#8FA38A` (looks like a second clove patch from the front). |
| **MushroomGreen** (C5) | (57, 4, 27) | (0, 1, 0) | 3.5 cm disc, green `#5DBD5E` with a tan plug. |
| **MushroomSmall** | (112, 28, 12) on the pink knob | (0.1, 0.9, 0.4) | 2.5 cm disc, red-brown `#8B3A4A`. |
| **MacroRed_1** | (36, 42, 12) on R1b right face | (0.2, 0.5, 0.8) | Red gorgonian-like macroalgae (Gracilaria/ "red grape" look): 10 cm tuft of thin branching red `#C23B5E` filaments; alpha cards + 40 thin tubes. |
| **MacroRed_2** | (42, 48, 10) | (0, 0.7, 0.7) | 7 cm tuft, same. |
| **MacroRed_3** | (38, 4, 28) on sand in the lane | (0, 1, 0) | 5 cm tuft `#B53556`. |
| **MacroRed_4** | (118, 7, 27) on R8 | (0.2, 0.8, 0.5) | 6 cm tuft `#B5294F`. |
| **AlgaeClumpLeft** | (6, 4, 24) on sand at the left foot | (0, 1, 0) | 7 × 4 cm loose green hair-algae ball `#7FA23A`. |
| **Aiptasia** ×3 | (68, 25, 16), (31, 30, 13), (92, 36, 9) | surface | 1.5 cm brown anemones. |
| **HairTufts** | GRAZE anchors + every crest | – | ≥ 60 alpha cards 2–4 cm, `#5C6B2E`, 2-sided. |

### 3.2 Equipment (all closed meshes; black plastic `#111214` roughness 0.5 unless noted)
| Node | Placement | Geometry |
|---|---|---|
| **PowerheadLeft** | on the LEFT glass: magnet plate at x 0–1.5, center (0.8, 44, 14); body axis points +X | Body: ribbed cylinder r 3.2 × 11 cm (12 rings 1.5 mm deep) from x 1.5 to 12.5 at y 44, z 14; front grille disc r 2.6; a cable r 0.35 from the body top going up the glass to the rim and over at (3, 61, 12) (`#1A1A1A`). This is the dark ribbed lump at upper-left in the photo. |
| **PowerheadBack** | on the BACK glass at (85.5, 47, 0); faces +Z, tilted 15° down-left | Round "circulation pump": disc r 4.0 × 4.5 cm thick, front cage of 12 radial slots, magnet 4 × 4 × 2 on the outside of the back glass; cable going up to the rim at x 84. Centered in the top of the ARCH window. |
| **PowerheadRight** | on the RIGHT glass: plate center (121.2, 44, 10); body axis −X | Same ribbed body as the left one, 10 cm long (x 111–120.5), with a bigger black foam sleeve r 3.8 × 6 on its intake end (the fat ribbed black lump at the right end of the photo). Cable up the right glass. |
| **Heater** | vertical on the back glass at x 119.5, z 2.5, y 21…52 | Tube r 1.4 × 31; black body, grey top cap `#8C8F93` 3 cm with a 2 mm red LED emissive `#FF2A1A` at y 51; suction cups 2 × r 1.3 at y 26 and y 46. |
| **IntakeTube** | from the rim down the right end: x 116.5, z 5, y 28…63 | Grey tube r 1.3 `#8A8F94` with a black strainer cylinder r 2.6 × 8 at its bottom (y 28–36); elbow at the top over the rim into the HOB box. |
| **Flipper** | on the FRONT glass inner face: box x 2…8, y 13…33, z 31.4…32.9 | 6 × 20 × 1.5 cm black; white "FLIPPER" + "FLOAT" label text on the front face (texture, rotated 90°, reading top-to-bottom); 4 × 1 mm felt pads touching the glass. A matching outer half (x 2…8, y 13…33, z 33.735…35.035) sits flush on the outside of the glass on its own 1 mm pads. (Was x 1…7 with the outer half at z 33.6…36; that stood 2.4 cm proud and the label panel projected past the tank's left edge.) |
| **FrontMagnet** (feeding/cleaner pad) | on the sand against the front glass, center (72, 6.5, 29) | Black cylinder r 4.2 × 4.5 cm, axis +Z (face toward the viewer, 1 cm from the glass), with a light grey-green angled bracket `#9CA59A` 9 × 2 × 4 cm lying to its left (x 65–72) at y 4–8. |
| **HoseArc** | clear tube, Bezier through (8, 36, 16) → (13, 48, 14) → (22, 54, 12) → (30, 50, 10) | r 0.8 cm, material clear `#DDE8E6` alpha 0.4, roughness 0.15, capped ends; a big smooth arc rising from behind the left powerhead over R2's crown and dropping onto R1b's top (photo: the bright curved tube upper-left). |
| **CableLeft** | r 0.35 black, from the Flipper down the left glass along the sand to (2, 4, 20) then up the back-left corner to the rim | gives the photo's loop of cable by the left pillar. |
| **FeedRing** | floating at (41, 56.2, 18) | Torus R 4.5, r 0.35, clear `#E6EEF0` alpha 0.5. In the photo it rests on the rim at x 40; in the sim it floats. |
| **HobFilter** | hangs on the back rim: body x 60…84, y 48…66, z −11…−1.035 (flush against the rim skirt), lid y 66…67 | Dark grey `#2A2C30` body with a lid; two rubber levelers (r 0.8) bridge the skirt-to-glass step at y 51; two upright filter cartridges 7 × 1.5 × 14 (`#3A3C40`, slotted) standing up out of the lid at x 66 and x 76; a 14 cm spill tray rests on the lip (y 61.4…62.0, z −1.035…2.6) with side walls, and its spout drops inside the glass to y 55 (1 cm into the water); an intake tube r 1.3 drops inside the tank at (82, 40…60, 3.2) with a black strainer at its foot and elbows over the rim into the body. Outflow (bubble spawn) (72, 55, 2.3). |
| **Skimmer** | right of the HOB, hangs on the back rim: body x 86…96, y 46…70, z −9…−1.035 (flush against the skirt) | Black pump block x 87…95, y 40…46 under the body; two rubber levelers on the glass at y 49; lid y 70…71 with two grey riser tubes r 1.6, y 71…92, at (89, –, −6) and (93, –, −6) (`#8A8F94`) with black collars; a black hanger saddle over the lip (x 87.5…94.5) with a front leg inside the glass and an intake leg r 1.0 dropping into the water at (91, 46…58, 3.4). These are the tall grey tubes above the rim right of center in the photo. |
| **AtoBox** | on the top rim, left: box x 16…22, y 61.4…63.4, z 28…33 | Small black controller with a 3 mm red LED emissive `#FF2020` at (19, 63.4, 30) (the red dot on the left rim). |
| **Jar** | on the right rim at x 102.5…110.5, y 61.4…71, z −4…4 (moved right to clear the AiPrimeR clamp) | Glass cylinder r 4 × 9.5, alpha 0.5, with a dark green-brown liquid fill 6 cm (`#4D4A2E`). Optional prop. |
| **AiPrimeL / AiPrimeR** | pucks centered at **(32, 71.6, 20)** and **(88, 71.6, 20)** (resized tank), horizontal; lens centre / `lightPos` (x, 69.6, 20) = `LightingState.LIGHT_POS` | Puck: rounded box 12.2 × 12.2 × 3.6 cm, black `#141414`, bottom face: a white lens plate 8 cm wide with a 4 × 4 cluster of 2.5 mm LED dots (emissive, color = §5 light color); top has a 1 cm raised heat-sink square; an arm socket on the rear edge. **Rim clamp**: a C-clamp 3.8 cm wide at x 38 (L) / 98.5 (R) wrapping the back rim: top plate on the lip (y 61.4…62.3, z −1.9…2.6), back leg down the skirt (y 57…61.4) with a thumb screw + knob, front leg inside the glass and a jaw foot under the lip with a screw pressing the lip underside and a thumb knob below. **Gooseneck arm**: black tube r 0.8 from a collar on the clamp, Bezier (cx, 62.3, 0.2) → (cx, 84, 0.2) → (x, 92, 1) → (x, 92, 9.4) into the socket. Power cable r 0.3 from the puck's rear, along the arm, behind the clamp and down the back of the tank. |

## 4. Fish hangout anchors (`anchors.json`; replace the list in ASSETS.md §3.4)
```json
{"anchors": [
 {"name":"HOST_ARCH_CLOWNS","type":"HOST","pos":[88,24,20],"radius":9,"note":"clown pair hovers in front of the right arch between the Kenya tree and the GSP mound"},
 {"name":"HOST_GSP_RIGHT","type":"HOST","pos":[105,20,24],"radius":6},
 {"name":"BURROW_GOBY","type":"CAVE_EEL","pos":[66,4,25],"dir":[0.55,0.1,-0.83],"note":"engineer goby / eel burrow under the Kenya mound's front lip"},
 {"name":"den_path","type":"PATH","points":[[66,4,25],[70,5,20],[75,6,15],[79,7,10],[83,8,5]]},
 {"name":"CAVE_BIG","type":"CAVE","pos":[36,14,6],"note":"left cavern, tang and eel sleep"},
 {"name":"CAVE_SHELF","type":"CAVE","pos":[54,16,6],"note":"under the shelf rock behind the center pillar"},
 {"name":"CAVE_DOTTYBACK","type":"CAVE","pos":[84,12,3],"note":"back-left pocket of the right arch"},
 {"name":"HOLE_BLENNY","type":"HOLE","pos":[22,18,25],"dir":[0.6,0.1,0.8],"depth":6},
 {"name":"HOLE_BLENNY_2","type":"HOLE","pos":[94,30,11],"dir":[0,0.2,1],"depth":5},
 {"name":"PERCH_R3_TOP","type":"PERCH","pos":[50,29,22],"normal":[0,1,0]},
 {"name":"PERCH_R2_CROWN","type":"PERCH","pos":[15,35,19],"normal":[0,1,0]},
 {"name":"PERCH_R5_LIP","type":"PERCH","pos":[70,24,21],"normal":[0,1,0.3]},
 {"name":"PERCH_SHELF","type":"PERCH","pos":[48,52,10],"normal":[0,1,0.2]},
 {"name":"PERCH_R9","type":"PERCH","pos":[104,51,6],"normal":[0,1,0]},
 {"name":"PERCH_CORALLINE_LOW","type":"PERCH","pos":[52,10,9],"normal":[0,1,0.6],"note":"hawkfish under the pink overhang near the sand (a_04.jpg)"},
 {"name":"STATION_CLEANER","type":"STATION","pos":[40,14,20],"note":"lane in front of the big cave mouth"},
 {"name":"SHRIMP_LEDGE_L","type":"LEDGE","pos":[40,36,13],"normal":[0,-1,0.3]},
 {"name":"SHRIMP_LEDGE_R","type":"LEDGE","pos":[95,38,11],"normal":[0,-1,0.3]},
 {"name":"HOVER_CARDINAL","type":"HOVER","pos":[26,20,15],"radius":6,"note":"gap between the left pillar and the left stack"},
 {"name":"SLEEP_TANG","type":"SLEEP","pos":[38,12,4],"dir":[-0.7,0.2,-0.7]},
 {"name":"SLEEP_WRASSE","type":"SLEEP","pos":[47,4,18],"dir":[0,0,-1],"note":"wrasse buries at the center pillar's back foot"},
 {"name":"SURFACE_FEED","type":"FEED","pos":[41,55,18]},
 {"name":"NORI_CLIP","type":"FEED","pos":[2,35,20]},
 {"name":"HOB_OUTFLOW","type":"FLOW","pos":[72,55,1],"dir":[0,-0.6,0.8]},
 {"name":"FLOW_PH_LEFT","type":"FLOW","pos":[12,44,14],"dir":[1,-0.1,0.1]},
 {"name":"FLOW_PH_BACK","type":"FLOW","pos":[85,47,4],"dir":[-0.3,-0.3,0.9]},
 {"name":"FLOW_PH_RIGHT","type":"FLOW","pos":[111,44,10],"dir":[-1,-0.1,0.2]},
 {"name":"GRAZE_1","type":"GRAZE","pos":[18,30,25]},{"name":"GRAZE_2","type":"GRAZE","pos":[30,46,14]},
 {"name":"GRAZE_3","type":"GRAZE","pos":[44,20,27]},{"name":"GRAZE_4","type":"GRAZE","pos":[50,52,10]},
 {"name":"GRAZE_5","type":"GRAZE","pos":[72,16,26]},{"name":"GRAZE_6","type":"GRAZE","pos":[96,46,10]},
 {"name":"GRAZE_7","type":"GRAZE","pos":[104,38,8]},{"name":"GRAZE_8","type":"GRAZE","pos":[60,5,30]},
 {"name":"coral_kenya_main","type":"CORAL","pos":[73,24,18],"normal":[0,1,0.2]},
 {"name":"coral_gsp_upper_left","type":"CORAL","pos":[20,40,14],"normal":[0.3,0.5,0.8]},
 {"name":"coral_gsp_lower_right","type":"CORAL","pos":[107,16,24],"normal":[0,0.7,0.7]},
 {"name":"coral_zoa_front_center","type":"CORAL","pos":[66,4,28],"normal":[0,1,0]},
 {"name":"coral_paly_left_foot","type":"CORAL","pos":[21,4,26],"normal":[0.2,1,0.3]},
 {"name":"coral_clove_kenya_front","type":"CORAL","pos":[75,13,25],"normal":[0.3,0.6,0.75]},
 {"name":"coral_organpipe_kenya_base","type":"CORAL","pos":[76,22,20],"normal":[0.2,0.9,0.3]},
 {"name":"coral_shroom_center","type":"CORAL","pos":[57,4,27],"normal":[0,1,0]},
 {"name":"coral_macro_back_left","type":"CORAL","pos":[36,42,12],"normal":[0.2,0.5,0.8]},
 {"name":"coral_macro_right_corner","type":"CORAL","pos":[118,7,27],"normal":[0.2,0.8,0.5]}
]}
```
Snap each `pos` to the nearest rock/sand surface after generation (max 2 cm) and write the snapped values.

## 5. Lighting and look (for `preview.py` renders)
- **Fixtures**: two spot/area lights at the AiPrime puck positions, pointing −Y, cone 110°, blend 0.4. Color **blue-heavy 16k look**: `#8FA9FF` at 85 % + a warm white `#FFF1DC` at 15 % (mix as two lamps per puck, 2000 W + 350 W in Cycles at 1 cm = 0.01 m scale, or 1 kW/180 W Eevee). A faint violet fill from the wall bounce `#6A5BD6` 5 %.
- **Water**: volume absorption `#2E8A78` density 0.045 /cm (greenish tint, back wall reads darker and greener); 1 % scatter for light shafts.
- **Caustics on the sand and rock tops**: projected animated texture `caustic_512` through the puck lamps (light texture or an emissive-shader gobo), scale 9 cm, intensity 0.6, scrolling 2 cm/s; sharpest at the surface-lit sand lanes (x 24–44, x 55–63, x 84–99).
- **Surface**: the camera sees the underside of the water surface as a mirror-dark band at the top (photo: the top 3 cm are the reflected rim and rock); keep total internal reflection on.
- **Rock algae response**: hair algae is soft and matte; bias subsurface 0.1 green `#6E8A3A`.
- **White balance** of output: lightly warm (the phone photo shows sand slightly blue-white `#D7DEE6`; sand should render `#D7DEE6` lit, `#9FB0C4` in shade).
- Exposure so the sand reads 80 % luminance, the black background 3 %, the Kenya tree 60 %.

## 6. Verification checklist (render all five, 1600 × 1200, Eevee or Cycles 64 spp)
| View | Camera (position → target, cm; vertical FOV) | Compare against |
|---|---|---|
| **Front** (match `tank_full.jpg` framing: the rim top at the frame's 30 % line, stand top at 78 %) | (61, 48, 215) → (61, 30, 16); FOV 38°, slight downward tilt so the top lip ledge is visible | silhouette of R2/R1/R4/R3/R5/arch/R7; the three black openings (big cave, under-shelf, arch); the Kenya crown centered at x 73 and reaching y 48; the GSP mat footprint x 99–115; the neon clump at (20, 40); the powerhead circle at (85.5, 47); the heater and ribbed pumps at both ends; the Flipper at the left front; the front magnet at x 72; the feed ring; the HOB + skimmer tubes and both lights above the rim; the X-lattice stand door and dark walnut top. |
| **3/4 left** | (−40, 70, 150) → (50, 25, 16); FOV 40° | the left pillar standing clear of the left stack with the 3 cm gap; the hose arc over R2; the shelf rock's underside open behind R3; the frame's end profile and center brace; no missing faces on the rim/trim/stand. |
| **3/4 right** | (170, 70, 150) → (75, 25, 16); FOV 40° | the arch's see-through window; the right slab touching the surface; R7 dome shape under the GSP mat; the right pumps, intake tube and heater not intersecting rock; cable routing. |
| **Top** | (61, 170, 16) → (61, 0, 16); FOV 40° | footprints vs the table in §2: depth placements (pillars front, stacks against the back glass); sand lanes bare; the rim lip width and the brace; the HOB/skimmer sitting on the back rim; lights over the tank at x 32 / 91. |
| **Left end** / **Right end** | (−150, 35, 16) → (0, 30, 16) and (272, 35, 16) → (122, 30, 16); FOV 35° | end-pane powerheads flush to the glass, Flipper inside+outside halves, rim/trim closed at the ends, seams visible, stand sides white and closed. |
Additional automated checks the build should print: per-object non-manifold edges = 0; no rock vertex below y = 1.5 or above y = 57 (except the two 1 cm caps); every anchor within 2 cm of a surface; rock tri count ≤ 45k; the back-glass black is visible through the three openings in the front render (sample pixels at the opening centers: luminance < 8 %).
