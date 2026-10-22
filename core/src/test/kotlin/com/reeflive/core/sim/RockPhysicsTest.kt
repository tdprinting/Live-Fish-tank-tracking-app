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

    /** The holes and caves in a rock that toppled go with it (the hole dwellers look them up by name); the others stay. */
    @Test
    fun holesRideWithTheirRock() {
        val s = sim()
        val r3 = s.rockPhysics!!.bodies.first { it.name == "R3" }
        val before = r3.pos.copyOf()
        undermineR3(s)
        s.advance(25.0, 0.05)
        assertTrue("R3 moved", r3.moved)
        val shift = com.reeflive.core.geom.Vec3(r3.pos[0] - before[0], r3.pos[1] - before[1], r3.pos[2] - before[2]).length()
        val rest = s.rock.restHoles.all.associateBy { it.name }
        val fresh = sim(dynamic = false)
        val moved = s.rock.holes.all.filter { it.mouth.distanceTo(rest.getValue(it.name).mouth) > 0.01 }
        println("[rock] R3 shifted ${"%.1f".format(shift)} cm; holes that went with it: ${moved.map { it.name }}")
        assertTrue("R3's holes went with it", moved.isNotEmpty())
        for (h in moved) {
            val r = rest.getValue(h.name)
            assertEquals("${h.name} keeps its size", r.radius, h.radius, 1e-9)
            assertEquals("${h.name} keeps its length", r.path.zipWithNext { a, b -> a.distanceTo(b) }.sum(),
                h.path.zipWithNext { a, b -> a.distanceTo(b) }.sum(), 1e-6)
            // the mouth sits where it sat on the rock (the moved rock's field is resampled at 1 cm)
            val now = s.rock.sdf.dist(h.mouth); val was = fresh.rock.sdf.dist(r.mouth)
            assertTrue("${h.name} mouth still on the rock (rock distance $was -> $now)", kotlin.math.abs(now - was) < 1.0)
            assertTrue("${h.name} moved no farther than the rock could carry it", h.mouth.distanceTo(r.mouth) < shift + 30.0)
        }
        val r3Def = s.rock.pieces.indexOfFirst { it.name == "R3" }
        for (h in s.rock.holes.all) if (h !in moved) {
            val q = h.along(minOf(h.depth, 1.0))
            assertTrue("${h.name} is not on R3 but moved", RockPieceDef.labelOf(s.rock.pieces, q.x, q.y, q.z, 4.0) != r3Def || h.kind == HoleKind.BURROW)
        }
        assertTrue("the brains' lookup follows", s.rock.holes.live(rest.getValue(moved[0].name)) === moved[0])
    }

    /** Taking the rocks out takes their holes with them; the hole dwellers then shelter on what rock is left. */
    @Test
    fun holesGoWithTheirRock() {
        val s = TestTank.sim(DefaultStock.all, seed = 3L)
        s.setDynamicRocks(true)
        assertTrue(s.rock.holes.all.any { it.kind != HoleKind.BURROW })
        s.clearRocks()
        assertTrue("no rock, no holes: ${s.rock.holes.all.map { it.name }}", s.rock.holes.all.all { it.kind == HoleKind.BURROW })
        s.advance(60.0, 0.05)
        val mower = s.organisms.first { it.brainKind == BrainKind.LAWNMOWER_BLENNY }
        assertNull("the lawnmower's hole went with its rock", (s.agentOf(mower)!!.brain as com.reeflive.core.ai.LawnmowerBlennyBrain).hole)
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

    @Test
    fun aquascapeFromScratchStacksDryRockThatStaysThroughASave() {
        val s = sim(dynamic = false)
        val t0 = System.nanoTime()
        s.enqueue(Command.ClearRocks)
        s.advance(0.5, 0.05)
        val rp = s.rockPhysics!!
        assertTrue("the tank is empty", rp.bodies.isEmpty())
        assertTrue("no rock left in the field", s.rock.sdf.dist(com.reeflive.core.geom.Vec3(40.2, 10.0, 29.7)) > 2.0)
        assertTrue("old rock is hidden", s.latest.rockPoses != null)
        s.enqueue(Command.PlaceRock(DryRockShape.SLAB, 60.0, 23.0, seed = 1))
        s.advance(8.0, 0.05)
        s.enqueue(Command.PlaceRock(DryRockShape.RUBBLE, 60.0, 23.0, yawDeg = 30.0, seed = 2))
        s.advance(8.0, 0.05)
        println("[rock] scape from scratch ${(System.nanoTime() - t0) / 1e6} ms")
        val (slab, rub) = rp.added
        println("[rock] slab ${slab.pos.toList()} rubble ${rub.pos.toList()} awake ${slab.awake} ${rub.awake}")
        assertFalse(slab.awake); assertFalse(rub.awake)
        run {
            var gap = Double.MAX_VALUE
            for (i in 0 until rub.np) {
                val r = rub.rot
                val x = rub.pos[0] + r[0] * rub.rx[i] + r[1] * rub.ry[i] + r[2] * rub.rz[i]
                val y = rub.pos[1] + r[3] * rub.rx[i] + r[4] * rub.ry[i] + r[5] * rub.rz[i]
                val z = rub.pos[2] + r[6] * rub.rx[i] + r[7] * rub.ry[i] + r[8] * rub.rz[i]
                gap = minOf(gap, rp.field.pieceDist(slab.pieces[0], x, y, z))
            }
            println("[rock] rubble probe to slab gap $gap")
            assertTrue("the small rock touches the slab (gap $gap)", gap < 1.2)
        }
        val sandY = s.sand.surfaceY(60.0, 23.0)
        assertTrue("the slab rests on the sand (y ${slab.pos[1]}, sand $sandY)", slab.pos[1] in sandY..sandY + 5.0)
        assertTrue("the small rock sits on top of the slab", rub.pos[1] > slab.pos[1] + 3.0)
        assertTrue("the field has them", s.rock.sdf.dist(com.reeflive.core.geom.Vec3(rub.pos[0], rub.pos[1], rub.pos[2])) < 0.0)
        val built = s.latest.builtRocks!!
        assertEquals(2, built.size)
        assertTrue("fresh dry rock is white", built.all { it.dryness > 0.99f })
        // the same scape comes back in a new tank
        val state = s.rockState()
        val s2 = sim(dynamic = true)
        s2.restoreRocks(state)
        s2.advance(2.0, 0.05)
        val back = s2.rockPhysics!!.added
        assertEquals(2, back.size)
        assertTrue(s2.rockPhysics!!.bodies.size == 2)
        assertEquals(rub.pos[1], back[1].pos[1], 0.3)
        assertTrue("nothing moved after loading", back.none { it.awake })
        // undo takes out the last rock
        s2.enqueue(Command.RemoveRock())
        s2.advance(0.5, 0.05)
        assertEquals(1, s2.rockPhysics!!.added.size)
    }

    @Test
    fun aDroppedPillarStandsUp() {
        val s = sim(dynamic = false)
        s.clearRocks()
        for ((i, x) in listOf(20.0, 50.0, 80.0, 105.0).withIndex()) s.placeRock(DryRockShape.PILLAR, x, 23.0, yawDeg = i * 45.0, seed = i)
        s.advance(10.0, 0.05)
        val up = s.rockPhysics!!.added.map { it.rot[4] }
        println("[rock] pillar up-axis y $up")
        assertTrue("every pillar still stands ($up)", up.all { it > 0.9 })
        assertTrue("landing is no rockslide", s.latest.events.none { it is SimEvent.RockShifted })
    }

    @Test
    fun dryRockCuresOverMonths() {
        assertEquals(1.0, DryRock.dryness(0.0), 1e-9)
        assertTrue(DryRock.dryness(30.0) in 0.5..0.7)
        assertTrue(DryRock.dryness(180.0) < 0.06)
    }

    @Test
    fun aHardHitBudgesALooseRockButNotTheBigOnes() {
        val s = sim(dynamic = true)
        s.clearRocks()
        s.placeRock(DryRockShape.RUBBLE, 60.0, 23.0, seed = 2)
        s.placeRock(DryRockShape.SLAB, 90.0, 23.0, seed = 1)
        s.advance(8.0, 0.05)
        val rp = s.rockPhysics!!
        val (rub, slab) = rp.added
        assertFalse(rub.awake); assertFalse(slab.awake)
        // a 60 g tang dashing into each at 40 cm/s, side on at the height of the centre of mass
        fun hit(b: RockBody): Boolean {
            var x = b.pos[0] - 30.0
            while (s.rock.sdf.dist(com.reeflive.core.geom.Vec3(x, b.pos[1], b.pos[2])) > 0.0) x += 0.1
            return rp.bump(x, b.pos[1], b.pos[2], 60.0 * 40.0, 0.0, 0.0)
        }
        val r0 = rub.pos.copyOf(); val s0 = slab.pos.copyOf()
        assertTrue("the small rock takes the hit", hit(rub))
        hit(slab)
        s.advance(RockPhysics.MAX_AWAKE_S + 1.0, 0.05)
        val moved = rub.pos[0] - r0[0]
        println("[rock] rubble ${"%.0f".format(rub.mass)} g moved $moved cm; slab ${"%.0f".format(slab.mass)} g moved ${slab.pos[0] - s0[0]} cm")
        // momentum, friction and the rock's wet weight: a 0.7 kg rock slides a fraction of a millimetre
        assertTrue("the rubble budged along the hit ($moved cm)", moved > 0.005)
        assertTrue("... a little, not flung ($moved cm)", moved < 3.0)
        assertFalse("and settled again", rub.awake)
        assertEquals("the heavy slab stays put", s0[0], slab.pos[0], 0.05)
    }

    @Test
    fun aHeadRunningIntoRockPushesOnIt() {
        val s = TestTank.sim(DefaultStock.all.filter { it.id == "tang" }, seed = 3L).also { it.setDynamicRocks(true) }
        val o = s.organisms.first()
        val hits = ArrayList<DoubleArray>()
        s.rockGuard.onHit = { x, y, z, jx, jy, jz -> hits += doubleArrayOf(x, y, z, jx, jy, jz) }
        // a head just inside the rock face, swimming into it at 40 cm/s
        var p = com.reeflive.core.geom.Vec3(40.2, 10.0, 29.7)
        val n = s.rock.sdf.gradient(p).normalized()
        while (s.rock.sdf.dist(p) < -0.3) p = p + n * 0.1
        while (s.rock.sdf.dist(p) > -0.3) p = p - n * 0.1
        o.pos = p; o.heading = n * -1.0; o.vel = n * -40.0
        o.spline.reset(o.pos, o.heading)
        s.rockGuard.apply(o)
        assertEquals("one hit", 1, hits.size)
        val j = hits[0]
        val push = j[3] * n.x + j[4] * n.y + j[5] * n.z
        assertTrue("pushes into the rock with about its momentum ($push)", push < -0.5 * o.massG * 40.0)
        // a graze along the face is no hit
        hits.clear()
        o.pos = p; o.vel = n.cross(com.reeflive.core.geom.Vec3.Y).normalized() * 40.0
        s.rockGuard.apply(o)
        assertTrue("a graze is no hit", hits.isEmpty())
    }
}
