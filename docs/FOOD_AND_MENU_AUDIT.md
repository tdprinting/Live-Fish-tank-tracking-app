# Food and live-menu audit

Owner requests: every food has its own model and behaviour in the tank; every menu button does something real
with visible in-tank feedback; menu actions are easy to follow; default feeding matches Tee's feeding clip
(docs/research/video/tee_tank_day.md, "Feeding", IMG_0097).

Where things live: food sprites and motion in `render/FoodVisuals.kt` and `assets/shaders/particles.{vert,frag}`;
3D action feedback in `render/TankBursts.kt` (pooled CPU particles, no extra draw calls); the per-action banner,
effect line, live readout and progress bar in `ui/live/ActionFeedback.kt`; the action → feedback table in
`ui/live/LiveReactions.kt`.

## Foods (in-tank look and motion)

Before: every food was the same round soft dot, only colour and size changed. Nori had no 3D model at all, and
nothing showed a frozen cube.

| Food | Sim behaviour (core, unchanged unless noted) | Before | After: in-tank look |
|---|---|---|---|
| Pellets (Tee's usual) | Changed to match the clip: ~60 tiny granules to a 1 g pinch (was 20), sink 1.5 cm/s (was 3). Dropped by hand at the back centre, in the return jet, and each granule starts with the local current's velocity so the return sweeps the cloud across. Grams per pinch, and all nutrient math, are unchanged. | Dark brown dot | Tiny pale lit sphere. A hand-pinch picture at the drop point, then a small splash. |
| Flakes | Float 45 s, then sink 0.6 cm/s | Tan dot | Thin, irregular, see-through flakes in mixed colours (orange, red, green, tan). They lie flat on the surface, then tumble and flutter as they sink. |
| Floating pellets | Float 5 min, then sink | Tan dot | Lighter tan spheres bobbing on the surface |
| Algae wafer | Sinks fast, lasts 3 h | Dark dot | Dark green disc with a rim and speckles. It tumbles as it sinks, then lies flat. |
| Mysis | Sinks 1.5 cm/s | Pale dot | A frosted frozen cube thaws over ~6 s and releases pale, curled, see-through shrimp with dark eyes |
| Brine shrimp | Sinks 0.5 cm/s | Orange dot | Thawing cube, then slender orange shrimp with forked tails and a limp wiggle |
| Krill | Sinks 2.5 cm/s | Red dot | Thawing cube, then curved, banded red-pink pieces with black eyes |
| Cyclops | Drifts | Red dot | Thawing cube, then tiny red teardrops in a faint red cloud |
| Prawn chunk | Sinks 4 cm/s | Pink dot | Blocky pink lump with pale flesh bands that tumbles as it sinks |
| Live pods | Drift | Pale dot | Tiny copepods with antennae that dart, glide and dart again |
| Phytoplankton | Drifts, does not settle | Green dot | Green haze puffs that spread and fade as the flow carries them |
| Coral food | Drifts | Pink dot | Pink-tan powder specks inside a spreading haze |
| Nori | A sheet on the clip that fish nibble (`noriRemaining`) | **Not drawn at all** | Dark green sheet on a clip on the left glass that sways with the flow. Grazing tears tiles off from the free edges first, and torn bits drift away. |

Fish targeting still uses the sim positions. The visual-only motion stays within 0.6 cm of the sim point and is
zero on a particle's first frame. Cost: food, clouds, bursts and pump jets share the existing particle buffer, now
12 floats per point in 5 point draws (was 3), and the burst pool is capped at 700 points.

## Live menu and sandbox controls

"Before" means what the tank itself showed. Every control already had a 2D overlay picture. "After" adds the 3D
in-tank feedback, a banner (action, effect, live value, progress bar) and a highlight ring on the spot where it
happens.

| Control | Sim effect | Before (in tank) | After (in tank / banner) |
|---|---|---|---|
| Feed: any food from the supply shelf (#47), dragged to where it goes in | `Feed(kind, typical dose)` where it is let go (the same sim path in Watch and sandbox) | Same dots for every food | Each food's own sprite (table above), plus a splash or plop. Banner says how this food behaves. |
| Long press on the tank | `Feed(PELLET)` at that point | **No-op: `onLongPress` was not wired on the Live screen** | Fixed: drops a pinch there, with a splash and a banner |
| Water → Change water 25 % / 10 % | `WaterChange` | Overlay only | Muck streams into the siphon, then fresh water swirls in from the return as bubbles. Banner shows nitrate live. Fine suspended muck also tracks the water's detritus load. |
| Warmer / Cooler | temp ±1 °C | Overlay only | Warm shimmer rising, or cool shimmer sinking, at the heater. Banner shows temperature. |
| Saltier / Less salty | sal ±0.002 | Overlay only | Salt grains falling and dissolving, or clear fresh-water streaks swirling. Banner shows salinity. |
| Shell builder / Calcium / Magnesium, More → dose chips | `Dose` | Overlay only | A coloured dye plume from the return that spreads with the flow. Banner shows the dosed value live ("dKH rising"). |
| Lights on / off / Normal day, Lamp goes out | `Lights` | Lights change | Lights change as before. Lights on adds warm glints under the lamp. Banner shows the light level. |
| Left / Right pump | `pump.PH-B/PH-A` | Particle drift only, and the overlay drew the right pump at x = 119 (wrong) | Always-on jet streaks from every running pump (new `SimSnapshot.pumps`). Toggling on gives a jet burst; toggling off gives a stall. The overlay spots now match the sim pumps. |
| Flow slider | `flow` | Particle drift | The jet streaks get faster and longer, or shorter. Banner shows flow. |
| Skimmer / Sock / Carbon / Algae garden / Germ zapper | `skimmer`, `sock`, `carbon`, `refugium`, `uv` | Overlay over the out-of-view sump only | Micro-bubble mist from the return (skimmer), muck pulled to the overflow (sock), a clarity sparkle (carbon), a green clarity sparkle (refugium), a violet glow at the return (UV). The banner says what each does. **Fixed:** these on/off rows (and pumps) followed the slider control mode, so in "Ramp" mode they took a day to switch and "1-hour spike" switched them back. On/off rows now always switch at once. |
| Add life → stock gallery / Another one | `AddMany` / `AddOrganism` | Animals appear | A gush of bubbles and bag water at the release point |
| Pods / More pods / Amphipods / Mysids | `Micro(DOSE_*)` | Overlay only | Tiny hoppers released at the surface that sink toward the rock |
| Phyto / Rotifers | `Micro(DOSE_PHYTO/ROTIFERS)` | Overlay only | A green or white haze dispersing in the flow |
| Peppermint shrimp / Berghia | `AddPestControl` | Overlay only | A bubble release at the surface |
| Spare shells | `AddSpareShells` | Overlay only | Shells sinking onto the sand with a sand puff |
| Live rock / Flatworm frag | `Micro` | Overlay only | A silt cloud at the rock |
| Clean glass | `ScrapeGlass` strokes | Film scraped off | Same, plus sparkles at the glass and a banner explaining the drag |
| New sock / Fresh carbon / Trim algae | `Maintain` | Overlay only | Muck pulled to the overflow, or a clarity sparkle. Banner shows nitrate for trimming. |
| Zap pest / Pest anemone | `Remove/AddAiptasia` | Pest model only | A white paste puff and flash, or a small silt puff |
| Move rocks | `RearrangeRock` | Overlay only | A big silt cloud and settling specks |
| Settle in | `SetAcclimation` | Overlay only | Green sparkles |
| Medicine Start / Stop | `Start/StopTreatment` | Overlay only | A medicine haze, copper dye, UV glow, fresh-water streaks (hyposalinity) or bubbles (tank transfer). Stop gives a clarity sparkle. Banner shows copper or salinity live where relevant. |
| Heater breaks / Fix / Stuck on | `Heater` | Overlay only | Sparks and cool shimmer, warm shimmer, or strong red-hot shimmer. Banner shows temperature. |
| Pumps stop tonight | `PowerheadFail` at 23:00 | Overlay only | Amber markers at both pumps. The banner says when, and the jets stop for real at 23:00. |
| Power cut | `POWER_OUTAGE` scenario | Lights out | Both pumps stall, the jets stop, the heater sparks. Banner shows oxygen. |
| Heat wave | temp +3 °C ramped over a day | Overlay only | Warm shimmer. The banner says "over the next day" and shows temperature live. |
| Salt crash / Copper spill / Too much food / No water changes | presets | Overlay only | Fresh-water swirl (salinity), a copper plume, a big splash (watching ammonia), or a brown muck haze and silt (watching nitrate) |
| A fish gets sick / More → diseases | `Infect` | Fish symptoms | Red sparkle at the centre, and the banner says to watch the fish cards |
| Fish can eat each other, test switches | `SetPredation`, `sc_*` | Overlay only | A tinted sparkle and a banner line |
| Knock glass, two-finger tap | `TapGlass` | Fish startle | Specks pushed off the pane at the tap point |
| Time speed (now one slider), Reset tank, Time-lapse | speed, `ResetToReality`, sheet | Four speed tiles; overlay, and the sim runs faster | One snapping slider (see below), a fast-forward badge over the tank, and faster drifting specks. Reset adds a clarity and sparkle wave. Time-lapse opens its sheet. |
| More → Look closer, Sound, Hide, Tips, Hermie, Happy meter, Tuck | UI only | Each toggles its own UI | No change needed: these do not change the tank, and their feedback is the UI they open or hide |

## Making actions easy to follow (simplified flow)

- **One banner** at the top of the tank for every action, in place of the caption under each picture. It names
  the action and its effect in plain words, for example "Dosing Shell builder · dKH rising: coral skeletons grow".
  It shows the live value being changed as a pulsing chip (for example "Alk 8.4 dKH"), runs a progress bar for the
  length of the action, and leaves on its own. It is a polite live region for TalkBack.
- **A highlight ring** pulses on the exact tank spot where the action happens (the projected sim point), so the eye
  knows where to look from any camera angle.
- **3D feedback in the water itself** (above) at the moment the picture "lands", when the sim command goes out.
- **Fewer steps:** long press on
  the tank feeds there, and on/off tiles act at once whatever slider mode is selected.

## Feeding clip (IMG_0097)

| Observation | Before | After |
|---|---|---|
| Drop point: hand pinch at the back middle, beside the return | `SURFACE_FEED` (41, 55, 20), front-left of centre | `Simulation.HAND_FEED` (68, 55, 4.5), just beside the HOB return (x = 72) (anchor `HAND_FEED` overrides it). The overlay feed spot moved with it. |
| 40-80 tiny pale ~1 mm granules per pinch | Pellet pinch = 20 dark 3 cm/s sinkers | 60 pale granules, 1.5 cm/s. `HandFeedTest` checks 40-80. |
| The return sweeps the cloud forward and sideways in ~2 s, then it sinks slowly | Food spawned at rest, then coupled to 70 % of the flow at 2/s | Food is dropped inside the HOB jet and spawns with the local current's velocity (the flow coupling itself is unchanged). In `HandFeedTest` the leading edge (90th percentile) reaches 13 cm in 2 s and the cloud sinks ~7 cm, i.e. ~1-2 cm/s plus jet push. A stronger fresh-food entrainment reached 18 cm but broke two existing AI acceptance tests, so it was dropped. |
| Clowns at the surface in ~2 s; tang mid-water; wrasses low | AI unchanged | Not retuned. The test checks that a clown comes within 20 cm of the surface within 4 s (it gets to ~10 cm). See "Left open". |
| A visible hand cue | Shaker bottle | New `FeedTool.PINCH` picture: thumb and finger rub twice and the granules drift off with the current |

## Time slider and Watch-mode options (Tee's request)

Tee: "make the time lapse a slider and add some options for when your just viewing the tank and not in sandbox
mode too." Everything here reuses existing systems; no water-chemistry or parameter controls were added to Watch.

**Time slider** (`ui/live/WatchOptions.kt`, `TimeSlider`): one slider snapping at 1×, 10×, 60×, 240×, 600× and
1000×. The stops are evenly spaced, so the scale is logarithmic. Above the slider: the speed ("60×"), the speed
in words ("an hour a minute") and the live tank clock ("Day 2 · 14:05"). It replaces the four speed tiles in
sandbox More → Time and is the first thing on the Watch card. While time runs fast, the **fast-forward badge**
sits above the dock: a sun or moon moves over its arc with the tank clock, a ring ticks faster at higher speeds,
and the speed and clock are shown. In the tank, drifting specks and muck also move faster (log of the speed, up
to 5.5×). In Watch mode, sliding past 1× forks a pretend copy (sandbox) so the real tank keeps real time. Hermie
and the banner say so, and sliding back to 1× returns to the real tank.

**Watch card** (`LiveCategory.VIEW`, shown first in Watch mode only):

| Option | What it does | Feedback |
|---|---|---|
| Lamps: Real clock / Day / Dusk / Night / Moonlight | Render-only light look (`render/ViewLight.kt`, read by `TankRenderer`). The sim's light schedule and photosynthesis do not change. | The tank relights at once. The tile stays lit, and the banner says it is only the look. |
| Torch | A warm beam over the tank that follows your finger (touches pass through to the tank) | The beam, with the rest of the tank dimmed a little. Banner: "Drag on the tank to shine it around". |
| Feed | Opens the supply shelf (#47), the same as the Supplies bubble; the tour stops | The camera swings to the shelf |
| Auto tour | The follow camera visits each living animal for 12 s in turn. A tap on the water stops it. | The camera moves, and a banner explains it |
| Cinematic | The existing "hide buttons" | Every control hides. Tap the water to bring them back. |
| Sound on / off | The existing tank-sound toggle | Tile state and sound |
| Follow a fish | Lists up to 12 living animals by name. A tap follows that one with the camera. | The camera follows, and the fish's card opens |
| Time slider | As above | Badge and clock |

Previews: `TimeSliderPreview` (slider and two badges) and `TorchPreview` in `WatchOptions.kt`.

## Left open

- Clown, tang and wrasse feeding heights and timing were not retuned. That is AI work (`ai/FishBrains.kt`) and is
  out of scope here.
- The water level does not drop during a water change in 3D. The water and glass draw path is being reworked in
  parallel, so the water change is shown with the siphon and refill bursts instead.
- The sump equipment is out of view, so its feedback is shown at the HOB return.
- The bacteria actions in the microscope cover the tank, so their feedback stays in that panel.
