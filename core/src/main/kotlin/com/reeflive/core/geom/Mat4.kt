package com.reeflive.core.geom

import kotlin.math.tan

/**
 * Column-major 4x4 float matrices stored in a FloatArray(16), the same layout OpenGL expects
 * (element (row r, col c) at index c*4 + r). Pure Kotlin so camera/picking math is unit-testable.
 */
object Mat4 {
    fun identity(out: FloatArray = FloatArray(16)): FloatArray {
        out.fill(0f); out[0] = 1f; out[5] = 1f; out[10] = 1f; out[15] = 1f
        return out
    }

    fun perspective(fovYDeg: Double, aspect: Double, near: Double, far: Double, out: FloatArray = FloatArray(16)): FloatArray {
        val f = 1.0 / tan(Math.toRadians(fovYDeg) / 2)
        out.fill(0f)
        out[0] = (f / aspect).toFloat()
        out[5] = f.toFloat()
        out[10] = ((far + near) / (near - far)).toFloat()
        out[11] = -1f
        out[14] = (2 * far * near / (near - far)).toFloat()
        return out
    }

    fun lookAt(eye: Vec3, target: Vec3, up: Vec3, out: FloatArray = FloatArray(16)): FloatArray {
        val f = (target - eye).normalized()
        var s = f cross up
        if (s.lengthSq() < 1e-12) s = f cross Vec3.Z
        s = s.normalized()
        val u = s cross f
        out[0] = s.x.toFloat(); out[4] = s.y.toFloat(); out[8] = s.z.toFloat()
        out[1] = u.x.toFloat(); out[5] = u.y.toFloat(); out[9] = u.z.toFloat()
        out[2] = (-f.x).toFloat(); out[6] = (-f.y).toFloat(); out[10] = (-f.z).toFloat()
        out[3] = 0f; out[7] = 0f; out[11] = 0f
        out[12] = (-(s dot eye)).toFloat()
        out[13] = (-(u dot eye)).toFloat()
        out[14] = (f dot eye).toFloat()
        out[15] = 1f
        return out
    }

    /** out = a × b (out may not alias a or b). */
    fun multiply(a: FloatArray, b: FloatArray, out: FloatArray = FloatArray(16)): FloatArray {
        for (c in 0 until 4) for (r in 0 until 4) {
            var s = 0f
            for (k in 0 until 4) s += a[k * 4 + r] * b[c * 4 + k]
            out[c * 4 + r] = s
        }
        return out
    }

    /** Translation × rotation(quaternion) × uniform scale. */
    fun trs(t: Vec3, q: Quat, s: Double, out: FloatArray = FloatArray(16)): FloatArray =
        trs(t, q, Vec3(s, s, s), out)

    fun trs(t: Vec3, q: Quat, s: Vec3, out: FloatArray = FloatArray(16)): FloatArray {
        val x = q.x; val y = q.y; val z = q.z; val w = q.w
        val xx = x * x; val yy = y * y; val zz = z * z
        val xy = x * y; val xz = x * z; val yz = y * z
        val wx = w * x; val wy = w * y; val wz = w * z
        out[0] = ((1 - 2 * (yy + zz)) * s.x).toFloat()
        out[1] = (2 * (xy + wz) * s.x).toFloat()
        out[2] = (2 * (xz - wy) * s.x).toFloat()
        out[3] = 0f
        out[4] = (2 * (xy - wz) * s.y).toFloat()
        out[5] = ((1 - 2 * (xx + zz)) * s.y).toFloat()
        out[6] = (2 * (yz + wx) * s.y).toFloat()
        out[7] = 0f
        out[8] = (2 * (xz + wy) * s.z).toFloat()
        out[9] = (2 * (yz - wx) * s.z).toFloat()
        out[10] = ((1 - 2 * (xx + yy)) * s.z).toFloat()
        out[11] = 0f
        out[12] = t.x.toFloat(); out[13] = t.y.toFloat(); out[14] = t.z.toFloat(); out[15] = 1f
        return out
    }

    fun transformPoint(m: FloatArray, p: Vec3): Vec3 {
        val x = m[0] * p.x + m[4] * p.y + m[8] * p.z + m[12]
        val y = m[1] * p.x + m[5] * p.y + m[9] * p.z + m[13]
        val z = m[2] * p.x + m[6] * p.y + m[10] * p.z + m[14]
        val w = m[3] * p.x + m[7] * p.y + m[11] * p.z + m[15]
        return if (w != 0.0 && w != 1.0) Vec3(x / w, y / w, z / w) else Vec3(x, y, z)
    }

    /** General 4x4 inverse; returns false if singular. */
    fun invert(m: FloatArray, out: FloatArray): Boolean {
        val inv = DoubleArray(16)
        val a = DoubleArray(16) { m[it].toDouble() }
        inv[0] = a[5] * a[10] * a[15] - a[5] * a[11] * a[14] - a[9] * a[6] * a[15] + a[9] * a[7] * a[14] + a[13] * a[6] * a[11] - a[13] * a[7] * a[10]
        inv[4] = -a[4] * a[10] * a[15] + a[4] * a[11] * a[14] + a[8] * a[6] * a[15] - a[8] * a[7] * a[14] - a[12] * a[6] * a[11] + a[12] * a[7] * a[10]
        inv[8] = a[4] * a[9] * a[15] - a[4] * a[11] * a[13] - a[8] * a[5] * a[15] + a[8] * a[7] * a[13] + a[12] * a[5] * a[11] - a[12] * a[7] * a[9]
        inv[12] = -a[4] * a[9] * a[14] + a[4] * a[10] * a[13] + a[8] * a[5] * a[14] - a[8] * a[6] * a[13] - a[12] * a[5] * a[10] + a[12] * a[6] * a[9]
        inv[1] = -a[1] * a[10] * a[15] + a[1] * a[11] * a[14] + a[9] * a[2] * a[15] - a[9] * a[3] * a[14] - a[13] * a[2] * a[11] + a[13] * a[3] * a[10]
        inv[5] = a[0] * a[10] * a[15] - a[0] * a[11] * a[14] - a[8] * a[2] * a[15] + a[8] * a[3] * a[14] + a[12] * a[2] * a[11] - a[12] * a[3] * a[10]
        inv[9] = -a[0] * a[9] * a[15] + a[0] * a[11] * a[13] + a[8] * a[1] * a[15] - a[8] * a[3] * a[13] - a[12] * a[1] * a[11] + a[12] * a[3] * a[9]
        inv[13] = a[0] * a[9] * a[14] - a[0] * a[10] * a[13] - a[8] * a[1] * a[14] + a[8] * a[2] * a[13] + a[12] * a[1] * a[10] - a[12] * a[2] * a[9]
        inv[2] = a[1] * a[6] * a[15] - a[1] * a[7] * a[14] - a[5] * a[2] * a[15] + a[5] * a[3] * a[14] + a[13] * a[2] * a[7] - a[13] * a[3] * a[6]
        inv[6] = -a[0] * a[6] * a[15] + a[0] * a[7] * a[14] + a[4] * a[2] * a[15] - a[4] * a[3] * a[14] - a[12] * a[2] * a[7] + a[12] * a[3] * a[6]
        inv[10] = a[0] * a[5] * a[15] - a[0] * a[7] * a[13] - a[4] * a[1] * a[15] + a[4] * a[3] * a[13] + a[12] * a[1] * a[7] - a[12] * a[3] * a[5]
        inv[14] = -a[0] * a[5] * a[14] + a[0] * a[6] * a[13] + a[4] * a[1] * a[14] - a[4] * a[2] * a[13] - a[12] * a[1] * a[6] + a[12] * a[2] * a[5]
        inv[3] = -a[1] * a[6] * a[11] + a[1] * a[7] * a[10] + a[5] * a[2] * a[11] - a[5] * a[3] * a[10] - a[9] * a[2] * a[7] + a[9] * a[3] * a[6]
        inv[7] = a[0] * a[6] * a[11] - a[0] * a[7] * a[10] - a[4] * a[2] * a[11] + a[4] * a[3] * a[10] + a[8] * a[2] * a[7] - a[8] * a[3] * a[6]
        inv[11] = -a[0] * a[5] * a[11] + a[0] * a[7] * a[9] + a[4] * a[1] * a[11] - a[4] * a[3] * a[9] - a[8] * a[1] * a[7] + a[8] * a[3] * a[5]
        inv[15] = a[0] * a[5] * a[10] - a[0] * a[6] * a[9] - a[4] * a[1] * a[10] + a[4] * a[2] * a[9] + a[8] * a[1] * a[6] - a[8] * a[2] * a[5]
        val det = a[0] * inv[0] + a[1] * inv[4] + a[2] * inv[8] + a[3] * inv[12]
        if (kotlin.math.abs(det) < 1e-20) return false
        for (i in 0 until 16) out[i] = (inv[i] / det).toFloat()
        return true
    }
}
