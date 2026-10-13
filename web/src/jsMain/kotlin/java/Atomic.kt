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
    fun getAndAdd(d: Int): Int { val o = v; v += d; return o }
    fun getAndSet(x: Int): Int { val o = v; v = x; return o }
    fun decrementAndGet() = --v
    fun getAndDecrement() = v--
    fun compareAndSet(e: Int, x: Int): Boolean = if (v == e) { v = x; true } else false
}

class AtomicLong(private var v: Long = 0) {
    fun get() = v
    fun set(x: Long) { v = x }
    fun incrementAndGet() = ++v
    fun getAndIncrement() = v++
    fun addAndGet(d: Long): Long { v += d; return v }
    fun getAndSet(x: Long): Long { val o = v; v = x; return o }
    fun compareAndSet(e: Long, x: Long): Boolean = if (v == e) { v = x; true } else false
}

class AtomicReference<T>(private var v: T) {
    fun get() = v
    fun set(x: T) { v = x }
    fun getAndSet(x: T): T { val o = v; v = x; return o }
    fun compareAndSet(e: T, x: T): Boolean = if (v === e) { v = x; true } else false
}
