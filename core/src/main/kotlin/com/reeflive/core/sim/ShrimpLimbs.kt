package com.reeflive.core.sim

import com.reeflive.core.geom.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The two shrimp's limbs (research 17, 19). [out] layout, [ENTRIES] entries:
 *  - 0: (stride phase, stepping 0..1, leg rock / claw display signal, grooming), the procedural limb signals;
 *  - 1..6: walking leg k = (pair − 2)·2 + (right ? 1 : 0): (swing lift × [LIFT_X], model-space foot offset xyz). The foot
 *    is planted on rock, sand or glass (or on a client's body while cleaning) and held there through stance, then
 *    lifted and swung to the next foothold in an alternating tripod (L2 R3 L4 / R2 L3 R4, shore crab analogue,
 *    research 19); the renderer bends the leg from its rest tip onto that point;
 *  - 7..10: picking leg pair p (P1, P2), side s: (0, model-space claw-tip offset xyz). The chelae reach to
 *    the skin or rock, pluck, and carry the morsel back to the mouth, left and right alternating;
 *  - 11: (picking weight, planted weight, left antenna lift, right antenna lift): the antennae lift away from rock ahead and
 *    drape down over a client while cleaning;
 *  - 12: (0, drop, pitch, roll), the body seated on its legs (model units; y −= drop + pitch·(x − [SEAT_X]) + roll·z).
 *
 * The brains call nothing here: [step] runs from Steering with the stride clock, [plant] from the Simulation after
 * steering with the surface (and [Organism.ridingOn], the fish being cleaned).
 */
class ShrimpLimbs : LimbRig(ENTRIES) {
    private var signal = 0.0
    private var groom = 0.0

    private val anchor = arrayOfNulls<Vec3>(6)
    private val from = arrayOfNulls<Vec3>(6)
    private val swing = DoubleArray(6) { -1.0 }
    private val held = DoubleArray(6)
    private val shuffleAt = DoubleArray(6) { 2.0 + 1.7 * it }
    private var planted = 0.0
    private var time = 0.0
    private var seed = 0x2545F491L

    private val pickPhase = DoubleArray(4) { if (it % 2 == 0) 0.0 else 0.5 }
    private val pickSpot = arrayOfNulls<Vec3>(4)
    private var picking = 0.0
    private var riderPos: Vec3? = null
    private var seatD = 0.0
    private var seatP = 0.0
    private var seatR = 0.0
    private var antL = 0.0
    private var antR = 0.0

    override fun step(o: Organism, dt: Double, speed: Double) {
        val k = min(1.0, dt * EASE_RATE)
        signal += (max(o.legRock, o.clawDisplay) - signal) * k
        groom += (o.groom - groom) * k
        put(0, frac(o.walkPhase), o.walkAmt, signal, groom)
    }

    /** Plants the walking legs, strokes the picking legs and steers the antennae for this tick. */
    fun plant(o: Organism, surf: CrawlSurface, dt: Double) {
        time += dt
        val legs = if (o.brainKind == BrainKind.BANDED_SHRIMP) BANDED_LEGS else SKUNK_LEGS
        val picks = if (o.brainKind == BrainKind.BANDED_SHRIMP) BANDED_PICKS else SKUNK_PICKS
        val L = o.lengthCm
        val f = o.heading.normalized()
        val up = o.up.rejectFrom(f).normalized(Vec3.Y)
        val rt = (f cross up).normalized(Vec3.Z)
        val rider = o.ridingOn?.takeIf { it.alive }
        val grounded = o.alive && o.attached
        val ease = min(1.0, dt * 8.0)
        // footholds on a client travel with it (its turns are slow while it poses)
        val rp = riderPos
        if (rider != null && rp != null) {
            val d = rider.pos - rp
            for (i in 0 until 6) { anchor[i] = anchor[i]?.plus(d); from[i] = from[i]?.plus(d) }
            for (j in 0 until 4) pickSpot[j] = pickSpot[j]?.plus(d)
        }
        riderPos = rider?.pos
        planted += ((if (grounded) 1.0 else 0.0) - planted) * ease
        fitSeat(o, surf, rider, legs, f, up, rt, L, if (grounded) min(1.0, dt * SEAT_RATE) else ease, grounded)

        // ---- walking legs ----
        val stepCm = STEP_BL * L
        var swingingA = false; var swingingB = false
        for (i in 0 until 6) if (swing[i] >= 0) { if (GROUP_A[i]) swingingA = true else swingingB = true }
        for (i in 0 until 6) {
            val base = (i / 2) * 3
            if (legs[base + 1] > 0.0) { put(1 + i, 0.0, 0.0, 0.0, 0.0); continue }   // no such leg (banded pair 2)
            val side = if (i % 2 == 1) 1.0 else -1.0
            val rest = world(o, f, up, rt, legs[base], legs[base + 1], legs[base + 2] * side)
            val home = if (!grounded) null else if (rider != null) grip(rider, rest, up, L)
                else surf.dist(rest).let { d -> if (d > REACH_BL * L) null else project(surf, rest - surf.normal(rest) * d) }
            if (home == null) {
                anchor[i] = null; swing[i] = -1.0; held[i] = max(0.0, held[i] - ease)
                put(1 + i, 0.0, 0.0, 0.0, 0.0); continue
            }
            held[i] = min(1.0, held[i] + ease)
            val a = anchor[i]
            var foot: Vec3
            var lift = 0.0
            if (a == null) {
                anchor[i] = home; swing[i] = -1.0; foot = home
            } else if (swing[i] < 0 && a.distanceTo(home) > 3 * stepCm) {
                // left far behind (a hop, a quick turn): step straight over, whichever tripod is down
                swing[i] = 0.0; from[i] = a; foot = a
            } else if (swing[i] >= 0) {
                swing[i] += dt / SWING_S
                val to = home + (home - from[i]!!).normalized(f) * (0.35 * stepCm)
                val s = min(swing[i], 1.0)
                val e = s * s * (3 - 2 * s)
                lift = sin(PI * s)
                foot = from[i]!!.lerp(to, e) + up * (LIFT_BL * L * lift)
                if (swing[i] >= 1.0) { swing[i] = -1.0; anchor[i] = to; foot = to; lift = 0.0 }
            } else {
                foot = a
                val groupA = GROUP_A[i]
                val otherSwinging = if (groupA) swingingB else swingingA
                val idle = time > shuffleAt[i]
                if (!otherSwinging && (a.distanceTo(home) > stepCm || idle)) {
                    swing[i] = 0.0; from[i] = a
                    if (groupA) swingingA = true else swingingB = true
                    shuffleAt[i] = time + 3.0 + 6.0 * rand()
                }
            }
            // a foot never reaches further from its hip than the leg is long
            val hip = world(o, f, up, rt, legs[base] + HIP_DX, HIP_Y, HIP_Z * side)
            val reach = foot - hip
            val maxLen = LEG_BL * L
            if (reach.length() > maxLen) foot = hip + reach * (maxLen / reach.length())
            putOffset(1 + i, LIFT_X * lift, foot - rest, f, up, rt, L, 0.3, held[i])
        }

        // ---- picking legs: reach, pluck, carry to the mouth ----
        val want = when {
            rider != null -> 1.0
            o.attached && o.mouth > 0.25 -> 0.8
            else -> 0.0
        }
        picking += (want - picking) * ease
        val hz = if (rider != null) PICK_HZ else PICK_HZ * 0.8
        for (j in 0 until 4) {
            val p = j / 2
            val side = if (j % 2 == 1) 1.0 else -1.0
            val base = p * 3
            val restTip = world(o, f, up, rt, picks[base], picks[base + 1], picks[base + 2] * side)
            val prev = pickPhase[j]
            pickPhase[j] = frac(prev + dt * hz * (if (p == 0) 1.0 else 0.85))
            if (pickPhase[j] < prev || pickSpot[j] == null) {
                // a new stroke: choose a spot on the skin (or rock) a little ahead of and beneath the claw
                val jx = (0.02 + 0.07 * rand()) * L
                val jz = (rand() - 0.5) * 0.08 * L
                val probe = restTip + f * jx + rt * jz + up * (0.1 * L)
                pickSpot[j] = if (rider != null) onBody(rider, probe, -up, 0.5 * L)
                    else surf.cast(probe, -up, 0.4 * L).let { t -> if (t < 0) null else probe - up * t }
            }
            val spot = pickSpot[j] ?: (restTip + f * (0.04 * L) - up * (0.04 * L))
            val mouth = world(o, f, up, rt, MOUTH_X, MOUTH_Y, MOUTH_Z * side)
            // 0..0.35 reach out, 0.35..0.5 grip and tug, 0.5..0.85 carry back, 0.85..1 at the mouth
            val ph = pickPhase[j]
            val tip = when {
                ph < 0.35 -> { val s = ph / 0.35; mouth.lerp(spot, s * s * (3 - 2 * s)) }
                ph < 0.5 -> { spot + up * (0.012 * L * sin(PI * (ph - 0.35) / 0.15 * 2)) }
                ph < 0.85 -> { val s = (ph - 0.5) / 0.35; spot.lerp(mouth, s * s * (3 - 2 * s)) }
                else -> mouth
            }
            putOffset(7 + j, 0.0, tip - restTip, f, up, rt, L, 0.2, picking)
        }

        // ---- antennae: up over rock ahead, down along a client's flank ----
        var tl = 0.0; var tr = 0.0
        if (rider != null) { tl = -0.6; tr = -0.6 }
        else for (s in 0 until 2) {
            val side = if (s == 1) 1.0 else -1.0
            var lift = 0.0
            for (r in 1..3) {
                val q = world(o, f, up, rt, 0.5 + 0.35 * r, 0.03 * r, side * 0.12 * r)
                val d = surf.dist(q)
                if (d < 0.12 * L) lift = max(lift, (0.12 * L - d) / (0.12 * L) * (1.0 - 0.2 * (r - 1)))
            }
            if (s == 0) tl = lift else tr = lift
        }
        // a probe point already inside rock would ask for more than a full lift: cap it, and ease gently (even on the
        // long fast-forward ticks, where a full step would snap from draped over a client straight to lifted)
        val ae = min(0.8, dt * 5.0)
        antL += (tl.coerceAtMost(1.0) - antL) * ae; antR += (tr.coerceAtMost(1.0) - antR) * ae
        put(11, min(picking, 0.99), planted, antL, antR)
    }

    /** One more step onto the surface: the first projection lands off it where the rock curves. */
    private fun project(surf: CrawlSurface, p: Vec3): Vec3 {
        val d = surf.dist(p)
        return if (abs(d) < 0.02) p else p - surf.normal(p) * d
    }

    /** A foothold on the client under the leg, or, past a narrow fish's back, on its flank as the leg wraps round. */
    private fun grip(c: Organism, rest: Vec3, up: Vec3, L: Double): Vec3? {
        onBody(c, rest + up * (0.25 * L), -up, 0.6 * L)?.let { return it }
        val h = c.heading.normalized()
        val mid = c.pos - h * (0.5 * c.lengthCm)
        val axis = mid + h * ((rest - mid) dot h)
        val dir = (axis - rest).normalized(-up)
        return onBody(c, rest - dir * (0.3 * L), dir, 0.9 * L)
    }

    /** Walking leg [i]'s planted foothold (world cm) while it is in stance, else null (tests). */
    internal fun stanceFoot(i: Int): Vec3? = if (swing[i] < 0) anchor[i] else null
    internal fun swinging(i: Int) = swing[i] >= 0

    /** Model point → world, with the body seated by [seatD], [seatP], [seatR] as the renderer draws it. */
    private fun world(o: Organism, f: Vec3, up: Vec3, rt: Vec3, mx: Double, my: Double, mz: Double): Vec3 =
        o.pos + (f * (mx - 0.5) + up * (my - seatD - seatP * (mx - SEAT_X) - seatR * mz) + rt * mz) * o.lengthCm

    private fun rawWorld(o: Organism, f: Vec3, up: Vec3, rt: Vec3, mx: Double, my: Double, mz: Double): Vec3 =
        o.pos + (f * (mx - 0.5) + up * my + rt * mz) * o.lengthCm

    /** Clearance (body lengths) below [p] to rock, sand and glass, or to the client's body; null when out of reach. */
    private fun gap(surf: CrawlSurface, rider: Organism?, p: Vec3, up: Vec3, L: Double): Double? =
        if (rider != null) onBody(rider, p + up * (0.3 * L), -up, 0.8 * L)?.let { (p - it).dot(up) / L }
        else surf.dist(p) / L

    /**
     * Seats the body on its legs: a least-squares drop, pitch and roll (model units, a shear the renderer applies
     * to every vertex, uLimb[12]) that bring the walking legs' rest tips onto the surface under them, as far as the
     * belly keeps clear of it. The sim keeps placing the animal by its head; this only settles how the body sits.
     */
    private fun fitSeat(o: Organism, surf: CrawlSurface, rider: Organism?, legs: DoubleArray, f: Vec3, up: Vec3, rt: Vec3,
                        L: Double, k: Double, grounded: Boolean) {
        var tD = 0.0; var tP = 0.0; var tR = 0.0
        if (grounded) {
            // normal equations for g = D + P·(x − SEAT_X) + R·z over the legs that find something under them
            var n = 0.0; var sx = 0.0; var sz = 0.0; var sxx = 0.0; var szz = 0.0; var sxz = 0.0
            var sg = 0.0; var sxg = 0.0; var szg = 0.0
            for (i in 0 until 6) {
                val base = (i / 2) * 3
                if (legs[base + 1] > 0.0) continue
                val side = if (i % 2 == 1) 1.0 else -1.0
                val x = legs[base] - SEAT_X; val z = legs[base + 2] * side
                // astride a client the legs wrap round its flanks: seat on its back under the midline
                val g = gap(surf, rider, rawWorld(o, f, up, rt, legs[base], legs[base + 1], if (rider != null) 0.0 else z), up, L) ?: continue
                if (g > SEAT_MAX + 0.1 || g < -0.1) continue
                n++; sx += x; sz += z; sxx += x * x; szz += z * z; sxz += x * z; sg += g; sxg += x * g; szg += z * g
            }
            if (n >= 3) {
                val a = doubleArrayOf(n, sx, sz, sx, sxx + RIDGE, sxz, sz, sxz, szz + RIDGE)
                val det = det3(a)
                if (abs(det) > 1e-12) {
                    tD = det3(doubleArrayOf(sg, sx, sz, sxg, sxx + RIDGE, sxz, szg, sxz, szz + RIDGE)) / det
                    tP = det3(doubleArrayOf(n, sg, sz, sx, sxg, sxz, sz, szg, szz + RIDGE)) / det
                    tR = det3(doubleArrayOf(n, sx, sg, sx, sxx + RIDGE, sxg, sz, sxz, szg)) / det
                }
            } else if (n > 0) tD = sg / n
            tP = tP.coerceIn(-SEAT_TILT, SEAT_TILT); tR = tR.coerceIn(-SEAT_TILT, SEAT_TILT)
            tD = tD.coerceIn(-0.03, SEAT_MAX)
            // never press the belly into the rock or the client
            for (bx in BELLY_X) {
                val g = gap(surf, rider, rawWorld(o, f, up, rt, bx, BELLY_Y, 0.0), up, L) ?: continue
                val over = tD + tP * (bx - SEAT_X) - (g - BELLY_CLEAR)
                if (over > 0) tD -= over
            }
            tD = max(tD, -0.03)
        }
        seatD += (tD - seatD) * k; seatP += (tP - seatP) * k; seatR += (tR - seatR) * k
        put(12, 0.0, seatD, seatP, seatR)
    }

    private fun det3(m: DoubleArray) =
        m[0] * (m[4] * m[8] - m[5] * m[7]) - m[1] * (m[3] * m[8] - m[5] * m[6]) + m[2] * (m[3] * m[7] - m[4] * m[6])

    private fun putOffset(i: Int, x: Double, dw: Vec3, f: Vec3, up: Vec3, rt: Vec3, L: Double, cap: Double, w: Double) {
        var dx = (dw dot f) / L; var dy = (dw dot up) / L; var dz = (dw dot rt) / L
        val len = sqrt(dx * dx + dy * dy + dz * dz)
        if (len > cap) { val s = cap / len; dx *= s; dy *= s; dz *= s }
        put(i, x * w, dx * w, dy * w, dz * w)
    }

    /** Small private LCG: idle steps and pick spots never draw from the brains' random streams. */
    private fun rand(): Double {
        seed = (seed * 6364136223846793005L + 1442695040888963407L)
        return ((seed ushr 11) and 0xFFFFFL).toDouble() / 0x100000.toDouble()
    }

    companion object {
        const val EASE_RATE = 8.0
        const val ENTRIES = 13
        /** Body lengths a planted foot may fall behind its rest place before the leg steps (design estimate). */
        const val STEP_BL = 0.07
        /** Swing duration; with a duty factor near 0.5 at a slow walk (research 19, shore crab analogue). */
        const val SWING_S = 0.18
        const val LIFT_BL = 0.05
        /** A walking leg reaches a foothold up to this far (body lengths) from its rest place, at most [LEG_BL] from its hip. */
        const val REACH_BL = 0.25
        const val LEG_BL = 0.33
        /** Body seat (uLimb[12]): pivot x, most drop and tilt, ease rate, ridge on the tilt fit, belly clearance. */
        const val SEAT_X = -0.1; const val SEAT_MAX = 0.2; const val SEAT_TILT = 0.35; const val SEAT_RATE = 4.0
        const val RIDGE = 0.02; const val BELLY_Y = -0.07; const val BELLY_CLEAR = 0.03
        private val BELLY_X = doubleArrayOf(0.25, 0.0, -0.25)
        /** Hips sit below the carapace, 0.052 behind the rest tip in x (both models' walking legs). */
        const val HIP_DX = 0.052; const val HIP_Y = -0.0435; const val HIP_Z = -0.0435
        /** Swing lift is sent scaled under 0.5 so the renderer's phase interpolation of x never wraps it. */
        const val LIFT_X = 0.4
        /** Picking strokes per second per claw while cleaning (DESIGN ESTIMATE; Caves 2019 reports no rate). */
        const val PICK_HZ = 1.8
        const val MOUTH_X = 0.31; const val MOUTH_Y = -0.07; const val MOUTH_Z = 0.02

        private val GROUP_A = booleanArrayOf(true, false, false, true, true, false)

        /** Rest tips (model units) of walking pairs 2, 3, 4, left side (z mirrored); y > 0 marks a missing leg. */
        private val SKUNK_LEGS = doubleArrayOf(
            -0.0219, -0.2178, -0.2784, -0.1001, -0.2178, -0.2784, -0.1697, -0.2178, -0.2784,
        )
        private val BANDED_LEGS = doubleArrayOf(
            0.0, 1.0, 0.0, -0.0857, -0.2256, -0.2884, -0.1578, -0.2256, -0.2884,
        )
        /** Rest tips of the picking pairs P1, P2 (left side). */
        private val SKUNK_PICKS = doubleArrayOf(0.3785, -0.1656, -0.1303, 0.3351, -0.1655, -0.1303)
        private val BANDED_PICKS = doubleArrayOf(0.4102, -0.1715, -0.135, 0.3652, -0.1715, -0.135)

        /**
         * The fish [c]'s body depth profile, 0..1 of its greatest half-depth at [xn] of its length from the snout:
         * deepest a little ahead of the middle, tapering to the snout and the tail stalk.
         */
        fun bodyProfile(xn: Double): Double = if (xn <= 0.0 || xn >= 1.0) 0.0 else sin(PI * xn.pow(0.7))

        /**
         * Where the ray from [p] along unit [dir] first meets the fish [c]'s body (half-depth `depthRatio·L/2` times
         * [bodyProfile], width under half the depth), or null within [maxT]. Marched, then refined by bisection.
         */
        fun onBody(c: Organism, p: Vec3, dir: Vec3, maxT: Double): Vec3? {
            val L = c.lengthCm
            val h = c.heading.normalized()
            val u = c.up.rejectFrom(h).normalized(Vec3.Y)
            val s = h cross u
            val hd = 0.5 * c.species.depthRatio * L
            fun inside(t: Double): Boolean {
                val q = p + dir * t - c.pos
                val xn = -(q dot h) / L
                val d = hd * bodyProfile(xn)
                if (d <= 0) return false
                val y = (q dot u) / d; val z = (q dot s) / (0.45 * d)
                return y * y + z * z <= 1.0
            }
            if (inside(0.0)) return p
            val n = 24
            var prev = 0.0
            for (k in 1..n) {
                val t = maxT * k / n
                if (inside(t)) {
                    var lo = prev; var hi = t
                    for (r in 0 until 6) { val m = 0.5 * (lo + hi); if (inside(m)) hi = m else lo = m }
                    return p + dir * hi
                }
                prev = t
            }
            return null
        }
    }
}
