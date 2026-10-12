package com.reeflive.app.render

import kotlin.math.max
import kotlin.math.sqrt

/**
 * Tap selection (ARCHITECTURE §8.2): a ray from the camera against each organism's bounding capsules
 * (4 segments along its spine, radius ∝ body length). Pure CPU math. Small animals get a minimum pick
 * radius so they stay tappable on a phone.
 */
object Picker {
    /** Capsule radius as a fraction of body length (fish depth/2 ≈ 0.2 BL), floored at [MIN_RADIUS_CM]. */
    const val RADIUS_FRACTION = 0.22f
    const val MIN_RADIUS_CM = 1.2f
    private val SEGMENTS = intArrayOf(0, 5, 10, 15)

    /**
     * Returns the id of the nearest organism whose capsules the ray (origin [o], unit direction [d]) hits,
     * or null. Invisible organisms are skipped.
     */
    fun pick(o: FloatArray, d: FloatArray, rigs: Collection<FishRig>): String? {
        var best: String? = null
        var bestT = Float.MAX_VALUE
        for (r in rigs) {
            if (!r.visible) continue
            val rad = max(r.scale * RADIUS_FRACTION, MIN_RADIUS_CM)
            for (k in 0 until SEGMENTS.size - 1) {
                val a = SEGMENTS[k] * 4; val b = SEGMENTS[k + 1] * 4
                val t = rayCapsule(o, d, r.spine[a], r.spine[a + 1], r.spine[a + 2], r.spine[b], r.spine[b + 1], r.spine[b + 2], rad)
                if (t != null && t < bestT) { bestT = t; best = r.id }
            }
        }
        return best
    }

    /** Ray (o, unit d) vs capsule (segment p→q, radius r): nearest t ≥ 0 or null (closest-approach test). */
    fun rayCapsule(o: FloatArray, d: FloatArray, px: Float, py: Float, pz: Float, qx: Float, qy: Float, qz: Float, r: Float): Float? {
        // closest points between the ray and the segment
        val ux = qx - px; val uy = qy - py; val uz = qz - pz
        val wx = o[0] - px; val wy = o[1] - py; val wz = o[2] - pz
        val a = d[0] * d[0] + d[1] * d[1] + d[2] * d[2]
        val b = d[0] * ux + d[1] * uy + d[2] * uz
        val c = ux * ux + uy * uy + uz * uz
        val dd = d[0] * wx + d[1] * wy + d[2] * wz
        val e = ux * wx + uy * wy + uz * wz
        val den = a * c - b * b
        var sc: Float; var tc: Float
        if (c < 1e-8f) { // degenerate segment → sphere
            sc = max(0f, -dd / a); tc = 0f
        } else {
            sc = if (den < 1e-8f) 0f else (b * e - c * dd) / den
            sc = max(sc, 0f)
            tc = ((b * sc + e) / c).coerceIn(0f, 1f)
            // re-solve the ray parameter for the clamped segment point
            sc = max(0f, (b * tc - dd) / a)
        }
        val cx = o[0] + d[0] * sc - (px + ux * tc)
        val cy = o[1] + d[1] * sc - (py + uy * tc)
        val cz = o[2] + d[2] * sc - (pz + uz * tc)
        val dist2 = cx * cx + cy * cy + cz * cz
        if (dist2 > r * r) return null
        // back off to the entry point (approximate: the sphere at the closest segment point)
        return max(0f, sc - sqrt(max(0f, r * r - dist2)))
    }

    /** Ray vs axis-aligned plane, e.g. the front glass z = 33: returns t or null. */
    fun rayPlaneZ(o: FloatArray, d: FloatArray, z: Float): Float? {
        if (kotlin.math.abs(d[2]) < 1e-6f) return null
        val t = (z - o[2]) / d[2]
        return if (t >= 0f) t else null
    }

    fun rayPlaneY(o: FloatArray, d: FloatArray, y: Float): Float? {
        if (kotlin.math.abs(d[1]) < 1e-6f) return null
        val t = (y - o[1]) / d[1]
        return if (t >= 0f) t else null
    }
}
