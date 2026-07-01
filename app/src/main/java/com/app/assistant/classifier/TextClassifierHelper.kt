package com.app.assistant.classifier

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textclassifier.TextClassifier
import com.google.mediapipe.tasks.text.textclassifier.TextClassifierResult
import java.util.concurrent.ScheduledThreadPoolExecutor

class TextClassifierHelper(
    var currentModel: String = MOBILEBERT,
    val context: Context,
    val listener: TextResultsListener,
) {
    private lateinit var textClassifier: TextClassifier
    private val executor = ScheduledThreadPoolExecutor(1)

    init {
        executor.execute {
            initClassifier()
        }
    }

    fun initClassifier() {
        val baseOptionsBuilder =
            BaseOptions
                .builder()
                .setModelAssetPath(currentModel)

        try {
            val baseOptions = baseOptionsBuilder.build()
            val optionsBuilder =
                TextClassifier.TextClassifierOptions
                    .builder()
                    .setBaseOptions(baseOptions)
            val options = optionsBuilder.build()
            textClassifier = TextClassifier.createFromOptions(context, options)
        } catch (e: Exception) {
            listener.onError(
                "Text classifier failed to initialize. See error logs for details"
            )
            Log.e(
                TAG,
                "Text classifier failed to load the task with error: " + e.message,
                e
            )
        }
    }

    // Run text classification using MediaPipe Text Classifier API
    fun classify(
        text: String,
        itemId: Long,
        loadingItemId: Long,
        speak: Boolean,
    ) {
        executor.execute {
            if (!::textClassifier.isInitialized) {
                listener.onError("Text classifier not initialized", itemId, loadingItemId, speak)
                return@execute
            }

            // inferenceTime is the amount of time, in milliseconds, that it takes to
            // classify the input text.
            var inferenceTime = SystemClock.uptimeMillis()

            // Remove all punctuation before classifying
            val cleanedText = text.replace(Regex("[.,?!]"), "")
            try {
                val results = textClassifier.classify(cleanedText)

                inferenceTime = SystemClock.uptimeMillis() - inferenceTime

                listener.onResult(results, inferenceTime, text, itemId, loadingItemId, speak)
            } catch (e: Exception) {
                Log.e(TAG, "Error running classification: ${e.message}", e)
                listener.onError("Classification failed: ${e.message}", itemId, loadingItemId, speak)
            }
        }
    }

    fun shutDown() {
        try {
            executor.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down executor", e)
        }
    }

    interface TextResultsListener {
        fun onError(error: String)

        fun onError(
            error: String,
            itemId: Long,
            loadingItemId: Long,
            speak: Boolean
        ) {
            onError(error)
        }

        fun onResult(
            results: TextClassifierResult,
            inferenceTime: Long,
            inputText: String,
            itemId: Long,
            loadingItemId: Long,
            speak: Boolean,
        )
    }

    companion object {
        const val TAG = "TextClassifierHelper"
        const val MOBILEBERT = "model.tflite"
    }
}
