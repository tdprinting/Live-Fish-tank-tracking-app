package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.Command
import com.reeflive.core.sim.FoodKind
import com.reeflive.core.sim.FoodParticle
import com.reeflive.core.sim.LivestockSeed
import com.reeflive.core.sim.SimEvent
import com.reeflive.core.sim.Simulation
import com.reeflive.core.sim.TankConfig
import com.reeflive.core.sim.TestTank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.asin

/** Place memory, feeding clock, keeper cue, mood and drift (SIMULATION §21.2–21.4). */
class MindMemoryTest {
    private fun Simulation.org(kind: BrainKind) = organisms.first { it.brainKind == kind }
    private fun Simulation.agent(kind: BrainKind) = agentOf(org(kind))!!
    private fun stock(vararg ids: String) = ids.map { TestTank.seed(it) }

    // ------------------------------------------------------------------ X3 feeding clock
    @Test
    fun clockLearnsAFeedingTimeAndForgetsItAfterMissedDays() {
        val k = FeedClock()
        val day = 86400.0
        val at = Vec3(60.0, 50.0, 10.0)
        for (d in 0 until 4) { k.learn(d * day + 13 * 3600.0, at); k.extinguish(d * day + 15 * 3600.0) }
        val before = 4 * day + 12.5 * 3600
        val a = k.anticipation(before)
        assertNotNull("anticipates 30 min before the 4-day feeding time", a)
        assertNull("nothing at 16:00", k.anticipation(4 * day + 16 * 3600.0))
        assertNull("nothing after only one feed", FeedClock().also { it.learn(13 * 3600.0, at) }.anticipation(day + 12.5 * 3600))
        // feeding withheld: anticipation lasts a few days (REE02: 3-10), then fades
        var lastDay = 4
        for (d in 4 until 14) {
            k.extinguish(d * day + 15 * 3600.0)
            if (k.anticipation((d + 1) * day + 12.5 * 3600) != null) lastDay = d + 1
        }
        assertTrue("still anticipating on day ${lastDay - 4} without food", lastDay - 4 in 1..9)
    }

    /** Tang fed at 13:00 for four days drifts toward the feeding spot before 13:00 on day five. */
    @Test
    fun fishGatherBeforeALearnedFeedingTime() {
        fun meanDistance(feedHour: Double, seed: Long): Pair<Double, Int> {
            // no automatic feeder: only the test's own feeds teach the clock
            val s = TestTank.sim(stock("tang"), seed = seed, startHour = 11.0, config = TankConfig(seed = seed, autoFeed = emptyList()))
            val point = s.feedPoint(null)
            var t = 11.0
            for (d in 0 until 4) {
                s.advanceAbstract((feedHour - t) * 3600); t = feedHour
                s.feed(FoodKind.MYSIS, 0.05, point)
                s.advanceAbstract(3600.0); t += 1.0
                s.advanceAbstract((24 - t + 11) * 3600); t = 11.0
            }
            // day five, 12:30 to 12:58, full behaviour
            s.advanceAbstract(90 * 60.0)
            val tang = s.org(BrainKind.TANG)
            var sum = 0.0; var n = 0; var waiting = 0
            s.advance(28 * 60.0, behaviorDt = 0.1) { sum += tang.pos.distanceTo(point); n++; if (tang.behaviorName == "Waiting for feeding time") waiting++ }
            return sum / n to waiting
        }
        for (seed in 1L..2L) {
            val (learned, waiting) = meanDistance(13.0, seed)
            val (control, controlWaiting) = meanDistance(17.0, seed)
            assertTrue("seed $seed: waiting ticks $waiting", waiting > 0)
            assertEquals("control fed at 17:00 does not wait at noon", 0, controlWaiting)
            assertTrue("seed $seed: mean distance to the feed spot $learned vs control $control", learned < 0.6 * control)
        }
    }

    @Test
    fun keeperCueBringsAFedFishToTheGlass() {
        fun closest(assoc: Double): Double {
            val s = TestTank.sim(stock("tang"), seed = 3L, startHour = 12.0)
            s.advance(5.0, behaviorDt = 0.1)
            val a = s.agent(BrainKind.TANG)
            a.brain.mind.keeperAssoc = assoc
            // the back glass, away from the tang's front-lane patrol. Resized tank: the back glass at (30, 30) is behind
            // the left shelf rock now, so the cue is on the open back glass at x 85 (clear of the HOB return at x 73)
            // and the tang starts the same ~65 cm away at the left
            val glass = Vec3(85.0, 33.0, 0.5)
            a.ctx.o.pos = s.rock.clampToWater(Vec3(20.0, 30.0, 25.0), 3.0)
            a.brain.mind.onKeeperCue(a.ctx, glass)
            var best = 1e9
            s.advance(40.0, behaviorDt = 0.1) { best = minOf(best, a.ctx.o.pos.distanceTo(glass)) }
            return best
        }
        val fed = closest(0.6)
        val naive = closest(0.0)
        assertTrue("fed fish came to the glass: $fed cm (naive $naive cm)", fed < 10.0 && naive > fed + 8)
    }

    // ------------------------------------------------------------------ X2 place memory, X4 mood
    @Test
    fun aRememberedRefugeIsUsedUnderThreat() {
        val s = TestTank.sim(stock("wrasse"), seed = 2L, startHour = 12.0)
        s.advance(5.0, behaviorDt = 0.1)
        val a = s.agent(BrainKind.WRASSE)
        val o = a.ctx.o
        // threat on one side; a remembered refuge on the other, nearer than the species' own cover (Flee only prefers
        // a remembered refuge when it is the nearer shelter)
        val from = o.pos - Vec3(15.0, 0.0, 0.0)
        val ownDist = a.ctx.coverPoint(from).distanceTo(o.pos)
        val refuge = a.ctx.safe(o.pos + Vec3(minOf(12.0, ownDist * 0.6), 0.0, 0.0), 1.0)
        assertTrue("test setup: refuge nearer than own cover", refuge.distanceTo(o.pos) < ownDist)
        a.brain.mind.places.note(PlaceKind.REFUGE, refuge, 1.0, s.simTimeSec)
        a.brain.onAttacked(a.ctx, from, 0.9)
        s.advance(0.2, behaviorDt = 0.1)
        val flee = a.brain.utility.behaviors.filterIsInstance<Flee>().first()
        assertTrue("fleeing: ${o.behaviorName}", a.brain.utility.current === flee)
        assertTrue("flees to the remembered refuge: ${flee.hideTarget} vs $refuge", flee.hideTarget.distanceTo(refuge) < 3.0)
        // the attack site is remembered as a threat, the fish is more fearful and a little shyer
        assertTrue(a.brain.mind.places.of(PlaceKind.THREAT).isNotEmpty())
        assertTrue(a.brain.mind.mood.fear > 0.3)
    }

    @Test
    fun aFleeThatReachesCoverIsRememberedAndFearFades() {
        val s = TestTank.sim(stock("wrasse"), seed = 4L, startHour = 12.0)
        s.advance(5.0, behaviorDt = 0.1)
        val a = s.agent(BrainKind.WRASSE)
        val o = a.ctx.o
        val bold0 = o.boldness
        a.brain.onAttacked(a.ctx, o.pos + Vec3(5.0, 0.0, 0.0), 0.9)
        s.advance(40.0, behaviorDt = 0.1)
        assertTrue("refuge learned", a.brain.mind.places.of(PlaceKind.REFUGE).isNotEmpty())
        assertTrue("a little shyer after the attack", o.boldness < bold0)
        val f0 = a.brain.mind.mood.fear
        s.advance(3600.0, behaviorDt = 0.5)
        assertTrue("fear fades: $f0 -> ${a.brain.mind.mood.fear}", a.brain.mind.mood.fear < 0.3 * f0)
        // drift is capped around the birth value
        repeat(100) { a.brain.onAttacked(a.ctx, o.pos + Vec3(5.0, 0.0, 0.0), 0.9) }
        assertTrue(o.boldness >= bold0 - Mind.DRIFT_CAP - 1e-9)
    }
}
