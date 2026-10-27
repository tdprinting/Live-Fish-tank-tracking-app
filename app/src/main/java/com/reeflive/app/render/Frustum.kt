package com.reeflive.app.render

import kotlin.math.sqrt

/**
 * The camera's six clip planes, pulled out of a column-major view-projection matrix once per frame, for cheap
 * bounding-sphere culling of animals that are off screen (their skinned vertex shader is the costliest per-vertex work
 * in the frame). Pure Kotlin, no allocation after construction.
 */
class Frustum {
    /** 6 planes × (a, b, c, d), normalised so a·x + b·y + c·z + d is a distance in cm (inside ≥ 0). */
    private val p = FloatArray(24)

    /** Extracts the planes of [m] (column-major, element (row, col) at col × 4 + row; Gribb–Hartmann). */
    fun set(m: FloatArray) {
        for (i in 0 until 3) {
            plane(i * 2, m, i, 1f)       // left / bottom / near: row3 + row_i
            plane(i * 2 + 1, m, i, -1f)  // right / top / far:    row3 − row_i
        }
    }

    private fun plane(k: Int, m: FloatArray, row: Int, sign: Float) {
        val a = m[3] + sign * m[row]
        val b = m[7] + sign * m[4 + row]
        val c = m[11] + sign * m[8 + row]
        val d = m[15] + sign * m[12 + row]
        val l = sqrt(a * a + b * b + c * c)
        val inv = if (l > 1e-12f) 1f / l else 0f
        val o = k * 4
        p[o] = a * inv; p[o + 1] = b * inv; p[o + 2] = c * inv; p[o + 3] = d * inv
    }

    /** False only when the sphere at ([x], [y], [z]) of radius [r] (cm) lies wholly outside a plane. */
    fun sphere(x: Float, y: Float, z: Float, r: Float): Boolean {
        var o = 0
        while (o < 24) {
            if (p[o] * x + p[o + 1] * y + p[o + 2] * z + p[o + 3] < -r) return false
            o += 4
        }
        return true
    }
}
