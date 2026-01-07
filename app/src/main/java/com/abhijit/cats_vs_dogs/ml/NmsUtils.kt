package com.abhijit.cats_vs_dogs.ml

import android.graphics.RectF

// will remove extra predicted boxes
object NmsUtils {
    fun applyNMS(
        detections: List<Detection>,
        iouThreshold: Float = 0.5f
    ): List<Detection> {

        val sorted = detections.sortedByDescending { it.confidence }.toMutableList()
        val selected = mutableListOf<Detection>()

        while (sorted.isNotEmpty()) {
            val best = sorted.removeAt(0)
            selected.add(best)

            val iterator = sorted.iterator()
            while (iterator.hasNext()) {
                val other = iterator.next()
                if (iou(best.box, other.box) > iouThreshold) {
                    iterator.remove()
                }
            }
        }
        return selected
    }

    // IOU needed by applyNMS, This measures how much two boxes overlap.
    private fun iou(a: RectF, b: RectF): Float {
        val intersectionLeft = maxOf(a.left, b.left)
        val intersectionTop = maxOf(a.top, b.top)
        val intersectionRight = minOf(a.right, b.right)
        val intersectionBottom = minOf(a.bottom, b.bottom)

        val intersectionArea =
            maxOf(0f, intersectionRight - intersectionLeft) *
                    maxOf(0f, intersectionBottom - intersectionTop)

        val areaA = (a.right - a.left) * (a.bottom - a.top)
        val areaB = (b.right - b.left) * (b.bottom - b.top)

        return intersectionArea / (areaA + areaB - intersectionArea + 1e-6f)
    }
}