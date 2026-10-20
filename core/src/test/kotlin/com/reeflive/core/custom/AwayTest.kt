package com.reeflive.core.custom

import com.reeflive.core.sim.Command
import com.reeflive.core.sim.SpeciesCatalog
import com.reeflive.core.sim.TestTank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** My tank while away (docs/ECONOMY.md §9): no hidden feeder, hunger builds, the grace, the welcome-back words. */
class AwayTest {
    private val clown = "amphiprion_ocellaris"
    private fun tank() = CustomTank.fresh(TankBuilds.LONG_REEF, SpeciesCatalog.fromClasspath(listOf(clown, "nassarius_sp")), TestTank.rock, 10 * 3600.0, 9L).also {
        it.enqueue(Command.AddOrganism(clown, 6.0, "Nemo"))
        it.enqueue(Command.AddOrganism(clown, 5.0, "Marlin"))
        it.stepWall(0.05)
    }

    @Test fun aDayAwayLeavesTheFishHungryAndSaysSo() {
        val sim = tank()
        val run = AwayCatchUp(sim, 1.5 * AwayCatchUp.DAY)
        var chunks = 0
        while (!run.step()) chunks++
        assertTrue("ran in chunks", chunks > 3)
        assertEquals(1.5 * AwayCatchUp.DAY, run.doneSec, 1e-6)
        val s = run.summary()
        assertTrue(s.worthShowing)
        assertTrue(s.hungry.containsAll(listOf("Nemo", "Marlin")))
        assertTrue(s.lines.first().contains("Nemo and Marlin are very hungry"))
        assertTrue(s.deaths.isEmpty())
        assertTrue(!sim.healthGrace)
    }

    @Test fun graceKeepsStarvingFishAliveTheFirstTwoDaysOnly() {
        val sim = tank()
        val nemo = sim.organisms.first { it.nickname == "Nemo" }
        nemo.condition = 0.0; nemo.hunger = 1.0; nemo.health = 0.2
        AwayCatchUp(sim, AwayCatchUp.GRACE_SEC).runAll()
        assertTrue("alive through the grace", nemo.alive)
        val s = AwayCatchUp(sim, 3 * AwayCatchUp.DAY).runAll()
        assertTrue("starved after it", !nemo.alive)
        assertEquals("Nemo" to "hunger", s.deaths.first())
        assertTrue(s.headline.contains("did not make it"))
    }

    @Test fun awayTimeIsCappedAtThirtyDays() {
        assertEquals(AwayCatchUp.MAX_DAYS * AwayCatchUp.DAY, AwayCatchUp(tank(), 90 * AwayCatchUp.DAY).totalSec, 1e-6)
    }
}
