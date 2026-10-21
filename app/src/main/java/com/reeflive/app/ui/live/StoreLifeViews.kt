package com.reeflive.app.ui.live

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.reeflive.app.render.StoreRoomControl
import com.reeflive.app.render.TankProjection
import com.reeflive.core.custom.economy.TradeOffer

/**
 * What the people in a fish store say (render StorePeople): a speech bubble over each talker's head, following them
 * every frame through [projection] (only the draw phase reads the frame clock, so the screen never recomposes). No
 * touches: taps go through to the store.
 */
@Composable
fun StoreBubbleLayer(room: StoreRoomControl, projection: TankProjection, modifier: Modifier = Modifier) {
    val s = LiveTheme.style
    var frameMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(room) { while (true) withFrameMillis { frameMs = it } }
    val measurer = rememberTextMeasurer()
    val textStyle = s.buttonText.copy(color = s.onCandy)
    val layouts = remember { HashMap<String, TextLayoutResult>() }
    val texts = remember { arrayOfNulls<String>(6) }
    val pos = remember { FloatArray(18) }
    val alpha = remember { FloatArray(6) }
    val pt = remember { FloatArray(3) }
    Canvas(modifier.fillMaxSize()) {
        if (frameMs == 0L) return@Canvas
        val n = room.bubbles(texts, pos, alpha)
        if (layouts.size > 48) layouts.clear()
        val w = size.width
        val h = size.height
        val padX = 10f * density
        val padY = 6f * density
        val tail = 9f * density
        val edge = 8f * density
        val line = s.outlineWidth.toPx()
        val depth = 3f * density
        for (i in 0 until n) {
            val text = texts[i] ?: continue
            if (!projection.project(pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2], pt) || pt[0] !in 0f..w || pt[1] !in 0f..h) continue
            val layout = layouts.getOrPut(text) { measurer.measure(text, textStyle, constraints = Constraints(maxWidth = (190f * density).toInt())) }
            val bw = layout.size.width + 2 * padX
            val bh = layout.size.height + 2 * padY
            val left = (pt[0] - bw / 2).coerceIn(edge, (w - bw - edge).coerceAtLeast(edge))
            val top = (pt[1] - tail - bh).coerceIn(edge, (h - bh - edge).coerceAtLeast(edge))
            val a = alpha[i]
            val radius = CornerRadius(bh / 2)
            drawRoundRect(s.outline, Offset(left, top + depth), Size(bw, bh), radius, alpha = a * 0.8f)
            drawRoundRect(Candy.white, Offset(left, top), Size(bw, bh), radius, alpha = a * 0.96f)
            drawRoundRect(s.outline, Offset(left, top), Size(bw, bh), radius, style = Stroke(line), alpha = a)
            val cx = pt[0].coerceIn(left + bh / 2, (left + bw - bh / 2).coerceAtLeast(left + bh / 2))
            val tipY = (top + bh + tail).coerceAtMost(pt[1])
            val path = Path().apply {
                moveTo(cx - tail * 0.6f, top + bh - line)
                lineTo(cx + tail * 0.6f, top + bh - line)
                lineTo(pt[0].coerceIn(cx - tail, cx + tail), tipY)
                close()
            }
            drawPath(path, Candy.white, alpha = a * 0.96f)
            drawPath(path, s.outline, alpha = a, style = Stroke(line))
            drawText(layout, topLeft = Offset(left + padX, top + padY), alpha = a)
        }
    }
}

/**
 * A store regular's swap (core StoreTrades): who is asking, what they would like and what they give. Yes cuts the frag
 * (or bags the spare) and floats the new animal in a bag at the top of My tank.
 */
@Composable
fun TradeCard(offer: TradeOffer, onAnswer: (Boolean) -> Unit) {
    val s = LiveTheme.style
    Dialog(onDismissRequest = { onAnswer(false) }) {
        Column(
            Modifier.widthIn(max = 400.dp).livePanel(s).background(s.panel.copy(alpha = 0.95f)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("${offer.trader} wants to trade!", style = s.titleText, color = s.label)
            Text("“${offer.line}”", style = MaterialTheme.typography.bodyMedium, color = s.label)
            Row(verticalAlignment = Alignment.Top) {
                Text("You give: ", style = s.buttonText, color = Candy.yellow)
                Text(if (offer.giveFrag) "a frag of your ${offer.giveName} (it stays in your tank, a little smaller)" else "one ${offer.giveName}", style = MaterialTheme.typography.bodyMedium, color = s.label)
            }
            Row(verticalAlignment = Alignment.Top) {
                Text("You get: ", style = s.buttonText, color = Candy.green)
                Text("a ${offer.getName}, in a bag at the top of your tank", style = MaterialTheme.typography.bodyMedium, color = s.label)
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                LivePill("No thanks", { onAnswer(false) }, tone = Candy.white)
                LivePill("Trade!", { onAnswer(true) }, tone = Candy.green)
            }
        }
    }
}
