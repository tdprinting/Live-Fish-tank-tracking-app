package com.reeflive.core.sim

import com.reeflive.core.geom.Vec3
import com.reeflive.core.geom.smoothstep
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The surfaces a crawler's body rests on (SIMULATION §19.6): the rock SDF, the live sand bed ([SandBed.surfaceY], the
 * sand the renderer draws, sloping from 3.5 cm at the front glass to 6 cm at the back, not the flat
 * [RockData.sandTopCm] plane) and the four glass panes. Signed distance in cm, positive in the water.
 */
class CrawlSurface(val rock: RockData, val sand: SandBed?) {
    fun dist(p: Vec3): Double {
        var d = rock.sdf.dist(p)
        val sy = sand?.surfaceY(p.x.coerceIn(0.0, rock.lengthCm), p.z.coerceIn(0.0, rock.widthCm)) ?: rock.sandTopCm
        val s = p.y - sy
        if (s < d) d = s
        if (p.x < d) d = p.x
        if (rock.lengthCm - p.x < d) d = rock.lengthCm - p.x
        if (p.z < d) d = p.z
        if (rock.widthCm - p.z < d) d = rock.widthCm - p.z
        return d
    }

    /** Outward surface normal at [p] (central differences, [h] cm). */
    fun normal(p: Vec3, h: Double = 0.2): Vec3 = Vec3(
        dist(Vec3(p.x + h, p.y, p.z)) - dist(Vec3(p.x - h, p.y, p.z)),
        dist(Vec3(p.x, p.y + h, p.z)) - dist(Vec3(p.x, p.y - h, p.z)),
        dist(Vec3(p.x, p.y, p.z + h)) - dist(Vec3(p.x, p.y, p.z - h)),
    ).normalized(Vec3.Y)

    /**
     * Sphere-traced distance from [from] along the unit [dir] to the surface, or −1 when nothing is hit within [maxT].
     * 0 means [from] is already inside rock, sand or glass.
     */
    fun cast(from: Vec3, dir: Vec3, maxT: Double): Double {
        var t = 0.0
        for (i in 0 until 28) {
            val d = dist(from + dir * t)
            if (d < HIT_CM) return if (i == 0 && d < 0) 0.0 else t
            t += max(d, 0.02)
            if (t > maxT) return -1.0
        }
        return -1.0
    }

    companion object {
        const val HIT_CM = 0.01
    }
}

/**
 * Where a crawler's model goes (SIMULATION §19.6). The crawler models stand on their own y = 0 plane (feet, foot,
 * disc and shell lip on the ground), while the sim walks [Organism.pos] at the brain's `surfaceOffset` above the
 * nearest surface along a single normal; drawn there, a hermit stood 0.5 cm off the rock with its legs crossing
 * into the mesh. The seat finds the surface under the body, fits the body up to a plane through four footprint
 * samples, and solves the model's contact points against [CrawlSurface] so the shell rests on it with no gap and
 * none of them inside. The organism's own position and up are untouched (behaviour and navigation are unchanged);
 * only the pose sent to the renderer ([Organism.toPose]) uses the seat.
 *
 * The hermit crab also plants its walking legs ([HermitLegs] entries 10–13): each foot sits on a foothold cast onto
 * the surface from above its stance point, stays there through the stance and swings to the next foothold; a
 * rotate-and-flex IK about the hip (`fish.vert` hermitLimb, replicated in [posedLeg]) reaches it, and a foothold
 * that would put the knee into rock is pulled in toward the body. The chelae may not dip below the surface, and the
 * antennae, antennules and eyestalks bend up and back away from rock and glass.
 *
 * The Sally Lightfoot plants all eight walking legs the same way ([CrabLegs] entries 10–17, research 27): a stance
 * foot stays where it touched down while the body walks over it (leading legs flex and pull, trailing legs extend and
 * push, from the IK alone), and each swing lifts the foot about a fifth of the leg length and sets it down half a
 * stance stroke ahead along the travel, cast onto rock, sand or glass. Pressed flat in its crevice the feet pull in
 * and the carapace drops onto the surface.
 */
class CrawlerSeat(val kind: BrainKind, val shape: SeatShape, val hermit: HermitModel?, val crab: CrabModel? = null) {
    /** Leg geometry of the animal whose legs this seat plants (hermit or Sally Lightfoot), null for the others. */
    val legModel: LegModel? = hermit ?: crab
    /** 0 = raw sim pose .. 1 = fully seated; eased so a crawler leaving or regaining a surface does not pop. */
    var blend = 0.0; private set
    private var inited = false
    private var upS = Vec3.Y
    private var fitUp = Vec3.Y
    private var oPrev = Vec3.ZERO
    private var wedge = 0.0
    private var oHeadPrev = Vec3.X
    /** The seat has had to hop several ticks running (see [update]); the hermit brain walks off and clears it. */
    val wedged: Boolean get() = wedge >= WEDGE_TICKS
    fun clearWedge() { wedge = 0.0 }

    /** Solved seat (unblended): model origin and frame (model +X = [fwd], +Y = [up], +Z = [right]). */
    var pos = Vec3.ZERO; private set
    var fwd = Vec3.X; private set
    var up = Vec3.Y; private set
    var right = Vec3.Z; private set

    // ---- planted legs (hermit 4, Sally Lightfoot 8) ----
    private val foot = Array(MAX_LEGS) { Vec3.ZERO }
    private val liftFrom = Array(MAX_LEGS) { Vec3.ZERO }
    private val planted = BooleanArray(MAX_LEGS)
    private val wasSwing = BooleanArray(MAX_LEGS)
    /** Posed foot tips (world) of the last update, as the renderer draws them (tests). */
    val footTip = Array(MAX_LEGS) { Vec3.ZERO }
    /** Foothold (world) each leg reached for in the last update, and whether it was swinging. */
    val footTarget = Array(MAX_LEGS) { Vec3.ZERO }
    val footSwing = BooleanArray(MAX_LEGS)
    /** Legs drawn pulled into the shell because no foothold keeps them out of rock (see [poseHermit]). */
    val legTucked = BooleanArray(MAX_LEGS)
    private val tuck = DoubleArray(MAX_LEGS)
    /** IK per leg: rotation (axis × angle, model space) and flex (shortening 0..0.5) of the last update. */
    private val ikRot = DoubleArray(MAX_LEGS * 3)
    private val ikFlex = DoubleArray(MAX_LEGS)
    /** Sally Lightfoot: half a stance stroke along the travel (model units, body x / z) and how far it is pressed flat. */
    private var strideX = 0.0
    private var strideZ = 0.0
    private var crabTuck = 0.0
    private val lastPh = DoubleArray(MAX_LEGS)
    var clawMaxDown = DoubleArray(2) { 1.2 }; private set
    var antennaBend = 0.0; private set
    var antennuleBend = 0.0; private set
    var eyeBend = 0.0; private set
    /** Legs planted this update (false while withdrawn or unseated). */
    var legsPlanted = false; private set
    /** Clearance (cm) of the lowest body contact point on the last seat; negative = some of the body is inside. */
    var clearance = 0.0; private set

    /** World point of model point ([mx], [my], [mz]) for a body of length [L] on the solved seat. */
    fun world(mx: Double, my: Double, mz: Double, L: Double): Vec3 = pos + (fwd * mx + up * my + right * mz) * L

    private fun toModel(w: Vec3, L: Double, out: DoubleArray) {
        val d = (w - pos) / L
        out[0] = d dot fwd; out[1] = d dot up; out[2] = d dot right
    }

    fun update(o: Organism, crawling: Boolean, s: CrawlSurface, dt: Double, simOffset: Double = -1.0) {
        val L = max(o.lengthCm, 0.3)
        val d0 = s.dist(o.pos)
        // (the Sally Lightfoot stands on long legs: its body may be over a dip, its feet on the rims round it)
        val onSurface = d0 < 0.5 * o.species.depthRatio * L + 0.2 + SURFACE_TOL_CM + (if (crab != null) CRAB_STAND_TOL * L else 0.0)
        // (the hermit and the Sally Lightfoot are seated whatever they do, the crab pressed into its crevice included)
        val want = o.alive && onSurface && (kind == BrainKind.HERMIT || kind == BrainKind.SALLY_CRAB || crawling && o.posture == 0)
        blend = (blend + ((if (want) 1.0 else 0.0) - blend).coerceIn(-BLEND_RATE * dt, BLEND_RATE * dt)).coerceIn(0.0, 1.0)
        if (blend <= 0.0) {
            o.seatOn = false; inited = false; legsPlanted = false; planted.fill(false)
            o.limbRig?.let { clearLegs(it) }
            return
        }
        val cl = o.limbRig as? CrabLegs
        if (crab != null && cl != null) crabStance(cl)
        // the surface under the body: step from the sim position onto it along the local normal (twice: a step
        // across the sand slope or a rock bulge lands a little off the first time)
        val n0 = s.normal(o.pos)
        var g = o.pos - n0 * d0
        g -= n0 * s.dist(g)
        // fit the body's up to four footprint samples cast onto the surface (a single-point normal tipped the shell
        // on every rock bump and left it hanging over a sand slope)
        val h0 = o.heading.rejectFrom(n0).normalized(n0.cross(Vec3.Z).normalized(Vec3.X))
        val l0 = n0 cross h0
        val a = shape.fore * L
        val b = shape.side * L
        val reach = max(a, b)
        val pF = footprint(s, g, n0, h0 * a, reach)
        val pB = footprint(s, g, n0, h0 * -a, reach)
        val pL = footprint(s, g, n0, l0 * b, reach)
        val pR = footprint(s, g, n0, l0 * -b, reach)
        var nf = ((pF - pB) cross (pL - pR)).normalized(n0)
        if ((nf dot n0) < 0.2) nf = n0
        val nT = (nf * FIT_WEIGHT + n0 * (1 - FIT_WEIGHT)).normalized(n0)
        // the seated up is kept from tick to tick (the contact solve's tilt persists) and eases toward the fit; a fit
        // more than ~100° away (the sim crossed to the opposite face of a slot) is taken at once
        upS = if (!inited || (upS dot nT) < -0.2) nT else Vec3.rotateToward(upS, nT, UP_RATE * dt, o.heading)
        inited = true
        fitUp = nT
        val pPrev = pos; val uPrev = up; val fPrev = fwd
        val hadSeat = o.seatOn && blend > 0
        val fwdT = o.heading.rejectFrom(nT).normalized(h0.rejectFrom(nT).normalized(Vec3.X))
        val rightT = fwdT cross nT
        up = upS
        fwd = fwdOn(o.heading, up, fPrev, h0)
        right = fwd cross up
        // 1. warm start: the last seat carried along with the sim's motion along the seated surface. A body at rest
        // stays at rest (a fresh solve from the sim position each tick could land on a different one of several rests
        // in a crevice and flick between them; on an overhang the sim's height over the surface jitters, the body
        // need not)
        var done = false
        if (hadSeat && near(pPrev, oPrev, uPrev, d0, L)) {
            pos = solveContacts(s, g, n0, L, pPrev + (o.pos - oPrev).rejectFrom(up))
            done = ok(lowestContact(s, pos, L), pos, o.pos, d0, L)
            // 2. else the last seat unmoved, while it is still clean and over the sim position
            if (!done) {
                pos = pPrev; up = uPrev; fwd = fwdOn(o.heading, up, fPrev, h0); right = fwd cross up
                done = ok(lowestContact(s, pos, L), pos, o.pos, d0, L)
            }
        }
        if (!done) {
            // 3. a fresh solve from the surface under the sim position: the fitted up, the bare surface normal, then
            // tipped fore, aft and to either side; the first clean one, else the least bad
            var bestP = pos; var bestU = up; var bestF = fwd; var bestM = -Double.MAX_VALUE
            for (tryK in 0 until 6) {
                val u0 = when (tryK) {
                    0 -> upS
                    1 -> n0
                    2 -> Vec3.rotateAbout(nT, rightT, RETRY_TILT)
                    3 -> Vec3.rotateAbout(nT, rightT, -RETRY_TILT)
                    4 -> Vec3.rotateAbout(nT, fwdT, RETRY_TILT)
                    else -> Vec3.rotateAbout(nT, fwdT, -RETRY_TILT)
                }
                up = u0
                fwd = fwdOn(o.heading, up, fPrev, h0)
                right = fwd cross up
                val pp = solveContacts(s, g, n0, L)
                val mm = lowestContact(s, pp, L)
                val good = ok(mm, pp, o.pos, d0, L)
                if (good || score(mm, pp, g, L) > score(bestM, bestP, g, L)) { bestP = pp; bestU = up; bestF = fwd; bestM = mm }
                if (good) break
            }
            pos = bestP; up = bestU; fwd = bestF; right = fwd cross up
        }
        var hop = !done
        if (hadSeat && pos.distanceTo(pPrev) < HOLD_EPS_CM && Vec3.angle(up, uPrev) < HOLD_EPS_RAD) {
            // a change under the clip tolerance: the last seat is kept exactly while still clean (no sub-mm shimmer)
            val pNew = pos; val uNew = up; val fNew = fwd
            pos = pPrev; up = uPrev; fwd = fwdOn(o.heading, up, fPrev, h0); right = fwd cross up
            if (!ok(lowestContact(s, pos, L), pos, o.pos, d0, L)) { pos = pNew; up = uNew; fwd = fNew; right = fwd cross up }
        }
        if (hadSeat) {
            // the drawn body glides rather than hops: a new seat further than the sim moved (plus a little) or turned
            // further than a crawler turns in a tick is approached by a limited step, whenever that step is itself
            // clean (otherwise the solved seat is taken at once: no clipping beats no hop)
            val stepMax = SEAT_SPEED * dt + o.pos.distanceTo(oPrev)
            // (the turn capped per tick too: at fast-forward a long tick must not swing the body about its head)
            val turnMax = min(SEAT_TURN * dt, SEAT_TURN_TICK)
            val dp = pos - pPrev
            val dA = Vec3.angle(uPrev, up)
            val dF = Vec3.angle(fPrev, fwd) - Vec3.angle(oHeadPrev, o.heading)
            if (dp.length() > stepMax || dA > turnMax || dF > turnMax) {
                val pNew = pos; val uNew = up; val fNew = fwd
                // (the Sally Lightfoot does not hop to a seat that is no cleaner, nor flip over onto another face in one
                // tick: wedged in a slot, or crossing the crest of a thin rock, the fresh solves flicked its wide body
                // between the two faces; it turns over the edge at its turn rate instead)
                val newClean = crab == null || ok(lowestContact(s, pNew, L), pNew, o.pos, d0, L) &&
                    Vec3.angle(uPrev, uNew) < CRAB_HOP_TURN && Vec3.angle(fPrev, fNew) < CRAB_HOP_TURN + Vec3.angle(oHeadPrev, o.heading)
                pos = pPrev + dp.clampLength(stepMax)
                up = if (dA > turnMax) Vec3.rotateToward(uPrev, uNew, turnMax, fNew).normalized(uNew) else uNew
                fwd = Vec3.rotateToward(fPrev, fNew, turnMax + Vec3.angle(oHeadPrev, o.heading), up).rejectFrom(up).normalized(fNew.rejectFrom(up).normalized(fPrev))
                right = fwd cross up
                if (!ok(lowestContact(s, pos, L), pos, o.pos, d0, L)) {
                    // the limited step touches something: let the contact solve settle it from there, and keep that
                    // if it stays within twice the step; only then does the body hop to the solved seat
                    val pS = pos; val uS = up; val fS = fwd
                    val pL = solveContacts(s, g, n0, L, pos)
                    if (!newClean) { if (!ok(lowestContact(s, pL, L), pL, o.pos, d0, L) || pL.distanceTo(pPrev) > 2 * stepMax ||
                            Vec3.angle(up, uPrev) > 2 * turnMax) { pos = pS; up = uS; fwd = fS; right = fwd cross up } else pos = pL }
                    else if (!(ok(lowestContact(s, pL, L), pL, o.pos, d0, L) && pL.distanceTo(pPrev) <= 2 * stepMax &&
                            Vec3.angle(up, uPrev) <= 2 * turnMax && Vec3.angle(fwd, fPrev) <= 2 * turnMax + Vec3.angle(oHeadPrev, o.heading))) {
                        pos = pNew; up = uNew; fwd = fNew; right = fwd cross up; hop = true
                    } else pos = pL
                }
            }
        }
        // wedged: the seat keeps having to hop (no clean rest carries over from the last tick); the brain walks it off
        wedge = if (hadSeat && hop) wedge + 1.0 else max(0.0, wedge - WEDGE_DECAY)
        if (crab != null) {
            // the Sally Lightfoot's wide carapace never shows inside the rock: where no seat fits (the sim crawled into
            // a slot narrower than its body, which its routes avoid and it jumps out of) the drawn body waits at the
            // last seat that was clear, while that is still within reach of the sim, rather than sink into the rock
            // (and it leaves that spot by a gliding step, never a hop across: the first clear step toward the new seat,
            // else it waits on)
            var m = lowestContact(s, pos, L, BODY_END)
            val stepMax = SEAT_SPEED * dt + o.pos.distanceTo(oPrev)
            if (hadSeat && hasClean && m >= -PEN_TOL_CM && pos.distanceTo(pClean) > 2 * stepMax) {
                val pNew = pos; val uNew = up
                pos = pClean + (pNew - pClean).clampLength(stepMax)
                up = Vec3.rotateToward(uClean, uNew, min(SEAT_TURN * dt, SEAT_TURN_TICK), fwd).normalized(uNew)
                fwd = fwdOn(o.heading, up, fClean, h0); right = fwd cross up
                m = lowestContact(s, pos, L, BODY_END)
                if (m < -PEN_TOL_CM && pClean.distanceTo(o.pos) >= CRAB_LAG_MAX * L) {
                    pos = pNew; up = uNew; fwd = fwdOn(o.heading, up, fClean, h0); right = fwd cross up
                    m = lowestContact(s, pos, L, BODY_END)
                }
            }
            if (!hadSeat) hasClean = false
            if (m < -PEN_TOL_CM && (!hasClean || pClean.distanceTo(o.pos) >= CRAB_LAG_MAX * L)) {
                // no clear seat to wait at (just seated after a leap, set down in a crevice, or left far behind): pushed out along
                // the surface normal under the deepest point, a few passes, keeping the least sunk
                var bestP = pos; var bestM = m
                for (k in 0 until DEPEN_PASSES) {
                    lowestContact(s, pos, L, BODY_END)
                    pos += s.normal(lowest) * (DEPEN_CM - m)
                    m = lowestContact(s, pos, L, BODY_END)
                    if (m > bestM) { bestM = m; bestP = pos }
                    if (m >= -PEN_TOL_CM) break
                }
                pos = bestP; m = bestM
            }
            waiting = false
            if (m < -PEN_TOL_CM && hadSeat && hasClean && pClean.distanceTo(o.pos) < CRAB_LAG_MAX * L &&
                lowestContactAt(s, pClean, uClean, fClean, L, BODY_END) >= -PEN_TOL_CM) {
                // (exactly as it was: turned in place to the sim's heading, the wide disc swung into the rock)
                pos = pClean; up = uClean; fwd = fClean; right = fwd cross up
                // and the sim does not walk on into the gap either: its step this tick is taken back, so the slot
                // is a wall to the crab (its routes stall there and re-plan round it)
                if (simOffset >= 0.0 && s.dist(oPrev) > 0.0) o.pos = oPrev
                waiting = true
            } else if (m >= -PEN_TOL_CM) { pClean = pos; uClean = up; fClean = fwd; hasClean = true }
        }
        val clean = ok(lowestContact(s, pos, L), pos, o.pos, d0, L)
        clearance = lowestContact(s, pos, L, BODY_END)
        if (kind == BrainKind.HERMIT && clean && blend >= 1.0 && simOffset >= 0.0) {
            // the sim follows the seat: where the body rests is higher or lower than where the sim walks it by more than a
            // few mm (an overhang or crevice where the single-normal crawl step thrashes): the sim takes the seat's pose
            // (the position at once when that point is itself clear of rock, sand and glass; the sim's up eases toward
            // the seat's at a crawler's turn rate and its heading is left to the steering, so neither flips or spins)
            // Only the height over the seated surface is written: the walk along the surface stays the sim's (writing
            // the whole seat pinned a hermit whose seat was held at a ledge: the sim stepped off each tick and was put
            // back, so it never reached its pick spot)
            val dh = (pos + up * simOffset - o.pos) dot up
            val simSeat = o.pos + up * dh
            if (kotlin.math.abs(dh) > WRITE_BACK_CM && s.dist(simSeat) >= 0.5 * simOffset) {
                o.pos = simSeat
                o.up = Vec3.rotateToward(o.up, up, min(WRITE_BACK_TURN * dt, SEAT_TURN_TICK), o.heading).normalized(up)
            }
        }
        upS = up
        oPrev = o.pos
        oHeadPrev = o.heading

        val hl = o.limbRig as? HermitLegs
        if (hermit != null && hl != null) poseHermit(o, hl, hermit, s, L, dt)
        if (crab != null && cl != null) poseCrab(o, cl, crab, s, L)

        o.seatOn = true
        // (unblended: [Organism.toPose] blends the whole body from the sim's spine by [blend])
        o.seatPos = pos
        o.seatUp = up
    }

    /** Body forward on [up]: the sim heading in that plane, or the last forward when the heading is nearly along up. */
    private fun fwdOn(h: Vec3, up: Vec3, prev: Vec3, h0: Vec3): Vec3 {
        val r = h.rejectFrom(up)
        if (r.lengthSq() > FWD_MIN * FWD_MIN) return r / r.length()
        val p = prev.rejectFrom(up)
        return if (p.lengthSq() > FWD_MIN * FWD_MIN) p / p.length() else r.normalized(h0.rejectFrom(up).normalized(Vec3.X))
    }

    /** A footprint sample: cast down onto the surface from above; over an edge (or inside a wall) it stays in the plane. */
    private fun footprint(s: CrawlSurface, g: Vec3, n: Vec3, off: Vec3, reach: Double): Vec3 {
        val q = g + off + n * reach
        val t = s.cast(q, n * -1.0, 2.5 * reach)
        return if (t > 0) q - n * t else g + off
    }

    /**
     * Rigid contact solve (position-based): every contact point inside rock, sand or glass is pushed out along its own
     * surface normal, and the body takes the mean of those pushes as a translation plus the matching small rotation
     * about its centre (so a shell against a wall tips up it instead of sliding through). With nothing inside, the body
     * settles along the normal under its lowest point until that point touches. A final pass pushes out anything still
     * inside (no penetration beats no gap).
     */
    private fun solveContacts(s: CrawlSurface, g: Vec3, n0: Vec3, L: Double, start: Vec3 = g): Vec3 {
        val c = shape.contacts
        val n = c.size / 3
        var p = start
        val tilt0 = fitUp
        for (iter in 0 until SOLVE_ITERS) {
            // centre of the contact set, the rotation pivot
            val cx = shape.cx; val cy = shape.cy; val cz = shape.cz
            val centre = p + (fwd * cx + up * cy + right * cz) * L
            var dp = Vec3.ZERO
            var tq = Vec3.ZERO
            var rr = 0.0
            var pen = 0
            var minD = Double.MAX_VALUE
            var i = 0
            while (i < c.size) {
                val w = p + (fwd * c[i] + up * c[i + 1] + right * c[i + 2]) * L
                val d = s.dist(w)
                if (d < minD) { minD = d; lowest = w }
                if (d < REST_CM - PEN_TOL_CM) {
                    val corr = s.normal(w) * (REST_CM - d)
                    dp += corr
                    val r = w - centre
                    tq += r cross corr
                    rr += r.lengthSq()
                    pen++
                }
                i += 3
            }
            if (pen == 0) {
                val err = REST_CM - minD
                if (kotlin.math.abs(err) < 2e-3) break
                // settle onto the surface along the normal under the lowest point (the body's up can lag the surface
                // over an edge, and settling along it slid the shell along a face)
                p += s.normal(lowest) * err.coerceIn(-STEP_MAX * L, STEP_MAX * L)
                continue
            }
            p += (dp / pen.toDouble()).clampLength(STEP_MAX * L)
            if (rr > 1e-9) {
                val rot = (tq / (rr + 1e-9)).clampLength(TURN_STEP)
                val ang = rot.length()
                if (ang > 1e-6) {
                    val axis = rot / ang
                    val newUp = Vec3.rotateAbout(up, axis, ang)
                    // the solved tilt stays within reach of the fitted surface up
                    if (Vec3.angle(newUp, tilt0) < TILT_MAX) {
                        val pc = p + (fwd * cx + up * cy + right * cz) * L
                        up = newUp.normalized(up)
                        fwd = Vec3.rotateAbout(fwd, axis, ang).rejectFrom(up).normalized(fwd)
                        right = fwd cross up
                        p = pc - (fwd * cx + up * cy + right * cz) * L
                    }
                }
            }
        }
        // no point may stay inside: push out along the deepest point's normal
        for (iter in 0 until 4) {
            val minD = lowestContact(s, p, L)
            if (minD >= -PEN_TOL_CM) break
            p += s.normal(lowest) * (REST_CM - minD).coerceAtMost(STEP_MAX * L)
        }
        // never far from where the sim walks it (a body wedged where it does not fit is not flung out of the crevice)
        val off = p - g
        val lim = OFFSET_MAX * L
        if (off.lengthSq() > lim * lim) p = g + off * (lim / off.length())
        // still inside (a pocket narrower than the shell, a knob under it): the smallest lift along the body's up, then
        // along the surface normal at the sim position, that clears every point
        if (lowestContact(s, p, L) < -PEN_TOL_CM) {
            p = liftClear(s, p, up, L) ?: liftClear(s, p, n0, L) ?: p
        }
        // a gap left under the lowest point: settle as far as nothing goes inside (toward the surface under it, then
        // along the body's up, then along the normal at the sim position)
        for (iter in 0 until 6) {
            val minD = lowestContact(s, p, L)
            if (minD <= REST_CM + 0.01 || minD < -PEN_TOL_CM) break
            val dir = when (iter % 3) { 0 -> s.normal(lowest) * -1.0; 1 -> up * -1.0; else -> n0 * -1.0 }
            val full = minD - REST_CM
            if (lowestContact(s, p + dir * full, L) >= -PEN_TOL_CM) { p += dir * full; continue }
            var lo = 0.0; var hi = full
            repeat(7) { val mid = 0.5 * (lo + hi); if (lowestContact(s, p + dir * mid, L) >= -PEN_TOL_CM) lo = mid else hi = mid }
            p += dir * lo
        }
        if (n == 0) return g
        return p
    }

    private var lowest = Vec3.ZERO

    /** Seat [p] (up [u]) still over the sim position [q] (height [d0] over its surface): along the surface within
     *  [NEAR_MAX] body lengths, across it within that plus the height. */
    private fun near(p: Vec3, q: Vec3, u: Vec3, d0: Double, L: Double): Boolean {
        val d = p - q
        val h = d dot u
        return (d - u * h).length() <= NEAR_MAX * L && kotlin.math.abs(h) <= NEAR_MAX * L + d0
    }

    /** A clean seat: nothing inside, at most [HOLD_GAP_CM] under the lowest point, and over the sim position. */
    private fun ok(minD: Double, p: Vec3, q: Vec3, d0: Double, L: Double) =
        minD >= -PEN_TOL_CM && minD <= holdGap && near(p, q, up, d0, L)

    /** (the Sally Lightfoot's legs reach down to the surface wherever its body stands, so a seat a few mm over a hollow
     *  is held rather than dropped for a hop to a different rest) */
    private val holdGap = if (crab != null) CRAB_HOLD_GAP_CM else HOLD_GAP_CM

    /** Ranks solve results: nothing inside first, then the smallest gap, then nearest the sim position. */
    private fun score(minD: Double, p: Vec3, g: Vec3, L: Double): Double =
        (if (minD < -PEN_TOL_CM) -100.0 + minD else -kotlin.math.abs(minD - REST_CM)) - 0.01 * p.distanceTo(g) / L

    private fun liftClear(s: CrawlSurface, p: Vec3, dir: Vec3, L: Double): Vec3? {
        var h = 0.04 * L
        while (h <= LIFT_MAX * L) {
            val q = p + dir * h
            if (lowestContact(s, q, L) >= -PEN_TOL_CM) {
                // back down to touching
                var lo = h - 0.04 * L; var hi = h
                repeat(4) { val mid = 0.5 * (lo + hi); if (lowestContact(s, p + dir * mid, L) >= -PEN_TOL_CM) hi = mid else lo = mid }
                return p + dir * hi
            }
            h += 0.04 * L
        }
        return null
    }

    /** Distance of the lowest contact point with the body origin at [p] (the point itself goes to [lowest]). */
    /** The Sally Lightfoot's drawn body is waiting at [waitPos], the last seat where it fitted (the sim is in a gap). */
    var waiting = false; private set
    val waitPos: Vec3 get() = pClean

    /** Last clear Sally Lightfoot seat (see [update]). */
    private var pClean = Vec3.ZERO
    private var uClean = Vec3.Y
    private var fClean = Vec3.X
    private var hasClean = false

    /** The crab's carapace and coxae only: its stance contacts stand in for the feet the leg IK places. */
    private val BODY_END = if (crab != null) CrabModel.STANCE_FROM * 3 else shape.contacts.size

    private fun lowestContactAt(s: CrawlSurface, p: Vec3, u: Vec3, f: Vec3, L: Double, end: Int): Double {
        val u0 = up; val f0 = fwd; val r0 = right
        up = u; fwd = f; right = f cross u
        val m = lowestContact(s, p, L, end)
        up = u0; fwd = f0; right = r0
        return m
    }

    private fun lowestContact(s: CrawlSurface, p: Vec3, L: Double, end: Int = shape.contacts.size): Double {
        val c = shape.contacts
        var minD = Double.MAX_VALUE
        var i = 0
        while (i < end) {
            val w = p + (fwd * c[i] + up * c[i + 1] + right * c[i + 2]) * L
            val d = s.dist(w)
            if (d < minD) { minD = d; lowest = w }
            i += 3
        }
        return minD
    }

    // ------------------------------------------------------------------ hermit crab

    private val tmp = DoubleArray(3)

    private fun poseHermit(o: Organism, rig: HermitLegs, m: HermitModel, s: CrawlSurface, L: Double, dt: Double) {
        val out = rig.out
        val legsOut = rig.retract < LEG_RETRACT_MAX
        legsPlanted = legsOut
        for (k in 0 until 4) {
            if (!legsOut) { planted[k] = false; wasSwing[k] = false; tuck[k] = 0.0; legTucked[k] = false; continue }
            val ph = out[k * 4].toDouble()
            stroke[k] = out[k * 4 + 1].toDouble()
            val lift = out[k * 4 + 2].toDouble()
            val swing = ph < SWING
            val e = 1.0 - 2.0 * (ph - SWING) / (1.0 - SWING)
            plantLeg(s, m, k, out, L, swing, ph / SWING, e, lift * SWING_LIFT * m.legW[k] * L)
            out[k * 4 + 3] = max(out[k * 4 + 3], smoothstep(0.0, 1.0, tuck[k]).toFloat())
        }
        // chelae: never dip below the surface under the tip and the palm
        for (cI in 0 until 2) {
            val cm = clawLimit(s, m, cI, L)
            clawMaxDown[cI] = cm
            // the limit only matters below the deepest dip the pick stroke can reach (`fish.vert`: down = pick ·
            // (0.55 w − 0.15) + 0.1 moving, w ≤ 1), so the value sent stays at that envelope while the claw is clear
            // and the pose signal does not leap whenever the surface under the claw changes
            val a = (4 + cI) * 4
            val reach = out[a + 1] * 0.4f + 0.1f * out[a + 2] + 0.05f
            out[14 * 4 + 1 + cI] = ease(out[14 * 4 + 1 + cI], min(cm.toFloat(), reach))
        }
        out[14 * 4] = 0f
        out[14 * 4 + 3] = if (legsOut) 1f else 0f
        // antennae, antennules and eyestalks bend away from anything they would reach into
        val front = rig.retract < 0.5
        val wantAnt = if (front) bendNeeded { b -> antennaClear(s, m, rig, b, L) } else 0.0
        val wantAntl = if (front) bendNeeded { b -> antennuleClear(s, m, rig, b, L) } else 0.0
        val wantEye = if (front) bendNeeded { b -> eyeClear(s, m, b, L) } else 0.0
        antennaBend = approach(antennaBend, wantAnt, dt)
        antennuleBend = approach(antennuleBend, wantAntl, dt)
        eyeBend = approach(eyeBend, wantEye, dt)
        out[8 * 4 + 2] = antennaBend.toFloat()
        out[9 * 4 + 2] = antennuleBend.toFloat()
        out[6 * 4 + 3] = eyeBend.toFloat()
    }

    /** Stance feet re-placed mid-stance because the hip could no longer reach them or they ended up inside (tests). */
    var replants = 0L; private set

    /** Hermit fore-aft stroke per leg (rad) for [footFor]. */
    private val stroke = DoubleArray(MAX_LEGS)

    /**
     * One planted leg (SIMULATION §19.6): in stance the foot holds the foothold it touched down on (re-placed only when
     * the hip can no longer reach it or it ends up inside), in swing it travels from where it lifted to the next
     * foothold, [liftH] cm clear of the surface at mid-swing; then the IK reaches it and a pose whose eased sweep
     * would cross rock folds the leg. [q] is the swing progress 0..1, [e] the stance progress (+1 touch-down .. −1
     * lift-off). Writes IK entry 10 + [k] of [out], [tuck] and [footTip].
     */
    private fun plantLeg(s: CrawlSurface, m: LegModel, k: Int, out: FloatArray, L: Double, swing: Boolean, q: Double, e: Double, liftH: Double) {
        val target: Vec3
        if (!swing) {
            if (!planted[k] || wasSwing[k] || !reachable(m, k, foot[k], L) || s.dist(foot[k]) < -FOOT_TOL_CM) {
                if (planted[k] && !wasSwing[k]) replants++
                foot[k] = footFor(s, m, k, e.coerceIn(-1.0, 1.0), L)
                planted[k] = true
            }
            target = foot[k]
            liftFrom[k] = foot[k]
        } else {
            if (!wasSwing[k] && !planted[k]) liftFrom[k] = footFor(s, m, k, -1.0, L)
            val end = footFor(s, m, k, 1.0, L)
            var base = liftFrom[k].lerp(end, smoothstep(0.0, 1.0, q))
            val t = s.cast(base + up * (0.3 * L), up * -1.0, 0.8 * L)
            if (t > 0) base = base + up * (0.3 * L - t + FOOT_R * L)
            target = base + up * (liftH * sin(PI * q))
            planted[k] = false
            foot[k] = end
        }
        wasSwing[k] = swing
        footSwing[k] = swing
        footTarget[k] = target
        var clear = solveLeg(s, m, k, target, L)
        val j = (10 + k) * 4
        val o0 = out[j]; val o1 = out[j + 1]; val o2 = out[j + 2]; val o3 = out[j + 3]
        out[j] = ease(o0, (FLEX0 + FLEX_SCALE * ikFlex[k]).toFloat())
        out[j + 1] = ease(o1, ikRot[k * 3].toFloat())
        out[j + 2] = ease(o2, ikRot[k * 3 + 1].toFloat())
        out[j + 3] = ease(o3, ikRot[k * 3 + 2].toFloat())
        if (legInside(s, m, k, L, out)) {
            // the eased sweep toward the new pose would cross rock: sweep with the leg drawn in (shortened, knee
            // raised); if even that crosses, the leg is pulled in to the body until it has a clear path
            out[j] = FLEX0 + FLEX_SCALE * FLEX_MAX.toFloat()
            if (legInside(s, m, k, L, out)) clear = false
        }
        tuck[k] = if (clear) max(0.0, tuck[k] - TUCK_STEP) else min(1.0, tuck[k] + TUCK_STEP)
        legTucked[k] = tuck[k] > 0.0
        footTip[k] = posedLeg(m, k, m.tip, k * 3, 1.0, L, out)
    }

    /** Foothold of leg [k] at stance progress [e] (+1 = touch-down, −1 = lift-off). */
    private fun footFor(s: CrawlSurface, m: LegModel, k: Int, e: Double, L: Double): Vec3 =
        if (m is CrabModel) crabFoothold(s, m, k, e, L) else foothold(s, m, k, stroke[k] * e, L)

    // ------------------------------------------------------------------ Sally Lightfoot

    /** Pressed flat: the stance contacts rise in the body frame, so the solve lowers the carapace onto the surface. */
    private fun crabStance(rig: CrabLegs) {
        crabTuck = rig.tuckAmt
        val c = shape.contacts
        val y = CrabModel.STANCE_Y + CrabModel.FLAT_DROP * crabTuck
        var i = CrabModel.STANCE_FROM * 3
        while (i < c.size) { c[i + 1] = y; i += 3 }
    }

    private fun poseCrab(o: Organism, rig: CrabLegs, m: CrabModel, s: CrawlSurface, L: Double) {
        val out = rig.out
        // half a stance stroke along the travel: where a foot set down now is passed by the body at mid-stance
        // (stance lasts (1 − swing share) of a cycle at the rig's stride rate)
        val v = o.vel
        val stance = (1.0 - CRAB_SWING) / max(rig.strideHz, 1.0)
        var sx = 0.5 * (v dot fwd) * stance / L * rig.stepping
        var sz = 0.5 * (v dot right) * stance / L * rig.stepping
        val sl = kotlin.math.sqrt(sx * sx + sz * sz)
        if (sl > CRAB_STRIDE_MAX) { sx *= CRAB_STRIDE_MAX / sl; sz *= CRAB_STRIDE_MAX / sl }
        strideX += (sx - strideX) * 0.5; strideZ += (sz - strideZ) * 0.5
        legsPlanted = true
        val stepping = rig.stepping > 0.1
        for (k in 0 until 8) {
            val ph = out[k * 4].toDouble()
            val swing = stepping && ph < CRAB_SWING
            val e = if (stepping) 1.0 - 2.0 * (ph - CRAB_SWING) / (1.0 - CRAB_SWING) else 0.0
            // the phase wrapped since the last tick: a whole swing went by between two ticks (a long fast-forward tick),
            // so this stance is on a new foothold
            if (!swing && ph < lastPh[k] - 1e-6) wasSwing[k] = true
            lastPh[k] = ph
            plantLeg(s, m, k, out, L, swing, ph / CRAB_SWING, e, CRAB_LIFT * rig.stepping * (1.0 - 0.6 * crabTuck) * m.legW[k] * L)
            // planted, the gait's own tuck is in the footholds; w carries only the fold of a leg with no clear pose
            out[k * 4 + 3] = smoothstep(0.0, 1.0, tuck[k]).toFloat()
        }
        for (cI in 0 until 2) {
            val cm = clawLimit(s, m, cI, L)
            clawMaxDown[cI] = cm
            // deepest dip the pick stroke can reach (`fish.vert` crabLimb: down = stroke · 0.5 w + 0.15 (reach − 0.5))
            val a = (8 + cI) * 4
            val reach = out[a + 1] * 0.5f + 0.15f * (out[a + 2] - 0.5f) + 0.05f
            out[18 * 4 + 1 + cI] = ease(out[18 * 4 + 1 + cI], min(cm.toFloat(), reach))
        }
        out[18 * 4] = 0f
        out[18 * 4 + 3] = 1f
    }

    /** Leg [k]'s rest tip (pulled toward the hip as the crab presses flat) plus [e] half strokes along the travel, cast onto the surface. */
    private fun crabFoothold(s: CrawlSurface, m: CrabModel, k: Int, e: Double, L: Double): Vec3 {
        val i = k * 3
        val hx = m.hip[i]; val hz = m.hip[i + 2]
        // (a little inside the rest reach: the trailing legs extend through the stroke and must not run out of leg)
        val keep = CRAB_STAND_REACH - CRAB_TUCK_PULL * crabTuck
        val x = hx + (m.tip[i] - hx) * keep + strideX * e
        val z = hz + (m.tip[i + 2] - hz) * keep + strideZ * e
        val w = world(x, m.tip[i + 1], z, L)
        val f = castFoot(s, w, L)
        if (onSurface(s, f, L) && reachable(m, k, f, L)) return f
        // the ground under the rest tip is out of reach (a ledge, the crest of a rock, a slot between two faces): the
        // nearest surface within reach of the hip instead, wrapping the foot round an edge or onto the other face,
        // then the same from reaches nearer the body; the dactyls grip whatever face they find (research 27)
        val hip = world(hx, m.hip[i + 1], hz, L)
        for (r in CRAB_REACH_TRY) {
            val q = if (r >= 1.0) w else hip + (w - hip) * r
            val p = project(s, q, L)
            if (onSurface(s, p, L) && reachable(m, k, p, L)) return p
            if (r < 1.0) { val c = castFoot(s, q, L); if (onSurface(s, c, L) && reachable(m, k, c, L)) return c }
        }
        return f
    }

    private fun onSurface(s: CrawlSurface, p: Vec3, L: Double) = kotlin.math.abs(s.dist(p) - FOOT_R * L) < FOOT_ON_CM

    /** [q] moved onto the nearest surface along the distance field (a foot tip radius off it). */
    private fun project(s: CrawlSurface, q0: Vec3, L: Double): Vec3 {
        var q = q0
        repeat(3) { q -= s.normal(q) * (s.dist(q) - FOOT_R * L) }
        return q
    }

    /** True when the drawn leg [k] (tip, knee or propodus as [out] poses it) is inside the surface. */
    private fun legInside(s: CrawlSurface, m: LegModel, k: Int, L: Double, out: FloatArray): Boolean =
        s.dist(posedLeg(m, k, m.tip, k * 3, 1.0, L, out)) < -FOOT_TOL_CM ||
            s.dist(posedLeg(m, k, m.knee, k * 3, m.kneeT, L, out)) < -FOOT_TOL_CM ||
            s.dist(posedLeg(m, k, m.mid, k * 3, MID_T, L, out)) < -FOOT_TOL_CM

    private fun clearLegs(rig: LimbRig) {
        for (i in 40 until rig.out.size) rig.out[i] = 0f
        if (rig !is HermitLegs) return
        rig.out[8 * 4 + 2] = 0f; rig.out[9 * 4 + 2] = 0f; rig.out[6 * 4 + 3] = 0f
        antennaBend = 0.0; antennuleBend = 0.0; eyeBend = 0.0
    }

    /** Limits a pose value's change per tick (the renderer interpolates snapshots; a jump reads as a glitch). */
    private fun ease(cur: Float, want: Float): Float {
        val d = want - cur
        return cur + d.coerceIn(-MAX_STEP, MAX_STEP)
    }

    private fun approach(cur: Double, want: Double, dt: Double): Double =
        if (want > cur) min(want, cur + MAX_STEP) else max(want, cur - BEND_RELAX * dt)

    /** Smallest bend from [BENDS] that clears; 1 when none does. */
    private inline fun bendNeeded(clear: (Double) -> Boolean): Double {
        for (b in BENDS) if (clear(b)) return b
        return 1.0
    }

    private fun reachable(m: LegModel, k: Int, w: Vec3, L: Double): Boolean {
        toModel(w, L, tmp)
        val bx = tmp[0] - m.hip[k * 3]; val by = tmp[1] - m.hip[k * 3 + 1]; val bz = tmp[2] - m.hip[k * 3 + 2]
        val r = sqrt(bx * bx + by * by + bz * bz) / m.chord[k]
        return r in REACH_MIN..REACH_MAX
    }

    /**
     * Foothold for leg [k] at stroke angle [yaw] (the shader's fore-aft swing about the hip): the rest tip, swung,
     * cast down onto the surface from above; inside a wall it is projected out instead; over an edge it hangs.
     */
    private fun foothold(s: CrawlSurface, m: LegModel, k: Int, yaw: Double, L: Double): Vec3 {
        val sg = if (m.hip[k * 3 + 2] >= 0) 1.0 else -1.0
        val ang = yaw * sg
        val hx = m.hip[k * 3]; val hz = m.hip[k * 3 + 2]
        val vx = m.tip[k * 3] - hx; val vz = m.tip[k * 3 + 2] - hz
        val c = kotlin.math.cos(ang); val sn = sin(ang)
        // rotation about +Y: x' = x c + z s, z' = −x s + z c
        return castFoot(s, world(hx + vx * c + vz * sn, m.tip[k * 3 + 1], hz - vx * sn + vz * c, L), L)
    }

    /** A foothold over model point [w]: cast down onto the surface from above; inside a wall it is projected out; over an edge it hangs. */
    private fun castFoot(s: CrawlSurface, w: Vec3, L: Double): Vec3 {
        val top = w + up * (0.3 * L)
        val t = s.cast(top, up * -1.0, 0.75 * L)
        return when {
            t > 0 -> top + up * (FOOT_R * L - t)
            t == 0.0 -> {
                var q = w
                repeat(2) { q -= s.normal(q) * (s.dist(q) - FOOT_R * L) }
                q
            }
            else -> top - up * (0.75 * L)
        }
    }

    /** IK to [target]: rotation of the rest leg about the hip onto it, then flex; a knee in rock pulls the foot in. */
    /** Solves leg [k] onto [target] (or a clear foothold near it); false when no pose keeps the leg out of rock. */
    private fun solveLeg(s: CrawlSurface, m: LegModel, k: Int, target: Vec3, L: Double): Boolean {
        best = -Double.MAX_VALUE
        if (tryLeg(s, m, k, target, L)) return true
        // the foothold cannot be reached without a leg segment in rock (or ends inside): search the surface around
        // the hip for one that can, nearest the intended stance first (shorter reach folds the leg and lifts its knee)
        val i = k * 3
        val hx = m.hip[i]; val hz = m.hip[i + 2]
        toModel(target, L, tmp)
        val ox = tmp[0] - hx; val oz = tmp[2] - hz
        for (f in REACH_TRY) for (yaw in YAW_TRY) {
            val c = kotlin.math.cos(yaw); val sn = sin(yaw)
            val w = world(hx + (ox * c + oz * sn) * f, 0.0, hz + (-ox * sn + oz * c) * f, L)
            val top = w + up * (0.3 * L)
            val t = s.cast(top, up * -1.0, 0.8 * L)
            if (t <= 0) continue
            if (tryLeg(s, m, k, top + up * (FOOT_R * L - t), L)) return true
        }
        // nowhere to stand: the leg folds up beside the body
        if (m is CrabModel) {
            // (the crab's long legs fold against the carapace rim: tip out at half and a third of the rest reach, knee up)
            val dx = m.tip[i] - hx; val dz = m.tip[i + 2] - hz
            for (f in CRAB_FOLD) if (tryLeg(s, m, k, world(hx + dx * f, m.hip[i + 1] + 0.12 * (1 - f), hz + dz * f, L), L)) return true
            ikRot[i] = bestRot[0]; ikRot[i + 1] = bestRot[1]; ikRot[i + 2] = bestRot[2]; ikFlex[k] = bestRot[3]
            return false
        }
        val sg = if (hz >= 0) 1.0 else -1.0
        if (tryLeg(s, m, k, world(hx + 0.06, m.hip[i + 1] + 0.1, hz + sg * 0.16, L), L)) return true
        if (tryLeg(s, m, k, world(hx + 0.02, m.hip[i + 1] + 0.2, hz + sg * 0.1, L), L)) return true
        if (tryLeg(s, m, k, world(hx - 0.04, m.hip[i + 1] + 0.05, hz + sg * 0.2, L), L)) return true
        ikRot[i] = bestRot[0]; ikRot[i + 1] = bestRot[1]; ikRot[i + 2] = bestRot[2]; ikFlex[k] = bestRot[3]
        return false
    }

    private var best = 0.0

    /** IK onto [tg]; true when the tip, knee and propodus are all clear. Keeps the least-bad solution in [bestRot]. */
    private fun tryLeg(s: CrawlSurface, m: LegModel, k: Int, tg: Vec3, L: Double): Boolean {
        val i = k * 3
        ik(m, k, tg, L)
        val score = min(s.dist(posedLegLocal(m, k, m.tip, i, 1.0, L)),
            min(s.dist(posedLegLocal(m, k, m.knee, i, m.kneeT, L)), s.dist(posedLegLocal(m, k, m.mid, i, MID_T, L))))
        if (score > best) { best = score; bestRot[0] = ikRot[i]; bestRot[1] = ikRot[i + 1]; bestRot[2] = ikRot[i + 2]; bestRot[3] = ikFlex[k] }
        return score >= -FOOT_TOL_CM
    }

    private val bestRot = DoubleArray(4)

    private fun ik(m: LegModel, k: Int, target: Vec3, L: Double) {
        toModel(target, L, tmp)
        val i = k * 3
        val ax = m.tip[i] - m.hip[i]; val ay = m.tip[i + 1] - m.hip[i + 1]; val az = m.tip[i + 2] - m.hip[i + 2]
        var bx = tmp[0] - m.hip[i]; var by = tmp[1] - m.hip[i + 1]; var bz = tmp[2] - m.hip[i + 2]
        val la = m.chord[k]
        var lb = sqrt(bx * bx + by * by + bz * bz)
        if (lb > REACH_MAX * la) { val f = REACH_MAX * la / lb; bx *= f; by *= f; bz *= f; lb = REACH_MAX * la }
        val cx = ay * bz - az * by; val cy = az * bx - ax * bz; val cz = ax * by - ay * bx
        val cl = sqrt(cx * cx + cy * cy + cz * cz)
        val ang = atan2(cl, ax * bx + ay * by + az * bz)
        if (cl > 1e-9) { ikRot[i] = cx / cl * ang; ikRot[i + 1] = cy / cl * ang; ikRot[i + 2] = cz / cl * ang }
        else { ikRot[i] = 0.0; ikRot[i + 1] = 0.0; ikRot[i + 2] = 0.0 }
        ikFlex[k] = (1.0 - lb / la).coerceIn(FLEX_MIN, FLEX_MAX)
    }

    /** World position of rest leg point (pts[[o]..]) at limb parameter [t] posed by the current [ikRot] / [ikFlex]. */
    private fun posedLegLocal(m: LegModel, k: Int, pts: DoubleArray, o: Int, t: Double, L: Double): Vec3 {
        val i = k * 3
        return posed(m, k, pts, o, t, L, ikRot[i], ikRot[i + 1], ikRot[i + 2], ikFlex[k])
    }

    /** As the renderer draws it, from the pose entries in [out] (what the snapshot carries). */
    fun posedLeg(m: LegModel, k: Int, pts: DoubleArray, o: Int, t: Double, L: Double, out: FloatArray): Vec3 {
        val j = (10 + k) * 4
        return posed(m, k, pts, o, t, L, out[j + 1].toDouble(), out[j + 2].toDouble(), out[j + 3].toDouble(),
            (out[j] - FLEX0) / FLEX_SCALE.toDouble())
    }

    /** `fish.vert` hermitLimb / crabLimb, planted legs: rotate about the hip, shorten by flex along t, raise the knee. */
    private fun posed(m: LegModel, k: Int, pts: DoubleArray, o: Int, t: Double, L: Double, rx: Double, ry: Double, rz: Double, flex: Double): Vec3 {
        val i = k * 3
        var v = Vec3(pts[o] - m.hip[i], pts[o + 1] - m.hip[i + 1], pts[o + 2] - m.hip[i + 2])
        val ang = sqrt(rx * rx + ry * ry + rz * rz)
        if (ang > 1e-6) v = Vec3.rotateAbout(v, Vec3(rx / ang, ry / ang, rz / ang), ang)
        v *= 1.0 - flex * t
        val r = 1.0 - flex
        v = Vec3(v.x, v.y + KNEE_RAISE * sqrt(max(0.0, 1.0 - r * r)) * m.legW[k] * sin(PI * t), v.z)
        return world(m.hip[i] + v.x, m.hip[i + 1] + v.y, m.hip[i + 2] + v.z, L)
    }

    /** Largest claw dip (rad, `fish.vert` rotation about −Z) that keeps the claw tip and palm on or above the surface. */
    private fun clawLimit(s: CrawlSurface, m: LegModel, c: Int, L: Double): Double =
        min(clawPointLimit(s, m, m.clawTip, c * 3, L), clawPointLimit(s, m, m.clawLow, c * 3, L)).coerceIn(CLAW_DOWN_MIN, CLAW_DOWN_MAX)

    private fun clawPointLimit(s: CrawlSurface, m: LegModel, pts: DoubleArray, i: Int, L: Double): Double {
        val w = world(pts[i], pts[i + 1], pts[i + 2], L)
        // the surface height under the point in the body frame (its clearance taken off its height)
        val floor = pts[i + 1] - s.dist(w) / L
        val vx = pts[i] - m.clawPivot[i]; val vy = pts[i + 1] - m.clawPivot[i + 1]
        val r = sqrt(vx * vx + vy * vy)
        if (r < 1e-6) return CLAW_DOWN_MAX
        val phi = atan2(vy, vx)
        val s0 = (floor + CLAW_CLEAR - m.clawPivot[i + 1]) / r
        return when {
            s0 <= -1.0 -> CLAW_DOWN_MAX
            s0 >= 1.0 -> phi - PI / 2
            else -> phi - asin(s0)
        }
    }

    /** `fish.vert` antenna sweep (k 8) at limb parameter [t] for [side] (+1 = right, model +Z). */
    private fun antennaPoint(m: HermitModel, rig: HermitLegs, pts: DoubleArray, side: Int, t: Double, bend: Double, L: Double): Vec3 {
        val o = if (side > 0) 3 else 0
        val A0 = rig.out[8 * 4].toDouble(); val A1 = rig.out[8 * 4 + 1].toDouble()
        val pw = m.antBase[3]
        val ph = 2 * PI * A0 + side * 1.3
        val h2 = t * t
        val off = Vec3(
            pts[o] - m.antBase[0] + h2 * A1 * 0.08 * pw * sin(0.5 * ph),
            pts[o + 1] - m.antBase[1] + h2 * A1 * 0.18 * pw * sin(ph - t * 3.0),
            pts[o + 2] - m.antBase[2] + h2 * A1 * 0.14 * pw * kotlin.math.cos(0.7 * ph - t * 2.5) * side,
        )
        val v = bendFeeler(off, side.toDouble(), bend)
        return world(m.antBase[0] + v.x, m.antBase[1] + v.y, m.antBase[2] + v.z, L)
    }

    private fun antennaClear(s: CrawlSurface, m: HermitModel, rig: HermitLegs, b: Double, L: Double): Boolean {
        for (side in SIDES) {
            if (s.dist(antennaPoint(m, rig, m.antTip, side, 1.0, b, L)) < FEELER_CLEAR_CM) return false
            if (s.dist(antennaPoint(m, rig, m.antMid, side, 0.5, b, L)) < FEELER_CLEAR_CM) return false
        }
        return true
    }

    private fun antennuleClear(s: CrawlSurface, m: HermitModel, rig: HermitLegs, b: Double, L: Double): Boolean {
        // worst case of the downward flick (fish.vert: off.y −= t² · flick · f · 0.3 · w, f ≤ 1)
        val flick = rig.out[9 * 4 + 1].toDouble() * 0.3 * m.antlBase[3]
        for (side in SIDES) {
            val o = if (side > 0) 3 else 0
            val off = Vec3(m.antlTip[o] - m.antlBase[0], m.antlTip[o + 1] - m.antlBase[1] - flick, m.antlTip[o + 2] - m.antlBase[2])
            val v = bendFeeler(off, side.toDouble(), b)
            if (s.dist(world(m.antlBase[0] + v.x, m.antlBase[1] + v.y, m.antlBase[2] + v.z, L)) < FEELER_CLEAR_CM) return false
        }
        return true
    }

    private fun eyeClear(s: CrawlSurface, m: HermitModel, b: Double, L: Double): Boolean {
        for (side in SIDES) {
            val o = if (side > 0) 3 else 0
            val w = world(m.eyeTip[o] - EYE_BACK * b * m.eyeW, m.eyeTip[o + 1] - EYE_DOWN * b * m.eyeW, m.eyeTip[o + 2], L)
            if (s.dist(w) < FEELER_CLEAR_CM) return false
        }
        return true
    }

    companion object {
        const val SURFACE_TOL_CM = 1.5
        const val BLEND_RATE = 3.0
        /** Weight of the footprint plane fit against the normal under the body centre. */
        const val FIT_WEIGHT = 0.7
        /** The seated up turns at most this fast (rad/s). */
        const val UP_RATE = 4.0
        const val SOLVE_ITERS = 10
        /** Largest solve rotation per iteration and largest tilt off the fitted surface up (rad). */
        const val TURN_STEP = 0.25
        const val TILT_MAX = 1.0
        /** Largest lift (× length) tried to clear a body that does not fit where it stands. */
        const val LIFT_MAX = 0.6
        /** Largest single solve step and largest seat offset from the surface point under the sim position (× length). */
        const val STEP_MAX = 0.25
        const val OFFSET_MAX = 0.6
        /** Largest gap (cm) under the lowest contact point of a clean seat; tilt (rad) of the fresh-solve retries. */
        const val HOLD_GAP_CM = 0.1
        const val CRAB_HOLD_GAP_CM = 0.5
        /** Largest turn (rad) of the crab's seat taken as a hop in one tick. */
        const val CRAB_HOP_TURN = 0.8
        /** How far (× length) the sim may get from the last clear Sally Lightfoot seat the drawn body waits at. */
        const val CRAB_LAG_MAX = 1.6
        /** Passes, and the margin (cm) left, pushing a sunk Sally Lightfoot seat with no clear one to wait at out of the rock. */
        const val DEPEN_PASSES = 4
        const val DEPEN_CM = 0.01
        /** How far (× length) a kept seat may sit from the sim position before a fresh solve replaces it. */
        const val NEAR_MAX = 1.0
        /** Fastest the drawn seat moves (cm/s, on top of the sim's own motion) and turns (rad/s) while it stays clean. */
        const val SEAT_SPEED = 10.0
        const val SEAT_TURN = 6.0
        const val SEAT_TURN_TICK = 0.4
        /** Below this sine of the heading's angle off the body plane the last forward is kept. */
        const val FWD_MIN = 0.3
        /** Seat changes under these (cm, rad) are not drawn: the last seat is held while clean. */
        const val HOLD_EPS_CM = 0.1
        const val HOLD_EPS_RAD = 0.05
        /** Seat (raised by the brain's surface offset) this far from the sim position (cm) is written back to the sim. */
        const val WRITE_BACK_CM = 0.3
        /** Rate (rad/s) at which the written-back sim up follows the seat's. */
        const val WRITE_BACK_TURN = 3.0
        /** Hop count (each hop +1, each clean carry −[WEDGE_DECAY]) at which the seat reports [wedged]. */
        const val WEDGE_TICKS = 4.0
        const val WEDGE_DECAY = 0.25
        /** Seat the brittle star and astrea too (see [forOrganism]). */
        const val SEAT_OTHER_CRAWLERS = false
        const val RETRY_TILT = 0.6
        /** Rest clearance of the lowest contact point and the penetration the solve tolerates before pushing (cm). */
        const val REST_CM = 0.02
        const val PEN_TOL_CM = 0.005
        const val FOOT_TOL_CM = 0.03
        /** Foot tip radius (model units): the dactyl tips sit this far off the ground in the model. */
        const val FOOT_R = 0.004
        /** Stance / swing split of the step cycle (`fish.vert`: swing for the first 40 %). */
        const val SWING = 0.4
        /** Swing foot clearance per unit of rig lift (× rest leg length), as `fish.vert`'s procedural step. */
        const val SWING_LIFT = 0.22
        const val REACH_MIN = 0.45
        const val REACH_MAX = 1.08
        const val FLEX_MIN = -0.08
        const val FLEX_MAX = 0.5
        /** The flex travels in the x (phase) slot: x = FLEX0 + FLEX_SCALE · flex, inside (0, 1) so it never wraps. */
        const val FLEX0 = 0.3f
        const val FLEX_SCALE = 0.5f
        const val KNEE_RAISE = 0.35
        const val KNEE_T = 0.45
        const val MAX_LEGS = 8
        /** Sally Lightfoot (research 27): swing share of the step cycle (duty factor 0.6), swing foot clearance
         *  (× rest leg length), largest half stroke (model units), feet pulled in when pressed flat (share of the
         *  rest reach), fold-up reaches tried when a leg has nowhere to stand. */
        const val CRAB_SWING = 0.4
        /** Extra sim height over the surface (× length) at which the crab still counts as standing on it. */
        const val CRAB_STAND_TOL = 0.3
        const val CRAB_LIFT = 0.2
        const val CRAB_STRIDE_MAX = 0.3
        const val CRAB_TUCK_PULL = 0.3
        /** Where a foot is set down, as a share of the rest reach from the coxa. */
        const val CRAB_STAND_REACH = 0.9
        private val CRAB_FOLD = doubleArrayOf(0.55, 0.4)
        private val CRAB_REACH_TRY = doubleArrayOf(1.0, 0.8, 0.65, 0.52)
        /** A foothold this close to the surface (cm, past the tip radius) counts as on it. */
        const val FOOT_ON_CM = 0.08
        const val MID_T = 0.75
        /** Legs plant until the crab is this far into its shell (they fold into the aperture beyond). */
        const val LEG_RETRACT_MAX = 0.6
        const val CLAW_DOWN_MIN = -0.6
        const val CLAW_DOWN_MAX = 1.2
        const val CLAW_CLEAR = 0.004
        const val FEELER_CLEAR_CM = 0.04
        /** Eyestalk bend: tip back and down per unit bend (× shield length), `fish.vert` k 7. */
        const val EYE_BACK = 0.5
        const val EYE_DOWN = 0.25
        /** Antenna bend: pitch up and sweep out-back per unit bend (rad), `fish.vert` k 8 / 9. */
        const val FEELER_PITCH = 1.1
        const val FEELER_YAW = 0.6
        const val BEND_RELAX = 0.8
        const val MAX_STEP = 0.45f
        /** A leg with no clear pose pulls in (and back out) over two ticks. */
        const val TUCK_STEP = 0.5
        private val BENDS = doubleArrayOf(0.0, 0.2, 0.4, 0.6, 0.8, 1.0)
        private val SIDES = intArrayOf(-1, 1)
        /** Fallback foothold search: reach (share of the intended hip-to-foot offset) and swing about the hip (rad). */
        private val REACH_TRY = doubleArrayOf(0.85, 0.65, 0.45)
        private val YAW_TRY = doubleArrayOf(0.0, 0.35, -0.35, 0.7, -0.7)

        /** `fish.vert` feeler bend: pitch up about +Z, then out and back about +Y (left +, right −). */
        fun bendFeeler(off: Vec3, side: Double, b: Double): Vec3 {
            if (b <= 0.0) return off
            val p = Vec3.rotateAbout(off, Vec3.Z, b * FEELER_PITCH)
            return Vec3.rotateAbout(p, Vec3.Y, -side * b * FEELER_YAW)
        }

        fun forOrganism(kind: BrainKind, speciesId: String): CrawlerSeat? = when (kind) {
            BrainKind.HERMIT -> {
                val m = if (speciesId.contains("paguristes", true) || speciesId.contains("scarlet", true)) HermitModel.SCARLET else HermitModel.BLUE
                CrawlerSeat(kind, SeatShape(0.3, 0.3, m.contacts + m.hip + m.clawPivot), m)
            }
            // (off for now: in FishGlitchTest the seat added body pops and speed spikes on these two, whose seats
            // are not yet held to a no-clip test; flip [SEAT_OTHER_CRAWLERS] once they pass both)
            BrainKind.SALLY_CRAB -> CrawlerSeat(kind, SeatShape(0.5, 0.6, CrabModel.SALLY.contacts.copyOf()), null, CrabModel.SALLY)
            BrainKind.BRITTLE_STAR -> if (SEAT_OTHER_CRAWLERS) CrawlerSeat(kind, SeatShape(0.22, 0.22, SeatShape.BRITTLE), null) else null
            BrainKind.ASTREA -> if (SEAT_OTHER_CRAWLERS) CrawlerSeat(kind, SeatShape(0.35, 0.35, SeatShape.ASTREA), null) else null
            else -> null
        }
    }
}

/**
 * A crawler model's footprint for [CrawlerSeat] (model units, glTF axes: +X forward, +Y up, +Z right; the models
 * stand on y = 0): half-extents of the four footprint samples and the contact points solved against the surface.
 * Contacts come from the committed GLBs (lowest vertices per footprint cell, limb tips; tools/blender/inverts.py).
 */
class SeatShape(val fore: Double, val side: Double, val contacts: DoubleArray) {
    /** Centre of the contact points (the solve's rotation pivot). */
    val cx = (0 until contacts.size / 3).sumOf { contacts[it * 3] } / (contacts.size / 3)
    val cy = (0 until contacts.size / 3).sumOf { contacts[it * 3 + 1] } / (contacts.size / 3)
    val cz = (0 until contacts.size / 3).sumOf { contacts[it * 3 + 2] } / (contacts.size / 3)

    companion object {
        /** Brittle star: the disc underside and each arm at 30 % of its length. */
        val BRITTLE = doubleArrayOf(
            -0.028, 0.004, 0.010, -0.004, 0.004, -0.025, 0.030, 0.004, 0.0,
            0.189, 0.009, 0.0, 0.059, 0.009, -0.180, -0.153, 0.009, -0.111, -0.153, 0.009, 0.111, 0.059, 0.009, 0.180,
        )
        /** Astrea: the foot sole and the shell's basal lip. */
        val ASTREA = doubleArrayOf(
            -0.207, 0.009, -0.106, 0.050, 0.0, 0.0, 0.050, 0.009, -0.212, 0.050, 0.009, 0.212, 0.347, 0.009, 0.0,
            0.260, 0.030, -0.260, 0.260, 0.030, 0.260, -0.542, 0.050, -0.261, -0.542, 0.050, 0.261,
            -0.134, 0.050, -0.587, -0.134, 0.050, 0.587, 0.602, 0.050, 0.0,
        )
    }
}

/**
 * Walking-leg geometry for the planted-leg IK ([CrawlerSeat]), model units: per leg the coxa pivot, rest dactyl tip,
 * knee (at limb parameter [kneeT]) and propodus (t 0.75), the rest leg length along its path (extras.limbPivots w);
 * per chela its pivot, tip and lowest point (left, right).
 */
abstract class LegModel(
    val hip: DoubleArray, val tip: DoubleArray, val knee: DoubleArray, val mid: DoubleArray, val legW: DoubleArray, val kneeT: Double,
    val clawPivot: DoubleArray, val clawTip: DoubleArray, val clawLow: DoubleArray,
) {
    val legs = hip.size / 3
    /** Hip → rest tip distance per leg. */
    val chord = DoubleArray(legs) { k ->
        val dx = tip[k * 3] - hip[k * 3]; val dy = tip[k * 3 + 1] - hip[k * 3 + 1]; val dz = tip[k * 3 + 2] - hip[k * 3 + 2]
        sqrt(dx * dx + dy * dy + dz * dz)
    }
}

/**
 * Sally Lightfoot geometry read from `sally_lightfoot.glb` (carapace width 1; legs k = 2 pair + (0 left −Z, 1 right
 * +Z) as in [CrabLegs]; knee = the leg's high point at t 0.5, about 0.29 = 0.3 CW up, research 27). [contacts]: the
 * carapace (underside, rim, dome), the coxae and, from [STANCE_FROM] on, eight stance points at 65 % of each leg's
 * rest reach on the ground plane, so the body stands at its rest height over the footholds and drops as it presses flat.
 */
class CrabModel(
    hip: DoubleArray, tip: DoubleArray, knee: DoubleArray, mid: DoubleArray, legW: DoubleArray,
    clawPivot: DoubleArray, clawTip: DoubleArray, clawLow: DoubleArray, body: DoubleArray,
) : LegModel(hip, tip, knee, mid, legW, 0.5, clawPivot, clawTip, clawLow) {
    val contacts: DoubleArray = DoubleArray(body.size + hip.size + hip.size).also { c ->
        body.copyInto(c)
        hip.copyInto(c, body.size)
        for (k in 0 until hip.size / 3) {
            val o = body.size + hip.size + k * 3
            c[o] = hip[k * 3] + (tip[k * 3] - hip[k * 3]) * STANCE_REACH
            c[o + 1] = STANCE_Y
            c[o + 2] = hip[k * 3 + 2] + (tip[k * 3 + 2] - hip[k * 3 + 2]) * STANCE_REACH
        }
    }

    companion object {
        const val STANCE_REACH = 0.32
        const val STANCE_Y = 0.004
        /** Stance contacts rise this far (model units) pressed flat: the carapace underside (y 0.037) to ~1.5 mm off the rock. */
        const val FLAT_DROP = 0.02
        /** Index of the first stance contact (after 18 carapace points and 8 coxae). */
        const val STANCE_FROM = 26

        /** Measured from sally_lightfoot.glb (tools/blender/inverts.py build_sally_lightfoot prints these). */
        val SALLY = CrabModel(
            hip = doubleArrayOf(0.2876, 0.2063, -0.3548, 0.2978, 0.1887, 0.3568, 0.1162, 0.1599, -0.4274, 0.141, 0.1598, 0.4519, -0.2324, 0.1559, -0.3878, -0.1634, 0.106, 0.4424, -0.4135, 0.1292, -0.2555, -0.4002, 0.1182, 0.3025),
            tip = doubleArrayOf(1.2102, 0.0428, -1.3505, 1.3139, 0.0364, 1.1783, 0.8808, 0.0257, -1.7007, 0.1656, 0.0211, 1.6932, -0.9557, 0.022, -1.0676, -0.8381, 0.0204, 1.4641, -1.2458, 0.0804, -0.3973, -1.1916, 0.0229, 0.3329),
            knee = doubleArrayOf(1.0074, 0.3411, -0.6399, 1.0255, 0.3345, 0.5356, 0.7571, 0.3084, -0.946, 0.6674, 0.2977, 1.0894, -0.1486, 0.3358, -1.2261, -0.1216, 0.3239, 1.2512, -0.7692, 0.2728, -0.8055, -0.8214, 0.2631, 0.8361),
            mid = doubleArrayOf(1.1754, 0.2244, -0.9909, 1.2348, 0.2233, 0.8448, 0.914, 0.2183, -1.3341, 0.4927, 0.1972, 1.475, -0.5891, 0.2166, -1.2679, -0.4657, 0.217, 1.4818, -1.1076, 0.2153, -0.6832, -1.1063, 0.1772, 0.6454),
            legW = doubleArrayOf(1.632, 1.572, 1.719, 1.769, 1.887, 1.748, 1.48, 1.468),
            clawPivot = doubleArrayOf(0.3461, 0.2073, -0.2429, 0.3363, 0.2003, 0.2686),
            clawTip = doubleArrayOf(0.6285, 0.0594, -0.196, 0.6795, 0.0508, 0.2196),
            clawLow = doubleArrayOf(0.5794, 0.0018, -0.1306, 0.5896, 0.0, 0.1334),
            body = doubleArrayOf(
                // underside
                -0.4157, 0.0387, 0.0, -0.2935, 0.0366, 0.2724, -0.1956, 0.1968, -0.3566, -0.1712, 0.0477, -0.1486, -0.1076, 0.0509, 0.1981,
                0.0, 0.0798, 0.0, 0.1076, 0.0956, -0.1981, 0.1956, 0.2119, 0.3566, 0.2054, 0.1375, 0.0, 0.2935, 0.2344, -0.2724, 0.3913, 0.2251, 0.0,
                // rim and top
                0.0065, 0.0981, -0.4991, 0.0269, 0.0796, 0.4914, 0.4234, 0.335, -0.2259, 0.4033, 0.3299, 0.2856, -0.488, 0.0949, -0.2301,
                -0.4728, 0.0917, 0.2991, 0.4024, 0.3724, 0.0203,
            ),
        )
    }
}

/**
 * Hermit crab model geometry for the seat and the leg IK, read from `hermit_blue.glb` / `hermit_scarlet.glb`
 * (model units; legs k = 2 pair + (0 left, 1 right) as in [HermitLegs]; left / right pairs are (−Z, +Z)).
 */
class HermitModel(
    /** Coxa pivot, rest dactyl tip, knee (t 0.45) and propodus (t 0.75) per leg. */
    hip: DoubleArray, tip: DoubleArray, knee: DoubleArray, mid: DoubleArray,
    /** Rest leg length along its path (extras.limbPivots w). */
    legW: DoubleArray,
    clawPivot: DoubleArray, clawTip: DoubleArray, clawLow: DoubleArray,
    /** Antenna base xyz + length, tips and midpoints (left, right). */
    val antBase: DoubleArray, val antTip: DoubleArray, val antMid: DoubleArray,
    val antlBase: DoubleArray, val antlTip: DoubleArray,
    /** Eyestalk corneas (left, right) and the shield length (eyestalk bend scale). */
    val eyeTip: DoubleArray, val eyeW: Double,
    /** Shell and shield points the seat rests on. */
    val contacts: DoubleArray,
) : LegModel(hip, tip, knee, mid, legW, CrawlerSeat.KNEE_T, clawPivot, clawTip, clawLow) {

    companion object {
        val BLUE = HermitModel(
            hip = doubleArrayOf(0.2223, 0.0794, -0.0694, 0.2223, 0.0794, 0.0694, 0.1760, 0.0794, -0.0694, 0.1760, 0.0794, 0.0694),
            tip = doubleArrayOf(0.5388, 0.0042, -0.4270, 0.5388, 0.0042, 0.4270, 0.2584, 0.0040, -0.5003, 0.2584, 0.0040, 0.5003),
            knee = doubleArrayOf(0.3607, 0.1564, -0.2467, 0.3607, 0.1564, 0.2467, 0.1980, 0.1569, -0.2788, 0.1980, 0.1569, 0.2788),
            mid = doubleArrayOf(0.4484, 0.0775, -0.3565, 0.4484, 0.0775, 0.3565, 0.2128, 0.0822, -0.4095, 0.2128, 0.0822, 0.4095),
            legW = doubleArrayOf(0.4627, 0.4627, 0.4417, 0.4417),
            clawPivot = doubleArrayOf(0.2644, 0.0752, -0.0631, 0.2644, 0.0752, 0.0631),
            clawTip = doubleArrayOf(0.5000, 0.0016, -0.0516, 0.4928, 0.0074, 0.0521),
            clawLow = doubleArrayOf(0.5000, 0.0016, -0.0516, 0.4876, 0.0067, 0.0533),
            antBase = doubleArrayOf(0.2864, 0.1036, 0.0, 0.5258),
            antTip = doubleArrayOf(0.7285, 0.1471, -0.3843, 0.7285, 0.1471, 0.3843),
            antMid = doubleArrayOf(0.4908, 0.1902, -0.1798, 0.4908, 0.1902, 0.1798),
            antlBase = doubleArrayOf(0.2970, 0.1162, 0.0, 0.1262),
            antlTip = doubleArrayOf(0.4042, 0.1935, -0.0445, 0.4042, 0.1935, 0.0445),
            eyeTip = doubleArrayOf(0.4242, 0.3011, -0.0733, 0.4242, 0.3011, 0.0733), eyeW = 0.2103,
            contacts = doubleArrayOf(
                -0.5000, 0.3625, 0.0793, -0.4365, 0.4124, 0.0808, -0.3981, 0.3506, 0.0247, -0.3937, 0.3352, 0.1537,
                -0.3894, 0.2659, 0.1126, -0.3003, 0.2926, -0.0023, -0.2792, 0.2976, 0.1803, -0.2790, 0.1709, 0.0729,
                -0.2667, 0.3703, 0.0626, -0.1915, 0.0991, 0.0740, -0.1793, 0.2069, -0.0348, -0.1696, 0.2471, 0.2022,
                -0.1580, 0.3521, 0.0948, -0.0671, 0.0126, 0.0744, -0.0600, 0.1392, -0.0646, -0.0524, 0.1351, 0.2347,
                -0.0264, 0.0186, 0.0665, -0.0244, 0.3265, 0.0959, 0.0388, 0.1899, 0.2487, 0.0528, 0.0919, -0.0956,
                0.0694, 0.1602, 0.2392, 0.0752, 0.0665, 0.0, 0.0872, 0.3037, 0.0800, 0.1027, 0.0825, -0.0923,
                0.1729, 0.1887, 0.0379,
                // shield underside and front
                0.0666, 0.0239, 0.0, 0.2997, 0.1111, 0.0,
            ),
        )
        val SCARLET = HermitModel(
            hip = doubleArrayOf(0.1769, 0.0895, -0.0790, 0.1769, 0.0895, 0.0790, 0.1242, 0.0895, -0.0790, 0.1242, 0.0895, 0.0790),
            tip = doubleArrayOf(0.5352, 0.0045, -0.4863, 0.5511, 0.0045, 0.5066, 0.2160, 0.0044, -0.5697, 0.2185, 0.0044, 0.5942),
            knee = doubleArrayOf(0.3341, 0.1731, -0.2804, 0.3411, 0.1733, 0.2894, 0.1492, 0.1736, -0.3171, 0.1503, 0.1737, 0.3277),
            mid = doubleArrayOf(0.4341, 0.0874, -0.4059, 0.4459, 0.0883, 0.4212, 0.1660, 0.0924, -0.4664, 0.1678, 0.0932, 0.4846),
            legW = doubleArrayOf(0.5269, 0.5532, 0.5029, 0.5281),
            clawPivot = doubleArrayOf(0.2248, 0.0847, -0.0718, 0.2248, 0.0847, 0.0718),
            clawTip = doubleArrayOf(0.5000, 0.0, -0.0585, 0.4726, 0.0111, 0.0599),
            clawLow = doubleArrayOf(0.5000, 0.0, -0.0585, 0.4668, 0.0104, 0.0612),
            antBase = doubleArrayOf(0.2502, 0.1180, 0.0, 0.5987),
            antTip = doubleArrayOf(0.7536, 0.1675, -0.4350, 0.7536, 0.1675, 0.4350),
            antMid = doubleArrayOf(0.4830, 0.2164, -0.2021, 0.4830, 0.2164, 0.2021),
            antlBase = doubleArrayOf(0.2616, 0.1317, 0.0, 0.1369),
            antlTip = doubleArrayOf(0.3779, 0.2155, -0.0482, 0.3779, 0.2155, 0.0482),
            eyeTip = doubleArrayOf(0.4056, 0.3407, -0.0822, 0.4056, 0.3407, 0.0822), eyeW = 0.2395,
            contacts = doubleArrayOf(
                -0.5000, 0.3515, 0.1262, -0.4443, 0.4030, 0.1324, -0.4011, 0.2305, 0.1325, -0.3952, 0.3251, 0.0510,
                -0.3940, 0.2598, 0.2030, -0.3759, 0.4016, 0.1315, -0.2955, 0.2744, 0.0102, -0.2925, 0.2461, 0.2746,
                -0.2855, 0.1443, 0.1315, -0.1839, 0.3976, 0.1624, -0.1832, 0.0137, 0.1562, -0.1811, 0.1981, -0.0539,
                -0.1781, 0.2186, 0.3039, -0.1653, 0.4001, 0.1020, -0.1329, 0.0202, 0.1392, -0.1103, 0.1441, 0.3454,
                -0.0622, 0.1128, -0.1162, -0.0546, 0.2107, 0.3497, -0.0125, 0.3928, 0.1535, 0.0042, 0.1029, -0.1346,
                0.0155, 0.0695, 0.0, 0.0556, 0.1785, 0.2729, 0.0710, 0.0929, -0.1171, 0.0718, 0.3758, 0.0824,
                0.1495, 0.0812, -0.0008, 0.1567, 0.1975, 0.0181,
                -0.0061, 0.0192, 0.0, 0.2650, 0.1256, 0.0,
            ),
        )
    }
}
