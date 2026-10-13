# ReefLive — UI Style System ("Abyss")

Ocean-inspired, modern, glassy. Dark theme is primary ("Abyss"); light theme ("Lagoon") is a faithful mirror. All values below are tokens; implement them once in `app/src/main/java/com/reeflive/app/ui/theme/` and never use raw hex in screens.

---

## 1. Color tokens

### 1.1 Abyss (dark, default)

| Token | Hex | Use |
|---|---|---|
| `abyss0` | `#030912` | Deepest background (behind the caustic layer) |
| `abyss1` | `#061327` | Base page background |
| `abyss2` | `#0A1F3D` | Gradient mid |
| `reef` | `#0E3B5A` | Gradient top / elevated sections |
| `lagoon` | `#1FA6C9` | Primary (buttons, active tab, links) |
| `lagoonBright` | `#5CE1E6` | Primary highlight / focus ring / sparkline |
| `foam` | `#E8F6FA` | On-surface text (primary) |
| `mist` | `#A7C4D4` | Secondary text |
| `kelp` | `#6E8FA3` | Tertiary text, disabled, axis labels |
| `glass` | `#FFFFFF` @ 8% | Card fill (glassmorphism) |
| `glassBorder` | `#FFFFFF` @ 16% | Card 1 dp border |
| `glassHi` | `#FFFFFF` @ 14% | Hovered/pressed card fill |
| `coral` | `#FF7A59` | Accent (FAB, hero highlights, "live" dot) |
| `coralSoft` | `#FFB08F` | Accent on dark text |
| `anemone` | `#C77DFF` | Secondary accent (overlay series, journal) |
| `sand` | `#E6DFD0` | Warm neutral (equipment icons, empty states) |

### 1.2 Status colors (accessible on `abyss1`/glass; contrast ≥ 4.5:1 for text, ≥ 3:1 for fills)

| Token | Hex | Text-on-dark variant | Fill @ opacity | Meaning |
|---|---|---|---|---|
| `statusGood` | `#35D0A6` | `#7CF2D0` | 22% | In ideal range |
| `statusCaution` | `#FFB547` | `#FFD089` | 22% | Acceptable but outside ideal, stale, slow drift |
| `statusDanger` | `#FF5C7A` | `#FF93A7` | 26% | Danger threshold or too-fast change |
| `statusUnknown` | `#6E8FA3` | `#A7C4D4` | 14% | Never logged |

Each status also has an icon: good = `check_circle`, caution = `warning` (triangle), danger = `error` (octagon), unknown = `help`. Never color-only.

### 1.3 Lagoon (light theme)

| Token | Hex |
|---|---|
| `bg0` | `#F2FAFD` |
| `bg1` | `#E3F3F9` |
| `bg2` | `#CFE9F3` |
| `reef` (sections) | `#B9DFEC` |
| `lagoon` (primary) | `#0B7FA3` |
| `lagoonBright` | `#0A9CC4` |
| `foam` (text primary) | `#0A1B2B` |
| `mist` (text secondary) | `#3E5A6E` |
| `kelp` (tertiary) | `#6E8699` |
| `glass` | `#FFFFFF` @ 55% |
| `glassBorder` | `#0A1B2B` @ 10% |
| `coral` | `#E85D3A` |
| `anemone` | `#8E44D8` |
| statusGood / Caution / Danger text | `#0E8C6C` / `#B26A00` / `#C8203F` |
| status fills | same hues at 18% |

### 1.4 Gradients

| Name | Stops | Use |
|---|---|---|
| `bgAbyss` | 0% `#061327` → 55% `#0A1F3D` → 100% `#0E3B5A` (vertical, top dark → bottom lighter: looking up from the deep) | Page background (dark) |
| `bgLagoon` | 0% `#F2FAFD` → 100% `#CFE9F3` | Page background (light) |
| `heroTile` | 135°: `#0E3B5A` → `#1FA6C9` @ 35% opacity over glass | Dashboard hero |
| `primaryButton` | 90°: `#1FA6C9` → `#5CE1E6` | Filled buttons |
| `coralButton` | 90°: `#FF7A59` → `#FFB08F` | FAB |
| `lightArc` | horizontal: `#2B3A6E` (moon) → `#6A3DFF` → `#2E5BFF` → `#DDEEFF` → `#3AA0FF` → `#2B3A6E` | Live Tank light HUD |
| `sparkFill` | vertical: series color @ 35% → 0% | Sparkline area |

---

## 2. Background: animated caustics layer

Behind every screen (not inside cards): `CausticBackground` composable.
- Layer 1: `bgAbyss` gradient.
- Layer 2: two drifting radial "light shafts": radial gradients `#1FA6C9` @ 10% → 0%, radius 60% of width, centers moving on slow Lissajous paths (periods 23 s and 31 s, amplitude 15% of width/height).
- Layer 3: caustic net: a tiled `R.drawable.caustic_tile` (512x512 WebP, generated with the Voronoi-edge script in `tools/textures/caustics.py`) drawn with `BlendMode.Screen` at 6% alpha, two copies scrolling at (6, 4) dp/s and (−4, 7) dp/s with scale 1.0 and 1.6.
- Layer 4: 12 bubbles (circles r 2–6 dp, alpha 10–25%) rising at 20–40 dp/s, wobble ±4 dp, respawn at bottom.
- Rendering: one `Canvas` with `withInfiniteTransition`; pauses when the screen is not resumed; disabled entirely by Reduce motion (static gradient only). Budget: < 2 ms/frame.

Cards sit on top with glass fill so the caustics show through faintly.

---

## 3. Typography

Bundle as `res/font/` (Google Fonts, OFL):
- **Display / Headlines:** `Outfit` (Outfit-SemiBold 600, Outfit-Medium 500). Geometric, rounded, modern.
- **Body / UI:** `Inter` (Inter-Regular 400, Inter-Medium 500, Inter-SemiBold 600) with tabular numerals (`fontFeatureSettings = "tnum"`) for values.
- **Numbers in tiles/charts:** `Inter` with `tnum` so columns align; big hero value uses `Outfit-SemiBold`.

Scale (Material 3 names, sp):

| Style | Font | Size/Line | Weight | Letter spacing |
|---|---|---|---|---|
| displayLarge | Outfit | 44/52 | 600 | −0.5 |
| displayMedium (hero value) | Outfit | 36/42 | 600 | −0.5 |
| headlineMedium (screen title) | Outfit | 26/32 | 600 | −0.25 |
| headlineSmall (section) | Outfit | 20/26 | 500 | 0 |
| titleMedium (card title) | Inter | 16/22 | 600 | 0 |
| bodyLarge | Inter | 16/24 | 400 | 0 |
| bodyMedium | Inter | 14/20 | 400 | 0 |
| labelLarge (buttons, chips) | Inter | 14/20 | 600 | 0.1 |
| labelMedium (tile unit, axis) | Inter | 12/16 | 500 | 0.3 |
| labelSmall (timestamps) | Inter | 11/14 | 500 | 0.4 |

Tile value: 28/32 Outfit 600. Sparkline axis: none. Chart axis labels: labelMedium in `kelp`.

---

## 4. Surfaces, shape, elevation

- Corner radius: cards 20 dp, tiles 18 dp, chips 999 dp (pill), buttons 999 dp, bottom sheets 28 dp top, dialogs 24 dp, text fields 14 dp.
- **Glass card** (`GlassCard`): fill `glass`, 1 dp border `glassBorder`, inner top highlight: 1 dp line `#FFFFFF` @ 10% along the top edge, shadow: `ambient #000000 @ 25%, 12 dp blur, y 6 dp` (dark) / `#0A1B2B @ 12%` (light). Content padding 16 dp. Optional `tint` (status color at 8%) for status cards. On Android 12+ apply `RenderEffect.createBlurEffect(18f, 18f)` to a snapshot of the background behind the card only when the card is in a scrolling LazyColumn of ≤ 12 cards; otherwise fake the blur with the gradient (performance guard: a `glassBlurEnabled` flag defaulting to true on API 31+ with ≥ 6 GB RAM, false otherwise).
- Elevation is expressed with blur + border, not Material tonal elevation.
- Dividers: `glassBorder` 1 dp, hairline only inside lists.

---

## 5. Iconography

Material Symbols Rounded (`androidx.compose.material:material-icons-extended`, weight 400, fill 1 for the active tab, 0 inactive; 24 dp, 20 dp inside chips) remain for the app's utility screens (navigation, log forms, settings). The live tank and the sandbox use ReefLive's own set below, so that every control a child taps looks like the thing it does.

### 5.1 ReefLive icon set (`ui/components/ReefGlyphs.kt`)

- 64 friendly two-tone glyphs on a 24 × 24 grid, 1.75 dp rounded strokes (2 dp for the chevron), drawn with Compose `Canvas` from SVG path strings tagged with a role: `INK` / `INK_FILL` take the text colour, `ACCENT` / `ACCENT_FILL` the soft coral (`coralSoft`, or a channel colour for LED rows), `SOFT` / `ACCENT_SOFT` are 22 % / 30 % washes. Paths parse once per process (`PathParser`), nothing is a bitmap or an XML drawable.
- Sizes: `Glyph(glyph, size)` 24 dp default, 20 dp in chips and tabs, 18 dp inside a `ReefButton`; `GlyphBadge` is the 40 dp tinted disc beside every sandbox row (48 dp for the lamp row).
- Families, by sandbox tab: Water (thermometer, salt, flask, bubbles, droplet, dropPlus / dropMinus / dropDown, waveSparkle, flame, snowflake), Minerals (shell, branch, rings, palette, gem, bottle, copperBottle), Waste & food (fishWaste, halfClean, crumbs, seaweed, fuzzyRock, feedTin), Light & waves (sun, moon, flow, clockSun, powerhead and its mirrored twin, led), Bugs & medicine (skimmer, sock, carbon, refugium, uvLamp, starfish, crab, pestAnemone, shrimp, slug, plugOff, calendarX), live buttons (fish, whatIf, eye / eyeOff, bucket, microscope, flipper, speaker / speakerOff, timelapse, touch, rocks, reset) and utilities (plus, minus, close, info, help, chevronUp, heart, trash, sparkleTip).
- Animated icons (`ui/live/LiveGlyphArt.kt`) replace the static glyph where a value is behind it: the thermometer's column follows the temperature slider, the bubbles rise faster and larger with oxygen / flow, the sun rises and sets with the light level (the moon takes over below 15 %), the eye blinks shut on the hide-controls button, the speaker's waves fade with the mute setting, and `ReefSwitch` is a water-filled toggle whose thumb morphs into a wave while it fizzes bubbles. Every animated read happens inside the Canvas lambda, so an idling icon never recomposes its row or the GL view; under Reduce motion they freeze at a sensible pose.
- Buttons, chips and tabs on the utility screens use `Modifier.tapSplash` (`ui/components/Splash.kt`): a soft disc from the press point followed by a thin ring, 450 ms, instead of the Material ripple. Every live-tank control adds `Modifier.pressSquash` (squash to 1.07 × 0.88 on press, springy rebound past 1 on release, scaled from the bottom edge) and `Modifier.bubbleBurst` (six bubbles rise ~56 dp from the press point over 700 ms) plus a haptic tick (§10.1). Nothing moves under Reduce motion; haptics stay.
- Preview sheet: `python3 tools/graphics/glyph_preview.py --out <dir>` renders `icon-set.png/.svg` from the Kotlin tables.

### 5.2 Food pictures (`ui/live/FoodArt.kt`)

One illustration per `FoodKind`, drawn on a 48-unit box in the `FoodPalette` tokens (`theme/Color.kt`) that the tank's particle tints are matched to: Flakes (four coloured flakes), Mysis shrimp (pink curled shrimp), Sinking pellets (brown balls), Nori sheet (on its clip, a bite out of a corner), Live pods, Prawn chunk, Phytoplankton (green haze), Brine shrimp (orange, feathery legs), Krill (red, big eye), Floating pellets (half out of the water line), Algae wafer (speckled green disc), Coral food (pink powder cloud) and Cyclops (orange-red). `FoodPicture(kind, drop)` plays the "dropped in" beat: the picture dips, squashes as it lands and sends two ripple rings out beneath it. The same script renders `food-pictures.png`. The containers that feed each food in the tank (shaker, frozen cube, nori clip, pipette) are in §10.2.

Species art: `res/drawable/species_<id>.webp` 512x512, flat illustration with soft lighting, same palette as the 3D texture; generated from the Blender model with a toon render (ASSETS §9).

---

## 6. Components

### 6.1 Bottom navigation
Floating glass pill, 64 dp tall, 16 dp from bottom and sides, at most 560 dp wide (`BottomBarMaxWidth`, centred: on a wide landscape screen the Live tank's rail runs down beside it), 5 items. Active item: filled icon in `lagoonBright`, label visible, 32x32 dp pill behind the icon in `lagoon` @ 20%. Inactive: `mist`, label hidden on phones < 360 dp wide. Center item (Live Tank) has a 4 dp `coral` "live" dot when the simulation is running in the background (it is not; dot shows when Sandbox has unsaved changes).

### 6.2 FAB
56 dp circle, `coralButton` gradient, icon `add` in `#1A0C08`, shadow coral @ 35% 16 dp. Press: scale 0.94, haptic `CONFIRM`.

### 6.3 Buttons
- Filled: `primaryButton` gradient, text `#04111C` labelLarge, height 48 dp, pill.
- Tonal: `lagoon` @ 16% fill, text `lagoonBright`.
- Outlined: 1 dp `glassBorder`, text `foam`.
- Destructive: `statusDanger` @ 16% fill, text `#FF93A7`.

### 6.4 Chips
Pill, 32 dp, fill `glass`, border `glassBorder`; selected: fill `lagoon` @ 22%, border `lagoon`, text `lagoonBright`. Parameter chips show the glyph at 18 dp.

### 6.5 Text fields
Fill `#FFFFFF` @ 6%, 14 dp radius, 1 dp border `glassBorder`, focused border `lagoonBright` 1.5 dp + outer glow `lagoonBright` @ 20% 6 dp. Numeric fields: Inter 22 sp tnum. Unit suffix in `mist`.

### 6.6 Status pill
Height 24 dp, fill status @ 22%, icon 14 dp + label labelMedium in the status text variant. Texts: "In range", "Watch", "Danger", "No data", "Stale".

### 6.7 Sheets and dialogs
Bottom sheet: glass fill at 92% opacity over `abyss1` (more opaque than cards for legibility), 28 dp top radius, 32x4 dp handle in `kelp`. Dialogs: same, 24 dp radius, centered, max width 360 dp.

### 6.8 Snackbar
Glass, `foam` text, action in `lagoonBright`, slides up with a bubble pop.

---

## 7. Motion and haptics

| Event | Motion | Haptic |
|---|---|---|
| Screen enter | fade 180 ms + slide up 12 dp (emphasized decelerate) | — |
| Tab switch | crossfade 150 ms; caustic layer continues uninterrupted (shared) | `TEXT_HANDLE_MOVE` (light) |
| Save test | sheet collapses 220 ms; tiles that changed do a 300 ms "ripple": status ring sweeps, value counts up (`animateIntAsState` over 400 ms) | `CONFIRM` |
| Task complete | check circle fills with a 250 ms spring (dampingRatio 0.55); card fades 200 ms later | `CONFIRM` |
| Danger alert appears | hero tile border pulses `statusDanger` twice (600 ms each) | `LONG_PRESS` once |
| Chart scrub | tooltip follows finger with 80 ms spring; point grows to 8 dp | `CLOCK_TICK` per point crossed |
| Pull-to-dismiss sheet | standard | — |
| Live Tank button press (rail bubble, tile, pill) | `pressSquash` + `bubbleBurst` (§5.1); the 6 dp bottom shadow squashes to ~1.5 dp | `CLOCK_TICK` |
| Live menu card open / close | scale 0.6→1 from the rail / dock side + fade (350 ms), closes 150 ms | — |
| Live action (feed, dose, water change, …) | in-tank picture 0.9–3.6 s (§10.2), command sent when it reaches the water; tap the tank to skip | `CLOCK_TICK` |
| Hermie reacts | hop (or worried shake) 420–600 ms, speech bubble expands, auto-hides after 5.2 s | — |
| Happy meter changes ≥ 3 points | bar springs to the new value, pill bounces 1.12 | — |
| Tap glass (Live) | knuckle rings on the glass at the tap point (`TAP_GLASS`, 0.9 s) | `CLOCK_TICK` |
| Reduce motion | all durations 0 except crossfades at 100 ms; caustics static; live action pictures become a 700 ms still pose with a fade, the command is sent at once | haptics unaffected |

Spring defaults: `spring(dampingRatio = 0.8, stiffness = 380)`. Durations: fast 150, normal 220, slow 350 ms.

---

## 8. Component specs

### 8.1 ParameterTile (dashboard grid, 2 columns)
- Size: width = (screen − 48) / 2, height 128 dp. GlassCard, status `tint`.
- Row 1: glyph 18 dp `mist`, name labelMedium `mist`, right-aligned status icon 16 dp.
- Row 2: value 28 sp Outfit 600 `foam` (tnum) + unit labelMedium `mist` baseline-aligned; trend arrow 16 dp (`north_east`/`south_east`/`east`) in status color immediately after the unit.
- Row 3: sparkline (height 28 dp, last 14 d) with ideal band drawn as a `statusGood` @ 15% rectangle and the line in `lagoonBright` 1.5 dp, end point dot 4 dp in status color; `sparkFill` under the line.
- Row 4: "2 d ago" labelSmall `kelp`; if stale (> cadence × 1.5) → text in caution color with `schedule` icon.
- Unknown state: value "—", subtitle "Tap to log", no sparkline, dashed border `glassBorder`.
- Press: scale 0.97; long-press: lifts (shadow doubles) for drag-reorder.

### 8.2 HeroTile
- Full width, height 196 dp, `heroTile` gradient over glass.
- Left column: name headlineSmall, value displayMedium, unit, status pill, "Δ +0.3 since Tue".
- Right: 30-day mini chart (height 100 dp, width 45%) with bands and event ticks.
- Bottom: one-line insight text bodyMedium `mist` generated by `InsightEngine` (e.g. "Alk consumption 0.4 dKH/day — dose 2.7 g baking soda daily to hold 8.5").

### 8.3 Sparkline (`Sparkline(points, band, color)`)
- Canvas; x = time (linear across the window even with gaps), y = value with 10% padding above/below the min/max union of data and band.
- Line: 1.5 dp, round joins, `cubicTo` Catmull-Rom smoothing (tension 0.5).
- Band: ideal rectangle `statusGood` @ 15%; if any point lies outside acceptable, draw the acceptable band too at 8%.
- No axes, no labels.

### 8.4 TaskCard
- GlassCard height 72 dp. Left: 28 dp check circle (outlined `mist`; overdue: `statusDanger` outline with dot). Middle: title titleMedium, subtitle labelSmall ("Every 7 d · last 6 d ago"). Right: due chip ("Today" good / "2 d overdue" danger / "in 3 d" neutral).
- Swipe right: complete; swipe left: snooze 1 d.

### 8.5 LivestockCard
- GlassCard height 96 dp. Left: species art 64 dp circle crop with 2 dp ring in status (health) color. Middle: nickname titleMedium, species italic labelMedium `mist`, "In tank 1 y 3 m" labelSmall. Right: status pill ("Healthy" / "Watch" / "Verify ID" caution / "Rehomed" unknown). Warning badge `straighten` when adult size exceeds the tank.

### 8.6 FishCard (Live Tank overlay)
- A `LivePanel` (§10.1): full width at the bottom in portrait, at most 420 dp at the bottom right in landscape (clear of the rail). Name in the rounded display font, × close dot, "Follow" as a cyan candy pill.
- Header: nickname, species, behavior word in `lagoonBright`.
- Three horizontal bars 6 dp tall, pill: Health (status color by value), Hunger (`coral`), Stress (`anemone`); labels left, % right.
- Symptom chips when present ("White spots", "Clamped fins", "Gasping").
- Buttons: "Follow" (toggle), "Feed here", "Details" (→ livestock detail).

### 8.7 EventMarker (charts)
- 10 dp glyph on the x-axis baseline: dose = droplet `lagoonBright`, water change = wave `anemone`, feed = `coral` dot, note = `sand` square; tap shows a tooltip.

---

## 9. Charts (hand-rolled Compose Canvas)

Decision: hand-rolled `Canvas` charts, no Vico. Reasons: (1) zero dependency risk in a build that can only fetch Maven Central through CI; (2) exact ocean styling (bands, glows, glass tooltips) without fighting a theme API; (3) needs are narrow: time-series line with bands, overlay series, scrub, zoom, markers. Implementation lives in `app/.../ui/charts/` (`TimeSeriesChart.kt`, `ChartScale.kt`, `ChartGestures.kt`, `Sparkline.kt`).

### 9.1 TimeSeriesChart layout
- Plot area: full width minus 40 dp left (y labels) and 8 dp right; height 240 dp (detail) / 100 dp (hero).
- Y axis: 4 gridlines, `glassBorder` 1 dp; labels labelMedium `kelp`, formatted by parameter precision (temp 1 dp, sal 3 dp, alk 1 dp, ca 0, po4 2–3 dp).
- X axis: tick labels per range: 7D → weekday initials; 30D → "Oct 3"; 90D → weeks; 1Y → months. Baseline carries event markers.
- Bands: danger zones beyond acceptable as `statusDanger` @ 10% with 45° hatch (2 dp lines, 8 dp spacing, 20% alpha); acceptable band `statusCaution` @ 8%; ideal band `statusGood` @ 16%; band edges have a 1 dp line at 40% of the band color.
- Series: 2.5 dp line `lagoonBright` with outer glow (same color 10 dp blur @ 30%); points 5 dp with 1.5 dp `abyss1` stroke; point fill = status color at that time. Gaps > 3× the median interval are drawn dashed (4/4 dp).
- Overlay series: `anemone` 2 dp line, right axis labels in `anemone`.
- Scrub: vertical 1 dp line `foam` @ 40%; tooltip GlassCard with time, value+unit, status pill, kit brand.
- Zoom: horizontal pinch adjusts window (min 3 days), pan drags time; double-tap resets the range.
- Empty: dashed band outlines only + "Log Alk to see the trend" centered in `mist`.

### 9.2 Ratio chart (NO3:PO4)
Same component with a log-scale y (ratio 10–1000), ideal band 50–150.

### 9.3 Light schedule chart
Stacked area, 8 channel colors (UI_STYLE §1.4 `lightArc` colors: white `#DDEEFF`, blue `#3AA0FF`, royal `#2E5BFF`, red `#FF4D4D`, green `#4DFF8A`, violet `#6A3DFF`, UV `#8F6BFF`, moon `#2B3A6E`), x 0–24 h, draggable points (8 dp handles).

---

## 10. Live Tank overlay styling

The tank is the screen. In both orientations the GL view fills the whole screen and never resizes; every control floats over it and can be hidden from More → Hide buttons (a tap on the water, the "Show buttons" pill or Back brings them back; a tap on the water never hides them, §10.8).

- **Landscape:** a slim rail down the left edge (84 dp, inside the display cutout / safe area) of big candy bubbles, one per category except More. Tapping a bubble opens that category's card beside the rail: compact translucent glass at most 35 % of the width (260–380 dp). The bubbles size themselves to the height: 60 dp with labels when all fit, otherwise smaller (never under 48 dp) without labels, so a child sees every category on a short phone; only if even that does not fit does the rail scroll. The chevron bubble at the top tucks the rail away to itself. More is its own bubble in the bottom-right corner and opens its card from there. Mode, clock, the meter and Hermie sit in the top-right corner. The bottom bar is capped at 560 dp and centred, so on wide screens the rail runs down beside it.
- **Portrait:** all seven bubbles in a dock (a glass strip) just above the bottom bar, one slot each across the width (44–60 dp bubbles, smaller labels when crowded); the card opens above the dock, at most 46 % of the height. The dock stays out; tapping the mode that is already picked only brings a tucked rail or dock back, it never hides anything. Mode and clock on the first top row; the meter (folded to its face) and Hermie on the second.
- **Categories** (`LiveCategory`, `ui/live/LiveMenu.kt`), each with its candy colour: Supplies (orange: not a card, it swings the camera to the 3D supply shelf, §10.6, which holds every food, water change, dose, maintenance job, the glass cleaner, test kit, microscope, knocker and rock pile that used to be Feed / Water / Clean tiles), Add life (pink), Tank (yellow: warmer / cooler / saltier nudges, lamps, pumps and gear, medicine; its More holds every water, light and flow slider and the medicines), Trouble (purple), More (white: time speed, time-lapse, reset, microscope, sound, knock on the glass, hide buttons, "What can I do?" (first in its Tools row), Hermie and the meter on / off). Watch mode shows only Supplies and More.
- **Cards** are big tiles first (`LiveTile`, ≥ 76 dp tall, 2–5 columns by width), each a picture and one or two words. One-shot tiles (feed, dose, break the heater…) close the card so the picture in the tank can be watched; on/off tiles (pumps, skimmer, sound) stay open and show ON / OFF. A **More** pill at the bottom of Tank, Add life and Trouble folds out the grown-up controls: every slider (`ReefSlider`), how sliders change things (set / step / ramp / spike), extra doses, predation, test ideas, the selected animal's size, medicines, disease tests and the what-if cards. A card closes itself after 25 s without a touch (45 s with More open), on its ×, on Back, on a tap on empty water, or when its bubble is tapped again.
- Reset, taking a fish out and "make a fish sick" ask first in a `LiveDialog`; heater failure, adding a pest anemone and starting a treatment offer Undo on the snackbar. Ranges and zones live in one table, `ui/live/SandboxParams.kt`; friendly words in `ui/live/SandboxFriendly.kt`.
- `ReefSlider` (`ui/components/ReefSlider.kt`): label + "Your tank" reference line, value pill (status icon, tap to type an exact value), −/+ 40 dp steppers with hold-to-repeat, 48 dp custom track with safe/caution/danger segments under a water-gradient fill, a bubble thumb that swells on touch, a reference marker, haptic tick on zone change, reset to the reference.
- Loading: a `lagoonBright` thin progress arc inside a dark glass circle while assets load; text "Filling the tank…".

### 10.1 Live theme: "Sea-glass Arcade" (`ui/live/LiveTheme.kt`)

The owner's pick (concepts 1 + 5). All colours, shapes and sizes of the live screen live in `LiveTheme` / `LiveStyle`; restyling means editing that file only.

| Token | Value |
|---|---|
| Outline | navy `#1B1340`, 3 dp on panels and bubbles (2.5 dp on pills) |
| Candy tones | Feed `#FFB13D`, Water `#5FD7FF`, Light `#FFE14D`, Add life `#FF7AA8`, Clean `#8EE66B`, Trouble `#B18CFF`, More `#F2F6FF`; danger `#FF5C6C` |
| Text on candy | navy |
| Panel | lagoon-teal glass `#0C5866` @ 74 % (dark) / `#C9F4F7` @ 84 % (light), soft top highlight, navy outline, 28 dp radius |
| Shapes | tiles 22 dp, panels 28 dp, pills fully round |
| Sizes | rail 84 dp, rail bubble 60 dp, tile ≥ 76 dp, every touch target ≥ 48 dp |
| Font | Outfit SemiBold (bundled rounded display face): buttons 13 sp, titles 18 sp |

- **Buttons** are sea-glass bubbles (`drawSeaGlass`): a coloured 6 dp drop shadow under the body, a radial highlight, a white inner rim, the navy outline and a gloss oval. Pressing pushes the body down onto its shadow (the "chunky" press), squashes it and releases bubbles (§5.1). A selected rail bubble gets a white ring and lifts to 1.08.
- **Pills** (`LivePill`) for small actions (Got it, More, Done cleaning, Follow, mode toggle, Show buttons); **panels** (`LivePanel`, `Modifier.livePanel`) for cards, the fish card, follow bar, tips, clean-glass hint, the microscope legend and dialogs.
- The Add-life gallery keeps its species cards with a navy outline (pink when picked) and pink count badges; the microscope uses candy pills for scene, zoom and doses.

### 10.2 Action animations (`ui/live/ActionFx.kt`, `ActionArt.kt`, `LiveReactions.kt`)

Every command the live screen sends plays a short picture in the tank, drawn as a Compose overlay through the camera (`render/TankProjection.kt`: the GL thread publishes the camera matrices each frame, the overlay projects tank-cm anchors to pixels and sizes its tools by pixels-per-cm). The tools are drawn on a 48-unit box in the same two-tone style as `FoodArt` and `ReefGlyphs`: navy outline, candy fill, white highlight.

- The command is sent at the picture's **impact** moment (when the food reaches the water, the dropper's first drop lands, the bag opens), so the real sim particles carry on the motion: feeding drops its pellets at the sim's own surface feed point (41, 55.7, 20) within its 2.5 cm spread; nori clips on at the NORI clip; doses fall at the HOB outflow.
- Sliders are the exception: the change is sent at once (a drag sends many) and one keyed picture plays per drag.
- 0.9–3.6 s each, at most 5 at a time; tap the tank to skip (every pending command is sent at once and the pictures fade in 180 ms; the tap does not also hide the controls). Pending commands are also sent when the screen pauses or leaves. Reduce motion: a 700 ms still pose with a fade, the command at once.
- The overlay has no pointer input (touches go to the tank) and reads its frame clock only in its draw lambda, so the GL view never recomposes. While the microscope covers the tank, pictures play at fixed screen spots.

| Command | Where in the menu | Picture |
|---|---|---|
| `Feed` | Feed tiles (each `FoodKind`) | shaker tapped three times over the water (flake, pellets, floating pellets, wafer); frozen cube dropped in and thawing (mysis, brine, krill, prawn, cyclops); nori clip pressed onto the glass (nori); pipette squirt (coral food, phyto, pods). Particles land where the sim spawns them |
| `Dose` | Water → minerals tiles, More doses | dropper bottle tilts, three drops, a swirl in the dose colour (alk purple, Ca white, Mg green, waste brown, copper orange) |
| `WaterChange` | Water → Change water 25 % / 10 % | siphon hose into a bucket with dirty water flowing, the waterline band on the front glass drops, then a jug pours fresh water back with bubbles |
| `Heater` | Trouble → heater breaks / fix | the heater shakes and sparks, its coil goes cold, an unplugged badge pops up and a thermometer drops (break); the coil glows warm again with twinkles (fix) |
| `Heater(stuckOn)` (preset) | Trouble → heater stuck on | red pulse rings and heat shimmer around the glowing heater, a thermometer climbing |
| `Lights` | Light → on / off / normal day | the lamp bar over the tank lights LED by LED with a beam (on); flickers on-off-on and the tank dims (off); pulses with a sun-clock badge (normal day) |
| `PowerheadFail` | Trouble → pumps stop tonight | the pump with a moon badge and twinkles |
| `SetParam` / `RampParam` / `Schedule` | sliders, Warmer / Cooler / Saltier / Less salty, pumps and gear tiles | temperature: thermometer rising (red) or falling (blue); salinity: a salt scoop pours, or a fresh-water jug; light: lamp bar; flow and pumps: the pump's blades spin up with flow lines and bubbles, or slow down with sparks and a red ✕; minerals / waste: dropper; skimmer, sock, carbon, refugium, UV: the device badge at the sump with a power ring filling and bubbles (on) or emptying and fading (off); everything else a sparkle with the row's glyph |
| heat wave (`RampParam` temp) | Trouble → heat wave | thermometer climbing with heat shimmer |
| `runScenario(POWER_OUTAGE)` | Trouble → power cut | plug pulled, sparks, the whole tank dims with a flicker |
| `runScenario(OVERFEEDING)` | Trouble → too much food | the shaker emptying a big pile of pellets |
| `SetParams` (missed water changes) | Trouble → no water changes | red alert pulse with a crossed-out calendar |
| `RampParam(sal)` (salt crash) | Trouble → salt crash | fresh-water jug pouring, salt meter dropping |
| `SetParam(copper)` (copper spill) | Trouble → copper spill | dropper with an orange swirl |
| `AddAiptasia` / `RemoveAiptasia` | Trouble → pest anemone / Clean → zap pest | the pest anemone grows out of the rock, swaying, with a red pulse / a syringe pushes in, the pest shrinks away with twinkles |
| `AddPestControl` | Add life → peppermint shrimp / Berghia | a bag floats at the surface, opens and lets the animal out |
| `StartTreatment` / `StopTreatment` | Clean → More → medicine | medicine dropper (copper orange, others blue); hyposalinity a fresh-water jug; tank transfer the net; UV the device glows on; stop a sparkle |
| `Infect` | Trouble → a fish gets sick, More disease chips | red alert pulse with the disease name |
| `Maintain` | Clean → new sock, fresh carbon, trim algae | the dirty old sock / carbon slides out brown and the clean one slides in with sparkles; scissors snip the algae garden and green bits fly |
| `AddSpareShells` | Add life → spare shells | three shells tumble onto the sand with little sand puffs |
| `RearrangeRock` | Clean → move rocks | the rocks shake and puff sand |
| `SetAcclimation` | Clean → settle in | hearts rise from the middle of the tank |
| `AddOrganism` / `AddMany` | Add life gallery, "another one" chips | a fish bag with the species glyph floats, bobs, opens and releases the animal(s) |
| `RemoveOrganism` | Add life → More → take out | a net dips into the water and lifts out, with ripples and bubbles |
| `SetSize` | Add life → More → size | arrows pointing out (bigger) or in (smaller) with twinkles |
| `SetPredation` | Light → More → fish can eat each other | sparkle with a fish, red (on) or green (off) |
| `Micro` | Add life → pods / phyto, More chips, microscope | pipette squirt (pods beige, phyto green, rotifers white, medicines blue); amphipods / mysids in a bag; live rock the rock shuffle; flatworm frag an alert |
| `TapGlass` | More → knock glass, two-finger tap on the tank | knuckle rings on the front glass |
| `ResetToReality` (+ `Photoperiod`, resets) | More → reset tank | a whoosh sweeping across the tank |
| speed (holder call) | More → time tiles | fast-forward chevrons sweep across the top of the tank, more the faster |
| time-lapse (holder call) | More → time-lapse | a spinning clock, then the sheet opens |
| clean-glass mode | Clean → clean glass | sparkle with the Flipper; the Flipper itself then follows the finger |

`ScrapeGlass` has no extra picture: the Flipper the finger drags is the animation. `Shadow` and `Powerheads` are not sent by the live UI.

### 10.6 Supply shelf (`ui/live/SupplyShelf.kt`, `SupplyCarry.kt`, `render/SupplyShelf.kt`, `supply_shelf.glb`)

- A warm driftwood rack with sea-glass edging and a lit "Supplies" sign stands to the right of the tank in the same 3D scene (x 134–236 cm), three rows: **Feed** (orange, one prop per `FoodKind`, each its own real package: tub, jar, pouch, frozen card, canister, tray, nori clip, bottles), **Care** (green: bucket 25 %, jug 10 %, alk / Ca / Mg dosing bottles, filter sock, carbon bag, scissors, syringe, drip cup) and **Tools** (cyan: the Flipper, test kit, microscope, knocker, rock tray). Every prop has a printed label (ASSETS §5.1). It is drawn only while the camera is there or on its way.
- **Name tags**: every prop has a candy price tag under it on the board's front lip, in its row's tone with the navy outline (sandbox-only ones faded in Watch mode), anchored through the camera so it follows the drag. A tag is exactly as wide as its prop's slot, so tags never overlap; text is 12 sp (10 sp when the feed slots are narrower than 70 dp), two lines at most, using the short `SupplyItem.tag`. Each row has a header chip (glyph + Feed / Care / Tools) under the board above it, sticky at the left edge while the camera looks along. Tags and headers fade in after the glide (instantly with Reduce Motion). Tapping a tag picks its prop exactly like tapping the prop; tapping a header looks at that row.
- The **Supplies** bubble glides the camera over to the shelf in 1 s on a gentle arc (Reduce Motion: a cut). One-finger drags look along the rows and between them (soft ends, like the orbit). Portrait: the bottom panel has Feed / Care / Tools pills that glide to a row, and "Back to tank" (Back works too). Landscape: the same pills stand in a column on the right and the HUD's top rows step aside, so the short screen keeps the rows in view. Pinch, pan and two-finger taps do nothing there.
- First use: a yellow coach line "Tap a supply, then drag it to the tank" sits above the panel until a supply has landed in the tank once (`AppSettings.supplyHintSeen`).
- A tap picks a prop at once (no double-tap wait on the shelf, `GestureTracker.instantTaps`) by its box (padded 0.8 cm): it rises 2.6 cm, grows 10 % and glows; its name and one line pop up above it in a glass label tinted with the row tone. Sandbox-only props stand dimmed in Watch mode and only say "Pretend tank only". The test kit stays on the shelf and shows the water strip.
- Any other prop is then **carried**: the camera glides back, the prop floats in a bubble with its name chip under it, above the bottom bar, and the tank's front glass glows as the drop zone; while dragging, a chip at the glass's top edge says "Drop the krill in" (and "Let go: Krill" over the tank). Let go over the tank (or just tap the tank): food goes in at that spot (`Command.Feed` with the point, its picture at the same point), the knocker knocks there, the rest run the same actions as the old tiles (their §10.2 pictures and Hermie lines, the command sent when the picture reaches the water); the Flipper enters clean-glass mode. Letting go off the tank keeps the prop in the hand ("Missed the tank!"); "Back to shelf" (or Back) returns to the shelf and "Cancel" puts it away and stays at the tank.

### 10.7 In-tank gauges (`ui/live/TankGauges.kt`)

Small glass badges drawn through the camera so they sit on the tank and move with it: a stick-on **thermometer** on the front glass (red column 18–34 °C, the reading in a pill tinted cold / ok / hot), the **heater** badge (flame glows while it heats, shows its set point or Off), **salinity**, the **lamp** level and the **pumps** (blades spin with the flow). Hidden with the other controls, in the microscope, on the shelf and while cleaning. A badge whose anchor would land under the HUD's top rows, the dock / bottom bar or the landscape rail is nudged just inside them, so on small phones no gauge covers a button. In the sandbox a tap on heater, salinity, lamp or pumps opens a big dial (drag round the 270° ring or − / +, then Set): the heater dial sends `Command.Heater(setC)`, the others the same `SetParam` as their sliders, each with its §10.2 picture. In the real tank they only read. The heater set point is tracked by the screen (the snapshot carries no heater read-out); the glow is inferred from water below the set point.

### 10.3 Hermie and the happy meter (`ui/live/Hermie.kt`)

- **Hermie**, a hermit crab, sits in a small pill at the top. After every action he hops (or shakes when worried) and says one line about it for ~7 s (`LiveReactions`); his first line tells a new user to tap him for help, and tapping him gives the next tip (separate lists for Watch and Sandbox, `LiveTips`). The × hides him; More → Hermie brings him back. Moods: happy, excited, worried, surprised, sleepy, each with its own face, claw wave, sweat drop or sparkle. He also tells the first-time tips card and the "are you sure?" dialogs.
- **Tank happy %** (`tankHappiness`): 60 % average health + 40 % average calm of the living animals, minus 10 points per water reading in danger, 3 per caution and 6 per dead animal; display only, it never feeds back into the sim. The pill shows a face whose smile follows the value and a candy bar (green ≥ 70, yellow ≥ 40, red below) that springs to new values and bounces on changes of 3 points or more. Tap folds it to the face; × hides it (More brings it back).

### 10.8 Taps, Back and help for a first-time user (`ui/live/LiveTaps.kt`, `LiveTips.kt`)

Written for someone who has never seen the app (a partner, a child): nothing a stray tap does should leave the screen blank or stuck, and there is always a labelled way back.

- **A tap on empty water only undoes things** (`LiveTaps.emptyWater`): it brings hidden buttons back, else closes an open menu card, else puts down the picked fish. It never hides the buttons (only More → Hide buttons does), it does nothing while a fish is being followed (a missed fish no longer drops the follow) and nothing when it only skipped an action picture. A tap on a fish brings hidden buttons back so its card can show.
- **Back** undoes the top thing, in order: carried prop → shelf → clean-glass mode → microscope → hidden buttons → open card → fish card → tips card; follow mode's Back leaves the follow. Only then does it leave the Live tab.
- **Follow mode** exits through a labelled cyan "Back to tank" pill just above the fish panel (it used to be a small × in the top-right corner that sat on the clock pill), the gesture hint fades above it, and prev / next step through living animals only.
- **First-time tips**: one card for Watch ("Welcome to your live tank!": meet a fish, look around, Supplies, the Flipper, Sandbox, Back / Hermie) and one for Sandbox, each remembered in settings (`watchHintSeen`, `sandboxHintSeen`); More → **What can I do?** opens the current mode's card again at any time (it closes an open card and puts down the fish so the tips have room). Back or "Got it" dismisses it. The words live in `LiveTips` with a test that they name only controls that exist.
- The **Flipper** on the glass starts clean-glass mode with a tap in both modes (it used to do nothing in the sandbox).
- The **microscope** takes every touch while it is open, so nothing reaches the hidden tank under it (taps there used to pick fish, knock the glass or start cleaning).
- Action pictures and their captions clamp to the screen without ever throwing (`clampSafe`), so a tiny split-screen window cannot crash the draw pass.

---

## 11. Empty states and copy voice
Short, warm, specific. "Nothing logged yet — tap + to add your first test." "All clear. Your reef is in range." Avoid exclamation marks; no emoji in UI copy.
