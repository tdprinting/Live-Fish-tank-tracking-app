package com.reeflive.core.sim

import com.reeflive.core.geom.Sdf
import com.reeflive.core.geom.Vec3
import kotlin.math.floor

/**
 * The water a body can reach from the open tank (SIMULATION §4.3): a flood fill, on a [CELL] grid, from the water just
 * under the surface through every cell at least `clear` cm from the rock and above the sand, one per body thickness
 * ([LEVELS]).
 *
 * The baked rock holds a few sealed hollows (closed shells of the scanned rock with water inside, or caves whose mouths are narrower than the animal). A fish placed in
 * one, or carried into one, could never leave once its body no longer passes through rock, so a body whose
 * middle sits in a hollow ([sealedIn]) is let through the rock until it is back in the open water.
 */
class OpenWater(rock: RockData, private val clear: Double = CLEAR) {
    private val nx = (rock.lengthCm / CELL).toInt() + 1
    private val ny = (rock.waterLevelCm / CELL).toInt() + 1
    private val nz = (rock.widthCm / CELL).toInt() + 1
    /** 0 = blocked (rock, sand), 1 = water not reached from the surface, 2 = open water. */
    private val state = ByteArray(nx * ny * nz)

    init {
        val sdf = rock.sdf
        for (iz in 0 until nz) for (iy in 0 until ny) for (ix in 0 until nx) {
            val x = (ix + 0.5) * CELL; val y = (iy + 0.5) * CELL; val z = (iz + 0.5) * CELL
            val water = y > rock.floorY(x, z) && sdf.dist(Vec3(x, y, z)) > clear
            state[idx(ix, iy, iz)] = if (water) 1 else 0
        }
        val stack = IntArray(nx * ny * nz)
        var sp = 0
        val top = ny - 2
        for (iz in 0 until nz) for (ix in 0 until nx) {
            val i = idx(ix, top, iz)
            if (state[i] == 1.toByte()) { state[i] = 2; stack[sp++] = i }
        }
        while (sp > 0) {
            val i = stack[--sp]
            val ix = i % nx; val iy = (i / nx) % ny; val iz = i / (nx * ny)
            fun visit(jx: Int, jy: Int, jz: Int) {
                if (jx < 0 || jy < 0 || jz < 0 || jx >= nx || jy >= ny || jz >= nz) return
                val j = idx(jx, jy, jz)
                if (state[j] == 1.toByte()) { state[j] = 2; stack[sp++] = j }
            }
            visit(ix - 1, iy, iz); visit(ix + 1, iy, iz)
            visit(ix, iy - 1, iz); visit(ix, iy + 1, iz)
            visit(ix, iy, iz - 1); visit(ix, iy, iz + 1)
        }
    }

    private fun idx(ix: Int, iy: Int, iz: Int) = (iz * ny + iy) * nx + ix

    private fun cellOf(p: Vec3): Int {
        val ix = floor(p.x / CELL).toInt().coerceIn(0, nx - 1)
        val iy = floor(p.y / CELL).toInt().coerceIn(0, ny - 1)
        val iz = floor(p.z / CELL).toInt().coerceIn(0, nz - 1)
        return idx(ix, iy, iz)
    }

    /**
     * Whether [p] is in a sealed hollow: water the open tank does not reach, with no open water within [NEAR] cells (3 cm)
     * (a body pressed against the rock or in a narrow gap has open water beside it and is not sealed in).
     */
    fun sealedIn(p: Vec3): Boolean {
        if (state[cellOf(p)] != 1.toByte()) return false
        val cx = floor(p.x / CELL).toInt(); val cy = floor(p.y / CELL).toInt(); val cz = floor(p.z / CELL).toInt()
        for (dz in -NEAR..NEAR) for (dy in -NEAR..NEAR) for (dx in -NEAR..NEAR) {
            val x = cx + dx; val y = cy + dy; val z = cz + dz
            if (x < 0 || y < 0 || z < 0 || x >= nx || y >= ny || z >= nz) continue
            if (state[idx(x, y, z)] == 2.toByte()) return false
        }
        return true
    }

    /** Whether [p] is open water the tank's swimmers can reach (for placements). */
    fun open(p: Vec3): Boolean = state[cellOf(p)] == 2.toByte()

    companion object {
        const val CELL = 0.5
        /** Narrowest gap a body is taken to pass (cm from the rock at the cell centre: a 0.8 cm opening). */
        const val CLEAR = 0.4
        const val NEAR = 6

        /** Clearances a body is matched to: the first at least its slimmest half-thickness plus 2 mm. */
        val LEVELS = doubleArrayOf(0.4, 0.8, 1.2, 1.6)

        private val cache = HashMap<Pair<Sdf, Double>, OpenWater>()

        /** Shared by every simulation on the same rock (the sand under it is the modelled bed at first use). */
        fun of(rock: RockData, clear: Double = CLEAR): OpenWater = synchronized(cache) { cache.getOrPut(rock.sdf to clear) { OpenWater(rock, clear) } }
    }
}
