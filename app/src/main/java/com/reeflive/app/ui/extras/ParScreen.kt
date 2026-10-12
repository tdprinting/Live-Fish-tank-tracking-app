package com.reeflive.app.ui.extras

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reeflive.app.appGraph
import com.reeflive.app.ui.common.TimeText
import com.reeflive.app.ui.components.ButtonKind
import com.reeflive.app.ui.components.EmptyState
import com.reeflive.app.ui.components.ReefButton
import com.reeflive.app.ui.components.ReefChip
import com.reeflive.app.ui.components.ReefTextField
import com.reeflive.app.ui.components.SectionHeader
import com.reeflive.app.ui.theme.GlassCard
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.core.extras.ParLogic
import com.reeflive.core.extras.ParReading
import com.reeflive.core.extras.ReefExtras
import com.reeflive.core.util.Fmt
import kotlinx.coroutines.launch

/** PAR readings per spot, with coral light bands. */
@Composable
fun ParContent(data: ReefExtras, onBack: () -> Unit, onAdd: (String?) -> Unit, onEdit: (ParReading) -> Unit, modifier: Modifier = Modifier) {
    val c = ReefTheme.colors
    val spots = ParLogic.latestBySpot(data.par)
    ExtrasPage("PAR map", if (spots.isEmpty()) "Light at each spot" else "${spots.size} spots measured", onBack, modifier, onAdd = { onAdd(null) }, addLabel = "Add PAR reading") {
        if (spots.isEmpty()) {
            EmptyState("No PAR readings", "Borrow or rent a PAR meter and log a reading at each coral. It shows whether each spot suits the coral living there.",
                icon = Icons.Rounded.WbSunny, action = { ReefButton("Add a reading", { onAdd(null) }) })
        }
        spots.forEach { r ->
            val history = data.par.filter { it.spot.trim().equals(r.spot.trim(), ignoreCase = true) }.sortedByDescending { it.at }
            GlassCard(Modifier.fillMaxWidth(), onClick = { onEdit(r) }) {
                Row {
                    Text(r.spot, style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.weight(1f))
                    Text("${Fmt.num(r.par, 0)} PAR", style = MaterialTheme.typography.titleMedium, color = c.primaryBright)
                }
                GlassBar((r.par / 400.0).toFloat())
                Text(ParLogic.band(r.par).label + (r.lightPercent?.let { " · lights at $it %" } ?: "") + " · ${TimeText.shortDate(r.at)}",
                    style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
                if (history.size > 1) {
                    Text("Earlier: " + history.drop(1).take(4).joinToString("  ") { "${TimeText.shortDate(it.at)} ${Fmt.num(it.par, 0)}" },
                        style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                }
                ReefButton("New reading here", { onAdd(r.spot) }, kind = ButtonKind.TONAL, compact = true)
            }
        }
        SectionHeader("Guide")
        GlassCard(Modifier.fillMaxWidth()) {
            ParLogic.bands.forEach { b -> InfoRow(b.label, if (b.hi >= 2000) "${Fmt.num(b.lo, 0)}+ PAR" else "${Fmt.num(b.lo, 0)}–${Fmt.num(b.hi, 0)} PAR") }
            Text("Measure at the coral with the lights at their midday peak. Raise intensity slowly (about 10 % a week).",
                style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
        }
    }
}

@Composable
private fun ParForm(initial: ParReading, knownSpots: List<String>, onSave: (ParReading) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var spot by remember { mutableStateOf(initial.spot) }
    var par by remember { mutableStateOf(if (initial.par > 0) Fmt.num(initial.par, 0) else "") }
    var pct by remember { mutableStateOf(initial.lightPercent?.toString().orEmpty()) }
    FormSheet("PAR reading", onDismiss, onSave = {
        onSave(initial.copy(spot = spot.trim(), par = parseNum(par) ?: 0.0, lightPercent = pct.toIntOrNull()))
    }, saveEnabled = spot.isNotBlank() && (parseNum(par) ?: -1.0) >= 0, onDelete = onDelete) {
        ReefTextField(spot, { spot = it }, Modifier.fillMaxWidth(), label = "Spot", placeholder = "Kenya tree top")
        if (knownSpots.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { knownSpots.take(4).forEach { s -> ReefChip(s, s == spot, { spot = s }) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReefTextField(par, { par = it }, Modifier.weight(1f), label = "PAR", numeric = true)
            ReefTextField(pct, { pct = it.filter(Char::isDigit) }, Modifier.weight(1f), label = "Light % (optional)", numeric = true)
        }
    }
}

@Composable
fun ParRoute(onBack: () -> Unit) {
    val g = LocalContext.current.appGraph
    val data by g.extras.data.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<ParReading?>(null) }
    ParContent(data, onBack, onAdd = { spot -> editing = ParReading(g.extras.newId(), spot.orEmpty(), g.clock.now(), 0.0) }, onEdit = { editing = it })
    editing?.let { r ->
        val exists = data.par.any { it.id == r.id }
        ParForm(r, ParLogic.latestBySpot(data.par).map { it.spot }, onSave = { scope.launch { g.extras.savePar(it) }; editing = null },
            onDelete = if (exists) ({ scope.launch { g.extras.deletePar(r.id) }; editing = null }) else null, onDismiss = { editing = null })
    }
}
