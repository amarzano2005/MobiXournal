package com.mobixournal.render

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import com.mobixournal.format.model.Stroke
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * High-accuracy handwriting recognizer powered by Google ML Kit Digital Ink Recognition.
 * Supports cursive and print handwriting across 300+ languages including Italian and English.
 */
object MlKitInkEngine {

    private const val TAG = "MlKitInkEngine"
    private val isDownloading = AtomicBoolean(false)
    private var cachedModel: DigitalInkRecognitionModel? = null
    private var cachedRecognizer: DigitalInkRecognizer? = null
    private var isModelReady = false

    /**
     * Resolves the best model identifier for the given locale or current device locale.
     */
    fun resolveModelIdentifier(locale: Locale = Locale.getDefault()): DigitalInkRecognitionModelIdentifier? {
        val tag = locale.toLanguageTag()
        DigitalInkRecognitionModelIdentifier.fromLanguageTag(tag)?.let { return it }
        val lang = locale.language
        DigitalInkRecognitionModelIdentifier.fromLanguageTag(lang)?.let { return it }
        // Fallbacks
        DigitalInkRecognitionModelIdentifier.fromLanguageTag("it")?.let { return it }
        return DigitalInkRecognitionModelIdentifier.fromLanguageTag("en-US")
    }

    private fun isMainThread(): Boolean =
        try {
            android.os.Looper.myLooper() != null && android.os.Looper.myLooper() == android.os.Looper.getMainLooper()
        } catch (_: Exception) {
            false
        }

    /**
     * Checks if the recognition model is available on device and ready for immediate inference.
     */
    fun isAvailable(): Boolean {
        if (isModelReady) return true
        if (isMainThread()) return false
        val model = getOrCreateModel() ?: return false
        return try {
            val task = RemoteModelManager.getInstance().isModelDownloaded(model)
            val downloaded = Tasks.await(task, 500, TimeUnit.MILLISECONDS) == true
            if (downloaded) {
                isModelReady = true
            }
            downloaded
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Asynchronously ensures the model is downloaded for offline use.
     */
    fun ensureModelDownloaded(onComplete: ((Boolean) -> Unit)? = null) {
        val model = getOrCreateModel() ?: run {
            onComplete?.invoke(false)
            return
        }
        val modelManager = RemoteModelManager.getInstance()
        modelManager.isModelDownloaded(model)
            .addOnSuccessListener { downloaded ->
                if (downloaded) {
                    isModelReady = true
                    onComplete?.invoke(true)
                } else {
                    if (isDownloading.compareAndSet(false, true)) {
                        Log.i(TAG, "Initiating ML Kit Digital Ink model download...")
                        modelManager.download(model, DownloadConditions.Builder().build())
                            .addOnSuccessListener {
                                isModelReady = true
                                isDownloading.set(false)
                                Log.i(TAG, "ML Kit Digital Ink model download complete.")
                                onComplete?.invoke(true)
                            }
                            .addOnFailureListener { e ->
                                isDownloading.set(false)
                                Log.w(TAG, "ML Kit Digital Ink model download failed", e)
                                onComplete?.invoke(false)
                            }
                    } else {
                        onComplete?.invoke(false)
                    }
                }
            }
            .addOnFailureListener {
                onComplete?.invoke(false)
            }
    }

    /**
     * Recognizes text from a group of strokes forming a word or phrase.
     * Returns a list of candidate interpretations (highest confidence first), or empty list on failure.
     */
    fun recognizeWord(strokes: List<Stroke>): List<String> {
        if (isMainThread()) {
            Log.w(TAG, "Cannot call Tasks.await on main thread; using fallback recognizer")
            return emptyList()
        }
        val recognizer = getOrCreateRecognizer() ?: return emptyList()
        val ink = buildInk(strokes) ?: return emptyList()

        return try {
            val task = recognizer.recognize(ink)
            val result = Tasks.await(task, 2500, TimeUnit.MILLISECONDS)
            val candidates = result.candidates.map { it.text.trim() }.filter { it.isNotEmpty() }
            candidates
        } catch (e: Exception) {
            Log.w(TAG, "ML Kit recognition error", e)
            emptyList()
        }
    }

    private fun buildInk(strokes: List<Stroke>): Ink? {
        if (strokes.isEmpty()) return null
        val inkBuilder = Ink.builder()
        var simTime = 0L

        for (stroke in strokes) {
            if (stroke.points.isEmpty()) continue
            val strokeBuilder = Ink.Stroke.builder()
            for (p in stroke.points) {
                strokeBuilder.addPoint(Ink.Point.create(p.x.toFloat(), p.y.toFloat(), simTime))
                simTime += 15L
            }
            inkBuilder.addStroke(strokeBuilder.build())
        }
        val ink = inkBuilder.build()
        return if (ink.strokes.isEmpty()) null else ink
    }

    @Synchronized
    private fun getOrCreateModel(): DigitalInkRecognitionModel? {
        if (cachedModel != null) return cachedModel
        val identifier = resolveModelIdentifier() ?: return null
        cachedModel = DigitalInkRecognitionModel.builder(identifier).build()
        return cachedModel
    }

    @Synchronized
    private fun getOrCreateRecognizer(): DigitalInkRecognizer? {
        if (cachedRecognizer != null) return cachedRecognizer
        val model = getOrCreateModel() ?: return null
        return try {
            val options = DigitalInkRecognizerOptions.builder(model).build()
            cachedRecognizer = DigitalInkRecognition.getClient(options)
            cachedRecognizer
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create DigitalInkRecognizer", e)
            null
        }
    }
}
