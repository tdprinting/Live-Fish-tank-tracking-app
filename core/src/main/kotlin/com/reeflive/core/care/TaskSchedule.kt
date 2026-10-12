package com.reeflive.core.care

import com.reeflive.core.model.CareTask
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class DueState { OVERDUE, TODAY, UPCOMING, LATER }

data class TaskDue(
    val task: CareTask,
    val dueAt: Long,
    val lastDoneAt: Long?,
    val state: DueState,
    /** Whole calendar days from today to the due date (negative when overdue). */
    val daysUntil: Long,
)

/** Recurrence math for care tasks. Pure and zone-aware so it is unit-testable. */
object TaskSchedule {

    /** Bit for [d] in [CareTask.weekdaysMask] (Monday = bit 0). */
    fun bit(d: DayOfWeek): Int = 1 shl (d.value - 1)

    /**
     * Next due instant: interval tasks are due [CareTask.intervalDays] after the last completion (or
     * after creation when never done) at the reminder time; weekday tasks on the next selected weekday
     * after the last completion. A snooze pushes the due time to at least [CareTask.snoozedUntil].
     */
    fun nextDue(task: CareTask, lastDoneAt: Long?, zone: ZoneId): Long {
        val time = LocalTime.of(task.reminderHour.coerceIn(0, 23), task.reminderMinute.coerceIn(0, 59))
        val anchorMs = lastDoneAt ?: task.createdAt
        val anchorDate = Instant.ofEpochMilli(anchorMs).atZone(zone).toLocalDate()
        val due: Long = when {
            task.intervalDays != null && task.intervalDays > 0 -> {
                val date = if (lastDoneAt == null) anchorDate else anchorDate.plusDays(task.intervalDays.toLong())
                date.atTime(time).atZone(zone).toInstant().toEpochMilli()
            }
            task.weekdaysMask != null && task.weekdaysMask != 0 -> {
                var d = if (lastDoneAt == null) anchorDate else anchorDate.plusDays(1)
                val mask: Int = task.weekdaysMask
                while ((mask and bit(d.dayOfWeek)) == 0) d = d.plusDays(1)
                d.atTime(time).atZone(zone).toInstant().toEpochMilli()
            }
            else -> anchorDate.atTime(time).atZone(zone).toInstant().toEpochMilli()
        }
        return maxOf(due, task.snoozedUntil ?: Long.MIN_VALUE)
    }

    fun due(task: CareTask, lastDoneAt: Long?, nowMs: Long, zone: ZoneId): TaskDue {
        val dueAt = nextDue(task, lastDoneAt, zone)
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val dueDate = Instant.ofEpochMilli(dueAt).atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(today, dueDate)
        val state = when {
            days < 0 -> DueState.OVERDUE
            days == 0L -> DueState.TODAY
            days <= 7 -> DueState.UPCOMING
            else -> DueState.LATER
        }
        return TaskDue(task, dueAt, lastDoneAt, state, days)
    }

    fun describeInterval(task: CareTask): String = when {
        task.intervalDays == 1 -> "Daily"
        task.intervalDays != null -> "Every ${task.intervalDays} d"
        task.weekdaysMask != null -> DayOfWeek.entries.filter { (task.weekdaysMask and bit(it)) != 0 }
            .joinToString(" ") { it.name.take(2).lowercase().replaceFirstChar { c -> c.uppercase() } }
        else -> "Once"
    }

    fun dueLabel(d: TaskDue): String = when {
        d.daysUntil < 0 -> "${-d.daysUntil} d overdue"
        d.daysUntil == 0L -> "Today"
        d.daysUntil == 1L -> "Tomorrow"
        else -> "in ${d.daysUntil} d"
    }

    fun dayKey(ms: Long, zone: ZoneId): Int {
        val d: LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        return d.year * 10000 + d.monthValue * 100 + d.dayOfMonth
    }
}
