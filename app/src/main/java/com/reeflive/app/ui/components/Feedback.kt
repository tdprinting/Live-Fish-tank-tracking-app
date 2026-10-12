package com.reeflive.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.reeflive.app.ui.theme.ReefShapes
import com.reeflive.app.ui.theme.ReefTheme

/** App-wide snackbar host, provided at the root so any screen can confirm an action. */
val LocalSnackbar = staticCompositionLocalOf { SnackbarHostState() }

/** Settings → Haptics. */
val LocalHapticsEnabled = staticCompositionLocalOf { true }

/** Haptic vocabulary (UI_STYLE §7) mapped onto the platform types Compose exposes. */
class ReefHaptics(private val feedback: HapticFeedback, private val enabled: Boolean) {
    /** Save, complete, FAB press. */
    fun confirm() {
        if (enabled) feedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    /** Tab switch, chart scrub ticks. */
    fun tick() {
        if (enabled) feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
}

@Composable
fun rememberHaptics(): ReefHaptics {
    val fb = LocalHapticFeedback.current
    val enabled = LocalHapticsEnabled.current
    return remember(fb, enabled) { ReefHaptics(fb, enabled) }
}

/** Glass snackbar (UI_STYLE §6.8): foam text, lagoonBright action. */
@Composable
fun GlassSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data -> GlassSnackbar(data) }
}

@Composable
private fun GlassSnackbar(data: SnackbarData) {
    val c = ReefTheme.colors
    Box(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .shadow(12.dp, ReefShapes.overlay, ambientColor = c.shadow, spotColor = c.shadow)
            .clip(ReefShapes.overlay)
            .background(c.sheet)
            .border(1.dp, c.glassBorder, ReefShapes.overlay)
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                data.visuals.message,
                style = MaterialTheme.typography.bodyMedium,
                color = c.text,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
            )
            val label = data.visuals.actionLabel
            if (label != null) {
                Spacer(Modifier.padding(start = 8.dp))
                TextAction(label, onClick = { data.performAction() })
            }
        }
    }
}
