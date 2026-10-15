package com.reeflive.app.render

import kotlin.math.max
import kotlin.math.sqrt

/**
 * Tap selection (ARCHITECTURE §8.2): a ray from the camera against each organism's bounding capsules
 * (4 segments along its spine, radius ∝ body length). Pure CPU math. Small animals get a minimum pick
 * radius, and a near miss within a fingertip's width still picks the closest animal, so they stay tappable on a phone.
 */
object Picker {
    /** Capsule radius as a fraction of body length (fish depth/2 ≈ 0.2 BL), floored at [MIN_RADIUS_CM]. */
    const val RADIUS_FRACTION = 0.22f
    const val MIN_RADIUS_CM = 1.2f
    private val SEGMENTS = intArrayOf(0, 5, 10, 15)

    /**
     * Returns the id of the nearest organism whose capsules the ray (origin [o], unit direction [d]) hits, or null.
     * Invisible organisms are skipped.
     *
     * [slack] makes small animals forgiving on a phone: a fingertip covers ~7 mm of glass, so when the ray misses every
     * capsule, the animal whose capsule it passes closest to (as a view angle: miss distance / depth, compared with
     * [slack], the tangent of the finger's angular radius from [fingerSlack]) is picked. A direct hit always wins; among
     * near misses the smallest angular miss wins, so a tap beside a snail picks the snail, not the tang behind it.
     */
    fun pick(o: FloatArray, d: FloatArray, rigs: Collection<FishRig>, slack: Float = 0f): String? {
        var best: String? = null
        var bestT = Float.MAX_VALUE
        var near: String? = null
        var nearMiss = Float.MAX_VALUE
        for (r in rigs) {
            if (!r.visible || r.bagged) continue
            val rad = max(r.scale * RADIUS_FRACTION, MIN_RADIUS_CM)
            for (k in 0 until SEGMENTS.size - 1) {
                val a = SEGMENTS[k] * 4; val b = SEGMENTS[k + 1] * 4
                closest(o, d, r.spine[a], r.spine[a + 1], r.spine[a + 2], r.spine[b], r.spine[b + 1], r.spine[b + 2])
                val dist = scratch[0]; val sc = scratch[1]
                if (dist <= rad) {
                    val t = max(0f, sc - sqrt(max(0f, rad * rad - dist * dist)))
                    if (t < bestT) { bestT = t; best = r.id }
                } else if (slack > 0f && sc > 1f) {
                    val miss = (dist - rad) / sc
                    if (miss <= slack && miss < nearMiss) { nearMiss = miss; near = r.id }
                }
            }
        }
        return best ?: near
    }

    /**
     * The tangent of a fingertip's angular radius: [fingerPx] (radius, px) on a view [heightPx] tall seen through a
     * vertical field of view [fovYDeg]. Multiply by depth (cm) for the finger's radius in the tank there.
     */
    fun fingerSlack(fingerPx: Float, heightPx: Int, fovYDeg: Float): Float =
        if (heightPx <= 0 || fingerPx <= 0f) 0f
        else 2f * kotlin.math.tan(Math.toRadians(fovYDeg / 2.0).toFloat()) * fingerPx / heightPx

    /** Ray (o, unit d) vs capsule (segment p→q, radius r): nearest t ≥ 0 or null (closest-approach test). */
    fun rayCapsule(o: FloatArray, d: FloatArray, px: Float, py: Float, pz: Float, qx: Float, qy: Float, qz: Float, r: Float): Float? {
        closest(o, d, px, py, pz, qx, qy, qz)
        val dist = scratch[0]; val sc = scratch[1]
        if (dist > r) return null
        // back off to the entry point (approximate: the sphere at the closest segment point)
        return max(0f, sc - sqrt(max(0f, r * r - dist * dist)))
    }

    /** Taps are handled on one thread (the GL thread), so one scratch pair serves every call without allocating. */
    private val scratch = FloatArray(2)

    /** Closest approach of the ray (o, unit d) to segment p→q: [scratch] = (distance, ray parameter there). */
    private fun closest(o: FloatArray, d: FloatArray, px: Float, py: Float, pz: Float, qx: Float, qy: Float, qz: Float) {
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
        scratch[0] = sqrt(cx * cx + cy * cy + cz * cz)
        scratch[1] = sc
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
