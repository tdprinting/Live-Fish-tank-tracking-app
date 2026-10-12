package com.reeflive.app.ui.more

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.reeflive.app.di.AppGraph
import com.reeflive.app.ui.components.ButtonKind
import com.reeflive.app.ui.components.LocalSnackbar
import com.reeflive.app.ui.components.ReefButton
import com.reeflive.app.ui.components.ReefChip
import com.reeflive.app.ui.components.ScreenHeader
import com.reeflive.app.ui.components.SectionHeader
import com.reeflive.app.ui.components.TimeOfDayDialog
import com.reeflive.app.ui.components.pagePadding
import com.reeflive.app.ui.components.rememberHaptics
import com.reeflive.app.ui.nav.reefViewModel
import com.reeflive.app.ui.theme.GlassCard
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.core.model.LightChannel
import com.reeflive.core.model.LightCurve
import com.reeflive.core.model.LightEdit
import com.reeflive.core.model.LightPoint
import com.reeflive.core.model.LightSchedule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

fun channelColor(ch: LightChannel): Color = Color(ch.argb.toInt())

fun minuteLabel(m: Int): String = "%02d:%02d".format(m / 60, m % 60)

data class LightUi(
    val loading: Boolean = true,
    val name: String = "",
    val points: List<LightPoint> = emptyList(),
    val selected: Int = 0,
    val channel: Int = LightChannel.ROYAL.ordinal,
    val dirty: Boolean = false,
    val photoperiod: String = "",
    val nowMinute: Int = 0,
)

data class LightActions(
    val onBack: () -> Unit = {},
    val onSelect: (Int) -> Unit = {},
    val onChannel: (Int) -> Unit = {},
    val onDrag: (index: Int, minute: Int, level: Double) -> Unit = { _, _, _ -> },
    val onLevel: (channel: Int, level: Double) -> Unit = { _, _ -> },
    val onPickTime: () -> Unit = {},
    val onAddPoint: () -> Unit = {},
    val onRemovePoint: () -> Unit = {},
    val onPreset: (String) -> Unit = {},
    val onSave: () -> Unit = {},
    val onRevert: () -> Unit = {},
)

/** Light schedule editor (DESIGN §5.12): 8 channel curves, draggable points, presets and the day's color strip. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LightScheduleContent(state: LightUi, actions: LightActions, modifier: Modifier = Modifier) {
    val c = ReefTheme.colors
    Column(modifier.fillMaxSize()) {
        ScreenHeader("Light schedule", subtitle = "${state.name} · ${state.photoperiod} photoperiod", onBack = actions.onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(pagePadding()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            GlassCard(Modifier.fillMaxWidth()) {
                CurveChart(state, actions)
                Spacer(Modifier.height(10.dp))
                SwatchStrip(state.points, state.nowMinute)
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    listOf("00", "06", "12", "18", "24").forEachIndexed { i, t ->
                        Text(t, style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                        if (i < 4) Spacer(Modifier.weight(1f))
                    }
                }
                Text("Drag a point of the highlighted channel · tap to select", style = MaterialTheme.typography.labelSmall, color = c.textTertiary, modifier = Modifier.padding(top = 6.dp))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LightChannel.entries.forEach { ch ->
                    ReefChip(ch.label, state.channel == ch.ordinal, { actions.onChannel(ch.ordinal) }, accent = channelColor(ch))
                }
            }
            val p = state.points.getOrNull(state.selected)
            if (p != null) {
                SectionHeader("Point ${state.selected + 1} of ${state.points.size}", action = {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ReefButton("Add", actions.onAddPoint, kind = ButtonKind.TONAL, icon = Icons.Rounded.Add, compact = true)
                        ReefButton("Remove", actions.onRemovePoint, kind = ButtonKind.OUTLINED, icon = Icons.Rounded.Delete, compact = true, enabled = state.points.size > LightEdit.MIN_POINTS)
                    }
                })
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Schedule, null, tint = c.textSecondary)
                        Spacer(Modifier.width(8.dp))
                        Text("Time", style = MaterialTheme.typography.bodyLarge, color = c.text, modifier = Modifier.weight(1f))
                        ReefChip(minuteLabel(p.minuteOfDay), false, actions.onPickTime)
                    }
                    Spacer(Modifier.height(6.dp))
                    LightChannel.entries.forEach { ch ->
                        val v = p.channels[ch.ordinal]
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(ch.label, style = MaterialTheme.typography.labelMedium, color = if (state.channel == ch.ordinal) c.text else c.textSecondary, modifier = Modifier.width(88.dp))
                            Slider(
                                value = v.toFloat(),
                                onValueChange = { actions.onLevel(ch.ordinal, it.toDouble()) },
                                modifier = Modifier.weight(1f).semantics { contentDescription = "${ch.label} level" },
                                colors = SliderDefaults.colors(thumbColor = channelColor(ch), activeTrackColor = channelColor(ch), inactiveTrackColor = c.glass),
                            )
                            Text("${(v * 100).roundToInt()}%", style = ReefTheme.numbers.label, color = c.textSecondary, modifier = Modifier.width(40.dp))
                        }
                    }
                }
            }
            SectionHeader("Presets")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LightCurve.PRESETS.keys.forEach { name -> ReefChip(name, state.name == name && !state.dirty, { actions.onPreset(name) }) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ReefButton("Revert", actions.onRevert, Modifier.weight(1f), kind = ButtonKind.OUTLINED, enabled = state.dirty)
                ReefButton("Save schedule", actions.onSave, Modifier.weight(1f), enabled = state.dirty)
            }
            Text("The Live Tank uses this schedule and the swatch colors as-is.", style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
        }
    }
}

@Composable
private fun CurveChart(state: LightUi, actions: LightActions) {
    val c = ReefTheme.colors
    val points by rememberUpdatedState(state.points)
    val channel by rememberUpdatedState(state.channel)
    val act by rememberUpdatedState(actions)
    var dragging by remember { mutableStateOf(-1) }
    val grid = c.glassBorder
    val nowColor = c.coral
    val selColor = c.text
    Canvas(
        Modifier.fillMaxWidth().height(220.dp)
            .semantics { contentDescription = "Light curves for 8 channels over 24 hours" }
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    val i = nearest(points, o.x, size.width.toFloat())
                    if (i >= 0) act.onSelect(i)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { o ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        dragging = points.indices.minByOrNull { i ->
                            val p = points[i]
                            val dx = p.minuteOfDay / 1440f * w - o.x
                            val dy = (1f - p.channels[channel].toFloat()) * h - o.y
                            dx * dx + dy * dy
                        }?.takeIf { i ->
                            val p = points[i]
                            abs(p.minuteOfDay / 1440f * w - o.x) < 32.dp.toPx()
                        } ?: -1
                        if (dragging >= 0) act.onSelect(dragging)
                    },
                    onDragEnd = { dragging = -1 },
                    onDragCancel = { dragging = -1 },
                    onDrag = { change, _ ->
                        if (dragging >= 0) {
                            change.consume()
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            val minute = ((change.position.x / w) * 1440f).roundToInt().coerceIn(0, 1439)
                            val snapped = (minute / 5) * 5
                            val level = (1f - change.position.y / h).coerceIn(0f, 1f).toDouble()
                            act.onDrag(dragging, snapped, level)
                        }
                    },
                )
            },
    ) {
        val w = size.width
        val h = size.height
        for (i in 0..4) {
            val y = h * i / 4f
            drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        }
        for (hr in listOf(6, 12, 18)) {
            val x = w * hr / 24f
            drawLine(grid, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
        }
        val nowX = w * state.nowMinute / 1440f
        drawLine(nowColor.copy(alpha = 0.7f), Offset(nowX, 0f), Offset(nowX, h), strokeWidth = 2f)
        val order = LightChannel.entries.sortedBy { if (it.ordinal == state.channel) 1 else 0 }
        for (ch in order) {
            val sel = ch.ordinal == state.channel
            val path = Path()
            var first = true
            for (m in 0..1440 step 10) {
                val v = LightCurve.channelsAt(state.points, m.toDouble())[ch.ordinal]
                val x = w * m / 1440f
                val y = (1f - v.toFloat()) * h
                if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
            }
            drawPath(
                path, channelColor(ch).copy(alpha = if (sel) 1f else 0.35f),
                style = Stroke(width = if (sel) 3.dp.toPx() else 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        val selCh = LightChannel.entries[state.channel]
        state.points.forEachIndexed { i, p ->
            val x = w * p.minuteOfDay / 1440f
            val y = (1f - p.channels[state.channel].toFloat()) * h
            val isSel = i == state.selected
            drawCircle(channelColor(selCh), radius = if (isSel) 8.dp.toPx() else 5.dp.toPx(), center = Offset(x, y))
            if (isSel) drawCircle(selColor, radius = 8.dp.toPx(), center = Offset(x, y), style = Stroke(2.dp.toPx()))
        }
    }
}

private fun nearest(points: List<LightPoint>, x: Float, w: Float): Int =
    points.indices.minByOrNull { abs(points[it].minuteOfDay / 1440f * w - x) } ?: -1

/** The day's color strip: 96 slices of the perceived light color (shared with the Live Tank). */
@Composable
fun SwatchStrip(points: List<LightPoint>, nowMinute: Int, modifier: Modifier = Modifier) {
    val c = ReefTheme.colors
    val colors = remember(points) {
        (0 until 96).map { i -> Color(LightCurve.swatchArgb(LightCurve.channelsAt(points, i * 15.0 + 7.5)).toInt()) }
    }
    Canvas(modifier.fillMaxWidth().height(22.dp).semantics { contentDescription = "Light color through the day" }) {
        val sw = size.width / colors.size
        colors.forEachIndexed { i, col -> drawRect(col, Offset(i * sw, 0f), Size(sw + 0.5f, size.height)) }
        val x = size.width * nowMinute / 1440f
        drawRoundRect(c.text, Offset(x - 1.5f, 0f), Size(3f, size.height), CornerRadius(2f))
    }
}

class LightViewModel(private val g: AppGraph) : ViewModel() {
    private data class Edit(val points: List<LightPoint>? = null, val name: String? = null, val selected: Int = 0, val channel: Int = LightChannel.ROYAL.ordinal)

    private val edit = MutableStateFlow(Edit())
    private var saved: LightSchedule? = null

    val state: StateFlow<LightUi> = combine(g.lights.active(), edit, com.reeflive.app.ui.dashboard.minuteTicker()) { s, e, now ->
        saved = s
        val pts = LightEdit.sorted(e.points ?: s.points)
        val zone = java.time.ZoneId.systemDefault()
        val t = java.time.Instant.ofEpochMilli(now).atZone(zone)
        val pp = LightEdit.photoperiodMinutes(pts)
        LightUi(
            loading = false,
            name = e.name ?: s.name,
            points = pts,
            selected = e.selected.coerceIn(0, (pts.size - 1).coerceAtLeast(0)),
            channel = e.channel,
            dirty = e.points != null && (e.points != s.points || (e.name != null && e.name != s.name)),
            photoperiod = "${pp / 60} h" + if (pp % 60 != 0) " ${pp % 60} min" else "",
            nowMinute = t.hour * 60 + t.minute,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LightUi())

    private fun pts() = state.value.points

    private fun custom(points: List<LightPoint>, selected: Int = edit.value.selected) {
        val e = edit.value
        edit.value = e.copy(points = points, selected = selected, name = if (e.name != null && LightCurve.PRESETS[e.name] == points) e.name else "Custom")
    }

    fun select(i: Int) { edit.value = edit.value.copy(selected = i) }
    fun channel(ch: Int) { edit.value = edit.value.copy(channel = ch) }

    fun drag(i: Int, minute: Int, level: Double) {
        val moved = LightEdit.move(pts(), i, minute)
        custom(LightEdit.setLevel(moved, i, edit.value.channel, level), i)
    }

    fun level(ch: Int, v: Double) = custom(LightEdit.setLevel(pts(), state.value.selected, ch, v))

    fun time(minute: Int) = custom(LightEdit.move(pts(), state.value.selected, minute))

    /** Adds a point halfway to the next one (or 30 min later). */
    fun addPoint(onFail: () -> Unit) {
        val p = pts()
        val i = state.value.selected
        val cur = p.getOrNull(i)?.minuteOfDay ?: 0
        val next = p.getOrNull(i + 1)?.minuteOfDay ?: (cur + 60).coerceAtMost(1439)
        val r = LightEdit.insert(p, (cur + next) / 2) ?: LightEdit.insert(p, (cur + 30).coerceAtMost(1439))
        if (r == null) onFail() else custom(r.first, r.second)
    }

    fun removePoint() {
        val i = state.value.selected
        custom(LightEdit.remove(pts(), i), (i - 1).coerceAtLeast(0))
    }

    fun preset(name: String) {
        val p = LightCurve.PRESETS[name] ?: return
        edit.value = edit.value.copy(points = p, name = name, selected = 0)
    }

    fun revert() { edit.value = Edit(channel = edit.value.channel) }

    fun save(onDone: () -> Unit) {
        val st = state.value
        viewModelScope.launch {
            val current = saved ?: g.lights.activeOnce()
            if (current.id == com.reeflive.app.data.repo.LightScheduleRepository.DEFAULT.id) g.lights.applyPreset(st.name, st.points)
            else g.lights.save(current.copy(name = st.name, points = st.points))
            edit.value = Edit(channel = edit.value.channel, selected = st.selected)
            onDone()
        }
    }
}

@Composable
fun LightScheduleRoute(onBack: () -> Unit) {
    val vm = reefViewModel { LightViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var pickTime by remember { mutableStateOf(false) }
    LightScheduleContent(
        state,
        LightActions(
            onBack = onBack,
            onSelect = vm::select,
            onChannel = vm::channel,
            onDrag = vm::drag,
            onLevel = vm::level,
            onPickTime = { pickTime = true },
            onAddPoint = { vm.addPoint { scope.launch { snackbar.showSnackbar("Too close to another point") } } },
            onRemovePoint = vm::removePoint,
            onPreset = { haptics.tick(); vm.preset(it) },
            onSave = { haptics.confirm(); vm.save { scope.launch { snackbar.showSnackbar("Light schedule saved") } } },
            onRevert = vm::revert,
        ),
    )
    val p = state.points.getOrNull(state.selected)
    if (pickTime && p != null) {
        TimeOfDayDialog(p.minuteOfDay / 60, p.minuteOfDay % 60, title = "Point time", onPick = { h, m ->
            vm.time(h * 60 + m)
            pickTime = false
        }, onDismiss = { pickTime = false })
    }
}
