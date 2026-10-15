package com.reeflive.app.ui.live

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.reeflive.app.ui.components.GlassIconButton
import com.reeflive.app.ui.components.Glyph
import com.reeflive.app.ui.components.ReefGlyphs
import com.reeflive.app.ui.theme.GlassCard
import com.reeflive.app.ui.theme.ReefLiveTheme
import com.reeflive.app.ui.theme.ReefShapes
import com.reeflive.app.ui.theme.ReefTheme
import kotlinx.coroutines.delay

/**
 * Minimal close-up mode overlay: a labelled "Back to tank" pill and the gesture hint just above the bottom panel
 * (name + behaviour line and prev/next). The exit used to be a small × in the top-right corner, where it sat on top of
 * the clock pill and was hard to find; system Back exits too.
 */
@Composable
fun FollowOverlay(
    state: FollowState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ReefTheme.colors
    // (no dispatcher in @Preview)
    if (LocalOnBackPressedDispatcherOwner.current != null) BackHandler(onBack = onExit)
    var hint by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(HINT_MS); hint = false }
    Box(modifier.fillMaxSize().padding(16.dp)) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimatedVisibility(hint, Modifier.align(Alignment.CenterHorizontally), enter = fadeIn(), exit = fadeOut()) {
                LivePanel(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(
                        "Drag to look around · pinch to get closer · double-tap for a close-up",
                        style = MaterialTheme.typography.labelMedium, color = c.textSecondary, textAlign = TextAlign.Center,
                    )
                }
            }
            LivePill(
                "Back to tank", onExit, Modifier.align(Alignment.End), tone = Candy.cyan,
                leading = { Glyph(ReefGlyphs.close, size = 16.dp, tint = LiveTheme.style.onCandy) },
            )
            LivePanel(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    GlassIconButton(Icons.Rounded.ChevronLeft, "Previous fish", onPrevious, tint = if (state.count > 1) null else c.textTertiary)
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.fishName, style = LiveTheme.style.titleText, color = LiveTheme.style.label, maxLines = 1)
                        Text(state.speciesName, style = MaterialTheme.typography.labelMedium, color = c.textSecondary, maxLines = 1)
                        if (state.thought.now.isNotBlank()) {
                            ThinkingBlock(state.id.ifBlank { state.fishName }, state.thought, Modifier.fillMaxWidth(), centered = true)
                        } else {
                            Text(
                                state.behaviorLine,
                                style = MaterialTheme.typography.bodyMedium, color = c.text, textAlign = TextAlign.Center, maxLines = 2,
                            )
                        }
                        if (state.count > 1) {
                            Text("${state.index + 1} / ${state.count}", style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                        }
                    }
                    GlassIconButton(Icons.Rounded.ChevronRight, "Next fish", onNext, tint = if (state.count > 1) null else c.textTertiary)
                }
            }
        }
    }
}

private const val HINT_MS = 4000L

private val previewFollow = FollowState("Pixel", "Ocellaris clownfish", "Falco hawkfish eyeing the skunk cleaner shrimp", 1, 5)

@Preview(name = "Follow · Abyss", widthDp = 393, heightDp = 400, showBackground = true)
@Composable
private fun FollowDarkPreview() = ReefLiveTheme(darkTheme = true, reduceMotion = true) {
    Box(Modifier.background(ReefTheme.colors.bg)) { FollowOverlay(previewFollow, {}, {}, {}) }
}

@Preview(name = "Follow · Lagoon", widthDp = 393, heightDp = 400, showBackground = true)
@Composable
private fun FollowLightPreview() = ReefLiveTheme(darkTheme = false, reduceMotion = true) {
    Box(Modifier.background(ReefTheme.colors.bg)) { FollowOverlay(previewFollow, {}, {}, {}) }
}
