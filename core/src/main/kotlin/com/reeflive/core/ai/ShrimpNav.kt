package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.geom.smoothstep
import com.reeflive.core.sim.Organism
import com.reeflive.core.sim.RockData
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/*
 * Shrimp getting about (SIMULATION §19.4): both shrimp walk on their pereiopods over rock, sand and glass, upside down on
 * a ceiling as readily as on a floor, and swim short hops with the pleopods when the walk cannot get there (a gap, a lip,
 * the far side of a pillar) or the goal is off the rock (food in the water, a client fish). Stenopus "rarely moves more
 * than half a metre unless disturbed" (ADW) and walks nearly everywhere; Lysmata swims readily to clients and food
 * [M: aquarium references; hop speeds DESIGN ESTIMATE].
 *
 * The surface walk (Steering.crawl) has no path-finding: it slides along the local surface toward the goal and can stall
 * on a wall or under a lip forever. [ShrimpNav] watches the walk's progress and, when it stalls, plans a hop through
 * open water: lift off the surface, cross over the rock (or in front of it) and land on the goal's surface at stance
 * height, turning belly-to-surface on the way in. Collision is the rock SDF only (clear segments), compatible with a
 * capsule-body collision layer.
 */

/** Height of a shrimp's body axis above the surface it stands on: the walking legs reach ~0.21 BL below it (shrimp model). */
fun shrimpStance(o: Organism): Double = 0.21 * o.lengthCm

/** Outward normal of the nearest environment surface (rock, sand, glass) at [p]. */
fun RockData.envNormal(p: Vec3, h: Double = 0.4): Vec3 =
    Vec3(envDist(Vec3(p.x + h, p.y, p.z)) - envDist(Vec3(p.x - h, p.y, p.z)),
        envDist(Vec3(p.x, p.y + h, p.z)) - envDist(Vec3(p.x, p.y - h, p.z)),
        envDist(Vec3(p.x, p.y, p.z + h)) - envDist(Vec3(p.x, p.y, p.z - h))).normalized(Vec3.Y)

/**
 * A spot on the rock near [near] for an animal to sit on: deterministic grid search (no rng) of surface points within
 * [radius] whose normal passes [accept], scored by [score] (lower is better); spots closer than [spacing] to any of
 * [taken] are skipped. Returns the point on the surface and its outward normal.
 */
fun RockData.perchNear(
    near: Vec3, radius: Double, step: Double = 1.5, spacing: Double = 0.0, taken: List<Vec3> = emptyList(),
    accept: (Vec3, Vec3) -> Boolean, score: (Vec3, Vec3) -> Double,
): Pair<Vec3, Vec3>? {
    var best: Pair<Vec3, Vec3>? = null
    var bestS = Double.MAX_VALUE
    val n = (radius / step).toInt()
    for (i in -n..n) for (j in -n..n) for (k in -n..n) {
        val p = near + Vec3(i * step, j * step, k * step)
        if (p.distanceTo(near) > radius) continue
        val d0 = sdf.dist(p)
        if (abs(d0) > step) continue
        val q = sdf.project(p, 0.0, 12)
        if (abs(sdf.dist(q)) > 0.15 || !inWater(q, 1.5)) continue
        val nrm = sdf.gradient(q)
        // a real face, not the inside of a crack: open water in front of it
        if (sdf.dist(q + nrm * 1.0) < 0.7 || sdf.dist(q + nrm * 3.0) < 2.0) continue
        if (!accept(q, nrm) || taken.any { it.distanceTo(q) < spacing }) continue
        val s = score(q, nrm)
        if (s < bestS) { bestS = s; best = q to nrm }
    }
    return best
}

/**
 * Heading along the surface at [p] (normal [n]) that lays a body of [len] cm flat on it, preferring [prefer]: each of
 * 16 tangent directions is scored by how far the body stations behind the head stray from [stand] cm off the surface.
 */
fun RockData.bodyHeading(p: Vec3, n: Vec3, len: Double, stand: Double, prefer: Vec3): Vec3 {
    val t1 = (if (abs(n.y) < 0.9) Vec3.Y else Vec3.X).rejectFrom(n).normalized(Vec3.X)
    val t2 = (n cross t1).normalized(Vec3.Z)
    var best = prefer.rejectFrom(n).normalized(t1)
    var bestS = Double.MAX_VALUE
    for (a in 0 until 16) {
        val ang = a * Math.PI / 8
        val h = (t1 * kotlin.math.cos(ang) + t2 * kotlin.math.sin(ang)).normalized(t1)
        var s = 0.0
        for (k in 1..4) {
            val b = p + n * stand - h * (len * k / 4)
            val d = envDist(b)
            s += abs(d - stand) + (if (d < 0.2) 4.0 else 0.0)
        }
        s += 2.0 * (1 - (h dot prefer.normalized(h)))
        if (s < bestS) { bestS = s; best = h }
    }
    return best
}

/**
 * A walking route over the surfaces from [from] to [to]: A* on a [step] cm grid of water cells at least [clear] and within [band]
 * cm of some surface (rock, sand or glass), searched inside the box around both ends grown by [margin] cm. Returns
 * waypoints (the end is [to]) or null when no route exists. For a crawler that must get round a wall rather than over
 * open water (the Sally Lightfoot's dash home). When [to] itself is off every surface the route ends at the nearest
 * reachable cell and then [to]. Bounded to [maxCells] expansions. [fits], when given, must also pass for a cell to be
 * walked (a wide crawler's whole body has room there).
 */
fun RockData.surfaceRoute(from: Vec3, to: Vec3, step: Double = 1.5, clear: Double = 0.15, band: Double = 3.0, margin: Double = 12.0, maxCells: Int = 40000,
                          fits: ((Vec3) -> Boolean)? = null): List<Vec3>? {
    val lo = Vec3(min(from.x, to.x) - margin, min(from.y, to.y) - margin, min(from.z, to.z) - margin)
    val nx = ((abs(from.x - to.x) + 2 * margin) / step).toInt() + 1
    val ny = ((abs(from.y - to.y) + 2 * margin) / step).toInt() + 1
    val nz = ((abs(from.z - to.z) + 2 * margin) / step).toInt() + 1
    fun idx(i: Int, j: Int, k: Int) = (i * ny + j) * nz + k
    fun at(i: Int, j: Int, k: Int) = Vec3(lo.x + i * step, lo.y + j * step, lo.z + k * step)
    fun cell(p: Vec3) = Triple(((p.x - lo.x) / step).toInt().coerceIn(0, nx - 1), ((p.y - lo.y) / step).toInt().coerceIn(0, ny - 1), ((p.z - lo.z) / step).toInt().coerceIn(0, nz - 1))
    val n = nx * ny * nz
    val walk = ByteArray(n)   // 0 unknown, 1 walkable, 2 blocked
    fun ok(i: Int, j: Int, k: Int): Boolean {
        val id = idx(i, j, k)
        if (walk[id] == 0.toByte()) {
            val p = at(i, j, k)
            val e = envDist(p)
            walk[id] = if (e in clear..band && inWater(p) && fits?.invoke(p) != false) 1 else 2
        }
        return walk[id] == 1.toByte()
    }
    val (si, sj, sk) = cell(from)
    val (ti, tj, tk) = cell(to)
    val g = DoubleArray(n) { Double.MAX_VALUE }
    val prev = IntArray(n) { -1 }
    val open = java.util.PriorityQueue<Pair<Double, Int>>(compareBy { it.first })
    val s0 = idx(si, sj, sk); val t0 = idx(ti, tj, tk)
    g[s0] = 0.0; open += 0.0 to s0
    var expanded = 0
    var near = s0
    var nearD = from.distanceTo(to)
    while (open.isNotEmpty() && expanded < maxCells) {
        val (_, cur) = open.poll()
        if (cur == t0) break
        expanded++
        val i = cur / (ny * nz); val j = (cur / nz) % ny; val k = cur % nz
        at(i, j, k).distanceTo(to).let { if (it < nearD) { nearD = it; near = cur } }
        for (di in -1..1) for (dj in -1..1) for (dk in -1..1) {
            if (di == 0 && dj == 0 && dk == 0) continue
            val a = i + di; val b = j + dj; val cc = k + dk
            if (a !in 0 until nx || b !in 0 until ny || cc !in 0 until nz) continue
            val nid = idx(a, b, cc)
            if (nid != t0 && !ok(a, b, cc)) continue
            val ng = g[cur] + step * kotlin.math.sqrt((di * di + dj * dj + dk * dk).toDouble())
            if (ng < g[nid]) {
                g[nid] = ng; prev[nid] = cur
                open += (ng + at(a, b, cc).distanceTo(to)) to nid
            }
        }
    }
    // the goal cell itself may be off every surface (a crevice mouth in open water): walk to the reachable cell
    // nearest it, then straight in
    val end = if (prev[t0] >= 0) t0 else near
    if (end == s0) return null
    val cells = ArrayList<Int>()
    var c = end
    while (c >= 0 && c != s0) { cells += c; c = prev[c] }
    cells.reverse()
    // every third cell is plenty for a crawl target; the last point is the goal itself
    val pts = cells.filterIndexed { ix, _ -> ix % 3 == 2 }.map { at(it / (ny * nz), (it / nz) % ny, it % nz) }.toMutableList()
    pts += to
    return pts
}

class ShrimpNav(private val o: Organism) {
    enum class Status { WALKING, SWIMMING, ARRIVED, STALLED }

    /** Swim hops taken (tests, diagnostics). */
    var hops = 0; private set
    val swimming: Boolean get() = path.isNotEmpty()
    private var path: List<Vec3> = emptyList()
    private var landUp: Vec3 = Vec3.Y
    private var goal = Vec3(-1e9, 0.0, 0.0)
    private var best = Double.MAX_VALUE
    private var bestAt = 0.0
    private var stalls = 0

    fun reset() { path = emptyList(); goal = Vec3(-1e9, 0.0, 0.0) }

    /**
     * One tick toward [target] (a point on a surface, or in open water when [goalUp] is null and [target] is off the
     * rock). Walks; hops when the walk stalls for [STALL_S] and [canHop], or at once when the shrimp or the goal is off
     * every surface. Returns the status; [arrive] cm counts as there.
     */
    fun drive(c: Ctx, out: SteerIntent, target: Vec3, goalUp: Vec3?, speed: Double, arrive: Double = 1.2, canHop: Boolean = true, swimCmS: Double = HOP_CM_S): Status {
        val stance = shrimpStance(o)
        if (target.distanceTo(goal) > 2.0) { goal = target; best = Double.MAX_VALUE; bestAt = c.t; stalls = 0; path = emptyList() }
        if (path.isNotEmpty()) return swim(c, out, swimCmS)
        val d = o.pos.distanceTo(target)
        val onSurface = c.rock.envDist(o.pos) < stance + 1.5
        val goalOnSurface = c.rock.envDist(target) < stance + 1.5
        if (d <= arrive) { stalls = 0; return Status.ARRIVED }
        if (canHop && (!onSurface || !goalOnSurface) && d > arrive) { plan(c, target, goalUp, stance); return swim(c, out, swimCmS) }
        if (d < best - 0.5) { best = d; bestAt = c.t }
        if (c.t - bestAt > STALL_S) {
            stalls++
            best = d; bestAt = c.t
            if (canHop) { plan(c, target, goalUp, stance); return swim(c, out, swimCmS) }
            return Status.STALLED
        }
        out.crawl = true; out.target = target; out.speed = speed; out.arriveRadius = arrive
        out.surfaceOffset = stance
        return Status.WALKING
    }

    /** Lift-off point, a route over or in front of the rock, the landing approach and the landing itself. */
    private fun plan(c: Ctx, target: Vec3, goalUp: Vec3?, stance: Double) {
        val r = c.rock
        val sdf = r.sdf
        val m = 1.5
        val n0 = r.envNormal(o.pos)
        val lift = r.clampToWater(o.pos + n0 * 2.0, m)
        val land: Vec3
        val approach: Vec3
        if (r.envDist(target) < stance + 1.5) {
            val nT = (goalUp ?: r.envNormal(target)).normalized(Vec3.Y)
            land = r.clampToWater(target + nT * (stance - min(stance, r.envDist(target))), 0.6)
            approach = r.clampToWater(land + nT * 2.5, m)
            landUp = nT
        } else {
            land = r.clampToWater(target, m)
            approach = land
            landUp = Vec3.Y
        }
        val clear = 0.6
        fun ok(a: Vec3, b: Vec3) = sdf.segmentClear(a, b, clear)
        val routes = ArrayList<List<Vec3>>()
        routes += listOf(lift, approach)
        val top = r.waterLevelCm - 2.5
        val front = r.widthCm - 3.0
        for (h in listOf(4.0, 8.0, 14.0, 22.0, 32.0)) {
            val y = min(top, max(lift.y, approach.y) + h)
            routes += listOf(lift, Vec3(lift.x, y, lift.z), Vec3(approach.x, y, approach.z), approach)
            routes += listOf(lift, Vec3(lift.x, y, front), Vec3(approach.x, y, front), approach)
        }
        for (y in listOf(lift.y, approach.y)) routes += listOf(lift, Vec3(lift.x, y, front), Vec3(approach.x, y, front), approach)
        fun length(p: List<Vec3>) = p.zipWithNext().sumOf { (a, b) -> a.distanceTo(b) }
        val route = routes.filter { p -> p.zipWithNext().all { (a, b) -> ok(a, b) } }.minByOrNull { length(it) }
            ?: listOf(lift, Vec3(lift.x, top, front), Vec3(approach.x, top, front), approach)
        path = (route + land).fold(ArrayList()) { acc, p -> if (acc.isEmpty() || acc.last().distanceTo(p) > 0.3) acc += p; acc }
        hops++
    }

    private fun swim(c: Ctx, out: SteerIntent, swimCmS: Double): Status {
        val next = path.first()
        val to = next - o.pos
        val dist = to.length()
        val step = swimCmS * c.dt
        val dir = to.normalized(o.heading)
        val p = if (dist <= step) next else o.pos + dir * step
        if (dist <= step + 0.05) path = path.drop(1)
        val left = path.size
        // swims head first and level-ish; rolls belly-to-surface over the last few cm before landing
        val h = Vec3(dir.x, dir.y.coerceIn(-0.5, 0.5), dir.z).normalized(o.heading)
        val toLand = if (left == 0) 0.0 else o.pos.distanceTo(path.last())
        val k = 1 - smoothstep(0.5, 4.0, toLand)
        val up = Vec3.Y.lerp(landUp, k).normalized(Vec3.Y)
        out.place(p, if (left <= 1 && toLand < 1.0) o.heading else h, up, 40.0)
        out.posture = 0
        o.walkAmt = max(0.0, o.walkAmt - c.dt * 3)
        return if (left == 0) { goal = Vec3(-1e9, 0.0, 0.0); Status.ARRIVED } else Status.SWIMMING
    }

    companion object {
        /** Seconds without 0.5 cm of progress before the walk counts as stalled (stop-go pauses last < 1 s). */
        const val STALL_S = 6.0
        /** Pleopod swimming speed for a hop, cm/s (DESIGN ESTIMATE, ~1 BL/s; no measured value found, research 17). */
        @com.reeflive.core.sim.Ev(com.reeflive.core.sim.Evidence.DESIGN_ESTIMATE, "17 shrimp motion") const val HOP_CM_S = 6.0
    }
}
