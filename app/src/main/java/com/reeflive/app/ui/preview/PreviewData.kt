package com.reeflive.app.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.reeflive.app.data.repo.AppSettings
import com.reeflive.app.data.repo.ParamEval
import com.reeflive.app.ui.common.ParamView
import com.reeflive.app.ui.common.UiMappers
import com.reeflive.app.ui.dashboard.DashboardActions
import com.reeflive.app.ui.dashboard.DashboardContent
import com.reeflive.app.ui.dashboard.DashboardMapper
import com.reeflive.app.ui.log.LogActions
import com.reeflive.app.ui.log.LogUi
import com.reeflive.app.ui.log.ParametersContent
import com.reeflive.app.ui.theme.CausticBackground
import com.reeflive.app.ui.theme.ReefLiveTheme
import com.reeflive.core.model.Tank
import com.reeflive.core.params.ParameterTable
import com.reeflive.core.status.Reading
import com.reeflive.core.status.StatusEngine
import java.time.ZoneId
import kotlin.math.sin

/** Deterministic sample data for Android Studio previews (both themes). */
object PreviewData {
    const val NOW = 1_790_000_000_000L
    private const val DAY = 86_400_000L

    private val centers = mapOf(
        "temp" to 77.5, "sal" to 1.025, "alk" to 8.4, "ca" to 420.0, "mg" to 1320.0, "ph" to 8.1, "no3" to 6.0, "po4" to 0.06,
    )

    val evals: List<ParamEval> by lazy {
        ParameterTable.SOFT_CORAL.map { def ->
            val c = centers[def.id]
            val h = if (c == null) emptyList() else (0 until 10).map { i ->
                val wobble = sin(i * 1.3 + def.order) * def.acceptable.span * 0.08
                val drift = if (def.id == "alk") -i * 0.04 else 0.0
                Reading(def.id, c + wobble - drift, NOW - (9 - i) * 3 * DAY)
            }
            ParamEval(def, StatusEngine.evaluate(def, h, NOW), h)
        }
    }

    val tank = Tank("tank-main", "Living room reef", 91.0, 46.0, 50.0, 55.0, 10.0)

    val dashboard by lazy {
        DashboardMapper.build(NOW, ZoneId.of("UTC"), tank, evals, AppSettings(loaded = true, onboarded = true), emptyList(), emptyList(), emptyList(), emptyList(), null, null)
    }

    val log by lazy {
        LogUi(
            loading = false,
            tiles = evals.map { UiMappers.tile(ParamView(it.def, it.def.baseUnit), it.eval, it.history, NOW) },
        )
    }
}

@Composable
private fun Frame(dark: Boolean, content: @Composable () -> Unit) {
    ReefLiveTheme(darkTheme = dark, reduceMotion = true) {
        CausticBackground(animate = false) { content() }
    }
}

@Preview(name = "Dashboard · Abyss", widthDp = 393, heightDp = 852)
@Composable
private fun DashboardDarkPreview() = Frame(true) { DashboardContent(PreviewData.dashboard, DashboardActions()) }

@Preview(name = "Dashboard · Lagoon", widthDp = 393, heightDp = 852)
@Composable
private fun DashboardLightPreview() = Frame(false) { DashboardContent(PreviewData.dashboard, DashboardActions()) }

@Preview(name = "Log · Abyss", widthDp = 393, heightDp = 852)
@Composable
private fun LogDarkPreview() = Frame(true) { ParametersContent(PreviewData.log, LogActions()) }

@Preview(name = "Log · Lagoon", widthDp = 393, heightDp = 852)
@Composable
private fun LogLightPreview() = Frame(false) { ParametersContent(PreviewData.log, LogActions()) }
