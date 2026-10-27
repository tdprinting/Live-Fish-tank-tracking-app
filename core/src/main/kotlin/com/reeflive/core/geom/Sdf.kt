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
        val dx = distAt(p.x + h, p.y, p.z) - distAt(p.x - h, p.y, p.z)
        val dy = distAt(p.x, p.y + h, p.z) - distAt(p.x, p.y - h, p.z)
        val dz = distAt(p.x, p.y, p.z + h) - distAt(p.x, p.y, p.z - h)
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
            val s = distAt(origin.x + d.x * t, origin.y + d.y * t, origin.z + d.z * t)
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

    private val ox = origin.x
    private val oy = origin.y
    private val oz = origin.z

    override fun distAt(x: Double, y: Double, z: Double): Double {
        val fx = (x - ox) / cell; val fy = (y - oy) / cell; val fz = (z - oz) / cell
        val x0 = floor(fx).toInt(); val y0 = floor(fy).toInt(); val z0 = floor(fz).toInt()
        val tx = fx - x0; val ty = fy - y0; val tz = fz - z0
        if (x0 >= 0 && y0 >= 0 && z0 >= 0 && x0 < nx - 1 && y0 < ny - 1 && z0 < nz - 1) {
            // all eight corners inside the grid (the common case): no clamping, the same arithmetic as below
            val sy = nx; val sz = nx * ny
            val a = (z0 * ny + y0) * nx + x0
            val c00 = lerp(d[a].toDouble(), d[a + 1].toDouble(), tx)
            val c10 = lerp(d[a + sy].toDouble(), d[a + sy + 1].toDouble(), tx)
            val c01 = lerp(d[a + sz].toDouble(), d[a + sz + 1].toDouble(), tx)
            val c11 = lerp(d[a + sz + sy].toDouble(), d[a + sz + sy + 1].toDouble(), tx)
            var v = lerp(lerp(c00, c10, ty), lerp(c01, c11, ty), tz)
            val ex = max(0.0, max(ox - x, x - (ox + (nx - 1) * cell)))
            val ey = max(0.0, max(oy - y, y - (oy + (ny - 1) * cell)))
            val ez = max(0.0, max(oz - z, z - (oz + (nz - 1) * cell)))
            if (ex + ey + ez > 0) v += sqrt(ex * ex + ey * ey + ez * ez)
            return v
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

    override fun cover(p: Vec3): Double {
        val cb = coverBytes ?: return super.cover(p)
        val ix = ((p.x - origin.x) / cell).toInt().coerceIn(0, nx - 1)
        val iy = ((p.y - origin.y) / cell).toInt().coerceIn(0, ny - 1)
        val iz = ((p.z - origin.z) / cell).toInt().coerceIn(0, nz - 1)
        return (cb[(iz * ny + iy) * nx + ix].toInt() and 0xFF) / 255.0
    }

    companion object {
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
            return SdfGrid(nx, ny, nz, origin, cell, d, cover)
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
