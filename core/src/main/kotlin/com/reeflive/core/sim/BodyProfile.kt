package com.reeflive.core.sim

import com.reeflive.core.geom.BodySpline
import com.reeflive.core.geom.Vec3
import kotlin.math.max
import kotlin.math.sqrt

/**
 * An animal's real body around its spine, measured from its model (SIMULATION §24.3, baked by
 * tools/collision/bake_body_profiles.py into [BODY_PROFILE_DATA]). The models are one body length long and the renderer
 * scales them by the body length, so every value is in body lengths (BL).
 *
 * At each of the [BodySpline.POINTS] spine stations the body is an elliptic section ([top] and [bottom] along the
 * station's up axis, [halfWidth] along its side axis), with the median fins as a thin blade from [finTop] to [finBottom]
 * in the up axis. [support] gives how far the body reaches from the spine point in a direction: a body out of the rock is
 * one whose every station is further from the rock than its support toward the rock.
 *
 * Animals drawn rigid at their organism (crabs, hermits, snails, the urchin, the scallop, the brittle star's disc) also
 * carry [hull]: the extreme points of the shell and body in model space.
 */
class BodyProfile(private val d: FloatArray, val hull: FloatArray) {
    init { require(d.size == N * 5) }

    fun top(i: Int): Double = d[i * 5].toDouble()
    fun bottom(i: Int): Double = d[i * 5 + 1].toDouble()
    fun halfWidth(i: Int): Double = d[i * 5 + 2].toDouble()
    fun finTop(i: Int): Double = d[i * 5 + 3].toDouble()
    fun finBottom(i: Int): Double = d[i * 5 + 4].toDouble()

    /** Largest reach (BL) of the body (fins at [FIN_GIVE]) from any station, for quick rejects. */
    val maxReach: Double = (0 until N).maxOf { i ->
        max(max(top(i) + (finTop(i) - top(i)) * FIN_GIVE, -(bottom(i) + (finBottom(i) - bottom(i)) * FIN_GIVE)), halfWidth(i))
    }

    /**
     * How far (BL) station [i]'s body reaches from its spine point along a direction whose components on the station's
     * up and side axes are [vu] and [vs]: the elliptic section's support `c·vu + √(a²vu² + b²vs²)`, or the median fins
     * (folded to [FIN_GIVE] of their height: fish fold them against what they touch) where they reach further.
     */
    fun support(i: Int, vu: Double, vs: Double): Double {
        val t = top(i); val b = bottom(i)
        val c = 0.5 * (t + b); val a = 0.5 * (t - b); val w = halfWidth(i)
        val body = c * vu + sqrt(a * a * vu * vu + w * w * vs * vs)
        val fin = if (vu >= 0.0) vu * (t + (finTop(i) - t) * FIN_GIVE) else vu * (b + (finBottom(i) - b) * FIN_GIVE)
        return max(body, fin)
    }

    companion object {
        const val N = BodySpline.POINTS
        /** Share of the median fins' height that counts as body (the rest folds on contact). */
        const val FIN_GIVE = 0.5

        /** Test species sheets name a few models by their old file names. */
        private val ALIASES = mapOf("models/hermit.glb" to "models/hermit_blue.glb")

        private val cache = HashMap<String, BodyProfile?>()

        /**
         * [o]'s body along its spine: null when it is drawn rigid at its organism, has no measured model, or is an
         * invertebrate walking a surface (its crawl, seat and leg rig hold that body to the surface).
         */
        fun spineOf(o: Organism): BodyProfile? = if (o.drawnRigid || (o.crawling && !o.isFish)) null else of(o.species)

        /** The measured body of [s] (null for a species whose model has no profile: sessile animals, corals). */
        fun of(s: SimSpecies): BodyProfile? {
            val m = s.model ?: return null
            return cache.getOrPut(m) {
                val key = ALIASES[m] ?: m
                val d = BODY_PROFILE_DATA[key] ?: return@getOrPut null
                BodyProfile(d, BODY_HULL_DATA[key] ?: FloatArray(0))
            }
        }
    }
}

/**
 * The drawn body frame at a spine station (as [BodySpline.sampleInto] builds it: forward along the spine, up from the
 * organism's up, side = forward × up), and how far the body reaches from the station along a direction. Mutable
 * scratch, so the per-tick checks allocate nothing.
 */
class StationFrame {
    var ux = 0.0; var uy = 1.0; var uz = 0.0
    var sx = 0.0; var sy = 0.0; var sz = 1.0

    fun set(o: Organism, pos: Array<Vec3>, i: Int) {
        val n = pos.size
        val a = pos[if (i == 0) 0 else i - 1]; val b = pos[if (i == n - 1) n - 1 else i + 1]
        var fx = a.x - b.x; var fy = a.y - b.y; var fz = a.z - b.z
        val fl = sqrt(fx * fx + fy * fy + fz * fz)
        if (fl < 1e-9) { fx = o.heading.x; fy = o.heading.y; fz = o.heading.z } else { fx /= fl; fy /= fl; fz /= fl }
        val u0 = o.up
        val d = u0.x * fx + u0.y * fy + u0.z * fz
        ux = u0.x - fx * d; uy = u0.y - fy * d; uz = u0.z - fz * d
        val ul = sqrt(ux * ux + uy * uy + uz * uz)
        if (ul < 1e-6) { ux = 0.0; uy = 1.0; uz = 0.0 } else { ux /= ul; uy /= ul; uz /= ul }
        sx = fy * uz - fz * uy; sy = fz * ux - fx * uz; sz = fx * uy - fy * ux
    }

    /** Reach (cm) of station [i] of [prof] scaled to [lengthCm] along the unit direction (vx, vy, vz). */
    fun reach(prof: BodyProfile, i: Int, lengthCm: Double, vx: Double, vy: Double, vz: Double): Double =
        prof.support(i, ux * vx + uy * vy + uz * vz, sx * vx + sy * vy + sz * vz) * lengthCm
}
