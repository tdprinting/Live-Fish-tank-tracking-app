package com.reeflive.core.geom

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Immutable 3D vector in world centimetres (ARCHITECTURE §8.3: +X right, +Y up, +Z toward the viewer). */
data class Vec3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Double) = Vec3(x * s, y * s, z * s)
    operator fun div(s: Double) = Vec3(x / s, y / s, z / s)
    operator fun unaryMinus() = Vec3(-x, -y, -z)

    infix fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    infix fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)

    fun lengthSq() = x * x + y * y + z * z
    fun length() = sqrt(lengthSq())
    fun distanceTo(o: Vec3): Double {
        val dx = x - o.x; val dy = y - o.y; val dz = z - o.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
    fun distanceSqTo(o: Vec3): Double {
        val dx = x - o.x; val dy = y - o.y; val dz = z - o.z
        return dx * dx + dy * dy + dz * dz
    }

    /** Unit vector; returns [fallback] for (near) zero vectors. */
    fun normalized(fallback: Vec3 = X): Vec3 {
        val l = length()
        return if (l < 1e-9) fallback else Vec3(x / l, y / l, z / l)
    }

    /** Vector with length clamped to [max]. */
    fun clampLength(max: Double): Vec3 {
        val l2 = lengthSq()
        return if (l2 <= max * max || l2 < 1e-18) this else this * (max / sqrt(l2))
    }

    fun withY(ny: Double) = Vec3(x, ny, z)
    fun horizontal() = Vec3(x, 0.0, z)
    fun lerp(o: Vec3, t: Double) = Vec3(x + (o.x - x) * t, y + (o.y - y) * t, z + (o.z - z) * t)
    fun isFinite() = x.isFinite() && y.isFinite() && z.isFinite()

    /** Component of this vector perpendicular to the unit vector [n]. */
    fun rejectFrom(n: Vec3): Vec3 = this - n * (this dot n)

    override fun toString(): String = "(%.2f, %.2f, %.2f)".format(x, y, z)

    companion object {
        val ZERO = Vec3(0.0, 0.0, 0.0)
        val X = Vec3(1.0, 0.0, 0.0)
        val Y = Vec3(0.0, 1.0, 0.0)
        val Z = Vec3(0.0, 0.0, 1.0)
        fun of(a: List<Double>) = Vec3(a[0], a[1], a[2])

        /** Angle in radians between two non-zero vectors. */
        fun angle(a: Vec3, b: Vec3): Double {
            val d = (a dot b) / (a.length() * b.length() + 1e-12)
            return acos(d.coerceIn(-1.0, 1.0))
        }

        /**
         * Rotates unit vector [from] toward unit vector [to] by at most [maxAngle] radians
         * (great-circle step). Handles antiparallel inputs by rotating about [fallbackAxis].
         */
        fun rotateToward(from: Vec3, to: Vec3, maxAngle: Double, fallbackAxis: Vec3 = Y): Vec3 {
            val ang = angle(from, to)
            if (ang <= maxAngle || ang < 1e-9) return to.normalized(from)
            var axis = from cross to
            if (axis.lengthSq() < 1e-12) {
                axis = from cross fallbackAxis
                if (axis.lengthSq() < 1e-12) axis = from cross X
            }
            return rotateAbout(from, axis.normalized(), maxAngle).normalized(from)
        }

        /** Rodrigues rotation of [v] about unit [axis] by [angle] radians. */
        fun rotateAbout(v: Vec3, axis: Vec3, angle: Double): Vec3 {
            val c = cos(angle); val s = sin(angle)
            return v * c + (axis cross v) * s + axis * ((axis dot v) * (1 - c))
        }
    }
}

fun clamp01(v: Double) = if (v < 0.0) 0.0 else if (v > 1.0) 1.0 else v
fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t
fun smoothstep(e0: Double, e1: Double, x: Double): Double {
    val t = clamp01((x - e0) / (e1 - e0))
    return t * t * (3 - 2 * t)
}
fun approxEq(a: Double, b: Double, eps: Double = 1e-9) = abs(a - b) <= eps
