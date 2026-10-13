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
import com.reeflive.core.sim.FoodGroup
import com.reeflive.core.sim.FoodKind
import com.reeflive.core.sim.MaintenanceTask
import com.reeflive.core.sim.MicroAction
import com.reeflive.core.sim.PestController
import kotlinx.coroutines.delay

/*
 * The live-tank menu (UI_STYLE §10.1): a few big candy bubbles, one per kid-friendly category. Landscape: a slim rail
 * of bubbles down the left edge (≈ 84 dp), so the tank keeps the rest of the screen; tapping a bubble opens a compact
 * glass card beside it (at most ~35 % of the width). Portrait: a dock of the same bubbles above the app's bottom bar,
 * the card opening above it. Cards are big tiles first; a "More" button folds out the grown-up sliders. A card closes
 * itself after a one-tap action (so the animation in the tank can be watched), after 12 s without a touch, on its ×,
 * or when the bubble is tapped again. The rail tucks away to a single bubble.
 */

/** Menu categories, each with its candy tone in [LiveTheme]. */
enum class LiveCategory(val label: String, val glyph: ReefGlyph, val title: String) {
    FEED("Feed", ReefGlyphs.feedTin, "Feed the fish"),
    WATER("Water", ReefGlyphs.droplet, "Water"),
    LIGHT("Light", ReefGlyphs.sun, "Light & flow"),
    LIFE("Add life", ReefGlyphs.fish, "Add life"),
    CLEAN("Clean", ReefGlyphs.flipper, "Clean & care"),
    TROUBLE("Trouble", ReefGlyphs.whatIf, "Tank trouble"),
    MORE("More", ReefGlyphs.sparkleTip, "More"),
    ;

    companion object {
        /** Watch mode can only feed, clean the glass and look: the rest needs the pretend tank. */
        fun shown(sandbox: Boolean): List<LiveCategory> = if (sandbox) entries.toList() else listOf(FEED, CLEAN, MORE)
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
    val shownOpen = open?.takeIf { it in cats && !(it == LiveCategory.CLEAN && !tools.sandbox) }
    // auto-dismiss: any touch inside the card restarts the timer
    var touches by remember { mutableIntStateOf(0) }
    var more by remember(shownOpen) { mutableStateOf(false) }
    LaunchedEffect(shownOpen, touches, more) {
        if (shownOpen != null) { delay(if (more) 30_000 else 12_000); onOpen(null) }
    }
    val tap: (LiveCategory) -> Unit = { c ->
        when {
            c == LiveCategory.CLEAN && !tools.sandbox -> tools.onCleanGlass()
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
                    val cat = shownOpen?.takeIf { it != LiveCategory.MORE } ?: LiveCategory.FEED
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
                    selected = c == open || (c == LiveCategory.CLEAN && tools.cleaning),
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
        // the dock: one slot per category across the width (the mode toggle or a tap on the water tucks it away)
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
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { MoreBody(c, panel, actions) }
                    }
                }
            }
        }
    }
}

private fun hasMore(c: LiveCategory, sandbox: Boolean) = sandbox && c in setOf(LiveCategory.WATER, LiveCategory.LIGHT, LiveCategory.LIFE, LiveCategory.CLEAN, LiveCategory.TROUBLE)

@Composable
private fun MoreBody(c: LiveCategory, panel: SandboxPanelState, actions: SandboxActions) {
    when (c) {
        LiveCategory.WATER -> WaterMore(panel, actions)
        LiveCategory.LIGHT -> LightMore(panel, actions)
        LiveCategory.LIFE -> LifeMore(panel, actions)
        LiveCategory.CLEAN -> CleanMore(actions)
        LiveCategory.TROUBLE -> TroubleMore(panel, actions)
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
        LiveCategory.FEED -> {
            LiveHelp("Tap a food to drop it in. Watch it go into the tank!")
            val groups = FoodGroup.entries
            groups.forEach { g ->
                val foods = FoodKind.entries.filter { it.group == g }
                if (foods.isNotEmpty()) {
                    LiveSectionTitle(g.label)
                    Tiles(tone, close, foods.map { k -> T(k.label, null, icon = { FoodPicture(k, size = 36.dp) }) { tools.onFeed(k) } })
                }
            }
        }
        LiveCategory.WATER -> {
            if (tools.readouts.isNotEmpty()) WaterStrip(tools.readouts)
            LiveSectionTitle("Water")
            Tiles(tone, close, listOf(
                T("Change water", ReefGlyphs.waveSparkle, sub = "25 %") { actions.onWaterChange(0.25) },
                T("Small change", ReefGlyphs.droplet, sub = "10 %") { actions.onWaterChange(0.10) },
                T("Warmer", ReefGlyphs.flame, closes = false) { bump("temp", 1.0) },
                T("Cooler", ReefGlyphs.snowflake, closes = false) { bump("temp", -1.0) },
                T("Saltier", ReefGlyphs.dropPlus, closes = false) { bump("sal", 0.002) },
                T("Less salty", ReefGlyphs.dropMinus, closes = false) { bump("sal", -0.002) },
            ))
            LiveSectionTitle("Coral minerals")
            Tiles(tone, close, listOf(
                T("Shell builder", ReefGlyphs.shell, sub = "Alk +1") { actions.onDose("alk", 1.0) },
                T("Calcium", ReefGlyphs.branch, sub = "+20") { actions.onDose("ca", 20.0) },
                T("Magnesium", ReefGlyphs.rings, sub = "+50") { actions.onDose("mg", 50.0) },
            ))
        }
        LiveCategory.LIGHT -> {
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
            ))
        }
        LiveCategory.LIFE -> {
            StockGallery(panel.stock, { card, n -> actions.onAddMany(card.speciesId, n) }, actions.onReset)
            LiveSectionTitle("Tiny life & helpers")
            Tiles(tone, close, listOf(
                T("Pods", ReefGlyphs.crumbs) { actions.onMicro(MicroAction.DOSE_TISBE) },
                T("Phyto", ReefGlyphs.seaweed) { actions.onMicro(MicroAction.DOSE_PHYTO) },
                T("Peppermint shrimp", ReefGlyphs.shrimp) { actions.onAddPestControl(PestController.PEPPERMINT_SHRIMP) },
                T("Berghia slugs", ReefGlyphs.slug) { actions.onAddPestControl(PestController.BERGHIA) },
                T("Spare shells", ReefGlyphs.shell) { actions.onAddSpareShells() },
            ))
        }
        LiveCategory.CLEAN -> {
            Tiles(tone, close, listOf(
                T("Clean glass", ReefGlyphs.flipper, on = tools.cleaning) { tools.onCleanGlass() },
                T("New sock", ReefGlyphs.sock) { actions.onMaintain(MaintenanceTask.CHANGE_SOCK) },
                T("Fresh carbon", ReefGlyphs.carbon) { actions.onMaintain(MaintenanceTask.REPLACE_CARBON) },
                T("Trim algae", ReefGlyphs.refugium) { actions.onMaintain(MaintenanceTask.HARVEST_MACRO) },
                T("Zap pest", ReefGlyphs.pestAnemone) { actions.onRemoveAiptasia() },
                T("Move rocks", ReefGlyphs.rocks) { actions.onRearrangeRock() },
                T("Settle in", ReefGlyphs.heart, sub = "Get used to now") { actions.onAcclimateAll() },
                T("Medicine", ReefGlyphs.bottle, closes = false) { onMore() },
            ))
        }
        LiveCategory.TROUBLE -> {
            LiveHelp("Break something on purpose, then speed up time (in More) to see what happens. Only the pretend tank changes.")
            Tiles(tone, close, listOf(
                T("Heater breaks", ReefGlyphs.plugOff, danger = true) { actions.onHeater(false) },
                T("Heater stuck on", ReefGlyphs.flame, danger = true) { actions.onPreset(SandboxPreset.HEATER_STUCK) },
                T("Fix heater", ReefGlyphs.flame) { actions.onHeater(true) },
                T("Pumps stop tonight", ReefGlyphs.powerhead) { actions.onPumpFailTonight() },
                T("Lamp goes out", ReefGlyphs.moon) { actions.onLights(false) },
                T("Power cut", ReefGlyphs.plugOff, danger = true) { actions.onPreset(SandboxPreset.POWER_OUTAGE) },
                T("Heat wave", ReefGlyphs.thermometer) { tools.onHeatWave() },
                T("Salt crash", ReefGlyphs.dropDown, danger = true) { actions.onPreset(SandboxPreset.SALINITY_CRASH) },
                T("Copper spill", ReefGlyphs.copperBottle, danger = true) { actions.onPreset(SandboxPreset.COPPER_OVERDOSE) },
                T("Too much food", ReefGlyphs.crumbs) { actions.onPreset(SandboxPreset.OVERFEEDING) },
                T("No water changes", ReefGlyphs.calendarX) { actions.onPreset(SandboxPreset.MISSED_WATER_CHANGES) },
                T("Pest anemone", ReefGlyphs.pestAnemone) { actions.onAddAiptasia() },
                T("A fish gets sick", ReefGlyphs.flask) { actions.onInfect(Disease.ICH) },
            ))
        }
        LiveCategory.MORE -> {
            if (tools.sandbox) {
                LiveSectionTitle("Time")
                Tiles(tone, close, panel.speeds.map { x ->
                    T(speedName(x), ReefGlyphs.timelapse, on = x == panel.speed, sub = formatSpeed(x), closes = false) { actions.onSpeed(x) }
                })
                LiveHelp(SandboxFriendly.speedHelp(panel.speed))
                Tiles(tone, close, listOf(
                    T(if (panel.timelapseStatus == null) "Time-lapse" else "See time-lapse", ReefGlyphs.timelapse, sub = panel.timelapseStatus) { actions.onTimelapse() },
                    T("Reset tank", ReefGlyphs.reset, danger = true) { actions.onReset() },
                ))
            } else {
                if (tools.readouts.isNotEmpty()) WaterStrip(tools.readouts)
            }
            LiveSectionTitle("Tools")
            Tiles(tone, close, listOfNotNull(
                onTuck?.let { t -> T(if (tucked) "Show menu" else "Tuck menu", ReefGlyphs.chevronUp) { t() } },
                T("Look closer", ReefGlyphs.microscope) { tools.onMicroscope() },
                T(if (tools.soundOn) "Sound on" else "Sound off", if (tools.soundOn) ReefGlyphs.speaker else ReefGlyphs.speakerOff, on = tools.soundOn, closes = false) { tools.onSound() },
                T("Knock glass", ReefGlyphs.touch) { tools.onTapGlass() },
                T("Hide buttons", ReefGlyphs.eyeOff) { tools.onHide() },
                T("Tips", ReefGlyphs.help) { tools.onHelp() },
                T("Hermie", null, on = tools.hermieOn, closes = false, icon = { HermieArt(HermieMood.HAPPY, remember { Animatable(0f) }, true, Modifier.size(36.dp)) }) { tools.onHermie() },
                T("Happy meter", ReefGlyphs.heart, on = tools.meterOn, closes = false) { tools.onMeter() },
            ))
        }
    }
}

private fun speedName(x: Double): String = when {
    x <= 1.0 -> "Real time"
    x <= 60.0 -> "Fast"
    x <= 240.0 -> "Faster"
    else -> "Super fast"
}
