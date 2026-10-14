package com.reeflive.core.sim

import com.reeflive.core.geom.Vec3
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** A piece torn off the sheet: where, how fast, and how much sheet it carries (cell units, [NoriCloth.total] = whole). */
class NoriBit(val pos: Vec3, val vel: Vec3, val area: Double)

/**
 * The nori sheet on its clip as a small flow-driven cloth (owner request "make the algae clip affected by flow";
 * SIMULATION §4.6). A [COLS] × [ROWS] grid of nodes pinched by the clip at the middle of its top edge, stepped with
 * position-based dynamics (stretch, shear and a weak bend constraint) and pushed by the water:
 *
 * - **Flow:** each node is dragged toward the local [com.reeflive.core.geom.FlowField] velocity (sampled once per step
 *   with the sim time, so the wavemaker pulse, pump on / off and touch puffs all reach it). Drag is a flat plate's:
 *   strong across the sheet, weak along it, so the sheet turns edge-on and streams downstream like a flag.
 * - **Turbulence:** a travelling wave of velocity noise, its strength a share of the local speed ([TURBULENCE]) plus a
 *   small floor, makes it flutter from the clip to the free end; in still water it only sways and sinks a little
 *   (wet nori is slightly heavier than seawater).
 * - **Grazing:** [bite] eats from the cell nearest the mouth (free edges first) and tugs it. A bitten edge cell
 *   that is down to [SHRED_BELOW] of its material tears off; so does a weakened edge cell the current overstretches
 *   ([TEAR_STRAIN]). Any part no longer joined to the clip through whole cells breaks away. Torn material leaves as
 *   [bits] (FoodSystem turns them into drifting nori food). Material is conserved: [remainingArea] + [eaten] + [torn]
 *   = [total].
 *
 * Cheap: 35 nodes, ≤ [MAX_SUBSTEPS] substeps of [SUBSTEP_S] per call, one flow sample per node per call. No rng: the
 * noise is a fixed function of node and time, so runs repeat exactly. The renderer skins its tiles over [x].
 */
class NoriCloth(
    val clip: Vec3,
    private val rock: RockData,
    val cols: Int = COLS,
    val rows: Int = ROWS,
    val widthCm: Double = WIDTH_CM,
    val lengthCm: Double = LENGTH_CM,
) {
    val nodeCount = cols * rows
    val cellCols = cols - 1
    val cellRows = rows - 1
    val cellCount = cellCols * cellRows
    /** Node positions (xyz per node, row-major: node (i across, j down) at `j * cols + i`). */
    val x = DoubleArray(nodeCount * 3)
    private val v = DoubleArray(nodeCount * 3)
    private val p = DoubleArray(nodeCount * 3)
    private val u = DoubleArray(nodeCount * 3)
    private val nrm = DoubleArray(nodeCount * 3)
    private val w = DoubleArray(nodeCount)
    val pinned = BooleanArray(nodeCount)
    /** Material left per cell, 0..1 (cell (i, j) at `j * cellCols + i`); exactly 0 once gone. */
    val material = DoubleArray(cellCount) { 1.0 }
    val total: Double = cellCount.toDouble()
    var eaten = 0.0; private set
    var torn = 0.0; private set
    /** Pieces torn off since the owner last drained them. */
    val bits = ArrayList<NoriBit>()
    /** Out of the glass the clip sticks to, into the water, and along the glass (the sheet's width). */
    val inward: Vec3
    val across: Vec3
    private val dxRest: Double
    private val dyRest: Double

    // constraints: node a, node b, rest length, stiffness, adjacent cells (−1 = none) or the middle node of a bend
    private val ca: IntArray
    private val cb: IntArray
    private val rest: DoubleArray
    private val stiff: DoubleArray
    private val cell1: IntArray
    private val cell2: IntArray
    private val mid: IntArray
    private var dirty = false

    init {
        inward = inwardFrom(clip, rock)
        across = (Vec3.Y cross inward).normalized(Vec3.Z)
        dxRest = widthCm / (cols - 1)
        dyRest = lengthCm / (rows - 1)
        val mid0 = (cols - 1) / 2.0
        for (j in 0 until rows) for (i in 0 until cols) {
            val n = j * cols + i
            val q = clip + inward * CLIP_OFFSET_CM + across * ((i - mid0) * dxRest) - Vec3.Y * (j * dyRest)
            set(x, n, q)
            pinned[n] = j == 0 && abs(i - mid0) <= CLIP_HALF_NODES
        }
        val la = ArrayList<Int>(); val lb = ArrayList<Int>(); val lr = ArrayList<Double>(); val ls = ArrayList<Double>()
        val l1 = ArrayList<Int>(); val l2 = ArrayList<Int>(); val lm = ArrayList<Int>()
        fun add(a: Int, b: Int, s: Double, c1: Int, c2: Int, m: Int = -1) {
            la += a; lb += b; ls += s; l1 += c1; l2 += c2; lm += m
            lr += sqrt(dist2(x, a, x, b))
        }
        fun cellAt(i: Int, j: Int) = if (i in 0 until cellCols && j in 0 until cellRows) j * cellCols + i else -1
        for (j in 0 until rows) for (i in 0 until cols) {
            val n = j * cols + i
            if (i + 1 < cols) add(n, n + 1, 1.0, cellAt(i, j - 1), cellAt(i, j))
            if (j + 1 < rows) add(n, n + cols, 1.0, cellAt(i - 1, j), cellAt(i, j))
            if (i + 1 < cols && j + 1 < rows) {
                add(n, n + cols + 1, SHEAR, cellAt(i, j), -1)
                add(n + 1, n + cols, SHEAR, cellAt(i, j), -1)
            }
            if (i + 2 < cols) add(n, n + 2, BEND, -1, -1, n + 1)
            if (j + 2 < rows) add(n, n + 2 * cols, BEND, -1, -1, n + cols)
        }
        ca = la.toIntArray(); cb = lb.toIntArray(); rest = lr.toDoubleArray(); stiff = ls.toDoubleArray()
        cell1 = l1.toIntArray(); cell2 = l2.toIntArray(); mid = lm.toIntArray()
        updateMass()
    }

    /** Fraction of the sheet left on the clip (0..1). */
    val remaining: Double get() = remainingArea / total
    val remainingArea: Double get() { var s = 0.0; for (m in material) s += m; return s }

    fun node(n: Int): Vec3 = Vec3(x[n * 3], x[n * 3 + 1], x[n * 3 + 2])
    fun nodeVel(n: Int): Vec3 = Vec3(v[n * 3], v[n * 3 + 1], v[n * 3 + 2])
    fun present(c: Int): Boolean = material[c] > 0.0

    /** Centre of cell [c] (mean of its four nodes). */
    fun cellCentre(c: Int): Vec3 {
        val i = c % cellCols; val j = c / cellCols
        val a = j * cols + i
        return (node(a) + node(a + 1) + node(a + cols) + node(a + cols + 1)) * 0.25
    }

    /** Area-weighted centre of the material still on the clip (the clip itself when nothing is left). */
    fun centroid(): Vec3 {
        var s = 0.0; var acc = Vec3.ZERO
        for (c in 0 until cellCount) if (present(c)) { acc += cellCentre(c) * material[c]; s += material[c] }
        return if (s > 0) acc / s else clip
    }

    /** A cell on the sheet's free edge: on the border of the grid or next to a cell that is gone. */
    fun isEdge(c: Int): Boolean {
        val i = c % cellCols; val j = c / cellCols
        if (i == 0 || j == 0 || i == cellCols - 1 || j == cellRows - 1) return true
        return !present(c - 1) || !present(c + 1) || !present(c - cellCols) || !present(c + cellCols)
    }

    private fun nodeAlive(n: Int): Boolean {
        val i = n % cols; val j = n / cols
        for (dj in -1..0) for (di in -1..0) {
            val ci = i + di; val cj = j + dj
            if (ci in 0 until cellCols && cj in 0 until cellRows && present(cj * cellCols + ci)) return true
        }
        return false
    }

    private fun nodeMaterial(n: Int): Double {
        val i = n % cols; val j = n / cols
        var s = 0.0
        for (dj in -1..0) for (di in -1..0) {
            val ci = i + di; val cj = j + dj
            if (ci in 0 until cellCols && cj in 0 until cellRows) s += material[cj * cellCols + ci]
        }
        return s / 4.0
    }

    private fun updateMass() {
        for (n in 0 until nodeCount) w[n] = if (pinned[n]) 0.0 else 1.0 / max(nodeMaterial(n), MIN_NODE_MASS)
    }

    private fun active(k: Int): Boolean {
        val m = mid[k]
        if (m >= 0) return nodeAlive(ca[k]) && nodeAlive(cb[k]) && nodeAlive(m)
        val c1 = cell1[k]; val c2 = cell2[k]
        return (c1 >= 0 && present(c1)) || (c2 >= 0 && present(c2))
    }

    /**
     * Advances the sheet by [dt] sim seconds ending at sim time [t]. [flowAt] gives the water velocity (cm/s) at a
     * point. Long steps (fast-forward) are cut to [MAX_STEP_S]: the sheet is a picture of the water, not a clock.
     */
    fun step(dt: Double, t: Double, flowAt: (Vec3) -> Vec3) {
        if (dt <= 0.0) return
        val h = min(dt, MAX_STEP_S)
        val n = ceil(h / SUBSTEP_S).toInt().coerceIn(1, MAX_SUBSTEPS)
        val hs = h / n
        for (k in 0 until nodeCount) {
            if (pinned[k] || !nodeAlive(k)) { u[k * 3] = 0.0; u[k * 3 + 1] = 0.0; u[k * 3 + 2] = 0.0; continue }
            val f = flowAt(node(k))
            u[k * 3] = f.x; u[k * 3 + 1] = f.y; u[k * 3 + 2] = f.z
        }
        for (s in 0 until n) substep(hs, t - h + (s + 1) * hs)
        tearOverstretched()
        if (dirty) { detachLoose(); updateMass(); dirty = false }
    }

    private fun substep(hs: Double, t: Double) {
        normals()
        for (k in 0 until nodeCount) {
            val o = k * 3
            if (pinned[k] || !nodeAlive(k)) {
                p[o] = x[o]; p[o + 1] = x[o + 1]; p[o + 2] = x[o + 2]
                if (!pinned[k]) { v[o] = 0.0; v[o + 1] = 0.0; v[o + 2] = 0.0 }
                continue
            }
            val ux = u[o]; val uy = u[o + 1]; val uz = u[o + 2]
            val speed = sqrt(ux * ux + uy * uy + uz * uz)
            // turbulence: a wave of velocity noise running from the clip to the free end (flag flutter)
            val amp = TURBULENCE * speed + TURB_FLOOR_CM_S
            val i = k % cols; val j = k / cols
            val ph = t * 2.3 - j * 0.95 + i * 0.4
            val tx = ux + amp * (0.6 * sin(ph * 1.7 + 1.3) + 0.4 * sin(ph * 3.1 + i * 2.1))
            val ty = uy + amp * 0.6 * (0.6 * sin(ph * 1.3 + 4.1) + 0.4 * sin(ph * 2.7 + j * 1.7))
            val tz = uz + amp * (0.6 * sin(ph * 1.9 + 2.6) + 0.4 * sin(ph * 3.7 + i * 0.9 + j))
            val rx = tx - v[o]; val ry = ty - v[o + 1]; val rz = tz - v[o + 2]
            val nx = nrm[o]; val ny = nrm[o + 1]; val nz = nrm[o + 2]
            val rn = rx * nx + ry * ny + rz * nz
            val ptx = rx - nx * rn; val pty = ry - ny * rn; val ptz = rz - nz * rn
            val rt = sqrt(ptx * ptx + pty * pty + ptz * ptz)
            val ms = 1.0 / max(nodeMaterial(k), 0.35)
            val kn = min((DRAG_N + DRAG_N_QUAD * abs(rn)) * hs * ms, 0.9)
            val kt = min((DRAG_T + DRAG_T_QUAD * rt) * hs * ms, 0.9)
            v[o] += nx * rn * kn + ptx * kt
            v[o + 1] += ny * rn * kn + pty * kt - SINK_ACC * hs
            v[o + 2] += nz * rn * kn + ptz * kt
            val damp = 1.0 - DAMP * hs
            v[o] *= damp; v[o + 1] *= damp; v[o + 2] *= damp
            p[o] = x[o] + v[o] * hs; p[o + 1] = x[o + 1] + v[o + 1] * hs; p[o + 2] = x[o + 2] + v[o + 2] * hs
        }
        repeat(ITERATIONS) {
            for (k in ca.indices) {
                if (!active(k)) continue
                val a = ca[k]; val b = cb[k]
                val wa = w[a]; val wb = w[b]
                val ws = wa + wb
                if (ws <= 0.0) continue
                val ax = a * 3; val bx = b * 3
                val dx = p[bx] - p[ax]; val dy = p[bx + 1] - p[ax + 1]; val dz = p[bx + 2] - p[ax + 2]
                val len = sqrt(dx * dx + dy * dy + dz * dz)
                if (len < 1e-9) continue
                var c = len - rest[k]
                // bending only resists folding flat (compression of a 2-apart pair), never stretch
                if (mid[k] >= 0 && c > 0) continue
                c *= stiff[k] / (len * ws)
                p[ax] += dx * c * wa; p[ax + 1] += dy * c * wa; p[ax + 2] += dz * c * wa
                p[bx] -= dx * c * wb; p[bx + 1] -= dy * c * wb; p[bx + 2] -= dz * c * wb
            }
            collide()
        }
        limitStretch()
        val inv = 1.0 / hs
        for (k in 0 until nodeCount) {
            if (pinned[k]) continue
            val o = k * 3
            if (nodeAlive(k)) {
                v[o] = (p[o] - x[o]) * inv; v[o + 1] = (p[o + 1] - x[o + 1]) * inv; v[o + 2] = (p[o + 2] - x[o + 2]) * inv
                // a constraint snap must not fling the sheet: cap node speed at a fast current's
                val sp = sqrt(v[o] * v[o] + v[o + 1] * v[o + 1] + v[o + 2] * v[o + 2])
                if (sp > MAX_NODE_SPEED) { val f = MAX_NODE_SPEED / sp; v[o] *= f; v[o + 1] *= f; v[o + 2] *= f }
            }
            x[o] = p[o]; x[o + 1] = p[o + 1]; x[o + 2] = p[o + 2]
        }
    }

    /**
     * Strain limit: no stretch edge longer than [MAX_STRETCH] × rest, fixed by moving the node farther from the clip
     * (rows top-down, then outward along each row), so a violent gust can never fling the sheet apart.
     */
    private fun limitStretch() {
        val pc = (cols - 1) / 2
        for (j in 0 until rows) {
            if (j > 0) for (i in 0 until cols) clampEdge(j * cols + i - cols, j * cols + i)
            for (i in pc + 1 until cols) clampEdge(j * cols + i - 1, j * cols + i)
            for (i in pc - 1 downTo 0) clampEdge(j * cols + i + 1, j * cols + i)
        }
    }

    private fun clampEdge(a: Int, b: Int) {
        if (pinned[b] || !nodeAlive(a) || !nodeAlive(b)) return
        val rl = if (a / cols == b / cols) dxRest else dyRest
        val ax = a * 3; val bx = b * 3
        val dx = p[bx] - p[ax]; val dy = p[bx + 1] - p[ax + 1]; val dz = p[bx + 2] - p[ax + 2]
        val len = sqrt(dx * dx + dy * dy + dz * dz)
        val maxL = rl * MAX_STRETCH
        if (len <= maxL) return
        val f = maxL / len
        p[bx] = p[ax] + dx * f; p[bx + 1] = p[ax + 1] + dy * f; p[bx + 2] = p[ax + 2] + dz * f
    }

    /** Keeps nodes in the water and out of the rock. */
    private fun collide() {
        val m = 0.3
        for (k in 0 until nodeCount) {
            if (pinned[k]) continue
            val o = k * 3
            p[o] = p[o].coerceIn(m, rock.lengthCm - m)
            p[o + 1] = p[o + 1].coerceIn(rock.sandTopCm + m, rock.waterLevelCm - m)
            p[o + 2] = p[o + 2].coerceIn(m, rock.widthCm - m)
        }
        for (k in 0 until nodeCount) {
            if (pinned[k]) continue
            val o = k * 3
            val q = Vec3(p[o], p[o + 1], p[o + 2])
            if (rock.sdf.dist(q) < ROCK_GAP_CM) { val r = rock.sdf.project(q, ROCK_GAP_CM, 4); set(p, k, r) }
        }
    }

    /** Sheet normal at every node (from its grid neighbours), sign-free: drag only needs the plane. */
    private fun normals() {
        for (j in 0 until rows) for (i in 0 until cols) {
            val n = j * cols + i
            val l = if (i > 0) n - 1 else n; val r = if (i < cols - 1) n + 1 else n
            val d = if (j > 0) n - cols else n; val dn = if (j < rows - 1) n + cols else n
            val ax = x[r * 3] - x[l * 3]; val ay = x[r * 3 + 1] - x[l * 3 + 1]; val az = x[r * 3 + 2] - x[l * 3 + 2]
            val bx = x[dn * 3] - x[d * 3]; val by = x[dn * 3 + 1] - x[d * 3 + 1]; val bz = x[dn * 3 + 2] - x[d * 3 + 2]
            var cx = ay * bz - az * by; var cy = az * bx - ax * bz; var cz = ax * by - ay * bx
            val len = sqrt(cx * cx + cy * cy + cz * cz)
            if (len < 1e-9) { cx = inward.x; cy = inward.y; cz = inward.z } else { cx /= len; cy /= len; cz /= len }
            nrm[n * 3] = cx; nrm[n * 3 + 1] = cy; nrm[n * 3 + 2] = cz
        }
    }

    /**
     * A fish bites at [at], taking up to [amount] (cell units) and tugging the bitten cell by [tug] (cm/s, the head
     * pulling back). Bites land on the free edge first. Returns the bitten cell's centre, or null when nothing is left.
     */
    fun bite(at: Vec3, amount: Double, tug: Vec3 = Vec3.ZERO): Vec3? {
        val c = biteCell(at) ?: return null
        val centre = cellCentre(c)
        val take = min(amount, material[c])
        material[c] -= take
        eaten += take
        if (material[c] < 1e-9) material[c] = 0.0
        val i = c % cellCols; val j = c / cellCols
        for (n in intArrayOf(j * cols + i, j * cols + i + 1, (j + 1) * cols + i, (j + 1) * cols + i + 1)) {
            if (pinned[n]) continue
            v[n * 3] += tug.x; v[n * 3 + 1] += tug.y; v[n * 3 + 2] += tug.z
        }
        // a bitten-down edge cell shreds off; its ragged neighbours on the edge may go with it
        if (material[c] > 0.0 && material[c] < SHRED_BELOW) tear(c)
        for (nb in intArrayOf(c - 1, c + 1, c - cellCols, c + cellCols)) {
            if (nb < 0 || nb >= cellCount || (nb % cellCols != i && nb / cellCols != j)) continue
            if (present(nb) && material[nb] < SHRED_BELOW && isEdge(nb)) tear(nb)
        }
        dirty = true
        detachLoose(); updateMass(); dirty = false
        return centre
    }

    /** The cell a bite at [at] lands on: the nearest present cell, edge cells favoured by [EDGE_BIAS_CM]. */
    fun biteCell(at: Vec3): Int? {
        var best = -1; var bd = Double.MAX_VALUE
        for (c in 0 until cellCount) {
            if (!present(c)) continue
            val d = cellCentre(c).distanceTo(at) - (if (isEdge(c)) EDGE_BIAS_CM else 0.0)
            if (d < bd) { bd = d; best = c }
        }
        return if (best < 0) null else best
    }

    /** Cell [c] tears off whole as a [NoriBit]. */
    fun tear(c: Int) {
        val m = material[c]
        if (m <= 0.0) return
        val i = c % cellCols; val j = c / cellCols
        val a = j * cols + i
        val vel = (nodeVel(a) + nodeVel(a + 1) + nodeVel(a + cols) + nodeVel(a + cols + 1)) * 0.25
        bits += NoriBit(cellCentre(c), vel, m)
        torn += m
        material[c] = 0.0
        dirty = true
    }

    /** Everything left breaks off (the clip lets go, or the sheet is down to scraps). */
    fun releaseAll() { for (c in 0 until cellCount) tear(c); dirty = false }

    /** Weakened edge cells the water pulls apart: any of their edges stretched past [TEAR_STRAIN]. */
    private fun tearOverstretched() {
        for (k in ca.indices) {
            if (mid[k] >= 0 || stiff[k] < 1.0 || !active(k)) continue
            val len = sqrt(dist2(x, ca[k], x, cb[k]))
            if (len <= rest[k] * TEAR_STRAIN) continue
            for (c in intArrayOf(cell1[k], cell2[k])) if (c >= 0 && present(c) && material[c] < WEAK_BELOW && isEdge(c)) tear(c)
        }
    }

    /** Cells no longer joined to the clip through whole cells (shared edges) break away. */
    private fun detachLoose() {
        val reach = BooleanArray(cellCount)
        val queue = ArrayDeque<Int>()
        for (c in 0 until cellCount) {
            if (!present(c)) continue
            val i = c % cellCols; val j = c / cellCols
            val a = j * cols + i
            if (pinned[a] || pinned[a + 1] || pinned[a + cols] || pinned[a + cols + 1]) { reach[c] = true; queue.add(c) }
        }
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst()
            val i = c % cellCols; val j = c / cellCols
            if (i > 0) visit(c - 1, reach, queue)
            if (i < cellCols - 1) visit(c + 1, reach, queue)
            if (j > 0) visit(c - cellCols, reach, queue)
            if (j < cellRows - 1) visit(c + cellCols, reach, queue)
        }
        for (c in 0 until cellCount) if (present(c) && !reach[c]) tear(c)
    }

    private fun visit(c: Int, reach: BooleanArray, q: ArrayDeque<Int>) {
        if (!reach[c] && present(c)) { reach[c] = true; q.add(c) }
    }

    /** Node positions as floats for the snapshot. */
    fun exportNodes(): FloatArray = FloatArray(nodeCount * 3) { x[it].toFloat() }
    fun exportCells(): FloatArray = FloatArray(cellCount) { material[it].toFloat() }

    /** Largest stretch of any active stretch / shear constraint (1 = rest length); tests. */
    fun maxStrain(): Double {
        var m = 0.0
        for (k in ca.indices) if (mid[k] < 0 && active(k)) m = max(m, sqrt(dist2(x, ca[k], x, cb[k])) / rest[k])
        return m
    }

    private fun set(a: DoubleArray, n: Int, q: Vec3) { a[n * 3] = q.x; a[n * 3 + 1] = q.y; a[n * 3 + 2] = q.z }

    private fun dist2(a: DoubleArray, i: Int, b: DoubleArray, j: Int): Double {
        val dx = a[i * 3] - b[j * 3]; val dy = a[i * 3 + 1] - b[j * 3 + 1]; val dz = a[i * 3 + 2] - b[j * 3 + 2]
        return dx * dx + dy * dy + dz * dz
    }

    companion object {
        const val COLS = 5
        const val ROWS = 7
        /** A strip of Sea Veggies nori on a clip: about 8 cm wide, 10 cm hanging (design estimate). */
        @Ev(Evidence.DESIGN_ESTIMATE) const val WIDTH_CM = 8.0
        @Ev(Evidence.DESIGN_ESTIMATE) const val LENGTH_CM = 10.0
        /** Grams of food in a whole sheet (one Feed of NORI). */
        const val SHEET_GRAMS = 1.0
        /** The clip pinches the sheet at one spot: the middle top node, a little in from the glass. */
        const val CLIP_HALF_NODES = 0.0
        const val CLIP_OFFSET_CM = 0.8
        const val SUBSTEP_S = 1.0 / 60.0
        const val MAX_SUBSTEPS = 8
        const val MAX_STEP_S = 0.15
        const val ITERATIONS = 6
        const val SHEAR = 0.6
        const val BEND = 0.08
        /** Flat-plate drag across (N) and along (T) the sheet, 1/s plus a quadratic part per cm/s. */
        const val DRAG_N = 6.0
        const val DRAG_N_QUAD = 0.35
        const val DRAG_T = 0.7
        const val DRAG_T_QUAD = 0.04
        const val DAMP = 0.6
        /** Wet nori sinks slowly in still water (cm/s² of net weight; terminal ≈ 0.3 cm/s with the drag). */
        @Ev(Evidence.DESIGN_ESTIMATE) const val SINK_ACC = 2.0
        /** Turbulence intensity (velocity noise as a share of the local speed) and its still-water floor (cm/s). */
        @Ev(Evidence.DESIGN_ESTIMATE, "reef-tank powerhead flow is strongly turbulent, intensity ~0.2-0.4") const val TURBULENCE = 0.3
        const val TURB_FLOOR_CM_S = 0.6
        const val MAX_NODE_SPEED = 80.0
        const val MIN_NODE_MASS = 0.15
        const val ROCK_GAP_CM = 0.25
        /** A bitten edge cell with less than this left tears off. */
        const val SHRED_BELOW = 0.3
        /** Edge cells below this that the current stretches past [TEAR_STRAIN] tear off. */
        const val WEAK_BELOW = 0.55
        const val TEAR_STRAIN = 1.3
        const val MAX_STRETCH = 1.4
        const val EDGE_BIAS_CM = 1.5

        /** Into the water from the glass pane nearest the clip (the clip's suction cup sits on it). */
        fun inwardFrom(clip: Vec3, rock: RockData): Vec3 {
            val d = listOf(
                clip.x to Vec3.X, rock.lengthCm - clip.x to Vec3(-1.0, 0.0, 0.0),
                clip.z to Vec3.Z, rock.widthCm - clip.z to Vec3(0.0, 0.0, -1.0),
            )
            return d.minBy { it.first }.second
        }
    }
}
