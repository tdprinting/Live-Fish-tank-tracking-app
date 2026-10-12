package com.reeflive.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reeflive.app.ui.common.TileUi
import com.reeflive.app.ui.components.EmptyState
import com.reeflive.app.ui.components.EnterRise
import com.reeflive.app.ui.components.EventRow
import com.reeflive.app.ui.components.GlassIconButton
import com.reeflive.app.ui.components.HeroTile
import com.reeflive.app.ui.components.ParameterTile
import com.reeflive.app.ui.components.ReorderItem
import com.reeflive.app.ui.components.ReorderSheet
import com.reeflive.app.ui.components.SectionHeader
import com.reeflive.app.ui.components.StatusIcon
import com.reeflive.app.ui.components.StatusPill
import com.reeflive.app.ui.components.TankPeek
import com.reeflive.app.ui.components.TaskCard
import com.reeflive.app.ui.components.TextAction
import com.reeflive.app.ui.components.pagePadding
import com.reeflive.app.ui.theme.GlassCard
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.app.ui.theme.status
import com.reeflive.app.ui.theme.statusText

data class DashboardActions(
    val onOpenParam: (String) -> Unit = {},
    val onLogParam: (String) -> Unit = {},
    val onCompleteTask: (String) -> Unit = {},
    val onOpenTask: (String) -> Unit = {},
    val onOpenCare: () -> Unit = {},
    val onOpenLive: () -> Unit = {},
    val onOpenMore: () -> Unit = {},
    val onDismissAlert: (String) -> Unit = {},
    val onSaveOrder: (List<String>) -> Unit = {},
)

/** Dashboard (DESIGN §5.2). */
@Composable
fun DashboardContent(state: DashboardUi, actions: DashboardActions, modifier: Modifier = Modifier) {
    val c = ReefTheme.colors
    var reorder by remember { mutableStateOf(false) }
    var completing by remember { mutableStateOf(setOf<String>()) }
    LazyColumn(modifier.fillMaxSize(), contentPadding = pagePadding(top = 0.dp)) {
        item(key = "header") { Header(state, actions.onOpenMore) }
        if (state.alerts.isNotEmpty()) {
            item(key = "alerts") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                    items(state.alerts, key = { it.id }) { a ->
                        GlassCard(
                            modifier = Modifier.width(260.dp),
                            tint = c.status(a.status),
                            contentPadding = PaddingValues(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 12.dp),
                            onClick = { actions.onOpenParam(a.paramId) },
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusIcon(a.status, size = 18.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(a.title, style = MaterialTheme.typography.titleSmall, color = c.statusText(a.status), modifier = Modifier.weight(1f))
                                IconButton(onClick = { actions.onDismissAlert(a.id) }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Rounded.Close, "Dismiss alert", tint = c.textTertiary, modifier = Modifier.size(18.dp))
                                }
                            }
                            Text(a.message, style = MaterialTheme.typography.bodyMedium, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        state.hero?.let { hero ->
            item(key = "hero") {
                EnterRise(key = hero.tile.id) {
                    HeroTile(hero, onClick = { actions.onOpenParam(hero.tile.id) })
                }
            }
        }
        item(key = "gridTitle") {
            SectionHeader("Parameters", action = { TextAction("Arrange", { reorder = true }) })
        }
        val rows = state.tiles.chunked(2)
        items(rows.size, key = { "row$it" }) { i ->
            val row = rows[i]
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { tile -> TileCell(tile, actions, { reorder = true }, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (state.due.isNotEmpty()) {
            item(key = "dueTitle") { SectionHeader("Due today", action = { TextAction("See all", actions.onOpenCare) }) }
            items(state.due, key = { "task-" + it.id }) { t ->
                TaskCard(
                    t,
                    checked = t.id in completing,
                    onCheck = {
                        completing = completing + t.id
                        actions.onCompleteTask(t.id)
                    },
                    onClick = { actions.onOpenTask(t.id) },
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
        } else if (!state.loading) {
            item(key = "dueTitle") { SectionHeader("Due today", action = { TextAction("Care", actions.onOpenCare) }) }
            item(key = "dueEmpty") {
                GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.TaskAlt, null, tint = c.goodText, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Nothing due today. Enjoy the view.", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                    }
                }
            }
        }
        state.peek?.let { p ->
            item(key = "peekTitle") { SectionHeader("Live Tank") }
            item(key = "peek") { TankPeek(p.caption, Color(p.lightArgb.toInt()), onClick = actions.onOpenLive) }
        }
        if (state.events.isNotEmpty()) {
            item(key = "eventsTitle") { SectionHeader("Recent") }
            item(key = "events") {
                GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                    state.events.forEach { EventRow(it) }
                }
            }
        }
        if (!state.loading && state.tiles.none { it.logged }) {
            item(key = "firstTest") {
                EmptyState("Your first test", "Tap the coral + button to log Alk, Ca or anything else. Charts and alerts start from there.")
            }
        }
    }
    if (reorder) {
        ReorderSheet(
            title = "Arrange tiles",
            items = state.tiles.map { ReorderItem(it.id, it.name) },
            onDone = { list ->
                actions.onSaveOrder(list.map { it.id })
                reorder = false
            },
            onDismiss = { reorder = false },
        )
    }
}

@Composable
private fun TileCell(tile: TileUi, actions: DashboardActions, onArrange: () -> Unit, modifier: Modifier) {
    ParameterTile(
        tile,
        onClick = { if (tile.logged) actions.onOpenParam(tile.id) else actions.onLogParam(tile.id) },
        onLongClick = onArrange,
        modifier = modifier,
    )
}

@Composable
private fun Header(state: DashboardUi, onMore: () -> Unit) {
    val c = ReefTheme.colors
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(state.date, style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
            Text(state.tankName, style = MaterialTheme.typography.headlineLarge, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(state.summaryStatus, label = state.summary)
                Spacer(Modifier.width(8.dp))
                Text(state.lastTest, style = MaterialTheme.typography.labelMedium, color = c.textTertiary, maxLines = 1)
            }
        }
        Box(Modifier.padding(start = 8.dp)) {
            GlassIconButton(Icons.Rounded.GridView, "More", onMore)
        }
    }
}
