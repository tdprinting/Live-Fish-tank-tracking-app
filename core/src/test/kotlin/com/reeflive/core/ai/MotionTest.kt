package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.Command
import com.reeflive.core.sim.DefaultStock
import com.reeflive.core.sim.Gait
import com.reeflive.core.sim.MotionProfiles
import com.reeflive.core.sim.Organism
import com.reeflive.core.sim.Simulation
import com.reeflive.core.sim.TestTank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Locomotion model (SIMULATION §15) and the motion-specific behaviour states. */
class MotionTest {
    private fun Simulation.org(kind: BrainKind) = organisms.first { it.brainKind == kind }

    /** Sample once per sim second while advancing. */
    private fun Simulation.sampled(seconds: Double, sample: () -> Unit) {
        var acc = 0.0
        var left = seconds
        while (left > 1e-6) {
            val chunk = minOf(60.0, left)
            advance(chunk, behaviorDt = 0.1) { acc += 0.1; if (acc >= 1.0 - 1e-9) { acc = 0.0; sample() } }
            left -= chunk
        }
    }

    /** Roll of [o] away from upright about its own long axis, degrees. */
    private fun rollDeg(o: Organism): Double {
        val upright = Vec3.Y.rejectFrom(o.heading).normalized(Vec3.Y)
        return Math.toDegrees(Vec3.angle(upright, o.up.rejectFrom(o.heading).normalized(Vec3.Y)))
    }

    @Test
    fun strokeFollowsBainbridgeAndLabriformGaitTransition() {
        // body-caudal: tail-beat frequency rises linearly with speed (Bainbridge 1958) and Strouhal stays 0.2–0.4
        val card = MotionProfiles.of(BrainKind.CARDINAL)
        var last = 0.0
        for (bl in listOf(0.5, 1.0, 2.0, 4.0)) {
            val s = card.stroke(bl)
            assertTrue("tail Hz rises with speed", s.tailHz > last)
            last = s.tailHz
            val st = s.tailHz * 2 * s.amp / bl
            if (bl >= 1.0) assertTrue("Strouhal $st at $bl BL/s", st in 0.15..0.45)
        }
        // labriform: pectorals carry slow swimming, the caudal fin takes over past the gait transition (Drucker & Jensen 1996)
        val wrasse = MotionProfiles.of(BrainKind.WRASSE)
        assertEquals(Gait.LABRIFORM, wrasse.gait)
        val slow = wrasse.stroke(1.0)
        val fast = wrasse.stroke(wrasse.gaitTransitionBL * 1.5)
        assertTrue("slow is pectoral (${slow.caudalShare})", slow.caudalShare < 0.2)
        assertTrue("fast is caudal (${fast.caudalShare})", fast.caudalShare > 0.8)
        assertTrue("pectoral rate rises with speed", wrasse.stroke(2.0).pecHz > slow.pecHz)
        assertEquals(Gait.PERCH_HOP, MotionProfiles.of(BrainKind.HAWK).gait)
        assertEquals(Gait.ARM_ROW, MotionProfiles.of(BrainKind.BRITTLE_STAR).gait)
    }

    /** Owner: the clowns host the hairy green mushrooms; both stay within ~15 cm of the discs most of the day. */
    @Test
    fun clownsHostTheHairyMushroom() {
        val sim = TestTank.sim(DefaultStock.all, seed = 4, startHour = 10.0)
        val shroom = sim.corals.first { it.species.id == "rhodactis_sp" }
        val clowns = sim.organisms.filter { it.brainKind == BrainKind.CLOWN }
        assertEquals(2, clowns.size)
        val near = IntArray(2); var n = 0
        sim.sampled(3600.0) {
            n++
            clowns.forEachIndexed { i, c -> if (c.pos.distanceTo(shroom.pos) <= 15.0) near[i]++ }
        }
        clowns.forEachIndexed { i, c -> assertTrue("${c.nickname} near shroom ${near[i].toDouble() / n}", near[i] >= 0.8 * n) }
        // the host the reproduction code resolves is the mushroom
        val f = clowns.first()
        val host = sim.agentOf(f)!!.brain.anchorPos(sim.agentOf(f)!!.ctx, "host")!!
        assertTrue("host resolves to the shroom", host.distanceTo(shroom.pos) < 3.0)
    }

    /** No swim bladder: the hawkfish perches and hops; it never cruises in open water for long. */
    @Test
    fun hawkfishNeverCruises() {
        val sim = TestTank.sim(DefaultStock.all, seed = 2, startHour = 11.0)
        val hawk = sim.org(BrainKind.HAWK)
        var attached = 0; var n = 0; var run = 0; var longest = 0
        sim.sampled(3600.0) {
            n++
            if (hawk.attached) { attached++; run = 0 } else { run++; longest = maxOf(longest, run) }
        }
        assertTrue("perched share ${attached.toDouble() / n}", attached >= 0.85 * n)
        assertTrue("longest free-swimming bout $longest s", longest <= 25)
    }

    /** Pajama cardinal: hangs nearly still by cover by day, ranges out foraging after lights-off. */
    @Test
    fun cardinalIsMoreActiveAtNight() {
        fun pathCm(hour: Double): Double {
            val sim = TestTank.sim(DefaultStock.all, seed = 3, startHour = hour)
            val o = sim.org(BrainKind.CARDINAL)
            sim.advance(120.0, behaviorDt = 0.1)
            var last = o.pos; var path = 0.0
            sim.advance(900.0, behaviorDt = 0.1) { path += o.pos.distanceTo(last); last = o.pos }
            return path
        }
        val day = pathCm(12.0)
        val night = pathCm(23.0)
        assertTrue("night ${"%.0f".format(night)} cm vs day ${"%.0f".format(day)} cm per 15 min", night > 1.1 * day)
    }

    /** Blue tang sleeps lying over on its side in its crevice. */
    @Test
    fun tangRestsOnItsSideAtNight() {
        val sim = TestTank.sim(DefaultStock.all, seed = 1, startHour = 23.5)
        val tang = sim.org(BrainKind.TANG)
        sim.advance(600.0, behaviorDt = 0.1)
        var rolled = 0; var n = 0
        sim.sampled(300.0) { n++; if (tang.sleeping && rollDeg(tang) > 50.0) rolled++ }
        assertTrue("side-resting ${rolled.toDouble() / n}", rolled >= 0.7 * n)
    }

    /** A juvenile blue tang startled hard enough flips onto its side and plays dead for 3–8 s. */
    @Test
    fun tangPlaysDeadWhenStartled() {
        var episodes = 0
        var maxRoll = 0.0
        var held = 0
        for (seed in 1L..4L) {
            val sim = TestTank.sim(DefaultStock.all, seed = seed, startHour = 12.0)
            val tang = sim.org(BrainKind.TANG)
            val brain = sim.agentOf(tang)!!.brain as TangBrain
            sim.advance(30.0, behaviorDt = 0.1)
            repeat(12) {
                sim.enqueue(Command.TapGlass(tang.pos))
                val before = brain.playDeadCount
                var inPose = 0
                sim.advance(30.0, behaviorDt = 0.1) {
                    if (brain.playDeadCount > before && tang.attached) { inPose++; maxRoll = maxOf(maxRoll, rollDeg(tang)) }
                }
                if (brain.playDeadCount > before) { episodes++; held = maxOf(held, inPose) }
            }
        }
        assertTrue("episodes $episodes", episodes >= 2)
        assertTrue("rolled ${"%.0f".format(maxRoll)} deg", maxRoll > 60.0)
        // held for at least 3 s (30 ticks at 0.1 s)
        assertTrue("held $held ticks", held >= 30)
    }
}
