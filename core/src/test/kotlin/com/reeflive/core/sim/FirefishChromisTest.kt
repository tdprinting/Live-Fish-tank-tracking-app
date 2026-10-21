package com.reeflive.core.sim

import com.reeflive.core.ai.ChromisBrain
import com.reeflive.core.ai.FirefishBrain
import com.reeflive.core.geom.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Firefish goby and blue-green chromis (docs/research/41_firefish_chromis.md). */
class FirefishChromisTest {
    private val ff = "nemateleotris_magnifica"
    private val ch = "chromis_viridis"

    private fun sim(seeds: List<LivestockSeed>, startHour: Double = 12.0, seed: Long = 2L): Simulation {
        val sim = TestTank.sim(emptyList(), seed = seed, startHour = startHour)
        for (s in seeds) { sim.species.ensure(s.speciesId); sim.addFromSeed(s) }
        return sim
    }

    private fun chromis(n: Int) = (1..n).map { LivestockSeed(ch, "C$it", 4.5 + 0.6 * it, id = "ch$it") }

    @Test fun sheetsAndStore() {
        val cat = SpeciesCatalog.fromClasspath(listOf(ff, ch))
        assertEquals(BrainKind.FIREFISH, cat[ff].brainKind)
        assertEquals(BrainKind.CHROMIS, cat[ch].brainKind)
        assertEquals("models/firefish.glb", cat[ff].model)
        assertEquals("models/chromis.glb", cat[ch].model)
        assertTrue("firefish are famous jumpers", cat[ff].jumpRisk > 0.5)
        assertTrue(cat[ch].jumpRisk < cat[ff].jumpRisk)
        assertNotNull(Stocking.entry(ff)); assertNotNull(Stocking.entry(ch))
    }

    @Test fun firefishClaimsAHoleAndDivesInWhenStartled() {
        val sim = sim(listOf(LivestockSeed(ff, "Fire", 6.5, id = "ff1")))
        val o = sim.organisms.first { it.species.id == ff }
        val b = sim.agentOf(o)!!.brain as FirefishBrain
        val hole = b.hole
        assertNotNull("claimed a bolt hole", hole); hole!!
        sim.advance(60.0, behaviorDt = 0.1)
        assertTrue("hovers near its hole: ${o.pos.distanceTo(hole.mouth)}", o.pos.distanceTo(hole.mouth) < 25.0)
        sim.enqueue(Command.TapGlass(o.pos))
        var inHole = false
        sim.advance(30.0, behaviorDt = 0.1) { if (o.posture == 3) inHole = true }
        assertTrue("darted into its hole", inHole)
    }

    @Test fun anOpenTopLosesALeapingFishAndALidKeepsItIn() {
        for (lid in listOf(false, true)) {
            val sim = sim(listOf(LivestockSeed(ff, "Fire", 6.5, id = "ff1")))
            sim.water.lid = lid
            val o = sim.organisms.first { it.species.id == ff }
            // just under the surface by the front glass, launched up and out over it
            o.pos = Vec3(60.0, sim.rock.waterLevelCm - 3.0, 4.0)
            o.leap = Leap(Vec3(0.0, 180.0, -70.0), lid = lid)
            sim.advance(5.0, behaviorDt = 0.05)
            val events = sim.eventLog.toList()
            if (lid) {
                assertTrue("still in the tank", o.alive && o in sim.organisms)
                assertNull("back to swimming", o.leap)
                assertTrue(events.any { it is SimEvent.Jumped && it.what == Jump.HIT_LID })
            } else {
                assertTrue("jumped out", events.any { it is SimEvent.Jumped && it.what == Jump.OUT })
                assertTrue("gone from the tank", o !in sim.organisms)
            }
        }
    }

    @Test fun leapOddsFollowTheStartle() {
        val sim = sim(listOf(LivestockSeed(ff, "Fire", 6.5, id = "ff1")))
        val o = sim.organisms.first { it.species.id == ff }
        val now = o.arrivalTime + 30 * 86400.0
        val settled = JumpModel.chance(o, 1.0, 2.0, 99.0, false, now)
        assertTrue("a hard scare near the top: $settled", settled in 0.02..0.1)
        assertTrue("new fish jump more", JumpModel.chance(o, 1.0, 2.0, 99.0, false, o.arrivalTime + 3600) > 2 * settled)
        assertTrue("deep fish dive for cover", JumpModel.chance(o, 1.0, 30.0, 99.0, false, now) < 0.4 * settled)
        assertTrue("a flinch never launches it", JumpModel.chance(o, 0.1, 2.0, 99.0, false, now) == 0.0)
        assertTrue("clears the rim from close by", JumpModel.reachCm(170.0, 1.2, 2.5) in 5.0..40.0)
    }

    @Test fun chromisSchoolTogether() {
        val sim = sim(chromis(6))
        sim.advance(240.0, behaviorDt = 0.1)
        val fish = sim.organisms.filter { it.species.id == ch }
        val nearest = fish.map { a -> fish.filter { it !== a }.minOf { it.pos.distanceTo(a.pos) } }
        assertTrue("near a mate: $nearest", nearest.average() < 20.0)
        assertTrue("in mid water", fish.count { it.pos.y > sim.rock.sandTopCm + 8 } >= 5)
    }

    @Test fun aSmallGroupPicksOnItsBottomFish() {
        fun pecksOnBottom(n: Int): Pair<Int, Boolean> {
            val sim = sim(chromis(n), seed = 4L)
            val fish = sim.organisms.filter { it.species.id == ch }
            val bottom = fish.minBy { it.lengthCm }
            var pecks = 0
            sim.advance(3 * 3600.0, behaviorDt = 0.2) {
                for (e in sim.drainEvents()) if (e is SimEvent.Chase && e.bout == "PECK" && e.targetId == bottom.id) pecks++
            }
            assertTrue((sim.agentOf(bottom)!!.brain as ChromisBrain).rank == n - 1)
            return pecks to bottom.relations.values.any { it.harassedBy > 0.2 }
        }
        val small = pecksOnBottom(3)
        val big = pecksOnBottom(7)
        assertTrue("trio's underdog chased ${small.first}× vs ${big.first}× in a group of 7", small.first > big.first)
        assertTrue("trio's underdog chased at least a few times: ${small.first}", small.first >= 5)
    }
}
