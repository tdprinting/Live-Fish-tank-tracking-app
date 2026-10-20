package com.reeflive.app.ui.live

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.core.custom.TankBuild
import com.reeflive.core.custom.TankBuilds
import kotlin.math.roundToInt

/**
 * "My tank" before there is one: pick a tank to set up. It starts clean and empty (cycled, ready for animals); then
 * Add life fills it, and everything in it is saved on the phone from then on.
 */
@Composable
fun MyTankSetupCard(onPick: (TankBuild) -> Unit, modifier: Modifier = Modifier) {
    val s = LiveTheme.style
    val c = ReefTheme.colors
    LivePanel(modifier.widthIn(max = 460.dp).fillMaxWidth()) {
        Text("Set up your own tank", style = s.titleText, color = s.label)
        Spacer(Modifier.height(4.dp))
        Text(
            "Pick a tank. It starts clean and empty, then add any animals you like from Add life. " +
                "Everything is saved: names, personalities, how much they grow.",
            style = MaterialTheme.typography.bodyMedium, color = c.text,
        )
        Spacer(Modifier.height(12.dp))
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TankBuilds.all.forEach { b ->
                LivePanel(Modifier.fillMaxWidth(), tint = Candy.green, onClick = { onPick(b) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(b.label, style = s.buttonText.copy(fontSize = 16.sp), color = s.label)
                            Text("${b.gallons.roundToInt()} gal · ${b.sizeLabel}${if (b.rimless) " · rimless" else ""}", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                        }
                        TankSketch(b, Modifier.size(width = 96.dp, height = 56.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(b.blurb, style = MaterialTheme.typography.bodySmall, color = c.text)
                }
            }
        }
    }
}


/**
 * A little drawing of [b], every build at the same scale so the sizes compare at a glance: the glass box in a
 * three-quarter view, water, sand, the black rim (none on a rimless tank) and the stand. Mirrors the web picker.
 */
@Composable
private fun TankSketch(b: TankBuild, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val sh = b.shape
        val slant = 0.42f
        // every build at the scale that fits the biggest (48 × 24 × 24 in) in the box
        val k = minOf(size.width / (121.9f + 61f * slant + 4f), size.height / (61f + 61f * slant + 12f))
        val l = sh.lengthCm.toFloat() * k; val h = sh.heightCm.toFloat() * k; val d = sh.depthCm.toFloat() * k * slant
        val w = sh.waterLevelCm.toFloat() * k; val sand = 4.5f * k
        val x0 = (size.width - l - d) / 2f; val y0 = size.height - 8f * k - 1f
        fun poly(p: List<Offset>) = Path().apply { moveTo(p[0].x, p[0].y); for (i in 1 until p.size) lineTo(p[i].x, p[i].y); close() }
        drawRoundRect(Color(0xFF6B4A2E), Offset(x0 - 2f, y0 + 1f), Size(l + d + 4f, 7f * k), androidx.compose.ui.geometry.CornerRadius(2f))
        val wy = y0 - w
        drawPath(poly(listOf(Offset(x0 + d, wy - d), Offset(x0 + l + d, wy - d), Offset(x0 + l + d, y0 - d), Offset(x0 + l, y0), Offset(x0, y0), Offset(x0, wy))), Color(0x592A8FC4))
        drawPath(poly(listOf(Offset(x0, y0 - sand), Offset(x0 + l, y0 - sand), Offset(x0 + l + d, y0 - sand - d), Offset(x0 + l + d, y0 - d), Offset(x0 + l, y0), Offset(x0, y0))), Color(0xFFE8DCC0))
        drawPath(poly(listOf(Offset(x0, wy), Offset(x0 + l, wy), Offset(x0 + l + d, wy - d), Offset(x0 + d, wy - d))), Color(0x737FD0FF))
        val top = y0 - h
        val glass = Color(0xE6BFE8FF)
        val thin = Stroke(width = 1.2f)
        drawRect(glass, Offset(x0, top), Size(l, h), style = thin)
        drawPath(poly(listOf(Offset(x0 + l, top), Offset(x0 + l + d, top - d), Offset(x0 + l + d, y0 - d), Offset(x0 + l, y0))), glass, style = thin)
        drawPath(poly(listOf(Offset(x0, top), Offset(x0 + d, top - d), Offset(x0 + l + d, top - d), Offset(x0 + l, top))), glass, style = thin)
        if (!b.rimless) {
            val rim = Stroke(width = 2.6f)
            drawPath(poly(listOf(Offset(x0, top), Offset(x0 + l, top), Offset(x0 + l + d, top - d), Offset(x0 + d, top - d))), Color(0xFF111111), style = rim)
            drawLine(Color(0xFF111111), Offset(x0, y0), Offset(x0 + l, y0), strokeWidth = 2.6f)
        }
    }
}
