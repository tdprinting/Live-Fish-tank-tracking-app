package com.reeflive.app.ui.livestock

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.reeflive.app.data.repo.TANK_ID
import com.reeflive.app.di.AppGraph
import com.reeflive.app.ui.components.ButtonKind
import com.reeflive.app.ui.components.DateDialog
import com.reeflive.app.ui.components.LocalSnackbar
import com.reeflive.app.ui.components.ReefButton
import com.reeflive.app.ui.components.ReefDialog
import com.reeflive.app.ui.components.ReefTextField
import com.reeflive.app.ui.components.parseDecimal
import com.reeflive.app.ui.components.rememberHaptics
import com.reeflive.app.ui.nav.Routes
import com.reeflive.app.ui.nav.reefViewModel
import com.reeflive.app.ui.theme.ReefShapes
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.core.model.HealthLog
import com.reeflive.core.model.Ids
import com.reeflive.core.model.Livestock
import com.reeflive.core.model.LivestockStatus
import com.reeflive.core.util.Fmt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

private fun AppGraph.displayGallons() = tank.tank.map { it?.displayGallons ?: 55.0 }

// ---------------------------------------------------------------- List

class LivestockListViewModel(private val g: AppGraph) : ViewModel() {
    private val showGone = MutableStateFlow(false)

    val state: StateFlow<LivestockListUi> = combine(g.livestock.all(), g.displayGallons(), showGone) { all, gal, gone ->
        val now = System.currentTimeMillis()
        LivestockListUi(
            loading = false,
            sections = LivestockMapper.sections(all, g.speciesDb, gal, now, gone),
            showGone = gone,
            count = all.filter { it.status == LivestockStatus.ALIVE }.sumOf { it.quantity },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LivestockListUi())

    fun showGone(v: Boolean) { showGone.value = v }
}

@Composable
fun LivestockListRoute(onBack: () -> Unit, onOpen: (String) -> Unit, onAdd: () -> Unit) {
    val vm = reefViewModel { LivestockListViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    LivestockListContent(state, LivestockListActions(onBack = onBack, onOpen = onOpen, onAdd = onAdd, onShowGone = vm::showGone))
}

// ---------------------------------------------------------------- Detail

class LivestockDetailViewModel(private val g: AppGraph, private val id: String) : ViewModel() {
    val state: StateFlow<LivestockDetailUi> = combine(
        g.livestock.byId(id), g.livestock.alive(), g.livestock.healthLogs(id), g.displayGallons(),
    ) { l, mates, health, gal ->
        if (l == null) LivestockDetailUi(loading = false, found = false)
        else LivestockMapper.detail(l, mates, health, g.speciesDb, gal, System.currentTimeMillis())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LivestockDetailUi())

    fun status(s: LivestockStatus) {
        viewModelScope.launch { g.livestock.byId(id).first()?.let { g.livestock.setStatus(it, s) } }
    }

    fun addHealth(symptom: String, treatment: String?, note: String?) {
        viewModelScope.launch {
            g.livestock.addHealth(HealthLog(Ids.new(), id, System.currentTimeMillis(), symptom.trim(), treatment?.trim()?.ifBlank { null }, note = note?.trim()?.ifBlank { null }))
        }
    }

    fun deleteHealth(hid: String) { viewModelScope.launch { g.livestock.deleteHealth(hid) } }
}

@Composable
fun LivestockDetailRoute(id: String, onBack: () -> Unit, onEdit: () -> Unit, onViewLive: () -> Unit) {
    val vm = reefViewModel(key = "ls-$id") { LivestockDetailViewModel(it, id) }
    val state by vm.state.collectAsStateWithLifecycle()
    var addingHealth by remember { mutableStateOf(false) }
    var deleteHealth by remember { mutableStateOf<String?>(null) }
    LivestockDetailContent(
        state,
        LivestockDetailActions(
            onBack = onBack, onEdit = onEdit, onViewLive = onViewLive,
            onAddHealth = { addingHealth = true },
            onDeleteHealth = { deleteHealth = it },
            onStatus = vm::status,
        ),
    )
    if (addingHealth) {
        HealthDialog(onSave = { s, t, n -> vm.addHealth(s, t, n); addingHealth = false }, onDismiss = { addingHealth = false })
    }
    deleteHealth?.let { hid ->
        ReefDialog("Delete this note?", "The health entry will be removed.", "Delete", onConfirm = {
            vm.deleteHealth(hid)
            deleteHealth = null
        }, onDismiss = { deleteHealth = null }, destructive = true)
    }
}

@Composable
private fun HealthDialog(onSave: (String, String?, String?) -> Unit, onDismiss: () -> Unit) {
    val c = ReefTheme.colors
    var symptom by remember { mutableStateOf("") }
    var treatment by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.sheet,
        shape = ReefShapes.dialog,
        title = { Text("Health note", style = MaterialTheme.typography.headlineSmall, color = c.text) },
        text = {
            Column {
                ReefTextField(symptom, { symptom = it }, Modifier.fillMaxWidth(), label = "Symptom", placeholder = "White spots, not eating…")
                Spacer(Modifier.height(8.dp))
                ReefTextField(treatment, { treatment = it }, Modifier.fillMaxWidth(), label = "Treatment (optional)", placeholder = "Garlic soak, QT…")
                Spacer(Modifier.height(8.dp))
                ReefTextField(note, { note = it }, Modifier.fillMaxWidth(), label = "Note (optional)", singleLine = false)
            }
        },
        confirmButton = { ReefButton("Save", { onSave(symptom, treatment, note) }, enabled = symptom.isNotBlank(), compact = true) },
        dismissButton = { ReefButton("Cancel", onDismiss, kind = ButtonKind.OUTLINED, compact = true) },
    )
}

// ---------------------------------------------------------------- Edit

object LivestockForms {
    fun of(l: Livestock) = LivestockForm(
        speciesId = l.speciesId,
        nickname = l.nickname,
        variant = l.variant.orEmpty(),
        quantity = l.quantity.toString(),
        sizeCm = l.sizeCm?.let { Fmt.trim(it, 1) }.orEmpty(),
        acquiredAt = l.acquiredAt,
        price = l.priceCents?.let { Fmt.trim(it / 100.0, 2) }.orEmpty(),
        source = l.source.orEmpty(),
        status = l.status,
        notes = l.notes.orEmpty(),
        photoPath = l.photoPath,
        verified = l.verified,
    )

    fun apply(f: LivestockForm, base: Livestock?, now: Long): Livestock {
        val l = base ?: Livestock(Ids.new(), TANK_ID, f.speciesId, f.nickname.trim(), createdAt = now, acquiredAt = now)
        return l.copy(
            speciesId = f.speciesId,
            nickname = f.nickname.trim(),
            variant = f.variant.trim().ifBlank { null },
            quantity = f.quantity.toIntOrNull()?.coerceAtLeast(1) ?: 1,
            sizeCm = parseDecimal(f.sizeCm),
            acquiredAt = f.acquiredAt ?: l.acquiredAt,
            priceCents = parseDecimal(f.price)?.let { (it * 100).roundToLong() },
            source = f.source.trim().ifBlank { null },
            notes = f.notes.trim().ifBlank { null },
            photoPath = f.photoPath,
            verified = if (base != null && base.speciesId != f.speciesId) true else f.verified,
        )
    }
}

class LivestockEditViewModel(private val g: AppGraph, private val id: String) : ViewModel() {
    private val isNew = id == Routes.NEW
    private val form = MutableStateFlow<LivestockForm?>(null)
    private val saving = MutableStateFlow(false)

    val state: StateFlow<LivestockEditUi> = combine(
        if (isNew) flowOf(null) else g.livestock.byId(id), form, g.displayGallons(), saving,
    ) { l, f, gal, busy ->
        val current = f ?: l?.let(LivestockForms::of) ?: LivestockForm(acquiredAt = System.currentTimeMillis())
        val s = g.speciesDb[current.speciesId]
        LivestockEditUi(
            loading = false, isNew = isNew, form = current, species = s,
            warning = s?.let { LivestockMapper.warning(it, gal) }, saving = busy,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LivestockEditUi(isNew = isNew))

    fun edit(f: LivestockForm) { form.value = f }

    fun search(q: String) = g.speciesDb.search(q)

    fun importPhoto(uri: android.net.Uri, onError: () -> Unit) {
        viewModelScope.launch {
            val p = g.photos.import(uri)
            if (p == null) onError() else form.value = state.value.form.copy(photoPath = p.path)
        }
    }

    fun save(onDone: (String) -> Unit) {
        val f = state.value.form
        if (!f.valid) return
        saving.value = true
        viewModelScope.launch {
            val base = if (isNew) null else g.livestock.byId(id).first()
            val l = LivestockForms.apply(f, base, System.currentTimeMillis())
            if (base != null && base.status != f.status) {
                g.livestock.save(l.copy(status = base.status), isNew = false)
                g.livestock.setStatus(l.copy(status = base.status), f.status)
            } else {
                g.livestock.save(l, isNew)
            }
            val oldPhoto = base?.photoPath
            if (oldPhoto != null && oldPhoto != l.photoPath) g.photos.delete(oldPhoto)
            saving.value = false
            onDone(l.id)
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            g.livestock.byId(id).first()?.photoPath?.let(g.photos::delete)
            g.livestock.delete(id)
            onDone()
        }
    }
}

@Composable
fun LivestockEditRoute(id: String, onBack: () -> Unit, onSaved: (String) -> Unit, onDeleted: () -> Unit) {
    val vm = reefViewModel(key = "lse-$id") { LivestockEditViewModel(it, id) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var pickDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.importPhoto(uri) { scope.launch { snackbar.showSnackbar("Couldn't read that photo") } }
    }
    LivestockEditContent(
        state = state,
        search = vm::search,
        actions = LivestockEditActions(
            onBack = onBack,
            onForm = vm::edit,
            onPickDate = { pickDate = true },
            onPickPhoto = { photo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onRemovePhoto = { vm.edit(state.form.copy(photoPath = null)) },
            onSave = {
                haptics.confirm()
                vm.save { newId -> onSaved(newId) }
            },
            onDelete = { confirmDelete = true },
        ),
    )
    if (pickDate) {
        DateDialog(state.form.acquiredAt, onPick = {
            vm.edit(state.form.copy(acquiredAt = it))
            pickDate = false
        }, onDismiss = { pickDate = false })
    }
    if (confirmDelete) {
        ReefDialog(
            "Delete permanently?",
            "Use status “Rehomed” or “Deceased” to keep its history. Deleting removes it and its health log.",
            "Delete",
            onConfirm = {
                confirmDelete = false
                vm.delete(onDeleted)
            },
            onDismiss = { confirmDelete = false },
            destructive = true,
        )
    }
}
