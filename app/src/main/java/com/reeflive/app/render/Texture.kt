package com.reeflive.app.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES30
import android.opengl.GLUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 2D texture (ARCHITECTURE §8.2 `Texture2D`): RGBA8 upload, mipmaps, trilinear, 4x anisotropy when the
 * extension exists. Color textures stay sRGB-encoded; shaders decode with `pow(2.2)`.
 */
class Texture private constructor(val id: Int, val width: Int, val height: Int) {

    fun bind(unit: Int) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit); Gl.check("glActiveTexture")
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id); Gl.check("glBindTexture")
    }

    fun release() = Gl.deleteTexture(id)

    companion object {
        private const val TEXTURE_MAX_ANISOTROPY_EXT = 0x84FE
        private const val MAX_TEXTURE_MAX_ANISOTROPY_EXT = 0x84FF
        private var anisotropy: Float = -1f

        /** Decodes PNG/WebP bytes, subsampling by powers of two until the longest edge ≤ [maxEdge]. CPU only. */
        fun decode(bytes: ByteArray, maxEdge: Int): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0) return null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxEdge) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPremultiplied = false // keep straight alpha (fin rays, alpha masks, hair-algae mask)
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inScaled = false
            }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        }

        fun fromBitmap(bmp: Bitmap, repeat: Boolean = true, mipmap: Boolean = true): Texture {
            val id = Gl.genTexture()
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id); Gl.check("glBindTexture")
            GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1); Gl.check("glPixelStorei")
            if (bmp.config == Bitmap.Config.ARGB_8888) {
                // ARGB_8888 is stored R,G,B,A in memory; straight alpha when decoded with inPremultiplied = false.
                val buf = ByteBuffer.allocateDirect(bmp.byteCount).order(ByteOrder.nativeOrder())
                bmp.copyPixelsToBuffer(buf); buf.position(0)
                GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, bmp.width, bmp.height, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf)
                Gl.check("glTexImage2D")
            } else {
                GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0); Gl.check("texImage2D")
            }
            params(repeat, mipmap)
            if (mipmap) { GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D); Gl.check("glGenerateMipmap") }
            return Texture(id, bmp.width, bmp.height)
        }

        fun fromBytes(bytes: ByteArray, maxEdge: Int, repeat: Boolean = true): Texture? {
            val bmp = decode(bytes, maxEdge) ?: return null
            return try { fromBitmap(bmp, repeat) } finally { bmp.recycle() }
        }

        /** 1×1 RGBA texture (default white / flat normal / neutral rough-AO). */
        fun solid(r: Int, g: Int, b: Int, a: Int = 255): Texture {
            val id = Gl.genTexture()
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id); Gl.check("glBindTexture")
            val px = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()).put(byteArrayOf(r.toByte(), g.toByte(), b.toByte(), a.toByte())).apply { position(0) }
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, 1, 1, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, px); Gl.check("glTexImage2D(solid)")
            params(repeat = true, mipmap = false)
            return Texture(id, 1, 1)
        }

        /** [w] × [h] straight-alpha RGBA8 pixels made in code (the fish net's mesh), repeating and mipmapped. */
        fun rgba(w: Int, h: Int, pixels: ByteArray): Texture {
            val id = Gl.genTexture()
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id); Gl.check("glBindTexture")
            GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1); Gl.check("glPixelStorei")
            val px = ByteBuffer.allocateDirect(pixels.size).order(ByteOrder.nativeOrder()).put(pixels).apply { position(0) }
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, w, h, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, px); Gl.check("glTexImage2D(rgba)")
            params(repeat = true, mipmap = true)
            GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D); Gl.check("glGenerateMipmap")
            return Texture(id, w, h)
        }

        /** Single-channel float data texture (R16F, linear-filterable in ES 3.0), e.g. the water heightfield. */
        fun floatR(w: Int, h: Int): Texture {
            val id = Gl.genTexture()
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id); Gl.check("glBindTexture")
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_R16F, w, h, 0, GLES30.GL_RED, GLES30.GL_FLOAT, null); Gl.check("glTexImage2D(R16F)")
            params(repeat = false, mipmap = false)
            return Texture(id, w, h)
        }

        fun updateFloatR(t: Texture, data: FloatArray) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t.id); Gl.check("glBindTexture")
            GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 4); Gl.check("glPixelStorei")
            GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, t.width, t.height, GLES30.GL_RED, GLES30.GL_FLOAT, Gl.floats(data, t.width * t.height))
            Gl.check("glTexSubImage2D(R16F)")
        }

        private fun params(repeat: Boolean, mipmap: Boolean) {
            val wrap = if (repeat) GLES30.GL_REPEAT else GLES30.GL_CLAMP_TO_EDGE
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, wrap); Gl.check("wrapS")
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, wrap); Gl.check("wrapT")
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, if (mipmap) GLES30.GL_LINEAR_MIPMAP_LINEAR else GLES30.GL_LINEAR); Gl.check("minFilter")
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR); Gl.check("magFilter")
            if (mipmap) {
                if (anisotropy < 0f) {
                    anisotropy = 0f
                    if (Gl.hasExtension("GL_EXT_texture_filter_anisotropic")) {
                        val v = FloatArray(1)
                        GLES30.glGetFloatv(MAX_TEXTURE_MAX_ANISOTROPY_EXT, v, 0); Gl.check("maxAnisotropy")
                        anisotropy = minOf(4f, v[0])
                    }
                }
                if (anisotropy > 1f) { GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, TEXTURE_MAX_ANISOTROPY_EXT, anisotropy); Gl.check("anisotropy") }
            }
        }

        /** Forget cached GL capabilities after a context loss. */
        fun resetCaps() { anisotropy = -1f }
    }
}
