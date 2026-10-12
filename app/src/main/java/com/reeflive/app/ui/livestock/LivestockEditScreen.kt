package com.reeflive.app.ui.livestock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reeflive.app.ui.common.TimeText
import com.reeflive.app.ui.components.ButtonKind
import com.reeflive.app.ui.components.GlassIconButton
import com.reeflive.app.ui.components.ReefButton
import com.reeflive.app.ui.components.ReefChip
import com.reeflive.app.ui.components.ReefSheet
import com.reeflive.app.ui.components.ReefTextField
import com.reeflive.app.ui.components.ScreenHeader
import com.reeflive.app.ui.components.SpeciesAvatar
import com.reeflive.app.ui.components.StoredPhoto
import com.reeflive.app.ui.components.pagePadding
import com.reeflive.app.ui.theme.GlassCard
import com.reeflive.app.ui.theme.ReefShapes
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.core.model.LivestockStatus
import com.reeflive.core.species.SpeciesSheet

/** Livestock edit fields as typed (sizes in cm, price in dollars). */
data class LivestockForm(
    val speciesId: String = "",
    val nickname: String = "",
    val variant: String = "",
    val quantity: String = "1",
    val sizeCm: String = "",
    val acquiredAt: Long? = null,
    val price: String = "",
    val source: String = "",
    val status: LivestockStatus = LivestockStatus.ALIVE,
    val notes: String = "",
    val photoPath: String? = null,
    val verified: Boolean = true,
) {
    val valid: Boolean get() = speciesId.isNotBlank() && (quantity.toIntOrNull() ?: 0) >= 1
}

data class LivestockEditUi(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val form: LivestockForm = LivestockForm(),
    val species: SpeciesSheet? = null,
    val warning: String? = null,
    val saving: Boolean = false,
)

data class LivestockEditActions(
    val onBack: () -> Unit = {},
    val onForm: (LivestockForm) -> Unit = {},
    val onPickDate: () -> Unit = {},
    val onPickPhoto: () -> Unit = {},
    val onRemovePhoto: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onDelete: () -> Unit = {},
)

@Composable
fun LivestockEditContent(
    state: LivestockEditUi,
    actions: LivestockEditActions,
    search: (String) -> List<SpeciesSheet>,
    modifier: Modifier = Modifier,
) {
    val c = ReefTheme.colors
    val f = state.form
    var picking by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize()) {
        ScreenHeader(if (state.isNew) "Add livestock" else "Edit", onBack = actions.onBack, actions = {
            if (!state.isNew) GlassIconButton(Icons.Rounded.Delete, "Delete", actions.onDelete, tint = c.dangerText)
        })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(pagePadding()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            GlassCard(Modifier.fillMaxWidth(), onClick = { picking = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val s = state.species
                    if (s != null) SpeciesAvatar(s.id, s.kind, s.colors, size = 48.dp)
                    else Icon(Icons.Rounded.Search, null, tint = c.primaryBright, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s?.common ?: "Choose species", style = MaterialTheme.typography.titleMedium, color = c.text)
                        Text(s?.scientific?.ifBlank { null } ?: "Search the species list", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                    Text("Change", style = MaterialTheme.typography.labelLarge, color = c.primaryBright)
                }
                state.warning?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = c.cautionText)
                }
            }
            GlassCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ReefTextField(f.nickname, { actions.onForm(f.copy(nickname = it)) }, Modifier.fillMaxWidth(), label = "Nickname", placeholder = state.species?.common ?: "Nemo")
                    ReefTextField(f.variant, { actions.onForm(f.copy(variant = it)) }, Modifier.fillMaxWidth(), label = "Variant / morph", placeholder = "e.g. Snowflake, Gold rim")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ReefTextField(f.quantity, { t -> actions.onForm(f.copy(quantity = t.filter { it.isDigit() }.take(3))) }, Modifier.weight(1f), label = "Count", numeric = true, error = !f.valid && f.speciesId.isNotBlank())
                        ReefTextField(f.sizeCm, { actions.onForm(f.copy(sizeCm = it)) }, Modifier.weight(1f), label = "Size now", numeric = true, suffix = {
                            Text("cm", style = MaterialTheme.typography.labelMedium, color = c.textTertiary)
                        })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CalendarMonth, null, tint = c.textSecondary)
                        Spacer(Modifier.width(8.dp))
                        Text("Added to tank", style = MaterialTheme.typography.bodyLarge, color = c.text, modifier = Modifier.weight(1f))
                        ReefChip(f.acquiredAt?.let { TimeText.longDate(it) } ?: "Pick date", false, actions.onPickDate)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ReefTextField(f.price, { actions.onForm(f.copy(price = it)) }, Modifier.weight(1f), label = "Price", numeric = true, suffix = {
                            Text("$", style = MaterialTheme.typography.labelMedium, color = c.textTertiary)
                        })
                        ReefTextField(f.source, { actions.onForm(f.copy(source = it)) }, Modifier.weight(1f), label = "Source", placeholder = "LFS, online…")
                    }
                    ReefTextField(f.notes, { actions.onForm(f.copy(notes = it)) }, Modifier.fillMaxWidth(), label = "Notes", singleLine = false, minLines = 2)
                }
            }
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Photo", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                Spacer(Modifier.height(8.dp))
                if (f.photoPath != null) {
                    StoredPhoto(f.photoPath, "Photo", Modifier.fillMaxWidth().height(180.dp), shape = ReefShapes.tile)
                    Spacer(Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReefButton(if (f.photoPath == null) "Add photo" else "Replace", actions.onPickPhoto, kind = ButtonKind.TONAL, icon = Icons.Rounded.AddAPhoto, compact = true)
                    if (f.photoPath != null) ReefButton("Remove", actions.onRemovePhoto, kind = ButtonKind.OUTLINED, compact = true)
                }
                Text("Photos stay on this phone; location data is removed.", style = MaterialTheme.typography.labelSmall, color = c.textTertiary, modifier = Modifier.padding(top = 6.dp))
            }
            if (!state.isNew) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LivestockStatus.entries.forEach { s -> ReefChip(s.label, f.status == s, { actions.onForm(f.copy(status = s)) }, accent = statusRing(c, s)) }
                }
            }
            ReefButton(if (state.isNew) "Add to tank" else "Save", actions.onSave, Modifier.fillMaxWidth(), enabled = f.valid && !state.saving)
        }
    }
    if (picking) {
        SpeciesPicker(search, f.speciesId, onPick = {
            actions.onForm(f.copy(speciesId = it.id, verified = true))
            picking = false
        }, onDismiss = { picking = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeciesPicker(search: (String) -> List<SpeciesSheet>, selected: String, onPick: (SpeciesSheet) -> Unit, onDismiss: () -> Unit) {
    val c = ReefTheme.colors
    var q by remember { mutableStateOf("") }
    val results = remember(q) { search(q) }
    ReefSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text("Species", style = MaterialTheme.typography.headlineSmall, color = c.text)
            Spacer(Modifier.height(8.dp))
            ReefTextField(q, { q = it }, Modifier.fillMaxWidth(), placeholder = "Search common or scientific name")
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.fillMaxWidth().height(420.dp)) {
                items(results, key = { it.id }) { s ->
                    PickerRow(s.common, listOf(s.section, s.scientific).filter { it.isNotBlank() }.joinToString(" · "), s.id == selected, { onPick(s) }) {
                        SpeciesAvatar(s.id, s.kind, s.colors, size = 40.dp)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
