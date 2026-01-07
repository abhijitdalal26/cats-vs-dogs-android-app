package com.abhijit.cats_vs_dogs.camera

import android.content.Context
import android.util.Size
import androidx.camera.core.*

import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService


// Taking camera feed
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.ImageProxy

class CameraManager(
    private val context: Context,
    private val previewView: PreviewView,
    private val cameraExecutor: ExecutorService,
    private val analyzer: (ImageProxy) -> Unit
) {

    private var cameraProvider: ProcessCameraProvider? = null

    fun start(lifecycleOwner: LifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({

            cameraProvider = cameraProviderFuture.get()

            // 1. Preview: Displays the live feed on Image window will has 30–60 FPS
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
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

            // 3️ Analyzer hook (NO UI / NO ML HERE)
            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                analyzer(imageProxy)
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }

        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        cameraProvider?.unbindAll()
    }
}
