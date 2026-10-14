package com.reeflive.core.seed

import com.reeflive.core.model.ReefType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedDataTest {
    @Test
    fun parsesTeesTank() {
        val seed = SeedData.loadFile()
        assertEquals("Tee's Reef", seed.tank.name)
        assertEquals(60.0, seed.tank.displayGallons, 0.0)
        assertEquals(ReefType.SOFT, seed.tank.reefType)
        assertEquals(35, seed.livestock.size)  // + 4 corals from the 2026-10-13 photos, + leopard wrasse and diamond goby
        assertEquals(8, seed.equipment.size)
        assertEquals(12, seed.tasks.size)
        assertTrue(seed.livestock.none { it.speciesId == "aiptasia_sp" })
        assertTrue(seed.livestock.single { it.nickname == "Hawk" }.verified)
        assertFalse(seed.livestock.single { it.nickname == "Brittle star" }.verified)
    }

    @Test
    fun materialisesWithLinksAndLights() {
        val b = SeedData.materialise(SeedData.loadFile(), nowMs = 1_000L)
        assertEquals(SeedData.DEFAULT_TANK_ID, b.tank.id)
        assertEquals(35, b.livestock.size)
        assertEquals(b.livestock.size, b.livestock.map { it.id }.toSet().size)
        val sock = b.tasks.single { it.title.startsWith("Filter sock") }
        assertEquals(b.equipment.single { it.name == "HOB filter / skimmer" }.id, sock.equipmentId)
        val nori = b.tasks.single { it.title == "Feed nori" }
        assertEquals(18, nori.reminderHour)
        assertTrue(b.lightSchedule.points.size >= 6)
        assertTrue(b.lightSchedule.intensityAt(14 * 60.0) > 0.4)
        assertTrue(b.lightSchedule.intensityAt(2 * 60.0) < 0.03)
    }
}
