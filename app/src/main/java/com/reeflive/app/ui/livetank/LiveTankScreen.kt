package com.reeflive.app.ui.livetank

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameNanos
import com.reeflive.app.render.CinemaSession
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import com.reeflive.app.tv.ScreensaverActivity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reeflive.app.appGraph
import com.reeflive.core.radio.RadioStation
import com.reeflive.app.audio.TankAudio
import com.reeflive.app.audio.TankRadio
import com.reeflive.app.ui.live.RecordShelf
import com.reeflive.app.data.repo.AppSettings
import com.reeflive.app.live.SimMode
import com.reeflive.app.render.FollowCamControl
import com.reeflive.app.render.GlassStroke
import com.reeflive.app.render.RenderQuality
import com.reeflive.app.render.BurstKind
import com.reeflive.app.render.TankBurst
import com.reeflive.app.render.TankBursts
import com.reeflive.app.render.SupplyShelfControl
import com.reeflive.app.render.AimHit
import com.reeflive.app.render.TankAimControl
import com.reeflive.app.ui.live.SpotTap
import com.reeflive.app.ui.live.TankSpotPopup
import com.reeflive.core.sim.TankSpot
import com.reeflive.core.sim.TankSpots
import com.reeflive.app.render.CareAct
import com.reeflive.app.render.CareJob
import com.reeflive.app.render.CareShowControl
import com.reeflive.app.render.TankProjection
import com.reeflive.app.render.ViewLight
import com.reeflive.app.render.TankView
import com.reeflive.app.ui.components.BottomBarClearance
import com.reeflive.app.ui.components.LocalSnackbar
import com.reeflive.app.ui.components.rememberHaptics
import com.reeflive.app.ui.live.ActionBanner
import com.reeflive.app.ui.live.ActionBannerState
import com.reeflive.app.ui.live.ActionFxLayer
import com.reeflive.app.ui.live.Feedback
import com.reeflive.app.ui.live.FxKind
import com.reeflive.app.ui.live.ActionFxState
import com.reeflive.app.ui.live.FastForwardBadge
import com.reeflive.app.ui.live.TorchOverlay
import com.reeflive.app.ui.live.TorchState
import com.reeflive.app.ui.live.trackTorch
import com.reeflive.app.ui.live.AddableSpecies
import com.reeflive.app.ui.live.Candy
import com.reeflive.app.ui.live.CleanGlassHint
import com.reeflive.app.ui.live.ClockPill
import com.reeflive.app.ui.live.DayPhase
import com.reeflive.app.ui.live.FishCard
import com.reeflive.app.ui.live.FishSummary
import com.reeflive.app.ui.live.FollowOverlay
import com.reeflive.app.ui.live.GaugeDial
import com.reeflive.app.ui.live.GaugeReadings
import com.reeflive.app.ui.live.FxPoint
import com.reeflive.app.ui.live.HappyMeterPill
import com.reeflive.app.ui.live.HermieMood
import com.reeflive.app.ui.live.HermiePill
import com.reeflive.app.ui.live.HermieState
import com.reeflive.app.ui.live.HudMode
import com.reeflive.app.ui.live.MyTankSetupCard
import com.reeflive.app.ui.live.CareCard
import com.reeflive.app.ui.live.MoneyChip
import com.reeflive.app.ui.live.StoreOverlay
import com.reeflive.app.ui.live.TryOnBar
import com.reeflive.app.ui.live.RewardFxLayer
import com.reeflive.app.ui.live.RewardFxState
import com.reeflive.app.ui.live.WelcomeBackCard
import com.reeflive.core.custom.economy.EconomyView
import com.reeflive.app.ui.live.HudState
import com.reeflive.app.ui.live.LiveCategory
import com.reeflive.app.ui.live.LiveDialog
import com.reeflive.app.ui.live.LiveMenu
import com.reeflive.app.ui.live.LivePill
import com.reeflive.app.ui.live.LiveReactions
import com.reeflive.app.ui.live.ToiletFlush
import com.reeflive.app.ui.live.LiveTheme
import com.reeflive.app.ui.live.LiveTaps
import com.reeflive.app.ui.live.LiveTools
import com.reeflive.app.ui.live.MicroscopeView
import com.reeflive.app.ui.live.ModeToggle
import com.reeflive.app.ui.live.Reaction
import com.reeflive.app.ui.live.BacteriaOverlay
import com.reeflive.app.ui.live.SandboxActions
import com.reeflive.app.ui.live.SandboxControlMode
import com.reeflive.app.ui.live.SandboxFriendly
import com.reeflive.app.ui.live.SandboxPanelState
import com.reeflive.app.ui.live.SandboxParamDef
import com.reeflive.app.ui.live.SandboxParams
import com.reeflive.app.ui.live.SandboxPreset
import com.reeflive.app.ui.live.SandboxTipsCard
import com.reeflive.app.ui.live.SelectedAnimalUi
import com.reeflive.app.ui.live.ShelfPick
import com.reeflive.app.ui.live.ShelfRow
import com.reeflive.app.ui.live.SupplyCarryLayer
import com.reeflive.app.ui.live.BagCarry
import com.reeflive.app.ui.live.BagCarryLayer
import com.reeflive.app.ui.live.BagTimerLayer
import com.reeflive.app.ui.live.CoralPickLayer
import com.reeflive.app.ui.live.SupplyItem
import com.reeflive.app.ui.live.SupplyShelfOverlay
import com.reeflive.app.ui.live.SubmarineControls
import com.reeflive.app.ui.live.TankGauge
import com.reeflive.app.ui.live.TankGaugeLayer
import com.reeflive.app.ui.live.ShowControlsPill
import com.reeflive.app.ui.live.StockUi
import com.reeflive.app.ui.live.TankReading
import com.reeflive.app.ui.live.StoreBubbleLayer
import com.reeflive.app.ui.live.ThoughtBubbleLayer
import com.reeflive.app.ui.live.TradeCard
import com.reeflive.app.ui.live.TimelapseSheet
import com.reeflive.app.ui.live.WaterTap
import com.reeflive.app.ui.live.liveSpec
import com.reeflive.app.ui.live.tankHappiness
import com.reeflive.app.ui.live.toFishCardState
import com.reeflive.app.ui.live.toFollowState
import com.reeflive.app.ui.live.waterReadouts
import com.reeflive.app.ui.live.testKitReadouts
import com.reeflive.app.ui.live.WaterReadout
import com.reeflive.app.ui.nav.BottomBarMaxWidth
import com.reeflive.app.ui.nav.reefViewModel
import com.reeflive.app.ui.theme.ReefMotion
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.AiptasiaTreatment
import com.reeflive.core.sim.PestController
import com.reeflive.core.sim.CoralKind
import com.reeflive.core.sim.CoralHealth
import com.reeflive.core.sim.DoseCheck
import com.reeflive.core.sim.DryRockShape
import com.reeflive.app.ui.live.AquascapeBar
import com.reeflive.app.ui.live.ScapeTool
import com.reeflive.core.sim.FoodKind
import com.reeflive.core.sim.CareFix
import com.reeflive.core.live.ReefFacts
import com.reeflive.core.sim.TankDoctor
import com.reeflive.core.sim.MaintenanceTask
import com.reeflive.core.sim.MicroAction
import com.reeflive.core.sim.SupplyPreview
import com.reeflive.core.sim.Mood
import com.reeflive.core.sim.OrganismStats
import com.reeflive.core.sim.TankDims
import com.reeflive.core.sim.TouchSurface
import com.reeflive.core.sim.timelapse.TimelapseState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun LiveQuality.toRender() = when (this) {
    LiveQuality.LOW -> RenderQuality.LOW
    LiveQuality.MEDIUM -> RenderQuality.MEDIUM
    LiveQuality.HIGH -> RenderQuality.HIGH
}

/** Height of the floating bottom bar area (64 dp pill + 16 dp margin) plus a little air. */
private val NavClearance = 88.dp
/** Height of the HUD's two top rows (mode / clock, then Hermie and the meter): the in-tank gauges stay below it. */
private val GaugeTopClearance = 112.dp

/** Point on the front glass, centre, for the "Tap glass" action (tank cm: [TankDims], 122 × 40.6 × 45.7). */
private val FrontGlassCentre: Vec3 get() = Vec3(com.reeflive.app.render.Camera.TANK[0].toDouble() / 2, 20.0, com.reeflive.app.render.Camera.TANK[2].toDouble())

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
/** The app-open reef fact was shown in this launch (once per process, not on every return to the Live tab). */
private var factShown = false

private data class ConfirmAsk(val title: String, val body: String, val confirmLabel: String, val run: () -> Unit)

/** Portrait dock height above the app bar (bubble 60 dp + label + padding), and with the dock tucked to one bubble. */
private val DockHeight = 112.dp
private val DockTucked = 72.dp

/**
 * Live Tank (UI_STYLE §10): the GL [TankView] always fills the screen; the menu ([LiveMenu]) floats over it as a slim
 * rail of candy bubbles down the left edge in landscape, or a dock of the same bubbles above the bottom bar in
 * portrait, and its cards are compact glass that close on their own. Every action plays a short picture in the tank
 * ([ActionFxLayer], projected through the camera) and sends its command when the picture reaches the water; a tap on
 * the tank skips the picture (the command is sent at once). Hermie and the happy meter sit as small pills at the top.
 * More → "Hide buttons" hides every control so only the tank shows; a tap on the water brings them back, and a tap on
 * the water never hides anything (UI_STYLE §10.8). Sim runs only while RESUMED.
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
    // in-tank action pictures, the camera projection they are drawn through, and Hermie
    val fx = remember { ActionFxState() }
    val projection = remember { TankProjection() }
    // in-tank 3D feedback (dye plumes, bubbles, pump jets) and the banner naming what is happening
    val bursts = remember { TankBursts() }
    // the follow camera's on-screen buttons (zoom, swing round): FollowOverlay presses, the renderer drains
    val followCam = remember { FollowCamControl() }
    val banner = remember { ActionBannerState() }
    // corals that bleached, died or budded while the app was closed (My tank catch-up)
    val coralNews by vm.coralNews.collectAsStateWithLifecycle()
    LaunchedEffect(coralNews) {
        if (coralNews.isNotEmpty()) { banner.show(Feedback("Your corals while you were away", coralNews.joinToString(" ")), Candy.green); vm.clearCoralNews() }
    }
    val hermie = remember { HermieState() }
    // tank sounds: created with the screen, released when it leaves composition, paused with the lifecycle
    val audio = remember { TankAudio(context.applicationContext) }
    DisposableEffect(audio) { onDispose { audio.release() } }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onResume(); audio.onResume() }
    // pending pictures send their commands before the sim pauses, so nothing is lost
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { fx.flush(); vm.onPause(); audio.onPause() }
    LaunchedEffect(settings.loaded, settings.tankSounds, settings.tankVolume) {
        if (settings.loaded) audio.setUserSettings(settings.tankSounds, settings.tankVolume)
    }
    LaunchedEffect(audio) {
        // full-rate snapshots: the throttled HUD flow would drop events
        vm.snapshots.collect { snap -> audio.onSnapshot(snap, powerheadShare(vm.extras.value)) }
    }
    // the table radio beside the tank: quiet background songs, on its own switch
    val radio = remember { TankRadio(context.applicationContext) { next -> scope.launch { settingsRepo.setRadioTrack(next) } } }
    DisposableEffect(radio) { onDispose { radio.release() } }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { radio.onResume() }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { radio.onPause() }
    val soundOn = settings.tankSounds
    val toggleSound: () -> Unit = { scope.launch { settingsRepo.setTankSounds(!soundOn) } }
    val tick: () -> Unit = audio::uiTick
    val haptics = rememberHaptics()
    var flushing by remember { mutableStateOf<com.reeflive.core.sim.NetFlush?>(null) }
    val hud by vm.hud.collectAsStateWithLifecycle()
    val mode by vm.mode.collectAsStateWithLifecycle()
    val speed by vm.speed.collectAsStateWithLifecycle()
    val lifePace by vm.lifePace.collectAsStateWithLifecycle()
    val quality by vm.quality.collectAsStateWithLifecycle()
    val followId by vm.followId.collectAsStateWithLifecycle()
    val stats by vm.selectedStats.collectAsStateWithLifecycle()
    val real by vm.real.collectAsStateWithLifecycle()
    val lightPct by vm.lightPct.collectAsStateWithLifecycle()
    val predation by vm.predation.collectAsStateWithLifecycle()
    val extras by vm.extras.collectAsStateWithLifecycle()
    val lapseState by vm.timelapse.collectAsStateWithLifecycle()
    var lapse by rememberSaveable { mutableStateOf(false) }

    // menu: the open card (null = none) and whether the rail / dock is out or tucked to one bubble
    var open by rememberSaveable { mutableStateOf<LiveCategory?>(null) }
    var railShown by rememberSaveable { mutableStateOf(true) }
    var hermieOn by rememberSaveable { mutableStateOf(true) }
    var meterOn by rememberSaveable { mutableStateOf(true) }
    // More → "Thought bubbles": what a few animals are thinking, over the 3D view (off by default: a calm tank)
    var thoughtsOn by rememberSaveable { mutableStateOf(false) }
    var micro by rememberSaveable { mutableStateOf(false) }
    var preset by rememberSaveable { mutableStateOf<SandboxPreset?>(null) }
    var controlMode by rememberSaveable { mutableStateOf(SandboxControlMode.SET) }
    // Watch card: lamp look (render only), torch, auto tour; a Watch-mode time-lapse runs on a sandbox fork
    var viewLight by rememberSaveable { mutableStateOf(ViewLight.REAL) }
    var torchOn by rememberSaveable { mutableStateOf(false) }
    var touring by rememberSaveable { mutableStateOf(false) }
    var lapseFork by rememberSaveable { mutableStateOf(false) }
    val torch = remember { TorchState() }
    // "sandbox" = every lever works: the pretend tank and My tank (the keeper's own saved tank)
    val sandbox = mode != SimMode.REALITY
    val myTank = mode == SimMode.CUSTOM
    // the menu's levers: My tank only (Watch's fast-forward copy keeps Watch's menu)
    val levers = sandbox && !lapseFork
    val customInfo by vm.custom.collectAsStateWithLifecycle()
    // My tank's sand dollars (docs/ECONOMY.md): the chip in the HUD opens the Care card; Tank Health counts as testing
    val economy by vm.economy.collectAsStateWithLifecycle()
    // records bought in My tank join the radio everywhere (kept in settings too, so Watch mode plays them)
    val boughtRecords = economy?.records.orEmpty()
    LaunchedEffect(boughtRecords) { if (!settings.radioOwned.containsAll(boughtRecords)) settingsRepo.addRadioOwned(boughtRecords) }
    val owned = settings.radioOwned + boughtRecords
    val radioTrack = RadioStation.playableOr(settings.radioTrack, owned)
    LaunchedEffect(settings.loaded, settings.radioOn, radioTrack, settings.radioVolume, owned) {
        if (settings.loaded) radio.set(settings.radioOn, radioTrack, settings.radioVolume, owned)
    }
    var recordsOpen by remember { mutableStateOf(false) }
    var careOpen by rememberSaveable { mutableStateOf(false) }
    // walking a fish store's display tanks (docs/ECONOMY.md §5): the 3D view shows the store's tank meanwhile
    val visit by vm.visit.collectAsStateWithLifecycle()
    var storePick by remember { mutableStateOf<String?>(null) }
    // the room shop's try-on: a stand (or colour, extra) shown on the tank with a Buy bar
    val roomShop by vm.roomShop.collectAsStateWithLifecycle()
    var trying by remember { mutableStateOf<com.reeflive.core.custom.room.Decor.Tile?>(null) }
    LaunchedEffect(myTank) { if (!myTank && trying != null) { trying = null; vm.decorTry(null) } }
    // taps in the 3D shop: another display tank walks there, the counter opens the till
    var counterRequest by remember { mutableIntStateOf(0) }
    DisposableEffect(vm.storeRoom) {
        val room = vm.storeRoom
        room.onStand = { i -> storePick = null; vm.showStoreTank(i) }
        room.onCounter = { counterRequest++ }
        room.onTrade = { vm.showTrade() }
        onDispose { room.onStand = null; room.onCounter = null; room.onTrade = null }
    }
    val rewardFx = remember { RewardFxState() }

    // every overlay control hidden, only the tank: only More → "Hide buttons" does this; a tap on the water, the
    // "Show buttons" pill or Back undo it
    var chromeHidden by rememberSaveable { mutableStateOf(false) }
    // first-time tips, one card for Watch and one for Sandbox (each remembered in settings), and again from
    // More → "What can I do?"
    var tipsAsked by rememberSaveable { mutableStateOf(false) }
    val hintSeen = if (sandbox) settings.sandboxHintSeen else settings.watchHintSeen
    val showTips = !chromeHidden && (tipsAsked || (settings.loaded && !hintSeen))
    val dismissTips: () -> Unit = {
        tipsAsked = false
        if (sandbox && !settings.sandboxHintSeen) scope.launch { settingsRepo.setSandboxHintSeen(true) }
        if (!sandbox && !settings.watchHintSeen) scope.launch { settingsRepo.setWatchHintSeen(true) }
    }
    var ask by remember { mutableStateOf<ConfirmAsk?>(null) }
    // a reef fact or reef-keeping tip each time the app opens (core ReefFacts), never the same one until all were shown
    var fact by remember { mutableStateOf<ReefFacts.Fact?>(null) }
    LaunchedEffect(Unit) {
        if (factShown) return@LaunchedEffect
        factShown = true
        val prefs = context.getSharedPreferences("reeflive_facts", android.content.Context.MODE_PRIVATE)
        val n = prefs.getLong("n", (0L..999L).random())
        prefs.edit().putLong("n", n + 1).apply()
        delay(1500)
        fact = ReefFacts.nth(n)
    }

    // Back undoes whatever is on top (the handler registered last wins): the tips card, a picked fish's card (follow
    // mode has its own Back in FollowOverlay), an open menu card, hidden buttons, then the modes below
    BackHandler(enabled = showTips) { dismissTips() }
    BackHandler(enabled = stats != null && followId == null) { vm.select(null) }
    BackHandler(enabled = open != null) { open = null }
    BackHandler(enabled = chromeHidden) { chromeHidden = false }
    BackHandler(enabled = micro) { micro = false }
    // clean-glass mode (both modes): drag the Flipper over the front glass; Back or "Done" leaves it
    var cleaning by rememberSaveable { mutableStateOf(false) }
    // aquascape builder (Tools → Build rockwork): taps on the tank drop or take out rocks
    var scaping by rememberSaveable { mutableStateOf(false) }
    var scapeShape by rememberSaveable { mutableStateOf(DryRockShape.BOULDER) }
    var scapeTool by rememberSaveable { mutableStateOf(ScapeTool.DROP) }
    var scapeTurn by rememberSaveable { mutableStateOf(0) }
    BackHandler(enabled = scaping) { scaping = false }
    BackHandler(enabled = cleaning) { cleaning = false }
    // Submarine mode (SubmarineControls): a toy sub the fish react to; Back docks it
    val subUi by vm.submarine.collectAsStateWithLifecycle()
    BackHandler(enabled = subUi.on) { vm.setSubmarine(subUi.copy(on = false)) }

    // Supplies mode (UI_STYLE §10.6): the camera looks at the 3D supply shelf beside the tank; a picked prop is then
    // carried back into the tank view and used where it is let go
    val shelf = remember { SupplyShelfControl() }
    var supplies by rememberSaveable { mutableStateOf(false) }
    var shelfPick by remember { mutableStateOf<ShelfPick?>(null) }
    var testKit by remember { mutableStateOf(false) }
    // what the shelf test kit read when it was last picked (a hobby kit's rounded reading: core TestKit)
    var kitReadouts by remember { mutableStateOf<List<WaterReadout>>(emptyList()) }
    // doses given in the last sim day, so a second dose the same day is flagged as a swing (core DoseCheck)
    val doseLog = remember { DoseCheck.Log() }
    var carry by remember { mutableStateOf<SupplyItem?>(null) }
    // the shop bag in hand after an animal is picked in the Shop (dragged onto the tank to float)
    var bagCarry by remember { mutableStateOf<BagCarry?>(null) }
    /** The keeper tapped "Later" on the coral spot picker (a pill brings it back). */
    var coralPickHidden by remember { mutableStateOf(false) }
    var pickSerial by remember { mutableIntStateOf(0) }
    val pickJob = remember { arrayOfNulls<Job>(1) }
    // "Tap a supply, then drag it to the tank" until the first supply lands in the tank (remembered in settings)
    var supplyHintDone by remember { mutableStateOf(false) }
    val showSupplyHint = settings.loaded && !settings.supplyHintSeen && !supplyHintDone
    BackHandler(enabled = supplies) { supplies = false }
    BackHandler(enabled = carry != null) { carry = null; supplies = true }
    BackHandler(enabled = bagCarry != null) { bagCarry = null; open = LiveCategory.LIFE }
    BackHandler(enabled = visit != null) { storePick = null; vm.leaveStore() }
    // in-tank gauges (§10.7): the heater's set point and state are tracked here (the snapshot has no heater read-out)
    var heaterSet by rememberSaveable { mutableDoubleStateOf(HEATER_DEFAULT_C) }
    var heaterWorking by rememberSaveable { mutableStateOf(true) }
    // Fix tank: the 3D upkeep show and the fixes still queued or playing in it
    val careShow = remember { CareShowControl() }
    // touch controls: a tap on a piece of gear pops out its menu right there (core TankSpots); food carried from the
    // shelf goes in exactly where it is let go or tapped (TankAimControl finds the fish, coral, rock or sand there)
    val aim = remember { TankAimControl() }
    var spotTap by remember { mutableStateOf<SpotTap?>(null) }
    var spotSerial by remember { mutableIntStateOf(0) }
    var aimFood by remember { mutableStateOf<FoodKind?>(null) }
    val fixQueued = remember { mutableStateListOf<TankDoctor.Kind>() }
    var dial by remember { mutableStateOf<TankGauge?>(null) }
    val reduceMotion = ReefTheme.reduceMotion
    // a store visit starts on the sidewalk and walks in the front door, unless Reduce Motion is on
    LaunchedEffect(reduceMotion) { vm.storeRoom.walkIn = !reduceMotion }
    SideEffect {
        shelf.locked = SupplyItem.lockedIds(sandbox)
        shelf.setOpen(supplies, reduceMotion)
    }

    /**
     * Plays [r]'s picture, names the action and its effect in the banner, Hermie says his line, and [run] sends the
     * command when the picture reaches the water, together with the action's 3D feedback in the tank.
     */
    fun cue(r: Reaction, run: (() -> Unit)? = null) {
        fx.play(r.fx) {
            run?.invoke()
            r.feedback.bursts.forEach(bursts::emit)
        }
        banner.show(r.feedback, bannerTone(r))
        if (hermieOn) hermie.react(r)
    }
    /** A picture for a change that was already sent (sliders): keyed ones play once per drag (the banner value stays live). */
    fun echo(r: Reaction?) {
        if (r == null) return
        val key = r.fx.key
        if (key != null && fx.isPlaying(key)) return
        fx.play(r.fx)
        r.feedback.bursts.forEach(bursts::emit)
        banner.show(r.feedback, bannerTone(r))
        if (hermieOn) hermie.react(r)
    }

    val toggleCleaning: () -> Unit = {
        tick()
        if (!cleaning) vm.select(null)
        cleaning = !cleaning
        open = null
        echo(LiveReactions.cleanGlass(cleaning))
    }
    val onScrape: (GlassStroke) -> Unit = { s ->
        vm.scrapeGlass(s)
        audio.scrape(s.lengthCm)
    }
    // a tap that only skipped a picture should not also hide the controls
    val skippedAt = remember { LongArray(1) }
    /** What a tap with no fish under it does now (LiveTaps). Any tap on the tank ends the auto tour first. */
    fun waterTap(): WaterTap {
        if (touring) { touring = false; vm.follow(null) }
        return LiveTaps.emptyWater(
            justSkipped = SystemClock.uptimeMillis() - skippedAt[0] < SKIP_PICK_GUARD_MS,
            controlsHidden = chromeHidden,
            following = vm.followId.value != null,
            cardOpen = open != null,
            fishPicked = vm.selectedOrganismId.value != null,
        )
    }
    /** Undoes what [tap] names (TOUCH is the caller's: it needs the point). */
    fun undoTap(tap: WaterTap) {
        when (tap) {
            WaterTap.SHOW_CONTROLS -> chromeHidden = false
            WaterTap.CLOSE_CARD -> open = null
            WaterTap.DESELECT -> vm.select(null)
            WaterTap.NOTHING, WaterTap.TOUCH -> Unit
        }
    }
    // a tap on a fish picks it (and brings hidden buttons back so its card can show); a tap anywhere else undoes what
    // is open (LiveTaps), else touches the tank (onTouchTank). It never hides the buttons or drops a followed fish.
    val onPick: (String?) -> Unit = { id ->
        val v = visit
        if (v != null) {
            // in a store a tap on an animal opens its card
            if (id != null) { haptics.tick(); v.speciesOf[id]?.let { storePick = it } }
        } else if (subUi.on) {
            // in the sub a tap on a fish sends it over to keep that fish company; empty water does nothing
            if (id != null) { tick(); vm.watchSubmarine(id, hud.submarine?.watchMode ?: com.reeflive.core.sim.SubWatch.HOVER) }
        } else if (id != null) {
            if (touring) { touring = false; vm.follow(null) }
            chromeHidden = false
            haptics.tick()       // every touch answers: a tick here, a ring around the animal in the tank (TankRenderer.touchRing)
            vm.select(id)
            vm.touchAnimal(id)   // a snail or crab tapped right on tucks in; a fish only gets picked (TankTouch.animalReaction)
        } else {
            undoTap(waterTap())
        }
    }
    // a fingertip on the tank with nothing open to undo (UI_STYLE §10.8): a soft knock and a light buzz, a ripple on
    // the glass (a puff on the sand), and the animals nearby answer it in the sim (Command.TouchTank)
    val onTouchTank: (Vec3, TouchSurface) -> Unit = { p, surface ->
        val tap = waterTap()
        if (scaping) {
            haptics.tick()
            if (scapeTool == ScapeTool.TAKE_OUT) vm.removeRock(p)
            else { vm.placeRock(scapeShape, p, surface, scapeTurn.toDouble()); audio.knock(0.5f) }
        } else if (tap == WaterTap.TOUCH) {
            vm.touchTank(p, surface)
            val glass = surface == TouchSurface.GLASS
            audio.knock(if (glass) 1f else 0.7f)
            haptics.tick()
            val kind = when (surface) {
                TouchSurface.SAND -> BurstKind.SAND_PUFF
                TouchSurface.SURFACE -> BurstKind.SURFACE_RIPPLE
                else -> BurstKind.GLASS_RIPPLE
            }
            bursts.emit(TankBurst(kind, p.x.toFloat(), p.y.toFloat(), p.z.toFloat(), amount = if (glass) 1f else 0.7f))
        } else {
            undoTap(tap)
        }
    }
    // a fingertip held on the glass or the water's top (UI_STYLE §10.8): slide it and bold fish follow, rest it and they
    // nip. The same #53 rule as a tap: with something open to undo, the hold only undoes it and the finger is ignored
    val fingerIgnored = remember { BooleanArray(1) }
    val onFinger: (Vec3?, Float, TouchSurface) -> Unit = { p, speed, surface ->
        if (p == null) {
            if (!fingerIgnored[0]) vm.finger(null, 0f, surface)
            fingerIgnored[0] = false
        } else if (!fingerIgnored[0]) {
            val down = !vm.fingerDown
            if (down) {
                val tap = waterTap()
                if (tap != WaterTap.TOUCH) { undoTap(tap); fingerIgnored[0] = true }
            }
            if (!fingerIgnored[0]) {
                vm.finger(p, speed, surface)
                if (down) { haptics.tick(); audio.knock(0.4f) }
            }
        }
    }
    // the held finger flicked as it lifted: a puff of water that sways the corals, the nori and the sand
    val onFlick: (Vec3, Vec3) -> Unit = { p, v -> vm.puff(p, v) }
    // long press on the tank: the usual pinch of food dropped right there
    val onLongPress: (Vec3) -> Unit = { p ->
        tick()
        vm.feed(FoodKind.PELLET, FoodKind.PELLET.typicalDoseGrams, p)
        val at = FxPoint(p.x.toFloat(), p.y.toFloat(), p.z.toFloat())
        val fb = Feedback.feeding(FoodKind.PELLET, at, "A pinch of pellets here")
        fb.bursts.forEach(bursts::emit)
        banner.show(fb, Candy.orange)
    }
    // the two-finger tap on the glass (sent at once: it is the finger itself)
    val onTankTap: (Vec3) -> Unit = { p ->
        vm.tapGlass(p)
        echo(LiveReactions.tapGlass(FxPoint(p.x.toFloat(), p.y.toFloat(), p.z.toFloat())))
    }

    /** Runs nothing more; offers "Undo" on the app snackbar for a command that has an opposite. */
    val offerUndo: (String, () -> Unit) -> Unit = { message, undo ->
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar(message, actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) { tick(); undo() }
        }
    }

    val hudState = HudState(
        // Watch's fast-forward copy (lapseFork) still reads as Watch: there is no Sandbox tab any more
        mode = if (mode == SimMode.CUSTOM) HudMode.MY_TANK else HudMode.WATCH,
        speed = speed,
        simTimeSec = hud.simTimeSec,
        dayPhase = DayPhase.entries.firstOrNull { it.label == hud.dayPhase } ?: DayPhase.DAY,
        readouts = waterReadouts(hud.water, hud.lightIntensity * 100.0, hud.flowStrength.toDouble()),
    )
    val happy = tankHappiness(hud.stats, hudState.readouts)

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

    // sliders and on/off rows: the command goes at once (a drag sends many), the picture plays once per drag
    val onSet: (SandboxParamDef, Double) -> Unit = { def, v ->
        val now = panel.now
        val old = def.valueIn(now)
        // on/off rows (pumps, equipment, test switches) always switch now: a ramp or spike would leave them half-done
        val onOff = def.max - def.min <= def.step
        def.simId?.let { id -> vm.applyParam(def.id, id, v / def.scale, old / def.scale, if (onOff) SandboxControlMode.SET else controlMode) }
        if (def.max - def.min <= def.step) tick()   // on/off rows (pumps) click; dragged sliders stay quiet
        if (sandbox) echo(LiveReactions.param(def.id, old, v))
    }
    val actions = SandboxActions(
        onSet = onSet,
        onSpeed = { x -> tick(); vm.setSpeed(x); echo(LiveReactions.speed(x)) },
        onControlMode = { controlMode = it },
        onPreset = { p ->
            tick()
            preset = p
            cue(LiveReactions.preset(p)) { vm.runPreset(p) }
        },
        onDismissPreset = { preset = null },
        onFeed = { k -> tick(); cue(LiveReactions.feed(k)) { vm.feed(k, k.typicalDoseGrams) } },
        onTapGlass = { tick(); cue(LiveReactions.tapGlass()) { vm.tapGlass(FrontGlassCentre) } },
        onWaterChange = { f -> tick(); cue(LiveReactions.waterChange(f)) { vm.waterChange(f) } },
        onSaltChange = { f, mix, salt -> tick(); cue(LiveReactions.waterChange(f, salt, mix, hud.water)) { vm.waterChange(f, mix) } },
        onDose = { id, d ->
            tick()
            val label = SandboxParams.byId(id)?.let { SandboxFriendly.of(it).title } ?: id
            // checked against what already went in today and the Ca × alk × Mg balance (core DoseCheck)
            val now = hud.simTimeSec
            val verdict = DoseCheck.check(id, d, hud.water, doseLog.lastDay(id, now))
            doseLog.add(now, id, d)
            cue(LiveReactions.dose(id, label, verdict)) { vm.dose(id, d) }
        },
        onHeater = { working ->
            tick()
            heaterWorking = working
            cue(LiveReactions.heater(working)) { vm.setHeater(working = working) }
            if (!working) offerUndo("The heater broke. The water will slowly cool.") { heaterWorking = true; cue(LiveReactions.heater(true)) { vm.setHeater(working = true) } }
        },
        onLights = { on -> tick(); cue(LiveReactions.lights(on)) { vm.setLights(on) } },
        onPredation = { on -> tick(); cue(LiveReactions.predation(on)) { vm.setPredation(on) } },
        onResize = { id, cm ->
            val before = panel.selected?.takeIf { it.id == id }
            vm.setSize(id, cm)
            echo(LiveReactions.resize(cm >= (before?.lengthCm ?: 0.0), before?.name ?: "Fish"))
        },
        onRemove = { id ->
            val name = panel.selected?.takeIf { it.id == id }?.name ?: "this animal"
            ask = ConfirmAsk(
                "Take out $name?",
                if (myTank) "$name leaves your tank for good." else "$name leaves the sandbox. Your real tank is not touched, and Reset tank brings everyone back.",
                "Take out",
            ) { tick(); cue(LiveReactions.remove(name)) { vm.removeOrganism(id) } }
        },
        onAdd = { a -> tick(); cue(LiveReactions.addSimple(a.name)) { vm.addOrganism(a.speciesId, a.sizeCm, "New ${a.name}") } },
        onReset = {
            ask = if (myTank) ConfirmAsk(
                "Start My tank over?",
                "Every animal and coral in ${customInfo.name.ifBlank { "My tank" }} goes, and you pick a new empty tank. This cannot be undone.",
                "Start over",
            ) { tick(); preset = null; heaterSet = HEATER_DEFAULT_C; heaterWorking = true; vm.startOverCustomTank() } else ConfirmAsk(
                "Reset the sandbox?",
                "Everything you changed here goes back to match your real tank: the water, the animals, the equipment and any what-ifs.",
                "Reset tank",
            ) { tick(); preset = null; heaterSet = HEATER_DEFAULT_C; heaterWorking = true; cue(LiveReactions.reset()) { vm.resetSandbox() } }
        },
        onAcclimateAll = { tick(); cue(LiveReactions.acclimate()) { vm.acclimateAll() } },
        onRearrangeRock = { tick(); cue(LiveReactions.rocks()) { vm.rearrangeRock() } },
        onPumpFailTonight = { tick(); cue(LiveReactions.pumpsTonight()) { vm.pumpFailTonight() } },
        onTimelapse = { echo(LiveReactions.timelapse()); lapse = true },
        onAddMany = { id, n, size ->
            tick()
            val card = stockCards.firstOrNull { it.key == id }
            // new animals come in a shop bag: it goes in the keeper's hand, to be dragged onto the tank
            if (card != null) { open = null; bagCarry = BagCarry(card, n, size) }
            else cue(LiveReactions.addSimple(id)) { vm.addMany(id, n, size) }
        },
        onAddAiptasia = {
            tick()
            cue(LiveReactions.addAiptasia()) { vm.addAiptasia() }
            offerUndo("Added a pest anemone") { cue(LiveReactions.removeAiptasia()) { vm.removeAiptasia() } }
        },
        onRemoveAiptasia = {
            tick()
            // nothing to zap is said so, and no command goes out
            val pests = hud.corals.count { CoralKind.resolve(it.speciesId) == CoralKind.AIPTASIA && it.tissue > 0.01f }
            if (pests > 0) cue(LiveReactions.removeAiptasia(pests)) { vm.removeAiptasia() } else cue(LiveReactions.removeAiptasia(0))
        },
        onTreatAiptasia = { t ->
            tick()
            // the tool goes to the biggest polyp that is still whole; the sim says whether it took or the polyp hid
            val live = hud.corals.filter { CoralKind.resolve(it.speciesId) == CoralKind.AIPTASIA && it.tissue > 0.5f }
            val target = live.maxByOrNull { it.size }
            if (target == null) cue(LiveReactions.removeAiptasia(0))
            else {
                banner.show(LiveReactions.treatAiptasia(t, live.size), Candy.green)
                val act = if (t == AiptasiaTreatment.LASER) CareAct.LASER else CareAct.INJECT
                val tint = when (t) { AiptasiaTreatment.KALK_PASTE -> 0xF4F4EE; AiptasiaTreatment.JOES_JUICE -> 0xE8F0FF; else -> 0xF2EEE2 }
                val at = floatArrayOf(target.restX, target.restY, target.restZ, target.restNx, target.restNy, target.restNz)
                careShow.play(CareJob(act, tint, at = at, onImpact = {
                    vm.treatAiptasia(t, target.id) { killed ->
                        val (line, mood) = LiveReactions.treated(t, killed)
                        if (hermieOn) hermie.say(line, mood)
                        banner.show(Feedback(if (killed) "Got it!" else "It hid!", line), if (killed) Candy.green else Candy.orange)
                    }
                }))
            }
        },
        onAddPestControl = { c -> tick(); cue(LiveReactions.pestControl(c)) { vm.addPestControl(c, if (c == PestController.BERGHIA) 3 else 1) } },
        onStartTreatment = { k ->
            tick()
            cue(LiveReactions.treatment(k, true)) { vm.startTreatment(k) }
            offerUndo("Started ${k.label}") { cue(LiveReactions.treatment(k, false)) { vm.stopTreatment(k) } }
        },
        onStopTreatment = { k -> tick(); cue(LiveReactions.treatment(k, false)) { vm.stopTreatment(k) } },
        onInfect = { d ->
            ask = ConfirmAsk(
                "Make a fish sick?",
                if (myTank) "A random fish in your tank catches ${SandboxFriendly.diseaseLabel(d)}. It is saved like everything else here." else "A random fish in the sandbox catches ${SandboxFriendly.diseaseLabel(d)}. Only the sandbox changes; Reset tank undoes it.",
                "Make one sick",
            ) { tick(); cue(LiveReactions.infect(d)) { vm.infect(d) } }
        },
        onMaintain = { t ->
            tick()
            val r = LiveReactions.maintain(t, hud.water)
            // a trim with nothing to cut only says so
            if (t == MaintenanceTask.HARVEST_MACRO && !SupplyPreview.canTrim(hud.water)) cue(r) else cue(r) { vm.maintain(t) }
        },
        onAddSpareShells = { tick(); cue(LiveReactions.shells()) { vm.addSpareShells() } },
        onMicro = { a -> tick(); cue(LiveReactions.micro(a)) { vm.micro(a) } },
        onHelp = { tick(); open = null; vm.select(null); tipsAsked = true },
    )
    // Fix tank: each fix plays in the 3D tank (render CareScene: the cup comes off and is emptied, the sock is swapped,
    // the bottle pours) and its commands go in when the animation reaches the water; heater, pumps and coral food
    // use their usual pictures. Queued kinds drop off the list so a fix is never sent twice.
    fun playFix(f: CareFix) {
        if (f.kind in fixQueued) return
        fixQueued += f.kind
        val act = when (f.kind) {
            TankDoctor.Kind.EMPTY_SKIMMER -> CareAct.EMPTY_CUP
            TankDoctor.Kind.SWAP_SOCK -> CareAct.SWAP_SOCK
            TankDoctor.Kind.FRESH_CARBON -> CareAct.SWAP_CARBON
            TankDoctor.Kind.DOSE_MG, TankDoctor.Kind.DOSE_CA, TankDoctor.Kind.DOSE_ALK -> CareAct.DOSE
            TankDoctor.Kind.TOP_OFF -> CareAct.TOP_OFF
            TankDoctor.Kind.WATER_CHANGE, TankDoctor.Kind.RAISE_SALT -> CareAct.WATER_CHANGE
            TankDoctor.Kind.LOWER_SALT -> CareAct.RO_SWAP
            TankDoctor.Kind.HEATER, TankDoctor.Kind.PUMPS, TankDoctor.Kind.FEED_CORALS -> CareAct.PAUSE
        }
        val tint = when (f.kind) {
            TankDoctor.Kind.DOSE_ALK -> 0xB08CFF
            TankDoctor.Kind.DOSE_CA -> 0xE8F2FF
            TankDoctor.Kind.DOSE_MG -> 0x8FE66B
            TankDoctor.Kind.RAISE_SALT -> 0xD6F2FF
            else -> 0xBFE6FF
        }
        val impact: () -> Unit = when (f.kind) {
            TankDoctor.Kind.HEATER -> ({ heaterWorking = true; heaterSet = TankDoctor.HEATER_C; cue(LiveReactions.heater(true)) { vm.care(f.commands) } })
            TankDoctor.Kind.PUMPS -> ({ cue(LiveReactions.pump("pump_left", true)) { vm.care(f.commands) } })
            TankDoctor.Kind.FEED_CORALS -> ({ cue(LiveReactions.feed(FoodKind.CORAL_FOOD)) { vm.care(f.commands) } })
            else -> ({ vm.care(f.commands); if (hermieOn) hermie.say(fixDoneLine(f.kind), HermieMood.HAPPY) })
        }
        if (act != CareAct.PAUSE) banner.show(Feedback(f.title, f.why), Candy.green)
        careShow.play(CareJob(act, tint, f.amount.toFloat(), onImpact = impact, onDone = {
            fixQueued -= f.kind
            if (fixQueued.isEmpty()) {
                banner.show(Feedback("All fixed!", "The tank check is happy. Check again in a few days."), Candy.green)
                if (hermieOn) hermie.say("All done! The tank is happy again.", HermieMood.EXCITED)
            }
        }))
    }
    val onModeChange: (HudMode) -> Unit = { m ->
        val target = when (m) { HudMode.MY_TANK -> SimMode.CUSTOM; HudMode.WATCH -> SimMode.REALITY }
        // tapping the side that is already picked never hides anything: it only brings the menu back out
        railShown = true; chromeHidden = false
        if (target != mode) {
            val toSandbox = target != SimMode.REALITY
            heaterSet = HEATER_DEFAULT_C; heaterWorking = true
            preset = null; open = null; tipsAsked = false; lapse = false; lapseFork = false
            vm.select(null); vm.follow(null)
            // a sandbox-only prop in the hand cannot be used in the real tank
            if (carry?.locked(toSandbox) == true) carry = null
            bagCarry = null
            vm.setMode(target)
            if (hermieOn) when (target) {
                SimMode.SANDBOX -> hermie.say("This is the pretend tank. Try anything, your real tank is safe!", HermieMood.EXCITED)
                SimMode.CUSTOM -> hermie.say("Your very own tank! Everything you do here is saved.", HermieMood.EXCITED)
                SimMode.REALITY -> hermie.say("Back to your real tank. Just watch and feed.", HermieMood.HAPPY)
            }
        }
    }
    // the Supplies bubble and the Watch card's "Feed": swing the camera to the 3D supply shelf (the tour stops)
    fun openShelf() {
        tick()
        open = null; vm.select(null); vm.follow(null); cleaning = false; touring = false
        carry = null; shelfPick = null; testKit = false
        supplies = true
    }
    val tools = LiveTools(
        sandbox = levers,
        myTank = myTank,
        stores = com.reeflive.core.custom.economy.FishStore.entries.map { com.reeflive.app.live.StoreFrontUi(it, (economy?.level ?: 1) >= it.level) },
        onStore = { f ->
            tick(); storePick = null
            vm.enterStore(f)
            if (hermieOn) hermie.say(f.greeting, HermieMood.HAPPY)
        },
        onStoreShut = { f -> tick(); if (hermieOn) hermie.say("Do your care jobs to level up and ${f.label} opens!", HermieMood.SURPRISED) },
        room = roomShop,
        onRoomTile = { t ->
            tick()
            when {
                t.owned -> {
                    trying = null
                    vm.decorUse(t.key); vm.showRoom()
                    if (hermieOn) hermie.say(if (t.key.startsWith("extra:")) "${t.label} ${if (t.inUse) "off" else "on"}!" else "${t.label} it is!", HermieMood.HAPPY)
                }
                t.locked -> if (hermieOn) hermie.say("That one opens at level ${t.level}. Keep doing your care jobs!", HermieMood.SURPRISED)
                else -> {
                    trying = t
                    open = null
                    vm.decorTry(t.key); vm.showRoom()
                    val coins = economy?.coins ?: 0
                    if (hermieOn) hermie.say(if (coins >= t.price) "How does it look? Buy it to keep it." else "You need ${t.price - coins} more sand dollars for this one.", HermieMood.HAPPY)
                }
            }
        },
        soundOn = soundOn,
        onSound = { tick(); toggleSound() },
        radioOn = settings.radioOn,
        radioSong = RadioStation.song(radioTrack).title,
        onRadio = {
            tick()
            val on = !settings.radioOn
            scope.launch { settingsRepo.setRadioOn(on) }
            val song = RadioStation.song(radioTrack)
            banner.show(if (on) Feedback("Radio on: ${song.title}", "${song.blurb}, playing softly beside the tank") else Feedback("Radio off", "Just the tank sounds now"), Candy.purple)
        },
        onRadioStep = { step ->
            tick()
            val next = RadioStation.step(radioTrack, step, owned)
            scope.launch { settingsRepo.setRadioTrack(next) }
            val song = RadioStation.song(next)
            banner.show(Feedback("Now playing: ${song.title}", song.blurb), Candy.purple)
        },
        onRecords = { tick(); open = null; recordsOpen = true },
        onMicroscope = { tick(); micro = true; if (hermieOn) hermie.say(LiveReactions.microscope(), HermieMood.EXCITED) },
        onAquascape = { tick(); open = null; vm.select(null); vm.follow(null); cleaning = false; scaping = true },
        onHide = { tick(); chromeHidden = true },
        onScreensaver = { tick(); context.startActivity(Intent(context, ScreensaverActivity::class.java)) },
        onHelp = {
            tick()
            open = null; vm.select(null); tipsAsked = true
        },
        cleaning = cleaning,
        onCleanGlass = toggleCleaning,
        hermieOn = hermieOn,
        onHermie = { tick(); hermieOn = !hermieOn },
        meterOn = meterOn,
        onMeter = { tick(); meterOn = !meterOn },
        thoughtsOn = thoughtsOn,
        onThoughts = {
            tick(); thoughtsOn = !thoughtsOn
            banner.show(Feedback(if (thoughtsOn) "Thought bubbles on" else "Thought bubbles off", if (thoughtsOn) "Up to three animals say what they are thinking" else "The fish keep their thoughts to themselves"), Candy.purple)
            if (thoughtsOn && hermieOn) hermie.say("Look! Now you can see what the fish are thinking.", HermieMood.HAPPY)
        },
        onTapGlass = { actions.onTapGlass() },
        onFeed = { k -> actions.onFeed(k) },
        readouts = hudState.readouts,
        lifePace = lifePace,
        onLifePace = { p ->
            tick(); vm.setLifePace(p)
            banner.show(Feedback("Growing speed: ${p.label}", "${p.blurb}. Fish still swim at real speed."), Candy.purple)
        },
        speed = if (sandbox) speed else 1.0,
        simTimeSec = hud.simTimeSec,
        onSpeed = { x ->
            tick()
            when {
                // Watch mode: fast-forward on a pretend copy, so the real tank keeps real time
                !sandbox && x > 1.0 -> {
                    vm.setSpeed(x); lapseFork = true; vm.setMode(SimMode.SANDBOX)
                    if (hermieOn) hermie.say("Fast-forward runs on a pretend copy. Your real tank keeps real time.", HermieMood.EXCITED)
                    echo(LiveReactions.speed(x))
                }
                sandbox && lapseFork && x <= 1.0 -> {
                    vm.setSpeed(1.0); lapseFork = false; open = null; vm.setMode(SimMode.REALITY)
                    echo(LiveReactions.speed(1.0))
                }
                else -> actions.onSpeed(x)
            }
        },
        viewLight = viewLight,
        onViewLight = { v ->
            tick(); viewLight = v
            banner.show(Feedback("Lamps: ${v.label}", if (v == ViewLight.REAL) "Back to your tank's real light schedule" else "Just the look: your real tank keeps its own schedule"), Candy.yellow)
        },
        torchOn = torchOn,
        onTorch = {
            tick(); torchOn = !torchOn
            banner.show(Feedback(if (torchOn) "Torch on" else "Torch off", if (torchOn) "Drag on the tank to shine it around" else "Torch put away"), Candy.yellow)
        },
        touring = touring,
        onTour = {
            tick(); touring = !touring
            if (!touring) vm.follow(null)
            banner.show(Feedback(if (touring) "Auto tour" else "Tour stopped", if (touring) "The camera visits each animal in turn. Tap the water to stop." else "Back to the free camera"), Candy.cyan)
        },
        fish = hud.stats.filter { it.alive }.take(12).map { it.id to it.nickname.ifBlank { it.common } },
        onFollowFish = { id -> tick(); touring = false; vm.follow(id) },
        subOn = subUi.on,
        onSubmarine = {
            tick(); touring = false; cleaning = false; open = null
            vm.setSubmarine(subUi.copy(on = !subUi.on))
        },
        // Watch card "Feed": the food lives on the supply shelf now, so it opens the shelf (same as the Supplies bubble)
        onOpenFeed = { openShelf() },
        onSupplies = { openShelf() },
        fixes = if (open == LiveCategory.FIX) TankDoctor.diagnose(hud.water, hud.care).filter { it.kind !in fixQueued } else emptyList(),
        fixing = fixQueued.size,
        hunger = if (open == LiveCategory.FIX) vm.hungerRows(hud.stats) else emptyList(),
        onFeedHungry = {
            tick()
            banner.show(Feedback("Dinner time!", "Each hungry animal gets its favourite food, dropped right above it"), Candy.orange)
            cue(LiveReactions.feed(FoodKind.PELLET)) { vm.feedHungry() }
        },
        coralRows = if (open == LiveCategory.FIX) CoralHealth.rows(hud.corals) else emptyList(),
        coralCare = if (open == LiveCategory.FIX && myTank) com.reeflive.core.sim.CoralCare.plan(hud) else null,
        onReefCare = if (myTank) ({
            tick()
            val plan = vm.reefCarePlan()
            if (vm.reefCare(plan.price)) {
                banner.show(Feedback("Coral care!", "In goes " + plan.words), Candy.green)
                cue(LiveReactions.feed(FoodKind.CORAL_FOOD))
            } else banner.show(Feedback("Not enough sand dollars", "Coral care costs ${plan.price}. Do today's chores to earn more."), Candy.orange)
        }) else null,
        onFix = { f -> tick(); playFix(f) },
        onFixAll = {
            tick()
            if (hermieOn) hermie.say("Let's fix it all up! Watch the tank.", HermieMood.EXCITED)
            TankDoctor.diagnose(hud.water, hud.care).filter { it.kind !in fixQueued }.forEach(::playFix)
        },
        onHeatWave = {
            tick()
            SandboxParams.byId("temp")?.let { def ->
                val cur = def.valueIn(panel.now)
                val hot = def.spec.snap(cur + 3.0)
                def.simId?.let { id -> cue(LiveReactions.heatWave()) { vm.applyParam(def.id, id, hot / def.scale, cur / def.scale, SandboxControlMode.RAMP) } }
            }
        },
    )
    // auto tour: the follow camera visits every animal in turn
    LaunchedEffect(touring) {
        var i = 0
        while (touring) {
            val ids = vm.hud.value.stats.filter { it.alive }.map { it.id }
            if (ids.isEmpty()) break
            vm.follow(ids[i % ids.size]); i++
            delay(TOUR_STOP_MS)
        }
    }
    LaunchedEffect(sandbox) { if (!sandbox) lapseFork = false }
    // a picked fish closes the menu card so its own card has the room
    LaunchedEffect(stats?.id) { if (stats != null) open = null }

    // a prop tapped on the shelf: its label pops up; a usable one is then carried back to the tank
    val onShelfPick: (String, Float, Float, Float) -> Unit = { id, x, y, z ->
        val item = SupplyItem.byId(id)
        if (item != null && supplies && carry == null) {
            tick()
            val px = FloatArray(3)
            val at = if (projection.project(x, y + 3f, z, px)) Offset(px[0], px[1]) else null
            val locked = item.locked(sandbox)
            pickSerial++
            shelfPick = ShelfPick(item, at, locked, pickSerial)
            pickJob[0]?.cancel()
            when {
                locked -> if (hermieOn) hermie.say("${item.label} is for the pretend tank. Switch to Sandbox to try it!", HermieMood.SURPRISED)
                item.staysOnShelf -> {
                    // a fresh test each pick: the kit's own rounding and read error, not the sim's exact numbers
                    kitReadouts = testKitReadouts(hud.water, kotlin.random.Random(pickSerial * 7919L + hud.simTimeSec.toLong()))
                    testKit = true
                    vm.testedWater()
                    if (hermieOn) hermie.say(LiveReactions.testKit(kitReadouts), HermieMood.HAPPY)
                }
                else -> pickJob[0] = scope.launch {
                    delay(if (reduceMotion) SHELF_LABEL_REDUCED_MS else SHELF_LABEL_MS)
                    supplies = false; shelfPick = null; testKit = false
                    // wait for the camera to arrive back at the tank (a cut with Reduce Motion)
                    if (!reduceMotion) delay(SHELF_TRAVEL_MS)
                    carry = item
                }
            }
            Unit
        }
    }
    val latestShelfPick by rememberUpdatedState(onShelfPick)
    DisposableEffect(shelf) {
        shelf.onPick = { id, x, y, z -> latestShelfPick(id, x, y, z) }
        onDispose { shelf.onPick = null }
    }
    // the carried prop let go over the tank: food drops in there, the knocker knocks there, the rest do their thing
    val onCarryDrop: (SupplyItem, Float, Float, Float, Float) -> Unit = { item, gx, gy, sx, sy ->
        carry = null
        if (!supplyHintDone) {
            supplyHintDone = true
            if (!settings.supplyHintSeen) scope.launch { settingsRepo.setSupplyHintSeen(true) }
        }
        val k = item.food
        when {
            // food goes in right where it was let go: the renderer finds the fish, coral, rock or sand there (onAim)
            k != null && k != FoodKind.NORI -> { tick(); aimFood = k; aim.aimAt(sx, sy) }
            item == SupplyItem.KNOCKER -> {
                val p = Vec3(gx.coerceIn(1f, com.reeflive.app.render.Camera.TANK[0] - 1f).toDouble(), gy.coerceIn(TankDims.SAND_TOP_F, com.reeflive.app.render.Camera.WATER_Y).toDouble(), FrontGlassCentre.z)
                tick()
                cue(LiveReactions.tapGlass(FxPoint(p.x.toFloat(), p.y.toFloat(), p.z.toFloat()))) { vm.tapGlass(p) }
            }
            else -> item.use(actions, tools)
        }
    }
    val onAim: (AimHit) -> Unit = { h ->
        val k = aimFood
        if (k != null) {
            aimFood = null
            val p = h.point
            cue(LiveReactions.feed(k, FxPoint(p.x.toFloat(), if (k.floats) SURFACE_Y else p.y.toFloat(), p.z.toFloat()))) {
                vm.feed(k, k.typicalDoseGrams, p, target = true)
            }
        }
    }
    val latestAim by rememberUpdatedState(onAim)
    DisposableEffect(aim) {
        aim.onAim = { h -> latestAim(h) }
        onDispose { aim.onAim = null }
    }
    // a tapped piece of gear: its pop-out menu (TankSpotPopup) replaces whatever card was open
    val onSpot: (TankSpot, Float, Float) -> Unit = { spot, x, y ->
        haptics.tick()
        spotSerial++
        spotTap = SpotTap(spot, Offset(x, y), spotSerial)
        open = null
        vm.select(null)
    }
    val onSpotOption: (com.reeflive.core.sim.SpotOption) -> Unit = { o ->
        tick()
        spotTap = null
        val f = o.fix
        if (f != null) playFix(f)
        else {
            o.commands.filterIsInstance<com.reeflive.core.sim.Command.Heater>().firstOrNull()?.setC?.let { heaterSet = it }
            vm.care(o.commands)
            banner.show(Feedback(o.label, "Done!"), Candy.green)
        }
    }
    val onCarryBack: (SupplyItem) -> Unit = { carry = null; tick(); supplies = true }
    val onBagDrop: (BagCarry, Float) -> Unit = { b, gx ->
        bagCarry = null
        tick()
        vm.floatBag(b.card.key, b.count, b.size, gx)
        echo(LiveReactions.bagIn(b.card, b.count, gx))
    }
    val onCarryCancel: () -> Unit = { carry = null; tick() }
    val gauges = GaugeReadings(
        tempC = hud.water.tempC,
        sg = hud.water.sg,
        lightPct = hud.lightIntensity * 100.0,
        flowPct = hud.flowStrength * 100.0,
        heaterSetC = heaterSet,
        heaterWorking = heaterWorking,
    )
    val applyGauge: (TankGauge, Double) -> Unit = { g, v ->
        if (g == TankGauge.HEATER) {
            val old = heaterSet
            heaterSet = v; heaterWorking = true
            tick()
            cue(LiveReactions.param("temp", old, v) ?: LiveReactions.heater(true)) { vm.setHeater(working = true, setC = v) }
        } else {
            g.paramId?.let { SandboxParams.byId(it) }?.let { def -> actions.onSet(def, v) }
        }
    }

    // ---- idle screensaver (Tee): 30 s with no touch fades into screensaver mode; one tap fades back to this view
    val cinema = remember { CinemaSession() }
    var cineOn by remember { mutableStateOf(false) }
    val cineFade = remember { mutableFloatStateOf(0f) }
    val cineChrome = remember { mutableFloatStateOf(0f) }
    val cineCaption = remember { mutableStateOf("") }
    val cineCaptionAlpha = remember { mutableFloatStateOf(0f) }
    val lastInput = remember { longArrayOf(SystemClock.uptimeMillis()) }
    val awayNow = vm.away.collectAsStateWithLifecycle().value != null
    // never mid-action: a store walk, a dialog or card that waits on the keeper, a drag, the shelf, a tool mode
    val idleBlocked by rememberUpdatedState(
        lapse || micro || careOpen || storePick != null || visit != null || ask != null || fact != null || cleaning ||
            scaping || supplies || carry != null || bagCarry != null || aimFood != null || dial != null || flushing != null ||
            subUi.on || awayNow || lapseState is TimelapseState.Running,
    )
    BackHandler(enabled = cineOn) { cinema.exit() }
    LaunchedEffect(cinema) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else (now - last) / 1e9f
                last = now
                val clock = SystemClock.uptimeMillis()
                // back from the background or another screen: the idle clock starts over
                if (dt > 1f) lastInput[0] = clock
                if (cinema.active) {
                    cinema.update(dt, vm.snapshots.value)
                    cineFade.floatValue = cinema.fade
                    // the controls fade out with the first dip and back in with the way out
                    cineChrome.floatValue = if (cinema.leaving) cinema.fade else maxOf(cineChrome.floatValue, cinema.fade)
                    cineCaptionAlpha.floatValue = cinema.captionAlpha
                    cinema.caption?.let { if (it != cineCaption.value) cineCaption.value = it }
                    if (!cinema.active) { cineOn = false; cineFade.floatValue = 0f; cineChrome.floatValue = 0f; lastInput[0] = clock }
                } else if (!idleBlocked && clock - lastInput[0] > IDLE_SCREENSAVER_MS) {
                    if (cinema.enter()) cineOn = true else lastInput[0] = clock
                }
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().pointerInput(Unit) {
        // any touch anywhere (seen before the controls and the tank take it) restarts the idle clock
        awaitPointerEventScope { while (true) { awaitPointerEvent(PointerEventPass.Initial); lastInput[0] = SystemClock.uptimeMillis() } }
    }) {
        val landscape = maxWidth > maxHeight
        val reduce = ReefTheme.reduceMotion
        val s = LiveTheme.style
        val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        // the bottom bar is capped and centred: on a wide landscape screen the rail runs down beside it
        val besideBar = landscape && maxWidth >= BottomBarMaxWidth + (s.railWidth + 32.dp) * 2
        val menuBottom = if (besideBar) navInset + 8.dp else NavClearance + navInset
        val following = followId != null && stats != null && !cleaning
        val menuUp = !chromeHidden && !micro && !cleaning && !following && !supplies && carry == null && bagCarry == null && !subUi.on && !scaping && visit == null
        // what the HUD keeps clear of: the rail on the left (landscape) or the dock above the bar (portrait)
        val hudStart = if (landscape && menuUp) s.railWidth + 4.dp else 0.dp
        val hudBottom = when {
            landscape -> BottomBarClearance
            menuUp -> NavClearance + navInset + if (railShown) DockHeight else DockTucked
            else -> BottomBarClearance
        }
        TankArea(
            vm, fx, projection, bursts, viewLight, torch, torchOn, if (supplies) null else followId, quality, cleaning, onScrape, { if (!cleaning) toggleCleaning() },
            { id -> spotTap = null; onPick(id) }, onTankTap, onLongPress, micro,
            onCloseMicro = { micro = false },
            onMicroAction = if (sandbox) actions.onMicro else null,
            onSkip = { skippedAt[0] = SystemClock.uptimeMillis() },
            bottomPad = BottomBarClearance,
            shelf = shelf,
            care = careShow,
            onTouchTank = { p, surface -> spotTap = null; onTouchTank(p, surface) },
            onFinger = onFinger,
            onFlick = onFlick,
            followCam = followCam,
            onSpot = onSpot,
            aim = aim,
            cinema = cinema,
        )
        // every control and card sits in this layer, faded out while the screensaver plays (their state is untouched)
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - cineChrome.floatValue }) {
        // gear taps mean "rock goes here" while building and nothing from inside the sub
        SideEffect { aim.gearTaps = !scaping && !subUi.on }
        val spotShown = spotTap
        if (spotShown != null && !micro && !supplies && carry == null && bagCarry == null && !cleaning && !subUi.on && !scaping) {
            val menu = TankSpots.menu(spotShown.spot, hud.water, hud.care, hud.lightIntensity.toDouble() > 0.05, sandbox)
            TankSpotPopup(spotShown, menu, onOption = onSpotOption, onClose = { tick(); spotTap = null })
        }
        if (scaping) AquascapeBar(
            shape = scapeShape, tool = scapeTool, turnDeg = scapeTurn,
            onShape = { tick(); scapeShape = it },
            onTool = { tick(); scapeTool = it },
            onTurn = { tick(); scapeTurn = (scapeTurn + 45) % 360 },
            onUndo = { tick(); vm.removeRock(null) },
            onEmpty = { tick(); vm.clearRocks() },
            onDone = { tick(); scaping = false },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 12.dp, end = 12.dp, bottom = navInset + 12.dp)
                .widthIn(max = 560.dp),
        )
        // speech bubbles over a few animals: same bounds as the tank view, draws only, touches go through
        if (thoughtsOn && !micro && !supplies && carry == null && bagCarry == null && !cleaning && !subUi.on) {
            ThoughtBubbleLayer(vm.snapshots, projection, Modifier.fillMaxSize())
        }
        TankGaugeLayer(
            visible = !chromeHidden && !micro && !supplies && carry == null && bagCarry == null && !cleaning && !subUi.on,
            projection = projection,
            r = gauges,
            onDial = if (sandbox) ({ g: TankGauge -> tick(); dial = g }) else null,
            // keep clear of the HUD's two top rows, the dock / bottom bar and the landscape rail
            safeTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + GaugeTopClearance,
            safeBottom = hudBottom,
            safeStart = hudStart,
        )
        // what is happening right now, where everyone looks first: under the top pills, clear of the rail
        if (!micro) {
            ActionBanner(
                banner, panel.now,
                Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                    .padding(start = hudStart + 12.dp, end = 12.dp, top = if (landscape) 104.dp else 108.dp),
            )
        }
        // time-lapse cue: sun / moon over its arc, speed and tank clock, above the dock while time runs fast
        if (!micro) {
            FastForwardBadge(
                if (sandbox) speed else 1.0, hud.simTimeSec,
                Modifier.align(Alignment.BottomCenter).padding(bottom = hudBottom + 8.dp),
            )
        }
        HudLayer(
            vm, hudState, onModeChange, followId, stats, cleaning, toggleCleaning, landscape,
            // landscape is short: on the shelf the HUD would cover the top row's props, so it steps aside there
            hidden = chromeHidden || micro || (supplies && landscape) || subUi.on,
            hermie = hermie, hermieOn = hermieOn, onHermieOff = { hermieOn = false },
            happy = happy, meterOn = meterOn, onMeterOff = { meterOn = false },
            showTips = showTips && open == null && !supplies && carry == null && bagCarry == null, onDismissTips = dismissTips,
            start = hudStart, bottom = hudBottom,
            onNetOut = { st ->
                // the card steps aside so the net can be watched; the goodbye plays once it is out (ToiletFlush)
                tick()
                vm.netOut(st.id)
                vm.select(null)
                echo(LiveReactions.netOut(st.nickname.ifBlank { st.common }))
            },
            followCam = followCam,
            economy = if (myTank) economy else null,
            onMoney = { tick(); careOpen = true },
            rewardFx = rewardFx,
        )
        AnimatedVisibility(
            visible = menuUp,
            enter = fadeIn(liveSpec(reduce)) + scaleIn(liveSpec(reduce), initialScale = 0.9f, transformOrigin = if (landscape) TransformOrigin(0f, 0.5f) else TransformOrigin(0.5f, 1f)),
            exit = fadeOut(liveSpec(reduce, ReefMotion.FAST)),
        ) {
            LiveMenu(
                landscape = landscape,
                open = open,
                onOpen = { c -> if (c != null) tick(); open = c },
                railShown = railShown,
                onRailShown = { railShown = it; if (!it) open = null },
                panel = panel,
                actions = actions,
                tools = tools,
                bottomClearance = menuBottom,
                cardBottomClearance = NavClearance + navInset,
            )
        }
        // switching between the real tank and the sandbox copy swaps simulations: launch the sub in the new one too
        LaunchedEffect(sandbox) { if (subUi.on) vm.setSubmarine(subUi) }
        if (subUi.on && !micro) {
            val watchId = hud.submarine?.watchId
            SubmarineControls(
                ui = subUi,
                curious = hud.submarine?.curious,
                wary = hud.submarine?.wary,
                watchName = watchId?.let { w -> hud.stats.firstOrNull { it.id == w }?.let { it.nickname.ifBlank { it.common } } },
                watchMode = hud.submarine?.watchMode ?: com.reeflive.core.sim.SubWatch.HOVER,
                onWatchMode = { m -> tick(); if (watchId != null) vm.watchSubmarine(watchId, m) },
                onLetGo = { tick(); vm.watchSubmarine(null) },
                onNextFish = { d -> tick(); vm.watchSubmarineNext(d) },
                onSteer = vm::steerSubmarine,
                onChange = { u ->
                    tick(); vm.setSubmarine(u)
                    if (u.visible != subUi.visible) banner.show(
                        Feedback(if (u.visible) "Peek-a-boo!" else "Invisible", if (u.visible) "The fish can see you again" else "Now the fish do not notice you at all"),
                        Candy.purple,
                    )
                },
                onDone = { tick(); vm.setSubmarine(subUi.copy(on = false)) },
            )
        }
        SupplyShelfOverlay(
            visible = supplies,
            pick = shelfPick,
            readouts = kitReadouts,
            showReadouts = testKit,
            projection = projection,
            locked = SupplyItem.lockedIds(sandbox),
            showHint = showSupplyHint,
            landscape = landscape,
            onTag = { item: SupplyItem -> shelf.pick(item.id) },
            onRow = { r: ShelfRow -> tick(); shelf.focusRow(r.ordinal) },
            onClose = { tick(); pickJob[0]?.cancel(); supplies = false; shelfPick = null; testKit = false },
            bottomClearance = NavClearance + navInset,
        )
        carry?.let { item ->
            SupplyCarryLayer(item, projection, onCarryDrop, onCarryBack, onCarryCancel, bottomClearance = NavClearance + navInset)
        }
        // floating acclimation bags: a timer over each, and Hurry up (fast-forward until they open)
        if (sandbox && hud.bags.isNotEmpty() && bagCarry == null && !micro && !supplies && !subUi.on) {
            BagTimerLayer(
                vm.snapshots, projection, hud.bags, hud.bagHurry,
                onHurry = { on -> tick(); vm.hurryBags(on); echo(LiveReactions.hurryBags(on)) },
                bottomClearance = hudBottom,
            )
        }
        // a coral out of its bag waits for the keeper to pick one of its glowing spots (My tank)
        val coralPick = hud.coralPick
        if (myTank && coralPick != null && bagCarry == null && !micro && !supplies && !subUi.on) {
            // a new coral to place: close any open menu so the spots and their badges are in view
            LaunchedEffect(coralPick.waiting) { if (!coralPickHidden) open = null }
            CoralPickLayer(
                coralPick, projection, coralPickHidden,
                onPlace = { spot -> tick(); vm.placeCoral(spot); coralPickHidden = false; echo(LiveReactions.coralPlaced(coralPick.name)) },
                onHide = { coralPickHidden = it },
                bottomClearance = hudBottom,
            )
        }
        bagCarry?.let { b ->
            BagCarryLayer(b, projection, onBagDrop, { bagCarry = null; tick(); open = LiveCategory.LIFE }, bottomClearance = NavClearance + navInset)
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
        // My tank with nothing set up yet: pick a tank to start (it is saved from then on)
        if (myTank && customInfo.ready && !customInfo.exists) {
            Box(Modifier.fillMaxSize().padding(16.dp).statusBarsPadding(), contentAlignment = Alignment.Center) {
                MyTankSetupCard(onPick = { b ->
                    tick(); vm.startCustomTank(b)
                    if (hermieOn) hermie.say("A brand new ${b.label}! Tap Shop to visit the fish stores and bring home your first animals.", HermieMood.EXCITED)
                    open = LiveCategory.LIFE
                })
            }
        }
        // My tank: money coming in is cheered by Hermie; the Tank Health card is the water test
        LaunchedEffect(Unit) {
            vm.gains.collect { g ->
                rewardFx.cheer(g)
                // a reef moment or birthday has its own card (RewardFx MomentCard) and bells
                if (g.story != null) audio.sparkle()
                if (!hermieOn || g.story != null && g.levelUp == 0) return@collect
                if (g.levelUp > 0) hermie.say("Level ${g.levelUp}! ${com.reeflive.core.custom.economy.Levels.opens(g.levelUp)}".trim(), HermieMood.EXCITED)
                else hermie.say("+${g.amount} sand dollars: ${g.label}", HermieMood.HAPPY)
            }
        }
        LaunchedEffect(open) { if (open == LiveCategory.FIX) vm.testedWater() }
        val eco = economy
        if (careOpen && myTank && eco != null) CareCard(eco, onCollect = { tick(); vm.collectHappyBonus() }, onDismiss = { careOpen = false })
        // the shop's room tone, and the bell on the door as the walk in reaches it
        val inStore = visit != null && myTank
        LaunchedEffect(inStore) { audio.store(inStore, if (reduceMotion) 0L else STORE_BELL_MS) }
        // the store's people talk in bubbles over their heads; a regular may come over with a swap
        if (visit != null && myTank) StoreBubbleLayer(vm.storeRoom, projection, Modifier.fillMaxSize())
        val tradeOffer by vm.trade.collectAsStateWithLifecycle()
        tradeOffer?.let { o -> if (myTank) TradeCard(o) { yes -> tick(); vm.answerTrade(yes); if (yes && hermieOn) hermie.say("Traded! Your ${o.getName} is floating in a bag at the top of your tank.", HermieMood.EXCITED) } }
        trying?.let { t ->
            if (myTank && visit == null) TryOnBar(
                t, economy?.coins ?: 0, hudBottom + 8.dp,
                onBuy = {
                    tick()
                    vm.decorBuy(t.key) { r ->
                        scope.launch {
                            when (r) {
                                com.reeflive.core.custom.room.DecorResult.OK -> {
                                    trying = null
                                    if (hermieOn) hermie.say(if (t.key.startsWith("stand:")) "Your new ${t.label.lowercase()} is in!" else "${t.label}: yours now!", HermieMood.EXCITED)
                                }
                                com.reeflive.core.custom.room.DecorResult.TOO_POOR -> if (hermieOn) hermie.say("Not enough sand dollars yet. Do your care jobs to earn more!", HermieMood.SURPRISED)
                                com.reeflive.core.custom.room.DecorResult.LEVEL_LOCKED -> if (hermieOn) hermie.say("That one opens at a higher level.", HermieMood.SURPRISED)
                                else -> { trying = null; vm.decorTry(null) }
                            }
                        }
                    }
                },
                onCancel = { tick(); trying = null; vm.decorTry(null) },
            )
        }
        visit?.let { v ->
            if (myTank) StoreOverlay(
                v, storePick,
                counterRequest = counterRequest,
                onCounter = { on -> vm.storeCounter(on) },
                onTank = { i -> tick(); storePick = null; vm.showStoreTank(i) },
                onLeave = { tick(); storePick = null; vm.leaveStore(); if (hermieOn) hermie.say("Back home. Come again soon!", HermieMood.HAPPY) },
                onCheckout = { bag, done -> vm.checkout(bag) { r -> scope.launch { done(r) } } },
                onSell = { o -> vm.sell(o.id); if (hermieOn) hermie.say("Sold ${o.name} for ${o.amount} sand dollars! The store will take good care of it.", HermieMood.HAPPY) },
                say = { if (hermieOn) hermie.say(it, HermieMood.SURPRISED) },
                tick = { tick() },
                topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                bottomInset = navInset,
            )
        }
        // a checkout went through: the chip counts down and Hermie says where the bags are
        LaunchedEffect(Unit) {
            vm.paid.collect { (total, n) ->
                storePick = null
                if (hermieOn) hermie.say(
                    "Paid $total! ${if (n == 1) "Your new animal is" else "Your $n new animals are"} floating in bags at the top of the tank.",
                    HermieMood.EXCITED,
                )
            }
        }
        if (myTank) RewardFxLayer(rewardFx)
        // My tank lived on while the keeper was away: the days race by, then what happened
        val awayUi by vm.away.collectAsStateWithLifecycle()
        awayUi?.let { a -> if (myTank) WelcomeBackCard(a, onCare = { tick(); vm.dismissAway(); careOpen = true }, onDismiss = { vm.dismissAway() }) }
        // the net lifted a body out: the toilet goodbye (once per body)
        LaunchedEffect(hud.netFlush) { vm.takeFlush(hud.netFlush)?.let { flushing = it } }
        flushing?.let { f ->
            ToiletFlush(
                f, onDone = { flushing = null },
                onSplash = { audio.plop(); haptics.tick() },
                onFlush = { audio.flush(); haptics.tick() },
            )
        }
        if (lapse && sandbox) {
            TimelapseSheet(
                state = lapseState, water = hud.water,
                onStart = vm::startTimelapse, onCancel = vm::cancelTimelapse, onNew = vm::clearTimelapse,
                onDismiss = { lapse = false },
            )
        }
        }
        // screensaver mode over the keeper's own view (idle for 30 s): every touch only leaves it
        if (cineOn) CinemaOverlay(cinema, cineFade, cineCaption, cineCaptionAlpha)
    }
    dial?.let { g ->
        val start = when (g) {
            TankGauge.HEATER -> heaterSet
            else -> g.paramId?.let { SandboxParams.byId(it) }?.valueIn(panel.now) ?: g.lo
        }
        GaugeDial(g, start, onSet = { v -> dial = null; applyGauge(g, v) }, onDismiss = { dial = null })
    }
    if (recordsOpen) RecordShelf(
        on = settings.radioOn, playing = radioTrack, owned = owned, coins = if (myTank) economy?.coins else null,
        volume = settings.radioVolume,
        onToggle = { tick(); scope.launch { settingsRepo.setRadioOn(!settings.radioOn) } },
        onVolume = { v -> scope.launch { settingsRepo.setRadioVolume(v) } },
        onPlay = { i ->
            tick()
            scope.launch { settingsRepo.setRadioTrack(i); if (!settings.radioOn) settingsRepo.setRadioOn(true) }
            val song = RadioStation.song(i)
            banner.show(Feedback("Now playing: ${song.title}", song.blurb), Candy.purple)
        },
        onBuy = { i ->
            val song = RadioStation.song(i)
            when {
                !myTank -> { tick(); banner.show(Feedback(song.title, "Buy it in My tank with ${song.price} sand dollars"), Candy.yellow) }
                vm.buyRecord(song.file) -> {
                    tick()
                    scope.launch { settingsRepo.addRadioOwned(setOf(song.file)); settingsRepo.setRadioTrack(i); settingsRepo.setRadioOn(true) }
                    banner.show(Feedback("New record: ${song.title}!", "${song.blurb}. It's on the radio now"), Candy.green)
                    if (hermieOn) hermie.say("A new record! Let's give it a spin.", HermieMood.EXCITED)
                }
                else -> { tick(); banner.show(Feedback("Not enough sand dollars", "${song.title} costs ${song.price}. Do today's care jobs to earn more"), Candy.yellow) }
            }
        },
        onDismiss = { recordsOpen = false },
    )
    // the "While you were away" card goes first; the fact waits until it is closed
    val awayOpen = vm.away.collectAsStateWithLifecycle().value != null
    fact?.takeIf { !awayOpen }?.let { f ->
        LiveDialog(title = f.kind.title, body = f.text, confirm = "Cool!", onConfirm = { fact = null }, onDismiss = { fact = null }, dismiss = "Close")
    }
    ask?.let { a ->
        LiveDialog(
            title = a.title,
            body = a.body,
            confirm = a.confirmLabel,
            onConfirm = { ask = null; a.run() },
            onDismiss = { ask = null },
            destructive = true,
        )
    }
}

/** The banner's colour for a reaction: red for trouble, the dose colour for doses, orange for food, else cyan. */
private fun bannerTone(r: Reaction): Color = when (r.fx.kind) {
    FxKind.FEED -> Candy.orange
    FxKind.HEATER_BREAK, FxKind.HEATER_STUCK, FxKind.POWER_OUT, FxKind.ALERT, FxKind.PEST_ADD, FxKind.PUMP_OFF -> Candy.red
    FxKind.DOSE, FxKind.PIPETTE, FxKind.SPARKLE, FxKind.BAG -> if (r.fx.tint != Color.Unspecified) r.fx.tint else Candy.cyan
    FxKind.HEARTS, FxKind.HEATER_FIX, FxKind.MAINTAIN, FxKind.SNIP, FxKind.DEVICE_ON -> Candy.green
    else -> Candy.cyan
}

/** How long the auto tour stays with each animal. */
private const val TOUR_STOP_MS = 12_000L
/** The walk in from the sidewalk reaches the store's door about now (Camera.ENTRY_S, ENTRY_HOLD): the bell rings. */
private const val STORE_BELL_MS = 1_500L
/** The sim's default heater set point (WaterState.heaterSetC default). */
private const val HEATER_DEFAULT_C = 25.5

/** Hermie's line as each fix lands. */
private fun fixDoneLine(k: TankDoctor.Kind): String = when (k) {
    TankDoctor.Kind.EMPTY_SKIMMER -> "Eww, skimmer goo! Cup's clean now."
    TankDoctor.Kind.SWAP_SOCK -> "Stinky old sock out, fresh one in!"
    TankDoctor.Kind.FRESH_CARBON -> "Fresh carbon for sparkly water."
    TankDoctor.Kind.WATER_CHANGE -> "Fresh salt water! The fish love it."
    TankDoctor.Kind.TOP_OFF -> "Topped off with fresh water."
    TankDoctor.Kind.LOWER_SALT -> "A little less salty now."
    TankDoctor.Kind.RAISE_SALT -> "A little saltier now."
    TankDoctor.Kind.DOSE_MG, TankDoctor.Kind.DOSE_CA, TankDoctor.Kind.DOSE_ALK -> "Coral vitamins in! Just the right amount."
    else -> "Fixed!"
}
/** Supplies: how long a picked prop's label shows before the camera glides back, and the glide (Camera.SHELF_TRAVEL_S). */
private const val SHELF_LABEL_MS = 700L
private const val SHELF_LABEL_REDUCED_MS = 300L
private const val SHELF_TRAVEL_MS = 1000L
/** Food dragged from the shelf goes in at the water surface, this far from the back glass (FxSpots.FEED). */
private val SURFACE_Y: Float get() = com.reeflive.app.render.Camera.WATER_Y - 0.4f
private const val FEED_Z = 23.4f

/** A tap on the tank within this long after it skipped a picture is not also a "hide the controls" tap. */
private const val SKIP_PICK_GUARD_MS = 700L

/**
 * Watches taps on the tank without taking them (the GL view still gets every event): a quick tap while pictures play
 * skips them ([ActionFxState.skipAll] sends their commands at once) and reports it through [onSkipped].
 */
private fun Modifier.skipFxOnTap(fx: ActionFxState, onSkipped: () -> Unit): Modifier = pointerInput(fx) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (fx.playing) {
            val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation(PointerEventPass.Initial) }
            if (up != null && fx.playing) { fx.skipAll(); onSkipped() }
        }
    }
}

/** Makes this layer a touch target of its own, so overlapping siblings drawn under it (the GL tank) get no touches. */
private fun Modifier.blockTouchesBelow(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope { while (true) awaitPointerEvent() }
}

/** The 3D tank, the action pictures drawn over it through the camera, and the microscope. */
@Composable
private fun TankArea(
    vm: LiveTankViewModel,
    fx: ActionFxState,
    projection: TankProjection,
    bursts: TankBursts,
    viewLight: ViewLight,
    torch: TorchState,
    torchOn: Boolean,
    followId: String?,
    quality: LiveQuality,
    cleaning: Boolean,
    onScrape: (GlassStroke) -> Unit,
    onFlipperTap: () -> Unit,
    onPick: (String?) -> Unit,
    onTapGlass: (Vec3) -> Unit,
    onLongPress: (Vec3) -> Unit,
    micro: Boolean,
    onCloseMicro: () -> Unit,
    onMicroAction: ((MicroAction) -> Unit)?,
    onSkip: () -> Unit,
    bottomPad: Dp,
    shelf: SupplyShelfControl,
    care: CareShowControl,
    onTouchTank: (Vec3, TouchSurface) -> Unit,
    onFinger: (Vec3?, Float, TouchSurface) -> Unit,
    onFlick: (Vec3, Vec3) -> Unit,
    followCam: FollowCamControl,
    onSpot: (TankSpot, Float, Float) -> Unit,
    aim: TankAimControl,
    cinema: CinemaSession,
) {
    Box(Modifier.fillMaxSize()) {
        val sub by vm.submarine.collectAsStateWithLifecycle()
        val nurseryView by vm.nurseryView.collectAsStateWithLifecycle()
        val haptics = rememberHaptics()
        TankView(
            snapshots = vm.snapshots,
            modifier = Modifier.fillMaxSize().skipFxOnTap(fx, onSkip).trackTorch(torch, torchOn),
            followId = if (cleaning) null else followId,
            quality = quality.toRender(),
            onPick = onPick,
            onTapGlass = onTapGlass,
            onLongPress = onLongPress,
            onFollowLost = { vm.follow(null) },
            // a follow started by a double tap or a press-and-hold on the animal: a firmer buzz than a tap
            onFollow = { id -> if (id != null) haptics.confirm(); vm.follow(id) },
            onTouchTank = onTouchTank,
            onFinger = onFinger,
            onFlick = onFlick,
            cleaning = cleaning,
            onScrape = onScrape,
            onFlipperTap = onFlipperTap,
            projection = projection,
            bursts = bursts,
            viewLight = viewLight,
            shelf = shelf,
            care = care,
            subFirstPerson = sub.on && sub.inside,
            nurseryRequest = nurseryView,
            followCam = followCam,
            onSpot = onSpot,
            aim = aim,
            storeRoom = vm.storeRoom,
            room = vm.roomControl,
            cinema = cinema,
        )
        TorchOverlay(torch, torchOn && !micro)
        AnimatedVisibility(
            visible = micro,
            enter = fadeIn(liveSpec(ReefTheme.reduceMotion)),
            exit = fadeOut(liveSpec(ReefTheme.reduceMotion, ReefMotion.FAST)),
        ) {
            // the microscope covers the tank view; doses and treatments only in the sandbox. It takes every touch, so
            // nothing reaches the hidden tank under it (a tap there used to pick fish, knock the glass or start cleaning)
            val hud by vm.hud.collectAsStateWithLifecycle()
            Box(Modifier.fillMaxSize().blockTouchesBelow()) {
                MicroscopeView(
                    state = hud.microfauna,
                    simTimeSec = hud.simTimeSec,
                    onClose = onCloseMicro,
                    modifier = Modifier.fillMaxSize().background(ReefTheme.colors.bg).statusBarsPadding().padding(bottom = bottomPad),
                    onAction = onMicroAction,
                )
                // bacteria section (research 18): a chip over the microscope header opens the guild panel; actions only in the sandbox
                BacteriaOverlay(
                    state = hud.bacteria,
                    modifier = Modifier.fillMaxSize().statusBarsPadding().padding(bottom = bottomPad),
                    onAction = if (onMicroAction != null) vm::bacteria else null,
                )
            }
        }
        // same bounds as the TankView, so camera pixels and canvas pixels agree; no pointer input, touches go through
        // captions live in the ActionBanner now: one place to read what is happening
        ActionFxLayer(fx, projection, Modifier.fillMaxSize(), screenOnly = micro, captions = false)
    }
}

/**
 * Everything drawn over the tank apart from the menu: mode, clock, Hermie and the happy meter at the top; the fish
 * card, follow bar, clean-glass hint and tips at the bottom. Keeps [start] / [bottom] clear for the rail or dock.
 */
@Composable
private fun HudLayer(
    vm: LiveTankViewModel,
    hudState: HudState,
    onModeChange: (HudMode) -> Unit,
    followId: String?,
    stats: OrganismStats?,
    cleaning: Boolean,
    onToggleCleaning: () -> Unit,
    landscape: Boolean,
    hidden: Boolean,
    hermie: HermieState,
    hermieOn: Boolean,
    onHermieOff: () -> Unit,
    happy: Int,
    meterOn: Boolean,
    onMeterOff: () -> Unit,
    showTips: Boolean,
    onDismissTips: () -> Unit,
    start: Dp,
    bottom: Dp,
    onNetOut: (OrganismStats) -> Unit,
    followCam: FollowCamControl,
    economy: EconomyView? = null,
    onMoney: () -> Unit = {},
    rewardFx: RewardFxState? = null,
) {
    val reduce = ReefTheme.reduceMotion
    val sandbox = hudState.mode != HudMode.WATCH
    val padStart by animateDpAsState(start, liveSpec(reduce), label = "hudStart")
    val padBottom by animateDpAsState(bottom, liveSpec(reduce), label = "hudBottom")
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(start = padStart, bottom = padBottom),
    ) {
        AnimatedVisibility(
            visible = !hidden,
            enter = fadeIn(liveSpec(reduce)) + slideInVertically(liveSpec(reduce)) { -it / 3 },
            exit = fadeOut(liveSpec(reduce, ReefMotion.FAST)) + slideOutVertically(liveSpec(reduce)) { -it / 3 },
        ) {
            // the meter starts folded to its face (tap shows "Tank happy N %") and Hermie takes the rest of the row
            val pills: @Composable RowScope.() -> Unit = {
                if (economy != null && rewardFx != null) MoneyChip(economy, onMoney, rewardFx)
                AnimatedVisibility(meterOn, enter = fadeIn(liveSpec(reduce)) + scaleIn(liveSpec(reduce)), exit = fadeOut(liveSpec(reduce, ReefMotion.FAST)) + scaleOut(liveSpec(reduce))) {
                    HappyMeterPill(happy, onMeterOff, startFolded = true)
                }
                AnimatedVisibility(
                    hermieOn,
                    modifier = Modifier.weight(1f, fill = false),
                    enter = fadeIn(liveSpec(reduce)) + scaleIn(liveSpec(reduce)),
                    exit = fadeOut(liveSpec(reduce, ReefMotion.FAST)) + scaleOut(liveSpec(reduce)),
                ) {
                    HermiePill(hermie, sandbox, onHermieOff, maxWidth = if (landscape) 220.dp else 260.dp, myTank = hudState.mode == HudMode.MY_TANK)
                }
            }
            if (landscape) {
                // top-right corner: the left side belongs to the rail and its card
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        ClockPill(hudState.simTimeSec, hudState.dayPhase)
                        ModeToggle(hudState.mode, onModeChange)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) { pills() }
                }
            } else {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        ModeToggle(hudState.mode, onModeChange)
                        ClockPill(hudState.simTimeSec, hudState.dayPhase)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) { pills() }
                }
            }
        }
        val st = stats
        // keep the last card on screen while it animates out
        var lastCard by remember { mutableStateOf<OrganismStats?>(null) }
        if (st != null) lastCard = st
        val following = followId != null && st != null && !cleaning
        if (following && st != null) {
            // only living animals: stepping onto a dead one would lose the follow at once
            val ids = vm.hud.value.stats.filter { it.alive }.map { it.id }
            val i = ids.indexOf(st.id).coerceAtLeast(0)
            fun step(d: Int) { if (ids.isNotEmpty()) vm.follow(ids[(i + d + ids.size) % ids.size]) }
            FollowOverlay(
                st.toFollowState(i, ids.size), { step(-1) }, { step(1) }, { vm.follow(null) },
                onZoom = { steps -> followCam.zoom(steps) },
                onOrbit = { dir -> followCam.orbit(dir * FollowCamControl.ORBIT_STEP) },
            )
        }
        val bottomCard = Modifier
            .align(if (landscape) Alignment.BottomEnd else Alignment.BottomCenter)
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .then(if (landscape) Modifier.widthIn(max = 420.dp) else Modifier)
        AnimatedVisibility(
            visible = cleaning,
            modifier = bottomCard,
            enter = fadeIn(liveSpec(reduce)) + slideInVertically(liveSpec(reduce)) { it / 2 },
            exit = fadeOut(liveSpec(reduce, ReefMotion.FAST)) + slideOutVertically(liveSpec(reduce)) { it / 2 },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) {
                CleanGlassHint()
                LivePill("Done cleaning", onToggleCleaning, tone = Candy.green)
            }
        }
        AnimatedVisibility(
            visible = !hidden && !cleaning && !following && st != null,
            modifier = bottomCard,
            enter = fadeIn(liveSpec(reduce)) + slideInVertically(liveSpec(reduce)) { it / 3 } + scaleIn(liveSpec(reduce), initialScale = 0.92f),
            exit = fadeOut(liveSpec(reduce, ReefMotion.FAST)) + slideOutVertically(liveSpec(reduce)) { it / 3 },
        ) {
            val card = st ?: lastCard
            if (card != null) {
                val hudSnap by vm.hud.collectAsStateWithLifecycle()
                val family = hudSnap.brood.panelFor(card.speciesId)?.let { p ->
                    p.copy(actions = p.actions.filter { !it.sandboxOnly || hudState.mode == HudMode.MY_TANK })
                }
                FishCard(
                    card.toFishCardState(), onFollow = { vm.follow(card.id) }, onClose = { vm.select(null) },
                    onRename = if (hudState.mode == HudMode.MY_TANK) ({ n: String -> vm.rename(card.id, n) }) else null,
                    onNetOut = { onNetOut(card) },
                    family = family,
                    onFamily = { id ->
                        if (id == com.reeflive.core.sim.Brood.VIEW_NURSERY) { vm.select(null); vm.viewNursery() } else vm.breed(id)
                    },
                )
            }
        }
        AnimatedVisibility(
            visible = showTips && !hidden && !cleaning && st == null,
            modifier = bottomCard,
            enter = fadeIn(liveSpec(reduce)) + slideInVertically(liveSpec(reduce)) { it / 3 } + scaleIn(liveSpec(reduce), initialScale = 0.92f),
            exit = fadeOut(liveSpec(reduce, ReefMotion.FAST)) + slideOutVertically(liveSpec(reduce)) { it / 3 },
        ) {
            SandboxTipsCard(onDismiss = onDismissTips, sandbox = sandbox, myTank = hudState.mode == HudMode.MY_TANK)
        }
    }
}


/** Idle time before the Live tank fades into screensaver mode. */
private const val IDLE_SCREENSAVER_MS = 30_000L

/** Screensaver layer over the live tank: the dip to black, the animal caption, and a touch catcher (a tap leaves). */
@Composable
private fun CinemaOverlay(
    cinema: CinemaSession,
    fade: androidx.compose.runtime.FloatState,
    caption: androidx.compose.runtime.State<String>,
    captionAlpha: androidx.compose.runtime.FloatState,
) {
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(cinema) {
                // every pointer event is eaten so nothing reaches the tank or the controls; a press leaves
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        e.changes.forEach { it.consume() }
                        if (e.changes.any { it.pressed }) cinema.exit()
                    }
                }
            },
    ) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = fade.floatValue }.background(androidx.compose.ui.graphics.Color.Black))
        androidx.compose.material3.Text(
            caption.value,
            color = androidx.compose.ui.graphics.Color.White,
            style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 32.dp, bottom = 40.dp)
                .graphicsLayer { alpha = captionAlpha.floatValue },
        )
    }
}
