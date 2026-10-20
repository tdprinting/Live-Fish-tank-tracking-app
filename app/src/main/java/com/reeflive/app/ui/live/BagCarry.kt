package com.reeflive.app.ui.live

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reeflive.app.render.TankProjection
import com.reeflive.app.ui.components.SpeciesAvatar
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.core.sim.BagPose
import com.reeflive.core.sim.SimSnapshot
import com.reeflive.core.sim.StockGroup
import com.reeflive.core.sim.StockSize
import com.reeflive.core.sim.TankDims
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

/*
 * New livestock arrives the way it does from a shop: in a sealed bag. Tapping an animal in Add life puts the bag in the
 * keeper's hand; drag it over the tank and let go (or just tap the tank) and it drops onto the water there with a
 * splash. It floats for 30 sim minutes while the bag water evens out to the tank's temperature, a little timer rides
 * over it, and "Hurry up" fast-forwards the clock. Then it tips over and the animals swim out (core AcclimationBags).
 */

/** The bag in the keeper's hand: [count] × [card] at [size]. */
data class BagCarry(val card: StockCard, val count: Int, val size: StockSize) {
    val label: String get() = if (count == 1) card.name else "$count × ${card.name}"
    val tone: Color get() = when (card.group) { StockGroup.FISH -> Candy.orange; StockGroup.INVERTS -> Candy.pink; StockGroup.CORALS -> Candy.green }
}

/**
 * The carried bag and its drop gesture, over the whole screen (lay it out at the GL view's bounds). [onDrop] gets the
 * tank x (cm along the front glass) the bag was let go over; [onCancel] puts it away.
 */
@Composable
fun BagCarryLayer(
    bag: BagCarry,
    projection: TankProjection,
    onDrop: (BagCarry, x: Float) -> Unit,
    onCancel: () -> Unit,
    bottomClearance: Dp,
) {
    val s = LiveTheme.style
    val reduce = ReefTheme.reduceMotion
    val density = LocalDensity.current
    val drop by rememberUpdatedState(onDrop)
    val lift = with(density) { 64.dp.toPx() }
    var finger by remember(bag) { mutableStateOf<Offset?>(null) }
    var over by remember(bag) { mutableStateOf(false) }
    var missed by remember(bag) { mutableStateOf(false) }
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(bag) { while (true) withFrameNanos { frame = it } }
    val glass = remember { FloatArray(2) }
    val corner = remember { FloatArray(3) }
    val sway = if (reduce) 0f else rememberInfiniteTransition(label = "bagSway").animateFloat(-6f, 6f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "sway").value
    val pulse = if (reduce) 1f else rememberInfiniteTransition(label = "bagPulse").animateFloat(0.35f, 0.9f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse").value
    val grow by animateFloatAsState(if (finger != null) 1.12f else 1f, liveSpec(reduce), label = "bagGrow")
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val restX = constraints.maxWidth / 2f
        val restY = constraints.maxHeight - with(density) { (bottomClearance + 190.dp).toPx() }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(bag) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        finger = down.position
                        missed = false
                        var pos = down.position
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (ch.positionChange() != Offset.Zero) ch.consume()
                            pos = ch.position
                            finger = pos
                            over = glassPointAt(projection, pos.x, pos.y - lift, glass) && insideTank(glass[0], glass[1])
                            if (!ch.pressed) break
                        }
                        finger = null
                        val hit = glassPointAt(projection, pos.x, pos.y - lift, glass) && insideTank(glass[0], glass[1])
                        over = false
                        missed = !hit
                        if (hit) drop(bag, glass[0])
                    }
                },
        ) {
            // the water surface glows as the drop zone: a band along the top of the front glass
            Canvas(Modifier.fillMaxSize()) {
                frame
                val path = Path()
                var ok = true
                val pts = arrayOf(0f to com.reeflive.app.render.Camera.WATER_Y - 12f, com.reeflive.app.render.Camera.TANK[0] to com.reeflive.app.render.Camera.WATER_Y - 12f, com.reeflive.app.render.Camera.TANK[0] to com.reeflive.app.render.Camera.WATER_Y + 2f, 0f to com.reeflive.app.render.Camera.WATER_Y + 2f)
                pts.forEachIndexed { i, (x, y) ->
                    if (!projection.project(x, y, com.reeflive.app.render.Camera.TANK[2] + 0.8f, corner)) ok = false
                    if (i == 0) path.moveTo(corner[0], corner[1]) else path.lineTo(corner[0], corner[1])
                }
                path.close()
                if (ok) {
                    drawPath(path, Candy.cyan.copy(alpha = if (over) 0.25f else 0.08f))
                    drawPath(path, Candy.cyan.copy(alpha = if (over) 1f else pulse), style = Stroke(5.dp.toPx()))
                }
            }
            val at = finger?.let { Offset(it.x, it.y - lift) } ?: Offset(restX, restY)
            Column(
                Modifier.offset { IntOffset((at.x - 70.dp.toPx()).roundToInt(), (at.y - 60.dp.toPx()).roundToInt()) }.width(140.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    Modifier
                        .size(96.dp, 120.dp)
                        .graphicsLayer { scaleX = grow; scaleY = grow; rotationZ = if (finger == null) sway else 0f; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.1f) }
                        .semantics { contentDescription = "Bag with ${bag.label}: drag it over the tank and let go" },
                ) {
                    ShopBag(bag, Modifier.fillMaxSize())
                }
                BagChip(if (over) "Let go: in it goes!" else bag.label, bag.tone, strong = true)
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = bottomClearance).padding(horizontal = 12.dp).widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LivePanel(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), tint = if (missed) Candy.yellow else null) {
                LiveHelp(
                    if (missed) "Missed the tank! Let go over the glowing water line."
                    else "Drag the bag onto the tank and let go. It floats while the water in it warms up.",
                )
            }
            LivePill("Cancel", onCancel, tone = Candy.white)
        }
    }
}

/** A clear shop bag with water, the animal inside, a knot and a rubber band; ×N when there are several. */
@Composable
fun ShopBag(bag: BagCarry, modifier: Modifier = Modifier) {
    val s = LiveTheme.style
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val body = Path().apply {
                moveTo(w * 0.44f, h * 0.12f)
                cubicTo(w * 0.30f, h * 0.30f, w * 0.06f, h * 0.42f, w * 0.06f, h * 0.68f)
                cubicTo(w * 0.06f, h * 0.92f, w * 0.30f, h * 0.98f, w * 0.5f, h * 0.98f)
                cubicTo(w * 0.70f, h * 0.98f, w * 0.94f, h * 0.92f, w * 0.94f, h * 0.68f)
                cubicTo(w * 0.94f, h * 0.42f, w * 0.70f, h * 0.30f, w * 0.56f, h * 0.12f)
                close()
            }
            drawPath(body, Color.White.copy(alpha = 0.22f))
            // the water fills the round bottom
            val water = Path().apply {
                moveTo(w * 0.1f, h * 0.55f)
                lineTo(w * 0.9f, h * 0.55f)
                cubicTo(w * 0.96f, h * 0.7f, w * 0.92f, h * 0.95f, w * 0.5f, h * 0.96f)
                cubicTo(w * 0.08f, h * 0.95f, w * 0.04f, h * 0.7f, w * 0.1f, h * 0.55f)
                close()
            }
            drawPath(water, Candy.cyan.copy(alpha = 0.45f))
            drawPath(body, s.outline, style = Stroke(s.outlineWidth.toPx()))
            // knot, band and the twisted top
            drawOval(s.outline, Offset(w * 0.40f, h * 0.02f), Size(w * 0.2f, h * 0.1f))
            drawRoundRect(Candy.yellow, Offset(w * 0.40f, h * 0.11f), Size(w * 0.2f, h * 0.05f), CornerRadius(4f, 4f))
            // a shine down the side
            drawLine(Color.White.copy(alpha = 0.7f), Offset(w * 0.2f, h * 0.5f), Offset(w * 0.16f, h * 0.72f), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)) {
            SpeciesAvatar(bag.card.speciesId, bag.card.kind, bag.card.colors, size = 48.dp)
        }
        if (bag.count > 1) {
            Box(Modifier.align(Alignment.TopEnd)) { BagChip("×${bag.count}", bag.tone, strong = true) }
        }
    }
}

/**
 * A timer over every floating bag (minutes left on a ring, the bag and tank temperatures) and, at the bottom, the
 * "Hurry up" button that fast-forwards the clock until the bags open. Draws over the tank; only the panel takes taps.
 */
@Composable
fun BagTimerLayer(
    snapshots: StateFlow<SimSnapshot>,
    projection: TankProjection,
    bags: List<BagPose>,
    hurry: Boolean,
    onHurry: (Boolean) -> Unit,
    bottomClearance: Dp,
) {
    val s = LiveTheme.style
    var frameMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) withFrameMillis { frameMs = it } }
    val measurer = rememberTextMeasurer()
    val textStyle = s.buttonText.copy(color = s.onCandy, fontSize = 13.sp)
    val smallStyle = s.buttonText.copy(color = s.onCandy, fontSize = 11.sp)
    val layouts = remember { HashMap<String, TextLayoutResult>() }
    val pt = remember { FloatArray(3) }
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            if (frameMs == 0L) return@Canvas
            val snap = snapshots.value
            if (layouts.size > 64) layouts.clear()
            val line = s.outlineWidth.toPx()
            for (b in snap.bags) {
                if (b.phase >= 2) continue
                // under the bag, in the water (above it is the tank rim and the HUD)
                if (!projection.project(b.x, b.y - b.radius * 1.8f, b.z, pt)) continue
                val ring = 15.dp.toPx()
                val mins = b.minutesLeft
                val t1 = if (b.phase == 0) "Splash!" else if (mins >= 1f) "${mins.roundToInt()} min" else "Almost!"
                val t2 = "%.1f° → %.1f°".format(b.bagTempC, b.tankTempC)
                val l1 = layouts.getOrPut(t1) { measurer.measure(t1, textStyle) }
                val l2 = layouts.getOrPut(t2) { measurer.measure(t2, smallStyle) }
                val pad = 8.dp.toPx()
                val bw = ring * 2 + pad * 3 + maxOf(l1.size.width, l2.size.width)
                val bh = maxOf(ring * 2, (l1.size.height + l2.size.height).toFloat()) + pad * 2
                val left = (pt[0] - bw / 2).coerceIn(4f, size.width - bw - 4f)
                val top = (pt[1] + 6.dp.toPx()).coerceIn(4f, size.height - bh - 4f)
                drawRoundRect(Color(0xFFF2F6FF), Offset(left, top), Size(bw, bh), CornerRadius(bh / 2, bh / 2))
                drawRoundRect(s.outline, Offset(left, top), Size(bw, bh), CornerRadius(bh / 2, bh / 2), style = Stroke(line))
                // the float as a ring that fills up
                val c = Offset(left + pad + ring, top + bh / 2)
                drawCircle(Candy.cyan.copy(alpha = 0.25f), ring - 3.dp.toPx(), c, style = Stroke(5.dp.toPx()))
                drawArc(
                    Candy.cyan, -90f, 360f * b.progress, false, Offset(c.x - ring + 3.dp.toPx(), c.y - ring + 3.dp.toPx()),
                    Size((ring - 3.dp.toPx()) * 2, (ring - 3.dp.toPx()) * 2), style = Stroke(5.dp.toPx(), cap = StrokeCap.Round),
                )
                val tx = left + pad * 2 + ring * 2
                drawText(l1, topLeft = Offset(tx, top + pad))
                drawText(l2, topLeft = Offset(tx, top + pad + l1.size.height))
            }
        }
        if (bags.any { it.phase <= 1 }) {
            val floating = bags.count { it.phase <= 1 }
            val mins = bags.filter { it.phase <= 1 }.maxOf { it.minutesLeft }.roundToInt()
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = bottomClearance + 8.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LivePanel(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(
                        (if (floating == 1) "Bag floating" else "$floating bags floating") + if (mins >= 1) " · out in $mins min" else " · opening",
                        style = s.buttonText, color = s.label,
                    )
                }
                LivePill(if (hurry) "Normal speed" else "⏩ Hurry up", { onHurry(!hurry) }, tone = if (hurry) Candy.white else Candy.yellow)
            }
        }
    }
}

@Composable
private fun BagChip(text: String, tone: Color, strong: Boolean) {
    val s = LiveTheme.style
    val shape = RoundedCornerShape(50)
    Text(
        text,
        style = s.buttonText.copy(fontSize = 14.sp),
        color = if (strong) s.onCandy else s.label,
        maxLines = 1,
        modifier = Modifier
            .clip(shape)
            .background(if (strong) tone else s.panel)
            .border(2.dp, s.outline, shape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
