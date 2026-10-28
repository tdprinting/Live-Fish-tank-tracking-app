package com.reeflive.app.render

/**
 * Fluorescent pigment colours for coral tissues whose GLB material carries no emissive (GSP, mushrooms and the
 * anemone already do). polyp.frag / softcoral.frag scale it by the blue share of the light, so it barely shows under
 * daylight and glows under actinics (Tee's tank video 2026-10-20: zoas glow lime and orange, clove and organ pipe
 * polyps green). Linear RGB, keyed by GLB material name; looked up once per material at load. A third (orange: an
 * eighth) of the first pass: under the daylight mix the zoas read as orange confetti, not tissue (research 11 §6).
 */
object Fluorescence {
    fun of(material: String): FloatArray? = when (material) {
        "zoa_polyp_orange" -> floatArrayOf(0.035f, 0.018f, 0.002f)
        "zoa_polyp_brown" -> floatArrayOf(0.025f, 0.09f, 0.01f)
        "paly_polyp_brown" -> floatArrayOf(0.04f, 0.08f, 0.007f)
        "clove_polyp" -> floatArrayOf(0.03f, 0.20f, 0.08f)
        "organpipe_polyp" -> floatArrayOf(0.03f, 0.15f, 0.07f)
        else -> null
    }
}
