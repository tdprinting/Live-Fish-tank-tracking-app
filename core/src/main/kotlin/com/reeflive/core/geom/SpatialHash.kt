package com.reeflive.core.geom

import kotlin.math.floor

/**
 * Uniform grid for neighbour queries (SIMULATION §4.2, cell 15 cm). Rebuilt every tick.
 *
 * The cells are a dense array over a box well beyond the tank ([MIN_CM]..[MAX_CM]), indexed directly: a query of
 * radius 40 cm visits ~200 cells, and a hashed lookup per cell (64-bit keys, emulated and slow in the web build) cost
 * more than the whole rest of the behaviour tick with a few dozen animals. Anything outside the box (never, in a tank)
 * goes to one overflow list that every query checks.
 */
class SpatialHash<T>(private val cell: Double = 15.0, private val position: (T) -> Vec3) {
    private val ox = floor(MIN_CM / cell).toInt()
    private val nx = floor(MAX_X_CM / cell).toInt() - ox + 1
    private val ny = floor(MAX_YZ_CM / cell).toInt() - ox + 1
    private val nz = ny
    @Suppress("UNCHECKED_CAST")
    private val cells = arrayOfNulls<ArrayList<T>>(nx * ny * nz) as Array<ArrayList<T>?>
    private val used = IntArray(nx * ny * nz)
    private var nUsed = 0
    private val outside = ArrayList<T>()
    // bounds of the occupied cells (inclusive), so a query skips the empty margin of the dense box
    private var bx0 = 0; private var bx1 = -1
    private var by0 = 0; private var by1 = -1
    private var bz0 = 0; private var bz1 = -1

    // the occupied cells in query order (ix, then iy, then iz): a query wider than there are occupied cells walks these
    // instead of every cell of its box (same cells, same order, so the same result)
    private var lexKey = IntArray(64)
    private var lexCell = IntArray(64)
    private var lexX = IntArray(64)
    private var lexY = IntArray(64)
    private var lexZ = IntArray(64)

    private fun idx(v: Double) = floor(v / cell).toInt()

    fun rebuild(items: Iterable<T>) {
        for (k in 0 until nUsed) cells[used[k]]!!.clear()
        nUsed = 0
        outside.clear()
        bx0 = Int.MAX_VALUE; by0 = Int.MAX_VALUE; bz0 = Int.MAX_VALUE; bx1 = -1; by1 = -1; bz1 = -1
        for (it in items) {
            val p = position(it)
            val ix = idx(p.x) - ox; val iy = idx(p.y) - ox; val iz = idx(p.z) - ox
            if (ix < 0 || iy < 0 || iz < 0 || ix >= nx || iy >= ny || iz >= nz) { outside.add(it); continue }
            val k = (iz * ny + iy) * nx + ix
            val l = cells[k] ?: ArrayList<T>(4).also { cells[k] = it }
            if (l.isEmpty()) used[nUsed++] = k
            l.add(it)
            if (ix < bx0) bx0 = ix; if (ix > bx1) bx1 = ix
            if (iy < by0) by0 = iy; if (iy > by1) by1 = iy
            if (iz < bz0) bz0 = iz; if (iz > bz1) bz1 = iz
        }
        if (lexKey.size < nUsed) {
            val n = maxOf(nUsed, 2 * lexKey.size)
            lexKey = IntArray(n); lexCell = IntArray(n); lexX = IntArray(n); lexY = IntArray(n); lexZ = IntArray(n)
        }
        // insertion sort of the occupied cells by (ix, iy, iz): a few dozen per tick
        for (u in 0 until nUsed) {
            val k = used[u]
            val ix = k % nx; val iy = (k / nx) % ny; val iz = k / (nx * ny)
            val key = (ix * ny + iy) * nz + iz
            var j = u
            while (j > 0 && lexKey[j - 1] > key) {
                lexKey[j] = lexKey[j - 1]; lexCell[j] = lexCell[j - 1]; lexX[j] = lexX[j - 1]; lexY[j] = lexY[j - 1]; lexZ[j] = lexZ[j - 1]
                j--
            }
            lexKey[j] = key; lexCell[j] = k; lexX[j] = ix; lexY[j] = iy; lexZ[j] = iz
        }
    }

    /** All items within [r] of [p] (exact distance check). */
    fun neighbors(p: Vec3, r: Double, out: MutableList<T> = ArrayList()): MutableList<T> {
        val r2 = r * r
        // (clamped to the occupied cells: the ones outside are empty, so the result and its order are the same)
        val x0 = maxOf(idx(p.x - r) - ox, bx0); val x1 = minOf(idx(p.x + r) - ox, bx1)
        val y0 = maxOf(idx(p.y - r) - ox, by0); val y1 = minOf(idx(p.y + r) - ox, by1)
        val z0 = maxOf(idx(p.z - r) - ox, bz0); val z1 = minOf(idx(p.z + r) - ox, bz1)
        if (x1 >= x0 && y1 >= y0 && z1 >= z0 && (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1) > nUsed) {
            for (u in 0 until nUsed) {
                val ix = lexX[u]; val iy = lexY[u]; val iz = lexZ[u]
                if (ix < x0 || ix > x1 || iy < y0 || iy > y1 || iz < z0 || iz > z1) continue
                val l = cells[lexCell[u]]!!
                for (i in 0 until l.size) { val it = l[i]; if (position(it).distanceSqTo(p) <= r2) out.add(it) }
            }
        } else for (ix in x0..x1) for (iy in y0..y1) for (iz in z0..z1) {
            val l = cells[(iz * ny + iy) * nx + ix] ?: continue
            for (i in 0 until l.size) { val it = l[i]; if (position(it).distanceSqTo(p) <= r2) out.add(it) }
        }
        for (i in 0 until outside.size) { val it = outside[i]; if (position(it).distanceSqTo(p) <= r2) out.add(it) }
        return out
    }

    companion object {
        /** The dense box (cm, tank coordinates): the 122 cm display tank, the nursery beside it and room to spare. */
        const val MIN_CM = -150.0
        const val MAX_X_CM = 350.0
        const val MAX_YZ_CM = 200.0
    }
}
