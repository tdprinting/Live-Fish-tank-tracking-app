package com.reeflive.app.ui.live

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.reeflive.app.live.AwayUi
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * "While you were away" (docs/ECONOMY.md §9; web care.js): first the days race by (a sun and moon swinging over a
 * day counter) while My tank catches up, then what happened in plain words and one button to go take care of it.
 */
@Composable
fun WelcomeBackCard(a: AwayUi, onCare: () -> Unit, onDismiss: () -> Unit) {
    val s = LiveTheme.style
    val done = a.summary
    Dialog(onDismissRequest = { if (done != null) onDismiss() }, properties = DialogProperties(dismissOnClickOutside = done != null, dismissOnBackPress = done != null)) {
        Column(
            Modifier
                .widthIn(max = 420.dp)
                .heightIn(max = 600.dp)
                .livePanel(s)
                .background(s.panel.copy(alpha = 0.94f))
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val shown by animateFloatAsState(a.daysDone.toFloat(), tween(300), label = "days")
            DayArc(shown)
            if (done == null) {
                Text("While you were away…", style = s.titleText, color = s.label)
                Text("Day ${floor(shown).toInt() + 1} of ${maxOf(1, kotlin.math.ceil(a.days).toInt())}", style = s.buttonText.copy(fontSize = 16.sp), color = Candy.yellow)
                Canvas(Modifier.fillMaxWidth().height(12.dp)) {
                    val r = CornerRadius(size.height / 2)
                    drawRoundRect(Candy.navy.copy(alpha = 0.55f), cornerRadius = r)
                    drawRoundRect(Candy.cyan, size = Size(size.width * a.progress.toFloat().coerceIn(0.03f, 1f), size.height), cornerRadius = r)
                    drawRoundRect(Candy.navy, cornerRadius = r, style = Stroke(2f))
                }
                Text("Your tank kept living: fish got hungry, water changed, algae grew.", style = MaterialTheme.typography.bodySmall, color = s.labelDim)
            } else {
                Text(done.headline, style = s.titleText, color = s.label)
                done.lines.forEach { line ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text("•", color = Candy.yellow, modifier = Modifier.width(18.dp))
                        Text(line, style = MaterialTheme.typography.bodyMedium, color = s.label)
                    }
                }
                if (done.deaths.isNotEmpty()) {
                    LiveSectionTitle("Goodbye")
                    done.deaths.forEach { (name, cause) ->
                        Text("$name ($cause)", style = MaterialTheme.typography.bodyMedium, color = s.labelDim)
                    }
                    LiveHelp("The net will take them out. Feeding every day and the jobs on the Care card keep everyone healthy.")
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                    LivePill("Later", onDismiss, tone = Candy.white)
                    if (done.lines.isNotEmpty() || done.deaths.isNotEmpty()) LivePill("Take care of it", onCare, tone = Candy.green)
                }
            }
        }
    }
}

/** A sun by day and a moon by night swinging over the water as the days go by. */
@Composable
private fun DayArc(days: Float) {
    Canvas(Modifier.fillMaxWidth().height(90.dp)) {
        val f = days - floor(days)
        val day = f < 0.5f
        val k = if (day) f / 0.5f else (f - 0.5f) / 0.5f
        val sky = if (day) Color(0xFF5FB8E6) else Color(0xFF14204A)
        drawRoundRect(sky, cornerRadius = CornerRadius(16.dp.toPx()))
        val cx = size.width * (0.1f + 0.8f * k)
        val cy = size.height * 0.85f - sin(k * PI.toFloat()) * size.height * 0.6f
        if (day) {
            for (i in 0 until 8) {
                val ang = i * PI.toFloat() / 4
                drawLine(Candy.yellow, Offset(cx + cos(ang) * 16.dp.toPx(), cy + sin(ang) * 16.dp.toPx()), Offset(cx + cos(ang) * 22.dp.toPx(), cy + sin(ang) * 22.dp.toPx()), 3.dp.toPx())
            }
            drawCircle(Candy.yellow, 12.dp.toPx(), Offset(cx, cy))
        } else {
            drawCircle(Color(0xFFF2F6FF), 11.dp.toPx(), Offset(cx, cy))
            drawCircle(sky, 9.dp.toPx(), Offset(cx + 5.dp.toPx(), cy - 3.dp.toPx()))
            for (i in 0 until 6) drawCircle(Color.White.copy(alpha = 0.6f), 1.5.dp.toPx(), Offset(size.width * (0.12f + 0.15f * i), size.height * (0.2f + 0.12f * (i % 2))))
        }
        // the water line
        drawRect(Color(0xFF1FA6C9).copy(alpha = 0.8f), Offset(0f, size.height * 0.82f), Size(size.width, size.height * 0.18f))
    }
}
