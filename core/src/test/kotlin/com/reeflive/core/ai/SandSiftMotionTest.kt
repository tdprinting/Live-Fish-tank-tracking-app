package com.reeflive.core.ai

import com.reeflive.core.geom.BodySpline
import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.LivestockSeed
import com.reeflive.core.sim.TestTank
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.acos
import kotlin.math.max

/**
 * Sandy's sifting as it looks (Tee 2026-10-20: "puts his head in the sand and then glitches out"): through every
 * scoop, chew and puff the body moves smoothly, never folds back on itself, stays out of the sand behind the head, and
 * the snout really bites into the bed with the mouth open.
 */
class SandSiftMotionTest {
    /**
     * Worst per-tick move, change of move (a pop), bend between neighbouring segments and depth under the sand of the
     * body behind the head.
     */
    private class Worst(var jump: Double = 0.0, var jerk: Double = 0.0, var fold: Double = 0.0, var under: Double = 0.0) {
        override fun toString() = "jump %.2f jerk %.2f cm fold %.1f° under the sand %.2f cm".format(jump, jerk, fold, under)
    }

    @Test fun sandySiftsSmoothlyWithItsMouthInTheSand() = check(3L)
    @Test fun andInAnotherTank() = check(5L)

    private fun check(seed: Long) {
        val sim = TestTank.sim(emptyList(), seed = seed, startHour = 11.0)
        sim.species.ensure("valenciennea_puellaris")
        val g = sim.addFromSeed(LivestockSeed("valenciennea_puellaris", "Sandy", 12.0, id = "dg1"))
        val b = sim.agentOf(g)!!.brain as DiamondGobyBrain
        val pts = Array(BodySpline.POINTS) { Vec3.ZERO }
        val prev = Array(BodySpline.POINTS) { Vec3.ZERO }
        val prev2 = Array(BodySpline.POINTS) { Vec3.ZERO }
        var have = 0
        var siftTicks = 0
        val fd = Worst() // while it feeds on the spot: strike, winnow, expel
        val tr = Worst() // while it glides between patches (the body follows the head's track, round rock corners too)
        var deepestSnout = 0.0 // snout under the sand while biting (cm)
        var mouthInSand = 0.0
        sim.advance(240.0, behaviorDt = 0.05) {
            val sifting = b.utility.current?.name == "sift"
            g.spline.sampleStations(pts)
            // (from 5 s in: the first ticks place the new fish)
            if (sifting && have >= 100) {
                siftTicks++
                val travel = b.utility.currentLabel == "Moving to a new patch of sand"
                val m = if (travel) tr else fd
                for (i in 2 until BodySpline.POINTS) {
                    m.jump = max(m.jump, pts[i].distanceTo(prev[i]))
                    m.jerk = max(m.jerk, (pts[i] - prev[i] - (prev[i] - prev2[i])).length())
                }
                for (i in 1 until BodySpline.POINTS - 1) {
                    val a = (pts[i - 1] - pts[i]).normalized(Vec3.X); val c = (pts[i] - pts[i + 1]).normalized(Vec3.X)
                    m.fold = max(m.fold, Math.toDegrees(acos((a dot c).coerceIn(-1.0, 1.0))))
                }
                for (i in 3 until BodySpline.POINTS) m.under = max(m.under, sim.sand.surfaceY(pts[i].x, pts[i].z) - pts[i].y)
                val under = sim.sand.surfaceY(g.pos.x, g.pos.z) - g.pos.y
                deepestSnout = max(deepestSnout, under)
                if (under > 0.15) mouthInSand = max(mouthInSand, g.mouth)
            }
            for (i in pts.indices) { prev2[i] = prev[i]; prev[i] = pts[i] }
            have++
        }
        println("seed $seed sift ticks $siftTicks mouthfuls ${b.mouthfuls}  feeding $fd  travel $tr  " +
            "snout ${"%.2f".format(deepestSnout)} cm mouth ${"%.2f".format(mouthInSand)}")
        assertTrue("it sifted (${b.mouthfuls} mouthfuls)", b.mouthfuls >= 8)
        assertTrue("snout bites into the bed (${deepestSnout} cm)", deepestSnout in 0.15..0.8)
        assertTrue("mouth wide open as it bites ($mouthInSand)", mouthInSand > 0.8)
        // feeding: smooth (the strike tips the body 2-3 cm, so a station may move ~1 cm a tick, but never pops or kinks)
        assertTrue("feeding: no body station moves more than 1.5 cm a tick ($fd)", fd.jump < 1.5)
        assertTrue("feeding: no station pops, its move changes by under 0.8 cm from tick to tick ($fd)", fd.jerk < 0.8)
        assertTrue("feeding: the body never kinks, under 40° between neighbouring segments ($fd)", fd.fold < 40.0)
        // gliding: no folding back on itself (it used to fold 166° and jump 3 cm a tick)
        assertTrue("gliding: no station jumps more than 2 cm a tick ($tr)", tr.jump < 2.0)
        assertTrue("gliding: no fold over 110° ($tr)", tr.fold < 110.0)
        assertTrue("feeding: the body behind the head stays out of the sand ($fd)", fd.under < 0.1)
        assertTrue("gliding: nor more than skims it ($tr)", tr.under < 0.5)
    }
}
