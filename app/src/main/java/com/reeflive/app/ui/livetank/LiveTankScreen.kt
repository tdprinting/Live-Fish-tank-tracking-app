package com.reeflive.app.ui.livetank

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reeflive.app.appGraph
import com.reeflive.app.ui.components.BottomBarClearance
import com.reeflive.app.ui.theme.GlassCard
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.core.util.Fmt
import java.time.LocalTime

/**
 * PLACEHOLDER owned by Track C (C3 replaces the whole file with the GL renderer + overlay).
 * It only proves the data hand-off: `MeasurementRepository.latestValues()`, `LivestockRepository.alive()`
 * and `LightScheduleRepository.active()` from `context.appGraph`.
 */
@Composable
fun LiveTankScreen(
    modifier: Modifier = Modifier,
    onOpenLivestock: (String) -> Unit = {},
) {
    val graph = LocalContext.current.appGraph
    val latest by remember(graph) { graph.measurements.latestValues() }.collectAsStateWithLifecycle(emptyMap())
    val alive by remember(graph) { graph.livestock.alive() }.collectAsStateWithLifecycle(emptyList())
    val light by remember(graph) { graph.lights.active() }.collectAsStateWithLifecycle(null)
    val c = ReefTheme.colors
    val now = LocalTime.now()
    val intensity = light?.intensityAt(now.hour * 60.0 + now.minute) ?: 0.0

    Box(modifier.fillMaxSize().statusBarsPadding().padding(bottom = BottomBarClearance), contentAlignment = Alignment.Center) {
        GlassCard(Modifier.padding(24.dp).fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Live Tank", style = MaterialTheme.typography.headlineMedium, color = c.text)
                Text("The 3D reef is on its way.", style = MaterialTheme.typography.bodyLarge, color = c.textSecondary)
                Text(
                    "${alive.sumOf { it.quantity }} organisms · lights ${Fmt.pct(intensity)} · ${latest.size} parameters logged",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textTertiary,
                )
            }
        }
    }
}
