package com.mobixournal.audio

import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import java.io.File

/**
 * On-device Whisper speech-to-text inference engine using sherpa-onnx.
 *
 * Runs locally on CPU/NNPACK without network requests, producing fast transcriptions
 * with word-level or chunk-level timestamp alignment.
 */
class WhisperOfflineTranscriber(
    private val modelDir: File,
    private val isTiny: Boolean = true,
) : AutoCloseable {

    private var recognizer: OfflineRecognizer? = null

    /**
     * Initializes the Whisper offline recognizer from [modelDir], using every core the device reports
     * unless a different count is asked for ([inferenceThreads]).
     */
    fun init(language: String = "it", numThreads: Int = inferenceThreads()): Boolean {
        return runCatching {
            val prefix = if (isTiny) "tiny" else "base"
            val encoder = File(modelDir, "$prefix-encoder.int8.onnx")
            val decoder = File(modelDir, "$prefix-decoder.int8.onnx")
            val tokens = File(modelDir, "$prefix-tokens.txt")

            if (!encoder.exists() || !decoder.exists() || !tokens.exists()) {
                return false
            }

            val langCode = when (language.lowercase().take(2)) {
                "it" -> "it"
                "en" -> "en"
                "es" -> "es"
                "fr" -> "fr"
                "de" -> "de"
                else -> "auto"
            }

            val whisperConfig = OfflineWhisperModelConfig(
                encoder = encoder.absolutePath,
                decoder = decoder.absolutePath,
                language = langCode,
                task = "transcribe",
            )

            val modelConfig = OfflineModelConfig().apply {
                whisper = whisperConfig
                this.tokens = tokens.absolutePath
                this.numThreads = numThreads.coerceIn(1, 8)
                provider = "cpu"
                modelType = "whisper"
            }

            val config = OfflineRecognizerConfig().apply {
                this.modelConfig = modelConfig
            }

            recognizer = OfflineRecognizer(assetManager = null, config = config)
            true
        }.getOrDefault(false)
    }

    /**
     * Transcribes 16 kHz 16-bit mono PCM [pcmBytes] into text.
     */
    fun transcribePcm(pcmBytes: ByteArray, sampleRate: Int = 16000): String {
        val rec = recognizer ?: return ""
        if (pcmBytes.size < 2) return ""

        val samples = FloatArray(pcmBytes.size / 2)
        for (i in samples.indices) {
            val low = pcmBytes[i * 2].toInt() and 0xFF
            val high = pcmBytes[i * 2 + 1].toInt()
            val sample16 = ((high shl 8) or low).toShort()
            samples[i] = sample16 / 32768.0f
        }

        val stream = rec.createStream()
        return try {
            stream.acceptWaveform(samples, sampleRate)
            rec.decode(stream)
            val result = rec.getResult(stream)
            result.text.trim()
        } finally {
            stream.release()
        }
    }

    /**
     * Transcribes every [window] with [onProgress] reporting, one decode each.
     *
     * The windows — not the VAD's phrases — are the unit of work, because Whisper pads every input to
     * its 30-second encoder window ([TranscriptionWindows]): decoding the phrases one by one paid that
     * cost per phrase. The text of a window goes back to the phrases it covers through
     * [TranscriptionWindows.distributeText], so segments keep their real timestamps.
     */
    fun transcribeWindows(
        windows: List<AudioWindow>,
        onProgress: (current: Int, total: Int) -> Unit,
        isCancelled: () -> Boolean = { false },
    ): List<TranscriptionSegment> {
        val results = mutableListOf<TranscriptionSegment>()
        val total = windows.size
        for ((index, window) in windows.withIndex()) {
            if (isCancelled()) break
            onProgress(index + 1, total)
            val text = transcribePcm(window.pcm(), 16000)
            if (text.isNotBlank()) results.addAll(TranscriptionWindows.distributeText(window, text))
        }
        return results
    }

    override fun close() {
        recognizer?.release()
        recognizer = null
    }

    companion object {
        /**
         * Inference threads for the recognizer: every core the device reports, capped at [MAX_THREADS].
         *
         * The count used to be a literal 4, which left half of an eight-core tablet unused. Above six
         * threads onnxruntime spends more time in contention and on the little cores than it gains back,
         * and a long decode is also a thermal question, so the cap stays at six rather than
         * `availableProcessors` outright.
         */
        const val MAX_THREADS = 6

        fun inferenceThreads(cores: Int = Runtime.getRuntime().availableProcessors()): Int =
            cores.coerceIn(2, MAX_THREADS)
    }
}
