package com.reeflive.app.render

import android.opengl.GLES30
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.ShortBuffer
import java.util.concurrent.atomic.AtomicInteger

/**
 * GL helpers. [debug] is set from the app's FLAG_DEBUGGABLE by [TankGlView]; when on, [check] reads
 * `glGetError` after every GL call site and logs/counts failures (PLAN C2 acceptance: no GL errors).
 */
object Gl {
    const val TAG = "ReefGL"

    @Volatile @JvmField var debug = false
    val errors = AtomicInteger(0)

    fun check(op: String) {
        if (!debug) return
        while (true) {
            val e = GLES30.glGetError()
            if (e == GLES30.GL_NO_ERROR) return
            errors.incrementAndGet()
            Log.e(TAG, "GL error 0x${Integer.toHexString(e)} after $op")
        }
    }

    /** [check] for a call site with a variable [arg]: the label is only built when debugging (no garbage per call). */
    fun check(op: String, arg: Any?) { if (debug) check("$op($arg)") }

    /** The program bound by [ShaderProgram.use], so rebinding the same one is skipped; 0 after a context loss. */
    @JvmField var currentProgram = 0

    private val tmp = IntArray(1)

    fun genBuffer(): Int { GLES30.glGenBuffers(1, tmp, 0); check("glGenBuffers"); return tmp[0] }
    fun genVertexArray(): Int { GLES30.glGenVertexArrays(1, tmp, 0); check("glGenVertexArrays"); return tmp[0] }
    fun genTexture(): Int { GLES30.glGenTextures(1, tmp, 0); check("glGenTextures"); return tmp[0] }
    fun genFramebuffer(): Int { GLES30.glGenFramebuffers(1, tmp, 0); check("glGenFramebuffers"); return tmp[0] }
    fun genRenderbuffer(): Int { GLES30.glGenRenderbuffers(1, tmp, 0); check("glGenRenderbuffers"); return tmp[0] }

    fun deleteBuffer(id: Int) { if (id != 0) { tmp[0] = id; GLES30.glDeleteBuffers(1, tmp, 0); check("glDeleteBuffers") } }
    fun deleteVertexArray(id: Int) { if (id != 0) { tmp[0] = id; GLES30.glDeleteVertexArrays(1, tmp, 0); check("glDeleteVertexArrays") } }
    fun deleteTexture(id: Int) { if (id != 0) { tmp[0] = id; GLES30.glDeleteTextures(1, tmp, 0); check("glDeleteTextures") } }
    fun deleteFramebuffer(id: Int) { if (id != 0) { tmp[0] = id; GLES30.glDeleteFramebuffers(1, tmp, 0); check("glDeleteFramebuffers") } }
    fun deleteRenderbuffer(id: Int) { if (id != 0) { tmp[0] = id; GLES30.glDeleteRenderbuffers(1, tmp, 0); check("glDeleteRenderbuffers") } }

    fun floats(a: FloatArray, count: Int = a.size): FloatBuffer =
        ByteBuffer.allocateDirect(maxOf(count, 1) * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(a, 0, count); position(0) }

    fun shorts(a: ShortArray): ShortBuffer =
        ByteBuffer.allocateDirect(maxOf(a.size, 1) * 2).order(ByteOrder.nativeOrder()).asShortBuffer().apply { put(a); position(0) }

    fun ints(a: IntArray): IntBuffer =
        ByteBuffer.allocateDirect(maxOf(a.size, 1) * 4).order(ByteOrder.nativeOrder()).asIntBuffer().apply { put(a); position(0) }

    fun hasExtension(name: String): Boolean = GLES30.glGetString(GLES30.GL_EXTENSIONS)?.contains(name) == true

    /** Sets the common render state for a pass (keeps state changes in one place). */
    fun state(depthTest: Boolean, depthWrite: Boolean, blend: Blend, cull: Boolean) {
        if (depthTest) GLES30.glEnable(GLES30.GL_DEPTH_TEST) else GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        check("depthTest")
        GLES30.glDepthMask(depthWrite); check("glDepthMask")
        when (blend) {
            Blend.NONE -> GLES30.glDisable(GLES30.GL_BLEND)
            Blend.ALPHA -> { GLES30.glEnable(GLES30.GL_BLEND); GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA) }
            Blend.PREMULT -> { GLES30.glEnable(GLES30.GL_BLEND); GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA) }
            Blend.ADD -> { GLES30.glEnable(GLES30.GL_BLEND); GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE) }
        }
        check("blend")
        if (cull) GLES30.glEnable(GLES30.GL_CULL_FACE) else GLES30.glDisable(GLES30.GL_CULL_FACE)
        check("cull")
    }

    enum class Blend { NONE, ALPHA, PREMULT, ADD }
}
