package com.reeflive.core.sim

import com.reeflive.core.ai.FileClamBrain
import com.reeflive.core.ai.PeppermintShrimpBrain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Peppermint shrimp and flame scallop (docs/research/42_peppermint_shrimp_flame_scallop.md). */
class PeppermintFlameScallopTest {
    private val pep = "lysmata_boggessi"
    private val clam = "ctenoides_scaber"

    private fun sim(id: String, nick: String, cm: Double, startHour: Double = 12.0, seed: Long = 3L): Simulation {
        val sim = TestTank.sim(emptyList(), seed = seed, startHour = startHour)
        sim.species.ensure(id)
        sim.addFromSeed(LivestockSeed(id, nick, cm, id = "a1"))
        return sim
    }

    private fun Simulation.a1() = organisms.first { it.id == "a1" }

    @Test fun sheetsResolveToTheirOwnBrains() {
        val db = SpeciesCatalog.fromClasspath(listOf(pep, clam))
        assertEquals(BrainKind.PEPPERMINT_SHRIMP, db[pep].brainKind)
        assertEquals(BrainKind.FILE_CLAM, db[clam].brainKind)
        assertEquals("models/flame_scallop.glb", db[clam].model)
        // a missing sheet still gets a peppermint, not the skunk cleaner of the same genus
        assertEquals(BrainKind.PEPPERMINT_SHRIMP, SpeciesCatalog.fallback(pep).brainKind)
        assertEquals(BrainKind.CLEANER_SHRIMP, BrainKind.byId("lysmata_amboinensis"))
        assertTrue(BrainKind.PEPPERMINT_SHRIMP.isShrimp)
        assertTrue(Stocking.entry(pep) != null && Stocking.entry(clam) != null)
    }

    @Test fun peppermintEatsASmallAiptasiaAtNight() {
        val sim = sim(pep, "Minty", 4.0, startHour = 21.0)
        sim.enqueue(Command.AddAiptasia(3)); sim.advance(1.0)
        val polyps = sim.corals.filter { it.kind == CoralKind.AIPTASIA }
        polyps.forEach { it.size = 0.3 }
        val o = sim.a1()
        o.hunger = 0.9
        val b = sim.agentOf(o)!!.brain as PeppermintShrimpBrain
        // up to three hours of the night in behaviour steps
        var t = 0.0
        while (t < 3 * 3600 && b.aiptasiaEaten == 0) { sim.advance(30.0); t += 30 }
        assertTrue("ate a small aiptasia within three hours (eaten ${b.aiptasiaEaten}, refused ${b.aiptasiaRefused})", b.aiptasiaEaten >= 1)
        assertTrue("a polyp is gone", polyps.any { it.tissue <= 0.01 })
        assertTrue("in the water", sim.rock.inWater(o.pos, 0.0))
    }

    @Test fun flameScallopWedgesIntoRockAndOpens() {
        val sim = sim(clam, "Flame", 6.0)
        sim.advance(60.0)
        val o = sim.a1()
        assertEquals(7, o.toPose().motionMode)
        assertTrue("sits at the rock: dist ${sim.rock.sdf.dist(o.pos)}", sim.rock.sdf.dist(o.pos) < 0.6 * o.lengthCm)
        assertTrue("in the water", sim.rock.inWater(o.pos, 0.0))
        assertTrue("valves open to feed: gape ${o.mouth}", o.mouth > 0.3)
    }

    @Test fun flameScallopSwimsWhenPestered() {
        val sim = sim(clam, "Flame", 6.0)
        sim.advance(60.0)
        val o = sim.a1()
        val b = sim.agentOf(o)!!.brain as FileClamBrain
        sim.addFromSeed(LivestockSeed("stenopus_hispidus", "Spike", 6.0, id = "cbs"))
        val shrimp = sim.organisms.first { it.id == "cbs" }
        var t = 0.0
        while (t < 120 && b.swims == 0) {
            shrimp.placeAt(o.pos + o.heading * (0.6 * o.lengthCm), shrimp.heading)
            sim.advance(0.5); t += 0.5
        }
        assertTrue("clapped away from the shrimp", b.swims >= 1)
        sim.advance(60.0)
        assertTrue("stays in the water", sim.rock.inWater(o.pos, 0.0))
    }

    @Test fun flameScallopStarvesWithoutPhytoplankton() {
        fun run(dose: Boolean): Organism {
            val sim = sim(clam, "Flame", 6.0, seed = 9L)
            val o = sim.a1()
            o.hunger = 0.3
            repeat(30) {
                if (dose) sim.water.micro.apply(MicroAction.DOSE_PHYTO)
                repeat(24) { sim.advanceAbstract(3600.0) }
            }
            return o
        }
        val starved = run(false)
        val fed = run(true)
        println("flame scallop after 30 d: no phyto hunger ${starved.hunger} cond ${starved.condition}; dosed hunger ${fed.hunger} cond ${fed.condition}")
        assertTrue("without phyto it goes hungry", starved.hunger > 0.6)
        assertTrue("without phyto it loses condition", starved.condition < fed.condition - 0.1)
        assertTrue("daily phyto keeps it fed", fed.hunger < 0.5)
    }
}
