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
  data class Feed(val kind: FoodKind, val grams: Double, val at: Vec3? = null): Command()   // FLAKE, MYSIS, NORI, PELLET
  data class WaterChange(val fraction: Double, val mix: WaterState = WaterState.FRESH_MIX): Command()
  data class Dose(val paramId: String, val delta: Double): Command()
  data class Lights(val on: Boolean?, val overrideIntensity: Double? = null): Command()
  data class Heater(val working: Boolean, val setC: Double? = null): Command()
  data class Powerheads(val on: Boolean): Command()
  data class AddOrganism(val speciesId: String, val sizeCm: Double, val nickname: String): Command()
  data class RemoveOrganism(val id: String): Command()
  data class TapGlass(val worldPoint: Vec3): Command()
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
grazing = Σ grazers (tang 0.004/h × hungerFactor, blenny 0.0015/h) when they are in GRAZE behavior
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
- `HoverCover`: hover 5–15 cm from cover (coverScore 0.3–0.6), mid-water (y 0.3–0.6), just under the center arch or beside the left pillar; nearly motionless, pectoral sculling, tail idle (amp 0.1, pectoral 1).
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

**Ecsenius blenny (`BlennyBrain`)**
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
- `Feed(kind, grams, at?)`: spawns particles at `at` or at `SURFACE_FEED` (under the feeding ring at the left-front, x = 20, z = 25, y = waterLevel). Count = grams × 40 (flake), × 25 (mysis), nori = one 4×6 cm sheet mesh at the clip anchor (`NORI_CLIP` at the left glass, y 35) that fish nibble (a `remaining` scalar scales the sheet).
- Particle physics: `v' = (flow(p) − v) × 2.0 + g_sink`, with `g_sink` = flake 0.6 cm/s terminal (floats 30–60 s first: buoyant phase with `sink = 0` for `t < 45 s`), mysis 1.5 cm/s, pellet 3 cm/s. Collide with SDF → settles on rock (`settled=true`, available to shrimp/blenny/eel); on sand → settles; after 20 min uneaten → `detritus += massPerParticle`, particle removed. Max 400 particles.
- Fish bite radius 1.2 cm; competition: a particle targeted by two fish goes to the first to reach it; clowns and tang get burst speed when hunger > 0.4.
- "Frenzy" score: all diet-matching fish with hunger > 0.2 get `Feed` ≥ 0.6 immediately on spawn (perception event), producing the rush.

### 4.7 Lights and sleep
`LightSchedule.intensity(t)` and channels from the app's schedule (default 03 §F5 ramp scaled to a 10-h photoperiod: moon 00:00–08:00 at 2%, dawn 08:00–09:30, peak 10:30–18:00, dusk 18:00–20:00, moon after). Fish react to the derivative too: lights-on within the last 2 min → DIURNAL fish `Flee`-lite (stress +0.05, dart 1 s) then resume; lights-off → within 10 min diurnal fish go to sleep anchors; nocturnal activate. Tap-glass at night startles more (×1.5).

### 4.8 Body spline (shared by sim and renderer)
Each organism keeps a trail of its head positions: a ring buffer sampled every `BL/16` cm of travel (so the body occupies the last `BL` of trail). `spine[i]` for i=0..15 is the trail point at arclength `i × BL/15` behind the head, with the frame (tangent = finite difference, up = smoothed organism up, right = cross). When the fish turns in place or hovers, the trail is relaxed toward a straight line behind the heading at rate 4/s so the body straightens. Eel: BL up to 80 cm → the trail naturally snakes through crevices; in the den, the trail is replaced by the baked `den_path`. For a straight cruise the lateral wave in the vertex shader adds the propulsive undulation (fish: small amplitude at the head, large at the tail; eel: full-body wave with `waveLen 0.6 BL`, `amp 0.06 BL`).

### 4.9 Determinism
`Rng` is xorshift128+ seeded from `tankId.hashCode()`; all behavior randomness goes through it so unit tests can assert trajectories.

---

## 5. Environment

### 5.1 Rock data (`RockData`)
Loaded from `assets/sim/rock_sdf.bin` + `anchors.json`. The sim module gets a `RockData` constructed by the app from the files; tests use an analytic `RockData.ofSpheres(...)` builder.

### 5.2 SDF file format
Header (little-endian): magic `RSDF`, uint32 nx ny nz, float32 originX/Y/Z, float32 cell; then nx·ny·nz float16 distances (cm); then nx·ny·nz uint8 cover scores. For the 122×33×61 tank at 1 cm: 245,586 cells → ~740 KB. As baked (ASSETS §3.5): nx = 122, ny = 61, nz = 33 (x, y-up, z), origin (0.5, 0.5, 0.5) = first cell centre, index = x + nx·(y + ny·z) (x fastest); cover byte 255 inside rock.

### 5.3 Flow field (`FlowField`)
Two powerheads (from the photos): **PH-A** at the back glass upper-center-right (x 84, y 48, z 1), aiming toward the front-left-down (dir (−0.6, −0.35, 0.72)); **PH-B** on the left glass upper (x 1, y 42, z 10), aiming right and slightly down (dir (0.95, −0.2, 0.25)); **HOB return** at the back center top (x 61, y 55, z 2), aiming forward-down (0, −0.5, 0.87) at low speed. Each jet: velocity at point p: `u = axis·(p − src)`, `r = |(p − src) − u axis|`, `v = V0 × (R0/(R0 + 0.08 u))² × exp(−(r/(R0 + 0.12 u))²)` for u > 0, with `V0 = 60 cm/s`, `R0 = 2.5 cm` (powerheads, ~800 gph class), HOB `V0 = 25`. Plus a global slow return circulation `v_g = 1.5 cm/s × (−sign(x − 61), 0, 0)` near the bottom (y < 20) and `+x` near the top, and rock shadowing: `v × clamp(sdf(p)/8, 0.15, 1)`. Powerheads off → only `v_g × 0.2`. Flow speed is sampled by corals (sway amplitude, retraction) and by fish (station holding, surfing spots: points where speed 8–20 cm/s).

### 5.4 Particulate
300 ambient particles advected by `flow` plus Brownian jitter (1 cm/s), respawned when out of the box.

### 5.5 Uneaten fraction
Computed per feed event: `u = remaining particles after 20 min / spawned`; influences detritus (§2.3).

### 5.6 Glass tap
`TapGlass(point)`: every fish within 30 cm gets `startle = skittishness × (1 − dist/30)`; `Flee` if startle > 0.35 else a flinch (0.3 s burst away 1 L); curious species may approach after 5 s.

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
