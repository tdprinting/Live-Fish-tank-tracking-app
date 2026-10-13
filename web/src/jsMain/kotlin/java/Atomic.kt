@file:Suppress("unused", "PackageDirectoryMismatch")

package java.util.concurrent.atomic

class AtomicBoolean(private var v: Boolean = false) {
    fun get() = v
    fun set(x: Boolean) { v = x }
    fun getAndSet(x: Boolean): Boolean { val o = v; v = x; return o }
    fun compareAndSet(e: Boolean, x: Boolean): Boolean = if (v == e) { v = x; true } else false
}

class AtomicInteger(private var v: Int = 0) {
    fun get() = v
    fun set(x: Int) { v = x }
    fun incrementAndGet() = ++v
    fun getAndIncrement() = v++
    fun addAndGet(d: Int): Int { v += d; return v }
}

class AtomicReference<T>(private var v: T) {
    fun get() = v
    fun set(x: T) { v = x }
}
