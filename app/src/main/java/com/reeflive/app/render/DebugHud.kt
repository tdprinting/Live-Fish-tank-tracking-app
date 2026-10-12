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

/** Developer overlay (ARCHITECTURE §8.2 `DebugHud`): fps, draw calls, triangles, GL errors, loading. */
@Composable
fun DebugHud(stats: StateFlow<RenderStats>, modifier: Modifier = Modifier) {
    val s by stats.collectAsState()
    Column(
        modifier
            .background(Color(0x99000000), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        val style = androidx.compose.ui.text.TextStyle(color = Color(0xFFB8F5E0), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Text("%.0f fps  %.1f ms  %s".format(s.fps, s.frameMs, s.quality.name), style = style)
        Text("draws ${s.drawCalls}  tris ${"%,d".format(s.triangles)}", style = style)
        Text("models ${s.modelsLoaded}/${s.modelsTotal}  organisms ${s.organisms}", style = style)
        Text("light %.2f  follow %s".format(s.lightIntensity, s.following ?: "-"), style = style)
        if (s.glErrors > 0) Text("GL errors ${s.glErrors}", style = style.copy(color = Color(0xFFFF8A80)))
        if (s.glInfo.isNotEmpty()) Text(s.glInfo, style = style.copy(fontSize = 9.sp))
    }
}
