package com.reeflive.core.units

import com.reeflive.core.chem.Oxygen

/**
 * Unit conversions with the factors of 01_reef_care §1.2.
 *
 * Alkalinity uses dKH as the hub: dKH × 0.357 = meq/L and dKH × 17.9 = ppm CaCO3, so
 * 8 dKH → 2.856 meq/L → 143.2 ppm (meq/L → ppm goes through dKH, i.e. ×50.14).
 * Salinity SG ↔ ppt uses the 25 °C lookup table with linear interpolation (and linear
 * extrapolation beyond the ends); ppt ↔ mS/cm uses 53/35 (53 mS/cm at 35 ppt).
 */
object UnitConverter {
    const val DKH_TO_MEQ = 0.357
    const val DKH_TO_PPM_CACO3 = 17.9
    const val NO3_TO_NO3N = 0.226
    const val PO4_TO_PO4P = 0.326
    const val PPT_TO_MS_CM = 53.0 / 35.0

    /** SG at 25 °C → ppt (ARCHITECTURE §4.1). */
    private val SG_TABLE = doubleArrayOf(1.020, 1.021, 1.022, 1.023, 1.024, 1.025, 1.026, 1.027, 1.028, 1.029, 1.030)
    private val PPT_TABLE = doubleArrayOf(26.7, 28.0, 29.3, 30.6, 31.9, 33.3, 34.6, 35.9, 37.2, 38.5, 39.8)

    fun sgToPpt(sg: Double): Double = interp(sg, SG_TABLE, PPT_TABLE)
    fun pptToSg(ppt: Double): Double = interp(ppt, PPT_TABLE, SG_TABLE)
    fun fToC(f: Double): Double = (f - 32.0) * 5.0 / 9.0
    fun cToF(c: Double): Double = c * 9.0 / 5.0 + 32.0

    fun canConvert(from: ParamUnit, to: ParamUnit): Boolean =
        runCatching { convert(1.0, from, to) }.isSuccess

    /**
     * Converts [value] between compatible units. [tempC] is used only for DO % saturation.
     * @throws IllegalArgumentException for incompatible units.
     */
    fun convert(value: Double, from: ParamUnit, to: ParamUnit, tempC: Double = 25.0): Double {
        if (from == to) return value
        return when {
            from.family == ParamUnit.Family.TEMP && to.family == ParamUnit.Family.TEMP ->
                if (from == ParamUnit.F) fToC(value) else cToF(value)
            from.family == ParamUnit.Family.SAL && to.family == ParamUnit.Family.SAL ->
                fromPpt(toPpt(value, from), to)
            from.family == ParamUnit.Family.ALK && to.family == ParamUnit.Family.ALK ->
                fromDkh(toDkh(value, from), to)
            from.family == ParamUnit.Family.DO && to.family == ParamUnit.Family.DO -> {
                val sat = Oxygen.saturation(tempC, 35.0)
                if (from == ParamUnit.MG_L) value / sat * 100.0 else value / 100.0 * sat
            }
            isConc(from) && isConc(to) -> fromPpm(toPpm(value, from), to)
            else -> throw IllegalArgumentException("Cannot convert ${from.name} to ${to.name}")
        }
    }

    private fun isConc(u: ParamUnit) = u == ParamUnit.PPM || u == ParamUnit.PPB ||
        u == ParamUnit.PPM_NO3_N || u == ParamUnit.PPM_PO4_P

    private fun toPpm(v: Double, u: ParamUnit): Double = when (u) {
        ParamUnit.PPM -> v
        ParamUnit.PPB -> v / 1000.0
        ParamUnit.PPM_NO3_N -> v / NO3_TO_NO3N
        ParamUnit.PPM_PO4_P -> v / PO4_TO_PO4P
        else -> throw IllegalArgumentException(u.name)
    }

    private fun fromPpm(v: Double, u: ParamUnit): Double = when (u) {
        ParamUnit.PPM -> v
        ParamUnit.PPB -> v * 1000.0
        ParamUnit.PPM_NO3_N -> v * NO3_TO_NO3N
        ParamUnit.PPM_PO4_P -> v * PO4_TO_PO4P
        else -> throw IllegalArgumentException(u.name)
    }

    private fun toPpt(v: Double, u: ParamUnit): Double = when (u) {
        ParamUnit.SG -> sgToPpt(v)
        ParamUnit.PPT -> v
        ParamUnit.MS_CM -> v / PPT_TO_MS_CM
        else -> throw IllegalArgumentException(u.name)
    }

    private fun fromPpt(v: Double, u: ParamUnit): Double = when (u) {
        ParamUnit.SG -> pptToSg(v)
        ParamUnit.PPT -> v
        ParamUnit.MS_CM -> v * PPT_TO_MS_CM
        else -> throw IllegalArgumentException(u.name)
    }

    private fun toDkh(v: Double, u: ParamUnit): Double = when (u) {
        ParamUnit.DKH -> v
        ParamUnit.MEQ_L -> v / DKH_TO_MEQ
        ParamUnit.PPM_CACO3 -> v / DKH_TO_PPM_CACO3
        else -> throw IllegalArgumentException(u.name)
    }

    private fun fromDkh(v: Double, u: ParamUnit): Double = when (u) {
        ParamUnit.DKH -> v
        ParamUnit.MEQ_L -> v * DKH_TO_MEQ
        ParamUnit.PPM_CACO3 -> v * DKH_TO_PPM_CACO3
        else -> throw IllegalArgumentException(u.name)
    }

    /** Piecewise-linear interpolation over a strictly increasing [xs], extrapolating linearly. */
    internal fun interp(x: Double, xs: DoubleArray, ys: DoubleArray): Double {
        val n = xs.size
        val i = when {
            x <= xs[0] -> 0
            x >= xs[n - 1] -> n - 2
            else -> {
                var k = 0
                while (k < n - 2 && x > xs[k + 1]) k++
                k
            }
        }
        val t = (x - xs[i]) / (xs[i + 1] - xs[i])
        return ys[i] + t * (ys[i + 1] - ys[i])
    }
}
