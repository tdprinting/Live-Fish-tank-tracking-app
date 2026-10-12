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

    val rock = p("rock", "static.vert", "rock.frag")
    val sand = p("sand", "static.vert", "rock.frag", mapOf("SAND" to "1"))
    val generic = p("generic", "static.vert", "generic.frag")
    val softCoral = p("softcoral", "static.vert", "softcoral.frag", mapOf("SOFTCORAL" to "1"))
    val card = p("card", "static.vert", "softcoral.frag", mapOf("SOFTCORAL" to "1", "CARD" to "1"))
    val polyp = p("polyp", "polyp.vert", "polyp.frag")
    val fish = p("fish", "fish.vert", "fish.frag")
    val fin = p("fin", "fish.vert", "fish.frag", mapOf("FIN" to "1"))
    val glass = p("glass", "static.vert", "glass.frag")
    val water = p("water", "water.vert", "water.frag")
    val particles = p("particles", "particles.vert", "particles.frag")
    val shafts = p("shafts", "shafts.vert", "shafts.frag")

    fun all() = listOf(rock, sand, generic, softCoral, card, polyp, fish, fin, glass, water, particles, shafts)
    fun release() = all().forEach { it.release() }
}

/**
 * A GLB material bound to a program: base color factor/texture, roughness, emissive, alpha cutoff.
 * Textures are owned by the model ([GpuModel]); [albedo] null means "use the white default".
 */
class Material(val kind: MaterialKind, val src: GlbMaterial, val albedo: Texture?) {
    val blended: Boolean get() = src.blended || kind == MaterialKind.GLASS || kind == MaterialKind.WATER
    val alphaCutoff: Float get() = when {
        src.alphaMode == "MASK" -> src.alphaCutoff
        (src.extras["alphaTest"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toFloatOrNull() != null ->
            (src.extras["alphaTest"] as kotlinx.serialization.json.JsonPrimitive).content.toFloat()
        else -> 0f
    }

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

    fun release() = Gl.deleteBuffer(ubo)

    companion object {
        /** std140: 3 mat4 (48 floats) + 12 vec4 (48) + 4 algae vec4 (16). */
        const val ALGAE_OFFSET = 96
        const val FLOATS = ALGAE_OFFSET + AlgaeVisuals.FLOATS
        const val FOG_DENSITY = 0.004f
        const val WATER_LEVEL = 56f
        const val SAND_TOP = 3f
        const val CAUSTIC_STRENGTH = 0.6f
        const val CAUSTIC_SCALE = 0.04f
        /** Seawater Beer-Lambert absorption per cm (R, G, B) and scattering (SIMULATION §9.7). */
        val ABSORB = floatArrayOf(0.0045f, 0.0009f, 0.0006f)
        const val SCATTER = 0.0004f
    }
}
