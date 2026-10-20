package com.reeflive.core.custom

import com.reeflive.core.sim.Command
import com.reeflive.core.sim.SpeciesCatalog
import com.reeflive.core.sim.TestTank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CareAlertsTest {
    private val noon = 12 * 3600_000L

    @Test fun hungryFishAndFullGearGetOneKindNoteEachWithinTheLimits() {
        val cat = SpeciesCatalog.fromClasspath(listOf("amphiprion_ocellaris"))
        val sim = CustomTank.fresh(TankBuilds.BREEDER, cat, TestTank.rock, 12 * 3600.0, seed = 3L)
        sim.enqueue(Command.AddOrganism("amphiprion_ocellaris", 6.0, "Nemo"))
        sim.stepWall(0.05)
        val plan = CareAlerts.plan(sim, noon, 0, hasEconomy = true, jobsLeftToday = 2)
        assertTrue(plan.isNotEmpty())
        assertTrue(plan.size <= CareAlerts.MAX_TOTAL)
        for (a in plan) {
            assertTrue("after the first 45 min", a.atMs >= noon + 45 * 60_000)
            val m = CareAlerts.localMinute(a.atMs, 0)
            assertTrue("not at night, got $m", m in CareAlerts.QUIET_TO_MIN until CareAlerts.QUIET_FROM_MIN)
        }
        assertEquals(plan.sortedBy { it.atMs }, plan)
    }

    @Test fun noFishAndNoJobsMeansNoHungerNote() {
        val cat = SpeciesCatalog.fromClasspath(listOf("amphiprion_ocellaris"))
        val sim = CustomTank.fresh(TankBuilds.NANO, cat, TestTank.rock, 12 * 3600.0, seed = 3L)
        val plan = CareAlerts.plan(sim, noon, 0, hasEconomy = false, jobsLeftToday = 0)
        assertTrue(plan.none { it.tag == "hungry" || it.tag == "jobs" })
    }

    @Test fun nightTimesMoveToMorningAndDaysAreCapped() {
        val due = (1..10).map { CareAlerts.Due(it * 300.0, "t$it", "T", "b", it) }
        val plan = CareAlerts.schedule(due, noon, 0)
        assertTrue(plan.size <= CareAlerts.MAX_TOTAL)
        assertTrue(CareAlerts.pastQuiet(23 * 60) == 9 * 60)
        assertEquals(0, CareAlerts.pastQuiet(10 * 60))
    }
}
