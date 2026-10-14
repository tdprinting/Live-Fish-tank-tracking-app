package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.geom.smoothstep
import com.reeflive.core.sim.Ev
import com.reeflive.core.sim.Evidence
import com.reeflive.core.sim.FoodParticle
import com.reeflive.core.sim.Organism
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Shared perception channels (research realism_gaps X1 and X6, SIMULATION §21.1). Food and threat queries pass
 * through three channels, each declared per species in [com.reeflive.core.sim.SensesSpec]:
 *
 * - **vision**: range = visionCm × (darkVision + (1 − darkVision) × smoothstep(0.02, 0.3, light)). By day the range
 *   spans the tank, so daylight foraging is exactly what it was; in the dark a diurnal fish sees only nearby food.
 * - **lateral line**: anything within lateralBL body lengths is felt whatever the light (POH04: catfish lose most
 *   nocturnal prey capture without it; the transfer to reef fish is an analogy).
 * - **chemoreception**: each food particle leaks scent that the current carries. A receiver is in the plume if it is
 *   within the diffusion radius √(4·D·age) of the particle, or downstream inside a cone of half-angle [PLUME_SPREAD]
 *   whose front has travelled |u|·age. Upstream animals smell nothing until diffusion reaches them (DIX12 direction).
 *
 * Nothing here draws random numbers, so adding the gate leaves every random sequence unchanged.
 */
object Perceive {
    /** Light level at which sight starts to recover from its darkness floor, and where it is full. */
    const val DIM_LIGHT = 0.02
    const val FULL_LIGHT = 0.3
    /** Turbulent diffusivity of scent in a stirred tank, cm²/s, and its reach cap (DESIGN ESTIMATES [L]). */
    @Ev(Evidence.DESIGN_ESTIMATE, "realism_gaps X1; eddy diffusivity [L]") const val SCENT_D = 1.5
    const val SCENT_DIFFUSE_MAX_CM = 25.0
    /** Half-angle of a downstream scent plume (tan) and its longest reach, cm [L]. */
    @Ev(Evidence.DESIGN_ESTIMATE, "realism_gaps X1; plume spread [L]") const val PLUME_SPREAD = 0.25
    const val PLUME_MAX_CM = 90.0
    /** Scent needs a few seconds to leach out of a fresh particle (s) [L]. */
    const val SCENT_LAG_S = 3.0
    /** Threat radius factor for invertebrates at night (the old 0.6 rule, kept). */
    const val INVERT_NIGHT_THREAT = 0.6

    /** 0..1 of full sight at light level [light] for an animal whose darkness floor is [dark]. */
    fun lightFactor(light: Double, dark: Double): Double = dark + (1 - dark) * smoothstep(DIM_LIGHT, FULL_LIGHT, light)

    /** How far [o] sees now, cm. */
    fun visionRange(c: Ctx, o: Organism = c.o): Double {
        val s = o.species.sensesOrDefault
        return s.visionCm * lightFactor(c.p.light, s.darkVision)
    }

    /** Lateral-line reach, cm (≥ 2 cm for any fish that has one). */
    fun lateralRange(o: Organism): Double {
        val bl = o.species.sensesOrDefault.lateralBL
        return if (bl <= 0) 0.0 else max(2.0, bl * o.lengthCm)
    }

    /**
     * Scent strength 0..1 of particle [f] at [at]: 1 inside the diffusion core, falling to 0 at the edge of the
     * downstream plume. 0 before [SCENT_LAG_S] and for an animal that ignores smell.
     */
    fun scent(c: Ctx, f: FoodParticle, at: Vec3): Double {
        val age = c.t - f.spawnedAt - SCENT_LAG_S
        if (age <= 0) return 0.0
        val d = at - f.pos
        val dist = d.length()
        val diffuse = min(SCENT_DIFFUSE_MAX_CM, sqrt(4 * SCENT_D * age))
        if (dist <= diffuse) return 1.0
        val u = c.sim.flow.velocityFast(f.pos)
        val speed = u.length()
        if (speed < 0.3) return 0.0
        val dir = u / speed
        val along = d dot dir
        if (along <= 0 || along > min(PLUME_MAX_CM, speed * age)) return 0.0
        val lateral = (d - dir * along).length()
        val width = diffuse + PLUME_SPREAD * along
        if (lateral > width) return 0.0
        return (1 - lateral / width) * (1 - 0.5 * along / PLUME_MAX_CM)
    }

    /**
     * Does [c]'s animal sense food particle [f] through any channel? Ungated species (invertebrates by default)
     * always do, which keeps the old radius search for them.
     */
    fun sensesFood(c: Ctx, f: FoodParticle): Boolean {
        val s = c.o.species.sensesOrDefault
        if (!s.gated) return true
        val d = c.o.pos.distanceTo(f.pos)
        if (d <= visionRange(c)) return true
        if (d <= lateralRange(c.o)) return true
        return s.chem > 0 && scent(c, f, c.o.pos) * s.chem >= SCENT_THRESHOLD
    }

    /** Minimum weighted scent that leads an animal to food it cannot see [L]. */
    const val SCENT_THRESHOLD = 0.15

    /**
     * Multiplier on a predator's threat radius for [me] (X6): invertebrates keep the old ×0.6 at night; a gated fish
     * notices a predator at its sight range in the dark, but never closer than its lateral-line reach.
     */
    fun threatScale(c: Ctx, me: Organism, baseRadius: Double): Double {
        if (!me.isFish) return if (c.p.night) INVERT_NIGHT_THREAT else 1.0
        val s = me.species.sensesOrDefault
        if (!s.gated || baseRadius <= 0) return 1.0
        val k = lightFactor(c.p.light, s.darkVision)
        if (k >= 1.0) return 1.0
        val r = max(baseRadius * k, min(baseRadius, lateralRange(me)))
        return r / baseRadius
    }
}
