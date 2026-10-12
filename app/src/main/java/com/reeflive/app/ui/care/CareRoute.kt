package com.reeflive.app.ui.care

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.reeflive.app.data.repo.TaskWithLast
import com.reeflive.app.di.AppGraph
import com.reeflive.app.ui.common.UiMappers
import com.reeflive.app.ui.components.ButtonKind
import com.reeflive.app.ui.components.LocalSnackbar
import com.reeflive.app.ui.components.ReefButton
import com.reeflive.app.ui.components.ReefTextField
import com.reeflive.app.ui.components.rememberHaptics
import com.reeflive.app.ui.dashboard.minuteTicker
import com.reeflive.app.ui.nav.reefViewModel
import com.reeflive.app.ui.theme.ReefShapes
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.core.care.DueState
import com.reeflive.core.care.TaskSchedule
import com.reeflive.core.model.TaskCompletion
import com.reeflive.core.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId

object CareMapper {
    fun build(tasks: List<TaskWithLast>, now: Long, zone: ZoneId): CareUi {
        val dues = tasks.filter { it.task.active }.map { TaskSchedule.due(it.task, it.lastDoneAt, now, zone) }.sortedBy { it.dueAt }
        return CareUi(
            loading = false,
            today = dues.filter { it.state == DueState.OVERDUE || it.state == DueState.TODAY }.map { UiMappers.task(it, now) },
            upcoming = dues.filter { it.state == DueState.UPCOMING }.map { UiMappers.task(it, now) },
            all = (dues + tasks.filter { !it.task.active }.map { TaskSchedule.due(it.task, it.lastDoneAt, now, zone) })
                .sortedBy { it.task.title.lowercase() }
                .map { d -> UiMappers.task(d, now).let { if (d.task.active) it else it.copy(dueLabel = "Paused", state = DueState.LATER) } },
        )
    }
}

class CareViewModel(private val g: AppGraph) : ViewModel() {
    private val inFlight = mutableSetOf<String>()

    val state: StateFlow<CareUi> = combine(g.tasks.tasks, minuteTicker()) { t, now -> CareMapper.build(t, now, ZoneId.systemDefault()) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CareUi())

    /** Default amount for the water-change dialog: 10 % of the system, rounded to 0.5 gal. */
    suspend fun suggestedGallons(): String {
        val sys = g.tank.get()?.systemGallons ?: 60.0
        return Fmt.trim(Math.round(sys * 0.1 * 2) / 2.0, 1)
    }

    fun complete(taskId: String, note: String?, amount: String?, onDone: (TaskCompletion?) -> Unit) {
        if (!inFlight.add(taskId)) return
        viewModelScope.launch {
            try {
                onDone(TaskOps.complete(g, taskId, note, amount))
            } finally {
                inFlight.remove(taskId)
            }
        }
    }

    fun undo(c: TaskCompletion) { viewModelScope.launch { TaskOps.undo(g, c) } }

    fun snooze(taskId: String, onDone: () -> Unit) {
        if (!inFlight.add(taskId)) return
        viewModelScope.launch {
            try {
                TaskOps.snoozeToTomorrow(g, taskId)
                onDone()
            } finally {
                inFlight.remove(taskId)
            }
        }
    }
}

/** Water-change completion asks how much was changed ("Changed 6 gal"); other tasks complete at once. */
@Composable
fun CompletionDialog(title: String, initialAmount: String, onConfirm: (note: String?, amount: String?) -> Unit, onDismiss: () -> Unit) {
    val c = ReefTheme.colors
    var amount by remember { mutableStateOf(initialAmount) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.sheet,
        shape = ReefShapes.dialog,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall, color = c.text) },
        text = {
            Column {
                ReefTextField(amount, { amount = it }, Modifier.fillMaxWidth(), label = "Gallons changed", numeric = true, suffix = {
                    Text("gal", style = MaterialTheme.typography.labelMedium, color = c.textTertiary)
                })
                Spacer(Modifier.height(10.dp))
                ReefTextField(note, { note = it }, Modifier.fillMaxWidth(), label = "Note (optional)", placeholder = "New salt batch, cleaned sump…", singleLine = false)
            }
        },
        confirmButton = { ReefButton("Done", { onConfirm(note.ifBlank { null }, amount.ifBlank { null }?.let { "$it gal" }) }, compact = true) },
        dismissButton = { ReefButton("Cancel", onDismiss, kind = ButtonKind.OUTLINED, compact = true) },
    )
}

@Composable
fun CareRoute(onOpenTask: (String) -> Unit, onNewTask: () -> Unit, onOpenCalc: (Calc) -> Unit) {
    val vm = reefViewModel { CareViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var asking by remember { mutableStateOf<Pair<String, String>?>(null) }
    var suggested by remember { mutableStateOf("6") }

    fun finish(id: String, title: String, note: String?, amount: String?) {
        haptics.confirm()
        vm.complete(id, note, amount) { c ->
            if (c != null) scope.launch {
                val r = snackbar.showSnackbar("Done: $title", actionLabel = "Undo", duration = SnackbarDuration.Short)
                if (r == SnackbarResult.ActionPerformed) vm.undo(c)
            }
        }
    }

    CareContent(
        state = state,
        actions = CareActions(
            onCheck = { id ->
                val t = (state.today + state.upcoming + state.all).firstOrNull { it.id == id }
                val title = t?.title ?: "Task"
                if (TaskOps.isWaterChange(title)) {
                    scope.launch {
                        suggested = vm.suggestedGallons()
                        asking = id to title
                    }
                } else finish(id, title, null, null)
            },
            onSnooze = { id ->
                haptics.tick()
                val title = (state.today + state.upcoming).firstOrNull { it.id == id }?.title ?: "Task"
                vm.snooze(id) { scope.launch { snackbar.showSnackbar("$title snoozed until tomorrow") } }
            },
            onOpenTask = onOpenTask,
            onNewTask = onNewTask,
            onOpenCalc = onOpenCalc,
        ),
    )
    asking?.let { (id, title) ->
        CompletionDialog(
            title = title,
            initialAmount = suggested,
            onConfirm = { note, amount ->
                asking = null
                finish(id, title, note, amount)
            },
            onDismiss = { asking = null },
        )
    }
}
