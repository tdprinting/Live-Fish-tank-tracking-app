package com.reeflive.app.render

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.StateFlow

/**
 * Frame-rate overlay (Settings → Live Tank → "Show frame rate"; ARCHITECTURE §8.2 `DebugHud`): frames per second, the
 * GL thread's time per frame and the worst gap between frames in the last half second, the render scale, the effects
 * auto quality has shed and the frame cap; then draw calls, triangles, GL errors and loading. [RenderStats] is published
 * twice a second, so only this small box recomposes, at 2 Hz.
 */
@Composable
fun DebugHud(stats: StateFlow<RenderStats>, modifier: Modifier = Modifier, detail: Boolean = true) {
    val s by stats.collectAsState()
    Column(
        modifier
            .background(Color(0x99000000), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        val style = androidx.compose.ui.text.TextStyle(color = Color(0xFFB8F5E0), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        val slow = s.targetFps > 0 && s.fps < s.targetFps * 0.85f
        Text(
            "%.0f fps  gl %.1f ms  worst %.0f ms".format(s.fps, s.frameMs, s.worstMs),
            style = if (slow) style.copy(color = Color(0xFFFFD27A)) else style,
        )
        val cap = if (s.targetFps > 0) "cap ${s.targetFps}" else "vsync"
        Text("%s  %.2fx  shed %d  %s".format(s.quality.name.lowercase(), s.renderScale, s.relief, cap), style = style)
        if (detail) {
            Text("draws ${s.drawCalls}  tris ${"%,d".format(s.triangles)}", style = style)
            Text("models ${s.modelsLoaded}/${s.modelsTotal}  organisms ${s.organisms}", style = style)
        }
        if (s.glErrors > 0) Text("GL errors ${s.glErrors}", style = style.copy(color = Color(0xFFFF8A80)))
        if (detail && s.glInfo.isNotEmpty()) Text(s.glInfo, style = style.copy(fontSize = 9.sp))
    }
}
