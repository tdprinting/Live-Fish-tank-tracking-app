package com.reeflive.core.sim

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Fish waste (research 18_fish_waste.md): feces are made, drift, and end in the filter, the crew or the sand. */
class WasteSystemTest {
    private fun f(x: Double) = "%.4f".format(x)

    @Test fun fishPoopAfterAMealAndItGoesSomewhere() {
        val sim = Presets.create(Presets.realityDefault, TestTank.catalog, TestTank.rock, startTimeSec = 11 * 3600.0)
        sim.advanceAbstract(3600.0)   // fill the guts with the background output
        val w = sim.water
        sim.enqueue(Command.Feed(FoodKind.MYSIS, 1.0))
        sim.advance(3 * 3600.0, behaviorDt = 0.1)
        val ws = sim.waste
        println("waste 3 h: pellets ${ws.pelletsMade} live ${ws.count} settled ${ws.settledCount()} spat ${ws.spatOut} | made ${f(ws.madePpm)} hob ${f(ws.hobPpm)} eaten ${f(ws.eatenPpm)} mulm ${f(ws.mulmPpm)} leached ${f(ws.leachedPpm)} held ${f(ws.heldPpm())} | hobLoad ${f(w.hobLoad)}")
        assertTrue("fish defecate", ws.pelletsMade >= 10)
        assertTrue("pellets meet a fate", ws.hobPpm + ws.eatenPpm + ws.mulmPpm + ws.leachedPpm > 0)
        assertTrue("snapshot carries the pellets", sim.snapshot().waste.size == ws.count * WasteSystem.STRIDE)
    }

    @Test fun uncleanedWasteRaisesNutrientsAndRinsingHelps() {
        fun run(rinseDays: Double): WaterSim {
            val sim = Presets.create(Presets.realityDefault, TestTank.catalog, TestTank.rock, startTimeSec = 0.0)
            sim.water.hobRinseDays = rinseDays
            sim.advanceAbstract(21 * 86400.0)
            return sim.water
        }
        val rinsed = run(3.0); val never = run(0.0)
        println("21 d: rinsed NO3 ${f(rinsed.s.no3)} PO4 ${f(rinsed.s.po4)} | never NO3 ${f(never.s.no3)} PO4 ${f(never.s.po4)} hobLoad ${f(never.hobLoad)}")
        assertTrue("a floss that is never rinsed leaks its waste back", never.s.no3 > rinsed.s.no3)
    }

    @Test fun nitrogenStaysBalancedWithWaste() {
        val sim = Presets.create(Presets.realityDefault, TestTank.catalog, TestTank.rock, startTimeSec = 0.0)
        val w = sim.water
        val n0 = w.nitrogenInSystemMg()
        sim.advanceAbstract(10 * 86400.0)
        // gut, pellet and floss N sit on the export side until they come back
        val lhs = w.nitrogenInSystemMg() + w.nExportMg
        val rhs = n0 + w.nInputMg
        println("N balance ${f(lhs)} vs ${f(rhs)}")
        assertTrue(abs(lhs - rhs) / rhs < 0.01)
    }
}
