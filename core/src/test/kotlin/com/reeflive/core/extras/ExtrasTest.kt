package com.reeflive.core.extras

import com.reeflive.core.care.EmergencyGuides
import com.reeflive.core.params.ParameterTable
import com.reeflive.core.status.ReefHealth
import com.reeflive.core.status.Reading
import com.reeflive.core.status.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ExtrasTest {
    private val zone = ZoneOffset.UTC
    private val day = 86_400_000L
    private val t0 = LocalDate.of(2026, 10, 1).atStartOfDay(zone).toInstant().toEpochMilli() + 10 * 3_600_000L

    @Test
    fun supplyStates() {
        val s = Supply("a", "Salt", SupplyCategory.SALT, quantity = 5.0, lowAt = 2.0)
        assertEquals(SupplyState.OK, ExtrasLogic.supplyState(s, t0))
        assertEquals(SupplyState.LOW, ExtrasLogic.supplyState(s.copy(quantity = 2.0), t0))
        assertEquals(SupplyState.OUT, ExtrasLogic.supplyState(s.copy(quantity = 0.0), t0))
        assertEquals(SupplyState.EXPIRING, ExtrasLogic.supplyState(s.copy(expiresAt = t0 + 10 * day), t0))
        assertEquals(SupplyState.EXPIRED, ExtrasLogic.supplyState(s.copy(expiresAt = t0 - 1), t0))
        assertEquals(1, ExtrasLogic.needsAttention(listOf(s, s.copy(id = "b", quantity = 1.0)), t0).size)
        assertEquals(30.0, ExtrasLogic.spendingSince(listOf(s.copy(cost = 30.0, boughtAt = t0), s.copy(cost = 9.0, boughtAt = t0 - 400 * day)), t0 - 365 * day), 1e-9)
    }

    @Test
    fun feedingStreakCountsFullDays() {
        val plan = FeedingPlan(feedsPerDay = 2)
        val f = (0 until 3).flatMap { d -> listOf(Feeding("$d-a", t0 - d * day, "Mysis"), Feeding("$d-b", t0 - d * day + 3_600_000L, "Nori")) }
        assertEquals(3, ExtrasLogic.feedingStreak(f, plan, t0, zone))
        // nothing yet today: the streak still counts up to yesterday
        assertEquals(3, ExtrasLogic.feedingStreak(f, plan, t0 + day, zone))
        assertEquals(2, ExtrasLogic.feedingsOn(f, LocalDate.of(2026, 10, 1), zone).size)
    }

    @Test
    fun treatmentSchedule() {
        val c = TreatmentCase("q", "Copper QT", TreatmentKind.COPPER, startAt = t0, days = 14, doseEveryDays = 2)
        assertEquals(1, ExtrasLogic.dayOf(c, t0, zone))
        assertTrue(ExtrasLogic.doseDue(c, t0, zone))
        assertFalse(ExtrasLogic.doseDue(c, t0 + day, zone)) // day 2: off day
        assertTrue(ExtrasLogic.doseDue(c, t0 + 2 * day, zone))
        assertFalse(ExtrasLogic.doseDue(c.copy(doses = listOf(t0 + 2 * day)), t0 + 2 * day + 3_600_000L, zone))
        assertEquals(4, ExtrasLogic.daysLeft(c, t0 + 9 * day, zone))
        assertFalse(ExtrasLogic.doseDue(c, t0 + 20 * day, zone))
        assertFalse(ExtrasLogic.doseDue(c.copy(endedAt = t0), t0, zone))
        assertEquals(28, TreatmentCase("o", "Watch", startAt = t0).days)
    }

    @Test
    fun extrasJsonRoundTripAndForwardCompatible() {
        val e = ReefExtras(supplies = listOf(Supply("a", "Salt")), cases = listOf(TreatmentCase("q", "QT", startAt = t0)))
        assertEquals(e, ReefExtras.decode(ReefExtras.encode(e)))
        assertEquals(0, ReefExtras.decode("""{"version":1,"future":true}""").supplies.size)
    }

    @Test
    fun stabilityAndHealthScore() {
        val alk = ParameterTable.SOFT_CORAL.first { it.id == "alk" }
        val steady = (0 until 10).map { Reading("alk", 8.3 + if (it % 2 == 0) 0.05 else 0.0, t0 + it * day) }
        val jumpy = (0 until 10).map { Reading("alk", if (it % 2 == 0) 7.0 else 9.5, t0 + it * day) }
        val s1 = ReefHealth.stability(alk, steady, t0, t0 + 10 * day)!!
        val s2 = ReefHealth.stability(alk, jumpy, t0, t0 + 10 * day)!!
        assertTrue("${s1.score} > ${s2.score}", s1.score > 80 && s2.score < 40)
        assertEquals(2.5, ReefHealth.maxSwingPerDay(jumpy, t0, t0 + 10 * day)!!, 1e-9)
        assertNull(ReefHealth.stability(alk, steady.take(2), t0, t0 + 10 * day))

        val best = ReefHealth.score(listOf(Status.GOOD, Status.GOOD), listOf(s1), daysSinceTest = 1.0, overdueTasks = 0)
        val worse = ReefHealth.score(listOf(Status.GOOD, Status.DANGER, Status.UNKNOWN), listOf(s2), daysSinceTest = 20.0, overdueTasks = 4)
        assertTrue(best.score >= 90)
        assertEquals("Thriving", best.grade)
        assertTrue(worse.score < 50)
        assertEquals(4, best.parts.size)
        assertEquals(10, ReefHealth.score(emptyList(), emptyList(), null, 0).parts[1].points)
    }

    @Test
    fun mostStableWeekAndRatio() {
        val defs = ParameterTable.SOFT_CORAL.filter { it.id == "alk" || it.id == "ca" }
        val now = t0 + 30 * day
        val alk = (0..30).map { Reading("alk", if (it < 15) (if (it % 2 == 0) 7.0 else 9.5) else 8.3, t0 + it * day) }
        val ca = (0..30).map { Reading("ca", if (it < 15) (if (it % 2 == 0) 380.0 else 460.0) else 420.0, t0 + it * day) }
        val w = ReefHealth.mostStableWeek(defs, mapOf("alk" to alk, "ca" to ca), now, 30)
        assertNotNull(w)
        assertTrue(w!!.startMs >= t0 + 15 * day)
        assertEquals(100, w.score)
        val ratio = ReefHealth.ratioSeries(listOf(Reading("no3", 5.0, t0)), listOf(Reading("po4", 0.05, t0 + 60_000)))
        assertEquals(100.0, ratio.single().second, 1e-6)
    }

    @Test
    fun guidesAndSymptomMatcher() {
        assertTrue(EmergencyGuides.guides.size >= 5)
        assertTrue(EmergencyGuides.guides.all { it.now.isNotEmpty() })
        val m = EmergencyGuides.match(setOf("White salt-grain spots", "Scratching on rock"))
        assertEquals("Marine ich (white spot)", m.first().name)
        assertTrue(EmergencyGuides.match(emptySet()).isEmpty())
        assertTrue(EmergencyGuides.allSigns.contains("Cloudy eye"))
    }
}
