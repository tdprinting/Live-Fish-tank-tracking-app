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

    fun apply(intent: SteerIntent) {
        val o = c.o
        val dt = c.dt
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
        when {
            intent.kinematic -> kinematic(o, intent, dt)
            intent.crawl -> {
                crawl(o, intent, dt)
                // crawling over rock/glass junctions can dip into the rock SDF: project back onto its surface
                if (intent.posture !in 3..5) {
                    val d = c.rock.sdf.dist(o.pos)
                    if (d < 0.05) o.pos = c.rock.sdf.project(o.pos, 0.08, iterations = 24)
                }
            }
            else -> swim(o, intent, dt)
        }
        if (o.brainKind == BrainKind.EEL && !intent.kinematic && abs(o.heading.y) > 0.87) o.heading = clampPitch(o.heading, 0.87)
        o.spline.override = intent.splineOverride
        val sp = o.vel.length()
        if (intent.kinematic || intent.crawl) relSpeed = sp
        val relax = 4.0 * (1 - sp / (0.6 * max(o.cruiseSpeed, 0.5))).coerceIn(0.0, 1.0) + 0.15
        o.spline.update(o.pos, o.heading, dt, if (o.brainKind == BrainKind.EEL) relax * 0.15 else relax)
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
        if (it.waddleAmp > 0) {
            waddleT += dt
            val side = (o.heading cross Vec3.Y).normalized(Vec3.Z)
            desired += side * (sin(2 * PI * it.waddleHz * waddleT) * it.waddleAmp * L)
        }
        if (it.separation > 0) {
            var sep = Vec3.ZERO
            for (n in c.p.neighbors(o.pos, L * 2.5 + 6)) {
                if (n === o || !n.isFish) continue
                val r = 1.2 * (L + n.lengthCm) / 2
                val d = o.pos - n.pos
                val dl = d.length()
                if (dl in 1e-6..r) sep += d / (dl * dl) * r
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
        if (!it.allowGlass) a += glassAvoid(o.pos, 0.5 * Lc.coerceAtMost(8.0) + 1)
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
        if (wantDir != null) o.heading = Vec3.rotateToward(o.heading, wantDir.normalized(o.heading), turnRate * dt)
        // C-start stage 1: the head snaps round away from the threat far faster than a routine turn
        if (cStartT in 0.0..C_STAGE1 && o.isFish) o.heading = Vec3.rotateAbout(cHeading, Vec3.Y, cSign * cAngle * smoothstep(0.0, C_STAGE1, cStartT)).normalized(o.heading)
        val pitchMax = if (o.brainKind == BrainKind.EEL) 0.87 else 0.57
        if (abs(o.heading.y) > pitchMax) o.heading = clampPitch(o.heading, pitchMax)

        // Body constraint: velocity mostly along the heading (fish cannot crab sideways fast)
        val fwd = vRaw dot o.heading
        val lateral = vRaw - o.heading * fwd
        val slip = if (o.brainKind == BrainKind.EEL) 0.2 else 0.55
        var v = if (walker) vRaw else o.heading * max(fwd, -0.2 * cruise) + lateral * slip
        relSpeed = (v - flow).length()

        var p = o.pos + v * dt
        // No swim bladder (hawkfish, blennies): the animal sinks whenever it stops swimming hard (SIMULATION §15).
        val sink = o.motion.sinkCmS
        if (sink > 0.0) p -= Vec3.Y * (sink * (1 - effort) * dt)
        // ---- hard resolve ----
        if (!it.ignoreRock) {
            val minD = 0.45 * Lc
            for (iter in 0 until 4) {
                val d = rock.sdf.dist(p)
                if (d >= minD) break
                val n = rock.sdf.gradient(p)
                p = p + n * (minD - d + 0.02)
                val vn = v dot n
                if (vn < 0) v -= n * (vn * 1.2)
            }
        }
        val gm = if (it.allowGlass) 0.6 else (0.25 * L).coerceIn(0.6, 4.0)
        p = Vec3(p.x.coerceIn(gm, rock.lengthCm - gm), p.y.coerceIn(rock.sandTopCm + max(0.6, 0.2 * L.coerceAtMost(8.0)), rock.waterLevelCm - 0.6), p.z.coerceIn(gm, rock.widthCm - gm))
        // The glass clamp can push back into rock in a tight gap: then hold position (never penetrate).
        if (!it.ignoreRock && rock.sdf.dist(p) < 0.34 * Lc && rock.sdf.dist(o.pos) >= rock.sdf.dist(p)) { p = o.pos; v = Vec3.ZERO }
        o.vel = v
        o.pos = p
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
        // surface postures (perched, hanging, grabbing on rock) must stay on the surface, never inside it;
        // dens/holes/burrows (3), cocoons (4) and buried animals (5) are allowed in by design
        if (it.posture !in 3..5) {
            val d = c.rock.sdf.dist(np)
            if (d < 0.1) np += c.rock.sdf.gradient(np).normalized(Vec3.Y) * (0.1 - d)
        }
        o.vel = (np - o.pos) / max(dt, 1e-6)
        o.pos = np
        it.kinHeading?.let { h -> o.heading = Vec3.rotateToward(o.heading, h.normalized(o.heading), Math.toRadians(240.0) * dt) }
        val upT = it.kinUp ?: it.upOverride ?: Vec3.Y.rejectFrom(o.heading).normalized(Vec3.Y)
        o.up = o.up.lerp(upT.rejectFrom(o.heading).normalized(upT), k.coerceAtLeast(0.1)).normalized(Vec3.Y)
        if (it.rollExtra != 0.0) o.up = Vec3.rotateAbout(o.up, o.heading, it.rollExtra)
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
        it.target?.let { t ->
            val d = t - o.pos
            val dl = d.length()
            if (dl > 0.2) dir = d.rejectFrom(n).normalized(o.heading) * (min(it.speed, if (it.urgency > 0.7) o.burstSpeed else o.cruiseSpeed * 1.5) * (dl / it.arriveRadius).coerceAtMost(1.0))
        }
        it.fleeFrom?.let { f -> dir += (o.pos - f).rejectFrom(n).normalized(o.heading) * o.burstSpeed }
        if (it.wander > 0) {
            wanderDir = Vec3.rotateAbout(wanderDir, n, 0.8 * sqrt(dt) * c.rng.gaussian()).rejectFrom(n).normalized(o.heading)
            dir += wanderDir * (o.cruiseSpeed * it.wander)
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
        o.limbRig?.let { lr ->
            // the rig reads the intended travel (lead arm, leading legs); a brittle star's disc moves in pulses
            lr.travel = dir
            if (it.armGait > 0) v = dir * lr.pulse
        }
        var p = o.pos + v * dt
        // re-attach to the surface at the requested offset
        val d = r.envDist(p)
        if (d > it.surfaceOffset + 3) p -= Vec3.Y * min(d, 2.0 * dt * 10)   // fell off: drop (gravity)
        else p -= envNormal(p) * (d - it.surfaceOffset)
        p = r.clampToWater(p, 0.2).let { q -> if (q.y < r.sandTopCm + it.surfaceOffset) q.withY(r.sandTopCm + it.surfaceOffset) else q }
        o.vel = (p - o.pos) / max(dt, 1e-6)
        o.pos = p
        // leg stride clock for the renderer: one stride ≈ a quarter body length (research 17, INFERENCE),
        // stepping eases in and out so a stop-go pause plants the feet
        val walkSpeed = o.vel.length()
        o.walkPhase = (o.walkPhase + dt * walkSpeed / max(0.25 * o.lengthCm, 0.5)) % 1000.0
        o.walkAmt += (smoothstep(0.05, 0.4, walkSpeed / max(o.cruiseSpeed, 0.1)) - o.walkAmt) * min(1.0, dt * 6)
        val nn = envNormal(p)
        var hd = if (v.lengthSq() > 1e-6) v.rejectFrom(nn).normalized(o.heading) else (it.faceDir ?: o.heading).rejectFrom(nn).normalized(o.heading)
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
        o.up = (it.upOverride ?: nn).normalized(Vec3.Y)
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
            o.tailHz = tailHz
            o.amp = amp * (1 - 0.3 * o.finClamp)
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
        o.appendagePhase = (o.appendagePhase + dt * 2 * PI * 0.3) % 1000.0
        o.pitchBias = it.pitchBias
    }

    companion object {
        /** C-start stage durations (s), stretched from the real 20–40 ms so the bend survives the 20 Hz sim tick. */
        const val C_STAGE1 = 0.12
        const val C_STAGE2 = 0.12
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
