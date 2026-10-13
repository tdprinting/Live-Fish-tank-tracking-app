package com.reeflive.core.sim

import com.reeflive.core.geom.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The foods added for the live-mode food picker (SIMULATION §4.6 table): brine shrimp, krill, floating pellets,
 * an algae wafer, coral food powder and cyclops. Checks the diet wiring, the particle physics each one needs
 * (floats, sinks and lasts, drifts) and that corals catch the fine foods.
 */
class NewFoodsTest {
    private fun sim() = TestTank.sim(DefaultStock.all, seed = 11L)
    private fun first(sim: Simulation, k: BrainKind) = sim.organisms.first { it.brainKind == k }

    @Test
    fun newKindsAreAppendedAndClassified() {
        // appended after PLANKTON: the renderer's colour / size tables are indexed by ordinal
        assertEquals(7, FoodKind.BRINE.ordinal); assertEquals(8, FoodKind.KRILL.ordinal); assertEquals(9, FoodKind.FLOATING_PELLET.ordinal)
        assertEquals(10, FoodKind.ALGAE_WAFER.ordinal); assertEquals(11, FoodKind.CORAL_FOOD.ordinal); assertEquals(12, FoodKind.CYCLOPS.ordinal)
        assertEquals(FoodKind.BRINE, FoodKind.of("brine"))
        assertEquals(FoodKind.FLOATING_PELLET, FoodKind.of("floating_pellet"))
        assertTrue(FoodKind.CORAL_FOOD.suspended && FoodKind.CYCLOPS.suspended)
        assertFalse(FoodKind.BRINE.suspended || FoodKind.KRILL.suspended || FoodKind.ALGAE_WAFER.suspended)
        assertTrue(FoodKind.FLAKE.floats && FoodKind.FLOATING_PELLET.floats)
        assertFalse(FoodKind.PELLET.floats || FoodKind.MYSIS.floats)
        assertTrue(FoodKind.ALGAE_WAFER.lifeSeconds > FoodKind.FLAKE.lifeSeconds)
        assertTrue(FoodKind.CORAL_FOOD.coralCatchable && FoodKind.CYCLOPS.coralCatchable && !FoodKind.KRILL.coralCatchable)
        // frozen foods are mostly water: a gram of brine shrimp loads the water far less than a gram of pellets
        assertTrue(FoodKind.BRINE.nutrientFactor < 0.5 * FoodKind.PELLET.nutrientFactor)
        assertTrue(FoodKind.KRILL.nutrientFactor < FoodKind.FLOATING_PELLET.nutrientFactor)
        FoodKind.entries.forEach { k ->
            assertTrue(k.name, k.label.isNotBlank() && k.blurb.isNotBlank() && k.typicalDoseGrams > 0)
        }
    }

    @Test
    fun speciesTakeTheRightNewFoods() {
        // sheets as shipped in core/src/main/resources/species (the test fixtures carry older, shorter diets)
        val clown = SimSpecies("amphiprion_ocellaris", diet = listOf("FLAKE", "PELLET", "MYSIS", "BRINE", "ALGAE"))
        val hawk = SimSpecies("cirrhitichthys_falco", diet = listOf("MYSIS", "KRILL", "BRINE", "PELLET"))
        val cardinal = SimSpecies("sphaeramia_nematoptera", diet = listOf("MYSIS", "BRINE", "FLAKE", "PELLET"))
        val duster = SimSpecies("sabellastarte_sp", kind = "INVERT", diet = listOf("PLANKTON", "PHYTOPLANKTON"))
        val nass = SimSpecies("nassarius_sp", kind = "INVERT", diet = listOf("DETRITUS", "MEATY", "PRAWN", "FLAKE"))
        val s = sim()
        val tang = first(s, BrainKind.TANG).species
        val hermit = first(s, BrainKind.HERMIT).species
        val snail = first(s, BrainKind.ASTREA).species
        // listed by name
        assertEquals(1.0, clown.dietMatch(FoodKind.BRINE), 0.0)
        assertEquals(1.0, hawk.dietMatch(FoodKind.KRILL), 0.0)
        assertEquals(1.0, clown.dietMatch(FoodKind.FLOATING_PELLET), 0.0)
        assertTrue(hawk.foodPreference(FoodKind.KRILL) > hawk.foodPreference(FoodKind.MYSIS))
        // grazers crowd a wafer; omnivores only nibble it; the duster ignores it
        assertEquals(1.0, tang.dietMatch(FoodKind.ALGAE_WAFER), 0.0)
        assertEquals(1.0, hermit.dietMatch(FoodKind.ALGAE_WAFER), 0.0)
        assertEquals(1.0, snail.dietMatch(FoodKind.ALGAE_WAFER), 0.0)
        assertTrue(clown.dietMatch(FoodKind.ALGAE_WAFER) in 0.1..0.5)
        assertEquals(0.0, duster.dietMatch(FoodKind.ALGAE_WAFER), 0.0)
        // fine foods for the filter feeders; the duster still ignores chunks
        assertEquals(1.0, duster.dietMatch(FoodKind.CORAL_FOOD), 0.0)
        assertEquals(1.0, duster.dietMatch(FoodKind.CYCLOPS), 0.0)
        assertEquals(0.0, duster.dietMatch(FoodKind.KRILL), 0.0)
        // "MEATY" scavengers take krill like prawn; mysis eaters take brine shrimp and pick at krill and cyclops
        assertEquals(1.0, nass.dietMatch(FoodKind.KRILL), 0.0)
        assertTrue(cardinal.dietMatch(FoodKind.CYCLOPS) > 0.5)
        assertEquals(1.0, cardinal.dietMatch(FoodKind.BRINE), 0.0)
        assertTrue(cardinal.dietMatch(FoodKind.KRILL) in 0.3..0.8)
        // an older sheet without BRINE still takes it as a near-mysis food
        val oldClown = first(s, BrainKind.CLOWN).species
        assertTrue(oldClown.dietMatch(FoodKind.BRINE) >= 0.8)
    }

    @Test
    fun floatingPelletsFloatThenSink() {
        val s = sim()
        val top = s.rock.waterLevelCm
        s.food.spawn(FoodKind.FLOATING_PELLET, 1.0, s.feedPoint(null), s.simTimeSec)
        s.advance(60.0)
        // a few get pushed onto rock that reaches the surface near the feeding ring and stick there, like flakes do;
        // the rest must still be riding the surface
        val afterMinute = s.food.particles.filter { !it.eaten && it.kind == FoodKind.FLOATING_PELLET && !it.settled }
        assertTrue("most pellets still afloat: ${afterMinute.size}", afterMinute.size >= 8)
        assertTrue("all still at the surface: " + afterMinute.map { "%.1f".format(it.pos.y) }, afterMinute.all { it.pos.y > top - 1.5 })
        s.advance(360.0)
        val later = s.food.particles.filter { !it.eaten && it.kind == FoodKind.FLOATING_PELLET }
        assertTrue("pellets sank after the float time: ${later.map { it.pos.y }}", later.any { it.pos.y < top - 5 } || later.any { it.settled })
    }

    @Test
    fun algaeWaferSettlesAndOutlastsFlake() {
        val s = sim()
        s.behaviorEnabled = false
        s.food.spawn(FoodKind.ALGAE_WAFER, 0.5, Vec3(60.0, 30.0, 20.0), s.simTimeSec)
        s.food.spawn(FoodKind.FLAKE, 0.3, Vec3(60.0, 30.0, 20.0), s.simTimeSec)
        s.advance(60.0, fullBehavior = false)
        val wafer = s.food.particles.filter { !it.eaten && it.kind == FoodKind.ALGAE_WAFER }
        assertTrue("wafer chunks spawned", wafer.isNotEmpty())
        assertTrue("wafer chunks settled within a minute", wafer.all { it.settled })
        s.advance(25 * 60.0, fullBehavior = false)   // past the 20-min flake expiry
        assertTrue(s.food.particles.none { it.kind == FoodKind.FLAKE })
        assertTrue(s.food.particles.any { it.kind == FoodKind.ALGAE_WAFER })
    }

    @Test
    fun coralFoodDriftsAndCoralsCatchIt() {
        val s = sim()
        assertTrue("test tank has corals", s.corals.isNotEmpty())
        val coral = s.corals.first { it.kind != CoralKind.MACRO }
        s.food.spawn(FoodKind.CORAL_FOOD, 0.3, coral.pos + Vec3(0.0, 2.0, 0.0), s.simTimeSec)
        s.food.spawn(FoodKind.CYCLOPS, 0.3, coral.pos + Vec3(0.0, 2.0, 0.0), s.simTimeSec)
        s.advance(30.0)
        val live = s.food.particles.filter { !it.eaten && it.kind.coralCatchable }
        assertTrue(live.none { it.settled })
        assertTrue("coral caught some: fed=${coral.fed}", coral.fed > 0.0)
    }

    @Test
    fun abstractFeedingFeedsTheRightMouths() {
        val s = sim()
        s.behaviorEnabled = false
        val clown = first(s, BrainKind.CLOWN)
        val tang = first(s, BrainKind.TANG)
        val duster = first(s, BrainKind.FEATHER_DUSTER)
        s.organisms.forEach { it.hunger = 0.9 }
        s.enqueue(Command.Feed(FoodKind.BRINE, 1.0))
        s.advance(5.0, fullBehavior = false)
        assertTrue("clown ate brine shrimp: ${clown.hunger}", clown.hunger < 0.9)
        assertEquals("duster ignores brine shrimp", 0.9, duster.hunger, 1e-9)
        tang.hunger = 0.9
        s.enqueue(Command.Feed(FoodKind.ALGAE_WAFER, 1.0))
        s.advance(5.0, fullBehavior = false)
        assertTrue("tang ate the wafer: ${tang.hunger}", tang.hunger < 0.9)
        val fedBefore = s.corals.map { it.fed }
        s.enqueue(Command.Feed(FoodKind.CORAL_FOOD, 0.3))
        s.advance(5.0, fullBehavior = false)
        assertTrue("corals fed by the dosed cloud", s.corals.zip(fedBefore).any { (c, before) -> c.fed > before })
    }
}
