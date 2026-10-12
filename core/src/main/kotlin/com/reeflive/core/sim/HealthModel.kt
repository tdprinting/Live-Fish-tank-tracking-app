package com.reeflive.core.sim

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
    const val ICH_BASE_PRESSURE = 0.0004
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

    private val DEFAULT_TOL = mapOf(
        "nh3Free" to ToleranceRange(optHi = 0.0, tolHi = 0.01, lethalHi = 0.05),
        "no2" to ToleranceRange(optHi = 0.0, tolHi = 0.15, lethalHi = 1.0),
        "do" to ToleranceRange(optLo = 6.0, tolLo = 5.0, lethalLo = 2.5),
        "ph" to ToleranceRange(8.0, 8.4, 7.9, 8.5, 7.5, 8.9),
        "sg" to ToleranceRange(1.022, 1.026, 1.020, 1.027, 1.016, 1.030),
    )
    private val INVERT_COPPER = ToleranceRange(optHi = 0.0, tolHi = 0.02, lethalHi = 0.1)
    private val FISH_COPPER = ToleranceRange(optHi = 0.05, tolHi = 0.25, lethalHi = 0.5)

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
        "po4" -> w.po4; "alk" -> w.alk; "ca" -> w.ca; "mg" -> w.mg; "copper" -> w.copper
        else -> null
    }

    fun tolerancesFor(s: SimSpecies): Map<String, ToleranceRange> {
        val m = LinkedHashMap<String, ToleranceRange>()
        DEFAULT_TOL.forEach { (k, v) -> m[k] = v }
        m["copper"] = if (s.kind.equals("FISH", true)) FISH_COPPER else INVERT_COPPER
        s.tolerance.forEach { (k, v) -> m[k] = v }
        if (!s.kind.equals("FISH", true)) m.remove("do")?.let { m["do"] = ToleranceRange(optLo = 5.0, tolLo = 4.0, lethalLo = 1.5) }
        return m
    }

    /** Environmental stress S_env and per-parameter parts; also sets [Organism.lethalExposure]. */
    fun envStress(o: Organism, w: WaterState): Double {
        var prod = 1.0
        var lethal = false
        o.envStress.clear()
        for ((k, tr) in tolerancesFor(o.species)) {
            val v = waterValue(k, w) ?: continue
            val (s, l) = stressOf(v, tr)
            if (s > 0.001) o.envStress[k] = s
            prod *= (1 - s)
            lethal = lethal || l
        }
        o.lethalExposure = lethal
        return 1 - prod
    }

    fun metabolicMult(tempC: Double) = 2.0.pow((tempC - 25) / 10)

    /** Hunger rise per minute while awake (SIMULATION §3.4, tuned). */
    fun hungerRatePerMin(o: Organism, tempC: Double) = o.species.feedsPerDay / HUNGER_DIVISOR * metabolicMult(tempC)

    fun step(o: Organism, w: WaterState, ctx: HealthContext, dtMin: Double, events: MutableList<SimEvent>) {
        if (!o.alive) { updateDeadVisuals(o); return }
        val s = o.species
        val sEnv = envStress(o, w)
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
        val overshoot = lethalOvershoot(o, w)
        var dh = -K_STRESS * sq(o.stress) - K_DISEASE * sq(stage) - K_STARVE * max(0.0, 0.5 - o.condition)
        if (o.lethalExposure) dh -= K_LETHAL * (1 + min(2.0, overshoot))
        if (o.health < 1) dh += K_REGEN * (1 - o.stress) * (1 - stage)
        o.health = (o.health + dh * dtMin).coerceAtMost(1.0)
        if (o.health <= 0) {
            o.health = 0.0
            kill(o, causeOf(o, sEnv), ctx.simTimeSec, events)
            return
        }
        updateSymptoms(o, w, ctx, events)
    }

    private fun lethalOvershoot(o: Organism, w: WaterState): Double {
        var best = 0.0
        for ((k, t) in tolerancesFor(o.species)) {
            val v = waterValue(k, w) ?: continue
            t.lethalHi?.let { lh -> if (v >= lh) { val span = lh - (t.tolHi ?: t.optHi ?: lh * 0.9); best = max(best, (v - lh) / max(span, 1e-6)) } }
            t.lethalLo?.let { ll -> if (v <= ll) { val span = (t.tolLo ?: t.optLo ?: ll * 1.1) - ll; best = max(best, (ll - v) / max(span, 1e-6)) } }
        }
        return best
    }

    private fun onset(o: Organism, d: Disease, stage: Double, ctx: HealthContext, events: MutableList<SimEvent>) {
        o.disease = d; o.diseaseStage = stage
        events += SimEvent.DiseaseOnset(o.id, d, ctx.simTimeSec)
    }

    private fun causeOf(o: Organism, sEnv: Double): String = when {
        o.condition < 0.3 -> "starvation"
        o.disease != null && o.diseaseStage > 0.5 -> o.disease!!.name.lowercase()
        o.lethalExposure || sEnv > 0.6 -> "water: " + (o.envStress.maxByOrNull { it.value }?.key ?: "parameters")
        else -> "stress"
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
        if (o.stress > 0.35) { sym += Symptom.COLOR_FADE; desat = (o.stress - 0.35) / 0.65 * 0.6 }
        o.finClamp = if (o.stress > 0.5) { sym += Symptom.CLAMPED_FINS; ((o.stress - 0.5) * 2).coerceAtMost(1.0) } else 0.0
        val sDo = o.envStress["do"] ?: 0.0
        val sTempHi = if (w.tempC > 26) (o.envStress["temp"] ?: 0.0) else 0.0
        if (sDo > 0.3 || sTempHi > 0.4) {
            sym += Symptom.RAPID_GILLS
            val sv = max(sDo, sTempHi)
            o.gillRate = 1.5 + 3 * sv; o.gill = 0.6
        } else { o.gillRate = 1.0; o.gill = 0.3 }
        if (w.doMgL < 4 && o.isFish) {
            sym += Symptom.GASPING; o.gill = 1.0
            if (!hadGasp) events += SimEvent.Gasping(o.id, ctx.simTimeSec)
        }
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
