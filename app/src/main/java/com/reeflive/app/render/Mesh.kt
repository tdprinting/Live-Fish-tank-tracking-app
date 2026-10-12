package com.reeflive.app.render

import android.opengl.GLES30

/**
 * VAO + interleaved VBO (pos3 nrm3 uv2 col4; `aSpine` = COLOR_0.a, ARCHITECTURE §8.2) + IBO, with an
 * optional per-instance buffer for polyps (attributes 4..6: posScale vec4, quat vec4, phaseVariant vec2).
 */
class Mesh private constructor(
    private val vao: Int,
    private val vbo: Int,
    private val ibo: Int,
    private var instanceVbo: Int,
    val indexCount: Int,
    private val indexType: Int,
    var instanceCount: Int,
) {
    val triangles: Int get() = indexCount / 3 * maxOf(instanceCount, 1)

    fun draw() {
        GLES30.glBindVertexArray(vao); Gl.check("glBindVertexArray")
        if (instanceCount > 0) {
            GLES30.glDrawElementsInstanced(GLES30.GL_TRIANGLES, indexCount, indexType, 0, instanceCount)
            Gl.check("glDrawElementsInstanced")
        } else {
            GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount, indexType, 0)
            Gl.check("glDrawElements")
        }
        GLES30.glBindVertexArray(0); Gl.check("glBindVertexArray(0)")
    }

    fun release() {
        Gl.deleteVertexArray(vao); Gl.deleteBuffer(vbo); Gl.deleteBuffer(ibo); Gl.deleteBuffer(instanceVbo)
        instanceVbo = 0
    }

    companion object {
        const val INSTANCE_FLOATS = 10 // pos3 scale1 quat4 phase1 variant1 (buffer layout, not glTF order)

        fun from(p: GlbPrimitive, instances: FloatArray? = null): Mesh = create(p.vertices, p.indices, instances)

        /**
         * [vertices]: STRIDE-12 interleaved floats; [instances]: [INSTANCE_FLOATS] per instance in buffer layout
         * (pos.xyz, scale, quat.xyzw, phase, variant).
         */
        fun create(vertices: FloatArray, indices: IntArray, instances: FloatArray? = null): Mesh {
            val vao = Gl.genVertexArray()
            GLES30.glBindVertexArray(vao); Gl.check("glBindVertexArray")
            val vbo = Gl.genBuffer()
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo); Gl.check("glBindBuffer(vbo)")
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertices.size * 4, Gl.floats(vertices), GLES30.GL_STATIC_DRAW); Gl.check("glBufferData(vbo)")
            val stride = GlbPrimitive.STRIDE * 4
            attrib(0, 3, stride, 0); attrib(1, 3, stride, 12); attrib(2, 2, stride, 24); attrib(3, 4, stride, 32)

            val vertexCount = vertices.size / GlbPrimitive.STRIDE
            val ibo = Gl.genBuffer()
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo); Gl.check("glBindBuffer(ibo)")
            val type: Int
            if (vertexCount <= 65535) {
                val s = ShortArray(indices.size) { indices[it].toShort() }
                GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, s.size * 2, Gl.shorts(s), GLES30.GL_STATIC_DRAW)
                type = GLES30.GL_UNSIGNED_SHORT
            } else {
                GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, indices.size * 4, Gl.ints(indices), GLES30.GL_STATIC_DRAW)
                type = GLES30.GL_UNSIGNED_INT
            }
            Gl.check("glBufferData(ibo)")

            var ivbo = 0
            var count = 0
            if (instances != null && instances.size >= INSTANCE_FLOATS) {
                count = instances.size / INSTANCE_FLOATS
                ivbo = Gl.genBuffer()
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, ivbo); Gl.check("glBindBuffer(instances)")
                GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, count * INSTANCE_FLOATS * 4, Gl.floats(instances, count * INSTANCE_FLOATS), GLES30.GL_STATIC_DRAW)
                Gl.check("glBufferData(instances)")
                val istride = INSTANCE_FLOATS * 4
                attrib(4, 4, istride, 0, divisor = 1)
                attrib(5, 4, istride, 16, divisor = 1)
                attrib(6, 2, istride, 32, divisor = 1)
            }
            GLES30.glBindVertexArray(0); Gl.check("glBindVertexArray(0)")
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0); Gl.check("glBindBuffer(0)")
            return Mesh(vao, vbo, ibo, ivbo, indices.size, type, count)
        }

        private fun attrib(loc: Int, size: Int, stride: Int, offset: Int, divisor: Int = 0) {
            GLES30.glEnableVertexAttribArray(loc); Gl.check("glEnableVertexAttribArray($loc)")
            GLES30.glVertexAttribPointer(loc, size, GLES30.GL_FLOAT, false, stride, offset); Gl.check("glVertexAttribPointer($loc)")
            if (divisor != 0) { GLES30.glVertexAttribDivisor(loc, divisor); Gl.check("glVertexAttribDivisor($loc)") }
        }

        /**
         * Converts a glTF polyp table (stride ≥ 10: pos3, quat4 xyzw, scale, phase, variant) to the buffer
         * layout, keeping a [density] fraction of the instances (evenly strided, so a halved mat stays even).
         */
        fun polypInstances(table: FloatArray, stride: Int, density: Float): FloatArray {
            val n = table.size / stride
            val keepEvery = if (density >= 0.999f) 1 else maxOf(1, Math.round(1f / density.coerceAtLeast(0.05f)))
            val kept = (0 until n).filter { it % keepEvery == 0 }
            val out = FloatArray(kept.size * INSTANCE_FLOATS)
            kept.forEachIndexed { k, i ->
                val s = i * stride; val o = k * INSTANCE_FLOATS
                out[o] = table[s]; out[o + 1] = table[s + 1]; out[o + 2] = table[s + 2]
                out[o + 3] = if (stride > 7) table[s + 7] else 1f
                out[o + 4] = table[s + 3]; out[o + 5] = table[s + 4]; out[o + 6] = table[s + 5]; out[o + 7] = table[s + 6]
                out[o + 8] = if (stride > 8) table[s + 8] else 0f
                out[o + 9] = if (stride > 9) table[s + 9] else 0.5f
            }
            return out
        }

        /** One identity instance (a polyp-part node without a table, e.g. the feather duster crown). */
        val SINGLE_INSTANCE = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0.5f)
    }
}
