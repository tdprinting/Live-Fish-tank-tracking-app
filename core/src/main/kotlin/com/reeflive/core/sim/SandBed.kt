package com.reeflive.core.sim

import com.reeflive.core.geom.Rng
import com.reeflive.core.geom.Sdf
import com.reeflive.core.geom.Vec3
import kotlin.math.PI
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
    /**
     * Dug-out cavity under rock (0..1): the clear gap between the sand and the rock underside over 2 cm, 0 where the
     * sand still meets the rock or there is no rock (SIMULATION §16.8). Empty for a bed without rock.
     */
    val cavity: FloatArray = FloatArray(0),
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

    /**
     * Visible sand surface (cm) at (x, z): the modelled bed plus what the animals did to it and any body buried in it
     * (static.vert lifts the mesh the same way).
     */
    fun surfaceY(x: Double, z: Double): Double = modelY(x, z) + heightAt(x, z) + inclusionAt(x, z)

    /**
     * An upper bound on [surfaceY] over [x0]..[x1] × [z0]..[z1] (the body collision's exact skips): the most of each
     * blended grid's heights there, plus a margin for the rounding of the blends.
     */
    fun surfaceMax(x0: Double, z0: Double, x1: Double, z1: Double): Double {
        val m = base?.maxIn(x0, z0, x1, z1) ?: max(baseY(z0), baseY(z1))
        return m + gridMax(height, x0, z0, x1, z1) + (if (anyInclusion) gridMax(inclusion, x0, z0, x1, z1) else 0.0) + 1e-5
    }

    /** The most of the cell values [heightAt] / [inclusionAt] blend in the box (NaN where one is). */
    private fun gridMax(g: FloatArray, x0: Double, z0: Double, x1: Double, z1: Double): Double {
        val i0 = (x0 / cellX - 0.5).coerceIn(0.0, nx - 1.001).toInt(); val i1 = (x1 / cellX - 0.5).coerceIn(0.0, nx - 1.001).toInt() + 1
        val k0 = (z0 / cellZ - 0.5).coerceIn(0.0, nz - 1.001).toInt(); val k1 = (z1 / cellZ - 0.5).coerceIn(0.0, nz - 1.001).toInt() + 1
        var m = Double.NEGATIVE_INFINITY
        for (k in k0..k1) for (i in i0..i1) m = max(m, g[k * nx + i].toDouble())
        return m
    }

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
            height[i] = (height[i] + (cm3 * w / ws / cellArea).toFloat()).coerceIn(floorH[i], MAX_MOUND)
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
            height[i] = (height[i] + dh.toFloat()).coerceIn(floorH[i], MAX_MOUND)
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
            var kind = when {
                u < dShare * 0.6 -> Grain.DETRITUS
                u < dShare * 0.6 + finesShare * 0.45 -> Grain.SILT
                u < dShare * 0.6 + finesShare -> Grain.FINE
                else -> Grain.SAND
            }
            // a cell dug to its floor has no sand left to throw: the puff is fines only (sand is never made from nothing)
            if ((kind == Grain.SAND || kind == Grain.FINE) && height[i0] - GRAIN_CM3 / cellArea < floorH[i0]) kind = Grain.SILT
            px[slot] = (x + rng.signed() * 0.6).toFloat(); py[slot] = (y + rng.nextDouble() * 0.4).toFloat(); pz[slot] = (z + rng.signed() * 0.6).toFloat()
            pvy[slot] = (upCmS * (0.5 + 0.5 * rng.nextDouble())).toFloat()
            phx[slot] = (hx + rng.signed() * 0.4 * upCmS).toFloat(); phz[slot] = (hz + rng.signed() * 0.4 * upCmS).toFloat()
            pfx[slot] = 0f; pfz[slot] = 0f; pkz[slot] = 0f
            page[slot] = 0f
            pkind[slot] = kind.ordinal.toByte()
            if (kind == Grain.DETRITUS) detritus[i0] = max(0f, detritus[i0] - DETRITUS_PER_PARTICLE.toFloat())
            else if (kind != Grain.SILT) height[i0] = height[i0] - (GRAIN_CM3 / cellArea).toFloat()
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
                    // no upper clamp: a grain always lands (slope failure in [relax] spreads a heap), so sand is conserved
                    else -> {
                        height[c] = height[c] + (GRAIN_CM3 / cellArea).toFloat()
                        raised(px[i] - cellX, pz[i] - cellZ, px[i] + cellX, pz[i] + cellZ)
                    }
                }
                remove = true
            } else if (page[i] > MAX_AGE_S) {
                if (kind == Grain.DETRITUS) resuspendedPending += DETRITUS_PER_PARTICLE
                // sand still up (a strong jet) drops where it is rather than vanishing
                else if (kind != Grain.SILT) { val c = index(px[i].toDouble(), pz[i].toDouble()); height[c] = height[c] + (GRAIN_CM3 / cellArea).toFloat() }
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
        // sand under rock is held by the roof and bound by the burrower's mucus: tunnel walls stand far steeper
        val confX = (CONFINED_REPOSE_TAN * cellX).toFloat(); val confZ = (CONFINED_REPOSE_TAN * cellZ).toFloat()
        repeat(2) {
            for (k in 0 until nz) for (i in 0 until nx) {
                val a = k * nx + i
                if (i + 1 < nx) slump(a, a + 1, if (roofed(a) || roofed(a + 1)) confX else maxStepX)
                if (k + 1 < nz) slump(a, a + nx, if (roofed(a) || roofed(a + nx)) confZ else maxStepZ)
            }
        }
        // 2. smoothing: bioturbation background + flow (diffusion, explicit, unconditionally tiny steps)
        val f = flowStrength.coerceIn(0.0, 2.0)
        val d = (D_BASE + D_FLOW * f * f) * dtS
        val ax = (d / (cellX * cellX)).coerceAtMost(0.2).toFloat(); val az = (d / (cellZ * cellZ)).coerceAtMost(0.2).toFloat()
        if (ax > 1e-7f) {
            // on the visible surface: a buried body's volume holds its cover up instead of sand creeping in under it
            val h = height.copyOf()
            if (anyInclusion) for (a in h.indices) h[a] += inclusion[a]
            for (k in 0 until nz) for (i in 0 until nx) {
                val a = k * nx + i
                val c = h[a]
                val l = if (i > 0) h[a - 1] else c; val r = if (i < nx - 1) h[a + 1] else c
                val b = if (k > 0) h[a - nx] else c; val t = if (k < nz - 1) h[a + nx] else c
                height[a] = height[a] + ax * (l + r - 2 * c) + az * (b + t - 2 * c)   // left to right: bit-identical to a bed with nothing buried
            }
        }
        // 2b. burrows under rock slowly fill back in: grains trickle off the walls and the flow carries sand in
        if (rockAttached) infill(dtMin, f)
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
        val dh = height[a] + inclusion[a] - height[b] - inclusion[b]
        if (abs(dh) <= maxStep) return
        val move = (abs(dh) - maxStep) * 0.5f * (if (dh > 0) 1f else -1f)
        height[a] -= move; height[b] += move
    }

    /** Back to a smooth, settled bed (reset to Reality). */
    fun reset() {
        height.fill(0f); fresh.fill(0f); detritus.fill(0.25f)
        particleCount = 0; resuspendedPending = 0.0
        burrows.clear(); carriedCm3 = 0.0; burrowEpoch++
        inclusion.fill(0f); anyInclusion = false; buried.clear()
        version++
    }

    // ============================================================== rock roof, tunnels and burrows (SIMULATION §16.8)
    /**
     * Rock underside over each cell (y, cm): the lowest rock above the deepest diggable sand, within [ROOF_RANGE] of the
     * modelled bed. [OPEN] = open water above the sand; [SOLID] = the rock goes down to the glass (nothing to dig under).
     * Filled by [attachRock]; all [OPEN] for a bed without rock.
     */
    val roof = FloatArray(nx * nz) { OPEN }
    /** Lowest height offset each cell can be dug to (cm): −[MAX_DEPTH] on open sand, almost to the glass under rock. */
    private val floorH = FloatArray(nx * nz) { -MAX_DEPTH }
    var rockAttached = false; private set
    /** The rock's distance field (set by [attachRock]): [fits] checks the body's real clearance from the rock with it. */
    private var rockSdf: Sdf? = null

    /**
     * Reads the rock underside over every cell from [sdf] (sphere-traced up each cell's centre column from just above
     * the glass). Called once by the Simulation; digging then opens real gaps between the sand and the rock.
     */
    fun attachRock(sdf: Sdf) {
        for (k in 0 until nz) for (i in 0 until nx) {
            val a = k * nx + i
            val x = (i + 0.5) * cellX; val z = (k + 0.5) * cellZ
            val base = baseY(z)
            var y = GLASS_Y + FLOOR_MARGIN
            var r = OPEN
            var d = sdf.dist(Vec3(x, y, z))
            if (d <= 0.0) r = SOLID
            else while (y < base + ROOF_RANGE) {
                y += max(ROOF_STEP, 0.9 * d)
                d = sdf.dist(Vec3(x, y, z))
                if (d <= 0.0) { r = y.toFloat(); break }
            }
            roof[a] = r
            floorH[a] = if (r != OPEN && r != SOLID) min(-MAX_DEPTH, -(base - GLASS_Y - FLOOR_MARGIN).toFloat()) else -MAX_DEPTH
        }
        rockAttached = true
        rockSdf = sdf
        version++
    }

    /** True for a cell with rock over its sand that digging can get under (not [OPEN], not [SOLID]). */
    fun roofed(a: Int): Boolean { val r = roof[a]; return r != OPEN && r != SOLID }
    fun underRock(x: Double, z: Double): Boolean = roof[index(x, z)] != OPEN

    /** Lowest height offset (cm) digging can reach at (x, z), bilinear like [heightAt]. */
    fun floorAt(x: Double, z: Double): Double {
        val fx = (x / cellX - 0.5).coerceIn(0.0, nx - 1.001)
        val fz = (z / cellZ - 0.5).coerceIn(0.0, nz - 1.001)
        val i = fx.toInt(); val k = fz.toInt()
        val tx = fx - i; val tz = fz - k
        val a = floorH[k * nx + i]; val b = floorH[k * nx + i + 1]
        val c = floorH[(k + 1) * nx + i]; val d = floorH[(k + 1) * nx + i + 1]
        return (a + (b - a) * tx) * (1 - tz) + (c + (d - c) * tx) * tz
    }

    /** Clear height (cm) between the sand and the rock underside at (x, z): +∞ in open water, 0 where they meet. */
    fun gapAt(x: Double, z: Double): Double {
        val r = roof[index(x, z)]
        if (r == OPEN) return Double.POSITIVE_INFINITY
        if (r == SOLID) return 0.0
        return max(0.0, r - surfaceY(x, z))
    }

    /** The most clearance digging could ever open at (x, z) (cm). */
    fun maxGapAt(x: Double, z: Double): Double {
        val r = roof[index(x, z)]
        if (r == OPEN) return Double.POSITIVE_INFINITY
        if (r == SOLID) return 0.0
        return max(0.0, r - (baseY(z) + floorAt(x, z)))
    }

    /** Whether digging to the floor could ever clear a body [bodyH] × [bodyW] at (x, z) (roof and rock shape). */
    fun diggable(x: Double, z: Double, bodyH: Double, bodyW: Double): Boolean {
        if (maxGapAt(x, z) < bodyH) return false
        val sdf = rockSdf ?: return true
        if (roof[index(x, z)] == OPEN) return true
        val floorY = baseY(z) + floorAt(x, z)
        // somewhere between the floor and the roof the body's midline clears the rock
        var y = floorY + 0.5 * bodyH
        val top = roof[index(x, z)] - 0.5 * bodyH
        while (y <= top) { if (sdf.dist(Vec3(x, y, z)) >= FIT_CLEAR * min(bodyH, bodyW)) return true; y += 0.2 }
        return false
    }

    /**
     * Whether a body [bodyH] tall and [bodyW] wide fits at (x, z) heading along ([dx], [dz]): the gap must clear the
     * body at its midline and at both flanks. Open water always fits.
     */
    fun fits(x: Double, z: Double, dx: Double, dz: Double, bodyH: Double, bodyW: Double): Boolean {
        val l = sqrt(dx * dx + dz * dz)
        val px = if (l > 1e-9) -dz / l else 0.0; val pz = if (l > 1e-9) dx / l else 1.0
        val hw = 0.5 * bodyW
        if (!(gapAt(x, z) >= bodyH && gapAt(x + px * hw, z + pz * hw) >= bodyH && gapAt(x - px * hw, z - pz * hw) >= bodyH)) return false
        // the rock's own shape inside the cell (a lump lower than the cell-centre roof, a wall at the side)
        val sdf = rockSdf ?: return true
        if (roof[index(x, z)] == OPEN) return true
        return sdf.dist(Vec3(x, surfaceY(x, z) + 0.5 * bodyH, z)) >= FIT_CLEAR * min(bodyH, bodyW)
    }

    /** Sand (cm³) an animal has in its mouth between a bite and a spit (counted in [sandVolumeCm3]). */
    var carriedCm3 = 0.0; private set
    /** Centre of the cell the last mouthful came from (cm). */
    var lastDigX = Double.NaN; private set
    var lastDigZ = Double.NaN; private set
    /** Mouthfuls taken out of tunnels and burrows (tests, captions). */
    var mouthfuls = 0L; private set

    /** Of the four cells that make up the bilinear surface at (x, z), the one whose digging lowers it most. */
    private fun digCell(x: Double, z: Double): Int {
        val fx = (x / cellX - 0.5).coerceIn(0.0, nx - 1.001)
        val fz = (z / cellZ - 0.5).coerceIn(0.0, nz - 1.001)
        val i = fx.toInt(); val k = fz.toInt()
        val tx = fx - i; val tz = fz - k
        var best = index(x, z); var bestW = -1.0
        for (c in 0 until 4) {
            val ci = i + (c and 1); val ck = k + (c shr 1)
            val a = ck * nx + ci
            val w = (if (c and 1 == 1) tx else 1 - tx) * (if (c shr 1 == 1) tz else 1 - tz)
            val score = w * (height[a] - floorH[a])
            if (score > bestW) { bestW = score; best = a }
        }
        return best
    }

    /**
     * A digger takes a mouthful of up to [cm3] at (x, z), from the cell that holds the surface up there most. Never
     * below the cell's floor. Returns what it took; that sand is [carriedCm3] until [spit] puts it down.
     */
    fun takeMouthful(x: Double, z: Double, cm3: Double): Double {
        val a = digCell(x, z)
        val take = min(cm3, max(0.0, (height[a] - floorH[a]).toDouble() * cellArea))
        if (take <= 1e-9) return 0.0
        height[a] = max(floorH[a], height[a] - (take / cellArea).toFloat())
        fresh[a] = max(fresh[a], 0.95f)
        lastDigX = (a % nx + 0.5) * cellX; lastDigZ = (a / nx + 0.5) * cellZ
        carriedCm3 += take
        movedCm3 += take
        mouthfuls++
        version++
        return take
    }

    /**
     * Spits [cm3] of carried sand onto a spoil pile at (x, z) (radius [r]): the coarse grains drop as a mound, a
     * little goes up as a puff ([plume] particles) that settles nearby. Mass-conserving (see [depositExact]).
     */
    fun spit(x: Double, y: Double, z: Double, cm3: Double, r: Double = 1.4, plume: Int = 5, hx: Double = 0.0, hz: Double = 0.0) {
        val v = min(cm3, carriedCm3).coerceAtLeast(0.0)
        carriedCm3 -= v
        depositExact(x, z, v, r)
        if (plume > 0) puff(x, y, z, plume, 3.5, hx, hz, finesShare = 0.45)
    }

    /** Drops whatever sand is carried at (x, z) (an interrupted digger lets its mouthful fall). */
    fun dropCarried(x: Double, z: Double) { if (carriedCm3 > 0) spit(x, 0.0, z, carriedCm3, 1.0, plume = 0) }

    /**
     * Adds exactly [cm3] around (x, z) within [r]: whatever would heap above [MAX_MOUND] spills to a wider ring
     * (sand is neither lost to the clamp nor made by it).
     */
    fun depositExact(x: Double, z: Double, cm3: Double, r: Double) {
        var left = cm3
        var rr = r
        var pass = 0
        while (left > 1e-9 && pass < 6) {
            val ws = weightSum(x, z, rr).coerceAtLeast(1e-6)
            var placed = 0.0
            val want = left
            around(x, z, rr) { i, w ->
                val add = min(want * w / ws / cellArea, max(0.0, (MAX_MOUND - height[i]).toDouble()))
                if (add > 0) { height[i] += add.toFloat(); placed += add * cellArea; fresh[i] = max(fresh[i], (0.6 + 0.4 * w).toFloat()) }
            }
            left -= placed
            rr *= 1.6; pass++
        }
        if (left > 1e-9) { val i = index(x, z); height[i] += (left / cellArea).toFloat() }
        if (cm3 > 0) { movedCm3 += cm3; version++ }
    }

    /** Takes up to [cm3] from around (x, z) within [r], never below the floors; returns what it took. */
    fun takeExact(x: Double, z: Double, cm3: Double, r: Double): Double {
        if (cm3 <= 0) return 0.0
        val ws = weightSum(x, z, r).coerceAtLeast(1e-6)
        var taken = 0.0
        around(x, z, r) { i, w ->
            val t = min(cm3 * w / ws / cellArea, max(0.0, (height[i] - floorH[i]).toDouble()))
            if (t > 0) { height[i] -= t.toFloat(); taken += t * cellArea; fresh[i] = max(fresh[i], (0.4 + 0.4 * w).toFloat()) }
        }
        if (taken > 0) version++
        return taken
    }

    /**
     * A body pushing over the sand at (x, z) along ([dx], [dz]): [cm3] is shoved out of a footprint of radius [r]
     * into a low bow wave ahead (60 %) and to both sides. Mass-conserving.
     */
    fun bowWave(x: Double, z: Double, dx: Double, dz: Double, r: Double, cm3: Double) {
        val l = sqrt(dx * dx + dz * dz)
        val ux = if (l > 1e-9) dx / l else 1.0; val uz = if (l > 1e-9) dz / l else 0.0
        val v = takeExact(x, z, cm3, r)
        if (v <= 0) return
        depositExact(x + ux * 1.4 * r, z + uz * 1.4 * r, v * 0.6, r)
        depositExact(x - uz * 1.3 * r, z + ux * 1.3 * r, v * 0.2, r)
        depositExact(x + uz * 1.3 * r, z - ux * 1.3 * r, v * 0.2, r)
        bowWaves++
    }

    /** A resting body leaves an impression of [cm3] at (x, z) (radius [r]); the sand is pressed out to a low rim. */
    fun impress(x: Double, z: Double, r: Double, cm3: Double) {
        val v = takeExact(x, z, cm3, r)
        if (v <= 0) return
        val q = v / 4
        depositExact(x + 1.5 * r, z, q, r); depositExact(x - 1.5 * r, z, q, r)
        depositExact(x, z + 1.5 * r, q, r); depositExact(x, z - 1.5 * r, q, r)
        impressions++
    }

    var bowWaves = 0L; private set
    var impressions = 0L; private set

    /** All the sand there is (cm³ relative to the modelled bed): the grid, the grains in suspension and the mouthfuls carried. */
    fun sandVolumeCm3(): Double {
        var s = 0.0
        for (h in height) s += h
        var g = 0
        for (i in 0 until particleCount) { val k = pkind[i].toInt(); if (k == Grain.SAND.ordinal || k == Grain.FINE.ordinal) g++ }
        return s * cellArea + g * GRAIN_CM3 + carriedCm3
    }

    /** Burrows dug under the rock, by owner (an engineer goby keeps a few; SIMULATION §16.8). */
    val burrows = ArrayList<Burrow>()
    /** Bumped when [burrows] changes (brains cache their own list). */
    var burrowEpoch = 0; private set

    fun burrowsOf(owner: String): List<Burrow> = burrows.filter { it.owner == owner }

    /**
     * Plans a burrow for [owner] from (mx, mz) into the rock along ([dx], [dz]): the mouth goes back to the rock edge
     * (the last open-water point before the roof), and the tunnel may run on as long as digging could ever clear a
     * body [bodyH] tall, up to [maxLen]. Null when there is no rock to dig under there.
     */
    fun planBurrow(owner: String, mx: Double, mz: Double, dx: Double, dz: Double, bodyH: Double, maxLen: Double, primary: Boolean): Burrow? {
        if (!rockAttached) return null
        val l = sqrt(dx * dx + dz * dz)
        if (l < 1e-9) return null
        val ux = dx / l; val uz = dz / l
        var x = mx; var z = mz
        // back out to the rock edge
        var back = 0.0
        while (underRock(x, z) && back < 10.0) { x -= ux * 0.4; z -= uz * 0.4; back += 0.4 }
        if (underRock(x, z)) { x = mx; z = mz }
        // then forward to the first roofed point (the mouth sits at the rock edge)
        var fwd = 0.0
        while (!underRock(x + ux * 0.4, z + uz * 0.4) && fwd < 10.0) { x += ux * 0.4; z += uz * 0.4; fwd += 0.4 }
        if (x < 1.0 || z < 1.0 || x > lengthCm - 1.0 || z > widthCm - 1.0) return null
        var s = 0.4
        while (s <= maxLen) {
            val px = x + ux * s; val pz = z + uz * s
            if (px < 0.5 || pz < 0.5 || px > lengthCm - 0.5 || pz > widthCm - 0.5) break
            if (!diggable(px, pz, bodyH, 0.65 * bodyH)) break
            s += 0.4
        }
        val len = s - 0.4
        if (len < 2.0) return null
        return Burrow(owner, x, z, ux, uz, len, primary)
    }

    fun addBurrow(b: Burrow): Burrow { burrows += b; burrowEpoch++; version++; return b }

    /**
     * Rock-edge sites near (cx, cz) within [radius] where a burrow could run at least [minLen] under the rock for a
     * body [bodyH] tall: open sand cells next to diggable roofed ones, the direction pointing into the rock. Best
     * (longest) first, at least [spacing] cm apart and from [avoid].
     */
    fun burrowSites(owner: String, cx: Double, cz: Double, radius: Double, bodyH: Double, minLen: Double, maxLen: Double, spacing: Double, avoid: List<Burrow>, count: Int): List<Burrow> {
        if (!rockAttached) return emptyList()
        val cands = ArrayList<Burrow>()
        for (k in 1 until nz - 1) for (i in 1 until nx - 1) {
            val a = k * nx + i
            if (roof[a] != OPEN) continue
            val x = (i + 0.5) * cellX; val z = (k + 0.5) * cellZ
            if ((x - cx) * (x - cx) + (z - cz) * (z - cz) > radius * radius) continue
            var dx = 0.0; var dz = 0.0
            for (o in intArrayOf(-1, 1)) {
                if (roofed(a + o)) dx += o.toDouble()
                if (roofed(a + o * nx)) dz += o.toDouble()
            }
            if (dx == 0.0 && dz == 0.0) continue
            val b = planBurrow(owner, x, z, dx, dz, bodyH, maxLen, false) ?: continue
            if (b.lengthCm >= minLen) cands += b
        }
        // cheapest to open first, then longest: a site where the rock foot sits deep in the sand needs centimetres of sand
        // moved before the body fits, and its pit slumps back in about as fast as one goby carries it out
        fun need(b: Burrow): Double {
            var sum = 0.0; var n = 0; var t = 0.0
            while (t <= b.lengthCm) {
                val g = gapAt(b.x(t), b.z(t))
                if (g.isFinite()) { sum += max(0.0, bodyH - g); n++ }
                t += REACH_STEP
            }
            return if (n == 0) 0.0 else sum / n
        }
        cands.sortBy { need(it) - 0.05 * it.lengthCm }
        val out = ArrayList<Burrow>()
        for (b in cands) {
            if (out.size >= count) break
            fun far(o: Burrow) = (o.mouthX - b.mouthX).let { it * it } + (o.mouthZ - b.mouthZ).let { it * it } >= spacing * spacing
            if (avoid.all(::far) && out.all(::far)) out += b
        }
        return out
    }

    /** How far (cm from the mouth) a body [bodyH] × [bodyW] can get into [b] now: the dig face. */
    fun reach(b: Burrow, bodyH: Double, bodyW: Double): Double {
        var s = 0.0
        while (s <= b.lengthCm) {
            if (!fits(b.x(s), b.z(s), b.dirX, b.dirZ, bodyH, bodyW)) return max(0.0, s - REACH_STEP)
            s += REACH_STEP
        }
        return b.lengthCm
    }

    /**
     * Where the next mouthful in [b] comes from (cm along the burrow; negative = the mouth apron), or null when there
     * is nothing left to dig: first the face (the burrow is not yet open to its length for this body), then the floor
     * of any stretch lower than [chamberH] (sand that trickled back in), then a longer tunnel, then the apron.
     */
    fun nextDig(b: Burrow, bodyH: Double, bodyW: Double, chamberH: Double, maxLen: Double): Double? {
        val r = reach(b, bodyH, bodyW)
        if (r < b.lengthCm) return min(b.lengthCm, r + REACH_STEP)
        var worst = Double.NaN; var worstGap = chamberH
        var s = 0.0
        while (s <= b.lengthCm) {
            val g = gapAt(b.x(s), b.z(s))
            if (g < worstGap && maxGapAt(b.x(s), b.z(s)) > g + 0.05) { worstGap = g; worst = s }
            s += REACH_STEP * 2
        }
        if (!worst.isNaN()) return worst
        val ext = b.lengthCm + 1.0
        if (ext <= maxLen && diggable(b.x(ext), b.z(ext), bodyH, bodyW) &&
            b.x(ext) in 0.5..(lengthCm - 0.5) && b.z(ext) in 0.5..(widthCm - 0.5)) {
            b.lengthCm = ext
            return ext
        }
        val ax = b.x(-1.0); val az = b.z(-1.0)
        return if (heightAt(ax, az) > floorAt(ax, az) + 0.05) -1.0 else null
    }

    /**
     * Digs [b] out at once (an established burrow, or abstract time): mouthfuls of [mouthCm3] from [nextDig] spat onto
     * the spoil pile at (sx, sz) until the body fits its length at [chamberH] or [budgetCm3] is spent. Mass-conserving.
     */
    fun excavate(b: Burrow, bodyH: Double, bodyW: Double, chamberH: Double, mouthCm3: Double, sx: Double, sz: Double, budgetCm3: Double): Double {
        var spent = 0.0
        var guard = 0
        while (spent < budgetCm3 && guard++ < 2000) {
            val s = nextDig(b, bodyH, bodyW, chamberH, b.lengthCm) ?: break
            if (s < 0) break
            val got = takeMouthful(b.x(s), b.z(s), min(mouthCm3, budgetCm3 - spent))
            if (got <= 0) break
            b.mouthfuls++
            spit(sx, 0.0, sz, got, 1.6, plume = 0)
            spent += got
        }
        return spent
    }

    /**
     * Sand trickling back into the tunnels under rock ([relax]): every pair of neighbouring cells with one under rock
     * evens out by a fraction set by [INFILL_TAU_MIN] (faster in strong flow). Pairwise, so sand is conserved.
     */
    private fun infill(dtMin: Double, flow: Double) {
        val k = (dtMin / INFILL_TAU_MIN * (0.5 + 0.5 * flow)).coerceAtMost(0.2).toFloat()
        if (k <= 0f) return
        for (kk in 0 until nz) for (i in 0 until nx) {
            val a = kk * nx + i
            if (i + 1 < nx) evenOut(a, a + 1, k)
            if (kk + 1 < nz) evenOut(a, a + nx, k)
        }
    }

    private fun evenOut(a: Int, b: Int, k: Float) {
        if (!roofed(a) && !roofed(b)) return
        val m = (height[a] - height[b]) * 0.5f * k
        if (m == 0f) return
        // never below a floor
        val mm = if (m > 0) min(m, max(0f, height[a] - floorH[a])) else max(m, -max(0f, height[b] - floorH[b]))
        height[a] -= mm; height[b] += mm
    }

    /** Cavity shading for the renderer: dug gap under rock beyond the gap the bed started with, 0..1 per cell. */
    private fun cavityArray(): FloatArray {
        val out = FloatArray(nx * nz)
        for (a in 0 until nx * nz) {
            if (!roofed(a)) continue
            val z = (a / nx + 0.5) * cellZ
            val gap = roof[a] - (baseY(z) + height[a])
            val start = roof[a] - baseY(z)
            if (gap > 0.2 && height[a] < -0.1f) out[a] = (min(gap, 3.0) / 3.0 * min(1.0, (gap - max(start, 0.0)) / 1.0 + 0.3)).toFloat().coerceIn(0f, 1f)
        }
        return out
    }

    /** Copies the bed's state from [o] (a time-lapse fork); suspended particles are not carried over. */
    fun copyFrom(o: SandBed) {
        o.height.copyInto(height); o.fresh.copyInto(fresh); o.detritus.copyInto(detritus)
        burrows.clear()
        for (b in o.burrows) burrows += Burrow(b.owner, b.mouthX, b.mouthZ, b.dirX, b.dirZ, b.lengthCm, b.primary).also { it.mouthfuls = b.mouthfuls }
        burrowEpoch++
        carriedCm3 = 0.0
        o.inclusion.copyInto(inclusion); anyInclusion = o.anyInclusion
        buried.clear()
        for ((k, b) in o.buried) buried[k] = b.copy()
        version++
    }

    // ============================================================== bodies buried in the sand (SIMULATION §16.10)
    /**
     * A body lying in the sand: the cells of its slot with their weights, the trench depth dug there, the sand thrown to
     * the rim spots, and the body volume it puts under the surface ([inclusion]).
     */
    class BuriedBody(
        val owner: String, val headX: Double, val headZ: Double, val ux: Double, val uz: Double,
        val lengthCm: Double, val widthCm: Double, val thickCm: Double, val coverCm: Double,
        val cells: IntArray, val w: FloatArray, val bottomY: Double,
    ) {
        /** Share of the trench dug and of the cover pulled back over the body (0..1 each). */
        var dug = 0.0
        var covered = 0.0
        /** Body volume added per cell so far (cm as height). */
        val added = FloatArray(cells.size)
        /** Sand thrown to the rim (cm³), per rim spot: ahead, left, right. */
        val rim = DoubleArray(3)
        fun rimX(k: Int) = when (k) { 0 -> headX + ux * 1.6; 1 -> cx - uz * (0.5 * widthCm + 1.6); else -> cx + uz * (0.5 * widthCm + 1.6) }
        fun rimZ(k: Int) = when (k) { 0 -> headZ + uz * 1.6; 1 -> cz + ux * (0.5 * widthCm + 1.6); else -> cz - ux * (0.5 * widthCm + 1.6) }
        val cx: Double get() = headX - ux * 0.5 * lengthCm
        val cz: Double get() = headZ - uz * 0.5 * lengthCm
        /** Centre height (cm) of the body lying on the trench floor. */
        val bodyY: Double get() = bottomY + 0.5 * thickCm
        /** Depth of the body's top under the surface it made (cm) once covered. */
        val coveredBy: Double get() = covered * coverCm

        fun copy(): BuriedBody = BuriedBody(owner, headX, headZ, ux, uz, lengthCm, widthCm, thickCm, coverCm, cells.copyOf(), w.copyOf(), bottomY).also {
            it.dug = dug; it.covered = covered; added.copyInto(it.added); rim.copyInto(it.rim)
        }
    }

    /** Body volume under the sand per cell (cm, as height): it lifts the visible surface but is not sand. */
    private val inclusion = FloatArray(nx * nz)
    private var anyInclusion = false
    private val buried = HashMap<String, BuriedBody>()
    fun buriedOf(owner: String): BuriedBody? = buried[owner]

    /** Buried-body volume under (x, z) as height (cm), bilinear like [heightAt]. */
    fun inclusionAt(x: Double, z: Double): Double {
        if (!anyInclusion) return 0.0
        val fx = (x / cellX - 0.5).coerceIn(0.0, nx - 1.001)
        val fz = (z / cellZ - 0.5).coerceIn(0.0, nz - 1.001)
        val i = fx.toInt(); val k = fz.toInt()
        val tx = fx - i; val tz = fz - k
        val a = inclusion[k * nx + i]; val b = inclusion[k * nx + i + 1]
        val c = inclusion[(k + 1) * nx + i]; val d = inclusion[(k + 1) * nx + i + 1]
        return (a + (b - a) * tx) * (1 - tz) + (c + (d - c) * tx) * tz
    }

    /** Sand (not counting buried bodies) above the glass at (x, z) (cm). */
    fun sandDepthAt(x: Double, z: Double): Double = baseY(z) + heightAt(x, z) - GLASS_Y

    /** Cells of a body slot from the head at (hx, hz) back along −(ux, uz) for [len], [width] wide, weight 1 at the midline. */
    private fun slot(hx: Double, hz: Double, ux: Double, uz: Double, len: Double, width: Double): Pair<IntArray, FloatArray> {
        val r = max(0.5 * width, 0.55 * max(cellX, cellZ))
        val ids = ArrayList<Int>(); val ws = ArrayList<Float>()
        val i0 = ((min(hx, hx - ux * len) - r) / cellX).toInt().coerceIn(0, nx - 1)
        val i1 = ((max(hx, hx - ux * len) + r) / cellX).toInt().coerceIn(0, nx - 1)
        val k0 = ((min(hz, hz - uz * len) - r) / cellZ).toInt().coerceIn(0, nz - 1)
        val k1 = ((max(hz, hz - uz * len) + r) / cellZ).toInt().coerceIn(0, nz - 1)
        for (k in k0..k1) for (i in i0..i1) {
            val px = (i + 0.5) * cellX - hx; val pz = (k + 0.5) * cellZ - hz
            val t = (-(px * ux + pz * uz)).coerceIn(0.0, len)
            val dx = px + ux * t; val dz = pz + uz * t
            val q = (dx * dx + dz * dz) / (r * r)
            if (q >= 1.0) continue
            ids += k * nx + i; ws += (1 - q).toFloat()
        }
        if (ids.isEmpty()) { ids += index(hx, hz); ws += 1f }
        return ids.toIntArray() to ws.toFloatArray()
    }

    /**
     * Whether a body [len] × [width] × [thick] can bury itself with its head at (hx, hz) heading (ux, uz) under [cover]
     * cm of sand: open sand (no rock over it, no other body in it) deep enough everywhere along the slot for the trench
     * (thick + cover) above the cell's floor.
     */
    fun canBury(hx: Double, hz: Double, ux: Double, uz: Double, len: Double, width: Double, thick: Double, cover: Double): Boolean {
        val (ids, _) = slot(hx, hz, ux, uz, len, width)
        val d = (thick + cover).toFloat()
        for (a in ids) {
            if (roof[a] != OPEN || inclusion[a] > 0.01f) return false
            if (height[a] - d < buryFloor(a)) return false
        }
        return true
    }

    /** Lowest a body slot can be dug to in cell [a] (height offset, cm): just above the glass (deeper than an open pit). */
    private fun buryFloor(a: Int): Float = min(floorH[a], (GLASS_Y + FLOOR_MARGIN - baseY((a / nx + 0.5) * cellZ)).toFloat())

    /** Starts a burial for [owner] (see [buryStep]); null when [canBury] says no. */
    fun startBurial(owner: String, hx: Double, hz: Double, ux0: Double, uz0: Double, len: Double, width: Double, thick: Double, cover: Double): BuriedBody? {
        val l = sqrt(ux0 * ux0 + uz0 * uz0)
        val ux = if (l > 1e-9) ux0 / l else 1.0; val uz = if (l > 1e-9) uz0 / l else 0.0
        if (!canBury(hx, hz, ux, uz, len, width, thick, cover)) return null
        buried[owner]?.let { finishEmergence(it) }
        val (ids, ws) = slot(hx, hz, ux, uz, len, width)
        val cxm = hx - ux * 0.5 * len; val czm = hz - uz * 0.5 * len
        val b = BuriedBody(owner, hx, hz, ux, uz, len, width, thick, cover, ids, ws, baseY(czm) + heightAt(cxm, czm) - thick - cover)
        buried[owner] = b
        dives++
        version++
        return b
    }

    /**
     * The body works [b] down to [digTo] (share of the trench, 0..1) and pulls the sand back over itself up to
     * [coverTo] (0..1). Digging lowers the slot and throws the sand to the rim (half ahead, a quarter to each side) while
     * the body's own volume goes in under the surface; covering takes it back from the rim onto the body. Exact sand
     * transfer: nothing made or lost. Returns the sand moved this step (cm³), for the plume.
     */
    fun buryStep(b: BuriedBody, digTo: Double, coverTo: Double): Double {
        var moved = 0.0
        val dDig = (digTo.coerceIn(0.0, 1.0) - b.dug).coerceAtLeast(0.0)
        if (dDig > 0) {
            val depth = b.thickCm + b.coverCm
            var taken = 0.0
            for ((j, a) in b.cells.withIndex()) {
                val want = (dDig * depth * b.w[j]).toFloat()
                val t = min(want, max(0f, height[a] - buryFloor(a)))
                height[a] -= t; taken += t * cellArea
                fresh[a] = max(fresh[a], 0.9f)
                val add = (dDig * b.thickCm * b.w[j]).toFloat()
                inclusion[a] += add; b.added[j] += add
            }
            anyInclusion = true
            val share = doubleArrayOf(0.5, 0.25, 0.25)
            for (k in 0 until 3) { depositExact(b.rimX(k), b.rimZ(k), taken * share[k], 1.0); b.rim[k] += taken * share[k] }
            b.dug += dDig
            moved += taken
        }
        val dCov = (coverTo.coerceIn(0.0, 1.0) - b.covered).coerceAtLeast(0.0)
        if (dCov > 0) {
            var wsum = 0.0
            for (wj in b.w) wsum += wj
            var want = 0.0
            for (wj in b.w) want += dCov * b.coverCm * wj * cellArea
            var got = 0.0
            val rimTotal = b.rim.sum().coerceAtLeast(1e-9)
            for (k in 0 until 3) {
                val t = takeExact(b.rimX(k), b.rimZ(k), min(b.rim[k], want * b.rim[k] / rimTotal), 1.0)
                b.rim[k] -= t; got += t
            }
            for ((j, a) in b.cells.withIndex()) { height[a] += (got * b.w[j] / wsum / cellArea).toFloat(); fresh[a] = max(fresh[a], 0.8f) }
            b.covered += dCov
            moved += got
        }
        if (moved > 0) { movedCm3 += moved; version++ }
        return moved
    }

    /**
     * The body pushes out of the sand: its volume leaves the slot, and the cover over its head end is thrown aside
     * (a dimple with a low rim, [burstShare] of the cover). The dimple then relaxes like any pit. Returns sand moved.
     */
    fun emergeFrom(owner: String, burstShare: Double = 0.5): Double {
        val b = buried.remove(owner) ?: return 0.0
        for ((j, a) in b.cells.withIndex()) inclusion[a] = max(0f, inclusion[a] - b.added[j])
        anyInclusion = buried.isNotEmpty() && inclusion.any { it > 0f }
        if (!anyInclusion) inclusion.fill(0f)
        val r = max(1.0, 0.6 * b.widthCm + 0.5)
        val v = takeExact(b.headX - b.ux * 0.25 * b.lengthCm, b.headZ - b.uz * 0.25 * b.lengthCm, burstShare * b.coverCm * PI * r * r * 0.5, r)
        if (v > 0) {
            depositExact(b.headX - b.uz * 1.6 * r, b.headZ + b.ux * 1.6 * r, 0.5 * v, r)
            depositExact(b.headX + b.uz * 1.6 * r, b.headZ - b.ux * 1.6 * r, 0.5 * v, r)
        }
        emergences++
        version++
        return v
    }

    private fun finishEmergence(b: BuriedBody) { emergeFrom(b.owner, 0.0) }

    // ============================================================== snapshot
    private var cached: SandGridSnapshot? = null

    /** Grid copy for the renderer; reused until the grid changes. */
    fun gridSnapshot(): SandGridSnapshot {
        val c = cached
        if (c != null && c.version == version) return c
        // the renderer draws the visible surface: sand plus any body lying under it
        val h = height.copyOf()
        if (anyInclusion) for (a in h.indices) h[a] += inclusion[a]
        return SandGridSnapshot(nx, nz, cellX.toFloat(), cellZ.toFloat(), h, fresh.copyOf(), detritus.copyOf(), version,
            if (rockAttached) cavityArray() else FloatArray(0)).also { cached = it }
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

        // ---- rock roof and burrows (SIMULATION §16.8) ----
        /** Roof marker: open water over the cell. */
        const val OPEN = Float.POSITIVE_INFINITY
        /** Roof marker: the rock reaches the glass in this cell (nothing to dig under). */
        const val SOLID = Float.NEGATIVE_INFINITY
        /** Bottom glass (cm) and the sand a digger leaves on it. */
        const val GLASS_Y = 0.0
        const val FLOOR_MARGIN = 0.5
        /** Rock lower than this over the modelled bed (cm) is a roof; higher is open water a fish swims under. */
        @Ev(Evidence.DESIGN_ESTIMATE) const val ROOF_RANGE = 8.0
        const val ROOF_STEP = 0.1
        const val REACH_STEP = 0.4
        /** A body fits where its midline is at least this × min(body height, body width) clear of the rock. */
        const val FIT_CLEAR = 0.45
        /**
         * Sand under rock, held by the roof and bound with the burrower's mucus, stands this steep (tan ≈ 63°) before it
         * slumps; open sand keeps [REPOSE_TAN].
         */
        @Ev(Evidence.DESIGN_ESTIMATE) const val CONFINED_REPOSE_TAN = 2.0
        /**
         * Burrows under rock even out with their walls with this time constant at 1× flow (min): a dug tunnel loses about
         * a third of its depth in three days unless it is maintained ("constantly dig and re-dig", hobbyist accounts).
         */
        @Ev(Evidence.DESIGN_ESTIMATE) const val INFILL_TAU_MIN = 4320.0
    }
}

/**
 * A burrow under the rock (SIMULATION §16.8): a straight tunnel from the [mouthX]/[mouthZ] point at the rock edge along
 * ([dirX], [dirZ]) for [lengthCm]. The gap is not stored: it is the live sand under the rock ([SandBed.reach]).
 */
class Burrow(val owner: String, val mouthX: Double, val mouthZ: Double, val dirX: Double, val dirZ: Double, var lengthCm: Double, val primary: Boolean) {
    var mouthfuls = 0L
    fun x(s: Double) = mouthX + dirX * s
    fun z(s: Double) = mouthZ + dirZ * s
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
