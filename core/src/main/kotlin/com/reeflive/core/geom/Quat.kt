package com.reeflive.core.geom

import kotlin.math.sqrt

/**
 * Unit quaternion (x, y, z, w). Used for the per-spine-point body frames shipped to the renderer
 * (ARCHITECTURE §8.8 `OrganismPose.frames`): the rotation maps model axes
 * X = forward (snout direction), Y = dorsal up, Z = forward × up into world space.
 */
data class Quat(val x: Double, val y: Double, val z: Double, val w: Double) {

    fun rotate(v: Vec3): Vec3 {
        // v' = q v q*, optimized form
        val tx = 2 * (y * v.z - z * v.y)
        val ty = 2 * (z * v.x - x * v.z)
        val tz = 2 * (x * v.y - y * v.x)
        return Vec3(
            v.x + w * tx + (y * tz - z * ty),
            v.y + w * ty + (z * tx - x * tz),
            v.z + w * tz + (x * ty - y * tx),
        )
    }

    operator fun times(o: Quat) = Quat(
        w * o.x + x * o.w + y * o.z - z * o.y,
        w * o.y - x * o.z + y * o.w + z * o.x,
        w * o.z + x * o.y - y * o.x + z * o.w,
        w * o.w - x * o.x - y * o.y - z * o.z,
    )

    fun normalized(): Quat {
        val l = sqrt(x * x + y * y + z * z + w * w)
        return if (l < 1e-12) IDENTITY else Quat(x / l, y / l, z / l, w / l)
    }

    fun dot(o: Quat) = x * o.x + y * o.y + z * o.z + w * o.w

    /** Normalized lerp along the shortest arc. */
    fun nlerp(o: Quat, t: Double): Quat {
        val s = if (dot(o) < 0) -1.0 else 1.0
        return Quat(
            x + (o.x * s - x) * t, y + (o.y * s - y) * t,
            z + (o.z * s - z) * t, w + (o.w * s - w) * t,
        ).normalized()
    }

    companion object {
        val IDENTITY = Quat(0.0, 0.0, 0.0, 1.0)

        /**
         * Rotation whose columns are [forward], [up] and forward × up (all re-orthonormalized).
         * [up] need not be exactly perpendicular to [forward].
         */
        fun fromForwardUp(forward: Vec3, up: Vec3): Quat {
            val f = forward.normalized()
            var u = up.rejectFrom(f)
            if (u.lengthSq() < 1e-10) u = (if (kotlin.math.abs(f.y) < 0.9) Vec3.Y else Vec3.X).rejectFrom(f)
            u = u.normalized()
            val s = f cross u
            return fromColumns(f, u, s)
        }

        /** Quaternion from an orthonormal basis given as matrix columns c0, c1, c2. */
        fun fromColumns(c0: Vec3, c1: Vec3, c2: Vec3): Quat {
            val m00 = c0.x; val m10 = c0.y; val m20 = c0.z
            val m01 = c1.x; val m11 = c1.y; val m21 = c1.z
            val m02 = c2.x; val m12 = c2.y; val m22 = c2.z
            val tr = m00 + m11 + m22
            return if (tr > 0) {
                val s = sqrt(tr + 1.0) * 2
                Quat((m21 - m12) / s, (m02 - m20) / s, (m10 - m01) / s, 0.25 * s)
            } else if (m00 > m11 && m00 > m22) {
                val s = sqrt(1.0 + m00 - m11 - m22) * 2
                Quat(0.25 * s, (m01 + m10) / s, (m02 + m20) / s, (m21 - m12) / s)
            } else if (m11 > m22) {
                val s = sqrt(1.0 + m11 - m00 - m22) * 2
                Quat((m01 + m10) / s, 0.25 * s, (m12 + m21) / s, (m02 - m20) / s)
            } else {
                val s = sqrt(1.0 + m22 - m00 - m11) * 2
                Quat((m02 + m20) / s, (m12 + m21) / s, 0.25 * s, (m10 - m01) / s)
            }.normalized()
        }

        fun axisAngle(axis: Vec3, angle: Double): Quat {
            val a = axis.normalized()
            val s = kotlin.math.sin(angle / 2)
            return Quat(a.x * s, a.y * s, a.z * s, kotlin.math.cos(angle / 2))
        }
    }
}
