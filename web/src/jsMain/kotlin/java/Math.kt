@file:Suppress("unused", "PackageDirectoryMismatch")

package java.math

enum class RoundingMode { HALF_UP, HALF_EVEN, DOWN, UP, FLOOR, CEILING }

/** Only BigDecimal(double).setScale(n, HALF_UP).toPlainString(), as util/Fmt uses it. */
class BigDecimal(private val v: Double) {
    private var scale = -1
    fun setScale(n: Int, @Suppress("UNUSED_PARAMETER") mode: RoundingMode): BigDecimal = BigDecimal(v).also { it.scale = n }
    fun toPlainString(): String = if (scale < 0) v.toString() else kotlin.text.fixedPoint(v, scale)
    fun toDouble() = v
}
