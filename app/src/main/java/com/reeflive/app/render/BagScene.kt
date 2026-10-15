package com.reeflive.app.render

import com.reeflive.core.sim.AcclimationBags
import com.reeflive.core.sim.BagFrame
import com.reeflive.core.sim.BagPose
import com.reeflive.core.sim.TankDims
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

    /** The plastic once it is cut open below the knot: bulb and neck, open at the cut. */
    fun plasticCut(): Part = Builder().apply {
        lathe(
            floatArrayOf(
                0.02f, -1.7f, 0.5f, -1.62f, 0.82f, -1.42f, 0.97f, -1.1f, 1.0f, -0.72f, 0.97f, -0.38f,
                0.88f, -0.05f, 0.72f, 0.28f, 0.56f, 0.52f, 0.52f, 0.6f,
            ),
            crumple = 0.035f,
        )
    }.build()

    /** A bucket with its rim at y = 0 and its floor at y = -1, radius 1 at the rim. */
    fun bucket(): Part = Builder().apply {
        lathe(floatArrayOf(0.01f, -1f, 0.84f, -1f, 0.86f, -0.98f, 0.97f, -0.06f, 1.0f, 0f, 1.04f, 0.0f, 1.04f, -0.05f), segs = 24)
    }.build()

    /** The pour: a tube of radius 1 from y = 0 down to y = -1, thinning a little as it falls. */
    fun stream(): Part = Builder().apply {
        lathe(floatArrayOf(1f, 0f, 0.9f, -0.35f, 0.75f, -0.7f, 0.7f, -1f), segs = 10)
    }.build()

    /** The rubber band round the knot. */
    fun band(): Part = Builder().apply {
        lathe(floatArrayOf(0.1f, 1.08f, 0.14f, 1.1f, 0.14f, 1.16f, 0.1f, 1.18f), segs = 14)
    }.build()
}

/**
 * The acclimation bags in the renderer (GL thread): interpolates [BagPose]s between snapshots by id, draws each one
 * see-through with the generic program (water tint, plastic, rubber band), and while one is being emptied the cut
 * bag, the water pouring out of it, the bucket under the net and the net itself. Reports the moments a bag splashes
 * down, the net goes in and the animals are let out so the particles can show it. Allocation-free per frame.
 */
internal class BagScene {
    private var plastic: Mesh? = null
    private var plasticCut: Mesh? = null
    private var water: Mesh? = null
    private var band: Mesh? = null
    private var bucket: Mesh? = null
    private var stream: Mesh? = null
    private var hoop: Mesh? = null
    private var netBag: Mesh? = null
    private var handle: Mesh? = null
    private var netTex: Texture? = null
    private val model = FloatArray(16)
    private val hModel = FloatArray(16)
    private val rot = DoubleArray(9)
    private val n = IntArray(1)
    private val px = FloatArray(MAX); private val py = FloatArray(MAX); private val pz = FloatArray(MAX)
    private val pr = FloatArray(MAX); private val tx = FloatArray(MAX); private val tz = FloatArray(MAX)
    private val yw = FloatArray(MAX); private val fade = FloatArray(MAX); private val ids = IntArray(MAX)
    private val coral = BooleanArray(MAX)
    private val cut = BooleanArray(MAX); private val wat = FloatArray(MAX)
    /** Net: hoop centre, facing, handle, radius (0 = none); bucket: rim centre, radius, height; gear fade. */
    private val net = FloatArray(MAX * 10)
    private val bkt = FloatArray(MAX * 5)
    private val gear = FloatArray(MAX)
    /** Pour: from, to, radius (0 = none). */
    private val pour = FloatArray(MAX * 7)
    private val seenIds = IntArray(MAX * 2); private val seenPhase = IntArray(MAX * 2); private var seenN = 0
    private val seenStep = IntArray(MAX * 2); private val seenNetY = FloatArray(MAX * 2)

    val count: Int get() = n[0]

    fun release() {
        plastic?.release(); plasticCut?.release(); water?.release(); band?.release(); bucket?.release(); stream?.release()
        hoop?.release(); netBag?.release(); handle?.release(); netTex?.release()
        plastic = null; plasticCut = null; water = null; band = null; bucket = null; stream = null
        hoop = null; netBag = null; handle = null; netTex = null
    }

    private fun ensure() {
        if (plastic != null) return
        fun mk(p: BagMesh.Part) = Mesh.create(p.vertices, p.indices)
        fun mkN(p: NetMesh.Part) = Mesh.create(p.vertices, p.indices)
        plastic = mk(BagMesh.plastic()); plasticCut = mk(BagMesh.plasticCut()); water = mk(BagMesh.water()); band = mk(BagMesh.band())
        bucket = mk(BagMesh.bucket()); stream = mk(BagMesh.stream())
        hoop = mkN(NetMesh.hoop()); netBag = mkN(NetMesh.bag()); handle = mkN(NetMesh.handle())
        netTex = Texture.rgba(32, 32, NetMesh.meshTexel(32))
    }

    /** Called when a bag lands (kind 0, a splash), the net goes in (0) and lets the animals out (kind 1, bubbles): kind, x, y, z, radius. */
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
            cut[k] = b.cut
            wat[k] = o.water + (b.water - o.water) * a
            val nb = k * 10
            net[nb] = o.netX + (b.netX - o.netX) * a; net[nb + 1] = o.netY + (b.netY - o.netY) * a; net[nb + 2] = o.netZ + (b.netZ - o.netZ) * a
            net[nb + 3] = o.netFx + (b.netFx - o.netFx) * a; net[nb + 4] = o.netFy + (b.netFy - o.netFy) * a; net[nb + 5] = o.netFz + (b.netFz - o.netFz) * a
            val fl = sqrt(net[nb + 3] * net[nb + 3] + net[nb + 4] * net[nb + 4] + net[nb + 5] * net[nb + 5]).coerceAtLeast(1e-5f)
            net[nb + 3] /= fl; net[nb + 4] /= fl; net[nb + 5] /= fl
            net[nb + 6] = b.netHx; net[nb + 7] = b.netHy; net[nb + 8] = b.netHz; net[nb + 9] = b.netR
            val bb = k * 5
            bkt[bb] = b.bucketX; bkt[bb + 1] = b.bucketY; bkt[bb + 2] = b.bucketZ; bkt[bb + 3] = b.bucketR; bkt[bb + 4] = b.bucketH
            gear[k] = o.gearFade + (b.gearFade - o.gearFade) * a
            val pb = k * 7
            pour[pb] = o.streamX0 + (b.streamX0 - o.streamX0) * a; pour[pb + 1] = o.streamY0 + (b.streamY0 - o.streamY0) * a
            pour[pb + 2] = o.streamZ0 + (b.streamZ0 - o.streamZ0) * a
            pour[pb + 3] = b.streamX1; pour[pb + 4] = b.streamY1; pour[pb + 5] = b.streamZ1
            pour[pb + 6] = if (o.streamR > 0f) b.streamR else 0f
            // changes since the last frame: splash on landing and when the net goes in, bubbles when it lets them out
            val s = slot(b.id)
            if (seenPhase[s] == 0 && b.phase >= 1) onBurst?.invoke(0, b.x, b.y, b.z, b.radius)
            val w = TankDims.WATER_LEVEL_F
            val ny = net[nb + 1]
            if (b.netR > 0f && !seenNetY[s].isNaN() && seenNetY[s] >= w && ny < w) onBurst?.invoke(0, net[nb], w, net[nb + 2], b.netR)
            if (seenStep[s] in 0 until AcclimationBags.STEP_NET_TURN && b.step >= AcclimationBags.STEP_NET_TURN) onBurst?.invoke(1, net[nb], ny - 0.5f * b.netR, net[nb + 2], b.netR)
            seenPhase[s] = b.phase; seenStep[s] = b.step; seenNetY[s] = if (b.netR > 0f) ny else Float.NaN
            k++
        }
        n[0] = k
        if (k == 0) seenN = 0
    }

    /** This bag's slot in the remembered-state ring (a new id starts as "never seen"). */
    private fun slot(id: Int): Int {
        for (i in 0 until seenN) if (seenIds[i] == id) return i
        if (seenN == seenIds.size) {
            System.arraycopy(seenIds, 1, seenIds, 0, seenN - 1); System.arraycopy(seenPhase, 1, seenPhase, 0, seenN - 1)
            System.arraycopy(seenStep, 1, seenStep, 0, seenN - 1); System.arraycopy(seenNetY, 1, seenNetY, 0, seenN - 1)
            seenN--
        }
        seenIds[seenN] = id; seenPhase[seenN] = -1; seenStep[seenN] = -1; seenNetY[seenN] = Float.NaN
        return seenN++
    }

    /** See-through bags, the bucket, the pour and the net (alpha blend, no depth write), after the water. Returns draw calls. */
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
            val g = gear[k].coerceIn(0f, 1f)
            // the bucket under the net: a plain white keeper's bucket
            if (bkt[k * 5 + 3] > 0f && g > 0.01f) {
                bucketModel(k)
                p.setMat4("uModel", model)
                p.set4f("uBaseColor", 0.9f, 0.92f, 0.94f, 0.96f * g)
                p.set1f("uRoughness", 0.5f)
                bucket?.draw(); calls++
            }
            val f = fade[k].coerceIn(0f, 1f)
            if (f > 0.01f) {
                buildModel(k)
                p.setMat4("uModel", model)
                // the bag water: shop water is a touch greener/clearer than the tank
                val w = wat[k].coerceIn(0f, 1f)
                if (w > 0.02f) {
                    p.set4f("uBaseColor", 0.55f, 0.82f, 0.9f, 0.22f * f * w)
                    p.set1f("uRoughness", 0.1f)
                    water?.draw(); calls++
                }
                // the plastic: clear with a faint blue cast, glossy
                p.set4f("uBaseColor", 0.92f, 0.97f, 1f, 0.38f * f)
                p.set1f("uRoughness", 0.12f)
                // a faint sheen so the clear plastic still reads in a dim tank
                p.set3f("uEmissive", 0.035f, 0.04f, 0.045f)
                (if (cut[k]) plasticCut else plastic)?.draw(); calls++
                p.set3f("uEmissive", 0f, 0f, 0f)
                // rubber band (corals come with a red band, fish and inverts with a yellow one); cut off with the knot
                if (!cut[k]) {
                    if (coral[k]) p.set4f("uBaseColor", 0.85f, 0.2f, 0.25f, f) else p.set4f("uBaseColor", 0.95f, 0.78f, 0.15f, f)
                    p.set1f("uRoughness", 0.55f)
                    band?.draw(); calls++
                }
            }
            // the bag water pouring into the net
            if (pour[k * 7 + 6] > 0f) {
                streamModel(k)
                p.setMat4("uModel", model)
                p.set4f("uBaseColor", 0.6f, 0.85f, 0.95f, 0.45f)
                p.set1f("uRoughness", 0.05f)
                p.set3f("uEmissive", 0.03f, 0.04f, 0.05f)
                stream?.draw(); calls++
                p.set3f("uEmissive", 0f, 0f, 0f)
            }
            if (net[k * 10 + 9] > 0f && g > 0.01f) calls += drawNet(p, k, g, white)
        }
        return calls
    }

    private fun drawNet(p: ShaderProgram, k: Int, g: Float, white: Texture): Int {
        val b = k * 10
        netModels(net[b], net[b + 1], net[b + 2], net[b + 3], net[b + 4], net[b + 5], net[b + 6], net[b + 7], net[b + 8], net[b + 9], model, hModel)
        // the same green aquarium net as the one that fetches bodies out
        p.set4f("uBaseColor", 0.16f, 0.62f, 0.38f, g)
        p.set1f("uRoughness", 0.35f)
        p.setMat4("uModel", model)
        hoop?.draw()
        p.set4f("uBaseColor", 0.1f, 0.45f, 0.3f, g)
        p.set1f("uRoughness", 0.45f)
        p.setMat4("uModel", hModel)
        handle?.draw()
        val tex = netTex ?: return 2
        tex.bind(0)
        p.set1f("uHasTexture", 1f)
        p.set3f("uEmissive", 0.03f, 0.035f, 0.03f)
        p.set4f("uBaseColor", 0.86f, 0.95f, 0.88f, 0.85f * g)
        p.set1f("uRoughness", 0.6f)
        p.setMat4("uModel", model)
        netBag?.draw()
        p.set3f("uEmissive", 0f, 0f, 0f)
        p.set1f("uHasTexture", 0f)
        white.bind(0)
        return 3
    }

    /** Model = translate(pos) · [BagFrame.rotation] · scale(radius), the same frame the sim keeps the animals in. */
    private fun buildModel(k: Int) {
        val s = pr[k]
        BagFrame.rotation(tx[k].toDouble(), tz[k].toDouble(), yw[k].toDouble(), rot)
        // row-major rotation → column-major GL
        model[0] = rot[0].toFloat() * s; model[1] = rot[3].toFloat() * s; model[2] = rot[6].toFloat() * s; model[3] = 0f
        model[4] = rot[1].toFloat() * s; model[5] = rot[4].toFloat() * s; model[6] = rot[7].toFloat() * s; model[7] = 0f
        model[8] = rot[2].toFloat() * s; model[9] = rot[5].toFloat() * s; model[10] = rot[8].toFloat() * s; model[11] = 0f
        model[12] = px[k]; model[13] = py[k]; model[14] = pz[k]; model[15] = 1f
    }

    private fun bucketModel(k: Int) {
        val b = k * 5
        val r = bkt[b + 3]; val h = bkt[b + 4]
        model[0] = r; model[1] = 0f; model[2] = 0f; model[3] = 0f
        model[4] = 0f; model[5] = h; model[6] = 0f; model[7] = 0f
        model[8] = 0f; model[9] = 0f; model[10] = r; model[11] = 0f
        model[12] = bkt[b]; model[13] = bkt[b + 1]; model[14] = bkt[b + 2]; model[15] = 1f
    }

    /** The pour tube: Y runs from the bag mouth (y 0) to the net (y -1), X and Z are its radius. */
    private fun streamModel(k: Int) {
        val b = k * 7
        val dx = pour[b] - pour[b + 3]; val dy = pour[b + 1] - pour[b + 4]; val dz = pour[b + 2] - pour[b + 5]
        val l = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-4f)
        val ux = dx / l; val uy = dy / l; val uz = dz / l
        // any two directions across it
        var ax = uz; var ay = 0f; var az = -ux
        var al = sqrt(ax * ax + az * az)
        if (al < 1e-4f) { ax = 1f; ay = 0f; az = 0f; al = 1f }
        ax /= al; ay /= al; az /= al
        val cx = uy * az - uz * ay; val cy = uz * ax - ux * az; val cz = ux * ay - uy * ax
        val r = pour[b + 6]
        model[0] = ax * r; model[1] = ay * r; model[2] = az * r; model[3] = 0f
        model[4] = dx; model[5] = dy; model[6] = dz; model[7] = 0f
        model[8] = cx * r; model[9] = cy * r; model[10] = cz * r; model[11] = 0f
        model[12] = pour[b]; model[13] = pour[b + 1]; model[14] = pour[b + 2]; model[15] = 1f
    }

    companion object { const val MAX = 8 }
}
