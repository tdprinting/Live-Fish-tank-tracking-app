package com.reeflive.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Help
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.reeflive.app.ui.theme.ReefMotion
import com.reeflive.app.ui.theme.ReefShapes
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.app.ui.theme.status
import com.reeflive.app.ui.theme.statusFill
import com.reeflive.app.ui.theme.statusText
import com.reeflive.core.status.Status

fun statusIcon(s: Status): ImageVector = when (s) {
    Status.GOOD -> Icons.Rounded.CheckCircle
    Status.CAUTION -> Icons.Rounded.Warning
    Status.DANGER -> Icons.Rounded.Error
    Status.UNKNOWN -> Icons.AutoMirrored.Rounded.Help
}

fun statusLabel(s: Status): String = when (s) {
    Status.GOOD -> "In range"; Status.CAUTION -> "Watch"; Status.DANGER -> "Danger"; Status.UNKNOWN -> "No data"
}

@Composable
fun StatusIcon(status: Status, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    Icon(statusIcon(status), contentDescription = statusLabel(status), tint = ReefTheme.colors.status(status), modifier = modifier.size(size))
}

/** Status pill (UI_STYLE §6.6): 24 dp, status fill, icon + label in the text variant. Never color-only. */
@Composable
fun StatusPill(status: Status, modifier: Modifier = Modifier, label: String = statusLabel(status)) {
    val c = ReefTheme.colors
    Row(
        modifier
            .height(24.dp)
            .clip(ReefShapes.pill)
            .background(c.statusFill(status))
            .padding(horizontal = 8.dp)
            .semantics { contentDescription = "Status: $label" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(statusIcon(status), null, tint = c.statusText(status), modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.statusText(status), maxLines = 1)
    }
}

/** "Stale" chip shown next to a value that has not been tested for > 1.5 × cadence. */
@Composable
fun StaleChip(days: Int, modifier: Modifier = Modifier) {
    val c = ReefTheme.colors
    Row(
        modifier.height(24.dp).clip(ReefShapes.pill).background(c.caution.copy(alpha = c.statusFillAlpha)).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Schedule, null, tint = c.cautionText, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text("Stale · ${days} d", style = MaterialTheme.typography.labelMedium, color = c.cautionText)
    }
}

/** Selectable pill chip (UI_STYLE §6.4). */
@Composable
fun ReefChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    accent: Color? = null,
) {
    val c = ReefTheme.colors
    val reduce = ReefTheme.reduceMotion
    val tone = accent ?: c.primary
    val bg by animateColorAsState(if (selected) tone.copy(alpha = 0.22f) else c.glass, ReefMotion.tween(reduce, ReefMotion.FAST), label = "chipBg")
    val border by animateColorAsState(if (selected) tone else c.glassBorder, ReefMotion.tween(reduce, ReefMotion.FAST), label = "chipBorder")
    val fg = if (selected) (accent ?: c.primaryBright) else c.text
    // press feedback: a quick shrink like the buttons (UI_STYLE §6.3), on top of the ripple
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, ReefMotion.spring(reduce), label = "chipPress")
    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .heightIn(min = 36.dp)
            .clip(ReefShapes.pill)
            .background(bg)
            .border(1.dp, border, ReefShapes.pill)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        leading?.invoke()
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
    }
}

/** Segmented pill control (Parameters | History, range selector, Reality | Sandbox). */
@Composable
fun SegmentedPills(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    selectedColor: Color? = null,
) {
    val c = ReefTheme.colors
    Row(
        modifier.clip(ReefShapes.pill).background(c.glass).border(1.dp, c.glassBorder, ReefShapes.pill).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            val bg by animateColorAsState(if (sel) c.primary.copy(alpha = 0.28f) else Color.Transparent, ReefMotion.tween(ReefTheme.reduceMotion, ReefMotion.FAST), label = "seg")
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 36.dp)
                    .clip(ReefShapes.pill)
                    .background(bg)
                    .clickable(role = Role.Tab) { onSelect(i) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                    color = if (sel) (selectedColor ?: c.primaryBright) else c.textSecondary,
                )
            }
        }
    }
}

/** Small colored dot (live status while typing, legend). */
@Composable
fun Dot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}
