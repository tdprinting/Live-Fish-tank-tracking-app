package com.reeflive.core.status

import com.reeflive.core.model.ParameterOverride
import com.reeflive.core.model.ReefType
import com.reeflive.core.params.ParameterTable
import com.reeflive.core.params.Range
import com.reeflive.core.params.Targets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightAndTargetsTest {
    private val day = TrendCalc.DAY_MS
    private val t0 = 1_760_000_000_000L

    @Test
    fun allReefTablesAreWellFormed() {
        for (type in ReefType.entries) {
            val defs = ParameterTable.forReefType(type)
            assertEquals(18, defs.size)
            for (d in defs) assertEquals("${type}/${d.id}", emptyList<String>(), Targets.validate(d))
        }
        assertEquals(Range(8.0, 9.5), ParameterTable.byId("alk", ReefType.SPS)!!.ideal)
        assertEquals(Range(400.0, 420.0), ParameterTable.byId("ca", ReefType.FOWLR)!!.ideal)
    }

    @Test
    fun overridesMergeAndHide() {
        val o = mapOf(
            "alk" to ParameterOverride("alk", idealLo = 7.5, idealHi = 8.5, favorite = false),
            "fe" to ParameterOverride("fe", hidden = true),
        )
        val eff = Targets.effective(ReefType.SOFT, o)
        assertEquals(17, eff.size)
        val alk = eff.single { it.id == "alk" }
        assertEquals(Range(7.5, 8.5), alk.ideal)
        assertEquals(Range(7.0, 11.0), alk.acceptable)
        assertEquals(false, alk.favorite)
    }

    @Test
    fun alkConsumptionInsightSuggestsBakingSoda() {
        val def = ParameterTable.byId("alk")!!
        val hist = listOf(8.8 to 0, 8.4 to 1, 8.0 to 2).map { (v, d) -> Reading("alk", v, t0 + d * day) }
        val c = InsightEngine.consumptionPerDay(hist, t0 + 2 * day)!!
        assertEquals(0.4, c, 1e-9)
        val text = InsightEngine.insight(def, hist, t0 + 2 * day, 60.0)
        assertTrue(text, text.startsWith("Alk consumption 0.40 dKH/day — dose 2.7 g baking soda daily to hold 8"))
    }

    @Test
    fun consumptionIgnoresReadingsBeforeAReset() {
        val hist = listOf(8.8 to 0, 7.5 to 3, 8.6 to 4, 8.2 to 5).map { (v, d) -> Reading("alk", v, t0 + d * day) }
        val c = InsightEngine.consumptionPerDay(hist, t0 + 5 * day, resetTimes = listOf(t0 + 3 * day + day / 2))
        assertEquals(0.4, c!!, 1e-9)
    }

    @Test
    fun staleAndRatioInsights() {
        val def = ParameterTable.byId("no3")!!
        val text = InsightEngine.insight(def, listOf(Reading("no3", 5.0, t0)), t0 + 12 * day, 60.0)
        assertTrue(text, text.startsWith("Not tested in 12 days"))
        assertEquals(100.0, InsightEngine.nutrientRatio(5.0, 0.05)!!, 1e-9)
        assertTrue(InsightEngine.ratioInsight(30.0)!!.contains("phosphate is high"))
        assertNull(InsightEngine.nutrientRatio(5.0, 0.0))
    }
}
