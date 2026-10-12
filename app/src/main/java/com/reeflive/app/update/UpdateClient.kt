package com.reeflive.app.update

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** A failure the user should read as-is. */
class UpdateException(message: String) : IOException(message)

/**
 * GitHub Releases over plain [HttpURLConnection] (no extra dependencies). The repository is public, so no token is
 * needed; an optional token (Updates → Advanced) is sent as `Authorization: Bearer` for a private fork or rate limits.
 */
class UpdateClient(private val token: () -> String?) {

    fun fetchLatest(): ReleaseInfo {
        val conn = open(UpdateLogic.LATEST_URL, "application/vnd.github+json", withToken = true)
        try {
            val code = conn.responseCode
            if (code != 200) throw UpdateException(describe(code, conn))
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            return UpdateLogic.parse(text) ?: throw UpdateException("The latest release has no version number yet.")
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Downloads [asset] into [dest] through the assets API (`Accept: application/octet-stream`). GitHub answers with
     * a redirect to a signed storage URL; it is followed by hand so the token is never sent to another host.
     */
    fun download(asset: ReleaseAsset, dest: File, onProgress: (read: Long, total: Long) -> Unit) {
        dest.parentFile?.mkdirs()
        val part = File(dest.parentFile, dest.name + ".part")
        var url = asset.apiUrl
        var auth = true
        var hops = 0
        while (true) {
            val conn = open(url, "application/octet-stream", withToken = auth)
            conn.instanceFollowRedirects = false
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val next = conn.getHeaderField("Location") ?: throw UpdateException("Download redirect without a location.")
                    if (++hops > 5) throw UpdateException("Too many redirects.")
                    val nextUrl = URL(URL(url), next)
                    if (nextUrl.host != URL(url).host) auth = false
                    url = nextUrl.toString()
                    continue
                }
                if (code != 200) throw UpdateException(describe(code, conn))
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: asset.size
                var read = 0L
                var lastReport = 0L
                conn.inputStream.use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            if (Thread.currentThread().isInterrupted) throw UpdateException("Download cancelled.")
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            read += n
                            if (read - lastReport >= 128 * 1024) {
                                lastReport = read
                                onProgress(read, total)
                            }
                        }
                    }
                }
                onProgress(read, total)
                if (!UpdateLogic.sizeMatches(asset.size, part.length())) {
                    part.delete()
                    throw UpdateException("The download was incomplete (${part.length()} of ${asset.size} bytes). Try again.")
                }
                if (dest.exists()) dest.delete()
                if (!part.renameTo(dest)) throw UpdateException("Could not save the update file.")
                return
            } finally {
                conn.disconnect()
            }
        }
    }

    private fun open(url: String, accept: String, withToken: Boolean): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("Accept", accept)
        conn.setRequestProperty("User-Agent", "ReefLive-Android")
        conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        val t = token()?.trim()
        if (withToken && !t.isNullOrEmpty()) conn.setRequestProperty("Authorization", "Bearer $t")
        return conn
    }

    private fun describe(code: Int, conn: HttpURLConnection): String {
        val hasToken = !token().isNullOrBlank()
        return when {
            code == 401 -> "GitHub rejected the access token. Fix or remove it under Updates → Advanced."
            code == 404 && !hasToken -> "No release has been published yet. (If the repository is private, add an access token under Updates → Advanced.)"
            code == 404 -> "No release found. Check that the token can read the ReefLive repository (Contents: Read)."
            code == 403 && conn.getHeaderField("X-RateLimit-Remaining") == "0" -> "GitHub rate limit reached. Try again in an hour."
            code == 403 -> "GitHub refused the request (403). Check the token's repository access."
            else -> "GitHub answered $code. Try again later."
        }
    }
}
