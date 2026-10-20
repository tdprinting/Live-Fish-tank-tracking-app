package com.reeflive.app.render

import com.reeflive.core.sim.CareState
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Where the care props rest and work, world cm. Mirrors tools/blender/care_tools.py (BUCKET_POS, the hose) and
 * equipment.py (SKIMMER_CUP on the skimmer lid, HOB_SOCK on the HOB filter lid). Everything stays out of the rock,
 * the glass and the equipment: the props work above the rim, the bucket floats beside the right-hand end in front
 * of the supply shelf, and the paths over the tank pass under the AI Prime pucks and in front of their arms.
 */
object CareLayout {
    const val MODEL = "models/care_tools.glb"
    /** Base of the skimmer's collection cup, on its collar on the skimmer lid (back middle). */
    val CUP = floatArrayOf(63f, 51.2f, -5.2f)
    /** The filter sock's collar on the HOB filter lid (back right), and the carbon bag beside it inside the HOB. */
    val SOCK = floatArrayOf(108.5f, 46.7f, -3.7f)
    val CARBON = floatArrayOf(102.8f, 46.7f, -3.7f)
    /** Bucket base: floating beside the right-hand end, in front of the supply shelf. */
    val BUCKET = floatArrayOf(133f, 6f, 35f)
    const val BUCKET_FLOOR = 0.6f
    const val BUCKET_TOP = 20.9f
    /** The hose pops in and out about this point (where it crosses the right-hand rim). */
    val HOSE_ANCHOR = floatArrayOf(121f, 45f, 38f)
    /** Gravel-vac mouth in the water (for the suction bursts). */
    val VAC = floatArrayOf(114f, 18f, 38f)
    /** Dosing bottle base while it pours: tipped to the left, its mouth over the HOB return at x 74. */
    val DOSE = floatArrayOf(89f, 47.5f, 9f)
    /** RO/DI jug base while it pours: back-left, tipped toward the viewer. */
    val JUG = floatArrayOf(14f, 47f, 6f)
    val WATER_Y: Float get() = Camera.WATER_Y
    const val CUP_H = 8f
    const val BOTTLE_H = 15.4f
    const val JUG_H = 24f

    private val spots by lazy { listOf(CUP, SOCK, CARBON, BUCKET, HOSE_ANCHOR, VAC, DOSE, JUG) }
    private val stock by lazy { spots.map { it.copyOf() } }

    /** Moves every prop spot with the glass, rim or equipment it works at for the tank on screen (core TankShape). */
    fun fitTo(s: com.reeflive.core.sim.TankShape) {
        for (i in spots.indices) {
            val a = spots[i]; val o = stock[i]
            a[0] = s.mapX(o[0].toDouble()).toFloat(); a[1] = s.mapY(o[1].toDouble()).toFloat(); a[2] = s.mapZ(o[2].toDouble()).toFloat()
        }
    }
}

/** One care animation. [seconds] long; the sim command goes out at [impact] (the moment the stuff reaches the tank). */
enum class CareAct(val seconds: Float, val impact: Float) {
    /** Skimmer cup lifted off, tipped out into the bucket, seated again. */
    EMPTY_CUP(7.8f, 4.5f),
    /** Dirty filter sock pulled out into the bucket, a clean one lowered in. */
    SWAP_SOCK(7.6f, 6.6f),
    /** Spent carbon bag out of the HOB into the bucket, a fresh bag in. */
    SWAP_CARBON(7.6f, 6.6f),
    /** Bottle uncapped, tipped over the HOB return, capped again. */
    DOSE(5.0f, 2.8f),
    /** RO/DI jug poured in at the back left. */
    TOP_OFF(5.0f, 3.2f),
    /** Siphon old water into the bucket, then pour a bucket of new salt water in. */
    WATER_CHANGE(11.4f, 8.6f),
    /** Siphon a little salt water out, then pour RO/DI water in from the jug. */
    RO_SWAP(9.6f, 8.0f),
    /** No props (heater, pumps, food: the screen draws its own picture); keeps the queue's rhythm. */
    PAUSE(1.8f, 0.25f),
    /** A syringe of aiptasia killer (Aiptasia-X, Joe's Juice, kalk paste) to a polyp's mouth ([CareJob.at]) and a squeeze. */
    INJECT(5.2f, 3.1f),
    /** An aiptasia laser held off a polyp, a second of beam. */
    LASER(4.6f, 2.5f),
}

/**
 * One queued animation. [tint] (sRGB 0xRRGGBB) colours the bottle's cap and label or the new water; [amount] is the
 * share of water swapped (sets how high the bucket fills). [onImpact] and [onDone] run on the main thread.
 */
class CareJob(
    val act: CareAct,
    val tint: Int = 0xFFFFFF,
    val amount: Float = 0.25f,
    /** [CareAct.INJECT] / [CareAct.LASER]: the polyp's foot x, y, z and rock normal x, y, z (world cm). */
    val at: FloatArray? = null,
    val onImpact: () -> Unit = {},
    val onDone: () -> Unit = {},
)

/**
 * The UI's handle on the care animations (any thread; the GL thread drains it). Jobs play one after another, so a
 * "Fix everything" list plays as a little show. [pending] counts queued and playing jobs for the UI.
 */
class CareShowControl {
    private val inbox = ConcurrentLinkedQueue<CareJob>()
    private val count = AtomicInteger(0)
    /** Jobs queued or playing. */
    val pending: Int get() = count.get()

    fun play(job: CareJob) { count.incrementAndGet(); inbox.add(job) }

    internal fun finished() { count.decrementAndGet() }

    internal fun take(): CareJob? = inbox.poll()
}

/**
 * GL-thread side: the props at rest (the skimmer cup with its skimmate level, the filter sock's collar when a sock is
 * fitted) and the queued care animations, posed by hand-tuned keyframes and drawn from care_tools.glb with the generic
 * program. Allocation-free per frame: poses go into fixed arrays, parts are looked up by node once per model.
 */
internal class CareScene {
    private enum class Node(val glb: String) {
        CUP("Cup"), CUP_LID("CupLid"), CUP_MUCK("CupMuck"), SOCK_RING("SockRing"), SOCK_BAG("SockBag"), CARBON("CarbonBag"),
        BOTTLE("Bottle"), BOTTLE_CAP("BottleCap"), BOTTLE_BAND("BottleBand"), BUCKET("Bucket"), BUCKET_HANDLE("BucketHandle"),
        BUCKET_WATER("BucketWater"), JUG("Jug"), JUG_CAP("JugCap"), JUG_WATER("JugWater"), HOSE("Hose"), STREAM("Stream"),
    }

    private var job: CareJob? = null
    /** The aiptasia tools ([CareAct.INJECT], [CareAct.LASER]), drawn from their own model by [drawPest]. */
    private val pest = PestScene()
    private val mouthPt = FloatArray(3)
    private var t = 0f
    private var impacted = false
    private var bound: GpuModel? = null
    private val parts = arrayOfNulls<Array<DrawPart>>(Node.entries.size)

    // draw list
    private var n = 0
    private val node = IntArray(MAX)
    private val mats = FloatArray(MAX * 16)
    private val tint = FloatArray(MAX * 4)
    private val tinted = BooleanArray(MAX)
    private val m = FloatArray(16)
    private val tmp = FloatArray(16)
    private val pt = FloatArray(3)

    /** Called with in-water bursts (dye plumes, splashes, suction). */
    var onBurst: ((TankBurst) -> Unit)? = null

    val busy: Boolean get() = job != null

    /**
     * Advances the playing job (or starts the next) by [dt] and poses everything for this frame. [post] runs a
     * callback on the main thread.
     */
    fun update(dt: Float, control: CareShowControl?, care: CareState?, post: (() -> Unit) -> Unit) {
        if (job == null && control != null) {
            job = control.take()
            t = 0f; impacted = false
        }
        val j = job
        if (j != null) {
            val prev = t
            t += dt
            cues(j, prev, t)
            if (!impacted && t >= j.act.impact) { impacted = true; post(j.onImpact) }
            if (t >= j.act.seconds) {
                job = null
                post(j.onDone)
                control?.finished()
            }
        }
        n = 0
        pest.clear()
        val c = care ?: REST
        val act = job?.act
        if (act != CareAct.EMPTY_CUP) restCup(c.skimmerCup)
        if (c.sockOn && act != CareAct.SWAP_SOCK) restSock(c.sockDirt)
        job?.let { pose(it, c) }
    }

    // ------------------------------------------------------------------ rest poses

    private fun restCup(fill: Float) {
        val p = CareLayout.CUP
        cup(p[0], p[1], p[2], 0f, 0f, fill, 0f, 1f)
    }

    private fun restSock(dirt: Float) {
        val p = CareLayout.SOCK
        sock(p[0], p[1], p[2], 1f, dirt, bag = false)
    }

    // ------------------------------------------------------------------ the shows

    private fun pose(j: CareJob, c: CareState) {
        when (j.act) {
            CareAct.EMPTY_CUP -> emptyCup(c.skimmerCup)
            CareAct.SWAP_SOCK -> swap(CareLayout.SOCK, true, if (c.sockOn) c.sockDirt else -1f)
            CareAct.SWAP_CARBON -> swap(CareLayout.CARBON, false, if (c.carbonOn) 1f - c.carbonLeft else -1f)
            CareAct.DOSE -> dose(j.tint)
            CareAct.TOP_OFF -> jug(t, 0f)
            CareAct.WATER_CHANGE -> waterChange(j.amount, j.tint)
            CareAct.RO_SWAP -> roSwap(j.amount)
            CareAct.PAUSE -> Unit
            CareAct.INJECT -> pest.inject(j, t)
            CareAct.LASER -> pest.laser(j, t)
        }
    }

    /** Lift, carry over the bucket, lid off, tip out, rinse, back on the skimmer. */
    private fun emptyCup(fill0: Float) {
        val c = CareLayout.CUP
        val b = CareLayout.BUCKET
        val lift = seg(t, 0f, 0.9f) - seg(t, 6.7f, 7.6f)
        val go = seg(t, 0.9f, 2.4f) - seg(t, 5.1f, 6.6f)
        val tip = seg(t, 2.6f, 3.1f) - seg(t, 4.6f, 5.0f)
        // carried along a curve: up off the skimmer, forward under the lamps, down to the bucket's side
        val x0 = c[0]; val y0 = c[1] + 7f; val z0 = c[2]
        val x2 = b[0] - 6.6f; val y2 = 38f; val z2 = b[2]
        val x = qb(x0, 76f, x2, go); val y = qb(y0, 62f, y2, go) - 7f * (1f - lift) * (1f - go); val z = qb(z0, 26f, z2, go)
        val roll = -125f * DEG * tip
        val yaw = 0.7f * lift * (1f - go)
        val fill = if (t < 3.0f) fill0 else fill0 * (1f - seg(t, 3.0f, 4.4f))
        val lidOff = seg(t, 2.2f, 2.6f) - seg(t, 4.9f, 5.2f)
        cup(x, y, z, yaw, roll, fill, lidOff, 1f)
        val pop = popIn(t, 1.1f) * popOut(t, 6.9f)
        val level = 2.2f * seg(t, 3.0f, 4.4f)
        bucket(b[0], b[1], b[2], pop, 0f, level, MUCK)
        if (tip > 0.8f && fill > 0.01f) {
            mouth(x, y, z, 0f, roll, CareLayout.CUP_H)
            stream(pt[0], pt[1], b[1] + CareLayout.BUCKET_FLOOR + level, MUCK, 0.9f)
        }
    }

    /**
     * The sock (or carbon bag) at [at]: the old one up and over into the bucket (skipped when none is fitted,
     * [dirt0] < 0), then a fresh one lowered into the HOB.
     */
    private fun swap(at: FloatArray, isSock: Boolean, dirt0: Float) {
        val b = CareLayout.BUCKET
        val pop = popIn(t, 0.9f) * popOut(t, 4.2f)
        bucket(b[0], b[1], b[2], pop, 0f, 2f * seg(t, 2.6f, 3.4f), MUCK)
        if (dirt0 >= 0f && t < 3.6f) {
            val up = seg(t, 0f, 1.2f)
            val go = seg(t, 1.2f, 2.6f)
            val down = seg(t, 2.6f, 3.4f)
            val x = qb(at[0], 118f, b[0], go)
            val y = qb(at[1] + 14f * up, 66f, b[1] + 38f, go) - 10f * down
            val z = qb(at[2], 20f, b[2], go)
            val s = 1f - seg(t, 3.0f, 3.6f)
            if (isSock) sock(x, y, z, s, dirt0, bag = true) else carbon(x, y, z, s, dirt0)
        }
        if (t >= 3.4f) {
            val appear = popIn(t, 3.4f)
            val go = seg(t, 4.0f, 5.4f)
            val down = seg(t, 5.6f, 6.6f)
            val x = at[0]
            val y = at[1] + 15f - 15f * down
            val z = at[2] + 16f * (1f - go)
            if (isSock) sock(x, y, z, appear, 0f, bag = true) else carbon(x, y, z, appear, 0f)
        }
    }

    /** Pop in over the return, cap off, tip, pour a coloured stream, tip back, cap on, pop out. */
    private fun dose(rgb: Int) {
        val d = CareLayout.DOSE
        val s = popIn(t, 0f) * popOut(t, 4.4f)
        val tip = seg(t, 1.3f, 1.9f) - seg(t, 3.0f, 3.4f)
        val roll = 100f * DEG * tip
        val y = d[1] - 3f * (1f - seg(t, 0f, 0.6f))
        val r = cr(rgb); val g = cg(rgb); val bl = cb(rgb)
        add(Node.BOTTLE, d[0], y, d[2], 0f, 0f, roll, s, s, s)
        add(Node.BOTTLE_BAND, d[0], y, d[2], 0f, 0f, roll, s, s, s, r, g, bl, 1f)
        // the cap: unscrewed (two turns, up 2.5 cm), parked beside the bottle while it pours, then screwed back on
        val off = seg(t, 0.5f, 1.1f) - seg(t, 3.6f, 4.2f)
        val park = seg(t, 1.1f, 1.3f) - seg(t, 3.4f, 3.6f)
        val onY = y + CAP_SEAT + 2.5f * off
        add(Node.BOTTLE_CAP, d[0] + 7f * park, onY + (y + 4f - onY) * park, d[2], 4f * PI.toFloat() * off, 0f, 0f, s, s, s, r, g, bl, 1f)
        if (tip > 0.85f && t < 3.0f) {
            mouth(d[0], y, d[2], 0f, roll, CareLayout.BOTTLE_H)
            stream(pt[0], pt[1], CareLayout.WATER_Y, rgb, 0.8f)
        }
    }

    /** The RO/DI jug: pops in at the back left, tips toward the viewer and pours, pops out. [t0] shifts its clock. */
    private fun jug(time: Float, t0: Float) {
        val u = time - t0
        if (u < 0f) return
        val j = CareLayout.JUG
        val s = popIn(u, 0f) * popOut(u, 4.4f)
        val tip = seg(u, 0.8f, 1.6f) - seg(u, 3.6f, 4.2f)
        val pitch = 105f * DEG * tip
        val level = 15f * (1f - seg(u, 1.6f, 3.6f)) + 0.6f
        add(Node.JUG, j[0], j[1], j[2], 0f, pitch, 0f, s, s, s)
        // the cap pops off before the pour and back on after it
        val cs = s * maxOf(1f - seg(u, 0.4f, 0.7f), seg(u, 3.9f, 4.2f))
        if (cs > 0.01f) add(Node.JUG_CAP, j[0], j[1], j[2], 0f, pitch, 0f, cs, cs, cs, offY = (CareLayout.JUG_H - 1.6f) * s / cs)
        add(Node.JUG_WATER, j[0], j[1], j[2], 0f, pitch, 0f, s, level * s, s, offY = 0.7f)
        if (tip > 0.85f && u < 3.6f) {
            mouthX(j[0], j[1], j[2], pitch, CareLayout.JUG_H)
            stream(pt[0], pt[1], CareLayout.WATER_Y, RO, 0.8f)
        }
    }

    /** Siphon old water out into the bucket, swap the bucket for new salt water, lift it over the tank and pour. */
    private fun waterChange(amount: Float, rgb: Int) {
        val b = CareLayout.BUCKET
        val full = 6f + 40f * amount.coerceIn(0.02f, 0.3f)
        siphon(full)
        if (t >= 5.4f && t < 11.4f) {
            val s = popIn(t, 5.9f) * popOut(t, 10.8f)
            // straight up clear of the glass first, then over the tank (and the same way back)
            val up = seg(t, 6.4f, 7.0f) - seg(t, 10.2f, 10.7f)
            val over = seg(t, 7.0f, 7.8f) - seg(t, 9.7f, 10.2f)
            val tip = seg(t, 7.7f, 8.1f) - seg(t, 9.4f, 9.8f)
            val x = b[0] + (118f - b[0]) * over
            val y = b[1] + (50f - b[1]) * up
            val z = b[2] + (30f - b[2]) * over
            val roll = 80f * DEG * tip
            val level = full * (1f - seg(t, 8.0f, 9.6f))
            bucket(x, y, z, s, roll, level, rgb)
            if (tip > 0.85f && level > 0.3f) {
                lip(x, y, z, roll)
                stream(pt[0], pt[1], CareLayout.WATER_Y, rgb, 1.6f)
            }
        }
    }

    /** A small siphon into the bucket, then the jug's RO/DI water in. */
    private fun roSwap(amount: Float) {
        siphon(4f + 60f * amount.coerceIn(0.01f, 0.1f))
        jug(t, 4.6f)
    }

    /** The bucket pops in empty, the hose drops in, old water fills it, the hose and bucket go again. */
    private fun siphon(full: Float) {
        val b = CareLayout.BUCKET
        if (t >= 6.0f) return
        val s = popIn(t, 0f) * popOut(t, 5.4f)
        bucket(b[0], b[1], b[2], s, 0f, full * seg(t, 1.3f, 4.8f), OLD)
        val hs = popIn(t, 0.5f) * popOut(t, 4.9f)
        if (hs > 0.01f) {
            val a = CareLayout.HOSE_ANCHOR
            add(Node.HOSE, a[0], a[1], a[2], 0f, 0f, 0f, hs, hs, hs, pre = a)
        }
    }

    /** Sounds of the water: suction at the vac, splashes and dye where things pour in. */
    private fun cues(j: CareJob, a: Float, b: Float) {
        fun at(time: Float) = a < time && b >= time
        val emit = onBurst ?: return
        when (j.act) {
            CareAct.DOSE -> if (at(2.0f)) {
                emit(TankBurst(BurstKind.DYE, 74f, CareLayout.WATER_Y - 0.6f, 9f, cr(j.tint), cg(j.tint), cb(j.tint), 1f))
                emit(TankBurst(BurstKind.SPLASH, 74f, CareLayout.WATER_Y - 0.4f, 9f, amount = 0.4f))
            }
            CareAct.TOP_OFF -> if (at(1.8f) || at(2.8f)) emit(TankBurst(BurstKind.FRESH, 14f, CareLayout.WATER_Y - 1f, 28f, amount = 0.8f))
            CareAct.WATER_CHANGE, CareAct.RO_SWAP -> {
                val v = CareLayout.VAC
                if (at(1.4f) || at(2.6f) || at(3.8f)) emit(TankBurst(BurstKind.WATER_CHANGE, v[0], v[1], v[2], amount = 0.7f))
                if (j.act == CareAct.WATER_CHANGE && (at(8.2f) || at(9.0f))) {
                    emit(TankBurst(BurstKind.SPLASH, 96f, CareLayout.WATER_Y - 0.4f, 30f, amount = 1.4f))
                    emit(TankBurst(BurstKind.BUBBLES, 96f, CareLayout.WATER_Y - 2f, 30f, amount = 0.8f))
                }
                if (j.act == CareAct.RO_SWAP && (at(6.4f) || at(7.4f))) emit(TankBurst(BurstKind.FRESH, 14f, CareLayout.WATER_Y - 1f, 28f, amount = 0.8f))
            }
            CareAct.SWAP_SOCK -> if (at(6.8f)) emit(TankBurst(BurstKind.SUCTION, 108f, 30f, 6f, amount = 0.8f))
            CareAct.SWAP_CARBON -> if (at(6.8f)) emit(TankBurst(BurstKind.CLARITY, 100f, 30f, 10f, amount = 0.8f))
            CareAct.EMPTY_CUP -> if (at(7.4f)) emit(TankBurst(BurstKind.MICROBUBBLES, 73f, CareLayout.WATER_Y - 1f, 3f, amount = 0.8f))
            CareAct.INJECT -> {
                pest.mouth(j, mouthPt)
                // the tool breaks the surface, then the dose puffs out over the polyp's mouth
                if (at(0.9f)) emit(TankBurst(BurstKind.SPLASH, mouthPt[0], CareLayout.WATER_Y - 0.4f, mouthPt[2], amount = 0.3f))
                if (at(3.0f)) emit(TankBurst(BurstKind.ZAP, mouthPt[0], mouthPt[1], mouthPt[2], cr(j.tint), cg(j.tint), cb(j.tint), 0.6f))
            }
            CareAct.LASER -> {
                pest.mouth(j, mouthPt)
                if (at(0.8f)) emit(TankBurst(BurstKind.SPLASH, mouthPt[0], CareLayout.WATER_Y - 0.4f, mouthPt[2], amount = 0.3f))
                if (at(2.4f) || at(2.9f)) emit(TankBurst(BurstKind.SPARKLE, mouthPt[0], mouthPt[1], mouthPt[2], 0.6f, 1f, 0.55f, 0.5f))
                if (at(3.0f)) emit(TankBurst(BurstKind.BUBBLES, mouthPt[0], mouthPt[1], mouthPt[2], amount = 0.25f))
            }
            else -> Unit
        }
    }

    // ------------------------------------------------------------------ prop helpers

    private fun cup(x: Float, y: Float, z: Float, yaw: Float, roll: Float, fill: Float, lidOff: Float, s: Float) {
        add(Node.CUP, x, y, z, yaw, 0f, roll, s, s, s)
        val h = (fill.coerceIn(0f, 1f) * 6.6f).coerceAtLeast(0.02f)
        if (fill > 0.005f) add(Node.CUP_MUCK, x, y, z, yaw, 0f, roll, s, h * s, s, offY = 0.15f)
        // the lid lifts straight up off the cup and waits there while it is tipped out
        if (lidOff <= 0f) add(Node.CUP_LID, x, y, z, yaw, 0f, roll, s, s, s)
        else add(Node.CUP_LID, x, y + 6f * lidOff, z, yaw, 0f, roll * (1f - lidOff), s, s, s)
    }

    private fun sock(x: Float, y: Float, z: Float, s: Float, dirt: Float, bag: Boolean) {
        if (s <= 0.01f) return
        add(Node.SOCK_RING, x, y, z, 0f, 0f, 0f, s, s, s)
        // felt goes from clean white to the brown of a 3-day-old sock
        val d = dirt.coerceIn(0f, 1f)
        val r = 0.95f + (0.36f - 0.95f) * d; val g = 0.94f + (0.27f - 0.94f) * d; val b = 0.9f + (0.15f - 0.9f) * d
        if (bag || y > CareLayout.SOCK[1] + 0.5f) add(Node.SOCK_BAG, x, y, z, 0f, 0f, 0f, s, s, s, r, g, b, 1f)
    }

    private fun carbon(x: Float, y: Float, z: Float, s: Float, spent: Float) {
        if (s <= 0.01f) return
        // spent carbon is dusty grey, fresh carbon jet black
        val k = 0.1f + 0.25f * spent.coerceIn(0f, 1f)
        add(Node.CARBON, x, y, z, 0f, 0f, 0f, s, s, s, k, k, k * 1.05f, 1f)
    }

    private fun bucket(x: Float, y: Float, z: Float, s: Float, roll: Float, level: Float, rgb: Int) {
        if (s <= 0.01f) return
        add(Node.BUCKET, x, y, z, 0f, 0f, roll, s, s, s)
        add(Node.BUCKET_HANDLE, x, y, z, 0f, 0f, roll, s, s, s)
        if (level > 0.05f) add(Node.BUCKET_WATER, x, y, z, 0f, 0f, roll, s, level * s, s, cr(rgb), cg(rgb), cb(rgb), 0.7f, offY = CareLayout.BUCKET_FLOOR)
    }

    /** A pour stream from ([x], [y]) straight down to [toY], at [z] of the last [mouth] / [lip] point. */
    private fun stream(x: Float, y: Float, toY: Float, rgb: Int, width: Float) {
        val len = y - toY
        if (len <= 0.2f) return
        add(Node.STREAM, x, y, pt[2], 0f, 0f, 0f, width, len, width, cr(rgb), cg(rgb), cb(rgb), 0.75f)
    }

    /** The mouth of a prop of height [h] posed at (x, y, z) with [yaw] and [roll] (about z) → [pt]. */
    private fun mouth(x: Float, y: Float, z: Float, yaw: Float, roll: Float, h: Float) {
        pt[0] = x - h * sin(roll); pt[1] = y + h * cos(roll); pt[2] = z
    }

    /** The mouth of a prop of height [h] pitched toward the viewer by [pitch] → [pt]. */
    private fun mouthX(x: Float, y: Float, z: Float, pitch: Float, h: Float) {
        pt[0] = x; pt[1] = y + h * cos(pitch); pt[2] = z + h * sin(pitch)
    }

    /** The lowest point of the bucket's rim when it is tipped by [roll] → [pt]. */
    private fun lip(x: Float, y: Float, z: Float, roll: Float) {
        val h = CareLayout.BUCKET_TOP; val r = 10f
        pt[0] = x - h * sin(roll) - r * cos(roll); pt[1] = y + h * cos(roll) - r * sin(roll); pt[2] = z
    }

    /**
     * Queues one part: model = T(x,y,z) · Ry(yaw) · Rz(roll) · Rx(pitch) · S(sx,sy,sz) · T(0, offY, 0) · T(-pre).
     * [pre] moves a world-space mesh (the hose) to the pivot first.
     */
    private fun add(
        nd: Node, x: Float, y: Float, z: Float, yaw: Float, pitch: Float, roll: Float, sx: Float, sy: Float, sz: Float,
        r: Float = -1f, g: Float = 0f, b: Float = 0f, a: Float = 1f, offY: Float = 0f, pre: FloatArray? = null,
    ) {
        if (n == MAX) return
        val cy = cos(yaw); val sy0 = sin(yaw)
        val cz = cos(roll); val sz0 = sin(roll)
        val cx = cos(pitch); val sx0 = sin(pitch)
        // R = Ry · Rz · Rx (column-major 3×3 in r[0..8])
        // Rz·Rx
        val a00 = cz; val a01 = -sz0 * cx; val a02 = sz0 * sx0
        val a10 = sz0; val a11 = cz * cx; val a12 = -cz * sx0
        val a20 = 0f; val a21 = sx0; val a22 = cx
        // Ry · (Rz·Rx)
        val r00 = cy * a00 + sy0 * a20; val r01 = cy * a01 + sy0 * a21; val r02 = cy * a02 + sy0 * a22
        val r10 = a10; val r11 = a11; val r12 = a12
        val r20 = -sy0 * a00 + cy * a20; val r21 = -sy0 * a01 + cy * a21; val r22 = -sy0 * a02 + cy * a22
        val o = n * 16
        mats[o] = r00 * sx; mats[o + 1] = r10 * sx; mats[o + 2] = r20 * sx; mats[o + 3] = 0f
        mats[o + 4] = r01 * sy; mats[o + 5] = r11 * sy; mats[o + 6] = r21 * sy; mats[o + 7] = 0f
        mats[o + 8] = r02 * sz; mats[o + 9] = r12 * sz; mats[o + 10] = r22 * sz; mats[o + 11] = 0f
        // translation: T + R·S·(0, offY, 0) − R·S·pre
        var px = 0f; var py = offY; var pz = 0f
        if (pre != null) { px -= pre[0]; py -= pre[1]; pz -= pre[2] }
        mats[o + 12] = x + mats[o] * px + mats[o + 4] * py + mats[o + 8] * pz
        mats[o + 13] = y + mats[o + 1] * px + mats[o + 5] * py + mats[o + 9] * pz
        mats[o + 14] = z + mats[o + 2] * px + mats[o + 6] * py + mats[o + 10] * pz
        mats[o + 15] = 1f
        node[n] = nd.ordinal
        tinted[n] = r >= 0f
        if (r >= 0f) { tint[n * 4] = r; tint[n * 4 + 1] = g; tint[n * 4 + 2] = b; tint[n * 4 + 3] = a }
        n++
    }

    // ------------------------------------------------------------------ draw

    /** The aiptasia tools posed this frame, from models/pest_tools.glb ([PestScene.MODEL]). */
    fun drawPest(model: GpuModel, p: ShaderProgram, white: Texture, blended: Boolean): Int = pest.draw(model, p, white, blended)

    private fun bind(model: GpuModel) {
        if (bound === model) return
        bound = model
        for (nd in Node.entries) parts[nd.ordinal] = model.parts.filter { it.node.name == nd.glb }.toTypedArray()
    }

    /** Opaque ([blended] false) or see-through parts of everything posed this frame. Returns draw calls. */
    fun draw(model: GpuModel, p: ShaderProgram, white: Texture, blended: Boolean): Int {
        if (n == 0) return 0
        bind(model)
        p.use()
        p.set1f("uLedDies", 0f)
        var calls = 0
        for (k in 0 until n) {
            val list = parts[node[k]] ?: continue
            for (part in list) {
                if (part.material.src.blended != blended) continue
                System.arraycopy(mats, k * 16, m, 0, 16)
                p.setMat4("uModel", if (part.identityWorld) m else M.mul(m, part.node.world, tmp))
                part.material.apply(p, white)
                if (tinted[k]) {
                    val o = k * 4
                    p.set4f("uBaseColor", lin(tint[o]), lin(tint[o + 1]), lin(tint[o + 2]), tint[o + 3])
                }
                part.mesh.draw()
                calls++
            }
        }
        return calls
    }

    companion object {
        const val MAX = 24
        /** Where the cap's base sits on the bottle's neck. */
        private const val CAP_SEAT = 14.0f
        private const val DEG = (PI / 180.0).toFloat()
        private val REST = CareState()
        /** Skimmate brown, old tank water, RO/DI water (sRGB). */
        const val MUCK = 0x4A3417
        const val OLD = 0xB8C79A
        const val RO = 0xDDF0FF

        private fun cr(rgb: Int) = ((rgb shr 16) and 0xFF) / 255f
        private fun cg(rgb: Int) = ((rgb shr 8) and 0xFF) / 255f
        private fun cb(rgb: Int) = (rgb and 0xFF) / 255f
        /** sRGB → linear, as the material factors are (Material.kt). */
        private fun lin(c: Float): Float = if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)

        /** Smoothstep of [t] over [a]..[b]. */
        fun seg(t: Float, a: Float, b: Float): Float {
            val u = ((t - a) / (b - a)).coerceIn(0f, 1f)
            return u * u * (3 - 2 * u)
        }

        /** Scale for a prop that pops in at [at] (0.45 s with a little overshoot). */
        fun popIn(t: Float, at: Float): Float {
            val u = ((t - at) / 0.45f).coerceIn(0f, 1f)
            if (u >= 1f) return 1f
            val c1 = 1.70158f; val c3 = c1 + 1
            val v = u - 1
            return (1 + c3 * v * v * v + c1 * v * v).coerceAtLeast(0f)
        }

        /** Scale for a prop that shrinks away from [at] over 0.4 s. */
        fun popOut(t: Float, at: Float): Float = 1f - seg(t, at, at + 0.4f)

        /** Quadratic Bézier through a, b (control), c. */
        fun qb(a: Float, b: Float, c: Float, u: Float): Float = (1 - u) * (1 - u) * a + 2 * (1 - u) * u * b + u * u * c
    }
}
