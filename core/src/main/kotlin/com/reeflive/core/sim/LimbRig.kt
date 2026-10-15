package com.reeflive.core.sim

import com.reeflive.core.geom.Rng
import com.reeflive.core.geom.Vec3
import com.reeflive.core.geom.smoothstep
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Per-limb pose sent from the sim to the renderer (SIMULATION §19, research 17): the brittle star's five arms and
 * the Sally Lightfoot's eight walking legs and two chelae, each posed about a pivot in the model, and the two
 * shrimp's limb-class signals. The sim owns the gait (which arm leads, which legs are in stance, where each claw is
 * in its picking stroke, the shrimp's stride clock and signals); the renderer only turns [out] into vertex motion.
 *
 * [out] is [count] × vec4 (at most [LIMBS]) and travels to the renderer as `OrganismPose.limbs` (shader `uLimb[]`).
 * Component x of every entry is a phase in cycles in [0, 1) or a slowly varying scalar (the renderer interpolates
 * x across the 1 → 0 wrap); y, z, w are plain scalars. The layout per animal is documented on [BrittleArms],
 * [CrabLegs] and [ShrimpLimbs]. Only the full [LIMBS]-entry rigs are posed about model pivots (`uLimbOn`).
 *
 * Arm angles are measured in the disc plane, counter-clockwise seen from above the animal: 0 = the model's +X
 * (`heading`), positive toward its left (`up × heading`, the model's −Z). Arm k of the brittle star model lies at
 * 2πk/5.
 */
abstract class LimbRig(val count: Int = LIMBS) {
    val out = FloatArray(count * 4)

    /** Intended travel this tick (cm/s, world), set by Steering.crawl before the disc pulse is applied. */
    var travel: Vec3 = Vec3.ZERO

    /** Advances the gait one step and refreshes [out]. [speed] is the animal's speed over the surface (cm/s). */
    abstract fun step(o: Organism, dt: Double, speed: Double)

    /** Multiplier on the crawl velocity this tick (the brittle star's disc moves in pulses; 1 = steady). */
    open val pulse: Double get() = 1.0

    protected fun put(i: Int, x: Double, y: Double, z: Double, w: Double) {
        out[i * 4] = x.toFloat(); out[i * 4 + 1] = y.toFloat(); out[i * 4 + 2] = z.toFloat(); out[i * 4 + 3] = w.toFloat()
    }

    companion object {
        const val LIMBS = 10

        fun forKind(kind: BrainKind, seed: Long): LimbRig? = when (kind) {
            BrainKind.BRITTLE_STAR -> BrittleArms(seed)
            BrainKind.SALLY_CRAB -> CrabLegs()
            BrainKind.HERMIT -> HermitLegs(seed)
            BrainKind.BANDED_SHRIMP, BrainKind.CLEANER_SHRIMP -> ShrimpLimbs()
            BrainKind.BOBTAIL_SQUID -> SquidRig()
            else -> null
        }

        fun frac(v: Double) = v - floor(v)

        /** Wraps an angle to (−π, π]. */
        fun wrap(a: Double): Double {
            var x = a % (2 * PI)
            if (x > PI) x -= 2 * PI
            if (x <= -PI) x += 2 * PI
            return x
        }

        /** Body frame in the surface plane: forward (heading), up, left (`up × forward`). */
        fun frame(o: Organism): Triple<Vec3, Vec3, Vec3> {
            val u = o.up.normalized(Vec3.Y)
            val f = o.heading.rejectFrom(u).normalized(o.heading)
            val l = (u cross f).normalized(Vec3.Z)
            return Triple(f, u, l)
        }
    }
}

/**
 * Black brittle star arms (research 17 §2). Rowing (Astley 2012, O. echinata, 75 % of cycles): the centre arm points
 * along the travel and barely works, the two arms either side of it make mirrored power strokes (≈ 45° of sweep,
 * contralateral lag within ±13 %), the two rear arms trail with ≈ 13°. Reverse rowing (25 %): the centre arm trails
 * and all four others push. The disc moves for ≈ 45 % of each 0.23 Hz cycle and is static for the rest, and a turn
 * is a new centre arm chosen during a static phase; the disc never rotates.
 *
 * Out, arm k = 0..4: limb k = (wave phase, base yaw rad, pitch rad (+ = tip up), reach 0..1 (0 = coiled under the
 * disc)); limb 5 + k = (arm length 0..1 after autotomy, wave amplitude rad, tip curl rad, 0).
 */
class BrittleArms(seed: Long) : LimbRig() {
    private val rng = Rng(seed xor 0xB5A7L)
    /** Index of the centre arm: the leading arm when rowing, the trailing arm when reverse rowing. */
    var lead = 0; private set
    var reverse = false; private set
    /** Times the centre arm was reassigned while travelling (the disc never turns instead). */
    var leadSwitches = 0; private set
    /** Rowing cycle phase (cycles). */
    var rowPhase = 0.0; private set
    var moving = 0.0; private set

    // ---- set by the brain each tick (reset in [step] after use) ----
    /** Arms extended; the rest stay coiled under the disc (day hide: 1–3 tips out). */
    var outArms = ARMS
    /** World direction the extended arms reach toward (cave mouth, food); null = no preference. */
    var outDir: Vec3? = null
    /** Disturbed: every arm pulls in fast. */
    var withdraw = false
    /** Arm-snaking intensity 0..1 (searching for a scent, emerging). */
    var snake = 0.0
    /** Arm looping a food item ( −1 = none), the item's world position and how far the loop has closed (0..1). */
    var feedArm = -1
    var feedAt: Vec3? = null
    var feedLoop = 0.0

    /** Arm length after autotomy (1 = whole; regrows ≈ 0.4 mm/day, Soong 1997). */
    val armLen = DoubleArray(ARMS) { 1.0 }

    private val yaw = DoubleArray(ARMS)
    private val pitch = DoubleArray(ARMS)
    private val reach = DoubleArray(ARMS) { 1.0 }
    private val waveAmp = DoubleArray(ARMS) { 0.1 }
    private val curl = DoubleArray(ARMS)
    private val wavePhase = DoubleArray(ARMS) { it * 0.37 }
    private var travelling = false

    fun armAngle(k: Int) = 2 * PI * k / ARMS

    /** Direction of travel the current arm roles serve, as a disc angle (the centre arm, or opposite it when reverse rowing). */
    val travelAngle: Double get() = wrap(armAngle(lead) + if (reverse) PI else 0.0)

    /** World direction of arm [k] in the disc plane. */
    fun armDir(o: Organism, k: Int): Vec3 {
        val (f, _, l) = frame(o)
        val a = armAngle(k) + yaw[k]
        return (f * cos(a) + l * sin(a)).normalized(f)
    }

    /** World point at [s] (0 root … 1 tip) along arm [k]: straight-arm approximation of the rendered curve. */
    fun armPoint(o: Organism, k: Int, s: Double): Vec3 =
        o.pos + armDir(o, k) * (o.lengthCm * (ROOT_R + ARM_LEN * armLen[k] * reach[k].coerceAtLeast(0.3) * s))

    /** The arm whose direction is closest to the world direction [d]. */
    fun nearestArm(o: Organism, d: Vec3): Int {
        val (f, _, l) = frame(o)
        val b = atan2(d dot l, d dot f)
        return (0 until ARMS).minBy { abs(wrap(armAngle(it) - b)) }
    }

    val meanReach: Double get() = reach.average()
    fun reachOf(k: Int) = reach[k]

    override val pulse: Double
        get() {
            if (moving < 0.05) return 1.0
            val p = frac(rowPhase)
            // the disc surges in the stance and creeps in the swing; mean 1 over a cycle
            val stance = PULSE_FLOOR + (1 - PULSE_FLOOR) * (if (p < DUTY) PI / (2 * DUTY) * sin(PI * p / DUTY) else 0.0)
            return 1 + (stance - 1) * moving
        }

    override fun step(o: Organism, dt: Double, speed: Double) {
        val (f, _, l) = frame(o)
        val cruise = max(o.cruiseSpeed, 0.3)
        val tr = travel
        val trSpeed = tr.length()
        val r = trSpeed / cruise
        val target = smoothstep(0.08, 0.45, r)
        moving += (target - moving) * min(1.0, dt * 3)
        // Astley 2012: 0.23 ± 0.08 Hz at 1.3 cm/s, speed set mostly by cycle frequency
        val hz = IDLE_HZ + (o.motion.rowHz * r.coerceIn(0.6, 2.6) - IDLE_HZ) * moving
        rowPhase = (rowPhase + hz * dt) % 1000.0
        val p = frac(rowPhase)
        if (trSpeed > 0.1 * cruise) {
            val beta = atan2(tr dot l, tr dot f)
            if (!travelling) {
                // a new bout: the mode holds for the whole bout (Astley 2012), and the first centre arm is free
                travelling = true
                reverse = rng.chance(REVERSE_SHARE)
                lead = best(beta)
            } else {
                val b = best(beta)
                val off = abs(wrap(armAngle(lead) + (if (reverse) PI else 0.0) - beta))
                // turns only during the static disc phase, with hysteresis past the 36° half-sector
                if (b != lead && p >= DUTY && off > SWITCH_OFF) { lead = b; leadSwitches++ }
            }
        } else if (moving < 0.05) travelling = false

        val stanceProg = if (p < DUTY) smoothstep(0.0, DUTY, p) else 1 - smoothstep(DUTY, 1.0, p)
        val swingLift = if (p >= DUTY) sin(PI * (p - DUTY) / (1 - DUTY)) else 0.0
        val k0 = min(1.0, dt * 4)
        val od = outDir
        val outRank = if (outArms < ARMS && od != null) (0 until ARMS).sortedByDescending { armDir(o, it) dot od } else (0 until ARMS).toList()
        for (k in 0 until ARMS) {
            val j = (k - lead + ARMS) % ARMS
            var ty = 0.0
            var tp = -0.04
            var tw = 0.10 + 0.35 * snake
            var tc = if (k % 2 == 0) 0.25 else -0.25
            if (moving > 0.01) {
                val m = moving
                if (!reverse) {
                    when (j) {
                        0 -> { tp = 0.12; tw += 0.12 }                        // leading arm probes ahead
                        1, 4 -> { val s = if (j == 1) 1.0 else -1.0; val lag = if (j == 1) 0.0 else 0.04
                            val pp = frac(rowPhase + lag)
                            val prog = if (pp < DUTY) smoothstep(0.0, DUTY, pp) else 1 - smoothstep(DUTY, 1.0, pp)
                            ty = s * (-0.06 + FORE_SWEEP * prog) * m
                            tp = 0.32 * (if (pp >= DUTY) sin(PI * (pp - DUTY) / (1 - DUTY)) else 0.0) * m - 0.05 }
                        else -> { val s = if (j == 2) 1.0 else -1.0
                            ty = s * (0.12 + HIND_SWEEP * (stanceProg - 0.5)) * m; tw *= 0.5 }   // dragged behind
                    }
                } else {
                    when (j) {
                        0 -> { tw *= 0.4; tc *= 0.3 }                           // trailing arm drags straight
                        else -> { val s = if (j == 1 || j == 2) -1.0 else 1.0
                            val amp = if (j == 2 || j == 3) 0.75 else 0.5
                            ty = s * (-0.05 + amp * stanceProg) * m
                            tp = 0.28 * swingLift * m - 0.05 }
                    }
                }
            }
            var tr2 = 1.0
            if (outArms < ARMS) {
                val rank = outRank.indexOf(k)
                if (rank >= outArms) { tr2 = 0.22; tw = 0.03; tp = -0.08 } else { tw = max(tw, 0.22); tp = max(tp, 0.08) }
            }
            if (k == feedArm) {
                feedAt?.let { fa ->
                    val d = fa - o.pos
                    val b = atan2(d dot l, d dot f)
                    ty = wrap(b - armAngle(k)).coerceIn(-1.1, 1.1)
                }
                tr2 = 1.0; tp = 0.05; tw = 0.06
                tc = (if (k % 2 == 0) 1.0 else -1.0) * (0.25 + 2.4 * feedLoop)   // the tip loops round the item
            }
            if (withdraw) { tr2 = 0.12; tw = 0.02 }
            val kr = if (withdraw) min(1.0, dt * 6) else min(1.0, dt * 0.8)
            yaw[k] += (ty - yaw[k]) * min(1.0, dt * 8)
            pitch[k] += (tp - pitch[k]) * k0
            reach[k] += (tr2 - reach[k]) * kr
            waveAmp[k] += (tw - waveAmp[k]) * k0
            curl[k] += (tc - curl[k]) * min(1.0, dt * 2.5)
            // travelling bend wave from root to tip; faster while snaking
            wavePhase[k] = (wavePhase[k] + dt * (0.22 + 0.55 * snake + 0.03 * k)) % 1000.0
            put(k, frac(wavePhase[k]), yaw[k], pitch[k], reach[k])
            put(ARMS + k, armLen[k], waveAmp[k], curl[k], 0.0)
        }
        // brain inputs are per tick
        outArms = ARMS; outDir = null; withdraw = false; snake = 0.0; feedArm = -1; feedAt = null; feedLoop = 0.0
        travel = Vec3.ZERO
    }

    private fun best(beta: Double): Int {
        val want = if (reverse) beta + PI else beta
        return (0 until ARMS).minBy { abs(wrap(armAngle(it) - want)) }
    }

    /** Autotomy (research 17 §2.6): sheds the distal part of arm [k], keeping [keep] of its length. */
    fun shed(k: Int, keep: Double) { armLen[k] = min(armLen[k], keep.coerceIn(0.15, 0.9)) }

    /** Regrowth over [dt] s for an animal of [spanCm] arm span: ≈ 0.4 mm/day (O. scolopendrina, Soong 1997). */
    fun regrow(dt: Double, spanCm: Double) {
        val armCm = max(0.5, spanCm * ARM_LEN)
        for (k in 0 until ARMS) if (armLen[k] < 1.0) armLen[k] = min(1.0, armLen[k] + dt * REGROW_CM_PER_DAY / 86400.0 / armCm)
    }

    companion object {
        const val ARMS = 5
        /** Model geometry (fraction of arm span): disc-arm junction radius and arm length (research 17 §2.1). */
        const val ROOT_R = 0.05
        const val ARM_LEN = 0.45
        /** Share of each cycle the disc is moving (Astley 2012 disc duty 0.42–0.46). */
        const val DUTY = 0.45
        /** Forelimb sweep ≈ 45° and hindlimb ≈ 13° per stroke (Astley 2012). */
        const val FORE_SWEEP = 0.78
        const val HIND_SWEEP = 0.22
        const val REVERSE_SHARE = 0.25
        /** Disc creep during the swing phase, as a share of the mean speed. */
        const val PULSE_FLOOR = 0.2
        const val IDLE_HZ = 0.06
        /** Centre-arm reassignment once the travel is this far (rad) off its sector (36° + margin). */
        const val SWITCH_OFF = 0.78
        const val REGROW_CM_PER_DAY = 0.04
    }
}

/**
 * Sally Lightfoot legs and chelae (research 17 §3, research 27). Walking legs step in an alternating tetrapod (Barnes
 * 1975, crabs walking sideways): L1 R2 L3 R4 against R1 L2 R3 L4, half a cycle apart, each foot touching down a
 * little after the one behind it (footfalls run back to front, Herreid & Full 1986). Leading legs (the side it walks
 * toward) pull by flexing in stance, trailing legs push by extending (Clarac & Coulmance 1971). The chelae pick at
 * algae in alternating strokes.
 *
 * Out, legs k = 0..7 (pair k/2 from the front, even = left, odd = right): (step phase, extension stroke signed
 * (− = leading, pulls), foot lift rad, tuck 0..1 (legs drawn in, body pressed flat)); claws 8 (left), 9 (right):
 * (pick phase, stroke 0..1, reach toward the rock 0..1, 0).
 *
 * Seated on a surface ([CrawlerSeat], written after [step] each tick; all zero when not seated), the legs are planted:
 * 10–17 = leg k's IK (0.3 + 0.5 flex, hip rotation as axis × angle in model space; the swing is the first
 * [CrawlerSeat.CRAB_SWING] of the step phase, duty factor 0.6), leg k's w = fold of a leg with no clear pose, and
 * 18 = (0, left / right chela max dip (rad), 1 = legs planted).
 */
class CrabLegs : LimbRig(COUNT) {
    var clock = 0.0; private set
    private var moving = 0.0
    private var side = 1.0
    /** [side] eased (−1..1): the legs swap leading/trailing roles over ~0.4 s, not in one tick. */
    private var sideS = 1.0
    private var speedS = 0.0
    private var tuck = 0.0
    private var pickS = 0.0
    private var clawClock = 0.0
    private var lastClaw = 0.0
    /** Completed claw strokes (both chelae), for tests and the HUD. */
    var pickStrokes = 0L; private set
    /** Stride frequency of the last step (Hz). */
    var strideHz = 0.0; private set
    /** Stepping 0..1 (0 = standing still: every foot planted). */
    val stepping: Double get() = moving
    /** Pressed flat 0..1 (eased). */
    val tuckAmt: Double get() = tuck

    // ---- set by the brain each tick ----
    /** Claw picking 0..1 (grazing film / hair algae, scraping a carcass). */
    var pick = 0.0
    /** Pressed flat into a crevice, legs drawn in. */
    var tucked = false

    override fun step(o: Organism, dt: Double, speed: Double) {
        val (f, u, _) = frame(o)
        val right = f cross u
        val L = max(o.lengthCm, 1.0)
        val m = smoothstep(0.3, 2.0, speed)
        moving += (m - moving) * min(1.0, dt * 8)
        // stride frequency from speed: a stride ≈ 0.45 carapace widths at 1.5–4.5 Hz (crabs speed up mostly by stepping
        // faster, research 27: walking 1.5–4 Hz); faster than that the strides lengthen instead (Herreid & Full 1986), which
        // also keeps every step visible in the 20 Hz pose stream (at 7 Hz a swing fell between two snapshots)
        strideHz = (speed / (STRIDE_W * L)).coerceIn(1.5, MAX_HZ)
        if (moving > 0.02) clock = (clock + strideHz * dt * moving) % 1000.0
        val v = if (travel.lengthSq() > 1e-6) travel else o.vel
        // swap leading / trailing legs only on a clear change of side (a course near the body axis flipped it every tick)
        if (v.lengthSq() > 0.04) {
            val s = (v dot right) / v.length()
            if (s > 0.25) side = 1.0 else if (s < -0.25) side = -1.0
        }
        sideS += (side - sideS) * min(1.0, dt * SIDE_RATE)
        speedS += (speed - speedS) * min(1.0, dt * 8)
        tuck += ((if (tucked) 1.0 else 0.0) - tuck) * min(1.0, dt * 5)
        val extAmp = (0.25 + 0.4 * min(1.0, speedS / (4 * L))) * moving
        val lift = 0.38 * moving
        for (k in 0 until 8) {
            val pair = k / 2
            val isRight = k % 2 == 1
            val group = (pair + (if (isRight) 1 else 0)) % 2
            // (the rear leg leads in phase: footfalls run back to front along each side)
            val ph = frac(clock + 0.5 * group + METACHRONAL * pair)
            // −1 = leading (pulls), +1 = trailing (pushes)
            val role = if (isRight) -sideS else sideS
            put(k, ph, role * extAmp, lift, tuck)
        }
        pickS += (pick - pickS) * min(1.0, dt * 6)
        val hz = PICK_HZ * (0.6 + 0.4 * pickS)
        if (pickS > 0.05) clawClock = (clawClock + hz * dt) % 1000.0
        if (floor(clawClock * 2) != floor(lastClaw * 2) && pickS > 0.3) pickStrokes++
        lastClaw = clawClock
        val reachC = 0.25 + 0.65 * pickS
        put(8, frac(clawClock), pickS, reachC, 0.0)
        put(9, frac(clawClock + 0.5), pickS, reachC, 0.0)
        pick = 0.0; tucked = false; travel = Vec3.ZERO
    }

    companion object {
        const val STRIDE_W = 0.45
        const val MAX_HZ = 4.5
        /** Leg-role swap rate (1/s): a full swap of the leading side takes ~0.4 s. */
        const val SIDE_RATE = 5.0
        const val METACHRONAL = 0.06
        /** Claw picking rate per chela (Hz); the two alternate, so strokes reach the mouth at twice this [L]. */
        const val PICK_HZ = 1.6
        /** Pose entries: the ten limb entries plus eight planted-leg IK entries and one for the chelae and the planting flag ([CrawlerSeat]). */
        const val COUNT = 19
    }
}

/**
 * Hermit crab (Clibanarius tricolor, Paguristes cadenati; docs/research/hermit_crab_motion.md). Only the chelipeds and
 * the two walking-leg pairs (pereiopods 2–3) are outside the shell; the reduced 4th–5th pairs grip the columella.
 * It walks forward, not sideways: the legs step as diagonal pairs (left P2 with right P3), and the shell rolls
 * from side to side with each shift of load and bobs twice per stride. A startled hermit snaps into the shell in a
 * fraction of a second; it comes back out slowly, eyestalks and antennae first, the walking legs last.
 *
 * Layout ([out], 10 × vec4, `fish.vert` hermitLimb, motion mode 5):
 * - legs k = 2 pair + (0 left, 1 right), pair 0 = P2: (step phase, fore-aft stroke (rad), foot lift, retract);
 * - chelae 4 left, 5 right: (pick phase, pick 0..1, walking support 0..1, retract);
 * - 6 shell: (stride phase, roll amplitude (rad), pitch bob (rad), 0);
 * - 7 shield and eyestalks: (eye scan phase, scan 0..1, body bob, retract);
 * - 8 antennae: (sweep phase, sweep 0..1, 0, retract); 9 antennules: (flick phase, flick 0..1, 0, retract).
 *
 * Surface contact ([CrawlerSeat], written after [step] each tick; all zero when the crab is not seated): 6.w eyestalk
 * bend, 8.z antenna bend, 9.z antennule bend (0..1, away from rock); 10–13 planted legs: leg k = (0.3 + 0.5 flex,
 * hip rotation as axis × angle in model space); 14 = (0, left / right chela max dip (rad), 1 = legs planted).
 */
class HermitLegs(seed: Long = 0L) : LimbRig(COUNT) {
    private val rng = Rng(seed xor 0x4E2A1L)
    var clock = 0.0; private set
    private var moving = 0.0
    private var pickS = 0.0
    private var clawClock = 0.0
    private var lastClaw = 0.0
    private var scan = 0.0
    private var sweep = 0.0
    private var flick = 0.0
    private var flickAmt = 0.0
    private var burstLeft = 0.0
    private var nextBurst = 1.0
    /** 0 out .. 1 fully withdrawn into the shell. */
    var retract = 0.0; private set
    /** Completed claw strokes (both chelae), for tests. */
    var pickStrokes = 0L; private set
    /** Stride frequency of the last step (Hz). */
    var strideHz = 0.0; private set

    // ---- set by the brain each tick ----
    /** Claws picking film, algae or a food crumb to the mouth 0..1. */
    var pick = 0.0
    /** Following a scent (antennules flick continuously). */
    var searching = false

    override fun step(o: Organism, dt: Double, speed: Double) {
        val L = max(o.lengthCm, 1.0)
        val m = smoothstep(0.15, 1.0, speed)
        moving += (m - moving) * min(1.0, dt * 6)
        strideHz = (speed / (STRIDE_L * L)).coerceIn(0.8, 3.5)
        if (moving > 0.02) clock = (clock + strideHz * dt * moving) % 1000.0
        // withdraw fast (≈ 0.15 s), re-emerge slowly (≈ 3 s)
        val want = (1.0 - o.extension).coerceIn(0.0, 1.0)
        retract += (want - retract) * min(1.0, dt * (if (want > retract) 12.0 else 0.35))
        val r = retract
        val rFront = smoothstep(0.45, 1.0, r)     // eyes and antennae: first out
        val rClaw = smoothstep(0.2, 0.8, r)
        val rLeg = smoothstep(0.0, 0.6, r)        // walking legs: last out
        val stroke = (0.28 + 0.12 * min(1.0, speed / (2 * L))) * moving
        val lift = 0.9 * moving
        for (k in 0 until 4) {
            val pair = k / 2
            val right = k % 2
            val ph = frac(clock + 0.5 * ((pair + right) % 2) + METACHRONAL * pair)
            put(k, ph, stroke, lift, rLeg)
        }
        pickS += (max(pick, min(1.0, o.mouth)) - pickS) * min(1.0, dt * 5)
        if (pickS > 0.05) clawClock = (clawClock + PICK_HZ * (0.6 + 0.4 * pickS) * dt) % 1000.0
        if (floor(clawClock * 2) != floor(lastClaw * 2) && pickS > 0.3) pickStrokes++
        lastClaw = clawClock
        put(4, frac(clawClock), pickS, moving, rClaw)
        put(5, frac(clawClock + 0.5), pickS, moving, smoothstep(0.1, 0.7, r))
        put(6, frac(clock), ROLL * moving, 0.03 * moving * sin(4 * PI * clock), 0.0)
        scan = (scan + dt * (0.15 + 0.1 * pickS)) % 1000.0
        // the eyestalks and antennae bob once per stride, smaller at a fast patter: at twice the stride rate the bob ran up
        // to 7 Hz, faster than the 20 Hz pose stream can carry, and flicked between extremes from one snapshot to the next
        val bob = 0.6 * moving * min(1.0, BOB_MAX_HZ / strideHz)
        put(7, frac(scan), 1.0 - 0.6 * moving, bob * sin(2 * PI * clock + 0.6), rFront)
        sweep = (sweep + dt * (if (searching || pickS > 0.3) 0.8 else 0.35)) % 1000.0
        put(8, frac(sweep), 0.5 + 0.5 * (if (searching) 1.0 else moving), 0.0, rFront)
        // antennule flicking comes in bursts with pauses (Waldrop & Koehl 2016); continuous while tracking a scent
        nextBurst -= dt
        if (nextBurst <= 0) { burstLeft = rng.range(1.0, 2.5); nextBurst = burstLeft + rng.range(2.0, 6.0) }
        burstLeft -= dt
        val on = if (searching || burstLeft > 0) 1.0 else 0.0
        flickAmt += (on - flickAmt) * min(1.0, dt * 8)
        flick = (flick + dt * FLICK_HZ) % 1000.0
        put(9, frac(flick), flickAmt * (1 - rFront), 0.0, rFront)
        pick = 0.0; searching = false; travel = Vec3.ZERO
    }

    companion object {
        /** Stride length as a fraction of shell length (INFERENCE from small decapod walking, docs/research). */
        const val STRIDE_L = 0.3
        const val METACHRONAL = 0.08
        /** Shell roll per stride at full walking (rad, ≈ 6°). */
        const val ROLL = 0.1
        const val PICK_HZ = 1.2
        const val FLICK_HZ = 2.5
        /** Stride rate (Hz) above which the eyestalk bob shrinks, keeping it smooth at the 20 Hz pose rate. */
        const val BOB_MAX_HZ = 1.5
        /** Pose entries: the ten limb entries plus four for the planted legs and one for the chelae and the planting flag ([CrawlerSeat]). */
        const val COUNT = 15
    }
}

/**
 * Banded coral shrimp and skunk cleaner shrimp limbs (research 17 shrimp motion detail). The shrimp models carry a
 * limb class per vertex (ASSETS §2.8 limb rig) and `fish.vert` (motion mode 2) animates each class procedurally, so
 * the sim only sends one entry of whole-animal signals:
 *
 * limb 0 = (stride phase (cycles, from `Organism.walkPhase`, set by Steering.crawl), stepping 0..1 (`walkAmt`),
 * signal 0..1 = max(skunk front-leg rock `legRock`, banded claw display `clawDisplay`), antenna grooming `groom`).
 */
class ShrimpLimbs : LimbRig(1) {
    /** The brains switch the signals on and off in one tick; the legs and antennae ease in and out (~0.25 s). */
    private var signal = 0.0
    private var groom = 0.0

    override fun step(o: Organism, dt: Double, speed: Double) {
        val k = min(1.0, dt * EASE_RATE)
        signal += (max(o.legRock, o.clawDisplay) - signal) * k
        groom += (o.groom - groom) * k
        put(0, frac(o.walkPhase), o.walkAmt, signal, groom)
    }

    companion object {
        const val EASE_RATE = 8.0
    }
}
