package com.reeflive.app.ui.live

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.reeflive.app.ui.components.GlassIconButton
import com.reeflive.app.ui.components.ReefGlyphs
import com.reeflive.app.ui.components.rememberHaptics
import com.reeflive.app.ui.components.tapSplash
import com.reeflive.app.ui.theme.FoodPalette
import com.reeflive.app.ui.theme.ReefLiveTheme
import com.reeflive.app.ui.theme.ReefMotion
import com.reeflive.app.ui.theme.ReefShapes
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.core.sim.FoodGroup
import com.reeflive.core.sim.FoodKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/*
 * Food pictures (UI_STYLE §5.2): one Compose-drawn illustration per FoodKind on a 48-unit box, in the FoodPalette
 * colours that the renderer's particle tints are matched to, plus the food picker that drops them into the tank.
 */

private const val BOX = 48f

/** Draws [kind] inside a 48-unit box scaled to this scope (letterboxed when not square). */
fun DrawScope.drawFood(kind: FoodKind) {
    val s = size.minDimension / BOX
    val dx = (size.width - BOX * s) / 2f
    val dy = (size.height - BOX * s) / 2f
    withTransform({ translate(dx, dy); scale(s, s, Offset.Zero) }) {
        when (kind) {
            FoodKind.FLAKE -> flakes()
            FoodKind.MYSIS -> mysis()
            FoodKind.PELLET -> pellets(FoodPalette.granule, FoodPalette.granuleShade, floating = false)
            FoodKind.NORI -> nori()
            FoodKind.PODS -> pods()
            FoodKind.PRAWN -> prawn()
            FoodKind.PLANKTON -> plankton()
            FoodKind.BRINE -> brine()
            FoodKind.KRILL -> krill()
            FoodKind.FLOATING_PELLET -> pellets(FoodPalette.pelletLight, FoodPalette.flakeTan, floating = true)
            FoodKind.ALGAE_WAFER -> wafer()
            FoodKind.CORAL_FOOD -> powder()
            FoodKind.CYCLOPS -> cyclops()
        }
    }
}

private fun quad(a: Offset, b: Offset, c: Offset, d: Offset) = Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close() }
private fun o(x: Float, y: Float) = Offset(x, y)
private fun round(w: Float) = Stroke(w, cap = StrokeCap.Round)

private fun DrawScope.flake(p: Path, color: Color) {
    drawPath(p, color)
    drawPath(p, FoodPalette.shade, style = Stroke(1.2f))
}

private fun DrawScope.flakes() {
    flake(quad(o(9f, 13f), o(22f, 9f), o(24f, 20f), o(13f, 24f)), FoodPalette.flakeOrange)
    flake(quad(o(26f, 11f), o(39f, 15f), o(35f, 26f), o(24f, 22f)), FoodPalette.flakeRed)
    flake(quad(o(13f, 28f), o(24f, 26f), o(27f, 36f), o(12f, 39f)), FoodPalette.flakeGreen)
    flake(quad(o(29f, 29f), o(41f, 28f), o(39f, 40f), o(30f, 38f)), FoodPalette.flakeTan)
    drawCircle(FoodPalette.flakeOrange, 1.4f, o(8f, 33f))
    drawCircle(FoodPalette.flakeRed, 1.1f, o(42f, 20f))
    drawCircle(FoodPalette.flakeTan, 1.2f, o(22f, 43f))
}

private fun DrawScope.shrimpBody(color: Color, deep: Color, centre: Offset, radius: Float, start: Float, sweep: Float, width: Float) {
    val tl = Offset(centre.x - radius, centre.y - radius)
    val sz = Size(radius * 2, radius * 2)
    drawArc(color, start, sweep, false, tl, sz, style = round(width))
    // segment lines across the back
    for (k in 1..4) {
        val a = Math.toRadians((start + sweep * k / 5f).toDouble())
        val cx = centre.x + Math.cos(a).toFloat() * radius
        val cy = centre.y + Math.sin(a).toFloat() * radius
        val nx = Math.cos(a).toFloat(); val ny = Math.sin(a).toFloat()
        drawLine(deep, Offset(cx - nx * width * 0.45f, cy - ny * width * 0.45f), Offset(cx + nx * width * 0.45f, cy + ny * width * 0.45f), 1.1f, StrokeCap.Round)
    }
}

private fun DrawScope.mysis() {
    val c = o(24f, 25f)
    shrimpBody(FoodPalette.mysisPink, FoodPalette.mysisDeep, c, 11f, 200f, 200f, 7f)
    // head at the right end of the arc, tail fan at the left
    drawCircle(FoodPalette.mysisDeep, 4.6f, o(34f, 21f))
    drawCircle(FoodPalette.eye, 1.3f, o(35.5f, 19.5f))
    drawCircle(FoodPalette.glint, 0.45f, o(35.9f, 19.1f))
    drawLine(FoodPalette.mysisDeep, o(36f, 17.5f), o(43f, 9f), 1.1f, StrokeCap.Round)
    drawLine(FoodPalette.mysisDeep, o(37.5f, 19f), o(45f, 15f), 1.1f, StrokeCap.Round)
    drawPath(Path().apply { moveTo(14f, 28f); lineTo(7f, 24f); lineTo(9f, 31f); close() }, FoodPalette.mysisPink)
    for (k in 0 until 5) {
        val x = 17f + k * 3.6f
        drawLine(FoodPalette.mysisDeep, o(x, 33f), o(x - 1.2f, 37.5f), 1.1f, StrokeCap.Round)
    }
}

private fun DrawScope.brine() {
    // a plump orange shrimp swimming right, feathery legs below, two stalked eyes
    drawLine(FoodPalette.brineOrange, o(11f, 25f), o(34f, 22f), 7f, StrokeCap.Round)
    drawLine(FoodPalette.brineDeep, o(12f, 23f), o(33f, 20.5f), 1.2f, StrokeCap.Round)
    for (k in 0 until 8) {
        val x = 13f + k * 2.8f
        drawLine(FoodPalette.brineDeep, o(x, 27.5f), o(x - 0.8f, 32f), 1f, StrokeCap.Round)
    }
    drawLine(FoodPalette.brineOrange, o(11f, 25f), o(5f, 20f), 2.2f, StrokeCap.Round)
    drawLine(FoodPalette.brineOrange, o(11f, 25f), o(5f, 30f), 2.2f, StrokeCap.Round)
    drawLine(FoodPalette.brineDeep, o(35f, 21f), o(38.5f, 17f), 1.2f, StrokeCap.Round)
    drawLine(FoodPalette.brineDeep, o(35f, 23f), o(39f, 26f), 1.2f, StrokeCap.Round)
    drawCircle(FoodPalette.eye, 1.6f, o(39f, 16.5f))
    drawCircle(FoodPalette.eye, 1.6f, o(39.5f, 26.5f))
}

private fun DrawScope.krill() {
    val c = o(23f, 30f)
    shrimpBody(FoodPalette.krillRed.copy(alpha = 0.9f), FoodPalette.krillPale, c, 13f, 215f, 150f, 6.5f)
    drawCircle(FoodPalette.krillRed, 4.4f, o(35f, 24f))
    drawCircle(FoodPalette.eye, 2.2f, o(36f, 22.5f))
    drawCircle(FoodPalette.glint, 0.7f, o(36.8f, 21.7f))
    drawLine(FoodPalette.krillPale, o(37f, 20f), o(45f, 10f), 1.1f, StrokeCap.Round)
    drawLine(FoodPalette.krillPale, o(38.5f, 22f), o(46f, 19f), 1.1f, StrokeCap.Round)
    drawPath(Path().apply { moveTo(12f, 24f); lineTo(5f, 19f); lineTo(6f, 27f); close() }, FoodPalette.krillRed)
    for (k in 0 until 6) {
        val x = 14f + k * 3.4f
        drawLine(FoodPalette.krillPale, o(x, 35.5f), o(x - 1f, 40f), 1.1f, StrokeCap.Round)
    }
}

private fun DrawScope.pellets(dark: Color, light: Color, floating: Boolean) {
    if (floating) {
        // surface line with ripples; the pellets sit half in the water
        drawLine(FoodPalette.water, o(4f, 27f), o(44f, 27f), 1.6f, StrokeCap.Round)
        listOf(o(14f, 25f), o(25f, 25.5f), o(35f, 24.5f)).forEach { p ->
            drawCircle(dark, 4.6f, p)
            drawCircle(light, 2.2f, Offset(p.x - 1.3f, p.y - 1.5f))
            drawArc(FoodPalette.water.copy(alpha = 0.7f), 20f, 140f, false, Offset(p.x - 7f, p.y - 3f), Size(14f, 10f), style = round(1.2f))
        }
    } else {
        listOf(o(15f, 31f), o(25f, 34f), o(34f, 28f), o(20f, 22f), o(30f, 21f)).forEach { p ->
            drawCircle(dark, 5f, p)
            drawCircle(light, 2.2f, Offset(p.x - 1.5f, p.y - 1.6f))
        }
    }
}

private fun DrawScope.nori() {
    // a sheet on a clip at the glass, with a bite out of one corner
    val sheet = Path().apply {
        moveTo(11f, 9f); lineTo(39f, 9f); lineTo(39f, 31f)
        cubicTo(34f, 30f, 32f, 36f, 33f, 40f); lineTo(11f, 40f); close()
    }
    drawPath(sheet, FoodPalette.noriGreen)
    for (k in 0 until 5) {
        val x = 15f + k * 5f
        drawLine(FoodPalette.noriDark, o(x, 12f), o(x + 1f, 37f), 1.1f, StrokeCap.Round)
    }
    drawPath(sheet, FoodPalette.noriDark, style = Stroke(1.2f))
    // the clip: a rounded block with a sucker ring
    drawRoundRect(FoodPalette.clipBlack, Offset(7f, 5f), Size(14f, 8f), CornerRadius(2.5f))
    drawCircle(FoodPalette.clipBlack, 3.5f, o(7f, 9f))
    drawCircle(FoodPalette.water.copy(alpha = 0.6f), 1.6f, o(7f, 9f), style = Stroke(1f))
}

private fun DrawScope.wafer() {
    withTransform({ rotate(-12f, o(24f, 26f)) }) {
        drawOval(FoodPalette.waferGreen, Offset(9f, 15f), Size(30f, 22f))
        drawOval(FoodPalette.noriDark, Offset(9f, 15f), Size(30f, 22f), style = Stroke(1.4f))
        listOf(o(16f, 22f), o(23f, 19f), o(30f, 23f), o(19f, 30f), o(27f, 31f), o(34f, 29f), o(14f, 28f), o(24f, 25f)).forEach {
            drawCircle(FoodPalette.waferSpeck, 1.1f, it)
        }
        drawLine(FoodPalette.noriDark, o(28f, 17f), o(33f, 21f), 1f, StrokeCap.Round)
    }
}

private fun DrawScope.pods() {
    listOf(Triple(14f, 16f, 0f), Triple(30f, 13f, 30f), Triple(20f, 33f, -20f), Triple(34f, 31f, 10f)).forEach { (x, y, deg) ->
        rotate(deg, o(x, y)) {
            drawOval(FoodPalette.podTan, Offset(x - 2.2f, y - 3.4f), Size(4.4f, 6.8f))
            drawLine(FoodPalette.podTan, o(x - 1f, y - 3.2f), o(x - 4f, y - 7.5f), 1f, StrokeCap.Round)
            drawLine(FoodPalette.podTan, o(x + 1f, y - 3.2f), o(x + 4f, y - 7.5f), 1f, StrokeCap.Round)
            drawLine(FoodPalette.podTan, o(x, y + 3.2f), o(x, y + 6.5f), 1f, StrokeCap.Round)
            drawCircle(FoodPalette.eye, 0.7f, o(x, y - 1.6f))
        }
    }
}

private fun DrawScope.prawn() {
    val chunk = Path().apply {
        moveTo(10f, 30f); cubicTo(12f, 16f, 22f, 10f, 36f, 14f); cubicTo(42f, 18f, 40f, 32f, 32f, 38f); cubicTo(24f, 42f, 12f, 40f, 10f, 30f); close()
    }
    drawPath(chunk, FoodPalette.prawnPink)
    drawPath(chunk, FoodPalette.prawnStripe.copy(alpha = 0.6f), style = Stroke(1.2f))
    for (k in 0 until 3) {
        val x = 17f + k * 7f
        drawArc(FoodPalette.prawnStripe, 200f, 140f, false, Offset(x - 6f, 14f + k * 1.5f), Size(12f, 22f), style = round(2f))
    }
    drawCircle(FoodPalette.glint.copy(alpha = 0.5f), 2.4f, o(18f, 20f))
}

private fun DrawScope.plankton() {
    listOf(Triple(24f, 24f, 13f), Triple(16f, 20f, 8f), Triple(32f, 19f, 7f), Triple(20f, 32f, 7f), Triple(31f, 31f, 6f)).forEach { (x, y, r) ->
        drawCircle(FoodPalette.phyto.copy(alpha = 0.28f), r, o(x, y))
    }
    listOf(o(18f, 23f), o(27f, 18f), o(30f, 28f), o(21f, 30f), o(25f, 25f)).forEach { drawCircle(FoodPalette.phyto, 1.8f, it) }
    listOf(o(14f, 30f), o(34f, 23f), o(23f, 14f)).forEach { drawCircle(FoodPalette.phyto, 1f, it) }
}

private val powderDots = listOf(
    Triple(16f, 22f, 1.4f), Triple(20f, 18f, 1.1f), Triple(24f, 24f, 1.7f), Triple(28f, 19f, 1.2f), Triple(31f, 25f, 1.5f),
    Triple(18f, 28f, 1.2f), Triple(23f, 31f, 1.4f), Triple(29f, 31f, 1.1f), Triple(34f, 29f, 1.0f), Triple(14f, 17f, 0.9f),
    Triple(33f, 16f, 0.9f), Triple(21f, 24f, 0.9f), Triple(27f, 27f, 1.0f), Triple(36f, 22f, 1.1f), Triple(12f, 26f, 1.0f),
    Triple(25f, 14f, 1.0f), Triple(19f, 34f, 0.9f), Triple(31f, 35f, 0.9f),
)

private fun DrawScope.powder() {
    drawCircle(FoodPalette.powderPink.copy(alpha = 0.18f), 15f, o(24f, 25f))
    powderDots.forEachIndexed { i, (x, y, r) -> drawCircle(if (i % 3 == 0) FoodPalette.powderOrange else FoodPalette.powderPink, r, o(x, y)) }
}

private fun DrawScope.cyclops() {
    listOf(Triple(15f, 17f, -20f), Triple(31f, 14f, 15f), Triple(21f, 32f, 25f), Triple(35f, 31f, -10f)).forEach { (x, y, deg) ->
        rotate(deg, o(x, y)) {
            drawLine(FoodPalette.cyclopsRed, o(x, y + 2f), o(x, y + 8f), 1.3f, StrokeCap.Round)
            drawLine(FoodPalette.cyclopsRed, o(x, y + 8f), o(x - 1.8f, y + 10.5f), 1f, StrokeCap.Round)
            drawLine(FoodPalette.cyclopsRed, o(x, y + 8f), o(x + 1.8f, y + 10.5f), 1f, StrokeCap.Round)
            drawCircle(FoodPalette.cyclopsRed, 3f, o(x, y))
            drawCircle(FoodPalette.cyclopsRed.copy(alpha = 0.7f), 1.5f, o(x - 3.2f, y + 3.5f))
            drawCircle(FoodPalette.cyclopsRed.copy(alpha = 0.7f), 1.5f, o(x + 3.2f, y + 3.5f))
            drawCircle(FoodPalette.eye, 0.8f, o(x, y - 1f))
            drawLine(FoodPalette.cyclopsRed, o(x - 1.5f, y - 2.5f), o(x - 4f, y - 5.5f), 0.9f, StrokeCap.Round)
            drawLine(FoodPalette.cyclopsRed, o(x + 1.5f, y - 2.5f), o(x + 4f, y - 5.5f), 0.9f, StrokeCap.Round)
        }
    }
}

// ---------------------------------------------------------------- picture and picker

/**
 * The picture of one food. [drop] 0..1 plays the "dropped in" beat: the food dips, squashes a little as it lands
 * and sends two ripple rings out beneath it.
 */
@Composable
fun FoodPicture(kind: FoodKind, modifier: Modifier = Modifier, size: Dp = 56.dp, drop: Float = 0f) {
    val ripple = ReefTheme.colors.primaryBright
    Canvas(modifier.size(size)) {
        val d = drop.coerceIn(0f, 1f)
        val dip = sin(d * PI).toFloat()
        val u = this.size.minDimension / BOX
        if (d > 0f && d < 1f) {
            val rp = ((d - 0.3f) / 0.7f).coerceIn(0f, 1f)
            if (rp > 0f) {
                val cx = this.size.width / 2; val cy = this.size.height * 0.86f
                for (k in 0 until 2) {
                    val q = (rp - k * 0.25f).coerceIn(0f, 1f)
                    if (q > 0f) drawOval(ripple.copy(alpha = 0.6f * (1f - q)), Offset(cx - 16f * u * q, cy - 5f * u * q), Size(32f * u * q, 10f * u * q), style = Stroke(1.5f * u))
                }
            }
        }
        withTransform({
            translate(0f, dip * 6f * u)
            scale(1f + 0.1f * dip, 1f - 0.14f * dip, Offset(this@Canvas.size.width / 2, this@Canvas.size.height * 0.8f))
        }) {
            drawFood(kind)
        }
    }
}

/**
 * The food picker (UI_STYLE §10): a glass tray of food pictures grouped by shelf. Tap one to drop a typical pinch
 * into the tank: the picture plays its drop beat, the row confirms what went in, and [onFeed] gets the kind.
 * [kinds] narrows the shelf (the Events tab offers four favourites); [compact] drops the header for inline use.
 */
@Composable
fun FoodTray(
    onFeed: (FoodKind) -> Unit,
    modifier: Modifier = Modifier,
    kinds: List<FoodKind> = FoodKind.entries,
    compact: Boolean = false,
    onClose: (() -> Unit)? = null,
) {
    val c = ReefTheme.colors
    val reduce = ReefTheme.reduceMotion
    val haptics = rememberHaptics()
    var flash by remember { mutableStateOf<String?>(null) }
    var flashKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(flashKey) { if (flash != null) { delay(1600); flash = null } }
    val feed: (FoodKind) -> Unit = { k ->
        haptics.confirm()
        onFeed(k)
        flash = "${k.label} dropped in"
        flashKey++
    }
    Column(
        modifier
            .clip(ReefShapes.tile)
            .background(if (compact) Color.Transparent else c.sheet.copy(alpha = c.sheet.alpha * 0.9f))
            .then(if (compact) Modifier else Modifier.border(1.dp, c.glassBorder, ReefShapes.tile))
            .padding(horizontal = if (compact) 0.dp else 12.dp, vertical = if (compact) 0.dp else 10.dp),
    ) {
        if (!compact) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Feed the fish", style = MaterialTheme.typography.titleSmall, color = c.text, modifier = Modifier.semantics { heading() })
                    Text(flash ?: "Tap a food to drop a pinch in", style = MaterialTheme.typography.labelSmall, color = if (flash != null) c.coralSoft else c.textSecondary, maxLines = 1)
                }
                if (onClose != null) GlassIconButton(null, "Close the food tray", onClose, glyph = ReefGlyphs.close)
            }
            Spacer(Modifier.height(6.dp))
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            val groups = FoodGroup.entries.map { g -> g to kinds.filter { it.group == g } }.filter { it.second.isNotEmpty() }
            groups.forEachIndexed { gi, (group, members) ->
                Column {
                    if (groups.size > 1) Text(group.label, style = MaterialTheme.typography.labelSmall, color = c.textTertiary, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        members.forEach { k -> FoodCard(k, compact, reduce) { feed(k) } }
                    }
                }
                if (gi < groups.lastIndex) {
                    Box(Modifier.padding(top = if (groups.size > 1) 18.dp else 4.dp).width(1.dp).height(if (compact) 60.dp else 72.dp).background(c.glassBorder))
                }
            }
        }
        if (compact) {
            AnimatedVisibility(flash != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Text(flash ?: "", style = MaterialTheme.typography.labelSmall, color = c.coralSoft, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun FoodCard(kind: FoodKind, compact: Boolean, reduce: Boolean, onTap: () -> Unit) {
    val c = ReefTheme.colors
    val drop = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    val pic = if (compact) 48.dp else 56.dp
    Column(
        Modifier
            .widthIn(min = pic + 16.dp, max = 84.dp)
            .clip(ReefShapes.tile)
            .background(c.glass)
            .border(1.dp, c.glassBorder, ReefShapes.tile)
            .tapSplash(interaction, c.primaryBright)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = "Drop ${kind.label.lowercase()} in") {
                onTap()
                if (!reduce) scope.launch { drop.snapTo(0f); drop.animateTo(1f, ReefMotion.tween(false, 650)) }
            }
            .semantics { contentDescription = "${kind.label}: ${kind.blurb}" }
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FoodPicture(kind, size = pic, drop = drop.value)
        Text(
            kind.label, style = MaterialTheme.typography.labelSmall, color = c.text, textAlign = TextAlign.Center,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(pic + 12.dp),
        )
    }
}

// ---------------------------------------------------------------- previews

@Preview(name = "Food tray · Abyss", widthDp = 393, heightDp = 190, showBackground = true)
@Composable
private fun FoodTrayPreview() = ReefLiveTheme(darkTheme = true, reduceMotion = true) {
    Box(Modifier.background(ReefTheme.colors.bg).padding(12.dp)) { FoodTray(onFeed = {}, onClose = {}) }
}

@Preview(name = "Food pictures", widthDp = 393, heightDp = 200, showBackground = true)
@Composable
private fun FoodPicturesPreview() = ReefLiveTheme(darkTheme = true, reduceMotion = true) {
    Column(Modifier.background(ReefTheme.colors.bg).padding(8.dp)) {
        FoodKind.entries.chunked(7).forEach { row ->
            Row { row.forEach { FoodPicture(it, size = 52.dp, modifier = Modifier.padding(2.dp)) } }
        }
    }
}
