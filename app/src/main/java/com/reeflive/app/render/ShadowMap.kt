package com.reeflive.app.render

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import com.reeflive.core.sim.TankDims

/**
 * Real-time shadows from the two AI Primes: one orthographic depth pass looking straight down over the tank interior
 * (the fixtures hang ~34 cm above the water, nearly overhead), sampled with hardware-compared PCF in lighting.glsl
 * `shadowAt` by every lit shader (rock, sand, corals, fish, equipment).
 *
 * Tiers ([RenderQuality.shadowMapSize]): High 2048×640 (≈ 0.06 cm per texel) re-rendered every frame with the fish;
 * Medium 1024×320 with the static casters only, re-rendered when the scene changes (models loading, coral list) and
 * every [STATIC_REFRESH_FRAMES] frames; Low has no pass. The depth texture always exists (1×1, cleared to far on Low)
 * so `uShadowMap` (a sampler2DShadow on [UNIT]) is never bound to an incomplete texture.
 *
 * The pass reuses the casters' own vertex shaders (static.vert / fish.vert + shadow.frag) by swapping the Frame UBO's
 * `uViewProj` for [matrix] ([FrameUniforms.overrideViewProj]) while it draws.
 */
class ShadowMap(quality: RenderQuality) {
    val width: Int = quality.shadowMapSize
    val height: Int = if (width > 0) (width * 5) / 16 else 0
    val enabled: Boolean get() = width > 0
    /** Re-render the fish into the map every frame (High). */
    val dynamic: Boolean = quality == RenderQuality.HIGH

    /** World → shadow clip (column-major), orthographic; depth grows downward from [Y_TOP]. */
    val matrix = FloatArray(16).also { m ->
        m[0] = 2f / (X1 - X0); m[12] = -2f * X0 / (X1 - X0) - 1f
        m[9] = 2f / (Z1 - Z0); m[13] = -2f * Z0 / (Z1 - Z0) - 1f
        m[6] = -2f / (Y_TOP - Y_BOTTOM); m[14] = 2f * Y_TOP / (Y_TOP - Y_BOTTOM) - 1f
        m[15] = 1f
    }
    /** cm per shadow texel along x (the PCF kernel is sized in cm). */
    val cmPerTexel: Float = if (width > 0) (X1 - X0) / width else 1f

    private val tex: Int = Gl.genTexture()
    private val fbo: Int
    private var signature = Long.MIN_VALUE
    private var framesSince = 0

    init {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex); Gl.check("bind shadow tex")
        val w = maxOf(width, 1); val h = maxOf(height, 1)
        val far = if (enabled) null else ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()).apply { putInt(-1); position(0) }
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_DEPTH_COMPONENT24, w, h, 0, GLES30.GL_DEPTH_COMPONENT, GLES30.GL_UNSIGNED_INT, far)
        Gl.check("shadow tex storage")
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR); Gl.check("shadow min")
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR); Gl.check("shadow mag")
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE); Gl.check("shadow wrapS")
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE); Gl.check("shadow wrapT")
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_COMPARE_MODE, GLES30.GL_COMPARE_REF_TO_TEXTURE); Gl.check("shadow compare")
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_COMPARE_FUNC, GLES30.GL_LEQUAL); Gl.check("shadow func")
        fbo = if (enabled) {
            val f = Gl.genFramebuffer()
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, f); Gl.check("bind shadow fbo")
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_TEXTURE_2D, tex, 0); Gl.check("attach shadow depth")
            GLES30.glDrawBuffers(1, intArrayOf(GLES30.GL_NONE), 0); Gl.check("shadow drawBuffers")
            GLES30.glReadBuffer(GLES30.GL_NONE); Gl.check("shadow readBuffer")
            val ok = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) == GLES30.GL_FRAMEBUFFER_COMPLETE
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0); Gl.check("unbind shadow fbo")
            if (ok) f else { Gl.deleteFramebuffer(f); 0 }
        } else 0
    }

    /** True when the map holds valid depth (and shaders should sample it). */
    var valid = false; private set

    /**
     * Whether to re-render this frame: always on High; on Medium when [sceneSignature] changed or the periodic refresh
     * is due. No pass on Low or when the FBO failed.
     */
    fun needsUpdate(sceneSignature: Long): Boolean {
        if (!enabled || fbo == 0) return false
        framesSince++
        if (dynamic || sceneSignature != signature || framesSince >= STATIC_REFRESH_FRAMES) {
            signature = sceneSignature; framesSince = 0
            return true
        }
        return false
    }

    /** Binds the depth target, clears it and sets depth-only state; draw the casters, then call [end]. */
    fun begin() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo); Gl.check("bind shadow fbo")
        GLES30.glViewport(0, 0, width, height); Gl.check("shadow viewport")
        Gl.state(depthTest = true, depthWrite = true, blend = Gl.Blend.NONE, cull = false)
        GLES30.glColorMask(false, false, false, false); Gl.check("shadow colorMask")
        GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT); Gl.check("shadow clear")
        // never sample the map while it is the render target
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + UNIT); Gl.check("shadow active")
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0); Gl.check("shadow unbind")
    }

    /** Restores colour writes; the caller binds its scene target and viewport next. */
    fun end() {
        GLES30.glColorMask(true, true, true, true); Gl.check("shadow colorMask restore")
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0); Gl.check("unbind shadow fbo")
        valid = true
    }

    fun bind() {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + UNIT); Gl.check("shadow active")
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex); Gl.check("bind shadow tex")
    }

    /** `uShadowMat` / `uShadow` into the Frame data ([FrameUniforms.SHADOW_OFFSET]); call before [FrameUniforms.fill]. */
    fun fill(d: FloatArray, offset: Int) {
        matrix.copyInto(d, offset)
        d[offset + 16] = if (valid) 1f else 0f
        d[offset + 17] = cmPerTexel
        d[offset + 18] = STRENGTH
        d[offset + 19] = BIAS_CM
    }

    fun release() {
        Gl.deleteFramebuffer(fbo)
        Gl.deleteTexture(tex)
    }

    companion object {
        /** Texture unit of `uShadowMap` ([ShaderProgram.SAMPLER_UNITS]). */
        const val UNIT = 14
        /** Shadow footprint (cm): the tank interior plus 1 cm. */
        const val X0 = -1f; const val X1 = TankDims.LENGTH_F + 1f
        const val Z0 = -1f; const val Z1 = TankDims.DEPTH_F + 1f
        const val Y_BOTTOM = -1f; const val Y_TOP = TankDims.HEIGHT_F - 1f
        /** Casters whose centre is above this (fixtures, rim, gooseneck) are left out. */
        const val CASTER_MAX_Y = TankDims.WATER_LEVEL_F
        /** How dark a full shadow gets (ambient, caustic bounce and scatter still reach in). */
        const val STRENGTH = 0.85f
        /** World-space lift toward the light before the depth compare (cm). */
        const val BIAS_CM = 0.12f
        const val STATIC_REFRESH_FRAMES = 120
    }
}
