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

    private fun idx(v: Double) = floor(v / cell).toInt()

    fun rebuild(items: Iterable<T>) {
        for (k in 0 until nUsed) cells[used[k]]!!.clear()
        nUsed = 0
        outside.clear()
        for (it in items) {
            val p = position(it)
            val ix = idx(p.x) - ox; val iy = idx(p.y) - ox; val iz = idx(p.z) - ox
            if (ix < 0 || iy < 0 || iz < 0 || ix >= nx || iy >= ny || iz >= nz) { outside.add(it); continue }
            val k = (iz * ny + iy) * nx + ix
            val l = cells[k] ?: ArrayList<T>(4).also { cells[k] = it }
            if (l.isEmpty()) used[nUsed++] = k
            l.add(it)
        }
    }

    /** All items within [r] of [p] (exact distance check). */
    fun neighbors(p: Vec3, r: Double, out: MutableList<T> = ArrayList()): MutableList<T> {
        val r2 = r * r
        val x0 = maxOf(idx(p.x - r) - ox, 0); val x1 = minOf(idx(p.x + r) - ox, nx - 1)
        val y0 = maxOf(idx(p.y - r) - ox, 0); val y1 = minOf(idx(p.y + r) - ox, ny - 1)
        val z0 = maxOf(idx(p.z - r) - ox, 0); val z1 = minOf(idx(p.z + r) - ox, nz - 1)
        for (ix in x0..x1) for (iy in y0..y1) for (iz in z0..z1) {
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
