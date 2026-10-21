package com.reeflive.app.render

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The mucus cocoon a sleeping fairy wrasse spins around itself in its crevice (core FairyWrasseBrain, [FishRig.cocoon]
 * 0..1 as it is built over the first part of the night, Grutter et al. 2011 on parrotfish/wrasse cocoons): a clear,
 * faintly milky ellipsoid around the fish, drawn see-through with the generic program after the fish. A fish that
 * bursts out (tap, attack) drops to 0 at once. Allocation-free per frame.
 */
internal class CocoonScene {
    private var shell: Mesh? = null
    private val model = FloatArray(16)

    fun release() { shell?.release(); shell = null }

    private fun ensure() {
        if (shell != null) return
        val (v, i) = sphere(16, 24)
        shell = Mesh.create(v, i)
    }

    /** Draws a cocoon around every rig with one; returns the draw calls. */
    fun draw(p: ShaderProgram, white: Texture, rigs: Collection<FishRig>): Int {
        var calls = 0
        for (r in rigs) {
            val c = r.cocoon
            if (c < 0.02f || !r.visible || !r.alive) continue
            if (calls == 0) {
                ensure()
                p.use()
                white.bind(0)
                p.set1f("uHasTexture", 0f); p.set1f("uAlphaCutoff", 0f); p.set1f("uLedDies", 0f)
                p.set1f("uRoughness", 0.08f)
                p.set3f("uEmissive", 0.02f, 0.025f, 0.03f)
            }
            if (!frame(r, c)) continue
            p.setMat4("uModel", model)
            // clear slime with a faint milky cast that thickens as the fish finishes it
            p.set4f("uBaseColor", 0.9f, 0.94f, 0.96f, 0.08f + 0.14f * c)
            shell?.draw(); calls++
        }
        if (calls > 0) p.set3f("uEmissive", 0f, 0f, 0f)
        return calls
    }

    /** Ellipsoid around the fish: long axis head → tail, a little longer than the body, the girth growing with [c]. */
    private fun frame(r: FishRig, c: Float): Boolean {
        val s = r.spine
        val hx = s[0]; val hy = s[1]; val hz = s[2]
        val tx = s[60]; val ty = s[61]; val tz = s[62]
        var ax = hx - tx; var ay = hy - ty; var az = hz - tz
        val len = sqrt(ax * ax + ay * ay + az * az)
        if (len < 0.1f) return false
        ax /= len; ay /= len; az /= len
        // side axis: horizontal, across the body (world up × long axis, or X if the fish hangs head-down)
        var bx = -az; var by = 0f; var bz = ax
        var bl = sqrt(bx * bx + bz * bz)
        if (bl < 1e-3f) { bx = 1f; bz = 0f; bl = 1f }
        bx /= bl; bz /= bl
        // up axis = long × side
        val ux = ay * bz - az * by; val uy = az * bx - ax * bz; val uz = ax * by - ay * bx
        val grow = 0.55f + 0.45f * c
        val ra = len * 0.62f                 // half length: the cocoon closes past the snout and tail
        val ru = len * 0.32f * grow          // height (the fish is deep-bodied side on)
        val rb = len * 0.22f * grow          // width
        model[0] = ax * ra; model[1] = ay * ra; model[2] = az * ra; model[3] = 0f
        model[4] = ux * ru; model[5] = uy * ru; model[6] = uz * ru; model[7] = 0f
        model[8] = bx * rb; model[9] = by * rb; model[10] = bz * rb; model[11] = 0f
        model[12] = (hx + tx) * 0.5f; model[13] = (hy + ty) * 0.5f; model[14] = (hz + tz) * 0.5f; model[15] = 1f
        return true
    }

    private companion object {
        /** A unit sphere ([GlbPrimitive.STRIDE] floats per vertex) with a slight lumpy wobble so it reads as slime. */
        fun sphere(rings: Int, segs: Int): Pair<FloatArray, IntArray> {
            val st = GlbPrimitive.STRIDE
            val v = FloatArray((rings + 1) * (segs + 1) * st)
            var o = 0
            for (a in 0..rings) {
                val th = (PI * a / rings).toFloat()
                for (b in 0..segs) {
                    val ph = (2 * PI * b / segs).toFloat()
                    val nx = cos(th); val ny = sin(th) * cos(ph); val nz = sin(th) * sin(ph)
                    val wob = 1f + 0.04f * sin(ph * 3f + th * 4f) * sin(th) + 0.02f * abs(sin(th * 5f))
                    v[o] = nx * wob; v[o + 1] = ny * wob; v[o + 2] = nz * wob
                    v[o + 3] = nx; v[o + 4] = ny; v[o + 5] = nz
                    v[o + 6] = b.toFloat() / segs; v[o + 7] = a.toFloat() / rings
                    v[o + 8] = 1f; v[o + 9] = 1f; v[o + 10] = 1f; v[o + 11] = 1f
                    o += st
                }
            }
            val idx = IntArray(rings * segs * 6)
            var k = 0
            for (a in 0 until rings) for (b in 0 until segs) {
                val p = a * (segs + 1) + b; val q = p + segs + 1
                idx[k++] = p; idx[k++] = q; idx[k++] = p + 1; idx[k++] = p + 1; idx[k++] = q; idx[k++] = q + 1
            }
            return v to idx
        }
    }
}
