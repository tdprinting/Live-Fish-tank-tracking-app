package com.reeflive.core.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Carcass ammonia is bounded by the body's own nitrogen and spread over the system volume (sandbox overstock case). */
class CarcassDecayTest {
    private fun f(v: Double) = "%.1f".format(v)

    @Test
    fun overstockDieOffIsBoundedByBiomassNitrogen() {
        val stock = (1..30).map { LivestockSeed("paracanthurus_hepatus", "Blue $it", 25.0, id = "tang$it") }
        val sim = TestTank.sim(stock)
        val w = sim.water
        val all = sim.organisms.toList()   // carcasses leave the tank at 48 h
        val massG = sim.organisms.sumOf { it.massG }
        val n0 = w.nitrogenInSystemMg()
        val in0 = w.nInputMg; val out0 = w.nExportMg
        var peakTan = 0.0
        var allDeadAt = -1.0
        for (h in 1..72) {
            sim.advanceAbstract(3600.0)
            peakTan = maxOf(peakTan, w.s.nh3Total)
            if (allDeadAt < 0 && sim.organisms.none { it.alive }) allDeadAt = h.toDouble()
        }
        val dead = all.filter { !it.alive }
        // the whole fish nitrogen (2.5 % of wet mass) spread over the system water: the hard ceiling
        val bodyN = massG * Simulation.CARCASS_N_FRACTION * 1000           // mg N
        val ceilingTan = bodyN * 17 / 14 / w.s.volumeL
        val released = all.sumOf { it.carcassTanReleasedG }
        println("Carcass: 30 × 25 cm blue tangs = ${f(massG / 1000)} kg; all dead by h $allDeadAt; peak TAN ${f(peakTan)} ppm " +
            "(body-N ceiling ${f(ceilingTan)} ppm in ${f(w.s.volumeL)} L)")
        assertTrue("the overstock kills: ${dead.size}", allDeadAt > 0)
        for (o in all) assertTrue("carcass ${o.id} leached ${o.carcassTanReleasedG}", o.carcassTanReleasedG <= Simulation.carcassTanPoolG(o) + 1e-9)
        assertTrue("TAN $peakTan under the body-N ceiling $ceilingTan", peakTan < ceilingTan)
        // carcasses removed at 48 h take the unleached N with them: well under two thirds of it ever enters the water
        assertTrue("released ${f(released)} g TAN of ${f(massG * 0.025 * 17 / 14)}", released < 0.65 * massG * Simulation.CARCASS_N_FRACTION * 17 / 14)
        // and every gram that went in is accounted for in the system N balance
        val lhs = w.nitrogenInSystemMg() + (w.nExportMg - out0)
        val rhs = n0 + (w.nInputMg - in0)
        assertEquals("N balance", 0.0, abs(lhs - rhs) / rhs, 0.01)
    }

    @Test
    fun oneSmallCarcassFoulsLittle() {
        val sim = TestTank.sim(listOf(TestTank.seed("tang")))
        val tang = sim.organisms.first()
        sim.advanceAbstract(3600.0)
        val tan0 = sim.water.s.nh3Total
        tang.alive = false; tang.deadSince = sim.simTimeSec; tang.deathCause = "test"
        var peak = 0.0
        repeat(48) { sim.advanceAbstract(3600.0); peak = maxOf(peak, sim.water.s.nh3Total) }
        println("Carcass: one ${f(tang.massG)} g juvenile tang → peak TAN ${"%.3f".format(peak)} ppm (from ${"%.3f".format(tan0)}), leached ${"%.2f".format(tang.carcassTanReleasedG)} g")
        assertTrue(tang.carcassTanReleasedG > 0.1 * Simulation.carcassTanPoolG(tang))
        assertTrue("small carcass, small spike: $peak", peak - tan0 < 0.5)
    }
}
