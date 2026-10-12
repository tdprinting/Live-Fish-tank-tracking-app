package com.reeflive.app.ui.livestock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Warning
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reeflive.app.ui.components.ButtonKind
import com.reeflive.app.ui.components.EmptyState
import com.reeflive.app.ui.components.FactRow
import com.reeflive.app.ui.components.GlassIconButton
import com.reeflive.app.ui.components.ReefButton
import com.reeflive.app.ui.components.ReefChip
import com.reeflive.app.ui.components.ScreenHeader
import com.reeflive.app.ui.components.SectionHeader
import com.reeflive.app.ui.components.SpeciesAvatar
import com.reeflive.app.ui.components.StoredPhoto
import com.reeflive.app.ui.components.pagePadding
import com.reeflive.app.ui.theme.GlassCard
import com.reeflive.app.ui.theme.ReefColors
import com.reeflive.app.ui.theme.ReefShapes
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.core.model.LivestockStatus
import com.reeflive.core.species.Risk

fun riskColor(c: ReefColors, r: Risk): Color = when (r.level) {
    0 -> c.goodText
    1 -> c.primaryBright
    2 -> c.cautionText
    else -> c.dangerText
}

fun statusRing(c: ReefColors, s: LivestockStatus): Color = when (s) {
    LivestockStatus.ALIVE -> c.good
    LivestockStatus.REHOMED -> c.textTertiary
    LivestockStatus.DECEASED -> c.danger
}

// ---------------------------------------------------------------- List

data class LivestockListUi(val loading: Boolean = true, val sections: List<LivestockSection> = emptyList(), val showGone: Boolean = false, val count: Int = 0)

data class LivestockListActions(
    val onBack: () -> Unit = {},
    val onOpen: (String) -> Unit = {},
    val onAdd: () -> Unit = {},
    val onShowGone: (Boolean) -> Unit = {},
)

/** Livestock list (DESIGN §5.10): Fish / Inverts / Corals / Algae. */
@Composable
fun LivestockListContent(state: LivestockListUi, actions: LivestockListActions, modifier: Modifier = Modifier) {
    val c = ReefTheme.colors
    LazyColumn(modifier.fillMaxSize(), contentPadding = pagePadding(top = 0.dp)) {
        item(key = "header") {
            ScreenHeader("Livestock", subtitle = "${state.count} in the tank", onBack = actions.onBack, modifier = Modifier.padding(horizontal = 0.dp), actions = {
                GlassIconButton(Icons.Rounded.Add, "Add livestock", actions.onAdd)
            })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                ReefChip("In tank", !state.showGone, { actions.onShowGone(false) })
                ReefChip("Include past", state.showGone, { actions.onShowGone(true) })
            }
        }
        if (!state.loading && state.sections.isEmpty()) {
            item(key = "empty") { EmptyState("No livestock yet", "Add your fish, inverts and corals to see compatibility and tank-size advice.", icon = Icons.Rounded.Pets) }
        }
        state.sections.forEach { section ->
            item(key = "s-" + section.title) { SectionHeader("${section.title} · ${section.items.sumOf { it.quantity }}") }
            items(section.items, key = { it.id }) { item ->
                LivestockCard(item, { actions.onOpen(item.id) }, Modifier.padding(bottom = 10.dp))
            }
        }
    }
}

@Composable
fun LivestockCard(item: LivestockCardUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = ReefTheme.colors
    GlassCard(modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(12.dp), fillAlpha = if (item.status == LivestockStatus.ALIVE) 1f else 0.6f) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (item.photoPath != null) StoredPhoto(item.photoPath, item.title, Modifier.size(56.dp), shape = ReefShapes.pill)
            else SpeciesAvatar(item.speciesId, item.kind, item.colors, size = 56.dp, ring = statusRing(c, item.status))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (item.quantity > 1) Text("  ×${item.quantity}", style = ReefTheme.numbers.label, color = c.textSecondary)
                    if (item.warning) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Rounded.Warning, "Outgrows this tank", tint = c.cautionText, modifier = Modifier.size(16.dp))
                    }
                }
                Text(item.species, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(item.since?.let { "$it in tank" }, if (item.status != LivestockStatus.ALIVE) item.status.label else null, if (item.unverified) "ID unconfirmed" else null)
                if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
            }
        }
    }
}

// ---------------------------------------------------------------- Detail

data class LivestockDetailActions(
    val onBack: () -> Unit = {},
    val onEdit: () -> Unit = {},
    val onViewLive: () -> Unit = {},
    val onAddHealth: () -> Unit = {},
    val onDeleteHealth: (String) -> Unit = {},
    val onStatus: (LivestockStatus) -> Unit = {},
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LivestockDetailContent(state: LivestockDetailUi, actions: LivestockDetailActions, modifier: Modifier = Modifier) {
    val c = ReefTheme.colors
    val card = state.card
    if (card == null) {
        Column(modifier.fillMaxSize()) {
            ScreenHeader("Livestock", onBack = actions.onBack)
            if (!state.loading) EmptyState("Not found", "This animal may have been deleted.")
        }
        return
    }
    var openNote by remember { mutableStateOf<String?>(null) }
    LazyColumn(modifier.fillMaxSize(), contentPadding = pagePadding(top = 0.dp)) {
        item(key = "header") {
            ScreenHeader("", onBack = actions.onBack, modifier = Modifier.padding(horizontal = 0.dp), actions = {
                GlassIconButton(Icons.Rounded.Edit, "Edit", actions.onEdit)
            })
        }
        item(key = "hero") {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (card.photoPath != null) StoredPhoto(card.photoPath, card.title, Modifier.fillMaxWidth().height(220.dp), shape = ReefShapes.card)
                else SpeciesAvatar(card.speciesId, card.kind, card.colors, size = 132.dp, ring = statusRing(c, card.status), contentDescription = card.species)
                Spacer(Modifier.height(12.dp))
                Text(card.title, style = MaterialTheme.typography.headlineMedium, color = c.text)
                Text(card.species, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                if (state.scientific.isNotBlank()) Text(state.scientific, style = MaterialTheme.typography.bodySmall.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), color = c.textTertiary)
                if (card.unverified) Text("Species ID unconfirmed — change it in Edit if needed", style = MaterialTheme.typography.labelSmall, color = c.cautionText, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(12.dp))
                if (state.canViewLive) ReefButton("View in Live Tank", actions.onViewLive, kind = ButtonKind.TONAL, icon = Icons.Rounded.Visibility, compact = true)
            }
        }
        state.warning?.let { w ->
            item(key = "warn") {
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth().clip(ReefShapes.tile).background(c.caution.copy(alpha = c.statusFillAlpha)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Warning, null, tint = c.cautionText)
                    Spacer(Modifier.width(10.dp))
                    Text(w, style = MaterialTheme.typography.bodyMedium, color = c.cautionText)
                }
            }
        }
        item(key = "facts") {
            SectionHeader("About")
            GlassCard(Modifier.fillMaxWidth()) {
                state.facts.forEach { (k, v) -> FactRow(k, v) }
                if (!state.notes.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(state.notes, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                }
            }
        }
        if (state.compat.isNotEmpty()) {
            item(key = "compat") {
                SectionHeader("Tank mates")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.compat.forEach { cu ->
                        val col = riskColor(c, cu.risk)
                        ReefChip("${cu.name} · ${cu.risk.label}", selected = openNote == cu.name, onClick = {
                            openNote = if (openNote == cu.name || cu.note.isBlank()) null else cu.name
                        }, accent = col)
                    }
                }
                state.compat.firstOrNull { it.name == openNote }?.let { cu ->
                    Text(cu.note, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        if (state.careNotes.isNotEmpty()) {
            item(key = "care") {
                SectionHeader("Care notes")
                GlassCard(Modifier.fillMaxWidth()) {
                    state.careNotes.forEach { n ->
                        Row(Modifier.padding(vertical = 3.dp)) {
                            Text("•", color = c.primaryBright, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.width(8.dp))
                            Text(n, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                        }
                    }
                }
            }
        }
        item(key = "healthTitle") {
            SectionHeader("Health log", action = { ReefButton("Add", actions.onAddHealth, kind = ButtonKind.TONAL, compact = true, icon = Icons.Rounded.HealthAndSafety) })
            if (state.health.isEmpty()) Text("No health notes. Log symptoms and treatments here.", style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
        }
        items(state.health, key = { it.id }) { h ->
            GlassCard(Modifier.fillMaxWidth().padding(bottom = 8.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp), onLongClick = { actions.onDeleteHealth(h.id) }) {
                Row {
                    Text(h.symptom, style = MaterialTheme.typography.bodyLarge, color = c.text, modifier = Modifier.weight(1f))
                    Text(h.date, style = MaterialTheme.typography.labelMedium, color = c.textTertiary)
                }
                if (h.detail != null) Text(h.detail, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
        }
        item(key = "status") {
            SectionHeader("Status")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LivestockStatus.entries.forEach { s ->
                    ReefChip(s.label, card.status == s, { if (card.status != s) actions.onStatus(s) }, accent = statusRing(c, s))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Small clickable text row used in pickers. */
@Composable
internal fun PickerRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit, leading: @Composable () -> Unit) {
    val c = ReefTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(ReefShapes.tile)
            .background(if (selected) c.primary.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = c.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
