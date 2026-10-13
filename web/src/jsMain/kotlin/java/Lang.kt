@file:Suppress("unused", "PackageDirectoryMismatch")

package java.lang

object Float {
    fun floatToIntBits(f: kotlin.Float): Int = f.toBits()
    fun intBitsToFloat(i: Int): kotlin.Float = kotlin.Float.fromBits(i)
}

object Double {
    fun doubleToLongBits(d: kotlin.Double): Long = d.toBits()
}

