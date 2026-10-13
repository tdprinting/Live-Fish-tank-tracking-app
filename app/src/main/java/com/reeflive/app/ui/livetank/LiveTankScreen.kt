package com.reeflive.app.ui.livetank

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.reeflive.app.ui.components.LocalSnackbar
import com.reeflive.app.ui.components.ReefDialog
import com.reeflive.app.ui.components.rememberHaptics
import com.reeflive.app.ui.live.AddableSpecies
import com.reeflive.app.ui.live.CleanGlassHint
import com.reeflive.app.ui.live.DayPhase
import com.reeflive.app.ui.live.FishCard
import com.reeflive.app.ui.live.FishSummary
import com.reeflive.app.ui.live.FoodTray
import com.reeflive.app.ui.live.FollowOverlay
import com.reeflive.app.ui.live.HudMode
import com.reeflive.app.ui.live.HudState
import com.reeflive.app.ui.live.LiveOverlay
import com.reeflive.app.ui.live.MicroscopeView
import com.reeflive.app.ui.live.SandboxActions
import com.reeflive.app.ui.live.SandboxCategory
import com.reeflive.app.ui.live.SandboxControlMode
import com.reeflive.app.ui.live.SandboxDock
import com.reeflive.app.ui.live.SandboxFriendly
import com.reeflive.app.ui.live.SandboxPanelState
import com.reeflive.app.ui.live.SandboxParamDef
import com.reeflive.app.ui.live.SandboxPreset
import com.reeflive.app.ui.live.SandboxTipsCard
import com.reeflive.app.ui.live.SelectedAnimalUi
import com.reeflive.app.ui.live.ShowControlsPill
import com.reeflive.app.ui.live.StockUi
import com.reeflive.app.ui.live.TankReading
import com.reeflive.app.ui.live.TimelapseSheet
import com.reeflive.app.ui.live.toFishCardState
import com.reeflive.app.ui.live.toFollowState
import com.reeflive.app.ui.live.waterReadouts
import com.reeflive.app.ui.live.liveSpec
import com.reeflive.app.ui.nav.reefViewModel
import com.reeflive.app.ui.theme.ReefMotion
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.FoodKind
import com.reeflive.core.sim.Mood
import com.reeflive.core.sim.OrganismStats
import com.reeflive.core.sim.timelapse.TimelapseState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import kotlinx.coroutines.delay
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

/** A question to confirm before something that can't be undone with one tap (reset, take a fish out, make one sick). */
private data class ConfirmAsk(val title: String, val body: String, val confirmLabel: String, val run: () -> Unit)

/**
 * Live Tank: GL [TankView] with the HUD on top and, in Sandbox mode, the [SandboxDock]. Portrait: the tank keeps the
 * whole screen and the dock floats over its bottom as a see-through panel that tucks down to its tabs (tap / drag the
 * handle, or automatically while a finger drags the tank), at most about 40 % of the height when open. Landscape: the
 * dock sits beside the tank. Tapping empty water hides every control so only the tank shows; tapping again (or "Show
 * controls") brings them back. Sim runs only while RESUMED.
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
    val snackbar = LocalSnackbar.current
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

    // every overlay control hidden, only the tank (tap empty water or "Show controls" to undo)
    var chromeHidden by rememberSaveable { mutableStateOf(false) }
    // a finger is dragging the tank: the portrait panel tucks away, and comes back shortly after the finger lifts
    var touchingTank by remember { mutableStateOf(false) }
    var tucked by remember { mutableStateOf(false) }
    LaunchedEffect(touchingTank) {
        if (touchingTank) tucked = true else { delay(450); tucked = false }
    }
    // first-time tips (remembered in settings), and again from the panel's (?) button
    var tipsAsked by rememberSaveable { mutableStateOf(false) }
    val showTips = sandbox && !chromeHidden && (tipsAsked || (settings.loaded && !settings.sandboxHintSeen))
    val dismissTips: () -> Unit = {
        tipsAsked = false
        if (!settings.sandboxHintSeen) scope.launch { settingsRepo.setSandboxHintSeen(true) }
    }
    var ask by remember { mutableStateOf<ConfirmAsk?>(null) }

    BackHandler(enabled = sandbox && expanded) { expanded = false }
    BackHandler(enabled = chromeHidden) { chromeHidden = false }
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
    // a tap on empty water with nothing selected shows / hides the controls; otherwise it selects as before
    val onPick: (String?) -> Unit = { id ->
        if (id == null && !cleaning && vm.selectedOrganismId.value == null && vm.followId.value == null) {
            chromeHidden = !chromeHidden
        } else {
            vm.select(id)
        }
    }
    val hideChrome: () -> Unit = { tick(); chromeHidden = true }

    /** Runs nothing more; offers "Undo" on the app snackbar for a command that has an opposite. */
    val offerUndo: (String, () -> Unit) -> Unit = { message, undo ->
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar(message, actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) { tick(); undo() }
        }
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
            toggles = extras.filterKeys { it.startsWith("eq_") || it.startsWith("sc_") },
        ),
        real = real,
        category = category,
        expanded = expanded && !tucked,
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
        onCategory = { category = it; expanded = true },
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
        onFeed = { k -> tick(); vm.feed(k, k.typicalDoseGrams) },
        onTapGlass = { tick(); vm.tapGlass(FrontGlassCentre) },
        onWaterChange = { tick(); vm.waterChange(it) },
        onDose = { id, d -> tick(); vm.dose(id, d) },
        onHeater = { working ->
            tick()
            vm.setHeater(working = working)
            if (!working) offerUndo("The heater broke. The water will slowly cool.") { vm.setHeater(working = true) }
        },
        onLights = { tick(); vm.setLights(it) },
        onPredation = { tick(); vm.setPredation(it) },
        onResize = { id, cm -> vm.setSize(id, cm) },
        onRemove = { id ->
            val name = panel.selected?.takeIf { it.id == id }?.name ?: "this animal"
            ask = ConfirmAsk(
                "Take out $name?",
                "$name leaves the sandbox. Your real tank is not touched, and Reset tank brings everyone back.",
                "Take out",
            ) { tick(); vm.removeOrganism(id) }
        },
        onAdd = { a -> tick(); vm.addOrganism(a.speciesId, a.sizeCm, "New ${a.name}") },
        onReset = {
            ask = ConfirmAsk(
                "Reset the sandbox?",
                "Everything you changed here goes back to match your real tank: the water, the animals, the equipment and any what-ifs.",
                "Reset tank",
            ) { tick(); vm.resetSandbox(); preset = null }
        },
        onAcclimateAll = { tick(); vm.acclimateAll() },
        onRearrangeRock = { tick(); vm.rearrangeRock() },
        onPumpFailTonight = { tick(); vm.pumpFailTonight() },
        onTimelapse = { lapse = true },
        onAddMany = { id, n -> tick(); vm.addMany(id, n) },
        onAddAiptasia = {
            tick()
            vm.addAiptasia()
            offerUndo("Added a pest anemone") { vm.removeAiptasia() }
        },
        onRemoveAiptasia = { tick(); vm.removeAiptasia() },
        onAddPestControl = { c -> tick(); vm.addPestControl(c) },
        onStartTreatment = { k ->
            tick()
            vm.startTreatment(k)
            offerUndo("Started ${k.label}") { vm.stopTreatment(k) }
        },
        onStopTreatment = { k -> tick(); vm.stopTreatment(k) },
        onInfect = { d ->
            ask = ConfirmAsk(
                "Make a fish sick?",
                "A random fish in the sandbox catches ${SandboxFriendly.diseaseLabel(d)}. Only the sandbox changes; Reset tank undoes it.",
                "Make one sick",
            ) { tick(); vm.infect(d) }
        },
        onMaintain = { t -> tick(); vm.maintain(t) },
        onAddSpareShells = { tick(); vm.addSpareShells() },
        onHelp = { tick(); tipsAsked = true },
    )
    val onModeChange: (HudMode) -> Unit = { m ->
        when {
            m == HudMode.SANDBOX && sandbox -> expanded = !expanded
            m == HudMode.SANDBOX -> { expanded = true; chromeHidden = false; preset = null; vm.setMode(SimMode.SANDBOX) }
            else -> { preset = null; vm.setMode(SimMode.REALITY) }
        }
    }
    val chrome = ChromeState(
        hidden = chromeHidden,
        onHide = hideChrome,
        showTips = showTips,
        onDismissTips = dismissTips,
    )
    val onTankTouch: (Boolean) -> Unit = { touchingTank = it }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val reduce = ReefTheme.reduceMotion
        val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val clearance = NavClearance + navInset
        if (landscape) {
            val sideWidth = (maxWidth * 0.42f).coerceIn(300.dp, 440.dp)
            Row(Modifier.fillMaxSize()) {
                TankArea(
                    vm, hudState, onModeChange, followId, stats, quality, soundOn, toggleSound, cleaning, toggleCleaning, onScrape,
                    onPick, chrome, onTankTouch = null,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    bottomPad = { BottomBarClearance },
                )
                AnimatedVisibility(
                    visible = sandbox && !chromeHidden,
                    enter = fadeIn(liveSpec(reduce)) + expandHorizontally(liveSpec(reduce)),
                    exit = fadeOut(liveSpec(reduce, ReefMotion.FAST)) + shrinkHorizontally(liveSpec(reduce)),
                ) {
                    SandboxDock(
                        panel, actions,
                        Modifier.width(sideWidth).fillMaxHeight().statusBarsPadding(),
                        side = true, bottomClearance = clearance,
                    )
                }
            }
        } else {
            // the tank keeps the full screen (the GL view never resizes); the panel floats over its bottom
            val density = LocalDensity.current
            var dockPx by remember { mutableIntStateOf(0) }
            // body height so header + tabs + body stay around 40 % of the screen above the bottom bar
            val bodyHeight = (maxHeight * 0.40f - 112.dp).coerceAtLeast(150.dp)
            val panelUp = sandbox && !chromeHidden
            TankArea(
                vm, hudState, onModeChange, followId, stats, quality, soundOn, toggleSound, cleaning, toggleCleaning, onScrape,
                onPick, chrome, onTankTouch = if (sandbox) onTankTouch else null,
                modifier = Modifier.fillMaxSize(),
                bottomPad = { if (panelUp) with(density) { dockPx.toDp() } + 8.dp else BottomBarClearance },
            )
            AnimatedVisibility(
                visible = panelUp,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(liveSpec(reduce)) { it } + fadeIn(liveSpec(reduce)),
                exit = slideOutVertically(liveSpec(reduce)) { it } + fadeOut(liveSpec(reduce, ReefMotion.FAST)),
            ) {
                SandboxDock(
                    panel, actions,
                    Modifier.fillMaxWidth().onSizeChanged { dockPx = it.height },
                    side = false, bottomClearance = clearance, bodyHeight = bodyHeight,
                )
            }
        }
        // the only control left while everything is hidden
        AnimatedVisibility(
            visible = chromeHidden,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp),
            enter = fadeIn(liveSpec(reduce)) + slideInVertically(liveSpec(reduce)) { -it / 2 },
            exit = fadeOut(liveSpec(reduce, ReefMotion.FAST)),
        ) {
            ShowControlsPill(onClick = { tick(); chromeHidden = false })
        }
        if (lapse && sandbox) {
            TimelapseSheet(
                state = lapseState, water = hud.water,
                onStart = vm::startTimelapse, onCancel = vm::cancelTimelapse, onNew = vm::clearTimelapse,
                onDismiss = { lapse = false },
            )
        }
    }
    ask?.let { a ->
        ReefDialog(
            title = a.title,
            body = a.body,
            confirm = a.confirmLabel,
            onConfirm = { ask = null; a.run() },
            onDismiss = { ask = null },
            destructive = true,
        )
    }
}

/** Show / hide state of the overlay controls and the first-time tips, handed to the tank layer. */
private class ChromeState(
    val hidden: Boolean,
    val onHide: () -> Unit,
    val showTips: Boolean,
    val onDismissTips: () -> Unit,
)

/**
 * Watches touches on the tank without taking them (the GL view still gets every event): reports true once a finger
 * has dragged past the touch slop (or a second finger lands), false when all fingers lift. Plain taps report nothing.
 */
private fun Modifier.watchTankDrags(onDragging: (Boolean) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val slop = viewConfiguration.touchSlop
        var dragging = false
        var travelled = 0f
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (!dragging) {
                val primary = event.changes.firstOrNull { it.id == down.id }
                if (primary != null) travelled += (primary.position - primary.previousPosition).getDistance()
                if (travelled > slop || event.changes.count { it.pressed } > 1) {
                    dragging = true
                    onDragging(true)
                }
            }
            if (event.changes.none { it.pressed }) break
        }
        if (dragging) onDragging(false)
    }
}

/** The 3D tank with the HUD, follow overlay, fish card and tips layered on top. */
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
    onPick: (String?) -> Unit,
    chrome: ChromeState,
    onTankTouch: ((Boolean) -> Unit)?,
    modifier: Modifier,
    bottomPad: () -> Dp,
) {
    var micro by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = micro) { micro = false }
    val latestTouch by rememberUpdatedState(onTankTouch)
    Box(modifier) {
        TankView(
            snapshots = vm.snapshots,
            modifier = Modifier.fillMaxSize().watchTankDrags { latestTouch?.invoke(it) },
            followId = if (cleaning) null else followId,
            quality = quality.toRender(),
            onPick = onPick,
            onTapGlass = vm::tapGlass,
            onFollowLost = { vm.follow(null) },
            onFollow = vm::follow,
            cleaning = cleaning,
            onScrape = onScrape,
        )
        HudLayer(
            vm, hudState, onModeChange, followId, stats, soundOn, onToggleSound, cleaning, onToggleCleaning,
            onMicro = { micro = true }, chrome = chrome, bottomPad = bottomPad,
        )
        AnimatedVisibility(
            visible = micro,
            enter = fadeIn(liveSpec(ReefTheme.reduceMotion)),
            exit = fadeOut(liveSpec(ReefTheme.reduceMotion, ReefMotion.FAST)),
        ) {
            // the microscope covers the tank view; doses and treatments only in the sandbox
            val hud by vm.hud.collectAsStateWithLifecycle()
            MicroscopeView(
                state = hud.microfauna,
                simTimeSec = hud.simTimeSec,
                onClose = { micro = false },
                modifier = Modifier.fillMaxSize().background(ReefTheme.colors.bg).statusBarsPadding().padding(bottom = bottomPad()),
                onAction = if (hudState.mode == HudMode.SANDBOX) vm::micro else null,
            )
        }
    }
}

/**
 * Everything drawn over the tank. Kept in its own composable so the panel height ([bottomPad], read here) only
 * recomposes this layer while the panel animates, not the GL view.
 */
@Composable
private fun HudLayer(
    vm: LiveTankViewModel,
    hudState: HudState,
    onModeChange: (HudMode) -> Unit,
    followId: String?,
    stats: OrganismStats?,
    soundOn: Boolean,
    onToggleSound: () -> Unit,
    cleaning: Boolean,
    onToggleCleaning: () -> Unit,
    onMicro: () -> Unit,
    chrome: ChromeState,
    bottomPad: () -> Dp,
) {
    val reduce = ReefTheme.reduceMotion
    var feedOpen by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = feedOpen) { feedOpen = false }
    val haptics = rememberHaptics()
    val onFeed: (FoodKind) -> Unit = { k -> vm.feed(k, k.typicalDoseGrams) }
    Box(Modifier.fillMaxSize().statusBarsPadding().padding(bottom = bottomPad())) {
        AnimatedVisibility(
            visible = !chrome.hidden,
            enter = fadeIn(liveSpec(reduce)) + slideInVertically(liveSpec(reduce)) { -it / 6 },
            exit = fadeOut(liveSpec(reduce, ReefMotion.FAST)) + slideOutVertically(liveSpec(reduce)) { -it / 6 },
        ) {
            LiveOverlay(
                state = hudState,
                onModeChange = onModeChange,
                onSpeedChange = vm::setSpeed,
                showSpeed = false,   // speed lives in the sandbox dock (reality always runs at 1x)
                soundOn = soundOn,
                onToggleSound = onToggleSound,
                cleaning = cleaning,
                onToggleCleaning = onToggleCleaning,
                onMicro = onMicro,
                onHideChrome = chrome.onHide,
                feedOpen = feedOpen,
                onToggleFeed = { haptics.tick(); feedOpen = !feedOpen },
            )
        }
        val st = stats
        // keep the last card on screen while it animates out
        var lastCard by remember { mutableStateOf<OrganismStats?>(null) }
        if (st != null) lastCard = st
        val following = followId != null && st != null && !cleaning
        if (following && st != null) {
            val ids = vm.hud.value.stats.map { it.id }
            val i = ids.indexOf(st.id).coerceAtLeast(0)
            fun step(d: Int) { if (ids.isNotEmpty()) vm.follow(ids[(i + d + ids.size) % ids.size]) }
            FollowOverlay(st.toFollowState(i, ids.size), { step(-1) }, { step(1) }, { vm.follow(null) })
        }
        // the food tray floats over the bottom of the water, above the fish card; it closes with the controls
        AnimatedVisibility(
            visible = feedOpen && !chrome.hidden && !cleaning,
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 16.dp),
            enter = fadeIn(liveSpec(reduce)) + slideInVertically(liveSpec(reduce)) { it / 3 },
            exit = fadeOut(liveSpec(reduce, ReefMotion.FAST)) + slideOutVertically(liveSpec(reduce)) { it / 3 },
        ) {
            FoodTray(onFeed = onFeed, onClose = { feedOpen = false })
        }
        AnimatedVisibility(
            visible = cleaning,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            enter = fadeIn(liveSpec(reduce)) + slideInVertically(liveSpec(reduce)) { it / 2 },
            exit = fadeOut(liveSpec(reduce, ReefMotion.FAST)) + slideOutVertically(liveSpec(reduce)) { it / 2 },
        ) {
            CleanGlassHint()
        }
        AnimatedVisibility(
            visible = !cleaning && !following && st != null && !feedOpen,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            enter = fadeIn(liveSpec(reduce)) + slideInVertically(liveSpec(reduce)) { it / 3 },
            exit = fadeOut(liveSpec(reduce, ReefMotion.FAST)) + slideOutVertically(liveSpec(reduce)) { it / 3 },
        ) {
            val card = st ?: lastCard
            if (card != null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FishCard(card.toFishCardState(), onFollow = { vm.follow(card.id) }, onClose = { vm.select(null) })
                }
            }
        }
        AnimatedVisibility(
            visible = chrome.showTips && !cleaning && st == null && !feedOpen,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            enter = fadeIn(liveSpec(reduce)) + slideInVertically(liveSpec(reduce)) { it / 3 },
            exit = fadeOut(liveSpec(reduce, ReefMotion.FAST)) + slideOutVertically(liveSpec(reduce)) { it / 3 },
        ) {
            SandboxTipsCard(onDismiss = chrome.onDismissTips)
        }
    }
}
