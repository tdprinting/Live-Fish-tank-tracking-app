package com.reeflive.app.ui.care

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reeflive.app.ui.components.ButtonKind
import com.reeflive.app.ui.components.DueChip
import com.reeflive.app.ui.components.FactRow
import com.reeflive.app.ui.components.GlassIconButton
import com.reeflive.app.ui.components.ReefButton
import com.reeflive.app.ui.components.ReefChip
import com.reeflive.app.ui.components.ReefTextField
import com.reeflive.app.ui.components.ScreenHeader
import com.reeflive.app.ui.components.SectionHeader
import com.reeflive.app.ui.components.SegmentedPills
import com.reeflive.app.ui.components.pagePadding
import com.reeflive.app.ui.theme.GlassCard
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.core.care.DueState
import java.time.DayOfWeek

/** Editable task fields as typed. */
data class TaskForm(
    val title: String = "",
    /** 0 = every N days, 1 = on weekdays. */
    val mode: Int = 0,
    val interval: String = "7",
    val weekdaysMask: Int = 0,
    val hour: Int = 8,
    val minute: Int = 0,
    val equipmentId: String? = null,
    val notes: String = "",
    val active: Boolean = true,
) {
    val intervalDays: Int? get() = interval.trim().toIntOrNull()?.takeIf { it in 1..3650 }
    val valid: Boolean get() = title.isNotBlank() && if (mode == 0) intervalDays != null else weekdaysMask != 0
    val timeLabel: String get() = "%02d:%02d".format(hour, minute)
}

data class CompletionUi(val id: String, val time: String, val detail: String?)

data class TaskDetailUi(
    val loading: Boolean = true,
    val isNew: Boolean = false,
    val found: Boolean = true,
    val form: TaskForm = TaskForm(),
    val dirty: Boolean = false,
    val dueLabel: String = "",
    val dueState: DueState = DueState.LATER,
    val lastDone: String = "Never",
    val nextDue: String = "",
    val equipment: List<Pair<String, String>> = emptyList(),
    val history: List<CompletionUi> = emptyList(),
)

data class TaskDetailActions(
    val onBack: () -> Unit = {},
    val onForm: (TaskForm) -> Unit = {},
    val onPickTime: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onComplete: () -> Unit = {},
    val onSnooze: () -> Unit = {},
    val onDelete: () -> Unit = {},
)

private val WEEK = DayOfWeek.entries

/** Task detail + edit (DESIGN §5.7 "New task": title, interval or weekdays, reminder time, equipment, notes). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskDetailContent(state: TaskDetailUi, actions: TaskDetailActions, modifier: Modifier = Modifier) {
    val c = ReefTheme.colors
    val f = state.form
    LazyColumn(modifier.fillMaxSize(), contentPadding = pagePadding(top = 0.dp)) {
        item(key = "header") {
            ScreenHeader(
                if (state.isNew) "New task" else f.title.ifBlank { "Task" },
                onBack = actions.onBack,
                modifier = Modifier.padding(horizontal = 0.dp),
                actions = { if (!state.isNew) GlassIconButton(Icons.Rounded.Delete, "Delete task", actions.onDelete, tint = c.dangerText) },
            )
        }
        if (!state.isNew && state.found) {
            item(key = "status") {
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Next due", style = MaterialTheme.typography.labelMedium, color = c.textTertiary)
                            Text(state.nextDue, style = MaterialTheme.typography.titleMedium, color = c.text)
                        }
                        DueChip(state.dueLabel, state.dueState)
                    }
                    Spacer(Modifier.height(4.dp))
                    FactRow("Last done", state.lastDone)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReefButton("Mark done", actions.onComplete, icon = Icons.Rounded.Check, compact = true)
                        ReefButton("Tomorrow", actions.onSnooze, kind = ButtonKind.OUTLINED, icon = Icons.Rounded.Snooze, compact = true)
                    }
                }
            }
        }
        item(key = "form") {
            SectionHeader(if (state.isNew) "Details" else "Edit")
            GlassCard(Modifier.fillMaxWidth()) {
                ReefTextField(f.title, { actions.onForm(f.copy(title = it)) }, Modifier.fillMaxWidth(), label = "Title", placeholder = "Water change 10%")
                Spacer(Modifier.height(14.dp))
                Text("Repeat", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                Spacer(Modifier.height(6.dp))
                SegmentedPills(listOf("Every N days", "On weekdays"), f.mode, { actions.onForm(f.copy(mode = it)) }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                if (f.mode == 0) {
                    ReefTextField(
                        f.interval, { t -> actions.onForm(f.copy(interval = t.filter { it.isDigit() }.take(4))) }, Modifier.fillMaxWidth(),
                        label = "Interval", numeric = true, error = f.intervalDays == null,
                        suffix = { Text("days", style = MaterialTheme.typography.labelMedium, color = c.textTertiary) },
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        WEEK.forEach { d ->
                            val bit = 1 shl (d.value - 1)
                            val on = (f.weekdaysMask and bit) != 0
                            ReefChip(d.name.take(2).lowercase().replaceFirstChar { it.uppercase() }, on, {
                                actions.onForm(f.copy(weekdaysMask = f.weekdaysMask xor bit))
                            })
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Schedule, null, tint = c.textSecondary)
                    Spacer(Modifier.width(8.dp))
                    Text("Reminder", style = MaterialTheme.typography.bodyLarge, color = c.text, modifier = Modifier.weight(1f))
                    ReefChip(f.timeLabel, false, actions.onPickTime)
                }
                if (state.equipment.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Text("Linked equipment", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ReefChip("None", f.equipmentId == null, { actions.onForm(f.copy(equipmentId = null)) })
                        state.equipment.forEach { (id, name) ->
                            ReefChip(name, f.equipmentId == id, { actions.onForm(f.copy(equipmentId = id)) })
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                ReefTextField(f.notes, { actions.onForm(f.copy(notes = it)) }, Modifier.fillMaxWidth(), label = "Notes", singleLine = false, minLines = 2)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Active", style = MaterialTheme.typography.bodyLarge, color = c.text, modifier = Modifier.weight(1f))
                    Switch(
                        checked = f.active, onCheckedChange = { actions.onForm(f.copy(active = it)) },
                        colors = SwitchDefaults.colors(checkedTrackColor = c.primary, checkedThumbColor = c.text, uncheckedTrackColor = c.glass),
                    )
                }
                Spacer(Modifier.height(12.dp))
                ReefButton(
                    if (state.isNew) "Create task" else "Save changes", actions.onSave, Modifier.fillMaxWidth(),
                    enabled = f.valid && (state.isNew || state.dirty),
                )
            }
        }
        if (state.history.isNotEmpty()) {
            item(key = "histTitle") { SectionHeader("History") }
            items(state.history, key = { it.id }) { h ->
                GlassCard(Modifier.fillMaxWidth().padding(bottom = 8.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(h.time, style = MaterialTheme.typography.bodyMedium, color = c.text)
                    if (h.detail != null) Text(h.detail, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
            }
        }
    }
}
