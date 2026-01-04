package com.abhijit.cats_vs_dogs

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Custom View that draws bounding boxes over the camera feed.
 * Ensure your Detection data class is accessible to this file.
 */
class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var results = listOf<Detection>()
    private val density = resources.displayMetrics.density

    // Paint for bounding boxes
    // Explicitly defining : Paint prevents the "Cannot infer type" error
    private val boxPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 3f * density   // Scaled for screen density (dp)
    }

    /**
     * Updates the list of detections and requests a redraw.
     */
    fun setResults(newResults: List<Detection>) {
        results = newResults
        // invalidate() must be called on the main thread to trigger onDraw()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (detection in results) {
            val box = detection.box

            // Clamp box to view bounds to prevent drawing off-screen
            val left = maxOf(0f, box.left)
            val top = maxOf(0f, box.top)
            val right = minOf(width.toFloat(), box.right)
            val bottom = minOf(height.toFloat(), box.bottom)

            canvas.drawRect(left, top, right, bottom, boxPaint)
        }
    }
}