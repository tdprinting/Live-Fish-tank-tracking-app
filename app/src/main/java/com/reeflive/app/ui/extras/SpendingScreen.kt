package com.reeflive.app.ui.extras

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Savings
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
import com.reeflive.app.ui.components.DateDialog
import com.reeflive.app.ui.components.EmptyState
import com.reeflive.app.ui.components.ReefButton
import com.reeflive.app.ui.components.ReefChip
import com.reeflive.app.ui.components.ReefTextField
import com.reeflive.app.ui.components.SectionHeader
import com.reeflive.app.ui.theme.GlassCard
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.core.extras.Expense
import com.reeflive.core.extras.ExpenseCategory
import com.reeflive.core.extras.ReefExtras
import com.reeflive.core.extras.SpendLogic
import kotlinx.coroutines.launch
import java.time.ZoneId

/** Spending tracker: logged expenses plus livestock purchase prices, by month, year and category. */
@Composable
fun SpendingContent(data: ReefExtras, livestockSpend: List<Expense>, now: Long, onBack: () -> Unit, onAdd: () -> Unit, onEdit: (Expense) -> Unit, modifier: Modifier = Modifier) {
    val c = ReefTheme.colors
    val zone = ZoneId.systemDefault()
    val all = data.expenses + livestockSpend
    val year = SpendLogic.startOfYear(now, zone)
    val month = SpendLogic.startOfMonth(now, zone)
    ExtrasPage("Spending", "${money(SpendLogic.total(all, year))} this year", onBack, modifier, onAdd = onAdd, addLabel = "Add expense") {
        if (all.isEmpty()) {
            EmptyState("Nothing spent yet", "Log salt, livestock, equipment and the electricity bill. Livestock prices you enter on each animal are counted too.",
                icon = Icons.Rounded.Savings, action = { ReefButton("Add an expense", onAdd) })
            return@ExtrasPage
        }
        GlassCard(Modifier.fillMaxWidth(), tint = c.primary) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Stat("This month", money(SpendLogic.total(all, month)), Modifier.weight(1f))
                Stat("This year", money(SpendLogic.total(all, year)), Modifier.weight(1f))
                Stat("All time", money(SpendLogic.total(all, 0)), Modifier.weight(1f))
            }
        }
        val cats = SpendLogic.byCategory(all, year)
        if (cats.isNotEmpty()) {
            SectionHeader("This year by category")
            GlassCard(Modifier.fillMaxWidth()) {
                val max = cats.maxOf { it.second }.coerceAtLeast(0.01)
                cats.forEach { (cat, total) ->
                    InfoRow(cat.label, null) { Text(money(total), style = MaterialTheme.typography.bodyMedium, color = c.text) }
                    GlassBar((total / max).toFloat(), height = 5.dp)
                }
            }
        }
        SectionHeader("Recent")
        GlassCard(Modifier.fillMaxWidth()) {
            all.sortedByDescending { it.at }.take(30).forEach { e ->
                val fromLivestock = e.id.startsWith(LIVESTOCK_PREFIX)
                InfoRow(e.item, "${e.category.label} · ${TimeText.shortDate(e.at)}" + if (fromLivestock) " · from Livestock" else "") {
                    Text(money(e.amount), style = MaterialTheme.typography.bodyMedium, color = c.text)
                }
                if (!fromLivestock) ReefButton("Edit", { onEdit(e) }, kind = ButtonKind.TONAL, compact = true)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    val c = ReefTheme.colors
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.textTertiary)
        Text(value, style = MaterialTheme.typography.titleMedium, color = c.text)
    }
}

const val LIVESTOCK_PREFIX = "livestock:"

@Composable
private fun ExpenseForm(initial: Expense, onSave: (Expense) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var item by remember { mutableStateOf(initial.item) }
    var amount by remember { mutableStateOf(if (initial.amount > 0) qty(initial.amount) else "") }
    var cat by remember { mutableStateOf(initial.category) }
    var at by remember { mutableStateOf(initial.at) }
    var pickDate by remember { mutableStateOf(false) }
    FormSheet("Expense", onDismiss, onSave = { onSave(initial.copy(item = item.trim(), amount = parseNum(amount) ?: 0.0, category = cat, at = at)) },
        saveEnabled = item.isNotBlank() && (parseNum(amount) ?: 0.0) > 0, onDelete = onDelete) {
        ReefTextField(item, { item = it }, Modifier.fillMaxWidth(), label = "What", placeholder = "Salt bucket")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReefTextField(amount, { amount = it }, Modifier.weight(1f), label = "Amount ($)", numeric = true)
            ReefButton(TimeText.shortDate(at), { pickDate = true }, kind = ButtonKind.OUTLINED, compact = true)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ExpenseCategory.entries.forEach { ReefChip(it.label, it == cat, { cat = it }) }
        }
    }
    if (pickDate) DateDialog(at, onPick = { at = it; pickDate = false }, onDismiss = { pickDate = false })
}

@Composable
fun SpendingRoute(onBack: () -> Unit) {
    val g = LocalContext.current.appGraph
    val data by g.extras.data.collectAsStateWithLifecycle()
    val livestock by g.livestock.all().collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Expense?>(null) }
    val fromLivestock = livestock.mapNotNull { l ->
        val cents = l.priceCents ?: return@mapNotNull null
        if (cents <= 0) return@mapNotNull null
        Expense(LIVESTOCK_PREFIX + l.id, l.acquiredAt ?: l.createdAt, l.nickname, cents / 100.0, ExpenseCategory.LIVESTOCK)
    }
    SpendingContent(data, fromLivestock, g.clock.now(), onBack, onAdd = { editing = Expense(g.extras.newId(), g.clock.now(), "", 0.0) }, onEdit = { editing = it })
    editing?.let { e ->
        val exists = data.expenses.any { it.id == e.id }
        ExpenseForm(e, onSave = { scope.launch { g.extras.saveExpense(it) }; editing = null },
            onDelete = if (exists) ({ scope.launch { g.extras.deleteExpense(e.id) }; editing = null }) else null, onDismiss = { editing = null })
    }
}
