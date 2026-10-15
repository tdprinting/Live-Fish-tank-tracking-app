package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.Anchor
import com.reeflive.core.sim.Burrow
import com.reeflive.core.sim.Organism
import com.reeflive.core.sim.SandBed
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Engineer goby, Pholidichthys leucotaenia (juvenile, black/yellow stripes). A shy, peaceful
 * burrower that lives under the rock: it excavates sand from beneath the rock and spits the
 * mouthfuls out as small piles near the burrow mouth. By day juveniles hover in a loose group
 * near the mouth picking plankton; at night they withdraw deep into the tunnels. Anguilliform
 * swimmer (same spline deformation as an eel). Never a predator (SIMULATION §4.5, owner correction).
 *
 * Tunnels are real sand (SIMULATION §16.8): the goby keeps a primary burrow under the rock at its anchor and up to
 * [EXTRA_BURROWS] more at nearby rock edges ([SandBed.burrowSites]). It only goes under the rock as far as the gap
 * between the sand and the rock underside clears its body ([SandBed.reach]); beyond that it digs at the face head
 * first, backs out with the mouthful and spits it on the spoil pile at the entrance. Sand trickles back in over days
 * ([SandBed.relax]), so the burrows need maintenance.
 */
class GobyBrain(c: Ctx) : SpeciesBrain(c) {
    companion object {
        /** Adult mode from this length (06 §1.5): burrow-bound by day and night (07 gap 3). */
        const val ADULT_CM = 15.0
        /** Mouthful of a 10 cm goby (cm³); research sand_bioturbation §2: each load ~0.5–2 cm³ [L]. Scales with gape² ([mouthfulCm3]). */
        const val GOBY_MOUTHFUL_CM3 = 0.6
        /** Chance an adult patrols outside on a given night: [I] ≈5 % (07 gap 3; 06 had 40 %, unsupported). */
        const val ADULT_NIGHT_PATROL_P = 0.05
        /** Peeking head swivel, rad, and the head-shake rate while spitting sand, Hz (DESIGN ESTIMATES). */
        const val PEEK_YAW = 0.35
        const val SPIT_HZ = 6.0
        /** Seconds the body eases out of the burrow pose before the dig pose (its own spline override) is taken. */
        const val OVERRIDE_SETTLE_S = 0.8
        /** Body width over body depth: an eel-like, laterally compressed body (DESIGN ESTIMATE from photos). */
        const val WIDTH_RATIO = 0.65
        /** The gap must clear the body (depth × width) by this factor before the goby goes in (fins, scales). */
        const val FIT_MARGIN = 1.15
        /** A burrow is first dug this many body lengths long; maintenance may extend it to [MAX_BL]. */
        const val BURROW_BL = 1.2
        const val MAX_BL = 1.6
        /** Height of the gap it keeps once it fits (× the fit height): sand that trickles back in is cleared. */
        const val CHAMBER = 1.4
        /** Extra burrows at rock edges within [SITE_RADIUS_CM] of the primary one, at least [SITE_SPACING_CM] apart. */
        const val EXTRA_BURROWS = 2
        const val SITE_RADIUS_CM = 16.0
        const val SITE_SPACING_CM = 8.0
        /** Slide-in and back-out speeds in the tunnel (body lengths / s; DESIGN ESTIMATE). */
        const val ENTER_BL_S = 0.5
        const val BACK_BL_S = 0.7
        /** The head reference point keeps this clear of the rock (cm). */
        const val ROCK_CLEAR = 0.08

        /** Mouthful (cm³) of a goby [lengthCm] long: [GOBY_MOUTHFUL_CM3] at 10 cm, ∝ gape². */
        fun mouthfulCm3(lengthCm: Double): Double = GOBY_MOUTHFUL_CM3 * (lengthCm / 10.0).let { it * it }
    }
    val burrow: Anchor? = c.rock.anchors["BURROW_GOBY"] ?: c.rock.anchors["CAVE_EEL"]
    private val anchorMouth: Vec3 = burrow?.pos ?: Vec3(62.0, 3.8, 32.5)
    val inDir: Vec3 = (burrow?.dir ?: Vec3(0.5, 0.1, -0.85)).normalized()
    val outDir: Vec3 = (inDir * -1.0).let { Vec3(it.x, 0.05, it.z).normalized(Vec3.Z) }
    val tunnel: List<Vec3> = (c.rock.anchors["burrow_path"] ?: c.rock.anchors["den_path"])?.points
        ?: listOf(anchorMouth, anchorMouth + inDir * 8.0, anchorMouth + inDir * 16.0)
    /** Individual offset around the mouth so a group spreads out (loose aggregation). */
    private val slot = Vec3(c.rng.signed() * 5, c.rng.range(1.5, 6.0), c.rng.signed() * 3)

    private val sand: SandBed get() = c.sim.sand
    /** Body depth and width the tunnel must clear (cm), and the half depth its reference point rides over the sand. */
    val bodyH: Double get() = o.species.depthRatio * o.lengthCm * FIT_MARGIN
    val bodyW: Double get() = bodyH * WIDTH_RATIO
    private val halfH: Double get() = (0.5 * o.species.depthRatio * o.lengthCm).coerceIn(0.2, 3.0)
    private val chamberH: Double get() = bodyH * CHAMBER

    private var cache: List<Burrow> = emptyList()
    private var cacheEpoch = -1
    /** This goby's burrows (primary first); planned on first use and again after the bed was reset. */
    val burrows: List<Burrow>
        get() {
            if (cacheEpoch != sand.burrowEpoch) {
                if (sand.burrowsOf(o.id).isEmpty()) planBurrows(predig = false)
                cache = sand.burrowsOf(o.id).sortedByDescending { it.primary }
                cacheEpoch = sand.burrowEpoch
            }
            return cache
        }
    val primary: Burrow? get() = burrows.firstOrNull { it.primary }

    /** Mouth of the main burrow (on the sand at the rock edge), or the anchor when there is no rock to dig under. */
    val mouth: Vec3

    init {
        install(InBurrow(), MouthHover(), Excavate(), NightPatrol(), Feed(this, 18.0, "Picking plankton"),
            Flee(this, { _, _ -> outside(1.5) }, "Darting into its burrow"), SurfaceGasp(this))
        // an established fish already has its main burrow: open it to a body length (sand moved to the spoil pile)
        if (sand.burrowsOf(o.id).isEmpty()) planBurrows(predig = true)
        mouth = primary?.let { Vec3(it.mouthX, max(anchorMouth.y, sand.surfaceY(it.mouthX, it.mouthZ) + 1.0), it.mouthZ) } ?: anchorMouth
    }

    private fun planBurrows(predig: Boolean) {
        val L = o.lengthCm
        val p = sand.planBurrow(o.id, anchorMouth.x, anchorMouth.z, inDir.x, inDir.z, bodyH, BURROW_BL * L, primary = true) ?: return
        sand.addBurrow(p)
        // extra burrows only at rock edges it can swim to straight from the main one (low over the sand, no rock between)
        fun apron(b: Burrow) = Vec3(b.x(-1.5), sand.surfaceY(b.x(-1.5), b.z(-1.5)) + 1.5, b.z(-1.5))
        val home = apron(p)
        val sites = sand.burrowSites(o.id, p.mouthX, p.mouthZ, SITE_RADIUS_CM, bodyH, 0.6 * L, BURROW_BL * L, SITE_SPACING_CM, listOf(p), 8)
            .filter { b -> c.rock.sdf.segmentClear(home, apron(b), 0.3) }
        for (b in sites.take(EXTRA_BURROWS)) sand.addBurrow(b)
        if (predig) {
            val sp = spoilAt(p, 0.0)
            sand.excavate(p, bodyH, bodyW, bodyH, 2.0, sp.x, sp.z, 400.0)
        }
    }

    /** Head reference point [s] cm along [b] (negative = out on the apron), riding [halfH] over the live sand. */
    fun at(b: Burrow, s: Double): Vec3 {
        val x = b.x(s); val z = b.z(s)
        val sy = sand.surfaceY(x, z)
        val p = Vec3(x, sy + halfH, z)
        // under a low roof (the dig face, the lip of the mouth) the head stays out of the rock: lower, into the sand
        val sdf = c.rock.sdf
        if (sdf.dist(p) >= ROCK_CLEAR) return p
        var y = p.y
        while (y > sy - halfH) { y -= 0.1; if (sdf.dist(Vec3(x, y, z)) >= ROCK_CLEAR) return Vec3(x, y, z) }
        return Vec3(x, sy - halfH, z)
    }

    /** The body as a polyline from a head at [sHead]: lying deeper into the tunnel ([inward]) or trailing out of it. */
    fun bodyLine(b: Burrow, sHead: Double, inward: Boolean): List<Vec3> {
        val L = o.lengthCm
        return List(9) { i -> at(b, sHead + (if (inward) 1.0 else -1.0) * L * i / 8) }
    }

    /** Where the head can be with the body lying in [b] behind it: [reach] minus a body length (≤ 0 = head outside). */
    fun reach(b: Burrow): Double = sand.reach(b, bodyH, bodyW)

    /** Spoil pile beside the entrance of [b]: about half a body length out, [side] cm to one side. */
    fun spoilAt(b: Burrow, side: Double): Vec3 {
        val q = at(b, -(0.35 * o.lengthCm + 1.5))
        return Vec3(q.x - b.dirZ * side, q.y, q.z + b.dirX * side)
    }

    /**
     * Adults are burrow-bound; juveniles hover and feed near the mouth by day (06 §1.5, 07 gap 3). The stage comes
     * from the sheet's `lifeStages` (adult from 15 cm = [ADULT_CM]; life_stages.md §5), [ADULT_CM] without one.
     */
    val adult: Boolean get() = o.species.lifeStages?.let { com.reeflive.core.sim.LifeStages.isAdult(o) } ?: (o.lengthCm >= ADULT_CM)
    /**
     * Striped juveniles school tight, "in formation to look like a single large body" (FishBase; life_stages.md §5):
     * they hover in a closer group than the subadults, whose stripes are breaking up.
     */
    val tightSchool: Boolean get() = com.reeflive.core.sim.LifeStages.isJuvenile(o)
    /** Sand mouthfuls spat out (burrow maintenance events, 06 §5.6 #14). */
    var sandEvents = 0
    /** Whether tonight is one of the rare nights an adult patrols outside (rolled at lights-off). */
    var patrolTonight = false
    private var lastNightRoll = -1e9

    /** Roll the adult night patrol once per night (p = [ADULT_NIGHT_PATROL_P], [I] from 07 gap 3). */
    fun rollNight(c: Ctx) {
        if (c.p.lightsOffAt <= lastNightRoll) return
        lastNightRoll = c.p.lightsOffAt
        // drawn from the night and the fish, not the behaviour stream: whether it patrols tonight does not hang on how
        // many random draws its day happened to take
        val night = kotlin.math.floor(c.p.lightsOffAt / 86400.0).toLong()
        patrolTonight = adult && com.reeflive.core.geom.Rng(c.o.id.hashCode().toLong() * 0x9E3779B97F4A7C15uL.toLong() + night).chance(ADULT_NIGHT_PATROL_P)
    }

    /**
     * Where the excavated sand comes from: the cell the last mouthful was taken from (under the rock), else the main
     * burrow's dig face, else the dig zone at the mouth (a pit in the sand bed, SIMULATION §16).
     */
    val digPoint: Vec3 get() {
        if (!sand.lastDigX.isNaN() && sand.mouthfuls > 0) return Vec3(sand.lastDigX, sand.surfaceY(sand.lastDigX, sand.lastDigZ), sand.lastDigZ)
        val p = primary ?: return c.rock.anchors["SAND_GOBY_DIG"]?.pos ?: outside(1.0)
        return at(p, min(p.lengthCm, reach(p) + SandBed.REACH_STEP))
    }

    fun outside(k: Double): Vec3 = primary?.let { at(it, -k) }
        ?: (Vec3(anchorMouth.x, max(anchorMouth.y, sand.surfaceY(anchorMouth.x, anchorMouth.z) + 1.0), anchorMouth.z) + outDir * k)
    fun inside(k: Double) = mouth + inDir * k

    /**
     * Body trailing back from the head when there is no dug burrow (the rock den path): down the tunnel when it faces
     * out, out through the mouth when it faces in (so a head-first goby does not fold back on itself at the mouth).
     */
    fun tunnelSpline(head: Vec3, heading: Vec3 = outDir): List<Vec3> =
        if ((heading dot inDir) > 0) listOf(head, anchorMouth, outside(o.lengthCm)) else listOf(head, anchorMouth) + tunnel.drop(1)

    /** Head position in the burrow by night (deep, facing in) or day (peeking out), limited to what is dug. */
    private fun restHeadS(b: Burrow, night: Boolean, peek: Double): Double {
        val r = reach(b)
        val L = o.lengthCm
        return if (night) min(L + 1.0, r - 0.3).coerceAtLeast(-0.6)   // head deepest, body behind it toward the mouth
        else min(-peek, r - 0.95 * L)                                     // head out, body lying in the tunnel
    }

    override fun restSpot(c: Ctx, night: Boolean): Rest {
        val b = primary
        if (b == null) {
            val head = if (night) inside(4.0) else outside(0.5)
            return Rest(head, if (night) inDir else outDir, posture = 3, spline = tunnelSpline(head, if (night) inDir else outDir))
        }
        val s = restHeadS(b, night, 0.5)
        val inV = Vec3(b.dirX, 0.0, b.dirZ)
        return Rest(at(b, s), if (night) inV else inV * -1.0, posture = 3, spline = bodyLine(b, s, inward = !night))
    }

    override fun findThreat(c: Ctx): Organism? = super.findThreat(c)

    /**
     * Abstract time (time-lapse, behaviour off): one mouthful from wherever [Excavate] would dig, spat [k] cm out of
     * the entrance with the given offsets; without a dug burrow the old pit at the dig zone.
     */
    fun abstractDig(k: Double, ox: Double, oz: Double) {
        val pick = chooseDig()
        if (pick == null) {
            if (primary != null) return
            val pit = digPoint
            sand.dig(pit.x, pit.z, GOBY_MOUTHFUL_CM3, 1.5)
            c.sim.depositSand(outside(k) + Vec3(ox, 0.0, oz), GOBY_MOUTHFUL_CM3)
            return
        }
        val (b, s) = pick
        val got = sand.takeMouthful(b.x(s), b.z(s), mouthfulCm3(o.lengthCm))
        if (got <= 0) return
        b.mouthfuls++
        val sp = spoilAt(b, ox * 0.5)
        c.sim.spitSand(sp, got)
    }

    /**
     * The next mouthful: the main burrow's face until it is open to its length, then the shallowest unfinished extra
     * burrow, then upkeep (floors, extension, apron) of the burrow that needs it, round-robin by mouthfuls.
     */
    fun chooseDig(): Pair<Burrow, Double>? {
        val bs = burrows
        if (bs.isEmpty()) return null
        val p = bs.first()
        if (reach(p) < p.lengthCm) sand.nextDig(p, bodyH, bodyW, chamberH, MAX_BL * o.lengthCm)?.let { return p to it }
        bs.filter { reach(it) < it.lengthCm }.minByOrNull { reach(it) / it.lengthCm }?.let { b ->
            sand.nextDig(b, bodyH, bodyW, chamberH, MAX_BL * o.lengthCm)?.let { return b to it }
        }
        for (b in bs.sortedBy { it.mouthfuls }) sand.nextDig(b, bodyH, bodyW, chamberH, MAX_BL * o.lengthCm)?.let { return b to it }
        return null
    }

    /**
     * In the burrow: deep and out of sight at night; by day it peeks with just the head out (research video
     * fish_engineer_goby [S1][M]), easing in and out and swivelling the head and eyes to look around. Only as deep
     * as the dug tunnel lets the body in.
     */
    inner class InBurrow : Behavior("burrow", "In its burrow") {
        private var lookYaw = 0.0
        private var nextLook = 0.0
        override fun score(c: Ctx): Double {
            val o = c.o
            return when {
                c.p.night -> 0.85 * Feed.nightRestDamp(c, this@GobyBrain)
                o.waryLevel > 0.3 || o.startle > 0.2 -> 0.7
                adult -> 0.75
                else -> 0.25 + 0.3 * o.skittishness
            }
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val o = c.o
            val night = c.p.night
            if (o.posture != 3 && o.pos.distanceTo(mouth) > 4) {
                val home = outside(1.0)
                val to = home - o.pos
                if (to.length() < 20.0 && c.rock.sdf.segmentClear(o.pos, home, 0.3)) {
                    // eel-like along the bed and the rock foot back to its burrow (the open-water swim holds a 20 cm body
                    // off the rock it lives against, which froze it a body length from home)
                    val dir = to.normalized(o.heading)
                    out.place(o.pos + dir * min(to.length(), o.cruiseSpeed * c.dt), dir, Vec3.Y, 6.0)
                    out.ampMul = 1.2
                } else out.seek(c.viaOpenWater(home), o.cruiseSpeed, arrive = 2.0, urgency = 0.5)
                return
            }
            if (!night && c.t > nextLook) { nextLook = c.t + c.rng.range(1.5, 4.0); lookYaw = c.rng.signed() * PEEK_YAW }
            val b = primary
            if (b == null) {
                val head = if (night) inside(4.0) else outside(0.6 + 0.4 * sin(c.t * 0.3))
                val face = if (night) inDir else Vec3.rotateAbout(outDir, Vec3.Y, lookYaw)
                out.place(head, face, Vec3.Y, 3.0)
                out.splineOverride = tunnelSpline(o.pos, face)
            } else {
                val s = restHeadS(b, night, 0.6 + 0.4 * sin(c.t * 0.3))
                val inV = Vec3(b.dirX, 0.0, b.dirZ)
                val face = if (night) inV else Vec3.rotateAbout(inV * -1.0, Vec3.Y, lookYaw)
                out.place(at(b, s), face, Vec3.Y, 3.0)
                out.splineOverride = bodyLine(b, s, inward = !night)
            }
            o.eyeYaw = if (night) 0.0 else lookYaw * 0.6
            out.posture = 3
            o.visible = !night
            o.sleeping = night
            o.gill = 0.4 + 0.3 * sin(2 * PI * 0.5 * c.t)
        }
        override fun exit(c: Ctx) { c.o.visible = true; c.o.eyeYaw = 0.0 }
    }

    /** Daytime: hover low in a loose group near the mouth, nose into the current, snapping at plankton. */
    inner class MouthHover : Behavior("mouthhover", "Hovering by its burrow") {
        private var nextSnap = 0.0
        override fun score(c: Ctx): Double = if (c.p.night || adult) 0.0 else 0.55 * (1 - 0.5 * c.o.waryLevel)
        override fun update(c: Ctx, out: SteerIntent) {
            val o = c.o
            val k = if (tightSchool) 0.55 else 1.0
            val spot = c.safe(outside(6.0) + slot * k + Vec3(sin(c.t * 0.11 + slot.x) * 2.0 * k, sin(c.t * 0.07) * 1.0, 0.0), 1.0)
            out.seek(spot, o.cruiseSpeed * 0.5, arrive = 2.5)
            out.hover = true
            out.ampMul = 0.6
            out.separation = if (tightSchool) 0.9 else 1.5
            // pick drifting particles within a few cm
            val f = if (o.hunger > 0.05) c.food.nearest(o.pos, 6.0) { o.species.dietMatch(it.kind) > 0 } else null
            if (f != null) {
                out.seek(f.pos, o.cruiseSpeed * 1.5, arrive = 0.5, urgency = 0.6)
                if (o.pos.distanceTo(f.pos) < 1.2 && c.t > nextSnap) { c.bite(f); nextSnap = c.t + 1.0 }
            }
        }
    }

    /**
     * Excavation trip (research: mouthfuls carried out and spat at the burrow mouth, fish_engineer_goby [S2][M],
     * Shedd [S1]): to the burrow mouth, slide in head first as far as the gap lets the body, dig at the face with
     * the head pushed into the sand and the body wriggling, back out with the mouthful, turn and spit it onto the
     * spoil pile at the entrance (a mound and a small plume). Without a burrow under rock: the old carry-and-spit.
     */
    inner class Excavate : Behavior("dig", "Moving sand out of its burrow") {
        private var phase = 0
        private var timer = 0.0
        private var dump = Vec3.ZERO
        private var nextAt = 0.0
        private var b: Burrow? = null
        private var sDig = 0.0
        private var sHead = 0.0
        private var digFor = 3.0
        private var carrying = 0.0
        override val committed: Boolean get() = phase in 1..4
        override val label: String get() = when (phase) {
            1 -> "Sliding into its burrow"; 2 -> "Digging under the rock"; 3 -> "Backing out with a mouthful of sand"
            else -> "Moving sand out of its burrow"
        }
        override fun score(c: Ctx): Double = if (c.p.night || c.t < nextAt || c.o.waryLevel > 0.3) 0.0 else if (adult) 0.93 else 0.72
        override fun enter(c: Ctx) {
            phase = 0; timer = 0.0; carrying = 0.0
            val pick = chooseDig()
            b = pick?.first; sDig = pick?.second ?: 0.0
            val side = c.rng.signed() * 5.0
            val bb = b
            if (bb != null) {
                dump = spoilAt(bb, side * 0.6).let { it.withY(sand.surfaceY(it.x, it.z) + 1.2) }
                digFor = c.rng.range(2.0, 5.0)
            } else {
                val sideV = (outDir cross Vec3.Y).normalized(Vec3.X) * side
                val d = c.safe(outside(c.rng.range(4.0, 9.0)) + sideV, 0.5)
                // spit just above the live sand surface, so the mouthful lands on the SandBed mound it builds
                dump = d.withY(sand.surfaceY(d.x, d.z) + 1.2)
            }
        }
        override fun exit(c: Ctx) {
            val o = c.o
            o.inSand = false
            // interrupted with sand in its mouth (a scare): it lets the mouthful fall where it is
            if (carrying > 0) { sand.spit(o.pos.x, sand.surfaceY(o.pos.x, o.pos.z) + 0.5, o.pos.z, carrying, 1.0, plume = 2); carrying = 0.0 }
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val o = c.o
            timer += c.dt
            val bb = b
            if (bb == null && primary != null) { nextAt = c.t + c.rng.range(600.0, 1800.0); finished = true; return }
            when (phase) {
                0 -> { // to the burrow mouth
                    val entry = if (bb != null) at(bb, -min(3.0, 0.4 * o.lengthCm)) else outside(0.5)
                    out.seek(if (o.pos.distanceTo(entry) > 15.0) c.viaOpenWater(entry) else entry, o.cruiseSpeed, arrive = 1.0)
                    // cannot get there (cut off by rock or a crowd): try again later
                    if (timer > 60.0) { nextAt = c.t + c.rng.range(60.0, 240.0); finished = true; return }
                    // (not before the body has eased out of the burrow pose it was in: the dig pose replaces that override outright,
                    // and taking it the next tick swung the tail 14 cm in one frame)
                    if (o.pos.distanceTo(entry) < 2.0 && timer > OVERRIDE_SETTLE_S) {
                        phase = 1; timer = 0.0
                        sHead = -min(3.0, 0.4 * o.lengthCm)
                    }
                }
                1 -> { // slide in head first, only as far as the gap clears the body
                    if (bb == null) { phase = 2; timer = 0.0; return }
                    o.inSand = false
                    val target = if (sDig < 0) sDig else min(sDig - 0.3, reach(bb))
                    sHead = min(target, sHead + ENTER_BL_S * o.lengthCm * c.dt)
                    placeIn(bb, out, wriggle = 1.0)
                    if (sHead >= target - 0.05 || timer > 30) { phase = 2; timer = 0.0 }
                }
                2 -> { // digging at the face: head in the sand, body working
                    if (bb == null) {
                        out.place(inside(2.5), inDir, Vec3.Y, 4.0)
                        out.posture = 3
                        out.splineOverride = tunnelSpline(o.pos, inDir)
                        if (timer > 4.0 + 4.0 * c.rng.nextDouble()) {
                            digPoint.let { pit -> sand.dig(pit.x, pit.z, GOBY_MOUTHFUL_CM3, 1.5) }
                            carrying = 0.0
                            phase = 4; timer = 0.0
                        }
                        return
                    }
                    o.inSand = true
                    placeIn(bb, out, wriggle = 1.8, nudge = 0.25 * sin(2 * PI * 2.5 * timer))
                    if (timer > digFor) {
                        val got = sand.takeMouthful(bb.x(sDig), bb.z(sDig), mouthfulCm3(o.lengthCm))
                        if (got > 0) bb.mouthfuls++
                        carrying = got
                        o.inSand = false
                        phase = 3; timer = 0.0
                    }
                }
                3 -> { // back out, still facing in, cheeks full
                    if (bb == null) { phase = 4; return }
                    o.inSand = false
                    o.mouth = 0.45
                    sHead -= BACK_BL_S * o.lengthCm * c.dt
                    placeIn(bb, out, wriggle = 1.2)
                    if (sHead <= -0.6 || timer > 30) { phase = 4; timer = 0.0 }
                }
                4 -> { // turn and carry the mouthful out: head a little down, short wriggling swim
                    o.inSand = false
                    o.mouth = 0.45
                    out.seek(dump, o.cruiseSpeed * 0.8, arrive = 1.0, urgency = 0.4)
                    out.ampMul = 1.3
                    out.pitchBias = -0.15
                    if (o.pos.distanceTo(dump) < 2.0 || timer > 10) { phase = 5; timer = 0.0 }
                }
                else -> { // spit: head jerks side to side as the sand is blown out
                    o.mouth = 0.6 + 0.4 * sin(2 * PI * SPIT_HZ * timer)
                    out.seek(dump + Vec3.Y * 1.5, 0.5, arrive = 1.0)
                    out.pitchBias = -0.4
                    out.rollExtra = 0.25 * sin(2 * PI * SPIT_HZ * timer)
                    out.waddleAmp = 0.2; out.waddleHz = SPIT_HZ
                    if (timer > 0.6) {
                        if (bb != null) {
                            val f = o.heading
                            if (carrying > 0) c.sim.spitSand(dump, carrying, f.x * 2.0, f.z * 2.0)
                            carrying = 0.0
                        } else c.sim.depositSand(dump, GOBY_MOUTHFUL_CM3)
                        sandEvents++
                        // adults: a maintenance trip every 0.7–2 h (≈ 5–15 per day); juveniles dig much more often
                        // (a mouthful every 20–40 s while working, research video fish_engineer_goby [S2][M]; with breaks)
                        nextAt = c.t + if (adult) c.rng.range(2500.0, 7000.0) else c.rng.range(25.0, 120.0)
                        finished = true
                    }
                }
            }
        }

        /** Head at [sHead] along the burrow facing in, the body trailing back out of the tunnel. */
        private fun placeIn(bb: Burrow, out: SteerIntent, wriggle: Double, nudge: Double = 0.0) {
            val head = at(bb, sHead + nudge).let { h -> if (nudge != 0.0 && c.rock.sdf.dist(h) < ROCK_CLEAR) at(bb, sHead) else h }
            // squeezing under the lip with the head low in the sand: it is pushing that sand, not passing through it
            if (head.y < sand.surfaceY(head.x, head.z) + halfH - 0.05) c.o.inSand = true
            out.place(head, Vec3(bb.dirX, 0.0, bb.dirZ), Vec3.Y, 8.0)
            out.posture = if (sHead > 0.3) 3 else 0
            out.splineOverride = bodyLine(bb, sHead, inward = false)
            out.ampMul = wriggle
        }
    }


    /** The rare adult night out (07 gap 3: ≈ 5 % of nights): a slow circuit within 25 cm of the burrow. */
    inner class NightPatrol : Behavior("nightpatrol", "Patrolling at night") {
        private var wp = Vec3.ZERO
        private var next = 0.0
        override fun score(c: Ctx): Double {
            if (!c.p.night) return 0.0
            rollNight(c)
            return if (patrolTonight) 0.9 else 0.0
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val o = c.o
            o.visible = true
            if (c.t > next || o.pos.distanceTo(wp) < 2) {
                next = c.t + c.rng.range(8.0, 20.0)
                wp = c.safe(outside(4.0) + Vec3(c.rng.signed() * 25, c.rng.range(1.0, 8.0), c.rng.signed() * 10), 1.0)
            }
            out.seek(wp, o.cruiseSpeed * 0.6, arrive = 2.0)
            out.ampMul = 0.8
        }
    }
}
