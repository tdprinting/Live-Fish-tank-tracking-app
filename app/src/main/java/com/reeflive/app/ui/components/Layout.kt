package com.reeflive.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.East
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.SouthEast
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.core.status.Trend

/** Bottom padding that keeps content clear of the floating bottom bar and FAB. */
val BottomBarClearance: Dp = 112.dp

/** Standard page padding for LazyColumns: 16 dp gutters, clearance for the floating bar. */
fun pagePadding(top: Dp = 8.dp): PaddingValues = PaddingValues(start = 16.dp, end = 16.dp, top = top, bottom = BottomBarClearance)

/** Screen title row with an optional back arrow and trailing actions. */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    val c = ReefTheme.colors
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

/** Section title (headlineSmall) with an optional trailing action. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 20.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = ReefTheme.colors.text, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

fun trendIcon(t: Trend): ImageVector? = when (t) {
    Trend.UP -> Icons.Rounded.NorthEast
    Trend.DOWN -> Icons.Rounded.SouthEast
    Trend.FLAT -> Icons.Rounded.East
    Trend.UNKNOWN -> null
}

fun trendLabel(t: Trend): String = when (t) {
    Trend.UP -> "rising"; Trend.DOWN -> "falling"; Trend.FLAT -> "stable"; Trend.UNKNOWN -> ""
}

@Composable
fun TrendArrow(trend: Trend, color: Color, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    val icon = trendIcon(trend) ?: return
    Icon(icon, contentDescription = trendLabel(trend), tint = color, modifier = modifier.size(size))
}

/** Warm, specific empty state (UI_STYLE §11). */
@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, icon: ImageVector? = null, action: (@Composable () -> Unit)? = null) {
    val c = ReefTheme.colors
    Column(modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (icon != null) {
            Icon(icon, null, tint = c.primaryBright.copy(alpha = 0.8f), modifier = Modifier.size(40.dp))
            Spacer(Modifier.padding(top = 12.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.text, textAlign = TextAlign.Center)
        Spacer(Modifier.padding(top = 6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.padding(top = 16.dp))
            action()
        }
    }
}

/** Screen-enter motion (UI_STYLE §7): fade 180 ms + rise 12 dp. Skipped with reduce motion. */
@Composable
fun EnterRise(key: Any? = Unit, delayMs: Int = 0, content: @Composable () -> Unit) {
    val reduce = ReefTheme.reduceMotion
    val p = remember(key) { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(key, reduce) {
        if (!reduce) p.animateTo(1f, tween(220, delayMillis = delayMs, easing = FastOutSlowInEasing))
    }
    Box(Modifier.graphicsLayer { alpha = p.value; translationY = (1f - p.value) * 12.dp.toPx() }) { content() }
}
