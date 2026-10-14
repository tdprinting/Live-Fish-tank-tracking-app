package com.reeflive.core.geom

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * A y-up height map over the tank floor (cm), sampled bilinearly between cell centres. Used for the modelled sand
 * surface: the static sand mesh the renderer draws (tank.glb `part = sand`: the sand slope, the mounds against the rock
 * feet, the lanes and the goby spoil fan, plus the rubble lying on it). The sim adds its dynamic [com.reeflive.core.sim.SandBed]
 * offsets on top, exactly as `static.vert` lifts the sand mesh, so the collision floor is the floor that is drawn.
 */
class HeightField(val lengthCm: Double, val widthCm: Double, val nx: Int, val nz: Int, private val h: FloatArray) {
    init { require(nx >= 2 && nz >= 2 && h.size >= nx * nz) }
    val cellX = lengthCm / nx
    val cellZ = widthCm / nz

    /** Height (cm) at (x, z), bilinear between cell centres, clamped at the edges. */
    fun at(x: Double, z: Double): Double {
        val fx = (x / cellX - 0.5).coerceIn(0.0, nx - 1.0001)
        val fz = (z / cellZ - 0.5).coerceIn(0.0, nz - 1.0001)
        val i = fx.toInt(); val k = fz.toInt()
        val tx = fx - i; val tz = fz - k
        val a = h[k * nx + i]; val b = h[k * nx + i + 1]
        val c = h[(k + 1) * nx + i]; val d = h[(k + 1) * nx + i + 1]
        return (a + (b - a) * tx) * (1 - tz) + (c + (d - c) * tx) * tz
    }

    fun max(): Double { var m = Double.NEGATIVE_INFINITY; for (i in 0 until nx * nz) m = max(m, h[i].toDouble()); return m }

    companion object {
        /**
         * Rasterizes triangles ([tris]: 9 floats per triangle, world cm) over the box [0, lengthCm] × [0, widthCm]:
         * every cell takes the highest triangle surface over its centre. Cells no triangle covers take their nearest
         * covered neighbour's height. Null when nothing covers the box.
         */
        fun fromTriangles(tris: FloatArray, lengthCm: Double, widthCm: Double, cell: Double = 0.5): HeightField? {
            val nx = max(2, kotlin.math.ceil(lengthCm / cell).toInt())
            val nz = max(2, kotlin.math.ceil(widthCm / cell).toInt())
            val cx = lengthCm / nx; val cz = widthCm / nz
            val h = FloatArray(nx * nz) { Float.NaN }
            var t = 0
            while (t + 8 < tris.size) {
                val ax = tris[t].toDouble(); val ay = tris[t + 1].toDouble(); val az = tris[t + 2].toDouble()
                val bx = tris[t + 3].toDouble(); val by = tris[t + 4].toDouble(); val bz = tris[t + 5].toDouble()
                val qx = tris[t + 6].toDouble(); val qy = tris[t + 7].toDouble(); val qz = tris[t + 8].toDouble()
                t += 9
                val det = (bz - qz) * (ax - qx) + (qx - bx) * (az - qz)
                if (kotlin.math.abs(det) < 1e-9) continue   // vertical (or degenerate) in plan view
                val i0 = max(0, floor(min(ax, min(bx, qx)) / cx - 0.5).toInt()); val i1 = min(nx - 1, kotlin.math.ceil(max(ax, max(bx, qx)) / cx - 0.5).toInt())
                val k0 = max(0, floor(min(az, min(bz, qz)) / cz - 0.5).toInt()); val k1 = min(nz - 1, kotlin.math.ceil(max(az, max(bz, qz)) / cz - 0.5).toInt())
                for (k in k0..k1) for (i in i0..i1) {
                    val px = (i + 0.5) * cx; val pz = (k + 0.5) * cz
                    val l1 = ((bz - qz) * (px - qx) + (qx - bx) * (pz - qz)) / det
                    val l2 = ((qz - az) * (px - qx) + (ax - qx) * (pz - qz)) / det
                    val l3 = 1 - l1 - l2
                    val e = -1e-6
                    if (l1 < e || l2 < e || l3 < e) continue
                    val y = (l1 * ay + l2 * by + l3 * qy).toFloat()
                    val j = k * nx + i
                    if (h[j].isNaN() || y > h[j]) h[j] = y
                }
            }
            if (h.all { it.isNaN() }) return null
            // fill uncovered cells from covered neighbours (a few dilation passes, then the minimum as a last resort)
            var holes = h.count { it.isNaN() }
            var pass = 0
            while (holes > 0 && pass++ < 64) {
                val src = h.copyOf()
                for (k in 0 until nz) for (i in 0 until nx) {
                    val j = k * nx + i
                    if (!src[j].isNaN()) continue
                    var s = 0f; var n = 0
                    if (i > 0 && !src[j - 1].isNaN()) { s += src[j - 1]; n++ }
                    if (i < nx - 1 && !src[j + 1].isNaN()) { s += src[j + 1]; n++ }
                    if (k > 0 && !src[j - nx].isNaN()) { s += src[j - nx]; n++ }
                    if (k < nz - 1 && !src[j + nx].isNaN()) { s += src[j + nx]; n++ }
                    if (n > 0) { h[j] = s / n; holes-- }
                }
            }
            if (holes > 0) { val lo = h.filter { !it.isNaN() }.minOrNull() ?: 0f; for (j in h.indices) if (h[j].isNaN()) h[j] = lo }
            return HeightField(lengthCm, widthCm, nx, nz, h)
        }

        /**
         * The sand surface of a tank model (binary glTF): every mesh whose node, mesh or material extras say
         * `part = [part]`, in world space (node hierarchy applied), rasterized by [fromTriangles].
         */
        fun fromGlb(bytes: ByteArray, lengthCm: Double, widthCm: Double, part: String = "sand", cell: Double = 0.5): HeightField? =
            fromTriangles(GlbTriangles.read(bytes, part), lengthCm, widthCm, cell)
    }
}

/** Minimal binary-glTF reader for the triangles of the meshes tagged with an `extras.part` (pure JVM, no Android). */
object GlbTriangles {
    fun read(bytes: ByteArray, part: String): FloatArray {
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(bytes.size >= 20 && bb.getInt(0) == 0x46546C67) { "not a GLB" }
        val jsonLen = bb.getInt(12)
        val json = Json.parseToJsonElement(String(bytes, 20, jsonLen, Charsets.UTF_8)) as JsonObject
        val binStart = 20 + jsonLen + 8
        val accessors = json.arr("accessors"); val views = json.arr("bufferViews")
        val meshes = json.arr("meshes"); val nodes = json.arr("nodes"); val materials = json.arr("materials")
        val out = ArrayList<Float>()

        fun accessor(i: Int): Pair<DoubleArray, Int> {
            val a = accessors[i] as JsonObject
            val v = views[a.int("bufferView") ?: 0] as JsonObject
            val comps = when ((a["type"] as JsonPrimitive).content) { "SCALAR" -> 1; "VEC2" -> 2; "VEC3" -> 3; "VEC4" -> 4; else -> 1 }
            val ct = a.int("componentType") ?: 5126
            val size = when (ct) { 5120, 5121 -> 1; 5122, 5123 -> 2; else -> 4 }
            val count = a.int("count") ?: 0
            val stride = v.int("byteStride") ?: (size * comps)
            val off = binStart + (v.int("byteOffset") ?: 0) + (a.int("byteOffset") ?: 0)
            val r = DoubleArray(count * comps)
            for (k in 0 until count) for (c in 0 until comps) {
                val p = off + k * stride + c * size
                r[k * comps + c] = when (ct) {
                    5126 -> bb.getFloat(p).toDouble()
                    5125 -> (bb.getInt(p).toLong() and 0xFFFFFFFFL).toDouble()
                    5123 -> (bb.getShort(p).toInt() and 0xFFFF).toDouble()
                    5121 -> (bb.get(p).toInt() and 0xFF).toDouble()
                    5122 -> bb.getShort(p).toDouble()
                    else -> bb.get(p).toDouble()
                }
            }
            return r to comps
        }

        fun partOf(o: JsonObject?): String? = ((o?.get("extras") as? JsonObject)?.get("part") as? JsonPrimitive)?.content

        fun walk(ni: Int, parent: DoubleArray, depth: Int) {
            if (depth > 32) return
            val n = nodes[ni] as JsonObject
            val world = Mat.mul(parent, Mat.local(n))
            n.int("mesh")?.let { mi ->
                val m = meshes[mi] as JsonObject
                for (pe in m.arr("primitives")) {
                    val p = pe as JsonObject
                    val mat = p.int("material")?.let { materials.getOrNull(it) as? JsonObject }
                    val tag = partOf(n) ?: partOf(m) ?: partOf(mat)
                    if (tag != part) continue
                    if ((p.int("mode") ?: 4) != 4) continue
                    val posIdx = (p["attributes"] as JsonObject).int("POSITION") ?: continue
                    val (pos, _) = accessor(posIdx)
                    val idx = p.int("indices")?.let { accessor(it).first } ?: DoubleArray(pos.size / 3) { it.toDouble() }
                    for (q in idx) {
                        val k = q.toInt() * 3
                        val x = pos[k]; val y = pos[k + 1]; val z = pos[k + 2]
                        out += (world[0] * x + world[4] * y + world[8] * z + world[12]).toFloat()
                        out += (world[1] * x + world[5] * y + world[9] * z + world[13]).toFloat()
                        out += (world[2] * x + world[6] * y + world[10] * z + world[14]).toFloat()
                    }
                }
            }
            (n["children"] as? JsonArray)?.forEach { c -> walk((c as JsonPrimitive).intOrNull ?: return@forEach, world, depth + 1) }
        }

        val scene = (json.arr("scenes").getOrNull(json.int("scene") ?: 0) as? JsonObject)
        val roots = scene?.arr("nodes")?.mapNotNull { (it as? JsonPrimitive)?.intOrNull } ?: nodes.indices.toList()
        for (r in roots) walk(r, Mat.IDENTITY, 0)
        return out.toFloatArray()
    }

    private fun JsonObject.arr(k: String): JsonArray = this[k] as? JsonArray ?: JsonArray(emptyList())
    private fun JsonObject.int(k: String): Int? = (this[k] as? JsonPrimitive)?.intOrNull

    /** Column-major 4×4 helpers for node transforms. */
    private object Mat {
        val IDENTITY = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0)

        fun mul(a: DoubleArray, b: DoubleArray): DoubleArray {
            val r = DoubleArray(16)
            for (c in 0 until 4) for (rr in 0 until 4) {
                var s = 0.0
                for (k in 0 until 4) s += a[k * 4 + rr] * b[c * 4 + k]
                r[c * 4 + rr] = s
            }
            return r
        }

        fun local(n: JsonObject): DoubleArray {
            (n["matrix"] as? JsonArray)?.let { m -> if (m.size == 16) return DoubleArray(16) { (m[it] as JsonPrimitive).doubleOrNull ?: 0.0 } }
            fun v(k: String, d: DoubleArray): DoubleArray = (n[k] as? JsonArray)?.let { a -> DoubleArray(d.size) { (a.getOrNull(it) as? JsonPrimitive)?.doubleOrNull ?: d[it] } } ?: d
            val t = v("translation", doubleArrayOf(0.0, 0.0, 0.0))
            val q = v("rotation", doubleArrayOf(0.0, 0.0, 0.0, 1.0))
            val s = v("scale", doubleArrayOf(1.0, 1.0, 1.0))
            val (x, y, z, w) = listOf(q[0], q[1], q[2], q[3])
            return doubleArrayOf(
                (1 - 2 * (y * y + z * z)) * s[0], (2 * (x * y + z * w)) * s[0], (2 * (x * z - y * w)) * s[0], 0.0,
                (2 * (x * y - z * w)) * s[1], (1 - 2 * (x * x + z * z)) * s[1], (2 * (y * z + x * w)) * s[1], 0.0,
                (2 * (x * z + y * w)) * s[2], (2 * (y * z - x * w)) * s[2], (1 - 2 * (x * x + y * y)) * s[2], 0.0,
                t[0], t[1], t[2], 1.0,
            )
        }
    }
}
