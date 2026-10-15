package com.reeflive.core.sim

import com.reeflive.core.geom.Vec3
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Path-finding through the water a body fits (SIMULATION §4.3). Bodies no longer pass through rock, so a swimmer whose
 * straight line to its goal is blocked needs a way round: this steers it along a breadth-first distance field to the
 * goal on a [CELL] grid of the [OpenWater] reachable water for its thickness class.
 *
 * Phone-cheap: fields are cached per (thickness class, goal cell), least recently used first out, and built a slice
 * at a time ([step], [BUDGET] cells a tick shared by all pending fields); a route query is a few array lookups. Until
 * a field is ready the swimmer keeps its old steering. The grids follow the loaded rock (a new rock is a new grid).
 */
class Navigation(private val rock: RockData) {

    /** Passable cells of one thickness class, at [CELL] (a cell is passable if any finer open-water cell in it is). */
    class Grid(val ow: OpenWater, rock: RockData, clear: Double) {
        val nx = (rock.lengthCm / CELL).toInt() + 1
        val ny = (rock.waterLevelCm / CELL).toInt() + 1
        val nz = (rock.widthCm / CELL).toInt() + 1
        val pass = BooleanArray(nx * ny * nz)
        /** Cost of entering a cell: 1 in open water, more hard by the glass and tight against the rock (routes keep off them). */
        val cost = ByteArray(nx * ny * nz)

        init {
            val k = (CELL / OpenWater.CELL).toInt()
            for (iz in 0 until nz) for (iy in 0 until ny) for (ix in 0 until nx) {
                var p = false
                loop@ for (dz in 0 until k) for (dy in 0 until k) for (dx in 0 until k)
                    if (ow.openCell(ix * k + dx, iy * k + dy, iz * k + dz)) { p = true; break@loop }
                if (p) {
                    // a route keeps the body off the glass, the sand and the surface too, not just the rock
                    val m = clear + BOUND_MARGIN
                    val x = (ix + 0.5) * CELL; val y = (iy + 0.5) * CELL; val z = (iz + 0.5) * CELL
                    p = x > m && x < rock.lengthCm - m && z > m && z < rock.widthCm - m &&
                        y > OpenWater.modelFloor(rock, x, z) + m + FLOOR_EXTRA && y < rock.waterLevelCm - m
                }
                pass[idx(ix, iy, iz)] = p
                if (p) {
                    val x = (ix + 0.5) * CELL; val y = (iy + 0.5) * CELL; val z = (iz + 0.5) * CELL
                    val glass = min(min(x, rock.lengthCm - x), min(z, rock.widthCm - z))
                    var k = 1
                    if (glass < GLASS_SOFT_CM) k += 2
                    if (rock.sdf.dist(Vec3(x, y, z)) < clear + ROCK_SOFT_CM) k += 2
                    cost[idx(ix, iy, iz)] = k.toByte()
                }
            }
        }

        fun idx(ix: Int, iy: Int, iz: Int) = (iz * ny + iy) * nx + ix
        /** The 26 neighbours of an inner cell as index offsets, and their step lengths ([Field]'s order: z, y, x). */
        internal val nbrOff = IntArray(26)
        internal val nbrStep = IntArray(26)
        init {
            var n = 0
            for (dz in -1..1) for (dy in -1..1) for (dx in -1..1) {
                val m = (if (dx != 0) 1 else 0) + (if (dy != 0) 1 else 0) + (if (dz != 0) 1 else 0)
                if (m == 0) continue
                nbrOff[n] = (dz * ny + dy) * nx + dx; nbrStep[n] = Field.STEP[m]; n++
            }
        }
        fun cx(x: Double) = floor(x / CELL).toInt().coerceIn(0, nx - 1)
        fun cy(y: Double) = floor(y / CELL).toInt().coerceIn(0, ny - 1)
        fun cz(z: Double) = floor(z / CELL).toInt().coerceIn(0, nz - 1)
        fun cellOf(p: Vec3) = idx(cx(p.x), cy(p.y), cz(p.z))
        fun centre(i: Int): Vec3 {
            val ix = i % nx; val iy = (i / nx) % ny; val iz = i / (nx * ny)
            return Vec3((ix + 0.5) * CELL, (iy + 0.5) * CELL, (iz + 0.5) * CELL)
        }
        fun passAt(p: Vec3) = pass[cellOf(p)]

        /** Whether the straight line a→b stays in passable cells (the first and last [END_SKIP] cm are not checked). */
        fun lineClear(a: Vec3, b: Vec3): Boolean {
            val len = a.distanceTo(b)
            if (len <= 2 * END_SKIP) return true
            val n = (len / LINE_STEP).toInt() + 1
            for (i in 0..n) {
                val s = i * LINE_STEP
                if (s < END_SKIP || s > len - END_SKIP) continue
                if (!passAt(a.lerp(b, s / len))) return false
            }
            return true
        }
    }

    /**
     * Travel cost to [goalCell] from every passable cell (Dial's bucketed Dijkstra over the 26 neighbours, steps of
     * 10 / 14 / 17 for face / edge / corner moves, times the cell's [Grid.cost]), built a slice at a time.
     */
    class Field(val grid: Grid, val goal: Vec3, goalCell: Int) {
        val dist = IntArray(grid.pass.size) { UNSET }
        private var buckets: Array<IntStack>? = Array(NB) { IntStack() }
        private var cur = 0
        private var curDist = 0
        private var pending = 0
        var done = false; private set
        var lastUsed = 0L

        init { dist[goalCell] = 0; buckets!![0].push(goalCell); pending = 1 }

        /** Expands up to [budget] cells; returns how many it expanded. */
        fun step(budget: Int): Int {
            val g = grid
            val bk = buckets ?: return 0
            var n = 0
            while (pending > 0 && n < budget) {
                while (bk[cur].isEmpty()) { cur = (cur + 1) % NB; curDist++ }
                val i = bk[cur].pop(); pending--
                if (dist[i] != curDist) continue
                n++
                val ix = i % g.nx; val iy = (i / g.nx) % g.ny; val iz = i / (g.nx * g.ny)
                if (ix > 0 && iy > 0 && iz > 0 && ix < g.nx - 1 && iy < g.ny - 1 && iz < g.nz - 1) {
                    // an inner cell (every passable one: the grid keeps off the glass, sand and surface): no bounds checks,
                    // the same neighbours in the same order
                    val off = g.nbrOff; val stp = g.nbrStep; val pass = g.pass; val cost = g.cost
                    for (q in 0 until 26) {
                        val j = i + off[q]
                        if (!pass[j]) continue
                        val w = stp[q] * cost[j]
                        val nd = curDist + w
                        if (nd < dist[j]) { dist[j] = nd; bk[(cur + w) % NB].push(j); pending++ }
                    }
                    continue
                }
                for (dz in -1..1) for (dy in -1..1) for (dx in -1..1) {
                    val x = ix + dx; val y = iy + dy; val z = iz + dz
                    if (x < 0 || y < 0 || z < 0 || x >= g.nx || y >= g.ny || z >= g.nz) continue
                    val j = g.idx(x, y, z)
                    if (!g.pass[j]) continue
                    val m = (if (dx != 0) 1 else 0) + (if (dy != 0) 1 else 0) + (if (dz != 0) 1 else 0)
                    if (m == 0) continue
                    val w = STEP[m] * g.cost[j]
                    val nd = curDist + w
                    if (nd < dist[j]) { dist[j] = nd; bk[(cur + w) % NB].push(j); pending++ }
                }
            }
            if (pending == 0) { done = true; buckets = null }
            return n
        }

        companion object {
            const val UNSET = Int.MAX_VALUE
            internal val STEP = intArrayOf(0, 10, 14, 17)
            private const val NB = 17 * MAX_COST + 1
        }
    }

    /** A growable stack of ints (one Dial bucket). */
    class IntStack {
        private var a = IntArray(64)
        private var n = 0
        fun isEmpty() = n == 0
        fun push(v: Int) { if (n == a.size) a = a.copyOf(n * 2); a[n++] = v }
        fun pop(): Int = a[--n]
    }

    private val fields = ArrayList<Field>()
    private var clock = 0L
    /** Cells expanded so far (tests / profiling). */
    var expanded = 0L; private set

    /** Result of the last [route]: the goal cannot be reached from where the body is (field complete). */
    var unreachable = false; private set

    fun grid(level: Int): Grid {
        val ow = OpenWater.of(rock, OpenWater.LEVELS[level])
        return synchronized(GRIDS) { GRIDS.getOrPut(ow) { Grid(ow, rock, OpenWater.LEVELS[level]) } }
    }

    /** Builds pending fields, oldest request first: call once a tick. */
    fun step() {
        var budget = BUDGET
        for (f in fields) {
            if (f.done) continue
            val n = f.step(budget); expanded += n; budget -= n
            if (budget <= 0) break
        }
    }

    /**
     * Where a body of thickness class [level] at [from] should head for [to]: [to] itself while the straight line is
     * clear, else a point down the distance field it can see. Null when there is no route yet (field pending) or none
     * at all ([unreachable] then true).
     */
    fun route(level: Int, from: Vec3, to: Vec3): Vec3? {
        unreachable = false
        val g = grid(level)
        if (g.lineClear(from, to)) return to
        val goalCell = nearestPassable(g, to, GOAL_SNAP) ?: run { unreachable = true; return null }
        val f = fieldFor(g, to, goalCell)
        f.lastUsed = ++clock
        val start = nearestReached(g, f, from, START_SNAP) ?: run {
            // the body is in a slot the grid does not count passable (the resolve lets it squeeze where the 1 cm grid
            // cannot): back out to the nearest cell of the route network it can see, then on from there
            if (!f.done) return null
            // in open water the body fits (its own cell passable) and still cut off from the goal: no way round is known
            // at this size (a goal in a pocket the body cannot enter): no route, the steering heads straight for it
            if (g.pass[g.cellOf(from)]) { unreachable = true; return null }
            val out = nearestReached(g, f, from, EXIT_SNAP)?.let { g.centre(it) }?.takeIf { rock.sdf.segmentClear(from, it, 0.3) }
            if (out == null) unreachable = true
            return out
        }
        // walk down the field, then take the farthest point along it in clear view (string pulling)
        var cur = start
        var best = g.centre(cur)
        val path = IntArray(DESCENT)
        var n = 0
        while (n < DESCENT && f.dist[cur] > 0) {
            val ix = cur % g.nx; val iy = (cur / g.nx) % g.ny; val iz = cur / (g.nx * g.ny)
            var nxt = cur; var nd = f.dist[cur]
            for (dz in -1..1) for (dy in -1..1) for (dx in -1..1) {
                val x = ix + dx; val y = iy + dy; val z = iz + dz
                if (x < 0 || y < 0 || z < 0 || x >= g.nx || y >= g.ny || z >= g.nz) continue
                val j = g.idx(x, y, z)
                val dj = f.dist[j]
                if (dj < nd) { nd = dj; nxt = j }
            }
            if (nxt == cur) break
            cur = nxt; path[n++] = cur
        }
        if (n == 0) return to
        for (k in n - 1 downTo 0) {
            val p = g.centre(path[k])
            if (k == 0 || g.lineClear(from, p)) { best = p; break }
        }
        return best
    }

    private fun fieldFor(g: Grid, to: Vec3, goalCell: Int): Field {
        for (f in fields) if (f.grid === g && f.goal.distanceTo(to) <= REUSE_CM) return f
        if (fields.size >= MAX_FIELDS) fields.remove(fields.minByOrNull { it.lastUsed }!!)
        return Field(g, to, goalCell).also { fields += it }
    }

    /** The passable cell nearest [p] within [r] cells (searched in growing shells), or null. */
    private fun nearestPassable(g: Grid, p: Vec3, r: Int): Int? {
        val c = g.cellOf(p)
        if (g.pass[c]) return c
        val cx = g.cx(p.x); val cy = g.cy(p.y); val cz = g.cz(p.z)
        for (k in 1..r) {
            var best: Int? = null; var bd = Int.MAX_VALUE
            for (dz in -k..k) for (dy in -k..k) for (dx in -k..k) {
                if (kotlin.math.abs(dx) != k && kotlin.math.abs(dy) != k && kotlin.math.abs(dz) != k) continue
                val x = cx + dx; val y = cy + dy; val z = cz + dz
                if (x < 0 || y < 0 || z < 0 || x >= g.nx || y >= g.ny || z >= g.nz) continue
                val j = g.idx(x, y, z)
                if (!g.pass[j]) continue
                val d = dx * dx + dy * dy + dz * dz
                if (d < bd) { bd = d; best = j }
            }
            if (best != null) return best
        }
        return null
    }

    /**
     * The nearest point to [p] that a body of thickness class [level] can get to (open water connected to the rest of
     * the tank), within [APPROACH_SNAP] cm; null when there is none (a client deep in a crevice narrower than the body).
     * A brain that works something in the rock from outside (a cleaner at a crevice) heads here.
     */
    fun approach(level: Int, p: Vec3): Vec3? {
        val g = grid(level)
        if (g.passAt(p)) return p
        return nearestPassable(g, p, APPROACH_SNAP)?.let { g.centre(it) }
    }

    private fun nearestReached(g: Grid, f: Field, p: Vec3, snap: Int): Int? {
        val c = g.cellOf(p)
        if (f.dist[c] != Field.UNSET) return c
        val cx = g.cx(p.x); val cy = g.cy(p.y); val cz = g.cz(p.z)
        var best: Int? = null; var bd = Int.MAX_VALUE
        for (dz in -snap..snap) for (dy in -snap..snap) for (dx in -snap..snap) {
            val x = cx + dx; val y = cy + dy; val z = cz + dz
            if (x < 0 || y < 0 || z < 0 || x >= g.nx || y >= g.ny || z >= g.nz) continue
            val j = g.idx(x, y, z)
            val d = f.dist[j]
            if (d == Field.UNSET) continue
            val score = d + 20 * max(max(kotlin.math.abs(dx), kotlin.math.abs(dy)), kotlin.math.abs(dz))
            if (score < bd) { bd = score; best = j }
        }
        return best
    }

    companion object {
        const val CELL = 1.0
        /** Extra room a route keeps from the glass and surface (cm), and from the sand on top of that (bodies are taller than wide). */
        const val BOUND_MARGIN = 0.6
        const val FLOOR_EXTRA = 0.8
        /** Cells expanded per tick across all pending fields (a whole-tank field is ~10 ticks). */
        const val BUDGET = 20000
        const val MAX_FIELDS = 8
        const val MAX_COST = 5
        const val GLASS_SOFT_CM = 3.0
        const val ROCK_SOFT_CM = 1.0
        /** A field built for a goal serves goals this close to it (a client that has moved on a little). */
        const val REUSE_CM = 8.0
        const val GOAL_SNAP = 8
        const val APPROACH_SNAP = 8
        const val START_SNAP = 2
        /** How far (cells) a body outside the network looks for the nearest cell of it in view ([route]). */
        const val EXIT_SNAP = 6
        const val DESCENT = 24
        const val LINE_STEP = 0.5
        const val END_SKIP = 1.0
        private val GRIDS = LruCache<OpenWater, Grid>(16)

    }
}
