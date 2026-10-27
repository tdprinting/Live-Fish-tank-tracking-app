package com.reeflive.app.render

import com.reeflive.core.sim.DryRock
import com.reeflive.core.sim.DryRockShape
import com.reeflive.core.sim.RockPieceDef
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * The mesh of a dry rock from the aquascape builder, made from the same blobs the sim collides with
 * ([DryRock.def]) so what you see is what fish swim around. Surface nets over the blobs' distance field, pitted and
 * eroded by value noise that only ever cuts into the rock (the drawn surface stays inside the collision shape), with
 * normals from the field's gradient. COLOR_0 carries what rock.frag reads from the scape mesh (tools/blender/
 * rock_surface.py): r cavity, g ridge (from the field's curvature), b open sky, a broad blotch noise. In the rock's
 * own frame; built once per shape and seed (a few ms).
 */
object DryRockMesh {
    private const val CELL = 0.5f
    private const val MEMO_MAX = 96
    /** Deepest pit / erosion the noise cuts (cm). */
    private const val ERODE_CM = 0.9
    /** Box-projected uv: cm per texture tile. */
    private const val UV_CM = 24f

    class Data(val vertices: FloatArray, val indices: IntArray)

    fun build(shape: DryRockShape, seed: Int): Data = build(DryRock.def(shape, seed), seed)

    private val memo = LinkedHashMap<Long, Data>()
    private fun key(shape: DryRockShape, seed: Int) = shape.ordinal.toLong() shl 32 or (seed.toLong() and 0xffffffffL)

    /** The shape's mesh data, made once per shape and seed (the last 96 are kept); a store's rocks are made ahead of the visit. */
    fun cached(shape: DryRockShape, seed: Int): Data {
        val k = key(shape, seed)
        memo[k]?.let { return it }
        val d = build(shape, seed)
        memo[k] = d
        if (memo.size > MEMO_MAX) { val it = memo.entries.iterator(); it.next(); it.remove() }
        return d
    }

    fun isCached(shape: DryRockShape, seed: Int): Boolean = memo.containsKey(key(shape, seed))

    fun build(def: RockPieceDef, seed: Int): Data {
        val b = def.box
        val pad = 1.5
        val x0 = b[0] - pad; val y0 = b[1] - pad; val z0 = b[2] - pad
        val nx = ((b[3] - b[0] + 2 * pad) / CELL).toInt() + 2
        val ny = ((b[4] - b[1] + 2 * pad) / CELL).toInt() + 2
        val nz = ((b[5] - b[2] + 2 * pad) / CELL).toInt() + 2
        val ns = seed * 0.6180339 % 97.0
        fun f(x: Double, y: Double, z: Double): Double {
            val d = def.dist(x, y, z)
            if (d > ERODE_CM + 1.0) return d
            // broad erosion plus pocked pits; never adds material
            val n = 0.55 * noise(x * 0.22 + ns, y * 0.22, z * 0.22) + 0.3 * noise(x * 0.6, y * 0.6 + ns, z * 0.6) + 0.15 * noise(x * 1.5, y * 1.5, z * 1.5 + ns)
            val pit = noise(x * 0.9 + 31.0, y * 0.9 + ns, z * 0.9 + 7.0)
            return d + ERODE_CM * (0.6 * n + 0.6 * smooth(0.62, 0.8, pit))
        }
        val v = FloatArray(nx * ny * nz)
        for (k in 0 until nz) for (j in 0 until ny) for (i in 0 until nx)
            v[(k * ny + j) * nx + i] = f(x0 + i * CELL, y0 + j * CELL, z0 + k * CELL).toFloat()
        fun at(i: Int, j: Int, k: Int) = v[(k * ny + j) * nx + i]

        // one vertex per cube the surface passes through: the mean of its edge crossings
        val cubeVert = IntArray((nx - 1) * (ny - 1) * (nz - 1)) { -1 }
        val pos = ArrayList<Float>()
        val corner = FloatArray(8)
        for (k in 0 until nz - 1) for (j in 0 until ny - 1) for (i in 0 until nx - 1) {
            var inside = 0
            for (c in 0 until 8) { corner[c] = at(i + (c and 1), j + (c shr 1 and 1), k + (c shr 2 and 1)); if (corner[c] < 0f) inside++ }
            if (inside == 0 || inside == 8) continue
            var sx = 0f; var sy = 0f; var sz = 0f; var n = 0
            for (e in EDGES) {
                val a = corner[e[0]]; val c = corner[e[1]]
                if ((a < 0f) == (c < 0f)) continue
                val t = a / (a - c)
                val ax = (e[0] and 1).toFloat(); val ay = (e[0] shr 1 and 1).toFloat(); val az = (e[0] shr 2 and 1).toFloat()
                val cx = (e[1] and 1).toFloat(); val cy = (e[1] shr 1 and 1).toFloat(); val cz = (e[1] shr 2 and 1).toFloat()
                sx += ax + (cx - ax) * t; sy += ay + (cy - ay) * t; sz += az + (cz - az) * t; n++
            }
            cubeVert[(k * (ny - 1) + j) * (nx - 1) + i] = pos.size / 3
            pos += (x0 + (i + sx / n) * CELL).toFloat(); pos += (y0 + (j + sy / n) * CELL).toFloat(); pos += (z0 + (k + sz / n) * CELL).toFloat()
        }
        fun cv(i: Int, j: Int, k: Int) = cubeVert[(k * (ny - 1) + j) * (nx - 1) + i]

        // vertex normals from the field's gradient
        val nv = pos.size / 3
        val nrm = FloatArray(nv * 3)
        val h = 0.25
        for (q in 0 until nv) {
            val x = pos[q * 3].toDouble(); val y = pos[q * 3 + 1].toDouble(); val z = pos[q * 3 + 2].toDouble()
            var gx = f(x + h, y, z) - f(x - h, y, z); var gy = f(x, y + h, z) - f(x, y - h, z); var gz = f(x, y, z + h) - f(x, y, z - h)
            val l = sqrt(gx * gx + gy * gy + gz * gz).coerceAtLeast(1e-9)
            gx /= l; gy /= l; gz /= l
            nrm[q * 3] = gx.toFloat(); nrm[q * 3 + 1] = gy.toFloat(); nrm[q * 3 + 2] = gz.toFloat()
        }

        // a quad across every grid edge the surface crosses, wound to face out
        val idx = ArrayList<Int>()
        fun quad(a: Int, b2: Int, c: Int, d: Int) {
            if (a < 0 || b2 < 0 || c < 0 || d < 0) return
            // orient by the averaged vertex normal
            val ux = pos[b2 * 3] - pos[a * 3]; val uy = pos[b2 * 3 + 1] - pos[a * 3 + 1]; val uz = pos[b2 * 3 + 2] - pos[a * 3 + 2]
            val wx = pos[c * 3] - pos[a * 3]; val wy = pos[c * 3 + 1] - pos[a * 3 + 1]; val wz = pos[c * 3 + 2] - pos[a * 3 + 2]
            val fx = uy * wz - uz * wy; val fy = uz * wx - ux * wz; val fz = ux * wy - uy * wx
            val mx = nrm[a * 3] + nrm[c * 3]; val my = nrm[a * 3 + 1] + nrm[c * 3 + 1]; val mz = nrm[a * 3 + 2] + nrm[c * 3 + 2]
            if (fx * mx + fy * my + fz * mz >= 0f) { idx += a; idx += b2; idx += c; idx += a; idx += c; idx += d }
            else { idx += a; idx += c; idx += b2; idx += a; idx += d; idx += c }
        }
        for (k in 1 until nz - 1) for (j in 1 until ny - 1) for (i in 0 until nx - 1)
            if ((at(i, j, k) < 0f) != (at(i + 1, j, k) < 0f)) quad(cv(i, j - 1, k - 1), cv(i, j, k - 1), cv(i, j, k), cv(i, j - 1, k))
        for (k in 1 until nz - 1) for (j in 0 until ny - 1) for (i in 1 until nx - 1)
            if ((at(i, j, k) < 0f) != (at(i, j + 1, k) < 0f)) quad(cv(i - 1, j, k - 1), cv(i, j, k - 1), cv(i, j, k), cv(i - 1, j, k))
        for (k in 0 until nz - 1) for (j in 1 until ny - 1) for (i in 1 until nx - 1)
            if ((at(i, j, k) < 0f) != (at(i, j, k + 1) < 0f)) quad(cv(i - 1, j - 1, k), cv(i, j - 1, k), cv(i, j, k), cv(i - 1, j, k))

        val s = GlbPrimitive.STRIDE
        val out = FloatArray(nv * s)
        for (q in 0 until nv) {
            val o = q * s
            val x = pos[q * 3]; val y = pos[q * 3 + 1]; val z = pos[q * 3 + 2]
            val ax = abs(nrm[q * 3]); val ay = abs(nrm[q * 3 + 1]); val az = abs(nrm[q * 3 + 2])
            out[o] = x; out[o + 1] = y; out[o + 2] = z
            out[o + 3] = nrm[q * 3]; out[o + 4] = nrm[q * 3 + 1]; out[o + 5] = nrm[q * 3 + 2]
            // box projection on the dominant axis
            if (ay >= ax && ay >= az) { out[o + 6] = x / UV_CM; out[o + 7] = z / UV_CM }
            else if (ax >= az) { out[o + 6] = z / UV_CM; out[o + 7] = y / UV_CM }
            else { out[o + 6] = x / UV_CM; out[o + 7] = y / UV_CM }
            // curvature over ~1.5 cm: the field's Laplacian is about 2 / radius on a convex bump, negative in a pocket
            val xd = x.toDouble(); val yd = y.toDouble(); val zd = z.toDouble(); val hc = 1.5
            val lap = (f(xd + hc, yd, zd) + f(xd - hc, yd, zd) + f(xd, yd + hc, zd) + f(xd, yd - hc, zd) + f(xd, yd, zd + hc) + f(xd, yd, zd - hc) - 6 * f(xd, yd, zd)) / (hc * hc)
            out[o + 8] = ((-lap - 0.1) * 2.0).coerceIn(0.0, 1.0).toFloat()
            out[o + 9] = ((lap - 0.3) * 1.5).coerceIn(0.0, 1.0).toFloat()
            // undersides see less of the water above
            out[o + 10] = (0.55 + 0.45 * (0.5 + 0.5 * nrm[q * 3 + 1])).toFloat()
            out[o + 11] = noise(xd * 0.05 + 3.1 + ns, yd * 0.05 + 7.7, zd * 0.05 + 1.3).toFloat()
        }
        return Data(out, idx.toIntArray())
    }

    private val EDGES = arrayOf(
        intArrayOf(0, 1), intArrayOf(2, 3), intArrayOf(4, 5), intArrayOf(6, 7),
        intArrayOf(0, 2), intArrayOf(1, 3), intArrayOf(4, 6), intArrayOf(5, 7),
        intArrayOf(0, 4), intArrayOf(1, 5), intArrayOf(2, 6), intArrayOf(3, 7),
    )

    private fun smooth(a: Double, b: Double, x: Double): Double { val t = ((x - a) / (b - a)).coerceIn(0.0, 1.0); return t * t * (3 - 2 * t) }

    /** 3-D value noise in 0..1 (hashed lattice, smoothstep blend). */
    fun noise(x: Double, y: Double, z: Double): Double {
        val ix = floor(x).toInt(); val iy = floor(y).toInt(); val iz = floor(z).toInt()
        val fx = x - ix; val fy = y - iy; val fz = z - iz
        val ux = fx * fx * (3 - 2 * fx); val uy = fy * fy * (3 - 2 * fy); val uz = fz * fz * (3 - 2 * fz)
        fun hsh(a: Int, b: Int, c: Int): Double {
            var h = a * 374761393 + b * 668265263 + c * 1274126177
            h = (h xor (h ushr 13)) * 1274126177
            return ((h xor (h ushr 16)) and 0xffff) / 65535.0
        }
        fun lx(b: Int, c: Int) = hsh(ix, iy + b, iz + c) + (hsh(ix + 1, iy + b, iz + c) - hsh(ix, iy + b, iz + c)) * ux
        val y0 = lx(0, 0) + (lx(1, 0) - lx(0, 0)) * uy
        val y1 = lx(0, 1) + (lx(1, 1) - lx(0, 1)) * uy
        return y0 + (y1 - y0) * uz
    }
}
