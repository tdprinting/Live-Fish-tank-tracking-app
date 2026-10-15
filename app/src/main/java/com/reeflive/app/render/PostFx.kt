package com.reeflive.app.render

import android.opengl.GLES30

/**
 * Post-processing (ARCHITECTURE §8.4 colour management, §8.2 follow lens). The scene renders into an MSAA FBO, is
 * resolved into a texture, then:
 * - bloom ([bloom], High): bright pass and a 9-tap blur (H then V) at half resolution;
 * - follow lens (`lens` > 0, Medium and High): the resolved scene is blurred at half resolution ([lensPasses] H+V
 *   pairs, wide taps) and composite.frag blends everything outside the followed fish's focus circle toward it, with
 *   a vignette and slight chromatic aberration.
 * The composite goes to the default framebuffer. All targets are allocated in [resize] and reused every frame.
 * On Medium the renderer creates this lazily the first time the lens is needed and only routes frames through it
 * while the lens is fading in, on or out.
 */
class PostFx(source: (String) -> String, private val samples: Int, private val bloom: Boolean) {
    private val bright = if (bloom) ShaderProgram.build("bloom_bright", "fullscreen.vert", "bloom_bright.frag", emptyMap(), source) else null
    private val blur = ShaderProgram.build("bloom_blur", "fullscreen.vert", "bloom_blur.frag", emptyMap(), source)
    private val composite = ShaderProgram.build("composite", "fullscreen.vert", "composite.frag", emptyMap(), source)
    private val emptyVao = Gl.genVertexArray()

    private var w = 0; private var h = 0
    private var msFbo = 0; private var msColor = 0; private var msDepth = 0
    private var resolveFbo = 0; private var resolveTex = 0
    /** Half-resolution targets: 0 bloom result, 1 scratch, 2 lens blur result. */
    private val halfFbo = IntArray(3); private val halfTex = IntArray(3)
    var ok = false; private set

    fun resize(width: Int, height: Int) {
        if (width == w && height == h && ok) return
        freeTargets()
        w = width; h = height
        if (w <= 0 || h <= 0) return
        val maxSamples = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_MAX_SAMPLES, maxSamples, 0); Gl.check("GL_MAX_SAMPLES")
        val s = minOf(samples, maxSamples[0]).coerceAtLeast(0)
        msFbo = Gl.genFramebuffer()
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, msFbo); Gl.check("bind msFbo")
        msColor = Gl.genRenderbuffer()
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, msColor); Gl.check("bind msColor")
        GLES30.glRenderbufferStorageMultisample(GLES30.GL_RENDERBUFFER, s, GLES30.GL_RGBA8, w, h); Gl.check("msColor storage")
        GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_RENDERBUFFER, msColor); Gl.check("attach msColor")
        msDepth = Gl.genRenderbuffer()
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, msDepth); Gl.check("bind msDepth")
        GLES30.glRenderbufferStorageMultisample(GLES30.GL_RENDERBUFFER, s, GLES30.GL_DEPTH_COMPONENT24, w, h); Gl.check("msDepth storage")
        GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_RENDERBUFFER, msDepth); Gl.check("attach msDepth")
        val complete = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) == GLES30.GL_FRAMEBUFFER_COMPLETE
        Gl.check("status ms")
        val r = colorTarget(w, h); resolveFbo = r.first; resolveTex = r.second
        for (i in 0 until 3) { val t = colorTarget(maxOf(1, w / 2), maxOf(1, h / 2)); halfFbo[i] = t.first; halfTex[i] = t.second }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0); Gl.check("unbind fbo")
        ok = complete
    }

    private fun colorTarget(tw: Int, th: Int): Pair<Int, Int> {
        val fbo = Gl.genFramebuffer()
        val tex = Gl.genTexture()
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex); Gl.check("bind tex")
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, tw, th, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null); Gl.check("tex storage")
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR); Gl.check("min")
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR); Gl.check("mag")
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE); Gl.check("wrapS")
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE); Gl.check("wrapT")
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo); Gl.check("bind fbo")
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, tex, 0); Gl.check("attach tex")
        return fbo to tex
    }

    /** Redirects scene rendering into the MSAA target. */
    fun begin() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, msFbo); Gl.check("bind msFbo")
        GLES30.glViewport(0, 0, w, h); Gl.check("viewport")
    }

    /**
     * Resolves, blooms, applies the follow lens and composites to the default framebuffer. [lens] 0..1 is the lens
     * amount, [focus] (u, v, radius) its sharp zone ([LensState.focus]). Returns draw calls issued.
     */
    fun end(lens: Float = 0f, focus: FloatArray? = null, lensPasses: Int = 1): Int {
        GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, msFbo); Gl.check("read msFbo")
        GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, resolveFbo); Gl.check("draw resolve")
        GLES30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GLES30.GL_COLOR_BUFFER_BIT, GLES30.GL_NEAREST); Gl.check("blit resolve")
        // the multisampled colour and depth are done: tiled GPUs can drop them instead of writing them back to memory
        GLES30.glInvalidateFramebuffer(GLES30.GL_READ_FRAMEBUFFER, 2, MS_ATTACHMENTS, 0); Gl.check("invalidate msFbo")
        Gl.state(depthTest = false, depthWrite = false, blend = Gl.Blend.NONE, cull = false)
        GLES30.glBindVertexArray(emptyVao); Gl.check("bind empty vao")
        val hw = maxOf(1, w / 2); val hh = maxOf(1, h / 2)
        var calls = 1
        val b = bright
        if (b != null) {
            // bright pass → half[0]; blur H → half[1], V → half[0]
            pass(halfFbo[0], hw, hh, b, resolveTex)
            blur.use(); blur.set2f("uDir", 1f / hw, 0f)
            pass(halfFbo[1], hw, hh, blur, halfTex[0])
            blur.use(); blur.set2f("uDir", 0f, 1f / hh)
            pass(halfFbo[0], hw, hh, blur, halfTex[1])
            calls += 3
        }
        val lensOn = lens > 0.002f && focus != null
        if (lensOn) {
            // wide-tap separable blur of the full-res scene into half[2] (via half[1]); more pairs = softer
            var src = resolveTex
            for (k in 0 until lensPasses.coerceIn(1, 3)) {
                blur.use(); blur.set2f("uDir", LENS_STEP / hw, 0f)
                pass(halfFbo[1], hw, hh, blur, src)
                blur.use(); blur.set2f("uDir", 0f, LENS_STEP / hh)
                pass(halfFbo[2], hw, hh, blur, halfTex[1])
                src = halfTex[2]
                calls += 2
            }
        }
        // composite
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0); Gl.check("bind default")
        GLES30.glViewport(0, 0, w, h); Gl.check("viewport")
        composite.use()
        composite.set1f("uBloomStrength", if (b != null) BLOOM_STRENGTH else 0f)
        if (lensOn && focus != null) {
            composite.set4f("uLens", lens, VIGNETTE, CHROMA, w.toFloat() / h)
            composite.set3f("uFocus", focus[0], focus[1], focus[2])
        } else {
            composite.set4f("uLens", 0f, 0f, 0f, 1f)
        }
        bindTex(9, resolveTex); bindTex(10, halfTex[0]); bindTex(12, halfTex[2])
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3); Gl.check("composite draw")
        GLES30.glBindVertexArray(0); Gl.check("unbind vao")
        return calls
    }

    private fun pass(fbo: Int, pw: Int, ph: Int, p: ShaderProgram, input: Int) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo); Gl.check("bind pass fbo")
        // a full-screen pass overwrites every pixel: no need to load the old contents
        GLES30.glInvalidateFramebuffer(GLES30.GL_FRAMEBUFFER, 1, MS_ATTACHMENTS, 0); Gl.check("invalidate pass fbo")
        GLES30.glViewport(0, 0, pw, ph); Gl.check("pass viewport")
        p.use()
        bindTex(9, input)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3); Gl.check("pass draw")
    }

    private fun bindTex(unit: Int, tex: Int) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit); Gl.check("active tex")
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex); Gl.check("bind tex")
    }

    private fun freeTargets() {
        Gl.deleteFramebuffer(msFbo); Gl.deleteRenderbuffer(msColor); Gl.deleteRenderbuffer(msDepth)
        Gl.deleteFramebuffer(resolveFbo); Gl.deleteTexture(resolveTex)
        for (i in 0 until 3) { Gl.deleteFramebuffer(halfFbo[i]); Gl.deleteTexture(halfTex[i]); halfFbo[i] = 0; halfTex[i] = 0 }
        msFbo = 0; msColor = 0; msDepth = 0; resolveFbo = 0; resolveTex = 0
        ok = false
    }

    fun release() {
        freeTargets()
        bright?.release(); blur.release(); composite.release()
        Gl.deleteVertexArray(emptyVao)
    }

    companion object {
        private val MS_ATTACHMENTS = intArrayOf(GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_DEPTH_ATTACHMENT)
        const val BLOOM_STRENGTH = 0.8f
        /** Lens blur tap spacing, in half-resolution texels (9 taps → about ±16 full-res px per pass pair). */
        const val LENS_STEP = 2f
        const val VIGNETTE = 0.45f
        /** Chromatic aberration: red/blue split (uv) in the corners at full lens. */
        const val CHROMA = 0.006f
    }
}
