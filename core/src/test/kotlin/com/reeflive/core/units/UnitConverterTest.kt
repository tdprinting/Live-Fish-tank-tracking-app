package com.reeflive.core.units

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitConverterTest {
    private fun conv(v: Double, a: ParamUnit, b: ParamUnit) = UnitConverter.convert(v, a, b)

    @Test
    fun sg1025Is33_3ppt() {
        assertEquals(33.3, conv(1.025, ParamUnit.SG, ParamUnit.PPT), 1e-9)
        assertEquals(1.025, conv(33.3, ParamUnit.PPT, ParamUnit.SG), 1e-9)
    }

    @Test
    fun sgInterpolatesBetweenTableRows() {
        assertEquals((31.9 + 33.3) / 2, conv(1.0245, ParamUnit.SG, ParamUnit.PPT), 1e-9)
    }

    @Test
    fun alkalinityChain8dkh() {
        val meq = conv(8.0, ParamUnit.DKH, ParamUnit.MEQ_L)
        assertEquals(2.856, meq, 1e-9)
        assertEquals(143.2, conv(meq, ParamUnit.MEQ_L, ParamUnit.PPM_CACO3), 1e-9)
        assertEquals(143.2, conv(8.0, ParamUnit.DKH, ParamUnit.PPM_CACO3), 1e-9)
    }

    @Test
    fun temperature() {
        assertEquals(25.0, conv(77.0, ParamUnit.F, ParamUnit.C), 1e-9)
        assertEquals(84.2, conv(29.0, ParamUnit.C, ParamUnit.F), 1e-9)
    }

    @Test
    fun nutrientForms() {
        assertEquals(2.26, conv(10.0, ParamUnit.PPM, ParamUnit.PPM_NO3_N), 1e-9)
        assertEquals(50.0, conv(0.05, ParamUnit.PPM, ParamUnit.PPB), 1e-9)
        assertEquals(0.0163, conv(0.05, ParamUnit.PPM, ParamUnit.PPM_PO4_P), 1e-9)
        assertEquals(53.0, conv(35.0, ParamUnit.PPT, ParamUnit.MS_CM), 1e-9)
    }

    @Test
    fun roundTripsWithin1e9() {
        val pairs = listOf(
            Triple(ParamUnit.F, ParamUnit.C, listOf(60.0, 77.3, 84.0)),
            Triple(ParamUnit.SG, ParamUnit.PPT, listOf(1.018, 1.0213, 1.025, 1.0267, 1.031)),
            Triple(ParamUnit.SG, ParamUnit.MS_CM, listOf(1.022, 1.026)),
            Triple(ParamUnit.PPT, ParamUnit.MS_CM, listOf(30.0, 35.0)),
            Triple(ParamUnit.DKH, ParamUnit.MEQ_L, listOf(6.0, 8.4, 12.0)),
            Triple(ParamUnit.DKH, ParamUnit.PPM_CACO3, listOf(7.1, 9.9)),
            Triple(ParamUnit.MEQ_L, ParamUnit.PPM_CACO3, listOf(2.5, 3.2)),
            Triple(ParamUnit.PPM, ParamUnit.PPB, listOf(0.03, 1.7)),
            Triple(ParamUnit.PPM, ParamUnit.PPM_NO3_N, listOf(0.5, 12.0)),
            Triple(ParamUnit.PPM, ParamUnit.PPM_PO4_P, listOf(0.02, 0.15)),
            Triple(ParamUnit.PPB, ParamUnit.PPM_PO4_P, listOf(30.0)),
            Triple(ParamUnit.MG_L, ParamUnit.PCT_SAT, listOf(5.5, 6.8)),
        )
        for ((a, b, values) in pairs) for (v in values) {
            val back = conv(conv(v, a, b), b, a)
            assertEquals("$a→$b→$a for $v", v, back, 1e-9)
        }
    }

    @Test
    fun incompatibleUnitsAreRejected() {
        assertFalse(UnitConverter.canConvert(ParamUnit.DKH, ParamUnit.PPM))
        assertFalse(UnitConverter.canConvert(ParamUnit.F, ParamUnit.SG))
        assertTrue(UnitConverter.canConvert(ParamUnit.PPB, ParamUnit.PPM_PO4_P))
    }

    @Test
    fun parsesSymbols() {
        assertEquals(ParamUnit.DKH, ParamUnit.parse("dKH"))
        assertEquals(ParamUnit.F, ParamUnit.parse("°F"))
        assertEquals(ParamUnit.PPM_CACO3, ParamUnit.parse("ppm CaCO3"))
        assertEquals(ParamUnit.MEQ_L, ParamUnit.parse("MEQ_L"))
    }
}
