package com.reeflive.core.status

import com.reeflive.core.model.AlertKind
import com.reeflive.core.params.ParameterDef
import com.reeflive.core.util.Fmt
import kotlin.math.abs
import kotlin.math.max

/** An alert to be persisted (deduped by paramId + kind + dayKey by the app). */
data class AlertDraft(val paramId: String, val kind: AlertKind, val message: String)

object AlertRules {
    /**
     * Alerts implied by an evaluation. Danger reasons → DANGER, fast change → FAST_CHANGE (also danger
     * severity), out-of-acceptable → CAUTION, stale → STALE. Out-of-ideal alone is not alerted.
     */
    fun draftsFor(def: ParameterDef, eval: Evaluation): List<AlertDraft> {
        val out = mutableListOf<AlertDraft>()
        for (r in eval.reasons) {
            when (r) {
                is Reason.Danger -> out += AlertDraft(def.id, AlertKind.DANGER, "Danger: ${message(def, r)}")
                is Reason.FastChange -> out += AlertDraft(def.id, AlertKind.FAST_CHANGE, "Danger: ${message(def, r)}")
                is Reason.OutOfAcceptable -> out += AlertDraft(def.id, AlertKind.CAUTION, "Watch: ${message(def, r)}")
                is Reason.Stale -> out += AlertDraft(def.id, AlertKind.STALE, message(def, r))
                is Reason.OutOfIdeal -> Unit
            }
        }
        return out
    }

    fun unit(def: ParameterDef): String = def.baseUnit.symbol.let { if (it.isEmpty() || it == "pH") "" else " $it" }

    fun message(def: ParameterDef, r: Reason): String {
        val n = def.shortName
        val u = unit(def)
        return when (r) {
            is Reason.Danger ->
                "$n ${Fmt.num(r.value, def.precision)}$u is ${if (r.low) "below" else "above"} ${Fmt.num(r.threshold, def.precision)}$u"
            is Reason.FastChange -> {
                val dir = if (r.delta < 0) "fell" else "rose"
                val rate = abs(r.delta) / max(1.0, r.days)
                "$n $dir ${Fmt.num(rate, def.precision)}$u/day"
            }
            is Reason.OutOfAcceptable ->
                "$n ${Fmt.num(r.value, def.precision)}$u is ${if (r.low) "below" else "above"} the acceptable range"
            is Reason.OutOfIdeal ->
                "$n ${Fmt.num(r.value, def.precision)}$u is a little ${if (r.low) "low" else "high"}"
            is Reason.Stale -> "Not tested in ${r.days.toInt()} days"
        }
    }

    /** One-line summary for snackbars: "Alk is in range" / "Alk is a little low". */
    fun shortVerdict(def: ParameterDef, eval: Evaluation): String {
        val first = eval.reasons.firstOrNull { it !is Reason.Stale }
        return when {
            eval.status == Status.UNKNOWN -> "${def.shortName} has no data"
            first == null -> "${def.shortName} is in range"
            else -> message(def, first)
        }
    }
}
