package com.abhijit.cats_vs_dogs.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

// When user click on upload button the instance for Image selection is created once user select images it's URI is passed to this
// this takes that URI of image convert it into a Bitmap

//Bitmap width = 640
//Bitmap height = 640
//Config = ARGB_8888

object ImagePicker {

    // Convert URI from users device to Bitmap
    fun uriToBitmap(context: Context ,uri: Uri): Bitmap? {
        return try {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Modern way for Android 9 (Pie) and above
                val source = ImageDecoder.createSource(context.contentResolver, uri)

                ImageDecoder.decodeBitmap(source) { decoder: ImageDecoder, _, _ ->
                    // This ensures the bitmap is mutable and uses software memory
                    // which is more stable for TFLite processing
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                // Older way for Android 8 and below
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }

            // Convert to ARGB_8888 if it's not already
            // YOLO models work best with standard 4-channel colors
            bitmap.copy(Bitmap.Config.ARGB_8888, true)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}