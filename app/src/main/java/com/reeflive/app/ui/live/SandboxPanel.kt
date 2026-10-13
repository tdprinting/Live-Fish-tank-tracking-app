package com.reeflive.app.ui.live

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Help
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PowerOff
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.reeflive.app.ui.components.ButtonKind
import com.reeflive.app.ui.components.ReefButton
import com.reeflive.app.ui.components.ReefChip
import com.reeflive.app.ui.components.ReefSlider
import com.reeflive.app.ui.components.ReefSliderSpec
import com.reeflive.app.ui.components.SegmentedPills
import com.reeflive.app.ui.theme.GlassCard
import com.reeflive.app.ui.theme.ReefLiveTheme
import com.reeflive.app.ui.theme.ReefMotion
import com.reeflive.app.ui.theme.ReefShapes
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.core.sim.Disease
import com.reeflive.core.sim.MaintenanceTask
import com.reeflive.core.sim.PestController
import com.reeflive.core.sim.TreatmentKind
import com.reeflive.core.sim.WaterState

// ---------------------------------------------------------------- state

/** Fish health summary shown in the dock header so the effect of a change is visible while adjusting. */
data class FishSummary(val alive: Int = 0, val stressed: Int = 0, val sick: Int = 0, val dead: Int = 0) {
    val text: String get() = buildList {
        add(if (alive == 1) "1 fish" else "$alive fish")
        if (stressed > 0) add("$stressed stressed")
        if (sick > 0) add("$sick sick")
        if (dead > 0) add("$dead dead")
    }.joinToString(" · ")
    val worried: Boolean get() = stressed + sick + dead > 0
}

/** The animal selected in the tank (tap a fish), for the resize / remove controls. */
data class SelectedAnimalUi(val id: String, val name: String, val lengthCm: Double)

/** A species that can be added (one already living in the tank). */
data class AddableSpecies(val speciesId: String, val name: String, val sizeCm: Double)

data class SandboxPanelState(
    /** Live sandbox values (null until the first snapshot). */
    val now: TankReading? = null,
    /** The real tank, for the slider markers. */
    val real: TankReading? = null,
    val category: SandboxCategory = SandboxCategory.WATER,
    /** Portrait: the body is showing (false = only the header and the tabs peek out). */
    val expanded: Boolean = true,
    val speed: Double = 1.0,
    val speeds: List<Double> = listOf(1.0, 60.0, 240.0, 1000.0),
    val controlMode: SandboxControlMode = SandboxControlMode.SET,
    val activePreset: SandboxPreset? = null,
    val fish: FishSummary = FishSummary(),
    val selected: SelectedAnimalUi? = null,
    val addable: List<AddableSpecies> = emptyList(),
    val predation: Boolean = true,
    /** Time-lapse status line for the Events tab ("Running 42 %", "Results ready"); null when idle. */
    val timelapseStatus: String? = null,
    /** "Add life" gallery: every species with a model, and how many of each are in the sandbox. */
    val stock: StockUi = StockUi(),
)

/** Everything the dock can ask for. */
data class SandboxActions(
    val onSet: (SandboxParamDef, Double) -> Unit = { _, _ -> },
    val onCategory: (SandboxCategory) -> Unit = {},
    val onToggleExpanded: () -> Unit = {},
    val onSpeed: (Double) -> Unit = {},
    val onControlMode: (SandboxControlMode) -> Unit = {},
    val onPreset: (SandboxPreset) -> Unit = {},
    val onDismissPreset: () -> Unit = {},
    val onFeed: () -> Unit = {},
    val onTapGlass: () -> Unit = {},
    val onWaterChange: (fraction: Double) -> Unit = {},
    val onDose: (paramId: String, delta: Double) -> Unit = { _, _ -> },
    val onHeater: (working: Boolean) -> Unit = {},
    val onLights: (on: Boolean?) -> Unit = {},
    val onPredation: (Boolean) -> Unit = {},
    val onResize: (id: String, sizeCm: Double) -> Unit = { _, _ -> },
    val onRemove: (id: String) -> Unit = {},
    val onAdd: (AddableSpecies) -> Unit = {},
    val onReset: () -> Unit = {},
    val onAcclimateAll: () -> Unit = {},
    val onRearrangeRock: () -> Unit = {},
    val onPumpFailTonight: () -> Unit = {},
    /** Opens the time-lapse sheet (set up a run, or see the progress / report of the current one). */
    val onTimelapse: () -> Unit = {},
    /** "Add life" gallery: add [count] of a species to the sandbox. */
    val onAddMany: (speciesId: String, count: Int) -> Unit = { _, _ -> },
    /** "Pests & treatments" tab. */
    val onAddAiptasia: () -> Unit = {},
    val onRemoveAiptasia: () -> Unit = {},
    val onAddPestControl: (PestController) -> Unit = {},
    val onStartTreatment: (TreatmentKind) -> Unit = {},
    val onStopTreatment: (TreatmentKind) -> Unit = {},
    val onInfect: (Disease) -> Unit = {},
    val onMaintain: (MaintenanceTask) -> Unit = {},
    val onAddSpareShells: () -> Unit = {},
    /** Shows the sandbox tips again. */
    val onHelp: () -> Unit = {},
)

/** One dose button: [paramId] for Command.Dose, [delta] in sim units. */
private data class DoseChip(val label: String, val paramId: String, val delta: Double)

private val doseChips = listOf(
    DoseChip("Alkalinity +1 dKH", "alk", 1.0),
    DoseChip("Calcium +20 ppm", "ca", 20.0),
    DoseChip("Magnesium +50 ppm", "mg", 50.0),
    DoseChip("Nitrate +5 ppm", "no3", 5.0),
    DoseChip("Phosphate +0.05 ppm", "po4", 0.05),
)

/** A big one-tap button for the "Quick changes" rows. */
private data class Quick(val label: String, val icon: ImageVector, val onClick: () -> Unit)

// ---------------------------------------------------------------- dock

/**
 * Sandbox controls. Portrait: a see-through glass panel floating over the bottom of the tank; collapsed it is only the
 * header and the tab row, expanded the body adds [bodyHeight] (the screen keeps the whole panel to about 40 % of the
 * height). Landscape ([side] = true): a full-height column beside the tank. [bottomClearance] keeps the content clear
 * of the floating bottom navigation.
 */
@Composable
fun SandboxDock(
    state: SandboxPanelState,
    actions: SandboxActions,
    modifier: Modifier = Modifier,
    side: Boolean = false,
    bottomClearance: Dp = 0.dp,
    bodyHeight: Dp? = null,
) {
    val c = ReefTheme.colors
    val reduce = ReefTheme.reduceMotion
    val shape = if (side) RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp) else ReefShapes.sheet
    // kept here (not in the body) so the scroll position survives the body tucking away while the tank is dragged
    val scroll = rememberScrollState()
    LaunchedEffect(state.category) { scroll.scrollTo(0) }
    Column(
        modifier
            .clip(shape)
            // portrait floats over the tank, so it is a little see-through
            .background(if (side) c.sheet else c.sheet.copy(alpha = c.sheet.alpha * 0.84f))
            .border(1.dp, c.glassBorder, shape),
    ) {
        DockHeader(state, actions, collapsible = !side)
        CategoryTabs(state.category, actions.onCategory)
        if (side) {
            DockBody(state, actions, scroll, Modifier.weight(1f), endPadding = bottomClearance)
        } else {
            AnimatedVisibility(
                visible = state.expanded,
                enter = fadeIn(liveSpec(reduce)) + expandVertically(liveSpec(reduce)),
                exit = fadeOut(liveSpec(reduce, ReefMotion.FAST)) + shrinkVertically(liveSpec(reduce)),
            ) {
                DockBody(
                    state, actions, scroll,
                    if (bodyHeight != null) Modifier.height(bodyHeight) else Modifier.heightIn(max = 320.dp),
                    endPadding = 16.dp,
                )
            }
            Spacer(Modifier.height(bottomClearance))
        }
    }
}

/** Handle + title row. Portrait: tap or drag it to tuck the panel away / bring it back. */
@Composable
private fun DockHeader(state: SandboxPanelState, actions: SandboxActions, collapsible: Boolean) {
    val c = ReefTheme.colors
    val reduce = ReefTheme.reduceMotion
    var m = Modifier.fillMaxWidth()
    if (collapsible) {
        val expanded by rememberUpdatedState(state.expanded)
        val toggle by rememberUpdatedState(actions.onToggleExpanded)
        m = m
            .pointerInput(Unit) {
                var dy = 0f
                val threshold = 24.dp.toPx()
                detectVerticalDragGestures(
                    onDragStart = { dy = 0f },
                    onDragEnd = { if ((dy > threshold && expanded) || (dy < -threshold && !expanded)) toggle() },
                    onVerticalDrag = { change, amount -> change.consume(); dy += amount },
                )
            }
            .clickable(
                onClickLabel = if (state.expanded) "Tuck the sandbox controls away" else "Show the sandbox controls",
                onClick = actions.onToggleExpanded,
            )
    }
    Column(m.padding(start = 8.dp, end = 8.dp, top = if (collapsible) 6.dp else 10.dp)) {
        if (collapsible) {
            // the grab handle (UI_STYLE §6.7)
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(ReefShapes.pill)
                    .background(c.textTertiary),
            )
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            if (collapsible) {
                val turn by animateFloatAsState(if (state.expanded) 180f else 0f, ReefMotion.spring(reduce), label = "chevron")
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.ExpandLess, contentDescription = null, tint = c.textSecondary,
                        modifier = Modifier.graphicsLayer { rotationZ = turn },
                    )
                }
            } else Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Sandbox", style = MaterialTheme.typography.titleMedium, color = c.coral, modifier = Modifier.semantics { heading() })
                val tone by animateColorAsState(
                    if (state.fish.worried) c.cautionText else c.textSecondary, ReefMotion.tween(reduce), label = "fishTone",
                )
                Text(
                    "Pretend tank · " + state.fish.text,
                    style = MaterialTheme.typography.labelMedium,
                    color = tone,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            RoundIconButton(Icons.AutoMirrored.Rounded.Help, "Sandbox tips", actions.onHelp)
        }
    }
}

/** Tab row: icon + plain-words label. Tapping a tab while the panel is tucked away opens it (the screen decides). */
@Composable
private fun CategoryTabs(selected: SandboxCategory, onSelect: (SandboxCategory) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 2.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SandboxFriendly.tabs.forEach { cat ->
            val sel = cat == selected
            val c = ReefTheme.colors
            ReefChip(
                text = SandboxFriendly.tabLabel(cat),
                selected = sel,
                onClick = { onSelect(cat) },
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .semantics { role = Role.Tab; this.selected = sel },
                leading = {
                    Icon(
                        SandboxFriendly.tabIcon(cat), contentDescription = null,
                        tint = if (sel) c.primaryBright else c.textSecondary, modifier = Modifier.size(18.dp),
                    )
                },
            )
        }
    }
}

/** The scrolling body: speed, then the tab's content (crossfading on tab change), then the big Reset button. */
@Composable
private fun DockBody(state: SandboxPanelState, actions: SandboxActions, scroll: ScrollState, modifier: Modifier, endPadding: Dp) {
    val reduce = ReefTheme.reduceMotion
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp)
            .padding(top = 2.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SpeedSelector(state, actions)
        val modes = SandboxControlMode.enabled
        if (modes.size > 1 && state.category in SliderTabs) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("How sliders change things", style = MaterialTheme.typography.labelMedium, color = ReefTheme.colors.textSecondary)
                SegmentedPills(
                    options = modes.map { it.label },
                    selected = modes.indexOf(state.controlMode).coerceAtLeast(0),
                    onSelect = { actions.onControlMode(modes[it]) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        AnimatedVisibility(
            state.activePreset != null,
            enter = fadeIn(liveSpec(reduce)) + expandVertically(liveSpec(reduce)),
            exit = fadeOut(liveSpec(reduce, ReefMotion.FAST)) + shrinkVertically(liveSpec(reduce)),
        ) {
            state.activePreset?.let { PresetBanner(it, actions.onDismissPreset) }
        }
        Crossfade(targetState = state.category, animationSpec = liveSpec(reduce, ReefMotion.FAST, crossfade = true), label = "sandboxTab") { cat ->
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                TabIntro(cat)
                when (cat) {
                    SandboxCategory.EVENTS -> EventsContent(state, actions)
                    SandboxCategory.PESTS -> PestsContent(state, actions)
                    SandboxCategory.STOCK -> StockGallery(state.stock, { card, n -> actions.onAddMany(card.speciesId, n) }, actions.onReset)
                    else -> ParamsContent(state, actions, cat)
                }
                ResetTankButton(actions.onReset)
            }
        }
        Spacer(Modifier.height(endPadding))
    }
}

/** Tabs whose sliders the Step / Ramp / Spike selector applies to. */
private val SliderTabs = setOf(SandboxCategory.WATER, SandboxCategory.CHEMISTRY, SandboxCategory.NUTRIENTS, SandboxCategory.LIGHT_FLOW)

@Composable
private fun SpeedSelector(state: SandboxPanelState, actions: SandboxActions) {
    val c = ReefTheme.colors
    val reduce = ReefTheme.reduceMotion
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Timelapse, contentDescription = null, tint = c.coralSoft, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Time speed", style = MaterialTheme.typography.labelLarge, color = c.text)
        }
        SegmentedPills(
            options = state.speeds.map(::formatSpeed),
            selected = state.speeds.indexOf(state.speed).coerceAtLeast(0),
            onSelect = { actions.onSpeed(state.speeds[it]) },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Simulation speed" },
            selectedColor = c.coralSoft,
        )
        Crossfade(targetState = state.speed, animationSpec = liveSpec(reduce, ReefMotion.FAST, crossfade = true), label = "speedHelp") { s ->
            Text(SandboxFriendly.speedHelp(s), style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
        }
    }
}

@Composable
private fun TabIntro(cat: SandboxCategory) {
    val c = ReefTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        BigIcon(SandboxFriendly.tabIcon(cat), c.coralSoft)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(SandboxFriendly.tabLabel(cat), style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.semantics { heading() })
            Text(SandboxFriendly.tabHelp(cat), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
    }
}

/** Full-width Reset at the end of every tab; the screen asks before it wipes anything. */
@Composable
private fun ResetTankButton(onReset: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ReefButton("Reset tank", onReset, Modifier.fillMaxWidth(), kind = ButtonKind.OUTLINED, icon = Icons.Rounded.RestartAlt)
        Text(
            "Puts the sandbox back to match your real tank. Your real tank never changes here.",
            style = MaterialTheme.typography.labelSmall,
            color = ReefTheme.colors.textTertiary,
        )
    }
}

@Composable
private fun PresetBanner(p: SandboxPreset, onDismiss: () -> Unit) {
    val c = ReefTheme.colors
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ReefShapes.tile,
        tint = c.coral,
        contentPadding = PaddingValues(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(presetIcon(p), contentDescription = null, tint = c.coral, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("${p.label} is happening", style = MaterialTheme.typography.labelLarge, color = c.text)
                Text(p.hint, style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
            }
            RoundIconButton(Icons.Rounded.Close, "Dismiss", onDismiss)
        }
    }
}

// ---------------------------------------------------------------- shared bits

/** 48 dp round touch target with a 22 dp icon. */
@Composable
private fun RoundIconButton(icon: ImageVector, description: String, onClick: () -> Unit, tint: Color? = null) {
    val c = ReefTheme.colors
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint ?: c.textSecondary, modifier = Modifier.size(22.dp))
    }
}

/** Big friendly icon in a soft circle, shown next to every sandbox control. */
@Composable
private fun BigIcon(icon: ImageVector, tint: Color, enabled: Boolean = true) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = if (enabled) 0.16f else 0.06f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) tint else tint.copy(alpha = 0.5f), modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ExplainText(text: String) {
    val c = ReefTheme.colors
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = c.textSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .clip(ReefShapes.overlay)
            .background(c.primary.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun SubHeader(text: String, icon: ImageVector? = null) {
    val c = ReefTheme.colors
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = c.coralSoft, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium, color = c.textSecondary, modifier = Modifier.semantics { heading() })
    }
}

@Composable
private fun Helper(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = ReefTheme.colors.textSecondary)
}

/** Animated on/off switch (thumb slides, track tints); the row around it handles the tap. */
@Composable
fun ReefSwitch(on: Boolean, modifier: Modifier = Modifier) {
    val c = ReefTheme.colors
    val reduce = ReefTheme.reduceMotion
    val track by animateColorAsState(if (on) c.primary else c.glassHi, ReefMotion.tween(reduce, ReefMotion.FAST), label = "switchTrack")
    val thumb by animateColorAsState(if (on) Color.White else c.textSecondary, ReefMotion.tween(reduce, ReefMotion.FAST), label = "switchThumb")
    val x by animateDpAsState(if (on) 20.dp else 0.dp, ReefMotion.spring(reduce), label = "switchX")
    Box(
        modifier
            .size(width = 52.dp, height = 32.dp)
            .clip(ReefShapes.pill)
            .background(track)
            .border(1.dp, c.glassBorder, ReefShapes.pill)
            .padding(4.dp),
    ) {
        Box(Modifier.offset { IntOffset(x.roundToPx(), 0) }.size(24.dp).clip(CircleShape).background(thumb))
    }
}

/** A whole-row switch: big icon, plain title, small expert line, optional (i) explanation, animated switch. */
@Composable
private fun SwitchRow(
    title: String,
    icon: ImageVector,
    on: Boolean,
    onToggle: () -> Unit,
    subtitle: String? = null,
    explain: String? = null,
    enabled: Boolean = true,
) {
    val c = ReefTheme.colors
    val reduce = ReefTheme.reduceMotion
    var showInfo by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().animateContentSize(liveSpec(reduce))) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(ReefShapes.tile)
                .clickable(enabled = enabled, role = Role.Switch, onClickLabel = if (on) "Turn off $title" else "Turn on $title", onClick = onToggle)
                .semantics { stateDescription = if (on) "On" else "Off" }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BigIcon(icon, if (on) c.primaryBright else c.textSecondary, enabled)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = if (enabled) c.text else c.textTertiary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelSmall, color = c.textTertiary, maxLines = 2)
            }
            if (!explain.isNullOrBlank()) {
                RoundIconButton(Icons.Rounded.Info, if (showInfo) "Hide what $title means" else "What is $title?", { showInfo = !showInfo }, tint = if (showInfo) c.primaryBright else c.textTertiary)
            }
            Spacer(Modifier.width(4.dp))
            ReefSwitch(on)
        }
        if (showInfo && !explain.isNullOrBlank()) ExplainText(explain)
    }
}

/** A wrapping row of big (48 dp) one-tap buttons. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickRow(title: String, items: List<Quick>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = ReefTheme.colors.textSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { q -> ReefButton(q.label, q.onClick, kind = ButtonKind.TONAL, icon = q.icon) }
        }
    }
}

// ---------------------------------------------------------------- parameter tabs

@Composable
private fun ParamsContent(state: SandboxPanelState, actions: SandboxActions, cat: SandboxCategory) {
    val defs = SandboxParams.inCategory(cat)
    val (live, pending) = defs.partition { it.supported }
    fun bump(id: String, delta: Double) {
        val def = SandboxParams.byId(id) ?: return
        actions.onSet(def, def.spec.snap(def.valueIn(state.now) + delta))
    }
    when (cat) {
        SandboxCategory.WATER -> QuickRow(
            "Quick changes",
            listOf(
                Quick("Warmer", Icons.Rounded.LocalFireDepartment) { bump("temp", 1.0) },
                Quick("Cooler", SandboxFriendly.coolerIcon) { bump("temp", -1.0) },
                Quick("Saltier", Icons.Rounded.WaterDrop) { bump("sal", 0.002) },
                Quick("Less salty", Icons.Rounded.Waves) { bump("sal", -0.002) },
            ),
        )
        SandboxCategory.NUTRIENTS -> QuickRow(
            "Quick changes",
            listOf(
                Quick("Feed the fish", Icons.Rounded.Restaurant, actions.onFeed),
                Quick("Clean the water", Icons.Rounded.Waves) { actions.onWaterChange(0.25) },
            ),
        )
        SandboxCategory.LIGHT_FLOW -> LightsRow(actions.onLights)
        else -> Unit
    }
    live.forEach { def -> ParamRow(def, state, actions) }
    if (pending.isNotEmpty()) {
        SubHeader("Coming soon")
        pending.forEach { def -> ParamRow(def, state, actions) }
    }
}

/** On/off rows (pumps, equipment) get a switch; everything else a slider. */
@Composable
private fun ParamRow(def: SandboxParamDef, state: SandboxPanelState, actions: SandboxActions) {
    if (def.max - def.min <= def.step) ToggleParam(def, state, actions) else ParamSlider(def, state, actions)
}

@Composable
private fun ToggleParam(def: SandboxParamDef, state: SandboxPanelState, actions: SandboxActions) {
    val f = SandboxFriendly.of(def)
    val on = def.valueIn(state.now) >= (def.min + def.max) / 2
    SwitchRow(
        title = f.title,
        icon = f.icon,
        on = on,
        onToggle = { actions.onSet(def, if (on) def.min else def.max) },
        subtitle = SandboxFriendly.expert(def, state.now) ?: def.pendingNote,
        explain = f.explain,
        enabled = def.supported,
    )
}

@Composable
private fun ParamSlider(def: SandboxParamDef, state: SandboxPanelState, actions: SandboxActions) {
    val now = state.now
    val f = SandboxFriendly.of(def)
    val caption = listOfNotNull(SandboxFriendly.expert(def, now), if (now != null) def.caption?.invoke(now) else null)
        .joinToString(" · ")
        .ifBlank { null }
    ReefSlider(
        spec = def.spec.copy(label = f.title),
        value = def.valueIn(now),
        onValueChange = { actions.onSet(def, it) },
        referenceValue = if (def.supported) state.real?.let(def.read)?.coerceIn(def.min, def.max) else null,
        enabled = def.supported,
        caption = caption,
        disabledReason = def.pendingNote,
        leading = { BigIcon(f.icon, ReefTheme.colors.coralSoft, def.supported) },
        info = f.explain.ifBlank { null },
    )
}

@Composable
private fun LightsRow(onLights: (Boolean?) -> Unit) {
    QuickRow(
        "Lamps",
        listOf(
            Quick("Lights on", Icons.Rounded.LightMode) { onLights(true) },
            Quick("Lights off", Icons.Rounded.DarkMode) { onLights(false) },
            Quick("Normal schedule", Icons.Rounded.Schedule) { onLights(null) },
        ),
    )
}

// ---------------------------------------------------------------- pests tab

/** "Bugs & medicine": one-tap actions first, then the equipment and scenario switches. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PestsContent(state: SandboxPanelState, actions: SandboxActions) {
    val c = ReefTheme.colors
    SubHeader("Pest anemones (aiptasia)", Icons.Rounded.BugReport)
    Helper("Weedy little anemones that sting corals. Peppermint shrimp and Berghia sea slugs eat them.")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ReefButton("Add one", actions.onAddAiptasia, kind = ButtonKind.TONAL, icon = Icons.Rounded.Add)
        ReefButton("Remove one", actions.onRemoveAiptasia, kind = ButtonKind.OUTLINED, icon = Icons.Rounded.Remove)
        ReefButton("Add peppermint shrimp", { actions.onAddPestControl(PestController.PEPPERMINT_SHRIMP) }, kind = ButtonKind.TONAL, icon = Icons.Rounded.Pets)
        ReefButton("Add Berghia slugs", { actions.onAddPestControl(PestController.BERGHIA) }, kind = ButtonKind.TONAL, icon = Icons.Rounded.Pets)
    }

    SubHeader("Medicine", Icons.Rounded.Medication)
    Helper("Start gives every fish the treatment for its usual length. UV runs until you stop it.")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TreatmentKind.values().forEach { k ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(k.label, style = MaterialTheme.typography.titleSmall, color = c.text, modifier = Modifier.weight(1f), maxLines = 2)
                ReefButton("Start", { actions.onStartTreatment(k) }, kind = ButtonKind.TONAL)
                Spacer(Modifier.width(8.dp))
                ReefButton("Stop", { actions.onStopTreatment(k) }, kind = ButtonKind.OUTLINED)
            }
        }
    }

    SubHeader("Make a fish sick (test)", Icons.Rounded.Science)
    Helper("A random fish catches it, so you can practise spotting and treating it. You will be asked first.")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Disease.values().forEach { d ->
            ReefChip(SandboxFriendly.diseaseLabel(d), selected = false, onClick = { actions.onInfect(d) }, modifier = Modifier.heightIn(min = 48.dp))
        }
    }

    SubHeader("Cleaning jobs", Icons.Rounded.FilterAlt)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ReefButton("Change filter sock", { actions.onMaintain(MaintenanceTask.CHANGE_SOCK) }, kind = ButtonKind.TONAL, icon = Icons.Rounded.FilterAlt)
        ReefButton("Fresh carbon", { actions.onMaintain(MaintenanceTask.REPLACE_CARBON) }, kind = ButtonKind.TONAL, icon = Icons.Rounded.Layers)
        ReefButton("Trim algae garden", { actions.onMaintain(MaintenanceTask.HARVEST_MACRO) }, kind = ButtonKind.TONAL, icon = Icons.Rounded.Eco)
        ReefButton("Add spare shells", actions.onAddSpareShells, kind = ButtonKind.TONAL, icon = Icons.Rounded.Spa)
    }

    SubHeader("Equipment & test switches")
    ParamsContent(state, actions, SandboxCategory.PESTS)
}

// ---------------------------------------------------------------- events tab

private fun presetIcon(p: SandboxPreset): ImageVector = when (p) {
    SandboxPreset.HEATER_STUCK -> Icons.Rounded.LocalFireDepartment
    SandboxPreset.POWER_OUTAGE -> Icons.Rounded.PowerOff
    SandboxPreset.OVERFEEDING -> Icons.Rounded.Restaurant
    SandboxPreset.MISSED_WATER_CHANGES -> Icons.Rounded.EventBusy
    SandboxPreset.SALINITY_CRASH -> Icons.Rounded.WaterDrop
    SandboxPreset.COPPER_OVERDOSE -> Icons.Rounded.Science
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventsContent(state: SandboxPanelState, actions: SandboxActions) {
    val c = ReefTheme.colors
    SubHeader("What if...?", Icons.Rounded.EventBusy)
    Helper("Tap one to make it happen in the sandbox, then speed up time to watch.")
    SandboxPreset.values().toList().chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            pair.forEach { p -> PresetCard(p, active = p == state.activePreset, Modifier.weight(1f)) { actions.onPreset(p) } }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }

    SubHeader("Do something", Icons.Rounded.TouchApp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ReefButton("Feed", actions.onFeed, kind = ButtonKind.CORAL, icon = Icons.Rounded.Restaurant)
        ReefButton("Tap the glass", actions.onTapGlass, kind = ButtonKind.TONAL, icon = Icons.Rounded.TouchApp)
        ReefButton("Small water change (10%)", { actions.onWaterChange(0.10) }, kind = ButtonKind.TONAL, icon = Icons.Rounded.Waves)
        ReefButton("Big water change (25%)", { actions.onWaterChange(0.25) }, kind = ButtonKind.TONAL, icon = Icons.Rounded.Waves)
    }

    SubHeader("Fast-forward test", Icons.Rounded.Timelapse)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        ReefButton(
            if (state.timelapseStatus == null) "Run a time-lapse" else "Open time-lapse",
            actions.onTimelapse, kind = ButtonKind.TONAL, icon = Icons.Rounded.Timelapse,
        )
        Text(
            state.timelapseStatus ?: "See days or weeks of change in a few seconds, without touching this sandbox.",
            style = MaterialTheme.typography.labelMedium,
            color = c.textSecondary,
            modifier = Modifier.weight(1f),
        )
    }

    SubHeader("Add minerals or waste", Icons.Rounded.Science)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        doseChips.forEach { d -> ReefChip(d.label, selected = false, onClick = { actions.onDose(d.paramId, d.delta) }, modifier = Modifier.heightIn(min = 48.dp)) }
    }

    SubHeader("Break or fix things", Icons.Rounded.PowerOff)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ReefButton("Heater breaks", { actions.onHeater(false) }, kind = ButtonKind.DESTRUCTIVE, icon = Icons.Rounded.PowerOff)
        ReefButton("Fix the heater", { actions.onHeater(true) }, kind = ButtonKind.TONAL, icon = Icons.Rounded.LocalFireDepartment)
        ReefButton("Pumps stop tonight (23:00–05:00)", actions.onPumpFailTonight, kind = ButtonKind.DESTRUCTIVE, icon = Icons.Rounded.Waves)
    }
    SwitchRow(
        title = "Fish can eat each other",
        icon = Icons.Rounded.Pets,
        on = state.predation,
        onToggle = { actions.onPredation(!state.predation) },
        subtitle = "Predation",
        explain = "Big hunters may eat small fish and shrimp. Turn it off to keep everyone safe while you test.",
    )

    SubHeader("Slow vs sudden changes")
    Helper("Fish get used to slow changes over days; sudden jumps stress them. Pick Step, Ramp or Spike above the sliders.")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ReefButton("Let fish get used to now", actions.onAcclimateAll, kind = ButtonKind.TONAL)
        ReefButton("Move the rocks", actions.onRearrangeRock, kind = ButtonKind.TONAL, icon = Icons.Rounded.Layers)
    }

    SubHeader("One animal", Icons.Rounded.Pets)
    val sel = state.selected
    if (sel == null) {
        Helper("Tap a fish in the tank to make it bigger, smaller or take it out.")
    } else {
        ReefSlider(
            spec = ReefSliderSpec("Size of ${sel.name}", "cm", 0.5, 40.0, 0.5, 1),
            value = sel.lengthCm,
            onValueChange = { actions.onResize(sel.id, it) },
        )
        ReefButton("Take out ${sel.name}", { actions.onRemove(sel.id) }, kind = ButtonKind.DESTRUCTIVE, icon = Icons.Rounded.DeleteOutline)
    }
    ReefButton("Add more life", { actions.onCategory(SandboxCategory.STOCK) }, kind = ButtonKind.TONAL, icon = Icons.Rounded.Add)
    if (state.addable.isNotEmpty()) {
        Text("Add another", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.addable.forEach { a -> ReefChip("+ ${a.name}", selected = false, onClick = { actions.onAdd(a) }, modifier = Modifier.heightIn(min = 48.dp)) }
        }
    }
}

@Composable
private fun PresetCard(p: SandboxPreset, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = ReefTheme.colors
    GlassCard(
        modifier = modifier.fillMaxHeight().semantics { role = Role.Button; stateDescription = if (active) "Happening now" else "" },
        shape = ReefShapes.tile,
        tint = if (active) c.coral else null,
        contentPadding = PaddingValues(12.dp),
        onClick = onClick,
    ) {
        BigIcon(presetIcon(p), if (active) c.coral else c.coralSoft)
        Spacer(Modifier.height(8.dp))
        Text(p.label, style = MaterialTheme.typography.labelLarge, color = c.text)
        Text(p.blurb, style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
    }
}

// ---------------------------------------------------------------- first-time tips

/**
 * Short first-time tips for the sandbox, floating over the tank. Shown once (remembered in settings) and again from the
 * (?) button in the panel header.
 */
@Composable
fun SandboxTipsCard(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val c = ReefTheme.colors
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = ReefShapes.tile,
        tint = c.coral,
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Text("This is your pretend tank", style = MaterialTheme.typography.titleSmall, color = c.text, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(6.dp))
        listOf(
            Icons.Rounded.TouchApp to "Pick a tab, then slide or tap to change things. Your real tank never changes.",
            Icons.Rounded.ExpandLess to "Tap or drag the panel's handle to tuck it away. It also tucks away while you swipe the tank.",
            Icons.Rounded.Waves to "Tap empty water to hide every button. Tap again to bring them back.",
            Icons.Rounded.RestartAlt to "Made a mess? Reset tank puts it all back.",
        ).forEach { (icon, text) ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                Icon(icon, contentDescription = null, tint = c.coralSoft, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(text, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
        }
        Spacer(Modifier.height(8.dp))
        ReefButton("Got it", onDismiss, Modifier.align(Alignment.End), kind = ButtonKind.CORAL)
    }
}

// ---------------------------------------------------------------- previews

private val previewReading = TankReading(WaterState(tempC = 29.4, no3 = 32.0, po4 = 0.21), lightPct = 100.0, flowPct = 100.0)
private val previewReal = TankReading(WaterState(), lightPct = 100.0, flowPct = 100.0)
private val previewPanel = SandboxPanelState(
    now = previewReading, real = previewReal, speed = 240.0,
    fish = FishSummary(alive = 7, stressed = 2), activePreset = SandboxPreset.HEATER_STUCK,
)

@Preview(name = "Sandbox dock · Abyss · 1.3x", widthDp = 393, heightDp = 520, showBackground = true, fontScale = 1.3f)
@Composable
private fun SandboxDockPreview() = ReefLiveTheme(darkTheme = true, reduceMotion = true) {
    Box(Modifier.background(ReefTheme.colors.bg)) { SandboxDock(previewPanel, SandboxActions(), Modifier.fillMaxWidth(), bodyHeight = 360.dp) }
}

@Preview(name = "Sandbox dock · Events · Lagoon", widthDp = 393, heightDp = 700, showBackground = true)
@Composable
private fun SandboxEventsPreview() = ReefLiveTheme(darkTheme = false, reduceMotion = true) {
    Box(Modifier.background(ReefTheme.colors.bg)) {
        SandboxDock(
            previewPanel.copy(category = SandboxCategory.EVENTS, selected = SelectedAnimalUi("f1", "Pixel", 6.5),
                addable = listOf(AddableSpecies("ocellaris", "Ocellaris clownfish", 6.0))),
            SandboxActions(), Modifier.fillMaxWidth(), bodyHeight = 560.dp,
        )
    }
}

@Preview(name = "Sandbox dock · collapsed", widthDp = 393, heightDp = 200, showBackground = true)
@Composable
private fun SandboxCollapsedPreview() = ReefLiveTheme(darkTheme = true, reduceMotion = true) {
    Box(Modifier.background(ReefTheme.colors.bg)) { SandboxDock(previewPanel.copy(expanded = false), SandboxActions(), Modifier.fillMaxWidth()) }
}
