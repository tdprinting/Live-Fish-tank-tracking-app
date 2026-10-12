package com.reeflive.app.ui.extras

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reeflive.app.appGraph
import com.reeflive.app.ui.common.TimeText
import com.reeflive.app.ui.components.ButtonKind
import com.reeflive.app.ui.components.DateDialog
import com.reeflive.app.ui.components.EmptyState
import com.reeflive.app.ui.components.ReefButton
import com.reeflive.app.ui.components.ReefTextField
import com.reeflive.app.ui.components.SectionHeader
import com.reeflive.app.ui.theme.GlassCard
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.core.extras.IcpFlag
import com.reeflive.core.extras.IcpLogic
import com.reeflive.core.extras.IcpResult
import com.reeflive.core.extras.ReefExtras
import com.reeflive.core.util.Fmt
import kotlinx.coroutines.launch

@Composable
fun icpColor(f: IcpFlag): Color {
    val c = ReefTheme.colors
    return when (f) {
        IcpFlag.OK -> c.good
        IcpFlag.LOW, IcpFlag.HIGH -> c.caution
        IcpFlag.UNKNOWN -> c.unknown
    }
}

/**
 * ICP lab results with trace elements: each result flagged against reference ranges, and the major elements
 * compared with your latest home-kit readings (research 01 §5: "calibrates home kits").
 */
@Composable
fun IcpContent(data: ReefExtras, homeLatest: Map<String, Double>, onBack: () -> Unit, onAdd: () -> Unit, onEdit: (IcpResult) -> Unit, modifier: Modifier = Modifier) {
    val c = ReefTheme.colors
    val results = data.icp.sortedByDescending { it.at }
    ExtrasPage("ICP tests", if (results.isEmpty()) "Lab results and trace elements" else "${results.size} results", onBack, modifier, onAdd = onAdd, addLabel = "Add ICP result") {
        if (results.isEmpty()) {
            EmptyState(
                "No ICP results yet", "Paste or type a lab report (Triton, ATI, Fauna Marin…). ReefLive flags trace elements and checks your home kits against the lab.",
                icon = Icons.Rounded.Science, action = { ReefButton("Add a result", onAdd) },
            )
            return@ExtrasPage
        }
        val latest = results.first()
        val flagged = IcpLogic.outOfRange(latest)
        GlassCard(Modifier.fillMaxWidth(), tint = if (flagged.isEmpty()) c.good else c.caution, onClick = { onEdit(latest) }) {
            Text("Latest · ${TimeText.longDate(latest.at)}" + if (latest.lab.isNotBlank()) " · ${latest.lab}" else "", style = MaterialTheme.typography.titleSmall, color = c.text)
            Text(
                if (flagged.isEmpty()) "All ${latest.values.size} elements within reference." else "${flagged.size} out of range: " + flagged.joinToString { it.element },
                style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
            )
            Spacer(Modifier.height(6.dp))
            latest.values.forEach { v ->
                val f = IcpLogic.flag(v)
                val ref = IcpLogic.ref(v.element)
                InfoRow("${ref?.name ?: v.element} (${v.element})", ref?.let { "Reference ${Fmt.num(it.lo, 2)}–${Fmt.num(it.hi, 2)} ${it.unit}" }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${Fmt.num(v.value, 2)} ${v.unit}", style = MaterialTheme.typography.bodyMedium, color = c.text)
                        StatusPill(f.label, icpColor(f))
                    }
                }
            }
        }
        val compare = latest.values.mapNotNull { v ->
            val ref = IcpLogic.ref(v.element) ?: return@mapNotNull null
            val home = ref.homeParam?.let { homeLatest[it] } ?: return@mapNotNull null
            Triple(ref, v.value, home)
        }
        if (compare.isNotEmpty()) {
            SectionHeader("Home kits vs lab")
            GlassCard(Modifier.fillMaxWidth()) {
                compare.forEach { (ref, lab, home) ->
                    val d = IcpLogic.kitDeltaPercent(lab, home)
                    InfoRow(ref.name, "Lab ${Fmt.num(lab, 2)} · your kit ${Fmt.num(home, 2)}") {
                        d?.let { StatusPill("${if (it >= 0) "+" else ""}${Fmt.num(it, 0)} %", if (kotlin.math.abs(it) <= 10) c.good else c.caution) }
                    }
                }
                Text("Kit readings within ±10 % of the lab are normal. Bigger gaps usually mean old reagents or a technique issue.",
                    style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
            }
        }
        if (results.size > 1) {
            SectionHeader("History")
            GlassCard(Modifier.fillMaxWidth()) {
                results.drop(1).forEach { r ->
                    InfoRow(TimeText.longDate(r.at), "${r.lab.ifBlank { "Lab" }} · ${r.values.size} elements · ${IcpLogic.outOfRange(r).size} flagged")
                    ReefButton("Open", { onEdit(r) }, kind = ButtonKind.TONAL, compact = true)
                }
            }
        }
    }
}

@Composable
private fun IcpForm(initial: IcpResult, onSave: (IcpResult) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    val c = ReefTheme.colors
    var lab by remember { mutableStateOf(initial.lab) }
    var at by remember { mutableStateOf(initial.at) }
    var text by remember { mutableStateOf(initial.values.joinToString("\n") { "${it.element} ${Fmt.num(it.value, 4)} ${it.unit}".trim() }) }
    var pickDate by remember { mutableStateOf(false) }
    val (values, bad) = IcpLogic.parse(text)
    FormSheet(if (initial.values.isEmpty()) "Add ICP result" else "ICP result", onDismiss, onSave = { onSave(initial.copy(lab = lab.trim(), at = at, values = values)) },
        saveEnabled = values.isNotEmpty(), onDelete = onDelete) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReefTextField(lab, { lab = it }, Modifier.weight(1f), label = "Lab", placeholder = "Triton")
            ReefButton(TimeText.shortDate(at), { pickDate = true }, kind = ButtonKind.OUTLINED, compact = true)
        }
        ReefTextField(text, { text = it }, Modifier.fillMaxWidth(), label = "One element per line", placeholder = "Mg 1320 mg/L\nI 42 µg/L\nSr 8.1",
            singleLine = false, minLines = 6)
        Text(
            "${values.size} elements read" + if (bad.isNotEmpty()) " · skipped: ${bad.take(3).joinToString(" / ")}" else "",
            style = MaterialTheme.typography.labelMedium, color = if (bad.isEmpty()) c.textTertiary else c.cautionText,
        )
    }
    if (pickDate) DateDialog(at, onPick = { at = it; pickDate = false }, onDismiss = { pickDate = false })
}

@Composable
fun IcpRoute(onBack: () -> Unit) {
    val g = LocalContext.current.appGraph
    val data by g.extras.data.collectAsStateWithLifecycle()
    val home by g.measurements.latestValues().collectAsStateWithLifecycle(emptyMap())
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<IcpResult?>(null) }
    IcpContent(data, home, onBack, onAdd = { editing = IcpResult(g.extras.newId(), g.clock.now()) }, onEdit = { editing = it })
    editing?.let { r ->
        val exists = data.icp.any { it.id == r.id }
        IcpForm(r, onSave = { scope.launch { g.extras.saveIcp(it) }; editing = null },
            onDelete = if (exists) ({ scope.launch { g.extras.deleteIcp(r.id) }; editing = null }) else null, onDismiss = { editing = null })
    }
}
