@file:Suppress("unused", "PackageDirectoryMismatch")

package java.util.concurrent

class ConcurrentLinkedQueue<T> : AbstractMutableCollection<T>() {
    private val items = ArrayDeque<T>()
    override val size: Int get() = items.size
    override fun add(element: T): Boolean { items.addLast(element); return true }
    fun offer(e: T) = add(e)
    fun poll(): T? = items.removeFirstOrNull()
    fun peek(): T? = items.firstOrNull()
    override fun iterator(): MutableIterator<T> = items.iterator()
}

/** Runs each task on a later macrotask; prefetches the asset the task is about to open (see [AssetPrefetch]). */
interface Executor { fun execute(r: Runnable) }

interface ExecutorService : Executor {
    fun shutdown()
    fun shutdownNow(): List<Runnable>
    val isShutdown: Boolean
}

fun interface ThreadFactory { fun newThread(r: Runnable): Thread }

object Executors {
    fun newSingleThreadExecutor(@Suppress("UNUSED_PARAMETER") f: ThreadFactory? = null): ExecutorService = BrowserExecutor()
}

private class BrowserExecutor : ExecutorService {
    private var down = false
    override val isShutdown get() = down
    override fun shutdown() { down = true }
    override fun shutdownNow(): List<Runnable> { down = true; return emptyList() }
    override fun execute(r: Runnable) {
        val path = AssetPrefetch.takeLast()
        val run = { if (!down) r.run() }
        if (path == null) kotlinx.browser.window.setTimeout(run, 0)
        else AssetPrefetch.fetch(path) { run() }
    }
}

/**
 * The renderer's AssetStore records each path in its `requested` key set right before it submits the load to its
 * executor; the set remembers the path so the executor can download the file before the (synchronous) load runs.
 */
object AssetPrefetch {
    private var last: String? = null
    var fetcher: (String, () -> Unit) -> Unit = { _, done -> done() }
    fun noteRequested(path: String) { last = path }
    fun takeLast(): String? = last.also { last = null }
    fun fetch(path: String, done: () -> Unit) = fetcher(path, done)
}

class ConcurrentHashMap<K, V> : HashMap<K, V>() {
    companion object {
        fun <T> newKeySet(): MutableSet<T> = RequestSet()
    }
}

private class RequestSet<T> : AbstractMutableSet<T>() {
    private val s = LinkedHashSet<T>()
    override val size get() = s.size
    override fun add(element: T): Boolean {
        val added = s.add(element)
        if (added && element is String) AssetPrefetch.noteRequested(element)
        return added
    }
    override fun iterator(): MutableIterator<T> = s.iterator()
}
