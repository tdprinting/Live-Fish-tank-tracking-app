package com.reeflive.core.sim

import com.reeflive.core.ai.GiantClamBrain
import com.reeflive.core.custom.economy.PriceBook
import com.reeflive.core.custom.economy.Stores
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rare inverts (docs/research/62_rare_inverts.md). */
class RareInvertsTest {
    private fun sim(id: String, hour: Double, len: Double): Pair<Simulation, Organism> {
        val sim = TestTank.sim(emptyList(), seed = 3L, startHour = hour)
        sim.species.ensure(id)
        sim.addFromSeed(LivestockSeed(id, id, len, id = "x"))
        sim.advance(1.0, behaviorDt = 0.05)
        return sim to sim.organisms.first { it.id == "x" }
    }

    @Test fun croceaSheetAndShop() {
        val id = "tridacna_crocea"
        val db = SpeciesCatalog.fromClasspath(listOf(id))
        assertEquals(BrainKind.GIANT_CLAM, db[id].brainKind)
        assertEquals("models/crocea_clam.glb", db[id].model)
        assertTrue(Stocking.entry(id) != null)
        assertEquals(150 * PriceBook.SCALE, PriceBook.listing(id)!!.price)
        assertTrue(PriceBook.listing(id)!!.level > PriceBook.listing("tridacna_maxima")!!.level)
        val (sim, _) = sim(id, 16.0, 6.0)
        assertTrue(Stores.warning(sim, id)!!.contains("rock"))
    }

    /** The boring clam settles on the rock (never the sand), gape up in the light, and starves of light sooner than a maxima. */
    @Test fun croceaBoresIntoBrightRock() {
        val (sim, o) = sim("tridacna_crocea", 16.0, 6.0)
        sim.advance(120.0, behaviorDt = 0.05)
        val sandY = sim.sand.surfaceY(o.pos.x, o.pos.z)
        println("crocea at %s heading %s PAR %.0f sand %.1f".format(o.pos, o.heading, GiantClamBrain.parAt(sim, o), sandY))
        assertTrue("on the rock, not the sand (y ${o.pos.y} vs sand $sandY)", o.pos.y - 0.5 * o.lengthCm > sandY + 1.0)
        assertTrue("gape up (${o.heading})", o.heading.y > 0.55)
        assertTrue("mantle out by day", o.extension > 0.9)
        assertEquals(GiantClamBrain.PAR_LOW_CROCEA, GiantClamBrain.parLow(o), 0.0)
        // at a day-average of 220 PAR a maxima is fine, a crocea loses its algae
        val m = sim("tridacna_maxima", 16.0, 12.0).second
        o.bleach = 0.0; m.bleach = 0.0
        repeat(10) { GiantClamBrain.symbionts(o, 26.0, 220.0, true, 86400.0); GiantClamBrain.symbionts(m, 26.0, 220.0, true, 86400.0) }
        assertTrue("crocea pales (${o.bleach})", o.bleach > 0.02)
        assertEquals("maxima keeps its colour", 0.0, m.bleach, 1e-9)
    }
}
