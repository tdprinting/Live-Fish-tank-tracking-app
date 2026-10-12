package com.reeflive.core.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Skimmer, filter sock, carbon, refugium and red macroalgae (coverage gaps W-6, C-7; research 01 §3.5, 03 A1/C1). */
class EquipmentExportTest {
    private fun f(v: Double) = "%.3f".format(v)

    /** A fed water model on a 12 h light day, 1-min steps. */
    private fun fed(w: WaterSim, days: Int, feedG: Double = 1.0, uneatenG: Double = 0.0, each: (Int) -> Unit = {}) {
        w.bioMassIndex = 60.0
        w.ato = true
        for (min in 0 until days * 1440) {
            if (min % 1440 == 600) { w.addFeed(feedG); if (uneatenG > 0) w.addDetritusTanGrams(uneatenG * 0.1) }
            w.lightIntensity = if (min % 1440 in 480..1200) 0.7 else 0.0
            w.step(60.0)
            each(min)
        }
    }

    /** Skimmer off: DOC accumulates and DO falls a little; fresh carbon then strips the DOC and allelochemicals. */
    @Test
    fun skimmerAndCarbonControlDoc() {
        val on = WaterSim(TankConfig(), WaterState())
        val off = WaterSim(TankConfig(), WaterState()).also { it.skimmer = false }
        var doOn = 0.0; var doOff = 0.0
        fed(on, 14) { if (it % 1440 == 300) doOn = on.s.doMgL }
        fed(off, 14) { if (it % 1440 == 300) doOff = off.s.doMgL }
        println("W-6 DOC skimmer on ${f(on.s.doc)} off ${f(off.s.doc)}; night DO on ${f(doOn)} off ${f(doOff)}")
        assertTrue("DOC with the skimmer off ${off.s.doc} vs ${on.s.doc}", off.s.doc > on.s.doc * 1.8)
        assertTrue("less aeration without the skimmer: $doOff vs $doOn", doOff < doOn)

        off.s.doc = 6.0; off.s.allelo = 2.0
        assertTrue("yellow water dims the light: ${off.clarity}", off.clarity < 0.95)
        off.setParam("carbon", 1.0)
        repeat(2 * 1440) { off.step(60.0) }
        println("W-6 fresh carbon 2 d: DOC ${f(off.s.doc)} allelo ${f(off.s.allelo)}")
        assertTrue("carbon strips DOC: ${off.s.doc}", off.s.doc < 2.0)
        assertTrue("carbon strips allelochemicals: ${off.s.allelo}", off.s.allelo < 0.1)
        // exhausted after ~3 weeks unless replaced (01 §3.5)
        off.carbonChangeDays = 0.0
        repeat(28 * 1440) { off.step(60.0) }
        assertTrue("carbon exhausted: ${off.carbonEfficiency}", off.carbonEfficiency < 0.2)
        off.maintain(MaintenanceTask.REPLACE_CARBON)
        assertEquals(1.0, off.carbonEfficiency, 1e-9)
    }

    /** 01 §3.5: a sock changed every 2-3 days exports detritus; one left in rots and gives the NH3/PO4 back. */
    @Test
    fun filterSockExportsUntilItRots() {
        fun run(changeDays: Double): WaterSim {
            val w = WaterSim(TankConfig(), WaterState(detritus = 0.5, no3 = 5.0))
            w.filterSock = true; w.sockChangeDays = changeDays
            fed(w, 21, feedG = 1.0, uneatenG = 1.5)
            return w
        }
        val none = WaterSim(TankConfig(), WaterState(detritus = 0.5, no3 = 5.0)).also { fed(it, 21, feedG = 1.0, uneatenG = 1.5) }
        val changed = run(3.0)
        val rotting = run(0.0)
        println("W-6 NO3 after 3 weeks: no sock ${f(none.s.no3)} sock changed ${f(changed.s.no3)} sock left in ${f(rotting.s.no3)} (load ${f(rotting.sockLoad)})")
        assertTrue("changed sock exports: ${changed.s.no3} vs ${none.s.no3}", changed.s.no3 < none.s.no3 - 0.5)
        assertTrue("a sock left in gives most of it back: ${rotting.s.no3}", rotting.s.no3 > changed.s.no3 + 0.3)
    }

    /** Nitrogen stays conserved with every export path on. */
    @Test
    fun massBalanceWithEquipment() {
        val w = WaterSim(TankConfig(), WaterState(no3 = 10.0, detritus = 0.3))
        w.filterSock = true; w.carbon = true; w.refugium = true; w.refugiumHarvestDays = 10.0
        val n0 = w.nitrogenInSystemMg()
        fed(w, 30) { if (it % (7 * 1440) == 0 && it > 0) w.waterChange(0.15) }
        val lhs = w.nitrogenInSystemMg() + w.nExportMg
        val rhs = n0 + w.nInputMg
        assertTrue("N balance ${f(lhs)} vs ${f(rhs)}", abs(lhs - rhs) / rhs < 0.01)
    }

    /** A refugium grows on NO3/PO4 and lowers them; harvest removes half the chaeto. Red macro bleaches in over-bright light. */
    @Test
    fun refugiumAndRedMacro() {
        val plain = TestTank.sim(DefaultStock.all)
        val fuge = TestTank.sim(DefaultStock.all).also { it.enqueue(Command.SetParam("refugium", 1.0)) }
        plain.advanceAbstract(30 * 86400.0)
        fuge.advanceAbstract(30 * 86400.0)
        val ch = fuge.water.s.chaeto
        println("W-6/C-7 30 d: NO3 plain ${f(plain.water.s.no3)} refugium ${f(fuge.water.s.no3)}; PO4 ${f(plain.water.s.po4)} vs ${f(fuge.water.s.po4)}; chaeto ${f(ch)} macro ${f(fuge.water.s.macro)}")
        assertTrue("chaeto grew: $ch", ch > WaterSim.CHAETO_START * 1.5)
        assertTrue("refugium lowers nitrate", fuge.water.s.no3 < plain.water.s.no3 - 0.3)
        assertTrue("refugium lowers phosphate", fuge.water.s.po4 < plain.water.s.po4)
        fuge.enqueue(Command.Maintain(MaintenanceTask.HARVEST_MACRO))
        fuge.advanceAbstract(60.0)
        assertEquals(ch * 0.5, fuge.water.s.chaeto, ch * 0.02)

        // display red macro: grows under normal light and nutrients, bleaches and shrinks under 1.4x light
        fun macroAfter(light: Double): Double {
            val w = WaterSim(TankConfig(), WaterState(no3 = 8.0, po4 = 0.08, macro = 0.3))
            w.macroTrimAbove = 0.0; w.ato = true
            for (min in 0 until 30 * 1440) {
                w.lightIntensity = if (min % 1440 in 480..1200) light else 0.0
                w.s.no3 = 8.0; w.s.po4 = 0.08
                w.step(60.0)
            }
            return w.s.macro
        }
        val normal = macroAfter(0.7)
        val bright = macroAfter(1.4)
        println("C-7 red macro after 30 d: normal light ${f(normal)} over-bright ${f(bright)}")
        assertTrue("red macro grows: $normal", normal > 0.33)
        assertTrue("over-bright light bleaches it back: $bright", bright < 0.3)
    }
}
