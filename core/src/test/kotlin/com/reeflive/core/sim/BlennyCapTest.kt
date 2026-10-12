package com.reeflive.core.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** B-10: one-blenny cap (second blenny chronically stressed) and the add-time warning API. */
class BlennyCapTest {
    private val mower get() = TestTank.seed("lawnmower")
    private val spot get() = TestTank.seed("tailspot")

    @Test
    fun secondBlennyIsChronicallyStressed() {
        val sim = TestTank.sim(listOf(mower, spot))
        sim.advanceAbstract(10 * 86400.0)
        val first = sim.organisms.first { it.id == "lawnmower" }
        val second = sim.organisms.first { it.id == "tailspot" }
        assertTrue("second chronic ${second.chronic} vs first ${first.chronic}", second.chronic > first.chronic + 0.05)
        assertTrue("second stress ${second.stress}", second.stress >= 0.12)
    }

    @Test
    fun lonelyBlennyIsNotStressedByTheCap() {
        val sim = TestTank.sim(listOf(mower))
        sim.advanceAbstract(10 * 86400.0)
        assertTrue(sim.organisms.first().chronic < 0.1)
    }

    @Test
    fun largeTankLiftsTheCap() {
        val sim = TestTank.sim(listOf(mower, spot), water = WaterState(volumeL = 600.0), config = TankConfig(systemLiters = 600.0))
        sim.advanceAbstract(10 * 86400.0)
        val spotChronic = sim.organisms.first { it.id == "tailspot" }.chronic
        assertTrue("chronic $spotChronic", spotChronic < 0.1)
    }

    @Test
    fun laterArrivalIsTheStressedOne() {
        val sim = TestTank.sim(listOf(spot))
        sim.enqueue(Command.AddOrganism("salarias_fasciatus", 10.0, "Late"))
        sim.advanceAbstract(10 * 86400.0)
        val late = sim.organisms.first { it.nickname == "Late" }
        val resident = sim.organisms.first { it.id == "tailspot" }
        assertTrue("late ${late.chronic} resident ${resident.chronic}", late.chronic > resident.chronic + 0.05)
    }

    @Test
    fun warningsForSecondBlenny() {
        val w = compatibilityWarnings("ecsenius_stigmatura", listOf("salarias_fasciatus", "amphiprion_ocellaris"), 227.0)
        assertEquals(listOf("blenny_cap"), w.map { it.code })
        assertTrue(compatibilityWarnings("salarias_fasciatus", listOf("ecsenius_stigmatura")).isNotEmpty())
        assertTrue(compatibilityWarnings("ecsenius_stigmatura", listOf("salarias_fasciatus"), 500.0).isEmpty())
        assertTrue(compatibilityWarnings("ecsenius_stigmatura", listOf("amphiprion_ocellaris")).isEmpty())
    }

    @Test
    fun otherWarnings() {
        assertEquals(listOf("dottyback_cap"), compatibilityWarnings("pseudochromis_indigo", listOf("pseudochromis_indigo")).map { it.code })
        assertEquals(listOf("tang_tank_size"), compatibilityWarnings("paracanthurus_hepatus", emptyList(), 227.0).map { it.code })
        assertTrue(compatibilityWarnings("paracanthurus_hepatus", emptyList(), 600.0).isEmpty())
        assertTrue(compatibilityWarnings("unknown_thing", listOf("salarias_fasciatus")).isEmpty())
    }
}
