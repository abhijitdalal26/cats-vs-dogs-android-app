package com.abhijit.cats_vs_dogs

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts

// For camera
import android.view.View
import androidx.core.content.ContextCompat
import android.util.Size
import android.content.pm.PackageManager
import android.graphics.RectF

// CameraX Core
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider

// CameraX UI & Concurrency
import androidx.camera.view.PreviewView
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// Taking camera feed
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
import androidx.camera.core.ImageProxy

import android.widget.Button
import android.widget.ImageView
import android.widget.TextView

import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil

import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.nio.MappedByteBuffer
import androidx.core.graphics.createBitmap
import androidx.privacysandbox.tools.core.model.Method



class MainActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var viewFinder: PreviewView // this to show camera output
    private lateinit var btnUpload: Button
    private lateinit var btnLiveCamera: Button
    private lateinit var resultText: TextView
    private var tflite: Interpreter? = null
    private val labels = listOf("Cat", "Dog")

    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null

    // Pre-allocating Objects cause if done in LiveInference it will 30 times -> 30 FPS images passes
    // created once when the app starts and reused forever.
    private lateinit var tensorImage: TensorImage
    private lateinit var imageProcessor: ImageProcessor
    private lateinit var outputBuffer: Array<Array<FloatArray>>
    private lateinit var overlayView: OverlayView // it act as canvas to draw bounding box

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted! Start the camera
            startCamera()
        } else {
            // Permission denied
            resultText.text = "Camera permission is required for live detection"
        }
    }

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

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Initialize pre-allocated objects
        imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(640, 640, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(0f, 255f))
            .build()

        tensorImage = TensorImage(DataType.FLOAT32)
        outputBuffer = Array(1) { Array(6) { FloatArray(8400) } }

        // Initialize UI : this is what links the code to the UI
        imageView = findViewById(R.id.imageView)
        viewFinder = findViewById(R.id.viewFinder)
        overlayView = findViewById(R.id.overlayView)

        btnUpload = findViewById(R.id.btnUpload)
        btnLiveCamera = findViewById(R.id.btnLiveCamera)
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
            // Show ImageView, Hide Camera
            imageView.visibility = View.VISIBLE
            viewFinder.visibility = View.GONE
            overlayView.visibility = View.GONE

            getImage.launch("image/*")
        }

        btnLiveCamera.setOnClickListener {
            // Hide ImageView, Show Camera
            imageView.visibility = View.GONE
            viewFinder.visibility = View.VISIBLE
            overlayView.visibility = View.VISIBLE

            // Check permissions and start camera
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                requestCameraPermission.launch(android.Manifest.permission.CAMERA)
            }

        }



    }

    // Working on LiveCamera
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Get the camera provider
            cameraProvider = cameraProviderFuture.get()

            // 1. Preview: Displays the live feed on Image window will has 30–60 FPS
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

            // 2. Taking images from camera as per its capacity we will resize it as per need
            val resolutionSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(
                    AspectRatioStrategy(
                        AspectRatio.RATIO_16_9,
                        AspectRatioStrategy.FALLBACK_RULE_AUTO
                    )
                )
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(1280, 720), // Camera-friendly resolution
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                )
                .build()

            val imageAnalysis = ImageAnalysis.Builder()
                .setResolutionSelector(resolutionSelector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()


            // 3. Use the CLASS-LEVEL executor here
            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                try {
                    // Convert frame to bitmap
                    val bitmap = imageProxy.toBitmap()
                    runLiveInference(bitmap)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    // IMPORTANT: Always close the proxy to receive the next frame
                    imageProxy.close()
                }
            }

            // 4. Select the back camera
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind all use cases before rebinding to prevent crashes
                cameraProvider?.unbindAll()

                // Bind everything to the Lifecycle
                cameraProvider?.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalysis
                )

            } catch (e: Exception) {
                resultText.text = "Camera binding failed"
                e.printStackTrace()
            }

        }, ContextCompat.getMainExecutor(this))
    }


    // Run inference on the Images which are converted to Bitmap for live
    // actually it is not live we are basically passing frames
    // it will has 3 FPS that's only what we need
    private fun runLiveInference(bitmap: Bitmap) {
        // if model not ready skip this frame
        if (tflite == null) return

        // already loaded imageProcessor, tensorImage, outputBuffer
        tensorImage.load(bitmap)
        val processedImage = imageProcessor.process(tensorImage)

        // 1. Prepare output buffer: YOLO11n usually has [1, 6, 8400]
        // 6 features: [x_center, y_center, width, height, cat_score, dog_score]
        // val outputBuffer = Array(1) { Array(6) { FloatArray(8400) } }

        // 2. Run Inference
        tflite?.run(processedImage.buffer, outputBuffer)

        // 3. Send the "raw" outputBuffer to the processing function
        val detections = processOutput(outputBuffer)

        // 4. Update the UI
        runOnUiThread {
            overlayView.setResults(detections)

            val best = detections.maxByOrNull { it.confidence }
            // Only update the text if we are reasonably sure
            if (best != null && best.confidence > 0.5f) {
                resultText.text = "${best.label} (${(best.confidence * 100).toInt()}%)"
            } else {
                resultText.text = "Scanning..."
            }
        }
    }

    // Extracting information from raw prediction done by tflite
    private fun processOutput(output: Array<Array<FloatArray>>): List<Detection> {
        // 1. Safety Check: If screen isn't ready, don't calculate
        if (overlayView.width == 0 || overlayView.height == 0) return emptyList()

        val detections = mutableListOf<Detection>()
        val viewW = overlayView.width.toFloat()
        val viewH = overlayView.height.toFloat()

        for (i in 0 until 8400) {
            val catScore = output[0][4][i]
            val dogScore = output[0][5][i]
            val confidence = maxOf(catScore, dogScore)

            if (confidence > 0.5f) {
                // 2. Normalized scaling (Value / ModelSize * ScreenSize)
                val xCenter = (output[0][0][i] / 640f) * viewW
                val yCenter = (output[0][1][i] / 640f) * viewH
                val w = (output[0][2][i] / 640f) * viewW
                val h = (output[0][3][i] / 640f) * viewH

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
        val finalDetections = applyNMS(detections)
        return finalDetections
    }

    // will remove extra predicted boxes
    private fun applyNMS(
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
//        val imageProcessor = ImageProcessor.Builder()
//            .add(ResizeOp(640, 640, ResizeOp.ResizeMethod.BILINEAR))
//            .add(NormalizeOp(0f, 255f)) // YOLO expects pixels between 0.0 and 1.0
//            .build()

        // 2. Load Bitmap into TensorImage
//        var tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(bitmap)
        val processedImage = imageProcessor.process(tensorImage)

        // 3. Prepare Output Buffer
        // For YOLO11 with 2 classes (Cat, Dog), the output shape is [1, 6, 8400]
        // 6 rows = (x, y, w, h, cat_score, dog_score)
        // 8400 columns = potential detection boxes
        // val outputBuffer = Array(1) { Array(6) { FloatArray(8400) } }

        // 4. Run Inference
        tflite?.run(processedImage.buffer, outputBuffer)

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



    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown() // Kills the background thread that runs the camera
    }

}


data class Detection(
    val box: RectF,
    val label: String,
    val confidence: Float
)

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