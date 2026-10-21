package com.reeflive.core.sim

import com.reeflive.core.ai.EcseniusStyle
import com.reeflive.core.ai.PairMatrix
import com.reeflive.core.ai.PeekerBlennyBrain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bicolor and midas blennies (research 50): both run the Ecsenius hole brain, but the bicolor stays low grazing round
 * its hole while the midas spends its day up in the water column picking plankton; they squabble with other blennies,
 * never share a hole, and leave a hole they have outgrown instead of squeezing into it.
 */
class EcseniusBlenniesTest {
    /** Adds [id] with its real sheet (the test catalog holds only the owner's stock; the app loads sheets the same way). */
    private fun add(sim: Simulation, id: String, cm: Double, name: String) {
        sim.species.ensure(id)
        sim.enqueue(Command.AddOrganism(id, cm, name))
    }

    private fun sheet(id: String) = SimSpecies.parse(javaClass.classLoader!!.getResourceAsStream("species/$id.json")!!.bufferedReader().readText())

    @Test
    fun sheetsResolveToTheEcseniusBrainWithTheirOwnStyle() {
        val bic = sheet("ecsenius_bicolor")
        val mid = sheet("ecsenius_midas")
        assertEquals(BrainKind.PEEKER_BLENNY, bic.brainKind)
        assertEquals(BrainKind.PEEKER_BLENNY, mid.brainKind)
        assertEquals(13.0, mid.maxLengthCm, 0.01)
        assertEquals(EcseniusStyle.BICOLOR, EcseniusStyle.of(bic.id))
        assertEquals(EcseniusStyle.MIDAS, EcseniusStyle.of(mid.id))
        assertEquals(EcseniusStyle.TAILSPOT, EcseniusStyle.of("ecsenius_stigmatura"))
        // the midas holds station in open water: it sinks far slower between beats than a perching blenny
        assertTrue(MotionProfiles.of(BrainKind.PEEKER_BLENNY, mid.id).sinkCmS < MotionProfiles.of(BrainKind.PEEKER_BLENNY).sinkCmS)
        assertTrue(Stocking.CATALOG.any { it.speciesId == bic.id } && Stocking.CATALOG.any { it.speciesId == mid.id })
        // the squabble rows apply to their own species only
        val rival = PairMatrix.row("midas_rival")!!
        assertEquals(listOf("ecsenius_midas"), rival.actorSpecies)
        assertNotNull(PairMatrix.row("bicolor_rival"))
        assertEquals(listOf("blenny_cap"), compatibilityWarnings("ecsenius_midas", listOf("ecsenius_bicolor"), 227.0).map { it.code })
    }

    /** Share of daytime ticks [id] spends out in open water (5 cm clear of rock, off the sand), alone with the owner's non-blenny stock (no rival to hide from). */
    private fun highShare(id: String, seed: Long): Pair<Double, PeekerBlennyBrain> {
        val stock = DefaultStock.all.filter { TestTank.catalog[it.speciesId].brainKind.let { k -> k != BrainKind.PEEKER_BLENNY && k != BrainKind.LAWNMOWER_BLENNY } }
        val sim = TestTank.sim(stock, seed = seed, startHour = 9.0)
        add(sim, id, 5.5, "B")
        var high = 0; var n = 0; var tick = 0
        sim.advance(5400.0) {
            if (++tick % 4 != 0) return@advance
            val o = sim.organisms.firstOrNull { it.nickname == "B" } ?: return@advance
            n++
            if (sim.rock.sdf.dist(o.pos) > 5.0 && o.pos.y > sim.sand.surfaceY(o.pos.x, o.pos.z) + 5.0) high++
        }
        val o = sim.organisms.first { it.nickname == "B" }
        return high.toDouble() / n to sim.agentOf(o)!!.brain as PeekerBlennyBrain
    }

    @Test
    fun midasForagesUpInTheWaterWhileTheBicolorStaysOnTheRock() {
        val (midF, mb) = highShare("ecsenius_midas", 4L)
        val (bicF, _) = highShare("ecsenius_bicolor", 4L)
        assertTrue("midas open-water bouts ${mb.midwaterBouts} (high $midF, bicolor $bicF)", mb.midwaterBouts >= 5)
        assertTrue("midas in open water $midF vs bicolor $bicF", midF > 0.15 && midF > 3 * bicF)
    }

    @Test
    fun newEcseniusInTheFullTankSquabbleButNeverShareAHole() {
        val sim = TestTank.sim(DefaultStock.all, seed = 3L, startHour = 10.0)
        add(sim, "ecsenius_bicolor", 5.0, "Bic")
        add(sim, "ecsenius_midas", 5.5, "Mid")
        val holes = sim.rock.holes.all.filter { it.singleOccupant }
        var shared = 0; var tick = 0
        sim.advance(2 * 3600.0) {
            if (++tick % 4 != 0) return@advance
            for (h in holes) if (sim.organisms.count { it.alive && h.holds(it.pos) } > 1) shared++
        }
        assertEquals("ticks with two heads in one hole", 0, shared)
        val held = sim.organisms.filter { it.alive }.flatMap { sim.holeClaims.heldBy(it) }
        assertEquals("a hole held twice", held.size, held.toSet().size)
        // the blennies fought: some Ecsenius was chased by another blenny
        val ecs = sim.organisms.filter { it.brainKind == BrainKind.PEEKER_BLENNY }
        val blennyIds = sim.organisms.filter { it.brainKind == BrainKind.PEEKER_BLENNY || it.brainKind == BrainKind.LAWNMOWER_BLENNY }.map { it.id }.toSet()
        assertTrue("an Ecsenius was harassed by a blenny", ecs.any { e -> e.relations.any { (k, r) -> k in blennyIds && r.harassedBy > 0.0 } })
    }

    @Test
    fun aBlennyTooBigForEveryHoleNeverEntersOne() {
        val sim = TestTank.sim(DefaultStock.all, seed = 5L, startHour = 9.0)
        // an adult midas (13 cm x 0.18 deep) is thicker than every bore in the owner's rock
        add(sim, "ecsenius_midas", 13.0, "Big")
        var inside = 0
        sim.advance(3600.0) {
            val big = sim.organisms.firstOrNull { it.nickname == "Big" } ?: return@advance
            if (sim.rock.holes.all.any { it.singleOccupant && it.holds(big.pos) }) inside++
        }
        val big = sim.organisms.first { it.nickname == "Big" }
        assertEquals(null, (sim.agentOf(big)!!.brain as PeekerBlennyBrain).hole)
        assertEquals("ticks inside a hole too narrow for it", 0, inside)
    }

    @Test
    fun aGrownBlennyGivesUpItsHole() {
        val sim = TestTank.sim(listOf(TestTank.seed("tailspot")), seed = 7L, startHour = 9.0)
        add(sim, "ecsenius_midas", 4.0, "Grow")
        sim.advance(60.0)
        val m = sim.organisms.first { it.nickname == "Grow" }
        val brain = sim.agentOf(m)!!.brain as PeekerBlennyBrain
        val first = brain.hole
        assertNotNull("a juvenile midas fits a blenny hole", first)
        m.lengthCm = 12.5
        sim.advance(700.0)
        val now = brain.hole
        assertTrue("moved to a hole that fits or gave it up: ${now?.name}", now == null || now.fits(m))
        assertFalse("still holds the outgrown hole", first!!.name in sim.holeClaims.heldBy(m) && !first.fits(m))
    }

    @Test
    fun aHomelessBlennyIsStressedAndFightsForAHole() {
        // no lawnmower: the four blenny holes go to the first four Ecsenius, the fifth has none
        val stock = DefaultStock.all.filter { TestTank.catalog[it.speciesId].brainKind != BrainKind.LAWNMOWER_BLENNY }
        val sim = TestTank.sim(stock, seed = 11L, startHour = 9.0)
        for ((k, id) in listOf("ecsenius_bicolor", "ecsenius_midas", "ecsenius_stigmatura", "ecsenius_bicolor").withIndex()) add(sim, id, 5.0 + 0.2 * k, "E$k")
        sim.advance(30.0)
        val ecs = sim.organisms.filter { it.brainKind == BrainKind.PEEKER_BLENNY }
        assertEquals(5, ecs.size)
        val brains = ecs.map { sim.agentOf(it)!!.brain as PeekerBlennyBrain }
        val homeless = ecs.filterIndexed { i, _ -> brains[i].hole == null }
        assertTrue("one blenny has no hole", homeless.isNotEmpty())
        assertTrue("homeless blenny is stressed ${homeless[0].socialStress}", homeless.all { it.socialStress >= PeekerBlennyBrain.HOMELESS_STRESS - 1e-9 })
        val holes = sim.rock.holes.all.filter { it.singleOccupant }
        var shared = 0; var tick = 0
        sim.advance(2 * 3600.0) {
            if (++tick % 4 != 0) return@advance
            for (h in holes) if (sim.organisms.count { it.alive && h.holds(it.pos) } > 1) shared++
        }
        assertTrue("hole fights ${brains.sumOf { it.holeFights }}", brains.sumOf { it.holeFights } >= 1)
        assertEquals("ticks with two heads in one hole", 0, shared)
        val held = sim.organisms.filter { it.alive }.flatMap { sim.holeClaims.heldBy(it) }
        assertEquals("a hole held twice", held.size, held.toSet().size)
    }
}
