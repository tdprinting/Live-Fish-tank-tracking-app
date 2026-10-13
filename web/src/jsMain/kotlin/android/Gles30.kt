@file:Suppress("unused", "PackageDirectoryMismatch", "FunctionName", "UNUSED_PARAMETER")

package android.opengl

import java.nio.Buffer

/**
 * android.opengl.GLES30 on top of a WebGL2 context, so the Android renderer (app/.../render) runs in the
 * browser unchanged. GL object names are ints on Android and objects in WebGL: [Names] maps between them.
 */
object GLES30 {
    /** The WebGL2RenderingContext; set by the web host before the renderer's onSurfaceCreated. */
    var gl: dynamic = null

    const val GL_NO_ERROR = 0
    const val GL_DEPTH_BUFFER_BIT = 0x00000100
    const val GL_COLOR_BUFFER_BIT = 0x00004000
    const val GL_POINTS = 0x0000
    const val GL_TRIANGLES = 0x0004
    const val GL_ONE = 1
    const val GL_SRC_ALPHA = 0x0302
    const val GL_ONE_MINUS_SRC_ALPHA = 0x0303
    const val GL_CULL_FACE = 0x0B44
    const val GL_DEPTH_TEST = 0x0B71
    const val GL_BLEND = 0x0BE2
    const val GL_UNPACK_ALIGNMENT = 0x0CF5
    const val GL_TEXTURE_2D = 0x0DE1
    const val GL_UNSIGNED_BYTE = 0x1401
    const val GL_UNSIGNED_SHORT = 0x1403
    const val GL_UNSIGNED_INT = 0x1405
    const val GL_FLOAT = 0x1406
    const val GL_RED = 0x1903
    const val GL_RGBA = 0x1908
    const val GL_RENDERER = 0x1F01
    const val GL_VERSION = 0x1F02
    const val GL_EXTENSIONS = 0x1F03
    const val GL_NEAREST = 0x2600
    const val GL_LINEAR = 0x2601
    const val GL_LINEAR_MIPMAP_LINEAR = 0x2703
    const val GL_TEXTURE_MAG_FILTER = 0x2800
    const val GL_TEXTURE_MIN_FILTER = 0x2801
    const val GL_TEXTURE_WRAP_S = 0x2802
    const val GL_TEXTURE_WRAP_T = 0x2803
    const val GL_REPEAT = 0x2901
    const val GL_CLAMP_TO_EDGE = 0x812F
    const val GL_TEXTURE0 = 0x84C0
    const val GL_ARRAY_BUFFER = 0x8892
    const val GL_ELEMENT_ARRAY_BUFFER = 0x8893
    const val GL_STATIC_DRAW = 0x88E4
    const val GL_DYNAMIC_DRAW = 0x88E8
    const val GL_FRAGMENT_SHADER = 0x8B30
    const val GL_VERTEX_SHADER = 0x8B31
    const val GL_COMPILE_STATUS = 0x8B81
    const val GL_LINK_STATUS = 0x8B82
    const val GL_RGBA8 = 0x8058
    const val GL_R16F = 0x822D
    const val GL_RGBA16F = 0x881A
    const val GL_DEPTH_COMPONENT24 = 0x81A6
    const val GL_UNIFORM_BUFFER = 0x8A11
    const val GL_INVALID_INDEX = -1
    const val GL_READ_FRAMEBUFFER = 0x8CA8
    const val GL_DRAW_FRAMEBUFFER = 0x8CA9
    const val GL_FRAMEBUFFER_COMPLETE = 0x8CD5
    const val GL_NONE = 0
    const val GL_TEXTURE_COMPARE_MODE = 0x884C
    const val GL_TEXTURE_COMPARE_FUNC = 0x884D
    const val GL_COMPARE_REF_TO_TEXTURE = 0x884E
    const val GL_COLOR_ATTACHMENT0 = 0x8CE0
    const val GL_DEPTH_ATTACHMENT = 0x8D00
    const val GL_FRAMEBUFFER = 0x8D40
    const val GL_RENDERBUFFER = 0x8D41
    const val GL_MAX_SAMPLES = 0x8D57
    // Also commonly used by the Android renderer; declared ahead so new render code keeps compiling for the web.
    const val GL_ZERO = 0
    const val GL_LINES = 0x0001
    const val GL_LINE_STRIP = 0x0003
    const val GL_TRIANGLE_STRIP = 0x0005
    const val GL_TRIANGLE_FAN = 0x0006
    const val GL_LESS = 0x0201
    const val GL_LEQUAL = 0x0203
    const val GL_ALWAYS = 0x0207
    const val GL_SRC_COLOR = 0x0300
    const val GL_ONE_MINUS_SRC_COLOR = 0x0301
    const val GL_DST_ALPHA = 0x0304
    const val GL_ONE_MINUS_DST_ALPHA = 0x0305
    const val GL_DST_COLOR = 0x0306
    const val GL_FRONT = 0x0404
    const val GL_BACK = 0x0405
    const val GL_CW = 0x0900
    const val GL_CCW = 0x0901
    const val GL_SCISSOR_TEST = 0x0C11
    const val GL_MAX_TEXTURE_SIZE = 0x0D33
    const val GL_BYTE = 0x1400
    const val GL_SHORT = 0x1402
    const val GL_INT = 0x1404
    const val GL_HALF_FLOAT = 0x140B
    const val GL_DEPTH_COMPONENT = 0x1902
    const val GL_RGB = 0x1907
    const val GL_FUNC_ADD = 0x8006
    const val GL_POLYGON_OFFSET_FILL = 0x8037
    const val GL_RGB8 = 0x8051
    const val GL_DEPTH_COMPONENT16 = 0x81A5
    const val GL_RG = 0x8227
    const val GL_R8 = 0x8229
    const val GL_R32F = 0x822E
    const val GL_RG16F = 0x822F
    const val GL_MIRRORED_REPEAT = 0x8370
    const val GL_RGBA32F = 0x8814
    const val GL_STREAM_DRAW = 0x88E0
    const val GL_NEAREST_MIPMAP_NEAREST = 0x2700
    const val GL_LINEAR_MIPMAP_NEAREST = 0x2701

    private object Names {
        private var next = 1
        private val objs = HashMap<Int, dynamic>()
        fun add(o: dynamic): Int { if (o == null) return 0; val id = next++; objs[id] = o; return id }
        fun get(id: Int): dynamic = if (id == 0) null else objs[id]
        fun remove(id: Int): dynamic = objs.remove(id)
    }

    private fun gen(n: Int, out: IntArray, off: Int, make: () -> dynamic) { for (i in 0 until n) out[off + i] = Names.add(make()) }
    private fun del(n: Int, ids: IntArray, off: Int, kill: (dynamic) -> Unit) {
        for (i in 0 until n) { val o = Names.remove(ids[off + i]); if (o != null) kill(o) }
    }
    /** Typed-array view of a java.nio buffer from its position (WebGL reads the whole view). */
    private fun data(b: Buffer?, bytes: Int = -1): dynamic = b?.view(bytes)

    /** WebGL wants a Uint8Array for UNSIGNED_BYTE pixels; byte buffers are Int8Array views. */
    private fun pixels(b: Buffer?, type: Int): dynamic {
        val v = data(b) ?: return null
        return when (type) {
            GL_UNSIGNED_BYTE -> org.khronos.webgl.Uint8Array(v.buffer, v.byteOffset, v.byteLength)
            GL_UNSIGNED_INT -> org.khronos.webgl.Uint32Array(v.buffer, v.byteOffset, v.byteLength / 4)
            else -> v
        }
    }

    // ---- state
    fun glEnable(cap: Int) = gl.enable(cap)
    fun glDisable(cap: Int) = gl.disable(cap)
    fun glBlendFunc(s: Int, d: Int) = gl.blendFunc(s, d)
    fun glDepthMask(f: Boolean) = gl.depthMask(f)
    fun glDepthFunc(f: Int) = gl.depthFunc(f)
    fun glCullFace(m: Int) = gl.cullFace(m)
    fun glFrontFace(m: Int) = gl.frontFace(m)
    fun glPolygonOffset(factor: Float, units: Float) = gl.polygonOffset(factor, units)
    fun glBlendFuncSeparate(sr: Int, dr: Int, sa: Int, da: Int) = gl.blendFuncSeparate(sr, dr, sa, da)
    fun glBlendEquation(m: Int) = gl.blendEquation(m)
    fun glColorMask(r: Boolean, g: Boolean, b: Boolean, a: Boolean) = gl.colorMask(r, g, b, a)
    fun glScissor(x: Int, y: Int, w: Int, h: Int) = gl.scissor(x, y, w, h)
    fun glLineWidth(w: Float) = gl.lineWidth(w)
    fun glViewport(x: Int, y: Int, w: Int, h: Int) = gl.viewport(x, y, w, h)
    fun glClearColor(r: Float, g: Float, b: Float, a: Float) = gl.clearColor(r, g, b, a)
    fun glClear(mask: Int) = gl.clear(mask)
    fun glPixelStorei(p: Int, v: Int) = gl.pixelStorei(p, v)
    fun glGetError(): Int = gl.getError() as Int
    fun glGetString(name: Int): String? = when (name) {
        GL_EXTENSIONS -> (gl.getSupportedExtensions() as Array<String>?)?.joinToString(" ") { "GL_$it" }
        else -> gl.getParameter(name) as String?
    }
    fun glGetIntegerv(p: Int, out: IntArray, off: Int) { out[off] = (gl.getParameter(p) as Number?)?.toInt() ?: 0 }
    fun glGetFloatv(p: Int, out: FloatArray, off: Int) {
        if (p == 0x84FF) { val ext = gl.getExtension("EXT_texture_filter_anisotropic"); out[off] = if (ext == null) 1f else (gl.getParameter(p) as Number).toFloat(); return }
        out[off] = (gl.getParameter(p) as Number?)?.toFloat() ?: 0f
    }

    // ---- buffers / VAOs
    fun glGenBuffers(n: Int, out: IntArray, off: Int) = gen(n, out, off) { gl.createBuffer() }
    fun glDeleteBuffers(n: Int, ids: IntArray, off: Int) = del(n, ids, off) { gl.deleteBuffer(it) }
    fun glBindBuffer(t: Int, id: Int) = gl.bindBuffer(t, Names.get(id))
    fun glBindBufferBase(t: Int, index: Int, id: Int) = gl.bindBufferBase(t, index, Names.get(id))
    fun glBufferData(t: Int, size: Int, b: Buffer?, usage: Int) {
        if (b == null) gl.bufferData(t, size, usage) else gl.bufferData(t, data(b, size), usage)
    }
    fun glBufferSubData(t: Int, offset: Int, size: Int, b: Buffer) = gl.bufferSubData(t, offset, data(b, size))
    fun glGenVertexArrays(n: Int, out: IntArray, off: Int) = gen(n, out, off) { gl.createVertexArray() }
    fun glDeleteVertexArrays(n: Int, ids: IntArray, off: Int) = del(n, ids, off) { gl.deleteVertexArray(it) }
    fun glBindVertexArray(id: Int) = gl.bindVertexArray(Names.get(id))
    fun glEnableVertexAttribArray(i: Int) = gl.enableVertexAttribArray(i)
    fun glVertexAttribPointer(i: Int, size: Int, type: Int, norm: Boolean, stride: Int, offset: Int) = gl.vertexAttribPointer(i, size, type, norm, stride, offset)
    fun glVertexAttribDivisor(i: Int, d: Int) = gl.vertexAttribDivisor(i, d)
    fun glVertexAttrib4f(i: Int, x: Float, y: Float, z: Float, w: Float) = gl.vertexAttrib4f(i, x, y, z, w)
    fun glDrawArrays(mode: Int, first: Int, count: Int) = gl.drawArrays(mode, first, count)
    fun glDrawArraysInstanced(mode: Int, first: Int, count: Int, instances: Int) = gl.drawArraysInstanced(mode, first, count, instances)
    fun glDisableVertexAttribArray(i: Int) = gl.disableVertexAttribArray(i)
    fun glDrawElements(mode: Int, count: Int, type: Int, offset: Int) = gl.drawElements(mode, count, type, offset)
    fun glDrawElementsInstanced(mode: Int, count: Int, type: Int, offset: Int, instances: Int) = gl.drawElementsInstanced(mode, count, type, offset, instances)

    // ---- textures
    fun glGenTextures(n: Int, out: IntArray, off: Int) = gen(n, out, off) { gl.createTexture() }
    fun glDeleteTextures(n: Int, ids: IntArray, off: Int) = del(n, ids, off) { gl.deleteTexture(it) }
    fun glBindTexture(t: Int, id: Int) { boundTexture = id; gl.bindTexture(t, Names.get(id)) }
    fun glActiveTexture(unit: Int) = gl.activeTexture(unit)
    fun glTexParameteri(t: Int, p: Int, v: Int) = gl.texParameteri(t, p, v)
    fun glTexParameterf(t: Int, p: Int, v: Float) {
        if (p == 0x84FE && gl.getExtension("EXT_texture_filter_anisotropic") == null) return
        gl.texParameterf(t, p, v)
    }
    fun glGenerateMipmap(t: Int) = gl.generateMipmap(t)
    fun glTexImage2D(t: Int, level: Int, internal: Int, w: Int, h: Int, border: Int, format: Int, type: Int, b: Buffer?) {
        gl.texImage2D(t, level, internal, w, h, border, format, type, pixels(b, type))
    }
    fun glTexSubImage2D(t: Int, level: Int, x: Int, y: Int, w: Int, h: Int, format: Int, type: Int, b: Buffer) =
        gl.texSubImage2D(t, level, x, y, w, h, format, type, pixels(b, type))
    /** Last texture bound with [glBindTexture] (GLUtils uploads decoded images into it later). */
    var boundTexture = 0; private set
    internal fun texture(id: Int): dynamic = Names.get(id)

    // ---- framebuffers
    fun glGenFramebuffers(n: Int, out: IntArray, off: Int) = gen(n, out, off) { gl.createFramebuffer() }
    fun glDeleteFramebuffers(n: Int, ids: IntArray, off: Int) = del(n, ids, off) { gl.deleteFramebuffer(it) }
    fun glBindFramebuffer(t: Int, id: Int) = gl.bindFramebuffer(t, Names.get(id))
    fun glDrawBuffers(n: Int, bufs: IntArray, off: Int) = gl.drawBuffers(Array(n) { bufs[off + it] })
    fun glReadBuffer(mode: Int) = gl.readBuffer(mode)
    fun glFramebufferTexture2D(t: Int, att: Int, tt: Int, tex: Int, level: Int) = gl.framebufferTexture2D(t, att, tt, Names.get(tex), level)
    fun glFramebufferRenderbuffer(t: Int, att: Int, rt: Int, rb: Int) = gl.framebufferRenderbuffer(t, att, rt, Names.get(rb))
    fun glCheckFramebufferStatus(t: Int): Int = gl.checkFramebufferStatus(t) as Int
    fun glGenRenderbuffers(n: Int, out: IntArray, off: Int) = gen(n, out, off) { gl.createRenderbuffer() }
    fun glDeleteRenderbuffers(n: Int, ids: IntArray, off: Int) = del(n, ids, off) { gl.deleteRenderbuffer(it) }
    fun glBindRenderbuffer(t: Int, id: Int) = gl.bindRenderbuffer(t, Names.get(id))
    fun glRenderbufferStorageMultisample(t: Int, samples: Int, fmt: Int, w: Int, h: Int) = gl.renderbufferStorageMultisample(t, samples, fmt, w, h)
    fun glBlitFramebuffer(sx0: Int, sy0: Int, sx1: Int, sy1: Int, dx0: Int, dy0: Int, dx1: Int, dy1: Int, mask: Int, filter: Int) =
        gl.blitFramebuffer(sx0, sy0, sx1, sy1, dx0, dy0, dx1, dy1, mask, filter)

    // ---- shaders / programs
    fun glCreateShader(type: Int): Int = Names.add(gl.createShader(type))
    fun glShaderSource(s: Int, src: String) = gl.shaderSource(Names.get(s), src)
    fun glCompileShader(s: Int) = gl.compileShader(Names.get(s))
    fun glGetShaderiv(s: Int, p: Int, out: IntArray, off: Int) { out[off] = if (gl.getShaderParameter(Names.get(s), p) as Boolean) 1 else 0 }
    fun glGetShaderInfoLog(s: Int): String = (gl.getShaderInfoLog(Names.get(s)) as String?) ?: ""
    fun glDeleteShader(s: Int) { val o = Names.remove(s); if (o != null) gl.deleteShader(o) }
    fun glCreateProgram(): Int = Names.add(gl.createProgram())
    fun glAttachShader(p: Int, s: Int) = gl.attachShader(Names.get(p), Names.get(s))
    fun glLinkProgram(p: Int) = gl.linkProgram(Names.get(p))
    fun glGetProgramiv(p: Int, pname: Int, out: IntArray, off: Int) { out[off] = if (gl.getProgramParameter(Names.get(p), pname) as Boolean) 1 else 0 }
    fun glGetProgramInfoLog(p: Int): String = (gl.getProgramInfoLog(Names.get(p)) as String?) ?: ""
    fun glDeleteProgram(p: Int) { val o = Names.remove(p); if (o != null) gl.deleteProgram(o) }
    fun glUseProgram(p: Int) = gl.useProgram(Names.get(p))
    fun glGetUniformLocation(p: Int, name: String): Int {
        val l = gl.getUniformLocation(Names.get(p), name)
        return if (l == null) -1 else Names.add(l)
    }
    fun glGetUniformBlockIndex(p: Int, name: String): Int = (gl.getUniformBlockIndex(Names.get(p), name) as Number).toInt().let { if (it == 0xFFFFFFFF.toInt() || it >= 0x7FFFFFFF) -1 else it }
    fun glUniformBlockBinding(p: Int, index: Int, binding: Int) = gl.uniformBlockBinding(Names.get(p), index, binding)

    // ---- uniforms
    private fun u(l: Int): dynamic = Names.get(l)
    fun glUniform1i(l: Int, v: Int) = gl.uniform1i(u(l), v)
    fun glUniform1f(l: Int, v: Float) = gl.uniform1f(u(l), v)
    fun glUniform2f(l: Int, a: Float, b: Float) = gl.uniform2f(u(l), a, b)
    fun glUniform3f(l: Int, a: Float, b: Float, c: Float) = gl.uniform3f(u(l), a, b, c)
    fun glUniform4f(l: Int, a: Float, b: Float, c: Float, d: Float) = gl.uniform4f(u(l), a, b, c, d)
    fun glUniform2i(l: Int, a: Int, b: Int) = gl.uniform2i(u(l), a, b)
    fun glUniform1fv(l: Int, count: Int, v: FloatArray, off: Int) = gl.uniform1fv(u(l), v, off, count)
    fun glUniform2fv(l: Int, count: Int, v: FloatArray, off: Int) = gl.uniform2fv(u(l), v, off, count * 2)
    fun glUniformMatrix3fv(l: Int, count: Int, transpose: Boolean, v: FloatArray, off: Int) = gl.uniformMatrix3fv(u(l), transpose, v, off, count * 9)
    fun glUniform3fv(l: Int, count: Int, v: FloatArray, off: Int) = gl.uniform3fv(u(l), v, off, count * 3)
    fun glUniform4fv(l: Int, count: Int, v: FloatArray, off: Int) = gl.uniform4fv(u(l), v, off, count * 4)
    fun glUniformMatrix4fv(l: Int, count: Int, transpose: Boolean, v: FloatArray, off: Int) = gl.uniformMatrix4fv(u(l), transpose, v, off, count * 16)
}

object GLUtils {
    /** Uploads [bmp] into the bound texture; a browser-decoded image arrives later and replaces the 1×1 stand-in. */
    fun texImage2D(target: Int, level: Int, bmp: android.graphics.Bitmap, border: Int) = bmp.uploadInto(GLES30.boundTexture)
}

class GLSurfaceView {
    interface Renderer {
        fun onSurfaceCreated(gl: javax.microedition.khronos.opengles.GL10?, config: javax.microedition.khronos.egl.EGLConfig?)
        fun onSurfaceChanged(gl: javax.microedition.khronos.opengles.GL10?, width: Int, height: Int)
        fun onDrawFrame(gl: javax.microedition.khronos.opengles.GL10?)
    }
}
