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
        GLES30.syncTextureUnit()
        gl.texImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, 1, 1, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, gray())
        val tex = GLES30.texture(texId)
        val type = ImageSize.mime(bytes)
        val blob = Blob(arrayOf(bytes), BlobPropertyBag(type))
        val opts: dynamic = js("({premultiplyAlpha: 'none', colorSpaceConversion: 'none', resizeQuality: 'high'})")
        opts.resizeWidth = width; opts.resizeHeight = height
        // no gl.isTexture / getTexParameter / getParameter: each is a synchronous round trip that stalls the page until
        // the GPU has finished everything queued (they were the top main-thread cost while textures streamed in)
        window.asDynamic().createImageBitmap(blob, opts).then { img: dynamic ->
            if (gl === GLES30.gl && GLES30.alive(texId, tex)) GLES30.withTexture2D(texId) {
                gl.pixelStorei(0x9241, 0) // UNPACK_PREMULTIPLY_ALPHA_WEBGL off
                gl.texImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, img)
                val minF = GLES30.minFilter(texId)
                if (minF != GLES30.GL_LINEAR && minF != GLES30.GL_NEAREST) gl.generateMipmap(GLES30.GL_TEXTURE_2D)
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

/** Width/height from a PNG IHDR, a JPEG SOF or a WebP (VP8 / VP8L / VP8X) header. */
internal object ImageSize {
    private fun u8(b: ByteArray, i: Int) = b[i].toInt() and 0xFF
    private fun be16(b: ByteArray, i: Int) = (u8(b, i) shl 8) or u8(b, i + 1)
    private fun be32(b: ByteArray, i: Int) = (u8(b, i) shl 24) or (u8(b, i + 1) shl 16) or (u8(b, i + 2) shl 8) or u8(b, i + 3)
    private fun le16(b: ByteArray, i: Int) = u8(b, i) or (u8(b, i + 1) shl 8)
    private fun le24(b: ByteArray, i: Int) = u8(b, i) or (u8(b, i + 1) shl 8) or (u8(b, i + 2) shl 16)

    /** "RIFF" size "WEBP" (tools/optimize_glb.py embeds the textures whose alpha matters as WebP). */
    private fun isWebp(b: ByteArray) = b.size > 30 && u8(b, 0) == 0x52 && u8(b, 1) == 0x49 && u8(b, 2) == 0x46 &&
        u8(b, 3) == 0x46 && u8(b, 8) == 0x57 && u8(b, 9) == 0x45 && u8(b, 10) == 0x42 && u8(b, 11) == 0x50

    /** The image's MIME type from its first bytes: the Blob type the browser decodes it as. */
    fun mime(b: ByteArray): String = when {
        b.size > 3 && u8(b, 0) == 0x89 -> "image/png"
        isWebp(b) -> "image/webp"
        else -> "image/jpeg"
    }

    fun of(b: ByteArray): Pair<Int, Int>? {
        if (b.size > 24 && u8(b, 0) == 0x89 && u8(b, 1) == 0x50) return be32(b, 16) to be32(b, 20)
        if (isWebp(b)) {
            // the first chunk: "VP8 " lossy (size in the key frame header), "VP8L" lossless, "VP8X" extended (alpha)
            val fourCc = (12 until 16).joinToString("") { b[it].toInt().toChar().toString() }
            return when (fourCc) {
                "VP8 " -> (le16(b, 26) and 0x3FFF) to (le16(b, 28) and 0x3FFF)
                "VP8L" -> {
                    val bits = u8(b, 21) or (u8(b, 22) shl 8) or (u8(b, 23) shl 16) or (u8(b, 24) shl 24)
                    ((bits and 0x3FFF) + 1) to (((bits ushr 14) and 0x3FFF) + 1)
                }
                "VP8X" -> (le24(b, 24) + 1) to (le24(b, 27) + 1)
                else -> null
            }
        }
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
