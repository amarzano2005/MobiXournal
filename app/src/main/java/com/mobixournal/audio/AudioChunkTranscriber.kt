package com.mobixournal.audio

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Sequentially transcribes [AudioChunk]s into [TranscriptionSegment]s with real millisecond timestamps.
 */
object AudioChunkTranscriber {

    /**
     * Transcribes [chunks] sequentially, emitting progress and returning chronological segments.
     */
    suspend fun transcribeChunks(
        context: Context,
        chunks: List<AudioChunk>,
        language: String,
        onProgress: (current: Int, total: Int) -> Unit,
        isCancelled: () -> Boolean,
    ): List<TranscriptionSegment> {
        val results = mutableListOf<TranscriptionSegment>()
        val total = chunks.size

        for ((index, chunk) in chunks.withIndex()) {
            if (isCancelled()) break
            onProgress(index + 1, total)

            val text = withContext(Dispatchers.Main) {
                transcribeSingleChunk(context, chunk, language, isCancelled)
            }

            if (!text.isNullOrBlank()) {
                results.add(
                    TranscriptionSegment(
                        startTimeMs = chunk.startMs,
                        endTimeMs = chunk.endMs,
                        text = text.trim(),
                        confidence = 0.9,
                    ),
                )
            }
            delay(120) // Brief rest between recognizer sessions to avoid ERROR_RECOGNIZER_BUSY
        }

        return results
    }

    private suspend fun transcribeSingleChunk(
        context: Context,
        chunk: AudioChunk,
        language: String,
        isCancelled: () -> Boolean,
    ): String? = suspendCancellableCoroutine { continuation ->
        if (isCancelled()) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        val pipe = runCatching { ParcelFileDescriptor.createPipe() }.getOrNull()
        if (pipe == null) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        val readSide = pipe[0]
        val writeSide = pipe[1]

        val recognizer = runCatching {
            SpeechRecognizer.createSpeechRecognizer(context)
        }.getOrNull() ?: runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else null
        }.getOrNull()

        if (recognizer == null) {
            runCatching { writeSide.close() }
            runCatching { readSide.close() }
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        var resumed = false
        val cleanUp: () -> Unit = {
            runCatching { writeSide.close() }
            runCatching { readSide.close() }
            runCatching { recognizer.destroy() }
        }

        fun safeResume(result: String?) {
            if (!resumed) {
                resumed = true
                cleanUp()
                if (continuation.isActive) continuation.resume(result)
            }
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, readSide)
            putExtra("android.speech.extra.AUDIO_INJECT_SOURCE", readSide)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, 16000)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 2)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4_000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3_000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1_500L)
        }

        val handler = Handler(Looper.getMainLooper())
        val timeoutMs = (chunk.durationMs * 35L / 50L) + 6_000L
        val timeoutRunnable = Runnable { safeResume(null) }
        handler.postDelayed(timeoutRunnable, timeoutMs.coerceAtLeast(6_000L))

        var accumulatedText = ""

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                handler.removeCallbacks(timeoutRunnable)
                safeResume(accumulatedText.ifBlank { null })
            }
            override fun onResults(results: Bundle?) {
                handler.removeCallbacks(timeoutRunnable)
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull { it.isNotBlank() } ?: accumulatedText
                safeResume(text.ifBlank { null })
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull { it.isNotBlank() }
                if (!text.isNullOrBlank()) {
                    accumulatedText = text.trim()
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        continuation.invokeOnCancellation {
            handler.removeCallbacks(timeoutRunnable)
            safeResume(null)
        }

        runCatching {
            recognizer.startListening(intent)
            pumpChunkToPipe(chunk.pcm, writeSide) { !resumed && !isCancelled() }
        }.onFailure {
            handler.removeCallbacks(timeoutRunnable)
            safeResume(null)
        }
    }

    private fun pumpChunkToPipe(
        pcm: ByteArray,
        writeSide: ParcelFileDescriptor,
        isActive: () -> Boolean,
    ) {
        Thread({
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(writeSide).use { stream ->
                    val chunkSize = 1600 // 50ms chunk
                    var offset = 0
                    while (offset < pcm.size && isActive()) {
                        val count = minOf(chunkSize, pcm.size - offset)
                        stream.write(pcm, offset, count)
                        stream.flush()
                        offset += count
                        Thread.sleep(35)
                    }
                }
            } catch (_: Exception) {
            } finally {
                runCatching { writeSide.close() }
            }
        }, "chunk-pcm-pump").start()
    }
}
