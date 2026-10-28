package com.reeflive.web

import kotlinx.coroutines.await
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import kotlin.js.Promise

/**
 * Downloads into [kotlin.Resources] (the classpath and the app's assets in the browser). Uses the global fetch and
 * timers, so it works on the page and in a worker alike.
 */
@OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
internal object Net {
    /** Where `assets/` and `res/` are served (ends in '/'). */
    var base = ""
    private val global: dynamic get() = js("globalThis")

    suspend fun fetchJson(path: String): dynamic {
        kotlin.Resources.get(resKey(path))?.let { return JSON.parse<dynamic>(it.decodeToString()) }
        val r: dynamic = global.fetch(base + path).unsafeCast<Promise<dynamic>>().await()
        if (r.ok != true) throw IllegalStateException("Missing $path (${r.status})")
        return r.json().unsafeCast<Promise<dynamic>>().await()
    }

    /**
     * [path]'s bytes, or null when it is missing. On the page, its loader (index.html `reefFetch`) may already be
     * downloading it, and takes a model or the rock field gzipped where the browser can unpack it; a worker fetches.
     */
    private suspend fun download(path: String, signal: dynamic = null): ArrayBuffer? {
        val pageFetch = global.reefFetch
        if (pageFetch != null && base == js("new URL('.', globalThis.location.href).href")) {
            return (pageFetch(path, signal) as Promise<ArrayBuffer?>).await()
        }
        val init: dynamic = js("({})")
        if (signal != null) init.signal = signal
        val r: dynamic = global.fetch(base + path, init).unsafeCast<Promise<dynamic>>().await()
        return if (r.ok == true) r.arrayBuffer().unsafeCast<Promise<ArrayBuffer>>().await() else null
    }

    /** A pack of small text files, `{ resource key: text }`, put into [kotlin.Resources] (missing pack: no-op). */
    suspend fun loadPack(path: String) {
        runCatching {
            val buf = download(path) ?: return
            val pack: dynamic = JSON.parse<dynamic>(js("new TextDecoder()").decode(buf).unsafeCast<String>())
            val enc: dynamic = js("new TextEncoder()")
            for (k in js("Object").keys(pack).unsafeCast<Array<String>>()) {
                val u8: dynamic = enc.encode(pack[k])
                kotlin.Resources.put(k, Int8Array(u8.buffer.unsafeCast<ArrayBuffer>(), u8.byteOffset.unsafeCast<Int>(), u8.byteLength.unsafeCast<Int>()).unsafeCast<ByteArray>())
            }
        }.onFailure { console.warn("[ReefLive] no $path: loading the files one by one") }
    }

    suspend fun fetchFile(path: String, timeoutMs: Int = 0) {
        if (kotlin.Resources.has(resKey(path))) return
        val ctl: dynamic = if (timeoutMs > 0) js("new AbortController()") else null
        val timer = if (ctl != null) global.setTimeout({ ctl.abort() }, timeoutMs) else null
        try {
            val ab = download(path, ctl?.signal) ?: return
            kotlin.Resources.put(resKey(path), Int8Array(ab).unsafeCast<ByteArray>())
        } finally {
            if (timer != null) global.clearTimeout(timer)
        }
    }

    /**
     * The renderer marks a model that fails to load as failed for good, so a dropped download (common for the 10 MB
     * rockscape on a phone connection) would leave the tank without rock until reload. A stalled download is cut
     * off and retried too, since the load waits for it.
     */
    suspend fun fetchWithRetry(path: String) {
        for (attempt in 0 until 4) {
            runCatching { fetchFile(path, timeoutMs = 45_000 shl attempt) }
            if (kotlin.Resources.has(resKey(path))) return
            kotlinx.coroutines.delay(500L shl attempt)
        }
        console.warn("[ReefLive] could not download $path")
    }

    /** "res/x" is a classpath resource "x"; "assets/x" stays "assets/x" (see AssetManager). */
    fun resKey(path: String) = if (path.startsWith("res/")) path.removePrefix("res/") else path

    suspend fun fetchAll(paths: List<String>) {
        val jobs = paths.map { p -> Promise<Unit> { ok, _ -> GlobalScope.launch { runCatching { fetchFile(p) }; ok(Unit) } } }
        Promise.all(jobs.toTypedArray()).await()
    }
}
