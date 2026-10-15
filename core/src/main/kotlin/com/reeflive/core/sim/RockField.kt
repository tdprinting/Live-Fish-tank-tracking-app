package com.reeflive.core.sim

import com.reeflive.core.geom.Ellipsoid
import com.reeflive.core.geom.Sdf
import com.reeflive.core.geom.SdfGrid
import com.reeflive.core.geom.Vec3
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * One loose rock of a rockscape: a group of the analytic blobs ([DefaultScape.blobs]) that moves as a whole. Only used
 * to tell which rock a point of the baked field (or a mesh vertex) belongs to: the nearest piece's smooth union.
 */
class RockPieceDef(val name: String, val blobs: List<Ellipsoid>) {
    /** Bounding box of the blobs (cm): min xyz, max xyz. */
    val box: DoubleArray = DoubleArray(6).also { b ->
        b[0] = blobs.minOf { it.center.x - it.radii.x }; b[1] = blobs.minOf { it.center.y - it.radii.y }; b[2] = blobs.minOf { it.center.z - it.radii.z }
        b[3] = blobs.maxOf { it.center.x + it.radii.x }; b[4] = blobs.maxOf { it.center.y + it.radii.y }; b[5] = blobs.maxOf { it.center.z + it.radii.z }
    }

    /** Smooth-union distance of this rock's blobs (the same log-sum-exp blend as [com.reeflive.core.geom.AnalyticSdf]). */
    fun dist(x: Double, y: Double, z: Double): Double {
        var d = Double.MAX_VALUE
        for (b in blobs) d = min(d, ellipsoid(b, x, y, z))
        var acc = 0.0
        for (b in blobs) { val e = (ellipsoid(b, x, y, z) - d) / SMOOTH_K; if (e < 8) acc += exp(-e) }
        return d - SMOOTH_K * ln(acc)
    }

    companion object {
        private const val SMOOTH_K = 1.5

        /** [Ellipsoid.dist] without the Vec3. */
        fun ellipsoid(e: Ellipsoid, x: Double, y: Double, z: Double): Double {
            val r = e.radii
            val qx = (x - e.center.x) / r.x; val qy = (y - e.center.y) / r.y; val qz = (z - e.center.z) / r.z
            val k0 = sqrt(qx * qx + qy * qy + qz * qz)
            val k1 = sqrt(qx * qx / (r.x * r.x) + qy * qy / (r.y * r.y) + qz * qz / (r.z * r.z))
            return if (k1 < 1e-9) -min(r.x, min(r.y, r.z)) else k0 * (k0 - 1.0) / k1
        }

        /**
         * Which of [pieces] a point belongs to: the nearest by analytic distance, or −1 when none is within [maxDist]
         * (glass, equipment). Used for the baked field's cells and for the renderer's mesh vertices alike.
         */
        fun labelOf(pieces: List<RockPieceDef>, x: Double, y: Double, z: Double, maxDist: Double = Double.MAX_VALUE): Int {
            var best = -1
            var bd = maxDist
            for (i in pieces.indices) {
                val b = pieces[i].box
                // cheap reject: farther from the box than the best so far
                val ex = max(0.0, max(b[0] - x, x - b[3])); val ey = max(0.0, max(b[1] - y, y - b[4])); val ez = max(0.0, max(b[2] - z, z - b[5]))
                if (ex * ex + ey * ey + ez * ez > bd * bd + 1e-9 && best >= 0) continue
                val d = pieces[i].dist(x, y, z)
                if (d < bd) { bd = d; best = i }
            }
            return best
        }
    }
}

/**
 * The rockscape as loose rocks whose field follows them (SIMULATION §5.2 rock SDF, made movable for [RockPhysics]).
 *
 * While nothing has moved, [dist] is the baked [base] field itself: static rock costs nothing. [prepare] (once, when
 * dynamic rocks are switched on) samples the base into a dense grid and labels every rock cell with its [pieces]
 * entry. The first time a rock moves, [markMoved] builds that rock's own distance grid (in its rest frame, from its
 * labelled cells by a chamfer distance transform) and a "static" grid of the rock left behind, and from then on [dist]
 * reads a composite grid: min(static rock, every moved rock at its current transform), refreshed by [refresh] only
 * over the boxes the moved rocks swept.
 */
class DynamicRockSdf(
    val base: Sdf,
    basePieces: List<RockPieceDef>,
    private val lengthCm: Double = TankDims.LENGTH_CM,
    private val heightCm: Double = TankDims.HEIGHT_CM,
    private val widthCm: Double = TankDims.DEPTH_CM,
) : Sdf {
    private val all = ArrayList(basePieces)
    /** The rockscape's rocks: those of the tank as built, then any added with the aquascape builder ([addPiece]). */
    val pieces: List<RockPieceDef> get() = all
    /** How many of [pieces] were baked into [base]. */
    val baseCount = basePieces.size

    /** True once any piece has moved: [dist] reads the composite grid. */
    var active = false; private set
    var prepared = false; private set

    // ---- grid layout (the baked grid's when it is one, else 1 cm over the tank)
    var nx = 0; private set
    var ny = 0; private set
    var nz = 0; private set
    var ox = 0.0; private set
    var oy = 0.0; private set
    var oz = 0.0; private set
    var cell = 1.0; private set

    /** Base field at every cell centre (cm). */
    lateinit var baseD: FloatArray; private set
    /** Piece index of every rock cell (base < 0), −1 elsewhere. */
    lateinit var label: ByteArray; private set
    /** Per piece: the grid box of its cells (i0, j0, k0, i1, j1, k1, inclusive), or null for a piece with no cells. */
    var pieceBox: Array<IntArray?> = emptyArray(); private set
    private var stat: FloatArray? = null
    private var comp: FloatArray? = null

    var moved = BooleanArray(baseCount); private set
    /** Taken out of the tank (aquascape builder): no longer part of the field. */
    var gone = BooleanArray(baseCount); private set
    private var pieceGrid = arrayOfNulls<LocalGrid>(baseCount)
    /** Rest → world transform per piece: x' = R x + t, R row-major (9) then t (3). */
    var xf: Array<DoubleArray> = Array(baseCount) { identityXf() }; private set
    /** World-space grid box each moved piece last covered (cleared when it moves on). */
    private var lastBox = arrayOfNulls<IntArray>(baseCount)
    private val dirty = IntArray(6)
    private var anyDirty = false
    /** Bumped each time the field changes (a [refresh] that re-derived cells); caches over the field key on it. */
    var revision = 0; private set

    override fun dist(p: Vec3): Double = if (!active) base.dist(p) else sample(comp!!, p.x, p.y, p.z)
    override fun distAt(x: Double, y: Double, z: Double): Double = if (!active) base.distAt(x, y, z) else sample(comp!!, x, y, z)
    fun distXyz(x: Double, y: Double, z: Double): Double = distAt(x, y, z)
    override fun cover(p: Vec3): Double = base.cover(p)

    /** The rock that never moved (cm); the base field until something moves. */
    fun staticDist(x: Double, y: Double, z: Double): Double = stat?.let { sample(it, x, y, z) } ?: base.dist(Vec3(x, y, z))

    /** Piece [i]'s own grid in its rest frame (moved and added pieces only). */
    fun localGrid(i: Int): LocalGrid? = pieceGrid[i]

    /** Piece [i] on its own at its current transform (cm); only for moved pieces. */
    fun pieceDist(i: Int, x: Double, y: Double, z: Double): Double {
        if (gone[i]) return 1000.0
        val g = pieceGrid[i] ?: return 1000.0
        val m = xf[i]
        val dx = x - m[9]; val dy = y - m[10]; val dz = z - m[11]
        // R^T (p - t)
        val lx = m[0] * dx + m[3] * dy + m[6] * dz
        val ly = m[1] * dx + m[4] * dy + m[7] * dz
        val lz = m[2] * dx + m[5] * dy + m[8] * dz
        return g.sample(lx, ly, lz)
    }

    // ============================================================== setup
    /** Samples the base and labels the rock cells (≈ 0.1–0.3 s on a phone; only when dynamic rocks are on). */
    fun prepare() {
        if (prepared) return
        val b = base
        if (b is SdfGrid) {
            nx = b.nx; ny = b.ny; nz = b.nz; ox = b.origin.x; oy = b.origin.y; oz = b.origin.z; cell = b.cell
        } else {
            cell = 1.0; ox = 0.5; oy = 0.5; oz = 0.5
            nx = floor(lengthCm).toInt(); ny = floor(heightCm).toInt(); nz = floor(widthCm).toInt()
        }
        val n = nx * ny * nz
        val d = FloatArray(n)
        val lab = ByteArray(n) { -1 }
        for (k in 0 until nz) for (j in 0 until ny) for (i in 0 until nx) {
            val a = idx(i, j, k)
            val x = ox + i * cell; val y = oy + j * cell; val z = oz + k * cell
            val v = b.dist(Vec3(x, y, z))
            d[a] = v.toFloat()
            if (v < 0.0) lab[a] = RockPieceDef.labelOf(all.subList(0, baseCount), x, y, z).toByte()
        }
        baseD = d; label = lab
        pieceBox = Array(baseCount) { p ->
            var i0 = Int.MAX_VALUE; var j0 = Int.MAX_VALUE; var k0 = Int.MAX_VALUE; var i1 = -1; var j1 = -1; var k1 = -1
            for (k in 0 until nz) for (j in 0 until ny) for (i in 0 until nx) if (lab[idx(i, j, k)].toInt() == p) {
                i0 = min(i0, i); j0 = min(j0, j); k0 = min(k0, k); i1 = max(i1, i); j1 = max(j1, j); k1 = max(k1, k)
            }
            if (i1 < 0) null else intArrayOf(i0, j0, k0, i1, j1, k1)
        }
        if (all.size > baseCount) pieceBox = pieceBox.copyOf(all.size)
        prepared = true
    }

    fun idx(i: Int, j: Int, k: Int) = (k * ny + j) * nx + i
    fun cx(i: Int) = ox + i * cell
    fun cy(j: Int) = oy + j * cell
    fun cz(k: Int) = oz + k * cell

    // ============================================================== moving pieces
    /**
     * Lifts [ids] out of the static rock: builds their own grids and re-derives the static grid around where they sat.
     * Their transforms stay as they are (identity for a piece that never moved) until [setTransform].
     */
    fun markMoved(ids: IntArray) {
        prepare()
        val fresh = ids.filter { !moved[it] && pieceBox[it] != null }
        if (fresh.isEmpty()) return
        if (stat == null) { stat = baseD.copyOf(); comp = baseD.copyOf() }
        for (p in fresh) { moved[p] = true; pieceGrid[p] = buildPieceGrid(p) }
        // static rock around the lifted pieces: distance to the cells still in place
        val r = IntArray(6) { if (it < 3) Int.MAX_VALUE else -1 }
        for (p in fresh) { val bx = pieceBox[p]!!; for (c in 0 until 3) { r[c] = min(r[c], bx[c] - PAD); r[c + 3] = max(r[c + 3], bx[c + 3] + PAD) } }
        clampBox(r)
        rebuildStatic(r)
        addDirty(r)
        active = true
        refresh()
    }

    /** Sets piece [i]'s rest → world transform (R row-major, then t); call [refresh] after a batch. */
    fun setTransform(i: Int, m: DoubleArray) {
        m.copyInto(xf[i])
        changed[i] = true
    }
    private var changed = BooleanArray(baseCount)

    /**
     * Adds a rock to the scape (aquascape builder): [def] in its own frame, placed by [setTransform]. Its field is
     * sampled from the analytic blobs; it counts as moved from the start. Returns its piece index.
     */
    fun addPiece(def: RockPieceDef): Int {
        prepare()
        if (stat == null) { stat = baseD.copyOf(); comp = baseD.copyOf() }
        val p = all.size
        all += def
        val n = all.size
        moved = moved.copyOf(n); gone = gone.copyOf(n); changed = changed.copyOf(n)
        pieceGrid = pieceGrid.copyOf(n); lastBox = lastBox.copyOf(n); pieceBox = pieceBox.copyOf(n)
        xf = Array(n) { if (it < p) xf[it] else identityXf() }
        val b = def.box
        val i0 = floor(b[0] / cell).toInt() - PAD; val j0 = floor(b[1] / cell).toInt() - PAD; val k0 = floor(b[2] / cell).toInt() - PAD
        val gx = kotlin.math.ceil((b[3] - b[0]) / cell).toInt() + 1 + 2 * PAD
        val gy = kotlin.math.ceil((b[4] - b[1]) / cell).toInt() + 1 + 2 * PAD
        val gz = kotlin.math.ceil((b[5] - b[2]) / cell).toInt() + 1 + 2 * PAD
        val v = FloatArray(gx * gy * gz)
        for (k in 0 until gz) for (j in 0 until gy) for (i in 0 until gx)
            v[(k * gy + j) * gx + i] = def.dist((i0 + i) * cell, (j0 + j) * cell, (k0 + k) * cell).toFloat()
        pieceGrid[p] = LocalGrid(gx, gy, gz, i0 * cell, j0 * cell, k0 * cell, cell, v)
        moved[p] = true
        active = true
        return p
    }

    /** Takes rock [p] out of the scape (aquascape builder); the field around where it was is re-derived. */
    fun removePiece(p: Int) {
        if (gone[p]) return
        if (!moved[p]) markMoved(intArrayOf(p))
        gone[p] = true
        lastBox[p]?.let { addDirty(it) }
        lastBox[p] = null
        refresh()
    }

    /** Re-derives the composite over the boxes moved pieces left and entered since the last refresh. */
    fun refresh() {
        if (!active) return
        for (p in pieces.indices) if (moved[p] && !gone[p] && (changed[p] || lastBox[p] == null)) {
            changed[p] = false
            val now = worldBox(p)
            lastBox[p]?.let { addDirty(it) }
            addDirty(now)
            lastBox[p] = now
        }
        if (!anyDirty) return
        val st = stat!!; val c = comp!!
        val boxes = pieces.indices.filter { moved[it] && !gone[it] }.map { it to lastBox[it]!! }
        for (k in dirty[2]..dirty[5]) for (j in dirty[1]..dirty[4]) for (i in dirty[0]..dirty[3]) {
            val a = idx(i, j, k)
            var v = st[a].toDouble()
            for ((p, bx) in boxes) {
                if (i < bx[0] || i > bx[3] || j < bx[1] || j > bx[4] || k < bx[2] || k > bx[5]) continue
                v = min(v, pieceDist(p, cx(i), cy(j), cz(k)))
            }
            c[a] = v.toFloat()
        }
        anyDirty = false
        revision++
    }

    /** The cells piece [p] covers at its current transform (its grid's corners, padded), clamped to the grid. */
    fun worldBox(p: Int): IntArray {
        val g = pieceGrid[p]!!
        val m = xf[p]
        var x0 = Double.MAX_VALUE; var y0 = Double.MAX_VALUE; var z0 = Double.MAX_VALUE
        var x1 = -Double.MAX_VALUE; var y1 = -Double.MAX_VALUE; var z1 = -Double.MAX_VALUE
        for (c in 0 until 8) {
            val lx = if (c and 1 == 0) g.ox else g.ox + (g.nx - 1) * g.cell
            val ly = if (c and 2 == 0) g.oy else g.oy + (g.ny - 1) * g.cell
            val lz = if (c and 4 == 0) g.oz else g.oz + (g.nz - 1) * g.cell
            val wx = m[0] * lx + m[1] * ly + m[2] * lz + m[9]
            val wy = m[3] * lx + m[4] * ly + m[5] * lz + m[10]
            val wz = m[6] * lx + m[7] * ly + m[8] * lz + m[11]
            x0 = min(x0, wx); y0 = min(y0, wy); z0 = min(z0, wz); x1 = max(x1, wx); y1 = max(y1, wy); z1 = max(z1, wz)
        }
        val r = intArrayOf(
            floor((x0 - ox) / cell).toInt(), floor((y0 - oy) / cell).toInt(), floor((z0 - oz) / cell).toInt(),
            floor((x1 - ox) / cell).toInt() + 1, floor((y1 - oy) / cell).toInt() + 1, floor((z1 - oz) / cell).toInt() + 1,
        )
        clampBox(r)
        return r
    }

    private fun addDirty(b: IntArray) {
        if (!anyDirty) { b.copyInto(dirty); anyDirty = true; return }
        for (c in 0 until 3) { dirty[c] = min(dirty[c], b[c]); dirty[c + 3] = max(dirty[c + 3], b[c + 3]) }
    }

    private fun clampBox(r: IntArray) {
        r[0] = r[0].coerceIn(0, nx - 1); r[1] = r[1].coerceIn(0, ny - 1); r[2] = r[2].coerceIn(0, nz - 1)
        r[3] = r[3].coerceIn(0, nx - 1); r[4] = r[4].coerceIn(0, ny - 1); r[5] = r[5].coerceIn(0, nz - 1)
    }

    /** Piece [p]'s own field in its rest frame: chamfer distances to / inside its cells, tightened by the base. */
    private fun buildPieceGrid(p: Int): LocalGrid {
        val bx = pieceBox[p]!!
        val i0 = bx[0] - PAD; val j0 = bx[1] - PAD; val k0 = bx[2] - PAD
        val gx = bx[3] - bx[0] + 1 + 2 * PAD; val gy = bx[4] - bx[1] + 1 + 2 * PAD; val gz = bx[5] - bx[2] + 1 + 2 * PAD
        val inside = BooleanArray(gx * gy * gz)
        for (k in 0 until gz) for (j in 0 until gy) for (i in 0 until gx) {
            val wi = i0 + i; val wj = j0 + j; val wk = k0 + k
            if (wi in 0 until nx && wj in 0 until ny && wk in 0 until nz && label[idx(wi, wj, wk)].toInt() == p) inside[(k * gy + j) * gx + i] = true
        }
        val dOut = Chamfer.distance(inside, gx, gy, gz, true)
        val dIn = Chamfer.distance(inside, gx, gy, gz, false)
        val v = FloatArray(gx * gy * gz)
        for (k in 0 until gz) for (j in 0 until gy) for (i in 0 until gx) {
            val a = (k * gy + j) * gx + i
            val wi = i0 + i; val wj = j0 + j; val wk = k0 + k
            val b = if (wi in 0 until nx && wj in 0 until ny && wk in 0 until nz) baseD[idx(wi, wj, wk)].toDouble() else -1e9
            v[a] = if (inside[a]) max(b, -(dIn[a] - 0.5) * cell).toFloat()
            else max(b, (dOut[a] - 0.5) * cell * CHAMFER_SAFE).toFloat()
        }
        return LocalGrid(gx, gy, gz, cx(i0), cy(j0), cz(k0), cell, v)
    }

    /** Static grid over [r]: distance to rock cells whose piece has not moved (chamfer within [r] padded). */
    private fun rebuildStatic(r: IntArray) {
        val st = stat!!
        val i0 = r[0] - PAD; val j0 = r[1] - PAD; val k0 = r[2] - PAD
        val gx = r[3] - r[0] + 1 + 2 * PAD; val gy = r[4] - r[1] + 1 + 2 * PAD; val gz = r[5] - r[2] + 1 + 2 * PAD
        val inside = BooleanArray(gx * gy * gz)
        for (k in 0 until gz) for (j in 0 until gy) for (i in 0 until gx) {
            val wi = i0 + i; val wj = j0 + j; val wk = k0 + k
            if (wi !in 0 until nx || wj !in 0 until ny || wk !in 0 until nz) continue
            val l = label[idx(wi, wj, wk)].toInt()
            if (l >= 0 && !moved[l]) inside[(k * gy + j) * gx + i] = true
        }
        val dOut = Chamfer.distance(inside, gx, gy, gz, true)
        val dIn = Chamfer.distance(inside, gx, gy, gz, false)
        val far = PAD * cell
        for (k in r[2]..r[5]) for (j in r[1]..r[4]) for (i in r[0]..r[3]) {
            val a = idx(i, j, k)
            val la = ((k - k0) * gy + (j - j0)) * gx + (i - i0)
            val b = baseD[a].toDouble()
            st[a] = if (inside[la]) max(b, -(dIn[la] - 0.5) * cell).toFloat()
            else max(b, min((dOut[la] - 0.5) * cell * CHAMFER_SAFE, far)).toFloat()
        }
    }

    // ============================================================== sampling
    private fun sample(g: FloatArray, x: Double, y: Double, z: Double): Double =
        trilinear(g, nx, ny, nz, ox, oy, oz, cell, x, y, z)

    /** A piece's own field in its rest frame. */
    class LocalGrid(val nx: Int, val ny: Int, val nz: Int, val ox: Double, val oy: Double, val oz: Double, val cell: Double, val d: FloatArray) {
        fun sample(x: Double, y: Double, z: Double) = trilinear(d, nx, ny, nz, ox, oy, oz, cell, x, y, z)
    }

    companion object {
        /** Cells of margin around a piece's own grid and around the static rebuild. */
        const val PAD = 6
        /** Chamfer steps overestimate the true distance by up to ~8 %; scaled down to stay conservative. */
        private const val CHAMFER_SAFE = 0.93

        fun identityXf() = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0)

        /** Trilinear lookup; outside the box adds the distance to it (like [SdfGrid.dist]). */
        fun trilinear(d: FloatArray, nx: Int, ny: Int, nz: Int, ox: Double, oy: Double, oz: Double, cell: Double, x: Double, y: Double, z: Double): Double {
            val fx = ((x - ox) / cell).coerceIn(0.0, nx - 1.0); val fy = ((y - oy) / cell).coerceIn(0.0, ny - 1.0); val fz = ((z - oz) / cell).coerceIn(0.0, nz - 1.0)
            val x0 = min(fx.toInt(), nx - 2).coerceAtLeast(0); val y0 = min(fy.toInt(), ny - 2).coerceAtLeast(0); val z0 = min(fz.toInt(), nz - 2).coerceAtLeast(0)
            val tx = fx - x0; val ty = fy - y0; val tz = fz - z0
            val sx = if (nx > 1) 1 else 0; val sy = if (ny > 1) nx else 0; val sz = if (nz > 1) nx * ny else 0
            val a = (z0 * ny + y0) * nx + x0
            val c00 = d[a] + (d[a + sx] - d[a]) * tx
            val c10 = d[a + sy] + (d[a + sy + sx] - d[a + sy]) * tx
            val c01 = d[a + sz] + (d[a + sz + sx] - d[a + sz]) * tx
            val c11 = d[a + sz + sy] + (d[a + sz + sy + sx] - d[a + sz + sy]) * tx
            val c0 = c00 + (c10 - c00) * ty
            val c1 = c01 + (c11 - c01) * ty
            var v = c0 + (c1 - c0) * tz
            val ex = max(0.0, max(ox - x, x - (ox + (nx - 1) * cell)))
            val ey = max(0.0, max(oy - y, y - (oy + (ny - 1) * cell)))
            val ez = max(0.0, max(oz - z, z - (oz + (nz - 1) * cell)))
            if (ex + ey + ez > 0) v += sqrt(ex * ex + ey * ey + ez * ez)
            return v
        }
    }
}

/** Two-pass 3-D chamfer distance transform with Euclidean neighbour steps (in cells). */
object Chamfer {
    private val S2 = sqrt(2.0)
    private val S3 = sqrt(3.0)

    /**
     * Distance (cells, centre to centre) from every cell to the nearest cell whose [mask] equals [target]; 0 on those
     * cells. Unreached cells stay at a large value.
     */
    fun distance(mask: BooleanArray, nx: Int, ny: Int, nz: Int, target: Boolean): DoubleArray {
        val big = 1e6
        val d = DoubleArray(nx * ny * nz) { if (mask[it] == target) 0.0 else big }
        // forward: neighbours already visited in x-fastest order (half of the 26)
        for (k in 0 until nz) for (j in 0 until ny) for (i in 0 until nx) {
            val a = (k * ny + j) * nx + i
            var v = d[a]
            if (v == 0.0) continue
            for (dk in -1..0) for (dj in -1..1) for (di in -1..1) {
                if (dk == 0 && (dj > 0 || (dj == 0 && di >= 0))) continue
                val ii = i + di; val jj = j + dj; val kk = k + dk
                if (ii < 0 || jj < 0 || kk < 0 || ii >= nx || jj >= ny) continue
                val w = step(di, dj, dk)
                val c = d[(kk * ny + jj) * nx + ii] + w
                if (c < v) v = c
            }
            d[a] = v
        }
        for (k in nz - 1 downTo 0) for (j in ny - 1 downTo 0) for (i in nx - 1 downTo 0) {
            val a = (k * ny + j) * nx + i
            var v = d[a]
            if (v == 0.0) continue
            for (dk in 0..1) for (dj in -1..1) for (di in -1..1) {
                if (dk == 0 && (dj < 0 || (dj == 0 && di <= 0))) continue
                val ii = i + di; val jj = j + dj; val kk = k + dk
                if (ii < 0 || jj < 0 || ii >= nx || jj >= ny || kk >= nz) continue
                val w = step(di, dj, dk)
                val c = d[(kk * ny + jj) * nx + ii] + w
                if (c < v) v = c
            }
            d[a] = v
        }
        return d
    }

    private fun step(di: Int, dj: Int, dk: Int): Double = when ((if (di != 0) 1 else 0) + (if (dj != 0) 1 else 0) + (if (dk != 0) 1 else 0)) {
        1 -> 1.0; 2 -> S2; else -> S3
    }
}
