package com.reeflive.app.ui.extras

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
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
import com.reeflive.app.ui.components.GlassIconButton
import com.reeflive.app.ui.components.LocalSnackbar
import com.reeflive.app.ui.components.ReefButton
import com.reeflive.app.ui.components.ReefTextField
import com.reeflive.app.ui.components.SectionHeader
import com.reeflive.app.ui.components.SegmentedPills
import com.reeflive.app.ui.theme.GlassCard
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.core.extras.ExtrasLogic
import com.reeflive.core.extras.Feeding
import com.reeflive.core.extras.FeedingPlan
import com.reeflive.core.extras.ReefExtras
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

data class FeedingActions(
    val onBack: () -> Unit = {},
    val onFeed: (String) -> Unit = {},
    val onDelete: (Feeding) -> Unit = {},
    val onPlan: (FeedingPlan) -> Unit = {},
    val onAddFood: () -> Unit = {},
)

/** Feeding log and schedule (research 01 §3.7): one tap per food, today vs plan, streak, recent history. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeedingContent(data: ReefExtras, now: Long, actions: FeedingActions, modifier: Modifier = Modifier) {
    val c = ReefTheme.colors
    val zone = ZoneId.systemDefault()
    val plan = data.feedingPlan
    val today = ExtrasLogic.feedingsOn(data.feedings, Instant.ofEpochMilli(now).atZone(zone).toLocalDate(), zone)
    val streak = ExtrasLogic.feedingStreak(data.feedings, plan, now, zone)
    ExtrasPage("Feeding", "${today.size} of ${plan.feedsPerDay} today" + if (streak > 1) " · $streak-day streak" else "", actions.onBack, modifier) {
        GlassCard(Modifier.fillMaxWidth(), tint = c.primary) {
            Text("Log a feeding", style = MaterialTheme.typography.titleSmall, color = c.text)
            GlassBar(today.size.toFloat() / plan.feedsPerDay.coerceAtLeast(1))
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                plan.foods.forEach { f -> ReefButton(f, { actions.onFeed(f) }, kind = ButtonKind.TONAL, compact = true) }
                ReefButton("+ Food", actions.onAddFood, kind = ButtonKind.OUTLINED, compact = true)
            }
            Text(
                "Feed small amounts for 2–3 minutes; uneaten food turns into nitrate and phosphate.",
                style = MaterialTheme.typography.bodySmall, color = c.textTertiary,
            )
        }
        SectionHeader("Plan")
        GlassCard(Modifier.fillMaxWidth()) {
            Text("Feedings per day", style = MaterialTheme.typography.bodyLarge, color = c.text)
            SegmentedPills(listOf("1", "2", "3", "4"), (plan.feedsPerDay - 1).coerceIn(0, 3), { actions.onPlan(plan.copy(feedsPerDay = it + 1)) }, Modifier.fillMaxWidth())
        }
        SectionHeader("Recent")
        GlassCard(Modifier.fillMaxWidth()) {
            val recent = data.feedings.sortedByDescending { it.at }.take(20)
            if (recent.isEmpty()) Text("No feedings logged yet.", style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
            recent.forEach { f ->
                InfoRow(f.food + if (f.amount.isNotBlank()) " · ${f.amount}" else "", "${TimeText.dayLabel(f.at, now)} ${TimeText.clock(f.at)}") {
                    GlassIconButton(Icons.Rounded.Close, "Delete", { actions.onDelete(f) })
                }
            }
        }
    }
}

@Composable
fun FeedingRoute(onBack: () -> Unit) {
    val g = LocalContext.current.appGraph
    val data by g.extras.data.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    var addFood by remember { mutableStateOf(false) }
    FeedingContent(
        data, g.clock.now(),
        FeedingActions(
            onBack = onBack,
            onFeed = { f -> scope.launch { g.extras.logFeeding(f); snackbar.showSnackbar("Fed $f") } },
            onDelete = { f -> scope.launch { g.extras.deleteFeeding(f.id) } },
            onPlan = { p -> scope.launch { g.extras.setPlan(p) } },
            onAddFood = { addFood = true },
        ),
    )
    if (addFood) {
        var name by remember { mutableStateOf("") }
        FormSheet("Add a food", { addFood = false }, onSave = {
            val plan = data.feedingPlan
            scope.launch { g.extras.setPlan(plan.copy(foods = (plan.foods + name.trim()).distinct())) }
            addFood = false
        }, saveEnabled = name.isNotBlank()) {
            ReefTextField(name, { name = it }, Modifier.fillMaxWidth(), label = "Food", placeholder = "Reef Roids, LRS, krill…")
        }
    }
}
