package com.reeflive.app.ui.live

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.reeflive.app.ui.components.GlassIconButton
import com.reeflive.app.ui.theme.GlassCard
import com.reeflive.app.ui.theme.ReefLiveTheme
import com.reeflive.app.ui.theme.ReefShapes
import com.reeflive.app.ui.theme.ReefTheme

/** Minimal close-up mode overlay: exit at top-right, name + behaviour line and prev/next at the bottom. */
@Composable
fun FollowOverlay(
    state: FollowState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ReefTheme.colors
    Box(modifier.fillMaxSize().padding(16.dp)) {
        GlassIconButton(Icons.Rounded.Close, "Exit follow mode", onExit, Modifier.align(Alignment.TopEnd))
        GlassCard(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            shape = ReefShapes.card,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                GlassIconButton(Icons.Rounded.ChevronLeft, "Previous fish", onPrevious, tint = if (state.count > 1) null else c.textTertiary)
                Column(Modifier.weight(1f).padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.fishName, style = MaterialTheme.typography.headlineSmall, color = c.text, maxLines = 1)
                    Text(state.speciesName, style = MaterialTheme.typography.labelMedium, color = c.textSecondary, maxLines = 1)
                    Text(
                        state.behaviorLine,
                        style = MaterialTheme.typography.bodyMedium, color = c.text, textAlign = TextAlign.Center, maxLines = 2,
                    )
                    if (state.count > 1) {
                        Text("${state.index + 1} / ${state.count}", style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                    }
                }
                GlassIconButton(Icons.Rounded.ChevronRight, "Next fish", onNext, tint = if (state.count > 1) null else c.textTertiary)
            }
        }
    }
}

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
