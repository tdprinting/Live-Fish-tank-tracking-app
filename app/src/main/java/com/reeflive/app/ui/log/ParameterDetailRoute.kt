package com.reeflive.app.ui.log

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.reeflive.app.data.repo.AppSettings
import com.reeflive.app.data.repo.SessionView
import com.reeflive.app.di.AppGraph
import com.reeflive.app.ui.charts.OverlaySeries
import com.reeflive.app.ui.common.ParamView
import com.reeflive.app.ui.common.TimeText
import com.reeflive.app.ui.common.UiMappers
import com.reeflive.app.ui.components.LocalSnackbar
import com.reeflive.app.ui.components.parseDecimal
import com.reeflive.app.ui.dashboard.minuteTicker
import com.reeflive.app.ui.nav.reefViewModel
import com.reeflive.core.model.EventType
import com.reeflive.core.model.ReefEvent
import com.reeflive.core.model.Tank
import com.reeflive.core.params.ParameterDef
import com.reeflive.core.params.ParameterTable
import com.reeflive.core.params.Range
import com.reeflive.core.params.Targets
import com.reeflive.core.status.InsightEngine
import com.reeflive.core.status.ParamStats
import com.reeflive.core.status.Reading
import com.reeflive.core.status.StatusEngine
import com.reeflive.core.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Pure mapping from repository data to [DetailUi]; kept outside the ViewModel so it is easy to reason about. */
object DetailMapper {
    private const val DAY = UiMappers.DAY
    val RANGE_DAYS: List<Int?> = listOf(7, 30, 90, 365, null)

    fun form(view: ParamView): TargetsForm {
        val d = view.def
        fun f(v: Double?) = v?.let { Fmt.trim(view.toDisplay(it), view.precision + 1) } ?: ""
        val lo = d.dangerLow?.let(view::toDisplay)
        val hi = d.dangerHigh?.let(view::toDisplay)
        // A decreasing conversion would swap the sides; keep "below" as the smaller number.
        val (dl, dh) = if (lo != null && hi != null && lo > hi) d.dangerHigh to d.dangerLow else d.dangerLow to d.dangerHigh
        val ideal = view.range(d.ideal)
        val acc = view.range(d.acceptable)
        return TargetsForm(
            idealLo = Fmt.trim(ideal.lo, view.precision + 1), idealHi = Fmt.trim(ideal.hi, view.precision + 1),
            accLo = Fmt.trim(acc.lo, view.precision + 1), accHi = Fmt.trim(acc.hi, view.precision + 1),
            dangerLo = f(dl), dangerHi = f(dh),
        )
    }

    /** Parses the form back into a definition in base units, or returns the problems found. */
    fun parse(view: ParamView, form: TargetsForm): Pair<ParameterDef?, List<String>> {
        val errs = mutableListOf<String>()
        fun req(s: String, label: String): Double? = parseDecimal(s).also { if (it == null) errs += "$label needs a number" }
        fun opt(s: String, label: String): Double? = if (s.isBlank()) null else parseDecimal(s).also { if (it == null) errs += "$label needs a number" }
        val il = req(form.idealLo, "Ideal low")
        val ih = req(form.idealHi, "Ideal high")
        val al = req(form.accLo, "Acceptable low")
        val ah = req(form.accHi, "Acceptable high")
        val dl = opt(form.dangerLo, "Danger below")
        val dh = opt(form.dangerHi, "Danger above")
        if (il == null || ih == null || al == null || ah == null || errs.isNotEmpty()) return null to errs
        fun r(a: Double, b: Double): Range {
            val x = view.toBase(a)
            val y = view.toBase(b)
            return Range(minOf(x, y), maxOf(x, y))
        }
        var dlb = dl?.let(view::toBase)
        var dhb = dh?.let(view::toBase)
        if (dlb != null && dhb != null && dlb > dhb) { val t = dlb; dlb = dhb; dhb = t }
        val def = view.def.copy(ideal = r(il, ih), acceptable = r(al, ah), dangerLow = dlb, dangerHigh = dhb)
        val v = Targets.validate(def)
        return (if (v.isEmpty()) def else null) to v
    }

    fun build(
        def: ParameterDef,
        all: List<ParameterDef>,
        readings: Map<String, List<Reading>>,
        sessions: List<SessionView>,
        events: List<ReefEvent>,
        settings: AppSettings,
        tank: Tank?,
        range: Int,
        overlayId: String?,
        form: TargetsForm?,
        now: Long,
    ): DetailUi {
        val view = ParamView(def, settings.displayUnitFor(def.id, def.baseUnit))
        val history = readings[def.id].orEmpty().sortedBy { it.epochMs }
        val eval = StatusEngine.evaluate(def, history, now)
        val days = RANGE_DAYS.getOrNull(range)
        val first = history.firstOrNull()?.epochMs
        val windowStart = when {
            days != null -> now - days * DAY
            first != null -> minOf(first, now - 7 * DAY)
            else -> now - 30 * DAY
        }
        val inWindow = history.filter { it.epochMs >= windowStart - 3 * DAY }
        val resets = events.filter { it.type == EventType.WATER_CHANGE || it.type == EventType.DOSE }.map { it.at }

        val stats = ParamStats.of(def, history, fromMs = windowStart)?.let { s ->
            val cons = if (def.id in setOf("alk", "ca", "mg", "k", "i", "sr")) InsightEngine.consumptionPerDay(history, now, resets) else null
            StatsUi(
                min = view.format(s.min),
                max = view.format(s.max),
                mean = view.format(s.mean),
                consumption = cons?.let { c ->
                    val mid = eval.latest?.value ?: def.ideal.mid
                    val d = view.toDisplay(mid) - view.toDisplay(mid - c)
                    Fmt.num(d, view.precision + 1) + " ${view.symbol}/day" + if (c < 0) " (rising)" else ""
                },
                inRange = "${(s.inRangeShare * 100).roundToInt()}%",
                count = s.count,
            )
        }

        val overlayOptions = all.filter { it.id != def.id && readings[it.id].orEmpty().isNotEmpty() }
            .sortedWith(compareBy({ it.id != ParameterTable.defaultOverlay(def.id) }, { !it.favorite }, { it.order }))
            .take(6)
            .map { it.id to it.shortName }
        val overlay = overlayId?.let { oid -> all.firstOrNull { it.id == oid } }?.let { od ->
            val ov = ParamView(od, settings.displayUnitFor(od.id, od.baseUnit))
            OverlaySeries(od.shortName, UiMappers.chartPoints(ov, readings[od.id].orEmpty().filter { it.epochMs >= windowStart - 3 * DAY }), ov.precision, ov.symbol)
        }

        val delta = eval.delta?.let { d ->
            val prev = eval.previous!!
            "${view.formatDelta(d, prev.value)} since ${TimeText.dayLabel(prev.epochMs, now).lowercase()}"
        }

        val rows = sessions.asSequence()
            .mapNotNull { s -> s.measurements.firstOrNull { it.paramId == def.id }?.let { s to it } }
            .sortedByDescending { it.second.takenAt }
            .take(40)
            .map { (s, m) -> HistoryRowUi(s.session.id, TimeText.full(m.takenAt), view.formatWithUnit(m.value), StatusEngine.valueStatus(def, m.value), s.session.kitBrand) }
            .toList()

        val f = form ?: form(view)
        val errors = if (form == null) emptyList() else parse(view, form).second

        return DetailUi(
            loading = false,
            id = def.id,
            name = def.name,
            unit = view.symbol,
            canCycleUnit = def.units.size > 1,
            value = eval.latest?.let { view.format(it.value) },
            status = eval.status,
            trend = eval.trend,
            delta = delta,
            tests = history.size,
            staleDays = eval.staleDays?.takeIf { eval.isStale }?.roundToInt(),
            insight = InsightEngine.insight(def, history, now, tank?.systemGallons ?: 60.0, resets),
            range = range,
            windowStart = windowStart,
            windowEnd = now,
            points = UiMappers.chartPoints(view, inWindow),
            bands = UiMappers.bands(view),
            precision = view.precision,
            events = UiMappers.events(events.filter { it.at >= windowStart && it.type != EventType.NOTE }),
            overlay = overlay,
            overlayOptions = overlayOptions,
            overlayId = overlay?.let { overlayId },
            stats = stats,
            targets = f,
            targetsEdited = form != null,
            targetErrors = errors,
            history = rows,
        )
    }
}

class ParameterDetailViewModel(private val g: AppGraph, private val paramId: String) : ViewModel() {
    private data class Ui(val range: Int = 1, val overlay: String? = null, val overlayTouched: Boolean = false, val form: TargetsForm? = null)

    private val ui = MutableStateFlow(Ui())

    private data class Data(
        val def: ParameterDef?,
        val all: List<ParameterDef>,
        val readings: Map<String, List<Reading>>,
        val sessions: List<SessionView>,
        val settings: AppSettings,
    )

    private val data = combine(
        g.targets.observeDef(paramId), g.targets.all, g.measurements.readings(), g.measurements.sessions(400), g.settings.settings,
    ) { d, a, r, s, st -> Data(d, a, r, s, st) }

    val state: StateFlow<DetailUi> = combine(data, g.events.since(0L), g.tank.tank, ui, minuteTicker()) { d, ev, tank, u, now ->
        val def = d.def ?: return@combine DetailUi(loading = false, id = paramId, name = ParameterTable.shortName(paramId))
        val overlay = if (u.overlayTouched) u.overlay
        else ParameterTable.defaultOverlay(paramId)?.takeIf { d.readings[it].orEmpty().isNotEmpty() && d.readings[paramId].orEmpty().isNotEmpty() }
        DetailMapper.build(def, d.all, d.readings, d.sessions, ev, d.settings, tank, u.range, overlay, u.form, now)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUi(id = paramId))

    fun range(i: Int) { ui.value = ui.value.copy(range = i) }
    fun overlay(id: String?) { ui.value = ui.value.copy(overlay = id, overlayTouched = true) }
    fun targets(f: TargetsForm) { ui.value = ui.value.copy(form = f) }

    fun cycleUnit() {
        viewModelScope.launch {
            val def = g.targets.def(paramId) ?: return@launch
            val s = g.settings.current()
            val view = ParamView(def, s.displayUnitFor(paramId, def.baseUnit))
            g.settings.setParamUnit(paramId, view.nextUnit())
            ui.value = ui.value.copy(form = null)
        }
    }

    /** Saves the edited targets; reports a message for the snackbar. */
    fun saveTargets(onDone: (String) -> Unit) {
        val form = ui.value.form ?: return
        viewModelScope.launch {
            val def = g.targets.def(paramId) ?: return@launch
            val view = ParamView(def, g.settings.current().displayUnitFor(paramId, def.baseUnit))
            val (parsed, errs) = DetailMapper.parse(view, form)
            if (parsed == null) {
                onDone(errs.firstOrNull() ?: "Check the targets")
                return@launch
            }
            g.targets.saveRanges(parsed)
            ui.value = ui.value.copy(form = null)
            g.alertEvaluator.runNow(notify = false)
            onDone("Targets saved")
        }
    }

    fun resetTargets(onDone: (String) -> Unit) {
        viewModelScope.launch {
            g.targets.resetRanges(paramId)
            ui.value = ui.value.copy(form = null)
            g.alertEvaluator.runNow(notify = false)
            onDone("Targets reset to defaults")
        }
    }
}

@Composable
fun ParameterDetailRoute(paramId: String, onBack: () -> Unit, onOpenSession: (String) -> Unit, onLog: (String) -> Unit) {
    val vm = reefViewModel(key = "param-$paramId") { ParameterDetailViewModel(it, paramId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val show: (String) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg) } }
    ParameterDetailContent(
        state = state,
        actions = DetailActions(
            onBack = onBack,
            onRange = vm::range,
            onCycleUnit = vm::cycleUnit,
            onOverlay = vm::overlay,
            onTargets = vm::targets,
            onSaveTargets = { vm.saveTargets(show) },
            onResetTargets = { vm.resetTargets(show) },
            onOpenSession = onOpenSession,
            onLog = { onLog(paramId) },
        ),
    )
}
