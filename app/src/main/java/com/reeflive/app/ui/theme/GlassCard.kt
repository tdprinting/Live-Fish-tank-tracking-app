package com.reeflive.app.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Glassmorphism card (UI_STYLE §4): translucent fill with a soft top-to-bottom sheen, 1 dp border,
 * a 1 dp highlight along the top edge, optional status [tint], dashed border for empty states.
 * Pressing scales to 0.97. The blur is faked with the sheen gradient (cheap on every device).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = ReefShapes.card,
    tint: Color? = null,
    dashed: Boolean = false,
    fillAlpha: Float = 1f,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = ReefTheme.colors
    val reduce = ReefTheme.reduceMotion
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && onClick != null) 0.97f else 1f, ReefMotion.spring(reduce), label = "press")
    val sheen = remember(c, tint, fillAlpha) {
        val base = c.glass.copy(alpha = (c.glass.alpha * fillAlpha).coerceIn(0f, 0.97f))
        val top = if (c.isDark) Color.White.copy(alpha = base.alpha + 0.03f) else Color.White.copy(alpha = (base.alpha + 0.15f).coerceAtMost(1f))
        val stops = mutableListOf(top, base, base.copy(alpha = base.alpha * 0.8f))
        Brush.verticalGradient(stops)
    }
    var m = modifier
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(shape)
        .background(sheen)
    if (tint != null) m = m.background(tint.copy(alpha = 0.08f))
    m = if (dashed) {
        m.drawWithContent {
            drawContent()
            drawRoundRect(
                color = c.glassBorder, style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
                cornerRadius = CornerRadius(18.dp.toPx()),
            )
        }
    } else {
        m.border(1.dp, c.glassBorder, shape)
            .drawWithContent {
                drawContent()
                val inset = 18.dp.toPx()
                drawLine(c.glassHighlight, Offset(inset, 1.dp.toPx()), Offset(size.width - inset, 1.dp.toPx()), strokeWidth = 1.dp.toPx())
            }
    }
    if (onClick != null || onLongClick != null) {
        m = m.combinedClickable(interactionSource = interaction, indication = null, onClick = { onClick?.invoke() }, onLongClick = onLongClick)
    }
    Column(m.padding(contentPadding), content = content)
}

/** Subtle hairline divider used inside glass lists. */
@Composable
fun GlassDivider(modifier: Modifier = Modifier, thickness: Dp = 1.dp) {
    val c = ReefTheme.colors
    androidx.compose.foundation.layout.Box(
        modifier.fillMaxWidth().height(thickness).background(c.glassBorder),
    )
}
