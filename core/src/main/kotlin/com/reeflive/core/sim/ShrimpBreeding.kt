package com.reeflive.core.sim

import com.reeflive.core.geom.Rng
import kotlin.math.pow

/**
 * Shrimp pairs, molt-linked mating and egg carrying (research 12, docs/research/12_cleaner_shrimp_detail.md).
 * Molting stays in [Simulation.crustaceanMolts]; this component only watches for a finished molt, so molt state and
 * egg state are tracked independently (research 12 "track molt and egg carrying independently").
 *
 * - **Banded coral shrimp** (*Stenopus hispidus*): two opposite-sex conspecifics that have shared space long enough
 *   ([PAIR_FAMILIARITY]) become a recognised pair; a recognised mate is never a target of the conspecific aggression
 *   rows ([Organism.mateId], Johnson 1977 individual recognition), while a stranger still is. Mating follows the
 *   female's molt when her mate is near; she carries the clutch for ≈ 22.8 d (S, 2010 breeding study). No mate → no clutch.
 * - **Skunk cleaner shrimp** (*Lysmata amboinensis*): functional simultaneous hermaphrodite (Fiedler 1998). After a
 *   molt an adult spawns; with another live adult conspecific in the tank the clutch is fertile, alone it is infertile.
 *   Eggs are carried until the next molt (Lysmata general, analogy) and then hatch (fertile) or are dropped.
 *
 * Larvae never recruit in a display tank (research 12; 11 §9 "eggs or larvae do not imply successful recruitment").
 * Own [Rng] so the behaviour streams are untouched. CPU: one pass over the shrimps per chemistry tick.
 */
class ShrimpBreeding(private val sim: Simulation) {
    private val rng = Rng(sim.config.seed * 37L + 0x5A1B7L)

    /** One clutch on a shrimp's swimmerets. [hatchAt] is NaN for Lysmata (hatch at the next molt). */
    class Clutch(val fertile: Boolean, val laidAt: Double, val hatchAt: Double)

    private val clutches = HashMap<String, Clutch>()
    private val seenMolt = HashMap<String, Double>()
    var fertileClutches = 0; private set
    var infertileClutches = 0; private set
    var hatches = 0; private set
    var droppedClutches = 0; private set
    var matings = 0; private set

    fun clutchOf(o: Organism): Clutch? = clutches[o.id]
    fun carryingEggs(o: Organism): Boolean = clutches.containsKey(o.id)

    fun reset() { clutches.clear(); seenMolt.clear(); fertileClutches = 0; infertileClutches = 0; hatches = 0; droppedClutches = 0; matings = 0 }

    /** Banded shrimp sex: explicit [Organism.sexFemale], then the role, else a stable draw from the id. */
    fun isFemale(o: Organism): Boolean = o.sexFemale ?: when {
        o.role.equals("FEMALE", true) -> true
        o.role.equals("MALE", true) -> false
        else -> (o.id.hashCode() and 1) == 0
    }

    fun step(dtMin: Double) {
        val t = sim.simTimeSec
        val shrimps = sim.organisms.filter { it.alive && (it.brainKind == BrainKind.BANDED_SHRIMP || it.brainKind == BrainKind.CLEANER_SHRIMP) }
        if (shrimps.isEmpty() && clutches.isEmpty()) return
        clutches.keys.retainAll(shrimps.map { it.id }.toSet())
        pairUp(shrimps)
        for (o in shrimps) {
            // a molt that has just begun: Lysmata releases (or drops) the clutch it carried
            val molt = o.lastMoltAt
            val seen = seenMolt[o.id]
            if (seen == null) { seenMolt[o.id] = molt; continue }
            if (molt > seen) {
                seenMolt[o.id] = molt
                clutches[o.id]?.let { c -> if (c.hatchAt.isNaN()) { clutches.remove(o.id); if (c.fertile) hatches++ else droppedClutches++ } }
            }
            // after the molt is over (soft-shelled, emerging): mating / spawning
            if (!o.molting && molt > -1e8 && t - molt < (MOLT_HIDE_H + SPAWN_WINDOW_H) * 3600 && clutches[o.id] == null && t - molt >= MOLT_HIDE_H * 3600 * 0.5) {
                if (o.lengthCm < MIN_ADULT_CM || o.health < 0.5) continue
                when (o.brainKind) {
                    BrainKind.CLEANER_SHRIMP -> {
                        val partner = shrimps.any { it !== o && it.brainKind == BrainKind.CLEANER_SHRIMP && it.lengthCm >= MIN_ADULT_CM }
                        clutches[o.id] = Clutch(partner, t, Double.NaN)
                        if (partner) { fertileClutches++; matings++ } else infertileClutches++
                    }
                    else -> if (isFemale(o)) {
                        val mate = shrimps.firstOrNull { it.id == o.mateId && it.pos.distanceTo(o.pos) <= MATE_REACH_CM }
                        if (mate != null) {
                            matings++; fertileClutches++
                            clutches[o.id] = Clutch(true, t, t + rng.range(INCUBATION_DAYS_MIN, INCUBATION_DAYS_MAX) * 86400)
                        }
                    }
                }
            }
            // banded shrimp eggs hatch on their own clock
            clutches[o.id]?.let { c -> if (!c.hatchAt.isNaN() && t >= c.hatchAt) { clutches.remove(o.id); hatches++ } }
        }
    }

    /** Opposite-sex banded shrimp that have become familiar form a pair; the bond lasts while both live. */
    private fun pairUp(shrimps: List<Organism>) {
        val banded = shrimps.filter { it.brainKind == BrainKind.BANDED_SHRIMP }
        for (o in banded) if (o.mateId != null && banded.none { it.id == o.mateId }) o.mateId = null
        if (banded.size < 2) return
        for (a in banded) {
            if (a.mateId != null) continue
            for (b in banded) {
                if (b === a || b.mateId != null || isFemale(a) == isFemale(b)) continue
                val fam = a.relations[b.id]?.familiarity ?: 0.0
                if (fam >= PAIR_FAMILIARITY) { a.mateId = b.id; b.mateId = a.id; break }
            }
        }
    }

    companion object {
        /** Familiarity two banded shrimp need before they are a pair (DESIGN ESTIMATE; research 12 has no number). */
        @Ev(Evidence.DESIGN_ESTIMATE, "12") const val PAIR_FAMILIARITY = 0.3
        /** Stenopus egg incubation, mean 22.8 d at 26 °C (SOURCED, 2010 reproductive-cycle study); spread D. */
        @Ev(Evidence.SOURCED, "12 2010 study") const val INCUBATION_DAYS_MIN = 20.5
        const val INCUBATION_DAYS_MAX = 25.1
        /** Stenopus adult intermolt at 26 °C (SOURCED, 25–26 d). */
        @Ev(Evidence.SOURCED, "12 2010 study") const val STENOPUS_INTERMOLT_26C = 25.5
        /** Temperature scaling of that interval (DESIGN ESTIMATE). */
        @Ev(Evidence.DESIGN_ESTIMATE, "12") const val INTERMOLT_Q10 = 2.0
        const val MIN_ADULT_CM = 3.5
        const val MATE_REACH_CM = 30.0
        const val MOLT_HIDE_H = 36.0
        const val SPAWN_WINDOW_H = 24.0

        /** Stenopus intermolt (days) at [tempC], clamped to a plausible 15–45 d. */
        fun stenopusIntermoltDays(tempC: Double): Double =
            (STENOPUS_INTERMOLT_26C * INTERMOLT_Q10.pow((26.0 - tempC) / 10.0)).coerceIn(15.0, 45.0)
    }
}
