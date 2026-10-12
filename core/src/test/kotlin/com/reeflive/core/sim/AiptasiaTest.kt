package com.reeflive.core.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Aiptasia spread and controllers (coverage gap C-4; research 01 §4.4, 03 C2, D). */
class AiptasiaTest {
    private fun tank(stock: List<LivestockSeed> = DefaultStock.all, seed: Long = 7L) = TestTank.sim(stock, seed = seed)

    private fun Simulation.seedAiptasia(n: Int, mature: Boolean = true): Simulation {
        enqueue(Command.AddAiptasia(n)); advanceAbstract(60.0)
        corals.filter { it.kind == CoralKind.AIPTASIA }.forEach { it.size = if (mature) 0.8 else 0.3 }
        return this
    }

    /** 03 C2: they spread by pedal laceration; three polyps become a colony within two months. */
    @Test
    fun spreadsByPedalLaceration() {
        val sim = tank().seedAiptasia(3)
        assertEquals(3, sim.aiptasiaCount)
        sim.advanceAbstract(60 * 86400.0)
        val n = sim.aiptasiaCount
        println("C-4 aiptasia after 60 d: $n polyps (from 3)")
        assertTrue("aiptasia spread: $n", n >= 8)
        val lacerates = sim.corals.filter { it.kind == CoralKind.AIPTASIA && it.anchor.isEmpty() }
        assertTrue("lacerates sit on rock near a parent", lacerates.all { sim.rock.sdf.dist(it.pos) < 1.0 && it.pos.y > sim.rock.sandTopCm })
    }

    /** S3: disturbing them spreads them; chemical treatment can leave fragments; moving rock tears polyps. */
    @Test
    fun disturbanceSpreadsThem() {
        val calm = tank().seedAiptasia(3)
        val moved = tank().seedAiptasia(3)
        repeat(4) { moved.enqueue(Command.RearrangeRock); moved.advanceAbstract(60.0) }
        println("C-4 after 4 rock moves: ${moved.aiptasiaCount} vs ${calm.aiptasiaCount}")
        assertTrue("rock moves fragment polyps", moved.aiptasiaCount > calm.aiptasiaCount)

        val treated = tank(seed = 11L).seedAiptasia(3)
        repeat(6) { treated.enqueue(Command.RearrangeRock); treated.advanceAbstract(60.0) }
        val before = treated.aiptasiaCount
        treated.enqueue(Command.RemoveAiptasia(before, chemical = true)); treated.advanceAbstract(60.0)
        println("C-4 chemical removal of $before polyps leaves ${treated.aiptasiaCount} fragments")
        assertTrue("injection kills most", treated.aiptasiaCount < before / 2)
    }

    /** S3: peppermint shrimp eat small aiptasia but may ignore established ones; Berghia eat any and starve after. */
    @Test
    fun controllers() {
        fun run(mature: Boolean, c: PestController?, n: Int, stock: List<LivestockSeed> = DefaultStock.all): Simulation {
            val sim = tank(stock).seedAiptasia(3, mature)
            repeat(2) { sim.enqueue(Command.RearrangeRock); sim.advanceAbstract(60.0) }
            sim.corals.filter { it.kind == CoralKind.AIPTASIA }.forEach { it.size = if (mature) 0.8 else 0.3 }
            if (c != null) sim.enqueue(Command.AddPestControl(c, n))
            sim.advanceAbstract(30 * 86400.0)
            return sim
        }
        val none = run(false, null, 0)
        val pepSmall = run(false, PestController.PEPPERMINT_SHRIMP, 2)
        val pepBig = run(true, PestController.PEPPERMINT_SHRIMP, 2)
        val berghia = run(true, PestController.BERGHIA, 4, stock = DefaultStock.corals)
        println("C-4 30 d: none ${none.aiptasiaCount}, peppermint on small ${pepSmall.aiptasiaCount}, on established ${pepBig.aiptasiaCount}, berghia ${berghia.aiptasiaCount} (berghia left ${"%.1f".format(berghia.pests.berghia)})")
        assertTrue("peppermint clears small ones", pepSmall.aiptasiaCount < none.aiptasiaCount / 2)
        assertTrue("established ones survive peppermint better", pepBig.aiptasiaCount > pepSmall.aiptasiaCount)
        assertTrue("berghia clear established ones", berghia.aiptasiaCount <= 1)
        berghia.advanceAbstract(45 * 86400.0)
        assertTrue("berghia starve once the aiptasia are gone: ${berghia.pests.berghia}", berghia.aiptasiaCount > 0 || berghia.pests.berghia == 0.0)

        // with the cleaner wrasse and shrimp in the tank the nudibranchs get eaten
        val eaten = tank().also { it.enqueue(Command.AddPestControl(PestController.BERGHIA, 4)) }
        val safe = tank(DefaultStock.corals).also { it.enqueue(Command.AddPestControl(PestController.BERGHIA, 4)) }
        eaten.seedAiptasia(3); safe.seedAiptasia(3)
        eaten.advanceAbstract(10 * 86400.0); safe.advanceAbstract(10 * 86400.0)
        assertTrue("predators cut berghia: ${eaten.pests.berghia} vs ${safe.pests.berghia}", eaten.pests.berghia < safe.pests.berghia)
    }
}
