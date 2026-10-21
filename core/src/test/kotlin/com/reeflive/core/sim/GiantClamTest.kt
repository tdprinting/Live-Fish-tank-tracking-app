package com.reeflive.core.sim

import com.reeflive.core.ai.GiantClamBrain
import com.reeflive.core.custom.economy.PriceBook
import com.reeflive.core.custom.economy.Stores
import com.reeflive.core.geom.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Maxima clam (docs/research/45_clam_crab_star_cucumber.md §1). */
class GiantClamTest {
    private val id = "tridacna_maxima"

    private fun sim(hour: Double, len: Double = 12.0): Pair<Simulation, Organism> {
        val sim = TestTank.sim(emptyList(), seed = 3L, startHour = hour)
        sim.species.ensure(id)
        sim.addFromSeed(LivestockSeed(id, "Max", len, id = "c"))
        sim.advance(1.0, behaviorDt = 0.05)
        return sim to sim.organisms.first { it.id == "c" }
    }

    @Test fun sheetRigAndStore() {
        val db = SpeciesCatalog.fromClasspath(listOf(id))
        assertEquals(BrainKind.GIANT_CLAM, db[id].brainKind)
        assertEquals(BrainKind.GIANT_CLAM, SpeciesCatalog.fallback(id).brainKind)
        assertEquals("models/maxima_clam.glb", db[id].model)
        assertTrue(LimbRig.forKind(BrainKind.GIANT_CLAM, 1L) is ClamRig)
        assertTrue(Stocking.entry(id) != null)
        assertEquals(110 * PriceBook.SCALE, PriceBook.listing(id)!!.price)
        val (sim, _) = sim(16.0)
        assertTrue(Stores.warning(sim, id)!!.contains("light"))
    }

    @Test fun sitsGapeUpInBrightLightWithItsShellOnTheRockNotInIt() {
        val (sim, o) = sim(16.0)
        sim.advance(120.0, behaviorDt = 0.05)
        val par = GiantClamBrain.parAt(sim, o)
        println("clam at %s, heading %s, PAR %.0f".format(o.pos, o.heading, par))
        assertTrue("under water (${o.pos.y + 0.1 * o.lengthCm} vs ${sim.rock.waterLevelCm})", o.pos.y + 0.1 * o.lengthCm < sim.rock.waterLevelCm - 1.0)
        assertTrue("gape up (${o.heading})", o.heading.y > 0.75)
        assertTrue("in good light ($par PAR)", par > GiantClamBrain.PAR_LOW)
        // the shell: hinge on the surface, both ends and the gape clear of rock and sand
        val L = o.lengthCm
        val side = o.heading.cross(o.up).normalized(Vec3.X)
        for (p in listOf(o.pos, o.pos + side * (0.4 * L) - o.heading * (0.25 * L), o.pos - side * (0.4 * L) - o.heading * (0.25 * L))) {
            assertTrue("shell clear of the rock at $p (${sim.rock.envDist(p)})", sim.rock.envDist(p) > -0.3)
        }
        assertTrue("hinge on the surface (${sim.rock.envDist(o.pos - o.heading * (0.5 * L))})", sim.rock.envDist(o.pos - o.heading * (0.5 * L)) < 1.5)
        assertTrue("mantle spread by day (${o.extension})", o.extension > 0.9)
    }

    @Test fun snapsShutToAShadowAndOpensAgainWithinAFewMinutes() {
        val (sim, o) = sim(16.0)
        sim.advance(120.0, behaviorDt = 0.05)
        val b = sim.agentOf(o)!!.brain as GiantClamBrain
        sim.agentOf(o)!!.let { a -> a.brain.onShadow(a.ctx, o.pos + Vec3(0.0, 10.0, 0.0)) }
        var shut = 1.0
        sim.advance(3.0, behaviorDt = 0.05) { shut = minOf(shut, o.extension) }
        assertTrue("mantle in at once ($shut)", shut < 0.2)
        assertEquals(1, b.closes)
        sim.advance(180.0, behaviorDt = 0.05)
        assertTrue("open again (${o.extension})", o.extension > 0.85)
    }

    @Test fun livesOnLightAndBleachesInHeat() {
        val (sim, o) = sim(9.0)
        o.hunger = 0.3
        sim.advanceAbstract(3 * 86400.0)
        println("clam after 3 days: hunger %.2f bleach %.2f".format(o.hunger, o.bleach))
        assertTrue("fed by its algae (${o.hunger})", o.hunger < 0.35)
        assertTrue("healthy colour (${o.bleach})", o.bleach < 0.05)
        // held hot against the heater and chiller (like BubbleTipAnemoneTest)
        repeat(7 * 24) { sim.water.s.tempC = 31.5; sim.advanceAbstract(3600.0) }
        println("clam after 7 hot days: hunger %.2f bleach %.2f desat %.2f".format(o.hunger, o.bleach, o.desat))
        assertTrue("bleached in the heat (${o.bleach})", o.bleach > 0.4)
        assertTrue("shows pale (${o.desat})", o.desat > 0.4)
    }

    @Test fun aDimSpotStarvesItsAlgae() {
        val o = sim(16.0).second
        GiantClamBrain.symbionts(o, 26.0, 60.0, true, 10 * 86400.0 * 0.4)
        assertTrue("paling in the dark (${o.bleach})", o.bleach > 0.2)
        GiantClamBrain.symbionts(o, 26.0, 350.0, true, 20 * 86400.0)
        assertTrue("recovers in good light (${o.bleach})", o.bleach < 0.1)
    }
}
