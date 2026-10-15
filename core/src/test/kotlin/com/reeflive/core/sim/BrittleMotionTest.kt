package com.reeflive.core.sim

import com.reeflive.core.ai.BrittleStarBrain
import com.reeflive.core.geom.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.pow

/**
 * Black brittle star arm contact and gait (SIMULATION §19.2, §19.7; Astley 2012): the disc is pulled along only in
 * the arms' power strokes, gripping tips hold their place on the substrate, the drawn arms lie on rock, sand and glass
 * without passing into them, and the star hides under its rock by day with a few tips out and is home before the
 * lights come on.
 */
class BrittleMotionTest {
    private fun Simulation.star() = organisms.first { it.brainKind == BrainKind.BRITTLE_STAR }
    private fun Simulation.brain() = agentOf(star())!!.brain as BrittleStarBrain
    private fun Simulation.arms() = star().limbRig as BrittleArms

    /** Half the drawn arm's thickness at [s] (cm): `tools/blender/brittle_geom.py` radius × squash. */
    private fun halfThick(s: Double, L: Double) = 0.0105 * 0.62 * (1 - 0.82 * s.pow(1.25)) * L

    private class Contact {
        var samples = 0L
        var inside = 0L
        var worst = 0.0
        var worstAt = ""
        /** Arm points of lying, outstretched arms, and those standing more than 0.6 cm off the surface. */
        var lying = 0L
        var hovering = 0L
        val share get() = inside.toDouble() / max(1L, samples)
    }

    /** Every drawn arm point (s = 0.1 … 1) against the surface: the arm's skin inside it by more than 0.1 cm is a clip. */
    private fun checkArms(sim: Simulation, c: Contact) {
        val o = sim.star()
        val arms = o.limbRig as BrittleArms
        val L = o.lengthCm
        for (k in 0 until BrittleArms.ARMS) {
            for (i in 1..10) {
                val s = i / 10.0
                if (s > arms.armLen[k]) break
                val p = arms.armPoint(o, k, s)
                val d = sim.crawlSurface.dist(p) - halfThick(s, L)
                c.samples++
                if (d < -0.1) {
                    c.inside++
                    if (-d > c.worst) { c.worst = -d; c.worstAt = "arm $k s=$s at $p (${o.behaviorName})" }
                }
                if (arms.liftOf(k) <= 0.0 && arms.reachOf(k) > 0.9 && s >= 0.5 && !arms.gripping(k)) { c.lying++; if (d > 0.6) c.hovering++ }
            }
        }
    }

    /** The spot on open sand (at least [awayCm] from [from]) with the most room around it and along +x. */
    private fun openSand(s: Simulation, from: Vec3 = Vec3.ZERO, awayCm: Double = 0.0): Vec3 {
        val y = s.rock.sandTopCm + 0.4
        return (15..105 step 2).flatMap { x -> (6..30 step 1).map { z -> Vec3(x.toDouble(), y, z.toDouble()) } }
            .filter { it.horizontal().distanceTo(from.horizontal()) >= awayCm }.maxBy { p ->
            listOf(p, p + Vec3(6.0, 0.0, 0.0), p + Vec3(12.0, 0.0, 0.0)).minOf { s.crawlSurface.dist(it + Vec3(0.0, 2.0, 0.0)) }
        }
    }

    @Test
    fun discIsPulledOnlyInPowerStrokesWhileTheTipsHold() {
        val s = TestTank.sim(listOf(TestTank.seed("brittle")), seed = 5L, startHour = 23.0)
        s.advance(5.0, behaviorDt = 0.05)
        val o = s.star(); val arms = s.arms()
        val start = openSand(s)
        o.placeAt(start, Vec3.X)
        o.up = Vec3.Y
        s.food.spawn(FoodKind.PELLET, 0.02, Vec3(start.x + 14.0, s.rock.sandTopCm + 0.3, start.z), s.simTimeSec - 600)
        var stance = 0.0; var swing = 0.0
        var shoves = 0
        var last = o.pos
        val lastTip = arrayOfNulls<Vec3>(BrittleArms.ARMS)
        var tipMax = 0.0; var tipSum = 0.0; var holdTicks = 0
        val grips0 = arms.grips
        s.advance(40.0, behaviorDt = 0.05) {
            o.hunger = maxOf(o.hunger, 0.7)
            val d = o.pos.distanceTo(last); last = o.pos
            // (a shove of more than 0.5 cm in a tick is the surface walk re-seating the disc off a rock edge, not rowing)
            if (arms.moving > 0.9) {
                if ((arms.rowPhase % 1.0) < BrittleArms.DUTY + 0.02) stance += d else if (d > 0.5) shoves++ else swing += d
            }
            for (k in 0 until BrittleArms.ARMS) {
                if (!arms.gripping(k)) { lastTip[k] = null; continue }
                val t = arms.tipContact(k)
                lastTip[k]?.let { val j = it.distanceTo(t); tipMax = max(tipMax, j); tipSum += j; holdTicks++ }
                lastTip[k] = t
            }
        }
        assertTrue("rowed toward the food (${"%.1f".format(stance)} cm in power strokes)", stance > 5.0)
        assertTrue("disc re-seated by a shove only now and then ($shoves)", shoves <= 3)
        assertTrue("disc still between strokes: ${"%.2f".format(swing)} cm in the recovery vs ${"%.1f".format(stance)} cm", swing < 0.03 * stance)
        assertTrue("arms took holds (${arms.grips - grips0})", arms.grips - grips0 >= 10 && holdTicks > 100)
        // (the hold is let go once the arm can no longer keep its tip within 2 × GRIP_TOL of it)
        assertTrue("a holding tip stays put (mean ${"%.3f".format(tipSum / holdTicks)} cm per tick)", tipSum / holdTicks < 0.02)
        assertTrue("a holding tip never skids far (max ${"%.3f".format(tipMax)} cm per tick)", tipMax < 2 * BrittleArms.GRIP_TOL * o.lengthCm)
    }

    /**
     * Over many paths (each seed roams its own way over rock, ledges, overhangs and sand): lying arms drape over rock
     * edges onto whatever is below instead of standing out in the water, no arm point passes deep into rock, sand or
     * glass (a long step straddling thin rock, a disc pulled through the sand), holding tips stay put and the disc rests
     * on the surface. Every seed is checked and all the failures are reported together.
     */
    @Test
    fun armsLieOnRockAndSandWithoutPassingIntoThem() {
        val fails = mutableListOf<String>()
        val seen = StringBuilder()
        for (seed in 1L..8L) {
            val s = TestTank.sim(listOf(TestTank.seed("brittle")), seed = seed, startHour = 23.0)
            val c = Contact()
            val o = s.star(); val arms = s.arms()
            val lastTip = arrayOfNulls<Vec3>(BrittleArms.ARMS)
            var tipSum = 0.0; var tipTicks = 0L
            var rock = 0L
            s.advance(900.0, behaviorDt = 0.05) {
                checkArms(s, c)
                if (o.up.y < 0.7) rock++
                for (k in 0 until BrittleArms.ARMS) {
                    if (!arms.gripping(k)) { lastTip[k] = null; continue }
                    val t = arms.tipContact(k)
                    lastTip[k]?.let { tipSum += it.distanceTo(t); tipTicks++ }
                    lastTip[k] = t
                }
            }
            val hover = c.hovering.toDouble() / max(1L, c.lying)
            val tip = tipSum / max(1L, tipTicks)
            seen.append("\n  seed $seed: hovering ${"%.2f".format(100 * hover)} %, inside ${"%.2f".format(100 * c.share)} %, " +
                "deepest ${"%.2f".format(c.worst)} cm, tip ${"%.3f".format(tip)} cm/tick, disc ${"%.2f".format(arms.discUnderCm)} cm")
            fun check(ok: Boolean, what: String) { if (!ok) fails += "seed $seed: $what" }
            check(rock > 1000, "roamed over the rock too ($rock tilted ticks)")
            check(c.share < 0.02, "arm skin inside rock/sand/glass: ${"%.2f".format(100 * c.share)} % of points")
            check(c.worst < 1.5, "deepest clip ${"%.2f".format(c.worst)} cm: ${c.worstAt}")
            check(c.hovering < 0.02 * c.lying, "lying arms rest on the surface (${c.hovering} of ${c.lying} points > 0.6 cm off it)")
            check(tip < 0.15, "holding tips stay on their holds over rock (mean ${"%.3f".format(tip)} cm per tick)")
            check(arms.discUnderCm > -0.1, "the disc rests on the surface (${"%.2f".format(arms.discUnderCm)} cm)")
        }
        assertTrue(fails.joinToString("\n") + seen, fails.isEmpty())
    }

    @Test
    fun hidesUnderItsRockByDayWithAFewTipsSweeping() {
        val s = TestTank.sim(listOf(TestTank.seed("brittle")), seed = 3L, startHour = 12.0)
        s.advance(240.0, behaviorDt = 0.05)
        val o = s.star(); val arms = s.arms()
        assertEquals("hiding by day: ${o.behaviorName}", 3, o.posture)
        val out = (0 until BrittleArms.ARMS).filter { arms.reachOf(it) > 0.6 }
        assertTrue("1-3 tips out, saw ${out.size}", out.size in 1..3)
        val c = Contact()
        val disc = o.pos
        val lo = DoubleArray(BrittleArms.ARMS) { 1e9 }; val hi = DoubleArray(BrittleArms.ARMS) { -1e9 }
        var discTop = 1e9; var discUnder = 1e9
        s.advance(60.0, behaviorDt = 0.05) {
            checkArms(s, c)
            discTop = minOf(discTop, arms.discTopClearCm); discUnder = minOf(discUnder, arms.discUnderCm)
            for (k in out) {
                // sideways across the arm's direction out of the hide
                val side = arms.tipContact(k) - o.pos
                val a = kotlin.math.atan2(side.z, side.x)
                lo[k] = minOf(lo[k], a); hi[k] = maxOf(hi[k], a)
            }
        }
        assertTrue("the disc stays in the hide (${"%.2f".format(o.pos.distanceTo(disc))} cm)", o.pos.distanceTo(disc) < 1.0)
        assertTrue("disc wedged under the rock without passing into it (top ${"%.2f".format(discTop)}, under ${"%.2f".format(discUnder)} cm)", discTop > -0.15 && discUnder > -0.1)
        val sweep = out.maxOf { hi[it] - lo[it] }
        assertTrue("out tips trawl from side to side (${"%.2f".format(sweep)} rad)", sweep > 0.08)
        assertTrue("arms in the hide do not pass into the rock: ${"%.2f".format(100 * c.share)} %, deepest ${"%.2f".format(c.worst)} cm ${c.worstAt}", c.share < 0.02 && c.worst < 1.0)
    }

    @Test
    fun isBackUnderItsRockBeforeTheLightsComeOn() {
        // out in the small hours: it roams, then heads home ahead of the lamp (on at ≈ 08:15)
        val s = TestTank.sim(listOf(TestTank.seed("brittle")), seed = 6L, startHour = 5.5)
        val o = s.star(); val b = s.brain()
        var roamed = 0.0
        var atLightsOn = -1.0
        s.advance(3.0 * 3600, behaviorDt = 0.1) {
            if (s.simTimeSec < 7.0 * 3600) roamed = max(roamed, o.pos.distanceTo(b.home))
            if (atLightsOn < 0 && s.lights.intensity(s.simTimeSec) >= LightSchedule.LIGHTS_ON) atLightsOn = o.pos.distanceTo(b.home)
        }
        assertTrue("out roaming in the small hours (${"%.1f".format(roamed)} cm from home)", roamed > 10.0)
        assertTrue("the lights came on during the run", atLightsOn >= 0)
        assertTrue("home when the lights came on (${"%.1f".format(atLightsOn)} cm away)", atLightsOn < 3.0)
        assertEquals("settled in its hide: ${o.behaviorName}", 3, o.posture)
    }

    @Test
    fun armsGoOverOrAroundAnAnimalInTheirWay() {
        val s = TestTank.sim(listOf(TestTank.seed("brittle"), TestTank.seed("crab")), seed = 5L, startHour = 23.0)
        s.advance(5.0, behaviorDt = 0.05)
        val o = s.star(); val arms = s.arms()
        val crab = s.organisms.first { it.brainKind == BrainKind.SALLY_CRAB }
        val start = openSand(s)
        o.placeAt(start, Vec3.X)
        o.up = Vec3.Y
        s.advance(0.1, behaviorDt = 0.05)
        // the crab sits across the middle of arm 0
        val k = 0
        val mid = arms.armPoint(o, k, 0.55)
        val r = BrittleArms.OTHER_R * crab.collisionLength
        crab.placeAt(Vec3(mid.x, s.rock.sandTopCm + 0.5 * r, mid.z), Vec3.Z)
        // (the arm finds its way on the next contact; then a startled crab soon scuttles off)
        s.advance(0.1, behaviorDt = 0.05)
        var minGap = 1e9
        s.advance(0.3, behaviorDt = 0.05) {
            for (i in 1..20) minGap = minOf(minGap, arms.armPoint(o, k, i / 20.0).distanceTo(crab.pos) - r)
        }
        assertTrue("arm 0 lies over or around the crab, not through it (closest ${"%.2f".format(minGap)} cm from its shell)", minGap > -0.35)
    }
}
