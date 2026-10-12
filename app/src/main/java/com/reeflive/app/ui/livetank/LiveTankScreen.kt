package com.reeflive.app.ui.livetank

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reeflive.app.appGraph
import com.reeflive.app.audio.TankAudio
import com.reeflive.app.data.repo.AppSettings
import com.reeflive.app.live.SimMode
import com.reeflive.app.render.GlassStroke
import com.reeflive.app.render.RenderQuality
import com.reeflive.app.render.TankView
import com.reeflive.app.ui.components.BottomBarClearance
import com.reeflive.app.ui.live.AddableSpecies
import com.reeflive.app.ui.live.CleanGlassHint
import com.reeflive.app.ui.live.DayPhase
import com.reeflive.app.ui.live.FishCard
import com.reeflive.app.ui.live.FishSummary
import com.reeflive.app.ui.live.FollowOverlay
import com.reeflive.app.ui.live.HudMode
import com.reeflive.app.ui.live.HudState
import com.reeflive.app.ui.live.LiveOverlay
import com.reeflive.app.ui.live.SandboxActions
import com.reeflive.app.ui.live.SandboxCategory
import com.reeflive.app.ui.live.SandboxControlMode
import com.reeflive.app.ui.live.SandboxDock
import com.reeflive.app.ui.live.SandboxPanelState
import com.reeflive.app.ui.live.SandboxParamDef
import com.reeflive.app.ui.live.SandboxPreset
import com.reeflive.app.ui.live.SelectedAnimalUi
import com.reeflive.app.ui.live.StockUi
import com.reeflive.app.ui.live.TankReading
import com.reeflive.app.ui.live.TimelapseSheet
import com.reeflive.app.ui.live.toFishCardState
import com.reeflive.app.ui.live.toFollowState
import com.reeflive.app.ui.live.waterReadouts
import com.reeflive.app.ui.nav.reefViewModel
import com.reeflive.app.ui.theme.ReefMotion
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.Mood
import com.reeflive.core.sim.OrganismStats
import com.reeflive.core.sim.timelapse.TimelapseState
import kotlinx.coroutines.launch

private fun LiveQuality.toRender() = when (this) {
    LiveQuality.LOW -> RenderQuality.LOW
    LiveQuality.MEDIUM -> RenderQuality.MEDIUM
    LiveQuality.HIGH -> RenderQuality.HIGH
}

/** Height of the floating bottom bar area (64 dp pill + 16 dp margin) plus a little air. */
private val NavClearance = 88.dp

/** Point on the front glass, centre, for the "Tap glass" action (tank cm: 122 × 61 × 33). */
private val FrontGlassCentre = Vec3(61.0, 30.0, 33.0)

private fun summarize(stats: List<OrganismStats>): FishSummary {
    val alive = stats.filter { it.alive }
    return FishSummary(
        alive = alive.size,
        stressed = alive.count { it.stress > 0.6f || it.mood == Mood.STRESSED },
        sick = alive.count { it.disease != null || it.mood == Mood.SICK },
        dead = stats.count { !it.alive },
    )
}

/** Share (0..1) of the sandbox powerhead toggles that are on; Watch mode keeps them all on. */
private fun powerheadShare(extras: Map<String, Double>): Float {
    val ids = LiveTankViewModel.PUMP_IDS.keys
    return ids.count { (extras[it] ?: 1.0) >= 0.5 }.toFloat() / ids.size.coerceAtLeast(1)
}

/**
 * Live Tank: GL [TankView] with the HUD on top and, in Sandbox mode, the [SandboxDock] docked inline: under the tank in
 * portrait (the tank view shrinks to the space above it), beside it in landscape. The Watch / Sandbox toggle opens and
 * closes the dock; tapping Sandbox again collapses / expands it. Sim runs only while RESUMED.
 */
@Composable
fun LiveTankScreen(
    modifier: Modifier = Modifier,
    onOpenLivestock: (String) -> Unit = {},
) {
    val vm = reefViewModel { LiveTankViewModel(it.simulationHolder) }
    val context = LocalContext.current
    val settingsRepo = context.appGraph.settings
    val settings by settingsRepo.settings.collectAsStateWithLifecycle(AppSettings())
    val scope = rememberCoroutineScope()
    // tank sounds: created with the screen, released when it leaves composition, paused with the lifecycle
    val audio = remember { TankAudio(context.applicationContext) }
    DisposableEffect(audio) { onDispose { audio.release() } }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onResume(); audio.onResume() }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { vm.onPause(); audio.onPause() }
    LaunchedEffect(settings.loaded, settings.tankSounds, settings.tankVolume) {
        if (settings.loaded) audio.setUserSettings(settings.tankSounds, settings.tankVolume)
    }
    LaunchedEffect(audio) {
        // full-rate snapshots: the 10 Hz HUD flow would drop events
        vm.snapshots.collect { snap -> audio.onSnapshot(snap, powerheadShare(vm.extras.value)) }
    }
    val soundOn = settings.tankSounds
    val toggleSound: () -> Unit = { scope.launch { settingsRepo.setTankSounds(!soundOn) } }
    val tick: () -> Unit = audio::uiTick
    val hud by vm.hud.collectAsStateWithLifecycle()
    val mode by vm.mode.collectAsStateWithLifecycle()
    val speed by vm.speed.collectAsStateWithLifecycle()
    val quality by vm.quality.collectAsStateWithLifecycle()
    val followId by vm.followId.collectAsStateWithLifecycle()
    val stats by vm.selectedStats.collectAsStateWithLifecycle()
    val real by vm.real.collectAsStateWithLifecycle()
    val lightPct by vm.lightPct.collectAsStateWithLifecycle()
    val predation by vm.predation.collectAsStateWithLifecycle()
    val extras by vm.extras.collectAsStateWithLifecycle()
    val lapseState by vm.timelapse.collectAsStateWithLifecycle()
    var lapse by rememberSaveable { mutableStateOf(false) }

    var expanded by rememberSaveable { mutableStateOf(true) }
    var category by rememberSaveable { mutableStateOf(SandboxCategory.WATER) }
    var preset by rememberSaveable { mutableStateOf<SandboxPreset?>(null) }
    var controlMode by rememberSaveable { mutableStateOf(SandboxControlMode.SET) }
    val sandbox = mode == SimMode.SANDBOX

    BackHandler(enabled = sandbox && expanded) { expanded = false }
    // clean-glass mode (both modes): drag the Flipper over the front glass; Back or "Done" leaves it
    var cleaning by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = cleaning) { cleaning = false }
    val toggleCleaning: () -> Unit = {
        tick()
        if (!cleaning) vm.select(null)
        cleaning = !cleaning
    }
    val onScrape: (GlassStroke) -> Unit = { s ->
        vm.scrapeGlass(s)
        audio.scrape(s.lengthCm)
    }

    val hudState = HudState(
        mode = if (sandbox) HudMode.SANDBOX else HudMode.WATCH,
        speed = speed,
        simTimeSec = hud.simTimeSec,
        dayPhase = DayPhase.entries.firstOrNull { it.label == hud.dayPhase } ?: DayPhase.DAY,
        readouts = waterReadouts(hud.water, hud.lightIntensity * 100.0, hud.flowStrength.toDouble()),
    )

    val speciesDb = context.appGraph.speciesDb
    val stockCards = remember(speciesDb) { StockUi.catalogCards { id -> speciesDb[id]?.colors ?: emptyMap() } }
    val panel = SandboxPanelState(
        now = TankReading(
            hud.water, lightPct = lightPct, flowPct = hud.flowStrength * 100.0,
            photoperiodH = extras["photoperiod"],
            pumps = extras.filterKeys { it.startsWith("pump_") },
            gains = extras.filterKeys { it.startsWith("spectrum_") }.mapKeys { it.key.removePrefix("spectrum_") },
        ),
        real = real,
        category = category,
        expanded = expanded,
        speed = speed,
        controlMode = controlMode,
        activePreset = preset,
        fish = summarize(hud.stats),
        selected = stats?.takeIf { it.alive }?.let { SelectedAnimalUi(it.id, it.nickname.ifBlank { it.common }, it.lengthCm.toDouble()) },
        addable = hud.stats.filter { it.alive }.groupBy { it.speciesId }.map { (id, list) ->
            AddableSpecies(id, list.first().common, list.map { it.lengthCm.toDouble() }.sorted()[list.size / 2])
        }.take(6),
        predation = predation,
        stock = StockUi(
            cards = stockCards,
            counts = hud.stats.filter { it.alive }.groupingBy { it.speciesId }.eachCount() +
                hud.corals.groupingBy { it.speciesId }.eachCount(),
            animals = hud.stats.count { it.alive },
            corals = hud.corals.size,
        ),
        timelapseStatus = when (val l = lapseState) {
            TimelapseState.Idle -> null
            is TimelapseState.Running -> "Running · ${(l.progress.fraction * 100).toInt()} %"
            is TimelapseState.Done -> if (l.cancelled) "Stopped early · partial results" else "Results ready"
            is TimelapseState.Failed -> "Failed: ${l.message}"
        },
    )

    val onSet: (SandboxParamDef, Double) -> Unit = { def, v ->
        val now = panel.now
        def.simId?.let { id -> vm.applyParam(def.id, id, v / def.scale, def.valueIn(now) / def.scale, controlMode) }
        if (def.max - def.min <= def.step) tick()   // on/off rows (pumps) click; dragged sliders stay quiet
    }
    val actions = SandboxActions(
        onSet = onSet,
        onCategory = { category = it },
        onToggleExpanded = { expanded = !expanded },
        onSpeed = { tick(); vm.setSpeed(it) },
        onControlMode = { controlMode = it },
        onPreset = { p ->
            tick()
            vm.runPreset(p)
            preset = p
            category = p.focus      // jump to the sliders that will move, so the effect is visible
            expanded = true
        },
        onDismissPreset = { preset = null },
        onFeed = { vm.feed() },
        onTapGlass = { tick(); vm.tapGlass(FrontGlassCentre) },
        onWaterChange = { tick(); vm.waterChange(it) },
        onDose = { id, d -> tick(); vm.dose(id, d) },
        onHeater = { tick(); vm.setHeater(working = it) },
        onLights = { tick(); vm.setLights(it) },
        onPredation = { tick(); vm.setPredation(it) },
        onResize = { id, cm -> vm.setSize(id, cm) },
        onRemove = { tick(); vm.removeOrganism(it) },
        onAdd = { a -> tick(); vm.addOrganism(a.speciesId, a.sizeCm, "New ${a.name}") },
        onReset = { tick(); vm.resetSandbox(); preset = null },
        onAcclimateAll = { tick(); vm.acclimateAll() },
        onRearrangeRock = { tick(); vm.rearrangeRock() },
        onPumpFailTonight = { tick(); vm.pumpFailTonight() },
        onTimelapse = { lapse = true },
        onAddMany = { id, n -> tick(); vm.addMany(id, n) },
    )
    val onModeChange: (HudMode) -> Unit = { m ->
        when {
            m == HudMode.SANDBOX && sandbox -> expanded = !expanded
            m == HudMode.SANDBOX -> { expanded = true; preset = null; vm.setMode(SimMode.SANDBOX) }
            else -> { preset = null; vm.setMode(SimMode.REALITY) }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val sideWidth = (maxWidth * 0.42f).coerceIn(300.dp, 440.dp)
        val dockHeight = (maxHeight * 0.55f).coerceAtLeast(minOf(320.dp, maxHeight * 0.7f))
        val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val clearance = NavClearance + navInset
        if (landscape) {
            Row(Modifier.fillMaxSize()) {
                TankArea(
                    vm, hudState, onModeChange, followId, stats, quality, soundOn, toggleSound, cleaning, toggleCleaning, onScrape,
                    Modifier.weight(1f).fillMaxHeight(), bottomPad = BottomBarClearance,
                )
                if (sandbox) {
                    SandboxDock(
                        panel, actions,
                        Modifier.width(sideWidth).fillMaxHeight().statusBarsPadding(),
                        side = true, bottomClearance = clearance,
                    )
                }
            }
        } else {
            val reduce = ReefTheme.reduceMotion
            // the dock takes a bit over half the height; the tank view gets the rest (never less than 30 %)
            val animated by animateDpAsState(dockHeight, ReefMotion.tween(reduce), label = "dock")
            Column(Modifier.fillMaxSize()) {
                TankArea(
                    vm, hudState, onModeChange, followId, stats, quality, soundOn, toggleSound, cleaning, toggleCleaning, onScrape,
                    Modifier.weight(1f).fillMaxWidth(), bottomPad = if (sandbox) 8.dp else BottomBarClearance,
                )
                if (sandbox) {
                    val dockModifier = if (expanded) Modifier.fillMaxWidth().height(animated) else Modifier.fillMaxWidth()
                    SandboxDock(panel, actions, dockModifier, side = false, bottomClearance = clearance)
                }
            }
        }
        if (lapse && sandbox) {
            TimelapseSheet(
                state = lapseState, water = hud.water,
                onStart = vm::startTimelapse, onCancel = vm::cancelTimelapse, onNew = vm::clearTimelapse,
                onDismiss = { lapse = false },
            )
        }
    }
}

/** The 3D tank with the HUD, follow overlay and fish card layered on top. */
@Composable
private fun TankArea(
    vm: LiveTankViewModel,
    hudState: HudState,
    onModeChange: (HudMode) -> Unit,
    followId: String?,
    stats: OrganismStats?,
    quality: LiveQuality,
    soundOn: Boolean,
    onToggleSound: () -> Unit,
    cleaning: Boolean,
    onToggleCleaning: () -> Unit,
    onScrape: (GlassStroke) -> Unit,
    modifier: Modifier,
    bottomPad: Dp,
) {
    Box(modifier) {
        TankView(
            snapshots = vm.snapshots,
            modifier = Modifier.fillMaxSize(),
            followId = if (cleaning) null else followId,
            quality = quality.toRender(),
            onPick = vm::select,
            onTapGlass = vm::tapGlass,
            onFollowLost = { vm.follow(null) },
            cleaning = cleaning,
            onScrape = onScrape,
        )
        Box(Modifier.fillMaxSize().statusBarsPadding().padding(bottom = bottomPad)) {
            LiveOverlay(
                state = hudState,
                onModeChange = onModeChange,
                onSpeedChange = vm::setSpeed,
                showSpeed = false,   // speed lives in the sandbox dock (reality always runs at 1x)
                soundOn = soundOn,
                onToggleSound = onToggleSound,
                cleaning = cleaning,
                onToggleCleaning = onToggleCleaning,
            )
            val st = stats
            if (cleaning) {
                CleanGlassHint(Modifier.align(Alignment.BottomCenter).padding(16.dp))
            } else if (followId != null && st != null) {
                val ids = vm.hud.value.stats.map { it.id }
                val i = ids.indexOf(st.id).coerceAtLeast(0)
                fun step(d: Int) { if (ids.isNotEmpty()) vm.follow(ids[(i + d + ids.size) % ids.size]) }
                FollowOverlay(st.toFollowState(i, ids.size), { step(-1) }, { step(1) }, { vm.follow(null) })
            } else if (st != null) {
                Column(Modifier.align(Alignment.BottomCenter).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FishCard(st.toFishCardState(), onFollow = { vm.follow(st.id) }, onClose = { vm.select(null) })
                }
            }
        }
    }
}
