package com.app.assistant.ui.screen

import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.app.assistant.camera.VisualBufferManager
import com.app.assistant.camera.imageProxyToRotatedJpeg
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


@Composable
fun CameraPreviewContainer(
    modifier: Modifier = Modifier,
    lensFacing: Int = CameraSelector.LENS_FACING_BACK
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var activeCamera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    var tapPoint by remember { mutableStateOf<Offset?>(null) }

    val focusRingAlpha = remember { Animatable(0f) }
    val focusRingScale = remember { Animatable(1f) }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(lifecycleOwner, lensFacing) {
        var cameraExecutor: java.util.concurrent.ExecutorService? = null
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    val executor = Executors.newSingleThreadExecutor()
                    cameraExecutor = executor

                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()

                            val preview = Preview.Builder().build().apply {
                                setSurfaceProvider(previewView.surfaceProvider)
                            }

                            val resolutionSelector = ResolutionSelector.Builder()
                                .setResolutionStrategy(
                                    ResolutionStrategy(
                                        Size(640, 480),
                                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                                    )
                                )
                                .build()

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setResolutionSelector(resolutionSelector)
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            var lastFrameTimestamp = 0L

                            imageAnalysis.setAnalyzer(executor) { imageProxy ->
                                val now = System.currentTimeMillis()
                                if (now - lastFrameTimestamp < 150) {
                                    imageProxy.close()
                                    return@setAnalyzer
                                }
                                lastFrameTimestamp = now

                                val jpegBytes = imageProxyToRotatedJpeg(imageProxy)
                                imageProxy.close()

                                if (jpegBytes != null) {
                                    val cachedFrame = com.app.assistant.camera.CachedFrame(jpegBytes, now)
                                    VisualBufferManager.addFrame(cachedFrame)
                                }
                            }

                            val cameraSelector = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                CameraSelector.DEFAULT_BACK_CAMERA
                            } else {
                                CameraSelector.DEFAULT_FRONT_CAMERA
                            }

                            cameraProvider.unbindAll()
                            val camera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis
                            )
                            activeCamera = camera
                        } catch (e: Exception) {
                            Log.e("CameraPreview", "Use case binding failed", e)
                        }
                    }, ContextCompat.getMainExecutor(context))
                }
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    try {
                        if (cameraProviderFuture.isDone) {
                            val cameraProvider = cameraProviderFuture.get()
                            cameraProvider.unbindAll()
                        }
                    } catch (e: Exception) {
                        Log.e("CameraPreview", "Error unbinding on ON_PAUSE", e)
                    }
                    cameraExecutor?.shutdown()
                    cameraExecutor = null
                    activeCamera = null
                    VisualBufferManager.clear()
                }
                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try {
                if (cameraProviderFuture.isDone) {
                    val cameraProvider = cameraProviderFuture.get()
                    cameraProvider.unbindAll()
                }
            } catch (e: Exception) {
                Log.e("CameraPreview", "Error unbinding on dispose", e)
            }
            cameraExecutor?.shutdown()
            activeCamera = null
            VisualBufferManager.clear()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(activeCamera) {
                    detectTapGestures { offset ->
                        val camera = activeCamera ?: return@detectTapGestures
                        tapPoint = offset

                        coroutineScope.launch {
                            focusRingAlpha.snapTo(1f)
                            focusRingScale.snapTo(1.5f)

                            focusRingScale.animateTo(1f, tween(300))
                            focusRingAlpha.animateTo(0f, tween(400, delayMillis = 100))
                        }

                        val factory = previewView.meteringPointFactory
                        val point = factory.createPoint(offset.x, offset.y)
                        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                            .setAutoCancelDuration(5, TimeUnit.SECONDS)
                            .build()
                        camera.cameraControl.startFocusAndMetering(action)
                    }
                }
        )

        tapPoint?.let { point ->
            val scale = focusRingScale.value
            val alpha = focusRingAlpha.value
            if (alpha > 0f) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .graphicsLayer {
                            translationX = point.x - size.width / 2f
                            translationY = point.y - size.height / 2f
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                        }
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                )
            }
        }
    }
}
