package com.reeflive.app.render

import android.opengl.GLES30
import com.reeflive.core.sim.SandBed
import com.reeflive.core.sim.SimSnapshot
import java.nio.FloatBuffer

/**
 * Dynamic sand bed on the GPU (SIMULATION §16). [SimSnapshot.sandGrid] (128 × 48 cells: height offset cm, freshness,
 * detritus, dug cavity under rock) is packed into a small RGBA16F texture on unit 13 (`uSandMap`): static.vert lifts
 * the sand's top vertices by the height (pits, mounds, trails, tunnels dug under the rock, spoil piles) and rock.frag
 * shades the slope, darkens freshly turned damp sand, tints mulm and shadows the floor of dug burrows (alpha).
 * Uploads only when the grid's version changes, at most every [UPLOAD_INTERVAL_NS] (≈ 98 KB each; the sim sends a
 * new grid every 0.1 s only while grains fly or a slope pours, every 0.25 s otherwise).
 */
class SandFx {
    private val tex = Gl.genTexture()
    private var w = 1
    private var h = 1
    private var version = Long.MIN_VALUE
    private var lastUploadNs = 0L
    private var data = FloatArray(4)
    private var extentX = 1f
    private var extentZ = 1f
    private var active = false

    init {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + UNIT); Gl.check("glActiveTexture(sand)")
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex); Gl.check("glBindTexture(sand)")
        allocate(1, 1)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE); Gl.check("wrapS(sand)")
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE); Gl.check("wrapT(sand)")
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR); Gl.check("minFilter(sand)")
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR); Gl.check("magFilter(sand)")
        upload()
    }

    private fun allocate(nw: Int, nh: Int) {
        w = nw; h = nh
        data = FloatArray(nw * nh * 4)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA16F, nw, nh, 0, GLES30.GL_RGBA, GLES30.GL_FLOAT, null)
        Gl.check("glTexImage2D(sand RGBA16F)")
    }

    private fun upload() {
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 4); Gl.check("glPixelStorei(sand)")
        val buf: FloatBuffer = Gl.floats(data, w * h * 4)
        GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, w, h, GLES30.GL_RGBA, GLES30.GL_FLOAT, buf)
        Gl.check("glTexSubImage2D(sand)")
    }

    /** Uploads the snapshot's grid when it changed (throttled). Row r of the texture is the sim's z cell r. */
    fun update(s: SimSnapshot?, nowNs: Long) {
        val g = s?.sandGrid ?: return
        if (g.version == version) return
        if (active && nowNs - lastUploadNs < UPLOAD_INTERVAL_NS) return
        val n = g.nx * g.nz
        if (g.nx < 2 || g.nz < 2 || g.height.size < n || g.fresh.size < n || g.detritus.size < n) return
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + UNIT); Gl.check("glActiveTexture(sand)")
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex); Gl.check("glBindTexture(sand)")
        if (g.nx != w || g.nz != h) allocate(g.nx, g.nz)
        val cav = g.cavity
        val hasCavity = cav.size >= n
        for (i in 0 until n) {
            data[i * 4] = g.height[i]
            data[i * 4 + 1] = g.fresh[i]
            data[i * 4 + 2] = g.detritus[i]
            data[i * 4 + 3] = if (hasCavity) cav[i] else 0f
        }
        upload()
        extentX = g.nx * g.cellXCm; extentZ = g.nz * g.cellZCm
        version = g.version; lastUploadNs = nowNs; active = true
    }

    /** Binds the map for the sand program (the SAND branch of drawStatic). */
    fun bind(p: ShaderProgram) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + UNIT); Gl.check("glActiveTexture(sand)")
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex); Gl.check("glBindTexture(sand)")
        p.set1f("uSandOn", if (active) 1f else 0f)
        p.set2f("uSandExtent", extentX, extentZ)
    }

    fun release() = Gl.deleteTexture(tex)

    companion object {
        /** Fixed sampler unit of `uSandMap` (ShaderProgram.SAMPLER_UNITS). */
        const val UNIT = 13
        const val UPLOAD_INTERVAL_NS = 80_000_000L
    }
}

/**
 * Sand grains and mulm clouds (SIMULATION §16): each of the sim's particles ([SimSnapshot.sandClouds], stride
 * [SandBed.CLOUD_STRIDE]: x, y, z, kind + age/60, velocity, sand carried, diameter, id) drawn with the shared particles program. Sand
 * and fine grains are crisp lit specks in the mixed aragonite tones (cream, white, pink, a grey fleck), a thrown clump
 * as a few grains together; silt is a faint haze that spreads as it ages; detritus brown flecks. The scene is drawn a
 * fraction of a tick behind the newest snapshot, so each particle is carried along its velocity by [aheadS] sim
 * seconds from the older snapshot: grains fall smoothly at 60 fps from 20 Hz sim steps.
 */
class SandCloudRenderer {
    private val vao = Gl.genVertexArray()
    private val vbo = Gl.genBuffer()
    private val out = FloatArray(CAPACITY * FLOATS)
    private val buffer = Gl.floats(FloatArray(CAPACITY * FLOATS))

    init {
        GLES30.glBindVertexArray(vao); Gl.check("glBindVertexArray(sandClouds)")
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo); Gl.check("glBindBuffer(sandClouds)")
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, CAPACITY * FLOATS * 4, null, GLES30.GL_DYNAMIC_DRAW); Gl.check("glBufferData(sandClouds)")
        GLES30.glEnableVertexAttribArray(0); Gl.check("enable0(sandClouds)")
        GLES30.glVertexAttribPointer(0, 4, GLES30.GL_FLOAT, false, FLOATS * 4, 0); Gl.check("ptr0(sandClouds)")
        GLES30.glEnableVertexAttribArray(1); Gl.check("enable1(sandClouds)")
        GLES30.glVertexAttribPointer(1, 4, GLES30.GL_FLOAT, false, FLOATS * 4, 16); Gl.check("ptr1(sandClouds)")
        GLES30.glBindVertexArray(0); Gl.check("unbind(sandClouds)")
    }

    /** Uploads and draws [clouds] carried [aheadS] sim seconds along their velocity; returns the draw calls issued (0 or 1). */
    fun draw(p: ShaderProgram, clouds: FloatArray?, viewportH: Int, aheadS: Float = 0f): Int {
        val st = SandBed.CLOUD_STRIDE
        if (clouds == null || clouds.size < st) return 0
        val n = clouds.size / st
        val t = aheadS.coerceIn(0f, MAX_AHEAD_S)
        var k = 0
        for (i in 0 until n) {
            if (k + MAX_SPLAT > CAPACITY) break
            val b = i * st
            val x = clouds[b] + clouds[b + 4] * t; val y = clouds[b + 1] + clouds[b + 5] * t; val z = clouds[b + 2] + clouds[b + 6] * t
            val code = clouds[b + 3]
            val kind = code.toInt().coerceIn(0, 3)
            val age = (code - kind).coerceIn(0f, 1f)   // age / 60 s
            val mass = clouds[b + 7]
            val dCm = clouds[b + 8] * 0.1f
            val id = clouds[b + 9].toInt()
            if (kind <= FINE && mass > 0f) {
                // a pinch of grains: opaque lit specks at the grain's own size (a hair over it so a lit grain reads),
                // tone per speck so a spray is mixed sand; a bigger packet is a few specks spread over a clump's width
                val grains = (mass / (2f * SandBed.GRAIN_CM3.toFloat()) + 0.5f).toInt().coerceIn(1, MAX_SPLAT)
                val spread = if (grains > 1) 0.03f + 2.5f * dCm else 0f
                for (s in 0 until grains) {
                    val o = k * FLOATS
                    val tone = (hash(id, s, 3) * TONES).toInt().coerceIn(0, TONES - 1) * 3
                    out[o] = x + spread * jitter(id, s, 0)
                    out[o + 1] = y + spread * 0.6f * jitter(id, s, 1)
                    out[o + 2] = z + spread * jitter(id, s, 2)
                    out[o + 3] = GRAIN_SIZE_K * dCm * (0.8f + 0.4f * hash(id, s, 4))
                    out[o + 4] = GRAIN_TONE[tone]; out[o + 5] = GRAIN_TONE[tone + 1]; out[o + 6] = GRAIN_TONE[tone + 2]
                    out[o + 7] = 0.95f
                    k++
                }
                continue
            }
            val spread = SPREAD[kind] * (1f + 4f * age)
            val fade = 1f - 0.6f * age
            for (s in 0 until SPLAT) {
                val o = k * FLOATS
                out[o] = x + spread * jitter(id, s, 0)
                out[o + 1] = y + spread * 0.6f * jitter(id, s, 1)
                out[o + 2] = z + spread * jitter(id, s, 2)
                out[o + 3] = SIZE[kind] * (1f + age)
                out[o + 4] = COLOR[kind * 4]; out[o + 5] = COLOR[kind * 4 + 1]; out[o + 6] = COLOR[kind * 4 + 2]
                out[o + 7] = COLOR[kind * 4 + 3] * fade
                k++
            }
        }
        if (k == 0) return 0
        buffer.position(0); buffer.put(out, 0, k * FLOATS); buffer.position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo); Gl.check("glBindBuffer(sandClouds)")
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, k * FLOATS * 4, buffer); Gl.check("glBufferSubData(sandClouds)")
        p.use()
        p.set1f("uViewportH", viewportH.toFloat())
        p.set2f("uSizeClamp", 1f, 10f)
        GLES30.glBindVertexArray(vao); Gl.check("glBindVertexArray(sandClouds)")
        // the particle shader's sprite-shape attribute is not fed here: pin it to the plain soft dot
        GLES30.glVertexAttrib4f(2, 0f, 0f, 0f, 1f); Gl.check("glVertexAttrib4f(sandClouds)")
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, k); Gl.check("glDrawArrays(sandClouds)")
        GLES30.glBindVertexArray(0); Gl.check("unbind(sandClouds)")
        return 1
    }

    fun release() { Gl.deleteVertexArray(vao); Gl.deleteBuffer(vbo) }

    private companion object {
        const val SPLAT = 3
        const val MAX_SPLAT = 3
        const val FLOATS = 8
        const val CAPACITY = SandBed.MAX_PARTICLES * 2
        /** Drawn speck size per grain diameter: a lit grain glints a little larger than it is. */
        const val GRAIN_SIZE_K = 1.6f
        /** Grain.FINE's ordinal: SAND and FINE are grains, SILT and DETRITUS clouds. */
        const val FINE = 1
        /** Furthest a particle is carried past its snapshot (s): a late frame never flings grains far. */
        const val MAX_AHEAD_S = 0.15f
        // per Grain ordinal: SAND, FINE, SILT, DETRITUS
        val SPREAD = floatArrayOf(0.12f, 0.2f, 0.45f, 0.3f)
        val SIZE = floatArrayOf(0.14f, 0.12f, 0.4f, 0.28f)
        val COLOR = floatArrayOf(
            0.78f, 0.73f, 0.62f, 0.85f,
            0.72f, 0.68f, 0.6f, 0.6f,
            0.62f, 0.6f, 0.54f, 0.22f,
            0.3f, 0.22f, 0.12f, 0.5f,
        )
        /** Reef aragonite sand: cream, white, pale pink (coralline bits), tan and the odd grey shell fleck (linear rgb). */
        const val TONES = 5
        val GRAIN_TONE = floatArrayOf(
            0.80f, 0.74f, 0.60f,
            0.88f, 0.86f, 0.80f,
            0.82f, 0.66f, 0.62f,
            0.70f, 0.62f, 0.48f,
            0.52f, 0.52f, 0.50f,
        )

        /** Deterministic jitter in −1..1 per (particle id, splat, axis), so a cloud does not shimmer between frames. */
        fun jitter(i: Int, s: Int, axis: Int): Float = hash(i, s, axis) * 2f - 1f

        fun hash(i: Int, s: Int, axis: Int): Float {
            var h = i * 73856093 xor (s * 19349663) xor (axis * 83492791)
            h = h xor (h ushr 13); h *= 0x5bd1e995; h = h xor (h ushr 15)
            return (h and 0xFFFF) / 65535f
        }
    }
}
