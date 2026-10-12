package com.reeflive.app.ui.extras

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Inventory2
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
import com.reeflive.app.ui.components.GlassIconButton
import com.reeflive.app.ui.components.ReefButton
import com.reeflive.app.ui.components.ReefChip
import com.reeflive.app.ui.components.ReefTextField
import com.reeflive.app.ui.components.SectionHeader
import com.reeflive.app.ui.theme.GlassCard
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.core.extras.ExtrasLogic
import com.reeflive.core.extras.ReefExtras
import com.reeflive.core.extras.SpendLogic
import com.reeflive.core.extras.Supply
import com.reeflive.core.extras.SupplyCategory
import com.reeflive.core.extras.SupplyState
import kotlinx.coroutines.launch
import java.time.ZoneId

data class SupplyActions(
    val onBack: () -> Unit = {},
    val onAdd: () -> Unit = {},
    val onEdit: (Supply) -> Unit = {},
    val onUse: (Supply) -> Unit = {},
    val onRestock: (Supply) -> Unit = {},
)

@Composable
fun supplyColor(s: SupplyState): Color {
    val c = ReefTheme.colors
    return when (s) {
        SupplyState.OK -> c.good
        SupplyState.LOW, SupplyState.EXPIRING -> c.caution
        SupplyState.OUT, SupplyState.EXPIRED -> c.danger
    }
}

/** Supplies inventory: salt, food, RO/DI cartridges, test reagents and meds with low-stock and expiry warnings. */
@Composable
fun SuppliesContent(data: ReefExtras, now: Long, actions: SupplyActions, modifier: Modifier = Modifier) {
    val c = ReefTheme.colors
    val attention = ExtrasLogic.needsAttention(data.supplies, now)
    val spent = SpendLogic.total(data.expenses.filter { it.category.name == "SUPPLIES" }, SpendLogic.startOfYear(now, ZoneId.systemDefault()))
    ExtrasPage(
        "Supplies", if (attention.isEmpty()) "${data.supplies.size} items · all stocked" else "${attention.size} need attention",
        actions.onBack, modifier, onAdd = actions.onAdd, addLabel = "Add supply",
    ) {
        if (data.supplies.isEmpty()) {
            EmptyState(
                "Nothing on the shelf yet", "Track salt, food, RO/DI cartridges and test reagents. ReefLive warns you before they run out or expire.",
                icon = Icons.Rounded.Inventory2,
                action = { ReefButton("Add a supply", actions.onAdd) },
            )
            return@ExtrasPage
        }
        if (spent > 0) Text("Supplies this year: ${money(spent)}", style = MaterialTheme.typography.labelMedium, color = c.textTertiary)
        val groups = data.supplies.sortedBy { it.name.lowercase() }.groupBy { it.category }.toSortedMap(compareBy { it.ordinal })
        groups.forEach { (cat, items) ->
            SectionHeader(cat.label)
            GlassCard(Modifier.fillMaxWidth()) {
                items.forEach { s ->
                    val st = ExtrasLogic.supplyState(s, now)
                    val exp = s.expiresAt?.let { " · expires ${TimeText.shortDate(it)}" }.orEmpty()
                    InfoRow(s.name, "${qty(s.quantity)} ${s.unit}".trim() + " · low at ${qty(s.lowAt)}" + exp, Modifier, null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (st != SupplyState.OK) StatusPill(st.label, supplyColor(st))
                            GlassIconButton(Icons.Rounded.Remove, "Use one", { actions.onUse(s) })
                            GlassIconButton(Icons.Rounded.Add, "Restock", { actions.onRestock(s) })
                        }
                    }
                    ReefButton("Edit", { actions.onEdit(s) }, kind = ButtonKind.TONAL, compact = true)
                }
            }
        }
    }
}

@Composable
private fun SupplyForm(initial: Supply, onSave: (Supply) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var cat by remember { mutableStateOf(initial.category) }
    var amount by remember { mutableStateOf(qty(initial.quantity)) }
    var unit by remember { mutableStateOf(initial.unit) }
    var low by remember { mutableStateOf(qty(initial.lowAt)) }
    var cost by remember { mutableStateOf(initial.cost?.let { qty(it) }.orEmpty()) }
    var expires by remember { mutableStateOf(initial.expiresAt) }
    var pickDate by remember { mutableStateOf(false) }
    FormSheet(
        if (initial.name.isBlank()) "New supply" else "Edit supply", onDismiss,
        onSave = {
            onSave(initial.copy(name = name.trim(), category = cat, quantity = parseNum(amount) ?: 0.0, unit = unit.trim(),
                lowAt = parseNum(low) ?: 0.0, cost = parseNum(cost), expiresAt = expires))
        },
        saveEnabled = name.isNotBlank(), onDelete = onDelete,
    ) {
        ReefTextField(name, { name = it }, Modifier.fillMaxWidth(), label = "Name", placeholder = "Red Sea Coral Pro salt")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SupplyCategory.entries.forEach { ReefChip(it.label, it == cat, { cat = it }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReefTextField(amount, { amount = it }, Modifier.weight(1f), label = "On hand", numeric = true)
            ReefTextField(unit, { unit = it }, Modifier.weight(1f), label = "Unit", placeholder = "bags, kg, tests")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReefTextField(low, { low = it }, Modifier.weight(1f), label = "Warn at or below", numeric = true)
            ReefTextField(cost, { cost = it }, Modifier.weight(1f), label = "Price (optional)", numeric = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReefButton(expires?.let { "Expires ${TimeText.shortDate(it)}" } ?: "Set expiry date", { pickDate = true }, kind = ButtonKind.OUTLINED, compact = true)
            if (expires != null) ReefButton("No expiry", { expires = null }, kind = ButtonKind.TONAL, compact = true)
        }
    }
    if (pickDate) DateDialog(expires, onPick = { expires = it; pickDate = false }, onDismiss = { pickDate = false })
}

@Composable
private fun RestockForm(s: Supply, onSave: (Double, Double?) -> Unit, onDismiss: () -> Unit) {
    var amount by remember { mutableStateOf("1") }
    var cost by remember { mutableStateOf("") }
    FormSheet("Restock ${s.name}", onDismiss, onSave = { onSave(parseNum(amount) ?: 0.0, parseNum(cost)) }, saveEnabled = (parseNum(amount) ?: 0.0) > 0, saveLabel = "Restock") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReefTextField(amount, { amount = it }, Modifier.weight(1f), label = "Add (${s.unit.ifBlank { "units" }})", numeric = true)
            ReefTextField(cost, { cost = it }, Modifier.weight(1f), label = "Paid (optional)", numeric = true)
        }
        Text("A price is added to Spending.", style = MaterialTheme.typography.bodySmall, color = ReefTheme.colors.textTertiary)
    }
}

@Composable
fun SuppliesRoute(onBack: () -> Unit) {
    val g = LocalContext.current.appGraph
    val data by g.extras.data.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Supply?>(null) }
    var restocking by remember { mutableStateOf<Supply?>(null) }
    SuppliesContent(
        data, g.clock.now(),
        SupplyActions(
            onBack = onBack,
            onAdd = { editing = Supply(g.extras.newId(), "") },
            onEdit = { editing = it },
            onUse = { s -> scope.launch { g.extras.adjustSupply(s.id, -1.0) } },
            onRestock = { restocking = it },
        ),
    )
    editing?.let { s ->
        val exists = data.supplies.any { it.id == s.id }
        SupplyForm(
            s,
            onSave = { scope.launch { g.extras.saveSupply(it) }; editing = null },
            onDelete = if (exists) ({ scope.launch { g.extras.deleteSupply(s.id) }; editing = null }) else null,
            onDismiss = { editing = null },
        )
    }
    restocking?.let { s ->
        RestockForm(s, onSave = { a, cost -> scope.launch { g.extras.restock(s.id, a, cost) }; restocking = null }, onDismiss = { restocking = null })
    }
}
