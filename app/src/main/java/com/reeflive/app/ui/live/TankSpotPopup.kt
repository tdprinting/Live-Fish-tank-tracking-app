package com.reeflive.app.ui.live

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reeflive.app.ui.components.Glyph
import com.reeflive.app.ui.components.GlyphBadge
import com.reeflive.app.ui.components.ReefGlyph
import com.reeflive.app.ui.components.ReefGlyphs
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.core.sim.SpotMenu
import com.reeflive.core.sim.SpotOption
import kotlin.math.roundToInt

/** A tapped piece of gear and where the finger landed (view px), for [TankSpotPopup]. */
data class SpotTap(val spot: com.reeflive.core.sim.TankSpot, val at: Offset, val serial: Int)

/** ReefGlyphs by name (core TankSpots names the pictures so the web shares them); a droplet if unknown. */
fun glyphNamed(name: String): ReefGlyph = ReefGlyphs.all.firstOrNull { it.name == name } ?: ReefGlyphs.droplet

/**
 * The little pop-out menu for a tapped piece of gear (core TankSpots): it springs out of the spot that was tapped with
 * a ring marking it, says in one line how the gear is doing, and offers one to three big buttons. Lay it out over the
 * GL view's bounds; only the card takes touches, so the tank around it still works (any other tap closes it).
 */
@Composable
fun TankSpotPopup(tap: SpotTap, menu: SpotMenu, onOption: (SpotOption) -> Unit, onClose: () -> Unit) {
    val s = LiveTheme.style
    val reduce = ReefTheme.reduceMotion
    val pop = remember(tap.serial) { Animatable(if (reduce) 1f else 0.3f) }
    LaunchedEffect(tap.serial) { if (!reduce) pop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 520f)) }
    val tone = if (menu.warn) Candy.orange else Candy.cyan
    Box(Modifier.fillMaxSize()) {
        // a ring on the tapped gear, so it is clear what the menu belongs to
        Canvas(Modifier.fillMaxSize()) {
            val r = 22.dp.toPx() * pop.value
            drawCircle(tone.copy(alpha = 0.25f), r, tap.at)
            drawCircle(tone, r, tap.at, style = Stroke(3.dp.toPx()))
        }
        var above = true
        Column(
            Modifier
                .layout { m, c ->
                    val p = m.measure(c.copy(minWidth = 0, minHeight = 0))
                    val gap = 30.dp.toPx()
                    val margin = 12.dp.toPx()
                    above = tap.at.y - gap - p.height >= margin
                    val y = if (above) tap.at.y - gap - p.height else tap.at.y + gap
                    val x = (tap.at.x - p.width / 2f).coerceIn(margin, (c.maxWidth - p.width - margin).coerceAtLeast(margin))
                    layout(c.maxWidth, c.maxHeight) { p.place(x.roundToInt(), y.roundToInt().coerceIn(0, (c.maxHeight - p.height).coerceAtLeast(0))) }
                }
                .graphicsLayer {
                    scaleX = pop.value; scaleY = pop.value
                    alpha = ((pop.value - 0.3f) / 0.7f).coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(0.5f, if (above) 1f else 0f)
                }
                .widthIn(max = 320.dp)
                // the card is a touch target of its own: a tap on its edge does not fall through to the tank
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
                .livePanel(s)
                .background(s.panel.copy(alpha = 0.92f))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlyphBadge(glyphNamed(menu.spot.glyph), tone, size = 36.dp)
                Spacer(Modifier.width(8.dp))
                Text(menu.title, style = s.titleText.copy(fontSize = 17.sp), color = s.label, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(32.dp).clip(RoundedCornerShape(50)).clickable(onClick = onClose)
                        .semantics { contentDescription = "Close"; role = Role.Button },
                    contentAlignment = Alignment.Center,
                ) { Glyph(ReefGlyphs.close, size = 20.dp, tint = s.labelDim) }
            }
            Text(menu.status, style = MaterialTheme.typography.bodySmall, color = if (menu.warn) Candy.orange else s.labelDim)
            if (menu.options.isEmpty()) {
                LiveHelp("Switch to My tank to change the gear.")
            } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (o in menu.options) SpotButton(o, onClick = { onOption(o) })
            }
        }
    }
}

@Composable
private fun SpotButton(o: SpotOption, onClick: () -> Unit) {
    val s = LiveTheme.style
    val tone = if (o.urgent) Candy.red else Candy.green
    Column(
        Modifier
            .width(84.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .semantics { role = Role.Button }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        GlyphBadge(glyphNamed(o.glyph), tone, size = 48.dp)
        Text(o.label, style = s.buttonText.copy(fontSize = 13.sp), color = s.label, textAlign = TextAlign.Center, maxLines = 2)
    }
}
