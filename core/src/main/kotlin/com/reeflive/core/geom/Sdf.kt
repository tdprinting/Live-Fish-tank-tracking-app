package com.reeflive.core.geom

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Signed distance to the rockscape in cm (positive outside rock). Sand and glass are NOT part of
 * this field; they are analytic planes handled by Steering (SIMULATION §4.2).
 */
interface Sdf {
    fun dist(p: Vec3): Double

    /** [dist] at (x, y, z) without building a [Vec3]: the baked grid answers allocation-free (camera hot path). */
    fun distAt(x: Double, y: Double, z: Double): Double = dist(Vec3(x, y, z))

    /** Outward unit normal (central differences). */
    fun gradient(p: Vec3, h: Double = 0.5): Vec3 {
        val dx = dist(Vec3(p.x + h, p.y, p.z)) - dist(Vec3(p.x - h, p.y, p.z))
        val dy = dist(Vec3(p.x, p.y + h, p.z)) - dist(Vec3(p.x, p.y - h, p.z))
        val dz = dist(Vec3(p.x, p.y, p.z + h)) - dist(Vec3(p.x, p.y, p.z - h))
        return Vec3(dx, dy, dz).normalized(Vec3.Y)
    }

    /** "How enclosed" a point is: fraction of 26 rays that hit rock within 20 cm (0 open … 1 cave). */
    fun cover(p: Vec3): Double

    /**
     * Moves [p] onto the surface (sdf = [offset]) along the gradient. In a crease the finite-difference gradient can
     * point the wrong way and the steps wander off; the iterate nearest the surface is returned, never one worse than [p].
     */
    fun project(p: Vec3, offset: Double = 0.0, iterations: Int = 8): Vec3 {
        var q = p
        var best = p
        var bestErr = Double.MAX_VALUE
        repeat(iterations) {
            val d = dist(q) - offset
            if (kotlin.math.abs(d) < 0.02) return q
            if (kotlin.math.abs(d) < bestErr) { bestErr = kotlin.math.abs(d); best = q }
            q = q - gradient(q) * d
        }
        return if (kotlin.math.abs(dist(q) - offset) <= bestErr) q else best
    }

    /** Sphere-traced ray hit distance within [maxDist], or null. */
    fun raycast(origin: Vec3, dir: Vec3, maxDist: Double): Double? {
        var t = 0.0
        val d = dir.normalized()
        var i = 0
        while (t < maxDist && i < 64) {
            val s = dist(origin + d * t)
            if (s < 0.05) return t
            t += max(s, 0.1)
            i++
        }
        return null
    }

    /** True if the straight segment a→b stays at least [clearance] outside rock (sampled). */
    fun segmentClear(a: Vec3, b: Vec3, clearance: Double = 0.0): Boolean {
        val len = a.distanceTo(b)
        val n = max(2, (len / 1.5).toInt())
        for (i in 0..n) if (dist(a.lerp(b, i.toDouble() / n)) < clearance) return false
        return true
    }

    companion object {
        /** The 26 cover-ray directions (unit cube neighbourhood). */
        val COVER_DIRS: List<Vec3> = buildList {
            for (x in -1..1) for (y in -1..1) for (z in -1..1) if (x != 0 || y != 0 || z != 0)
                add(Vec3(x.toDouble(), y.toDouble(), z.toDouble()).normalized())
        }
    }
}

/** Shared cover implementation: ray-march with caching on a coarse grid. */
abstract class CachedCoverSdf(private val cacheCell: Double = 2.0) : Sdf {
    private val cache = HashMap<Long, Double>()

    override fun cover(p: Vec3): Double {
        val ix = floor(p.x / cacheCell).toInt(); val iy = floor(p.y / cacheCell).toInt(); val iz = floor(p.z / cacheCell).toInt()
        val k = ((ix.toLong() and 0xFFFF) shl 32) or ((iy.toLong() and 0xFFFF) shl 16) or (iz.toLong() and 0xFFFF)
        synchronized(cache) { cache[k]?.let { return it } }
        val c = Vec3((ix + 0.5) * cacheCell, (iy + 0.5) * cacheCell, (iz + 0.5) * cacheCell)
        val v = if (dist(c) < 0) 1.0 else Sdf.COVER_DIRS.count { raycast(c, it, 20.0) != null } / 26.0
        synchronized(cache) { cache[k] = v }
        return v
    }
}

/** Ellipsoid primitive for [AnalyticSdf]. */
data class Ellipsoid(val center: Vec3, val radii: Vec3) {
    fun dist(p: Vec3): Double {
        // Approximate ellipsoid SDF (Inigo Quilez "bound" variant: exact on the axes, good enough here).
        val qx = (p.x - center.x) / radii.x
        val qy = (p.y - center.y) / radii.y
        val qz = (p.z - center.z) / radii.z
        val k0 = sqrt(qx * qx + qy * qy + qz * qz)
        val k1 = sqrt(qx * qx / (radii.x * radii.x) + qy * qy / (radii.y * radii.y) + qz * qz / (radii.z * radii.z))
        return if (k1 < 1e-9) -min(radii.x, min(radii.y, radii.z)) else k0 * (k0 - 1.0) / k1
    }
}

/** A capsule-chain tunnel (carved out of the rock): polyline with a radius. */
data class Tunnel(val points: List<Vec3>, val radius: Double) {
    fun dist(p: Vec3): Double {
        var best = Double.MAX_VALUE
        for (i in 0 until points.size - 1) {
            val a = points[i]; val b = points[i + 1]
            val ab = b - a
            val t = clamp01(((p - a) dot ab) / (ab.lengthSq() + 1e-12))
            best = min(best, p.distanceTo(a + ab * t))
        }
        return best - radius
    }
}

/**
 * Analytic rock used until Track B's `rock_sdf.bin` exists (and by tests):
 * smooth union of ellipsoids, minus carved tunnels/holes.
 */
class AnalyticSdf(
    val blobs: List<Ellipsoid>,
    val carves: List<Tunnel> = emptyList(),
    private val smoothK: Double = 1.5,
) : CachedCoverSdf() {
    override fun dist(p: Vec3): Double {
        if (blobs.isEmpty()) return 1000.0
        // Smooth min via log-sum-exp; exact min where blobs are far apart.
        val ds = DoubleArray(blobs.size)
        var d = Double.MAX_VALUE
        for (i in blobs.indices) {
            val bd = blobs[i].dist(p)
            ds[i] = bd
            d = min(d, bd)
        }
        var acc = 0.0
        for (bd in ds) {
            val e = (bd - d) / smoothK
            if (e < 8) acc += exp(-e)
        }
        d -= smoothK * ln(acc)
        for (c in carves) d = max(d, -c.dist(p))
        return d
    }
}

/** No rock at all (flow/unit tests). */
object EmptySdf : Sdf {
    override fun dist(p: Vec3) = 1000.0
    override fun distAt(x: Double, y: Double, z: Double) = 1000.0
    override fun gradient(p: Vec3, h: Double) = Vec3.Y
    override fun cover(p: Vec3) = 0.0
}

/**
 * Baked grid (SIMULATION §5.2): magic "RSDF", uint32 nx ny nz, float32 origin xyz, float32 cell,
 * nx·ny·nz float16 distances (cm, x fastest), then nx·ny·nz uint8 cover scores.
 */
class SdfGrid(
    val nx: Int, val ny: Int, val nz: Int,
    val origin: Vec3, val cell: Double,
    private val d: FloatArray, private val coverBytes: ByteArray?,
) : CachedCoverSdf() {

    private fun at(ix: Int, iy: Int, iz: Int): Float {
        val x = ix.coerceIn(0, nx - 1); val y = iy.coerceIn(0, ny - 1); val z = iz.coerceIn(0, nz - 1)
        return d[(z * ny + y) * nx + x]
    }

    override fun dist(p: Vec3): Double = distAt(p.x, p.y, p.z)

    override fun distAt(x: Double, y: Double, z: Double): Double {
        val fx = (x - origin.x) / cell; val fy = (y - origin.y) / cell; val fz = (z - origin.z) / cell
        val x0 = floor(fx).toInt(); val y0 = floor(fy).toInt(); val z0 = floor(fz).toInt()
        val tx = fx - x0; val ty = fy - y0; val tz = fz - z0
        if (x0 >= 0 && y0 >= 0 && z0 >= 0 && x0 < nx - 1 && y0 < ny - 1 && z0 < nz - 1) {
            // inside the grid (the body-collision hot path): the same trilinear blend without the clamps
            val i = (z0 * ny + y0) * nx + x0
            val sy = nx; val sz = nx * ny
            val c00 = lerp(d[i].toDouble(), d[i + 1].toDouble(), tx)
            val c10 = lerp(d[i + sy].toDouble(), d[i + sy + 1].toDouble(), tx)
            val c01 = lerp(d[i + sz].toDouble(), d[i + sz + 1].toDouble(), tx)
            val c11 = lerp(d[i + sy + sz].toDouble(), d[i + sy + sz + 1].toDouble(), tx)
            return lerp(lerp(c00, c10, ty), lerp(c01, c11, ty), tz)
        }
        val c00 = lerp(at(x0, y0, z0).toDouble(), at(x0 + 1, y0, z0).toDouble(), tx)
        val c10 = lerp(at(x0, y0 + 1, z0).toDouble(), at(x0 + 1, y0 + 1, z0).toDouble(), tx)
        val c01 = lerp(at(x0, y0, z0 + 1).toDouble(), at(x0 + 1, y0, z0 + 1).toDouble(), tx)
        val c11 = lerp(at(x0, y0 + 1, z0 + 1).toDouble(), at(x0 + 1, y0 + 1, z0 + 1).toDouble(), tx)
        var v = lerp(lerp(c00, c10, ty), lerp(c01, c11, ty), tz)
        // Outside the grid: add the distance to the grid box so queries stay conservative.
        val ex = max(0.0, max(origin.x - x, x - (origin.x + (nx - 1) * cell)))
        val ey = max(0.0, max(origin.y - y, y - (origin.y + (ny - 1) * cell)))
        val ez = max(0.0, max(origin.z - z, z - (origin.z + (nz - 1) * cell)))
        if (ex + ey + ez > 0) v += sqrt(ex * ex + ey * ey + ez * ez)
        return v
    }

    /**
     * Outward unit normal from the derivative of the trilinear blend in the cell holding [p] (8 samples instead of
     * the 48 of six [dist] calls); the body collision uses it every tick. Outside the grid it is [gradient].
     */
    fun gradientFast(p: Vec3): Vec3 {
        val fx = (p.x - origin.x) / cell; val fy = (p.y - origin.y) / cell; val fz = (p.z - origin.z) / cell
        val x0 = floor(fx).toInt(); val y0 = floor(fy).toInt(); val z0 = floor(fz).toInt()
        if (!(x0 >= 0 && y0 >= 0 && z0 >= 0 && x0 < nx - 1 && y0 < ny - 1 && z0 < nz - 1)) return gradient(p)
        val tx = fx - x0; val ty = fy - y0; val tz = fz - z0
        val i = (z0 * ny + y0) * nx + x0
        val sy = nx; val sz = nx * ny
        val v000 = d[i].toDouble(); val v100 = d[i + 1].toDouble()
        val v010 = d[i + sy].toDouble(); val v110 = d[i + sy + 1].toDouble()
        val v001 = d[i + sz].toDouble(); val v101 = d[i + sz + 1].toDouble()
        val v011 = d[i + sy + sz].toDouble(); val v111 = d[i + sy + sz + 1].toDouble()
        val gx = lerp(lerp(v100 - v000, v110 - v010, ty), lerp(v101 - v001, v111 - v011, ty), tz)
        val gy = lerp(lerp(v010 - v000, v110 - v100, tx), lerp(v011 - v001, v111 - v101, tx), tz)
        val gz = lerp(lerp(v001 - v000, v101 - v100, tx), lerp(v011 - v010, v111 - v110, tx), ty)
        val g = Vec3(gx, gy, gz)
        return if (g.lengthSq() < 1e-12) gradient(p) else g.normalized(Vec3.Y)
    }

    /**
     * Steepest the trilinear blend gets (cm per cm) in each cell, then the most of that within ±ρ cells ([slopeIn]'s
     * tables, ρ = 0 … [SLOPE_REACH], built on first use). In a cell, the gradient's x part is a blend of the cell's four
     * x-edge differences (likewise y and z), so |∇| ≤ √(Gx² + Gy² + Gz²) / cell, with G the largest edge difference
     * along each axis. A non-finite node leaves NaN or ∞ around it (no bound there).
     */
    private val slopes = arrayOfNulls<FloatArray>(SLOPE_REACH + 1)
    private val cx = nx - 1; private val cy = ny - 1; private val cz = nz - 1

    private fun slopeTable(rho: Int): FloatArray {
        slopes[rho]?.let { return it }
        val t = if (rho == 0) {
            val s = FloatArray(cx * cy * cz)
            val sy = nx; val sz = nx * ny
            fun e(a: Int, b: Int) = kotlin.math.abs(d[b].toDouble() - d[a].toDouble())
            for (z in 0 until cz) for (y in 0 until cy) for (x in 0 until cx) {
                val i = (z * ny + y) * nx + x
                val gx = max(max(e(i, i + 1), e(i + sy, i + sy + 1)), max(e(i + sz, i + sz + 1), e(i + sy + sz, i + sy + sz + 1)))
                val gy = max(max(e(i, i + sy), e(i + 1, i + 1 + sy)), max(e(i + sz, i + sz + sy), e(i + 1 + sz, i + 1 + sz + sy)))
                val gz = max(max(e(i, i + sz), e(i + 1, i + 1 + sz)), max(e(i + sy, i + sy + sz), e(i + 1 + sy, i + 1 + sy + sz)))
                s[(z * cy + y) * cx + x] = (sqrt(gx * gx + gy * gy + gz * gz) / cell * (1 + 1e-6)).toFloat()
            }
            s
        } else {
            // the ρ − 1 table widened by one cell along each axis in turn
            var a = slopeTable(rho - 1)
            for (axis in 0..2) {
                val b = FloatArray(a.size)
                val n = if (axis == 0) cx else if (axis == 1) cy else cz
                val step = if (axis == 0) 1 else if (axis == 1) cx else cx * cy
                for (z in 0 until cz) for (y in 0 until cy) for (x in 0 until cx) {
                    val i = (z * cy + y) * cx + x
                    val k = if (axis == 0) x else if (axis == 1) y else z
                    var m = a[i]
                    if (k > 0) m = slopeMax(m, a[i - step])
                    if (k < n - 1) m = slopeMax(m, a[i + step])
                    b[i] = m
                }
                a = b
            }
            a
        }
        slopes[rho] = t
        return t
    }

    private fun slopeMax(a: Float, b: Float): Float = if (a.isNaN() || b.isNaN()) Float.NaN else max(a, b)

    /**
     * An upper bound on how fast [distAt] changes (cm per cm) in the box ([x0], [y0], [z0])–([x1], [y1], [z1]):
     * `|dist(p) − dist(q)| ≤ bound · |p − q|` for p and q in it (the body collision's exact skips). Outside the grid the
     * distance runs flat from the outer cells plus the distance to the grid box (slope 1). NaN where the bake holds a
     * non-finite value; +∞ for a box wider than the tables reach.
     */
    fun slopeIn(x0: Double, y0: Double, z0: Double, x1: Double, y1: Double, z1: Double): Double {
        val fx0 = floor((x0 - origin.x) / cell); val fy0 = floor((y0 - origin.y) / cell); val fz0 = floor((z0 - origin.z) / cell)
        val fx1 = floor((x1 - origin.x) / cell); val fy1 = floor((y1 - origin.y) / cell); val fz1 = floor((z1 - origin.z) / cell)
        if (!(fx0 <= fx1 && fy0 <= fy1 && fz0 <= fz1)) return Double.NaN
        val out = fx0 < 0 || fy0 < 0 || fz0 < 0 || fx1 > cx - 1 || fy1 > cy - 1 || fz1 > cz - 1
        // the cells the box touches (clamped: outside, the blend reads the outer cells)
        val i0 = fx0.coerceIn(0.0, cx - 1.0).toInt(); val i1 = fx1.coerceIn(0.0, cx - 1.0).toInt()
        val j0 = fy0.coerceIn(0.0, cy - 1.0).toInt(); val j1 = fy1.coerceIn(0.0, cy - 1.0).toInt()
        val k0 = fz0.coerceIn(0.0, cz - 1.0).toInt(); val k1 = fz1.coerceIn(0.0, cz - 1.0).toInt()
        val w = max(i1 - i0, max(j1 - j0, k1 - k0))
        val rho = (w + 1) / 2
        val v = if (rho <= SLOPE_REACH) {
            // one entry of the ±ρ table at the middle cell covers every cell of the box
            slopeTable(rho)[(((k0 + k1) / 2) * cy + (j0 + j1) / 2) * cx + (i0 + i1) / 2].toDouble()
        } else {
            // a wide box (a whole body): the blocks of the ±SLOPE_REACH table it touches, one entry per block
            val t = slopeTable(SLOPE_REACH)
            val st = 2 * SLOPE_REACH + 1
            var m = 0f
            var k = k0 + SLOPE_REACH
            while (true) {
                val kk = min(k, k1)
                var j = j0 + SLOPE_REACH
                while (true) {
                    val jj = min(j, j1)
                    var i = i0 + SLOPE_REACH
                    while (true) {
                        val e = t[(kk * cy + jj) * cx + min(i, i1)]
                        if (e.isNaN()) return Double.NaN
                        if (e > m) m = e
                        if (i >= i1) break
                        i += st
                    }
                    if (j >= j1) break
                    j += st
                }
                if (k >= k1) break
                k += st
            }
            m.toDouble()
        }
        return if (out) v + 1.0 else v
    }

    override fun cover(p: Vec3): Double {
        val cb = coverBytes ?: return super.cover(p)
        val ix = ((p.x - origin.x) / cell).toInt().coerceIn(0, nx - 1)
        val iy = ((p.y - origin.y) / cell).toInt().coerceIn(0, ny - 1)
        val iz = ((p.z - origin.z) / cell).toInt().coerceIn(0, nz - 1)
        return (cb[(iz * ny + iy) * nx + ix].toInt() and 0xFF) / 255.0
    }

    companion object {
        /** Widest reach (cells either side) of the [slopeIn] tables. */
        const val SLOPE_REACH = 3

        fun parse(bytes: ByteArray): SdfGrid {
            val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = String(bytes, 0, 4, Charsets.US_ASCII)
            require(magic == "RSDF") { "Not an RSDF file (magic=$magic)" }
            b.position(4)
            val nx = b.int; val ny = b.int; val nz = b.int
            val origin = Vec3(b.float.toDouble(), b.float.toDouble(), b.float.toDouble())
            val cell = b.float.toDouble()
            val n = nx * ny * nz
            require(n > 0 && bytes.size >= 32 + n * 2) { "RSDF truncated" }
            val d = FloatArray(n) { Half.toFloat(b.short) }
            val cover = if (b.remaining() >= n) ByteArray(n).also { b.get(it) } else null
            repairFaces(nx, ny, nz, cell.toFloat(), d)
            repairSigns(nx, ny, nz, cell.toFloat(), d)
            return SdfGrid(nx, ny, nz, origin, cell, d, cover)
        }

        /**
         * Sign errors of the bake: isolated cells and small pockets reading "inside rock" (−1 … −10 cm) in open water
         * next to cells reading several cm outside. Bodies were pushed out of rock that is not there. A true distance
         * field changes by at most one cell per cell, so a negative cell that breaks that against a positive neighbour
         * is flipped; repeated until the pockets are gone (their interiors flip as their edges do).
         */
        internal fun repairSigns(nx: Int, ny: Int, nz: Int, cell: Float, d: FloatArray, passes: Int = 24): Int {
            val lim = 1.5f * cell
            var total = 0
            val sx = 1; val sy = nx; val sz = nx * ny
            repeat(passes) {
                var flips = 0
                for (z in 0 until nz) for (y in 0 until ny) for (x in 0 until nx) {
                    val i = z * sz + y * sy + x * sx
                    val v = d[i]
                    if (v >= 0f) continue
                    fun bad(j: Int) = d[j] > 0f && d[j] - v > lim && kotlin.math.abs(d[j] + v) <= lim
                    if (x > 0 && bad(i - sx) || x < nx - 1 && bad(i + sx) || y > 0 && bad(i - sy) || y < ny - 1 && bad(i + sy) ||
                        z > 0 && bad(i - sz) || z < nz - 1 && bad(i + sz)) { d[i] = -v; flips++ }
                }
                total += flips
                if (flips == 0) return total
            }
            return total
        }

        /**
         * The bake's outermost cell layers can carry garbage (e.g. −14 cm "inside rock" in open water against the front
         * glass while the layer behind reads +14 cm). A distance field changes by at most one cell per cell; a face
         * value that breaks that against its inner neighbour is replaced by the neighbour's value.
         */
        internal fun repairFaces(nx: Int, ny: Int, nz: Int, cell: Float, d: FloatArray): Int {
            fun idx(x: Int, y: Int, z: Int) = (z * ny + y) * nx + x
            val lim = 1.5f * cell
            var fixed = 0
            fun fix(a: Int, inner: Int) { if (kotlin.math.abs(d[a] - d[inner]) > lim) { d[a] = d[inner]; fixed++ } }
            if (nx >= 2) for (z in 0 until nz) for (y in 0 until ny) { fix(idx(0, y, z), idx(1, y, z)); fix(idx(nx - 1, y, z), idx(nx - 2, y, z)) }
            if (ny >= 2) for (z in 0 until nz) for (x in 0 until nx) { fix(idx(x, 0, z), idx(x, 1, z)); fix(idx(x, ny - 1, z), idx(x, ny - 2, z)) }
            if (nz >= 2) for (y in 0 until ny) for (x in 0 until nx) { fix(idx(x, y, 0), idx(x, y, 1)); fix(idx(x, y, nz - 1), idx(x, y, nz - 2)) }
            return fixed
        }
    }
}

/** IEEE 754 binary16 → float. */
object Half {
    fun toFloat(h: Short): Float {
        val bits = h.toInt() and 0xFFFF
        val sign = (bits ushr 15) and 1
        val exp = (bits ushr 10) and 0x1F
        val mant = bits and 0x3FF
        val v: Float = when (exp) {
            0 -> (mant / 1024f) * 6.1035156e-5f
            31 -> if (mant == 0) Float.POSITIVE_INFINITY else Float.NaN
            else -> (1f + mant / 1024f) * Math.pow(2.0, (exp - 15).toDouble()).toFloat()
        }
        return if (sign == 1) -v else v
    }

    fun fromFloat(f: Float): Short {
        val bits = java.lang.Float.floatToIntBits(f)
        val sign = (bits ushr 16) and 0x8000
        var value = (bits and 0x7FFFFFFF) + 0x1000
        if (value >= 0x47800000) {
            return if ((bits and 0x7FFFFFFF) >= 0x47800000 && (bits and 0x7FFFFFFF) > 0x7F800000) (sign or 0x7E00).toShort()
            else (sign or 0x7C00).toShort()
        }
        if (value >= 0x38800000) return (sign or ((value - 0x38000000) ushr 13)).toShort()
        if (value < 0x33000000) return sign.toShort()
        value = (bits and 0x7FFFFFFF) ushr 23
        return (sign or ((((bits and 0x7FFFFF) or 0x800000) + (0x800000 ushr (value - 102))) ushr (126 - value))).toShort()
    }
}
