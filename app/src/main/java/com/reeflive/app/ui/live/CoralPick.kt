package com.reeflive.app.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reeflive.app.render.TankProjection
import com.reeflive.core.sim.CoralPickPose
import com.reeflive.core.sim.CoralSpotPose
import kotlin.math.roundToInt

/**
 * My tank, after a coral's bag is emptied: its best spots glow in the tank (renderer, CoralSpotMarkers) and each gets a
 * numbered badge over it with stars and the reason ("Bright light, gentle flow"). Tapping a badge puts the coral
 * there; the panel at the bottom offers "Pick for me" (the best spot) and "Later" (the coral waits; a pill brings
 * the spots back). Only the badges and the panel take taps, so the tank can still be turned to look.
 */
@Composable
fun CoralPickLayer(
    pick: CoralPickPose,
    projection: TankProjection,
    hidden: Boolean,
    onPlace: (Int) -> Unit,
    onHide: (Boolean) -> Unit,
    bottomClearance: Dp,
) {
    val s = LiveTheme.style
    // a frame tick read only while placing, so the badges follow the camera without recomposing
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(hidden) { while (!hidden) withFrameNanos { frame = it } }
    Box(Modifier.fillMaxSize()) {
        if (!hidden) for (spot in pick.spots) {
            SpotAnchor(projection, { frame }, spot) { SpotBadge(spot) { onPlace(spot.rank) } }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = bottomClearance + 8.dp).padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (hidden) {
                LivePill("🪸 Place ${pick.name}" + if (pick.waiting > 1) " (${pick.waiting})" else "", { onHide(false) }, tone = Candy.yellow)
            } else {
                LivePanel(Modifier.widthIn(max = 420.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(placeTitle(pick), style = s.buttonText, color = s.label)
                    Text(
                        if (pick.spots.isEmpty()) "No free spot right now. Pick for me finds some room." else "Tap a glowing spot. Gold is the best one.",
                        style = s.buttonText.copy(fontSize = 13.sp), color = s.label,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    LivePill("✨ Pick for me", { onPlace(-1) }, tone = Candy.yellow)
                    LivePill("Later", { onHide(true) }, tone = Candy.white)
                }
            }
        }
    }
}

/** "Where should your Zoanthids live?" (and how many more wait). */
fun placeTitle(pick: CoralPickPose): String =
    "Where should your ${pick.name} live?" + if (pick.waiting > 1) " (${pick.waiting} to place)" else ""

@Composable
private fun SpotBadge(spot: CoralSpotPose, onClick: () -> Unit) {
    val s = LiveTheme.style
    val shape = RoundedCornerShape(16.dp)
    val tone = if (spot.rank == 0) Candy.yellow else Candy.cyan
    Column(
        Modifier
            .widthIn(max = 180.dp)
            .clip(shape)
            .background(tone)
            .border(2.dp, s.outline, shape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Spot ${spot.rank + 1}, ${spot.stars} stars: ${spot.reason}. Tap to put the coral here." }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("${spot.rank + 1}  " + "★".repeat(spot.stars) + "☆".repeat(3 - spot.stars), style = s.buttonText.copy(fontSize = 14.sp), color = s.onCandy)
        Text(spot.reason, style = s.buttonText.copy(fontSize = 11.sp), color = s.onCandy, maxLines = 2)
    }
}

/** Places [content] centred just above the spot (it hides while the spot is off screen or behind the camera). */
@Composable
private fun SpotAnchor(projection: TankProjection, frame: () -> Long, spot: CoralSpotPose, content: @Composable () -> Unit) {
    val pt = remember { FloatArray(3) }
    Box(
        Modifier.layout { m, c ->
            val p = m.measure(c.copy(minWidth = 0, minHeight = 0))
            val w = if (c.hasBoundedWidth) c.maxWidth else p.width
            val h = if (c.hasBoundedHeight) c.maxHeight else p.height
            layout(w, h) {
                frame()
                // above the beacon, so the badge does not cover the glowing ring
                if (projection.project(spot.x, spot.y + BADGE_UP_CM, spot.z, pt)) {
                    val px = (pt[0] - p.width / 2f).roundToInt()
                    val py = (pt[1] - p.height).roundToInt()
                    if (px > -p.width && py > -p.height && px < w && py < h) p.place(px.coerceIn(0, maxOf(0, w - p.width)), py.coerceIn(0, maxOf(0, h - p.height)))
                }
            }
        },
    ) { content() }
}

private const val BADGE_UP_CM = 8.5f
