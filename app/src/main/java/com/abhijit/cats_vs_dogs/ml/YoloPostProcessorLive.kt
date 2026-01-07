package com.abhijit.cats_vs_dogs.ml

import android.graphics.RectF

// Extracting information from raw prediction done by tflite
object YoloPostProcessorLive {
    private const val CONFIDENCE_THRESHOLD = 0.5f
    private const val MODEL_INPUT_SIZE = 640
    private const val NUM_BOXES = 8400

    // Extracting information from raw prediction done by tflite
    fun processOutput(
        output: Array<Array<FloatArray>>,
        viewW: Int,
        viewH: Int
        ): List<Detection> {

        // 1. Safety Check: If screen isn't ready, don't calculate
        if (viewW == 0 || viewH == 0) return emptyList()

        val detections = mutableListOf<Detection>()

        for (i in 0 until NUM_BOXES) {

            val catScore = output[0][4][i]
            val dogScore = output[0][5][i]
            val confidence = maxOf(catScore, dogScore)

            if (confidence < CONFIDENCE_THRESHOLD) continue

            // 2. Normalized scaling (Value / ModelSize * ScreenSize)
            val xCenter = output[0][0][i] * viewW
            val yCenter = output[0][1][i] * viewH
            val w = output[0][2][i] * viewW
            val h = output[0][3][i] * viewH

            // 3. Boundary Clipping (Ensures boxes stay on screen)
            val left = maxOf(0f, xCenter - w / 2)
            val top = maxOf(0f, yCenter - h / 2)

            val right = minOf(viewW.toFloat(), xCenter + w / 2)
            val bottom = minOf(viewH.toFloat(), yCenter + h / 2)

            detections.add(Detection(
                RectF(left, top, right, bottom),
                if (catScore > dogScore) "Cat" else "Dog",
                confidence
            ))
        }

        // apply NMS which will show only one box with high confidence
        return NmsUtils.applyNMS(detections)
    }
}