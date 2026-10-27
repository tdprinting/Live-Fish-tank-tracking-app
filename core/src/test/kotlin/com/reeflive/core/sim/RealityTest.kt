package com.reeflive.core.sim

import com.reeflive.core.model.LightPoint
import com.reeflive.core.model.Livestock
import com.reeflive.core.units.ParamUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.reeflive.core.model.LightSchedule as TrackerSchedule

class RealityTest {
    @Test
    fun waterFromTrackerUnits() {
        val w = Reality.waterFrom(mapOf("temp" to 78.8, "sal" to 1.025, "alk" to 8.4, "nh3" to 0.25, "no3" to 12.0, "i" to 0.05))
        assertEquals(26.0, w.tempC, 0.01)
        assertEquals(1.025, w.sg, 1e-6)
        assertEquals(8.4, w.alk, 1e-9)
        assertEquals(0.25, w.nh3Total, 1e-9)
        assertTrue(w.nh3Free > 0 && w.nh3Free < 0.25)
        assertEquals(1.0263, Reality.waterFrom(mapOf("sal" to 35.0)).sg, 0.001)
    }

    @Test
    fun implausibleNutrientsAreClampedAndFlagged() {
        val w = Reality.waterFrom(mapOf("no3" to 5000.0, "po4" to 220.0))
        assertEquals(com.reeflive.core.live.ReadingSanity.NO3_MAX, w.no3, 1e-9)
        assertEquals(com.reeflive.core.live.ReadingSanity.PO4_MAX, w.po4, 1e-9)
        assertEquals(12.0, Reality.waterFrom(mapOf("no3" to 12.0)).no3, 1e-9)
        assertEquals(0.05, Reality.waterFrom(mapOf("po4" to 0.05)).po4, 1e-9)
        assertTrue(com.reeflive.core.live.ReadingSanity.warning("no3", 79.0)!!.contains("units"))
        assertTrue(com.reeflive.core.live.ReadingSanity.warning("po4", 0.8)!!.contains("units"))
        assertEquals(null, com.reeflive.core.live.ReadingSanity.warning("no3", 20.0))
        assertEquals(null, com.reeflive.core.live.ReadingSanity.warning("po4", 0.22))
    }

    /** The "79 / 0.22" sighting is exactly NO3-N 17.9 and PO4-P 0.07 read as NO3 / PO4: the converter itself is right. */
    @Test
    fun nutrientUnitFactorsRoundTrip() {
        val c = com.reeflive.core.units.UnitConverter
        assertEquals(79.2, c.convert(17.9, ParamUnit.PPM_NO3_N, ParamUnit.PPM), 0.2)
        assertEquals(0.215, c.convert(0.07, ParamUnit.PPM_PO4_P, ParamUnit.PPM), 0.01)
    }

    @Test
    fun groupsExpandAndKeepTheLivestockId() {
        val rows = listOf(
            Livestock("L1", "t", "amphiprion_ocellaris", "Nemo", sizeCm = 7.0),
            Livestock("L2", "t", "astraea_tecta", "Astrea", quantity = 3),
        )
        val seeds = Reality.seedsFrom(rows) { 2.5 }
        assertEquals(4, seeds.size)
        assertEquals("L1", seeds[0].id)
        assertEquals(listOf("L2", "L2#2", "L2#3"), seeds.drop(1).map { it.id })
        assertEquals("L2", Reality.livestockIdOf("L2#3"))
        val sim = TestTank.sim(seeds)
        assertTrue(sim.organisms.any { it.id == "L1" } && sim.organisms.count { Reality.livestockIdOf(it.id) == "L2" } == 3)
    }

    @Test
    fun lightScheduleMaps() {
        val s = TrackerSchedule("s", "t", "n", listOf(LightPoint(600, List(8) { 0.5 }), LightPoint(1200, List(8) { 0.0 })))
        val l = Reality.lightsFrom(s)
        assertEquals(0.5, l.channels(600 * 60.0)[0], 1e-9)
    }
}
