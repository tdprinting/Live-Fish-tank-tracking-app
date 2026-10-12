package com.reeflive.app.update

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reeflive.app.appGraph
import com.reeflive.app.ui.components.ButtonKind
import com.reeflive.app.ui.components.ReefButton
import com.reeflive.app.ui.components.ReefSheet
import com.reeflive.app.ui.components.ReefTextField
import com.reeflive.app.ui.components.ScreenHeader
import com.reeflive.app.ui.components.SectionHeader
import com.reeflive.app.ui.components.pagePadding
import com.reeflive.app.ui.more.LinkRow
import com.reeflive.app.ui.more.SwitchRow
import com.reeflive.app.ui.theme.GlassCard
import com.reeflive.app.ui.theme.ReefShapes
import com.reeflive.app.ui.theme.ReefTheme
import java.io.File

/** Human size: "12.4 MB". */
internal fun mb(bytes: Long): String = if (bytes <= 0) "?" else "%.1f MB".format(bytes / 1_048_576.0)

/** Glass progress bar in the lagoon gradient. */
@Composable
internal fun UpdateProgress(fraction: Float, modifier: Modifier = Modifier) {
    val c = ReefTheme.colors
    Box(modifier.fillMaxWidth().height(8.dp).background(c.glass, ReefShapes.pill)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(c.primaryBrush, ReefShapes.pill))
    }
}

/**
 * Starts the system installer, first sending the user to "Install unknown apps" for ReefLive when Android needs that
 * permission; returning from Settings continues the install.
 */
@Composable
internal fun rememberInstaller(onError: (String) -> Unit): (File) -> Unit {
    val ctx = LocalContext.current
    val updates = ctx.appGraph.updates
    var pending by remember { mutableStateOf<File?>(null) }
    fun launch(file: File) {
        try {
            ctx.startActivity(updates.installIntent(ctx, file))
        } catch (e: ActivityNotFoundException) {
            onError("No installer found on this phone.")
        } catch (e: IllegalArgumentException) {
            onError("The update file is not available any more. Download it again.")
        }
    }
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val f = pending
        pending = null
        if (f != null) {
            if (updates.canInstall(ctx)) launch(f) else onError("Allow \"Install unknown apps\" for ReefLive to install updates.")
        }
    }
    return { file ->
        if (updates.canInstall(ctx)) {
            launch(file)
        } else {
            pending = file
            try {
                settings.launch(updates.unknownSourcesIntent(ctx))
            } catch (e: ActivityNotFoundException) {
                pending = null
                onError("Open Settings → Apps → ReefLive → Install unknown apps and allow it.")
            }
        }
    }
}

/** App-wide host: runs the launch check once and shows the "Update available" sheet. */
@Composable
fun UpdateSheetHost() {
    val updates = LocalContext.current.appGraph.updates
    LaunchedEffect(Unit) { updates.checkOnLaunch() }
    val visible by updates.sheetVisible.collectAsStateWithLifecycle()
    if (visible) UpdateSheet(updates)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdateSheet(updates: UpdateManager) {
    val c = ReefTheme.colors
    val state by updates.state.collectAsStateWithLifecycle()
    var error by remember { mutableStateOf<String?>(null) }
    val install = rememberInstaller { error = it }
    val release = when (val s = state) {
        is UpdateState.Available -> s.release
        is UpdateState.Downloading -> s.release
        is UpdateState.Ready -> s.release
        is UpdateState.Failed -> s.release
        else -> null
    }
    ReefSheet(onDismiss = { updates.dismiss() }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding()) {
            Text("Update available", style = MaterialTheme.typography.headlineSmall, color = c.text)
            if (release != null) {
                Text(
                    "Version ${release.versionName} · you have ${updates.installedName}",
                    style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
                )
                Spacer(Modifier.height(12.dp))
                GlassCard(Modifier.fillMaxWidth().heightIn(max = 260.dp), tint = c.primary) {
                    Text("What's new", style = MaterialTheme.typography.titleSmall, color = c.text)
                    Spacer(Modifier.height(6.dp))
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            release.notes.ifBlank { "Fixes and improvements." },
                            style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Installs over this version and keeps all your tests, tasks, livestock and photos.",
                    style = MaterialTheme.typography.bodySmall, color = c.textTertiary,
                )
            }
            Spacer(Modifier.height(14.dp))
            when (val s = state) {
                is UpdateState.Downloading -> {
                    UpdateProgress(s.fraction)
                    Spacer(Modifier.height(6.dp))
                    Text("Downloading… ${mb(s.read)} of ${mb(s.total)}", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                }
                is UpdateState.Ready -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReefButton("Later", { updates.dismiss() }, kind = ButtonKind.OUTLINED)
                    ReefButton("Install", { install(s.file) }, Modifier.weight(1f), icon = Icons.Rounded.InstallMobile)
                }
                is UpdateState.Failed -> {
                    Text(s.message, style = MaterialTheme.typography.bodySmall, color = c.dangerText)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReefButton("Later", { updates.dismiss() }, kind = ButtonKind.OUTLINED)
                        ReefButton("Try again", { updates.download() }, Modifier.weight(1f), icon = Icons.Rounded.Refresh, enabled = s.release != null)
                    }
                }
                else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReefButton("Later", { updates.dismiss() }, kind = ButtonKind.OUTLINED)
                    val size = release?.asset?.size ?: -1L
                    ReefButton(if (size > 0) "Download · ${mb(size)}" else "Download", { updates.download() }, Modifier.weight(1f), icon = Icons.Rounded.Download)
                }
            }
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = c.cautionText)
            }
        }
    }
}

/** Settings → Updates: version, manual check, auto-check toggle and (under Advanced) an optional GitHub token. */
@Composable
fun UpdatesRoute(onBack: () -> Unit) {
    val c = ReefTheme.colors
    val updates = LocalContext.current.appGraph.updates
    val state by updates.state.collectAsStateWithLifecycle()
    var auto by remember { mutableStateOf(updates.prefs.autoCheck) }
    var hasToken by remember { mutableStateOf(updates.prefs.hasToken) }
    var tokenInput by remember { mutableStateOf("") }
    var showAdvanced by remember { mutableStateOf(hasToken) }
    var error by remember { mutableStateOf<String?>(null) }
    val install = rememberInstaller { error = it }
    val uri = LocalUriHandler.current

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Updates", onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(pagePadding(top = 0.dp))) {
            GlassCard(Modifier.fillMaxWidth(), tint = c.primary) {
                Text("ReefLive ${updates.installedName}", style = MaterialTheme.typography.titleMedium, color = c.text)
                Text("Build ${updates.installedCode}", style = MaterialTheme.typography.labelMedium, color = c.textTertiary)
                Spacer(Modifier.height(8.dp))
                val status = when (val s = state) {
                    UpdateState.Idle -> "Updates come from the ReefLive releases on GitHub."
                    UpdateState.Checking -> "Checking…"
                    is UpdateState.UpToDate -> "You're up to date."
                    is UpdateState.Available -> "Version ${s.release.versionName} is available."
                    is UpdateState.Downloading -> "Downloading ${s.release.versionName}… ${(s.fraction * 100).toInt()} %"
                    is UpdateState.Ready -> "Version ${s.release.versionName} is downloaded and ready to install."
                    is UpdateState.Failed -> s.message
                }
                Text(status, style = MaterialTheme.typography.bodyMedium, color = if (state is UpdateState.Failed) c.dangerText else c.textSecondary)
                val s = state
                if (s is UpdateState.Downloading) {
                    Spacer(Modifier.height(8.dp))
                    UpdateProgress(s.fraction)
                }
                Spacer(Modifier.height(12.dp))
                when (s) {
                    is UpdateState.Available -> ReefButton("See what's new", { updates.showSheet() }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Download)
                    is UpdateState.Ready -> ReefButton("Install update", { install(s.file) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.InstallMobile)
                    else -> ReefButton(
                        "Check for updates", { updates.check(manual = true) }, Modifier.fillMaxWidth(),
                        icon = Icons.Rounded.Refresh, enabled = s !is UpdateState.Checking && s !is UpdateState.Downloading,
                    )
                }
                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = c.cautionText)
                }
            }
            SectionHeader("Automatic")
            GlassCard(Modifier.fillMaxWidth()) {
                SwitchRow("Automatically check for updates", "When ReefLive opens, at most every 6 hours", auto) {
                    auto = it
                    updates.setAutoCheck(it)
                }
            }
            SectionHeader("Advanced")
            if (!showAdvanced) {
                LinkRow("GitHub access token", "Optional. Only needed if the releases are private", onClick = { showAdvanced = true })
            } else {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("GitHub access token", style = MaterialTheme.typography.titleSmall, color = c.text)
                    Text(
                        "Optional. ReefLive's releases are public, so updates work without one. Add a token only if the " +
                            "repository becomes private or GitHub rate-limits you.\n\n" +
                            "To make one: GitHub → Settings → Developer settings → Fine-grained tokens → Generate new token. " +
                            "Repository access: only ${UpdateLogic.REPO}. Permissions: Contents → Read-only. Paste it below.",
                        style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                    )
                    Spacer(Modifier.height(10.dp))
                    ReefTextField(
                        tokenInput, { tokenInput = it.trim() }, Modifier.fillMaxWidth(),
                        label = "Token",
                        placeholder = if (hasToken) "Saved (hidden) — paste to replace" else "github_pat_…",
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReefButton("Save", {
                            updates.setToken(tokenInput)
                            hasToken = updates.prefs.hasToken
                            tokenInput = ""
                        }, compact = true, enabled = tokenInput.isNotBlank())
                        if (hasToken) {
                            ReefButton("Remove", {
                                updates.setToken(null)
                                hasToken = false
                            }, kind = ButtonKind.DESTRUCTIVE, compact = true)
                        }
                        ReefButton("Create token", { runCatching { uri.openUri(UpdateLogic.TOKEN_HELP_URL) } }, kind = ButtonKind.OUTLINED, compact = true)
                    }
                    if (hasToken) {
                        Spacer(Modifier.height(6.dp))
                        Text("A token is saved, encrypted on this phone.", style = MaterialTheme.typography.labelMedium, color = c.goodText)
                    }
                }
            }
        }
    }
}
