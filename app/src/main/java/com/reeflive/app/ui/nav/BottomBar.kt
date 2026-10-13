package com.reeflive.app.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.reeflive.app.ui.theme.ReefMotion
import com.reeflive.app.ui.theme.ReefShapes
import com.reeflive.app.ui.theme.ReefTheme

/** Widest the floating bar gets (wide / landscape screens centre it). */
val BottomBarMaxWidth = 560.dp

/**
 * Floating glass pill navigation (UI_STYLE §6.1): 64 dp tall, 16 dp from the edges. The active item gets a
 * filled icon in lagoonBright on a lagoon @ 20 % pill plus its label; labels of inactive items stay hidden.
 */
@Composable
fun BottomBar(
    current: Tab?,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
    liveDot: Boolean = false,
) {
    val c = ReefTheme.colors
    val shape = ReefShapes.pill
    val fill = remember(c) {
        Brush.verticalGradient(
            if (c.isDark) listOf(c.bgMid.copy(alpha = 0.86f), c.bg.copy(alpha = 0.92f))
            else listOf(Color.White.copy(alpha = 0.86f), c.bgMid.copy(alpha = 0.9f)),
        )
    }
    // capped at BottomBarMaxWidth and centred, so in landscape the Live tank's side rail can run down beside it
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp), contentAlignment = Alignment.Center) {
        val narrow = maxWidth < 344.dp
        Row(
            Modifier
                .widthIn(max = BottomBarMaxWidth)
                .fillMaxWidth()
                .height(64.dp)
                .shadow(18.dp, shape, ambientColor = c.shadow, spotColor = c.shadow)
                .clip(shape)
                .background(fill)
                .border(1.dp, c.glassBorder, shape)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Tab.entries.forEach { tab ->
                BarItem(
                    tab = tab,
                    selected = tab == current,
                    showLabel = tab == current && !narrow,
                    dot = tab == Tab.LIVE && liveDot,
                    onClick = { onSelect(tab) },
                )
            }
        }
    }
}

@Composable
private fun BarItem(tab: Tab, selected: Boolean, showLabel: Boolean, dot: Boolean, onClick: () -> Unit) {
    val c = ReefTheme.colors
    val reduce = ReefTheme.reduceMotion
    val pill by animateFloatAsState(if (selected) 1f else 0f, ReefMotion.spring(reduce), label = "navpill")
    Box(
        Modifier
            .fillMaxHeight()
            .clip(ReefShapes.pill)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .semantics {
                this.selected = selected
                contentDescription = tab.label
            }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .graphicsLayer { alpha = 0.999f }
                .clip(ReefShapes.pill)
                .background(c.primary.copy(alpha = 0.20f * pill))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                Icon(
                    if (selected) tab.selectedIcon else tab.icon,
                    contentDescription = null,
                    tint = if (selected) c.primaryBright else c.textSecondary,
                    modifier = Modifier.size(24.dp),
                )
                if (dot) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(6.dp)
                            .background(c.coral, CircleShape),
                    )
                }
            }
            AnimatedVisibility(
                visible = showLabel,
                enter = if (reduce) fadeIn() else fadeIn() + expandHorizontally(),
                exit = if (reduce) fadeOut() else fadeOut() + shrinkHorizontally(),
            ) {
                Row {
                    Spacer(Modifier.width(6.dp))
                    Text(tab.label, style = MaterialTheme.typography.labelLarge, color = c.primaryBright, maxLines = 1)
                }
            }
        }
    }
}
