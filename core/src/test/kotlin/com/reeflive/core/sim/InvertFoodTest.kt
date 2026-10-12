package com.reeflive.core.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Research 04 §15 item 9: raw prawn (Nassarius), plankton/phyto (feather duster), mysis (cleaner shrimp). */
class InvertFoodTest {
    private fun sim() = TestTank.sim(DefaultStock.all, seed = 3L)
    private fun first(sim: Simulation, k: BrainKind) = sim.organisms.first { it.brainKind == k }

    @Test
    fun newKindsParseAndClassify() {
        assertEquals(FoodKind.PRAWN, FoodKind.of("prawn"))
        assertEquals(FoodKind.PLANKTON, FoodKind.of("PLANKTON"))
        assertTrue(FoodKind.PLANKTON.suspended && FoodKind.PODS.suspended)
        assertFalse(FoodKind.PRAWN.suspended || FoodKind.MYSIS.suspended)
        // appended after PODS so the renderer's colour table (indexed by ordinal) keeps its old entries
        assertEquals(4, FoodKind.PODS.ordinal); assertEquals(5, FoodKind.PRAWN.ordinal); assertEquals(6, FoodKind.PLANKTON.ordinal)
    }

    @Test
    fun speciesPreferences() {
        val s = sim()
        val nass = first(s, BrainKind.NASSARIUS).species
        val duster = first(s, BrainKind.FEATHER_DUSTER).species
        val shrimp = first(s, BrainKind.CLEANER_SHRIMP).species
        val clown = first(s, BrainKind.CLOWN).species
        assertTrue(nass.eats(FoodKind.PRAWN))
        assertTrue(nass.foodPreference(FoodKind.PRAWN) > nass.foodPreference(FoodKind.MYSIS))
        assertTrue(duster.eats(FoodKind.PLANKTON))
        assertFalse(duster.eats(FoodKind.PRAWN))
        assertTrue(shrimp.foodPreference(FoodKind.MYSIS) > shrimp.foodPreference(FoodKind.FLAKE))
        assertEquals(0.0, clown.dietMatch(FoodKind.PRAWN), 0.0)
        assertEquals(0.0, clown.dietMatch(FoodKind.PLANKTON), 0.0)
    }

    @Test
    fun prawnFeedsNassariusAndPlanktonFeedsDuster() {
        val s = sim()
        s.behaviorEnabled = false
        val nass = s.organisms.filter { it.brainKind == BrainKind.NASSARIUS }
        val duster = first(s, BrainKind.FEATHER_DUSTER)
        nass.forEach { it.hunger = 0.9 }; duster.hunger = 0.9
        s.enqueue(Command.Feed(FoodKind.PRAWN, 1.0))
        s.advance(5.0, fullBehavior = false)
        assertTrue("nassarius ate prawn: ${nass.map { it.hunger }}", nass.any { it.hunger < 0.9 })
        assertEquals("duster ignores prawn", 0.9, duster.hunger, 1e-9)
        s.enqueue(Command.Feed(FoodKind.PLANKTON, 0.2))
        s.advance(5.0, fullBehavior = false)
        assertTrue("duster ate plankton: ${duster.hunger}", duster.hunger < 0.9)
    }

    @Test
    fun planktonDriftsPrawnSettles() {
        val s = sim()
        s.food.spawn(FoodKind.PLANKTON, 0.05, com.reeflive.core.geom.Vec3(60.0, 30.0, 20.0), s.simTimeSec)
        s.food.spawn(FoodKind.PRAWN, 0.5, com.reeflive.core.geom.Vec3(60.0, 30.0, 20.0), s.simTimeSec)
        s.advance(120.0)
        val live = s.food.particles.filter { !it.eaten }
        assertTrue(live.filter { it.kind == FoodKind.PLANKTON }.none { it.settled })
        assertTrue(live.filter { it.kind == FoodKind.PRAWN }.all { it.settled || it.pos.y < 30.0 })
    }
}
