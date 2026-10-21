package com.reeflive.core.sim

import com.reeflive.core.geom.Vec3

/** What a newcomer came in on (each carries its own chance of a stowaway). */
enum class HitchSource { CORAL_FRAG, LIVE_ROCK, ANIMAL, DRY_ROCK }

/**
 * A pest or critter that rides into the tank uninvited (research 42 §B, research 01 §4.4): on a coral frag's plug or
 * rock, in live rock, in the water of a fish bag, or one that came in long ago and only now shows itself. [perDay] is
 * the background chance per sim day while the tank holds any life; [perSource] the chance per newcomer of each
 * [HitchSource] (ordinal). [arrive] puts the stowaway in the tank ([near] = where it came in, null for a background
 * arrival) and returns false when it could not (already established, nothing for it to live on).
 */
class HitchhikerKind(
    val id: String,
    val label: String,
    val perDay: Double,
    val perSource: DoubleArray,
    val arrive: (sim: Simulation, near: Vec3?) -> Boolean,
)

/**
 * Uninvited arrivals (research 42; shared with the aiptasia / nudibranch pests). The add commands call [onAdded] with
 * what came in; [step] runs on the chemistry tick with the sim's growth stream, so the slow background arrivals also
 * happen during an away catch-up. Each arrival emits [SimEvent.Hitchhiker]. Stateless apart from the kinds table: a
 * saved tank needs nothing extra (what arrived lives on in its own population).
 */
object Hitchhikers {
    /**
     * Bristleworms (Eurythoe-type amphinomids): nearly every piece of live rock carries some, frags and their plugs often
     * do, a fish bag rarely; with no new rock a dry-rock tank still gets its first one within a few months (one that rode
     * in as a juvenile in a frag's crevice, or with sand, grows big enough to be seen), est.
     */
    val BRISTLEWORM = HitchhikerKind(
        "bristleworm", "A bristleworm",
        perDay = 1.0 / 60,
        perSource = doubleArrayOf(0.15, 0.9, 0.03, 0.0),
    ) { sim, _ ->
        val m = sim.water.micro
        if (m.total(MicroGroup.BRISTLEWORM) >= ESTABLISHED_WORMS) false
        else {
            // one to three young worms, into the rock pores
            m.add(MicroGroup.BRISTLEWORM, MicroHabitat.ROCK, 1.0 + sim.hitchRng.nextInt(3))
            true
        }
    }

    /**
     * Aiptasia (glass anemone, sim/PestModel.kt): a small polyp on a frag's plug or a live rock, never in a fish bag or
     * on dry rock (aiptasia thread: 6 % per frag, 25 % per live rock, est.). One that came in with no spot named lands
     * on the rock at mid height.
     */
    val AIPTASIA = HitchhikerKind(
        "aiptasia", "An aiptasia anemone",
        perDay = 0.0,
        perSource = doubleArrayOf(0.06, 0.25, 0.0, 0.0),
    ) { sim, near ->
        val at = near ?: Vec3(sim.rock.lengthCm * (0.2 + 0.6 * sim.hitchRng.nextDouble()), (sim.rock.sandTopCm + sim.rock.waterLevelCm) / 2,
            sim.rock.widthCm * 0.4)
        AiptasiaModel.hitchhike(sim, at) != null
    }

    /** Every kind that can arrive on its own; another pest adds its row here. */
    val kinds: MutableList<HitchhikerKind> = mutableListOf(BRISTLEWORM, AIPTASIA)

    /** At this many worms the population is established and newcomers no longer count as an arrival. */
    const val ESTABLISHED_WORMS = 20.0

    /** A newcomer of [source] went in at [at]: each kind rolls its stowaway chance. */
    fun onAdded(sim: Simulation, source: HitchSource, at: Vec3?) {
        if (!sim.hitchhikersEnabled) return
        for (k in kinds) {
            val p = k.perSource[source.ordinal]
            if (p > 0 && sim.hitchRng.chance(p) && k.arrive(sim, at)) sim.emitHitchhiker(k)
        }
    }

    /** The background arrivals over [dtMin] sim minutes (only into a tank with something living in it). */
    fun step(sim: Simulation, dtMin: Double) {
        if (!sim.hitchhikersEnabled) return
        if (sim.organisms.none { it.alive } && sim.corals.isEmpty()) return
        val days = dtMin / 1440
        for (k in kinds) {
            if (k.perDay > 0 && sim.hitchRng.chance(k.perDay * days) && k.arrive(sim, null)) sim.emitHitchhiker(k)
        }
    }
}
