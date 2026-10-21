package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.Ev
import com.reeflive.core.sim.Evidence
import com.reeflive.core.sim.FoodParticle
import com.reeflive.core.sim.HoleKind
import com.reeflive.core.sim.RockHole
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Firefish goby, *Nemateleotris magnifica* (fire dartfish; docs/research/41_firefish_chromis.md §1). It claims a rock
 * hole as its bolt hole and hovers a few centimetres out from it, body level and nose into the current, picking
 * drifting plankton. It flicks its long first dorsal fin up and down as a signal, more when another fish comes near. At
 * anything frightening it dives head first into the hole, hides, turns round inside and peeks out before it comes
 * back out; it sleeps in the hole. Unpaired firefish fight over holes (pair row firefish_firefish); two that arrive
 * together may be a mated pair and leave each other alone.
 *
 * Firefish are famous jumpers: its sheet's jumpRisk 0.9 makes a hard startle send it up and out of an open-top tank
 * ([com.reeflive.core.sim.JumpModel], run from [SpeciesBrain.preTick] for every jumper).
 */
class FirefishBrain(c: Ctx) : SpeciesBrain(c) {
    /** Its bolt hole: a deep enough rock hole that fits its body (one animal per hole); null = a rock-face shelter. */
    private var hole0: RockHole? = pickHole(c)
    val hole: RockHole? get() = c.rock.holes.live(hole0)
    private val shelterAt: Vec3 = c.rock.sdf.project(hole0?.mouth ?: defaultNear(c), 0.5)
    val holePos: Vec3 get() = hole?.mouth ?: shelterAt
    private val holeOut: Vec3 get() = hole?.axis ?: c.rock.sdf.gradient(shelterAt).normalized(Vec3.Z)
    val dive = HoleDive()
    private var paired = false
    private var nextHoleCheck = 0.0

    // the dorsal flick (research 41 §1.2: the long first dorsal is flicked as a signal)
    private var nextFlick = 0.0
    private var flicksLeft = 0
    private var flickAt = -1e9

    init {
        install(dive, HoverOverHole(), FirefishFeed(), VisitCleaner(this), SurfaceGasp(this), Flash(this))
    }

    override fun restSpot(c: Ctx, night: Boolean): Rest {
        val h = hole
        return when {
            h != null && night -> holeRest(c, h, -0.5)
            h != null -> Rest(stationPoint(c, HOVER_MIN_CM), holeOut.horizontal().normalized(Vec3.Z))
            else -> rockShelterRest(c, shelterAt)
        }
    }

    override fun anchorPos(c: Ctx, anchor: String): Vec3? = if (anchor == "hole") holePos else super.anchorPos(c, anchor)

    override fun shelterSpot(c: Ctx): Vec3? = c.safe(holePos + holeOut * (c.clearance + 1.0))

    override fun peekRest(c: Ctx): Rest? = hole?.let { holeRest(c, it, PEEK_HEAD_OUT_CM) }

    override fun preTick(c: Ctx) {
        super.preTick(c)
        if (!paired) { paired = true; findMate(c) }
        if (c.t >= nextHoleCheck) { nextHoleCheck = c.t + 300; if (hole == null) pickHole(c)?.let { hole0 = it } }
        flick(c)
    }

    /**
     * Two firefish that came in together (within the hour) are a mated pair half the time (stores sell pairs; research
     * 41 §1.5); a pair is never a target of the conspecific row. Design estimate.
     */
    private fun findMate(c: Ctx) {
        if (o.mateId != null) return
        for (m in c.sim.organisms) {
            if (m === o || !m.alive || m.brainKind != BrainKind.FIREFISH || m.mateId != null) continue
            if (kotlin.math.abs(m.arrivalTime - o.arrivalTime) > 3600.0) continue
            if (o.rng.chance(PAIR_CHANCE)) { o.mateId = m.id; m.mateId = o.id }
            return
        }
    }

    /** The dorsal: raised as built; bouts of 2–4 quick fold-and-raise flicks, more often with another fish close by. */
    private fun flick(c: Ctx) {
        val inHole = o.posture == 3 || dive.phaseNow in 1..3
        if (inHole) { o.finFold += (1.0 - o.finFold) * min(1.0, c.dt * 8); return }
        if (c.t >= nextFlick && flicksLeft == 0) {
            val near = c.p.neighbors(o.pos, FLICK_NEAR_CM).any { it !== o && it.alive && it.isFish }
            flicksLeft = 2 + o.rng.nextInt(3)
            nextFlick = c.t + o.rng.range(FLICK_GAP_MIN_S, FLICK_GAP_MAX_S) * (if (near) 0.35 else 1.0)
        }
        if (flicksLeft > 0 && c.t - flickAt >= FLICK_S) { flickAt = c.t; flicksLeft-- }
        val k = (c.t - flickAt) / FLICK_S
        // a fold to ~70 % and back in one flick, raised again between flicks
        o.finFold = if (k in 0.0..1.0) 0.7 * sin(PI * k) else o.finFold * 0.8
    }

    /** Where it hovers: out from the hole mouth and up a little, [lift] cm above the mouth. */
    fun stationPoint(c: Ctx, lift: Double): Vec3 = c.safe(holePos + holeOut.horizontal().normalized(holeOut) * (c.clearance + 1.5) + Vec3.Y * lift)

    /** How high over its hole it hovers now (cm): lower while new, after a scare, when threatened or sick (research 41 §1.3). */
    fun hoverLift(c: Ctx): Double {
        val days = (c.t - o.arrivalTime) / 86400.0
        var k = (0.35 + 0.65 * min(1.0, days / 7.0)) * (0.6 + 0.8 * o.boldness)
        if (o.timidUntil > c.t || o.waryLevel > 0.3) k *= 0.5
        if (o.health < 0.6) k *= 0.5
        return HOVER_MIN_CM + (HOVER_MAX_CM - HOVER_MIN_CM) * k.coerceIn(0.0, 1.0)
    }

    /**
     * The day hover (research 41 §1.3–1.4): within a few cm of its station, almost still on sculling pectorals, the
     * body level and the head into the current, with a snap now and then at a drifting particle.
     */
    inner class HoverOverHole : Behavior("firehover", "Hovering over its hole") {
        private var p = Vec3.ZERO
        private var next = 0.0
        private var yaw = 0.0
        private var nextSnap = 0.0
        override fun score(c: Ctx) = if (c.p.night) 0.0 else 0.45
        override fun enter(c: Ctx) { next = 0.0 }
        override fun update(c: Ctx, out: SteerIntent) {
            if (c.t > next) {
                next = c.t + c.rng.range(6.0, 15.0)
                val u = c.rng.unitVector()
                p = c.safe(stationPoint(c, hoverLift(c)) + Vec3(u.x * 2.5, u.y * 1.5, u.z * 2.5))
                yaw = c.rng.signed() * 0.4
            }
            out.seek(p, c.o.cruiseSpeed * 0.6, arrive = 1.5)
            out.hover = true; out.ampMul = 0.25; out.pectoral = 1.0; out.tailMul = 0.3
            out.maxPitch = HOVER_PITCH
            val f = c.sim.flow.velocityFast(c.o.pos).horizontal()
            val base = if (f.length() > 0.5) f * -1.0 else holeOut.horizontal()
            out.faceDir = Vec3.rotateAbout(base.normalized(Vec3.Z), Vec3.Y, yaw)
            if (c.t > nextSnap) { nextSnap = c.t + c.rng.range(8.0, 30.0); c.o.mouth = 1.0 }
        }
    }

    /** Drifting food near its hole only: a firefish never ranges far for food (research 41 §1.6). */
    inner class FirefishFeed : Feed(this@FirefishBrain, 30.0) {
        override fun accept(c: Ctx, f: FoodParticle): Boolean {
            if (f.settled || f.onSurface) return false
            val days = (c.t - c.o.arrivalTime) / 86400.0
            return f.pos.distanceTo(holePos) < (if (days < 3 || c.o.timidUntil > c.t) 12.0 else FORAGE_CM)
        }
        override fun scoreMul(c: Ctx, f: FoodParticle): Double = if (f.pos.distanceTo(holePos) < 12.0) 1.0 else 0.6
    }

    /**
     * Dive and hide (research 41 §1.4, §1.7): at a threat or a startle it shoots head first into its hole; by night it
     * sleeps there. While fully inside it turns round, then peeks out head first before it swims back out. In a hole
     * shorter than its body it backs out the way it came. Without a hole it presses against the rock face.
     */
    inner class HoleDive : Behavior("hole", "Darting into its hole") {
        /** 0 dash to the mouth, 1 slide in head first, 2 hiding (or asleep), 3 peeking out, -1 not in a hole. */
        var phase = -1; private set
        val phaseNow: Int get() = phase
        private var dHead = 0.0
        private var hideUntil = 0.0
        private var phaseAt = 0.0
        private var turned = false
        override val committed: Boolean get() = phase in 0..2 && !finished
        override val label: String get() = when {
            c.p.night && phase == 2 -> "Asleep in its hole"
            phase == 2 -> "Hiding in its hole"
            phase == 3 -> "Peeking out of its hole"
            else -> "Darting into its hole"
        }
        override fun score(c: Ctx): Double {
            threat?.let { if (it.alive) return 0.96 }
            if (startledRecently && startleMag > 0.05) return 0.96
            if (c.p.night) return 0.85 * Feed.nightRestDamp(c, this@FirefishBrain)
            if (phase in 0..3 && c.t < hideUntil + 30) return 0.9
            if (c.o.health < 0.4) return 0.7
            return 0.0
        }
        override fun enter(c: Ctx) {
            val h = hole
            val scared = threat != null || startledRecently
            startleAt = -1e9
            if (scared) { hideUntil = c.t + c.rng.range(HIDE_MIN_S, HIDE_MAX_S) * (0.5 + c.o.skittishness); c.o.startle = max(c.o.startle, 0.15) }
            else hideUntil = c.t
            phaseAt = c.t
            // already inside: stay in
            phase = if (h != null && c.o.posture == 3 && h.contains(c.o.pos, 0.8)) 2 else 0
            if (phase == 2) turned = true
        }
        override fun exit(c: Ctx) {
            if (phase >= 1) c.o.spline.reset(c.o.pos, c.o.heading)
            phase = -1
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val h = hole?.takeIf { c.sim.holeClaims.claim(it, c.o) }
            if (h == null) {
                out.rest(rockShelterRest(c, shelterAt), 4.0)
                c.o.sleeping = c.p.night
                if (!c.p.night && c.t > hideUntil && threat == null) finished = true
                return
            }
            val L = c.L
            val deep = min(h.depth - 0.3, L + 0.2)   // head this far in: the tail just inside when the hole is long enough
            val fullyIn = deep - L >= 0.0
            when (phase) {
                0 -> {
                    // dash to a point a little out in front of the mouth, facing in
                    val stand = h.along(-(0.5 * L + 0.6))
                    out.seek(c.viaOpenWater(stand), c.o.burstSpeed, arrive = 1.0, urgency = 1.0)
                    if (c.o.pos.distanceTo(stand) < max(1.2, 0.25 * L) || c.t - phaseAt > 4) { phase = 1; phaseAt = c.t; dHead = -0.5 * L; turned = false }
                }
                1 -> {
                    // head first into the hole, fast (research 41 §1.4: it "darts in")
                    dHead = min(deep, dHead + DIVE_BL_S * L * c.dt)
                    poseInHole(c, h, out, dHead, inward = true, rate = 40.0)
                    out.ampMul = 1.6; out.tailMul = 1.6
                    if (dHead >= deep - 0.05) { phase = 2; phaseAt = c.t }
                }
                2 -> {
                    if (!turned) poseInHole(c, h, out, deep, inward = true, rate = 10.0)
                    else out.rest(holeRest(c, h, -0.5), 5.0)
                    c.o.sleeping = c.p.night
                    out.ampMul = 0.2
                    // turning round inside: a long hole only, unseen (the body is all in the rock)
                    if (!turned && fullyIn && c.t - phaseAt > 2.0) turned = true
                    if (!c.p.night && c.t > hideUntil && threat == null && c.o.health >= 0.4) {
                        phase = 3; phaseAt = c.t
                        if (!turned) dHead = deep
                    }
                }
                else -> {
                    if (turned) {
                        // head out, looking around, then out it comes
                        out.rest(holeRest(c, h, PEEK_HEAD_OUT_CM), 5.0)
                        c.o.eyeYaw = 0.35 * sin(2 * PI * 0.6 * c.t + c.o.appendagePhase)
                        if (c.t - phaseAt > peekS(c)) finished = true
                    } else {
                        // a short hole: back out tail first the way it came
                        dHead = max(-0.6 * L, dHead - 0.6 * L * c.dt)
                        poseInHole(c, h, out, dHead, inward = true, rate = 20.0)
                        if (dHead <= -0.55 * L) finished = true
                    }
                }
            }
        }

        private fun peekS(c: Ctx) = 4.0 + 10.0 * c.o.skittishness

        /** The body laid along the hole axis with the head [d] cm in, facing in ([inward]) or out. */
        private fun poseInHole(c: Ctx, h: RockHole, out: SteerIntent, d: Double, inward: Boolean, rate: Double) {
            val L = c.L
            val up = Vec3.Y.rejectFrom(h.axis).normalized(h.up)
            val dir = if (inward) h.axis * -1.0 else h.axis
            out.place(h.along(d), dir, up, rate)
            out.posture = if (d - L > -0.3 * L) 3 else 0
            out.splineOverride = h.polyline(d - L, d).asReversed()
            out.pectoral = 0.6
        }
    }

    companion object {
        /** Hover height over the hole mouth (cm), research 41 §1.3: no published figure, "just above the bottom" [design]. */
        @Ev(Evidence.DESIGN_ESTIMATE, "41 §1.3") const val HOVER_MIN_CM = 2.0
        const val HOVER_MAX_CM = 12.0
        /** It picks food only this far from its hole (cm) [design]. */
        @Ev(Evidence.DESIGN_ESTIMATE, "41 §1.6") const val FORAGE_CM = 30.0
        /** Largest |heading.y| in the hover (~9°): a level body like the cardinal's. */
        const val HOVER_PITCH = 0.15
        /** Dive speed into the hole (BL/s), hide time after a scare (s) and the head-out peek. */
        @Ev(Evidence.DESIGN_ESTIMATE, "41 §1.4") const val DIVE_BL_S = 6.0
        const val HIDE_MIN_S = 20.0
        const val HIDE_MAX_S = 90.0
        const val PEEK_HEAD_OUT_CM = 1.8
        /** Dorsal flick: one fold-and-raise (s), gap between bouts (s), and how close another fish makes it flick more (cm). */
        @Ev(Evidence.DESIGN_ESTIMATE, "41 §1.2") const val FLICK_S = 0.3
        const val FLICK_GAP_MIN_S = 5.0
        const val FLICK_GAP_MAX_S = 20.0
        const val FLICK_NEAR_CM = 15.0
        /** Two firefish added within the hour are a mated pair this often [design]. */
        @Ev(Evidence.DESIGN_ESTIMATE, "41 §1.5") const val PAIR_CHANCE = 0.5
        private val KINDS = setOf(HoleKind.HOLE, HoleKind.CREVICE, HoleKind.THROUGH)

        /** A hole low on the rock toward the front, spread a little per fish so two firefish do not crowd one spot. */
        private fun defaultNear(c: Ctx): Vec3 =
            Vec3(c.rock.lengthCm * c.o.rng.range(0.3, 0.7), c.rock.sandTopCm + 8.0, c.rock.widthCm * 0.6)

        /** The nearest free hole deep enough to hide its whole body, else any free hole that fits; claimed at once. */
        private fun pickHole(c: Ctx): RockHole? {
            val o = c.o
            val near = c.rock.anchors["HOLE_FIREFISH"]?.pos ?: defaultNear(c)
            val claims = c.sim.holeClaims
            val br = RockHole.bodyRadius(o)
            val deep = c.rock.holes.all.filter { it.kind in KINDS && it.fits(br) && it.depth >= 0.8 * o.lengthCm && claims.usableBy(it, o) }
                .minByOrNull { it.mouth.distanceTo(near) }
            if (deep != null && claims.claim(deep, o)) return deep
            return c.rock.holes.claimHome(o, KINDS, near, claims)
        }
    }
}
