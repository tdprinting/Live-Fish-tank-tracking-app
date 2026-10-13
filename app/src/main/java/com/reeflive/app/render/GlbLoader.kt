package com.reeflive.app.render

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parsed glTF binary, CPU side only (no GL, no Android: unit-testable on the JVM). Only the subset in
 * ARCHITECTURE §8.2 / ASSETS is read: meshes with POSITION/NORMAL/TEXCOORD_0/COLOR_0, uint8/16/32 indices,
 * metallic-roughness materials (+ emissive, optional KHR_materials_emissive_strength), the node TRS
 * hierarchy, embedded PNG/WebP images and node/mesh `extras`. Everything else (unknown keys, extensions,
 * skins, animations, cameras) is ignored.
 */
class GlbModel(
    val name: String,
    val nodes: List<GlbNode>,
    val materials: List<GlbMaterial>,
    /** Encoded image bytes (PNG/WebP), indexed by glTF texture → image. */
    val images: List<ByteArray>,
    /** Extras of the scene root node (e.g. fish `lengthCm`, coral `anchor`). */
    val rootExtras: JsonObject,
) {
    val triangles: Int get() = nodes.sumOf { n -> n.primitives.sumOf { it.indices.size / 3 } }

    fun rootString(key: String): String? = (rootExtras[key] as? JsonPrimitive)?.contentOrNullSafe()
    fun rootFloat(key: String): Float? = (rootExtras[key] as? JsonPrimitive)?.doubleOrNull?.toFloat()
}

/** A mesh-bearing node, flattened: [world] is the node's accumulated transform (column-major). */
class GlbNode(
    val name: String,
    val part: String,
    val extras: JsonObject,
    val world: FloatArray,
    val primitives: List<GlbPrimitive>,
) {
    val hidden: Boolean get() = (extras["hidden"] as? JsonPrimitive)?.booleanOrNull == true

    /** Polyp instance table (`extras.instances`, stride `instanceStride`, default 10) or null. */
    fun instances(): FloatArray? = (extras["instances"] as? JsonArray)?.let { a -> FloatArray(a.size) { (a[it] as JsonPrimitive).doubleOrNull?.toFloat() ?: 0f } }
    val instanceStride: Int get() = (extras["instanceStride"] as? JsonPrimitive)?.intOrNull ?: 10
    fun float(key: String): Float? = (extras[key] as? JsonPrimitive)?.doubleOrNull?.toFloat()
    /** A flat float array (`extras.limbPivots`, …) or null. */
    fun floats(key: String): FloatArray? = (extras[key] as? JsonArray)?.let { a -> FloatArray(a.size) { (a[it] as? JsonPrimitive)?.doubleOrNull?.toFloat() ?: 0f } }
    fun vec3(key: String): FloatArray? = (extras[key] as? JsonArray)?.takeIf { it.size >= 3 }?.let { a -> FloatArray(3) { (a[it] as JsonPrimitive).doubleOrNull?.toFloat() ?: 0f } }
}

/**
 * Interleaved vertices, [STRIDE] floats each: pos3, normal3, uv2, color4 (COLOR_0 normalized to 0..1;
 * for fish COLOR_0.a is the spine coordinate `aSpine`). [bmin]/[bmax] are bounds in the node's local frame.
 */
class GlbPrimitive(
    val vertices: FloatArray,
    val indices: IntArray,
    val material: Int,
    val bmin: FloatArray,
    val bmax: FloatArray,
) {
    val vertexCount: Int get() = vertices.size / STRIDE

    companion object { const val STRIDE = 12 }
}

class GlbMaterial(
    val name: String,
    /** Linear RGBA. */
    val baseColor: FloatArray,
    /** Image index (into [GlbModel.images]) of the base color texture, or -1. */
    val baseColorImage: Int,
    val metallic: Float,
    val roughness: Float,
    /** Linear RGB × KHR_materials_emissive_strength. */
    val emissive: FloatArray,
    val alphaMode: String,
    val alphaCutoff: Float,
    val doubleSided: Boolean,
    val extras: JsonObject,
) {
    val blended: Boolean get() = alphaMode == "BLEND"

    companion object {
        val DEFAULT = GlbMaterial("default", floatArrayOf(0.8f, 0.8f, 0.8f, 1f), -1, 0f, 0.8f, floatArrayOf(0f, 0f, 0f), "OPAQUE", 0.5f, false, JsonObject(emptyMap()))
    }
}

class GlbFormatException(msg: String) : RuntimeException(msg)

object GlbLoader {
    private const val MAGIC = 0x46546C67 // "glTF"
    private const val CHUNK_JSON = 0x4E4F534A
    private const val CHUNK_BIN = 0x004E4942
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(bytes: ByteArray, name: String = "model"): GlbModel {
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (bytes.size < 20 || bb.getInt(0) != MAGIC) throw GlbFormatException("$name: not a GLB")
        if (bb.getInt(4) != 2) throw GlbFormatException("$name: unsupported glTF version ${bb.getInt(4)}")
        var off = 12
        var jsonText: String? = null
        var binOff = -1; var binLen = 0
        while (off + 8 <= bytes.size) {
            val len = bb.getInt(off); val type = bb.getInt(off + 4)
            when (type) {
                CHUNK_JSON -> jsonText = String(bytes, off + 8, len, Charsets.UTF_8)
                CHUNK_BIN -> { binOff = off + 8; binLen = len }
                else -> Unit // unknown chunk: ignored
            }
            off += 8 + len
        }
        val root = json.parseToJsonElement(jsonText ?: throw GlbFormatException("$name: no JSON chunk")).jsonObj()
        return Parser(root, bb, binOff, binLen, name).run()
    }

    private class Parser(val j: JsonObject, val bb: ByteBuffer, val binOff: Int, val binLen: Int, val name: String) {
        val accessors = j.arr("accessors")
        val views = j.arr("bufferViews")
        val meshes = j.arr("meshes")
        val nodesJ = j.arr("nodes")

        fun run(): GlbModel {
            val images = j.arr("images").map { im -> im.jsonObj().int("bufferView")?.let { viewBytes(it) } ?: ByteArray(0) }
            val texToImage = j.arr("textures").map { it.jsonObj().int("source") ?: -1 }
            val materials = j.arr("materials").map { parseMaterial(it.jsonObj(), texToImage) }
            val sceneIdx = j.int("scene") ?: 0
            val scenes = j.arr("scenes")
            val roots: List<Int> = (scenes.getOrNull(sceneIdx)?.jsonObj()?.arr("nodes")?.mapNotNull { (it as? JsonPrimitive)?.intOrNull })
                ?: nodesJ.indices.filter { i -> nodesJ.none { n -> n.jsonObj().arr("children").any { (it as? JsonPrimitive)?.intOrNull == i } } }
            val out = ArrayList<GlbNode>()
            val rootExtras = if (roots.size == 1) nodesJ[roots[0]].jsonObj().obj("extras") else JsonObject(emptyMap())
            for (r in roots) walk(r, M.identity(), out, 0)
            return GlbModel(name, out, materials, images, rootExtras)
        }

        fun walk(i: Int, parent: FloatArray, out: MutableList<GlbNode>, depth: Int) {
            if (depth > 32 || i !in nodesJ.indices) return
            val n = nodesJ[i].jsonObj()
            val world = M.mul(parent, localMatrix(n))
            val meshIdx = n.int("mesh")
            if (meshIdx != null && meshIdx in meshes.indices) {
                val mesh = meshes[meshIdx].jsonObj()
                // Node extras win over mesh extras (both are written by the exporter).
                val extras = JsonObject(mesh.obj("extras") + n.obj("extras"))
                // a coral's instanced tufts named "Polyps" without a part tag still draw as polyps (hairy mushroom)
                val part = (extras["part"] as? JsonPrimitive)?.contentOrNullSafe()
                    ?: if ((n.str("name") ?: "").equals("Polyps", ignoreCase = true) && extras["instances"] != null) "polyp" else ""
                val prims = mesh.arr("primitives").mapNotNull { parsePrimitive(it.jsonObj()) }
                out += GlbNode(n.str("name") ?: mesh.str("name") ?: "node$i", part, extras, world, prims)
            }
            for (c in n.arr("children")) (c as? JsonPrimitive)?.intOrNull?.let { walk(it, world, out, depth + 1) }
        }

        fun localMatrix(n: JsonObject): FloatArray {
            n.floats("matrix")?.takeIf { it.size == 16 }?.let { return it }
            val t = n.floats("translation") ?: floatArrayOf(0f, 0f, 0f)
            val r = n.floats("rotation") ?: floatArrayOf(0f, 0f, 0f, 1f)
            val s = n.floats("scale") ?: floatArrayOf(1f, 1f, 1f)
            return M.trs(t, r, s)
        }

        fun parsePrimitive(p: JsonObject): GlbPrimitive? {
            val mode = p.int("mode") ?: 4
            if (mode != 4) return null // triangles only
            val attrs = p.obj("attributes")
            val posA = attrs.int("POSITION") ?: return null
            val pos = readAccessor(posA)
            val count = pos.count
            val nrm = attrs.int("NORMAL")?.let { readAccessor(it) }
            val uv = attrs.int("TEXCOORD_0")?.let { readAccessor(it) }
            val col = attrs.int("COLOR_0")?.let { readAccessor(it) }
            val st = GlbPrimitive.STRIDE
            val v = FloatArray(count * st)
            val bmin = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
            val bmax = floatArrayOf(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
            for (k in 0 until count) {
                val o = k * st
                for (c in 0 until 3) {
                    val x = pos.get(k, c); v[o + c] = x
                    if (x < bmin[c]) bmin[c] = x
                    if (x > bmax[c]) bmax[c] = x
                }
                if (nrm != null) { v[o + 3] = nrm.get(k, 0); v[o + 4] = nrm.get(k, 1); v[o + 5] = nrm.get(k, 2) } else v[o + 4] = 1f
                if (uv != null) { v[o + 6] = uv.get(k, 0); v[o + 7] = uv.get(k, 1) }
                if (col != null) {
                    v[o + 8] = col.get(k, 0); v[o + 9] = col.get(k, 1); v[o + 10] = col.get(k, 2)
                    v[o + 11] = if (col.comps >= 4) col.get(k, 3) else 1f
                } else { v[o + 8] = 1f; v[o + 9] = 1f; v[o + 10] = 1f; v[o + 11] = 1f }
            }
            val idx = p.int("indices")?.let { a ->
                val acc = readAccessor(a)
                IntArray(acc.count) { acc.getInt(it) }
            } ?: IntArray(count) { it }
            for (i in idx) if (i < 0 || i >= count) throw GlbFormatException("$name: index $i out of range ($count vertices)")
            return GlbPrimitive(v, idx, p.int("material") ?: -1, bmin, bmax)
        }

        fun parseMaterial(m: JsonObject, texToImage: List<Int>): GlbMaterial {
            val pbr = m.obj("pbrMetallicRoughness")
            val tex = pbr.obj("baseColorTexture").int("index")
            val strength = m.obj("extensions").obj("KHR_materials_emissive_strength").float("emissiveStrength") ?: 1f
            val em = (m.floats("emissiveFactor") ?: floatArrayOf(0f, 0f, 0f)).copyOf(3).also { e -> for (i in 0 until 3) e[i] *= strength }
            return GlbMaterial(
                name = m.str("name") ?: "material",
                baseColor = (pbr.floats("baseColorFactor") ?: floatArrayOf(1f, 1f, 1f, 1f)).copyOf(4).also { if (it.size == 4 && pbr.floats("baseColorFactor")?.size == 3) it[3] = 1f },
                baseColorImage = tex?.let { texToImage.getOrNull(it) } ?: -1,
                metallic = pbr.float("metallicFactor") ?: 1f,
                roughness = pbr.float("roughnessFactor") ?: 1f,
                emissive = em,
                alphaMode = m.str("alphaMode") ?: "OPAQUE",
                alphaCutoff = m.float("alphaCutoff") ?: 0.5f,
                doubleSided = (m["doubleSided"] as? JsonPrimitive)?.booleanOrNull ?: false,
                extras = m.obj("extras"),
            )
        }

        fun viewBytes(viewIdx: Int): ByteArray {
            val v = views.getOrNull(viewIdx)?.jsonObj() ?: throw GlbFormatException("$name: bad bufferView $viewIdx")
            val off = (v.int("byteOffset") ?: 0)
            val len = v.int("byteLength") ?: 0
            if (binOff < 0 || off + len > binLen) throw GlbFormatException("$name: bufferView $viewIdx outside BIN")
            return ByteArray(len).also { System.arraycopy(bb.array(), binOff + off, it, 0, len) }
        }

        inner class Accessor(val base: Int, val stride: Int, val compType: Int, val comps: Int, val count: Int, val normalized: Boolean) {
            private fun compSize() = when (compType) { 5120, 5121 -> 1; 5122, 5123 -> 2; else -> 4 }
            fun raw(i: Int, c: Int): Double {
                val at = base + i * stride + c * compSize()
                return when (compType) {
                    5126 -> bb.getFloat(at).toDouble()
                    5120 -> bb.get(at).toDouble().let { if (normalized) maxOf(it / 127.0, -1.0) else it }
                    5121 -> (bb.get(at).toInt() and 0xFF).toDouble().let { if (normalized) it / 255.0 else it }
                    5122 -> bb.getShort(at).toDouble().let { if (normalized) maxOf(it / 32767.0, -1.0) else it }
                    5123 -> (bb.getShort(at).toInt() and 0xFFFF).toDouble().let { if (normalized) it / 65535.0 else it }
                    5125 -> (bb.getInt(at).toLong() and 0xFFFFFFFFL).toDouble()
                    else -> throw GlbFormatException("$name: componentType $compType")
                }
            }
            fun get(i: Int, c: Int): Float = if (c < comps) raw(i, c).toFloat() else 0f
            fun getInt(i: Int): Int = raw(i, 0).toInt()
        }

        fun readAccessor(i: Int): Accessor {
            val a = accessors.getOrNull(i)?.jsonObj() ?: throw GlbFormatException("$name: bad accessor $i")
            val comps = when (a.str("type")) { "SCALAR" -> 1; "VEC2" -> 2; "VEC3" -> 3; "VEC4" -> 4; "MAT4" -> 16; else -> 1 }
            val ct = a.int("componentType") ?: 5126
            val count = a.int("count") ?: 0
            val compSize = when (ct) { 5120, 5121 -> 1; 5122, 5123 -> 2; else -> 4 }
            val viewIdx = a.int("bufferView") ?: throw GlbFormatException("$name: sparse/empty accessors unsupported")
            val v = views[viewIdx].jsonObj()
            val stride = v.int("byteStride") ?: (comps * compSize)
            val base = binOff + (v.int("byteOffset") ?: 0) + (a.int("byteOffset") ?: 0)
            val end = base + (if (count > 0) (count - 1) * stride + comps * compSize else 0)
            if (binOff < 0 || end > binOff + binLen) throw GlbFormatException("$name: accessor $i outside BIN")
            return Accessor(base, stride, ct, comps, count, (a["normalized"] as? JsonPrimitive)?.booleanOrNull == true)
        }
    }

    // --- tiny JSON helpers (tolerant: wrong types read as absent) ---
    private fun JsonElement.jsonObj(): JsonObject = this as? JsonObject ?: JsonObject(emptyMap())
    private fun JsonObject.arr(k: String): JsonArray = this[k] as? JsonArray ?: JsonArray(emptyList())
    private fun JsonObject.obj(k: String): JsonObject = this[k] as? JsonObject ?: JsonObject(emptyMap())
    private fun JsonObject.int(k: String): Int? = (this[k] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.float(k: String): Float? = (this[k] as? JsonPrimitive)?.doubleOrNull?.toFloat()
    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNullSafe()
    private fun JsonObject.floats(k: String): FloatArray? = (this[k] as? JsonArray)?.let { a -> FloatArray(a.size) { (a[it] as? JsonPrimitive)?.doubleOrNull?.toFloat() ?: 0f } }
}

internal fun JsonPrimitive.contentOrNullSafe(): String? = if (this is kotlinx.serialization.json.JsonNull) null else content

/** Minimal column-major 4×4 float helpers (the core [com.reeflive.core.geom.Mat4] is double-based). */
internal object M {
    fun identity() = FloatArray(16).also { it[0] = 1f; it[5] = 1f; it[10] = 1f; it[15] = 1f }

    fun mul(a: FloatArray, b: FloatArray, out: FloatArray = FloatArray(16)): FloatArray {
        for (c in 0 until 4) for (r in 0 until 4) {
            var s = 0f
            for (k in 0 until 4) s += a[k * 4 + r] * b[c * 4 + k]
            out[c * 4 + r] = s
        }
        return out
    }

    fun trs(t: FloatArray, q: FloatArray, s: FloatArray, out: FloatArray = FloatArray(16)): FloatArray {
        val (x, y, z, w) = q
        val xx = x * x; val yy = y * y; val zz = z * z; val xy = x * y; val xz = x * z; val yz = y * z; val wx = w * x; val wy = w * y; val wz = w * z
        out[0] = (1 - 2 * (yy + zz)) * s[0]; out[1] = 2 * (xy + wz) * s[0]; out[2] = 2 * (xz - wy) * s[0]; out[3] = 0f
        out[4] = 2 * (xy - wz) * s[1]; out[5] = (1 - 2 * (xx + zz)) * s[1]; out[6] = 2 * (yz + wx) * s[1]; out[7] = 0f
        out[8] = 2 * (xz + wy) * s[2]; out[9] = 2 * (yz - wx) * s[2]; out[10] = (1 - 2 * (xx + yy)) * s[2]; out[11] = 0f
        out[12] = t[0]; out[13] = t[1]; out[14] = t[2]; out[15] = 1f
        return out
    }

    fun transformPoint(m: FloatArray, x: Float, y: Float, z: Float, out: FloatArray, o: Int = 0) {
        out[o] = m[0] * x + m[4] * y + m[8] * z + m[12]
        out[o + 1] = m[1] * x + m[5] * y + m[9] * z + m[13]
        out[o + 2] = m[2] * x + m[6] * y + m[10] * z + m[14]
    }

    fun isIdentity(m: FloatArray): Boolean = (0 until 16).all { kotlin.math.abs(m[it] - (if (it % 5 == 0) 1f else 0f)) < 1e-6f }

    /** General 4×4 inverse; returns false when singular. */
    fun invert(m: FloatArray, out: FloatArray): Boolean {
        val inv = FloatArray(16)
        inv[0] = m[5] * m[10] * m[15] - m[5] * m[11] * m[14] - m[9] * m[6] * m[15] + m[9] * m[7] * m[14] + m[13] * m[6] * m[11] - m[13] * m[7] * m[10]
        inv[4] = -m[4] * m[10] * m[15] + m[4] * m[11] * m[14] + m[8] * m[6] * m[15] - m[8] * m[7] * m[14] - m[12] * m[6] * m[11] + m[12] * m[7] * m[10]
        inv[8] = m[4] * m[9] * m[15] - m[4] * m[11] * m[13] - m[8] * m[5] * m[15] + m[8] * m[7] * m[13] + m[12] * m[5] * m[11] - m[12] * m[7] * m[9]
        inv[12] = -m[4] * m[9] * m[14] + m[4] * m[10] * m[13] + m[8] * m[5] * m[14] - m[8] * m[6] * m[13] - m[12] * m[5] * m[10] + m[12] * m[6] * m[9]
        inv[1] = -m[1] * m[10] * m[15] + m[1] * m[11] * m[14] + m[9] * m[2] * m[15] - m[9] * m[3] * m[14] - m[13] * m[2] * m[11] + m[13] * m[3] * m[10]
        inv[5] = m[0] * m[10] * m[15] - m[0] * m[11] * m[14] - m[8] * m[2] * m[15] + m[8] * m[3] * m[14] + m[12] * m[2] * m[11] - m[12] * m[3] * m[10]
        inv[9] = -m[0] * m[9] * m[15] + m[0] * m[11] * m[13] + m[8] * m[1] * m[15] - m[8] * m[3] * m[13] - m[12] * m[1] * m[11] + m[12] * m[3] * m[9]
        inv[13] = m[0] * m[9] * m[14] - m[0] * m[10] * m[13] - m[8] * m[1] * m[14] + m[8] * m[2] * m[13] + m[12] * m[1] * m[10] - m[12] * m[2] * m[9]
        inv[2] = m[1] * m[6] * m[15] - m[1] * m[7] * m[14] - m[5] * m[2] * m[15] + m[5] * m[3] * m[14] + m[13] * m[2] * m[7] - m[13] * m[3] * m[6]
        inv[6] = -m[0] * m[6] * m[15] + m[0] * m[7] * m[14] + m[4] * m[2] * m[15] - m[4] * m[3] * m[14] - m[12] * m[2] * m[7] + m[12] * m[3] * m[6]
        inv[10] = m[0] * m[5] * m[15] - m[0] * m[7] * m[13] - m[4] * m[1] * m[15] + m[4] * m[3] * m[13] + m[12] * m[1] * m[7] - m[12] * m[3] * m[5]
        inv[14] = -m[0] * m[5] * m[14] + m[0] * m[6] * m[13] + m[4] * m[1] * m[14] - m[4] * m[2] * m[13] - m[12] * m[1] * m[6] + m[12] * m[2] * m[5]
        inv[3] = -m[1] * m[6] * m[11] + m[1] * m[7] * m[10] + m[5] * m[2] * m[11] - m[5] * m[3] * m[10] - m[9] * m[2] * m[7] + m[9] * m[3] * m[6]
        inv[7] = m[0] * m[6] * m[11] - m[0] * m[7] * m[10] - m[4] * m[2] * m[11] + m[4] * m[3] * m[10] + m[8] * m[2] * m[7] - m[8] * m[3] * m[6]
        inv[11] = -m[0] * m[5] * m[11] + m[0] * m[7] * m[9] + m[4] * m[1] * m[11] - m[4] * m[3] * m[9] - m[8] * m[1] * m[7] + m[8] * m[3] * m[5]
        inv[15] = m[0] * m[5] * m[10] - m[0] * m[6] * m[9] - m[4] * m[1] * m[10] + m[4] * m[2] * m[9] + m[8] * m[1] * m[6] - m[8] * m[2] * m[5]
        var det = m[0] * inv[0] + m[1] * inv[4] + m[2] * inv[8] + m[3] * inv[12]
        if (kotlin.math.abs(det) < 1e-20f) return false
        det = 1f / det
        for (i in 0 until 16) out[i] = inv[i] * det
        return true
    }

    fun perspective(fovYDeg: Float, aspect: Float, near: Float, far: Float, out: FloatArray) {
        val f = 1f / kotlin.math.tan(Math.toRadians(fovYDeg / 2.0)).toFloat()
        out.fill(0f)
        out[0] = f / aspect; out[5] = f
        out[10] = (far + near) / (near - far); out[11] = -1f
        out[14] = 2 * far * near / (near - far)
    }

    fun lookAt(ex: Float, ey: Float, ez: Float, tx: Float, ty: Float, tz: Float, out: FloatArray) {
        var fx = tx - ex; var fy = ty - ey; var fz = tz - ez
        val fl = kotlin.math.sqrt(fx * fx + fy * fy + fz * fz).coerceAtLeast(1e-6f); fx /= fl; fy /= fl; fz /= fl
        // s = f × up(0,1,0)
        var sx = -fz; var sy = 0f; var sz = fx
        var sl = kotlin.math.sqrt(sx * sx + sz * sz)
        if (sl < 1e-5f) { sx = 1f; sz = 0f; sl = 1f }
        sx /= sl; sz /= sl; sy /= sl
        val ux = sy * fz - sz * fy; val uy = sz * fx - sx * fz; val uz = sx * fy - sy * fx
        out[0] = sx; out[4] = sy; out[8] = sz
        out[1] = ux; out[5] = uy; out[9] = uz
        out[2] = -fx; out[6] = -fy; out[10] = -fz
        out[3] = 0f; out[7] = 0f; out[11] = 0f
        out[12] = -(sx * ex + sy * ey + sz * ez)
        out[13] = -(ux * ex + uy * ey + uz * ez)
        out[14] = fx * ex + fy * ey + fz * ez
        out[15] = 1f
    }
}
