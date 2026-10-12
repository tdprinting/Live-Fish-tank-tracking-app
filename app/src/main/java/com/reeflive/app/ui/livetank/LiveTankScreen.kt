package com.reeflive.app.ui.livetank

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reeflive.app.live.SimMode
import com.reeflive.app.render.RenderQuality
import com.reeflive.app.render.TankView
import com.reeflive.app.ui.components.BottomBarClearance
import com.reeflive.app.ui.components.ButtonKind
import com.reeflive.app.ui.components.ReefButton
import com.reeflive.app.ui.live.FishCard
import com.reeflive.app.ui.live.FollowOverlay
import com.reeflive.app.ui.live.HudMode
import com.reeflive.app.ui.live.HudState
import com.reeflive.app.ui.live.LiveOverlay
import com.reeflive.app.ui.live.SandboxActions
import com.reeflive.app.ui.live.SandboxPanel
import com.reeflive.app.ui.live.SandboxScenario
import com.reeflive.app.ui.live.SandboxState
import com.reeflive.app.ui.live.toFishCardState
import com.reeflive.app.ui.live.toFollowState
import com.reeflive.app.ui.live.waterReadouts
import com.reeflive.app.ui.nav.reefViewModel
import com.reeflive.core.live.Scenario

private fun LiveQuality.toRender() = when (this) {
    LiveQuality.LOW -> RenderQuality.LOW
    LiveQuality.MEDIUM -> RenderQuality.MEDIUM
    LiveQuality.HIGH -> RenderQuality.HIGH
}

private fun SandboxScenario.toCore() = when (this) {
    SandboxScenario.HEAT_WAVE -> Scenario.HEAT_WAVE
    SandboxScenario.NITRATE_SPIKE -> Scenario.NITRATE_SPIKE
    SandboxScenario.SALINITY_SWING -> Scenario.SALINITY_SWING
    SandboxScenario.POWER_OUTAGE -> Scenario.POWER_OUTAGE
    SandboxScenario.ALGAE_BLOOM -> Scenario.ALGAE_BLOOM
    SandboxScenario.OVERFEEDING -> Scenario.OVERFEEDING
}

/** Live Tank: GL [TankView] with the HUD, sandbox sheet, fish card and follow overlay on top. Sim runs only while RESUMED. */
@Composable
fun LiveTankScreen(
    modifier: Modifier = Modifier,
    onOpenLivestock: (String) -> Unit = {},
) {
    val vm = reefViewModel { LiveTankViewModel(it.simulationHolder) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onResume() }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { vm.onPause() }
    val hud by vm.hud.collectAsStateWithLifecycle()
    val mode by vm.mode.collectAsStateWithLifecycle()
    val speed by vm.speed.collectAsStateWithLifecycle()
    val quality by vm.quality.collectAsStateWithLifecycle()
    val followId by vm.followId.collectAsStateWithLifecycle()
    val stats by vm.selectedStats.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }

    val lightPct = hud.lightIntensity * 100.0
    val hudState = HudState(
        mode = if (mode == SimMode.SANDBOX) HudMode.SANDBOX else HudMode.WATCH,
        speed = speed,
        simTimeSec = hud.simTimeSec,
        readouts = waterReadouts(hud.water, lightPct, hud.flowStrength.toDouble()),
    )
    val actions = SandboxActions(
        onSetParam = { id, v ->
            when (id) {
                "light" -> vm.setLights(v > 0.0, v / 100.0)
                "flow" -> vm.setPowerheads(v > 0.05)
                "photoperiod", "algae" -> Unit // no ViewModel/Command equivalent yet
                else -> vm.setParam(id, v)
            }
        },
        onFeed = { vm.feed() },
        onScenario = { vm.runScenario(it.toCore()) },
        onReset = { vm.resetSandbox() },
        onToggleAdvanced = { advanced = it },
    )

    Box(modifier.fillMaxSize()) {
        TankView(
            snapshots = vm.snapshots,
            modifier = Modifier.fillMaxSize(),
            followId = followId,
            quality = quality.toRender(),
            onPick = vm::select,
            onTapGlass = vm::tapGlass,
            onFollowLost = { vm.follow(null) },
        )
        Box(Modifier.fillMaxSize().statusBarsPadding().padding(bottom = BottomBarClearance)) {
            LiveOverlay(
                state = hudState,
                onModeChange = { vm.setMode(if (it == HudMode.SANDBOX) SimMode.SANDBOX else SimMode.REALITY) },
                onSpeedChange = vm::setSpeed,
            )
            val st = stats
            if (followId != null && st != null) {
                val ids = hud.stats.map { it.id }
                val i = ids.indexOf(st.id).coerceAtLeast(0)
                fun step(d: Int) { if (ids.isNotEmpty()) vm.follow(ids[(i + d + ids.size) % ids.size]) }
                FollowOverlay(st.toFollowState(i, ids.size), { step(-1) }, { step(1) }, { vm.follow(null) })
            } else {
                Column(Modifier.align(Alignment.BottomCenter).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (mode == SimMode.SANDBOX) {
                        ReefButton("Sandbox", { sheet = true }, Modifier.fillMaxWidth(), kind = ButtonKind.TONAL)
                    }
                    if (st != null) FishCard(st.toFishCardState(), onFollow = { vm.follow(st.id) }, onClose = { vm.select(null) })
                }
            }
        }
        if (sheet && mode == SimMode.SANDBOX) {
            SandboxPanel(
                state = SandboxState.from(hud.water, hud.lightIntensity.toDouble(), 12.0, hud.flowStrength.toDouble())
                    .copy(advancedExpanded = advanced),
                actions = actions,
                onDismiss = { sheet = false },
            )
        }
    }
}
