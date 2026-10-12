package com.reeflive.app.ui.dashboard

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.reeflive.app.di.AppGraph
import com.reeflive.app.ui.care.TaskOps
import com.reeflive.app.ui.components.LocalSnackbar
import com.reeflive.app.ui.components.rememberHaptics
import com.reeflive.app.ui.nav.reefViewModel
import com.reeflive.core.model.TaskCompletion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId

/** Re-emits every minute so relative times ("2 h ago") and due states stay fresh. */
fun minuteTicker(): Flow<Long> = flow {
    while (true) {
        emit(System.currentTimeMillis())
        delay(60_000)
    }
}

class DashboardViewModel(private val g: AppGraph) : ViewModel() {
    private data class Part1(
        val tank: com.reeflive.core.model.Tank?,
        val evals: List<com.reeflive.app.data.repo.ParamEval>,
        val settings: com.reeflive.app.data.repo.AppSettings,
        val tasks: List<com.reeflive.app.data.repo.TaskWithLast>,
    )

    private data class Part2(
        val events: List<com.reeflive.core.model.ReefEvent>,
        val alerts: List<com.reeflive.core.model.Alert>,
        val alive: List<com.reeflive.core.model.Livestock>,
        val light: com.reeflive.core.model.LightSchedule,
    )

    private val p1 = combine(g.tank.tank, g.measurements.latestEvaluations(), g.settings.settings, g.tasks.tasks) { t, e, s, k -> Part1(t, e, s, k) }
    private val p2 = combine(
        g.events.since(System.currentTimeMillis() - 45 * 86_400_000L), g.alerts.active(), g.livestock.alive(), g.lights.active(),
    ) { e, a, l, li -> Part2(e, a, l, li) }

    val state: StateFlow<DashboardUi> = combine(p1, p2, minuteTicker()) { a, b, now ->
        DashboardMapper.build(
            now = now, zone = ZoneId.systemDefault(), tank = a.tank, evals = a.evals, settings = a.settings, tasks = a.tasks,
            events = b.events, alerts = b.alerts, alive = b.alive, light = b.light, species = g.speciesDb,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUi())

    fun complete(taskId: String, onDone: (TaskCompletion?) -> Unit) {
        viewModelScope.launch { onDone(TaskOps.complete(g, taskId)) }
    }

    fun undo(c: TaskCompletion) {
        viewModelScope.launch { TaskOps.undo(g, c) }
    }

    fun dismissAlert(id: String) {
        viewModelScope.launch { g.alerts.acknowledge(id) }
    }

    fun saveOrder(ids: List<String>) {
        viewModelScope.launch { g.settings.setTileOrder(ids) }
    }
}

@Composable
fun DashboardRoute(
    onOpenParam: (String) -> Unit,
    onLogParam: (String) -> Unit,
    onOpenTask: (String) -> Unit,
    onOpenCare: () -> Unit,
    onOpenLive: () -> Unit,
    onOpenMore: () -> Unit,
) {
    val vm = reefViewModel { DashboardViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    DashboardContent(
        state = state,
        actions = DashboardActions(
            onOpenParam = onOpenParam,
            onLogParam = onLogParam,
            onCompleteTask = { id ->
                haptics.confirm()
                val title = state.due.firstOrNull { it.id == id }?.title ?: "Task"
                vm.complete(id) { c ->
                    if (c != null) scope.launch {
                        val r = snackbar.showSnackbar("Done: $title", actionLabel = "Undo", duration = SnackbarDuration.Short)
                        if (r == SnackbarResult.ActionPerformed) vm.undo(c)
                    }
                }
            },
            onOpenTask = onOpenTask,
            onOpenCare = onOpenCare,
            onOpenLive = onOpenLive,
            onOpenMore = onOpenMore,
            onDismissAlert = vm::dismissAlert,
            onSaveOrder = vm::saveOrder,
        ),
    )
}
