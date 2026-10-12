package com.reeflive.core.status

import com.reeflive.core.params.ParameterDef

/** Summary statistics for a window of readings (parameter detail, DESIGN §5.5). Values in base units. */
data class ParamStats(
    val count: Int,
    val min: Double,
    val max: Double,
    val mean: Double,
    /** Share of readings inside the acceptable range, 0..1. */
    val inRangeShare: Double,
    /** Share of readings inside the ideal range, 0..1. */
    val idealShare: Double,
) {
    companion object {
        /** Stats of [history] within [fromMs, toMs]; null when nothing falls in the window. */
        fun of(def: ParameterDef, history: List<Reading>, fromMs: Long = Long.MIN_VALUE, toMs: Long = Long.MAX_VALUE): ParamStats? {
            val vs = history.filter { it.epochMs in fromMs..toMs }.map { it.value }
            if (vs.isEmpty()) return null
            return ParamStats(
                count = vs.size,
                min = vs.min(),
                max = vs.max(),
                mean = vs.average(),
                inRangeShare = vs.count { it in def.acceptable }.toDouble() / vs.size,
                idealShare = vs.count { it in def.ideal }.toDouble() / vs.size,
            )
        }
    }
}
