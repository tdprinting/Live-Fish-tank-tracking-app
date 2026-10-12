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

Material Symbols Rounded (variable font `MaterialSymbolsRounded` bundled subset, or the `material-icons-extended` compose artifact if available on Maven Central; both acceptable, prefer the compose artifact `androidx.compose.material:material-icons-extended`). Weight 400, fill 1 for active tab, 0 inactive. Size 24 dp; 20 dp inside chips.

Custom parameter glyphs (`res/drawable/ic_param_*.xml`, 24 dp, 1.75 dp stroke, rounded caps): temp (thermometer), sal (droplet with wave), ph (flask), alk (layered bars), ca (hexagon "Ca"), mg ("Mg"), nh3/no2/no3 (molecule triangle with 1/2/3 dots), po4 ("P" in diamond), k, sr, i, orp (lightning), do (bubbles), tds (filter), sio2, fe.

Species art: `res/drawable/species_<id>.webp` 512x512, flat illustration with soft lighting, same palette as the 3D texture; generated from the Blender model with a toon render (ASSETS §9).

---

## 6. Components

### 6.1 Bottom navigation
Floating glass pill, 64 dp tall, 16 dp from bottom and sides, 5 items. Active item: filled icon in `lagoonBright`, label visible, 32x32 dp pill behind the icon in `lagoon` @ 20%. Inactive: `mist`, label hidden on phones < 360 dp wide. Center item (Live Tank) has a 4 dp `coral` "live" dot when the simulation is running in the background (it is not; dot shows when Sandbox has unsaved changes).

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
| Live Tank feed button | ripple of 6 bubbles from the button; button scale 0.92→1 | `CONFIRM` |
| Tap glass (Live) | concentric ring 400 ms at tap point in the overlay | `CLOCK_TICK` |
| Reduce motion | all durations 0 except crossfades at 100 ms; caustics static | haptics unaffected |

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
- Width 300 dp (tablet) or full-width bottom sheet (phone, 180 dp). Glass at 88% opacity.
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
- Overlays are glass at 70% opacity, 14 dp radius, 12 dp padding, no caustic background (the 3D view is the background).
- Speed chip: pill with `fast_forward` icon; active speed in `lagoonBright`.
- Mode toggle: segmented pill; Sandbox selected shows `coral` text to signal "not real".
- Loading: a `lagoonBright` thin progress arc inside a dark glass circle while assets load; text "Filling the tank…".

---

## 11. Empty states and copy voice
Short, warm, specific. "Nothing logged yet — tap + to add your first test." "All clear. Your reef is in range." Avoid exclamation marks; no emoji in UI copy.
