package com.reeflive.core.sim

import com.reeflive.core.geom.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The nori sheet as a flow-driven cloth ([NoriCloth]): stable in any current, streams the way the water goes,
 * flutters, and every bite, tear and loose piece conserves the sheet's material. Then in the tank: grazers bite from
 * the downstream side and torn pieces drift off as food.
 */
class NoriClothTest {
    /** An open tank with one small rock far from the clip. */
    private val rock = RockData.ofSpheres(listOf(Vec3(100.0, 10.0, 15.0) to 5.0))
    private val clip = Vec3(2.0, 35.0, 18.0)

    private fun run(c: NoriCloth, seconds: Double, dt: Double = 0.05, t0: Double = 0.0, flow: (Vec3, Double) -> Vec3): Double {
        var t = t0
        while (t < t0 + seconds - 1e-9) { t += dt; c.step(dt, t) { p -> flow(p, t) } }
        return t
    }

    private fun finite(c: NoriCloth) = (0 until c.nodeCount).all { c.node(it).isFinite() }

    private fun freeCentroid(c: NoriCloth): Vec3 {
        var acc = Vec3.ZERO; var n = 0
        for (k in 0 until c.nodeCount) if (!c.pinned[k]) { acc += c.node(k); n++ }
        return acc / n.toDouble()
    }

    @Test
    fun staysStableInStillWaterAndStrongFlow() {
        for (speed in listOf(0.0, 8.0, 25.0, 60.0, 120.0)) {
            val c = NoriCloth(clip, rock)
            // an awkward mix: a strong pulsing jet with a swirl and changing direction
            run(c, 120.0) { _, t -> Vec3(speed * (0.6 + 0.4 * kotlin.math.sin(t)), 0.3 * speed * kotlin.math.cos(t * 0.7), speed * 0.8) }
            assertTrue("finite at $speed cm/s", finite(c))
            val diag = sqrt(NoriCloth.WIDTH_CM * NoriCloth.WIDTH_CM + NoriCloth.LENGTH_CM * NoriCloth.LENGTH_CM)
            for (k in 0 until c.nodeCount) assertTrue("node $k within reach of the clip at $speed cm/s", c.node(k).distanceTo(clip) < diag + 2.0)
            assertTrue("no runaway stretch at $speed cm/s: ${c.maxStrain()}", c.maxStrain() < NoriCloth.MAX_STRETCH + 0.05)
            assertEquals("nothing tears without bites", 1.0, c.remaining, 1e-12)
        }
    }

    @Test
    fun hangsInStillWaterAndStreamsDownstream() {
        val still = NoriCloth(clip, rock)
        run(still, 30.0) { _, _ -> Vec3.ZERO }
        val hang = freeCentroid(still) - clip
        assertTrue("still water: the sheet hangs below the clip ($hang)", hang.y < -3.0)

        for (dir in listOf(Vec3(0.0, 0.0, 1.0), Vec3(0.0, 0.0, -1.0), Vec3(1.0, 0.0, 0.0), Vec3(0.0, 1.0, 0.0))) {
            val c = NoriCloth(clip, rock)
            run(c, 30.0) { _, _ -> dir * 20.0 }
            val off = freeCentroid(c) - clip
            val along = off dot dir
            println("flow $dir: offset $off, along %.2f".format(along))
            assertTrue("the sheet streams with the flow $dir: $off", along > 0.55 * off.length() && along > 3.0)
        }
    }

    @Test
    fun fluttersInACurrentAndFollowsAPumpSwitchingOff() {
        val c = NoriCloth(clip, rock)
        var on = true
        var t = run(c, 20.0) { _, _ -> Vec3(0.0, 0.0, 20.0) }
        // flutter: the free corner keeps moving in a steady current
        val tip = c.nodeCount - 1
        val zs = ArrayList<Double>()
        repeat(80) { t = run(c, 0.05, t0 = t) { _, _ -> Vec3(0.0, 0.0, 20.0) }; zs += c.node(tip).x + c.node(tip).y }
        val mean = zs.average()
        val sd = sqrt(zs.sumOf { (it - mean) * (it - mean) } / zs.size)
        assertTrue("the free end flutters (sd %.3f cm)".format(sd), sd > 0.05)
        // pump off: it sinks back to hanging
        on = false
        run(c, 40.0, t0 = t) { _, _ -> if (on) Vec3(0.0, 0.0, 20.0) else Vec3.ZERO }
        assertTrue("pump off: it hangs again", (freeCentroid(c) - clip).y < -3.0)
    }

    @Test
    fun bitesTearsAndLoosePiecesConserveMaterial() {
        val c = NoriCloth(clip, rock)
        run(c, 5.0) { _, _ -> Vec3(0.0, 0.0, 15.0) }
        var bitArea = 0.0
        var t = 5.0
        var bites = 0
        while (c.remaining > 0.0 && bites < 2000) {
            val at = c.centroid() + Vec3(0.0, -6.0, 6.0)
            c.bite(at, 0.12, Vec3(0.0, 0.0, 8.0))
            bites++
            t = run(c, 0.2, t0 = t) { _, _ -> Vec3(0.0, 0.0, 15.0) }
            bitArea += c.bits.sumOf { it.area }
            c.bits.clear()
            assertEquals("material is conserved after bite $bites", c.total, c.remainingArea + c.eaten + c.torn, 1e-9)
            assertTrue(finite(c))
        }
        assertEquals("all of it is gone", 0.0, c.remaining, 1e-12)
        assertTrue("some was torn off as pieces, not just eaten: torn ${c.torn}", c.torn > 0.5)
        assertEquals("every torn piece left as a bit", c.torn, bitArea, 1e-9)
        assertNull(c.bite(clip, 0.1))
    }

    @Test
    fun cuttingTheSheetAcrossDropsTheLowerPart() {
        val c = NoriCloth(clip, rock)
        // eat a whole row of cells across the middle: everything below breaks away
        val row = 2
        for (i in 0 until c.cellCols) c.tear(row * c.cellCols + i)
        c.step(0.05, 0.05) { Vec3.ZERO }
        val below = (row + 1 until c.cellRows).sumOf { j -> (0 until c.cellCols).count { i -> c.present(j * c.cellCols + i) } }
        assertEquals("nothing hangs below the cut", 0, below)
        assertTrue("the loose part left as bits", c.bits.size >= (c.cellRows - row - 1) * c.cellCols)
        assertTrue((0 until row * c.cellCols).all { c.present(it) })
    }

    @Test
    fun aGrazerBitesFromDownstreamAndTornPiecesBecomeFood() {
        val s = TestTank.sim(DefaultStock.all, seed = 5L, startHour = 10.0)
        s.advance(5.0)
        s.organisms.filter { it.isFish }.forEach { it.hunger = 0.8 }
        s.enqueue(Command.Feed(FoodKind.NORI, 1.0))
        var sawBits = false
        var t = 0.0
        while (t < 900.0) {
            s.advance(1.0, behaviorDt = 0.05); t += 1.0
            if (s.food.particles.any { it.kind == FoodKind.NORI && !it.eaten }) sawBits = true
            val n = s.food.nori ?: break
            assertTrue("the cloth stays finite", (0 until n.cloth.nodeCount).all { n.cloth.node(it).isFinite() })
            assertEquals(n.cloth.total, n.cloth.remainingArea + n.cloth.eaten + n.cloth.torn, 1e-9)
        }
        val sheet = s.food.nori
        val bites = sheet?.bites ?: emptyList()
        println("nori: remaining=${sheet?.remaining} bites=${sheet?.biteCount} sawBits=$sawBits")
        assertTrue("grazers bit the sheet: ${sheet?.biteCount}", sheet == null || sheet.biteCount > 5)
        if (bites.isNotEmpty()) {
            val downstream = bites.count { b -> ((b.fishPos - b.at) dot b.downstream) > 0.0 }
            bites.take(8).forEach { b -> println("bite fish ${b.fishPos} at ${b.at} down ${b.downstream}") }
            assertTrue("bites are taken from the downstream side: $downstream / ${bites.size}", downstream * 4 >= bites.size * 3)
        }
        assertTrue("torn pieces drift off as nori food", sawBits)
    }

    @Test
    fun wavemakerPulseAndPumpReachTheSheetThroughTheFlowField() {
        // an open tank (no rock near the clip) with one powerhead blowing along the left glass past the clip
        val s = Simulation(TankConfig(seed = 2L), TestTank.catalog, rock, 2L, 10 * 3600.0, WaterState(), emptyList())
        s.flow.jets = listOf(com.reeflive.core.geom.Powerhead("PH-T", Vec3(4.0, 31.0, 2.0), Vec3(0.0, 0.0, 1.0)))
        s.enqueue(Command.Feed(FoodKind.NORI, 1.0))
        s.advance(10.0)
        val zs = ArrayList<Vec3>()
        repeat(40) { s.advance(1.0); zs += s.food.nori!!.cloth.centroid() }
        val spread = zs.maxOf { a -> zs.maxOf { b -> a.distanceTo(b) } }
        val on = zs.last()
        s.enqueue(Command.Powerheads(false))
        s.advance(40.0)
        val off = s.food.nori!!.cloth.centroid()
        val clipY = s.food.nori!!.pos.y
        println("centroid with pumps $on (spread %.2f over the wavemaker period), without $off".format(spread))
        assertTrue("the sheet works in the pulsing jet: spread $spread", spread > 0.3 && on.z > s.food.nori!!.pos.z + 2.0)
        assertTrue("pumps off: it sinks back to hanging (${on.distanceTo(off)} cm)", on.distanceTo(off) > 1.0 && off.y < clipY - 3.0)
    }
}
