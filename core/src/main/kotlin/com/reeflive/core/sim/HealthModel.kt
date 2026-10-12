package com.reeflive.core.sim

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Per-tick context the health model needs from the rest of the simulation. */
data class HealthContext(
    val simTimeSec: Double,
    val volumeL: Double,
    /** Σ ICH stage over *other* infected fish (infection pressure). */
    val infectedIchSum: Double = 0.0,
    /** Cleaner wrasse: other fish present that could be clients. */
    val clientsAvailable: Boolean = true,
    /** A same-niche rival is present (lawnmower + tailspot blenny; research 04 §15). */
    val rivalPresent: Boolean = false,
    val night: Boolean = false,
    /** Sandbox pump strength (FlowField.strength, 0..2; 1 = Tee's powerheads). */
    val flowStrength: Double = 1.0,
    /** Light intensity 0..1.5 (stress light channel). */
    val light: Double = 0.5,
)

/**
 * Organism health (SIMULATION §3). [step] advances `dtMin` minutes (the chem tick is 1 min).
 * Rates follow the spec structure; magnitudes were re-tuned so timelines match research 02
 * (ich spots ~2 days after a 30 °C stress, death 2–3 weeks untreated; wrasse starvation 3–6
 * weeks). See SIMULATION §3.7 "Tuned constants".
 */
object HealthModel {
    // ---- tuned constants (per minute) ----
    const val STRESS_RISE = 0.08
    const val STRESS_FALL = 0.02
    const val CHRONIC_RATE = 0.0007
    const val ICH_BASE_PRESSURE = 0.0005
    const val ICH_GROWTH = 0.0000165
    const val ICH_RECOVERY = 0.0000035
    const val BACT_GROWTH = 0.0002
    const val BACT_RECOVERY = 0.00008
    const val K_STRESS = 0.00016
    const val K_DISEASE = 0.00035
    const val K_REGEN = 0.0003
    const val K_LETHAL = 0.0004
    const val K_STARVE = 0.003
    const val HUNGER_DIVISOR = 1440.0 * 1.4

    /**
     * Calibrated group profiles (research 05 §18; NH3 in ppm NH3 = 1.214 × NH3-N, NO2 in ppm NO2⁻ = 3.28 × NO2-N).
     * They take precedence over sheet tolerances, which in v1 are generic copies; a sheet may still add keys.
     * Fish: 31 °C → gasping, reduced appetite in hours; 32–33 °C acute; NH3-N 0.05 safe, 0.25–0.5 lethargy,
     * 0.5–1.0 mortality in ~24 h; DO 4–5 stress, 3–4 gasping, < 3 mortality in hours.
     */
    private val DEFAULT_TOL = mapOf(
        "temp" to ToleranceRange(23.5, 28.5, 22.0, 30.5, 17.0, 33.0),
        "nh3Free" to ToleranceRange(optHi = 0.02, tolHi = 0.06, lethalHi = 0.6),
        "no2" to ToleranceRange(optHi = 0.3, tolHi = 1.6, lethalHi = 3.3),
        "no3" to ToleranceRange(optHi = 30.0, tolHi = 60.0, lethalHi = 200.0),
        "do" to ToleranceRange(optLo = 5.0, tolLo = 4.0, lethalLo = 3.0),
        "ph" to ToleranceRange(7.85, 8.45, 7.65, 8.6, 7.4, 8.9),
        "sg" to ToleranceRange(1.021, 1.0265, 1.018, 1.028, 1.012, 1.032),
    )
    /** Invertebrates are more sensitive than fish (research 05 §2, §4, §8, §12). */
    /** mg/L iodide+iodate; NSW ≈ 0.06. Below ~0.02 molts start failing (Simulation.crustaceanMolts). */
    val CRUSTACEAN_IODINE = ToleranceRange(optLo = 0.04, tolLo = 0.02, lethalLo = 0.003)

    private val INVERT_TOL = mapOf(
        "temp" to ToleranceRange(24.0, 28.0, 22.5, 29.5, 18.0, 32.0),
        "nh3Free" to ToleranceRange(optHi = 0.01, tolHi = 0.04, lethalHi = 0.35),
        "no2" to ToleranceRange(optHi = 0.15, tolHi = 0.8, lethalHi = 2.0),
        "no3" to ToleranceRange(optHi = 20.0, tolHi = 50.0, lethalHi = 120.0),
        "do" to ToleranceRange(optLo = 5.5, tolLo = 4.5, lethalLo = 3.5),
        "ph" to ToleranceRange(7.9, 8.4, 7.75, 8.55, 7.5, 8.8),
        "sg" to ToleranceRange(1.023, 1.026, 1.021, 1.0275, 1.018, 1.030),
        "mg" to ToleranceRange(optLo = 1200.0, tolLo = 1050.0, lethalLo = 800.0),
        "ca" to ToleranceRange(optLo = 380.0, tolLo = 330.0, lethalLo = 200.0),
    )
    private val INVERT_COPPER = ToleranceRange(optHi = 0.005, tolHi = 0.03, lethalHi = 0.1)
    private val FISH_COPPER = ToleranceRange(optHi = 0.05, tolHi = 0.25, lethalHi = 0.5)

    /**
     * Rate-of-change shock (SIMULATION §9.6): the animal is acclimated to an exponentially lagged
     * value; the gap |current − acclimated| in units of a "safe step" drives stress through
     * [SHOCK_RANGE] (0.4 step = noticeable, 1 step = tolerable limit, 2.5 steps = lethal).
     */
    data class ShockSteps(val temp: Double, val sg: Double, val ph: Double)
    // research 05: fish shrug off 0.003–0.005 SG/h, osmotic stress at 0.008–0.010, mortality at 0.015+;
    // shrimp ~0.002 SG/h. Temp swings > 1 °C/h noticeable, 2–3 °C/h stress. pH steps ~0.1 stress corals.
    val FISH_STEPS = ShockSteps(temp = 3.0, sg = 0.008, ph = 0.5)
    val INVERT_STEPS = ShockSteps(temp = 2.0, sg = 0.004, ph = 0.3)
    /** Sally Lightfoot crab: salinity swings hit it hardest (owner note). */
    val CRAB_STEPS = ShockSteps(temp = 2.0, sg = 0.003, ph = 0.3)
    val SHOCK_RANGE = ToleranceRange(optHi = 0.4, tolHi = 1.0, lethalHi = 1.9)
    /** Acclimation time constants (minutes): temp 12 h, SG 2 days (inverts 3), pH 8 h. */
    const val ACCL_TAU_TEMP = 720.0
    const val ACCL_TAU_SG = 2880.0
    const val ACCL_TAU_SG_INVERT = 4320.0
    const val ACCL_TAU_PH = 480.0

    fun updateAcclimation(o: Organism, w: WaterState, dtMin: Double) {
        if (o.acclTemp.isNaN()) { o.acclTemp = w.tempC; o.acclSg = w.sg; o.acclPh = w.ph; return }
        fun lag(a: Double, v: Double, tau: Double) = a + (v - a) * (1 - kotlin.math.exp(-dtMin / tau))
        o.acclTemp = lag(o.acclTemp, w.tempC, ACCL_TAU_TEMP)
        o.acclSg = lag(o.acclSg, w.sg, if (o.isFish) ACCL_TAU_SG else ACCL_TAU_SG_INVERT)
        o.acclPh = lag(o.acclPh, w.ph, ACCL_TAU_PH)
    }

    /** Appetite multiplier: stressed or failing animals stop eating (a classic early sign). */
    fun appetite(o: Organism): Double {
        val st = ((o.stress - 0.55) / 0.35).coerceIn(0.0, 1.0)
        return (1 - 0.85 * st) * (if (o.health < 0.25) 0.2 else 1.0) * (if (o.disease != null && o.diseaseStage > 0.6) 0.4 else 1.0) *
            StressModel.appetite(o, o.lastTempC)
    }

    /** Tolerance curve (SIMULATION §3.3): returns s in 0..1 and whether the lethal bound is crossed. */
    fun stressOf(v: Double, t: ToleranceRange): Pair<Double, Boolean> {
        val optHi = t.optHi; val tolHi = t.tolHi; val lethalHi = t.lethalHi
        if (optHi != null && v > optHi) {
            if (tolHi != null && v <= tolHi) return Pair(sq((v - optHi) / max(tolHi - optHi, 1e-9)) * 0.4, false)
            val th = tolHi ?: optHi
            if (lethalHi != null && v >= lethalHi) return Pair(1.0, true)
            val lh = lethalHi ?: (th + (th - optHi).coerceAtLeast(1e-6) * 2)
            return Pair((0.4 + 0.6 * ((v - th) / max(lh - th, 1e-9)).pow(1.5)).coerceAtMost(1.0), false)
        }
        val optLo = t.optLo; val tolLo = t.tolLo; val lethalLo = t.lethalLo
        if (optLo != null && v < optLo) {
            if (tolLo != null && v >= tolLo) return Pair(sq((optLo - v) / max(optLo - tolLo, 1e-9)) * 0.4, false)
            val tl = tolLo ?: optLo
            if (lethalLo != null && v <= lethalLo) return Pair(1.0, true)
            val ll = lethalLo ?: (tl - (optLo - tl).coerceAtLeast(1e-6) * 2)
            return Pair((0.4 + 0.6 * ((tl - v) / max(tl - ll, 1e-9)).pow(1.5)).coerceAtMost(1.0), false)
        }
        return Pair(0.0, false)
    }

    private fun sq(x: Double) = x * x

    private fun waterValue(key: String, w: WaterState): Double? = when (key) {
        "temp" -> w.tempC; "sg" -> w.sg; "ph" -> w.ph; "nh3Free", "nh3free" -> w.nh3Free
        "nh3", "nh3Total" -> w.nh3Total; "no2" -> w.no2; "no3" -> w.no3; "do" -> w.doMgL
        "po4" -> w.po4; "alk" -> w.alk; "ca" -> w.ca; "mg" -> w.mg; "copper" -> w.copper; "i", "iodine" -> w.i
        else -> null
    }

    fun tolerancesFor(s: SimSpecies): Map<String, ToleranceRange> {
        val fish = s.kind.equals("FISH", true)
        val m = LinkedHashMap<String, ToleranceRange>()
        s.tolerance.forEach { (k, v) -> m[k] = v }
        (if (fish) DEFAULT_TOL else INVERT_TOL).forEach { (k, v) -> m[k] = v }
        m["copper"] = if (fish) FISH_COPPER else INVERT_COPPER
        // Species modifiers (research 05 §1, §8): tangs and cardinals fail first in heat and low oxygen.
        when (s.brainKind) {
            BrainKind.TANG, BrainKind.CARDINAL -> {
                m["temp"] = ToleranceRange(23.5, 28.5, 22.0, 30.0, 18.0, 32.5)
                m["do"] = ToleranceRange(optLo = 5.5, tolLo = 4.5, lethalLo = 3.2)
            }
            BrainKind.EEL -> m["nh3Free"] = ToleranceRange(optHi = 0.03, tolHi = 0.09, lethalHi = 0.8)
            // Crustaceans need iodine to molt; the Sally Lightfoot also tolerates salinity swings poorly (SIMULATION §9.6).
            BrainKind.SALLY_CRAB, BrainKind.CLEANER_SHRIMP, BrainKind.BANDED_SHRIMP -> {
                m["i"] = CRUSTACEAN_IODINE
                if (s.brainKind == BrainKind.SALLY_CRAB) m["sg"] = ToleranceRange(1.024, 1.026, 1.022, 1.027, 1.019, 1.029)
            }
            else -> {}
        }
        return m
    }

    /** Environmental stress S_env and per-parameter parts; also sets [Organism.lethalExposure] / [Organism.lethalOvershoot]. */
    fun envStress(o: Organism, w: WaterState, flowStrength: Double = 1.0): Double {
        var prod = 1.0
        var lethal = false
        var over = 0.0
        o.envStress.clear()
        fun add(key: String, v: Double, tr: ToleranceRange) {
            val (st, l) = stressOf(v, tr)
            if (st > 0.001) o.envStress[key] = st
            prod *= (1 - st)
            if (l) {
                lethal = true
                tr.lethalHi?.let { lh -> if (v >= lh) over = max(over, (v - lh) / max(lh - (tr.tolHi ?: tr.optHi ?: lh * 0.9), 1e-6)) }
                tr.lethalLo?.let { ll -> if (v <= ll) over = max(over, (ll - v) / max((tr.tolLo ?: tr.optLo ?: ll * 1.1) - ll, 1e-6)) }
            }
        }
        for ((k, tr) in tolerancesFor(o.species)) add(k, waterValue(k, w) ?: continue, tr)
        if (!o.acclTemp.isNaN()) {
            val st = if (o.isFish) FISH_STEPS else if (o.brainKind == BrainKind.SALLY_CRAB) CRAB_STEPS else INVERT_STEPS
            add("shockTemp", abs(w.tempC - o.acclTemp) / st.temp, SHOCK_RANGE)
            add("shockSg", abs(w.sg - o.acclSg) / st.sg, SHOCK_RANGE)
            add("shockPh", abs(w.ph - o.acclPh) / st.ph, SHOCK_RANGE)
        }
        // Strong flow: weak swimmers (< 1.2 BL/s cruise) tire holding station; everyone above ~1.7×.
        if (o.isFish && o.brainKind != BrainKind.EEL) {
            val weak = o.species.locomotion.cruiseBL < 1.2
            val sFlow = ((flowStrength - (if (weak) 1.25 else 1.7)) / 0.6).coerceIn(0.0, 1.0) * (if (weak) 0.5 else 0.3)
            if (sFlow > 0.001) { o.envStress["flow"] = sFlow; prod *= (1 - sFlow) }
        }
        o.lethalExposure = lethal
        o.lethalOvershoot = over
        return 1 - prod
    }

    fun metabolicMult(tempC: Double) = 2.0.pow((tempC - 25) / 10)

    /** Hunger rise per minute while awake (SIMULATION §3.4, tuned). */
    fun hungerRatePerMin(o: Organism, tempC: Double) = o.species.feedsPerDay / HUNGER_DIVISOR * metabolicMult(tempC)

    fun step(o: Organism, w: WaterState, ctx: HealthContext, dtMin: Double, events: MutableList<SimEvent>) {
        if (!o.alive) { updateDeadVisuals(o); return }
        val s = o.species
        updateAcclimation(o, w, dtMin)
        o.lastTempC = w.tempC
        val sEnvTol = envStress(o, w, ctx.flowStrength)
        // research 06 §4.1: per-channel acute/chronic stress against acclimated setpoints
        val sEnvNew = StressModel.update(o, w, StressModel.Env(ctx.light, ctx.flowStrength), dtMin)
        val sEnv = max(sEnvTol, sEnvNew)
        // ---- other stress sources (decay per minute) ----
        o.socialStress = max(0.0, o.socialStress - 0.1 * dtMin)
        o.startle = max(0.0, o.startle - 0.2 * dtMin)
        var sSpace = 0.0
        if (o.brainKind == BrainKind.TANG && o.lengthCm > 10 && ctx.volumeL < 450) sSpace = 0.05 + 0.01 * (o.lengthCm - 10)
        if (o.brainKind == BrainKind.EEL && o.lengthCm > 50) sSpace = 0.05
        val sDark = if (o.brainKind == BrainKind.WRASSE && !ctx.clientsAvailable) 0.05 else 0.0
        val sHunger = 0.3 * max(0.0, o.hunger - 0.6)
        val sRival = if (ctx.rivalPresent) 0.05 else 0.0
        val target = min(1.0, sEnv + o.socialStress.coerceAtMost(0.25) + sSpace + o.startle.coerceAtMost(0.45) + sDark + sHunger + sRival)
        // ---- dynamics ----
        val k = if (target > o.stress) STRESS_RISE else STRESS_FALL
        o.stress += (target - o.stress) * (1 - (1 - k).pow(dtMin))
        o.chronic += (o.stress - o.chronic) * (1 - (1 - CHRONIC_RATE).pow(dtMin))

        val mm = metabolicMult(w.tempC)
        val sleepMult = if (o.sleeping) 0.5 else 1.0
        o.hunger = min(1.0, o.hunger + s.feedsPerDay / HUNGER_DIVISOR * mm * sleepMult * dtMin)
        o.condition += (-1.0 / (s.starvationDays * 1440) * max(0.0, o.hunger - 0.5) * 2 + (if (o.hunger < 0.4) 0.00015 else 0.0)) * dtMin
        o.condition = o.condition.coerceIn(0.0, 1.0)
        o.nutritionGreens = max(0.0, o.nutritionGreens - dtMin / (7 * 1440.0))

        o.immunity += (0.002 * (1 - o.immunity) * (1 - o.chronic) - 0.004 * o.chronic - 0.003 * max(0.0, o.hunger - 0.7)) * dtMin
        o.immunity = o.immunity.coerceIn(0.0, 1.0)

        // ---- disease ----
        val tF = if (w.tempC > 28) 4.0 else if (w.tempC > 26.5) 1.5 else 1.0
        val hardInv = (2 * (1 - s.hardiness)).coerceIn(0.2, 2.0)
        val sNh3 = o.envStress["nh3Free"] ?: 0.0
        val sNo2 = o.envStress["no2"] ?: 0.0
        if (o.disease == null) {
            val ichSus = s.diseaseSusceptibility["ICH"] ?: if (o.isFish) 0.3 else 0.0
            val pIch = ICH_BASE_PRESSURE * tF * (1 + 3 * ctx.infectedIchSum) * ichSus * sq(1 - o.immunity) * hardInv
            val pBact = if (sNh3 + sNo2 > 0.3) 0.0005 * (sNh3 + sNo2) * (1 - o.immunity) else 0.0
            val hlleSus = s.diseaseSusceptibility["HLLE"] ?: 0.0
            when {
                o.isFish && o.rng.chance(pIch * dtMin) -> onset(o, Disease.ICH, 0.05, ctx, events)
                o.rng.chance(pBact * dtMin) -> onset(o, Disease.BACTERIAL, 0.05, ctx, events)
                hlleSus > 0 && o.nutritionGreens < 0.2 && o.rng.chance(0.0002 * hlleSus * dtMin) -> onset(o, Disease.HLLE, 0.05, ctx, events)
            }
        } else {
            val d = when (o.disease!!) {
                Disease.ICH -> ICH_GROWTH * (1 - o.immunity) * tF - ICH_RECOVERY * o.immunity
                Disease.VELVET -> 2 * ICH_GROWTH * (1 - o.immunity) * tF - ICH_RECOVERY * o.immunity
                Disease.BACTERIAL -> BACT_GROWTH * (sNh3 + sNo2) - BACT_RECOVERY * o.immunity
                Disease.HLLE -> 0.00005 * (if (o.nutritionGreens < 0.3) 1.0 else 0.0) - 0.00002
            }
            o.diseaseStage = (o.diseaseStage + d * dtMin).coerceAtMost(1.0)
            if (o.diseaseStage <= 0) {
                events += SimEvent.Recovered(o.id, o.disease!!, ctx.simTimeSec)
                o.disease = null; o.diseaseStage = 0.0
            }
        }
        val stage = if (o.disease == Disease.HLLE) o.diseaseStage * 0.2 else o.diseaseStage

        // ---- health ----
        val overshoot = o.lethalOvershoot
        var dh = -K_STRESS * sq(o.stress) - K_DISEASE * sq(stage) - K_STARVE * max(0.0, 0.5 - o.condition)
        if (o.lethalExposure) dh -= K_LETHAL * (1 + min(4.0, overshoot))
        if (o.health < 1) dh += K_REGEN * (1 - o.stress) * (1 - stage)
        o.health = (o.health + dh * dtMin).coerceAtMost(1.0)
        if (o.health <= 0) {
            o.health = 0.0
            kill(o, causeOf(o, sEnv), ctx.simTimeSec, events)
            return
        }
        updateSymptoms(o, w, ctx, events)
    }

    private fun onset(o: Organism, d: Disease, stage: Double, ctx: HealthContext, events: MutableList<SimEvent>) {
        o.disease = d; o.diseaseStage = stage
        events += SimEvent.DiseaseOnset(o.id, d, ctx.simTimeSec)
    }

    private fun causeOf(o: Organism, sEnv: Double): String = when {
        o.condition < 0.3 -> "starvation"
        o.disease != null && o.diseaseStage > 0.5 -> o.disease!!.name.lowercase()
        o.lethalExposure || sEnv > 0.6 -> "water: " + label(o.envStress.maxByOrNull { it.value }?.key ?: "parameters")
        else -> "stress"
    }

    /** Human words for a stress key (death cause, fish card "why"). */
    fun label(key: String): String = when (key) {
        "temp" -> "temperature"; "sg" -> "salinity"; "ph" -> "pH"; "nh3Free" -> "ammonia"; "no2" -> "nitrite"
        "no3" -> "nitrate"; "do" -> "low oxygen"; "mg" -> "magnesium"; "copper" -> "copper"; "i" -> "low iodine"; "flow" -> "strong flow"
        "shockTemp" -> "temperature swing"; "shockSg" -> "salinity swing"; "shockPh" -> "pH swing"
        else -> key
    }

    fun kill(o: Organism, cause: String, simTimeSec: Double, events: MutableList<SimEvent>) {
        if (!o.alive) return
        o.alive = false
        o.health = 0.0
        o.deadSince = simTimeSec
        o.deathCause = cause
        o.sleeping = false
        events += SimEvent.Died(o.id, cause, simTimeSec)
        updateDeadVisuals(o)
    }

    private fun updateDeadVisuals(o: Organism) {
        o.symptoms.clear(); o.symptoms += Symptom.DEAD
        o.mood = Mood.DEAD
        o.desat = 0.8; o.eyeCloud = 1.0; o.finClamp = 1.0; o.gill = 0.0; o.amp = 0.0
    }

    /** Symptom → render uniforms mapping (SIMULATION §3.5). */
    fun updateSymptoms(o: Organism, w: WaterState, ctx: HealthContext, events: MutableList<SimEvent>) {
        val sym = o.symptoms
        val hadGasp = Symptom.GASPING in sym
        sym.clear()
        var desat = 0.0
        val ss = o.stressState
        if (o.stress > 0.35) { sym += Symptom.COLOR_FADE; desat = (o.stress - 0.35) / 0.65 * 0.6 }
        // colour loss from chronic load (06 §4.1.3)
        desat += 0.4 * StressModel.CHANNELS.maxOf { ss.slow(it) * it.weight }
        val clampOld = if (o.stress > 0.5) ((o.stress - 0.5) * 2).coerceAtMost(1.0) else 0.0
        o.finClamp = maxOf(clampOld, StressModel.finClamp(o, w.tempC, o.brightExposed && ctx.light > 0.6))
        if (o.finClamp > 0.3) sym += Symptom.CLAMPED_FINS
        // Signs are driven by the responsible channel (06 §4.1.3): heat/O2 → breathing first, ammonia → clamp and hide.
        val hz = StressModel.gillHz(o, w.tempC)
        o.gillRate = hz
        o.gill = (0.3 + 0.25 * (hz - 1)).coerceIn(0.3, 1.0)
        if (hz > 1.7) sym += Symptom.RAPID_GILLS
        val doFast = ss.fast(StressModel.Ch.DO)
        val column = StressModel.isColumnFish(o.brainKind, ctx.night)
        val gasp = o.isFish && (w.doMgL < 2.0 || (column && (doFast > 0.5 || w.nh3Free >= 0.25)) || (!column && doFast > 0.9))
        if (gasp) {
            sym += Symptom.GASPING; o.gill = 1.0
            if (!hadGasp) { events += SimEvent.Gasping(o.id, ctx.simTimeSec); o.counters.add(Counters.K.GASP, ctx.simTimeSec) }
        }
        // benthic fish in hypoxia or any animal under heavy load go to cover instead (S:Yu23)
        if (o.isFish && !column && doFast > 0.5 || ss.sEnv > 0.5) sym += Symptom.HIDING
        if (o.stress > 0.65) { sym += Symptom.HIDING; sym += Symptom.LETHARGY }
        // prolonged ammonia clouds the eyes; clears slowly once water is good
        val sNh3 = o.envStress["nh3Free"] ?: 0.0
        o.eyeCloud = (o.eyeCloud + (if (sNh3 > 0.5) 0.0005 else -0.0002)).coerceIn(0.0, 0.7)
        o.ich = 0.0
        when (o.disease) {
            Disease.ICH, Disease.VELVET -> {
                if (o.diseaseStage > 0.15) { sym += Symptom.WHITE_SPOTS; sym += Symptom.FLASHING; o.ich = o.diseaseStage }
                if (o.diseaseStage > 0.5) { sym += Symptom.LETHARGY; sym += Symptom.HIDING }
            }
            Disease.HLLE -> if (o.diseaseStage > 0.2) sym += Symptom.LESIONS
            Disease.BACTERIAL -> if (o.diseaseStage > 0.3) { sym += Symptom.LESIONS; sym += Symptom.LETHARGY }
            null -> {}
        }
        o.thin = if (o.condition < 0.7) { sym += Symptom.THIN; ((0.7 - o.condition) / 0.3).coerceIn(0.0, 1.0) } else 0.0
        if (o.health < 0.3) { sym += Symptom.LETHARGY; desat += 0.3 }
        if (o.molting) sym += Symptom.MOLTING
        o.desat = desat.coerceIn(0.0, 1.0)
        o.mood = when {
            o.sleeping -> Mood.SLEEPING
            o.disease != null && o.diseaseStage > 0.15 || o.health < 0.5 -> Mood.SICK
            o.stress > 0.5 -> Mood.STRESSED
            o.hunger > 0.65 -> Mood.HUNGRY
            o.stress > 0.3 || o.startle > 0.1 -> Mood.ALERT
            else -> Mood.CALM
        }
    }
}
