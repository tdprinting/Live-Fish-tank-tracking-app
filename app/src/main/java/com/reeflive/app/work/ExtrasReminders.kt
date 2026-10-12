package com.reeflive.app.work

import com.reeflive.app.ui.nav.Routes
import com.reeflive.core.extras.ExtrasLogic
import com.reeflive.core.extras.ReefExtras
import java.time.ZoneId

/** One daily reminder notification. */
data class ExtrasReminder(val tag: String, val title: String, val text: String, val route: String)

/** Low-stock / expiring supplies and due treatment doses, posted with the daily parameter check. */
object ExtrasReminders {
    fun build(e: ReefExtras, now: Long, zone: ZoneId): List<ExtrasReminder> {
        val out = mutableListOf<ExtrasReminder>()
        val low = ExtrasLogic.needsAttention(e.supplies, now)
        if (low.isNotEmpty()) {
            out += ExtrasReminder(
                "supplies", if (low.size == 1) "${low[0].name}: ${ExtrasLogic.supplyState(low[0], now).label.lowercase()}" else "${low.size} supplies need attention",
                low.joinToString(" · ") { "${it.name} (${ExtrasLogic.supplyState(it, now).label.lowercase()})" }, Routes.SUPPLIES,
            )
        }
        val due = e.cases.filter { ExtrasLogic.doseDue(it, now, zone) }
        if (due.isNotEmpty()) {
            out += ExtrasReminder(
                "doses", if (due.size == 1) "Dose due: ${due[0].title}" else "${due.size} treatment doses due",
                due.joinToString(" · ") { "${it.title}, day ${ExtrasLogic.dayOf(it, now, zone)} of ${it.days}" + if (it.dose.isNotBlank()) " (${it.dose})" else "" },
                Routes.TREATMENTS,
            )
        }
        return out
    }
}
