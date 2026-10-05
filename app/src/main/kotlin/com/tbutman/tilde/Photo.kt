package com.tbutman.tilde

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.os.Build
import java.io.File
import kotlin.math.max

/**
 * The profile photo: a cropped square kept in the app's private storage. It is only ever shown on
 * this phone's screen, never sent in a tap (a photo would make the contact card slow to read).
 */
object Photo {
    /** Large enough to crop from comfortably, small enough not to strain memory. */
    private const val MAX_SIDE = 2048

    private fun file(context: Context) = File(context.filesDir, "profile_photo.jpg")

    fun load(context: Context): Bitmap? = file(context).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    fun save(context: Context, bitmap: Bitmap) {
        val tmp = File(context.filesDir, "profile_photo.tmp")
        tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        tmp.renameTo(file(context))
    }

    fun delete(context: Context) {
        file(context).delete()
    }

    /** Reads a picked photo the right way up and no larger than MAX_SIDE. */
    fun decode(context: Context, uri: Uri): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder applies the camera's rotation (EXIF) itself.
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val side = max(info.size.width, info.size.height)
                if (side > MAX_SIDE) decoder.setTargetSampleSize(side / MAX_SIDE)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching null
            val degrees = context.contentResolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
            if (degrees == 0f) bitmap
            else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)
        }
    }.getOrNull()
}
