package com.reeflive.app.ui.nav

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.reeflive.app.ui.components.EmptyState
import com.reeflive.app.ui.components.ScreenHeader
import com.reeflive.app.ui.dashboard.DashboardRoute
import com.reeflive.app.ui.log.LogRoute
import com.reeflive.app.ui.log.ParameterDetailRoute
import com.reeflive.app.ui.log.QuickLogRoute
import com.reeflive.app.ui.log.TestSessionRoute
import com.reeflive.app.ui.more.ExportRoute

/*
 * Route → screen wiring. Each destination builds its ViewModel via [reefViewModel] and hands plain state
 * to a stateless *Content composable. Screens land milestone by milestone; until then a calm placeholder.
 */

@Composable
private fun Placeholder(title: String, onBack: (() -> Unit)? = null) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title, onBack = onBack)
        EmptyState("Coming up next", "This part of ReefLive is still being built.")
    }
}

@Composable
fun DashboardDestination(nav: NavHostController) {
    val log = LocalQuickLog.current
    DashboardRoute(
        onOpenParam = { nav.navigate(Routes.param(it)) },
        onLogParam = { log(listOf(it)) },
        onOpenTask = { nav.navigate(Routes.task(it)) },
        onOpenCare = { nav.navigateTab(Tab.CARE) },
        onOpenLive = { nav.navigateTab(Tab.LIVE) },
        onOpenMore = { nav.navigateTab(Tab.MORE) },
    )
}

@Composable
fun LogDestination(nav: NavHostController) =
    LogRoute(onOpenParam = { nav.navigate(Routes.param(it)) }, onOpenSession = { nav.navigate(Routes.session(it)) })

@Composable
fun ParamDestination(nav: NavHostController, paramId: String, onBack: () -> Unit) {
    val log = LocalQuickLog.current
    ParameterDetailRoute(paramId, onBack = onBack, onOpenSession = { nav.navigate(Routes.session(it)) }, onLog = { log(listOf(it)) })
}

@Composable fun SessionDestination(sessionId: String, onBack: () -> Unit) = TestSessionRoute(sessionId, onBack)
@Composable fun CareDestination(nav: NavHostController) = Placeholder("Care")
@Composable fun TaskDestination(taskId: String, onBack: () -> Unit) = Placeholder("Task", onBack)
@Composable fun CalcDestination(calcId: String, onBack: () -> Unit) = Placeholder("Calculator", onBack)
@Composable fun MoreDestination(nav: NavHostController) = Placeholder("More")
@Composable fun LivestockListDestination(nav: NavHostController, onBack: () -> Unit) = Placeholder("Livestock", onBack)
@Composable fun LivestockDetailDestination(nav: NavHostController, id: String, onBack: () -> Unit) = Placeholder("Livestock", onBack)
@Composable fun LivestockEditDestination(id: String, onBack: () -> Unit) = Placeholder("Livestock", onBack)
@Composable fun EquipmentListDestination(nav: NavHostController, onBack: () -> Unit) = Placeholder("Equipment", onBack)
@Composable fun EquipmentEditDestination(id: String, onBack: () -> Unit) = Placeholder("Equipment", onBack)
@Composable fun JournalListDestination(nav: NavHostController, onBack: () -> Unit) = Placeholder("Journal", onBack)
@Composable fun JournalEditDestination(id: String, onBack: () -> Unit) = Placeholder("Journal", onBack)
@Composable fun LightsDestination(onBack: () -> Unit) = Placeholder("Light schedule", onBack)
@Composable fun TankDestination(onBack: () -> Unit) = Placeholder("Tank", onBack)
@Composable fun SettingsDestination(nav: NavHostController, onBack: () -> Unit) = Placeholder("Settings", onBack)
@Composable fun TargetsDestination(onBack: () -> Unit) = Placeholder("Targets", onBack)
@Composable fun ExportDestination(onBack: () -> Unit) = ExportRoute(onBack)
@Composable fun AboutDestination(onBack: () -> Unit) = Placeholder("About", onBack)

@Composable fun QuickLogHost(request: QuickLogRequest, onDismiss: () -> Unit) = QuickLogRoute(request.paramIds, onDismiss)
