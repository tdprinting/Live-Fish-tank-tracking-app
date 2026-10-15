package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.Anchor
import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.BrittleArms
import com.reeflive.core.sim.CrabLegs
import com.reeflive.core.sim.FoodParticle
import com.reeflive.core.sim.HermitLegs
import com.reeflive.core.sim.Organism
import com.reeflive.core.sim.ShrimpLimbs
import com.reeflive.core.sim.SimEvent
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * Invertebrate brains (research 04, SIMULATION §4.5 additions). Same utility architecture with
 * simpler behavior sets. Crawlers move with `SteerIntent.crawl` (surface-constrained), sessile
 * animals with kinematic placement.
 */

/** Crawl to settled/low food within [radius] and eat it. */
open class Scavenge(val b: SpeciesBrain, val radius: Double, val urgentAbove: Double = 0.5, label: String = "Scavenging", val settledOnly: Boolean = true) : Behavior("scavenge", label) {
    private var f: FoodParticle? = null
    private var lastSeen = 0.0
    override fun score(c: Ctx): Double {
        if (c.o.hunger < 0.12) return 0.0
        f = c.findFood(radius, los = false) { !settledOnly || it.settled || it.pos.y < c.rock.sandTopCm + 6 }
        return if (f != null) (0.55 + 0.35 * c.o.hunger) else 0.0
    }
    override fun enter(c: Ctx) { lastSeen = c.t }
    override fun update(c: Ctx, out: SteerIntent) {
        var ff = f
        if (ff == null || ff.eaten) { ff = c.findFood(radius, los = false) { !settledOnly || it.settled || it.pos.y < c.rock.sandTopCm + 6 }; f = ff }
        if (ff == null) { if (c.t - lastSeen > 3) finished = true; return }
        lastSeen = c.t
        out.crawl = true
        out.target = ff.pos
        out.speed = if (c.o.hunger > urgentAbove) c.o.burstSpeed else c.o.cruiseSpeed * 1.5
        out.urgency = if (c.o.hunger > urgentAbove) 0.8 else 0.3
        out.arriveRadius = 1.0
        out.surfaceOffset = surfaceOffset(c)
        if (c.o.pos.distanceTo(ff.pos) < 1.5 + 0.3 * c.L) { c.bite(ff); f = null }
    }
}

/** A random open-sand point (clear of rock by 2 cm) at height [lift] above the sand. */
fun sandSpot(c: Ctx, xr: ClosedFloatingPointRange<Double>, zr: ClosedFloatingPointRange<Double>, lift: Double): Vec3 {
    var p = Vec3(c.rng.range(xr.start, xr.endInclusive), c.rock.sandTopCm + lift, c.rng.range(zr.start, zr.endInclusive))
    for (i in 0 until 30) {
        if (c.rock.sdf.dist(p) > 2.0) return p
        p = Vec3(c.rng.range(xr.start, xr.endInclusive), c.rock.sandTopCm + lift, c.rng.range(zr.start, zr.endInclusive))
    }
    return p
}

fun surfaceOffset(c: Ctx) = 0.5 * c.o.species.depthRatio * c.L + 0.2

/** Wander along surfaces around a centre. */
open class CrawlWander(val b: SpeciesBrain, val centre: (Ctx) -> Vec3, val radius: Double, val weight: (Ctx) -> Double, label: String = "Wandering") : Behavior("crawl", label) {
    private var wp = Vec3.ZERO
    private var next = 0.0
    override fun score(c: Ctx) = weight(c)
    override fun enter(c: Ctx) { next = 0.0 }
    override fun update(c: Ctx, out: SteerIntent) {
        if (c.t > next || c.o.pos.distanceTo(wp) < 1.0) {
            next = c.t + c.rng.range(20.0, 60.0)
            val ce = centre(c)
            wp = c.rockSurfacePoint(ce, radius)?.first ?: Vec3(ce.x + c.rng.signed() * radius, c.rock.sandTopCm, ce.z + c.rng.signed() * radius * 0.5)
            wp = c.rock.clampToWater(wp, 1.0)
        }
        out.crawl = true; out.target = wp; out.speed = c.o.cruiseSpeed; out.arriveRadius = 1.0
        out.surfaceOffset = surfaceOffset(c)
    }
}

/** Hidden while molting (24–48 h); the shell lies on the sand (Simulation.moltShells). */
class MoltHide(val b: SpeciesBrain, val spot: (Ctx) -> Rest) : Behavior("molt", "Molting (hidden)") {
    override fun score(c: Ctx) = if (c.o.molting) 0.99 else 0.0
    override val committed: Boolean get() = true
    override fun update(c: Ctx, out: SteerIntent) {
        val r = spot(c)
        out.rest(r.copy(posture = 6), 1.0)
        c.o.visible = false
        if (!c.o.molting) finished = true
    }
    override fun exit(c: Ctx) { c.o.visible = true }
}

/**
 * Shrimp tail-flip escape (caridoid escape): a 0.5 s backward burst of abdominal flexions facing the threat, then back
 * to shelter on foot, or with a swim hop when the flip left it in open water or the walk stalls ([ShrimpNav]).
 */
class TailFlip(val b: SpeciesBrain, val nav: ShrimpNav? = null, val home: (Ctx) -> Rest) : Behavior("tailflip", "Tail-flip escape") {
    private var from = Vec3.ZERO
    override val committed: Boolean get() = elapsed < 6
    override fun score(c: Ctx): Double {
        b.threat?.let { return 0.96 }
        return if (b.startledRecently && b.startleMag > 0.3) 0.9 else 0.0
    }
    override fun enter(c: Ctx) { from = b.threat?.pos ?: b.startleFrom; b.startleAt = -1e9; nav?.reset() }
    override fun update(c: Ctx, out: SteerIntent) {
        if (elapsed < 0.5) {
            out.fleeFrom = from; out.urgency = 1.0
            out.faceDir = (from - c.o.pos).normalized(c.o.heading)
            return
        }
        val h = home(c)
        if (nav != null) {
            if (nav.drive(c, out, h.pos, h.up, c.o.cruiseSpeed * 2.0) == ShrimpNav.Status.ARRIVED) out.rest(h, 3.0)
        } else {
            out.crawl = true; out.target = h.pos; out.speed = c.o.burstSpeed * 0.5; out.urgency = 0.8
            out.surfaceOffset = surfaceOffset(c)
        }
        if (elapsed > 6 && b.threat == null) finished = true
    }
}

/**
 * Shrimp food run (feeding time, research 12/04): walk to settled food or food lodged on the rock, or swim a short hop
 * to grab a particle drifting within [swimRadius] cm (the skunk cleaner steals food out of the water column and from
 * fishes' mouths; the banded shrimp lunges for nearby pieces) [M behaviour; radii DESIGN ESTIMATE]. Picks with the small
 * chelae (mouth strokes) and goes home afterwards through the resting behaviour's navigation.
 */
class ShrimpForage(val b: SpeciesBrain, val nav: ShrimpNav, val radius: Double, val swimRadius: Double, label: String) : Behavior("scavenge", label) {
    private var f: FoodParticle? = null
    private var lastSeen = 0.0
    private fun reachable(c: Ctx, p: FoodParticle): Boolean =
        p.settled || c.rock.envDist(p.pos) < shrimpStance(c.o) + 2.0 || p.pos.distanceTo(c.o.pos) < swimRadius
    override fun score(c: Ctx): Double {
        if (c.o.hunger < 0.12 || c.o.molting) return 0.0
        f = c.findFood(radius, los = false) { reachable(c, it) }
        return if (f != null) (0.55 + 0.35 * c.o.hunger) else 0.0
    }
    override fun enter(c: Ctx) { lastSeen = c.t; nav.reset() }
    override fun update(c: Ctx, out: SteerIntent) {
        var ff = f
        if (ff == null || ff.eaten) { ff = c.findFood(radius, los = false) { reachable(c, it) }; f = ff }
        if (ff == null) { if (c.t - lastSeen > 3) finished = true; out.crawl = c.rock.envDist(c.o.pos) < 3; out.surfaceOffset = shrimpStance(c.o); return }
        lastSeen = c.t
        val reach = 1.5 + 0.3 * c.L
        nav.drive(c, out, ff.pos, null, c.o.cruiseSpeed * (if (c.o.hunger > 0.4) 1.8 else 1.4), arrive = reach * 0.7,
            swimCmS = ShrimpNav.HOP_CM_S * (if (b.o.brainKind == com.reeflive.core.sim.BrainKind.CLEANER_SHRIMP) 1.4 else 1.0))
        c.o.mouth = max(c.o.mouth, 0.3)
        if (c.o.pos.distanceTo(ff.pos) < reach) { c.bite(ff); f = null }
    }
}

/**
 * Antenna grooming (research 17, Bauer 1981: Stenopus draws one antennule at a time through the brush of its third
 * maxillipeds and cleans the long flagellum with the first pereiopod; carideans groom the same way, ANALOGY for
 * Lysmata). Done in short bouts while the shrimp sits at its ledge or station, as an overlay on that resting
 * behaviour (it never changes which behaviour runs, so it draws no rng and leaves other sequences as they were).
 * Interval and bout length are DESIGN ESTIMATES.
 */
class GroomClock {
    companion object {
        @com.reeflive.core.sim.Ev(com.reeflive.core.sim.Evidence.DESIGN_ESTIMATE, "17 Bauer 1981") const val INTERVAL_S = 240.0
        const val BOUT_MIN_S = 6.0
        const val BOUT_MAX_S = 14.0
    }
    private var next = -1.0
    private var start = 0.0
    private var until = -1.0
    private var n = 0
    var bouts = 0; private set
    val grooming: Boolean get() = until > start && lastT < until
    private var lastT = 0.0
    /** Deterministic 0..1 jitter per bout from the organism id. */
    private fun jitter(c: Ctx): Double { val x = kotlin.math.sin(n++ * 12.9898 + c.o.id.hashCode() * 0.001) * 43758.5453; return x - kotlin.math.floor(x) }

    /** Call every tick from the resting behaviour; [allowed] false (a client, a threat) ends a bout early. */
    fun tick(c: Ctx, allowed: Boolean) {
        lastT = c.t
        if (next < 0) next = c.t + INTERVAL_S * (0.3 + 0.7 * jitter(c))
        val ok = allowed && !c.o.molting
        if (c.t >= until && c.t >= next && ok) {
            start = c.t; until = c.t + BOUT_MIN_S + (BOUT_MAX_S - BOUT_MIN_S) * jitter(c); bouts++
            next = until + INTERVAL_S * (0.6 + 0.8 * jitter(c))
        }
        if (c.t < until && !ok) until = c.t
        if (c.t < until) {
            val e = c.t - start
            // ease in, hold, ease out: the antenna is drawn down to the mouthparts and pulled through
            c.o.groom = com.reeflive.core.geom.smoothstep(0.0, 1.0, e) * (1 - com.reeflive.core.geom.smoothstep(until - start - 1.0, until - start, e))
            c.o.mouth = max(c.o.mouth, 0.4 * c.o.groom)
        }
    }
}

fun hangPose(c: Ctx, a: Anchor?, fallback: Vec3, inverted: Boolean): Rest {
    val p = a?.pos ?: fallback
    val n = (a?.normal ?: if (inverted) Vec3(0.0, -1.0, 0.3) else Vec3.Y).normalized(Vec3.Y)
    val h = Vec3.X.rejectFrom(n).normalized(Vec3.Z)
    return Rest(p + n * surfaceOffset(c), h, n, posture = if (inverted) 2 else 1)
}

/**
 * A shrimp's resting spot resolved on the real rock (the anchors file stores points that can sit in open water, as the
 * cleaning station does, or just inside the rock, and normals from an older bake): [perchNear] around the anchor, the
 * body laid along the surface ([bodyHeading]) at stance height. Null when no face passes [accept].
 */
fun shrimpPerch(c: Ctx, near: Vec3, radius: Double, posture: Int, taken: List<Vec3>, prefer: Vec3,
                accept: (Vec3, Vec3) -> Boolean, score: (Vec3, Vec3) -> Double): Rest? {
    val hit = c.rock.perchNear(near, radius, spacing = 3.5, taken = taken, accept = accept, score = score) ?: return null
    val (q, n) = hit
    val st = shrimpStance(c.o)
    val h = c.rock.bodyHeading(q, n, c.L, st, prefer)
    return Rest(q + n * st, h, n, posture)
}

// =====================================================================================
// Banded coral shrimp (Stenopus hispidus): hangs under ledges, antennae, scavenges, molts
// =====================================================================================
class BandedShrimpBrain(c: Ctx) : SpeciesBrain(c) {
    companion object {
        /**
         * Keep-apart distance from a skunk cleaner's station, cm (06 §2.14, §5.6 #15). Was 30; Tee's tank keeps both
         * shrimp with the banded one under the LEFT ledge (tee_tank_day P1, T96 0:09 [H]), about 25 cm above the
         * station, so the rule only moves it when the skunk sits practically next door (DESIGN ESTIMATE).
         */
        const val KEEP_APART_CM = 20.0
        /** Intruder distance that raises the claw display, cm (DESIGN ESTIMATE). */
        const val DISPLAY_CM = 8.0
        /** Cleans per fish visit (SOURCED for its stations, research 12 NTOU thesis: 5.6 %; not a universal constant). */
        @com.reeflive.core.sim.Ev(com.reeflive.core.sim.Evidence.SOURCED, "12 NTOU thesis") const val CLEAN_PER_VISIT = 0.056
        /** Parasite removal by day vs night (ANALOGY from grouper trials, Vaughan 2018; value DESIGN ESTIMATE). */
        const val DAY_PARASITE_FACTOR = 0.3
        /** Night foray radius around the ledge, cm: territory 1–2 m wide in the wild (Wikipedia/ADW, <0.5 m moves) scaled to the tank [M][L]. */
        const val FORAY_CM = 30.0
    }
    private val requested: Anchor? = c.rock.anchors[o.hostAnchor ?: "SHRIMP_LEDGE"] ?: c.rock.anchors["SHRIMP_LEDGE"]
    var ledge: Anchor? = requested; private set
    private var nextLedgeCheck = 0.0
    val nav = ShrimpNav(o)
    private var perch: Rest? = null
    private var perchFor: String? = null

    /**
     * The hang spot: the underside of the ledge (normal pointing down, n.y < −0.45) nearest the anchor, in its shade,
     * the body along the ceiling with the head toward the open front so the antennae reach out of the overhang
     * (Tee's photos b_02/b_03: upside down under the ledge, claws and antennae out [H]). A mate takes the next spot.
     */
    fun hang(c: Ctx): Rest {
        val a = ledge
        val key = a?.name ?: "fallback"
        if (perchFor != key) {
            perchFor = key
            val near = a?.pos ?: Vec3(39.9, 26.2, 16.6)
            val taken = c.sim.organisms.filter { it !== o && it.brainKind == com.reeflive.core.sim.BrainKind.BANDED_SHRIMP }
                .mapNotNull { (c.sim.agentOf(it)?.brain as? BandedShrimpBrain)?.perchPoint }
            perch = shrimpPerch(c, near, 10.0, 2, taken, Vec3(0.2, 0.0, 1.0),
                accept = { _, n -> n.y < -0.45 },
                score = { q, n -> q.distanceTo(near) + 6.0 * (1 - c.rock.sdf.cover(q + n * 2.0)) + 3.0 * (1 + n.y) })
        }
        return perch ?: hangPose(c, a, Vec3(39.9, 26.2, 16.6), inverted = true)
    }
    /** The surface point under the hang spot (for a mate's spacing). */
    val perchPoint: Vec3? get() = perch?.let { it.pos - (it.up ?: Vec3.Y) * shrimpStance(o) }

    /**
     * Banded and skunk shrimp keep apart (06 §2.14, §5.6 #15): with a skunk cleaner in the tank the
     * banded shrimp takes the ledge farthest from it when its own is within [KEEP_APART_CM].
     */
    fun chooseLedge(c: Ctx) {
        val skunks = c.sim.organisms.filter { it.alive && it.brainKind == BrainKind.CLEANER_SHRIMP }.map {
            (c.sim.agentOf(it)?.brain as? CleanerShrimpBrain)?.homeSpot ?: it.homePos.takeIf { h -> h != Vec3.ZERO } ?: it.pos
        }
        if (skunks.isEmpty()) { ledge = requested; return }
        fun sep(a: Anchor) = skunks.minOf { it.distanceTo(a.pos) }
        val req = requested ?: return
        if (sep(req) >= KEEP_APART_CM) { ledge = req; return }
        ledge = c.rock.anchors.ofType("LEDGE").maxByOrNull { sep(it) }?.takeIf { sep(it) > sep(req) } ?: req
    }

    override fun preTick(c: Ctx) {
        super.preTick(c)
        if (c.t >= nextLedgeCheck) { nextLedgeCheck = c.t + 600; chooseLedge(c) }
        o.groom = max(0.0, o.groom - c.dt * 2)
        // claw display: the big banded chelae swing up and out toward an intruder at the ledge (DESIGN ESTIMATE,
        // research 17 found no measured threat display; Stenopus defends its shelter, 12 notes). A client it is
        // cleaning is not an intruder (Clean drops the display).
        val intruder = threat != null || c.p.neighbors(o.pos, DISPLAY_CM + 4).any {
            it !== o && it.alive && it.pos.distanceTo(o.pos) < DISPLAY_CM &&
                (it.brainKind == BrainKind.BANDED_SHRIMP && it.id != o.mateId || it.brainKind == BrainKind.CLEANER_SHRIMP || (it.isFish && it.vel.length() > 0.5 * it.cruiseSpeed))
        }
        o.clawDisplay += ((if (intruder && !o.molting) 1.0 else 0.0) - o.clawDisplay) * kotlin.math.min(1.0, c.dt * 3)
    }

    override fun abstractPose(c: Ctx, out: SteerIntent) {
        if (c.t >= nextLedgeCheck) { nextLedgeCheck = c.t + 600; chooseLedge(c) }
        super.abstractPose(c, out)
    }

    init {
        install(Hang(), ShrimpForage(this, nav, 30.0, 8.0, "Scavenging"), NightForage(), Clean(), MoltHide(this) { hang(it) }, TailFlip(this, nav) { hang(it) })
    }

    override fun restSpot(c: Ctx, night: Boolean) = hang(c)

    override fun anchorPos(c: Ctx, anchor: String): Vec3? = if (anchor == "ledge") hang(c).pos else super.anchorPos(c, anchor)

    val groomClock = GroomClock()

    /**
     * Upside down under the ledge (Tee's tank [H]; "hangs upside-down … antennae waving", research 02). The antennae
     * advertise: when a fish idles within [ADVERT_CM] the shrimp waves them faster and sways side to side (the
     * "dance" near home, Wikipedia S. hispidus [M]; rate DESIGN ESTIMATE).
     */
    inner class Hang : Behavior("hang", "Hanging under the ledge") {
        override val label: String get() = when {
            nav.swimming -> "Swimming back to its ledge"
            !home -> "Walking back to its ledge"
            groomClock.grooming -> "Grooming its antennae"
            else -> "Hanging under the ledge"
        }
        private var home = true
        private var shuffle = Vec3.ZERO
        private var next = 0.0
        override fun score(c: Ctx) = if (c.p.night) 0.25 else 0.5
        override fun enter(c: Ctx) { nav.reset() }
        override fun update(c: Ctx, out: SteerIntent) {
            val r = hang(c)
            val n = r.up ?: Vec3.Y
            if (c.o.pos.distanceTo(r.pos) > 2.5 || nav.swimming) {
                home = nav.drive(c, out, r.pos, n, c.o.cruiseSpeed * 1.2, arrive = 1.0) == ShrimpNav.Status.ARRIVED
                if (!home) return
            }
            home = true
            if (c.t > next) {
                next = c.t + c.rng.range(5.0, 10.0)
                // a small step along the ceiling, kept at stance height on the real (curved) surface
                val st = shrimpStance(c.o)
                val foot = c.rock.sdf.project(r.pos - n * st + c.rng.unitVector().rejectFrom(n) * 0.8, 0.0)
                shuffle = if (kotlin.math.abs(c.rock.sdf.dist(foot)) < 0.2) foot + c.rock.sdf.gradient(foot) * st - r.pos else Vec3.ZERO
            }
            out.rest(r.copy(pos = r.pos + shuffle), 1.5)
            c.o.extension = 1.0
            val client = nearestOther(c, ADVERT_CM) { it.isFish && it.vel.length() < 0.4 * it.cruiseSpeed + 0.3 }
            if (client != null && threat == null) {
                out.rollExtra = 0.07 * sin(2 * PI * 0.6 * c.t)
                c.o.appendagePhase += c.dt * 2.5
            }
            groomClock.tick(c, threat == null && client == null)
        }
    }

    /**
     * Foray drive (research 12, Esaka 2016): mostly nocturnal, but weak light lowers the day/night contrast, so a
     * shaded ledge or a dim ramp lets it out by day. Light at the ledge is the tank light × (1 − 0.6 × rock cover).
     */
    fun forayDrive(c: Ctx): Double {
        val shade = ledge?.let { c.rock.sdf.cover(it.pos + (it.normal ?: Vec3.Y) * 2.0) } ?: 0.0
        val local = c.p.light * (1 - 0.6 * shade)
        return 1 - 0.85 * com.reeflive.core.geom.smoothstep(0.08, 0.45, local)
    }

    /**
     * Night foraging over its territory (research 12; ADW: never more than ~0.5 m unless disturbed): walks the rock
     * within [FORAY_CM] of the ledge, over cave ceilings and overhang undersides as well as tops (it walks inverted
     * as easily as upright), stops at each spot to pick the surface with the small chelae, then moves on.
     */
    inner class NightForage : Behavior("crawl", "Foraging") {
        private var wp: Rest? = null
        private var next = 0.0
        private var dwellUntil = -1.0
        override val label: String get() = if (c.t < dwellUntil) "Picking at the rock" else "Foraging"
        override fun score(c: Ctx) = 0.45 * forayDrive(c)
        override fun enter(c: Ctx) { next = 0.0; nav.reset() }
        private fun pick(c: Ctx, around: Vec3, radius: Double) {
            val hit = c.rockSurfacePoint(around, radius, minNormalY = -1.0)
            wp = if (hit != null) Rest(hit.first + hit.second * shrimpStance(c.o), c.o.heading, hit.second, 0)
            else Rest(sandSpot(c, (around.x - radius)..(around.x + radius), 8.0..(c.rock.widthCm - 3), shrimpStance(c.o)), c.o.heading, Vec3.Y, 0)
            next = c.t + c.rng.range(30.0, 80.0)
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val home = hang(c).pos
            if (wp == null || c.t > next) pick(c, home, FORAY_CM)
            val w = wp!!
            if (c.t < dwellUntil) {
                out.crawl = true; out.target = null; out.surfaceOffset = shrimpStance(c.o)
                c.o.mouth = max(c.o.mouth, 0.5 + 0.3 * sin(2 * PI * 1.5 * c.t))
                return
            }
            when (nav.drive(c, out, w.pos, w.up, c.o.cruiseSpeed, arrive = 1.2, canHop = c.rock.envDist(c.o.pos) > shrimpStance(c.o) + 1.5)) {
                ShrimpNav.Status.ARRIVED -> { dwellUntil = c.t + c.rng.range(4.0, 15.0); next = dwellUntil + 1.0; wp = null; out.crawl = true; out.surfaceOffset = shrimpStance(c.o) }
                ShrimpNav.Status.STALLED -> pick(c, c.o.pos, 12.0)
                else -> {}
            }
        }
    }

    /** Fish visits to the shrimp and how many it cleaned (research 12: 5.6 % of visits at Taiwanese stations). */
    class VisitStats { var visits = 0; var cleans = 0 }
    val visitStats = VisitStats()
    private val visiting = HashSet<String>()

    /**
     * Rare cleaning (research 12, NTOU thesis): each fish *visit* (a fish arriving within 6 cm and slowing) is one
     * roll at [CLEAN_PER_VISIT]; no recruitment dance, no joint cleaning. Parasite work is weaker by day
     * (Vaughan 2018: on-fish parasites fell only in night trials), [DAY_PARASITE_FACTOR].
     */
    inner class Clean : Behavior("clean", "Cleaning a fish") {
        private var client: Organism? = null
        private var nextPick = 0.0
        override fun score(c: Ctx): Double {
            val near = nearestOther(c, 6.0) { it.isFish && it.brainKind != BrainKind.HAWK && it.vel.length() < 0.3 * it.cruiseSpeed + 0.5 && it.pos.distanceTo(c.o.pos) < 5 }
            // count each arrival once; a fish that leaves (> 9 cm) ends its visit
            visiting.retainAll { id -> c.sim.organisms.any { it.id == id && it.alive && it.pos.distanceTo(c.o.pos) < 9 } }
            if (near != null && !c.o.molting && visiting.add(near.id)) {
                visitStats.visits++
                if (client == null && c.t >= nextPick && c.rng.chance(CLEAN_PER_VISIT)) { client = near; visitStats.cleans++ }
            }
            val cl = client
            if (cl != null && (!cl.alive || cl.pos.distanceTo(c.o.pos) > 9)) client = null
            return if (client != null) 0.6 else 0.0
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val cl = client ?: run { finished = true; return }
            // stays on its surface (it "usually stays in contact with the substrate", ADW) and turns to the client
            val up = c.o.up
            out.rest(Rest(c.o.pos, (cl.pos - c.o.pos).rejectFrom(up).normalized(c.o.heading), up, c.o.posture), 2.0)
            c.o.appendagePhase += c.dt * 6
            // picks with the small chelae of P1-P2 and passes food to the mouth; the big claws stay down
            c.o.mouth = 0.6 + 0.4 * sin(2 * PI * 1.5 * c.t)
            c.o.clawDisplay = 0.0
            if (elapsed > 4) {
                client = null
                val f = if (c.p.night) 1.0 else DAY_PARASITE_FACTOR
                cl.parasiteLoad = max(0.0, cl.parasiteLoad - 0.3 * f)
                c.o.hunger = max(0.0, c.o.hunger - 0.03)
                nextPick = c.t + 120
                finished = true
            }
        }
    }
}

/** A slow fish within this distance of the hanging banded shrimp gets the antenna wave and sway, cm (DESIGN ESTIMATE). */
private const val ADVERT_CM = 12.0

// =====================================================================================
// Skunk cleaner shrimp (Lysmata amboinensis): station, rocking advert, climbs onto clients
// =====================================================================================
class CleanerShrimpBrain(c: Ctx) : SpeciesBrain(c) {
    val anchor: Anchor? = c.rock.anchors["STATION_SHRIMP"]
    private val anchorPos: Vec3 = anchor?.pos ?: Vec3(106.0, 28.0, 16.0)
    val nav = ShrimpNav(o)
    private var perch: Rest? = null

    /**
     * Its station: a rock face near the station anchor that looks out over open water toward the front (clients must
     * reach it, and it is a "coral head or cave mouth", research video/invert_cleaner_shrimp) — not the anchor point
     * itself, which sits in open water 4 cm off the rock. Other skunk cleaners take the next spot along (pairs and
     * groups share a station, Wikipedia/Aquarium of the Pacific [M]). The body lies head-up/out along the face.
     */
    fun sit(c: Ctx): Rest {
        perch?.let { return it }
        val taken = c.sim.organisms.filter { it !== o && it.brainKind == BrainKind.CLEANER_SHRIMP }
            .mapNotNull { (c.sim.agentOf(it)?.brain as? CleanerShrimpBrain)?.homeSpot }
        val r = shrimpPerch(c, anchorPos, 14.0, 1, taken, Vec3(0.0, 0.6, 0.8),
            accept = { _, n -> n.y > -0.3 && n.z > -0.35 },
            score = { q, n -> q.distanceTo(anchorPos) + 4.0 * max(0.0, -n.z) + 2.0 * c.rock.sdf.cover(q + n * 3.0) })
            ?: hangPose(c, anchor, Vec3(106.0, 28.0, 16.0), inverted = false).let { h -> Rest(h.pos, Vec3.Y.rejectFrom(h.up!!).normalized(h.heading), h.up, 1) }
        perch = r
        // clients pose in open water just in front of the shrimp
        station.pos = c.safe(r.pos + (r.up ?: Vec3.Y) * 3.5, 2.0)
        return r
    }
    /** Surface point of its station (for spacing and the banded shrimp's keep-apart rule). */
    val homeSpot: Vec3? get() = perch?.let { it.pos - (it.up ?: Vec3.Y) * shrimpStance(o) }
    val station = CleaningStation(o, anchorPos + (anchor?.normal ?: Vec3.Z) * 4.0, isShrimp = true)

    init {
        c.p.stations += station
        install(Station(), Climb(), ShrimpForage(this, nav, 25.0, 20.0, "Grabbing food"), MoltHide(this) { sit(it) }, TailFlip(this, nav) { sit(it) })
    }

    override fun restSpot(c: Ctx, night: Boolean) = sit(c)

    /** Approaches are judged every tick, whatever the shrimp is doing (a fish arriving mid-clean still gets the signal). */
    override fun preTick(c: Ctx) {
        super.preTick(c)
        if (perch == null) sit(c)
        approaches(c)
        val rocking = c.t < rockUntil
        c.o.legRock = if (rocking) 1.0 else max(0.0, c.o.legRock - c.dt * 2)
        c.o.groom = max(0.0, c.o.groom - c.dt * 2)
    }

    /** Approach statistics (06 §5.6 #16): approaches, leg-rocks and cleans offered, for predators and others. */
    class ApproachStats { var approaches = 0; var rocks = 0; var cleans = 0 }
    val predatorStats = ApproachStats()
    /** Antenna taps before a clean, and clients that left during the tap (research 12: advert → antenna → clean). */
    var antennaTaps = 0; private set
    var tapWithdrawals = 0; private set
    var nightCleans = 0; private set
    companion object {
        const val APPROACH_CM = 10.0
        /** Front-leg rocking rate, Hz: "rapid back and forth" with no measured rate (research 17; DESIGN ESTIMATE). */
        const val ROCK_HZ = 3.0
        /** Extra leg-rocking in darkness (research 12, Caves 2019: dim light elicits rocking; direction S, gain DESIGN ESTIMATE). */
        @com.reeflive.core.sim.Ev(com.reeflive.core.sim.Evidence.DESIGN_ESTIMATE, "12 Caves 2019") const val DIM_ROCK_GAIN = 0.6
        /** Antenna-tap phase before mounting the client, seconds (sequence S; duration D). */
        const val TAP_MIN_S = 1.0
        const val TAP_MAX_S = 2.0
        /** Side-to-side body sway of the station "dance" while a fish is about, Hz and roll (rad) (Aquarium of the Pacific [M]; numbers DESIGN ESTIMATE). */
        const val SWAY_HZ = 0.6
        const val SWAY_ROLL = 0.06
        /** Swim speed onto a client, cm/s: Lysmata swims up to and onto the fish (DESIGN ESTIMATE). */
        const val MOUNT_CM_S = 8.0
    }

    /** Leg-rock probability: predators 0.51, others 0.12 in full light (S:Caves19), up to ×1.6 in the dark. */
    fun rockChance(c: Ctx, predator: Boolean): Double =
        ((if (predator) 0.51 else 0.12) * (1 + DIM_ROCK_GAIN * (1 - dayLight(c)))).coerceAtMost(0.95)
    val otherStats = ApproachStats()
    private val near = HashSet<String>()
    private var rockUntil = -1e9

    /** Hawk and dottyback count as predators of a cleaner shrimp (S:Caves19 signalling context). */
    fun isPredator(f: Organism) = f.brainKind == BrainKind.HAWK || f.brainKind == BrainKind.DOTTYBACK || f.brainKind == BrainKind.EEL

    /**
     * A fish arriving within [APPROACH_CM] of the station: rock the legs (predators 0.51, others 0.12;
     * S:Caves19 51 % vs 12 %) and offer a cleaning (predators 0.28, others 0.51; 06 §5.3).
     */
    private fun approaches(c: Ctx) {
        if (!station.open) return
        val inside = HashSet<String>()
        for (f in c.p.neighbors(station.pos, APPROACH_CM + 4)) {
            if (!f.isFish || !f.alive || f.pos.distanceTo(station.pos) > APPROACH_CM) continue
            inside += f.id
            if (!near.add(f.id)) continue
            val pred = isPredator(f)
            val st = if (pred) predatorStats else otherStats
            st.approaches++
            if (c.rng.chance(rockChance(c, pred))) {
                st.rocks++
                rockUntil = c.t + c.rng.range(1.0, 3.0)
                c.sim.emit(SimEvent.LegRock(c.o.id, f.id, c.sim.simTimeSec))
            }
            if (station.client == null && c.rng.chance(if (pred) 0.28 else 0.51)) {
                st.cleans++
                station.client = f; station.clientReady = true
                f.poseFor = c.o; f.poseUntil = c.t + 25.0
            }
        }
        near.retainAll { id -> id in inside || c.sim.organisms.any { it.id == id && it.alive && it.pos.distanceTo(station.pos) < APPROACH_CM + 4 } }
    }

    val groomClock = GroomClock()

    inner class Station : Behavior("station", "Advertising at station") {
        override val label: String get() = when {
            nav.swimming -> "Swimming back to its station"
            !home -> "Walking back to its station"
            groomClock.grooming -> "Grooming its antennae"
            else -> "Advertising at station"
        }
        private var home = true
        override fun score(c: Ctx) = 0.5
        override fun enter(c: Ctx) { nav.reset() }
        override fun update(c: Ctx, out: SteerIntent) {
            val r = sit(c)
            if (c.o.pos.distanceTo(r.pos) > 2.0 || nav.swimming) {
                home = nav.drive(c, out, r.pos, r.up, c.o.cruiseSpeed * 1.5, arrive = 0.8, swimCmS = ShrimpNav.HOP_CM_S * 1.4) == ShrimpNav.Status.ARRIVED
                station.advertising = false
                if (!home) return
            }
            home = true
            out.rest(r, 2.0)
            val client = c.p.neighbors(station.pos, 30.0).any { it.isFish && it.alive && it.brainKind != BrainKind.EEL }
            station.advertising = client
            groomClock.tick(c, station.client == null && threat == null && c.p.neighbors(station.pos, APPROACH_CM).none { it.isFish && it.alive })
            val rocking = c.t < rockUntil
            // the signal is the white front legs rocking with the antennae lashing (research 17/19, Caves 2019), drawn by
            // the renderer from legRock. The body stays planted: only a slight side-to-side lean while a fish is close
            // (the "dance", Aquarium of the Pacific [M]); no measured body motion exists, so it is kept small
            out.rollExtra = when {
                rocking -> SWAY_ROLL * 0.5 * sin(2 * PI * ROCK_HZ * c.t)
                client && c.p.neighbors(station.pos, APPROACH_CM + 5).any { it.isFish && it.alive } -> SWAY_ROLL * sin(2 * PI * SWAY_HZ * c.t)
                else -> 0.0
            }
            // antennae sweep faster while a client is about (advertising)
            c.o.appendagePhase += c.dt * (if (rocking) 4.0 else if (client) 2.0 else 0.5)
        }
    }

    /**
     * Antenna tap (1–2 s, beside the client), then climb onto it and pick along its body for 8–20 s. A client that
     * leaves during the tap is a withdrawal, not a cleaning. Works day and night (research 12, Vaughan 2018).
     * The shrimp swims across to the client at [MOUNT_CM_S] (it does not jump), rides it, and swims home afterwards
     * through [Station]'s navigation.
     */
    inner class Climb : Behavior("climb", "Cleaning a client") {
        private var dur = 12.0
        private var tap = 1.5
        private var mounted = false
        override val committed: Boolean get() = elapsed < dur + tap
        override fun score(c: Ctx): Double {
            val cl = station.client ?: return 0.0
            return if (station.clientReady && cl.alive && cl.pos.distanceTo(c.o.pos) < 15) 0.9 else 0.0
        }
        override fun enter(c: Ctx) { dur = c.rng.range(8.0, 20.0); tap = c.rng.range(TAP_MIN_S, TAP_MAX_S); antennaTaps++; mounted = false }
        override fun update(c: Ctx, out: SteerIntent) {
            val cl = station.client
            if (cl == null || !cl.alive) { c.o.ridingOn = null; finished = true; station.release(); return }
            // the client poses for this shrimp: it may still name the wrasse that last cleaned it, and then its
            // VisitCleaner let go of the shrimp's station as it began to pose (a clean-visit loop every 1.5 s)
            cl.poseFor = c.o
            if (elapsed < tap) {
                // antennae on the client's flank before any physical cleaning
                out.rest(Rest(c.o.pos, (cl.pos - c.o.pos).normalized(c.o.heading), c.o.up, 1), 2.0)
                c.o.appendagePhase += c.dt * 14
                cl.poseUntil = max(cl.poseUntil, c.t + 0.5)
                if (cl.pos.distanceTo(c.o.pos) > 15) { tapWithdrawals++; station.release(); finished = true }
                return
            }
            val k = ((elapsed - tap) / dur).coerceIn(0.0, 1.0)
            // walks back along the client from behind its eyes toward the tail, standing on the curve of its back
            val along = 0.15 + 0.4 * k
            val onBack = cl.pos - cl.heading * (cl.lengthCm * along) +
                cl.up * (cl.lengthCm * cl.species.depthRatio * 0.5 * ShrimpLimbs.bodyProfile(along) + 0.8 * shrimpStance(c.o))
            val gap = onBack - c.o.pos
            if (!mounted && gap.length() > 0.6) {
                // swim across head first, legs reaching for the fish
                val step = gap.clampLength(MOUNT_CM_S * c.dt)
                out.place(c.o.pos + step, gap.normalized(c.o.heading), Vec3.Y.lerp(cl.up, 0.5).normalized(Vec3.Y), 40.0)
                cl.poseUntil = max(cl.poseUntil, c.t + 0.5)
                c.o.appendagePhase += c.dt * 6
                return
            }
            mounted = true
            c.o.ridingOn = cl
            out.place(onBack, cl.heading, cl.up, 12.0)
            out.posture = 1
            c.o.mouth = 0.5 + 0.5 * sin(2 * PI * 2 * c.t)
            c.o.appendagePhase += c.dt * 6
            cl.poseUntil = max(cl.poseUntil, c.t + 0.5)
            if (elapsed > dur + tap) { if (c.p.night) nightCleans++; finishClean(c, cl, isShrimp = true); cl.poseUntil = c.t; station.release(); finished = true }
        }
        override fun exit(c: Ctx) { nav.reset(); c.o.ridingOn = null }
    }
}

// =====================================================================================
// Hermit crabs: walk rock and sand, pick/graze, scavenge, retreat into the shell
// =====================================================================================
class HermitBrain(c: Ctx) : SpeciesBrain(c) {
    private val start = c.rock.anchors.ofType("GRAZE").let { g -> if (g.isEmpty()) Vec3(60.0, 4.0, 38.0) else g[o.rng.nextInt(g.size)].pos }

    init {
        install(CrawlWander(this, { it.o.homePos }, 25.0, { 0.35 }, "Wandering"), Pick(), Scavenge(this, 35.0), Retreat(), ShellSwapBehavior(this))
    }

    /** Walking legs, chelae, shell roll and retraction (sim/LimbRig.kt HermitLegs). */
    val legs: HermitLegs? = o.limbRig as? HermitLegs

    /** Until this sim time the hermit does not settle to pick (it walks off a spot where its body would not sit). */
    private var unwedgeUntil = -1e9

    override fun preTick(c: Ctx) {
        super.preTick(c)
        // wedged under an overhang or in a slot where the shell has no clean rest (sim/CrawlerSeat.kt): give up the
        // spot or waypoint and walk on, rather than hop in place
        o.seat?.let { st ->
            if (st.wedged) {
                st.clearWedge()
                unwedgeUntil = c.t + UNWEDGE_S
                utility.current?.let { if (it.name == "pick" || it.name == "crawl") it.finished = true }
            }
        }
        val l = legs ?: return
        // claws work the surface while it stands picking; antennules flick nonstop while it follows a food scent
        when (utility.current?.name) {
            "pick" -> if (o.vel.length() < 0.5) l.pick = 1.0
            "scavenge" -> { l.searching = true; if (o.vel.length() < 0.5) l.pick = 0.8 }
        }
    }

    override fun restSpot(c: Ctx, night: Boolean): Rest {
        val sp = c.rockSurfacePoint(o.homePos.takeIf { it != Vec3.ZERO } ?: start, 6.0, minNormalY = 0.3)
        return sp?.let { (p, n) -> Rest(p + n * surfaceOffset(c), Vec3.X.rejectFrom(n).normalized(Vec3.Z), n, 0) }
            ?: Rest(Vec3(start.x, c.rock.sandTopCm + surfaceOffset(c), start.z), Vec3.X, Vec3.Y)
    }

    inner class Pick : Behavior("pick", "Picking at rock") {
        private var spot: Pair<Vec3, Vec3>? = null
        private var next = 0.0
        override fun score(c: Ctx) = if (c.t < unwedgeUntil) 0.0 else 0.3 + 0.3 * c.o.hunger
        override fun enter(c: Ctx) { spot = c.rockSurfacePoint(c.o.pos, 12.0, minNormalY = 0.0) }
        override fun update(c: Ctx, out: SteerIntent) {
            // stay on the surface even on the frame it gives up (a hermit never floats free of the rock)
            out.crawl = true; out.surfaceOffset = surfaceOffset(c)
            val s = spot ?: run { finished = true; return }
            if (c.o.pos.distanceTo(s.first) > 1.5) { out.target = s.first; out.speed = c.o.cruiseSpeed; return }
            if (c.t > next) {
                next = c.t + 2
                c.o.hunger = max(0.0, c.o.hunger - 0.004)
                c.sim.addGrazing(c.sim.grazeRemovalPerMin(c.o) / 15)
                c.o.mouth = 1.0
            }
            c.o.appendagePhase += c.dt * 5
            if (elapsed > c.rng.range(20.0, 60.0)) finished = true
        }
    }

    inner class Retreat : Behavior("retreat", "Hiding in shell") {
        private var dur = 15.0
        override val committed: Boolean get() = elapsed < dur
        override fun score(c: Ctx) = if (threat != null || startledRecently && startleMag > 0.1) 0.95 else 0.0
        override fun enter(c: Ctx) { dur = c.rng.range(10.0, 30.0); startleAt = -1e9 }
        override fun update(c: Ctx, out: SteerIntent) {
            out.rest(Rest(c.o.pos, c.o.heading, c.o.up, 6), 1.0)
            c.o.extension = 0.0
            if (elapsed > dur) { finished = true; c.o.extension = 1.0 }
        }
        override fun exit(c: Ctx) { c.o.extension = 1.0 }
    }

    companion object {
        /** Seconds a hermit that was wedged walks on before it settles to pick again. */
        const val UNWEDGE_S = 20.0
    }
}

// =====================================================================================
// Black brittle star (Ophiocoma erinaceus, research 17 §2): under rock by day with 1–3 arm tips out, rows out at night
// or on a food scent (arms snake out first, then the disc), loops food in along an arm, runs for shelter when
// disturbed, sheds an arm tip when bitten and regrows it. The disc never turns: BrittleArms reassigns the lead arm.
// =====================================================================================
class BrittleStarBrain(c: Ctx) : SpeciesBrain(c) {
    val home: Vec3 = c.rock.anchors.pos("BRITTLE_HOME", Vec3(36.0, 5.5, 10.4))
    /** The five-arm gait rig (sim/LimbRig.kt); the behaviours only say which arms reach where. */
    val arms: BrittleArms = o.limbRig as BrittleArms
    /** Arm-tip photoreception: withdraw from a light step or a shadow (ai/BrittleLight.kt; AIZ01 analogy). */
    val lightShy = LightWithdraw(this)
    /** Arm tips this individual leaves out of its hide by day (1–3; research 17 §2.3). */
    val tipsOut: Int = 1 + Math.floorMod(o.id.hashCode(), 3)

    init {
        install(HideUnder(), Roam(), ArmFeed(), RaceToFood(), Retract(), lightShy)
    }

    /** Centre-arm reassignments while travelling (coordinated arm roles; Astley 2012). */
    val leadArmSwitches: Int get() = arms.leadSwitches
    /** Food items looped by an arm and passed to the mouth while the disc stayed put. */
    var armFeeds = 0; private set
    /** Emergences where the arms were already out (mean reach > 0.75) before the disc left the hide. */
    var armsFirstEmergences = 0; private set
    var emergences = 0; private set
    /** Arms shed to an attacker (autotomy). */
    var autotomies = 0; private set
    private var mouthDir: Vec3? = null

    override fun preTick(c: Ctx) {
        super.preTick(c)
        arms.regrow(c.dt, o.lengthCm)
    }

    /** The disc keeps whatever heading it has (a brittle star has no front to turn). */
    private fun flatHeading() = o.heading.horizontal().normalized(Vec3.X)

    override fun restSpot(c: Ctx, night: Boolean) = Rest(Vec3(home.x, c.sim.sand.surfaceY(home.x, home.z) + 0.4, home.z), flatHeading(), Vec3.Y, posture = 3)

    /** The open side of the hide (most clearance, a little toward the front glass), where the day arm tips poke out. */
    fun caveMouth(c: Ctx): Vec3 = mouthDir ?: run {
        val r = restSpot(c, false).pos
        var best = Vec3.Z
        var bs = -1e9
        for (i in 0 until 16) {
            val a = 2 * PI * i / 16
            val d = Vec3(kotlin.math.cos(a), 0.0, kotlin.math.sin(a))
            val s = c.rock.sdf.dist(r + d * (0.4 * c.L)) + c.rock.sdf.dist(r + d * (0.2 * c.L)) + 0.3 * d.z
            if (s > bs) { bs = s; best = d }
        }
        best.also { mouthDir = it }
    }

    inner class Roam : CrawlWander(this, { home }, 40.0, { c -> 0.55 * c.activity }, "Roaming") {
        override fun update(c: Ctx, out: SteerIntent) { super.update(c, out); out.armGait = ARMS; arms.snake = 0.15 }
    }

    inner class HideUnder : Behavior("hide", "Hiding under rock") {
        override fun score(c: Ctx) = 0.65 * (1 - c.activity) + 0.1
        override fun update(c: Ctx, out: SteerIntent) {
            val r = restSpot(c, false)
            if (c.o.pos.distanceTo(r.pos) > 2.5) {
                out.crawl = true; out.armGait = ARMS; out.target = r.pos; out.speed = c.o.cruiseSpeed * 1.5; out.surfaceOffset = 0.4
                c.o.extension = 1.0
                return
            }
            out.rest(r, 1.0)
            // disc wedged under the rock, 1–3 arm tips out of the mouth of the hide, sweeping slowly (research 17 §2.3)
            arms.outArms = tipsOut; arms.outDir = caveMouth(c); arms.snake = 0.25
            c.o.extension = (0.35 + 0.15 * sin(c.t * 0.4 + c.o.appendagePhase)) * LightWithdraw.dayReach(c.p.light)
        }
    }

    /**
     * Arm feeding (research 17 §2.4): the disc stays put while the arm nearest the item reaches out, loops its tip
     * round it and passes it back along the arm to the mouth (Chartok 1983: a particle moved 10 cm along an arm in
     * 8–15 s). The food item itself rides the arm, so the renderer shows it travelling in.
     */
    inner class ArmFeed : Behavior("armfeed", "Looping food in with an arm") {
        private var f: FoodParticle? = null
        private var arm = -1
        private var start = Vec3.ZERO
        private var reachS = 2.0
        private var carryS = 5.0
        override val committed: Boolean get() = arm >= 0 && elapsed < reachS + carryS
        override fun score(c: Ctx): Double {
            if (c.o.hunger < 0.1) return 0.0
            f = c.food.nearest(c.o.pos, ARM_REACH * c.L) { (it.settled || it.pos.y < c.rock.sandTopCm + 3) && (it.claimedBy == null || it.claimedBy == c.o.id) }
            return if (f != null) 0.9 else 0.0
        }
        override fun enter(c: Ctx) { arm = -1 }
        override fun update(c: Ctx, out: SteerIntent) {
            val ff = f
            if (ff == null || ff.eaten) { finished = true; return }
            out.rest(Rest(c.o.pos, c.o.heading, c.o.up, c.o.posture), 1.0)   // the disc does not move
            c.o.extension = 1.0
            c.o.focusId = null
            if (arm < 0) {
                arm = arms.nearestArm(c.o, ff.pos - c.o.pos)
                start = ff.pos
                val d = ff.pos.distanceTo(c.o.pos)
                reachS = c.rng.range(1.5, 3.0) * (0.5 + d / (ARM_REACH * c.L))
                carryS = c.rng.range(8.0, 15.0) * maxOf(d, 2.0) / 10.0
            }
            arms.feedArm = arm
            arms.feedAt = start
            arms.outDir = start - c.o.pos
            if (elapsed < reachS) {
                arms.feedLoop = com.reeflive.core.geom.smoothstep(0.4 * reachS, reachS, elapsed)
                return
            }
            arms.feedLoop = 1.0
            // held in the loop and passed mouthward along the arm
            ff.claimedBy = c.o.id; ff.settled = true; ff.vel = Vec3.ZERO
            val prog = ((elapsed - reachS) / carryS).coerceIn(0.0, 1.0)
            ff.pos = start.lerp(c.o.pos + c.o.up * 0.3, prog)
            if (prog >= 1.0) { c.bite(ff); armFeeds++; arm = -1; finished = true }
        }
        override fun exit(c: Ctx) { f?.let { if (!it.eaten && it.claimedBy == c.o.id) it.claimedBy = null }; arm = -1 }
    }

    /**
     * Food rush on a scent (research 17 §2.4): the scent reaches it after [SCENT_DELAY_S] + distance / [SCENT_CM_S]
     * (Chartok 1983: O. anaglyptica active about 2 min after food went in). From the hide the arms snake out toward
     * the food first and the disc follows a few seconds later; then it rows over at burst speed.
     */
    inner class RaceToFood : Behavior("scavenge", "Racing to food") {
        private var f: FoodParticle? = null
        private var emergeUntil = 0.0
        private var counted = true
        private var lastSeen = 0.0
        private fun smelt(c: Ctx, p: FoodParticle) = (p.settled || p.pos.y < c.rock.sandTopCm + 6) &&
            c.t - p.bornAt > SCENT_DELAY_S + p.pos.distanceTo(c.o.pos) / SCENT_CM_S
        override fun score(c: Ctx): Double {
            if (c.o.hunger < 0.12) return 0.0
            f = c.findFood(50.0, los = false) { smelt(c, it) }
            return if (f != null) 0.55 + 0.35 * c.o.hunger else 0.0
        }
        override fun enter(c: Ctx) {
            lastSeen = c.t
            val hidden = c.o.posture == 3 || c.o.pos.distanceTo(restSpot(c, false).pos) < 3.0
            emergeUntil = if (hidden) c.t + c.rng.range(5.0, 10.0) else c.t
            counted = !hidden
        }
        override fun update(c: Ctx, out: SteerIntent) {
            var ff = f
            if (ff == null || ff.eaten) { ff = c.findFood(50.0, los = false) { smelt(c, it) }; f = ff }
            if (ff == null) { if (c.t - lastSeen > 3) finished = true; return }
            lastSeen = c.t
            arms.outDir = ff.pos - c.o.pos
            if (c.t < emergeUntil) {
                // arms first: the disc stays in the hide while all five arms snake out toward the scent
                out.rest(Rest(c.o.pos, c.o.heading, c.o.up, c.o.posture), 1.0)
                arms.snake = 1.0
                return
            }
            if (!counted) { counted = true; emergences++; if (arms.meanReach > 0.75) armsFirstEmergences++ }
            out.crawl = true; out.armGait = ARMS
            out.target = ff.pos; out.speed = c.o.burstSpeed; out.urgency = 0.8; out.arriveRadius = 1.0
            out.surfaceOffset = surfaceOffset(c)
            arms.snake = 0.3
            c.o.extension = 1.0
            // within an arm's reach the arm takes over (ArmFeed)
            if (c.o.pos.distanceTo(ff.pos) < 0.8 * ARM_REACH * c.L) finished = true
        }
    }

    /**
     * Disturbed (tap, attack, a predator close): every arm whips in, then, if it is out in the open, it rows back to
     * its hide at burst speed (BIOS 2014: brittle stars in a lit arena ran for shelter under rocks).
     */
    inner class Retract : Behavior("retract", "Pulling arms in and fleeing") {
        private var run = false
        override val committed: Boolean get() = elapsed < 1.2 || run
        override fun score(c: Ctx) = if (startledRecently || threat != null) 0.9 else 0.0
        override fun enter(c: Ctx) { startleAt = -1e9; run = c.o.pos.distanceTo(restSpot(c, false).pos) > 4.0 }
        override fun update(c: Ctx, out: SteerIntent) {
            if (elapsed < 1.2 || !run) {
                out.rest(Rest(c.o.pos, c.o.heading, c.o.up, c.o.posture), 1.0)
                arms.withdraw = true
                c.o.extension = max(0.1, c.o.extension - c.dt * 2)
                if (!run && elapsed > 5) finished = true
                return
            }
            val r = restSpot(c, false)
            out.crawl = true; out.armGait = ARMS; out.target = r.pos; out.speed = c.o.burstSpeed; out.urgency = 0.9
            out.arriveRadius = 1.0; out.surfaceOffset = 0.4
            c.o.extension = 0.6
            if (c.o.pos.distanceTo(r.pos) < 2.5 || elapsed > 90) { run = false; finished = true }
        }
        override fun exit(c: Ctx) { run = false }
    }

    /** Bitten: the arm nearest the attacker may break off distally (autotomy, research 17 §2.6) and regrows slowly. */
    override fun onAttacked(c: Ctx, from: Vec3, mag: Double, by: Organism?) {
        super.onAttacked(c, from, mag, by)
        if (mag >= 0.5 && c.rng.chance(AUTOTOMY_P)) {
            arms.shed(arms.nearestArm(o, from - o.pos), c.rng.range(0.3, 0.7))
            autotomies++
        }
    }

    companion object {
        const val ARMS = 5
        /** Arm reach as a fraction of total span (disc radius + arm; research 17 §2.1: arms ≈ 4 disc diameters). */
        const val ARM_REACH = 0.45
        /** Scent lag before a food rush: a fixed delay plus a slow plume (s, cm/s) [L]. */
        const val SCENT_DELAY_S = 15.0
        const val SCENT_CM_S = 0.5
        /** Chance a survived bite costs an arm tip [L]; up to 47 % of wild O. echinata carry missing or regrowing arms [M]. */
        const val AUTOTOMY_P = 0.5
    }
}

// =====================================================================================
// Brown feather duster: sessile, crown filters, snaps into tube on shadow/touch/food above
// =====================================================================================
class FeatherDusterBrain(c: Ctx) : SpeciesBrain(c) {
    val tube: Vec3 = c.rock.anchors.pos("DUSTER_TUBE", Vec3(86.0, 4.5, 27.3))
    val crown: Vec3 get() = Vec3(tube.x, c.rock.sandTopCm + 0.6 * o.lengthCm, tube.z)

    init { install(Filter(), Snap(), OutOfTube()) }

    /** The worm has left its tube (InvertEcology, research 11 §8): out of sight, not dead, not merely retracted. */
    inner class OutOfTube : Behavior("abandoned", "Left its tube") {
        override fun score(c: Ctx) = if (c.sim.invertEcology.tubeAbandoned(c.o)) 0.99 else 0.0
        override val committed: Boolean get() = true
        override fun update(c: Ctx, out: SteerIntent) {
            out.rest(restSpot(c, false).copy(posture = 6), 3.0)
            c.o.extension = 0.0
            c.o.visible = false
            if (!c.sim.invertEcology.tubeAbandoned(c.o)) finished = true
        }
        override fun exit(c: Ctx) { c.o.visible = true }
    }

    override fun restSpot(c: Ctx, night: Boolean) = Rest(crown, Vec3.Y, Vec3(0.0, 0.0, 1.0), posture = 0)

    override fun initialPlace(c: Ctx) {
        o.homePos = crown
        o.placeAt(crown, Vec3.Y)
        o.up = Vec3(0.0, 0.0, 1.0)
    }

    override fun onFood(c: Ctx, kind: com.reeflive.core.sim.FoodKind, at: Vec3) {
        super.onFood(c, kind, at)
        // food dosed right above the crown makes it flinch first (04 §15)
        if (Vec3(at.x - crown.x, 0.0, at.z - crown.z).length() < 15) { startleAt = c.t; startleMag = 0.5; startleFrom = at }
    }

    inner class Filter : Behavior("filter", "Filter feeding") {
        private var next = 0.0
        override fun score(c: Ctx) = 0.5
        override fun update(c: Ctx, out: SteerIntent) {
            out.rest(restSpot(c, false), 3.0)
            // a lost crown regrows over 1-2 months (B-6): the fan opens only as far as the regrowth allows
            c.o.extension = min(c.sim.invertEcology.crownOf(c.o), c.o.extension + c.dt / 10)
            c.o.posture = 0
            c.o.appendagePhase += c.dt * 0.8
            if (c.t > next && c.o.extension > 0.7) {
                next = c.t + 1.0
                val f = c.food.nearest(crown + Vec3(0.0, 1.0, 0.0), 8.0) { !it.settled }
                if (f != null && c.o.hunger > 0.05) c.bite(f)
                c.o.hunger = max(0.0, c.o.hunger - 0.8 * com.reeflive.core.sim.HealthModel.hungerRatePerMin(c.o, c.sim.water.s.tempC) / 60 * (c.sim.water.s.detritus / 0.2).coerceIn(0.3, 1.2))
            }
        }
    }

    inner class Snap : Behavior("snap", "Retracted into tube") {
        private var dur = 30.0
        override val committed: Boolean get() = elapsed < dur
        override fun score(c: Ctx): Double {
            if (startledRecently || c.t - shadowAt < 0.6) return 0.95
            val touch = c.p.neighbors(crown, 6.0).any { it !== c.o && it.isFish && it.pos.distanceTo(crown) < 3 + 0.3 * it.collisionLength }
            return if (touch) 0.95 else 0.0
        }
        override fun enter(c: Ctx) { dur = c.rng.range(20.0, 60.0); startleAt = -1e9; shadowAt = -1e9 }
        override fun update(c: Ctx, out: SteerIntent) {
            out.rest(restSpot(c, false).copy(posture = 6), 3.0)
            c.o.extension = max(0.0, c.o.extension - c.dt * 8)
            if (elapsed > dur) finished = true
        }
    }
}

// =====================================================================================
// Nassarius snails: buried with siphon up, erupt and race to food, re-bury
// =====================================================================================
class NassariusBrain(c: Ctx) : SpeciesBrain(c) {
    private var buryAt: Vec3 = Vec3.ZERO
    /** Food cue → emergence (research 11 §5 sequence: buried → cue response → travel → feed bout → rebury). */
    private var cueAt = -1e9
    private var emergeAt = -1e9
    /** Feed bouts completed and their durations in s (tests / UI). */
    val boutLengths = ArrayList<Double>()
    var cueResponses = 0; private set
    /** Latency of the last cue response, s. */
    var lastLatencyS = Double.NaN; private set

    private val bout = FeedBout()

    init { install(Buried(), Erupt(), bout, CrawlWander(this, { it.o.homePos }, 20.0, { c -> 0.15 * c.activity + 0.05 }, "Roaming the sand")) }

    /**
     * A food cue: emergence after a latency that shortens the longer the snail has fasted (Lucena 2012 direction;
     * values DESIGN ESTIMATE): [LATENCY_MIN_S]–[LATENCY_MAX_S] × (1.2 − hunger).
     */
    override fun onFood(c: Ctx, kind: com.reeflive.core.sim.FoodKind, at: Vec3) {
        super.onFood(c, kind, at)
        if (c.t - cueAt < 60) return
        cueAt = c.t
        lastLatencyS = cueLatency(c.o.hunger, c.rng.nextDouble())
        emergeAt = c.t + lastLatencyS
        cueResponses++
    }

    companion object {
        const val LATENCY_MIN_S = 20.0
        const val LATENCY_MAX_S = 240.0
        /** Buried pose: shell centre this far below the sand top (× length) and the siphon tilt above horizontal (DESIGN ESTIMATES). */
        const val BURY_FRAC = 0.15
        const val SIPHON_TILT_DEG = 60.0
        /** Feed bout lognormal: mean 6 min, ~3/4 of bouts ≤ 8 min (ANALOGY, N. vibex field bouts, Lucena 2012). */
        @com.reeflive.core.sim.Ev(com.reeflive.core.sim.Evidence.ANALOGY, "11 §5 Lucena 2012") const val BOUT_MEAN_MIN = 6.0
        const val BOUT_SIGMA = 0.75
        fun cueLatency(hunger: Double, u: Double): Double =
            (LATENCY_MIN_S + (LATENCY_MAX_S - LATENCY_MIN_S) * u) * (1.2 - hunger.coerceIn(0.0, 1.0))
        /** Bout length in s from a standard normal draw [z]. */
        fun boutSeconds(z: Double): Double {
            val mu = kotlin.math.ln(BOUT_MEAN_MIN) - BOUT_SIGMA * BOUT_SIGMA / 2
            return (kotlin.math.exp(mu + BOUT_SIGMA * z) * 60).coerceIn(30.0, 25 * 60.0)
        }
    }

    override fun restSpot(c: Ctx, night: Boolean): Rest {
        val p = if (buryAt == Vec3.ZERO) o.homePos else buryAt
        return Rest(Vec3(p.x, c.sim.sand.surfaceY(p.x, p.z) + 0.1, p.z), Vec3.X, Vec3.Y, posture = 5)
    }

    override fun initialPlace(c: Ctx) {
        o.homePos = sandSpot(c, 10.0..110.0, 24.0..31.0, 0.1)
        buryAt = o.homePos
        o.placeAt(o.homePos, Vec3.X)
    }

    inner class Buried : Behavior("buried", "Buried, siphon up") {
        private var sink = 0.0
        override fun score(c: Ctx) = 0.45
        override fun enter(c: Ctx) {
            val p = c.o.pos
            // only bury in open sand; a snail up on the rock walks back down to its sand patch first
            buryAt = if (p.y < c.sim.sand.surfaceY(p.x, p.z) + 1.5 && c.rock.sdf.dist(p) > 2.0) p else o.homePos
            sink = 0.0
        }
        override fun update(c: Ctx, out: SteerIntent) {
            if (sink == 0.0 && c.o.pos.distanceTo(buryAt) > 2.0) {
                out.crawl = true; out.target = buryAt; out.speed = c.o.cruiseSpeed; out.surfaceOffset = surfaceOffset(c)
                return
            }
            sink = min(1.0, sink + c.dt / 3)
            val r = restSpot(c, c.p.night)
            if (sink < 1) out.rest(r.copy(posture = 0), 1.0)
            else {
                // under the sand with only the siphon out (research video invert_nassarius): the shell lies a little
                // below the surface, nose tilted ~60° up into the current so the siphon tube samples the water
                val fl = c.sim.flow.velocityFast(r.pos).horizontal()
                val h = if (fl.length() > 0.5) (fl * -1.0).normalized(Vec3.X) else c.o.heading.horizontal().normalized(Vec3.X)
                val tilt = Math.toRadians(SIPHON_TILT_DEG)
                val head = h * kotlin.math.cos(tilt) + Vec3.Y * kotlin.math.sin(tilt)
                val up = h * -kotlin.math.sin(tilt) + Vec3.Y * kotlin.math.cos(tilt)
                out.rest(Rest(r.pos.withY(c.sim.sand.surfaceY(r.pos.x, r.pos.z) - BURY_FRAC * c.o.lengthCm), head, up, posture = 5), 1.0)
            }
            c.o.extension = 0.6 + 0.2 * sin(c.t * 0.5 + c.o.appendagePhase)   // siphon
        }
    }

    /** Travel to the food once the cue latency has passed; arriving starts a feed bout instead of an instant bite. */
    inner class Erupt : Behavior("erupt", "Racing to food") {
        private var f: FoodParticle? = null
        override fun score(c: Ctx): Double {
            if (c.o.hunger < 0.1 || c.t < emergeAt || bout.active) return 0.0
            f = c.findFood(40.0, los = false) { it.settled || it.pos.y < c.rock.sandTopCm + 6 }
            val cued = c.t - cueAt < 90 + LATENCY_MAX_S && c.food.any
            return if (f != null || cued) 0.75 else 0.0
        }
        override fun update(c: Ctx, out: SteerIntent) {
            var ff = f
            if (ff == null || ff.eaten) { ff = c.findFood(40.0, los = false) { it.settled || it.pos.y < c.rock.sandTopCm + 6 }; f = ff }
            out.crawl = true; out.speed = c.o.burstSpeed; out.surfaceOffset = surfaceOffset(c); out.urgency = 0.6; out.arriveRadius = 1.0
            out.target = ff?.pos ?: foodAlertPos.withY(c.rock.sandTopCm)
            c.o.extension = 1.0
            if (ff != null && c.o.pos.distanceTo(ff.pos) < 1.5 + 0.3 * c.L) { bout.start(c, ff); finished = true }
            if (ff == null && elapsed > 120) finished = true
        }
    }

    /** Proboscis on the food for a lognormal bout (mean ≈ 6 min), then it leaves and reburies (research 11 §5). */
    inner class FeedBout : Behavior("feedbout", "Feeding with its proboscis") {
        var active = false; private set
        private var food: FoodParticle? = null
        private var dur = 360.0
        private var start = 0.0
        fun start(c: Ctx, f: FoodParticle) { active = true; food = f; dur = boutSeconds(c.rng.gaussian()); start = c.t }
        override fun score(c: Ctx) = if (active) 0.9 else 0.0
        override val committed: Boolean get() = active
        override fun update(c: Ctx, out: SteerIntent) {
            out.rest(Rest(c.o.pos, c.o.heading, c.o.up, 0), 1.0)
            c.o.mouth = 0.6 + 0.4 * sin(c.t * 2.0)
            c.o.extension = 1.0
            // the item is eaten over the bout (one bite at the start of the bout, then nibbling keeps hunger falling)
            food?.let { if (!it.eaten && c.o.pos.distanceTo(it.pos) < 3.0 + 0.3 * c.L) c.bite(it); food = null }
            if (c.t - start >= dur) { active = false; boutLengths += c.t - start; finished = true }
        }
    }
}

// =====================================================================================
// Astrea snails: slow grazing on glass and rock; may flip and get stuck
// =====================================================================================
class AstreaBrain(c: Ctx) : SpeciesBrain(c) {
    /** Upside down since (NaN = upright). There is no death timer after inversion (research 11 §6). */
    var flippedSince = Double.NaN; private set
    val flipped: Boolean get() = !flippedSince.isNaN()
    var rightingAttempts = 0; private set
    var rightings = 0; private set
    private var nextAttempt = 0.0

    /** Sandbox / tests: knock it over now. */
    fun flip(c: Ctx) { flippedSince = c.t; nextAttempt = c.t + c.rng.range(ATTEMPT_MIN_S, ATTEMPT_MAX_S) }

    companion object {
        /** Righting attempts every 10–40 min; success 0.12 on open sand, 0.35 with rock or glass within foot reach (DESIGN ESTIMATE). */
        @com.reeflive.core.sim.Ev(com.reeflive.core.sim.Evidence.DESIGN_ESTIMATE, "11 §6") const val ATTEMPT_MIN_S = 600.0
        const val ATTEMPT_MAX_S = 2400.0
        const val P_RIGHT_OPEN = 0.12
        const val P_RIGHT_ANCHORED = 0.35
    }

    init { install(GlassGraze(this), Flipped()) }

    override fun restSpot(c: Ctx, night: Boolean): Rest = if (o.homePos == Vec3.ZERO) Rest(Vec3(60.0, 0.55 * c.rock.waterLevelCm, c.rock.widthCm - surfaceOffset(c)), Vec3.Y, Vec3(0.0, 0.0, -1.0))
        else Rest(o.homePos, o.heading, o.up)

    override fun initialPlace(c: Ctx) {
        var onFront = o.rng.chance(0.5)
        var p = Vec3(o.rng.range(10.0, 110.0), o.rng.range(10.0, c.rock.waterLevelCm - 6), if (onFront) c.rock.widthCm - surfaceOffset(c) else surfaceOffset(c))
        // never start where rock leans on the back pane
        var tries = 0
        while (c.rock.sdf.dist(p) < 2.0 && tries++ < 20) {
            onFront = onFront || tries > 10
            p = Vec3(o.rng.range(10.0, 110.0), o.rng.range(10.0, c.rock.waterLevelCm - 6), if (onFront) c.rock.widthCm - surfaceOffset(c) else surfaceOffset(c))
        }
        o.homePos = p
        o.placeAt(p, Vec3.Y)
        o.up = if (onFront) Vec3(0.0, 0.0, -1.0) else Vec3.Z
    }

    /**
     * Overturned on the sand (loss of attachment), then repeated righting attempts with foot contact until one works
     * (research 11 §6: overturn, attempts and recovery are separate events; no lethal timeout after inversion).
     */
    inner class Flipped : Behavior("flipped", "Flipped over") {
        override val committed: Boolean get() = flipped
        override fun score(c: Ctx): Double {
            if (flipped) return 0.99
            // fell from the glass onto the sand: small chance per hour to land upside down
            val onSand = c.o.pos.y < c.rock.sandTopCm + 1.5
            if (onSand && c.rng.chance(c.dt / 3600 * 0.3)) { flip(c); return 0.99 }
            return 0.0
        }
        override fun update(c: Ctx, out: SteerIntent) {
            out.rest(Rest(c.o.pos.withY(c.sim.sand.surfaceY(c.o.pos.x, c.o.pos.z) + 0.6), c.o.heading, Vec3.Y * -1.0, 7), 1.0)
            c.o.startle = max(c.o.startle, 0.1)
            if (c.t >= nextAttempt) {
                // a righting attempt: the foot reaches out and twists (visible as an appendage flick)
                rightingAttempts++
                c.o.appendagePhase += 2.0
                val anchored = c.rock.envDist(c.o.pos) < 2.5 && (c.rock.sdf.dist(c.o.pos) < 3.0 || c.o.pos.z < 3.0 || c.o.pos.z > c.rock.widthCm - 3.0)
                if (c.rng.chance(if (anchored) P_RIGHT_ANCHORED else P_RIGHT_OPEN)) { flippedSince = Double.NaN; rightings++; finished = true }
                else nextAttempt = c.t + c.rng.range(ATTEMPT_MIN_S, ATTEMPT_MAX_S)
            }
        }
    }
}

/** Slow film/hair grazing over glass and rock (astrea, asterina). */
class GlassGraze(val b: SpeciesBrain) : Behavior("glassgraze", "Grazing") {
    private var wp = Vec3.ZERO
    private var next = 0.0
    override fun score(c: Ctx) = 0.5
    override fun update(c: Ctx, out: SteerIntent) {
        val r = c.rock
        if (c.t > next || c.o.pos.distanceTo(wp) < 0.8) {
            next = c.t + c.rng.range(60.0, 240.0)
            val o = c.o
            // stay on the current surface type most of the time
            val onGlass = o.pos.z < 1.5 || o.pos.z > r.widthCm - 1.5 || o.pos.x < 1.5 || o.pos.x > r.lengthCm - 1.5
            wp = if (onGlass || c.rng.chance(0.3)) {
                val z = if (o.pos.z > r.widthCm / 2) r.widthCm - 0.3 else 0.3
                // glass spots where rock leans on the pane are not reachable: keep only open glass
                (0 until 8).asSequence().map {
                    Vec3((o.pos.x + c.rng.signed() * 15).coerceIn(3.0, r.lengthCm - 3), (o.pos.y + c.rng.signed() * 10).coerceIn(r.sandTopCm + 2, r.waterLevelCm - 4), z)
                }.firstOrNull { p -> r.sdf.dist(p) > 2.0 && r.sdf.dist((p + o.pos) * 0.5) > 1.0 } ?: (c.rockSurfacePoint(o.pos, 15.0)?.first ?: o.pos)
            } else c.rockSurfacePoint(o.pos, 15.0)?.first ?: o.pos
        }
        out.crawl = true; out.target = wp; out.speed = c.o.cruiseSpeed; out.surfaceOffset = surfaceOffset(c)
        c.sim.addGrazing(c.sim.grazeRemovalPerMin(c.o) * c.dt / 60)
        c.o.hunger = max(0.0, c.o.hunger - 1.6 * com.reeflive.core.sim.HealthModel.hungerRatePerMin(c.o, c.sim.water.s.tempC) * c.dt / 60)
        c.o.nutritionGreens = 1.0
    }
}

// =====================================================================================
// Asterina starfish: tiny, crawl the glass and rock grazing film
// =====================================================================================
class AsterinaBrain(c: Ctx) : SpeciesBrain(c) {
    init { install(CorrelatedWalk(), Scavenge(this, 6.0)) }

    /** Path length travelled (cm), and how much of it was with food nearby (tests). */
    var pathCm = 0.0; private set
    var pathNearFoodCm = 0.0; private set
    var timeNearFoodS = 0.0; private set
    var timeS = 0.0; private set

    companion object {
        /** Heading noise per step (rad, σ) and step period: correlated random walk (ANALOGY, Lohmann 2016 A. anomala; values D). */
        @com.reeflive.core.sim.Ev(com.reeflive.core.sim.Evidence.ANALOGY, "11 §7 Lohmann 2016") const val TURN_SIGMA = 0.35
        const val STEP_S = 4.0
        /** Speed multiplier with food in reach (shorter travel with food present). */
        const val FOOD_SLOWDOWN = 0.35
    }

    /**
     * Correlated random walk over the glass and rock (research 11 §7): the heading persists with small turns rather
     * than a fresh random direction; travel slows to [FOOD_SLOWDOWN] where there is food (settled food within 6 cm, or a
     * rich film), so the star covers less ground near food. Grazes film as it goes.
     */
    inner class CorrelatedWalk : Behavior("crw", "Grazing") {
        private var dir = Vec3.ZERO
        private var nextTurn = 0.0
        override fun score(c: Ctx) = 0.5
        override fun update(c: Ctx, out: SteerIntent) {
            val o = c.o
            val n = o.up.normalized(Vec3.Z)
            if (dir == Vec3.ZERO) dir = o.heading.rejectFrom(n).normalized(Vec3.Y)
            if (c.t >= nextTurn) {
                nextTurn = c.t + STEP_S
                dir = Vec3.rotateAbout(dir, n, TURN_SIGMA * c.rng.gaussian()).rejectFrom(n).normalized(dir)
            }
            val r = c.rock
            var ahead = o.pos + dir * 3.0
            // turn back at the water edges and away from rock leaning on the pane (reflect the walk)
            if (ahead.x < 3 || ahead.x > r.lengthCm - 3 || ahead.y < r.sandTopCm + 1 || ahead.y > r.waterLevelCm - 3) {
                dir = Vec3.rotateAbout(dir, n, PI * (0.6 + 0.8 * c.rng.nextDouble())).rejectFrom(n).normalized(dir)
                ahead = o.pos + dir * 3.0
            }
            val film = c.sim.water.algae.cover(com.reeflive.core.sim.AlgaeGroup.FILM, com.reeflive.core.sim.AlgaeSurface.GLASS)
            val nearFood = c.food.countNear(o.pos, 6.0) > 0 || film > 0.5
            val speed = o.cruiseSpeed * (if (nearFood) FOOD_SLOWDOWN else 1.0)
            out.crawl = true; out.target = ahead; out.speed = speed; out.arriveRadius = 0.5; out.surfaceOffset = surfaceOffset(c)
            val step = o.vel.length() * c.dt
            pathCm += step; timeS += c.dt
            if (nearFood) { pathNearFoodCm += step; timeNearFoodS += c.dt }
            c.sim.addGrazing(c.sim.grazeRemovalPerMin(o) * c.dt / 60)
            o.hunger = max(0.0, o.hunger - 1.6 * com.reeflive.core.sim.HealthModel.hungerRatePerMin(o, c.sim.water.s.tempC) * c.dt / 60)
            o.nutritionGreens = 1.0
        }
    }

    override fun restSpot(c: Ctx, night: Boolean): Rest = Rest(o.pos, o.heading, o.up)

    override fun initialPlace(c: Ctx) {
        val p = Vec3(o.rng.range(5.0, 115.0), o.rng.range(5.0, 20.0), 0.3 + surfaceOffset(c))
        o.homePos = p
        o.placeAt(p, Vec3.Y)
        o.up = Vec3.Z
    }
}

// =====================================================================================
// Generic invertebrate: crawl and scavenge
// =====================================================================================
class GenericInvertBrain(c: Ctx) : SpeciesBrain(c) {
    init { install(CrawlWander(this, { it.o.homePos }, 25.0, { 0.4 }), Scavenge(this, 30.0)) }
    override fun restSpot(c: Ctx, night: Boolean) = Rest(o.homePos, o.heading, o.up)
    override fun initialPlace(c: Ctx) {
        val p = sandSpot(c, 10.0..110.0, 20.0..30.0, surfaceOffset(c))
        o.homePos = p; o.placeAt(p, Vec3.X)
    }
}

// =====================================================================================
// Sally Lightfoot crab (Percnon gibbesi, research 17 §3): flattened into a rock crevice by day, out at dusk and night
// on rock and glass, scuttling sideways on an alternating-tetrapod gait; mostly scrapes film and tears hair algae with
// quick alternating claw strokes and scavenges; dashes back into its crevice at the slightest disturbance, extra shy
// while its new shell hardens after a molt; a large, underfed one may rarely grab a sleeping small fish or a snail at
// night (SIMULATION §9.6).
// =====================================================================================
class SallyCrabBrain(c: Ctx) : SpeciesBrain(c) {
    val crevice: Anchor? = c.rock.anchors[o.hostAnchor ?: "CREVICE_CRAB"] ?: c.rock.anchors["CREVICE_CRAB"] ?: c.rock.anchors["CAVE_SMALL"]
    /**
     * Where it presses in. A cave anchor marks the middle of the cave, open water a crawler cannot reach, so a home
     * more than [HOME_SNAP_CM] off the rock is moved onto the nearest wall.
     */
    var home: Vec3 = (crevice?.pos ?: Vec3(56.0, 11.8, 7.8)).let { p ->
        if (c.rock.sdf.dist(p) > HOME_SNAP_CM) c.rock.sdf.project(p, 0.5, iterations = 24) else p
    }
        private set
    /** How far [home] has been eased out of a crevice too tight for the carapace (cm). */
    private var homeOut = 0.0

    override fun preTick(c: Ctx) {
        super.preTick(c)
        // pressed into a crevice narrower than its carapace the drawn body would sit in the rock (sim/CrawlerSeat.kt):
        // the spot it wedges into eases out of the crack, along the rock normal, until the whole body fits
        // (a crevice anchor too tight for the carapace: home is the nearest rock spot by it, well under the water and
        // off the glass, where the whole body fits)
        if (!homeFitted) {
            homeFitted = true
            if (!fits(c, home, c.rock.envNormal(home))) {
                var best: Vec3? = null
                for (r in HOME_FIT_R) {
                    repeat(16) {
                        c.rockSurfacePoint(home, r, minNormalY = -0.3)?.let { (p, n) ->
                            val inside = p.y < c.rock.waterLevelCm - HOME_WATER_CM && p.z in HOME_GLASS_CM..(c.rock.widthCm - HOME_GLASS_CM) &&
                                p.x in HOME_GLASS_CM..(c.rock.lengthCm - HOME_GLASS_CM)
                            if (inside && fits(c, p, n) && (best == null || p.distanceTo(home) < best!!.distanceTo(home))) best = p
                        }
                    }
                    if (best != null) break
                }
                best?.let { home = it }
            }
        }
        val st = o.seat ?: return
        if (o.posture == 4 && st.blend >= 1.0 && st.clearance < -HOME_FIT_TOL_CM && homeOut < HOME_OUT_MAX_CM &&
            o.pos.distanceTo(home) < 2.0) {
            val step = min(HOME_OUT_RATE * c.dt, HOME_OUT_MAX_CM - homeOut)
            home += c.rock.envNormal(home) * step
            homeOut += step
        }
        // out on the rock, a spot where the carapace does not fit (a slot behind the rock, a gap at the glass): it
        // gives the spot up and moves on rather than stand wedged in it
        tightT = if (o.posture == 0 && st.blend >= 1.0 && st.clearance < -HOME_FIT_TOL_CM) tightT + c.dt else 0.0
        if (tightT > TIGHT_S && squeezeTo == null) {
            // the nearest rock or glass close by with room for the whole carapace
            var best: Vec3? = null
            for (r in SQUEEZE_R) {
                repeat(10) {
                    c.rockSurfacePoint(o.pos, r, minNormalY = -0.2)?.let { (p, n) ->
                        if (fits(c, p, n) && (best == null || p.distanceTo(o.pos) < best!!.distanceTo(o.pos))) best = p + n * surfaceOffset(c)
                    }
                }
                if (best != null) break
            }
            squeezeTo = best ?: home
        }
    }
    private var tightT = 0.0
    private var homeFitted = false
    /** Where it squeezes out to from a slot too narrow for its carapace (see [Squeeze]). */
    private var squeezeTo: Vec3? = null

    /** Wedged in a slot narrower than its carapace: it sidles out to the nearest spot with room, then carries on. */
    inner class Squeeze : Behavior("squeeze", "Squeezing out of a gap") {
        private val router = Router()
        private var jumping = false
        override fun enter(c: Ctx) { router.reset(); jumping = false }
        override val committed: Boolean get() = elapsed < 2.0 || jumping
        override fun score(c: Ctx) = if (squeezeTo != null) 0.95 else 0.0
        override fun update(c: Ctx, out: SteerIntent) {
            val to = squeezeTo ?: run { finished = true; return }
            if (c.o.pos.distanceTo(to) < 1.5 || elapsed > 12.0) { squeezeTo = null; finished = true; return }
            // still wedged after a few seconds of sidling: it jumps clear across the open water, as the real crab
            // leaps from rock to rock (research 27 §2: ~20 cm)
            val tight = (o.seat?.clearance ?: 0.0) < -HOME_FIT_TOL_CM
            if (!jumping && elapsed > SQUEEZE_JUMP_S && tight && c.rock.sdf.segmentClear(c.o.pos, to, 0.3) && fitsAt(c, to)) jumping = true
            if (jumping) {
                val d = to - c.o.pos
                val step = c.o.burstSpeed * c.dt
                val dir = d.normalized(c.o.heading)
                val up = c.o.up.lerp(c.rock.envNormal(to), 0.15).normalized(Vec3.Y)
                out.place(if (d.length() <= step) to else c.o.pos + dir * step, dir.rejectFrom(up).normalized(c.o.heading), up, 40.0)
                if (d.length() <= step + 0.05) { squeezeTo = null; finished = true }
                return
            }
            scuttle(c, out, router.next(c, to), fast = false)
            out.allowGlass = true
        }
    }

    /**
     * Room for the carapace standing at surface point [p] (normal [n]): its rim, a body height off the surface, clear of
     * rock, sand and glass all round (a grazing spot in a slot narrower than the crab would wedge it into the rock).
     */
    fun fits(c: Ctx, p: Vec3, n: Vec3): Boolean {
        val s = c.sim.crawlSurface
        val t1 = (n cross (if (kotlin.math.abs(n.y) < 0.9) Vec3.Y else Vec3.X)).normalized(Vec3.X)
        val t2 = n cross t1
        val r = FIT_RIM * o.lengthCm
        val h = n * (FIT_HEIGHT * o.lengthCm)
        if (s.dist(p + h) < FIT_CLEAR_CM) return false
        for (i in 0 until 6) {
            val a = i * PI / 3
            if (s.dist(p + h + (t1 * kotlin.math.cos(a) + t2 * sin(a)) * r) < FIT_CLEAR_CM) return false
        }
        return true
    }

    /**
     * Room for the legs at surface point [p] (normal [n]): most of a ring a leg's reach out, a knee height off the
     * surface plane, is open water (in a pocket of rock its long legs would find no clear sweep and fold up).
     */
    fun legRoom(c: Ctx, p: Vec3, n: Vec3): Boolean {
        val s = c.sim.crawlSurface
        val t1 = (n cross (if (kotlin.math.abs(n.y) < 0.9) Vec3.Y else Vec3.X)).normalized(Vec3.X)
        val t2 = n cross t1
        val r = LEG_ROOM_R * o.lengthCm
        val h = n * (LEG_ROOM_H * o.lengthCm)
        var open = 0
        for (i in 0 until 8) {
            val a = i * PI / 4
            if (s.dist(p + h + (t1 * kotlin.math.cos(a) + t2 * sin(a)) * r) > 0.0) open++
        }
        return open >= LEG_ROOM_OPEN
    }

    init {
        install(HideCrevice(), Graze(), CrabScavenge(), CarcassPick(), Dart(), NightGrab(), Squeeze(), MoltHide(this) { restSpot(it, false) })
    }

    /** Legs and chelae (sim/LimbRig.kt): the behaviours say when the claws pick and when it presses flat. */
    val legs: CrabLegs = o.limbRig as CrabLegs
    /** Seconds spent grazing, for tests and the research note (research 17 §3.3: plant material is the bulk). */
    var grazeSeconds = 0.0; private set
    /** Dashes back to the crevice after a startle. */
    var darts = 0; private set
    private var lastDartAt = -1e9

    /** Soft after a molt: hides more and grazes only next to its crevice for two days (research 17 §3.5) [L]. */
    fun softShell(c: Ctx) = !c.o.molting && c.t - c.o.lastMoltAt < SOFT_SHELL_S

    /** Wedged flat into the crevice; posture 4 lets it sit inside the rock's skin (Steering skips SDF projection). */
    override fun restSpot(c: Ctx, night: Boolean): Rest = hangPose(c, crevice, home, inverted = false).copy(pos = home, posture = 4)

    /** Stop-and-go sideways scuttle: short bursts with pauses, like the real crab. */
    private fun scuttle(c: Ctx, out: SteerIntent, target: Vec3, fast: Boolean) {
        out.crawl = true; out.sideways = true; out.target = target; out.surfaceOffset = surfaceOffset(c); out.arriveRadius = 1.0
        val cycle = (c.t + o.appendagePhase) % 2.0
        // (unhurried, it slows as it closes in: a sprint at a spot a hand's breadth away overshot it and shuttled to and fro)
        val walk = min(0.5 * c.o.burstSpeed, max(c.o.cruiseSpeed, SLOW_IN * c.o.pos.distanceTo(target)))
        if (fast || cycle < 0.6) { out.speed = if (fast) c.o.burstSpeed else walk; out.urgency = 0.8 }
        else { out.speed = 0.0; out.urgency = 0.0 }
    }

    /** Room the carapace needs from rock, sand and glass along a walking route (cm). */
    private fun routeClear(c: Ctx) = ROUTE_CLEAR * c.L

    /**
     * A walking route to [to] only through water where the whole carapace fits on the surface below ([fits]: it never
     * plans through a slot narrower than its body), else one with the plain clearance, else null.
     */
    private fun routeTo(c: Ctx, to: Vec3): List<Vec3>? {
        val cl = routeClear(c)
        return c.rock.surfaceRoute(c.o.pos, to, clear = cl, band = cl + 2.0, fits = { q -> fitsAt(c, q) })
            ?: c.rock.surfaceRoute(c.o.pos, to, clear = cl, band = cl + 2.0)
    }

    /** [fits] on the surface under water point [q]. */
    private fun fitsAt(c: Ctx, q: Vec3): Boolean {
        val n = c.rock.envNormal(q)
        return fits(c, q - n * c.rock.envDist(q), n)
    }

    /**
     * Follows a straight scuttle toward a target, and once that stalls (a wall, a slot behind the rock) a walking route
     * round the obstacle through water with room for the whole carapace.
     */
    private inner class Router {
        var route: List<Vec3> = emptyList(); private set
        private var bestD = 1e9
        private var stuckT = 0.0
        private var planned: Vec3? = null
        fun reset() { route = emptyList(); bestD = 1e9; stuckT = 0.0; planned = null }
        fun next(c: Ctx, target: Vec3): Vec3 {
            val d = c.o.pos.distanceTo(target)
            if (d < bestD - 0.3) { bestD = d; stuckT = 0.0 } else stuckT += c.dt
            // planned up front for a new target (a straight scuttle walked it into slots behind the rock), and again
            // from where it is whenever it stalls on the way
            val fresh = planned.let { it == null || it.distanceTo(target) > 2.0 }
            if ((fresh && d > 3.0) || stuckT > ROUTE_STALL_S) {
                route = routeTo(c, target) ?: emptyList()
                planned = target; stuckT = 0.0; bestD = d
            }
            while (route.size > 1 && (c.o.pos.distanceTo(route.first()) < 2.5 || c.o.pos.distanceTo(route[1]) < c.o.pos.distanceTo(route.first()))) route = route.drop(1)
            if (route.size == 1 && c.o.pos.distanceTo(route.first()) < 1.5) route = emptyList()
            return route.firstOrNull() ?: target
        }
    }

    inner class HideCrevice : Behavior("hide", "Hiding in a crevice") {
        private val router = Router()
        override fun enter(c: Ctx) { router.reset() }
        // after a dash home it stays pressed in for a minute or so before venturing out again
        override fun score(c: Ctx) = 0.7 * (1 - c.activity) + 0.12 + (if (softShell(c)) 0.35 else 0.0) + (if (c.t - lastDartAt < WARY_S) 0.4 else 0.0)
        override fun update(c: Ctx, out: SteerIntent) {
            val r = restSpot(c, false)
            if (c.o.pos.distanceTo(r.pos) > 2.0) { scuttle(c, out, router.next(c, r.pos), fast = false); return }
            out.rest(r, 1.0)
            legs.tucked = true
            // pressed flat in the crevice, now and then picking at the film just inside it
            if (sin(c.t * 0.21 + c.o.appendagePhase) > 0.6) legs.pick = 0.4
            c.o.extension = 0.3 + 0.2 * sin(c.t * 0.3 + c.o.appendagePhase)   // legs and eyes peek out
        }
    }

    inner class Graze : Behavior("graze", "Grazing hair algae") {
        private var spot: Pair<Vec3, Vec3>? = null
        private var next = 0.0
        private var dur = 30.0
        override fun score(c: Ctx): Double {
            val w = c.sim.water.s
            // hair algae and the film on rock and glass (research 17 §3.3: scrapes biofilm, tears off algae)
            val algae = w.algaeHair + 0.5 * w.film + 0.3 * w.diatoms
            val food = algae / (algae + 0.02)
            // By day it only slips out to rock right next to its crevice.
            return (0.3 + 0.3 * c.o.hunger) * (0.35 + 0.65 * food) * (0.25 + 0.75 * c.activity) * (if (softShell(c)) 0.5 else 1.0)
        }
        override fun enter(c: Ctx) {
            val night = c.activity > 0.5 && !softShell(c)
            // out at night it sometimes climbs the glass to scrape the film there
            spot = (if (night && c.rng.chance(GLASS_GRAZE_P)) glassSpot(c) else null)
            for (i in 0 until 10) {
                if (spot != null) break
                // (only where the carapace fits and the legs have rock to spread over, not a pocket they fold up in)
                spot = c.rockSurfacePoint(if (night) c.o.pos + Vec3(c.rng.signed() * 15, 0.0, c.rng.signed() * 6) else home, if (night) 25.0 else 10.0, minNormalY = 0.0)
                    ?.takeIf { (p, n) -> fits(c, p, n) && legRoom(c, p, n) }
            }
            dur = c.rng.range(15.0, 45.0)
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val s = spot ?: run { finished = true; return }
            if (elapsed > dur + 40) { finished = true; return }   // unreachable spot
            if (c.o.pos.distanceTo(s.first) > 1.5) { scuttle(c, out, s.first, fast = false); return }
            out.crawl = true; out.sideways = true; out.surfaceOffset = surfaceOffset(c); out.allowGlass = true
            if (c.t > next) {
                next = c.t + 2
                c.o.hunger = max(0.0, c.o.hunger - 0.004)
                c.sim.addGrazing(c.sim.grazeRemovalPerMin(c.o) / 15)
                c.o.mouth = 1.0
                // shuffles a step or two sideways along the patch between bites
                val shuffle = s.first + (c.o.heading cross c.o.up).normalized(Vec3.Z) * (c.rng.signed() * 1.2)
                if (fits(c, shuffle, s.second) && legRoom(c, shuffle, s.second)) spot = s.copy(first = shuffle)
            }
            legs.pick = 1.0      // quick alternating claw strokes to the mouth
            grazeSeconds += c.dt
            c.o.appendagePhase += c.dt * 6
            if (elapsed > dur) finished = true
        }
    }

    /** An open-glass point on the back or side pane near the crevice (film algae; it climbs vertical glass). */
    private fun glassSpot(c: Ctx): Pair<Vec3, Vec3>? {
        val r = c.rock
        for (i in 0 until 8) {
            val back = c.rng.chance(0.7)
            val p = if (back) Vec3((home.x + c.rng.signed() * 20).coerceIn(3.0, r.lengthCm - 3), (home.y + c.rng.signed() * 10).coerceIn(r.sandTopCm + 3, r.waterLevelCm - 5), 0.3)
                else Vec3(if (home.x > r.lengthCm / 2) r.lengthCm - 0.3 else 0.3, (home.y + c.rng.signed() * 10).coerceIn(r.sandTopCm + 3, r.waterLevelCm - 5), (home.z + c.rng.signed() * 8).coerceIn(2.0, r.widthCm - 2))
            val n = if (back) Vec3.Z else if (p.x > 1) Vec3.X * -1.0 else Vec3.X
            // (clear of the rock by more than its legs reach, or the legs reach the rock and pull it into the corner)
            if (r.sdf.dist(p) > max(2.5, GLASS_ROCK_L * o.lengthCm) && fits(c, p, n) && legRoom(c, p, n)) return p to n
        }
        return null
    }

    inner class CrabScavenge : Scavenge(this, 40.0, urgentAbove = 0.3, label = "Scavenging") {
        override fun update(c: Ctx, out: SteerIntent) { super.update(c, out); out.sideways = true; legs.pick = 0.6 }
    }

    /**
     * Very fast sideways dash back into the crevice when startled or a bigger fish comes close (research 17 §3.4:
     * "retreat to shelter at the slightest disturbance"). It keeps going until it is in; if the rock blocks the
     * straight line it side-steps over it for a moment and tries again.
     */
    inner class Dart : Behavior("dart", "Darting for cover") {
        private var arrived = false
        private var bestD = 1e9
        private var stuckT = 0.0
        private var route: List<Vec3> = emptyList()
        private var leap: Vec3? = null
        override val committed: Boolean get() = elapsed < MIN_HIDE_S || (!arrived && elapsed < 15)
        override fun score(c: Ctx): Double {
            if (threat != null || startledRecently && startleMag > 0.1) return 0.97
            val big = nearestOther(c, 6.0) { it.isFish && it.lengthCm > 0.8 * c.o.lengthCm && it.vel.length() > 3.0 }
            return if (big != null) 0.92 else 0.0
        }
        override fun enter(c: Ctx) { startleAt = -1e9; darts++; arrived = false; bestD = 1e9; stuckT = 0.0; route = emptyList(); leap = null }
        override fun update(c: Ctx, out: SteerIntent) {
            val r = restSpot(c, false)
            val d = c.o.pos.distanceTo(r.pos)
            if (arrived || d <= 1.5) {
                arrived = true
                out.rest(r, 1.0); c.o.extension = 0.1; legs.tucked = true
                if (elapsed > MIN_HIDE_S && threat == null) { finished = true; lastDartAt = c.t }
                return
            }
            leap?.let { l ->
                // scrambling straight across a gap or round a lip to the next waypoint (the surface walk cannot turn
                // from one face onto another across open water)
                val to = l - c.o.pos
                val step = c.o.burstSpeed * c.dt
                val dir = to.normalized(c.o.heading)
                val up = c.o.up.lerp(c.rock.envNormal(l), 0.15).normalized(Vec3.Y)
                out.place(if (to.length() <= step) l else c.o.pos + dir * step, dir.rejectFrom(up).normalized(c.o.heading), up, 40.0)
                if (to.length() <= step + 0.05) { leap = null; stuckT = 0.0; bestD = d }
                return
            }
            if (d < bestD - 0.3) { bestD = d; stuckT = 0.0 } else stuckT += c.dt
            // the straight dash is blocked (a wall between it and the crevice): follow a walking route round it
            // (re-planned from where it is if it stalls on the route too; waypoints are grid cells, so it passes one
            // within ~2.5 cm while holding its stance off the rock)
            if (stuckT > 0.8) {
                val next = route.firstOrNull()
                if (next != null && c.rock.sdf.segmentClear(c.o.pos, next, 0.3) && fitsAt(c, next)) leap = next
                else route = routeTo(c, r.pos) ?: c.rock.surfaceRoute(c.o.pos, r.pos, clear = surfaceOffset(c)) ?: emptyList()
                stuckT = 0.0; bestD = d
                if (leap != null) return update(c, out)
            }
            while (route.size > 1 && (c.o.pos.distanceTo(route.first()) < 2.5 || c.o.pos.distanceTo(route[1]) < c.o.pos.distanceTo(route.first()))) route = route.drop(1)
            scuttle(c, out, if (route.isNotEmpty()) route.first() else r.pos, fast = true)
        }
    }

    /** Dead animals picked at (scavenging the dead, never counted as predation; research 11 §3). */
    var carcassBites = 0; private set

    /**
     * Scavenging a dead animal (research 11 §3): handling a carcass is a separate behaviour from live predation. It
     * picks at a body on the sand or rock within 25 cm (prey already dead), more readily when hungry.
     */
    inner class CarcassPick : Behavior("carcass", "Picking at a dead animal") {
        private var body: Organism? = null
        private var next = 0.0
        override val targetVerb: String? get() = "picking at the remains of"
        override fun score(c: Ctx): Double {
            if (c.o.hunger < 0.2) return 0.0
            // (a carcass bobs up off the bottom at about 1 cm/s: one right beside a crab perched on a ledge is still
            // within its reach, whatever the height)
            body = c.sim.organisms.firstOrNull { b -> val d = b.pos.distanceTo(c.o.pos); !b.alive && b.deadSince != null && d < 25.0 && (b.pos.y < c.rock.sandTopCm + 15 || d < NEAR_CARCASS_CM) }
            return if (body != null) 0.5 + 0.3 * c.o.hunger else 0.0
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val b = body ?: run { finished = true; return }
            if (b.alive || b !in c.sim.organisms) { finished = true; return }
            c.o.focusId = b.id
            // the claws reach about a third of a body length: arrival is judged along the surface, so a carcass bobbing
            // a few cm above the rock it walks on is picked at from beneath it
            if (c.o.pos.distanceTo(b.pos) > max(2.0, 0.35 * c.o.lengthCm)) { scuttle(c, out, b.pos, fast = false); if (elapsed > 40) finished = true; return }
            out.crawl = true; out.sideways = true; out.surfaceOffset = surfaceOffset(c)
            c.o.appendagePhase += c.dt * 6
            legs.pick = 1.0
            if (c.t > next) { next = c.t + 3; carcassBites++; c.o.hunger = max(0.0, c.o.hunger - 0.01); c.o.mouth = 1.0 }
            if (elapsed > 60 || c.o.hunger < 0.15) finished = true
        }
    }

    /** Rare: a large crab that has gone hungry grabs a snail at night; a sleeping small fish only in the sandbox scenario. */
    inner class NightGrab : Behavior("grab", "Grabbing prey") {
        private var prey: Organism? = null
        private var nextRoll = 0.0
        override val committed: Boolean get() = elapsed < 4
        override val targetVerb: String? get() = "grabbing at"
        override fun score(c: Ctx): Double {
            val o = c.o
            if (!c.sim.predationEnabled || !c.p.night || o.lengthCm < GRAB_MIN_CM || o.hunger < GRAB_HUNGER) return 0.0
            if (c.t < nextRoll) return if (prey?.alive == true) 0.85 else 0.0
            nextRoll = c.t + 3600.0
            prey = null
            if (!c.rng.chance(GRAB_TRY_PER_HOUR)) return 0.0
            // live fish only in the opt-in sandbox scenario (research 11 §3: Percnon is primarily a grazer)
            prey = nearestOther(c, 15.0) { Predation.preyWeight(o, it, true) > 0 && (if (it.isFish) c.sim.crabLiveFishScenario else c.sim.unsourcedPredation) }
            return if (prey != null) 0.85 else 0.0
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val p = prey ?: run { finished = true; return }
            if (!p.alive) { prey = null; finished = true; return }
            c.o.focusId = p.id
            scuttle(c, out, p.pos, fast = true)
            if (c.o.pos.distanceTo(p.pos) < 1.0 + 0.4 * p.lengthCm) {
                if (c.rng.chance(GRAB_SUCCESS)) c.sim.eaten(c.o, p) else c.sim.agentOf(p)?.let { it.brain.onAttacked(it.ctx, c.o.pos, 1.0, c.o) }
                prey = null
                finished = true
            }
            if (elapsed > 20) { prey = null; finished = true }
        }
    }

    companion object {
        /** Leg span (cm) above which a crab may take live prey. */
        const val NEAR_CARCASS_CM = 10.0
        const val GRAB_MIN_CM = 5.0
        const val GRAB_HUNGER = 0.85
        const val GRAB_TRY_PER_HOUR = 0.15
        const val GRAB_SUCCESS = 0.3
        /** Two days soft after a molt [L]. */
        const val SOFT_SHELL_S = 48 * 3600.0
        /** A home farther than this from rock is moved onto the nearest wall (cm) [L]. */
        const val HOME_SNAP_CM = 3.0
        /** Share of night grazing bouts spent on the glass film [L]. */
        const val GLASS_GRAZE_P = 0.15
        /** Crevice fit: body clearance tolerated, how fast and how far the wedge spot eases out (cm/s, cm). */
        const val HOME_FIT_TOL_CM = 0.03
        const val HOME_OUT_RATE = 4.0
        /** Seconds wedged with the body in rock before it leaves a grazing or scavenging spot. */
        const val TIGHT_S = 0.3
        /** Walking speed per cm still to go (1/s) when not dashing. */
        const val SLOW_IN = 2.0
        /** Walking routes keep this much room (× length) round the body; a straight scuttle stalled this long (s) takes one. */
        const val ROUTE_CLEAR = 0.3
        /** Seconds of sidling out of a slot before it jumps clear instead. */
        const val SQUEEZE_JUMP_S = 4.0
        /** Home search round a crevice anchor too tight for the carapace: radii (cm), and how far it keeps off the
         *  water surface and the glass (cm). */
        val HOME_FIT_R = doubleArrayOf(3.0, 5.0, 7.0)
        const val HOME_WATER_CM = 6.0
        const val HOME_GLASS_CM = 3.0
        const val ROUTE_STALL_S = 1.5
        /** Search radii (cm) for a spot with room when squeezing out. */
        private val SQUEEZE_R = doubleArrayOf(4.0, 8.0, 12.0)
        /** Carapace fit test: rim radius and height (× length), clearance (cm). */
        const val FIT_RIM = 0.55
        const val FIT_HEIGHT = 0.2
        /** [legRoom]: ring radius and height (carapace widths) and how many of its 8 points must be open water. */
        const val LEG_ROOM_R = 1.1
        const val LEG_ROOM_H = 0.25
        const val LEG_ROOM_OPEN = 6
        /** A glass grazing spot stands this far (carapace widths) from the rock: past the legs' reach. */
        const val GLASS_ROCK_L = 1.9
        const val FIT_CLEAR_CM = 0.05
        const val HOME_OUT_MAX_CM = 2.5
        /** Seconds pressed into the crevice after a dash, then extra wary for [WARY_S] [L]. */
        const val MIN_HIDE_S = 8.0
        const val WARY_S = 60.0
    }
}
