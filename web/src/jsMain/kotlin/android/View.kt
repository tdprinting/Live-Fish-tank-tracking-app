@file:Suppress("unused", "PackageDirectoryMismatch", "UNUSED_PARAMETER")

package android.view

import android.content.Context

class ViewConfiguration private constructor(val scaledTouchSlop: Int, val scaledDoubleTapSlop: Int) {
    companion object {
        /** CSS-pixel equivalents of Android's 8 dp / 100 dp. */
        fun get(context: Context) = ViewConfiguration(8, 100)
    }
}

interface ViewParent { fun requestDisallowInterceptTouchEvent(disallow: Boolean) }

open class View { val parent: ViewParent? = null }

/** A MotionEvent built from browser pointer events (see web TouchInput). */
class MotionEvent(
    val actionMasked: Int,
    val actionIndex: Int,
    private val ids: IntArray,
    private val xs: FloatArray,
    private val ys: FloatArray,
    val eventTime: Long,
) {
    val pointerCount: Int get() = ids.size
    fun getPointerId(i: Int) = ids[i]
    fun getX(i: Int) = xs[i]
    fun getY(i: Int) = ys[i]

    companion object {
        const val ACTION_DOWN = 0
        const val ACTION_UP = 1
        const val ACTION_MOVE = 2
        const val ACTION_CANCEL = 3
        const val ACTION_POINTER_DOWN = 5
        const val ACTION_POINTER_UP = 6
    }
}
