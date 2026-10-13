package com.reeflive.app.ui.live

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.reeflive.app.ui.components.ReefChip
import com.reeflive.app.ui.components.StatusIcon
import com.reeflive.app.ui.theme.GlassCard
import com.reeflive.app.ui.theme.ReefLiveTheme
import com.reeflive.app.ui.theme.ReefMotion
import com.reeflive.app.ui.theme.ReefShapes
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.app.ui.theme.statusText
import com.reeflive.core.status.Status

/**
 * Live Tank HUD (stateless). Lays out top-to-bottom: mode toggle + clock, speed chips, water strip, sound button,
 * and an events toast pinned to the bottom. Empty space is left untouched so the GL view shows through.
 */
@Composable
fun LiveOverlay(
    state: HudState,
    onModeChange: (HudMode) -> Unit,
    onSpeedChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    onToastDismiss: (Long) -> Unit = {},
    showSpeed: Boolean = true,
    /** Tank sounds on/off for the quick mute button; null hides the button. */
    soundOn: Boolean? = null,
    onToggleSound: () -> Unit = {},
    /** Clean-glass mode for the [CleanGlassButton]; null hides the button. */
    cleaning: Boolean? = null,
    onToggleCleaning: () -> Unit = {},
    /** Opens the microscope view; null hides the button. */
    onMicro: (() -> Unit)? = null,
    /** Hides every control so only the tank shows (tap the tank or "Show controls" to bring them back); null hides the button. */
    onHideChrome: (() -> Unit)? = null,
) {
    Box(modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.fillMaxWidth().align(Alignment.TopStart), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                ModeToggle(state.mode, onModeChange)
                ClockPill(state.simTimeSec, state.dayPhase)
            }
            if (showSpeed) SpeedChips(state.speeds, state.speed, onSpeedChange)
            if (state.readouts.isNotEmpty()) WaterStrip(state.readouts)
            // under the strip at the right edge: the top row has no room for them on a phone
            if (soundOn != null || cleaning != null || onMicro != null || onHideChrome != null) {
                Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (onMicro != null) MicroButton(onMicro)
                    if (cleaning != null) CleanGlassButton(cleaning, onToggleCleaning)
                    if (soundOn != null) SoundButton(soundOn, onToggleSound)
                    if (onHideChrome != null) HideChromeButton(onHideChrome)
                }
            }
        }
        AnimatedVisibility(
            visible = state.toast != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            state.toast?.let { EventToast(it, onDismiss = { onToastDismiss(it.id) }) }
        }
    }
}

/**
 * Watch / Sandbox segmented pill. Selecting Sandbox opens the inline sandbox panel; tapping Sandbox again while it is
 * selected collapses / expands it (the screen decides, [onModeChange] fires on every tap). Sandbox shows coral text
 * to signal "not real" (UI_STYLE §10).
 */
@Composable
fun ModeToggle(mode: HudMode, onModeChange: (HudMode) -> Unit, modifier: Modifier = Modifier) {
    val c = ReefTheme.colors
    Row(
        modifier.clip(ReefShapes.pill).background(c.glass).padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HudMode.values().forEach { m ->
            val sel = m == mode
            val selColor = if (m == HudMode.SANDBOX) c.coral else c.primaryBright
            val reduce = ReefTheme.reduceMotion
            val fg by animateColorAsState(if (sel) selColor else c.textSecondary, ReefMotion.tween(reduce, ReefMotion.FAST), label = "modeFg")
            val bg by animateColorAsState(if (sel) selColor.copy(alpha = 0.22f) else selColor.copy(alpha = 0f), ReefMotion.tween(reduce, ReefMotion.FAST), label = "modeBg")
            Text(
                m.label,
                style = MaterialTheme.typography.labelLarge,
                color = fg,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { selected = sel }
                    .clip(ReefShapes.pill)
                    .background(bg)
                    .clickable(role = Role.Tab) { onModeChange(m) }
                    .padding(horizontal = 16.dp, vertical = 13.dp),
            )
        }
    }
}

/** Glass pill that opens the microscope (pods and plankton up close). */
@Composable
fun MicroButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = ReefTheme.colors
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clip(ReefShapes.pill)
            .background(c.glass)
            .clickable(role = Role.Button, onClickLabel = "Open the microscope") { onClick() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = c.text, modifier = Modifier.size(18.dp))
        Text("Micro", style = MaterialTheme.typography.labelLarge, color = c.text)
    }
}

/** Round glass mute / unmute button for the tank sounds. */
@Composable
fun SoundButton(on: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val c = ReefTheme.colors
    Box(
        modifier
            .size(48.dp)
            .clip(ReefShapes.pill)
            .background(c.glass)
            .clickable(role = Role.Switch, onClickLabel = if (on) "Mute tank sounds" else "Unmute tank sounds") { onToggle() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (on) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
            contentDescription = if (on) "Tank sounds on" else "Tank sounds off",
            tint = if (on) c.text else c.textSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Glass pill shown while the controls are hidden: brings them back (tapping empty water does too). */
@Composable
fun ShowControlsPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = ReefTheme.colors
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clip(ReefShapes.pill)
            .background(c.glass)
            .clickable(role = Role.Button, onClickLabel = "Show the controls") { onClick() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Rounded.Visibility, contentDescription = null, tint = c.text, modifier = Modifier.size(18.dp))
        Text("Show controls", style = MaterialTheme.typography.labelLarge, color = c.text)
    }
}

/** Round glass button that hides all the overlay controls so the whole tank shows. */
@Composable
fun HideChromeButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = ReefTheme.colors
    Box(
        modifier
            .size(48.dp)
            .clip(ReefShapes.pill)
            .background(c.glass)
            .clickable(role = Role.Button, onClickLabel = "Hide the controls") { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.VisibilityOff, contentDescription = "Hide controls", tint = c.text, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun SpeedChips(speeds: List<Double>, speed: Double, onSpeedChange: (Double) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        speeds.forEach { s -> ReefChip(text = formatSpeed(s), selected = s == speed, onClick = { onSpeedChange(s) }) }
    }
}

private fun phaseIcon(p: DayPhase): ImageVector = when (p) {
    DayPhase.NIGHT -> Icons.Rounded.DarkMode
    DayPhase.DAY -> Icons.Rounded.LightMode
    DayPhase.DAWN, DayPhase.DUSK -> Icons.Rounded.WbTwilight
}

@Composable
fun ClockPill(simTimeSec: Double, phase: DayPhase, modifier: Modifier = Modifier) {
    val c = ReefTheme.colors
    Row(
        modifier.clip(ReefShapes.pill).background(c.glass).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(phaseIcon(phase), contentDescription = phase.label, tint = c.coralSoft, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(formatSimClock(simTimeSec), style = MaterialTheme.typography.labelLarge, color = c.text)
        Spacer(Modifier.width(6.dp))
        Text(phase.label, style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
    }
}

/** Compact water-state strip: one cell per readout, status icon + status-coloured value. */
@Composable
fun WaterStrip(readouts: List<WaterReadout>, modifier: Modifier = Modifier) {
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = ReefShapes.tile,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            readouts.forEach { WaterCell(it) }
        }
    }
}

@Composable
private fun WaterCell(r: WaterReadout) {
    val c = ReefTheme.colors
    Column(
        Modifier.semantics { contentDescription = "${r.label} ${r.valueText}" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(r.label, style = MaterialTheme.typography.labelSmall, color = c.textTertiary, maxLines = 1)
        Text(r.valueText, style = MaterialTheme.typography.labelLarge, color = c.statusText(r.status), maxLines = 1)
        StatusIcon(r.status, size = 12.dp)
    }
}

@Composable
fun EventToast(toast: HudToast, modifier: Modifier = Modifier, onDismiss: () -> Unit = {}) {
    val c = ReefTheme.colors
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = ReefShapes.tile,
        tint = if (toast.severity == Status.UNKNOWN) null else statusColor(toast.severity),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        onClick = onDismiss,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusIcon(toast.severity)
            Spacer(Modifier.width(10.dp))
            Text(toast.text, style = MaterialTheme.typography.bodyMedium, color = c.text, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
internal fun statusColor(s: Status) = with(ReefTheme.colors) {
    when (s) {
        Status.GOOD -> good
        Status.CAUTION -> caution
        Status.DANGER -> danger
        Status.UNKNOWN -> unknown
    }
}

// ---------------------------------------------------------------- previews

internal val previewHud = HudState(
    mode = HudMode.WATCH,
    speed = 60.0,
    simTimeSec = 2 * 86_400.0 + 14 * 3600 + 25 * 60,
    dayPhase = DayPhase.DUSK,
    readouts = listOf(
        WaterReadout("temp", "Temp", "25.2°", Status.GOOD),
        WaterReadout("sal", "Sal", "1.025", Status.GOOD),
        WaterReadout("no3", "NO3", "18.0", Status.CAUTION),
        WaterReadout("po4", "PO4", "0.18", Status.DANGER),
        WaterReadout("light", "Light", "45%", Status.GOOD),
        WaterReadout("flow", "Flow", "1.0x", Status.GOOD),
    ),
    toast = HudToast(1, "Pixel the clownfish is hungry", Status.CAUTION),
)

@Preview(name = "HUD · Abyss", widthDp = 393, heightDp = 400, showBackground = true)
@Composable
private fun LiveOverlayDarkPreview() = ReefLiveTheme(darkTheme = true, reduceMotion = true) {
    Box(Modifier.background(ReefTheme.colors.bg)) { LiveOverlay(previewHud, {}, {}, soundOn = true) }
}

@Preview(name = "HUD · Lagoon", widthDp = 393, heightDp = 400, showBackground = true)
@Composable
private fun LiveOverlayLightPreview() = ReefLiveTheme(darkTheme = false, reduceMotion = true) {
    Box(Modifier.background(ReefTheme.colors.bg)) { LiveOverlay(previewHud.copy(mode = HudMode.SANDBOX), {}, {}) }
}
