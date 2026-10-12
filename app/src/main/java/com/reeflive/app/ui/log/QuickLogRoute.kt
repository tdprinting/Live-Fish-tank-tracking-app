package com.reeflive.app.ui.log

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.reeflive.app.appGraph
import com.reeflive.app.data.repo.LogEntry
import com.reeflive.app.data.repo.SessionView
import com.reeflive.app.di.AppGraph
import com.reeflive.app.ui.common.ParamView
import com.reeflive.app.ui.components.DateTimeDialog
import com.reeflive.app.ui.components.LocalSnackbar
import com.reeflive.app.ui.components.ReefSheet
import com.reeflive.app.ui.components.parseDecimal
import com.reeflive.app.ui.components.rememberHaptics
import com.reeflive.core.model.AlertKind
import com.reeflive.core.params.ParameterDef
import com.reeflive.core.params.ParameterTable
import com.reeflive.core.status.AlertRules
import com.reeflive.core.status.Status
import com.reeflive.core.status.StatusEngine
import com.reeflive.core.units.ParamUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * State holder for one opening of the Quick Log sheet. Not a ViewModel: the sheet lives outside the NavHost
 * and each opening starts fresh. Saving runs on the app scope so it completes even if the sheet is gone.
 */
class QuickLogController(
    private val g: AppGraph,
    private val scope: CoroutineScope,
    preset: List<String>,
    /** When set, the controller edits this existing session instead of creating a new one. */
    private val editing: SessionView? = null,
) {
    private var defs: List<ParameterDef> = emptyList()
    private var units: MutableMap<String, ParamUnit> = mutableMapOf()
    private var lastBase: Map<String, Double> = emptyMap()
    private var order: MutableList<String> = preset.toMutableList()
    private var texts: MutableMap<String, String> = mutableMapOf()

    var ui by mutableStateOf(QuickLogUi(takenAt = System.currentTimeMillis()))
        private set
    var focus by mutableStateOf(preset.lastOrNull())
        private set

    suspend fun load() {
        defs = g.targets.effectiveOnce(includeHidden = false)
        val s = g.settings.current()
        defs.forEach { units[it.id] = s.displayUnitFor(it.id, it.baseUnit) }
        lastBase = g.measurements.latestValues().first()
        val kits = g.measurements.recentKits().first()
        ui = ui.copy(kit = s.lastKitBrand ?: kits.firstOrNull(), kits = kits)
        editing?.let { e ->
            // Include hidden parameters that this session recorded so nothing is silently dropped.
            val all = g.targets.effectiveOnce(includeHidden = true)
            val ids = e.measurements.map { it.paramId }
            defs = defs + all.filter { d -> d.id in ids && defs.none { it.id == d.id } }
            order.clear()
            e.measurements.sortedBy { m -> defs.firstOrNull { it.id == m.paramId }?.order ?: 99 }.forEach { m ->
                val d = defs.firstOrNull { it.id == m.paramId } ?: return@forEach
                val unit = m.enteredUnit.takeIf { it in d.units } ?: units[d.id] ?: d.baseUnit
                units[d.id] = unit
                order.add(d.id)
                texts[d.id] = ParamView(d, unit).format(m.value)
            }
            ui = ui.copy(
                takenAt = e.session.takenAt, isNow = false, kit = e.session.kitBrand,
                note = e.session.note.orEmpty(), showNote = !e.session.note.isNullOrBlank(),
            )
            focus = null
        }
        rebuild()
    }

    private fun view(id: String): ParamView? {
        val d = defs.firstOrNull { it.id == id } ?: return null
        return ParamView(d, units[id] ?: d.baseUnit)
    }

    private fun rebuild() {
        val chips = defs.sortedWith(compareBy({ !it.favorite }, { it.order })).map { LogChip(it.id, it.shortName, it.id in order) }
        val rows = order.mapNotNull { id ->
            val v = view(id) ?: return@mapNotNull null
            val text = texts[id].orEmpty()
            val parsed = parseDecimal(text)
            val status = parsed?.let { StatusEngine.valueStatus(v.def, v.toBase(it)) } ?: Status.UNKNOWN
            LogRowUi(
                paramId = id, name = v.def.name, text = text, unit = v.symbol, canCycleUnit = v.def.units.size > 1,
                last = lastBase[id]?.let { v.format(it) }, status = status, error = text.isNotBlank() && parsed == null,
            )
        }
        ui = ui.copy(chips = chips, rows = rows)
    }

    fun toggle(id: String) {
        if (id in order) {
            if (texts[id].isNullOrBlank()) order.remove(id)
        } else {
            order.add(id)
            focus = id
        }
        rebuild()
    }

    fun weekly() {
        ParameterTable.WEEKLY_SET.filter { w -> defs.any { it.id == w } && w !in order }.forEach { order.add(it) }
        focus = order.firstOrNull { texts[it].isNullOrBlank() }
        rebuild()
    }

    fun remove(id: String) {
        order.remove(id)
        texts.remove(id)
        rebuild()
    }

    fun text(id: String, t: String) {
        texts[id] = t.filter { it.isDigit() || it == '.' || it == ',' || it == '-' }.take(10)
        rebuild()
    }

    fun cycle(id: String) {
        val v = view(id) ?: return
        units[id] = v.nextUnit()
        rebuild()
    }

    fun time(ms: Long) {
        ui = ui.copy(takenAt = ms, isNow = kotlin.math.abs(System.currentTimeMillis() - ms) < 120_000)
    }

    fun kit(k: String?) { ui = ui.copy(kit = k) }
    fun note(n: String) { ui = ui.copy(note = n) }
    fun toggleNote() { ui = ui.copy(showNote = !ui.showNote) }

    /** Saves the session, runs the alert check and reports a one-line summary for the snackbar. */
    fun save(onSaved: (String) -> Unit) {
        if (!ui.canSave) return
        val entries = order.mapNotNull { id ->
            val v = view(id) ?: return@mapNotNull null
            val n = parseDecimal(texts[id].orEmpty()) ?: return@mapNotNull null
            LogEntry(id, v.toBase(n), v.unit)
        }
        if (entries.isEmpty()) return
        ui = ui.copy(saving = true)
        val takenAt = if (ui.isNow) System.currentTimeMillis() else ui.takenAt
        val kit = ui.kit
        val note = ui.note
        if (editing != null) {
            g.appScope.launch {
                val msg = runCatching {
                    val session = editing.session.copy(takenAt = takenAt, kitBrand = kit?.trim()?.ifBlank { null }, note = note.trim().ifBlank { null })
                    g.measurements.updateSession(session, entries)
                    g.alertEvaluator.runNow(notify = false)
                    "Test updated"
                }.getOrElse { "Couldn't save: ${it.message ?: "unknown error"}" }
                scope.launch { onSaved(msg) }
            }
            return
        }
        g.appScope.launch {
            val msg = runCatching {
                g.measurements.saveSession(takenAt, kit, note, entries)
                g.settings.setLastKitBrand(kit)
                val fresh = g.alertEvaluator.runNow()
                val danger = fresh.firstOrNull { it.kind == AlertKind.DANGER || it.kind == AlertKind.FAST_CHANGE }
                val first = entries.first()
                val def = defs.first { it.id == first.paramId }
                val verdict = danger?.message ?: run {
                    val h = g.measurements.historyOnce(def.id, takenAt - 60 * 86_400_000L)
                    AlertRules.shortVerdict(def, StatusEngine.evaluate(def, h, System.currentTimeMillis()))
                }
                "Saved ${entries.size} value" + (if (entries.size > 1) "s" else "") + " · " + verdict
            }.getOrElse { "Couldn't save: ${it.message ?: "unknown error"}" }
            scope.launch { onSaved(msg) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickLogRoute(preset: List<String>, onDismiss: () -> Unit) {
    val graph = LocalContext.current.appGraph
    val scope = rememberCoroutineScope()
    val ctl = remember { QuickLogController(graph, scope, preset) }
    val sheet: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val snackbar = LocalSnackbar.current
    val haptics = rememberHaptics()
    var picking by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { ctl.load() }

    fun close(after: () -> Unit = {}) {
        scope.launch {
            sheet.hide()
            onDismiss()
            after()
        }
    }

    ReefSheet(onDismiss = onDismiss, state = sheet) {
        QuickLogContent(
            state = ctl.ui,
            focusParam = ctl.focus,
            actions = QuickLogActions(
                onChip = ctl::toggle,
                onWeekly = ctl::weekly,
                onRemove = ctl::remove,
                onText = ctl::text,
                onCycleUnit = ctl::cycle,
                onPickTime = { picking = true },
                onKit = ctl::kit,
                onNote = ctl::note,
                onToggleNote = ctl::toggleNote,
                onSave = {
                    haptics.confirm()
                    ctl.save { msg ->
                        // The sheet's own scope dies with it; show the confirmation from the app scope.
                        close { graph.appScope.launch(Dispatchers.Main) { snackbar.showSnackbar(msg) } }
                    }
                },
                onClose = { close() },
            ),
        )
    }
    if (picking) {
        DateTimeDialog(
            initial = ctl.ui.takenAt,
            onPick = {
                ctl.time(it)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}
