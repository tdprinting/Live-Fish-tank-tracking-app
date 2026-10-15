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

/** Perception channels: sight with light, lateral line, scent plumes (SIMULATION §21.1). */
class PerceptionTest {
    private fun Simulation.org(kind: BrainKind) = organisms.first { it.brainKind == kind }
    private fun Simulation.agent(kind: BrainKind) = agentOf(org(kind))!!
    private fun stock(vararg ids: String) = ids.map { TestTank.seed(it) }

    // ------------------------------------------------------------------ X1 perception
    /** A water point with a usable current (≥ 3 cm/s) and room 30 cm up- and downstream. */
    private fun plumeSite(s: Simulation): Pair<Vec3, Vec3>? {
        for (x in 20..100 step 10) for (y in 15..45 step 10) for (z in 8..28 step 5) {
            val p = Vec3(x.toDouble(), y.toDouble(), z.toDouble())
            if (!s.rock.inWater(p, 3.0) || s.rock.sdf.dist(p) < 3) continue
            val u = s.flow.velocityFast(p)
            if (u.length() < 3.0) continue
            val d = u.normalized()
            val down = p + d * 30.0; val up = p - d * 30.0
            if (s.rock.inWater(down, 2.0) && s.rock.inWater(up, 2.0)) return p to d
        }
        return null
    }

    @Test
    fun inTheDarkFoodIsFoundBySmellDownstreamNotUpstream() {
        val s = TestTank.sim(stock("tang"), seed = 1L, startHour = 23.0)
        s.advance(2.0, behaviorDt = 0.1)
        val a = s.agent(BrainKind.TANG)
        val (p, dir) = plumeSite(s) ?: error("no current anywhere")
        val f = FoodParticle(99999, FoodKind.MYSIS, p, Vec3.ZERO, s.simTimeSec - 60, 0, s.simTimeSec - 60)
        val tang = a.ctx.o
        assertTrue("night light ${a.ctx.p.light}", a.ctx.p.light < 0.05)
        tang.pos = p + dir * 30.0
        assertTrue("downstream: smelt", Perceive.sensesFood(a.ctx, f))
        tang.pos = p - dir * 30.0
        assertFalse("upstream in the dark: not found", Perceive.sensesFood(a.ctx, f))
        tang.pos = p - dir * (0.8 * tang.lengthCm)
        assertTrue("within the lateral line: felt", Perceive.sensesFood(a.ctx, f))
        // the same particle by day is seen from both sides
        a.ctx.p.light = 1.0
        tang.pos = p - dir * 30.0
        assertTrue("upstream by day: seen", Perceive.sensesFood(a.ctx, f))
        // and it reaches the food search itself
        a.ctx.p.light = 0.0
        s.food.particles += f
        tang.hunger = 0.8
        assertNull("findFood upstream at night", a.ctx.findFood(80.0, los = false))
        tang.pos = p + dir * 30.0
        assertNotNull("findFood downstream at night", a.ctx.findFood(80.0, los = false))
    }

    @Test
    fun invertebratesKeepTheOldSearchAndFishLoseSightAtNight() {
        val s = TestTank.sim(stock("tang", "cardinal", "nass1"), seed = 1L, startHour = 12.0)
        s.advance(1.0)
        val tang = s.agent(BrainKind.TANG).ctx
        val card = s.agent(BrainKind.CARDINAL).ctx
        assertTrue(Perceive.visionRange(tang) >= 100.0)
        tang.p.light = 0.0
        assertTrue("diurnal fish sees little at night", Perceive.visionRange(tang) < 20.0)
        assertTrue("crepuscular cardinal keeps more of its sight", Perceive.visionRange(card) > 2 * Perceive.visionRange(tang))
        assertFalse(s.org(BrainKind.NASSARIUS).species.sensesOrDefault.gated)
    }
}
