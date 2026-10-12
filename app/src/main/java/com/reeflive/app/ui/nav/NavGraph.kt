package com.reeflive.app.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.reeflive.app.data.repo.AppSettings
import com.reeflive.app.ui.components.GlassSnackbarHost
import com.reeflive.app.ui.components.LocalSnackbar
import com.reeflive.app.ui.components.ReefFab
import com.reeflive.app.ui.components.rememberHaptics
import com.reeflive.app.ui.livetank.LiveTankScreen
import com.reeflive.app.ui.onboarding.OnboardingRoute
import com.reeflive.app.ui.theme.CausticBackground

/** Opens the Quick Log sheet, optionally with parameters pre-added (e.g. from "Tap to log"). */
val LocalQuickLog = compositionLocalOf<(List<String>) -> Unit> { {} }

/** A Quick Log request: parameter ids to pre-add. */
data class QuickLogRequest(val paramIds: List<String>)

fun NavHostController.navigateTab(tab: Tab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * App shell: one shared caustic background (it keeps flowing across tab switches), the NavHost,
 * the floating bottom bar, the global "Log test" FAB and the glass snackbar host.
 */
@Composable
fun ReefRoot(settings: AppSettings, pendingRoute: String?, onRouteConsumed: () -> Unit) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val haptics = rememberHaptics()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    var quickLog by remember { mutableStateOf<QuickLogRequest?>(null) }
    val start = remember { if (settings.onboarded) Routes.DASHBOARD else Routes.ONBOARDING }
    val fade = if (settings.reduceMotion) 100 else 150
    val openLog: (List<String>) -> Unit = { ids -> quickLog = QuickLogRequest(ids) }

    LaunchedEffect(pendingRoute, settings.onboarded) {
        val r = pendingRoute ?: return@LaunchedEffect
        if (!settings.onboarded) return@LaunchedEffect
        runCatching { nav.navigate(r) { launchSingleTop = true } }
        onRouteConsumed()
    }

    CompositionLocalProvider(LocalSnackbar provides snackbar, LocalQuickLog provides openLog) {
        CausticBackground(animate = !settings.reduceMotion && route != Routes.LIVE) {
            NavHost(
                navController = nav,
                startDestination = start,
                modifier = Modifier.fillMaxSize(),
                enterTransition = { fadeIn(tween(fade)) },
                exitTransition = { fadeOut(tween(fade)) },
                popEnterTransition = { fadeIn(tween(fade)) },
                popExitTransition = { fadeOut(tween(fade)) },
            ) {
                composable(Routes.ONBOARDING) {
                    OnboardingRoute(
                        animate = !settings.reduceMotion,
                        onDone = {
                            nav.navigate(Routes.DASHBOARD) {
                                popUpTo(Routes.ONBOARDING) { inclusive = true }
                            }
                        },
                    )
                }
                reefGraph(nav)
            }

            val tab = Tab.forRoute(route)
            val showBar = Tab.isRoot(route)
            AnimatedVisibility(
                visible = showBar,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
            ) {
                BottomBar(
                    current = tab,
                    onSelect = {
                        haptics.tick()
                        if (it != tab) nav.navigateTab(it) else nav.popBackStack(it.route, inclusive = false)
                    },
                    modifier = Modifier.navigationBarsPadding(),
                )
            }
            AnimatedVisibility(
                visible = showBar && tab?.showsFab == true,
                modifier = Modifier.align(Alignment.BottomEnd),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                ReefFab(
                    onClick = {
                        haptics.confirm()
                        openLog(emptyList())
                    },
                    modifier = Modifier.navigationBarsPadding().padding(end = 24.dp, bottom = 96.dp),
                )
            }
            GlassSnackbarHost(
                snackbar,
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (showBar) 88.dp else 12.dp),
            )
        }
        quickLog?.let { req -> QuickLogHost(req, onDismiss = { quickLog = null }) }
    }
}

/** Every destination except onboarding. */
private fun androidx.navigation.NavGraphBuilder.reefGraph(nav: NavHostController) {
    val back: () -> Unit = { nav.popBackStack() }
    composable(Routes.DASHBOARD) { DashboardDestination(nav) }
    composable(Routes.LOG) { LogDestination(nav) }
    composable(Routes.PARAM, arguments = listOf(navArgument("paramId") { type = NavType.StringType })) { e ->
        ParamDestination(nav, e.arguments?.getString("paramId").orEmpty(), back)
    }
    composable(Routes.SESSION, arguments = listOf(navArgument("sessionId") { type = NavType.StringType })) { e ->
        SessionDestination(e.arguments?.getString("sessionId").orEmpty(), back)
    }
    composable(Routes.LIVE) {
        LiveTankScreen(onOpenLivestock = { id -> nav.navigate(Routes.livestock(id)) })
    }
    composable(Routes.CARE) { CareDestination(nav) }
    composable(Routes.TASK, arguments = listOf(navArgument("taskId") { type = NavType.StringType })) { e ->
        TaskDestination(e.arguments?.getString("taskId").orEmpty(), back)
    }
    composable(Routes.CALC, arguments = listOf(navArgument("calcId") { type = NavType.StringType })) { e ->
        CalcDestination(e.arguments?.getString("calcId").orEmpty(), back)
    }
    composable(Routes.MORE) { MoreDestination(nav) }
    composable(Routes.LIVESTOCK) { LivestockListDestination(nav, back) }
    composable(Routes.LIVESTOCK_DETAIL, arguments = listOf(navArgument("id") { type = NavType.StringType })) { e ->
        LivestockDetailDestination(nav, e.arguments?.getString("id").orEmpty(), back)
    }
    composable(Routes.LIVESTOCK_EDIT, arguments = listOf(navArgument("id") { type = NavType.StringType })) { e ->
        LivestockEditDestination(nav, e.arguments?.getString("id").orEmpty(), back)
    }
    composable(Routes.EQUIPMENT) { EquipmentListDestination(nav, back) }
    composable(Routes.EQUIPMENT_EDIT, arguments = listOf(navArgument("id") { type = NavType.StringType })) { e ->
        EquipmentEditDestination(e.arguments?.getString("id").orEmpty(), back)
    }
    composable(Routes.JOURNAL) { JournalListDestination(nav, back) }
    composable(Routes.JOURNAL_EDIT, arguments = listOf(navArgument("id") { type = NavType.StringType })) { e ->
        JournalEditDestination(e.arguments?.getString("id").orEmpty(), back)
    }
    composable(Routes.LIGHTS) { LightsDestination(back) }
    composable(Routes.TANK) { TankDestination(back) }
    composable(Routes.SETTINGS) { SettingsDestination(nav, back) }
    composable(Routes.TARGETS) { TargetsDestination(back) }
    composable(Routes.EXPORT) { ExportDestination(back) }
    composable(Routes.ABOUT) { AboutDestination(back) }
}
