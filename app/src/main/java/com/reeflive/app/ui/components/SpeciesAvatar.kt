package com.reeflive.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.app.ui.theme.parseHexColor
import com.reeflive.core.species.SpeciesKind
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Silhouette family for the procedural species art. */
enum class AvatarShape { FISH, EEL, SHRIMP, STAR, SHELL, CRAB, DUSTER, CORAL, ALGAE }

fun avatarShapeFor(speciesId: String, kind: SpeciesKind?): AvatarShape {
    val id = speciesId.lowercase()
    return when {
        id.startsWith("echidna") -> AvatarShape.EEL
        id.startsWith("stenopus") || id.startsWith("lysmata") -> AvatarShape.SHRIMP
        id.startsWith("ophiocoma") || id.startsWith("asterina") -> AvatarShape.STAR
        id.startsWith("nassarius") || id.startsWith("lithopoma") -> AvatarShape.SHELL
        id.startsWith("clibanarius") || id.startsWith("paguristes") -> AvatarShape.CRAB
        id.startsWith("sabellastarte") -> AvatarShape.DUSTER
        kind == SpeciesKind.CORAL -> AvatarShape.CORAL
        kind == SpeciesKind.ALGAE -> AvatarShape.ALGAE
        kind == SpeciesKind.INVERT -> AvatarShape.SHELL
        else -> AvatarShape.FISH
    }
}

/**
 * Procedural species art (stands in for `species_*.webp` illustrations): a glass circle with a deep-water
 * gradient and a simple silhouette painted in the species' sheet colors. [ring] is the health/status ring.
 */
@Composable
fun SpeciesAvatar(
    speciesId: String,
    kind: SpeciesKind?,
    colors: Map<String, String>,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    ring: Color? = null,
    contentDescription: String? = null,
) {
    val c = ReefTheme.colors
    val primary = parseHexColor(colors["primary"]) ?: c.primaryBright
    val secondary = parseHexColor(colors["secondary"]) ?: c.coral
    val accent = parseHexColor(colors["accent"]) ?: secondary
    val shape = remember(speciesId, kind) { avatarShapeFor(speciesId, kind) }
    val bg = remember(c, primary) {
        Brush.radialGradient(listOf(c.reef.copy(alpha = 0.95f), c.bg.copy(alpha = 0.95f)))
    }
    var m = modifier.size(size).clip(CircleShape).background(bg)
    m = if (ring != null) m.border(2.dp, ring, CircleShape) else m.border(1.dp, c.glassBorder, CircleShape)
    if (contentDescription != null) m = m.semantics { this.contentDescription = contentDescription }
    Box(m) {
        Canvas(Modifier.fillMaxSize()) {
            // soft light from above
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.14f), Color.Transparent), center = Offset(this.size.width * 0.35f, this.size.height * 0.2f), radius = this.size.minDimension * 0.7f))
            when (shape) {
                AvatarShape.FISH -> drawFish(primary, secondary, accent)
                AvatarShape.EEL -> drawEel(primary, secondary, accent)
                AvatarShape.SHRIMP -> drawShrimp(primary, secondary)
                AvatarShape.STAR -> drawStar(primary, secondary)
                AvatarShape.SHELL -> drawShell(primary, secondary)
                AvatarShape.CRAB -> drawCrab(primary, secondary, accent)
                AvatarShape.DUSTER -> drawDuster(primary, secondary)
                AvatarShape.CORAL -> drawCoral(primary, secondary, accent)
                AvatarShape.ALGAE -> drawAlgae(primary, secondary)
            }
        }
    }
}

private fun DrawScope.drawFish(body: Color, band: Color, eye: Color) {
    val w = size.width
    val h = size.height
    val cx = w * 0.46f
    val cy = h * 0.52f
    val bw = w * 0.52f
    val bh = h * 0.32f
    val tail = Path().apply {
        moveTo(cx + bw * 0.42f, cy)
        lineTo(cx + bw * 0.78f, cy - bh * 0.55f)
        lineTo(cx + bw * 0.70f, cy)
        lineTo(cx + bw * 0.78f, cy + bh * 0.55f)
        close()
    }
    drawPath(tail, body.copy(alpha = 0.9f))
    val fin = Path().apply {
        moveTo(cx - bw * 0.1f, cy - bh * 0.45f)
        quadraticTo(cx + bw * 0.1f, cy - bh * 0.95f, cx + bw * 0.3f, cy - bh * 0.35f)
        close()
    }
    drawPath(fin, body.copy(alpha = 0.85f))
    drawOval(body, Offset(cx - bw / 2, cy - bh / 2), Size(bw, bh))
    // a vertical band
    drawOval(band.copy(alpha = 0.9f), Offset(cx - bw * 0.12f, cy - bh * 0.48f), Size(bw * 0.12f, bh * 0.96f))
    drawCircle(Color.White.copy(alpha = 0.9f), radius = bh * 0.13f, center = Offset(cx - bw * 0.3f, cy - bh * 0.1f))
    drawCircle(eye, radius = bh * 0.07f, center = Offset(cx - bw * 0.3f, cy - bh * 0.1f))
}

private fun DrawScope.drawEel(body: Color, spots: Color, dots: Color) {
    val w = size.width
    val h = size.height
    val p = Path().apply {
        moveTo(w * 0.18f, h * 0.62f)
        cubicTo(w * 0.35f, h * 0.30f, w * 0.55f, h * 0.85f, w * 0.82f, h * 0.42f)
    }
    drawPath(p, body, style = Stroke(width = h * 0.16f, cap = StrokeCap.Round))
    for (i in 0 until 5) {
        val t = 0.15f + i * 0.17f
        drawCircle(spots.copy(alpha = 0.85f), h * 0.045f, Offset(w * (0.2f + t * 0.6f), h * (0.55f - 0.05f * sin(t * 6f))))
        drawCircle(dots, h * 0.02f, Offset(w * (0.2f + t * 0.6f), h * (0.55f - 0.05f * sin(t * 6f))))
    }
    drawCircle(Color.White, h * 0.03f, Offset(w * 0.79f, h * 0.40f))
}

private fun DrawScope.drawShrimp(body: Color, band: Color) {
    val w = size.width
    val h = size.height
    val p = Path().apply {
        moveTo(w * 0.25f, h * 0.70f)
        quadraticTo(w * 0.30f, h * 0.30f, w * 0.68f, h * 0.38f)
    }
    drawPath(p, body, style = Stroke(width = h * 0.15f, cap = StrokeCap.Round))
    drawPath(p, band.copy(alpha = 0.85f), style = Stroke(width = h * 0.05f, cap = StrokeCap.Round))
    for (k in 0..1) {
        val a = Path().apply {
            moveTo(w * 0.68f, h * 0.38f)
            quadraticTo(w * 0.85f, h * (0.15f + k * 0.08f), w * (0.88f - k * 0.1f), h * (0.08f + k * 0.04f))
        }
        drawPath(a, Color.White.copy(alpha = 0.8f), style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawStar(body: Color, center: Color) {
    val cx = size.width / 2
    val cy = size.height / 2
    val r = size.minDimension * 0.32f
    for (i in 0 until 5) {
        val a = (i * 72f - 90f) * (PI.toFloat() / 180f)
        val tip = Offset(cx + r * cos(a), cy + r * sin(a))
        drawLine(body, Offset(cx, cy), tip, strokeWidth = r * 0.28f, cap = StrokeCap.Round)
    }
    drawCircle(center, r * 0.26f, Offset(cx, cy))
}

private fun DrawScope.drawShell(body: Color, line: Color) {
    val cx = size.width * 0.5f
    val cy = size.height * 0.54f
    val r = size.minDimension * 0.28f
    rotate(-25f, Offset(cx, cy)) {
        drawOval(body, Offset(cx - r, cy - r * 0.8f), Size(r * 2f, r * 1.6f))
        val p = Path()
        var rr = r * 0.75f
        var ang = 0f
        p.moveTo(cx + rr, cy)
        while (rr > r * 0.08f) {
            ang += 0.35f
            rr *= 0.95f
            p.lineTo(cx + rr * cos(ang), cy + rr * 0.8f * sin(ang))
        }
        drawPath(p, line.copy(alpha = 0.8f), style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawCrab(shell: Color, body: Color, legs: Color) {
    val w = size.width
    val h = size.height
    for (i in 0..2) drawLine(legs, Offset(w * 0.40f, h * (0.62f + i * 0.04f)), Offset(w * (0.22f - i * 0.02f), h * (0.70f + i * 0.06f)), strokeWidth = h * 0.04f, cap = StrokeCap.Round)
    drawCircle(body, h * 0.09f, Offset(w * 0.40f, h * 0.60f))
    drawShell(shell, body)
}

private fun DrawScope.drawDuster(tube: Color, crown: Color) {
    val w = size.width
    val h = size.height
    val base = Offset(w * 0.5f, h * 0.88f)
    val top = Offset(w * 0.5f, h * 0.48f)
    drawLine(tube, base, top, strokeWidth = h * 0.08f, cap = StrokeCap.Round)
    for (i in 0 until 9) {
        val a = (-160f + i * 17.5f) * (PI.toFloat() / 180f)
        val r = h * 0.30f
        drawLine(crown.copy(alpha = 0.85f), top, Offset(top.x + r * cos(a), top.y + r * sin(a)), strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round)
        drawLine(tube.copy(alpha = 0.7f), Offset(top.x + r * 0.5f * cos(a), top.y + r * 0.5f * sin(a)), Offset(top.x + r * 0.7f * cos(a), top.y + r * 0.7f * sin(a)), strokeWidth = 1.6.dp.toPx())
    }
}

private fun DrawScope.drawCoral(base: Color, polyp: Color, tip: Color) {
    val w = size.width
    val h = size.height
    drawOval(base.copy(alpha = 0.9f), Offset(w * 0.18f, h * 0.58f), Size(w * 0.64f, h * 0.26f))
    val pts = listOf(0.28f to 0.52f, 0.42f to 0.42f, 0.58f to 0.44f, 0.72f to 0.54f, 0.50f to 0.60f, 0.35f to 0.62f, 0.65f to 0.64f)
    for ((x, y) in pts) {
        drawLine(base, Offset(w * x, h * 0.70f), Offset(w * x, h * y), strokeWidth = w * 0.06f, cap = StrokeCap.Round)
        drawCircle(polyp, w * 0.055f, Offset(w * x, h * y))
        drawCircle(tip.copy(alpha = 0.9f), w * 0.02f, Offset(w * x, h * y))
    }
}

private fun DrawScope.drawAlgae(blade: Color, dark: Color) {
    val w = size.width
    val h = size.height
    for (i in 0 until 5) {
        val x = w * (0.30f + i * 0.10f)
        val p = Path().apply {
            moveTo(x, h * 0.86f)
            quadraticTo(x + w * 0.12f * (if (i % 2 == 0) 1 else -1), h * 0.55f, x + w * 0.04f, h * (0.25f + 0.05f * i))
        }
        drawPath(p, if (i % 2 == 0) blade else dark, style = Stroke(width = w * 0.05f, cap = StrokeCap.Round))
    }
}
