package com.reeflive.app.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.ui.graphics.vector.ImageVector

/** Navigation routes (DESIGN §3). */
object Routes {
    const val ONBOARDING = "onboarding"
    const val DASHBOARD = "dashboard"
    const val LOG = "log"
    const val LIVE = "live"
    const val CARE = "care"
    const val MORE = "more"

    const val PARAM = "log/{paramId}"
    fun param(id: String) = "log/$id"

    const val SESSION = "log/session/{sessionId}"
    fun session(id: String) = "log/session/$id"

    const val TASK = "care/task/{taskId}"
    fun task(id: String) = "care/task/$id"
    const val NEW = "new"

    const val CALC = "care/calc/{calcId}"
    fun calc(id: String) = "care/calc/$id"

    const val LIVESTOCK = "more/livestock"
    const val LIVESTOCK_DETAIL = "more/livestock/{id}"
    fun livestock(id: String) = "more/livestock/$id"
    const val LIVESTOCK_EDIT = "more/livestock/edit/{id}"
    fun livestockEdit(id: String) = "more/livestock/edit/$id"

    const val EQUIPMENT = "more/equipment"
    const val EQUIPMENT_EDIT = "more/equipment/{id}"
    fun equipment(id: String) = "more/equipment/$id"

    const val JOURNAL = "more/journal"
    const val JOURNAL_EDIT = "more/journal/{id}"
    fun journal(id: String) = "more/journal/$id"

    const val LIGHTS = "more/lights"
    const val TANK = "more/tank"
    const val SETTINGS = "more/settings"
    const val TARGETS = "more/settings/targets"
    const val EXPORT = "more/export"
    const val ABOUT = "more/about"
    const val UPDATES = "more/settings/updates"
    const val INSIGHTS = "more/insights"
    const val SUPPLIES = "more/supplies"
    const val FEEDING = "more/feeding"
    const val TREATMENTS = "more/treatments"
    const val EMERGENCY = "more/emergency"
    const val ICP = "more/icp"
    const val CORALS = "more/corals"
    const val PAR = "more/par"
    const val SPENDING = "more/spending"
}

/** The five bottom-bar destinations. */
enum class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    DASHBOARD(Routes.DASHBOARD, "Reef", Icons.Outlined.WaterDrop, Icons.Rounded.WaterDrop),
    LOG(Routes.LOG, "Log", Icons.Outlined.Insights, Icons.Rounded.Insights),
    LIVE(Routes.LIVE, "Live", Icons.Outlined.Waves, Icons.Rounded.Waves),
    CARE(Routes.CARE, "Care", Icons.Outlined.TaskAlt, Icons.Rounded.TaskAlt),
    MORE(Routes.MORE, "More", Icons.Outlined.GridView, Icons.Rounded.GridView);

    val showsFab: Boolean get() = this == DASHBOARD || this == LOG || this == CARE

    companion object {
        /** The tab owning [route] (by its first path segment). */
        fun forRoute(route: String?): Tab? {
            val head = route?.substringBefore('/') ?: return null
            return entries.firstOrNull { it.route == head }
        }

        fun isRoot(route: String?): Boolean = entries.any { it.route == route }
    }
}
