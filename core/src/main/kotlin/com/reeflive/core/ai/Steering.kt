package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.geom.lerp
import com.reeflive.core.geom.smoothstep
import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.Gait
import com.reeflive.core.sim.Organism
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Converts a [SteerIntent] into motion (SIMULATION §4.3): seek/arrive/wander/flee/separate/
 * hold-station, rock and glass avoidance, turn-rate and pitch limits, hard collision resolve,
 * animation outputs and the body spline update.
 */
class Steering(private val c: Ctx) {
    private var wanderDir: Vec3 = Vec3.X
    /** Kinematic placement's up before [SteerIntent.rollExtra] (the roll is an offset from it, not added every tick). */
    private var kinBaseUp: Vec3? = null
    /** Low-passed course the swimming head turns toward (null until the first swim step). */
    private var wantSmooth: Vec3? = null
    /** Low-passed wander course of a crawler. */
    private var crawlWander: Vec3 = Vec3.X
    /** Low-passed walking course of a crawler (null when not crawling). */
    private var crawlDirS: Vec3? = null
    private var lateralAccel = 0.0
    private var waddleT = c.rng.nextDouble() * 10

    // ---- motion model state (SIMULATION §15) ----
    /** Swimming speed through the water (cm/s) of the last swim step. */
    private var relSpeed = 0.0
    /** Beat-and-glide: tail beats left in this bout, glide time left, smoothed glide weight. */
    private var beatsLeft = 3.0
    private var gliding = false
    private var glideLeft = 0.0
    private var glideMix = 0.0
    private var glideActive = false
    /** Share of the caudal fin in propulsion at the last animation step (0 = pure pectoral rowing). */
    private var caudalShare = 0.0
    /** Escape C-start: time since it began (−1 = none), turn sign (+1 = left), total turn, heading at the start. */
    private var cStartT = -1.0
    private var cSign = 1.0
    private var cAngle = 0.0
    private var cHeading = Vec3.X
    private var lastCStart = -1e9
    private var wasFleeing = false
    /** Stop-go walking clock (hermits, shrimp). */
    private var stopGoT = c.o.appendagePhase
    /** Time a crawler's step has been undone in a row (wedged in a crevice). */
    private var projStuckT = 0.0

    fun apply(intent: SteerIntent) {
        val o = c.o
        val dt = c.dt
        // a glass-guarded swimmer told to stop facing a given way stops where its tail, a body length behind, is inside
        // the glass too: otherwise the guard squashes the tail against the pane (a pecking butterfly, a tang posing for
        // the cleaner with its back to the end glass)
        if (o.spline.glassGuard && !intent.kinematic && !intent.crawl) {
            val t = intent.target
            val f = intent.faceDir
            if (t != null && f != null) intent.target = c.tailInside(t, f)
        }
        o.posture = intent.posture
        // a walker / glider given an empty intent (a behaviour that just finished) stays on its surface
        // instead of dropping into the swim integrator for a tick
        if (!intent.kinematic && !intent.crawl && intent.target == null && intent.fleeFrom == null && intent.wander == 0.0 &&
            !o.isFish && (o.motion.gait == Gait.WALK || o.motion.gait == Gait.ARM_ROW || o.motion.gait == Gait.GLIDE) &&
            c.rock.envDist(o.pos) < 3.0) {
            intent.crawl = true
            intent.surfaceOffset = max(intent.surfaceOffset, c.rock.envDist(o.pos).coerceAtMost(1.5))
        }
        updateCStart(o, intent, dt)
        if (!intent.kinematic) kinBaseUp = null
        when {
            intent.kinematic -> kinematic(o, intent, dt)
            intent.crawl -> {
                val before = o.pos
                crawl(o, intent, dt)
                // crawling over rock/glass junctions can dip into the rock SDF: project back onto its surface. In a
                // crevice the nearest surface can be centimetres away: rather than jump there, the step is undone.
                if (intent.posture !in 3..5) {
                    val d = c.rock.sdf.dist(o.pos)
                    if (d < 0.05) {
                        val q = c.rock.sdf.project(o.pos, 0.08, iterations = 24)
                        val maxJump = 0.5 + max(max(o.cruiseSpeed, 1.0), (o.pos - before).length() / max(dt, 1e-6)) * dt
                        // (a projection that did not converge, still inside the rock, is no way out either)
                        val qOk = c.rock.sdf.dist(q) >= 0.0
                        o.pos = if (qOk && q.distanceTo(o.pos) <= maxJump || c.rock.sdf.dist(before) < 0.0) { projStuckT = 0.0; q } else {
                            // slide the step along the rock face instead; failing that stand still, and after a couple of
                            // seconds wedged accept the projection (one hop beats being frozen in a crevice for good)
                            val step = o.pos - before
                            val nb = c.rock.sdf.gradient(before).normalized(Vec3.Y)
                            val slid = before + (step - nb * min(0.0, step dot nb))
                            projStuckT += dt
                            when {
                                c.rock.sdf.dist(slid) >= 0.05 && slid.distanceTo(before) > 1e-4 -> { projStuckT = 0.0; slid }
                                projStuckT > PROJ_STUCK_S && qOk -> { projStuckT = 0.0; q }
                                else -> before
                            }
                        }
                        o.vel = (o.pos - before) / max(dt, 1e-6)
                    }
                }
                // leg stride clock for the renderer: one stride ≈ a quarter body length (research 17, INFERENCE),
                // stepping eases in and out so a stop-go pause plants the feet. Counted after the projection, so a
                // step that was undone takes no stride.
                val walkSpeed = o.vel.length()
                o.walkPhase = (o.walkPhase + dt * walkSpeed / max(0.25 * o.lengthCm, 0.5)) % 1000.0
                o.walkAmt += (smoothstep(0.05, 0.4, walkSpeed / max(o.cruiseSpeed, 0.1)) - o.walkAmt) * min(1.0, dt * 6)
            }
            else -> {
                // The swim integrator's steering and avoidance gains are stable up to ~0.15 s steps; the 0.5 s step of
                // fast-forward made every swimmer's velocity overshoot and reverse each tick (heading flicking back and
                // forth on screen), so long steps are split.
                val n = if (dt > SWIM_MAX_DT + 1e-9) kotlin.math.ceil(dt / SWIM_MAX_DT).toInt() else 1
                for (k in 0 until n) swim(o, intent, dt / n)
            }
        }
        if (intent.kinematic || intent.crawl) wantSmooth = null
        if (!intent.crawl) crawlDirS = null
        if (o.brainKind == BrainKind.EEL && !intent.kinematic && abs(o.heading.y) > 0.87) o.heading = clampPitch(o.heading, 0.87)
        o.spline.override = intent.splineOverride
        val sp = o.vel.length()
        // the whole-body turn is a swimming shape: kinematic poses (sifting, burrow watch, hole entries) and a fish
        // hovering, backing or sculling sideways (whose trail folds back on itself) keep the trail
        o.spline.spreadOn = !intent.kinematic && !intent.crawl && intent.splineOverride == null &&
            sp > 0.4 * o.cruiseSpeed && (o.vel dot o.heading) > 0.7 * sp
        if (intent.kinematic || intent.crawl) relSpeed = sp
        val relax = 4.0 * (1 - sp / (0.6 * max(o.cruiseSpeed, 0.5))).coerceIn(0.0, 1.0) + 0.15
        o.spline.update(o.pos, o.heading, dt, if (o.brainKind == BrainKind.EEL) relax * 0.15 else relax)
        o.spline.keepInside(GLASS_KEEP_CM, c.rock.lengthCm - GLASS_KEEP_CM, GLASS_KEEP_CM, c.rock.widthCm - GLASS_KEEP_CM, c.rock.waterLevelCm - GLASS_KEEP_CM)
        animate(o, intent, dt, sp)
    }

    // ---------------------------------------------------------------- swimming
    private fun swim(o: Organism, it: SteerIntent, dt: Double) {
        val rock = c.rock
        val L = o.lengthCm
        val Lc = o.collisionLength
        val cruise = max(o.cruiseSpeed, 0.5)
        val burst = max(o.burstSpeed, cruise)
        val maxSpeed = if (it.urgency > 0.7) burst else min(burst, max(cruise * 1.4, it.speed))
        val flow = if (o.brainKind == BrainKind.EEL) Vec3.ZERO else c.sim.flow.velocityFast(o.pos)

        // ---- desired velocity ----
        var desired = Vec3.ZERO
        it.target?.let { t ->
            val d = t - o.pos
            val dist = d.length()
            if (dist > 1e-6) {
                val arrive = (dist / max(it.arriveRadius, 0.1)).coerceAtMost(1.0)
                desired += d / dist * (min(it.speed, maxSpeed) * arrive)
            }
        }
        it.fleeFrom?.let { f ->
            val away = (o.pos - f).normalized(o.heading * -1.0)
            desired += away * burst
        }
        if (it.wander > 0) {
            wanderDir = Vec3.rotateAbout(wanderDir, c.rng.unitVector(), 0.6 * sqrt(dt) * c.rng.signed()).normalized()
            // flatten vertical wandering, bias toward the zone centre and home
            val zoneMid = rock.sandTopCm + (o.species.zone.yMin + o.species.zone.yMax) / 2 * (rock.waterLevelCm - rock.sandTopCm)
            var bias = Vec3(0.0, (zoneMid - o.pos.y) * 0.03, 0.0)
            val home = o.homePos
            val hr = max(o.species.homeRadiusCm, 10.0)
            val dh = o.pos.distanceTo(home)
            if (dh > 1) bias += (home - o.pos) / dh * (0.3 * (dh / hr) * (dh / hr))
            wanderDir = Vec3(wanderDir.x, wanderDir.y * 0.4, wanderDir.z).normalized()
            desired += (wanderDir + bias).normalized() * (cruise * it.wander)
        }
        // The waddle / shimmy is a sideways sway of the body, not a course: it moves the fish directly and never
        // enters the velocity the heading follows (as a desired velocity it swung a hovering clown ±100° at 1.3 Hz).
        var waddleVel = Vec3.ZERO
        if (it.waddleAmp > 0) {
            waddleT += dt
            val side = (o.heading cross Vec3.Y).normalized(Vec3.Z)
            waddleVel = side * (sin(2 * PI * it.waddleHz * waddleT) * it.waddleAmp * L)
        }
        if (it.separation > 0) {
            var sep = Vec3.ZERO
            for (n in c.p.neighbors(o.pos, L * 2.5 + 6)) {
                if (n === o) continue
                if (!n.isFish) {
                    // a bubble-tip anemone's crown: fish steer around the stinging tentacles (its clowns nestle in)
                    if (n.brainKind == BrainKind.ANEMONE && o.isFish && n.alive && !c.sim.anemones.welcomes(n, o)) {
                        val r = 0.5 * n.lengthCm * n.extension.coerceAtLeast(0.45) + 0.5 * L
                        val d = o.pos - n.pos
                        val dl = d.length()
                        if (dl in 1e-6..r) sep += d / (dl * dl) * (r * (1.5 - dl / r))
                    }
                    continue
                }
                val r = 1.2 * (L + n.lengthCm) / 2
                val d = o.pos - n.pos
                val dl = d.length()
                // full strength (r/dl) well inside, eased to zero across the edge (0.9 r → 1.1 r): the hard cut-off
                // flicked two neighbours apart and back every tick as one crossed it
                if (dl in 1e-6..(1 + SEP_EDGE) * r) sep += d / (dl * dl) * (r * min(1.0, ((1 + SEP_EDGE) * r - dl) / (2 * SEP_EDGE * r)))
            }
            desired += sep * (cruise * 0.6 * it.separation)
        }
        // Beat-and-glide (Videler & Weihs 1982): thrust surges during a bout of beats and the fish coasts in the glide.
        if (glideActive) desired *= 1.1 - 0.4 * glideMix
        val effort = (desired.length() / max(0.6 * cruise, 0.3)).coerceIn(0.0, 1.0) * (1 - 0.7 * glideMix)
        // Fish swim relative to water: idle fish drift, hoverers hold station.
        desired += if (it.hover) Vec3.ZERO else flow * 0.5

        // ---- acceleration with priority (avoid first) ----
        val maxAccel = maxSpeed * 3 + 5
        var a = Vec3.ZERO
        if (!it.ignoreRock) {
            val look = o.pos + o.vel * 0.4
            val clearance = 0.6 * Lc + 1.0
            val d = rock.sdf.dist(look)
            if (d < clearance) a += rock.sdf.gradient(look) * ((clearance - d) * 40)
        }
        if (!it.allowGlass) {
            a += glassAvoid(o.pos, 0.5 * Lc.coerceAtMost(8.0) + 1)
            // a glass-guarded long body keeps its tail off the pane too (the guard would squash it there)
            if (o.spline.glassGuard) a += glassAvoid(o.pos - o.heading * (0.9 * L), 2.0)
        }
        a = a.clampLength(maxAccel * 2)
        val steer = (desired - o.vel) * 3.0
        a += steer.clampLength(max(0.0, maxAccel * 2 - a.length()))
        lateralAccel = a dot (o.heading cross Vec3.Y).normalized(Vec3.Z)

        // ---- integrate ----
        val vRaw = (o.vel + a * dt).clampLength(maxSpeed + flow.length())

        // ---- heading: turn toward the steered velocity (before the body constraint) ----
        val rawSpeed = vRaw.length()
        val turnRate = Math.toRadians(o.species.locomotion.turnRateDeg.coerceAtLeast(30.0)) * (1 + it.urgency)
        val walker = o.motion.gait == Gait.WALK
        val wantDir: Vec3? = when {
            // a shrimp's tail-flip shoots it backwards while it keeps facing the threat
            walker && it.faceDir != null -> it.faceDir
            rawSpeed > 0.35 * cruise -> vRaw / rawSpeed
            it.faceDir != null -> it.faceDir
            desired.length() > 0.2 * cruise -> desired.normalized()
            else -> null
        }
        // How much of a swimming fish's course its body follows: a slow, unhurried fish holds a level body and turns
        // smoothly; at cruise it points where it goes (1 = old behaviour, always for urgent moves and walkers).
        val urgent = it.urgency >= 0.7 || cStartT >= 0 || walker
        val follow = if (urgent) 1.0 else smoothstep(0.3, 1.2, rawSpeed / cruise)
        if (wantDir != null) {
            // (a behaviour may cap the pitch it steers to)
            val mp0 = it.maxPitch
            var w = wantDir.normalized(o.heading).let { d -> if (mp0 != null && abs(d.y) > mp0) clampPitch(d, mp0) else d }
            if (!urgent && o.brainKind != BrainKind.EEL) {
                // repositioning up or down at low speed is done with the fins, not by pointing the nose there
                val curH = Vec3(o.heading.x, 0.0, o.heading.z).normalized(Vec3.X)
                val hz = Vec3(w.x, 0.0, w.z)
                val hl = hz.length().coerceAtMost(1.0)
                // on a mostly vertical course the small horizontal residual is noise: it must not spin the body round
                val yawK = smoothstep(0.15, 0.7, hl)
                val hdir = if (hl > 1e-6) (curH * (1 - yawK) + hz / hl * yawK).normalized(curH) else curH
                w = (hdir * max(hl, 0.35) + Vec3.Y * (w.y * follow)).normalized(o.heading)
            }
            // low-pass the course the head turns to: velocity noise, a separation nudge or an arrive overshoot no
            // longer flick the heading back and forth tick to tick
            // a fish all but holding station has no real course: its want direction is noise, so it turns slowly
            val drive = smoothstep(0.08, 0.5, max(rawSpeed, desired.length()) / cruise)
            val rate = if (urgent) 1e9 else (2.5 + 10.0 * follow) * (0.15 + 0.85 * drive)
            val prev = wantSmooth
            val ws = if (prev == null) w else (prev + (w - prev) * (1 - exp(-rate * dt))).normalized(w)
            wantSmooth = ws
            var omega = turnRate * (if (urgent) 1.0 else 0.3 + 0.7 * drive)
            val gait = o.motion.gait
            if (!urgent && o.isFish && (gait == Gait.LABRIFORM || gait == Gait.BODY_CAUDAL)) {
                // a routine turn carves an arc no tighter than the species' radius at this speed; nearly still, a
                // pectoral rower pivots on its fins (MotionProfile.turnRadiusBL). Perch-hoppers and eel-like bodies
                // steer each short hop onto its landing spot and keep their own turning
                val labriform = gait == Gait.LABRIFORM
                if (labriform) omega = max(omega, 0.45 * turnRate)
                val still = 1 - smoothstep(0.2, 0.8, rawSpeed / cruise)
                omega = min(omega, rawSpeed / (o.motion.turnRadiusBL * o.lengthCm) + omega * still)
            }
            o.heading = Vec3.rotateToward(o.heading, ws, omega * dt)
        } else wantSmooth = o.heading
        // C-start stage 1: the head snaps round away from the threat far faster than a routine turn
        if (cStartT in 0.0..C_STAGE1 && o.isFish) o.heading = Vec3.rotateAbout(cHeading, Vec3.Y, cSign * cAngle * smoothstep(0.0, C_STAGE1, cStartT)).normalized(o.heading)
        val pitchMax = it.maxPitch ?: if (o.brainKind == BrainKind.EEL) 0.87 else 0.57
        // a behaviour's tighter limit levels the body out at the turn rate (no snap when it starts)
        if (abs(o.heading.y) > pitchMax) o.heading = if (it.maxPitch != null) Vec3.rotateToward(o.heading, clampPitch(o.heading, pitchMax), turnRate * dt) else clampPitch(o.heading, pitchMax)

        // Body constraint: velocity mostly along the heading (fish cannot crab sideways fast)
        val fwd = vRaw dot o.heading
        val lateral = vRaw - o.heading * fwd
        val slip = if (o.brainKind == BrainKind.EEL) 0.2 else 0.55
        // a slow fish sculls sideways and up/down with its pectorals, so it may drift off its body axis
        val latY = Vec3.Y * lateral.y
        val slipSide = if (o.brainKind == BrainKind.EEL) slip else lerp(0.85, slip, follow)
        val slipUp = if (o.brainKind == BrainKind.EEL) slip else lerp(1.0, slip, follow)
        var v = if (walker) vRaw else o.heading * max(fwd, -0.2 * cruise) + (lateral - latY) * slipSide + latY * slipUp
        relSpeed = (v - flow).length()

        var p = o.pos + (v + waddleVel) * dt
        // No swim bladder (hawkfish, blennies): the animal sinks whenever it stops swimming hard (SIMULATION §15).
        val sink = o.motion.sinkCmS
        if (sink > 0.0) p -= Vec3.Y * (sink * (1 - effort) * dt)
        // ---- hard resolve ----
        if (!it.ignoreRock) {
            val minD = 0.45 * Lc
            val swum = p
            for (iter in 0 until 4) {
                val d = rock.sdf.dist(p)
                if (d >= minD) break
                val n = rock.sdf.gradient(p)
                p = p + n * (minD - d + 0.02)
                val vn = v dot n
                if (vn < 0) v -= n * (vn * 1.2)
            }
            // a fish that starts the tick deep in the rock (just out of its hole or den) is eased out at swimming
            // speed, not shoved its whole depth in one tick (a blenny popped 3–7 cm leaving its hole). It is always
            // put past the hold-position band below (0.34 L): a fish eased only to 0.3 L there was frozen in place for
            // good, every later step counting as one toward the rock (an engineer goby stuck at a lip all night).
            val push = p - swum
            val pl = push.length()
            val maxPush = max(o.burstSpeed, 10.0) * dt
            if (rock.sdf.dist(o.pos) < HOLD_BAND * Lc && pl > maxPush) {
                val clear = HOLD_BAND * Lc + 0.05
                var f = maxPush / pl
                while (f < 1.0 && rock.sdf.dist(swum + push * f) < clear) f = min(1.0, f + 0.1)
                p = swum + push * f
            }
        }
        val gm = if (it.allowGlass) 0.6 else (0.25 * L).coerceIn(0.6, 4.0)
        p = Vec3(p.x.coerceIn(gm, rock.lengthCm - gm), p.y.coerceIn(rock.sandTopCm + max(0.6, 0.2 * L.coerceAtMost(8.0)), rock.waterLevelCm - 0.6), p.z.coerceIn(gm, rock.widthCm - gm))
        // The glass clamp can push back into rock in a tight gap: then hold position (never penetrate). Held there, it
        // may still move along the rock face, so a fish wedged between rock and glass is not frozen for good (an engineer
        // goby stuck all night 1 cm off a ledge against the back glass, its burrow a few cm away).
        if (!it.ignoreRock && rock.sdf.dist(p) < HOLD_BAND * Lc && rock.sdf.dist(o.pos) >= rock.sdf.dist(p)) {
            // (the wanted course, its rock-ward part removed, at up to half cruise; it may close in to WEDGE_CLEAR·L)
            val n = rock.sdf.gradient(o.pos).normalized(Vec3.Y)
            val tan = desired - n * min(0.0, desired dot n)
            var q = o.pos + tan.clampLength(0.5 * cruise) * dt
            q = Vec3(q.x.coerceIn(gm, rock.lengthCm - gm), q.y.coerceIn(rock.sandTopCm + max(0.6, 0.2 * L.coerceAtMost(8.0)), rock.waterLevelCm - 0.6), q.z.coerceIn(gm, rock.widthCm - gm))
            if (q.distanceTo(o.pos) > 1e-4 && ((q - o.pos) dot desired) > 0 && rock.sdf.dist(q) >= WEDGE_CLEAR * Lc) { v = (q - o.pos) / max(dt, 1e-6); p = q }
            else { p = o.pos; v = Vec3.ZERO }
        }
        o.vel = v
        o.pos = p
        val prevUp = o.up
        val upBase = Vec3.Y.rejectFrom(o.heading).normalized(Vec3.Y)
        // bank into turns; labriform rowers add their rolling waddle and bobbing pitch (clown, wrasse)
        val mp = o.motion
        val rowing = (1 - caudalShare) * smoothstep(0.05, 0.4, relSpeed / max(L, 0.3) + 0.2)
        val wobble = if (mp.wobbleRoll > 0) mp.wobbleRoll * rowing * sin(2 * PI * mp.wobbleHz * c.t + o.appendagePhase) else 0.0
        val roll = (lateralAccel / max(maxAccel, 1.0)).coerceIn(-1.0, 1.0) * 0.17 * mp.bankGain + it.rollExtra + wobble
        o.up = (it.upOverride ?: Vec3.rotateAbout(upBase, o.heading, roll)).normalized(Vec3.Y)
        val bob = if (mp.bobPitch > 0) mp.bobPitch * rowing * smoothstep(0.3, 1.0, relSpeed / max(L, 0.3)) * sin(2 * PI * mp.bobHz * c.t) else 0.0
        if (it.pitchBias != 0.0 || bob != 0.0) {
            val side = (o.heading cross o.up).normalized(Vec3.Z)
            o.up = Vec3.rotateAbout(o.up, side, it.pitchBias + bob)
        }
        // rolling upright out of a side-lying pose (playing dead, sleeping on the sand) or into one takes a moment:
        // the body's up turns at a capped rate, never 90° in one tick (bank and wobble stay well under the cap)
        val upT = o.up
        o.up = Vec3.rotateToward(prevUp.normalized(upT), upT, SWIM_UP_RATE * dt, o.heading).rejectFrom(o.heading).normalized(upT)
    }

    private fun glassAvoid(p: Vec3, margin: Double): Vec3 {
        val r = c.rock
        var a = Vec3.ZERO
        fun push(dist: Double, dir: Vec3) { if (dist < margin) a += dir * ((margin - dist) * 30) }
        push(p.x, Vec3.X); push(r.lengthCm - p.x, Vec3.X * -1.0)
        push(p.z, Vec3.Z); push(r.widthCm - p.z, Vec3.Z * -1.0)
        push(p.y - r.sandTopCm, Vec3.Y); push(r.waterLevelCm - 1 - p.y, Vec3.Y * -1.0)
        return a
    }

    // ---------------------------------------------------------------- kinematic
    private fun kinematic(o: Organism, it: SteerIntent, dt: Double) {
        val target = it.kinPos ?: o.pos
        val k = 1 - exp(-it.kinRate * dt)
        var np = o.pos.lerp(target, k)
        // A placement is an animal moving, not a cut: never faster than it can swim or walk (a perch or hole picked
        // across the tank used to be crossed in a few ticks, which drew as a teleport). Abstract mode parks at will.
        val maxStep = if (c.sim.abstractMode) Double.MAX_VALUE else max(1.2 * max(o.burstSpeed, o.cruiseSpeed), KIN_MIN_SPEED) * dt
        run {
            val step = np - o.pos
            val sl = step.length()
            if (sl > maxStep) np = o.pos + step * (maxStep / sl)
        }
        // surface postures (perched, hanging, grabbing on rock) must stay on the surface, never inside it;
        // dens/holes/burrows (3), cocoons (4) and buried animals (5) are allowed in by design
        if (it.posture !in 3..5) {
            val sdf = c.rock.sdf
            val d = sdf.dist(np)
            if (d < 0.1) {
                // pushed out along the gradient, a few passes (one pass left a creeping step 0.05 cm inside on a curved face)
                var pushed = np
                for (iter in 0 until 4) {
                    val dq = sdf.dist(pushed)
                    if (dq >= 0.1) break
                    pushed += sdf.gradient(pushed).normalized(Vec3.Y) * (0.1 - dq + 0.005)
                }
                // a step deep into the rock (a client swimming past a ledge) popped the cleaner out the far side, 6 cm
                // in one tick: from outside, stop at the surface along the step instead
                np = if (pushed.distanceTo(o.pos) > maxStep + 0.5 && sdf.dist(o.pos) >= 0.1) {
                    var lo = 0.0; var hi = 1.0
                    repeat(10) { val m = (lo + hi) / 2; if (sdf.dist(o.pos.lerp(np, m)) >= 0.1) lo = m else hi = m }
                    // pressed flat against the face (no headway along the step): slide along it instead of stopping
                    // for good (a goby hauling sand stood frozen at a lip between it and its burrow)
                    val stop = o.pos.lerp(np, lo)
                    if (stop.distanceTo(o.pos) > 0.05 * maxStep) stop else {
                        val n = sdf.gradient(o.pos).normalized(Vec3.Y)
                        val step = np - o.pos
                        val slid = o.pos + (step - n * min(0.0, step dot n))
                        if (sdf.dist(slid) >= 0.1) slid else stop
                    }
                } else pushed
                // one gradient step is not enough in a thin wedge of rock (a hop arc cutting a corner)
                if (sdf.dist(np) < 0.0) np = sdf.project(np, 0.1)
            }
        }
        o.vel = (np - o.pos) / max(dt, 1e-6)
        o.pos = np
        it.kinHeading?.let { h -> o.heading = Vec3.rotateToward(o.heading, h.normalized(o.heading), Math.toRadians(240.0) * dt) }
        val upT = it.kinUp ?: it.upOverride ?: Vec3.Y.rejectFrom(o.heading).normalized(Vec3.Y)
        // the roll (a sway, a lean) is drawn about the placement's own up: applied to o.up itself it compounded every
        // tick, and a resting cleaner shrimp swaying 0.12 rad turned its body ~85°/s against the up-rate cap
        val base0 = (kinBaseUp ?: o.up).normalized(Vec3.Y)
        val upL = base0.lerp(upT.rejectFrom(o.heading).normalized(upT), k.coerceAtLeast(0.1)).normalized(Vec3.Y)
        // capped like a crawler's: at a long tick the lerp alone flipped a cleaner onto its back in one step
        val base = Vec3.rotateToward(base0, upL, CRAWL_UP_RATE * dt, o.heading)
        kinBaseUp = base
        o.up = if (it.rollExtra != 0.0) Vec3.rotateAbout(base, o.heading, it.rollExtra) else base
    }

    // ---------------------------------------------------------------- surface crawling
    /** Distance to the nearest environment surface (rock, sand, glass) and its normal. */
    private fun envNormal(p: Vec3): Vec3 {
        val r = c.rock
        val h = 0.4
        fun f(q: Vec3) = r.envDist(q)
        return Vec3(f(Vec3(p.x + h, p.y, p.z)) - f(Vec3(p.x - h, p.y, p.z)), f(Vec3(p.x, p.y + h, p.z)) - f(Vec3(p.x, p.y - h, p.z)), f(Vec3(p.x, p.y, p.z + h)) - f(Vec3(p.x, p.y, p.z - h))).normalized(Vec3.Y)
    }

    private fun crawl(o: Organism, it: SteerIntent, dt: Double) {
        val r = c.rock
        val n = envNormal(o.pos)
        var dir = Vec3.ZERO
        var targetDist = Double.MAX_VALUE
        it.target?.let { t ->
            val d = t - o.pos
            val dl = d.length()
            targetDist = dl
            if (dl > 0.2) {
                // a target straight above or below (on the wall over its head) leaves only a tiny, sign-flipping
                // tangent residual: walk only as much as the surface actually leads toward it
                val tangent = d.rejectFrom(n)
                // (an urgent dash keeps scrabbling along the residual: a crab's crevice behind the face it clings to is only
                // reached that way, with the brain's side-step detours)
                val lead = if (it.urgency > 0.7) 1.0 else smoothstep(0.05, 0.3, tangent.length() / dl)
                // arrival is judged along the surface: a target a few cm off it (food in the water by the glass) is
                // reached once the walker is under it, instead of being circled at full speed
                dir = tangent.normalized(o.heading) * (min(it.speed, if (it.urgency > 0.7) o.burstSpeed else o.cruiseSpeed * 1.5) * (tangent.length() / it.arriveRadius).coerceAtMost(1.0) * lead)
            }
        }
        it.fleeFrom?.let { f -> dir += (o.pos - f).rejectFrom(n).normalized(o.heading) * o.burstSpeed }
        if (it.wander > 0) {
            wanderDir = Vec3.rotateAbout(wanderDir, n, 0.8 * sqrt(dt) * c.rng.gaussian()).rejectFrom(n).normalized(o.heading)
            // walk along a smoothed wander course: the raw random walk turns by ~10° a tick, which made every
            // wandering crawler shiver left and right
            crawlWander = (crawlWander + (wanderDir - crawlWander) * (1 - exp(-WANDER_SMOOTH_RATE * dt))).rejectFrom(n).normalized(wanderDir)
            dir += crawlWander * (o.cruiseSpeed * it.wander)
        }
        // the walking course, low-passed: where two rock faces meet the raw course can flip between them every tick
        if (dir.lengthSq() > 1e-9) {
            val dn = dir.normalized()
            val prev = crawlDirS ?: o.heading.rejectFrom(n).normalized(dn)
            // (closing in fast on a target the course may turn on a radius of half the distance left, down to 1.5 cm:
            // the fixed rate swung a scuttling crab round its food in circles; far off it stays smooth)
            val rate = max(CRAWL_DIR_RATE, dir.length() / max(CRAWL_TURN_RADIUS_CM, 0.5 * targetDist))
            crawlDirS = (prev + (dn - prev) * (1 - exp(-rate * dt))).rejectFrom(n).normalized(dn)
        }
        // stop-go walking (hermits drag the shell a few steps then pause; shrimp pause every 1–2 s)
        val mp = o.motion
        if (mp.stopGoMoveS > 0 && it.urgency < 0.7 && it.fleeFrom == null) {
            stopGoT += dt
            val cycle = mp.stopGoMoveS + mp.stopGoPauseS
            if (stopGoT % cycle > mp.stopGoMoveS) dir = Vec3.ZERO
            if (stopGoT > 1e6) stopGoT -= cycle * kotlin.math.floor(1e6 / cycle)
        }
        var v = dir
        val forwardWalker = !it.sideways && it.armGait == 0 && it.urgency < 0.7 && it.fleeFrom == null
        if (forwardWalker && dir.lengthSq() > 1e-9) {
            // a walker goes where its body points: facing away from the way to go it turns first, then walks. Moving
            // along any direction at once let it ping-pong between the two faces of a rock corner every tick.
            val course = crawlDirS ?: dir.normalized()
            val align = o.heading.rejectFrom(n).normalized(o.heading) dot course
            v = dir * smoothstep(-0.1, 0.6, align) * smoothstep(-0.3, 0.5, course dot dir.normalized())
        } else if (it.armGait == 0 && it.fleeFrom == null && dir.lengthSq() > 1e-9) {
            // a crab walks the smoothed course too: on the two faces of a rock corner the raw course points back the
            // way it came every tick, and a scuttling Sally shuttled 0.7 cm to and fro at 15 cm/s
            val course = crawlDirS ?: dir.normalized()
            v = course * (dir.length() * smoothstep(-0.3, 0.5, course dot dir.normalized()))
        }
        o.limbRig?.let { lr ->
            // the rig reads the intended travel (lead arm, leading legs); a brittle star's disc moves in pulses
            lr.travel = dir
            if (it.armGait > 0) v = dir * lr.pulse
        }
        var p = o.pos + v * dt
        // re-attach to the surface at the requested offset
        val d = r.envDist(p)
        if (d > it.surfaceOffset + 3) p -= Vec3.Y * min(d, 2.0 * dt * 10)   // fell off: drop (gravity)
        else {
            // settle onto the surface at walking pace: a landing or a step off an edge is not a 3 cm snap in one tick
            // (pushing out of rock is not capped)
            val maxC = max(1.5 * v.length(), REATTACH_CM_S) * dt
            p -= envNormal(p) * (d - it.surfaceOffset).coerceAtMost(maxC)
        }
        p = r.clampToWater(p, 0.2).let { q -> if (q.y < r.sandTopCm + it.surfaceOffset) q.withY(r.sandTopCm + it.surfaceOffset) else q }
        // pressed against a wall it cannot climb, the re-attach throws a walker back past where it stood: it stays put
        // instead of shuttling to and fro between the wall and the floor every tick
        if (forwardWalker && d <= it.surfaceOffset + 3 && dir.lengthSq() > 1e-9 && ((p - o.pos) dot dir) < 0) p = o.pos
        o.vel = (p - o.pos) / max(dt, 1e-6)
        o.pos = p
        // the body's up eases onto a new surface (over an edge, into a corner) instead of snapping to it each tick
        val nTarget = if (mp.footprintBL > 0) footprintNormal(p, o, mp.footprintBL * o.lengthCm) else envNormal(p)
        val nn = Vec3.rotateToward(o.up.normalized(Vec3.Y), nTarget, (if (mp.bodyUpRate > 0) mp.bodyUpRate else CRAWL_UP_RATE) * dt, o.heading)
        // the body turns to the smoothed course (a crab's side to it), never to the raw per-tick one
        val travel = if (it.armGait == 0 && v.lengthSq() > 1e-9 || forwardWalker && dir.lengthSq() > 1e-9) (crawlDirS ?: dir.normalized(o.heading)) * dir.length() else v
        var hd = if (travel.lengthSq() > 1e-6) travel.rejectFrom(nn).normalized(o.heading) else (it.faceDir ?: o.heading).rejectFrom(nn).normalized(o.heading)
        if (it.sideways && v.lengthSq() > 1e-6) {
            // crab gait: body across the travel direction, keep whichever side is closer to the current heading
            val side = nn.cross(hd).normalized(o.heading)
            hd = if (side.dot(o.heading) >= 0) side else side * -1.0
        }
        if (it.armGait > 0) {
            // radial crawler: the disc never turns (Astley 2012). It only stays tangent to the surface; the limb rig
            // picks whichever arm points along the travel as the new centre arm
            // carry the heading over a change of surface by the smallest rotation (parallel transport), never a yaw
            val axis = o.up cross nn
            val sa = axis.length()
            val h = if (sa > 1e-6) Vec3.rotateAbout(o.heading, axis / sa, kotlin.math.atan2(sa, o.up dot nn)) else o.heading
            o.heading = h.rejectFrom(nn).normalized(hd)
        } else o.heading = Vec3.rotateToward(o.heading, hd, Math.toRadians(o.species.locomotion.turnRateDeg.coerceAtLeast(20.0)) * dt)
        o.up = (it.upOverride?.let { u -> Vec3.rotateToward(o.up.normalized(Vec3.Y), u.normalized(Vec3.Y), CRAWL_UP_RATE * dt, o.heading) } ?: nn).normalized(Vec3.Y)
    }

    /**
     * Surface normal averaged under a walker's footprint: the local normal at [p] and at four points [span] cm fore and
     * aft and [span]/2 to either side, each first stepped onto the surface (MotionProfile.footprintBL). Over a sharp
     * corner the samples disagree and the mean leans the body part way, as legs straddling the edge would.
     */
    private fun footprintNormal(p: Vec3, o: Organism, span: Double): Vec3 {
        val n0 = envNormal(p)
        val f = o.heading.rejectFrom(n0).normalized(n0.cross(Vec3.Z).normalized(Vec3.X))
        val l = n0 cross f
        var sum = n0 * 2.0
        for (k in 0 until 4) {
            val off = when (k) { 0 -> f * span; 1 -> f * -span; 2 -> l * (0.5 * span); else -> l * (-0.5 * span) }
            val q = p + off
            sum += envNormal(q - envNormal(q) * c.rock.envDist(q).coerceIn(-span, span))
        }
        return sum.normalized(n0)
    }

    // ---------------------------------------------------------------- escape C-start
    /**
     * Fast-start escape (Domenici & Blake 1997, J Exp Biol 200:1165): stage 1 bends the body into a C and
     * turns the head away from the threat, stage 2 is the return tail stroke that launches the burst.
     * Real stage 1 lasts 20–40 ms; it is stretched to [C_STAGE1] so it spans at least two sim ticks.
     * A shrimp's tail-flip (caridoid escape) is a train of ventral abdominal flexions instead.
     */
    private fun updateCStart(o: Organism, it: SteerIntent, dt: Double) {
        val from = it.fleeFrom
        val fleeing = from != null && it.urgency >= 0.99 && !it.kinematic && !it.crawl
        if (fleeing && !wasFleeing && c.t - lastCStart > 2.0) {
            lastCStart = c.t
            cStartT = 0.0
            cHeading = o.heading
            val away = (o.pos - from!!).horizontal().normalized(o.heading * -1.0)
            val h = o.heading.horizontal().normalized(Vec3.X)
            val turn = Vec3.angle(h, away)
            cSign = if (((h cross away) dot Vec3.Y) >= 0) 1.0 else -1.0
            cAngle = min(turn, Math.toRadians(o.motion.cStartDeg))
        }
        wasFleeing = fleeing
        if (cStartT >= 0) {
            cStartT += dt
            o.bend = if (o.motion.gait == Gait.WALK) {
                // tail-flips at ~8 Hz for half a second
                if (cStartT < 0.5) max(0.0, sin(2 * PI * 8.0 * cStartT)) else 0.0
            } else when {
                // stage 1 curls toward the side the head turns (model −Z is the fish's left)
                cStartT < C_STAGE1 -> -cSign * smoothstep(0.0, C_STAGE1 * 0.7, cStartT)
                // stage 2: the tail sweeps back through
                cStartT < C_STAGE1 + C_STAGE2 -> -cSign * (1 - 1.5 * (cStartT - C_STAGE1) / C_STAGE2)
                else -> o.bend * exp(-dt * 10)
            }
            if (cStartT > 1.0) { cStartT = -1.0; o.bend = 0.0 }
        } else o.bend *= exp(-dt * 10)
    }

    // ---------------------------------------------------------------- animation
    /**
     * Fin and body kinematics from the species [com.reeflive.core.sim.MotionProfile] and the speed through the water
     * (SIMULATION §15): labriform pectoral rowing below the gait-transition speed, Strouhal-scaled tail beats above it,
     * pectoral sculling while hovering, beat-and-glide at low speed, still fins on a perch.
     */
    private fun animate(o: Organism, it: SteerIntent, dt: Double, speed: Double) {
        val loc = o.species.locomotion
        val mp = o.motion
        val cruise = max(o.cruiseSpeed, 0.5)
        val L = max(o.lengthCm, 0.3)
        if (o.isFish) {
            val bl = (if (it.kinematic) speed else relSpeed) / L
            val st = mp.stroke(bl)
            var tailHz = st.tailHz
            var amp = st.amp
            var pecHz = st.pecHz
            var pecAmp = st.pecAmp
            val hovering = speed < 0.4 * cruise
            val resting = it.kinematic && speed < 0.3 * cruise
            if (resting) {
                // perched, wedged, in a hole or cocooned: fins still, a slow breathing ripple
                amp = 0.004; tailHz = 0.6; pecAmp = if (it.posture == 1) 0.05 else 0.12; pecHz = 0.5 * mp.pecHzHover
            } else if (hovering && mp.gait == Gait.LABRIFORM) {
                // hovering labriform fish scull with the pectorals and keep the tail nearly still
                amp = min(amp, 0.6 * mp.rowBodyAmp + 0.004)
            }
            // beat-and-glide at routine speeds (cardinal, dottyback, the perch-hoppers' coast after a launch)
            val canGlide = mp.glideS > 0 && it.urgency < 0.7 && !it.kinematic && !it.hover && bl in 0.3..3.5 && cStartT < 0
            glideActive = canGlide
            if (canGlide) {
                if (gliding) {
                    glideLeft -= dt
                    if (glideLeft <= 0) { gliding = false; beatsLeft = mp.beatsPerBout }
                } else {
                    beatsLeft -= tailHz * dt
                    if (beatsLeft <= 0) {
                        gliding = true
                        // deterministic jitter (no rng draw, so behaviour sequences stay as they were)
                        val j = (o.swimPhase * 0.618034) % 1.0
                        glideLeft = mp.glideS * (0.6 + 0.8 * j)
                    }
                }
            } else { gliding = false; beatsLeft = min(beatsLeft, mp.beatsPerBout) }
            glideMix += ((if (gliding) 1.0 else 0.0) - glideMix) * min(1.0, dt * 10)
            amp *= 1 - 0.85 * glideMix
            tailHz *= 1 - 0.5 * glideMix
            pecAmp *= 1 - 0.5 * glideMix
            amp *= it.ampMul
            tailHz *= it.tailMul
            if (o.sleeping) { amp *= 0.3; pecAmp *= 0.4; pecHz *= 0.6 }
            if (Symptomatic.lethargic(o)) { amp *= 0.6; pecAmp *= 0.6 }
            if (o.health < 0.3) { amp *= 0.5; pecAmp *= 0.5 }
            caudalShare = st.caudalShare
            // muscle inertia: the beat amplitude eases toward its target (a fish does not snap from hovering to a full
            // beat between two ticks, which read as a rubbery wobble); a C-start is not eased. The rate is left alone:
            // the glide timing reads the phase, so the whole-tank behaviour stays exactly as it was.
            o.tailHz = tailHz
            val ampTarget = amp * (1 - 0.3 * o.finClamp)
            o.amp = if (cStartT >= 0) ampTarget else o.amp + (ampTarget - o.amp) * min(1.0, dt * (if (ampTarget > o.amp) 8.0 else 5.0))
            o.pecAmp = pecAmp * (1 - 0.6 * o.finClamp)
            o.pecPhase = (o.pecPhase + pecHz * dt) % 1000.0
            o.pectoral = it.pectoral ?: when {
                hovering || resting -> 1.0
                mp.gait == Gait.LABRIFORM -> 0.9 - 0.6 * st.caudalShare
                else -> 0.35
            }
        } else {
            o.limbRig?.step(o, dt, speed)
            val frac = ((speed - cruise) / max(o.burstSpeed - cruise, 0.1)).coerceIn(0.0, 1.0)
            o.tailHz = loc.tailHzCruise * (speed / cruise).coerceIn(0.3, 2.2) * it.tailMul
            o.amp = lerp(loc.ampCruise, loc.ampBurst, frac) * it.ampMul * (if (speed < 0.4 * cruise) 0.5 else 1.0)
            o.pectoral = it.pectoral ?: 0.4
            if (mp.gait == Gait.ARM_ROW) {
                // brittle star: the side arms row at ~0.5 Hz (one stroke ≈ 2 s, Astley 2012), faster when rushing;
                // at rest the arm tips sweep slowly, probing for scent
                val r = speed / cruise
                val moving = smoothstep(0.1, 0.6, r)
                o.pecPhase = (o.pecPhase + dt * (0.12 + (mp.rowHz * r.coerceIn(0.5, 2.2) - 0.12) * moving)) % 1000.0
                o.pecAmp = 0.08 + (mp.rowSweep - 0.08) * moving
            } else {
                // shrimp pleopods fan constantly at 5–8 Hz (stored for the renderer; legs keep their own phase)
                o.pecPhase = (o.pecPhase + dt * (if (mp.gait == Gait.WALK) 6.0 else 0.0)) % 1000.0
                // swimmerets idle at a low ventilating stroke and beat fully while moving fast or fleeing
                o.pecAmp = if (mp.gait == Gait.WALK) 0.3 + 0.7 * smoothstep(0.8, 2.0, speed / cruise) else 0.0
            }
        }
        o.swimPhase = (o.swimPhase + o.tailHz * dt) % 1000.0
        // radians, wrapped at a whole number of turns so sin(phase) never jumps (the renderer's antennae and legs
        // read it as an angle; a wrap at 1000 rad snapped them once every ~9 min)
        o.appendagePhase = (o.appendagePhase + dt * 2 * PI * 0.3) % com.reeflive.core.sim.Organism.APPENDAGE_WRAP
        o.pitchBias = it.pitchBias
    }

    companion object {
        /** Body trail points stay this far (cm) inside the glass and under the water line. */
        const val GLASS_KEEP_CM = 0.1
        /** C-start stage durations (s), stretched from the real 20–40 ms so the bend survives the 20 Hz sim tick. */
        const val C_STAGE1 = 0.12
        const val C_STAGE2 = 0.12
        /** Half-width (fraction of the personal space) of the band across its edge where separation eases out. */
        const val SEP_EDGE = 0.1
        /** Rock clearance (× collision length) inside which a swimmer holds position rather than step closer. */
        const val HOLD_BAND = 0.34
        /** Closest (× collision length) a swimmer wedged between rock and glass may slide along the rock. */
        const val WEDGE_CLEAR = 0.2
        /** Longest swim integration step (s); longer behaviour steps (fast-forward) are split. */
        const val SWIM_MAX_DT = 0.1
        /** Rate (1/s) of the crawler's wander-course low-pass. */
        const val WANDER_SMOOTH_RATE = 1.5
        /** Fastest a crawler settles onto its surface (cm/s) beyond its own walking speed. */
        const val REATTACH_CM_S = 8.0
        /** Rate (1/s) of the crawler's walking-course low-pass. */
        const val CRAWL_DIR_RATE = 4.0
        /** Tightest turn (cm radius) of the smoothed walking course at speed. */
        const val CRAWL_TURN_RADIUS_CM = 1.5
        /** Seconds a crawler stays wedged before it takes the long projection out (s). */
        const val PROJ_STUCK_S = 2.0
        /** Fastest a crawler's up vector follows a change of surface (rad/s, 240°/s). */
        const val CRAWL_UP_RATE = 4.19
        /** Max roll rate of a swimming body's up vector (rad/s, ~290°/s). */
        const val SWIM_UP_RATE = 5.0
        /** Floor (cm/s) of the kinematic placement speed cap, for the slow walkers. */
        const val KIN_MIN_SPEED = 6.0
    }
}

/** Limit |heading.y| to [maxY] keeping the horizontal direction (falls back to the X axis when vertical). */
internal fun clampPitch(h: Vec3, maxY: Double): Vec3 {
    val hz = Vec3(h.x, 0.0, h.z)
    val dir = if (hz.length() > 1e-6) hz.normalized() else Vec3.X
    return dir * sqrt(1 - maxY * maxY) + Vec3(0.0, maxY * kotlin.math.sign(h.y), 0.0)
}

internal object Symptomatic {
    fun lethargic(o: Organism) = o.symptoms.contains(com.reeflive.core.sim.Symptom.LETHARGY)
}
