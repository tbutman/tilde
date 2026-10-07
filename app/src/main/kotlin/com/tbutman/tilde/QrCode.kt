package com.tbutman.tilde

import android.content.Context
import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** The QR code as a bitmap: dark modules on the theme's light QR colour, with a quiet zone. */
object QrCode {
    private const val DARK = 0xFF0B0D10.toInt()

    /** `scale` pixels per module: 16 for the Share screen, less for the home-screen widget. */
    fun bitmap(context: Context, text: String, scale: Int = 16): Bitmap {
        val hints = mapOf(EncodeHintType.MARGIN to 4, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M)
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
        val width = matrix.width * scale
        val height = matrix.height * scale
        val light = context.getColor(R.color.qr_light)
        val pixels = IntArray(width * height) { i -> if (matrix[(i % width) / scale, (i / width) / scale]) DARK else light }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}
