package com.reeflive.app.render

import com.reeflive.core.sim.FishNet
import com.reeflive.core.sim.NetPose
import com.reeflive.core.sim.TankDims
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Procedural meshes for the fish net (`core/sim/FishNet.kt`), for a hoop radius of 1 with the mouth facing +Y: the
 * wire hoop at y = 0, the mesh bag trailing to y = -[FishNet.BAG_DEPTH], and a unit-length handle along +Y.
 * Pure Kotlin (STRIDE-12 vertices, as [BagMesh]).
 */
object NetMesh {
    class Part(val vertices: FloatArray, val indices: IntArray)

    private class Builder {
        val v = ArrayList<Float>(); val i = ArrayList<Int>()
        val count: Int get() = v.size / GlbPrimitive.STRIDE
        fun vertex(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, u: Float, w: Float) {
            v += x; v += y; v += z; v += nx; v += ny; v += nz; v += u; v += w; v += 1f; v += 1f; v += 1f; v += 1f
        }

        /** A surface of revolution about Y through the profile ([r], [y]) pairs; UVs repeat [uRep] × [vRep] times. */
        fun lathe(profile: FloatArray, segs: Int, uRep: Float = 1f, vRep: Float = 1f) {
            val base = count
            val n = profile.size / 2
            for (a in 0 until n) {
                val r = profile[a * 2]; val y = profile[a * 2 + 1]
                val a0 = maxOf(0, a - 1); val a1 = minOf(n - 1, a + 1)
                val dr = profile[a1 * 2] - profile[a0 * 2]; val dy = profile[a1 * 2 + 1] - profile[a0 * 2 + 1]
                var nr = -dy; var ny = dr
                val l = sqrt(nr * nr + ny * ny).coerceAtLeast(1e-5f); nr /= l; ny /= l
                for (b in 0..segs) {
                    val ph = (2 * PI * b / segs).toFloat()
                    vertex(r * cos(ph), y, r * sin(ph), nr * cos(ph), ny, nr * sin(ph), uRep * b / segs, vRep * a / (n - 1))
                }
            }
            for (a in 0 until n - 1) for (b in 0 until segs) {
                val p = base + a * (segs + 1) + b; val q = p + segs + 1
                i += p; i += q; i += p + 1; i += p + 1; i += q; i += q + 1
            }
        }

        fun build() = Part(v.toFloatArray(), i.toIntArray())
    }

    /** The wire hoop: a thin torus round the mouth. */
    fun hoop(): Part = Builder().apply {
        val t = 0.045f
        val prof = FloatArray(2 * 9)
        for (k in 0..8) {
            val a = (2 * PI * k / 8).toFloat()
            prof[k * 2] = 1f + t * cos(a); prof[k * 2 + 1] = t * sin(a)
        }
        lathe(prof, segs = 36)
    }.build()

    /** The mesh bag: soft-sided, rounding off to a closed bottom. */
    fun bag(): Part = Builder().apply {
        val d = FishNet.BAG_DEPTH.toFloat()
        lathe(
            floatArrayOf(
                1f, 0f, 0.99f, -0.12f * d, 0.95f, -0.3f * d, 0.86f, -0.5f * d, 0.72f, -0.68f * d,
                0.54f, -0.83f * d, 0.33f, -0.94f * d, 0.12f, -0.995f * d, 0.01f, -d,
            ),
            segs = 28, uRep = 14f, vRep = 6f,
        )
    }.build()

    /** The handle: a unit-long rod (radius 1) with a rounded grip end, scaled by the scene. */
    fun handle(): Part = Builder().apply {
        lathe(floatArrayOf(1f, 0f, 1f, 0.78f, 1.6f, 0.8f, 1.6f, 0.98f, 1.1f, 1f, 0.01f, 1.0f), segs = 12)
    }.build()

    /** RGBA8 net texture: a square knotted mesh on a clear ground (one cell per texture repeat). */
    fun meshTexel(size: Int = 32): ByteArray {
        val out = ByteArray(size * size * 4)
        for (y in 0 until size) for (x in 0 until size) {
            val dx = minOf(x, size - 1 - x); val dy = minOf(y, size - 1 - y)
            val line = minOf(dx, dy)
            val a = when { line == 0 -> 255; line == 1 -> 170; else -> 0 }
            val o = (y * size + x) * 4
            out[o] = 245.toByte(); out[o + 1] = 250.toByte(); out[o + 2] = 245.toByte(); out[o + 3] = a.toByte()
        }
        return out
    }
}

/**
 * The fish net in the renderer (GL thread): interpolates the [NetPose] between snapshots, draws the hoop and handle
 * solid and the mesh bag see-through, and reports when the hoop breaks the surface so the particles can splash.
 * Allocation-free per frame.
 */
internal class NetScene {
    private var hoop: Mesh? = null
    private var bag: Mesh? = null
    private var handle: Mesh? = null
    private var meshTex: Texture? = null
    private val model = FloatArray(16)
    private val hModel = FloatArray(16)
    private var on = false
    private var x = 0f; private var y = 0f; private var z = 0f
    private var fx = 0f; private var fy = 1f; private var fz = 0f
    private var hx = 0f; private var hy = 1f; private var hz = 0f
    private var r = 5f
    private var lastY = Float.NaN

    val visible: Boolean get() = on

    /** Called when the hoop goes into (kind 0) or out of (kind 1) the water: kind, x, y, z, radius. */
    var onSplash: ((Int, Float, Float, Float, Float) -> Unit)? = null

    fun release() {
        hoop?.release(); bag?.release(); handle?.release(); meshTex?.release()
        hoop = null; bag = null; handle = null; meshTex = null
    }

    private fun ensure() {
        if (hoop != null) return
        fun mk(p: NetMesh.Part) = Mesh.create(p.vertices, p.indices)
        hoop = mk(NetMesh.hoop()); bag = mk(NetMesh.bag()); handle = mk(NetMesh.handle())
        meshTex = Texture.rgba(32, 32, NetMesh.meshTexel(32))
    }

    fun update(prev: NetPose?, cur: NetPose?, alpha: Float) {
        if (cur == null) { on = false; lastY = Float.NaN; return }
        val p = if (prev != null && prev.targetId == cur.targetId) prev else cur
        val a = if (p === cur) 1f else alpha
        x = p.x + (cur.x - p.x) * a; y = p.y + (cur.y - p.y) * a; z = p.z + (cur.z - p.z) * a
        fx = p.nx + (cur.nx - p.nx) * a; fy = p.ny + (cur.ny - p.ny) * a; fz = p.nz + (cur.nz - p.nz) * a
        val fl = sqrt(fx * fx + fy * fy + fz * fz).coerceAtLeast(1e-5f); fx /= fl; fy /= fl; fz /= fl
        hx = cur.hx; hy = cur.hy; hz = cur.hz
        r = cur.radius
        val w = TankDims.WATER_LEVEL_F
        if (!lastY.isNaN()) {
            if (lastY >= w && y < w) onSplash?.invoke(0, x, w, z, r)
            if (lastY < w && y >= w) onSplash?.invoke(1, x, w, z, r)
        }
        lastY = y
        on = true
    }

    /** Hoop and handle (opaque pass). Returns draw calls. */
    fun drawSolid(p: ShaderProgram, white: Texture): Int {
        if (!on) return 0
        ensure()
        buildModels()
        p.use()
        white.bind(0)
        p.set1f("uHasTexture", 0f); p.set1f("uAlphaCutoff", 0f); p.set1f("uLedDies", 0f)
        p.set3f("uEmissive", 0f, 0f, 0f)
        // a classic green aquarium net: coated wire hoop, plastic handle
        p.set4f("uBaseColor", 0.16f, 0.62f, 0.38f, 1f)
        p.set1f("uRoughness", 0.35f)
        p.setMat4("uModel", model)
        hoop?.draw()
        p.set4f("uBaseColor", 0.1f, 0.45f, 0.3f, 1f)
        p.set1f("uRoughness", 0.45f)
        p.setMat4("uModel", hModel)
        handle?.draw()
        return 2
    }

    /** The mesh bag (alpha blend, no depth write), after the water. Returns draw calls. */
    fun drawMesh(p: ShaderProgram): Int {
        if (!on) return 0
        val tex = meshTex ?: return 0
        p.use()
        tex.bind(0)
        p.set1f("uHasTexture", 1f); p.set1f("uAlphaCutoff", 0f); p.set1f("uLedDies", 0f)
        // a faint sheen keeps the white mesh readable in a dim tank
        p.set3f("uEmissive", 0.03f, 0.035f, 0.03f)
        p.set4f("uBaseColor", 0.86f, 0.95f, 0.88f, 0.85f)
        p.set1f("uRoughness", 0.6f)
        p.setMat4("uModel", model)
        bag?.draw()
        p.set3f("uEmissive", 0f, 0f, 0f)
        p.set1f("uHasTexture", 0f)
        return 1
    }

    /** Hoop / bag: Y = facing, Z = the handle side, X = Y × Z, scaled by the radius. Handle: Y along the handle. */
    private fun buildModels() {
        // Z: the handle direction with the facing taken out (falls back to any perpendicular)
        var zx = hx; var zy = hy; var zz = hz
        val d = zx * fx + zy * fy + zz * fz
        zx -= fx * d; zy -= fy * d; zz -= fz * d
        var zl = sqrt(zx * zx + zy * zy + zz * zz)
        if (zl < 1e-3f) { zx = 0f; zy = 0f; zz = 1f; val e = fz; zx -= fx * e; zy -= fy * e; zz -= fz * e; zl = sqrt(zx * zx + zy * zy + zz * zz).coerceAtLeast(1e-5f) }
        zx /= zl; zy /= zl; zz /= zl
        val xx = fy * zz - fz * zy; val xy = fz * zx - fx * zz; val xz = fx * zy - fy * zx
        model[0] = xx * r; model[1] = xy * r; model[2] = xz * r; model[3] = 0f
        model[4] = fx * r; model[5] = fy * r; model[6] = fz * r; model[7] = 0f
        model[8] = zx * r; model[9] = zy * r; model[10] = zz * r; model[11] = 0f
        model[12] = x; model[13] = y; model[14] = z; model[15] = 1f
        // the handle starts at the hoop rim on the handle side and runs out of the tank
        val bx = x + zx * r; val by = y + zy * r; val bz = z + zz * r
        // its own frame: Y = handle, X ⟂ handle in the horizontal plane
        var ax = hz; var az = -hx
        val al = sqrt(ax * ax + az * az)
        if (al < 1e-4f) { ax = 1f; az = 0f } else { ax /= al; az /= al }
        // C = A × H (A = (ax, 0, az))
        val c0 = -az * hy; val c1 = az * hx - ax * hz; val c2 = ax * hy
        val rad = HANDLE_RADIUS_CM; val len = HANDLE_LENGTH_CM
        hModel[0] = ax * rad; hModel[1] = 0f; hModel[2] = az * rad; hModel[3] = 0f
        hModel[4] = hx * len; hModel[5] = hy * len; hModel[6] = hz * len; hModel[7] = 0f
        hModel[8] = c0 * rad; hModel[9] = c1 * rad; hModel[10] = c2 * rad; hModel[11] = 0f
        hModel[12] = bx; hModel[13] = by; hModel[14] = bz; hModel[15] = 1f
    }

    companion object {
        const val HANDLE_RADIUS_CM = 0.55f
        const val HANDLE_LENGTH_CM = 48f
    }
}
