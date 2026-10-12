package com.reeflive.core.status

import com.reeflive.core.model.ReefType
import com.reeflive.core.params.ParameterTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParamStatsTest {
    private val day = TrendCalc.DAY_MS
    private val t0 = 1_760_000_000_000L
    private val alk = ParameterTable.byId("alk", ReefType.SOFT)!!

    @Test
    fun statsOverWindow() {
        val h = listOf(7.0, 8.0, 9.0, 13.0).mapIndexed { i, v -> Reading("alk", v, t0 + i * day) }
        val all = ParamStats.of(alk, h)!!
        assertEquals(4, all.count)
        assertEquals(7.0, all.min, 1e-9)
        assertEquals(13.0, all.max, 1e-9)
        assertEquals(9.25, all.mean, 1e-9)
        val acceptable = h.count { it.value in alk.acceptable }.toDouble() / 4
        assertEquals(acceptable, all.inRangeShare, 1e-9)

        val late = ParamStats.of(alk, h, fromMs = t0 + 2 * day)!!
        assertEquals(2, late.count)
        assertEquals(9.0, late.min, 1e-9)
    }

    @Test
    fun emptyWindowIsNull() {
        assertNull(ParamStats.of(alk, emptyList()))
        assertNull(ParamStats.of(alk, listOf(Reading("alk", 8.0, t0)), fromMs = t0 + day))
    }
}
