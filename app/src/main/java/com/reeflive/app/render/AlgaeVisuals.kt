package com.reeflive.app.render

import com.reeflive.core.sim.AlgaeCover
import com.reeflive.core.sim.AlgaeGroup
import com.reeflive.core.sim.AlgaeSurface

/**
 * Packs the per-surface algae cover (research 09 A6) into the four `uAlgae*` vec4s of the `Frame` UBO
 * (assets/shaders/common.glsl): glass, sand, rock top, rock shade. Pure math, no GL.
 */
object AlgaeVisuals {
    const val FLOATS = 16

    /** Which group goes in each slot: four vec4s, in UBO order. Keep in sync with common.glsl. */
    val LAYOUT: Array<Pair<AlgaeGroup, AlgaeSurface>> = arrayOf(
        // uAlgaeGlass: film, diatoms, cyano, dinos
        AlgaeGroup.FILM to AlgaeSurface.GLASS, AlgaeGroup.DIATOMS to AlgaeSurface.GLASS,
        AlgaeGroup.CYANO to AlgaeSurface.GLASS, AlgaeGroup.DINOS to AlgaeSurface.GLASS,
        // uAlgaeSand: diatoms, cyano, dinos, hair
        AlgaeGroup.DIATOMS to AlgaeSurface.SAND, AlgaeGroup.CYANO to AlgaeSurface.SAND,
        AlgaeGroup.DINOS to AlgaeSurface.SAND, AlgaeGroup.HAIR to AlgaeSurface.SAND,
        // uAlgaeTop: hair, cyano, dinos, bubble
        AlgaeGroup.HAIR to AlgaeSurface.ROCK_TOP, AlgaeGroup.CYANO to AlgaeSurface.ROCK_TOP,
        AlgaeGroup.DINOS to AlgaeSurface.ROCK_TOP, AlgaeGroup.BUBBLE to AlgaeSurface.ROCK_TOP,
        // uAlgaeShade: coralline, cyano, dinos, bubble
        AlgaeGroup.CORALLINE to AlgaeSurface.ROCK_SHADE, AlgaeGroup.CYANO to AlgaeSurface.ROCK_SHADE,
        AlgaeGroup.DINOS to AlgaeSurface.ROCK_SHADE, AlgaeGroup.BUBBLE to AlgaeSurface.ROCK_SHADE,
    )

    /**
     * Writes [FLOATS] covers (each clamped to 0..1, NaN → 0) into [out] at [offset]. Once the Flipper has scraped the
     * front glass the glass slots carry the cover of untouched glass ([AlgaeCover.glassBase]); glass.frag thins it
     * with the scrape mask texture, so the stripes look clean and the rest of the film looks as it did.
     */
    fun pack(cover: AlgaeCover, out: FloatArray, offset: Int) {
        val base = cover.glassBase
        for (i in 0 until FLOATS) {
            val (g, s) = LAYOUT[i]
            val idx = g.ordinal * AlgaeCover.SURFACES + s.ordinal
            val v = when {
                s == AlgaeSurface.GLASS && base != null && g.ordinal < base.size -> base[g.ordinal]
                idx < cover.cover.size -> cover.cover[idx]
                else -> 0f
            }
            out[offset + i] = if (v.isNaN()) 0f else v.coerceIn(0f, 1f)
        }
    }

    /** `uDinoLift` (uTank.w): share of the dino film lifted into the water at night, 0..1. */
    fun dinoLift(cover: AlgaeCover): Float = cover.dinoLift.let { if (it.isNaN()) 0f else it.coerceIn(0f, 1f) }

    /**
     * Bubble algae spheres to draw ([BubbleAlgaeDraw]): the rock cover of bubble algae as a share of its cover limit
     * (research 09 A6: 30 % of the rock), shade and top weighted by area as the sim weights its headline, times the
     * [budget] of instance slots. Fractional: the last sphere grows in.
     */
    fun bubbleCount(cover: AlgaeCover, budget: Int): Float {
        val shade = cover.cover(AlgaeGroup.BUBBLE, AlgaeSurface.ROCK_SHADE)
        val top = cover.cover(AlgaeGroup.BUBBLE, AlgaeSurface.ROCK_TOP)
        val c = (BUBBLE_SHADE_WEIGHT * shade + (1f - BUBBLE_SHADE_WEIGHT) * top) / BUBBLE_CMAX
        if (c.isNaN() || c <= 0f || budget <= 0) return 0f
        return c.coerceAtMost(1f) * budget
    }

    /** Bubble algae cover limit on rock (AlgaeModel: Cmax 0.3). */
    const val BUBBLE_CMAX = 0.3f
    /** Shaded rock's share of the rock area (AlgaeModel: shade 0.7, top 0.5 of the footprint). */
    const val BUBBLE_SHADE_WEIGHT = 0.7f / 1.2f
}
