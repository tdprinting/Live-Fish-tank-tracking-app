package com.reeflive.app.ui.live

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.reeflive.app.ui.components.Glyph
import com.reeflive.app.ui.components.ReefGlyphs
import com.reeflive.app.ui.theme.ReefLiveTheme
import com.reeflive.app.ui.theme.ReefTheme
import kotlinx.coroutines.delay

/**
 * Close-up (follow) mode overlay, kept to the edges so the fish stays in view:
 * - big labelled camera buttons down the right edge (in the bottom row on short screens): Closer, Further and Turn
 *   (swing round the fish 45°); each eases the camera, and at the closest / furthest framing it gives a little and
 *   bounces back, so a press always shows;
 * - a labelled "Stop following" pill just above the card (system Back exits too);
 * - a compact name card at the bottom: big previous / next arrows round the name and one line of what it is doing
 *   (tap the card for its thoughts);
 * - a hint for the first few seconds: drag to look around, tap another fish to follow it.
 */
@Composable
fun FollowOverlay(
    state: FollowState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    /** Zoom buttons: +1 closer, −1 further out; null hides them. */
    onZoom: ((Float) -> Unit)? = null,
    /** Turn button: +1 swings round the fish one step; null hides it. */
    onOrbit: ((Float) -> Unit)? = null,
) {
    val s = LiveTheme.style
    // (no dispatcher in @Preview)
    if (LocalOnBackPressedDispatcherOwner.current != null) BackHandler(onBack = onExit)
    var hint by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(HINT_MS); hint = false }
    var expanded by remember(state.id) { mutableStateOf(false) }
    val hasCam = onZoom != null || onOrbit != null
    val camButtons: @Composable () -> Unit = {
        if (onZoom != null) {
            CamButton(Icons.Rounded.Add, "Closer", Candy.green) { onZoom(1f) }
            CamButton(Icons.Rounded.Remove, "Further", Candy.yellow) { onZoom(-1f) }
        }
        if (onOrbit != null) CamButton(Icons.Rounded.Refresh, "Turn", Candy.pink) { onOrbit(1f) }
    }
    BoxWithConstraints(modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 12.dp)) {
        // tall screens: the camera buttons down the right edge, where a thumb rests; short (landscape): in the bottom row
        val short = maxHeight < 520.dp
        if (hasCam && !short) {
            Column(
                Modifier.align(Alignment.CenterEnd),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { camButtons() }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).widthIn(max = 560.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimatedVisibility(hint, Modifier.align(Alignment.CenterHorizontally), enter = fadeIn(), exit = fadeOut()) {
                LivePanel(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(
                        "Drag to look around · tap another fish to follow it",
                        style = MaterialTheme.typography.labelMedium, color = s.label, textAlign = TextAlign.Center,
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                verticalAlignment = Alignment.Bottom,
            ) {
                if (hasCam && short) camButtons()
                LivePill(
                    "Stop following", onExit, tone = Candy.cyan,
                    leading = { Glyph(ReefGlyphs.close, size = 16.dp, tint = s.onCandy) },
                )
            }
            LivePanel(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                onClick = { expanded = !expanded },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // only with someone to step to: a lone fish gets no arrows that would do nothing
                    if (state.count > 1) {
                        LiveRailButton(null, "Previous fish", Candy.cyan, onPrevious, size = 48.dp, showLabel = false) {
                            Icon(Icons.Rounded.ChevronLeft, "Previous fish", tint = s.onCandy, modifier = Modifier.size(30.dp))
                        }
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                state.fishName, style = s.titleText, color = s.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (state.count > 1) {
                                Text("  ${state.index + 1}/${state.count}", style = MaterialTheme.typography.labelSmall, color = s.labelDim, maxLines = 1)
                            }
                        }
                        val line = state.thought.now.ifBlank { state.behaviorLine }
                        if (expanded && state.thought.now.isNotBlank()) {
                            ThinkingBlock(state.id.ifBlank { state.fishName }, state.thought, Modifier.fillMaxWidth(), centered = true)
                        } else if (line.isNotBlank()) {
                            Text(
                                line, style = MaterialTheme.typography.bodySmall, color = s.label, textAlign = TextAlign.Center,
                                maxLines = if (expanded) 3 else 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    // only with someone to step to: a lone fish gets no arrows that would do nothing
                    if (state.count > 1) {
                        LiveRailButton(null, "Next fish", Candy.cyan, onNext, size = 48.dp, showLabel = false) {
                            Icon(Icons.Rounded.ChevronRight, "Next fish", tint = s.onCandy, modifier = Modifier.size(30.dp))
                        }
                    }
                }
            }
        }
    }
}

/** One big labelled follow-camera button (a candy bubble with a navy icon). */
@Composable
private fun CamButton(icon: ImageVector, label: String, tone: Color, onClick: () -> Unit) {
    val s = LiveTheme.style
    LiveRailButton(null, label, tone, onClick, size = 52.dp, compactLabel = true) {
        Icon(icon, label, tint = s.onCandy, modifier = Modifier.size(28.dp))
    }
}

private const val HINT_MS = 4000L

private val previewFollow = FollowState("Pixel", "Ocellaris clownfish", "Falco hawkfish eyeing the skunk cleaner shrimp", 1, 5)

@Preview(name = "Follow · Abyss", widthDp = 393, heightDp = 400, showBackground = true)
@Composable
private fun FollowDarkPreview() = ReefLiveTheme(darkTheme = true, reduceMotion = true) {
    Box(Modifier.background(ReefTheme.colors.bg)) { FollowOverlay(previewFollow, {}, {}, {}, onZoom = {}, onOrbit = {}) }
}

@Preview(name = "Follow · Lagoon", widthDp = 393, heightDp = 400, showBackground = true)
@Composable
private fun FollowLightPreview() = ReefLiveTheme(darkTheme = false, reduceMotion = true) {
    Box(Modifier.background(ReefTheme.colors.bg)) { FollowOverlay(previewFollow, {}, {}, {}, onZoom = {}, onOrbit = {}) }
}
