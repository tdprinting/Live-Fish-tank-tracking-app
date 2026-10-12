package com.reeflive.core.sim.timelapse

import com.reeflive.core.sim.HealthModel
import com.reeflive.core.sim.Mood
import com.reeflive.core.sim.SimEvent
import com.reeflive.core.sim.WaterState

enum class Verdict(val label: String) {
    COPED("Coped"), STRESSED("Stressed"), SICK("Got sick"), DIED("Died");

    val severity: Int get() = ordinal
}

/** Thresholds that turn a stress/health series into a verdict. A condition must hold for [sustain] samples. */
data class ReportThresholds(
    val stressOn: Float = 0.35f,
    val stressOff: Float = 0.25f,
    val sickHealth: Float = 0.6f,
    val sustain: Int = 2,
)

/** The likely cause of a problem: the water channel furthest out of range at onset. */
data class Cause(
    /** Base channel key: "sg", "temp", "ph", "nh3Free", "no2", "no3", "do", ... */
    val channel: String,
    val label: String,
    /** The model's stress contribution from that channel (0..1), or 0 when inferred from water drift. */
    val contribution: Double,
    /** Water value of the channel at onset, when WaterState exposes it. */
    val value: Double?,
    /** Other channels at onset, strongest first. */
    val others: List<Pair<String, Double>> = emptyList(),
)

data class OrganismReport(
    val id: String,
    val nickname: String,
    val speciesId: String,
    val common: String,
    val isCoral: Boolean,
    val verdict: Verdict,
    val peakStress: Float,
    val minHealth: Float,
    val finalHealth: Float,
    val stressedAtHour: Double?,
    val sickAtHour: Double?,
    val diedAtHour: Double?,
    val cause: Cause?,
    /** The sim's own death cause text ("water: salinity"), when it died. */
    val deathCause: String?,
    /** Was a problem seen and then clear (and still alive) by the end. */
    val recovered: Boolean,
    val recoveredAtHour: Double?,
) {
    val coped: Boolean get() = verdict == Verdict.COPED
}

/** All individuals of one species; [verdict] is the worst of them. */
data class SpeciesReport(
    val speciesId: String,
    val common: String,
    val isCoral: Boolean,
    val individuals: List<OrganismReport>,
) {
    val verdict: Verdict get() = individuals.maxByOrNull { it.verdict.severity }?.verdict ?: Verdict.COPED
    val firstOnsetHour: Double? get() = individuals.mapNotNull { listOfNotNull(it.stressedAtHour, it.sickAtHour, it.diedAtHour).minOrNull() }.minOrNull()
    val cause: Cause? get() = individuals.filter { it.cause != null }.minByOrNull { listOfNotNull(it.stressedAtHour, it.sickAtHour, it.diedAtHour).minOrNull() ?: Double.MAX_VALUE }?.cause
    val diedCount: Int get() = individuals.count { it.verdict == Verdict.DIED }
    val recoveredAll: Boolean get() = individuals.filter { !it.coped }.let { bad -> bad.isNotEmpty() && bad.all { it.recovered } }
}

data class TimelapseReport(val species: List<SpeciesReport>, val thresholds: ReportThresholds = ReportThresholds()) {
    val allOk: Boolean get() = species.all { it.verdict == Verdict.COPED }
    val organisms: List<OrganismReport> get() = species.flatMap { it.individuals }

    fun forSpecies(id: String): SpeciesReport? = species.firstOrNull { it.speciesId == id }

    /** One-line summary, e.g. "2 species died, 3 stressed, 9 coped". */
    fun headline(): String {
        if (species.isEmpty()) return "Nothing to report"
        val by = species.groupingBy { it.verdict }.eachCount()
        val parts = Verdict.values().reversed().mapNotNull { v -> by[v]?.let { "$it ${v.label.lowercase()}" } }
        return parts.joinToString(", ")
    }

    companion object {
        fun build(rec: TimelapseRecording, th: ReportThresholds = ReportThresholds()): TimelapseReport {
            val samples = rec.samples
            if (samples.isEmpty()) return TimelapseReport(emptyList(), th)
            val diedEvents = rec.events.map { it.event }.filterIsInstance<SimEvent.Died>().associateBy { it.id }
            val ids = samples.flatMap { s -> s.organisms.map { it.id } }.distinct()
            val reports = ids.map { id -> organismReport(id, rec, th, diedEvents[id]) }
            val grouped = reports.groupBy { it.speciesId }.map { (sid, list) ->
                SpeciesReport(sid, list.first().common, list.first().isCoral, list)
            }.sortedWith(compareByDescending<SpeciesReport> { it.verdict.severity }.thenBy { it.firstOnsetHour ?: Double.MAX_VALUE })
            return TimelapseReport(grouped, th)
        }

        private fun organismReport(id: String, rec: TimelapseRecording, th: ReportThresholds, died: SimEvent.Died?): OrganismReport {
            val rows = rec.samples.mapNotNull { s -> s.organisms.firstOrNull { it.id == id }?.let { s to it } }
            val first = rows.first().second
            fun isStressed(o: OrganismSample) = o.stress >= th.stressOn || o.mood == Mood.STRESSED
            fun isSick(o: OrganismSample) = o.disease != null && o.diseaseStage > 0.15f || o.health < th.sickHealth || o.mood == Mood.SICK
            fun isDead(o: OrganismSample) = !o.alive

            fun onset(pred: (OrganismSample) -> Boolean, needed: Int): Int? {
                var run = 0
                for ((i, r) in rows.withIndex()) {
                    run = if (pred(r.second)) run + 1 else 0
                    if (run >= needed) return i - needed + 1
                }
                return null
            }
            val iStress = onset(::isStressed, th.sustain)
            val iSick = onset(::isSick, th.sustain)
            val iDead = onset(::isDead, 1)
            // A sick/dying animal counts as stressed too, at the earliest of the three.
            val problemIdx = listOfNotNull(iStress, iSick, iDead).minOrNull()
            val verdict = when {
                iDead != null -> Verdict.DIED
                iSick != null -> Verdict.SICK
                iStress != null -> Verdict.STRESSED
                else -> Verdict.COPED
            }
            val hour = { i: Int? -> i?.let { rows[it].first.hour } }
            val diedHour = hour(iDead) ?: died?.let { (it.simTimeSec - rec.samples.first().simTimeSec) / 3600.0 }

            var cause: Cause? = null
            if (problemIdx != null) {
                // Look at the onset sample and the one before it: the first sample over the line may already be
                // past the peak channel for a fast kill, and stress channels fade once the animal is dead.
                val at = rows[problemIdx]
                val base = rows.first().first.water
                cause = causeAt(at.second, at.first.water, base)
                    ?: rows.getOrNull(problemIdx - 1)?.let { causeAt(it.second, it.first.water, base) }
                if (cause == null || (iDead != null && problemIdx == iDead)) {
                    rows.take(iDead ?: rows.size).lastOrNull { causeAt(it.second, it.first.water, base) != null }
                        ?.let { cause = causeAt(it.second, it.first.water, base) }
                }
                if (cause == null) cause = driftCause(at.first.water, base)
            }

            var recovered = false
            var recoveredAt: Double? = null
            if (problemIdx != null && iDead == null && rows.size > problemIdx) {
                val tail = rows.takeLast(th.sustain).map { it.second }
                val clear = tail.all { it.stress < th.stressOff && !isSick(it) }
                val lastBad = rows.indexOfLast { it.second.stress >= th.stressOff || isSick(it.second) }
                if (clear && lastBad < rows.lastIndex) {
                    recovered = true
                    recoveredAt = rows[lastBad.coerceAtLeast(problemIdx) + 1].first.hour
                }
            }
            return OrganismReport(
                id = id, nickname = first.nickname, speciesId = first.speciesId, common = first.common, isCoral = first.isCoral,
                verdict = verdict,
                peakStress = rows.maxOf { it.second.stress },
                minHealth = rows.minOf { it.second.health },
                finalHealth = rows.last().second.health,
                stressedAtHour = hour(iStress), sickAtHour = hour(iSick), diedAtHour = diedHour,
                cause = cause, deathCause = died?.cause ?: rows.lastOrNull { !it.second.alive }?.second?.cause,
                recovered = recovered, recoveredAtHour = recoveredAt,
            )
        }

        /** Collapse a swing/shock key onto the channel it measures. */
        fun baseChannel(key: String): String = when (key) {
            "shockTemp" -> "temp"; "shockSg" -> "sg"; "shockPh" -> "ph"; else -> key
        }

        private fun causeAt(o: OrganismSample, w: WaterState, base: WaterState): Cause? {
            val merged = HashMap<String, Double>()
            for ((k, v) in o.envStress) merged.merge(baseChannel(k), v) { a, b -> maxOf(a, b) }
            val ranked = merged.entries.filter { it.value > 0.02 }.sortedByDescending { it.value }
            val top = ranked.firstOrNull() ?: return null
            return Cause(
                channel = top.key, label = HealthModel.label(top.key), contribution = top.value,
                value = w.get(waterId(top.key)), others = ranked.drop(1).take(3).map { it.key to it.value },
            )
        }

        /** Fallback when the model exposes no per-channel stress: the water channel that moved furthest from the start. */
        private fun driftCause(w: WaterState, base: WaterState): Cause? {
            val ranked = DRIFT_SCALE.mapNotNull { (key, scale) ->
                val a = w.get(waterId(key)) ?: return@mapNotNull null
                val b = base.get(waterId(key)) ?: return@mapNotNull null
                key to kotlin.math.abs(a - b) / scale
            }.filter { it.second > 0.25 }.sortedByDescending { it.second }
            val top = ranked.firstOrNull() ?: return null
            return Cause(top.first, HealthModel.label(top.first), 0.0, w.get(waterId(top.first)), ranked.drop(1).take(3))
        }

        private fun waterId(channel: String) = when (channel) { "nh3Free" -> "nh3free"; else -> channel }

        /** Change in a channel that counts as "one unit out of range" for the drift fallback. */
        private val DRIFT_SCALE = mapOf(
            "temp" to 2.0, "sg" to 0.003, "ph" to 0.3, "alk" to 1.5, "ca" to 40.0, "mg" to 100.0,
            "no3" to 10.0, "no2" to 0.1, "nh3Free" to 0.02, "do" to 1.5, "po4" to 0.1, "copper" to 0.05,
        )
    }
}
