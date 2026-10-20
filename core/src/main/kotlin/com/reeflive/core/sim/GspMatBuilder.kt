package com.reeflive.core.sim

import com.reeflive.core.geom.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A green star polyp mat and its polyps, built from the rock mesh that is actually drawn (the renderer's rockscape
 * or a dry rock from the aquascape builder), so the mat lies on the rock you see and no polyp grows into it.
 *
 * - The mat is a copy of the rock's own triangles around the colony. Each vertex carries its distance from where
 *   the colony was placed, measured along the rock surface (Dijkstra over the welded mesh edges): a mat spreads over
 *   the rock it touches and never jumps through rock or across open water. Shaded rock (the mesh's open-sky term,
 *   COLOR_0.b) counts up to [SHADE_COST] times longer, so the mat runs out over lit faces first and creeps slowly
 *   into caves and under ledges (GSP is photosynthetic; research 41_green_star_polyps.md).
 * - Polyps are spread over those triangles by dart throwing at [spacing] cm, sorted by that distance, each pointing
 *   out of the surface. A polyp whose tentacles would reach into rock, sand or glass (the [clearance] field, its own
 *   frame) is shortened to fit or left out.
 *
 * Mesh layout: STRIDE-12 vertices (pos3 nrm3 uv2 col4) with uv.x = distance along the rock (cm); instances in the
 * renderer's polyp buffer layout (pos3, scale, quat xyzw, phase, packed) with packed = round(dist·16) + variant.
 */
object GspMatBuilder {
    const val STRIDE = 12
    const val INSTANCE_FLOATS = 10
    /** Farthest along the rock a mat is laid out (the growth model's rMax 30 cm plus the margin's runners). */
    const val REACH_CM = 34.0
    /** Distance cost through fully shaded rock relative to open, lit rock. */
    const val SHADE_COST = 2.5
    /** The colony's placement must lie this close to the mesh (cm), or there is no rock to grow on. */
    const val SNAP_CM = 3.0
    /** Starting on shaded rock counts like starting this much farther away (cm). */
    const val START_LIGHT_CM = 2.0
    /** Long-tentacle polyp height at scale 1 (corals.py GSP_LONG_TENT 1.7 cm + stalk) and the clearance it needs. */
    const val POLYP_CM = 2.0
    const val UNREACHED = 1.0e4f
    private val FIT_STEPS = doubleArrayOf(0.4, 0.8, 1.2, 1.6, 2.0, 2.4)

    class Result(
        val vertices: FloatArray,
        val indices: IntArray,
        val instances: FloatArray,
        /** Distance along the rock of each instance, ascending (instances are sorted by it). */
        val instanceDist: FloatArray,
        /** Farthest reached rock, cm. */
        val reach: Float,
    ) {
        val instanceCount: Int get() = instanceDist.size
        /** Instances within [r] cm of the placement (a prefix of the sorted table). */
        fun countWithin(r: Float): Int {
            var lo = 0; var hi = instanceDist.size
            while (lo < hi) { val m = (lo + hi) ushr 1; if (instanceDist[m] <= r) lo = m + 1 else hi = m }
            return lo
        }
    }

    /**
     * [vertices]/[indices]: the rock mesh (STRIDE-12, COLOR_0.b open sky), transformed by column-major [world] (null:
     * as is) into the frame [anchor] and [normal] are given in. [keep] limits the mat to part of the mesh (one loose
     * rock). [clearance] is the distance to the nearest rock, sand or glass in that frame (cm).
     */
    fun build(
        vertices: FloatArray, indices: IntArray, world: FloatArray?, anchor: Vec3, normal: Vec3, seed: Int,
        spacing: Double, maxPolyps: Int, clearance: (Double, Double, Double) -> Double,
        keep: ((Double, Double, Double) -> Boolean)? = null,
        /** The placed colony's own radius (cm): light only steers growth beyond it. */
        startRadius: Double = CoralGrowth.growth(CoralKind.GSP).r0,
    ): Result? {
        val nv = vertices.size / STRIDE
        // ---- vertices near the colony, welded by position
        val reach2 = (REACH_CM + 2) * (REACH_CM + 2)
        val weld = HashMap<Long, Int>()
        val map = IntArray(nv) { -1 }
        val px = ArrayList<Double>(); val py = ArrayList<Double>(); val pz = ArrayList<Double>()
        val nx = ArrayList<Double>(); val ny = ArrayList<Double>(); val nz = ArrayList<Double>()
        val sky = ArrayList<Double>(); val cav = ArrayList<Double>(); val cnt = ArrayList<Int>()
        for (i in 0 until nv) {
            val o = i * STRIDE
            var x = vertices[o].toDouble(); var y = vertices[o + 1].toDouble(); var z = vertices[o + 2].toDouble()
            var vx = vertices[o + 3].toDouble(); var vy = vertices[o + 4].toDouble(); var vz = vertices[o + 5].toDouble()
            if (world != null) {
                val w = world
                val tx = w[0] * x + w[4] * y + w[8] * z + w[12]; val ty = w[1] * x + w[5] * y + w[9] * z + w[13]; val tz = w[2] * x + w[6] * y + w[10] * z + w[14]
                val ux = w[0] * vx + w[4] * vy + w[8] * vz; val uy = w[1] * vx + w[5] * vy + w[9] * vz; val uz = w[2] * vx + w[6] * vy + w[10] * vz
                x = tx; y = ty; z = tz; vx = ux; vy = uy; vz = uz
            }
            val dx = x - anchor.x; val dy = y - anchor.y; val dz = z - anchor.z
            if (dx * dx + dy * dy + dz * dz > reach2) continue
            if (keep != null && !keep(x, y, z)) continue
            val key = (round(x * 500).toLong() and 0x1FFFFF) or ((round(y * 500).toLong() and 0x1FFFFF) shl 21) or ((round(z * 500).toLong() and 0x1FFFFF) shl 42)
            val j = weld.getOrPut(key) {
                px += x; py += y; pz += z; nx += 0.0; ny += 0.0; nz += 0.0; sky += 0.0; cav += 0.0; cnt += 0
                px.size - 1
            }
            nx[j] = nx[j] + vx; ny[j] = ny[j] + vy; nz[j] = nz[j] + vz
            sky[j] = sky[j] + vertices[o + 10]; cav[j] = cav[j] + vertices[o + 8]; cnt[j] = cnt[j] + 1
            map[i] = j
        }
        val n = px.size
        if (n < 3) return null
        val skyA = DoubleArray(n) { (sky[it] / cnt[it]).coerceIn(0.0, 1.0) }
        val nrm = DoubleArray(3 * n)
        for (j in 0 until n) {
            val l = sqrt(nx[j] * nx[j] + ny[j] * ny[j] + nz[j] * nz[j]).coerceAtLeast(1e-9)
            nrm[3 * j] = nx[j] / l; nrm[3 * j + 1] = ny[j] / l; nrm[3 * j + 2] = nz[j] / l
        }
        // ---- triangles and edges
        val tris = ArrayList<Int>()
        val adj = Array(n) { IntArrayList() }
        for (t in 0 until indices.size / 3) {
            val a = map[indices[3 * t]]; val b = map[indices[3 * t + 1]]; val c = map[indices[3 * t + 2]]
            if (a < 0 || b < 0 || c < 0 || a == b || b == c || a == c) continue
            tris += a; tris += b; tris += c
            adj[a].addUnique(b); adj[a].addUnique(c); adj[b].addUnique(a); adj[b].addUnique(c); adj[c].addUnique(a); adj[c].addUnique(b)
        }
        if (tris.isEmpty()) return null
        // ---- start: near the placement, on the best-lit rock there (a placement point snapped to the underside of a
        // ledge starts the colony on the lit face beside it), facing roughly the placement's way
        var start = -1; var best = Double.MAX_VALUE
        for (j in 0 until n) {
            if (adj[j].size == 0) continue
            val dx = px[j] - anchor.x; val dy = py[j] - anchor.y; val dz = pz[j] - anchor.z
            val d2 = dx * dx + dy * dy + dz * dz
            if (d2 > SNAP_CM * SNAP_CM) continue
            val facing = nrm[3 * j] * normal.x + nrm[3 * j + 1] * normal.y + nrm[3 * j + 2] * normal.z
            val score = sqrt(d2) + START_LIGHT_CM * (1 - skyA[j]) + (if (facing < -0.2) START_LIGHT_CM else 0.0)
            if (score < best) { best = score; start = j }
        }
        if (start < 0) return null
        // ---- distance along the rock (light-weighted)
        val dist = DoubleArray(n) { Double.POSITIVE_INFINITY }
        val heap = MinHeap(n)
        dist[start] = 0.0; heap.push(start, 0.0)
        while (heap.size > 0) {
            val i = heap.pop()
            val di = dist[i]
            if (di > REACH_CM) break
            val a = adj[i]
            for (k in 0 until a.size) {
                val j = a[k]
                val ex = px[j] - px[i]; val ey = py[j] - py[i]; val ez = pz[j] - pz[i]
                // the colony as placed covers its starting size whatever the light; growth beyond it favours light
                val shade = if (di < startRadius) 0.0 else 1.0 - 0.5 * (skyA[i] + skyA[j])
                val nd = di + sqrt(ex * ex + ey * ey + ez * ez) * (1.0 + (SHADE_COST - 1.0) * shade)
                if (nd < dist[j]) { dist[j] = nd; heap.push(j, nd) }
            }
        }
        // ---- the mat: triangles with any reached corner
        val used = IntArray(n) { -1 }
        val outIdx = ArrayList<Int>()
        var reach = 0.0
        val keptTris = ArrayList<Int>()
        for (t in 0 until tris.size / 3) {
            val a = tris[3 * t]; val b = tris[3 * t + 1]; val c = tris[3 * t + 2]
            if (min(dist[a], min(dist[b], dist[c])) > REACH_CM) continue
            keptTris += t
            for (v in intArrayOf(a, b, c)) {
                if (used[v] < 0) used[v] = 0
                if (dist[v].isFinite()) reach = max(reach, dist[v])
            }
        }
        var m = 0
        for (j in 0 until n) if (used[j] >= 0) used[j] = m++
        val outV = FloatArray(m * STRIDE)
        for (j in 0 until n) {
            val k = used[j]; if (k < 0) continue
            val o = k * STRIDE
            outV[o] = px[j].toFloat(); outV[o + 1] = py[j].toFloat(); outV[o + 2] = pz[j].toFloat()
            outV[o + 3] = nrm[3 * j].toFloat(); outV[o + 4] = nrm[3 * j + 1].toFloat(); outV[o + 5] = nrm[3 * j + 2].toFloat()
            outV[o + 6] = if (dist[j].isFinite()) dist[j].toFloat() else UNREACHED; outV[o + 7] = skyA[j].toFloat()
            outV[o + 8] = (cav[j] / cnt[j]).toFloat(); outV[o + 9] = 0f; outV[o + 10] = skyA[j].toFloat(); outV[o + 11] = 1f
        }
        for (t in keptTris) for (q in 0 until 3) outIdx += used[tris[3 * t + q]]
        // ---- polyps
        val inst = polyps(tris, keptTris, px, py, pz, nrm, dist, seed, spacing, maxPolyps, clearance)
        return Result(outV, outIdx.toIntArray(), inst.first, inst.second, reach.toFloat())
    }

    private fun polyps(
        tris: List<Int>, kept: List<Int>, px: List<Double>, py: List<Double>, pz: List<Double>, nrm: DoubleArray,
        dist: DoubleArray, seed: Int, spacing0: Double, maxPolyps: Int, clearance: (Double, Double, Double) -> Double,
    ): Pair<FloatArray, FloatArray> {
        val triArea = DoubleArray(kept.size)
        val triNear = DoubleArray(kept.size)
        for ((k, t) in kept.withIndex()) {
            val a = tris[3 * t]; val b = tris[3 * t + 1]; val c = tris[3 * t + 2]
            val ux = px[b] - px[a]; val uy = py[b] - py[a]; val uz = pz[b] - pz[a]
            val vx = px[c] - px[a]; val vy = py[c] - py[a]; val vz = pz[c] - pz[a]
            val cx = uy * vz - uz * vy; val cy = uz * vx - ux * vz; val cz = ux * vy - uy * vx
            triArea[k] = 0.5 * sqrt(cx * cx + cy * cy + cz * cz)
            triNear[k] = min(dist[a], min(dist[b], dist[c]))
        }
        // nearest rock first: past the polyp budget only the far rim of a very large mat stays bare
        val order = kept.indices.sortedBy { triNear[it] }
        val spacing = spacing0
        val rng = Lcg(seed)
        val cell = spacing
        val grid = HashMap<Long, IntArrayList>()
        val cand = ArrayList<DoubleArray>()   // x y z nx ny nz dist
        val minD2 = (0.85 * spacing) * (0.85 * spacing)
        fun key(x: Double, y: Double, z: Double): Long =
            (floor(x / cell).toLong() and 0x1FFFFF) or ((floor(y / cell).toLong() and 0x1FFFFF) shl 21) or ((floor(z / cell).toLong() and 0x1FFFFF) shl 42)
        for (k in order) {
            if (cand.size >= maxPolyps) break
            val t = kept[k]
            val a = tris[3 * t]; val b = tris[3 * t + 1]; val c = tris[3 * t + 2]
            var tries = triArea[k] / (spacing * spacing) * 2.2
            while (tries > 0) {
                if (tries < 1 && rng.next() > tries) break
                tries -= 1
                var u = rng.next(); var v = rng.next()
                if (u + v > 1) { u = 1 - u; v = 1 - v }
                val w = 1 - u - v
                val da = dist[a]; val db = dist[b]; val dc = dist[c]
                if (!da.isFinite() || !db.isFinite() || !dc.isFinite()) continue
                val d = w * da + u * db + v * dc
                if (d > REACH_CM) continue
                val x = w * px[a] + u * px[b] + v * px[c]; val y = w * py[a] + u * py[b] + v * py[c]; val z = w * pz[a] + u * pz[b] + v * pz[c]
                var nnx = w * nrm[3 * a] + u * nrm[3 * b] + v * nrm[3 * c]
                var nny = w * nrm[3 * a + 1] + u * nrm[3 * b + 1] + v * nrm[3 * c + 1]
                var nnz = w * nrm[3 * a + 2] + u * nrm[3 * b + 2] + v * nrm[3 * c + 2]
                val nl = sqrt(nnx * nnx + nny * nny + nnz * nnz).coerceAtLeast(1e-9); nnx /= nl; nny /= nl; nnz /= nl
                val gx = floor(x / cell).toLong(); val gy = floor(y / cell).toLong(); val gz = floor(z / cell).toLong()
                var clash = false
                loop@ for (ix in -1..1) for (iy in -1..1) for (iz in -1..1) {
                    val l = grid[((gx + ix) and 0x1FFFFF) or (((gy + iy) and 0x1FFFFF) shl 21) or (((gz + iz) and 0x1FFFFF) shl 42)] ?: continue
                    for (q in 0 until l.size) {
                        val o = cand[l[q]]
                        val ex = o[0] - x; val ey = o[1] - y; val ez = o[2] - z
                        if (ex * ex + ey * ey + ez * ez < minD2) { clash = true; break@loop }
                    }
                }
                if (clash) continue
                grid.getOrPut(key(x, y, z)) { IntArrayList() }.add(cand.size)
                cand += doubleArrayOf(x, y, z, nnx, nny, nnz, d)
            }
        }
        // point each polyp out of the rock (a little toward the light), fit it to the room it has
        val out = ArrayList<DoubleArray>()
        for (p in cand) {
            var ax = p[3]; var ay = p[4] + 0.15; var az = p[5]
            val al = sqrt(ax * ax + ay * ay + az * az).coerceAtLeast(1e-9); ax /= al; ay /= al; az /= al
            if (ax * p[3] + ay * p[4] + az * p[5] < 0.6) { ax = p[3]; ay = p[4]; az = p[5] }
            // room straight out and around the crown: at height h the tentacles spread about 0.4·h from the axis.
            // Measured against the field at the polyp's own foot, so the coarse field's offset from the drawn mesh
            // cancels out.
            val c0 = clearance(p[0], p[1], p[2])
            var fitH = POLYP_CM * 1.2
            for (h in FIT_STEPS) {
                if (clearance(p[0] + ax * h, p[1] + ay * h, p[2] + az * h) - c0 < 0.4 * h - 0.15) { fitH = h - 0.4; break }
            }
            val fit = (fitH / POLYP_CM).coerceIn(0.0, 1.0)
            if (fit < 0.3) continue
            val s = (0.95 + 0.3 * rng.next()) * fit
            out += doubleArrayOf(p[0] + p[3] * 0.03, p[1] + p[4] * 0.03, p[2] + p[5] * 0.03, s, ax, ay, az, rng.next() * 2 * PI, p[6], rng.next())
        }
        out.sortBy { it[8] }
        val inst = FloatArray(out.size * INSTANCE_FLOATS)
        val dists = FloatArray(out.size)
        val flowX = -0.6; val flowY = -0.2; val flowZ = 0.7
        for ((k, p) in out.withIndex()) {
            val o = k * INSTANCE_FLOATS
            inst[o] = p[0].toFloat(); inst[o + 1] = p[1].toFloat(); inst[o + 2] = p[2].toFloat(); inst[o + 3] = p[3].toFloat()
            // +Y -> axis, then a twist about it
            val q = alignY(p[4], p[5], p[6], p[7])
            inst[o + 4] = q[0]; inst[o + 5] = q[1]; inst[o + 6] = q[2]; inst[o + 7] = q[3]
            // sway phase coherent across the mat (corals.py gsp: dot(p, flow) / 25)
            val ph = (p[0] * flowX + p[1] * flowY + p[2] * flowZ) / 25.0 + 0.03 * (p[9] - 0.5)
            inst[o + 8] = (ph - floor(ph)).toFloat()
            inst[o + 9] = (round(p[8] * 16).toFloat() + (p[9] * 0.99).toFloat())
            dists[k] = p[8].toFloat()
        }
        return inst to dists
    }

    /** Quaternion (x, y, z, w) turning +Y onto unit (ax, ay, az) after a [twist] about +Y. */
    fun alignY(ax: Double, ay: Double, az: Double, twist: Double): FloatArray {
        // q1: shortest arc +Y -> a
        var qx: Double; var qy: Double; var qz: Double; var qw: Double
        if (ay < -0.9999) { qx = 1.0; qy = 0.0; qz = 0.0; qw = 0.0 } else {
            // cross(Y, a) = (az, 0, -ax), w = 1 + dot
            qx = az; qy = 0.0; qz = -ax; qw = 1.0 + ay
            val l = sqrt(qx * qx + qz * qz + qw * qw); qx /= l; qz /= l; qw /= l
        }
        // q2: twist about +Y
        val ts = sin(twist / 2); val tc = cos(twist / 2)
        // q1 * q2 with q2 = (0, ts, 0, tc)
        val rx = qw * 0 + qx * tc + qy * 0 - qz * ts
        val ry = qw * ts - qx * 0 + qy * tc + qz * 0
        val rz = qw * 0 + qx * ts - qy * 0 + qz * tc
        val rw = qw * tc - qx * 0 - qy * ts - qz * 0
        return floatArrayOf(rx.toFloat(), ry.toFloat(), rz.toFloat(), rw.toFloat())
    }

    private class IntArrayList {
        var data = IntArray(6); var size = 0
        operator fun get(i: Int) = data[i]
        fun add(v: Int) { if (size == data.size) data = data.copyOf(size * 2); data[size++] = v }
        fun addUnique(v: Int) { for (i in 0 until size) if (data[i] == v) return; add(v) }
    }

    /** Binary min-heap of (node, key) with lazy deletion (a node may be pushed again with a lower key). */
    private class MinHeap(cap: Int) {
        private var node = IntArray(max(cap, 16)); private var key = DoubleArray(max(cap, 16))
        var size = 0; private set
        fun push(n: Int, k: Double) {
            if (size == node.size) { node = node.copyOf(size * 2); key = key.copyOf(size * 2) }
            var i = size++
            while (i > 0) {
                val p = (i - 1) / 2
                if (key[p] <= k) break
                node[i] = node[p]; key[i] = key[p]; i = p
            }
            node[i] = n; key[i] = k
        }
        fun pop(): Int {
            val top = node[0]
            val ln = node[--size]; val lk = key[size]
            var i = 0
            while (true) {
                val l = 2 * i + 1
                if (l >= size) break
                val c = if (l + 1 < size && key[l + 1] < key[l]) l + 1 else l
                if (key[c] >= lk) break
                node[i] = node[c]; key[i] = key[c]; i = c
            }
            if (size > 0) { node[i] = ln; key[i] = lk }
            return top
        }
    }

    /** Small deterministic generator (same polyps on every device and in the web build). */
    private class Lcg(seed: Int) {
        private var s = (seed.toLong() xor 0x5DEECE66DL) and 0xFFFFFFFFFFFFL
        fun next(): Double {
            s = (s * 0x5DEECE66DL + 0xBL) and 0xFFFFFFFFFFFFL
            return (s ushr 22).toDouble() / (1L shl 26).toDouble()
        }
    }

}
