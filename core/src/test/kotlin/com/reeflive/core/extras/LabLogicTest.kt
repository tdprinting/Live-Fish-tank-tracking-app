package com.reeflive.core.extras

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class LabLogicTest {
    @Test
    fun parsesPastedIcp() {
        val (values, bad) = IcpLogic.parse(
            """
            Element, Value, Unit
            Mg, 1320, mg/L
            Iodine 42 µg/L
            Sr;8.1
            garbage line
            """.trimIndent(),
        )
        assertEquals(listOf("Mg", "I", "Sr"), values.map { it.element })
        assertEquals(42.0, values[1].value, 1e-9)
        assertEquals("mg/L", values[2].unit)
        assertEquals(listOf("garbage line"), bad)
    }

    @Test
    fun flagsAgainstReference() {
        assertEquals(IcpFlag.OK, IcpLogic.flag(IcpValue("Mg", 1320.0, "mg/L")))
        assertEquals(IcpFlag.LOW, IcpLogic.flag(IcpValue("I", 42.0, "ppb")))
        assertEquals(IcpFlag.HIGH, IcpLogic.flag(IcpValue("Cu", 8.0, "µg/L")))
        assertEquals(IcpFlag.UNKNOWN, IcpLogic.flag(IcpValue("Cu", 8.0, "mg/L")))
        assertEquals(IcpFlag.UNKNOWN, IcpLogic.flag(IcpValue("Xx", 1.0)))
        val r = IcpResult("r", 0, values = listOf(IcpValue("Mg", 1320.0, "mg/L"), IcpValue("I", 42.0, "µg/L")))
        assertEquals(listOf("I"), IcpLogic.outOfRange(r).map { it.element })
        assertEquals(10.0, IcpLogic.kitDeltaPercent(400.0, 440.0)!!, 1e-9)
    }

    @Test
    fun parBandsAndLatest() {
        assertTrue(ParLogic.band(60.0).label.startsWith("Low"))
        assertEquals("SPS", ParLogic.band(350.0).label)
        val l = ParLogic.latestBySpot(listOf(ParReading("1", "Kenya", 1, 120.0), ParReading("2", "kenya ", 5, 140.0), ParReading("3", "Sand", 3, 40.0)))
        assertEquals(listOf(140.0, 40.0), l.map { it.par })
    }

    @Test
    fun spending() {
        val z = ZoneOffset.UTC
        val now = LocalDate.of(2026, 10, 12).atStartOfDay(z).toInstant().toEpochMilli()
        val jan = SpendLogic.startOfYear(now, z)
        val e = listOf(
            Expense("a", now, "Salt", 60.0, ExpenseCategory.SUPPLIES),
            Expense("b", now - 1, "Coral", 40.0, ExpenseCategory.CORALS),
            Expense("c", jan - 1, "Old pump", 200.0, ExpenseCategory.EQUIPMENT),
        )
        assertEquals(100.0, SpendLogic.total(e, jan), 1e-9)
        assertEquals(ExpenseCategory.SUPPLIES, SpendLogic.byCategory(e, jan).first().first)
        assertEquals(LocalDate.of(2026, 10, 1).atStartOfDay(z).toInstant().toEpochMilli(), SpendLogic.startOfMonth(now, z))
    }
}
