package com.reeflive.core.sim

import com.reeflive.core.geom.Vec3
import com.reeflive.core.geom.smoothstep
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
    val melting: Boolean get() = tissue < 0.2

    fun toPose() = CoralPose(
        id, extension.toFloat(), bleach.toFloat(), brown.toFloat(), algaeCover.toFloat(), swayPhase.toFloat(),
        species.id, anchor, flowSpeed.toFloat(), tissue.toFloat(),
    )
}

object CoralSim {
    data class Params(val temp: ToleranceRange, val nightExtension: Double, val flowSensitive: Boolean, val needsFlow: Boolean, val lightHi: Double, val lightLo: Double)

    fun params(k: CoralKind): Params = when (k) {
        CoralKind.KENYA -> Params(ToleranceRange(24.5, 27.5, 23.0, 29.0, 20.0, 30.5), 0.5, true, false, 0.85, 0.15)
        CoralKind.GSP -> Params(ToleranceRange(24.5, 27.0, 23.0, 29.0, 20.0, 31.0), 0.15, false, true, 0.95, 0.15)
        CoralKind.ZOA -> Params(ToleranceRange(24.5, 27.0, 23.0, 29.0, 20.0, 31.0), 0.3, false, false, 0.9, 0.2)
        CoralKind.CLOVE -> Params(ToleranceRange(24.5, 27.0, 23.0, 29.0, 20.0, 31.0), 0.35, false, true, 0.85, 0.1)
        CoralKind.MUSHROOM -> Params(ToleranceRange(24.0, 27.5, 22.0, 29.0, 20.0, 31.0), 0.2, false, false, 0.9, 0.05)
        CoralKind.MACRO -> Params(ToleranceRange(22.0, 28.0, 20.0, 30.0, 17.0, 32.0), 1.0, false, false, 1.1, 0.0)
        CoralKind.ORGAN_PIPE -> Params(ToleranceRange(25.0, 28.0, 24.0, 29.0, 21.0, 30.5), 0.1, false, false, 0.95, 0.35)
        CoralKind.AIPTASIA -> Params(ToleranceRange(22.0, 28.0, 20.0, 30.0, 17.0, 33.0), 1.0, false, false, 1.1, 0.0)
        CoralKind.GENERIC -> Params(ToleranceRange(24.5, 27.0, 23.0, 29.0, 20.0, 31.0), 0.3, false, false, 0.9, 0.15)
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

    fun step(
        c: Coral, w: WaterState, lightIntensity: Double, minutesSinceOn: Double?, minutesUntilOff: Double?,
        dtMin: Double, simTimeSec: Double, events: MutableList<SimEvent>,
    ) {
        val p = params(c.kind)
        val (sTemp, _) = HealthModel.stressOf(w.tempC, p.temp)
        val (sSg, _) = HealthModel.stressOf(w.sg, ToleranceRange(1.023, 1.027, 1.020, 1.029, 1.015, 1.033))
        val (sAlk, _) = HealthModel.stressOf(w.alk, ToleranceRange(7.0, 11.0, 6.0, 12.0, 4.0, 14.0))
        val (sNh3, _) = HealthModel.stressOf(w.nh3Free, ToleranceRange(optHi = 0.005, tolHi = 0.02, lethalHi = 0.1))
        val (sPo4, _) = HealthModel.stressOf(w.po4, ToleranceRange(optHi = 0.15, tolHi = 0.3, lethalHi = 1.0))
        val (sCu, _) = HealthModel.stressOf(w.copper, ToleranceRange(optHi = 0.02, tolHi = 0.1, lethalHi = 0.3))
        val sLight = if (minutesSinceOn == null) 0.0 else when {
            lightIntensity > p.lightHi -> ((lightIntensity - p.lightHi) / 0.15).coerceIn(0.0, 1.0)
            lightIntensity < p.lightLo && lightIntensity > 0.05 -> ((p.lightLo - lightIntensity) / p.lightLo).coerceIn(0.0, 1.0) * 0.5
            else -> 0.0
        }
        var sFlow = 0.0
        if (p.flowSensitive && w.flowOn && c.flowSpeed > 25) sFlow = 0.3
        if (p.needsFlow && !w.flowOn) sFlow = 0.4
        val parts = doubleArrayOf(sTemp, sSg, sAlk, sNh3, sPo4 * 0.3, sLight, sFlow, sCu)
        var prod = 1.0
        for (s in parts) prod *= (1 - s)
        c.stress = 1 - prod

        c.disturb = max(0.0, c.disturb - 0.25 * dtMin)
        var target = lightOpen(c.kind, minutesSinceOn, minutesUntilOff) * (1 - 0.8 * c.stress) * (if (w.flowOn || !p.needsFlow) 1.0 else 0.5)
        target *= (1 - c.disturb).coerceIn(0.0, 1.0)
        if (c.melting) target = 0.0
        val tau = if (c.disturb > 0.3) 2.0 else 20.0   // snap shut when touched, open slowly
        c.extension += (target - c.extension) * (dtMin / tau).coerceAtMost(1.0)

        c.bleach += (0.0008 * max(0.0, sTemp - 0.5) + 0.0003 * sLight - (if (c.stress < 0.1) 0.000005 else 0.0)) * dtMin
        c.bleach = c.bleach.coerceIn(0.0, 1.0)
        if (c.bleach > 0.5 && !c.bleachingReported) { c.bleachingReported = true; events += SimEvent.CoralBleaching(c.id, simTimeSec) }
        if (c.bleach < 0.2) c.bleachingReported = false
        c.brown += (0.0002 * ((w.po4 - 0.1) / 0.2).coerceIn(0.0, 1.0) + 0.0001 * ((w.no3 - 25) / 25).coerceIn(0.0, 1.0) - 0.00003) * dtMin
        c.brown = c.brown.coerceIn(0.0, 1.0)
        c.tissue += (-0.00005 * (if (c.bleach > 0.8) 1.0 else 0.0) - 0.0002 * sNh3 - 0.00003 * c.algaeCover - 0.0003 * sCu +
            (if (c.stress < 0.1 && c.tissue < 1) 0.00002 else 0.0)) * dtMin
        c.tissue = c.tissue.coerceIn(0.0, 1.0)
        c.algaeCover += (0.001 * w.algaeHair - 0.0003 * c.algaeCover) * dtMin / 60
        c.algaeCover = c.algaeCover.coerceIn(0.0, 1.0)
    }
}
