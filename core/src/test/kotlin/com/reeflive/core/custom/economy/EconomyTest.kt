package com.reeflive.core.custom.economy

import com.reeflive.core.custom.CustomTank
import com.reeflive.core.custom.CustomTankCodec
import com.reeflive.core.custom.CustomTankSave
import com.reeflive.core.custom.TankBuilds
import com.reeflive.core.sim.Command
import com.reeflive.core.sim.FoodKind
import com.reeflive.core.sim.MaintenanceTask
import com.reeflive.core.sim.Simulation
import com.reeflive.core.sim.SpeciesCatalog
import com.reeflive.core.sim.TestTank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** My tank's sand dollars (docs/ECONOMY.md): the daily list, the streak, the happy tank bonus, goals and saving. */
class EconomyTest {
    private val clown = "amphiprion_ocellaris"
    private fun tank(): Simulation {
        val sim = CustomTank.fresh(TankBuilds.LONG_REEF, SpeciesCatalog.fromClasspath(listOf(clown, "zoanthus_sp")), TestTank.rock, 10 * 3600.0, seed = 3L)
        sim.stepWall(0.05)
        return sim
    }

    /** Does every task on today's list with the command that does it. */
    private fun doAll(sim: Simulation, e: Economy, day: String) {
        for (id in e.state.tasks) when (CareTask.byId(id)!!) {
            CareTask.FEED_FISH -> sim.enqueue(Command.Feed(FoodKind.PELLET, 0.5))
            CareTask.FEED_CORALS -> sim.enqueue(Command.Feed(FoodKind.CORAL_FOOD, 0.2))
            CareTask.TEST_WATER -> e.testedWater(day)
            CareTask.TOP_OFF -> sim.enqueue(Command.Maintain(MaintenanceTask.TOP_OFF))
            CareTask.EMPTY_SKIMMER -> sim.enqueue(Command.Maintain(MaintenanceTask.EMPTY_SKIMMER))
            CareTask.CHANGE_SOCK -> sim.enqueue(Command.Maintain(MaintenanceTask.CHANGE_SOCK))
            CareTask.CLEAN_GLASS -> sim.enqueue(Command.ScrapeGlass(com.reeflive.core.sim.GlassPane.FRONT, 10.0, 10.0, 30.0, 10.0))
            CareTask.SIPHON_SAND -> sim.enqueue(Command.Maintain(MaintenanceTask.SIPHON_SAND))
            CareTask.HARVEST_MACRO -> sim.enqueue(Command.Maintain(MaintenanceTask.HARVEST_MACRO))
            CareTask.REPLACE_CARBON -> sim.enqueue(Command.Maintain(MaintenanceTask.REPLACE_CARBON))
            CareTask.WATER_CHANGE -> sim.enqueue(Command.WaterChange(0.15))
        }
        sim.stepWall(0.05)
        e.update(sim, day)
    }

    @Test fun aNewTankStartsWithMoneyAndThreeTasks() {
        val sim = tank()
        val e = Economy()
        e.update(sim, "2026-10-20")
        assertEquals(Economy.START_COINS, e.state.coins)
        assertEquals(3, e.state.tasks.size)
        assertEquals(1, e.level)
        // the same day gives the same list
        assertEquals(CareTasks.pick(sim, "2026-10-20", 3, "2026-10-20"), e.state.tasks.map { CareTask.byId(it) })
        // a brand-new tank is not asked for a water change on day one, but is a week later
        assertTrue(CareTask.WATER_CHANGE.id !in e.state.tasks)
        assertTrue(CareTask.WATER_CHANGE in CareTasks.pick(sim, "2026-10-27", 8, "2026-10-20"))
    }

    @Test fun doingTheListPaysTasksAndTheAllDoneBonusOnce() {
        val sim = tank()
        val e = Economy()
        e.update(sim, "2026-10-20")
        val pay = e.state.tasks.sumOf { CareTask.byId(it)!!.pay }
        doAll(sim, e, "2026-10-20")
        assertTrue(e.state.allDonePaid)
        assertEquals(Economy.START_COINS + pay + Economy.ALL_DONE_BONUS, e.state.coins)
        assertEquals(1, e.state.streak)
        // doing it all again the same day pays nothing more
        doAll(sim, e, "2026-10-20")
        assertEquals(Economy.START_COINS + pay + Economy.ALL_DONE_BONUS, e.state.coins)
        assertTrue(e.takeGains().isNotEmpty())
    }

    @Test fun streakGrowsDayByDayAndResetsAfterAMissedDay() {
        val sim = tank()
        val e = Economy()
        for (d in 20..23) { e.update(sim, "2026-10-$d"); doAll(sim, e, "2026-10-$d") }
        assertEquals(4, e.state.streak)
        assertEquals(1.3, e.view(sim).multiplier, 1e-9)
        e.update(sim, "2026-10-25")   // skipped the 24th
        assertEquals(0, e.state.streak)
        assertEquals(1.0, e.view(sim).multiplier, 1e-9)
    }

    @Test fun happyTankBonusPaysForHealthyAnimalsOncePerDay() {
        val sim = tank()
        sim.enqueue(Command.AddMany(clown, 2, 3.0))
        sim.stepWall(0.05)
        val e = Economy()
        e.update(sim, "2026-10-20")
        assertEquals(2 * Economy.HAPPY_PER_ANIMAL, e.collectHappy(sim, "2026-10-20"))
        assertEquals(0, e.collectHappy(sim, "2026-10-20"))
        // with fish in, feeding is always on the list, and the first fish goal paid
        assertTrue(CareTask.FEED_FISH.id in e.state.tasks)
        assertTrue(Goal.FIRST_FISH.id in e.state.goals)
    }

    @Test fun levelsFollowStarPoints() {
        assertEquals(1, Levels.level(0))
        assertEquals(1, Levels.level(59))
        assertEquals(2, Levels.level(60))
        assertEquals(4, Levels.level(360))
        assertEquals(10, Levels.level(1_000_000))
    }

    @Test fun spendingNeverGoesBelowZeroAndSalesGiveNoStars() {
        val e = Economy()
        assertFalse(e.spend(Economy.START_COINS + 1, "Blue tang", "2026-10-20"))
        assertTrue(e.spend(25, "Clownfish", "2026-10-20"))
        e.sold(10, "Snail", "2026-10-20")
        assertEquals(Economy.START_COINS - 15, e.state.coins)
        assertEquals(0, e.state.stars)
    }

    @Test fun calendarDays() {
        assertEquals(1, CalendarDay.daysBetween("2026-12-31", "2027-01-01"))
        assertEquals(1, CalendarDay.daysBetween("2028-02-28", "2028-02-29"))
        assertEquals(0L, CalendarDay.epochDay("1970-01-01"))
        assertEquals("2026-03-07", CalendarDay.key(2026, 3, 7))
    }

    @Test fun economySurvivesSaveAndOlderSavesStartWithMoney() {
        val sim = tank()
        val e = Economy()
        e.update(sim, "2026-10-20"); doAll(sim, e, "2026-10-20")
        val text = CustomTankCodec.encode(CustomTank.capture(sim, CustomTankSave(economy = e.state), 1L))
        assertEquals(e.state, CustomTankCodec.decode(text)!!.economy)
        // a save from before the economy
        val old = CustomTankCodec.decode("""{"version":1,"name":"Old tank","buildId":"nano_cube_20"}""")!!
        assertEquals(Economy.START_COINS, old.economy.coins)
    }
}
