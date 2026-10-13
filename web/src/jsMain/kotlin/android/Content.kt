@file:Suppress("unused", "PackageDirectoryMismatch", "UNUSED_PARAMETER")

package android.content

import android.content.res.AssetManager

/** Just enough Context for the renderer: its assets. */
open class Context(val assets: AssetManager) {
    val applicationContext: Context get() = this
}
