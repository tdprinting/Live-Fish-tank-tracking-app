package com.reeflive.core.params

import com.reeflive.core.model.ReefType
import com.reeflive.core.units.ParamUnit

/**
 * Canonical parameter targets (ARCHITECTURE §4.2, DESIGN §4, 01_reef_care §1.1).
 * The four reef-type tables share acceptable/danger/cadence and differ only in ideal ranges.
 * User overrides are merged on top by [Targets].
 */
object ParameterTable {
    val SOFT_CORAL: List<ParameterDef> = listOf(
        ParameterDef("temp", "Temperature", "thermometer", ParamUnit.F, listOf(ParamUnit.C), 1, Range(76.0, 78.0), Range(74.0, 80.0), 72.0, 84.0, 1.0, 1, true, 0, null),
        ParameterDef("sal", "Salinity", "droplet", ParamUnit.SG, listOf(ParamUnit.PPT, ParamUnit.MS_CM), 3, Range(1.024, 1.026), Range(1.023, 1.027), 1.020, 1.029, 0.001, 7, true, 1, null),
        ParameterDef("alk", "Alkalinity", "layers", ParamUnit.DKH, listOf(ParamUnit.MEQ_L, ParamUnit.PPM_CACO3), 1, Range(8.0, 9.0), Range(7.0, 11.0), 6.0, 12.0, 1.0, 7, true, 2, null),
        ParameterDef("ca", "Calcium", "ca", ParamUnit.PPM, emptyList(), 0, Range(400.0, 440.0), Range(380.0, 460.0), 350.0, 500.0, 20.0, 7, true, 3, null),
        ParameterDef("mg", "Magnesium", "mg", ParamUnit.PPM, emptyList(), 0, Range(1250.0, 1350.0), Range(1200.0, 1400.0), 1100.0, 1500.0, 50.0, 30, true, 4, null),
        ParameterDef("ph", "pH", "flask", ParamUnit.PH, emptyList(), 2, Range(8.1, 8.4), Range(7.9, 8.5), 7.8, 8.6, 0.2, 7, true, 5, null),
        ParameterDef("no3", "Nitrate", "no3", ParamUnit.PPM, listOf(ParamUnit.PPM_NO3_N), 1, Range(3.0, 15.0), Range(2.0, 25.0), 1.0, 25.0, 5.0, 7, true, 6, true),
        ParameterDef("po4", "Phosphate", "po4", ParamUnit.PPM, listOf(ParamUnit.PPB, ParamUnit.PPM_PO4_P), 3, Range(0.02, 0.06), Range(0.01, 0.10), null, 0.15, 0.02, 7, true, 7, true),
        ParameterDef("nh3", "Ammonia (total)", "nh3", ParamUnit.PPM, emptyList(), 2, Range(0.0, 0.0), Range(0.0, 0.02), null, 0.25, null, null, false, 8, true),
        ParameterDef("no2", "Nitrite", "no2", ParamUnit.PPM, emptyList(), 2, Range(0.0, 0.0), Range(0.0, 0.05), null, 0.25, null, null, false, 9, true),
        ParameterDef("k", "Potassium", "k", ParamUnit.PPM, emptyList(), 0, Range(380.0, 420.0), Range(360.0, 440.0), 340.0, 460.0, 10.0, 30, false, 10, null),
        ParameterDef("sr", "Strontium", "sr", ParamUnit.PPM, emptyList(), 1, Range(8.0, 9.0), Range(7.0, 11.0), 6.0, null, 0.5, 90, false, 11, null),
        ParameterDef("i", "Iodine", "i", ParamUnit.PPM, emptyList(), 3, Range(0.05, 0.06), Range(0.04, 0.08), null, 0.1, null, 90, false, 12, null),
        ParameterDef("orp", "ORP", "bolt", ParamUnit.MV, emptyList(), 0, Range(325.0, 375.0), Range(300.0, 400.0), 250.0, 420.0, null, 7, false, 13, null),
        ParameterDef("do", "Dissolved O2", "bubbles", ParamUnit.MG_L, listOf(ParamUnit.PCT_SAT), 1, Range(6.5, 7.0), Range(5.5, 9.0), 5.0, null, null, null, false, 14, false),
        ParameterDef("tds", "RO/DI TDS", "filter", ParamUnit.PPM, emptyList(), 0, Range(0.0, 0.0), Range(0.0, 5.0), null, 10.0, null, null, false, 15, true),
        ParameterDef("sio2", "Silicate", "sio2", ParamUnit.PPM, emptyList(), 2, Range(0.0, 0.5), Range(0.0, 1.0), null, 1.0, null, 30, false, 16, true),
        ParameterDef("fe", "Iron", "fe", ParamUnit.PPM, emptyList(), 3, Range(0.001, 0.005), Range(0.0, 0.01), null, 0.02, null, 365, false, 17, true),
    )

    /** FOWLR ideals (01_reef_care §1.1, FOWLR column). */
    val FOWLR: List<ParameterDef> = SOFT_CORAL.withIdeals(
        "temp" to Range(76.0, 79.0), "ph" to Range(8.1, 8.3), "alk" to Range(7.0, 9.0),
        "ca" to Range(400.0, 420.0), "no3" to Range(5.0, 20.0), "po4" to Range(0.03, 0.10), "sr" to Range(8.0, 10.0),
    )

    /** LPS ideals: between the soft-coral and SPS columns (no LPS column in the research; documented choice). */
    val LPS: List<ParameterDef> = SOFT_CORAL.withIdeals(
        "alk" to Range(8.0, 9.5), "ca" to Range(410.0, 440.0), "no3" to Range(2.0, 10.0),
        "po4" to Range(0.02, 0.06), "sr" to Range(8.0, 10.0),
    )

    /** SPS ideals (01_reef_care §1.1, SPS column). */
    val SPS: List<ParameterDef> = SOFT_CORAL.withIdeals(
        "sal" to Range(1.025, 1.026), "alk" to Range(8.0, 9.5), "ca" to Range(420.0, 450.0),
        "mg" to Range(1280.0, 1380.0), "no3" to Range(2.0, 8.0), "po4" to Range(0.02, 0.05),
        "sr" to Range(8.0, 10.0), "orp" to Range(340.0, 380.0),
    )

    fun forReefType(type: ReefType): List<ParameterDef> = when (type) {
        ReefType.FOWLR -> FOWLR
        ReefType.SOFT -> SOFT_CORAL
        ReefType.LPS -> LPS
        ReefType.SPS -> SPS
    }

    fun byId(id: String, type: ReefType = ReefType.SOFT): ParameterDef? = forReefType(type).firstOrNull { it.id == id }

    val ALL_IDS: List<String> = SOFT_CORAL.map { it.id }

    /** Favorites, in default dashboard order (DESIGN §5.2). */
    val DEFAULT_FAVORITES: List<String> = listOf("temp", "sal", "alk", "ca", "mg", "ph", "no3", "po4")

    /** The six weekly parameters added by the "Weekly set" chip (DESIGN §6.2). */
    val WEEKLY_SET: List<String> = listOf("sal", "alk", "ca", "ph", "no3", "po4")

    private val SHORT = mapOf(
        "temp" to "Temp", "sal" to "Salinity", "alk" to "Alk", "ca" to "Ca", "mg" to "Mg", "ph" to "pH",
        "no3" to "NO3", "po4" to "PO4", "nh3" to "NH3", "no2" to "NO2", "k" to "K", "sr" to "Sr", "i" to "Iodine",
        "orp" to "ORP", "do" to "DO", "tds" to "TDS", "sio2" to "SiO2", "fe" to "Fe",
    )

    fun shortName(id: String): String = SHORT[id] ?: id

    /** Default overlay pairs for the detail chart (DESIGN §5.5). */
    fun defaultOverlay(id: String): String? = when (id) {
        "alk" -> "ca"; "ca" -> "alk"; "no3" -> "po4"; "po4" -> "no3"; "temp" -> "do"; "do" -> "temp"
        "mg" -> "ca"; "sal" -> "temp"
        else -> null
    }

    private fun List<ParameterDef>.withIdeals(vararg pairs: Pair<String, Range>): List<ParameterDef> {
        val m = pairs.toMap()
        return map { d -> m[d.id]?.let { d.copy(ideal = it) } ?: d }
    }
}
