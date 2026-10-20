package com.reeflive.app.render

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.TankSpot
import com.reeflive.core.sim.TouchSurface
import java.util.concurrent.atomic.AtomicReference

/** What an aimed tap landed on: the world point (cm), the surface there, and the animal when it was one. */
class AimHit(val point: Vec3, val surface: TouchSurface, val animalId: String?)

/**
 * Tap-to-place (food from the shelf goes in right where the player taps). While [armed], the next single tap on the
 * tank is not a pick or a touch: the renderer finds what is under the finger (an animal, a coral, rock, sand, or the
 * open water half way back) and [onAim] hears it on the main thread. A host whose own layer takes the touches (the
 * Android carry layer) asks with [aimAt] instead. Any thread.
 */
class TankAimControl {
    @Volatile var armed: Boolean = false
    @Volatile var onAim: ((AimHit) -> Unit)? = null
    /** Taps on the gear open its pop-out menu ([TankViewListener.onSpot]); hosts turn it off while taps mean something else (rock building). */
    @Volatile var gearTaps: Boolean = true
    private val request = AtomicReference<FloatArray?>(null)

    /** Aim at view pixel ([x], [y]) on the next frame (the GL view's own pixels, origin top-left). */
    fun aimAt(x: Float, y: Float) = request.set(floatArrayOf(x, y))

    internal fun take(): FloatArray? = request.getAndSet(null)
}

/**
 * The tank's tappable gear in equipment.glb (tools/blender/equipment.py node names) and the skimmer cup from
 * care_tools.glb, as padded world boxes. A tap whose ray reaches one of these before any rock or sand opens that
 * gear's pop-out menu ([TankViewListener.onSpot]).
 */
internal object SpotBoxes {
    val NODES: Map<String, TankSpot> = mapOf(
        "Skimmer" to TankSpot.SKIMMER,
        "HobFilter" to TankSpot.FILTER, "ReturnPipe" to TankSpot.FILTER, "ReturnNozzle" to TankSpot.FILTER,
        "Heater" to TankSpot.HEATER, "HeaterLED" to TankSpot.HEATER,
        "PowerheadLeft" to TankSpot.PUMPS, "PowerheadBack" to TankSpot.PUMPS, "PowerheadRight" to TankSpot.PUMPS,
        "AiPrimeL" to TankSpot.LIGHTS, "AiPrimeR" to TankSpot.LIGHTS, "AiPrimeL_Lens" to TankSpot.LIGHTS, "AiPrimeR_Lens" to TankSpot.LIGHTS,
        "AtoBox" to TankSpot.TOP_OFF, "AtoLED" to TankSpot.TOP_OFF,
    )
    /** Finger slack around each box (cm): slim gear like the heater is still easy to hit. */
    const val PAD = 1.5f

    /** Boxes (minX, minY, minZ, maxX, maxY, maxZ) with their spots, built once from the loaded equipment model. */
    fun build(equipment: GpuModel): List<Pair<TankSpot, FloatArray>> {
        val out = ArrayList<Pair<TankSpot, FloatArray>>()
        for (p in equipment.parts) {
            val spot = NODES[p.node.name] ?: continue
            val b = p.worldBounds
            out += spot to floatArrayOf(b[0] - PAD, b[1] - PAD, b[2] - PAD, b[3] + PAD, b[4] + PAD, b[5] + PAD)
        }
        val c = CareLayout.CUP
        out += TankSpot.SKIMMER to floatArrayOf(c[0] - 3f - PAD, c[1] - PAD, c[2] - 3f - PAD, c[0] + 3f + PAD, c[1] + CareLayout.CUP_H + PAD, c[2] + 3f + PAD)
        return out
    }

    /** Nearest box the ray (origin [o], unit direction [d]) enters: its spot and distance, or null. */
    fun hit(boxes: List<Pair<TankSpot, FloatArray>>, o: FloatArray, d: FloatArray): Pair<TankSpot, Float>? {
        var best: TankSpot? = null
        var bestT = Float.MAX_VALUE
        for ((spot, b) in boxes) {
            val t = rayBox(o, d, b) ?: continue
            if (t < bestT) { bestT = t; best = spot }
        }
        return best?.let { it to bestT }
    }

    /** Slab test: entry distance of the ray into box [b] (0 when it starts inside), or null on a miss. */
    fun rayBox(o: FloatArray, d: FloatArray, b: FloatArray): Float? {
        var t0 = 0f; var t1 = Float.MAX_VALUE
        for (k in 0 until 3) {
            val lo = b[k]; val hi = b[k + 3]
            if (kotlin.math.abs(d[k]) < 1e-7f) { if (o[k] < lo || o[k] > hi) return null; continue }
            var a = (lo - o[k]) / d[k]; var c = (hi - o[k]) / d[k]
            if (a > c) { val s = a; a = c; c = s }
            if (a > t0) t0 = a
            if (c < t1) t1 = c
            if (t0 > t1) return null
        }
        return t0
    }
}
