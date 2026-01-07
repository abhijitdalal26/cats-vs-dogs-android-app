package com.abhijit.cats_vs_dogs.ui


import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.abhijit.cats_vs_dogs.R
import com.abhijit.cats_vs_dogs.camera.CameraManager
import com.abhijit.cats_vs_dogs.ml.YoloEngine
import com.abhijit.cats_vs_dogs.ml.YoloOutputParser
import com.abhijit.cats_vs_dogs.ml.YoloPostProcessorLive
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity() {
    private lateinit var imageView: ImageView
    private lateinit var viewFinder: PreviewView // this to show camera output
    private lateinit var btnUpload: Button
    private lateinit var btnLiveCamera: Button
    private lateinit var resultText: TextView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var overlayView: OverlayView // it act as canvas to draw bounding box
    private var lastInferenceTime = 0L
    private val INFERENCE_INTERVAL_MS = 300 // ~3 FPS
    private lateinit var yoloEngine: YoloEngine
    private lateinit var cameraManager: CameraManager
    private var isLiveCameraRunning = false



    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted! Start the camera
            cameraManager.start(this)
        } else {
            // Permission denied
            resultText.text = "Camera permission is required for live detection"
        }
    }

    // For uploadButton take image from gallery and make inference
    private val getImage = registerForActivityResult<String, Uri?>(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            imageView.imageTintList = null
            imageView.setPadding(0, 0, 0, 0)
            imageView.setImageURI(it)

            val bitmap = ImagePicker.uriToBitmap(this, it)
            if (bitmap != null) {
                classifyImage(bitmap)
            }
        }
    }

    // onCreate function during start of app
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        // Initialize UI : this is what links the code to the UI
        imageView = findViewById(R.id.imageView)
        viewFinder = findViewById(R.id.viewFinder)
        overlayView = findViewById(R.id.overlayView)

        btnUpload = findViewById(R.id.btnUpload)
        btnLiveCamera = findViewById(R.id.btnLiveCamera)
        resultText = findViewById(R.id.resultText)

        // Pre-allocating Objects cause if done in LiveInference it will 30 times -> 30 FPS images passes
        // created once when the app starts and reused forever.
        yoloEngine = YoloEngine(applicationContext) // Initialize model

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Creating a camera object from Camera Manager class
        setupCameraManager()


        btnUpload.setOnClickListener {
            // Show ImageView, Hide Camera
            imageView.visibility = View.VISIBLE
            viewFinder.visibility = View.GONE
            overlayView.visibility = View.GONE

            if (isLiveCameraRunning) {
                cameraManager.stop()
                isLiveCameraRunning = false
            }

            getImage.launch("image/*")
        }

        btnLiveCamera.setOnClickListener {
            // Hide ImageView, Show Camera
            imageView.visibility = View.GONE
            viewFinder.visibility = View.VISIBLE
            overlayView.visibility = View.VISIBLE

            // Check permissions and start camera
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
            ) {
                isLiveCameraRunning = true
                cameraManager.start(this)
            } else {
                requestCameraPermission.launch(Manifest.permission.CAMERA)
            }
        }


    }


    private fun setupCameraManager() {
        // Creating a camera object from Camera Manager class
        cameraManager = CameraManager(
            context = applicationContext,
            previewView = viewFinder,
            cameraExecutor = cameraExecutor
        ) { imageProxy ->

            try {
                val now = System.currentTimeMillis()
                if (now - lastInferenceTime >= INFERENCE_INTERVAL_MS) {
                    lastInferenceTime = now

                    // Convert frame to bitmap
                    val bitmap = imageProxy.toBitmap()

                    // 1 Run inference on the bitmap
                    val rawOutput = yoloEngine.run(bitmap)

                    // 2 Extract information from raw prediction done by tflite
                    val detections = YoloPostProcessorLive.processOutput(
                        rawOutput,
                        overlayView.width,
                        overlayView.height
                    )

                    // 3️ update UI
                    runOnUiThread {
                        overlayView.setResults(detections)
                        overlayView.invalidate()

                        val best = detections.maxByOrNull { it.confidence }
                        resultText.text =
                            if (best != null && best.confidence > 0.5f)
                                "${best.label} (${(best.confidence * 100).toInt()}%)"
                            else
                                "Scanning..."
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                // IMPORTANT: Always close the proxy to receive the next frame
                imageProxy.close()
            }
        }
    }


    // Classify the image using the model
    private fun classifyImage(bitmap: Bitmap) {
        // getting model response
        val output = yoloEngine.run(bitmap)
        // Post-process (Extracting the best result)
        val result = YoloOutputParser.postProcessing(output)

        // 6. update UI
        if (result != null) {
            val (label, confidence) = result
            resultText.text = "Prediction: $label (${(confidence * 100).toInt()}%)"
        } else {
            resultText.text = "Could not identify a Cat or Dog clearly."
        }
    }


    override fun onPause() {
        super.onPause()

        if (isLiveCameraRunning) {
            cameraManager.stop()
            isLiveCameraRunning = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown() // Kills the background thread that runs the camera
        yoloEngine.close()
    }
}


