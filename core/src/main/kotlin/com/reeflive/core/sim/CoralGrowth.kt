package com.reeflive.core.sim

import com.reeflive.core.geom.Rng
import com.reeflive.core.geom.Vec3
import com.reeflive.core.geom.smoothstep
import java.util.PriorityQueue
import java.util.WeakHashMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The rock surface an encrusting colony can reach from its anchor (research 03 C2: "GSP mat rarely crosses a sand gap
 * > 5 cm"). Cells of a [CELL] cm grid lying on the rock surface above the sand are linked to their neighbours; cells at
 * the foot of the rock are also linked across the sand when the gap is at most [SAND_GAP] cm. [dist] holds each cell's
 * shortest path along that surface from the anchor, so a mat of radius r covers every cell with dist ≤ r.
 */
class SurfacePatch private constructor(val cells: List<Vec3>, val dist: DoubleArray, val maxRadius: Double) {
    private val sorted = dist.filter { it.isFinite() }.sorted().toDoubleArray()

    /** Farthest reachable surface from the anchor (the mat stops growing there), cm. */
    val maxReach: Double = sorted.lastOrNull() ?: 0.0

    /** Surface reached by a mat of geodesic radius [r], cm². */
    fun area(r: Double): Double = upperBound(r) * CELL * CELL

    /** Everything reachable within [maxRadius]. */
    val reachableArea: Double get() = sorted.size * CELL * CELL

    /** Geodesic distance from the anchor to the surface under [p] (∞ when [p] is not on reachable rock). */
    fun distTo(p: Vec3, tolerance: Double = 2.5): Double {
        var best = Double.POSITIVE_INFINITY
        var bestD = tolerance * tolerance
        for (i in cells.indices) {
            val d2 = cells[i].distanceSqTo(p)
            if (d2 <= bestD) { bestD = d2; best = dist[i] }
        }
        return best
    }

    private fun upperBound(r: Double): Int {
        var lo = 0; var hi = sorted.size
        while (lo < hi) { val m = (lo + hi) ushr 1; if (sorted[m] <= r) lo = m + 1 else hi = m }
        return lo
    }

    companion object {
        const val CELL = 1.5
        const val SAND_GAP = 5.0
        private val cache = WeakHashMap<RockData, HashMap<String, SurfacePatch?>>()

        /** Cached per rockscape and anchor position (time-lapse forks and tests reuse it). */
        fun of(rock: RockData, from: Vec3, maxRadius: Double): SurfacePatch? = synchronized(cache) {
            val key = "%.2f,%.2f,%.2f,%.1f".format(from.x, from.y, from.z, maxRadius)
            cache.getOrPut(rock) { HashMap() }.getOrPut(key) { build(rock, from, maxRadius) }
        }

        fun build(rock: RockData, from: Vec3, maxRadius: Double): SurfacePatch? {
            val h = CELL
            val n = (maxRadius / h).toInt() + 2
            val x0 = from.x - n * h; val y0 = max(from.y - n * h, rock.sandTopCm); val z0 = from.z - n * h
            val nx = 2 * n + 1; val ny = ((from.y + n * h - y0) / h).toInt() + 1; val nz = 2 * n + 1
            val index = IntArray(nx * ny * nz) { -1 }
            val cells = ArrayList<Vec3>()
            for (ix in 0 until nx) for (iy in 0 until ny) for (iz in 0 until nz) {
                val p = Vec3(x0 + ix * h, y0 + iy * h, z0 + iz * h)
                if (p.y < rock.sandTopCm + 0.3 || !rock.inWater(p) || p.distanceTo(from) > maxRadius + h) continue
                val d = rock.sdf.dist(p)
                if (d < -0.75 || d > 1.0) continue
                index[(ix * ny + iy) * nz + iz] = cells.size
                cells += p
            }
            if (cells.isEmpty()) return null
            val start = cells.indices.minByOrNull { cells[it].distanceSqTo(from) }!!
            if (cells[start].distanceTo(from) > 4.0) return null
            val dist = DoubleArray(cells.size) { Double.POSITIVE_INFINITY }
            // sand-foot cells, for bridging short sand gaps
            val foot = cells.indices.filter { cells[it].y < rock.sandTopCm + 2.0 }
            val pq = PriorityQueue<Pair<Double, Int>>(compareBy { it.first })
            dist[start] = 0.0; pq += 0.0 to start
            while (pq.isNotEmpty()) {
                val (d, i) = pq.poll()
                if (d > dist[i] || d > maxRadius) continue
                val c = cells[i]
                val ix = Math.round((c.x - x0) / h).toInt(); val iy = Math.round((c.y - y0) / h).toInt(); val iz = Math.round((c.z - z0) / h).toInt()
                fun relax(j: Int, cost: Double) {
                    val nd = d + cost
                    if (nd < dist[j]) { dist[j] = nd; pq += nd to j }
                }
                for (dx in -1..1) for (dy in -1..1) for (dz in -1..1) {
                    if (dx == 0 && dy == 0 && dz == 0) continue
                    val jx = ix + dx; val jy = iy + dy; val jz = iz + dz
                    if (jx !in 0 until nx || jy !in 0 until ny || jz !in 0 until nz) continue
                    val j = index[(jx * ny + jy) * nz + jz]
                    if (j >= 0) relax(j, h * sqrt((dx * dx + dy * dy + dz * dz).toDouble()))
                }
                if (c.y < rock.sandTopCm + 2.0) for (j in foot) {
                    if (j == i) continue
                    val g = cells[j].horizontal().distanceTo(c.horizontal())
                    if (g > h * 1.8 && g <= SAND_GAP) relax(j, g)
                }
            }
            for (i in dist.indices) if (dist[i] > maxRadius) dist[i] = Double.POSITIVE_INFINITY
            return SurfacePatch(cells, dist, maxRadius)
        }
    }
}

/**
 * Coral growth, fragmentation and competition (coverage gaps C-1, C-2, C-8; research 03 C1-C2, D).
 *
 * Encrusting colonies (GSP, zoas, clove, mushrooms) advance a mat edge along the rock ([SurfacePatch]): GSP several
 * cm/month, a 30 cm face in 3-4 months, stopped by a sand gap over 5 cm. Kenya trees grow fast and drop branches that
 * root where they land; clove polyps are slow. Growth needs a healthy, unstressed colony and is helped by nitrate and
 * phosphate in range (GSP, 03 C1), held back by hair algae on the colony (S12) and low Sr/Ca (05 §10).
 *
 * Competition: Kenya leaks terpenoids (allelopathy, keep 10 cm away, carbon helps), zoas and palys sting on contact,
 * GSP smothers what its mat reaches. Colonies already living side by side when the tank is loaded keep the pressure
 * they had then as a baseline (they have coexisted); only pressure above that (growth, frags, new placements) acts.
 */
object CoralGrowth {
    enum class Form { ENCRUSTING, TREE, MACRO, NONE }

    /**
     * [r0] starting mat radius (cm) or canopy radius at size 1; [rate] mat edge cm/day or tree size fraction/day at full
     * vigour; [rMax] how far a mat may spread; [maxSize] tree size cap.
     */
    data class Growth(val form: Form, val r0: Double, val rate: Double, val rMax: Double = 0.0, val maxSize: Double = 1.0)

    fun growth(k: CoralKind): Growth = when (k) {
        // 03 C2: GSP "several cm per month; colonizes a 30 cm rock face in 3-4 months" → ~0.1 cm/day mat edge
        CoralKind.GSP -> Growth(Form.ENCRUSTING, 6.0, 0.10, rMax = 30.0)
        CoralKind.ZOA -> Growth(Form.ENCRUSTING, 5.0, 0.04, rMax = 20.0)                  // est.
        // 03 C2: clove "disappointingly slow" (blue clove vigorous)
        CoralKind.CLOVE -> Growth(Form.ENCRUSTING, 4.0, 0.02, rMax = 15.0)
        CoralKind.MUSHROOM -> Growth(Form.ENCRUSTING, 4.0, 0.015, rMax = 12.0)             // est.
        // 03 A1/C2: Kenya "grows very quickly" (no number): ~0.6 %/day size, sheds branches to propagate
        CoralKind.KENYA -> Growth(Form.TREE, 5.0, 0.006, maxSize = 2.5)
        CoralKind.ORGAN_PIPE -> Growth(Form.TREE, 3.0, 0.001, maxSize = 1.6)               // est.
        CoralKind.MACRO -> Growth(Form.MACRO, 4.0, 0.0)
        CoralKind.AIPTASIA -> Growth(Form.NONE, 1.0, 0.0)
        CoralKind.GENERIC -> Growth(Form.NONE, 3.0, 0.0)
    }

    /** Growth-vigour boost at full [Coral.fed] (design estimate). */

    const val FED_BOOST = 0.15

    const val KENYA_SHED_SIZE = 1.4
    const val KENYA_SHED_PER_DAY = 1.0 / 20
    const val KENYA_MAX_COLONIES = 6
    const val KENYA_RANGE_CM = 10.0
    const val STING_RANGE_CM = 3.0

    /** Footprint radius used for edge-to-edge distances, cm. */
    fun extent(c: Coral): Double {
        val g = growth(c.kind)
        return when (g.form) {
            Form.ENCRUSTING -> if (c.radiusCm > 0) c.radiusCm else g.r0
            Form.TREE, Form.MACRO -> g.r0 * c.size
            Form.NONE -> g.r0 * c.size
        }
    }

    /**
     * Biomass / calcification multiplier from colony size. Linear in size (mats: √area) rather than area: the placed
     * colonies' demand was tuned against weekly 10 % water changes, and most new mat is thin young tissue (est.).
     */
    fun mass(c: Coral): Double = c.size.coerceIn(0.0, 2.5)

    /** Growth vigour 0..1 from health, stress, light, nutrients, algae on the colony, Sr and Ca (03 C1, 05 §10). */
    fun vigor(c: Coral, w: WaterState): Double {
        if (c.tissue <= 0.05) return 0.0
        val health = (1 - c.stress) * (1 - c.stress) * c.tissue * (1 - 0.7 * c.bleach)
        val light = ((if (c.acclLight.isNaN()) 0.6 else c.acclLight) / 0.6).coerceIn(0.2, 1.2)
        val nut = when {
            w.no3 < 0.5 && w.po4 < 0.01 -> 0.5
            c.kind == CoralKind.GSP -> 0.7 + 0.3 * smoothstep(2.0, 15.0, w.no3) * smoothstep(0.01, 0.06, w.po4)
            else -> 0.85 + 0.15 * smoothstep(1.0, 10.0, w.no3)
        }
        val algae = 1 - c.algaeCover
        val sr = 0.7 + 0.3 * ((w.sr - 4) / 3).coerceIn(0.0, 1.0)
        val ca = if (c.kind == CoralKind.ORGAN_PIPE) WaterSim.calcificationFactor(w) else 0.9 + 0.1 * ((w.ca - 300) / 100).coerceIn(0.0, 1.0)
        // target feeding (coral powder, cyclops, plankton) adds a little heterotrophic growth (design estimate, ≤ +15 %)
        val fed = 1.0 + FED_BOOST * c.fed.coerceIn(0.0, 1.0)
        return (health * light * nut * algae * sr * ca * fed).coerceIn(0.0, 1.2)
    }

    /**
     * One growth step of [dtMin] minutes. Returns a new Kenya branch when one drops onto rock (the caller adds it),
     * or null. [macroIndex] is the display red-macro biomass (WaterState.macro) that sets a macro colony's size.
     */
    fun step(c: Coral, w: WaterState, rock: RockData, rng: Rng, dtMin: Double, kenyaCount: Int, newId: () -> String): Coral? {
        val g = growth(c.kind)
        val days = dtMin / 1440
        val v = vigor(c, w)
        val shrink = if (c.tissue < 0.6 || c.stress > 0.7) 0.5 * (1 - c.tissue + c.stress).coerceAtMost(1.0) else 0.0
        when (g.form) {
            Form.ENCRUSTING -> {
                if (c.radiusCm <= 0) c.radiusCm = g.r0
                val patch = c.patch ?: SurfacePatch.of(rock, c.pos, g.rMax).also { c.patch = it }
                val reach = patch?.maxReach ?: g.rMax
                // smothered tissue does not grow
                c.radiusCm += g.rate * days * (v * (1 - c.overgrown) - shrink)
                c.radiusCm = c.radiusCm.coerceIn(0.5, max(g.r0, reach + SurfacePatch.CELL))
                if (patch != null) {
                    val a0 = max(patch.area(g.r0), SurfacePatch.CELL * SurfacePatch.CELL)
                    c.size = sqrt(max(patch.area(c.radiusCm), SurfacePatch.CELL * SurfacePatch.CELL) / a0)
                    c.cover = (patch.area(c.radiusCm) / patch.reachableArea.coerceAtLeast(1e-9)).coerceIn(0.0, 1.0)
                } else {
                    c.size = c.radiusCm / g.r0
                    c.cover = ((c.radiusCm * c.radiusCm) / (g.rMax * g.rMax)).coerceIn(0.0, 1.0)
                }
            }
            Form.TREE -> {
                c.size = (c.size + c.size * g.rate * days * (v * (1 - c.overgrown) - shrink)).coerceIn(0.1, g.maxSize)
                c.cover = ((c.size - 0.1) / (g.maxSize - 0.1)).coerceIn(0.0, 1.0)
                if (c.kind == CoralKind.KENYA && c.size > KENYA_SHED_SIZE && v > 0.3 && rng.chance(KENYA_SHED_PER_DAY * days)) {
                    c.size -= 0.15
                    if (kenyaCount < KENYA_MAX_COLONIES) return dropBranch(c, rock, rng, newId())
                }
            }
            Form.MACRO -> {
                c.size = sqrt((w.macro / 0.3).coerceIn(0.05, 4.0))
                c.cover = (w.macro / WaterSim.MACRO_CAP).coerceIn(0.0, 1.0)
            }
            Form.NONE -> {}
        }
        return null
    }

    /** A Kenya branch falls from the canopy edge and roots where it lands on rock; on sand it is lost (03 A1). */
    fun dropBranch(c: Coral, rock: RockData, rng: Rng, id: String): Coral? {
        val ang = rng.nextDouble() * 2 * PI
        val r = extent(c) + rng.range(1.0, 10.0)
        val start = Vec3(c.pos.x + cos(ang) * r, c.pos.y + 2.0, c.pos.z + sin(ang) * r)
        if (!rock.inWater(start, 1.0)) return null
        val hit = rock.sdf.raycast(start, Vec3(0.0, -1.0, 0.0), start.y - rock.sandTopCm) ?: return null
        val p = start + Vec3(0.0, -hit, 0.0)
        if (p.y < rock.sandTopCm + 0.5) return null
        val frag = Coral(id, c.species, "${c.nickname} branch", "", p, rock.sdf.gradient(p), rng.nextDouble() * 6.28)
        frag.size = 0.3
        frag.tissue = 0.9
        return frag
    }

    // ------------------------------------------------------------------ competition (C-2)

    /** Raw neighbour pressure on [victim] from [aggressor]: chemical stress and the fraction smothered. */
    fun pressure(aggressor: Coral, victim: Coral, carbonEfficiency: Double): Pair<Double, Double> {
        if (aggressor === victim || aggressor.kind == victim.kind || aggressor.tissue < 0.05) return 0.0 to 0.0
        val d = aggressor.pos.distanceTo(victim.pos) - extent(aggressor) - extent(victim)
        val vigour = aggressor.tissue * min(aggressor.size, 2.5)
        return when (aggressor.kind) {
            // 03 D: Kenya allelopathy "could damage other corals that are too close", keep 10 cm away, carbon helps
            CoralKind.KENYA -> if (victim.kind == CoralKind.MACRO) 0.0 to 0.0 else
                0.3 * (1 - d / KENYA_RANGE_CM).coerceIn(0.0, 1.0) * vigour * (1 - 0.6 * carbonEfficiency) to 0.0
            // 03 D: zoa/paly toxin; sting neighbours on contact (K)
            CoralKind.ZOA -> 0.3 * (1 - d / STING_RANGE_CM).coerceIn(0.0, 1.0) * vigour to 0.0
            // aiptasia sting what they touch (est.)
            CoralKind.AIPTASIA -> 0.2 * (1 - d / STING_RANGE_CM).coerceIn(0.0, 1.0) * aggressor.tissue to 0.0
            // 03 D: GSP acts by overgrowth, not sting: the mat smothers what it reaches
            CoralKind.GSP -> {
                val geo = aggressor.geoCache.getOrPut(victim.id) {
                    aggressor.patch?.distTo(victim.pos, 3.0 + extent(victim) * 0.5) ?: (aggressor.pos.distanceTo(victim.pos) - extent(victim))
                }
                val over = if (geo.isFinite()) ((aggressor.radiusCm - geo) / (2 * extent(victim)).coerceAtLeast(1.0)).coerceIn(0.0, 1.0) else 0.0
                0.15 * over to over
            }
            else -> 0.0 to 0.0
        }
    }

    /**
     * Sets [Coral.neighborStress] and [Coral.overgrown] for every colony, net of each established colony's baseline,
     * plus the tank-wide allelochemical level (03 D: carbon removes it; WaterSim.allelo).
     */
    fun compete(corals: List<Coral>, w: WaterState, carbonEfficiency: Double) {
        val sTank = 0.3 * smoothstep(1.0, 3.0, w.allelo)
        for (v in corals) {
            var keep = 1.0
            var over = 0.0
            for (a in corals) {
                val (s, o) = pressure(a, v, carbonEfficiency)
                keep *= (1 - s); over = max(over, o)
            }
            val raw = 1 - keep
            if (v.baseline == null) v.baseline = if (v.established) raw to over else 0.0 to 0.0
            val (b0, o0) = v.baseline!!
            val tank = if (v.kind == CoralKind.KENYA || v.kind == CoralKind.MACRO || v.kind == CoralKind.AIPTASIA) 0.0 else sTank
            v.neighborStress = 1 - (1 - (raw - b0).coerceAtLeast(0.0)) * (1 - tank)
            v.overgrown = (over - o0).coerceIn(0.0, 1.0)
        }
    }

    /** Allelochemical release per hour (index/h): Kenya terpenoids and zoa/paly toxins scale with living colony size. */
    fun alleloSource(corals: List<Coral>): Double = corals.sumOf {
        when (it.kind) {
            CoralKind.KENYA -> 0.006 * it.size * it.tissue
            CoralKind.ZOA -> 0.002 * it.size * it.tissue
            else -> 0.0
        }
    }
}
