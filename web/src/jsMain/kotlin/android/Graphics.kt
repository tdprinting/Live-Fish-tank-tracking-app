@file:Suppress("unused", "PackageDirectoryMismatch", "UNUSED_PARAMETER")

package android.graphics

import android.opengl.GLES30
import org.khronos.webgl.Uint8Array
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import kotlinx.browser.window

/**
 * Browser image decoding is asynchronous, so a Bitmap carries the encoded bytes and its (header-parsed) size.
 * [uploadInto] puts a 1×1 stand-in in the texture now and the decoded image once the browser has it.
 */
class Bitmap internal constructor(internal val bytes: ByteArray, val width: Int, val height: Int) {
    enum class Config { ALPHA_8, RGB_565, ARGB_8888, HARDWARE }
    val config: Config = Config.HARDWARE
    val byteCount: Int get() = width * height * 4
    fun recycle() {}
    fun copyPixelsToBuffer(dst: java.nio.Buffer): Unit = error("pixels are decoded by the browser")
    private fun gray(): Uint8Array = Uint8Array(4).also { it.asDynamic().set(arrayOf(128, 128, 128, 255)) }

    internal fun uploadInto(texId: Int) {
        val gl = GLES30.gl
        gl.texImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, 1, 1, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, gray())
        val tex = GLES30.texture(texId)
        val type = if (bytes.size > 3 && bytes[0] == 0x89.toByte()) "image/png" else "image/jpeg"
        val blob = Blob(arrayOf(bytes), BlobPropertyBag(type))
        val opts: dynamic = js("({premultiplyAlpha: 'none', colorSpaceConversion: 'none', resizeQuality: 'high'})")
        opts.resizeWidth = width; opts.resizeHeight = height
        window.asDynamic().createImageBitmap(blob, opts).then { img: dynamic ->
            if (gl.isTexture(tex) as Boolean) {
                val prev = gl.getParameter(0x8069) // TEXTURE_BINDING_2D
                gl.bindTexture(GLES30.GL_TEXTURE_2D, tex)
                gl.pixelStorei(0x9241, 0) // UNPACK_PREMULTIPLY_ALPHA_WEBGL off
                gl.texImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, img)
                val minF = gl.getTexParameter(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER) as Int
                if (minF != GLES30.GL_LINEAR && minF != GLES30.GL_NEAREST) gl.generateMipmap(GLES30.GL_TEXTURE_2D)
                gl.bindTexture(GLES30.GL_TEXTURE_2D, prev)
            }
            img.close()
        }
    }
}

object BitmapFactory {
    class Options {
        var inJustDecodeBounds = false
        var inSampleSize = 1
        var inPremultiplied = true
        var inPreferredConfig: Bitmap.Config? = null
        var inScaled = true
        var outWidth = -1
        var outHeight = -1
    }

    fun decodeByteArray(b: ByteArray, off: Int, len: Int, o: Options? = null): Bitmap? {
        val (w, h) = ImageSize.of(b) ?: return null
        if (o != null) { o.outWidth = w; o.outHeight = h; if (o.inJustDecodeBounds) return null }
        val s = (o?.inSampleSize ?: 1).coerceAtLeast(1)
        return Bitmap(b, maxOf(1, w / s), maxOf(1, h / s))
    }
}

/** Width/height from a PNG IHDR or a JPEG SOF header. */
internal object ImageSize {
    private fun u8(b: ByteArray, i: Int) = b[i].toInt() and 0xFF
    private fun be16(b: ByteArray, i: Int) = (u8(b, i) shl 8) or u8(b, i + 1)
    private fun be32(b: ByteArray, i: Int) = (u8(b, i) shl 24) or (u8(b, i + 1) shl 16) or (u8(b, i + 2) shl 8) or u8(b, i + 3)

    fun of(b: ByteArray): Pair<Int, Int>? {
        if (b.size > 24 && u8(b, 0) == 0x89 && u8(b, 1) == 0x50) return be32(b, 16) to be32(b, 20)
        if (b.size > 4 && u8(b, 0) == 0xFF && u8(b, 1) == 0xD8) {
            var i = 2
            while (i + 9 < b.size) {
                if (u8(b, i) != 0xFF) { i++; continue }
                val m = u8(b, i + 1)
                if (m in 0xC0..0xCF && m != 0xC4 && m != 0xC8 && m != 0xCC) return be16(b, i + 7) to be16(b, i + 5)
                i += 2 + be16(b, i + 2)
            }
        }
        return null
    }
}
