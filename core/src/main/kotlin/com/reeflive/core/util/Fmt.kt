package com.reeflive.core.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.abs

/** Locale-stable number formatting for messages and exports. */
object Fmt {
    /** Fixed decimals, no grouping, '.' decimal separator. Negative zero prints as 0. */
    fun num(v: Double, decimals: Int): String {
        if (v.isNaN()) return "—"
        val d = decimals.coerceIn(0, 6)
        val s = BigDecimal(v).setScale(d, RoundingMode.HALF_UP).toPlainString()
        return if (s.trimStart('-').all { it == '0' || it == '.' }) s.trimStart('-') else s
    }

    /** Shortest form up to [maxDecimals], trailing zeros trimmed (e.g. 8.50 → 8.5, 420.0 → 420). */
    fun trim(v: Double, maxDecimals: Int = 3): String {
        val s = num(v, maxDecimals)
        return if (s.contains('.')) s.trimEnd('0').trimEnd('.') else s
    }

    fun signed(v: Double, decimals: Int): String = (if (v > 0 && abs(v) >= 0.5 * Math.pow(10.0, -decimals.toDouble())) "+" else "") + num(v, decimals)

    fun pct(fraction: Double): String = String.format(Locale.US, "%.0f%%", fraction * 100.0)

    /** Human age like "just now", "3 h ago", "2 d ago". */
    fun ago(ms: Long): String {
        val min = ms / 60_000L
        return when {
            min < 1 -> "just now"
            min < 60 -> "$min min ago"
            min < 60 * 24 -> "${min / 60} h ago"
            else -> "${min / (60 * 24)} d ago"
        }
    }
}
