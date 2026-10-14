package com.reeflive.core.geom

import com.reeflive.core.sim.TankDims
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Fluid grid resolution (cells along the tank length; the other axes follow from a cubic cell). */
enum class FluidQuality(val nx: Int, val iterations: Int) { LOW(32, 30), MEDIUM(48, 45), HIGH(64, 60) }

/**
 * A pump as a momentum source: real flow rate (US gph) through a round nozzle. Nozzle exit speed is
 * `v0 = Q / (π (d/2)²)`. On the coarse grid the jet is forced over the cells within one cell of
 * the nozzle (SIMULATION §9.7).
 */
data class Pump(
    val name: String,
    val pos: Vec3,
    val dir: Vec3,
    val gph: Double,
    val nozzleCm: Double,
    /** Mass source/sink pair (HOB: return jet + intake) instead of a pure momentum source. */
    val intake: Vec3? = null,
    val on: Boolean = true,
) {
    val axis: Vec3 = dir.normalized()
    /** Volumetric flow in cm³/s (1 US gal = 3785.41 cm³). */
    val flowCm3s: Double get() = gph * 3785.41 / 3600.0
    /** Nozzle exit speed in cm/s. */
    val exitSpeed: Double get() = flowCm3s / (PI * nozzleCm * nozzleCm / 4)

    companion object {
        /**
         * Tee's tank (photos): two small wavemaker-style powerheads and an HOB filter (est. ratings;
         * SIMULATION §9.7). Positions match FlowField.defaultJets / anchors.
         */
        fun defaults(): List<Pump> = listOf(
            Pump("PH-A", Vec3(77.0, 32.0, 1.5), Vec3(-0.6, -0.35, 0.72), gph = 850.0, nozzleCm = 3.2),
            Pump("PH-B", Vec3(1.5, 28.0, 13.0), Vec3(0.95, -0.2, 0.25), gph = 600.0, nozzleCm = 2.8),
            Pump("HOB", Vec3(73.0, 37.4, 2.0), Vec3(0.0, -0.4, 0.92), gph = 200.0, nozzleCm = 6.0, intake = Vec3(114.5, 19.0, 5.0)),
        )
    }
}

/**
 * Coarse 3D incompressible flow ("stable fluids", Stam 1999) on a MAC grid, cm and seconds.
 *
 * Per step:
 * 1. Pump forcing and thermal buoyancy.
 * 2. Vorticity confinement.
 * 3. Eddy viscosity.
 * 4. Semi-Lagrangian advection.
 * 5. Pressure projection (red–black Gauss–Seidel with SOR, warm-started).
 *
 * Rock cells (rock SDF < 0 at the cell centre) are solid. Glass, sand and the surface are
 * free-slip walls. The HOB is a source/sink pair, so the projection solves ∇·u = s.
 *
 * Scalar channels on cell centres, advected and diffused:
 * - Temperature anomaly (°C vs the tank mean, heater plume).
 * - A dose tracer (excess concentration in the dosed parameter's units).
 * - Food scent.
 * The tank-mean of each anomaly is removed every step, so the well-mixed value stays in WaterSim.
 *
 * Not thread-safe: step and sample from the simulation thread; the renderer gets [snapshotGrid] copies.
 */
class FluidSim(
    val lengthCm: Double = TankDims.LENGTH_CM,
    val depthCm: Double = TankDims.DEPTH_CM,
    val sandCm: Double = TankDims.SAND_TOP_CM,
    val waterCm: Double = TankDims.WATER_LEVEL_CM,
    quality: FluidQuality = FluidQuality.MEDIUM,
    var pumps: List<Pump> = Pump.defaults(),
    solidAt: ((Vec3) -> Boolean)? = null,
) {
    val nx = quality.nx
    val h = lengthCm / nx
    val ny = max(4, ceil((waterCm - sandCm) / h).toInt())
    val nz = max(4, ceil(depthCm / h).toInt())
    val iterations = quality.iterations
    private val n = nx * ny * nz

    // MAC faces
    private val u = FloatArray((nx + 1) * ny * nz); private val u0 = FloatArray(u.size)
    private val v = FloatArray(nx * (ny + 1) * nz); private val v0 = FloatArray(v.size)
    private val w = FloatArray(nx * ny * (nz + 1)); private val w0 = FloatArray(w.size)
    private val p = FloatArray(n)
    private val rhs = FloatArray(n)
    private val src = FloatArray(n)
    val solid = BooleanArray(n)
    val temp = FloatArray(n); private val tmp = FloatArray(n)
    val dose = FloatArray(n)
    val scent = FloatArray(n)

    /** Global pump strength (sandbox 0..2) and master switch; the HOB runs unless [hobOn] is false. */
    var strength = 1.0
    var powerheadsOn = true
    var hobOn = true
    var viscosity = 0.5          // eddy viscosity cm²/s (molecular 0.01 is far below grid scale)
    var diffusivity = 0.5        // scalar eddy diffusivity cm²/s
    var vorticityEps = 0.35
    /** Thermal expansion of seawater × g (cm/s² per °C): 2.6e-4 /K × 981. */
    var buoyancy = 0.255
    var stepCount = 0L; private set
    var time = 0.0; private set

    init {
        if (solidAt != null) for (k in 0 until nz) for (j in 0 until ny) for (i in 0 until nx) solid[idx(i, j, k)] = solidAt(center(i, j, k))
    }

    fun idx(i: Int, j: Int, k: Int) = i + nx * (j + ny * k)
    private fun iu(i: Int, j: Int, k: Int) = i + (nx + 1) * (j + ny * k)
    private fun iv(i: Int, j: Int, k: Int) = i + nx * (j + (ny + 1) * k)
    private fun iw(i: Int, j: Int, k: Int) = i + nx * (j + ny * k)
    fun center(i: Int, j: Int, k: Int) = Vec3((i + 0.5) * h, sandCm + (j + 0.5) * h, (k + 0.5) * h)
    private fun fluid(i: Int, j: Int, k: Int) = i in 0 until nx && j in 0 until ny && k in 0 until nz && !solid[idx(i, j, k)]

    // ================================================================== step
    fun step(dtIn: Double, heaterWatts: Double = 0.0, heaterPos: Vec3? = null) {
        val dt = dtIn.coerceIn(1e-4, 0.1)
        force(dt)
        thermal(dt, heaterWatts, heaterPos)
        confine(dt)
        diffuseVelocity(dt)
        advectVelocity(dt)
        project(dt)
        advectScalars(dt)
        stepCount++; time += dt
    }

    private fun cellOf(pos: Vec3): IntArray = intArrayOf(
        floor(pos.x / h).toInt().coerceIn(0, nx - 1),
        floor((pos.y - sandCm) / h).toInt().coerceIn(0, ny - 1),
        floor(pos.z / h).toInt().coerceIn(0, nz - 1),
    )

    /** Pumps: relax face velocities within ~1 cell of the nozzle toward the jet velocity. */
    private fun force(dt: Double) {
        java.util.Arrays.fill(src, 0f)
        val relax = 1 - exp(-dt * 12.0)
        for (pm in pumps) {
            if (!pm.on) continue
            val isHob = pm.intake != null
            if (isHob && !hobOn) continue
            if (!isHob && !powerheadsOn) continue
            val s = if (isHob) 1.0 else strength
            // jet resolved on the grid with the same momentum flux ρ v² A: v_cell = v0 √(A_nozzle / h²), capped at 100 cm/s
            val aNoz = PI * pm.nozzleCm * pm.nozzleCm / 4
            val vj = min(pm.exitSpeed * sqrt(aNoz / (h * h)), 100.0) * s
            val c0 = pm.pos + pm.axis * (0.75 * h)
            val target = pm.axis * vj
            val rad = 1.1 * h
            val (ci, cj, ck) = cellOf(c0).let { Triple(it[0], it[1], it[2]) }
            for (k in ck - 2..ck + 2) for (j in cj - 2..cj + 2) for (i in ci - 2..ci + 2) {
                if (!fluid(i, j, k)) continue
                val c = center(i, j, k)
                val d = c.distanceTo(c0)
                if (d > rad) continue
                val wgt = relax * (1 - d / rad)
                if (i > 0) u[iu(i, j, k)] += ((target.x - u[iu(i, j, k)]) * wgt).toFloat()
                if (j > 0) v[iv(i, j, k)] += ((target.y - v[iv(i, j, k)]) * wgt).toFloat()
                if (k > 0) w[iw(i, j, k)] += ((target.z - w[iw(i, j, k)]) * wgt).toFloat()
            }
            if (isHob) {
                // mass source at the return, equal sink at the intake (cm³/s per cell volume → 1/s)
                val q = pm.flowCm3s / (h * h * h)
                val a = cellOf(c0); val b = cellOf(pm.intake!!)
                if (!solid[idx(a[0], a[1], a[2])] && !solid[idx(b[0], b[1], b[2])]) {
                    src[idx(a[0], a[1], a[2])] += q.toFloat()
                    src[idx(b[0], b[1], b[2])] -= q.toFloat()
                }
            }
        }
    }

    /** Heater plume source and Boussinesq buoyancy on v faces. */
    private fun thermal(dt: Double, watts: Double, at: Vec3?) {
        if (watts > 0 && at != null) {
            // heater tube ~ 3 vertical cells; dT/dt = P / (ρ c V)
            val c = cellOf(at)
            val cells = (0..2).map { intArrayOf(c[0], (c[1] + it - 1).coerceIn(0, ny - 1), c[2]) }.filter { !solid[idx(it[0], it[1], it[2])] }
            if (cells.isNotEmpty()) {
                val dT = watts * dt / (1.0 * 4.186 * h * h * h * cells.size)   // ρ 1 g/cm³, c 4.186 J/(g·K)
                for (q in cells) temp[idx(q[0], q[1], q[2])] += dT.toFloat()
            }
        }
        if (buoyancy == 0.0) return
        for (k in 0 until nz) for (j in 1 until ny) for (i in 0 until nx) {
            if (solid[idx(i, j, k)] || solid[idx(i, j - 1, k)]) continue
            val t = 0.5 * (temp[idx(i, j, k)] + temp[idx(i, j - 1, k)])
            v[iv(i, j, k)] += (buoyancy * t * dt).toFloat()
        }
    }

    // ---------------------------------------------------------------- sampling
    private fun sampleGrid(f: FloatArray, sx: Int, sy: Int, sz: Int, gx: Double, gy: Double, gz: Double): Double {
        val x = gx.coerceIn(0.0, sx - 1.0001); val y = gy.coerceIn(0.0, sy - 1.0001); val z = gz.coerceIn(0.0, sz - 1.0001)
        val i = x.toInt(); val j = y.toInt(); val k = z.toInt()
        val tx = x - i; val ty = y - j; val tz = z - k
        fun at(a: Int, b: Int, c: Int) = f[a + sx * (b + sy * c)].toDouble()
        val c00 = at(i, j, k) * (1 - tx) + at(i + 1, j, k) * tx
        val c10 = at(i, j + 1, k) * (1 - tx) + at(i + 1, j + 1, k) * tx
        val c01 = at(i, j, k + 1) * (1 - tx) + at(i + 1, j, k + 1) * tx
        val c11 = at(i, j + 1, k + 1) * (1 - tx) + at(i + 1, j + 1, k + 1) * tx
        return (c00 * (1 - ty) + c10 * ty) * (1 - tz) + (c01 * (1 - ty) + c11 * ty) * tz
    }

    private fun su(f: FloatArray, x: Double, y: Double, z: Double) = sampleGrid(f, nx + 1, ny, nz, x / h, (y - sandCm) / h - 0.5, z / h - 0.5)
    private fun sv(f: FloatArray, x: Double, y: Double, z: Double) = sampleGrid(f, nx, ny + 1, nz, x / h - 0.5, (y - sandCm) / h, z / h - 0.5)
    private fun sw(f: FloatArray, x: Double, y: Double, z: Double) = sampleGrid(f, nx, ny, nz + 1, x / h - 0.5, (y - sandCm) / h - 0.5, z / h)
    private fun sc(f: FloatArray, x: Double, y: Double, z: Double) = sampleGrid(f, nx, ny, nz, x / h - 0.5, (y - sandCm) / h - 0.5, z / h - 0.5)

    /** Velocity (cm/s) at a world point (trilinear on the MAC faces); zero inside rock. */
    fun velocity(pos: Vec3): Vec3 {
        val c = cellOf(pos)
        if (solid[idx(c[0], c[1], c[2])]) return Vec3.ZERO
        return Vec3(su(u, pos.x, pos.y, pos.z), sv(v, pos.x, pos.y, pos.z), sw(w, pos.x, pos.y, pos.z))
    }

    fun tempAnomaly(pos: Vec3) = sc(temp, pos.x, pos.y, pos.z)
    fun doseExcess(pos: Vec3) = sc(dose, pos.x, pos.y, pos.z)
    fun scentAt(pos: Vec3) = sc(scent, pos.x, pos.y, pos.z)

    // ---------------------------------------------------------------- advection
    private fun advectVelocity(dt: Double) {
        u.copyInto(u0); v.copyInto(v0); w.copyInto(w0)
        for (k in 0 until nz) for (j in 0 until ny) for (i in 1 until nx) {
            if (solid[idx(i, j, k)] || solid[idx(i - 1, j, k)]) { u[iu(i, j, k)] = 0f; continue }
            val x = i * h; val y = sandCm + (j + 0.5) * h; val z = (k + 0.5) * h
            val bx = x - dt * su(u0, x, y, z); val by = y - dt * sv(v0, x, y, z); val bz = z - dt * sw(w0, x, y, z)
            u[iu(i, j, k)] = su(u0, bx, by, bz).toFloat()
        }
        for (k in 0 until nz) for (j in 1 until ny) for (i in 0 until nx) {
            if (solid[idx(i, j, k)] || solid[idx(i, j - 1, k)]) { v[iv(i, j, k)] = 0f; continue }
            val x = (i + 0.5) * h; val y = sandCm + j * h; val z = (k + 0.5) * h
            val bx = x - dt * su(u0, x, y, z); val by = y - dt * sv(v0, x, y, z); val bz = z - dt * sw(w0, x, y, z)
            v[iv(i, j, k)] = sv(v0, bx, by, bz).toFloat()
        }
        for (k in 1 until nz) for (j in 0 until ny) for (i in 0 until nx) {
            if (solid[idx(i, j, k)] || solid[idx(i, j, k - 1)]) { w[iw(i, j, k)] = 0f; continue }
            val x = (i + 0.5) * h; val y = sandCm + (j + 0.5) * h; val z = k * h
            val bx = x - dt * su(u0, x, y, z); val by = y - dt * sv(v0, x, y, z); val bz = z - dt * sw(w0, x, y, z)
            w[iw(i, j, k)] = sw(w0, bx, by, bz).toFloat()
        }
    }

    private fun advectScalars(dt: Double) {
        // food scent decays (τ 2 min); temperature and dose anomalies relax toward the mean slowly (τ 30 min)
        val decayScent = exp(-dt / 120.0).toFloat()
        val relaxMix = exp(-dt / 1800.0).toFloat()
        for (f in arrayOf(temp, dose, scent)) {
            f.copyInto(tmp)
            var before = 0.0; var after = 0.0; var count = 0
            for (k in 0 until nz) for (j in 0 until ny) for (i in 0 until nx) {
                val id = idx(i, j, k)
                if (solid[id]) { f[id] = 0f; continue }
                before += tmp[id]; count++
                val c = center(i, j, k)
                val vel = velocity(c)
                val back = c - vel * dt
                var value = sc(tmp, back.x, back.y, back.z)
                // explicit diffusion (stable: κ dt / h² ≪ 1/6)
                val lap = lap(tmp, i, j, k)
                value += diffusivity * dt / (h * h) * lap
                f[id] = value.toFloat()
                after += value
            }
            // semi-Lagrangian advection is not conservative: restore the total, then remove the mean (mixed part lives in WaterSim)
            if (count > 0) {
                val corr = ((before - after) / count).toFloat()
                val mean = (before / count).toFloat()
                val isScent = f === scent
                for (id in 0 until n) if (!solid[id]) f[id] = if (isScent) maxOf(0f, f[id] + corr) else f[id] + corr - mean
            }
            val k = if (f === scent) decayScent else relaxMix
            for (id in 0 until n) f[id] *= k
        }
    }

    private fun lap(f: FloatArray, i: Int, j: Int, k: Int): Double {
        val c = f[idx(i, j, k)].toDouble()
        var s = 0.0
        fun nb(a: Int, b: Int, d: Int) { s += if (fluid(a, b, d)) f[idx(a, b, d)] - c else 0.0 }
        nb(i + 1, j, k); nb(i - 1, j, k); nb(i, j + 1, k); nb(i, j - 1, k); nb(i, j, k + 1); nb(i, j, k - 1)
        return s
    }

    private fun diffuseVelocity(dt: Double) {
        val a = (viscosity * dt / (h * h)).toFloat()
        if (a <= 0f) return
        fun smooth(f: FloatArray, g: FloatArray, sx: Int, sy: Int, sz: Int) {
            f.copyInto(g)
            for (k in 1 until sz - 1) for (j in 1 until sy - 1) for (i in 1 until sx - 1) {
                val id = i + sx * (j + sy * k)
                val l = g[id - 1] + g[id + 1] + g[id - sx] + g[id + sx] + g[id - sx * sy] + g[id + sx * sy] - 6 * g[id]
                f[id] = g[id] + a * l
            }
        }
        smooth(u, u0, nx + 1, ny, nz); smooth(v, v0, nx, ny + 1, nz); smooth(w, w0, nx, ny, nz + 1)
    }

    /** Vorticity confinement (Fedkiw et al. 2001): re-injects small-scale swirl lost to numerical diffusion. */
    private fun confine(dt: Double) {
        if (vorticityEps <= 0) return
        val ox = FloatArray(n); val oy = FloatArray(n); val oz = FloatArray(n); val mag = FloatArray(n)
        fun cu(i: Int, j: Int, k: Int) = 0.5 * (u[iu(i, j, k)] + u[iu(i + 1, j, k)])
        fun cv(i: Int, j: Int, k: Int) = 0.5 * (v[iv(i, j, k)] + v[iv(i, j + 1, k)])
        fun cw(i: Int, j: Int, k: Int) = 0.5 * (w[iw(i, j, k)] + w[iw(i, j, k + 1)])
        for (k in 1 until nz - 1) for (j in 1 until ny - 1) for (i in 1 until nx - 1) {
            val id = idx(i, j, k)
            if (solid[id]) continue
            val wx = (cw(i, j + 1, k) - cw(i, j - 1, k) - cv(i, j, k + 1) + cv(i, j, k - 1)) / (2 * h)
            val wy = (cu(i, j, k + 1) - cu(i, j, k - 1) - cw(i + 1, j, k) + cw(i - 1, j, k)) / (2 * h)
            val wz = (cv(i + 1, j, k) - cv(i - 1, j, k) - cu(i, j + 1, k) + cu(i, j - 1, k)) / (2 * h)
            ox[id] = wx.toFloat(); oy[id] = wy.toFloat(); oz[id] = wz.toFloat()
            mag[id] = sqrt(wx * wx + wy * wy + wz * wz).toFloat()
        }
        val e = vorticityEps * h * dt
        for (k in 2 until nz - 2) for (j in 2 until ny - 2) for (i in 2 until nx - 2) {
            val id = idx(i, j, k)
            if (solid[id]) continue
            var gx = (mag[idx(i + 1, j, k)] - mag[idx(i - 1, j, k)]) / (2 * h)
            var gy = (mag[idx(i, j + 1, k)] - mag[idx(i, j - 1, k)]) / (2 * h)
            var gz = (mag[idx(i, j, k + 1)] - mag[idx(i, j, k - 1)]) / (2 * h)
            val gl = sqrt(gx * gx + gy * gy + gz * gz) + 1e-6
            gx /= gl; gy /= gl; gz /= gl
            val fx = (gy * oz[id] - gz * oy[id]) * e
            val fy = (gz * ox[id] - gx * oz[id]) * e
            val fz = (gx * oy[id] - gy * ox[id]) * e
            u[iu(i, j, k)] += (0.5 * fx).toFloat(); u[iu(i + 1, j, k)] += (0.5 * fx).toFloat()
            v[iv(i, j, k)] += (0.5 * fy).toFloat(); v[iv(i, j + 1, k)] += (0.5 * fy).toFloat()
            w[iw(i, j, k)] += (0.5 * fz).toFloat(); w[iw(i, j, k + 1)] += (0.5 * fz).toFloat()
        }
    }

    // ---------------------------------------------------------------- projection
    private fun enforceWalls() {
        for (k in 0 until nz) for (j in 0 until ny) { u[iu(0, j, k)] = 0f; u[iu(nx, j, k)] = 0f }
        for (k in 0 until nz) for (i in 0 until nx) { v[iv(i, 0, k)] = 0f; v[iv(i, ny, k)] = 0f }
        for (j in 0 until ny) for (i in 0 until nx) { w[iw(i, j, 0)] = 0f; w[iw(i, j, nz)] = 0f }
        for (k in 0 until nz) for (j in 0 until ny) for (i in 0 until nx) if (solid[idx(i, j, k)]) {
            u[iu(i, j, k)] = 0f; u[iu(i + 1, j, k)] = 0f; v[iv(i, j, k)] = 0f; v[iv(i, j + 1, k)] = 0f; w[iw(i, j, k)] = 0f; w[iw(i, j, k + 1)] = 0f
        }
    }

    /** Discrete divergence of cell (i,j,k) in 1/s. */
    fun divergence(i: Int, j: Int, k: Int): Double =
        ((u[iu(i + 1, j, k)] - u[iu(i, j, k)]) + (v[iv(i, j + 1, k)] - v[iv(i, j, k)]) + (w[iw(i, j, k + 1)] - w[iw(i, j, k)])) / h

    fun source(i: Int, j: Int, k: Int): Double = src[idx(i, j, k)].toDouble()

    private fun project(dt: Double) {
        enforceWalls()
        // ∇²p = (∇·u − s) / dt·… we fold dt into p (p' = p·dt/ρ): u -= ∇p'
        for (k in 0 until nz) for (j in 0 until ny) for (i in 0 until nx) {
            val id = idx(i, j, k)
            rhs[id] = if (solid[id]) 0f else ((divergence(i, j, k) - src[id]) * h * h).toFloat()
        }
        val omega = 1.75f
        repeat(iterations) {
            for (color in 0..1) for (k in 0 until nz) for (j in 0 until ny) {
                var i = (j + k + color) and 1
                while (i < nx) {
                    val id = idx(i, j, k)
                    if (!solid[id]) {
                        var sum = 0f; var cnt = 0
                        if (i + 1 < nx && !solid[id + 1]) { sum += p[id + 1]; cnt++ }
                        if (i > 0 && !solid[id - 1]) { sum += p[id - 1]; cnt++ }
                        if (j + 1 < ny && !solid[id + nx]) { sum += p[id + nx]; cnt++ }
                        if (j > 0 && !solid[id - nx]) { sum += p[id - nx]; cnt++ }
                        if (k + 1 < nz && !solid[id + nx * ny]) { sum += p[id + nx * ny]; cnt++ }
                        if (k > 0 && !solid[id - nx * ny]) { sum += p[id - nx * ny]; cnt++ }
                        if (cnt > 0) {
                            val gs = (sum - rhs[id]) / cnt
                            p[id] += omega * (gs - p[id])
                        }
                    }
                    i += 2
                }
            }
        }
        // pressure has a free constant: keep its mean at 0 so warm starts don't drift
        var mean = 0.0; var c = 0
        for (id in 0 until n) if (!solid[id]) { mean += p[id]; c++ }
        if (c > 0) { val m = (mean / c).toFloat(); for (id in 0 until n) if (!solid[id]) p[id] -= m }
        for (k in 0 until nz) for (j in 0 until ny) for (i in 1 until nx)
            if (!solid[idx(i, j, k)] && !solid[idx(i - 1, j, k)]) u[iu(i, j, k)] -= (p[idx(i, j, k)] - p[idx(i - 1, j, k)]) / h.toFloat()
        for (k in 0 until nz) for (j in 1 until ny) for (i in 0 until nx)
            if (!solid[idx(i, j, k)] && !solid[idx(i, j - 1, k)]) v[iv(i, j, k)] -= (p[idx(i, j, k)] - p[idx(i, j - 1, k)]) / h.toFloat()
        for (k in 1 until nz) for (j in 0 until ny) for (i in 0 until nx)
            if (!solid[idx(i, j, k)] && !solid[idx(i, j, k - 1)]) w[iw(i, j, k)] -= (p[idx(i, j, k)] - p[idx(i, j, k - 1)]) / h.toFloat()
        enforceWalls()
    }

    // ---------------------------------------------------------------- inputs / outputs
    /** Inject a dose (excess concentration × tank volume spread into the cells around [at]). */
    fun injectDose(at: Vec3, excessTimesTankL: Double, tankL: Double) {
        val c = cellOf(at)
        val cellL = h * h * h / 1000.0
        val cells = ArrayList<Int>()
        for (k in c[2] - 1..c[2] + 1) for (j in c[1] - 1..c[1] + 1) for (i in c[0] - 1..c[0] + 1) if (fluid(i, j, k)) cells += idx(i, j, k)
        if (cells.isEmpty()) return
        val per = (excessTimesTankL * tankL / (cellL * cells.size)).toFloat()
        // remove the uniform share so the field stays mean-free (the tank mean already includes the dose)
        val share = (excessTimesTankL * tankL / (cellL * fluidCount())).toFloat()
        for (id in 0 until n) if (!solid[id]) dose[id] -= share
        for (id in cells) dose[id] += per
    }

    fun addScent(at: Vec3, amount: Double) { val c = cellOf(at); val id = idx(c[0], c[1], c[2]); if (!solid[id]) scent[id] += amount.toFloat() }

    private var fluidCells = -1
    fun fluidCount(): Int { if (fluidCells < 0) fluidCells = solid.count { !it }; return fluidCells }

    fun maxSpeed(): Double {
        var m = 0.0
        for (k in 0 until nz) for (j in 0 until ny) for (i in 0 until nx) if (!solid[idx(i, j, k)]) {
            val c = center(i, j, k); m = max(m, velocity(c).length())
        }
        return m
    }

    /** Cell-centred velocity copy for the renderer (nx·ny·nz·3 floats, x fastest), plus dims. */
    fun snapshotGrid(): FlowGrid {
        val out = FloatArray(n * 3)
        for (k in 0 until nz) for (j in 0 until ny) for (i in 0 until nx) {
            val id = idx(i, j, k)
            if (solid[id]) continue
            out[id * 3] = 0.5f * (u[iu(i, j, k)] + u[iu(i + 1, j, k)])
            out[id * 3 + 1] = 0.5f * (v[iv(i, j, k)] + v[iv(i, j + 1, k)])
            out[id * 3 + 2] = 0.5f * (w[iw(i, j, k)] + w[iw(i, j, k + 1)])
        }
        return FlowGrid(nx, ny, nz, h.toFloat(), sandCm.toFloat(), out)
    }

    /** Mean |∇·u − s| over fluid cells relative to (max speed / h): 0 = exactly incompressible. */
    fun relativeDivergence(): Double {
        var sum = 0.0; var c = 0
        for (k in 0 until nz) for (j in 0 until ny) for (i in 0 until nx) if (!solid[idx(i, j, k)]) {
            sum += abs(divergence(i, j, k) - src[idx(i, j, k)]); c++
        }
        val ms = maxSpeed()
        return if (ms < 1e-9 || c == 0) 0.0 else (sum / c) / (ms / h)
    }
}

/** Renderer-side copy of the flow (cell centres, cm/s). */
class FlowGrid(val nx: Int, val ny: Int, val nz: Int, val cell: Float, val yOrigin: Float, val vel: FloatArray)
