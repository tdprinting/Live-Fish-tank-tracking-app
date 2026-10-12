package com.reeflive.app.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.reeflive.app.data.repo.TANK_ID
import com.reeflive.app.di.AppGraph
import com.reeflive.app.ui.care.TaskOps
import com.reeflive.app.ui.common.TimeText
import com.reeflive.app.ui.components.DateDialog
import com.reeflive.app.ui.components.EmptyState
import com.reeflive.app.ui.components.GlassIconButton
import com.reeflive.app.ui.components.LocalSnackbar
import com.reeflive.app.ui.components.ReefButton
import com.reeflive.app.ui.components.ReefChip
import com.reeflive.app.ui.components.ReefDialog
import com.reeflive.app.ui.components.ReefTextField
import com.reeflive.app.ui.components.ScreenHeader
import com.reeflive.app.ui.components.pagePadding
import com.reeflive.app.ui.components.rememberHaptics
import com.reeflive.app.ui.nav.Routes
import com.reeflive.app.ui.nav.reefViewModel
import com.reeflive.app.ui.theme.GlassCard
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.core.model.Equipment
import com.reeflive.core.model.EquipmentType
import com.reeflive.core.model.Ids
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

fun equipmentIcon(t: EquipmentType): ImageVector = when (t) {
    EquipmentType.LIGHT -> Icons.Rounded.Lightbulb
    EquipmentType.POWERHEAD -> Icons.Rounded.Waves
    EquipmentType.HEATER -> Icons.Rounded.Thermostat
    EquipmentType.FILTER -> Icons.Rounded.FilterAlt
    EquipmentType.SKIMMER -> Icons.Rounded.Air
    EquipmentType.ATO -> Icons.Rounded.WaterDrop
    EquipmentType.CONTROLLER -> Icons.Rounded.Memory
    EquipmentType.OTHER -> Icons.Rounded.Build
}

data class EquipmentRowUi(val id: String, val type: EquipmentType, val name: String, val detail: String, val service: String?)

data class EquipmentListUi(val loading: Boolean = true, val items: List<EquipmentRowUi> = emptyList())

// ---------------------------------------------------------------- List

@Composable
fun EquipmentListContent(state: EquipmentListUi, onBack: () -> Unit, onOpen: (String) -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val c = ReefTheme.colors
    LazyColumn(modifier.fillMaxSize(), contentPadding = pagePadding(top = 0.dp)) {
        item(key = "header") {
            ScreenHeader("Equipment", onBack = onBack, modifier = Modifier.padding(horizontal = 0.dp), actions = {
                GlassIconButton(Icons.Rounded.Add, "Add equipment", onAdd)
            })
        }
        if (!state.loading && state.items.isEmpty()) {
            item(key = "empty") { EmptyState("No equipment yet", "Add lights, pumps and heaters. A service interval creates a reminder.", icon = Icons.Rounded.Build) }
        }
        items(state.items, key = { it.id }) { e ->
            GlassCard(Modifier.fillMaxWidth().padding(bottom = 10.dp), onClick = { onOpen(e.id) }, contentPadding = PaddingValues(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(CircleShape).background(c.primary.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                        Icon(equipmentIcon(e.type), null, tint = c.primaryBright)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.name, style = MaterialTheme.typography.titleMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(e.detail, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (e.service != null) Text(e.service, style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                    }
                }
            }
        }
    }
}

class EquipmentListViewModel(g: AppGraph) : ViewModel() {
    val state: StateFlow<EquipmentListUi> = g.equipment.all().map { list ->
        val now = System.currentTimeMillis()
        EquipmentListUi(
            loading = false,
            items = list.sortedWith(compareBy({ it.type.ordinal }, { it.name })).map { e ->
                EquipmentRowUi(
                    e.id, e.type, e.name,
                    listOfNotNull(e.type.label, listOfNotNull(e.brand, e.model).joinToString(" ").ifBlank { null }).joinToString(" · "),
                    e.serviceIntervalDays?.let { d ->
                        "Service every $d d" + (e.lastServicedAt?.let { " · last ${TimeText.ago(it, now)}" } ?: "")
                    },
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EquipmentListUi())
}

@Composable
fun EquipmentListRoute(onBack: () -> Unit, onOpen: (String) -> Unit, onAdd: () -> Unit) {
    val vm = reefViewModel { EquipmentListViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    EquipmentListContent(state, onBack, onOpen, onAdd)
}

// ---------------------------------------------------------------- Edit

data class EquipmentForm(
    val type: EquipmentType = EquipmentType.OTHER,
    val name: String = "",
    val brand: String = "",
    val model: String = "",
    val installedAt: Long? = null,
    val interval: String = "",
    val notes: String = "",
) {
    val intervalDays: Int? get() = interval.trim().toIntOrNull()?.takeIf { it > 0 }
    val valid: Boolean get() = name.isNotBlank() && (interval.isBlank() || intervalDays != null)
}

data class EquipmentEditUi(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val form: EquipmentForm = EquipmentForm(),
    val lastServiced: String? = null,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EquipmentEditContent(
    state: EquipmentEditUi,
    onBack: () -> Unit,
    onForm: (EquipmentForm) -> Unit,
    onPickDate: () -> Unit,
    onSave: () -> Unit,
    onServiced: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ReefTheme.colors
    val f = state.form
    Column(modifier.fillMaxSize()) {
        ScreenHeader(if (state.isNew) "Add equipment" else f.name.ifBlank { "Equipment" }, onBack = onBack, actions = {
            if (!state.isNew) GlassIconButton(Icons.Rounded.Delete, "Delete", onDelete, tint = c.dangerText)
        })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(pagePadding()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (!state.isNew && f.intervalDays != null) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Last serviced", style = MaterialTheme.typography.labelMedium, color = c.textTertiary)
                            Text(state.lastServiced ?: "Never", style = MaterialTheme.typography.titleMedium, color = c.text)
                        }
                        ReefButton("Serviced", onServiced, icon = Icons.Rounded.Check, compact = true)
                    }
                }
            }
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Type", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    EquipmentType.entries.forEach { t ->
                        ReefChip(t.label, f.type == t, { onForm(f.copy(type = t)) }, leading = {
                            Icon(equipmentIcon(t), null, tint = if (f.type == t) c.primaryBright else c.textSecondary, modifier = Modifier.size(16.dp))
                        })
                    }
                }
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ReefTextField(f.name, { onForm(f.copy(name = it)) }, Modifier.fillMaxWidth(), label = "Name", placeholder = "Return pump")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ReefTextField(f.brand, { onForm(f.copy(brand = it)) }, Modifier.weight(1f), label = "Brand")
                        ReefTextField(f.model, { onForm(f.copy(model = it)) }, Modifier.weight(1f), label = "Model")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CalendarMonth, null, tint = c.textSecondary)
                        Spacer(Modifier.width(8.dp))
                        Text("Installed", style = MaterialTheme.typography.bodyLarge, color = c.text, modifier = Modifier.weight(1f))
                        ReefChip(f.installedAt?.let { TimeText.longDate(it) } ?: "Pick date", false, onPickDate)
                    }
                    ReefTextField(
                        f.interval, { t -> onForm(f.copy(interval = t.filter { it.isDigit() }.take(4))) }, Modifier.fillMaxWidth(),
                        label = "Service every", placeholder = "e.g. 60", numeric = true,
                        suffix = { Text("days", style = MaterialTheme.typography.labelMedium, color = c.textTertiary) },
                    )
                    Text("A service interval adds a reminder in Care.", style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                    ReefTextField(f.notes, { onForm(f.copy(notes = it)) }, Modifier.fillMaxWidth(), label = "Notes", singleLine = false, minLines = 2)
                }
            }
            ReefButton(if (state.isNew) "Add" else "Save", onSave, Modifier.fillMaxWidth(), enabled = f.valid)
        }
    }
}

class EquipmentEditViewModel(private val g: AppGraph, private val id: String) : ViewModel() {
    private val isNew = id == Routes.NEW
    private val form = MutableStateFlow<EquipmentForm?>(null)

    val state: StateFlow<EquipmentEditUi> = combine(if (isNew) flowOf(null) else g.equipment.byId(id), form) { e, f ->
        EquipmentEditUi(
            loading = false,
            isNew = isNew,
            form = f ?: e?.let {
                EquipmentForm(it.type, it.name, it.brand.orEmpty(), it.model.orEmpty(), it.installedAt, it.serviceIntervalDays?.toString().orEmpty(), it.notes.orEmpty())
            } ?: EquipmentForm(),
            lastServiced = e?.lastServicedAt?.let { TimeText.ago(it) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EquipmentEditUi(isNew = isNew))

    fun edit(f: EquipmentForm) { form.value = f }

    fun save(onDone: () -> Unit) {
        val f = state.value.form
        if (!f.valid) return
        viewModelScope.launch {
            val base = if (isNew) null else g.equipment.byId(id).first()
            val e = (base ?: Equipment(Ids.new(), TANK_ID, f.type, f.name.trim())).copy(
                type = f.type, name = f.name.trim(), brand = f.brand.trim().ifBlank { null }, model = f.model.trim().ifBlank { null },
                installedAt = f.installedAt, serviceIntervalDays = f.intervalDays, notes = f.notes.trim().ifBlank { null },
            )
            val saved = g.equipment.save(e)
            val oldTask = base?.taskId
            saved.taskId?.let { TaskOps.reschedule(g, it) }
            if (oldTask != null && saved.serviceIntervalDays == null) g.reminders.cancel(oldTask)
            form.value = null
            onDone()
        }
    }

    fun serviced(onDone: () -> Unit) {
        viewModelScope.launch {
            val e = g.equipment.byId(id).first() ?: return@launch
            g.equipment.markServiced(e)
            e.taskId?.let { TaskOps.complete(g, it, note = "Serviced ${e.name}") }
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            val e = g.equipment.byId(id).first()
            e?.taskId?.let { TaskOps.delete(g, it) }
            g.equipment.delete(id)
            onDone()
        }
    }
}

@Composable
fun EquipmentEditRoute(id: String, onBack: () -> Unit) {
    val vm = reefViewModel(key = "eq-$id") { EquipmentEditViewModel(it, id) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var pickDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    EquipmentEditContent(
        state,
        onBack = onBack,
        onForm = vm::edit,
        onPickDate = { pickDate = true },
        onSave = { haptics.confirm(); vm.save(onBack) },
        onServiced = { haptics.confirm(); vm.serviced { scope.launch { snackbar.showSnackbar("Marked serviced") } } },
        onDelete = { confirmDelete = true },
    )
    if (pickDate) {
        DateDialog(state.form.installedAt, onPick = { vm.edit(state.form.copy(installedAt = it)); pickDate = false }, onDismiss = { pickDate = false })
    }
    if (confirmDelete) {
        ReefDialog("Delete equipment?", "Its service reminder is removed too.", "Delete", onConfirm = {
            confirmDelete = false
            vm.delete(onBack)
        }, onDismiss = { confirmDelete = false }, destructive = true)
    }
}
