package com.reeflive.core.custom

import com.reeflive.core.sim.CoralHealth
import com.reeflive.core.sim.Simulation
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * My tank while the keeper is away (docs/ECONOMY.md §9): the whole time away is run, up to [MAX_DAYS], in chunks a
 * host can spread over frames ([step]) so a week away plays as a short fast-forward instead of a frozen screen. For the
 * first [GRACE_SEC] slow decline (hunger, creeping nitrate) cannot kill ([Simulation.healthGrace]); sudden disasters
 * still can. [summary] says what changed, in plain words, for the welcome-back card.
 */
class AwayCatchUp(private val sim: Simulation, seconds: Double) {
    val totalSec: Double = seconds.coerceIn(0.0, MAX_DAYS * DAY)
    var doneSec = 0.0
        private set
    val finished: Boolean get() = doneSec >= totalSec - 1e-6
    val progress: Double get() = if (totalSec <= 0) 1.0 else doneSec / totalSec
    private val before = AwayReport.capture(sim)

    /** Runs up to [maxSec] more of the time away (the hosts call it with a chunk per frame); true when finished. */
    fun step(maxSec: Double = CHUNK_SEC): Boolean {
        var left = min(maxSec, totalSec - doneSec)
        while (left > 1e-6) {
            // the grace ends at exactly GRACE_SEC: split the chunk there
            val inGrace = doneSec < GRACE_SEC
            val dt = if (inGrace) min(left, GRACE_SEC - doneSec) else left
            sim.healthGrace = inGrace
            sim.advanceAbstract(dt)
            doneSec += dt; left -= dt
        }
        sim.healthGrace = false
        if (finished) sim.stepWall(0.0)
        return finished
    }

    /** Runs all of it now (tests, and short gaps where a progress screen is not worth it). */
    fun runAll(): AwaySummary { while (!finished) step(); return summary() }

    fun summary(): AwaySummary = AwayReport.summarize(before, sim, doneSec)

    companion object {
        const val DAY = 86400.0
        const val MAX_DAYS = 30.0
        const val GRACE_SEC = 2 * DAY
        /** Sim seconds per frame-sized chunk (≈ 60-120 ms of work on a phone). */
        const val CHUNK_SEC = 3 * 3600.0
        /** Away at least this long and the welcome-back card shows. */
        const val REPORT_SEC = 4 * 3600.0
    }
}

/** What happened while away: the headline, plain-word lines, and who died of what. */
data class AwaySummary(
    val awaySec: Double,
    val headline: String,
    val lines: List<String>,
    val deaths: List<Pair<String, String>>,
    val hungry: List<String>,
) {
    val worthShowing: Boolean get() = awaySec >= AwayCatchUp.REPORT_SEC || deaths.isNotEmpty()
}

/** Before/after snapshots of the things a keeper would notice on coming home. */
object AwayReport {
    class Before(
        val alive: Map<String, String>,
        val no3: Double, val po4: Double, val sg: Double, val film: Double, val hair: Double, val cup: Float, val sockH: Float, val full: Float,
        /** Each coral colony's health on leaving ([CoralHealth.changes] words what happened to them). */
        val corals: List<CoralHealth.Mark> = emptyList(),
    )

    fun capture(sim: Simulation): Before {
        val w = sim.water
        val c = w.careState()
        return Before(
            sim.organisms.filter { it.alive }.associate { it.id to name(it.nickname, it.species.common) },
            w.s.no3, w.s.po4, w.s.sg, w.s.film, w.s.algaeHair, c.skimmerCup, c.sockAgeH, c.waterFull, CoralHealth.marks(sim.corals),
        )
    }

    private fun name(nick: String, common: String) = nick.substringBefore(" #").ifBlank { common }.ifBlank { "An animal" }

    fun summarize(b: Before, sim: Simulation, awaySec: Double): AwaySummary {
        val w = sim.water
        val c = w.careState()
        val lines = ArrayList<String>()
        val deaths = b.alive.mapNotNull { (id, n) ->
            val o = sim.organisms.firstOrNull { it.id == id }
            if (o != null && o.alive) null else n to cause(o?.deathCause)
        }
        val hungry = sim.organisms.filter { it.alive && it.isFish && it.hunger > 0.7 }.map { name(it.nickname, it.species.common) }
        if (hungry.isNotEmpty()) lines += "${list(hungry)} ${if (hungry.size == 1) "is" else "are"} very hungry!"
        val sick = sim.organisms.filter { it.alive && it.disease != null && it.diseaseStage > 0.15 }.map { name(it.nickname, it.species.common) }
        if (sick.isNotEmpty()) lines += "${list(sick)} ${if (sick.size == 1) "looks" else "look"} sick."
        if (c.skimmerOn && c.skimmerCup >= 0.95f && b.cup < 0.95f) lines += "The skimmer cup overflowed."
        else if (c.skimmerOn && c.skimmerCup >= 0.6f && b.cup < 0.6f) lines += "The skimmer cup is getting full."
        if (c.sockOn && c.sockAgeH >= 48f && b.sockH < 48f) lines += "The filter sock is clogged and starting to rot."
        if (c.waterFull < 0.97f && c.waterFull < b.full - 0.005f) lines += "Water evaporated: the tank needs a top-off (salinity ${"%.3f".fmt(w.s.sg)})."
        if (w.s.no3 > b.no3 + 3) lines += "Nitrate went up to ${w.s.no3.roundToInt()}."
        if (w.s.po4 > b.po4 + 0.05 && w.s.po4 > 0.1) lines += "Phosphate went up to ${"%.2f".fmt(w.s.po4)}."
        lines += CoralHealth.changes(b.corals, sim.corals)
        if (w.s.film > b.film + 0.08) lines += "Algae grew on the glass."
        if (w.s.algaeHair > b.hair + 0.05) lines += "Hair algae is spreading on the rocks."
        val days = awaySec / AwayCatchUp.DAY
        val away = when {
            days >= 1.5 -> "${days.roundToInt()} days"
            days >= 0.75 -> "a day"
            else -> "${(awaySec / 3600).roundToInt()} hours"
        }
        val headline = when {
            deaths.isNotEmpty() -> "You were away $away. Some animals did not make it."
            lines.isEmpty() -> "You were away $away. Everyone is doing fine!"
            else -> "You were away $away. Your tank needs some care."
        }
        return AwaySummary(awaySec, headline, lines, deaths, hungry)
    }

    /** A gentle cause from the sim's death cause. */
    fun cause(c: String?): String = when {
        c == null -> "unknown"
        c == "starvation" -> "hunger"
        c.startsWith("water") -> "bad water" + c.substringAfter("water", "").let { if (it.length > 2) " (${it.removePrefix(": ")})" else "" }
        c == "stress" -> "stress"
        c.contains("eaten") -> "eaten"
        else -> "sickness ($c)"
    }

    private fun list(names: List<String>): String = when (names.size) {
        1 -> names[0]
        2 -> "${names[0]} and ${names[1]}"
        else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
    }

    /** "%.3f".fmt(x) without String.format (core also compiles to JS). */
    private fun String.fmt(v: Double): String {
        val digits = removePrefix("%.").removeSuffix("f").toIntOrNull() ?: 2
        var p = 1.0
        repeat(digits) { p *= 10 }
        val r = kotlin.math.round(v * p) / p
        val s = r.toString()
        val dot = s.indexOf('.')
        return if (dot < 0) s + "." + "0".repeat(digits) else s.padEnd(dot + 1 + digits, '0').take(dot + 1 + digits)
    }
}
