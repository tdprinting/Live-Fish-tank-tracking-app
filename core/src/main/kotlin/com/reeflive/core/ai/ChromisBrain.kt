package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.Ev
import com.reeflive.core.sim.Evidence
import com.reeflive.core.sim.FoodParticle
import com.reeflive.core.sim.Organism
import com.reeflive.core.sim.SimEvent
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Blue-green chromis, *Chromis viridis* (docs/research/41_firefish_chromis.md §2). A mid-water shoal over the reef:
 * each fish keeps a body length or so from its mates, lines up with them and drifts with the group, facing into the
 * current to pick plankton. A scare runs through the shoal (one bolting sets off its neighbours) and the whole group
 * dives into the rock; at night each fish shelters against the rock.
 *
 * The group has a pecking order by size and boldness (research 41 §2.5): the dominant fish chases the bottom fish. In a
 * small group (2–5) the chases fall on one or two fish, which pile up social stress until they sicken ("last one
 * standing"); in a group of 6 or more the aggression spreads thin and the shoal stays calm. A lone chromis has nobody
 * to quarrel with.
 */
class ChromisBrain(c: Ctx) : SpeciesBrain(c) {
    /** Where this fish shelters at night and when scared: pressed to the rock face near the middle of the reef. */
    private val nightNear: Vec3 = Vec3(c.rock.lengthCm * c.o.rng.range(0.25, 0.75), c.rock.sandTopCm + c.o.rng.range(8.0, 16.0),
        c.rock.widthCm * c.o.rng.range(0.3, 0.6))
    private var nightRest: Rest? = null
    /** The shoal's patch of open water (a wander point near the reef top, shared in effect through cohesion). */
    private var wanderAt = Vec3.ZERO
    private var nextWander = 0.0
    /** Shoal summary, refreshed every [SCAN_S]: mates near, their centre and mean velocity, the push away from crowding. */
    private var scanIn = 0.0
    private var mates = 0
    private var centre = Vec3.ZERO
    private var meanVel = Vec3.ZERO
    private var push = Vec3.ZERO
    /** Rank in the tank's chromis order (0 = top) and group size, refreshed every [RANK_S]. */
    var rank = 0; private set
    var groupSize = 1; private set
    private var rankIn = 0.0

    init {
        install(Shoal(), ChromisFeed(), Peck(),
            Flee(this, { c, from -> c.safe(shelterRest(c).pos + (c.o.pos - from).horizontal().normalized(Vec3.Z) * 2.0) }, "Diving into the rock"),
            Sleep(this, "Sheltering in the rock", 0.75), VisitCleaner(this), GlassCurious(this), SurfaceGasp(this), Flash(this))
    }

    private fun shelterRest(c: Ctx): Rest = nightRest ?: rockShelterRest(c, nightNear).also { nightRest = it }

    override fun restSpot(c: Ctx, night: Boolean): Rest =
        if (night) shelterRest(c) else Rest(c.safe(Vec3(nightNear.x, c.zoneY(0.6), nightNear.z)), Vec3.X)

    override fun shelterSpot(c: Ctx): Vec3? = shelterRest(c).pos

    override fun preTick(c: Ctx) {
        super.preTick(c)
        rankIn -= c.dt
        if (rankIn <= 0) { rankIn = RANK_S * c.rng.range(0.8, 1.2); rerank(c) }
        scanIn -= c.dt
        if (scanIn <= 0) { scanIn = SCAN_S; scan(c) }
    }

    /** The tank's chromis ranked by [Interactions.dominates] (size, then boldness and aggression): my place and the count. */
    private fun rerank(c: Ctx) {
        var n = 1
        var above = 0
        for (m in c.sim.organisms) {
            if (m === o || !m.alive || m.brainKind != BrainKind.CHROMIS) continue
            n++
            if (Interactions.dominates(m, o)) above++
        }
        groupSize = n; rank = above
    }

    /**
     * One pass over the neighbours (allocation-free apart from the hash query): mates for cohesion, alignment and
     * separation, and alarm contagion from a mate that has just bolted.
     */
    private fun scan(c: Ctx) {
        var n = 0
        var cx = 0.0; var cy = 0.0; var cz = 0.0
        var vx = 0.0; var vy = 0.0; var vz = 0.0
        var px = 0.0; var py = 0.0; var pz = 0.0
        val sep = SEPARATION_BL * c.L
        var alarm: Organism? = null
        for (m in c.p.neighbors(o.pos, SHOAL_CM)) {
            if (m === o || !m.alive || m.brainKind != BrainKind.CHROMIS) continue
            n++
            cx += m.pos.x; cy += m.pos.y; cz += m.pos.z
            vx += m.vel.x; vy += m.vel.y; vz += m.vel.z
            val dx = o.pos.x - m.pos.x; val dy = o.pos.y - m.pos.y; val dz = o.pos.z - m.pos.z
            val d = sqrt(dx * dx + dy * dy + dz * dz)
            if (d < sep && d > 1e-6) { val k = (sep - d) / (sep * d); px += dx * k; py += dy * k; pz += dz * k }
            val f = m.brain.current
            if (alarm == null && f is Flee && f.elapsed < 1.0 && d < ALARM_CM) alarm = m
        }
        mates = n
        if (n > 0) {
            centre = Vec3(cx / n, cy / n, cz / n); meanVel = Vec3(vx / n, vy / n, vz / n)
        }
        push = Vec3(px, py, pz)
        // a mate bolting next to it sets it off too (the shoal flashes into the rock together)
        if (alarm != null && !startledRecently && c.t - startleAt > 5.0 && threat == null) {
            startleAt = c.t; startleFrom = alarm.pos - alarm.vel.normalized(Vec3.Y) * 5.0; startleMag = 0.4
        }
    }

    /** Aggression per hour of the top fish at group size [n] (research 41 §2.5: 3–4 is worst, 6+ calm) [design]. */
    fun pecksPerHour(n: Int): Double = when {
        n <= 1 -> 0.0
        n == 2 -> 4.0
        n <= 4 -> 8.0
        n == 5 -> 4.0
        else -> 1.5
    }

    /**
     * Shoaling in open water (research 41 §2.3–2.4): cohesion toward the mates within [SHOAL_CM], alignment with their
     * swimming, and a push away inside [SEPARATION_BL] body lengths, around a wander point that drifts over the reef in
     * the upper water. Alone it hovers and patrols like any small damsel.
     */
    inner class Shoal : Behavior("shoal", "Schooling in open water") {
        private var nextSnap = 0.0
        override fun score(c: Ctx) = if (c.p.night) 0.0 else 0.45
        override fun update(c: Ctx, out: SteerIntent) {
            val o = c.o
            if (c.t > nextWander || o.pos.distanceTo(wanderAt) < 3.0) {
                nextWander = c.t + c.rng.range(8.0, 20.0)
                val z = o.species.zone
                wanderAt = c.safe(Vec3(c.rng.range(12.0, c.rock.lengthCm - 12.0), c.zoneY(c.rng.range(z.yMin, z.yMax)),
                    c.rng.range(10.0, c.rock.widthCm - 10.0)))
            }
            var goal = wanderAt
            if (mates > 0) {
                // the bottom fish of a quarrelsome small group hangs back at the edge
                val cohesion = if (groupSize in 2..5 && rank == groupSize - 1) 0.35 else 0.7
                goal = wanderAt.lerp(centre, cohesion) + meanVel * 0.6
            }
            goal = c.safe(goal + push * (2.0 * c.L))
            val z = o.species.zone
            goal = goal.withY(goal.y.coerceIn(c.zoneY(z.yMin), c.zoneY(z.yMax)))
            val d = o.pos.distanceTo(goal)
            out.seek(goal, o.cruiseSpeed * (if (d > 15) 0.9 else 0.5), arrive = 2.0)
            out.hover = d < 4.0
            out.separation = 0.6
            out.maxPitch = 0.3
            if (d < 4.0) {
                val f = c.sim.flow.velocityFast(o.pos).horizontal()
                if (f.length() > 0.5) out.faceDir = f.normalized(Vec3.X) * -1.0
                else if (meanVel.length() > 0.5) out.faceDir = meanVel.horizontal().normalized(o.heading)
            }
            // plankton picking (research 41 §2.6): a snap every few seconds
            if (c.t > nextSnap) { nextSnap = c.t + c.rng.range(3.0, 10.0); o.mouth = 1.0 }
        }
    }

    /** Quick to any food in the water column; not a bottom picker (research 41 §2.6). */
    inner class ChromisFeed : Feed(this@ChromisBrain, 70.0) {
        override fun accept(c: Ctx, f: FoodParticle) = !f.settled
        override fun scoreMul(c: Ctx, f: FoodParticle): Double = if (f.pos.y > c.zoneY(0.3)) 1.0 else 0.6
    }

    /**
     * The pecking order (research 41 §2.5): a fish above the bottom of the order chases the lowest-ranked mate in reach,
     * at a rate set by the group size and its own place; the chased fish takes social stress and fears its chaser.
     */
    inner class Peck : Behavior("peck", "Chasing a tankmate") {
        private var who: Organism? = null
        private var nextAt = -1.0
        private var chasing = false
        override val committed: Boolean get() = chasing && !finished
        override val targetVerb: String get() = "chasing"
        override fun score(c: Ctx): Double {
            val o = c.o
            if (c.p.night || groupSize < 2 || rank >= groupSize - 1 || o.stress > 0.6 || o.health < 0.5) return 0.0
            if (chasing) return if (finished) 0.0 else 0.62
            if (nextAt < 0) { schedule(c); return 0.0 }
            if (c.t < nextAt) return 0.0
            // the lowest-ranked mate in reach: the one at the bottom of the order is the usual target
            var best: Organism? = null
            var bestScore = -1
            for (m in c.p.neighbors(o.pos, PECK_REACH_CM)) {
                if (m === o || !m.alive || m.brainKind != BrainKind.CHROMIS || !Interactions.dominates(o, m)) continue
                val r = (c.sim.agentOf(m)?.brain as? ChromisBrain)?.rank ?: continue
                if (r > bestScore) { bestScore = r; best = m }
            }
            who = best
            if (best == null) { nextAt = c.t + 30.0; return 0.0 }
            return 0.62
        }
        private fun schedule(c: Ctx) {
            val rate = pecksPerHour(groupSize) * (1.0 - rank.toDouble() / max(1, groupSize - 1)) * (0.5 + c.o.aggression)
            nextAt = if (rate <= 0) c.t + 600.0 else c.t + 3600.0 / rate * c.rng.range(0.5, 1.5)
        }
        override fun enter(c: Ctx) { schedule(c); chasing = true }
        override fun exit(c: Ctx) { chasing = false; who = null }
        override fun update(c: Ctx, out: SteerIntent) {
            val w = who ?: run { finished = true; return }
            c.o.focusId = w.id
            out.seek(w.pos, c.o.burstSpeed * 0.6, arrive = 0.5, urgency = 0.8)
            if (c.o.pos.distanceTo(w.pos) < 0.6 * c.L + 0.5 * w.lengthCm) {
                c.sim.agentOf(w)?.let { it.brain.onAttacked(it.ctx, c.o.pos, PECK_MAG, c.o) }
                w.socialStress += PECK_STRESS
                c.sim.emit(SimEvent.Chase(c.o.id, w.id, "PECK", nip = true, displayOnly = false, simTimeSec = c.sim.simTimeSec))
                finished = true
            }
            if (elapsed > 2.5 || !w.alive) finished = true
        }
    }

    companion object {
        /** Mates within this range count for the shoal (cm) and closer than this many body lengths push apart [design]. */
        @Ev(Evidence.DESIGN_ESTIMATE, "41 §2.4") const val SHOAL_CM = 25.0
        const val SEPARATION_BL = 1.1
        /** A mate bolting this close sets it off (cm) [design]. */
        const val ALARM_CM = 15.0
        const val SCAN_S = 0.25
        const val RANK_S = 10.0
        /** Pecking: reach (cm), hit strength (startle) and social stress per chase [design]. */
        @Ev(Evidence.DESIGN_ESTIMATE, "41 §2.5") const val PECK_REACH_CM = 30.0
        const val PECK_MAG = 0.4
        const val PECK_STRESS = 0.04
    }
}
