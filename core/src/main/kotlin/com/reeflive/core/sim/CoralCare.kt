package com.reeflive.core.sim

import kotlin.math.floor
import kotlin.math.min

/**
 * The one-tap "Care for corals and inverts" button in Tank Health (My tank): what a careful reef keeper gives the
 * corals and invertebrates on a care day (docs/research/coral_health.md §4). Doses magnesium, calcium and alkalinity
 * back to the soft-coral targets inside a day's safe swing ([DoseCheck.DAILY_MAX], never into a mix that would crust),
 * tops iodine up for corals that shed and for shrimp and crabs that moult, sprays coral food when the corals have not
 * eaten lately, and gives the feather duster phytoplankton and each anemone a piece of meaty food once a day.
 *
 * Pure: [plan] lists what the tank needs now (the screens show it with its sand dollar [Plan.price]); the sim runs the
 * same plan from its own state when the button's [Command.ReefCare] arrives, so the preview and the deed agree.
 */
object CoralCare {
    enum class Kind(val label: String, val price: Int) {
        MAGNESIUM("magnesium", 3),
        CALCIUM("calcium", 3),
        ALKALINITY("alkalinity buffer", 3),
        IODINE("iodine", 2),
        CORAL_FOOD("coral food", 3),
        PHYTO("phytoplankton for the feather duster", 2),
        ANEMONE_FOOD("a bit of mysis for the anemone", 2),
    }

    /** One thing it gives: a dose [amount] (ppm, dKH) or a feeding. */
    data class Item(val kind: Kind, val amount: Double = 0.0)

    data class Plan(val items: List<Item>) {
        /** Sand dollars the supplies cost (docs/ECONOMY.md: about 1 per real US dollar of supplies). */
        val price: Int get() = items.sumOf { it.kind.price }
        val isEmpty: Boolean get() = items.isEmpty()
        /** "magnesium, calcium and coral food". */
        val words: String get() = items.map { it.kind.label }.let { l ->
            if (l.size <= 1) l.joinToString() else l.dropLast(1).joinToString(", ") + " and " + l.last()
        }
    }

    /**
     * Soft-coral reef targets (01 §2.1: alk 7-11, Ca 380-460, Mg 1250-1450; iodine ~0.06 ppm natural sea water) and
     * how far under them before a dose is worth it.
     */
    @Ev(Evidence.SOURCED, "01 §2.1; coral_health §4")
    val TARGETS = mapOf("mg" to 1320.0, "ca" to 420.0, "alk" to 8.5, "i" to 0.06)
    val DOSE_BELOW = mapOf("mg" to 1260.0, "ca" to 400.0, "alk" to 8.0, "i" to 0.045)
    private val ROUND = mapOf("mg" to 10.0, "ca" to 5.0, "alk" to 0.1, "i" to 0.005)
    /** Iodine dosed at most this much a day (ppm): strong iodine burns soft-coral tissue (est.). */
    const val IODINE_DAILY_MAX = 0.03

    /** Corals count as hungry when their mean recent catch ([Coral.fed]) is under this. */
    const val CORALS_FED = 0.4
    /** Filter feeders and anemones get their food at most this often (sim hours). */
    const val FEED_EVERY_H = 20.0

    /**
     * What the tank needs. [w] the water, [coralFed] mean recent catch of the living corals (NaN: no corals),
     * [anemones] and [filterFeeders] living counts, [crustaceans] shrimp and crabs (they need iodine to moult),
     * [hoursSinceFeed] since the last care feeding, [dosed24h] how much of each param went in during the last day.
     */
    fun plan(
        w: WaterState, coralFed: Double, anemones: Int, filterFeeders: Int, crustaceans: Int,
        hoursSinceFeed: Double, dosed24h: (String) -> Double = { 0.0 },
    ): Plan {
        val out = ArrayList<Item>()
        val p = w.copy()
        // magnesium first: it keeps calcium and carbonate in solution
        dose(out, p, "mg", Kind.MAGNESIUM, dosed24h)
        dose(out, p, "ca", Kind.CALCIUM, dosed24h)
        dose(out, p, "alk", Kind.ALKALINITY, dosed24h)
        if (!coralFed.isNaN() || crustaceans > 0) dose(out, p, "i", Kind.IODINE, dosed24h)
        if (!coralFed.isNaN() && coralFed < CORALS_FED) out += Item(Kind.CORAL_FOOD)
        if (hoursSinceFeed >= FEED_EVERY_H) {
            if (filterFeeders > 0) out += Item(Kind.PHYTO)
            if (anemones > 0) out += Item(Kind.ANEMONE_FOOD)
        }
        return Plan(out)
    }

    private fun dose(out: MutableList<Item>, p: WaterState, id: String, kind: Kind, dosed24h: (String) -> Double) {
        val now = p.get(id) ?: return
        if (now >= DOSE_BELOW.getValue(id)) return
        val cap = (if (id == "i") IODINE_DAILY_MAX else DoseCheck.DAILY_MAX[id] ?: return) - dosed24h(id)
        val r = ROUND.getValue(id)
        val delta = floor(min(TARGETS.getValue(id) - now, cap) / r) * r
        if (delta < r - 1e-9) return
        if (id != "i" && DoseCheck.check(id, delta, p) == DoseCheck.Verdict.PRECIPITATION) return
        out += Item(kind, delta)
        when (id) { "alk" -> p.alk += delta; "ca" -> p.ca += delta; "mg" -> p.mg += delta; "i" -> p.i += delta }
    }

    /** The plan from a snapshot (the button's preview and price). */
    fun plan(s: SimSnapshot): Plan {
        var fed = 0.0; var n = 0
        for (c in s.corals) if (c.tissue > 0.1 && CoralKind.resolve(c.speciesId).let { it != CoralKind.MACRO && it != CoralKind.AIPTASIA }) { fed += c.fed; n++ }
        var anem = 0; var duster = 0; var crust = 0
        for (st in s.stats) if (st.alive) {
            val b = BrainKind.byId(st.speciesId) ?: continue
            when {
                b == BrainKind.ANEMONE -> anem++
                b == BrainKind.FEATHER_DUSTER -> duster++
                isCrustacean(b) -> crust++
            }
        }
        return plan(s.water, if (n == 0) Double.NaN else fed / n, anem, duster, crust, (s.simTimeSec - s.reefCareAt) / 3600)
    }

    fun isCrustacean(b: BrainKind) = b == BrainKind.BANDED_SHRIMP || b == BrainKind.CLEANER_SHRIMP || b == BrainKind.HERMIT || b == BrainKind.SALLY_CRAB
}
