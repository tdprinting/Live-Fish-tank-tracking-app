package com.reeflive.core.care

import com.reeflive.core.model.CareTask
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

class TaskScheduleTest {
    private val zone = ZoneId.of("America/Chicago")
    private fun ms(y: Int, mo: Int, d: Int, h: Int = 12, mi: Int = 0) = LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun intervalTaskReschedulesFromCompletion() {
        val t = CareTask("t", "tank", "Water change", intervalDays = 7, reminderHour = 10, createdAt = ms(2025, 10, 1))
        assertEquals(ms(2025, 10, 1, 10), TaskSchedule.nextDue(t, null, zone))
        assertEquals(ms(2025, 10, 10, 10), TaskSchedule.nextDue(t, ms(2025, 10, 3, 19), zone))
    }

    @Test
    fun weekdayTask() {
        val mask = TaskSchedule.bit(DayOfWeek.MONDAY) or TaskSchedule.bit(DayOfWeek.THURSDAY)
        val t = CareTask("t", "tank", "Dose", weekdaysMask = mask, reminderHour = 8, createdAt = ms(2025, 10, 1)) // Wednesday
        assertEquals(ms(2025, 10, 2, 8), TaskSchedule.nextDue(t, null, zone))
        assertEquals(ms(2025, 10, 6, 8), TaskSchedule.nextDue(t, ms(2025, 10, 2, 9), zone))
        assertEquals("Mo Th", TaskSchedule.describeInterval(t))
    }

    @Test
    fun dueStatesAndSnooze() {
        val t = CareTask("t", "tank", "Glass", intervalDays = 7, createdAt = ms(2025, 10, 1))
        val d = TaskSchedule.due(t, ms(2025, 10, 1), ms(2025, 10, 10), zone)
        assertEquals(DueState.OVERDUE, d.state)
        assertEquals("2 d overdue", TaskSchedule.dueLabel(d))
        val snoozed = t.copy(snoozedUntil = ms(2025, 10, 11, 8))
        assertEquals(DueState.UPCOMING, TaskSchedule.due(snoozed, ms(2025, 10, 1), ms(2025, 10, 10), zone).state)
        assertEquals(DueState.TODAY, TaskSchedule.due(t, ms(2025, 10, 1), ms(2025, 10, 8, 6), zone).state)
        assertEquals(20251008, TaskSchedule.dayKey(ms(2025, 10, 8, 6), zone))
    }
}
