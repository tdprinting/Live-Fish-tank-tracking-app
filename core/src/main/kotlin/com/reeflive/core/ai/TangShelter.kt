package com.reeflive.core.ai

import com.reeflive.core.geom.Sdf
import com.reeflive.core.geom.Vec3
import com.reeflive.core.geom.smoothstep
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Blue tang shelter, play and nerves (Tee 2026-10-15: "blue hippo tangs like to hide under rock or coral when they are
 * sleeping or scared or nervous", "sometimes … rub up against rocks and race across the tank really fast. they are
 * playful fish"; research video fish_blue_tang.md).
 *
 * P. hepatus lives around coral heads and retreats into them when frightened, and wedges into a crevice to sleep
 * (FishBase species summary [M]; [S3][M]). Here the shelters are real overhangs of the rockwork: points with rock
 * close above and on most sides, found once by scanning the rock SDF. Every pose is checked against the rock at the
 * tang's nose, tail, fin tips and flanks, so a wedged or rubbing tang touches the rock without passing into it.
 */
class TangShelters(
    private val c: Ctx,
    /** Body half-extents as fractions of total length (defaults: the P. hepatus disc; [forSpecies] for the others). */
    val halfLen: Double = HALF_LEN,
    val halfDepth: Double = HALF_DEPTH,
    val halfThick: Double = HALF_THICK,
) {
    /** A pose that keeps the whole body out of the rock: centre, heading, up, and the rock's outward normal. */
    data class Pose(val pos: Vec3, val heading: Vec3, val up: Vec3, val normal: Vec3, val entry: Vec3)

    /** A shelter: a point under rock and how enclosed it is (0..1). */
    data class Spot(val pos: Vec3, val cover: Double)

    /**
     * The shelters of the rockwork as it stands. My tank's rocks shift when dug under and can be rebuilt, so the scan is
     * redone once the rocks have moved and come to rest again (at most every [RESCAN_S]); [generation] counts rescans.
     */
    val spots: List<Spot> get() {
        val rp = c.sim.rockPhysics
        val v = rp?.version ?: 0L
        val cur = scanned
        if (cur == null || v != scannedVersion && !rp!!.anyAwake && c.t - scannedAt >= RESCAN_S) {
            scannedVersion = v; scannedAt = c.t
            if (cur != null) generation++
            return sharedScan(v).also { scanned = it }
        }
        return cur
    }
    private var scanned: List<Spot>? = null
    private var scannedVersion = Long.MIN_VALUE
    private var scannedAt = -1e9
    /** Bumped each time the rocks moved and the shelters were found again (the bed is refitted then). */
    var generation = 0; private set

    /**
     * The scan does not depend on the body, so every fish of one sim shares it (two tangs and a harem of fairy wrasses
     * would otherwise each walk the whole rock SDF at load): one slot, keyed by the sim and its rock version.
     */
    private fun sharedScan(version: Long): List<Spot> {
        val k = lastScan
        if (k != null && k.sim === c.sim && k.rock === c.rock && k.version == version) return k.spots
        return scan().also { lastScan = ScanKey(c.sim, c.rock, version, it) }
    }

    private fun scan(): List<Spot> {
        val rock = c.rock
        val sdf = rock.sdf
        val out = ArrayList<Spot>()
        val step = SCAN_STEP
        var x = 3.0
        while (x < rock.lengthCm - 3) {
            var z = 3.0
            while (z < rock.widthCm - 3) {
                var y = rock.sandTopCm + 2.0
                while (y < rock.waterLevelCm - 6) {
                    val p = Vec3(x, y, z)
                    val d = sdf.dist(p)
                    if (d in 0.8..4.5 && rock.inWater(p, 1.0) && y > c.sim.sand.surfaceY(x, z) + 1.5) {
                        // a roof: rock straight above, close enough to read as "under" it
                        val roof = sdf.raycast(p, Vec3.Y, ROOF_CM)
                        if (roof != null) {
                            val cov = sdf.cover(p)
                            if (cov >= MIN_COVER) out += Spot(p, cov + 0.3 * (1 - roof / ROOF_CM))
                        }
                    }
                    y += step
                }
                z += step
            }
            x += step
        }
        // best first, spread out so the list is a set of distinct places, not one cave counted many times
        out.sortByDescending { it.cover }
        val kept = ArrayList<Spot>()
        for (s in out) if (kept.none { it.pos.distanceTo(s.pos) < SPREAD_CM }) kept += s
        return kept
    }

    /** How far above the body centre (cm) the rock may be and still be a roof over its back: half a body length above the dorsal tip. */
    fun roofMax(L: Double) = min(ROOF_CM, (halfDepth + ROOF_ABOVE) * L)

    /**
     * How tucked in a fish posed at [p] is (0 out in the open .. 1 walled in): the share of short rays from its flanks,
     * back, nose and tail that meet rock within a body length, plus a roof close over its back. A spot that merely
     * lies in a rock's shadow scores low; a crevice or the hollow under a ledge scores high.
     */
    fun snug(p: Vec3, L: Double): Double {
        val sdf = c.rock.sdf
        var hits = 0
        for (d in Sdf.COVER_DIRS) if (d.y > -0.5 && sdf.raycast(p, d, SNUG_REACH * L) != null) hits++
        val roofAt = sdf.raycast(p, Vec3.Y, ROOF_CM)
        val roofScore = if (roofAt == null) 0.0 else 1.0 - smoothstep(halfDepth * L, roofMax(L) + 0.5 * L, roofAt)
        return 0.7 * hits / UPPER_DIRS + 0.3 * roofScore
    }

    /** Body sample points (nose, tail, fin tips, flanks) of a tang of length [L] posed at [p] facing [h] with [up]. */
    fun clear(p: Vec3, h: Vec3, up: Vec3, L: Double, margin: Double = 0.05): Boolean {
        val sdf = c.rock.sdf
        val side = (h cross up).normalized(Vec3.Z)
        val a = halfLen * L; val b = halfDepth * L; val t = halfThick * L
        val pts = arrayOf(
            p, p + h * a, p - h * a, p + up * b, p - up * b, p + side * t, p - side * t,
            p + h * (0.6 * a) + up * (0.6 * b), p + h * (0.6 * a) - up * (0.6 * b),
            p - h * (0.6 * a) + up * (0.6 * b), p - h * (0.6 * a) - up * (0.6 * b),
        )
        for (q in pts) {
            if (sdf.dist(q) < margin) return false
            if (!c.rock.inWater(q, 0.2) || q.y < c.sim.sand.surfaceY(q.x, q.z) + 0.1) return false
        }
        return true
    }

    /**
     * Fits a tang of length [L] snugly against the rock at [near], rolled [roll] rad toward the rock: heading along the
     * rock face, as close in as the body allows (never closer than [minGap] of body thickness off it). Null when no pose
     * there keeps the body out of the rock.
     */
    fun fit(near: Vec3, L: Double, roll: Double, minGap: Double = 0.15, roof: Boolean = false): Pose? {
        val sdf = c.rock.sdf
        val surf = sdf.project(near, 0.0)
        if (abs(sdf.dist(surf)) > 0.3) return null
        val n = sdf.gradient(surf).normalized(Vec3.Y)
        // heading along the face, level: across the rock's normal (a horizontal-ish face gives an arbitrary course)
        var h0 = (n cross Vec3.Y).horizontal()
        if (h0.length() < 0.2) h0 = Vec3.X
        h0 = h0.normalized(Vec3.X)
        if ((h0 dot c.o.heading) < 0) h0 = h0 * -1.0
        // a cave is rarely straight along the nearest face: try turning into it a little either way
        for (turn in TURNS) {
            val h = if (turn == 0.0) h0 else Vec3.rotateAbout(h0, Vec3.Y, turn)
            val up = rolledUpToward(h, n, roll)
            var d = halfThick * L + minGap
            val hi = halfDepth * L + 2.5
            while (d <= hi) {
                val p = surf + n * d
                if ((!roof || sdf.raycast(p, Vec3.Y, roofMax(L)) != null) && clear(p, h, up, L)) {
                    entryFrom(p, n, h, up, L)?.let { return Pose(p, h, up, n, it) }
                }
                d += 0.25
            }
        }
        return null
    }

    /**
     * The open-water doorway of a pose: walking straight out along the rock normal from [p], every step clear of rock
     * for the whole body, to a point with room to turn. Null when the way out is blocked (the spot can't be reached).
     */
    private fun entryFrom(p: Vec3, n: Vec3, h: Vec3, up: Vec3, L: Double): Vec3? {
        var d = 1.0
        while (d <= ENTRY_MAX_CM) {
            val q = p + n * d
            if (!clear(q, h, up, L)) return null
            // room to turn in any direction without touching rock (the body's half length is free all round)
            if (c.rock.sdf.dist(q) >= halfLen * L + 0.5 && c.rock.inWater(q, halfLen * L) &&
                q.y > c.sim.sand.surfaceY(q.x, q.z) + halfLen * L) return q
            d += 1.0
        }
        return null
    }

    /** [p] moved by [by] (same attitude), if the body is clear there and it has its own way out; else null. */
    fun shifted(p: Pose, by: Vec3, L: Double): Pose? {
        val q = p.pos + by
        if (!clear(q, p.heading, p.up, L)) return null
        val e = entryFrom(q, p.normal, p.heading, p.up, L) ?: return null
        return Pose(q, p.heading, p.up, p.normal, e)
    }

    /** True if the body stays clear at every point of the straight move [a]→[b] in attitude [h]/[up]. */
    fun pathClear(a: Vec3, b: Vec3, h: Vec3, up: Vec3, L: Double): Boolean {
        val n = max(2, (a.distanceTo(b) / 0.5).toInt())
        for (i in 0..n) if (!clear(a.lerp(b, i.toDouble() / n), h, up, L)) return false
        return true
    }

    /** Up vector of a fish facing [h], rolled [angle] rad so its back leans toward the rock with outward normal [n]. */
    fun rolledUpToward(h: Vec3, n: Vec3, angle: Double): Vec3 {
        val up0 = Vec3.Y.rejectFrom(h).normalized(Vec3.Y)
        val side = (h cross up0).normalized(Vec3.Z)
        // roll the back toward the rock: +side when the rock is on the +side, else −side
        val sign = if ((side dot n) < 0) 1.0 else -1.0
        return Vec3.rotateAbout(up0, h, sign * angle)
    }

    /**
     * The best shelter for a tang at [from] fleeing [threat] (or none): enclosed spots near it, away from the threat,
     * that fit its body. Tries the [tries] best-scoring candidates.
     */
    fun pick(from: Vec3, threat: Vec3?, L: Double, roll: Double, prefer: Vec3? = null, tries: Int = 8, snuggest: Boolean = false, avoid: List<Vec3> = emptyList()): Pose? {
        // never a refuge right by what scared it (a crevice under the clowns' corner, fleeing the clowns): it would be
        // displayed at again as soon as it got there and stay scared for the rest of the hide), nor on a territorial
        // neighbour's doorstep (the clowns' host, the dottyback's cave): hidden there it was displayed at and chased
        // again and again, its fear never eased and it never came out (TangHideTest, SIMULATION §24)
        val doors = residentDoors()
        val away = spots.filter { s ->
            (threat == null || s.pos.distanceTo(threat) >= THREAT_CLEAR_CM) && doors.none { s.pos.distanceTo(it) < THREAT_CLEAR_CM }
        }.ifEmpty { spots }.filter { s -> avoid.none { s.pos.distanceTo(it) < SPREAD_CM } }
        val ranked = away.sortedByDescending { s ->
            var v = s.cover - 0.012 * s.pos.distanceTo(from)
            if (threat != null) v += 0.01 * min(40.0, s.pos.distanceTo(threat))
            if (prefer != null) v -= 0.02 * s.pos.distanceTo(prefer)
            v
        }
        if (!snuggest) {
            for (s in ranked.take(tries)) fit(s.pos, L, roll, roof = true)?.let { return it }
            return null
        }
        // a bed: of the best-placed fits, the one most walled in (it sleeps wedged in, not just in a rock's shadow)
        var best: Pose? = null; var bestV = -1.0
        for (s in ranked.take(tries)) {
            val q = fit(s.pos, L, roll, roof = true) ?: continue
            val v = snug(q.pos, L) - 0.004 * q.pos.distanceTo(prefer ?: from)
            if (v > bestV) { bestV = v; best = q }
        }
        return best
    }

    /** The homes territorial neighbours defend: each clown's host, each dottyback's cave. */
    private fun residentDoors(): List<Vec3> = c.sim.organisms.mapNotNull { o ->
        if (!o.alive || o === c.o) return@mapNotNull null
        val a = c.sim.agentOf(o) ?: return@mapNotNull null
        when (val br = a.brain) {
            is ClownBrain -> br.host(a.ctx)
            is DottybackBrain -> br.shelterSpot(a.ctx)
            else -> null
        }
    }

    private class ScanKey(val sim: Any, val rock: Any, val version: Long, val spots: List<Spot>)

    companion object {
        private var lastScan: ScanKey? = null

        /** Shelters fitted to [o]'s body: the slender fairy wrasse and the tall-finned sailfin tangs differ from the blue tang's disc. */
        fun forSpecies(c: Ctx): TangShelters = when {
            c.o.brainKind == com.reeflive.core.sim.BrainKind.FAIRY_WRASSE -> TangShelters(c, 0.48, 0.14, 0.07)
            c.o.species.zebrasoma -> TangShelters(c, 0.47, 0.31, 0.075)
            else -> TangShelters(c)
        }

        /** Body half-extents as fractions of total length: nose/tail, dorsal/anal fin tips, flank (P. hepatus disc) [L]. */
        const val HALF_LEN = 0.48
        const val HALF_DEPTH = 0.27
        const val HALF_THICK = 0.08
        const val SCAN_STEP = 2.5
        const val ROOF_CM = 14.0
        const val MIN_COVER = 0.5
        const val SPREAD_CM = 6.0
        /** A shelter this close (cm) to the scare it flees, or to a territorial neighbour's home, is passed over while there is another. */
        const val THREAT_CLEAR_CM = 15.0
        const val ENTRY_MAX_CM = 24.0
        /** Roof over the back: at most this many body lengths above the dorsal tip [L]. */
        const val ROOF_ABOVE = 0.5
        /** Reach of the snugness rays, body lengths, and how many of [Sdf.COVER_DIRS] are not steeply down. */
        const val SNUG_REACH = 1.0
        val UPPER_DIRS = Sdf.COVER_DIRS.count { it.y > -0.5 }.toDouble()
        /** Shortest gap (s) between shelter rescans after the rocks moved. */
        const val RESCAN_S = 30.0
        private val TURNS = doubleArrayOf(0.0, 0.35, -0.35, 0.7, -0.7)
    }
}

/**
 * Gets a tang into a fitted [TangShelters.Pose] without touching rock on the way: swim to its open-water doorway, turn
 * and roll to the pose's attitude there (room to turn all round), then glide straight in along the path [TangShelters]
 * cleared for exactly that attitude. [leave] reverses it.
 */
class Dock {
    private var phase = 0
    private var k = 0.0
    val home: Boolean get() = phase == 3
    /** Between the doorway and the pose (gliding in, settled or gliding out): must not be cut off mid-way. */
    val inside: Boolean get() = phase in 2..4
    fun reset() { phase = 0; k = 0.0 }
    /** Already in the pose (it got there some other cleared way). */
    fun setHome() { phase = 3; k = 1.0 }

    /** Moves toward [p]; true once in place. [at] overrides the spot on the cleared doorway→pose line once home. */
    fun drive(c: Ctx, out: SteerIntent, p: TangShelters.Pose, speed: Double, rate: Double = 6.0): Boolean {
        val o = c.o
        when (phase) {
            0 -> {
                out.seek(c.viaOpenWater(p.entry), speed, arrive = 1.5, urgency = 0.6)
                out.hover = true
                if (o.pos.distanceTo(p.entry) < 1.5) phase = 1
            }
            1 -> {
                out.place(p.entry, p.heading, p.up, 3.0)
                if ((o.heading dot p.heading) > 0.985 && (o.up dot p.up) > 0.975 && o.pos.distanceTo(p.entry) < 0.5) phase = 2
            }
            2 -> {
                k = min(1.0, k + c.dt * GLIDE_CMS / max(1.0, p.entry.distanceTo(p.pos)))
                out.place(p.entry.lerp(p.pos, k), p.heading, p.up, rate)
                if (k >= 1.0) phase = 3
            }
            3 -> return true
        }
        return phase == 3
    }

    /** Glides back out to the doorway along the same line; true once out (then free to swim off). */
    fun leave(c: Ctx, out: SteerIntent, p: TangShelters.Pose): Boolean {
        if (phase < 2) { phase = 5; return true }
        if (phase != 4) { phase = 4; k = 1.0 }
        k = max(0.0, k - c.dt * GLIDE_CMS / max(1.0, p.entry.distanceTo(p.pos)))
        out.place(p.entry.lerp(p.pos, k), p.heading, p.up, 6.0)
        if (k <= 0.0 && c.o.pos.distanceTo(p.entry) < 0.5) { phase = 5; return true }
        return false
    }

    companion object {
        /** Glide speed in and out of a crevice, cm/s [L]. */
        const val GLIDE_CMS = 6.0
    }
}

/**
 * Nervous or scared (after a startle, a threat, high fear or stress): tucks under the nearest overhang away from the
 * scare, rolled ~35° with its back to the rock and a little pale, and stays put for 1-4 min, now and then easing its
 * head out to look before tucking back in. It comes out once its fear has eased.
 */
class TangHide(val b: TangBrain) : Behavior("tanghide", "Hiding under the rock") {
    private var pose: TangShelters.Pose? = null
    private var hold = 90.0
    private var peekAt = 0.0
    private var peekUntil = -1.0
    private val dock = Dock()
    private var leaving = false

    private var doneAfter = -1e9
    private var retryAt = -1e9

    private fun nervous(c: Ctx) = max(b.mind.mood.fear, c.o.stress)

    override fun score(c: Ctx): Double {
        val o = c.o
        if (c.p.night || c.t < retryAt) return 0.0
        val n0 = nervous(c)
        // came out after this scare and has calmed down: stays out until something new scares it
        if (doneAfter >= b.scaredAt && n0 < NERVOUS) return 0.0
        val sinceScare = c.t - b.scaredAt
        val n = n0
        val scared = sinceScare < SCARE_WINDOW_S * (0.5 + o.skittishness)
        if (!scared && n < NERVOUS) return 0.0
        return (0.62 + 0.3 * max(n, if (scared) 0.5 else 0.0)).coerceAtMost(0.9)
    }

    override fun enter(c: Ctx) {
        val o = c.o
        pose = b.shelters.pick(o.pos, b.scareFrom.takeIf { c.t - b.scaredAt < 60 }, o.lengthCm, HIDE_ROLL)
        hold = c.rng.range(60.0, 240.0) * (0.5 + o.skittishness)
        peekAt = c.t + c.rng.range(8.0, 20.0)
        peekUntil = -1.0
        dock.reset(); leaving = false
        if (pose == null) { finished = true; retryAt = c.t + 20 }
    }

    override fun update(c: Ctx, out: SteerIntent) {
        val o = c.o
        val p = pose ?: run { finished = true; return }
        o.desat = max(o.desat, 0.15)
        if (leaving) {
            if (dock.leave(c, out, p)) { finished = true; doneAfter = c.t; hides++ }
            return
        }
        if (!dock.drive(c, out, p, o.burstSpeed * 0.5)) {
            if (elapsed > 60 && !dock.home) finished = true
            return
        }
        // a peek: the head eases out from under the rock along the cleared path, then back in
        var at = p.pos
        if (c.t >= peekAt && peekUntil < 0) peekUntil = c.t + c.rng.range(2.0, 4.0)
        if (peekUntil > 0) {
            at = p.pos.lerp(p.entry, min(1.0, 0.25 * o.lengthCm / max(1.0, p.entry.distanceTo(p.pos))))
            if (c.t > peekUntil) { peekUntil = -1.0; peekAt = c.t + c.rng.range(10.0, 25.0) }
        }
        out.place(at, p.heading, p.up, 2.0)
        out.pectoral = 0.5; out.ampMul = 0.3
        if (elapsed > hold && nervous(c) < CALM && c.t - b.scaredAt > 30) leaving = true
    }

    /** Keeps the glide out from being cut short by whatever it wants to do next. */
    override val committed: Boolean get() = leaving || dock.inside

    /** Hides finished so far (tests). */
    var hides = 0; private set

    /** Where it is hiding (tests), null when not. */
    val hidePose: TangShelters.Pose? get() = pose
    override fun exit(c: Ctx) {
        pose = null
    }

    companion object {
        /** Seconds after a scare that it wants to hide (scaled by skittishness 0.5-1.5×) [L]. */
        const val SCARE_WINDOW_S = 150.0
        /** Fear or stress at which it hides without a fresh scare, and below which it comes out [L]. */
        const val NERVOUS = 0.35
        const val CALM = 0.25
        /** Roll while hiding (rad, ~35°): half way to the night side-rest [L]. */
        const val HIDE_ROLL = 0.6
    }
}

/**
 * Play: now and then by day a calm, healthy tang swoops to a steep rock face, rolls a little toward it and slides its
 * flank along the rock for a second, then pushes off (Tee's own tank; healthy tangs "flash" now and then as well as
 * when itchy, research 06 §5.6 #12 counts only the itchy flashes). The flank touches the rock, nothing goes into it.
 */
class TangRub(val b: TangBrain) : Behavior("tangrub", "Rubbing on the rock") {
    /** Sim time of the next rub (tests may bring it forward). */
    internal var nextAt = -1.0
    private var start: TangShelters.Pose? = null
    private var end: TangShelters.Pose? = null
    private val dockIn = Dock()
    private val dockOut = Dock()
    private var slideAt = -1.0

    override val committed: Boolean get() = dockIn.inside || slideAt >= 0

    override fun score(c: Ctx): Double {
        val o = c.o
        if (b.utility.current === this) return 0.75
        if (nextAt < 0) nextAt = c.t + c.rng.range(RUB_MIN_S, RUB_MAX_S)
        if (c.t < nextAt || c.activity < 0.6) return 0.0
        if (o.disease != null || o.health < 0.7 || b.mind.mood.fear > 0.2 || o.stress > 0.3 || o.hunger > 0.6) return 0.0
        return 0.75
    }

    override fun enter(c: Ctx) {
        val o = c.o
        val L = o.lengthCm
        nextAt = c.t + 20   // no face fits here: try again shortly, somewhere else
        start = null; end = null; slideAt = -1.0
        dockIn.reset(); dockOut.reset()
        for (i in 0 until 8) {
            val sp = c.rockSurfacePoint(o.pos, 30.0, minNormalY = -0.3) ?: continue
            if (abs(sp.second.y) > 0.6) continue   // a steep face, not a ledge top
            val a = b.shelters.fit(sp.first + sp.second * 0.5, L, RUB_ROLL, minGap = 0.1) ?: continue
            // the slide along the face and its way out at the far end must be clear for the whole body too
            val e = b.shelters.shifted(a, a.heading * SLIDE_CM, L) ?: continue
            if (!b.shelters.pathClear(a.pos, e.pos, a.heading, a.up, L)) continue
            start = a; end = e
            nextAt = c.t + c.rng.range(RUB_MIN_S, RUB_MAX_S)
            break
        }
        if (start == null) finished = true
    }

    override fun update(c: Ctx, out: SteerIntent) {
        val o = c.o
        val a = start ?: run { finished = true; return }
        val e = end ?: run { finished = true; return }
        if (slideAt < 0) {
            if (dockIn.drive(c, out, a, o.burstSpeed * 0.6, rate = 8.0)) slideAt = c.t
            else if (elapsed > 15 && !dockIn.inside) finished = true
            return
        }
        val since = c.t - slideAt
        if (since <= SLIDE_S + 0.2) {
            // the slide: along the face with the flank on the rock, a quick wiggle, ~1 s
            out.place(a.pos.lerp(e.pos, smoothstep(0.0, SLIDE_S, since)), a.heading, a.up, 10.0)
            out.tailMul = 1.6; out.ampMul = 1.4
            if (since > SLIDE_S) dockOut.setHome()
            return
        }
        // push off: straight out from the far end of the rub
        if (dockOut.leave(c, out, e)) { finished = true; rubs++; slideAt = -1.0 }
    }

    /** Rubs so far (tests). */
    var rubs = 0; private set

    companion object {
        /** Gap between rubs, s (one every ~8-25 min by day) [L]. */
        const val RUB_MIN_S = 480.0
        const val RUB_MAX_S = 1500.0
        const val RUB_ROLL = 0.45
        const val SLIDE_CM = 5.0
        const val SLIDE_S = 1.0
    }
}

/**
 * Play: a sudden dash at near burst speed across the length of the tank along the open front lane, sometimes straight
 * back again (Tee: "race across the tank really fast"). Calm, healthy tangs only, by day, every ~6-20 min [L].
 */
class TangZoom(val b: TangBrain) : Behavior("tangzoom", "Racing across the tank") {
    /** Sim time of the next dash (tests may bring it forward). */
    internal var nextAt = -1.0
    private var wp = Vec3.ZERO
    private var legs = 1

    override fun score(c: Ctx): Double {
        val o = c.o
        if (b.utility.current === this) return 0.75
        if (nextAt < 0) nextAt = c.t + c.rng.range(ZOOM_MIN_S, ZOOM_MAX_S)
        if (c.t < nextAt || c.activity < 0.6) return 0.0
        if (o.disease != null || o.health < 0.7 || b.mind.mood.fear > 0.3 || o.hunger > 0.7) return 0.0
        return 0.75
    }

    override fun enter(c: Ctx) {
        nextAt = c.t + c.rng.range(ZOOM_MIN_S, ZOOM_MAX_S)
        legs = if (c.rng.chance(0.3)) 2 else 1
        pick(c)
    }

    private fun pick(c: Ctx) {
        val r = c.rock
        val toRight = c.o.pos.x < r.lengthCm / 2
        val x = if (toRight) c.rng.range(r.lengthCm - 22.0, r.lengthCm - 10.0) else c.rng.range(10.0, 22.0)
        wp = c.safe(Vec3(x, c.zoneY(c.rng.range(0.45, 0.8)), r.widthCm * c.rng.range(0.6, 0.8)))
    }

    override fun update(c: Ctx, out: SteerIntent) {
        val o = c.o
        out.seek(c.viaOpenWater(wp), o.burstSpeed * 0.9, arrive = 3.0, urgency = 0.9)
        out.tailMul = 1.5
        if (o.pos.distanceTo(wp) < 2 * c.L) {
            if (--legs > 0) pick(c) else { finished = true; zooms++ }
        }
        if (elapsed > 8) finished = true
    }

    /** Dashes so far (tests). */
    var zooms = 0; private set

    companion object {
        const val ZOOM_MIN_S = 360.0
        const val ZOOM_MAX_S = 1200.0
    }
}
