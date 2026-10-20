package com.reeflive.core.sim

import com.reeflive.core.geom.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Touch controls: tapping the gear opens a pop-out menu that fits what the gear needs now, and food aimed at a spot in
 * the tank goes in right there (floating food on the surface above it, the rest just above the spot).
 */
class TankSpotsTest {
    private val water = WaterState()

    @Test
    fun aFullSkimmerCupOffersEmptyingFirst() {
        val m = TankSpots.menu(TankSpot.SKIMMER, water, CareState(skimmerCup = 0.92f), lightsOn = true, editable = true)
        assertTrue(m.warn)
        assertTrue(m.status, m.status.contains("92 %"))
        val first = m.options.first()
        assertEquals("empty_cup", first.id)
        assertTrue(first.urgent)
        assertEquals(TankDoctor.Kind.EMPTY_SKIMMER, first.fix?.kind)
        assertTrue(first.fix!!.commands.contains(Command.Maintain(MaintenanceTask.EMPTY_SKIMMER)))
    }

    @Test
    fun watchModeLeavesGearSwitchesOut() {
        for (spot in TankSpot.entries) {
            val m = TankSpots.menu(spot, water, CareState(), lightsOn = true, editable = false)
            assertTrue("${spot.name}: ${m.options}", m.options.none { it.gear })
            assertTrue(m.status.isNotBlank())
        }
        // upkeep still works on the real tank
        assertTrue(TankSpots.menu(TankSpot.SKIMMER, water, CareState(), true, editable = false).options.any { it.id == "empty_cup" })
    }

    @Test
    fun everySpotOffersSomethingOnAnEditableTank() {
        for (spot in TankSpot.entries) {
            val m = TankSpots.menu(spot, water, CareState(), lightsOn = false, editable = true)
            assertTrue(spot.name, m.options.isNotEmpty())
            assertEquals("option ids are distinct", m.options.size, m.options.map { it.id }.toSet().size)
        }
    }

    @Test
    fun aBrokenHeaterOffersTheSwapAndStoppedPumpsTurnBackOn() {
        val h = TankSpots.menu(TankSpot.HEATER, water, CareState(heaterWorking = false), true, editable = true)
        assertTrue(h.warn)
        assertEquals("heater_fix", h.options.single().id)
        val p = TankSpots.menu(TankSpot.PUMPS, water.copy().also { it.flowOn = false }, CareState(), true, editable = false)
        assertEquals(TankDoctor.Kind.PUMPS, p.options.single().fix?.kind)
        assertFalse(TankSpots.menu(TankSpot.PUMPS, water, CareState(), true, editable = true).warn)
    }

    private fun sim(): Simulation {
        val rock = RockData.ofSpheres(listOf(Vec3(100.0, 10.0, 15.0) to 5.0))
        return Simulation(TankConfig(seed = 3L), TestTank.catalog, rock, 3L, 10 * 3600.0, WaterState(), emptyList())
    }

    @Test
    fun targetFedFoodGoesInWhereItWasAimed() {
        val s = sim()
        val aim = Vec3(20.0, 6.0, 30.0)
        s.enqueue(Command.Feed(FoodKind.PELLET, 1.0, aim, target = true))
        s.advance(0.1)
        val ps = s.food.particles
        assertTrue(ps.isNotEmpty())
        val cx = ps.sumOf { it.pos.x } / ps.size; val cz = ps.sumOf { it.pos.z } / ps.size; val cy = ps.sumOf { it.pos.y } / ps.size
        assertEquals("over the aimed spot (x)", aim.x, cx, 2.5)
        assertEquals("over the aimed spot (z)", aim.z, cz, 2.5)
        assertTrue("let go just above it, not at the surface: $cy", cy < aim.y + Simulation.TARGET_LIFT_CM + 2.0)
    }

    @Test
    fun floatingFoodLandsOnTheSurfaceAboveTheAim() {
        val s = sim()
        val aim = Vec3(90.0, 8.0, 10.0)
        val at = s.targetRelease(FoodKind.FLAKE, aim)
        assertEquals(aim.x, at.x, 1e-9); assertEquals(aim.z, at.z, 1e-9)
        assertTrue(at.y > s.rock.waterLevelCm - 1)
        val sunk = s.targetRelease(FoodKind.MYSIS, Vec3(90.0, s.rock.waterLevelCm - 0.5, 10.0))
        assertTrue("never above the release band under the surface", sunk.y <= s.rock.waterLevelCm - 2.5 + 1e-9)
    }
}
