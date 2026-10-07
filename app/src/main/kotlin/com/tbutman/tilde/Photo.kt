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
 * A card's photo: a cropped square kept in the app's private storage. It is never in a tap or the
 * QR code (a photo would make the contact card slow to read); a contact card sent with Send
 * includes it only when the owner switches that on (Prefs.sendPhoto).
 */
object Photo {
    /** Large enough to crop from comfortably, small enough not to strain memory. */
    private const val MAX_SIDE = 2048

    /** Before cards there was one photo; it becomes card 1's (see Prefs). */
    const val LEGACY_FILE = "profile_photo.jpg"

    /** Each card has its own photo file. */
    fun file(dir: File, cardId: String) = File(dir, "photo-$cardId.jpg")

    fun load(context: Context, cardId: String): Bitmap? =
        file(context.filesDir, cardId).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    fun save(context: Context, cardId: String, bitmap: Bitmap) {
        val tmp = File(context.filesDir, "photo.tmp")
        tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        tmp.renameTo(file(context.filesDir, cardId))
    }

    fun delete(context: Context, cardId: String) {
        file(context.filesDir, cardId).delete()
    }

    /** The card's photo as a small JPEG (at most 400 px), for a sent contact card; null without one. */
    fun smallJpeg(context: Context, cardId: String): ByteArray? {
        val photo = load(context, cardId) ?: return null
        val scale = minOf(1f, 400f / maxOf(photo.width, photo.height))
        val small = if (scale < 1f) Bitmap.createScaledBitmap(photo, (photo.width * scale).toInt(), (photo.height * scale).toInt(), true) else photo
        return java.io.ByteArrayOutputStream().also { small.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
    }

    /** For copying a card: the copy gets its own file, so deleting one photo leaves the other. */
    fun copy(context: Context, fromCardId: String, toCardId: String) {
        val from = file(context.filesDir, fromCardId)
        if (from.exists()) from.copyTo(file(context.filesDir, toCardId), overwrite = true)
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
