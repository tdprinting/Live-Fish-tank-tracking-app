package com.reeflive.core.sim

import com.reeflive.core.geom.BodySpline
import com.reeflive.core.geom.Vec3
import kotlin.math.sqrt

/**
 * Hole-aware rock guard (SIMULATION §24), run after Steering every tick: no part of a body (head or any of the
 * [BodySpline.POINTS] spine stations) stays inside solid rock unless it lies in a modelled hole or cave that fits the
 * animal ([HoleSet.inFittingHole]); the rock feet buried under the sand count as sand. A head inside a single-occupant
 * hole held by another animal ([HoleClaims]) is put back out past the mouth. A head found inside rock is moved back onto the surface; a body whose trail cuts
 * through rock (a tail swung round a corner, a straight rigid body on a concave face) is re-laid from the head with
 * the same segment lengths, each station stepping round the rock (never jumping), and the trail takes that shape.
 *
 * Deliberately small and self-contained: it is a backstop for behaviours (which aim for real holes or the rock face
 * themselves), and the body-collision layer (sim/BodyCollision.kt, resolveEnv) can replace it wholesale.
 */
class RockGuard(
    private val rock: RockData,
    private val sandY: (Double, Double) -> Double = { _, _ -> rock.sandTopCm },
    private val claims: HoleClaims? = null,
) {
    /** Single-occupant holes, for the claim check (with a reach radius for a quick reject); redone when rocks move. */
    private var ownFor: HoleSet? = null
    private var ownHoles: List<RockHole> = emptyList()
    private var ownReach = DoubleArray(0)

    /** Heads moved back out of a hole another animal holds (diagnostics, tests). */
    var claimFixes = 0L; private set
    private val pos = Array(BodySpline.POINTS) { Vec3.ZERO }
    private val cand = Array(7) { Vec3.ZERO }

    /** Heads moved out of rock / bodies re-laid since the start (diagnostics, tests). */
    var headFixes = 0L; private set
    /**
     * Told of each head that ran into the rock faster than [HIT_MIN_CM_S]: the point it hit and the impulse it gave
     * the rock (g·cm/s). The simulation hands it to the rock physics while rocks can shift.
     */
    var onHit: ((Double, Double, Double, Double, Double, Double) -> Unit)? = null
    /** This body already struck the rock this tick (one hit per body per tick). */
    private var struck = false
    var bodyFixes = 0L; private set

    // the animal being guarded: its measured body (null: the spine line alone, as before profiles), length, and the
    // most any station's body reaches (cm), which bounds the field lookups
    private var cur: Organism? = null
    private var prof: BodyProfile? = null
    private var lenCm = 0.0
    private var reach = 0.0
    private val fr = StationFrame()
    private val frames = Array(BodySpline.POINTS) { Vec3.ZERO }
    private var framesOk = false
    private var skin = HOLE_SLACK

    /**
     * How far (cm) station [i]'s body at [p] reaches toward the rock (its section's support against the field's
     * gradient there, oriented by the drawn frame of the station as last sampled): the clearance it needs.
     */
    private fun need(i: Int, p: Vec3, d: Double = rock.sdf.dist(p)): Double {
        val pr = prof ?: return 0.0
        // the field's gradient by forward differences from the value already read (without building points)
        val sdf = rock.sdf; val h = 0.5
        var gx = sdf.distAt(p.x + h, p.y, p.z) - d
        var gy = sdf.distAt(p.x, p.y + h, p.z) - d
        var gz = sdf.distAt(p.x, p.y, p.z + h) - d
        val gl = kotlin.math.sqrt(gx * gx + gy * gy + gz * gz)
        if (gl < 1e-9) { gx = 0.0; gy = 1.0; gz = 0.0 } else { gx /= gl; gy /= gl; gz /= gl }
        gnx = gx; gny = gy; gnz = gz
        val o = cur!!
        if (!framesOk) { o.spline.sampleStations(frames); framesOk = true }
        fr.set(o, frames, i)
        return fr.reach(pr, i, lenCm, -gx, -gy, -gz).coerceAtLeast(0.0)
    }

    /** The field gradient the last [need] was taken along. */
    private var gnx = 0.0; private var gny = 1.0; private var gnz = 0.0

    private fun blocked(p: Vec3, br: Double, i: Int): Boolean {
        val ri = prof?.let { it.reach(i) * lenCm } ?: 0.0
        if (!mayBeRock(p, ri)) return false
        val d = rock.sdf.dist(p)
        if (d >= ri - TOL) return false
        if (blockedAt(rock, p, d, br, TOL, sandY, skin)) return true
        if (prof == null) return false
        // the body round the station: inside the rock where it reaches past the field's clearance, unless that contact
        // lies under the sand (a buried rock foot) or the station sits in a hole that fits it
        val nd = need(i, p, d)
        if (d >= nd - TOL) return false
        val ex = p.x - gnx * nd; val ey = p.y - gny * nd; val ez = p.z - gnz * nd
        if (ey < sandY(ex, ez) + BODY_SAND_SKIN) return false
        return !(d >= -HOLE_SLACK && rock.holes.inFittingHole(p, br))
    }

    // The lowest the rock SDF grid reads anywhere in each cell (its lowest corner: the field is trilinear over a cell).
    // A station whose body reaches less than that from its spine cannot touch the rock there, and most spine stations of
    // most animals skip the field lookup (SIMULATION §24). Rebuilt when
    // a moved rock has settled ([DynamicRockSdf.revision] unchanged for [MASK_SETTLE] checks); off until then.
    private val grid = ((rock.sdf as? DynamicRockSdf)?.base ?: rock.sdf) as? com.reeflive.core.geom.SdfGrid
    private var mask: FloatArray? = null
    private var maskRev = -1
    private var seenRev = -1
    private var settle = 0

    private fun mayBeRock(p: Vec3, reach: Double): Boolean {
        val g = grid ?: return true
        val rev = (rock.sdf as? DynamicRockSdf)?.revision ?: 0
        if (rev != maskRev) {
            if (rev != seenRev) { seenRev = rev; settle = 0 }
            if (++settle < MASK_SETTLE && maskRev >= 0) return true
            buildMask(g); maskRev = rev
        }
        val fx = (p.x - g.origin.x) / g.cell; val fy = (p.y - g.origin.y) / g.cell; val fz = (p.z - g.origin.z) / g.cell
        if (fx < 0.0 || fy < 0.0 || fz < 0.0 || fx >= g.nx - 1.0 || fy >= g.ny - 1.0 || fz >= g.nz - 1.0) return true
        return mask!![(fz.toInt() * (g.ny - 1) + fy.toInt()) * (g.nx - 1) + fx.toInt()] < reach - TOL
    }

    private fun buildMask(g: com.reeflive.core.geom.SdfGrid) {
        val sdf = rock.sdf
        val nx = g.nx; val ny = g.ny; val nz = g.nz
        val node = FloatArray(nx * ny * nz)
        for (k in 0 until nz) for (j in 0 until ny) for (i in 0 until nx)
            node[(k * ny + j) * nx + i] = sdf.distAt(g.origin.x + i * g.cell, g.origin.y + j * g.cell, g.origin.z + k * g.cell).toFloat()
        val m = mask ?: FloatArray((nx - 1) * (ny - 1) * (nz - 1)).also { mask = it }
        for (k in 0 until nz - 1) for (j in 0 until ny - 1) for (i in 0 until nx - 1) {
            var lo = Float.MAX_VALUE
            for (c in 0 until 8) lo = minOf(lo, node[((k + (c shr 2)) * ny + j + ((c shr 1) and 1)) * nx + i + (c and 1)])
            m[(k * (ny - 1) + j) * (nx - 1) + i] = lo
        }
    }

    /**
     * One animal per hole: a head inside a hole another animal holds goes back out past the mouth. Run by [apply], and
     * again after a crawler's seat has placed it (the seat's height write-back could set a hermit walking the lip of a
     * crevice back inside it).
     */
    fun keepClaims(o: Organism) {
        if (claims == null || !o.alive) return
        if (ownFor !== rock.holes) {
            ownHoles = rock.holes.all.filter { it.singleOccupant }
            ownReach = DoubleArray(ownHoles.size) { ownHoles[it].depth + ownHoles[it].radius + 1.0 }
            ownFor = rock.holes
        }
        for (i in ownHoles.indices) {
            val h = ownHoles[i]
            if (o.pos.distanceTo(h.mouth) > ownReach[i] || !h.holds(o.pos)) continue
            val holder = claims.holder(h) ?: continue
            if (holder === o) continue
            o.pos = o.pos + h.axis * (-((o.pos - h.mouth) dot h.axis) + CLAIM_OUT_CM)
            claimFixes++
        }
    }

    fun apply(o: Organism) {
        if (!o.alive) return
        val sdf = rock.sdf
        val br = RockHole.bodyRadius(o)
        keepClaims(o)
        // a crawler on its seat is drawn where its seat rests it (CrawlerSeat): its spine line is guarded, not a body
        // (and so is a body on a behaviour's own path into a hole, den or burrow that fits it, and a goby at its burrow,
        // in the sand pocket it dug under the rock)
        val pr = if (o.seatOn || o.atBurrow || o.spline.override != null) null else BodyProfile.spineOf(o)
        cur = o; prof = pr; lenCm = o.lengthCm
        skin = if (o.isFish) SKIN_ROCK else HOLE_SLACK
        reach = if (pr == null) 0.0 else pr.maxReach * o.lengthCm
        // every station lies within a body length of the spline's head: a head that far from the rock (most fish,
        // most of the time) has nothing to check (the full check ran for every animal every tick: SIMULATION §24)
        if (sdf.dist(o.pos) > o.spline.length + o.pos.distanceTo(o.spline.headPos) + FAR_SLACK_CM + reach) {
            if (o.spline.override == null && o.spline.holding) o.spline.releaseCorrection()
            return
        }
        // the drawn frames the body sections are oriented by: sampled when a body first nears the rock
        framesOk = false
        struck = false
        if (blocked(o.pos, br, 0)) {
            // out onto the rock face, never down into the sand bed
            val p0 = o.pos
            val q = sdf.project(o.pos, need(0, o.pos) + MARGIN, iterations = 16)
            o.pos = q.withY(maxOf(q.y, minOf(o.pos.y, sandY(q.x, q.z) + MARGIN)))
            headFixes++
            // run into the rock hard: the head stops against it and hands the rock its momentum (a loose rock budges)
            val hit = onHit
            if (hit != null && !struck) {
                val nx = o.pos.x - p0.x; val ny = o.pos.y - p0.y; val nz = o.pos.z - p0.z
                val nl = sqrt(nx * nx + ny * ny + nz * nz)
                if (nl > 1e-6) {
                    val vIn = -(o.vel.x * nx + o.vel.y * ny + o.vel.z * nz) / nl
                    if (vIn > HIT_MIN_CM_S) { val j = o.massG * vIn / nl; hit(p0.x, p0.y, p0.z, -nx * j, -ny * j, -nz * j); struck = true }
                }
            }
        }
        if (o.spline.override == null && o.spline.holding) {
            // a correction is held for as long as the plain straight body would still cut the rock: a rigid body keeps
            // showing it at full weight, a trail stops relaxing back toward the straight line. Letting it ease or relax
            // back into the rock and re-laying it from there flailed the body every few ticks (FishGlitch BODY_POP)
            val seg = o.spline.length / (BodySpline.POINTS - 1)
            var rawBad = false
            for (i in 0 until BodySpline.POINTS) if (blocked(o.pos - o.heading * (seg * i), br, i)) { rawBad = true; break }
            if (!rawBad) o.spline.releaseCorrection() else if (o.spline.rigid) o.spline.holdCorrection()
        }
        // a trail adopts the re-laid stations as its points and is sampled again from them, which can shift a station
        // back into the rock (a goby's tail 0.34 cm into the crack by its burrow): one more pass from the new shape
        if (relay(o, br) && o.spline.override == null && !o.spline.rigid) relay(o, br)
    }

    /** Re-lays the body round the rock if any station is blocked; true if it did. */
    private fun relay(o: Organism, br: Double): Boolean {
        val sdf = rock.sdf
        o.spline.sampleStations(pos)
        framesOk = false
        var bad = false
        for (i in 0 until BodySpline.POINTS) if (blocked(pos[i], br, i)) { bad = true; break }
        if (!bad) return false
        bodyFixes++
        val seg = o.spline.length / (BodySpline.POINTS - 1)
        val out = ArrayList<Vec3>(BodySpline.POINTS)
        var prev = if (blocked(pos[0], br, 0)) sdf.project(pos[0], need(0, pos[0]) + MARGIN, iterations = 16) else pos[0]
        out.add(prev)
        var back = (pos[1] - pos[0]).normalized(o.heading * -1.0)
        for (i in 1 until BodySpline.POINTS) {
            // the next station one segment on from the last, never jumping: toward where the trail had it, else straight
            // on, else slid along the rock face, else straight away from the rock
            val n = sdf.gradient(prev).normalized(Vec3.Y)
            val q = pos[i]
            val towardQ = (q - prev).normalized(back)
            // first choice: toward the trail point pushed just out of the rock, the least change of shape (a trail the
            // relax has eased a little into the rock comes back out where it was, instead of folding to another side)
            val qOut = if (blocked(q, br, i)) sdf.project(q, need(i, q) + MARGIN, iterations = 8) else q
            cand[0] = (qOut - prev).normalized(towardQ); cand[1] = towardQ; cand[2] = back
            cand[3] = back.rejectFrom(n).normalized(n); cand[4] = towardQ.rejectFrom(n).normalized(n)
            cand[5] = (back + n).normalized(n); cand[6] = n
            var best: Vec3? = null
            var bestD = -1e9
            var ok = false
            // ... and if one segment along that still ends in the rock, that end pushed out too (and back to one segment)
            val c0 = prev + cand[0] * seg
            if (clear(c0, br, i)) { best = c0; ok = true } else {
                val c1 = prev + (sdf.project(c0, need(i, c0) + MARGIN, iterations = 8) - prev).normalized(cand[0]) * seg
                if (clear(c1, br, i)) { best = c1; ok = true }
            }
            if (!ok) for (d in cand) {
                val c = prev + d * seg
                if (clear(c, br, i)) { best = c; ok = true; break }
                val dc = sdf.dist(c) - (if (prof == null) 0.0 else need(i, c))
                if (dc > bestD) { bestD = dc; best = c }
            }
            if (!ok) {
                // a pocket or a thin spur where the gradient misleads: the open axis direction closest to straight on
                val dirs = AXES.sortedByDescending { it dot back }
                val c = dirs.map { prev + it * seg }.firstOrNull { !blocked(it, br, i) && sdf.dist(it) >= MARGIN * 0.5 }
                    ?: dirs.map { prev + it * seg }.firstOrNull { !blocked(it, br, i) }
                    // boxed in (a long rigid body in a crack): fold the rest of the body back along itself
                    ?: out.getOrNull(out.size - 2)
                if (c != null) best = c
            }
            val c = best!!
            back = (c - prev).normalized(back)
            out.add(c)
            prev = c
        }
        reportHit(o, out)
        finish(o, out, br)
        return true
    }

    /**
     * The station the re-lay moved furthest out of the rock, against the way the body was going: a body swimming into
     * the rock that fast ([HIT_MIN_CM_S]) hands it the momentum of the part that struck ([onHit]).
     */
    private fun reportHit(o: Organism, out: List<Vec3>) {
        val hit = onHit ?: return
        if (struck) return
        var best = -1; var bestV = HIT_MIN_CM_S; var bnx = 0.0; var bny = 0.0; var bnz = 0.0
        for (i in out.indices) {
            val dx = out[i].x - pos[i].x; val dy = out[i].y - pos[i].y; val dz = out[i].z - pos[i].z
            val dl = sqrt(dx * dx + dy * dy + dz * dz)
            if (dl < HIT_MIN_MOVE_CM) continue
            val vIn = -(o.vel.x * dx + o.vel.y * dy + o.vel.z * dz) / dl
            if (vIn > bestV) { bestV = vIn; best = i; bnx = dx / dl; bny = dy / dl; bnz = dz / dl }
        }
        if (best < 0) return
        val j = o.massG * bestV * HIT_MASS_SHARE
        val p = pos[best]
        hit(p.x, p.y, p.z, -bnx * j, -bny * j, -bnz * j)
        struck = true
    }

    private fun finish(o: Organism, out: List<Vec3>, br: Double) {
        // a re-lay that leaves the body no further out of the rock than the shape shown (a long rigid body boxed in
        // against the rock) keeps the shown shape: re-laying it from its own last re-lay every tick flailed it
        if (o.spline.override != null && penetration(out, br) >= penetration(pos.asList(), br) - 1e-6) return
        // a free-swimming trail is corrected in place (no pop when it clears the rock); a behaviour's own polyline
        // (hole, den) and a rigid body are shown through the override
        if (o.spline.override == null && !o.spline.rigid) o.spline.adopt(out) else o.spline.correct(out)
    }

    /**
     * A spot a corrected station may take: out of the rock with a little clearance, in the sand (over a buried rock foot,
     * as [blocked] counts it: re-laying a body that lies in the sand round the buried rock popped it), or in a hole that
     * fits the body.
     */
    private fun clear(c: Vec3, br: Double, i: Int): Boolean =
        !blocked(c, br, i) && (rock.sdf.dist(c) >= MARGIN * 0.5 || c.y < sandY(c.x, c.z) + SAND_SKIN || rock.holes.inFittingHole(c, br))

    /** How far the bodies round the stations of [p] reach into rock beyond the tolerance, summed (cm). */
    private fun penetration(p: List<Vec3>, br: Double): Double {
        var s = 0.0
        for (i in p.indices) { val q = p[i]; if (blocked(q, br, i)) s += (if (prof == null) 0.0 else need(i, q)) - rock.sdf.dist(q) - TOL }
        return s
    }

    companion object {
        /** Penetration tolerated before the guard acts (cm; the baked SDF is a 1 cm grid). */
        const val TOL = 0.1
        /** Clearance a corrected station is given (cm). */
        const val MARGIN = 0.12
        /** Guard checks with an unchanged rock field before the may-be-rock mask is rebuilt after a rock moved. */
        const val MASK_SETTLE = 2000
        /** Margin (cm) over a body length beyond which the rock cannot reach any station (the baked field's slack). */
        const val FAR_SLACK_CM = 1.0
        /** How far past the mouth a head is put back out of a hole another animal holds (cm). */
        const val CLAIM_OUT_CM = 0.3
        /** Speed into the rock (cm/s) a head must hit it at to push on it: a dash or a startle, not a graze. */
        const val HIT_MIN_CM_S = 15.0
        /** Least a re-laid station must have been moved out of the rock to count as striking it (cm). */
        const val HIT_MIN_MOVE_CM = 0.2
        /** Share of the body's mass behind a strike by part of the body (the head carries it all). [I] */
        const val HIT_MASS_SHARE = 0.5
        private val AXES = listOf(Vec3.X, Vec3.X * -1.0, Vec3.Y, Vec3.Y * -1.0, Vec3.Z, Vec3.Z * -1.0,
            Vec3(1.0, 1.0, 0.0).normalized(), Vec3(-1.0, 1.0, 0.0).normalized(), Vec3(0.0, 1.0, 1.0).normalized(), Vec3(0.0, 1.0, -1.0).normalized())
        /**
         * How far inside the baked rock surface a point in a fitting hole may read (cm): the 1 cm SDF grid reads a
         * 1–2 cm hole as shallow rock, but deeper than this the descriptor's round volume has missed the real walls.
         */
        const val HOLE_SLACK = 1.0
        /** Height above the sand surface still counted as lying on the sand (cm). */
        const val SAND_SKIN = 0.6
        /** How deep (cm) into a rock foot a body lying on the sand may read (the 1 cm field's error at the sand line). */
        const val SKIN_ROCK = 0.3
        /** A body's contact with the rock this little above the sand surface is with a rock foot the sand covers (cm). */
        const val BODY_SAND_SKIN = 0.1

        /** Inside rock, and not in the open volume of a hole that fits a body of half-thickness [br]. */
        fun blocked(rock: RockData, p: Vec3, br: Double, tol: Double = TOL, sandY: (Double, Double) -> Double = { _, _ -> rock.sandTopCm }): Boolean {
            // the rock feet run on under the sand: a body on or in the sand there is in sand, not in rock. Lying on the
            // sand (the skin over it) counts only where the rock is shallow: a tail skimming the sand through a rock foot
            // the sand is banked against was a body 2.6 cm inside the rock (Stripes at the goby spoil, t=34035 seed 1),
            // and 1 cm (a watchman's tail at its burrow) still showed through, so a fish is let by only the field's error
            return blockedAt(rock, p, rock.sdf.dist(p), br, tol, sandY, HOLE_SLACK)
        }

        /**
         * [blocked] with the field already read at [p] ([d]); a spine point lying on the sand may read [skin] cm into a
         * rock foot (a fish only the field's error, [SKIN_ROCK]; a crawler's seat rests it on the foot it walks).
         */
        fun blockedAt(rock: RockData, p: Vec3, d: Double, br: Double, tol: Double, sandY: (Double, Double) -> Double, skin: Double): Boolean {
            if (d >= -tol) return false
            val sy = sandY(p.x, p.z)
            if (p.y < sy) return false
            if (p.y < sy + SAND_SKIN && d >= -skin) return false
            return d < -tol && (d < -HOLE_SLACK || !rock.holes.inFittingHole(p, br))
        }
    }
}
