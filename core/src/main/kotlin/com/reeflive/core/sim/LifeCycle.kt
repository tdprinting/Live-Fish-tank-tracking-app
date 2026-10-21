package com.reeflive.core.sim

import com.reeflive.core.geom.Rng
import kotlin.math.exp
import kotlin.math.pow

/**
 * Lifespan and ageing (coverage_gaps B-8; research 02 / 04 "1F Lifespan").
 *
 * Every organism carries a chronological age ([Organism.ageSec]) that only advances in sim time (the chem tick runs in
 * time-lapse and abstract mode too). Chronic stress and an undersized tank add [Organism.agingDebtSec]: the animal is
 * physiologically older than its calendar age (research 02 §10 item 1: palette tang and snowflake moray in 60 gal are
 * "chronically stressed, with growth stunting and a lifespan penalty").
 *
 * Near the end of the species lifespan senescence rises: immunity is dragged down (more disease) and a small, rising
 * hazard of natural death applies, cause "old age".
 */
object LifeCycle {
    const val SEC_PER_YEAR = 365.0 * 86400.0
    /** Physiological age (fraction of the lifespan) where senescence begins. */
    const val SENESCENCE_START = 0.75
    /** Span of the fraction over which senescence goes from 0 to 1. */
    const val SENESCENCE_SPAN = 0.35
    /** Annual death hazard at [HAZARD_START]; grows e-fold every 1/[HAZARD_GROWTH] of a lifespan. (tune) */
    const val HAZARD_START = 0.85
    const val BASE_HAZARD_PER_YEAR = 0.05
    const val HAZARD_GROWTH = 6.0
    /** Nobody outlives twice the species lifespan. */
    const val MAX_FRACTION = 2.0
    /** Immunity drag per minute at full senescence (equilibrium immunity ≈ 1 - 0.2 × senescence). (tune) */
    const val IMMUNITY_DRAG = 0.0004
    /** Extra ageing rate per unit of chronic stress above [CHRONIC_FREE]. */
    const val CHRONIC_AGING = 1.5
    const val CHRONIC_FREE = 0.15
    const val CRAMPED_AGING = 0.5

    /** Tank volume (L) below which the species is chronically cramped (research 02 §10 item 1): tang 450 L, eel 130 gal. */
    fun minVolumeL(k: BrainKind): Double = when (k) { BrainKind.TANG -> 450.0; BrainKind.EEL -> 490.0; else -> 0.0 }

    /**
     * The same for one species, where the species differs from its kind (research 42: yellow tang ~100 gal ≈ 380 L, purple
     * tang ~125 gal ≈ 475 L; the blue tang keeps the kind's 450 L).
     */
    fun minVolumeL(speciesId: String, k: BrainKind): Double = SPECIES_MIN_L[speciesId] ?: minVolumeL(k)
    fun minVolumeL(o: Organism): Double = minVolumeL(o.species.id, o.brainKind)

    private val SPECIES_MIN_L = mapOf("zebrasoma_flavescens" to 380.0, "zebrasoma_xanthurum" to 475.0,
        // research 60: gem tang 125-240 gal recommended, taken as 150 gal; wrought iron butterfly, a 15 cm rover
        "zebrasoma_gemmatum" to 570.0, "chaetodon_daedalma" to 570.0)

    fun lifespanSec(s: SimSpecies) = s.lifespanYears.coerceAtLeast(0.1) * SEC_PER_YEAR

    /**
     * Starting age for an animal. An explicit [seedYears] wins; otherwise an animal bought at its listed size is assumed
     * to be somewhere between 10 % and 50 % through its life, scaling with how grown it is (a 7.5 cm tang is a juvenile,
     * a full-size clownfish is mid-life), with ±15 % individual spread from a stream derived from the animal id.
     */
    fun initialAgeSec(o: Organism, seedYears: Double? = null): Double {
        if (seedYears != null) return seedYears.coerceAtLeast(0.0) * SEC_PER_YEAR
        val grown = (o.lengthCm / o.species.maxLengthCm.coerceAtLeast(0.5)).coerceIn(0.0, 1.0)
        val r = Rng(o.id.hashCode().toLong() * 6364136223846793005L + 0x4147L)
        return lifespanSec(o.species) * (0.1 + 0.4 * grown) * (0.85 + 0.3 * r.nextDouble())
    }

    /** Extra ageing rate on top of real time: 0 = none; chronic stress and cramped tank. */
    fun extraAgingRate(o: Organism, volumeL: Double): Double {
        var extra = CHRONIC_AGING * (o.chronic - CHRONIC_FREE).coerceAtLeast(0.0)
        val minV = minVolumeL(o)
        if (minV > 0 && volumeL < minV) {
            val size = (o.lengthCm / (0.5 * o.species.maxLengthCm.coerceAtLeast(1.0))).coerceIn(0.3, 1.0)
            extra += CRAMPED_AGING * (1 - volumeL / minV) * size
        }
        return extra
    }

    /** Physiological age as a fraction of the species lifespan. */
    fun lifeFraction(o: Organism): Double = (o.ageSec + o.agingDebtSec) / lifespanSec(o.species)

    /** 0 before [SENESCENCE_START], rising smoothly to 1. */
    fun senescence(o: Organism): Double = ((lifeFraction(o) - SENESCENCE_START) / SENESCENCE_SPAN).coerceIn(0.0, 1.0).pow(2)

    /** Multiplier on new-infection pressure (HealthModel). */
    fun susceptibility(o: Organism): Double = 1.0 + senescence(o)

    /** Annual natural-death hazard at a physiological age fraction. */
    fun hazardPerYear(fraction: Double): Double =
        if (fraction < HAZARD_START) 0.0 else BASE_HAZARD_PER_YEAR * exp(HAZARD_GROWTH * (fraction - HAZARD_START))

    /** Advances age by [dtSec] of sim time; applies senescence and may end the animal's life ("old age"). */
    fun step(o: Organism, dtSec: Double, volumeL: Double, simTimeSec: Double, events: MutableList<SimEvent>) {
        if (!o.alive) return
        o.ageSec += dtSec
        o.agingDebtSec += dtSec * extraAgingRate(o, volumeL)
        val frac = lifeFraction(o)
        val sen = senescence(o)
        if (sen > 0) o.immunity = (o.immunity - IMMUNITY_DRAG * sen * dtSec / 60.0).coerceAtLeast(0.0)
        if (frac >= MAX_FRACTION) { HealthModel.kill(o, OLD_AGE, simTimeSec, events); return }
        val h = hazardPerYear(frac)
        if (h > 0 && o.rng.chance(h * dtSec / SEC_PER_YEAR)) HealthModel.kill(o, OLD_AGE, simTimeSec, events)
    }

    const val OLD_AGE = "old age"
}
