package com.tbutman.tilde

import android.content.Context
import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** The QR code as a bitmap: dark modules on the theme's light QR colour, with a quiet zone. */
object QrCode {
    private const val DARK = 0xFF0B0D10.toInt()

    /**
     * The code's modules, or null when the text doesn't fit in a QR code (about 2.3 kB at level M):
     * ZXing throws then, and the Share screen, the full-screen code and the widget say so instead.
     * Plain ZXing, so it's unit-tested.
     */
    fun matrix(text: String): BitMatrix? {
        val hints = mapOf(EncodeHintType.MARGIN to 4, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M)
        return try {
            QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
        } catch (_: WriterException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /** `scale` pixels per module: 16 for the Share screen, less for the home-screen widget. Null when it's too long. */
    fun bitmap(context: Context, text: String, scale: Int = 16): Bitmap? {
        val matrix = matrix(text) ?: return null
        val width = matrix.width * scale
        val height = matrix.height * scale
        val light = context.getColor(R.color.qr_light)
        val pixels = IntArray(width * height) { i -> if (matrix[(i % width) / scale, (i / width) / scale]) DARK else light }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}
