package com.reeflive.app.render

import android.opengl.GLES30
import kotlin.math.sqrt

/**
 * Squid ink puffs (SIMULATION §20, core SquidRig). A startled bobtail squid leaves a dark pseudomorph about its own
 * size and jets away from it. The sim sends the cloud in the squid's limb pose (motion mode 4): `limbs[13..15]` the
 * cloud centre (world cm), `limbs[17]` its age (s), `limbs[18]` its radius (cm), `limbs[19]` its density 0..1 (0 =
 * no cloud). Drawn as soft GL_POINTS with the particles program, after the sand clouds: a dense core that spreads,
 * thins and drifts with the flow until the skimmer takes it.
 */
class InkCloudRenderer {
    private val vao = Gl.genVertexArray()
    private val vbo = Gl.genBuffer()
    private val out = FloatArray(CAPACITY * FLOATS)
    private val buffer = Gl.floats(FloatArray(CAPACITY * FLOATS))

    init {
        GLES30.glBindVertexArray(vao); Gl.check("glBindVertexArray(ink)")
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo); Gl.check("glBindBuffer(ink)")
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, CAPACITY * FLOATS * 4, null, GLES30.GL_DYNAMIC_DRAW); Gl.check("glBufferData(ink)")
        GLES30.glEnableVertexAttribArray(0); Gl.check("enable0(ink)")
        GLES30.glVertexAttribPointer(0, 4, GLES30.GL_FLOAT, false, FLOATS * 4, 0); Gl.check("ptr0(ink)")
        GLES30.glEnableVertexAttribArray(1); Gl.check("enable1(ink)")
        GLES30.glVertexAttribPointer(1, 4, GLES30.GL_FLOAT, false, FLOATS * 4, 16); Gl.check("ptr1(ink)")
        GLES30.glBindVertexArray(0); Gl.check("unbind(ink)")
    }

    /** Draws the ink clouds of the squid in [rigs]; returns the number of draw calls issued (0 or 1). */
    fun draw(p: ShaderProgram, rigs: Collection<FishRig>, viewportH: Int): Int {
        var k = 0
        for (r in rigs) {
            if (r.motionMode != 4 || r.limbCount < 5) continue
            val density = r.limbs[19]
            if (density <= 0.01f) continue
            if (k + SPLAT > CAPACITY) break
            val cx = r.limbs[13]; val cy = r.limbs[14]; val cz = r.limbs[15]
            val age = r.limbs[17]
            val radius = maxOf(r.limbs[18], 0.5f)
            // the fresh puff is a tight dark blob; it spreads into a loose, lighter haze
            val core = 1f / (1f + 0.25f * age)
            val cloud = r.id.hashCode()   // per squid, so the splat layout is stable frame to frame
            for (s in 0 until SPLAT) {
                val o = k * FLOATS
                val jx = jitter(cloud, s, 0); val jy = jitter(cloud, s, 1); val jz = jitter(cloud, s, 2)
                // points fill a sphere, denser toward the centre (r ~ u^1.5)
                val len = sqrt(jx * jx + jy * jy + jz * jz).coerceAtLeast(1e-3f)
                val rr = radius * (0.25f + 0.75f * (1f - core * 0.5f)) * (0.5f + 0.5f * jitter(cloud, s, 3)).let { it * sqrt(it) }
                out[o] = cx + jx / len * rr
                out[o + 1] = cy + jy / len * rr * 0.8f + 0.04f * age   // ink is a little buoyant in its mucus
                out[o + 2] = cz + jz / len * rr
                out[o + 3] = radius * (0.7f + 0.3f * jitter(cloud, s, 4))
                out[o + 4] = INK[0]; out[o + 5] = INK[1]; out[o + 6] = INK[2]
                out[o + 7] = (0.25f + 0.45f * core) * density
                k++
            }
        }
        if (k == 0) return 0
        buffer.position(0); buffer.put(out, 0, k * FLOATS); buffer.position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo); Gl.check("glBindBuffer(ink)")
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, k * FLOATS * 4, buffer); Gl.check("glBufferSubData(ink)")
        p.use()
        p.set1f("uViewportH", viewportH.toFloat())
        p.set2f("uSizeClamp", 2f, 160f)
        GLES30.glBindVertexArray(vao); Gl.check("glBindVertexArray(ink)")
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, k); Gl.check("glDrawArrays(ink)")
        GLES30.glBindVertexArray(0); Gl.check("unbind(ink)")
        return 1
    }

    fun release() { Gl.deleteVertexArray(vao); Gl.deleteBuffer(vbo) }

    private companion object {
        const val SPLAT = 18
        const val FLOATS = 8
        const val CAPACITY = 12 * SPLAT   // a dozen clouds at once is far more squid than any tank holds
        /** Sepia-black ink (linear rgb): melanin, a touch warm. */
        val INK = floatArrayOf(0.025f, 0.018f, 0.014f)

        /** Deterministic jitter in −1..1 per (cloud, splat, axis), so a cloud does not shimmer between frames. */
        fun jitter(i: Int, s: Int, axis: Int): Float {
            var h = i * 73856093 xor (s * 19349663) xor (axis * 83492791)
            h = h xor (h ushr 13); h *= 0x5bd1e995; h = h xor (h ushr 15)
            return ((h and 0xFFFF) / 32767.5f) - 1f
        }
    }
}
