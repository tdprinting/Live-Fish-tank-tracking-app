@file:Suppress("unused", "PackageDirectoryMismatch", "UNUSED_PARAMETER")

package android.os


object SystemClock {
    // globalThis, not window: the sim also runs in a Web Worker
    fun uptimeMillis(): Long = (js("globalThis").performance.now() as Double).toLong()
    fun elapsedRealtime(): Long = uptimeMillis()
}

class Looper private constructor() {
    companion object { private val main = Looper(); fun getMainLooper() = main; fun myLooper() = main }
}

/** Main-thread handler on the global timers ([postAtTime] uses the [SystemClock.uptimeMillis] clock). */
private val timersHost: dynamic get() = js("globalThis")

class Handler(looper: Looper? = null) {
    private val timers = HashMap<Runnable, MutableList<Int>>()
    fun post(r: Runnable): Boolean = postDelayed(r, 0)
    fun postDelayed(r: Runnable, ms: Long): Boolean {
        var id = 0
        id = timersHost.setTimeout({ timers[r]?.remove(id); r.run() }, ms.toInt().coerceAtLeast(0)) as Int
        timers.getOrPut(r) { ArrayList() }.add(id)
        return true
    }
    fun postAtTime(r: Runnable, uptimeMs: Long): Boolean = postDelayed(r, uptimeMs - SystemClock.uptimeMillis())
    fun removeCallbacks(r: Runnable) { timers.remove(r)?.forEach { timersHost.clearTimeout(it) } }
}
