package com.abhijit.cats_vs_dogs.ml

import android.graphics.RectF
import android.view.View
import com.abhijit.cats_vs_dogs.ml.NmsUtils

// Extracting information from raw prediction done by tflite
object YoloPostProcessorLive {
    private const val CONFIDENCE_THRESHOLD = 0.5f
    private const val MODEL_INPUT_SIZE = 640
    private const val NUM_BOXES = 8400

    // Extracting information from raw prediction done by tflite
    fun processOutput(
        output: Array<Array<FloatArray>>,
        overlayView: View
        ): List<Detection> {

        // 1. Safety Check: If screen isn't ready, don't calculate
        if (overlayView.width == 0 || overlayView.height == 0) return emptyList()

        val detections = mutableListOf<Detection>()
        val viewW = overlayView.width.toFloat()
        val viewH = overlayView.height.toFloat()

        for (i in 0 until NUM_BOXES) {
            val catScore = output[0][4][i]
            val dogScore = output[0][5][i]

            val confidence = maxOf(catScore, dogScore)

            if (confidence > CONFIDENCE_THRESHOLD) {
                // 2. Normalized scaling (Value / ModelSize * ScreenSize)
                val xCenter = (output[0][0][i] / MODEL_INPUT_SIZE) * viewW
                val yCenter = (output[0][1][i] / MODEL_INPUT_SIZE) * viewH
                val w = (output[0][2][i] / MODEL_INPUT_SIZE) * viewW
                val h = (output[0][3][i] / MODEL_INPUT_SIZE) * viewH

                // 3. Boundary Clipping (Ensures boxes stay on screen)
                val left = maxOf(0f, xCenter - w / 2)
                val top = maxOf(0f, yCenter - h / 2)
                val right = minOf(viewW, xCenter + w / 2)
                val bottom = minOf(viewH, yCenter + h / 2)

                detections.add(Detection(
                    RectF(left, top, right, bottom),
                    if (catScore > dogScore) "Cat" else "Dog",
                    confidence
                ))
            }
        }

        // apply NMS which will show only one box with high confidence
        return NmsUtils.applyNMS(detections)
    }
}