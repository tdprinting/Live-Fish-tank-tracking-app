package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.geom.smoothstep
import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.Organism

// =====================================================================================
// Green serpent star (Ophiarachna incrassata, research 45 §3). The black brittle star's life on a bigger, bolder animal:
// under the rock by day with an arm or two out, out over the rock at night and racing to food at any hour. It differs
// in one way that matters to a keeper: a hungry one catches small fish resting near the rock at night, loops an arm
// round it and passes it to its mouth (research 45 §3: "eats fish and shrimp after lights-out"; "reef-safe only when
// food is short"). It finds its own hide at the foot of the rock (the tank's BRITTLE_HOME stays the black star's).
// =====================================================================================
class SerpentStarBrain(c: Ctx) : BrittleStarBrain(c, findHide(c), { b -> listOf(FishGrab(b as SerpentStarBrain)) }) {
    companion object {
        /** Hunger above which it tries for a fish (fed stars leave fish alone; research 45 §3) [E]. */
        const val HUNT_HUNGER = 0.55
        /** Longest fish it can take, as a share of its span (a 35 cm star: ~6 cm, a firefish or a cardinal) [E]. */
        const val MAX_PREY_FRAC = 0.18
        /** Share of grabs that hold; the rest wake the fish, which gets away [E]. */
        const val GRAB_HOLDS = 0.35
        /** Rest after a try, s (research 45 §0 design rate ~0.02/h when hungry and a fish is in reach) [E]. */
        const val RETRY_MIN_S = 3600.0
        const val RETRY_MAX_S = 3 * 3600.0
        /** Seconds the arm takes to pass a caught fish to the mouth [E]. */
        const val CARRY_S = 30.0

        /**
         * Its hide: the most covered spot on the sand at the foot of the rock, a few cm out from it, away from the black
         * star's crevice and other stars' hides. A deterministic grid over the tank floor (no rng).
         */
        fun findHide(c: Ctx): Vec3 {
            val sand = c.rock.sandTopCm
            val avoid = ArrayList<Vec3>()
            avoid.add(c.rock.anchors.pos("BRITTLE_HOME", Vec3(36.0, 5.5, 10.4)))
            for (x in c.sim.organisms) if (x !== c.o && x.alive && x.brainKind == BrainKind.BRITTLE_STAR) avoid.add(x.homePos)
            var best: Vec3? = null
            var bestS = -1e9
            var x = 8.0
            while (x < c.rock.lengthCm - 8) {
                var z = 6.0
                while (z < c.rock.widthCm - 6) {
                    val p = Vec3(x, c.sim.sand.surfaceY(x, z) + 1.0, z)
                    val d = c.rock.sdf.dist(p)
                    if (d in 0.8..4.0 && avoid.none { it.distanceTo(p) < 15.0 }) {
                        val s = c.rock.sdf.cover(p) - 0.05 * d - 0.002 * kotlin.math.abs(p.x - c.rock.lengthCm * 0.5)
                        if (s > bestS) { bestS = s; best = p }
                    }
                    z += 3.0
                }
                x += 3.0
            }
            return best ?: Vec3(c.rock.lengthCm * 0.6, sand + 1.0, c.rock.widthCm * 0.3)
        }
    }

    /** Fish caught and eaten, and grabs that missed (tests, captions). */
    var fishEaten = 0; internal set
    var grabs = 0; internal set
    var nextTry = 0.0; internal set

    /** Fish it can take: small, asleep near the rock or sand, in reach of an arm from where its disc is. */
    internal fun preyNear(c: Ctx): Organism? {
        val reach = ARM_REACH * c.L
        return c.p.neighbors(c.o.pos, reach).firstOrNull {
            it !== c.o && it.alive && it.isFish && it.sleeping && it.visible && it.heldBy == null &&
                it.lengthCm <= MAX_PREY_FRAC * c.L && it.pos.distanceTo(c.o.pos) < reach && c.rock.envDist(it.pos) < 3.0
        }
    }
}

/**
 * A grab at a sleeping fish: the disc stays put, the nearest arm whips out and loops round it (faster than the slow
 * reach for food), and either holds it and passes it to the mouth like food, or the fish wakes and bolts.
 */
class FishGrab(private val b: SerpentStarBrain) : Behavior("fishgrab", "Grabbing a sleeping fish") {
    private var prey: Organism? = null
    private var arm = -1
    private var held = false
    private var start = Vec3.ZERO
    override val committed: Boolean get() = arm >= 0
    override fun score(c: Ctx): Double {
        if (arm >= 0) return 0.85   // mid-grab: keep the fish it has
        if (!c.p.night || c.o.hunger < SerpentStarBrain.HUNT_HUNGER || c.t < c.o.satietyUntil || c.t < b.nextTry) return 0.0
        prey = b.preyNear(c)
        return if (prey != null) 0.85 else 0.0
    }
    override fun enter(c: Ctx) { arm = -1; held = false }
    override fun update(c: Ctx, out: SteerIntent) {
        val f = prey
        if (f == null || !f.alive) { finished = true; return }
        out.rest(Rest(c.o.pos, c.o.heading, c.o.up, c.o.posture), 1.0)
        c.o.extension = 1.0
        c.o.focusId = f.id
        val arms = b.arms
        if (arm < 0) { arm = arms.nearestArm(c.o, f.pos - c.o.pos); start = f.pos; b.grabs++ }
        arms.feedArm = arm; arms.feedAt = start; arms.outDir = start - c.o.pos
        val strike = 1.2
        if (elapsed < strike) { arms.feedLoop = smoothstep(0.2, strike, elapsed); return }
        arms.feedLoop = 1.0
        if (!held) {
            if (c.rng.chance(SerpentStarBrain.GRAB_HOLDS)) { held = true; f.heldBy = c.o }
            else {
                // it woke: off it goes, and the star waits a while before trying again
                c.sim.agentOf(f)?.let { a -> a.brain.onAttacked(a.ctx, c.o.pos, 0.7, c.o) }
                b.nextTry = c.t + c.rng.range(SerpentStarBrain.RETRY_MIN_S, SerpentStarBrain.RETRY_MAX_S)
                arm = -1; finished = true
                return
            }
        }
        // held in the coiled arm and passed along it to the mouth under the disc
        val prog = ((elapsed - strike) / SerpentStarBrain.CARRY_S).coerceIn(0.0, 1.0)
        f.pos = arms.armPoint(c.o, arm, BrittleStarBrain.CARRY_FROM * (1 - prog)); f.vel = Vec3.ZERO
        if (prog >= 1.0) {
            f.heldBy = null
            c.sim.eaten(c.o, f)
            b.fishEaten++
            b.nextTry = c.t + SerpentStarBrain.RETRY_MAX_S
            arm = -1; finished = true
        }
    }
    override fun exit(c: Ctx) {
        prey?.let { if (it.heldBy === c.o) it.heldBy = null }
        b.arms.feedArm = -1; b.arms.feedLoop = 0.0
        arm = -1
    }
}
