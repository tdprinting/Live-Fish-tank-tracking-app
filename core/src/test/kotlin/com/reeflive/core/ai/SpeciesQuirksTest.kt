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

/** Species quirks: hawkfish night sand rest, brittle-star light sense, tail-first hole entry, dottyback patrol (SIMULATION §21.5). */
class SpeciesQuirksTest {
    private fun Simulation.org(kind: BrainKind) = organisms.first { it.brainKind == kind }
    private fun Simulation.agent(kind: BrainKind) = agentOf(org(kind))!!
    private fun stock(vararg ids: String) = ids.map { TestTank.seed(it) }

    // ------------------------------------------------------------------ hawkfish
    @Test
    fun hawkfishRestsHeadUpOnTheSandAtNight() {
        val s = TestTank.sim(stock("hawk"), seed = 1L, startHour = 21.5)
        val hawk = s.org(BrainKind.HAWK)
        val hb = s.agent(BrainKind.HAWK).brain as HawkBrain
        var resting = 0
        s.advance(15 * 60.0, behaviorDt = 0.1) { if (hawk.behaviorName == "Resting on the sand") resting++ }
        assertTrue("settled: ${hb.sandRest.settles}, resting ticks $resting", hb.sandRest.settles >= 1 && resting > 0)
        assertEquals("Resting on the sand", hawk.behaviorName)
        assertTrue("on the sand: y ${hawk.pos.y} sand ${s.rock.sandTopCm}", hawk.pos.y - s.rock.sandTopCm < 2.5)
        val gap = s.rock.sdf.dist(hawk.pos)
        assertTrue("within 10 cm of the rock base: $gap", gap <= SandRest.ROCK_GAP_CM + 1.0)
        val pitchDeg = Math.toDegrees(asin(hawk.heading.y))
        assertTrue("head up 15-20 deg: $pitchDeg", pitchDeg in 13.0..22.0)
        // the same spot the next night, and back up on a perch by day
        val spot = hb.sandRest.spotOrNull()!!
        s.advanceAbstract(12 * 3600.0)
        s.advance(20 * 60.0, behaviorDt = 0.1)
        assertTrue("up off the sand by day: ${hawk.behaviorName} y ${hawk.pos.y}", hawk.pos.y - s.rock.sandTopCm > 3.0)
        assertEquals(spot, hb.sandRest.spotOrNull())
    }

    // ------------------------------------------------------------------ brittle star
    @Test
    fun brittleStarArmsWithdrawFromLightAndShadow() {
        val s = TestTank.sim(stock("brittle"), seed = 1L, startHour = 23.0)
        s.advance(60.0, behaviorDt = 0.1)
        val b = s.agent(BrainKind.BRITTLE_STAR).brain as BrittleStarBrain
        assertEquals(0, b.lightShy.withdrawals)
        s.enqueue(Command.Lights(true, 1.0))
        var withdrawn = false
        s.advance(4.0, behaviorDt = 0.1) { if (b.o.behaviorName.startsWith("Pulling its arms in from the light")) withdrawn = true }
        assertTrue("light step → withdrawal (${b.lightShy.withdrawals})", b.lightShy.withdrawals >= 1 && withdrawn)
        s.enqueue(Command.Lights(null, null))
        s.advance(60.0, behaviorDt = 0.1)
        val n = b.lightShy.withdrawals
        s.enqueue(Command.Shadow(b.o.pos + Vec3(0.0, 15.0, 0.0)))
        s.advance(2.0, behaviorDt = 0.1)
        assertTrue("shadow → withdrawal", b.lightShy.withdrawals > n)
        // a slow dawn ramp does not trigger it
        val quiet = TestTank.sim(stock("brittle"), seed = 1L, startHour = 8.0)
        quiet.advance(3600.0, behaviorDt = 0.5)
        assertEquals(0, (quiet.agent(BrainKind.BRITTLE_STAR).brain as BrittleStarBrain).lightShy.withdrawals)
    }

    // ------------------------------------------------------------------ blennies, dottyback
    @Test
    fun tailspotBacksIntoItsHoleTailFirst() {
        val s = TestTank.sim(stock("tailspot"), seed = 1L, startHour = 22.0)
        s.advance(1.0, behaviorDt = 0.1)
        val a = s.agent(BrainKind.PEEKER_BLENNY)
        val hb = a.brain as PeekerBlennyBrain
        val hole = s.rock.anchors[hb.hole!!.name]!!
        val out = (hole.dir ?: Vec3.Z).normalized()
        a.ctx.o.placeAt(a.ctx.safe(hole.pos + out * 12.0 + Vec3(5.0, 3.0, 0.0)), out * -1.0)
        a.ctx.o.posture = 0
        val th = a.brain.utility.behaviors.filterIsInstance<TailFirstHole>().first()
        var sawBacking = false
        var facingOutWhileBacking = true
        s.advance(60.0, behaviorDt = 0.05) {
            if (th.phaseNow == 2 && a.brain.utility.current === th) {
                sawBacking = true
                if ((a.ctx.o.heading dot out) < 0.7) facingOutWhileBacking = false
            }
        }
        assertTrue("backed in: ${th.backIns}, saw backing $sawBacking", th.backIns >= 1 && sawBacking)
        assertTrue("head faces out of the hole while it backs in", facingOutWhileBacking)
        assertEquals(3, a.ctx.o.posture)
    }

    @Test
    fun dottybackPatrolsALearnedRoute() {
        val s = TestTank.sim(stock("dottyback"), seed = 2L, startHour = 11.0)
        s.advance(1.0)
        val a = s.agent(BrainKind.DOTTYBACK)
        val patrol = a.brain.utility.behaviors.filterIsInstance<CavePatrol>().first()
        s.advance(30 * 60.0, behaviorDt = 0.1)
        val route = a.brain.mind.places.of(PlaceKind.PATROL).map { it.pos }
        assertTrue("rounds ${patrol.rounds}", patrol.rounds >= 2)
        assertEquals(CavePatrol.ROUTE_STOPS, route.size)
        val cave = (a.brain as DottybackBrain).cave
        assertTrue("route stays in the territory", route.all { it.distanceTo(cave) < 2.5 * maxOf(8.0, a.ctx.o.species.territorialRadius) })
    }
}
