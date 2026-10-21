package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.HealthModel
import com.reeflive.core.sim.Organism
import com.reeflive.core.sim.Simulation
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

// =====================================================================================
// Tiger tail sea cucumber (Holothuria hilla, research 45 §4). A long, soft deposit feeder: by day it lies with its front
// end pushed under a rock ledge and the tail out on the sand; after dark it creeps slowly over the open sand on its tube
// feet, its ring of mop-headed oral tentacles pressing on the sand and wiping the film (detritus, bacteria, microalgae)
// into its mouth one at a time, and leaves a trail of cleaned sand behind. Startled or grabbed, it shortens into a firm
// fat sausage and pulls its tentacles in. Badly stressed, attacked, or dead in the tank, it releases holothurin, a
// saponin that poisons fish through the gills ([com.reeflive.core.sim.WaterState.holothurin]): carbon takes it out.
//
// The body is not a rigid rod: it lies along the path its head crawled ([trail], fed to the body spline), so it curves
// over the sand the way the animal went, never across rock it did not crawl over.
// =====================================================================================
class SeaCucumberBrain(c: Ctx) : SpeciesBrain(c) {
    companion object {
        /** Feeding crawl and the quicker crawl home (cm/s). Holothurians creep ~0.5-2 m/h [E, research 45 §4 GAP]. */
        const val FEED_CM_S = 0.05
        const val HOME_CM_S = 0.1
        /** Fastest the head swings round (rad/s) [E]. */
        const val TURN_RAD_S = 0.06
        /** Spacing of the head's track points (cm). */
        const val TRAIL_STEP_CM = 1.0
        /** Sand film taken per second of tentacle feeding: share of the detritus under the head [E]. */
        const val SIFT_SHARE = 0.03
        /** Hunger worked off per unit of sifted detritus load (index × cm²) [E]. */
        const val SIFT_GAIN = 0.06
        /** Tentacle feeding cycles per second: a tentacle wiped in the mouth about every 2 s, 20 in the ring [E]. */
        const val FEED_CYCLE_HZ = 0.025
        /** Stress above which it spills holothurin if it lasts [STRESS_SPILL_S] (research 45 §0: > 0.7) [E]. */
        const val TOXIN_STRESS = 0.7
        const val STRESS_SPILL_S = 1800.0
        /** Least time between two spills (s). */
        const val SPILL_COOLDOWN_S = 3 * 86400.0
        /** A live spill's share of a whole body's holothurin (the dead body leaches all of it) [E]. */
        const val SPILL_SHARE = 0.25
        /** Holothurin index of a 25 cm cucumber's whole store in Tee's 227 L system ([WaterState.holothurin]). */
        const val STORE_INDEX_227L = 1.0
        /** A dead cucumber leaches its store over this long (h) [E]. */
        const val CARCASS_LEACH_H = 6.0
        /** Pump entrainment (research 45 §4): within this of an intake (cm), this chance per hour [E]. */
        const val INTAKE_CM = 15.0
        const val INTAKE_PER_H = 0.001

        /** Holothurin a [o]-sized cucumber holds, as a [com.reeflive.core.sim.WaterState.holothurin] index in [sim]. */
        fun storeIndex(o: Organism, sim: Simulation): Double {
            val r = o.lengthCm / 25.0
            return STORE_INDEX_227L * r * r * r * 227.0 / sim.water.s.volumeL.coerceAtLeast(20.0)
        }

        /** [share] of its holothurin into the water. */
        fun spill(o: Organism, sim: Simulation, share: Double) {
            sim.water.s.holothurin += share * storeIndex(o, sim)
        }

        /** A dead cucumber's body leaching its toxin: the store goes out over [CARCASS_LEACH_H] (Simulation.corpses). */
        fun carcassLeach(o: Organism, sim: Simulation, ageSec: Double, dtMin: Double) {
            if (ageSec > CARCASS_LEACH_H * 3600) return
            val b = sim.agentOf(o)?.brain as? SeaCucumberBrain
            if (b?.leached == true) return
            val left = 1 - SPILL_SHARE * (b?.spills ?: 0).coerceAtMost(2)
            spill(o, sim, left * min(dtMin * 60, CARCASS_LEACH_H * 3600 - ageSec) / (CARCASS_LEACH_H * 3600))
        }

        /** Fast-forward: the sand bed's film (it lives on what settles; one per tank, research 45 §4). */
        fun abstractFeed(o: Organism, sim: Simulation, dtMin: Double) {
            val rise = HealthModel.hungerRatePerMin(o, sim.water.s.tempC)
            val film = (sim.water.s.detritus / 0.2).coerceIn(0.2, 1.2)
            // other cucumbers share the same sand
            val n = sim.organisms.count { it.alive && it.brainKind == BrainKind.SEA_CUCUMBER }
            o.hunger = max(0.0, o.hunger - rise * 1.1 * film / max(1, n).toDouble().let { 0.4 + 0.6 * it } * dtMin)
        }
    }

    /** The head's track, head first: the body lies along it (the spline override). */
    private val trail = ArrayList<Vec3>()
    private val body = ArrayList<Vec3>()
    /** 0 relaxed .. 1 balled up; tentacles out 0..1; the feeding and crawl-wave phases. */
    var contraction = 0.0; private set
    var tentacles = 0.0; private set
    private var feedPhase = 0.0
    private var crawlPhase = 0.0
    private var crawlAmp = 0.0
    private var moving = false
    private var hide: Rest? = null
    private var stressedS = 0.0
    private var lastSpill = -1e9
    private var siftAcc = 0.0
    private var intakeAcc = 0.0
    /** Sand film sifted (load), cm crawled, spills (tests, captions). */
    var sifted = 0.0; private set
    var crawled = 0.0; private set
    var contractions = 0; private set
    /** Live spills so far, and whether the dead body already let its toxin go (pump: all at once). */
    var spills = 0; private set
    var leached = false; private set

    init { install(Contract(), Hide(), SandFeed()) }

    private val lift: Double get() = 0.05
    private val halfW: Double get() = 0.066 * c.L
    private val bodyH: Double get() = 0.08 * c.L

    /** Room for the body here: the rock clear of the body's top and sides, inside the glass. */
    private fun clear(c: Ctx, p: Vec3): Boolean {
        val m = halfW + 0.5
        if (p.x < m || p.x > c.rock.lengthCm - m || p.z < m || p.z > c.rock.widthCm - m) return false
        return c.rock.sdf.dist(p + Vec3.Y * (0.55 * bodyH)) > halfW + 0.3
    }

    private fun onSand(c: Ctx, p: Vec3) = p.withY(c.sim.sand.surfaceY(p.x, p.z) + lift)

    /**
     * Its daytime spot: on the sand at the foot of the rock where the most rock overhangs, the head facing in under it
     * (the body then lies back out over the sand). A deterministic grid over the floor, away from other cucumbers.
     */
    private fun findHide(c: Ctx): Rest {
        var best: Rest? = null
        var bs = -1e9
        var x = 6.0
        while (x < c.rock.lengthCm - 6) {
            var z = 5.0
            while (z < c.rock.widthCm - 5) {
                val p = onSand(c, Vec3(x, 0.0, z))
                val top = p + Vec3.Y * (0.55 * bodyH)
                val d = c.rock.sdf.dist(top)
                if (d > halfW + 0.3 && d < halfW + 2.5) {
                    val g = c.rock.sdf.gradient(top).horizontal()
                    if (g.lengthSq() > 1e-6) {
                        val out = g.normalized()
                        // the body must fit back out over the sand behind the head
                        var fits = true
                        var k = 1
                        while (k <= 4 && fits) { fits = clear(c, p + out * (0.25 * k * c.L)); k++ }
                        val others = c.sim.organisms.any { it !== c.o && it.alive && it.brainKind == BrainKind.SEA_CUCUMBER && it.pos.distanceTo(p) < 0.6 * c.L }
                        if (fits && !others) {
                            val s = c.rock.sdf.cover(top) - 0.1 * d
                            if (s > bs) { bs = s; best = Rest(p, out * -1.0, Vec3.Y) }
                        }
                    }
                }
                z += 2.5
            }
            x += 2.5
        }
        return best ?: Rest(onSand(c, Vec3(0.5 * c.rock.lengthCm, 0.0, 0.75 * c.rock.widthCm)), Vec3.X, Vec3.Y)
    }

    override fun restSpot(c: Ctx, night: Boolean): Rest = hide ?: findHide(c).also { hide = it }

    override fun initialPlace(c: Ctx) {
        val r = restSpot(c, false)
        o.homePos = r.pos
        o.placeAt(r.pos, r.heading)
        o.up = Vec3.Y
        resetTrail(c, r.pos, r.heading)
    }

    /** A straight body back from [head] along -[fwd] (bent away from rock if it has to be). */
    private fun resetTrail(c: Ctx, head: Vec3, fwd: Vec3) {
        trail.clear()
        trail.add(head)
        var p = head
        var d = fwd.horizontal().normalized(Vec3.X) * -1.0
        var len = 0.0
        while (len < 1.3 * c.L) {
            var q = onSand(c, p + d * TRAIL_STEP_CM)
            if (!clear(c, q)) {
                // swing the tail away from the rock
                for (a in doubleArrayOf(0.3, -0.3, 0.6, -0.6, 1.0, -1.0)) {
                    val d2 = Vec3.rotateAbout(d, Vec3.Y, a)
                    val q2 = onSand(c, p + d2 * TRAIL_STEP_CM)
                    if (clear(c, q2)) { d = d2; q = q2; break }
                }
            }
            trail.add(q); p = q; len += TRAIL_STEP_CM
        }
    }

    override fun abstractPose(c: Ctx, out: SteerIntent) {
        // fast-forward: it stays where it is (it feeds abstractly, Simulation.abstractFeed)
        out.place(o.pos, o.heading, Vec3.Y, 30.0)
        out.splineOverride = bodyLine(c)
        o.sleeping = !c.p.night
    }

    override fun preTick(c: Ctx) {
        super.preTick(c)
        moving = false
        if (trail.isEmpty()) resetTrail(c, o.pos, o.heading)
        // long, heavy stress: it spills some of its toxin (research 45 §4: "releases holothurin under stress")
        if (o.stress > TOXIN_STRESS) stressedS += c.dt else stressedS = max(0.0, stressedS - c.dt)
        if (stressedS > STRESS_SPILL_S && c.t - lastSpill > SPILL_COOLDOWN_S) { doSpill(c); stressedS = 0.0 }
        intakeCheck(c)
    }

    private fun doSpill(c: Ctx) {
        lastSpill = c.t
        spills++
        spill(o, c.sim, SPILL_SHARE)
    }

    /** Grabbed or bitten: it balls up, and a real bite makes it spill. */
    override fun onAttacked(c: Ctx, from: Vec3, mag: Double, by: Organism?) {
        super.onAttacked(c, from, mag, by)
        if (mag >= 0.5 && c.t - lastSpill > SPILL_COOLDOWN_S) doSpill(c)
    }

    /** An uncovered pump intake within [INTAKE_CM] of its body can suck it in (research 45 §4). */
    private fun intakeCheck(c: Ctx) {
        intakeAcc += c.dt
        if (intakeAcc < 60.0) return
        val h = intakeAcc / 3600.0
        intakeAcc = 0.0
        val f = c.sim.fluid ?: return
        for (p in f.pumps) {
            if (!p.on) continue
            val at = p.intake ?: p.pos
            if (at.distanceTo(o.pos) < INTAKE_CM && c.rng.chance(INTAKE_PER_H * h)) {
                c.sim.killNow(o, "sucked into the ${p.name} intake")
                spill(o, c.sim, 1 - SPILL_SHARE * spills.coerceAtMost(2))
                leached = true
                return
            }
        }
    }

    /**
     * One step of creeping toward [goal] at [speed]: the head swings toward it no faster than [TURN_RAD_S], steering
     * round rock it would hit, and the rest of the body follows the track.
     */
    private fun crawl(c: Ctx, out: SteerIntent, goal: Vec3, speed: Double): Boolean {
        val head = o.pos
        val want = (goal - head).horizontal()
        val dist = want.length()
        var fwd = o.heading.horizontal().normalized(Vec3.X)
        if (dist > 0.3) {
            val to = want.normalized(fwd)
            val ang = acos((fwd dot to).coerceIn(-1.0, 1.0))
            val side = sign((fwd cross to).y).let { if (it == 0.0) 1.0 else it }
            fwd = Vec3.rotateAbout(fwd, Vec3.Y, side * min(ang, TURN_RAD_S * c.dt))
        }
        val step = min(speed * c.dt, dist)
        var next = onSand(c, head + fwd * step)
        if (!clear(c, next)) {
            // blocked: swing toward the clearer side and creep on
            val l = Vec3.rotateAbout(fwd, Vec3.Y, 0.6); val r = Vec3.rotateAbout(fwd, Vec3.Y, -0.6)
            val dl = c.rock.sdf.dist(onSand(c, head + l * 2.0) + Vec3.Y * (0.55 * bodyH))
            val dr = c.rock.sdf.dist(onSand(c, head + r * 2.0) + Vec3.Y * (0.55 * bodyH))
            fwd = Vec3.rotateAbout(fwd, Vec3.Y, (if (dl > dr) 1.0 else -1.0) * 3 * TURN_RAD_S * c.dt)
            next = onSand(c, head + fwd * step)
            if (!clear(c, next)) next = onSand(c, head)
        }
        val moved = next.distanceTo(head)
        if (moved > 1e-4) { moving = true; crawled += moved }
        out.place(next, fwd, Vec3.Y, 30.0)
        if (trail.isEmpty() || trail[0].distanceTo(next) >= TRAIL_STEP_CM) { trail.add(0, next); trimTrail(c) }
        out.splineOverride = bodyLine(c, next)
        return dist < 0.6
    }

    private fun trimTrail(c: Ctx) {
        var len = 0.0
        var i = 1
        while (i < trail.size) {
            len += trail[i].distanceTo(trail[i - 1])
            if (len > 1.3 * c.L) { while (trail.size > i + 1) trail.removeAt(trail.size - 1); break }
            i++
        }
    }

    /** The body line, head first: the head, then the track, each point on the sand as it is now. */
    private fun bodyLine(c: Ctx, head: Vec3 = o.pos): List<Vec3> {
        body.clear()
        body.add(head)
        for (p in trail) if (p.distanceSqTo(head) > 0.01) body.add(onSand(c, p))
        return body
    }

    /** Holds still where it is, the body along its track. */
    private fun stay(c: Ctx, out: SteerIntent) {
        out.place(o.pos, o.heading, Vec3.Y, 30.0)
        out.splineOverride = bodyLine(c)
    }

    override fun postSteer(c: Ctx) {
        val dt = c.dt
        val cur = utility.current
        val ball = if (cur is Contract) 1.0 else 0.0
        contraction += (ball - contraction) * min(1.0, dt * (if (ball > contraction) 1.2 else 0.08))
        val tentTarget = if (cur is SandFeed && contraction < 0.3) 1.0 else 0.0
        tentacles += (tentTarget - tentacles) * min(1.0, dt * (if (tentTarget > tentacles) 0.3 else 1.5))
        feedPhase = (feedPhase + dt * FEED_CYCLE_HZ * tentacles) % com.reeflive.core.sim.Organism.APPENDAGE_WRAP
        crawlAmp += ((if (moving) 1.0 else 0.0) - crawlAmp) * min(1.0, dt * 0.5)
        // the swelling travels head to tail about once every 8 s while it creeps
        crawlPhase = (crawlPhase + dt * 0.12 * crawlAmp) % 1000.0
        o.appendagePhase = feedPhase
        o.pecPhase = crawlPhase
        o.pecAmp = crawlAmp
        o.pectoral = contraction
        o.mouth = tentacles
        o.amp = 0.0
        o.extension = 1 - contraction
        o.up = Vec3.Y
    }

    /** Balls up where it is: a tap, a hand, a fish nipping at it, or heavy stress. */
    inner class Contract : Behavior("contract", "Balled up tight") {
        private var hold = 60.0
        override val committed: Boolean get() = elapsed < hold
        override fun score(c: Ctx): Double = when {
            startledRecently || threat != null -> 0.97
            o.stress > TOXIN_STRESS -> 0.8
            else -> 0.0
        }
        override fun enter(c: Ctx) { contractions++; hold = c.rng.range(60.0, 240.0); startleAt = -1e9 }
        override fun update(c: Ctx, out: SteerIntent) {
            stay(c, out)
            if (elapsed > hold && threat == null && !startledRecently) finished = true
        }
    }

    /** By day: creep back to its spot and lie with its head under the ledge, the tail out on the sand. */
    inner class Hide : Behavior("hide", "Half hidden under the rock") {
        private var stage = 0
        override val label: String get() = if (stage < 2) "Creeping back under the rock" else "Half hidden under the rock"
        override fun score(c: Ctx) = if (c.p.night) 0.2 else 0.5
        override fun enter(c: Ctx) { stage = 0 }
        override fun update(c: Ctx, out: SteerIntent) {
            val r = restSpot(c, false)
            // in head first: first to a point a body length out from the spot, then straight in
            val staging = onSand(c, r.pos - r.heading * (0.7 * c.L))
            when (stage) {
                0 -> if (o.pos.distanceTo(r.pos) < 0.5 * c.L) stage = 1 else if (crawl(c, out, staging, HOME_CM_S)) stage = 1
                1 -> if (crawl(c, out, r.pos, HOME_CM_S)) stage = 2
                else -> { stay(c, out); o.sleeping = true }
            }
        }
    }

    /**
     * Night feeding: a slow meander over the open sand, the tentacles mopping the film into its mouth (and any food
     * that has settled in front of it), sifted sand left behind.
     */
    inner class SandFeed : Behavior("sift", "Mopping the sand with its tentacles") {
        private var goal = Vec3.ZERO
        override fun score(c: Ctx): Double = when {
            c.p.night -> 0.6
            // a hungry cucumber also feeds by day in a quiet tank
            c.o.hunger > 0.6 -> 0.55
            else -> 0.0
        }
        override fun enter(c: Ctx) { goal = sandGoal(c) }
        override fun update(c: Ctx, out: SteerIntent) {
            if (elapsed > 1200.0 && c.rng.chance(c.dt / 600.0) || o.pos.distanceTo(goal) < 1.0) goal = sandGoal(c)
            crawl(c, out, goal, FEED_CM_S * (0.6 + 0.4 * tentacles))
            if (tentacles < 0.5) return
            siftAcc += c.dt
            if (siftAcc >= 1.0) {
                val head = o.pos + o.heading * (0.03 * c.L)
                val took = c.sim.sand.sift(head.x, head.z, 0.06 * c.L, SIFT_SHARE * siftAcc)
                sifted += took
                o.hunger = max(0.0, o.hunger - SIFT_GAIN * took)
                // settled food in reach of the tentacles
                c.food.nearest(head, 0.1 * c.L) { it.settled && o.species.dietMatch(it.kind) > 0 }?.let { c.bite(it) }
                siftAcc = 0.0
            }
        }

        /** Somewhere on open sand 10-40 cm off that the body can reach in a straight creep. */
        private fun sandGoal(c: Ctx): Vec3 {
            var best = o.pos
            var bestD = -1.0
            for (i in 0 until 14) {
                val d = Vec3.rotateAbout(o.heading.horizontal().normalized(Vec3.X), Vec3.Y, c.rng.signed() * PI * (if (i < 8) 0.5 else 1.0))
                val q = onSand(c, c.rock.clampToWater(o.pos + d * c.rng.range(10.0, 40.0), halfW + 3))
                if (!clear(c, q) || !c.rock.sdf.segmentClear(o.pos + Vec3.Y * (0.55 * bodyH), q + Vec3.Y * (0.55 * bodyH), halfW)) continue
                val dd = abs(q.distanceTo(o.pos))
                if (dd > bestD) { bestD = dd; best = q }
                if (i >= 3) break
            }
            return best
        }
    }
}
