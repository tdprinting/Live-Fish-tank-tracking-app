package com.reeflive.app.ui.log

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reeflive.app.ui.common.TileUi
import com.reeflive.app.ui.components.EmptyState
import com.reeflive.app.ui.components.GlassIconButton
import com.reeflive.app.ui.components.ParameterRow
import com.reeflive.app.ui.components.ReorderItem
import com.reeflive.app.ui.components.ReorderSheet
import com.reeflive.app.ui.components.ScreenHeader
import com.reeflive.app.ui.components.SegmentedPills
import com.reeflive.app.ui.components.pagePadding
import com.reeflive.app.ui.theme.GlassCard
import com.reeflive.app.ui.theme.ReefShapes
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.app.ui.theme.statusFill
import com.reeflive.app.ui.theme.statusText
import com.reeflive.core.status.Status

/** One value chip in a history session ("Alk 8.6"). */
data class ValueChip(val paramId: String, val text: String, val status: Status)

data class SessionUi(val id: String, val time: String, val kit: String?, val note: String?, val values: List<ValueChip>)

data class HistoryDay(val label: String, val sessions: List<SessionUi>)

data class LogUi(
    val loading: Boolean = true,
    val tiles: List<TileUi> = emptyList(),
    /** For the arrange sheet: every parameter including hidden ones. */
    val arrange: List<ReorderItem> = emptyList(),
    val days: List<HistoryDay> = emptyList(),
)

data class LogActions(
    val onOpenParam: (String) -> Unit = {},
    val onOpenSession: (String) -> Unit = {},
    val onDeleteSession: (String) -> Unit = {},
    val onArrange: (List<ReorderItem>) -> Unit = {},
)

/** Log tab (DESIGN §5.4): Parameters | History. */
@Composable
fun ParametersContent(state: LogUi, actions: LogActions, modifier: Modifier = Modifier, initialTab: Int = 0) {
    var tab by remember { mutableStateOf(initialTab) }
    var arranging by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize()) {
        ScreenHeader("Log", actions = {
            if (tab == 0) GlassIconButton(Icons.Rounded.Tune, "Arrange and hide parameters", { arranging = true })
        })
        SegmentedPills(listOf("Parameters", "History"), tab, { tab = it }, Modifier.padding(horizontal = 16.dp).fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        if (tab == 0) ParamList(state, actions) else HistoryList(state, actions)
    }
    if (arranging) {
        ReorderSheet(
            title = "Parameters",
            items = state.arrange,
            allowHide = true,
            onDone = {
                actions.onArrange(it)
                arranging = false
            },
            onDismiss = { arranging = false },
        )
    }
}

@Composable
private fun ParamList(state: LogUi, actions: LogActions) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pagePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(state.tiles, key = { it.id }) { t ->
            ParameterRow(t, onClick = { actions.onOpenParam(t.id) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryList(state: LogUi, actions: LogActions) {
    val c = ReefTheme.colors
    if (!state.loading && state.days.isEmpty()) {
        EmptyState("No tests yet", "Every test you log shows up here, grouped by day. Swipe one away to delete it.", icon = Icons.Rounded.History)
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pagePadding()) {
        state.days.forEach { day ->
            item(key = "day-" + day.label) {
                Text(day.label, style = MaterialTheme.typography.titleSmall, color = c.textSecondary, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
            }
            items(day.sessions, key = { it.id }) { s ->
                val dismiss = rememberSwipeToDismissBoxState(
                    confirmValueChange = { v ->
                        if (v == SwipeToDismissBoxValue.EndToStart) {
                            actions.onDeleteSession(s.id)
                            true
                        } else false
                    },
                )
                SwipeToDismissBox(
                    state = dismiss,
                    enableDismissFromStartToEnd = false,
                    modifier = Modifier.padding(bottom = 10.dp),
                    backgroundContent = {
                        Box(
                            Modifier.fillMaxSize().clip(ReefShapes.card).background(c.danger.copy(alpha = 0.22f)).padding(end = 20.dp),
                            contentAlignment = Alignment.CenterEnd,
                        ) { Icon(Icons.Rounded.Delete, "Delete", tint = c.dangerText) }
                    },
                ) {
                    SessionCard(s, onClick = { actions.onOpenSession(s.id) })
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SessionCard(s: SessionUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = ReefTheme.colors
    GlassCard(modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.time, style = ReefTheme.numbers.body, color = c.text)
            if (s.kit != null) {
                Spacer(Modifier.width(8.dp))
                Text("· ${s.kit}", style = MaterialTheme.typography.labelMedium, color = c.textTertiary)
            }
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            s.values.forEach { v ->
                Text(
                    v.text,
                    style = ReefTheme.numbers.label,
                    color = c.statusText(v.status),
                    modifier = Modifier.clip(ReefShapes.pill).background(c.statusFill(v.status)).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        if (!s.note.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(s.note, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
