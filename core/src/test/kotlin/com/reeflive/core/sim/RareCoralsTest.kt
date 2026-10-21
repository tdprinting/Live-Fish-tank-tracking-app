package com.reeflive.core.sim

import com.reeflive.core.custom.economy.PriceBook
import com.reeflive.core.geom.AnalyticSdf
import com.reeflive.core.geom.Ellipsoid
import com.reeflive.core.geom.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rare corals (docs/research/61_rare_corals.md): gold torch, bounce mushroom, scolymia. */
class RareCoralsTest {
    private val rock = RockData(AnalyticSdf(listOf(Ellipsoid(Vec3(60.0, 3.0, 16.0), Vec3(40.0, 18.0, 14.0)))), AnchorSet(emptyList()))

    private fun sp(id: String): SimSpecies { TestTank.catalog.ensure(id); return TestTank.catalog[id] }

    private fun coral(id: String, speciesId: String, near: Vec3): Coral {
        val p = rock.sdf.project(near, 0.2)
        return Coral(id, sp(speciesId), id, "", p, rock.sdf.gradient(p), 0.0)
    }

    @Test
    fun sheetsKindsAndShop() {
        assertEquals(CoralKind.EUPHYLLIA, CoralKind.resolve("euphyllia_glabrescens_gold"))
        assertEquals(CoralKind.LPS, CoralKind.resolve("homophyllia_australis"))
        assertEquals(CoralKind.MUSHROOM, CoralKind.resolve("rhodactis_inchoata"))
        for ((id, level, usd) in listOf(Triple("euphyllia_glabrescens_gold", 6, 250), Triple("homophyllia_australis", 7, 300), Triple("rhodactis_inchoata", 8, 250))) {
            val s = sp(id)
            assertTrue(id, s.common.isNotBlank())
            assertTrue("$id is a loose frag", s.fragColony)
            assertNotNull("in Add life", Stocking.entry(id))
            val l = PriceBook.listing(id)!!
            assertEquals(id, level, l.level)
            assertEquals(id, usd * PriceBook.SCALE, l.price)
        }
        // the gold torch stings as hard as a torch; the scolymia has a short reach
        assertEquals(StonyCorals.sting(sp("euphyllia_glabrescens")), StonyCorals.sting(sp("euphyllia_glabrescens_gold")))
        assertTrue(StonyCorals.sting(sp("homophyllia_australis"))!!.sweeperCm <= 8.0)
        assertEquals("SAND", sp("homophyllia_australis").coral!!.ground)
    }

    /** A gold torch and a plain torch side by side never fight: every Euphyllia tolerates the others. */
    @Test
    fun goldTorchToleratesOtherTorches() {
        val gold = coral("gold", "euphyllia_glabrescens_gold", Vec3(50.0, 25.0, 16.0))
        val torch = coral("torch", "euphyllia_glabrescens", Vec3(62.0, 25.0, 16.0))
        assertTrue(StonyCorals.tolerates(gold, torch) && StonyCorals.tolerates(torch, gold))
    }

    /** The scolymia opens its feeding tentacles after dark, relies on meaty food more than a duncan, and hates strong light. */
    @Test
    fun scolymiaFeedsAtNightAndWantsLowLight() {
        val ws = WaterState()
        val s = coral("scoly", "homophyllia_australis", Vec3(60.0, 25.0, 16.0))
        repeat(4 * 60) { CoralSim.step(s, ws, 0.0, null, null, 1.0, it * 60.0, ArrayList()) }
        assertTrue("night feeding tentacles: ${s.tentacles}", s.tentacles > 0.8)
        val scoly = CoralHealth.profile(sp("homophyllia_australis"))
        val duncan = CoralHealth.profile(sp("duncanopsammia_axifuga"))
        assertTrue(scoly.feedNeed > duncan.feedNeed)
        assertTrue(scoly.lightBleach > duncan.lightBleach)
        // a duncan sheet takes light up to 0.75
        assertTrue(sp("homophyllia_australis").coral!!.lightHi < 0.75)
    }

    /** Bounce mushrooms split far more slowly than plain hairy mushrooms and want less light. */
    @Test
    fun bounceIsSlowAndShy() {
        val bounce = CoralHealth.profile(sp("rhodactis_inchoata"))
        val hairy = CoralHealth.profile(sp("rhodactis_sp"))
        assertTrue(bounce.spreadEveryDays >= 2 * hairy.spreadEveryDays)
        assertTrue(bounce.lightBleach > hairy.lightBleach)
        // the hairy mushroom sheet takes light up to 0.55
        assertTrue(sp("rhodactis_inchoata").coral!!.lightHi < 0.55)
    }

    /** Added to the tank, each goes to a free spot of its own like a stony frag, not onto another coral's anchor. */
    @Test
    fun addedToAFreeSpot() {
        val sim = TestTank.sim(DefaultStock.all, seed = 31)
        for (id in listOf("euphyllia_glabrescens_gold", "rhodactis_inchoata", "homophyllia_australis")) {
            sim.species.ensure(id)
            sim.addFromSeed(LivestockSeed(id, id, 0.0, id = id))
            val c = sim.corals.first { it.id == id }
            assertEquals(id, "", c.anchor)
            assertTrue("$id moved off the studio spot", c.pos.distanceTo(StonyCorals.STUDIO) > 1.0)
            val others = sim.corals.filter { it !== c }
            assertTrue("$id has room", others.all { it.pos.distanceTo(c.pos) > 4.0 })
        }
    }
}
