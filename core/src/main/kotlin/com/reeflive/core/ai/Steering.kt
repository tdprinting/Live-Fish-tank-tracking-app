package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.geom.lerp
import com.reeflive.core.sim.BrainKind
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

    fun apply(intent: SteerIntent) {
        val o = c.o
        val dt = c.dt
        o.posture = intent.posture
        when {
            intent.kinematic -> kinematic(o, intent, dt)
            intent.crawl -> {
                crawl(o, intent, dt)
                // crawling over rock/glass junctions can dip into the rock SDF: project back onto its surface
                if (intent.posture !in 3..5) {
                    val d = c.rock.sdf.dist(o.pos)
                    if (d < 0.05) o.pos += c.rock.sdf.gradient(o.pos).normalized(Vec3.Y) * (0.05 - d)
                }
            }
            else -> swim(o, intent, dt)
        }
        if (o.brainKind == BrainKind.EEL && !intent.kinematic && abs(o.heading.y) > 0.87) o.heading = clampPitch(o.heading, 0.87)
        o.spline.override = intent.splineOverride
        val sp = o.vel.length()
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
        val wantDir: Vec3? = when {
            rawSpeed > 0.35 * cruise -> vRaw / rawSpeed
            it.faceDir != null -> it.faceDir
            desired.length() > 0.2 * cruise -> desired.normalized()
            else -> null
        }
        if (wantDir != null) o.heading = Vec3.rotateToward(o.heading, wantDir.normalized(o.heading), turnRate * dt)
        val pitchMax = if (o.brainKind == BrainKind.EEL) 0.87 else 0.57
        if (abs(o.heading.y) > pitchMax) o.heading = clampPitch(o.heading, pitchMax)

        // Body constraint: velocity mostly along the heading (fish cannot crab sideways fast)
        val fwd = vRaw dot o.heading
        val lateral = vRaw - o.heading * fwd
        val slip = if (o.brainKind == BrainKind.EEL) 0.2 else 0.55
        var v = o.heading * max(fwd, -0.2 * cruise) + lateral * slip

        var p = o.pos + v * dt
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
        val roll = (lateralAccel / max(maxAccel, 1.0)).coerceIn(-1.0, 1.0) * 0.17 + it.rollExtra
        o.up = (it.upOverride ?: Vec3.rotateAbout(upBase, o.heading, roll)).normalized(Vec3.Y)
        if (it.pitchBias != 0.0) {
            val side = (o.heading cross o.up).normalized(Vec3.Z)
            o.up = Vec3.rotateAbout(o.up, side, it.pitchBias)
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
        val v = dir
        var p = o.pos + v * dt
        // re-attach to the surface at the requested offset
        val d = r.envDist(p)
        if (d > it.surfaceOffset + 3) p -= Vec3.Y * min(d, 2.0 * dt * 10)   // fell off: drop (gravity)
        else p -= envNormal(p) * (d - it.surfaceOffset)
        p = r.clampToWater(p, 0.2).let { q -> if (q.y < r.sandTopCm + it.surfaceOffset) q.withY(r.sandTopCm + it.surfaceOffset) else q }
        o.vel = (p - o.pos) / max(dt, 1e-6)
        o.pos = p
        val nn = envNormal(p)
        var hd = if (v.lengthSq() > 1e-6) v.rejectFrom(nn).normalized(o.heading) else (it.faceDir ?: o.heading).rejectFrom(nn).normalized(o.heading)
        if (it.sideways && v.lengthSq() > 1e-6) {
            // crab gait: body across the travel direction, keep whichever side is closer to the current heading
            val side = nn.cross(hd).normalized(o.heading)
            hd = if (side.dot(o.heading) >= 0) side else side * -1.0
        }
        o.heading = Vec3.rotateToward(o.heading, hd, Math.toRadians(o.species.locomotion.turnRateDeg.coerceAtLeast(20.0)) * dt)
        o.up = (it.upOverride ?: nn).normalized(Vec3.Y)
    }

    // ---------------------------------------------------------------- animation
    private fun animate(o: Organism, it: SteerIntent, dt: Double, speed: Double) {
        val loc = o.species.locomotion
        val cruise = max(o.cruiseSpeed, 0.5)
        val frac = ((speed - cruise) / max(o.burstSpeed - cruise, 0.1)).coerceIn(0.0, 1.0)
        o.tailHz = loc.tailHzCruise * (speed / cruise).coerceIn(0.3, 2.2) * it.tailMul
        var amp = lerp(loc.ampCruise, loc.ampBurst, frac) * it.ampMul
        val hovering = speed < 0.4 * cruise
        if (hovering) amp *= 0.5
        if (Symptomatic.lethargic(o)) amp *= 0.6
        if (o.health < 0.3) amp *= 0.5
        o.amp = amp * (1 - 0.3 * o.finClamp)
        o.pectoral = it.pectoral ?: if (hovering) 1.0 else 0.4
        o.swimPhase = (o.swimPhase + o.tailHz * dt) % 1000.0
        o.appendagePhase += dt * 2 * PI * 0.3
        o.pitchBias = it.pitchBias
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
