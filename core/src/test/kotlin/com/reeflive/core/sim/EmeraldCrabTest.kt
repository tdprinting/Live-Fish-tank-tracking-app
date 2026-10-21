package com.reeflive.core.sim

import com.reeflive.core.ai.EmeraldCrabBrain
import com.reeflive.core.custom.economy.PriceBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.min

/** Emerald crab (research 43 §1): hides in a crevice by day, scoops algae at night on planted legs, darts home from a tap. */
class EmeraldCrabTest {
    private val id = "mithraculus_sculptus"

    private fun sim(hour: Double, seed: Long = 7L): Pair<Simulation, Organism> {
        val sim = TestTank.sim(DefaultStock.corals, seed = seed, startHour = hour)
        sim.species.ensure(id)
        sim.enqueue(Command.AddOrganism(id, 3.0, "Emmy"))
        sim.advance(1.0, behaviorDt = 0.05)
        return sim to sim.organisms.first { it.brainKind == BrainKind.EMERALD_CRAB }
    }

    @Test fun sheetRigAndStore() {
        val (_, o) = sim(12.0)
        assertEquals("models/emerald_crab.glb", o.species.model)
        assertEquals(3, o.toPose().motionMode)
        assertTrue(o.limbRig is CrabLegs)
        assertTrue(BrainKind.EMERALD_CRAB.isCrab)
        assertEquals(GrazerType.EMERALD_CRAB, GrazerType.of(o))
        assertTrue("bubble algae first", GrazerType.EMERALD_CRAB.pref[AlgaeGroup.BUBBLE.ordinal] >= 1.0)
        assertEquals(12 * PriceBook.SCALE, PriceBook.listing(id)!!.price)
        assertTrue(Stocking.entry(id) != null)
    }

    @Test fun hidesByDayAndGrazesAtNightOnPlantedLegs() {
        // noon: wedged in its crevice
        val (day, d) = sim(12.0)
        day.advance(1800.0, behaviorDt = 0.05)
        assertEquals("hides by day", "Hiding in a crevice", d.behaviorName)

        val (sim, o) = sim(21.0)
        o.hunger = 0.5
        val b = sim.agentOf(o)!!.brain as EmeraldCrabBrain
        val surf = sim.crawlSurface
        var samples = 0; var bodyIn = 0; var footIn = 0; var worstFoot = 0.0; var moved = 0.0
        var last = o.pos
        sim.advance(2 * 3600.0, behaviorDt = 0.05) {
            val seat = o.seat ?: return@advance
            if (!o.alive || !o.visible || !o.seatOn || seat.blend < 1.0) return@advance
            samples++
            moved += o.pos.distanceTo(last); last = o.pos
            val m = seat.crab!!
            val c = seat.shape.contacts
            var minD = Double.MAX_VALUE
            var i = 0
            while (i < m.stanceFrom * 3) { minD = min(minD, surf.dist(seat.world(c[i], c[i + 1], c[i + 2], o.lengthCm))); i += 3 }
            if (minD < -0.1) bodyIn++
            for (k in 0 until 8) {
                if (seat.legTucked[k]) continue
                val dd = surf.dist(seat.footTip[k])
                worstFoot = min(worstFoot, dd)
                if (dd < -0.1) footIn++
            }
        }
        println("emerald crab: $samples seated ticks, moved %.0f cm, scoops ${b.scoops}, body in $bodyIn, feet in $footIn (worst %.2f)".format(moved, -worstFoot))
        assertTrue("seated", samples > 5000)
        assertTrue("walks about at night", moved > 20)
        assertTrue("scoops algae (${b.scoops})", b.scoops > 10)
        assertTrue("carapace in the surface on $bodyIn ticks", bodyIn <= samples / 500)
        assertTrue("feet in the surface on $footIn leg-ticks", footIn <= samples / 200)
    }

    @Test fun dartsHomeFromATap() {
        val (sim, o) = sim(22.0)
        sim.advance(600.0, behaviorDt = 0.05)
        val b = sim.agentOf(o)!!.brain as EmeraldCrabBrain
        b.onTap(sim.agentOf(o)!!.ctx, o.pos, 0.6)
        sim.advance(2.0, behaviorDt = 0.05)
        assertEquals(1, b.retreats)
        assertEquals("Darting back into its crevice", o.behaviorName)
    }

    @Test fun aHungryCrabNipsSoftCoralsAtNight() {
        val (sim, o) = sim(22.0)
        val b = sim.agentOf(o)!!.brain as EmeraldCrabBrain
        val zoa = sim.corals.first { EmeraldCrabBrain.weight(it.kind) > 0 }
        o.homePos = zoa.pos + zoa.normal * 2.0
        o.placeAt(o.homePos, o.heading)
        sim.advance(3600.0, behaviorDt = 0.05) { o.hunger = 0.9 }
        println("emerald crab nips: ${b.coralNips}")
        assertTrue("nipped polyps (${b.coralNips})", b.coralNips > 0)
    }
}
