package com.reeflive.core.custom

import com.reeflive.core.sim.Command
import com.reeflive.core.sim.LifePace
import com.reeflive.core.sim.SpeciesCatalog
import com.reeflive.core.sim.TestTank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** My tank's growing speed: slow biology runs on the game clock, everything else on the sim clock. */
class LifePaceTest {
    private val clown = "amphiprion_ocellaris"
    private fun catalog() = SpeciesCatalog.fromClasspath(listOf(clown, "zoanthus_sp"))

    private fun tank(pace: Double) = CustomTank.fresh(TankBuilds.DEFAULT, catalog(), TestTank.rock, 10 * 3600.0, seed = 3L).also {
        it.lifePace = pace
        it.enqueue(Command.AddOrganism(clown, 3.0, "Nemo"))
        it.stepWall(0.05)
    }

    @Test fun realPaceKeepsTheGameClockOnTheSimClock() {
        val sim = tank(1.0)
        sim.advanceAbstract(3 * 3600.0)
        assertEquals(sim.simTimeSec, sim.lifeSec, 0.0)
        assertEquals(1.0, sim.lifeAge(sim.simTimeSec - 86400.0), 1e-12)
    }

    @Test fun fastPaceGrowsFishSevenTimesFasterButHungerAndLightsStayReal() {
        val slow = tank(1.0); val fast = tank(LifePace.FAST.pace)
        val l0 = slow.organisms.single().lengthCm
        slow.advanceAbstract(86400.0); fast.advanceAbstract(86400.0)
        val gSlow = slow.organisms.single().lengthCm - l0
        val gFast = fast.organisms.single().lengthCm - l0
        assertTrue("grew at all", gSlow > 0)
        // a 3 cm clown is a youngster: Fast also gives it the youngsters' growth spurt
        assertEquals(7.0 * LifePace.YOUNG_BOOST, gFast / gSlow, 3.0)
        assertEquals(slow.simTimeSec, fast.simTimeSec, 1e-6)
        assertEquals("a day of play is a tank week", 7.0, (fast.lifeSec - 10 * 3600.0) / 86400.0, 1e-4)
        assertEquals(slow.minuteOfDay, fast.minuteOfDay, 1e-6)
        assertEquals(slow.organisms.single().hunger, fast.organisms.single().hunger, 0.05)
        assertEquals(slow.organisms.single().ageSec, fast.organisms.single().ageSec, 1e-6)
    }

    @Test fun aRaisedBabyClownReachesBreedingSizeInAboutAWeekAtFast() {
        val sim = tank(LifePace.FAST.pace)
        val nemo = sim.organisms.single()
        nemo.lengthCm = 2.5
        var days = 0
        while (nemo.lengthCm < com.reeflive.core.sim.Reproduction.FEMALE_MATURE_CM && days < 60) { sim.advanceAbstract(86400.0); days++ }
        assertTrue("took $days days", days in 5..14)
    }

    @Test fun paceAndGameClockSurviveSaveAndOlderSavesTakeTheDefault() {
        val sim = tank(LifePace.FASTER.pace)
        sim.advanceAbstract(3600.0)
        val re = CustomTank.restore(CustomTankCodec.decode(CustomTankCodec.encode(CustomTank.capture(sim, CustomTankSave(seed = 3L), 1L)))!!, catalog(), TestTank.rock)
        assertEquals(LifePace.FASTER.pace, re.lifePace, 0.0)
        assertEquals(sim.lifeSec, re.lifeSec, 1e-6)
        // a save from before the growing speed existed: Fast, with the game clock starting at the sim clock
        val old = CustomTankCodec.decode("""{"version":1,"simTimeSec":50000.0,"seed":3}""")!!
        val o = CustomTank.restore(old, catalog(), TestTank.rock)
        assertEquals(LifePace.DEFAULT.pace, o.lifePace, 0.0)
        assertEquals(50000.0, o.lifeSec, 0.0)
        assertEquals(LifePace.FAST, LifePace.of(6.0))
    }
}
