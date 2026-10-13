@file:Suppress("unused", "PackageDirectoryMismatch")

package java.nio

import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.DataView
import org.khronos.webgl.Float32Array
import org.khronos.webgl.Int16Array
import org.khronos.webgl.Int32Array
import org.khronos.webgl.Int8Array

class ByteOrder private constructor(val little: Boolean) {
    companion object {
        val LITTLE_ENDIAN = ByteOrder(true)
        val BIG_ENDIAN = ByteOrder(false)
        fun nativeOrder() = LITTLE_ENDIAN
    }
}

/**
 * java.nio buffers over one ArrayBuffer. Kotlin/JS primitive arrays are typed arrays, so puts are block copies
 * and [view] hands WebGL a zero-copy typed-array view from the current position.
 */
abstract class Buffer internal constructor(internal val ab: ArrayBuffer, internal val byteOff: Int, val capacity: Int, internal val elem: Int) {
    internal var pos = 0
    fun position(): Int = pos
    open fun position(p: Int): Buffer { pos = p; return this }
    fun remaining(): Int = capacity - pos
    fun hasRemaining() = pos < capacity
    fun limit(): Int = capacity
    fun rewind(): Buffer { pos = 0; return this }
    fun clear(): Buffer { pos = 0; return this }
    fun flip(): Buffer { pos = 0; return this }
    /** Typed view of the remaining contents (at most [bytes] bytes when ≥ 0). */
    internal abstract fun view(bytes: Int): dynamic
    internal fun count(bytes: Int) = if (bytes < 0) capacity - pos else minOf(capacity - pos, bytes / elem)
}

class ByteBuffer private constructor(ab: ArrayBuffer, off: Int, cap: Int) : Buffer(ab, off, cap, 1) {
    private val dv = DataView(ab, off, cap)
    private var le = false
    private val bytes: ByteArray = Int8Array(ab, off, cap).unsafeCast<ByteArray>()

    fun order(o: ByteOrder): ByteBuffer { le = o.little; return this }
    fun order(): ByteOrder = if (le) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN
    override fun position(p: Int): ByteBuffer { pos = p; return this }
    fun array(): ByteArray = bytes
    override fun view(bytes: Int): dynamic = Int8Array(ab, byteOff + pos, count(bytes))

    fun get(i: Int): Byte = dv.getInt8(i)
    fun get(dst: ByteArray): ByteBuffer { for (k in dst.indices) dst[k] = dv.getInt8(pos + k); pos += dst.size; return this }
    fun getShort(i: Int): Short = dv.getInt16(i, le)
    fun getInt(i: Int): Int = dv.getInt32(i, le)
    fun getFloat(i: Int): Float = dv.getFloat32(i, le)
    val short: Short get() = getShort(pos).also { pos += 2 }
    val int: Int get() = getInt(pos).also { pos += 4 }
    val float: Float get() = getFloat(pos).also { pos += 4 }
    fun put(src: ByteArray): ByteBuffer { bytes.asDynamic().set(src, pos); pos += src.size; return this }
    fun put(b: Byte): ByteBuffer { dv.setInt8(pos, b); pos += 1; return this }
    fun putInt(v: Int): ByteBuffer { dv.setInt32(pos, v, le); pos += 4; return this }

    fun asFloatBuffer() = FloatBuffer(ab, byteOff + pos, (capacity - pos) / 4)
    fun asShortBuffer() = ShortBuffer(ab, byteOff + pos, (capacity - pos) / 2)
    fun asIntBuffer() = IntBuffer(ab, byteOff + pos, (capacity - pos) / 4)

    companion object {
        fun wrap(a: ByteArray): ByteBuffer {
            val t = a.unsafeCast<Int8Array>()
            return ByteBuffer(t.buffer, t.byteOffset, t.length)
        }
        fun allocateDirect(n: Int) = ByteBuffer(ArrayBuffer(n), 0, n)
        fun allocate(n: Int) = allocateDirect(n)
    }
}

class FloatBuffer internal constructor(ab: ArrayBuffer, off: Int, cap: Int) : Buffer(ab, off, cap, 4) {
    private val a: FloatArray = Float32Array(ab, off, cap).unsafeCast<FloatArray>()
    override fun position(p: Int): FloatBuffer { pos = p; return this }
    fun put(src: FloatArray): FloatBuffer = put(src, 0, src.size)
    fun put(src: FloatArray, off: Int, len: Int): FloatBuffer {
        a.asDynamic().set(src.asDynamic().subarray(off, off + len), pos); pos += len; return this
    }
    fun put(v: Float): FloatBuffer { a[pos++] = v; return this }
    fun get(i: Int): Float = a[i]
    override fun view(bytes: Int): dynamic = Float32Array(ab, byteOff + pos * 4, count(bytes))
    companion object { fun allocate(n: Int) = ByteBuffer.allocateDirect(n * 4).asFloatBuffer() }
}

class ShortBuffer internal constructor(ab: ArrayBuffer, off: Int, cap: Int) : Buffer(ab, off, cap, 2) {
    private val a: ShortArray = Int16Array(ab, off, cap).unsafeCast<ShortArray>()
    override fun position(p: Int): ShortBuffer { pos = p; return this }
    fun put(src: ShortArray): ShortBuffer { a.asDynamic().set(src, pos); pos += src.size; return this }
    override fun view(bytes: Int): dynamic = Int16Array(ab, byteOff + pos * 2, count(bytes))
}

class IntBuffer internal constructor(ab: ArrayBuffer, off: Int, cap: Int) : Buffer(ab, off, cap, 4) {
    private val a: IntArray = Int32Array(ab, off, cap).unsafeCast<IntArray>()
    override fun position(p: Int): IntBuffer { pos = p; return this }
    fun put(src: IntArray): IntBuffer { a.asDynamic().set(src, pos); pos += src.size; return this }
    override fun view(bytes: Int): dynamic = Int32Array(ab, byteOff + pos * 4, count(bytes))
}
