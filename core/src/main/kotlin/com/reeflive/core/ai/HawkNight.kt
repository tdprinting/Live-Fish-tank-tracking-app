package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.Ev
import com.reeflive.core.sim.Evidence
import com.reeflive.core.sim.Organism
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The falco hawkfish's night rest (Tee's footage, docs/research/video/tee_tank_night.md T89 0:01–0:08 [H]; FishBase
 * "rests at coral-head bases", research 10 §5): at dusk it leaves its perch and comes down in short hops (it has no
 * swim bladder) to a spot of open sand within [ROCK_GAP_CM] of the rock base, settles propped on its pelvic fins and
 * thick lower pectoral rays with the head raised [PITCH] (15–20°), and stays there all night: body still, eyes and
 * head turning toward movement every 1–3 s. It keeps the same spot night after night (place memory, kind NIGHT).
 * A torch in the app is render-only, so nothing here startles it; a lights-on startle while resting is ignored.
 */
class SandRest(val hawk: HawkBrain) : Behavior("sandrest", "Resting on the sand") {
    private var spot: Vec3? = null
    private var heading = Vec3.X
    private var state = HOP
    private var hopT = -1.0
    private var hopDur = 0.5
    private var hopFrom = Vec3.ZERO
    private var hopTo = Vec3.ZERO
    private var pause = 0.0
    private var nextLook = 0.0
    private var look = 0.0
    private var lookPitch = 0.0
    /** Nights it has settled on the sand (tests). */
    var settles = 0; private set
    override val noisy: Boolean get() = false
    override val label: String get() = if (state == RESTING) "Resting on the sand" else "Hopping down to the sand"

    override fun score(c: Ctx): Double {
        val o = c.o
        if (!o.alive || c.activity > NIGHT_ACTIVITY) return 0.0
        // settled at night it ignores the lights-on flinch and the odd glance of light (Tee's torch clip)
        if (state == RESTING && hawk.startledRecently && hawk.startleMag <= 0.25) hawk.startleAt = -1e9
        return 0.85
    }

    override fun enter(c: Ctx) {
        val s = spot ?: findSpot(c, hawk).also { spot = it }
        state = if (s != null && c.o.pos.distanceTo(s) < 1.5) RESTING else HOP
        hopT = -1.0; pause = 0.0
        if (s != null) heading = restHeading(c, s)
    }

    override fun update(c: Ctx, out: SteerIntent) {
        val o = c.o
        val s = spot ?: run { finished = true; return }
        o.sleeping = c.p.night
        when (state) {
            HOP -> {
                if (pause > 0) {
                    pause -= c.dt
                    out.place(o.pos, o.heading, o.up, 6.0); out.posture = 1; out.pectoral = 1.0
                    return
                }
                if (hopT < 0) {
                    hopFrom = o.pos
                    hopTo = nextLanding(c, s)
                    hopT = 0.0
                    hopDur = (hopFrom.distanceTo(hopTo) / (HawkHop.HOP_BL_S * o.lengthCm)).coerceIn(0.25, 1.0)
                }
                hopT += c.dt
                val u = (hopT / hopDur).coerceIn(0.0, 1.0)
                val d = hopFrom.distanceTo(hopTo)
                var q = hopFrom.lerp(hopTo, u) + Vec3.Y * (4 * (0.25 * d + 0.8) * u * (1 - u))
                val clear = 0.5 * o.species.depthRatio * o.lengthCm
                if (c.rock.sdf.dist(q) < clear) q = c.rock.sdf.project(q, clear)
                out.place(q, horizontalHeading(hopTo - hopFrom, o.heading), null, 40.0)
                // tail flick to launch, fins folded in the air, pectorals spread to land
                out.ampMul = if (u < 0.3) 2.0 else 0.3; out.tailMul = if (u < 0.3) 1.5 else 0.5; out.pectoral = if (u > 0.8) 1.0 else 0.2
                if (u >= 1.0) {
                    hopT = -1.0
                    if (hopTo.distanceTo(s) < 0.5) { state = RESTING; settles++ } else pause = PAUSE_S
                }
            }
            else -> {
                // propped on the pelvic fins, head raised; small head and eye turns toward movement every 1–3 s
                if (c.t > nextLook) {
                    nextLook = c.t + 1.0 + 2.0 * (0.5 + 0.5 * sin(c.t * 1.7 + o.appendagePhase))
                    val mover = nearestMover(c)
                    look = if (mover != null) {
                        val to = (mover.pos - o.pos).horizontal()
                        val sgn = if (((heading cross to) dot Vec3.Y) > 0) 1.0 else -1.0
                        (Vec3.angle(heading, to) * sgn).coerceIn(-0.35, 0.35)
                    } else 0.25 * sin(c.t * 0.9 + o.appendagePhase)
                    lookPitch = if (mover != null && mover.pos.y > o.pos.y + 5) 0.06 else 0.0
                }
                val h = Vec3.rotateAbout(heading, Vec3.Y, 0.4 * look)
                val side = (h cross Vec3.Y).normalized(Vec3.Z)
                val pitch = PITCH + lookPitch
                out.place(s, Vec3.rotateAbout(h, side, pitch), Vec3.rotateAbout(Vec3.Y, side, pitch), 3.0)
                out.posture = 1; out.pectoral = 1.0; out.ampMul = 0.0; out.tailMul = 0.2
                o.eyeYaw = look * 0.8
            }
        }
    }

    override fun exit(c: Ctx) { state = HOP; hopT = -1.0 }

    /** The current night spot, if one has been chosen (tests, abstract mode). */
    fun spotOrNull(): Vec3? = spot

    /** Resting pose at the night spot (abstract mode): head-up on the sand. */
    fun restPose(c: Ctx): Rest? {
        val s = spot ?: findSpot(c, hawk).also { spot = it } ?: return null
        val h = restHeading(c, s)
        val side = (h cross Vec3.Y).normalized(Vec3.Z)
        return Rest(s, Vec3.rotateAbout(h, side, PITCH), Vec3.rotateAbout(Vec3.Y, side, PITCH), posture = 1)
    }

    /** Next landing on the way down: at most [HawkHop.HOP_MAX_BL] body lengths, on rock or sand (never mid-water). */
    private fun nextLanding(c: Ctx, goal: Vec3): Vec3 {
        val o = c.o
        val max = HawkHop.HOP_MAX_BL * o.lengthCm
        if (o.pos.distanceTo(goal) <= max) return goal
        val q = o.pos + (goal - o.pos).normalized(Vec3.Y) * max
        val off = 0.5 * o.species.depthRatio * o.lengthCm + 0.3
        val onRock = c.rock.sdf.project(q, off)
        // on the live sand (SandBed.surfaceY: 3.5-6 cm, not the flat 3 cm plane SandContact would lift it off every tick)
        val onSand = Vec3(q.x, c.sim.sand.surfaceY(q.x, q.z) + off, q.z)
        val rockOk = c.rock.inWater(onRock, 1.0) && onRock.distanceTo(q) < 6 && c.rock.sdf.gradient(onRock).y > 0.2
        return if (rockOk && onRock.distanceTo(q) < onSand.distanceTo(q)) onRock else c.rock.clampToWater(onSand, 1.0)
    }

    private fun nearestMover(c: Ctx): Organism? =
        nearestOther(c, LOOK_CM) { it.vel.length() > 1.0 }

    companion object {
        private const val HOP = 0
        private const val RESTING = 1
        /** Head-up pitch on the sand, rad (17.5°: Tee's clip 15–20° [H]). */
        @Ev(Evidence.SOURCED, "tee_tank_night.md T89 [H]: pitch +15-20 deg") const val PITCH = 0.305
        /** Sand spot within this distance of the rock base, cm (Tee's clip "within 10 cm" [H]). */
        @Ev(Evidence.SOURCED, "tee_tank_night.md T89 [H]: within 10 cm of rock") const val ROCK_GAP_CM = 10.0
        /** Below this activity (dusk) it goes down. */
        const val NIGHT_ACTIVITY = 0.3
        /** Pause between hops on the way down, s [L]. */
        @Ev(Evidence.DESIGN_ESTIMATE, "fish_falco_hawkfish: hop 2-4 BL then stop [L]") const val PAUSE_S = 1.5
        /** Eyes follow movement within this range, cm [L]. */
        const val LOOK_CM = 40.0

        /** Heading on the sand: facing away from the rock (out into the open), level. */
        fun restHeading(c: Ctx, s: Vec3): Vec3 = horizontalHeading(c.rock.sdf.gradient(s), Vec3.Z)

        /**
         * The resting body lies clear of the rock at [p]. It faces away from the rock ([restHeading]), so its body points
         * back toward it, and the head's 2.5 cm gap alone let a 6.5 cm body lie in a rock foot. Each station along the
         * body (head-up at [PITCH], yawed up to [BODY_YAW] either way for a bent trail and the head turns) is taken at
         * no lower than the live sand plus [SandContact] clearance (the sand holds the tail up there) and must keep
         * [BODY_CLEAR_CM] plus the body's half-width from the rock.
         */
        fun bodyClear(c: Ctx, o: Organism, p: Vec3): Boolean {
            val h0 = restHeading(c, p)
            val keep = BODY_CLEAR_CM + 0.35 * o.species.depthRatio * o.lengthCm
            val lie = com.reeflive.core.sim.SandContact.clearance(o) * 0.5
            for (yaw in doubleArrayOf(0.0, BODY_YAW, -BODY_YAW, 0.5 * BODY_YAW, -0.5 * BODY_YAW)) {
                val h = Vec3.rotateAbout(h0, Vec3.Y, yaw)
                val back = (h * cos(PITCH) + Vec3.Y * sin(PITCH)) * -1.0
                for (i in 1..6) {
                    var q = p + back * (i / 6.0 * o.lengthCm)
                    val floor = c.sim.sand.surfaceY(q.x, q.z) + lie
                    if (q.y < floor) q = q.withY(floor)
                    if (c.rock.sdf.dist(q) < keep) return false
                }
            }
            return true
        }
        /** Yaw either way the resting body is checked over (rad, ~20 deg), and the rock clearance it keeps (cm). */
        const val BODY_YAW = 0.35
        const val BODY_CLEAR_CM = 0.3

        /**
         * A night spot: open sand 2.5–[ROCK_GAP_CM] cm from the rock, with open water above, nearest the hawk's lowest
         * perch. Remembered across nights (PlaceKind.NIGHT). Deterministic search (no rng).
         */
        fun findSpot(c: Ctx, hawk: HawkBrain): Vec3? {
            hawk.mind.places.best(PlaceKind.NIGHT, c.t)?.let { return it.pos }
            val o = c.o
            val off = 0.5 * o.species.depthRatio * o.lengthCm + 0.3
            val base = hawk.lowestSheltered().pos
            var best: Vec3? = null
            var bs = Double.MAX_VALUE
            for (ring in 1..8) for (k in 0 until 16) {
                val a = 2 * PI * k / 16
                val r = ring * 4.0
                val x = base.x + cos(a) * r; val z = base.z + sin(a) * r * 0.8
                val p = Vec3(x, c.sim.sand.surfaceY(x, z) + off, z)
                if (!c.rock.inWater(p, 1.5)) continue
                val gap = c.rock.sdf.dist(p)
                if (gap < 2.5 || gap > ROCK_GAP_CM) continue
                if (c.rock.sdf.dist(p + Vec3.Y * 4.0) < 2.0) continue
                if (!bodyClear(c, o, p)) continue
                val score = p.distanceTo(base) + 0.5 * gap
                if (score < bs) { bs = score; best = p }
            }
            best?.let { hawk.mind.places.note(PlaceKind.NIGHT, it, 1.0, c.t) }
            return best
        }
    }
}

/** Hop constants shared by the hawkfish's hops (video fish_falco_hawkfish: "hop 2-4 BL then stop", burst 3-5 BL/s [L]). */
object HawkHop {
    @Ev(Evidence.DESIGN_ESTIMATE, "fish_falco_hawkfish [L]") const val HOP_BL_S = 3.0
    const val HOP_MAX_BL = 3.0
}
