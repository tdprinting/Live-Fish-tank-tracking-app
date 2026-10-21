package com.reeflive.core.sim

import com.reeflive.core.ai.CardinalBrain
import com.reeflive.core.ai.GrammaBrain
import com.reeflive.core.ai.WatchmanGobyBrain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Popular fish pack, batch 1 (research 50): royal gramma, Banggai cardinalfish, yellow watchman goby. */
class PopularFishBatch1Test {
    private val gramma = "gramma_loreto"
    private val banggai = "pterapogon_kauderni"
    private val watchman = "cryptocentrus_cinctus"

    private fun withFish(id: String, cm: Double, startHour: Double, seed: Long = 7L, n: Int = 1): Pair<Simulation, List<Organism>> {
        val sim = TestTank.sim(DefaultStock.corals, seed = seed, startHour = startHour)
        sim.species.ensure(id)
        val fish = (1..n).map { sim.addFromSeed(LivestockSeed(id, "Fish $it", cm, id = "f$it")) }
        return sim to fish
    }

    @Test fun sheetsResolveBrainsModelsAndShop() {
        val cat = SpeciesCatalog.fromClasspath(listOf(gramma, banggai, watchman))
        assertEquals(BrainKind.GRAMMA, cat[gramma].brainKind)
        assertEquals(BrainKind.CARDINAL, cat[banggai].brainKind)
        assertEquals(BrainKind.WATCHMAN_GOBY, cat[watchman].brainKind)
        assertEquals("models/royal_gramma.glb", cat[gramma].model)
        assertEquals("models/banggai.glb", cat[banggai].model)
        assertEquals("models/watchman_goby.glb", cat[watchman].model)
        for (id in listOf(gramma, banggai, watchman)) assertNotNull(Stocking.entry(id))
        assertEquals(Gait.PERCH_HOP, MotionProfiles.of(BrainKind.WATCHMAN_GOBY).gait)
        assertTrue(compatibilityWarnings(gramma, listOf("pseudochromis_indigo"), 227.0).any { it.code == "gramma_dottyback" })
        assertTrue(compatibilityWarnings(watchman, listOf("valenciennea_puellaris"), 227.0).any { it.code == "watchman_gobies" })
        assertTrue(compatibilityWarnings(banggai, emptyList(), 227.0).any { it.code == "banggai_care" })
    }

    /** By day the gramma hangs by its cave, and near rock it turns its belly to the rock. */
    @Test fun grammaHangsByItsCaveBellyToRock() {
        val (sim, f) = withFish(gramma, 6.5, 11.0)
        val o = f.single()
        val b = sim.agentOf(o)!!.brain as GrammaBrain
        sim.advance(1800.0, behaviorDt = 0.1)
        var near = 0; var n = 0; var tilted = 0
        sim.advance(1800.0, behaviorDt = 0.1) {
            n++
            if (o.pos.distanceTo(b.cave) < 25.0) near++
            if (o.up.y < 0.8) tilted++
        }
        assertTrue("near its cave ${near * 100 / n}%", near > 0.8 * n)
        assertTrue("never tilted toward the rock", tilted > 0)
        assertTrue(o.visible)
    }

    /** At night it is in its cave. */
    @Test fun grammaSleepsInItsCave() {
        val (sim, f) = withFish(gramma, 6.5, 22.0)
        val o = f.single()
        val b = sim.agentOf(o)!!.brain as GrammaBrain
        sim.advance(1800.0, behaviorDt = 0.1)
        assertTrue(o.sleeping)
        assertTrue("${o.pos.distanceTo(b.cave)} cm from its cave", o.pos.distanceTo(b.cave) < 10.0)
    }

    /** The watchman lies at its burrow by day and goes in at a scare, flicking its tail first. */
    @Test fun watchmanWatchesAndDives() {
        val (sim, f) = withFish(watchman, 7.5, 11.0)
        val o = f.single()
        val b = sim.agentOf(o)!!.brain as WatchmanGobyBrain
        sim.advance(1200.0, behaviorDt = 0.1)
        assertTrue("${o.pos.distanceTo(b.burrow)} cm from its burrow", o.pos.distanceTo(b.burrow) < 10.0)
        assertTrue(o.pos.y < sim.rock.sandTopCm + 6.0)
        val before = b.alarms
        b.startleAt = sim.simTimeSec; b.startleMag = 0.6; b.startleFrom = o.pos + com.reeflive.core.geom.Vec3(10.0, 5.0, 0.0)
        sim.advance(5.0, behaviorDt = 0.1)
        assertEquals(before + 1, b.alarms)
        assertEquals("dive", o.brain.current?.name)
    }

    /** At night it is out of sight in its burrow. */
    @Test fun watchmanSleepsInItsBurrow() {
        val (sim, f) = withFish(watchman, 7.5, 22.0)
        val o = f.single()
        sim.advance(1200.0, behaviorDt = 0.1)
        assertFalse(o.visible)
        assertTrue(o.sleeping)
    }

    /** Two watchmen added together share one burrow half the time; never two pairs' worth of burrows for a pair. */
    @Test fun watchmanPairSharesABurrow() {
        var paired = 0
        for (seed in 1L..8L) {
            val (sim, f) = withFish(watchman, 7.5, 11.0, seed = seed, n = 2)
            val a = sim.agentOf(f[0])!!.brain as WatchmanGobyBrain
            val b = sim.agentOf(f[1])!!.brain as WatchmanGobyBrain
            if (b.mate === f[0]) { paired++; assertEquals(a.burrow, b.burrow) }
        }
        assertTrue("paired $paired of 8", paired in 1..7)
    }

    /** A Banggai pair: the male broods 20-30 days, then spits out real baby fish (no larvae) into the tank. */
    @Test fun banggaiMaleBroodsAMonthAndReleasesYoung() {
        val sim = TestTank.sim(listOf(
            LivestockSeed(banggai, "Bang", 6.5, role = "MALE", id = "bm"),
            LivestockSeed(banggai, "Gai", 6.0, role = "FEMALE", id = "bf"),
        ), seed = 4)
        val male = sim.organisms.first { it.id == "bm" }
        fun days(n: Int, until: () -> Boolean) {
            repeat(n * 24) {
                for (o in sim.organisms) if (o.alive) { o.hunger = 0.2; o.stress = 0.0 }
                sim.advanceAbstract(3600.0)
                if (until()) return
            }
        }
        days(25) { male.mouthbrooding }
        assertTrue("never brooded", male.mouthbrooding)
        val start = sim.simTimeSec
        days(40) { !male.mouthbrooding }
        val d = (sim.simTimeSec - start) / 86400.0
        assertTrue("brooded $d d", d in 19.5..31.5)
        assertTrue(sim.repro.banggaiYoungReleased in 1..Reproduction.BANGGAI_YOUNG_SHOWN)
        val young = sim.organisms.filter { it.species.id == banggai && it.lengthCm < 2.0 }
        assertEquals(sim.repro.banggaiYoungReleased, young.size)
        assertTrue(young.all { (sim.agentOf(it)!!.brain as CardinalBrain).banggai })
    }

    /** A pajama and a Banggai never breed together. */
    @Test fun pajamaAndBanggaiDoNotCrossBreed() {
        val sim = TestTank.sim(listOf(
            LivestockSeed(banggai, "Bang", 6.5, role = "MALE", id = "bm"),
            LivestockSeed("sphaeramia_nematoptera", "Pajama", 6.0, role = "FEMALE", id = "pf"),
        ), seed = 4)
        repeat(20 * 24) {
            for (o in sim.organisms) if (o.alive) { o.hunger = 0.2; o.stress = 0.0 }
            sim.advanceAbstract(3600.0)
        }
        assertFalse(sim.organisms.any { it.mouthbrooding })
    }
}
