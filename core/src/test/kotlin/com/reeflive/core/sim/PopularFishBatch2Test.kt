package com.reeflive.core.sim

import com.reeflive.core.ai.ClownGobyBrain
import com.reeflive.core.ai.MandarinBrain
import com.reeflive.core.ai.SixlineBrain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Popular fish pack, batch 2 (research 51): mandarin dragonet, six line wrasse, yellow clown goby. */
class PopularFishBatch2Test {
    private val mandarin = "synchiropus_splendidus"
    private val sixline = "pseudocheilinus_hexataenia"
    private val goby = "gobiodon_okinawae"

    private fun withFish(id: String, cm: Double, startHour: Double, seed: Long = 7L, n: Int = 1): Pair<Simulation, List<Organism>> {
        val sim = TestTank.sim(DefaultStock.corals, seed = seed, startHour = startHour)
        sim.species.ensure(id)
        val fish = (1..n).map { sim.addFromSeed(LivestockSeed(id, "Fish $it", cm, id = "f$it")) }
        return sim to fish
    }

    @Test fun sheetsResolveBrainsModelsAndShop() {
        val cat = SpeciesCatalog.fromClasspath(listOf(mandarin, sixline, goby))
        assertEquals(BrainKind.MANDARIN, cat[mandarin].brainKind)
        assertEquals(BrainKind.SIXLINE, cat[sixline].brainKind)
        assertEquals(BrainKind.CLOWN_GOBY, cat[goby].brainKind)
        assertEquals("models/mandarin.glb", cat[mandarin].model)
        assertEquals("models/sixline_wrasse.glb", cat[sixline].model)
        assertEquals("models/clown_goby.glb", cat[goby].model)
        for (id in listOf(mandarin, sixline, goby)) assertNotNull(Stocking.entry(id))
        assertEquals(PodPredator.MANDARIN, PodPredator.entries.first { it.name == "MANDARIN" })
        assertTrue(compatibilityWarnings(mandarin, listOf("macropharyngodon_bipartitus"), 227.0).any { it.code == "mandarin_rivals" })
        assertTrue(compatibilityWarnings(sixline, listOf("labroides_dimidiatus"), 227.0).any { it.code == "sixline_wrasses" })
        assertTrue(compatibilityWarnings(goby, emptyList(), 227.0).any { it.code == "clown_goby_coral" })
    }

    /** By day the mandarin hops along the low rock pecking pods, and the pods feed it. */
    @Test fun mandarinPecksPodsLowOnTheRock() {
        val (sim, f) = withFish(mandarin, 6.5, 11.0)
        val o = f.single()
        val b = sim.agentOf(o)!!.brain as MandarinBrain
        sim.advance(600.0, behaviorDt = 0.1)
        var low = 0; var n = 0
        o.hunger = 0.5
        sim.advance(1800.0, behaviorDt = 0.1) { n++; if (o.pos.y < sim.rock.sandTopCm + MandarinBrain.LOW_CM + 6.0) low++ }
        assertTrue("pecks ${b.pecks}", b.pecks > 200)
        assertTrue("low ${low * 100 / n}%", low > 0.85 * n)
        assertTrue("hunger ${o.hunger}", o.hunger < 0.5)
    }

    /** Without pods (dry rock) the same mandarin goes hungry: it lives on the tank's microfauna. */
    @Test fun mandarinStarvesWithoutPods() {
        fun hungerAfter(dry: Boolean): Double {
            val (sim, f) = withFish(mandarin, 6.5, 11.0)
            if (dry) sim.water.micro.dryRock()
            val o = f.single()
            o.acceptsPrepared = false
            o.hunger = 0.3
            sim.advance(3600.0, behaviorDt = 0.1)
            return o.hunger
        }
        val fed = hungerAfter(false)
        val dry = hungerAfter(true)
        assertTrue("mature $fed, dry rock $dry", dry > fed + 0.03)
    }

    /** A mandarin pair: at dusk he courts her, they rise together and spawn under the surface. */
    @Test fun mandarinPairSpawnsAtDusk() {
        val sim = TestTank.sim(DefaultStock.corals + listOf(
            LivestockSeed(mandarin, "Him", 7.0, role = "MALE", id = "mm"),
            LivestockSeed(mandarin, "Her", 6.0, role = "FEMALE", id = "mf"),
        ), seed = 5, startHour = 18.3)
        val m = sim.organisms.first { it.id == "mm" }
        val b = sim.agentOf(m)!!.brain as MandarinBrain
        for (o in sim.organisms) if (o.alive) { o.hunger = 0.1; o.stress = 0.0 }
        b.spawnTonight(sim.simTimeSec)
        var topY = 0.0
        var her = 0.0
        val fem = sim.organisms.first { it.id == "mf" }
        sim.advance(2.5 * 3600, behaviorDt = 0.1) { if (b.spawnPhase == 2) { topY = maxOf(topY, m.pos.y); her = maxOf(her, fem.pos.y) } }
        assertEquals(1, b.spawns)
        assertTrue("rose to $topY", topY > sim.rock.waterLevelCm - 15.0)
        assertTrue("she rose to $her", her > sim.rock.waterLevelCm - 18.0)
    }

    /** The six line darts and picks all day, and sleeps in its hole in a mucus cocoon. */
    @Test fun sixlineHuntsThenCocoonsAtNight() {
        val (sim, f) = withFish(sixline, 7.0, 11.0)
        val o = f.single()
        val b = sim.agentOf(o)!!.brain as SixlineBrain
        sim.advance(1800.0, behaviorDt = 0.1)
        assertTrue("picks ${b.picks}", b.picks > 100)
        val (sim2, f2) = withFish(sixline, 7.0, 22.0)
        val b2 = sim2.agentOf(f2.single())!!.brain as SixlineBrain
        sim2.advance(1800.0, behaviorDt = 0.1)
        assertTrue(b2.cocooned)
        assertTrue(f2.single().pos.distanceTo(b2.den) < 10.0)
    }

    /** A settled six line chases a fish added after it. */
    @Test fun sixlineChasesANewcomer() {
        val sim = TestTank.sim(DefaultStock.corals + listOf(LivestockSeed(sixline, "Six", 7.0, id = "s")), seed = 3, startHour = 11.0)
        val b = sim.agentOf(sim.organisms.first { it.id == "s" })!!.brain as SixlineBrain
        sim.species.ensure("sphaeramia_nematoptera")
        sim.addFromSeed(LivestockSeed("sphaeramia_nematoptera", "New", 5.0, id = "n"))
        val n = sim.organisms.first { it.id == "n" }
        var near = 0
        sim.advance(2 * 3600.0, behaviorDt = 0.1) { if (n.pos.distanceTo(b.den) < 60) near++ }
        assertTrue("chases ${b.chases}, newcomer near its patch $near ticks", b.chases >= 1)
    }

    /** The clown goby sits in the Kenya tree, and two of them squabble then pair, the bigger one male. */
    @Test fun clownGobiesPerchInCoralAndPair() {
        val (sim, f) = withFish(goby, 3.0, 11.0, n = 2)
        val a = sim.agentOf(f[0])!!.brain as ClownGobyBrain
        val b = sim.agentOf(f[1])!!.brain as ClownGobyBrain
        f[0].lengthCm = 3.2
        assertNotNull(a.coral)
        sim.advance(600.0, behaviorDt = 0.1)
        assertTrue("${f[0].pos.distanceTo(a.perch)} cm from its perch", f[0].pos.distanceTo(a.perch) < 3.0)
        sim.advance(3 * 3600.0, behaviorDt = 0.1)
        assertTrue("squabbles ${a.squabbles}+${b.squabbles}", a.mate === f[1] && b.mate === f[0])
        assertEquals(false, f[0].sexFemale)
        assertEquals(true, f[1].sexFemale)
    }
}
