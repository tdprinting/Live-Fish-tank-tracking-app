package com.reeflive.core.geom

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sign

/** A jet source (powerhead / HOB return), SIMULATION §5.3. */
data class Powerhead(
    val name: String,
    val pos: Vec3,
    val dir: Vec3,
    val v0: Double = 60.0,
    val r0: Double = 2.5,
    val on: Boolean = true,
) {
    val axis: Vec3 = dir.normalized()
}

/**
 * Tank water velocity field in cm/s (SIMULATION §5.3).
 *
 * Each jet: `u = axis·(p − src)`, `r = |(p − src) − u·axis|`,
 * `v = V0 (R0/(R0 + 0.08u))² exp(−(r/(R0 + 0.12u))²)` for u > 0, plus a slow global return
 * circulation (−x near the bottom, +x near the top), scaled by rock shadowing
 * `clamp(sdf/8, 0.15, 1)`. With powerheads off only the global field ×0.2 remains.
 * A gentle wavemaker pulse modulates the jets (±[pulseAmp], 20 s period; tune).
 */
class FlowField(
    jets: List<Powerhead>,
    private val sdf: Sdf,
    private val tankLength: Double = 121.9,
    private val waterLevel: Double = 56.0,
    private val pulseAmp: Double = 0.12,
) {
    var jets: List<Powerhead> = jets
        set(v) { field = v; gridValid = false }

    /** Master switch (Command.Powerheads); HOB keeps running unless also disabled. */
    var powerheadsOn: Boolean = true
        set(v) { field = v; gridValid = false }

    /** Sandbox pump strength multiplier on the powerhead jets (0 = off … 2 = double), HOB unaffected. */
    var strength: Double = 1.0
        set(v) { field = v.coerceIn(0.0, 2.0); gridValid = false }

    /** Speed of a single jet at point p (no shadowing). Exposed for tests (§8.9). */
    fun jetVelocity(j: Powerhead, p: Vec3): Vec3 {
        val rel = p - j.pos
        val u = rel dot j.axis
        if (u <= 0) return Vec3.ZERO
        val r = (rel - j.axis * u).length()
        val spread = j.r0 + 0.12 * u
        val decay = j.r0 / (j.r0 + 0.08 * u)
        val v = j.v0 * decay * decay * exp(-(r / spread) * (r / spread))
        return j.axis * v
    }

    private fun globalCirculation(p: Vec3): Vec3 {
        val mid = tankLength / 2
        return when {
            p.y < 20 -> Vec3(-1.5 * sign(p.x - mid), 0.0, 0.0)
            p.y > waterLevel - 15 -> Vec3(1.5 * sign(p.x - mid + 1e-6), 0.0, 0.0)
            else -> Vec3(0.0, if (p.x < mid) -0.4 else 0.4, 0.0)
        }
    }

    private fun isPowerhead(j: Powerhead) = !j.name.startsWith("HOB")

    /** Full-fidelity velocity at p and sim time t. */
    fun velocity(p: Vec3, t: Double = 0.0): Vec3 {
        var v = Vec3.ZERO
        var any = false
        val pulse = 1.0 + pulseAmp * sin(2 * PI * t / 20.0)
        for (j in jets) {
            if (!j.on) continue
            if (isPowerhead(j) && !powerheadsOn) continue
            any = any || isPowerhead(j)
            v += jetVelocity(j, p) * (if (isPowerhead(j)) pulse * strength else 1.0)
        }
        v += globalCirculation(p) * (if (any) strength.coerceAtLeast(0.2) else 0.2)
        val shadow = (sdf.dist(p) / 8.0).coerceIn(0.15, 1.0)
        return v * shadow
    }

    // ---- coarse cached grid for cheap queries (ambient particles, renderer) ----
    private val cell = 4.0
    private val gx = 32; private val gy = 16; private val gz = 10
    private var grid = FloatArray(gx * gy * gz * 3)
    @Volatile private var gridValid = false

    private fun buildGrid() {
        val g = FloatArray(gx * gy * gz * 3)
        for (z in 0 until gz) for (y in 0 until gy) for (x in 0 until gx) {
            val v = velocity(Vec3(x * cell, y * cell, z * cell))
            val i = ((z * gy + y) * gx + x) * 3
            g[i] = v.x.toFloat(); g[i + 1] = v.y.toFloat(); g[i + 2] = v.z.toFloat()
        }
        grid = g
        gridValid = true
    }

    /** Trilinear lookup in a 4 cm grid (built lazily, invalidated when jets change). */
    fun velocityFast(p: Vec3): Vec3 {
        if (!gridValid) synchronized(this) { if (!gridValid) buildGrid() }
        val g = grid
        val fx = (p.x / cell).coerceIn(0.0, gx - 1.001); val fy = (p.y / cell).coerceIn(0.0, gy - 1.001); val fz = (p.z / cell).coerceIn(0.0, gz - 1.001)
        val x0 = floor(fx).toInt(); val y0 = floor(fy).toInt(); val z0 = floor(fz).toInt()
        val tx = fx - x0; val ty = fy - y0; val tz = fz - z0
        var rx = 0.0; var ry = 0.0; var rz = 0.0
        for (dz in 0..1) for (dy in 0..1) for (dx in 0..1) {
            val w = (if (dx == 1) tx else 1 - tx) * (if (dy == 1) ty else 1 - ty) * (if (dz == 1) tz else 1 - tz)
            val i = (((z0 + dz) * gy + (y0 + dy)) * gx + (x0 + dx)) * 3
            rx += g[i] * w; ry += g[i + 1] * w; rz += g[i + 2] * w
        }
        return Vec3(rx, ry, rz)
    }

    companion object {
        /** Powerheads observed in Tee's tank photos (SIMULATION §5.3). HOB x = 71 to match ASSETS/anchors. */
        fun defaultJets(): List<Powerhead> = listOf(
            Powerhead("PH-A", Vec3(84.0, 48.0, 1.0), Vec3(-0.6, -0.35, 0.72)),
            Powerhead("PH-B", Vec3(1.0, 42.0, 10.0), Vec3(0.95, -0.2, 0.25)),
            Powerhead("HOB", Vec3(71.0, 55.0, 2.0), Vec3(0.0, -0.5, 0.87), v0 = 25.0),
        )
    }
}
