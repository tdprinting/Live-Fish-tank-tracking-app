package com.reeflive.core.sim

import com.reeflive.core.geom.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Taking a dead animal out with the fish net ([FishNet], [Command.NetOut]). */
class FishNetTest {
    private fun sim(): Simulation = Simulation(TankConfig(seed = 7L), SpeciesCatalog(), TestTank.rock, 7L, 12 * 3600.0)

    private fun dead(s: Simulation, id: String, species: String, at: Vec3? = null): Organism {
        val o = s.addFromSeed(LivestockSeed(species, id, 6.0, id = id))
        at?.let { o.pos = it }
        HealthModel.kill(o, "test", s.simTimeSec, ArrayList())
        return o
    }

    @Test fun netScoopsTheBodyLiftsItOutAndItIsGone() {
        val s = sim()
        val body = dead(s, "nemo", "amphiprion_ocellaris")
        val buddy = s.addFromSeed(LivestockSeed("amphiprion_ocellaris", "Marlin", 6.0, id = "marlin"))
        s.advance(60.0, behaviorDt = 0.1)   // let the body sink or float to where it rests
        s.enqueue(Command.NetOut("nemo"))
        val rock = s.rock
        var sawCarry = false
        var phase = -1
        val steps = ((FishNet.TOTAL_SEC + 0.5) / 0.05).toInt()
        repeat(steps) {
            s.stepWall(0.05)
            val n = s.snapshot().net ?: return@repeat
            assertTrue("phases only go forward", n.phase >= phase); phase = n.phase
            val hoop = Vec3(n.x.toDouble(), n.y.toDouble(), n.z.toDouble())
            if (hoop.y < rock.waterLevelCm) {
                assertTrue("hoop off the rock", rock.sdf.dist(hoop) > n.radius - 0.3)
                assertTrue("hoop inside the glass", hoop.x > n.radius && hoop.x < rock.lengthCm - n.radius && hoop.z > n.radius && hoop.z < rock.widthCm - n.radius)
                assertTrue("hoop above the sand", hoop.y - n.radius * 0.85 > s.sand.surfaceY(hoop.x, hoop.z))
                if (buddy.alive) assertTrue("no fish swims through the net", buddy.pos.distanceTo(hoop) > n.radius * 0.9)
            }
            if (n.carrying) {
                sawCarry = true
                assertTrue("body rides in the bag", body.pos.distanceTo(hoop) < n.radius * 2.5)
            }
        }
        assertTrue(sawCarry)
        assertEquals(FishNet.OUT, phase)
        val snap = s.snapshot()
        assertNull("net gone", snap.net)
        assertTrue("body removed", s.organisms.none { it.id == "nemo" })
        assertTrue("its carcass no longer leaches", snap.stats.none { it.id == "nemo" })
        val f = snap.netFlush
        assertNotNull(f)
        assertEquals(1, f!!.seq)
        assertEquals("nemo", f.id)
        assertTrue(f.isFish)
        assertTrue("the living clown stays", s.organisms.any { it.id == "marlin" && it.alive })
    }

    @Test fun livingAnimalsAreNotNettedAndTapsQueue() {
        val s = sim()
        s.addFromSeed(LivestockSeed("amphiprion_ocellaris", "Live", 6.0, id = "live"))
        s.enqueue(Command.NetOut("live"))
        s.stepWall(0.05)
        assertNull(s.snapshot().net)
        dead(s, "a", "amphiprion_ocellaris", Vec3(30.0, 20.0, 25.0))
        dead(s, "b", "amphiprion_ocellaris", Vec3(90.0, 20.0, 25.0))
        s.enqueue(Command.NetOut("a")); s.enqueue(Command.NetOut("b")); s.enqueue(Command.NetOut("a"))
        s.stepWall(0.05)
        assertEquals("a", s.snapshot().net?.targetId)
        assertEquals(listOf("a", "b"), s.net.pending())
        repeat(((FishNet.TOTAL_SEC * 2 + 1) / 0.05).toInt()) { s.stepWall(0.05) }
        assertFalse(s.net.busy)
        assertEquals(2, s.snapshot().netFlush?.seq)
        assertTrue(s.organisms.none { it.id == "a" || it.id == "b" })
        assertTrue(s.organisms.any { it.id == "live" })
    }
}
