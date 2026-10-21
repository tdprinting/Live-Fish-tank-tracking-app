package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.CrabLegs
import com.reeflive.core.sim.HealthModel
import com.reeflive.core.sim.Organism
import com.reeflive.core.sim.Simulation
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// =====================================================================================
// Anemone porcelain crab (Neopetrolisthes maculatus, research 45 §2). A filter feeder that lives with a host anemone:
// it sits among or just under the tentacles, big flat claws out in front, and sweeps the water with its two feather
// fans (the third maxillipeds) in turn, combing plankton and drifting food to its mouth; the claws are for show and
// for shoving, not for feeding. A pair shares a host; a third crab gets shoved off. Startled, it scuttles backwards
// under the tentacles. With no anemone in the tank it keeps to a sheltered rock ledge.
// =====================================================================================
class PorcelainCrabBrain(c: Ctx) : SpeciesBrain(c) {
    companion object {
        /** Hunger worked off per hunger gained while fanning in a fed reef tank (fines, plankton, detritus) [E]. */
        const val FILTER_GAIN = 1.4
        /** Crabs a host keeps: a pair (research 45 §2) [M]. */
        const val PER_HOST = 2
        /** Rival range: another porcelain crab this close at its host starts a shoving match (cm) [E]. */
        const val RIVAL_CM = 6.0
        /** Drifting food it can comb out of the water in front of it (cm). */
        const val CATCH_CM = 3.0
        const val HIDE_MIN_S = 20.0

        /** Suspended food a porcelain crab can live on, 0..1: a fed reef tank always has some (fines, detritus). */
        fun foodAvailable(sim: Simulation): Double = 0.5 + 0.5 * FileClamBrain.foodAvailable(sim)

        fun abstractFeed(o: Organism, sim: Simulation, dtMin: Double) {
            val rise = HealthModel.hungerRatePerMin(o, sim.water.s.tempC)
            o.hunger = max(0.0, o.hunger - FILTER_GAIN * rise * foodAvailable(sim) * 0.7 * dtMin)
        }
    }

    val legs: CrabLegs = o.limbRig as CrabLegs
    /** Its host anemone (null: none in the tank, it lives on the rock). */
    var host: Organism? = null; private set
    private var spot: Rest? = null
    private var spotAt = -1e9
    private var nextPause = 0.0
    private var pauseUntil = -1.0
    /** Drifting food items combed in, shoving matches and darts back under cover (tests, captions). */
    var catches = 0; private set
    var spars = 0; private set
    var retreats = 0; private set

    init {
        install(
            Retreat(), FilterFeed(), Spar(),
            CrawlWander(this, { restSpot(it, false).pos }, 6.0, { c -> if (c.p.night) 0.12 else 0.04 }, "Shuffling round its host"),
            MoltHide(this) { restSpot(it, false) },
        )
    }

    /** The host: the nearest live anemone that has room (fewer than [PER_HOST] other porcelain crabs on it). */
    private fun pickHost(c: Ctx): Organism? {
        val h = host
        if (h != null && h.alive) return h
        host = c.sim.organisms.filter { it.alive && it.brainKind == BrainKind.ANEMONE && crabsOn(c, it) < PER_HOST }
            .minByOrNull { it.pos.distanceTo(o.pos) }
        return host
    }

    private fun crabsOn(c: Ctx, an: Organism) = c.sim.organisms.count { x ->
        x !== o && x.alive && x.brainKind == BrainKind.PORCELAIN_CRAB && (c.sim.agentOf(x)?.brain as? PorcelainCrabBrain)?.host === an
    }

    /**
     * Its spot: on the rock right at the host's foot, under the tentacle crown (sheltered, flow carrying food past), the
     * body facing out toward the front glass; without a host, the most sheltered ledge near where it was put.
     */
    override fun restSpot(c: Ctx, night: Boolean): Rest {
        val cached = spot
        val h = host
        val hostMoved = h != null && cached != null && cached.pos.distanceTo(h.pos) > 6.0 + h.lengthCm * 0.5
        if (cached != null && !hostMoved && c.t - spotAt < 86400.0 && kotlin.math.abs(c.rock.envDist(cached.pos) - surfaceOffset(c)) < 1.0) return cached
        val an = pickHost(c)
        val around = an?.pos ?: o.homePos.takeIf { it != Vec3.ZERO } ?: o.pos
        val r = if (an != null) 4.0 + 0.25 * an.lengthCm else 8.0
        var best: Pair<Vec3, Vec3>? = null
        var bs = -1e9
        for (i in 0 until 10) {
            val sp = c.rockSurfacePoint(around, r, minNormalY = -0.2) ?: continue
            val cover = c.rock.sdf.cover(sp.first + sp.second * 1.0)
            val near = if (an != null) -0.15 * sp.first.distanceTo(an.pos) else 0.0
            val s = cover + near + 0.2 * sp.second.y
            if (s > bs) { bs = s; best = sp }
        }
        val out = best?.let { (p, n) -> Rest(p + n * surfaceOffset(c), Vec3.Z.rejectFrom(n).normalized(Vec3.X.rejectFrom(n).normalized(Vec3.Z)), n, 0) }
            ?: Rest(around, o.heading, o.up, 0)
        spot = out; spotAt = c.t
        o.homePos = out.pos
        return out
    }

    override fun initialPlace(c: Ctx) {
        pickHost(c)
        val r = restSpot(c, false)
        o.homePos = r.pos
        super.initialPlace(c)
    }

    override fun preTick(c: Ctx) {
        super.preTick(c)
        legs.tucked = false
        legs.pick = 0.0
        legs.fan = 0.0
    }

    private fun walk(c: Ctx, out: SteerIntent, target: Vec3, fast: Boolean = false) {
        out.crawl = true; out.target = target; out.surfaceOffset = surfaceOffset(c); out.arriveRadius = 0.6
        out.allowGlass = true
        out.speed = if (fast) c.o.burstSpeed else min(c.o.cruiseSpeed * 1.5, max(c.o.cruiseSpeed, c.o.pos.distanceTo(target)))
        out.urgency = if (fast) 0.7 else 0.3
    }

    /** A tap, a hand, a fish rushing it: it scuttles (backwards, claws still out) under its host and stays a while. */
    inner class Retreat : Behavior("retreat", "Scuttling back under its anemone") {
        override val label: String get() = if (host != null) "Scuttling back under its anemone" else "Scuttling into a crevice"
        private var arrived = false
        override val committed: Boolean get() = elapsed < HIDE_MIN_S
        override fun score(c: Ctx): Double {
            if (threat != null || startledRecently && startleMag > 0.1) return 0.97
            val big = nearestOther(c, 5.0) { it.isFish && it.lengthCm > 2 * c.o.lengthCm && it.vel.length() > 4.0 }
            return if (big != null) 0.9 else 0.0
        }
        override fun enter(c: Ctx) { retreats++; arrived = false; startleAt = -1e9 }
        override fun update(c: Ctx, out: SteerIntent) {
            val r = restSpot(c, false)
            if (arrived || c.o.pos.distanceTo(r.pos) < 1.0) {
                arrived = true
                out.rest(r, 1.0); legs.tucked = true; c.o.extension = 0.1
                if (elapsed > HIDE_MIN_S && threat == null) finished = true
                return
            }
            walk(c, out, r.pos, fast = true)
            // it backs away rather than turning to run
            out.faceDir = (c.o.pos - r.pos).normalized(c.o.heading)
        }
    }

    /**
     * Filter feeding (research 45 §2): sat at its spot facing out, the two fans sweeping in turn, a short rest every
     * minute or so; drifting food within reach is combed in, and the water's fines and plankton feed it slowly.
     */
    inner class FilterFeed : Behavior("filter", "Fanning the water for food") {
        override val label: String get() = when {
            b.c.t < pauseUntil -> "Resting its fans"
            host != null -> "Fanning the water from its anemone"
            else -> "Fanning the water for food"
        }
        private val b = this@PorcelainCrabBrain
        private var nextFeed = 0.0
        override fun score(c: Ctx) = if (c.p.night) 0.6 else 0.5
        override fun update(c: Ctx, out: SteerIntent) {
            val r = restSpot(c, false)
            if (c.o.pos.distanceTo(r.pos) > 1.2) { walk(c, out, r.pos); return }
            out.rest(r, 1.0)
            c.o.extension = 1.0
            if (c.t > nextPause) { pauseUntil = c.t + c.rng.range(3.0, 8.0); nextPause = pauseUntil + c.rng.range(30.0, 90.0) }
            val fanning = c.t >= pauseUntil
            if (fanning) legs.fan = 1.0
            if (fanning && c.t >= nextFeed) {
                nextFeed = c.t + 1.0
                val rise = HealthModel.hungerRatePerMin(c.o, c.sim.water.s.tempC) / 60.0
                c.o.hunger = max(0.0, c.o.hunger - FILTER_GAIN * rise * foodAvailable(c.sim))
                val front = c.o.pos + c.o.heading * (0.8 * c.L) + c.o.up * (0.5 * c.L)
                c.food.nearest(front, CATCH_CM + c.L) { !it.settled && c.o.species.dietMatch(it.kind) > 0 }?.let { c.bite(it); catches++; c.o.mouth = 1.0 }
            }
        }
    }

    /**
     * Shoving match: a porcelain crab that is not its mate (a host keeps two) creeps in on its spot; they face off and
     * push with their big flat claws, and the smaller one gives up and finds somewhere else.
     */
    inner class Spar : Behavior("spar", "Shoving a rival with its claws") {
        private var rival: Organism? = null
        private var dur = 8.0
        override val targetVerb: String? get() = "shoving"
        override fun score(c: Ctx): Double {
            val h = host ?: return 0.0
            rival = nearestOther(c, RIVAL_CM) { it.brainKind == BrainKind.PORCELAIN_CRAB && it.alive }
            val r = rival ?: return 0.0
            val theirs = (c.sim.agentOf(r)?.brain as? PorcelainCrabBrain)?.host
            // its mate (one other crab on the host) is welcome; a newcomer is not
            return if (theirs !== h || crabsOn(c, h) > PER_HOST - 1) 0.7 else 0.0
        }
        override fun enter(c: Ctx) { spars++; dur = c.rng.range(5.0, 15.0) }
        override fun update(c: Ctx, out: SteerIntent) {
            val r = rival ?: run { finished = true; return }
            out.rest(Rest(c.o.pos, (r.pos - c.o.pos).rejectFrom(c.o.up).normalized(c.o.heading), c.o.up, 0), 3.0)
            legs.pick = 0.6 + 0.4 * sin(c.t * 9.0)
            c.o.focusId = r.id
            if (elapsed > dur) {
                // the smaller crab leaves to find another host or ledge
                if (c.o.lengthCm < r.lengthCm) { host = null; spot = null; o.homePos = Vec3.ZERO; pickOtherHost(c, r) }
                finished = true
            }
        }
        override fun exit(c: Ctx) { c.o.focusId = null }
    }

    private fun pickOtherHost(c: Ctx, winner: Organism) {
        val wonAt = (c.sim.agentOf(winner)?.brain as? PorcelainCrabBrain)?.host
        host = c.sim.organisms.filter { it.alive && it.brainKind == BrainKind.ANEMONE && it !== wonAt && crabsOn(c, it) < PER_HOST }
            .minByOrNull { it.pos.distanceTo(o.pos) }
        if (host == null) o.homePos = c.o.pos + c.o.heading * -10.0
        spotAt = -1e9
    }
}
