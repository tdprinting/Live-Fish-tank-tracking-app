package com.reeflive.core.chem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChemistryTest {
    @Test
    fun ammoniaWorkedExample() {
        assertEquals(9.2465, Ammonia.pKa(25.0), 1e-3)
        val f = Ammonia.fraction(8.2, 25.0)
        assertEquals(0.0825, f, 0.001)
        assertEquals(0.00825, Ammonia.freeNh3(0.1, 8.2, 25.0), 1e-4)
        assertFalse(Ammonia.isDangerous(Ammonia.freeNh3(0.1, 8.2, 25.0)))
        assertTrue(Ammonia.isDangerous(Ammonia.freeNh3(0.5, 8.3, 28.0)))
        assertTrue("more NH3 at higher pH", Ammonia.fraction(8.5, 25.0) > f)
        assertTrue("more NH3 when warmer", Ammonia.fraction(8.2, 28.0) > f)
    }

    @Test
    fun dosingCalciumChloride60Gal() {
        val p = Dosing.product("cacl2")!!
        assertEquals(6.3, Dosing.grams(p, 10.0, 60.0), 0.1)
        assertEquals(8.3, Dosing.grams(Dosing.product("cacl2_2h2o")!!, 10.0, 60.0), 0.1)
    }

    @Test
    fun dosingBakingSoda60Gal() {
        val p = Dosing.product("nahco3")!!
        assertEquals(6.8, Dosing.grams(p, 1.0, 60.0), 0.1)
        assertEquals(1.0, Dosing.deltaFromGrams(p, Dosing.grams(p, 1.0, 60.0), 60.0), 1e-12)
    }

    @Test
    fun dosingMagnesiumAndSodaAsh() {
        assertEquals(94.6, Dosing.grams(Dosing.product("mgcl2_6h2o")!!, 50.0, 60.0), 0.2)
        assertEquals(12.0 * 0.357, Dosing.grams(Dosing.product("na2co3")!!, 1.0, 60.0), 0.05)
        assertEquals(30.0, Dosing.ml(1.5, 60.0, 3.0), 1e-9)
    }

    @Test
    fun dosingPlanRespectsDailyCap() {
        val plan = Dosing.plan(totalAmount = 17.0, delta = 2.5, capPerDay = 1.0)
        assertEquals(3, plan.days)
        assertTrue(plan.exceedsCap)
        assertEquals(17.0 / 3, plan.perDay, 1e-9)
        assertEquals(1, Dosing.plan(5.0, 0.8, 1.0).days)
    }

    @Test
    fun waterChangeDilution() {
        assertEquals(9.0, WaterChange.after(10.0, 0.0, 0.10), 1e-9)
        assertEquals(8.9, WaterChange.after(9.0, 8.0, 0.10), 1e-9)
        val out = WaterChange.predict(mapOf("no3" to 10.0, "alk" to 9.0, "temp" to 77.0), mapOf("no3" to 0.0, "alk" to 8.0), 0.1)
        assertEquals(9.0, out["no3"]!!, 1e-9)
        assertEquals(77.0, out["temp"]!!, 1e-9)
        assertEquals(0.1, WaterChange.fraction(6.0, 60.0), 1e-12)
    }

    @Test
    fun saltMix() {
        assertEquals(1360.0, SaltMix.grams(1.025, 10.0), 1e-9)
        assertEquals(0.5, SaltMix.cups(136.0), 1e-9)
        assertEquals(1414.4, SaltMix.grams(1.026, 10.0), 1e-9)
    }

    @Test
    fun evaporationDrift() {
        val sg = Evaporation.sgAfter(1.025, 60.0, 1.0, 3.0)
        assertEquals(1.0 + 0.025 * 60.0 / 57.0, sg, 1e-12)
        assertTrue(Evaporation.driftPerDay(1.025, 60.0, 1.0) > 0.0004)
        assertEquals(3.0, Evaporation.topOffGallons(sg, 1.025, 57.0), 1e-9)
    }

    @Test
    fun oxygenSaturation() {
        assertEquals(6.6, Oxygen.saturation(25.0, 35.0), 1e-9)
        assertEquals(5.5, Oxygen.saturation(32.0, 35.0), 1e-9)
        assertEquals(6.6 + 0.05 * 5, Oxygen.saturation(25.0, 30.0), 1e-9)
        assertEquals(100.0, Oxygen.percentSaturation(6.6, 25.0), 1e-9)
    }
}
