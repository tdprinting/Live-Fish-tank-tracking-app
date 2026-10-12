package com.reeflive.app.ui.care

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.reeflive.app.appGraph
import com.reeflive.app.ui.components.EmptyState
import com.reeflive.app.ui.components.LocalSnackbar
import com.reeflive.app.ui.components.ScreenHeader
import com.reeflive.app.ui.components.rememberHaptics
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun CalculatorRoute(calcId: String, onBack: () -> Unit) {
    val g = LocalContext.current.appGraph
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val calc = Calc.of(calcId)
    var ctx by remember { mutableStateOf<CalcContext?>(null) }
    LaunchedEffect(Unit) {
        val s = g.settings.current()
        ctx = CalcContext(
            latest = g.measurements.latestValues().first(),
            defs = g.targets.effectiveOnce(includeHidden = true).associateBy { it.id },
            systemGallons = g.tank.get()?.systemGallons?.takeIf { it > 0 } ?: 60.0,
            tempUnit = s.tempUnit,
        )
    }
    if (calc == null) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Calculator", onBack = onBack)
            EmptyState("Unknown calculator", "This calculator doesn't exist.")
        }
        return
    }
    val c = ctx ?: return
    CalculatorContent(
        calc = calc,
        ctx = c,
        actions = CalcActions(
            onBack = onBack,
            onLogDose = { detail, title ->
                haptics.confirm()
                scope.launch {
                    val e = g.events.logDose(detail, title)
                    val r = snackbar.showSnackbar("$title · marked on the ${com.reeflive.core.params.ParameterTable.shortName(detail.paramId)} chart", actionLabel = "Undo")
                    if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) g.events.delete(e.id)
                }
            },
            onLogWaterChange = { gallons, fraction, mix ->
                haptics.confirm()
                scope.launch {
                    val e = g.events.logWaterChange(gallons, fraction, mix)
                    val r = snackbar.showSnackbar("Water change logged", actionLabel = "Undo")
                    if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) g.events.delete(e.id)
                }
            },
        ),
    )
}
