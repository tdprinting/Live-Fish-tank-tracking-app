package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.Anchor
import com.reeflive.core.sim.RockData
import kotlin.math.abs

/**
 * Rock-top perches for perch-and-hop fish (research 30 §3). The baked PERCH anchors are kept (their numbered aliases,
 * which sit on the same spots, are dropped), and more exposed rock tops are found once, at start-up, by tracing down
 * from the surface on a coarse grid. A real hawkfish rotates through many perches across its
 * range (Coker et al. 2017: 7.6–9.1 perch changes per 10 min) rather than ping-ponging between two.
 */
object PerchSites {
    /** Grid step for the top-down trace, cm. */
    private const val STEP_CM = 5.0
    /** Least spacing between two perches, cm. */
    const val SPACING_CM = 9.0
    /** Most perches kept (anchors first). */
    const val MAX = 22
    /** Least depth of a rock-top perch under the water line, cm. */
    const val SURFACE_GAP_CM = 10.0

    fun build(rock: RockData, sandY: (Double, Double) -> Double, named: List<Anchor>): List<Anchor> {
        val out = ArrayList<Anchor>()
        for (a in named) if (out.none { it.pos.distanceTo(a.pos) < 1.0 }) out.add(a)
        val sdf = rock.sdf
        val cand = ArrayList<Anchor>()
        var x = STEP_CM
        while (x < rock.lengthCm - STEP_CM) {
            var z = STEP_CM
            while (z < rock.widthCm - STEP_CM) {
                top(rock, x, z)?.let { (p, n) ->
                    // a real rock top (not the sand bed), facing up, with water above for the fish to sit in
                    // well under the water line and off the glass, so a fish sitting head-up on it stays wet and clear
                    if (p.y > sandY(p.x, p.z) + 4.0 && p.y < rock.waterLevelCm - SURFACE_GAP_CM && n.y > 0.55 &&
                        sdf.dist(p + n * 4.0) > 3.0 && rock.inWater(p + n * 2.0, 6.0))
                        cand.add(Anchor("PERCH_TOP_${cand.size}", "PERCH", p, normal = n))
                }
                z += STEP_CM
            }
            x += STEP_CM
        }
        // higher, more exposed tops first (a hawkfish sits where it can see), then spread them out
        cand.sortByDescending { it.pos.y }
        for (a in cand) {
            if (out.size >= MAX) break
            if (out.all { it.pos.distanceTo(a.pos) >= SPACING_CM }) out.add(a)
        }
        return out
    }

    /** Sphere-trace straight down from just under the surface; the first rock contact and its normal, or null. */
    private fun top(rock: RockData, x: Double, z: Double): Pair<Vec3, Vec3>? {
        val sdf = rock.sdf
        var p = Vec3(x, rock.waterLevelCm - 2.0, z)
        var k = 0
        while (k++ < 64 && p.y > rock.sandTopCm) {
            val d = sdf.dist(p)
            if (d < 0.25) {
                val q = sdf.project(p, 0.0)
                if (abs(sdf.dist(q)) > 0.3) return null
                return q to sdf.gradient(q)
            }
            p = p.withY(p.y - maxOf(0.3, d))
        }
        return null
    }

    /** Still a rock surface (the rocks can shift in the sandbox). */
    fun valid(rock: RockData, a: Anchor): Boolean = abs(rock.sdf.dist(a.pos)) < 0.8
}
