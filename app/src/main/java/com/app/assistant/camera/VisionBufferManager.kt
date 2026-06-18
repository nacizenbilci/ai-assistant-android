package com.app.assistant.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.util.Collections
import java.util.ArrayList

data class CachedFrame(val jpegBytes: ByteArray, val timestamp: Long)

object VisionBufferManager {
    private val buffer = Collections.synchronizedList(ArrayList<CachedFrame>())
    private const val MAX_SIZE = 15

    fun addFrame(frame: CachedFrame) {
        synchronized(buffer) {
            buffer.add(frame)
            if (buffer.size > MAX_SIZE) {
                buffer.removeAt(0)
            }
        }
    }

    fun getOptimalFrame(speechStartTimestamp: Long): CachedFrame? {
        synchronized(buffer) {
            if (buffer.isEmpty()) return null
            var optimalFrame = buffer[0]
            var minDiff = Math.abs(optimalFrame.timestamp - speechStartTimestamp)
            for (i in 1 until buffer.size) {
                val frame = buffer[i]
                val diff = Math.abs(frame.timestamp - speechStartTimestamp)
                if (diff < minDiff) {
                    minDiff = diff
                    optimalFrame = frame
                }
            }
            return optimalFrame
        }
    }

    fun clear() {
        buffer.clear()
    }
}

fun imageProxyToRotatedJpeg(imageProxy: ImageProxy): ByteArray? {
    try {
        val yBuffer = imageProxy.planes[0].buffer
        val uBuffer = imageProxy.planes[1].buffer
        val vBuffer = imageProxy.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + ySize / 2)
        yBuffer.get(nv21, 0, ySize)

        val uRowStride = imageProxy.planes[1].rowStride
        val uPixelStride = imageProxy.planes[1].pixelStride
        val vRowStride = imageProxy.planes[2].rowStride
        val vPixelStride = imageProxy.planes[2].pixelStride

        val width = imageProxy.width
        val height = imageProxy.height

        var pos = ySize
        for (row in 0 until height / 2) {
            for (col in 0 until width / 2) {
                val uIndex = row * uRowStride + col * uPixelStride
                val vIndex = row * vRowStride + col * vPixelStride
                
                if (vIndex < vBuffer.capacity() && uIndex < uBuffer.capacity()) {
                    nv21[pos++] = vBuffer.get(vIndex)
                    nv21[pos++] = uBuffer.get(uIndex)
                }
            }
        }

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 80, out)
        val jpegBytes = out.toByteArray()

        val rotation = imageProxy.imageInfo.rotationDegrees
        if (rotation == 0) {
            return jpegBytes
        }

        val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size) ?: return null
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        bitmap.recycle()

        val rotatedOut = ByteArrayOutputStream()
        rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, rotatedOut)
        rotatedBitmap.recycle()
        return rotatedOut.toByteArray()
    } catch (e: Exception) {
        android.util.Log.e("VisionBufferManager", "Error converting ImageProxy to rotated JPEG", e)
        return null
    }
}
