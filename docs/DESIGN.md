# ReefLive — Product Design

Owner: Tee. Tank: 60 gal (assumed 48" x 13" x 24", 122 x 33 x 61 cm) mixed soft-coral reef, 2x AI Prime HD on goosenecks.
This document is the product spec. UI_STYLE.md holds the visual system, ARCHITECTURE.md the code, SIMULATION.md the sandbox, ASSETS.md the 3D assets, PLAN.md the build order.

Everything about Tee's tank (dimensions, livestock, equipment, light schedule) is **seed data** in `core/src/main/resources/seed/tee_tank.json`, never hard-coded in logic. The user can edit all of it in the app.

---

## 1. Product statement

ReefLive is two products in one shell:

1. **Tracker** — a fast, beautiful reef-keeping log: parameters, graphs, alerts, reminders, calculators, livestock, equipment, journal.
2. **Live Tank** — a real-time 3D recreation of Tee's tank with AI-driven fish, corals that react to water chemistry, driven by the tracker's latest measurements, playable as a sandbox.

The two are linked: logging a test updates the Live Tank's water; the Live Tank's "what-if" sandbox never writes back to the log.

Design principles (in priority order):
1. **Ten seconds to log.** The most common action (log a test) must take under 10 s from app open.
2. **Glanceable truth.** Dashboard shows status without reading numbers: color, trend arrow, band.
3. **Ocean, not spreadsheet.** Deep-sea dark theme, glass cards, slow caustic motion. Never looks like a form.
4. **Offline-first, local-only.** No accounts, no server. CSV/JSON export is the backup.
5. **Data-driven.** Species, parameters, thresholds, tank are data tables the user can override.

---

## 2. Feature list, prioritized

### v1 (must ship; milestones M1–M6 in PLAN.md)

| # | Feature | Notes |
|---|---|---|
| F1 | Parameter log (all parameters in §4 table) with timestamp, value, unit, test-kit brand, note | Multi-parameter "test session" entry |
| F2 | Quick Log sheet: tap FAB, tap parameter chips, type numbers with a large numeric pad, Save | Target <10 s |
| F3 | Dashboard with parameter tiles (last value, status color, sparkline, trend arrow, age) | |
| F4 | Big chart per parameter: 7/30/90/365 d, ideal band, acceptable band, danger zones, events overlay, pinch zoom, scrub tooltip | Hand-rolled Compose Canvas (see UI_STYLE §9) |
| F5 | Overlay chart (two parameters, e.g. Alk vs Ca; NO3:PO4 ratio) | |
| F6 | Status engine: in-range / caution / danger per parameter; rate-of-change alerts; staleness ("not tested in 9 days") | ARCHITECTURE §7 |
| F7 | Alerts list + Android notifications for danger and too-fast change | WorkManager, evaluated on each save + daily |
| F8 | Care: recurring tasks with reminders (water change, filter sock, carbon/GFO, skimmer cup, glass, pumps, ICP) | Seeded defaults |
| F9 | Calculators: dosing (alk/Ca/Mg, baking soda, soda ash, CaCl2, MgCl2·6H2O, MgSO4·7H2O, generic 2-part by product concentration), water change (dilution prediction), salt mix (grams of salt for target SG + volume), evaporation/salinity drift, free-ammonia (NH3) from TAN/pH/temp, unit converter | Formulas in 01_reef_care.md §2–3, implemented in `core` |
| F10 | Livestock inventory: species (from species DB), nickname, acquired date, size, cost, status (alive/rehomed/deceased), notes, photo, health log; future-size warnings (tang, eel) | |
| F11 | Equipment inventory with maintenance intervals and "last serviced" | |
| F12 | Light schedule editor: AI Prime HD channels, 8 channels x time points; drives Live Tank lighting | |
| F13 | Feeding log + water change log + dosing log (all become chart event markers) | |
| F14 | Journal: dated notes + photos, tagged to livestock/coral/event | |
| F15 | CSV export/import (measurements, events) and full JSON backup/restore | |
| F16 | Onboarding: pre-seeded with Tee's tank; "Is this your tank?" confirm/edit flow | |
| F17 | Live Tank 3D view: rockscape, sand, glass, water effects, all livestock, fish AI, day/night, touch camera, tap-to-follow with stats card | SIMULATION.md, ASSETS.md |
| F18 | Live Tank "Reality" mode driven by latest logged values; "Sandbox" mode with parameter sliders, actions (feed, water change, dose, lights, heater fail, add/remove fish), time speed 1x–1000x; reset to reality | |
| F19 | Light/dark themes, dynamic status colors accessible (WCAG AA against card background) | |

### v1.1 (after the first release)

| # | Feature |
|---|---|
| G1 | Cycle tracker for a new tank (ammonia → nitrite → nitrate checkpoints) |
| G2 | Quarantine / treatment tracker (hypo ramp schedule, copper days, tank-transfer calendar) |
| G3 | Disease tracker with symptom checklist and treatment outcomes |
| G4 | ICP import (CSV) with home-kit comparison |
| G5 | Salt-mix batch tracker (per-batch SG/alk/Ca/Mg) |
| G6 | Cost tracking and running totals |
| G7 | Multiple tanks |
| G8 | Home-screen widget (status strip) |
| G9 | Photo time-lapse per coral (growth compare slider) |

### Later

Cloud sync, controller integrations (Apex/Hydros), sharing a tank snapshot, AR view, voice logging.

---

## 3. Navigation

Bottom navigation bar, 5 destinations. The bar floats over a glass surface (UI_STYLE §6.1).

| Tab | Icon (Material Symbols Rounded) | Route | Root screen |
|---|---|---|---|
| Dashboard | `water_drop` | `dashboard` | DashboardScreen |
| Log | `monitoring` | `log` | ParametersScreen (list of parameters + history) |
| Live Tank | `waves` | `live` | LiveTankScreen (full-bleed GL) |
| Care | `task_alt` | `care` | CareScreen (tasks, reminders, calculators) |
| More | `grid_view` | `more` | MoreScreen (livestock, equipment, journal, settings, export) |

Global FAB "Log test" (`add`) is visible on Dashboard, Log and Care; opens QuickLogSheet (modal bottom sheet). On Live Tank the FAB is replaced by the sandbox controls.

Navigation graph (Navigation Compose, single Activity):

```
dashboard
log
log/{paramId}                 ParameterDetailScreen (big chart + history)
log/session/{sessionId}       TestSessionScreen (edit an entry)
live
live/fish/{organismId}        (overlay card, not a route; handled inside LiveTankScreen state)
care
care/task/{taskId}            TaskDetailScreen
care/calc/{calcId}            CalculatorScreen (dosing|waterchange|saltmix|evaporation|ammonia|convert)
more
more/livestock                LivestockListScreen
more/livestock/{id}           LivestockDetailScreen
more/livestock/add            LivestockEditScreen
more/equipment, more/equipment/{id}
more/journal, more/journal/{id}, more/journal/add
more/lights                   LightScheduleScreen
more/tank                     TankSettingsScreen (dimensions, volume, name)
more/settings                 SettingsScreen (units, theme, notifications, parameters & targets editor)
more/export                   ExportScreen
onboarding                    (first launch only)
```

Back behavior: system back pops; tabs retain their own back stacks (`saveState/restoreState`).

---

## 4. Parameter definitions (product view)

Full Kotlin table in ARCHITECTURE §6. Product-level summary for Tee's soft-coral reef (user-editable in Settings → Parameters):

| id | Name | Default unit | Alt units | Ideal | Acceptable | Danger (either side) | Max change/day | Test cadence |
|---|---|---|---|---|---|---|---|---|
| temp | Temperature | °F | °C | 76–78 | 74–80 | <72, >84 | 1 °F | daily (probe) |
| sal | Salinity | SG | ppt, mS/cm | 1.024–1.026 | 1.023–1.027 | <1.020, >1.029 | 0.001 SG | weekly |
| ph | pH | pH | — | 8.1–8.4 | 7.9–8.5 | <7.8, >8.6 | 0.2 | weekly |
| alk | Alkalinity | dKH | meq/L, ppm CaCO3 | 8–9 | 7–11 | <6, >12 | 1.0 dKH | weekly (2–3x/wk when dosing) |
| ca | Calcium | ppm | — | 400–440 | 380–460 | <350, >500 | 20 ppm | weekly |
| mg | Magnesium | ppm | — | 1250–1350 | 1200–1400 | <1100, >1500 | 50 ppm | monthly |
| nh3 | Ammonia (total) | ppm | — | 0 | 0–0.02 | >0.25 (free NH3 >0.02) | n/a | event |
| no2 | Nitrite | ppm | — | 0 | 0–0.05 | >0.25 | n/a | event |
| no3 | Nitrate | ppm | ppm NO3-N | 3–15 | 2–25 | <1, >25 | 5 ppm | weekly |
| po4 | Phosphate | ppm | ppb, ppm PO4-P | 0.02–0.06 | 0.01–0.10 | >0.15 | 0.02 ppm | weekly |
| k | Potassium | ppm | — | 380–420 | 360–440 | <340, >460 | 10 ppm | monthly |
| sr | Strontium | ppm | — | 8–9 | 7–11 | <6 | 0.5 ppm | quarterly |
| i | Iodine | ppm | — | 0.05–0.06 | 0.04–0.08 | >0.1 | n/a | quarterly |
| orp | ORP | mV | — | 325–375 | 300–400 | <250, >420 | n/a | weekly |
| do | Dissolved O2 | mg/L | % sat | 6.5–7.0 | >5.5 | <5 | n/a | optional |
| tds | RO/DI TDS | ppm | — | 0 | 0–5 | >10 | n/a | per batch |
| sio2 | Silicate | ppm | — | 0–0.5 | <1 | >1 | n/a | monthly |
| fe | Iron | ppm | — | 0.001–0.005 | 0–0.01 | >0.02 | n/a | yearly |

"Danger" is bidirectional only where listed. The status engine also raises **caution** when the value is within acceptable but outside ideal, and **danger** when the change since the previous reading exceeds max-change/day scaled by elapsed days.

Derived metrics (computed, shown as tiles when inputs exist):
- Free ammonia NH3 (from nh3 total, ph, temp) — Emerson 1975 (01_reef_care §2.4).
- NO3:PO4 mass ratio; flag <50 or >150.
- Alk consumption (dKH/day) and Ca consumption (ppm/day) from the last 2 readings between doses/water changes.
- Mg/Ca ratio (target 3.0–3.4).
- Salinity drift per day (SG/day).
- DO % saturation.

---

## 5. Screens

All screens use the glass-over-caustics background (UI_STYLE §4). Spacing grid 8 dp. Phone portrait is the primary layout; tablets get two-pane Log and More.

### 5.1 Onboarding (first launch)

Pages (horizontal pager, 4 pages, "Skip" top-right):
1. **Welcome** — full-bleed animated caustic background, app name in Display font, one line: "Track it. Watch it live." Button: Get started.
2. **Your tank** — Pre-filled card: "Tee's Reef · 60 gal · 48 x 13 x 24 in · soft coral". Fields: name, length/width/height (in or cm, unit toggle), display volume (auto-computed, editable), sump volume (default 0), coral type (FOWLR / Soft / LPS / SPS → picks default targets). Button: Looks right.
3. **Your livestock** — list of pre-seeded livestock cards with a checkbox each (2 clownfish, blue tang, pajama cardinal, cleaner wrasse, snowflake eel, banded coral shrimp, Falco hawkfish (assumed — badge "verify"), Ecsenius blenny (assumed — badge "verify"), corals: Kenya tree, GSP x2, zoanthids, clove polyps, green mushroom, red macroalgae). Uncheck to remove; "+ Add" to add from species DB. Button: Continue.
4. **Reminders & units** — units (°F/°C, SG/ppt, dKH/meq), notification permission request (Android 13+), default reminder time (08:00). Button: Dive in.

Completing onboarding sets `DataStore.onboarded = true` and inserts seed data. Re-run from Settings → "Reset to Tee's tank seed".

### 5.2 Dashboard

Layout top to bottom (LazyColumn, 16 dp side gutter):
1. **Header** — tank name (Title), date, "Last test 2 d ago", small weather-like status chip: "All clear" (teal) / "2 cautions" (amber) / "1 danger" (coral-red). Right: avatar button → More.
2. **Alerts strip** (only when present) — horizontal cards, each: icon, parameter, message ("Alk fell 1.4 dKH in 1 day"), tap → ParameterDetail.
3. **Hero tile** — the single most important parameter today (danger first, else caution with worst z-score, else alk). Big value, unit, status ring, 30-day sparkline with band, trend arrow and "Δ since last".
4. **Parameter grid** — 2 columns of ParameterTile (UI_STYLE §8.1), ordered by user (long-press drag). Default order: temp, sal, alk, ca, mg, ph, no3, po4, then the rest if ever logged. Tiles never logged show "—" and "Tap to log". Tap → ParameterDetail.
5. **Due today** — up to 3 task cards (UI_STYLE §8.4) with check-off button; "See all" → Care.
6. **Live Tank peek** — 16:9 card with a static render thumbnail of the tank (`live_thumb.webp` asset, replaced by the last real frame once the renderer has run) and overlay: "7 fish · lights 78% · sim 14:32". Tap → Live Tank.
7. **Recent events** — last 5 (feed, water change, dose, note) as a compact timeline.

Pull-to-refresh is not needed (local data); instead a subtle "caustic ripple" plays when data changes.

### 5.3 Quick Log sheet (F2)

Modal bottom sheet, 92% height, glass surface.
- Top: date-time pill (defaults now; tap to edit), kit-brand pill (last used; tap to change).
- **Chip row** (wrap): all parameters, favorites first (temp, sal, alk, ca, mg, ph, no3, po4). Tapping a chip adds an input row below and focuses it.
- Each **input row**: parameter name, last value ghosted ("last 8.4"), large text field (numeric keypad), unit toggle (tap cycles units; converts on save), status dot updating live as you type.
- Bottom: **Save** button (full width, pill). Secondary: "Add note" and "Add photo".
- After Save: sheet collapses with a bubble-rise animation; snackbar "Saved 4 values · Alk is in range".
- Repeated-parameter behavior: saving creates one `TestSession` with N `Measurement`s.
- Keyboard flow: Next moves to the next row; Done saves.

Timing budget: open (300 ms) → tap chip (1 s) → type (3 s) → save (300 ms). Under 10 s for 2–3 values.

### 5.4 Log (Parameters list)

- Segmented control: **Parameters** | **History**.
- Parameters: list rows per parameter: name, latest value + unit, status pill, 14-day sparkline, "tested 3 d ago". Tap → ParameterDetail. Overflow: reorder, hide.
- History: reverse-chronological test sessions grouped by day; each shows chips of values; swipe-to-delete with undo; tap → TestSessionScreen.

### 5.5 Parameter detail (F4)

1. Header: name, unit toggle, status pill, big current value, Δ vs previous with arrow, "N tests".
2. **Range selector**: 7D / 30D / 90D / 1Y / All (segmented).
3. **Big chart** (UI_STYLE §9): line with points, ideal band (teal translucent), acceptable band (lighter), danger zones (red hatched beyond), event markers (droplet=dose, wave=water change, fork=feed, note=journal) along the bottom, pinch-zoom horizontal, long-press scrub tooltip (value, time, kit).
4. **Stats row**: min / max / mean / consumption-per-day (alk, ca, mg only) / days in range %.
5. **Overlay** chip: "Compare with…" adds a second series (right axis), default pairs: alk↔ca, no3↔po4, temp↔do.
6. **Targets card**: ideal/acceptable/danger editable inline (writes `ParameterTargetOverride`).
7. **History list** below (same rows as Log → History filtered).

### 5.6 Live Tank

Full-bleed `GLSurfaceView` under a transparent Compose overlay.
- **Top bar (overlay)**: mode toggle **Reality | Sandbox**, sim clock ("Day 3 · 14:32"), speed chip (1x, 10x, 60x, 240x, 1000x; long-press for slider), settings gear (quality: Low/Med/High; show stats).
- **Bottom sheet (peek 72 dp)** — "Tank status": 6 mini tiles (temp, sal, alk, no3, po4, nh3 free) from the sim state; expand to the **Sandbox panel**:
  - Sliders for every simulated parameter (temp, sal, ph, alk, ca, mg, nh3, no2, no3, po4, do, light intensity %).
  - Action buttons: Feed (flake / frozen / nori), Water change (10/20/30%), Dose (alk/ca/mg, amount), Lights on/off, Heater fail / fix, Powerheads on/off, Add fish (species picker), Remove fish, Tap glass (also by double-tapping the glass), Reset to reality.
  - Reality mode disables sliders and actions except Feed/Tap glass (cosmetic), and shows "Driven by your log — last test 2 d ago".
- **Tap a fish**: camera eases to a follow cam; a **Fish card** slides from the right (phone: bottom): name, species, mood emoji-free status word ("Calm", "Hungry", "Stressed", "Sick: ich"), bars for hunger, stress, health, current behavior ("Patrolling", "Being cleaned"), temperament tags. Tap elsewhere or X to release.
- **Gestures**: one-finger drag orbit, two-finger pan, pinch zoom, double-tap = tap glass (fish react), long-press on sand/rock = drop food there (sandbox).
- **Time/light HUD**: a slim arc at the top showing the AI Prime schedule position (dawn/day/dusk/night).
- First-launch tip overlay with 3 gesture hints.

### 5.7 Care

Sections (LazyColumn):
1. **Today / Overdue** task cards (check circle, title, interval, last done, due in). Check → completion logged + optional note dialog ("Changed 6 gal").
2. **Upcoming (7 days)**.
3. **Calculators** grid (2 columns, glass tiles): Dosing, Water change, Salt mix, Evaporation, Ammonia, Convert.
4. **All tasks** list + "New task" (title, interval days or weekday set, reminder time, linked equipment, notes).

Seeded tasks (interval): Water change 10% (7 d), Filter sock/floss (3 d), Skimmer cup (7 d), Glass clean (7 d), Carbon swap (21 d), GFO check (30 d), Powerhead clean (60 d), Heater check (30 d), Light lens clean (30 d), Test Mg/K (30 d), ICP (180 d), Feed nori (1 d, 18:00).

### 5.8 Calculators (each a screen with inputs on top, result card, "Log this dose" button)

- **Dosing**: parameter (alk/ca/mg), current, target, system volume (prefilled), product (list with w fraction; "custom" with g/L or ppm per mL), → grams or mL, days to spread respecting max change/day, warning if the jump exceeds the cap.
- **Water change**: % or gallons, mix values (alk/ca/mg/no3/po4 defaults), → predicted post-change values for each parameter with before/after bars.
- **Salt mix**: target SG, volume, salt grams per gallon (product-specific, default 0.5 cup ≈ 136 g/gal for 1.025), → grams/cups.
- **Evaporation**: evaporation gal/day, current SG, days since top-off → predicted SG and daily drift.
- **Ammonia**: TAN, pH, temp → free NH3 ppm + verdict.
- **Convert**: all unit pairs from 01_reef_care §1.2.

### 5.9 More

Grid of destination tiles: Livestock (count), Equipment, Journal, Light schedule, Tank, Export/Backup, Settings, About. Below: "Seeded from your tank — edit anytime".

### 5.10 Livestock list/detail/edit

- List: cards (UI_STYLE §8.5) with species art (2D illustration from `drawable/species_*.webp`, same silhouette as 3D model), nickname, species, time in tank, status pill; sections Fish / Inverts / Corals / Algae.
- Detail: hero photo or art, facts (size now vs adult, lifespan, diet, temperament, tank-size warning banner for blue tang and snowflake eel: "Adult size 12 in — needs 120 gal+"), compatibility chips vs tank mates (from the interaction matrix), health log (symptom, treatment, date), journal entries tagged, "View in Live Tank" (jumps and follows).
- Edit: species picker (search species DB), nickname, acquired date, size cm, price, source, status, notes, photo.

### 5.11 Equipment

List + detail: type (light, powerhead, heater, filter, skimmer, ATO, controller, other), brand/model, installed date, service interval (creates/links a task), notes. Seed: 2x AI Prime HD (gooseneck), HOB filter/skimmer, powerhead left, powerhead right, heater, magnetic cleaner, feeding ring.

### 5.12 Light schedule

Timeline editor 00:00–24:00; 8 channels (Cool White, Blue, Royal Blue, Photo Red, Green, Violet, UV, Moon) as stacked curves; drag points; presets: "AI Soft Coral", "Blue heavy", "Acclimation −30%". Shows resulting swatch strip (color through the day) which the Live Tank uses verbatim.

### 5.13 Journal

Chronological cards with photo thumbnails, tags (livestock, coral, event type). Add: text, photos (camera or gallery, stored in app files, EXIF stripped), tags. Entries also appear as chart markers.

### 5.14 Settings

Units; theme (System/Dark/Light); notification time; parameters editor (targets per parameter, hide/show, favorites); reset seed; danger thresholds; haptics on/off; reduce motion; data: export CSV, backup JSON, restore, clear.

---

## 6. Key user flows

### 6.1 Log a test in under 10 seconds
Open app → Dashboard → FAB → chips "Alk", "Ca" → type 8.6, Next, 420 → Save. Snackbar confirms. Tiles animate to new values; alert evaluation runs synchronously on save (fast, in-memory for the last 30 d) and posts a notification only if a new danger appeared.

### 6.2 Quick-add multiple parameters
Same sheet; "Weekly set" chip adds the 6 weekly parameters at once in order; Next advances; empty rows are discarded on save.

### 6.3 Read a trend
Dashboard tile arrow: ↑ rising, ↓ falling, → stable; computed on the last 3 points via least-squares slope normalized by max-change/day: |slope| < 0.25×cap → stable. Tile color is status. Tap → detail chart with bands.

### 6.4 Get alerted
On save and daily at 08:00 (WorkManager periodic): evaluate StatusEngine over all parameters; create `Alert` rows (deduped by parameter + kind + day); notify channel "Reef alerts" (high importance for danger, default for caution/staleness). Tapping a notification deep-links to ParameterDetail.

### 6.5 Maintenance reminder
Task due → notification at reminder time (WorkManager OneTime scheduled at completion for the next due date) → notification action "Done" completes it without opening the app.

### 6.6 Dose from a calculator
Calculator → "Log this dose" creates `DoseEvent` (parameter, product, grams, expected Δ) → shows as a droplet marker on charts; the simulation applies it in Reality mode as a step at that time.

### 6.7 Export/import
Export → share sheet with `reeflive_measurements_YYYYMMDD.csv` (columns: timestamp_iso, parameter_id, value, unit, kit, session_id, note) and `reeflive_backup_YYYYMMDD.json` (all tables). Import CSV: preview, map columns if headers differ, dedupe by (timestamp, parameter).

### 6.8 Watch the tank
Live Tank opens in Reality mode; sim state initialized from latest values (missing ones → ideal mid-point). Fish behave per time-of-day from the light schedule and the device clock. Switch to Sandbox to experiment; "Reset" returns to reality.

---

## 7. Notifications

Channels: `reef_alerts_danger` (IMPORTANCE_HIGH, vibrate), `reef_alerts_caution` (DEFAULT), `reef_tasks` (DEFAULT), `reef_staleness` (LOW). Quiet hours 22:00–07:00 for non-danger. Android 13+ runtime permission asked in onboarding and again from Settings.

---

## 8. Accessibility and quality bar

- All status colors paired with icon + text; never color-only.
- Touch targets ≥ 48 dp; chart scrub is accessible via TalkBack as a list of points.
- Reduce motion toggle disables caustic animation and bubble transitions.
- Dynamic font scaling supported up to 1.3x without clipping (tiles wrap).
- Cold start to Dashboard < 1.5 s on a mid-range phone; Live Tank ≥ 30 fps at Medium quality on a 2021 mid-range (Snapdragon 7xx / Dimensity 800 class).

---

## 9. Seed data: Tee's tank (`seed/tee_tank.json`, excerpt)

```json
{
  "tank": {"name": "Tee's Reef", "lengthCm": 121.9, "widthCm": 33.0, "heightCm": 61.0,
           "displayGallons": 60, "sumpGallons": 0, "reefType": "SOFT", "sandDepthCm": 3.0},
  "livestock": [
    {"speciesId": "amphiprion_ocellaris", "nickname": "Orange", "variant": "classic", "sizeCm": 7.0, "role": "FEMALE"},
    {"speciesId": "amphiprion_ocellaris", "nickname": "Darwin", "variant": "darwin_black", "sizeCm": 5.5, "role": "MALE"},
    {"speciesId": "paracanthurus_hepatus", "nickname": "Blue", "sizeCm": 6.5, "lifeStage": "JUVENILE"},
    {"speciesId": "sphaeramia_nematoptera", "nickname": "PJ", "sizeCm": 5.0},
    {"speciesId": "labroides_dimidiatus", "nickname": "Cleaner", "sizeCm": 7.0},
    {"speciesId": "echidna_nebulosa", "nickname": "Snowflake", "sizeCm": 35.0},
    {"speciesId": "stenopus_hispidus", "nickname": "Bandit", "sizeCm": 5.0},
    {"speciesId": "cirrhitichthys_falco", "nickname": "Hawk", "sizeCm": 5.0, "verified": false},
    {"speciesId": "ecsenius_bicolor", "nickname": "Blenny", "sizeCm": 5.0, "verified": false},
    {"speciesId": "capnella_sp", "nickname": "Kenya tree", "placement": "kenya_main"},
    {"speciesId": "briareum_sp", "nickname": "Neon GSP", "placement": "gsp_upper_left"},
    {"speciesId": "briareum_sp", "nickname": "GSP mat", "placement": "gsp_lower_right"},
    {"speciesId": "zoanthus_sp", "nickname": "Zoas", "placement": "zoa_front_center"},
    {"speciesId": "clavularia_sp", "nickname": "Cloves", "placement": "clove_kenya_base"},
    {"speciesId": "discosoma_sp", "nickname": "Green shroom", "placement": "shroom_front_left"},
    {"speciesId": "gracilaria_sp", "nickname": "Red macro", "placement": "macro_back_left"},
    {"speciesId": "aiptasia_sp", "nickname": "Aiptasia?", "placement": "aiptasia_kenya_base", "verified": false}
  ],
  "equipment": [
    {"type": "LIGHT", "name": "AI Prime HD (left)", "serviceDays": 30},
    {"type": "LIGHT", "name": "AI Prime HD (right)", "serviceDays": 30},
    {"type": "FILTER", "name": "HOB filter / skimmer", "serviceDays": 7},
    {"type": "POWERHEAD", "name": "Powerhead left", "serviceDays": 60},
    {"type": "POWERHEAD", "name": "Powerhead right/back", "serviceDays": 60},
    {"type": "HEATER", "name": "Heater", "serviceDays": 30},
    {"type": "OTHER", "name": "Flipper magnet cleaner"},
    {"type": "OTHER", "name": "Feeding ring"}
  ],
  "lightSchedule": "ai_soft_coral_default"
}
```

Placements map to anchor names in the rockscape asset (ASSETS §3.4).
