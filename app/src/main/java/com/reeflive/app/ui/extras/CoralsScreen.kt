package com.reeflive.app.ui.extras

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Spa
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reeflive.app.appGraph
import com.reeflive.app.ui.common.TimeText
import com.reeflive.app.ui.components.ButtonKind
import com.reeflive.app.ui.components.EmptyState
import com.reeflive.app.ui.components.LocalSnackbar
import com.reeflive.app.ui.components.ReefButton
import com.reeflive.app.ui.components.ReefChip
import com.reeflive.app.ui.components.ReefTextField
import com.reeflive.app.ui.components.SectionHeader
import com.reeflive.app.ui.components.SpeciesAvatar
import com.reeflive.app.ui.components.StoredPhoto
import com.reeflive.app.ui.theme.GlassCard
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.core.extras.FragEntry
import com.reeflive.core.extras.FragFate
import com.reeflive.core.extras.GrowthEntry
import com.reeflive.core.extras.ReefExtras
import com.reeflive.core.species.SpeciesKind
import com.reeflive.core.util.Fmt
import kotlinx.coroutines.launch

/** A coral in the tank, as the coral log needs it. */
data class CoralItem(val id: String, val name: String, val speciesId: String, val species: String, val colors: Map<String, String> = emptyMap())

data class CoralActions(
    val onBack: () -> Unit = {},
    val onOpen: (CoralItem) -> Unit = {},
    val onAddPhoto: (CoralItem) -> Unit = {},
    val onAddSize: (CoralItem) -> Unit = {},
    val onAddFrag: (CoralItem) -> Unit = {},
    val onEditGrowth: (GrowthEntry) -> Unit = {},
    val onEditFrag: (FragEntry) -> Unit = {},
)

/** Coral growth timeline (photos and sizes over time) and frag log, per coral. */
@Composable
fun CoralsContent(data: ReefExtras, corals: List<CoralItem>, selected: CoralItem?, actions: CoralActions, modifier: Modifier = Modifier) {
    val c = ReefTheme.colors
    if (selected == null) {
        ExtrasPage("Coral log", "Growth photos and frags", actions.onBack, modifier) {
            if (corals.isEmpty()) {
                EmptyState("No corals in Livestock", "Add corals in Livestock to keep a growth timeline and a frag log for each.", icon = Icons.Rounded.Spa)
            }
            corals.forEach { coral ->
                val g = data.growth.filter { it.livestockId == coral.id }
                val f = data.frags.filter { it.livestockId == coral.id }
                GlassCard(Modifier.fillMaxWidth(), onClick = { actions.onOpen(coral) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SpeciesAvatar(coral.speciesId, SpeciesKind.CORAL, coral.colors, size = 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(coral.name, style = MaterialTheme.typography.titleSmall, color = c.text)
                            Text(
                                "${g.size} check-ins · ${f.sumOf { it.count }} frags" + (g.maxByOrNull { it.at }?.let { " · last ${TimeText.shortDate(it.at)}" } ?: ""),
                                style = MaterialTheme.typography.labelMedium, color = c.textTertiary,
                            )
                        }
                    }
                }
            }
        }
        return
    }
    val growth = data.growth.filter { it.livestockId == selected.id }.sortedBy { it.at }
    val frags = data.frags.filter { it.livestockId == selected.id }.sortedByDescending { it.at }
    ExtrasPage(selected.name, selected.species, actions.onBack, modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReefButton("Add photo", { actions.onAddPhoto(selected) }, compact = true)
            ReefButton("Log size", { actions.onAddSize(selected) }, kind = ButtonKind.TONAL, compact = true)
            ReefButton("Log frag", { actions.onAddFrag(selected) }, kind = ButtonKind.OUTLINED, compact = true)
        }
        SectionHeader("Growth timeline")
        if (growth.isEmpty()) Text("Add a photo every few weeks from the same angle to see it grow.", style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
        val sized = growth.filter { it.sizeCm != null }
        if (sized.size >= 2) {
            val first = sized.first()
            val last = sized.last()
            val days = ((last.at - first.at) / 86_400_000L).coerceAtLeast(1)
            Text("Grew ${Fmt.num(last.sizeCm!! - first.sizeCm!!, 1)} cm in $days days.", style = MaterialTheme.typography.bodyMedium, color = c.goodText)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            growth.forEach { e ->
                GlassCard(Modifier.width(150.dp), onClick = { actions.onEditGrowth(e) }) {
                    val path = e.photoPath
                    if (path != null) StoredPhoto(path, "Photo of ${selected.name}", Modifier.fillMaxWidth().height(110.dp))
                    Text(TimeText.shortDate(e.at), style = MaterialTheme.typography.labelLarge, color = c.text)
                    e.sizeCm?.let { Text("${Fmt.num(it, 1)} cm", style = MaterialTheme.typography.labelMedium, color = c.primaryBright) }
                    if (e.note.isNotBlank()) Text(e.note, style = MaterialTheme.typography.labelSmall, color = c.textTertiary, maxLines = 2)
                }
            }
        }
        SectionHeader("Frags")
        if (frags.isEmpty()) Text("No frags logged.", style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
        else GlassCard(Modifier.fillMaxWidth()) {
            frags.forEach { f ->
                InfoRow("${f.count} × ${f.fate.label}", TimeText.shortDate(f.at) + (f.price?.let { " · ${money(it)}" } ?: "") + if (f.note.isNotBlank()) " · ${f.note}" else "") {
                    ReefButton("Edit", { actions.onEditFrag(f) }, kind = ButtonKind.TONAL, compact = true)
                }
            }
        }
    }
}

@Composable
private fun GrowthForm(initial: GrowthEntry, onSave: (GrowthEntry) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var size by remember { mutableStateOf(initial.sizeCm?.let { Fmt.num(it, 1) }.orEmpty()) }
    var note by remember { mutableStateOf(initial.note) }
    FormSheet("Growth check-in", onDismiss, onSave = { onSave(initial.copy(sizeCm = parseNum(size), note = note.trim())) },
        saveEnabled = initial.photoPath != null || parseNum(size) != null, onDelete = onDelete) {
        initial.photoPath?.let { StoredPhoto(it, null, Modifier.fillMaxWidth().height(180.dp)) }
        ReefTextField(size, { size = it }, Modifier.fillMaxWidth(), label = "Size (cm, widest point)", numeric = true)
        ReefTextField(note, { note = it }, Modifier.fillMaxWidth(), label = "Note", placeholder = "Polyps fully out, new branch")
    }
}

@Composable
private fun FragForm(initial: FragEntry, onSave: (FragEntry) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var count by remember { mutableStateOf(initial.count.toString()) }
    var fate by remember { mutableStateOf(initial.fate) }
    var price by remember { mutableStateOf(initial.price?.let { qty(it) }.orEmpty()) }
    var note by remember { mutableStateOf(initial.note) }
    FormSheet("Frag", onDismiss, onSave = { onSave(initial.copy(count = count.toIntOrNull()?.coerceAtLeast(1) ?: 1, fate = fate, price = parseNum(price), note = note.trim())) },
        onDelete = onDelete) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReefTextField(count, { count = it.filter(Char::isDigit) }, Modifier.weight(1f), label = "How many", numeric = true)
            ReefTextField(price, { price = it }, Modifier.weight(1f), label = "Price (optional)", numeric = true)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FragFate.entries.forEach { ReefChip(it.label, it == fate, { fate = it }) }
        }
        ReefTextField(note, { note = it }, Modifier.fillMaxWidth(), label = "Note", placeholder = "Glued to plug, to Sam")
    }
}

@Composable
fun CoralsRoute(onBack: () -> Unit) {
    val g = LocalContext.current.appGraph
    val data by g.extras.data.collectAsStateWithLifecycle()
    val livestock by g.livestock.alive().collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val corals = livestock.mapNotNull { l ->
        val s = g.speciesDb[l.speciesId] ?: return@mapNotNull null
        if (s.kind != SpeciesKind.CORAL) return@mapNotNull null
        CoralItem(l.id, l.nickname, l.speciesId, s.common, s.colors)
    }
    var selectedId by remember { mutableStateOf<String?>(null) }
    val selected = corals.firstOrNull { it.id == selectedId }
    var growth by remember { mutableStateOf<GrowthEntry?>(null) }
    var frag by remember { mutableStateOf<FragEntry?>(null) }
    var photoFor by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val id = photoFor
        if (uri != null && id != null) scope.launch {
            val p = g.photos.import(uri)
            if (p == null) snackbar.showSnackbar("Couldn't read that photo") else growth = GrowthEntry(g.extras.newId(), id, p.takenAt, photoPath = p.path)
        }
    }
    BackHandler(enabled = selected != null) { selectedId = null }
    CoralsContent(
        data, corals, selected,
        CoralActions(
            onBack = { if (selected != null) selectedId = null else onBack() },
            onOpen = { selectedId = it.id },
            onAddPhoto = {
                photoFor = it.id
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onAddSize = { growth = GrowthEntry(g.extras.newId(), it.id, g.clock.now()) },
            onAddFrag = { frag = FragEntry(g.extras.newId(), it.id, g.clock.now()) },
            onEditGrowth = { growth = it },
            onEditFrag = { frag = it },
        ),
    )
    growth?.let { e ->
        val exists = data.growth.any { it.id == e.id }
        GrowthForm(e, onSave = { scope.launch { g.extras.saveGrowth(it) }; growth = null },
            onDelete = if (exists) ({
                scope.launch {
                    g.extras.deleteGrowth(e.id)
                    e.photoPath?.let { g.photos.delete(it) }
                }
                growth = null
            }) else null,
            onDismiss = {
                // A picked photo that was never saved is removed again.
                if (!exists) e.photoPath?.let { p -> scope.launch { g.photos.delete(p) } }
                growth = null
            })
    }
    frag?.let { f ->
        val exists = data.frags.any { it.id == f.id }
        FragForm(f, onSave = { scope.launch { g.extras.saveFrag(it) }; frag = null },
            onDelete = if (exists) ({ scope.launch { g.extras.deleteFrag(f.id) }; frag = null }) else null, onDismiss = { frag = null })
    }
}
