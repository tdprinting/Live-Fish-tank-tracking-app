package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.HermitLegs
import com.reeflive.core.sim.LimbRig
import com.reeflive.core.sim.TestTank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Hermit crab walking legs, chelae and shell withdrawal (SIMULATION §19.5, docs/research/hermit_crab_motion.md). */
class HermitLegsTest {
    private fun sim() = TestTank.sim(listOf(TestTank.seed("hermit1")), seed = 5L, startHour = 23.0)

    @Test
    fun legsStepInDiagonalPairs() {
        val s = sim()
        val o = s.organisms.first { it.brainKind == BrainKind.HERMIT }
        val rig = LimbRig.forKind(BrainKind.HERMIT, 1L) as HermitLegs
        o.heading = Vec3.X; o.up = Vec3.Y; o.extension = 1.0
        repeat(40) { rig.step(o, 0.05, 1.5) }
        fun ph(k: Int) = rig.out[k * 4]
        fun d(a: Int, b: Int) = abs(((ph(a) - ph(b) + 1.5f) % 1f) - 0.5f)
        // left P2 (0) steps with right P3 (3), against right P2 (1) and left P3 (2)
        assertTrue("L2/R3 together", d(0, 3) < 0.12f)
        assertTrue("L2/R2 antiphase", d(0, 1) > 0.38f)
        assertTrue("L2/L3 antiphase", d(0, 2) > 0.38f)
        assertTrue("stride frequency ${rig.strideHz}", rig.strideHz in 0.8..3.5)
        assertTrue("shell rolls while walking", rig.out[6 * 4 + 1] > 0.05f)
    }

    @Test
    fun snapsIntoTheShellAndComesOutEyesFirst() {
        val s = sim()
        val o = s.organisms.first { it.brainKind == BrainKind.HERMIT }
        val rig = LimbRig.forKind(BrainKind.HERMIT, 2L) as HermitLegs
        o.extension = 0.0
        repeat(5) { rig.step(o, 0.05, 0.0) }
        assertTrue("withdrawn within 0.25 s (${rig.retract})", rig.retract > 0.9)
        o.extension = 1.0
        repeat(30) { rig.step(o, 0.05, 0.0) }
        val eyes = rig.out[7 * 4 + 3]; val leg = rig.out[0 * 4 + 3]
        assertTrue("eyestalks out before the legs (eyes $eyes, legs $leg)", eyes < leg)
        repeat(400) { rig.step(o, 0.05, 0.0) }
        assertTrue("fully out again", rig.out[0 * 4 + 3] < 0.05f && rig.out[7 * 4 + 3] < 0.05f)
    }

    @Test
    fun picksWithAlternatingClawsInTheSim() {
        val s = sim()
        val o = s.organisms.first { it.brainKind == BrainKind.HERMIT }
        o.hunger = 0.9
        s.advance(900.0, behaviorDt = 0.1)
        val rig = o.limbRig as HermitLegs
        assertEquals(5, o.toPose().motionMode)
        assertEquals(LimbRig.LIMBS * 4, o.toPose().limbs.size)
        assertTrue("claw strokes ${rig.pickStrokes}", rig.pickStrokes > 20)
        // the two chelae work half a cycle apart
        val dph = abs(((rig.out[4 * 4] - rig.out[5 * 4] + 1.5f) % 1f) - 0.5f)
        assertTrue("alternating chelae", dph > 0.45f)
    }
}
