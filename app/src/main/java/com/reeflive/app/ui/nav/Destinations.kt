package com.reeflive.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import com.reeflive.app.ui.care.CalculatorRoute
import com.reeflive.app.ui.care.CareRoute
import com.reeflive.app.ui.care.TaskDetailRoute
import com.reeflive.app.ui.dashboard.DashboardRoute
import com.reeflive.app.ui.livestock.LivestockDetailRoute
import com.reeflive.app.ui.livestock.LivestockEditRoute
import com.reeflive.app.ui.livestock.LivestockListRoute
import com.reeflive.app.ui.log.LogRoute
import com.reeflive.app.ui.log.ParameterDetailRoute
import com.reeflive.app.ui.log.QuickLogRoute
import com.reeflive.app.ui.log.TestSessionRoute
import com.reeflive.app.ui.more.EquipmentEditRoute
import com.reeflive.app.ui.more.EquipmentListRoute
import com.reeflive.app.ui.more.ExportRoute
import com.reeflive.app.ui.more.JournalEditRoute
import com.reeflive.app.ui.more.JournalListRoute
import com.reeflive.app.ui.more.MoreRoute
import com.reeflive.app.ui.more.AboutRoute
import com.reeflive.app.ui.more.LightScheduleRoute
import com.reeflive.app.ui.more.SettingsRoute
import com.reeflive.app.ui.more.TankSettingsRoute
import com.reeflive.app.ui.more.TargetsRoute

/*
 * Route → screen wiring. Each destination builds its ViewModel via [reefViewModel] and hands plain state
 * to a stateless *Content composable.
 */

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
@Composable
fun CareDestination(nav: NavHostController) = CareRoute(
    onOpenTask = { nav.navigate(Routes.task(it)) },
    onNewTask = { nav.navigate(Routes.task(Routes.NEW)) },
    onOpenCalc = { nav.navigate(Routes.calc(it.id)) },
)

@Composable fun TaskDestination(taskId: String, onBack: () -> Unit) = TaskDetailRoute(taskId, onBack)
@Composable fun CalcDestination(calcId: String, onBack: () -> Unit) = CalculatorRoute(calcId, onBack)
@Composable fun MoreDestination(nav: NavHostController) = MoreRoute(onOpen = { nav.navigate(it) })
@Composable
fun LivestockListDestination(nav: NavHostController, onBack: () -> Unit) = LivestockListRoute(
    onBack = onBack,
    onOpen = { nav.navigate(Routes.livestock(it)) },
    onAdd = { nav.navigate(Routes.livestockEdit(Routes.NEW)) },
)

@Composable
fun LivestockDetailDestination(nav: NavHostController, id: String, onBack: () -> Unit) = LivestockDetailRoute(
    id = id,
    onBack = onBack,
    onEdit = { nav.navigate(Routes.livestockEdit(id)) },
    onViewLive = {
        LiveFocus.follow(id)
        nav.navigateTab(Tab.LIVE)
    },
)

@Composable
fun LivestockEditDestination(nav: NavHostController, id: String, onBack: () -> Unit) = LivestockEditRoute(
    id = id,
    onBack = onBack,
    onSaved = { newId ->
        if (id == Routes.NEW) {
            nav.navigate(Routes.livestock(newId)) { popUpTo(Routes.LIVESTOCK) }
        } else onBack()
    },
    onDeleted = { nav.popBackStack(Routes.LIVESTOCK, inclusive = false) },
)
@Composable
fun EquipmentListDestination(nav: NavHostController, onBack: () -> Unit) =
    EquipmentListRoute(onBack, onOpen = { nav.navigate(Routes.equipment(it)) }, onAdd = { nav.navigate(Routes.equipment(Routes.NEW)) })
@Composable fun EquipmentEditDestination(id: String, onBack: () -> Unit) = EquipmentEditRoute(id, onBack)
@Composable
fun JournalListDestination(nav: NavHostController, onBack: () -> Unit) =
    JournalListRoute(onBack, onOpen = { nav.navigate(Routes.journal(it)) }, onAdd = { nav.navigate(Routes.journal(Routes.NEW)) })
@Composable fun JournalEditDestination(id: String, onBack: () -> Unit) = JournalEditRoute(id, onBack)
@Composable fun LightsDestination(onBack: () -> Unit) = LightScheduleRoute(onBack)
@Composable fun TankDestination(onBack: () -> Unit) = TankSettingsRoute(onBack)
@Composable
fun SettingsDestination(nav: NavHostController, onBack: () -> Unit) = SettingsRoute(
    onBack = onBack,
    onTargets = { nav.navigate(Routes.TARGETS) },
    onTank = { nav.navigate(Routes.TANK) },
    onExport = { nav.navigate(Routes.EXPORT) },
    onUpdates = { nav.navigate(Routes.UPDATES) },
    onWiped = {
        nav.navigate(Routes.ONBOARDING) {
            popUpTo(nav.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    },
)
@Composable fun TargetsDestination(nav: NavHostController, onBack: () -> Unit) = TargetsRoute(onBack, onOpenParam = { nav.navigate(Routes.param(it)) })
@Composable fun ExportDestination(onBack: () -> Unit) = ExportRoute(onBack)
@Composable fun AboutDestination(onBack: () -> Unit) = AboutRoute(onBack)

@Composable fun QuickLogHost(request: QuickLogRequest, onDismiss: () -> Unit) = QuickLogRoute(request.paramIds, onDismiss)
