package com.reeflive.app.render

import com.reeflive.core.sim.SimSnapshot
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * In-tank feedback for user actions, drawn by the renderer with the shared particle sprites (no extra draw calls):
 * a dropper's coloured plume spreading in the flow, a water change siphoning muck out and swirling fresh water in, a
 * heater's warm shimmer, salt grains dissolving, a pump's jet kicking in, a bag of bubbles, a silt cloud when rock
 * moves, sparkles when the glass is clean. Each is a short, pooled burst (CAPACITY points at most).
 */
enum class BurstKind {
    /** Food hitting the surface: a few bubbles and ripple specks. */
    SPLASH,
    /** Something heavier going in (a frozen cube, a wafer): a plop of bubbles. */
    PLOP,
    /** Dosing: a coloured plume from the return that spreads with the flow. */
    DYE,
    /** Water change: muck drawn into the siphon at the point, then fresh water swirling in from the return. */
    WATER_CHANGE,
    /** Warmer / heater on: warm shimmer rising from the point. */
    HEAT,
    /** Cooler / heater off: cool shimmer sinking from the point. */
    COOL,
    /** Heater breaks: a few sparks and a burp of bubbles. */
    SPARKS,
    /** Saltier: grains falling and dissolving. */
    SALT,
    /** Less salty: fresh water swirling in as clear streaks. */
    FRESH,
    /** A pump starts: a fast jet of streaks along its axis (snaps to the nearest pump). */
    PUMP_ON,
    /** A pump stops: the jet stalls and the specks it carried drift down. */
    PUMP_OFF,
    /** A bag or net opens: a gush of bubbles and bag water. */
    BUBBLES,
    /** Rock moved / live rock: a cloud of silt and settling specks. */
    SILT,
    /** Pest zapped: a white paste puff and a flash. */
    ZAP,
    /** Generic: tinted sparkles drifting in the flow. */
    SPARKLE,
    /** Knock on the glass: specks pushed away from the pane. */
    TAP,
    /** Live pods released: tiny hoppers sinking toward the rock. */
    PODS,
    /** Phyto / rotifers / medicine cloud: a tinted haze dispersing in the flow. */
    HAZE,
    /** Skimmer: fine micro-bubbles from the return. */
    MICROBUBBLES,
    /** Filter sock: suspended muck drawn toward the overflow. */
    SUCTION,
    /** Carbon, clean water: a clear sparkle through the water column. */
    CLARITY,
    /** UV steriliser: a violet glow at the return. */
    UV_GLOW,
    /** Lights on: warm motes glinting under the lamp. */
    LIGHT_MOTES,
    /** Spare shells dropped in: shells sinking with a puff of sand. */
    SHELLS,
    /** Torn nori bits drifting off the clip (the sim now tears real pieces off the cloth; kept for hosts that emit it). */
    NORI_BITS,
    /** A fingertip tap on the glass: a small ring of ripples spreading over the pane from the point. */
    GLASS_RIPPLE,
    /** A fingertip tap on the sand: a little puff of grains that lifts and settles. */
    SAND_PUFF,
    /** A tap (or a held fingertip) on the water's top: rings spreading over the surface. */
    SURFACE_RIPPLE,
    /** A flick in the water: faint streaks along the puff ([TankBurst.r], g, b carry its direction × speed / 45 cm/s). */
    SWISH,
}

/** One burst request: what, where (tank cm), an sRGB tint (0..1) and a size factor. */
class TankBurst(
    val kind: BurstKind,
    val x: Float,
    val y: Float,
    val z: Float,
    val r: Float = 1f,
    val g: Float = 1f,
    val b: Float = 1f,
    val amount: Float = 1f,
)

/** Thread-safe mailbox from the UI to the renderer: [emit] from any thread, the GL thread drains it each frame. */
class TankBursts {
    private val queue = ConcurrentLinkedQueue<TankBurst>()

    fun emit(b: TankBurst) { if (queue.size < MAX_PENDING) queue.add(b) }

    internal fun drain(into: (TankBurst) -> Unit) {
        while (true) into(queue.poll() ?: return)
    }

    private companion object { const val MAX_PENDING = 32 }
}

/**
 * The GL-thread side: a fixed pool of burst particles stepped on the CPU (flow, buoyancy, sinking, swirl, suction),
 * plus the always-on pump jets read from [SimSnapshot.pumps] (a faint stream of streaks from every running pump, longer
 * and faster with the flow slider, gone when the pump is off). Pure Kotlin.
 */
class BurstField(seed: Int = 23) {
    private val rnd = Random(seed)
    private val px = FloatArray(CAP); private val py = FloatArray(CAP); private val pz = FloatArray(CAP)
    private val vx = FloatArray(CAP); private val vy = FloatArray(CAP); private val vz = FloatArray(CAP)
    private val cx = FloatArray(CAP); private val cy = FloatArray(CAP); private val cz = FloatArray(CAP)
    private val age = FloatArray(CAP); private val life = FloatArray(CAP)
    private val s0 = FloatArray(CAP); private val s1 = FloatArray(CAP)
    private val cr = FloatArray(CAP); private val cg = FloatArray(CAP); private val cb = FloatArray(CAP); private val ca = FloatArray(CAP)
    private val shape = FloatArray(CAP); private val seedA = FloatArray(CAP)
    private val flags = IntArray(CAP)
    private var count = 0
    private val jetAcc = FloatArray(8)
    private val v = FloatArray(3)

    val active: Int get() = count

    fun spawn(b: TankBurst, snap: SimSnapshot?) {
        val tint = floatArrayOf(lin(b.r), lin(b.g), lin(b.b))
        val k = b.amount.coerceIn(0.2f, 3f)
        fun n(base: Int) = (base * k).toInt().coerceAtLeast(1)
        val x = b.x; val y = b.y.coerceAtMost(Camera.WATER_Y - 0.3f); val z = b.z
        when (b.kind) {
            BurstKind.SPLASH -> {
                repeat(n(12)) { add(x + g(1.5f), Camera.WATER_Y - 0.4f, z + g(1.5f), g(2f), -2f - rnd.nextFloat() * 4f, g(2f), 1.2f + rnd.nextFloat(), 0.22f, 0.16f, WHITE, 0.55f, Shape.BUBBLE, BUOY or DRAG) }
                repeat(n(10)) { val a = rnd.nextFloat() * TAU; add(x, Camera.WATER_Y - 0.2f, z, cos(a) * 9f, 0f, sin(a) * 9f, 0.7f, 0.5f, 0.2f, WHITE, 0.25f, Shape.STREAK, DRAG or ORIENT) }
            }
            BurstKind.PLOP -> {
                repeat(n(16)) { add(x + g(0.8f), Camera.WATER_Y - 1.5f - rnd.nextFloat() * 3f, z + g(0.8f), g(1f), 2f, g(1f), 1.6f + rnd.nextFloat(), 0.25f, 0.2f, WHITE, 0.6f, Shape.BUBBLE, BUOY, delay = rnd.nextFloat() * 0.4f) }
                add(x, Camera.WATER_Y - 1.5f, z, 0f, -1f, 0f, 2f, 1.5f, 5f, WHITE, 0.08f, Shape.HAZE, FLOW)
            }
            BurstKind.DYE -> {
                repeat(n(28)) {
                    val d = rnd.nextFloat()
                    add(x + g(0.6f), y - 1f, z + 1f + g(0.6f), g(3f), -5f - 4f * d, 6f + 6f * d + g(2f), 6f + rnd.nextFloat() * 3f, 0.8f, 6f + rnd.nextFloat() * 4f, tint, 0.26f, Shape.HAZE, FLOW or DRAG, delay = d * 0.8f)
                }
                repeat(n(6)) { add(x + g(0.3f), y + 0.5f, z + 0.5f, 0f, -6f, 0f, 0.8f, 0.35f, 0.25f, tint, 0.9f, Shape.SPHERE, 0, delay = it * 0.12f) }
            }
            BurstKind.WATER_CHANGE -> {
                // 1. old water and muck pulled into the siphon
                repeat(n(55)) {
                    val a = rnd.nextFloat() * TAU; val rr = 4f + rnd.nextFloat() * 12f
                    add(x + cos(a) * rr, (y - 8f + g(6f)).coerceIn(Camera.SAND_Y + 1f, Camera.WATER_Y - 2f), z + sin(a) * rr * 0.6f, 0f, 0f, 0f, 1.7f, 0.22f, 0.12f, MUCK, 0.7f, Shape.DOT, ATTRACT, cxv = x, cyv = y, czv = z)
                }
                repeat(n(20)) { add(x + g(4f), Camera.SAND_Y + 0.6f, z + g(3f), g(1f), 3f, g(1f), 2.2f, 0.8f, 3f, MUCK, 0.2f, Shape.HAZE, ATTRACT, cxv = x, cyv = y, czv = z) }
                // 2. fresh salt water swirling in from the return
                val rx = HOB_OUT[0]; val ry = HOB_OUT[1]; val rz = HOB_OUT[2]
                repeat(n(80)) {
                    add(rx + g(1f), ry - rnd.nextFloat() * 2f, rz + 3f + g(1f), g(2f), -10f - rnd.nextFloat() * 8f, 6f + g(2f), 2.6f + rnd.nextFloat(), 0.2f, 0.12f, WHITE, 0.5f, Shape.BUBBLE, SWIRL or BUOY or DRAG, delay = 1.4f + rnd.nextFloat() * 1.6f, cxv = rx, cyv = ry - 15f, czv = rz + 10f)
                }
                repeat(n(16)) { add(rx + g(2f), ry - 4f - rnd.nextFloat() * 10f, rz + 6f + g(4f), g(1f), -2f, 2f, 4f, 2f, 9f, CLEAR, 0.07f, Shape.HAZE, FLOW, delay = 1.6f + rnd.nextFloat()) }
            }
            BurstKind.HEAT, BurstKind.COOL -> {
                val hot = b.kind == BurstKind.HEAT
                val c = if (b.r != 1f || b.g != 1f || b.b != 1f) tint else if (hot) WARM else COOL_C
                repeat(n(30)) {
                    add(x + g(1.5f), y + g(5f), z + 1.5f + g(1.5f), g(0.5f), if (hot) 3f + rnd.nextFloat() * 3f else -2f - rnd.nextFloat() * 2f, g(0.5f), 2.5f + rnd.nextFloat(), 1.4f, 2.2f, c, 0.16f, Shape.STREAK, ORIENT or WOBBLE, delay = rnd.nextFloat() * 1.2f)
                }
                repeat(n(8)) { add(x + g(0.8f), y + g(4f), z + 1f, 0f, if (hot) 1f else -0.5f, 0f, 1.6f, 0.8f, 1.6f, c, 0.35f, Shape.GLOW, 0, delay = rnd.nextFloat() * 0.6f) }
            }
            BurstKind.SPARKS -> {
                repeat(n(18)) { add(x + g(0.5f), y + g(2f), z + 1f, g(8f), 4f + rnd.nextFloat() * 6f, 4f + g(4f), 0.5f + rnd.nextFloat() * 0.3f, 0.3f, 0.1f, SPARK, 1f, Shape.GLOW, DRAG) }
                repeat(n(10)) { add(x + g(0.6f), y + g(2f), z + 1f, g(1f), 2f, g(1f), 2f, 0.25f, 0.2f, WHITE, 0.6f, Shape.BUBBLE, BUOY, delay = 0.2f + rnd.nextFloat() * 0.4f) }
            }
            BurstKind.SALT -> {
                repeat(n(50)) { add(x + g(4f), Camera.WATER_Y - 0.5f, z + g(3f), 0f, -6f - rnd.nextFloat() * 4f, 0f, 2f + rnd.nextFloat(), 0.2f, 0.04f, WHITE, 0.9f, Shape.DOT, FLOW, delay = rnd.nextFloat() * 0.8f) }
                repeat(n(10)) { add(x + g(5f), Camera.WATER_Y - 4f - rnd.nextFloat() * 6f, z + g(3f), 0f, -1f, 0f, 3.5f, 2f, 6f, WHITE, 0.06f, Shape.HAZE, FLOW, delay = 0.6f + rnd.nextFloat()) }
            }
            BurstKind.FRESH -> {
                repeat(n(36)) {
                    val a = rnd.nextFloat() * TAU
                    add(x + cos(a) * 3f, Camera.WATER_Y - 1f - rnd.nextFloat() * 6f, z + sin(a) * 3f, 0f, -2f, 0f, 2.5f + rnd.nextFloat(), 1.6f, 2.6f, CLEAR, 0.18f, Shape.STREAK, SWIRL or ORIENT, delay = rnd.nextFloat() * 0.8f, cxv = x, cyv = y - 10f, czv = z)
                }
            }
            BurstKind.PUMP_ON, BurstKind.PUMP_OFF -> {
                val p = nearestPump(snap, x, y, z)
                val ox = p?.get(0) ?: x; val oy = p?.get(1) ?: y; val oz = p?.get(2) ?: z
                val ax = p?.get(3) ?: 1f; val ay = p?.get(4) ?: 0f; val az = p?.get(5) ?: 0f
                if (b.kind == BurstKind.PUMP_ON) {
                    repeat(n(45)) {
                        val sp = 35f + rnd.nextFloat() * 25f
                        add(ox + g(0.6f), oy + g(0.6f), oz + g(0.6f), ax * sp + g(3f), ay * sp + g(3f), az * sp + g(3f), 1.1f + rnd.nextFloat() * 0.6f, 1.2f, 2.2f, WHITE, 0.32f, Shape.STREAK, DRAG or ORIENT or FLOW, delay = rnd.nextFloat() * 0.7f)
                    }
                    repeat(n(14)) { add(ox + g(0.5f), oy + g(0.5f), oz + g(0.5f), ax * 20f, ay * 20f, az * 20f, 1.8f, 0.22f, 0.2f, WHITE, 0.55f, Shape.BUBBLE, DRAG or BUOY, delay = rnd.nextFloat() * 0.5f) }
                } else {
                    repeat(n(30)) {
                        val d = rnd.nextFloat() * 14f
                        add(ox + ax * d + g(1.5f), oy + ay * d + g(1.5f), oz + az * d + g(1.5f), ax * 12f, ay * 12f, az * 12f, 2.5f + rnd.nextFloat(), 0.25f, 0.2f, MUCK_LIGHT, 0.6f, Shape.DOT, DRAG or SINK)
                    }
                    repeat(n(6)) { add(ox + g(0.5f), oy, oz + g(0.5f), 0f, 1f, 0f, 2f, 0.22f, 0.2f, WHITE, 0.5f, Shape.BUBBLE, BUOY, delay = rnd.nextFloat() * 0.6f) }
                }
            }
            BurstKind.BUBBLES -> {
                repeat(n(55)) { add(x + g(1.5f), y - 2f - rnd.nextFloat() * 4f, z + g(1.5f), g(4f), -4f + g(3f), g(4f), 2f + rnd.nextFloat(), 0.15f + rnd.nextFloat() * 0.25f, 0.2f, WHITE, 0.6f, Shape.BUBBLE, BUOY or DRAG, delay = rnd.nextFloat() * 0.5f) }
                repeat(n(8)) { add(x + g(2f), y - 3f, z + g(2f), g(1f), -2f, g(1f), 4f, 1.5f, 6f, tint, 0.08f, Shape.HAZE, FLOW) }
            }
            BurstKind.SILT -> {
                repeat(n(30)) { add(x + g(4f), y + g(2f), z + g(3f), g(2f), 1f + rnd.nextFloat() * 2f, g(2f), 5f + rnd.nextFloat() * 3f, 1.2f, 5f, SILT_C, 0.2f, Shape.HAZE, FLOW or DRAG, delay = rnd.nextFloat() * 0.4f) }
                repeat(n(40)) { add(x + g(4f), y + g(3f), z + g(3f), g(4f), 2f + g(2f), g(4f), 5f, 0.14f, 0.12f, SILT_C, 0.8f, Shape.DOT, SINK or DRAG or FLOW) }
            }
            BurstKind.ZAP -> {
                add(x, y, z + 0.5f, 0f, 0f, 0f, 0.5f, 1f, 3f, WHITE, 0.9f, Shape.GLOW, 0)
                repeat(n(12)) { add(x + g(0.8f), y + g(0.8f), z + g(0.8f), g(1f), 0.5f, g(1f), 4f, 1f, 4.5f, WHITE, 0.3f, Shape.HAZE, FLOW, delay = 0.1f) }
                repeat(n(18)) { add(x + g(0.5f), y + g(0.5f), z + g(0.5f), g(5f), g(5f), g(5f), 2f, 0.15f, 0.1f, WHITE, 0.8f, Shape.DOT, DRAG or SINK) }
            }
            BurstKind.SPARKLE -> repeat(n(30)) {
                add(x + g(8f), y + g(6f), z + g(5f), g(1f), 0.5f + g(1f), g(1f), 1.2f + rnd.nextFloat(), 0.6f, 0.2f, tint, 0.8f, Shape.GLOW, FLOW or TWINKLE, delay = rnd.nextFloat() * 0.6f)
            }
            BurstKind.TAP -> repeat(n(40)) {
                val a = rnd.nextFloat() * TAU; val rr = rnd.nextFloat() * 3f
                add(x + cos(a) * rr, y + sin(a) * rr, min(z, Camera.TANK[2] - 0.6f), cos(a) * 6f, sin(a) * 6f, -14f - rnd.nextFloat() * 10f, 1f, 0.6f, 0.4f, CLEAR, 0.35f, Shape.STREAK, DRAG or ORIENT)
            }
            BurstKind.PODS -> repeat(n(45)) {
                add(x + g(3f), Camera.WATER_Y - 1f - rnd.nextFloat() * 3f, z + g(3f), g(1f), -1.5f, g(1f), 14f + rnd.nextFloat() * 8f, 0.32f, 0.3f, POD_C, 0.95f, Shape.POD, HOP or FLOW, delay = rnd.nextFloat() * 0.6f)
            }
            BurstKind.HAZE -> {
                repeat(n(30)) { add(x + g(2f), (y - 2f + g(2f)), z + g(2f), g(2f), -3f - rnd.nextFloat() * 3f, g(2f), 10f + rnd.nextFloat() * 6f, 1.5f, 9f, tint, 0.2f, Shape.HAZE, FLOW or DRAG, delay = rnd.nextFloat() * 0.8f) }
                repeat(n(4)) { add(x + g(0.3f), y + 0.5f, z, 0f, -6f, 0f, 0.8f, 0.4f, 0.3f, tint, 0.9f, Shape.SPHERE, 0, delay = it * 0.15f) }
            }
            BurstKind.MICROBUBBLES -> {
                val rx = HOB_OUT[0]; val ry = HOB_OUT[1]; val rz = HOB_OUT[2]
                repeat(n(90)) { add(rx + g(2f), ry - 1f - rnd.nextFloat() * 6f, rz + 3f + g(2f), g(2f), -6f - rnd.nextFloat() * 8f, 5f + g(3f), 3f + rnd.nextFloat() * 2f, 0.08f, 0.06f, WHITE, 0.7f, Shape.DOT, FLOW or DRAG or BUOY_SLOW, delay = rnd.nextFloat() * 1.2f) }
            }
            BurstKind.SUCTION -> repeat(n(50)) {
                add(rnd.nextFloat() * 100f + 10f, Camera.SAND_Y + 6f + rnd.nextFloat() * 40f, rnd.nextFloat() * 25f + 4f, 0f, 0f, 0f, 2.2f, 0.18f, 0.08f, MUCK, 0.6f, Shape.DOT, ATTRACT, delay = rnd.nextFloat() * 0.5f, cxv = HOB_IN[0], cyv = HOB_IN[1], czv = HOB_IN[2])
            }
            BurstKind.CLARITY -> repeat(n(45)) {
                add(5f + rnd.nextFloat() * 112f, Camera.SAND_Y + 4f + rnd.nextFloat() * 46f, 3f + rnd.nextFloat() * 27f, 0f, 0f, 0f, 1f + rnd.nextFloat(), 0.5f, 0.15f, CLEAR, 0.6f, Shape.GLOW, TWINKLE or FLOW, delay = rnd.nextFloat() * 1.2f)
            }
            BurstKind.UV_GLOW -> {
                val rx = HOB_OUT[0]; val ry = HOB_OUT[1]; val rz = HOB_OUT[2]
                repeat(n(16)) { add(rx + g(1.5f), ry - 1f - rnd.nextFloat() * 4f, rz + 2f + g(2f), g(1f), -3f, 4f, 1.6f + rnd.nextFloat(), 1f, 2.2f, UV_C, 0.5f, Shape.GLOW, FLOW, delay = rnd.nextFloat() * 0.6f) }
            }
            BurstKind.LIGHT_MOTES -> repeat(n(40)) {
                add(20f + rnd.nextFloat() * 82f, Camera.WATER_Y - 1f - rnd.nextFloat() * 18f, 4f + rnd.nextFloat() * 25f, 0f, -1f, 0f, 1.4f + rnd.nextFloat(), 0.45f, 0.2f, WARM_LIGHT, 0.7f, Shape.GLOW, TWINKLE or FLOW, delay = rnd.nextFloat() * 0.8f)
            }
            BurstKind.SHELLS -> {
                repeat(n(3)) { add(x + g(4f), Camera.WATER_Y - 1f, z + g(3f), 0f, -12f, 0f, 3.5f, 1.4f, 1.4f, SHELL_C, 1f, Shape.CHUNK, SINK_FAST, delay = it * 0.25f) }
                repeat(n(10)) { add(x + g(5f), Camera.SAND_Y + 0.6f, z + g(3f), g(2f), 2f, g(2f), 3f, 1f, 3.5f, SILT_C, 0.16f, Shape.HAZE, DRAG, delay = 1.2f + rnd.nextFloat() * 0.6f) }
            }
            BurstKind.GLASS_RIPPLE -> {
                // two thin rings of streaks on the inside of the pane, racing outward and fading (no z travel)
                val gz = min(z, Camera.TANK[2] - 0.5f)
                for (ring in 0 until 2) {
                    val m = n(if (ring == 0) 22 else 14)
                    val sp = if (ring == 0) 7f else 4.5f
                    repeat(m) {
                        val a = TAU * it / m + rnd.nextFloat() * 0.1f
                        add(x + cos(a) * 0.4f, y + sin(a) * 0.4f, gz, cos(a) * sp, sin(a) * sp, 0f, 0.75f, 0.45f, 0.3f, CLEAR, if (ring == 0) 0.5f else 0.3f, Shape.STREAK, DRAG or ORIENT, delay = ring * 0.14f)
                    }
                }
            }
            BurstKind.SAND_PUFF -> {
                val sy = Camera.SAND_Y + 0.4f
                repeat(n(8)) { add(x + g(1.2f), sy + rnd.nextFloat() * 0.6f, z + g(1.2f), g(1.5f), 1.2f + rnd.nextFloat() * 1.5f, g(1.5f), 2.2f + rnd.nextFloat(), 0.6f, 2.6f, SILT_C, 0.18f, Shape.HAZE, DRAG or FLOW) }
                repeat(n(16)) { add(x + g(0.8f), sy, z + g(0.8f), g(4f), 2.5f + rnd.nextFloat() * 3f, g(4f), 1.6f, 0.12f, 0.1f, SILT_C, 0.85f, Shape.DOT, SINK or DRAG) }
            }
            BurstKind.SURFACE_RIPPLE -> {
                // rings racing out over the surface, plus a few glints where the finger touched
                val sy = Camera.WATER_Y - 0.3f
                for (ring in 0 until 2) {
                    val m = n(if (ring == 0) 20 else 12)
                    val sp = if (ring == 0) 9f else 5.5f
                    repeat(m) {
                        val a = TAU * it / m + rnd.nextFloat() * 0.1f
                        add(x + cos(a) * 0.4f, sy, z + sin(a) * 0.4f, cos(a) * sp, 0f, sin(a) * sp, 0.9f, 0.45f, 0.3f, CLEAR, if (ring == 0) 0.45f else 0.28f, Shape.STREAK, DRAG or ORIENT, delay = ring * 0.16f)
                    }
                }
                repeat(n(4)) { add(x + g(0.6f), sy - 0.2f, z + g(0.6f), 0f, -0.5f, 0f, 0.6f, 0.4f, 0.2f, WHITE, 0.5f, Shape.GLOW, TWINKLE) }
            }
            BurstKind.SWISH -> {
                val dx = b.r * 45f; val dy = b.g * 45f; val dz = b.b * 45f
                repeat(n(14)) {
                    val k = 0.5f + rnd.nextFloat() * 0.6f
                    add(x + g(1.5f), y + g(1.5f), z + g(1.5f), dx * k, dy * k, dz * k, 0.8f + rnd.nextFloat() * 0.5f, 0.5f, 1.2f, CLEAR, 0.22f, Shape.STREAK, DRAG or ORIENT, delay = rnd.nextFloat() * 0.15f)
                }
                repeat(n(4)) { add(x + g(1f), y + g(1f), z + g(1f), dx * 0.3f, dy * 0.3f, dz * 0.3f, 1.4f, 0.22f, 0.18f, WHITE, 0.45f, Shape.BUBBLE, BUOY_SLOW or DRAG) }
            }
            BurstKind.NORI_BITS -> repeat(n(3)) {
                add(x + g(1.5f), y + g(3f), z + g(3f), 1f + g(1f), -0.6f, g(1f), 6f, 0.7f, 0.5f, FoodVisuals.NORI_COLOR, 0.9f, Shape.NORI, FLOW or SINK)
            }
        }
    }

    /** Steps every burst particle and the pump jets; [snap] gives the flow, the pumps and the nori sheet. */
    fun step(dtIn: Float, time: Float, snap: SimSnapshot?) {
        val dt = dtIn.coerceIn(0f, 0.1f)
        emitJets(dt, snap)
        // torn nori: the sim tears real pieces off its cloth (food particles), so nothing extra is spawned here
        val grid = snap?.flowGrid
        val strength = snap?.flowStrength ?: 1f
        val gusts = snap?.gusts ?: NO_GUSTS
        var i = 0
        while (i < count) {
            age[i] += dt
            if (age[i] >= life[i]) { kill(i); continue }
            if (age[i] < 0f) { i++; continue }
            val f = flags[i]
            if (f and DRAG != 0) { val d = exp(-2.2f * dt); vx[i] *= d; vy[i] *= d; vz[i] *= d }
            if (f and BUOY != 0) vy[i] = min(vy[i] + 24f * dt, 13f)
            if (f and BUOY_SLOW != 0) vy[i] = min(vy[i] + 6f * dt, 1.5f)
            if (f and SINK != 0) vy[i] = max(vy[i] - 4f * dt, -2f)
            if (f and SINK_FAST != 0) vy[i] = max(vy[i] - 30f * dt, -14f)
            if (f and SWIRL != 0) {
                val rx = px[i] - cx[i]; val rz = pz[i] - cz[i]
                val r = sqrt(rx * rx + rz * rz).coerceAtLeast(0.5f)
                val sp = 14f / (1f + r * 0.08f)
                vx[i] += (-rz / r * sp - vx[i] * 0.5f) * dt * 3f
                vz[i] += (rx / r * sp - vz[i] * 0.5f) * dt * 3f
            }
            if (f and ATTRACT != 0) {
                val dx = cx[i] - px[i]; val dy = cy[i] - py[i]; val dz = cz[i] - pz[i]
                val d = sqrt(dx * dx + dy * dy + dz * dz)
                if (d < 0.8f) { kill(i); continue }
                val sp = 4f + 50f * (age[i] / life[i]).pow(2)
                vx[i] = dx / d * sp; vy[i] = dy / d * sp; vz[i] = dz / d * sp
            }
            if (f and HOP != 0 && rnd.nextFloat() < dt * 1.4f) { vx[i] += g(6f); vy[i] += g(4f); vz[i] += g(6f) }
            if (f and HOP != 0) { val d = exp(-5f * dt); vx[i] *= d; vz[i] *= d; vy[i] = vy[i] * d + (-0.8f) * (1f - d) }
            var fx = 0f; var fy = 0f; var fz = 0f
            if (f and FLOW != 0) {
                ParticleField.flowAt(grid, px[i], py[i], pz[i], time, v); fx = v[0] * strength * 0.6f; fy = v[1] * strength * 0.4f; fz = v[2] * strength * 0.6f
                if (gusts.isNotEmpty()) { gustAt(gusts, px[i], py[i], pz[i], v); fx += v[0]; fy += v[1]; fz += v[2] }
            }
            if (f and WOBBLE != 0) { fx += sin(time * 3f + seedA[i] * 20f) * 1.2f; fz += cos(time * 2.6f + seedA[i] * 13f) * 0.8f }
            px[i] += (vx[i] + fx) * dt; py[i] += (vy[i] + fy) * dt; pz[i] += (vz[i] + fz) * dt
            // keep inside the water: bubbles pop at the surface, the rest stop at the walls and the sand
            if (py[i] > Camera.WATER_Y - 0.25f) { if (shape[i] == Shape.BUBBLE || f and BUOY != 0) { kill(i); continue }; py[i] = Camera.WATER_Y - 0.25f; vy[i] = min(vy[i], 0f) }
            if (py[i] < Camera.SAND_Y + 0.2f) { py[i] = Camera.SAND_Y + 0.2f; vy[i] = 0f; vx[i] *= 0.5f; vz[i] *= 0.5f }
            px[i] = px[i].coerceIn(0.4f, Camera.TANK[0] - 0.4f); pz[i] = pz[i].coerceIn(0.4f, Camera.TANK[2] - 0.4f)
            i++
        }
    }

    /** Packs live particles: soft hazes into [haze], everything else into [solid]. [viewProj] orients streaks. */
    fun pack(viewProj: FloatArray, time: Float, haze: PointSink, solid: PointSink, maxHaze: Int, maxSolid: Int) {
        var nh = 0; var ns = 0
        for (i in 0 until count) {
            if (age[i] < 0f) continue
            val t = age[i] / life[i]
            val fadeIn = min(age[i] / min(0.15f, life[i] * 0.2f), 1f)
            val fadeOut = min((1f - t) / 0.4f, 1f)
            var a = ca[i] * fadeIn * fadeOut
            if (flags[i] and TWINKLE != 0) a *= 0.55f + 0.45f * sin(time * 9f + seedA[i] * 40f)
            if (a <= 0.004f) continue
            val size = s0[i] + (s1[i] - s0[i]) * t
            val angle = if (flags[i] and ORIENT != 0) FoodVisuals.screenAngle(viewProj, px[i], py[i], pz[i], vx[i], vy[i], vz[i]) else seedA[i] * 6.283f + time * 0.3f
            if (shape[i] == Shape.HAZE) {
                if (nh >= maxHaze) continue
                haze.put(px[i], py[i], pz[i], size, cr[i], cg[i], cb[i], a, Shape.HAZE, seedA[i], angle, 0f); nh++
            } else {
                if (ns >= maxSolid) continue
                solid.put(px[i], py[i], pz[i], size, cr[i], cg[i], cb[i], a, shape[i], seedA[i], angle, if (shape[i] == Shape.POD) 0f else 1f); ns++
            }
        }
    }

    fun clear() { count = 0 }

    private fun emitJets(dt: Float, snap: SimSnapshot?) {
        val p = snap?.pumps ?: return
        val stride = SimSnapshot.PUMP_STRIDE
        val n = min(p.size / stride, jetAcc.size)
        for (j in 0 until n) {
            val o = j * stride
            val out = p[o + 6]
            if (out <= 0.01f) { jetAcc[j] = 0f; continue }
            val v0 = p[o + 7]
            jetAcc[j] += dt * JET_RATE * out * (v0 / 60f).coerceIn(0.3f, 1f)
            while (jetAcc[j] >= 1f) {
                jetAcc[j] -= 1f
                if (count >= CAP - 40) break   // keep room for action bursts
                val sp = v0 * out * (0.55f + rnd.nextFloat() * 0.35f)
                val ax = p[o + 3]; val ay = p[o + 4]; val az = p[o + 5]
                add(
                    p[o] + ax * 1.5f + g(0.7f), p[o + 1] + ay * 1.5f + g(0.7f), p[o + 2] + az * 1.5f + g(0.7f),
                    ax * sp + g(2f), ay * sp + g(2f), az * sp + g(2f), 0.9f + 0.5f * out, 0.9f, 1.6f + 0.6f * out, WHITE, 0.12f, Shape.STREAK, DRAG or ORIENT,
                )
            }
        }
    }

    private fun nearestPump(snap: SimSnapshot?, x: Float, y: Float, z: Float): FloatArray? {
        val p = snap?.pumps ?: return null
        val stride = SimSnapshot.PUMP_STRIDE
        var best = -1; var bd = Float.MAX_VALUE
        for (j in 0 until p.size / stride) {
            val o = j * stride
            val d = (p[o] - x) * (p[o] - x) + (p[o + 1] - y) * (p[o + 1] - y) + (p[o + 2] - z) * (p[o + 2] - z)
            if (d < bd) { bd = d; best = j }
        }
        return if (best < 0) null else p.copyOfRange(best * stride, best * stride + stride)
    }

    private fun add(
        x: Float, y: Float, z: Float, vxv: Float, vyv: Float, vzv: Float, lifeS: Float, size0: Float, size1: Float,
        c: FloatArray, alpha: Float, shp: Float, fl: Int, delay: Float = 0f, cxv: Float = 0f, cyv: Float = 0f, czv: Float = 0f,
    ) {
        if (count >= CAP) return
        val i = count++
        px[i] = x.coerceIn(0.4f, Camera.TANK[0] - 0.4f); py[i] = y.coerceIn(Camera.SAND_Y + 0.2f, Camera.WATER_Y - 0.2f); pz[i] = z.coerceIn(0.4f, Camera.TANK[2] - 0.4f)
        vx[i] = vxv; vy[i] = vyv; vz[i] = vzv
        cx[i] = cxv; cy[i] = cyv; cz[i] = czv
        age[i] = -delay; life[i] = lifeS
        s0[i] = size0; s1[i] = size1
        cr[i] = c[0]; cg[i] = c[1]; cb[i] = c[2]; ca[i] = alpha
        shape[i] = shp; seedA[i] = rnd.nextFloat(); flags[i] = fl
    }

    /** Swap-remove particle [i] (the last one moves into its slot and is stepped next). */
    private fun kill(i: Int) {
        val l = --count
        if (i == l) return
        px[i] = px[l]; py[i] = py[l]; pz[i] = pz[l]; vx[i] = vx[l]; vy[i] = vy[l]; vz[i] = vz[l]
        cx[i] = cx[l]; cy[i] = cy[l]; cz[i] = cz[l]; age[i] = age[l]; life[i] = life[l]; s0[i] = s0[l]; s1[i] = s1[l]
        cr[i] = cr[l]; cg[i] = cg[l]; cb[i] = cb[l]; ca[i] = ca[l]; shape[i] = shape[l]; seedA[i] = seedA[l]; flags[i] = flags[l]
    }

    /** Gaussian-ish jitter of about ±[s]. */
    private fun g(s: Float): Float = (rnd.nextFloat() + rnd.nextFloat() + rnd.nextFloat() - 1.5f) * s * 0.8f

    companion object {
        const val CAP = 700
        /** Jet streaks per second from a pump at full output. */
        const val JET_RATE = 14f
        private const val TAU = (2 * PI).toFloat()
        // behaviour flags
        private const val FLOW = 1
        private const val DRAG = 2
        private const val BUOY = 4
        private const val SINK = 8
        private const val SWIRL = 16
        private const val ATTRACT = 32
        private const val ORIENT = 64
        private const val TWINKLE = 128
        private const val HOP = 256
        private const val WOBBLE = 512
        private const val BUOY_SLOW = 1024
        private const val SINK_FAST = 2048
        private val NO_GUSTS = FloatArray(0)

        /**
         * Velocity (cm/s) the snapshot's touch puffs ([SimSnapshot.gusts]) add at a point, into [out]; the same puff
         * profile the sim uses ([com.reeflive.core.geom.FlowGust.strength]).
         */
        fun gustAt(g: FloatArray, x: Float, y: Float, z: Float, out: FloatArray) {
            out[0] = 0f; out[1] = 0f; out[2] = 0f
            val stride = com.reeflive.core.geom.FlowGust.STRIDE
            var o = 0
            while (o + stride <= g.size) {
                val sp = com.reeflive.core.geom.FlowGust.strength(
                    g[o].toDouble(), g[o + 1].toDouble(), g[o + 2].toDouble(), g[o + 3].toDouble(), g[o + 4].toDouble(), g[o + 5].toDouble(),
                    g[o + 6].toDouble(), g[o + 7].toDouble(), x.toDouble(), y.toDouble(), z.toDouble(),
                ).toFloat()
                out[0] += g[o + 3] * sp; out[1] += g[o + 4] * sp; out[2] += g[o + 5] * sp
                o += stride
            }
        }

        /** HOB return outlet and intake (equipment.glb: ReturnNozzle outflow (73, 37.4, 2.3) at the back middle, HobFilter intake x 114.5 at the right). */
        val HOB_OUT = floatArrayOf(73f, Camera.WATER_Y - 0.7f, 2f)
        val HOB_IN = floatArrayOf(114.5f, Camera.WATER_Y - 3f, 2f)

        private fun lin(c: Float) = c.coerceIn(0f, 1f).pow(2.2f)
        private fun rgb(r: Int, g: Int, b: Int) = floatArrayOf(lin(r / 255f), lin(g / 255f), lin(b / 255f))
        private val WHITE = rgb(235, 245, 255)
        private val CLEAR = rgb(210, 235, 255)
        private val MUCK = rgb(120, 92, 60)
        private val MUCK_LIGHT = rgb(170, 150, 120)
        private val SILT_C = rgb(196, 182, 150)
        private val WARM = rgb(255, 170, 90)
        private val COOL_C = rgb(120, 190, 255)
        private val SPARK = rgb(255, 230, 140)
        private val POD_C = rgb(216, 203, 168)
        private val UV_C = rgb(170, 110, 255)
        private val WARM_LIGHT = rgb(255, 236, 190)
        private val SHELL_C = rgb(226, 210, 184)
    }
}
