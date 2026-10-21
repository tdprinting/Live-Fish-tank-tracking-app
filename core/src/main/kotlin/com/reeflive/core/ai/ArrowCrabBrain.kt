package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.CrabLegs
import com.reeflive.core.sim.MicroGroup
import com.reeflive.core.sim.Organism
import com.reeflive.core.sim.WormField
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// =====================================================================================
// Arrow crab (Stenorhynchus seticornis, research 42 §A): a spider crab on stilts. By day it stands still under a
// ledge or on the rock in the open, legs spread, picking now and then at the film with its claws; at dusk and through
// the night it stalks slowly over the rock on its long legs (about 1 cm/s), reaching into every crevice for
// bristleworms, which it draws out and eats, and picking at detritus and anything meaty that settles. Not shy: it does
// not dash for cover. A hungry one pokes at hermits and newly molted shrimp; the rare kill of a small sleeping fish or a
// soft shrimp is the matrix's nightly roll (Simulation.nightRolls, interactions.json arrow_*).
// =====================================================================================
class ArrowCrabBrain(c: Ctx) : SpeciesBrain(c) {
    val legs: CrabLegs = o.limbRig as CrabLegs

    /** Bristleworms caught: visible ones taken off the rock, and ones drawn out of a crevice it probed (tests, captions). */
    var wormsCaught = 0; private set
    var crevicesProbed = 0; private set
    /** Pokes at a hermit or soft shrimp (harassment, never a kill here). */
    var pokes = 0; private set

    private var perch: Rest? = null
    private var perchAt = -1e9

    init {
        install(
            Perch(), HuntWorm(), Probe(), ArrowScavenge(), Pick(), Harass(),
            CrawlWander(this, { it.o.homePos }, 35.0, { c -> if (c.p.night) 0.35 + 0.2 * c.o.hunger else 0.08 }, "Stalking over the rock"),
            MoltHide(this) { restSpot(it, false) },
        )
    }

    /**
     * Its daytime stand: a rock spot near home with something over it (a ledge, an overhang) and room for the legs; it
     * keeps one for an hour (or until the rock moves under it).
     */
    override fun restSpot(c: Ctx, night: Boolean): Rest {
        val cached = perch
        if (cached != null && c.t - perchAt < PERCH_KEEP_S && kotlin.math.abs(c.rock.sdf.dist(cached.pos) - surfaceOffset(c)) < 1.0) return cached
        val around = o.homePos.takeIf { it != Vec3.ZERO } ?: o.pos
        var best: Pair<Vec3, Vec3>? = null
        var bs = -1.0
        for (i in 0 until 6) {
            val sp = c.rockSurfacePoint(around, 10.0, minNormalY = 0.3) ?: continue
            val s = c.rock.sdf.cover(sp.first + sp.second * 2.0)
            if (s > bs) { bs = s; best = sp }
        }
        val r = best?.let { (p, n) -> Rest(p + n * surfaceOffset(c), o.heading.rejectFrom(n).normalized(Vec3.X.rejectFrom(n).normalized(Vec3.Z)), n, 0) }
            ?: Rest(around, o.heading, o.up, 0)
        perch = r; perchAt = c.t
        return r
    }

    override fun initialPlace(c: Ctx) {
        val start = c.rockSurfacePoint(Vec3(c.rock.lengthCm * c.rng.range(0.3, 0.7), c.rock.sandTopCm + 12, c.rock.widthCm * 0.4), 20.0, minNormalY = 0.4)
        o.homePos = start?.let { (p, n) -> p + n * surfaceOffset(c) } ?: sandSpot(c, 20.0..100.0, 15.0..30.0, surfaceOffset(c))
        super.initialPlace(c)
    }

    override fun preTick(c: Ctx) {
        super.preTick(c)
        legs.tucked = false
        legs.pick = 0.0
    }

    /** A slow, stilted walk: steady, unhurried, a pause now and then to feel ahead with the claws. */
    private fun stalk(c: Ctx, out: SteerIntent, target: Vec3, fast: Boolean = false) {
        out.crawl = true; out.target = target; out.surfaceOffset = surfaceOffset(c); out.arriveRadius = 1.0
        out.allowGlass = true
        val pause = !fast && sin(c.t * 0.7 + c.o.appendagePhase) > 0.85
        out.speed = if (pause) 0.0 else if (fast) c.o.burstSpeed else min(c.o.cruiseSpeed * 1.5, max(c.o.cruiseSpeed, c.o.pos.distanceTo(target)))
        out.urgency = if (fast) 0.6 else 0.3
    }

    /** Claws within reach of [p] (the chelipeds reach about half a carapace ahead, the legs let it lean in). */
    private fun inReach(c: Ctx, p: Vec3) = c.o.pos.distanceTo(p) < REACH_L * c.L + 1.0

    inner class Perch : Behavior("perch", "Standing under a ledge") {
        override fun score(c: Ctx) = 0.45 * (1 - c.activity) + 0.1
        override fun update(c: Ctx, out: SteerIntent) {
            val r = restSpot(c, false)
            if (c.o.pos.distanceTo(r.pos) > 1.5) { stalk(c, out, r.pos); if (elapsed > 90) perchAt = -1e9; return }
            out.rest(r, 1.0)
            // still as a stick, the claws working the film now and then
            if (sin(c.t * 0.17 + c.o.appendagePhase) > 0.7) { legs.pick = 0.5; c.o.mouth = 0.5 }
        }
    }

    /**
     * A bristleworm out on the rock within sight (a body length or two of feelers): it walks over, reaches in and pulls it
     * off; one that slips back into its hole first is lost.
     */
    inner class HuntWorm : Behavior("hunt", "Hunting a bristleworm") {
        private var w: WormField.Worm? = null
        override val committed: Boolean get() = w != null && elapsed < 15
        override fun score(c: Ctx): Double {
            if (c.o.hunger < 0.1) return 0.0
            w = c.sim.worms.nearest(c.o.pos, HUNT_R)
            return if (w != null) 0.7 + 0.2 * c.o.hunger else 0.0
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val ww = w ?: run { finished = true; return }
            if (!ww.exposed) { w = null; finished = true; return }
            stalk(c, out, ww.head, fast = c.o.pos.distanceTo(ww.head) < 6)
            legs.pick = 0.4
            if (inReach(c, ww.head)) {
                legs.pick = 1.0
                if (c.rng.chance(CATCH_P) && c.sim.worms.catch(ww, c.o)) {
                    wormsCaught++
                    c.o.eat(WORM_G)
                    c.o.mouth = 1.0
                }
                w = null; finished = true
            }
            if (elapsed > 40) { w = null; finished = true }
        }
    }

    /**
     * At night it works the crevices: stands at one, claws deep inside, for half a minute, and now and then draws out a
     * worm hiding there. The chance follows how many worms the rock holds (a tank full of them pays every few probes).
     */
    inner class Probe : Behavior("probe", "Probing crevices for worms") {
        private var spot: Pair<Vec3, Vec3>? = null
        private var dur = 30.0
        override fun score(c: Ctx): Double {
            val worms = c.sim.water.micro.total(MicroGroup.BRISTLEWORM)
            if (worms < 1 || c.o.hunger < 0.15) return 0.0
            return (0.25 + 0.3 * c.o.hunger) * (0.3 + 0.7 * c.activity) * (worms / (worms + 20.0) + 0.3)
        }
        override fun enter(c: Ctx) {
            spot = null
            var bs = -1.0
            for (i in 0 until 6) {
                val sp = c.rockSurfacePoint(c.o.pos, 20.0, minNormalY = -0.1) ?: continue
                val s = c.rock.sdf.cover(sp.first + sp.second * 0.8)
                if (s > bs) { bs = s; spot = sp }
            }
            dur = c.rng.range(20.0, 45.0)
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val s = spot ?: run { finished = true; return }
            if (c.o.pos.distanceTo(s.first) > REACH_L * c.L + 1.5) { stalk(c, out, s.first + s.second * surfaceOffset(c)); if (elapsed > 60) finished = true; return }
            out.crawl = true; out.surfaceOffset = surfaceOffset(c); out.speed = 0.0
            legs.pick = 1.0
            c.o.appendagePhase += c.dt * 3
            if (elapsed > dur) {
                crevicesProbed++
                val worms = c.sim.water.micro.total(MicroGroup.BRISTLEWORM)
                if (worms >= 1 && c.rng.chance(1 - exp(-worms / PROBE_K))) {
                    c.sim.water.micro.remove(MicroGroup.BRISTLEWORM, 1.0)
                    wormsCaught++
                    c.o.eat(WORM_G)
                    c.o.mouth = 1.0
                }
                finished = true
            }
        }
    }

    inner class ArrowScavenge : Scavenge(this, 35.0, urgentAbove = 0.6, label = "Picking up food") {
        override fun update(c: Ctx, out: SteerIntent) { super.update(c, out); out.speed = min(out.speed, c.o.cruiseSpeed * 2); legs.pick = 0.7 }
    }

    /** Picking detritus and film off the rock with the claws, a bout at a time. */
    inner class Pick : Behavior("pick", "Picking at the rock") {
        private var spot: Pair<Vec3, Vec3>? = null
        private var dur = 20.0
        private var next = 0.0
        override fun score(c: Ctx) = (0.2 + 0.25 * c.o.hunger) * (0.4 + 0.6 * c.activity)
        override fun enter(c: Ctx) { spot = c.rockSurfacePoint(c.o.pos, 12.0, minNormalY = 0.0); dur = c.rng.range(15.0, 40.0) }
        override fun update(c: Ctx, out: SteerIntent) {
            val s = spot ?: run { finished = true; return }
            if (c.o.pos.distanceTo(s.first) > 1.5 + surfaceOffset(c)) { stalk(c, out, s.first + s.second * surfaceOffset(c)); if (elapsed > 50) finished = true; return }
            out.crawl = true; out.surfaceOffset = surfaceOffset(c); out.speed = 0.0
            legs.pick = 1.0
            if (c.t > next) { next = c.t + 3; c.o.hunger = max(0.0, c.o.hunger - 0.002); c.o.mouth = 1.0 }
            if (elapsed > dur) finished = true
        }
    }

    /**
     * A hungry crab at night pokes at a hermit or a soft, newly molted shrimp close by: it reaches in with the claws and
     * the other animal backs off (research 42 §A.4; a kill is only ever the nightly roll).
     */
    inner class Harass : Behavior("harass", "Poking at a neighbour") {
        private var prey: Organism? = null
        private var nextLook = 0.0
        override val targetVerb: String? get() = "poking at"
        override fun score(c: Ctx): Double {
            if (!c.p.night || c.o.hunger < HARASS_HUNGER) return 0.0
            if (c.t >= nextLook) {
                nextLook = c.t + 60.0
                prey = nearestOther(c, 12.0) { it.brainKind != BrainKind.ARROW_CRAB && !it.isFish && Predation.preyWeight(c.o, it, true) > 0 }
            }
            return if (prey?.alive == true) 0.5 else 0.0
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val p = prey ?: run { finished = true; return }
            if (!p.alive) { prey = null; finished = true; return }
            c.o.focusId = p.id
            stalk(c, out, p.pos)
            if (inReach(c, p.pos)) {
                legs.pick = 1.0
                pokes++
                c.sim.agentOf(p)?.let { it.brain.onAttacked(it.ctx, c.o.pos, 0.3, c.o) }
                prey = null; nextLook = c.t + 600.0; finished = true
            }
            if (elapsed > 30) { prey = null; finished = true }
        }
    }

    companion object {
        /** It spots an exposed worm this far off (cm): touch and smell, not eyes, over a couple of leg spans [L]. */
        const val HUNT_R = 18.0
        /** Claws reach this far (× length) ahead of the body centre. */
        const val REACH_L = 0.6
        /** Share of reaches that get a grip before the worm pulls back (est.). */
        const val CATCH_P = 0.6
        /** Grams of food in a worm (a few cm of polychaete, est.). */
        const val WORM_G = 0.05
        /** Probe success: 1 − e^(−worms / K) per crevice probed (est.: about one worm in every four probes at 20 worms). */
        const val PROBE_K = 70.0
        const val HARASS_HUNGER = 0.5
        const val PERCH_KEEP_S = 3600.0
    }
}
