package com.abhijit.cats_vs_dogs.ml

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp

// Initialize the necessary components for inference

// Use run to pass bitmap and get prediction in raw format.
class YoloEngine(context: Context) {

    private val interpreter: Interpreter = Interpreter(
        FileUtil.loadMappedFile(context, "yolo_float16.tflite"),
        Interpreter.Options().setNumThreads(4)
    )
    // Pre-process the Image
    private val imageProcessor: ImageProcessor = ImageProcessor.Builder()
        .add(ResizeOp(640, 640, ResizeOp.ResizeMethod.BILINEAR))
        .add(NormalizeOp(0f, 255f)) // YOLO expects pixels between 0.0 and 1.0
        .build()
    private val tensorImage = TensorImage(DataType.FLOAT32)
    private val outputBuffer = Array(1) { Array(6) { FloatArray(8400) } }

    fun run(bitmap: Bitmap): Array<Array<FloatArray>> {
        tensorImage.load(bitmap)
        val processed = imageProcessor.process(tensorImage)
        interpreter.run(processed.buffer, outputBuffer)
        return outputBuffer
    }

    // * Output Buffer
    // For YOLO11 with 2 classes (Cat, Dog), the output shape is [1, 6, 8400]
    // 6 rows = (x, y, w, h, cat_score, dog_score)
    // 8400 columns = potential detection boxes
    // val outputBuffer = Array(1) { Array(6) { FloatArray(8400) } }

    fun close() {
        interpreter.close()
    }
}

