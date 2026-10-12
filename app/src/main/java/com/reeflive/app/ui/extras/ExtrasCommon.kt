package com.reeflive.app.ui.extras

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.reeflive.app.ui.theme.ReefShapes
import com.reeflive.app.ui.theme.ReefTheme

/** Thin glass bar; [color] defaults to the lagoon gradient. */
@Composable
fun GlassBar(fraction: Float, modifier: Modifier = Modifier, color: Color? = null, height: Dp = 8.dp) {
    val c = ReefTheme.colors
    Box(modifier.fillMaxWidth().height(height).background(c.glass, ReefShapes.pill)) {
        val m = Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight()
        if (color != null) Box(m.background(color, ReefShapes.pill)) else Box(m.background(c.primaryBrush, ReefShapes.pill))
    }
}

/** Colour for a 0–100 score. */
@Composable
fun scoreColor(score: Int): Color {
    val c = ReefTheme.colors
    return when {
        score >= 70 -> c.good
        score >= 50 -> c.caution
        else -> c.danger
    }
}

/** Health ring: a 270° arc filled to [score] / 100 with the number in the middle. */
@Composable
fun ScoreRing(score: Int, label: String, modifier: Modifier = Modifier, size: Dp = 132.dp) {
    val c = ReefTheme.colors
    val tone = scoreColor(score)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = 12.dp.toPx()
            val inset = stroke / 2
            val arc = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(c.glass, 135f, 270f, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(tone, 135f, 270f * score.coerceIn(0, 100) / 100f, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$score", style = MaterialTheme.typography.displaySmall, color = c.text)
            Text(label, style = MaterialTheme.typography.labelMedium, color = tone)
        }
    }
}
