package com.reeflive.app.audio

import com.reeflive.core.sim.OrganismStats
import com.reeflive.core.sim.SimEvent
import com.reeflive.core.sim.Symptom
import kotlin.math.sqrt

/** Volumes (0..1, already scaled by the user volume) of the four ambience loops. */
data class AmbienceLevels(val flow: Float, val hum: Float, val hob: Float, val bubbles: Float) {
    companion object {
        val SILENT = AmbienceLevels(0f, 0f, 0f, 0f)
    }
}

/** One-shot sounds; [minGapMs] is the shortest time between two plays of the same cue. */
enum class Cue(val minGapMs: Long) {
    FEED(600),
    PELLET(90),
    CLICK(1_500),
    TICK(120),
    ALERT(4_000),
}

/** Pure mapping from sim state and events to sounds (kept free of Android so it is unit-tested). */
object TankSoundMix {
    /** Night / lights-off floor: ambience never drops below this share of its daytime level. */
    const val NIGHT_FLOOR = 0.5f
    /** Light intensity (0..1.5 in the snapshot) at and above which the ambience is at full daytime level. */
    const val DAY_LIGHT = 0.5f

    private const val FLOW_BASE = 0.45f
    private const val HUM_BASE = 0.08f
    private const val HOB_BASE = 0.40f
    private const val BUBBLES_BASE = 0.30f

    /**
     * Ambience for the current sim state.
     *
     * @param flowStrength snapshot pump strength (0..2; 0 when the powerheads are switched off)
     * @param powerheadShare share of the individual powerheads that are on (0..1; sandbox pump toggles)
     * @param powerOn the sim's pump master switch (`WaterState.flowOn`); false in a power outage or pump failure.
     *   The sim has no separate HOB switch, so the HOB trickle, its bubbles and the pump hum follow it.
     * @param lightIntensity snapshot light intensity (0 = lights off / night)
     * @param volume user volume 0..1
     */
    fun ambience(flowStrength: Float, powerheadShare: Float, powerOn: Boolean, lightIntensity: Float, volume: Float): AmbienceLevels {
        val v = volume.coerceIn(0f, 1f)
        if (v <= 0f) return AmbienceLevels.SILENT
        val day = nightFactor(lightIntensity) * v
        val drive = if (powerOn) (flowStrength.coerceIn(0f, 2f) * powerheadShare.coerceIn(0f, 1f)) else 0f
        // sqrt: loudness grows quickly from off, then flattens (double flow is ~1.4x, not 2x)
        val flow = if (drive < 0.02f) 0f else FLOW_BASE * sqrt(drive)
        val hob = if (powerOn) HOB_BASE else 0f
        val hum = if (powerOn) HUM_BASE else 0f
        val bubbles = if (powerOn) BUBBLES_BASE else 0f
        return AmbienceLevels(
            flow = (flow * day).coerceIn(0f, 1f),
            hum = (hum * day).coerceIn(0f, 1f),
            hob = (hob * day).coerceIn(0f, 1f),
            bubbles = (bubbles * day).coerceIn(0f, 1f),
        )
    }

    /** 1 by day; eases down to [NIGHT_FLOOR] as the lights dim to off. */
    fun nightFactor(lightIntensity: Float): Float {
        val k = (lightIntensity / DAY_LIGHT).coerceIn(0f, 1f)
        return NIGHT_FLOOR + (1f - NIGHT_FLOOR) * k
    }

    /** The one-shot for a sim event, if any. Deaths and predation chime; crustacean and cleaning moments click. */
    fun cueFor(e: SimEvent): Cue? = when (e) {
        is SimEvent.Fed -> Cue.FEED
        is SimEvent.Died, is SimEvent.Eaten -> Cue.ALERT
        is SimEvent.Molted, is SimEvent.Cleaned, is SimEvent.LegRock, is SimEvent.Jolt -> Cue.CLICK
        else -> null
    }

    /** How many pellet-drop ticks follow a feeding plop (more grams, more pellets; 2..5). */
    fun pelletCount(grams: Double): Int = (2 + grams * 2).toInt().coerceIn(2, 5)

    /** Ids of living animals that show loss of equilibrium (research 08 heat stage). */
    fun loeIds(stats: List<OrganismStats>): Set<String> =
        stats.filter { it.alive && Symptom.LOSS_OF_EQUILIBRIUM in it.symptoms }.mapTo(HashSet()) { it.id }
}

/** Per-cue rate limit: [tryPlay] answers whether the cue may play at [nowMs] and records it if so. */
class CueLimiter {
    private val last = HashMap<Cue, Long>()

    fun tryPlay(cue: Cue, nowMs: Long): Boolean {
        val prev = last[cue]
        if (prev != null && nowMs - prev < cue.minGapMs) return false
        last[cue] = nowMs
        return true
    }
}
