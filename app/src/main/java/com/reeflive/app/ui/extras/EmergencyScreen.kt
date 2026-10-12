package com.reeflive.app.ui.extras

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reeflive.app.ui.components.ReefChip
import com.reeflive.app.ui.components.SectionHeader
import com.reeflive.app.ui.theme.GlassCard
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.core.care.EmergencyGuides
import com.reeflive.core.care.Guide

/** Offline emergency guides and a fish symptom checker (research 01 §4.3). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmergencyContent(onBack: () -> Unit, modifier: Modifier = Modifier, initialOpen: String? = null) {
    val c = ReefTheme.colors
    var open by rememberSaveable { mutableStateOf(initialOpen) }
    var signs by rememberSaveable { mutableStateOf(listOf<String>()) }
    ExtrasPage("Emergency", "Works offline · act first, read later", onBack, modifier) {
        EmergencyGuides.guides.forEach { g ->
            GuideCard(g, open == g.id) { open = if (open == g.id) null else g.id }
        }
        SectionHeader("Sick fish? Pick what you see")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            EmergencyGuides.allSigns.forEach { s ->
                ReefChip(s, s in signs, { signs = if (s in signs) signs - s else signs + s }, accent = c.coral)
            }
        }
        val matches = EmergencyGuides.match(signs.toSet())
        matches.take(4).forEach { m ->
            GlassCard(Modifier.fillMaxWidth(), tint = if (m.urgent) c.danger else null) {
                Row {
                    Text(m.name, style = MaterialTheme.typography.titleSmall, color = c.text, modifier = Modifier.weight(1f))
                    if (m.urgent) StatusPill("Urgent", c.danger)
                }
                Text("Signs: ${m.signs.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
                Spacer(Modifier.height(4.dp))
                Text(m.action, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            }
        }
        Text(
            "Reference only, not a diagnosis. Dose medications by the product label, and treat fish in a quarantine tank, not the display.",
            style = MaterialTheme.typography.bodySmall, color = c.textTertiary,
        )
    }
}

@Composable
private fun GuideCard(g: Guide, expanded: Boolean, onToggle: () -> Unit) {
    val c = ReefTheme.colors
    GlassCard(Modifier.fillMaxWidth(), onClick = onToggle, tint = if (expanded) c.coral else null) {
        Row {
            Text(g.title, style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.weight(1f))
            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = c.textSecondary)
        }
        Text(g.summary, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            Text("Right now", style = MaterialTheme.typography.labelLarge, color = c.coral)
            g.now.forEachIndexed { i, s -> Text("${i + 1}. $s", style = MaterialTheme.typography.bodyMedium, color = c.text) }
            Spacer(Modifier.height(6.dp))
            Text("Then", style = MaterialTheme.typography.labelLarge, color = c.primaryBright)
            g.then.forEach { s -> Text("• $s", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary) }
            if (g.avoid.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("Avoid", style = MaterialTheme.typography.labelLarge, color = c.cautionText)
                g.avoid.forEach { s -> Text("• $s", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary) }
            }
        }
    }
}

@Composable
fun EmergencyRoute(onBack: () -> Unit) = EmergencyContent(onBack)
