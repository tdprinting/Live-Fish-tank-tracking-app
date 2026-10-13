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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.reeflive.app.render.TankProjection
import com.reeflive.app.ui.components.Glyph
import com.reeflive.app.ui.theme.ReefTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * Carrying a supply from the shelf into the tank (UI_STYLE §10.6): after a prop is picked the camera glides back to
 * the tank and the prop floats in the player's hand at the bottom of the screen. Drag it over the tank (the front glass
 * glows) and let go, or just tap the tank, to use it there: food drops in at that spot, the knocker knocks there, the
 * rest do their usual thing. Letting go off the tank, or "Put back", returns it to the shelf.
 */

/** Tank interior on the front glass (cm): the drop zone. */
private const val GLASS_Z = 33.8f
private const val TANK_L = 121.9f
private const val TANK_TOP = 61f

/**
 * Solves which front-glass point (tank cm x, y on z = [z]) is under view pixel ([sx], [sy]) with a few Newton steps
 * on the camera projection (a homography, so it converges in 3–4). Writes x, y into [out]; false if it did not
 * converge (point behind the camera or off at a grazing angle).
 */
fun glassPointAt(projection: TankProjection, sx: Float, sy: Float, out: FloatArray, z: Float = GLASS_Z): Boolean {
    val p = FloatArray(3)
    var x = TANK_L / 2; var y = TANK_TOP / 2
    val h = 1f
    repeat(8) {
        if (!projection.project(x, y, z, p)) return false
        val fx = p[0] - sx; val fy = p[1] - sy
        if (abs(fx) < 0.5f && abs(fy) < 0.5f) { out[0] = x; out[1] = y; return true }
        val px = p[0]; val py = p[1]
        if (!projection.project(x + h, y, z, p)) return false
        val a = (p[0] - px) / h; val c = (p[1] - py) / h
        if (!projection.project(x, y + h, z, p)) return false
        val b = (p[0] - px) / h; val d = (p[1] - py) / h
        val det = a * d - b * c
        if (abs(det) < 1e-6f) return false
        x -= (d * fx - b * fy) / det
        y -= (-c * fx + a * fy) / det
        x = x.coerceIn(-400f, 500f); y = y.coerceIn(-400f, 400f)
    }
    if (!projection.project(x, y, z, p)) return false
    out[0] = x; out[1] = y
    return abs(p[0] - sx) < 3f && abs(p[1] - sy) < 3f
}

/** True when tank point ([x], [y]) on the front glass lies inside the tank. */
fun insideTank(x: Float, y: Float): Boolean = x in 0f..TANK_L && y in 0f..TANK_TOP

/**
 * The carried prop and its drop gesture, over the whole screen (lay it out at the GL view's bounds). [onDrop] gets the
 * front-glass point (tank cm) the prop was let go over; [onPutBack] runs when it is let go off the tank or put back.
 */
@Composable
fun SupplyCarryLayer(
    item: SupplyItem,
    projection: TankProjection,
    onDrop: (SupplyItem, x: Float, y: Float) -> Unit,
    onPutBack: (SupplyItem) -> Unit,
    bottomClearance: Dp,
) {
    val s = LiveTheme.style
    val reduce = ReefTheme.reduceMotion
    val density = LocalDensity.current
    val drop by rememberUpdatedState(onDrop)
    val back by rememberUpdatedState(onPutBack)
    val lift = with(density) { 56.dp.toPx() }
    var finger by remember(item) { mutableStateOf<Offset?>(null) }
    var over by remember(item) { mutableStateOf(false) }
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(item) { while (true) withFrameNanos { frame = it } }
    val glass = remember { FloatArray(2) }
    val corner = remember { FloatArray(3) }
    val bob = if (reduce) 0f else rememberInfiniteTransition(label = "carryBob").animateFloat(-5f, 5f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "bob").value
    val pulse = if (reduce) 1f else rememberInfiniteTransition(label = "carryPulse").animateFloat(0.35f, 0.9f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse").value
    val grow by animateFloatAsState(if (finger != null) 1.15f else 1f, liveSpec(reduce), label = "carryGrow")
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val restX = constraints.maxWidth / 2f
        val restY = constraints.maxHeight - with(density) { (bottomClearance + 120.dp).toPx() }
        // the drop gesture: wherever the finger goes down the prop jumps into it; let go to use it there
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(item) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        finger = down.position
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
                        if (hit) drop(item, glass[0], glass[1]) else back(item)
                    }
                },
        ) {
            // the tank's front glass glows as the drop zone (brighter while the prop is over it)
            Canvas(Modifier.fillMaxSize()) {
                frame
                val path = Path()
                var ok = true
                val pts = arrayOf(0f to 3f, TANK_L to 3f, TANK_L to 56f, 0f to 56f)
                pts.forEachIndexed { i, (x, y) ->
                    if (!projection.project(x, y, GLASS_Z, corner)) ok = false
                    if (i == 0) path.moveTo(corner[0], corner[1]) else path.lineTo(corner[0], corner[1])
                }
                path.close()
                if (ok) {
                    val tone = item.row.tone
                    drawPath(path, tone.copy(alpha = if (over) 0.22f else 0.08f))
                    drawPath(path, tone.copy(alpha = if (over) 1f else pulse), style = Stroke(5.dp.toPx()))
                }
            }
            val at = finger?.let { Offset(it.x, it.y - lift) } ?: Offset(restX, restY + bob * density.density)
            Box(
                Modifier
                    .offset { IntOffset((at.x - 40.dp.toPx()).roundToInt(), (at.y - 40.dp.toPx()).roundToInt()) }
                    .size(80.dp)
                    .graphicsLayer { scaleX = grow; scaleY = grow }
                    .clip(CircleShape)
                    .background(s.panel)
                    .border(3.dp, item.row.tone, CircleShape)
                    .semantics { contentDescription = "${item.label}: drag it over the tank and let go" },
                contentAlignment = Alignment.Center,
            ) {
                val k = item.food
                if (k != null) FoodPicture(k, size = 60.dp) else Glyph(item.glyph, size = 44.dp, tint = s.label, accent = item.row.tone)
            }
        }
        // hint and "Put back" (a sibling above the gesture layer, so its taps stay its own)
        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = bottomClearance).padding(horizontal = 12.dp).widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LivePanel(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                LiveHelp(if (item.food != null) "Drag the ${item.label.lowercase()} over the tank and let go to feed there." else "Drag it over the tank and let go to use it.")
            }
            LivePill("Put back", { onPutBack(item) }, tone = Candy.white)
        }
    }
}
