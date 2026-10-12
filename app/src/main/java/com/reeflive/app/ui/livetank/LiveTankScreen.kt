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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.reeflive.app.ui.nav.reefViewModel
import com.reeflive.app.ui.components.BottomBarClearance
import com.reeflive.app.ui.theme.GlassCard
import com.reeflive.app.ui.theme.ReefTheme

/**
 * Hosts the Live Tank: a placeholder [Box] where the GL view and overlay will go, wired to [LiveTankViewModel].
 * The simulation only runs while this destination is RESUMED.
 */
@Composable
fun LiveTankScreen(
    modifier: Modifier = Modifier,
    onOpenLivestock: (String) -> Unit = {},
) {
    val vm = reefViewModel { LiveTankViewModel(it.simulationHolder) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onResume() }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { vm.onPause() }
    val peek by vm.peek.collectAsStateWithLifecycle()
    val hud by vm.hud.collectAsStateWithLifecycle()
    val c = ReefTheme.colors

    Box(modifier.fillMaxSize().statusBarsPadding().padding(bottom = BottomBarClearance), contentAlignment = Alignment.Center) {
        GlassCard(Modifier.padding(24.dp).fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Live Tank", style = MaterialTheme.typography.headlineMedium, color = c.text)
                Text("The 3D reef is on its way.", style = MaterialTheme.typography.bodyLarge, color = c.textSecondary)
                Text(
                    "${peek.fishAlive} fish alive · ${hud.organisms.size} simulated · " + (peek.lastEvent ?: "no events yet"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textTertiary,
                )
            }
        }
    }
}
