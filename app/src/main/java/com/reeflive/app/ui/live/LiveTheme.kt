package com.reeflive.app.ui.live

import androidx.compose.animation.animateColorAsState
import kotlinx.coroutines.launch
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reeflive.app.ui.components.Glyph
import com.reeflive.app.ui.components.ReefGlyph
import com.reeflive.app.ui.components.bubbleBurst
import com.reeflive.app.ui.components.pressSquash
import com.reeflive.app.ui.components.rememberHaptics
import com.reeflive.app.ui.theme.ReefColors
import com.reeflive.app.ui.theme.ReefFonts
import com.reeflive.app.ui.theme.ReefMotion
import com.reeflive.app.ui.theme.ReefTheme

/*
 * The live tank's look in ONE place (UI_STYLE §10.1): "Sea-glass Arcade", the owner's pick (concepts 1 + 5).
 * Glossy sea-glass bubble buttons (radial highlight, white rim, coloured drop shadow) drawn with the arcade's thick
 * navy outline and candy colours, sitting on a chunky 6 dp bottom shadow that squashes when pressed, with bubbles
 * rising from every press. Panels are translucent lagoon-teal glass with a thick navy outline. Every live control is
 * built from the composables in this file ([LiveRailButton], [LiveTile], [LivePill], [Modifier.livePanel]) and the
 * colours here; restyling the live screen means editing [LiveTheme.build] and this file only.
 */

/** Colours the in-tank action animations (ActionFx / ActionArt) are painted with. */
@Immutable
data class FxPalette(
    /** Outline / dark second tone of every tool (the arcade navy). */
    val ink: Color,
    /** Light first tone (bottle bodies, bucket, bag). */
    val body: Color,
    /** See-through plastic / glass. */
    val glass: Color,
    val water: Color,
    val waterDeep: Color,
    val spark: Color,
    val heat: Color,
    val cold: Color,
    val danger: Color,
    val good: Color,
    /** Lamp light. */
    val light: Color,
    val bubble: Color,
    /** Caption text and its backing. */
    val caption: Color,
    val captionBg: Color,
    /** The candy set, for confetti and tool trims. */
    val orange: Color,
    val cyan: Color,
    val yellow: Color,
    val pink: Color,
    val green: Color,
    val purple: Color,
)

/** Every live-tank chrome token. Build one with [LiveTheme.style]. */
@Immutable
data class LiveStyle(
    /** Arcade navy: every button, tile and panel outline, and the ink of glyphs on candy buttons. */
    val outline: Color,
    val outlineWidth: Dp,
    /** Translucent lagoon-teal glass of panels and the portrait dock. */
    val panel: Color,
    val panelHi: Color,
    /** Text on [panel]. */
    val label: Color,
    val labelDim: Color,
    /** Text on a candy button (always the navy). */
    val onCandy: Color,
    val danger: Color,
    val good: Color,
    val caution: Color,
    /** The chunky bottom shadow under bubbles and tiles. */
    val depth: Dp,
    val tileShape: Shape,
    val panelShape: Shape,
    val pillShape: Shape,
    /** Rail width in landscape, and the bubble inside it (≥ 56 dp touch). */
    val railWidth: Dp,
    val railButton: Dp,
    /** Minimum height of a menu tile. */
    val tileMin: Dp,
    /** Rounded display face for labels and titles. */
    val display: FontFamily,
    val fx: FxPalette,
    private val tones: Map<LiveCategory, Color>,
) {
    /** The candy colour that identifies a menu category (its bubble, its card header, its tiles). */
    fun tone(c: LiveCategory): Color = tones[c] ?: Candy.white

    /** Bold rounded label text for buttons. */
    val buttonText: TextStyle get() = TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp)
    val titleText: TextStyle get() = TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 22.sp)
}

/** The Coral Arcade candy palette (concept 5). */
object Candy {
    val navy = Color(0xFF1B1340)
    val orange = Color(0xFFFFB13D)
    val cyan = Color(0xFF5FD7FF)
    val yellow = Color(0xFFFFE14D)
    val pink = Color(0xFFFF7AA8)
    val green = Color(0xFF8EE66B)
    val purple = Color(0xFFB18CFF)
    val white = Color(0xFFF2F6FF)
    val red = Color(0xFFFF5C6C)
}

object LiveTheme {
    /** The live-tank style for the current app theme (Abyss / Lagoon). */
    val style: LiveStyle
        @Composable get() {
            val c = ReefTheme.colors
            return remember(c) { build(c) }
        }

    /** Maps the app colours onto the live chrome. Change this (and this file) to restyle the live screen. */
    fun build(c: ReefColors): LiveStyle {
        val dark = c.isDark
        return LiveStyle(
            outline = Candy.navy,
            outlineWidth = 3.dp,
            panel = if (dark) Color(0xFF0C5866).copy(alpha = 0.74f) else Color(0xFFC9F4F7).copy(alpha = 0.84f),
            panelHi = Color.White.copy(alpha = if (dark) 0.10f else 0.45f),
            label = if (dark) Color.White else Candy.navy,
            labelDim = if (dark) Color(0xFFCDEFF3) else Color(0xFF3B3466),
            onCandy = Candy.navy,
            danger = Candy.red,
            good = Candy.green,
            caution = Candy.orange,
            depth = 6.dp,
            tileShape = RoundedCornerShape(22.dp),
            panelShape = RoundedCornerShape(28.dp),
            pillShape = RoundedCornerShape(999.dp),
            railWidth = 84.dp,
            railButton = 60.dp,
            tileMin = 76.dp,
            display = ReefFonts.outfit,
            fx = FxPalette(
                ink = Candy.navy,
                body = Color(0xFFFDF8EE),
                glass = Color(0xFFDDF4FA).copy(alpha = 0.55f),
                water = Color(0xFF5CE1E6),
                waterDeep = Color(0xFF1FA6C9),
                spark = Candy.yellow,
                heat = Color(0xFFFF6B3D),
                cold = Candy.cyan,
                danger = Candy.red,
                good = Candy.green,
                light = Color(0xFFFFF4C2),
                bubble = Color.White.copy(alpha = 0.8f),
                caption = Color.White,
                captionBg = Candy.navy.copy(alpha = 0.82f),
                orange = Candy.orange, cyan = Candy.cyan, yellow = Candy.yellow,
                pink = Candy.pink, green = Candy.green, purple = Candy.purple,
            ),
            tones = mapOf(
                LiveCategory.SUPPLIES to Candy.orange,
                LiveCategory.LIGHT to Candy.yellow,
                LiveCategory.LIFE to Candy.pink,
                LiveCategory.TROUBLE to Candy.purple,
                LiveCategory.MORE to Candy.white,
            ),
        )
    }
}

private fun Color.mix(other: Color, t: Float) = lerp(this, other, t)

/**
 * Paints a sea-glass bubble filling this scope above its [depthPx] shadow lane: the coloured drop shadow (the tone,
 * darkened, under a navy rim) [pressedDepth] px below the body, then the body itself shifted down by what the press
 * took off the shadow, a radial highlight from the top-left, the thick navy outline, an inner white rim and a gloss
 * oval. [corner] < 0 draws a circle, otherwise a rounded rectangle with that corner radius (px).
 */
fun DrawScope.drawSeaGlass(
    tone: Color,
    outline: Color,
    outlinePx: Float,
    depthPx: Float,
    pressedDepth: Float,
    corner: Float,
    dim: Boolean = false,
) {
    val w = size.width
    val h = size.height - depthPx
    if (w <= 0f || h <= 0f) return
    val base = if (dim) tone.mix(Color(0xFF7D86A6), 0.55f) else tone
    val shadow = base.mix(outline, 0.45f)
    val drop = depthPx - pressedDepth           // how far the body moved down
    val half = outlinePx / 2
    if (corner < 0f) {
        val r = minOf(w, h) / 2 - half
        val c = Offset(w / 2, h / 2 + drop)
        drawCircle(shadow, r, Offset(w / 2, h / 2 + depthPx))
        drawCircle(outline, r, Offset(w / 2, h / 2 + depthPx), style = Stroke(outlinePx))
        drawCircle(
            Brush.radialGradient(
                listOf(base.mix(Color.White, 0.55f), base, base.mix(outline, 0.18f)),
                center = Offset(c.x - r * 0.35f, c.y - r * 0.45f), radius = r * 1.6f,
            ),
            r, c,
        )
        drawCircle(Color.White.copy(alpha = 0.55f), r - outlinePx * 0.9f, c, style = Stroke(outlinePx * 0.45f))
        drawCircle(outline, r, c, style = Stroke(outlinePx))
        drawOval(Color.White.copy(alpha = 0.75f), Offset(c.x - r * 0.62f, c.y - r * 0.72f), Size(r * 0.62f, r * 0.34f))
        drawCircle(Color.White.copy(alpha = 0.9f), r * 0.07f, Offset(c.x + r * 0.08f, c.y - r * 0.62f))
    } else {
        val tl = Offset(half, half + drop)
        val sz = Size(w - outlinePx, h - outlinePx)
        val cr = CornerRadius(corner)
        drawRoundRect(shadow, Offset(half, half + depthPx), sz, cr)
        drawRoundRect(outline, Offset(half, half + depthPx), sz, cr, style = Stroke(outlinePx))
        drawRoundRect(
            Brush.radialGradient(
                listOf(base.mix(Color.White, 0.5f), base, base.mix(outline, 0.15f)),
                center = Offset(tl.x + sz.width * 0.2f, tl.y + sz.height * 0.1f), radius = maxOf(sz.width, sz.height) * 1.1f,
            ),
            tl, sz, cr,
        )
        val inset = outlinePx * 0.9f
        drawRoundRect(Color.White.copy(alpha = 0.5f), Offset(tl.x + inset, tl.y + inset), Size(sz.width - 2 * inset, sz.height - 2 * inset), CornerRadius(maxOf(corner - inset, 0f)), style = Stroke(outlinePx * 0.45f))
        drawRoundRect(outline, tl, sz, cr, style = Stroke(outlinePx))
        val gw = minOf(sz.width * 0.42f, 64f * density)
        drawRoundRect(Color.White.copy(alpha = 0.6f), Offset(tl.x + corner * 0.55f, tl.y + outlinePx * 1.6f), Size(gw, 5f * density), CornerRadius(2.5f * density))
    }
}

/** Glyph accent on a candy body: white, except on the white More tone where white would vanish. */
internal fun accentOn(tone: Color): Color = if (tone.red > 0.9f && tone.green > 0.9f && tone.blue > 0.9f) Candy.purple else Color.White

/**
 * The big round menu bubble: a [LiveStyle.railButton] sea-glass bubble in its category [tone] with a navy glyph, a
 * label on a navy pill under it, the chunky shadow that squashes as you press, a bounce on release, rising bubbles and
 * a haptic tick. [selected] lifts it and rings it white (its card is open); [badge] adds a red dot.
 */
@Composable
fun LiveRailButton(
    glyph: ReefGlyph?,
    label: String,
    tone: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    badge: Boolean = false,
    size: Dp = LiveTheme.style.railButton,
    showLabel: Boolean = true,
    /** Smaller label text for a crowded portrait dock. */
    compactLabel: Boolean = false,
    content: (@Composable () -> Unit)? = null,
) {
    val s = LiveTheme.style
    val reduce = ReefTheme.reduceMotion
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val depth by animateFloatAsState(if (pressed) 0.25f else 1f, ReefMotion.spring(reduce), label = "railDepth")
    val lift by animateFloatAsState(if (selected) 1.08f else 1f, ReefMotion.bouncy(reduce), label = "railLift")
    Column(
        modifier
            .widthIn(min = size)
            .semantics { this.selected = selected }
            .graphicsLayer { scaleX = lift; scaleY = lift }
            .pressSquash(interaction)
            .bubbleBurst(interaction, tone, s.outline)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = label) {
                haptics.tick()
                onClick()
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(size, size + s.depth)
                .drawBehind {
                    val d = s.depth.toPx()
                    if (selected) drawCircle(Color.White.copy(alpha = 0.85f), this.size.width / 2 + 3.dp.toPx(), Offset(this.size.width / 2, (this.size.height - d) / 2 + d * (1f - depth)), style = Stroke(3.dp.toPx()))
                    drawSeaGlass(tone, s.outline, s.outlineWidth.toPx(), d, d * depth, -1f)
                },
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                Modifier
                    .size(size)
                    .graphicsLayer { translationY = s.depth.toPx() * (1f - depth) },
                contentAlignment = Alignment.Center,
            ) {
                if (content != null) content() else if (glyph != null) Glyph(glyph, size = size * 0.5f, tint = s.onCandy, accent = accentOn(tone))
            }
            if (badge) {
                Box(Modifier.align(Alignment.TopEnd).size(14.dp).clip(CircleShape).background(s.danger).border(2.dp, s.outline, CircleShape))
            }
        }
        if (showLabel) {
            Text(
                label,
                style = if (compactLabel) s.buttonText.copy(fontSize = 10.5.sp, lineHeight = 13.sp) else s.buttonText,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 3.dp)
                    .widthIn(max = if (compactLabel) size + 8.dp else size + 24.dp)
                    .clip(s.pillShape)
                    .background(s.outline.copy(alpha = 0.86f))
                    .padding(horizontal = if (compactLabel) 4.dp else 7.dp, vertical = 1.dp),
            )
        }
    }
}

/**
 * One action inside a menu card: a chunky candy tile at least [LiveStyle.tileMin] tall with a navy glyph and a
 * one- or two-word label in the rounded display face. [on] (non-null) makes it a switch tile: full colour with a white
 * "ON" tag when on, greyed when off. [danger] paints it red (things that break the tank). Same squash, bounce, bubbles
 * and haptics as [LiveRailButton].
 */
@Composable
fun LiveTile(
    glyph: ReefGlyph?,
    label: String,
    tone: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sub: String? = null,
    on: Boolean? = null,
    danger: Boolean = false,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
) {
    val s = LiveTheme.style
    val reduce = ReefTheme.reduceMotion
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val depth by animateFloatAsState(if (pressed) 0.2f else 1f, ReefMotion.spring(reduce), label = "tileDepth")
    val t = if (danger) s.danger else tone
    val lit = on ?: true
    val shown by animateColorAsState(if (lit && enabled) t else t.mix(Color(0xFF7D86A6), 0.55f), ReefMotion.tween(reduce, ReefMotion.FAST), label = "tileTone")
    Box(
        modifier
            .heightIn(min = s.tileMin + s.depth)
            .pressSquash(interaction, enabled, strength = 0.6f)
            .bubbleBurst(interaction, t, s.outline, enabled)
            .semantics { if (on != null) stateDescription = if (on) "On" else "Off" }
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = if (on != null) Role.Switch else Role.Button, onClickLabel = label) {
                haptics.tick()
                onClick()
            }
            .drawBehind { drawSeaGlass(shown, s.outline, s.outlineWidth.toPx(), s.depth.toPx(), s.depth.toPx() * depth, 20.dp.toPx()) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = s.tileMin)
                .graphicsLayer { translationY = s.depth.toPx() * (1f - depth) }
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                if (icon != null) icon()
                else if (glyph != null) Glyph(glyph, size = 32.dp, tint = s.onCandy, accent = accentOn(t))
            }
            Spacer(Modifier.height(3.dp))
            Text(
                label,
                style = s.buttonText,
                color = s.onCandy,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (sub != null || on != null) {
                Text(
                    sub ?: if (on == true) "ON" else "OFF",
                    style = s.buttonText.copy(fontSize = 11.sp, lineHeight = 13.sp),
                    color = s.onCandy.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * A small candy pill (HUD chips, the mode switch halves, speed choices): sea-glass with the navy outline and a short
 * shadow. [selected] fills it with [tone]; unselected pills are frosted white.
 */
@Composable
fun LivePill(
    text: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    tone: Color = Candy.cyan,
    selected: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    role: Role = Role.Button,
) {
    val s = LiveTheme.style
    val reduce = ReefTheme.reduceMotion
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val depth by animateFloatAsState(if (pressed) 0.2f else 1f, ReefMotion.spring(reduce), label = "pillDepth")
    val fill by animateColorAsState(if (selected) tone else Color(0xFFE9F3FF), ReefMotion.tween(reduce, ReefMotion.FAST), label = "pillFill")
    val d = 4.dp
    var m = modifier
        .heightIn(min = 44.dp + d)
        .semantics { this.selected = selected }
    if (onClick != null) {
        m = m
            .pressSquash(interaction, strength = 0.5f)
            .bubbleBurst(interaction, tone, s.outline)
            .clickable(interactionSource = interaction, indication = null, role = role, onClickLabel = text) { haptics.tick(); onClick() }
    }
    Box(m.drawBehind { drawSeaGlass(fill, s.outline, 2.5.dp.toPx(), d.toPx(), d.toPx() * depth, (this.size.height - d.toPx()) / 2) }) {
        Row(
            Modifier
                .heightIn(min = 44.dp)
                .graphicsLayer { translationY = d.toPx() * (1f - depth) }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            leading?.invoke()
            Text(text, style = s.buttonText.copy(fontSize = 14.sp), color = s.onCandy, maxLines = 1, overflow = TextOverflow.Ellipsis)
            trailing?.invoke()
        }
    }
}

/** Translucent lagoon-teal glass with the thick navy outline and a soft inner highlight: every live panel and card. */
fun Modifier.livePanel(s: LiveStyle, shape: Shape = s.panelShape): Modifier = this
    .clip(shape)
    .background(s.panel)
    .background(Brush.verticalGradient(listOf(s.panelHi, Color.Transparent), endY = 220f))
    .border(s.outlineWidth, s.outline, shape)

/** A live panel with padding and a column inside (fish card, tips, follow bar, dialogs). */
@Composable
fun LivePanel(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    tint: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val s = LiveTheme.style
    var m = modifier.livePanel(s)
    if (tint != null) m = m.background(tint.copy(alpha = 0.18f))
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(m.padding(contentPadding), content = content)
}

/** Lays [count] tiles out in rows of [columns] equal-width cells (a simple non-lazy grid for menu cards). */
@Composable
fun TileGrid(count: Int, columns: Int, modifier: Modifier = Modifier, tile: @Composable (index: Int, cell: Modifier) -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        var i = 0
        while (i < count) {
            val start = i
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (k in 0 until columns) {
                    val idx = start + k
                    if (idx < count) tile(idx, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
                }
            }
            i += columns
        }
    }
}

/** Section title inside a live card. */
@Composable
fun LiveSectionTitle(text: String, modifier: Modifier = Modifier) {
    val s = LiveTheme.style
    Text(text, style = s.buttonText.copy(fontSize = 15.sp), color = s.label, modifier = modifier.padding(top = 4.dp, bottom = 2.dp))
}

/** Plain helper text inside a live card. */
@Composable
fun LiveHelp(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = LiveTheme.style.labelDim, modifier = modifier)
}

/**
 * The live screen's "are you sure?" dialog: a glass card with a worried Hermie, the question, and two big pills
 * (Cancel in white, the action in red when [destructive]). It pops in with a bounce.
 */
@Composable
fun LiveDialog(
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    dismiss: String = "Cancel",
) {
    val s = LiveTheme.style
    val reduce = ReefTheme.reduceMotion
    Dialog(onDismissRequest = onDismiss) {
        val pop = remember { Animatable(if (reduce) 1f else 0.7f) }
        val hop = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            if (!reduce) {
                launch { pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 420f)) }
                hop.animateTo(1f, tween(600))
            }
        }
        Column(
            Modifier
                .widthIn(max = 420.dp)
                .graphicsLayer { scaleX = pop.value; scaleY = pop.value; alpha = ((pop.value - 0.7f) / 0.3f).coerceIn(0f, 1f) }
                .livePanel(s)
                .background(s.panel.copy(alpha = 0.9f))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HermieArt(if (destructive) HermieMood.WORRIED else HermieMood.SURPRISED, hop, reduce, Modifier.size(52.dp))
                Spacer(Modifier.width(10.dp))
                Text(title, style = s.titleText, color = s.label, modifier = Modifier.weight(1f))
            }
            Text(body, style = MaterialTheme.typography.bodyMedium, color = s.labelDim)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                LivePill(dismiss, onDismiss, tone = Candy.white)
                LivePill(confirm, onConfirm, tone = if (destructive) Candy.red else Candy.green)
            }
        }
    }
}
