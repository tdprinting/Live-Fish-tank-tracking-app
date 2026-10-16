package com.reeflive.core.sim

import com.reeflive.core.ai.LawnmowerBlennyBrain
import com.reeflive.core.ai.PeekerBlennyBrain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One animal per hole (owner bug 2026-10-14, Tee clip B: two blennies used the same hole; SIMULATION §24): a
 * single-occupant hole is claimed by one animal and never entered by another. Over hours of the full stock, plus a
 * second tailspot added to force a newcomer to look for a hole, no blenny hole or small cave ever has two heads in it.
 */
class HoleOccupancyTest {
    private fun checkRun(sim: Simulation, seconds: Double, label: String): Int {
        val holes = sim.rock.holes.all.filter { it.singleOccupant }
        assertTrue("single-occupant holes in the rock", holes.isNotEmpty())
        var worst = ""
        var bad = 0
        var occupied = 0
        var tick = 0
        sim.advance(seconds) {
            if (++tick % 2 != 0) return@advance
            for (h in holes) {
                var n = 0
                var who = ""
                for (o in sim.organisms) if (o.alive && h.holds(o.pos)) { n++; who += " ${o.nickname}" }
                if (n >= 1) occupied++
                if (n > 1) { bad++; if (worst.isEmpty()) worst = "$label t=${"%.0f".format(sim.simTimeSec)} ${h.name}:$who" }
            }
        }
        assertEquals("ticks with two heads in one hole; first: $worst", 0, bad)
        return occupied
    }

    @Test
    fun neverTwoHeadsInOneHole() {
        var occupied = 0
        for ((seed, hour) in listOf(1L to 9.0, 2L to 19.5)) {
            val sim = TestTank.sim(DefaultStock.all, seed = seed, startHour = hour)
            sim.enqueue(Command.AddOrganism("ecsenius_stigmatura", 5.5, "Newspot"))
            occupied += checkRun(sim, 5400.0, "seed $seed")
            // the claims: each blenny holds its own hole, never one another animal holds
            val held = sim.organisms.filter { it.alive }.associateWith { sim.holeClaims.heldBy(it) }
            val all = held.values.flatten()
            assertEquals("a hole held twice: $held", all.size, all.toSet().size)
            val lmb = sim.organisms.first { it.brainKind == BrainKind.LAWNMOWER_BLENNY }
            val spots = sim.organisms.filter { it.brainKind == BrainKind.PEEKER_BLENNY }
            assertEquals(2, spots.size)
            val lh = (sim.agentOf(lmb)!!.brain as LawnmowerBlennyBrain).hole
            val sh = spots.map { (sim.agentOf(it)!!.brain as PeekerBlennyBrain).hole }
            assertTrue("the lawnmower has a hole", lh != null)
            for (h in sh) assertNotEquals("a tailspot defaults to the lawnmower's hole", lh?.name, h?.name)
            if (sh.all { it != null }) assertNotEquals("the two tailspots share a hole", sh[0]!!.name, sh[1]!!.name)
        }
        assertTrue("the blennies used their holes ($occupied hole-ticks)", occupied > 0)
    }

    @Test
    fun aClaimedHoleIsNotUsableByAnother() {
        val sim = TestTank.sim(DefaultStock.all, seed = 3L, startHour = 10.0)
        val lmb = sim.organisms.first { it.brainKind == BrainKind.LAWNMOWER_BLENNY }
        val ts = sim.organisms.first { it.brainKind == BrainKind.PEEKER_BLENNY }
        val lh = (sim.agentOf(lmb)!!.brain as LawnmowerBlennyBrain).hole!!
        assertTrue(sim.holeClaims.usableBy(lh, lmb))
        assertTrue("the tailspot cannot take the lawnmower's hole", !sim.holeClaims.claim(lh, ts))
        // the holder gone (dead), the hole is free again
        lmb.alive = false
        assertTrue(sim.holeClaims.claim(lh, ts))
    }
}
