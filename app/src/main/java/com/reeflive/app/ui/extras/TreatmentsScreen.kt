package com.reeflive.app.ui.extras

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reeflive.app.appGraph
import com.reeflive.app.ui.common.TimeText
import com.reeflive.app.ui.components.ButtonKind
import com.reeflive.app.ui.components.DateDialog
import com.reeflive.app.ui.components.EmptyState
import com.reeflive.app.ui.components.ReefButton
import com.reeflive.app.ui.components.ReefChip
import com.reeflive.app.ui.components.ReefTextField
import com.reeflive.app.ui.components.SectionHeader
import com.reeflive.app.ui.theme.GlassCard
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.core.extras.ExtrasLogic
import com.reeflive.core.extras.ReefExtras
import com.reeflive.core.extras.TreatmentCase
import com.reeflive.core.extras.TreatmentKind
import com.reeflive.core.model.Livestock
import kotlinx.coroutines.launch
import java.time.ZoneId

data class TreatmentActions(
    val onBack: () -> Unit = {},
    val onAdd: () -> Unit = {},
    val onEdit: (TreatmentCase) -> Unit = {},
    val onDose: (TreatmentCase) -> Unit = {},
    val onUndoDose: (TreatmentCase) -> Unit = {},
    val onEnd: (TreatmentCase) -> Unit = {},
)

/** Quarantine and medication tracker (research 01 §3.8, §4.3). */
@Composable
fun TreatmentsContent(data: ReefExtras, names: Map<String, String>, now: Long, actions: TreatmentActions, modifier: Modifier = Modifier) {
    val c = ReefTheme.colors
    val zone = ZoneId.systemDefault()
    val active = data.cases.filter { ExtrasLogic.isActive(it) }.sortedBy { it.startAt }
    val done = data.cases.filterNot { ExtrasLogic.isActive(it) }.sortedByDescending { it.endedAt }
    val due = active.count { ExtrasLogic.doseDue(it, now, zone) }
    ExtrasPage(
        "Quarantine & meds", if (active.isEmpty()) "No active treatments" else "${active.size} active" + if (due > 0) " · $due dose due" else "",
        actions.onBack, modifier, onAdd = actions.onAdd, addLabel = "New treatment",
    ) {
        if (data.cases.isEmpty()) {
            EmptyState(
                "No quarantine or treatment", "Track a quarantine, copper or prazi course: who is in it, the dose, every dose given and how it ended.",
                icon = Icons.Rounded.Medication, action = { ReefButton("Start one", actions.onAdd) },
            )
            Text(
                "Reference: quarantine new fish 4–6 weeks; copper 14–28 days (never in the display with inverts); " +
                    "hyposalinity 1.009–1.010 SG for 4–6 weeks; tank transfer every 72 h × 4. Always follow the product label.",
                style = MaterialTheme.typography.bodySmall, color = c.textTertiary,
            )
            return@ExtrasPage
        }
        active.forEach { t ->
            val day = ExtrasLogic.dayOf(t, now, zone)
            val isDue = ExtrasLogic.doseDue(t, now, zone)
            val dosedToday = ExtrasLogic.dosedToday(t, now, zone)
            GlassCard(Modifier.fillMaxWidth(), tint = if (isDue) c.caution else c.primary, onClick = { actions.onEdit(t) }) {
                Row {
                    Text(t.title, style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.weight(1f))
                    StatusPill(t.kind.label, c.primaryBright)
                }
                val who = t.livestockIds.mapNotNull { names[it] }
                if (who.isNotEmpty()) Text(who.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                if (t.dose.isNotBlank()) Text("Dose: ${t.dose}", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Day ${day.coerceAtMost(t.days)} of ${t.days} · ${ExtrasLogic.daysLeft(t, now, zone)} left · ${t.doses.size} doses logged",
                    style = MaterialTheme.typography.labelMedium, color = c.textTertiary,
                )
                GlassBar(ExtrasLogic.progress(t, now, zone))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        isDue -> ReefButton("Log today's dose", { actions.onDose(t) }, compact = true)
                        dosedToday -> ReefButton("Undo today's dose", { actions.onUndoDose(t) }, kind = ButtonKind.OUTLINED, compact = true)
                        t.doseEveryDays > 0 -> Text("Next dose on schedule", style = MaterialTheme.typography.labelMedium, color = c.goodText)
                    }
                    Spacer(Modifier.weight(1f))
                    ReefButton("End", { actions.onEnd(t) }, kind = ButtonKind.TONAL, compact = true)
                }
            }
        }
        if (done.isNotEmpty()) {
            SectionHeader("Finished")
            GlassCard(Modifier.fillMaxWidth()) {
                done.forEach { t ->
                    InfoRow(t.title, "${t.kind.label} · ${TimeText.shortDate(t.startAt)} – ${t.endedAt?.let { TimeText.shortDate(it) }.orEmpty()}" +
                        if (t.outcome.isNotBlank()) " · ${t.outcome}" else "")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TreatmentForm(initial: TreatmentCase, livestock: List<Livestock>, onSave: (TreatmentCase) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(initial.title) }
    var kind by remember { mutableStateOf(initial.kind) }
    var dose by remember { mutableStateOf(initial.dose) }
    var days by remember { mutableStateOf(initial.days.toString()) }
    var every by remember { mutableStateOf(initial.doseEveryDays) }
    var start by remember { mutableStateOf(initial.startAt) }
    var who by remember { mutableStateOf(initial.livestockIds.toSet()) }
    var pickDate by remember { mutableStateOf(false) }
    FormSheet(
        if (initial.title.isBlank()) "New treatment" else "Edit treatment", onDismiss,
        onSave = {
            onSave(initial.copy(title = title.trim(), kind = kind, dose = dose.trim(), days = days.toIntOrNull()?.coerceIn(1, 365) ?: kind.defaultDays,
                doseEveryDays = every, startAt = start, livestockIds = who.toList()))
        },
        saveEnabled = title.isNotBlank(), onDelete = onDelete,
    ) {
        ReefTextField(title, { title = it }, Modifier.fillMaxWidth(), label = "Name", placeholder = "New tang quarantine")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TreatmentKind.entries.forEach { k ->
                ReefChip(k.label, k == kind, {
                    kind = k
                    days = k.defaultDays.toString()
                    every = if (k == TreatmentKind.OBSERVATION || k == TreatmentKind.HYPOSALINITY) 0 else if (k == TreatmentKind.TANK_TRANSFER) 3 else 1
                })
            }
        }
        ReefTextField(dose, { dose = it }, Modifier.fillMaxWidth(), label = "Dose / target", placeholder = "Copper 0.5 ppm, prazi 1 tsp per 20 gal…")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReefTextField(days, { days = it.filter(Char::isDigit) }, Modifier.weight(1f), label = "Length (days)", numeric = true)
            ReefButton("Starts ${TimeText.shortDate(start)}", { pickDate = true }, Modifier.weight(1f), kind = ButtonKind.OUTLINED, compact = true)
        }
        Text("Dose reminder", style = MaterialTheme.typography.labelLarge, color = ReefTheme.colors.text)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0 to "None", 1 to "Daily", 2 to "Every 2 d", 3 to "Every 3 d", 7 to "Weekly").forEach { (n, l) -> ReefChip(l, every == n, { every = n }) }
        }
        if (livestock.isNotEmpty()) {
            Text("Who is in it", style = MaterialTheme.typography.labelLarge, color = ReefTheme.colors.text)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                livestock.forEach { l -> ReefChip(l.nickname, l.id in who, { who = if (l.id in who) who - l.id else who + l.id }) }
            }
        }
    }
    if (pickDate) DateDialog(start, onPick = { start = it; pickDate = false }, onDismiss = { pickDate = false })
}

@Composable
private fun EndForm(t: TreatmentCase, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var outcome by remember { mutableStateOf("") }
    FormSheet("End ${t.title}", onDismiss, onSave = { onSave(outcome.trim()) }, saveLabel = "End treatment") {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Healthy, moved to display", "Still sick", "Lost").forEach { o -> ReefChip(o, outcome == o, { outcome = o }) }
        }
        ReefTextField(outcome, { outcome = it }, Modifier.fillMaxWidth(), label = "Outcome")
    }
}

@Composable
fun TreatmentsRoute(onBack: () -> Unit) {
    val g = LocalContext.current.appGraph
    val data by g.extras.data.collectAsStateWithLifecycle()
    val livestock by g.livestock.alive().collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<TreatmentCase?>(null) }
    var ending by remember { mutableStateOf<TreatmentCase?>(null) }
    TreatmentsContent(
        data, livestock.associate { it.id to it.nickname }, g.clock.now(),
        TreatmentActions(
            onBack = onBack,
            onAdd = { editing = TreatmentCase(g.extras.newId(), "", startAt = g.clock.now()) },
            onEdit = { editing = it },
            onDose = { t -> scope.launch { g.extras.logDose(t.id) } },
            onUndoDose = { t -> scope.launch { g.extras.undoDose(t.id) } },
            onEnd = { ending = it },
        ),
    )
    editing?.let { t ->
        val exists = data.cases.any { it.id == t.id }
        TreatmentForm(
            t, livestock,
            onSave = { scope.launch { g.extras.saveCase(it) }; editing = null },
            onDelete = if (exists) ({ scope.launch { g.extras.deleteCase(t.id) }; editing = null }) else null,
            onDismiss = { editing = null },
        )
    }
    ending?.let { t -> EndForm(t, onSave = { o -> scope.launch { g.extras.endCase(t.id, o) }; ending = null }, onDismiss = { ending = null }) }
}
