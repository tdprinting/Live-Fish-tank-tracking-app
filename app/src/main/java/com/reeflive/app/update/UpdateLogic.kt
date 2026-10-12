package com.reeflive.app.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** One downloadable file of a GitHub release. [apiUrl] is the assets API URL (works for private repos with a token). */
data class ReleaseAsset(val name: String, val apiUrl: String, val browserUrl: String, val size: Long)

data class ReleaseInfo(
    val tag: String,
    val name: String,
    val notes: String,
    val versionCode: Long,
    val asset: ReleaseAsset?,
    val htmlUrl: String,
) {
    /** "0.1.20261012.57" from tag "v0.1.20261012.57". */
    val versionName: String get() = tag.removePrefix("v")
}

/** Pure release parsing and version rules (no Android), unit-tested. */
object UpdateLogic {
    const val REPO = "tdprinting/Live-Fish-tank-tracking-app"
    const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"
    const val TOKEN_HELP_URL = "https://github.com/settings/personal-access-tokens/new"

    /** CI names the release-build asset this way on every release. */
    const val APK_NAME = "reeflive.apk"

    /** CI sets versionCode = GITHUB_RUN_NUMBER + RUN_OFFSET; the tag ends in the run number. */
    const val RUN_OFFSET = 100
    const val AUTO_INTERVAL_MS = 6 * 60 * 60 * 1000L

    private val json = Json { ignoreUnknownKeys = true }
    private val CODE_RE = Regex("""versionCode\s*[:=]\s*(\d+)""", RegexOption.IGNORE_CASE)
    private val TAG_RE = Regex("""^v?\d+\.\d+\.\d+\.(\d+)$""")

    /** Release body line `versionCode: N` wins; otherwise the run number at the end of the tag + [RUN_OFFSET]. */
    fun versionCodeOf(tag: String, body: String): Long? =
        CODE_RE.find(body)?.groupValues?.get(1)?.toLongOrNull()
            ?: TAG_RE.find(tag.trim())?.groupValues?.get(1)?.toLongOrNull()?.plus(RUN_OFFSET)

    /** Parses `GET /releases/latest`; null when it is not a usable release (no version code). */
    fun parse(text: String): ReleaseInfo? {
        val o = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        val tag = o.str("tag_name") ?: return null
        val body = o.str("body").orEmpty()
        val code = versionCodeOf(tag, body) ?: return null
        val assets = runCatching { o["assets"]?.jsonArray }.getOrNull().orEmpty().mapNotNull { e ->
            val a = runCatching { e.jsonObject }.getOrNull() ?: return@mapNotNull null
            val name = a.str("name") ?: return@mapNotNull null
            val url = a.str("url") ?: return@mapNotNull null
            ReleaseAsset(name, url, a.str("browser_download_url").orEmpty(), a["size"]?.jsonPrimitive?.longOrNull ?: -1L)
        }
        return ReleaseInfo(
            tag = tag,
            name = o.str("name") ?: tag,
            notes = cleanNotes(body),
            versionCode = code,
            asset = pickAsset(assets),
            htmlUrl = o.str("html_url").orEmpty(),
        )
    }

    /** [APK_NAME] first, then any non-debug APK, then any APK. */
    fun pickAsset(assets: List<ReleaseAsset>): ReleaseAsset? =
        assets.firstOrNull { it.name == APK_NAME }
            ?: assets.firstOrNull { it.name.endsWith(".apk") && !it.name.contains("debug") }
            ?: assets.firstOrNull { it.name.endsWith(".apk") }

    fun isNewer(release: ReleaseInfo, installedCode: Long): Boolean = release.versionCode > installedCode

    /** At most one automatic check per [AUTO_INTERVAL_MS]; a clock that went backwards allows a check. */
    fun dueForAutoCheck(lastCheckMs: Long, nowMs: Long): Boolean =
        lastCheckMs <= 0L || nowMs < lastCheckMs || nowMs - lastCheckMs >= AUTO_INTERVAL_MS

    /** Drops the machine line and light markdown so notes read as plain text. */
    fun cleanNotes(body: String): String = body.lines()
        .filterNot { CODE_RE.containsMatchIn(it) }
        .map { line ->
            line.trimEnd()
                .replace(Regex("""^#+\s*"""), "")
                .replace(Regex("""^\s*[*-]\s+"""), "• ")
                .replace("**", "")
                .replace(Regex("""\[([^\]]+)]\([^)]+\)"""), "$1")
        }
        .joinToString("\n")
        .replace(Regex("""\n{3,}"""), "\n\n")
        .trim()

    /** Size check after download: the file must match the size GitHub reports (when it reports one). */
    fun sizeMatches(expected: Long, actual: Long): Boolean = actual > 0 && (expected <= 0 || expected == actual)

    private fun JsonObject.str(key: String): String? = runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()
}
