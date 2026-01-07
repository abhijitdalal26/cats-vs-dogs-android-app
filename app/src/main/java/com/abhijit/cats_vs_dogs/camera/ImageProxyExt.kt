package com.abhijit.cats_vs_dogs.camera

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import androidx.core.graphics.createBitmap

// ImageProxy - frame straight from the camera hardware
// Convert ImageProxy from camera to Bitmap
fun ImageProxy.toBitmap(): Bitmap {
    val buffer = planes[0].buffer
    buffer.rewind()

    val pixelStride = planes[0].pixelStride // Should be 4 for RGBA
    val rowStride = planes[0].rowStride
    val rowPadding = rowStride - (pixelStride * width)

    // 1. Create a bitmap that matches the buffer's internal layout (including padding)
    val bitmap = createBitmap(width + rowPadding / pixelStride, height)

    // 2. Copy the "messy" hardware data into our bitmap
    bitmap.copyPixelsFromBuffer(buffer)

    // 3. If there was padding, crop it out to return a clean 'width x height' image
    return if (rowPadding == 0) {
        bitmap
    } else {
        Bitmap.createBitmap(bitmap, 0, 0, width, height)
    }
}