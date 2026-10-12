package com.reeflive.core.params

import com.reeflive.core.units.ParamUnit
import kotlinx.serialization.Serializable

@Serializable
data class Range(val lo: Double, val hi: Double) {
    operator fun contains(v: Double): Boolean = v >= lo - EPS && v <= hi + EPS
    val span: Double get() = hi - lo
    val mid: Double get() = (lo + hi) / 2.0

    companion object {
        const val EPS = 1e-9
    }
}

/**
 * One water parameter with its targets (ARCHITECTURE §4.2). Values are in [baseUnit].
 * [higherIsWorse]: true for pollutants (NO3, PO4, NH3…), false when only low is bad (DO), null when both sides matter.
 */
data class ParameterDef(
    val id: String,
    val name: String,
    val glyph: String,
    val baseUnit: ParamUnit,
    val altUnits: List<ParamUnit>,
    val precision: Int,
    val ideal: Range,
    val acceptable: Range,
    val dangerLow: Double?,
    val dangerHigh: Double?,
    val maxChangePerDay: Double?,
    val cadenceDays: Int?,
    val favorite: Boolean,
    val order: Int,
    val higherIsWorse: Boolean?,
) {
    val units: List<ParamUnit> get() = listOf(baseUnit) + altUnits
    val shortName: String get() = ParameterTable.shortName(id)
}
