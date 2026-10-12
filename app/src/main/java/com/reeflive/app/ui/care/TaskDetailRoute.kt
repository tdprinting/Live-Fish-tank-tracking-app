package com.reeflive.app.ui.care

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.reeflive.app.data.repo.TANK_ID
import com.reeflive.app.di.AppGraph
import com.reeflive.app.ui.common.TimeText
import com.reeflive.app.ui.components.LocalSnackbar
import com.reeflive.app.ui.components.ReefDialog
import com.reeflive.app.ui.components.TimeOfDayDialog
import com.reeflive.app.ui.components.rememberHaptics
import com.reeflive.app.ui.dashboard.minuteTicker
import com.reeflive.app.ui.nav.Routes
import com.reeflive.app.ui.nav.reefViewModel
import com.reeflive.core.care.TaskSchedule
import com.reeflive.core.model.CareTask
import com.reeflive.core.model.Ids
import com.reeflive.core.model.TaskCompletion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId

object TaskForms {
    fun of(t: CareTask): TaskForm = TaskForm(
        title = t.title,
        mode = if (t.intervalDays == null && t.weekdaysMask != null) 1 else 0,
        interval = (t.intervalDays ?: 7).toString(),
        weekdaysMask = t.weekdaysMask ?: 0,
        hour = t.reminderHour,
        minute = t.reminderMinute,
        equipmentId = t.equipmentId,
        notes = t.notes.orEmpty(),
        active = t.active,
    )

    /** Applies the form onto [base] (or a new task); null when the form is invalid. */
    fun apply(f: TaskForm, base: CareTask?, now: Long): CareTask? {
        if (!f.valid) return null
        val t = base ?: CareTask(Ids.new(), TANK_ID, f.title.trim(), createdAt = now)
        return t.copy(
            title = f.title.trim(),
            intervalDays = if (f.mode == 0) f.intervalDays else null,
            weekdaysMask = if (f.mode == 1) f.weekdaysMask else null,
            reminderHour = f.hour,
            reminderMinute = f.minute,
            equipmentId = f.equipmentId,
            notes = f.notes.trim().ifBlank { null },
            active = f.active,
        )
    }
}

class TaskDetailViewModel(private val g: AppGraph, private val taskId: String) : ViewModel() {
    private val isNew = taskId == Routes.NEW
    private val form = MutableStateFlow<TaskForm?>(if (isNew) TaskForm() else null)

    val state: StateFlow<TaskDetailUi> = combine(
        if (isNew) flowOf(null) else g.tasks.task(taskId),
        if (isNew) flowOf(emptyList()) else g.tasks.completions(taskId),
        g.equipment.all(),
        form,
        minuteTicker(),
    ) { task, comps, equipment, f, now ->
        if (!isNew && task == null) return@combine TaskDetailUi(loading = false, found = false)
        val zone = ZoneId.systemDefault()
        val last = comps.maxOfOrNull { it.completedAt }
        val due = task?.let { TaskSchedule.due(it, last, now, zone) }
        val base = task?.let(TaskForms::of)
        TaskDetailUi(
            loading = false,
            isNew = isNew,
            form = f ?: base ?: TaskForm(),
            dirty = f != null && f != base,
            dueLabel = due?.let(TaskSchedule::dueLabel).orEmpty(),
            dueState = due?.state ?: com.reeflive.core.care.DueState.LATER,
            lastDone = last?.let { TimeText.ago(it, now) } ?: "Never",
            nextDue = due?.let { TimeText.full(it.dueAt) }.orEmpty(),
            equipment = equipment.map { it.id to it.name },
            history = comps.sortedByDescending { it.completedAt }.take(30).map { c ->
                CompletionUi(c.id, TimeText.full(c.completedAt), listOfNotNull(c.amount, c.note).joinToString(" · ").ifBlank { null })
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskDetailUi(isNew = isNew))

    fun edit(f: TaskForm) { form.value = f }

    fun save(onSaved: (String) -> Unit) {
        val f = state.value.form
        viewModelScope.launch {
            val base = if (isNew) null else g.tasks.taskOnce(taskId)
            val t = TaskForms.apply(f, base, System.currentTimeMillis()) ?: return@launch
            TaskOps.save(g, t)
            form.value = if (isNew) TaskForm() else null
            onSaved(if (isNew) "Task created" else "Task saved")
        }
    }

    fun complete(note: String?, amount: String?, onDone: (TaskCompletion?) -> Unit) {
        viewModelScope.launch { onDone(TaskOps.complete(g, taskId, note, amount)) }
    }

    fun undo(c: TaskCompletion) { viewModelScope.launch { TaskOps.undo(g, c) } }

    fun snooze(onDone: () -> Unit) { viewModelScope.launch { TaskOps.snoozeToTomorrow(g, taskId); onDone() } }

    fun delete(onDone: () -> Unit) { viewModelScope.launch { TaskOps.delete(g, taskId); onDone() } }

    suspend fun suggestedGallons(): String {
        val sys = g.tank.get()?.systemGallons ?: 60.0
        return com.reeflive.core.util.Fmt.trim(Math.round(sys * 0.1 * 2) / 2.0, 1)
    }
}

@Composable
fun TaskDetailRoute(taskId: String, onBack: () -> Unit) {
    val vm = reefViewModel(key = "task-$taskId") { TaskDetailViewModel(it, taskId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var pickTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var askAmount by remember { mutableStateOf<String?>(null) }

    fun finish(note: String?, amount: String?) {
        haptics.confirm()
        vm.complete(note, amount) { c ->
            if (c != null) scope.launch {
                val r = snackbar.showSnackbar("Marked done", actionLabel = "Undo", duration = SnackbarDuration.Short)
                if (r == SnackbarResult.ActionPerformed) vm.undo(c)
            }
        }
    }

    TaskDetailContent(
        state = state,
        actions = TaskDetailActions(
            onBack = onBack,
            onForm = vm::edit,
            onPickTime = { pickTime = true },
            onSave = {
                haptics.confirm()
                vm.save { msg ->
                    scope.launch { snackbar.showSnackbar(msg) }
                    if (state.isNew) onBack()
                }
            },
            onComplete = {
                if (TaskOps.isWaterChange(state.form.title)) scope.launch { askAmount = vm.suggestedGallons() } else finish(null, null)
            },
            onSnooze = { vm.snooze { scope.launch { snackbar.showSnackbar("Snoozed until tomorrow") } } },
            onDelete = { confirmDelete = true },
        ),
    )
    if (pickTime) {
        TimeOfDayDialog(state.form.hour, state.form.minute, onPick = { h, m ->
            vm.edit(state.form.copy(hour = h, minute = m))
            pickTime = false
        }, onDismiss = { pickTime = false })
    }
    askAmount?.let { initial ->
        CompletionDialog(state.form.title, initial, onConfirm = { note, amount ->
            askAmount = null
            finish(note, amount)
        }, onDismiss = { askAmount = null })
    }
    if (confirmDelete) {
        ReefDialog(
            title = "Delete this task?",
            body = "Its reminder and completion history will be removed.",
            confirm = "Delete",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                vm.delete(onBack)
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
