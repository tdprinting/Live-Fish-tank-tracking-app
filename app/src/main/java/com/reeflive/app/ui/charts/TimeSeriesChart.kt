package com.reeflive.app.ui.charts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.reeflive.app.ui.common.TimeText
import com.reeflive.app.ui.components.StatusPill
import com.reeflive.app.ui.components.rememberHaptics
import com.reeflive.app.ui.theme.ReefColors
import com.reeflive.app.ui.theme.ReefShapes
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.app.ui.theme.status
import com.reeflive.core.model.EventType
import com.reeflive.core.params.Range
import com.reeflive.core.status.Status
import com.reeflive.core.util.Fmt
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class GestureMode { TAP, SCRUB, PAN, ZOOM, CANCEL }

/**
 * The big parameter chart (UI_STYLE §9.1): ideal/acceptable bands, hatched zones beyond acceptable, dashed
 * danger thresholds, glowing line with status-colored points (gaps > 3× the median interval dashed),
 * an optional overlay series on a right axis, event markers on the baseline, long-press scrub with a glass
 * tooltip (time, value, status, kit), horizontal pinch zoom (min 3 days), pan, and double-tap reset.
 * All values are in display units. The window [windowStart, windowEnd] comes from the range selector.
 */
@Composable
fun TimeSeriesChart(
    points: List<ChartPoint>,
    bands: ChartBands?,
    windowStart: Long,
    windowEnd: Long,
    precision: Int,
    unit: String,
    modifier: Modifier = Modifier,
    height: Dp = 240.dp,
    events: List<ChartEvent> = emptyList(),
    overlay: OverlaySeries? = null,
    interactive: Boolean = true,
    showAxes: Boolean = true,
    emptyText: String = "No readings yet",
) {
    val c = ReefTheme.colors
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val haptics = rememberHaptics()
    val labelStyle = MaterialTheme.typography.labelMedium

    var view by remember(windowStart, windowEnd) { mutableStateOf(windowStart to windowEnd) }
    var scrubT by remember { mutableStateOf<Long?>(null) }
    var scrubIndex by remember { mutableStateOf(-1) }
    var selectedEvent by remember { mutableStateOf<ChartEvent?>(null) }
    val viewState = rememberUpdatedState(view)
    val pointsState = rememberUpdatedState(points)
    val eventsState = rememberUpdatedState(events)
    val grow by animateFloatAsState(if (scrubT != null) 1f else 0f, label = "scrubGrow")

    val padLeft = with(density) { (if (showAxes) 40.dp else 0.dp).toPx() }
    val padRight = with(density) { (if (overlay != null && showAxes) 40.dp else 8.dp).toPx() }
    val padTop = with(density) { 8.dp.toPx() }
    val padBottom = with(density) { (if (showAxes) 30.dp else if (events.isNotEmpty()) 14.dp else 4.dp).toPx() }
    val markerHit = with(density) { 18.dp.toPx() }
    val maxSpan = maxOf(windowEnd - windowStart, ChartMath.MIN_SPAN_MS) * 2
    val dataLo = minOf(points.firstOrNull()?.t ?: windowStart, windowStart)

    fun tAt(x: Float, width: Float): Long {
        val v = viewState.value
        val w = (width - padLeft - padRight).coerceAtLeast(1f)
        return v.first + (((x - padLeft) / w) * (v.second - v.first)).toLong()
    }

    val a11y = remember(points, unit) {
        if (points.isEmpty()) emptyText
        else "Chart with ${points.size} readings. " + points.takeLast(10).reversed().joinToString("; ") {
            "${Fmt.num(it.v, precision)} $unit on ${TimeText.full(it.t)}"
        }
    }

    BoxWithConstraints(modifier.fillMaxWidth().height(height)) {
        val widthPx = with(density) { maxWidth.toPx() }
        val gestures = if (!interactive) Modifier else Modifier.pointerInput(windowStart, windowEnd, widthPx) {
            val slop = viewConfiguration.touchSlop
            val longPress = viewConfiguration.longPressTimeoutMillis
            var lastTapUp = 0L
            var lastTapX = -1000f
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (down.uptimeMillis - lastTapUp < 300 && abs(down.position.x - lastTapX) < slop * 4) {
                    // double tap → reset the window
                    view = windowStart to windowEnd
                    scrubT = null
                    selectedEvent = null
                    lastTapUp = 0L
                    do {
                        val e = awaitPointerEvent()
                    } while (e.changes.any { it.pressed })
                    return@awaitEachGesture
                }
                var moved = Offset.Zero
                val mode = withTimeoutOrNull(longPress) {
                    var m: GestureMode? = null
                    while (m == null) {
                        val e = awaitPointerEvent()
                        val pressed = e.changes.filter { it.pressed }
                        if (pressed.size >= 2) {
                            m = GestureMode.ZOOM
                        } else if (pressed.isEmpty()) {
                            m = GestureMode.TAP
                        } else {
                            val ch: PointerInputChange = e.changes.firstOrNull { it.id == down.id } ?: pressed.first()
                            moved += ch.positionChange()
                            if (moved.getDistance() > slop) m = if (abs(moved.x) > abs(moved.y)) GestureMode.PAN else GestureMode.CANCEL
                        }
                    }
                    m
                } ?: GestureMode.SCRUB
                when (mode) {
                    GestureMode.CANCEL -> Unit
                    GestureMode.TAP -> {
                        lastTapUp = down.uptimeMillis
                        lastTapX = down.position.x
                        // tap near the baseline selects an event marker
                        val baseline = size.height - padBottom
                        val evs = eventsState.value
                        selectedEvent = if (down.position.y > baseline - markerHit && evs.isNotEmpty()) {
                            val t = tAt(down.position.x, size.width.toFloat())
                            val nearest = evs.minByOrNull { abs(it.t - t) }
                            val v = viewState.value
                            val pxPerMs = (size.width - padLeft - padRight) / (v.second - v.first).coerceAtLeast(1L).toFloat()
                            nearest?.takeIf { abs(it.t - t) * pxPerMs < markerHit }
                        } else null
                        scrubT = null
                    }
                    GestureMode.SCRUB -> {
                        selectedEvent = null
                        var x = down.position.x + moved.x
                        fun update() {
                            val t = tAt(x, size.width.toFloat())
                            val i = ChartMath.nearest(pointsState.value, t)
                            if (i != scrubIndex && i >= 0) haptics.tick()
                            scrubIndex = i
                            scrubT = t
                        }
                        update()
                        while (true) {
                            val e = awaitPointerEvent()
                            val ch = e.changes.firstOrNull { it.id == down.id }
                            if (ch == null || !ch.pressed) break
                            if (ch.positionChanged()) {
                                x = ch.position.x
                                ch.consume()
                                update()
                            }
                        }
                        scrubT = null
                        scrubIndex = -1
                    }
                    GestureMode.PAN, GestureMode.ZOOM -> {
                        scrubT = null
                        selectedEvent = null
                        while (true) {
                            val e = awaitPointerEvent()
                            val pressed = e.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            val v = viewState.value
                            val w = (size.width - padLeft - padRight).coerceAtLeast(1f)
                            val msPerPx = (v.second - v.first) / w
                            var n = v
                            if (pressed.size >= 2) {
                                val z = e.calculateZoom()
                                if (z != 1f) {
                                    val anchor = tAt(e.calculateCentroid(useCurrent = true).x, size.width.toFloat())
                                    n = ChartMath.zoom(n.first, n.second, z, anchor, maxSpan = maxSpan)
                                }
                            }
                            val dx = e.calculatePan().x
                            val shift = (-dx * msPerPx).toLong()
                            n = (n.first + shift) to (n.second + shift)
                            val hi = maxOf(windowEnd, System.currentTimeMillis()) + (n.second - n.first) / 10
                            view = ChartMath.clampWindow(n.first, n.second, dataLo - (n.second - n.first) / 10, hi)
                            e.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    }
                }
            }
        }

        Canvas(Modifier.fillMaxSize().then(gestures).semantics { contentDescription = a11y }) {
            val (t0, t1) = view
            val plotW = size.width - padLeft - padRight
            val plotH = size.height - padTop - padBottom
            if (plotW <= 0f || plotH <= 0f) return@Canvas
            val span = t1 - t0
            val visible = points.filterIndexed { i, p ->
                p.t in t0..t1 ||
                    (i + 1 < points.size && points[i + 1].t in t0..t1 && p.t < t0) ||
                    (i > 0 && points[i - 1].t in t0..t1 && p.t > t1)
            }
            val dom = ChartMath.domain(visible.filter { it.t in (t0 - span / 10)..(t1 + span / 10) }.map { it.v }, bands?.acceptable)
            val s = ChartScale(t0, t1, dom.lo, dom.hi, padLeft, padTop, plotW, plotH)

            if (bands != null) drawBands(s, bands, dom, c, empty = points.isEmpty())
            if (showAxes) drawYAxis(s, dom, precision, c, measurer, labelStyle)
            if (showAxes) drawXAxis(s, c, measurer, labelStyle)

            clipRect(s.left - 6.dp.toPx(), s.top - 6.dp.toPx(), s.right + 6.dp.toPx(), s.bottom + 6.dp.toPx()) {
                if (overlay != null && overlay.points.isNotEmpty()) drawOverlay(s, overlay, c, measurer, labelStyle, showAxes)
                drawSeries(s, visible, c)
            }

            if (events.isNotEmpty()) drawEvents(s, events, c, selectedEvent)

            val st = scrubT
            if (st != null && scrubIndex in points.indices) {
                val p = points[scrubIndex]
                val x = s.x(p.t)
                drawLine(c.text.copy(alpha = 0.4f), Offset(x, s.top), Offset(x, s.bottom), strokeWidth = 1.dp.toPx())
                val r = (2.5f + 1.5f * grow).dp.toPx()
                drawCircle(c.status(p.status).copy(alpha = 0.3f), r * 2f, Offset(x, s.y(p.v)))
                drawCircle(c.status(p.status), r, Offset(x, s.y(p.v)))
            }

            if (points.isEmpty()) {
                val layout = measurer.measure(emptyText, labelStyle.copy(color = c.textSecondary, textAlign = TextAlign.Center))
                drawText(layout, topLeft = Offset(s.left + (plotW - layout.size.width) / 2f, s.top + (plotH - layout.size.height) / 2f))
            }
        }

        // Scrub tooltip
        val idx = scrubIndex
        if (scrubT != null && idx in points.indices) {
            val p = points[idx]
            val (t0, t1) = view
            val plotW = widthPx - padLeft - padRight
            val xPx = padLeft + (p.t - t0).toFloat() / (t1 - t0).coerceAtLeast(1L) * plotW
            val tipW = with(density) { 168.dp.toPx() }
            val left = (xPx - tipW / 2f).coerceIn(0f, (widthPx - tipW).coerceAtLeast(0f))
            ChartTooltip(
                title = TimeText.full(p.t),
                value = "${Fmt.num(p.v, precision)} $unit".trim(),
                status = p.status,
                kit = p.kit,
                modifier = Modifier.offset { IntOffset(left.roundToInt(), 0) }.width(168.dp),
            )
        }
        val ev = selectedEvent
        if (ev != null) {
            val (t0, t1) = view
            val plotW = widthPx - padLeft - padRight
            val xPx = padLeft + (ev.t - t0).toFloat() / (t1 - t0).coerceAtLeast(1L) * plotW
            val tipW = with(density) { 180.dp.toPx() }
            val left = (xPx - tipW / 2f).coerceIn(0f, (widthPx - tipW).coerceAtLeast(0f))
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .offset { IntOffset(left.roundToInt(), -with(density) { 34.dp.toPx() }.roundToInt()) }
                    .widthIn(max = 180.dp)
                    .clip(ReefShapes.overlay)
                    .background(c.sheet)
                    .border(1.dp, c.glassBorder, ReefShapes.overlay)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(ev.title, style = MaterialTheme.typography.labelLarge, color = c.text, maxLines = 2)
                Text(TimeText.full(ev.t), style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
            }
        }
    }
}

@Composable
private fun ChartTooltip(title: String, value: String, status: Status, kit: String?, modifier: Modifier) {
    val c = ReefTheme.colors
    Column(
        modifier
            .clip(ReefShapes.overlay)
            .background(c.sheet)
            .border(1.dp, c.glassBorder, ReefShapes.overlay)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, style = ReefTheme.numbers.body, color = c.text, modifier = Modifier.weight(1f))
            if (status != Status.UNKNOWN) StatusPill(status)
        }
        if (!kit.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text("Kit: $kit", style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
        }
    }
}

private fun DrawScope.drawBands(s: ChartScale, b: ChartBands, dom: Range, c: ReefColors, empty: Boolean) {
    val acc = b.acceptable
    val ideal = b.ideal
    val dangerFill = c.danger.copy(alpha = 0.10f)
    val hatch = c.danger.copy(alpha = 0.20f)
    // beyond acceptable: red fill + 45° hatch
    if (dom.hi > acc.hi) hatched(s.left, s.y(dom.hi), s.right, s.y(acc.hi), dangerFill, hatch)
    if (dom.lo < acc.lo) hatched(s.left, s.y(acc.lo), s.right, s.y(dom.lo), dangerFill, hatch)
    if (empty) {
        val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
        for (r in listOf(acc to c.caution, ideal to c.good)) {
            val top = s.y(r.first.hi)
            val bot = s.y(r.first.lo)
            drawRect(r.second.copy(alpha = 0.5f), Offset(s.left, top), Size(s.width, (bot - top).coerceAtLeast(1f)), style = Stroke(1.dp.toPx(), pathEffect = dash))
        }
        return
    }
    bandRect(s, acc, c.caution.copy(alpha = 0.08f))
    bandRect(s, ideal, c.good.copy(alpha = 0.16f))
    val edge = 1.dp.toPx()
    for (v in listOf(acc.lo, acc.hi)) drawLine(c.caution.copy(alpha = 0.4f), Offset(s.left, s.y(v)), Offset(s.right, s.y(v)), edge)
    for (v in listOf(ideal.lo, ideal.hi)) drawLine(c.good.copy(alpha = 0.4f), Offset(s.left, s.y(v)), Offset(s.right, s.y(v)), edge)
    val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
    for (v in listOfNotNull(b.dangerLow, b.dangerHigh)) {
        if (v in dom.lo..dom.hi) drawLine(c.danger.copy(alpha = 0.55f), Offset(s.left, s.y(v)), Offset(s.right, s.y(v)), edge, pathEffect = dash)
    }
}

private fun DrawScope.hatched(l: Float, t: Float, r: Float, b: Float, fill: Color, line: Color) {
    if (b - t < 0.5f) return
    drawRect(fill, Offset(l, t), Size(r - l, b - t))
    clipRect(l, t, r, b) {
        val step = 8.dp.toPx()
        val w = 2.dp.toPx()
        var x = l - (b - t)
        while (x < r) {
            drawLine(line, Offset(x, b), Offset(x + (b - t), t), w)
            x += step
        }
    }
}

private fun DrawScope.drawYAxis(s: ChartScale, dom: Range, precision: Int, c: ReefColors, m: TextMeasurer, style: TextStyle) {
    val ticks = ChartMath.niceTicks(dom, 4)
    val dec = maxOf(ChartMath.tickDecimals(ticks, precision), 0)
    val st = style.copy(color = c.textTertiary)
    for (v in ticks) {
        val y = s.y(v)
        if (y < s.top - 1 || y > s.bottom + 1) continue
        drawLine(c.glassBorder, Offset(s.left, y), Offset(s.right, y), 1.dp.toPx())
        val layout = m.measure(Fmt.num(v, dec), st)
        drawText(layout, topLeft = Offset(s.left - 6.dp.toPx() - layout.size.width, y - layout.size.height / 2f))
    }
}

private fun DrawScope.drawXAxis(s: ChartScale, c: ReefColors, m: TextMeasurer, style: TextStyle) {
    val st = style.copy(color = c.textTertiary)
    var lastRight = -1e9f
    for (tick in ChartMath.xTicks(s.t0, s.t1, TimeText.zone())) {
        val x = s.x(tick.t)
        val layout = m.measure(tick.label, st)
        val left = x - layout.size.width / 2f
        if (left < lastRight + 6.dp.toPx() || left < s.left - 12.dp.toPx() || left + layout.size.width > size.width) continue
        drawText(layout, topLeft = Offset(left, s.bottom + 12.dp.toPx()))
        lastRight = left + layout.size.width
    }
}

private fun DrawScope.drawSeries(s: ChartScale, pts: List<ChartPoint>, c: ReefColors) {
    if (pts.isEmpty()) return
    val line = c.primaryBright
    val gap = ChartMath.gapThreshold(pts)
    val offsets = pts.map { Offset(s.x(it.t), s.y(it.v)) }
    // split into solid runs; long gaps become dashed straight segments
    val runs = mutableListOf<MutableList<Offset>>(mutableListOf(offsets[0]))
    val dashed = mutableListOf<Pair<Offset, Offset>>()
    for (i in 1 until pts.size) {
        if (pts[i].t - pts[i - 1].t > gap) {
            dashed += offsets[i - 1] to offsets[i]
            runs += mutableListOf(offsets[i])
        } else runs.last() += offsets[i]
    }
    val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
    for ((a, b) in dashed) drawLine(line.copy(alpha = 0.7f), a, b, 2.dp.toPx(), cap = StrokeCap.Round, pathEffect = dash)
    val path = Path()
    for (run in runs) {
        if (run.size < 2) continue
        smoothPath(run, path)
        // soft glow: wide translucent strokes under the line
        drawPath(path, line.copy(alpha = 0.06f), style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, line.copy(alpha = 0.12f), style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, line, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    val r = 2.5.dp.toPx()
    val ring = 1.5.dp.toPx()
    pts.forEachIndexed { i, p ->
        val o = offsets[i]
        if (o.x < s.left - r || o.x > s.right + r) return@forEachIndexed
        drawCircle(c.bg, r + ring, o)
        drawCircle(c.status(p.status), r, o)
    }
}

private fun DrawScope.drawOverlay(s: ChartScale, o: OverlaySeries, c: ReefColors, m: TextMeasurer, style: TextStyle, axes: Boolean) {
    val inView = o.points.filter { it.t in s.t0..s.t1 }
    val dom = ChartMath.domain(inView.ifEmpty { o.points }.map { it.v }, null)
    val so = ChartScale(s.t0, s.t1, dom.lo, dom.hi, s.left, s.top, s.width, s.height)
    val offs = o.points.map { Offset(so.x(it.t), so.y(it.v)) }
    if (offs.size >= 2) drawPath(smoothPath(offs), c.anemone.copy(alpha = 0.9f), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    offs.forEach { drawCircle(c.anemone, 2.dp.toPx(), it) }
    if (axes) {
        val st = style.copy(color = c.anemone)
        for (v in ChartMath.niceTicks(dom, 4)) {
            val y = so.y(v)
            if (y < s.top || y > s.bottom) continue
            val layout = m.measure(Fmt.num(v, ChartMath.tickDecimals(ChartMath.niceTicks(dom, 4), o.precision)), st)
            drawText(layout, topLeft = Offset(s.right + 6.dp.toPx(), y - layout.size.height / 2f))
        }
    }
}

private fun DrawScope.drawEvents(s: ChartScale, events: List<ChartEvent>, c: ReefColors, selected: ChartEvent?) {
    val y = s.bottom + 5.dp.toPx()
    val r = 5.dp.toPx()
    for (e in events) {
        if (e.t < s.t0 || e.t > s.t1) continue
        val x = s.x(e.t)
        val k = if (e == selected) 1.4f else 1f
        when (e.type) {
            EventType.DOSE -> droplet(Offset(x, y), r * k, c.primaryBright)
            EventType.WATER_CHANGE -> wave(Offset(x, y), r * k, c.anemone)
            EventType.FEED -> drawCircle(c.coral, r * 0.7f * k, Offset(x, y))
            EventType.NOTE -> drawRect(c.sand, Offset(x - r * 0.65f * k, y - r * 0.65f * k), Size(r * 1.3f * k, r * 1.3f * k))
            else -> drawCircle(c.textTertiary, r * 0.5f * k, Offset(x, y))
        }
        if (e == selected) drawLine(c.text.copy(alpha = 0.3f), Offset(x, s.top), Offset(x, s.bottom), 1.dp.toPx())
    }
}

private fun DrawScope.droplet(center: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(center.x, center.y - r * 1.2f)
        cubicTo(center.x + r * 0.9f, center.y - r * 0.2f, center.x + r, center.y + r * 0.9f, center.x, center.y + r)
        cubicTo(center.x - r, center.y + r * 0.9f, center.x - r * 0.9f, center.y - r * 0.2f, center.x, center.y - r * 1.2f)
        close()
    }
    drawPath(p, color)
}

private fun DrawScope.wave(center: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(center.x - r, center.y)
        cubicTo(center.x - r * 0.5f, center.y - r, center.x, center.y - r, center.x, center.y)
        cubicTo(center.x, center.y + r, center.x + r * 0.5f, center.y + r, center.x + r, center.y)
    }
    drawPath(p, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
}
