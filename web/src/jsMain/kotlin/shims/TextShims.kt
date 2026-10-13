@file:Suppress("unused")

// kotlin.text / kotlin.io / kotlin.collections members that exist only on the JVM.
package kotlin.text

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow

class Charset internal constructor(val name: String)

object Charsets {
    val UTF_8 = Charset("UTF-8")
    val US_ASCII = Charset("US-ASCII")
    val ISO_8859_1 = Charset("ISO-8859-1")
}

fun ByteArray.toString(@Suppress("UNUSED_PARAMETER") charset: Charset): String = decodeToString()

fun String(bytes: ByteArray, offset: Int, length: Int, @Suppress("UNUSED_PARAMETER") charset: Charset): String =
    bytes.decodeToString(offset, offset + length)

fun String.format(vararg args: Any?): String = jvmFormat(this, args)

fun String.Companion.format(format: String, vararg args: Any?): String = jvmFormat(format, args)

fun String.Companion.format(@Suppress("UNUSED_PARAMETER") locale: java.util.Locale?, format: String, vararg args: Any?): String =
    jvmFormat(format, args)

/** Fixed-point with HALF_UP rounding, like Java's %.Nf for the values the sim formats. */
fun fixedPoint(v: Double, decimals: Int): String {
    if (v.isNaN()) return "NaN"
    if (v.isInfinite()) return if (v > 0) "Infinity" else "-Infinity"
    val neg = v < 0
    val scale = 10.0.pow(decimals)
    val scaled = floor(abs(v) * scale + 0.5 + 1e-9)
    var digits = scaled.toLong().toString()
    if (decimals > 0) {
        if (digits.length <= decimals) digits = "0".repeat(decimals - digits.length + 1) + digits
        digits = digits.substring(0, digits.length - decimals) + "." + digits.substring(digits.length - decimals)
    }
    val isZero = scaled == 0.0
    return if (neg && !isZero) "-$digits" else digits
}

private val SPEC = Regex("%([-+ 0,(#]*)(\\d+)?(?:\\.(\\d+))?([dfsxXeEgc%n])")

private fun jvmFormat(fmt: String, args: Array<out Any?>): String {
    var i = 0
    return SPEC.replace(fmt) { m ->
        val flags = m.groupValues[1]; val width = m.groupValues[2].toIntOrNull() ?: 0
        val prec = m.groupValues[3].toIntOrNull(); val conv = m.groupValues[4]
        if (conv == "%") return@replace "%"
        if (conv == "n") return@replace "\n"
        val a = args.getOrNull(i++)
        var s = when (conv) {
            "d" -> (a as? Number)?.toLong()?.toString() ?: a.toString()
            "f" -> fixedPoint((a as Number).toDouble(), prec ?: 6)
            "e", "E" -> (a as Number).toDouble().asDynamic().toExponential(prec ?: 6).unsafeCast<String>()
                .replace(Regex("e([+-])(\\d)$"), "e$10$2").let { if (conv == "E") it.uppercase() else it }
            "g" -> (a as Number).toDouble().asDynamic().toPrecision(prec ?: 6).unsafeCast<String>()
            "x" -> (a as Number).toLong().toString(16)
            "X" -> (a as Number).toLong().toString(16).uppercase()
            "c" -> a.toString()
            else -> a.toString().let { if (prec != null) it.take(prec) else it }
        }
        if ('+' in flags && conv in "df" && !s.startsWith("-")) s = "+$s"
        if (',' in flags && conv in "df") {
            val neg = s.startsWith("-"); val body = s.removePrefix("-")
            val int = body.substringBefore('.'); val frac = body.substringAfter('.', "")
            s = (if (neg) "-" else "") + int.reversed().chunked(3).joinToString(",").reversed() + (if (frac.isNotEmpty()) ".$frac" else "")
        }
        if (s.length < width) s = when {
            '-' in flags -> s.padEnd(width)
            '0' in flags && conv in "df" -> (if (s.startsWith("-")) "-" + s.drop(1).padStart(width - 1, '0') else s.padStart(width, '0'))
            else -> s.padStart(width)
        }
        s
    }
}
