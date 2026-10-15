package com.reeflive.core.live

import com.reeflive.core.sim.Command
import com.reeflive.core.sim.FoodKind
import com.reeflive.core.sim.Mood
import com.reeflive.core.sim.SimEvent
import com.reeflive.core.sim.SimSnapshot

/** Pure glue between app data (repositories) and the simulation (ARCHITECTURE §5.2 / §9, PLAN C3). */

/** Fast-forward of the reality sim after the Live tab was away. */
object FastForward {
    const val CAP_SIM_SEC = 24 * 3600.0
    /** Gaps shorter than this are not worth a catch-up pass. */
    const val MIN_SIM_SEC = 5.0

    /** Sim seconds to skip for [elapsedWallMs] at [speed] (reality = 1), capped at 24 h of sim. */
    fun simSeconds(elapsedWallMs: Long, speed: Double = 1.0, capSec: Double = CAP_SIM_SEC): Double =
        (elapsedWallMs.coerceAtLeast(0L) / 1000.0 * speed.coerceAtLeast(0.0)).coerceAtMost(capSec)
}

/** Reality pick-up: latest logged values / alive livestock / active light schedule -> sim inputs. */
object RealityInit {
    /** Logged parameter id -> sim parameter id accepted by [Command.SetParam]. */
    private val PARAM_MAP = mapOf(
        "temp" to "temp", "sal" to "sg", "alk" to "alk", "ca" to "ca", "mg" to "mg", "ph" to "ph",
        "no3" to "no3", "po4" to "po4", "nh3" to "nh3", "no2" to "no2", "k" to "k", "sr" to "sr", "i" to "i", "do" to "do",
    )

    /** Logged value (base unit; temperature is stored in °F) -> (sim param id, sim value), or null if not simulated. */
    fun simValue(paramId: String, baseValue: Double): Pair<String, Double>? {
        val sim = PARAM_MAP[paramId] ?: return null
        return sim to if (paramId == "temp") (baseValue - 32.0) * 5.0 / 9.0 else baseValue
    }

    /** SetParam commands for values that differ from [previous] (reality pick-up of a newly logged measurement). */
    fun changedCommands(previous: Map<String, Double>, latest: Map<String, Double>): List<Command> =
        latest.mapNotNull { (id, v) ->
            if (previous[id] == v) null else simValue(id, v)?.let { (sim, value) -> Command.SetParam(sim, value) }
        }
}

/** One step of a scenario: [atSimSec] sim seconds after the scenario started. */
data class ScenarioStep(val atSimSec: Double, val command: Command)

/** Sandbox scenario presets, each a Command sequence (some with delayed steps). */
enum class Scenario(val label: String) {
    HEAT_WAVE("Heat wave"),
    NITRATE_SPIKE("Nitrate spike"),
    SALINITY_SWING("Salinity swing"),
    POWER_OUTAGE("Power outage"),
    ALGAE_BLOOM("Algae bloom"),
    OVERFEEDING("Overfeeding"),
    ROCKSLIDE("Rockslide");

    fun steps(): List<ScenarioStep> = Scenarios.steps(this)
}

object Scenarios {
    private const val HOUR = 3600.0

    fun steps(s: Scenario): List<ScenarioStep> = when (s) {
        Scenario.HEAT_WAVE -> listOf(
            ScenarioStep(0.0, Command.SetParam("roomtemp", 35.0)),
            ScenarioStep(0.0, Command.Heater(working = true, setC = 31.0, stuckOn = true)),
        )
        Scenario.NITRATE_SPIKE -> listOf(
            ScenarioStep(0.0, Command.Dose("no3", 60.0)),
            ScenarioStep(0.0, Command.Dose("po4", 0.3)),
        )
        Scenario.SALINITY_SWING -> listOf(
            ScenarioStep(0.0, Command.SetParam("sg", 1.033)),
            ScenarioStep(6 * HOUR, Command.SetParam("sg", 1.018)),
        )
        Scenario.POWER_OUTAGE -> listOf(
            ScenarioStep(0.0, Command.Powerheads(false)),
            ScenarioStep(0.0, Command.Heater(working = false)),
            ScenarioStep(0.0, Command.Lights(on = false)),
            ScenarioStep(4 * HOUR, Command.Powerheads(true)),
            ScenarioStep(4 * HOUR, Command.Heater(working = true)),
            ScenarioStep(4 * HOUR, Command.Lights(on = null)),
        )
        Scenario.ALGAE_BLOOM -> listOf(
            ScenarioStep(0.0, Command.Dose("po4", 0.4)),
            ScenarioStep(0.0, Command.Dose("no3", 20.0)),
            ScenarioStep(0.0, Command.SetParam("algae", 0.8)),
        )
        Scenario.OVERFEEDING -> List(6) { i ->
            ScenarioStep(i * 300.0, Command.Feed(if (i % 2 == 0) FoodKind.FLAKE else FoodKind.PELLET, 1.5))
        }
        // a goby's worth of digging all at once under one side of a rock; it shifts if "Rocks can shift" is on
        Scenario.ROCKSLIDE -> listOf(ScenarioStep(0.0, Command.DynamicRocks(true)), ScenarioStep(0.0, Command.UndermineRock()))
    }
}

/** Delayed scenario steps, released as sim time passes. Not thread safe: use from the sim thread. */
class ScenarioQueue {
    private val pending = ArrayList<Pair<Double, Command>>()
    val isEmpty get() = pending.isEmpty()

    fun schedule(nowSimSec: Double, steps: List<ScenarioStep>) { steps.forEach { pending += (nowSimSec + it.atSimSec) to it.command } }
    fun clear() = pending.clear()

    /** Commands whose time has come, in schedule order. */
    fun due(nowSimSec: Double): List<Command> {
        if (pending.isEmpty()) return emptyList()
        val out = pending.filter { it.first <= nowSimSec }.map { it.second }
        if (out.isNotEmpty()) pending.removeAll { it.first <= nowSimSec }
        return out
    }
}

/** Dashboard peek summary (fish count, any sick fish, last event). */
data class LivePeek(
    val fishAlive: Int = 0,
    val anySick: Boolean = false,
    val sickNames: List<String> = emptyList(),
    val lastEvent: String? = null,
) {
    companion object {
        fun from(s: SimSnapshot, lastEvent: String? = null): LivePeek {
            val sick = s.stats.filter { it.alive && (it.disease != null || it.mood == Mood.SICK) }
            return LivePeek(s.organisms.count { it.alive }, sick.isNotEmpty(), sick.map { it.nickname }, lastEvent)
        }

        fun describe(e: SimEvent): String? = when (e) {
            is SimEvent.Died -> "${e.id} died (${e.cause})"
            is SimEvent.DiseaseOnset -> "${e.id} shows signs of ${e.disease.name.lowercase()}"
            is SimEvent.Recovered -> "${e.id} recovered from ${e.disease.name.lowercase()}"
            is SimEvent.Molted -> "${e.id} molted"
            is SimEvent.CoralBleaching -> "${e.id} is bleaching"
            is SimEvent.AlgaeBloom -> "Algae bloom"
            is SimEvent.Gasping -> "${e.id} is gasping"
            is SimEvent.Eaten -> "${e.preyId} was eaten by ${e.predatorId}"
            is SimEvent.Fed -> "Fed ${e.grams} g"
            is SimEvent.NearMiss -> "${e.preyId} had a close call with ${e.predatorId}"
            else -> null   // Cleaned, Chase, Pose, Jolt, TerritoryReset, Quiver, LegRock: too frequent for the peek
        }

        /** The latest describable event of a batch. */
        fun lastDescribed(events: List<SimEvent>): String? = events.asReversed().firstNotNullOfOrNull { describe(it) }
    }
}
