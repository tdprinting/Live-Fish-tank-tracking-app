package com.reeflive.core.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tank Health → "Feed the hungry ones" ([HungryFeed]): every hungry animal gets its own food dropped above it. */
class HungryFeedTest {
    @Test
    fun everyHungryAnimalGetsItsFavouriteFoodAboveIt() {
        val s = TestTank.sim(DefaultStock.all, seed = 3L)
        s.advance(2.0)
        val fed = s.organisms.filter { it.alive }
        fed.forEach { it.hunger = 0.9 }
        val drops = HungryFeed.plan(s.organisms, s.rock.waterLevelCm, s.rock.lengthCm, s.rock.widthCm)
        val covered = drops.flatMap { it.ids }.toSet()
        val feedable = fed.filter { HungryFeed.bestFood(it.species) != null }
        assertEquals("every hungry animal is in exactly one drop", feedable.map { it.id }.toSet(), covered)
        assertEquals(covered.size, drops.sumOf { it.ids.size })
        for (d in drops) {
            assertTrue("drops land on the surface: ${d.at}", d.at.y > s.rock.waterLevelCm - 1)
            assertTrue("never more than one normal tap per drop: $d", d.grams <= maxOf(d.kind.typicalDoseGrams, HungryFeed.MIN_PARTICLES * d.kind.gramsPerParticle) + 1e-9)
            for (id in d.ids) {
                val o = s.organisms.first { it.id == id }
                assertEquals("each gets the food it likes best", HungryFeed.bestFood(o.species), d.kind)
                assertTrue("dropped near it: ${o.pos} vs ${d.at}", kotlin.math.hypot(o.pos.x - d.at.x, o.pos.z - d.at.z) <= HungryFeed.SHARE_CM * 2 + 4)
            }
        }
        val tang = s.organisms.first { it.brainKind == BrainKind.TANG }
        assertEquals("the tang gets an algae wafer", FoodKind.ALGAE_WAFER, HungryFeed.bestFood(tang.species))
    }

    @Test
    fun nobodyHungryMeansNoFood() {
        val s = TestTank.sim(DefaultStock.all, seed = 3L)
        s.organisms.forEach { it.hunger = 0.1 }
        assertTrue(HungryFeed.plan(s.organisms, s.rock.waterLevelCm, s.rock.lengthCm, s.rock.widthCm).isEmpty())
        val rows = HungryFeed.rows(s.latest.stats.ifEmpty { s.organisms.map { it.stats() } }) { s.species[it] }
        assertTrue(rows.isNotEmpty() && rows.none { it.hungry })
    }

    @Test
    fun feedingTheHungryFillsThemUp() {
        val s = TestTank.sim(DefaultStock.all, seed = 4L)
        s.advance(3.0)
        val fish = s.organisms.filter { it.alive && it.isFish }
        fish.forEach { it.hunger = 0.85 }
        s.enqueue(Command.FeedHungry)
        s.advance(240.0)
        val after = fish.filter { it.alive }.map { it.hunger }.average()
        println("fish hunger after the auto feed: %.2f".format(after))
        assertTrue("the fish ate: mean hunger $after", after < 0.6)
    }
}
