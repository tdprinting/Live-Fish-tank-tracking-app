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
        // budding is stochastic: judge the median of five tanks, not one seed's draw (one seed in ten ends near 6-7)
        val sims = (1L..5L).map { s -> tank(seed = s).seedAiptasia(3).also { assertEquals(3, it.aiptasiaCount); it.advanceAbstract(60 * 86400.0) } }
        val sim = sims.sortedBy { it.aiptasiaCount }[2]
        val n = sim.aiptasiaCount
        println("C-4 aiptasia after 60 d: ${sims.map { it.aiptasiaCount }} polyps (from 3), median $n")
        assertTrue("aiptasia spread: $n", n >= 8)
        for (s in sims) {
            val lacerates = s.corals.filter { it.kind == CoralKind.AIPTASIA && it.anchor.isEmpty() }
            assertTrue("lacerates sit on rock near a parent", lacerates.all { s.rock.sdf.dist(it.pos) < 1.0 && it.pos.y > s.rock.sandTopCm })
        }
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

    /** aiptasia_control.md §2-5: an open polyp dosed dies over the product's time; a closed one mostly escapes. */
    @Test
    fun treatmentsKillOpenPolypsAndMissClosedOnes() {
        var allOpen = 0; var allClosed = 0
        for (t in AiptasiaTreatment.entries) {
            var killedOpen = 0; var killedClosed = 0; var runs = 0
            for (seed in 1L..16L) {
                val sim = tank(DefaultStock.corals, seed).seedAiptasia(3)
                val pair = sim.corals.filter { it.kind == CoralKind.AIPTASIA }
                if (pair.size < 2 || runs == 12) continue
                runs++
                val a = pair[0]; val b = pair[1]
                a.extension = 1.0; a.disturb = 0.0
                b.extension = 0.1
                sim.enqueue(Command.TreatAiptasia(t, a.id)); sim.enqueue(Command.TreatAiptasia(t, b.id))
                sim.advanceAbstract(t.dieSec + 180.0)
                if (sim.corals.none { it.id == a.id }) killedOpen++
                if (sim.corals.none { it.id == b.id }) killedClosed++
            }
            println("treat ${t.label}: open killed $killedOpen/$runs, closed killed $killedClosed/$runs")
            assertTrue("enough tanks with two polyps", runs >= 8)
            assertTrue("${t.label} kills most open polyps", killedOpen * 2 > runs)
            if (t != AiptasiaTreatment.SCRAPE) { allOpen += killedOpen; allClosed += killedClosed }
        }
        assertTrue("closed polyps escape more often: $allClosed vs $allOpen", allClosed < allOpen * 3 / 4)
    }

    /** A dosed polyp shrinks, pales and stays pulled in while it dies (the renderer draws exactly these fields). */
    @Test
    fun treatedPolypShrivels() {
        val sim = tank(DefaultStock.corals).seedAiptasia(1)
        val p = sim.corals.first { it.kind == CoralKind.AIPTASIA }
        p.extension = 1.0
        p.treatment = AiptasiaTreatment.KALK_PASTE; p.treatLeftSec = AiptasiaTreatment.KALK_PASTE.dieSec
        sim.advanceAbstract(900.0)
        assertTrue("half dead: ${p.tissue}", p.tissue in 0.3..0.7)
        assertTrue("whitened: ${p.bleach}", p.bleach > 0.3)
        assertTrue("pulled in: ${p.extension}", p.extension < 0.5)
        sim.advanceAbstract(1200.0)
        assertEquals(0, sim.aiptasiaCount)
    }

    /** Berghia eat one polyp at a time, slowly, faster at night; [SimSnapshot.berghiaPrey] names it for the renderer. */
    @Test
    fun berghiaGrazeOnePolypAtATime() {
        val sim = tank(DefaultStock.corals).seedAiptasia(3)
        sim.enqueue(Command.AddPestControl(PestController.BERGHIA, 3))
        sim.advanceAbstract(6 * 3600.0)
        val prey = sim.pests.berghiaPrey
        assertTrue("a polyp is being eaten", prey != null)
        val bitten = sim.corals.first { it.id == prey }
        assertTrue("the prey is being eaten: ${bitten.tissue}", bitten.tissue < 0.99)
        assertEquals(prey, sim.snapshot().berghiaPrey)
    }

    /** My tank keeps its Berghia and peppermint shrimp across a save. */
    @Test
    fun pestsSurviveSave() {
        val st = PestState().apply { berghia = 4.5; peppermint = 2 }
        val back = PestState().apply { restore(st.save()) }
        assertEquals(4.5, back.berghia, 1e-9)
        assertEquals(2, back.peppermint)
    }

    /** A hitchhiker turns up on the rock right beside the frag it came in on. */
    @Test
    fun hitchhikerSitsBesideItsFrag() {
        val sim = tank(DefaultStock.corals)
        val frag = sim.corals.first { it.kind != CoralKind.AIPTASIA }
        sim.enqueue(Command.AddAiptasia(1, frag.id)); sim.advanceAbstract(60.0)
        val hh = sim.corals.filter { it.kind == CoralKind.AIPTASIA }
        assertEquals(1, hh.size)
        assertTrue("near its frag: ${hh[0].pos.distanceTo(frag.pos)}", hh[0].pos.distanceTo(frag.pos) < 8.0)
        assertTrue("on the rock", sim.rock.sdf.dist(hh[0].pos) < 1.0)
    }
}
