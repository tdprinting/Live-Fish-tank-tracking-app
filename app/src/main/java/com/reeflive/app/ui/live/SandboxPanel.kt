package com.reeflive.app.ui.live

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PowerOff
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.reeflive.app.ui.components.ButtonKind
import com.reeflive.app.ui.components.ReefButton
import com.reeflive.app.ui.components.ReefChip
import com.reeflive.app.ui.components.ReefSlider
import com.reeflive.app.ui.components.ReefSliderSpec
import com.reeflive.app.ui.components.SegmentedPills
import com.reeflive.app.ui.theme.GlassCard
import com.reeflive.app.ui.theme.ReefLiveTheme
import com.reeflive.app.ui.theme.ReefShapes
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.core.sim.WaterState

// ---------------------------------------------------------------- state

/** Fish health summary shown in the dock header so the effect of a change is visible while adjusting. */
data class FishSummary(val alive: Int = 0, val stressed: Int = 0, val sick: Int = 0, val dead: Int = 0) {
    val text: String get() = buildList {
        add("$alive fish")
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
    val expanded: Boolean = true,
    val speed: Double = 1.0,
    val speeds: List<Double> = listOf(1.0, 60.0, 240.0, 1000.0),
    val controlMode: SandboxControlMode = SandboxControlMode.SET,
    val activePreset: SandboxPreset? = null,
    val fish: FishSummary = FishSummary(),
    val selected: SelectedAnimalUi? = null,
    val addable: List<AddableSpecies> = emptyList(),
    val predation: Boolean = true,
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
)

/** One dose button: [paramId] for Command.Dose, [delta] in sim units. */
private data class DoseChip(val label: String, val paramId: String, val delta: Double)

private val doseChips = listOf(
    DoseChip("Alk +1 dKH", "alk", 1.0),
    DoseChip("Ca +20 ppm", "ca", 20.0),
    DoseChip("Mg +50 ppm", "mg", 50.0),
    DoseChip("NO3 +5 ppm", "no3", 5.0),
    DoseChip("PO4 +0.05 ppm", "po4", 0.05),
)

// ---------------------------------------------------------------- dock

/**
 * Inline sandbox dock. Portrait: a collapsible panel under the tank (the tank view shrinks instead of being covered).
 * Landscape ([side] = true): a full-height column to the right of the tank. [bottomClearance] keeps the content clear of
 * the floating bottom navigation.
 */
@Composable
fun SandboxDock(
    state: SandboxPanelState,
    actions: SandboxActions,
    modifier: Modifier = Modifier,
    side: Boolean = false,
    bottomClearance: Dp = 0.dp,
) {
    val c = ReefTheme.colors
    val shape = if (side) RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp) else ReefShapes.sheet
    val open = side || state.expanded
    Column(
        modifier
            .clip(shape)
            .background(c.sheet)
            .border(1.dp, c.glassBorder, shape),
    ) {
        DockHeader(state, actions, collapsible = !side)
        SegmentedPills(
            options = state.speeds.map(::formatSpeed),
            selected = state.speeds.indexOf(state.speed).coerceAtLeast(0),
            onSelect = { actions.onSpeed(state.speeds[it]) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .semantics { contentDescription = "Simulation speed" },
            selectedColor = c.coralSoft,
        )
        if (open) {
            val modes = SandboxControlMode.enabled
            if (modes.size > 1) {
                // Placeholder for sim/realism's step / ramp / schedule modes: appears as soon as a second mode is available.
                SegmentedPills(
                    options = modes.map { it.label },
                    selected = modes.indexOf(state.controlMode).coerceAtLeast(0),
                    onSelect = { actions.onControlMode(modes[it]) },
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
                )
            }
            CategoryTabs(state.category, actions.onCategory)
            AnimatedVisibility(state.activePreset != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                state.activePreset?.let { PresetBanner(it, actions.onDismissPreset) }
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (state.category == SandboxCategory.EVENTS) EventsContent(state, actions)
                else ParamsContent(state, actions)
                Spacer(Modifier.height(bottomClearance))
            }
        } else {
            Spacer(Modifier.height(10.dp + bottomClearance))
        }
    }
}

@Composable
private fun DockHeader(state: SandboxPanelState, actions: SandboxActions, collapsible: Boolean) {
    val c = ReefTheme.colors
    var m = Modifier.fillMaxWidth().heightIn(min = 56.dp)
    if (collapsible) {
        m = m.clickable(
            onClickLabel = if (state.expanded) "Collapse sandbox controls" else "Expand sandbox controls",
            onClick = actions.onToggleExpanded,
        )
    }
    Row(m.padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (collapsible) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(if (state.expanded) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess, contentDescription = null, tint = c.textSecondary)
            }
        } else Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text("Sandbox", style = MaterialTheme.typography.titleMedium, color = c.coral, modifier = Modifier.semantics { heading() })
            Text(
                state.fish.text,
                style = MaterialTheme.typography.labelMedium,
                color = if (state.fish.worried) c.cautionText else c.textSecondary,
                maxLines = 2,
            )
        }
        ReefButton("Reset", actions.onReset, kind = ButtonKind.OUTLINED, icon = Icons.Rounded.RestartAlt, compact = true)
    }
}

@Composable
private fun CategoryTabs(selected: SandboxCategory, onSelect: (SandboxCategory) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SandboxCategory.values().forEach { cat ->
            val sel = cat == selected
            ReefChip(
                text = cat.label,
                selected = sel,
                onClick = { onSelect(cat) },
                modifier = Modifier.semantics { role = Role.Tab; this.selected = sel },
            )
        }
    }
}

@Composable
private fun PresetBanner(p: SandboxPreset, onDismiss: () -> Unit) {
    val c = ReefTheme.colors
    GlassCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
        shape = ReefShapes.tile,
        tint = c.coral,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${p.label} running", style = MaterialTheme.typography.labelLarge, color = c.text)
                Text(p.hint, style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
            }
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Dismiss", onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Dismiss", tint = c.textSecondary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- parameter tabs

@Composable
private fun ParamsContent(state: SandboxPanelState, actions: SandboxActions) {
    val defs = SandboxParams.inCategory(state.category)
    val (live, pending) = defs.partition { it.supported }
    if (state.category == SandboxCategory.LIGHT_FLOW) LightsRow(actions.onLights)
    live.forEach { def -> ParamSlider(def, state, actions) }
    if (pending.isNotEmpty()) {
        SubHeader("Coming soon")
        pending.forEach { def -> ParamSlider(def, state, actions) }
    }
}

@Composable
private fun ParamSlider(def: SandboxParamDef, state: SandboxPanelState, actions: SandboxActions) {
    val now = state.now
    ReefSlider(
        spec = def.spec,
        value = def.valueIn(now),
        onValueChange = { actions.onSet(def, it) },
        referenceValue = if (def.supported) state.real?.let(def.read)?.coerceIn(def.min, def.max) else null,
        enabled = def.supported,
        caption = if (now != null) def.caption?.invoke(now) else null,
        disabledReason = def.pendingNote,
    )
}

@Composable
private fun LightsRow(onLights: (Boolean?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SubHeader("Lights")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReefButton("On", { onLights(true) }, Modifier.weight(1f), kind = ButtonKind.TONAL, compact = true)
            ReefButton("Off", { onLights(false) }, Modifier.weight(1f), kind = ButtonKind.TONAL, compact = true)
            ReefButton("Schedule", { onLights(null) }, Modifier.weight(1f), kind = ButtonKind.OUTLINED, compact = true)
        }
    }
}

@Composable
private fun SubHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = ReefTheme.colors.textSecondary,
        modifier = Modifier.padding(top = 4.dp).semantics { heading() },
    )
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
    SubHeader("Scenarios")
    SandboxPreset.values().toList().chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            pair.forEach { p -> PresetCard(p, active = p == state.activePreset, Modifier.weight(1f)) { actions.onPreset(p) } }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }

    SubHeader("Actions")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ReefButton("Feed", actions.onFeed, kind = ButtonKind.CORAL, icon = Icons.Rounded.Restaurant, compact = true)
        ReefButton("Tap glass", actions.onTapGlass, kind = ButtonKind.TONAL, icon = Icons.Rounded.TouchApp, compact = true)
        ReefButton("Water change 10%", { actions.onWaterChange(0.10) }, kind = ButtonKind.TONAL, compact = true)
        ReefButton("Water change 25%", { actions.onWaterChange(0.25) }, kind = ButtonKind.TONAL, compact = true)
    }

    SubHeader("Dose")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        doseChips.forEach { d -> ReefChip(d.label, selected = false, onClick = { actions.onDose(d.paramId, d.delta) }) }
    }

    SubHeader("Equipment")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ReefButton("Heater fails", { actions.onHeater(false) }, kind = ButtonKind.DESTRUCTIVE, compact = true)
        ReefButton("Heater fixed", { actions.onHeater(true) }, kind = ButtonKind.TONAL, compact = true)
        ReefButton("Pumps fail 23:00–05:00", actions.onPumpFailTonight, kind = ButtonKind.DESTRUCTIVE, compact = true)
        ReefChip(
            text = if (state.predation) "Predation on" else "Predation off",
            selected = state.predation,
            onClick = { actions.onPredation(!state.predation) },
            modifier = Modifier.semantics { stateDescription = if (state.predation) "On" else "Off" },
        )
    }

    SubHeader("Change tests")
    Text(
        "Fish adjust to slow changes over days; sudden jumps stress them. Use the Step / Ramp / Spike selector on the sliders.",
        style = MaterialTheme.typography.bodyMedium, color = ReefTheme.colors.textSecondary,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ReefButton("Acclimate all to now", actions.onAcclimateAll, kind = ButtonKind.TONAL, compact = true)
        ReefButton("Rearrange rock", actions.onRearrangeRock, kind = ButtonKind.TONAL, compact = true)
    }

    SubHeader("Livestock")
    val sel = state.selected
    if (sel == null) {
        Text("Tap a fish in the tank to resize or remove it.", style = MaterialTheme.typography.bodyMedium, color = ReefTheme.colors.textSecondary)
    } else {
        ReefSlider(
            spec = ReefSliderSpec("Size of ${sel.name}", "cm", 0.5, 40.0, 0.5, 1),
            value = sel.lengthCm,
            onValueChange = { actions.onResize(sel.id, it) },
        )
        ReefButton("Remove ${sel.name}", { actions.onRemove(sel.id) }, kind = ButtonKind.DESTRUCTIVE, icon = Icons.Rounded.DeleteOutline, compact = true)
    }
    if (state.addable.isNotEmpty()) {
        Text("Add another", style = MaterialTheme.typography.labelMedium, color = ReefTheme.colors.textSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.addable.forEach { a -> ReefChip("+ ${a.name}", selected = false, onClick = { actions.onAdd(a) }) }
        }
    }
}

@Composable
private fun PresetCard(p: SandboxPreset, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = ReefTheme.colors
    GlassCard(
        modifier = modifier.fillMaxHeight().semantics { role = Role.Button; stateDescription = if (active) "Running" else "" },
        shape = ReefShapes.tile,
        tint = if (active) c.coral else null,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        onClick = onClick,
    ) {
        Icon(presetIcon(p), contentDescription = null, tint = if (active) c.coral else c.coralSoft, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(6.dp))
        Text(p.label, style = MaterialTheme.typography.labelLarge, color = c.text)
        Text(p.blurb, style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
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
    Box(Modifier.background(ReefTheme.colors.bg)) { SandboxDock(previewPanel, SandboxActions(), Modifier.fillMaxWidth().height(520.dp)) }
}

@Preview(name = "Sandbox dock · Events · Lagoon", widthDp = 393, heightDp = 700, showBackground = true)
@Composable
private fun SandboxEventsPreview() = ReefLiveTheme(darkTheme = false, reduceMotion = true) {
    Box(Modifier.background(ReefTheme.colors.bg)) {
        SandboxDock(
            previewPanel.copy(category = SandboxCategory.EVENTS, selected = SelectedAnimalUi("f1", "Pixel", 6.5),
                addable = listOf(AddableSpecies("ocellaris", "Ocellaris clownfish", 6.0))),
            SandboxActions(), Modifier.fillMaxWidth().height(700.dp),
        )
    }
}

@Preview(name = "Sandbox dock · collapsed", widthDp = 393, heightDp = 200, showBackground = true)
@Composable
private fun SandboxCollapsedPreview() = ReefLiveTheme(darkTheme = true, reduceMotion = true) {
    Box(Modifier.background(ReefTheme.colors.bg)) { SandboxDock(previewPanel.copy(expanded = false), SandboxActions(), Modifier.fillMaxWidth()) }
}
