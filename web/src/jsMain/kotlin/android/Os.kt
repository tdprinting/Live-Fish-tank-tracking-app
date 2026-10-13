@file:Suppress("unused", "PackageDirectoryMismatch", "UNUSED_PARAMETER")

package android.os

import kotlinx.browser.window

object SystemClock {
    fun uptimeMillis(): Long = window.performance.now().toLong()
    fun elapsedRealtime(): Long = uptimeMillis()
}

class Looper private constructor() {
    companion object { private val main = Looper(); fun getMainLooper() = main; fun myLooper() = main }
}

/** Main-thread handler on window timers ([postAtTime] uses the [SystemClock.uptimeMillis] clock). */
class Handler(looper: Looper? = null) {
    private val timers = HashMap<Runnable, MutableList<Int>>()
    fun post(r: Runnable): Boolean = postDelayed(r, 0)
    fun postDelayed(r: Runnable, ms: Long): Boolean {
        var id = 0
        id = window.setTimeout({ timers[r]?.remove(id); r.run() }, ms.toInt().coerceAtLeast(0))
        timers.getOrPut(r) { ArrayList() }.add(id)
        return true
    }
    fun postAtTime(r: Runnable, uptimeMs: Long): Boolean = postDelayed(r, uptimeMs - SystemClock.uptimeMillis())
    fun removeCallbacks(r: Runnable) { timers.remove(r)?.forEach { window.clearTimeout(it) } }
}
