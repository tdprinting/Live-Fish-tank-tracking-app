package com.reeflive.core.custom

import com.reeflive.core.sim.HealthModel
import com.reeflive.core.sim.Simulation
import com.reeflive.core.sim.TankDoctor
import kotlin.math.max

/** One phone notification for My tank, due at [atMs] (wall clock, epoch ms). [tag] replaces an older one of the same kind. */
data class PlannedAlert(val atMs: Long, val tag: String, val title: String, val body: String)

/**
 * My tank's "your tank needs you" notifications (thread "Tank care notifications"). When the app closes, the hosts ask
 * for the plan: when each thing will be due if nobody opens the tank, worked out from the tank as it is now (hunger
 * rises at [HealthModel.hungerRatePerMin], the skimmer cup at its average fill rate since it was last emptied, the
 * sock and the water level by the clock). Android schedules the plan locally; the web app hands it to the push sender
 * (push-server/). Opening the app again cancels whatever has not fired.
 *
 * Kid-friendly and never spammy: nothing in the first [MIN_DELAY_MIN] (they just saw the tank), nothing at night (moved
 * to [QUIET_TO_MIN]), alerts close together merged into one, at most [PER_DAY] a day and [MAX_TOTAL] in all.
 *
 * Hunger, water drift and fouling run on real time in My tank (see LifePace), so sim minutes are wall minutes here.
 */
object CareAlerts {
    const val HUNGRY = 0.65
    const val VERY_HUNGRY = 0.9
    const val MIN_DELAY_MIN = 45.0
    const val MERGE_MIN = 45.0
    const val PER_DAY = 3
    const val MAX_TOTAL = 6
    const val HORIZON_H = 72.0
    /** Quiet hours, local minutes of the day: 8:30 pm to 8:00 am. */
    const val QUIET_FROM_MIN = 20 * 60 + 30
    const val QUIET_TO_MIN = 8 * 60
    const val NEW_JOBS_MIN = 9 * 60
    const val JOBS_LEFT_MIN = 17 * 60 + 30

    /**
     * The plan for [sim] as of [nowMs] (when the tank last ran), in the keeper's time zone [utcOffsetMin].
     * [hasEconomy] adds the daily care list; [jobsLeftToday] is how many of today's jobs are not done yet.
     */
    fun plan(sim: Simulation, nowMs: Long, utcOffsetMin: Int, hasEconomy: Boolean, jobsLeftToday: Int): List<PlannedAlert> {
        val due = ArrayList<Due>()
        hunger(sim, due)
        equipment(sim, due)
        if (hasEconomy) jobs(nowMs, utcOffsetMin, jobsLeftToday, due)
        return schedule(due, nowMs, utcOffsetMin)
    }

    /** Something that will need the keeper [inMin] minutes from now; lower [rank] wins the title when merged. */
    internal class Due(val inMin: Double, val tag: String, val title: String, val body: String, val rank: Int)

    private fun hunger(sim: Simulation, out: MutableList<Due>) {
        val tempC = sim.water.s.tempC
        val soon = ArrayList<Pair<Double, String>>()
        val starving = ArrayList<Double>()
        for (o in sim.organisms) {
            if (!o.alive || !o.isFish) continue
            val rate = HealthModel.hungerRatePerMin(o, tempC)
            if (rate <= 0) continue
            soon += max(0.0, (HUNGRY - o.hunger) / rate) to o.nickname.ifBlank { o.species.common }
            starving += max(0.0, (VERY_HUNGRY - o.hunger) / rate)
        }
        if (soon.isEmpty()) return
        soon.sortBy { it.first }
        val first = soon[0].first
        // everyone who gets hungry within an hour of the first one is named in the same note
        val names = soon.filter { it.first <= first + 60 }.map { it.second }.distinct()
        val who = when {
            soon.size == 1 -> names[0]
            names.size == 1 -> "${names[0]} and friends"
            names.size == 2 -> "${names[0]} and ${names[1]}"
            else -> "${names[0]}, ${names[1]} and ${names.size - 2} more"
        }
        val verb = if (names.size == 1 && soon.size == 1) "is" else "are"
        out += Due(first, "hungry", "Feeding time! 🐟", "$who $verb hungry. Come drop in some food!", 0)
        val worst = starving.min()
        if (worst >= first + 4 * 60) {
            out += Due(worst, "hungry", "Your fish are very hungry 😟", "They have not eaten in a long time. Please feed them soon!", 0)
        }
    }

    private fun equipment(sim: Simulation, out: MutableList<Due>) {
        val w = sim.water
        val care = w.careState()
        if (care.skimmerOn && w.skimmerCupAgeH > 1 && w.skimmerCup > 0.01) {
            val perH = w.skimmerCup / w.skimmerCupAgeH
            val h = max(0.0, (TankDoctor.CUP_EMPTY_AT - w.skimmerCup) / perH)
            if (w.skimmerEmptyDays <= 0 || h < w.skimmerEmptyDays * 24 - w.skimmerCupAgeH) {
                out += Due(h * 60, "skimmer", "The skimmer cup is full 🟤", "Tip out the brown gunk so the skimmer can keep cleaning.", 2)
            }
        }
        if (care.sockOn) {
            val h = max(0.0, TankDoctor.SOCK_SWAP_H - w.sockAgeH)
            if (w.sockChangeDays <= 0 || h < w.sockChangeDays * 24 - w.sockAgeH) {
                out += Due(h * 60, "sock", "Time for a clean filter sock 🧦", "The old one is dirty. Swap in a fresh sock.", 2)
            }
        }
        if (!care.ato) {
            val perH = w.config.evapLitersPerDay / 24 / w.config.systemLiters
            if (perH > 0) {
                val h = max(0.0, (care.waterFull - TankDoctor.TOP_OFF_BELOW) / perH)
                out += Due(h * 60, "topoff", "The water is getting low 💧", "Top off the tank with fresh water.", 3)
            }
        }
        val urgent = TankDoctor.diagnose(w.s, care).filter { it.urgent && it.kind != TankDoctor.Kind.EMPTY_SKIMMER && it.kind != TankDoctor.Kind.SWAP_SOCK }
        if (urgent.isNotEmpty()) {
            out += Due(2 * 60.0, "water", "Your tank needs help 🩺", "${urgent[0].title}. Open Tank Health to fix it.", 1)
        }
    }

    private fun jobs(nowMs: Long, utcOffsetMin: Int, left: Int, out: MutableList<Due>) {
        val minOfDay = localMinute(nowMs, utcOffsetMin)
        if (left > 0 && minOfDay < JOBS_LEFT_MIN) {
            val n = if (left == 1) "1 care job" else "$left care jobs"
            out += Due((JOBS_LEFT_MIN - minOfDay).toDouble(), "jobs", "Sand dollars waiting 🪙", "You still have $n to do today.", 4)
        }
        out += Due((1440 - minOfDay + NEW_JOBS_MIN).toDouble(), "jobs", "New care jobs are ready 🪙", "Look after your tank today and earn sand dollars!", 4)
    }

    /** Local minute of the day (0 until 1440) at [ms]. */
    fun localMinute(ms: Long, utcOffsetMin: Int): Int = (ms / 60_000 + utcOffsetMin).mod(1440L).toInt()

    /** Minutes to add to a time [minOfDay] so it lands outside quiet hours. */
    internal fun pastQuiet(minOfDay: Int): Int = when {
        minOfDay >= QUIET_FROM_MIN -> 1440 - minOfDay + QUIET_TO_MIN
        minOfDay < QUIET_TO_MIN -> QUIET_TO_MIN - minOfDay
        else -> 0
    }

    internal fun schedule(due: List<Due>, nowMs: Long, utcOffsetMin: Int): List<PlannedAlert> {
        // when each one may actually ring: not right away, not at night
        val timed = due.filter { it.inMin <= HORIZON_H * 60 }.map { d ->
            var at = nowMs + (max(d.inMin, MIN_DELAY_MIN) * 60_000).toLong()
            at += pastQuiet(localMinute(at, utcOffsetMin)) * 60_000L
            at to d
        }.sortedWith(compareBy({ it.first }, { it.second.rank }))
        // close together: one note
        val groups = ArrayList<MutableList<Pair<Long, Due>>>()
        for (t in timed) {
            val g = groups.lastOrNull()
            if (g != null && t.first - g[0].first <= (MERGE_MIN * 60_000).toLong()) g += t else groups += mutableListOf(t)
        }
        val out = ArrayList<PlannedAlert>()
        val perDay = HashMap<Long, Int>()
        for (g in groups) {
            if (out.size >= MAX_TOTAL) break
            val at = g[0].first
            val day = (at / 60_000 + utcOffsetMin).floorDiv(1440L)
            val n = perDay[day] ?: 0
            if (n >= PER_DAY) continue
            perDay[day] = n + 1
            val items = g.map { it.second }.distinctBy { it.tag }
            out += if (items.size == 1) {
                PlannedAlert(at, items[0].tag, items[0].title, items[0].body)
            } else {
                val lead = items.minBy { it.rank }
                PlannedAlert(at, lead.tag, "Your tank needs you!", items.sortedBy { it.rank }.joinToString(" ") { it.body })
            }
        }
        return out
    }
}
