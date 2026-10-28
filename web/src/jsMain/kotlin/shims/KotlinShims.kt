@file:Suppress("unused", "NOTHING_TO_INLINE")

// JVM-only APIs that :core uses, re-declared for Kotlin/JS so the web build compiles the
// unmodified core sources (single source of truth). Declared in `kotlin` so they are
// default-imported exactly like java.lang.* / kotlin.jvm.* are on the JVM.
// Needs -Xallow-kotlin-package (web/build.gradle.kts). Browsers are single-threaded, so
// locks and volatile are no-ops.
package kotlin

import kotlin.math.pow
import kotlin.reflect.KClass

annotation class Volatile
annotation class JvmField
annotation class JvmStatic
annotation class Synchronized

object Integer {
    const val MAX_VALUE = Int.MAX_VALUE
    const val MIN_VALUE = Int.MIN_VALUE
    fun toHexString(i: Int): String = (i.toLong() and 0xFFFFFFFFL).toString(16)
}

inline fun <R> synchronized(@Suppress("UNUSED_PARAMETER") lock: Any, block: () -> R): R = block()

object Math {
    const val PI: Double = kotlin.math.PI
    const val E: Double = kotlin.math.E
    inline fun toRadians(d: Double) = d / 180.0 * PI
    inline fun toDegrees(r: Double) = r * 180.0 / PI
    inline fun sqrt(x: Double) = kotlin.math.sqrt(x)
    inline fun cbrt(x: Double) = kotlin.math.cbrt(x)
    inline fun pow(a: Double, b: Double) = a.pow(b)
    inline fun exp(x: Double) = kotlin.math.exp(x)
    inline fun log(x: Double) = kotlin.math.ln(x)
    inline fun log10(x: Double) = kotlin.math.log10(x)
    inline fun sin(x: Double) = kotlin.math.sin(x)
    inline fun cos(x: Double) = kotlin.math.cos(x)
    inline fun tan(x: Double) = kotlin.math.tan(x)
    inline fun atan2(y: Double, x: Double) = kotlin.math.atan2(y, x)
    inline fun floor(x: Double) = kotlin.math.floor(x)
    inline fun ceil(x: Double) = kotlin.math.ceil(x)
    inline fun abs(x: Double) = kotlin.math.abs(x)
    inline fun abs(x: Int) = kotlin.math.abs(x)
    inline fun min(a: Double, b: Double) = kotlin.math.min(a, b)
    inline fun max(a: Double, b: Double) = kotlin.math.max(a, b)
    inline fun min(a: Int, b: Int) = kotlin.math.min(a, b)
    inline fun max(a: Int, b: Int) = kotlin.math.max(a, b)
    fun round(x: Double): Long = kotlin.math.floor(x + 0.5).toLong()
    fun round(x: Float): Int = kotlin.math.floor(x + 0.5f).toInt()
    fun floorDiv(a: Long, b: Long): Long { val q = a / b; return if ((a % b != 0L) && ((a xor b) < 0)) q - 1 else q }
    fun floorDiv(a: Int, b: Int): Int { val q = a / b; return if ((a % b != 0) && ((a xor b) < 0)) q - 1 else q }
    fun floorMod(a: Int, b: Int): Int = a - floorDiv(a, b) * b
    fun random(): Double = kotlin.random.Random.nextDouble()
}

/** Classpath resources come from [Resources], filled by the web app before the sim starts. */
open class ClassLoader {
    fun getResourceAsStream(path: String): java.io.InputStream? = Resources.get(path)?.let { java.io.InputStream(it) }
    fun getResource(path: String): Any? = Resources.get(path)
}

object Resources {
    private val files = HashMap<String, ByteArray>()
    fun put(path: String, bytes: ByteArray) { files[path] = bytes }
    fun get(path: String): ByteArray? = files[path]
    fun has(path: String) = path in files
}

fun interface Runnable { fun run() }

/** java.lang.Thread: start() runs the body on a later macrotask (the browser has one thread). */
class Thread(private val body: Runnable? = null, val name: String = "") {
    var priority = NORM_PRIORITY
    var isDaemon = false
    val contextClassLoader: ClassLoader = ClassLoader()
    fun start() { body?.let { b -> js("globalThis").setTimeout({ b.run() }, 0) } }
    companion object {
        const val NORM_PRIORITY = 5
        private val main = Thread()
        fun currentThread(): Thread = main
    }
}

object System {
    // globalThis, not window: the sim also runs in a Web Worker
    private val t0 = (js("globalThis").performance.now() as Double)
    fun nanoTime(): Long = (((js("globalThis").performance.now() as Double) - t0) * 1e6).toLong()
    fun currentTimeMillis(): Long = kotlin.js.Date.now().toLong()
    fun arraycopy(src: Any, srcPos: Int, dst: Any, dstPos: Int, len: Int) {
        val s: dynamic = src; val d: dynamic = dst
        if (s === d && srcPos < dstPos) { for (i in len - 1 downTo 0) d[dstPos + i] = s[srcPos + i] }
        else for (i in 0 until len) d[dstPos + i] = s[srcPos + i]
    }
}

class JavaClass internal constructor() { val classLoader: ClassLoader? = ClassLoader() }

val <T : Any> KClass<T>.java: JavaClass get() = JavaClass()
