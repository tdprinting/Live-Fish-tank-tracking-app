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
/** Drawn rigid and centred on its spine head (the renderer's limb-rigged crawlers, TankRenderer uLimbOn). */
internal fun drawnCentred(o: Organism): Boolean = (o.limbRig?.out?.size ?: 0) >= 40

/**
 * A brittle star seated on its arms ([BrittleArms]): its rig rests the disc on the rock, sand and glass and keeps every
 * arm out of them, so those are its rig's, as a hermit's are its seat's ([CrawlerSeat]).
 */
internal fun armSeated(o: Organism): Boolean = o.seatOn && o.limbRig is BrittleArms

class BodyState {
    val p = Array(N) { Vec3.ZERO }
    val up = Array(N) { Vec3.Y }
    val side = Array(N) { Vec3.Z }
    /** Station spine coordinate, centre offset along up, semi-axes along up and side (cm). */
    val u = DoubleArray(N)
    val ctr = DoubleArray(N)
    val a = DoubleArray(N)
    val b = DoubleArray(N)
    /**
     * A crawler on its seat ([CrawlerSeat]) is drawn where the seat rests it, not at the sim position: its body is that
     * drawn body. While a resolve moves the animal, the seat is carried along at this offset from the sim position.
     */
    internal var seatOff: Vec3? = null
    /** Heading and up of the spline's remembered clear pose ([BodySpline.saveSafe]). */
    var safeHeading = Vec3.X
    var safeUp = Vec3.Y
    /** Swim wave and bend of that pose (they widen the body's sections, [refresh]). */
    var safeAmp = 0.0
    var safeBend = 0.0
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
    /** Escaping a sealed hollow of the rock ([BodyCollision.sealedIn]). */
    var escaping = false
    /** The last resolve left this pose clear of the environment (committed as the clear pose at the end of the tick). */
    var clearPose = false
    internal var clearBroad = false
    internal var clearRock = true
    /** Coming out of a burrow / the sand: resolves left that it is still let into the geometry (0: not emerging). */
    var emerging = 0
    /** The last exempt resolve was in a burrow, a hole or the sand (posture 3 / 5). */
    internal var inBurrow = false
    /** Normal of the contact under the belly at the last resolve (null: none), for a resting fish's settle. */
    var bellyN: Vec3? = null
    /** Sim time the body's middle first read sealed in (−1: it does not). */
    var sealedSince = -1.0

    fun refresh(o: Organism) {
        if (unchanged(o)) return
        if (o.species !== shapeOf || shape == null) { shape = BodyShapes.of(o.species); shapeOf = o.species }
        val s = shape!!
        val st = s.stations(N)
        val L = o.lengthCm
        val seat = seatOf(o)
        val rigid = seat != null || o.spline.rigid && o.spline.override == null
        if (!rigid) o.spline.samplePoints(spine)
        // (seated: straight back from the seat point along the seat's forward, its up the seat's, as Organism.toPose draws it)
        val base = if (seat != null) seatOff?.let { o.pos + it } ?: o.seatPos else o.pos
        val h = if (seat != null) seat.fwd else o.heading.normalized(Vec3.X)
        val upRef = if (seat != null) o.seatUp else o.up
        // a limb-rigged crawler (brittle star, Sally Lightfoot, hermit: ten or more limbs) is drawn rigid about spine
        // station 0, its model centred there (fish.vert, uLimbOn: snout at +0.5 L, tail at -0.5 L), as its seat's
        // contact points are; every other body runs from the head back along its spine
        val centred = drawnCentred(o)
        // the renderer's lateral swim wave and C-start bend (fish.vert) widen the swept body; the shrimp tail-flip curls the tail down
        val amp = min(o.amp, MAX_WAVE_AMP)
        val mode = o.toPoseMotionMode()
        for (i in 0 until N) {
            val uu = s.uMin + (s.uMax - s.uMin) * i / (N - 1)
            u[i] = uu
            p[i] = if (!rigid) spineAt(uu) else if (centred) base - h * ((uu - 0.5) * L) else base - h * (uu * L)
        }
        var cx = 0.0; var cy = 0.0; var cz = 0.0
        for (i in 0 until N) {
            val t = if (rigid) h else (if (i == 0) p[0] - p[1] else if (i == N - 1) p[N - 2] - p[N - 1] else p[i - 1] - p[i + 1]).normalized(h)
            val upv = upRef.rejectFrom(t).normalized(Vec3.Y.rejectFrom(t).normalized(Vec3.Z))
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
        remember(o)
    }

    // ---- what the last [refresh] read: the same again builds the same body, so it is kept (the collision refreshes a
    // body many times a tick, mostly unmoved). Every input [refresh] reads must be here. ----
    private var mValid = false
    private var mSpline: BodySpline? = null
    private var mVersion = 0L
    private var mPos = Vec3.ZERO; private var mHeading = Vec3.X; private var mUp = Vec3.Y
    private var mAmp = 0.0; private var mBend = 0.0; private var mLength = 0.0; private var mMode = 0
    private var mSpecies: SimSpecies? = null
    private var mSeat: CrawlerSeat? = null
    private var mSeatFwd = Vec3.X; private var mSeatPos = Vec3.ZERO; private var mSeatUp = Vec3.Y; private var mSeatOff: Vec3? = null

    private fun unchanged(o: Organism): Boolean {
        if (!mValid || o.spline.overridden) return false
        val seat = seatOf(o)
        return o.spline === mSpline && o.spline.version == mVersion && o.pos == mPos && o.heading == mHeading && o.up == mUp &&
            o.amp == mAmp && o.bend == mBend && o.lengthCm == mLength && o.species === mSpecies && o.toPoseMotionMode() == mMode &&
            seat === mSeat && (seat == null || seat.fwd == mSeatFwd && o.seatPos == mSeatPos && o.seatUp == mSeatUp && seatOff == mSeatOff)
    }

    private fun remember(o: Organism) {
        val seat = seatOf(o)
        mValid = !o.spline.overridden
        mSpline = o.spline; mVersion = o.spline.version
        mPos = o.pos; mHeading = o.heading; mUp = o.up; mAmp = o.amp; mBend = o.bend; mLength = o.lengthCm
        mSpecies = o.species; mMode = o.toPoseMotionMode(); mSeat = seat
        if (seat != null) { mSeatFwd = seat.fwd; mSeatPos = o.seatPos; mSeatUp = o.seatUp; mSeatOff = seatOff }
    }

    companion object {
        const val N = 16
        /** fish.vert MAX_WAVE_AMP: the drawn tail wave is capped at 0.12 BL. */
        const val MAX_WAVE_AMP = 0.12
    }
}

/** The seat [o] is drawn on (fully blended in), or null: then its body is the sim's own. */
internal fun seatOf(o: Organism): CrawlerSeat? = o.seat?.takeIf { o.seatOn && it.blend >= 1.0 }

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

    /**
     * Rock distance of each of a section's [RIM] rim points (centre [c], unit axes [u], [s], semi-axes [a], [b]; [dc] the
     * distance at the centre) into [rimD], except that a point the baked grid's slope bound
     * ([com.reeflive.core.geom.SdfGrid.slopeIn]) proves at least [thr] from the rock reads +∞ unsampled: first the whole
     * rim against the centre, then each arc of three against its middle point. The bound is exact, so every reading at
     * or below [thr] is the sampled one and a skip changes no result.
     */
    private fun rimRock(c: Vec3, u: Vec3, s: Vec3, a: Double, b: Double, dc: Double, thr: Double) {
        val r = reach(a, b, u, s)
        val g = grid
        // (no grid, or a box past its tables: ∞ or NaN, which skips nothing)
        val k = g?.slopeIn(c.x - r, c.y - r, c.z - r, c.x + r, c.y + r, c.z + r) ?: Double.POSITIVE_INFINITY
        if (dc - k * r - 1e-6 >= thr) { rimD.fill(Double.POSITIVE_INFINITY); return }
        val ra = k * (ARC_CHORD * r) + 1e-6
        for (m in 0 until RIM step 3) {
            val dm = rimSample(c, u, s, a, b, m)
            rimD[m] = dm
            val prev = (m + RIM - 1) % RIM; val next = m + 1
            if (dm - ra >= thr) { rimD[prev] = Double.POSITIVE_INFINITY; rimD[next] = Double.POSITIVE_INFINITY }
            else { rimD[prev] = rimSample(c, u, s, a, b, prev); rimD[next] = rimSample(c, u, s, a, b, next) }
        }
    }
    private val rimD = DoubleArray(RIM)

    /**
     * Most any point of a section (centre [c], unit axes [u], [s], semi-axes [a], [b]) can be under the sand: the floor's
     * bound over the section's plan box ([RockData.floorMax]) less its lowest point. A skip on it changes no result.
     */
    private fun sandReach(c: Vec3, u: Vec3, s: Vec3, a: Double, b: Double): Double {
        val ex = abs(u.x * a) + abs(s.x * b) + 1e-9; val ez = abs(u.z * a) + abs(s.z * b) + 1e-9
        val ey = abs(u.y * a) + abs(s.y * b) + 1e-9
        return rock.floorMax(c.x - ex, c.z - ez, c.x + ex, c.z + ez) - (c.y - ey)
    }

    /** Rim point [k] of a section: `c + u·(a·cos) + s·(b·sin)`, summed as the Vec3 operators sum it. */
    private fun rimX(c: Vec3, u: Vec3, s: Vec3, a: Double, b: Double, k: Int) = c.x + u.x * (a * RIM_COS[k]) + s.x * (b * RIM_SIN[k])
    private fun rimY(c: Vec3, u: Vec3, s: Vec3, a: Double, b: Double, k: Int) = c.y + u.y * (a * RIM_COS[k]) + s.y * (b * RIM_SIN[k])
    private fun rimZ(c: Vec3, u: Vec3, s: Vec3, a: Double, b: Double, k: Int) = c.z + u.z * (a * RIM_COS[k]) + s.z * (b * RIM_SIN[k])
    private fun rimSample(c: Vec3, u: Vec3, s: Vec3, a: Double, b: Double, k: Int) =
        rock.sdf.distAt(rimX(c, u, s, a, b, k), rimY(c, u, s, a, b, k), rimZ(c, u, s, a, b, k))

    /** Farthest a point `a·cos θ·u + b·sin θ·s` (u, s unit) of an elliptic section gets from its centre, or a bit more. */
    private fun reach(a: Double, b: Double, u: Vec3, s: Vec3): Double {
        val m = max(abs(a), abs(b))
        return sqrt(m * m + abs(a * b * (u dot s)))
    }
    private val hash = SpatialHash<Organism>(15.0) { it.body.centre }
    private val pairDelta = LinkedHashMap<Organism, Array<Vec3>>()
    /** Summed falloff weight of the pushes on each spline station ([pairDelta]): overlapping segment pairs share one push. */
    private val pairWeight = HashMap<Organism, DoubleArray>()
    /** Seated crawlers in a pair with another seated one this pass (one may climb onto the other's shell). */
    private val climbers = HashSet<Organism>()
    private val order = HashMap<Organism, Int>()

    // ---- counters (tests / profiling) ----
    /** Off only for cost measurements (the bodies then pass through everything as before). */
    var enabled = true
    var envPushes = 0L; private set
    var envCalls = 0L; private set
    var wedgeRestores = 0L; private set
    /** Wedge holds per species id (tests). */
    val wedgeBy = HashMap<String, Long>()
    var pairPushes = 0L; private set

    /** True when this animal's body is deliberately allowed into the geometry (see class doc). */
    fun exempt(o: Organism): Boolean =
        o.brainKind.isSessile || o.posture == 3 || o.posture == 5 || o.inSand || wedgedAsleep(o) || o.spline.override != null || !o.visible || o.body.emerging > 0

    /**
     * A tang asleep in its bed: wedged on its side under an overhang, in a pose its shelter search fitted to the rock
     * (TangShelters), as a goby's burrow is let into the sand. Coming out it is held like a body leaving a burrow.
     */
    private fun wedgedAsleep(o: Organism): Boolean = o.sleeping && o.brainKind == BrainKind.TANG

    /** Decor and equipment as solids, coral as soft contacts (set by the Simulation; null = none). */
    var obstacles: StaticObstacles? = null
    /** Station pushes off the static obstacles (tests). */
    var obstaclePushes = 0L; private set
    private val openWater = arrayOfNulls<OpenWater>(OpenWater.LEVELS.size)
    /** Sealed-hollow escapes so far (tests). */
    var sealedEscapes = 0L; private set

    /**
     * Thickness class of [o]'s body ([OpenWater.LEVELS] index): the gap a body passes is set by its slimmest
     * cross-section at its thickest station, plus 2 mm.
     */
    fun levelOf(o: Organism): Int {
        val sh = o.body.currentShape ?: BodyShapes.of(o.species)
        var thick = 0.0
        for (i in sh.u.indices) thick = max(thick, min(sh.halfWidth[i], 0.5 * (sh.top[i] - sh.bottom[i])))
        val need = thick * o.lengthCm + 0.2
        val lv = OpenWater.LEVELS.indexOfFirst { it >= need }
        return if (lv < 0) OpenWater.LEVELS.size - 1 else lv
    }

    /**
     * Whether [o]'s body is escaping a sealed hollow of the rock ([OpenWater.sealedIn]). Latched: once sealed in, the
     * body passes the rock until its middle is back in the open water. Only a clock ([now], the sim time: the
     * steering's once-a-tick call) starts an escape, and only after the middle has read sealed for [SEALED_DELAY_S]: a
     * fish brushing the edge of a pocket too narrow for it (one tick in a crevice cell) does not drop the rock.
     */
    fun sealedIn(o: Organism, now: Double = Double.NaN): Boolean {
        o.body.refresh(o)
        // water even the thinnest body (level 0) cannot reach from the open tank: a closed shell of the bake. A cave
        // whose mouth is only narrower than this fish is not one: the fish stays in it like any other wedge (no clip)
        val lv = 0
        val w = openWater[lv] ?: OpenWater.of(rock, OpenWater.LEVELS[lv]).also { openWater[lv] = it }
        val b = o.body
        if (b.escaping) { if (w.open(b.centre)) { b.escaping = false; b.sealedSince = -1.0 } }
        else if (w.sealedIn(b.centre)) {
            if (!now.isNaN()) {
                if (b.sealedSince < 0) b.sealedSince = now
                if (now - b.sealedSince >= SEALED_DELAY_S) b.escaping = true
            }
        } else b.sealedSince = -1.0
        if (b.escaping) sealedEscapes++
        return b.escaping
    }

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
        // the body follows wherever the animal was put since its spline last moved (the crawler seat writes the seated
        // pose back into the position): pushed from a stale spline head, the position would snap back to it
        if (o.spline.headPos != o.pos && o.spline.override == null) o.spline.update(o.pos, o.heading, 0.0, 0.0)
        // a seated crawler's body is its drawn one, on the seat: the pushes move the seat, not the sim position the
        // crawl walks (moved, its walk would reattach to another face and flip the animal over), and no wedge hold
        // applies (its seat keeps it on the surface it walks; a hold would snap it back to an old pose)
        val seat = seatOf(o)
        val sim0 = if (seat != null) SimPose(o) else null
        if (seat != null) o.body.seatOff = o.seatPos - o.pos
        try { return resolveEnv0(o, dt, rockOn, surfaceOn, settle, wedgeFallback && seat == null && !armSeated(o)) } finally {
            val off = o.body.seatOff
            o.body.seatOff = null
            if (seat != null && sim0 != null && off != null) {
                val d = o.pos - sim0.pos
                sim0.restore(o)
                // the sim position stays out of the sand too (sand heaped over a resting crawler, a placement onto an
                // old height): it is what the walk and the brain read
                val fy = rock.floorY(o.pos.x, o.pos.z)
                if (o.pos.y < fy) { o.pos = o.pos.withY(fy); o.spline.update(o.pos, o.heading, 0.0, 0.0) }
                // (what is left is equipment, decor and the water surface: it may climb onto them, so no slide)
                // (and set back down onto the surface unless that puts it deeper into the equipment it climbed, or it is
                // climbing onto another hermit in this pair pass)
                val ob = obstacles
                val r = 0.3 * o.lengthCm
                carrySeat(o, seat, sim0.pos + off + d - o.seatPos, slide = false,
                    settle = if (o in climbers) null else { p -> ob == null || ob.depth(p, r) <= max(ob.depth(seat.pos, r), 0.0) + SKIN })
                // the seat's own contact points (shell, shield, coxae) are its rock and sand: one still inside after
                // the seat's solve (sand heaped on it, a squeeze) is pushed out along that surface's normal
                seat.contactPush(rock, o.lengthCm, SKIN)?.let { carrySeat(o, seat, it, slide = false) }
            } else if (o.attached && drawnCentred(o) && !armSeated(o)) {
                // a limb-rigged crawler on the rock: its sim position is the point its feet stand round, and stays on the
                // rock's surface, not in it (the body push and its tip move it as a whole; a crevice can close over it)
                val dr = rock.sdf.dist(o.pos)
                if (dr < 0.0) { o.pos = rock.sdf.project(o.pos, 0.02); o.spline.update(o.pos, o.heading, 0.0, 0.0) }
                // nor under the sand (sand that settled over it, a placement onto an old height), where a rock roof
                // leaves the room to stand on it
                val fy = rock.floorY(o.pos.x, o.pos.z)
                if (o.pos.y < fy && rock.sdf.dist(o.pos.withY(fy)) > 0.0) { o.pos = o.pos.withY(fy); o.spline.update(o.pos, o.heading, 0.0, 0.0) }
            }
        }
    }

    /** The sim pose of an animal (position, heading, up, spline), to put back after a trial move. */
    private class SimPose(o: Organism) {
        val pos = o.pos; val heading = o.heading; val up = o.up; val spline = o.spline.pose()
        fun restore(o: Organism) { o.spline.setPose(spline); o.pos = pos; o.heading = heading; o.up = up }
    }

    /**
     * Moves the seat [o] is drawn on by [d] (the push its drawn body took), at most [SEAT_CARRY_CM] a resolve: the
     * seat's own contact solve settles the rest next tick, so the drawn body glides rather than hops.
     */
    /**
     * Moves a seated crawler's drawn body by [d] (at most [SEAT_CARRY_CM]). A shove off another animal ([slide]) slides
     * it along the surface it holds on to, not off it (the seat's contact solve owns the normal), and sets it back down.
     */
    private fun carrySeat(o: Organism, seat: CrawlerSeat, d: Vec3, slide: Boolean = true, settle: ((Vec3) -> Boolean)? = null) {
        val m = if (slide) d - seat.up * (d dot seat.up) else d
        if (m.lengthSq() > 1e-18) seat.carry(o, m.clampLength(SEAT_CARRY_CM), settle = if (slide) CrawlerSeat.ANY else settle)
    }

    private fun resolveEnv0(o: Organism, dt: Double, rockOn: Boolean, surfaceOn: Boolean, settle: Boolean, wedgeFallback: Boolean): Double {
        // coming out of a burrow, a hole or the sand (posture 3 / 5 just left): the body is still partly in it, and
        // stays let in (inside the glass) until it is out, for at most [EMERGE_TICKS] resolves
        val bs = o.body
        if (o.posture == 3 || o.posture == 5 || o.inSand || wedgedAsleep(o)) { bs.inBurrow = true; bs.emerging = 0 }
        else if (bs.inBurrow) {
            if (bs.emerging == 0) bs.emerging = EMERGE_TICKS + 1
            bs.emerging--
            if (bs.emerging <= 0 || rimWithin(o, rockOn, surfaceOn, 0.0)) { bs.emerging = 0; bs.inBurrow = false }
        }
        if (exempt(o)) {
            o.spline.dropSafe(); o.body.clearPose = false
            // a burrow, a den or the sand lets a body into the rock and sand, never through the glass (a goby's tail in
            // a burrow by the front pane, tee-clips #6)
            if (o.posture == 3 || o.posture == 5 || bs.emerging > 0) keepInsideGlass(o)
            return 0.0
        }
        // (the sealed-in check below refreshes the body for these; unless the settle then turns it, the first pass needs
        // no refresh of its own)
        var fresh = rockOn && o.isFish && !o.attached
        // a swimmer in a sealed hollow of the rock passes through the rock until it is back in the open water (a
        // crawler on the rock is always against it: never counted sealed in)
        // (a crawler on its seat: the seat's contact solve rests its shell and feet on the rock and sand, CrawlerSeat;
        // the pushes here keep it off the glass, the surface, the equipment and the other animals, and resolveEnv pushes
        // a contact point the seat left inside out along the surface normal)
        val seatedNow = seatOf(o) != null || armSeated(o)
        @Suppress("NAME_SHADOWING") val rockOn = rockOn && !(o.isFish && !o.attached && sealedIn(o)) && !seatedNow
        envCalls++
        val mask = StaticObstacles.passMask(o)
        var worst = 0.0
        var clear = false
        var broad = false
        var lastPush = 0.0
        var bellyN = Vec3.ZERO
        var bellyCount = 0
        // (a seated crawler is moved as a whole: the seat keeps its shape on the surface)
        val seated = seatOf(o) != null
        val rigid = o.spline.rigid || seated
        // how far each station was from the rock on the first pass, and how far the body has moved since: a station
        // that started farther from the rock than the body has moved cannot have reached it (distance is 1-Lipschitz)
        var moved = 0.0
        // (not a fish propped on its fins, posture 1: its pitch is the pose it holds, a hawkfish head-up on the sand)
        if (settle && o.isFish && o.posture != 1) o.body.bellyN?.let { n ->
            fresh = false
            val k = min(1.0, dt * SETTLE_RATE)
            val flat = o.heading.rejectFrom(n).normalized(o.heading)
            // (not past a swimmer's pitch limit: on a steep face it rests level against the slope, it does not stand on it)
            if (abs(flat.y) <= SETTLE_MAX_PITCH) o.heading = o.heading.lerp(flat, k).normalized(o.heading)
            o.up = o.up.lerp(n.rejectFrom(o.heading).normalized(o.up), k).normalized(Vec3.Y)
        }
        // (a fast body rolls over an edge as fast as it travels round it: a startled crab's dash home is not capped)
        tipLeft = max(MAX_TIP_RESOLVE, o.vel.length() * dt / max(0.25 * o.lengthCm, 0.5))
        for (iter in 0 until MAX_ITERATIONS) {
            val body = o.body
            if (iter > 0 || !fresh) body.refresh(o)
            if (iter == 0) {
                val dc = if (rockOn) rock.sdf.dist(body.centre) / SDF_LIP - body.radius else Double.POSITIVE_INFINITY
                rockGap.fill(dc)
                // broad phase: a body whose bounding sphere is clear of the rock, the sand, the glass and the surface
                // (most swimmers in open water, most of the time) needs no station checks
                // (a body in a coral colony goes on to the station checks: they report where it touches the coral)
                if (dc > SKIN && clearOfBounds(body, surfaceOn) && obstacles.let { ob -> ob == null || ob.push(body.centre, body.radius + SKIN, mask) == null && !ob.softTouched }) { clear = true; broad = true; break }
            }
            // no obstacle near the body (grown by how far a station's rock pushes may move it first): no station push
            // can find one either
            val obNear = obstacles?.near(body.centre, body.radius + SKIN + OB_MARGIN + 1e-6, mask) ?: false
            // the highest the sand gets under the body (grown by SAND_MARGIN): a station that stays within that margin
            // of where it started and sits higher above it than its sand checks reach skips them (they could not fire;
            // nor do they for a seated crawler)
            val sandR = body.radius + SAND_MARGIN + 1e-6
            val sandTop = if (seatedNow) Double.NEGATIVE_INFINITY else rock.floorMax(body.centre.x - sandR, body.centre.z - sandR, body.centre.x + sandR, body.centre.z + sandR)
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
                // the halfway section to the next station reaches half a segment farther from this centre
                val boundH = if (i < BodyState.N - 1) max(bound, abs(body.ctr[i + 1]) + max(body.a[i + 1], body.b[i + 1]) + SKIN) + 0.5 * p0.distanceTo(body.p[i + 1]) else bound
                if (rockOn && rockGap[i] < moved + SKIN) {
                    val d = rock.sdf.dist(q)
                    if (iter == 0) rockGap[i] = d / SDF_LIP - boundH
                    if (d < SDF_LIP * boundH) {
                        // planar contact at the centre, refined at the section's deepest point (curved and concave rock)
                        if (d < bound) {
                            val n = grad(q)
                            contact(n, d)
                            val e = body.extreme(i, -n) + (q - p0)
                            val de = rock.sdf.dist(e)
                            if (de < -SKIN * 0.5) { val ne = grad(e); q += ne * (-de + SKIN); noteContact(o, ne, i, rigid, body) }
                        }
                        // concave rock (a ridge, a crevice lip) can reach a side of the section the centre's normal does
                        // not face: the deepest of twelve rim points is pushed out along its own normal
                        if (d < SDF_LIP * boundH - SKIN) {
                            val off = q - p0
                            val c = p0 + body.up[i] * body.ctr[i] + off
                            var dMin = 0.0; var at = c
                            // (the rim points of a section the grid's slope bound keeps out of the rock by more than the
                            // half skin the push below needs are not sampled: the same push, far fewer samples)
                            if (d < SDF_LIP * bound - SKIN) {
                                val dc0 = rock.sdf.dist(c)
                                val ua = body.up[i]; val sa = body.side[i]; val aa = body.a[i]; val ba = body.b[i]
                                rimRock(c, ua, sa, aa, ba, dc0, -SKIN * 0.5)
                                for (k in 0 until RIM) {
                                    val dr = rimD[k]
                                    if (dr < dMin) { dMin = dr; at = Vec3(rimX(c, ua, sa, aa, ba, k), rimY(c, ua, sa, aa, ba, k), rimZ(c, ua, sa, aa, ba, k)) }
                                }
                                // and the centre (a thin rock edge can pass between the rim points)
                                if (dc0 < dMin) { dMin = dc0; at = c }
                            }
                            // and halfway to the next station (a rock edge between two stations)
                            if (i < BodyState.N - 1) {
                                val j = i + 1
                                val hu = (body.up[i] + body.up[j]).normalized(body.up[i])
                                val hs = (body.side[i] + body.side[j]).normalized(body.side[i])
                                val ha = 0.5 * (body.a[i] + body.a[j]); val hb = 0.5 * (body.b[i] + body.b[j])
                                val hc = (body.p[i] + body.p[j]) * 0.5 + hu * (0.5 * (body.ctr[i] + body.ctr[j])) + off
                                val dh = rock.sdf.dist(hc)
                                rimRock(hc, hu, hs, ha, hb, dh, -SKIN * 0.5)
                                for (k in 0 until RIM) {
                                    val dr = rimD[k]
                                    if (dr < dMin) { dMin = dr; at = Vec3(rimX(hc, hu, hs, ha, hb, k), rimY(hc, hu, hs, ha, hb, k), rimZ(hc, hu, hs, ha, hb, k)) }
                                }
                                if (dh < dMin) { dMin = dh; at = hc }
                            }
                            if (dMin < -SKIN * 0.5) { val nr = grad(at); q += nr * (-dMin + SKIN); noteContact(o, nr, i, rigid, body) }
                        }
                    }
                }
                // decor and equipment ([StaticObstacles]): the section's bounding sphere is pushed off their capsules; coral
                // is soft (the body passes, the colony bends): the station is reported in its contacts
                if (obNear || (q - p0).length() > OB_MARGIN) obstacles?.let { ob ->
                    val cc = q + body.up[i] * body.ctr[i]
                    ob.push(cc, max(body.a[i], body.b[i]) + SKIN, mask, o.id)?.let { v -> q += v; obstaclePushes++ }
                }
                // the sand is a height field, not a distance field: a steep mound beside the station can reach the body
                // even when the sand under the centre is far below, so the lowest point of the section is checked too
                val sandFar = q.y - sandTop >= boundH + SAND_SLOPE_CM && (q.x - p0.x) * (q.x - p0.x) + (q.z - p0.z) * (q.z - p0.z) <= SAND_MARGIN * SAND_MARGIN
                val fy = if (sandFar) 0.0 else rock.floorY(q.x, q.z)
                val low = if (sandFar) q else body.extreme(i, DOWN) + (q - p0)
                if (!seatedNow && !sandFar && (q.y - fy < boundH + SAND_SLOPE_CM || low.y - rock.floorY(low.x, low.z) < SKIN)) {
                    val n = floorNormal(q.x, q.z)
                    contact(n, (q.y - fy) * n.y)   // (q has not moved since fy was read)
                    // the sand curves (mounds against the rock feet, rubble, pits): check the height under the section's
                    // deepest points (along the slope normal, straight down, and twelve around its rim) and lift what is under
                    val off = q - p0
                    var deficit = Double.NEGATIVE_INFINITY
                    fun under(e: Vec3) { deficit = max(deficit, rock.floorY(e.x, e.z) - e.y) }
                    under(body.extreme(i, -n) + off)
                    under(body.extreme(i, DOWN) + off)
                    val cc = q + body.up[i] * body.ctr[i]
                    // (rim points without a Vec3 each: the same sums in the same order)
                    // (a rim the floor's bound keeps clear by more than the half skin the lift needs is not sampled)
                    fun underRim(c: Vec3, u: Vec3, s: Vec3, a: Double, b: Double) {
                        if (sandReach(c, u, s, a, b) <= -SKIN * 0.5) return
                        for (k in 0 until RIM) {
                            val ac = a * RIM_COS[k]; val bs = b * RIM_SIN[k]
                            val rx = c.x + u.x * ac + s.x * bs; val rz = c.z + u.z * ac + s.z * bs
                            deficit = max(deficit, rock.floorY(rx, rz) - (c.y + u.y * ac + s.y * bs))
                        }
                    }
                    underRim(cc, body.up[i], body.side[i], body.a[i], body.b[i])
                    // and halfway to the next station (a mound between two stations)
                    if (i < BodyState.N - 1) {
                        val j = i + 1
                        val hu = (body.up[i] + body.up[j]).normalized(body.up[i])
                        val hs = (body.side[i] + body.side[j]).normalized(body.side[i])
                        val ha = 0.5 * (body.a[i] + body.a[j]); val hb = 0.5 * (body.b[i] + body.b[j])
                        val hc = (body.p[i] + body.p[j]) * 0.5 + hu * (0.5 * (body.ctr[i] + body.ctr[j])) + off
                        underRim(hc, hu, hs, ha, hb)
                    }
                    if (deficit > -SKIN * 0.5) { q += Vec3.Y * (deficit + SKIN); noteContact(o, n, i, rigid, body) }
                }
                // (a seated crawler's glass is its seat's, as its rock and sand are)
                if (!seatedNow) {
                    if (q.x < bound) contact(Vec3.X, q.x)
                    if (rock.lengthCm - q.x < bound) contact(Vec3(-1.0, 0.0, 0.0), rock.lengthCm - q.x)
                    if (q.z < bound) contact(Vec3.Z, q.z)
                    if (rock.widthCm - q.z < bound) contact(Vec3(0.0, 0.0, -1.0), rock.widthCm - q.z)
                }
                if (surfaceOn && rock.waterLevelCm - q.y < bound) contact(Vec3(0.0, -1.0, 0.0), rock.waterLevelCm - q.y)
                // the halfway section to the next station against the glass and the surface (a body crossing a pane
                // corner-on reaches it between two stations)
                if (i < BodyState.N - 1 && !seatedNow) {
                    val j = i + 1
                    val pj = body.p[j]
                    val hpx = (p0.x + pj.x) * 0.5 + (q.x - p0.x); val hpy = (p0.y + pj.y) * 0.5 + (q.y - p0.y); val hpz = (p0.z + pj.z) * 0.5 + (q.z - p0.z)
                    if (hpx < boundH || rock.lengthCm - hpx < boundH || hpz < boundH || rock.widthCm - hpz < boundH || surfaceOn && rock.waterLevelCm - hpy < boundH) {
                        val hp = Vec3(hpx, hpy, hpz)
                        val hu = (body.up[i] + body.up[j]).normalized(body.up[i])
                        val hs = (body.side[i] + body.side[j]).normalized(body.side[i])
                        val ha = 0.5 * (body.a[i] + body.a[j]); val hb = 0.5 * (body.b[i] + body.b[j])
                        val hc = hp + hu * (0.5 * (body.ctr[i] + body.ctr[j]))
                        // support of the section's ellipse along each wall's inward normal: |up·n|·a + |side·n|·b
                        fun wall(nx: Double, ny: Double, nz: Double, gap: Double) {
                            val sup = abs(hu.x * nx + hu.y * ny + hu.z * nz) * ha + abs(hs.x * nx + hs.y * ny + hs.z * nz) * hb
                            val pen = sup - gap + SKIN
                            if (pen > 0) q += Vec3(nx, ny, nz) * pen
                        }
                        wall(1.0, 0.0, 0.0, hc.x); wall(-1.0, 0.0, 0.0, rock.lengthCm - hc.x)
                        wall(0.0, 0.0, 1.0, hc.z); wall(0.0, 0.0, -1.0, rock.widthCm - hc.z)
                        if (surfaceOn) wall(0.0, -1.0, 0.0, rock.waterLevelCm - hc.y)
                    }
                }
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
            if (rigid && !seated && o.spline.override == null) tip(o, body)
            // (a fish propped on its fins, posture 1 (a hawkfish head-up on the sand, a blenny on its perch), holds its
            // pose: it is lifted off what it rests on, not bent or tipped flat)
            applyDeltas(o, body, if (rigid || iter >= BEND_ITERATIONS || o.isFish && o.posture == 1) null else deltas, translate)
        }
        // still pushing (the planar contacts over-push at a convex edge, and two faces can trade the body back and forth)
        // but every rim point is clear: the pose fits
        if (!clear && wedgeFallback && lastPush > WEDGE_LIMIT && rimWithin(o, rockOn, surfaceOn, 0.0)) clear = true
        lastClear = clear || lastPush <= WEDGE_LIMIT
        o.body.clearPose = clear
        o.body.clearBroad = broad
        o.body.clearRock = rockOn
        if (clear) {
            // remembered as the pose to hold to only once it is clear of the other bodies too ([commitSafe])
            if (wedgeFallback) o.body.held = 0   // (not the re-settle of a held pose: that is still the same hold)
        } else if (wedgeFallback && lastPush > WEDGE_LIMIT) {
            // held this long, the behaviour keeps pressing into the same wedge: find clear water instead of freezing
            if (o.body.held >= HOLD_MAX) {
                if (unstick(o, rockOn, surfaceOn)) { o.body.held = 0; return worst }
                // nothing clear near the pose it was pressed into: back out (or hop) from the pose it last fitted in
                if (restoreHeld(o, count = false) && unstick(o, rockOn, surfaceOn)) { o.body.held = 0; return worst }
            }
            if (restoreHeld(o)) {
                // a wedge the pushes cannot clear (a crevice narrower than the body, rock against sand): the body stays
                // where it last fitted instead of passing into the geometry. The swim wave and bend have moved on since
                // that pose was saved: settle it against them once more, and if that cannot clear it, the saved pose
                // itself (it was clear) stands
                resolveEnv(o, dt, rockOn, surfaceOn, false, wedgeFallback = false)
                if (!lastClear) {
                    restoreHeld(o, count = false)
                    // the saved pose is no longer clear either (the sand rose under it, an obstacle moved): hop out now
                    if (!rimWithin(o, rockOn, surfaceOn, SKIN)) {
                        o.body.held = 0
                        // nowhere clear to hop to either: forget that pose and push out from where the body is
                        if (!unstick(o, rockOn, surfaceOn)) { o.spline.dropSafe(); resolveEnv(o, dt, rockOn, surfaceOn, false, wedgeFallback = false) }
                    }
                }
                return worst
            }
            // wedged with no clear pose to return to (a fresh placement): back out along the body or lift clear
            unstick(o, rockOn, surfaceOn)
        }
        // the contact under the belly, for the next resolve's settle (applied before its pushes, so a settle that tips
        // the tail toward the sand is pushed out like any other move, never left in it)
        o.body.bellyN = if (bellyCount >= 2) bellyN.normalized(Vec3.Y) else null
        return worst
    }

    /** Worst depth of the body's rims (twelve points around every station) in the rock, sand, glass or surface. */
    /** Translates [o]'s body back inside the glass if any station's section reaches through it. */
    private fun keepInsideGlass(o: Organism) {
        val body = o.body
        body.refresh(o)
        var hiX = 0.0; var loX = 0.0; var hiZ = 0.0; var loZ = 0.0
        for (i in 0 until BodyState.N) {
            val c = body.p[i] + body.up[i] * body.ctr[i]
            val r = max(body.a[i], body.b[i]) + SKIN
            hiX = max(hiX, r - c.x); loX = min(loX, rock.lengthCm - r - c.x)
            hiZ = max(hiZ, r - c.z); loZ = min(loZ, rock.widthCm - r - c.z)
        }
        val t = Vec3(hiX + loX, 0.0, hiZ + loZ)
        if (t.lengthSq() < 1e-10) return
        applyDeltas(o, body, null, t)
        o.pos = o.spline.headPos
    }

    private fun clearOfBounds(body: BodyState, surfaceOn: Boolean): Boolean {
        val c = body.centre
        val r = body.radius + SKIN
        if (c.x < r || c.x > rock.lengthCm - r || c.z < r || c.z > rock.widthCm - r) return false
        if (surfaceOn && c.y + r > rock.waterLevelCm) return false
        val head = body.p[0]; val tail = body.p[BodyState.N - 1]
        val floor = max(rock.floorY(c.x, c.z), max(rock.floorY(head.x, head.z), rock.floorY(tail.x, tail.z)))
        return c.y - r > floor + BROAD_FLOOR_MARGIN
    }

    private fun rimDepth(o: Organism, rockOn: Boolean, surfaceOn: Boolean): Double {
        val body = o.body
        body.refresh(o)
        val mask = StaticObstacles.passMask(o)
        val ob = obstacles
        var worst = Double.NEGATIVE_INFINITY
        // every station and halfway between stations (the sections the resolve keeps clear), rim points and centre
        for (h in 0 until 2 * BodyState.N - 1) {
            val i = h / 2; val j = if (h % 2 == 1) i + 1 else i; val f = if (h % 2 == 1) 0.5 else 0.0
            val p = body.p[i] + (body.p[j] - body.p[i]) * f
            val up = (body.up[i] + (body.up[j] - body.up[i]) * f).normalized(Vec3.Y)
            val side = (body.side[i] + (body.side[j] - body.side[i]) * f).normalized(Vec3.Z)
            val a = body.a[i] + (body.a[j] - body.a[i]) * f
            val b = body.b[i] + (body.b[j] - body.b[i]) * f
            val c = p + up * (body.ctr[i] + (body.ctr[j] - body.ctr[i]) * f)
            for (k in 0..RIM) {
                val r = if (k == RIM) c else c + up * (a * RIM_COS[k]) + side * (b * RIM_SIN[k])
                if (rockOn) worst = max(worst, -rock.sdf.dist(r))
                worst = max(worst, rock.floorY(r.x, r.z) - r.y)
                worst = max(worst, max(-r.x, r.x - rock.lengthCm))
                worst = max(worst, max(-r.z, r.z - rock.widthCm))
                if (surfaceOn) worst = max(worst, r.y - rock.waterLevelCm)
                if (ob != null) worst = max(worst, ob.depth(r, 0.0, mask))
            }
        }
        return worst
    }

    /**
     * Whether [rimDepth] is at most [limit] (below it when [strict]): the comparison its callers make, without the number.
     * It stops at the first point past the limit, and skips the rock samples the grid's slope bound keeps clear of the
     * limit ([rimRock]) and the obstacle samples of one whose bounding sphere is ([StaticObstacles.depth]
     * of the sphere bounds that of every point in it), so it answers exactly as the comparison on [rimDepth] would.
     */
    private fun rimWithin(o: Organism, rockOn: Boolean, surfaceOn: Boolean, limit: Double, strict: Boolean = false): Boolean {
        val body = o.body
        body.refresh(o)
        val mask = StaticObstacles.passMask(o)
        val ob = obstacles
        // (NaN is past every limit, as it is in rimDepth's max)
        fun over(v: Double) = if (strict) !(v < limit) else !(v <= limit)
        // (a skipped rock point reads +∞: never past the limit; a strict limit skips only points strictly clear of it)
        val rockThr = if (strict) -limit + 1e-9 else -limit
        // (an obstacle a sphere misses reads at most 0 at any point in it: its bound serves a limit of at least 0 only)
        val obBound = ob != null && !strict && limit >= 0.0
        // the whole body first: every rim point lies within [rad] of its centre (a section's reach is at most half its
        // largest semi-axis past it), so what the bounds clear here needs no section's samples
        var semi = 0.0
        for (i in 0 until BodyState.N) semi = max(semi, max(body.a[i], body.b[i]))
        val bc = body.centre
        val rad = body.radius + 0.5 * semi + 1e-6
        val bodyRock = rockOn && !(rock.sdf.dist(bc) - (grid?.slopeIn(bc.x - rad, bc.y - rad, bc.z - rad, bc.x + rad, bc.y + rad, bc.z + rad) ?: Double.POSITIVE_INFINITY) * rad - 1e-6 >= rockThr)
        val bodySand = over(rock.floorMax(bc.x - rad, bc.z - rad, bc.x + rad, bc.z + rad) - (bc.y - rad))
        val bodyOb = ob != null && (!obBound || over(ob.depth(bc, rad, mask)))
        val bodyGlass = over(max(rad - bc.x, bc.x + rad - rock.lengthCm)) || over(max(rad - bc.z, bc.z + rad - rock.widthCm)) ||
            surfaceOn && over(bc.y + rad - rock.waterLevelCm)
        if (!bodyRock && !bodySand && !bodyOb && !bodyGlass) return true
        for (h in 0 until 2 * BodyState.N - 1) {
            val i = h / 2; val j = if (h % 2 == 1) i + 1 else i; val f = if (h % 2 == 1) 0.5 else 0.0
            val p = body.p[i] + (body.p[j] - body.p[i]) * f
            val up = (body.up[i] + (body.up[j] - body.up[i]) * f).normalized(Vec3.Y)
            val side = (body.side[i] + (body.side[j] - body.side[i]) * f).normalized(Vec3.Z)
            val a = body.a[i] + (body.a[j] - body.a[i]) * f
            val b = body.b[i] + (body.b[j] - body.b[i]) * f
            val c = p + up * (body.ctr[i] + (body.ctr[j] - body.ctr[i]) * f)
            if (bodyRock) {
                val dc = rock.sdf.dist(c)
                if (over(-dc)) return false
                rimRock(c, up, side, a, b, dc, rockThr)
                for (k in 0 until RIM) if (over(-rimD[k])) return false
            }
            // the section's own bounds for the obstacles, the sand and the glass
            val r = reach(a, b, up, side) + 1e-9
            val rimOb = bodyOb && (!obBound || over(ob!!.depth(c, r, mask)))
            val rimSand = bodySand && over(sandReach(c, up, side, a, b))
            val rimGlass = bodyGlass && (over(max(r - c.x, c.x + r - rock.lengthCm)) || over(max(r - c.z, c.z + r - rock.widthCm)) ||
                surfaceOn && over(c.y + r - rock.waterLevelCm))
            if (!rimOb && !rimSand && !rimGlass) continue
            for (k in 0..RIM) {
                val x = if (k == RIM) c.x else rimX(c, up, side, a, b, k)
                val y = if (k == RIM) c.y else rimY(c, up, side, a, b, k)
                val z = if (k == RIM) c.z else rimZ(c, up, side, a, b, k)
                if (rimSand && over(rock.floorY(x, z) - y)) return false
                if (rimGlass && (over(max(-x, x - rock.lengthCm)) || over(max(-z, z - rock.widthCm)))) return false
                if (rimGlass && surfaceOn && over(y - rock.waterLevelCm)) return false
                if (rimOb && over(ob!!.depth(Vec3(x, y, z), 0.0, mask))) return false
            }
        }
        return true
    }

    private fun saveSafe(o: Organism) {
        o.spline.saveSafe(); o.body.safeHeading = o.heading; o.body.safeUp = o.up; o.body.safeAmp = o.amp; o.body.safeBend = o.bend
    }

    /**
     * The sand rose inside [r] (x0, z0, x1, z1: [SandBed.takeRaised]) after these bodies were resolved this step (a
     * mouthful spat out, grains settling out of a cloud, a slump): a body over it whose section is now under the new
     * surface is pushed out again. Bodies elsewhere cost a bounds test.
     */
    fun afterSandRaised(animals: List<Organism>, r: DoubleArray, dt: Double) {
        if (!enabled) return
        for (o in animals) {
            if (!o.alive || exempt(o)) continue
            val body = o.body
            body.refresh(o)
            val c = body.centre; val rad = body.radius + SKIN
            if (c.x + rad < r[0] || c.x - rad > r[2] || c.z + rad < r[1] || c.z - rad > r[3]) continue
            if (c.y - rad > rock.floorY(c.x, c.z) + BROAD_FLOOR_MARGIN + MAX_MOUND_STEP_CM) continue
            if (sandDepth(body) <= 0.0) continue
            sandRepushes++
            resolveEnv(o, dt, rockOn = !body.escaping)
        }
    }
    var sandRepushes = 0L; private set

    /** Deepest point of the body's sections (stations and halfway) under the sand (cm, ≤ 0 = clear). */
    private fun sandDepth(body: BodyState): Double {
        var worst = Double.NEGATIVE_INFINITY
        for (h in 0 until 2 * BodyState.N - 1) {
            val i = h / 2; val j = if (h % 2 == 1) i + 1 else i; val f = if (h % 2 == 1) 0.5 else 0.0
            val p = body.p[i] + (body.p[j] - body.p[i]) * f
            val up = (body.up[i] + (body.up[j] - body.up[i]) * f).normalized(Vec3.Y)
            val side = (body.side[i] + (body.side[j] - body.side[i]) * f).normalized(Vec3.Z)
            val a = body.a[i] + (body.a[j] - body.a[i]) * f
            val b = body.b[i] + (body.b[j] - body.b[i]) * f
            val c = p + up * (body.ctr[i] + (body.ctr[j] - body.ctr[i]) * f)
            for (k in 0 until RIM) {
                val q = c + up * (a * RIM_COS[k]) + side * (b * RIM_SIN[k])
                worst = max(worst, rock.floorY(q.x, q.z) - q.y)
            }
        }
        return worst
    }

    /** Back to the last clear pose (a wedge hold); false when there is none. */
    private fun restoreHeld(o: Organism, count: Boolean = true): Boolean {
        if (!o.spline.restoreSafe()) return false
        o.pos = o.spline.headPos; o.heading = o.body.safeHeading; o.up = o.body.safeUp
        // the same body as when it was saved: the swim wave and bend widen the sections (a wider tail beat in the wedge)
        o.amp = o.body.safeAmp; o.bend = o.body.safeBend
        o.vel = Vec3.ZERO
        if (count) {
            o.body.held++
            wedgeRestores++
            wedgeBy.merge(o.species.id, 1L) { x, y -> x + y }
        }
        return true
    }

    /** Hops the body to the nearest clear spot along or above it; false (and the body left where it was) when none. */
    private fun unstick(o: Organism, rockOn: Boolean, surfaceOn: Boolean): Boolean {
        val L = o.lengthCm
        val dirs = listOf(o.heading, o.heading * -1.0, Vec3.Y, o.up)
        var moved = Vec3.ZERO
        // the axis at head, middle and tail before the hop: a hop must not carry the body through a thin rock wall
        val body = o.body
        body.refresh(o)
        val axis = IntArray(3) { it * (BodyState.N - 1) / 2 }.map { i -> body.p[i] + body.up[i] * body.ctr[i] }
        val axis0 = axis.map { if (rockOn) rock.sdf.dist(it) else 0.0 }
        val start = o.spline.pose()
        val h0 = o.heading
        // (a crawler on its rig of legs or arms only steps out: a hop of a few cm is no walk, and the pose it is held
        // to is clear, so it keeps that until the next step out is)
        val steps = if (o.attached && drawnCentred(o)) 1 else UNSTICK_STEPS
        for (k in 1..steps) {
            val dist = min(L * 0.15, 0.75) * k
            // backing out along its own spine first, the way it came in: the body only passes where it already was
            // (and where the tail carries on straight behind it), so a fish nosed into a slot it cannot turn in backs out
            if (o.spline.override == null) {
                if (moved != Vec3.ZERO) { o.spline.setPose(start); moved = Vec3.ZERO }
                o.spline.backUp(dist)
                // (the heading turns toward the stretch of spine the head backed onto, within a tick's turn: a trail
                // crumpled by grazing or hovering points anywhere there)
                o.pos = o.spline.headPos; o.heading = Vec3.rotateToward(h0, o.spline.headingDir, BACKUP_TURN_RAD)
                body.refresh(o)
                val tail = body.p[BodyState.N - 1] + body.up[BodyState.N - 1] * body.ctr[BodyState.N - 1]
                val ok = !rockOn || !crossesWall(axis.subList(2, 3), axis0.subList(2, 3), tail - axis[2])
                val rd = if (ok && rimWithin(o, rockOn, surfaceOn, 0.0, strict = true)) -1.0 else 1.0
                if (rd < 0.0) {
                    o.vel = Vec3.ZERO
                    o.body.clearPose = true
                    return true
                }
                o.spline.setPose(start); o.pos = o.spline.headPos; o.heading = h0
            }
            for (d in dirs) {
                val t = d * dist
                if (rockOn && crossesWall(axis, axis0, t)) continue
                for (j in 0 until BodySpline.POINTS) deltas[j] = t - moved
                o.spline.displace(deltas)
                o.pos = o.spline.headPos
                moved = t
                if (rimWithin(o, rockOn, surfaceOn, 0.0, strict = true)) {
                    o.vel = Vec3.ZERO
                    o.body.clearPose = true
                    return true
                }
            }
        }
        // nowhere clear nearby: stay where the pushes left the body
        o.spline.setPose(start)
        o.pos = o.spline.headPos; o.heading = h0
        return false
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

    /** What is left of [MAX_TIP_RESOLVE] in the current resolve. */
    private var tipLeft = 0.0

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
        val w = Vec3(wx, wy, wz) / rr
        val ang = w.length()
        if (ang < 1e-4) return
        val axis = w.normalized(Vec3.Y)
        // per iteration and per resolve: the iterations of one tick add up, and the walk re-seats the animal on the
        // face it tipped onto, so a large tip in one tick reads as a twitch (FishGlitchTest TURN_RATE / SPEED_SPIKE)
        val a = min(min(ang, MAX_TIP), tipLeft)
        if (a < 1e-4) return
        tipLeft -= a
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
        pinned.clear()
        overlapping.clear()
        var settled = false
        for (pass in 0 until PAIR_PASSES) if (!pairPass(animals, stations, dt, holdAttached = pass < PAIR_PASSES / 2, refresh = pass == 0)) { settled = true; break }
        // the passes ran out with bodies still being pushed apart: those may still overlap
        if (settled) overlapping.clear()
        commitSafe(animals)
    }

    /**
     * Remembers each body's pose as its clear pose (the one a wedge holds it to) when its last resolve left it clear of
     * the environment and it overlaps no other body: a pose saved while two bodies overlapped would be restored into
     * that overlap again by every hold.
     */
    private fun commitSafe(animals: List<Organism>) {
        for (o in animals) {
            // (a body near the geometry is measured as the no-overlap checks measure it before it becomes the pose
            // every hold returns to: a pose the station pushes called clear but a rim or section centre is still in)
            if (o.body.clearPose && o.alive && o !in overlapping && (o.body.clearBroad || rimWithin(o, o.body.clearRock, true, SKIN))) saveSafe(o)
            o.body.clearPose = false
        }
    }
    /** Bodies the last pair pass pushed (still overlapping when the passes ran out). */
    private val overlapping = HashSet<Organism>()

    /** Bodies the environment pushed back against their pair push this tick (against the rock, the equipment): they yield no more. */
    private val pinned = HashSet<Organism>()

    private fun pairPass(animals: List<Organism>, stations: List<com.reeflive.core.ai.CleaningStation>, dt: Double, holdAttached: Boolean, refresh: Boolean = true): Boolean {
        val live = ArrayList<Organism>(animals.size)
        // (a body coming out of a burrow is let into the rock and sand, not into another animal)
        for (o in animals) if (o.alive && (!exempt(o) || o.body.emerging > 0 && o.posture != 3 && o.posture != 5 && o.spline.override == null && o.visible)) {
            // (after the first pass every body is as its last refresh left it: a pushed one was refreshed after its resolve)
            if (refresh) o.body.refresh(o)
            // a body that is not finite or absurdly large (a broken trail) is skipped rather than hashed
            if (o.body.centre.isFinite() && o.body.radius < 4 * o.lengthCm + 20) live += o
        }
        if (live.size < 2) return false
        hash.rebuild(live)
        var maxR = 0.0
        for (o in live) maxR = max(maxR, o.body.radius)
        pairDelta.clear()
        pairWeight.clear()
        climbers.clear()
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
                if (seatOf(a) != null && seatOf(b) != null) { climbers += a; climbers += b }
            }
        }
        if (pairDelta.isEmpty()) return false
        overlapping.clear()
        for ((o, d) in pairDelta) {
            // every segment pair that overlaps pushes the same stations: their mean, not their sum, takes the overlap
            // out (a sum pushes a body lying alongside another many times too far, and it springs back next tick)
            val w = pairWeight[o]
            if (w != null) for (q in d.indices) if (w[q] > 1.0) d[q] = d[q] / w[q]
            overlapping += o
            pairPushes++
            val before = o.body.centre
            val seat = seatOf(o)
            if (seat != null) {
                // a seated crawler: its drawn body (the seat) takes the push, the sim position stays (see resolveEnv);
                // it slides along the surface it holds (what the push loses there is the other body's to take, below),
                // except off another seated crawler, onto whose shell it may climb
                carrySeat(o, seat, d[0], slide = o !in climbers)
            } else {
                o.spline.displace(d)
                o.pos = o.spline.headPos
            }
            resolveEnv(o, dt)
            // the environment took back most of the push (the body is against the rock or the equipment on that side): from
            // now on the other body of the pair moves instead, or the two would trade the same overlap pass after pass
            // (measured at the body's middle against the mean push: a push on the tail barely moves the head)
            var wx = 0.0; var wy = 0.0; var wz = 0.0
            for (v in d) { wx += v.x; wy += v.y; wz += v.z }
            val want = Vec3(wx, wy, wz) / d.size.toDouble()
            o.body.refresh(o)
            if (want.lengthSq() > 1e-6 && ((o.body.centre - before) dot want) < 0.5 * want.lengthSq()) pinned += o
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

    private val segMid = Array(BodyState.N - 1) { Vec3.ZERO }
    private val segReach = DoubleArray(BodyState.N - 1)

    private fun pair(a: Organism, b: Organism, holdAttached: Boolean) {
        val A = a.body; val B = b.body
        val (wa, wb) = weights(a, b, holdAttached)
        // (B's segment middles and reaches, once for the pair rather than once per segment of A)
        for (j in 0 until BodyState.N - 1) {
            segMid[j] = (B.p[j] + B.p[j + 1]) * 0.5
            segReach[j] = B.p[j].distanceTo(B.p[j + 1]) * 0.5 + max(abs(B.ctr[j]) + max(B.a[j], B.b[j]), abs(B.ctr[j + 1]) + max(B.a[j + 1], B.b[j + 1]))
        }
        for (i in 0 until BodyState.N - 1) {
            val ma = (A.p[i] + A.p[i + 1]) * 0.5
            val ha = A.p[i].distanceTo(A.p[i + 1]) * 0.5 + max(abs(A.ctr[i]) + max(A.a[i], A.b[i]), abs(A.ctr[i + 1]) + max(A.a[i + 1], A.b[i + 1]))
            if (ma.distanceTo(B.centre) > ha + B.radius) continue
            for (j in 0 until BodyState.N - 1) {
                val mb = segMid[j]
                val hb = segReach[j]
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
        // a body the environment is holding (wedged against the rock, [BodyState.held]) cannot yield: pushed, it is put
        // back where it fitted, into the other body again; the other one takes the whole push
        val ma = if (a.attached && holdAttached || a.body.held > 0 || a in pinned) 1e6 else a.lengthCm * a.lengthCm * a.lengthCm
        val mb = if (b.attached && holdAttached || b.body.held > 0 || b in pinned) 1e6 else b.lengthCm * b.lengthCm * b.lengthCm
        return (mb / (ma + mb)) to (ma / (ma + mb))
    }

    private fun addDelta(o: Organism, body: BodyState, i0: Int, i1: Int, t: Double, d: Vec3) {
        val arr = pairDelta.getOrPut(o) { Array(BodySpline.POINTS) { Vec3.ZERO } }
        val wt = pairWeight.getOrPut(o) { DoubleArray(BodySpline.POINTS) }
        if (o.spline.rigid || seatOf(o) != null) { for (k in arr.indices) { arr[k] = arr[k] + d; wt[k] += 1.0 }; return }
        // body station → spline station (u = i/15), spread over a few stations so the push bends the spine smoothly
        val f = ((body.u[i0] + (body.u[i1] - body.u[i0]) * t) * (BodySpline.POINTS - 1)).coerceIn(0.0, BodySpline.POINTS - 1.0)
        for (q in 0 until BodySpline.POINTS) {
            val fall = (1 - abs(q - f) / 3.0).coerceAtLeast(0.0)
            if (fall > 0) { arr[q] = arr[q] + d * fall; wt[q] += fall }
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

    /**
     * Deepest point of [o]'s body sections (every station and halfway, rim and centre) in the rock, sand, glass,
     * surface or a static obstacle (cm, ≤ 0 = clear): what the no-overlap checks measure. A burrower leaving its hole
     * keeps its burrow posture until this reads clear.
     */
    fun sectionDepth(o: Organism, rockOn: Boolean = true): Double = rimDepth(o, rockOn, true)

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
        /**
         * Resolves a body leaving a burrow or the sand may take to come clear of it (several a tick: ≈ 4-10 s at 20 Hz;
         * at 40 a diamond goby's tail was still in its burrow when the hold ran out, and popped out in one tick).
         */
        const val EMERGE_TICKS = 200
        const val ITERATIONS = 6
        private val DOWN = Vec3(0.0, -1.0, 0.0)
        private const val RIM = 12
        /** A push still this large after the last iteration (the skin plus 0.3 mm of overlap) means the body is wedged (cm). */
        const val WEDGE_LIMIT = 0.08
        private const val UNSTICK_STEPS = 8
        /** Resolves in a row a wedged body is held before it backs out to clear water instead (~1 s at 20 Hz). */
        private const val HOLD_MAX = 20
        /** Largest tip of a rigid body per resolve iteration (rad). */
        private const val MAX_TIP = 0.15
        /**
         * Largest tip of a slow rigid body over one resolve, all iterations (rad; 0.06 rad a tick is 69 deg/s at 20 Hz);
         * a faster one may tip by the arc it travels over a quarter of its length.
         */
        private const val MAX_TIP_RESOLVE = 0.06
        private val RIM_COS = DoubleArray(RIM) { kotlin.math.cos(it * 2 * Math.PI / RIM) }
        private val RIM_SIN = DoubleArray(RIM) { kotlin.math.sin(it * 2 * Math.PI / RIM) }
        /** How far a station may move before its sand checks that the body's sand bound covers (cm). */
        private const val SAND_MARGIN = 1.0
        /** How far a station may move before its obstacle push that the body's obstacle check covers (cm). */
        private const val OB_MARGIN = 1.0
        /** Longest chord between neighbouring rim points, per unit [reach] (2 sin 15°, rounded up). */
        private const val ARC_CHORD = 0.5177
        const val MAX_ITERATIONS = 12
        /** Iterations that bend the spine before the remaining ones translate the body. */
        const val BEND_ITERATIONS = 3
        /** Animal-vs-animal passes per tick (each followed by the environment resolve of the bodies it moved). */
        const val PAIR_PASSES = 10
        /**
         * How much faster than 1 cm per cm the baked rock distance can change (the trilinear blend of a 1 cm float16
         * grid: up to ~1.5 measured across the reef). A reading of d promises only d / SDF_LIP cm of clear water, so
         * every skip (broad phase, a station far from the rock, the rim checks) uses that; at 1.0 a fin edge the
         * centre read as clear sat 3 mm in the rock.
         */
        const val SDF_LIP = 1.75
        /** A mound beside the body's middle can stand this much above the sand under it (cm): the sand re-push's bounds. */
        const val MAX_MOUND_STEP_CM = 3.0
        /** How much higher the sand under a section's rim can be than under its centre (a mound, a pile's flank) (cm). */
        const val SAND_SLOPE_CM = 1.0
        /** How long a body's middle reads sealed in before it starts to pass the rock to get out (s). */
        const val SEALED_DELAY_S = 1.0
        /** Contact gap the resolve leaves (cm): a body comes to rest 0.5 mm off the surface. */
        const val SKIN = 0.05
        /** Sand slope allowance (cm) of the broad phase: the bed under the body may rise this much between head, middle and tail. */
        const val BROAD_FLOOR_MARGIN = 1.5
        /** How fast a resting fish's body settles onto the slope under its belly (1/s). */
        const val SETTLE_RATE = 4.0
        /** Largest move of a seated crawler's drawn body by one resolve (cm, [carrySeat]): a hop is the seat's to make. */
        const val SEAT_CARRY_CM = 1.5
        /** Largest heading change of a back-out along the spine ([unstick]), rad. */
        const val BACKUP_TURN_RAD = 0.3
        /** Steepest slope (sin of pitch) a resting fish's body follows. */
        const val SETTLE_MAX_PITCH = 0.57

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

