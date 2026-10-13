package com.reeflive.app.render

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** One drawable node of a model: mesh + material + its GLB node. */
class DrawPart(
    val node: GlbNode,
    val mesh: Mesh,
    val material: Material,
    val kind: MaterialKind,
    /** World-space centre of this part (polyps: mean instance position), for drawing one clump of a coral model. */
    val center: FloatArray = FloatArray(3),
) {
    val part: String get() = node.part
    /** Fin kind for the fish shader (0 median, 1 pectoral, 2 caudal, 3 pelvic). */
    val finKind: Float = node.name.lowercase().let { n ->
        when { "pectoral" in n -> 1f; "caudal" in n -> 2f; "pelvic" in n -> 3f; else -> 0f }
    }
    val eyePivot: FloatArray = node.vec3("pivot") ?: floatArrayOf(0.4f, 0.05f, 0f)
    val identityWorld: Boolean = M.isIdentity(node.world)
}

/** A GLB uploaded to GL. */
class GpuModel(val path: String, val glb: GlbModel, val parts: List<DrawPart>, private val textures: List<Texture>) {
    val triangles: Int = parts.sumOf { it.mesh.triangles }
    val rootAnchor: String? = glb.rootString("anchor")
    /** Organism models in cm (feather duster) are placed rigidly instead of spline-skinned. */
    val rigid: Boolean = glb.rootString("unit")?.startsWith("centimeters") == true

    /** Bounds over all non-polyp parts in world space: min xyz, max xyz. */
    val bounds: FloatArray = FloatArray(6).also { b ->
        b[0] = Float.MAX_VALUE; b[1] = Float.MAX_VALUE; b[2] = Float.MAX_VALUE
        b[3] = -Float.MAX_VALUE; b[4] = -Float.MAX_VALUE; b[5] = -Float.MAX_VALUE
        val tmp = FloatArray(3)
        for (p in parts) {
            val prims = p.node.primitives
            for (pr in prims) for (cx in 0..1) for (cy in 0..1) for (cz in 0..1) {
                M.transformPoint(p.node.world, if (cx == 0) pr.bmin[0] else pr.bmax[0], if (cy == 0) pr.bmin[1] else pr.bmax[1], if (cz == 0) pr.bmin[2] else pr.bmax[2], tmp)
                for (k in 0 until 3) { b[k] = minOf(b[k], tmp[k]); b[k + 3] = maxOf(b[k + 3], tmp[k]) }
            }
        }
        if (b[0] > b[3]) b.fill(0f)
    }

    fun release() {
        parts.forEach { it.mesh.release() }
        textures.forEach { it.release() }
    }
}

/** CPU-side result of a background load. */
private class Parsed(val path: String, val glb: GlbModel?, val bitmaps: Map<Int, Bitmap>, val error: Throwable?)

/**
 * Asynchronous model/texture loading: GLB parsing and image decoding run on a worker thread; GL uploads
 * happen on the GL thread in [pump] (a few per frame, so the first frame is not blocked).
 */
class AssetStore(private val assets: AssetManager, private var quality: RenderQuality) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { r -> Thread(r, "reef-assets").apply { priority = Thread.NORM_PRIORITY - 1 } }
    private val requested = ConcurrentHashMap.newKeySet<String>()
    private val ready = ConcurrentLinkedQueue<Parsed>()
    private val models = HashMap<String, GpuModel>()
    private val failed = HashSet<String>()
    private val textureReady = ConcurrentLinkedQueue<Pair<String, Bitmap?>>()
    private val textures = HashMap<String, Texture>()
    private val modelFiles: Set<String> by lazy { (assets.list("models") ?: emptyArray()).map { "models/$it" }.toSet() }

    val loadedCount: Int get() = models.size
    val requestedCount: Int get() = requested.size

    fun exists(path: String) = path in modelFiles

    /** Starts loading [path] if needed; returns the model once it is on the GPU. */
    fun model(path: String): GpuModel? {
        models[path]?.let { return it }
        if (path in failed || !exists(path)) return null
        if (requested.add(path)) executor.execute { ready.add(parse(path)) }
        return null
    }

    /** Shared texture by asset path (e.g. "textures/caustic_512.png"). */
    fun texture(path: String, maxEdge: Int = quality.maxTexture): Texture? {
        textures[path]?.let { return it }
        if (requested.add(path)) executor.execute {
            val bmp = runCatching { assets.open(path).use { Texture.decode(it.readBytes(), maxEdge) } }.getOrNull()
            textureReady.add(path to bmp)
        }
        return null
    }

    private fun parse(path: String): Parsed = try {
        val bytes = assets.open(path).use { it.readBytes() }
        val glb = GlbLoader.parse(bytes, path)
        val used = glb.materials.map { it.baseColorImage }.filter { it >= 0 }.toSet()
        val maxEdge = if (glb.rootFloat("lengthCm") != null && quality == RenderQuality.LOW) 512 else quality.maxTexture
        val bitmaps = used.mapNotNull { i -> glb.images.getOrNull(i)?.let { b -> Texture.decode(b, maxEdge)?.let { i to it } } }.toMap()
        Parsed(path, glb, bitmaps, null)
    } catch (t: Throwable) {
        Parsed(path, null, emptyMap(), t)
    }

    /** GL thread: uploads up to [budget] finished loads. Returns true if anything was uploaded. */
    fun pump(budget: Int = 2): Boolean {
        var did = false
        repeat(budget) {
            val t = textureReady.poll()
            if (t != null) {
                val (path, bmp) = t
                textures[path] = if (bmp != null) Texture.fromBitmap(bmp).also { bmp.recycle() } else Texture.solid(128, 128, 255)
                did = true
            }
            val p = ready.poll() ?: return did
            did = true
            if (p.glb == null) {
                Log.e(Gl.TAG, "load ${p.path} failed", p.error)
                failed += p.path
                return@repeat
            }
            models[p.path] = upload(p)
        }
        return did
    }

    private fun upload(p: Parsed): GpuModel {
        val glb = p.glb!!
        val texByImage = HashMap<Int, Texture>()
        for ((i, bmp) in p.bitmaps) { texByImage[i] = Texture.fromBitmap(bmp); bmp.recycle() }
        val organism = glb.rootFloat("lengthCm") != null
        val parts = ArrayList<DrawPart>()
        for (node in glb.nodes) {
            if (node.hidden) continue
            val table = if (node.part == "polyp") node.instances() else null
            val density = if (glb.rootString("species")?.contains("star_polyps") == true) quality.polypDensity else 1f
            val inst = when {
                table != null -> Mesh.polypInstances(table, node.instanceStride, density)
                node.part == "polyp" -> Mesh.SINGLE_INSTANCE
                else -> null
            }
            for (prim in node.primitives) {
                val src = glb.materials.getOrNull(prim.material) ?: GlbMaterial.DEFAULT
                val kind = if (organism) organismKind(node.part) else MaterialKind.forPart(node.part, src.blended)
                if (kind == MaterialKind.UNLIT_HIDDEN) continue
                val mat = Material(kind, src, texByImage[src.baseColorImage])
                parts += DrawPart(node, Mesh.from(prim, inst), mat, kind, partCenter(node, prim, table))
            }
        }
        return GpuModel(p.path, glb, parts, texByImage.values.toList())
    }

    private fun partCenter(node: GlbNode, prim: GlbPrimitive, table: FloatArray?): FloatArray {
        val out = FloatArray(3)
        val stride = node.instanceStride
        if (table != null && stride >= 3 && table.size >= stride) {
            val n = table.size / stride
            for (i in 0 until n) for (k in 0 until 3) out[k] += table[i * stride + k]
            for (k in 0 until 3) out[k] /= n.toFloat()
            return out
        }
        M.transformPoint(node.world, (prim.bmin[0] + prim.bmax[0]) * 0.5f, (prim.bmin[1] + prim.bmax[1]) * 0.5f, (prim.bmin[2] + prim.bmax[2]) * 0.5f, out)
        return out
    }

    private fun organismKind(part: String) = when (part) {
        "fin" -> MaterialKind.FIN_SHEET
        "polyp" -> MaterialKind.POLYP
        else -> MaterialKind.FISH_SKIN
    }

    /** GL context lost: forget GPU objects (they died with the context) and reload lazily. */
    fun onContextLost(newQuality: RenderQuality) {
        quality = newQuality
        models.clear(); textures.clear(); failed.clear(); requested.clear()
        ready.clear(); textureReady.clear()
    }

    /** Releases GPU objects (GL thread, context alive). */
    fun release() {
        models.values.forEach { it.release() }
        textures.values.forEach { it.release() }
        models.clear(); textures.clear()
    }

    fun shutdown() = executor.shutdownNow()
}

/**
 * Species → model path, read from the core species sheets on the classpath (`species/<id>.json`:
 * `model`, `variantModels`). Missing sheets or assets fall back to the procedural placeholder.
 */
class ModelRegistry(private val loader: ClassLoader? = ModelRegistry::class.java.classLoader) {
    private class Entry(val model: String?, val variants: Map<String, String>, val kind: String)
    private val cache = HashMap<String, Entry>()
    private val json = Json { ignoreUnknownKeys = true }

    private fun entry(speciesId: String): Entry = cache.getOrPut(speciesId) {
        val text = runCatching { loader?.getResourceAsStream("species/$speciesId.json")?.bufferedReader()?.use { it.readText() } }.getOrNull()
        val o = text?.let { runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
        if (o == null) Entry(null, emptyMap(), "") else Entry(
            (o["model"] as? JsonPrimitive)?.contentOrNullSafe(),
            (o["variantModels"] as? JsonObject)?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNullSafe()?.let { k to it } }?.toMap() ?: emptyMap(),
            (o["kind"] as? JsonPrimitive)?.contentOrNullSafe() ?: "",
        )
    }

    /** Model path for an organism; [variant] picks `variantModels` when present. */
    fun modelFor(speciesId: String, variant: String?): String? {
        val e = entry(speciesId)
        return variant?.let { e.variants[it] } ?: e.model
    }

    /** All candidate models for a coral species (default + variants); the renderer matches the anchor. */
    fun coralModels(speciesId: String): List<String> {
        val e = entry(speciesId)
        return listOfNotNull(e.model) + e.variants.values
    }

    companion object {
        fun lod1(path: String): String = path.removeSuffix(".glb") + "_lod1.glb"

        /**
         * Stand-ins for coral models that are mapped but not shipped yet. The hairy green mushroom
         * (species `rhodactis_sp` → `models/mushroom_hairy.glb`) borrows the plain mushroom until its own
         * GLB lands; the renderer draws the stand-in moved to the colony's anchor.
         */
        val FALLBACK_MODELS: Map<String, String> = mapOf("models/mushroom_hairy.glb" to "models/mushroom.glb")
    }
}
