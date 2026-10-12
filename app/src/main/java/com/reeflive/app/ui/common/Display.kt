package com.reeflive.app.ui.common

import com.reeflive.core.params.ParameterDef
import com.reeflive.core.params.Range
import com.reeflive.core.units.ParamUnit
import com.reeflive.core.units.UnitConverter
import com.reeflive.core.util.Fmt
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * A parameter as the user sees it: values converted from the stored base unit into the preferred
 * display [unit] with a matching precision. All UI formatting goes through this.
 */
data class ParamView(val def: ParameterDef, val unit: ParamUnit) {
    val precision: Int = precisionFor(def, unit)
    val symbol: String get() = unit.symbol
    val isBase: Boolean get() = unit == def.baseUnit
    private val convertible: Boolean = unit != def.baseUnit && UnitConverter.canConvert(def.baseUnit, unit)

    fun toDisplay(base: Double): Double = if (!convertible) base else UnitConverter.convert(base, def.baseUnit, unit)

    fun toBase(display: Double): Double = if (!convertible) display else UnitConverter.convert(display, unit, def.baseUnit)

    fun range(r: Range): Range {
        val a = toDisplay(r.lo)
        val b = toDisplay(r.hi)
        return Range(minOf(a, b), maxOf(a, b))
    }

    /** "8.6" */
    fun format(base: Double): String = Fmt.num(toDisplay(base), precision)

    /** "8.6 dKH" */
    fun formatWithUnit(base: Double): String = format(base) + if (symbol.isEmpty() || symbol == "pH") "" else " $symbol"

    /** Signed change in display units ("+0.3"). Uses a linear delta so offsets (°F) cancel. */
    fun formatDelta(baseDelta: Double, around: Double): String {
        val d = toDisplay(around + baseDelta) - toDisplay(around)
        return Fmt.signed(d, precision)
    }

    /** Cycles to the next unit this parameter supports (quick-log unit toggle). */
    fun nextUnit(): ParamUnit {
        val units = def.units
        val i = units.indexOf(unit)
        return units[(i + 1) % units.size]
    }

    companion object {
        fun precisionFor(def: ParameterDef, unit: ParamUnit): Int = when {
            unit == def.baseUnit -> def.precision
            unit == ParamUnit.F || unit == ParamUnit.C -> 1
            unit == ParamUnit.SG -> 3
            unit == ParamUnit.PPT -> 1
            unit == ParamUnit.MS_CM -> 1
            unit == ParamUnit.DKH -> 1
            unit == ParamUnit.MEQ_L -> 2
            unit == ParamUnit.PPM_CACO3 -> 0
            unit == ParamUnit.PPB -> 0
            unit == ParamUnit.PPM_NO3_N -> 2
            unit == ParamUnit.PPM_PO4_P -> 3
            else -> def.precision
        }
    }
}

/** Relative and absolute time labels used across screens. */
object TimeText {
    private val dayMonth = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    private val dayMonthYear = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
    private val weekdayDayMonth = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
    private val time = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val full = DateTimeFormatter.ofPattern("EEE MMM d · HH:mm", Locale.US)

    fun zone(): ZoneId = ZoneId.systemDefault()

    fun ago(ms: Long, now: Long = System.currentTimeMillis()): String {
        val d = now - ms
        val min = d / 60_000
        return when {
            min < 1 -> "just now"
            min < 60 -> "$min min ago"
            min < 60 * 24 -> "${min / 60} h ago"
            else -> {
                val days = ChronoUnit.DAYS.between(date(ms), date(now))
                when {
                    days <= 1 -> "yesterday"
                    days < 30 -> "$days d ago"
                    days < 365 -> "${days / 30} mo ago"
                    else -> "${days / 365} y ago"
                }
            }
        }
    }

    fun date(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone()).toLocalDate()

    fun dayLabel(ms: Long, now: Long = System.currentTimeMillis()): String {
        val d = date(ms)
        val today = date(now)
        return when (d) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> if (d.year == today.year) weekdayDayMonth.format(d) else dayMonthYear.format(d)
        }
    }

    fun shortDate(ms: Long): String = dayMonth.format(Instant.ofEpochMilli(ms).atZone(zone()))
    fun clock(ms: Long): String = time.format(Instant.ofEpochMilli(ms).atZone(zone()))
    fun full(ms: Long): String = full.format(Instant.ofEpochMilli(ms).atZone(zone()))

    /** "Day 3 · 14:32"-style short duration, "1 y 3 m" for livestock age. */
    fun span(fromMs: Long, toMs: Long = System.currentTimeMillis()): String {
        val a = date(fromMs)
        val b = date(toMs)
        val months = ChronoUnit.MONTHS.between(a, b)
        val days = ChronoUnit.DAYS.between(a, b)
        return when {
            months >= 12 -> "${months / 12} y" + if (months % 12 != 0L) " ${months % 12} m" else ""
            months >= 1 -> "$months m"
            else -> "$days d"
        }
    }
}
