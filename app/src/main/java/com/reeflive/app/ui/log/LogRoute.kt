package com.reeflive.app.ui.log

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.reeflive.app.data.repo.AppSettings
import com.reeflive.app.data.repo.SessionView
import com.reeflive.app.di.AppGraph
import com.reeflive.app.ui.common.ParamView
import com.reeflive.app.ui.common.TimeText
import com.reeflive.app.ui.common.UiMappers
import com.reeflive.app.ui.components.LocalSnackbar
import com.reeflive.app.ui.components.ReorderItem
import com.reeflive.app.ui.dashboard.minuteTicker
import com.reeflive.app.ui.nav.reefViewModel
import com.reeflive.core.params.ParameterDef
import com.reeflive.core.params.ParameterTable
import com.reeflive.core.status.StatusEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Maps sessions to History days (newest first). */
object HistoryMapper {
    fun days(sessions: List<SessionView>, defs: Map<String, ParameterDef>, settings: AppSettings, now: Long): List<HistoryDay> =
        sessions.sortedByDescending { it.session.takenAt }
            .groupBy { TimeText.dayLabel(it.session.takenAt, now) }
            .map { (label, list) -> HistoryDay(label, list.map { session(it, defs, settings) }) }

    fun session(s: SessionView, defs: Map<String, ParameterDef>, settings: AppSettings): SessionUi = SessionUi(
        id = s.session.id,
        time = TimeText.clock(s.session.takenAt),
        kit = s.session.kitBrand,
        note = s.session.note,
        values = s.measurements.sortedBy { defs[it.paramId]?.order ?: 99 }.map { m ->
            val def = defs[m.paramId] ?: ParameterTable.byId(m.paramId)
            if (def == null) ValueChip(m.paramId, "${m.paramId} ${m.value}", com.reeflive.core.status.Status.UNKNOWN)
            else {
                val v = ParamView(def, settings.displayUnitFor(def.id, def.baseUnit))
                ValueChip(def.id, "${def.shortName} ${v.formatWithUnit(m.value)}", StatusEngine.valueStatus(def, m.value))
            }
        },
    )
}

class LogViewModel(private val g: AppGraph) : ViewModel() {
    val state: StateFlow<LogUi> = combine(
        g.measurements.latestEvaluations(), g.targets.all, g.measurements.sessions(300), g.settings.settings, minuteTicker(),
    ) { evals, all, sessions, settings, now ->
        val defs = all.associateBy { it.id }
        LogUi(
            loading = false,
            tiles = evals.map { pe -> UiMappers.tile(ParamView(pe.def, settings.displayUnitFor(pe.id, pe.def.baseUnit)), pe.eval, pe.history, now) },
            arrange = all.map { d -> ReorderItem(d.id, d.name, visible = evals.any { it.id == d.id }) },
            days = HistoryMapper.days(sessions, defs, settings, now),
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LogUi())

    fun delete(id: String, onDeleted: (SessionView) -> Unit) {
        viewModelScope.launch { g.measurements.deleteSession(id)?.let(onDeleted) }
    }

    fun restore(s: SessionView) {
        viewModelScope.launch {
            g.measurements.restore(s)
            g.alertEvaluator.runNow(notify = false)
        }
    }

    fun arrange(items: List<ReorderItem>) {
        viewModelScope.launch {
            g.targets.setOrder(items.map { it.id })
            items.forEach { g.targets.setHidden(it.id, !it.visible) }
        }
    }
}

@Composable
fun LogRoute(onOpenParam: (String) -> Unit, onOpenSession: (String) -> Unit) {
    val vm = reefViewModel { LogViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    ParametersContent(
        state = state,
        actions = LogActions(
            onOpenParam = onOpenParam,
            onOpenSession = onOpenSession,
            onDeleteSession = { id ->
                vm.delete(id) { snap ->
                    scope.launch {
                        val r = snackbar.showSnackbar("Test deleted", actionLabel = "Undo", duration = SnackbarDuration.Long)
                        if (r == SnackbarResult.ActionPerformed) vm.restore(snap)
                    }
                }
            },
            onArrange = vm::arrange,
        ),
    )
}
