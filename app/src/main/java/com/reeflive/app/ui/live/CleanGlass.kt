package com.reeflive.app.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.reeflive.app.ui.theme.GlassCard
import com.reeflive.app.ui.theme.ReefLiveTheme
import com.reeflive.app.ui.theme.ReefShapes
import com.reeflive.app.ui.theme.ReefTheme

/**
 * HUD pill that enters / leaves clean-glass mode: the camera squares up to the front glass and dragging a finger slides
 * the Flipper magnet scraper over it, clearing the algae film it passes (the stripes grow back over days).
 */
@Composable
fun CleanGlassButton(cleaning: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val c = ReefTheme.colors
    val tone = c.primaryBright
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clip(ReefShapes.pill)
            .background(if (cleaning) tone.copy(alpha = 0.22f) else c.glass)
            .clickable(role = Role.Button, onClickLabel = if (cleaning) "Stop cleaning the glass" else "Clean the glass with the Flipper") { onToggle() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (cleaning) Icons.Rounded.Check else Icons.Rounded.TouchApp,
            contentDescription = null,
            tint = if (cleaning) tone else c.text,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            if (cleaning) "Done" else "Clean glass",
            style = MaterialTheme.typography.labelLarge,
            color = if (cleaning) tone else c.text,
        )
    }
}

/** Bottom hint while clean-glass mode is on. */
@Composable
fun CleanGlassHint(modifier: Modifier = Modifier) {
    val c = ReefTheme.colors
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = ReefShapes.tile,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Column {
            Text("Drag the Flipper across the glass", style = MaterialTheme.typography.titleSmall, color = c.text)
            Text(
                "The pad scrapes off the film it passes. Bare stripes grow back over a few days.",
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
            )
        }
    }
}

@Preview(name = "Clean glass · Abyss", widthDp = 393, heightDp = 200, showBackground = true)
@Composable
private fun CleanGlassDarkPreview() = ReefLiveTheme(darkTheme = true, reduceMotion = true) {
    Box(Modifier.background(ReefTheme.colors.bg).padding(16.dp)) {
        Column {
            Row { CleanGlassButton(false, {}); Spacer(Modifier.width(8.dp)); CleanGlassButton(true, {}) }
            Spacer(Modifier.height(12.dp))
            CleanGlassHint()
        }
    }
}
