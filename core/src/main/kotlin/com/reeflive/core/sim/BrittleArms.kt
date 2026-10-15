package com.reeflive.core.sim

import com.reeflive.core.geom.Rng
import com.reeflive.core.geom.Vec3
import com.reeflive.core.geom.smoothstep
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Black brittle star arms (research 17 §2; SIMULATION §19.2, §19.7).
 *
 * **Gait** (Astley 2012, *Ophiocoma echinata*). Rowing (75 % of bouts): the centre arm points along the travel and
 * probes with its tip a little up, the two arms either side of it make mirrored power strokes, the two rear arms trail
 * and push. Reverse rowing (25 %): the centre arm trails and all four others push. Each cycle (0.23 Hz at cruise) has a
 * stance, the first [DUTY] 45 %, and a recovery swing. In the stance every working arm grips: its tip stays where it
 * touched down and the arm bends about it (an IK on base swing and curl), so the disc is pulled past the planted tips
 * and the arm sweeps back by itself (≈ 45° for the rowing arms at cruise). The disc moves only in the stance ([pulse]
 * is 0 in the swing). In the swing the arm lifts its tip and reaches forward for its next hold. A turn is a new centre
 * arm chosen in the static phase; the disc never rotates.
 *
 * **Arm shape.** An arm is a chain of many short vertebrae, so it bends smoothly along its whole length and far more
 * sideways than up and down: base swing spread over the proximal arm with the distal part lagging, a travelling bend
 * wave root → tip, a curl that grows distally (S-curves when it opposes the swing, a loop round food), and S-folds when
 * drawn in. Vertically it only lifts its tip (swing, probing, waving at the cave mouth) and otherwise lies on whatever
 * is under it.
 *
 * **Contact** ([contact], each tick after the move). The disc is seated on the surface under it (rock, live sand bed,
 * glass) and lifted clear of any bump under its rim ([Organism.seatPos]). Each arm is then marched out from the disc in
 * [SAMPLES] steps, the way the shader draws it: at each step the surface is found from the arm's own level along the
 * disc's up ([CrawlSurface.heightAt]: it follows a slope, climbs a bump of up to [CLIMB] per step and hangs at most
 * [DROP] per step over an edge), animals lying in the way are draped over, a step that would run into a wall (a rock
 * face, a cave side, the glass, the sand at the foot of a rock it is climbing) bends aside toward the side with room and
 * the rest of the arm follows the wall, and a lifted tip stays under a ceiling. The march's per-step direction and
 * height are what the renderer draws, so what is drawn is what was checked.
 *
 * **Out** ([COUNT] = 40 × vec4, `fish.vert` starArm): arm k owns entries 8k … 8k + 7, read as 32 floats f[0..31]:
 * - the eight x slots f[4i] (i = 0..7) carry the step-end heights y₁ … y₈, as 0.5 + 0.5 y (the renderer interpolates
 *   x as a wrapping phase, so x stays inside (0, 1));
 * - the 24 other slots, in order (f[1], f[2], f[3], f[5], …), carry the step directions ψ₁ … ψ₁₅ (rad from the arm's
 *   rest direction, + = toward the model's left, −Z), the heights y₉ … y₁₅ and the arm length 0..1 after autotomy.
 * Heights are model units (fraction of the span) along the disc's up from its ground plane (the arm's centreline is
 * [ARM_Y] above that). Each step is 1 / [SAMPLES] of the arm; its run shortens by its rise so the arm keeps its length.
 */
class BrittleArms(seed: Long) : LimbRig(COUNT) {
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
    /** Arms extended; the rest stay folded under the disc (day hide: 1–3 tips out). */
    var outArms = ARMS
    /** World direction the extended arms reach toward (cave mouth, food); null = no preference. */
    var outDir: Vec3? = null
    /** Disturbed: every arm pulls in fast. */
    var withdraw = false
    /** Arm-snaking intensity 0..1 (searching for a scent, emerging). */
    var snake = 0.0
    /** 0..1: the extended arms sweep slowly from side to side over the substrate (day hide: trawling for detritus). */
    var sweep = 0.0
    /** Arm looping a food item ( −1 = none), the item's world position and how far the loop has closed (0..1). */
    var feedArm = -1
    var feedAt: Vec3? = null
    var feedLoop = 0.0

    /** Arm length after autotomy (1 = whole; regrows ≈ 0.4 mm/day, Soong 1997). */
    val armLen = DoubleArray(ARMS) { 1.0 }

    // ---- arm shape (the gait's intent, before contact) ----
    private val yaw = DoubleArray(ARMS)
    private val pitch = DoubleArray(ARMS)
    private val reach = DoubleArray(ARMS) { 1.0 }
    private val waveAmp = DoubleArray(ARMS) { 0.1 }
    private val curl = DoubleArray(ARMS)
    private val wavePhase = DoubleArray(ARMS) { it * 0.37 }
    private var sweepPhase = frac(seed * 0.618034)
    private var travelling = false

    // ---- grips ----
    private val gripWant = BooleanArray(ARMS)
    private val grip = BooleanArray(ARMS)
    private val anchor = Array(ARMS) { Vec3.ZERO }
    private val ikYaw = DoubleArray(ARMS)
    private val ikCurl = DoubleArray(ARMS)
    private val wasStance = BooleanArray(ARMS)
    private val gotGrip = BooleanArray(ARMS)
    private val slipped = BooleanArray(ARMS)
    /** Shape at the end of the last hold, where the recovery swing starts from. */
    private val relYaw = DoubleArray(ARMS)
    private val relCurl = DoubleArray(ARMS)

    // ---- the drawn arms: per step direction (rad from rest), end height (model), support (cm), wall bend ----
    private val psiS = DoubleArray(ARMS * SAMPLES)
    private val yS = DoubleArray(ARMS * SAMPLES)
    /** The drawn step directions at the last contact: a step turns at most [PSI_STEP] from them per contact. */
    private val psiPrev = DoubleArray(ARMS * SAMPLES)
    private var easing = false
    private val supS = DoubleArray(ARMS * SAMPLES)
    private val carryS = DoubleArray(ARMS * SAMPLES)
    private val defl = DoubleArray(ARMS * SAMPLES)
    private val pref = DoubleArray(ARMS) { if (it % 2 == 0) 1.0 else -1.0 }
    private val blockedNow = IntArray(ARMS)
    private val bentNow = IntArray(ARMS)

    // ---- disc seat and frame of the last contact ----
    private var base = Vec3.ZERO
    private var upSm = Vec3.Y
    private var fS = Vec3.X
    private var uS = Vec3.Y
    private var rS = Vec3.Z
    private var spanL = 1.0
    private var seated = false

    // ---- scratch ----
    private val nx = DoubleArray(SAMPLES + 1)
    private val ny = DoubleArray(SAMPLES + 1)
    private val nz = DoubleArray(SAMPLES + 1)
    private val ta = DoubleArray(3)
    private val tb = DoubleArray(3)
    private val tc = DoubleArray(3)
    private val otherPos = arrayOfNulls<Vec3>(MAX_OTHERS)
    private val otherR = DoubleArray(MAX_OTHERS)
    private var nOthers = 0

    /** Holds taken and holds let go before the stance ended (the tip could no longer reach its hold), for tests. */
    var grips = 0L; private set
    var slips = 0L; private set
    /** Clearance (cm) of the disc's top and of its underside from rock, sand and glass at the last contact. */
    var discTopClearCm = 0.0; private set
    var discUnderCm = 0.0; private set

    fun armAngle(k: Int) = 2 * PI * k / ARMS

    /** Direction of travel the current arm roles serve, as a disc angle (the centre arm, or opposite it when reverse rowing). */
    val travelAngle: Double get() = wrap(armAngle(lead) + if (reverse) PI else 0.0)

    /** World direction of arm [k]'s base swing in the disc plane. */
    fun armDir(o: Organism, k: Int): Vec3 {
        val (f, _, l) = frame(o)
        val a = armAngle(k) + yaw[k]
        return (f * cos(a) + l * sin(a)).normalized(f)
    }

    /** The arm whose direction is closest to the world direction [d]. */
    fun nearestArm(o: Organism, d: Vec3): Int {
        val (f, _, l) = frame(o)
        val b = atan2(d dot l, d dot f)
        var best = 0
        var bd = Double.MAX_VALUE
        for (k in 0 until ARMS) { val e = abs(wrap(armAngle(k) - b)); if (e < bd) { bd = e; best = k } }
        return best
    }

    val meanReach: Double get() = reach.average()
    fun reachOf(k: Int) = reach[k]
    /** Tip lift of arm [k] (rad; + = up). */
    fun liftOf(k: Int) = pitch[k]
    /** Arm [k]'s tip is holding the substrate (stance grip). */
    fun gripping(k: Int) = grip[k]
    /** Steps of arm [k] that found no clear way at the last contact (pressed into a wall on every side). */
    fun blocked(k: Int) = blockedNow[k]
    /** Steps of arm [k] bent aside by a wall at the last contact. */
    fun bent(k: Int) = bentNow[k]
    /** Surface height (cm, along the disc's up from its ground plane) under step [j] = 1..[SAMPLES] of arm [k]. */
    fun supportCm(k: Int, j: Int): Double = supS[k * SAMPLES + j - 1]

    override val pulse: Double
        get() {
            if (moving < 0.05) return 1.0
            val p = frac(rowPhase)
            // the disc is pulled along in the stance only (mean 1 over a cycle) and rests while the arms recover
            val stance = if (p < DUTY) PI / (2 * DUTY) * sin(PI * p / DUTY) else 0.0
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
        // Astley 2012: 0.23 ± 0.08 Hz at 1.3 cm/s, speed set mostly by cycle frequency; each stance carries the disc
        // about [STRIDE_ARM] arm lengths (a ≈ 45° sweep of a planted arm), so a faster star cycles faster
        val armCm = max(0.5, ARM_LEN * o.lengthCm)
        val rowHz = max(o.motion.rowHz * 0.6, trSpeed / (STRIDE_ARM * armCm)).coerceAtMost(MAX_HZ)
        val hz = IDLE_HZ + (rowHz - IDLE_HZ) * moving
        rowPhase = (rowPhase + hz * dt) % 1000.0
        val p = frac(rowPhase)
        if (trSpeed > 0.1 * cruise) {
            val beta = atan2(tr dot l, tr dot f)
            if (!travelling) {
                // a new bout: the mode holds for the whole bout (Astley 2012), and the first centre arm is free
                travelling = true
                reverse = rng.chance(REVERSE_SHARE)
                lead = best(beta)
                releaseAll()
            } else {
                val b = best(beta)
                val off = abs(wrap(armAngle(lead) + (if (reverse) PI else 0.0) - beta))
                // turns only during the static disc phase, with hysteresis past the 36° half-sector
                if (b != lead && p >= DUTY && off > SWITCH_OFF) { lead = b; leadSwitches++; releaseAll() }
            }
        } else if (moving < 0.05) travelling = false

        val stanceProg = if (p < DUTY) smoothstep(0.0, DUTY, p) else 1 - smoothstep(DUTY, 1.0, p)
        val swingLift = if (p >= DUTY) sin(PI * (p - DUTY) / (1 - DUTY)) else 0.0
        val k0 = min(1.0, dt * 4)
        val od = outDir
        val ranked = outArms < ARMS && od != null
        sweepPhase = (sweepPhase + dt * SWEEP_HZ) % 1000.0
        for (k in 0 until ARMS) {
            val j = (k - lead + ARMS) % ARMS
            var ty = 0.0
            var tp = -0.04
            var tw = 0.10 + 0.35 * snake
            var tcu = if (k % 2 == 0) 0.25 else -0.25
            var want = false
            // this arm's own cycle phase (the right rowing arm lags) and whether it works in this gait
            var ph = p
            var works = false
            if (moving > 0.01) {
                val m = moving
                val working = m > GRIP_MOVING
                if (!reverse) {
                    when (j) {
                        0 -> { tp = 0.12; tw += 0.12 }                        // leading arm probes ahead, tip up
                        1, 4 -> {
                            // the rowing pair: grip and pull in the stance (the right arm a little behind), reach
                            // forward with the tip lifted in the swing
                            val s = if (j == 1) 1.0 else -1.0
                            val pp = frac(rowPhase - if (j == 1) 0.0 else ROW_LAG)
                            ph = pp; works = true
                            if (pp < DUTY) {
                                want = working
                                ty = s * (-PROTRACT + FORE_SWEEP * smoothstep(0.0, DUTY, pp)) * m; tp = -0.05; tw = 0.03
                            } else {
                                ty = s * -PROTRACT * m
                                tp = SWING_LIFT * sin(PI * (pp - DUTY) / (1 - DUTY)) * m - 0.05
                            }
                        }
                        else -> {
                            // rear pair: plant and push in the stance, drawn after the disc in the swing
                            val s = if (j == 2) 1.0 else -1.0
                            ty = s * (0.12 + HIND_SWEEP * (stanceProg - 0.5)) * m; tw *= 0.5; works = true
                            if (p < DUTY) { want = working; tw = 0.03 } else tp = 0.12 * swingLift * m - 0.05
                        }
                    }
                } else {
                    when (j) {
                        0 -> { tw *= 0.4; tcu *= 0.3 }                          // trailing arm drags straight
                        else -> {
                            val s = if (j == 1 || j == 2) -1.0 else 1.0
                            val amp = if (j == 2 || j == 3) 0.75 else 0.5
                            ty = s * (-0.05 + amp * stanceProg) * m
                            tp = 0.28 * swingLift * m - 0.05; works = true
                            if (p < DUTY) { want = working; tw = 0.03 }
                        }
                    }
                }
            }
            // the stance / swing bookkeeping of a working arm: a new stance clears the last one's hold; an arm that lost
            // its hold lifts its tip clear and waits for the next stance (it does not drag on the substrate); after a
            // hold the recovery swing carries the arm from where it let go to its next reach over the whole swing
            var recover = Double.NaN
            if (works) {
                val inStance = ph < DUTY
                if (inStance && !wasStance[k]) { gotGrip[k] = false; slipped[k] = false }
                wasStance[k] = inStance
                if (inStance && slipped[k]) { want = false; tp = 0.6 * SWING_LIFT * moving }
                if (!inStance && gotGrip[k]) recover = smoothstep(DUTY, RECOVER_END, ph)
            } else { wasStance[k] = false; gotGrip[k] = false; slipped[k] = false }
            var tr2 = 1.0
            if (outArms < ARMS) {
                val rank = if (ranked) rankToward(o, k, od!!) else k
                if (rank >= outArms) { tr2 = 0.22; tw = 0.03; tp = -0.08; want = false }
                else {
                    tw = max(tw, 0.22); tp = max(tp, 0.08)
                    // the tips out of the hide trawl slowly from side to side (research 17 §2.3)
                    ty += sweep * SWEEP_AMP * sin(2 * PI * sweepPhase + 1.9 * k)
                }
            }
            if (k == feedArm) {
                feedAt?.let { fa ->
                    val d = fa - o.pos
                    val b = atan2(d dot l, d dot f)
                    ty = wrap(b - armAngle(k)).coerceIn(-1.1, 1.1)
                }
                tr2 = 1.0; tp = 0.05; tw = 0.06; want = false
                tcu = (if (k % 2 == 0) 1.0 else -1.0) * (0.25 + 2.4 * feedLoop)   // the tip loops round the item
            }
            if (withdraw) { tr2 = 0.12; tw = 0.02; want = false }
            if (armLen[k] < 0.3) want = false
            gripWant[k] = want
            val kr = if (withdraw) min(1.0, dt * 6) else min(1.0, dt * 0.8)
            if (!recover.isNaN() && k != feedArm && !withdraw && outArms == ARMS) {
                yaw[k] = relYaw[k] + (ty - relYaw[k]) * recover
                curl[k] = relCurl[k] + (tcu - relCurl[k]) * recover
            } else if (!grip[k]) {
                yaw[k] += (ty - yaw[k]) * min(1.0, dt * 8)
                curl[k] += (tcu - curl[k]) * min(1.0, dt * 2.5)
            }
            pitch[k] += (tp - pitch[k]) * k0
            reach[k] += (tr2 - reach[k]) * kr
            waveAmp[k] += (tw - waveAmp[k]) * k0
            // travelling bend wave from root to tip; faster while snaking
            wavePhase[k] = (wavePhase[k] + dt * (0.22 + 0.55 * snake + 0.03 * k)) % 1000.0
            // with no surface contact yet (a fresh animal, a unit test) the arm is drawn as shaped, on a flat floor
            if (!seated) { freeArm(k); emit(k) }
        }
        // brain inputs are per tick
        outArms = ARMS; outDir = null; withdraw = false; snake = 0.0; sweep = 0.0; feedArm = -1; feedAt = null; feedLoop = 0.0
        travel = Vec3.ZERO
    }

    /** Rank of arm [k] among the five by how well it points along [d] (0 = best; ties by index). */
    private fun rankToward(o: Organism, k: Int, d: Vec3): Int {
        val (f, _, l) = frame(o)
        val df = f dot d; val dl = l dot d
        val dk = cos(armAngle(k)) * df + sin(armAngle(k)) * dl
        var rank = 0
        for (i in 0 until ARMS) {
            if (i == k) continue
            val di = cos(armAngle(i)) * df + sin(armAngle(i)) * dl
            if (di > dk || di == dk && i < k) rank++
        }
        return rank
    }

    private fun releaseAll() { for (k in 0 until ARMS) grip[k] = false }

    private fun best(beta: Double): Int {
        val want = if (reverse) beta + PI else beta
        var b = 0
        var bd = Double.MAX_VALUE
        for (k in 0 until ARMS) { val e = abs(wrap(armAngle(k) - want)); if (e < bd) { bd = e; b = k } }
        return b
    }

    // ------------------------------------------------------------------ arm shape and the drawn curve

    /**
     * Yaw of arm [k] at [s] from its rest direction: base swing spread over the proximal arm, bend wave, distal curl, and
     * the fold of an arm drawn in (reach < 1): S-folds that stay in the arm's own sector, so a tucked arm neither crosses
     * its neighbours nor reaches out (a curl one way coiled it into a loop across the next arm).
     */
    private fun armYaw(k: Int, s: Double, yawB: Double, curlB: Double): Double {
        val alt = if (k % 2 == 0) 1.0 else -1.0
        val coil = 1.0 - reach[k].coerceIn(0.0, 1.0)
        return yawB * (1.0 - 0.55 * s) + waveAmp[k] * s * sin(2 * PI * (1.3 * s - frac(wavePhase[k]))) +
            curlB * 2.0 * smoothstep(0.3, 1.0, s) + alt * coil * FOLD_AMP * sin(2 * PI * FOLD_WAVES * s)
    }

    /** Lift (pitch, rad) of arm [k] at [s]: the tip rises, the root does not. */
    private fun armLift(k: Int, s: Double): Double = pitch[k] * s * (0.4 + 0.6 * s)

    /** The arm as shaped on a flat floor (no contact): steps follow the shape, heights only the tip lift. */
    private fun freeArm(k: Int) {
        val seg = ARM_LEN / SAMPLES
        var free = 0.0
        for (j in 1..SAMPLES) {
            val sm = (j - 0.5) / SAMPLES
            free += sin(armLift(k, sm)) * seg
            val i = k * SAMPLES + j - 1
            psiS[i] = armYaw(k, sm, yaw[k], curl[k]); yS[i] = max(free, 0.0); supS[i] = 0.0; carryS[i] = 0.0
        }
    }

    /** Writes arm [k]'s drawn steps to [out] (layout in the class doc). */
    private fun emit(k: Int) {
        val o = k * 32
        val b = k * SAMPLES
        for (i in 0 until 8) out[o + 4 * i] = (0.5 + 0.5 * yS[b + i].coerceIn(-Y_MAX, Y_MAX)).toFloat()
        for (j in 0 until SAMPLES) out[o + slot(j)] = psiS[b + j].toFloat()
        for (j in 8 until SAMPLES) out[o + slot(SAMPLES + j - 8)] = yS[b + j].coerceIn(-Y_MAX, Y_MAX).toFloat()
        out[o + slot(22)] = armLen[k].toFloat()
        out[o + slot(23)] = 0f
    }

    /** n-th plain (non-x) slot of an arm's 32 floats. */
    private fun slot(n: Int) = 4 * (n / 3) + 1 + n % 3

    /**
     * Model-space nodes of arm [k]'s drawn curve into [nx]/[ny]/[nz]: from the sent steps, or ([trial]) with the shape's
     * yaw at base swing [yawB] and curl [curlB] plus the last contact's wall bends and heights (the grip IK's model).
     */
    private fun nodes(k: Int, trial: Boolean, yawB: Double, curlB: Double) {
        val a0 = armAngle(k)
        val seg = ARM_LEN / SAMPLES
        nx[0] = ROOT_R * cos(a0); ny[0] = 0.0; nz[0] = -ROOT_R * sin(a0)
        for (j in 1..SAMPLES) {
            val i = k * SAMPLES + j - 1
            val psi = a0 + if (trial) armYaw(k, (j - 0.5) / SAMPLES, yawB, curlB) + carryS[i] else psiS[i]
            val y = yS[i].coerceIn(-Y_MAX, Y_MAX)
            val dy = y - ny[j - 1]
            val hz = sqrt(max(seg * seg - dy * dy, MIN_RUN * seg * seg))
            nx[j] = nx[j - 1] + cos(psi) * hz; ny[j] = y; nz[j] = nz[j - 1] - sin(psi) * hz
        }
    }

    /** Point at [s] on the curve through the last [nodes] (Catmull-Rom, as `fish.vert`), model space; y = centreline. */
    private fun curveAt(k: Int, s: Double, res: DoubleArray) {
        val sN = min(s.coerceIn(0.0, 1.0), armLen[k].coerceIn(0.1, 1.0)) * SAMPLES
        val j = min(floor(sN).toInt(), SAMPLES - 1)
        val t = sN - j
        res[0] = cr(nx, j, t); res[1] = ARM_Y + cr(ny, j, t); res[2] = cr(nz, j, t)
    }

    private fun cr(n: DoubleArray, j: Int, t: Double): Double {
        val p1 = n[j]; val p2 = n[j + 1]
        val p0 = if (j == 0) 2 * p1 - p2 else n[j - 1]
        val p3 = if (j + 2 > SAMPLES) 2 * p2 - p1 else n[j + 2]
        val t2 = t * t; val t3 = t2 * t
        return 0.5 * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3)
    }

    private fun world(mx: Double, my: Double, mz: Double): Vec3 = base + (fS * mx + uS * my + rS * mz) * spanL

    /** World point at [s] (0 root … 1 tip) on arm [k]'s centreline as drawn (after the last [contact]). */
    fun armPoint(o: Organism, k: Int, s: Double): Vec3 {
        if (!seated) {
            return o.pos + armDir(o, k) * (o.lengthCm * (ROOT_R + ARM_LEN * armLen[k] * reach[k].coerceAtLeast(0.3) * s))
        }
        nodes(k, false, 0.0, 0.0)
        curveAt(k, s, ta)
        return world(ta[0], ta[1], ta[2])
    }

    /** World point where arm [k]'s drawn tip touches (its centreline less the arm's own half-thickness). */
    fun tipContact(k: Int): Vec3 {
        nodes(k, false, 0.0, 0.0)
        curveAt(k, 1.0, ta)
        return world(ta[0], ta[1] - ARM_Y, ta[2])
    }

    // ------------------------------------------------------------------ contact

    override fun unseat(o: Organism) {
        if (seated) { o.seatOn = false; seated = false }
        releaseAll()
    }

    override fun contact(o: Organism, s: CrawlSurface, others: List<Organism>, dt: Double) {
        val L = max(o.lengthCm, 1.0)
        // the disc tilts to the rock under it at a turning rate, not in a tick (rough rock jitters the surface normal,
        // which would swing the whole star and wrench its holding tips off their holds)
        val target = o.up.normalized(Vec3.Y)
        upSm = if (!seated) target else {
            val ang = acos((upSm dot target).coerceIn(-1.0, 1.0))
            val maxA = UP_RATE * dt
            if (ang <= maxA || ang < 1e-6) target else (upSm * sin(ang - maxA) + target * sin(maxA)).normalized(target)
        }
        val u = upSm
        val f = o.heading.rejectFrom(u).normalized(fS)
        fS = f; uS = u; rS = f cross u; spanL = L
        seatDisc(o, s)
        easing = seated
        if (!seated) for (k in 0 until ARMS) freeArm(k)
        seated = true
        o.seatOn = true; o.seatPos = base; o.seatUp = u
        gatherOthers(o, s, others, L)
        for (k in 0 until ARMS) {
            val b = k * SAMPLES
            psiS.copyInto(psiPrev, b, b, b + SAMPLES)
            holdGrip(k, L)
            march(k, s, L, dt)
            if (grip[k] && offHold(k) > GRIP_TOL * L) {
                // the lie of the ground bent the holding arm off its hold: solve again with those bends, else let go
                holdGrip(k, L)
                if (grip[k]) march(k, s, L, 0.0)
                if (grip[k] && offHold(k) > 2 * GRIP_TOL * L) { grip[k] = false; slips++; slipped[k] = true }
            }
            emit(k)
        }
    }

    /** How far (cm, in the disc plane) holding arm [k]'s drawn tip lies from its hold. */
    private fun offHold(k: Int): Double {
        val d = tipContact(k) - anchor[k]
        val f = d dot fS; val r = d dot rS
        return sqrt(f * f + r * r)
    }

    /** The disc rests on the surface under it, lifted clear of any bump under its rim. */
    private fun seatDisc(o: Organism, s: CrawlSurface) {
        val u = uS
        val t = s.cast(o.pos, u * -1.0, 3.0)
        base = when {
            t > 0 -> o.pos - u * t
            t == 0.0 -> o.pos
            else -> o.pos - u * 0.4
        }
        for (i in 0 until 4) {
            val m = discMin(s, false)
            if (m >= -0.005) break
            base += u * (-m + 0.01)
        }
        discUnderCm = discMin(s, false)
        discTopClearCm = discMin(s, true)
    }

    /** Lowest clearance of the disc's underside ring and centre ([top]: its dome and upper rim). */
    private fun discMin(s: CrawlSurface, top: Boolean): Double {
        val y = if (top) 0.02 else DISC_FOOT_Y
        val r = if (top) 0.7 * DISC_R else DISC_R
        var m = s.dist(world(0.0, if (top) 0.032 else DISC_FOOT_Y, 0.0))
        for (i in 0 until 10) {
            val a = 2 * PI * i / 10
            m = min(m, s.dist(world(r * cos(a), y, r * sin(a))))
        }
        return m
    }

    /** Other animals lying on the substrate within the arms' reach (the arms drape over them). */
    private fun gatherOthers(o: Organism, s: CrawlSurface, others: List<Organism>, L: Double) {
        nOthers = 0
        for (q in others) {
            if (q === o || !q.alive || !q.visible) continue
            val r = OTHER_R * q.collisionLength
            val reachCm = (ROOT_R + ARM_LEN) * L + r
            if (q.pos.distanceSqTo(o.pos) > reachCm * reachCm) continue
            if (s.dist(q.pos) > r + 0.5) continue          // swimming over, not lying in the way
            otherPos[nOthers] = q.pos; otherR[nOthers] = r
            if (++nOthers == MAX_OTHERS) break
        }
    }

    /** Stance grip: the tip holds where it touched down and the arm bends about it while the disc is pulled past. */
    private fun holdGrip(k: Int, L: Double) {
        if (!gripWant[k]) { grip[k] = false; return }
        // an arm bent against a wall or with nowhere to lie cannot take a hold there
        if (!grip[k] && (blockedNow[k] > 0 || bentNow[k] > 0)) return
        if (!grip[k]) {
            // touch-down: the hold is where the tip is now, on the surface
            anchor[k] = tipContact(k)
            ikYaw[k] = yaw[k]; ikCurl[k] = curl[k]
            grip[k] = true; grips++
        }
        val d = anchor[k] - base
        if (d.length() > (ROOT_R + ARM_LEN) * L * 1.05 || blockedNow[k] > 0 || !solveGrip(k, (d dot fS) / L, (d dot rS) / L)) {
            grip[k] = false; slips++; slipped[k] = true
            return
        }
        yaw[k] = ikYaw[k]; curl[k] = ikCurl[k]
        relYaw[k] = yaw[k]; relCurl[k] = curl[k]; gotGrip[k] = true
    }

    private fun trialTip(k: Int, y: Double, c: Double, res: DoubleArray) {
        nodes(k, true, y, c)
        curveAt(k, 1.0, res)
    }

    /** Base swing and curl that put arm [k]'s drawn tip on (tx, tz) in the disc plane (Newton, finite differences). */
    private fun solveGrip(k: Int, tx: Double, tz: Double): Boolean {
        var y = ikYaw[k]
        var c = ikCurl[k]
        for (it in 0 until IK_ITERS) {
            trialTip(k, y, c, ta)
            val ex = tx - ta[0]; val ez = tz - ta[2]
            if (ex * ex + ez * ez < 0.04 * GRIP_TOL * GRIP_TOL) break
            trialTip(k, y + IK_EPS, c, tb)
            trialTip(k, y, c + IK_EPS, tc)
            val a = (tb[0] - ta[0]) / IK_EPS; val b = (tc[0] - ta[0]) / IK_EPS
            val cc = (tb[2] - ta[2]) / IK_EPS; val dd = (tc[2] - ta[2]) / IK_EPS
            val det = a * dd - b * cc
            if (abs(det) < 1e-12) break
            val dy = ((dd * ex - b * ez) / det).coerceIn(-0.4, 0.4)
            val dc = ((-cc * ex + a * ez) / det).coerceIn(-0.4, 0.4)
            y = (y + dy).coerceIn(-IK_YAW_MAX, IK_YAW_MAX)
            c = (c + dc).coerceIn(-IK_CURL_MAX, IK_CURL_MAX)
        }
        trialTip(k, y, c, ta)
        val ex = tx - ta[0]; val ez = tz - ta[2]
        if (ex * ex + ez * ez > GRIP_TOL * GRIP_TOL) return false
        ikYaw[k] = y; ikCurl[k] = c
        return true
    }

    /**
     * Marches arm [k] out from the disc in [SAMPLES] steps, the way the shader draws it, fitting each step to the surface
     * under it (see the class doc) and storing the drawn step directions and heights.
     */
    private fun march(k: Int, s: CrawlSurface, L: Double, dt: Double) {
        val len = armLen[k].coerceIn(0.1, 1.0)
        val seg = ARM_LEN / SAMPLES                 // model units
        val spacing = seg * L                       // cm
        val maxUp = CLIMB * spacing
        val maxDown = DROP * spacing
        val a0 = armAngle(k)
        var cx = ROOT_R * cos(a0)
        var cz = -ROOT_R * sin(a0)
        var yDraw = 0.0                              // model, drawn (support + lift)
        var sup = 0.0                                // cm, the surface the arm lies on
        var free = 0.0
        var carry = 0.0
        var blocked = 0
        var bent = 0
        val relax = DEFL_RELAX * dt
        for (j in 1..SAMPLES) {
            val i = k * SAMPLES + j - 1
            val sm = (j - 0.5) / SAMPLES
            free += sin(armLift(k, sm)) * seg
            val shape = armYaw(k, sm, yaw[k], curl[k])
            if ((j - 1).toDouble() / SAMPLES >= len - 1e-9) {
                // past the stump (autotomy): carried along level, never drawn
                psiS[i] = shape + carry; yS[i] = yDraw; supS[i] = sup; carryS[i] = carry; defl[i] = 0.0
                continue
            }
            val prev = defl[i]
            // (a holding arm keeps its bends: relaxing them would drag its tip off the hold)
            val relaxed = if (grip[k]) prev else if (prev > 0) max(0.0, prev - relax) else min(0.0, prev + relax)
            val p = pref[k]
            // a step turns at most PSI_STEP from where it was drawn last contact: a wall met all at once (the disc set
            // down in its hide, a crab walking onto the arm) is worked round over a few ticks, not in one snap
            val lo = if (easing) psiPrev[i] - shape - carry - PSI_STEP else -1e9
            val hi = if (easing) psiPrev[i] - shape - carry + PSI_STEP else 1e9
            var took = Double.NaN
            var supN = sup
            for (n in 0 until CANDIDATES) {
                val cand = (when (n) {
                    0 -> relaxed
                    1 -> prev
                    else -> prev + DEFL_TRY[(n - 2) / 2] * (if (n % 2 == 0) p else -p)
                }).coerceIn(-DEFL_MAX, DEFL_MAX).coerceIn(lo, hi)
                if (n == 1 && cand == relaxed) continue
                val psi = a0 + shape + carry + cand
                val h = stepHeight(s, psi, cx, cz, seg, sup, yDraw, free, L, maxUp, maxDown)
                if (h > maxUp) continue
                took = cand; supN = sup + h
                break
            }
            if (took.isNaN()) {
                // pressed into rock on every side (a crevice, an overhang): the step goes wherever it is least buried,
                // bending harder, rising or sinking along the up
                var best = -1e9
                took = relaxed.coerceIn(lo, hi); supN = sup
                for (n in 0 until FALLBACK_DEFL.size) {
                    val cand = (prev + FALLBACK_DEFL[n] * p).coerceIn(-DEFL_MAX, DEFL_MAX).coerceIn(lo, hi)
                    val psi = a0 + shape + carry + cand
                    val c = cos(psi); val sn = sin(psi)
                    for (m in -1..1) {
                        val supT = sup + m * (if (m > 0) maxUp else maxDown)
                        val dy = supT / L + max(free, 0.0) - yDraw
                        val hz = sqrt(max(seg * seg - dy * dy, MIN_RUN * seg * seg))
                        val d = clearOf(s, world(cx + c * hz, ARM_Y + supT / L + max(free, 0.0), cz - sn * hz))
                        if (d > best + 1e-6) { best = d; took = cand; supN = supT }
                    }
                }
                blocked++
            } else if (abs(took) > 1e-6) {
                bent++
                if (abs(took) > abs(prev) + 1e-6) pref[k] = if (took > 0) 1.0 else -1.0
            }
            defl[i] = took
            carry += took
            val psi = a0 + shape + carry
            val c = cos(psi); val sn = sin(psi)
            var drawn = supN / L + max(free, 0.0)
            if (free > 0.0) {
                // a lifted step stays clear of a ceiling or overhang: lowered toward the surface until it is
                for (it in 0 until 3) {
                    val dy = drawn - yDraw
                    val hz = sqrt(max(seg * seg - dy * dy, MIN_RUN * seg * seg))
                    val q = world(cx + c * hz, ARM_Y + drawn, cz - sn * hz)
                    if (s.dist(q) >= TIP_CLEAR_CM) break
                    drawn = supN / L + 0.5 * (drawn - supN / L) * (if (it == 2) 0.0 else 1.0)
                }
            }
            val dy = drawn - yDraw
            val hz = sqrt(max(seg * seg - dy * dy, MIN_RUN * seg * seg))
            cx += c * hz; cz -= sn * hz
            yDraw = drawn; sup = supN
            psiS[i] = psi - a0; yS[i] = drawn; supS[i] = supN; carryS[i] = carry
        }
        blockedNow[k] = blocked
        bentNow[k] = bent
    }

    /**
     * Rise (cm, along the disc's up) of the surface under the step from (cx, cz) in direction [psi]: probed from the
     * arm's own level where the step would land, then again where its run (shortened by that rise) lands; bodies lying
     * there are climbed over. More than [maxUp]: a wall.
     */
    private fun stepHeight(
        s: CrawlSurface, psi: Double, cx: Double, cz: Double, seg: Double, sup: Double, yDraw: Double, free: Double,
        L: Double, maxUp: Double, maxDown: Double,
    ): Double {
        val c = cos(psi); val sn = sin(psi)
        var w = world(cx + c * seg, 0.0, cz - sn * seg)
        // (the support keeps a hair of clearance from the surface, so a skin-thick arm never shows through it)
        var h = s.heightAt(w + uS * sup, uS, maxUp, maxDown + CLEAR_CM) + CLEAR_CM
        if (h > maxUp) return h
        val dy = (sup + h) / L + max(free, 0.0) - yDraw
        val hz = sqrt(max(seg * seg - dy * dy, MIN_RUN * seg * seg))
        w = world(cx + c * hz, 0.0, cz - sn * hz)
        val h2 = s.heightAt(w + uS * sup, uS, maxUp, maxDown + CLEAR_CM) + CLEAR_CM
        if (h2 > maxUp) return h2
        h = h2
        // the step's middle must be clear too (a step can cut across a ridge or the corner of a block)
        val mid = world(cx + 0.5 * c * hz, ARM_Y + 0.5 * (yDraw + (sup + h) / L + max(free, 0.0)), cz - 0.5 * sn * hz)
        if (s.dist(mid) < -MID_TOL_CM) return maxUp + 1.0
        for (n in 0 until nOthers) {
            val top = topOf(otherPos[n]!!, otherR[n], w) - sup
            if (top > h) { if (top <= 2 * maxUp) h = top else return maxUp + 1.0 }
        }
        return h
    }

    /** Clearance (cm) of [p] from the surface and from the bodies the arms drape over. */
    private fun clearOf(s: CrawlSurface, p: Vec3): Double {
        var d = s.dist(p)
        for (n in 0 until nOthers) d = min(d, p.distanceTo(otherPos[n]!!) - otherR[n])
        return d
    }

    /** Height (cm, along the disc's up from [w]) of the top of a body of radius [r] at [c] above the point [w]. */
    private fun topOf(c: Vec3, r: Double, w: Vec3): Double {
        val d = c - w
        val h = d dot uS
        val lat2 = d.lengthSq() - h * h
        if (lat2 >= r * r) return -1e9
        return h + sqrt(r * r - lat2)
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
        /** Pose entries: eight per arm (see the class doc). */
        const val COUNT = 40
        /** Model geometry (fraction of arm span): disc-arm junction radius and arm length (research 17 §2.1). */
        const val ROOT_R = 0.05
        const val ARM_LEN = 0.45
        /** Arm centreline height, disc radius and disc underside in the model (tools/blender/brittle_geom.py). */
        const val ARM_Y = 0.0085
        const val DISC_R = 0.056
        const val DISC_FOOT_Y = 0.004
        /** Share of each cycle the disc is moving (Astley 2012 disc duty 0.42–0.46). */
        const val DUTY = 0.45
        /** Forelimb sweep ≈ 45° and hindlimb ≈ 13° per stroke (Astley 2012): the shape's targets when no grip holds. */
        const val FORE_SWEEP = 0.78
        const val HIND_SWEEP = 0.22
        /** The rowing arms reach this far (rad) ahead of their rest direction at the end of the recovery. */
        const val PROTRACT = 0.45
        /** Disc travel per stance in arm lengths, and the fastest stroke cycle (Hz, a startled sprint). */
        const val STRIDE_ARM = 0.7
        const val MAX_HZ = 0.9
        /** Tip lift (rad) of a rowing arm in its recovery swing. */
        const val SWING_LIFT = 0.32
        /** The recovery swing reaches the arm's next hold by this cycle phase (eased from the end of the stance). */
        const val RECOVER_END = 0.92
        /** Lag of the right rowing arm (cycles; Astley 2012: contralateral lag within ±13 %). */
        const val ROW_LAG = 0.04
        const val REVERSE_SHARE = 0.25
        const val IDLE_HZ = 0.06
        /** Centre-arm reassignment once the travel is this far (rad) off its sector (36° + margin). */
        const val SWITCH_OFF = 0.78
        const val REGROW_CM_PER_DAY = 0.04
        /** Arms grip only once the gait is this far in (a creeping start drags them). */
        const val GRIP_MOVING = 0.3
        /** A hold is kept while the IK puts the tip within this of it (model units, ≈ 0.2 cm on a 15 cm star). */
        const val GRIP_TOL = 0.012
        const val IK_ITERS = 6
        const val IK_EPS = 1e-3
        const val IK_YAW_MAX = 1.8
        const val IK_CURL_MAX = 1.8
        /** A drawn-in arm folds in S-curves: yaw amplitude (rad) at full fold and folds along the arm. */
        const val FOLD_AMP = 1.5
        const val FOLD_WAVES = 1.6
        /** Day hide: side-to-side trawl of the tips out of the hide (rad, Hz). */
        const val SWEEP_AMP = 0.35
        const val SWEEP_HZ = 0.07
        /** Steps per arm (each 1/15 of the arm, ≈ 0.45 cm on a 15 cm star). */
        const val SAMPLES = 15
        /** Steepest rise (per step length, ≈ 54°) and drop over an edge per step an arm follows. */
        const val CLIMB = 1.4
        const val DROP = 0.98
        /** A step's run never shrinks below this share (squared) of its length, however steep its rise. */
        const val MIN_RUN = 0.09
        /** Heights are clamped to ± this (model units). */
        const val Y_MAX = 0.9
        /** Wall bends: tried per step (rad, each way, preferred side first), limit, relax rate (rad/s). */
        private val DEFL_TRY = doubleArrayOf(0.25, 0.5, 0.8, 1.2)
        private val CANDIDATES = 2 + 2 * DEFL_TRY.size
        /** Bends (rad, preferred side positive) tried when every [DEFL_TRY] bend is walled. */
        private val FALLBACK_DEFL = doubleArrayOf(0.0, 0.5, -0.5, 1.0, -1.0, 1.4, -1.4)
        const val DEFL_MAX = 1.4
        const val DEFL_RELAX = 0.6
        /** Bodies the arms drape over: radius as a share of their collision length; at most this many. */
        const val OTHER_R = 0.3
        const val MAX_OTHERS = 8
        /** Clearance (cm) a lifted step keeps from a ceiling. */
        const val TIP_CLEAR_CM = 0.15
        /** Clearance (cm) of a lying arm's support from the surface; a step whose middle sinks deeper than [MID_TOL_CM]
         *  into it (a ridge or a block's corner cut across) is a wall. */
        const val CLEAR_CM = 0.08
        /** Fastest tilt (rad/s) of the seated disc toward the rock under it. */
        const val UP_RATE = 0.8
        /** Largest turn (rad) of a drawn step from one contact to the next. */
        const val PSI_STEP = 0.5
        const val MID_TOL_CM = 0.0
    }
}
