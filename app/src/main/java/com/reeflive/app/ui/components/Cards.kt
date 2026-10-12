package com.reeflive.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Healing
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.PersonAddAlt
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reeflive.app.ui.common.EventUi
import com.reeflive.app.ui.common.TaskUi
import com.reeflive.app.ui.theme.GlassCard
import com.reeflive.app.ui.theme.Ink
import com.reeflive.app.ui.theme.PeekPalette
import com.reeflive.app.ui.theme.ReefMotion
import com.reeflive.app.ui.theme.ReefShapes
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.core.care.DueState
import com.reeflive.core.model.EventType
import kotlin.math.sin

/** Round check button with a springy fill (UI_STYLE §7 "Task complete"). */
@Composable
fun CheckCircleButton(checked: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, danger: Boolean = false, contentDescription: String = "Mark done") {
    val c = ReefTheme.colors
    val fill by animateFloatAsState(if (checked) 1f else 0f, ReefMotion.bouncy(ReefTheme.reduceMotion), label = "check")
    val ring = if (danger) c.danger else c.textSecondary
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).border(1.5.dp, if (checked) c.good else ring, CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(28.dp).scale(fill).clip(CircleShape).background(c.good))
            if (fill > 0.5f) Icon(Icons.Rounded.Check, null, tint = Ink.onPrimary, modifier = Modifier.size(18.dp))
            else if (danger) Box(Modifier.size(6.dp).clip(CircleShape).background(c.danger))
        }
    }
}

/** Task row card (UI_STYLE §8.4). */
@Composable
fun TaskCard(task: TaskUi, onCheck: () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier, checked: Boolean = false) {
    val c = ReefTheme.colors
    GlassCard(
        modifier = modifier.fillMaxWidth().heightIn(min = 72.dp),
        shape = ReefShapes.tile,
        contentPadding = PaddingValues(start = 6.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CheckCircleButton(checked, onCheck, danger = task.overdue, contentDescription = "Mark ${task.title} done")
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(task.title, style = MaterialTheme.typography.titleMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(task.subtitle, style = MaterialTheme.typography.labelSmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            DueChip(task.dueLabel, task.state)
        }
    }
}

@Composable
fun DueChip(label: String, state: DueState) {
    val c = ReefTheme.colors
    val (bg, fg) = when (state) {
        DueState.OVERDUE -> c.danger.copy(alpha = c.statusFillAlpha) to c.dangerText
        DueState.TODAY -> c.good.copy(alpha = c.statusFillAlpha) to c.goodText
        else -> c.glass to c.textSecondary
    }
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        maxLines = 1,
        modifier = Modifier.clip(ReefShapes.pill).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

fun eventIcon(t: EventType): ImageVector = when (t) {
    EventType.FEED -> Icons.Rounded.Restaurant
    EventType.WATER_CHANGE -> Icons.Rounded.Waves
    EventType.DOSE -> Icons.Rounded.WaterDrop
    EventType.NOTE -> Icons.Rounded.EditNote
    EventType.LIGHT_CHANGE -> Icons.Rounded.Lightbulb
    EventType.ADD_LIVESTOCK -> Icons.Rounded.PersonAddAlt
    EventType.REMOVE_LIVESTOCK -> Icons.Rounded.PersonRemove
    EventType.TREATMENT -> Icons.Rounded.Healing
}

@Composable
fun eventColor(t: EventType): Color {
    val c = ReefTheme.colors
    return when (t) {
        EventType.DOSE -> c.primaryBright
        EventType.WATER_CHANGE -> c.anemone
        EventType.FEED -> c.coral
        EventType.NOTE -> c.sand
        EventType.TREATMENT -> c.caution
        else -> c.textSecondary
    }
}

/** One line of the recent-events timeline. */
@Composable
fun EventRow(e: EventUi, modifier: Modifier = Modifier, last: Boolean = false) {
    val c = ReefTheme.colors
    val tint = eventColor(e.type)
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(eventIcon(e.type), null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(e.title, style = MaterialTheme.typography.bodyMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(e.time, style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
    }
}

/**
 * Live Tank peek (DESIGN §5.2 item 6): a painted 16:9 miniature of the reef — water gradient tinted by the
 * current light color, sand, rock silhouettes, a few fish — with an info overlay.
 */
@Composable
fun TankPeek(caption: String, lightColor: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = ReefTheme.colors
    GlassCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp), onClick = onClick) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                drawRect(Brush.verticalGradient(listOf(lightColor.copy(alpha = 0.55f), c.reef, c.bg)))
                // light shafts
                for (i in 0 until 3) {
                    val x = w * (0.2f + i * 0.3f)
                    val p = Path().apply {
                        moveTo(x - w * 0.04f, 0f); lineTo(x + w * 0.04f, 0f); lineTo(x + w * 0.14f, h); lineTo(x - w * 0.02f, h); close()
                    }
                    drawPath(p, Color.White.copy(alpha = 0.05f))
                }
                // sand
                drawOval(c.sand.copy(alpha = 0.55f), Offset(-w * 0.1f, h * 0.80f), Size(w * 1.2f, h * 0.45f))
                // rocks
                val rock = PeekPalette.rock.copy(alpha = 0.95f)
                drawOval(rock, Offset(w * 0.05f, h * 0.52f), Size(w * 0.34f, h * 0.40f))
                drawOval(rock, Offset(w * 0.30f, h * 0.60f), Size(w * 0.22f, h * 0.30f))
                drawOval(rock, Offset(w * 0.62f, h * 0.48f), Size(w * 0.30f, h * 0.44f))
                // corals
                drawCircle(c.coral.copy(alpha = 0.85f), h * 0.05f, Offset(w * 0.18f, h * 0.53f))
                drawCircle(c.good.copy(alpha = 0.85f), h * 0.06f, Offset(w * 0.72f, h * 0.50f))
                drawCircle(c.anemone.copy(alpha = 0.8f), h * 0.04f, Offset(w * 0.40f, h * 0.61f))
                // fish
                fun fish(cx: Float, cy: Float, s: Float, body: Color, flip: Boolean) {
                    val d = if (flip) -1f else 1f
                    drawOval(body, Offset(cx - s, cy - s * 0.45f), Size(s * 2f, s * 0.9f))
                    val tail = Path().apply {
                        moveTo(cx - d * s * 0.9f, cy); lineTo(cx - d * s * 1.5f, cy - s * 0.5f); lineTo(cx - d * s * 1.5f, cy + s * 0.5f); close()
                    }
                    drawPath(tail, body)
                }
                fish(w * 0.52f, h * 0.30f, h * 0.06f, PeekPalette.clown, false)
                fish(w * 0.60f, h * 0.36f, h * 0.05f, PeekPalette.tang, true)
                fish(w * 0.30f, h * 0.24f, h * 0.045f, PeekPalette.pale, false)
                // surface shimmer
                for (i in 0 until 12) {
                    val x = w * i / 11f
                    drawCircle(Color.White.copy(alpha = 0.10f), 2.dp.toPx(), Offset(x, h * 0.03f + 3.dp.toPx() * sin(i.toFloat())))
                }
            }
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, c.bgDeep.copy(alpha = 0.75f))))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Waves, null, tint = c.primaryBright, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(caption, style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
