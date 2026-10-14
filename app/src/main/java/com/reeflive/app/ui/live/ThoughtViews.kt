package com.reeflive.app.ui.live

import android.os.SystemClock
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import com.reeflive.app.render.TankProjection
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.core.sim.SimSnapshot
import com.reeflive.core.sim.Thought
import kotlinx.coroutines.flow.StateFlow

/**
 * "Thinking…" lines for the fish card and the follow bar (UI_STYLE §10.9): what the animal is doing, its next plan
 * and how it feels, in kid words from core (ai/Thoughts.kt). Each line is held ≥ 1.5 s ([ThoughtHold]) and cross-fades,
 * so the 4 Hz HUD never makes it flicker; a new plan or a new animal shows at once.
 */
@Composable
fun ThinkingBlock(id: String, thought: Thought, modifier: Modifier = Modifier, centered: Boolean = false) {
    val c = ReefTheme.colors
    val s = LiveTheme.style
    val reduce = ReefTheme.reduceMotion
    val hold = remember(id) { ThoughtHold() }
    val lines = hold.update(id, thought, SystemClock.uptimeMillis())
    if (lines.isEmpty) return
    val align = if (centered) TextAlign.Center else TextAlign.Start
    Column(
        modifier.semantics { contentDescription = "Thinking: ${lines.now} ${lines.next} ${lines.feeling}" },
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text("Thinking…", style = s.buttonText, color = s.labelDim, textAlign = align)
        Crossfade(lines.now, animationSpec = liveSpec(reduce, crossfade = true), label = "thinkNow") { t ->
            Text(t, style = MaterialTheme.typography.bodyMedium, color = c.text, textAlign = align, maxLines = 3, modifier = Modifier.fillMaxWidth())
        }
        if (lines.next.isNotBlank()) {
            Crossfade(lines.next, animationSpec = liveSpec(reduce, crossfade = true), label = "thinkNext") { t ->
                Text(t, style = MaterialTheme.typography.labelMedium, color = c.textSecondary, textAlign = align, maxLines = 2, modifier = Modifier.fillMaxWidth())
            }
        }
        Crossfade(lines.feeling, animationSpec = liveSpec(reduce, crossfade = true), label = "thinkFeel") { t ->
            Text(t, style = MaterialTheme.typography.labelMedium, color = c.textSecondary, textAlign = align, maxLines = 2, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * More → "Thought bubbles": small speech bubbles over at most three animals in the 3D view ([ThoughtBubblePicker]
 * chooses: fresh changes of plan and busy animals first, nearer ones before far ones; each fades in, stays ~4.5 s and
 * fades out). Positions follow the animals every frame through [projection]; only the draw phase reads the frame
 * clock, so it never recomposes the screen. No touches: taps go through to the tank.
 */
@Composable
fun ThoughtBubbleLayer(
    snapshots: StateFlow<SimSnapshot>,
    projection: TankProjection,
    modifier: Modifier = Modifier,
) {
    val s = LiveTheme.style
    val picker = remember { ThoughtBubblePicker() }
    var frameMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(picker) { while (true) withFrameMillis { frameMs = it } }
    DisposableEffect(picker) { onDispose { picker.clear() } }
    val measurer = rememberTextMeasurer()
    val textStyle = s.buttonText.copy(color = s.onCandy)
    val layouts = remember { HashMap<String, TextLayoutResult>() }
    val pt = remember { FloatArray(3) }
    val anchor = remember { FloatArray(3) }
    Canvas(modifier.fillMaxSize()) {
        val now = frameMs
        if (now == 0L) return@Canvas
        val snap = snapshots.value
        val w = size.width
        val h = size.height
        val bubbles = picker.update(snap.stats, now) { id ->
            if (ThoughtBubblePicker.anchorOf(snap, id, anchor) && projection.project(anchor[0], anchor[1], anchor[2], pt) &&
                pt[0] in 0f..w && pt[1] in 0f..h
            ) floatArrayOf(pt[0], pt[1], pt[2]) else null
        }
        if (layouts.size > 48) layouts.clear()
        val padX = 10f * density
        val padY = 6f * density
        val tail = 9f * density
        val edge = 8f * density
        val line = s.outlineWidth.toPx()
        val depth = 3f * density
        for (b in bubbles) {
            val layout = layouts.getOrPut(b.text) {
                measurer.measure(b.text, textStyle, constraints = Constraints(maxWidth = (170f * density).toInt()))
            }
            val bw = layout.size.width + 2 * padX
            val bh = layout.size.height + 2 * padY
            val left = (b.x - bw / 2).coerceIn(edge, (w - bw - edge).coerceAtLeast(edge))
            val top = (b.y - tail - bh).coerceIn(edge, (h - bh - edge).coerceAtLeast(edge))
            val a = b.alpha
            val radius = CornerRadius(bh / 2)
            // arcade chunk: navy depth shadow, white candy glass, navy outline, a tail pointing at the animal
            drawRoundRect(s.outline, Offset(left, top + depth), Size(bw, bh), radius, alpha = a * 0.8f)
            drawRoundRect(Candy.white, Offset(left, top), Size(bw, bh), radius, alpha = a * 0.96f)
            drawRoundRect(s.outline, Offset(left, top), Size(bw, bh), radius, style = Stroke(line), alpha = a)
            val cx = b.x.coerceIn(left + bh / 2, (left + bw - bh / 2).coerceAtLeast(left + bh / 2))
            val tipY = (top + bh + tail).coerceAtMost(b.y)
            val path = Path().apply {
                moveTo(cx - tail * 0.6f, top + bh - line)
                lineTo(cx + tail * 0.6f, top + bh - line)
                lineTo(b.x.coerceIn(cx - tail, cx + tail), tipY)
                close()
            }
            drawPath(path, Candy.white, alpha = a * 0.96f)
            drawPath(path, s.outline, alpha = a, style = Stroke(line))
            drawText(layout, topLeft = Offset(left + padX, top + padY), alpha = a)
        }
    }
}
