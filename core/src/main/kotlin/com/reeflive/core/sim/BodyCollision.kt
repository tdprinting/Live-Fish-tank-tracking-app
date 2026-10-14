package com.reeflive.core.sim

import com.reeflive.core.geom.BodySpline
import com.reeflive.core.geom.SpatialHash
import com.reeflive.core.geom.Vec3
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * An animal's collision body for the current tick: [N] stations along the spine, each an elliptic cross-section
 * (SIMULATION §4.3, [BodyShape]) placed on the bent spine with the body's up and side axes. Rebuilt by [refresh].
 */
class BodyState {
    val p = Array(N) { Vec3.ZERO }
    val up = Array(N) { Vec3.Y }
    val side = Array(N) { Vec3.Z }
    /** Station spine coordinate, centre offset along up, semi-axes along up and side (cm). */
    val u = DoubleArray(N)
    val ctr = DoubleArray(N)
    val a = DoubleArray(N)
    val b = DoubleArray(N)
    /** Heading and up of the spline's remembered clear pose ([BodySpline.saveSafe]). */
    var safeHeading = Vec3.X
    var safeUp = Vec3.Y
    /** Consecutive resolves that held the body at its clear pose. */
    var held = 0
    var centre = Vec3.ZERO; private set
    var radius = 0.0; private set
    private val spine = Array(BodySpline.POINTS) { Vec3.ZERO }

    /**
     * Distance from station [i]'s spine point to the body's farthest point in direction [v] (unit): the support
     * function of the elliptic section `c·(U·v) + √(a²(U·v)² + b²(S·v)²)`.
     */
    fun support(i: Int, v: Vec3): Double {
        val vu = up[i] dot v; val vs = side[i] dot v
        return ctr[i] * vu + sqrt(a[i] * a[i] * vu * vu + b[i] * b[i] * vs * vs)
    }

    /** The point of station [i]'s section farthest in direction [v] (unit), where [support] is reached. */
    fun extreme(i: Int, v: Vec3): Vec3 {
        val vu = up[i] dot v; val vs = side[i] dot v
        val r = sqrt(a[i] * a[i] * vu * vu + b[i] * b[i] * vs * vs)
        val c = p[i] + up[i] * ctr[i]
        return if (r < 1e-9) c else c + up[i] * (a[i] * a[i] * vu / r) + side[i] * (b[i] * b[i] * vs / r)
    }

    /** [support] at a fractional station index (linear between the two stations). */
    fun supportAt(f: Double, v: Vec3): Double {
        val k = min(f.toInt().coerceAtLeast(0), N - 2)
        val t = (f - k).coerceIn(0.0, 1.0)
        return support(k, v) * (1 - t) + support(k + 1, v) * t
    }

    /** Spine point at coordinate [x] (0 head … 1 tail) on the sampled spine; outside [0, 1] along the end tangents. */
    private fun spineAt(x: Double): Vec3 {
        val f = x * (BodySpline.POINTS - 1)
        if (f <= 0) return spine[0] + (spine[0] - spine[1]).normalized(Vec3.X) * (-f * spine[0].distanceTo(spine[1]))
        val k = min(f.toInt(), BodySpline.POINTS - 2)
        val t = f - k
        return spine[k] + (spine[k + 1] - spine[k]) * t
    }

    private var shapeOf: SimSpecies? = null
    private var shape: BodyShape? = null
    /** The shape the last [refresh] used. */
    val currentShape: BodyShape? get() = shape

    fun refresh(o: Organism) {
        if (o.species !== shapeOf || shape == null) { shape = BodyShapes.of(o.species); shapeOf = o.species }
        val s = shape!!
        val st = s.stations(N)
        val L = o.lengthCm
        val rigid = o.spline.rigid && o.spline.override == null
        if (!rigid) o.spline.samplePoints(spine)
        val h = o.heading.normalized(Vec3.X)
        // the renderer's lateral swim wave and C-start bend (fish.vert) widen the swept body; the shrimp tail-flip curls the tail down
        val amp = min(o.amp, MAX_WAVE_AMP)
        val mode = o.toPoseMotionMode()
        for (i in 0 until N) {
            val uu = s.uMin + (s.uMax - s.uMin) * i / (N - 1)
            u[i] = uu
            p[i] = if (rigid) o.pos - h * (uu * L) else spineAt(uu)
        }
        var cx = 0.0; var cy = 0.0; var cz = 0.0
        for (i in 0 until N) {
            val t = if (rigid) h else (if (i == 0) p[0] - p[1] else if (i == N - 1) p[N - 2] - p[N - 1] else p[i - 1] - p[i + 1]).normalized(h)
            val upv = o.up.rejectFrom(t).normalized(Vec3.Y.rejectFrom(t).normalized(Vec3.Z))
            up[i] = upv
            side[i] = (t cross upv).normalized(Vec3.Z)
            val uu = u[i].coerceIn(0.0, 1.0)
            val top = st[0][i] * L
            var bot = st[1][i] * L
            var w = st[2][i] * L
            if (mode == 0 && o.isFish) {
                w += L * (amp * (0.1 + 0.9 * uu * uu) + abs(o.bend) * 0.55 * abs((uu - 0.35) * (uu - 0.35) - 0.04))
            } else if (mode == 2) {
                val ab = max(uu - 0.4, 0.0)
                bot -= L * 0.9 * max(o.bend, 0.0) * ab * ab
                w += L * amp * (0.1 + 0.9 * uu * uu)
            }
            ctr[i] = (top + bot) / 2
            a[i] = max(0.0, (top - bot) / 2)
            b[i] = w
            cx += p[i].x; cy += p[i].y; cz += p[i].z
        }
        centre = Vec3(cx / N, cy / N, cz / N)
        var r = 0.0
        for (i in 0 until N) r = max(r, centre.distanceTo(p[i]) + abs(ctr[i]) + max(a[i], b[i]))
        radius = r
    }

    companion object {
        const val N = 16
        /** fish.vert MAX_WAVE_AMP: the drawn tail wave is capped at 0.12 BL. */
        const val MAX_WAVE_AMP = 0.12
    }
}

/** The renderer's motion mode for this animal (OrganismPose.motionMode): 0 fish, 1 brittle star, 2 shrimp, 3 crab. */
internal fun Organism.toPoseMotionMode(): Int = when (motion.gait) {
    Gait.ARM_ROW -> 1
    Gait.WALK -> if (brainKind == BrainKind.BANDED_SHRIMP || brainKind == BrainKind.CLEANER_SHRIMP) 2 else if (brainKind == BrainKind.SALLY_CRAB) 3 else 0
    else -> 0
}

/**
 * Keeps every animal's body out of the rock, the sand (the dynamic [SandBed] surface), the glass and the water
 * surface, and out of the other animals (SIMULATION §4.3 "hard collision resolve"). Called after steering: each
 * penetrating station is pushed out along the surface normal (planar contact against the elliptic section), the push
 * bends the body spline locally, and the velocity component into the surface is removed so the animal slides.
 * Concave rock is checked on twelve rim points around each station and halfway between stations. A body the pushes
 * cannot clear (a crevice narrower than it) is held at its last clear pose; a fresh placement with none backs out
 * along its body or lifts clear.
 *
 * Deliberate exceptions are explicit states, never accidental overlap: in a hole, den or burrow (posture 3, the
 * gobies, blennies and the eel, the brittle star in its crevice), buried in the sand (posture 5: nassarius, a sand-diving
 * wrasse), a den / hole spline path, the sessile tube worm, and a cleaner touching its client.
 */
class BodyCollision(private val rock: RockData) {
    private val deltas = Array(BodySpline.POINTS) { Vec3.ZERO }
    private val rockGap = DoubleArray(BodyState.N)
    /** Whether the last [resolveEnv] left the body clear (within the wedge limit). */
    private var lastClear = true
    private val grid = rock.sdf as? com.reeflive.core.geom.SdfGrid
    private fun grad(p: Vec3): Vec3 = grid?.gradientFast(p) ?: rock.sdf.gradient(p)
    private val hash = SpatialHash<Organism>(15.0) { it.body.centre }
    private val pairDelta = LinkedHashMap<Organism, Array<Vec3>>()
    private val order = HashMap<Organism, Int>()

    // ---- counters (tests / profiling) ----
    /** Off only for cost measurements (the bodies then pass through everything as before). */
    var enabled = true
    var envPushes = 0L; private set
    var envCalls = 0L; private set
    var wedgeRestores = 0L; private set
    /** Wedge holds per species id (tests). */
    val wedgeBy = java.util.TreeMap<String, Long>()
    var pairPushes = 0L; private set

    /** True when this animal's body is deliberately allowed into the geometry (see class doc). */
    fun exempt(o: Organism): Boolean =
        o.brainKind.isSessile || o.posture == 3 || o.posture == 5 || o.spline.override != null || !o.visible

    /** Floor (sand surface) height and its outward normal at (x, z). */
    fun floorNormal(x: Double, z: Double): Vec3 {
        val e = 0.5
        val dx = rock.floorY(x + e, z) - rock.floorY(x - e, z)
        val dz = rock.floorY(x, z + e) - rock.floorY(x, z - e)
        return Vec3(-dx / (2 * e), 1.0, -dz / (2 * e)).normalized(Vec3.Y)
    }

    /**
     * Pushes [o]'s body out of the environment. [rockOn] = false skips the rock (a behaviour that passes through it
     * on purpose); [surfaceOn] = false lets the body break the water surface (a floating carcass). [settle] (a resting
     * or slow fish) lays the body along the contact under its belly: pitch and roll follow the surface.
     * Returns the deepest penetration found before the resolve (cm).
     */
    fun resolveEnv(o: Organism, dt: Double, rockOn: Boolean = true, surfaceOn: Boolean = true, settle: Boolean = false, wedgeFallback: Boolean = true): Double {
        if (!enabled) return 0.0
        if (exempt(o)) { o.spline.dropSafe(); return 0.0 }
        envCalls++
        var worst = 0.0
        var clear = false
        var lastPush = 0.0
        var bellyN = Vec3.ZERO
        var bellyCount = 0
        val rigid = o.spline.rigid
        // how far each station was from the rock on the first pass, and how far the body has moved since: a station
        // that started farther from the rock than the body has moved cannot have reached it (distance is 1-Lipschitz)
        var moved = 0.0
        for (iter in 0 until MAX_ITERATIONS) {
            val body = o.body
            body.refresh(o)
            if (iter == 0) {
                val dc = if (rockOn) rock.sdf.dist(body.centre) - body.radius else Double.POSITIVE_INFINITY
                java.util.Arrays.fill(rockGap, dc)
            }
            var any = false
            var best = 0.0
            // the translation that serves every station's push at once: per axis the largest push each way
            var hiX = 0.0; var hiY = 0.0; var hiZ = 0.0; var loX = 0.0; var loY = 0.0; var loZ = 0.0
            for (i in 0 until BodyState.N) {
                val p0 = body.p[i]
                var q = p0
                val bound = abs(body.ctr[i]) + max(body.a[i], body.b[i]) + SKIN
                fun contact(n: Vec3, d: Double) {
                    val pen = body.support(i, -n) - d + SKIN
                    if (pen <= 0) return
                    if (iter == 0) worst = max(worst, pen - SKIN)
                    q += n * pen
                    if (n dot body.up[i] > 0.5) { bellyN += n; bellyCount++ }
                    val vn = o.vel dot n
                    if (vn < 0 && (i == 0 || rigid)) o.vel -= n * vn
                }
                if (rockOn && rockGap[i] < moved + SKIN) {
                    val d = rock.sdf.dist(q)
                    if (iter == 0) rockGap[i] = d - bound
                    if (d < bound) {
                        // planar contact at the centre, refined at the section's deepest point (curved and concave rock)
                        val n = grad(q)
                        contact(n, d)
                        val e = body.extreme(i, -n) + (q - p0)
                        val de = rock.sdf.dist(e)
                        if (de < -SKIN * 0.5) { val ne = grad(e); q += ne * (-de + SKIN); noteContact(o, ne, i, rigid, body) }
                        // concave rock (a ridge, a crevice lip) can reach a side of the section the centre's normal does
                        // not face: the deepest of twelve rim points is pushed out along its own normal
                        if (d < bound - SKIN) {
                            val off = q - p0
                            val c = p0 + body.up[i] * body.ctr[i] + off
                            var dMin = 0.0; var at = c
                            for (k in 0 until RIM) {
                                val r = c + body.up[i] * (body.a[i] * RIM_COS[k]) + body.side[i] * (body.b[i] * RIM_SIN[k])
                                val dr = rock.sdf.dist(r)
                                if (dr < dMin) { dMin = dr; at = r }
                            }
                            // and halfway to the next station (a rock edge between two stations)
                            if (i < BodyState.N - 1) {
                                val j = i + 1
                                val hu = (body.up[i] + body.up[j]).normalized(body.up[i])
                                val hs = (body.side[i] + body.side[j]).normalized(body.side[i])
                                val ha = 0.5 * (body.a[i] + body.a[j]); val hb = 0.5 * (body.b[i] + body.b[j])
                                val hc = (body.p[i] + body.p[j]) * 0.5 + hu * (0.5 * (body.ctr[i] + body.ctr[j])) + off
                                for (k in 0 until RIM) {
                                    val r = hc + hu * (ha * RIM_COS[k]) + hs * (hb * RIM_SIN[k])
                                    val dr = rock.sdf.dist(r)
                                    if (dr < dMin) { dMin = dr; at = r }
                                }
                            }
                            if (dMin < -SKIN * 0.5) { val nr = grad(at); q += nr * (-dMin + SKIN); noteContact(o, nr, i, rigid, body) }
                        }
                    }
                }
                // the sand is a height field, not a distance field: a steep mound beside the station can reach the body
                // even when the sand under the centre is far below, so the lowest point of the section is checked too
                val fy = rock.floorY(q.x, q.z)
                val low = body.extreme(i, DOWN) + (q - p0)
                if (q.y - fy < bound || low.y - rock.floorY(low.x, low.z) < SKIN) {
                    val n = floorNormal(q.x, q.z)
                    contact(n, (q.y - rock.floorY(q.x, q.z)) * n.y)
                    // the sand curves (mounds against the rock feet, rubble, pits): check the height under the section's
                    // deepest points (along the slope normal, straight down, and twelve around its rim) and lift what is under
                    val off = q - p0
                    var deficit = Double.NEGATIVE_INFINITY
                    fun under(e: Vec3) { deficit = max(deficit, rock.floorY(e.x, e.z) - e.y) }
                    under(body.extreme(i, -n) + off)
                    under(body.extreme(i, DOWN) + off)
                    val cc = q + body.up[i] * body.ctr[i]
                    for (k in 0 until RIM) under(cc + body.up[i] * (body.a[i] * RIM_COS[k]) + body.side[i] * (body.b[i] * RIM_SIN[k]))
                    if (deficit > -SKIN * 0.5) { q += Vec3.Y * (deficit + SKIN); noteContact(o, n, i, rigid, body) }
                }
                if (q.x < bound) contact(Vec3.X, q.x)
                if (rock.lengthCm - q.x < bound) contact(Vec3(-1.0, 0.0, 0.0), rock.lengthCm - q.x)
                if (q.z < bound) contact(Vec3.Z, q.z)
                if (rock.widthCm - q.z < bound) contact(Vec3(0.0, 0.0, -1.0), rock.widthCm - q.z)
                if (surfaceOn && rock.waterLevelCm - q.y < bound) contact(Vec3(0.0, -1.0, 0.0), rock.waterLevelCm - q.y)
                val dq = q - p0
                if (dq.lengthSq() > 1e-12) {
                    any = true
                    if (dq.lengthSq() > best) best = dq.lengthSq()
                    hiX = max(hiX, dq.x); hiY = max(hiY, dq.y); hiZ = max(hiZ, dq.z)
                    loX = min(loX, dq.x); loY = min(loY, dq.y); loZ = min(loZ, dq.z)
                }
                deltas[i.coerceAtMost(deltas.size - 1)] = dq
            }
            if (!any) { clear = true; break }
            lastPush = sqrt(best)
            // beyond the usual iterations only a real overlap (a fresh placement, a wedge) keeps the loop going
            if (iter >= ITERATIONS && best < 0.02 * 0.02) { clear = true; break }
            // still pushing hard after the usual iterations with a clear pose to fall back on: a wedge, stop here
            if (iter >= ITERATIONS && wedgeFallback && lastPush > WEDGE_LIMIT && o.spline.hasSafe) break
            envPushes++
            moved += 2 * sqrt(best)
            // bend the body first; what bending cannot clear (a tail in the wedge where rock meets sand, a spline
            // relaxing back into the rock) moves the whole body, head included
            val translate = Vec3(hiX + loX, hiY + loY, hiZ + loZ)
            // a rigid body (a shell, a carapace) on curved rock also tips: the small rotation that best serves the
            // station pushes (shape matching about the body centre), then the translation
            if (rigid && o.spline.override == null) tip(o, body)
            applyDeltas(o, body, if (rigid || iter >= BEND_ITERATIONS) null else deltas, translate)
        }
        lastClear = clear || lastPush <= WEDGE_LIMIT
        if (clear) {
            o.spline.saveSafe(); o.body.safeHeading = o.heading; o.body.safeUp = o.up
            if (wedgeFallback) o.body.held = 0   // (not the re-settle of a held pose: that is still the same hold)
        } else if (wedgeFallback && lastPush > WEDGE_LIMIT && o.body.held >= HOLD_MAX) {
            // held this long, the behaviour keeps pressing into the same wedge: find clear water instead of freezing
            o.body.held = 0
            o.spline.dropSafe()
            unstick(o, rockOn, surfaceOn)
        } else if (wedgeFallback && lastPush > WEDGE_LIMIT && o.spline.restoreSafe()) {
            o.body.held++
            // a wedge the pushes cannot clear (a crevice narrower than the body, rock against sand): the body stays
            // where it last fitted instead of passing into the geometry
            o.pos = o.spline.headPos; o.heading = o.body.safeHeading; o.up = o.body.safeUp
            o.vel = Vec3.ZERO
            wedgeRestores++
            wedgeBy.merge(o.species.id, 1L) { x, y -> x + y }
            // the swim wave and bend have moved on since that pose was saved: settle it against them once more
            resolveEnv(o, dt, rockOn, surfaceOn, false, wedgeFallback = false)
            // still caught (the pose fitted a body that has since bent or grown): back out to clear water
            if (!lastClear) unstick(o, rockOn, surfaceOn)
            return worst
        } else if (wedgeFallback && lastPush > WEDGE_LIMIT) {
            // wedged with no clear pose to return to (a fresh placement): back out along the body or lift clear
            unstick(o, rockOn, surfaceOn)
        }
        if (settle && o.isFish && bellyCount >= 2) {
            val n = bellyN.normalized(Vec3.Y)
            val k = min(1.0, dt * SETTLE_RATE)
            val flat = o.heading.rejectFrom(n).normalized(o.heading)
            o.heading = o.heading.lerp(flat, k).normalized(o.heading)
            o.up = o.up.lerp(n.rejectFrom(o.heading).normalized(o.up), k).normalized(Vec3.Y)
        }
        return worst
    }

    /** Worst depth of the body's rims (twelve points around every station) in the rock, sand, glass or surface. */
    private fun rimDepth(o: Organism, rockOn: Boolean, surfaceOn: Boolean): Double {
        val body = o.body
        body.refresh(o)
        var worst = Double.NEGATIVE_INFINITY
        for (i in 0 until BodyState.N) {
            val c = body.p[i] + body.up[i] * body.ctr[i]
            for (k in 0 until RIM) {
                val r = c + body.up[i] * (body.a[i] * RIM_COS[k]) + body.side[i] * (body.b[i] * RIM_SIN[k])
                if (rockOn) worst = max(worst, -rock.sdf.dist(r))
                worst = max(worst, rock.floorY(r.x, r.z) - r.y)
                worst = max(worst, max(-r.x, r.x - rock.lengthCm))
                worst = max(worst, max(-r.z, r.z - rock.widthCm))
                if (surfaceOn) worst = max(worst, r.y - rock.waterLevelCm)
            }
        }
        return worst
    }

    private fun unstick(o: Organism, rockOn: Boolean, surfaceOn: Boolean) {
        val L = o.lengthCm
        val dirs = listOf(o.heading, o.heading * -1.0, Vec3.Y, o.up)
        var moved = Vec3.ZERO
        // the axis at head, middle and tail before the hop: a hop must not carry the body through a thin rock wall
        val body = o.body
        body.refresh(o)
        val axis = IntArray(3) { it * (BodyState.N - 1) / 2 }.map { i -> body.p[i] + body.up[i] * body.ctr[i] }
        val axis0 = axis.map { if (rockOn) rock.sdf.dist(it) else 0.0 }
        for (k in 1..UNSTICK_STEPS) {
            val dist = min(L * 0.15, 0.75) * k
            for (d in dirs) {
                val t = d * dist
                if (rockOn && crossesWall(axis, axis0, t)) continue
                for (j in 0 until BodySpline.POINTS) deltas[j] = t - moved
                o.spline.displace(deltas)
                o.pos = o.spline.headPos
                moved = t
                if (rimDepth(o, rockOn, surfaceOn) < 0.0) {
                    o.vel = Vec3.ZERO
                    o.spline.saveSafe(); o.body.safeHeading = o.heading; o.body.safeUp = o.up
                    return
                }
            }
        }
        // nowhere clear nearby: stay where the pushes left the body
        for (j in 0 until BodySpline.POINTS) deltas[j] = moved * -1.0
        o.spline.displace(deltas)
        o.pos = o.spline.headPos
    }

    /** Whether moving the [axis] points by [t] passes deeper into rock than they start (through a wall). */
    private fun crossesWall(axis: List<Vec3>, d0: List<Double>, t: Vec3): Boolean {
        val n = max(1, ceil(t.length() / 0.3).toInt())
        for (j in axis.indices) {
            val floor = min(d0[j], 0.0) - 0.3
            for (s in 1..n) if (rock.sdf.dist(axis[j] + t * (s.toDouble() / n)) < floor) return true
        }
        return false
    }

    private fun tip(o: Organism, body: BodyState) {
        val c = body.centre
        var wx = 0.0; var wy = 0.0; var wz = 0.0; var rr = 0.0
        for (i in 0 until BodyState.N) {
            val r = body.p[i] - c
            val d = deltas[i]
            val x = r cross d
            wx += x.x; wy += x.y; wz += x.z
            rr += r.lengthSq()
        }
        if (rr < 1e-9) return
        var w = Vec3(wx, wy, wz) / rr
        val ang = w.length()
        if (ang < 1e-4) return
        if (ang > MAX_TIP) w = w * (MAX_TIP / ang)
        val axis = w.normalized(Vec3.Y)
        val a = min(ang, MAX_TIP)
        val h0 = o.heading
        o.heading = Vec3.rotateAbout(o.heading, axis, a).normalized(h0)
        o.up = Vec3.rotateAbout(o.up, axis, a).normalized(Vec3.Y)
        // rotate about the centre, not the head: move the head by the arc it swings through
        val head = o.pos
        val moved = c + Vec3.rotateAbout(head - c, axis, a)
        for (j in 0 until BodySpline.POINTS) deltas[j] = moved - head
        o.spline.displace(deltas)
        o.pos = o.spline.headPos
        o.spline.update(o.pos, o.heading, 0.0, 0.0)   // a rigid spline only takes the new head and heading
    }

    private fun noteContact(o: Organism, n: Vec3, i: Int, rigid: Boolean, body: BodyState) {
        val vn = o.vel dot n
        if (vn < 0 && (i == 0 || rigid)) o.vel -= n * vn
    }

    /** Moves the body: per-station displacements on a bending spline, the largest one as a translation on a rigid body. */
    private fun applyDeltas(o: Organism, body: BodyState, perStation: Array<Vec3>?, translate: Vec3) {
        if (perStation == null) {
            for (i in 0 until BodySpline.POINTS) deltas[i] = translate
            o.spline.displace(deltas)
        } else {
            // body stations sit at u = uMin + (uMax − uMin)·i/(N−1); the spline wants them at its own stations u = i/15
            val s = body.currentShape ?: BodyShapes.of(o.species)
            if (abs(s.uMin) < 1e-9 && abs(s.uMax - 1.0) < 1e-9 && BodyState.N == BodySpline.POINTS) o.spline.displace(perStation)
            else {
                val tmp = Array(BodySpline.POINTS) { k ->
                    val uk = k.toDouble() / (BodySpline.POINTS - 1)
                    val f = ((uk - s.uMin) / (s.uMax - s.uMin) * (BodyState.N - 1)).coerceIn(0.0, BodyState.N - 1.0)
                    val j = min(f.toInt(), BodyState.N - 2); val t = f - j
                    perStation[j] * (1 - t) + perStation[j + 1] * t
                }
                o.spline.displace(tmp)
            }
        }
        o.pos = o.spline.headPos
    }

    /**
     * Animal against animal: elliptic-section capsule chains, found through a spatial hash of body centres (the tank
     * holds 20–40 animals). Overlaps are split by mass (an attached animal, perched or walking, does not yield to a
     * swimmer), then every moved body is pushed out of the environment again (the rock always wins).
     */
    fun resolvePairs(animals: List<Organism>, stations: List<com.reeflive.core.ai.CleaningStation>, dt: Double) {
        if (!enabled) return
        for (pass in 0 until PAIR_PASSES) if (!pairPass(animals, stations, dt, holdAttached = pass < PAIR_PASSES / 2)) break
    }

    private fun pairPass(animals: List<Organism>, stations: List<com.reeflive.core.ai.CleaningStation>, dt: Double, holdAttached: Boolean): Boolean {
        val live = ArrayList<Organism>(animals.size)
        for (o in animals) if (o.alive && !exempt(o)) {
            o.body.refresh(o)
            // a body that is not finite or absurdly large (a broken trail) is skipped rather than hashed
            if (o.body.centre.isFinite() && o.body.radius < 4 * o.lengthCm + 20) live += o
        }
        if (live.size < 2) return false
        hash.rebuild(live)
        var maxR = 0.0
        for (o in live) maxR = max(maxR, o.body.radius)
        pairDelta.clear()
        order.clear()
        for ((k, o) in live.withIndex()) order[o] = k
        val near = ArrayList<Organism>()
        for (a in live) {
            near.clear()
            hash.neighbors(a.body.centre, a.body.radius + maxR, near)
            val ka = order[a] ?: continue
            for (b in near) {
                val kb = order[b] ?: continue
                if (kb <= ka) continue   // each unordered pair once
                if (a.body.centre.distanceTo(b.body.centre) > a.body.radius + b.body.radius) continue
                if (touchAllowed(a, b, stations)) continue
                pair(a, b, holdAttached)
            }
        }
        if (pairDelta.isEmpty()) return false
        for ((o, d) in pairDelta) {
            pairPushes++
            o.spline.displace(d)
            o.pos = o.spline.headPos
            resolveEnv(o, dt)
        }
        return true
    }

    /** Cleaner and client touch on purpose (the wrasse's mouth and the shrimp's legs on the client's skin). */
    private fun touchAllowed(a: Organism, b: Organism, stations: List<com.reeflive.core.ai.CleaningStation>): Boolean {
        fun cleaner(x: Organism) = x.brainKind == BrainKind.WRASSE || x.brainKind == BrainKind.CLEANER_SHRIMP || x.brainKind == BrainKind.BANDED_SHRIMP
        if (cleaner(a) && a.focusId == b.id || cleaner(b) && b.focusId == a.id) return true
        // a predator's strike: the open jaws reach the prey it is lunging at
        if (a.mouth > 0.5 && a.focusId == b.id || b.mouth > 0.5 && b.focusId == a.id) return true
        if (a.poseFor === b || b.poseFor === a) return true
        for (s in stations) if (s.client != null && (s.cleaner === a && s.client === b || s.cleaner === b && s.client === a)) return true
        return false
    }

    private fun pair(a: Organism, b: Organism, holdAttached: Boolean) {
        val A = a.body; val B = b.body
        val (wa, wb) = weights(a, b, holdAttached)
        for (i in 0 until BodyState.N - 1) {
            val ma = (A.p[i] + A.p[i + 1]) * 0.5
            val ha = A.p[i].distanceTo(A.p[i + 1]) * 0.5 + max(abs(A.ctr[i]) + max(A.a[i], A.b[i]), abs(A.ctr[i + 1]) + max(A.a[i + 1], A.b[i + 1]))
            if (ma.distanceTo(B.centre) > ha + B.radius) continue
            for (j in 0 until BodyState.N - 1) {
                val mb = (B.p[j] + B.p[j + 1]) * 0.5
                val hb = B.p[j].distanceTo(B.p[j + 1]) * 0.5 + max(abs(B.ctr[j]) + max(B.a[j], B.b[j]), abs(B.ctr[j + 1]) + max(B.a[j + 1], B.b[j + 1]))
                if (ma.distanceTo(mb) > ha + hb + SKIN) continue
                val (s, t) = closestParams(A.p[i], A.p[i + 1], B.p[j], B.p[j + 1])
                val pa = A.p[i] + (A.p[i + 1] - A.p[i]) * s
                val pb = B.p[j] + (B.p[j + 1] - B.p[j]) * t
                val d = pa.distanceTo(pb)
                val n = if (d > 1e-6) (pa - pb) / d else (A.centre - B.centre).normalized(Vec3.Y)
                val pen = A.supportAt(i + s, -n) + B.supportAt(j + t, n) - d + SKIN
                if (pen > 0) {
                    addDelta(a, A, i, i + 1, s, n * (pen * wa))
                    addDelta(b, B, j, j + 1, t, n * (-pen * wb))
                }
            }
        }
    }

    /**
     * Share of the push each side takes: inverse mass, and an attached animal (perched, crawling) holds its ground in
     * the first passes; when the swimmer is pinned between it and the rock, the later passes move both by mass.
     */
    private fun weights(a: Organism, b: Organism, holdAttached: Boolean): Pair<Double, Double> {
        val ma = if (a.attached && holdAttached) 1e6 else a.lengthCm * a.lengthCm * a.lengthCm
        val mb = if (b.attached && holdAttached) 1e6 else b.lengthCm * b.lengthCm * b.lengthCm
        return (mb / (ma + mb)) to (ma / (ma + mb))
    }

    private fun addDelta(o: Organism, body: BodyState, i0: Int, i1: Int, t: Double, d: Vec3) {
        val arr = pairDelta.getOrPut(o) { Array(BodySpline.POINTS) { Vec3.ZERO } }
        if (o.spline.rigid) { for (k in arr.indices) arr[k] = arr[k] + d; return }
        // body station → spline station (u = i/15), spread over a few stations so the push bends the spine smoothly
        val f = ((body.u[i0] + (body.u[i1] - body.u[i0]) * t) * (BodySpline.POINTS - 1)).coerceIn(0.0, BodySpline.POINTS - 1.0)
        for (q in 0 until BodySpline.POINTS) {
            val fall = (1 - abs(q - f) / 3.0).coerceAtLeast(0.0)
            if (fall > 0) arr[q] = arr[q] + d * fall
        }
    }

    /** Deepest penetration of [o]'s body into the environment (cm, ≤ 0 = clear): rock, floor, glass, water surface. */
    fun envPenetration(o: Organism, rockOn: Boolean = true, surfaceOn: Boolean = true): Double {
        val body = o.body
        body.refresh(o)
        var worst = Double.NEGATIVE_INFINITY
        for (i in 0 until BodyState.N) {
            val q = body.p[i]
            fun pen(n: Vec3, d: Double) { worst = max(worst, body.support(i, -n) - d) }
            if (rockOn) { val d = rock.sdf.dist(q); if (d < 20) pen(grad(q), d) }
            val n = floorNormal(q.x, q.z); pen(n, (q.y - rock.floorY(q.x, q.z)) * n.y)
            pen(Vec3.X, q.x); pen(Vec3(-1.0, 0.0, 0.0), rock.lengthCm - q.x)
            pen(Vec3.Z, q.z); pen(Vec3(0.0, 0.0, -1.0), rock.widthCm - q.z)
            if (surfaceOn) pen(Vec3(0.0, -1.0, 0.0), rock.waterLevelCm - q.y)
        }
        return worst
    }

    /** Overlap of two bodies (cm, ≤ 0 = clear), station against station. */
    fun pairPenetration(a: Organism, b: Organism): Double {
        a.body.refresh(a); b.body.refresh(b)
        val A = a.body; val B = b.body
        if (A.centre.distanceTo(B.centre) > A.radius + B.radius) return -1.0
        var worst = Double.NEGATIVE_INFINITY
        for (i in 0 until BodyState.N - 1) for (j in 0 until BodyState.N - 1) {
            val (s, t) = closestParams(A.p[i], A.p[i + 1], B.p[j], B.p[j + 1])
            val pa = A.p[i] + (A.p[i + 1] - A.p[i]) * s
            val pb = B.p[j] + (B.p[j + 1] - B.p[j]) * t
            val d = pa.distanceTo(pb)
            if (d < 1e-9) return Double.POSITIVE_INFINITY
            val n = (pa - pb) / d
            val sa = A.supportAt(i + s, -n)
            val sb = B.supportAt(j + t, n)
            worst = max(worst, sa + sb - d)
        }
        return worst
    }

    fun touchAllowedForTest(a: Organism, b: Organism, stations: List<com.reeflive.core.ai.CleaningStation>) = touchAllowed(a, b, stations)

    companion object {
        const val ITERATIONS = 6
        private val DOWN = Vec3(0.0, -1.0, 0.0)
        private const val RIM = 12
        /** A push still this large after the last iteration (the skin plus 1 mm of overlap) means the body is wedged (cm). */
        const val WEDGE_LIMIT = 0.15
        private const val UNSTICK_STEPS = 8
        /** Resolves in a row a wedged body is held before it backs out to clear water instead (~1 s at 20 Hz). */
        private const val HOLD_MAX = 20
        /** Largest tip of a rigid body per resolve iteration (rad). */
        private const val MAX_TIP = 0.15
        private val RIM_COS = DoubleArray(RIM) { kotlin.math.cos(it * 2 * Math.PI / RIM) }
        private val RIM_SIN = DoubleArray(RIM) { kotlin.math.sin(it * 2 * Math.PI / RIM) }
        const val MAX_ITERATIONS = 12
        /** Iterations that bend the spine before the remaining ones translate the body. */
        const val BEND_ITERATIONS = 3
        /** Animal-vs-animal passes per tick (each followed by the environment resolve of the bodies it moved). */
        const val PAIR_PASSES = 6
        /** Contact gap the resolve leaves (cm): a body comes to rest 0.5 mm off the surface. */
        const val SKIN = 0.05
        /** How fast a resting fish's body settles onto the slope under its belly (1/s). */
        const val SETTLE_RATE = 4.0

        /** Parameters (s, t) of the closest points of segments p1–q1 and p2–q2 (Ericson, Real-Time Collision Detection 5.1.9). */
        fun closestParams(p1: Vec3, q1: Vec3, p2: Vec3, q2: Vec3): Pair<Double, Double> {
            val d1 = q1 - p1; val d2 = q2 - p2; val r = p1 - p2
            val a = d1 dot d1; val e = d2 dot d2; val f = d2 dot r
            if (a <= 1e-12 && e <= 1e-12) return 0.0 to 0.0
            var s: Double; var t: Double
            if (a <= 1e-12) { s = 0.0; t = (f / e).coerceIn(0.0, 1.0) }
            else {
                val c = d1 dot r
                if (e <= 1e-12) { t = 0.0; s = (-c / a).coerceIn(0.0, 1.0) }
                else {
                    val b = d1 dot d2
                    val den = a * e - b * b
                    s = if (den > 1e-12) ((b * f - c * e) / den).coerceIn(0.0, 1.0) else 0.0
                    t = (b * s + f) / e
                    if (t < 0) { t = 0.0; s = (-c / a).coerceIn(0.0, 1.0) } else if (t > 1) { t = 1.0; s = ((b - c) / a).coerceIn(0.0, 1.0) }
                }
            }
            return s to t
        }
    }
}
