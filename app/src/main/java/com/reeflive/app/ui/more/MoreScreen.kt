package com.reeflive.app.ui.more

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Emergency
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.reeflive.app.di.AppGraph
import com.reeflive.app.ui.components.ScreenHeader
import com.reeflive.app.ui.components.pagePadding
import com.reeflive.app.ui.nav.Routes
import com.reeflive.app.ui.nav.reefViewModel
import com.reeflive.app.ui.theme.GlassCard
import com.reeflive.app.ui.theme.ReefShapes
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.app.ui.common.TimeText
import com.reeflive.app.ui.extras.money
import com.reeflive.core.extras.ExtrasLogic
import com.reeflive.core.extras.ParLogic
import com.reeflive.core.extras.ReefExtras
import com.reeflive.core.extras.SpendLogic
import com.reeflive.core.model.LivestockStatus
import java.time.Instant
import java.time.ZoneId
import com.reeflive.core.util.Fmt
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** The reef-keeping extras tiles (Insights, Supplies, Feeding …) placed after the first four. */
object MoreExtras {
    fun tiles(e: ReefExtras, now: Long): List<MoreTile> {
        val zone = ZoneId.systemDefault()
        val attention = ExtrasLogic.needsAttention(e.supplies, now).size
        val active = e.cases.filter { ExtrasLogic.isActive(it) }
        val due = active.count { ExtrasLogic.doseDue(it, now, zone) }
        val fedToday = ExtrasLogic.feedingsOn(e.feedings, Instant.ofEpochMilli(now).atZone(zone).toLocalDate(), zone).size
        return listOf(
            MoreTile(Routes.INSIGHTS, "Insights", "Health score · stability", Icons.Rounded.Insights),
            MoreTile(Routes.EMERGENCY, "Emergency", "Guides · sick fish", Icons.Rounded.Emergency),
            MoreTile(Routes.FEEDING, "Feeding", "$fedToday of ${e.feedingPlan.feedsPerDay} today", Icons.Rounded.Restaurant),
            MoreTile(Routes.SUPPLIES, "Supplies", if (attention > 0) "$attention need attention" else "${e.supplies.size} items", Icons.Rounded.Inventory2),
            MoreTile(Routes.TREATMENTS, "Quarantine & meds", when { due > 0 -> "$due dose due"; active.isNotEmpty() -> "${active.size} active"; else -> "None active" }, Icons.Rounded.Medication),
            MoreTile(Routes.CORALS, "Coral log", "${e.growth.size} check-ins · ${e.frags.sumOf { it.count }} frags", Icons.Rounded.Spa),
            MoreTile(Routes.ICP, "ICP tests", e.icp.maxByOrNull { it.at }?.let { "Last ${TimeText.shortDate(it.at)}" } ?: "Trace elements", Icons.Rounded.Science),
            MoreTile(Routes.PAR, "PAR map", "${ParLogic.latestBySpot(e.par).size} spots", Icons.Rounded.WbSunny),
            MoreTile(Routes.SPENDING, "Spending", money(SpendLogic.total(e.expenses, SpendLogic.startOfYear(now, zone))) + " this year", Icons.Rounded.Savings),
        )
    }

    fun insert(base: List<MoreTile>, e: ReefExtras, now: Long): List<MoreTile> = base.take(4) + tiles(e, now) + base.drop(4)
}

data class MoreTile(val route: String, val title: String, val detail: String, val icon: ImageVector)

data class MoreUi(val tankName: String = "", val tankDetail: String = "", val tiles: List<MoreTile> = emptyList())

/** More tab (DESIGN §5.9): a grid of destinations. */
@Composable
fun MoreContent(state: MoreUi, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = ReefTheme.colors
    Column(modifier.fillMaxSize()) {
        ScreenHeader("More", subtitle = state.tankName.ifBlank { null })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(pagePadding()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            state.tiles.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { t -> Tile(t, { onOpen(t.route) }, Modifier.weight(1f)) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Seeded from your tank — edit anytime. ${state.tankDetail}",
                style = MaterialTheme.typography.bodySmall, color = c.textTertiary, modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun Tile(t: MoreTile, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = ReefTheme.colors
    GlassCard(modifier.heightIn(min = 104.dp), shape = ReefShapes.tile, onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(c.primary.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(t.icon, null, tint = c.primaryBright, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(t.title, style = MaterialTheme.typography.titleSmall, color = c.text)
        Text(t.detail, style = MaterialTheme.typography.labelSmall, color = c.textTertiary, maxLines = 1)
    }
}

class MoreViewModel(g: AppGraph) : ViewModel() {
    val state: StateFlow<MoreUi> = combine(
        g.tank.tank, g.livestock.alive(), g.equipment.all(), g.journal.entries, g.lights.active(),
    ) { tank, alive, eq, journal, light ->
        MoreUi(
            tankName = tank?.name.orEmpty(),
            tankDetail = tank?.let { "${Fmt.trim(it.displayGallons, 0)} gal display" + if (it.sumpGallons > 0) " + ${Fmt.trim(it.sumpGallons, 0)} gal sump." else "." }.orEmpty(),
            tiles = listOf(
                MoreTile(Routes.LIVESTOCK, "Livestock", "${alive.filter { it.status == LivestockStatus.ALIVE }.sumOf { it.quantity }} in tank", Icons.Rounded.Pets),
                MoreTile(Routes.EQUIPMENT, "Equipment", "${eq.size} items", Icons.Rounded.Build),
                MoreTile(Routes.JOURNAL, "Journal", if (journal.isEmpty()) "Notes & photos" else "${journal.size} entries", Icons.Rounded.Book),
                MoreTile(Routes.LIGHTS, "Light schedule", light.name, Icons.Rounded.Lightbulb),
                MoreTile(Routes.TANK, "Tank", tank?.let { "${Fmt.trim(it.systemGallons, 0)} gal system" } ?: "Set up", Icons.Rounded.Straighten),
                MoreTile(Routes.EXPORT, "Export & backup", "CSV · JSON", Icons.Rounded.Backup),
                MoreTile(Routes.SETTINGS, "Settings", "Units, theme, alerts", Icons.Rounded.Settings),
                MoreTile(Routes.ABOUT, "About", "ReefLive", Icons.Rounded.Info),
            ),
        )
    }.combine(g.extras.data) { ui, e -> ui.copy(tiles = MoreExtras.insert(ui.tiles, e, g.clock.now())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoreUi())
}

@Composable
fun MoreRoute(onOpen: (String) -> Unit) {
    val vm = reefViewModel { MoreViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    MoreContent(state, onOpen)
}
