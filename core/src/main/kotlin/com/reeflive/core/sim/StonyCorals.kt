package com.reeflive.core.sim

import com.reeflive.core.geom.Vec3
import com.reeflive.core.geom.smoothstep
import kotlin.math.max
import kotlin.math.min

/**
 * Stony corals (scleractinians: Euphyllia, the meaty LPS, later SPS): night tentacles, sweeper tentacles and their
 * stings (docs/research/stony_corals.md).
 *
 * Large-polyp stony corals put out a second set of tentacles after dark: Euphyllia (hammer, torch, frogspawn) keep
 * their day tentacles half out and send long, thin sweeper tentacles into the neighbours; duncans, acans and the
 * other meaty LPS open short feeding tentacles to catch plankton. [Coral.tentacles] is how far those night tentacles
 * are out (0..1, renderer `uPolypDrive`), and [Coral.sweepers] how developed the sweepers are: sweepers are grown in
 * response to a competitor within reach (Hidaka & Yamazato 1984 on Galaxea; hobby: "strong current and close
 * neighbours produce more sweepers") over days, and are reabsorbed when nothing is left to fight.
 */
object StonyCorals {
    /**
     * Sting reach of one species: [dayCm] is how far its ordinary tentacles touch past the colony edge, [sweeperCm]
     * how far fully grown sweepers reach at night, [strength] the sting pressure at contact (0..1, × size and tissue).
     */
    data class Sting(val dayCm: Double, val sweeperCm: Double, val strength: Double)

    @Ev(Evidence.DESIGN_ESTIMATE, "stony_corals.md §2 (retail spacing 6-10 in; sweepers 'about 6 in beyond the coral', torch up to ~25 cm)")
    fun sting(sp: SimSpecies): Sting? {
        val genus = sp.id.lowercase().substringBefore('_')
        return when {
            sp.id.startsWith("euphyllia_glabrescens") -> Sting(5.0, 20.0, 0.45)     // torch: the longest, hardest stinger
            sp.id.startsWith("fimbriaphyllia_divisa") -> Sting(4.0, 15.0, 0.35)     // frogspawn
            sp.id.startsWith("fimbriaphyllia_ancora") -> Sting(3.5, 12.0, 0.35)     // hammer
            genus.startsWith("euphyllia") || genus.startsWith("fimbriaphyllia") -> Sting(4.0, 15.0, 0.35)
            genus.startsWith("duncanopsammia") -> Sting(1.5, 5.0, 0.12)              // short night sweepers, mild
            sp.coralKind == CoralKind.LPS -> Sting(1.5, 6.0, 0.2)
            else -> null
        }
    }

    /** Meaty foods large-polyp corals catch and eat (target-fed mysis, brine shrimp, krill bits; stony_corals.md). */
    val MEATY: Set<FoodKind> = setOf(FoodKind.MYSIS, FoodKind.BRINE, FoodKind.KRILL)

    /** Corals with a second set of tentacles after dark (sweepers or feeding tentacles). */
    fun hasNightTentacles(k: CoralKind): Boolean = k == CoralKind.EUPHYLLIA || k == CoralKind.LPS

    /** Days for a full set of sweepers to grow with a competitor in reach, and to be reabsorbed without one (est.). */
    @Ev(Evidence.DESIGN_ESTIMATE, "stony_corals.md §2: sweepers develop over days to weeks after contact")
    const val SWEEPER_GROW_DAYS = 6.0
    const val SWEEPER_LOSE_DAYS = 20.0

    /** Share of the sweepers a Euphyllia puts out at night even with nobody near (they are a regular night sight). */
    const val SWEEPER_BASE = 0.35

    /**
     * Night tentacle target for [c]: out after dark (and a little in dim dusk light), held in by stress, a touch and
     * melting tissue; Euphyllia sweepers by how developed they are.
     */
    fun tentacleTarget(c: Coral, minutesSinceOn: Double?, minutesUntilOff: Double?): Double {
        if (!hasNightTentacles(c.kind) || c.melting) return 0.0
        val day = if (minutesSinceOn == null || minutesUntilOff == null) 0.0
            else smoothstep(30.0, 90.0, minutesSinceOn) * smoothstep(0.0, 45.0, minutesUntilOff)
        val dark = 1 - day
        val calm = (1 - 0.8 * c.stress) * (1 - c.disturb).coerceIn(0.0, 1.0)
        val grown = if (c.kind == CoralKind.EUPHYLLIA) SWEEPER_BASE + (1 - SWEEPER_BASE) * c.sweepers else 1.0
        return (dark * calm * grown).coerceIn(0.0, 1.0)
    }

    /** Eases [Coral.tentacles] toward its target (they take ~half an hour to stretch out, seconds to snap in). */
    fun stepTentacles(c: Coral, minutesSinceOn: Double?, minutesUntilOff: Double?, dtMin: Double) {
        if (!hasNightTentacles(c.kind)) return
        val t = tentacleTarget(c, minutesSinceOn, minutesUntilOff)
        val tau = if (t < c.tentacles && c.disturb > 0.3) 1.0 else 30.0
        c.tentacles += (t - c.tentacles) * (dtMin / tau).coerceAtMost(1.0)
    }

    /**
     * Grows sweepers on [c] while another species sits within its sweeper reach, and reabsorbs them when none does.
     * [dtMin] is biology time (My tank's game clock).
     */
    fun developSweepers(c: Coral, corals: List<Coral>, dtMin: Double) {
        if (c.kind != CoralKind.EUPHYLLIA) return
        val s = sting(c.species) ?: return
        var rival = false
        for (o in corals) {
            if (o === c || o.tissue <= CoralHealth.DEAD_TISSUE || o.kind == CoralKind.MACRO || tolerates(c, o)) continue
            if (gap(c, o) < s.sweeperCm) { rival = true; break }
        }
        val days = dtMin / 1440.0
        c.sweepers = if (rival && c.tissue > 0.5) min(1.0, c.sweepers + days / SWEEPER_GROW_DAYS * (1 - c.stress))
            else max(0.0, c.sweepers - days / SWEEPER_LOSE_DAYS)
    }

    /** Edge-to-edge distance between two colonies, cm. */
    fun gap(a: Coral, b: Coral): Double = a.pos.distanceTo(b.pos) - CoralGrowth.extent(a) - CoralGrowth.extent(b)

    /** How far [c]'s stings reach past its edge right now: day tentacles, plus the sweepers that are out. */
    fun reachNow(c: Coral): Double {
        val s = sting(c.species) ?: return 0.0
        val night = if (c.kind == CoralKind.EUPHYLLIA) c.tentacles else c.tentacles * 0.6
        return s.dayCm + (s.sweeperCm - s.dayCm) * night.coerceIn(0.0, 1.0)
    }

    /** Longest reach [k] can have, for keeping new buds out of range ([CoralHealth.place]). */
    fun maxReach(sp: SimSpecies): Double = sting(sp)?.sweeperCm ?: 0.0

    /**
     * Euphyllia of different species mostly live side by side (hammer, frogspawn and torch are kept touching in the
     * hobby); other stony corals sting anything not of their own species. Every other kind keeps its old rule: one
     * kind never harms its own kind.
     */
    fun tolerates(a: Coral, b: Coral): Boolean = when {
        a.kind != b.kind -> false
        a.kind == CoralKind.LPS -> a.species.id == b.species.id
        else -> true
    }

    /** Sting pressure from [aggressor] on [victim] whose edges are [d] cm apart. */
    fun pressure(aggressor: Coral, victim: Coral, d: Double): Double {
        val s = sting(aggressor.species) ?: return 0.0
        if (victim.kind == CoralKind.MACRO) return 0.0
        val reach = reachNow(aggressor)
        if (d >= reach) return 0.0
        val vigour = aggressor.tissue * min(aggressor.size, 2.0)
        return s.strength * (1 - (d / reach).coerceAtLeast(0.0)).coerceIn(0.0, 1.0) * vigour
    }

    /**
     * Where a coral with no home anchor sits ([Simulation.addFromSeed], [Stocking]): corals that are not in Tee's tank
     * have no anchor on its rock, so their GLBs (tools/blender/stony_corals.py) are built with the colony base here and
     * every colony is drawn as that model moved by its offset.
     */
    val STUDIO: Vec3 = Vec3(60.0, 10.0, 20.0)
}
