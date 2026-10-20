package com.reeflive.core.sim

import com.reeflive.core.ai.ConchBrain
import com.reeflive.core.ai.TrochusBrain
import com.reeflive.core.ai.UrchinBrain
import com.reeflive.core.geom.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cleanup crew pack: trochus snail, fighting conch, tuxedo urchin (docs/research/43_cleanup_crew.md). */
class CleanupCrewTest {
    private val trochus = "tectus_fenestratus"
    private val conch = "strombus_alatus"
    private val urchin = "mespilia_globulus"

    private fun sim(id: String, cm: Double, startHour: Double = 12.0, seed: Long = 5L): Simulation {
        val sim = TestTank.sim(emptyList(), seed = seed, startHour = startHour)
        sim.species.ensure(id)
        sim.addFromSeed(LivestockSeed(id, "Crew", cm, id = "a1"))
        return sim
    }

    private fun Simulation.a1() = organisms.first { it.id == "a1" }

    @Test fun sheetsResolveToTheirOwnBrainsAndModels() {
        val db = SpeciesCatalog.fromClasspath(listOf(trochus, conch, urchin))
        assertEquals(BrainKind.TROCHUS, db[trochus].brainKind)
        assertEquals(BrainKind.CONCH, db[conch].brainKind)
        assertEquals(BrainKind.URCHIN, db[urchin].brainKind)
        assertEquals("models/fighting_conch.glb", db[conch].model)
        for (id in listOf(trochus, conch, urchin)) assertTrue(id, Stocking.entry(id) != null)
        assertTrue(BrainKind.TROCHUS.isSnail && BrainKind.CONCH.isSnail && !BrainKind.URCHIN.isSnail)
    }

    @Test fun trochusGrazesTheGlassAndRightsItself() {
        val sim = sim(trochus, 3.0)
        sim.advance(30.0)
        val o = sim.a1()
        assertEquals(8, o.toPose().motionMode)
        assertEquals(GrazerType.TROCHUS, GrazerType.of(o))
        assertTrue("in the water", sim.rock.inWater(o.pos, 0.0))
        // knock it onto its back on the sand by the front glass: it reaches with its foot and turns over
        o.placeAt(Vec3(0.5 * sim.rock.lengthCm, sim.rock.sandTopCm + 2, sim.rock.widthCm - 2.0), Vec3.X)
        val b = sim.agentOf(o)!!.brain as TrochusBrain
        b.flip(sim.agentOf(o)!!.ctx)
        var t = 0.0
        while (t < 6 * 3600 && b.flipped) { sim.advance(10.0); t += 10 }
        println("trochus righted after ${t / 60} min, ${b.rightingAttempts} attempts")
        assertTrue("righted itself within 6 h (attempts ${b.rightingAttempts})", !b.flipped && b.rightings == 1)
        assertTrue("in the water", sim.rock.inWater(o.pos, 0.0))
    }

    @Test fun trochusPullsIntoItsShellWhenPoked() {
        val sim = sim(trochus, 3.0)
        sim.advance(20.0)
        val o = sim.a1()
        val b = sim.agentOf(o)!!.brain as TrochusBrain
        b.onTap(sim.agentOf(o)!!.ctx, o.pos, 0.5)
        sim.advance(3.0)
        assertTrue("withdrew", b.withdrawals == 1 && o.extension < 0.3)
    }

    @Test fun conchLeapsOverTheSandAndStaysOffTheRock() {
        val sim = sim(conch, 7.0, startHour = 22.0)
        val o = sim.a1()
        o.hunger = 0.8      // hungry: out foraging, not buried
        val b = sim.agentOf(o)!!.brain as ConchBrain
        val start = o.pos
        var maxRock = Double.MAX_VALUE
        for (i in 0 until 600) {
            sim.advance(1.0)
            maxRock = minOf(maxRock, sim.rock.sdf.dist(o.pos))
            assertTrue("in the water", sim.rock.inWater(o.pos, 0.0))
        }
        println("conch: ${b.leaps} leaps, moved ${o.pos.distanceTo(start)} cm, closest rock $maxRock cm")
        assertEquals(8, o.toPose().motionMode)
        assertTrue("leapt around (${b.leaps})", b.leaps >= 20)
        assertTrue("got somewhere", o.pos.distanceTo(start) > 5.0)
        assertTrue("stayed on open sand (rock $maxRock)", maxRock > 1.0)
        assertTrue("near the sand", o.pos.y < sim.sand.surfaceY(o.pos.x, o.pos.z) + o.species.depthRatio * o.lengthCm + 0.5)
    }

    @Test fun conchLeapsAwayFromAPoke() {
        val sim = sim(conch, 7.0, startHour = 22.0)
        val o = sim.a1()
        o.hunger = 0.8
        sim.advance(5.0)
        val b = sim.agentOf(o)!!.brain as ConchBrain
        b.onTap(sim.agentOf(o)!!.ctx, o.pos + Vec3(3.0, 0.0, 0.0), 0.6)
        sim.advance(10.0)
        assertTrue("escaped", b.escapes == 1)
    }

    @Test fun urchinWearsMoreDebrisInBrightLight() {
        fun cover(hour: Double): Double {
            val sim = sim(urchin, 5.0, startHour = hour)
            repeat(180) { sim.advance(20.0) }     // an hour
            val o = sim.a1()
            assertEquals(9, o.toPose().motionMode)
            assertTrue("in the water", sim.rock.inWater(o.pos, 0.0))
            return (sim.agentOf(o)!!.brain as UrchinBrain).cover
        }
        val noon = cover(13.0)
        val night = cover(1.0)
        println("urchin cover: noon $noon, night $night")
        assertTrue("covered at noon ($noon) more than at night ($night)", noon > night + 0.3)
    }

    @Test fun urchinGrazesCoralline() {
        val sim = sim(urchin, 5.0)
        val g = GrazerType.of(sim.a1())
        assertEquals(GrazerType.URCHIN, g)
        assertTrue(g!!.pref[AlgaeGroup.CORALLINE.ordinal] > 0.5)
    }
}
