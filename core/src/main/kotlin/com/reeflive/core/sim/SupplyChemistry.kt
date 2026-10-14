package com.reeflive.core.sim

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round
import kotlin.random.Random

/*
 * The chemistry behind the supply shelf's care props (docs/research/shelf_audit.md): what a hobby test kit actually
 * shows for the sim's water, whether a dose is safe, and what a water change or a trim will do before it is done.
 * Pure functions over [WaterState]; the commands themselves stay Command.Dose / WaterChange / Maintain.
 * No String.format here: core also compiles to JS for the web sandbox.
 */

/**
 * The shelf's test kit (API Reef Master + Salifert KH). A hobby kit does not show the sim's exact numbers:
 * nitrate and phosphate are read off a colour card (the reader picks one of the two nearest swatches, the nearer one
 * more often), calcium is counted in drops of titrant (20 ppm a drop, end point ±1 drop) and alkalinity is read off
 * the Salifert syringe (0.2 dKH steps, ±0.2). So a phosphate of 0.04 reads "0" on the card, which is real: hobby
 * phosphate cards cannot see the low range a reef runs at. Per-kit resolutions are the makers' instructions; the
 * read errors are design estimates.
 */
object TestKit {
    /** One reading: [id] the SandboxParams row it belongs to, [label] short, [value] what the kit shows, [text] formatted. */
    class Reading(val id: String, val label: String, val value: Double, val text: String, val unit: String, val method: String)

    /** API nitrate card (ppm NO3). */
    val NO3_CARD = doubleArrayOf(0.0, 5.0, 10.0, 20.0, 40.0, 80.0, 160.0)
    /** API phosphate card (ppm PO4). */
    val PO4_CARD = doubleArrayOf(0.0, 0.25, 0.5, 1.0, 2.0, 5.0, 10.0)
    /** API calcium: one drop of reagent #2 per 20 ppm on a 5 mL sample. */
    const val CA_PER_DROP = 20.0
    /** Salifert KH syringe resolution, dKH (0.01 mL of titrant ≈ 0.1 meq/L ≈ 0.2-0.3 dKH). */
    const val KH_STEP = 0.2

    fun read(w: WaterState, rnd: Random): List<Reading> = listOf(
        stepRead(w.alk, KH_STEP, rnd).let { v -> Reading("alk", "KH", v, kitFmt(v, 1), "dKH", "Salifert KH syringe") },
        dropRead(w.ca, rnd).let { v -> Reading("ca", "Ca", v, kitFmt(v, 0), "ppm", "API drops, 20 ppm each") },
        cardRead(w.no3, NO3_CARD, rnd).let { v -> Reading("no3", "NO3", v, kitFmt(v, 0), "ppm", "API colour card") },
        cardRead(w.po4, PO4_CARD, rnd).let { v -> Reading("po4", "PO4", v, kitFmt(v, 2), "ppm", "API colour card") },
    )

    /**
     * A colour card: the swatch below or above [v], the upper one with probability equal to how far [v] has moved toward
     * it (a reader matching colours by eye). Off the top of the card reads the top swatch.
     */
    fun cardRead(v: Double, card: DoubleArray, rnd: Random): Double {
        if (v <= card.first()) return card.first()
        if (v >= card.last()) return card.last()
        var i = 0
        while (v > card[i + 1]) i++
        val lo = card[i]; val hi = card[i + 1]
        val t = (v - lo) / (hi - lo)
        return if (rnd.nextDouble() < t) hi else lo
    }

    /** Calcium titration: drops counted to the colour change, the end point off by up to one drop. */
    fun dropRead(v: Double, rnd: Random): Double {
        val drops = round(v / CA_PER_DROP + (rnd.nextDouble() - 0.5) * 1.6)
        return max(0.0, drops) * CA_PER_DROP
    }

    /** A syringe read to [step], off by up to one step. */
    fun stepRead(v: Double, step: Double, rnd: Random): Double {
        val n = round(v / step + (rnd.nextDouble() - 0.5) * 1.6)
        return max(0.0, n * step)
    }
}

/**
 * Is a dose from the shelf safe (research 01 §2.1, 05 §9-10; the usual reef-keeping rules of thumb)? Alkalinity
 * should move no more than ~1 dKH a day (corals react to the swing: CoralSim's alk shock), calcium ~25 ppm and
 * magnesium ~100 ppm a day; high Ca × alk with low Mg falls out as white CaCO3 crust ([WaterSim.precipitationDkhPerHour]).
 */
object DoseCheck {
    enum class Verdict { OK, FAST_SWING, TOO_HIGH, PRECIPITATION }

    /** Largest comfortable change per day: alkalinity dKH, calcium ppm, magnesium ppm. */
    val DAILY_MAX = mapOf("alk" to 1.0, "ca" to 25.0, "mg" to 100.0)
    /** Above these the dose itself is the problem (01 §2.1 targets: alk 7-11, Ca 380-460, Mg 1250-1450). */
    val HIGH = mapOf("alk" to 11.0, "ca" to 470.0, "mg" to 1500.0)

    /**
     * The verdict for dosing [delta] of [paramId] into [w] when [dosedLast24h] of it already went in today. The water
     * after the dose (and anything still dripping in) is checked for precipitation first, the daily swing next, then
     * the level.
     */
    fun check(paramId: String, delta: Double, w: WaterState, dosedLast24h: Double = 0.0): Verdict {
        val id = paramId.lowercase()
        val max = DAILY_MAX[id] ?: return Verdict.OK
        val after = w.copy()
        when (id) { "alk" -> after.alk += delta; "ca" -> after.ca += delta; "mg" -> after.mg += delta }
        val precipBefore = WaterSim.precipitationDkhPerHour(w)
        val precipAfter = WaterSim.precipitationDkhPerHour(after)
        if (id != "mg" && precipAfter > 0 && precipAfter >= precipBefore) return Verdict.PRECIPITATION
        if (dosedLast24h + delta > max + 1e-9) return Verdict.FAST_SWING
        val high = HIGH.getValue(id)
        if ((after.get(id) ?: 0.0) > high) return Verdict.TOO_HIGH
        return Verdict.OK
    }

    /** What went in during the last sim day, per parameter: the screen keeps one per tank session. */
    class Log {
        private val doses = ArrayList<Triple<Double, String, Double>>()

        fun add(simTimeSec: Double, paramId: String, delta: Double) {
            doses += Triple(simTimeSec, paramId.lowercase(), delta)
            // a reset sandbox can jump back in time: drop anything from "the future" and anything older than a day
            doses.removeAll { it.first > simTimeSec + 1 || simTimeSec - it.first > DAY_S }
        }

        fun lastDay(paramId: String, simTimeSec: Double): Double =
            doses.filter { it.second == paramId.lowercase() && simTimeSec - it.first in 0.0..DAY_S }.sumOf { it.third }

        fun clear() = doses.clear()

        private companion object { const val DAY_S = 86400.0 }
    }
}

/** Previews for the shelf's water changes and the algae trim (pure; the sim does the real thing). */
object SupplyPreview {
    /** The main levels right after swapping [fraction] of [w] for [mix] (the same mixing as WaterSim.waterChange). */
    fun afterWaterChange(w: WaterState, fraction: Double, mix: WaterState): WaterState {
        val f = fraction.coerceIn(0.0, 1.0)
        fun m(a: Double, b: Double) = a * (1 - f) + b * f
        return w.copy(
            tempC = m(w.tempC, mix.tempC), sg = m(w.sg, mix.sg), alk = m(w.alk, mix.alk), ca = m(w.ca, mix.ca), mg = m(w.mg, mix.mg),
            no3 = m(w.no3, mix.no3), po4 = m(w.po4, mix.po4), nh3Total = m(w.nh3Total, mix.nh3Total), i = m(w.i, mix.i),
        )
    }

    /** Is there anything to trim: the display red macro or the refugium chaeto (WaterSim.harvestMacro)? */
    fun canTrim(w: WaterState): Boolean = w.macro >= MIN_TRIM || w.chaeto >= MIN_TRIM

    /**
     * Nitrate (ppm-equivalent of the tank volume) locked in what a trim cuts and takes out for good: a third of the red
     * macro and half the chaeto, at the macroalgae's stoichiometric yield ([WaterSim.MACRO_YIELD]).
     */
    fun trimExportNo3(w: WaterState): Double = (w.macro * 0.33 + w.chaeto * 0.5) * WaterSim.MACRO_YIELD

    /** Below this biomass index there is nothing worth cutting. */
    const val MIN_TRIM = 0.05
}

/** [v] with [decimals] places (0-2), for labels on both the JVM and JS. */
fun kitFmt(v: Double, decimals: Int): String {
    val k = when (decimals) { 0 -> 1L; 1 -> 10L; else -> 100L }
    val n = round(abs(v) * k).toLong()
    val sign = if (v < 0 && n != 0L) "-" else ""
    if (decimals <= 0) return "$sign$n"
    val frac = (n % k).toString().padStart(decimals, '0')
    return "$sign${n / k}.$frac"
}
