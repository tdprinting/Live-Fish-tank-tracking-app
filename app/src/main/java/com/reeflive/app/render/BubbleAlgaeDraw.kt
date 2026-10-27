package com.reeflive.app.render

import com.reeflive.core.sim.AlgaeCover
import com.reeflive.core.sim.DefaultScape
import com.reeflive.core.sim.RockPieceDef
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Bubble algae (Valonia / Ventricaria; research 09 A6, SIMULATION §11.2): glossy dark-green spheres 2-30 mm across,
 * single and then in clusters, on the shaded undersides and crevices of the rock. One sphere mesh drawn instanced
 * (bubble.vert / bubble.frag): no per-frame allocation or buffer upload.
 *
 * Spots are picked once per rock mesh ([candidates]): rock triangles weighted by area and by how shaded and creviced
 * they are (the baked COLOR_0 cavity and open-sky AO), then put in growth order outward from a few seed cells, so a
 * rising cover grows the clusters out from where they started. The sim's bubble cover ([AlgaeVisuals.bubbleCount])
 * picks how many of the first spots show; the last one swells in (uCount).
 *
 * Budget (ARCHITECTURE §8.7): [MAX_SPHERES] × 64 triangles on High, half on Medium and at relief 2, none on Low or at
 * relief 3, where rock.frag's flat glossy tint stands in. The instance buffer is rebuilt only when a rock moves (each
 * sphere rides its rock's pose) or the tank is trimmed.
 */
class BubbleAlgaeDraw {
    private var forPart: DrawPart? = null
    /** Per spot, in growth order: x y z (world cm), nx ny nz, radius (cm), rock piece (−1: none), colour variant. */
    private var spots = FloatArray(0)
    private var spotCount = 0
    private var mesh: Mesh? = null
    private val inst = FloatArray(MAX_SPHERES * Mesh.INSTANCE_FLOATS)
    private var instCount = 0
    private var builtMoved = -1L
    private var builtVersion = Long.MIN_VALUE
    private var builtTrim = false
    private val tmp = FloatArray(3)

    /** Spheres this preset and relief level may draw (0 = tint only). */
    fun budget(quality: RenderQuality, relief: Int): Int = when {
        quality == RenderQuality.LOW || relief >= 3 -> 0
        quality == RenderQuality.HIGH && relief < 2 -> MAX_SPHERES
        else -> MAX_SPHERES / 2
    }

    /**
     * Draws the bubble algae on [rock] (the rockscape's rock part) for [cover]; [p] = the bubble program. [trim]: the
     * scape is cut down for a smaller tank, so spots on no rock stay out. Returns triangles drawn.
     */
    fun draw(p: ShaderProgram, rock: DrawPart?, cover: AlgaeCover, rockDraw: RockPoseDraw, rockVersion: Long, trim: Boolean, budget: Int): Int {
        if (budget <= 0 || rock?.prim == null) return 0
        val count = AlgaeVisuals.bubbleCount(cover, budget)
        if (count < 0.02f) return 0
        if (rock !== forPart) { candidates(rock); forPart = rock; builtMoved = -1L }
        if (spotCount == 0) return 0
        val m = mesh ?: Mesh.create(SPHERE.first, SPHERE.second, Mesh.SINGLE_INSTANCE).also { mesh = it; builtMoved = -1L }
        if (rockDraw.moved != builtMoved || rockVersion != builtVersion || trim != builtTrim) {
            builtMoved = rockDraw.moved; builtVersion = rockVersion; builtTrim = trim
            fill(rockDraw, trim)
            if (instCount > 0) m.updateInstances(inst, instCount)
        }
        val n = minOf(ceil(count).toInt(), instCount)
        if (n <= 0) return 0
        m.instanceCount = n
        p.use()
        p.set1f("uCount", count)
        m.draw()
        return m.triangles
    }

    /** The instance table: every spot at its rock's current pose (scale = radius, phase = growth rank, variant = colour). */
    private fun fill(rockDraw: RockPoseDraw, trim: Boolean) {
        var k = 0
        for (i in 0 until spotCount) {
            if (k >= MAX_SPHERES) break
            val o = i * SPOT
            val piece = spots[o + 7].toInt()
            if (trim && piece < 0) continue
            var x = spots[o]; var y = spots[o + 1]; var z = spots[o + 2]
            var nx = spots[o + 3]; var ny = spots[o + 4]; var nz = spots[o + 5]
            val pose = if (piece >= 0) rockDraw.matrix(piece) else null
            if (pose != null) {
                M.transformPoint(pose, x, y, z, tmp); x = tmp[0]; y = tmp[1]; z = tmp[2]
                val tx = pose[0] * nx + pose[4] * ny + pose[8] * nz
                val ty = pose[1] * nx + pose[5] * ny + pose[9] * nz
                val tz = pose[2] * nx + pose[6] * ny + pose[10] * nz
                val l = sqrt(tx * tx + ty * ty + tz * tz).coerceAtLeast(1e-5f)
                nx = tx / l; ny = ty / l; nz = tz / l
            }
            val q = k * Mesh.INSTANCE_FLOATS
            inst[q] = x; inst[q + 1] = y; inst[q + 2] = z; inst[q + 3] = spots[o + 6]
            // quaternion turning +Y onto the rock normal (180° about X when it points straight down)
            var qx = nz; var qy = 0f; var qz = -nx; var qw = 1f + ny
            if (qw < 1e-4f) { qx = 1f; qy = 0f; qz = 0f; qw = 0f }
            val ql = sqrt(qx * qx + qy * qy + qz * qz + qw * qw)
            inst[q + 4] = qx / ql; inst[q + 5] = qy / ql; inst[q + 6] = qz / ql; inst[q + 7] = qw / ql
            inst[q + 8] = k.toFloat(); inst[q + 9] = spots[o + 8]
            k++
        }
        instCount = k
    }

    /** Picks [MAX_SPHERES] shaded, creviced spots on [part] (weighted reservoir by triangle) and orders them by growth. */
    private fun candidates(part: DrawPart) {
        val prim = part.prim ?: return
        val v = prim.vertices; val idx = prim.indices; val st = GlbPrimitive.STRIDE
        val w = part.node.world
        val tris = idx.size / 3
        val key = FloatArray(tris)
        val a = FloatArray(3); val b = FloatArray(3); val c = FloatArray(3)
        for (t in 0 until tris) {
            val i0 = idx[t * 3] * st; val i1 = idx[t * 3 + 1] * st; val i2 = idx[t * 3 + 2] * st
            M.transformPoint(w, v[i0], v[i0 + 1], v[i0 + 2], a)
            M.transformPoint(w, v[i1], v[i1 + 1], v[i1 + 2], b)
            M.transformPoint(w, v[i2], v[i2 + 1], v[i2 + 2], c)
            val cy = (a[1] + b[1] + c[1]) / 3f
            if (cy < Camera.SAND_Y + 0.8f || cy > Camera.WATER_Y - 2f) { key[t] = -1f; continue }
            val ux = b[0] - a[0]; val uy = b[1] - a[1]; val uz = b[2] - a[2]
            val vx = c[0] - a[0]; val vy = c[1] - a[1]; val vz = c[2] - a[2]
            val cx = uy * vz - uz * vy; val cyy = uz * vx - ux * vz; val cz = ux * vy - uy * vx
            val area2 = sqrt(cx * cx + cyy * cyy + cz * cz)
            if (area2 < 1e-6f) { key[t] = -1f; continue }
            val ny = cyy / area2
            // COLOR_0 (rock_surface.py): r cavity, b open sky; bubbles want shade, crevices and undersides (A6)
            val cavity = (v[i0 + 8] + v[i1 + 8] + v[i2 + 8]) / 3f
            val sky = (v[i0 + 10] + v[i1 + 10] + v[i2 + 10]) / 3f
            val shade = 0.6f * (1f - sky) + 0.4f * cavity + (if (ny < 0.2f) 0.3f else 0f)
            val wt = 0.5f * area2 * (0.15f + shade * shade)
            // weighted reservoir key u^(1/w) (Efraimidis-Spirakis), deterministic per triangle
            key[t] = hash(t * 2 + 1).coerceIn(1e-6f, 1f).pow(1f / wt.coerceAtLeast(1e-6f))
        }
        val order = (0 until tris).filter { key[it] >= 0f }.sortedByDescending { key[it] }.take(MAX_SPHERES)
        val n = order.size
        val raw = FloatArray(n * SPOT)
        for ((k, t) in order.withIndex()) {
            val i0 = idx[t * 3] * st; val i1 = idx[t * 3 + 1] * st; val i2 = idx[t * 3 + 2] * st
            M.transformPoint(w, v[i0], v[i0 + 1], v[i0 + 2], a)
            M.transformPoint(w, v[i1], v[i1 + 1], v[i1 + 2], b)
            M.transformPoint(w, v[i2], v[i2 + 1], v[i2 + 2], c)
            // a random point in the triangle
            var r1 = hash(t * 7 + 3); var r2 = hash(t * 13 + 5)
            if (r1 + r2 > 1f) { r1 = 1f - r1; r2 = 1f - r2 }
            val o = k * SPOT
            for (j in 0 until 3) raw[o + j] = a[j] + (b[j] - a[j]) * r1 + (c[j] - a[j]) * r2
            val ux = b[0] - a[0]; val uy = b[1] - a[1]; val uz = b[2] - a[2]
            val vx = c[0] - a[0]; val vy = c[1] - a[1]; val vz = c[2] - a[2]
            var nx = uy * vz - uz * vy; var ny = uz * vx - ux * vz; var nz = ux * vy - uy * vx
            // the GLB winds outward; average with the vertex normals so a sliver triangle cannot flip it
            val sx = v[i0 + 3] + v[i1 + 3] + v[i2 + 3]; val sy = v[i0 + 4] + v[i1 + 4] + v[i2 + 4]; val sz = v[i0 + 5] + v[i1 + 5] + v[i2 + 5]
            M.transformPoint(w, sx, sy, sz, tmp)
            val wx = tmp[0] - w[12]; val wy = tmp[1] - w[13]; val wz = tmp[2] - w[14]
            if (nx * wx + ny * wy + nz * wz < 0f) { nx = -nx; ny = -ny; nz = -nz }
            val l = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-6f)
            raw[o + 3] = nx / l; raw[o + 4] = ny / l; raw[o + 5] = nz / l
            // mostly small cells, a few big ones (2-30 mm across, A6; 1-4 cm for the largest Valonia cells, A1)
            raw[o + 6] = 0.1f + 1.3f * hash(t * 17 + 11).pow(2.5f)
            raw[o + 7] = RockPieceDef.labelOf(DefaultScape.pieces, raw[o].toDouble(), raw[o + 1].toDouble(), raw[o + 2].toDouble(), RockPoseDraw.LABEL_MAX_CM).toFloat()
            raw[o + 8] = hash(t * 23 + 13)
        }
        // growth order: outward from the strongest few spots (the clusters' first cells), with a little jitter
        val seeds = minOf(SEEDS, n)
        val dist = FloatArray(n)
        for (k in 0 until n) {
            var best = Float.MAX_VALUE
            for (s in 0 until seeds) {
                val dx = raw[k * SPOT] - raw[s * SPOT]; val dy = raw[k * SPOT + 1] - raw[s * SPOT + 1]; val dz = raw[k * SPOT + 2] - raw[s * SPOT + 2]
                best = minOf(best, dx * dx + dy * dy + dz * dz)
            }
            dist[k] = sqrt(best) + 4f * hash(k * 31 + 7)
        }
        val grow = (0 until n).sortedBy { dist[it] }
        spots = FloatArray(n * SPOT)
        for ((k, src) in grow.withIndex()) System.arraycopy(raw, src * SPOT, spots, k * SPOT, SPOT)
        spotCount = n
    }

    fun onContextLost() { mesh = null; builtMoved = -1L }

    fun release() { mesh?.release(); mesh = null; forPart = null; spotCount = 0 }

    companion object {
        /** Spheres on High (half on Medium): 400 × 64 triangles ≈ 26k at full cover, the GSP mat's budget. */
        const val MAX_SPHERES = 400
        /** Clusters the cover grows out from. */
        const val SEEDS = 7
        private const val SPOT = 9

        private fun hash(i: Int): Float {
            var h = i * 0x27d4eb2d + 0x165667b1
            h = (h xor (h ushr 15)) * 0x2c1b3c6d
            h = (h xor (h ushr 12)) * 0x297a2d39
            h = h xor (h ushr 15)
            return (h ushr 8) / 16777216f
        }

        /** Unit sphere, 8 slices × 5 stacks (64 triangles), STRIDE-12 vertices (pos, normal, uv, colour). */
        val SPHERE: Pair<FloatArray, IntArray> by lazy {
            val slices = 8; val stacks = 5
            val verts = ArrayList<Float>()
            for (i in 0..stacks) {
                val th = Math.PI * i / stacks
                for (j in 0..slices) {
                    val ph = 2 * Math.PI * j / slices
                    val x = (sin(th) * cos(ph)).toFloat(); val y = cos(th).toFloat(); val z = (sin(th) * sin(ph)).toFloat()
                    verts += listOf(x, y, z, x, y, z, j.toFloat() / slices, i.toFloat() / stacks, 1f, 1f, 1f, 1f)
                }
            }
            val ind = ArrayList<Int>()
            val row = slices + 1
            for (i in 0 until stacks) for (j in 0 until slices) {
                val a0 = i * row + j; val b0 = a0 + row
                if (i != 0) { ind += a0; ind += b0; ind += a0 + 1 }
                if (i != stacks - 1) { ind += a0 + 1; ind += b0; ind += b0 + 1 }
            }
            verts.toFloatArray() to ind.toIntArray()
        }
    }
}
