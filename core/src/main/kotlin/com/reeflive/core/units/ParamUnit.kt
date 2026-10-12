package com.reeflive.core.units

/**
 * Measurement units (ARCHITECTURE §4.1). Named `ParamUnit` rather than `Unit` so it never
 * shadows `kotlin.Unit` in files that import it (Compose code is full of `() -> Unit`).
 */
enum class ParamUnit(val symbol: String, val family: Family) {
    F("°F", Family.TEMP),
    C("°C", Family.TEMP),
    SG("SG", Family.SAL),
    PPT("ppt", Family.SAL),
    MS_CM("mS/cm", Family.SAL),
    DKH("dKH", Family.ALK),
    MEQ_L("meq/L", Family.ALK),
    PPM_CACO3("ppm CaCO3", Family.ALK),
    PPM("ppm", Family.CONC),
    PPB("ppb", Family.CONC),
    PPM_NO3_N("ppm NO3-N", Family.CONC_NO3),
    PPM_PO4_P("ppm PO4-P", Family.CONC_PO4),
    PH("pH", Family.PH),
    MV("mV", Family.ORP),
    MG_L("mg/L", Family.DO),
    PCT_SAT("% sat", Family.DO),
    NONE("", Family.NONE),
    ;

    enum class Family { TEMP, SAL, ALK, CONC, CONC_NO3, CONC_PO4, PH, ORP, DO, NONE }

    companion object {
        /** Lenient lookup by enum name or symbol (case-insensitive), used by CSV import. */
        fun parse(text: String?): ParamUnit? {
            if (text == null) return null
            val t = text.trim()
            if (t.isEmpty()) return NONE
            entries.firstOrNull { it.name.equals(t, ignoreCase = true) }?.let { return it }
            entries.firstOrNull { it.symbol.equals(t, ignoreCase = true) }?.let { return it }
            return when (t.lowercase().replace(" ", "")) {
                "f", "degf", "fahrenheit" -> F
                "c", "degc", "celsius" -> C
                "dkh", "kh" -> DKH
                "meq", "meq/l" -> MEQ_L
                "ppmcaco3", "mg/lcaco3" -> PPM_CACO3
                "mg/l", "mgl" -> MG_L
                "%", "%sat", "pct" -> PCT_SAT
                "ms", "mscm" -> MS_CM
                else -> null
            }
        }
    }
}
