package com.reeflive.core.sim

import com.reeflive.core.geom.Vec3
import com.reeflive.core.geom.smoothstep
import kotlin.math.max
import kotlin.math.min

/** Aiptasia predators the keeper can add (research 03 D, 01 §4.4). */
enum class PestController { PEPPERMINT_SHRIMP, BERGHIA }

/**
 * What the keeper kills an aiptasia with (docs/research/aiptasia_control.md). [openKill] / [closedKill]: chance the
 * dose kills a polyp that is open / pulled into its hole (a closed polyp takes in little of it); [fragment]: chance the
 * dying polyp leaves a pedal lacerate behind; [dieSec]: sim seconds from the dose until it is gone; [whiten]: how pale
 * it goes while it dies (Aiptasia-X and the calcium hydroxide pastes coat it white; the laser just shrivels it).
 * A miss still makes the polyp snap shut and sulk in its hole for a while.
 */
enum class AiptasiaTreatment(
    val label: String, val openKill: Double, val closedKill: Double, val fragment: Double, val dieSec: Double, val whiten: Double,
) {
    /** Red Sea Aiptasia-X: injected at the oral disc, "dies within minutes" encapsulated in globules (retailer, §2). */
    AIPTASIA_X("Aiptasia-X", 0.92, 0.45, 0.1, 300.0, 0.85),
    /** Blue Life Joe's Juice: fed over the mouth; behaves like a calcium hydroxide slurry (§3). */
    JOES_JUICE("Joe's Juice", 0.85, 0.4, 0.15, 900.0, 0.9),
    /** Home-made kalkwasser paste in a blunt syringe, into the mouth, left about an hour (§4); base hits spread them. */
    KALK_PASTE("Kalk paste", 0.8, 0.3, 0.2, 1800.0, 1.0),
    /** Aiptasia laser: closes in seconds, often needs another pass, tissue left can regrow (§5). */
    LASER("Laser", 0.75, 0.6, 0.08, 20.0, 0.25),
    /** Scraping it off the rock: the classic way to make ten out of one (§1, "when scraped or disturbed"). */
    SCRAPE("Scrape", 0.9, 0.9, 0.5, 30.0, 0.0),
}

/** Controller populations living in the tank but not simulated as animals (they hide in the rock). */
class PestState {
    /** Peppermint shrimp added with [Command.AddPestControl] (any Lysmata boggessi/wurdemanni organism counts too). */
    var peppermint = 0
    /** Berghia nudibranch population (fractional: they breed and starve gradually). */
    var berghia = 0.0
    var peppermintAcc = 0.0
    /** Established polyps owed to the peppermints (fractional, so the rare kill comes at its expected rate). */
    var peppermintLargeAcc = 0.0
    var berghiaAcc = 0.0
    /** The polyp the Berghia are working on now (they graze one down before moving to the next). */
    var berghiaPrey: String? = null

    fun copyFrom(o: PestState) {
        peppermint = o.peppermint; berghia = o.berghia; peppermintAcc = o.peppermintAcc; peppermintLargeAcc = o.peppermintLargeAcc; berghiaAcc = o.berghiaAcc; berghiaPrey = o.berghiaPrey
    }

    /** For My tank saves (CustomTankSave.pests). */
    fun save(): Map<String, Double> = buildMap {
        if (peppermint > 0) put("peppermint", peppermint.toDouble())
        if (berghia > 0) put("berghia", berghia)
    }

    fun restore(m: Map<String, Double>) {
        peppermint = (m["peppermint"] ?: 0.0).toInt().coerceAtLeast(0)
        berghia = (m["berghia"] ?: 0.0).coerceAtLeast(0.0)
    }
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
    /** Polyps eaten per controller per day (est.; research 42 §1.5: established polyps are mostly ignored, even by a hungry shrimp). */
    const val PEPPERMINT_SMALL_PER_DAY = 0.5
    const val PEPPERMINT_LARGE_PER_DAY = 0.04
    const val BERGHIA_PER_DAY = 0.4
    const val BERGHIA_BREED_PER_DAY = 0.04
    const val BERGHIA_STARVE_PER_DAY = 1.0 / 7
    const val BERGHIA_PREDATION_PER_DAY = 0.05
    const val NIGHT_FEED = 1.7
    const val DAY_FEED = 0.45
    /** Chance a store frag or piece of live rock carries an aiptasia (est.; dry rock carries none). */
    const val HITCHHIKE_FRAG = 0.06
    const val HITCHHIKE_LIVE_ROCK = 0.25

    fun polyps(sim: Simulation) = sim.corals.filter { it.kind == CoralKind.AIPTASIA && it.tissue > 0.01 }

    /**
     * Peppermint shrimp eating by the numbers: controllers from an older save ([PestState.peppermint]) plus, while the
     * behaviour layer is off (fast time, away catch-up), the live peppermints, which otherwise hunt polyp by polyp
     * themselves ([com.reeflive.core.ai.PeppermintShrimpBrain]).
     */
    fun peppermintCount(sim: Simulation): Int = sim.pests.peppermint +
        (if (sim.behaviorEnabled && !sim.abstractMode) 0 else sim.organisms.count { it.alive && !it.molting && it.brainKind == BrainKind.PEPPERMINT_SHRIMP })

    /** [lifeMin]: the step on the game clock ([Simulation.lifePace]) for growth and spread; a zapped polyp dies on [dtMin]. */
    fun step(sim: Simulation, dtMin: Double, night: Boolean = false, lifeMin: Double = dtMin) {
        val days = lifeMin / 1440
        dying(sim, dtMin * 60)
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
            // disturbed (shadow, fish contact, tap): fragments now and then (not one already dying or being grazed down)
            if (p.disturb > 0.5 && p.treatment == null && p.id != sim.pests.berghiaPrey && sim.simTimeSec - p.lastFragAt > 3600) {
                p.lastFragAt = sim.simTimeSec
                if (rng.chance(0.3)) lacerate(sim, p)?.let { born += it }
            }
        }
        add(sim, born)
        controllers(sim, days, night)
        // dead polyps disappear
        sim.corals.removeAll { it.kind == CoralKind.AIPTASIA && it.tissue <= 0.01 }
    }

    private fun controllers(sim: Simulation, days: Double, night: Boolean) {
        val st = sim.pests
        val rng = sim.growthRng
        val pep = peppermintCount(sim)
        if (pep > 0) {
            st.peppermintAcc += pep * days
            while (st.peppermintAcc >= 1) {
                st.peppermintAcc -= 1
                val live = polyps(sim)
                if (live.isEmpty()) { st.peppermintAcc = 0.0; break }
                // a shrimp-day finds a small polyp at 0.5/day, an established one only at 0.04/day
                val small = live.filter { it.size < MATURE }
                val pSmall = PEPPERMINT_SMALL_PER_DAY * min(1.0, small.size / 3.0)
                val pLarge = PEPPERMINT_LARGE_PER_DAY
                when {
                    small.isNotEmpty() && rng.chance(pSmall) -> kill(small[rng.nextInt(small.size)])
                    // an established one now and then, at its expected rate (a dice roll here ran in streaks)
                    else -> { st.peppermintLargeAcc += pLarge; if (st.peppermintLargeAcc >= 1) { st.peppermintLargeAcc -= 1; kill(live[rng.nextInt(live.size)]) } }
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
            // they graze mostly after lights-out and hide in the rock by day (supplier care notes; est. weights that
            // average about 1 over a 10 h night); one polyp at a time, a small one goes faster
            val w = if (night) NIGHT_FEED else DAY_FEED
            var eat = st.berghia * BERGHIA_PER_DAY * days * w
            while (eat > 1e-9) {
                val prey = prey(sim) ?: break
                val need = prey.tissue * max(0.3, prey.size)
                prey.disturb = max(prey.disturb, 0.6)   // a polyp being eaten stays pulled in
                if (eat >= need) { eat -= need; kill(prey) } else { prey.tissue -= eat / max(0.3, prey.size); eat = 0.0 }
            }
        } else st.berghiaPrey = null
    }

    /** The Berghia's current polyp, or the next one: the nearest to the last (they crawl on), else any. */
    private fun prey(sim: Simulation): Coral? {
        val st = sim.pests
        val live = polyps(sim)
        if (live.isEmpty()) { st.berghiaPrey = null; return null }
        live.firstOrNull { it.id == st.berghiaPrey && it.treatment == null }?.let { return it }
        val from = sim.corals.firstOrNull { it.id == st.berghiaPrey }?.pos
        val pick = live.filter { it.treatment == null }.ifEmpty { live }.let { l ->
            if (from != null) l.minByOrNull { it.pos.distanceSqTo(from) }!! else l[sim.growthRng.nextInt(l.size)]
        }
        st.berghiaPrey = pick.id
        return pick
    }

    private fun kill(p: Coral) { p.tissue = 0.0 }

    /** Treated polyps shrink away over their treatment's [AiptasiaTreatment.dieSec]. */
    private fun dying(sim: Simulation, dtSec: Double) {
        for (p in sim.corals) {
            val t = p.treatment ?: continue
            if (p.kind != CoralKind.AIPTASIA) continue
            p.disturb = 1.0
            p.bleach = max(p.bleach, t.whiten * (1 - p.treatLeftSec / t.dieSec).coerceIn(0.0, 1.0))
            p.treatLeftSec -= dtSec
            p.tissue = if (p.treatLeftSec <= 0) 0.0 else min(p.tissue, (p.treatLeftSec / t.dieSec).coerceIn(0.05, 1.0))
        }
    }

    /**
     * Nearest live polyp to [pos] within [radiusCm] no bigger than [maxSize] (shrimp and other predators; the
     * peppermint shrimp thread's brain), or null. Polyps already dying from a treatment are skipped.
     */
    fun preyNear(sim: Simulation, pos: Vec3, radiusCm: Double, maxSize: Double = 1.0): Coral? {
        var best: Coral? = null
        var bd = radiusCm * radiusCm
        for (c in sim.corals) {
            if (c.kind != CoralKind.AIPTASIA || c.tissue <= 0.01 || c.size > maxSize || c.treatment != null) continue
            val d = c.pos.distanceSqTo(pos)
            if (d < bd) { bd = d; best = c }
        }
        return best
    }

    /** A predator's bite: takes [amount] of the polyp's tissue (scaled by its size) and makes it pull in. True when gone. */
    fun bite(sim: Simulation, polyp: Coral, amount: Double): Boolean {
        polyp.disturb = max(polyp.disturb, 0.8)
        polyp.tissue -= amount / max(0.3, polyp.size)
        if (polyp.tissue > 0.01) return false
        kill(polyp)
        return true
    }

    /** What a treatment did, for the screen to say (also emitted as [SimEvent.AiptasiaTreated]). */
    enum class TreatResult { KILLED, HID, NONE }

    /**
     * The keeper doses polyp [id] (null: the biggest untreated one) with [t]. An open polyp usually dies over
     * [AiptasiaTreatment.dieSec]; one pulled into its hole (night, just touched) mostly escapes and stays shut a while.
     * A dying polyp may leave a lacerate.
     */
    fun treat(sim: Simulation, t: AiptasiaTreatment, id: String? = null): TreatResult {
        val live = polyps(sim).filter { it.treatment == null }
        val p = (if (id != null) live.firstOrNull { it.id == id } else null) ?: live.maxByOrNull { it.size } ?: return TreatResult.NONE
        val rng = sim.growthRng
        val open = p.extension > 0.45
        val result = if (rng.chance(if (open) t.openKill else t.closedKill)) {
            p.treatment = t; p.treatLeftSec = t.dieSec
            if (rng.chance(t.fragment)) lacerate(sim, p)?.let { add(sim, listOf(it)) }
            TreatResult.KILLED
        } else {
            // it snapped shut in time: pulled into the rock, it sulks for an hour (the laser still singes it)
            p.disturb = 1.0
            p.lastFragAt = sim.simTimeSec
            if (t == AiptasiaTreatment.LASER) p.size = max(0.2, p.size * 0.6)
            TreatResult.HID
        }
        sim.emit(SimEvent.AiptasiaTreated(p.id, t, result == TreatResult.KILLED, sim.simTimeSec))
        return result
    }

    /**
     * A hitchhiker: one small aiptasia on the rock within a few cm of [near] (a store frag's spot, a new live rock).
     * Returns it, or null when there is no rock there.
     */
    fun hitchhike(sim: Simulation, near: Vec3): Coral? {
        if (polyps(sim).size >= CAP) return null
        val rng = sim.growthRng
        val rock = sim.rock
        repeat(8) {
            val q = rock.sdf.project(near + Vec3(rng.signed(), rng.signed() * 0.5, rng.signed()) * 4.0, 0.2)
            if (!rock.inWater(q, 0.5) || q.y < rock.sandTopCm + 0.5 || kotlin.math.abs(rock.sdf.dist(q) - 0.2) > 0.5) return@repeat
            val c = Coral("aiptasia#${sim.nextFragId()}", sim.species[SPECIES_ID], "Aiptasia", "", q, rock.sdf.gradient(q), rng.nextDouble() * 6.28)
            c.size = 0.3
            sim.corals += c
            sim.emit(SimEvent.CoralSpawned("", c.id, sim.simTimeSec))
            return c
        }
        return null
    }

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
