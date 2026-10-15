package com.reeflive.core.sim

import com.reeflive.core.ai.LeopardWrasseBrain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bodies burying themselves in the sand (SIMULATION §16.10): the leopard wrasse's night in the sand. */
class SandBurialTest {
    // a ~10 cm leopard wrasse lying on its side: slot as wide as the body is deep, as thick as the body is wide
    private val len = 10.0
    private val wid = 3.6
    private val thick = 1.6
    private val cover = 2.2

    @Test fun buriesOnlyWhereTheSandIsDeepEnough() {
        val s = SandBed(121.9, 33.0, 3.0, 1L)
        // the bed is 6 cm deep at the back glass and 3.5 cm at the front: 3.8 cm of body and cover fits only toward the back
        assertTrue(s.canBury(60.0, 5.0, 1.0, 0.0, len, wid, thick, cover))
        assertFalse(s.canBury(60.0, 30.0, 1.0, 0.0, len, wid, thick, cover))
        assertNull(s.startBurial("w", 60.0, 30.0, 1.0, 0.0, len, wid, thick, cover))
        // a pit already dug where it would lie leaves too little sand under it
        repeat(40) { s.takeMouthful(55.0, 6.0, 1.0) }
        s.dropCarried(20.0, 25.0)
        assertFalse(s.canBury(60.0, 6.0, 1.0, 0.0, len, wid, thick, cover))
    }

    @Test fun burialMovesSandAndLeavesARealCover() {
        val s = SandBed(121.9, 33.0, 3.0, 1L)
        val v0 = s.sandVolumeCm3()
        val surf0 = s.surfaceY(55.0, 6.0)
        val b = s.startBurial("w", 60.0, 6.0, 1.0, 0.0, len, wid, thick, cover)!!
        // the dive: digs the slot (sand to the rim, a depression and a rim form), then pulls the cover back
        for (i in 1..20) {
            val k = i / 20.0
            s.buryStep(b, k / 0.85, (k - 0.55) / 0.45)
            assertEquals("sand conserved mid-dive (step $i)", v0, s.sandVolumeCm3(), 1e-2)
            if (i == 8) {
                assertTrue("a depression while it digs", s.surfaceY(55.0, 6.0) < surf0 - 0.5)
                assertTrue("a rim ahead", s.cellHeight(b.rimX(0), b.rimZ(0)) > 0.1)
            }
        }
        s.buryStep(b, 1.0, 1.0)
        assertEquals(v0, s.sandVolumeCm3(), 1e-2)
        // buried for real: the body's top lies under the cover, its centre 2-5 cm under the surface it left
        val top = b.bodyY + 0.5 * thick
        val surfNow = s.surfaceY(b.cx, b.cz)
        assertTrue("cover over the body ${surfNow - top} cm", surfNow - top in 0.6 * cover..1.4 * cover)
        assertTrue("centre ${surfNow - b.bodyY} cm down", surfNow - b.bodyY in 2.0..5.0)
        assertTrue("the surface over it is close to where it was (${surfNow - surf0})", kotlin.math.abs(surfNow - surf0) < 1.0)
        assertTrue("body sits above the glass", b.bodyY - 0.5 * thick > SandBed.GLASS_Y)
        // the sand over a buried body does not creep in under it overnight
        repeat(600) { s.relax(1.0, 1.0, 0.2) }
        assertTrue(s.surfaceY(b.cx, b.cz) - top > 0.5 * cover)
        assertEquals(v0, s.sandVolumeCm3(), 1e-2)
    }

    @Test fun emergenceLeavesADimpleThatRelaxes() {
        val s = SandBed(121.9, 33.0, 3.0, 1L)
        val v0 = s.sandVolumeCm3()
        val b = s.startBurial("w", 60.0, 6.0, 1.0, 0.0, len, wid, thick, cover)!!
        s.buryStep(b, 1.0, 1.0)
        val before = s.surfaceY(b.cx, b.cz)
        s.emergeFrom("w")
        assertNull(s.buriedOf("w"))
        assertEquals(v0, s.sandVolumeCm3(), 1e-2)
        val base = s.baseY(b.cz)
        val dimple0 = base - s.surfaceY(b.cx, b.cz)
        assertTrue("a dimple where the body was ($dimple0 cm; surface before ${before - base})", dimple0 > 0.5)
        repeat(6 * 60) { s.relax(1.0, 1.0, 0.2) }
        val dimple6h = base - s.surfaceY(b.cx, b.cz)
        assertTrue("relaxing: $dimple0 → $dimple6h cm", dimple6h < dimple0 && dimple6h > 0.0)
        assertEquals(v0, s.sandVolumeCm3(), 1e-2)
    }

    @Test fun liveWrasseSleepsUnderRealSand() {
        val sim = TestTank.sim(DefaultStock.corals, seed = 5L, startHour = 19.0)
        sim.species.ensure("macropharyngodon_bipartitus")
        val o = sim.addFromSeed(LivestockSeed("macropharyngodon_bipartitus", "Leopard", 8.5, id = "lw1"))
        val br = sim.agentOf(o)!!.brain as LeopardWrasseBrain
        val v0 = sim.sand.sandVolumeCm3()
        sim.advance(1.5 * 3600, behaviorDt = 0.1)
        assertTrue(br.buried)
        val b = sim.sand.buriedOf(o.id)
        assertNotNull("a body in the sand", b); b!!
        val depth = sim.sand.surfaceY(o.pos.x, o.pos.z) - o.pos.y
        assertTrue("head $depth cm under the sand", depth in 2.0..5.0)
        assertTrue("body above the glass", b.bodyY - 0.5 * b.thickCm > SandBed.GLASS_Y)
        // only grains still in suspension or drifted away count against the bed
        assertEquals("sand conserved through the dive", v0, sim.sand.sandVolumeCm3(), 0.05 * kotlin.math.abs(v0) + 0.5)
    }
}
