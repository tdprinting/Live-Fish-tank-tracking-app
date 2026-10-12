package com.reeflive.core.chem

import com.reeflive.core.units.UnitConverter
import kotlin.math.ceil
import kotlin.math.pow

/** Free (un-ionised) ammonia, Emerson et al. 1975 (01_reef_care §2.4). */
object Ammonia {
    const val DANGER_FREE_PPM = 0.02

    fun pKa(tempC: Double): Double = 0.09018 + 2729.92 / (tempC + 273.15)

    /** Fraction of total ammonia present as NH3. */
    fun fraction(ph: Double, tempC: Double): Double = 1.0 / (1.0 + 10.0.pow(pKa(tempC) - ph))

    fun freeNh3(tanPpm: Double, ph: Double, tempC: Double): Double = tanPpm * fraction(ph, tempC)

    fun isDangerous(freePpm: Double): Boolean = freePpm > DANGER_FREE_PPM
}

/** Dissolved-oxygen saturation (ARCHITECTURE §4.4). */
object Oxygen {
    private val T = doubleArrayOf(20.0, 22.0, 24.0, 25.0, 26.0, 28.0, 30.0)
    private val DO35 = doubleArrayOf(7.4, 7.1, 6.8, 6.6, 6.5, 6.2, 5.9)

    /** mg/L at saturation for [tempC] and salinity [ppt]. Beyond the table: −0.2 mg/L per +1 °C. */
    fun saturation(tempC: Double, ppt: Double = 35.0): Double {
        val base = when {
            tempC > T.last() -> DO35.last() - 0.2 * (tempC - T.last())
            tempC < T.first() -> DO35.first() + 0.2 * (T.first() - tempC)
            else -> UnitConverter.interp(tempC, T, DO35)
        }
        return (base + 0.05 * (35.0 - ppt)).coerceAtLeast(0.1)
    }

    fun percentSaturation(mgL: Double, tempC: Double, ppt: Double = 35.0): Double = mgL / saturation(tempC, ppt) * 100.0
}

/** What a product raises. Alk products are per dKH, Ca/Mg per ppm. */
enum class DoseTarget(val paramId: String, val unitLabel: String) { ALK("alk", "dKH"), CA("ca", "ppm"), MG("mg", "ppm") }

/**
 * A dosing product. [gramsPerUnitPerGallon] = grams to raise 1 unit (ppm or dKH) in 1 US gal.
 * Liquid products use [effectPerMlPerGallon] instead (units raised per mL in 1 gal).
 */
data class DosingProduct(
    val id: String,
    val name: String,
    val target: DoseTarget,
    val gramsPerUnitPerGallon: Double? = null,
    val effectPerMlPerGallon: Double? = null,
) {
    val isLiquid: Boolean get() = effectPerMlPerGallon != null
}

/** Dosing math, 01_reef_care §3.3. g = ΔC × V(gal) × 3.785 / (1000 × w) for elements; 0.318 g/gal/meq for NaHCO3. */
object Dosing {
    const val L_PER_GAL = 3.785
    const val W_CACL2_ANH = 0.361
    const val W_CACL2_DIHYDRATE = 0.273
    const val W_MGCL2_HEXA = 0.120
    const val W_MGSO4_HEPTA = 0.099
    const val NAHCO3_G_PER_GAL_PER_MEQ = 0.318
    const val NA2CO3_G_PER_GAL_PER_MEQ = 0.200

    private fun elemental(w: Double) = L_PER_GAL / (1000.0 * w)

    val PRODUCTS: List<DosingProduct> = listOf(
        DosingProduct("nahco3", "Baking soda (NaHCO3)", DoseTarget.ALK, NAHCO3_G_PER_GAL_PER_MEQ * UnitConverter.DKH_TO_MEQ),
        DosingProduct("na2co3", "Soda ash (Na2CO3)", DoseTarget.ALK, NA2CO3_G_PER_GAL_PER_MEQ * UnitConverter.DKH_TO_MEQ),
        DosingProduct("cacl2", "Calcium chloride, anhydrous", DoseTarget.CA, elemental(W_CACL2_ANH)),
        DosingProduct("cacl2_2h2o", "Calcium chloride dihydrate", DoseTarget.CA, elemental(W_CACL2_DIHYDRATE)),
        DosingProduct("mgcl2_6h2o", "Magnesium chloride hexahydrate", DoseTarget.MG, elemental(W_MGCL2_HEXA)),
        DosingProduct("mgso4_7h2o", "Magnesium sulfate heptahydrate", DoseTarget.MG, elemental(W_MGSO4_HEPTA)),
    )

    fun product(id: String): DosingProduct? = PRODUCTS.firstOrNull { it.id == id }

    fun productsFor(target: DoseTarget): List<DosingProduct> = PRODUCTS.filter { it.target == target }

    /** Grams of [product] to raise by [delta] (dKH or ppm) in [volumeGal]. */
    fun grams(product: DosingProduct, delta: Double, volumeGal: Double): Double {
        val g = product.gramsPerUnitPerGallon ?: error("${product.id} is a liquid product")
        return delta * volumeGal * g
    }

    /** mL of a liquid product raising [effectPerMlPerGallon] units per mL per gallon. */
    fun ml(delta: Double, volumeGal: Double, effectPerMlPerGallon: Double): Double = delta * volumeGal / effectPerMlPerGallon

    /** Expected rise from a known amount (inverse of [grams]). */
    fun deltaFromGrams(product: DosingProduct, grams: Double, volumeGal: Double): Double =
        grams / (volumeGal * (product.gramsPerUnitPerGallon ?: error("liquid")))

    data class Plan(val totalAmount: Double, val days: Int, val perDay: Double, val delta: Double, val exceedsCap: Boolean)

    /** Splits a dose over days so each day stays within [capPerDay]. */
    fun plan(totalAmount: Double, delta: Double, capPerDay: Double?): Plan {
        if (delta <= 0.0) return Plan(0.0, 0, 0.0, delta, false)
        val days = if (capPerDay == null || capPerDay <= 0) 1 else ceil(delta / capPerDay - 1e-9).toInt().coerceAtLeast(1)
        return Plan(totalAmount, days, totalAmount / days, delta, capPerDay != null && delta > capPerDay + 1e-9)
    }
}

/** Water-change dilution: after = before(1 − f) + mix·f (01_reef_care §3.1). */
object WaterChange {
    fun after(before: Double, mix: Double, fraction: Double): Double = before * (1.0 - fraction) + mix * fraction

    fun predict(before: Map<String, Double>, mix: Map<String, Double>, fraction: Double): Map<String, Double> =
        before.mapValues { (id, v) -> mix[id]?.let { after(v, it, fraction) } ?: v }

    fun fraction(changeGallons: Double, systemGallons: Double): Double =
        if (systemGallons <= 0) 0.0 else (changeGallons / systemGallons).coerceIn(0.0, 1.0)

    /** Default fresh-mix values (01_reef_care §3.2): NO3/PO4 zero. */
    val DEFAULT_MIX: Map<String, Double> = linkedMapOf("alk" to 7.5, "ca" to 410.0, "mg" to 1300.0, "no3" to 0.0, "po4" to 0.0)
}

/** Salt for a target SG; linear in (SG − 1.000)/0.025 with 136 g/gal at 1.025 (≈ ½ cup). */
object SaltMix {
    const val DEFAULT_G_PER_GAL_AT_1025 = 136.0
    const val GRAMS_PER_CUP = 272.0

    fun grams(targetSg: Double, gallons: Double, saltGramsPerGalAt1025: Double = DEFAULT_G_PER_GAL_AT_1025): Double =
        gallons * saltGramsPerGalAt1025 * (targetSg - 1.0) / 0.025

    fun cups(grams: Double, gramsPerCup: Double = GRAMS_PER_CUP): Double = grams / gramsPerCup
}

/** Salinity drift from evaporation without top-off: sg' = 1 + (sg − 1) × V / (V − e·d). */
object Evaporation {
    fun sgAfter(sg: Double, totalGal: Double, evapGalPerDay: Double, days: Double): Double {
        val remaining = totalGal - evapGalPerDay * days
        require(remaining > 0) { "All water evaporated" }
        return 1.0 + (sg - 1.0) * totalGal / remaining
    }

    /** First-day SG drift (SG/day). */
    fun driftPerDay(sg: Double, totalGal: Double, evapGalPerDay: Double): Double = sgAfter(sg, totalGal, evapGalPerDay, 1.0) - sg

    /** Fresh RO/DI needed to return to [targetSg] from [currentSg]. */
    fun topOffGallons(currentSg: Double, targetSg: Double, totalGal: Double): Double =
        if (currentSg <= targetSg) 0.0 else totalGal * ((currentSg - 1.0) / (targetSg - 1.0) - 1.0)
}
