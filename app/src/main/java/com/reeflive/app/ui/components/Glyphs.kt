package com.reeflive.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BubbleChart
import androidx.compose.material.icons.rounded.ElectricBolt
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reeflive.app.ui.theme.ReefFonts
import com.reeflive.app.ui.theme.ReefTheme

private fun iconFor(paramId: String): ImageVector? = when (paramId) {
    "temp" -> Icons.Rounded.Thermostat
    "sal" -> Icons.Rounded.WaterDrop
    "ph" -> Icons.Rounded.Science
    "alk" -> Icons.Rounded.Layers
    "orp" -> Icons.Rounded.ElectricBolt
    "do" -> Icons.Rounded.BubbleChart
    "tds" -> Icons.Rounded.FilterAlt
    else -> null
}

private fun symbolFor(paramId: String): String = when (paramId) {
    "ca" -> "Ca"; "mg" -> "Mg"; "k" -> "K"; "sr" -> "Sr"; "i" -> "I"; "fe" -> "Fe"; "sio2" -> "Si"; "po4" -> "P"
    "no3", "no2", "nh3" -> "N"
    else -> paramId.take(2).replaceFirstChar { it.uppercase() }
}

/**
 * Parameter glyph (UI_STYLE §5): a rounded icon for physical parameters, an element badge
 * (rounded square with the symbol, 1.75 dp stroke) for chemistry; nitrogen species show
 * 1/2/3 dots for NH3/NO2/NO3.
 */
@Composable
fun ParamGlyph(paramId: String, modifier: Modifier = Modifier, size: Dp = 18.dp, tint: Color = ReefTheme.colors.textSecondary) {
    val icon = iconFor(paramId)
    if (icon != null) {
        Icon(icon, contentDescription = null, tint = tint, modifier = modifier.size(size))
        return
    }
    val dots = when (paramId) { "nh3" -> 1; "no2" -> 2; "no3" -> 3; else -> 0 }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val sw = 1.5.dp.toPx()
            if (paramId == "po4") {
                // diamond
                val p = androidx.compose.ui.graphics.Path().apply {
                    moveTo(this@Canvas.size.width / 2, sw); lineTo(this@Canvas.size.width - sw, this@Canvas.size.height / 2)
                    lineTo(this@Canvas.size.width / 2, this@Canvas.size.height - sw); lineTo(sw, this@Canvas.size.height / 2); close()
                }
                drawPath(p, tint, style = Stroke(sw))
            } else {
                drawRoundRect(tint, topLeft = Offset(sw / 2, sw / 2), size = androidx.compose.ui.geometry.Size(this.size.width - sw, this.size.height - sw), cornerRadius = CornerRadius(4.dp.toPx()), style = Stroke(sw))
            }
            if (dots > 0) {
                val r = 1.2.dp.toPx()
                val y = this.size.height - 3.5.dp.toPx()
                val step = 3.2.dp.toPx()
                val start = this.size.width / 2 - step * (dots - 1) / 2
                repeat(dots) { drawCircle(tint, r, Offset(start + it * step, y)) }
            }
        }
        val fs = (size.value * if (symbolFor(paramId).length > 1) 0.42f else 0.5f).sp
        Text(
            symbolFor(paramId),
            style = TextStyle(fontFamily = ReefFonts.inter, fontWeight = FontWeight.SemiBold, fontSize = fs, color = tint),
            modifier = if (dots > 0) Modifier.offset(y = -(size * 0.14f)) else Modifier,
        )
    }
}
