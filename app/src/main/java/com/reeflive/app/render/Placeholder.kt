package com.reeflive.app.render

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Procedural stand-in for organisms whose GLB is missing (PLAN C2: "placeholder capsules for fish"):
 * a unit-length spindle facing +X with COLOR_0.a = 0.5 − x so it skins like a real fish. Pure Kotlin.
 */
object Placeholder {
    /** Returns (vertices STRIDE-12, indices). [depth] is body depth / length. */
    fun spindle(depth: Float = 0.3f, rings: Int = 16, segments: Int = 12): Pair<FloatArray, IntArray> {
        val st = GlbPrimitive.STRIDE
        val verts = FloatArray((rings + 1) * (segments + 1) * st)
        var o = 0
        for (i in 0..rings) {
            val u = i.toFloat() / rings            // 0 snout .. 1 tail
            val x = 0.5f - u
            val r = (depth * 0.5f * sin(PI * (0.08 + 0.84 * u)).toFloat() * (1f - 0.35f * u)).coerceAtLeast(0.004f)
            for (j in 0..segments) {
                val a = (2 * PI * j / segments).toFloat()
                val cy = cos(a); val sz = sin(a)
                val y = cy * r; val z = sz * r * 0.55f
                val nl = sqrt(cy * cy + (sz / 0.55f) * (sz / 0.55f)).coerceAtLeast(1e-4f)
                verts[o] = x; verts[o + 1] = y; verts[o + 2] = z
                verts[o + 3] = 0f; verts[o + 4] = cy / nl; verts[o + 5] = sz / 0.55f / nl
                verts[o + 6] = u; verts[o + 7] = 0.5f + 0.5f * cy
                verts[o + 8] = 0f; verts[o + 9] = 0f; verts[o + 10] = 0f; verts[o + 11] = u
                o += st
            }
        }
        val idx = IntArray(rings * segments * 6)
        var k = 0
        for (i in 0 until rings) for (j in 0 until segments) {
            val a = i * (segments + 1) + j; val b = a + segments + 1
            idx[k++] = a; idx[k++] = b; idx[k++] = a + 1
            idx[k++] = a + 1; idx[k++] = b; idx[k++] = b + 1
        }
        return verts to idx
    }

    /** Placeholder tint per brain archetype so stand-ins are still distinguishable. */
    fun colorFor(speciesId: String): FloatArray {
        val h = (speciesId.hashCode() and 0xFFFF) / 65535f
        val a = (h * 2 * PI).toFloat()
        return floatArrayOf(0.45f + 0.35f * cos(a), 0.45f + 0.35f * cos(a + 2.1f), 0.45f + 0.35f * cos(a + 4.2f), 1f)
    }
}
