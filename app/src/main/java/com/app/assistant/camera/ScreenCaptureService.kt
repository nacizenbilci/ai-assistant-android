package com.app.assistant.camera

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.app.PendingIntent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.app.assistant.R
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

object ScreenCaptureServiceHelper {
    var onServiceStopped: (() -> Unit)? = null
    var onToggleMuteRequested: (() -> Unit)? = null
    var onToggleHandsFreeRequested: (() -> Unit)? = null

    @Volatile
    var isMicMuted: Boolean = false
    @Volatile
    var isHandsFreeActive: Boolean = false

    var onStateChanged: (() -> Unit)? = null
}

class ScreenCaptureService : Service() {

    companion object {
        const val ACTION_START = "com.app.assistant.action.START_SCREEN_CAPTURE"
        const val ACTION_STOP = "com.app.assistant.action.STOP_SCREEN_CAPTURE"
        const val ACTION_TOGGLE_MUTE = "com.app.assistant.action.TOGGLE_MUTE"
        const val ACTION_TOGGLE_HANDS_FREE = "com.app.assistant.action.TOGGLE_HANDS_FREE"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        private const val NOTIFICATION_ID = 5001
        private const val CHANNEL_ID = "screen_capture_channel"
        private const val TAG = "ScreenCaptureService"
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var lastFrameTime = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        handlerThread = HandlerThread("ScreenCaptureThread").apply {
            start()
        }
        backgroundHandler = Handler(handlerThread!!.looper)

        ScreenCaptureServiceHelper.onStateChanged = {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, buildNotification())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopServiceInternal()
            return START_NOT_STICKY
        }

        if (action == ACTION_TOGGLE_MUTE) {
            ScreenCaptureServiceHelper.onToggleMuteRequested?.invoke()
            return START_NOT_STICKY
        }

        if (action == ACTION_TOGGLE_HANDS_FREE) {
            ScreenCaptureServiceHelper.onToggleHandsFreeRequested?.invoke()
            return START_NOT_STICKY
        }

        if (action == ACTION_START) {
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
            val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_RESULT_DATA)
            }

            if (resultCode != 0 && resultData != null) {
                startForegroundNotification()
                startScreenCapture(resultCode, resultData)
            } else {
                Log.e(TAG, "Invalid result code or data, stopping service")
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Capture Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification for Screen Share Mode"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification = buildNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val isMuted = ScreenCaptureServiceHelper.isMicMuted
        val isHandsFree = ScreenCaptureServiceHelper.isHandsFreeActive

        val muteText = if (isMuted) "Unmute" else "Mute"
        val muteIcon = if (isMuted) R.drawable.ic_mic else R.drawable.ic_mic_off

        val handsFreeText = if (isHandsFree) "Close Hands-Free" else "Start Hands-Free"
        val handsFreeIcon = R.drawable.ic_mic

        val statusText = when {
            isMuted -> "Microphone Muted"
            isHandsFree -> "Hands-Free Active"
            else -> "Capturing screen for interactions"
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val stopIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(this, 1, stopIntent, flags)

        val muteIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ACTION_TOGGLE_MUTE
        }
        val mutePendingIntent = PendingIntent.getService(this, 2, muteIntent, flags)

        val handsFreeIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ACTION_TOGGLE_HANDS_FREE
        }
        val handsFreePendingIntent = PendingIntent.getService(this, 3, handsFreeIntent, flags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Sharing Active")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_screen_share)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(muteIcon, muteText, mutePendingIntent)
            .addAction(handsFreeIcon, handsFreeText, handsFreePendingIntent)
            .addAction(R.drawable.ic_stop, "Stop Screen", stopPendingIntent)
            .build()
    }

    private fun startScreenCapture(resultCode: Int, resultData: Intent) {
        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        try {
            val projection = mpManager.getMediaProjection(resultCode, resultData)
            mediaProjection = projection

            projection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.d(TAG, "MediaProjection stopped by system/user")
                    stopServiceInternal()
                }
            }, backgroundHandler)

            setupCapturePipeline(projection)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start media projection capture", e)
            stopServiceInternal()
        }
    }

    private fun setupCapturePipeline(projection: MediaProjection) {
        val metrics = resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels
        val densityDpi = metrics.densityDpi

        // Aspect ratio calculations scaled down to width ~720px
        val targetWidth = 720
        val targetHeight = (targetWidth * (screenHeight.toFloat() / screenWidth.toFloat())).toInt()
        val targetDensityDpi = (densityDpi * (targetWidth.toFloat() / screenWidth.toFloat())).toInt()

        Log.d(TAG, "Screen size: ${screenWidth}x${screenHeight}. Scaling capture to ${targetWidth}x${targetHeight} at ${targetDensityDpi}dpi")

        // Max images capacity of 2. Format: RGBA_8888.
        val reader = ImageReader.newInstance(
            targetWidth,
            targetHeight,
            PixelFormat.RGBA_8888,
            2
        )
        imageReader = reader

        reader.setOnImageAvailableListener({ r ->
            var image: android.media.Image? = null
            try {
                image = r.acquireLatestImage()
                if (image == null) return@setOnImageAvailableListener

                val now = System.currentTimeMillis()
                if (now - lastFrameTime < 150) {
                    image.close()
                    image = null
                    return@setOnImageAvailableListener
                }
                lastFrameTime = now

                val width = image.width
                val height = image.height
                val plane = image.planes[0]
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * width

                val bitmapWidth = width + rowPadding / pixelStride
                val bitmap = Bitmap.createBitmap(
                    bitmapWidth,
                    height,
                    Bitmap.Config.ARGB_8888
                )
                buffer.position(0)
                bitmap.copyPixelsFromBuffer(buffer)

                // Close image immediately to prevent VirtualDisplay stalling
                image.close()
                image = null

                val finalBitmap = if (rowPadding > 0) {
                    val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                    bitmap.recycle()
                    cropped
                } else {
                    bitmap
                }

                val out = ByteArrayOutputStream()
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                finalBitmap.recycle()

                val jpegBytes = out.toByteArray()
                val cachedFrame = CachedFrame(jpegBytes, now)
                VisualBufferManager.addFrame(cachedFrame)
            } catch (e: Exception) {
                Log.e(TAG, "Error converting image frame", e)
                try {
                    image?.close()
                } catch (ex: Exception) {
                    // Ignore
                }
            }
        }, backgroundHandler)

        // Create VirtualDisplay using ImageReader surface
        virtualDisplay = projection.createVirtualDisplay(
            "ScreenCaptureDisplay",
            targetWidth,
            targetHeight,
            targetDensityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            backgroundHandler
        )
    }

    private fun stopServiceInternal() {
        Log.d(TAG, "Stopping ScreenCaptureService and cleaning up resources")
        try {
            virtualDisplay?.release()
            virtualDisplay = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing virtual display", e)
        }

        try {
            imageReader?.close()
            imageReader = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing image reader", e)
        }

        try {
            mediaProjection?.stop()
            mediaProjection = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping media projection", e)
        }

        try {
            handlerThread?.quitSafely()
            handlerThread = null
            backgroundHandler = null
        } catch (e: Exception) {
            Log.e(TAG, "Error quitting handler thread", e)
        }

        // Clear visual buffer to prevent leaks
        VisualBufferManager.clear()

        ScreenCaptureServiceHelper.onStateChanged = null

        stopForeground(true)
        stopSelf()
        ScreenCaptureServiceHelper.onServiceStopped?.invoke()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopServiceInternal()
    }
}
