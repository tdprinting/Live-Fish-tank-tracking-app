package com.reeflive.app.ui.more

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.reeflive.app.data.repo.TANK_ID
import com.reeflive.app.di.AppGraph
import com.reeflive.app.ui.common.TimeText
import com.reeflive.app.ui.components.ButtonKind
import com.reeflive.app.ui.components.DateTimeDialog
import com.reeflive.app.ui.components.EmptyState
import com.reeflive.app.ui.components.GlassIconButton
import com.reeflive.app.ui.components.LocalSnackbar
import com.reeflive.app.ui.components.ReefButton
import com.reeflive.app.ui.components.ReefChip
import com.reeflive.app.ui.components.ReefDialog
import com.reeflive.app.ui.components.ReefTextField
import com.reeflive.app.ui.components.ScreenHeader
import com.reeflive.app.ui.components.StoredPhoto
import com.reeflive.app.ui.components.pagePadding
import com.reeflive.app.ui.components.rememberHaptics
import com.reeflive.app.ui.nav.Routes
import com.reeflive.app.ui.nav.reefViewModel
import com.reeflive.app.ui.theme.GlassCard
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.core.model.EventType
import com.reeflive.core.model.Ids
import com.reeflive.core.model.JournalEntry
import com.reeflive.core.model.Photo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Tags are livestock ids or event-type names; [label] is what the chip shows. */
data class TagUi(val id: String, val label: String)

data class JournalCardUi(val id: String, val date: String, val text: String, val photos: List<String>, val tags: List<String>)

data class JournalListUi(val loading: Boolean = true, val items: List<JournalCardUi> = emptyList())

object JournalTags {
    /** Event-type tags offered in the editor (DESIGN §5.13). */
    val EVENT_TAGS = listOf(EventType.FEED, EventType.WATER_CHANGE, EventType.DOSE, EventType.TREATMENT, EventType.ADD_LIVESTOCK)

    fun label(tag: String, livestock: Map<String, String>): String =
        livestock[tag] ?: EventType.entries.firstOrNull { it.name == tag }?.label ?: tag
}

// ---------------------------------------------------------------- List

@Composable
fun JournalListContent(state: JournalListUi, onBack: () -> Unit, onOpen: (String) -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val c = ReefTheme.colors
    LazyColumn(modifier.fillMaxSize(), contentPadding = pagePadding(top = 0.dp)) {
        item(key = "header") {
            ScreenHeader("Journal", onBack = onBack, modifier = Modifier.padding(horizontal = 0.dp), actions = {
                GlassIconButton(Icons.Rounded.Add, "New entry", onAdd)
            })
        }
        if (!state.loading && state.items.isEmpty()) {
            item(key = "empty") {
                EmptyState("Your reef diary", "Write down what you changed and snap photos — entries show as markers on your charts.", icon = Icons.Rounded.Book, action = {
                    ReefButton("New entry", onAdd, icon = Icons.Rounded.Add, compact = true)
                })
            }
        }
        items(state.items, key = { it.id }) { j ->
            GlassCard(Modifier.fillMaxWidth().padding(bottom = 12.dp), onClick = { onOpen(j.id) }, contentPadding = PaddingValues(14.dp)) {
                Text(j.date, style = MaterialTheme.typography.labelMedium, color = c.textTertiary)
                Spacer(Modifier.height(4.dp))
                Text(j.text, style = MaterialTheme.typography.bodyLarge, color = c.text, maxLines = 4, overflow = TextOverflow.Ellipsis)
                if (j.photos.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        j.photos.take(3).forEach { p -> StoredPhoto(p, null, Modifier.weight(1f).height(96.dp)) }
                        repeat((3 - j.photos.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
                    }
                }
                if (j.tags.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(j.tags.joinToString("  ·  "), style = MaterialTheme.typography.labelSmall, color = c.primaryBright, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

class JournalListViewModel(g: AppGraph) : ViewModel() {
    val state: StateFlow<JournalListUi> = combine(g.journal.entries, g.livestock.all()) { entries, ls ->
        val names = ls.associate { it.id to it.nickname.ifBlank { g.speciesDb[it.speciesId]?.common ?: it.speciesId } }
        JournalListUi(
            loading = false,
            items = entries.sortedByDescending { it.entry.at }.map { j ->
                JournalCardUi(j.entry.id, TimeText.full(j.entry.at), j.entry.text, j.photos.map { it.path }, j.entry.tags.map { JournalTags.label(it, names) })
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JournalListUi())
}

@Composable
fun JournalListRoute(onBack: () -> Unit, onOpen: (String) -> Unit, onAdd: () -> Unit) {
    val vm = reefViewModel { JournalListViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    JournalListContent(state, onBack, onOpen, onAdd)
}

// ---------------------------------------------------------------- Edit

data class JournalForm(val at: Long = System.currentTimeMillis(), val text: String = "", val photos: List<Photo> = emptyList(), val tags: List<String> = emptyList())

data class JournalEditUi(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val form: JournalForm = JournalForm(),
    val tagOptions: List<TagUi> = emptyList(),
    val importing: Boolean = false,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JournalEditContent(
    state: JournalEditUi,
    onBack: () -> Unit,
    onForm: (JournalForm) -> Unit,
    onPickDate: () -> Unit,
    onAddPhotos: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ReefTheme.colors
    val f = state.form
    Column(modifier.fillMaxSize()) {
        ScreenHeader(if (state.isNew) "New entry" else "Entry", onBack = onBack, actions = {
            if (!state.isNew) GlassIconButton(Icons.Rounded.Delete, "Delete entry", onDelete, tint = c.dangerText)
        })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(pagePadding()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CalendarMonth, null, tint = c.textSecondary)
                    Spacer(Modifier.width(8.dp))
                    Text("When", style = MaterialTheme.typography.bodyLarge, color = c.text, modifier = Modifier.weight(1f))
                    ReefChip(TimeText.full(f.at), false, onPickDate)
                }
                Spacer(Modifier.height(12.dp))
                ReefTextField(f.text, { onForm(f.copy(text = it)) }, Modifier.fillMaxWidth(), placeholder = "What happened in the tank today?", singleLine = false, minLines = 5)
            }
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Photos", style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.weight(1f))
                    ReefButton(if (state.importing) "Adding…" else "Add photos", onAddPhotos, kind = ButtonKind.TONAL, icon = Icons.Rounded.AddAPhoto, compact = true, enabled = !state.importing)
                }
                if (f.photos.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(f.photos, key = { it.id }) { p ->
                            Box {
                                StoredPhoto(p.path, "Photo", Modifier.size(112.dp))
                                IconButton(
                                    onClick = { onForm(f.copy(photos = f.photos.filter { it.id != p.id })) },
                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(28.dp).clip(CircleShape).background(c.bgDeep.copy(alpha = 0.6f)),
                                ) { Icon(Icons.Rounded.Close, "Remove photo", tint = c.text, modifier = Modifier.size(16.dp)) }
                            }
                        }
                    }
                }
                Text("Stored on this phone only; location data is removed.", style = MaterialTheme.typography.labelSmall, color = c.textTertiary, modifier = Modifier.padding(top = 8.dp))
            }
            if (state.tagOptions.isNotEmpty()) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Tags", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.tagOptions.forEach { t ->
                            val on = t.id in f.tags
                            ReefChip(t.label, on, { onForm(f.copy(tags = if (on) f.tags - t.id else f.tags + t.id)) })
                        }
                    }
                }
            }
            ReefButton("Save", onSave, Modifier.fillMaxWidth(), enabled = f.text.isNotBlank() || f.photos.isNotEmpty())
        }
    }
}

class JournalEditViewModel(private val g: AppGraph, private val id: String) : ViewModel() {
    private val isNew = id == Routes.NEW
    private val form = MutableStateFlow<JournalForm?>(null)
    private val importing = MutableStateFlow(false)
    /** Photos imported in this session and not yet saved; deleted if the editor is abandoned. */
    private val pending = mutableSetOf<String>()
    private var saved = false

    val state: StateFlow<JournalEditUi> = combine(
        if (isNew) flowOf(null) else g.journal.byId(id), g.livestock.alive(), form, importing,
    ) { item, ls, f, busy ->
        val tagOptions = ls.map { TagUi(it.id, it.nickname.ifBlank { g.speciesDb[it.speciesId]?.common ?: it.speciesId }) } +
            JournalTags.EVENT_TAGS.map { TagUi(it.name, it.label) }
        JournalEditUi(
            loading = false, isNew = isNew,
            form = f ?: item?.let { JournalForm(it.entry.at, it.entry.text, it.photos, it.entry.tags) } ?: JournalForm(),
            tagOptions = tagOptions, importing = busy,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JournalEditUi(isNew = isNew))

    fun edit(f: JournalForm) { form.value = f }

    fun importPhotos(uris: List<android.net.Uri>, onFailed: (Int) -> Unit) {
        if (uris.isEmpty()) return
        importing.value = true
        viewModelScope.launch {
            var failed = 0
            val added = uris.mapNotNull { u -> g.photos.import(u).also { if (it == null) failed++ } }
            added.forEach { pending += it.path }
            form.value = state.value.form.let { it.copy(photos = it.photos + added) }
            importing.value = false
            if (failed > 0) onFailed(failed)
        }
    }

    fun save(onDone: () -> Unit) {
        val f = state.value.form
        viewModelScope.launch {
            val base = if (isNew) null else g.journal.byId(id).first()?.entry
            val entry = (base ?: JournalEntry(Ids.new(), TANK_ID, f.at, f.text.trim())).copy(at = f.at, text = f.text.trim(), tags = f.tags)
            val removed = g.journal.save(entry, f.photos, isNew)
            removed.forEach(g.photos::delete)
            // Photos imported then removed before saving were never attached.
            pending.filter { p -> f.photos.none { it.path == p } }.forEach(g.photos::delete)
            pending.clear()
            saved = true
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            g.journal.delete(id).forEach(g.photos::delete)
            pending.forEach(g.photos::delete)
            pending.clear()
            saved = true
            onDone()
        }
    }

    override fun onCleared() {
        if (!saved && pending.isNotEmpty()) {
            val paths = pending.toList()
            g.appScope.launch { paths.forEach(g.photos::delete) }
        }
    }
}

@Composable
fun JournalEditRoute(id: String, onBack: () -> Unit) {
    val vm = reefViewModel(key = "j-$id") { JournalEditViewModel(it, id) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var pickDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(6)) { uris ->
        vm.importPhotos(uris) { n -> scope.launch { snackbar.showSnackbar("Couldn't read $n photo" + if (n > 1) "s" else "") } }
    }
    JournalEditContent(
        state,
        onBack = onBack,
        onForm = vm::edit,
        onPickDate = { pickDate = true },
        onAddPhotos = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onSave = { haptics.confirm(); vm.save(onBack) },
        onDelete = { confirmDelete = true },
    )
    if (pickDate) {
        DateTimeDialog(state.form.at, onPick = { vm.edit(state.form.copy(at = it)); pickDate = false }, onDismiss = { pickDate = false })
    }
    if (confirmDelete) {
        ReefDialog("Delete entry?", "The text and its photos will be removed from this phone.", "Delete", onConfirm = {
            confirmDelete = false
            vm.delete(onBack)
        }, onDismiss = { confirmDelete = false }, destructive = true)
    }
}
