package com.reeflive.core.geom

import com.reeflive.core.sim.TankDims
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Water-surface heightfield (cm) at y = waterLevel, from the 2-D damped wave equation
 * `h_tt = c² ∇²h − γ h_t` (SIMULATION §9.7), integrated with an explicit leapfrog scheme and
 * substeps that keep `c·dt/dx ≤ 0.5`. Wave speed c = 23 cm/s is the gravity–capillary minimum
 * phase speed of water.
 *
 * Disturbances:
 * - The HOB return (continuous splashing).
 * - Pump agitation under the surface (from the flow's top layer).
 * - Food landing on the surface.
 * - Taps.
 *
 * [heights] is published for the renderer, which refracts light through the surface normals for
 * caustics and the view from below.
 */
class WaterSurface(
    val lengthCm: Double = TankDims.LENGTH_CM,
    val depthCm: Double = TankDims.DEPTH_CM,
    val sx: Int = 96,
    /** Square-ish cells: ≈ 1.27 cm along both axes. */
    val sz: Int = kotlin.math.max(4, kotlin.math.round(sx * depthCm / lengthCm).toInt()),
    seed: Long = 7L,
) {
    val dx = lengthCm / sx
    val dz = depthCm / sz
    private var cur = FloatArray(sx * sz)
    private var prev = FloatArray(sx * sz)
    private var next = FloatArray(sx * sz)
    private val rng = Rng(seed)
    var waveSpeed = 23.0
    var damping = 1.2           // 1/s
    var hobPos: Vec3? = Vec3(73.0, TankDims.WATER_LEVEL_CM, 2.0)   // equipment.glb ReturnNozzle `outflow` (back middle, video T97)
    var hobOn = true
    /** Surface agitation from the pumps (0..2, set from the flow solver or pump strength). */
    var agitation = 1.0

    val heights: FloatArray get() = cur

    private fun id(i: Int, k: Int) = i + sx * k

    fun impulse(x: Double, z: Double, amp: Double, radiusCm: Double = 1.5) {
        val ci = (x / dx).toInt(); val ck = (z / dz).toInt()
        val r = max(1, (radiusCm / dx).toInt() + 1)
        for (k in ck - r..ck + r) for (i in ci - r..ci + r) {
            if (i !in 0 until sx || k !in 0 until sz) continue
            val d = sqrt(((i - ci) * dx).let { it * it } + ((k - ck) * dz).let { it * it })
            if (d > radiusCm) continue
            val f = 0.5 * (1 + kotlin.math.cos(Math.PI * d / radiusCm))
            cur[id(i, k)] -= (amp * f).toFloat()
        }
    }

    fun step(dtIn: Double) {
        val dt = min(dtIn, 0.1)
        val maxDt = 0.5 * min(dx, dz) / waveSpeed
        val subs = max(1, kotlin.math.ceil(dt / maxDt).toInt())
        val h = dt / subs
        repeat(subs) { sub(h) }
        // sources (once per call; rates scale with dt)
        hobPos?.let { p -> if (hobOn && rng.chance(min(1.0, dt * 25))) impulse(p.x + rng.signed() * 4, p.z + 1 + rng.nextDouble() * 4, 0.05 + 0.05 * rng.nextDouble(), 1.6) }
        if (agitation > 0 && rng.chance(min(1.0, dt * 12 * agitation))) impulse(rng.range(0.0, lengthCm), rng.range(0.0, depthCm), 0.015 * agitation, 2.5)
        // volume is conserved: impulses displace water, they don't remove it (keep the mean level at 0)
        var mean = 0.0
        for (x in cur) mean += x
        val m = (mean / cur.size).toFloat()
        for (i in cur.indices) { cur[i] -= m; prev[i] -= m }
    }

    private fun sub(dt: Double) {
        val c2 = (waveSpeed * dt).let { it * it }
        val ax = c2 / (dx * dx); val az = c2 / (dz * dz)
        val damp = exp(-damping * dt).toFloat()
        for (k in 0 until sz) for (i in 0 until sx) {
            val c = cur[id(i, k)]
            // reflective (Neumann) edges at the glass
            val l = cur[id(max(i - 1, 0), k)]; val r = cur[id(min(i + 1, sx - 1), k)]
            val d = cur[id(i, max(k - 1, 0))]; val u = cur[id(i, min(k + 1, sz - 1))]
            val lap = ax * (l + r - 2 * c) + az * (d + u - 2 * c)
            next[id(i, k)] = ((2 * c - prev[id(i, k)]) * 1f + lap.toFloat()).let { c + (it - c) * damp }
        }
        val t = prev; prev = cur; cur = next; next = t
    }

    /** Surface slope (∂h/∂x, ∂h/∂z) at a point; the normal is (−sx, 1, −sz) normalized. */
    fun slope(x: Double, z: Double): Pair<Double, Double> {
        val i = (x / dx).toInt().coerceIn(1, sx - 2); val k = (z / dz).toInt().coerceIn(1, sz - 2)
        return Pair((cur[id(i + 1, k)] - cur[id(i - 1, k)]) / (2 * dx), (cur[id(i, k + 1)] - cur[id(i, k - 1)]) / (2 * dz))
    }

    fun energy(): Double { var e = 0.0; for (i in cur.indices) e += cur[i] * cur[i]; return e }
}
