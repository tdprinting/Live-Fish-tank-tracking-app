package com.reeflive.app.render

import android.opengl.GLES30
import android.util.Log

/**
 * GLSL ES 3.00 program compiled at runtime from `assets/shaders/` (ARCHITECTURE §8.2). Supports
 * `#include "file"` (resolved once, recursively) and `#define` injection right after `#version`.
 * Uniform locations are cached by name; the `Frame` UBO is bound to [FRAME_BINDING] and the sampler
 * names in [SAMPLER_UNITS] are assigned fixed texture units.
 */
class ShaderProgram private constructor(val id: Int, val name: String) {
    /** Per uniform: its location and the last scalar/vector value sent (uniform state lives in the program, so an
     *  unchanged value is skipped). One map, so each set costs a single lookup (string hashing is not cached in JS). */
    private class Slot(val name: String, val loc: Int) { val v = FloatArray(4); var valid = false; var next: Slot? = null }
    private val slots = HashMap<String, Slot>()
    // Draws set the same uniforms in the same order every frame, so each lookup first tries the slot that followed
    // the previous one last time (compared by reference: uniform names are literals); the hash map
    // (String.hashCode is uncached on JS) is only the fallback, which relinks the sequence.
    private var prev: Slot? = null

    fun use() {
        if (Gl.currentProgram == id) return
        GLES30.glUseProgram(id); Gl.check("glUseProgram", name)
        Gl.currentProgram = id
    }

    private fun slot(u: String): Slot {
        val p = prev
        val c = p?.next
        if (c != null && c.name === u) { prev = c; return c }
        val s = slots.getOrPut(u) { Slot(u, GLES30.glGetUniformLocation(id, u).also { Gl.check("glGetUniformLocation") }) }
        p?.next = s
        prev = s
        return s
    }

    fun loc(uniform: String): Int = slot(uniform).loc

    /** The uniform's slot when it exists and does not already hold (x, y, z, w) (which are then recorded), else null. */
    private fun changed(u: String, x: Float, y: Float, z: Float, w: Float): Slot? {
        val s = slot(u)
        if (s.loc < 0) return null
        val v = s.v
        if (s.valid && v[0] == x && v[1] == y && v[2] == z && v[3] == w) return null
        v[0] = x; v[1] = y; v[2] = z; v[3] = w; s.valid = true
        return s
    }

    /** The slot for an array/matrix upload (never deduplicated; forgets the cached scalar value), or null. */
    private fun raw(u: String): Slot? { val s = slot(u); if (s.loc < 0) return null; s.valid = false; return s }

    fun set1f(u: String, v: Float) { val s = changed(u, v, 0f, 0f, 0f) ?: return; GLES30.glUniform1f(s.loc, v); Gl.check("glUniform1f", u) }
    fun set1i(u: String, v: Int) { val s = changed(u, v.toFloat(), 0f, 0f, 0f) ?: return; GLES30.glUniform1i(s.loc, v); Gl.check("glUniform1i", u) }
    fun set2f(u: String, x: Float, y: Float) { val s = changed(u, x, y, 0f, 0f) ?: return; GLES30.glUniform2f(s.loc, x, y); Gl.check("glUniform2f", u) }
    fun set3f(u: String, x: Float, y: Float, z: Float) { val s = changed(u, x, y, z, 0f) ?: return; GLES30.glUniform3f(s.loc, x, y, z); Gl.check("glUniform3f", u) }
    fun set4f(u: String, x: Float, y: Float, z: Float, w: Float) { val s = changed(u, x, y, z, w) ?: return; GLES30.glUniform4f(s.loc, x, y, z, w); Gl.check("glUniform4f", u) }
    fun set3fv(u: String, v: FloatArray) { val s = raw(u) ?: return; GLES30.glUniform3fv(s.loc, 1, v, 0); Gl.check("glUniform3fv", u) }
    fun set4fv(u: String, v: FloatArray, count: Int = v.size / 4) { val s = raw(u) ?: return; GLES30.glUniform4fv(s.loc, count, v, 0); Gl.check("glUniform4fv", u) }
    fun setMat4(u: String, m: FloatArray) { val s = raw(u) ?: return; GLES30.glUniformMatrix4fv(s.loc, 1, false, m, 0); Gl.check("glUniformMatrix4fv", u) }

    fun release() {
        if (Gl.currentProgram == id) Gl.currentProgram = 0
        GLES30.glDeleteProgram(id); Gl.check("glDeleteProgram")
    }

    companion object {
        const val FRAME_BINDING = 0

        /** Fixed sampler → texture unit assignment shared by every program. */
        val SAMPLER_UNITS = linkedMapOf(
            "uAlbedo" to 0, "uNormalMap" to 1, "uRoughAo" to 2, "uCaustic" to 3, "uSurface" to 4,
            "uIridLut" to 5, "uEnv" to 6, "uWaterN1" to 7, "uWaterN2" to 8, "uScene" to 9, "uBloom" to 10,
            "uGlassMask" to 11, "uDof" to 12, "uSandMap" to SandFx.UNIT, "uShadowMap" to ShadowMap.UNIT,
        )

        /**
         * Builds a program. [source] returns a shader file's text by name (from assets); [defines] are injected
         * as `#define NAME VALUE` lines.
         */
        fun build(name: String, vert: String, frag: String, defines: Map<String, String>, source: (String) -> String): ShaderProgram {
            val vs = compile(GLES30.GL_VERTEX_SHADER, "$name:$vert", preprocess(source(vert), defines, source))
            val fs = compile(GLES30.GL_FRAGMENT_SHADER, "$name:$frag", preprocess(source(frag), defines, source))
            val p = GLES30.glCreateProgram(); Gl.check("glCreateProgram")
            GLES30.glAttachShader(p, vs); Gl.check("glAttachShader(vs)")
            GLES30.glAttachShader(p, fs); Gl.check("glAttachShader(fs)")
            GLES30.glLinkProgram(p); Gl.check("glLinkProgram")
            val ok = IntArray(1)
            GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, ok, 0); Gl.check("glGetProgramiv")
            GLES30.glDeleteShader(vs); Gl.check("glDeleteShader")
            GLES30.glDeleteShader(fs); Gl.check("glDeleteShader")
            if (ok[0] == 0) {
                val log = GLES30.glGetProgramInfoLog(p)
                GLES30.glDeleteProgram(p)
                throw IllegalStateException("link $name failed: $log")
            }
            val prog = ShaderProgram(p, name)
            prog.use()
            val block = GLES30.glGetUniformBlockIndex(p, "Frame"); Gl.check("glGetUniformBlockIndex")
            if (block != GLES30.GL_INVALID_INDEX) { GLES30.glUniformBlockBinding(p, block, FRAME_BINDING); Gl.check("glUniformBlockBinding") }
            for ((s, unit) in SAMPLER_UNITS) prog.set1i(s, unit)
            return prog
        }

        /** Resolves includes and injects defines (pure; unit-testable). */
        fun preprocess(src: String, defines: Map<String, String>, source: (String) -> String): String = preprocessAt(src, defines, source, 0)

        private fun preprocessAt(src: String, defines: Map<String, String>, source: (String) -> String, depth: Int): String {
            require(depth < 8) { "include depth" }
            val sb = StringBuilder()
            var versionSeen = false
            for (line in src.lines()) {
                val t = line.trim()
                when {
                    depth == 0 && t.startsWith("#version") -> {
                        sb.append(line).append('\n')
                        for ((k, v) in defines) sb.append("#define ").append(k).append(' ').append(v).append('\n')
                        versionSeen = true
                    }
                    t.startsWith("#include") -> {
                        val file = t.substringAfter('"').substringBefore('"')
                        sb.append(preprocessAt(source(file), emptyMap(), source, depth + 1)).append('\n')
                    }
                    else -> sb.append(line).append('\n')
                }
            }
            if (depth == 0 && !versionSeen) throw IllegalArgumentException("shader without #version")
            return sb.toString()
        }

        private fun compile(type: Int, label: String, src: String): Int {
            val s = GLES30.glCreateShader(type); Gl.check("glCreateShader")
            GLES30.glShaderSource(s, src); Gl.check("glShaderSource")
            GLES30.glCompileShader(s); Gl.check("glCompileShader")
            val ok = IntArray(1)
            GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, ok, 0); Gl.check("glGetShaderiv")
            if (ok[0] == 0) {
                val log = GLES30.glGetShaderInfoLog(s)
                GLES30.glDeleteShader(s)
                Log.e(Gl.TAG, "compile $label failed:\n$log")
                throw IllegalStateException("compile $label failed: $log")
            }
            return s
        }
    }
}
