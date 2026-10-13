package com.reeflive.core.sim

import com.reeflive.core.ai.GobyBrain
import com.reeflive.core.ai.PairBouts
import com.reeflive.core.ai.PairMatrix
import com.reeflive.core.geom.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The five open gaps of research 06 §6 as resolved by research 07 (docs/research/07_gap_sources.md). */
class ResearchGapsTest {

    private fun Simulation.org(kind: BrainKind) = organisms.first { it.brainKind == kind }
    private fun Simulation.pairBouts(o: Organism) = agentOf(o)!!.brain.utility.behaviors.filterIsInstance<PairBouts>().first()

    /** Gap 1: lawnmower chases ~90 % of intruding blennies of its size or smaller, ~20 % of damsel-like grazers; no larger blenny. */
    @Test
    fun gap1_lawnmowerChaseProportions() {
        val sim = TestTank.sim(DefaultStock.all, seed = 1L, startHour = 11.0)
        sim.advance(1.0)
        val lmb = sim.org(BrainKind.LAWNMOWER_BLENNY)
        val ts = sim.org(BrainKind.PEEKER_BLENNY)
        val ctx = sim.agentOf(lmb)!!.ctx
        val pb = sim.pairBouts(lmb)
        val row = PairMatrix.row("lmb_tailspot")!!
        assertEquals("sourced intrusion probability", 0.9, row.pEvent, 1e-9)
        assertEquals("damsel analogue (tang)", 0.2, PairMatrix.row("lmb_tang")!!.pEvent, 1e-9)
        lmb.hunger = 0.6
        assertTrue("a smaller blenny is chased", pb.probability(ctx, row, ts) > 0.0)
        val small = ts.lengthCm
        ts.lengthCm = 1.3 * lmb.lengthCm
        assertEquals("a larger blenny is not", 0.0, pb.probability(ctx, row, ts), 0.0)
        ts.lengthCm = small
        // the dottyback rows carry no sourced rate: their evidence says so
        assertTrue(PairMatrix.rows.filter { it.actor == "DOTTYBACK" }.all { "[I]" in it.evidence })
    }
}
