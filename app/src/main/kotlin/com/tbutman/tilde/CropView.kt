package com.tbutman.tilde

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * Pick the part of a photo to use: pinch to zoom, drag to move, double-tap to reset. The photo
 * always covers the round frame (it can't be dragged or shrunk past an edge), so the crop never
 * has empty corners. `crop()` returns the framed square; the app shows it as a circle.
 */
class CropView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var bitmap: Bitmap? = null
        set(value) {
            field = value
            reset()
        }

    private val imageMatrix = Matrix()
    private val frame = RectF()
    private val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val scrimPaint = Paint().apply { color = Color.argb(170, 0, 0, 0) }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 2 * resources.displayMetrics.density
    }
    private val scrim = Path().apply { fillType = Path.FillType.EVEN_ODD }

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val factor = detector.scaleFactor.coerceIn(minScale() / currentScale(), MAX_ZOOM * minScale() / currentScale())
            imageMatrix.postScale(factor, factor, detector.focusX, detector.focusY)
            keepFrameCovered()
            invalidate()
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            imageMatrix.postTranslate(-dx, -dy)
            keepFrameCovered()
            invalidate()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            reset()
            return true
        }
    })

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val side = min(w, h) * FRAME_FRACTION
        frame.set((w - side) / 2, (h - side) / 2, (w + side) / 2, (h + side) / 2)
        scrim.reset()
        scrim.addRect(0f, 0f, w.toFloat(), h.toFloat(), Path.Direction.CW)
        scrim.addCircle(frame.centerX(), frame.centerY(), side / 2, Path.Direction.CW)
        reset()
    }

    /** Fit the photo so it just covers the frame, centred. */
    fun reset() {
        val bmp = bitmap ?: return invalidate()
        if (frame.isEmpty) return
        val scale = max(frame.width() / bmp.width, frame.height() / bmp.height)
        imageMatrix.setScale(scale, scale)
        imageMatrix.postTranslate(frame.centerX() - bmp.width * scale / 2, frame.centerY() - bmp.height * scale / 2)
        invalidate()
    }

    /** Turn the photo a quarter turn clockwise, keeping the zoom fitted. */
    fun rotate() {
        val bmp = bitmap ?: return
        bitmap = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(90f) }, true)
    }

    /** The framed part of the photo as a `size` x `size` square. */
    fun crop(size: Int = OUTPUT_SIZE): Bitmap? {
        val bmp = bitmap ?: return null
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val m = Matrix(imageMatrix).apply {
            postTranslate(-frame.left, -frame.top)
            postScale(size / frame.width(), size / frame.height())
        }
        Canvas(out).drawBitmap(bmp, m, imagePaint)
        return out
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (bitmap == null) return false
        scaleDetector.onTouchEvent(event)
        if (!scaleDetector.isInProgress) gestureDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val bmp = bitmap ?: return
        canvas.drawBitmap(bmp, imageMatrix, imagePaint)
        canvas.drawPath(scrim, scrimPaint)
        canvas.drawCircle(frame.centerX(), frame.centerY(), frame.width() / 2, ringPaint)
    }

    private fun currentScale(): Float = FloatArray(9).also { imageMatrix.getValues(it) }[Matrix.MSCALE_X]

    private fun minScale(): Float {
        val bmp = bitmap ?: return 1f
        return max(frame.width() / bmp.width, frame.height() / bmp.height)
    }

    /** Scale up if needed, then slide the photo back so it covers the whole frame. */
    private fun keepFrameCovered() {
        val bmp = bitmap ?: return
        if (currentScale() < minScale()) {
            val f = minScale() / currentScale()
            imageMatrix.postScale(f, f, frame.centerX(), frame.centerY())
        }
        val shown = RectF(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat()).also { imageMatrix.mapRect(it) }
        var dx = 0f
        var dy = 0f
        if (shown.left > frame.left) dx = frame.left - shown.left
        if (shown.right < frame.right) dx = frame.right - shown.right
        if (shown.top > frame.top) dy = frame.top - shown.top
        if (shown.bottom < frame.bottom) dy = frame.bottom - shown.bottom
        imageMatrix.postTranslate(dx, dy)
    }

    companion object {
        const val OUTPUT_SIZE = 640
        private const val FRAME_FRACTION = 0.82f
        private const val MAX_ZOOM = 6f
    }
}
