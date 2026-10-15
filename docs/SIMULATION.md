# ReefLive — Simulation and Fish AI

All of this lives in `:core` (`com.reeflive.core.sim`, `.ai`, `.geom`, `.species`) and is unit-tested without Android. Numbers come from `research/01_reef_care.md`, `02_fish_species.md`, `03_corals_rock_light.md`; anything marked (tune) is a design constant to adjust by eye.

Units: cm, seconds (sim time), °C internally (UI converts), SG, ppm, dKH. World frame per ARCHITECTURE §8.3.

---

## 1. Simulation core

### 1.1 Entities
```kotlin
class Simulation(config: TankConfig, species: SpeciesDb, rock: RockData, rng: Rng) {
  val water: WaterSim; val organisms: MutableList<Organism>; val corals: MutableList<Coral>; val food: FoodSystem
  val flow: FlowField; val perception: Perception; val lights: LightSchedule
  var speed: Double = 1.0           // 1..1000 (sim seconds per wall second)
  fun enqueue(cmd: Command)
  fun stepWall(dtWall: Double)      // called by the sim thread at 20 Hz
  fun snapshot(): SimSnapshot
}
data class TankConfig(val lengthCm: Double, val widthCm: Double, val heightCm: Double, val waterLevelCm: Double, val sandTopCm: Double,
    val systemLiters: Double, val heaterSetC: Double = 25.5, val heaterWatts: Double = 200.0, val roomC: Double = 22.0,
    val evapLitersPerDay: Double = 3.0, val atoEnabled: Boolean = false, val powerheads: List<Powerhead>)
```

### 1.2 Time stepping (two rates)
- **Behavior/physics tick** `dtB = 1/20 s` sim time: steering, collision, animation phases, food particles. When `speed > 20`, the behavior tick runs at most 20 times per wall second with `dtB = speed/20` capped at 0.5 s per tick (fish still move smoothly but cover more ground; above speed 240 the behavior layer switches to **abstract mode**: fish are placed at their behavior's home anchors with idle animation, and only chemistry/health run).
- **Chemistry/health tick** `dtC = 60 s` sim time, accumulated: while `accum ≥ 60` run `WaterSim.step(60)`, `HealthModel.step(60)` for each organism, `CoralSim.step(60)`.
- Speeds offered: 1, 10, 60, 240, 1000. Sim clock `simTimeSec` starts at wall-clock time of day so lights match reality.

### 1.3 Commands (sandbox)
```kotlin
sealed class Command {
  data class SetParam(val paramId: String, val value: Double): Command()
  data class Feed(val kind: FoodKind, val grams: Double, val at: Vec3? = null): Command()   // any FoodKind (§4.6)
  data class WaterChange(val fraction: Double, val mix: WaterState = WaterState.FRESH_MIX): Command()
  data class Dose(val paramId: String, val delta: Double): Command()
  data class Lights(val on: Boolean?, val overrideIntensity: Double? = null): Command()
  data class Heater(val working: Boolean, val setC: Double? = null): Command()
  data class Powerheads(val on: Boolean): Command()
  data class AddOrganism(val speciesId: String, val sizeCm: Double, val nickname: String): Command()
  data class RemoveOrganism(val id: String): Command()
  data class TapGlass(val worldPoint: Vec3): Command()
  data class TouchTank(val point: Vec3, val surface: TouchSurface, val strength: Double = 1.0): Command()
  data class SetSpeed(val speed: Double): Command()
  data class ResetToReality(val water: WaterState, val livestock: List<LivestockSeed>): Command()
}
```
Reality mode initialization: `WaterState` from the latest logged value of each parameter (missing → ideal midpoint), plus `bacteria` capacity set to mature (`B_aob = B_nob = 1.0`). Sandbox edits are discarded on Reset.

---

## 2. Water chemistry model (`WaterSim`)

State (`WaterState`): `tempC, sg, ph, alk (dKH), ca, mg, nh3Total (TAN ppm), no2, no3, po4, doMgL, k, sr, i, volumeL (current), algaeHair (0..1), cyano (0..1), bacteriaAob (0..1), bacteriaNob (0..1), detritus (ppm-equivalent organics), lightPar (0..1 effective), flowOn`.

Bioload inputs per tick: fish mass, feeding, coral demand. All derivatives below are per hour; the step applies `x += dx/dt × dt/3600` with `dt = 60`.

### 2.1 Temperature
```
heaterPower = heaterOn && tempC < setC − 0.3 ? heaterWatts : 0        // thermostat hysteresis 0.3 °C, turns off at setC + 0.1
lightHeat   = 25 W × lightIntensity × 2 fixtures
pumpHeat    = flowOn ? 15 W : 0
lossW       = k_loss × (tempC − roomC),  k_loss = 9 W/°C (60 gal open-top glass; tune)
evapCoolW   = evapRate(L/h) × 2260 kJ/kg /3600 × 1000 → ~0.63 W per mL/h ⇒ 3 L/day ≈ 79 W
dT/dt (°C/h) = (heaterPower + lightHeat + pumpHeat − lossW − evapCoolW) × 3600 / (volumeL × 4186)
```
Heater fail (sandbox): `heaterOn=false` → the tank drifts to room temp with time constant `volumeL×4186/(k_loss×3600)` ≈ 29 h. "Heater stuck on" command: `heaterPower=heaterWatts` always → +0.6 °C/h.

### 2.2 Evaporation and salinity
```
evapLh = evapLitersPerDay/24 × (1 + 0.08 (tempC − 25)) × (flowOn ? 1 : 0.8)
volumeL −= evapLh × dt/3600  (ATO on: volumeL constant, salt mass constant → sg constant)
saltMass = (sg − 1) × volumeL  (conserved)  ⇒ sg = 1 + saltMass / volumeL
```
Top-off with RO: `volumeL = systemLiters`. Water change fraction f with mix M: every concentration `c = c(1−f) + M.c f`; temperature `t = t(1−f) + M.t f`; detritus `×(1−f)`; hair algae unchanged.

### 2.3 Nitrogen cycle (ppm as N-equivalents simplified to ppm of each species)
```
excretionTAN (ppm/h) = Σ_fish 0.0030 × massG^0.8 × metabolicMult(tempC) / volumeL × 1000 × 60    (tune so 9 fish ≈ 0.02 ppm TAN/h gross)
   massG = 0.02 × lengthCm^3 (fish), eel 0.01×L^3;   metabolicMult = 2^((tempC−25)/10)
feedTAN = feedGrams × 0.07 × 1000 / volumeL  released over 6 h after each feed (first-order, τ=2 h); uneaten fraction u (§5.5) adds u×feedGrams×0.10 to detritus
detritusTAN = detritus × 0.02 /h  (detritus decays into TAN; detritus −= same)
nitrification1 = kAOB × bacteriaAob × TAN/(TAN + 0.05) × tempFactor,  kAOB = 0.6 ppm/h       (saturating; mature bacteria clear 0.5 ppm in ~1 h → in practice TAN stays ~0)
nitrification2 = kNOB × bacteriaNob × NO2/(NO2 + 0.05) × tempFactor,  kNOB = 0.5 ppm/h
tempFactor = clamp(1 + 0.07 (tempC − 25), 0.3, 1.5);  pH < 7.6 halves it
dTAN/dt = excretion + feedTAN + detritusTAN − nitrification1
dNO2/dt = nitrification1 × 2.7 (mass NO2/NH3 ≈ 46/17) − nitrification2
dNO3/dt = nitrification2 × 1.35 (62/46) − export
export = coralUptake (0.004 ppm/h × coralBiomass) + algaeUptake (0.02 × algaeHair + 0.01 × macro) + skimmer (0.0015 × NO3 /h when flowOn)
bacteria growth: dB/dt = 0.004 × (substrate/(substrate+0.05)) × (1 − B) − 0.0005 × (B > substrate-supported? ) ; bacteria die 50% per day with no O2 (DO < 2) or temp > 35
```
Reality mode starts with `B=1`. A "new tank" sandbox preset starts `B=0.02` to reproduce the cycle in 01 §3.9 (ammonia peak day 5–10, nitrite peak day 14–20, zero by ~day 30).

### 2.4 Phosphate
```
dPO4/dt = feedGrams × 0.012 × 1000/volumeL (over 6 h) + detritus × 0.0008 − 0.0015 × PO4 × (gfoActive?1:0) − algaeUptake 0.0015 × algaeHair − coral 0.0003 × PO4
```
Water change dilutes. GFO is a sandbox toggle (`SetParam("gfo", 1.0)`).

### 2.5 Alkalinity, calcium, magnesium
```
coralDemandDkhPerDay = 0.15 (soft coral + coralline for this tank; configurable per coral: Kenya 0.02, GSP 0.03 each, zoa 0.02, clove 0.01, coralline 0.05, mushroom 0.005)
dAlk/dt = −demand/24 − precipitation;  dCa/dt = −demand/24 × 7.1 ppm per dKH ... (1 dKH ≈ 7.1 ppm Ca for CaCO3 stoichiometry: 17.9 ppm CaCO3 × 40/100)
precipitation = alk > 11 && ca > 460 ? 0.02 dKH/h : 0  (also lowers Ca 0.14 ppm/h);  mg < 1150 doubles precipitation
dMg/dt = −demand/24 × 0.1  (slow)
Dose(alk, Δ) is applied as a step limited to 1 dKH per 10 min (the rest queues) to mimic slow dosing; pH bumps +0.03 per dKH for soda ash products (ignored for baking soda).
```

### 2.6 pH
```
phEq(alk, co2) : base 8.15 at alk 8.5 and "ambient CO2 index" 1.0
pH = 8.15 + 0.045 × (alk − 8.5) − 0.35 × (co2Index − 1) 
co2Index evolves: dCO2/dt = +0.06 × respiration (fish mass + bacteria) − 0.10 × photosynthesis(lightIntensity × coralBiomass + algae) − 0.08 × (co2Index − 1) × gasExchange(flowOn ? 1 : 0.4)
→ produces a natural day/night swing of ~0.25 pH (day high ~8.35, night low ~8.1 at alk 8.5). Flow off: swing widens and the mean drops by ~0.15.
SetParam("ph") sets co2Index to match (inverse of the formula) so the value persists but relaxes.
```

### 2.7 Dissolved oxygen
```
doSat = Oxygen.saturation(tempC, ppt(sg))           // 6.6 mg/L at 25 °C, 35 ppt
dDO/dt = kLa × (doSat − DO) + photosynthesis × 0.35 − respiration × 0.4
kLa = flowOn ? 1.8 /h : 0.25 /h (surface agitation);  respiration = (Σ massG^0.8 × metabolicMult)/volumeL × 12 + bacteriaAob×0.05 + algae×0.02 (mg/L/h; tune so a stocked tank at night with flow holds DO ≈ 5.9–6.3 and without flow falls to ~3.5 in 8 h)
```

### 2.8 Algae and cyano (visuals and grazing)
```
dHair/dt = 0.0025 × light × g(PO4, NO3) × (1 − hair) − grazing − 0.0004 × hair
g = clamp((PO4 − 0.03)/0.12, 0, 1) × 0.6 + clamp((NO3 − 5)/20, 0, 1) × 0.4
grazing = Σ grazers when they are in GRAZE behavior (as built: per-animal removal rates in §9.3)
dCyano/dt = 0.002 × (flowOn ? 0.2 : 1.0) × clamp(detritus/5,0,1) × (1 − cyano) − 0.0008 × cyano
```
`algaeBloom` exposed to the renderer = `hair`; `cyano` tints sand patches (future; v1 exposes it but renderer ignores).

### 2.9 Free ammonia and derived
Every chem tick computes `nh3Free = Ammonia.freeNh3(TAN, pH, tempC)` and `ppt`, used by health.

### 2.10 Invariants (unit tests)
- With no feeding and B=1, TAN and NO2 stay < 0.01 ppm for 30 days; NO3 rises ≈ 2–5 ppm/week with 9 fish fed 1 g/day (matches hobby experience).
- Evaporation of 3 L/day without ATO raises SG by ≈ 0.0003/day at 227 L.
- Heater off at room 22 °C: temp reaches 23 °C within ~60 h.
- Daily pH swing between 0.15 and 0.35.

---

## 3. Organism health model (`HealthModel`)

### 3.1 State per organism
```kotlin
class Organism(val id, val species: SpeciesSheet, var lengthCm: Double) {
  var hunger = 0.3          // 0 full .. 1 starving
  var stress = 0.0          // 0..1 (fast)
  var chronic = 0.0         // 0..1 slow-integrated stress
  var immunity = 1.0        // 0..1
  var health = 1.0          // 0..1 ; 0 = dead
  var disease: Disease? ; var diseaseStage: Double  // 0..1
  var condition = 1.0       // body condition (starvation), 0.5 = emaciated
  var alive = true; var deadSince: Double?
  var mood: Mood            // CALM, ALERT, HUNGRY, STRESSED, SICK, SLEEPING, DEAD (derived)
  val symptoms: MutableSet<Symptom>
  // behavior/physics
  var pos: Vec3; var vel: Vec3; var heading: Vec3; var up: Vec3; var swimPhase; var amp; ...
}
```

### 3.2 Species sheet (`species/<id>.json`)
```json
{
  "id": "paracanthurus_hepatus", "kind": "FISH", "common": "Blue tang", "family": "Acanthuridae",
  "maxLengthCm": 31, "typicalAdultCm": 25, "growthCmPerYear": 5, "lifespanYears": 15,
  "depthRatio": 0.52, "massCoeff": 0.025,
  "locomotion": {"mode": "LABRIFORM", "cruiseBL": 1.5, "burstBL": 7, "tailHzCruise": 4, "turnRateDeg": 240, "waveLen": 0.9, "ampCruise": 0.04, "ampBurst": 0.09},
  "zone": {"yMin": 0.25, "yMax": 0.95, "openWater": 0.8, "coverAffinity": 0.3},
  "activity": "DIURNAL", "schooling": false, "territorialRadius": 0, "homeRadiusCm": 60,
  "temperament": {"aggression": 0.3, "boldness": 0.6, "curiosity": 0.7, "skittishness": 0.5},
  "diet": ["ALGAE", "NORI", "MYSIS", "FLAKE", "PELLET"], "feedsPerDay": 4, "grazer": true,
  "tolerance": {
    "temp":  {"optLo": 24, "optHi": 27, "tolLo": 23, "tolHi": 28, "lethalLo": 20, "lethalHi": 31},
    "sg":    {"optLo": 1.023, "optHi": 1.026, "tolLo": 1.021, "tolHi": 1.027, "lethalLo": 1.016, "lethalHi": 1.030},
    "ph":    {"optLo": 8.0, "optHi": 8.4, "tolLo": 7.9, "tolHi": 8.5, "lethalLo": 7.4, "lethalHi": 8.9},
    "nh3Free": {"optHi": 0.0, "tolHi": 0.01, "lethalHi": 0.05},
    "no2":   {"optHi": 0.0, "tolHi": 0.1, "lethalHi": 1.0},
    "no3":   {"optHi": 20, "tolHi": 30, "lethalHi": 80},
    "do":    {"optLo": 6.0, "tolLo": 5.0, "lethalLo": 2.5}
  },
  "diseaseSusceptibility": {"ICH": 1.0, "VELVET": 0.5, "HLLE": 0.6, "BACTERIAL": 0.4},
  "hardiness": 0.45, "starvationDays": 21,
  "size": {"lod0Tris": 4500}, "model": "models/tang.glb",
  "colors": {"primary": "#2B5FD9", "secondary": "#F4D01A"}, "iridescence": 0.35
}
```
One sheet per species listed in DESIGN §9. Tolerance values are copied from 02_fish_species §E tables (optimal/tolerable/lethal), converted to °C and free-NH3 where the table gave total ammonia (free = total × 0.08 at pH 8.2).

### 3.3 Tolerance curve → stress contribution
For each parameter p with value v and bounds (optLo, optHi, tolLo, tolHi, lethalLo, lethalHi) (missing side ⇒ not applicable):
```
s_p(v) = 0                                   if optLo ≤ v ≤ optHi
       = ((v − optHi)/(tolHi − optHi))^2 × 0.4     if optHi < v ≤ tolHi      (0..0.4)
       = 0.4 + 0.6 × ((v − tolHi)/(lethalHi − tolHi))^1.5   if tolHi < v < lethalHi (0.4..1)
       = 1.0 (+ lethal flag)                 if v ≥ lethalHi
(mirror for the low side)
S_env = 1 − Π_p (1 − s_p)                     (probabilistic OR of stressors)
```
Additional stress sources (added before clamp): `S_social` (being chased: +0.25 while fleeing, decays 0.1/min), `S_space` (tang: 0.05 if lengthCm > 10 in < 450 L; eel: 0.05 if lengthCm > 50), `S_startle` (+0.15 per glass tap, decays 0.2/min), `S_dark` (cleaner wrasse with no clients 0.05), `S_hunger = 0.3 × max(0, hunger − 0.6)`.

Stress dynamics (per minute): `stress += (S_target − stress) × (S_target > stress ? 0.08 : 0.02)` — rises fast, relaxes slow (half-life ≈ 35 min). Chronic: `chronic += (stress − chronic) × 0.0007` (half-life ≈ 16 h).

### 3.4 Immunity, disease, health
```
immunity' = +0.002 × (1 − immunity) × (1 − chronic) − 0.004 × chronic − 0.003 × max(0, hunger − 0.7)   per minute; clamp 0..1
infectionPressure(ICH) = 0.0008 × tempFactorIch × (1 + 3 × Σ_infected stage) per minute,  tempFactorIch = tempC > 28 ? 3 : tempC > 26.5 ? 1.5 : 1
onset: if no disease and rng < infectionPressure × susceptibility(ICH) × (1 − immunity)^2 × hardinessInv → disease = ICH, stage = 0.05
  (Reproduces 02 §2E: tang at 29–30 °C with immunity falling → spots within ~48 h; a calm tank at 25 °C with immunity ~0.9 almost never seeds ich.)
stage' (ICH) = +0.0012 × (1 − immunity) × tempFactorIch − 0.0006 × immunity   per minute   → visible spots at stage > 0.15 (~3–7 days), death stage 1.0 (~2–3 weeks untreated)
stage' (BACTERIAL, from nh3/no2 burns) = +0.002 × (s_nh3 + s_no2) − 0.0008 × immunity
HLLE (tang only): stage' = +0.00005 × (nutritionPoor ? 1 : 0) − 0.00002
health' = −0.0015 × stress^2 − 0.004 × stage(disease) − 0.003 × max(0, 0.5 − condition) − lethal ? 0.05 : 0  + 0.0008 × (1 − stress) × (health < 1)   per minute
death: health ≤ 0 → alive=false; body floats (density) or sinks (eel, shrimp, hawkfish, blenny sink) and decays: detritus += massG × 0.5 over 24 h; eel may eat the corpse (removes it). Corpse removed after 48 h sim.
condition' = −1/(starvationDays × 1440) × max(0, hunger − 0.5) × 2 + 0.0004 × (hunger < 0.4)
hunger' = +1/(feedsPerDay × 6 h) per minute × metabolicMult  (0.0028/min for 1 feed/day species), −0.35 per ration eaten
```
Cleaner wrasse special: `acceptsPrepared` (0.7 probability rolled at creation) — if false, only cleaning (parasites on others) feeds it: each successful clean −0.08 hunger, so it slowly starves in a clean tank (02 §4E) unless the sandbox adds pods ("Feed: pods" sets hunger −0.4 for the wrasse).

### 3.5 Symptom mapping (health → visuals and behavior)
| Condition | Symptom flags | Render uniforms | Behavior change |
|---|---|---|---|
| stress > 0.35 | COLOR_FADE | desat = (stress−0.35)/0.65 × 0.6 | boldness −50%, more HIDE |
| stress > 0.5 | CLAMPED_FINS | finClamp = (stress−0.5)×2 | cruise speed −30% |
| s_do > 0.3 or s_temp(high) > 0.4 | RAPID_GILLS | gillRate = 1.5 + 3×s, gill = 0.6 | — |
| DO < 4 mg/L | GASPING | gill = 1 | behavior forced to SURFACE_GASP (hover at y = waterLevel − 2 under the HOB outflow) |
| disease ICH stage > 0.15 | WHITE_SPOTS | ich = stage | FLASH (rubs on rock) every 3–8 min, visits cleaner more |
| disease ICH stage > 0.5 | LETHARGY, HIDING | amp × 0.6 | HIDE utility +0.4, feeding −50% |
| condition < 0.7 (wrasse) | THIN | thin = (0.7−condition)/0.3 | — |
| health < 0.3 | LETHARGY | amp × 0.5, desat +0.3 | rests near bottom / in cover |
| dead | DEAD | desat 0.8, eyeCloud 1, finClamp 1 | FLOAT_DEAD (buoyant species: rises to surface, belly-up, drifts with flow) or SINK_DEAD |
| corals (CoralSim) | RETRACT / BLEACH / BROWN / MELT | extension, bleach, brown, (melt = scale 0.9, extension 0) | — |

### 3.6 Coral model (`CoralSim`) per colony
State: `extension (0..1 target by light & flow & stress), bleach, brown, tissue (1..0), algaeCover`.
```
coralStress = 1 − Π(1 − s_p) over temp (opt 24.5–27, tol 23–29, lethal 20/31 for GSP/zoa/clove; Kenya tol 25–29, lethal 20/30.5), sg (opt 1.023–1.027), alk (opt 7–11, tol 6–12), nh3Free (tolHi 0.02), po4 high (optHi 0.15 → brown), light (opt 0.3–0.8 intensity; >0.9 for mushroom = bleach), flow (Kenya: flowOn & powerhead local speed > 25 cm/s → stress 0.3; GSP: flow off → retract)
extensionTarget = lightOpen(t) × (1 − 0.8 coralStress) × (flowOn ? 1 : 0.5)   where lightOpen follows 03 §B: opens 1–2 h after lights-on, closes 30 min before lights-off
extension += (target − extension) × dt/(20 min)
bleach' = +0.0004 × max(0, s_temp − 0.5) + 0.0003 × s_light − 0.00005 × (coralStress < 0.1)    (full bleach in ~2 days at 31 °C; recovery over months per 03 §C2)
brown' = +0.0002 × clamp((po4 − 0.1)/0.2,0,1) + 0.0001 × clamp((no3 − 25)/25,0,1) − 0.00003
tissue' = −0.0005 × (bleach > 0.8) − 0.002 × (s_nh3) − 0.0003 × algaeCover ; tissue < 0.2 → MELT (Kenya, mushroom)
algaeCover' = 0.001 × hair − 0.0005 × grazing
Kenya "pulse": branches sway; at night extension 0.5 (03 §B).
GSP growth (cosmetic): mat scale += 0.02/month when extension avg > 0.6 (Live Tank only, capped at 1.3×).
```

---

## 4. Fish AI

### 4.1 Architecture
Three layers per organism, evaluated every behavior tick:
1. **Perception** (shared): spatial hash of organisms (cell 15 cm), rock SDF, flow field, food list, light level, "observer" events (glass taps), cleaning-station registry, host/anchor registry.
2. **Utility brain** (`UtilityBrain`): a species-specific list of `Behavior`s, each with `score(ctx): Double` (0..1) and a small internal state machine (`enter/update/exit`). The best-scoring behavior wins with hysteresis: switch only if `best > current + 0.12` or current has `finished`. Scores combine drives: hunger, stress, time of day (`activityCurve`), distance to home, threats, social opportunities. Reasons: utility AI gives emergent, smooth, interruptible choices (a BT would need many edge cases for interrupts like "feed" or "flee").
3. **Steering** (`Steering`): the behavior outputs a `SteerIntent(target: Vec3?, desiredSpeedBL, urgency, faceDir?, hover: Boolean, postureHint)`; Steering converts it to acceleration using classic behaviors (seek/arrive/wander/flee/separate/cohere/align/avoidRock/avoidGlass/followFlow), clamps by species turn-rate and speed, integrates, and resolves collisions with the SDF.

### 4.2 Perception details
- `SpatialHash<Organism>` rebuilt each tick; `neighbors(pos, r)`.
- `Sdf` (`SdfGrid`): 1 cm grid of signed distance to rock (positive outside), baked by `tools/sdf/bake_sdf.py` → `assets/sim/rock_sdf.bin` (header: dims, origin, cell size; float16). `dist(p)` trilinear; `gradient(p)` central differences → normal. Sand plane y=3 and glass walls are analytic (min with box SDF).
- **Line of sight to rock / cover**: `coverScore(p)` = baked field in the same file (second channel, uint8): fraction of 26 rays from p (hemisphere + horizontal) that hit rock within 20 cm → "how enclosed" (0 open, 1 cave).
- **Anchors** (`assets/sim/anchors.json`, from ASSETS §3.4): named points with type: `CAVE_EEL`, `CAVE_SMALL`, `HOLE_BLENNY`, `PERCH` (hawkfish, 5 of them), `HOST_KENYA`, `HOST_GSP_RIGHT`, `STATION_CLEANER` (under the center arch), `SHRIMP_LEDGE` (underside of the right overhang), `SLEEP_TANG` (wedge crevice), `GRAZE_PATCH`×8 (hair algae spots), `SURFACE_FEED` (under the feeding ring), `HOB_OUTFLOW`.
- `FlowField.velocity(p)`: §5.6.
- Events: `GlassTap(point, t)`, `FoodDropped`, `LightsChanged`.

### 4.3 Steering primitives (all return acceleration cm/s²)
```
maxSpeed = cruiseBL × L (or burstBL × L when urgency > 0.7); maxAccel = maxSpeed × 3
seek(t) = normalize(t − p) × maxSpeed − v ; arrive(t, r=2L): scale desired speed by clamp(d/r)
wander: target point on a sphere (r = 1.5L) ahead, jittered 0.3 rad/s; biased toward zone center (yMin..yMax of the water column) with weight 0.3 and toward home with weight proportional to (d_home/homeRadius)^2
flee(threat): away, urgency 1
separate(r = 1.2 × (L_i + L_j)/2): Σ (p − p_j)/d² ; cohere/align only for schooling species (cardinal, weight 0.4/0.3 within 25 cm)
avoidRock: d = sdf(p + v×0.4 s (lookahead)); if d < clearance (0.6 L + 1 cm): a += gradient × (clearance − d) × 40 ; also hard-resolve after integration: if sdf(p) < 0.45 L, push p along gradient and reflect the normal velocity component ×0.2
avoidGlass: same against the box (walls at x=0, x=Lx, z=0, z=Lz, y=sandTop, y=waterLevel−1): fish keep ≥ 0.5 L from glass unless behavior is GLASS_CURIOUS
followFlow: v_rel = v − flow(p); drag a −= v_rel × 0.8 (fish drift with current when idle; hovering species hold station by adding +flow × 0.9 to the desired velocity)
Priority blending: a = clamp(avoidRock + avoidGlass) ⊕ flee ⊕ behavior ⊕ separate ⊕ wander (truncated sum: add in order until |a| reaches maxAccel)
Heading: heading slerps toward v (turn rate limit), roll 10° × lateral accel; pitch limited ±35° (eel ±60°).
```
Animation outputs from steering: `tailHz = tailHzCruise × clamp(|v|/(cruiseBL×L), 0.3, 2.2)`; `amp = lerp(ampCruise, ampBurst, speedFraction)` (× 0.5 when hovering with pectorals, pectoral uniform = 1 when |v| < 0.4 cruise); `swimPhase += tailHz × dt`.

### 4.4 Common behaviors (available to most fish)
| Behavior | score | state machine summary |
|---|---|---|
| `Idle/HoverHome` | 0.2 + 0.3×(1−hunger) + 0.3×homeDist/homeRadius | arrive at a jittered point within homeRadius; hover facing flow |
| `Wander/Patrol` | zone.openWater × 0.35 × activity(t) | wander with zone bias; pick waypoints every 4–10 s |
| `Feed` | if food present: 0.5 + 0.5×hunger × dietMatch × activity(t) (clowns/tang/hawkfish get +0.2 when food is near the surface) | target nearest reachable particle (SDF line check), arrive, "bite" within 1.2 cm → particle consumed, hunger −0.06 per flake-sized particle; after 2–3 min of no food, exit |
| `Graze` (grazers) | 0.3 + 0.5×hunger × algaeHair × daylight | pick GRAZE_PATCH weighted by hair density & distance; nose to rock, short rasping nudges every 1.5 s (head pitch ±10°); each rasp hunger −0.01, hair −0.0005 |
| `Flee` | threatNear ? 0.9 + urgency : 0 — threat = predator species within 2.5 L moving toward me, or glass tap within 25 cm, or sudden lights-on | burst away from threat toward nearest cover anchor (coverScore > 0.6 within 40 cm), stay 10–40 s (skittishness), stress +0.15 |
| `Hide/Rest` | 0.25×stress + 0.5×(sick) + 0.7×(night for diurnal) | go to species sleep anchor / nearest cover; speed 0.2 cruise; amp 0.3; fins half-clamped |
| `VisitCleaner` | stationActive ? 0.15 + 0.6×ich + 0.1×(tang,clown) : 0; cooldown 10 min | approach STATION_CLEANER to 1 L, pose (pitch head-up 25°, pectorals spread, nearly still, mouth open uniform) for 10–30 s while the wrasse is in CLEAN state, then leave; ICH stage −0.02 per visit |
| `GlassCurious` | tapRecent && curiosity > 0.5 && stress < 0.3 ? 0.4 : 0 (clowns, tang, wrasse) | approach the tap point to 4 cm, hover 5–15 s |
| `SurfaceGasp` | DO < 4 or gill > 0.9 → 0.95 | hover at surface near HOB_OUTFLOW |
| `Dead` | !alive → 1.0 | float/sink physics only |

Activity curve `activity(t)`: DIURNAL = smoothstep over light intensity (0.05 → 0.3); NOCTURNAL = 1 − that; CREPUSCULAR (cardinal) = peaks at light intensity 0.05–0.3 (dawn/dusk) with 0.5 at full day and 0.7 at night.

### 4.5 Species brains (`SpeciesBrains.kt`, selected by `species.brain`)

> As built, brains are resolved from `species.brain` or the genus of the sheet id (`BrainKind`); §9 lists every brain, including the invertebrates, and the constants retuned with the SimRunner.

**Ocellaris clownfish pair (`ClownBrain`)** — host = `HOST_KENYA` for both (Kenya tree) with `HOST_GSP_RIGHT` as a secondary (tank photos show them near both; the pair's `hostAnchor` is a livestock field, default Kenya).
- `HoverHost` (replaces Idle): stay within 1–3 BL of the host anchor; characteristic *waddle*: desired velocity gets a 1.3 Hz lateral sinusoid (±0.3 BL/s) and ±8° head-down pitch bob; frequently (every 6–15 s) a quick dart into the branches (target = host anchor + random offset 3 cm) and out.
- `NestleHost` (night): both at host anchor ± 4 cm, amp 0.15, slow fin fanning, no wander. Female closer to the center.
- `DefendHost`: score 0.6 if another fish (not partner) is within 1.5 L of the host anchor and `aggression` (female 0.6, male 0.3) → charge at the intruder to 0.5 L, nip (lunge 0.1 s), return; intruder gets `S_social` and a `Flee` impulse scaled by size ratio (the tang ignores clowns when > 1.8× their length).
- Pair rule: male keeps 0.5–2 L from the female, yields (moves off) when she approaches within 0.5 L; feeding: female eats first (male's `Feed` score ×0.7 while she is within 2 L of the food).
- Feeding: rise fast to the surface (burst), snatch.
- Glass: curious.
- Threat: eel within 3 L → flee to host.

**Blue tang (`TangBrain`)**
- `Patrol`: long sweeps across the open lane (front third of the tank, y 0.35–0.9): waypoints alternate left ↔ right ends (x 15 → 105 cm), occasionally rising; "surfing": when flow speed at p > 8 cm/s, hold station against it for 3–8 s with pectoral flapping.
- `Graze` weight ×1.5; nori strips (Feed with kind NORI) pin the tang to the clip location (`SURFACE_FEED` area at the left glass) for 2–5 min.
- `Startle`: any tap or sudden movement → dart into `SLEEP_TANG` crevice or behind the left pillar, 15–40 s; the tang is `skittishness` 0.5.
- Night: wedged in `SLEEP_TANG` (crevice anchor in the left back wall), lying tilted 30°, amp 0.1; pale color (desat 0.3 at night is normal and not a symptom).
- Sick (ICH): `Flash` behavior: dart to a rock face, roll 40° and scrape (0.5 s), repeat; `VisitCleaner` strongly.
- `Space` stress grows with size (sandbox "grow fish" slider shows the cramped behavior: more turning, shorter runs: waypoint spacing shrinks to 0.5 L when L > 15 cm).

**Pajama cardinal (`CardinalBrain`)**
- `HoverCover`: hover 5–15 cm from cover (coverScore 0.3–0.6), mid-water (y 0.3–0.6), just under the center arch or beside the left pillar; nearly motionless, pectoral sculling, tail idle (amp 0.1, pectoral 1). Since 2026-10-14 it hovers level: the drift target changes every 8–20 s with vertical offsets ×0.3, speed 0.25 cruise, and `SteerIntent.maxPitch` = 0.17 rad caps the body pitch (it was sitting at the 0.6 rad default clamp). It faces into the flow, or else away from the rock toward the front glass (research fish_pajama_cardinalfish, 2026-10-14).
- `NocturnalForage` and `CardinalFeed` take only open-water food (`openWaterFood`: not settled, above the sand, and not touching rock). It is a mid-water picker; before this it hung for hours over pellets lodged in rock it could not reach. It gives up on a particle after `Feed.STUCK_S` without progress.
- Crepuscular activity: at dawn/dusk `Wander` radius grows to 40 cm and it hunts drifting particles (`Feed` on mysis preferred, dietMatch flakes 0.5).
- Shy: observer tap → retreat behind the pillar for 30 s; never `GlassCurious`.
- Schooling hooks (cohere/align) active if more cardinals are added in the sandbox.
- Night: clusters at `CAVE_SMALL` entrance; threat: eel within 3 L at night → flee up and away from rock (not into it).

**Cleaner wrasse (`WrasseBrain`)**
- `RunStation`: owns `STATION_CLEANER` (under the center arch, between pillar and arch, y ≈ 20 cm). Loop: hover at the station facing out; *advertise dance* every 10–25 s: 2 s of exaggerated up-down body oscillation (pitch ±20° at 2 Hz, tail amp ×1.6) when a potential client is within 35 cm. When a client enters `VisitCleaner` pose, enter `Clean`: move along the client's body (sample 4 points along its spine over 8–20 s, 1 cm off the skin, head toward the body, small nibbles 1.5 Hz), then return. Client cooldown as above. Hunger −0.08 per clean if the client has `ich` else −0.02.
- `Surf`: between clients, zips along the rock face in the lower-mid zone with constant motion (wander jitter 0.6 rad/s, cruise 1.6 BL/s), dips into crevices.
- `Feed`: accepts prepared food only if `acceptsPrepared`.
- Night: `SleepCocoon` at `CAVE_SMALL` (or buried in sand near the left pillar base): motionless, a faint translucent "cocoon" shell mesh scaled 1.15× around the body with alpha 0.12.
- Threat: eel within 2 L → flee; hawkfish within 1 L near its perch → avoid (adds a repulsion field around PERCH anchors when the hawkfish is on them).

**Snowflake eel (`EelBrain`)**
- Den: `CAVE_EEL` (right of center, under the Kenya rock, head emerging at sand level — matches the photos). `HeadOut`: body spline anchored: tail/body points fixed inside the den path (a baked polyline `den_path` of 8 points in anchors.json), head 5–12 cm out, slow sway ±2 cm (0.2 Hz); **gill/mouth pumping**: `gill` uniform = 0.5 + 0.5 sin(2π × 0.4 t) continuously (mouth open/close is the same uniform on the jaw region mask).
- `Roam` (night, or hunger > 0.6): leaves the den, anguilliform travel along the sand/rock interface (zone y 0.0–0.35, coverAffinity 0.9), explores crevices (targets: CAVE_* and holes) and the sand perimeter; path is the spline (§4.8) so the body follows the head's trail. Speed 0.5 BL/s; burst 2.5 BL/s for strikes.
- `Hunt`: hunger > 0.7 → prey candidates: shrimp (weight 1.0), cardinal (0.5 at night), blenny (0.3, only if the blenny is outside its hole), wrasse (0.2, only at night in cover), clowns (0.1). If prey within 1.5 BL and within 30° of the head and not in a hole: strike (burst toward prey, 0.4 s); hit test capsule → prey removed (`SimEvent.Eaten`), hunger −0.6, 3-day satiety. Escape probability for prey = 0.5 + 0.4×preyBoldness... concretely `rng < 0.55` prey escapes (gets flee impulse). The shrimp on the `SHRIMP_LEDGE` is safe unless the eel's roam path passes within 6 cm during a hungry night (p = 0.3 per pass).
- Sandbox toggle "eel can eat tankmates" default ON in Sandbox, OFF in Reality (Reality mode never kills livestock from predation; only water chemistry outcomes are shown, so Tee's real inventory stays consistent).
- Feeding: Feed(MYSIS/PELLET) near the den → head-out lunge at sinking particles within 8 cm.
- Day with lights on: retreats fully (`HeadOut` depth 2 cm, or fully hidden 30% of the time).

**Falco hawkfish (`HawkBrain`)**
- `Perch`: sits on PERCH anchors (5: left pillar top, center pillar top, right arch tip, front Kenya rock lip, GSP mound top) on pectoral fins (posture: pectorals spread 1, pitch +10°, body resting on surface: pos = anchor + normal×0.3 L, up = anchor normal); motionless 20–120 s with occasional head turns (yaw ±25°, 0.5 s) toward passing fish.
- `Hop`: picks another perch (weighted by distance and by proximity to drifting food/fish), short arcing burst (speed 3 BL/s, no sustained swimming), lands and settles (0.3 s squat).
- `Pounce`: food particle or small prey (cardinal if < 0.5 × hawk length, shrimp if < 0.6; only sandbox) within 15 cm and in view → burst strike then return to perch. Semi-aggressive: chases the wrasse or blenny if they come within 1 L of its perch (short charge, 1 s).
- Night: on the lowest perch under an overhang, amp 0.
- Startle: drops behind the perch rock (coverScore target 0.6) for 20 s.

**Tailspot blenny, Ecsenius stigmatura (`PeekerBlennyBrain`)** — §9.2 lists the lawnmower blenny, dottyback and invertebrate brains added with the livestock update.
- Home hole: `HOLE_BLENNY` (left pillar mid-height hole visible in blenny.jpg). `Peek`: body inside the hole along a 6 cm baked path, head out 1–2 cm, eye swivel (eye uniform yaw ±20°, 1 Hz random); duration 20–90 s.
- `Dash`: pops out to a GRAZE_PATCH within 30 cm (burst, 0.6 s), `Graze` for 10–40 s (perched on the rock on pectorals/pelvics, rasping nudges), dashes back to the hole on any threat (hawkfish within 1.5 L, eel within 3 L, tap) or after grazing.
- `Perch` on rock near the hole with the tail curled (posture spline: tail bends 40° to one side).
- Night: fully in the hole.

**Banded coral shrimp (`ShrimpBrain`)**
- Home `SHRIMP_LEDGE` (underside of the right overhang near the Kenya base; b_02.jpg shows it on a rock face). `Hang`: inverted on the rock underside (up = −surface normal), antennae waving: antenna bones are two curve meshes animated by `uAntennaPhase` (slow 0.3 Hz sweep ±25°, independent phases), claws held raised; small leg shuffles.
- `Scavenge` (night or food within 20 cm): walks along the rock (surface-constrained movement: position projected to SDF = 0.4 cm, up = gradient) at 0.15 BL/s to food particles that settled on rock/sand within 30 cm; picks and eats (hunger −0.1).
- `Clean`: if a fish poses within 4 cm of its ledge (eel head out nearby counts) → antennae tap toward it, claws pick (cosmetic, hunger −0.03).
- Molt: every 3–8 weeks sim time, hides fully for 36 h (not rendered), then re-emerges; a translucent molt shell object appears on the sand for 24 h.
- Threat: eel within 12 cm → tail-flip escape (burst 40 cm/s for 0.5 s, backwards), then hide deeper in the ledge crevice.

### 4.6 Feeding system (`FoodSystem`)
- `Feed(kind, grams, at?)`: spawns particles at `at` or at `SURFACE_FEED` (under the feeding ring at the left-front, x = 20, z = 25, y = waterLevel). Count = grams / `gramsPerParticle` (table below); nori = one 8×10 cm sheet at the clip anchor (`NORI_CLIP` on the left glass at the front corner, y 27, z 40: clear of the left rock so grazers reach its open side) simulated as a cloth (`NoriCloth`, below).
- Particle physics: `v' = (flow(p) − v) × 2.0 + g_sink`; floating kinds hold `sink = 0` at the surface for `floatSeconds`, then sink at `sinkCmS`. Collide with SDF → settles on rock (`settled=true`, available to shrimp/blenny/eel); on sand → settles; after `lifeSeconds` uneaten → `detritus += massPerParticle`, particle removed. Max 400 particles. Suspended kinds (pods, phyto, coral food, cyclops) barely sink and ride the flow.
- Fish bite radius 1.2 cm; competition: a particle targeted by two fish goes to the first to reach it; clowns and tang get burst speed when hunger > 0.4.
- "Frenzy" score: all diet-matching fish with hunger > 0.2 get `Feed` ≥ 0.6 immediately on spawn (perception event), producing the rush.
- Coral feeding (`Simulation.coralCatch`, 1 Hz): a colony with tissue ≥ 0.1 and polyps ≥ 30 % extended catches one `coralCatchable` particle (coral food, cyclops, phyto, pods) within `2 cm + its extent` (3–12 cm) per second; each catch adds 0.08 to `Coral.fed` (capped at 1, decays with a 2-day half-time) and `CoralGrowth.vigor` gains up to +15 % (`FED_BOOST`). Abstract (fast-forward) feeding of a catchable kind bumps every colony by 0.24. Magnitudes are design estimates: target feeding of LPS and zooplankton capture by SPS is well documented (Houlbrèque & Ferrier-Pagès 2009, *Biol. Rev.* 84:1–17, "Heterotrophy in tropical scleractinian corals"), but a per-catch growth number is not.

**Food table** (`FoodKind`, ordinal order; the renderer indexes colours and sizes by ordinal, so new kinds are appended):

| Kind | g / particle | sink cm/s | float s | nutrient factor | basis |
|---|---|---|---|---|---|
| FLAKE | 1/40 | 0.6 | 45 | 1.0 | dry food, ~8 % moisture; flakes float then sink (hobby observation) |
| MYSIS | 1/25 | 1.5 | 0 | 0.3 | frozen mysis is ~88–92 % moisture (Hikari / San Francisco Bay Brand guaranteed analyses), so ~1/3 the dry nutrient of flake |
| PELLET | 1/20 | 3.0 | 0 | 1.0 | sinking pellet, ~1–1.5 mm (Hikari Marine-S, New Life Spectrum 1 mm) |
| NORI | sheet | 0 | ∞ (clipped) | 0.3 | dried seaweed, low protein per gram eaten; sheet nibbled, not particles |
| PODS | 1/400 | 0.05 | 0 | 0.2 | live copepods 0.5–2 mm swim; mass per animal ~2–3 mg |
| PRAWN | 1/4 | 2.5 | 0 | 0.35 | raw prawn ~78 % moisture (USDA FoodData Central, shrimp raw) |
| PLANKTON | 1/300 | 0.0 | 0 | 0.2 | live phytoplankton stays in suspension |
| BRINE | 1/60 | 0.5 | 0 | 0.2 | adult *Artemia* 8–10 mm, ~90 % moisture and poor in HUFA unless enriched (FAO *Manual on the production and use of live food for aquaculture*, 1996, §4; Sorgeloos et al.) |
| KRILL | 1/8 | 2.5 | 0 | 0.35 | freeze-dried / frozen krill pieces, 15–25 mm whole; frozen ~80 % moisture (Hikari Krill guaranteed analysis) |
| FLOATING_PELLET | 1/20 | 1.5 | 300 | 1.0 | extruded floating pellets stay up for minutes before waterlogging (manufacturer "floating" claim; 300 s is a design estimate) |
| ALGAE_WAFER | 1/6 | 3.5 | 0 | 1.0 | Hikari Algae Wafers: sink fast, hold shape for hours (3 h life here), ≤10 % moisture |
| CORAL_FOOD | 1/300 | 0.05 | 0 | 1.0 | powdered coral food 150–200 µm (Polyp Lab Reef-Roids particle size as published) stays suspended; dry powder |
| CYCLOPS | 1/250 | 0.15 | 0 | 0.3 | Cyclop-eeze / frozen cyclops ~0.8 mm (Argent); frozen, so mostly water |

Sink rates, float times and `lifeSeconds` (20 min default, 30 min suspended kinds, 3 h wafer) are design estimates tuned for the simulation's 20-minute detritus window, not measurements. Diet wiring (`SimSpecies.eats` / `dietMatch`): BRINE, KRILL and CYCLOPS fall under the MEATY / ZOOPLANKTON diet tags, so clowns, hawkfish, cardinals, wrasses and filter-feeders take them without sheet changes; FLOATING_PELLET follows PELLET; ALGAE_WAFER needs an algae / spirulina / nori diet (tangs, blennies, snails); CORAL_FOOD counts as plankton for filter-feeders and is otherwise coral-only. Preferences: hawkfish favour krill ×1.3, tangs / lawnmower blennies / astrea snails algae wafers ×1.2, cardinals / peeker blennies / feather dusters cyclops ×1.2 (design estimates reflecting hobby feeding guides). Hermit crabs and brittle stars scavenge a prawn chunk at 0.8 (their sheets list no MEATY tag; Nassarius stay first at 1.5).

**Waste per food** (supply shelf, docs/research/shelf_audit.md): `WaterSim.addFeed(dryGrams, po4Factor, docFactor)` scales the phosphate and DOC each food leaches against the tuned reference foods (flake, pellets, floating pellets, mysis, pods = 1, so the autofeed schedule is unchanged): coral food PO4 ×1.6 / DOC ×1.4, krill ×1.5 / ×1.2, prawn ×1.4 / ×1.5, brine ×1.2 / ×1.3, cyclops ×1.2 / ×1.2, algae wafer ×0.8 / ×0.8, phyto ×0.6 / ×1.5, nori ×0.5 / ×0.6 (design estimates: meaty frozen foods and coral powders carry more P and shed P-rich thaw juice; seaweed is P-poor). `FoodKind.properties` turns the table into the shelf's one-line "how it moves · who eats it · waste" text.

### 4.6.1 Care props from the supply shelf (`SupplyChemistry.kt`)
- **Water changes** use the prop's salt: `WaterState.CORAL_PRO_MIX` (big, 25 %: alk 12, Ca 445, Mg 1370, K 395 at SG 1.025, 25 °C; Red Sea's published ranges scaled to 1.025) lifts alk ≈ 0.9 dKH, a "noticeable" step for the corals' alk-shock rule (`CoralSim.ALK_STEP`); `INSTANT_OCEAN_MIX` (small, 10 %: alk 10.5, Ca 400, Mg 1280) barely moves the minerals. `SupplyPreview.afterWaterChange` predicts the result for the banner (same mixing as `WaterSim.waterChange`).
- **Doses** stay `Command.Dose` (rate-limited); `DoseCheck.check` flags a dose for the UI: Ca × alk precipitating after it (`precipitationDkhPerHour`, Mg sets the threshold), more than 1 dKH / 25 ppm Ca / 100 ppm Mg in a sim day (`DoseCheck.Log`), or a level above 11 dKH / 470 / 1500 ppm.
- **New sock / fresh carbon** (`WaterSim.maintain`): fit one when none runs (`filterSock` / `carbon` = true), then reset its age. Carbon now also adsorbs iodine (`CARBON_IODINE` 0.004 /h × efficiency, τ ≈ 10 days fresh; 01 §3.5 trace stripping), which matters for crustacean moults below 0.02 ppm.
- **Trim** (`HARVEST_MACRO`): `SupplyPreview.canTrim` (macro or chaeto ≥ 0.05) gates the prop; `trimExportNo3` reports the nitrate locked in the third of the macro and half the chaeto cut (`MACRO_YIELD`).
- **Test kit** (`TestKit.read`): Salifert KH in 0.2 dKH steps ±1 step, API calcium in 20 ppm drops ±1 drop, API NO3 (0/5/10/20/40/80/160) and PO4 (0/0.25/0.5/1/2/5/10) colour cards read as the nearer swatch more often (probability = how far toward the next one), so a reef's 0.04 PO4 reads 0. Read errors are design estimates; resolutions are the kits' instructions.
- **Zap pest**: the UI counts Aiptasia in the snapshot first; none → no `RemoveAiptasia` command.

### 4.7 Lights and sleep
`LightSchedule.intensity(t)` and channels from the app's schedule (default 03 §F5 ramp scaled to a 10-h photoperiod: moon 00:00–08:00 at 2%, dawn 08:00–09:30, peak 10:30–18:00, dusk 18:00–20:00, moon after). Fish react to the derivative too: lights-on within the last 2 min → DIURNAL fish `Flee`-lite (stress +0.05, dart 1 s) then resume; lights-off → within 10 min diurnal fish go to sleep anchors; nocturnal activate. Tap-glass at night startles more (×1.5).

### 4.8 Body spline (shared by sim and renderer)
Each organism keeps a trail of its head positions: a ring buffer sampled every `BL/16` cm of travel (so the body occupies the last `BL` of trail). `spine[i]` for i=0..15 is the trail point at arclength `i × BL/15` behind the head, with the frame (tangent = finite difference, up = smoothed organism up, right = cross). When the fish turns in place or hovers, the trail is relaxed toward a straight line behind the heading at rate 4/s so the body straightens. Eel: BL up to 80 cm → the trail naturally snakes through crevices; in the den, the trail is replaced by the baked `den_path`. For a straight cruise the lateral wave in the vertex shader adds the propulsive undulation (fish: small amplitude at the head, large at the tail; eel: full-body wave with `waveLen 0.6 BL`, `amp 0.06 BL`).

### 4.9 Determinism
`Rng` is xorshift128+ seeded from `tankId.hashCode()`; all behavior randomness goes through it so unit tests can assert trajectories.

### 4.10 What is it thinking (`ai/Thoughts.kt`, `OrganismStats.thought`)
Owner request (Tee): "see what the fish are thinking or planning on doing in plain english". Every snapshot's stats carry a `Thought` per animal, built by `Thoughts.of` and cached by `ThoughtBook` (rebuilt every 5 ticks, ~4 Hz at the 20 Hz wall tick):

- **now**: the winning behaviour as a sentence with the animal's name and its target, e.g. "Nemo is heading home to the hairy mushrooms to rest.", "Doc is cleaning Blue the tang's gills.", "Stripes is backing into its burrow because Falco the hawkfish is close.". Every behaviour of every brain has its own wording (`Thoughts.line`, keyed by behaviour name, brain and the phase label); a fish idling with hunger > 0.65 "is hungry and watching the water for food". Targets come from `Caption` (focus id, else the likely nearby animal; cleaners only name fish); names read "Blue the tang", "Marlin" (same species), "Hermit 2" (the nickname already says it).
- **next**: the runner-up behaviour by its last utility score (`UtilityBrain.lastScore`, ≥ 0.2, sticky within 0.08 so it does not flip every tick; reactions such as flee or a tap are never offered), else a time-of-day plan ("find a cozy spot before the lights go out" in the last 45 min of light, "wake up when the lights come on", "come out when it gets dark" for nocturnal animals). "Next: graze some algae."
- **feeling** and **mood**: heat stage → too hot; sick → unwell; a fear plan → scared (and who); stress > 0.5 → stressed (with the worst water stress in words: "water is warm", "there is ammonia in the water"); asleep → sleepy; hunger > 0.65 → hungry; then feisty, proud, busy, curious, a bit stressed, peckish, happy, relaxed. "Feeling: a bit stressed (water is warm)."
- **why** (the top driver: hunger, fear, curiosity, territory, family, home, cleaning job, getting cleaned, rest, water, health, growing, routine), a first-person **bubble** ("Yum, mysis!", "Eek! Hide!", "Zzz…"), a **planKey** (behaviour + target: the UI holds a line until it changes) and an **interest** 0..3 for the thought bubbles.

It is read-only: it never draws from an rng, never writes to an organism or a brain (no `Counters` reads either: their roll mutates), and `Simulation.thoughtsEnabled = false` gives `Thought.NONE`. `ThoughtsTest` checks two same-seed runs are identical with it on and off, that every behaviour of every brain kind (default stock, moray, squid, both generic brains) has its own non-generic sentence with no raw names, and a dusk run reads as plain English.

---

## 5. Environment

### 5.1 Rock data (`RockData`)
Loaded from `assets/sim/rock_sdf.bin` + `anchors.json`. The sim module gets a `RockData` constructed by the app from the files; tests use an analytic `RockData.ofSpheres(...)` builder.

### 5.2 SDF file format
Header (little-endian): magic `RSDF`, uint32 nx ny nz, float32 originX/Y/Z, float32 cell; then nx·ny·nz float16 distances (cm); then nx·ny·nz uint8 cover scores. For the 121.9×40.6×45.7 tank at 1 cm: 122·41·46 = 230,092 cells → ~690 KB. As baked (ASSETS §3.5): nx = 122, ny = 41, nz = 46 (x, y-up, z), origin (0.5, 0.5, 0.5) = first cell centre, index = x + nx·(y + ny·z) (x fastest); cover byte 255 inside rock.

### 5.3 Flow field (`FlowField`)
Two powerheads (from the photos): **PH-A** at the back glass upper-center-right (x 77, y 32, z 1; resized tank), aiming toward the front-left-down (dir (−0.6, −0.35, 0.72)); **PH-B** on the left glass upper (x 1, y 28, z 13), aiming right and slightly down (dir (0.95, −0.2, 0.25)); **HOB return** nozzle at the back middle (x 73, y 37.4, z 2; piped from the HOB filter on the right, video T97), aiming forward and a little down (0, −0.4, 0.92) at low speed. Each jet: velocity at point p: `u = axis·(p − src)`, `r = |(p − src) − u axis|`, `v = V0 × (R0/(R0 + 0.08 u))² × exp(−(r/(R0 + 0.12 u))²)` for u > 0, with `V0 = 60 cm/s`, `R0 = 2.5 cm` (powerheads, ~800 gph class), HOB `V0 = 25`. Plus a global slow return circulation `v_g = 1.5 cm/s × (−sign(x − 61), 0, 0)` near the bottom (y < 0.36 × water level) and `+x` near the top, and rock shadowing: `v × clamp(sdf(p)/8, 0.15, 1)`. Powerheads off → only `v_g × 0.2`. Flow speed is sampled by corals (sway amplitude, retraction) and by fish (station holding, surfing spots: points where speed 8–20 cm/s).

**Gusts** (`FlowGust`, a flick of the finger): `addGust(pos, vel, t)` adds up to 4 short puffs on top of the jets, never baked into the cached grid. Each is a travelling Gaussian blob: centre `pos + dir × speed × τ(1 − e^(−age/τ))` with τ 0.8 s, radius 3.5 cm + 0.35 × distance travelled, speed `speed × e^(−age/τ)`, faded out over the last second of its 4 s life; launch speed ≤ 45 cm/s. `velocity()` and `velocityFast()` add them, so corals, sand, particles and the nori all feel the same puff; the snapshot carries them (`gusts`, stride 8) for the renderer's coral sway and flow motes.

**Nori cloth** (`NoriCloth`): the clipped sheet is a 5×7-node mass-spring grid (24 cells), hung from one pinned node at the clip so it can swing and turn in any current. Position-based dynamics at 1/60 s substeps (≤ 8 per call), 6 constraint iterations: stretch, shear 0.6, bend 0.08 (compression only), a strain limit of 1.4× rest from the pin outward, sinking 2 cm/s². Each node reads `FlowField.velocity` (so the pump schedule, wavemaker pulse, pump off and gusts all reach it; no outlet is hard-coded) through anisotropic flat-plate drag, strong across the sheet and weak along it, plus a travelling-wave turbulence term (30 % of the local speed, no rng) that makes it flutter. Pane and rock contact use the rock SDF. Material per cell: a bite takes from the nearest cell, preferring ragged edges; an edge cell bitten below 30 % tears off with weak neighbours, a cell weakened below 55 % tears when strained past 1.3, and any cells no longer connected to the pin break away. Torn pieces become NORI `FoodParticle`s (grams by area, slow sink) that drift in the flow and get eaten like any food. Material is conserved: `remaining + eaten + torn = total`. The snapshot carries the pose (`NoriPose`: nodes and per-cell material) and the renderer skins its tiles over it.

**Grazing the nori** (`NibbleNori`): grazers come at the sheet from downstream (flow at the sheet's centre, else straight out from the glass), face up-current and pick the most downstream cell whose station they can reach; tangs and others take a bite with a tug and a head shake, the lawnmower blenny rasps in quick bouts of small bites. A grazer that cannot get there in 8 s gives up for a minute.

### 5.4 Particulate
300 ambient particles advected by `flow` plus Brownian jitter (1 cm/s), respawned when out of the box.

### 5.5 Uneaten fraction
Computed per feed event: `u = remaining particles after 20 min / spawned`; influences detritus (§2.3).

### 5.6 Glass tap
`TapGlass(point)`: every fish within 30 cm gets `startle = skittishness × (1 − dist/30)`; `Flee` if startle > 0.35 else a flinch (0.3 s burst away 1 L); curious species may approach after 5 s.

`TouchTank(point, surface, strength)` is the gentle single-finger tap (`TankTouch`, UI_STYLE §10.8). Surfaces GLASS / SAND / ROCK / CORAL (a rock tap within 5 cm of a colony). Per animal `TankTouch.reaction` picks INVESTIGATE (food-conditioned bold fish, glass, ≤ 50 cm, day), AVOID (shy fish, ≤ 28 cm), PEEK (blennies), FREEZE (shrimp, sally crab), CHECK (diamond goby, nassarius on sand), DUCK (hermits, dusters; crustaceans, blennies and hawkfish on rock: the species' own startle at 0.4–0.7) or NONE; strength falls off linearly with distance. The shared `TouchReact` behaviour (score 0.75–0.9, no utility noise and no rng until a tap, so it changes no other sequence) plays it for a few seconds. Polyp corals (GSP, zoa, clove) within 12 cm of a rock / coral tap get `disturb` 0.6 (0.8 on the tapped colony). Habituation per animal: +0.22 per answered tap, decays with τ 20 min, ignored at ≥ 0.75; a feed clears it. Three taps within 4 s (× speed) and 10 cm escalate to `TapGlass`.

Surface SURFACE (a tap at the water's top): food-conditioned bold fish within 50 cm RISE to just under the surface for 4–9 s (not at night); shy fish close by AVOID; the surface gets a small ripple impulse.

`Finger(point, speedCmS, surface)` is a fingertip held on the glass (null = lifted; no news for 6 s counts as lifted). Each animal is considered once per hold: bold fish that would INVESTIGATE FOLLOW instead: they track the finger along the pane (offset 0.5 L + 2 cm, 10 cm + 0.5 L while it moves faster than 35 cm/s) and, once it has rested 1.5 s, close in and nip every 0.7–1.4 s (`touchNips`). Lifting ends following after a 2.5 s linger. A finger moving faster than 35 cm/s only makes shy fish and crustaceans close by react. Holds habituate like taps.

`WaterPuff(point, velocity)` is a flick: a `FlowGust` (§5.3), a sand puff where it passes within reach of the bed, polyp `disturb` 0.4 within 12 cm of its path, and shy fish and crustaceans in it flinch (AVOID / FREEZE / DUCK).

`TouchAnimal(id)` is a tap right on an animal: snails, crabs, stars and other walkers TUCK (stop, pull in for 5–15 s), hermits, dusters, brittle stars DUCK, shrimp FREEZE, fish NONE (the tap only picks them). Habituation as for taps.

**Submarine mode.** `SubmarineMode(on, visible, headlight)` launches a toy sub (`sim/Submarine.kt`, 4.6 cm, radius 1.4 cm, 12 cm/s) steered by `SteerSubmarine(SubControls)`. It moves on wall time (`stepWall`) and is pushed clear of the glass, surface, live sand (`SandBed.surfaceY`) and rock SDF; its pose is `SimSnapshot.submarine`. Every 0.25 s `ai.SubmarineSense` gives animals within 40 cm a `SubmarineRules.reaction`: STARTLE (closing > 7 cm/s within 9 + 10 × skittishness cm: the species flee via `onTap`), INVESTIGATE (curious clowns, tangs, wrasse, dottybacks; they follow it), POSTURE (clown within 14 cm of its host, dottyback at its cave: darts; hawkfish and eel watch), AVOID (cardinal, shy tangs, burrowers), HIDE (gobies, blennies, hermits, dusters, shrimp up close), FREEZE (shrimp, crab). `SubmarineReact` (noiseless, no rng while idle) plays the held ones. Loitering builds habituation over 200 s (ignored at 0.75, τ 20 min, a startle takes 0.2 back). Invisible: nothing is evaluated, so the run is identical to one without the sub (`SubmarineTest`). `SubmarineWatch(id, mode)` sets an autopilot for fish watching: the sim feeds it the animal's position and velocity each step, and the sub glides over (up to 10 cm/s while still more than 15 cm beyond its spot, then closing no faster than 4 cm/s, under the startle speed) to a polite distance (max(11, 1.5 L + 4) cm) a little above the fish, nose on it, matching its velocity. HOVER keeps a fixed bearing, CIRCLE drifts round it at 0.11 rad/s, ALONGSIDE holds beside its heading. The stick then works relative to the fish (sideways swings round it, forward/back goes closer or further, rise changes height); a dead or missing animal ends the watch. Reactions and habituation are unchanged.

---

## 6. Snapshot and events
`SimSnapshot` (ARCHITECTURE §8.8) is produced every behavior tick; `SimEvent`s: `Eaten(predator, prey)`, `Died(id, cause)`, `DiseaseOnset(id, disease)`, `Recovered(id)`, `Molted(id)`, `CoralBleaching(id)`, `AlgaeBloom`, `Gasping(id)`. The Live Tank UI shows events as toast lines ("Blue has white spots — ich. Lower temp, check stress.") and in Sandbox mode a log panel.

---

## 7. Tuning presets (`sim/presets.json`)
`reality_default`, `new_tank_cycle` (B=0.02, no fish), `heater_failure_summer` (room 30 °C), `overfeeding` (3× feed), `powerhead_failure`, `ich_outbreak` (tang stage 0.2). Exposed under Sandbox → "Scenarios".

---

## 8. Acceptance tests (core)
1. `WaterSimTest.nitrogenMassBalance`: Σ(N in TAN/NO2/NO3/detritus/exported) conserved within 1%.
2. `WaterSimTest.cycleTimeline`: new-tank preset → ammonia peak between day 4 and 12, nitrite peak between day 10 and 25, both < 0.1 ppm by day 45.
3. `HealthModelTest.tangIchAtHighTemp`: 30 °C for 72 h with immunity starting 0.8 → ich stage > 0.15 in ≤ 7 days with probability > 0.9 across 50 seeds.
4. `HealthModelTest.healthyTankNoDeaths`: ideal water for 90 days, feeding on schedule → all alive, health > 0.9 (wrasse with acceptsPrepared=true).
5. `HealthModelTest.wrasseStarves`: acceptsPrepared=false, no clients with ich → death between day 20 and 60.
6. `AiTest.clownsStayNearHost`: 10 min sim, 95% of samples within 3 BL of the host anchor.
7. `AiTest.noRockPenetration`: 10 min sim all species, `sdf(pos) > 0.3 L` at every tick.
8. `AiTest.eelStrikesShrimpWhenHungry`: hunger 0.9, night, shrimp 5 cm from the den path → Eaten event within 30 min in ≥ 50% of seeds.
9. `FlowFieldTest.jetDecay`: speed at 30 cm along PH-A axis between 8 and 20 cm/s.
10. `SplineTest.continuity`: spine points spacing BL/15 ± 10% during a 180° turn.


---

## 9. As built (Track C notes)

This section records what the `:core` implementation actually does where it differs from, or adds to, §1–§8. Code: `core/src/main/kotlin/com/reeflive/core/{geom,sim,ai}`; tests: `core/src/test/kotlin/com/reeflive/core/{geom,sim,ai}`; species fixtures used by the tests: `core/src/test/resources/sim-fixtures/species/*.json` (Track A owns the production sheets in `core/src/main/resources/species/`; `SpeciesCatalog.fallback` supplies archetype sheets for any id without one).

### 9.1 Livestock (coordinator update)
Fish: 2 ocellaris clowns (female + male), juvenile blue tang, pajama cardinal, cleaner wrasse, **engineer goby** (*Pholidichthys leucotaenia*, owner correction: not a snowflake eel, see §9.6), Falco hawkfish, **lawnmower blenny** (*Salarias fasciatus*), **tailspot blenny** (*Ecsenius stigmatura*), **indigo dottyback** (*Pseudochromis*). Inverts: banded coral shrimp, **skunk cleaner shrimp**, 3 hermit crabs, **black brittle star**, **brown feather duster**, 3 nassarius, 3 astrea, 2 asterina. Corals: Kenya tree, 2× GSP, zoas, clove, mushrooms, red macro, and **organ pipe coral** (*Tubipora musica*) at the Kenya base (replacing the aiptasia placeholder; `CoralKind.ORGAN_PIPE`). Seeds: `sim/Presets.kt` `DefaultStock`.

### 9.2 Brains (all utility AI, `ai/FishBrains.kt`, `ai/InvertBrains.kt`)
Common behaviors (`ai/CommonBehaviors.kt`): HoverHome, Wander, Feed (frenzy on a fresh feed), NibbleNori, Graze, Flee (flinch for startle ≤ 0.35, else 10–40 s × skittishness to cover), Sleep, VisitCleaner, GlassCurious, SurfaceGasp, Flash. Threats come from `Predation.threatRadius` and only alarm when the predator is closing (> 0.5 cm/s toward the animal) or within half the radius; invertebrates notice predators at 0.6× range at night.

| Brain | Behaviors |
|---|---|
| Clown | HoverHost (waddle 1.3 Hz, head bob, darts into the host every 6–15 s, male keeps 0.5–2 BL from the female), NestleHost (night), DefendHost, Feed (female first, +0.2 near the surface), GlassCurious, Flee to host, VisitCleaner |
| Tang | Patrol (front-lane sweeps, flow surfing 3–8 s), Graze ×1.5, NibbleNori, Flee to `SLEEP_TANG`, Sleep wedged and tilted 30° (pale), Flash, VisitCleaner |
| Cardinal | HoverCover (pectoral sculling), DuskHunt (light 0.03–0.35), Feed (mid-water only), Flee (up and away from rock when the eel is the threat), schooling hooks |
| Wrasse | RunStation (`STATION_CLEANER`, advertise dance), Clean (works along the client 8–20 s), Surf, Feed (only if `acceptsPrepared`), Cocoon (night, posture 4) |
| Eel | HeadOut (den, spline = `den_path`, gill/mouth pumping, lunges at food within 8 cm, hides 30 % of daytime), Roam (night / hungry), Hunt (strike within 14 cm and 30°, or on contact; strike ≈ 100 cm/s; 45 % kill per hit when predation is enabled, 15 s cooldown, 3-day satiety) |
| Hawk | PerchHop (kinematic perch on pectorals with head turns, arcing hops at 3 BL/s, lowest perch at night), Pounce (food within 15/25/40 cm; prey only in sandbox), ChaseIntruder (wrasse/blennies within 1 BL of its perch) |
| **Lawnmower blenny** | RockGraze (perch on rock and rasp 5–20 s, hop 5–15 cm, stays within 20 cm of `HOLE_LAWNMOWER`), InHole (night), BenthicFeed, BlennyRival (one blenny per tank, 04 §15) |
| **Tailspot blenny** | InHole/Peek at `HOLE_BLENNY` (eye swivel), PlanktonDash (darts up for drifting food or plankton every 30–90 s), PerchNearHole (tail curled), Graze ×0.4, BlennyRival |
| **Dottyback** | CaveDart (short rock-hugging darts around `CAVE_DOTTYBACK`, pauses in cover), Territorial (moderate to blennies, low to the cardinal, none to shrimp — 04 §13), Feed, Sleep in cave |
| Banded shrimp | Hang (inverted under the left ledge, at a perch found on the SDF at run time; leg shuffles; sways at a passing fish), ShrimpForage, NightForage (walks rock tops, walls and ceilings within 30 cm), cosmetic Clean, MoltHide, TailFlip (then walks or swims home). See §19.5. |
| **Skunk cleaner shrimp** | Station on a rock face near `STATION_SHRIMP` (perch found at run time; rocks side to side at 0.8 Hz when clients are within 30 cm), Climb (swims onto the posing client at ≤ 8 cm/s, rides it 8–20 s, swims home), ShrimpForage, MoltHide (station closed while molting), TailFlip. See §19.5. |
| **Hermit crab** | CrawlWander, Pick (rasping rock), Scavenge, Retreat into shell (posture 6) |
| **Brittle star** | HideUnder (`BRITTLE_HOME`, 1–3 arm tips out toward the cave mouth), Roam at night (arm rowing), RaceToFood (arms first, then the disc), ArmFeed (arm loops the item to the mouth), Retract (arms in, run home), autotomy when bitten. See §19. |
| **Feather duster** | Filter (crown open, eats drifting particles within 8 cm plus a detritus baseline), Snap (retracts on shadow, tap, a fish touching the crown, or food dosed within 15 cm above; reopens over 10 s) |
| **Nassarius** | Buried (siphon only, posture 5), Erupt and race to food after any feed, re-bury where they stop |
| **Astrea** | GlassGraze (slow on glass/rock), Flipped (lands upside down on the sand occasionally, rights itself after 2–6 h) |
| **Asterina** | GlassGraze, small Scavenge; population splits every ~30 days up to a cap of 4 |

Cleaning stations (04 §13C): the wrasse and the skunk shrimp each register a `CleaningStation`; clients pick the open station with the best `fitness / (1 + d/30)` (fitness grows with the cleaner's hunger). A client posing at a station blocks it for others. Fish `parasiteLoad` grows 0.1/h and is cleared by a visit; VisitCleaner score = 0.15 + 0.6·min(1, 3·ichStage) + 0.5·parasiteLoad (+0.1 tang/clown). Molting shrimp close their station for 36 h.

Molting: banded shrimp every 21–56 days, skunk every 21–42 days, hidden 36 h; a molt shell lies on the sand for 24 h (`SimSnapshot.moltShells`). Failed molts (25 %) when Ca < 360, Mg < 1150 or iodine < 0.02. Copper is the global invertebrate kill switch (invertebrate copper tolerance: optimum 0, lethal 0.1 ppm).

Posture codes in `OrganismPose.posture`: 0 swim/crawl, 1 perched, 2 hanging, 3 in hole/den, 4 cocoon, 5 buried, 6 retracted/hidden, 7 flipped.

### 9.3 Tuned constants (SimRunner + acceptance tests)
| Constant | Value | Why |
|---|---|---|
| Nitrification `K_AOB`, `K_NOB` | 0.10, 0.15 ppm/h at B = 1 | cycle peaks on day ≈ 6.7 (NH3) and ≈ 15 (NO2) |
| Bacteria growth `MU_AOB`, `MU_NOB` | 0.012, 0.007 /h | same |
| `new_tank_cycle` preset | ghost-feed 0.3 g flake/day, B = 0.02 | fishless-cycle timeline (§8.2) |
| Food nutrient factor | flake/pellet/floating pellet/algae wafer/coral food 1.0, mysis 0.3, nori 0.3, cyclops 0.3, prawn/krill 0.35, pods/phyto/brine 0.2 | frozen food is mostly water; avoids NO2 spikes after the eel's feed (§4.6 table) |
| `FEED_TAN_PER_G`, `FEED_PO4_PER_G` | 0.04, 0.004 | NO3 +2–5 ppm/week at 1 g/day |
| `K_EXCRETION` | 0.0074 mg TAN/h per g^0.8 | basal excretion |
| Eel `massCoeff` | 0.0022 | a 45 cm snowflake ≈ 200 g |
| Coral alkalinity demand | Kenya 0.004, GSP 0.006, zoa 0.003, clove 0.002, mushroom 0.001, organ pipe 0.008 dKH/day + 0.01 abiotic | a soft-coral tank holds alk ≈ 7–8.5 with water changes |
| Routine water change | 10 % every 7 days at 10:00 (`TankConfig.waterChangeEveryDays/Fraction`) | assumed until the app passes Tee's logged routine |
| Corpse detritus | 0.02 g TAN per g of body over 24 h | a dead blenny should spike ammonia, not wipe out the tank |
| Hunger | rise = feedsPerDay / (1440 × 1.4) per min × 2^((T−25)/10) (× 0.5 asleep); a meal of 0.012 × mass^0.8 g lowers hunger by 0.8 | |
| Abstract-mode grazing | grazers eat 1.6 × their hunger rise × availability (hair/(hair+0.02), or biofilm 0.7 for snails/asterina/hermits, 0.35 for the lawnmower); hair removal per minute: tang 2e-6, lawnmower 3e-6, tailspot 0.5e-6, astrea 1e-6, hermit 0.5e-6, asterina 0.2e-6 | healthy-tank hair settles ≈ 0.05–0.1 |
| Filter feeders / detritivores (abstract) | 0.8 × hunger rise × clamp(detritus/0.2, 0.3, 1.2) | duster, brittle star and nassarius survive 90 days |
| Ich | base pressure 0.0004/min, temperature factor ×4 above 28 °C (×1.5 above 26.5), growth 1.65e-5, recovery 3.5e-6 | §8.3 (> 45/50 seeds) |
| Health | K_STRESS 0.00016, K_DISEASE 0.00035 (stage²), K_REGEN 0.0003, K_LETHAL 0.0004 × (1 + min(2, overshoot)), K_STARVE 0.003 | §8.4/§8.5 |
| Eel collision length | min(L, 6 cm) | clearance follows girth, so it can use the den and crevices |
| Steering hard resolve | up to 4 push-outs per tick; if the glass clamp pushes back into rock the animal holds position | §8.7 never violated |

### 9.4 Acceptance test interpretation
- §8.7 "sdf(pos) > 0.3 L": uses the rock SDF only (sand and glass are analytic) and the collision length; animals in an attached posture (perch, hole, den, hang, crawl, buried) must only satisfy sdf ≥ 0. Tested at 09:30, 12:00 and 21:00 with a feed and a glass tap.
- §8.8: the shrimp is placed 5 cm in front of the den mouth; predation is enabled (sandbox); 20 seeds.
- §8.4: runs in health-only mode (`advanceAbstract`) with the default feeding schedule (09:30 flake 0.35 g, 17:30 mysis 0.6 g, eel 1.2 g mysis every 3 days, duster 0.3 g pods every 2 days) and the routine water change.
- §8.9 uses an empty SDF (no rock shadowing).
- Extra tests: N mass balance with water changes, established tank stays clean, NO3 rise, evaporation SG rise, heater-off cooling, daily pH swing, copper kills inverts, heater stuck on, no predation in reality mode, feeding reduces hunger, cleaners serve clients, night behaviors, all brains stay finite and in the water.

### 9.5 Contract extensions (backward compatible, all defaulted)
- `OrganismPose`: `brain`, `posture`, `eyeYaw`, `mouth`, `appendagePhase`, `extension`, `visible`, `nickname`.
- `CoralPose`: `speciesId`, `anchor`, `flow`, `tissue`.
- `SimSnapshot`: `speed`, `lightIntensity`, `stats` (fish card), `tick`, `abstractMode`, `moltShells`, `noriRemaining`, `nori` (cloth pose), `gusts`, `sandPiles`, `flowStrength`, `surfaceHeights`/`surfaceDims`, `flowGrid`.
- `OrganismStats` (FishCard, follow mode):
  - `caption`, `focusId`, `envStress` and `cause`.
  - `intent`: the caption without the name, e.g. "eyeing the skunk cleaner shrimp".
  - `intentTargetId`.
  - `relations`: a list of `RelationInfo`, the five most salient social memories. Each has `label`, one of "Afraid of", "Chased by", "Chases", "Dominates", "Defers to", "Cleaning partner", "Wary of" or "Knows", plus `familiarity`, `fear`, `harassedBy`, `harassed`, `dominance`, `cleanings` and `secondsSinceSeen`.
  - `waryOfId`: the animal it is keeping clear of right now.
- `sim/Reality`: maps the tracker's data into the sim.
  - `waterFrom(latestValues)` converts Track A base units (°F, SG).
  - `seedsFrom(livestock)`: group rows expand to `id`, `id#2`, and so on. The first organism keeps the livestock id, so `LiveFocus.followId` matches it. `livestockIdOf` reverses the mapping.
  - `lightsFrom(schedule)`.
- `Command`: `Heater.stuckOn`, `AddOrganism.role`, `SetSize`, `SetPredation`, `Shadow`.
- `TankConfig`: `autoFeed`, `predationEnabled`, `waterChangeEveryDays`, `waterChangeFraction`, `seed`.
- Anchors added for the new livestock (Track B: please add them to `anchors.json`; defaults are merged in when missing): `coral_organpipe_kenya_base`, `HOLE_LAWNMOWER` (30, 10, 11; dir 0.3, 0.1, 0.95; depth 7), `STATION_SHRIMP` (50, 18, 26), `DUSTER_TUBE` (15, 3, 27), `BRITTLE_HOME` (95, 4, 15), `CAVE_DOTTYBACK` (42, 18, 6). The HOB outflow is at x = 71 (ASSETS) rather than 61.

### 9.6 Owner corrections and the realism pass (research 05)

**Engineer goby, not an eel.** Tee's "eel" is a juvenile **engineer goby** (*Pholidichthys leucotaenia*, black/yellow stripes, `engineer_goby.glb`). `BrainKind.ENGINEER_GOBY` (genus `pholidichthys`; sheet `brain: "goby"` is aliased) drives `GobyBrain` (`ai/GobyBrain.kt`):
- Burrow under the R5 rock at sand level (anchor `BURROW_GOBY`, falling back to `CAVE_EEL` / `den_path`); the body trails down the tunnel spline (posture 3, same anguilliform spline deformation as the eel).
- By day it hovers in a loose group 2–9 cm off the mouth, picking plankton (`MouthHover`, `Feed` r 18 cm). It also makes excavation trips (`Excavate`, every 45–300 s): into the burrow for 4–8 s, out with a mouthful (`mouth` = 1), then it spits 0.6 cm³ onto a sand pile 4–9 cm from the mouth.
- `Simulation.sandPiles` holds up to 8 piles. A pile slumps with τ = 4 days, faster in strong flow. Abstract mode deposits at the day rate. Exported as `SimSnapshot.sandPiles` (x, z, radius, height).
- At night it stays deep in the tunnel, invisible. Shy: startles or fear send it to the mouth.
- Never a predator. The moray brain (`EelBrain`) is kept for other tanks only. Its tests use an explicit moray seed.
- Fish collision length is capped at 3 cm, so clearance follows girth.
- `K_EXCRETION` was retuned 0.0074 → 0.016 because removing the 200 g moray cut fish biomass ~3×. Nitrate still rises 2–5 ppm/week.

**Dottyback** (striped indigo hybrid): its `Territorial` behavior now also briefly chases any smaller fish within 10 cm of its cave.

**Calibrated tolerance profiles** (`HealthModel`).
- Group profiles take precedence over sheet tolerances, which in v1 are generic copies.
- Units: NH3 is ppm NH3 = 1.214 × NH3-N; NO2 is ppm NO2⁻ = 3.28 × NO2-N. Format is opt / tol / lethal.

| key | fish | inverts |
|---|---|---|
| temp °C | 23.5–28.5 / 22–30.5 / 17–33 (tang, cardinal lethal 32.5, tol 30) | 24–28 / 22.5–29.5 / 18–32 |
| NH3 free | ≤0.02 / 0.06 / 0.6 (eel-type 0.8) | ≤0.01 / 0.04 / 0.35 |
| NO2 | ≤0.3 / 1.6 / 3.3 | ≤0.15 / 0.8 / 2.0 |
| NO3 | ≤30 / 60 / 200 | ≤20 / 50 / 120 |
| DO mg/L | ≥5.0 / 4.0 / 3.0 (tang, cardinal 5.5 / 4.5 / 3.2) | ≥5.5 / 4.5 / 3.5 |
| pH | 7.85–8.45 / 7.65–8.6 / 7.4–8.9 | 7.9–8.4 / 7.75–8.55 / 7.5–8.8 |
| SG | 1.021–1.0265 / 1.018–1.028 / 1.012–1.032 | 1.023–1.026 / 1.021–1.0275 / 1.018–1.030 |
| Mg, Ca | — | Mg ≥1200 / 1050 / 800; Ca ≥380 / 330 / 200 |
| Cu | ≤0.05 / 0.25 / 0.5 | ≤0.005 / 0.03 / 0.1 |

**Rate-of-change shock.**
- Each animal has acclimated temp, SG and pH values. They lag the water with τ 12 h, 2 days (inverts 3 days) and 8 h respectively.
- `|now − acclimated| / step` feeds the tolerance curve opt 0.4 / tol 1.0 / lethal 1.9 (keys `shockTemp`, `shockSg`, `shockPh`).
- Steps: fish temp 3 °C, SG 0.008, pH 0.5. Inverts: 2 °C, 0.004, 0.3.
- These follow research 05: fish shrug off 0.003–0.005 SG/h, and 0.015+ is lethal. Shrimp tolerate about 0.002 SG/h.
- Lethal health loss is `K_LETHAL × (1 + min(4, overshoot))`, so death comes in about 8–30 h.

**Flow stress** (key `flow`): weak swimmers (cruise < 1.2 BL/s) are stressed above pump strength 1.25; all fish above 1.7. Morays and gobies are exempt.

**Signs** follow research 05 §4:
1. Rapid gills (max of DO, temperature, NH3 and NO2 stress > 0.3).
2. Gasping (fish: DO < 4, NH3 stress > 0.55, heat > 0.6). `SurfaceGasp` takes over.
3. Hiding and lethargy (stress > 0.65): the new `Unwell` behavior keeps them low near cover.
4. Appetite loss: `HealthModel.appetite` scales `Feed`, and the fish refuses food below 0.3.
5. Clamped fins.
6. Moribund: health < 0.2 or stress > 0.88. Fish lie on the sand and roll; inverts stop moving.

Eyes cloud after about a day above NH3 stress 0.5 (`eyeCloud`).

**Cascades.**
- Decomposition consumes O2: detritus decay × 20 mg O2 per mg TAN, plus nitrification (3.4 per TAN, 0.35 per NO2).
- Dying coral tissue becomes detritus: `CORAL_TAN_PER_COLONY` 2 g TAN × biomass.
- Pump strength above 1 adds detritus export.

**Water and algae** (`WaterSim`).
- Pump strength 0–2 (`SetParam("flow", v)`, where 0 means pumps off) scales the powerhead jets (`FlowField.strength`), O2 transfer (kLa × (0.3 + 0.7 s)), skimmer NO3 export, pump heat and cyano washout.
- **Cyano**: grows with light × (PO4, detritus, low N:P < 30) × low flow, ×1.5 above 29 °C.
- **Diatoms**: grow in a new tank (immature bacteria) or with high nutrients, under moderate light. They fade over weeks.
- **Coralline**: grows under moderate light when Ca ≥ 350, alk ≥ 6, Mg ≥ 1150, pH ≥ 7.75 and PO4 is low. It recedes when any of those is off.
- Photoperiod acts through light hours, since all growth terms scale with intensity.

**Corals** (`CoralSim`).
- Temperature bands shifted to the 30–31 °C tank bleaching trigger.
- NOAA DHW accumulator: degree-weeks above 29 °C. Bleaching tends toward smoothstep(3, 8 DHW) over ~3 days, and tissue is lost past 8 DHW. Recovery runs at 0.00001/min (~70 days).
- Salinity shock: step 0.004 SG, lethal at 2 steps. This closes polyps fast and costs tissue at 0.002/min, so death comes in hours.
- pH: 7.9–8.4 / 7.7–8.55 / 7.3–8.9. An alkalinity swing has a 2 dKH step.
- Light-jump shock against a 3-week acclimated level causes retraction, then paling.
- Extension × (0.5 + 0.5 smoothstep(0.05, 0.6, pump strength)). Kenya retracts above strength 1.5.
- Ultra-low nutrients (NO3 < 0.5, PO4 < 0.01) pale soft corals.

**Social layer** (`ai/Interactions.kt`, `ai/Social.kt`).
- Every 1 s each animal scans every other living animal (identity, species, size, distance, state). It keeps a `Relation` per individual:
  - Familiarity: +0.02/h within 30 cm.
  - Fear: half-life 3 h.
  - Harassed-by / harassed: half-life 6 h.
  - Dominance in ±1.
  - Cleaning history.
- `onAttacked(..., by)` and `finishClean` write to it.
- Research 05 §17 matrix (`Interactions.aggression`):
  - Moderate 0.5–0.7: tang–tang, wrasse–wrasse, hawk–hawk, dottyback–dottyback, blennies.
  - Low 0.1–0.35: clown pair hierarchy, cardinals, hawk→dottyback, dottyback→clown and →hawk, banded→cleaner shrimp.
  - High: banded–banded 0.8.
- The generic `Rivalry` behavior handles pairs that the species behaviors don't. Only the dominant conspecific chases (female clown, then the larger fish, then the bolder one). The cooldown is 900 s ÷ drive.
- `Social.chaseDrive` lowers the drive for familiar neighbours ("dear enemy") and raises it for recent provokers. It drops to 0 against a dominant fish.
- `KeepClear` makes a fish avoid the individual it fears. That is either a remembered attacker or a much larger aggressive fish it hasn't met before.

**Non-repetition.**
- Every utility score below 0.95 gets per-behavior Ornstein–Uhlenbeck noise: τ 20 s, amplitude = the individual's `whim` (0.025–0.055, drawn at birth). This comes on top of the ±15 % temperament jitter and the 0.12 hysteresis.

**Captions** (`ai/Caption.kt`, `OrganismStats.caption`).
- Built from the species name, the behavior's verb and its focus (`Organism.focusId`, or the nearest plausible partner). Examples:
  - "Falco hawkfish watching the skunk cleaner shrimp"
  - "Bluestreak cleaner wrasse cleaning the blue tang"
  - "Engineer goby · moving sand out of its burrow"

**Acceptance tests** (`ParameterEffectsTest`):
- 31 °C for 2 days: fish show rapid gills and stress but all survive. Corals are stressed. Fish recover within 4 days at 25.5 °C.
- SG 1.025 → 1.018 over 1 h and held 2 days: fish stress > 0.4 with no fish deaths, and at least one shrimp dies.
- NH3 at 0.25 NH3-N for 24 h: gill signs, no deaths. At 1.0 NH3-N, at least half the fish and half the inverts die within 3 days.
- A die-off left in the tank: TAN peaks above 0.3 and DO dips.
- Pumps off for 12 h: DO drops. Strength 2 stresses the cardinal.
- Lights forced on with PO4 0.2 for 14 days: more than 1.3× the hair algae plus cyano of the normal schedule.

`AiTest` adds `gobyDigsAndHides` and `victimsRememberAttackers`. `ICH_BASE_PRESSURE` went 0.0004 → 0.0005 to keep the 30 °C ich test above 90 % once heat stress was recalibrated.

**Sally Lightfoot crab** (*Percnon gibbesi*, `BrainKind.SALLY_CRAB`, sheet `brain` "crab" / "sally_lightfoot", model `sally_lightfoot.glb`, default stock id `crab`, 5 cm leg span). Brain `SallyCrabBrain`:

- Hides wedged in a crevice by day: anchor `CREVICE_CRAB`, falling back to `CAVE_SMALL`. Rest posture 4, with the legs peeking out. By night (activity is NOCTURNAL) it roams up to about 25 cm.
- Moves in a stop-and-go sideways scuttle: `SteerIntent.sideways` turns the body across the travel direction. Each 2 s cycle is a 0.6 s burst at half burst speed, then a pause.
- `Dart` (score 0.97): when startled, threatened, or a larger fish passes faster than 3 cm/s within 6 cm, it runs back to the crevice at full burst speed (8 BL/s).
- Grazes hair algae: `grazeRemovalPerMin` 1.2e-6, scaled by size. By day it grazes only within 10 cm of its crevice. In abstract mode it grazes only at night, with a film floor of 0.5.
- Scavenges food: it is in `SCAVENGERS`, with a 40 cm radius.
- Rare `NightGrab`: needs predation on, night, a leg span of at least 5 cm and hunger of at least 0.85. It rolls once per sim hour with p = 0.15, and the strike lands with p = 0.3, so a starving crab with prey nearby succeeds about 4.5 % per hour.
  - Prey: Astrea or Nassarius snails, or a sleeping fish shorter than 0.8 × the crab's leg span.
  - A missed strike counts as an attack in the victim's social memory.
- Sensitivities:
  - Copper uses the invert profile.
  - Salinity level: opt 1.024–1.026, tol 1.022–1.027, lethal 1.019 / 1.029.
  - Salinity swings use `CRAB_STEPS`, with an SG step of 0.003 versus 0.004 for other inverts.
  - Iodine uses `CRUSTACEAN_IODINE` (opt ≥ 0.04, tol ≥ 0.02, lethal < 0.003 mg/L), shared with the shrimps.
  - It molts every 28–56 days and hides for 24 h. Below 0.02 mg/L iodine, or with low Ca or Mg, 25 % of molts fail.
- Tuning: adding the crab raised the lawnmower blenny's abstract film floor from 0.35 to 0.4, so it survives the 90-day healthy-tank run.

### 9.7 Water physics: flow solver, scalar transport, surface, optics (owner request)

**Flow** (`geom/FluidSim.kt`): a stable-fluids solver (Stam 1999) on a MAC grid with cubic cells over the water volume (x 0–121.9, y 3–38.1, z 0–45.7; sizes from `TankDims`).

| `FluidQuality` | grid | cell | projection iterations |
|---|---|---|---|
| LOW | 32 × 10 × 12 | 3.8 cm | 30 |
| MEDIUM | 48 × 14 × 18 | 2.54 cm | 45 |
| HIGH | 64 × 19 × 24 | 1.9 cm | 60 |

- **Step**, with dt ≤ 0.1 s:
  1. Pump forcing.
  2. Heater source and Boussinesq buoyancy (β·g = 2.6e-4 K⁻¹ × 981 = 0.255 cm/s² per °C).
  3. Vorticity confinement (ε 0.35).
  4. Eddy viscosity (0.5 cm²/s; molecular 0.01 is far below grid scale).
  5. Semi-Lagrangian advection.
  6. Pressure projection by red–black Gauss–Seidel SOR (ω 1.75), warm-started, mean pressure removed.
- **Boundaries**: rock cells (SDF < 0 at the centre) are solid. Glass, sand and the surface are free-slip walls.
- **Pumps** (`Pump.defaults`, est. ratings): PH-A 850 gph through a 3.2 cm nozzle, PH-B 600 gph through 2.8 cm, and the HOB at 200 gph.
  - Nozzle exit speed `v0 = Q/(π d²/4)`, about 111 cm/s for PH-A.
  - The grid jet keeps the momentum flux: `v_cell = v0 √(A_noz/h²)`, capped at 100 cm/s. Face velocities within 1.1 cells of the nozzle relax toward it (rate 12/s).
  - The HOB is a mass source at its return plus an equal sink at the intake, so the projection solves ∇·u = s.
- **Runtime and coupling**:
  - Runs on the sim thread at about 15 Hz of wall time: `Simulation.enableFluid(quality)`, after a 20 s spin-up.
  - `FlowField.fluid` routes every `velocityFast` query to it. That covers food and detritus drift, coral sway and flow speed, fish station-holding, swimming effort and corpses.
  - The renderer gets a cell-centred `FlowGrid` copy about 4×/s for particle advection.
  - Pump strength and on/off map to `strength` / `powerheadsOn`.
  - Tests use the analytic field unless they enable the solver.
- **Measured** (JVM): LOW is about 4 ms/step and MEDIUM about 14 ms. After spin-up the relative divergence is < 0.02 (`FluidTest`). It stays stable for 150 s with the heater on, with peak speed < 160 cm/s. No GLES 3.1 compute path yet; the CPU solver is the only path.

**Scalar transport** (same grid, cell centres): temperature anomaly (°C), a dose tracer (in the last dosed parameter's units) and food scent. Each is advected semi-Lagrangian and diffused (κ 0.5 cm²/s).
- The advection loss is restored, so the total is conserved. The mean is removed, so the well-mixed value stays in `WaterSim`.
- Anomalies relax with τ 30 min; scent with τ 2 min.
- The heater adds `P·dt/(ρ c V)` to its cells while it is powered.
- A dose enters 2 cm below / 4 cm in front of the HOB return and is removed uniformly from the mean.
- `Simulation.localWater(p)` = tank mean + local anomalies. The health tick uses it per animal while the solver is on, so a fish sitting in a fresh dose plume or by a stuck heater feels it first.

**Surface** (`geom/WaterSurface.kt`): 96 × 26 heightfield (1.27 cm) obeying the damped wave equation `h_tt = c²∇²h − γ h_t`.
- c = 23 cm/s, the gravity–capillary minimum. γ = 1.2/s. Leapfrog substeps keep c·dt/dx ≤ 0.5. Glass edges reflect, and the mean level is kept at 0 (volume conserved).
- Sources:
  - HOB return splashing: 25 impulses/s of 0.05–0.1 cm.
  - Pump agitation: 12/s × strength.
  - Food landing: 0.08 cm.
  - Glass taps: 0.03 cm.
- Published as `SimSnapshot.surfaceHeights` / `surfaceDims`. The renderer refracts the key lights through its normals for caustics and the view from below.

**Optics** (renderer, `assets/shaders`):
- Snell refraction n = 1.34 (seawater) / 1.0 (air) / 1.52 (glass).
- Schlick Fresnel, F0 = 0.02 water–air and 0.04 glass.
- Total internal reflection above the critical angle asin(1/1.34) = 48.3°.
- Beer–Lambert spectral attenuation of seawater along the view path, per cm (R, G, B) = (0.0045, 0.0009, 0.0006) absorption + 0.0004 scattering. Red is lost fastest, so the far glass and deep shadow read blue-green. In-scatter takes the light colour × (0.05, 0.22, 0.32).

### 9.8 SimRunner
`./gradlew -PcoreOnly :core:simRunner` prints a 24 h scenario (each hour 2 min of full 20 Hz behavior plus abstract time, and 12-min windows around the 09:30 and 17:30 feeds) with every animal's behavior mix, hunger, stress and the water state. `--args="long 90"` runs 90 days of health/chemistry with a daily log; `--args="preset ich_outbreak 14"` runs a preset.

### 9.10 Social and stress realism (research 06/07, `RealismAcceptanceTest`)
Sources: `docs/research/06_interactions_analysis.md` (§4.1, §5.3, §5.5, §5.6) and `07_gap_sources.md`. Tags: [S] sourced, [D] derived, [I] inferred and tunable.

**Stress channels (`StressModel`, 06 §4.1).**
- Each channel (temp, sg, pH, NH3, NO2, NO3, DO, light, social) has a fast state `S_fast`. It follows the drive with τ_rise / τ_fall.
- Each channel also has a slow state `S_slow`. It follows `slowGain × S_fast` (or the chronic floor) with τ_slowRise / τ_slowFall.
- The drive is measured against an acclimation setpoint `A`, which tracks the parameter with τ_accl.
  - Ramps slower than τ_accl therefore move `A` and raise little stress.
  - Steps saturate `S_fast`.
- `S_env = 1 − Π(1 − w·max(fast, slow))`.
- NH3 has `slowGain` 0.35 and τ_slowFall 7 d. Gill irritation lingers but does not pin appetite (c27). Fin clamp uses `0.8·max(slow, 0.5·fast)`.
- The social channel has a linear envelope, unlike the other channels' smoothstep. It is fed by a reservoir (τ 120 s) that chases, jolts and glass taps add to. A tap therefore peaks within minutes and decays over about an hour (c24).
- **Thermal acclimation.** The temperature setpoint is capped at `thermalCeiling`. `HealthModel.acclimatedTemp` shifts optHi and tolHi up by at most 3 °C as `A_temp` rises, and keeps them 2 °C and 1 °C below lethal. A fish held at 31 °C settles (c22, c25).

**Pair matrix (`ai/PairMatrix.kt`, `sim/interactions.json`, 06 §5.3).**
- *Scan.* Every 0.5 s each actor scans its rows. A target entering the trigger radius opens an entry. After `lingerS` the entry rolls once a minute with probability `p`.
- *Modifiers on p:* habituation by familiarity, newcomer ×, sensitisation, hunger, size terms, temper, stress, crowding (computed every step; `CROWDING_BASE` and `CROWDING_GAIN` were calibrated on the 33 cm-deep footprint and scale with `TankDims.DEPTH_CM`, so the default stock in Tee's measured tank stays the ×1 baseline), tactile damping, and rank.
- *Hourly cap.* A token bucket that refills at `floor + (max − floor)(1 − fam)`, scaled by the modifiers below, and holds max(1, ¼ h of the cap).
- **Host-defence sensitisation:**
  - repeated intrusions raise p up to ×2;
  - they shorten the stare;
  - re-rolls come every 60 s × (latency factor)², so about 5 s by the third intrusion (S:Fleming24; c02).
- **Tactile damping** (S:Cheney08) multiplies both the cap and p by 0.35 for 20 min after a dance. It does not apply:
  - between strangers (either animal in its first day; the field percentages include cleaners);
  - to the clown pair's dominance quiver.
- **Standoff rows** shrink their watched halo past familiarity 0.6, to 40 % at familiarity 1 ("the halos partition the rock"; c08).
- **`RearrangeRock`** keeps intrusion memory and caps familiarity at 0.5. Territorial cap and p then run ×(1 + 2·e^(−t/12 h)) [I] (c20).
- A wrasse at work is never a target and is not `ANY_FISH`.
- **Lawnmower rows** use the 07 gap 1 figures (Townsend04): 0.9 per intruding blenny of its own size or smaller (`sizeTerm notLarger`), 0.2 for the tang. Dottyback rates are [I].
- **Night predation** (`perNight` rows, rolled at 02:00 every step) makes one roll per actor and row, on the nearest eligible target.
  - **Sally Lightfoot** (07 gap 5): rolls only when hungry; sleeping fish must be under 0.8× its leg span; snail 0.05 and fish 0.01 per night [I].
  - **Shrimp** (07 gap 4): banded-on-skunk kills are behind `crossSpeciesShrimpKills` (off). Conspecific molt kills (`skunk_skunk_molt`, `cbs_cbs_molt`) need a group of 3 or more (S:Wong11, S:Johnson69).

**Cleaner station loop (`WrasseBrain.Work`, 06 §5.5).**
- *Who.* The wrasse picks clients by weight:
  - base `area × (1 + 3·ich + 0.5·posing) × (1 − 0.5·threat)`, where area = (L/7)²·√(depth/0.35);
  - × a due term that grows with time since the last contact (900 s scale);
  - × 10 for overdue clients (45 min), which are sought tank-wide;
  - × up to 4 for aggressors whose damping is lapsing.
- *How.* Sequence: approach → tactile dance (high-rank, hungry or recently chasing clients) → pose request → inspection.
  - The pose request succeeds with p = (0.3 + 0.6·trust) × need. Need recovers over 2000 s after a full pose, so the tang gets 1–3 full poses/h plus brief passes.
  - Inspection length is lognormal with mean 6.7 s × area^¼.
  - Cheating runs at 0.025/s, or 0.17/s when hunger > 0.6 (S:Pinto11).
  - The rest between bouts is 15–75 s × (1.3 − hunger).
- *Getting there.* There is no path-finding. `Ctx.viaOpenWater` sends a swimmer over the rock, or to a doorway beside the goal under an overhang. A swimmer in a crawl space under a low rock roof (e.g. beneath R9 by the dottyback cave) first slides out sideways to a spot with open water above it, since it cannot climb from where it is. A client still unreachable after 20 s is skipped for 5 min.
- The shrimp counts approaches every tick (`preTick`, c16).

**Newcomers.** A tailspot added to the tank prospects holes and grazing patches for its first day (c09). Engineer goby adults patrol on about 5 % of nights (07 gap 3).

**Swim speeds** (07 gap 2). `SwimSpeed` caps cruise at the family field speed (labriform 68.8 cm/s, body-caudal 39 cm/s; Fulton07). Burst BL/s falls as (adult/L)^0.5 past adult size.

**Test mapping.**
- `RealismAcceptanceTest` c01–c28 follow 06 §5.6 by number. Windows are 2–5 h instead of a 10 h day where a rate per hour is checked.
- c22 and c25 clear spontaneous ich (`noIch()`), because the heat checks are about stress filters.
- `ResearchGapsTest` gap1–gap5 cover research 07.

## 10. Catastrophic heat (`HeatModel`, `ai/HeatBehaviors.kt`)
Research: `docs/research/08_extreme_heat.md` (§2 stages, §6 recovery, §7 model). Runs on the chemistry tick beside the chronic tolerance path. Per animal it keeps a damage value `D` (0..1), the stage, the episode's peak stage, a long-term acclimation temperature, a dT/dt filter and a 24 h hourly window for slow-creep detection.

Equations (constants in `HeatModel`):
- `t_death(T) = anchor × 10^(−slope × (T − 36))`, floor 3 min. `D += dt / t_death(T − refuge − ΔCTmax)` while `T − refuge > T_ag`.
- LOE dwell: `D += dt / t_LOE` while `T − refuge ≥ CTmax − 1.5`, with `t_LOE = loeMin × 10^(−0.5 × (T − refuge − (CTmax − 1.5)))`, floor 1 min. This term is an addition to 08 §7.1, which alone cannot give the 10–40 min LOE window on a 1 °C/h ramp.
- Recovery: `D × exp(−dt / 24 h)` once `T < T_ag`.
- Stage thresholds on `D`: AGITATED 0.05, GASPING 0.25, LETHARGIC 0.50, LOE 0.85. Stage = max(thermal stage from `T − CTmax`, stage from `D`). Stages rise at once and step down one stage at a time, holding 30 min at AGITATED and 2 h otherwise.
- `CTmax` = profile CTmax + acclimation (+0.4 °C per °C above 26 °C, cap +2, τ 7 d; shrimp do not acclimate) + heat hardening (+0.5 °C for 7 d after a LETHARGIC episode) − slow-creep penalty (1 °C). `T_ag = CTmax − 4` and does not move.
- Acute spikes: dT/dt filtered with τ 10 min; ≥ 5 °C/h counts as a spike, ≥ 0.3 °C/h sustained as creep.
- Delayed mortality: a per-profile roll at each peak stage (`pLethargic` default 0.10, `pLoe` default 0.35).
- Surface seeking is an oxygen behaviour only (`HeatModel.surfaceAllowed`): the ASR threshold is `20 % + 1.5 %·(T − 28)` of saturation, and absolute DO ≤ 3 mg/L lets any fish surface.

Profiles (CTmax, t_death anchor at 36 °C, slope; `T_ag = CTmax − 4`):

| Group | CTmax °C | t_death anchor (min) | slope |
|---|---|---|---|
| Cleaner shrimp | 35.0 | 20 | 1.2 |
| Turbo/Astraea snail | 35.0 | 20 | 1.0 |
| Cardinalfish | 36.0 | 30 | 1.0 |
| Tang, clownfish, cleaner wrasse, Sally Lightfoot | 36.5 | 60–120 | 1.0 |
| Dottyback, hawkfish, moray (est.) | 37.5 | 480 | 0.9 |
| Engineer goby, blenny, hermit crab | 38.0–38.5 | 2160 | 0.9 |
| Nerite/cerith snail | 39.0 | 7200 | 0.9 |

Behaviour (`ai/HeatBehaviors.kt`, installed on every brain): agitated fish dart, gasping fish hold at the surface, lethargic fish sit on the bottom, LOE fish drift. Crabs (hermit, Sally) climb out when agitated. Blennies and gobies hide low instead of darting. Snails detach at CTmax, re-attach 50/50, and are dead if not back on their foot after 12 h. Acute coral sloughing is in `CoralSim`. Not yet built: the heat-death spasm animation.

Source: `core/.../sim/HeatModel.kt`, `core/.../ai/HeatBehaviors.kt`, `core/.../sim/CoralSim.kt`. Tests: `core/src/test/.../sim/HeatModelTest.kt`.

## 11. Algae (`AlgaeModel`, `render/AlgaeVisuals.kt`)
Research: `docs/research/09_algae_and_extremes.md` Part A (A0–A7). `AlgaeModel` is owned by `WaterSim` and replaces the old `WaterSim.algae()` growth term. Seven groups (diatoms, GSA film, hair, cyano, dinos, bubble, coralline) grow on four surfaces (glass, sand, rock top, rock shade). Each (group, surface) cell keeps a biomass.

Growth: `μ = μmax × f_nut × f_T × f_pH × f_sal × f_Cu`.
- `f_nut` is the Liebig minimum of Monod terms for N (`NO3 + NH3 + NO2` as NO3-eq), P (PO4) and, for diatoms, silicate (`K_Si = 0.14 ppm SiO2`), with an internal store that rises fast and decays while starving.
- `f_T` is a Gaussian about each group's optimum; `f_sal` and `f_pH` are linear ramps, and `f_Cu = 1 / (1 + Cu / 0.5)`.
- Light enters per surface through `PAR_AT_FULL` (glass 180, sand 130, rock top 380, shade 100 µmol m⁻² s⁻¹ at full intensity). `DAY_GAIN = 2.4` makes `μmax` the daily rate under a ~10 h day.

Die-back: `d = d_basal + starvation (f < 0.1) + blackout (dark > 14 h) + heat + copper + chemistry`. Chemistry terms include pH and carbonate for coralline.

Cover: `Cover_s = Cmax × (1 − exp(−B / (area × b_half)))` per surface. `AlgaeCover` carries `cover[group, surface]` and a per-group `vigor` (low = pale) to the renderer through `SimSnapshot.algae`.

Decay and grazing: labile decay 0.2 /d, refractory 0.025 /d, 15 % of decaying tissue turns refractory, O2 demand 1.4 g per g DW. Grazers are `GrazerType` rows (g DW/day per reference animal, preference per group, access per surface), capped at 3 g m⁻² d⁻¹, with half of grazed tissue egested to detritus.

Coupling: headline covers are mirrored into `WaterState` (`algaeHair`, `diatoms`, `cyano`, `dinos`, `film`, `bubble`, `coralline`). A value written from outside re-seeds that group's biomass on the next step. The sandbox algae control sets hair cover (`SandboxParams`).

Rendering: `AlgaeVisuals` packs the 16 floats into four `vec4`s of the `Frame` UBO (glass, sand, rock top, rock shade). Its layout must match `assets/shaders/common.glsl`, and `AlgaeVisualsTest` checks the offsets. `rock.frag` and `glass.frag` draw patchy world-space fbm masks with thresholds set so the covered area tracks the cover fraction.

Source: `core/.../sim/AlgaeModel.kt`, `app/.../render/AlgaeVisuals.kt`, `assets/shaders/{common.glsl,rock.frag,glass.frag}`. Tests: `core/src/test/.../sim/AlgaeModelTest.kt`, `app/.../render/AlgaeVisualsTest.kt`.

### 11.1 Grazing trails on the front glass (`sim/GlassTrails.kt`, `GlassMask.graze`)

Owner request "flesh out the natural systems"; research 15 (*Lithopoma*) asks for "actual grazing tracks". Film on aquarium glass grows back between cleanings (§11, `GlassMask.regrow`), and the snails and stars that crawl the pane graze it as they go, leaving the clean, meandering trails every reef keeper sees in the morning.

- **Who**: grazers whose `GrazerType` sweeps film along a path: astrea / turbo / top snails (`TURBO`, `TROCHUS`) and asterina stars (`ASTERINA`). Fish (tang, blennies) bite in patches and hermits pick, so they leave no trail. Only the front pane carries a spatial mask, so only animals with their foot on it (centre within 2.5 cm of the pane, up vector into the tank) draw.
- **Swath and take**: turban and top snails have a rhipidoglossan radula, a broom of many fine teeth that sweeps loose microalgae and film but cannot excavate the substrate (Steneck & Watling 1982, *Mar. Biol.* 68: 299–319; Hawkins & Hartnoll 1983, *Oceanogr. Mar. Biol. Annu. Rev.* 21: 195–282). So a pass takes most of a soft film, not all of it: 60 % per pass over a swath 0.35 × shell length (~0.9 cm for Tee's 2.5 cm astreas; DESIGN ESTIMATE, the snout's width). Asterinas evert the stomach over the film: half the star's width, 40 % per pass (est.).
- **Speed**: astreas now glide at 0.1 cm/s (6 cm/min) and hurry at 0.3 cm/s. Research 17 gives "several cm/min" for a 2–3 cm reef snail on glass (Stomatella, the fast glider, does 1–3 mm/s); the old 0.5 cm/s (30 cm/min) drew trails five times too long.
- **Geometry** (`GlassMask.graze`): a segment every 0.6 cm of travel (a jump of more than 8 cm starts a new trail). A cell loses film in proportion to how much of its width across the track the band covers, and only cells whose centre projects onto [0, 1) of the segment count, so a path's segments tile it with no darker beads at the joints. The mask went from 64 × 32 to 128 × 64 cells (0.95 × 0.55 cm on the 122 × 35 cm wetted front pane) so a 1 cm trail is drawn at its own width; the GPU's bilinear filter softens the edges.
- **Mass**: `AlgaeModel` already removes the grazers' glass share from the glass biomass as a whole (§11 grazing). The trail only says *where*: the mask is a relative density, so the film between trails is drawn that much denser and the total is unchanged. Trails fill in at the film's net growth rate (logistic, seeded from the edges; days, not hours, and not at night), and a water change's wipe or the sandbox film slider resets the pane.
- **Cost**: per tick one cheap check per animal, no allocation; grazed cells are written quietly and published (one `version` bump, one snapshot copy of the mask) at most every 40 ticks (2 s at 20 Hz). Scrapes and regrowth still publish at once.
- **Renderer**: unchanged path: `glass.frag` thins every glass group by the mask (`uGlassMask`), so the trails show as clear lines in the green film and the brown diatom dust, on Android and the web.
- Tests: `GlassTrailsTest` (an even, narrow trail with untouched film beside and past it; a second pass cleans deeper and the film grows back in a week; three astreas on the front pane wear > 20 cells of trail in 2 h while most of the pane stays filmed; fish and back-pane grazers leave none), `GlassMaskTest` (updated for 128 × 64).

## 12. Time-lapse engine (`sim/timelapse/`)
Fast-forwards a copy of the live tank under a scripted change and reports how each animal coped. The live simulation is not touched.

- **Spec** (`TimelapseSpec`): `durationHours`, `sampleEveryHours` (auto), `chunkMinutes` (30), and a list of changes. `Step` sets a parameter once. `Ramp` moves it linearly over `overHours` from the current value by default. `Schedule` applies (hour, value) points, optionally repeating every N hours. `Do` enqueues any `Command`.
- **Fork** (`TimelapseFork.of`): copies water, equipment, livestock with health state, corals and lights. Behaviour positions and floating food are not copied, because the abstract layer parks animals at their home spots.
- **Run** (`TimelapseRun`): calls `Simulation.advanceAbstract` in chunks, with the behaviour layer off and 60 s chemistry and health steps. Changes are delivered as ordinary commands, and ramps are re-applied each chunk. A `CancelToken` stops the run and keeps the partial recording.
- **Recorder and report** (`TimelapseRecorder`, `TimelapseReport`): samples per-animal stats and events. Each animal gets a verdict, worst last: Coped < Stressed < Got sick < Died. The report groups these per species.
- **UI**: `ui/live/TimelapseSheet.kt`, `SimulationHolder`, entry point in the sandbox dock.

Tests: `core/src/test/.../sim/timelapse/TimelapseTest.kt` (runs headless on a `TestTank` fork).
## 13. Microfauna: pods, plankton and the microscope view (research 10)
`sim/MicrofaunaModel.kt` holds the small animals of `docs/research/10_microfauna.md` as aggregate populations. It lives in `WaterSim` next to the algae model (`water.micro`), steps from the chemistry tick in 15-min chunks, and costs nothing at 1x.

**Groups** (`MicroGroup`): harpacticoids (Tisbe, and the "Tigger pod" *Tigriopus californicus*, ~1 mm), calanoids (*Parvocalanus*, ~0.3 mm), cyclopoids, gammarid and caprellid amphipods, mysids, ostracods, isopods, rotifers, ciliates, phytoplankton, bristleworms, micro-snails, spaghetti worms, forams and red flatworms. Caprellids, mysids and flatworms are absent until added. A new tank on dry rock (`dryRock()`, the `new_tank_cycle` preset) has only ciliates and phyto.

**Habitats** (`MicroHabitat`), in individuals:
- `ROCK`: the algae model's rock area (1.2 × footprint) plus 3,000 cm² per litre of display macroalgae.
- `SAND`: the footprint.
- `GLASS`: the lower 8 cm band the pods use.
- `CHAETO`: 3,000 cm² of filament per litre of sump refugium (`SetParam("refugium", L)`). Fish cannot reach it.
- `COLUMN`: per litre.

Capacities are the §3.2 densities with the §3.1 refuge factor folded in, times `(0.3 + 0.7 f_food)`. Column grazers (calanoids, rotifers) take their capacity from recent phyto (a 6-day memory that jumps with a dose), so eggs laid in a bloom still grow up after the cells are gone.

**Dynamics** per group and habitat (§3.1):
- Growth is `r_max · exp(−((T − Topt)/w)²) · f_food`.
  - `f_food = F/(Ks + F)`. F comes from the algae film and diatom biomass, 10 % of the hair, a 2.5 g m⁻² biofilm × microbial maturity, and 3 g m⁻² per ppm of detritus.
  - Phyto, ciliates, nauplii and DOC feed the groups that eat them.
- Copepods, amphipods and mysids carry a juvenile pool.
  - Births go to juveniles, which mature after `0.6 G(T)`. Calanoid copepodites count as adults earlier (`0.45 G`).
  - The birth rate solves Euler–Lotka for two stages, so growth with full food at 27 °C is exactly `r_max`.
- Background mortality, flushing for column plankton (0.1 d⁻¹), the §4 parameter tiers and predation remove animals.
- Animals spill between habitats toward equal fill (0.3 d⁻¹).
- A group that has once reached 20 % of K keeps a hidden rock seed. It never hits zero unless a toxin kills it (copper, medication), and then it is gone until re-seeded.

**Diel exposure**: §2.2 day/night fractions with a 0.4 h lag. The column fraction moves with it.

**Predators** (`PodPredator`, from the live animals each chem tick): the §2.6 weight matrix, multi-prey Holling II on encounters.
- Encounters are `weight × (exposed × access + hidden × pore access) × N`.
- Handling is in harpacticoid equivalents: a gammarid is 10, a bristleworm 50.
- Fish take juveniles at 0.2. Half-saturation is 20,000 pod-eq for fed generalists, 10,000 for the cardinal (night only), and 800 for a mandarin / dragonet (2,000 a day, picks into the pores).
- Corals use the §3.4 per-polyp Holling on the column density, with `c_max` 1/h and D½ 2/mL; adult copepods escape × 0.5.
  - Polyps per colony (est.): GSP 500, Kenya 100, clove 150, organ pipe 200, zoas 50 at 0.3/h.
  - Polyps only count while extended.
  - More than 0.5 prey per polyp in a day sets `coralFed` for 3 days.

**Water parameters** (§4):
- Heat: none below `T_lethal − 2`, then 20 → 50 %/d up to `T_lethal`, then steeper. Breeding stops 2 °C below `T_lethal`.
- Salinity: no effect between 28 and 38 ppt; slower growth from 20 to 28 ppt. Harpacticoids die at up to 50 %/d below ~22 ppt, mysids faster.
- Copper: copepods stop breeding at 0.02 ppm. 0.15 ppm clears every crustacean, worm and snail in 3 days. Residual leaching keeps r × 0.5 for 60 days after the copper is gone (§4 says 30; §6.3 #6 re-seeds on day 30).
- Low DO, pH, ammonia and nitrate tiers as written in §4.
- Fenbendazole and praziquantel run for 3 days.

**Feedback into the tank**:
- Dead microfauna go to detritus at 0.02 g TAN per g wet, through `addDetritusTanGrams`, so the nitrogen balance holds.
- Harpacticoids (0.005 mg DW/day) and micro-snails graze the diatom and green film (`AlgaeModel.podGrazeGPerDay`, at most 5 %/day of the palatable film).
- A water change removes that fraction of the column plankton.
- Killing more than 1,000 flatworms dumps their mass into detritus and holds a 0.3 social-stress input on every fish for 48 h.
- Phyto does not draw nitrogen from the water. A full dose is about 14 mg N, or 0.06 ppm NO3.

**Sandbox actions** (`Command.Micro(MicroAction, amount)`):

| Action | Effect |
|---|---|
| Tisbe bottle | 3,500 harpacticoids |
| Parvocalanus bottle (`DOSE_TIGGER`) | 2,000 calanoids, 9 per L |
| Tigger pod bottle (`DOSE_TIGRIOPUS`, Tigriopus) | 1,500 harpacticoids on rock + 500 on glass |
| Phyto | 2 × 10⁵ cells/mL |
| Rotifers | 5 per mL |
| Amphipods | 100 |
| Mysids | 50 |
| Live rock | 2 % of K of the rock groups |
| Flatworm frag | 5 flatworms |
| Flatworm treatment | Flatworms killed, toxin released |
| Fenbendazole, praziquantel | Course starts |

**Snapshot**: `SimSnapshot.microfauna` (`MicrofaunaState`), cached between steps. It carries:
- per-group totals, juvenile ratio, egg-sac (breeding) index, exposure, trend and presence;
- `visible[group × scene]`, the expected animals in view per cm² of rock, glass or sand or per mL of water right now. Glass at night includes half of the rock population's emergence.
- phyto cells/mL;
- the torch estimate (pods per 100 cm² of glass at lights-out + 1 h);
- the pod budget (copepods born vs eaten per day);
- coral capture and the toxin flag.

`TimelapseFork` and `rebuildWater` copy the whole model.

**Microscope** (`sim/MicroView.kt`, used by the app's Micro view):
- Zooms 1x / 10x / 40x have fields of 510 / 5 / 0.3 cm² and focus volumes of 2,500 / 25 / 0.3 mL.
- A group renders only if it spans at least 2 px of a 1080 px field. It is left out if it is bigger than 60 % of the field.
- Each group gets one Poisson draw from the expected count, capped at 300 sprites in total; the remainder shows as shimmer.
- Copepod nauplii appear at 10x and 40x only when juveniles exceed 20 % of adults.
- Phyto shows as a green haze, linear in log10(cells/mL) from 10³ to 10⁶, plus up to 60 Tetraselmis-size cells at 40x.
- Sampling uses a fixed seed per scene, zoom and hour.

**As built in Tee's tank** (`TestTank`, 60 days): about 14,000 harpacticoids (60 % of K), 1,300 cyclopoids, 950 amphipods, 3,000 ostracods, 85 bristleworms, 135 micro-snails, about 25 ciliates per mL and 100 phyto cells per mL. The torch test gives about 150 pods per 100 cm² of glass.

**Tests**: `MicrofaunaModelTest` implements the eleven §6.3 checks plus a Simulation/time-lapse integration test. Interpretations:
- **#1**: runs the mandarin on top of Tee's stock. "Exposed glass" means the night glass count.
- **#2**: measures adult calanoids.
- **#9**: uses a 20 per mL rotifer dose, the §5.2 dosed density. 20 per L of copepods cannot reach 500 captures a day with the §3.4 rates.
- **#10**: "saturation" means at least 90 % of 20 per cm².

**Tuned away from the research numbers** (est. values, to meet §6.3 together):

| Parameter | Research value | Used |
|---|---|---|
| Harpacticoid film Ks | 1 g m⁻² | 5 g m⁻² |
| Calanoid r_max | 0.45 d⁻¹ | 0.6 d⁻¹ (121 eggs per female lifetime supports it) |
| Calanoid capacity | 5 per L at 10⁴ cells/mL | Hill curve: about 10 per L at 5 × 10⁴, 130 per L at 2 × 10⁵ |
| Amphipod `T_lethal` | 34 °C | 36 °C, so §6.3 #7's 30-60 % loss at 35 °C holds |
| Cyclopoid `T_lethal` | 36 °C | 37 °C |
| Phyto clearance by benthic filter feeders | — | 1.8 d⁻¹ on top of 0.3 d⁻¹ export, so a dose clears in about 2 days |

## 14. Research 10-15 (behaviour detail)

Sources: [fish detail](research/10_fish_behavior_detail.md), [invertebrate detail](research/11_invertebrate_behavior_detail.md), [cleaner shrimp detail](research/12_cleaner_shrimp_detail.md), [master livestock research](research/15_master_livestock_research.md) (13 and 14 are source directories). Not to be confused with the older [research 10 microfauna](research/10_microfauna.md) in §13.

The research's main point: many older constants are design estimates, not sourced values. Behaviour is now modelled as encounter sequences that can fail, and it depends on context (light, molt state, size, intrusion) rather than fixed quotas or a single kill roll.

**Evidence labels.** `sim/Evidence.kt` has three levels: `SOURCED` (a direct study or species account of the taxon), `ANALOGY` (another species or setting, labelled as a transfer) and `DESIGN_ESTIMATE` (a provisional model choice). It also has a source-only `@Ev(level, ref)` tag on constants and `EvidenceLedger`, a list of rows readable at runtime (species, constant, value, level, reference, and what tank observation would test it). The project-wide ledger document is `docs/research/evidence_ledger.md`.

**Sandbox scenarios (off by default).** Both are `SetParam` ids, 1 = on, readable through `paramValue`, and copied into time-lapse forks.

| id | Flag | What it enables | Why it is off |
|---|---|---|---|
| `crab_predation` | `Simulation.crabLiveFishScenario` | Sally Lightfoot attacks on sleeping small fish, in the brain (`NightGrab`) and the 02:00 night rolls | *Percnon* is mainly a grazer; no live-fish capture rate is sourced (11 §3) |
| `asterina_coral` | `Simulation.asterinaCorallivoryScenario` | The old asterina → organ-pipe tissue loss | No species-confirmed corallivory for the owner's unidentified stars (11 §7) |

Snail predation by the crab and hermit snail-shell theft still need `predationEnabled` (sandbox). They are labelled design estimates.

### 14.1 Fish

| Species | Change |
|---|---|
| Ocellaris | Colour forms behave identically: `variant` is cosmetic only (test `clownColourFormsBehaveIdentically`). |
| Blue tang | New `ColumnPick`: holds in the current and snaps at passing zooplankton every 3–8 s. Open-water feeding now comes first and picking at surfaces second (`Graze` weight 1.5 → `GRAZE_WEIGHT` 1.0). `TangFeed` favours drifting particles. The alarm retreat to its refuge was already reversible. |
| Pajama cardinal | Foraging range follows the light ramp rather than a clock label: `forageRadius` is half of 40 cm at the 0.3 light threshold and the full 40 cm in darkness. It still takes nearby food by day. |
| Cleaner wrasse | The existing approach → tactile advert → pose (client can refuse) → inspect → jolt/complete sequence now keeps counts in `WrasseBrain.encounters`, with failed approaches kept in the denominator. `OVERDUE_S` is relabelled a design-estimate weighting, not a visit guarantee. Max length is now **14 cm TL** (FishBase; was 11). |
| Falco hawkfish | Scans, departures, landings and food strikes are counted as separate events (`HawkBrain.events`). A strike on a live animal (sandbox) goes approach → strike → miss (`STRIKE_CONTACT_P`) / injury / kill. A kill needs a soft shrimp (molted within 48 h) or prey under 0.5 × its own length. The old flat 40 % kill is gone. The 5–20 hops/h dwell is labelled a design estimate. Max length is now **8 cm TL** (was 7). |
| Lawnmower blenny | `matrixQuality` makes a rasp's food value come from the whole surface film matrix: rock film/diatom cover, hair algae and detritus. Before, it came from hair algae alone. Territorial response stays conditional on intrusion (`lmb_tailspot` 0.9, `lmb_tang` 0.15 per entry, Heron Island 90 % vs 20 %). |
| Tailspot blenny | Head-out inspection, then a short excursion, then a return to the hole mouth (`excursions` / `returns`). It fully retreats (head in) while threatened, freshly startled or timid. Fixed a bug: `PlanktonDash` used to be dropped the tick after it started. `MOVE_AWAY_CM` is labelled a design estimate. |
| Indigo dottyback | Behaviour is unchanged (cave ownership and patrol). The c07/c08 quotas ("≥ 3 chases in hour 1", "≤ 1/h after 7 days") are recorded as design estimates. |
| Engineer fish | Already modelled as pickup → transport → release, plus feeding by the burrow. `ADULT_CM` 15, the 18 cm feed radius and the 5 % night patrol are labelled design estimates (no code change). |

### 14.2 Invertebrates

| Species | Change |
|---|---|
| Banded coral shrimp | Cleans on **5.6 % of fish visits** (`CLEAN_PER_VISIT`, counted per arrival in `visitStats`), with no dance. Removes fewer parasites by day (`DAY_PARASITE_FACTOR` 0.3). Night forays follow `forayDrive`: light at the ledge × (1 − 0.6 shade), so dim light or shade lets it out by day. Intermolt is 25.5 d at 26 °C, with Q10 = 2 (`ShrimpBreeding.stenopusIntermoltDays`, ±8 %); it was 21–56 d. Mate recognition: opposite-sex shrimp that became familiar form a pair (`Organism.mateId`), and a recognised mate is never a target of the conspecific aggression rows, though a stranger still is. Mating follows the female's molt when her mate is within 30 cm, and the clutch incubates 20.5–25.1 d (mean ≈ 22.8). |
| Skunk cleaner shrimp | Leg-rock chance is 0.51 for predators and 0.12 for others, up to ×1.6 in the dark (`rockChance`). The sequence is advert → **antenna tap** (1–2 s, the client may leave: `tapWithdrawals`) → climb and clean. It cleans by day and at night (`nightCleans`). As a hermaphrodite, after a molt an adult spawns: the clutch is fertile if another adult is in the tank and infertile alone. Eggs are carried until the next molt, then hatch or are dropped. Molt state and egg state are tracked separately (`sim/ShrimpBreeding.kt`). Larvae never recruit. |
| Hermits | A smaller attacker wins a shell fight at most 8 % of the time (`SMALL_ATTACKER_WIN`). A defender that molted in the last 48 h loses 25 % more often; a freshly molted attacker wins half as often. Hermits molt every 30–60 d (design estimate). Counted separately: fights, wins, small-attacker fights and wins, and inspections of occupied snail shells (`occupiedShellInspections`). A kill happens only on the sandbox roll. Scarlet stays at 0.25 × blue-leg pushiness. |
| Sally Lightfoot | Grazing comes first. New `CarcassPick` scavenges dead animals, separate from predation (`carcassBites`, no `Eaten` event). Live-fish attacks only happen with `crab_predation`. |
| Nassarius | Buried → food cue → emerges after a latency of 20–240 s × (1.2 − hunger), so hungrier snails come out sooner → travels → a **feed bout** with the proboscis on the food (lognormal, mean 6 min, about 3/4 of bouts ≤ 8 min, after Lucena 2012) → reburies. |
| Astrea | Flipping is now loss of attachment, then a righting attempt every 10–40 min. Each attempt succeeds 12 % of the time on open sand and 35 % with rock or glass in reach. There is **no death timer** (and the old "always rights after 2–6 h" timer is gone). |
| Asterina | `CorrelatedWalk`: the heading persists with σ 0.35 rad turns every 4 s. Speed drops to 0.35× near food or a rich film, so it covers less ground there. Organ-pipe damage is opt-in (`asterina_coral`). Fission is kept as an analogy (fissiparous trade asterinids). |
| Brittle star | `ArmFeed`: the disc stays put while one arm reaches, loops round the item and carries it to the mouth (10 cm in 8–15 s, Chartok 1983). When it travels the disc never turns: `BrittleArms` makes a different arm the centre arm (§19.2). |
| Feather duster | Retraction, crown loss and tube abandonment are separate states (`InvertEcology.dusterState`). After 3 days crownless with health < 0.4, the worm leaves the tube: invisible, but alive. It re-settles above health 0.7. The 30–60 d regrowth is labelled a design estimate. |

### 14.3 Tests

New: `ai/BehaviourDetailTest.kt` (23 tests: evidence labels, sheet lengths, colour forms, banded visit rate / light / intermolt / pairing, Lysmata fertility, skunk tap and night cleaning, wrasse encounters, tang column feeding, cardinal ramp, hawk strike outcomes, lawnmower matrix, tailspot excursions, hermit size rule, crab scenario and scavenging, nassarius latency and bouts, astrea righting, asterina CRW, brittle arm feeding, duster states).

Changed: `AsterinaOrganPipeTest` now turns on `asterina_coral` explicitly, because research 11 §7 contradicts the default-on premise, and it adds `corallivoryIsOffByDefault`. `RealismAcceptanceTest` is unchanged.

## 15. Locomotion model (`sim/MotionProfile.kt`, `ai/Steering.kt`, `shaders/fish.vert`)

Every animal now moves by a per-species **motion profile** (`MotionProfiles.of(brainKind)`): data in core that
both the steering and the renderer read. Behaviours still say *where* to go; the profile decides *how* the body
gets there. Evidence tags: **[M]** a source states it, **[L]** inferred from the research notes, **DESIGN
ESTIMATE** a tuning value. YouTube was not reachable; the per-species facts come from `docs/research/video/*.md`
(page sources listed there) plus the published locomotion papers below.

### 15.1 Gaits

| Gait | Species | What drives the body |
|---|---|---|
| `LABRIFORM` | clown, tang, cleaner wrasse | Pectoral rowing at slow speed; the tail takes over past the gait-transition speed `U(p-c)` |
| `BODY_CAUDAL` | pajama cardinal, dottyback | Tail beats with beat-and-glide bouts |
| `ANGUILLIFORM` | engineer goby (and moray) | Whole-body wave, low amplitude |
| `PERCH_HOP` | Falco hawkfish, lawnmower and tailspot blennies | No swim bladder: perches, short hops with a tail flick, sinks when it stops swimming |
| `WALK` | shrimp, hermits, Sally crab | Leg walking at fixed cm/s with stop-go pauses; tail-flip escape for shrimp |
| `ARM_ROW` | brittle star | Leading arm forward, the side arms row in mirrored strokes |
| `GLIDE` | nassarius, astrea, asterina | Foot or tube-foot gliding at fixed cm/s |

### 15.2 Stroke kinematics (`MotionProfile.stroke(bl)`)

- **Tail-beat frequency** rises linearly with speed (Bainbridge 1958, J Exp Biol 35:129, `U = L(3f − 4)/4`):
  `f = tailHzMin + (St / tailPP) · U/L`, with a Strouhal number of 0.25–0.35 (cruising fish sit at
  St 0.2–0.4: Taylor, Nudds & Thomas 2003, Nature 425:707). **[M]** for the law, DESIGN ESTIMATE per species.
- **Tail amplitude** (peak to peak) about 0.2 L once the beat is above ~5 Hz (Bainbridge 1958), smaller at slow beats.
- **Labriform swimmers**: pectoral frequency climbs linearly with speed up to the gait-transition speed, and the
  stroke sweep plateaus near it. Past the transition the caudal fin joins in (Drucker & Jensen 1996, J Exp Biol
  199:2235; their `U(p-c)` is size-dependent, ~1.8 L/s for a 23 cm surfperch). The share of caudal thrust
  (`caudalShare`) blends pectoral rowing into body-caudal beats between 0.75 and 1.05 × `U(p-c)`. Clown 1.8 L/s,
  tang 2.2, wrasse 3.0 (DESIGN ESTIMATES from the research notes' cruise and burst speeds).
- **Beat-and-glide** (Videler & Weihs 1982, J Exp Biol 97:169): cardinal (0.8 s glides after 2 beats), dottyback,
  hawkfish and blennies. Thrust surges in the beat bout, the body coasts straight in the glide. The bout timing
  uses a deterministic jitter (no extra random draws).
- **Hover**: labriform fish hold station by sculling the pectorals while the body barely moves.
- **Turning and banking**: the roll into a turn scales with `bankGain`; clowns add a ±5° rolling waddle at 1.3 Hz,
  and wrasse a ±3° pitch bob at 2 Hz (research notes [L]).
- **Escape C-start** (Domenici & Blake 1997, J Exp Biol 200:1165): when a flee starts at full urgency the head
  snaps away from the threat by up to `cStartDeg`. Stage 1 curls the body into a C (`OrganismPose.bend`), stage 2
  is the return stroke. Real stage 1 lasts 20–40 ms; here it is stretched to 0.12 s so that it spans two sim ticks.
  Shrimp make a train of ventral tail-flips (~8 Hz for 0.5 s) instead.
- **No swim bladder**: hawkfish and blennies sink at 1.5–2.5 cm/s whenever they stop swimming (Reef Builders on
  *C. falco* [M]; combtooth blennies outside the fangblenny clade lack a swim bladder [M]).

### 15.3 What the renderer gets

`OrganismPose` gained `pecPhase` (stroke phase in cycles), `pecAmp` (stroke sweep), `bend` (C-start curl, + toward
the fish's right side) and `motionMode` (0 fish, 1 brittle-star arm rowing, 2 shrimp tail-flip). `FishRig`
interpolates them and `fish.vert` reads them as `uMotion`:

- Pectorals row fore and aft with a feathering lift, driven by the sim phase instead of a fixed 7 Hz flutter.
- The C-start bends the head and tail to one side and the middle to the other.
- Shrimp flex the abdomen down during a tail-flip.
- Brittle star: the arm along +X leads and stays still, the side arms sweep in mirrored strokes in the disc plane,
  and the trailing arms drag (Astley 2012, J Exp Biol 215:1923: ~2 s per stroke cycle). This is now the fallback
  for a model without limb pivots; the rebuilt star and the Sally Lightfoot use the limb rig (§19), and
  `motionMode` 3 marks the crab.

### 15.4 Behaviour states that are mostly motion

| Species | State | Rule |
|---|---|---|
| Blue tang | `PlayDead` | A hard startle (tap or attack, magnitude > 0.35) makes a juvenile flip onto its side (~85° over 0.4 s) and lie rigid and 20 % paler for 3–8 s, settling onto rock or sinking slowly, with p = 0.4 for juveniles and 0.05 for adults (research video fish_blue_tang [S1][M]; the odds are a DESIGN ESTIMATE). Counted in `TangBrain.playDeadCount`. |
| Blue tang | `TangSleep` | At night it lies over ~70° in its crevice with its back toward the rock (observed 30–90° [S3][M]). The roll builds over 20 s. |
| Clownfish | `HoverHost` | A host-wiggle bout every 20–60 s for 2–4 s: the fish drops onto the host and shimmies at 3 Hz with a rolling body. Labelled "Wiggling in the host". |
| Cleaner wrasse | `RunStation` | Greeting dance for an unfamiliar fish within 15 cm, and advertising when clients are within 35 cm: the rear see-saws ±20° at 2 Hz with wide tail beats for 2–4 s ([S1][S2][M]). |
| Clients | `PoseForCleaner` | They freeze head-up with fins spread, mouth open and gill covers flared, and only the pectorals scull. |
| Engineer goby | `InBurrow`, `Excavate` | It peeks with only the head out, swivelling its head and eyes every 1.5–4 s. It carries sand head-down with full cheeks and spits it with a 6 Hz head shake. A juvenile makes a dig trip every 25–120 s ("every 20–40 s" while working [S2][M]). |
| Falco hawkfish | `PerchHop`, `DuckToPerch` | It sits propped head-up ~15° on its pectorals ([S1][M]). A startle sends it in a burst to the perch farthest from the threat, where it clamps on instead of hovering in cover. |
| Lawnmower blenny | `RockGraze` | Ballistic hops between patches at ~3 L/s (0.25–1.2 s arcs, a tail flick at take-off, fins folded in the air). Targets more than 3 L away are crawled to instead. Between rasps it sits propped head-up 15°, and the head dips for each bite. |
| Dottyback | `CaveDart` | It pauses in each crevice mouth facing out before the next dart. |
| Nassarius | `Buried` | Fully buried, the shell lies 0.15 L under the sand with the siphon end tilted 60° up into the current. |
| All feeders | `Feed` | A particle it gets no closer to (by 1 cm in 8 s) is given up for 20 s. This fixes fish pinned against rock and glass chasing pods in the front corner. |

### 15.5 Hairy mushroom host (owner, 2026-10)

The clowns host a cluster of hairy green mushrooms (`rhodactis_sp`, *Rhodactis* sp., `CoralKind.MUSHROOM`). The
cluster sits where the `mushroom_hairy.glb` model is built: on top of the low purple-coralline R10 rock in front of
the right recess, under the shelf's front edge (resized tank, ≈ (81, 13, 21); evidence in docs/specs/tank_design.md
§1 v3 note: [P p14], [P full], night clip T84). The anchor `HOST_SHROOM` and its alias `coral_shroom_hairy` are that
snapped point, with `HOST_ARCH_CLOWNS` ~4 cm above it. The owner's
research docs on `claude/project-thread-r6d0sa` were not reachable when this was written. The specs below come
from the coordinator's summary of them.

- Both clowns' placement is `HOST_SHROOM`, which is also the `ClownBrain` default. Anything that resolves
  `"host"` (nesting in `Reproduction`, hosting, defending the host) uses it.
- On a flat host (`ClownBrain.flatHost`: the anchor name contains SHROOM, MUSHROOM or RHODACTIS) the pair stays
  within ~1.1 L of the cluster and holds its body centre 3.6–4.6 cm above the disc tops, which puts the belly about 1–3 cm
  over the tentacles. They do not weave up through branches. Wiggle bouts and night nestling sit at the
  lowest height, with the female lowest and central.
- The discs stay open at night: `CoralSim.nightExtensionOf` gives *Rhodactis* 0.9 night extension, against the
  mushroom-kind default.
- App: the species sheet maps to `models/mushroom_hairy.glb`, which ships, with its Polyps node drawn as coral
  polyps. If the file were ever missing, `TankRenderer.coralModels` would draw `mushroom.glb`'s main clump moved
  to the colony's anchor (`ModelRegistry.FALLBACK_MODELS`). A node named
  `Polyps` with an instance table draws as coral polyps even without a `part` tag.

### 15.6 Tests

`ai/MotionTest.kt`:

- Stroke laws: tail Hz rises with speed, Strouhal stays 0.15–0.45, and the labriform gait transition holds.
- Both clowns spend ≥ 80 % of an hour within 15 cm of the hairy mushroom, and `"host"` resolves to it.
- The hawkfish is attached ≥ 85 % of the time, and its longest free-swimming bout is ≤ 25 s.
- The cardinal covers more ground in 15 min at night than by day.
- The tang lies rolled > 50° while asleep.
- A tapped juvenile tang plays dead, rolled > 60°, for ≥ 3 s.

## 16. Dynamic sand bed and the diamond goby (`sim/SandBed.kt`, `ai/DiamondGobyBrain.kt`, `render/SandVisuals.kt`)

Sources: [research 16, diamond goby](research/16_diamond_goby.md). The sand used to be a static mesh with no state. It is now a coarse grid that the animals change, and the renderer shows those changes.

### 16.1 The grid

`SandBed` is 128 × 48 cells over the 121.9 × 45.7 cm floor (≈ 0.95 cm square; 64 × 24 before §16.11). Each cell holds three values:

- **height**: an offset in cm from the modelled bed. It is clamped to −3 (pit) … +4 (mound) on open sand; under rock it can be dug almost to the glass (§16.8).
- **freshness**: 0…1. Turned sand is the damp, darker layer from below. It fades with τ = 6 h.
- **detritus**: a mulm index of 0…1.5. It starts at 0.25 and relaxes over 2 days toward the water-column detritus (less in strong flow).

The bed's surface is `baseY(z) + height`, where `baseY` follows the visual bed: 3.5 cm at the front glass and 6 cm at the back. Edits conserve sand:

- `dig` and `deposit` move volume.
- `plough` cuts a mass-conserving furrow with berms. A groove narrower than half a cell is widened to that width and kept at the same volume.
- `freshen` only turns the surface.
- `sift` removes a fraction of the detritus.

`relax` runs once per chemistry tick:

- Slopes steeper than the angle of repose (tan 0.62, ≈ 32°) slump.
- Diffusion grows with pump flow (`D = 1e-5 + 4e-5·f²` cm²/s), so strong flow erases ripples and trails within hours, while a still bed keeps a pit for days.

All of this uses its own `Rng`, so the behaviour random streams and the existing tests are unchanged.

### 16.2 Who moves sand

`SandWork.step` runs after every behaviour tick. It only reads what the animals actually did: their positions and postures. It never changes their behaviour.

| Animal | What it does to the bed |
|---|---|
| Nassarius | Ploughs a trail every 0.6 cm crawled on sand (0.1 cm deep). Burying or emerging (posture 5) leaves a crater with a spoil ring and a small puff. |
| Hermits, Sally Lightfoot | Scratch: freshen plus a faint furrow every cm walked, and a puff every 12 marks. |
| Brittle star | A shallow, wide furrow where its arms drag. |
| Engineer goby | Digs tunnels under the rock mouthful by mouthful and spits the spoil at the entrance (§16.8). In abstract mode one mouthful every ~3 daylight minutes. |
| Diamond goby | Scoops pits at its feeding stations, puffs sand and fines out of its gills, digs its burrow, and sifts detritus away (§16.5). |
| Leopard wrasse | Digs a body-sized slot at dusk, throws the sand to a rim, pulls it back over itself, and bursts out after lights-on (§16.10). |
| Pump flow | Smooths the bed (§16.1) and refills burrows (§16.8). |
| Every body | Rests on the live surface and pushes sand aside where it touches it (§16.9). |

Teleports (more than 6 cm in one tick) are ignored.

### 16.3 Suspended clouds

`SandBed.puff` throws up to 160 pooled particles of four grain classes. The mix depends on how much detritus the cell holds. They settle at the Ferguson & Church (2004) velocity for aragonite in seawater (2.93 / 1.023 g/cm³, ν = 0.95e-6 m²/s):

| Grain | Diameter | Settling speed |
|---|---|---|
| Sand | 0.5 mm | ≈ 7.8 cm/s |
| Fine | 0.2 mm | ≈ 2.7 cm/s |
| Silt | 63 µm | ≈ 0.35 cm/s |
| Detritus floc | 0.3 mm, 1.10 g/cm³ | ≈ 0.3 cm/s (inside the 16–368 m/day marine-snow range) |

Particle motion:

- The jet relaxes in 0.25 s.
- Particles then drift with 0.6 × the local flow, plus turbulent vertical diffusion that grows with flow speed.
- A particle that reaches the bed re-joins its cell: sand adds height and detritus adds load.

The resulting timing: a goby's puff 5 cm up drops its sand in ≈ 1–2 s, while the haze of silt and mulm takes 10–60 s (longer in strong flow). Particles left up after 240 s count as resuspended.

### 16.4 Coupling to chemistry

Detritus that stays in suspension can reach the filtration. Once per chemistry tick, `WaterSim.captureResuspended` takes a small, capped fraction of the column detritus (≤ 0.02 % per minute, ≤ 5 % per call) into the filter sock load and nitrogen export. It does this only when a sock or skimmer is running. Sifting itself only moves the bed's own mulm index. Food for the sifter comes through the existing microfauna pathway (§16.5), so nitrogen bookkeeping is unchanged.

### 16.5 Diamond goby (*Valenciennea puellaris*, sandbox only)

The diamond goby is not in the owner's tank. It is in the stocking gallery (`Stocking.CATALOG`) and the sandbox "Add life" list. Its sheet is `species/valenciennea_puellaris.json`: max 16 cm, typical 12 cm, minimum 55 gal, reef safe "with caution". The model is `models/diamond_goby.glb` (+ LOD1).

**Brain** (`DiamondGobyBrain`):

- **Sift.** The main daytime behaviour, in four phases:
  - travel to a station on open sand within 35 cm of the burrow;
  - scoop: head down for 0.6 s, digging 0.35 cm³ and sifting the cell's detritus;
  - chew: 1.5–3.5 s with flared gills;
  - expel: a puff out of the gills, the sand dropped beside the pit, any coral within 7 cm and less than 8 cm above the bed dusted (it retracts; `coralsDusted`, `SandBed.coralDustings`).

  Each station gets 3–8 mouthfuls, then the goby hops 8–25 cm to the next one.
- **DigBurrow.** Every 30–90 minutes by day it carries 3–6 loads out of a burrow under a rock overhang and dumps them as a mound.
- **InBurrow.** At night it hides in the burrow (invisible) and flees there when scared.
- **BottomFeed.** If it accepts prepared food (60 % do), it takes food that has sunk to the sand.
- **Pairs.** A second diamond goby pairs with an unpaired one. The pair shares the burrow and forages within 14 cm of each other.

**Food.** `MicrofaunaModel.sandPreyIndex()` is the SAND-habitat pod biomass, weighted by the new `PodPredator.SAND_SIFTER` row (1,500 pod-eq a day, sand access 1, hidden 0.25). It is 1 for a mature bed at its starting population and 0 for dry rock. Each mouthful offsets `SIFT_YIELD_MIN × index` minutes of hunger rise. In abstract mode the relief is `ABSTRACT_RELIEF × index` per daylight minute. On a 227 L bed with no refugium and no prepared food, the goby itself draws the sand fauna down. It holds condition for about 10 days, then starts to thin. On a sterile bed it is thin within the fortnight. Compatibility:

- `sand_sifter_tank_size` (WARN below 200 L), otherwise `sand_sifter_food` (INFO);
- `diamond_goby_pair` (WARN when adding a third, fifth, … one).

### 16.6 Snapshot and renderer

`SimSnapshot.sandGrid` (`SandGridSnapshot`: dims, cell size, height/fresh/detritus arrays, version) is rebuilt at most every 0.25 s × speed. It is shared until the bed changes. `SimSnapshot.sandClouds` holds the live cloud particles at stride 4 (x, y, z, kind + age/60).

`SandGridSnapshot.cavity` (0…1 per cell) marks sand dug out from under rock: the gap over 2 cm, more where it was dug beyond the bed's starting gap.

In the app:

- `SandFx` packs the grid into a 64 × 24 RGBA16F texture on sampler unit 13 (`uSandMap`). It uploads only on a version change, at most 5×/s.
- `static.vert` (`SAND`) lifts the top vertices of the sand by the height.
- `rock.frag` (`SAND`) takes the slope from the height gradient, darkens and slightly glosses freshly turned sand, tints mulm brown, brightens freshly sifted patches, and shadows the floor of dug burrows (alpha = cavity).
- Dug tunnels need no extra geometry: the sand mesh under the rock is lowered by the height, which opens the gap to the rock's underside. Spoil piles are the same lift upward, and each spit throws a `puff` that the cloud renderer draws.
- `SandCloudRenderer` draws each cloud particle as three jittered points with the particles program. Sand is a pale burst, silt a faint spreading haze, detritus brown flecks.

The CPU cost is O(cells) per chemistry tick plus O(particles ≤ 160) per step.

### 16.7 Tests

`sim/SandBedTest.kt`:

- settling speeds against Ferguson & Church;
- clouds settle (coarse sand < 3.5 s, everything 10–60 s, sand conserved);
- flow keeps detritus up longer;
- relax smooths faster in strong flow and freshness fades;
- nassarius trails and burial craters, both direct and fed in a live run;
- the engineer goby's pit and mound;
- the diamond goby: catalogue and compatibility, sifting (mouthfuls, puffs, stations, sifted load, level bed), pair burrow, dusting of low corals, decline without sand fauna, and the prey index on a sterile bed.

`sim/SandBurrowTest.kt`:

- the rock roof read from the SDF;
- mass conservation through mouthfuls, spits, plumes, bow waves, impressions and three days of `relax`;
- an undug burrow does not fit the body; it fits to its length once dug, and refills over days when left alone;
- a live goby on a fresh bed never has its head beyond the dug face, and its burrow gets longer;
- no head (and no spine midpoint, 0.3 cm tolerance) below the sand surface unless burrowing, over 30 min of the full tank at dusk; contacts moved sand;
- burrows and the bed carry over into a time-lapse fork.

### 16.8 Tunnels under rock and the engineer goby's burrows

Sources: [research video fish_engineer_goby](research/video/fish_engineer_goby.md), [sand_bioturbation](research/video/sand_bioturbation.md); Wikipedia (*Pholidichthys leucotaenia*: adults excavate with the mouth, "eject mouthfuls of debris", a colony's maze of tunnels ≈ 6 m); Shedd Aquarium (scoop up sand or gravel and spit it at the tunnel entrance); hobbyist guides (rock resting on sand rather than glass gets undermined; they dig and re-dig constantly). No footage was watched (YouTube blocked).

**Roof.** `SandBed.attachRock(sdf)` sphere-traces each cell's centre column from 0.5 cm above the glass. The first rock hit within `ROOF_RANGE` (8 cm) over the modelled bed is the cell's `roof`. Rock at the bottom marks the cell `SOLID` (nothing to dig under); no rock marks it `OPEN`. A roofed cell can be dug to 0.5 cm above the glass (`floorH`).

- `gapAt(x, z)` = roof − live surface (∞ in open water, 0 where sand meets rock).
- `fits(x, z, dir, bodyH, bodyW)` needs the gap ≥ body height at the midline and at both flanks (± half the width across the heading).
- `maxGapAt` is the most a gap could ever open (roof − floor).

**Mass.** `takeMouthful` takes sand from the cell that holds the surface up most at that point. It never goes below the floor, and the sand is counted as `carriedCm3` until `spit` drops it. `spit` deposits it with `depositExact`, which spills to a wider ring instead of losing sand to the mound clamp, and throws a plume. `sandVolumeCm3()` is the grid plus suspended sand plus carried sand. It stays constant through digging, spitting, clouds, bow waves, impressions, slumping, smoothing and infill. Puffs never take sand a cell does not have, grains landing are never clamped away, and coarse grains that time out drop where they are.

**Burrows.** A `Burrow` is a straight tunnel from a mouth at the rock edge into the rock. The live gap is not stored: `reach(b, h, w)` walks it in 0.4 cm steps. `nextDig` gives the next mouthful:

1. the face (the first point where the body does not fit);
2. any stretch lower than the chamber height (`CHAMBER` 1.4 × the fit height), i.e. sand that trickled back;
3. one more cm of tunnel, up to 1.6 body lengths, while the rock allows;
4. the mouth apron.

**Refill.** `relax` slumps sand under rock only past tan 2.0 (≈ 63°; roof-confined, mucus-bound walls). Every neighbour pair with a roofed cell evens out with τ = 3 days at 1× flow (faster in strong flow). A tunnel left alone shallows by about a third in three days, so the goby must keep digging.

**GobyBrain.** The fit height is body depth × 1.15; the width is 0.65 × that.

- **Burrows.** It plans a primary burrow from `BURROW_GOBY` along its direction (1.2 body lengths), plus up to two rock-edge sites within 24 cm (`burrowSites`) that it can swim to in a straight line. Fish built with the tank have the primary burrow already open to its length (an established home, sand moved to the spoil pile). The others start undug. `burrowSites` ranks sites by the sand still to move (the mean shortfall of the gap below the body height along the tunnel), then by length. A site where the rock foot sits centimetres deep in the sand needs a pit that slumps back in about as fast as one goby can carry it out. In Tee's 48 x 18 x 16 in layout the `BURROW_GOBY` anchor is a natural cave a 10 cm goby already fits (2–3 cm under the rock), so the digging happens at the extra sites.
- **Excavate** (trip timing unchanged: adults every 0.7–2 h, juveniles every 25–120 s):
  1. swims to the mouth;
  2. slides in head first at 0.5 BL/s, only as far as `reach`;
  3. digs at the face for 2–5 s with the head in the sand (`Organism.inSand`) and the body wriggling;
  4. takes a mouthful of 0.6 cm³ × (L/10 cm)², backs out at 0.7 BL/s still facing in, turns, swims to the spoil pile about half a body length outside the entrance, and spits (mound plus plume, `Simulation.spitSand`). If interrupted, it drops what it carries.
- **InBurrow.** By day the head peeks out with the body lying in the tunnel. At night the head is deep and facing in. Both are limited to the dug length; with a short tunnel more of the body stays outside.
- The rare night patrol, the hover and the flee are unchanged.

### 16.9 Bodies against the sand (`sim/SandContact.kt`)

After every behaviour tick (and in abstract placement), `SandContact.step` lifts any body whose reference point is below `surfaceY + clearance`, where clearance is half the body depth for fish and 0.15 cm for crawlers. It also clears downward velocity and re-anchors the spline head. `Steering.crawl` still re-attaches crawlers to the flat `RockData.sandTopCm` plane, so on sand the clamp lifts them back onto the live surface each tick (the collision layer is the place to make the walk itself read `surfaceY`). `Ctx.safe` lifts a target point that lies inside the live sand to `surfaceY` + 0.2 cm, so no swimmer is sent to a point it cannot reach. Points above the sand are left alone: an earlier floor of `surfaceY` + body clearance also moved cave, cover and station picks, and the dottyback then spent about 40 % of its time visiting the cleaner instead of patrolling (c20). Drifting plankton (`FoodKind.suspended`) is kept over the live sand in the same way, because the flat `clampToWater` floor left copepods 1.6 cm inside the bed where the diamond goby kept going for them. A lawnmower blenny perched low on a rock face lies along the face, because head-up there would hang its body into the sand. A clown coming back to its host with rock in the way goes over the rock (`HoverHost`), not into it. `viaOpenWater` no longer returns the swimmer's own position when the way over is blocked at its height: it tries a higher crossing, a doorway, then a way out of the pocket, and only then holds (a cardinal had held still under a ledge for 90 min on its way to the cleaner). A leopard wrasse picks sleep sand with open water above it and swims over the patch before dropping onto it. A client posing for a cleaner (`PoseForCleaner`) holds the spot where it began to pose and swims back to it if pushed more than 2 cm away. Before, it sought a point just ahead of itself, so separation from the cleaner working along its body pushed it off: a posing engineer goby was herded 25 cm along the front glass. The rest of the body follows: `SandContact` raises spline trail points that lie under the live surface by up to 6 cm/s (`BodySpline.liftTrail`), and while a body is drawn along an override path (den, burrow), its trail is kept on that path. When the override is let go, the body therefore eases out of the shape it was drawn in, not out of the head's track through the sand. A goby leaving its burrow had its middle 1.7 cm under the mound at the mouth. The diamond goby checks a sifting station's rock clearance at the height it hovers over the live sand, not at the flat plane; a station under a rock foot left it 0.3 cm off the rock when it swam off to feed (`AiTest.noRockPenetration`). An engineer goby away from its burrow mouth slides along the bed to it when the way is clear: the open-water swim holds a 20 cm body off the rock it lives against. A swimmer pinched between the sand and a rock overhang is moved aside by the sand, to the nearest spot on the live surface (within about the lift height) with its swim clearance to rock (0.45 L) and off the glass. Before, the steering pushed it out of the rock down onto the flat `sandTopCm` floor and the lift put it straight back each tick: a blue tang was frozen for 95 min in the gap under a ledge at the back glass, out of reach of the cleaners (`RealismAcceptance` c04). A client pushed off its pose spot gives way less to its neighbours (separation 0.25 while it swims back): separation grows as r/d near a cleaner nosing along its flank, which outran the way back. A cleaner shrimp climbing onto a client sets the client's `poseFor` to itself, and `VisitCleaner` keeps the station's client when it hands over to a pose for that cleaner. A stale `poseFor` naming the wrasse that last cleaned it had let the visit release the shrimp's station as the pose began, a clean-visit loop every 1.5 s (`FishGlitchTest`). The tang's patrol leaves a rock pocket through `viaOpenWater`, because a straight seek held it nose to the rock for minutes (`SubmarineTest`). The hawkfish's night landing and rest spots are on `surfaceY` (3.5 to 6 cm), not the flat 3 cm plane that the clamp lifted it off every tick.

Exempt (`SandContact.exempt`): posture 5 (buried nassarius, sleeping leopard wrasse, bobtail squid), `Organism.inSand` (a goby's head in its dig face), and the feather duster (its tube is set in the sand).

Contact moves sand (mass-conserving):

- **Bow wave.** Each 0.25 BL (≥ 0.6 cm) travelled in contact pushes a 0.2 mm layer over the body width (plus what a pressed-in body overlapped) 60 % ahead and 20 % to each side. Nassarius, hermits, the Sally Lightfoot and the brittle star keep their `SandWork` trails instead.
- **Impression.** After 20 s still in contact, a brittle star leaves one disc impression and a goby three along its body (0.12 cm deep, with a rim).

Wedged between rock and sand (a crevice at the rock foot, a diamond goby dashing into its burrow), the lift stops `ROCK_KEEP` (0.3 cm) short of the rock. The body then shoves the sand out from under itself (bow waves at least one cell wide, so the sand lands outside the cells the head reads). Sand that cannot move (bed at its floor) carries the head up inside that rock margin.

Swap point: when the shared collision layer lands (`BodyCollision.resolveEnv` reading `SandBed.surfaceY` live), `clamp` becomes its sand term and `exempt` its per-cell rule. The displacement half stays.

### 16.10 Sand physics shared by every species, and bodies buried in it

Every species moves sand through the same `SandBed` operations, and all of them conserve mass: `sandVolumeCm3()` (grid + suspended sand grains + mouthfuls carried) changes only when fines drift out as silt.

- **Transfer.** `takeExact`/`depositExact` (spill to wider rings past `MAX_MOUND`), `takeMouthful`/`spit` (§16.8), `bowWave` and `impress` (§16.9), `buryStep`/`emergeFrom` (below). No take goes below a cell's floor.
- **Angle of repose.** `relax` slumps any step steeper than tan 32° (open sand) or tan 63° (roofed tunnel walls). It works on the *visible* surface, so a buried body's volume holds its cover up.
- **Flow.** Diffusion grows with pump flow (ripples and pits flatten over hours to days). Roofed cells infill slowly (§16.8).
- **Suspension.** A puff lifts grains out of the cell (sand where there is sand, silt at the floor). They drift with the local flow and settle back by fall speed. A sand grain that lands or times out goes back into the grid where it is.
- **Brains.** Anything that rests on, burrows in or sleeps under the sand reads `SandBed.surfaceY`, not the flat `RockData.sandTopCm`: the gobies, nassarius rest and burial, brittle star rest, an overturned snail, the bobtail squid's fallback bed, the leopard wrasse, and food settling (`FoodSystem.groundY`). The renderer's sand mesh is coarser than this grid, so a pit can look slightly shallower than the body in it.

**Bodies in the sand (`SandBed.BuriedBody`).** A body lying in the sand is a slot of cells along its length. `inclusion` holds the body's own volume as height per cell. It lifts `surfaceY` and the rendered surface but is not sand.

- `canBury(head, dir, len, width, thick, cover)`: open sand only (no rock over it, no other body in it), and deep enough along the whole slot for thick + cover above a floor 0.5 cm over the glass. The 3.5 cm front of the bed is too shallow for a 10 cm wrasse (1.6 cm thick + 2.2 cm cover). The back (up to 6 cm) is not.
- `buryStep(b, digTo, coverTo)`: digging lowers the slot by (thick + cover) × share and throws the sand to a rim: half ahead of the head, a quarter to each side. This makes a depression and a rim. The body volume goes in under the surface as it digs. Covering takes the rim sand back over the body. Exact transfer.
- `emergeFrom(owner)`: the body volume leaves and the cover over the head end is thrown aside. This leaves a dimple about as deep as the body was thick, with a low rim. `relax` slowly flattens it.

**Leopard wrasse (*Macropharyngodon bipartitus*).** It dives at dusk (light < 0.25, or lights out) at a sleep spot where `canBury` holds; spots on shallow or dug-out sand are skipped. It lies on its side: the slot is as wide as the body is deep (0.36 L), thick 0.45 × that, with cover `max(1, 3 − thick/2)` cm, so the body centre is 3 cm down (research: 2–5 cm).

- **Dive.** Lasts 1.05–3.06 s (SICB P2-191, *H. bivittatus*). Digging runs over the first 85 % and covering from 55 %. Small puffs rise as it wriggles and settle back.
- **Night.** It is placed at `BuriedBody.bodyY` under the surface it made.
- **Emergence.** 15–60 min after lights-on it bursts out (`emergeFrom` plus a puff) and rises from that depth.

Abstract mode (no dive) keeps it 3 cm under the live surface. Tests: `SandBurialTest`, `LeopardWrasseTest`.

### 16.11 Sand as grains: thrown, rolling and pouring (owner request 2026-10-15)

Tee asked for sand that moves as particles: Marina has to really bury herself, and Sandy has to pick sand up, clean it
and spit it out. The bed stays a heightfield (`surfaceY` is still the collision floor for every other system), but sand
that moves now travels as grains in between, and slopes fail as it happens.

- **Finer bed.** 128 × 48 cells (≈ 0.95 cm, research sand_bioturbation: "1 cm acceptable"). The renderer splits the sand
  mesh's top triangles once at load (`Mesh.subdivide`, ≈ 0.75 × 1 cm vertices) so a fish-sized slot or a mouthful's pit
  shows; the skirt down the glass stays as built.
- **Grains** (`launchGrains`, the hook for anything that digs or spits). Sand taken off the bed is thrown as pooled
  particles (≤ 1200 live, recycled after landing their sand) that each carry their share (a clump of at most 0.03 cm³).
  They ride the jet (τ 0.25 s), sink at their Ferguson–Church settling velocity (§16.3) and join the grid only where
  they stop, so a rim, a heap or a fish's cover is built by the grains that land on it. Mass-conserving:
  `sandVolumeCm3` counts grains in flight and sand held in mouths.
- **Rolling.** A grain landing on a face steeper than 0.85 × repose rolls on: submerged weight down the slope
  (g (ρs − ρw)/(ρs + ½ρw) ≈ 540 cm/s² for aragonite), Coulomb friction at the dynamic angle (tan 28°, research §3: an
  avalanche runs until ≤ ~28°) and water drag, until it stops on a gentler slope (≤ 2.5 s).
- **Slope failure as it happens** (`avalanche`, every step). Tiles of 8 × 8 cells that changed are watched for 1.5 s: any
  pair steeper than repose (tan 32°, tan 63° under rock) sheds its excess with τ 0.3 s, part of it as rolling grains,
  so a rim pours back into a slot and a spoil heap slumps while you watch. Same rule as `relax`'s first pass, so a
  bed left alone ends where it did before. Never below a floor.
- **Marina** (`buryStep(…, airborne = 0.35, wave)`): 35 % of what she digs flies up and out (70 % to the side her tail
  sweeps, from the rear two thirds where the body wave is biggest) and 35 % of the cover is thrown back from the rim
  onto her; the rest is laid as before, then the faces pour. Bursting out throws grains up around the hole. Share is
  a design estimate; timing stays SICB P2-191 (1.05–3.06 s).
- **Sandy** (diamond goby): nose down at 35° with the mouth 3.5 mm into the bed; `scoop` takes the mouthful (0.18 cm³,
  research S1) into `heldCm3`, a pit at the mouth, a few grains spilling from the mouth corners. While it chews, fines
  seep from both gill slits; then three gill pumps blow the held sand out of both gill openings (back and out), and it
  pours down beside and behind it. Interrupted, it drops what it holds.
- **Engineer goby.** A live spit lets 40 % of the load fall as grains onto its spoil heap.
- **Renderer.** `SandCloudRenderer` draws grains as lit specks in mixed aragonite tones (a clump as three), carried
  along their velocity from the older snapshot so they fall smoothly at 60 fps; the sim sends a new sand grid every
  0.1 s while grains fly or a slope pours (`SandBed.busy`), 0.25 s otherwise; `SandFx` uploads it at most every 80 ms.
- **Cost** (JVM, full stock, 20 Hz): +0.1 ms per tick during a dive, +0.2 ms per tick while Sandy sifts all day.

Tests: `SandGrainTest`.

## 17. Microfauna in the display tank (`sim/MicroCritters.kt`, `render/MicroCritterVisuals.kt`)

Sources: [research 10](research/10_microfauna.md) §2.2 diel behaviour and §5.3 motion primitives; [microfauna morphology](research/17_microfauna_morphology.md) (anatomy, colour in life, speeds; owner request 2026-10-13: "see some of the bigger microfauna in the actual tank and not just under the microscope").

**What shows.** `CritterKind` lists the animals big enough to see with the naked eye: Eurythoe-type bristleworms, gammarid amphipods, mysids, sphaeromatid isopods, harpacticoid pods on the front glass (only within ±12 cm of the camera's look-at point, they are ~1 mm), micro-snails, red planaria (only once the pest is present), spaghetti-worm tentacles, skeleton shrimp (caprellids) clinging to the turf on the rock tops, podocopid ostracods on the glass and free-swimming calanoids (*Parvocalanus*) hopping in the water, both only near the camera, and the sessile red foram *Homotrema rubrum* on shaded rock undersides. Asterina stars were already sim organisms; their model was rebuilt (fissiparous form, 6 unequal arms, granular mottled top).

**How many.** Expected animals out on each habitat = `MicrofaunaState.visible(group, scene)` (the model's density × diel exposure) × that habitat's area (`rockCm2`, `sandCm2`, `glassCm2`, new on the state). Shown = `cap · E / (E + K)` per kind (K is a visual half-saturation, est.), so a mature tank shows one or two worms and a few amphipods by day and many more an hour after lights-out. Settled food adds up to 3 worms by day (they come out to feed). Spaghetti worms: `0.5 × exposure` of the buried worms have their 5 tentacles out (more at night), plus one more while food lies on the sand.

**How they move** (wall-clock, ×sim speed up to 4):
- Bristleworm: comes out of a crevice head-first, gliding 8 mm/s along the rock or sand with a lateral body wave and a backward metachronal parapodial wave; turns toward settled food within 12 cm; after 40-200 s (or ~16 body lengths of travel) reverses down its own trail into the hole at 30 mm/s. The body follows the trail, so a worm half out of a hole looks it.
- Amphipod: stop-go scuttle (legs 6.5 Hz), rare side-swim bouts rolled 90° at 5 cm/s (pleopods 12 Hz), lands on the nearest rock or drops to the sand; flees into cover when a fish comes within 5 cm.
- Mysids: a loose swarm 3 cm off a cave mouth, 2.5 Hz positional jitter, heading into the main flow; a fish within 6 cm makes one tail-flip backward ~7 BL.
- Calanoids: 1-3 mm hops every 0.5-2 s biased upward toward the light, sinking ~0.7 mm/s between.
- Pods, isopods, snails, flatworms: correlated random walks on their surface with pauses (pods dart 2-5 mm).
- Skeleton shrimp: grip with the rear pereopods and stand off the rock, swaying as a 0.7 Hz pendulum in the flow with the gnathopods held up; let go and drop into the turf to hide.
- *Homotrema*: never moves; it appears and disappears only with the population.
- Spaghetti-worm tentacles: creep out over the sand at 1.2 mm/s along gently curving paths; pull back within ~2 s when a fish's head comes within 4 cm, then stay in for 20-60 s.
Hiding animals back into their surface and vanish; nothing is ever teleported in view.

**Rendering.** Each critter is a 16-station spine + frames, drawn with the organism shaders (the same spline skinning as fish and inverts), never picked or followed. Models are built by `tools/blender/microfauna.py`. Nearest first, skipping anything under ~3 px, until 12k (High) / 2k (Medium) triangles are spent; off on Low. `tools/validate_glb.py` counts that budget in its on-screen estimate.

**Accuracy audit** (2026-10-13): an independent review against research 10/17 checked sizes, speeds, beat rates and diel exposure; its findings (tentacle diel cycle, predator flight, mysid jitter amplitude, fireworm length up to 12 cm, calanoid antennule length, podocopid eye size, paired mysid eyes, missing water-column pods) are fixed here.

**Tests** (`MicroCrittersTest`): more worms and amphipods at night than by day from a mature tank's own model; they withdraw when the lights come on; every body has its length and sits on rock, sand or glass; tentacles retract from a passing fish.

## 18. Bacteria layer (`sim/BacteriaModel.kt`, research 18)

Source: [`docs/research/18_reef_bacteria.md`](research/18_reef_bacteria.md) ("Implementable model"). Most of its numbers are marked [K] (recalled, not checked), so here they are design estimates. Each constant carries an `@Ev` tag, and the main ones are in `EvidenceLedger` under `"bacteria"`. The app shows the evidence level on each guild row.

`WaterSim.bacteria` steps every 10 sim minutes, after the microfauna and before the gases. It is deterministic. Guilds:

| Guild | State | What it does to the tank |
|---|---|---|
| AOB / NOB | `WaterState.bacteriaAob/Nob` (unchanged, grown by `WaterSim.nitrogen`) | Rates and growth are multiplied by `aobFactor`/`nobFactor`: antibiotic (Kinh 0.5 / 1.0 mg/L, half-life 2 d) and copper (Kinh 1 mg/L). The antibiotic also kills 0.25/d × inhibition. Bottled nitrifiers add 5 % of a mature bed × 0.3-0.6 establishment (more with food) over ~2 days. |
| AOA | `aoa` 0..1 | Up to 0.002 ppm NH3/h, Ks 0.0017 ppm NH3 (0.1 µM). In a normal tank this share is inside the tuned AOB rates (AOB dominate reef rock), so nothing extra is applied and the default tank is unchanged. Antibiotics do not touch archaea, so AOA keep oxidising ammonia (rate × AOB inhibition) while the bacteria are held back. Comammox is off (marine). |
| Heterotrophs | `hetBase` (resident, follows DOC), `hetC/N/P` (grown on dosed carbon), `freeCells` | Natural DOC breakdown stays `WaterSim.DOC_DECAY`. Its N and P are already in the food budget, so it is not counted again. Carbon dosing feeds a separate labile pool. Uptake is 5/d per unit biomass. Yield is 0.4 × nutrient limit; the rest is respired at 2.67 mg O2/mg C. N comes from NH3 first, then NO3. C:N:P is 50:10:1 replete, flexible to C:P 500 when P-starved. New cells are skimmed (0.01/h) or die (0.2/d) back to detritus (N) and PO4. Free count = 5×10⁵ × resident + 10 % of the carbon-fed cells (20 fg C each). Above ~10⁷/mL the water is cloudy (`clarity` −15 %). UV strips the column at 1/h; the biofilm is untouched. |
| Denitrifiers | `denit` (1 = mature reference bed), `anoxicMaturity` | Oxic layer 2 cm (thinner with detritus, deeper with sand sifters). Anoxic sand + rock interiors (50 %) set capacity. The reference 3 cm bed's denitrification is already inside WaterSim's tuned NO3 export, so only the extra is applied: a deeper bed (0.117 ppm NO3/day per unit above 1), and 25 % of dosed carbon reaching anoxic zones at C:N 5. |
| Sulfate reducers | `srb`, `sulfideStore` | Below 1 cm of anoxic sand. Store 0.15 mg/L per cm, built over ~2-3 weeks. Stirring (`STIR_SAND`) releases 80 % over ~20 min into `WaterState.h2s`, which oxidises at ~1.4/h (O2 cost 1.9 mg/mg). It also re-oxygenates the bed. A 5 cm bed gives ~0.12 mg/L. Tee's 3 cm bed and a 2 cm bed give none. |
| Cyanobacteria | `WaterState.cyano` (AlgaeModel) | The antibiotic adds 0.8/d × inhibition die-back (`AlgaeModel.cyanoExtraDeathPerDay`). |
| Pathogens (Vibrio-type) | `vibrioLog` (log10 cells/mL, base 3) | Relaxes (0.6/d up, 0.4/d down) toward 3 + 2.5·heat(27→30 °C) + organics (DOC > 1.5, detritus > 0.4, NO2) + low O2 + bloom + young community − 1.5·probiotic − 0.7·UV − 2·antibiotic. `pathogenPressure` (from 3×10³/mL) adds `HealthModel.PATHOGEN_ONSET` to the BACTERIAL onset and drives its growth. `coralPathogenStress` (≥ 10⁴/mL and > 28 °C) adds coral stress and tissue loss in `CoralSim`. Research 18's µ = 0.5/d cannot meet its own "10× in 3 days at 29 °C" check, so the rate is chosen to meet the check. |
| Biofilm | `WaterState.microbes` | The antibiotic strips it (0.15/d × inhibition), so a medicated tank also loses its dinoflagellate guard (09 A2). |
| Probiotic | `probiotic` 0..1 (10⁶/mL) | +0.3 per dose, decays 0.25/d (no bloom, 17 §6.2), and competes with the pathogens. |

H2S also enters fish and invert `envStress` (`HealthModel.H2S_FISH`: opt 0.005, tol 0.03, lethal 0.2 mg/L) and coral stress (`CoralSim.H2S_CORAL`).

**N accounting.** Uptake into cells and N2 gas count as export. Dead cells returned to detritus count as input, the same as the algae. The §8.1 balance holds within 1 %.

**Sandbox.** `Command.Bacteria(BacteriaAction, amount)` takes these actions: dose nitrifiers, start or stop carbon dosing (2 mg C/L/day × amount), antibiotic (3 mg/L), UV on or off (the same `WaterSim.uv` the treatments use), stir the sand, and dose probiotic. SetParam ids: `carbondose`, `sanddepth`, `antibiotic`, `h2s`, `probiotic`. `SimSnapshot.bacteria` (`BacteriaState`) lists each guild with its level 0..1, trend per day, amount, and a plain-language effect. In the app, a "Bacteria" chip on the microscope header opens `BacteriaPanel` (`ui/live/BacteriaPanel.kt`); its actions show only in the sandbox.

**Tests** (`BacteriaModelTest`, research 18 §E):

- **New tank cycle.** The tuned cycle finishes at ~3.3 weeks on this light ghost-feed, so the check is 3-6 weeks. Research says 4-6 weeks. A bottle speeds it up.
- **Carbon dosing.** 2 mg C/L/day lowers NO3 by about 0.6 mg N/L a week and draws PO4 down. The effect is smaller in low-nitrate water. Without the skimmer the cells pile up instead.
- **Overdose.** 8 mg C/L/day for 5 days gives > 10⁷ cells/mL and a pre-dawn O2 ≥ 1 mg/L lower. With the pumps off, the bloom clears after dosing stops.
- **Antibiotic.** Nitrification stalls, then recovers.
- **Stirred sand.** A 5 cm bed gives 0.1-0.5 mg/L H2S. A 2 or 3 cm bed gives < 0.05.
- **Pathogens.** Vibrio stays below 10⁴ at 26 °C and rises ≥ 10× in 3 days at 29 °C. Warm, dirty water gives pressure on fish and corals, and probiotic plus UV push it down.
- **UV.** Free cells fall by 50-90 % while the nitrifiers are unchanged.
- **Nitrogen.** The mass balance holds.
- **Mature tank.** Tee's tank stays stable for 30 days.

The research-17 E.8 cyanobacteria check is the AlgaeModel's own (research 09).

**Default tank unchanged.** A mature tank can have no dose, no antibiotic, no copper and no stirring, with water below ~27 °C and normal O2. In that tank every coupling is exactly 0 or 1, so the existing chemistry and behaviour runs come out the same. A young community (low `microbes`), low O2 or dirty water can still raise the pathogen pressure a little. Weeks at 31 °C now also raise the Vibrio risk, so `RealismAcceptanceTest.noIch` clears bacterial infections as well as ich, to keep c25 about acclimation alone.
## 19. Limb rig: brittle star arms, Sally Lightfoot legs and shrimp limb signals (`sim/LimbRig.kt`, `shaders/fish.vert`)

Sources: [research 17, brittle star and Sally Lightfoot](research/17_brittle_star_sally_crab.md). The two many-limbed crawlers no longer use the generic leg sweep. The sim owns each limb's pose, meaning which arm leads, which legs are in stance and where each claw is in its stroke. The renderer turns that pose into vertex motion. There are no skins or bones: each limb is posed about a pivot stored in the model.

### 19.1 Data path

- **Core.** `Organism.limbRig` is a `LimbRig.forKind`: `BrittleArms` for `BRITTLE_STAR`, `CrabLegs` for `SALLY_CRAB`, `ShrimpLimbs` for `BANDED_SHRIMP` and `CLEANER_SHRIMP`, and null for every other kind. This is the one channel for limb pose from the sim to the renderer.
  - `Steering.animate` calls `limbRig.step(o, dt, speed)` once per tick.
  - `Steering.crawl` writes the intended travel to `limbRig.travel` and multiplies the crawl velocity by `limbRig.pulse`. The disc moves in pulses; the crab's pulse is a steady 1.
- **Pose.** `OrganismPose.limbs` holds `LimbRig.count` vec4s: 40 for the brittle star (8 per arm, §19.7), 10 for the Sally Lightfoot, 15 for the hermit, 1 for the shrimp, empty for other kinds. Component x is a phase in [0, 1) or a slow scalar kept inside (0, 1); y, z and w are plain scalars.
  - `motionMode` is 1 for the brittle star and 3 for the crab.
  - Both bodies are now `rigid` splines. The disc never bends or turns with its path.
- **App.**
  - `FishRig.limbs` interpolates the vec4s (`limbCount` of them). x is interpolated across the 1 → 0 wrap.
  - `DrawPart.limbPivots` / `GpuModel.limbPivots` read the node extras `limbPivots`: 10 × vec4 in glTF model space, xyz = limb root and w = rest length.
  - `TankRenderer.fishUniforms` uploads every entry to `uLimb[40]` (uLimb[0] is zeroed when the pose has none) and sets `uLimbOn` and `uLimbPivot[10]`. `uLimbOn` is 1 only when the model has pivots and the pose has at least 10 limbs. Older GLBs fall back to the §15.3 shader paths.
- **Shader** (`fish.vert`, `uLimbOn`).
  - The body, carapace and eyes are placed rigidly at spine station 0, which is the organism position, with frame 0.
  - Limb vertices (part 3, `COLOR_0.r` = t along the limb, `COLOR_0.b` = limb / 9) go through `starArm` (mode 1) or `crabLimb` (mode 3).
- **Shrimp** (`ShrimpLimbs`, research 17 shrimp motion detail). One entry, uLimb[0] = (stride phase from `Organism.walkPhase`, stepping `walkAmt`, signal = max(skunk front-leg rock `legRock`, banded claw display `clawDisplay`), antenna grooming `groom`). The shrimp models have no pivots; `fish.vert` (mode 2, `uLimbOn` 0) animates each limb class from the vertex colour (ASSETS §2.8 limb rig) with uLimb[0], `uPick` (mouth) and `uMotion` (pleopods).
- **Offline.** `tools/blender/limbpose.py` is a numpy mirror of both functions. It poses previews and species cards (`preview.import_glb`) the way the app does.

### 19.2 Brittle star arms (`BrittleArms`)

- **Rowing** (75 % of bouts; Astley 2012). Arm roles are counted from the centre arm `lead`:
  - The centre arm probes ahead with its tip a little up.
  - The two arms either side make mirrored power strokes: `FORE_SWEEP` 0.78 rad, lifted in the swing, with the right arm 0.04 cycle behind.
  - The two rear arms trail with `HIND_SWEEP` 0.22 rad.
- **Reverse rowing** (25 %, `REVERSE_SHARE`). The centre arm drags behind and the other four push.
- **Cycle.**
  - Each stance carries the disc `STRIDE_ARM` 0.7 arm lengths (a planted arm sweeping ≈ 45°), so the cycle runs at speed / stride, at least 0.6 × `MotionProfile.rowHz` (0.23 Hz) and at most `MAX_HZ` 0.9 Hz. It idles at 0.06 Hz.
  - The disc is pulled along only in the power stroke, the first `DUTY` 45 % of the cycle (`pulse`, a half sine with mean 1 over the cycle), and rests while the arms recover.
- **Turning.**
  - The mode holds for a whole bout.
  - A turn is a new centre arm, chosen only in the static disc phase and only once the travel is more than 0.78 rad off the current arm's sector (`leadSwitches`).
  - `Steering.crawl` parallel-transports the heading over the surface, so the disc never yaws.
- **Grip.** The rowing pair and the rear pair grip in the stance: at touch-down the tip's drawn point on the surface becomes its hold, and every tick a Newton solve on the arm's base swing and curl (finite differences through the same march as the drawing, 6 iterations, limits ±1.8 rad) keeps the drawn tip on it while the disc is pulled past. A hold the arm can no longer reach (out of reach, walled, or more than 2 × `GRIP_TOL` off after a re-solve) is let go (`slips`); a slipped arm lifts its tip until its next stance. In the recovery swing the arm eases from its release shape to its next hold (by `RECOVER_END` 0.92 of the cycle), tip lifted `SWING_LIFT`.
- **Arm shape.** The base swing and curl (from the grip, else the gait targets) plus:
  - a travelling bend wave from root to tip (`waveAmp`, faster while `snake`), so arms lie in sideways S-curves;
  - a resting tip curl, alternating in sign;
  - S-folds when drawn in (reach → 0; ± `FOLD_AMP` 1.5 rad, 1.6 folds along the arm), which keep a tucked arm in its own sector instead of looping across its neighbour;
  - in the day hide, a slow side-to-side trawl of the out tips (`sweep`: ± `SWEEP_AMP` 0.35 rad at `SWEEP_HZ` 0.07 Hz).

  shape(s) = yaw × (1 − 0.55 s) + wave × s sin 2π(1.3 s − phase) + curl × 2 smoothstep(0.3, 1, s) + fold × 1.5 sin 2π(1.6 s). The sim then fits this shape to the ground (§19.7).
- **Shader (`starArm`).** It integrates the sim's 15 step directions and heights into nodes and draws the Catmull-Rom curve through them (end tangents extrapolated), the cross-section riding on the curve's frame. Vertices past the arm's current length collapse onto the stump.
- **Brain states (`BrittleStarBrain`).**
  - `HideUnder` by day leaves `outArms` = 1–3 tips (fixed per animal) out toward the cave mouth, trawling slowly. Homing to the hide, a stall (no headway for 6 s: the surface walk has no path-finding and stalls on a face whose way home runs straight off it) sends it along the face for 8 s (down toward the sand first, else sideways, alternating), then it tries again.
  - `Roam` at night only (never with the lights on) uses the arm gait. An hour before the lights come on (`dawnSoon`, `DAWN_LEAD_S` 3600 s) Roam scores 0 and HideUnder 0.8, so the star is home under its rock before the lamp.
  - `RaceToFood`: the scent arrives after 15 s + distance / 0.5 cm/s. A hidden star first reaches out with its arms for 5–10 s (`armsFirstEmergences`) and then rows out at burst speed.
  - `ArmFeed`: the nearest arm reaches out, loops its tip round the item (`feedLoop` → curl) and carries the item to the mouth at 10 cm per 8–15 s (`armFeeds`).
  - `Retract`: every arm is pulled in at 6/s for 1.2 s, then it runs home at burst speed.
  - `onAttacked`: with p 0.5 the arm nearest the attacker breaks distally (`shed`, `autotomies`) and regrows at 0.4 mm/day.

### 19.3 Sally Lightfoot legs and claws (`CrabLegs`)

- **Legs** (k = 2 pair + (0 left, 1 right), pair 0 at the front).
  - Gait: an alternating tetrapod, group = (pair + side) mod 2, half a cycle apart. Each leg also has a 0.06-cycle rear-to-front lag.
  - Stride: frequency = speed / (0.45 carapace widths), clamped to 1.5–7 Hz.
  - The pose for each leg is (phase, signed extension stroke, foot lift, tuck).
  - The legs on the side the crab is moving toward are leading: their stroke sign is negative, so they flex and pull. The trailing legs extend and push.
  - Shader (`crabLimb`): the first half of the cycle is the swing, with the foot lifted about the leg's horizontal axis. The second half is the stance, with the foot planted while the leg flexes or extends along its length. A small fore-aft yaw is added. Tuck draws the legs in and down.
- **Chelae 8/9.**
  - The pose for each claw is (pick phase, stroke, reach), with the two claws half a cycle apart at `PICK_HZ` 1.6 Hz each. `pickStrokes` counts the strokes.
  - In the shader, each claw dips toward the rock and comes back to the mouth.
- **Brain states (`SallyCrabBrain`).**
  - `Graze` scores hair algae, film and diatoms. It shuffles sideways between bites with `pick` = 1, and at night it grazes a glass spot 15 % of the time (`glassSpot`).
  - `CrabScavenge` (pick 0.6) and `CarcassPick` (pick 1).
  - `HideCrevice`: legs tucked, with occasional picking. It gets +0.35 for 48 h after a molt (`softShell`) and +0.4 for 60 s after a dart.
  - `Dart` is committed for at least 8 s (`MIN_HIDE_S`, or until it arrives, up to 15 s). It tucks on arrival and counts `darts`. When the straight dash stalls (2026-10-14), it plans a walking route with `RockData.surfaceRoute` (A* over 1.5 cm water cells near a surface; see §19.5). If it stalls on a waypoint it can see, it scrambles straight across to it. The old random side-step kept hitting the same wall.
  - `NightGrab` and the `crab_predation` sandbox toggle are unchanged.

### 19.5 Hermit crab legs, chelae and shell (`HermitLegs`, research [hermit_crab_motion](research/hermit_crab_motion.md))

- **Rig.** `LimbRig.forKind(HERMIT)` is a `HermitLegs`; `motionMode` 5. Every part of the model (shell, shield, eyestalks, limbs) carries its limb index in `COLOR_0.b`, and `fish.vert` `hermitLimb` poses it about `extras.limbPivots`.
- **Legs** (k = 2 pair + (0 left, 1 right), pair 0 = P2). Forward walking in diagonal pairs: left P2 steps with right P3, half a cycle against the other two, with a 0.08-cycle lag from P2 to P3. Stride = 0.3 shell lengths, 0.8–3.5 Hz. The pose is (phase, fore-aft stroke rad, foot lift, withdrawal); the foot swings forward lifted in the first 40 % of the cycle and pushes back planted in the rest.
- **Chelae 4/5** (pick phase, pick, walking support, withdrawal): alternate strokes to the mouth at `PICK_HZ` 1.2 Hz each while the brain's `pick` is set (`Pick` at its spot: 1; `Scavenge` once slowed: 0.8) or `mouth` is up.
- **Shell 6**: rolls ±0.1 rad once per stride and bobs twice per stride while walking.
- **Shield and eyestalks 7**: eyestalks scan out of step; the shield bobs with the stride. **Antennae 8** sweep (faster while `searching`), **antennules 9** flick at 2.5 Hz in 1–2.5 s bursts every 2–6 s, nonstop while following a scent.
- **Withdrawal.** `retract` follows 1 − `Organism.extension` (Retreat sets 0): in at 12/s (≈ 0.15 s), out at 0.35/s. Eyestalks and antennae come out first, then the chelae, the walking legs last. In the shader every part but the shell shrinks toward `limbPivots[7]`, just inside the aperture; the left chela stays at the opening.
- **Tests.** `ai/HermitLegsTest.kt`: diagonal pairs and shell roll; snaps in within 0.25 s and comes out eyes first; alternating claw strokes while picking in the sim.

### 19.6 Crawler surface seat and planted feet (`sim/CrawlerSeat.kt`)

The crawler models stand on their own y = 0 plane, but the sim walks `Organism.pos` at the brain's `surfaceOffset` (0.5 × depthRatio × L + 0.2 cm) above the nearest surface along one normal, against the flat `sandTopCm` plane. Drawn there, a hermit stood about 0.5 cm off a rock face with legs and antennae crossing into the mesh, and hung over the sand slope (owner clip C 1.5–7 s). The seat fixes the drawn pose only; `pos`, `up` and every behaviour are unchanged.

- **Surface** (`CrawlSurface`): min of the rock SDF, the live sand bed (`SandBed.surfaceY`, the sand the renderer draws: 3.5 cm at the front glass to 6 cm at the back, plus digging) and the four glass panes.
- **Seat.** Step from `pos` onto the surface, fit the body up to a plane through four footprint samples cast down onto it (70 % fit, 30 % normal under the body; eased at 4 rad/s and kept from tick to tick), then a rigid contact solve over the model's contact points (hermit: 26 shell points, shield, coxae and chela bases, from the GLBs): points inside are pushed out along their own normal as a translation plus a small rotation about the body centre (a shell against a wall tips up it), and with nothing inside the body settles until its lowest point touches (0.02 cm). A final pass lifts it clear of anything still inside, then settles as far as nothing goes in. If that leaves no clean rest (a slot narrower than the shell), it retries from the bare normal, the last tilt and four tipped starts, and else holds the last clean seat within a body length. `Organism.toPose` draws the rigid body at `seatPos` / `seatUp`; `blend` eases 0 ↔ 1 at 3/s when the animal leaves or regains a surface.
- **Hermit legs** (`HermitLegs` entries 10–13, `fish.vert` hermitLimb with `uLimb[14].w` = 1): each foot has a foothold cast onto the surface from above its stance point (the rest tip swung by the stroke). It stays there through the stance (re-planted if it leaves 0.45–1.08 of the hip-to-tip reach), swings to the next foothold in the first 40 % of the cycle with the rig's lift, and a rotate-about-the-hip IK (axis × angle) plus a flex that shortens the leg and raises the knee reaches it. If the tip, knee or propodus would be inside, the foot searches the surface around the hip (3 reaches × 5 swings) and then folds up beside the body; a leg with no clear pose at all is pulled into the shell over two ticks.
- **Chelae** (`uLimb[14].y/.z`): the dip is capped so the claw tip and palm stay on or above the surface under them. **Antennae, antennules** (8.z, 9.z) pitch up and sweep out and back, **eyestalks** (6.w) lean back and down, by the smallest of 0, 0.2 … 1 that keeps them 0.04 cm off rock and glass (raised by ≤ 0.45 per tick, relaxed at 0.8/s).
- **Continuity.** Each tick first carries the last seat along with the sim's motion along the surface and keeps it if it is still clean (nothing inside, ≤ 0.1 cm gap, within one body length of `pos`); then the last seat unmoved; only then a fresh solve (fitted up, bare normal, four 0.6 rad tilts). A seat that would hop more than 10 cm/s × dt beyond the sim's own move, or turn faster than 6 rad/s, is approached by a limited step when that step is clean. Leg IK, claw limits and feeler bends change by at most 0.45 per tick; a leg whose eased sweep would cross rock is drawn shortened, else pulled in.
- **Sim follows the seat** (hermits). A clean seat whose point raised by the brain's `surfaceOffset` is more than 0.3 cm from `pos` (and itself clear of rock, sand and glass) is written back into `pos` along the seat's up only (the walk along the surface stays the sim's: writing the whole seat pinned a hermit at a ledge), and the sim's `up` eases toward the seat's (3 rad/s, ≤ 0.4 rad a tick), so the single-normal crawl step stops thrashing on overhangs and in crevices. Seat changes under 0.1 cm / 0.05 rad are not drawn (the last seat is held while clean). A seat that keeps hopping (+1 a hop, −0.25 a clean tick, at 4) reports `wedged`: `HermitBrain` ends its pick or wander and does not settle to pick for 20 s, so it walks off.
- **Other crawlers**: shapes exist for the Sally Lightfoot (carapace underside + dactyl tips), brittle star (disc + arms at 30 %) and astrea (sole + shell lip), but `SEAT_OTHER_CRAWLERS` is off: their seats added body pops and speed spikes in `FishGlitchTest` and have no no-clip test yet.
- **Cost.** About 100–200 distance samples per seated hermit per tick (a few hundred when a leg has to search); no collections are built per tick.
- **Tests.** `sim/HermitSeatTest.kt`: the full stock for 3 h at 20 Hz (from dusk) and 4 h at 10×, every tick, every seated hermit: the lowest body contact point is ≤ 0.3 cm off the surface and none is more than 0.1 cm inside; no planted foot tip or knee is more than 0.1 cm inside.

### 19.4 Tests

`sim/BrittleMotionTest.kt` (§19.7): the disc moves only in the power strokes (< 3 % of its travel in the recovery) while gripping tips hold (mean < 0.02 cm a tick on sand); for each of seeds 1–8, over 15 min of night roaming over rock and sand < 2 % of drawn arm points sit more than 0.1 cm inside a surface (deepest < 1.5 cm), < 2 % of lying arm points stand more than 0.6 cm off it, and the disc rests on it; by day the disc stays in its hide without passing into the rock while 1–3 tips trawl; from the small hours it roams, then is home and settled when the lights come on; an arm goes over or round a crab put across it.

`ai/CrawlerTest.kt` has 8 tests:
- the star hides by day with only a few tips out;
- it comes out at night;
- it emerges for food by day, arms first;
- it switches the lead arm instead of turning (the travel direction changes by more than 1.5 rad while the disc turns by less than 0.15 rad);
- an arm loops food to the mouth;
- it retreats fast when disturbed and sheds an arm when bitten;
- the crab mostly grazes, with alternating claw strokes at 1.5–5 per second, and darts home on a startle and tucks;
- the crab's legs step in an alternating tetrapod.

### 19.7 Brittle star arm contact (`sim/BrittleArms.kt`, `LimbRig.contact`, 2026-10-15)

The arms are fitted to the rock, sand bed, glass (`CrawlSurface`) and other animals every tick, after the seat update (`Simulation.behaviorTick` / `abstractPlacement` call `limbRig.contact`; dead animals `unseat`).

- **Disc.** The disc is cast down onto the surface along its up and lifted clear of any bump under its underside ring (`discUnderCm`; `discTopClearCm` checks the dome and upper rim): each of up to `SEAT_ITERS` 6 tries lifts it along its up or pushes it along the surface normal at its deepest sample, whichever leaves it clearer, never deeper and never more than `SEAT_SHIFT_CM` 1.2 cm (lifting along a stale up alone walked a disc wedged between sand and a ledge 9 cm down through the sand, its arms with it). When the sim's up names no surface within `SEAT_REACH_CM` 1.5 cm under the disc it turns toward the nearest surface's normal instead. Its up eases toward the sim's at `UP_RATE` 0.8 rad/s instead of following rough rock's normal tick by tick (that swung the whole star and wrenched holding tips off). `Organism.seatOn/seatPos/seatUp` draw it there.
- **March.** Each arm is marched out from its root in `SAMPLES` 15 steps of 1/15 arm, exactly as the shader draws it. Per step, the surface height under the step's end (probed along the disc's up with `CrawlSurface.heightAt`, then again where the step's run, shortened by that rise, lands) sets the step's height; the arm keeps `CLEAR_CM` 0.08 cm off it. A rise of more than `CLIMB` 1.4 step lengths, a step whose middle cuts into rock (a ridge, a block's corner) or a body too high to climb is a wall: the step bends aside, trying 0.25, 0.5, 0.8, 1.2 rad each way (the side that last worked first; a bend is held while needed and relaxes at 0.6 rad/s, never while gripping). Walled on every side (a crevice, an overhang), the step goes where it is least buried (7 bends × rise, level or sink). Drops over an edge of up to `DROP` 0.98 step lengths are followed, so arms drape over edges. When the two probes disagree (an edge or riser between them), the run whose probed rise gives back that run is bisected (`EDGE_ITERS` 2) and at a sheer edge the step's end rests against it on its arc, so no step ends where its height was not probed (that let a step straddle thin rock or stand out over an edge). A lying step with nothing under it within its drop (`drape`) is not held out in the water along the disc's frame: from a fan of bends (`DRAPE_TURNS`, plus a turn toward straight down) and rises (steepest drop to level, or up to the steepest rise when the disc is upside down) it takes the clear one whose end lies nearest rock, sand, glass or an animal (a bend costs 0.15 cm/rad, pointing away from straight down up to 0.2 cm), so arms wrap round ledges and lie on the face or on what is below. Lifted steps (swing, probing tips) are lowered under a ceiling to keep 0.15 cm clear.
- **Animals.** Animals lying within reach (sphere of 0.3 × collision length, up to 8) are climbed over like rock where the rise allows, else bent round.
- **No snaps.** A drawn step turns at most `PSI_STEP` 0.5 rad from one contact to the next (bends are chosen within that), so a wall met all at once is worked round over a few ticks.
- **Pose layout** (8 vec4 per arm, arm k at `uLimb[8k]`): the x slots carry heights y₁..y₈ as 0.5 + 0.5 y (the renderer lerps x as a phase); the plain slots n = 0.. carry the step directions ψ₁..ψ₁₅ (rad from the rest direction), heights y₉..y₁₅, the arm's length, 0. `limbpose.py` packs and unpacks it (`brittle_pack`).
- **Cost.** ≈ 2 surface probes per step (more when bending), 5 × 15 steps, plus a 6-iteration grip solve for holding arms, and at edges up to 3 more probes per step and a draping fan of ≈ 15 end checks: about 1–1.4 thousand surface samples per tick for the one star (≈ 35 % more than before the drape); no collections are built per tick.

### 19.5 Shrimp navigation and perches (`ai/ShrimpNav.kt`, 2026-10-14)

A 24-hour diagnostic run of the owner's stock found three problems:
- The banded shrimp never reached its ledge. The skunk's keep-apart rule (30 cm) sent it to the right ledge, and the surface walk stalled on a wall at x ≈ 69 for the whole day, right side up.
- The skunk's `STATION_SHRIMP` anchor sits 4 cm off the rock. The shrimp paced the sand, climbed the front glass and teleported onto clients.
- The baked anchor normals disagree with the SDF gradients.

What changed:
- **Perches are found at run time.** `RockData.perchNear` is a deterministic grid search of SDF surface points that have open water in front of them.
  - Banded shrimp: an underside (normal y < −0.45) near the left ledge (owner photos b_02–b_04). The score is distance + 6·(1 − cover) + 3·(1 + n.y).
  - Skunk: an upright or vertical face near the station that does not face the back glass.
  - Animals of the same species keep 3.5 cm apart.
  - The body is laid flat on the surface with `bodyHeading`.
  - The banded keep-apart distance is 20 cm, and the check excludes its mate.
- **`ShrimpNav`** wraps `Steering.crawl`. The shrimp walks at stance height (0.21 BL off the surface). If the walk makes no 0.5 cm of progress for 6 s, or if the shrimp or its goal is off every surface, it plans a pleopod swim hop. The hop lifts 2 cm off the surface and takes the shortest SDF-clear route: direct, over the rock at +4…32 cm, or along the front glass. It lands at stance height and rolls belly-to-surface over the last 4 cm. Hop speed is 6 cm/s, a design estimate. There is no general collision; only clear segments are checked against the SDF, so this stays compatible with a capsule body layer.
- **Banded shrimp at night:** `NightForage` picks waypoints on rock of any orientation (ceilings included) within `FORAY_CM` = 30 cm and dwells 4–15 s picking at each one. At dawn it walks back and hangs inverted (posture 2).
- **Skunk:** `Climb` swims to the client's back at `MOUNT_CM_S` = 8 cm/s instead of being placed there. After `TailFlip`, both species navigate home.
- **Antenna snap:** `appendagePhase` wrapped at 1000 rad, a non-multiple of 2π, which made the antennae snap about every 9 minutes. It now wraps at `Organism.APPENDAGE_WRAP` = 2π·159, and `FishRig.lerpPhase` uses the same period.

Tests: `ai/CritterAccuracyTest.kt` checks that:
- the banded shrimp hangs inverted under the left ledge with the full stock;
- it gets home from the old stall point;
- it forages on foot, including inverted, within its territory;
- the skunk sits on rock at its station, not the sand;
- two skunks take separate spots;
- the skunk swims onto a client at a bounded speed and back;
- the cardinal hovers level and slow;
- the wrap period is a whole number of turns.

## 20. Hummingbird bobtail squid, sandbox only (`ai/BobtailSquidBrain.kt`, `sim/SquidRig.kt`, research cephalopod_squid)

*Euprymna berryi* is an optional species in the stocking gallery (invertebrates). It is not in the owner's tank preset. The verdict, the sources and the [M]/[L] tags are in `docs/research/cephalopod_squid.md`.

### 20.1 Body, motion and tolerances

- **Kind.** `BrainKind.BOBTAIL_SQUID` (genera *Euprymna*, *Sepiola*, *Sepietta*, *Rondeletiola*; brain alias `bobtail_squid`). It is not a fish: `isFish = false`, so the spline is rigid.
- **Gait.** `Gait.FIN_JET` is moved by the swim integrator:
  - fins at 1.5–3 Hz, cruise 0.6 BL/s, burst 8 BL/s;
  - no body wave and no C-start;
  - `toPose` sends motion mode 4 with the [SquidRig] limbs.
- **Collision body.** `body` block in the JSON: a capsule 4.4 × 1.15 cm, with stations measured from the GLB.
- **Temperature.**
  - The HealthModel override sets an optimum of 20–25.5 °C and a tolerance of 18–27.5 °C.
  - HeatModel `CEPHALOPOD` sets the onset at 28.5 °C and the CTmax at 33.5 °C. It does not acclimate.
  - Dissolved oxygen: optimum ≥ 6 mg/L.

### 20.2 Brain

| Behaviour | When | What it does |
|---|---|---|
| `Buried` | Day | Travels to its sand bed, then buries for 8 s, puffing sand. Sits at posture 5: mantle 0.32 of its depth below the surface, eyes out. Ignores weak or distant taps. |
| `Hover` | Night | Waypoints over the sand and the rock face, away from hawks. Arms spread, passing clouds. |
| `Hunt` | Night, hungry | Stalk → poise (dorsal arms up) → tentacle strike. Pods come from `MicrofaunaModel.podPreyIndex(BOBTAIL_SQUID)`: 60 % hit, 40 min of hunger relief per catch, then 1–3 min handling. Shrimp under 0.75 × its length (molting under 1.1 ×) and hermits under 0.35 × are killed with `sim.eaten`. Fish are never prey. |
| `TargetFed` | Any time | Thawed food, for the 50 % that accept prepared food. |
| `Jet` | Threat or startle | Blanch, then ink if the startle is above 0.3 or a predator is near and the ink reserve is above 0.3. Kinematic jet backwards 6–10 BL facing the threat, then hide dark. Jets again if the threat stays within 2 BL. Ink refills in 1 h. |

- **Light organ.** At lights-on the squid vents 90 % of its symbionts, which regrow over about 6 h. Night glow = symbionts × (0.25 + 0.75 · smoothstep(0, 0.05, light)).
- **Abstract mode.** At night, hunger falls at a rate set by the pod index. The rate uses the sim clock, because the behaviour dt is capped.
- **Predation.**
  - The hawkfish's threat radius and prey weight are 0 while the squid is buried (posture 5). Once exposed, it is prey of weight 0.5.
  - The squid's own threat table:

    | Threat | Radius |
    |---|---|
    | Hawk or eel | 3.5 BL |
    | Dottyback | 2 BL |
    | Banded shrimp at night | 1.2 BL |

### 20.3 Render signals (`SquidRig`, 5 × vec4)

| Entry | x | y | z | w |
|---|---|---|---|---|
| `[0]` | fin phase | fin stroke | mantle contraction | fin wrap |
| `[1]` | arm phase | spread | dorsal-pair raise | tentacle |
| `[2]` | cloud phase | chromatophore expansion | cloud strength | glow |
| `[3]` | 0 | ink centre x | ink centre y | ink centre z |
| `[4]` | 0 | ink age | ink radius | ink density |

These signals drive three parts of the renderer:
- `fish.vert`: `squidPose` handles part 0 (mantle), part 1 (fins) and part 3 (arms by class, tentacles).
- `fish.frag`: `uChroma` = (expansion, cloud, glow, 1 + cloud phase) drives procedural chromatophores and the ventral glow.
- `InkCloudRenderer`: draws the ink puff as particles.

### 20.4 Warnings and tests

- **Warnings** (`Compatibility.kt`):

  | Code | Severity | When |
  |---|---|---|
  | `squid_predator_fish` | Severe | With a hawkfish, dottyback or eel, in either order of adding |
  | `squid_eats_crustaceans` | Warning | With shrimp, hermits or a crab |
  | `squid_busy_fish` | Warning | With any other fish |
  | `squid_short_life` | Info | Always |

- **Tests.** `sim/BobtailSquidTest.kt` has 11 tests:
  - sheet and catalog;
  - the warnings for the owner's tank;
  - burial by day with the eyes out;
  - emerging at night with fins and glow;
  - pod hunting with tentacle strikes;
  - eating a small shrimp but not an adult cleaner;
  - ink and a backward jet when startled;
  - shyness of the hawkfish and safety while buried;
  - venting at dawn;
  - living on pods but starving on dry rock;
  - adding from the stocking gallery.

## 21. Perception, memory, mood and species quirks (research realism_gaps, `ai/Perceive.kt`, `ai/Mind.kt`)

Source: the realism gap analysis `realism_gaps.md` (project research files, 2026-10-14) with its keys: POH04, DIX12, HAZ96, AIZ01, REE02, TOM25, WIL94, SOA11, MRL23. Most numbers below are design estimates inside the direction of a source; each constant carries an `@Ev` tag and the main ones are rows in `EvidenceLedger` (species `"perception"`, `"mind"` and the species ids). Nothing in this section draws random numbers except where noted, so a fish that never meets a trigger behaves exactly as before.

### 21.1 Perception channels (X1, X6)

Each species sheet may carry a `senses` block (`sim/Senses.kt`, `SensesSpec`); without one, fish get the defaults below and invertebrates are ungated (the old radius search, so their tuned scavenging stays as it was).

| Field | Default (fish) | Meaning | Evidence |
|---|---|---|---|
| `visionCm` | 120 | sight range in full light; covers the tank so daytime search is unchanged | DESIGN-ESTIMATE |
| `darkVision` | 0.12 diurnal, 0.5 nocturnal/crepuscular | fraction of sight left in darkness; range = visionCm × (dark + (1 − dark) · smoothstep(0.02, 0.3, light)) | MRL23 (title only) direction; values [L] |
| `lateralBL` | 1.5 | lateral-line reach in body lengths, light-independent | POH04 catfish analogy; value [L] |
| `chem` | 0.6 | weight of smell | DIX12 goby, HAZ96 hermit analogies |
| `gated` | true (false for invertebrates) | false = old search |  |

- **Food** (`Ctx.findFood`): a particle is found if it is within sight, within the lateral line, or the animal is in its scent plume. Scent: inside √(4·D·age) of the particle (D 1.5 cm²/s, cap 25 cm), or downstream inside a cone of half-angle tan 0.25 whose front has travelled |u|·age along the local current, up to 90 cm; 3 s leach lag; weighted scent ≥ 0.15 counts. In the dark a diurnal fish upstream of food finds nothing until it drifts into the plume; downstream it smells it.
- **Threats** (`SpeciesBrain.findThreat`): invertebrates keep the old ×0.6 at night. A fish's threat radius is scaled by its sight factor but never below its lateral-line reach.

### 21.2 Place memory (X2)

`PlaceMemory` holds a few entries per kind, each fading with a half-life; writing within 6 cm of an entry reinforces it.

| Kind | Written by | Read by | Half-life | Cap |
|---|---|---|---|---|
| REFUGE | a flee that reached its cover | `Flee.enter`: a remembered refuge within 45 cm, not toward the threat or a threat site, replaces the computed cover when it is nearer: the nearest familiar shelter (not for the hawkfish, which picks its own perch) | 7 d | 3 |
| THREAT | `onAttacked` | refuge choice | 6 h (as `Social.kt`) | 4 |
| NIGHT | the hawkfish's sand spot | `SandRest` | 30 d | 1 |
| PATROL | the dottyback's route | `CavePatrol` | 14 d | 6 |
| GRAZE | (reserved) | | 1 d | 6 |

### 21.3 Keeper and feeding clock (X3)

- `Simulation.feed` (every mode, abstract included) calls `Mind.onKeeperFeed` for every animal whose diet matches. `FeedClock` keeps up to 3 slots (REE02: golden shiners learn up to three times and places). A feed within 90 min of a slot shifts it 30 % toward the new time and place and adds 0.15 × (1 − strength); feeds within 30 min are one meal. A slot whose time passes without food loses 20 % per missed day (REE02: anticipation outlasted withheld food by 3–10 days).
- **`AnticipateFeed`** (fish except eel, hawkfish, cardinal, the two gobies and the leopard wrasse): from 60 min before to 30 min after a slot with strength ≥ 0.35, an awake, hungry, unafraid fish sweeps a slow figure-eight under the learned feeding spot, rising as the time nears, mouth working near the end. Score ≤ 0.55 (below the cleaner's work and any startle). From the third consistent daily feed.
- **`ComeToKeeper`**: each meal raises a keeper association by 0.06 × (1 − a) (−10 % per day without food; TOM25 analogy: sea bream learned the diver who fed them over 12 days). A keeper cue (a gentle tap, a hand's shadow) with association ≥ 0.35 brings a calm, awake fish to the glass at that spot for 20–60 s (video fish_tailspot_blenny S2 "approaches the glass when the observer is near"). A knock (`TapGlass`) is a startle, not a cue. The app's torch is render-only and is not a cue.

### 21.4 Mood and personality drift (X4)

- `Mood.fear` (τ 29 min, ≈ 20 min half-life) rises 0.6 × magnitude per attack and 0.3 × per startle; `arousal` (τ 4.3 min) with feeds, startles and attacks. Feed scores × (1 − 0.5 · fear); glass curiosity and begging need fear < 0.3.
- A tactile session with the cleaner (the dance that sets `aggressionDampUntil`) halves fear and arousal and trims social stress by 20 % (SOA11, *Ctenochaetus* cortisol analogy): the tang's P2 row.
- Drift (WIL94 direction): +0.002 boldness per safe meal, −0.02 boldness and +0.01 skittishness per attack ≥ 0.4, −0.003 skittishness per tap not followed by an attack within a minute; capped at ±0.15 around the birth value (the ±15 % birth jitter is unchanged).

### 21.5 Species quirks

| Species | Sequence | Evidence |
|---|---|---|
| Falco hawkfish (`ai/HawkNight.kt` `SandRest`) | At dusk (activity < 0.3) it hops down from its perch in ≤ 3 BL ballistic hops at 3 BL/s with 1.5 s pauses on rock or sand, to an open-sand spot 2.5–10 cm from the rock base near its lowest perch (remembered, same spot every night). It settles propped on its pelvic fins, head up 17.5°, body still, head and eyes turning toward movement every 1–3 s. A lights-on flinch is ignored while resting. At dawn `PerchHop` hops it back up. Abstract mode parks it in the same pose. | Tee's night clip T89 [H] (sand, within 10 cm, pitch 15–20°, no flight); FishBase "rests at coral-head bases" (10 §5) |
| Brittle star (`ai/BrittleLight.kt` `LightWithdraw`) | A light step (≥ 0.08 within ~30 s; lights forced on) or a shadow within 40 cm: all arms whip in and the disc freezes 3–8 s (score 0.93), then the old behaviours resume (by day it rows home). Slow dawn ramps do not trigger it. The day arm tips out of the hide reach × (1 − 0.5 · light). | AIZ01 calcite lenses (*O. wendtii*, ANALOGY for *O. erinaceus*); video invert_brittle_star S3; durations [L] |
| Tailspot and lawnmower blennies (`ai/HoleEntry.kt` `TailFirstHole`) | Home is entered tail first: swim to a stand-off at the mouth, pivot to face out (6 rad/s), scull backwards along the tunnel axis at 0.8 BL/s with the body laid along the tunnel, then rest head-out (fully in at night). Leaving is head first. | owner direction 2026-10-14; head-out inspection, 10 §7; rates [L] |
| Tailspot blenny | `GlassCurious` added (fear-gated), plus `ComeToKeeper`. A newcomer's trip out to a prospecting site is committed (only a flee breaks it off, at most `PROSPECT_TRIP_S` 25 s [L]) | video fish_tailspot_blenny S2 [M] |
| Indigo dottyback (`ai/DottyPatrol.kt` `CavePatrol`) | A learned route of 4 crevice mouths within its territory (PATROL memory). Each round: dart along the rock to each stop, watch from the crevice mouth facing out 2–8 s, dart back to sit in the cave mouth 5–20 s. One stop is swapped every 6 rounds. | 10 §8 "record cave, patrol route, retreats"; video fish_indigo_dottyback "cave-mouth darts" [M]; numbers [L] |
| Blue tang | Already present: `PlayDead` (side-roll 3–8 s after a hard startle) and `TangSleep` (wedged, rolled ~70°). New: cleaner contact lowers its fear and stress (21.4). | video fish_blue_tang S1, S3 [M]; SOA11 |

### 21.6 Predation evidence gate (X7)

`Simulation.unsourcedPredation` (SetParam `unsourced_predation`, default off, copied by the time-lapse fork) gates the predation rolls no source supports, even in the sandbox with predation on: the Sally Lightfoot's nightly snail rolls and its `NightGrab` on snails (PUC06, PIZ25 support grazing and scavenging only), and the moray's kill roll (the strike still lands as an attack that startles). Crab-on-fish keeps its own `crab_predation` scenario. The hawkfish's strike outcomes stay as they were (10 §5 separates approach, strike, contact and kill).

Two crawler fixes ride with it: `Sdf.project` returns the iterate nearest the surface (in a rock crease the finite-difference gradient can point the wrong way, and the old last iterate could sit deeper in the rock than the input), and the Sally Lightfoot's home, which falls back to the `CAVE_SMALL` anchor in the middle of a cave, is moved onto the nearest wall when it lies more than 3 cm off the rock (`HOME_SNAP_CM` [L]); a crawler cannot reach open water.

### 21.7 Tests

`ai/PerceptionTest`, `ai/MindMemoryTest`, `ai/SpeciesQuirksTest` and `ai/PredationGateTest`: food in the dark found by smell downstream, not upstream, felt within the lateral line, seen by day; invertebrates ungated and the cardinal keeping more night sight; the clock learning a time and fading after missed days; a tang gathering under the feed spot before a learned 13:00 feed (control fed at 17:00); a keeper cue bringing a fed fish to the glass; a remembered refuge used under attack; a flee writing a refuge, fear fading and drift capped; the hawkfish's night sand pose and its return to a perch by day; the brittle star's withdrawal from a light step and a shadow but not from the dawn ramp; the tailspot backing tail first into its hole; the dottyback's patrol rounds; the predation gate.

### 21.8 Deferred

Engineer-goby juvenile schooling (the goby and sand code belong to the sand-bed work), the cleaner's audience effect and cortisol-driven cheating (P2; c03/c04 sensitive), clown host learning and size hierarchy, lawnmower graze-patch memory (the algae model has no spatial film value yet), nassarius and brittle-star plume emergence, astrea and asterina day/night rhythms, the asterina fission identity gate, and every hermit item (owned by the hermit rebuild). The pajama cardinal and both shrimp are being reworked elsewhere.
## 21. Glitch guard and motion smoothing (`FishGlitchTest`, `ai/Steering.kt`, `geom/BodySpline.kt`, `render/FishRig.kt`)

A reviewer said the fish "glitch out a lot". `FishGlitchTest` measures that. It runs the default stock for 5 sim hours: dawn, midday and dusk at the live 20 Hz tick on seeds 1–3, and 2 h at 10× (0.5 s ticks) on seed 4. Every tick it checks each animal's pose, as the renderer receives it, against these causes:

| Cause | What it flags |
|---|---|
| `NAN` | Any non-finite pose value. |
| `TELEPORT`, `SPEED_SPIKE` | The head moves faster than 1.5 × burst + 12 cm/s. |
| `BODY_POP` | A spine station moves more than the head's travel plus a rigid swing. |
| `HEADING_FLIP`, `TURN_RATE` | A flip, or a turn too fast for the species. Thresholds scale with dt; a C-start is exempt. |
| `UP_FLIP` | The up vector rolls more than 60° in a tick, or more than 250°/s. |
| `OSCILLATION` | 6 or more yaw reversals of at least 8° within 3 s. Yaw is measured about the animal's own up, so a crab on a wall is not judged in world yaw. |
| `BEHAVIOUR_THRASH`, `POSTURE_THRASH` | 6 or more behaviour or posture changes within 10 s. A behaviour's own phase captions (the diamond goby's sift cycle) do not count. |
| `POSE_JUMP` | The bend or a limb scalar jumps in one tick. |

The test prints a ranked table by species and by behaviour, and writes every hit to `core/build/glitch-hits.txt`. Penetration of rock, sand, glass and the surface is printed but not asserted; the body-collision work owns it. The budgets in the test are a regression guard.

Root causes fixed:

- **Engineer goby burrow path** (`GobyBrain.tunnelSpline`). The body path ran from the head to the mouth and then down the tunnel whichever way the goby faced. A goby going in head first to dig folded back on itself, and its body flipped 20 cm in one tick as the head passed the mouth. Facing in, the body now trails out through the mouth.
- **Body spline.** A hole, den or burrow override polyline snapped the head to the path's first point. The override is now anchored at the head and blended in and out at 6/s (`BodySpline.overrideMix`).
- **Kinematic placement.**
  - A perch or hole picked across the tank was crossed in a few ticks. The step is now capped at max(1.2 × burst, 6 cm/s).
  - A step that ended deep in rock was pushed out the far side. From outside, it now stops at the surface along the step.
  - The up vector turns at most 240°/s.
  - The push out of rock runs up to four passes. A single pass left a creeping step 0.05 cm inside a curved face.
- **Swim heading.**
  - The heading used to follow the raw velocity. Now the course is low-passed (`wantSmooth`), at a rate that drops when the fish is hovering or barely driven.
  - A slow, unhurried fish no longer pitches its nose at a vertical course or yaws at its tiny horizontal residual.
  - Urgent moves (urgency ≥ 0.7, C-start, walkers) turn as before.
- **Waddle and separation.**
  - The labriform waddle is now a direct sideways offset, not a desired velocity. As a velocity it spun a hovering clown ±100°.
  - Separation keeps its full r/d strength until 0.9 r and eases to zero by 1.1 r (`SEP_EDGE`). It used to cut from unit strength to 0 as a neighbour crossed r. A fade wholly inside the edge weakened spacing, and one wholly outside it strengthened spacing. Either changed who meets whom on the rock enough to move the single-seed c01 (clowns near host) and c09 (blenny entries) checks. Over seeds 1–16, c09 entries fell from 4.3 per run to 2.7 with the inner fade.
  - The clown's dart target is picked once per dart.
- **Feeding reach.** A bite is measured from the mouth, half a body length ahead of the centre, as well as from the centre. Rock avoidance holds a swimmer's centre about 0.6 L + 1 cm off the rock. Before this change, a wrasse after mysis settled on a ledge hovered 4 cm short and never ate. It now eats on every seed tested in `AiTest.feedingReducesHunger`; it failed 2 of 6 seeds once the smoother turning changed which particles it chased.
- **Fast forward.** At 10× a 0.5 s tick overshot the steering integrator every tick. Swim now integrates in substeps of at most 0.1 s.
- **Swim roll.** The up vector rolls at most 5 rad/s. A tang rolled upright from "playing dead" or sleeping in one tick.
- **Leaving a hole.** A fish that starts a tick deep in rock is eased out at max(burst, 10 cm/s) instead of being shoved its whole depth at once (a blenny popped 3–7 cm). It is always put past the hold band (0.34 L).
- **Wedged between rock and glass.** The hold-position rule (never step closer to rock inside 0.34 L) used to freeze a fish that the glass clamp held against a rock face. It now lets the fish slide along the face on its wanted course, at up to half cruise and no closer than 0.2 L. Before, an engineer goby stayed stuck all night 1 cm off a ledge, its burrow a few cm away (c14). A kinematic step pressed flat against a face slides along it in the same way, instead of stopping.
- **Crawlers.**
  - Wander and the walking course are low-passed.
  - A forward walker turns before it walks. A crab walks the smoothed course.
  - On a corner whose raw course points back each tick, an attached walker that the re-attach would throw backwards stays put.
  - Re-attach is capped at max(1.5 × walk speed, 8 cm/s); push-out from rock stays uncapped.
  - A post-crawl projection that would jump further than a tick's walk is replaced by the step slid along the rock face. If that slide is blocked too, the step is undone. After 2 s wedged, the walker takes the projection (one hop beats freezing in a crevice). A projection that did not converge is never taken.
  - Arrival is judged along the surface, so food just off the glass is reached from under it instead of being circled. Near a target, the smoothed course may turn on a radius of half the remaining distance (at least 1.5 cm).
  - A target straight above or below leaves only a small tangent residual whose sign flips. The walker only follows as much of it as the surface leads toward the target. Urgent dashes are exempt.
  - The leg stride clock runs after the projection, so an undone step takes no stride.
  - The up vector turns at most 240°/s.
- **Limb rig.**
  - The hermit crab's eyestalk and antenna bob runs once per stride, not twice, and shrinks above 1.5 strides/s. At twice per stride it reached 7 Hz, faster than the 20 Hz pose stream can carry, and flicked between its extremes on each snapshot (about 1 900 `POSE_JUMP` hits per run).
  - Crab legs swap leading and trailing roles over about 0.4 s, and only on a clear change of side (hysteresis ±0.25).
  - The shrimp signal and grooming values ease in.
- **Appendage phase.** It uses the core wrap `Organism.APPENDAGE_WRAP` (2π·159) from main, and the renderer interpolates it across that wrap.
- **Render interpolation** (`SnapshotInterpolator`).
  - The blend used to restart at 0 whenever a snapshot arrived. Every early snapshot jumped the fish forward and every late one froze it.
  - Snapshots now sit on a smoothed timeline stamped by sim time, so a skipped snapshot leaves a two-tick gap rather than a double-speed tick.
  - The scene is drawn one interval plus twice the measured jitter behind the newest stamp.
  - After a pause the blend restarts from the held pose.
  - Each snapshot's id map is built once per snapshot, not once per frame.

Hits over the 5-hour run:

| Cause | Before | After |
|---|---|---|
| `OSCILLATION` | ~45 900 | ~600–2 600 |
| `HEADING_FLIP` | ~37 900 | 0 |
| `UP_FLIP` | ~12 600 | 0 |
| `POSE_JUMP` | ~8 500 | 0–1 |
| `BODY_POP` | 870 | 0–2 |
| `SPEED_SPIKE` | 224 | ~10–30 |
| `TURN_RATE` | 112 | 0–2 |
| `TELEPORT` | 9–14 | 0–4 |
| `NAN` | 0 | 0 |

**Clown host defence (`ai/PairMatrix.kt`).** The tailspot's hole (`HOLE_TAILSPOT`) lies 16 cm from the anemone, inside the clowns' 22 cm halo. Every time a newcomer tailspot left its hole, the clowns counted it as a host intruder and nipped it back in (10 female and 4 male chases in 3 h on seed 1). Its prospecting trips rarely reached the lawnmower, so c09 depended on luck. Two changes:

- A fish whose own shelter (`homePos`) lies inside a host's halo is a resident there, not an intruder, while it stays within 4 cm plus half its length of that shelter. A harmless resident is also let be anywhere in the outer halo, and chased only into the 10 cm core (06 §5.3, core / halo 10 / 22). A resident with its own row against the clowns, such as the dottyback's standoff, is a rival and is still defended against throughout the halo, so c02 holds.
- A host defender breaks off the chase once the intruder is out of the halo. It does not leave its anemone.

c09 entries over seeds 1–8 are now 4–9 per 3 h (they were 0–7), and almost every entry draws a chase.

The cleaner wrasse's pose scheduling was also made robust, for c03 on seeds 1–5:

- A client poses at most once per `POSE_MIN_GAP_S` (1290 s) unless it carries ich.
- A client that is due a pose is weighted 3× when the wrasse picks its next client.
- Each inspection length is drawn when the client poses. It is the lognormal quantile u₀ + k·φ (mod 1) of that client's k-th pose, with a random u₀. Each length is still lognormal, but a client's few poses average near the mean. Independent draws pushed the tang's mean past 10 s on one seed in five.

What remains sits mostly in the collision code:

- The dottyback ping-pongs on the look-ahead avoidance (`rock.sdf.gradient(pos + vel·0.4)` × 40) where it is wedged near rock at 10×.
- A blenny is pushed up to 3 cm out of its hole in one tick by the hard rock resolve.
- A grazing snail circles where it cannot reach its target.
- 0–4 `TELEPORT` hops of 4–10 cm per run come from two sources. One is a crawler wedged in a crevice for 2 s that takes the projection out. The other is the swim hard resolve pushing a clown or blenny out of rock.
- The clowns wiggle in the host.

## 22. Livelier fish (research 30, `ai/PerchSites.kt`, `FishBehaviorProbe`)

Sources: [clown, tang, cardinal](research/30_fish_ai_clown_tang_cardinal.md), [wrasses and hawkfish](research/30_fish_ai_wrasses_hawkfish.md),
[blennies, gobies, dottyback](research/30_fish_ai_blennies_gobies_dottyback.md). `FishBehaviorProbe` (run with
`REEFLIVE_PROBE=1`, writes `core/build/fish_probe.txt`) measures every fish over 30 min of day and 15 min of night:
speed in BL/s, share of time still, stop-go bouts per minute, turning, depth, distance to rock, ground covered and
the behaviour time budget. `FishTraceDump` (`REEFLIVE_TRACE=<dir>`) writes paths for the top-down previews.

### 22.1 Part 1: rock perchers

| Species | Measured before | Change |
|---|---|---|
| Falco hawkfish | Never changed perch in 30 min (32 cm² covered): the numbered `PERCH_n` aliases sit on the same spots as the named perches, so "nearest other perch" was itself. | `PerchSites` keeps the named perches without aliases and adds rock tops found once by a top-down trace (up to 22, ≥ 9 cm apart, ≥ 10 cm under the water line). The next perch is a weighted draw: hops near 22 cm, recent perches, occupied perches, the clowns' host and cleaning stations avoided, food and passing fish attract. Dwell is 15 s + an exponential tail (mean ~70 s), from *P. forsteri* (Coker et al. 2017: 7.6–9.1 perch changes per 10 min) [ANALOGY]. It glances at moving fish and food in quick head turns, pivots on its perch every 12–40 s, and turns to face the next perch and crouches before it hops. A perch it cannot reach within 8 s is remembered as unreachable for 15 min and it lands on the nearest other perch instead. `c11` now expects 25–80 changes/h. |
| Lawnmower blenny | One rasp every second for its whole day (60/min). | Bite bouts of 2–5 rasps at ~0.65 s, then a 4–12 s look-round with the head up and the eyes swivelling, ~20 bites/min while grazing (field: 3,427 bites/day, Townsend & Tibbetts 2004) [SOURCED rate]. Food per rasp is scaled up to match. A fifth of its moves cross its 28 cm range. |
| Tailspot blenny | Head-out in its hole 95 % of the day. | Hole score 0.45 → 0.38 and perching beside the hole 0.35 → 0.42 (15–60 s, 4–12 cm out, rested 10–40 s between), with head turns and bobs: about two thirds in the hole. It rests level along the rock face, facing away from the hole. |
| Leopard wrasse | Hung in one place picking every 3–6 s; every mild clown charge buried it for 5–20 min. | Forages along a path: 1–3 pecks per spot, then glides 5–12 cm on (turning back toward home past its range), now and then pausing to look round, avoiding clowns and fish that chased it. Only a chase of magnitude ≥ 0.5 buries it, for a time scaled by the magnitude. |
| All fish (`KeepClear`) | The tang spent a third of its day hovering, staring at the female clown that once chased it. | A 3–8 s bout to cover, then 10–30 s off unless the feared fish closes in. |
| Skunk cleaner shrimp | A hawkfish perched by the station posed in a climb → tail-flip → climb loop. | Fleeing releases the client; a fish is offered a cleaning at most once per 5 min. |
| Body spline | The 10× speed tick (0.5 s) popped the body when a fish turned hard. | Trail relax capped at 0.5 per step (`BodySpline.MAX_RELAX_STEP`). |
| Hermit crab legs | A startled hermit pulled its legs fully in within one tick. | Withdrawal capped at 4 retract units/s (`HermitLegs.WITHDRAW_MAX_PER_S`), fully in within ~0.25 s. |

AI cost: the probe's 30 sim-minutes take the same wall time as before (~13.5 s); the perch trace runs once at start-up.

### 22.2 Part 2: clowns, tang, cardinal

| Species | Measured before | Change |
|---|---|---|
| Ocellaris clowns | At the host 99 % of the day with no trips off it. | `Sortie`: 8–25 s trips 0.8–2 body lengths over the discs (never past 2.9 BL from the host), snapping at drifting plankton every 2–5 s. Trips come about once a minute in the first hours after lights-on and less than half as often through midday (`ClownBrain.diel`, juvenile ocellaris activity, research 30 §1). The male tags along a length behind and below the female, and goes up on his own about half as often. |
| Blue tang | Patrol swept wall to wall along one lane at one speed. | `Patrol` is a circuit along the rock face, 1–2 body lengths off it: 12–30 cm legs on a height band that changes when it turns, a pace of 0.7–1.3× cruise per leg, a look-about hover on a fifth of the legs, and a 12 % chance of turning back early. |
| Pajama cardinal | Held still by its cover all day (correct, research 30 §3) with nothing else. | Still rests by day, but turns to watch a fish passing within 18 cm and snaps at a drifting particle every 20–60 s. |
| Falco hawkfish | A flee to a rock-top perch could hover at the approach point, a clearance above it, until the flee ended. | It clamps on at the approach point too. |

`LivelierFishTest` checks the clown trips (more at dawn than midday, within the leash) and the tang's height spread.

## 23. Fish waste (`sim/WasteSystem.kt`, `render/WasteVisuals.kt`, research 18_fish_waste)

- **Guts.** 17 % of each fish's N output is fecal, not gill ammonia (feed N budget: 13 % in solids vs 62 % dissolved,
  Canary Is. sea bream); `WaterSim` diverts that share of the excretion term into `fecalPending`, which
  `WasteSystem.collect` shares out by mass^0.8 over the awake fish. A swallowed meal (`Organism.eatenG`) moves 13 % of
  its leachable N out of the water's leaching pool into the gut. Contents pass through over the transit time (5 h at
  25 °C, Q10 2.2; damselfish retain food 6.5 h) and leave as a pellet of ~1.5 h of output at the vent.
- **Pellets.** Up to 240, sinking 0.2-1.2 cm/s (sea bream feces 0.48 cm/s mean), carried by the flow, leaching 12 %/h
  of their N as NH4 + PO4. Fates: the HOB intake strainer pulls in what drifts within 9 cm (`WaterSim.hobCapture`);
  tangs, clowns, blennies, dottyback and cardinal mouth drifting pellets and spit most back out (coprophagy, Robertson
  1982); hermits, nassarius, brittle star, crab and shrimp eat settled ones within reach; a current over 8 cm/s lifts
  settled pellets again; after ~10 h lying still a pellet breaks down into mulm: the detritus pool plus a dark patch in
  the sand grid (`SandBed.mulch`), so waste collects in the dead spots.
- **Filter.** The HOB floss load rots from 2 to 7 days old (3 %/h when fully ripe) unless rinsed (`hobRinseDays`,
  default 7; Maintain `RINSE_FILTER`). Maintain `SIPHON_SAND` takes the settled pellets, 40 % of the detritus pool and
  40 % of the sand grid's mulm.
- **Abstract mode** (high speed, behaviour off): ready guts go straight to fixed shares (25 % HOB, 15 % leached, rest
  detritus). N is conserved: gut, pellet and floss contents sit on the export side until they return.
