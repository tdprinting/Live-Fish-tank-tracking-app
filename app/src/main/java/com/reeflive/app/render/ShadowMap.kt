package com.reeflive.app.render

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
 * On High the static casters (rock, equipment, still reef details) go to a second depth target only when the scene
 * changes or the periodic refresh is due ([staticStale]); each frame [begin] copies that depth in and only the moving
 * casters (corals, fish, the magnet cleaner) are drawn again. The result is the same map at a fraction of the vertices.
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
    /**
     * Auto quality relief ([TankRenderer.relief]): [lite] leaves the fish out and refreshes like Medium (static casters
     * and corals, on scene changes and every [STATIC_REFRESH_FRAMES] frames); [off] skips the pass and tells the shaders
     * there is no map (no PCF taps per pixel). Both change at runtime, with nothing to reallocate.
     */
    var lite = false
    var off = false
        set(v) { if (field && !v) signature = Long.MIN_VALUE; field = v }
    /** Fish go into the map this pass. */
    val withFish: Boolean get() = dynamic && !lite

    /** World → shadow clip (column-major), orthographic; depth grows downward from the top of the glass. */
    val matrix = FloatArray(16)
    /** cm per shadow texel along x (the PCF kernel is sized in cm). */
    var cmPerTexel: Float = 1f; private set
    private val fitted = FloatArray(3)

    init { fit() }

    /**
     * Footprint over the tank on screen ([Camera.TANK]: the interior plus 1 cm); a My tank of another size refits it.
     * Returns true when it changed (the cached static layer must be redrawn).
     */
    fun fit(): Boolean {
        val t = Camera.TANK
        if (t[0] == fitted[0] && t[1] == fitted[1] && t[2] == fitted[2]) return false
        t.copyInto(fitted)
        val x1 = t[0] + 1f; val z1 = t[2] + 1f; val yTop = t[1] - 1f
        val m = matrix
        m.fill(0f)
        m[0] = 2f / (x1 - X0); m[12] = -2f * X0 / (x1 - X0) - 1f
        m[9] = 2f / (z1 - Z0); m[13] = -2f * Z0 / (z1 - Z0) - 1f
        m[6] = -2f / (yTop - Y_BOTTOM); m[14] = 2f * yTop / (yTop - Y_BOTTOM) - 1f
        m[15] = 1f
        cmPerTexel = if (width > 0) (x1 - X0) / width else 1f
        signature = Long.MIN_VALUE; staticSignature = Long.MIN_VALUE
        return true
    }

    private val tex: Int = Gl.genTexture()
    private val fbo: Int = initTarget(tex)
    /** High only: the static casters' depth, copied into [fbo] each frame (0 when unavailable: drawn every frame). */
    private val staticTex: Int = if (dynamic && fbo != 0) Gl.genTexture() else 0
    private val staticFbo: Int = if (staticTex != 0) initTarget(staticTex) else 0
    private var signature = Long.MIN_VALUE
    private var framesSince = 0
    private var staticSignature = Long.MIN_VALUE
    private var staticFrames = 0

    /** Allocates depth storage for [t] and, when the map is enabled, a depth-only FBO on it (0 if incomplete). */
    private fun initTarget(t: Int): Int {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t); Gl.check("bind shadow tex")
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
        if (!enabled) return 0
        val f = Gl.genFramebuffer()
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, f); Gl.check("bind shadow fbo")
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_TEXTURE_2D, t, 0); Gl.check("attach shadow depth")
        GLES30.glDrawBuffers(1, intArrayOf(GLES30.GL_NONE), 0); Gl.check("shadow drawBuffers")
        GLES30.glReadBuffer(GLES30.GL_NONE); Gl.check("shadow readBuffer")
        val ok = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) == GLES30.GL_FRAMEBUFFER_COMPLETE
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0); Gl.check("unbind shadow fbo")
        return if (ok) f else { Gl.deleteFramebuffer(f); 0 }
    }

    /** True when the map holds valid depth (and shaders should sample it). */
    var valid = false; private set

    /**
     * Whether to re-render this frame: always on High; on Medium when [sceneSignature] changed or the periodic refresh
     * is due. No pass on Low or when the FBO failed.
     */
    fun needsUpdate(sceneSignature: Long): Boolean {
        if (!enabled || fbo == 0 || off) return false
        framesSince++
        if (withFish || sceneSignature != signature || framesSince >= STATIC_REFRESH_FRAMES) {
            signature = sceneSignature; framesSince = 0
            return true
        }
        return false
    }

    /** True when the static casters are cached in their own target ([staticStale], [beginStatic]). */
    val cachesStatic: Boolean get() = staticFbo != 0

    /** High: whether the cached static layer must be redrawn (scene changed, never drawn, or periodic refresh). */
    fun staticStale(sceneSignature: Long): Boolean {
        if (sceneSignature != staticSignature || ++staticFrames >= STATIC_REFRESH_FRAMES) {
            staticSignature = sceneSignature; staticFrames = 0
            return true
        }
        return false
    }

    /** Binds and clears the static target; draw the static casters next, then call [begin]. */
    fun beginStatic() = bindTarget(staticFbo, clear = true)

    /**
     * Binds the depth target and sets depth-only state: cleared, or on High the cached static layer copied in. Draw the
     * (remaining) casters, then call [end].
     */
    fun begin() {
        if (staticFbo == 0) { bindTarget(fbo, clear = true); return }
        GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, staticFbo); Gl.check("read static shadow")
        GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, fbo); Gl.check("draw shadow")
        GLES30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GLES30.GL_DEPTH_BUFFER_BIT, GLES30.GL_NEAREST)
        Gl.check("blit static shadow")
        bindTarget(fbo, clear = false)
    }

    private fun bindTarget(target: Int, clear: Boolean) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, target); Gl.check("bind shadow fbo")
        GLES30.glViewport(0, 0, width, height); Gl.check("shadow viewport")
        Gl.state(depthTest = true, depthWrite = true, blend = Gl.Blend.NONE, cull = false)
        GLES30.glColorMask(false, false, false, false); Gl.check("shadow colorMask")
        if (clear) { GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT); Gl.check("shadow clear") }
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
        d[offset + 16] = if (valid && !off) 1f else 0f
        d[offset + 17] = cmPerTexel
        d[offset + 18] = STRENGTH
        d[offset + 19] = BIAS_CM
    }

    fun release() {
        Gl.deleteFramebuffer(fbo)
        Gl.deleteTexture(tex)
        Gl.deleteFramebuffer(staticFbo)
        Gl.deleteTexture(staticTex)
    }

    companion object {
        /** Texture unit of `uShadowMap` ([ShaderProgram.SAMPLER_UNITS]). */
        const val UNIT = 14
        /** Shadow footprint (cm): the tank interior plus 1 cm (the far sides follow the tank, [fit]). */
        const val X0 = -1f
        const val Z0 = -1f
        const val Y_BOTTOM = -1f
        /** Casters whose centre is above this (fixtures, rim, gooseneck) are left out. */
        val CASTER_MAX_Y: Float get() = Camera.WATER_Y
        /** How dark a full shadow gets (ambient, caustic bounce and scatter still reach in). */
        const val STRENGTH = 0.85f
        /** World-space lift toward the light before the depth compare (cm). */
        const val BIAS_CM = 0.12f
        const val STATIC_REFRESH_FRAMES = 120
    }
}
