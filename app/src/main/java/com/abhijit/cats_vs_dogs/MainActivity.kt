package com.abhijit.cats_vs_dogs

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts

import android.widget.Button
import android.widget.ImageView
import android.widget.TextView

import androidx.appcompat.app.AppCompatActivity
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil

import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.nio.MappedByteBuffer

class MainActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var btnUpload: Button
    private lateinit var resultText: TextView
    private var tflite: Interpreter? = null
    private val labels = listOf("Cat", "Dog")

    private val getImage = registerForActivityResult<String, Uri?>(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            imageView.imageTintList = null
            imageView.setPadding(0, 0, 0, 0)
            imageView.setImageURI(it)

            val bitmap = uriToBitmap(it)
            if (bitmap != null) {
                classifyImage(bitmap)
            }
        }
    }

    // onCreate function during start of app
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI
        imageView = findViewById(R.id.imageView)
        btnUpload = findViewById(R.id.btnUpload)
        resultText = findViewById(R.id.resultText)

        // Load the model
        try {
            val opt = Interpreter.Options().apply {
                setNumThreads(4)
            }
            tflite = Interpreter(FileUtil.loadMappedFile(this, "yolo_float16.tflite"), opt)
        } catch (e: Exception) {
            e.printStackTrace()
        }


        btnUpload.setOnClickListener {
            getImage.launch("image/*")
        }

    }

    // Convert URI from users device to Bitmap
    private fun uriToBitmap(uri: Uri): Bitmap? {
        return try {
            val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                // Modern way for Android 9 (Pie) and above
                val source = ImageDecoder.createSource(this.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder: ImageDecoder, _, _ ->
                    // This ensures the bitmap is mutable and uses software memory
                    // which is more stable for TFLite processing
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                // Older way for Android 8 and below
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(this.contentResolver, uri)
            }

            // Convert to ARGB_8888 if it's not already
            // YOLO models work best with standard 4-channel colors
            bitmap.copy(Bitmap.Config.ARGB_8888, true)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }


    // Classify the image using the model
    private fun classifyImage(bitmap: Bitmap) {
        if (tflite == null) {
            resultText.text = getString(R.string.model_loading_fails)
            return
        }

        // 1. Pre-process the Image
        // model was trained on 640x640 images.
        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(640, 640, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(0f, 255f)) // YOLO expects pixels between 0.0 and 1.0
            .build()

        // 2. Load Bitmap into TensorImage
        var tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(bitmap)
        tensorImage = imageProcessor.process(tensorImage)

        // 3. Prepare Output Buffer
        // For YOLO11 with 2 classes (Cat, Dog), the output shape is [1, 6, 8400]
        // 6 rows = (x, y, w, h, cat_score, dog_score)
        // 8400 columns = potential detection boxes
        val outputBuffer = Array(1) { Array(6) { FloatArray(8400) } }

        // 4. Run Inference
        tflite?.run(tensorImage.buffer, outputBuffer)

        // 5. Post-process (Extracting the best result)
        parseYOLOOutput(outputBuffer)
    }

    // Prediction is done but need to extract the result from the outputBuffer
    private fun parseYOLOOutput(output: Array<Array<FloatArray>>) {
        val labels = listOf("Cat", "Dog")
        val confidenceThreshold = 0.45f // Only show result if model is > 45% sure

        var bestScore = 0f
        var bestClassIndex = -1

        // output[0] is the first (and only) image
        // output[0][4] contains Cat scores for all 8400 boxes
        // output[0][5] contains Dog scores for all 8400 boxes

        for (i in 0 until 8400) {
            val catScore = output[0][4][i]
            val dogScore = output[0][5][i]

            // Check Cat
            if (catScore > bestScore) {
                bestScore = catScore
                bestClassIndex = 0
            }
            // Check Dog
            if (dogScore > bestScore) {
                bestScore = dogScore
                bestClassIndex = 1
            }
        }

        // Update the UI
        runOnUiThread {
            if (bestClassIndex != -1 && bestScore > confidenceThreshold) {
                val percentage = (bestScore * 100).toInt()
                resultText.text = "Prediction: ${labels[bestClassIndex]} ($percentage%)"
            } else {
                resultText.text = "Could not identify a Cat or Dog clearly."
            }
        }
    }


}