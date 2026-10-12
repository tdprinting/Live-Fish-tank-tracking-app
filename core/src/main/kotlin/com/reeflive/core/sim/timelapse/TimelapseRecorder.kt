package com.reeflive.core.sim.timelapse

import com.reeflive.core.sim.Mood
import com.reeflive.core.sim.Organism
import com.reeflive.core.sim.OrganismPose
import com.reeflive.core.sim.OrganismStats
import com.reeflive.core.sim.SimEvent
import com.reeflive.core.sim.Simulation
import com.reeflive.core.sim.StressModel
import com.reeflive.core.sim.WaterState

/**
 * One animal at one sample time. Everything comes from [OrganismStats] generically ([envStress] carries
 * whatever stress channels the health model exposes); [signs] holds the visible-sign floats from the pose
 * (desaturation, white spots, thinness, clamped fins, ...).
 */
data class OrganismSample(
    val id: String,
    val speciesId: String,
    val nickname: String,
    val common: String,
    val health: Float,
    val stress: Float,
    val hunger: Float,
    val alive: Boolean,
    val mood: Mood,
    val behavior: String,
    val intent: String,
    val symptoms: List<String>,
    val disease: String?,
    val diseaseStage: Float,
    val envStress: Map<String, Double>,
    val signs: Map<String, Float>,
    val cause: String?,
    val isCoral: Boolean = false,
)

/** Water and every animal at [hour] hours into the run. */
data class TimelapseSample(
    val hour: Double,
    val simTimeSec: Double,
    val water: WaterState,
    val lightIntensity: Float,
    val organisms: List<OrganismSample>,
)

/** A sim event with its time in hours from the start of the run. */
data class TimelapseEvent(val hour: Double, val event: SimEvent)

/** The full recording of a run. */
data class TimelapseRecording(
    val spec: TimelapseSpec,
    val samples: List<TimelapseSample>,
    val events: List<TimelapseEvent>,
    /** False when the run was cancelled; [samples] then hold what was recorded so far. */
    val completed: Boolean,
) {
    val speciesIds: List<String> get() = samples.firstOrNull()?.organisms?.map { it.speciesId }?.distinct().orEmpty()

    /** Sample at or just before [hour]. */
    fun sampleAt(hour: Double): TimelapseSample? =
        samples.lastOrNull { it.hour <= hour + 1e-9 } ?: samples.firstOrNull()

    /** One series per organism id of [selector] over time. */
    fun series(id: String, selector: (OrganismSample) -> Float): List<Pair<Double, Float>> =
        samples.mapNotNull { s -> s.organisms.firstOrNull { it.id == id }?.let { s.hour to selector(it) } }
}

/** Samples the simulation into [TimelapseSample]s. Reads the public snapshot and Simulation state only. */
class TimelapseRecorder(private val sim: Simulation, private val spec: TimelapseSpec) {
    private val samples = ArrayList<TimelapseSample>()
    private val events = ArrayList<TimelapseEvent>()
    private val startSec = sim.simTimeSec

    val sampleCount: Int get() = samples.size
    val lastSampleHour: Double get() = samples.lastOrNull()?.hour ?: -1.0
    fun hourNow(): Double = (sim.simTimeSec - startSec) / 3600.0

    /** Collect the events the last advance produced (the sim drains them into its latest snapshot). */
    fun collectEvents() {
        val h = hourNow()
        for (e in sim.latest.events) events += TimelapseEvent(((e.simTimeSec - startSec) / 3600.0).coerceAtLeast(0.0).coerceAtMost(h + 1e-6), e)
    }

    fun sample() {
        val snap = sim.latest
        val poses = snap.poses()
        val live = sim.organisms.associateBy { it.id }
        val orgs = snap.stats.map { it.toSample(poses[it.id], live[it.id]) } + sim.corals.map { c ->
            val health = (c.tissue * (1.0 - 0.5 * c.bleach)).toFloat()
            OrganismSample(
                id = c.id, speciesId = c.species.id, nickname = c.nickname, common = c.species.common,
                health = health, stress = c.stress.toFloat().coerceIn(0f, 1f), hunger = 0f, alive = !c.melting,
                mood = if (c.melting) Mood.DEAD else if (c.bleach > 0.3) Mood.SICK else Mood.CALM,
                behavior = "", intent = "", symptoms = if (c.bleach > 0.2) listOf("Bleaching") else emptyList(),
                disease = null, diseaseStage = 0f, envStress = emptyMap(),
                signs = mapOf("bleach" to c.bleach.toFloat(), "brown" to c.brown.toFloat(), "tissue" to c.tissue.toFloat(), "algae" to c.algaeCover.toFloat()),
                cause = null, isCoral = true,
            )
        }
        samples += TimelapseSample(hourNow(), sim.simTimeSec, snap.water.copy(), snap.lightIntensity, orgs)
    }

    /**
     * Tolerance-curve stress plus the research 06 per-channel levels (max of acute and chronic), keyed
     * like the health model so the report can name the cause ("sal" → "sg", "nh3" → "nh3Free").
     */
    private fun mergeChannels(env: Map<String, Double>, o: Organism): Map<String, Double> {
        val out = HashMap(env)
        for (c in StressModel.CHANNELS) {
            val key = when (c) { StressModel.Ch.SAL -> "sg"; StressModel.Ch.NH3 -> "nh3Free"; else -> c.key }
            val v = c.weight * o.stressState.level(c)
            if (v > 0.0) out.merge(key, v) { a, b -> maxOf(a, b) }
        }
        return out
    }

    fun finish(completed: Boolean) = TimelapseRecording(spec, samples.toList(), events.toList(), completed)

    private fun com.reeflive.core.sim.SimSnapshot.poses(): Map<String, OrganismPose> = organisms.associateBy { it.id }

    private fun OrganismStats.toSample(p: OrganismPose?, o: Organism?) = OrganismSample(
        id = id, speciesId = speciesId, nickname = nickname, common = common, health = health, stress = stress,
        hunger = hunger, alive = alive, mood = mood, behavior = behavior, intent = intent,
        symptoms = symptoms.map { it.label }, disease = disease?.name, diseaseStage = diseaseStage,
        envStress = o?.let { mergeChannels(envStress, it) } ?: envStress, cause = cause,
        signs = p?.let {
            mapOf("desat" to it.desat, "ich" to it.ich, "thin" to it.thin, "finClamp" to it.finClamp, "gillRate" to it.gillRate, "eyeCloud" to it.eyeCloud)
        }.orEmpty(),
    )
}
