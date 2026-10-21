package com.reeflive.app.render

import com.reeflive.core.sim.CoralPickPose
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The spots offered for a new coral ([CoralPickPose], My tank) drawn in the tank with the shared particle sprites (no
 * extra draw calls): a slowly turning ring of glowing beads lying on the rock around each spot and a few beads
 * drifting up out of it like a beacon, gold for the best spot and aqua for the others. Pure Kotlin, allocation free.
 */
object CoralSpotMarkers {
    const val RING = 16
    const val BEACON = 5
    /** Points per spot, and at most for all spots. */
    const val PER_SPOT = RING + BEACON + 1
    const val MAX = 4 * PER_SPOT
    /** Ring radius (cm) and how far above the surface it floats (clear of the rock's bumps). */
    const val RADIUS_CM = 3.2f
    const val LIFT_CM = 0.9f
    const val BEACON_CM = 7f

    private val GOLD = floatArrayOf(1f, 0.78f, 0.25f)
    private val AQUA = floatArrayOf(0.35f, 0.92f, 1f)

    fun pack(pick: CoralPickPose?, time: Float, sink: PointSink, max: Int) {
        if (pick == null || max <= 0) return
        var left = max
        for (s in pick.spots) {
            if (left < PER_SPOT) return
            left -= PER_SPOT
            val c = if (s.rank == 0) GOLD else AQUA
            // a frame on the surface: u, v across it, n out of it
            var nx = s.nx; var ny = s.ny; var nz = s.nz
            val nl = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-4f)
            nx /= nl; ny /= nl; nz /= nl
            // u = normalize(n × X) (or n × Z when n is along X)
            var ux = 0f; var uy = nz; var uz = -ny
            if (uy * uy + uz * uz < 1e-4f) { ux = ny; uy = -nx; uz = 0f }
            val ul = sqrt(ux * ux + uy * uy + uz * uz)
            ux /= ul; uy /= ul; uz /= ul
            val vx = ny * uz - nz * uy; val vy = nz * ux - nx * uz; val vz = nx * uy - ny * ux
            val cx = s.x + nx * LIFT_CM; val cy = s.y + ny * LIFT_CM; val cz = s.z + nz * LIFT_CM
            val pulse = 0.75f + 0.25f * sin(time * 3f + s.rank * 1.7f)
            val spin = time * 0.6f + s.rank
            for (i in 0 until RING) {
                val a = spin + i * (6.2831855f / RING)
                val ca = cos(a) * RADIUS_CM; val sa = sin(a) * RADIUS_CM
                // every other bead brighter: reads as a dashed, turning ring
                val bead = if (i % 2 == 0) 0.55f else 0.4f
                sink.put(cx + ux * ca + vx * sa, cy + uy * ca + vy * sa, cz + uz * ca + vz * sa, bead, c[0], c[1], c[2], 0.9f * pulse, Shape.GLOW, i.toFloat(), 0f, 1f)
            }
            // the centre glows, and beads rise from it and fade
            sink.put(cx, cy + 0.3f, cz, 1.6f * pulse, c[0], c[1], c[2], 0.55f, Shape.GLOW, 0f, 0f, 1f)
            for (k in 0 until BEACON) {
                val t = ((time * 0.35f + k.toFloat() / BEACON + s.rank * 0.13f) % 1f)
                sink.put(cx, cy + 0.6f + t * BEACON_CM, cz, 0.7f * (1f - 0.5f * t), c[0], c[1], c[2], 0.85f * (1f - t), Shape.GLOW, k.toFloat(), 0f, 1f)
            }
        }
    }
}
