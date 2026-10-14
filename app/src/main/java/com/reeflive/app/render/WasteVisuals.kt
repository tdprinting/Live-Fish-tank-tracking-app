package com.reeflive.app.render

import com.reeflive.core.sim.WasteSystem

/**
 * Fish feces ([WasteSystem], research 18_fish_waste): each pellet is a short, slightly bent string (sprite shape 16 in
 * particles.frag) that tumbles slowly as it sinks, then lies flat on the sand or rock, slumping, paling and fading
 * as it breaks down into mulm. Colour follows the diet: brown for meaty eaters, olive for the algae grazers, pale for
 * the plankton and parasite pickers.
 */
object WasteVisuals {
    // per tint: brown, olive green-brown, pale grey-buff (linear rgb)
    private val COLOR = floatArrayOf(
        0.20f, 0.13f, 0.07f,
        0.17f, 0.18f, 0.07f,
        0.42f, 0.38f, 0.30f,
    )

    /** Packs at most [max] pellets from [w] (stride [WasteSystem.STRIDE]) into [sink]. */
    fun pack(w: FloatArray, max: Int, sink: PointSink) {
        val n = minOf(w.size / WasteSystem.STRIDE, max)
        for (i in 0 until n) {
            val o = i * WasteSystem.STRIDE
            val tint = w[o + 4].toInt().coerceIn(0, 2) * 3
            val settled = w[o + 5]
            val age = (settled - 1f).coerceIn(0f, 1f)          // breakdown progress once settled
            val seed = w[o + 6]
            val fade = if (settled > 0.5f) 1f - 0.55f * age else 1f
            val pale = 1f + 0.6f * age                          // bacteria and fungal film pale a lying pellet
            val angle = if (settled > 0.5f) seed * 6.283f else w[o + 7]
            // param: bend of the string; a settled pellet flattens and crumbles (> 1 marks it lying)
            val param = if (settled > 0.5f) 1f + age else 0.2f + 0.6f * seed
            sink.put(w[o], w[o + 1], w[o + 2], w[o + 3], COLOR[tint] * pale, COLOR[tint + 1] * pale, COLOR[tint + 2] * pale, 0.92f * fade, Shape.FECES, seed, angle, param)
        }
    }
}
