package com.reeflive.core.sim

import com.reeflive.core.ai.LeopardWrasseBrain
import com.reeflive.core.seed.SeedData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Blue star leopard wrasse (research video fish_leopard_wrasse) and the owner's two added fish. */
class LeopardWrasseTest {
    private val leopard = "macropharyngodon_bipartitus"
    private val diamond = "valenciennea_puellaris"

    private fun leopardSim(startHour: Double, config: TankConfig = TankConfig(seed = 5L)): Pair<Simulation, LeopardWrasseBrain> {
        val sim = TestTank.sim(DefaultStock.corals, seed = 5L, startHour = startHour, config = config)
        sim.species.ensure(leopard)
        val o = sim.addFromSeed(LivestockSeed(leopard, "Leopard", 8.5, id = "lw1"))
        return sim to (sim.agentOf(o)!!.brain as LeopardWrasseBrain)
    }

    @Test fun ownersTankHasBothFish() {
        val ids = DefaultStock.fish.map { it.speciesId }
        assertTrue(leopard in ids && diamond in ids)
        assertTrue(Presets.realityDefault.livestock.any { it.speciesId == leopard })
        assertTrue(Presets.realityDefault.livestock.any { it.speciesId == diamond })
        val seed = SeedData.loadFile().livestock.map { it.speciesId }
        assertTrue("seed tank lists both: $seed", leopard in seed && diamond in seed)
        val sim = Presets.create(Presets.realityDefault, TestTank.catalog, TestTank.rock)
        assertEquals(1, sim.organisms.count { it.brainKind == BrainKind.LEOPARD_WRASSE })
        assertEquals(1, sim.organisms.count { it.brainKind == BrainKind.DIAMOND_GOBY })
    }

    @Test fun speciesSheetResolvesBrainModelAndMotion() {
        val cat = SpeciesCatalog.fromClasspath(listOf(leopard))
        val s = cat[leopard]
        assertEquals(BrainKind.LEOPARD_WRASSE, s.brainKind)
        assertEquals("models/leopard_wrasse.glb", s.model)
        assertTrue(s.maxLengthCm in 12.0..14.0)
        assertEquals(Gait.LABRIFORM, MotionProfiles.of(BrainKind.LEOPARD_WRASSE).gait)
        assertTrue(Stocking.entry(leopard) != null)
        assertTrue(compatibilityWarnings(leopard, listOf("cirrhitichthys_falco"), 400.0).any { it.code == "leopard_wrasse_bullies" })
        assertTrue(compatibilityWarnings(leopard, listOf(leopard), 400.0).any { it.code == "leopard_wrasse_pair" })
    }

    /** Daytime: hovers in the coral and picks every few seconds (10-20 a minute while foraging). */
    @Test fun picksPodsMostOfTheDay() {
        val (sim, b) = leopardSim(12.0)
        sim.advance(600.0, behaviorDt = 0.1)
        val perMin = b.picks / 10.0
        assertTrue("picks per minute $perMin", perMin in 4.0..20.0)
        assertTrue(b.lastYield > 0)
        assertFalse(b.buried)
        assertTrue(sim.organisms.first().visible)
    }

    /** Evening: dives head-first into open sand, leaving a crater with a rim, and is out of sight for the night. */
    @Test fun divesIntoTheSandAtDusk() {
        val (sim, b) = leopardSim(19.0)
        val o = sim.organisms.first()
        sim.advance(1.5 * 3600, behaviorDt = 0.1)
        assertTrue("buried at 20:30", b.buried)
        assertFalse(o.visible)
        assertTrue(o.sleeping)
        assertEquals(1, b.dives)
        assertTrue(sim.sand.dives >= 1)
        val spot = b.sleepSpot
        assertTrue("crater over the fish", sim.sand.cellHeight(spot.x, spot.z) < -0.05)
        assertTrue("buried under the sand", o.pos.y < sim.sand.surfaceY(spot.x, spot.z))
    }

    /** Morning: stays under the sand for 15-60 min after the lights come on, then pushes out. */
    @Test fun emergesWithALagAfterLightsOn() {
        val (sim, b) = leopardSim(7.5)
        var lightsOn = -1.0
        var seenAt = -1.0
        val o = sim.organisms.first()
        sim.advance(3.0 * 3600, behaviorDt = 0.1) {
            if (lightsOn < 0 && sim.perception.lightsOnAt > 0) lightsOn = sim.perception.lightsOnAt
            if (lightsOn > 0 && seenAt < 0 && o.visible && !b.buried) seenAt = sim.simTimeSec
        }
        assertTrue("dived before dawn", b.dives >= 1)
        assertTrue("lights came on", lightsOn > 0)
        val lag = seenAt - lightsOn
        assertTrue("emerged $lag s after lights-on", lag in LeopardWrasseBrain.EMERGE_LAG_MIN_S..LeopardWrasseBrain.EMERGE_LAG_MAX_S + 60.0)
        assertEquals(1, b.emergences)
        assertTrue(sim.sand.emergences >= 1)
    }

    /** A newly added leopard wrasse goes into the sand and stays there (Fenner: "for days"). */
    @Test fun newcomerHidesInTheSandForDays() {
        val sim = TestTank.sim(DefaultStock.corals, seed = 6L, startHour = 11.0)
        sim.species.ensure(leopard)
        sim.enqueue(Command.AddOrganism(leopard, 8.0, "New leopard"))
        sim.advance(1800.0, behaviorDt = 0.1)
        val o = sim.organisms.first { it.brainKind == BrainKind.LEOPARD_WRASSE }
        val b = sim.agentOf(o)!!.brain as LeopardWrasseBrain
        assertTrue("buried after 30 min", b.buried && !o.visible)
        sim.advance(1800.0, behaviorDt = 0.1)
        assertTrue("still buried an hour in, by day", b.buried && !o.visible)
        assertEquals(0, b.emergences)
    }

    @Test fun picksDependOnTheTanksPods() {
        val (sim, _) = leopardSim(12.0)
        sim.advanceAbstract(3600.0)
        val mature = sim.water.micro.rockPreyIndex()
        sim.water.micro.dryRock()
        assertTrue("mature tank index $mature", mature in 0.4..1.6)
        assertTrue(sim.water.micro.rockPreyIndex() < 0.1 * mature)
        assertTrue(PodPredator.of(sim.organisms.first()) == PodPredator.LEOPARD_WRASSE)
    }

    @Test fun sandDiveMovesSandWithoutMakingIt() {
        val s = SandBed(121.9, 33.0, 3.0, 1L)
        val before = s.meanHeight()
        s.sandDive(60.0, 20.0, 1.0, 0.0, 9.0)
        assertTrue(s.cellHeight(60.0, 20.0) < 0)
        assertTrue("rim ahead", s.cellHeight(63.5, 20.0) > 0)
        s.sandEmerge(60.0, 20.0, 9.0)
        assertEquals(before, s.meanHeight(), 1e-4)
        assertEquals(1L, s.dives); assertEquals(1L, s.emergences)
    }
}
