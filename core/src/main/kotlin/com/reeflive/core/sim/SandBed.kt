package com.reeflive.core.sim

import com.reeflive.core.geom.Rng
import com.reeflive.core.geom.Vec3
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Grain settling velocity in still seawater (SIMULATION §16.3). Ferguson & Church (2004), "A simple universal equation
 * for grain settling velocity", J. Sediment. Res. 74: 933–937:
 * `w = R g D² / (C1 ν + sqrt(0.75 C2 R g D³))` with C1 = 18 and C2 = 1.0 for natural (sub-angular) grains; it reduces
 * to Stokes' law for silt and to a drag law for coarse sand.
 */
object Settling {
    /** Aragonite (CaCO₃) grain density, g/cm³ (mineral data 2.93–2.95; biogenic grains with pores settle a little slower). */
    @Ev(Evidence.SOURCED, "aragonite mineral density 2.93-2.95 g/cm3") const val ARAGONITE_DENSITY = 2.93
    /** Seawater density at 25 °C, 35 PSU (g/cm³). */
    const val SEAWATER_DENSITY = 1.023
    /** Kinematic viscosity of seawater at 25 °C, 35 PSU (m²/s). */
    const val SEAWATER_NU = 0.95e-6
    const val C1 = 18.0
    const val C2_NATURAL = 1.0

    /** Terminal settling velocity in cm/s of a grain of diameter [dMm] (mm) and density [densityGcm3]. */
    fun velocityCmS(dMm: Double, densityGcm3: Double, c2: Double = C2_NATURAL, nu: Double = SEAWATER_NU): Double {
        val d = dMm / 1000.0
        val r = (densityGcm3 - SEAWATER_DENSITY) / SEAWATER_DENSITY
        if (r <= 0 || d <= 0) return 0.0
        val g = 9.81
        return 100.0 * r * g * d * d / (C1 * nu + sqrt(0.75 * c2 * r * g * d * d * d))
    }
}

/**
 * What a stirred-up cloud is made of. Reef aquarium aragonite sands are sold at 0.25–1.0 mm (oolite), 0.5–1.5 mm
 * (Fiji Pink) and 1–2 mm (Special Grade) (CaribSea Arag-Alive grade sheet); a lived-in bed also holds carbonate silt
 * from bioerosion and detritus (mulm) flocs. Settling speeds follow [Settling.velocityCmS]; detritus flocs fall at the
 * low end of marine-snow rates (Alldredge & Gotschalk 1988, 16–368 m/day ≈ 0.02–0.4 cm/s).
 */
enum class Grain(val label: String, val diameterMm: Double, val densityGcm3: Double) {
    /** Medium aragonite sand (0.5 mm): ≈ 8 cm/s, back on the bed within a second or two. */
    SAND("Sand", 0.5, Settling.ARAGONITE_DENSITY),
    /** Fine sand (0.2 mm): ≈ 2.7 cm/s. */
    FINE("Fine sand", 0.2, Settling.ARAGONITE_DENSITY),
    /** Carbonate silt (63 µm): ≈ 0.35 cm/s, the haze that hangs for tens of seconds. */
    SILT("Silt", 0.063, Settling.ARAGONITE_DENSITY),
    /** Detritus floc (0.3 mm, 1.10 g/cm³): ≈ 0.3 cm/s; strong flow keeps it up for the filter sock. */
    DETRITUS("Detritus", 0.3, 1.10);

    val settlingCmS: Double = Settling.velocityCmS(diameterMm, densityGcm3)
}

/** Read-only copy of the sand grid for the renderer (x fastest; heights in cm relative to the modelled sand surface). */
class SandGridSnapshot(
    val nx: Int,
    val nz: Int,
    val cellXCm: Float,
    val cellZCm: Float,
    /** Height offset (cm, + mound / − pit) per cell. */
    val height: FloatArray,
    /** Freshly turned (0..1): darker, damp-looking sand that fades back to the surface colour over hours. */
    val fresh: FloatArray,
    /** Detritus (mulm) load index (0 clean .. ~1.5 dirty). */
    val detritus: FloatArray,
    val version: Long,
)

/**
 * The dynamic sand bed (SIMULATION §16): a coarse [NX] × [NZ] grid over the tank floor (≈ 1.9 × 1.4 cm cells) with a
 * height offset, a "freshly turned" freshness and a detritus load per cell, plus a small pool of suspended sand and
 * detritus particles ([MAX_PARTICLES]) that settle at their grain's settling velocity.
 *
 * Animals change it through [dig], [deposit], [plough], [freshen], [sift] and [puff] ([SandWork] for the crawling
 * inverts, the goby brains for the fish). [relax] runs once per chemistry tick: slope failure at the angle of repose,
 * slow smoothing that grows with pump strength, freshness fading and detritus settling from the water column.
 * Pure Kotlin, own random stream (never draws from the behaviour RNG).
 */
class SandBed(val lengthCm: Double, val widthCm: Double, val sandTopCm: Double, seed: Long) {
    val nx = NX
    val nz = NZ
    val cellX = lengthCm / nx
    val cellZ = widthCm / nz
    val cellArea = cellX * cellZ
    val height = FloatArray(nx * nz)
    val fresh = FloatArray(nx * nz)
    val detritus = FloatArray(nx * nz) { 0.25f }
    /** Bumped on every change; the snapshot copies the grid only when it moved on. */
    var version = 0L; private set

    // where the bed rose since the last [takeRaised] (x0, z0, x1, z1; empty when x0 > x1): a body resolved against the
    // old surface earlier in the step may now be under the new one
    private var rx0 = Double.POSITIVE_INFINITY; private var rz0 = Double.POSITIVE_INFINITY
    private var rx1 = Double.NEGATIVE_INFINITY; private var rz1 = Double.NEGATIVE_INFINITY
    private fun raised(x0: Double, z0: Double, x1: Double, z1: Double) {
        rx0 = min(rx0, x0); rz0 = min(rz0, z0); rx1 = max(rx1, x1); rz1 = max(rz1, z1)
    }

    /** The rectangle (x0, z0, x1, z1) where the sand rose since the last call, or null; clears it. */
    fun takeRaised(): DoubleArray? {
        if (rx0 > rx1) return null
        return doubleArrayOf(rx0, rz0, rx1, rz1).also { rx0 = Double.POSITIVE_INFINITY; rz0 = Double.POSITIVE_INFINITY; rx1 = Double.NEGATIVE_INFINITY; rz1 = Double.NEGATIVE_INFINITY }
    }
    private val rng = Rng(seed xor 0x5A4DL)

    // ---- counters (tests, HUD; no SimEvents) ----
    /** Sand volume moved by digging and mouthfuls (cm³). */
    var movedCm3 = 0.0; private set
    var puffs = 0L; private set
    /** Detritus load (index × cm²) removed from the bed by sifting. */
    var siftedLoad = 0.0; private set
    /** Times a cloud settled on a low coral (a sand sifter's mouthful landing on a frag). */
    var coralDustings = 0L
    /** Detritus particles that stayed suspended past [MAX_AGE_S] (go to the filter); drained by the chemistry tick. */
    var resuspendedPending = 0.0

    // ---- suspended particles (structure of arrays) ----
    private val px = FloatArray(MAX_PARTICLES)
    private val py = FloatArray(MAX_PARTICLES)
    private val pz = FloatArray(MAX_PARTICLES)
    private val pvy = FloatArray(MAX_PARTICLES)
    private val phx = FloatArray(MAX_PARTICLES)
    private val phz = FloatArray(MAX_PARTICLES)
    private val pfx = FloatArray(MAX_PARTICLES)
    private val pfz = FloatArray(MAX_PARTICLES)
    /** Eddy diffusivity at the particle (cm²/s), refreshed with the flow sample. */
    private val pkz = FloatArray(MAX_PARTICLES)
    private val page = FloatArray(MAX_PARTICLES)
    private val pkind = ByteArray(MAX_PARTICLES)
    var particleCount = 0; private set
    private var stepCount = 0L

    // ============================================================== geometry
    /** Modelled sand surface before any digging (cm): 3.5 at the front glass → 6.0 at the back (tools/blender/rockscape.sand_base). */
    fun baseY(z: Double): Double = BASE_FRONT + (BASE_BACK - BASE_FRONT) * ((widthCm - z) / widthCm).coerceIn(0.0, 1.0)

    fun ix(x: Double) = (x / cellX).toInt().coerceIn(0, nx - 1)
    fun iz(z: Double) = (z / cellZ).toInt().coerceIn(0, nz - 1)
    fun index(x: Double, z: Double) = iz(z) * nx + ix(x)

    /** Height offset at (x, z), bilinear between cell centres. */
    fun heightAt(x: Double, z: Double): Double {
        val fx = (x / cellX - 0.5).coerceIn(0.0, nx - 1.001)
        val fz = (z / cellZ - 0.5).coerceIn(0.0, nz - 1.001)
        val i = fx.toInt(); val k = fz.toInt()
        val tx = fx - i; val tz = fz - k
        val a = height[k * nx + i]; val b = height[k * nx + i + 1]
        val c = height[(k + 1) * nx + i]; val d = height[(k + 1) * nx + i + 1]
        return (a + (b - a) * tx) * (1 - tz) + (c + (d - c) * tx) * tz
    }

    /**
     * The modelled (undisturbed) sand surface: the drawn sand mesh rasterized ([com.reeflive.core.sim.RockData.sandBase],
     * set by the app from tank.glb) when there is one, else the linear front-to-back slope [baseY].
     */
    var base: com.reeflive.core.geom.HeightField? = null

    fun modelY(x: Double, z: Double): Double = base?.at(x, z) ?: baseY(z)

    /** Visible sand surface (cm) at (x, z): the modelled bed plus what the animals did to it (static.vert lifts the mesh the same way). */
    fun surfaceY(x: Double, z: Double): Double = modelY(x, z) + heightAt(x, z)

    fun freshAt(x: Double, z: Double): Double = fresh[index(x, z)].toDouble()
    fun detritusAt(x: Double, z: Double): Double = detritus[index(x, z)].toDouble()
    fun cellHeight(x: Double, z: Double): Double = height[index(x, z)].toDouble()

    /** Calls [f] for every cell within [r] cm of (x, z) with weight 1 − (d/r)² (> 0). */
    private inline fun around(x: Double, z: Double, r: Double, f: (i: Int, w: Double) -> Unit) {
        val rr = max(r, 0.5 * min(cellX, cellZ))
        val i0 = ((x - rr) / cellX).toInt().coerceIn(0, nx - 1); val i1 = ((x + rr) / cellX).toInt().coerceIn(0, nx - 1)
        val k0 = ((z - rr) / cellZ).toInt().coerceIn(0, nz - 1); val k1 = ((z + rr) / cellZ).toInt().coerceIn(0, nz - 1)
        var any = false
        for (k in k0..k1) for (i in i0..i1) {
            val dx = (i + 0.5) * cellX - x; val dz = (k + 0.5) * cellZ - z
            val q = (dx * dx + dz * dz) / (rr * rr)
            if (q >= 1.0) continue
            any = true
            f(k * nx + i, 1 - q)
        }
        if (!any) f(index(x, z), 1.0)
    }

    private fun weightSum(x: Double, z: Double, r: Double): Double { var s = 0.0; around(x, z, r) { _, w -> s += w }; return s }

    // ============================================================== animal edits
    /** Removes [cm3] of sand around (x, z) within [r] cm (a pit); the cells are freshly turned. */
    fun dig(x: Double, z: Double, cm3: Double, r: Double = 1.5) = move(x, z, -cm3, r)

    /** Adds [cm3] of sand around (x, z) within [r] cm (a mound); freshly turned. */
    fun deposit(x: Double, z: Double, cm3: Double, r: Double = 2.0) = move(x, z, cm3, r)

    private fun move(x: Double, z: Double, cm3: Double, r: Double) {
        if (cm3 == 0.0) return
        val ws = weightSum(x, z, r).coerceAtLeast(1e-6)
        around(x, z, r) { i, w ->
            height[i] = (height[i] + (cm3 * w / ws / cellArea).toFloat()).coerceIn(-MAX_DEPTH, MAX_MOUND)
            fresh[i] = max(fresh[i], (0.6 + 0.4 * w).toFloat())
        }
        movedCm3 += abs(cm3)
        if (cm3 > 0) raised(x - r - cellX, z - r - cellZ, x + r + cellX, z + r + cellZ)
        version++
    }

    /**
     * A furrow along unit direction ([dx], [dz]) at (x, z): the centre line sinks by [depth0] and the spoil is pushed to
     * both sides (a Mexican-hat profile across the trail, mass-conserving). Nassarius trails, brittle-star arm drags.
     */
    fun plough(x: Double, z: Double, dx: Double, dz: Double, depth0: Double, width0: Double, freshness: Double = 0.8) {
        val l = sqrt(dx * dx + dz * dz)
        val ux = if (l > 1e-6) dx / l else 1.0; val uz = if (l > 1e-6) dz / l else 0.0
        // a groove narrower than half a cell would fall between the cell centres; widen it and keep its volume
        val width = max(width0, 0.5 * max(cellX, cellZ))
        val depth = depth0 * width0 / width
        around(x, z, 2.5 * width) { i, w ->
            val cx = (i % nx + 0.5) * cellX - x; val cz = (i / nx + 0.5) * cellZ - z
            val across = (-uz * cx + ux * cz) / width
            val s2 = across * across
            val dh = -depth * (1 - 2 * s2) * exp(-s2) * sqrt(w)
            height[i] = (height[i] + dh.toFloat()).coerceIn(-MAX_DEPTH, MAX_MOUND)
            if (s2 < 2.5) fresh[i] = max(fresh[i], (freshness * (1 - s2 / 2.5)).toFloat())
        }
        raised(x - 2.5 * width - cellX, z - 2.5 * width - cellZ, x + 2.5 * width + cellX, z + 2.5 * width + cellZ)
        version++
    }

    /** Turns the top layer without moving it (claws, legs scratching): freshness only. */
    fun freshen(x: Double, z: Double, r: Double, amount: Double) {
        around(x, z, r) { i, w -> fresh[i] = max(fresh[i], (amount * w).toFloat()) }
        version++
    }

    /** Sand dives (sand-sleeping wrasses going in) and emergences counted since the start (tests, captions). */
    var dives = 0L; private set
    var emergences = 0L; private set

    /**
     * A sand-diving wrasse goes in head-first at (x, z), travelling along ([hx], [hz]) (research video fish_leopard_wrasse:
     * plunge, then body waves until covered, 1–3 s, Halichoeres proxy). The bed shows a shallow crater over the fish with
     * the displaced sand thrown up as a low rim ahead and to the sides; volume is conserved (pit = rim). [bodyCm] is the
     * fish length; the crater volume scales with it ([DIVE_CM3_PER_CM]).
     */
    fun sandDive(x: Double, z: Double, hx: Double, hz: Double, bodyCm: Double) {
        val l = sqrt(hx * hx + hz * hz)
        val ux = if (l > 1e-6) hx / l else 1.0; val uz = if (l > 1e-6) hz / l else 0.0
        val vol = DIVE_CM3_PER_CM * bodyCm
        val r = max(1.0, 0.18 * bodyCm)
        dig(x, z, vol, r)
        // rim: half thrown ahead of the dive, the rest to both sides
        deposit(x + ux * 1.6 * r, z + uz * 1.6 * r, vol * 0.5, r * 1.1)
        deposit(x - uz * 1.4 * r, z + ux * 1.4 * r, vol * 0.25, r)
        deposit(x + uz * 1.4 * r, z - ux * 1.4 * r, vol * 0.25, r)
        freshen(x, z, 2.2 * r, 1.0)
        dives++
    }

    /** The buried fish pushes out at (x, z): a small pit where the head breaks the surface, the sand shrugged aside. */
    fun sandEmerge(x: Double, z: Double, bodyCm: Double) {
        val vol = 0.4 * DIVE_CM3_PER_CM * bodyCm
        val r = max(0.8, 0.12 * bodyCm)
        dig(x, z, vol, r)
        deposit(x + 1.5 * r, z, vol * 0.5, r)
        deposit(x - 1.5 * r, z, vol * 0.5, r)
        emergences++
    }

    /**
     * A sifter works the sand around (x, z): removes [fraction] of the cells' detritus and returns the load taken
     * (index × cm²). The rest of the mouthful's fines go up in the [puff].
     */
    fun sift(x: Double, z: Double, r: Double, fraction: Double): Double {
        var taken = 0.0
        around(x, z, r) { i, w ->
            val d = detritus[i] * (fraction * w).coerceIn(0.0, 1.0)
            detritus[i] -= d.toFloat()
            taken += d * cellArea
        }
        siftedLoad += taken
        version++
        return taken
    }

    /**
     * A puff of sand: [n] particles from (x, y, z) blown up at [upCmS] (jet speed) with a horizontal push along
     * ([hx], [hz]). Each sand particle stands for [GRAIN_CM3] of sand taken from the cell under it, so a cloud moves sand
     * downstream when it settles. The share of detritus follows the cell's load.
     */
    fun puff(x: Double, y: Double, z: Double, n: Int, upCmS: Double, hx: Double = 0.0, hz: Double = 0.0, finesShare: Double = 0.35) {
        if (n <= 0) return
        puffs++
        val i0 = index(x, z)
        val dShare = (detritus[i0] / 1.5f).coerceIn(0f, 0.6f).toDouble()
        for (k in 0 until n) {
            val slot = if (particleCount < MAX_PARTICLES) particleCount++ else ((stepCount + k) % MAX_PARTICLES).toInt()
            val u = rng.nextDouble()
            val kind = when {
                u < dShare * 0.6 -> Grain.DETRITUS
                u < dShare * 0.6 + finesShare * 0.45 -> Grain.SILT
                u < dShare * 0.6 + finesShare -> Grain.FINE
                else -> Grain.SAND
            }
            px[slot] = (x + rng.signed() * 0.6).toFloat(); py[slot] = (y + rng.nextDouble() * 0.4).toFloat(); pz[slot] = (z + rng.signed() * 0.6).toFloat()
            pvy[slot] = (upCmS * (0.5 + 0.5 * rng.nextDouble())).toFloat()
            phx[slot] = (hx + rng.signed() * 0.4 * upCmS).toFloat(); phz[slot] = (hz + rng.signed() * 0.4 * upCmS).toFloat()
            pfx[slot] = 0f; pfz[slot] = 0f; pkz[slot] = 0f
            page[slot] = 0f
            pkind[slot] = kind.ordinal.toByte()
            if (kind == Grain.DETRITUS) detritus[i0] = max(0f, detritus[i0] - DETRITUS_PER_PARTICLE.toFloat())
            else if (kind != Grain.SILT) height[i0] = (height[i0] - (GRAIN_CM3 / cellArea).toFloat()).coerceIn(-MAX_DEPTH, MAX_MOUND)
        }
        version++
    }

    // ============================================================== clouds
    /**
     * Moves the suspended particles by [dt] sim s. The jet that threw a particle up dies away over [JET_TAU_S]; then it
     * falls at its grain's settling velocity, drifts with the local flow ([flowAt], sampled every 8th step) and is
     * kicked up and down by turbulence that grows with the flow speed. Particles that reach the bed settle into it;
     * detritus still up after [MAX_AGE_S] has reached the filter ([resuspendedPending]).
     */
    fun stepClouds(dt: Double, flowAt: ((Double, Double, Double) -> Vec3)?) {
        stepCount++
        if (particleCount == 0) return
        var i = 0
        val kJet = min(1.0, dt / JET_TAU_S)
        val kDrift = min(1.0, dt / DRIFT_TAU_S)
        while (i < particleCount) {
            val kind = Grain.entries[pkind[i].toInt()]
            val ws = kind.settlingCmS
            if (flowAt != null && (i + stepCount) % 8 == 0L) {
                val v = flowAt(px[i].toDouble(), py[i].toDouble(), pz[i].toDouble())
                pfx[i] = v.x.toFloat(); pfz[i] = v.z.toFloat()
                pkz[i] = (TURB_K * sqrt(v.x * v.x + v.y * v.y + v.z * v.z)).toFloat()
            }
            // turbulence (eddy diffusivity ∝ local speed) keeps the slow fines up; coarse sand outruns it
            if (pkz[i] > 0f) py[i] = (py[i] + rng.gaussian() * sqrt(2.0 * pkz[i] * dt)).toFloat()
            pvy[i] += ((-ws - pvy[i]) * kJet).toFloat()
            phx[i] += ((pfx[i] * FLOW_COUPLING - phx[i]) * kDrift).toFloat()
            phz[i] += ((pfz[i] * FLOW_COUPLING - phz[i]) * kDrift).toFloat()
            px[i] = (px[i] + phx[i] * dt).toFloat().coerceIn(0.3f, (lengthCm - 0.3).toFloat())
            pz[i] = (pz[i] + phz[i] * dt).toFloat().coerceIn(0.3f, (widthCm - 0.3).toFloat())
            py[i] = (py[i] + pvy[i] * dt).toFloat()
            page[i] = (page[i] + dt).toFloat()
            val ground = surfaceY(px[i].toDouble(), pz[i].toDouble())
            var remove = false
            if (py[i] <= ground) {
                val c = index(px[i].toDouble(), pz[i].toDouble())
                when (kind) {
                    Grain.DETRITUS -> detritus[c] = min(MAX_DETRITUS, detritus[c] + DETRITUS_PER_PARTICLE.toFloat())
                    Grain.SILT -> {}
                    else -> {
                        height[c] = (height[c] + (GRAIN_CM3 / cellArea).toFloat()).coerceIn(-MAX_DEPTH, MAX_MOUND)
                        raised(px[i] - cellX, pz[i] - cellZ, px[i] + cellX, pz[i] + cellZ)
                    }
                }
                remove = true
            } else if (page[i] > MAX_AGE_S) {
                if (kind == Grain.DETRITUS) resuspendedPending += DETRITUS_PER_PARTICLE
                remove = true
            }
            if (remove) { removeAt(i); continue }
            i++
        }
        version++
    }

    private fun removeAt(i: Int) {
        val l = particleCount - 1
        if (i != l) {
            px[i] = px[l]; py[i] = py[l]; pz[i] = pz[l]; pvy[i] = pvy[l]; phx[i] = phx[l]; phz[i] = phz[l]
            pfx[i] = pfx[l]; pfz[i] = pfz[l]; pkz[i] = pkz[l]; page[i] = page[l]; pkind[i] = pkind[l]
        }
        particleCount = l
    }

    /** Count of suspended particles of [g]. */
    fun suspended(g: Grain? = null): Int { var n = 0; for (i in 0 until particleCount) if (g == null || pkind[i].toInt() == g.ordinal) n++; return n }

    /** Highest suspended particle of [g] above the bed (cm), or 0. */
    fun maxHeightAboveBed(g: Grain? = null): Double {
        var m = 0.0
        for (i in 0 until particleCount) if (g == null || pkind[i].toInt() == g.ordinal) m = max(m, py[i] - surfaceY(px[i].toDouble(), pz[i].toDouble()))
        return m
    }

    /** Suspended particles for the renderer: x, y, z (cm) and kind + age fraction (`ordinal + min(age/60 s, 0.99)`). */
    fun cloudArray(): FloatArray {
        val out = FloatArray(particleCount * 4)
        for (i in 0 until particleCount) {
            out[i * 4] = px[i]; out[i * 4 + 1] = py[i]; out[i * 4 + 2] = pz[i]
            out[i * 4 + 3] = pkind[i] + min(page[i] / 60f, 0.99f)
        }
        return out
    }

    // ============================================================== slow processes
    /**
     * Once per chemistry tick ([dtMin] minutes): slope failure (angle of repose), smoothing that grows with the pumps
     * ([flowStrength] 0..2, ripples and pits flatten over hours to days in strong flow), freshness fading and detritus
     * settling toward what the water column carries ([waterDetritusPpm]).
     */
    fun relax(dtMin: Double, flowStrength: Double, waterDetritusPpm: Double) {
        val dtS = dtMin * 60
        // 1. avalanche: no slope steeper than the angle of repose of wet sand
        val maxStepX = (REPOSE_TAN * cellX).toFloat(); val maxStepZ = (REPOSE_TAN * cellZ).toFloat()
        repeat(2) {
            for (k in 0 until nz) for (i in 0 until nx) {
                val a = k * nx + i
                if (i + 1 < nx) slump(a, a + 1, maxStepX)
                if (k + 1 < nz) slump(a, a + nx, maxStepZ)
            }
        }
        // 2. smoothing: bioturbation background + flow (diffusion, explicit, unconditionally tiny steps)
        val f = flowStrength.coerceIn(0.0, 2.0)
        val d = (D_BASE + D_FLOW * f * f) * dtS
        val ax = (d / (cellX * cellX)).coerceAtMost(0.2).toFloat(); val az = (d / (cellZ * cellZ)).coerceAtMost(0.2).toFloat()
        if (ax > 1e-7f) {
            val h = height.copyOf()
            for (k in 0 until nz) for (i in 0 until nx) {
                val a = k * nx + i
                val c = h[a]
                val l = if (i > 0) h[a - 1] else c; val r = if (i < nx - 1) h[a + 1] else c
                val b = if (k > 0) h[a - nx] else c; val t = if (k < nz - 1) h[a + nx] else c
                height[a] = c + ax * (l + r - 2 * c) + az * (b + t - 2 * c)
            }
        }
        // 3. freshly turned sand fades back (biofilm and oxidised surface grains), detritus settles toward the column's load
        val kf = exp(-dtMin / FRESH_TAU_MIN).toFloat()
        val target = (waterDetritusPpm / DETRITUS_REF_PPM * (1 - 0.25 * min(f, 2.0))).coerceIn(0.0, MAX_DETRITUS.toDouble()).toFloat()
        val kd = (1 - exp(-dtMin / DETRITUS_TAU_MIN)).toFloat()
        for (a in 0 until nx * nz) {
            fresh[a] *= kf
            if (fresh[a] < 0.002f) fresh[a] = 0f
            detritus[a] += (target - detritus[a]) * kd
        }
        raised(0.0, 0.0, lengthCm, widthCm)   // slumps and smoothing raise the low cells anywhere
        version++
    }

    private fun slump(a: Int, b: Int, maxStep: Float) {
        val dh = height[a] - height[b]
        if (abs(dh) <= maxStep) return
        val move = (abs(dh) - maxStep) * 0.5f * (if (dh > 0) 1f else -1f)
        height[a] -= move; height[b] += move
    }

    /** Back to a smooth, settled bed (reset to Reality). */
    fun reset() {
        height.fill(0f); fresh.fill(0f); detritus.fill(0.25f)
        particleCount = 0; resuspendedPending = 0.0
        version++
    }

    // ============================================================== snapshot
    private var cached: SandGridSnapshot? = null

    /** Grid copy for the renderer; reused until the grid changes. */
    fun gridSnapshot(): SandGridSnapshot {
        val c = cached
        if (c != null && c.version == version) return c
        return SandGridSnapshot(nx, nz, cellX.toFloat(), cellZ.toFloat(), height.copyOf(), fresh.copyOf(), detritus.copyOf(), version).also { cached = it }
    }

    /** Mean height offset (cm): stays near zero because every edit moves sand rather than making it. */
    fun meanHeight(): Double { var s = 0.0; for (h in height) s += h; return s / height.size }

    companion object {
        const val NX = 64
        const val NZ = 24
        const val MAX_PARTICLES = 160
        const val BASE_FRONT = 3.5
        const val BASE_BACK = 6.0
        const val MAX_DEPTH = 3.0f
        const val MAX_MOUND = 4.0f
        const val MAX_DETRITUS = 1.5f
        /** Sand volume one cloud particle stands for (cm³). */
        const val GRAIN_CM3 = 0.004
        const val DETRITUS_PER_PARTICLE = 0.01
        /** Sand displaced by a sand-diving wrasse per cm of body length (cm³/cm): a ~9 cm fish leaves a ~1 cm³ crater. */
        @Ev(Evidence.DESIGN_ESTIMATE) const val DIVE_CM3_PER_CM = 0.12
        /** Jet decay time of a puff (s) and the relaxation of a particle's drift toward the flow (s). */
        @Ev(Evidence.DESIGN_ESTIMATE) const val JET_TAU_S = 0.25
        const val DRIFT_TAU_S = 0.8
        /** Share of the local flow a near-bed particle drifts with (the boundary layer is slower than the probe point). */
        @Ev(Evidence.DESIGN_ESTIMATE) const val FLOW_COUPLING = 0.6
        /** Eddy diffusivity per cm/s of flow (cm²/s per cm/s). */
        @Ev(Evidence.DESIGN_ESTIMATE) const val TURB_K = 0.03
        const val MAX_AGE_S = 240.0
        /** Angle of repose of wet carbonate sand ≈ 32° (tan). */
        @Ev(Evidence.ANALOGY, "wet sand angle of repose 30-35 deg") const val REPOSE_TAN = 0.62
        /** Smoothing diffusivity (cm²/s): background bioturbation and per (pump strength)² (ripples flatten over a day or two at 1×). */
        @Ev(Evidence.DESIGN_ESTIMATE) const val D_BASE = 1.0e-5
        @Ev(Evidence.DESIGN_ESTIMATE) const val D_FLOW = 4.0e-5
        /** Freshly turned sand looks like the rest again after ~6 h (1/e). */
        @Ev(Evidence.DESIGN_ESTIMATE) const val FRESH_TAU_MIN = 360.0
        /** Detritus on the bed follows the column's load over ~2 days; [DETRITUS_REF_PPM] reads as load 1. */
        @Ev(Evidence.DESIGN_ESTIMATE) const val DETRITUS_TAU_MIN = 2880.0
        const val DETRITUS_REF_PPM = 0.3
    }
}

/**
 * Sand work by the crawling inverts (SIMULATION §16.2), run after every behaviour tick on what they actually did: a
 * nassarius burying or emerging throws a little crater and puff, and leaves a ploughed trail while it crawls over the
 * sand; hermits and the Sally Lightfoot scratch the top layer as they walk; the brittle star's arms drag furrows. Pure
 * observation of positions and postures, so it never changes the animals' own behaviour streams.
 */
class SandWork(private val sand: SandBed, private val rock: RockData) {
    private class Track(var x: Double, var z: Double, var posture: Int, var acc: Double, var marks: Int)
    private val tracks = HashMap<String, Track>()
    private var gc = 0

    /** Trail marks made by nassarius (tests). */
    var nassariusMarks = 0L; private set
    var buryEvents = 0L; private set

    fun step(organisms: List<Organism>) {
        if (++gc % 400 == 0) { val ids = organisms.mapTo(HashSet()) { it.id }; tracks.keys.retainAll(ids) }
        for (o in organisms) {
            if (!o.alive) continue
            val kind = o.brainKind
            if (kind != BrainKind.NASSARIUS && kind != BrainKind.HERMIT && kind != BrainKind.SALLY_CRAB && kind != BrainKind.BRITTLE_STAR) continue
            val p = o.pos
            val t = tracks.getOrPut(o.id) { Track(p.x, p.z, o.posture, 0.0, 0) }
            val dx = p.x - t.x; val dz = p.z - t.z
            val moved = sqrt(dx * dx + dz * dz)
            val onSand = p.y < rock.floorY(p.x, p.z) + 2.0 + 0.5 * o.species.depthRatio * o.lengthCm && rock.sdf.dist(p) > 0.6
            if (moved > 6.0) { t.x = p.x; t.z = p.z; t.acc = 0.0; t.posture = o.posture; continue }   // placed, not walked
            if (kind == BrainKind.NASSARIUS && onSand && (t.posture == 5) != (o.posture == 5)) {
                // burying or emerging: a small crater, the spoil around it and a little puff
                sand.dig(p.x, p.z, 0.25, 0.9)
                sand.deposit(p.x, p.z, 0.25, 2.0)
                sand.puff(p.x, sand.surfaceY(p.x, p.z) + 0.2, p.z, 3, 2.0)
                buryEvents++
            }
            t.posture = o.posture
            if (!onSand || o.posture == 5 || moved < 1e-4) { t.x = p.x; t.z = p.z; continue }
            t.acc += moved
            val step = when (kind) { BrainKind.NASSARIUS -> 0.6; BrainKind.BRITTLE_STAR -> 1.2; else -> 1.0 }
            if (t.acc >= step) {
                t.acc = 0.0
                when (kind) {
                    BrainKind.NASSARIUS -> { sand.plough(p.x, p.z, dx, dz, 0.10, 0.45, 0.85); nassariusMarks++ }
                    BrainKind.BRITTLE_STAR -> sand.plough(p.x, p.z, dx, dz, 0.06, 1.0, 0.6)
                    else -> {
                        sand.freshen(p.x, p.z, 0.9 + 0.1 * o.lengthCm, 0.55)
                        sand.plough(p.x, p.z, dx, dz, 0.025, 0.4, 0.5)
                        t.marks++
                        if (t.marks % 12 == 0) sand.puff(p.x, sand.surfaceY(p.x, p.z) + 0.2, p.z, 2, 1.5)
                    }
                }
            }
            t.x = p.x; t.z = p.z
        }
    }
}
