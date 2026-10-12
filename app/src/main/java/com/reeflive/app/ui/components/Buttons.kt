package com.reeflive.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reeflive.app.ui.theme.Ink
import com.reeflive.app.ui.theme.ReefMotion
import com.reeflive.app.ui.theme.ReefShapes
import com.reeflive.app.ui.theme.ReefTheme

enum class ButtonKind { FILLED, TONAL, OUTLINED, DESTRUCTIVE, CORAL }

/** Pill button family (UI_STYLE §6.3), 48 dp tall, press scale 0.96. */
@Composable
fun ReefButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.FILLED,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    val c = ReefTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, ReefMotion.spring(ReefTheme.reduceMotion), label = "btn")
    val (bg: Brush?, fg: Color, border: Color?) = when (kind) {
        ButtonKind.FILLED -> Triple(c.primaryBrush, Ink.onPrimary, null)
        ButtonKind.CORAL -> Triple(c.coralBrush, Ink.onCoral, null)
        ButtonKind.TONAL -> Triple(Brush.linearGradient(listOf(c.primary.copy(alpha = 0.16f), c.primary.copy(alpha = 0.16f))), c.primaryBright, null)
        ButtonKind.OUTLINED -> Triple(null, c.text, c.glassBorder)
        ButtonKind.DESTRUCTIVE -> Triple(Brush.linearGradient(listOf(c.danger.copy(alpha = 0.16f), c.danger.copy(alpha = 0.16f))), c.dangerText, null)
    }
    var m = modifier
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .defaultMinSize(minHeight = if (compact) 36.dp else 48.dp)
        .clip(ReefShapes.pill)
    if (bg != null) m = m.background(bg)
    if (border != null) m = m.border(1.dp, border, ReefShapes.pill)
    m = m.clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
        .alpha(if (enabled) 1f else 0.45f)
        .padding(horizontal = if (compact) 14.dp else 22.dp, vertical = if (compact) 8.dp else 12.dp)
    Row(m, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(if (compact) 18.dp else 20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Global "Log test" FAB (UI_STYLE §6.2): 56 dp coral gradient circle with a soft coral glow. */
@Composable
fun ReefFab(onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector = Icons.Rounded.Add, contentDescription: String = "Log test") {
    val c = ReefTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, ReefMotion.spring(ReefTheme.reduceMotion), label = "fab")
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(16.dp, CircleShape, ambientColor = c.coral.copy(alpha = 0.35f), spotColor = c.coral.copy(alpha = 0.35f))
            .size(56.dp)
            .clip(CircleShape)
            .background(c.coralBrush)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Ink.onCoral, modifier = Modifier.size(28.dp))
    }
}

/** Round glass icon button (48 dp touch target). */
@Composable
fun GlassIconButton(icon: ImageVector, contentDescription: String?, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color? = null) {
    val c = ReefTheme.colors
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(c.glass)
            .border(1.dp, c.glassBorder, CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint ?: c.text, modifier = Modifier.size(22.dp))
    }
}

/** Small text action ("See all"). */
@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = ReefTheme.colors
    Text(
        text, style = MaterialTheme.typography.labelLarge, color = c.primaryBright,
        modifier = modifier.clip(ReefShapes.pill).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp).height(20.dp),
    )
}
