package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.Simulation
import com.reeflive.core.sim.TestTank
import org.junit.Assert.assertTrue
import org.junit.Test

/** Shrimp limb hooks and the behaviours that drive them (research 17). */
class ShrimpMotionTest {
    private fun Simulation.org(kind: BrainKind) = organisms.first { it.brainKind == kind }
    private fun stock(vararg ids: String) = ids.map { TestTank.seed(it) }

    @Test
    fun bothShrimpGroomTheirAntennaeInShortBouts() {
        for (kind in listOf(BrainKind.BANDED_SHRIMP, BrainKind.CLEANER_SHRIMP)) {
            val s = TestTank.sim(stock(if (kind == BrainKind.BANDED_SHRIMP) "banded" else "skunk"), seed = 3L, startHour = 11.0)
            val o = s.org(kind)
            var peak = 0.0
            var groomingTicks = 0
            var ticks = 0
            s.advance(1800.0, behaviorDt = 0.1) { peak = maxOf(peak, o.groom); ticks++; if (o.groom > 0.5) groomingTicks++ }
            assertTrue("$kind groomed (peak ${o.groom} / $peak)", peak > 0.8)
            // bouts are short: grooming fills a small share of the time, not the whole half hour
            assertTrue("$kind grooming share ${groomingTicks.toDouble() / ticks}", groomingTicks.toDouble() / ticks < 0.25)
        }
    }

    @Test
    fun bandedShrimpRaisesItsClawsAtAnIntruderNotAtAClient() {
        val s = TestTank.sim(stock("banded", "tang"), seed = 2L, startHour = 12.0)
        val shrimp = s.org(BrainKind.BANDED_SHRIMP)
        val tang = s.org(BrainKind.TANG)
        s.advance(5.0, behaviorDt = 0.1) { tang.pos = Vec3(20.0, 45.0, 25.0); tang.vel = Vec3.ZERO }
        assertTrue("calm ${shrimp.clawDisplay}", shrimp.clawDisplay < 0.2)
        // a fish darting past within a few cm
        s.advance(2.0, behaviorDt = 0.1) { tang.pos = shrimp.pos + Vec3(3.0, -2.0, 0.0); tang.vel = Vec3(tang.cruiseSpeed * 2, 0.0, 0.0) }
        assertTrue("display ${shrimp.clawDisplay}", shrimp.clawDisplay > 0.6)
        s.advance(5.0, behaviorDt = 0.1) { tang.pos = Vec3(20.0, 45.0, 25.0); tang.vel = Vec3.ZERO }
        assertTrue("relaxed ${shrimp.clawDisplay}", shrimp.clawDisplay < 0.2)
        val pose = s.snapshot().organisms.first { it.id == shrimp.id }
        assertTrue(pose.limbSignal < 0.2)
    }

    @Test
    fun walkingAdvancesTheStrideClockAndStandingStopsIt() {
        val s = TestTank.sim(stock("banded"), seed = 5L, startHour = 23.0)
        val o = s.org(BrainKind.BANDED_SHRIMP)
        var moved = 0.0
        var stepped = 0.0
        var last = o.walkPhase
        s.advance(600.0, behaviorDt = 0.1) {
            val d = (o.walkPhase - last + 1000.0) % 1000.0
            last = o.walkPhase
            moved += o.vel.length() * 0.1
            stepped += d
            assertTrue(o.walkAmt in 0.0..1.0)
        }
        // strides follow the distance walked (one per quarter body length)
        assertTrue("strides $stepped for $moved cm", stepped <= 1.2 * moved / (0.25 * o.lengthCm) + 1)
        if (moved > 5) assertTrue("strides $stepped for $moved cm", stepped > 0.2 * moved / (0.25 * o.lengthCm))
        val pose = s.snapshot().organisms.first { it.id == o.id }
        assertTrue(pose.walkPhase >= 0f && pose.motionMode == 2)
    }
}
