package com.reeflive.core.sim

import com.reeflive.core.geom.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A gentle tap on the tank ([Command.TouchTank], [TankTouch]): who comes to look, who hides, sand and rock taps, a
 * quick run of taps becoming a knock, and habituation.
 */
class TankTouchTest {
    private fun tank(seed: Long = 3L) = TestTank.sim(DefaultStock.all, seed = seed, startHour = 12.0).also { it.advance(5.0) }
    private fun Simulation.o(id: String) = organisms.first { it.id == id }
    /** The front-glass point straight in front of [o]. */
    private fun Simulation.glassBy(o: Organism) = Vec3(o.pos.x, o.pos.y, rock.widthCm)

    @Test
    fun speciesAnswerTheGlassByTemperament() {
        val s = tank()
        fun r(id: String, d: Double, surface: TouchSurface = TouchSurface.GLASS, night: Boolean = false) = TankTouch.reaction(s.o(id), surface, d, night)
        // food-conditioned, bold fish come to look (research 02 §1C, §2C, §4C, §6C, 04 §3D)
        for (id in listOf("clown_f", "clown_m", "tang", "wrasse", "hawk", "dottyback")) assertEquals(id, TouchReaction.INVESTIGATE, r(id, 25.0))
        // shy fish turn away by day; the pajama cardinal is out and unbothered at night
        assertEquals(TouchReaction.AVOID, r("cardinal", 10.0))
        assertEquals(TouchReaction.AVOID, r("goby", 10.0))
        assertEquals(TouchReaction.NONE, r("cardinal", 10.0, night = true))
        // blennies peek; shrimp and the crab freeze; hermits pull into their shells; snails and stars do not care
        assertEquals(TouchReaction.PEEK, r("lawnmower", 15.0))
        assertEquals(TouchReaction.PEEK, r("tailspot", 15.0))
        for (id in listOf("banded", "skunk", "crab")) assertEquals(id, TouchReaction.FREEZE, r(id, 10.0))
        assertEquals(TouchReaction.DUCK, r("hermit1", 10.0))
        for (id in listOf("astrea1", "brittle", "asterina1")) assertEquals(id, TouchReaction.NONE, r(id, 5.0))
        // nobody comes to look in the dark
        assertEquals(TouchReaction.NONE, r("tang", 20.0, night = true))
        // out of reach
        assertEquals(TouchReaction.NONE, r("tang", 80.0))
        assertEquals(TouchReaction.NONE, r("skunk", 40.0))
    }

    @Test
    fun sandAndRockTapsHaveTheirOwnAnswers() {
        val s = tank()
        fun r(id: String, d: Double, surface: TouchSurface) = TankTouch.reaction(s.o(id), surface, d, false)
        assertEquals(TouchReaction.CHECK, r("nass1", 12.0, TouchSurface.SAND))
        assertEquals(TouchReaction.CHECK, r("diamond_goby", 20.0, TouchSurface.SAND))
        assertEquals(TouchReaction.NONE, r("tang", 10.0, TouchSurface.SAND))
        for (id in listOf("banded", "skunk", "crab", "hermit1", "lawnmower", "tailspot", "hawk")) assertEquals(id, TouchReaction.DUCK, r(id, 10.0, TouchSurface.ROCK))
        assertEquals(TouchReaction.AVOID, r("cardinal", 10.0, TouchSurface.CORAL))
        assertEquals(TouchReaction.NONE, r("tang", 10.0, TouchSurface.ROCK))
        assertEquals(TouchReaction.DUCK, r("duster", 8.0, TouchSurface.GLASS))
        assertTrue(TankTouch.retracts(CoralKind.GSP) && TankTouch.retracts(CoralKind.ZOA) && TankTouch.retracts(CoralKind.CLOVE))
        assertFalse(TankTouch.retracts(CoralKind.MUSHROOM))
    }

    @Test
    fun boldFishSwimOverToTheTap() {
        var came = 0
        val seeds = 1L..4L
        for (seed in seeds) {
            val s = tank(seed)
            val tang = s.o("tang")
            // a spot on the front glass a hand's width along from the tang
            val p = s.glassBy(tang) + Vec3(if (tang.pos.x < 60.0) 20.0 else -20.0, 0.0, 0.0)
            val d0 = tang.pos.distanceTo(p)
            s.enqueue(Command.TouchTank(p, TouchSurface.GLASS))
            var best = d0
            var label = false
            var t = 0.0
            while (t < 12.0) { s.advance(0.25); t += 0.25; best = minOf(best, tang.pos.distanceTo(p)); if (tang.behaviorName == "Coming to see the tap") label = true }
            println("seed $seed tang: d0=%.1f closest=%.1f label=$label".format(d0, best))
            assertEquals(TouchReaction.INVESTIGATE, s.lastTouch!!.reactions["tang"])
            if (label && best < 10.0) came++
        }
        assertTrue("the tang comes to the glass in most seeds: $came/${seeds.count()}", came * 4 >= seeds.count() * 3)
    }

    @Test
    fun shyFishTurnAwayAndShrimpFreeze() {
        val s = tank()
        val card = s.o("cardinal")
        val p = s.glassBy(card)
        s.enqueue(Command.TouchTank(p, TouchSurface.GLASS))
        var label = false
        repeat(12) { s.advance(0.25); if (card.behaviorName == "Turning away from the tap") label = true }
        assertTrue("the cardinal turns away: ${card.behaviorName}", label)

        // the banded shrimp under its ledge (a skunk busy on a client may carry on cleaning)
        val shrimp = s.o("banded")
        s.advance(30.0)
        // its ledge is at the back: the tap lands in the water a hand's width in front of it
        val q = shrimp.pos + Vec3(0.0, 0.0, 10.0)
        s.enqueue(Command.TouchTank(q, TouchSurface.GLASS))
        s.advance(0.5)
        val held = shrimp.pos
        var moved = 0.0
        var groom = 0.0
        var froze = 0
        repeat(6) {
            s.advance(0.25)
            if (shrimp.behaviorName == "Freezing still") { froze++; moved = maxOf(moved, shrimp.pos.distanceTo(held)); groom = maxOf(groom, shrimp.groom) }
        }
        assertTrue("the shrimp freezes: $froze samples, moved $moved", froze >= 3 && moved < 0.3)
        assertTrue("antennae drawn in: $groom", groom > 0.3)
    }

    @Test
    fun rockTapRetractsPolypsAndSandTapBringsTheSifters() {
        val s = tank()
        val gsp = s.corals.first { it.kind == CoralKind.GSP }
        val far = s.corals.filter { TankTouch.retracts(it.kind) && it.pos.distanceTo(gsp.pos) > 30.0 }
        s.enqueue(Command.TouchTank(gsp.pos, TouchSurface.ROCK))
        s.advance(0.1)
        assertEquals("a rock tap on a colony is a coral tap", TouchSurface.CORAL, s.lastTouch!!.surface)
        // a GSP mat closes around the tap (GspPolyps), and only a little as a whole
        assertTrue("the tapped GSP polyps retract: ${GspPolyps.closedAt(gsp, gsp.pos, s.simTimeSec)}", GspPolyps.closedAt(gsp, gsp.pos, s.simTimeSec) > 0.9)
        assertTrue("the rest of the mat stays out: ${gsp.disturb}", gsp.disturb in 0.05..0.5)
        assertTrue("colonies far away do not", far.all { it.disturb < 0.6 })

        val nass = s.o("nass1")
        val p = Vec3(nass.pos.x + 8.0, s.rock.sandTopCm, nass.pos.z)
        val d0 = nass.pos.distanceTo(p)
        s.advance(20.0)   // apart from the coral tap, so it is not a quick run
        s.enqueue(Command.TouchTank(p, TouchSurface.SAND))
        s.advance(0.1)
        assertEquals(TouchReaction.CHECK, s.lastTouch!!.reactions["nass1"])
        var best = d0
        var label = false
        repeat(160) { s.advance(0.25); best = minOf(best, nass.pos.distanceTo(p)); if (nass.behaviorName == "Checking the sand puff") label = true }
        assertTrue("the nassarius comes to check the puff: label=$label d0=$d0 closest=$best", label && best < d0 - 1.0)
    }

    @Test
    fun quickTapsInOneSpotBecomeAKnock() {
        val s = tank()
        val card = s.o("cardinal")
        val p = s.glassBy(card)
        s.enqueue(Command.TouchTank(p, TouchSurface.GLASS)); s.advance(0.5)
        assertFalse(s.lastTouch!!.knock)
        s.enqueue(Command.TouchTank(p + Vec3(2.0, 0.0, 0.0), TouchSurface.GLASS)); s.advance(0.5)
        assertFalse(s.lastTouch!!.knock)
        assertTrue("gentle taps are not knocks", s.perception.taps.isEmpty())
        s.enqueue(Command.TouchTank(p, TouchSurface.GLASS)); s.advance(0.1)
        assertTrue("the third quick tap is a knock", s.lastTouch!!.knock)
        assertTrue("the knock is the full glass tap (startle, GlassCurious)", s.perception.taps.isNotEmpty())
        // a tap somewhere else, or after a pause, is gentle again
        s.advance(10.0)
        s.enqueue(Command.TouchTank(p, TouchSurface.GLASS)); s.advance(0.1)
        assertFalse(s.lastTouch!!.knock)
    }

    @Test
    fun tappingWithNothingHappeningGetsIgnoredUntilFoodOrTime() {
        val s = tank()
        val tang = s.o("tang")
        fun tap(): TouchReaction? {
            s.enqueue(Command.TouchTank(s.glassBy(tang), TouchSurface.GLASS))
            s.advance(15.0)   // far enough apart never to be a quick run
            return s.lastTouch!!.reactions["tang"]
        }
        val answers = (1..8).map { tap() }
        println("tang answers: $answers")
        assertEquals("the first tap is answered", TouchReaction.INVESTIGATE, answers.first())
        val first = answers.indexOfFirst { it == null }
        assertTrue("it stops caring within a handful of taps: $answers", first in 2..6)
        assertTrue("and keeps ignoring them: $answers", answers.drop(first).all { it == null })
        // taps that paid off with food are worth answering again
        s.enqueue(Command.Feed(FoodKind.PELLET, FoodKind.PELLET.typicalDoseGrams))
        s.advance(60.0)
        assertEquals(TouchReaction.INVESTIGATE, tap())
        // and plain time brings the interest back
        repeat(6) { tap() }
        assertEquals(null, tap())
        val later = s.simTimeSec + 2 * 3600.0
        assertTrue("interest recovers after a couple of quiet hours: ${s.touchMemory.habituation("tang", later)}",
            s.touchMemory.habituation("tang", later) < 0.1)
    }

    @Test
    fun anIdleTouchReactionDrawsNoRandomness() {
        // the shared TouchReact behaviour is installed in every brain: until a tap it must not change any sequence
        val a = TestTank.sim(DefaultStock.all, seed = 9L)
        val b = TestTank.sim(DefaultStock.all, seed = 9L)
        a.advance(30.0); b.advance(30.0)
        for ((x, y) in a.organisms.zip(b.organisms)) assertEquals(x.id, x.pos, y.pos)
        assertTrue(a.agentOf(a.o("tang"))!!.brain.utility.behaviors.any { it.name == "touch" && !it.noisy })
    }
}
