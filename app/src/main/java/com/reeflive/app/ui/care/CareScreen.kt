package com.reeflive.app.ui.care

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Scale
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reeflive.app.ui.common.TaskUi
import com.reeflive.app.ui.components.EmptyState
import com.reeflive.app.ui.components.GlassIconButton
import com.reeflive.app.ui.components.ScreenHeader
import com.reeflive.app.ui.components.SectionHeader
import com.reeflive.app.ui.components.TaskCard
import com.reeflive.app.ui.components.pagePadding
import com.reeflive.app.ui.theme.GlassCard
import com.reeflive.app.ui.theme.ReefShapes
import com.reeflive.app.ui.theme.ReefTheme

/** The six calculators (DESIGN §5.8); [id] is the route argument. */
enum class Calc(val id: String, val title: String, val subtitle: String) {
    DOSING("dosing", "Dosing", "Alk · Ca · Mg"),
    WATER_CHANGE("water", "Water change", "Predict after values"),
    SALT("salt", "Salt mix", "Grams for target SG"),
    EVAPORATION("evap", "Evaporation", "Salinity drift"),
    AMMONIA("ammonia", "Ammonia", "Free NH3 from TAN"),
    CONVERT("convert", "Convert", "Units"),
    ;

    companion object {
        fun of(id: String): Calc? = entries.firstOrNull { it.id == id }
    }
}

fun calcIcon(c: Calc): ImageVector = when (c) {
    Calc.DOSING -> Icons.Rounded.Science
    Calc.WATER_CHANGE -> Icons.Rounded.Waves
    Calc.SALT -> Icons.Rounded.Scale
    Calc.EVAPORATION -> Icons.Rounded.Air
    Calc.AMMONIA -> Icons.Rounded.WaterDrop
    Calc.CONVERT -> Icons.Rounded.SwapHoriz
}

data class CareUi(
    val loading: Boolean = true,
    val today: List<TaskUi> = emptyList(),
    val upcoming: List<TaskUi> = emptyList(),
    val all: List<TaskUi> = emptyList(),
)

data class CareActions(
    val onCheck: (String) -> Unit = {},
    val onSnooze: (String) -> Unit = {},
    val onOpenTask: (String) -> Unit = {},
    val onNewTask: () -> Unit = {},
    val onOpenCalc: (Calc) -> Unit = {},
)

/** Care tab (DESIGN §5.7). Swipe a due task right to complete, left to snooze until tomorrow. */
@Composable
fun CareContent(state: CareUi, actions: CareActions, modifier: Modifier = Modifier) {
    val c = ReefTheme.colors
    LazyColumn(modifier.fillMaxSize(), contentPadding = pagePadding(top = 0.dp)) {
        item(key = "header") {
            ScreenHeader("Care", modifier = Modifier.padding(horizontal = 0.dp), actions = {
                GlassIconButton(Icons.Rounded.Add, "New task", actions.onNewTask)
            })
        }
        item(key = "todayTitle") { SectionHeader("Today") }
        if (!state.loading && state.today.isEmpty()) {
            item(key = "todayEmpty") {
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.TaskAlt, null, tint = c.goodText, modifier = Modifier.size(22.dp))
                        Text("  All caught up — nothing due today.", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                    }
                }
            }
        }
        items(state.today, key = { "t-" + it.id }) { t -> SwipeTask(t, actions, Modifier.padding(bottom = 10.dp)) }
        if (state.upcoming.isNotEmpty()) {
            item(key = "upTitle") { SectionHeader("Next 7 days") }
            items(state.upcoming, key = { "u-" + it.id }) { t -> SwipeTask(t, actions, Modifier.padding(bottom = 10.dp)) }
        }
        item(key = "calcTitle") { SectionHeader("Calculators") }
        item(key = "calcs") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Calc.entries.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { calc -> CalcTile(calc, { actions.onOpenCalc(calc) }, Modifier.weight(1f)) }
                    }
                }
            }
        }
        item(key = "allTitle") { SectionHeader("All tasks") }
        if (!state.loading && state.all.isEmpty()) {
            item(key = "allEmpty") {
                EmptyState("No tasks yet", "Add water changes, filter socks and other chores to get reminders.", icon = Icons.Rounded.TaskAlt)
            }
        }
        items(state.all, key = { "a-" + it.id }) { t ->
            GlassCard(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                shape = ReefShapes.tile,
                onClick = { actions.onOpenTask(t.id) },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.title, style = MaterialTheme.typography.bodyLarge, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(t.subtitle, style = MaterialTheme.typography.labelSmall, color = c.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    com.reeflive.app.ui.components.DueChip(t.dueLabel, t.state)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeTask(t: TaskUi, actions: CareActions, modifier: Modifier = Modifier) {
    val c = ReefTheme.colors
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            when (v) {
                // Act, then let the card spring back: the list update moves it once the data changes,
                // and a cancelled completion dialog leaves it where it was.
                SwipeToDismissBoxValue.StartToEnd -> actions.onCheck(t.id)
                SwipeToDismissBoxValue.EndToStart -> actions.onSnooze(t.id)
                else -> Unit
            }
            false
        },
    )
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        backgroundContent = {
            val toEnd = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Box(
                Modifier.fillMaxSize().clip(ReefShapes.tile)
                    .background(if (toEnd) c.good.copy(alpha = 0.22f) else c.sand.copy(alpha = 0.22f))
                    .padding(horizontal = 20.dp),
                contentAlignment = if (toEnd) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                if (toEnd) Icon(Icons.Rounded.Check, "Done", tint = c.goodText)
                else Icon(Icons.Rounded.Snooze, "Snooze until tomorrow", tint = c.text)
            }
        },
    ) {
        TaskCard(t, onCheck = { actions.onCheck(t.id) }, onClick = { actions.onOpenTask(t.id) })
    }
}

@Composable
private fun CalcTile(calc: Calc, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = ReefTheme.colors
    GlassCard(modifier.heightIn(min = 92.dp), shape = ReefShapes.tile, onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(c.primary.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(calcIcon(calc), null, tint = c.primaryBright, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(calc.title, style = MaterialTheme.typography.titleSmall, color = c.text)
        Text(calc.subtitle, style = MaterialTheme.typography.labelSmall, color = c.textTertiary, maxLines = 1)
    }
}
