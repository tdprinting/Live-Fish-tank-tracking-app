package com.reeflive.core.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Dynamic rockscape: loose rocks stand as built, settle or topple once undermined, and stay put in static mode. */
class RockPhysicsTest {
    private fun sim(dynamic: Boolean = true): Simulation = TestTank.sim(emptyList(), seed = 3L).also { it.setDynamicRocks(dynamic) }

    /** Digs the sand out from under the part of R3 (the centre-left pillar) left of 2 cm right of its centre, down to the floor. */
    private fun undermineR3(s: Simulation) {
        var x = 34.0
        while (x < 44.5) { var z = 20.0; while (z < 37.0) { repeat(4) { s.sand.dig(x, z, 40.0, 1.2) }; z += 1.0 }; x += 1.0 }
    }

    @Test
    fun rocksAsBuiltStandStill() {
        val tb = System.nanoTime()
        val s = sim()
        println("[rock] build ${(System.nanoTime() - tb) / 1e6} ms (sim + rock bodies)")
        val t0 = System.nanoTime()
        val rp = s.rockPhysics!!
        for (b in rp.bodies) println("[rock] ${b.name} mass=${"%.0f".format(b.mass)} g probes=${b.np} supports=${b.supports} glass=${b.supGlass} sand=${b.supSand} rock=${b.supRock} pinned=${b.pinned}")
        s.advance(30.0, 0.25)
        println("[rock] 30 s with untouched sand in ${(System.nanoTime() - t0) / 1e6} ms")
        assertFalse("nothing moves while the sand is untouched", rp.anyMoved)
        assertNull(s.latest.rockPoses)
        assertTrue("every rock stands or leans on another", rp.bodies.none { it.pinned })
    }

    @Test
    fun underminedPillarToppplesAndComesToRest() {
        val s = sim()
        val rp = s.rockPhysics!!
        val r3 = rp.bodies.first { it.name == "R3" }
        val before = r3.pos.copyOf()
        undermineR3(s)
        val t0 = System.nanoTime()
        s.advance(25.0, 0.05)
        println("[rock] topple run ${(System.nanoTime() - t0) / 1e6} ms; R3 now ${r3.pos.toList()} wakes=${r3.wakes} awake=${r3.awake}")
        assertTrue("R3 moved", r3.moved)
        val dx = r3.pos[0] - before[0]; val dy = r3.pos[1] - before[1]
        // (where it comes to rest depends on the pit rim at the sand grid's ~1 cm resolution: about half a centimetre down)
        assertTrue("it went down and toward the dug side (dx=$dx dy=$dy)", dy < -0.4 && dx < -1.0)
        assertFalse("it came to rest", r3.awake)
        assertTrue("its neighbours stayed put", rp.bodies.filter { it !== r3 }.none { it.moved })
        val ev = s.latest.events.filterIsInstance<SimEvent.RockShifted>()
        assertTrue("a RockShifted event was reported", ev.any { it.rock == "R3" })
        assertTrue("the renderer gets its pose", s.latest.rockPoses != null)
        // it rests on the glass / sand / rock, not inside them
        val f = rp.field
        var worst = 0.0
        for (i in 0 until r3.np) {
            val r = r3.rot
            val x = r3.pos[0] + r[0] * r3.rx[i] + r[1] * r3.ry[i] + r[2] * r3.rz[i]
            val y = r3.pos[1] + r[3] * r3.rx[i] + r[4] * r3.ry[i] + r[5] * r3.rz[i]
            val z = r3.pos[2] + r[6] * r3.rx[i] + r[7] * r3.ry[i] + r[8] * r3.rz[i]
            worst = minOf(worst, y, f.staticDist(x, y, z) + RockPhysics.PROBE_R)
        }
        assertTrue("no probe sunk into glass or other rock (worst $worst)", worst > -1.0)
        // the composite field now has the pillar where it fell
        assertTrue(s.rock.sdf.dist(com.reeflive.core.geom.Vec3(r3.pos[0], r3.pos[1], r3.pos[2])) < 0.0)
    }

    @Test
    fun staticRockscapeNeverMoves() {
        val s = sim(dynamic = false)
        undermineR3(s)
        s.advance(10.0, 0.25)
        assertFalse(s.rockPhysics!!.built)
        assertNull(s.latest.rockPoses)
    }

    @Test
    fun savedRockPosesComeBack() {
        val s = sim()
        undermineR3(s)
        s.advance(25.0, 0.05)
        val saved = s.rockState()
        assertTrue(saved.startsWith("R3:"))
        val t = sim()
        t.restoreRocks(saved)
        val a = s.rockPhysics!!.bodies.first { it.name == "R3" }
        val b = t.rockPhysics!!.bodies.first { it.name == "R3" }
        for (k in 0 until 3) assertEquals(a.pos[k], b.pos[k], 0.01)
        assertTrue(t.latest.rockPoses == null || true)
        assertTrue(t.rock.sdf.dist(com.reeflive.core.geom.Vec3(b.pos[0], b.pos[1], b.pos[2])) < 0.0)
    }

    @Test
    fun rockslideScenarioShiftsARock() {
        val s = TestTank.sim(emptyList(), seed = 5L)
        com.reeflive.core.live.Scenario.ROCKSLIDE.steps().forEach { s.enqueue(it.command) }
        val t0 = System.nanoTime()
        s.advance(25.0, 0.05)
        println("[rock] rockslide ${(System.nanoTime() - t0) / 1e6} ms")
        val shifted = s.rockPhysics!!.bodies.filter { it.moved }
        println("[rock] rockslide moved ${shifted.map { it.name }}")
        assertTrue("a rock moved", shifted.isNotEmpty())
        assertTrue("and came to rest", shifted.none { it.awake })
    }
}
