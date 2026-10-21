package com.reeflive.core.sim

import com.reeflive.core.ai.ButterflyBrain
import com.reeflive.core.ai.TangBrain
import com.reeflive.core.custom.economy.PriceBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rare and expensive fish, batch 1 (research 60): gem tang and wrought iron butterflyfish. */
class RareFishBatch1Test {
    private val gem = "zebrasoma_gemmatum"
    private val iron = "chaetodon_daedalma"

    private fun withFish(stock: List<LivestockSeed>, id: String, startHour: Double, seed: Long = 7L, n: Int = 1): Pair<Simulation, List<Organism>> {
        val sim = TestTank.sim(stock, seed = seed, startHour = startHour)
        sim.species.ensure(id)
        val fish = (1..n).map { sim.addFromSeed(LivestockSeed(id, "Fish $it", 0.0, id = "f$it")) }
        return sim to fish
    }

    @Test fun sheetsResolveBrainsModelsAndShop() {
        val cat = SpeciesCatalog.fromClasspath(listOf(gem, iron))
        assertEquals(BrainKind.TANG, cat[gem].brainKind)
        assertTrue(cat[gem].zebrasoma)
        assertEquals(BrainKind.BUTTERFLY, cat[iron].brainKind)
        assertEquals("models/gem_tang.glb", cat[gem].model)
        assertEquals("models/wrought_iron_butterfly.glb", cat[iron].model)
        for (id in listOf(gem, iron)) {
            assertNotNull(Stocking.entry(id))
            val l = PriceBook.listing(id)!!
            assertTrue("$id is a late unlock (level ${l.level})", l.level >= 8)
            assertTrue("$id costs more than a purple tang", l.price > PriceBook.listing("zebrasoma_xanthurum")!!.price)
        }
        assertEquals(570.0, LifeCycle.minVolumeL(gem, BrainKind.TANG), 0.0)
        assertTrue(compatibilityWarnings(gem, listOf("zebrasoma_flavescens"), 800.0).any { it.code == "zebrasoma_pair" })
        assertTrue(compatibilityWarnings(iron, listOf("sabellastarte_sp"), 800.0).any { it.code == "butterfly_fanworms" })
        assertTrue(compatibilityWarnings(iron, emptyList(), 227.0).any { it.code == "butterfly_tank_size" })
        // cool-water fish: the owner's 26 °C is above their comfort band
        assertTrue(cat[iron].tolerance["temp"]!!.optHi!! < 26.0 && cat[gem].tolerance["temp"]!!.optHi!! < 26.0)
    }

    /** By day the butterfly roves and pecks the rock face. */
    @Test fun butterflyPecksTheRockByDay() {
        val (sim, f) = withFish(DefaultStock.corals, iron, 10.0)
        val o = f.single()
        val b = sim.agentOf(o)!!.brain as ButterflyBrain
        sim.advance(3600.0, behaviorDt = 0.1) { o.hunger = maxOf(o.hunger, 0.4) }
        assertTrue("rock pecks ${b.rockPecks}", b.rockPecks > 20)
        assertTrue(o.visible && !o.sleeping)
    }

    /** It eats fanworms: an open feather duster gets pecked, and its fan snaps shut. */
    @Test fun butterflyPecksTheFeatherDuster() {
        val (sim, f) = withFish(DefaultStock.all, iron, 10.0)
        val o = f.single()
        val b = sim.agentOf(o)!!.brain as ButterflyBrain
        assertTrue(sim.organisms.any { it.alive && it.brainKind == BrainKind.FEATHER_DUSTER })
        sim.advance(4 * 3600.0, behaviorDt = 0.1) { o.hunger = maxOf(o.hunger, 0.5) }
        assertTrue("duster pecks ${b.dusterPecks}", b.dusterPecks > 0)
    }

    /** Two wrought irons pair up and swim together. */
    @Test fun butterfliesPairUp() {
        val (sim, f) = withFish(DefaultStock.corals, iron, 11.0, n = 2)
        sim.advance(600.0, behaviorDt = 0.1)
        assertEquals(f[1].id, f[0].mateId)
        var close = 0; var n = 0
        sim.advance(1800.0, behaviorDt = 0.1) { n++; if (f[0].pos.distanceTo(f[1].pos) < ButterflyBrain.PAIR_REACH_CM) close++ }
        assertTrue("together ${close * 100 / n}% of the time", close > 0.6 * n)
    }

    /** At night it sleeps against the rock, never inside it. */
    @Test fun butterflySleepsAgainstTheRock() {
        val (sim, f) = withFish(DefaultStock.corals, iron, 22.0)
        val o = f.single()
        sim.advance(2400.0, behaviorDt = 0.1)
        assertTrue(o.sleeping)
        assertTrue("${sim.rock.sdf.dist(o.pos)} cm from the rock", sim.rock.sdf.dist(o.pos) > 0.0 && sim.rock.sdf.dist(o.pos) < 12.0)
    }

    /** The gem tang is a Zebrasoma: it grazes the rock like the yellow tang. */
    @Test fun gemTangGrazesLikeASailfinTang() {
        val (sim, f) = withFish(DefaultStock.corals, gem, 10.0)
        val o = f.single()
        val b = sim.agentOf(o)!!.brain as TangBrain
        assertTrue(b.zebra)
        var grazing = 0
        sim.advance(3600.0, behaviorDt = 0.1) { o.hunger = maxOf(o.hunger, 0.5); if (o.brain.current?.name == "graze") grazing++ }
        assertTrue("never grazed", grazing > 0)
    }
}
