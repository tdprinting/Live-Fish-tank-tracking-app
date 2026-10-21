package com.reeflive.core.sim

import com.reeflive.core.ai.PorcelainCrabBrain
import com.reeflive.core.custom.economy.PriceBook
import com.reeflive.core.custom.economy.Stores
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.min

/** Anemone porcelain crab (docs/research/45_clam_crab_star_cucumber.md §2). */
class PorcelainCrabTest {
    private val id = "neopetrolisthes_maculatus"
    private val bta = "entacmaea_quadricolor"

    private fun sim(hour: Double, anemone: Boolean, vararg crabs: Pair<String, Double>): Simulation {
        val sim = TestTank.sim(emptyList(), seed = 6L, startHour = hour)
        if (anemone) { sim.species.ensure(bta); sim.addFromSeed(LivestockSeed(bta, "Bubbles", 8.0, id = "an")) }
        sim.species.ensure(id)
        for ((n, len) in crabs) sim.addFromSeed(LivestockSeed(id, n, len, id = n))
        sim.advance(1.0, behaviorDt = 0.05)
        return sim
    }

    @Test fun sheetRigAndStore() {
        val sim = sim(12.0, false, "p" to 1.8)
        val o = sim.organisms.first { it.id == "p" }
        assertEquals("models/porcelain_crab.glb", o.species.model)
        assertEquals(BrainKind.PORCELAIN_CRAB, o.brainKind)
        assertEquals(3, o.toPose().motionMode)
        assertTrue(o.limbRig is CrabLegs)
        assertTrue(BrainKind.PORCELAIN_CRAB.isCrab)
        assertEquals(18 * PriceBook.SCALE, PriceBook.listing(id)!!.price)
        assertTrue(Stocking.entry(id) != null)
        assertTrue(Stores.warning(sim, id)!!.contains("anemone"))
    }

    @Test fun livesAtItsAnemoneFanningOnPlantedLegs() {
        val sim = sim(14.0, true, "p" to 1.8)
        val o = sim.organisms.first { it.id == "p" }
        val an = sim.organisms.first { it.id == "an" }
        val b = sim.agentOf(o)!!.brain as PorcelainCrabBrain
        val legs = o.limbRig as CrabLegs
        val surf = sim.crawlSurface
        val sweeps0 = legs.fanSweeps
        var samples = 0; var bodyIn = 0; var footIn = 0; var fanned = 0
        sim.advance(1800.0, behaviorDt = 0.05) {
            if (o.toPose().limbs.let { it.size > 35 && it[35] > 0.5f }) fanned++
            val seat = o.seat ?: return@advance
            if (!o.alive || !o.visible || !o.seatOn || seat.blend < 1.0) return@advance
            samples++
            val m = seat.crab!!
            val c = seat.shape.contacts
            var minD = Double.MAX_VALUE
            var i = 0
            while (i < m.stanceFrom * 3) { minD = min(minD, surf.dist(seat.world(c[i], c[i + 1], c[i + 2], o.lengthCm))); i += 3 }
            if (minD < -0.1) bodyIn++
            for (k in 0 until 8) {
                if (seat.legTucked[k]) continue
                if (surf.dist(seat.footTip[k]) < -0.1) footIn++
            }
        }
        val sweeps = legs.fanSweeps - sweeps0
        println("porcelain crab: host ${b.host?.id}, %.1f cm from it, $samples seated, sweeps $sweeps, fanned $fanned ticks, body in $bodyIn, feet in $footIn (${o.behaviorName})".format(o.pos.distanceTo(an.pos)))
        assertTrue("its host is the anemone", b.host === an)
        assertTrue("beside its anemone (${o.pos.distanceTo(an.pos)} cm)", o.pos.distanceTo(an.pos) < 10.0)
        assertTrue("fans sweep about once a second each ($sweeps in 30 min)", sweeps > 1500)
        assertTrue("seated", samples > 10000)
        assertTrue("carapace in the surface on $bodyIn ticks", bodyIn <= samples / 500)
        assertTrue("feet in the surface on $footIn leg-ticks", footIn <= samples / 200)
    }

    @Test fun aPairSharesTheHostAndAThirdIsShovedOff() {
        val sim = sim(14.0, true, "a" to 2.0, "b" to 1.8, "c" to 1.4)
        val an = sim.organisms.first { it.id == "an" }
        sim.advance(1800.0, behaviorDt = 0.05)
        val brains = listOf("a", "b", "c").map { n -> sim.agentOf(sim.organisms.first { it.id == n })!!.brain as PorcelainCrabBrain }
        println("porcelain hosts: ${brains.map { it.host?.id }}, spars ${brains.sumOf { it.spars }}")
        assertEquals("two on the anemone", 2, brains.count { it.host === an })
    }

    @Test fun scuttlesUnderItsAnemoneFromATap() {
        val sim = sim(14.0, true, "p" to 1.8)
        val o = sim.organisms.first { it.id == "p" }
        sim.advance(300.0, behaviorDt = 0.05)
        val b = sim.agentOf(o)!!.brain as PorcelainCrabBrain
        b.onTap(sim.agentOf(o)!!.ctx, o.pos, 0.6)
        sim.advance(2.0, behaviorDt = 0.05)
        assertEquals(1, b.retreats)
        assertEquals("Scuttling back under its anemone", o.behaviorName)
    }
}
