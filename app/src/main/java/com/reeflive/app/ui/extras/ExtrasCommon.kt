package com.reeflive.app.ui.extras

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.reeflive.app.ui.components.ButtonKind
import com.reeflive.app.ui.components.GlassIconButton
import com.reeflive.app.ui.components.ReefButton
import com.reeflive.app.ui.components.ReefSheet
import com.reeflive.app.ui.components.ScreenHeader
import com.reeflive.app.ui.components.pagePadding
import com.reeflive.app.ui.theme.ReefShapes
import com.reeflive.app.ui.theme.ReefTheme
import java.util.Locale

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
    val track = c.glass
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = 12.dp.toPx()
            val inset = stroke / 2
            val arc = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 135f, 270f, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(tone, 135f, 270f * score.coerceIn(0, 100) / 100f, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$score", style = MaterialTheme.typography.displaySmall, color = c.text)
            Text(label, style = MaterialTheme.typography.labelMedium, color = tone)
        }
    }
}

/** Standard extras page: header with back and an optional "+" action, scrolling content. */
@Composable
fun ExtrasPage(
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onAdd: (() -> Unit)? = null,
    addLabel: String = "Add",
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        ScreenHeader(title, subtitle = subtitle, onBack = onBack, actions = {
            if (onAdd != null) GlassIconButton(Icons.Rounded.Add, addLabel, onAdd)
        })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(pagePadding(top = 0.dp)),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

/** Glass bottom sheet with a title and Save / Cancel (and optional Delete). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormSheet(
    title: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean = true,
    saveLabel: String = "Save",
    onDelete: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = ReefTheme.colors
    ReefSheet(onDismiss = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp)
                .navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = c.text)
            content()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) ReefButton("Delete", onDelete, kind = ButtonKind.DESTRUCTIVE, compact = true)
                Spacer(Modifier.weight(1f))
                ReefButton("Cancel", onDismiss, kind = ButtonKind.OUTLINED, compact = true)
                ReefButton(saveLabel, onSave, enabled = saveEnabled, compact = true)
            }
        }
    }
}

/** Two-line row: title + detail on the left, [trailing] on the right. */
@Composable
fun InfoRow(title: String, detail: String?, modifier: Modifier = Modifier, titleColor: Color? = null, trailing: @Composable () -> Unit = {}) {
    val c = ReefTheme.colors
    Row(modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor ?: c.text)
            if (!detail.isNullOrBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
        }
        trailing()
    }
}

/** Small coloured status pill. */
@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.background(color.copy(alpha = 0.18f), ReefShapes.pill).padding(horizontal = 10.dp, vertical = 3.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/** Parses a user-typed number ("1,5" or "1.5"); null when blank or invalid. */
fun parseNum(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

fun money(v: Double): String = "$" + String.format(Locale.US, "%,.2f", v)

/** "3" for 3.0, "2.5" for 2.5. */
fun qty(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
