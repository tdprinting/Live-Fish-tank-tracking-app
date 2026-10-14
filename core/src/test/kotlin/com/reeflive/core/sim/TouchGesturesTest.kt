package com.reeflive.core.sim

import com.reeflive.core.geom.FlowGust
import com.reeflive.core.geom.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The touch gestures beyond a single tap (UI_STYLE §10.8): a finger held and slid on the glass ([Command.Finger]),
 * a tap on the water surface, a flick that puffs the water ([Command.WaterPuff]) and a tap right on an animal
 * ([Command.TouchAnimal]), with habituation as for taps.
 */
class TouchGesturesTest {
    private fun tank(seed: Long = 3L) = TestTank.sim(DefaultStock.all, seed = seed, startHour = 12.0).also { it.advance(5.0) }
    private fun Simulation.o(id: String) = organisms.first { it.id == id }
    private fun Simulation.glassBy(o: Organism) = Vec3(o.pos.x, o.pos.y, rock.widthCm)
    private fun Simulation.brainOf(o: Organism) = agentOf(o)!!.brain

    @Test
    fun fingerRulesFollowTheTapRules() {
        val s = tank()
        fun r(id: String, d: Double, night: Boolean = false) = TankTouch.fingerReaction(s.o(id), TouchSurface.GLASS, d, night)
        for (id in listOf("clown_f", "tang", "wrasse", "hawk", "dottyback")) assertEquals(id, TouchReaction.FOLLOW, r(id, 25.0))
        assertEquals(TouchReaction.AVOID, r("cardinal", 10.0))
        assertEquals(TouchReaction.PEEK, r("lawnmower", 15.0))
        assertEquals(TouchReaction.FREEZE, r("banded", 10.0))
        assertEquals("nobody follows in the dark", TouchReaction.NONE, r("tang", 20.0, night = true))
        // a finger whisked past: shy fish and crustaceans close by react, nobody comes to follow
        assertEquals(TouchReaction.AVOID, TankTouch.fastFingerReaction(s.o("cardinal"), 10.0, false))
        assertEquals(TouchReaction.NONE, TankTouch.fastFingerReaction(s.o("tang"), 10.0, false))
        // the water surface: food-conditioned fish rise expecting food
        assertEquals(TouchReaction.RISE, TankTouch.reaction(s.o("tang"), TouchSurface.SURFACE, 30.0, false))
        assertEquals(TouchReaction.NONE, TankTouch.reaction(s.o("tang"), TouchSurface.SURFACE, 30.0, true))
        // an animal tapped right on the body
        for (id in listOf("astrea1", "nass1", "crab", "asterina1")) assertEquals(id, TouchReaction.TUCK, TankTouch.animalReaction(s.o(id)))
        assertEquals(TouchReaction.DUCK, TankTouch.animalReaction(s.o("hermit1")))
        assertEquals(TouchReaction.FREEZE, TankTouch.animalReaction(s.o("skunk")))
        assertEquals("a tapped fish only gets picked", TouchReaction.NONE, TankTouch.animalReaction(s.o("tang")))
    }

    @Test
    fun boldFishFollowAFingerSlidAlongTheGlassAndNipWhenItRests() {
        var followed = 0
        var nipped = 0
        val seeds = 1L..4L
        for (seed in seeds) {
            val s = tank(seed)
            val tang = s.o("tang")
            // put a finger on the glass a hand's width from the tang, then slide it 30 cm along the pane
            val start = s.glassBy(tang) + Vec3(if (tang.pos.x < 60.0) 12.0 else -12.0, 0.0, 0.0)
            val dir = if (start.x < 60.0) 1.0 else -1.0
            var p = start
            s.enqueue(Command.Finger(p, 0.0)); s.advance(0.25)
            assertEquals(TouchReaction.FOLLOW, s.finger!!.reactions["tang"])
            val gaps = ArrayList<Double>()
            for (k in 0 until 60) {
                p = start + Vec3(dir * 0.5 * k, 0.0, 0.0)   // 10 cm/s
                s.enqueue(Command.Finger(p, 10.0)); s.advance(0.05 * 1)
                s.advance(0.2)
                gaps += tang.pos.distanceTo(p)
            }
            // then it rests in one spot
            repeat(40) { s.enqueue(Command.Finger(p, 0.0)); s.advance(0.25) }
            val nips = s.brainOf(tang).touchNips
            val late = gaps.takeLast(20).average()
            println("seed $seed: follow gaps first %.1f last20 %.1f, nips $nips, ${tang.behaviorName}".format(gaps.first(), late))
            if (late < 12.0) followed++
            if (nips > 0) nipped++
            // lifting the finger ends it after a short linger
            s.enqueue(Command.Finger(null)); s.advance(TankTouch.FINGER_LINGER_S + 1.5)
            assertNull(s.finger)
            assertTrue("the tang stops following once the finger is gone: ${tang.behaviorName}", tang.behaviorName != "Following your finger")
        }
        assertTrue("the tang keeps up with the finger in most seeds: $followed", followed * 4 >= seeds.count() * 3)
        assertTrue("and nips at the resting fingertip: $nipped", nipped * 2 >= seeds.count())
    }

    @Test
    fun aLostFingerTimesOutAndHoldsGetIgnoredUntilFood() {
        val s = tank()
        val tang = s.o("tang")
        s.enqueue(Command.Finger(s.glassBy(tang), 0.0)); s.advance(0.5)
        s.advance(Simulation.FINGER_TIMEOUT_S + 1.0)
        assertNull("no news from the finger: it counts as lifted", s.finger)
        fun hold(): TouchReaction? {
            s.enqueue(Command.Finger(s.glassBy(tang), 0.0)); s.advance(0.5)
            val r = s.finger?.reactions?.get("tang")
            s.enqueue(Command.Finger(null)); s.advance(15.0)
            return r
        }
        val answers = (1..8).map { hold() }
        println("tang finger answers: $answers")
        val first = answers.indexOfFirst { it == null }
        assertTrue("it stops caring about a finger that never brings food: $answers", first in 2..6)
        s.enqueue(Command.Feed(FoodKind.PELLET, FoodKind.PELLET.typicalDoseGrams)); s.advance(60.0)
        assertEquals(TouchReaction.FOLLOW, hold())
    }

    @Test
    fun aTapOnTheWaterSurfaceBringsFedFishUp() {
        var rose = 0
        val seeds = 1L..4L
        for (seed in seeds) {
            val s = tank(seed)
            val tang = s.o("tang")
            val p = Vec3(tang.pos.x.coerceIn(20.0, 100.0), s.rock.waterLevelCm, 16.0)
            val y0 = tang.pos.y
            s.enqueue(Command.TouchTank(p, TouchSurface.SURFACE)); s.advance(0.1)
            assertEquals(TouchReaction.RISE, s.lastTouch!!.reactions["tang"])
            var top = y0
            repeat(40) { s.advance(0.25); top = maxOf(top, tang.pos.y) }
            println("seed $seed: tang y0 %.1f top %.1f".format(y0, top))
            if (top > s.rock.waterLevelCm - 10.0 && top > y0 + 1.0) rose++
        }
        assertTrue("the tang comes up under the ripple: $rose", rose * 4 >= seeds.count() * 3)
    }

    @Test
    fun tappedSnailsAndCrabsTuckInAndGetUsedToIt() {
        val s = tank()
        val snail = s.o("astrea1")
        s.enqueue(Command.TouchAnimal("astrea1")); s.advance(1.0)
        assertEquals("astrea1" to TouchReaction.TUCK, s.lastAnimalTouch)
        assertTrue("the foot pulls in: ${snail.extension} ${snail.behaviorName}", snail.extension < 0.3 && snail.behaviorName == "Pulled in")
        s.advance(25.0)
        assertTrue("and comes back out: ${snail.extension}", snail.extension > 0.9)
        val crab = s.o("crab")
        s.enqueue(Command.TouchAnimal("crab")); s.advance(0.5)
        assertEquals(TouchReaction.TUCK, s.lastAnimalTouch!!.second)
        s.enqueue(Command.TouchAnimal("hermit1")); s.advance(0.5)
        assertEquals(TouchReaction.DUCK, s.lastAnimalTouch!!.second)
        s.enqueue(Command.TouchAnimal("tang")); s.advance(0.5)
        assertEquals(TouchReaction.NONE, s.lastAnimalTouch!!.second)
        // the same snail poked again and again stops bothering
        val answers = (1..6).map { s.enqueue(Command.TouchAnimal("astrea1")); s.advance(10.0); s.lastAnimalTouch!!.second }
        println("snail answers: $answers, crab ${crab.behaviorName}")
        val first = answers.indexOfFirst { it == TouchReaction.NONE }
        assertTrue("habituates within a few pokes: $answers", answers.first() == TouchReaction.TUCK && first in 1..4 && answers.drop(first).all { it == TouchReaction.NONE })
    }

    @Test
    fun aFlickPuffsTheWaterAndItFades() {
        val s = tank()
        val card = s.o("cardinal")
        val gsp = s.corals.first { it.kind == CoralKind.GSP }
        val from = gsp.pos + Vec3(-8.0, 3.0, 0.0)
        val v = Vec3(30.0, 0.0, 0.0)
        val before = s.flow.velocity(from + Vec3(3.0, 0.0, 0.0), s.simTimeSec)
        s.enqueue(Command.WaterPuff(from, v)); s.advance(0.1)
        val during = s.flow.velocity(from + Vec3(3.0, 0.0, 0.0), s.simTimeSec)
        assertTrue("the puff adds a current along the flick: $before → $during", (during - before).x > 8.0)
        assertTrue("it reaches the snapshot for the renderer", s.latest.gusts.size == FlowGust.STRIDE)
        assertTrue("polyps in its path pull in a little: ${gsp.disturb}", gsp.disturb > 0.1)
        s.advance(FlowGust.LIFE_S + 0.5)
        assertTrue("and it is gone a few seconds later", s.flow.gusts.isEmpty() && s.latest.gusts.isEmpty())
        // a puff straight at the shy cardinal makes it flinch away
        s.enqueue(Command.WaterPuff(card.pos - Vec3(6.0, 0.0, 0.0), Vec3(25.0, 0.0, 0.0))); s.advance(0.1)
        assertEquals(TouchReaction.AVOID, s.brainOf(card).touchKind)
    }

    @Test
    fun theNoriCatchesAPuff() {
        val s = tank()
        s.enqueue(Command.Feed(FoodKind.NORI, 1.0))
        s.advance(5.0)
        val sheet = s.food.nori!!
        val c0 = sheet.cloth.centroid()
        s.enqueue(Command.WaterPuff(c0 + Vec3(0.0, 0.0, -6.0), Vec3(0.0, 0.0, 35.0)))
        var moved = 0.0
        repeat(12) { s.advance(0.1); moved = maxOf(moved, sheet.cloth.centroid().distanceTo(c0)) }
        println("nori moved %.2f cm in the puff".format(moved))
        assertTrue("the sheet swings in the puff: $moved", moved > 0.5)
    }
}
