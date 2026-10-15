package com.reeflive.app.render

import android.content.res.AssetManager
import com.reeflive.core.sim.LifeForm
import com.reeflive.core.sim.LifeStageSpec
import com.reeflive.core.sim.SimSpecies
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Juvenile / adult / sex forms on screen (docs/research/life_stages.md). The core sends each fish's [LifeForm] (stage
 * blend, body-shape scale, sex form); this maps the form names to body textures painted on the model's own UV layout
 * by `tools/blender/life_stages.py` (`textures/stages/manifest.json`: model path → form → texture) and to the
 * fish shader's additive `uAlbedoStage` / `uStageMix` / `uStageShape` uniforms.
 *
 * Texture: unit 0 gets form A (or keeps the GLB's own body albedo when A is the model's base form), unit [UNIT]
 * form B, mixed by `uStageMix`.
 * A is the stage the fish is leaving and B the stage it is growing into; once grown (and where the species has one),
 * A is the adult form and B the sex form (leopard wrasse terminal male), mixed by the sex-change progress.
 */
class LifeStageVisuals(private val assets: AssetManager?, private val loader: ClassLoader? = LifeStageVisuals::class.java.classLoader) {
    /** Two textures and their mix for one material (texA null = keep the GLB's own texture on unit 0). */
    class TexPair {
        var texA: String? = null
        var texB: String? = null
        var mix = 0f
        fun clear() { texA = null; texB = null; mix = 0f }
    }

    /** Resolved draw inputs for one fish (reused; no allocation per frame). */
    class Binding {
        val body = TexPair()
        /** Fin sheet forms (engineer goby: the juvenile and adult fins lose the subadult's yellow ticks). */
        val fin = TexPair()
        /** Added to 1 in the shader: body depth (model Y) and width (model Z) scale − 1. */
        var depth = 0f
        var width = 0f
        fun clear() { body.clear(); fin.clear(); depth = 0f; width = 0f }
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private class ModelForms(val base: String?, val forms: Map<String, String>, val finBase: String?, val finForms: Map<String, String>)

    /** model path (LOD0 name) → its stage forms; `base` is the form the GLB's own body (fin) texture shows. */
    private val manifest: Map<String, ModelForms> by lazy {
        val text = runCatching { assets?.open(MANIFEST)?.bufferedReader()?.use { it.readText() } }.getOrNull() ?: return@lazy emptyMap()
        val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return@lazy emptyMap()
        val models = root["models"] as? JsonObject ?: return@lazy emptyMap()
        fun str(o: JsonObject?, k: String) = (o?.get(k) as? JsonPrimitive)?.takeIf { it.isString }?.content
        fun map(o: JsonObject?) = o?.mapNotNull { (k, p) -> (p as? JsonPrimitive)?.content?.let { k to it } }?.toMap() ?: emptyMap()
        models.mapValues { (_, v) ->
            val o = v as? JsonObject
            val fins = o?.get("fins") as? JsonObject
            ModelForms(str(o, "base"), map(o?.get("forms") as? JsonObject), str(fins, "base"), map(fins?.get("forms") as? JsonObject))
        }
    }

    private val specs = HashMap<String, LifeStageSpec?>()

    // (getOrPut would re-read and re-parse the species JSON on every call for the null entries: inverts, every frame)
    private fun spec(speciesId: String): LifeStageSpec? {
        if (specs.containsKey(speciesId)) return specs[speciesId]
        val text = runCatching { loader?.getResourceAsStream("species/${ModelRegistry.SHEET_ALIASES[speciesId] ?: speciesId}.json")?.bufferedReader()?.use { it.readText() } }.getOrNull()
        val sp = text?.let { runCatching { SimSpecies.parse(it) }.getOrNull() }
        val r = if (sp == null || !sp.kind.equals("FISH", true)) null else LifeStageSpec.of(sp)
        specs[speciesId] = r
        return r
    }

    /** Every stage texture of [modelPath], for preloading. */
    fun textures(modelPath: String): Collection<String> = manifest[lod0(modelPath)]?.let { it.forms.values + it.finForms.values } ?: emptyList()

    /**
     * Fills [out] for a fish drawn with [modelPath]. [form] is the core's view (from `LifeFormChannel`); without one
     * (demo snapshots) the stage blend is worked out here from the sheet and the drawn length [lengthCm].
     */
    fun resolve(speciesId: String, modelPath: String, lengthCm: Float, form: LifeForm?, out: Binding) {
        out.clear()
        var a: String; var b: String; var mix: Float
        if (form != null) {
            out.depth = form.depthScale - 1f; out.width = form.widthScale - 1f
            val sexForm = form.sexForm
            if (sexForm != null && form.sexMix > 0f && form.stageMix <= 0f) { a = form.toForm; b = sexForm; mix = form.sexMix }
            else { a = form.fromForm; b = form.toForm; mix = form.stageMix }
        } else {
            val sp = spec(speciesId) ?: return
            val (sa, sb, m) = sp.blendAt(lengthCm.toDouble())
            a = sa.formName; b = sb.formName; mix = m.toFloat()
            out.depth = (sa.depthScale + (sb.depthScale - sa.depthScale) * m).toFloat() - 1f
            out.width = (sa.widthScale + (sb.widthScale - sa.widthScale) * m).toFloat() - 1f
        }
        val mf = manifest[lod0(modelPath)] ?: return
        pick(mf.base, mf.forms, a, b, mix, out.body)
        if (mf.finForms.isNotEmpty()) {
            // fins follow the growth stages only (no sex forms)
            val fa = form?.fromForm ?: a; val fb = form?.toForm ?: b; val fm = form?.stageMix ?: mix
            pick(mf.finBase, mf.finForms, fa, fb, fm, out.fin)
        }
    }

    /** Texture pair for forms [a] → [b] at [mix]; null = the GLB's own texture (the base form, or no stage texture). */
    private fun pick(base: String?, forms: Map<String, String>, a: String, b: String, mix: Float, out: TexPair) {
        val ta = if (a == base) null else forms[a]
        val tb = if (b == base) null else forms[b]
        when {
            a == b || mix <= 0.001f -> out.texA = ta
            mix >= 0.999f -> out.texA = tb
            ta == tb -> out.texA = ta
            ta == null -> { out.texB = tb; out.mix = mix }
            tb == null -> { out.texB = ta; out.mix = 1f - mix }
            else -> { out.texA = ta; out.texB = tb; out.mix = mix }
        }
    }

    companion object {
        const val MANIFEST = "textures/stages/manifest.json"
        /** Texture unit of `uAlbedoStage` (units 0–13 are taken; GLES 3 guarantees 16). */
        const val UNIT = 15

        /** "models/x_lod1.glb" → "models/x.glb" (both LODs share the UV layout and the stage textures). */
        fun lod0(path: String): String = if (path.endsWith("_lod1.glb")) path.removeSuffix("_lod1.glb") + ".glb" else path
    }
}
