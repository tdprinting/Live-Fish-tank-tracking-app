package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.Anchor
import com.reeflive.core.sim.Organism
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * Engineer goby, Pholidichthys leucotaenia (juvenile, black/yellow stripes). A shy, peaceful
 * burrower that lives under the rock: it excavates sand from beneath the rock and spits the
 * mouthfuls out as small piles near the burrow mouth. By day juveniles hover in a loose group
 * near the mouth picking plankton; at night they withdraw deep into the tunnels. Anguilliform
 * swimmer (same spline deformation as an eel). Never a predator (SIMULATION §4.5, owner correction).
 */
class GobyBrain(c: Ctx) : SpeciesBrain(c) {
    companion object {
        /** Adult mode from this length (06 §1.5): burrow-bound by day and night (07 gap 3). */
        const val ADULT_CM = 15.0
        const val GOBY_MOUTHFUL_CM3 = 0.6
        /** Chance an adult patrols outside on a given night: [I] ≈5 % (07 gap 3; 06 had 40 %, unsupported). */
        const val ADULT_NIGHT_PATROL_P = 0.05
        /** Peeking head swivel, rad, and the head-shake rate while spitting sand, Hz (DESIGN ESTIMATES). */
        const val PEEK_YAW = 0.35
        const val SPIT_HZ = 6.0
    }
    val burrow: Anchor? = c.rock.anchors["BURROW_GOBY"] ?: c.rock.anchors["CAVE_EEL"]
    val mouth: Vec3 = burrow?.pos ?: Vec3(69.0, 5.0, 23.0)
    val inDir: Vec3 = (burrow?.dir ?: Vec3(0.5, 0.1, -0.85)).normalized()
    val outDir: Vec3 = (inDir * -1.0).let { Vec3(it.x, 0.05, it.z).normalized(Vec3.Z) }
    val tunnel: List<Vec3> = (c.rock.anchors["burrow_path"] ?: c.rock.anchors["den_path"])?.points
        ?: listOf(mouth, mouth + inDir * 8.0, mouth + inDir * 16.0)
    /** Individual offset around the mouth so a group spreads out (loose aggregation). */
    private val slot = Vec3(c.rng.signed() * 5, c.rng.range(1.5, 6.0), c.rng.signed() * 3)

    init {
        install(InBurrow(), MouthHover(), Excavate(), NightPatrol(), Feed(this, 18.0, "Picking plankton"),
            Flee(this, { _, _ -> outside(1.5) }, "Darting into its burrow"), SurfaceGasp(this))
    }

    /** Adults (≥ [ADULT_CM]) are burrow-bound; juveniles hover and feed near the mouth by day (06 §1.5, 07 gap 3). */
    val adult: Boolean get() = o.lengthCm >= ADULT_CM
    /** Sand mouthfuls spat out (burrow maintenance events, 06 §5.6 #14). */
    var sandEvents = 0
    /** Whether tonight is one of the rare nights an adult patrols outside (rolled at lights-off). */
    var patrolTonight = false
    private var lastNightRoll = -1e9

    /** Roll the adult night patrol once per night (p = [ADULT_NIGHT_PATROL_P], [I] from 07 gap 3). */
    fun rollNight(c: Ctx) {
        if (c.p.lightsOffAt <= lastNightRoll) return
        lastNightRoll = c.p.lightsOffAt
        patrolTonight = adult && c.rng.chance(ADULT_NIGHT_PATROL_P)
    }

    /** Where the excavated sand comes from: the dig zone at the burrow mouth (a pit in the sand bed, SIMULATION §16). */
    val digPoint: Vec3 get() = c.rock.anchors["SAND_GOBY_DIG"]?.pos ?: outside(1.0)

    fun outside(k: Double) = Vec3(mouth.x, max(mouth.y, c.rock.sandTopCm + 1.0), mouth.z) + outDir * k
    fun inside(k: Double) = mouth + inDir * k

    /** Body trailing back down the tunnel from the head. */
    fun tunnelSpline(head: Vec3): List<Vec3> = listOf(head, mouth) + tunnel.drop(1)

    override fun restSpot(c: Ctx, night: Boolean): Rest {
        val head = if (night) inside(4.0) else outside(0.5)
        return Rest(head, if (night) inDir else outDir, posture = 3, spline = tunnelSpline(head))
    }

    override fun findThreat(c: Ctx): Organism? = super.findThreat(c)

    /**
     * In the burrow: deep and out of sight at night; by day it peeks with just the head out (research video
     * fish_engineer_goby [S1][M]), easing in and out and swivelling the head and eyes to look around.
     */
    inner class InBurrow : Behavior("burrow", "In its burrow") {
        private var lookYaw = 0.0
        private var nextLook = 0.0
        override fun score(c: Ctx): Double {
            val o = c.o
            return when {
                c.p.night -> 0.85
                o.waryLevel > 0.3 || o.startle > 0.2 -> 0.7
                adult -> 0.75
                else -> 0.25 + 0.3 * o.skittishness
            }
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val o = c.o
            val night = c.p.night
            if (o.posture != 3 && o.pos.distanceTo(mouth) > 4) {
                out.seek(outside(1.0), o.cruiseSpeed, arrive = 2.0, urgency = 0.5)
                return
            }
            val head = if (night) inside(4.0) else outside(0.6 + 0.4 * sin(c.t * 0.3))
            if (!night && c.t > nextLook) { nextLook = c.t + c.rng.range(1.5, 4.0); lookYaw = c.rng.signed() * PEEK_YAW }
            val face = if (night) inDir else Vec3.rotateAbout(outDir, Vec3.Y, lookYaw)
            o.eyeYaw = if (night) 0.0 else lookYaw * 0.6
            out.place(head, face, Vec3.Y, 3.0)
            out.posture = 3
            out.splineOverride = tunnelSpline(o.pos)
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
            val spot = c.safe(outside(6.0) + slot + Vec3(sin(c.t * 0.11 + slot.x) * 2.0, sin(c.t * 0.07) * 1.0, 0.0), 1.0)
            out.seek(spot, o.cruiseSpeed * 0.5, arrive = 2.5)
            out.hover = true
            out.ampMul = 0.6
            out.separation = 1.5
            // pick drifting particles within a few cm
            val f = if (o.hunger > 0.05) c.food.nearest(o.pos, 6.0) { o.species.dietMatch(it.kind) > 0 } else null
            if (f != null) {
                out.seek(f.pos, o.cruiseSpeed * 1.5, arrive = 0.5, urgency = 0.6)
                if (o.pos.distanceTo(f.pos) < 1.2 && c.t > nextSnap) { c.bite(f); nextSnap = c.t + 1.0 }
            }
        }
    }

    /**
     * Excavation trip: duck into the burrow, emerge carrying a mouthful of sand, swim a few cm out
     * and spit it onto a growing pile (Simulation.depositSand → render sand piles).
     */
    inner class Excavate : Behavior("dig", "Moving sand out of its burrow") {
        private var phase = 0
        private var timer = 0.0
        private var dump = Vec3.ZERO
        private var nextAt = 0.0
        override val committed: Boolean get() = phase in 1..2
        override fun score(c: Ctx): Double = if (c.p.night || c.t < nextAt || c.o.waryLevel > 0.3) 0.0 else if (adult) 0.93 else 0.72
        override fun enter(c: Ctx) {
            phase = 0; timer = 0.0
            val side = (outDir cross Vec3.Y).normalized(Vec3.X) * (c.rng.signed() * 5.0)
            val d = c.safe(outside(c.rng.range(4.0, 9.0)) + side, 0.5)
            // spit just above the live sand surface, so the mouthful lands on the SandBed mound it builds
            dump = d.withY(c.sim.sand.surfaceY(d.x, d.z) + 1.2)
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val o = c.o
            timer += c.dt
            when (phase) {
                0 -> { // into the mouth
                    out.seek(outside(0.5), o.cruiseSpeed, arrive = 1.0)
                    if (o.pos.distanceTo(outside(0.5)) < 2.0) { phase = 1; timer = 0.0 }
                }
                1 -> { // digging inside, out of sight for a few seconds
                    out.place(inside(2.5), inDir, Vec3.Y, 4.0)
                    out.posture = 3
                    out.splineOverride = tunnelSpline(o.pos)
                    if (timer > 4.0 + 4.0 * c.rng.nextDouble()) {
                        // the mouthful leaves the pit as it turns to carry it out
                        digPoint.let { pit -> c.sim.sand.dig(pit.x, pit.z, GOBY_MOUTHFUL_CM3, 1.5) }
                        phase = 2; timer = 0.0
                    }
                }
                2 -> { // carry the mouthful out: cheeks full, head a little down, short wriggling swim
                    o.mouth = 0.45
                    out.seek(dump, o.cruiseSpeed * 0.8, arrive = 1.0, urgency = 0.4)
                    out.ampMul = 1.3
                    out.pitchBias = -0.15
                    if (o.pos.distanceTo(dump) < 2.0 || timer > 10) { phase = 3; timer = 0.0 }
                }
                else -> { // spit: head jerks side to side as the sand is blown out
                    o.mouth = 0.6 + 0.4 * sin(2 * PI * SPIT_HZ * timer)
                    out.seek(dump + Vec3.Y * 1.5, 0.5, arrive = 1.0)
                    out.pitchBias = -0.4
                    out.rollExtra = 0.25 * sin(2 * PI * SPIT_HZ * timer)
                    out.waddleAmp = 0.2; out.waddleHz = SPIT_HZ
                    if (timer > 0.6) {
                        c.sim.depositSand(dump, GOBY_MOUTHFUL_CM3)
                        sandEvents++
                        // adults: a maintenance trip every 0.7–2 h (≈ 5–15 per day); juveniles dig much more often
                        // (a mouthful every 20–40 s while working, research video fish_engineer_goby [S2][M]; with breaks)
                        nextAt = c.t + if (adult) c.rng.range(2500.0, 7000.0) else c.rng.range(25.0, 120.0)
                        finished = true
                    }
                }
            }
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
