package com.reeflive.core.geom

import com.reeflive.core.sim.TankDims
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
    private val tankLength: Double = TankDims.LENGTH_CM,
    private val waterLevel: Double = TankDims.WATER_LEVEL_CM,
    private val pulseAmp: Double = 0.12,
    private val tankDepth: Double = TankDims.DEPTH_CM,
) {
    var jets: List<Powerhead> = jets
        set(v) { field = v; gridValid = false }

    /** Master switch (Command.Powerheads); HOB keeps running unless also disabled. */
    var powerheadsOn: Boolean = true
        set(v) { field = v; gridValid = false }

    /** Sandbox pump strength multiplier on the powerhead jets (0 = off … 2 = double), HOB unaffected. */
    var strength: Double = 1.0
        set(v) { field = v.coerceIn(0.0, 2.0); gridValid = false }

    /** When set (Live Tank), velocities come from the Navier–Stokes solver instead of the analytic jets (§9.7). */
    @Volatile var fluid: FluidSim? = null

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
            p.y < 0.36 * waterLevel -> Vec3(-1.5 * sign(p.x - mid), 0.0, 0.0)
            p.y > 0.73 * waterLevel -> Vec3(1.5 * sign(p.x - mid + 1e-6), 0.0, 0.0)
            else -> Vec3(0.0, if (p.x < mid) -0.4 else 0.4, 0.0)
        }
    }

    private fun isPowerhead(j: Powerhead) = !j.name.startsWith("HOB")

    /**
     * Touch puffs in the water (`Command.WaterPuff`, a flick of the finger), added on top of the jets or the solver by
     * [velocity] and [velocityFast] (never baked into the cached grid). [now] is the sim time they are evaluated at.
     */
    val gusts = ArrayList<FlowGust>()
    var now = 0.0

    /** A puff of water at [pos] moving at [vel] (cm/s) from sim time [t]; at most [FlowGust.MAX] live at once. */
    fun addGust(pos: Vec3, vel: Vec3, t: Double) {
        val sp = vel.length()
        if (sp < 1e-6) return
        if (gusts.size >= FlowGust.MAX) gusts.removeAt(0)
        gusts += FlowGust(pos, vel / sp, sp.coerceAtMost(FlowGust.MAX_SPEED), t)
    }

    /** Drops spent puffs. */
    fun pruneGusts(t: Double) { if (gusts.isNotEmpty()) gusts.removeAll { t - it.bornAt > FlowGust.LIFE_S || t < it.bornAt - 1.0 } }

    /** Velocity the live puffs add at [p] (cm/s). */
    fun gustVelocity(p: Vec3): Vec3 {
        var vx = 0.0; var vy = 0.0; var vz = 0.0
        for (g in gusts) {
            val s = FlowGust.strength(g.pos.x, g.pos.y, g.pos.z, g.dir.x, g.dir.y, g.dir.z, g.speed, now - g.bornAt, p.x, p.y, p.z)
            vx += g.dir.x * s; vy += g.dir.y * s; vz += g.dir.z * s
        }
        return Vec3(vx, vy, vz)
    }

    /** Full-fidelity velocity at p and sim time t. */
    fun velocity(p: Vec3, t: Double = 0.0): Vec3 {
        val base = baseVelocity(p, t)
        return if (gusts.isEmpty()) base else base + gustVelocity(p)
    }

    private fun baseVelocity(p: Vec3, t: Double): Vec3 {
        fluid?.let { return it.velocity(p) }
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
    private val gx = kotlin.math.ceil(tankLength / cell).toInt() + 1
    private val gy = kotlin.math.ceil(waterLevel / cell).toInt() + 1
    private val gz = kotlin.math.ceil(tankDepth / cell).toInt() + 1
    private var grid = FloatArray(gx * gy * gz * 3)
    @Volatile private var gridValid = false

    private fun buildGrid() {
        val g = FloatArray(gx * gy * gz * 3)
        for (z in 0 until gz) for (y in 0 until gy) for (x in 0 until gx) {
            val v = baseVelocity(Vec3(x * cell, y * cell, z * cell), 0.0)
            val i = ((z * gy + y) * gx + x) * 3
            g[i] = v.x.toFloat(); g[i + 1] = v.y.toFloat(); g[i + 2] = v.z.toFloat()
        }
        grid = g
        gridValid = true
    }

    /** Trilinear lookup in a 4 cm grid (built lazily, invalidated when jets change). */
    fun velocityFast(p: Vec3): Vec3 {
        val base = baseFast(p)
        return if (gusts.isEmpty()) base else base + gustVelocity(p)
    }

    private fun baseFast(p: Vec3): Vec3 {
        fluid?.let { return it.velocity(p) }
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
        /** Powerheads observed in Tee's tank photos (SIMULATION §5.3): equipment.glb PowerheadBack / PowerheadLeft and
         *  the ReturnNozzle `outflow` (back middle, piped from the HobFilter), matching the FLOW_* / HOB_OUTFLOW anchors. */
        fun defaultJets(): List<Powerhead> = listOf(
            Powerhead("PH-A", Vec3(77.0, 32.0, 1.0), Vec3(-0.6, -0.35, 0.72)),
            Powerhead("PH-B", Vec3(1.0, 28.0, 13.0), Vec3(0.95, -0.2, 0.25)),
            Powerhead("HOB", Vec3(73.0, 37.4, 2.0), Vec3(0.0, -0.4, 0.92), v0 = 25.0),
        )
    }
}

/**
 * One puff of water pushed by a flick of the finger (`Command.WaterPuff`): a blob of moving water that travels along
 * [dir] from [pos], slowing as it spreads (a decaying vortex-ring-like slug, design estimate). [strength] is shared by
 * the sim ([FlowField.gustVelocity]) and the renderer (coral sway, burst specks) so both see the same puff.
 */
class FlowGust(val pos: Vec3, val dir: Vec3, val speed: Double, val bornAt: Double) {
    companion object {
        /** Live puffs at once, their lifetime (s), slowing time (s), start radius (cm) and fastest puff (cm/s). */
        const val MAX = 4
        const val LIFE_S = 4.0
        const val TAU_S = 0.8
        const val R0_CM = 3.5
        const val MAX_SPEED = 45.0
        /** Floats per puff in a snapshot: x, y, z, dir x, y, z, speed (cm/s), age at the snapshot (s). */
        const val STRIDE = 8

        /** Speed (cm/s, along the puff's direction) of a puff of [speed] [age] s old, at point ([x], [y], [z]). */
        fun strength(px: Double, py: Double, pz: Double, dx: Double, dy: Double, dz: Double, speed: Double, age: Double, x: Double, y: Double, z: Double): Double {
            if (age < 0.0 || age > LIFE_S) return 0.0
            val decay = exp(-age / TAU_S)
            val travel = speed * TAU_S * (1.0 - decay)
            val cx = px + dx * travel; val cy = py + dy * travel; val cz = pz + dz * travel
            val r = R0_CM + 0.35 * travel
            val ex = x - cx; val ey = y - cy; val ez = z - cz
            val d2 = (ex * ex + ey * ey + ez * ez) / (r * r)
            if (d2 > 9.0) return 0.0
            val fade = if (age > LIFE_S - 1.0) LIFE_S - age else 1.0
            return speed * decay * exp(-d2) * fade
        }
    }
}
