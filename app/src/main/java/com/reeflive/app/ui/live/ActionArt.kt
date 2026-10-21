package com.reeflive.app.ui.live

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.withTransform
import com.reeflive.app.ui.components.ReefGlyph
import com.reeflive.app.ui.components.ReefGlyphs
import com.reeflive.app.ui.components.drawGlyph
import com.reeflive.app.ui.theme.FoodPalette
import com.reeflive.core.sim.FoodKind
import com.reeflive.core.sim.TankDims
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * The pictures for the in-tank action animations (ActionFx.kt), in the live style (LiveTheme): every tool is drawn
 * on a 48-unit box like FoodArt's food pictures, with light candy / sea-glass bodies, the arcade-navy outline, a
 * white gloss and FoodPalette food. Each draw function takes the effect's time in ms and draws its pose at that
 * moment, so the same code plays the animation, shows the Reduce-motion still, and renders the preview sheet
 * (tools/graphics/action_preview.py mirrors the geometry).
 */

/** Everything one effect needs to draw one frame. */
internal class FxFrame(
    /** Anchor in canvas px (the projected tank point). */
    val ax: Float,
    val ay: Float,
    /** Screen px per tank cm at the anchor's depth. */
    val ppcm: Float,
    /** Size of a 48-unit tool box in px. */
    val unit: Float,
    /** Time into the effect, ms, and its full length. */
    val ms: Float,
    val dur: Float,
    val alpha: Float,
    val reduce: Boolean,
    val pal: FxPalette,
    /** Projects another tank point (cm) to canvas px; never null in practice (falls back to screen fractions). */
    val project: (Float, Float, Float) -> Offset?,
) {
    /** 0..1 progress of [ms] through the window [from, to] ms. */
    fun span(from: Float, to: Float): Float = ((ms - from) / (to - from)).coerceIn(0f, 1f)
}

// ---------------------------------------------------------------- small helpers

private fun ease(t: Float): Float { val x = t.coerceIn(0f, 1f); return x * x * (3f - 2f * x) }
private fun easeOutBack(t: Float): Float { val x = t.coerceIn(0f, 1f) - 1f; return 1f + x * x * (2.7f * x + 1.7f) }
private fun hash(i: Int): Float { var x = i * 374761393 + 668265263; x = (x xor (x ushr 13)) * 1274126177; return ((x xor (x ushr 16)) and 0xFFFF) / 65535f }
private fun o(x: Float, y: Float) = Offset(x, y)

/** Rotates the local point ([x], [y]) about (24, 24) by [deg] (clockwise on screen). */
private fun rot(x: Float, y: Float, deg: Float): Offset {
    val r = deg * PI.toFloat() / 180f
    val dx = x - 24f; val dy = y - 24f
    return Offset(24f + dx * cos(r) - dy * sin(r), 24f + dx * sin(r) + dy * cos(r))
}

/** Draws [block] in a 48-unit box centred on ([cx], [cy]) and [unit] px wide, rotated [deg] about its centre. */
private inline fun DrawScope.box(cx: Float, cy: Float, unit: Float, deg: Float = 0f, sx: Float = 1f, sy: Float = 1f, block: DrawScope.() -> Unit) {
    val s = unit / 48f
    withTransform({
        translate(cx - 24f * s, cy - 24f * s)
        scale(s, s, Offset.Zero)
        if (deg != 0f) rotate(deg, Offset(24f, 24f))
        if (sx != 1f || sy != 1f) scale(sx, sy, Offset(24f, 40f))
    }) { block() }
}

/** Canvas point of local box point ([lx], [ly]) for a box centred on ([cx], [cy]) rotated [deg]. */
private fun boxPoint(cx: Float, cy: Float, unit: Float, deg: Float, lx: Float, ly: Float): Offset {
    val p = rot(lx, ly, deg)
    val s = unit / 48f
    return Offset(cx + (p.x - 24f) * s, cy + (p.y - 24f) * s)
}

/** Draws [block] at [alpha] (one offscreen layer, only when fading). */
private inline fun DrawScope.faded(alpha: Float, block: DrawScope.() -> Unit) {
    if (alpha >= 0.995f) { block(); return }
    val paint = Paint().apply { this.alpha = alpha.coerceIn(0f, 1f) }
    drawContext.canvas.saveLayer(Rect(Offset.Zero, size), paint)
    block()
    drawContext.canvas.restore()
}

private fun stroke(w: Float) = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round)

/** A ReefGlyph drawn in a local box ([x], [y], [w]) of the current transform. */
private fun DrawScope.glyphAt(g: ReefGlyph, x: Float, y: Float, w: Float, tint: Color, accent: Color) {
    inset(x, y, size.width - x - w, size.height - y - w) { drawGlyph(g, tint, accent) }
}

/** A FoodArt picture drawn in a local box. */
private fun DrawScope.foodAt(k: FoodKind, x: Float, y: Float, w: Float) {
    inset(x, y, size.width - x - w, size.height - y - w) { drawFood(k) }
}

/** Food particle colours, matching the renderer's particle tints (FoodPalette). */
internal fun foodColors(k: FoodKind): List<Color> = when (k) {
    FoodKind.FLAKE -> listOf(FoodPalette.flakeOrange, FoodPalette.flakeRed, FoodPalette.flakeGreen, FoodPalette.flakeTan)
    FoodKind.PELLET -> listOf(FoodPalette.granule, FoodPalette.granuleShade)
    FoodKind.FLOATING_PELLET -> listOf(FoodPalette.pelletLight, FoodPalette.flakeTan)
    FoodKind.ALGAE_WAFER -> listOf(FoodPalette.waferGreen, FoodPalette.waferSpeck)
    FoodKind.NORI -> listOf(FoodPalette.noriGreen)
    FoodKind.MYSIS -> listOf(FoodPalette.mysisPink, FoodPalette.mysisDeep)
    FoodKind.BRINE -> listOf(FoodPalette.brineOrange, FoodPalette.brineDeep)
    FoodKind.KRILL -> listOf(FoodPalette.krillRed, FoodPalette.krillPale)
    FoodKind.PRAWN -> listOf(FoodPalette.prawnPink, FoodPalette.prawnStripe)
    FoodKind.CYCLOPS -> listOf(FoodPalette.cyclopsRed)
    FoodKind.CORAL_FOOD -> listOf(FoodPalette.powderPink, FoodPalette.powderOrange)
    FoodKind.PLANKTON -> listOf(FoodPalette.phyto)
    FoodKind.PODS -> listOf(FoodPalette.podTan)
}

/** How each food goes in. [PINCH]: by hand, as Tee feeds her granules beside the return (video T97). */
internal enum class FeedTool { PINCH, SHAKER, CUBE, CLIP, PIPETTE }

internal fun feedTool(k: FoodKind): FeedTool = when (k) {
    FoodKind.PELLET -> FeedTool.PINCH
    FoodKind.FLAKE, FoodKind.FLOATING_PELLET, FoodKind.ALGAE_WAFER -> FeedTool.SHAKER
    FoodKind.MYSIS, FoodKind.BRINE, FoodKind.KRILL, FoodKind.PRAWN, FoodKind.CYCLOPS -> FeedTool.CUBE
    FoodKind.NORI -> FeedTool.CLIP
    FoodKind.CORAL_FOOD, FoodKind.PLANKTON, FoodKind.PODS -> FeedTool.PIPETTE
}

// ---------------------------------------------------------------- tools (48-unit boxes)

/** Food shaker bottle, mouth up at (24, 4): see-through body showing the food, candy lid, label band. */
internal fun DrawScope.shakerBottle(food: FoodKind, lid: Color, ink: Color, glass: Color) {
    val cols = foodColors(food)
    drawRoundRect(glass, o(13f, 13f), Size(22f, 31f), CornerRadius(6f))
    // food inside, lower part
    drawRoundRect(cols[0].copy(alpha = 0.9f), o(14.5f, 27f), Size(19f, 15.5f), CornerRadius(4.5f))
    for (i in 0 until 7) drawCircle(cols[i % cols.size], 1.6f, o(16.5f + (i * 2.7f) % 15f, 30f + (i * 4.3f) % 10f))
    drawRoundRect(lid.copy(alpha = 0.92f), o(13f, 19f), Size(22f, 7f))
    drawCircle(Color.White, 1.4f, o(19f, 22.5f)); drawCircle(Color.White, 1.4f, o(24f, 22.5f)); drawCircle(Color.White, 1.4f, o(29f, 22.5f))
    drawRoundRect(ink, o(13f, 13f), Size(22f, 31f), CornerRadius(6f), style = stroke(2.4f))
    // neck and lid
    drawRoundRect(glass, o(16.5f, 8.5f), Size(15f, 5.5f), CornerRadius(1.5f))
    drawRoundRect(ink, o(16.5f, 8.5f), Size(15f, 5.5f), CornerRadius(1.5f), style = stroke(2f))
    drawRoundRect(lid, o(15f, 3f), Size(18f, 6.5f), CornerRadius(2.5f))
    drawRoundRect(ink, o(15f, 3f), Size(18f, 6.5f), CornerRadius(2.5f), style = stroke(2.2f))
    for (k in 0 until 4) drawCircle(ink, 0.8f, o(18.5f + k * 3.7f, 5f))
    drawLine(Color.White.copy(alpha = 0.75f), o(16.5f, 16f), o(16.5f, 39f), 2f, StrokeCap.Round)
}

/** Frozen cube: icy rounded block with the food frozen inside. */
internal fun DrawScope.frozenCube(food: FoodKind, ink: Color, ice: Color, thaw: Float) {
    val sz = 26f * (1f - 0.55f * thaw)
    val x = 24f - sz / 2; val y = 24f - sz / 2
    drawRoundRect(ice.copy(alpha = 0.55f * (1f - thaw * 0.6f)), o(x, y), Size(sz, sz), CornerRadius(5f))
    foodAt(food, x + 2f, y + 2f, sz - 4f)
    drawRoundRect(Color.White.copy(alpha = 0.35f * (1f - thaw)), o(x + 2f, y + 2f), Size(sz * 0.45f, sz * 0.18f), CornerRadius(2f))
    drawRoundRect(ink, o(x, y), Size(sz, sz), CornerRadius(5f), style = stroke(2.2f))
}

/** Pipette / dropper, tip down at (24, 46), squeeze bulb on top ([squeeze] 0..1 flattens it). */
internal fun DrawScope.pipette(tint: Color, bulb: Color, ink: Color, glass: Color, squeeze: Float) {
    val bw = 14f * (1f + 0.25f * squeeze); val bh = 12f * (1f - 0.4f * squeeze)
    drawRoundRect(glass, o(20.5f, 14f), Size(7f, 24f), CornerRadius(2.5f))
    drawRoundRect(tint, o(21.5f, 24f + 6f * squeeze), Size(5f, 13f - 6f * squeeze), CornerRadius(2f))
    drawPath(Path().apply { moveTo(20.5f, 37.5f); lineTo(27.5f, 37.5f); lineTo(24.8f, 46f); lineTo(23.2f, 46f); close() }, glass)
    drawPath(Path().apply { moveTo(20.5f, 37.5f); lineTo(27.5f, 37.5f); lineTo(24.8f, 46f); lineTo(23.2f, 46f); close() }, ink, style = stroke(2f))
    drawRoundRect(ink, o(20.5f, 14f), Size(7f, 24f), CornerRadius(2.5f), style = stroke(2f))
    drawOval(bulb, o(24f - bw / 2, 15f - bh), Size(bw, bh + 2f))
    drawOval(ink, o(24f - bw / 2, 15f - bh), Size(bw, bh + 2f), style = stroke(2.2f))
    drawOval(Color.White.copy(alpha = 0.7f), o(24f - bw / 2 + 2.5f, 15f - bh + 2f), Size(bw * 0.3f, bh * 0.3f))
    drawLine(Color.White.copy(alpha = 0.7f), o(22.2f, 17f), o(22.2f, 34f), 1.4f, StrokeCap.Round)
}

/** Dropper bottle, dropper tip at (24, 1): candy cap, body with coloured liquid and a label. */
internal fun DrawScope.doseBottle(tint: Color, cap: Color, ink: Color, body: Color) {
    drawRoundRect(body, o(13f, 17f), Size(22f, 28f), CornerRadius(6f))
    drawRoundRect(tint.copy(alpha = 0.85f), o(14.5f, 26f), Size(19f, 17.5f), CornerRadius(4.5f))
    drawRoundRect(Color.White.copy(alpha = 0.9f), o(16f, 29f), Size(16f, 8f), CornerRadius(2f))
    drawCircle(tint, 2.2f, o(20f, 33f))
    drawLine(ink.copy(alpha = 0.6f), o(24f, 32f), o(29.5f, 32f), 1.2f, StrokeCap.Round)
    drawLine(ink.copy(alpha = 0.6f), o(24f, 34.5f), o(28f, 34.5f), 1.2f, StrokeCap.Round)
    drawRoundRect(ink, o(13f, 17f), Size(22f, 28f), CornerRadius(6f), style = stroke(2.4f))
    drawRoundRect(cap, o(17f, 9f), Size(14f, 9f), CornerRadius(2.5f))
    drawRoundRect(ink, o(17f, 9f), Size(14f, 9f), CornerRadius(2.5f), style = stroke(2.2f))
    drawPath(Path().apply { moveTo(20f, 9f); lineTo(28f, 9f); lineTo(25f, 1.5f); lineTo(23f, 1.5f); close() }, body)
    drawPath(Path().apply { moveTo(20f, 9f); lineTo(28f, 9f); lineTo(25f, 1.5f); lineTo(23f, 1.5f); close() }, ink, style = stroke(2f))
    drawLine(Color.White.copy(alpha = 0.75f), o(16.3f, 20f), o(16.3f, 41f), 2f, StrokeCap.Round)
}

/** Bucket with a handle; [water] colours what is inside. */
internal fun DrawScope.bucket(shell: Color, water: Color, ink: Color) {
    val body = Path().apply { moveTo(9f, 16f); lineTo(39f, 16f); lineTo(35f, 44f); lineTo(13f, 44f); close() }
    drawArc(ink, 180f, 180f, false, o(12f, 4f), Size(24f, 24f), style = stroke(2.4f))
    drawPath(body, shell)
    drawRect(water.copy(alpha = 0.85f), o(10.5f, 17.5f), Size(27f, 5f))
    drawLine(ink.copy(alpha = 0.35f), o(11.5f, 30f), o(36.5f, 30f), 2f)
    drawPath(body, ink, style = stroke(2.4f))
    drawOval(ink, o(8f, 13.5f), Size(32f, 5f), style = stroke(2.2f))
    drawLine(Color.White.copy(alpha = 0.7f), o(14f, 21f), o(16f, 40f), 2f, StrokeCap.Round)
}

/** Jug of fresh water, spout at the top right (40, 12). */
internal fun DrawScope.jug(shell: Color, water: Color, ink: Color) {
    val body = Path().apply { moveTo(12f, 14f); lineTo(36f, 14f); lineTo(41f, 10f); lineTo(38f, 18f); lineTo(37f, 42f); quadraticTo(24f, 46f, 11f, 42f); close() }
    drawPath(body, shell)
    drawPath(Path().apply { moveTo(12.5f, 26f); lineTo(36.8f, 26f); lineTo(36.8f, 41.5f); quadraticTo(24f, 45f, 11.5f, 41.5f); close() }, water.copy(alpha = 0.8f))
    drawPath(body, ink, style = stroke(2.4f))
    drawArc(ink, 90f, 180f, false, o(4f, 18f), Size(14f, 18f), style = stroke(2.4f))
    drawLine(Color.White.copy(alpha = 0.75f), o(16f, 18f), o(16f, 38f), 2f, StrokeCap.Round)
}

/** Heater: glass tube with a coil that glows with [glow] (0 = cold grey). */
internal fun DrawScope.heater(glow: Float, heat: Color, ink: Color, glass: Color) {
    drawRoundRect(glass, o(18f, 7f), Size(12f, 38f), CornerRadius(6f))
    val coil = lerpColor(Color(0xFF8A90A8), heat, glow)
    if (glow > 0.05f) drawRoundRect(heat.copy(alpha = 0.35f * glow), o(14f, 10f), Size(20f, 34f), CornerRadius(10f))
    val p = Path().apply { moveTo(24f, 12f); for (k in 0 until 7) { lineTo(if (k % 2 == 0) 27.5f else 20.5f, 15f + k * 4f) }; lineTo(24f, 42f) }
    drawPath(p, coil, style = stroke(2.4f))
    drawRoundRect(ink, o(18f, 7f), Size(12f, 38f), CornerRadius(6f), style = stroke(2.4f))
    drawRoundRect(ink, o(17f, 2f), Size(14f, 7f), CornerRadius(2f))
    drawLine(ink, o(24f, 2f), o(24f, -14f), 2.2f, StrokeCap.Round)
    drawLine(Color.White.copy(alpha = 0.7f), o(20.5f, 12f), o(20.5f, 40f), 1.6f, StrokeCap.Round)
}

/** Thermometer with its column at [level] 0..1 in [col]. */
internal fun DrawScope.thermometer(level: Float, col: Color, ink: Color, body: Color) {
    drawRoundRect(body, o(19f, 3f), Size(10f, 34f), CornerRadius(5f))
    drawCircle(body, 8f, o(24f, 39f))
    val top = 34f - 28f * level.coerceIn(0f, 1f)
    drawRoundRect(col, o(21.8f, top), Size(4.4f, 37f - top), CornerRadius(2.2f))
    drawCircle(col, 5.5f, o(24f, 39f))
    drawRoundRect(ink, o(19f, 3f), Size(10f, 34f), CornerRadius(5f), style = stroke(2.2f))
    drawCircle(ink, 8f, o(24f, 39f), style = stroke(2.2f))
    for (k in 0 until 4) drawLine(ink, o(30.5f, 10f + k * 6f), o(33f, 10f + k * 6f), 1.4f, StrokeCap.Round)
    drawCircle(Color.White.copy(alpha = 0.8f), 1.8f, o(21.5f, 37f))
}

/** Wave pump: round body, grill, three blades turned by [angle] degrees. */
internal fun DrawScope.pump(angle: Float, on: Boolean, ink: Color, body: Color, accent: Color) {
    drawRoundRect(ink, o(4f, 18f), Size(8f, 12f), CornerRadius(2f))
    drawCircle(body, 15f, o(26f, 24f))
    drawCircle(ink, 15f, o(26f, 24f), style = stroke(2.4f))
    drawCircle(if (on) accent.copy(alpha = 0.35f) else ink.copy(alpha = 0.12f), 10.5f, o(26f, 24f))
    for (k in 0 until 3) {
        val a = (angle + k * 120f) * PI.toFloat() / 180f
        drawLine(ink, o(26f, 24f), o(26f + cos(a) * 9f, 24f + sin(a) * 9f), 3f, StrokeCap.Round)
    }
    drawCircle(ink, 2.6f, o(26f, 24f))
    drawCircle(Color.White.copy(alpha = 0.7f), 2.6f, o(19f, 15f))
}

/** LED lamp bar; [lit] 0..1 per LED in [leds]. */
internal fun DrawScope.lampBar(leds: FloatArray, light: Color, ink: Color, body: Color) {
    drawRoundRect(body, o(2f, 17f), Size(44f, 11f), CornerRadius(4f))
    drawRoundRect(ink, o(2f, 17f), Size(44f, 11f), CornerRadius(4f), style = stroke(2.4f))
    for (i in leds.indices) {
        val x = 7f + i * (34f / (leds.size - 1).coerceAtLeast(1))
        drawCircle(lerpColor(Color(0xFF5A6080), light, leds[i]), 2.4f, o(x, 25f))
    }
    drawLine(ink, o(10f, 17f), o(6f, 5f), 1.8f, StrokeCap.Round)
    drawLine(ink, o(38f, 17f), o(42f, 5f), 1.8f, StrokeCap.Round)
}

/** Fish bag: a knotted see-through bag of water with [animal] inside; [open] 0..1 unties the knot. */
internal fun DrawScope.fishBag(animal: ReefGlyph?, open: Float, water: Color, ink: Color, glass: Color, tint: Color) {
    val bag = Path().apply {
        moveTo(20f, 10f); cubicTo(8f, 16f, 6f, 40f, 24f, 44f); cubicTo(42f, 40f, 40f, 16f, 28f, 10f); close()
    }
    drawPath(bag, glass)
    drawPath(Path().apply { moveTo(10.5f, 26f); cubicTo(10f, 38f, 16f, 43.5f, 24f, 43.5f); cubicTo(32f, 43.5f, 38f, 38f, 37.5f, 26f); close() }, water.copy(alpha = 0.55f))
    if (animal != null && open < 0.4f) glyphAt(animal, 14f, 23f, 20f, ink, tint)
    drawPath(bag, ink, style = stroke(2.2f))
    // the knot: closed it ties the neck; opening spreads the two ends apart
    val spread = 4f * open
    drawLine(ink, o(20f, 10f), o(22f - spread, 4f - open * 2f), 2.2f, StrokeCap.Round)
    drawLine(ink, o(28f, 10f), o(26f + spread, 4f - open * 2f), 2.2f, StrokeCap.Round)
    if (open < 0.3f) drawCircle(tint, 2.6f, o(24f, 9f))
    drawLine(Color.White.copy(alpha = 0.7f), o(14f, 20f), o(13f, 32f), 2f, StrokeCap.Round)
}

/** Net on a handle. */
internal fun DrawScope.net(ink: Color, mesh: Color) {
    drawLine(ink, o(24f, 0f), o(24f, 24f), 3f, StrokeCap.Round)
    drawOval(mesh.copy(alpha = 0.35f), o(10f, 24f), Size(28f, 20f))
    for (k in 1 until 5) drawLine(ink.copy(alpha = 0.5f), o(10f + k * 5.6f, 26f), o(10f + k * 5.6f, 42f), 1f)
    for (k in 1 until 3) drawLine(ink.copy(alpha = 0.5f), o(11f, 24f + k * 6.5f), o(37f, 24f + k * 6.5f), 1f)
    drawOval(ink, o(10f, 24f), Size(28f, 20f), style = stroke(2.4f))
}

/** Syringe pointing down, needle tip at (24, 46); [push] 0..1 moves the plunger in. */
internal fun DrawScope.syringe(push: Float, liquid: Color, ink: Color, glass: Color) {
    drawRoundRect(glass, o(19f, 10f), Size(10f, 26f), CornerRadius(2f))
    drawRect(liquid, o(20.5f, 14f + 18f * push), Size(7f, 21f - 18f * push))
    drawRoundRect(ink, o(19f, 10f), Size(10f, 26f), CornerRadius(2f), style = stroke(2.2f))
    drawLine(ink, o(24f, 36f), o(24f, 46f), 1.6f, StrokeCap.Round)
    drawLine(ink, o(24f, 14f + 18f * push), o(24f, 2f + 18f * push), 2.4f, StrokeCap.Round)
    drawLine(ink, o(19f, 2f + 18f * push), o(29f, 2f + 18f * push), 2.6f, StrokeCap.Round)
    drawLine(ink, o(15f, 11f), o(33f, 11f), 2.4f, StrokeCap.Round)
}

/** Scissors, blades opened by [open] 0..1. */
internal fun DrawScope.scissors(open: Float, ink: Color, blade: Color) {
    val a = 8f + 22f * open
    for (sgn in listOf(-1f, 1f)) {
        withTransform({ rotate(sgn * a, o(24f, 26f)) }) {
            drawPath(Path().apply { moveTo(24f, 26f); lineTo(22.5f, 4f); lineTo(25.5f, 8f); close() }, blade)
            drawPath(Path().apply { moveTo(24f, 26f); lineTo(22.5f, 4f); lineTo(25.5f, 8f); close() }, ink, style = stroke(1.8f))
            drawCircle(ink, 5f, o(24f + sgn * 4f, 36f), style = stroke(2.4f))
        }
    }
    drawCircle(ink, 1.6f, o(24f, 26f))
}

/** Wall plug and socket; [gap] 0..1 pulls the plug out. */
internal fun DrawScope.plug(gap: Float, ink: Color, body: Color) {
    drawRoundRect(body, o(2f, 14f), Size(16f, 20f), CornerRadius(4f))
    drawRoundRect(ink, o(2f, 14f), Size(16f, 20f), CornerRadius(4f), style = stroke(2.2f))
    drawCircle(ink, 1.6f, o(10f, 20f)); drawCircle(ink, 1.6f, o(10f, 28f))
    val dx = 4f + 12f * gap
    drawRoundRect(body, o(16f + dx, 16f), Size(12f, 16f), CornerRadius(3f))
    drawRoundRect(ink, o(16f + dx, 16f), Size(12f, 16f), CornerRadius(3f), style = stroke(2.2f))
    drawLine(ink, o(16f + dx, 20f), o(12f + dx, 20f), 2f, StrokeCap.Round)
    drawLine(ink, o(16f + dx, 28f), o(12f + dx, 28f), 2f, StrokeCap.Round)
    drawLine(ink, o(28f + dx, 24f), o(46f + dx, 24f), 2.6f, StrokeCap.Round)
}

/** A friendly germ: wobbly blob with bumps, eyes and a frown. */
internal fun DrawScope.germ(tint: Color, ink: Color, wobble: Float) {
    val p = Path()
    for (i in 0..24) {
        val a = i / 24f * 2f * PI.toFloat()
        val r = 13f + 2.2f * sin(a * 6f + wobble * 6f)
        val x = 24f + cos(a) * r; val y = 25f + sin(a) * r
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    drawPath(p, tint)
    drawPath(p, ink, style = stroke(2.2f))
    drawCircle(Color.White, 3.4f, o(19.5f, 22f)); drawCircle(Color.White, 3.4f, o(28.5f, 22f))
    drawCircle(ink, 1.7f, o(20f, 22.5f)); drawCircle(ink, 1.7f, o(29f, 22.5f))
    drawArc(ink, 200f, 140f, false, o(19f, 28f), Size(10f, 6f), style = stroke(1.8f))
}

private fun lerpColor(a: Color, b: Color, t: Float): Color = androidx.compose.ui.graphics.lerp(a, b, t.coerceIn(0f, 1f))

// ---------------------------------------------------------------- shared particles

/** Expanding ripple ellipses on the water surface at ([x], [y]). */
private fun DrawScope.surfaceRipples(x: Float, y: Float, w: Float, t: Float, color: Color, rings: Int = 2) {
    for (k in 0 until rings) {
        val q = (t - k * 0.22f).coerceIn(0f, 1f)
        if (q <= 0f || q >= 1f) continue
        val rw = w * (0.2f + q)
        drawOval(color.copy(alpha = color.alpha * 0.8f * (1f - q)), o(x - rw / 2, y - rw * 0.14f), Size(rw, rw * 0.28f), style = Stroke(2f * density))
    }
}

/** A coloured swirl spreading at the surface (doses): flattened spiral arms and a soft disc. */
private fun DrawScope.swirl(x: Float, y: Float, w: Float, t: Float, tint: Color) {
    if (t <= 0f || t >= 1f) return
    val a = (1f - t)
    withTransform({ scale(1f, 0.36f, o(x, y)) }) {
        drawCircle(tint.copy(alpha = 0.22f * a), w * (0.2f + 0.5f * t), o(x, y))
        for (arm in 0 until 2) {
            val p = Path()
            for (i in 0..28) {
                val f = i / 28f
                val ang = arm * PI.toFloat() + f * 3.2f * PI.toFloat() + t * 4f
                val r = w * 0.55f * f * (0.3f + 0.7f * t)
                val px = x + cos(ang) * r; val py = y + sin(ang) * r
                if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
            }
            drawPath(p, tint.copy(alpha = 0.85f * a), style = stroke(3.2f * density * (1f - 0.5f * t)))
        }
    }
}

/** Bubbles rising from ([x], [y]). */
private fun DrawScope.bubbles(x: Float, y: Float, h: Float, t: Float, color: Color, n: Int = 7, seed: Int = 0) {
    for (k in 0 until n) {
        val q = ((t - hash(seed + k) * 0.5f) / 0.5f).coerceIn(0f, 1f)
        if (q <= 0f || q >= 1f) continue
        val bx = x + (hash(seed + k * 7) - 0.5f) * h * 0.5f + sin(q * 8f + k) * 4f * density
        val by = y - h * q
        val r = (2f + 3f * hash(seed + k * 13)) * density
        drawCircle(color.copy(alpha = color.alpha * (1f - q)), r, o(bx, by), style = Stroke(1.4f * density))
    }
}

/** Little yellow sparks (zig-zags) bursting from ([x], [y]). */
private fun DrawScope.sparks(x: Float, y: Float, w: Float, t: Float, color: Color, seed: Int = 3) {
    if (t <= 0f || t >= 1f) return
    for (k in 0 until 7) {
        val a = (k / 7f + hash(seed + k) * 0.1f) * 2f * PI.toFloat()
        val r0 = w * 0.1f + w * 0.35f * t
        val r1 = r0 + w * 0.16f * (1f - t)
        val p = Path().apply {
            moveTo(x + cos(a) * r0, y + sin(a) * r0)
            lineTo(x + cos(a + 0.18f) * (r0 + r1) / 2, y + sin(a + 0.18f) * (r0 + r1) / 2)
            lineTo(x + cos(a) * r1, y + sin(a) * r1)
        }
        drawPath(p, color.copy(alpha = 1f - t), style = stroke(2.6f * density))
    }
}

/** Four-point twinkles around ([x], [y]). */
private fun DrawScope.twinkles(x: Float, y: Float, w: Float, t: Float, color: Color, n: Int = 6, seed: Int = 11) {
    for (k in 0 until n) {
        val q = ((t - hash(seed + k) * 0.4f) / 0.6f).coerceIn(0f, 1f)
        if (q <= 0f || q >= 1f) continue
        val a = k / n.toFloat() * 2f * PI.toFloat()
        val r = w * (0.35f + 0.25f * q)
        val cx = x + cos(a) * r; val cy = y + sin(a) * r * 0.8f
        val s = (3f + 4f * sin(q * PI.toFloat())) * density
        drawLine(color, o(cx - s, cy), o(cx + s, cy), 2f * density, StrokeCap.Round)
        drawLine(color, o(cx, cy - s), o(cx, cy + s), 2f * density, StrokeCap.Round)
    }
}

/** Red pulse rings (trouble). */
private fun DrawScope.pulse(x: Float, y: Float, w: Float, t: Float, color: Color) {
    for (k in 0 until 3) {
        val q = ((t * 1.6f) - k * 0.3f).let { it - kotlin.math.floor(it) }
        if (t * 1.6f - k * 0.3f < 0f) continue
        drawCircle(color.copy(alpha = 0.7f * (1f - q) * (1f - t * 0.5f)), w * (0.25f + 0.55f * q), o(x, y), style = Stroke(3.5f * density * (1f - q * 0.6f)))
    }
}

/** A falling teardrop at ([x], [y]). */
private fun DrawScope.drop(x: Float, y: Float, r: Float, color: Color, ink: Color) {
    val p = Path().apply { moveTo(x, y - r * 1.8f); cubicTo(x + r * 0.2f, y - r, x + r, y - r * 0.2f, x + r, y + r * 0.2f); cubicTo(x + r, y + r * 0.8f, x + r * 0.5f, y + r, x, y + r); cubicTo(x - r * 0.5f, y + r, x - r, y + r * 0.8f, x - r, y + r * 0.2f); cubicTo(x - r, y - r * 0.2f, x - r * 0.2f, y - r, x, y - r * 1.8f); close() }
    drawPath(p, color)
    drawPath(p, ink, style = Stroke(1.4f * density))
}

/** The whole view darkening (power cut, lights off). */
private fun DrawScope.dim(amount: Float) {
    if (amount > 0.005f) drawRect(Color(0xFF02060C).copy(alpha = amount.coerceIn(0f, 0.8f)))
}

// ---------------------------------------------------------------- dispatcher

/** Draws one frame of [fx]. */
internal fun DrawScope.drawFx(fx: TankFx, f: FxFrame) {
    faded(f.alpha) {
        when (fx.kind) {
            FxKind.FEED -> when (feedTool(fx.food ?: FoodKind.FLAKE)) {
                FeedTool.PINCH -> feedPinch(fx, f)
                FeedTool.SHAKER -> feedShaker(fx, f)
                FeedTool.CUBE -> feedCube(fx, f)
                FeedTool.CLIP -> feedClip(f)
                FeedTool.PIPETTE -> feedPipette(fx, f, foodColors(fx.food ?: FoodKind.CORAL_FOOD)[0])
            }
            FxKind.PIPETTE -> feedPipette(fx, f, fx.tint.orElse(f.pal.green))
            FxKind.DOSE -> dose(fx, f)
            FxKind.WATER_CHANGE -> waterChange(fx, f)
            FxKind.HEATER_BREAK, FxKind.HEATER_FIX, FxKind.HEATER_STUCK -> heaterFx(fx, f)
            FxKind.THERMO_UP, FxKind.THERMO_DOWN -> thermoFx(fx.kind == FxKind.THERMO_UP, f)
            FxKind.SALT_UP -> saltUp(f)
            FxKind.SALT_DOWN -> freshPour(f)
            FxKind.PUMP_ON, FxKind.PUMP_OFF, FxKind.PUMP_FAIL_TONIGHT -> pumpFx(fx, f)
            FxKind.LIGHTS_ON, FxKind.LIGHTS_OFF, FxKind.LIGHTS_AUTO -> lightsFx(fx, f)
            FxKind.POWER_OUT -> powerOut(f)
            FxKind.ALERT -> alert(fx, f)
            FxKind.PEST_ADD -> pestAdd(fx, f)
            FxKind.PEST_REMOVE -> pestRemove(fx, f)
            FxKind.BAG -> bagFx(fx, f)
            FxKind.NET -> netFx(f)
            FxKind.DEVICE_ON, FxKind.DEVICE_OFF -> deviceFx(fx, f)
            FxKind.MAINTAIN -> maintainFx(fx, f)
            FxKind.SNIP -> snipFx(fx, f)
            FxKind.SHELLS -> shellsFx(f)
            FxKind.ROCKS -> rocksFx(f)
            FxKind.HEARTS -> heartsFx(f)
            FxKind.TAP_GLASS -> tapGlassFx(f)
            FxKind.GROW -> growFx(fx, f)
            FxKind.SPEED -> speedFx(fx, f)
            FxKind.RESET -> resetFx(f)
            FxKind.SPARKLE -> sparkleFx(fx, f)
            FxKind.TIMELAPSE -> timelapseFx(f)
        }
    }
}

private fun Color.orElse(c: Color) = if (this == Color.Unspecified) c else this

// ---------------------------------------------------------------- feeding

/**
 * Shaker: drops in above the feeding spot, tips mouth-down, taps three times (each tap shakes a puff of food out),
 * rights itself and floats away. Particles fall to the projected spawn point within the sim's spawn spread; the sim
 * command goes out as the first puff lands (FxKind.FEED.impactMs), so the real particles pick up from there.
 */
private fun DrawScope.feedShaker(fx: TankFx, f: FxFrame) {
    val food = fx.food ?: FoodKind.FLAKE
    val u = f.unit
    val ms = f.ms
    val enter = easeOutBack(f.span(0f, 350f))
    val tilt = 128f * ease(f.span(350f, 750f)) * (1f - ease(f.span(1650f, 2050f)))
    val leave = ease(f.span(2050f, 2600f))
    // three taps between 750 and 1650: a quick jerk along the mouth direction
    var jerk = 0f
    if (ms in 750f..1650f) { val ph = ((ms - 750f) % 300f) / 300f; if (ph < 0.4f) jerk = sin(ph / 0.4f * PI.toFloat()) }
    val mouthAt = o(f.ax, f.ay - u * 0.6f)
    // place the box so that, fully tilted, the mouth sits at mouthAt
    val m = rot(24f, 4f, 128f)
    val s = u / 48f
    val cx0 = mouthAt.x - (m.x - 24f) * s
    val cy0 = mouthAt.y - (m.y - 24f) * s
    val dirX = (m.x - 24f) / 20f; val dirY = (m.y - 24f) / 20f
    val cx = cx0 + dirX * jerk * 3f * s
    val cy = cy0 - (1f - enter) * u * 0.8f - leave * u * 0.9f + dirY * jerk * 3f * s
    val cols = foodColors(food)
    // particles: 3 puffs × 5 bits; each falls from the mouth to the surface, then settles or floats
    val mouth = boxPoint(cx, cy, u, tilt, 24f, 4f)
    for (tap in 0 until 3) for (j in 0 until 5) {
        val born = 810f + tap * 300f + j * 22f
        val age = ms - born
        if (age < 0f) continue
        val fall = 430f
        val id = tap * 5 + j
        val landX = f.ax + (hash(id) - 0.5f) * 2f * 2.5f * f.ppcm
        val col = cols[id % cols.size]
        val r = (if (food == FoodKind.ALGAE_WAFER) 4.5f else if (food == FoodKind.FLAKE) 3.2f else 2.8f) * density
        if (age < fall) {
            val q = age / fall
            val x = mouth.x + (landX - mouth.x) * q
            val y = mouth.y + (f.ay - mouth.y) * q * q
            if (food == FoodKind.FLAKE) drawRect(col, o(x - r, y - r * 0.7f), Size(r * 2f, r * 1.4f)) else drawCircle(col, r, o(x, y))
        } else {
            val q = ((age - fall) / 500f).coerceIn(0f, 1f)
            val sink = if (food.floats) 0f else q * 1.2f * f.ppcm
            if (q < 1f) {
                if (food == FoodKind.FLAKE) drawRect(col.copy(alpha = 1f - q), o(landX - r, f.ay + sink - r * 0.7f), Size(r * 2f, r * 1.4f))
                else drawCircle(col.copy(alpha = 1f - q), r, o(landX, f.ay + sink))
            }
            if (j == 0) surfaceRipples(landX, f.ay, u * 0.8f, q, f.pal.water)
        }
    }
    box(cx, cy, u, tilt) { shakerBottle(food, cols[0], f.pal.ink, f.pal.glass.copy(alpha = 0.9f)) }
}

/**
 * Hand pinch (video T97): thumb and finger come down over the drop point beside the return, rub open twice to let the
 * granules go (each rub a little shower that the return then carries off), and lift away. The sim command goes out as
 * the first granules land (FxKind.FEED.impactMs), so the real particles carry on from there.
 */
private fun DrawScope.feedPinch(fx: TankFx, f: FxFrame) {
    val food = fx.food ?: FoodKind.PELLET
    val u = f.unit
    val enter = easeOutBack(f.span(0f, 450f))
    val leave = ease(f.span(1900f, 2500f))
    // the rub: fingertips part twice between 800 and 1600 ms
    var open = 0f
    if (f.ms in 800f..1600f) { val ph = ((f.ms - 800f) % 400f) / 400f; open = sin(ph * PI.toFloat()) }
    val tipX = f.ax
    val tipY = f.ay - u * 0.55f - (1f - enter) * u * 0.9f - leave * u * 1.1f
    val r = u * 0.085f
    val cols = foodColors(food)
    // granules: two showers of 8, falling to the surface, then drifting off with the current (+x on screen)
    for (shower in 0 until 2) for (j in 0 until 8) {
        val born = 900f + shower * 400f + j * 25f
        val age = f.ms - born
        if (age < 0f) continue
        val id = shower * 8 + j
        val fall = 380f
        val sx = tipX + (hash(id) - 0.5f) * r * 2f
        val gr = 2.2f * density
        val col = cols[id % cols.size]
        if (age < fall) {
            val q = age / fall
            drawCircle(col, gr, o(sx, tipY + r + (f.ay - tipY - r) * q * q))
        } else {
            val q = ((age - fall) / 700f).coerceIn(0f, 1f)
            if (q < 1f) drawCircle(col.copy(alpha = 1f - q), gr, o(sx + q * (0.8f + hash(id + 9)) * 4f * f.ppcm, f.ay + q * (1f + hash(id + 3)) * 2f * f.ppcm))
            if (j == 0) surfaceRipples(sx, f.ay, u * 0.7f, q, f.pal.water)
        }
    }
    // the hand: index finger from the upper right, thumb from the right below it, tips meeting over the drop point
    val spread = open * r * 1.6f
    fun finger(fromX: Float, fromY: Float, toX: Float, toY: Float, w: Float) {
        drawLine(f.pal.ink, o(fromX, fromY), o(toX, toY), w + 3f * density, StrokeCap.Round)
        drawLine(FoodPalette.skin, o(fromX, fromY), o(toX, toY), w, StrokeCap.Round)
        drawCircle(FoodPalette.skinShade, w * 0.28f, o(toX - (toX - fromX) * 0.08f, toY - (toY - fromY) * 0.08f))
    }
    finger(tipX + u * 0.75f, tipY - u * 0.85f, tipX + spread * 0.3f, tipY - r * 0.4f - spread, r * 2f)
    finger(tipX + u * 0.9f, tipY - u * 0.15f, tipX + spread, tipY + r * 0.5f + spread * 0.3f, r * 2.3f)
}

/** Frozen cube: pops out above the water, drops in with a splash, then floats and thaws as bits drift down. */
private fun DrawScope.feedCube(fx: TankFx, f: FxFrame) {
    val food = fx.food ?: FoodKind.MYSIS
    val u = f.unit
    val appear = easeOutBack(f.span(0f, 350f))
    val fall = f.span(450f, 900f)
    val y = if (f.ms < 450f) f.ay - u * 0.9f + sin(f.ms / 60f) * 2f * density else f.ay - u * 0.9f + u * 0.9f * fall * fall
    val thaw = f.span(950f, 2300f)
    val bob = if (f.ms > 900f) sin((f.ms - 900f) / 140f) * 2.5f * density * (1f - thaw) else 0f
    if (f.ms > 900f) {
        surfaceRipples(f.ax, f.ay, u, f.span(900f, 1700f), f.pal.water, rings = 3)
        val cols = foodColors(food)
        for (k in 0 until 9) {
            val q = ((f.ms - 1000f - k * 110f) / 900f).coerceIn(0f, 1f)
            if (q <= 0f || q >= 1f) continue
            val x = f.ax + (hash(k + 40) - 0.5f) * 2.5f * 2f * f.ppcm * q
            drawCircle(cols[k % cols.size].copy(alpha = 1f - q * 0.6f), 2.6f * density, o(x, f.ay + q * 3f * f.ppcm))
        }
    }
    box(f.ax, y + bob - u * 0.1f * (if (f.ms > 900f) 0f else 1f), u * 0.75f * appear, sx = if (f.ms in 900f..1000f) 1.15f else 1f, sy = if (f.ms in 900f..1000f) 0.85f else 1f) {
        frozenCube(food, f.pal.ink, f.pal.cold, thaw)
    }
}

/** Nori: the clip glides onto the left glass, clamps with a squash, and stays as the sim's sheet appears. */
private fun DrawScope.feedClip(f: FxFrame) {
    val u = f.unit
    val q = ease(f.span(0f, 800f))
    val x = f.ax + u * 0.35f + (1f - q) * u * 0.6f
    val y = f.ay - (1f - q) * u * 1.0f
    val clamp = f.span(820f, 1000f)
    val sq = if (clamp in 0.01f..0.99f) 1f - 0.15f * sin(clamp * PI.toFloat()) else 1f
    if (f.ms > 900f) surfaceRipples(x - u * 0.25f, y - u * 0.25f, u * 0.6f, f.span(900f, 1500f), f.pal.water)
    box(x, y, u * 0.9f, sx = 2f - sq, sy = sq) { foodAt(FoodKind.NORI, 0f, 0f, 48f) }
}

/** Pipette: dips in, squeezes three times, each squeeze puffing a cloud of [tint] into the water. */
private fun DrawScope.feedPipette(fx: TankFx, f: FxFrame, tint: Color) {
    val u = f.unit
    val enter = easeOutBack(f.span(0f, 400f))
    val leave = ease(f.span(1700f, 2200f))
    var squeeze = 0f
    if (f.ms in 450f..1450f) { val ph = ((f.ms - 450f) % 330f) / 330f; squeeze = sin(ph * PI.toFloat()) }
    val tilt = -28f
    val tip = o(f.ax, f.ay + 0.04f * u)
    val t = rot(24f, 46f, tilt)
    val s = u / 48f
    val cx = tip.x - (t.x - 24f) * s
    val cy = tip.y - (t.y - 24f) * s - (1f - enter) * u * 0.7f - leave * u * 0.8f
    for (k in 0 until 3) {
        val q = f.span(560f + k * 330f, 1700f + k * 330f)
        if (q <= 0f || q >= 1f) continue
        for (j in 0 until 9) {
            val a = hash(k * 11 + j) * 2f * PI.toFloat()
            val r = (0.4f + 0.6f * hash(j + k * 5)) * u * 0.42f * ease(q)
            drawCircle(tint.copy(alpha = 0.8f * (1f - q)), (2f + 2f * hash(j)) * density, o(tip.x + cos(a) * r, tip.y + u * 0.08f + sin(a) * r * 0.6f + q * u * 0.2f))
        }
        drawCircle(tint.copy(alpha = 0.18f * (1f - q)), u * 0.3f * ease(q), o(tip.x, tip.y + u * 0.12f))
    }
    surfaceRipples(f.ax, f.ay, u * 0.7f, f.span(500f, 1300f), f.pal.water)
    box(cx, cy, u, tilt) { pipette(tint, fx.tint2.orElse(f.pal.pink), f.pal.ink, f.pal.glass.copy(alpha = 0.9f), squeeze) }
}

// ---------------------------------------------------------------- dosing and water

/** Dropper bottle tips over the return, three drops fall, each blooming a coloured swirl on the surface. */
private fun DrawScope.dose(fx: TankFx, f: FxFrame) {
    val u = f.unit
    val tint = fx.tint.orElse(f.pal.purple)
    val enter = easeOutBack(f.span(0f, 300f))
    val tilt = 168f * ease(f.span(300f, 650f)) * (1f - ease(f.span(1750f, 2050f)))
    val leave = ease(f.span(2050f, 2400f))
    val m = rot(24f, 1f, 168f)
    val s = u / 48f
    val tipTarget = o(f.ax, f.ay - u * 0.55f)
    val cx = tipTarget.x - (m.x - 24f) * s
    val cy = tipTarget.y - (m.y - 24f) * s - (1f - enter) * u * 0.7f - leave * u * 0.9f
    val tip = boxPoint(cx, cy, u, tilt, 24f, 1f)
    for (k in 0 until 3) {
        val start = 650f + k * 360f
        val grow = f.span(start, start + 120f)
        val fall = f.span(start + 120f, start + 420f)
        val r = 4f * density
        if (grow > 0f && fall <= 0f) drop(tip.x, tip.y + r * grow, r * grow, tint, f.pal.ink)
        else if (fall > 0f && fall < 1f) drop(tip.x, tip.y + (f.ay - tip.y) * fall * fall, r, tint, f.pal.ink)
        swirl(f.ax + (k - 1) * 0.8f * f.ppcm, f.ay, u * 0.9f, f.span(start + 420f, start + 1500f), tint)
        if (fall >= 1f) surfaceRipples(f.ax, f.ay, u * 0.6f, f.span(start + 420f, start + 1000f), Color.White)
    }
    box(cx, cy, u, tilt) { doseBottle(tint, fx.tint2.orElse(tint), f.pal.ink, f.pal.body) }
}

/**
 * Water change: a siphon hose runs from the water into a bucket outside the right end; old water flows out along it
 * while the waterline on the front glass drops; then a jug of fresh salt water pours back in with bubbles and the line
 * returns to the top. The sim's water change goes out when the drain finishes.
 */
private fun DrawScope.waterChange(fx: TankFx, f: FxFrame) {
    val pal = f.pal
    val u = f.unit
    val frac = fx.amount.coerceIn(0.02f, 0.5f)
    val dropCm = (frac / 0.25f * 3f).coerceIn(1.2f, 6f)
    val down = ease(f.span(300f, 1500f)) * (1f - ease(f.span(1900f, 3100f)))
    val d = dropCm * down
    val wy = com.reeflive.app.render.Camera.WATER_Y - 0.4f
    val gz = com.reeflive.app.render.Camera.TANK[2]; val gl = com.reeflive.app.render.Camera.TANK[0]
    // 1) the waterline band on the front glass
    val a0 = f.project(0f, wy, gz); val a1 = f.project(gl, wy, gz)
    val b0 = f.project(0f, wy - d, gz); val b1 = f.project(gl, wy - d, gz)
    if (a0 != null && a1 != null && b0 != null && b1 != null && d > 0.05f) {
        val band = Path().apply { moveTo(a0.x, a0.y); lineTo(a1.x, a1.y); lineTo(b1.x, b1.y); lineTo(b0.x, b0.y); close() }
        drawPath(band, Color(0xFF02101E).copy(alpha = 0.45f))
        val wave = Path()
        val n = 24
        for (i in 0..n) {
            val t = i / n.toFloat()
            val x = b0.x + (b1.x - b0.x) * t
            val y = b0.y + (b1.y - b0.y) * t + sin(t * 18f + f.ms / 120f) * 2f * density
            if (i == 0) wave.moveTo(x, y) else wave.lineTo(x, y)
        }
        drawPath(wave, pal.water, style = stroke(3f * density))
        drawPath(wave, Color.White.copy(alpha = 0.6f), style = stroke(1.2f * density))
    }
    // 2) hose from the siphon end to the bucket
    val inWater = f.project(FxSpots.SIPHON.x, FxSpots.SIPHON.y, FxSpots.SIPHON.z) ?: o(f.ax, f.ay)
    val rim = f.project(FxSpots.SIPHON.x + 6f, 64f, FxSpots.SIPHON.z) ?: o(inWater.x, inWater.y - u)
    val bk = f.project(FxSpots.BUCKET.x, FxSpots.BUCKET.y, FxSpots.BUCKET.z) ?: o(rim.x + u, rim.y)
    val bx = bk.x.coerceAtMost(size.width - u * 0.55f); val by = bk.y.clampSafe(u * 0.8f, size.height - u)
    val hoseIn = ease(f.span(0f, 350f)) * (1f - ease(f.span(1600f, 1900f)))
    if (hoseIn > 0f) {
        val end = o(rim.x + (inWater.x - rim.x) * hoseIn, rim.y + (inWater.y - rim.y) * hoseIn)
        val hose = Path().apply { moveTo(bx - u * 0.1f, by - u * 0.3f); cubicTo(bx - u * 0.1f, rim.y - u * 0.4f, rim.x, rim.y - u * 0.5f, rim.x, rim.y); lineTo(end.x, end.y) }
        drawPath(hose, pal.ink, style = stroke(7f * density))
        drawPath(hose, Color(0xFFBFE8D9), style = stroke(4f * density))
        // old water flowing out: brown dashes running along the hose
        if (f.ms in 300f..1550f) {
            for (k in 0 until 6) {
                val q = ((f.ms / 600f + k / 6f) % 1f)
                val t = 1f - q
                val x = if (t > 0.5f) end.x + (rim.x - end.x) * (1f - t) * 2f else rim.x + (bx - u * 0.1f - rim.x) * (0.5f - t) * 2f
                val y = if (t > 0.5f) end.y + (rim.y - end.y) * (1f - t) * 2f else rim.y - sin((0.5f - t) * 2f * PI.toFloat()) * u * 0.35f + (by - u * 0.3f - rim.y) * (0.5f - t) * 2f
                drawCircle(Color(0xFF9C7A4A), 2.2f * density, o(x, y))
            }
        }
    }
    box(bx, by, u * 0.85f) { bucket(Color(0xFFE9F3FF), Color(0xFF9C7A4A), pal.ink) }
    // 3) fresh water pours in from a jug
    val jugIn = easeOutBack(f.span(1600f, 1900f)) * (1f - ease(f.span(3150f, 3500f)))
    if (jugIn > 0f) {
        val pour = f.span(1900f, 3100f)
        val tilt = 55f * ease(f.span(1700f, 1950f)) * (1f - ease(f.span(3000f, 3250f)))
        val target = f.project(84f, wy - d, 20f) ?: o(f.ax, f.ay)
        val jx = target.x - u * 0.55f; val jy = target.y - u * 1.0f - (1f - jugIn) * u * 0.5f
        val spout = boxPoint(jx, jy, u * 0.85f, tilt, 41f, 10f)
        if (pour > 0f && pour < 0.97f) {
            val stream = Path().apply { moveTo(spout.x, spout.y); quadraticTo(spout.x + u * 0.12f, spout.y + (target.y - spout.y) * 0.3f, target.x, target.y) }
            drawPath(stream, pal.water.copy(alpha = 0.85f), style = stroke(6f * density))
            drawPath(stream, Color.White.copy(alpha = 0.7f), style = stroke(2f * density))
            surfaceRipples(target.x, target.y, u * 0.7f, (f.ms / 500f) % 1f, Color.White)
            bubbles(target.x, target.y + u * 0.6f, u * 0.6f, (f.ms % 900f) / 900f, pal.bubble, seed = (f.ms / 900f).toInt() * 7)
        }
        box(jx, jy, u * 0.85f, tilt) { jug(Color(0xFFE9F3FF), pal.water, pal.ink) }
    }
}

// ---------------------------------------------------------------- equipment and trouble

private fun DrawScope.heaterFx(fx: TankFx, f: FxFrame) {
    val u = f.unit
    val pal = f.pal
    val glow = when (fx.kind) {
        FxKind.HEATER_BREAK -> 1f - ease(f.span(450f, 1100f))
        FxKind.HEATER_FIX -> ease(f.span(200f, 900f))
        else -> 0.8f + 0.2f * sin(f.ms / 90f)
    }
    val shake = if (fx.kind == FxKind.HEATER_BREAK && f.ms in 400f..1000f) sin(f.ms / 18f) * 3f * density else 0f
    if (fx.kind == FxKind.HEATER_STUCK) {
        pulse(f.ax, f.ay, u * 1.4f, f.span(0f, f.dur), pal.heat)
        for (k in 0 until 3) {
            val q = ((f.ms / 700f) + k / 3f) % 1f
            val p = Path()
            for (i in 0..10) { val t = i / 10f; val x = f.ax + u * (0.45f + 0.12f * k) + sin(t * 9f + f.ms / 100f) * 3f * density; val y = f.ay + u * 0.3f - t * u * 0.8f - q * u * 0.2f; if (i == 0) p.moveTo(x, y) else p.lineTo(x, y) }
            drawPath(p, pal.heat.copy(alpha = 0.6f * (1f - q)), style = stroke(2f * density))
        }
    }
    box(f.ax + shake, f.ay, u) { heater(glow, pal.heat, pal.ink, pal.glass.copy(alpha = 0.9f)) }
    when (fx.kind) {
        FxKind.HEATER_BREAK -> {
            sparks(f.ax, f.ay - u * 0.45f, u, f.span(400f, 1200f), pal.spark)
            val pop = easeOutBack(f.span(900f, 1250f))
            if (pop > 0f) {
                box(f.ax + u * 0.75f, f.ay - u * 0.35f, u * 0.55f * pop) {
                    drawCircle(Color.White, 22f, o(24f, 24f)); drawCircle(pal.ink, 22f, o(24f, 24f), style = stroke(3f))
                    glyphAt(ReefGlyphs.plugOff, 8f, 8f, 32f, pal.ink, pal.danger)
                }
            }
            val drop = ease(f.span(1000f, 2200f))
            if (drop > 0f) box(f.ax + u * 0.8f, f.ay + u * 0.45f, u * 0.6f) { thermometer(0.7f - 0.4f * drop, pal.cold, pal.ink, pal.body) }
        }
        FxKind.HEATER_FIX -> twinkles(f.ax, f.ay, u, f.span(500f, 1600f), pal.spark)
        else -> {
            val rise = ease(f.span(300f, 2000f))
            box(f.ax + u * 0.8f, f.ay + u * 0.3f, u * 0.6f) { thermometer(0.4f + 0.55f * rise, pal.heat, pal.ink, pal.body) }
        }
    }
}

private fun DrawScope.thermoFx(up: Boolean, f: FxFrame) {
    val pal = f.pal
    val u = f.unit
    val q = ease(f.span(200f, 1500f))
    val level = if (up) 0.35f + 0.55f * q else 0.85f - 0.55f * q
    val pop = easeOutBack(f.span(0f, 300f))
    if (up) {
        for (k in 0 until 3) {
            val w = ((f.ms / 600f) + k / 3f) % 1f
            val p = Path()
            for (i in 0..8) { val t = i / 8f; val x = f.ax + (k - 1) * u * 0.3f + sin(t * 8f + f.ms / 90f) * 2.5f * density; val y = f.ay - u * 0.5f - t * u * 0.45f - w * u * 0.15f; if (i == 0) p.moveTo(x, y) else p.lineTo(x, y) }
            drawPath(p, pal.heat.copy(alpha = 0.7f * (1f - w)), style = stroke(2.2f * density))
        }
    } else {
        twinkles(f.ax, f.ay - u * 0.1f, u, f.span(200f, 1700f), pal.cold)
        box(f.ax + u * 0.55f, f.ay - u * 0.45f, u * 0.45f * pop) { glyphAt(ReefGlyphs.snowflake, 0f, 0f, 48f, pal.cold, Color.White) }
    }
    box(f.ax, f.ay, u * 0.85f * pop) { thermometer(level, if (up) pal.heat else pal.cold, pal.ink, pal.body) }
}

/** Salt: a scoop tips white crystals in; they sparkle into a white swirl. */
private fun DrawScope.saltUp(f: FxFrame) {
    val pal = f.pal
    val u = f.unit
    val tilt = 110f * ease(f.span(250f, 650f)) * (1f - ease(f.span(1500f, 1850f)))
    val enter = easeOutBack(f.span(0f, 300f))
    val cx = f.ax - u * 0.3f; val cy = f.ay - u * 0.95f - (1f - enter) * u * 0.5f
    val lip = boxPoint(cx, cy, u * 0.8f, tilt, 36f, 18f)
    for (k in 0 until 14) {
        val born = 650f + k * 55f
        val q = ((f.ms - born) / 380f)
        if (q < 0f || q > 1f) continue
        val x = lip.x + (f.ax + (hash(k) - 0.5f) * 3f * f.ppcm - lip.x) * q
        val y = lip.y + (f.ay - lip.y) * q * q
        drawRect(Color.White, o(x - 2f * density, y - 2f * density), Size(4f * density, 4f * density))
    }
    swirl(f.ax, f.ay, u * 0.8f, f.span(1000f, 2100f), Color.White)
    box(cx, cy, u * 0.8f, tilt) {
        val cup = Path().apply { moveTo(10f, 18f); lineTo(38f, 18f); lineTo(34f, 38f); lineTo(14f, 38f); close() }
        drawPath(cup, pal.cyan)
        drawOval(Color.White, o(11f, 12f), Size(26f, 10f))
        for (k in 0 until 5) drawRect(Color.White, o(14f + k * 4.5f, 13f + (k % 2) * 2f), Size(3f, 3f))
        drawPath(cup, pal.ink, style = stroke(2.4f))
        drawLine(pal.ink, o(38f, 22f), o(46f, 26f), 3f, StrokeCap.Round)
    }
}

/** Fresh water (salinity drops): a jug pours a long stream, the water ripples, a drop-down sign bobs beside it. */
private fun DrawScope.freshPour(f: FxFrame) {
    val pal = f.pal
    val u = f.unit
    val enter = easeOutBack(f.span(0f, 300f))
    val tilt = 60f * ease(f.span(250f, 600f)) * (1f - ease(f.span(1900f, 2200f)))
    val jx = f.ax - u * 0.6f; val jy = f.ay - u * 1.05f - (1f - enter) * u * 0.5f
    val spout = boxPoint(jx, jy, u * 0.85f, tilt, 41f, 10f)
    val pour = f.span(600f, 1950f)
    if (pour > 0f && pour < 1f) {
        val stream = Path().apply { moveTo(spout.x, spout.y); quadraticTo(spout.x + u * 0.15f, spout.y + (f.ay - spout.y) * 0.3f, f.ax, f.ay) }
        drawPath(stream, pal.cold.copy(alpha = 0.85f), style = stroke(6f * density))
        drawPath(stream, Color.White.copy(alpha = 0.7f), style = stroke(2f * density))
        surfaceRipples(f.ax, f.ay, u * 0.8f, (f.ms / 450f) % 1f, Color.White)
        bubbles(f.ax, f.ay + u * 0.6f, u * 0.6f, (f.ms % 800f) / 800f, pal.bubble, seed = (f.ms / 800f).toInt() * 5)
    }
    box(jx, jy, u * 0.85f, tilt) { jug(Color(0xFFE9F3FF), pal.cold, pal.ink) }
    val pop = easeOutBack(f.span(500f, 900f))
    box(f.ax + u * 0.7f, f.ay - u * 0.5f + sin(f.ms / 200f) * 3f * density, u * 0.5f * pop) {
        drawCircle(Color.White, 22f, o(24f, 24f)); drawCircle(pal.ink, 22f, o(24f, 24f), style = stroke(3f))
        glyphAt(ReefGlyphs.dropDown, 8f, 8f, 32f, pal.ink, pal.cold)
    }
}

private fun DrawScope.pumpFx(fx: TankFx, f: FxFrame) {
    val pal = f.pal
    val u = f.unit
    val mirror = fx.at.x > com.reeflive.app.render.Camera.TANK[0] / 2f
    val on = fx.kind == FxKind.PUMP_ON
    // blade angle: integrate a speed that ramps up (on) or down (off)
    val t = f.ms / 1000f
    val ang = if (on) 360f * (t * t * 1.2f) else 360f * (1.8f * t - 0.45f * t * t).coerceAtLeast(0f)
    val flow = if (on) ease(f.span(200f, 900f)) else 1f - ease(f.span(100f, 1100f))
    for (k in 0 until 3) {
        val q = ((f.ms / 500f) + k / 3f) % 1f
        val dir = if (mirror) -1f else 1f
        val x0 = f.ax + dir * u * (0.45f + 0.9f * q)
        val p = Path().apply { moveTo(x0 - dir * u * 0.25f, f.ay - u * 0.15f + k * u * 0.15f); quadraticTo(x0, f.ay - u * 0.25f + k * u * 0.15f, x0 + dir * u * 0.25f, f.ay - u * 0.12f + k * u * 0.15f) }
        drawPath(p, pal.water.copy(alpha = 0.8f * flow * (1f - q)), style = stroke(3f * density))
    }
    box(f.ax, f.ay, u * 0.85f, sx = if (mirror) -1f else 1f) { pump(ang, on || flow > 0.3f, pal.ink, pal.body, pal.cyan) }
    when (fx.kind) {
        FxKind.PUMP_OFF -> {
            sparks(f.ax, f.ay - u * 0.3f, u * 0.8f, f.span(150f, 800f), pal.spark)
            val pop = easeOutBack(f.span(700f, 1000f))
            box(f.ax, f.ay - u * 0.62f, u * 0.4f * pop) { drawCircle(pal.danger, 20f, o(24f, 24f)); drawCircle(pal.ink, 20f, o(24f, 24f), style = stroke(3f)); drawLine(Color.White, o(16f, 16f), o(32f, 32f), 5f, StrokeCap.Round); drawLine(Color.White, o(32f, 16f), o(16f, 32f), 5f, StrokeCap.Round) }
        }
        FxKind.PUMP_FAIL_TONIGHT -> {
            val pop = easeOutBack(f.span(200f, 600f))
            box(f.ax, f.ay - u * 0.65f, u * 0.5f * pop) { drawCircle(pal.ink, 22f, o(24f, 24f)); glyphAt(ReefGlyphs.moon, 8f, 8f, 32f, pal.yellow, Color.White) }
            twinkles(f.ax, f.ay - u * 0.65f, u * 0.6f, f.span(300f, 1600f), pal.yellow, n = 4)
        }
        else -> bubbles(f.ax, f.ay, u * 0.8f, f.span(300f, 1500f), pal.bubble, seed = 21)
    }
}

private fun DrawScope.lightsFx(fx: TankFx, f: FxFrame) {
    val pal = f.pal
    val u = f.unit
    val leds = FloatArray(6)
    val level = when (fx.kind) {
        FxKind.LIGHTS_ON -> ease(f.span(100f, 900f))
        FxKind.LIGHTS_OFF -> {
            // the flicker: on, off, half, off, a last try, dark
            val ms = f.ms
            when {
                ms < 200f -> 1f
                ms < 300f -> 0.15f
                ms < 420f -> 0.85f
                ms < 520f -> 0.05f
                ms < 600f -> 0.55f
                else -> 0f
            }
        }
        else -> 0.6f + 0.4f * sin(f.ms / 260f)
    }
    for (i in leds.indices) leds[i] = if (fx.kind == FxKind.LIGHTS_ON) ease(f.span(100f + i * 90f, 400f + i * 90f)) else level
    if (fx.kind == FxKind.LIGHTS_OFF) dim((1f - level) * 0.35f * (1f - f.span(1500f, f.dur)))
    if (level > 0.02f) {
        val beam = Path().apply { moveTo(f.ax - u * 0.75f, f.ay + u * 0.1f); lineTo(f.ax + u * 0.75f, f.ay + u * 0.1f); lineTo(f.ax + u * 1.3f, f.ay + u * 1.6f); lineTo(f.ax - u * 1.3f, f.ay + u * 1.6f); close() }
        drawPath(beam, androidx.compose.ui.graphics.Brush.verticalGradient(listOf(pal.light.copy(alpha = 0.45f * level), Color.Transparent), startY = f.ay, endY = f.ay + u * 1.6f))
    }
    box(f.ax, f.ay, u * 1.4f) { lampBar(leds, pal.light, pal.ink, Color(0xFFE9F3FF)) }
    if (fx.kind == FxKind.LIGHTS_AUTO) {
        val pop = easeOutBack(f.span(0f, 400f))
        box(f.ax + u * 0.9f, f.ay - u * 0.15f, u * 0.5f * pop) { drawCircle(Color.White, 22f, o(24f, 24f)); drawCircle(pal.ink, 22f, o(24f, 24f), style = stroke(3f)); glyphAt(ReefGlyphs.clockSun, 8f, 8f, 32f, pal.ink, pal.orange) }
    }
}

/** Power cut: the plug pulls out with a spark, the tank flickers and goes dim, and the dead equipment pops up crossed out. */
private fun DrawScope.powerOut(f: FxFrame) {
    val pal = f.pal
    val u = f.unit
    val ms = f.ms
    val flicker = when {
        ms < 450f -> 0f
        ms < 540f -> 0.4f
        ms < 620f -> 0.1f
        ms < 700f -> 0.45f
        ms < 2300f -> 0.38f
        else -> 0.38f * (1f - f.span(2300f, 2800f))
    }
    dim(flicker)
    val gap = ease(f.span(250f, 500f))
    box(f.ax, f.ay, u) { plug(gap, pal.ink, Color(0xFFE9F3FF)) }
    sparks(f.ax + u * 0.02f, f.ay, u * 0.8f, f.span(430f, 1000f), pal.spark)
    val dead = listOf(ReefGlyphs.powerhead, ReefGlyphs.flame, ReefGlyphs.sun)
    dead.forEachIndexed { i, g ->
        val pop = easeOutBack(f.span(800f + i * 120f, 1100f + i * 120f))
        if (pop > 0f) {
            box(f.ax + (i - 1) * u * 0.62f, f.ay + u * 0.85f, u * 0.5f * pop) {
                drawCircle(Color.White, 22f, o(24f, 24f)); drawCircle(pal.ink, 22f, o(24f, 24f), style = stroke(3f))
                glyphAt(g, 9f, 9f, 30f, pal.ink, pal.orange)
                drawLine(pal.danger, o(10f, 38f), o(38f, 10f), 4.5f, StrokeCap.Round)
            }
        }
    }
}

/** Trouble pulse with a badge: a germ for disease, or [TankFx.glyph]. */
private fun DrawScope.alert(fx: TankFx, f: FxFrame) {
    val pal = f.pal
    val u = f.unit
    val tint = fx.tint.orElse(pal.danger)
    pulse(f.ax, f.ay, u * 1.3f, f.span(0f, f.dur), tint)
    val pop = easeOutBack(f.span(100f, 450f))
    val bob = sin(f.ms / 160f) * 3f * density
    box(f.ax, f.ay + bob, u * 0.8f * pop) {
        val g = fx.glyph
        if (g == null) germ(pal.green, pal.ink, f.ms / 1000f)
        else { drawCircle(Color.White, 21f, o(24f, 24f)); drawCircle(pal.ink, 21f, o(24f, 24f), style = stroke(3f)); glyphAt(g, 9f, 9f, 30f, pal.ink, tint) }
    }
}

private fun DrawScope.pestAdd(fx: TankFx, f: FxFrame) {
    val pal = f.pal
    val u = f.unit
    pulse(f.ax, f.ay, u, f.span(200f, f.dur), pal.danger)
    val grow = easeOutBack(f.span(0f, 600f))
    val sway = sin(f.ms / 150f) * 8f
    box(f.ax, f.ay - u * 0.15f, u * 0.75f * grow, deg = sway) { glyphAt(fx.glyph ?: ReefGlyphs.pestAnemone, 0f, 0f, 48f, Color(0xFFD9C79A), pal.pink) }
}

private fun DrawScope.pestRemove(fx: TankFx, f: FxFrame) {
    val pal = f.pal
    val u = f.unit
    val shrink = 1f - ease(f.span(900f, 1400f))
    if (shrink > 0f) box(f.ax, f.ay - u * 0.15f, u * 0.7f * shrink) { glyphAt(fx.glyph ?: ReefGlyphs.pestAnemone, 0f, 0f, 48f, Color(0xFFD9C79A), pal.pink) }
    if (f.ms > 1300f) twinkles(f.ax, f.ay - u * 0.15f, u * 0.7f, f.span(1300f, 2000f), Color.White)
    val inn = ease(f.span(0f, 500f)) * (1f - ease(f.span(1500f, 1900f)))
    val push = ease(f.span(550f, 1100f))
    box(f.ax + u * 0.15f, f.ay - u * 0.85f - (1f - inn) * u * 0.6f, u * 0.9f, deg = 25f) { syringe(push, pal.cyan, pal.ink, pal.glass.copy(alpha = 0.9f)) }
}

/** New animal: the bag drifts down, floats and bobs, the knot opens and the animal swims out with bubbles. */
private fun DrawScope.bagFx(fx: TankFx, f: FxFrame) {
    val pal = f.pal
    val u = f.unit
    val glyph = fx.glyph ?: ReefGlyphs.fish
    val tint = fx.tint.orElse(pal.orange)
    val down = ease(f.span(0f, 650f))
    val bob = if (f.ms > 650f) sin((f.ms - 650f) / 180f) * 3f * density else 0f
    val open = ease(f.span(1500f, 1800f))
    val away = ease(f.span(2100f, 2700f))
    val bx = f.ax; val by = f.ay - u * 0.42f - (1f - down) * u * 0.9f + bob - away * u * 0.9f
    surfaceRipples(f.ax, f.ay, u, f.span(600f, 1500f), f.pal.water)
    // the animal swims out of the bag and down into the tank
    val swim = f.span(1650f, 2700f)
    if (swim > 0f && swim < 1f) {
        val n = fx.amount.toInt().coerceIn(1, 4)
        for (k in 0 until n) {
            val q = (swim - k * 0.08f).coerceIn(0f, 1f)
            val x = bx + (k - (n - 1) / 2f) * u * 0.25f + sin(q * 7f + k) * u * 0.08f + q * u * 0.4f
            val y = f.ay + q * u * 0.9f
            box(x, y, u * 0.42f, deg = 15f * sin(q * 10f)) { glyphAt(glyph, 0f, 0f, 48f, pal.ink, tint) }
        }
        bubbles(bx, f.ay + u * 0.4f, u * 0.5f, swim, pal.bubble, seed = 5)
    }
    box(bx, by, u * 0.95f, deg = sin(f.ms / 300f) * 5f) { fishBag(glyph, open, pal.water, pal.ink, pal.glass.copy(alpha = 0.6f), tint) }
}

private fun DrawScope.netFx(f: FxFrame) {
    val pal = f.pal
    val u = f.unit
    val dip = sin(f.span(100f, 1700f) * PI.toFloat())
    box(f.ax, f.ay - u * 0.9f + dip * u * 0.8f, u) { net(pal.ink, pal.cyan) }
    surfaceRipples(f.ax, f.ay, u, f.span(500f, 1300f), pal.water)
    bubbles(f.ax, f.ay + u * 0.3f, u * 0.4f, f.span(700f, 1500f), pal.bubble, seed = 9)
}

/** Equipment switched: the device badge, a power ring filling (on) or emptying (off), bubbles or a fade. */
private fun DrawScope.deviceFx(fx: TankFx, f: FxFrame) {
    val pal = f.pal
    val u = f.unit
    val on = fx.kind == FxKind.DEVICE_ON
    val pop = easeOutBack(f.span(0f, 350f))
    val ring = if (on) ease(f.span(150f, 800f)) else 1f - ease(f.span(150f, 800f))
    val tint = fx.tint.orElse(if (on) pal.green else pal.danger)
    box(f.ax, f.ay, u * 0.95f * pop) {
        drawCircle(if (on) Color.White else Color(0xFFC9CFE0), 20f, o(24f, 24f))
        drawArc(tint, -90f, 360f * ring, false, o(1.5f, 1.5f), Size(45f, 45f), style = stroke(4.5f))
        drawCircle(pal.ink, 20f, o(24f, 24f), style = stroke(3f))
        glyphAt(fx.glyph ?: ReefGlyphs.skimmer, 10f, 10f, 28f, if (on) pal.ink else Color(0xFF6A7090), tint)
    }
    if (on) bubbles(f.ax, f.ay - u * 0.3f, u * 0.6f, f.span(300f, 1500f), pal.bubble, seed = 31)
    else { val q = f.span(500f, 1400f); if (q > 0f && q < 1f) drawCircle(Color(0xFFB5BCD0).copy(alpha = 0.5f * (1f - q)), u * 0.2f * (1f + q), o(f.ax, f.ay - u * 0.5f - q * u * 0.4f)) }
}

/** Maintenance swap: the dirty old part slides out brown, the clean new one slides in with sparkles. */
private fun DrawScope.maintainFx(fx: TankFx, f: FxFrame) {
    val pal = f.pal
    val u = f.unit
    val g = fx.glyph ?: ReefGlyphs.sock
    val out = ease(f.span(250f, 950f))
    val inn = easeOutBack(f.span(900f, 1350f))
    if (out < 1f) {
        box(f.ax + out * u * 0.9f, f.ay + out * u * 0.4f, u * 0.75f, deg = out * 25f) {
            drawCircle(Color(0xFFD8C2A0), 20f, o(24f, 24f)); drawCircle(pal.ink, 20f, o(24f, 24f), style = stroke(3f))
            glyphAt(g, 10f, 10f, 28f, Color(0xFF6B4F2A), Color(0xFF9C7A4A))
            for (k in 0 until 5) drawCircle(Color(0xFF6B4F2A), 1.6f, o(14f + k * 5f, 16f + (k % 3) * 8f))
        }
    }
    if (inn > 0f) {
        box(f.ax, f.ay, u * 0.8f * inn) {
            drawCircle(Color.White, 20f, o(24f, 24f)); drawCircle(pal.ink, 20f, o(24f, 24f), style = stroke(3f))
            glyphAt(g, 10f, 10f, 28f, pal.ink, fx.tint.orElse(pal.cyan))
        }
        twinkles(f.ax, f.ay, u * 0.9f, f.span(1100f, 2100f), pal.yellow)
    }
}

/** Trim the algae garden: scissors snip, green bits fly. */
private fun DrawScope.snipFx(fx: TankFx, f: FxFrame) {
    val pal = f.pal
    val u = f.unit
    box(f.ax, f.ay + u * 0.15f, u * 0.75f) {
        drawCircle(Color.White, 20f, o(24f, 24f)); drawCircle(pal.ink, 20f, o(24f, 24f), style = stroke(3f))
        glyphAt(fx.glyph ?: ReefGlyphs.refugium, 10f, 10f, 28f, pal.ink, pal.green)
    }
    var open = 0.5f
    if (f.ms in 300f..1300f) open = 0.5f + 0.5f * cos(((f.ms - 300f) / 250f) * 2f * PI.toFloat())
    box(f.ax + u * 0.35f, f.ay - u * 0.35f, u * 0.8f, deg = -35f) { scissors(open, pal.ink, Color(0xFFE9F3FF)) }
    for (k in 0 until 8) {
        val q = ((f.ms - 400f - k * 100f) / 700f).coerceIn(0f, 1f)
        if (q <= 0f || q >= 1f) continue
        val a = hash(k + 70) * 2f * PI.toFloat()
        drawOval(pal.green.copy(alpha = 1f - q), o(f.ax + cos(a) * u * 0.6f * q, f.ay + sin(a) * u * 0.4f * q + q * q * u * 0.3f), Size(6f * density, 3f * density))
    }
}

private fun DrawScope.shellsFx(f: FxFrame) {
    val pal = f.pal
    val u = f.unit
    for (k in 0 until 3) {
        val q = f.span(k * 180f, 700f + k * 180f)
        if (q <= 0f) continue
        val bounce = if (q >= 1f) kotlin.math.abs(sin((f.ms - 700f - k * 180f) / 120f)) * 4f * density * (1f - f.span(700f + k * 180f, 1400f + k * 180f)) else 0f
        val x = f.ax + (k - 1) * u * 0.45f
        val y = f.ay - u * 1.2f * (1f - q * q) - bounce
        box(x, y, u * 0.45f, deg = (1f - q) * 120f) { glyphAt(ReefGlyphs.shell, 0f, 0f, 48f, pal.ink, pal.pink) }
    }
    if (f.ms > 700f) for (k in 0 until 3) { val q = f.span(700f + k * 180f, 1300f + k * 180f); if (q in 0.01f..0.99f) drawCircle(Color(0xFFE6DFD0).copy(alpha = 0.6f * (1f - q)), u * 0.15f * (0.5f + q), o(f.ax + (k - 1) * u * 0.45f, f.ay + u * 0.12f)) }
}

private fun DrawScope.rocksFx(f: FxFrame) {
    val pal = f.pal
    val u = f.unit
    val shake = sin(f.ms / 25f) * 4f * density * (1f - f.span(200f, 1200f))
    for (k in 0 until 8) {
        val q = ((f.ms - 150f - k * 60f) / 900f).coerceIn(0f, 1f)
        if (q <= 0f || q >= 1f) continue
        val a = (k / 8f) * 2f * PI.toFloat()
        drawCircle(Color(0xFFE6DFD0).copy(alpha = 0.55f * (1f - q)), u * 0.12f * (0.5f + q), o(f.ax + cos(a) * u * 0.55f * q, f.ay + u * 0.3f + sin(a) * u * 0.2f * q))
    }
    box(f.ax + shake, f.ay, u * 0.9f) { glyphAt(ReefGlyphs.rocks, 0f, 0f, 48f, pal.ink, pal.orange) }
}

private fun DrawScope.heartsFx(f: FxFrame) {
    val pal = f.pal
    val u = f.unit
    for (k in 0 until 6) {
        val q = ((f.ms - k * 120f) / 1300f).coerceIn(0f, 1f)
        if (q <= 0f || q >= 1f) continue
        val x = f.ax + (hash(k + 3) - 0.5f) * u * 1.6f + sin(q * 6f + k) * u * 0.08f
        val y = f.ay + u * 0.3f - q * u * 1.1f
        box(x, y, u * (0.3f + 0.15f * hash(k)), sx = 1f, sy = 1f) { glyphAt(ReefGlyphs.heart, 0f, 0f, 48f, pal.pink, Color.White) }
    }
}

/** Knock on the glass: a knuckle taps twice and rings ripple out on the pane. */
private fun DrawScope.tapGlassFx(f: FxFrame) {
    val pal = f.pal
    val u = f.unit
    for (k in 0 until 3) {
        val q = f.span(80f + k * 140f, 700f + k * 140f)
        if (q <= 0f || q >= 1f) continue
        drawCircle(Color.White.copy(alpha = 0.7f * (1f - q)), u * 0.15f + u * 0.6f * q, o(f.ax, f.ay), style = Stroke(2.5f * density))
    }
    val knock = if (f.ms < 360f) kotlin.math.abs(sin(f.ms / 360f * 2f * PI.toFloat())) else 0f
    box(f.ax + u * 0.2f, f.ay - u * 0.2f - knock * u * 0.12f, u * 0.7f, deg = -20f) { glyphAt(ReefGlyphs.touch, 0f, 0f, 48f, Color.White, pal.orange) }
}

private fun DrawScope.growFx(fx: TankFx, f: FxFrame) {
    val pal = f.pal
    val u = f.unit
    val q = ease(f.span(0f, 900f))
    val bigger = fx.amount >= 1f
    for (k in 0 until 4) {
        val a = (k * 90f + 45f) * PI.toFloat() / 180f
        val r = u * (if (bigger) 0.25f + 0.35f * q else 0.6f - 0.35f * q)
        val x = f.ax + cos(a) * r; val y = f.ay + sin(a) * r
        val dir = if (bigger) 1f else -1f
        drawLine(pal.yellow, o(x, y), o(x + cos(a) * dir * u * 0.15f, y + sin(a) * dir * u * 0.15f), 4f * density, StrokeCap.Round)
    }
    twinkles(f.ax, f.ay, u * 0.8f, f.span(200f, 1300f), Color.White)
}

/** Fast-forward chevrons sweep across the top of the tank. */
private fun DrawScope.speedFx(fx: TankFx, f: FxFrame) {
    val pal = f.pal
    val u = f.unit
    val q = ease(f.span(0f, f.dur))
    val n = (fx.amount.toInt()).coerceIn(1, 4)
    for (k in 0 until n + 1) {
        val x = size.width * (0.15f + 0.7f * q) + (k - n / 2f) * u * 0.32f
        val y = f.ay
        val a = sin(q * PI.toFloat())
        val p = Path().apply { moveTo(x - u * 0.12f, y - u * 0.2f); lineTo(x + u * 0.12f, y); lineTo(x - u * 0.12f, y + u * 0.2f) }
        drawPath(p, pal.ink.copy(alpha = a), style = stroke(9f * density))
        drawPath(p, pal.purple.copy(alpha = a), style = stroke(5f * density))
    }
}

/** Reset: a big swoosh of water sweeps across the whole view, trailing sparkles. */
private fun DrawScope.resetFx(f: FxFrame) {
    val pal = f.pal
    val w = size.width; val h = size.height
    val q = ease(f.span(0f, 1100f))
    val x = -w * 0.4f + w * 1.8f * q
    for (k in 0 until 3) {
        val off = k * w * 0.08f
        val p = Path().apply {
            moveTo(x - off - w * 0.35f, h * 0.15f)
            cubicTo(x - off, h * 0.2f, x - off + w * 0.1f, h * 0.55f, x - off - w * 0.15f, h * 0.9f)
        }
        val col = listOf(pal.cyan, Color.White, pal.water)[k]
        drawPath(p, col.copy(alpha = 0.55f - k * 0.12f), style = stroke((26f - k * 6f) * density))
    }
    for (k in 0 until 10) {
        val sx = x - w * 0.3f - hash(k) * w * 0.3f
        val sy = h * (0.15f + 0.7f * hash(k + 9))
        val s = (3f + 4f * hash(k + 4)) * density
        drawLine(Color.White.copy(alpha = 0.8f * (1f - q)), o(sx - s, sy), o(sx + s, sy), 2f * density, StrokeCap.Round)
        drawLine(Color.White.copy(alpha = 0.8f * (1f - q)), o(sx, sy - s), o(sx, sy + s), 2f * density, StrokeCap.Round)
    }
}

/** A glyph pops in a white disc with twinkles (sliders and switches without their own picture). */
private fun DrawScope.sparkleFx(fx: TankFx, f: FxFrame) {
    val pal = f.pal
    val u = f.unit
    val pop = easeOutBack(f.span(0f, 350f))
    val tint = fx.tint.orElse(pal.cyan)
    box(f.ax, f.ay, u * 0.75f * pop) {
        drawCircle(Color.White, 21f, o(24f, 24f)); drawCircle(pal.ink, 21f, o(24f, 24f), style = stroke(3f))
        glyphAt(fx.glyph ?: ReefGlyphs.sparkleTip, 9f, 9f, 30f, pal.ink, tint)
    }
    twinkles(f.ax, f.ay, u * 0.8f, f.span(150f, 1200f), tint)
}

/** A clock with spinning hands (time-lapse started). */
private fun DrawScope.timelapseFx(f: FxFrame) {
    val pal = f.pal
    val u = f.unit
    val pop = easeOutBack(f.span(0f, 350f))
    val spin = f.ms / 1000f * 720f
    box(f.ax, f.ay, u * 0.9f * pop) {
        drawCircle(Color.White, 20f, o(24f, 24f)); drawCircle(pal.ink, 20f, o(24f, 24f), style = stroke(3f))
        val a1 = spin * PI.toFloat() / 180f; val a2 = spin / 12f * PI.toFloat() / 180f
        drawLine(pal.ink, o(24f, 24f), o(24f + sin(a1) * 14f, 24f - cos(a1) * 14f), 2.6f, StrokeCap.Round)
        drawLine(pal.purple, o(24f, 24f), o(24f + sin(a2) * 9f, 24f - cos(a2) * 9f), 3.4f, StrokeCap.Round)
        drawCircle(pal.ink, 2.4f, o(24f, 24f))
    }
}
