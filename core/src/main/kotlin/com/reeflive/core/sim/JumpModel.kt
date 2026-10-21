package com.reeflive.core.sim

import com.reeflive.core.ai.Ctx
import com.reeflive.core.geom.Vec3
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One leap in progress ([Organism.leap]): the sim moves the body ballistically and the brain is paused until it is
 * back in the water ([JumpModel.step]).
 */
class Leap(var vel: Vec3, val lid: Boolean) {
    /** 0 swimming up to the surface, 1 in the air over the tank, 2 falling outside the glass, 3 lying on the floor. */
    var phase = 0
    var t = 0.0
    var phaseT = 0.0
    /** It bumped the lid (with a lid) or the glass rim and fell back in. */
    var bumped = false
}

/**
 * Fish jumping out of the tank (research 41 §1.8, §2.8). Firefish are the hobby's best-known "carpet surfers": a hard
 * startle (a knock, a chase, the lights snapping on or off, the first days in a new tank) sends one rocketing up
 * through the water, and in an open-top tank it can clear the rim and land on the floor. Every fish with a
 * [SimSpecies.jumpRisk] above 0 takes part; the hook is the startle itself ([SpeciesBrain.preTick] calls [maybeLeap]),
 * so a species only sets its sheet's value.
 *
 * Whether a leap leaves the tank is plain ballistics, not a second roll: the launch speed and angle carry the fish over
 * the rim only when it starts near enough to the glass. With a lid ([WaterSim.lid]) it hits the lid and drops back.
 * No published jump rates exist, so the per-startle odds are design estimates tuned so that a settled firefish in an
 * open-top tank is usually lost within a few months (hobby reports, research 41 §1.8).
 */
object JumpModel {
    /** Leap odds of one full-strength startle for a species with jumpRisk 1 (research 41 §3: about 6 % per startle). */
    @Ev(Evidence.DESIGN_ESTIMATE, "41 §1.8, §3") const val PER_STARTLE = 0.06
    /** Startles weaker than this never trigger a leap (a lights-on flinch, a ripple). */
    const val MIN_MAG = 0.15
    /** e-folding depth (cm) below the surface: a fish deep in the tank turns to cover instead of shooting upward. */
    @Ev(Evidence.DESIGN_ESTIMATE, "41 §1.8") const val SURFACE_CM = 25.0
    /** A bolt hole (or the species' own shelter) this close is where it goes instead (cm) and how much that cuts the odds. */
    const val HOME_NEAR_CM = 12.0
    const val HOME_NEAR_MULT = 0.35
    /** First days in a new tank: no familiar escape route yet (hobby reports: new firefish jump most). */
    const val NEWCOMER_DAYS = 3.0
    const val NEWCOMER_MULT = 3.0
    const val SETTLING_DAYS = 14.0
    const val SETTLING_MULT = 1.5
    /** Startled in the dark: it cannot see where its hole is. */
    const val DARK_MULT = 2.0
    /** Afraid of a tankmate (harassed): an extra push to bolt anywhere. */
    const val HARASSED_MULT = 1.5
    /** Launch speed (cm/s) and angle above horizontal: an escape burst of ~15–30 BL/s for a 7 cm fish [design]. */
    @Ev(Evidence.DESIGN_ESTIMATE, "41 §1.8") const val V_MIN = 120.0
    const val V_MAX = 220.0
    const val ANGLE_MIN = 0.95   // ~55°
    const val ANGLE_MAX = 1.4    // ~80°
    const val G = 981.0
    /** The floor below the tank (cm below its bottom; a 30 in stand) and how long it flaps there before the sim lets it go. */
    const val FLOOR_CM = -76.0
    const val FLOOR_S = 1.5

    /** Odds that a startle of [mag] makes [o] leap now; [homeCm] how far its own shelter is (+inf when it has none). */
    fun chance(o: Organism, mag: Double, depthCm: Double, homeCm: Double, dark: Boolean, now: Double): Double {
        val risk = o.species.jumpRisk
        if (risk <= 0.0 || !o.alive || !o.isFish || mag <= MIN_MAG) return 0.0
        val days = (now - o.arrivalTime) / 86400.0
        var p = risk * PER_STARTLE * min(1.0, (mag - MIN_MAG) / (1 - MIN_MAG))
        p *= exp(-max(0.0, depthCm) / SURFACE_CM)
        if (homeCm < HOME_NEAR_CM) p *= HOME_NEAR_MULT
        p *= if (days < NEWCOMER_DAYS) NEWCOMER_MULT else if (days < SETTLING_DAYS) SETTLING_MULT else 1.0
        if (dark) p *= DARK_MULT
        if (o.waryLevel > 0.5) p *= HARASSED_MULT
        return min(p, 0.5)
    }

    /**
     * A startle of [mag] from [from]: roll the leap and start it (true). The launch heads up and away from the scare;
     * a fish with rock between it and the surface cannot leap and stays with its brain's own escape.
     */
    fun maybeLeap(c: Ctx, mag: Double, from: Vec3, homeCm: Double): Boolean {
        val o = c.o
        if (o.leap != null || o.attached && o.posture == 3) return false
        val sim = c.sim
        val rock = sim.rock
        val depth = rock.waterLevelCm - o.pos.y
        val p = chance(o, mag, depth, homeCm, c.p.light < 0.1, c.t)
        if (p <= 0.0 || !o.rng.chance(p)) return false
        // up and away from the scare, mostly along the way it faces
        val away = (o.pos - from).horizontal().normalized(o.heading.horizontal().normalized(Vec3.X))
        val h = (o.heading.horizontal().normalized(away) + away).normalized(away)
        val a = o.rng.range(ANGLE_MIN, ANGLE_MAX)
        val dir = (h * cos(a) + Vec3.Y * sin(a)).normalized(Vec3.Y)
        val v = o.rng.range(V_MIN, V_MAX)
        // the path to the surface must be open water
        val toSurface = depth / max(dir.y, 0.2)
        if (!rock.sdf.segmentClear(o.pos, o.pos + dir * toSurface, 0.3)) return false
        o.leap = Leap(dir * v, lid = sim.water.lid)
        o.attached = false
        o.posture = 0
        sim.emit(SimEvent.Jumped(o.id, Jump.LEAPT, sim.simTimeSec))
        return true
    }

    /**
     * Move a leaping fish by [dt] s. Returns true when it is finished out of the tank (the caller removes it and reports
     * the death); a leap that falls back into the water clears [Organism.leap] and hands the fish back to its brain.
     */
    fun step(sim: Simulation, o: Organism, dt: Double): Boolean {
        val lp = o.leap ?: return false
        val rock = sim.rock
        val top = sim.config.heightCm
        lp.t += dt; lp.phaseT += dt
        var p = o.pos
        var v = lp.vel
        when (lp.phase) {
            0 -> {
                // the burst up through the water (drag ignored over these few centimetres)
                p += v * dt
                if (p.y >= rock.waterLevelCm) { lp.phase = 1; lp.phaseT = 0.0 }
                else if (rock.sdf.dist(p) < 0.3 || lp.t > 1.5) return land(o, lp, inside = true)
            }
            1 -> {
                v = v.withY(v.y - G * dt)
                p += v * dt
                val bodyHalf = 0.5 * o.species.depthRatio * o.lengthCm
                val outside = p.x < 0.0 || p.x > rock.lengthCm || p.z < 0.0 || p.z > rock.widthCm
                if (lp.lid && p.y >= top - bodyHalf && v.y > 0) {
                    // the lid: a thump and back down
                    p = p.withY(top - bodyHalf)
                    v = Vec3(v.x * 0.4, -0.3 * v.y, v.z * 0.4)
                    if (!lp.bumped) { lp.bumped = true; sim.emit(SimEvent.Jumped(o.id, Jump.HIT_LID, sim.simTimeSec)) }
                }
                if (outside) {
                    if (p.y > top) {
                        lp.phase = 2; lp.phaseT = 0.0
                        sim.emit(SimEvent.Jumped(o.id, Jump.OUT, sim.simTimeSec))
                    } else {
                        // below the rim: it hits the glass and slides back in
                        p = Vec3(p.x.coerceIn(0.5, rock.lengthCm - 0.5), p.y, p.z.coerceIn(0.5, rock.widthCm - 0.5))
                        v = Vec3(-0.2 * v.x, min(v.y, 0.0), -0.2 * v.z)
                        lp.bumped = true
                    }
                }
                if (lp.phase == 1 && p.y < rock.waterLevelCm && v.y < 0) {
                    o.pos = p
                    return land(o, lp, inside = true)
                }
            }
            2 -> {
                v = v.withY(v.y - G * dt)
                p += v * dt
                if (p.y <= FLOOR_CM) { p = p.withY(FLOOR_CM); v = Vec3.ZERO; lp.phase = 3; lp.phaseT = 0.0 }
            }
            else -> {
                // flapping on the floor
                o.amp = 0.12 * max(0.0, 1 - lp.phaseT / FLOOR_S)
                if (lp.phaseT >= FLOOR_S) return true
            }
        }
        lp.vel = v
        o.vel = v
        o.pos = p
        if (v.length() > 1.0) o.heading = v.normalized(o.heading)
        // a frantic tail in the water and the air
        if (lp.phase < 3) { o.amp = 0.11; o.tailHz = 9.0 }
        o.swimPhase = (o.swimPhase + o.tailHz * dt) % 1000.0
        o.spline.override = null
        o.spline.update(o.pos, o.heading, dt, 2.0)
        return false
    }

    /** Back in the water (or it never made the surface): the brain takes over, still badly startled. */
    private fun land(o: Organism, lp: Leap, inside: Boolean): Boolean {
        o.leap = null
        o.vel = lp.vel * 0.2
        o.startle = max(o.startle, 0.4)
        o.heading = o.heading.horizontal().normalized(Vec3.X)
        o.spline.reset(o.pos, o.heading)
        return !inside
    }

    /** Horizontal launch range (cm) of a leap at [v] cm/s and [angle] rad that must clear [rimCm] above its exit (tests, captions). */
    fun reachCm(v: Double, angle: Double, rimCm: Double): Double {
        val vy = v * sin(angle); val vx = v * cos(angle)
        val disc = vy * vy - 2 * G * rimCm
        if (disc < 0) return 0.0
        // time from leaving the water until it falls back to rim height
        val t = (vy + sqrt(disc)) / G
        return vx * t
    }
}

/** What a leaping fish did ([SimEvent.Jumped]). */
enum class Jump { LEAPT, HIT_LID, OUT }
