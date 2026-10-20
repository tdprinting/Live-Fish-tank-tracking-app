package com.reeflive.app.render

/**
 * Fluorescent pigment colours for coral tissues whose GLB material carries no emissive (GSP, mushrooms and the
 * anemone already do). polyp.frag / softcoral.frag scale it by the blue share of the light, so it barely shows under
 * daylight and glows under actinics (Tee's tank video 2026-10-20: zoas glow lime and orange, clove and organ pipe
 * polyps green). Linear RGB, keyed by GLB material name; looked up once per material at load.
 */
object Fluorescence {
    fun of(material: String): FloatArray? = when (material) {
        "zoa_polyp_orange" -> floatArrayOf(0.30f, 0.15f, 0.01f)
        "zoa_polyp_brown" -> floatArrayOf(0.07f, 0.26f, 0.03f)
        "paly_polyp_brown" -> floatArrayOf(0.12f, 0.24f, 0.02f)
        "clove_polyp" -> floatArrayOf(0.03f, 0.20f, 0.08f)
        "organpipe_polyp" -> floatArrayOf(0.03f, 0.15f, 0.07f)
        else -> null
    }
}
