package com.reeflive.app.ui.live

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.reeflive.app.ui.components.Glyph
import com.reeflive.app.ui.components.ReefGlyph
import com.reeflive.app.ui.components.ReefGlyphs
import com.reeflive.app.ui.theme.ReefMotion
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.core.sim.Disease
import com.reeflive.app.render.ViewLight
import com.reeflive.core.sim.FoodKind
import com.reeflive.core.sim.MicroAction
import com.reeflive.core.sim.PestController
import kotlinx.coroutines.delay

/*
 * The live-tank menu (UI_STYLE §10.1): a few big candy bubbles a child can read. Look (time, follow, tour, sub,
 * microscope), Supplies (feed, care, clean glass, test kit, ... on the 3D supply shelf beside the tank, SupplyShelf.kt:
 * the bubble swings the camera there instead of opening a card), Tank Health (water, hunger bars, auto feed, fixes) and
 * Settings; My tank adds Add life and Tank (lights, water, gear, rockwork; the grown-up sliders, medicine and the
 * on-purpose trouble fold out under "More"). Landscape: a slim rail
 * of bubbles down the left edge (≈ 84 dp), so the tank keeps the rest of the screen; tapping a bubble opens a compact
 * glass card beside it (at most ~35 % of the width). Portrait: a dock of the same bubbles above the app's bottom bar,
 * the card opening above it. Cards are big tiles first; a "More" button folds out the grown-up sliders. A card closes
 * itself after a one-tap action (so the animation in the tank can be watched), after 25 s without a touch, on its ×,
 * or when the bubble is tapped again. The rail tucks away to a single bubble.
 */

/** Menu categories, each with its candy tone in [LiveTheme]. */
enum class LiveCategory(val label: String, val glyph: ReefGlyph, val title: String) {
    /** Both modes: time slider, follow a fish, auto tour, submarine, look closer, screensaver (Watch adds the lamp looks). */
    VIEW("Look", ReefGlyphs.eye, "Look around"),
    /** Not a card: swings the camera to the supply shelf ([LiveTools.onSupplies]). */
    SUPPLIES("Supplies", ReefGlyphs.feedTin, "Supply shelf"),
    /** Water, hunger bars with "Feed the hungry ones", and the tank check's fixes, each played in the 3D tank (TankHealthBody). */
    FIX("Tank Health", ReefGlyphs.heart, "Tank Health"),
    LIFE("Add life", ReefGlyphs.fish, "Add life"),
    /** Lights, water, gear and rockwork; its More folds out the sliders, medicine and the on-purpose trouble. */
    LIGHT("Tank", ReefGlyphs.sun, "Lights, water & gear"),
    /** Not a bubble any more (its tiles live under Tank → More): kept as the purple tone of the trouble presets. */
    TROUBLE("Trouble", ReefGlyphs.whatIf, "Tank trouble"),
    MORE("Settings", ReefGlyphs.sparkleTip, "Settings & help"),
    ;

    companion object {
        /** Watch can look, use the shelf, check the tank's health and change settings; My tank adds Add life and Tank. */
        fun shown(sandbox: Boolean): List<LiveCategory> =
            if (sandbox) listOf(VIEW, SUPPLIES, FIX, LIFE, LIGHT, MORE) else listOf(VIEW, SUPPLIES, FIX, MORE)
    }
}

/** Live-screen tools and toggles that are not sandbox commands (sound, microscope, hide, Hermie, meter, glass). */
class LiveTools(
    val sandbox: Boolean,
    val soundOn: Boolean,
    val onSound: () -> Unit,
    val onMicroscope: () -> Unit,
    val onHide: () -> Unit,
    val onHelp: () -> Unit,
    val cleaning: Boolean,
    val onCleanGlass: () -> Unit,
    val hermieOn: Boolean,
    val onHermie: () -> Unit,
    val meterOn: Boolean,
    val onMeter: () -> Unit,
    val onTapGlass: () -> Unit,
    val onFeed: (FoodKind) -> Unit,
    val readouts: List<WaterReadout>,
    /** Things that make a fish ill ask first; the menu only reports the pick. */
    val onHeatWave: () -> Unit = {},
    /** Tools → Build rockwork: opens the aquascape builder (white dry rock). */
    val onAquascape: () -> Unit = {},
    // ---- time slider (both modes) and the Watch card
    val speed: Double = 1.0,
    val simTimeSec: Double = 0.0,
    val onSpeed: (Double) -> Unit = {},
    val viewLight: ViewLight = ViewLight.REAL,
    val onViewLight: (ViewLight) -> Unit = {},
    val torchOn: Boolean = false,
    val onTorch: () -> Unit = {},
    val touring: Boolean = false,
    val onTour: () -> Unit = {},
    /** Submarine mode (SubmarineControls): steer a toy sub among the fish. */
    val subOn: Boolean = false,
    val onSubmarine: () -> Unit = {},
    /** Fish to follow: id to name. */
    val fish: List<Pair<String, String>> = emptyList(),
    val onFollowFish: (String) -> Unit = {},
    val onOpenFeed: () -> Unit = {},
    /** The Supplies bubble: look at the supply shelf beside the tank. */
    val onSupplies: () -> Unit = {},
    /** Full-screen screensaver: slow cinematic moves and fish follows with soft transitions. */
    val onScreensaver: () -> Unit = {},
    /** More → "Thought bubbles": speech bubbles over a few animals saying what they are thinking. */
    val thoughtsOn: Boolean = false,
    val onThoughts: () -> Unit = {},
    /** Fix tank: what the tank check says the tank needs now, and how many fixes are still playing. */
    val fixes: List<com.reeflive.core.sim.CareFix> = emptyList(),
    val fixing: Int = 0,
    val onFix: (com.reeflive.core.sim.CareFix) -> Unit = {},
    val onFixAll: () -> Unit = {},
    /** Tank Health: every living animal's hunger bar (core HungryFeed) and "Feed the hungry ones". */
    val hunger: List<com.reeflive.core.sim.HungerRow> = emptyList(),
    val onFeedHungry: () -> Unit = {},
    /** Tank Health: each coral's health (core CoralHealth) and, in My tank, the one-tap coral and invert care (core CoralCare). */
    val coralRows: List<com.reeflive.core.sim.CoralHealth.Row> = emptyList(),
    val coralCare: com.reeflive.core.sim.CoralCare.Plan? = null,
    val onReefCare: (() -> Unit)? = null,
    /** My tank (saved): "Reset tank" becomes "Start over" and the Add life words say it is saved. */
    val myTank: Boolean = false,
    /** My tank's growing speed (slow biology only; animals still move at real speed). */
    val lifePace: com.reeflive.core.sim.LifePace = com.reeflive.core.sim.LifePace.DEFAULT,
    val onLifePace: (com.reeflive.core.sim.LifePace) -> Unit = {},
    /** My tank's fish stores (docs/ECONOMY.md §5): Add life shows their storefronts instead of the free gallery. */
    val stores: List<com.reeflive.app.live.StoreFrontUi> = emptyList(),
    val onStore: (com.reeflive.core.custom.economy.FishStore) -> Unit = {},
    val onStoreShut: (com.reeflive.core.custom.economy.FishStore) -> Unit = {},
)

/**
 * The whole menu layer, laid over the full-screen tank. [open] is the category whose card shows (null = none);
 * [railShown] false tucks the rail / dock to one bubble. [bottomClearance] keeps it above the app's bottom bar.
 */
@Composable
fun LiveMenu(
    landscape: Boolean,
    open: LiveCategory?,
    onOpen: (LiveCategory?) -> Unit,
    railShown: Boolean,
    onRailShown: (Boolean) -> Unit,
    panel: SandboxPanelState,
    actions: SandboxActions,
    tools: LiveTools,
    bottomClearance: Dp,
    modifier: Modifier = Modifier,
    /** Landscape: where the cards stop (the bottom bar's top), when the rail itself may run down beside the bar. */
    cardBottomClearance: Dp = bottomClearance,
) {
    val reduce = ReefTheme.reduceMotion
    val cats = LiveCategory.shown(tools.sandbox)
    val shownOpen = open?.takeIf { it in cats && it != LiveCategory.SUPPLIES }
    // auto-dismiss: any touch inside the card restarts the timer
    var touches by remember { mutableIntStateOf(0) }
    var more by remember(shownOpen) { mutableStateOf(false) }
    LaunchedEffect(shownOpen, touches, more) {
        if (shownOpen != null) { delay(if (more) CARD_IDLE_MORE_MS else CARD_IDLE_MS); onOpen(null) }
    }
    val tap: (LiveCategory) -> Unit = { c ->
        when {
            c == LiveCategory.SUPPLIES -> { onOpen(null); tools.onSupplies() }
            c == shownOpen -> onOpen(null)
            else -> onOpen(c)
        }
    }
    val close = { onOpen(null) }
    val cardModifier = Modifier.pointerInput(Unit) {
        awaitEachGesture { awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial); touches++ }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val boxH = maxHeight
        val enterCard = { origin: TransformOrigin ->
            fadeIn(liveSpec<Float>(reduce)) + scaleIn(liveSpec(reduce, ReefMotion.SLOW), initialScale = 0.6f, transformOrigin = origin)
        }
        val exitCard = { origin: TransformOrigin ->
            fadeOut(liveSpec<Float>(reduce, ReefMotion.FAST)) + scaleOut(liveSpec(reduce), targetScale = 0.7f, transformOrigin = origin)
        }
        if (landscape) {
            val cardW = (maxWidth * 0.35f).coerceIn(260.dp, 380.dp)
            // the rail: every category but More, down the left edge
            val railCats = cats - LiveCategory.MORE
            Row(
                Modifier
                    .fillMaxHeight()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Top)),
            ) {
                Rail(railCats, shownOpen, tap, railShown, onRailShown, tools, vertical = true, modifier = Modifier.padding(bottom = bottomClearance))
                AnimatedVisibility(
                    modifier = Modifier.padding(bottom = cardBottomClearance),
                    visible = shownOpen != null && shownOpen != LiveCategory.MORE && railShown,
                    enter = enterCard(TransformOrigin(0f, 0.3f)),
                    exit = exitCard(TransformOrigin(0f, 0.3f)),
                ) {
                    val cat = shownOpen?.takeIf { it != LiveCategory.MORE } ?: LiveCategory.LIGHT
                    MenuCard(cat, more, { more = it }, close, panel, actions, tools, cardModifier.width(cardW).fillMaxHeight().padding(vertical = 8.dp), onTuck = { onRailShown(false) })
                }
            }
            // More (time, reset, tools) is its own bubble in the bottom-right corner, its card opening from there
            Column(
                Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End))
                    .padding(end = 12.dp, bottom = bottomClearance),
                horizontalAlignment = Alignment.End,
            ) {
                AnimatedVisibility(
                    visible = shownOpen == LiveCategory.MORE,
                    enter = enterCard(TransformOrigin(1f, 1f)),
                    exit = exitCard(TransformOrigin(1f, 1f)),
                ) {
                    MenuCard(
                        LiveCategory.MORE, more, { more = it }, close, panel, actions, tools,
                        cardModifier.width(cardW).heightIn(max = boxH * 0.72f).padding(bottom = 8.dp),
                        onTuck = { onRailShown(!railShown) }, tucked = !railShown,
                    )
                }
                LiveRailButton(
                    LiveCategory.MORE.glyph, LiveCategory.MORE.label, LiveTheme.style.tone(LiveCategory.MORE),
                    onClick = { tap(LiveCategory.MORE) },
                    selected = shownOpen == LiveCategory.MORE,
                    size = 52.dp,
                )
            }
        } else {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = bottomClearance)
                    .padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedVisibility(
                    visible = shownOpen != null && railShown,
                    enter = enterCard(TransformOrigin(0.5f, 1f)),
                    exit = exitCard(TransformOrigin(0.5f, 1f)),
                ) {
                    val cat = shownOpen ?: LiveCategory.MORE
                    MenuCard(cat, more, { more = it }, close, panel, actions, tools, cardModifier.fillMaxWidth().heightIn(max = boxH * 0.46f).padding(bottom = 8.dp))
                }
                Rail(cats, shownOpen, tap, railShown, onRailShown, tools, vertical = false)
            }
        }
    }
}

/**
 * The bubbles: a column (landscape rail) or a row (portrait dock), plus the tuck bubble. The bubbles size themselves
 * to the room: full 60 dp with labels when everything fits, smaller (never under 48 dp) and label-less on short
 * landscape phones, so a child sees every category without scrolling; if even that does not fit, the strip scrolls.
 */
@Composable
private fun Rail(
    cats: List<LiveCategory>,
    open: LiveCategory?,
    onTap: (LiveCategory) -> Unit,
    shown: Boolean,
    onShown: (Boolean) -> Unit,
    tools: LiveTools,
    vertical: Boolean,
    modifier: Modifier = Modifier,
) {
    val s = LiveTheme.style
    val reduce = ReefTheme.reduceMotion
    val turn by animateFloatAsState(if (shown) 0f else 180f, ReefMotion.bouncy(reduce), label = "tuck")
    val tuck: @Composable () -> Unit = {
        LiveRailButton(
            null, if (shown) "Tuck" else "Menu", Candy.white,
            onClick = { onShown(!shown) },
            size = 40.dp,
            showLabel = !shown && !vertical,
            content = {
                Box(Modifier.graphicsLayer { rotationZ = (if (vertical) -90f else 180f) + turn }) {
                    Glyph(ReefGlyphs.chevronUp, size = 22.dp, tint = s.onCandy)
                }
            },
        )
    }
    val items: @Composable (bubble: Dp, labels: Boolean, compact: Boolean) -> Unit = { bubble, labels, compact ->
        cats.forEach { c ->
            AnimatedVisibility(shown, enter = fadeIn(liveSpec(reduce)) + scaleIn(liveSpec(reduce), initialScale = 0.3f), exit = fadeOut(liveSpec(reduce, ReefMotion.FAST)) + scaleOut(liveSpec(reduce), targetScale = 0.3f)) {
                LiveRailButton(
                    c.glyph, c.label, s.tone(c), onClick = { onTap(c) },
                    selected = c == open || (c == LiveCategory.SUPPLIES && tools.cleaning),
                    size = bubble,
                    showLabel = labels,
                    compactLabel = compact,
                    modifier = if (vertical) Modifier.padding(vertical = 2.dp) else Modifier.padding(horizontal = 2.dp),
                )
            }
        }
    }
    val n = cats.size.coerceAtLeast(1)
    if (vertical) {
        BoxWithConstraints(modifier.width(s.railWidth).fillMaxHeight()) {
            // one slot per category (bubble + shadow + 4 dp), and the tuck bubble on top when there is room for it;
            // a 44 dp bubble still makes a 50 dp tall, 84 dp wide touch target with its shadow
            val room = maxHeight - 10.dp
            val withTuck = room - 50.dp >= (44.dp + s.depth + 4.dp) * n || !shown
            val per = (if (withTuck) room - 50.dp else room) / n
            val labels = per >= s.railButton + s.depth + 30.dp
            val bubble = if (labels) s.railButton else (per - s.depth - 4.dp).coerceIn(44.dp, s.railButton)
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 6.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (withTuck) tuck()
                items(bubble, labels, false)
            }
        }
    } else if (!shown) {
        // tucked: one bubble to bring the dock back
        Box(modifier.fillMaxWidth().padding(6.dp), contentAlignment = Alignment.CenterEnd) { tuck() }
    } else {
        // the dock: one slot per category across the width (it stays out; More → Hide buttons clears the screen)
        BoxWithConstraints(modifier.fillMaxWidth()) {
            val per = (maxWidth - 12.dp) / n
            val compact = per < 76.dp
            val bubble = (per - 8.dp).coerceIn(44.dp, s.railButton)
            Row(
                Modifier
                    .fillMaxWidth()
                    .livePanel(s)
                    .horizontalScroll(rememberScrollState())
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                items(bubble, true, compact)
            }
        }
    }
}

/** One category's card: header, big tiles, and the More fold-out. */
@Composable
private fun MenuCard(
    cat: LiveCategory,
    more: Boolean,
    onMore: (Boolean) -> Unit,
    close: () -> Unit,
    panel: SandboxPanelState,
    actions: SandboxActions,
    tools: LiveTools,
    modifier: Modifier,
    onTuck: (() -> Unit)? = null,
    tucked: Boolean = false,
) {
    val s = LiveTheme.style
    val reduce = ReefTheme.reduceMotion
    val scroll = rememberScrollState()
    LaunchedEffect(cat) { scroll.scrollTo(0) }
    Column(modifier.livePanel(s)) {
        // header on the category's colour
        Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 2.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            LiveRailButton(cat.glyph, cat.label, s.tone(cat), onClick = {}, size = 40.dp, showLabel = false)
            Spacer(Modifier.width(10.dp))
            AnimatedContent(cat, transitionSpec = { fadeIn(liveSpec(reduce, crossfade = true)) togetherWith fadeOut(liveSpec(reduce, ReefMotion.FAST, crossfade = true)) }, label = "cardTitle", modifier = Modifier.weight(1f)) { c ->
                Text(c.title, style = s.titleText, color = s.label, modifier = Modifier.semantics { heading() })
            }
            CloseDot(close, "Close the ${cat.label} card")
        }
        AnimatedContent(
            cat,
            transitionSpec = { (fadeIn(liveSpec(reduce, crossfade = true)) + scaleIn(liveSpec(reduce), initialScale = 0.92f)) togetherWith fadeOut(liveSpec(reduce, ReefMotion.FAST, crossfade = true)) },
            label = "cardBody",
            modifier = Modifier.weight(1f, fill = false),
        ) { c ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .padding(horizontal = 12.dp)
                    .padding(top = 6.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CardBody(c, close, panel, actions, tools, onMore = { onMore(true) }, onTuck = onTuck, tucked = tucked)
                if (hasMore(c, tools.sandbox)) {
                    val turn by animateFloatAsState(if (more) 180f else 0f, ReefMotion.bouncy(reduce), label = "moreTurn")
                    LivePill(
                        if (more) "Less" else "More",
                        { onMore(!more) },
                        Modifier.align(Alignment.CenterHorizontally),
                        tone = s.tone(c),
                        trailing = { Glyph(ReefGlyphs.chevronUp, size = 18.dp, tint = s.onCandy, modifier = Modifier.graphicsLayer { rotationZ = 180f + turn }) },
                    )
                    AnimatedVisibility(more, enter = fadeIn(liveSpec(reduce)) + expandVertically(liveSpec(reduce)), exit = fadeOut(liveSpec(reduce, ReefMotion.FAST)) + shrinkVertically(liveSpec(reduce))) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { MoreBody(c, panel, actions, tools, close) }
                    }
                }
            }
        }
    }
}

private fun hasMore(c: LiveCategory, sandbox: Boolean) = sandbox && c in setOf(LiveCategory.LIGHT, LiveCategory.LIFE)

@Composable
private fun MoreBody(c: LiveCategory, panel: SandboxPanelState, actions: SandboxActions, tools: LiveTools, close: () -> Unit) {
    when (c) {
        LiveCategory.LIGHT -> {
            LightMore(panel, actions)
            WaterMore(panel, actions, withMode = false)
            CleanMore(actions)
            TroubleTiles(LiveTheme.style.tone(LiveCategory.TROUBLE), close, actions, tools)
            TroubleMore(panel, actions)
        }
        LiveCategory.LIFE -> LifeMore(panel, actions)
        else -> Unit
    }
}

/** A tile spec for the grids below. [closes] = the card shuts after it so the tank animation can be watched. */
private class T(
    val label: String,
    val glyph: ReefGlyph?,
    val on: Boolean? = null,
    val danger: Boolean = false,
    val closes: Boolean = true,
    val sub: String? = null,
    val icon: (@Composable () -> Unit)? = null,
    val run: () -> Unit,
)

@Composable
private fun Tiles(tone: Color, close: () -> Unit, items: List<T>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = (maxWidth / 96.dp).toInt().coerceIn(2, 5)
        TileGrid(items.size, cols) { i, cell ->
            val t = items[i]
            LiveTile(
                t.glyph, t.label, tone, onClick = { t.run(); if (t.closes) close() }, modifier = cell,
                on = t.on, danger = t.danger, sub = t.sub, icon = t.icon,
            )
        }
    }
}

@Composable
private fun ColumnScope.CardBody(
    c: LiveCategory,
    close: () -> Unit,
    panel: SandboxPanelState,
    actions: SandboxActions,
    tools: LiveTools,
    onMore: () -> Unit,
    onTuck: (() -> Unit)?,
    tucked: Boolean,
) {
    val s = LiveTheme.style
    val tone = s.tone(c)
    val now = panel.now
    fun row(id: String): SandboxParamDef? = SandboxParams.byId(id)
    fun isOn(id: String): Boolean = row(id)?.let { it.valueIn(now) >= (it.min + it.max) / 2 } ?: false
    fun flip(id: String) { row(id)?.let { d -> actions.onSet(d, if (isOn(id)) d.min else d.max) } }
    fun bump(id: String, delta: Double) { row(id)?.let { d -> actions.onSet(d, d.spec.snap(d.valueIn(now) + delta)) } }
    when (c) {
        // never a card: the bubble opens the 3D supply shelf (feed, care and tools live there)
        LiveCategory.SUPPLIES -> Unit
        LiveCategory.FIX -> TankHealthBody(tools.readouts, tools.hunger, tools.onFeedHungry, tools.fixes, tools.fixing, tone, tools.onFix, tools.onFixAll, close,
            tools.coralRows, tools.coralCare, tools.onReefCare)
        LiveCategory.LIGHT -> {
            LiveHelp("Feeding, water changes, doses and cleaning are on the supply shelf.")
            LiveSectionTitle("Water")
            Tiles(tone, close, listOf(
                T("Warmer", ReefGlyphs.flame, closes = false) { bump("temp", 1.0) },
                T("Cooler", ReefGlyphs.snowflake, closes = false) { bump("temp", -1.0) },
                T("Saltier", ReefGlyphs.dropPlus, closes = false) { bump("sal", 0.002) },
                T("Less salty", ReefGlyphs.dropMinus, closes = false) { bump("sal", -0.002) },
                T("Medicine", ReefGlyphs.bottle, closes = false) { onMore() },
            ))
            LiveSectionTitle("Lamps")
            Tiles(tone, close, listOf(
                T("Lights on", ReefGlyphs.sun) { actions.onLights(true) },
                T("Lights off", ReefGlyphs.moon) { actions.onLights(false) },
                T("Normal day", ReefGlyphs.clockSun) { actions.onLights(null) },
            ))
            LiveSectionTitle("Pumps & gear")
            Tiles(tone, close, listOf(
                T("Left pump", ReefGlyphs.powerhead, on = isOn("pump_left"), closes = false) { flip("pump_left") },
                T("Right pump", ReefGlyphs.powerhead.mirrored(), on = isOn("pump_right"), closes = false) { flip("pump_right") },
                T("Skimmer", ReefGlyphs.skimmer, on = isOn("eq_skimmer"), closes = false) { flip("eq_skimmer") },
                T("Filter sock", ReefGlyphs.sock, on = isOn("eq_sock"), closes = false) { flip("eq_sock") },
                T("Carbon", ReefGlyphs.carbon, on = isOn("eq_carbon"), closes = false) { flip("eq_carbon") },
                T("Algae garden", ReefGlyphs.refugium, on = isOn("eq_refugium"), closes = false) { flip("eq_refugium") },
                T("Germ zapper", ReefGlyphs.uvLamp, on = isOn("eq_uv"), closes = false) { flip("eq_uv") },
                T("Build rockwork", ReefGlyphs.rocks, sub = "Add white dry rock") { tools.onAquascape() },
            ))
            LiveHelp("More has the exact sliders, medicine and trouble you can cause on purpose.")
        }
        LiveCategory.LIFE -> if (tools.myTank) {
            // My tank buys its animals at the fish stores; the free gallery stays in the sandbox
            StoreStreet(tools.stores, { close(); tools.onStore(it) }, tools.onStoreShut)
            LiveSectionTitle("Tiny life & helpers")
            Tiles(tone, close, listOf(
                T("Pods", ReefGlyphs.crumbs) { actions.onMicro(MicroAction.DOSE_TISBE) },
                T("Phyto", ReefGlyphs.seaweed) { actions.onMicro(MicroAction.DOSE_PHYTO) },
                T("Spare shells", ReefGlyphs.shell) { actions.onAddSpareShells() },
            ))
        } else {
            StockGallery(panel.stock, { card, n, size -> actions.onAddMany(card.key, n, size) }, actions.onReset, myTank = tools.myTank)
            LiveSectionTitle("Tiny life & helpers")
            Tiles(tone, close, listOf(
                T("Pods", ReefGlyphs.crumbs) { actions.onMicro(MicroAction.DOSE_TISBE) },
                T("Phyto", ReefGlyphs.seaweed) { actions.onMicro(MicroAction.DOSE_PHYTO) },
                T("Peppermint shrimp", ReefGlyphs.shrimp) { actions.onAddPestControl(PestController.PEPPERMINT_SHRIMP) },
                T("Berghia slugs", ReefGlyphs.slug) { actions.onAddPestControl(PestController.BERGHIA) },
                T("Spare shells", ReefGlyphs.shell) { actions.onAddSpareShells() },
            ))
        }
        LiveCategory.TROUBLE -> Unit
        LiveCategory.VIEW -> {
            LiveSectionTitle("Time")
            TimeSlider(tools.speed, tools.simTimeSec, tools.onSpeed, tone = tone)
            LiveHelp(if (tools.sandbox) SandboxFriendly.speedHelp(panel.speed) else "Faster than real time runs on a pretend copy of your tank. Slide back to 1× to return to your real tank.")
            if (tools.myTank) {
                LiveSectionTitle("Growing speed")
                Tiles(tone, close, com.reeflive.core.sim.LifePace.entries.map { p ->
                    T(p.label, lifePaceGlyph(p), on = tools.lifePace == p, closes = false) { tools.onLifePace(p) }
                })
                LiveHelp(tools.lifePace.blurb + ". Growing, spreading and babies speed up; the fish still swim at real speed.")
            }
            if (tools.fish.isNotEmpty()) {
                LiveSectionTitle("Follow a fish")
                Tiles(tone, close, tools.fish.map { (id, name) -> T(name, ReefGlyphs.fish) { tools.onFollowFish(id) } })
            }
            LiveSectionTitle("Look around")
            Tiles(tone, close, listOfNotNull(
                T(if (tools.touring) "Stop tour" else "Auto tour", ReefGlyphs.eye, on = tools.touring) { tools.onTour() },
                T("Submarine", ReefGlyphs.bubbles, on = tools.subOn, sub = "Swim with the fish") { tools.onSubmarine() },
                T("Look closer", ReefGlyphs.microscope, sub = "Tiny critters") { tools.onMicroscope() },
                if (tools.sandbox) T(if (panel.timelapseStatus == null) "Time-lapse" else "See time-lapse", ReefGlyphs.timelapse, sub = panel.timelapseStatus) { actions.onTimelapse() } else null,
                T("Torch", ReefGlyphs.led, on = tools.torchOn, closes = false, sub = "Drag to aim") { tools.onTorch() },
                T("Screensaver", ReefGlyphs.moon, sub = "Relax and watch") { tools.onScreensaver() },
                T("Hide buttons", ReefGlyphs.eyeOff, sub = "Just the tank") { tools.onHide() },
            ))
            if (!tools.sandbox) {
                // the lamp looks only change the picture: My tank has real lights under Tank
                LiveSectionTitle("Lamp look")
                Tiles(tone, close, ViewLight.entries.map { v ->
                    T(v.label, viewLightGlyph(v), on = tools.viewLight == v, closes = false) { tools.onViewLight(v) }
                })
            }
        }
        LiveCategory.MORE -> {
            LiveSectionTitle("Sound & helpers")
            Tiles(tone, close, listOfNotNull(
                T(if (tools.soundOn) "Sound on" else "Sound off", if (tools.soundOn) ReefGlyphs.speaker else ReefGlyphs.speakerOff, on = tools.soundOn, closes = false) { tools.onSound() },
                T("Hermie", null, on = tools.hermieOn, closes = false, icon = { HermieArt(HermieMood.HAPPY, remember { Animatable(0f) }, true, Modifier.size(36.dp)) }) { tools.onHermie() },
                T("Happy meter", ReefGlyphs.heart, on = tools.meterOn, closes = false) { tools.onMeter() },
                T("Thought bubbles", ReefGlyphs.bubbles, on = tools.thoughtsOn, closes = false, sub = "What fish think") { tools.onThoughts() },
                onTuck?.let { t -> T(if (tucked) "Show menu" else "Tuck menu", ReefGlyphs.chevronUp) { t() } },
            ))
            LiveSectionTitle("Help")
            Tiles(tone, close, listOf(T("What can I do?", ReefGlyphs.help) { tools.onHelp() }))
            if (tools.sandbox) {
                LiveSectionTitle(if (tools.myTank) "Start again" else "Reset")
                Tiles(tone, close, listOf(T(if (tools.myTank) "Start over" else "Reset tank", ReefGlyphs.reset, danger = true) { actions.onReset() }))
            }
        }
    }
}

/** Tank → More: break something on purpose, then speed up time (Look) to see what happens. */
@Composable
private fun TroubleTiles(tone: Color, close: () -> Unit, actions: SandboxActions, tools: LiveTools) {
    LiveSectionTitle("Make trouble on purpose")
    LiveHelp(if (tools.myTank) "This is your saved tank, so trouble here sticks. Tank Health can fix most of it." else "Break something on purpose, then speed up time to see what happens.")
    Tiles(tone, close, listOf(
        T("Heater breaks", ReefGlyphs.plugOff, danger = true) { actions.onHeater(false) },
        T("Heater stuck on", ReefGlyphs.flame, danger = true) { actions.onPreset(SandboxPreset.HEATER_STUCK) },
        T("Pumps stop tonight", ReefGlyphs.powerhead) { actions.onPumpFailTonight() },
        T("Power cut", ReefGlyphs.plugOff, danger = true) { actions.onPreset(SandboxPreset.POWER_OUTAGE) },
        T("Heat wave", ReefGlyphs.thermometer) { tools.onHeatWave() },
        T("Salt crash", ReefGlyphs.dropDown, danger = true) { actions.onPreset(SandboxPreset.SALINITY_CRASH) },
        T("Copper spill", ReefGlyphs.copperBottle, danger = true) { actions.onPreset(SandboxPreset.COPPER_OVERDOSE) },
        T("Too much food", ReefGlyphs.crumbs) { actions.onPreset(SandboxPreset.OVERFEEDING) },
        T("Rockslide", ReefGlyphs.rocks, danger = true) { actions.onPreset(SandboxPreset.ROCKSLIDE) },
        T("No water changes", ReefGlyphs.calendarX) { actions.onPreset(SandboxPreset.MISSED_WATER_CHANGES) },
        T("Pest anemone", ReefGlyphs.pestAnemone) { actions.onAddAiptasia() },
        T("A fish gets sick", ReefGlyphs.flask) { actions.onInfect(Disease.ICH) },
    ))
}

/** A card left alone closes itself after this long (longer with the grown-up sliders out): long enough to read. */
private const val CARD_IDLE_MS = 25_000L
private const val CARD_IDLE_MORE_MS = 45_000L

private fun viewLightGlyph(v: ViewLight): ReefGlyph = when (v) {
    ViewLight.REAL -> ReefGlyphs.clockSun
    ViewLight.DAY -> ReefGlyphs.sun
    ViewLight.DUSK -> ReefGlyphs.sun
    ViewLight.NIGHT -> ReefGlyphs.moon
    ViewLight.MOON -> ReefGlyphs.moon
    ViewLight.BLUE -> ReefGlyphs.uvLamp
}

private fun lifePaceGlyph(p: com.reeflive.core.sim.LifePace): ReefGlyph = when (p) {
    com.reeflive.core.sim.LifePace.REAL -> ReefGlyphs.clockSun
    com.reeflive.core.sim.LifePace.FAST -> ReefGlyphs.seaweed
    com.reeflive.core.sim.LifePace.FASTER -> ReefGlyphs.timelapse
}
