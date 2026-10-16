package com.reeflive.core.sim

import kotlin.math.max
import kotlin.math.min

/**
 * The collision body of a species: its cross-section along the spine, measured from the rendered model (SIMULATION
 * §4.3). Models are unit length (the renderer scales them by the body length, `uScale = lengthCm`), snout at the head
 * station, so every value here is a fraction of the body length (BL).
 *
 * A station at spine coordinate [u] (0 snout … 1 tail tip; negative = ahead of the head point, for the crab and the
 * brittle star whose models are centred on it) has the body's top and bottom (model +Y, relative to the spine; fins
 * included except the pectorals, which fold against the body) and its half-width (model Z). Between stations the
 * values are interpolated, so the body is a chain of elliptic-section capsules that follows the bending spine.
 */
class BodyShape(val u: DoubleArray, val top: DoubleArray, val bottom: DoubleArray, val halfWidth: DoubleArray) {
    init { require(u.size >= 2 && top.size == u.size && bottom.size == u.size && halfWidth.size == u.size) }

    /** Distance (BL) from the spine down to the lowest point of the body (0 for animals whose sole is the spine). */
    val sole: Double = max(0.0, -bottom.min())
    /** Largest distance (BL) of any body point from the spine (bounding radius of the cross-section). */
    val maxRadius: Double = u.indices.maxOf { max(max(top[it], -bottom[it]), halfWidth[it]) }
    val uMin: Double get() = u.first()
    val uMax: Double get() = u.last()

    private fun idx(x: Double): Pair<Int, Double> {
        if (x <= u[0]) return 0 to 0.0
        if (x >= u[u.size - 1]) return u.size - 2 to 1.0
        var i = 0
        while (i < u.size - 2 && x > u[i + 1]) i++
        return i to ((x - u[i]) / (u[i + 1] - u[i]))
    }

    fun topAt(x: Double): Double { val (i, t) = idx(x); return top[i] + (top[i + 1] - top[i]) * t }
    fun bottomAt(x: Double): Double { val (i, t) = idx(x); return bottom[i] + (bottom[i + 1] - bottom[i]) * t }
    fun halfWidthAt(x: Double): Double { val (i, t) = idx(x); return halfWidth[i] + (halfWidth[i + 1] - halfWidth[i]) * t }

    /** top / bottom / half-width at [n] stations evenly spaced from [uMin] to [uMax] (cached: the body refreshes every tick). */
    fun stations(n: Int): Array<DoubleArray> {
        stationCache?.let { if (it[0].size == n) return it }
        val c = arrayOf(DoubleArray(n), DoubleArray(n), DoubleArray(n))
        for (i in 0 until n) {
            val x = uMin + (uMax - uMin) * i / (n - 1)
            c[0][i] = topAt(x); c[1][i] = bottomAt(x); c[2][i] = halfWidthAt(x)
        }
        stationCache = c
        return c
    }
    @Volatile private var stationCache: Array<DoubleArray>? = null

    companion object {
        fun of(vararg st: Double): BodyShape {
            require(st.size % 4 == 0)
            val n = st.size / 4
            return BodyShape(DoubleArray(n) { st[it * 4] }, DoubleArray(n) { st[it * 4 + 1] }, DoubleArray(n) { st[it * 4 + 2] }, DoubleArray(n) { st[it * 4 + 3] })
        }
    }
}

/**
 * Body shapes per animal, the single source for collision sizes. The tables were measured from the GLB models in
 * `app/src/main/assets/models` (vertices binned by spine coordinate `u = 0.5 − x`; body, shell, foot and median /
 * pelvic / caudal fins; pectorals, eyes, antennae and claws left out; legs count for the height only). Station tuples
 * are (u, top, bottom, half-width) in BL. Animals without a model here get an ellipse from the species' depth ratio.
 */
object BodyShapes {
    private val TABLE: Map<BrainKind, BodyShape> = mapOf(
        BrainKind.CLOWN to BodyShape.of(0.00, 0.088, -0.118, 0.049, 0.10, 0.149, -0.159, 0.064, 0.20, 0.226, -0.180, 0.067, 0.30, 0.291, -0.224, 0.067, 0.40, 0.291, -0.210, 0.067, 0.50, 0.278, -0.228, 0.063, 0.60, 0.249, -0.228, 0.056, 0.70, 0.223, -0.206, 0.044, 0.80, 0.089, -0.089, 0.031, 0.90, 0.109, -0.109, 0.021, 1.00, 0.107, -0.107, 0.010),
        BrainKind.TANG to BodyShape.of(0.00, 0.084, -0.076, 0.031, 0.10, 0.173, -0.166, 0.050, 0.20, 0.263, -0.201, 0.056, 0.30, 0.291, -0.230, 0.056, 0.40, 0.292, -0.280, 0.056, 0.50, 0.292, -0.281, 0.056, 0.60, 0.280, -0.271, 0.051, 0.70, 0.247, -0.241, 0.041, 0.80, 0.065, -0.065, 0.029, 0.90, 0.107, -0.107, 0.010, 1.00, 0.123, -0.123, 0.010),
        BrainKind.CARDINAL to BodyShape.of(0.00, 0.116, -0.079, 0.029, 0.10, 0.162, -0.153, 0.043, 0.20, 0.180, -0.180, 0.046, 0.30, 0.319, -0.211, 0.046, 0.40, 0.319, -0.259, 0.047, 0.50, 0.222, -0.233, 0.044, 0.60, 0.290, -0.250, 0.039, 0.70, 0.324, -0.102, 0.031, 0.80, 0.074, -0.074, 0.019, 0.90, 0.122, -0.122, 0.010, 1.00, 0.137, -0.137, 0.010),
        BrainKind.WRASSE to BodyShape.of(0.00, 0.041, -0.041, 0.033, 0.10, 0.079, -0.079, 0.051, 0.20, 0.131, -0.087, 0.054, 0.30, 0.139, -0.123, 0.056, 0.40, 0.140, -0.100, 0.057, 0.50, 0.139, -0.128, 0.055, 0.60, 0.132, -0.127, 0.049, 0.70, 0.119, -0.113, 0.041, 0.80, 0.101, -0.094, 0.030, 0.90, 0.074, -0.074, 0.015, 1.00, 0.087, -0.087, 0.010),
        // leopard wrasse (Macropharyngodon): the cleaner wrasse's profile at its deeper body (depth 0.36 vs 0.22 BL, [I]);
        // its night sand-dive is posture 5, exempt
        BrainKind.LEOPARD_WRASSE to BodyShape.of(0.000, 0.067, -0.067, 0.041, 0.100, 0.129, -0.129, 0.064, 0.200, 0.214, -0.142, 0.068, 0.300, 0.227, -0.201, 0.070, 0.400, 0.229, -0.164, 0.071, 0.500, 0.227, -0.209, 0.069, 0.600, 0.216, -0.208, 0.061, 0.700, 0.195, -0.185, 0.051, 0.800, 0.165, -0.154, 0.037, 0.900, 0.121, -0.121, 0.019, 1.000, 0.142, -0.142, 0.013),
        BrainKind.ENGINEER_GOBY to BodyShape.of(0.00, 0.038, -0.038, 0.023, 0.10, 0.044, -0.057, 0.027, 0.20, 0.046, -0.046, 0.039, 0.30, 0.075, -0.046, 0.041, 0.40, 0.075, -0.046, 0.035, 0.50, 0.073, -0.064, 0.036, 0.60, 0.070, -0.064, 0.036, 0.70, 0.065, -0.060, 0.032, 0.80, 0.057, -0.052, 0.022, 0.90, 0.043, -0.039, 0.024, 1.00, 0.010, -0.010, 0.019),
        BrainKind.HAWK to BodyShape.of(0.00, 0.052, -0.087, 0.037, 0.10, 0.119, -0.128, 0.055, 0.20, 0.219, -0.147, 0.060, 0.30, 0.265, -0.207, 0.060, 0.40, 0.265, -0.207, 0.059, 0.50, 0.259, -0.177, 0.056, 0.60, 0.200, -0.190, 0.048, 0.70, 0.181, -0.175, 0.039, 0.80, 0.071, -0.071, 0.029, 0.90, 0.094, -0.094, 0.021, 1.00, 0.103, -0.103, 0.010),
        BrainKind.LAWNMOWER_BLENNY to BodyShape.of(0.00, 0.137, -0.082, 0.059, 0.10, 0.162, -0.120, 0.066, 0.20, 0.173, -0.108, 0.066, 0.30, 0.170, -0.090, 0.064, 0.40, 0.163, -0.096, 0.061, 0.50, 0.152, -0.130, 0.052, 0.60, 0.147, -0.124, 0.044, 0.70, 0.134, -0.111, 0.033, 0.80, 0.119, -0.091, 0.023, 0.90, 0.088, -0.088, 0.013, 1.00, 0.088, -0.088, 0.010),
        BrainKind.PEEKER_BLENNY to BodyShape.of(0.00, 0.079, -0.061, 0.053, 0.10, 0.104, -0.104, 0.053, 0.20, 0.147, -0.104, 0.053, 0.30, 0.146, -0.075, 0.052, 0.40, 0.142, -0.074, 0.049, 0.50, 0.135, -0.118, 0.045, 0.60, 0.129, -0.116, 0.039, 0.70, 0.117, -0.104, 0.031, 0.80, 0.101, -0.085, 0.022, 0.90, 0.080, -0.080, 0.013, 1.00, 0.080, -0.080, 0.010),
        BrainKind.DOTTYBACK to BodyShape.of(0.00, 0.052, -0.058, 0.031, 0.10, 0.092, -0.092, 0.043, 0.20, 0.145, -0.118, 0.045, 0.30, 0.149, -0.148, 0.045, 0.40, 0.150, -0.146, 0.044, 0.50, 0.150, -0.147, 0.041, 0.60, 0.142, -0.142, 0.036, 0.70, 0.129, -0.126, 0.026, 0.80, 0.094, -0.090, 0.019, 0.90, 0.053, -0.053, 0.010, 1.00, 0.039, -0.039, 0.010),
        BrainKind.DIAMOND_GOBY to BodyShape.of(0.00, 0.053, -0.066, 0.045, 0.10, 0.082, -0.082, 0.054, 0.20, 0.106, -0.120, 0.054, 0.30, 0.179, -0.136, 0.053, 0.40, 0.172, -0.099, 0.050, 0.50, 0.152, -0.137, 0.045, 0.60, 0.141, -0.128, 0.038, 0.70, 0.124, -0.111, 0.029, 0.80, 0.038, -0.038, 0.019, 0.90, 0.071, -0.071, 0.013, 1.00, 0.070, -0.070, 0.010),
        BrainKind.BANDED_SHRIMP to BodyShape.of(0.00, 0.067, -0.093, 0.009, 0.10, 0.105, -0.174, 0.052, 0.20, 0.111, -0.136, 0.085, 0.30, 0.128, -0.081, 0.096, 0.40, 0.127, -0.142, 0.104, 0.50, 0.120, -0.162, 0.104, 0.60, 0.098, -0.228, 0.081, 0.70, 0.057, -0.228, 0.061, 0.80, 0.014, -0.215, 0.053, 0.90, -0.058, -0.298, 0.079, 1.00, -0.250, -0.298, 0.010),
        BrainKind.CLEANER_SHRIMP to BodyShape.of(0.00, 0.055, 0.034, 0.007, 0.10, 0.064, -0.165, 0.034, 0.20, 0.089, -0.165, 0.054, 0.30, 0.102, -0.065, 0.059, 0.40, 0.107, -0.117, 0.060, 0.50, 0.108, -0.216, 0.059, 0.60, 0.107, -0.216, 0.056, 0.70, 0.082, -0.216, 0.050, 0.80, 0.043, -0.151, 0.042, 0.90, -0.037, -0.218, 0.075, 1.00, -0.212, -0.229, 0.039),
        BrainKind.HERMIT to BodyShape.of(0.00, 0.04, 0.0, 0.02, 0.10, 0.05, 0.0, 0.02, 0.20, 0.066, 0.0, 0.02, 0.30, 0.218, 0.0, 0.05, 0.40, 0.292, 0.0, 0.088, 0.50, 0.382, 0.0, 0.224, 0.60, 0.522, 0.0, 0.339, 0.70, 0.640, 0.0, 0.376, 0.80, 0.651, 0.0, 0.375, 0.90, 0.628, 0.0, 0.336, 1.00, 0.493, 0.0, 0.183),
        // limb-rigged: the body is centred on the head point (u < 0 lies ahead of it); legs and claws are posed by the rig
        // the carapace only: the measured box (−0.4…0.4, half-width to 0.49 L) is the leg span, and the rig plants the
        // feet on the surface; the carapace rides a little above the feet plane
        BrainKind.SALLY_CRAB to BodyShape.of(-0.30, 0.200, 0.10, 0.200, -0.15, 0.300, 0.06, 0.300, 0.00, 0.301, 0.06, 0.300, 0.15, 0.300, 0.06, 0.300, 0.30, 0.200, 0.10, 0.200),
        BrainKind.BRITTLE_STAR to BodyShape.of(-0.10, 0.024, 0.0, 0.019, 0.00, 0.032, 0.0, 0.059, 0.10, 0.024, 0.0, 0.039),
        BrainKind.NASSARIUS to BodyShape.of(0.00, 0.076, 0.0, 0.056, 0.10, 0.076, 0.0, 0.169, 0.20, 0.290, 0.0, 0.126, 0.30, 0.653, 0.0, 0.279, 0.40, 0.742, 0.0, 0.343, 0.50, 0.774, 0.0, 0.343, 0.60, 0.792, 0.0, 0.343, 0.70, 0.749, 0.0, 0.343, 0.80, 0.677, 0.0, 0.239, 0.90, 0.642, 0.0, 0.193, 1.00, 0.581, 0.0, 0.115),
        BrainKind.ASTREA to BodyShape.of(0.00, 0.180, 0.0, 0.221, 0.10, 0.292, 0.0, 0.471, 0.20, 0.451, 0.0, 0.392, 0.30, 0.553, 0.0, 0.459, 0.40, 0.701, 0.0, 0.497, 0.50, 0.770, 0.0, 0.510, 0.60, 0.701, 0.0, 0.587, 0.70, 0.553, 0.0, 0.459, 0.80, 0.451, 0.0, 0.399, 0.90, 0.292, 0.0, 0.318, 1.00, 0.180, 0.0, 0.261),
        BrainKind.ASTERINA to BodyShape.of(0.00, 0.012, 0.0, 0.075, 0.10, 0.012, 0.0, 0.116, 0.20, 0.065, 0.0, 0.391, 0.30, 0.104, 0.0, 0.430, 0.40, 0.104, 0.0, 0.374, 0.50, 0.111, 0.0, 0.298, 0.60, 0.104, 0.0, 0.389, 0.70, 0.065, 0.0, 0.441, 0.80, 0.065, 0.0, 0.393, 0.90, 0.012, 0.0, 0.058),
    )

    private val cache = HashMap<String, BodyShape>()

    /** The body of [s] (species-level: a new species with its own brain falls back to its depth ratio). */
    /**
     * Shapes keyed by species id, measured from the species' JSON `body` block.
     * Hummingbird bobtail squid: mantle and head capsule from euprymna_berryi.json `body` (arms bundled at u 0–0.15);
     * its daytime sand burial is posture 5, which the collision already exempts.
     */
    private val BY_ID: Map<String, BodyShape> = mapOf(
        "euprymna_berryi" to BodyShape.of(
            0.0, 0.02, -0.02, 0.03,
            0.15, 0.05, -0.05, 0.06,
            0.3, 0.084, -0.061, 0.091,
            0.4, 0.096, -0.119, 0.1,
            0.5, 0.164, -0.143, 0.176,
            0.6, 0.182, -0.16, 0.198,
            0.7, 0.182, -0.162, 0.199,
            0.8, 0.168, -0.155, 0.186,
            0.9, 0.135, -0.129, 0.151,
            1.0, 0.055, -0.062, 0.065,
        ),
    )

    fun of(s: SimSpecies): BodyShape {
        BY_ID[s.id]?.let { return it }
        if (s.brainKind == BrainKind.BOBTAIL_SQUID) BY_ID["euprymna_berryi"]?.let { return it }
        TABLE[s.brainKind]?.let { return it }
        return synchronized(cache) { cache.getOrPut(s.id) { fallback(s) } }
    }

    /**
     * Fusiform body from the species sheet: half-depth = depthRatio / 2 at mid-body tapering to the head and the
     * peduncle, width a third of the depth (a typical reef fish), plus a caudal fin; inverts sit on their sole.
     */
    fun fallback(s: SimSpecies): BodyShape {
        val d = (s.depthRatio.takeIf { it > 0 } ?: 0.3) / 2
        val us = doubleArrayOf(0.0, 0.1, 0.2, 0.35, 0.5, 0.65, 0.8, 0.9, 1.0)
        val k = doubleArrayOf(0.35, 0.7, 0.92, 1.0, 0.97, 0.8, 0.45, 0.4, 0.45)
        return if (s.brainKind.isFish) BodyShape(us, DoubleArray(us.size) { d * k[it] }, DoubleArray(us.size) { -d * k[it] }, DoubleArray(us.size) { min(d, 0.06) * k[it] * 0.9 })
        else BodyShape(us, DoubleArray(us.size) { 2 * d * k[it] }, DoubleArray(us.size) { 0.0 }, DoubleArray(us.size) { d * k[it] })
    }
}
