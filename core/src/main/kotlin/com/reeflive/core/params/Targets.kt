package com.reeflive.core.params

import com.reeflive.core.model.ParameterOverride
import com.reeflive.core.model.ReefType

/** Merges user [ParameterOverride]s onto the reef-type defaults. */
object Targets {
    fun apply(def: ParameterDef, o: ParameterOverride?): ParameterDef {
        if (o == null) return def
        return def.copy(
            ideal = Range(o.idealLo ?: def.ideal.lo, o.idealHi ?: def.ideal.hi),
            acceptable = Range(o.accLo ?: def.acceptable.lo, o.accHi ?: def.acceptable.hi),
            dangerLow = o.dangerLo ?: def.dangerLow,
            dangerHigh = o.dangerHi ?: def.dangerHigh,
            maxChangePerDay = o.maxChange ?: def.maxChangePerDay,
            cadenceDays = o.cadenceDays ?: def.cadenceDays,
            favorite = o.favorite ?: def.favorite,
            order = o.order ?: def.order,
        )
    }

    /** All effective definitions for [type], ordered, hidden ones removed unless [includeHidden]. */
    fun effective(
        type: ReefType,
        overrides: Map<String, ParameterOverride>,
        includeHidden: Boolean = false,
    ): List<ParameterDef> = ParameterTable.forReefType(type)
        .filter { includeHidden || overrides[it.id]?.hidden != true }
        .map { apply(it, overrides[it.id]) }
        .sortedBy { it.order }

    /** Validation used by the targets editor: ideal ⊆ acceptable, danger outside acceptable. */
    fun validate(def: ParameterDef): List<String> {
        val errs = mutableListOf<String>()
        if (def.ideal.lo > def.ideal.hi) errs += "Ideal low is above ideal high"
        if (def.acceptable.lo > def.acceptable.hi) errs += "Acceptable low is above acceptable high"
        if (def.ideal.lo < def.acceptable.lo - Range.EPS || def.ideal.hi > def.acceptable.hi + Range.EPS) errs += "Ideal must sit inside acceptable"
        def.dangerLow?.let { if (it > def.acceptable.lo + Range.EPS) errs += "Danger low must be at or below acceptable low" }
        def.dangerHigh?.let { if (it < def.acceptable.hi - Range.EPS) errs += "Danger high must be at or above acceptable high" }
        return errs
    }
}
