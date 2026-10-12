package com.reeflive.core.status

import com.reeflive.core.model.AlertKind
import com.reeflive.core.params.ParameterTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.KClass

class StatusEngineTest {
    private val day = TrendCalc.DAY_MS
    private val t0 = 1_760_000_000_000L

    private data class Case(
        val name: String,
        val param: String,
        /** (value, day offset) */
        val history: List<Pair<Double, Double>>,
        val nowDay: Double,
        val status: Status,
        val reasons: Set<KClass<out Reason>>,
        val trend: Trend? = null,
    )

    private fun h(vararg p: Pair<Double, Double>) = p.toList()

    private val cases = listOf(
        Case("no data", "alk", emptyList(), 0.0, Status.UNKNOWN, emptySet(), Trend.UNKNOWN),
        Case("ideal alk", "alk", h(8.5 to 0.0), 0.5, Status.GOOD, emptySet(), Trend.UNKNOWN),
        Case("ideal edge low", "alk", h(8.0 to 0.0), 0.5, Status.GOOD, emptySet()),
        Case("ideal edge high", "alk", h(9.0 to 0.0), 0.5, Status.GOOD, emptySet()),
        Case("acceptable low alk", "alk", h(7.5 to 0.0), 0.5, Status.CAUTION, setOf(Reason.OutOfIdeal::class)),
        Case("acceptable high ca", "ca", h(450.0 to 0.0), 0.5, Status.CAUTION, setOf(Reason.OutOfIdeal::class)),
        Case("between acceptable and danger", "alk", h(6.5 to 0.0), 0.5, Status.CAUTION, setOf(Reason.OutOfAcceptable::class)),
        Case("danger low alk", "alk", h(5.8 to 0.0), 0.5, Status.DANGER, setOf(Reason.Danger::class)),
        Case("danger high temp", "temp", h(85.0 to 0.0), 0.1, Status.DANGER, setOf(Reason.Danger::class)),
        Case("danger threshold itself is not danger", "temp", h(84.0 to 0.0), 0.1, Status.CAUTION, setOf(Reason.OutOfAcceptable::class)),
        Case("po4 high danger", "po4", h(0.2 to 0.0), 0.5, Status.DANGER, setOf(Reason.Danger::class)),
        Case("po4 low has no danger side", "po4", h(0.005 to 0.0), 0.5, Status.CAUTION, setOf(Reason.OutOfAcceptable::class)),
        Case("ammonia zero is good", "nh3", h(0.0 to 0.0), 0.5, Status.GOOD, emptySet()),
        Case("ammonia trace is caution", "nh3", h(0.01 to 0.0), 0.5, Status.CAUTION, setOf(Reason.OutOfIdeal::class)),
        Case("ammonia high is danger", "nh3", h(0.5 to 0.0), 0.5, Status.DANGER, setOf(Reason.Danger::class)),
        Case("fast drop alk 1.4 in a day", "alk", h(8.6 to 0.0, 7.2 to 1.0), 1.1, Status.DANGER,
            setOf(Reason.FastChange::class, Reason.OutOfIdeal::class)),
        Case("slow drop over 3 days ok", "alk", h(8.9 to 0.0, 8.0 to 3.0), 3.1, Status.GOOD, emptySet()),
        Case("fast rise ca within hours", "ca", h(400.0 to 0.0, 425.0 to 0.2), 0.3, Status.DANGER, setOf(Reason.FastChange::class)),
        Case("change exactly at cap is fine", "alk", h(8.0 to 0.0, 9.0 to 1.0), 1.1, Status.GOOD, emptySet()),
        Case("salinity fast change", "sal", h(1.025 to 0.0, 1.0235 to 1.0), 1.1, Status.DANGER,
            setOf(Reason.FastChange::class, Reason.OutOfIdeal::class)),
        Case("stale weekly param keeps GOOD color", "alk", h(8.5 to 0.0), 11.0, Status.GOOD, setOf(Reason.Stale::class)),
        Case("stale and caution", "ca", h(385.0 to 0.0), 12.0, Status.CAUTION, setOf(Reason.OutOfIdeal::class, Reason.Stale::class)),
        Case("not yet stale at 1.5x cadence", "alk", h(8.5 to 0.0), 10.4, Status.GOOD, emptySet()),
        Case("no cadence never stale", "nh3", h(0.0 to 0.0), 400.0, Status.GOOD, emptySet()),
        Case("rising trend", "no3", h(5.0 to 0.0, 10.0 to 2.0, 15.0 to 4.0), 4.1, Status.GOOD, emptySet(), Trend.UP),
        Case("falling trend", "ca", h(440.0 to 0.0, 420.0 to 2.0, 400.0 to 4.0), 4.1, Status.GOOD, emptySet(), Trend.DOWN),
        Case("flat trend", "alk", h(8.5 to 0.0, 8.6 to 3.0, 8.5 to 6.0), 6.1, Status.GOOD, emptySet(), Trend.FLAT),
        Case("unsorted history is handled", "alk", h(7.2 to 1.0, 8.6 to 0.0), 1.1, Status.DANGER,
            setOf(Reason.FastChange::class, Reason.OutOfIdeal::class)),
    )

    @Test
    fun tableDriven() {
        assertTrue("at least 24 cases", cases.size >= 24)
        for (c in cases) {
            val def = ParameterTable.byId(c.param)!!
            val hist = c.history.map { (v, d) -> Reading(c.param, v, t0 + (d * day).toLong()) }
            val e = StatusEngine.evaluate(def, hist, t0 + (c.nowDay * day).toLong())
            assertEquals(c.name, c.status, e.status)
            assertEquals(c.name, c.reasons, e.reasons.map { it::class }.toSet())
            c.trend?.let { assertEquals(c.name + " trend", it, e.trend) }
        }
    }

    @Test
    fun fastDropProducesTheSpecifiedAlertText() {
        val def = ParameterTable.byId("alk")!!
        val hist = listOf(Reading("alk", 8.6, t0), Reading("alk", 7.2, t0 + day))
        val e = StatusEngine.evaluate(def, hist, t0 + day)
        val drafts = AlertRules.draftsFor(def, e)
        val fast = drafts.single { it.kind == AlertKind.FAST_CHANGE }
        assertEquals("Danger: Alk fell 1.4 dKH/day", fast.message)
    }

    @Test
    fun staleAlertAndDangerAlert() {
        val def = ParameterTable.byId("temp")!!
        val e = StatusEngine.evaluate(def, listOf(Reading("temp", 86.0, t0)), t0 + 3 * day)
        val kinds = AlertRules.draftsFor(def, e).map { it.kind }.toSet()
        assertEquals(setOf(AlertKind.DANGER, AlertKind.STALE), kinds)
    }

    @Test
    fun trendNeedsTwoDaySpanExtendsWindow() {
        val def = ParameterTable.byId("alk")!!
        // three readings within one day, plus an older one 3 days back: window grows to include it
        val hist = listOf(
            Reading("alk", 10.0, t0), Reading("alk", 8.5, t0 + 3 * day),
            Reading("alk", 8.5, t0 + 3 * day + 3_600_000), Reading("alk", 8.5, t0 + 3 * day + 7_200_000),
        )
        val e = StatusEngine.evaluate(def, hist, t0 + 4 * day)
        assertEquals(Trend.DOWN, e.trend)
    }
}
