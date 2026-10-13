@file:Suppress("unused", "PackageDirectoryMismatch")

package java.util

class Locale private constructor(val tag: String) {
    companion object {
        val US = Locale("en-US")
        val ROOT = Locale("")
        val ENGLISH = Locale("en")
        fun getDefault() = US
    }
}

class UUID private constructor(private val s: String) {
    override fun toString() = s
    companion object {
        fun randomUUID(): UUID {
            val r = kotlin.random.Random
            val hex = "0123456789abcdef"
            val sb = StringBuilder()
            for (i in 0 until 36) sb.append(
                when (i) {
                    8, 13, 18, 23 -> '-'
                    14 -> '4'
                    19 -> hex[8 + r.nextInt(4)]
                    else -> hex[r.nextInt(16)]
                }
            )
            return UUID(sb.toString())
        }
    }
}

object Arrays {
    fun fill(a: FloatArray, v: Float) = a.fill(v)
    fun fill(a: DoubleArray, v: Double) = a.fill(v)
    fun fill(a: IntArray, v: Int) = a.fill(v)
    fun fill(a: ByteArray, v: Byte) = a.fill(v)
    fun fill(a: BooleanArray, v: Boolean) = a.fill(v)
}

class WeakHashMap<K, V> : HashMap<K, V>()

/** Binary min-heap with the java.util.PriorityQueue surface the sim uses. */
class PriorityQueue<T>(private val cmp: Comparator<in T>) : AbstractMutableCollection<T>() {
    private val heap = ArrayList<T>()
    override val size: Int get() = heap.size
    override fun add(element: T): Boolean {
        heap.add(element); var i = heap.size - 1
        while (i > 0) { val p = (i - 1) / 2; if (cmp.compare(heap[i], heap[p]) >= 0) break; swap(i, p); i = p }
        return true
    }
    fun offer(e: T) = add(e)
    fun peek(): T? = heap.firstOrNull()
    /** Platform-typed on the JVM (T!), so callers destructure it without a null check. */
    fun poll(): T {
        if (heap.isEmpty()) return null.unsafeCast<T>()
        val top = heap[0]; val last = heap.removeAt(heap.size - 1)
        if (heap.isNotEmpty()) {
            heap[0] = last; var i = 0
            while (true) {
                val l = 2 * i + 1; val r = l + 1; var m = i
                if (l < heap.size && cmp.compare(heap[l], heap[m]) < 0) m = l
                if (r < heap.size && cmp.compare(heap[r], heap[m]) < 0) m = r
                if (m == i) break; swap(i, m); i = m
            }
        }
        return top
    }
    override fun clear() = heap.clear()
    override fun iterator(): MutableIterator<T> = heap.iterator()
    private fun swap(a: Int, b: Int) { val t = heap[a]; heap[a] = heap[b]; heap[b] = t }
}
