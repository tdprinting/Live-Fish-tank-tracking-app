package com.reeflive.app.ui.live

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.reeflive.app.ui.components.Glyph
import com.reeflive.app.ui.components.ReefChip
import com.reeflive.app.ui.components.ReefGlyph
import com.reeflive.app.ui.components.ReefGlyphs
import com.reeflive.app.ui.components.StatusIcon
import com.reeflive.app.ui.components.bubbleBurst
import com.reeflive.app.ui.components.pressSquash
import com.reeflive.app.ui.components.rememberHaptics
import com.reeflive.app.ui.components.tapSplash
import com.reeflive.app.ui.theme.GlassCard
import com.reeflive.app.ui.theme.ReefLiveTheme
import com.reeflive.app.ui.theme.ReefMotion
import com.reeflive.app.ui.theme.ReefShapes
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.app.ui.theme.statusText
import com.reeflive.core.status.Status

/**
 * Live Tank HUD (stateless). Lays out top-to-bottom: mode toggle + clock, speed chips, water strip, the round tool
 * buttons, and an events toast pinned to the bottom. Empty space is left untouched so the GL view shows through.
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
    /** Whether the food tray is open, for the [FeedButton]; null hides the button. */
    feedOpen: Boolean? = null,
    onToggleFeed: () -> Unit = {},
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
            if (soundOn != null || cleaning != null || onMicro != null || onHideChrome != null || feedOpen != null) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState(), reverseScrolling = true),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (feedOpen != null) FeedButton(feedOpen, onToggleFeed)
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
 * Watch / Sandbox segmented pill. [onModeChange] fires on every tap; the live screen switches mode, and a tap on the
 * side already picked only brings a tucked menu back (it never hides anything). Sandbox shows coral text
 * to signal "not real" (UI_STYLE §10). Each option carries its glyph: an eye for Watch, a bucket for Sandbox.
 */
@Composable
fun ModeToggle(mode: HudMode, onModeChange: (HudMode) -> Unit, modifier: Modifier = Modifier) {
    val s = LiveTheme.style
    val haptics = rememberHaptics()
    Row(
        modifier.livePanel(s, s.pillShape).padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HudMode.values().forEach { m ->
            val sel = m == mode
            // live theme (UI_STYLE §10.1): the picked side is a candy pill, Sandbox pink to signal "not real"
            val tone = when (m) { HudMode.SANDBOX -> Candy.pink; HudMode.MY_TANK -> Candy.green; HudMode.WATCH -> Candy.cyan }
            val reduce = ReefTheme.reduceMotion
            val fg by animateColorAsState(if (sel) s.onCandy else s.label, ReefMotion.tween(reduce, ReefMotion.FAST), label = "modeFg")
            val bg by animateColorAsState(if (sel) tone else tone.copy(alpha = 0f), ReefMotion.tween(reduce, ReefMotion.FAST), label = "modeBg")
            val grow by animateFloatAsState(if (sel) 1.15f else 1f, ReefMotion.bouncy(reduce), label = "modeGlyph")
            val interaction = remember { MutableInteractionSource() }
            Row(
                Modifier
                    .heightIn(min = 48.dp)
                    .semantics { selected = sel }
                    .pressSquash(interaction)
                    .bubbleBurst(interaction, tone, Color.White)
                    .clip(ReefShapes.pill)
                    .background(bg)
                    .clickable(interactionSource = interaction, indication = null, role = Role.Tab) { haptics.tick(); onModeChange(m) }
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Glyph(
                    when (m) { HudMode.SANDBOX -> ReefGlyphs.bucket; HudMode.MY_TANK -> ReefGlyphs.fish; HudMode.WATCH -> ReefGlyphs.eye },
                    size = 20.dp, tint = fg, accent = if (sel) Color.White else s.labelDim,
                    modifier = Modifier.graphicsLayer { scaleX = grow; scaleY = grow },
                )
                Text(m.label, style = s.buttonText, color = fg)
            }
        }
    }
}

/** A round 48 dp glass tool button: press shrink + water splash, glyph or custom content. */
@Composable
private fun ToolButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    role: Role = Role.Button,
    active: Boolean = false,
    interaction: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    val c = ReefTheme.colors
    val reduce = ReefTheme.reduceMotion
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, ReefMotion.spring(reduce), label = "tool")
    val bg by animateColorAsState(if (active) c.primaryBright.copy(alpha = 0.22f) else c.glass, ReefMotion.tween(reduce, ReefMotion.FAST), label = "toolBg")
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .size(48.dp)
            .clip(ReefShapes.pill)
            .background(bg)
            .tapSplash(interaction, c.primaryBright)
            .clickable(interactionSource = interaction, indication = null, role = role, onClickLabel = label) { onClick() },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** A pill-shaped glass tool button with a glyph and a word. */
@Composable
private fun ToolPill(
    onClick: () -> Unit,
    label: String,
    text: String,
    glyph: ReefGlyph,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    val c = ReefTheme.colors
    val reduce = ReefTheme.reduceMotion
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, ReefMotion.spring(reduce), label = "pill")
    val tone = c.primaryBright
    val bg by animateColorAsState(if (active) tone.copy(alpha = 0.22f) else c.glass, ReefMotion.tween(reduce, ReefMotion.FAST), label = "pillBg")
    val fg by animateColorAsState(if (active) tone else c.text, ReefMotion.tween(reduce, ReefMotion.FAST), label = "pillFg")
    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .heightIn(min = 48.dp)
            .clip(ReefShapes.pill)
            .background(bg)
            .tapSplash(interaction, tone)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = label) { onClick() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Glyph(glyph, size = 20.dp, tint = fg, accent = c.coralSoft)
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

/** Glass pill that opens the microscope (pods and plankton up close). */
@Composable
fun MicroButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    ToolPill(onClick, "Open the microscope", "Micro", ReefGlyphs.microscope, modifier)
}

/**
 * The food tin: opens / closes the food tray. The tin tips over while the tray is open, as if pouring, and rights
 * itself when it closes.
 */
@Composable
fun FeedButton(open: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val c = ReefTheme.colors
    val reduce = ReefTheme.reduceMotion
    val tilt by animateFloatAsState(if (open) -28f else 0f, ReefMotion.bouncy(reduce), label = "tin")
    ToolButton(
        onToggle, if (open) "Close the food tray" else "Feed the fish",
        modifier.semantics { stateDescription = if (open) "Food tray open" else "Food tray closed" },
        active = open,
    ) {
        Glyph(
            ReefGlyphs.feedTin, size = 24.dp, tint = if (open) c.primaryBright else c.text, accent = c.coral,
            contentDescription = "Feed",
            modifier = Modifier.graphicsLayer { rotationZ = tilt },
        )
    }
}

/** Round glass mute / unmute button for the tank sounds: the speaker's waves fade in and out with the setting. */
@Composable
fun SoundButton(on: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val c = ReefTheme.colors
    ToolButton(
        onToggle, if (on) "Mute tank sounds" else "Unmute tank sounds",
        modifier.semantics { stateDescription = if (on) "Tank sounds on" else "Tank sounds off" },
        role = Role.Switch,
    ) {
        SpeakerIcon(on, size = 24.dp, tint = if (on) c.text else c.textSecondary, accent = c.coralSoft)
    }
}

/** Glass pill shown while the controls are hidden: brings them back (tapping the water or Back does too). */
@Composable
fun ShowControlsPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    LivePill(
        "Show buttons", onClick, modifier, tone = Candy.cyan,
        leading = { Glyph(ReefGlyphs.eye, size = 20.dp, tint = LiveTheme.style.onCandy, accent = Color.White) },
    )
}

/** Round glass button that hides all the overlay controls so the whole tank shows: an eye that closes as you tap. */
@Composable
fun HideChromeButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = ReefTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    ToolButton(onClick, "Hide the controls", modifier, interaction = interaction) {
        EyeIcon(open = !pressed, size = 24.dp, tint = c.text, accent = c.coralSoft)
    }
}

@Composable
fun SpeedChips(speeds: List<Double>, speed: Double, onSpeedChange: (Double) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        speeds.forEach { s -> ReefChip(text = formatSpeed(s), selected = s == speed, onClick = { onSpeedChange(s) }) }
    }
}

/** Where the sun sits for the clock's little sky: 0 = moon, 1 = full sun. */
private fun phaseLevel(p: DayPhase): Float = when (p) {
    DayPhase.NIGHT -> 0f
    DayPhase.DAWN -> 0.4f
    DayPhase.DAY -> 1f
    DayPhase.DUSK -> 0.3f
}

@Composable
fun ClockPill(simTimeSec: Double, phase: DayPhase, modifier: Modifier = Modifier) {
    val s = LiveTheme.style
    Row(
        modifier
            .livePanel(s, s.pillShape)
            .semantics { contentDescription = "${formatSimClock(simTimeSec)}, ${phase.label}" }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SunMoonIcon(phaseLevel(phase), size = 20.dp, tint = Candy.yellow, accent = Candy.orange)
        Spacer(Modifier.width(6.dp))
        Text(formatSimClock(simTimeSec), style = s.buttonText, color = s.label)
        Spacer(Modifier.width(6.dp))
        Text(phase.label, style = MaterialTheme.typography.labelMedium, color = s.labelDim)
    }
}

/** Compact water-state strip: one cell per readout, status icon + status-coloured value. */
@Composable
fun WaterStrip(readouts: List<WaterReadout>, modifier: Modifier = Modifier) {
    val s = LiveTheme.style
    Box(modifier.fillMaxWidth().livePanel(s, s.tileShape).padding(horizontal = 10.dp, vertical = 8.dp)) {
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
        Text(r.label, style = MaterialTheme.typography.labelSmall, color = LiveTheme.style.labelDim, maxLines = 1)
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
internal fun statusColor(s: Status): Color = with(ReefTheme.colors) {
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
    Box(Modifier.background(ReefTheme.colors.bg)) {
        LiveOverlay(previewHud, {}, {}, soundOn = true, cleaning = false, onMicro = {}, onHideChrome = {}, feedOpen = false)
    }
}

@Preview(name = "HUD · Lagoon", widthDp = 393, heightDp = 400, showBackground = true)
@Composable
private fun LiveOverlayLightPreview() = ReefLiveTheme(darkTheme = false, reduceMotion = true) {
    Box(Modifier.background(ReefTheme.colors.bg)) { LiveOverlay(previewHud.copy(mode = HudMode.SANDBOX), {}, {}, feedOpen = true) }
}
