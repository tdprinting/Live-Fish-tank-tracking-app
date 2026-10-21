package com.reeflive.core.sim

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Bodies against the sand (SIMULATION §16.9). Run after every behaviour tick (and after [SandWork]): no animal's body
 * reference point (the head; its trail follows) ends a tick inside the live sand surface ([SandBed.surfaceY], which
 * digging lowers and spoil raises) unless it is burrowing ([exempt]). A body that touches the sand moves it instead of
 * passing through it: a low bow wave ahead of anything travelling in contact, and a resting impression with a rim
 * where a brittle star or a goby lies still. All of it is mass-conserving sand transfer in [SandBed].
 *
 * Swap point: when the shared collision layer (`BodyCollision.resolveEnv` reading `SandBed.surfaceY`) lands, [clamp]
 * becomes its sand term and [exempt] its per-cell exemption; the displacement half stays here. Uses no random numbers.
 */
class SandContact(private val sand: SandBed, private val rock: com.reeflive.core.geom.Sdf? = null) {
    private class St(var x: Double, var z: Double, var acc: Double, var rest: Double, var impressed: Boolean, var pre: Double = 0.0)
    private val st = HashMap<String, St>()
    private var gc = 0

    /** Ticks on which a body was lifted out of the sand (tests, tuning). */
    var lifts = 0L; private set

    /** Lifts [o] onto the live sand surface (clearance [clearance]); returns how deep (cm) it was in. */
    fun clamp(o: Organism): Double {
        if (exempt(o)) return 0.0
        val p = o.pos
        val need = sand.surfaceY(p.x, p.z) + clearance(o)
        val pen = need - p.y
        if (pen <= 0.0) return 0.0
        var lift = pen
        val sdf = rock
        // a swimmer pinched between the sand and a rock overhang: the steering pushes it out of the rock, down onto the
        // flat sandTopCm floor, and the lift puts it straight back, each tick (a tang frozen 95 min in the gap under a
        // ledge at the back glass). The sand moves it aside instead, to the nearest spot it fits.
        if (sdf != null && o.isFish && !o.attached) {
            val clear = SWIM_ROCK_MIN * o.collisionLength
            val dp = sdf.dist(p)
            if (sdf.dist(p.withY(need)) < min(dp, clear)) {
                val q = pinchExit(o, p, pen, min(dp, clear), sdf)
                if (q != null) {
                    o.pos = q
                    if (o.vel.y < 0.0) o.vel = o.vel.withY(0.0)
                    o.spline.update(o.pos, o.heading, 0.0, 0.0)
                    lifts++
                    return pen
                }
            }
        }
        // never lift a body into rock: wedged between rock and sand (a crevice at the rock foot) the rock wins
        if (sdf != null) {
            val keep = min(sdf.dist(p), ROCK_KEEP)
            if (sdf.dist(p.withY(need)) < keep) {
                var lo = 0.0; var hi = pen
                repeat(6) { val m = 0.5 * (lo + hi); if (sdf.dist(p.withY(p.y + m)) >= keep) lo = m else hi = m }
                lift = lo
            }
        }
        if (lift < pen - 1e-4) {
            // wedged between rock and sand: the body pushes the sand out from under itself (mass-conserving)
            // (radius at least a cell, so the shoved sand lands outside the cells the surface under the head reads)
            val r = max(max(sand.cellX, sand.cellZ), 0.5 * bodyWidth(o))
            val q = p.withY(p.y + lift)
            var k = 0
            while (k++ < 6) {
                val left = sand.surfaceY(q.x, q.z) + clearance(o) - q.y
                if (left <= 1e-4) break
                sand.bowWave(q.x, q.z, o.heading.x, o.heading.z, r, left * PI * r * r * 0.6)
            }
            // whatever sand could not be shoved (bed at its floor): the head rides on it, inside the rock margin
            lift = max(lift, min(pen, sand.surfaceY(p.x, p.z) - p.y))
            if (lift <= 1e-4) return pen
        }
        o.pos = p.withY(p.y + lift)
        if (o.vel.y < 0.0) o.vel = o.vel.withY(0.0)
        // the spline's head follows (no new trail point, no relaxation)
        o.spline.update(o.pos, o.heading, 0.0, 0.0)
        lifts++
        return pen
    }

    /** Nearest point within about [pen] of [p], resting on the sand, with [keep] cm to rock and off the glass; null if none. */
    private fun pinchExit(o: Organism, p: com.reeflive.core.geom.Vec3, pen: Double, keep: Double, sdf: com.reeflive.core.geom.Sdf): com.reeflive.core.geom.Vec3? {
        val m = (0.25 * o.lengthCm).coerceIn(0.6, 4.0)
        val c = clearance(o)
        for (k in 1..3) {
            val r = max(0.5, 0.5 * k * pen)
            var best: com.reeflive.core.geom.Vec3? = null
            var bestD = keep
            for (i in 0 until 8) {
                val a = i * PI / 4
                val x = p.x + kotlin.math.cos(a) * r; val z = p.z + kotlin.math.sin(a) * r
                if (x < m || x > sand.lengthCm - m || z < m || z > sand.widthCm - m) continue
                val q = com.reeflive.core.geom.Vec3(x, sand.surfaceY(x, z) + c, z)
                val d = sdf.dist(q)
                if (d >= bestD) { bestD = d; best = q }
            }
            if (best != null) return best
        }
        return null
    }

    /**
     * [clamp] ahead of the rock guard (Simulation): the guard then lays the body where it will lie on the live sand, not
     * at the flat sand plane the crawl step puts it on (the lift came after the guard, and a rigid brittle star lifted
     * 1.5 cm onto the live sand at a rock foot had its body in the rock). [step] keeps the depth for its bow wave.
     */
    fun preClamp(o: Organism) {
        val pen = clamp(o)
        if (pen > 0.0) st.getOrPut(o.id) { St(o.pos.x, o.pos.z, 0.0, 0.0, false) }.pre = pen
    }

    private var floorClear = 0.0
    private val floor = object : com.reeflive.core.geom.BodySpline.Floor {
        override fun y(x: Double, z: Double) = sand.surfaceY(x, z) + floorClear
        // over a buried rock foot the point stays in the sand (the rock guard counts it as lying in the sand): lifted
        // after the guard it came up inside the rock (a grazing lawnmower's tail 1 cm into the foot of the rock)
        override fun canLift(p: com.reeflive.core.geom.Vec3, to: Double): Boolean {
            val sdf = rock ?: return true
            return sdf.dist(p.withY(to)) >= ROCK_KEEP
        }
    }

    /**
     * The rest of the body on the sand too: trail points under the live surface rise onto it at up to [TRAIL_LIFT_CM_S]
     * (the head is [clamp]'s). Only for a body within a length of the sand; the override paths (dens, burrows) are drawn
     * as they are.
     */
    private fun liftBody(o: Organism, dt: Double) {
        if (exempt(o) || !o.alive) return
        val p = o.pos
        if (p.y - sand.surfaceY(p.x, p.z) > o.lengthCm) return
        floorClear = 0.5 * clearance(o)
        o.spline.liftTrail(floor, TRAIL_LIFT_CM_S * max(dt, 0.05))
    }

    /** Clamp everyone; with [displace] (live behaviour, not abstract placement) the contacts also move sand. */
    fun step(organisms: List<Organism>, dt: Double, displace: Boolean) {
        if (++gc % 400 == 0) { val ids = organisms.mapTo(HashSet()) { it.id }; st.keys.retainAll(ids) }
        for (o in organisms) {
            if (o.leap != null) continue   // in the air or out on the floor (JumpModel)
            val early = st[o.id]
            val pre = early?.pre ?: 0.0
            if (early != null) early.pre = 0.0
            val pen = max(clamp(o), pre)
            liftBody(o, dt)
            if (!displace || !o.alive) continue
            val p = o.pos
            val t = st.getOrPut(o.id) { St(p.x, p.z, 0.0, 0.0, false) }
            val dx = p.x - t.x; val dz = p.z - t.z
            val moved = sqrt(dx * dx + dz * dz)
            t.x = p.x; t.z = p.z
            if (exempt(o) || moved > 6.0) { t.acc = 0.0; t.rest = 0.0; continue }   // burrowing, or placed rather than moved
            val contact = pen > 0.0 || p.y - clearance(o) <= sand.surfaceY(p.x, p.z) + CONTACT_TOL
            if (!contact) { t.acc = 0.0; t.rest = 0.0; t.impressed = false; continue }
            val w = bodyWidth(o)
            if (moved > MOVING_CM_S * dt) {
                t.rest = 0.0; t.impressed = false
                if (o.brainKind in TRAIL_KINDS) continue   // SandWork draws their trails already
                t.acc += moved
                val step = max(0.6, 0.25 * o.lengthCm)
                if (t.acc >= step) {
                    t.acc = 0.0
                    // a skimming body shoves a thin layer; one pressed in (pen > 0) shoves what it would have overlapped
                    val vol = (BOW_DEPTH + 0.5 * pen.coerceAtMost(0.3)) * w * step
                    sand.bowWave(p.x, p.z, dx, dz, max(0.5, 0.5 * w), vol)
                }
            } else {
                t.rest += dt
                if (t.rest > REST_S && !t.impressed && o.brainKind in IMPRESSERS) {
                    t.impressed = true
                    if (o.isFish) {
                        // the body lies along its spline: three pressed patches from head to tail
                        val r = max(0.6, 0.8 * w)
                        val vol = IMPRESS_DEPTH * PI * r * r * 0.5
                        for (f in doubleArrayOf(0.2, 0.5, 0.8)) {
                            val q = p - o.heading * (f * o.lengthCm)
                            sand.impress(q.x, q.z, r, vol)
                        }
                    } else {
                        val r = max(0.8, 0.25 * o.lengthCm)
                        sand.impress(p.x, p.z, r, IMPRESS_DEPTH * PI * r * r * 0.5)
                    }
                }
            }
        }
    }

    companion object {
        /**
         * Buried (posture 5: nassarius, sand-sleeping wrasse, bobtail squid), flagged by its brain as burrowing, a
         * feather duster (its tube is anchored down in the sand; the crown stands above it), or a diamond goby in its own
         * burrow under a rock (its burrow is not dug into the grid; the engineer goby's are).
         */
        fun exempt(o: Organism): Boolean = o.posture == 5 || o.inSand || o.brainKind == BrainKind.FEATHER_DUSTER || o.brainKind == BrainKind.ANEMONE || o.brainKind == BrainKind.FILE_CLAM || o.brainKind == BrainKind.GIANT_CLAM ||
            o.brainKind == BrainKind.DIAMOND_GOBY && o.posture == 3

        /** Height of the body reference point over the sand when the body rests on it (cm). */
        fun clearance(o: Organism): Double =
            if (o.isFish) (0.5 * o.species.depthRatio * o.lengthCm).coerceIn(0.2, 3.0) else CRAWLER_CLEARANCE

        fun bodyWidth(o: Organism): Double =
            if (o.isFish) (0.7 * o.species.depthRatio * o.lengthCm).coerceIn(0.3, 4.0) else (0.5 * o.lengthCm).coerceIn(0.3, 6.0)

        /** Crawlers' reference point sits on their feet / foot (cm). */
        @Ev(Evidence.DESIGN_ESTIMATE) const val CRAWLER_CLEARANCE = 0.15
        /** Fastest a trail point under the sand is raised onto it (cm/s; at 20 Hz 0.3 cm a tick, below any body pop). */
        const val TRAIL_LIFT_CM_S = 6.0
        /** Within this (cm) of the surface the body touches the sand. */
        const val CONTACT_TOL = 0.12
        /** Clearance from rock a lift must not eat into (cm). */
        const val ROCK_KEEP = 0.3
        /** A swimmer's rock clearance in body lengths (Steering's hard resolve, 0.45 L). */
        const val SWIM_ROCK_MIN = 0.45
        /** Below this speed (cm/s) a body is resting. */
        const val MOVING_CM_S = 0.3
        /** Sand shoved per step of contact travel: a 0.2 mm layer over the body's width (cm). */
        @Ev(Evidence.DESIGN_ESTIMATE) const val BOW_DEPTH = 0.02
        /** A still body leaves its impression after this long (s), pressed about this deep (cm). */
        const val REST_S = 20.0
        @Ev(Evidence.DESIGN_ESTIMATE) const val IMPRESS_DEPTH = 0.12
        private val TRAIL_KINDS = setOf(BrainKind.NASSARIUS, BrainKind.CONCH, BrainKind.HERMIT, BrainKind.SALLY_CRAB, BrainKind.ARROW_CRAB, BrainKind.EMERALD_CRAB, BrainKind.BRITTLE_STAR)
        private val IMPRESSERS = setOf(BrainKind.BRITTLE_STAR, BrainKind.ENGINEER_GOBY, BrainKind.DIAMOND_GOBY)
    }
}
