package com.reeflive.core.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Disease treatments and the added diseases (coverage gaps W-4, W-3; research 01 §3.8, §4.3, 05 §12). */
class TreatmentTest {
    private fun f(v: Double) = "%.2f".format(v)

    private fun fishTank(seed: Long = 5L) = TestTank.sim(DefaultStock.fish, seed = seed).also { it.advanceAbstract(86400.0) }

    private fun Simulation.fish(id: String) = organisms.firstOrNull { it.id == id } ?: error("$id was removed (died): ${eventLog.filterIsInstance<SimEvent.Died>()}")

    /** Runs [days] and returns the first day the fish was clean (or null), plus its worst stage. */
    private fun Simulation.follow(id: String, days: Int): Pair<Int?, Double> {
        var clean: Int? = null; var worst = 0.0
        for (d in 1..days) {
            advanceAbstract(86400.0)
            val o = fish(id)
            worst = maxOf(worst, o.diseaseStage)
            if (clean == null && o.alive && o.disease == null) clean = d
        }
        return clean to worst
    }

    /** 01 §3.8: SG down at 0.002/day to 1.010, held: ich clears and the fish live; velvet is not helped by it (§4.3). */
    @Test
    fun hyposalinityCuresIchNotVelvet() {
        val control = fishTank()
        control.enqueue(Command.Infect("tang", Disease.ICH, 0.3)); control.fish("tang").immunity = 0.4
        val (cleanControl, worstControl) = control.follow("tang", 20)

        val hypo = fishTank()
        hypo.enqueue(Command.Infect("tang", Disease.ICH, 0.3)); hypo.fish("tang").immunity = 0.4
        hypo.enqueue(Command.StartTreatment(TreatmentKind.HYPOSALINITY))
        var minSg = 2.0
        var clean: Int? = null
        for (d in 1..30) {
            hypo.advanceAbstract(86400.0)
            minSg = minOf(minSg, hypo.water.s.sg)
            if (d == 4) assertTrue("ramp 0.002/day: ${hypo.water.s.sg}", hypo.water.s.sg in 1.0155..1.0185)
            if (clean == null && hypo.fish("tang").disease == null) clean = d
        }
        println("W-4 ich: untreated clean day $cleanControl (worst ${f(worstControl)}), hyposalinity clean day $clean, min SG ${"%.4f".format(minSg)}")
        assertTrue("SG reached the hypo band: $minSg", minSg < 1.0105)
        assertTrue("hypo cleared ich: $clean", clean != null && clean <= 20)
        assertTrue("untreated ich lingers: clean day $cleanControl", cleanControl == null || cleanControl > clean!!)
        assertTrue("fish died in hypo: ${hypo.organisms.filter { !it.alive }.map { it.id + ":" + it.deathCause }}", hypo.organisms.all { it.alive })

        val velvet = fishTank()
        velvet.enqueue(Command.Infect("tang", Disease.VELVET, 0.2)); velvet.fish("tang").immunity = 0.4
        velvet.enqueue(Command.StartTreatment(TreatmentKind.HYPOSALINITY))
        velvet.advanceAbstract(6 * 86400.0)
        val t = velvet.fish("tang")
        println("W-4 velvet under hypo after 6 d: ${t.disease} ${f(t.diseaseStage)} alive=${t.alive}")
        assertTrue("velvet not cured by hypo", !t.alive || (t.disease == Disease.VELVET && t.diseaseStage > 0.4))
    }

    /**
     * 01 §3.8 / §4.3: a copper course (0.20 ppm) in a fish-only QT clears velvet; the same course in the display kills
     * the inverts (05 §12, the W-3 kill switch), and their die-off fouls the water.
     */
    @Test
    fun copperCourseCuresVelvetKillsInverts() {
        val qt = fishTank()
        qt.enqueue(Command.Infect("clown_f", Disease.VELVET, 0.1)); qt.fish("clown_f").immunity = 0.5
        qt.enqueue(Command.StartTreatment(TreatmentKind.COPPER, days = 14.0))
        var peak = 0.0
        var cleared: Int? = null
        for (d in 1..20) {
            qt.advanceAbstract(86400.0)
            peak = maxOf(peak, qt.water.s.copper)
            if (cleared == null && qt.fish("clown_f").disease == null) cleared = d
        }
        val dead = qt.organisms.filter { !it.alive }.map { it.id + ":" + it.deathCause }
        println("W-4 copper QT: peak ${"%.3f".format(peak)} ppm, velvet cleared day $cleared, dead $dead")
        assertEquals(Treatments.COPPER_PPM, peak, 0.02)
        assertTrue("velvet cleared by copper: $cleared", cleared != null && qt.fish("clown_f").alive)
        assertTrue("fish tolerate treatment copper: $dead", dead.isEmpty())
        qt.advanceAbstract(10 * 86400.0)
        assertTrue("course over: copper stripped by carbon ${qt.water.s.copper}", !qt.treatments.isActive(TreatmentKind.COPPER) && qt.water.s.copper < 0.05)

        val display = TestTank.sim(DefaultStock.all, seed = 5L)
        display.enqueue(Command.StartTreatment(TreatmentKind.COPPER, days = 14.0))
        display.advanceAbstract(6 * 86400.0)
        val inverts = display.organisms.filter { !it.isFish }
        val lost = DefaultStock.inverts.size - inverts.count { it.alive }
        println("W-4 copper in the display: $lost/${DefaultStock.inverts.size} inverts dead after 6 d, corals tissue ${display.corals.map { "%.2f".format(it.tissue) }}")
        assertTrue("copper kills the inverts: $lost", lost >= DefaultStock.inverts.size / 2)
    }

    /** 01 §3.8: four transfers every 72 h (12 days) leave the trophonts behind; UV only cuts spread. */
    @Test
    fun tankTransferAndUv() {
        val sim = fishTank()
        for (id in listOf("tang", "clown_m", "cardinal")) { sim.enqueue(Command.Infect(id, Disease.ICH, 0.4)); sim.fish(id).immunity = 0.3 }
        sim.enqueue(Command.StartTreatment(TreatmentKind.TANK_TRANSFER, fishIds = listOf("tang", "clown_m", "cardinal")))
        sim.advanceAbstract(12.1 * 86400.0)
        assertTrue("transfer cleared ich: ${sim.organisms.map { it.id to it.disease }}", listOf("tang", "clown_m", "cardinal").all { sim.fish(it).alive && sim.fish(it).disease == null })

        val o = sim.fish("tang")
        val t = Treatments().also { it.active[TreatmentKind.UV] = ActiveTreatment(TreatmentKind.UV, 0.0, 1e9, null) }
        val m = t.modsFor(o, WaterState(), 0.0).getValue(Disease.ICH)
        assertEquals(0.2, m.transmission, 1e-9)
        assertEquals(0.0, m.clearPerMin, 1e-12)
        sim.enqueue(Command.StartTreatment(TreatmentKind.UV)); sim.advanceAbstract(60.0)
        assertTrue(sim.water.uv)
        sim.enqueue(Command.StopTreatment(TreatmentKind.UV)); sim.advanceAbstract(60.0)
        assertTrue(!sim.water.uv)
    }

    /** 01 §4.3: Brooklynella is fast and deadly (formalin, not copper); flukes are slow (praziquantel). */
    @Test
    fun brooklynellaAndFlukes() {
        fun run(d: Disease, treat: TreatmentKind?, days: Int): Organism {
            val sim = fishTank()
            sim.enqueue(Command.Infect("clown_f", d, 0.1)); sim.fish("clown_f").immunity = 0.2
            if (treat != null) sim.enqueue(Command.StartTreatment(treat))
            sim.advanceAbstract(days * 86400.0)
            return sim.fish("clown_f")
        }
        val brook = run(Disease.BROOKLYNELLA, null, 7)
        val brookCopper = run(Disease.BROOKLYNELLA, TreatmentKind.COPPER, 7)
        val brookFormalin = run(Disease.BROOKLYNELLA, TreatmentKind.FORMALIN, 7)
        val flukes = run(Disease.FLUKES, null, 7)
        val prazi = run(Disease.FLUKES, TreatmentKind.PRAZIQUANTEL, 7)
        println("W-4 7 d: Brooklynella untreated ${brook.disease} ${f(brook.diseaseStage)} alive=${brook.alive} (${brook.deathCause}); copper ${f(brookCopper.diseaseStage)}; formalin ${brookFormalin.disease}; flukes ${f(flukes.diseaseStage)} alive=${flukes.alive}; prazi ${prazi.disease}")
        assertTrue("Brooklynella advances fast", !brook.alive || brook.diseaseStage > 0.5)
        assertTrue("copper does not stop Brooklynella", !brookCopper.alive || brookCopper.diseaseStage > 0.5)
        assertNull("formalin cured Brooklynella", brookFormalin.disease)
        assertTrue("brook shows mucus or died", !brook.alive || Symptom.MUCUS in brook.symptoms)
        assertTrue("flukes are slow: ${flukes.diseaseStage}", flukes.alive && flukes.diseaseStage < 0.45)
        assertNull("prazi cured flukes", prazi.disease)
    }
}
