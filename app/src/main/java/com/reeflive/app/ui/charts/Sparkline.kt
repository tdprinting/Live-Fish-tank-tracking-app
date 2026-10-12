package com.reeflive.app.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.core.params.Range

/**
 * Catmull–Rom (tension 0.5) path through [pts] as cubic Béziers. Control points are clamped
 * vertically to the neighbouring values so the curve never overshoots into a false peak.
 */
fun smoothPath(pts: List<Offset>, path: Path = Path()): Path {
    path.reset()
    if (pts.isEmpty()) return path
    path.moveTo(pts[0].x, pts[0].y)
    if (pts.size == 1) return path
    for (i in 0 until pts.size - 1) {
        val p0 = pts[maxOf(i - 1, 0)]
        val p1 = pts[i]
        val p2 = pts[i + 1]
        val p3 = pts[minOf(i + 2, pts.size - 1)]
        val lo = minOf(p1.y, p2.y)
        val hi = maxOf(p1.y, p2.y)
        val c1 = Offset(p1.x + (p2.x - p0.x) / 6f, (p1.y + (p2.y - p0.y) / 6f).coerceIn(lo, hi))
        val c2 = Offset(p2.x - (p3.x - p1.x) / 6f, (p2.y - (p3.y - p1.y) / 6f).coerceIn(lo, hi))
        path.cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
    }
    return path
}

/**
 * Tile sparkline (UI_STYLE §8.3): x is linear time across [t0, t1], y pads 10 % around the union of data
 * and band. Draws the ideal band (good @ 15 %), the acceptable band when a point falls outside it, a soft fill
 * under the line, the smoothed 1.5 dp line and an end dot in [endColor].
 */
@Composable
fun Sparkline(
    points: List<ChartPoint>,
    t0: Long,
    t1: Long,
    modifier: Modifier = Modifier,
    ideal: Range? = null,
    acceptable: Range? = null,
    lineColor: Color = ReefTheme.colors.primaryBright,
    endColor: Color = lineColor,
) {
    val c = ReefTheme.colors
    Canvas(modifier) {
        if (points.isEmpty()) return@Canvas
        val outside = acceptable != null && points.any { it.v < acceptable.lo || it.v > acceptable.hi }
        val dom = ChartMath.domain(points.map { it.v }, ideal)
        val s = ChartScale(t0, t1, dom.lo, dom.hi, 0f, 2.dp.toPx(), size.width - 4.dp.toPx(), size.height - 4.dp.toPx())
        if (outside) bandRect(s, acceptable, c.good.copy(alpha = 0.08f))
        if (ideal != null) bandRect(s, ideal, c.good.copy(alpha = 0.15f))
        val pts = points.map { Offset(s.x(it.t).coerceIn(s.left, s.right), s.y(it.v)) }
        if (pts.size >= 2) {
            val line = smoothPath(pts)
            val fill = Path().apply {
                addPath(line)
                lineTo(pts.last().x, size.height)
                lineTo(pts.first().x, size.height)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.22f), Color.Transparent)))
            drawPath(line, lineColor, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        val end = pts.last()
        drawCircle(endColor.copy(alpha = 0.25f), 4.dp.toPx(), end)
        drawCircle(endColor, 2.dp.toPx(), end)
    }
}

internal fun DrawScope.bandRect(s: ChartScale, r: Range, color: Color) {
    val yTop = s.y(r.hi).coerceIn(s.top - 2f, s.bottom + 2f)
    val yBot = s.y(r.lo).coerceIn(s.top - 2f, s.bottom + 2f)
    if (yBot - yTop <= 0.5f) {
        // A zero-width ideal (e.g. ammonia 0–0) still shows as a hairline.
        drawLine(color.copy(alpha = (color.alpha * 3f).coerceAtMost(1f)), Offset(s.left, yTop), Offset(s.right, yTop), strokeWidth = 1.dp.toPx())
        return
    }
    drawRect(color, Offset(s.left, yTop), Size(s.width, yBot - yTop))
}
