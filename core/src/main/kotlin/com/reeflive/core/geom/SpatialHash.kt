package com.reeflive.core.geom

import kotlin.math.floor

/** Uniform grid hash for neighbour queries (SIMULATION §4.2, cell 15 cm). Rebuilt every tick. */
class SpatialHash<T>(private val cell: Double = 15.0, private val position: (T) -> Vec3) {
    private val cells = HashMap<Long, MutableList<T>>()
    private val pool = ArrayList<MutableList<T>>()

    private fun key(ix: Int, iy: Int, iz: Int): Long =
        ((ix.toLong() and 0x1FFFFF) shl 42) or ((iy.toLong() and 0x1FFFFF) shl 21) or (iz.toLong() and 0x1FFFFF)

    private fun idx(v: Double) = floor(v / cell).toInt()

    fun rebuild(items: Iterable<T>) {
        for (l in cells.values) { l.clear(); pool.add(l) }
        cells.clear()
        for (it in items) {
            val p = position(it)
            val k = key(idx(p.x), idx(p.y), idx(p.z))
            cells.getOrPut(k) { if (pool.isEmpty()) ArrayList(4) else pool.removeAt(pool.size - 1) }.add(it)
        }
    }

    /** All items within [r] of [p] (exact distance check). */
    fun neighbors(p: Vec3, r: Double, out: MutableList<T> = ArrayList()): MutableList<T> {
        val r2 = r * r
        val x0 = idx(p.x - r); val x1 = idx(p.x + r)
        val y0 = idx(p.y - r); val y1 = idx(p.y + r)
        val z0 = idx(p.z - r); val z1 = idx(p.z + r)
        for (ix in x0..x1) for (iy in y0..y1) for (iz in z0..z1) {
            val l = cells[key(ix, iy, iz)] ?: continue
            for (it in l) if (position(it).distanceSqTo(p) <= r2) out.add(it)
        }
        return out
    }
}
