@file:Suppress("unused", "PackageDirectoryMismatch")

package android.content.res

import java.io.InputStream

/**
 * App assets served from the web build's `assets/` folder. Files must be fetched into [kotlin.Resources] (under
 * "assets/<path>") before [open]; [list] reads the build-time index.
 */
class AssetManager(private val index: Map<String, List<String>>) {
    fun open(path: String): InputStream {
        val b = kotlin.Resources.get("assets/$path") ?: throw java.io.IOException("asset not loaded: $path")
        return InputStream(b)
    }
    fun list(dir: String): Array<String>? = index[dir.trimEnd('/')]?.toTypedArray()
}
