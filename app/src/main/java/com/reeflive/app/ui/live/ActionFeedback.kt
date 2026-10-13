package com.reeflive.app.ui.live

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reeflive.app.render.BurstKind
import com.reeflive.app.render.TankBurst
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.core.sim.FoodKind
import kotlinx.coroutines.delay
import java.util.Locale

/*
 * What a menu action tells the owner while it happens (UI_STYLE §10.2): one banner at the top of the tank naming the
 * action and its effect in plain words ("Dosing shell builder · dKH rising"), a live readout of the value it changes
 * that pulses while the banner shows, and a progress bar for the length of the action; a highlight ring on the spot in
 * the tank where it happens (ActionFxLayer); and the in-tank 3D feedback (render/TankBursts.kt): the dye plume, the
 * siphon and refill swirl, the pump jet, the bubbles. One place to look instead of a caption, a picture and Hermie.
 */

/**
 * The feedback for one action: [title] and [effect] for the banner, [bursts] for the 3D tank, [watch] a SandboxParams
 * row id whose live value the banner shows (null = none), [durationMs] how long the banner's progress bar runs.
 */
@Immutable
class Feedback(
    val title: String,
    val effect: String,
    val bursts: List<TankBurst> = emptyList(),
    val watch: String? = null,
    val durationMs: Int = 2600,
) {
    /** The same feedback showing [rowId]'s live value. */
    fun withWatch(rowId: String?) = Feedback(title, effect, bursts, rowId, durationMs)
    fun withEffect(text: String) = Feedback(title, text, bursts, watch, durationMs)
    fun plus(more: List<TankBurst>) = Feedback(title, effect, bursts + more, watch, durationMs)

    companion object {
        /** The usual feedback for an effect picture: its caption as the title, a plain effect line and the matching 3D burst. */
        fun from(fx: TankFx): Feedback {
            val at = fx.at
            val t = fx.tint
            val title = fx.caption ?: ""
            val ms = fx.kind.ms + 400
            return when (fx.kind) {
                FxKind.FEED -> feeding(fx.food ?: FoodKind.PELLET, at, title)
                FxKind.PIPETTE -> Feedback(title, "A little cloud drifts off with the flow", listOf(burst(BurstKind.HAZE, at, t)), durationMs = ms)
                FxKind.DOSE -> Feedback(title.ifEmpty { "Dosing" }, "Dripping in at the return; the flow spreads it", listOf(burst(BurstKind.DYE, at, t)), durationMs = ms)
                FxKind.WATER_CHANGE -> Feedback(
                    title, "Old water and muck out, fresh salt water in: nitrate drops",
                    listOf(burst(BurstKind.WATER_CHANGE, at, amount = (fx.amount * 4f).coerceIn(0.4f, 2.5f))), watch = "no3", durationMs = ms,
                )
                FxKind.HEATER_BREAK -> Feedback(title, "No more heat: the water slowly cools", listOf(burst(BurstKind.SPARKS, FxSpots.HEATER), burst(BurstKind.COOL, FxSpots.HEATER)), watch = "temp", durationMs = ms)
                FxKind.HEATER_FIX -> Feedback(title, "Warming back up to its set point", listOf(burst(BurstKind.HEAT, FxSpots.HEATER)), watch = "temp", durationMs = ms)
                FxKind.HEATER_STUCK -> Feedback(title, "It keeps heating: the water gets hotter and hotter", listOf(burst(BurstKind.HEAT, FxSpots.HEATER, Color(0xFFFF7A4A), 2f)), watch = "temp", durationMs = ms)
                FxKind.THERMO_UP -> Feedback(title, "Water warming", listOf(burst(BurstKind.HEAT, FxSpots.HEATER)), watch = "temp", durationMs = ms)
                FxKind.THERMO_DOWN -> Feedback(title, "Water cooling", listOf(burst(BurstKind.COOL, FxSpots.HEATER)), watch = "temp", durationMs = ms)
                FxKind.SALT_UP -> Feedback(title, "Salt dissolving: salinity going up", listOf(burst(BurstKind.SALT, at)), watch = "sal", durationMs = ms)
                FxKind.SALT_DOWN -> Feedback(title, "Fresh water in: salinity going down", listOf(burst(BurstKind.FRESH, at)), watch = "sal", durationMs = ms)
                FxKind.PUMP_ON -> Feedback(title, "Water moving: see the jet", listOf(burst(BurstKind.PUMP_ON, at)), durationMs = ms)
                FxKind.PUMP_OFF -> Feedback(title, "The jet stops: less flow and less air", listOf(burst(BurstKind.PUMP_OFF, at)), durationMs = ms)
                FxKind.PUMP_FAIL_TONIGHT -> Feedback(
                    title, "Set for 23:00, six hours: speed up time to watch",
                    listOf(burst(BurstKind.SPARKLE, FxSpots.PUMP_LEFT, Candy.orange, 0.5f), burst(BurstKind.SPARKLE, FxSpots.PUMP_RIGHT, Candy.orange, 0.5f)), durationMs = ms,
                )
                FxKind.LIGHTS_ON -> Feedback(title, "Lamps up: corals feed on the light", listOf(burst(BurstKind.LIGHT_MOTES, at)), watch = "light", durationMs = ms)
                FxKind.LIGHTS_OFF -> Feedback(title, "The tank goes dark: night for the fish", watch = "light", durationMs = ms)
                FxKind.LIGHTS_AUTO -> Feedback(title, "Back to the normal day and night", listOf(burst(BurstKind.LIGHT_MOTES, at, amount = 0.5f)), durationMs = ms)
                FxKind.POWER_OUT -> Feedback(
                    title, "Pumps, heater and lamps off: oxygen and heat start to fall",
                    listOf(burst(BurstKind.PUMP_OFF, FxSpots.PUMP_LEFT), burst(BurstKind.PUMP_OFF, FxSpots.PUMP_RIGHT), burst(BurstKind.SPARKS, FxSpots.HEATER)), watch = "do", durationMs = ms,
                )
                FxKind.ALERT -> Feedback(title, "Watch the fish cards and the readouts", listOf(burst(BurstKind.SPARKLE, at, t.orElse(Candy.red), 0.6f)), durationMs = ms)
                FxKind.PEST_ADD -> Feedback(title, "It stings corals and spreads if left alone", listOf(burst(BurstKind.SILT, at, amount = 0.3f)), durationMs = ms)
                FxKind.PEST_REMOVE -> Feedback(title, "A dab of paste and it is gone", listOf(burst(BurstKind.ZAP, at)), durationMs = ms)
                FxKind.BAG -> Feedback(title, "Released at the surface", listOf(burst(BurstKind.BUBBLES, at, t, (0.7f + fx.amount * 0.15f).coerceAtMost(2f))), durationMs = ms)
                FxKind.NET -> Feedback(title, "Netted out of the tank", listOf(burst(BurstKind.BUBBLES, at, amount = 0.5f)), durationMs = ms)
                FxKind.DEVICE_ON -> Feedback(title, "Running", listOf(burst(BurstKind.SPARKLE, FxSpots.DOSE, Candy.green, 0.5f)), durationMs = ms)
                FxKind.DEVICE_OFF -> Feedback(title, "Stopped", listOf(burst(BurstKind.SPARKLE, FxSpots.DOSE, Color(0xFF9AA4B8), 0.4f)), durationMs = ms)
                FxKind.MAINTAIN -> Feedback(title, "The water clears up", listOf(burst(BurstKind.CLARITY, at)), durationMs = ms)
                FxKind.SNIP -> Feedback(title, "Trimming takes nitrate and phosphate out with it", listOf(burst(BurstKind.CLARITY, at, Candy.green)), watch = "no3", durationMs = ms)
                FxKind.SHELLS -> Feedback(title, "Shells settle on the sand for the hermits", listOf(burst(BurstKind.SHELLS, at)), durationMs = ms)
                FxKind.ROCKS -> Feedback(title, "Silt puffs up; everyone looks for a new home spot", listOf(burst(BurstKind.SILT, at, amount = 1.5f)), durationMs = ms)
                FxKind.HEARTS -> Feedback(title, "Everyone is used to the water now", listOf(burst(BurstKind.SPARKLE, at, Candy.green)), durationMs = ms)
                FxKind.TAP_GLASS -> Feedback("Knock on the glass", "The fish startle and dart away", listOf(burst(BurstKind.TAP, at)), durationMs = ms)
                FxKind.GROW -> Feedback(title, if (fx.amount >= 0.5f) "Growing" else "Shrinking", durationMs = ms)
                FxKind.SPEED -> Feedback(title.ifEmpty { "Time" }, "Everything in the tank now runs at this speed", durationMs = ms)
                FxKind.RESET -> Feedback(title, "Water, animals and equipment match your real tank again", listOf(burst(BurstKind.CLARITY, at), burst(BurstKind.SPARKLE, at, Candy.cyan)), durationMs = ms)
                FxKind.SPARKLE -> Feedback(title, "Changed", listOf(burst(BurstKind.SPARKLE, at, t.orElse(Candy.cyan), 0.6f)), durationMs = ms)
                FxKind.TIMELAPSE -> Feedback(title, "Pick how far ahead to look", durationMs = ms)
            }
        }

        /** What each food looks like going in: the effect line, and the splash / plop where it lands. */
        fun feeding(k: FoodKind, at: FxPoint = FxSpots.FEED, title: String = k.label): Feedback {
            val effect = when (k) {
                FoodKind.PELLET -> "Pinched in by the return: the current sweeps it across, then it sinks slowly"
                FoodKind.FLAKE -> "Flakes float a while, then flutter down"
                FoodKind.FLOATING_PELLET -> "Pellets bob on top, then soak up and sink"
                FoodKind.ALGAE_WAFER -> "The wafer sinks for snails, hermits and the tang"
                FoodKind.MYSIS -> "The cube thaws and pale little shrimp drift free"
                FoodKind.BRINE -> "The cube thaws and orange brine shrimp drift out"
                FoodKind.KRILL -> "The cube thaws and krill pieces sink for the big eaters"
                FoodKind.CYCLOPS -> "A red cloud of tiny copepods for small mouths"
                FoodKind.PRAWN -> "A pink chunk sinks fast for the scavengers"
                FoodKind.NORI -> "A sheet on the clip: grazers tear at it all day"
                FoodKind.PODS -> "Live pods hop off toward the rock"
                FoodKind.PLANKTON -> "A green haze drifts through the flow"
                FoodKind.CORAL_FOOD -> "A pink powder cloud for corals and dusters"
            }
            val bursts = when (feedTool(k)) {
                FeedTool.PINCH -> listOf(burst(BurstKind.SPLASH, at, amount = 0.7f))
                FeedTool.SHAKER -> listOf(burst(BurstKind.SPLASH, at))
                FeedTool.CUBE -> listOf(burst(BurstKind.PLOP, at))
                FeedTool.CLIP -> emptyList()
                FeedTool.PIPETTE -> listOf(burst(BurstKind.SPLASH, at, amount = 0.4f))
            }
            return Feedback(title.ifEmpty { k.label }, effect, bursts, durationMs = FxKind.FEED.ms + 600)
        }

        /** A 3D burst at a tank spot with a Compose tint (Unspecified = white). */
        fun burst(kind: BurstKind, at: FxPoint, tint: Color = Color.Unspecified, amount: Float = 1f): TankBurst {
            val c = if (tint == Color.Unspecified) Color.White else tint
            return TankBurst(kind, at.x, at.y, at.z, c.red, c.green, c.blue, amount)
        }

        private fun Color.orElse(c: Color) = if (this == Color.Unspecified) c else this
    }
}

/** The banner's current item; [id] restarts its progress and timer when the same action is picked again. */
@Immutable
class BannerItem(val id: Long, val feedback: Feedback, val tone: Color)

/** Holds the action banner. One per screen: `remember { ActionBannerState() }`. */
@Stable
class ActionBannerState {
    var current by mutableStateOf<BannerItem?>(null)
        private set
    private var serial = 0L

    fun show(f: Feedback, tone: Color = Candy.cyan) { if (f.title.isNotBlank()) current = BannerItem(serial++, f, tone) }
    fun clear(id: Long? = null) { if (id == null || current?.id == id) current = null }
}

/**
 * The action banner: what is happening, what it does, the live value it changes (pulsing) and a progress bar. Stays
 * for the action's length plus a moment to read, then slides away; a new action replaces it.
 */
@Composable
fun ActionBanner(state: ActionBannerState, reading: TankReading?, modifier: Modifier = Modifier) {
    val s = LiveTheme.style
    val reduce = ReefTheme.reduceMotion
    val item = state.current
    var last by remember { mutableStateOf<BannerItem?>(null) }
    if (item != null) last = item
    val progress = remember { Animatable(0f) }
    LaunchedEffect(item?.id) {
        val it = item ?: return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(it.feedback.durationMs, easing = LinearEasing))
        delay(HOLD_MS)
        state.clear(it.id)
    }
    AnimatedVisibility(
        visible = item != null,
        modifier = modifier,
        enter = fadeIn(liveSpec(reduce)) + slideInVertically(liveSpec(reduce)) { -it / 2 },
        exit = fadeOut(liveSpec(reduce)) + slideOutVertically(liveSpec(reduce)) { -it / 2 },
    ) {
        val b = last ?: return@AnimatedVisibility
        val f = b.feedback
        val pulse = rememberInfiniteTransition(label = "bannerPulse")
        val beat by pulse.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(if (reduce) 1 else 700, easing = LinearEasing), RepeatMode.Reverse),
            label = "beat",
        )
        val pulseAmt = if (reduce) 0f else beat
        Column(
            Modifier
                .widthIn(max = 420.dp)
                .livePanel(s, s.pillShape)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .padding(start = 12.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // a pulsing dot in the action's colour: "this is happening now"
                Canvas(Modifier.size(14.dp)) {
                    drawCircle(b.tone.copy(alpha = 0.35f * (1f - pulseAmt)), radius = size.minDimension / 2f * (0.7f + 0.6f * pulseAmt), center = Offset(size.width / 2, size.height / 2))
                    drawCircle(b.tone, radius = size.minDimension / 3.2f)
                }
                Column(Modifier.weight(1f, fill = false)) {
                    Text(f.title, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = s.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(f.effect, style = MaterialTheme.typography.bodySmall, color = s.labelDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                f.watch?.let { id -> SandboxParams.byId(id) }?.let { def ->
                    val v = def.valueIn(reading)
                    val text = String.format(Locale.US, "%.${def.decimals}f", v) + if (def.unit.isNotEmpty()) " ${def.unit}" else ""
                    Box(
                        Modifier
                            .graphicsLayer { val k = 1f + 0.06f * pulseAmt; scaleX = k; scaleY = k }
                            .clip(RoundedCornerShape(50))
                            .background(b.tone.copy(alpha = 0.22f + 0.18f * pulseAmt))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text("${def.shortLabel} $text", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = s.label, maxLines = 1)
                    }
                }
            }
            // progress for the action's length
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(s.label.copy(alpha = 0.12f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.value.coerceIn(0f, 1f))
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(b.tone),
                )
            }
        }
    }
}

private const val HOLD_MS = 1600L
