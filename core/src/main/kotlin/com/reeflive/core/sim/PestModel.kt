package com.reeflive.core.sim

import com.reeflive.core.geom.Vec3
import com.reeflive.core.geom.smoothstep
import kotlin.math.max
import kotlin.math.min

/** Aiptasia predators the keeper can add (research 03 D, 01 §4.4). */
enum class PestController { PEPPERMINT_SHRIMP, BERGHIA }

/** Controller populations living in the tank but not simulated as animals (they hide in the rock). */
class PestState {
    /** Peppermint shrimp added with [Command.AddPestControl] (any Lysmata boggessi/wurdemanni organism counts too). */
    var peppermint = 0
    /** Berghia nudibranch population (fractional: they breed and starve gradually). */
    var berghia = 0.0
    var peppermintAcc = 0.0
    var berghiaAcc = 0.0

    fun copyFrom(o: PestState) { peppermint = o.peppermint; berghia = o.berghia; peppermintAcc = o.peppermintAcc; berghiaAcc = o.berghiaAcc }
}

/**
 * Aiptasia (coverage gap C-4; research 01 §4.4, 03 C2 and D). Each polyp is a [Coral] of kind AIPTASIA. Mature polyps
 * bud lacerates by pedal laceration (est. one per ~2 weeks when well fed); a disturbed polyp, a scraped one or one hit
 * with a chemical often leaves fragments that grow into new polyps ("spreads when disturbed; chemical treatment can
 * cause fragmentation", S3). Peppermint shrimp eat small ones but may ignore established ones; Berghia eat only
 * aiptasia, breed while there is food, starve when it is gone and are eaten by shrimp and wrasses (S3).
 */
object AiptasiaModel {
    const val SPECIES_ID = "aiptasia_sp"
    /** The peppermint shrimp the keeper adds against aiptasia (research 42). */
    const val PEPPERMINT_ID = "lysmata_boggessi"
    const val CAP = 60
    /** Lacerates per mature polyp per day at full food (est.; literature on A. pallida culture ~ weekly when fed). */
    const val BUD_PER_DAY = 1.0 / 14
    const val MATURE = 0.6
    /** Polyps eaten per controller per day (est.). */
    const val PEPPERMINT_SMALL_PER_DAY = 0.5
    const val PEPPERMINT_LARGE_PER_DAY = 0.08
    const val BERGHIA_PER_DAY = 0.4
    const val BERGHIA_BREED_PER_DAY = 0.04
    const val BERGHIA_STARVE_PER_DAY = 1.0 / 7
    const val BERGHIA_PREDATION_PER_DAY = 0.05

    fun polyps(sim: Simulation) = sim.corals.filter { it.kind == CoralKind.AIPTASIA && it.tissue > 0.01 }

    /**
     * Peppermint shrimp eating by the numbers: controllers from an older save ([PestState.peppermint]) plus, while the
     * behaviour layer is off (fast time, away catch-up), the live peppermints, which otherwise hunt polyp by polyp
     * themselves ([com.reeflive.core.ai.PeppermintShrimpBrain]).
     */
    fun peppermintCount(sim: Simulation): Int = sim.pests.peppermint +
        (if (sim.behaviorEnabled && !sim.abstractMode) 0 else sim.organisms.count { it.alive && !it.molting && it.brainKind == BrainKind.PEPPERMINT_SHRIMP })

    fun step(sim: Simulation, dtMin: Double) {
        val days = dtMin / 1440
        val rng = sim.growthRng
        val w = sim.water.s
        val all = polyps(sim)
        // food: detritus and dissolved nutrients (they also host zooxanthellae)
        val food = 0.4 + 0.4 * smoothstep(0.05, 0.4, w.detritus) + 0.2 * smoothstep(1.0, 10.0, w.no3)
        val born = ArrayList<Coral>()
        for (p in all) {
            val vig = p.tissue * (1 - p.stress) * food
            p.size = min(1.0, p.size + 0.05 * vig * days)
            // pedal laceration
            if (p.size >= MATURE && rng.chance(BUD_PER_DAY * vig * days)) lacerate(sim, p)?.let { born += it }
            // disturbed (shadow, fish contact, tap): fragments now and then
            if (p.disturb > 0.5 && sim.simTimeSec - p.lastFragAt > 3600) {
                p.lastFragAt = sim.simTimeSec
                if (rng.chance(0.3)) lacerate(sim, p)?.let { born += it }
            }
        }
        add(sim, born)
        controllers(sim, days)
        // dead polyps disappear
        sim.corals.removeAll { it.kind == CoralKind.AIPTASIA && it.tissue <= 0.01 }
    }

    private fun controllers(sim: Simulation, days: Double) {
        val st = sim.pests
        val rng = sim.growthRng
        val pep = peppermintCount(sim)
        if (pep > 0) {
            st.peppermintAcc += pep * days
            while (st.peppermintAcc >= 1) {
                st.peppermintAcc -= 1
                val live = polyps(sim)
                if (live.isEmpty()) { st.peppermintAcc = 0.0; break }
                // a shrimp-day finds a small polyp at 0.5/day, an established one only at 0.08/day
                val small = live.filter { it.size < MATURE }
                val pSmall = PEPPERMINT_SMALL_PER_DAY * min(1.0, small.size / 3.0)
                val pLarge = PEPPERMINT_LARGE_PER_DAY
                when {
                    small.isNotEmpty() && rng.chance(pSmall) -> kill(small[rng.nextInt(small.size)])
                    rng.chance(pLarge) -> kill(live[rng.nextInt(live.size)])
                }
            }
        }
        if (st.berghia > 0) {
            val n = polyps(sim).size
            val predators = sim.organisms.any { it.alive && (it.brainKind.isShrimp || it.brainKind == BrainKind.WRASSE) } || pep > 0
            val badWater = sim.water.s.copper > 0.01 || sim.water.s.nh3Free > 0.02
            var rate = 0.0
            if (n > 0) rate += BERGHIA_BREED_PER_DAY * (1 - st.berghia / (2.0 * n + 2)).coerceAtLeast(-1.0) else rate -= BERGHIA_STARVE_PER_DAY
            if (predators) rate -= BERGHIA_PREDATION_PER_DAY
            if (badWater) rate -= 0.2
            st.berghia = max(0.0, st.berghia * (1 + rate * days))
            if (st.berghia < 0.3) st.berghia = 0.0
            st.berghiaAcc += st.berghia * BERGHIA_PER_DAY * days
            while (st.berghiaAcc >= 1) {
                st.berghiaAcc -= 1
                val live = polyps(sim)
                if (live.isEmpty()) { st.berghiaAcc = 0.0; break }
                kill(live[rng.nextInt(live.size)])
            }
        }
    }

    private fun kill(p: Coral) { p.tissue = 0.0 }

    /** A pedal lacerate: a small new polyp 1-4 cm from [p] on the rock (nothing on sand). */
    fun lacerate(sim: Simulation, p: Coral): Coral? {
        if (polyps(sim).size >= CAP) return null
        val rng = sim.growthRng
        val rock = sim.rock
        val off = Vec3(rng.signed(), rng.signed() * 0.5, rng.signed()).normalized() * rng.range(1.0, 4.0)
        val q = rock.sdf.project(p.pos + off, 0.2)
        if (!rock.inWater(q, 0.5) || q.y < rock.sandTopCm + 0.5 || q.distanceTo(p.pos) > 6.0 || kotlin.math.abs(rock.sdf.dist(q) - 0.2) > 0.5) return null
        val id = "${p.id.substringBefore('~')}~a${sim.nextFragId()}"
        return Coral(id, p.species, "Aiptasia", "", q, rock.sdf.gradient(q), rng.nextDouble() * 6.28).also { it.size = 0.2 }
    }

    private fun add(sim: Simulation, born: List<Coral>) {
        for (c in born) {
            sim.corals += c
            sim.emit(SimEvent.CoralSpawned(c.id.substringBefore('~'), c.id, sim.simTimeSec))
        }
    }

    /** Seed [count] aiptasia (sandbox add-pest): on the aiptasia anchors first, then as lacerates of those. */
    fun seed(sim: Simulation, count: Int) {
        repeat(count) {
            val used = sim.corals.filter { it.kind == CoralKind.AIPTASIA }.map { it.anchor }.toSet()
            val anchor = sim.rock.anchors.withPrefix("coral_aiptasia").firstOrNull { it.name !in used }
            if (anchor != null) {
                sim.addFromSeed(LivestockSeed(SPECIES_ID, "Aiptasia", placement = anchor.name, id = "aiptasia#${sim.nextFragId()}"))
            } else {
                val parent = polyps(sim).let { l -> if (l.isEmpty()) return else l[sim.growthRng.nextInt(l.size)] }
                lacerate(sim, parent)?.let { c -> c.size = 0.7; add(sim, listOf(c)) }
            }
        }
    }

    /**
     * Remove up to [count] polyps, biggest first. Scraping fragments half the time; an injected chemical kills the
     * polyp but fragments one in five (S3).
     */
    fun remove(sim: Simulation, count: Int, chemical: Boolean) {
        val targets = polyps(sim).sortedByDescending { it.size }.take(count)
        val born = ArrayList<Coral>()
        for (p in targets) {
            if (sim.growthRng.chance(if (chemical) 0.2 else 0.5)) lacerate(sim, p)?.let { born += it }
            kill(p)
        }
        sim.corals.removeAll { it.kind == CoralKind.AIPTASIA && it.tissue <= 0.01 }
        add(sim, born)
    }

    /** Moving the rock tears polyps: each leaves a fragment half the time. */
    fun rockMoved(sim: Simulation) {
        val born = ArrayList<Coral>()
        for (p in polyps(sim)) if (sim.growthRng.chance(0.5)) lacerate(sim, p)?.let { born += it }
        add(sim, born)
    }
}
