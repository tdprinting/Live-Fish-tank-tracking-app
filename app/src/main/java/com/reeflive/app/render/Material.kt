package com.reeflive.app.render

import android.opengl.GLES30
import com.reeflive.core.sim.AlgaeCover

/** Material variants (ARCHITECTURE §8.2); the GLB `extras.part` picks one ([forPart]). */
enum class MaterialKind {
    ROCK, SAND, SOFT_CORAL, CARD, POLYP, FISH_SKIN, FIN_SHEET, GLASS, WATER, GENERIC, EMITTER, UNLIT_HIDDEN;

    companion object {
        /** Scene/coral parts. Organism parts are handled by the fish path. */
        fun forPart(part: String, blended: Boolean): MaterialKind = when (part) {
            "rock" -> ROCK
            "sand" -> SAND
            "glass" -> GLASS
            "water" -> WATER
            "stand" -> UNLIT_HIDDEN
            "emitter" -> EMITTER
            "polyp" -> POLYP
            "card" -> CARD
            "body", "tube" -> SOFT_CORAL
            "fin" -> FIN_SHEET
            "backglass", "rim", "equipment", "light" -> GENERIC
            else -> if (blended) GENERIC else GENERIC
        }
    }
}

/** All shader programs (compiled once per GL context). */
class Programs(private val source: (String) -> String, quality: RenderQuality) {
    private val q = mapOf("QUALITY" to quality.shaderLevel.toString())
    private fun p(name: String, v: String, f: String, extra: Map<String, String> = emptyMap()) = ShaderProgram.build(name, v, f, q + extra, source)

    val rock = p("rock", "static.vert", "rock.frag", mapOf("ROCK" to "1"))
    val sand = p("sand", "static.vert", "rock.frag", mapOf("SAND" to "1"))
    val generic = p("generic", "static.vert", "generic.frag")
    /** The supply shelf beside the tank (SupplyShelfScene): its own room light rig instead of the fixtures. */
    val shelf = p("shelf", "static.vert", "shelf.frag")
    val softCoral = p("softcoral", "static.vert", "softcoral.frag", mapOf("SOFTCORAL" to "1"))
    val card = p("card", "static.vert", "softcoral.frag", mapOf("SOFTCORAL" to "1", "CARD" to "1"))
    val polyp = p("polyp", "polyp.vert", "polyp.frag")
    /** Green star polyp stolon mat on a copy of the rock's triangles (GspMatDraw). */
    val gspMat = p("gsp_mat", "static.vert", "gsp_mat.frag", mapOf("GSPMAT" to "1"))
    val fish = p("fish", "fish.vert", "fish.frag")
    val fin = p("fin", "fish.vert", "fish.frag", mapOf("FIN" to "1"))
    val glass = p("glass", "static.vert", "glass.frag")
    val water = p("water", "water.vert", "water.frag")
    val particles = p("particles", "particles.vert", "particles.frag")
    val shafts = p("shafts", "shafts.vert", "shafts.frag")
    val snow = p("snow", "snow.vert", "snow.frag")
    /** Shadow-map depth pass (ShadowMap): the casters' own vertex shaders with a depth-only fragment shader. */
    val shadow = p("shadow", "static.vert", "shadow.frag")
    val shadowCoral = p("shadow_coral", "static.vert", "shadow.frag", mapOf("SOFTCORAL" to "1"))
    val shadowFish = p("shadow_fish", "fish.vert", "shadow.frag")

    fun all() = listOf(rock, sand, generic, shelf, softCoral, card, polyp, gspMat, fish, fin, glass, water, particles, shafts, snow, shadow, shadowCoral, shadowFish)
    fun release() = all().forEach { it.release() }
}

/**
 * A GLB material bound to a program: base color factor/texture, roughness, emissive, alpha cutoff.
 * Textures are owned by the model ([GpuModel]); [albedo] null means "use the white default".
 */
class Material(val kind: MaterialKind, val src: GlbMaterial, val albedo: Texture?) {
    val blended: Boolean get() = src.blended || kind == MaterialKind.GLASS || kind == MaterialKind.WATER
    /** Parsed once: [apply] runs for every part every frame. */
    val alphaCutoff: Float = if (src.alphaMode == "MASK") src.alphaCutoff
        else (src.extras["alphaTest"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toFloatOrNull() ?: 0f

    /** Binds texture unit 0 and the per-material uniforms. [white] is the 1×1 default. */
    fun apply(p: ShaderProgram, white: Texture, alphaTestOverride: Float? = null) {
        (albedo ?: white).bind(0)
        val c = src.baseColor
        p.set4f("uBaseColor", c[0], c[1], c[2], c.getOrElse(3) { 1f })
        p.set1f("uHasTexture", if (albedo != null) 1f else 0f)
        p.set1f("uRoughness", src.roughness)
        p.set3f("uEmissive", src.emissive[0], src.emissive[1], src.emissive[2])
        p.set1f("uAlphaCutoff", alphaTestOverride ?: alphaCutoff)
    }
}

/**
 * The std140 `Frame` uniform block (layout in assets/shaders/common.glsl), one UBO updated per frame.
 */
class FrameUniforms {
    val data = FloatArray(FLOATS)
    private val ubo: Int = Gl.genBuffer()
    private val buffer = Gl.floats(data)

    init {
        GLES30.glBindBuffer(GLES30.GL_UNIFORM_BUFFER, ubo); Gl.check("glBindBuffer(UBO)")
        GLES30.glBufferData(GLES30.GL_UNIFORM_BUFFER, FLOATS * 4, null, GLES30.GL_DYNAMIC_DRAW); Gl.check("glBufferData(UBO)")
        GLES30.glBindBufferBase(GLES30.GL_UNIFORM_BUFFER, ShaderProgram.FRAME_BINDING, ubo); Gl.check("glBindBufferBase(UBO)")
        GLES30.glBindBuffer(GLES30.GL_UNIFORM_BUFFER, 0); Gl.check("glBindBuffer(0)")
    }

    fun fill(cam: Camera, timeSec: Float, light: LightingState, quality: RenderQuality, algaeBloom: Float, flow: Float, hasSurface: Boolean, surfaceW: Int, surfaceH: Int, algae: AlgaeCover = AlgaeCover.EMPTY) {
        val d = data
        cam.view.copyInto(d, 0); cam.proj.copyInto(d, 16); cam.viewProj.copyInto(d, 32)
        d[48] = cam.eye[0]; d[49] = cam.eye[1]; d[50] = cam.eye[2]; d[51] = timeSec
        val cosHalf = kotlin.math.cos(Math.toRadians(LightingState.CONE_DEG / 2.0)).toFloat()
        val lc = light.lightColor()
        for (i in 0 until 2) {
            val lp = LightingState.LIGHT_POS[i]
            d[52 + i * 4] = lp[0]; d[53 + i * 4] = lp[1]; d[54 + i * 4] = lp[2]; d[55 + i * 4] = cosHalf
            d[60 + i * 4] = lc[0]; d[61 + i * 4] = lc[1]; d[62 + i * 4] = lc[2]; d[63 + i * 4] = light.intensity
        }
        d[68] = light.ambient[0]; d[69] = light.ambient[1]; d[70] = light.ambient[2]; d[71] = light.blueFraction
        d[72] = light.fogColor[0]; d[73] = light.fogColor[1]; d[74] = light.fogColor[2]; d[75] = FOG_DENSITY
        d[76] = WATER_LEVEL; d[77] = CAUSTIC_STRENGTH; d[78] = CAUSTIC_SCALE; d[79] = quality.shaderLevel.toFloat()
        d[80] = if (light.night) 1f else 0f; d[81] = algaeBloom; d[82] = flow; d[83] = SAND_TOP
        d[84] = ABSORB[0]; d[85] = ABSORB[1]; d[86] = ABSORB[2]; d[87] = SCATTER
        d[88] = Camera.TANK[0]; d[89] = Camera.TANK[1]; d[90] = Camera.TANK[2]; d[91] = 0f
        d[92] = surfaceW.toFloat(); d[93] = surfaceH.toFloat(); d[94] = if (hasSurface) 1f else 0f; d[95] = 0f
        AlgaeVisuals.pack(algae, d, ALGAE_OFFSET) // uAlgaeGlass, uAlgaeSand, uAlgaeTop, uAlgaeShade (96..111)
        buffer.position(0); buffer.put(d); buffer.position(0)
        GLES30.glBindBuffer(GLES30.GL_UNIFORM_BUFFER, ubo); Gl.check("glBindBuffer(UBO)")
        GLES30.glBufferSubData(GLES30.GL_UNIFORM_BUFFER, 0, FLOATS * 4, buffer); Gl.check("glBufferSubData(UBO)")
        GLES30.glBindBufferBase(GLES30.GL_UNIFORM_BUFFER, ShaderProgram.FRAME_BINDING, ubo); Gl.check("glBindBufferBase(UBO)")
    }

    /**
     * Quality-scaled realism knobs (`uFx`): light-shaft strength, caustic dispersion (High), surface ripple. Call
     * before [fill], which uploads the whole block.
     */
    fun setEffects(quality: RenderQuality) {
        data[FX_OFFSET + 1] = quality.shaftStrength
        data[FX_OFFSET + 2] = if (quality == RenderQuality.HIGH) 1f else 0f
        data[FX_OFFSET + 3] = if (quality == RenderQuality.LOW) 0.5f else 1f
    }

    private val casterD2 = FloatArray(MAX_CASTERS)

    /**
     * Picks up to [max] visible organisms nearest the eye (ex, ey, ez) as soft contact-shadow casters (`uCasters`,
     * `uFx.x`). No allocation. Call before [fill].
     */
    fun setCasters(rigs: Collection<FishRig>, max: Int, ex: Float, ey: Float, ez: Float) {
        val cap = max.coerceIn(0, MAX_CASTERS)
        var n = 0
        if (cap > 0) for (r in rigs) {
            if (!r.visible) continue
            val s = r.spine
            val x = s[24]; val y = s[25]; val z = s[26]
            val dx = x - ex; val dy = y - ey; val dz = z - ez
            val d2 = dx * dx + dy * dy + dz * dz
            var slot = -1
            if (n < cap) { slot = n; n++ }
            else {
                var worst = 0
                for (k in 1 until n) if (casterD2[k] > casterD2[worst]) worst = k
                if (d2 < casterD2[worst]) slot = worst
            }
            if (slot < 0) continue
            casterD2[slot] = d2
            val o = CASTER_OFFSET + slot * 4
            data[o] = x; data[o + 1] = y; data[o + 2] = z; data[o + 3] = r.scale
        }
        data[FX_OFFSET] = n.toFloat()
    }

    /**
     * Re-uploads only `uViewProj` (floats 32..47): [m] for the shadow-map pass ([ShadowMap.matrix]), or the camera's
     * own matrix (null) to restore it afterwards. Call after [fill].
     */
    fun overrideViewProj(m: FloatArray?) {
        vpBuffer.position(0)
        if (m != null) vpBuffer.put(m, 0, 16) else vpBuffer.put(data, VIEW_PROJ_OFFSET, 16)
        vpBuffer.position(0)
        GLES30.glBindBuffer(GLES30.GL_UNIFORM_BUFFER, ubo); Gl.check("glBindBuffer(UBO)")
        GLES30.glBufferSubData(GLES30.GL_UNIFORM_BUFFER, VIEW_PROJ_OFFSET * 4, 16 * 4, vpBuffer); Gl.check("glBufferSubData(viewProj)")
    }
    private val vpBuffer = Gl.floats(FloatArray(16))

    fun release() = Gl.deleteBuffer(ubo)

    companion object {
        /** std140: 3 mat4 (48 floats) + 12 vec4 (48) + 4 algae vec4 (16) + uFx (4) + 12 casters (48) + uShadowMat (16) + uShadow (4). */
        const val VIEW_PROJ_OFFSET = 32
        const val ALGAE_OFFSET = 96
        const val FX_OFFSET = ALGAE_OFFSET + AlgaeVisuals.FLOATS
        const val CASTER_OFFSET = FX_OFFSET + 4
        const val MAX_CASTERS = 12
        /** `uShadowMat` then `uShadow` ([ShadowMap.fill]). */
        const val SHADOW_OFFSET = CASTER_OFFSET + MAX_CASTERS * 4
        const val FLOATS = SHADOW_OFFSET + 20
        const val FOG_DENSITY = 0.005f
        const val WATER_LEVEL = Camera.WATER_Y
        const val SAND_TOP = Camera.SAND_Y
        const val CAUSTIC_STRENGTH = 0.85f
        const val CAUSTIC_SCALE = 0.04f
        /**
         * Beer-Lambert absorption per cm (R, G, B) and scattering (SIMULATION §9.7). Somewhat above pure seawater so
         * the blue-green fall-off reads across a 122 cm tank (a side view keeps ~40 % of red).
         */
        val ABSORB = floatArrayOf(0.0072f, 0.0016f, 0.0010f)
        const val SCATTER = 0.0004f
    }
}
