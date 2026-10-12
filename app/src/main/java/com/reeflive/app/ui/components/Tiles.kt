package com.reeflive.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reeflive.app.ui.charts.Sparkline
import com.reeflive.app.ui.charts.TimeSeriesChart
import com.reeflive.app.ui.common.HeroUi
import com.reeflive.app.ui.common.TileUi
import com.reeflive.app.ui.theme.GlassCard
import com.reeflive.app.ui.theme.ReefShapes
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.app.ui.theme.status
import com.reeflive.app.ui.theme.statusText
import com.reeflive.core.status.Status
import com.reeflive.core.util.Fmt

/**
 * Number text that counts from its previous value to the new one over 400 ms when it changes
 * (UI_STYLE §7 "Save test"). Non-numeric text (e.g. "—") is shown as is.
 */
@Composable
fun AnimatedNumber(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val target = text.toDoubleOrNull()
    if (target == null || ReefTheme.reduceMotion) {
        Text(text, style = style, color = color, modifier = modifier, maxLines = 1)
        return
    }
    val decimals = text.substringAfter('.', "").length
    val anim = remember { Animatable(target.toFloat()) }
    LaunchedEffect(target) { anim.animateTo(target.toFloat(), tween(400, easing = FastOutSlowInEasing)) }
    val shown = if (anim.isRunning) Fmt.num(anim.value.toDouble(), decimals) else text
    Text(shown, style = style, color = color, modifier = modifier, maxLines = 1)
}

/** Dashboard parameter tile (UI_STYLE §8.1). */
@Composable
fun ParameterTile(
    tile: TileUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val c = ReefTheme.colors
    val desc = if (tile.logged) "${tile.name} ${tile.value} ${tile.unit}, ${statusLabel(tile.status)}, ${tile.ago}" else "${tile.name}, no data, tap to log"
    GlassCard(
        modifier = modifier.height(128.dp).semantics(mergeDescendants = true) { contentDescription = desc },
        shape = ReefShapes.tile,
        tint = if (tile.logged && tile.status != Status.GOOD) c.status(tile.status) else null,
        dashed = !tile.logged,
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 10.dp),
        onClick = onClick,
        onLongClick = onLongClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ParamGlyph(tile.id, size = 18.dp, tint = c.textSecondary)
            Spacer(Modifier.width(6.dp))
            Text(tile.name, style = MaterialTheme.typography.labelMedium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (tile.logged) StatusIcon(tile.status, size = 16.dp)
        }
        Spacer(Modifier.height(4.dp))
        if (!tile.logged) {
            Text("—", style = ReefTheme.numbers.tileValue, color = c.textTertiary)
            Spacer(Modifier.weight(1f))
            Text("Tap to log", style = MaterialTheme.typography.labelSmall, color = c.primaryBright)
            return@GlassCard
        }
        Row(verticalAlignment = Alignment.Bottom) {
            AnimatedNumber(tile.value.orEmpty(), ReefTheme.numbers.tileValue, c.text, Modifier.alignByBaseline())
            Spacer(Modifier.width(4.dp))
            Text(tile.unit, style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.alignByBaseline(), maxLines = 1)
            Spacer(Modifier.width(4.dp))
            TrendArrow(tile.trend, c.statusText(tile.status), Modifier.padding(bottom = 4.dp))
        }
        Sparkline(
            tile.spark, tile.sparkStart, tile.sparkEnd,
            modifier = Modifier.fillMaxWidth().height(28.dp),
            ideal = tile.ideal, acceptable = tile.acceptable,
            endColor = c.status(tile.status),
        )
        Spacer(Modifier.weight(1f))
        if (tile.staleDays != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Schedule, null, tint = c.cautionText, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text(tile.ago.orEmpty(), style = MaterialTheme.typography.labelSmall, color = c.cautionText, maxLines = 1)
            }
        } else {
            Text(tile.ago.orEmpty(), style = MaterialTheme.typography.labelSmall, color = c.textTertiary, maxLines = 1)
        }
    }
}

/** Dashboard hero (UI_STYLE §8.2): the parameter that matters most today. */
@Composable
fun HeroTile(hero: HeroUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = ReefTheme.colors
    val t = hero.tile
    // Danger: the border pulses twice when the hero appears (UI_STYLE §7).
    val reduce = ReefTheme.reduceMotion
    val pulse = remember(t.id, t.status) { Animatable(0f) }
    LaunchedEffect(t.id, t.status) {
        if (t.status == Status.DANGER && !reduce) {
            repeat(2) {
                pulse.animateTo(1f, tween(300))
                pulse.animateTo(0f, tween(300))
            }
        }
    }
    val borderAlpha by animateFloatAsState(if (t.status == Status.DANGER) 0.6f else 0f, label = "heroBorder")
    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 196.dp)
            .background(c.heroBrush, ReefShapes.card)
            .border(1.5.dp, c.danger.copy(alpha = (borderAlpha * 0.5f + pulse.value * 0.5f).coerceIn(0f, 1f)), ReefShapes.card),
        onClick = onClick,
        contentPadding = PaddingValues(18.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(0.55f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ParamGlyph(t.id, size = 20.dp, tint = c.textSecondary)
                    Spacer(Modifier.width(8.dp))
                    Text(t.name, style = MaterialTheme.typography.headlineSmall, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    AnimatedNumber(t.value ?: "—", MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum"), c.text, Modifier.alignByBaseline())
                    Spacer(Modifier.width(6.dp))
                    Text(t.unit, style = MaterialTheme.typography.titleMedium, color = c.textSecondary, modifier = Modifier.alignByBaseline())
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill(t.status)
                    TrendArrow(t.trend, c.statusText(t.status), size = 18.dp)
                }
                if (hero.delta != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(hero.delta, style = ReefTheme.numbers.label, color = c.textSecondary)
                }
            }
            Box(Modifier.weight(0.45f).height(110.dp).padding(start = 8.dp)) {
                TimeSeriesChart(
                    points = hero.points, bands = hero.bands, windowStart = hero.windowStart, windowEnd = hero.windowEnd,
                    precision = hero.precision, unit = t.unit, height = 110.dp, events = hero.events,
                    interactive = false, showAxes = false, emptyText = "",
                    modifier = Modifier.fillMaxHeight(),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(hero.insight, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
    }
}

/** Compact list row for the Log → Parameters list (DESIGN §5.4). */
@Composable
fun ParameterRow(tile: TileUi, onClick: () -> Unit, modifier: Modifier = Modifier, onLongClick: (() -> Unit)? = null) {
    val c = ReefTheme.colors
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = ReefShapes.tile,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        onClick = onClick,
        onLongClick = onLongClick,
        dashed = !tile.logged,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).background(c.primary.copy(alpha = 0.14f), ReefShapes.pill), contentAlignment = Alignment.Center) {
                ParamGlyph(tile.id, size = 20.dp, tint = c.primaryBright)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tile.name, style = MaterialTheme.typography.titleMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (tile.logged) {
                        Text("${tile.value} ${tile.unit}".trim(), style = ReefTheme.numbers.body, color = c.text)
                        Spacer(Modifier.width(6.dp))
                        Text("· tested ${tile.ago}", style = MaterialTheme.typography.labelSmall, color = if (tile.staleDays != null) c.cautionText else c.textTertiary, maxLines = 1)
                    } else {
                        Text("Not logged yet", style = MaterialTheme.typography.labelMedium, color = c.textTertiary)
                    }
                }
            }
            if (tile.logged) {
                Sparkline(
                    tile.spark, tile.sparkStart, tile.sparkEnd,
                    modifier = Modifier.width(72.dp).height(28.dp).alpha(0.95f),
                    ideal = tile.ideal, acceptable = tile.acceptable, endColor = c.status(tile.status),
                )
                Spacer(Modifier.width(10.dp))
                StatusPill(tile.status)
            }
        }
    }
}
