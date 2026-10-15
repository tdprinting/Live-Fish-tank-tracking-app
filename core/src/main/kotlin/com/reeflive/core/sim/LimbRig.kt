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
 * [out] is [count] × vec4 (at most 40, shader `uLimb[40]`; FishRig.limbs) and travels to the renderer as `OrganismPose.limbs`.
 * Component x of every entry is a phase in cycles in [0, 1) or a scalar kept inside (0, 1) (the renderer interpolates
 * x across the 1 → 0 wrap, so a plain 1.0 or a negative value would wrap); y, z, w are plain scalars. The layout per
 * animal is documented on [BrittleArms], [CrabLegs], [HermitLegs] and [ShrimpLimbs]. Only rigs with at least [LIMBS]
 * entries are posed about model pivots (`uLimbOn`).
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

    /**
     * Surface contact after the move and [step] (SIMULATION §19.7), called by the sim once per tick for every living
     * animal with a rig: limbs that lie on or grip the substrate fit themselves to [s] (rock SDF, live sand bed,
     * glass) and drape over the [others] lying in their way. Default: nothing (the hermit's legs are planted by its
     * [CrawlerSeat] instead).
     */
    open fun contact(o: Organism, s: CrawlSurface, others: List<Organism>, dt: Double) {}

    /** The animal died or left the tank: drop any seat this rig owns ([Organism.seatOn]). */
    open fun unseat(o: Organism) {}

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

// BrittleArms (the brittle star's five arms) lives in BrittleArms.kt.

/**
 * Sally Lightfoot legs and chelae (research 17 §3). Walking legs step in an alternating tetrapod (Barnes 1975,
 * crabs walking sideways): L1 R2 L3 R4 against R1 L2 R3 L4, half a cycle apart, with a small rear-to-front lag on
 * each side. Leading legs (the side it walks toward) pull by flexing in stance, trailing legs push by extending
 * (Clarac & Coulmance 1971). The chelae pick at algae in alternating strokes.
 *
 * Out, legs k = 0..7 (pair k/2 from the front, even = left, odd = right): (step phase, extension stroke signed
 * (− = leading, pulls), foot lift rad, tuck 0..1 (legs drawn in, body pressed flat)); claws 8 (left), 9 (right):
 * (pick phase, stroke 0..1, reach toward the rock 0..1, 0).
 */
class CrabLegs : LimbRig() {
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
        // stride frequency from speed: a stride ≈ 0.45 carapace widths, 1.5–7 Hz (crabs speed up mostly by stepping faster)
        strideHz = (speed / (STRIDE_W * L)).coerceIn(1.5, 7.0)
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
            val ph = frac(clock + 0.5 * group + METACHRONAL * (3 - pair))
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
        /** Leg-role swap rate (1/s): a full swap of the leading side takes ~0.4 s. */
        const val SIDE_RATE = 5.0
        const val METACHRONAL = 0.06
        /** Claw picking rate per chela (Hz); the two alternate, so strokes reach the mouth at twice this [L]. */
        const val PICK_HZ = 1.6
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
