package com.reeflive.app.render

/**
 * Lighting derived from the AI Prime HD's 8 channels (ARCHITECTURE §8.5). Channel order: white, blue,
 * royal, red, green, violet, uv, moon (0..1 each, as in [com.reeflive.core.sim.SimSnapshot.lights]).
 * Pure math (unit-tested); colors are linear RGB.
 */
class LightingState private constructor(
    /** Σ chan × rgb × weight (linear RGB, not normalized). */
    val keyColor: FloatArray,
    /** clamp(Σ chan × weight, 0, 1). */
    val intensity: Float,
    val ambient: FloatArray,
    val fogColor: FloatArray,
    /** (blue + royal + violet) / (white + blue + royal + violet + ε): coral fluorescence driver. */
    val blueFraction: Float,
    /** True when intensity < 0.03: moon channel only; particles dim; fish specular reduced. */
    val night: Boolean,
) {
    /** Light color × intensity for one fixture, normalized so a full-white peak ≈ 1 (shader `uLightColor`). */
    fun lightColor(): FloatArray {
        val m = maxOf(keyColor[0], keyColor[1], keyColor[2], 1e-4f)
        val k = intensity / m
        return floatArrayOf(keyColor[0] * k, keyColor[1] * k, keyColor[2] * k)
    }

    companion object {
        val CHANNEL_RGB: Array<FloatArray> = arrayOf(
            floatArrayOf(0.85f, 0.93f, 1.0f),  // white
            // blue / royal carry less red than the UI swatches (core LightSchedule): with 0.23 / 0.18 red the blue-heavy
            // schedules lit the sand and water periwinkle-violet instead of the photos' clean blue
            floatArrayOf(0.12f, 0.60f, 1.0f),  // blue
            floatArrayOf(0.14f, 0.34f, 1.0f),  // royal
            floatArrayOf(1.0f, 0.3f, 0.3f),    // red
            floatArrayOf(0.3f, 1.0f, 0.54f),   // green
            floatArrayOf(0.42f, 0.24f, 1.0f),  // violet
            floatArrayOf(0.35f * 0.5f, 0.2f * 0.5f, 0.9f * 0.5f),  // UV
            floatArrayOf(0.17f * 0.15f, 0.23f * 0.15f, 0.43f * 0.15f), // moon
        )
        /** Diode counts (4,4,4,1,1,1,1,1)/16. */
        val WEIGHTS = floatArrayOf(4f, 4f, 4f, 1f, 1f, 1f, 1f, 1f).map { it / 16f }.toFloatArray()

        /** Fixture lens centres (cm) and cone (ARCHITECTURE §8.5); must equal equipment.glb `AiPrimeL/R_Lens` `lightPos`. */
        val LIGHT_POS: Array<FloatArray> = arrayOf(floatArrayOf(32f, 90f, 16f), floatArrayOf(91f, 90f, 16f))
        const val CONE_DEG = 120f
        private val FOG_TINT = floatArrayOf(0x0E / 255f, 0x3B / 255f, 0x5A / 255f)
        private val FOG_FLOOR = floatArrayOf(0x02 / 255f, 0x06 / 255f, 0x0C / 255f)
        const val NIGHT_THRESHOLD = 0.03f

        fun from(channels: FloatArray): LightingState {
            val c = FloatArray(8) { if (it < channels.size) channels[it].coerceIn(0f, 1f) else 0f }
            val raw = FloatArray(8) { c[it] * WEIGHTS[it] }
            val rawIntensity = raw.sum().coerceIn(0f, 1f)
            val night = rawIntensity < NIGHT_THRESHOLD
            // Night: moon channel only (the other diodes are below visibility).
            val use = if (night) FloatArray(8) { if (it == 7) c[7] else 0f } else c
            val key = FloatArray(3)
            for (i in 0 until 8) for (k in 0 until 3) key[k] += use[i] * CHANNEL_RGB[i][k] * WEIGHTS[i]
            val intensity = if (night) maxOf(rawIntensity, c[7] * WEIGHTS[7]) else rawIntensity
            val ambient = FloatArray(3) { key[it] * 0.18f }
            // Normalize the hue so fog tracks the light colour, not its weight sum, then scale by intensity.
            val kmax = maxOf(key[0], key[1], key[2], 1e-4f)
            val fog = FloatArray(3) { key[it] / kmax * FOG_TINT[it] * intensity + FOG_FLOOR[it] }
            val blue = c[1] + c[2] + c[5]
            val blueFraction = blue / (c[0] + blue + 1e-4f)
            return LightingState(key, intensity, ambient, fog, blueFraction.coerceIn(0f, 1f), night)
        }
    }
}
