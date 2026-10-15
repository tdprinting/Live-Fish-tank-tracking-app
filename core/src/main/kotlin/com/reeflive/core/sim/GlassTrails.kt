package com.reeflive.core.sim

import kotlin.math.sqrt

/**
 * Grazing trails on the front glass (SIMULATION §11.1). Astrea and other turban / top snails, and asterina stars,
 * graze the film off the pane as they crawl, leaving clean meandering trails in it; the film creeps back over a trail
 * in days at its own net growth rate ([GlassMask.regrow]), and the next pass wears it open again. Where [AlgaeModel]
 * already takes their grazing off the glass biomass as a whole, this only says *where* on the front pane it went: the
 * mask is a relative density, so the film between the trails is drawn that much thicker and the total is unchanged.
 *
 * Each grazer on the front pane (foot on the glass: within [ON_PANE_CM] of it, its up vector pointing into the tank)
 * lays a segment every [MARK_STEP_CM] of travel ([GlassMask.graze]); a jump of more than [JUMP_CM] (a teleport, a
 * fall) starts a new trail. Trails are published at most every [PUBLISH_TICKS] sim ticks (2 s at 20 Hz) so the
 * snapshot copy of the mask is not remade every tick. Allocation-free per tick.
 */
class GlassTrails {
    private var ticks = 0

    fun step(organisms: List<Organism>, mask: GlassMask, frontZ: Double) {
        for (o in organisms) {
            val g = if (o.alive) GrazerType.of(o) else null
            val trail = g?.let { trailOf(it) }
            if (trail == null || o.pos.z < frontZ - ON_PANE_CM || o.up.z > -0.5) { o.trailX = Double.NaN; continue }
            val x = o.pos.x; val y = o.pos.y
            if (o.trailX.isNaN()) { o.trailX = x; o.trailY = y; continue }
            val dx = x - o.trailX; val dy = y - o.trailY
            val d = sqrt(dx * dx + dy * dy)
            if (d < MARK_STEP_CM) continue
            if (d <= JUMP_CM) {
                val w = (trail.widthFraction * o.lengthCm).coerceIn(0.3, 2.5)
                mask.graze(o.trailX, o.trailY, x, y, w, trail.take)
            }
            o.trailX = x; o.trailY = y
        }
        if (++ticks >= PUBLISH_TICKS) { ticks = 0; mask.publish() }
    }

    /** How a grazer wears the film: band width as a share of body length, and the share of film one pass takes. */
    class Trail(val widthFraction: Double, val take: Double)

    companion object {
        /**
         * Turban and top snails (Turbinidae, Trochidae: rhipidoglossan radula) sweep loose microalgal film but cannot
         * excavate (Steneck & Watling 1982), so one pass takes most of a soft film, not all of it; the swath is about
         * the snout's width, ~1/3 of the shell (DESIGN ESTIMATE, research 15 asks for observed tracks).
         */
        @Ev(Evidence.DESIGN_ESTIMATE, "Steneck & Watling 1982 Mar Biol 68:299; Hawkins & Hartnoll 1983; 15 Lithopoma")
        val SNAIL = Trail(widthFraction = 0.35, take = 0.6)
        /** Asterina stars evert the stomach over the film: a disc about half the star wide, a lighter clean (est.). */
        @Ev(Evidence.DESIGN_ESTIMATE, "11 §7")
        val STAR = Trail(widthFraction = 0.5, take = 0.4)

        fun trailOf(g: GrazerType): Trail? = when (g) {
            GrazerType.TURBO, GrazerType.TROCHUS -> SNAIL
            GrazerType.ASTERINA -> STAR
            else -> null
        }

        /** Foot on the pane: centre within this of the glass. */
        const val ON_PANE_CM = 2.5
        /** Travel (cm) between trail segments: about one mask cell. */
        const val MARK_STEP_CM = 0.6
        /** Longer moves in one step are a jump, not a crawl (no trail across it). */
        const val JUMP_CM = 8.0
        const val PUBLISH_TICKS = 40
    }
}
