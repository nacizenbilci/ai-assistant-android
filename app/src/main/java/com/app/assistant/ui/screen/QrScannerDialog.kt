package com.app.assistant.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

// The URL where users can securely import and export their keys as a QR code
const val EXPORT_KEYS_WEBSITE_URL = "https://example.com/import-keys"

@Composable
fun QrScannerDialog(
    onDismissRequest: () -> Unit,
    onCodeScanned: (String) -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
        }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (hasCameraPermission) {
                ScannerCameraPreview(
                    onCodeScanned = onCodeScanned,
                    onDismissRequest = onDismissRequest
                )
            } else {
                PermissionDeniedContent(
                    onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onClose = onDismissRequest
                )
            }
        }
    }
}

@Composable
fun ScannerCameraPreview(
    onCodeScanned: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uriHandler = LocalUriHandler.current
    var isScanned by remember { mutableStateOf(false) }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    // Camera Lifecycle & Setup
    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val executor = Executors.newSingleThreadExecutor()

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().apply {
                    setSurfaceProvider(previewView.surfaceProvider)
                }

                // Analyze frames with ML Kit Barcode Scanner
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(
                    executor,
                    QrCodeAnalyzer { result ->
                        if (!isScanned) {
                            isScanned = true
                            // Trigger callback on main thread
                            ContextCompat.getMainExecutor(context).execute {
                                onCodeScanned(result)
                            }
                        }
                    }
                )

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )

            } catch (e: Exception) {
                Log.e("QrScannerDialog", "CameraX UseCase binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            try {
                if (cameraProviderFuture.isDone) {
                    val cameraProvider = cameraProviderFuture.get()
                    cameraProvider.unbindAll()
                }
            } catch (e: Exception) {
                Log.e("QrScannerDialog", "Error unbinding CameraX", e)
            }
            executor.shutdown()
        }
    }

    // Camera Feed Underlay
    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize()
    )

    // Scanner overlay (Canvas with square cutout + neon green frame + scanning line)
    val infiniteTransition = rememberInfiniteTransition(label = "laser")
    val laserPosition by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laserPosition"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val boxWidth = canvasWidth * 0.7f
        val boxHeight = boxWidth // Square viewfinder
        
        val left = (canvasWidth - boxWidth) / 2
        val top = (canvasHeight - boxHeight) / 2
        
        // Draw transparent black mask with cutout
        val path = Path().apply {
            addRect(Rect(0f, 0f, canvasWidth, canvasHeight))
        }
        val cutoutPath = Path().apply {
            addRoundRect(
                RoundRect(
                    left = left,
                    top = top,
                    right = left + boxWidth,
                    bottom = top + boxHeight,
                    cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx())
                )
            )
        }
        
        val finalPath = Path.combine(PathOperation.Difference, path, cutoutPath)
        drawPath(finalPath, color = Color.Black.copy(alpha = 0.65f))
        
        // Viewfinder Frame Outline
        drawRoundRect(
            color = Color(0xFF00E676), // Neon Green
            topLeft = Offset(left, top),
            size = androidx.compose.ui.geometry.Size(boxWidth, boxHeight),
            cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx()),
            style = Stroke(width = 3.dp.toPx())
        )

        // Viewfinder Target Breathing Line (Red Laser)
        val laserY = top + (boxHeight * laserPosition)
        drawLine(
            color = Color(0xFFFF1744), // Bright Red
            start = Offset(left + 15.dp.toPx(), laserY),
            end = Offset(left + boxWidth - 15.dp.toPx(), laserY),
            strokeWidth = 2.dp.toPx()
        )
    }

    // UI Controls Overlay
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Close Button
        IconButton(
            onClick = onDismissRequest,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close Scanner",
                tint = Color.White
            )
        }

        // Instructions Card
        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 70.dp)
                .statusBarsPadding()
                .fillMaxWidth(0.9f),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E1E2C).copy(alpha = 0.9f)
            ),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Import API Keys Safely",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "To import your API keys securely, visit this URL on your desktop/other device:",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = EXPORT_KEYS_WEBSITE_URL,
                    color = Color(0xFF29B6F6), // Sky Blue Link Color
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    textDecoration = TextDecoration.Underline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clickable {
                            try {
                                uriHandler.openUri(EXPORT_KEYS_WEBSITE_URL)
                            } catch (e: Exception) {
                                Log.e("QrScannerDialog", "Failed to open URI", e)
                            }
                        }
                        .padding(4.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Align the QR code on the screen within the frame to scan.",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun PermissionDeniedContent(
    onRequestPermission: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = "Warning",
            tint = Color(0xFFFFB300),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Camera Permission Required",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "We need access to your camera to scan QR codes and safely import API keys.",
            color = Color.LightGray,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF00E676),
                contentColor = Color.Black
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Grant Permission", fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onClose) {
            Text("Cancel", color = Color.White)
        }
    }
}

class QrCodeAnalyzer(
    private val onQrCodeScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .build()

    private val scanner = BarcodeScanning.getClient(options)

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        val rawValue = barcode.rawValue
                        if (rawValue != null && rawValue.isNotBlank()) {
                            onQrCodeScanned(rawValue)
                            break
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("QrCodeAnalyzer", "Barcode scanning failed", e)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}
