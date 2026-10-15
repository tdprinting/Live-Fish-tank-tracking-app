package com.reeflive.core.geom

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Keeps the camera eye out of the tank's solid things: the rock (its [Sdf]), the sand bed (a height field: a base
 * sloping from [sandFront] at the front glass to [sandBack] at the back, plus the sim's dig / mound grid), axis-aligned
 * boxes (corals, equipment) and capsules (animal bodies). The glass is not in here; the camera clamps against it.
 *
 * Every obstacle has a clearance shell ([rockClearance], [sandClearance], [boxClearance], [bodyClearance]); "free" is the
 * distance to the nearest shell (negative inside one). Three operations, all allocation-free (camera hot path):
 * - [pushOut]: moves a point out of every shell it is in (iterative, along each obstacle's own normal).
 * - [cast]: sphere-traces a ray and returns how far it gets before touching a shell (the follow camera's line of sight
 *   from the fish to the eye). It can start inside an obstacle (a fish in a coral) and only counts once it is clear.
 * - [move]: carries the eye from where it is toward where it wants to be without ever passing through anything:
 *   it sphere-traces, stops at contact, slides along the surface for the rest of the move and pushes out at the end.
 *   Because the trace is conservative, a fast move can never pop through a thin rock spur.
 *
 * Plain Kotlin (shared with the web build); hosts fill the boxes, capsules and sand grid, then query.
 */
class EyeCollider(maxBoxes: Int = 128, maxCapsules: Int = 256) {
    /** The rock, or null before it has loaded. */
    var sdf: Sdf? = null

    // ---- sand ----
    /** Tank interior footprint (cm): the sand only applies over x in 0..[lengthX], z in 0..[depthZ]. */
    var lengthX = 121.9f
    var depthZ = 33f
    /** Undisturbed sand height (cm) at the front glass (z = [depthZ]) and at the back (z = 0). */
    var sandFront = 3.5f
    var sandBack = 6f
    private var sandH: FloatArray? = null
    private var sandNx = 0
    private var sandNz = 0
    private var sandCellX = 1f
    private var sandCellZ = 1f

    // ---- clearances (cm) ----
    var rockClearance = 3f
    var sandClearance = 1.5f
    var boxClearance = 1f
    var bodyClearance = 1f

    // ---- boxes: min xyz, max xyz ----
    private val box = FloatArray(maxBoxes * 6)
    private val boxCap = maxBoxes
    var boxCount = 0; private set

    // ---- capsules: a xyz, b xyz, radius; plus an owner tag ----
    private val cap = FloatArray(maxCapsules * 7)
    private val capOwner = IntArray(maxCapsules)
    private val capCap = maxCapsules
    var capsuleCount = 0; private set
    private var maxRadius = 0f

    // ---- broad phase: the boxes / capsules near the current query ----
    private val actBox = IntArray(maxBoxes)
    private var actBoxN = 0
    private val actCap = IntArray(maxCapsules)
    private var actCapN = 0
    /** Capsules with this owner tag are left out of the current query ([cast] from inside the followed animal). */
    private var skipOwner = Int.MIN_VALUE

    /** Sand dig / mound grid (cm offsets, x fastest, [nx] × [nz] cells of [cellX] × [cellZ]); null = flat base only. */
    fun setSandGrid(heights: FloatArray?, nx: Int, nz: Int, cellX: Float, cellZ: Float) {
        if (heights == null || nx < 2 || nz < 2 || heights.size < nx * nz) { sandH = null; return }
        sandH = heights; sandNx = nx; sandNz = nz; sandCellX = cellX; sandCellZ = cellZ
    }

    fun clearBoxes() { boxCount = 0 }

    fun addBox(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float) {
        if (boxCount >= boxCap || x1 < x0 || y1 < y0 || z1 < z0) return
        val o = boxCount * 6
        box[o] = x0; box[o + 1] = y0; box[o + 2] = z0; box[o + 3] = x1; box[o + 4] = y1; box[o + 5] = z1
        boxCount++
    }

    fun clearCapsules() { capsuleCount = 0; maxRadius = 0f }

    fun addCapsule(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, radius: Float, owner: Int = 0) {
        if (capsuleCount >= capCap) return
        val o = capsuleCount * 7
        cap[o] = ax; cap[o + 1] = ay; cap[o + 2] = az; cap[o + 3] = bx; cap[o + 4] = by; cap[o + 5] = bz; cap[o + 6] = radius
        capOwner[capsuleCount] = owner
        maxRadius = max(maxRadius, radius)
        capsuleCount++
    }

    /** Sand surface height (cm) at (x, z): the sloped base plus the grid offset (bilinear between cell centres). */
    fun sandY(x: Float, z: Float): Float {
        val k = ((depthZ - z) / depthZ).coerceIn(0f, 1f)
        var y = sandFront + (sandBack - sandFront) * k
        val h = sandH ?: return y
        val fx = (x / sandCellX - 0.5f).coerceIn(0f, sandNx - 1.001f)
        val fz = (z / sandCellZ - 0.5f).coerceIn(0f, sandNz - 1.001f)
        val i = fx.toInt(); val j = fz.toInt()
        val tx = fx - i; val tz = fz - j
        val a = h[j * sandNx + i]; val b = h[j * sandNx + i + 1]
        val c = h[(j + 1) * sandNx + i]; val d = h[(j + 1) * sandNx + i + 1]
        y += (a + (b - a) * tx) * (1 - tz) + (c + (d - c) * tx) * tz
        return y
    }

    private fun onSand(x: Float, z: Float) = x > -SAND_EDGE && x < lengthX + SAND_EDGE && z > -SAND_EDGE && z < depthZ + SAND_EDGE

    // ------------------------------------------------------------------ broad phase

    /** Picks the boxes and capsules that can matter inside the (padded) box around both points. */
    private fun gather(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float) {
        val pad = max(boxClearance, bodyClearance + maxRadius) + 1f
        val x0 = min(ax, bx) - pad; val y0 = min(ay, by) - pad; val z0 = min(az, bz) - pad
        val x1 = max(ax, bx) + pad; val y1 = max(ay, by) + pad; val z1 = max(az, bz) + pad
        actBoxN = 0
        for (i in 0 until boxCount) {
            val o = i * 6
            if (box[o] > x1 || box[o + 3] < x0 || box[o + 1] > y1 || box[o + 4] < y0 || box[o + 2] > z1 || box[o + 5] < z0) continue
            actBox[actBoxN++] = i
        }
        actCapN = 0
        for (i in 0 until capsuleCount) {
            if (capOwner[i] == skipOwner) continue
            val o = i * 7
            if (min(cap[o], cap[o + 3]) > x1 || max(cap[o], cap[o + 3]) < x0 ||
                min(cap[o + 1], cap[o + 4]) > y1 || max(cap[o + 1], cap[o + 4]) < y0 ||
                min(cap[o + 2], cap[o + 5]) > z1 || max(cap[o + 2], cap[o + 5]) < z0) continue
            actCap[actCapN++] = i
        }
    }

    // ------------------------------------------------------------------ distance

    private fun rockFree(x: Float, y: Float, z: Float): Float {
        val s = sdf ?: return BIG
        return s.distAt(x.toDouble(), y.toDouble(), z.toDouble()).toFloat() - rockClearance
    }

    /** Vertical gap above the sand shell, scaled down a little so it stays a safe tracing step on gentle slopes. */
    private fun sandFree(x: Float, y: Float, z: Float): Float =
        if (onSand(x, z)) (y - sandY(x, z) - sandClearance) * SAND_SLOPE_SAFETY else BIG

    private fun boxDist(i: Int, x: Float, y: Float, z: Float): Float {
        val o = i * 6
        val dx = max(box[o] - x, x - box[o + 3]); val dy = max(box[o + 1] - y, y - box[o + 4]); val dz = max(box[o + 2] - z, z - box[o + 5])
        val ox = max(dx, 0f); val oy = max(dy, 0f); val oz = max(dz, 0f)
        return sqrt(ox * ox + oy * oy + oz * oz) + min(max(dx, max(dy, dz)), 0f)
    }

    /** Distance from (x, y, z) to capsule [i]'s axis segment; the closest axis point goes to [closest]. */
    private fun segDist(i: Int, x: Float, y: Float, z: Float): Float {
        val o = i * 7
        val ax = cap[o]; val ay = cap[o + 1]; val az = cap[o + 2]
        val ux = cap[o + 3] - ax; val uy = cap[o + 4] - ay; val uz = cap[o + 5] - az
        val uu = ux * ux + uy * uy + uz * uz
        val t = if (uu < 1e-8f) 0f else (((x - ax) * ux + (y - ay) * uy + (z - az) * uz) / uu).coerceIn(0f, 1f)
        closest[0] = ax + ux * t; closest[1] = ay + uy * t; closest[2] = az + uz * t
        val dx = x - closest[0]; val dy = y - closest[1]; val dz = z - closest[2]
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
    private val closest = FloatArray(3)

    /** Distance from (x, y, z) to the nearest clearance shell among the gathered obstacles (negative inside one). */
    private fun freeGathered(x: Float, y: Float, z: Float): Float {
        var f = min(rockFree(x, y, z), sandFree(x, y, z))
        for (k in 0 until actBoxN) f = min(f, boxDist(actBox[k], x, y, z) - boxClearance)
        for (k in 0 until actCapN) {
            val i = actCap[k]
            f = min(f, segDist(i, x, y, z) - cap[i * 7 + 6] - bodyClearance)
        }
        return f
    }

    /** Distance from (x, y, z) to the nearest clearance shell (negative inside one). */
    fun free(x: Float, y: Float, z: Float): Float {
        skipOwner = Int.MIN_VALUE
        gather(x, y, z, x, y, z)
        return freeGathered(x, y, z)
    }

    // ------------------------------------------------------------------ queries

    /**
     * Moves [p] out of every clearance shell it is inside, each along its own obstacle's normal (a few rounds, so a push
     * out of one obstacle into another is resolved too). Returns true when it moved.
     */
    fun pushOut(p: FloatArray): Boolean {
        skipOwner = Int.MIN_VALUE
        gather(p[0], p[1], p[2], p[0], p[1], p[2])
        return pushOutGathered(p)
    }

    private fun pushOutGathered(p: FloatArray): Boolean {
        var moved = false
        for (round in 0 until PUSH_ROUNDS) {
            var any = false
            // rock, along the SDF gradient
            val s = sdf
            if (s != null) {
                val d = rockFree(p[0], p[1], p[2])
                if (d < 0f) {
                    rockNormal(s, p[0], p[1], p[2], nrm)
                    p[0] -= nrm[0] * d; p[1] -= nrm[1] * d; p[2] -= nrm[2] * d
                    any = true
                }
            }
            // sand, straight up
            if (onSand(p[0], p[2])) {
                val floor = sandY(p[0], p[2]) + sandClearance
                if (p[1] < floor) { p[1] = floor; any = true }
            }
            for (k in 0 until actBoxN) {
                val i = actBox[k]
                val d = boxDist(i, p[0], p[1], p[2]) - boxClearance
                if (d < 0f) { boxPush(i, p, d); any = true }
            }
            for (k in 0 until actCapN) {
                val i = actCap[k]
                val r = cap[i * 7 + 6] + bodyClearance
                val dist = segDist(i, p[0], p[1], p[2])
                if (dist < r) {
                    var nx = p[0] - closest[0]; var ny = p[1] - closest[1]; var nz = p[2] - closest[2]
                    if (dist < 1e-4f) { nx = 0f; ny = 1f; nz = 0f } else { nx /= dist; ny /= dist; nz /= dist }
                    val push = r - dist
                    p[0] += nx * push; p[1] += ny * push; p[2] += nz * push
                    any = true
                }
            }
            if (!any) break
            moved = true
        }
        return moved
    }
    private val nrm = FloatArray(3)

    private fun rockNormal(s: Sdf, x: Float, y: Float, z: Float, out: FloatArray) {
        val h = 0.5
        val xd = x.toDouble(); val yd = y.toDouble(); val zd = z.toDouble()
        var gx = (s.distAt(xd + h, yd, zd) - s.distAt(xd - h, yd, zd)).toFloat()
        var gy = (s.distAt(xd, yd + h, zd) - s.distAt(xd, yd - h, zd)).toFloat()
        var gz = (s.distAt(xd, yd, zd + h) - s.distAt(xd, yd, zd - h)).toFloat()
        val l = sqrt(gx * gx + gy * gy + gz * gz)
        if (l < 1e-6f) { gx = 0f; gy = 1f; gz = 0f } else { gx /= l; gy /= l; gz /= l }
        out[0] = gx; out[1] = gy; out[2] = gz
    }

    /** Pushes [p] out of box [i]'s shell ([d] < 0 is how deep it is): away from the nearest point, or out the nearest face. */
    private fun boxPush(i: Int, p: FloatArray, d: Float) {
        val o = i * 6
        val cx = p[0].coerceIn(box[o], box[o + 3]); val cy = p[1].coerceIn(box[o + 1], box[o + 4]); val cz = p[2].coerceIn(box[o + 2], box[o + 5])
        val dx = p[0] - cx; val dy = p[1] - cy; val dz = p[2] - cz
        val out = sqrt(dx * dx + dy * dy + dz * dz)
        if (out > 1e-4f) {
            val push = -d
            p[0] += dx / out * push; p[1] += dy / out * push; p[2] += dz / out * push
            return
        }
        // inside: leave by the nearest face, ending the clearance beyond it
        var best = p[0] - box[o]; var axis = 0; var sign = -1f
        var v = box[o + 3] - p[0]; if (v < best) { best = v; axis = 0; sign = 1f }
        v = p[1] - box[o + 1]; if (v < best) { best = v; axis = 1; sign = -1f }
        v = box[o + 4] - p[1]; if (v < best) { best = v; axis = 1; sign = 1f }
        v = p[2] - box[o + 2]; if (v < best) { best = v; axis = 2; sign = -1f }
        v = box[o + 5] - p[2]; if (v < best) { axis = 2; sign = 1f }
        p[axis] = if (sign > 0f) box[o + 3 + axis] + boxClearance else box[o + axis] - boxClearance
    }

    /**
     * Sphere-traces from (ox, oy, oz) along the unit direction (dx, dy, dz) and returns the distance (≤ [maxT]) at which
     * the point first touches a clearance shell. A start inside an obstacle is skipped over first (only what lies
     * beyond counts). Capsules tagged [ignoreOwner] are left out (the animal the ray starts from).
     */
    fun cast(ox: Float, oy: Float, oz: Float, dx: Float, dy: Float, dz: Float, maxT: Float, ignoreOwner: Int = Int.MIN_VALUE): Float {
        skipOwner = ignoreOwner
        gather(ox, oy, oz, ox + dx * maxT, oy + dy * maxT, oz + dz * maxT)
        var t = 0f
        var clear = false
        var i = 0
        while (t < maxT && i < MAX_STEPS) {
            val f = freeGathered(ox + dx * t, oy + dy * t, oz + dz * t)
            if (!clear) {
                if (f >= 0f) clear = true else { t += SKIP_STEP; i++; continue }
            }
            if (f < HIT_EPS) break
            t += max(f, MIN_STEP)
            i++
        }
        skipOwner = Int.MIN_VALUE
        return min(t, maxT)
    }

    /**
     * Carries [p] toward (tx, ty, tz) without passing through anything: trace to the first contact, slide the rest of
     * the way along the surface (up to [SLIDES] times), then push out. [p] should start clear (it is pushed out first).
     */
    fun move(p: FloatArray, tx: Float, ty: Float, tz: Float) {
        skipOwner = Int.MIN_VALUE
        gather(p[0], p[1], p[2], tx, ty, tz)
        pushOutGathered(p)
        var gx = tx; var gy = ty; var gz = tz
        for (slide in 0 until SLIDES) {
            var dx = gx - p[0]; var dy = gy - p[1]; var dz = gz - p[2]
            val len = sqrt(dx * dx + dy * dy + dz * dz)
            if (len < 1e-4f) break
            dx /= len; dy /= len; dz /= len
            val t = traceMove(p[0], p[1], p[2], dx, dy, dz, len)
            p[0] += dx * t; p[1] += dy * t; p[2] += dz * t
            if (t >= len - 1e-4f) break
            // blocked: keep only the part of the remaining move along the surface
            freeNormal(p[0], p[1], p[2], nrm)
            var rx = gx - p[0]; var ry = gy - p[1]; var rz = gz - p[2]
            val into = rx * nrm[0] + ry * nrm[1] + rz * nrm[2]
            if (into < 0f) { rx -= nrm[0] * into; ry -= nrm[1] * into; rz -= nrm[2] * into }
            gx = p[0] + rx; gy = p[1] + ry; gz = p[2] + rz
        }
        pushOutGathered(p)
    }

    /** How far along the move the point can go before it would sink more than [PEN_TOL] into a shell. */
    private fun traceMove(ox: Float, oy: Float, oz: Float, dx: Float, dy: Float, dz: Float, len: Float): Float {
        var t = 0f
        var f = freeGathered(ox, oy, oz)
        var i = 0
        while (i < MAX_STEPS) {
            val nt = min(len, t + max(f, MIN_STEP))
            val nf = freeGathered(ox + dx * nt, oy + dy * nt, oz + dz * nt)
            if (nf < -PEN_TOL && nf < f) return t
            t = nt; f = nf
            if (t >= len) return len
            i++
        }
        return t
    }

    /** Outward normal of the nearest shell at (x, y, z) (central differences of [freeGathered]). */
    private fun freeNormal(x: Float, y: Float, z: Float, out: FloatArray) {
        val h = NORMAL_H
        var gx = freeGathered(x + h, y, z) - freeGathered(x - h, y, z)
        var gy = freeGathered(x, y + h, z) - freeGathered(x, y - h, z)
        var gz = freeGathered(x, y, z + h) - freeGathered(x, y, z - h)
        val l = sqrt(gx * gx + gy * gy + gz * gz)
        if (l < 1e-6f) { gx = 0f; gy = 1f; gz = 0f } else { gx /= l; gy /= l; gz /= l }
        out[0] = gx; out[1] = gy; out[2] = gz
    }

    companion object {
        private const val BIG = 1e6f
        /** The sand shell reaches this far past the glass footprint (cm), so the eye cannot slip under it at an edge. */
        private const val SAND_EDGE = 0.5f
        /** Vertical gap → tracing step: a little under 1 for the gentle slopes of the bed. */
        private const val SAND_SLOPE_SAFETY = 0.85f
        private const val PUSH_ROUNDS = 4
        private const val SLIDES = 3
        private const val MAX_STEPS = 96
        private const val MIN_STEP = 0.15f
        private const val SKIP_STEP = 0.25f
        private const val HIT_EPS = 0.02f
        /** A move may graze a shell by this much (cm) before it counts as blocked; the final push-out removes it. */
        private const val PEN_TOL = 0.3f
        private const val NORMAL_H = 0.25f

        /**
         * Distance (cm) from the eye to a corner of the near plane: [near] × √(1 + tan²(fov/2) · (1 + aspect²)). The eye
         * must stay further than this from anything solid or the near plane cuts into it.
         */
        fun nearCornerDistance(near: Float, fovYDeg: Float, aspect: Float): Float {
            val t = kotlin.math.tan(fovYDeg * 0.5f * kotlin.math.PI.toFloat() / 180f)
            return near * sqrt(1f + t * t * (1f + aspect * aspect))
        }
    }
}
