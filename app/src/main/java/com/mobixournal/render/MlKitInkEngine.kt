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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * High-accuracy handwriting recognizer powered by Google ML Kit Digital Ink Recognition.
 * Supports English as the primary language and includes major languages such as Italian,
 * Spanish, French, and German.
 */
object MlKitInkEngine {

    private const val TAG = "MlKitInkEngine"
    private val isDownloading = AtomicBoolean(false)
    private val cachedModels = ConcurrentHashMap<String, DigitalInkRecognitionModel>()
    private val cachedRecognizers = ConcurrentHashMap<String, DigitalInkRecognizer>()
    @Volatile private var isModelReady = false
    @Volatile private var lastAvailabilityCheckTime = 0L
    @Volatile private var lastAvailabilityResult = false
    private val modelReadyListeners = mutableListOf<() -> Unit>()

    /** Major language tags supported for handwriting recognition. */
    val MAJOR_LANGUAGE_TAGS = listOf("en-US", "it-IT", "es-ES", "fr-FR", "de-DE")

    /**
     * Returns the list of active language tags, prioritizing English and Italian,
     * along with the device's default locale and other major languages.
     */
    fun activeLanguageTags(): List<String> {
        val deviceLocale = Locale.getDefault()
        val deviceTag = if (deviceLocale.country.isNotEmpty()) {
            "${deviceLocale.language}-${deviceLocale.country}"
        } else {
            deviceLocale.language
        }
        return (listOf("en-US", "it-IT", deviceTag) + MAJOR_LANGUAGE_TAGS).distinct()
    }

    /**
     * Registers a listener to be invoked when any model is downloaded and ready for inference.
     */
    fun addOnModelReadyListener(listener: () -> Unit) {
        synchronized(modelReadyListeners) {
            if (isModelReady) {
                listener()
            } else {
                modelReadyListeners.add(listener)
            }
        }
    }

    private fun notifyModelReady() {
        isModelReady = true
        val listeners = synchronized(modelReadyListeners) {
            val copy = ArrayList(modelReadyListeners)
            modelReadyListeners.clear()
            copy
        }
        for (listener in listeners) {
            try { listener() } catch (_: Exception) {}
        }
    }

    /**
     * Resolves the model identifier for a given language tag, falling back to base language if needed.
     */
    fun resolveModelIdentifier(tag: String): DigitalInkRecognitionModelIdentifier? {
        DigitalInkRecognitionModelIdentifier.fromLanguageTag(tag)?.let { return it }
        val baseLang = tag.substringBefore('-')
        if (baseLang.isNotEmpty() && baseLang != tag) {
            DigitalInkRecognitionModelIdentifier.fromLanguageTag(baseLang)?.let { return it }
        }
        return null
    }

    /**
     * Resolves the primary model identifier for the given locale or English fallback.
     */
    fun resolveModelIdentifier(locale: Locale = Locale.getDefault()): DigitalInkRecognitionModelIdentifier? {
        val fullTag = if (locale.country.isNotEmpty()) "${locale.language}-${locale.country}" else locale.language
        return resolveModelIdentifier(fullTag)
            ?: resolveModelIdentifier("en-US")
            ?: resolveModelIdentifier("it-IT")
    }

    private fun isMainThread(): Boolean =
        try {
            android.os.Looper.myLooper() != null && android.os.Looper.myLooper() == android.os.Looper.getMainLooper()
        } catch (_: Exception) {
            false
        }

    /**
     * Checks if at least one recognition model is available on device and ready for immediate inference.
     * Throttles remote checks so offline indexing never spends repetitive timeouts per word.
     */
    fun isAvailable(): Boolean {
        if (isModelReady) return true
        if (isMainThread()) return false
        val now = System.currentTimeMillis()
        if (now - lastAvailabilityCheckTime < 10_000L) {
            return lastAvailabilityResult
        }
        return try {
            val modelManager = RemoteModelManager.getInstance()
            val priorityTags = listOf("en-US", "it-IT")
            for (tag in priorityTags) {
                val model = getOrCreateModel(tag) ?: continue
                try {
                    val task = modelManager.isModelDownloaded(model)
                    val downloaded = Tasks.await(task, 250, TimeUnit.MILLISECONDS) == true
                    if (downloaded) {
                        lastAvailabilityCheckTime = now
                        lastAvailabilityResult = true
                        notifyModelReady()
                        return true
                    }
                } catch (_: Throwable) {}
            }
            lastAvailabilityCheckTime = now
            lastAvailabilityResult = false
            false
        } catch (_: Throwable) {
            lastAvailabilityCheckTime = now
            lastAvailabilityResult = false
            false
        }
    }

    /**
     * Asynchronously ensures the models for English, Italian, and the current locale are downloaded.
     */
    fun ensureModelDownloaded(onComplete: ((Boolean) -> Unit)? = null) {
        try {
            val modelManager = RemoteModelManager.getInstance()
            val tagsToDownload = listOf("en-US", "it-IT").let { base ->
                val devTag = Locale.getDefault().toLanguageTag()
                (base + devTag).distinct()
            }
            var anySuccess = false
            val remaining = java.util.concurrent.atomic.AtomicInteger(tagsToDownload.size)

            for (tag in tagsToDownload) {
                val model = getOrCreateModel(tag)
                if (model == null) {
                    if (remaining.decrementAndGet() == 0) onComplete?.invoke(anySuccess)
                    continue
                }
                modelManager.isModelDownloaded(model)
                    .addOnSuccessListener { downloaded ->
                        if (downloaded) {
                            anySuccess = true
                            notifyModelReady()
                            if (remaining.decrementAndGet() == 0) onComplete?.invoke(anySuccess)
                        } else {
                            Log.i(TAG, "Initiating ML Kit Digital Ink model download for $tag...")
                            modelManager.download(model, DownloadConditions.Builder().build())
                                .addOnSuccessListener {
                                    anySuccess = true
                                    notifyModelReady()
                                    Log.i(TAG, "ML Kit Digital Ink model download complete for $tag.")
                                    if (remaining.decrementAndGet() == 0) onComplete?.invoke(anySuccess)
                                }
                                .addOnFailureListener { e ->
                                    Log.w(TAG, "ML Kit Digital Ink model download failed for $tag", e)
                                    if (remaining.decrementAndGet() == 0) onComplete?.invoke(anySuccess)
                                }
                        }
                    }
                    .addOnFailureListener {
                        if (remaining.decrementAndGet() == 0) onComplete?.invoke(anySuccess)
                    }
            }
        } catch (_: Throwable) {
            onComplete?.invoke(false)
        }
    }

    /**
     * Recognizes text from a group of strokes forming a word or phrase across available language models.
     * Returns candidate interpretations from English and major languages (including Italian),
     * highest confidence first.
     */
    fun recognizeWord(strokes: List<Stroke>): List<String> {
        if (isMainThread()) {
            Log.w(TAG, "Cannot call Tasks.await on main thread; using fallback recognizer")
            return emptyList()
        }
        val recognizers = getAvailableRecognizers()
        if (recognizers.isEmpty()) return emptyList()
        val ink = buildInk(strokes) ?: return emptyList()

        val allCandidates = mutableListOf<String>()
        for (recognizer in recognizers) {
            try {
                val task = recognizer.recognize(ink)
                val result = Tasks.await(task, 1500, TimeUnit.MILLISECONDS)
                for (cand in result.candidates) {
                    val text = cand.text.trim()
                    if (text.isNotEmpty() && text !in allCandidates) {
                        allCandidates.add(text)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "ML Kit recognition error", e)
            }
        }
        return allCandidates
    }

    private fun getAvailableRecognizers(): List<DigitalInkRecognizer> {
        return try {
            val modelManager = RemoteModelManager.getInstance()
            val list = mutableListOf<DigitalInkRecognizer>()
            for (tag in activeLanguageTags()) {
                val model = getOrCreateModel(tag) ?: continue
                val existing = cachedRecognizers[tag]
                if (existing != null) {
                    list.add(existing)
                } else {
                    try {
                        val task = modelManager.isModelDownloaded(model)
                        if (Tasks.await(task, 100, TimeUnit.MILLISECONDS) == true) {
                            val options = DigitalInkRecognizerOptions.builder(model).build()
                            val newRecognizer = DigitalInkRecognition.getClient(options)
                            cachedRecognizers[tag] = newRecognizer
                            list.add(newRecognizer)
                        }
                    } catch (_: Throwable) {}
                }
            }
            list
        } catch (_: Throwable) {
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
                simTime += 10L
            }
            inkBuilder.addStroke(strokeBuilder.build())
            simTime += 150L // Inter-stroke pause for natural gesture segmentation
        }
        val ink = inkBuilder.build()
        return if (ink.strokes.isEmpty()) null else ink
    }

    fun getOrCreateModel(tag: String = "en-US"): DigitalInkRecognitionModel? {
        cachedModels[tag]?.let { return it }
        val identifier = resolveModelIdentifier(tag) ?: return null
        val model = DigitalInkRecognitionModel.builder(identifier).build()
        cachedModels[tag] = model
        return model
    }
}
