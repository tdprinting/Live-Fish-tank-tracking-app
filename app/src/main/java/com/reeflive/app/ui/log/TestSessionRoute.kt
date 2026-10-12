package com.reeflive.app.ui.log

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
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
import com.reeflive.app.data.repo.SessionView
import com.reeflive.app.ui.components.DateTimeDialog
import com.reeflive.app.ui.components.EmptyState
import com.reeflive.app.ui.components.LocalSnackbar
import com.reeflive.app.ui.components.ReefDialog
import com.reeflive.app.ui.components.ScreenHeader
import com.reeflive.app.ui.components.rememberHaptics
import com.reeflive.app.ui.theme.ReefTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Edit one logged test (DESIGN §5.4 "tap a session"): same editor as Quick Log, plus delete with undo.
 * Loads the session once — edits replace its values on save.
 */
@Composable
fun TestSessionRoute(sessionId: String, onBack: () -> Unit) {
    val graph = LocalContext.current.appGraph
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val haptics = rememberHaptics()
    var loaded by remember { mutableStateOf(false) }
    var session by remember { mutableStateOf<SessionView?>(null) }
    LaunchedEffect(sessionId) {
        session = graph.measurements.session(sessionId).first()
        loaded = true
    }
    val s = session
    if (!loaded) return
    if (s == null) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Test", onBack = onBack)
            EmptyState("Test not found", "It may have been deleted.", icon = Icons.Rounded.SearchOff)
        }
        return
    }
    val ctl = remember(s.session.id) { QuickLogController(graph, scope, emptyList(), editing = s) }
    LaunchedEffect(ctl) { ctl.load() }
    var picking by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    QuickLogContent(
        state = ctl.ui,
        focusParam = ctl.focus,
        title = "Edit test",
        inSheet = false,
        headerActions = {
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, "Delete test", tint = ReefTheme.colors.dangerText) }
        },
        actions = QuickLogActions(
            onChip = ctl::toggle,
            onWeekly = ctl::weekly,
            onRemove = ctl::remove,
            onText = ctl::text,
            onCycleUnit = ctl::cycle,
            onPickTime = { picking = true },
            onKit = ctl::kit,
            onNote = ctl::note,
            onToggleNote = ctl::toggleNote,
            onSave = {
                haptics.confirm()
                ctl.save { msg ->
                    onBack()
                    graph.appScope.launch(Dispatchers.Main) { snackbar.showSnackbar(msg) }
                }
            },
            onClose = onBack,
        ),
    )
    if (picking) {
        DateTimeDialog(
            initial = ctl.ui.takenAt,
            onPick = {
                ctl.time(it)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
    if (confirmDelete) {
        ReefDialog(
            title = "Delete this test?",
            body = "All ${s.measurements.size} values from this test will be removed.",
            confirm = "Delete",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                graph.appScope.launch {
                    val snap = graph.measurements.deleteSession(s.session.id) ?: return@launch
                    graph.alertEvaluator.runNow(notify = false)
                    launch(Dispatchers.Main) {
                        val r = snackbar.showSnackbar("Test deleted", actionLabel = "Undo", duration = SnackbarDuration.Long)
                        if (r == SnackbarResult.ActionPerformed) {
                            graph.measurements.restore(snap)
                            graph.alertEvaluator.runNow(notify = false)
                        }
                    }
                }
                onBack()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
