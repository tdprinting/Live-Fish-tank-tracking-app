package com.reeflive.core.sim

import com.reeflive.core.ai.LawnmowerBlennyBrain
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lawnmower blenny colour change and cave use (research 51; Tee 2026-10-20): it darkens on rock and in caves, pales
 * on sand and when scared, hides in the open caves rather than only its bore, and sleeps in one.
 */
class LawnmowerCamouflageTest {
    private fun mower(sim: Simulation) = sim.organisms.first { it.brainKind == BrainKind.LAWNMOWER_BLENNY }
    private fun brain(sim: Simulation) = sim.agentOf(mower(sim))!!.brain as LawnmowerBlennyBrain

    @Test
    fun darkOnRockAndInCavesPaleWhenScared() {
        val sim = TestTank.sim(DefaultStock.all, seed = 4L, startHour = 9.0)
        var rockSum = 0.0; var rockN = 0; var coverSum = 0.0; var coverN = 0; var tick = 0
        sim.advance(2 * 3600.0) {
            if (++tick % 20 != 0) return@advance
            val o = mower(sim)
            if (o.posture == 3) { coverSum += o.skinTone; coverN++ }
            else if (o.posture == 1 && sim.rock.sdf.dist(o.pos) < 2.5) { rockSum += o.skinTone; rockN++ }
        }
        val b = brain(sim)
        assertTrue("perched on rock ($rockN samples) mean tone ${rockSum / rockN}", rockN > 20 && rockSum / rockN > 0.15)
        assertTrue("in cover ($coverN samples) mean tone ${coverSum / coverN}", coverN > 5 && coverSum / coverN > 0.3)
        assertTrue("went to a cave in the day (${b.caveVisits})", b.caveVisits >= 1)
        // a hard tap right by it: it blanches within seconds and makes for a cave
        val before = mower(sim).skinTone
        sim.enqueue(Command.TapGlass(mower(sim).pos))
        sim.advance(8.0)
        assertTrue("blanched after a scare: $before -> ${mower(sim).skinTone} worry ${b.worry} posture ${mower(sim).posture} ${mower(sim).behaviorName} z ${mower(sim).pos}", mower(sim).skinTone < before - 0.1)
        assertTrue("hides in a cave after a scare", b.cave != null)
    }

    @Test
    fun sleepsInACave() {
        val sim = TestTank.sim(DefaultStock.all, seed = 2L, startHour = 22.5)
        sim.advance(600.0)
        val o = mower(sim)
        val b = brain(sim)
        val cv = b.cave
        assertTrue("has a cave at night", cv != null)
        assertTrue("in the cave at night (posture ${o.posture}, ${o.behaviorName})", o.posture == 3 && cv!!.contains(o.pos, 1.0))
    }
}
