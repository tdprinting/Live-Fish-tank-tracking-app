package com.reeflive.app.ui.live

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.reeflive.app.render.TankProjection
import com.reeflive.app.ui.components.ReefGlyph
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.core.sim.FoodKind
import kotlinx.coroutines.flow.first

/*
 * In-tank action animations (UI_STYLE §10.2). Every button that changes the tank plays a short picture of the action
 * in or over the water: a food bottle tapping pellets in, a dropper dripping a coloured swirl, a bucket and siphon
 * lowering the waterline, a sparking heater, a bag releasing a new fish, a whoosh on reset. They are drawn in one
 * Compose Canvas laid exactly over the GL view and positioned in tank centimetres through the renderer's
 * [TankProjection], so the bottle hangs over the real feeding spot from any camera angle.
 *
 * Timing: an effect may carry an `onImpact` callback that the screen uses to send the sim command at the moment the
 * picture reaches the water (food lands, the drain finishes, the bag opens), so the real particles carry on the motion.
 * Effects last 1–3 s, run only while something is playing (no frame loop when idle), read their clock in the draw
 * phase only (the GL view never recomposes) and never take a touch: a tap on the tank skips them ([skipAll]).
 * Reduce motion: a 0.7 s fade of the effect's key pose, and the command goes out at once.
 */

/** A spot in the tank, in world cm (origin inside bottom-front-left; +y up, +z toward the viewer). */
@Immutable
data class FxPoint(val x: Float, val y: Float, val z: Float)

/** Where things happen, matched to the sim's own anchors (core RockData / Simulation) so pictures and particles agree. */
object FxSpots {
    /** SURFACE_FEED at the waterline: where `Command.Feed` with no point spawns its particles. */
    val FEED = FxPoint(41f, 55.7f, 20f)
    /** NORI_CLIP on the left glass. */
    val NORI = FxPoint(2f, 35f, 18f)
    /** HOB_OUTFLOW: `Command.Dose` enters here and spreads with the flow. */
    val DOSE = FxPoint(71f, 55.7f, 3f)
    /** Where AddOrganism / AddMany release fish (tank middle, just under the surface). */
    val RELEASE = FxPoint(61f, 55.7f, 20f)
    val SURFACE = FxPoint(61f, 55.7f, 18f)
    val CENTRE = FxPoint(61f, 32f, 18f)
    val HEATER = FxPoint(7f, 30f, 4f)
    val THERMO = FxPoint(18f, 44f, 14f)
    val PUMP_LEFT = FxPoint(3f, 45f, 16f)
    val PUMP_RIGHT = FxPoint(119f, 45f, 16f)
    /** Above the tank: the lamp. */
    val LIGHT = FxPoint(61f, 66f, 16f)
    /** The sump equipment lives under the tank, out of view: shown on the back-right corner above the water. */
    val SUMP = FxPoint(108f, 62f, 3f)
    val ROCK = FxPoint(72f, 28f, 15f)
    /** coral_aiptasia_kenya_base. */
    val AIPTASIA = FxPoint(68f, 37f, 14f)
    val SAND = FxPoint(58f, 4f, 26f)
    val GLASS = FxPoint(61f, 30f, 33f)
    /** Water change: the siphon end in the water and the bucket outside the right-hand end. */
    val SIPHON = FxPoint(104f, 44f, 20f)
    val BUCKET = FxPoint(132f, 58f, 22f)
}

/** What picture to play, with its length and the moment the action "happens" (ms). */
enum class FxKind(val ms: Int, val impactMs: Int) {
    FEED(2600, 1300),
    DOSE(2400, 1050),
    PIPETTE(2200, 650),
    WATER_CHANGE(3600, 1500),
    HEATER_BREAK(2400, 450),
    HEATER_FIX(1800, 300),
    HEATER_STUCK(2600, 300),
    THERMO_UP(2000, 200),
    THERMO_DOWN(2000, 200),
    SALT_UP(2200, 800),
    SALT_DOWN(2400, 800),
    PUMP_ON(1800, 200),
    PUMP_OFF(2000, 200),
    PUMP_FAIL_TONIGHT(2200, 200),
    LIGHTS_ON(1700, 200),
    LIGHTS_OFF(2000, 200),
    LIGHTS_AUTO(1600, 200),
    POWER_OUT(2800, 450),
    ALERT(2000, 150),
    PEST_ADD(2000, 300),
    PEST_REMOVE(2000, 900),
    BAG(2900, 1700),
    NET(2000, 900),
    DEVICE_ON(1600, 150),
    DEVICE_OFF(1600, 150),
    MAINTAIN(2200, 1000),
    SNIP(2000, 900),
    SHELLS(2000, 700),
    ROCKS(1800, 250),
    HEARTS(1800, 150),
    TAP_GLASS(900, 120),
    GROW(1400, 100),
    SPEED(1100, 0),
    RESET(1500, 550),
    SPARKLE(1300, 100),
    TIMELAPSE(1600, 0),
}

/**
 * One effect to play. [tint] colours drops, swirls and pulses; [food] picks the container for FEED; [glyph] is the
 * device, animal or pest shown; [caption] is a short plain-words line under the picture; [amount] scales it (water
 * change fraction, number of animals); [key] coalesces repeats (a dragged slider plays once, not every step).
 */
@Immutable
class TankFx(
    val kind: FxKind,
    val at: FxPoint,
    val tint: Color = Color.Unspecified,
    val food: FoodKind? = null,
    val glyph: ReefGlyph? = null,
    val caption: String? = null,
    val amount: Float = 1f,
    val key: String? = null,
    /** Second tone (dose colour on the bottle label, old vs new equipment). */
    val tint2: Color = Color.Unspecified,
)

/** A playing effect. Plain fields: only [ActionFxState.frameNs] and the list itself are snapshot state. */
internal class ActiveFx(val id: Long, val fx: TankFx, val onImpact: (() -> Unit)?) {
    var startNs = -1L
    var impacted = false
    var fadeFromNs = -1L
    var captionLayout: TextLayoutResult? = null

    fun durationMs(reduce: Boolean): Long = if (reduce) REDUCED_MS else fx.kind.ms.toLong()
    fun impactMs(reduce: Boolean): Long = if (reduce) 0L else fx.kind.impactMs.toLong()

    fun fireImpact() {
        if (!impacted) { impacted = true; onImpact?.invoke() }
    }

    companion object {
        const val REDUCED_MS = 700L
    }
}

/**
 * Plays [TankFx]s. Create one per screen (`remember { ActionFxState() }`), draw it with [ActionFxLayer], and call
 * [play] from button handlers. [flush] runs every pending impact at once (the screen calls it on pause / dispose, so
 * no command is lost if the picture never finishes).
 */
@Stable
class ActionFxState {
    internal val active = mutableStateListOf<ActiveFx>()
    /** Frame clock of the effect loop; read only inside the layer's draw lambda. */
    internal var frameNs by mutableLongStateOf(0L)
        private set
    internal var reduce = false
    private var nextId = 0L

    /** True while anything is playing (for semantics / tests). */
    val playing: Boolean get() = active.isNotEmpty()

    /**
     * Plays [fx]; [onImpact] runs when the picture reaches the water (or at once under Reduce motion, or when the
     * effect is skipped). A repeat of a playing keyed effect only runs its [onImpact].
     */
    fun play(fx: TankFx, onImpact: (() -> Unit)? = null) {
        val k = fx.key
        if (k != null && active.any { it.fx.key == k && it.fadeFromNs < 0 }) { onImpact?.invoke(); return }
        while (active.size >= MAX_ACTIVE) finish(active.first())
        active += ActiveFx(nextId++, fx, onImpact)
    }

    /** True while an effect with [key] plays (not yet skipped). */
    fun isPlaying(key: String): Boolean = active.any { it.fx.key == key && it.fadeFromNs < 0 }

    /** Tap on the tank: every effect sends its command now and fades out fast. */
    fun skipAll() {
        for (a in active.toList()) {
            a.fireImpact()
            if (a.fadeFromNs < 0) a.fadeFromNs = frameNs
        }
    }

    /** Sends every pending command and clears the screen. */
    fun flush() {
        for (a in active.toList()) a.fireImpact()
        active.clear()
    }

    private fun finish(a: ActiveFx) {
        a.fireImpact()
        active.remove(a)
    }

    internal suspend fun run() {
        while (true) {
            if (active.isEmpty()) snapshotFlow { active.isEmpty() }.first { !it }
            withFrameNanos { t -> step(t) }
        }
    }

    private fun step(t: Long) {
        frameNs = t
        for (a in active.toList()) {
            if (a.startNs < 0) a.startNs = t
            val e = (t - a.startNs) / 1_000_000L
            if (e >= a.impactMs(reduce)) a.fireImpact()
            val skippedOut = a.fadeFromNs >= 0 && (t - a.fadeFromNs) / 1_000_000L >= SKIP_FADE_MS
            if (e >= a.durationMs(reduce) || skippedOut) finish(a)
        }
    }

    companion object {
        const val MAX_ACTIVE = 5
        const val SKIP_FADE_MS = 180L
    }
}

/**
 * Draws the playing effects. Lay it out at exactly the position and size of the TankView so view pixels and canvas
 * pixels agree. [screenOnly] places effects by fixed screen fractions instead of the camera (the microscope covers the
 * tank). No pointer input: touches go straight through to the tank.
 */
@Composable
fun ActionFxLayer(
    state: ActionFxState,
    projection: TankProjection?,
    modifier: Modifier = Modifier,
    screenOnly: Boolean = false,
) {
    val reduce = ReefTheme.reduceMotion
    SideEffect { state.reduce = reduce }
    LaunchedEffect(state) { state.run() }
    DisposableEffect(state) { onDispose { state.flush() } }
    val pal = LiveTheme.style.fx
    val measurer = rememberTextMeasurer()
    val captionStyle = MaterialTheme.typography.labelLarge.copy(color = pal.caption, fontWeight = FontWeight.Bold)
    val pt = remember { FloatArray(3) }
    Canvas(modifier.fillMaxSize()) {
        val now = state.frameNs
        val list = state.active
        if (list.isEmpty()) return@Canvas
        val project: (Float, Float, Float) -> Offset? = { x, y, z ->
            if (!screenOnly && projection != null && projection.project(x, y, z, pt)) Offset(pt[0], pt[1]) else fallback(x, y)
        }
        for (a in list) {
            if (a.startNs < 0 || now < a.startNs) continue
            val ms = (now - a.startNs) / 1_000_000f
            val dur = a.durationMs(reduce).toFloat()
            var alpha = envelope(ms, dur)
            if (a.fadeFromNs >= 0) alpha *= (1f - (now - a.fadeFromNs) / 1_000_000f / ActionFxState.SKIP_FADE_MS).coerceIn(0f, 1f)
            if (alpha <= 0.01f) continue
            val at = a.fx.at
            val ok = !screenOnly && projection != null && projection.project(at.x, at.y, at.z, pt)
            val ax: Float; val ay: Float; val ppcm: Float
            if (ok) { ax = pt[0]; ay = pt[1]; ppcm = pt[2] } else { val f = fallback(at.x, at.y); ax = f.x; ay = f.y; ppcm = size.width / 160f }
            val unit = (ppcm * 16f).coerceIn(64f * density, 128f * density)
            val margin = unit * 0.6f
            val frame = FxFrame(
                ax = ax.clampSafe(margin, size.width - margin),
                ay = ay.clampSafe(margin * 1.4f, size.height - margin * 0.6f),
                ppcm = ppcm.coerceIn(1f, 40f * density),
                unit = unit,
                ms = if (reduce) poseMs(a.fx.kind) else ms,
                dur = a.fx.kind.ms.toFloat(),
                alpha = alpha,
                reduce = reduce,
                pal = pal,
                project = project,
            )
            drawFx(a.fx, frame)
            a.fx.caption?.let { text ->
                val layout = a.captionLayout ?: measurer.measure(text, captionStyle).also { a.captionLayout = it }
                drawCaption(layout, frame.ax, frame.ay + unit * 0.62f, pal, alpha)
            }
        }
    }
}

/** Fade in over 150 ms, out over the last 300 ms. */
private fun envelope(ms: Float, dur: Float): Float {
    val inA = (ms / 150f).coerceIn(0f, 1f)
    val outA = ((dur - ms) / 300f).coerceIn(0f, 1f)
    return minOf(inA, outA)
}

/** Under Reduce motion every effect shows one still frame: the moment that explains it best. */
private fun poseMs(k: FxKind): Float = when (k) {
    FxKind.FEED -> 1150f
    FxKind.DOSE -> 1000f
    FxKind.WATER_CHANGE -> 1500f
    FxKind.BAG -> 1500f
    FxKind.POWER_OUT, FxKind.LIGHTS_OFF -> 1500f
    else -> k.ms * 0.45f
}

/** Without a camera: map tank x across the screen and height down it. */
private fun DrawScope.fallback(x: Float, y: Float): Offset =
    Offset(size.width * (0.12f + 0.76f * (x / 122f).coerceIn(0f, 1.1f)), size.height * (0.22f + 0.56f * (1f - y / 61f)))

private fun DrawScope.drawCaption(layout: TextLayoutResult, cx: Float, top: Float, pal: FxPalette, alpha: Float) {
    val w = layout.size.width.toFloat()
    val h = layout.size.height.toFloat()
    val padX = 12f * density
    val padY = 5f * density
    val left = (cx - w / 2 - padX).clampSafe(4f * density, size.width - w - 2 * padX - 4f * density)
    val y = top.coerceAtMost(size.height - h - 2 * padY - 4f * density)
    drawRoundRect(pal.captionBg, Offset(left, y), Size(w + 2 * padX, h + 2 * padY), CornerRadius((h + 2 * padY) / 2), alpha = alpha)
    drawText(layout, topLeft = Offset(left + padX, y + padY), alpha = alpha)
}
