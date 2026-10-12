package com.reeflive.core.sim

import com.reeflive.core.geom.Vec3
import com.reeflive.core.geom.smoothstep
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

/** One coral / macroalgae colony (SIMULATION §3.6). */
class Coral(
    val id: String,
    val species: SimSpecies,
    val nickname: String,
    val anchor: String,
    val pos: Vec3,
    val normal: Vec3,
    swaySeed: Double,
) {
    val kind: CoralKind = species.coralKind
    var extension = 0.8
    var bleach = 0.0
    var brown = 0.0
    var tissue = 1.0
    var algaeCover = 0.0
    var swayPhase = swaySeed
    var flowSpeed = 0.0
    var stress = 0.0
    var bleachingReported = false
    /** Disturbance (fish contact, food on it) retracts briefly; decays per minute. */
    var disturb = 0.0
    /** Degree heating weeks above the bleaching threshold (NOAA logic, research 05 §1). */
    var dhw = 0.0
    /** Acclimated SG / alkalinity / light (rate-of-change shock); NaN until the first chem tick. */
    var acclSg = Double.NaN
    var acclAlk = Double.NaN
    var acclLight = Double.NaN
    /** Acute heat necrosis D_coral and the bare-skeleton fraction it has exposed (research 08 §5; irreversible). */
    var necrosis = 0.0
    var skeleton = 0.0
    val melting: Boolean get() = tissue < 0.2

    fun toPose() = CoralPose(
        id, extension.toFloat(), bleach.toFloat(), brown.toFloat(), algaeCover.toFloat(), swayPhase.toFloat(),
        species.id, anchor, flowSpeed.toFloat(), tissue.toFloat(), skeleton.toFloat(),
    )
}

object CoralSim {
    data class Params(val temp: ToleranceRange, val nightExtension: Double, val flowSensitive: Boolean, val needsFlow: Boolean, val lightHi: Double, val lightLo: Double)

    fun params(k: CoralKind): Params = when (k) {
        // temp: research 05 §1 — normal 26–28, stress from 30, bleaching trigger 30–31 °C in a tank (est.)
        CoralKind.KENYA -> Params(ToleranceRange(24.0, 28.0, 22.5, 29.5, 18.0, 32.0), 0.5, true, false, 0.85, 0.15)
        CoralKind.GSP -> Params(ToleranceRange(24.0, 28.0, 22.5, 30.0, 18.0, 32.5), 0.15, false, true, 0.95, 0.15)
        CoralKind.ZOA -> Params(ToleranceRange(24.0, 28.0, 22.5, 30.0, 18.0, 32.5), 0.3, false, false, 0.9, 0.2)
        CoralKind.CLOVE -> Params(ToleranceRange(24.0, 28.0, 22.5, 29.5, 18.0, 32.0), 0.35, false, true, 0.85, 0.1)
        CoralKind.MUSHROOM -> Params(ToleranceRange(23.5, 28.0, 22.0, 30.0, 18.0, 32.5), 0.2, false, false, 0.9, 0.05)
        CoralKind.MACRO -> Params(ToleranceRange(22.0, 28.5, 20.0, 30.5, 17.0, 33.0), 1.0, false, false, 1.1, 0.0)
        CoralKind.ORGAN_PIPE -> Params(ToleranceRange(24.5, 28.0, 23.5, 29.5, 20.0, 31.5), 0.1, false, false, 0.95, 0.35)
        CoralKind.AIPTASIA -> Params(ToleranceRange(22.0, 29.0, 20.0, 31.0, 17.0, 34.0), 1.0, false, false, 1.1, 0.0)
        CoralKind.GENERIC -> Params(ToleranceRange(24.0, 28.0, 22.5, 29.5, 18.0, 32.0), 0.3, false, false, 0.9, 0.15)
    }

    /**
     * Extension driven by the light cycle: opens 1–2 h after lights-on, closes 30 min before
     * lights-off (03 §B).
     */
    fun lightOpen(k: CoralKind, minutesSinceOn: Double?, minutesUntilOff: Double?): Double {
        val p = params(k)
        if (minutesSinceOn == null || minutesUntilOff == null) return p.nightExtension
        val open = smoothstep(45.0, 110.0, minutesSinceOn) * smoothstep(0.0, 30.0, minutesUntilOff)
        return p.nightExtension + (1 - p.nightExtension) * open
    }

    /** Bleaching threshold for the DHW accumulator (tank analogue of the local max monthly mean + 1 °C). */
    const val DHW_THRESHOLD_C = 29.0
    val SG_SHOCK = ToleranceRange(optHi = 0.4, tolHi = 1.0, lethalHi = 2.0)   // in units of 0.004 SG
    const val SG_STEP = 0.004
    const val ALK_STEP = 2.0

    fun step(
        c: Coral, w: WaterState, lightIntensity: Double, minutesSinceOn: Double?, minutesUntilOff: Double?,
        dtMin: Double, simTimeSec: Double, events: MutableList<SimEvent>, flowStrength: Double = if (w.flowOn) 1.0 else 0.0,
    ) {
        val p = params(c.kind)
        if (c.acclSg.isNaN()) { c.acclSg = w.sg; c.acclAlk = w.alk; c.acclLight = lightIntensity.coerceAtLeast(0.6) }
        c.acclSg += (w.sg - c.acclSg) * (1 - exp(-dtMin / 2880.0))
        c.acclAlk += (w.alk - c.acclAlk) * (1 - exp(-dtMin / 1440.0))
        if (minutesSinceOn != null && lightIntensity > 0.05) c.acclLight += (lightIntensity - c.acclLight) * (1 - exp(-dtMin / 30240.0))
        val (sTemp, _) = HealthModel.stressOf(w.tempC, p.temp)
        val (sSg, _) = HealthModel.stressOf(w.sg, ToleranceRange(1.023, 1.027, 1.020, 1.029, 1.015, 1.033))
        val (sSgShock, sgLethal) = HealthModel.stressOf(abs(w.sg - c.acclSg) / SG_STEP, SG_SHOCK)
        val (sAlk, _) = HealthModel.stressOf(w.alk, ToleranceRange(7.0, 11.0, 6.0, 12.0, 4.0, 14.0))
        val (sAlkShock, _) = HealthModel.stressOf(abs(w.alk - c.acclAlk) / ALK_STEP, HealthModel.SHOCK_RANGE)
        val (sPh, _) = HealthModel.stressOf(w.ph, ToleranceRange(7.9, 8.4, 7.7, 8.55, 7.3, 8.9))
        val (sNh3, _) = HealthModel.stressOf(w.nh3Free, ToleranceRange(optHi = 0.02, tolHi = 0.1, lethalHi = 0.6))
        val (sPo4, _) = HealthModel.stressOf(w.po4, ToleranceRange(optHi = 0.1, tolHi = 0.3, lethalHi = 1.0))
        val (sCu, _) = HealthModel.stressOf(w.copper, ToleranceRange(optHi = 0.02, tolHi = 0.1, lethalHi = 0.3))
        val sLight = if (minutesSinceOn == null) 0.0 else when {
            lightIntensity > p.lightHi -> ((lightIntensity - p.lightHi) / 0.15).coerceIn(0.0, 1.0)
            lightIntensity < p.lightLo && lightIntensity > 0.05 -> ((p.lightLo - lightIntensity) / p.lightLo).coerceIn(0.0, 1.0) * 0.5
            else -> 0.0
        }
        // Sudden light jump vs the 3-week acclimated level (research 05 §14): retraction then paling.
        val sLightShock = if (minutesSinceOn == null || p.lightLo <= 0.0) 0.0 else ((lightIntensity - c.acclLight - 0.2) / 0.4).coerceIn(0.0, 1.0)
        // Flow (research 05 §15–16): low flow reduces extension; Kenya dislikes strong direct flow.
        var sFlow = 0.0
        if (p.flowSensitive && (c.flowSpeed > 25 || flowStrength > 1.5)) sFlow = 0.3
        if (p.needsFlow && flowStrength < 0.3) sFlow = 0.4
        val parts = doubleArrayOf(sTemp, sSg, sSgShock, sAlk, sAlkShock * 0.5, sPh, sNh3, sPo4 * 0.3, sLight, sLightShock * 0.6, sFlow, sCu)
        var prod = 1.0
        for (s in parts) prod *= (1 - s)
        c.stress = 1 - prod

        c.disturb = max(0.0, c.disturb - 0.25 * dtMin)
        val flowExt = 0.5 + 0.5 * smoothstep(0.05, 0.6, flowStrength)
        var target = lightOpen(c.kind, minutesSinceOn, minutesUntilOff) * (1 - 0.8 * c.stress) * flowExt
        target *= (1 - c.disturb).coerceIn(0.0, 1.0)
        // acute heat (research 08 §5): heavy mucus and retraction from the first sign of necrosis
        if (c.necrosis > 0.02) target *= 0.2
        if (c.melting) target = 0.0
        val tau = if (c.disturb > 0.3 || sSgShock > 0.4) 2.0 else 20.0   // snap shut when touched or osmotically shocked
        c.extension += (target - c.extension) * (dtMin / tau).coerceAtMost(1.0)

        // Heat: NOAA DHW accumulation (4 DHW significant, 8 severe) plus a fast acute response above the tolerance band.
        val wk = dtMin / (7 * 1440.0)
        c.dhw = if (w.tempC > DHW_THRESHOLD_C) c.dhw + (w.tempC - DHW_THRESHOLD_C) * wk else max(0.0, c.dhw - 0.5 * wk)
        val dhwTarget = smoothstep(3.0, 8.0, c.dhw)
        // Acute pathway (research 08 §5, distinct from DHW): above T_necro tissue sloughs instead of bleaching.
        HeatModel.coralStep(c, w.tempC, minutesSinceOn == null, dtMin)
        val acute = HeatModel.coralAcute(c, w.tempC)
        val starving = if (w.no3 < 0.5 && w.po4 < 0.01) 0.00002 else 0.0     // ultra-low nutrients pale soft corals over weeks
        c.bleach += (0.0008 * max(0.0, sTemp - 0.5) * (if (acute) 0.0 else 1.0) + max(0.0, dhwTarget - c.bleach) / (3 * 1440.0) + 0.0003 * sLight + 0.0004 * sLightShock + starving -
            (if (c.stress < 0.15) 0.00001 else 0.0)) * dtMin
        c.bleach = c.bleach.coerceIn(0.0, 1.0)
        if (c.bleach > 0.5 && !c.bleachingReported) { c.bleachingReported = true; events += SimEvent.CoralBleaching(c.id, simTimeSec) }
        if (c.bleach < 0.2) c.bleachingReported = false
        c.brown += (0.0002 * ((w.po4 - 0.1) / 0.2).coerceIn(0.0, 1.0) + 0.0001 * ((w.no3 - 25) / 25).coerceIn(0.0, 1.0) - 0.00003) * dtMin
        c.brown = c.brown.coerceIn(0.0, 1.0)
        c.tissue += (-0.00005 * (if (c.bleach > 0.8) 1.0 else 0.0) - 0.00005 * smoothstep(8.0, 12.0, c.dhw) - 0.0002 * sNh3 -
            0.00003 * c.algaeCover - 0.0003 * sCu - (if (sgLethal) 0.002 else 0.0) - 0.0002 * max(0.0, sPh - 0.6) +
            (if (c.stress < 0.1 && c.tissue < 1) 0.00002 else 0.0)) * dtMin
        // bare skeleton does not regrow tissue; more than half bare and the colony dies (08 §6, §7.2)
        if (c.skeleton > 0.5) c.tissue -= dtMin / 360.0
        c.tissue = c.tissue.coerceIn(0.0, 1.0 - c.skeleton)
        c.algaeCover += (0.001 * w.algaeHair + 0.0005 * w.cyano - 0.0003 * c.algaeCover) * dtMin / 60
        c.algaeCover = c.algaeCover.coerceIn(0.0, 1.0)
    }
}
