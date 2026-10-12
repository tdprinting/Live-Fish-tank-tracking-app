package com.reeflive.app.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.reeflive.app.data.repo.Clock
import com.reeflive.core.model.Ids
import com.reeflive.core.model.Photo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Copies picked images into app-private storage (`files/photos`). The image is decoded, rotated upright
 * from its EXIF orientation and re-encoded as JPEG, so no EXIF (GPS, device, time) survives.
 */
class PhotoStore(private val context: Context, private val clock: Clock) {
    private val dir: File get() = File(context.filesDir, DIR).apply { mkdirs() }

    fun file(relativePath: String): File = File(context.filesDir, relativePath)

    suspend fun import(uri: Uri): Photo? = withContext(Dispatchers.IO) {
        runCatching { importBlocking(uri) }.getOrNull()
    }

    private fun importBlocking(uri: Uri): Photo? {
        val cr = context.contentResolver
        val orientation = cr.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2

        val decoded = cr.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val upright = rotate(decoded, orientation)

        val id = Ids.new()
        val out = File(dir, "$id.jpg")
        FileOutputStream(out).use { upright.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
        val photo = Photo(id, null, null, "$DIR/$id.jpg", clock.now(), upright.width, upright.height)
        if (upright !== decoded) decoded.recycle()
        upright.recycle()
        return photo
    }

    private fun rotate(src: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return src
        }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    fun delete(relativePath: String) {
        runCatching { file(relativePath).delete() }
    }

    companion object {
        const val DIR = "photos"
        private const val MAX_EDGE = 2048
        private const val QUALITY = 88
    }
}
