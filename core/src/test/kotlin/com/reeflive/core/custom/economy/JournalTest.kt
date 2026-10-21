package com.reeflive.core.custom.economy

import com.reeflive.core.custom.CustomTank
import com.reeflive.core.custom.TankBuilds
import com.reeflive.core.sim.Command
import com.reeflive.core.sim.SimEvent
import com.reeflive.core.sim.Simulation
import com.reeflive.core.sim.SpeciesCatalog
import com.reeflive.core.sim.TestTank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The reef journal (docs/ECONOMY.md §4 "Reef moments"): moments pay once ever, birthdays once per fish. */
class JournalTest {
    private val clown = "amphiprion_ocellaris"
    private val day = "2026-10-21"
    // a tank that has run 40 days, so a fish can have arrived a month ago
    private val START = 40 * 86400.0 + 10 * 3600.0
    private fun tank(): Simulation {
        val sim = CustomTank.fresh(TankBuilds.LONG_REEF, SpeciesCatalog.fromClasspath(listOf(clown, "zoanthus_sp")), TestTank.rock, START, seed = 3L)
        sim.stepWall(0.05)
        return sim
    }

    @Test fun aMomentPaysOnceWithItsStory() {
        val sim = tank()
        val e = Economy()
        e.update(sim, day)
        e.takeGains()
        val coins = e.state.coins
        sim.emit(SimEvent.Molted("nobody", sim.simTimeSec))
        e.update(sim, day)
        val g = e.takeGains().single { it.story != null }
        assertEquals(Moment.MOLT.reward, g.amount)
        assertEquals(Moment.MOLT.fact, g.story!!.fact)
        assertEquals(coins + Moment.MOLT.reward, e.state.coins)
        assertTrue(Moment.MOLT.id in e.state.journal.moments)
        assertTrue(e.state.ledger.first().label.startsWith("Reef moment"))
        // the same thing again, now or after a reload, pays nothing
        sim.stepWall(0.05)
        sim.emit(SimEvent.Molted("nobody", sim.simTimeSec))
        e.update(sim, day)
        val again = Economy(e.state)
        again.update(sim, day)
        assertTrue(e.takeGains().none { it.story != null } && again.takeGains().none { it.story != null })
        assertTrue(e.view(sim).journal.moments.single { it.moment == Moment.MOLT }.found)
    }

    @Test fun aFishGetsEachBirthdayOnceAndForgetsThemWhenItLeaves() {
        val sim = tank()
        sim.enqueue(Command.AddOrganism(clown, 6.0, "Nemo"))
        sim.stepWall(0.05)
        val e = Economy()
        e.update(sim, day)
        assertTrue(e.state.journal.kept.contains(clown))
        val nemo = sim.organisms.single { it.nickname == "Nemo" }
        nemo.arrivalTime = sim.simTimeSec - 31 * 86400.0
        e.takeGains()
        e.update(sim, day)
        // 7 and 30 days both passed: one cheer, for the month
        val g = e.takeGains().filter { it.story != null }
        assertEquals(1, g.size)
        assertEquals(Journal.BIRTHDAY_PAY[1], g[0].amount)
        assertEquals("Happy birthday, Nemo!", g[0].story!!.title)
        e.update(sim, day)
        assertTrue(e.takeGains().none { it.story != null })
        assertEquals(2, e.state.journal.birthdays[nemo.id])
        sim.enqueue(Command.RemoveOrganism(nemo.id))
        sim.stepWall(0.05)
        e.update(sim, day)
        assertTrue(e.state.journal.birthdays.isEmpty())
    }
}
