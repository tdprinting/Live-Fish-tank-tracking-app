package com.reeflive.app.update

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val checkedAt: Long) : UpdateState
    data class Available(val release: ReleaseInfo) : UpdateState
    data class Downloading(val release: ReleaseInfo, val read: Long, val total: Long) : UpdateState {
        val fraction: Float get() = if (total > 0) (read.toFloat() / total).coerceIn(0f, 1f) else 0f
    }
    data class Ready(val release: ReleaseInfo, val file: File) : UpdateState
    data class Failed(val message: String, val release: ReleaseInfo? = null) : UpdateState
}

/**
 * In-app updater: checks GitHub Releases on launch (at most every 6 h, when enabled) or on demand, downloads the APK
 * into the cache and hands it to the system installer. The same applicationId and signing key on every build mean
 * each update installs over the previous one and keeps all data.
 */
class UpdateManager(private val app: Application, private val scope: CoroutineScope) {
    val prefs = UpdatePrefs(app)
    private val client = UpdateClient { prefs.token() }

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Whether the "Update available" sheet is showing. */
    private val _sheet = MutableStateFlow(false)
    val sheetVisible: StateFlow<Boolean> = _sheet.asStateFlow()

    private var job: Job? = null

    val installedCode: Long by lazy {
        runCatching { PackageInfoCompat.getLongVersionCode(app.packageManager.getPackageInfo(app.packageName, 0)) }.getOrDefault(1L)
    }
    val installedName: String by lazy {
        runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull() ?: "0.1.0"
    }

    private val dir: File get() = File(app.cacheDir, "updates")

    /** Launch hook: honours the toggle and the 6 h spacing; quietly ignores failures. */
    fun checkOnLaunch() {
        cleanOldDownloads()
        if (!prefs.autoCheck) return
        if (!UpdateLogic.dueForAutoCheck(prefs.lastCheckMs, System.currentTimeMillis())) return
        check(manual = false)
    }

    /** Manual checks always show the result; automatic ones only pop the sheet for a new, not-dismissed version. */
    fun check(manual: Boolean) {
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = UpdateState.Checking
            val result = runCatching { client.fetchLatest() }
            prefs.lastCheckMs = System.currentTimeMillis()
            val release = result.getOrElse { e ->
                _state.value = if (manual) UpdateState.Failed(e.message ?: "Could not reach GitHub.") else UpdateState.Idle
                return@launch
            }
            if (!UpdateLogic.isNewer(release, installedCode)) {
                _state.value = UpdateState.UpToDate(System.currentTimeMillis())
                return@launch
            }
            val cached = apkFile(release)
            _state.value = if (cached.exists() && release.asset != null && UpdateLogic.sizeMatches(release.asset.size, cached.length())) {
                UpdateState.Ready(release, cached)
            } else {
                UpdateState.Available(release)
            }
            if (manual || release.versionCode != prefs.dismissedCode) _sheet.value = true
        }
    }

    fun download() {
        val release = when (val s = _state.value) {
            is UpdateState.Available -> s.release
            is UpdateState.Failed -> s.release
            else -> null
        } ?: return
        val asset = release.asset
        if (asset == null) {
            _state.value = UpdateState.Failed("This release has no APK attached.", release)
            return
        }
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = UpdateState.Downloading(release, 0, asset.size)
            val file = apkFile(release)
            val ok = runCatching {
                client.download(asset, file) { read, total -> _state.value = UpdateState.Downloading(release, read, total) }
            }
            _state.value = ok.fold(
                onSuccess = { UpdateState.Ready(release, file) },
                onFailure = { UpdateState.Failed(it.message ?: "Download failed.", release) },
            )
        }
    }

    fun showSheet() {
        if (_state.value.let { it is UpdateState.Available || it is UpdateState.Downloading || it is UpdateState.Ready }) _sheet.value = true
    }

    /** "Later": hides the sheet; automatic checks stay quiet about this version until a newer one appears. */
    fun dismiss() {
        _sheet.value = false
        when (val s = _state.value) {
            is UpdateState.Available -> prefs.dismissedCode = s.release.versionCode
            is UpdateState.Ready -> prefs.dismissedCode = s.release.versionCode
            else -> Unit
        }
    }

    fun setToken(token: String?) = prefs.setToken(token)

    fun setAutoCheck(on: Boolean) {
        prefs.autoCheck = on
    }

    // ------------------------------------------------------------- install

    /** Android 8+ asks the user once to allow installs from ReefLive ("Install unknown apps"). */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    /** System installer for the downloaded APK; it installs over the current app and keeps its data. */
    fun installIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun apkFile(release: ReleaseInfo) = File(dir, "reeflive-${release.versionCode}.apk")

    /** Removes APKs for versions that are installed already. */
    private fun cleanOldDownloads() {
        scope.launch {
            dir.listFiles()?.forEach { f ->
                val code = Regex("""reeflive-(\d+)\.apk""").find(f.name)?.groupValues?.get(1)?.toLongOrNull()
                if (code == null || code <= installedCode) f.delete()
            }
        }
    }
}
