package com.reeflive.app.ui.more

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.reeflive.app.appGraph
import com.reeflive.app.data.export.BackupManager
import com.reeflive.app.ui.common.TimeText
import com.reeflive.app.ui.components.ButtonKind
import com.reeflive.app.ui.components.FactRow
import com.reeflive.app.ui.components.LocalSnackbar
import com.reeflive.app.ui.components.ReefButton
import com.reeflive.app.ui.components.ReefDialog
import com.reeflive.app.ui.components.ScreenHeader
import com.reeflive.app.ui.components.pagePadding
import com.reeflive.app.ui.theme.GlassCard
import com.reeflive.app.ui.theme.ReefShapes
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.core.io.CsvCodec
import com.reeflive.core.io.CsvImportResult
import com.reeflive.core.params.ParameterTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId

/** What the user sees before confirming a CSV import. */
data class ImportPreview(
    val rows: Int,
    val sessions: Int,
    val params: String,
    val span: String,
    val errors: List<String>,
    val result: CsvImportResult,
)

data class ExportUi(val tests: Int = 0, val busy: Boolean = false)

data class ExportActions(
    val onBack: () -> Unit = {},
    val onExportCsv: () -> Unit = {},
    val onImportCsv: () -> Unit = {},
    val onBackup: () -> Unit = {},
    val onRestore: () -> Unit = {},
)

/** Export & backup (DESIGN §5.10, §6.7). */
@Composable
fun ExportContent(state: ExportUi, actions: ExportActions, modifier: Modifier = Modifier) {
    val c = ReefTheme.colors
    Column(modifier.fillMaxSize()) {
        ScreenHeader("Export & backup", onBack = actions.onBack, subtitle = "${state.tests} tests stored on this phone")
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(pagePadding()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ExportCard(
                icon = Icons.Rounded.TableChart,
                title = "Measurements (CSV)",
                body = "Every test value with time, unit and kit. Opens in any spreadsheet. Import accepts the same columns and common variations.",
            ) {
                ReefButton("Export CSV", actions.onExportCsv, kind = ButtonKind.TONAL, icon = Icons.Rounded.FileDownload, compact = true, enabled = !state.busy)
                ReefButton("Import CSV", actions.onImportCsv, kind = ButtonKind.OUTLINED, icon = Icons.Rounded.FileUpload, compact = true, enabled = !state.busy)
            }
            ExportCard(
                icon = Icons.Rounded.Backup,
                title = "Full backup (JSON)",
                body = "Tank, tests, targets, tasks, livestock, equipment, journal and lighting in one file. Photos are not included.",
            ) {
                ReefButton("Back up", actions.onBackup, kind = ButtonKind.TONAL, icon = Icons.Rounded.FileDownload, compact = true, enabled = !state.busy)
                ReefButton("Restore", actions.onRestore, kind = ButtonKind.OUTLINED, icon = Icons.Rounded.Restore, compact = true, enabled = !state.busy)
            }
            Text(
                "Files are saved wherever you choose — Drive, Downloads or another app. ReefLive never uploads anything.",
                style = MaterialTheme.typography.bodySmall, color = c.textTertiary, modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun ExportCard(icon: ImageVector, title: String, body: String, buttons: @Composable () -> Unit) {
    val c = ReefTheme.colors
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).padding(0.dp), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = c.primaryBright, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.text)
        }
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { buttons() }
    }
}

object ImportPreviewer {
    fun preview(text: String, zone: ZoneId = ZoneId.systemDefault()): ImportPreview {
        val r = CsvCodec.import(text, zone)
        val sessions = r.rows.map { it.sessionId ?: "t${it.takenAt}|${it.kit.orEmpty()}" }.distinct().size
        val params = r.rows.groupingBy { it.paramId }.eachCount().entries.sortedByDescending { it.value }
            .joinToString(", ") { "${ParameterTable.shortName(it.key)} ${it.value}" }
        val span = if (r.rows.isEmpty()) "—" else {
            val a = r.rows.minOf { it.takenAt }
            val b = r.rows.maxOf { it.takenAt }
            "${TimeText.shortDate(a)} – ${TimeText.shortDate(b)}"
        }
        return ImportPreview(r.rows.size, sessions, params.ifEmpty { "—" }, span, r.errors, r)
    }
}

@Composable
fun ExportRoute(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val g = ctx.appGraph
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    var tests by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<ImportPreview?>(null) }
    var restoreText by remember { mutableStateOf<String?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }

    suspend fun refresh() { tests = g.measurements.totalCount() }
    LaunchedEffect(Unit) { refresh() }

    fun run(block: suspend () -> String) {
        busy = true
        scope.launch {
            val msg = runCatching { block() }.getOrElse { "Something went wrong: ${it.message ?: it.javaClass.simpleName}" }
            busy = false
            refresh()
            snackbar.showSnackbar(msg)
        }
    }

    suspend fun write(uri: android.net.Uri, text: String) = withContext(Dispatchers.IO) {
        val out = ctx.contentResolver.openOutputStream(uri, "wt") ?: error("Couldn't open the file")
        out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    suspend fun read(uri: android.net.Uri): String = withContext(Dispatchers.IO) {
        val input = ctx.contentResolver.openInputStream(uri) ?: error("Couldn't open the file")
        input.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    val csvOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) run {
            write(uri, g.measurements.exportCsv())
            "Exported $tests tests"
        }
    }
    val jsonOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) run {
            write(uri, g.backup.exportJson())
            "Backup saved"
        }
    }
    val csvIn = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                runCatching { read(uri) }
                    .mapCatching { withContext(Dispatchers.Default) { ImportPreviewer.preview(it) } }
                    .onSuccess { preview = it }
                    .onFailure { snackbar.showSnackbar("Couldn't read that file") }
                busy = false
            }
        }
    }
    val jsonIn = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching { read(uri) }
                    .onSuccess { restoreText = it; confirmRestore = true }
                    .onFailure { snackbar.showSnackbar("Couldn't read that file") }
            }
        }
    }

    ExportContent(
        state = ExportUi(tests, busy),
        actions = ExportActions(
            onBack = onBack,
            onExportCsv = { csvOut.launch(BackupManager.csvName(System.currentTimeMillis())) },
            onImportCsv = { csvIn.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream")) },
            onBackup = { jsonOut.launch(BackupManager.backupName(System.currentTimeMillis())) },
            onRestore = { jsonIn.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
        ),
    )

    preview?.let { p ->
        val c = ReefTheme.colors
        AlertDialog(
            onDismissRequest = { preview = null },
            containerColor = c.sheet,
            shape = ReefShapes.dialog,
            title = { Text("Import ${p.rows} values?", style = MaterialTheme.typography.headlineSmall, color = c.text) },
            text = {
                Column {
                    FactRow("Tests", "${p.sessions}")
                    FactRow("Dates", p.span)
                    FactRow("Parameters", p.params)
                    if (p.errors.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("${p.errors.size} line(s) skipped", style = MaterialTheme.typography.labelLarge, color = c.cautionText)
                        p.errors.take(4).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = c.textSecondary) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Values already in ReefLive are kept; duplicates are skipped.", style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
                }
            },
            confirmButton = {
                ReefButton("Import", {
                    preview = null
                    run {
                        val s = g.measurements.importRows(p.result.rows)
                        g.alertEvaluator.runNow(notify = false)
                        "Imported ${s.inserted} values in ${s.sessions} tests" + if (s.skipped > 0) " · ${s.skipped} skipped" else ""
                    }
                }, enabled = p.rows > 0, compact = true)
            },
            dismissButton = { ReefButton("Cancel", { preview = null }, kind = ButtonKind.OUTLINED, compact = true) },
        )
    }

    if (confirmRestore) {
        ReefDialog(
            title = "Replace everything?",
            body = "Restoring a backup replaces all tests, tasks, livestock and settings for the tank with the backup's contents. This can't be undone.",
            confirm = "Restore",
            destructive = true,
            onConfirm = {
                confirmRestore = false
                val text = restoreText ?: return@ReefDialog
                restoreText = null
                run {
                    val b = g.backup.restoreJson(text)
                    g.reminders.rescheduleAll()
                    g.alertEvaluator.runNow(notify = false)
                    "Restored ${b.measurements.size} values, ${b.tasks.size} tasks, ${b.livestock.size} livestock"
                }
            },
            onDismiss = {
                confirmRestore = false
                restoreText = null
            },
        )
    }
}
