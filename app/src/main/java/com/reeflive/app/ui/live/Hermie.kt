package com.reeflive.app.ui.live

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reeflive.app.ui.components.Glyph
import com.reeflive.app.ui.components.ReefGlyphs
import com.reeflive.app.ui.components.pressSquash
import com.reeflive.app.ui.components.rememberHaptics
import com.reeflive.app.ui.theme.ReefMotion
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.core.sim.OrganismStats
import com.reeflive.core.status.Status
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/*
 * Hermie, the hermit-crab guide (UI_STYLE §10.3), and the "Tank happy" meter: two small pills at the top of the live
 * screen. Hermie pipes up with a tip or a reaction to every action (a hop for good things, a worried wobble for
 * trouble), then tucks his speech bubble away; both pills can be dismissed and brought back from the More card.
 */

/** What Hermie is saying and how he feels; [react] makes him hop and talk. */
@Stable
class HermieState {
    var line by mutableStateOf<String?>(LiveTips.HELLO)
        private set
    var mood by mutableStateOf(HermieMood.HAPPY)
        private set
    /** Bumps on every reaction so the pill can hop and re-open its bubble. */
    var beat by mutableIntStateOf(0)
        private set

    fun react(r: Reaction) = say(r.say, r.mood)

    fun say(text: String, mood: HermieMood = HermieMood.HAPPY) {
        line = text
        this.mood = mood
        beat++
    }

    private var tip = 0
    /** Tapping Hermie: the next tip. */
    fun nextTip(sandbox: Boolean) {
        val tips = if (sandbox) SandboxTips else WatchTips
        say(tips[tip % tips.size], HermieMood.HAPPY)
        tip++
    }

    companion object {
        val WatchTips = LiveTips.hermieWatch
        val SandboxTips = LiveTips.hermieSandbox
    }
}

/**
 * Hermie's pill: the animated crab in a candy bubble and, after each reaction, his speech bubble for ~7 s.
 * Tapping him gives the next tip; the small × hides him (bring him back from More).
 */
@Composable
fun HermiePill(state: HermieState, sandbox: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier, maxWidth: Dp = 300.dp) {
    val s = LiveTheme.style
    val reduce = ReefTheme.reduceMotion
    val haptics = rememberHaptics()
    var talking by remember { mutableStateOf(true) }
    val hop = remember { Animatable(0f) }
    LaunchedEffect(state.beat) {
        talking = true
        if (!reduce) launch {
            hop.snapTo(0f)
            hop.animateTo(1f, tween(if (state.mood == HermieMood.WORRIED) 600 else 420))
        }
        delay(TALK_MS)
        talking = false
    }
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .livePanel(s, s.pillShape)
            .semantics { liveRegion = LiveRegionMode.Polite; contentDescription = "Hermie the hermit crab" + (state.line?.let { ": $it" } ?: "") }
            .padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .pressSquash(interaction)
                .clickable(interactionSource = interaction, indication = null, onClickLabel = "Ask Hermie for a tip") { haptics.tick(); state.nextTip(sandbox) },
        ) {
            HermieArt(state.mood, hop, reduce, Modifier.size(48.dp))
        }
        AnimatedVisibility(
            visible = talking && state.line != null,
            enter = fadeIn(liveSpec(reduce)) + expandHorizontally(liveSpec(reduce)),
            exit = fadeOut(liveSpec(reduce, ReefMotion.FAST)) + shrinkHorizontally(liveSpec(reduce)),
        ) {
            AnimatedContent(
                targetState = state.line ?: "",
                transitionSpec = { fadeIn(liveSpec(reduce, ReefMotion.FAST, crossfade = true)) togetherWith fadeOut(liveSpec(reduce, ReefMotion.FAST, crossfade = true)) },
                label = "hermieLine",
            ) { text ->
                Text(
                    text,
                    style = s.buttonText.copy(fontSize = 13.sp, lineHeight = 16.sp),
                    color = s.label,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = maxWidth).padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }
        CloseDot(onDismiss, "Hide Hermie")
    }
}

/** How long Hermie's speech bubble stays open: long enough for a slow reader. */
private const val TALK_MS = 7000L

/** A 32 dp × in a navy disc (inside a 44 dp touch target). */
@Composable
internal fun CloseDot(onClick: () -> Unit, label: String) {
    val s = LiveTheme.style
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(44.dp)
            .pressSquash(interaction)
            .clickable(interactionSource = interaction, indication = null, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(26.dp).livePanel(s, s.pillShape), contentAlignment = Alignment.Center) {
            Glyph(ReefGlyphs.close, size = 14.dp, tint = s.label, contentDescription = label)
        }
    }
}

/**
 * Hermie himself: a pink-and-orange spiral shell, red legs, a waving claw and eyes on stalks, in the arcade outline.
 * Idles (blinks, eyes look around, claw waves) on a slow loop; [hop] 0..1 plays the reaction: a hop for happy and
 * excited, a shake for worried. Still pose under Reduce motion.
 */
@Composable
fun HermieArt(mood: HermieMood, hop: Animatable<Float, *>, reduce: Boolean, modifier: Modifier = Modifier) {
    val loop: State<Float> = if (reduce) remember { mutableFloatStateOf(0.3f) } else {
        rememberInfiniteTransition(label = "hermie").animateFloat(0f, 1f, infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart), label = "hermieLoop")
    }
    Canvas(modifier) {
        val t = loop.value
        val h = hop.value
        val s = size.minDimension / 48f
        val jump = when (mood) {
            HermieMood.WORRIED, HermieMood.SURPRISED -> 0f
            else -> sin(h * PI.toFloat()) * 7f
        }
        val shake = if (mood == HermieMood.WORRIED || mood == HermieMood.SURPRISED) sin(h * 6f * PI.toFloat()) * 2.2f * (1f - h) else 0f
        withTransform({ scale(s, s, Offset.Zero); translate(shake, -jump) }) {
            drawHermie(mood, t, h)
        }
    }
}

private val Navy = Candy.navy
private val ShellPink = Color(0xFFFF7AA8)
private val ShellOrange = Color(0xFFFFB13D)
private val CrabRed = Color(0xFFFF6A4D)

/** Hermie on a 48-unit box ([t] idle loop 0..1, [h] reaction 0..1). Also used by the preview script's geometry. */
fun DrawScope.drawHermie(mood: HermieMood, t: Float, h: Float) {
    val stroke = Stroke(2.2f, cap = StrokeCap.Round)
    // legs
    for (k in 0 until 3) {
        val x = 14f + k * 5f
        val kick = sin((t * 6f + k) * 2f * PI.toFloat()) * 1.2f
        drawLine(Navy, Offset(x, 34f), Offset(x - 3f + kick, 42f), 3.4f, StrokeCap.Round)
        drawLine(CrabRed, Offset(x, 34f), Offset(x - 3f + kick, 42f), 1.8f, StrokeCap.Round)
    }
    // shell: a fat spiral, pink with an orange band
    drawCircle(ShellPink, 13.5f, Offset(30f, 25f))
    drawArc(ShellOrange, 200f, 200f, false, Offset(20f, 15f), Size(20f, 20f), style = Stroke(4f))
    drawArc(Navy, 160f, 270f, false, Offset(24f, 19f), Size(12f, 12f), style = stroke)
    drawArc(Navy, 120f, 240f, false, Offset(27f, 22f), Size(6f, 6f), style = stroke)
    drawCircle(Navy, 13.5f, Offset(30f, 25f), style = stroke)
    drawOval(Color.White.copy(alpha = 0.75f), Offset(22f, 14f), Size(7f, 3.5f))
    // body peeking out of the shell
    drawRoundRect(CrabRed, Offset(9f, 27f), Size(15f, 9f), CornerRadius(4.5f))
    drawRoundRect(Navy, Offset(9f, 27f), Size(15f, 9f), CornerRadius(4.5f), style = stroke)
    // claw: waves on the idle loop and big on a happy hop
    val wave = sin(t * 2f * PI.toFloat() * 2f) * 10f + (if (mood == HermieMood.EXCITED || mood == HermieMood.HAPPY) sin(h * PI.toFloat()) * 25f else 0f)
    rotate(-wave, Offset(10f, 31f)) {
        drawLine(Navy, Offset(10f, 31f), Offset(5f, 27f), 3.6f, StrokeCap.Round)
        drawLine(CrabRed, Offset(10f, 31f), Offset(5f, 27f), 2f, StrokeCap.Round)
        val claw = Path().apply { moveTo(5f, 27f); cubicTo(-1f, 26f, -1f, 18f, 4f, 18f); lineTo(5.5f, 22f); lineTo(7.5f, 19f); cubicTo(10f, 21f, 9f, 26f, 5f, 27f); close() }
        drawPath(claw, CrabRed)
        drawPath(claw, Navy, style = Stroke(1.8f))
    }
    // eyes on stalks; blink at the end of the loop, look around in the middle
    val blink = if (t in 0.93f..0.97f || mood == HermieMood.SLEEPY) 0.25f else 1f
    val look = sin(t * 2f * PI.toFloat()) * 0.8f
    val big = if (mood == HermieMood.SURPRISED) 1.25f else 1f
    for ((sx, top) in listOf(13f to 12f, 19f to 11f)) {
        drawLine(Navy, Offset(sx, 27f), Offset(sx - 1f, top + 3f), 2.4f, StrokeCap.Round)
        val c = Offset(sx - 1f, top)
        drawOval(Color.White, Offset(c.x - 3.2f * big, c.y - 3.2f * big * blink), Size(6.4f * big, 6.4f * big * blink))
        drawOval(Navy, Offset(c.x - 3.2f * big, c.y - 3.2f * big * blink), Size(6.4f * big, 6.4f * big * blink), style = Stroke(1.6f))
        if (blink > 0.5f) drawCircle(Navy, 1.5f, Offset(c.x + look, c.y + 0.3f))
        if (mood == HermieMood.WORRIED) drawLine(Navy, Offset(c.x - 3f, c.y - 5f + (if (sx < 16f) -1f else 1f)), Offset(c.x + 3f, c.y - 5f + (if (sx < 16f) 1f else -1f)), 1.4f, StrokeCap.Round)
    }
    // mouth
    when (mood) {
        HermieMood.SURPRISED -> drawCircle(Navy, 1.6f, Offset(16f, 32.5f))
        HermieMood.WORRIED -> drawArc(Navy, 200f, 140f, false, Offset(13.5f, 32f), Size(5f, 3f), style = Stroke(1.4f))
        HermieMood.SLEEPY -> {
            drawLine(Navy, Offset(14f, 32.5f), Offset(18f, 32.5f), 1.4f, StrokeCap.Round)
            val z = (t * 3f) % 1f
            drawLine(Navy, Offset(22f, 8f - z * 4f), Offset(25f, 8f - z * 4f), 1.2f); drawLine(Navy, Offset(25f, 8f - z * 4f), Offset(22f, 11f - z * 4f), 1.2f); drawLine(Navy, Offset(22f, 11f - z * 4f), Offset(25f, 11f - z * 4f), 1.2f)
        }
        else -> drawArc(Navy, 20f, 140f, false, Offset(13.5f, 29.5f), Size(5f, 4f), style = Stroke(1.4f))
    }
    if (mood == HermieMood.WORRIED) {
        val d = Path().apply { moveTo(24f, 8f); cubicTo(25.5f, 10f, 26.5f, 11.5f, 26.5f, 12.5f); cubicTo(26.5f, 14f, 21.5f, 14f, 21.5f, 12.5f); cubicTo(21.5f, 11.5f, 22.5f, 10f, 24f, 8f); close() }
        drawPath(d, Candy.cyan)
    }
    if (mood == HermieMood.EXCITED && h in 0.05f..0.95f) {
        val a = 1f - h
        drawLine(Candy.yellow.copy(alpha = a), Offset(40f, 6f), Offset(40f, 12f), 1.8f, StrokeCap.Round)
        drawLine(Candy.yellow.copy(alpha = a), Offset(37f, 9f), Offset(43f, 9f), 1.8f, StrokeCap.Round)
    }
}

// ---------------------------------------------------------------- happy meter

/**
 * "Tank happy" 0..100 from the sim: the animals' health and calm (60 / 40) minus a few points for every water
 * reading in caution / danger. Display only; it never feeds back into the sim.
 */
fun tankHappiness(stats: List<OrganismStats>, readouts: List<WaterReadout>): Int {
    val alive = stats.filter { it.alive }
    val animals = if (alive.isEmpty()) 1.0 else {
        val health = alive.map { it.health.toDouble().coerceIn(0.0, 1.0) }.average()
        val calm = 1.0 - alive.map { it.stress.toDouble().coerceIn(0.0, 1.0) }.average()
        0.6 * health + 0.4 * calm
    }
    val dead = stats.count { !it.alive }
    val penalty = readouts.sumOf { r -> when (r.status) { Status.DANGER -> 10.0; Status.CAUTION -> 3.0; else -> 0.0 } } + dead * 6.0
    return (animals * 100.0 - penalty).coerceIn(0.0, 100.0).toInt()
}

/**
 * The meter pill: a face that smiles / frowns, "Tank happy 87 %" and a candy bar that fills and changes colour as
 * the value moves (spring), with a little bounce whenever it changes by a few points. Tap to fold it to the face;
 * × hides it (bring it back from More).
 */
@Composable
fun HappyMeterPill(value: Int, onDismiss: () -> Unit, modifier: Modifier = Modifier, startFolded: Boolean = false) {
    val s = LiveTheme.style
    val reduce = ReefTheme.reduceMotion
    var folded by remember { mutableStateOf(startFolded) }
    val shown by animateFloatAsState(value / 100f, if (reduce) tween(0) else spring(dampingRatio = 0.6f, stiffness = 120f), label = "happy")
    val bounce = remember { Animatable(0f) }
    var last by remember { mutableIntStateOf(value) }
    LaunchedEffect(value) {
        if (kotlin.math.abs(value - last) >= 3 && !reduce) { bounce.snapTo(1f); bounce.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 300f)) }
        last = value
    }
    val tone = when {
        value >= 70 -> Candy.green
        value >= 40 -> Candy.yellow
        else -> Candy.red
    }
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .graphicsLayer { val k = 1f + 0.12f * bounce.value; scaleX = k; scaleY = k }
            .livePanel(s, s.pillShape)
            .pressSquash(interaction, strength = 0.4f)
            .clickable(interactionSource = interaction, indication = null, onClickLabel = if (folded) "Show the happy meter" else "Fold the happy meter") { folded = !folded }
            .semantics { contentDescription = "Tank happy $value percent" }
            .padding(start = 6.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(32.dp)) { drawFace(shown, tone) }
        AnimatedVisibility(!folded, enter = fadeIn(liveSpec(reduce)) + expandHorizontally(liveSpec(reduce)), exit = fadeOut(liveSpec(reduce, ReefMotion.FAST)) + shrinkHorizontally(liveSpec(reduce))) {
            Column(Modifier.padding(start = 6.dp, end = 2.dp)) {
                Text("Tank happy $value%", style = s.buttonText.copy(fontSize = 13.sp), color = s.label, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                Canvas(Modifier.width(96.dp).height(10.dp)) {
                    val r = CornerRadius(size.height / 2)
                    drawRoundRect(Navy.copy(alpha = 0.55f), cornerRadius = r)
                    drawRoundRect(tone, size = Size(size.width * shown.coerceIn(0.04f, 1f), size.height), cornerRadius = r)
                    drawRoundRect(Color.White.copy(alpha = 0.5f), topLeft = Offset(3f, 2f), size = Size((size.width * shown - 6f).coerceAtLeast(0f), size.height * 0.3f), cornerRadius = r)
                    drawRoundRect(Navy, cornerRadius = r, style = Stroke(2f))
                }
            }
        }
        CloseDot(onDismiss, "Hide the happy meter")
    }
}

/** A candy face whose mouth curves with [v] (0 frown .. 1 big smile). */
private fun DrawScope.drawFace(v: Float, tone: Color) {
    val w = size.minDimension
    val c = Offset(size.width / 2, size.height / 2)
    val r = w / 2 - 2f
    drawCircle(tone, r, c)
    drawCircle(Navy, r, c, style = Stroke(w * 0.07f))
    drawCircle(Navy, w * 0.06f, Offset(c.x - r * 0.35f, c.y - r * 0.2f))
    drawCircle(Navy, w * 0.06f, Offset(c.x + r * 0.35f, c.y - r * 0.2f))
    val curve = (v - 0.5f) * 2f   // -1 frown .. 1 smile
    val mouth = Path().apply {
        moveTo(c.x - r * 0.45f, c.y + r * 0.3f)
        quadraticTo(c.x, c.y + r * 0.3f + curve * r * 0.45f, c.x + r * 0.45f, c.y + r * 0.3f)
    }
    drawPath(mouth, Navy, style = Stroke(w * 0.07f, cap = StrokeCap.Round))
    drawOval(Color.White.copy(alpha = 0.7f), Offset(c.x - r * 0.6f, c.y - r * 0.75f), Size(r * 0.5f, r * 0.25f))
}
