@file:Suppress("unused", "PackageDirectoryMismatch", "UNUSED_PARAMETER")

package android.util

object Log {
    fun e(tag: String, msg: String, t: Throwable? = null): Int { console.error("[$tag] $msg", t); return 0 }
    fun w(tag: String, msg: String, t: Throwable? = null): Int { console.warn("[$tag] $msg", t); return 0 }
    fun i(tag: String, msg: String): Int { console.info("[$tag] $msg"); return 0 }
    fun d(tag: String, msg: String): Int { console.log("[$tag] $msg"); return 0 }
}
