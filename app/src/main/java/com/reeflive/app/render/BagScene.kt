package com.reeflive.app.render

import com.reeflive.core.sim.BagPose
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Procedural meshes for an acclimation bag (sandbox Add life, `core/sim/AcclimationBag.kt`), for a bulb radius of 1:
 * the waterline is y = 0, the round water-filled bulb hangs below it (centre y -0.7) and the air-filled neck narrows
 * to a knot and a crumpled twist above. Pure Kotlin (STRIDE-12 vertices, as [SubmarineMesh]).
 */
object BagMesh {
    class Part(val vertices: FloatArray, val indices: IntArray)

    private class Builder {
        val v = ArrayList<Float>(); val i = ArrayList<Int>()
        val count: Int get() = v.size / GlbPrimitive.STRIDE
        fun vertex(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, u: Float, w: Float) {
            v += x; v += y; v += z; v += nx; v += ny; v += nz; v += u; v += w; v += 1f; v += 1f; v += 1f; v += 1f
        }

        /** A surface of revolution about Y through the profile ([r], [y]) pairs, with a small crumple in the plastic. */
        fun lathe(profile: FloatArray, segs: Int = 22, crumple: Float = 0f) {
            val base = count
            val n = profile.size / 2
            for (a in 0 until n) {
                val r = profile[a * 2]; val y = profile[a * 2 + 1]
                // profile slope → normal
                val a0 = maxOf(0, a - 1); val a1 = minOf(n - 1, a + 1)
                val dr = profile[a1 * 2] - profile[a0 * 2]; val dy = profile[a1 * 2 + 1] - profile[a0 * 2 + 1]
                var nr = dy; var ny = -dr
                val l = sqrt(nr * nr + ny * ny).coerceAtLeast(1e-5f); nr /= l; ny /= l
                for (b in 0..segs) {
                    val ph = (2 * PI * b / segs).toFloat()
                    val wob = 1f + crumple * (sin(ph * 3f + y * 5f) * 0.6f + sin(ph * 7f - y * 3f) * 0.4f)
                    val rr = r * wob
                    vertex(rr * cos(ph), y, rr * sin(ph), nr * cos(ph), ny, nr * sin(ph), b.toFloat() / segs, a.toFloat() / (n - 1))
                }
            }
            for (a in 0 until n - 1) for (b in 0 until segs) {
                val p = base + a * (segs + 1) + b; val q = p + segs + 1
                i += p; i += q; i += p + 1; i += p + 1; i += q; i += q + 1
            }
        }

        /** A flat disc (the water surface inside the bag) at height [y]. */
        fun disc(r: Float, y: Float, segs: Int = 22) {
            val base = count
            vertex(0f, y, 0f, 0f, 1f, 0f, 0.5f, 0.5f)
            for (b in 0..segs) {
                val ph = (2 * PI * b / segs).toFloat()
                vertex(r * cos(ph), y, r * sin(ph), 0f, 1f, 0f, 0.5f + 0.5f * cos(ph), 0.5f + 0.5f * sin(ph))
            }
            for (b in 0 until segs) { i += base; i += base + 1 + b; i += base + 2 + b }
        }

        fun build() = Part(v.toFloatArray(), i.toIntArray())
    }

    /** The plastic: bulb, neck, knot and the twisted tail above it. */
    fun plastic(): Part = Builder().apply {
        lathe(
            floatArrayOf(
                0.02f, -1.7f, 0.5f, -1.62f, 0.82f, -1.42f, 0.97f, -1.1f, 1.0f, -0.72f, 0.97f, -0.38f,
                0.88f, -0.05f, 0.72f, 0.28f, 0.5f, 0.6f, 0.3f, 0.86f, 0.14f, 1.04f, 0.09f, 1.14f,
                0.17f, 1.3f, 0.2f, 1.45f, 0.12f, 1.6f, 0.02f, 1.66f,
            ),
            crumple = 0.035f,
        )
    }.build()

    /** The bag water: the bulb a hair inside the plastic, capped by its own little surface at the waterline. */
    fun water(): Part = Builder().apply {
        lathe(floatArrayOf(0.02f, -1.66f, 0.48f, -1.58f, 0.8f, -1.39f, 0.94f, -1.08f, 0.97f, -0.72f, 0.94f, -0.38f, 0.86f, -0.06f))
        disc(0.86f, -0.06f)
    }.build()

    /** The rubber band round the knot. */
    fun band(): Part = Builder().apply {
        lathe(floatArrayOf(0.1f, 1.08f, 0.14f, 1.1f, 0.14f, 1.16f, 0.1f, 1.18f), segs = 14)
    }.build()
}

/**
 * The acclimation bags in the renderer (GL thread): interpolates [BagPose]s between snapshots by id, draws each one
 * see-through with the generic program (water tint, plastic, rubber band), and reports the moments a bag splashes
 * down and tips its animals out so the particles can show it. Allocation-free per frame.
 */
internal class BagScene {
    private var plastic: Mesh? = null
    private var water: Mesh? = null
    private var band: Mesh? = null
    private val model = FloatArray(16)
    private val n = IntArray(1)
    private val px = FloatArray(MAX); private val py = FloatArray(MAX); private val pz = FloatArray(MAX)
    private val pr = FloatArray(MAX); private val tx = FloatArray(MAX); private val tz = FloatArray(MAX)
    private val yw = FloatArray(MAX); private val fade = FloatArray(MAX); private val ids = IntArray(MAX)
    private val coral = BooleanArray(MAX)
    private val seenIds = IntArray(MAX * 2); private val seenPhase = IntArray(MAX * 2); private var seenN = 0

    val count: Int get() = n[0]

    fun release() { plastic?.release(); water?.release(); band?.release(); plastic = null; water = null; band = null }

    private fun ensure() {
        if (plastic != null) return
        fun mk(p: BagMesh.Part) = Mesh.create(p.vertices, p.indices)
        plastic = mk(BagMesh.plastic()); water = mk(BagMesh.water()); band = mk(BagMesh.band())
    }

    /** Called when a bag lands (kind 0, a splash) and when it tips over (kind 1, bubbles): kind, x, y, z, radius. */
    var onBurst: ((Int, Float, Float, Float, Float) -> Unit)? = null

    /** This frame's bags from the previous and current snapshots. */
    fun update(prev: List<BagPose>?, cur: List<BagPose>?, alpha: Float) {
        var k = 0
        if (cur != null) for (b in cur) {
            if (k == MAX) break
            var p: BagPose? = null
            if (prev != null) for (q in prev) if (q.id == b.id) { p = q; break }
            val a = if (p == null) 1f else alpha
            val o = p ?: b
            ids[k] = b.id
            px[k] = o.x + (b.x - o.x) * a; py[k] = o.y + (b.y - o.y) * a; pz[k] = o.z + (b.z - o.z) * a
            pr[k] = b.radius
            tx[k] = o.tiltX + (b.tiltX - o.tiltX) * a; tz[k] = o.tiltZ + (b.tiltZ - o.tiltZ) * a
            yw[k] = o.yaw + (b.yaw - o.yaw) * a
            fade[k] = 1f - (o.lift + (b.lift - o.lift) * a)
            coral[k] = b.coral
            // phase changes since the last frame: splash on landing, bubbles when it tips
            val last = lastPhase(b.id)
            if (last == 0 && b.phase >= 1) onBurst?.invoke(0, b.x, b.y, b.z, b.radius)
            if (last in 0..1 && b.phase >= 2) onBurst?.invoke(1, b.x, b.y - 0.7f * b.radius, b.z, b.radius)
            remember(b.id, b.phase)
            k++
        }
        n[0] = k
        if (k == 0) seenN = 0
    }

    private fun lastPhase(id: Int): Int {
        for (i in 0 until seenN) if (seenIds[i] == id) return seenPhase[i]
        return -1
    }

    private fun remember(id: Int, phase: Int) {
        for (i in 0 until seenN) if (seenIds[i] == id) { seenPhase[i] = phase; return }
        if (seenN == seenIds.size) { System.arraycopy(seenIds, 1, seenIds, 0, seenN - 1); System.arraycopy(seenPhase, 1, seenPhase, 0, seenN - 1); seenN-- }
        seenIds[seenN] = id; seenPhase[seenN] = phase; seenN++
    }

    /** See-through bags (alpha blend, no depth write), after the water. Returns draw calls. */
    fun draw(p: ShaderProgram, white: Texture): Int {
        val cnt = n[0]
        if (cnt == 0) return 0
        ensure()
        p.use()
        white.bind(0)
        p.set1f("uHasTexture", 0f); p.set1f("uAlphaCutoff", 0f); p.set1f("uLedDies", 0f)
        p.set3f("uEmissive", 0f, 0f, 0f)
        var calls = 0
        for (k in 0 until cnt) {
            buildModel(k)
            val f = fade[k].coerceIn(0f, 1f)
            p.setMat4("uModel", model)
            // the bag water: shop water is a touch greener/clearer than the tank
            p.set4f("uBaseColor", 0.55f, 0.82f, 0.9f, 0.22f * f)
            p.set1f("uRoughness", 0.1f)
            water?.draw(); calls++
            // the plastic: clear with a faint blue cast, glossy
            p.set4f("uBaseColor", 0.92f, 0.97f, 1f, 0.38f * f)
            p.set1f("uRoughness", 0.12f)
            // a faint sheen so the clear plastic still reads in a dim tank
            p.set3f("uEmissive", 0.035f, 0.04f, 0.045f)
            plastic?.draw(); calls++
            p.set3f("uEmissive", 0f, 0f, 0f)
            // rubber band (corals come with a red band, fish and inverts with a yellow one)
            if (coral[k]) p.set4f("uBaseColor", 0.85f, 0.2f, 0.25f, f) else p.set4f("uBaseColor", 0.95f, 0.78f, 0.15f, f)
            p.set1f("uRoughness", 0.55f)
            band?.draw(); calls++
        }
        return calls
    }

    /** Model = translate(pos) · Rz(tiltZ) · Rx(tiltX) · Ry(yaw) · scale(radius). */
    private fun buildModel(k: Int) {
        val s = pr[k]
        val cy = cos(yw[k]); val sy = sin(yw[k])
        val cx = cos(tx[k]); val sx = sin(tx[k])
        val cz = cos(tz[k]); val sz = sin(tz[k])
        set3(ry, cy, 0f, -sy, 0f, 1f, 0f, sy, 0f, cy)
        set3(rx, 1f, 0f, 0f, 0f, cx, sx, 0f, -sx, cx)
        set3(rz, cz, sz, 0f, -sz, cz, 0f, 0f, 0f, 1f)
        mul3(rx, ry, tmpA); mul3(rz, tmpA, tmpB)
        model[0] = tmpB[0] * s; model[1] = tmpB[1] * s; model[2] = tmpB[2] * s; model[3] = 0f
        model[4] = tmpB[3] * s; model[5] = tmpB[4] * s; model[6] = tmpB[5] * s; model[7] = 0f
        model[8] = tmpB[6] * s; model[9] = tmpB[7] * s; model[10] = tmpB[8] * s; model[11] = 0f
        model[12] = px[k]; model[13] = py[k]; model[14] = pz[k]; model[15] = 1f
    }

    private val rx = FloatArray(9)
    private val ry = FloatArray(9)
    private val rz = FloatArray(9)

    private fun set3(m: FloatArray, a: Float, b: Float, c: Float, d: Float, e: Float, f: Float, g: Float, h: Float, i: Float) {
        m[0] = a; m[1] = b; m[2] = c; m[3] = d; m[4] = e; m[5] = f; m[6] = g; m[7] = h; m[8] = i
    }

    private val tmpA = FloatArray(9)
    private val tmpB = FloatArray(9)

    /** out = a · b for column-major 3×3. */
    private fun mul3(a: FloatArray, b: FloatArray, out: FloatArray) {
        for (c in 0..2) for (r in 0..2) out[c * 3 + r] = a[r] * b[c * 3] + a[3 + r] * b[c * 3 + 1] + a[6 + r] * b[c * 3 + 2]
    }

    companion object { const val MAX = 8 }
}
