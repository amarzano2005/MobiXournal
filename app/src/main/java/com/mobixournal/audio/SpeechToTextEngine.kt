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
import java.io.OutputStream
import java.util.Locale

/**
 * Continuous speech recognizer wrapper using Android's platform [SpeechRecognizer]
 * with persistent session reuse, partial-result preservation across utterance boundaries,
 * audio-clock timestamp alignment, and automatic PCM pipe fallback on Android 13+.
 */
class SpeechToTextEngine(
    private val context: Context,
    private val elapsedMsProvider: (() -> Int)? = null,
) {

    private val handler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var isSessionActive = false
    private var isPaused = false
    private var sessionStartTimeMs = 0L
    private var activeLanguage: String = Locale.getDefault().toLanguageTag()
    private var activeListener: TranscriptionListener? = null
    private var currentUtteranceStartMs = 0
    private var bestPartialText: String = ""
    private var bestPartialEndMs: Int = 0

    /** Whether offline recognition should be preferred when the offline pack is confirmed installed. */
    var preferOffline: Boolean = false

    /** Enabled automatically on API 33+ if concurrent mic access fails with ERROR_AUDIO (3). */
    private var useAudioPipe: Boolean = false
    private var pipeReadPfd: ParcelFileDescriptor? = null
    private var pipeWritePfd: ParcelFileDescriptor? = null
    @Volatile
    private var pipeSink: OutputStream? = null

    /** Listener callback for progressive speech recognition events. */
    interface TranscriptionListener {
        fun onSegmentRecognized(segment: TranscriptionSegment)
        fun onPartialSubtitle(text: String, startMs: Int, endMs: Int) {}
        fun onError(error: String) {}
        fun onFinished() {}
    }

    /**
     * Checks whether speech recognition is available on this platform.
     */
    fun isAvailable(): Boolean =
        SpeechRecognizer.isRecognitionAvailable(context) ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context))

    private fun currentElapsedMs(): Int {
        val provided = elapsedMsProvider?.invoke()
        if (provided != null && provided > 0) return provided
        return (System.currentTimeMillis() - sessionStartTimeMs).toInt().coerceAtLeast(0)
    }

    /**
     * Begins real-time listening and continuous segment transcription for an active recording session.
     */
    fun startListening(
        language: String = Locale.getDefault().toLanguageTag(),
        listener: TranscriptionListener,
    ) {
        if (isSessionActive) return
        isSessionActive = true
        isPaused = false
        sessionStartTimeMs = System.currentTimeMillis()
        activeLanguage = language
        activeListener = listener
        bestPartialText = ""
        bestPartialEndMs = 0
        currentUtteranceStartMs = 0

        if (Looper.myLooper() == Looper.getMainLooper()) {
            startListeningInternal(recreate = false)
        } else {
            handler.post { startListeningInternal(recreate = false) }
        }
    }

    private fun ensureRecognizer(): SpeechRecognizer? {
        speechRecognizer?.let { return it }
        val recognizer = runCatching {
            SpeechRecognizer.createSpeechRecognizer(context)
        }.getOrNull() ?: run {
            android.util.Log.e("SpeechToTextEngine", "Failed to create SpeechRecognizer on this device")
            return null
        }
        recognizer.setRecognitionListener(createRecognitionListener())
        speechRecognizer = recognizer
        return recognizer
    }

    private fun buildIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, activeLanguage)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, activeLanguage)
        putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        if (preferOffline && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        if (useAudioPipe && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            attachAudioPipe(this)
        }
    }

    private fun attachAudioPipe(intent: Intent) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        closeAudioPipe()
        runCatching {
            val pipe = ParcelFileDescriptor.createPipe()
            pipeReadPfd = pipe[0]
            pipeWritePfd = pipe[1]
            pipeSink = ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipe[0])
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, PIPE_SAMPLE_RATE)
        }
    }

    private fun closeAudioPipe() {
        runCatching { pipeSink?.close() }
        pipeSink = null
        runCatching { pipeWritePfd?.close() }
        pipeWritePfd = null
        runCatching { pipeReadPfd?.close() }
        pipeReadPfd = null
    }

    /**
     * Feeds raw 16-bit mono PCM captured by [AudioRecorder] or [OpusAudioRecorder] into the
     * speech recognizer pipe when [useAudioPipe] fallback is active on Android 13+.
     */
    fun feedPcm(buffer: ByteArray, bytesRead: Int, sampleRate: Int) {
        val sink = pipeSink ?: return
        if (!useAudioPipe || !isSessionActive || isPaused || bytesRead <= 1) return
        runCatching {
            val resampled = downsample16BitMono(buffer, bytesRead, sampleRate, PIPE_SAMPLE_RATE)
            sink.write(resampled)
        }
    }

    private fun downsample16BitMono(src: ByteArray, bytesRead: Int, srcRate: Int, dstRate: Int): ByteArray {
        if (srcRate <= dstRate || srcRate <= 0) return src.copyOf(bytesRead)
        val totalSamples = bytesRead / 2
        val outSamples = (totalSamples.toLong() * dstRate / srcRate).toInt().coerceAtLeast(1)
        val out = ByteArray(outSamples * 2)
        for (i in 0 until outSamples) {
            val srcIdx = ((i.toLong() * srcRate) / dstRate).toInt().coerceIn(0, totalSamples - 1) * 2
            out[i * 2] = src[srcIdx]
            out[i * 2 + 1] = src[srcIdx + 1]
        }
        return out
    }

    private fun startListeningInternal(recreate: Boolean = false) {
        if (!isSessionActive || isPaused) return
        if (recreate) destroyCurrentRecognizer()

        val recognizer = ensureRecognizer() ?: run {
            activeListener?.onError("Could not initialize speech recognizer")
            return
        }

        currentUtteranceStartMs = currentElapsedMs()
        runCatching {
            recognizer.startListening(buildIntent())
        }.onFailure { err ->
            android.util.Log.w("SpeechToTextEngine", "startListening failed", err)
            if (isSessionActive && !isPaused) {
                scheduleRestart(400L, recreate = true)
            } else {
                activeListener?.onError(err.message ?: "Failed to start speech recognition")
            }
        }
    }

    private fun extractBestText(bundle: Bundle?): String =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull { it.isNotBlank() }
            ?.trim()
            .orEmpty()

    private fun flushPendingPartialIfAny() {
        val text = bestPartialText.trim()
        if (text.isEmpty()) return
        val nowMs = currentElapsedMs()
        val endMs = bestPartialEndMs.coerceAtLeast(nowMs).coerceAtLeast(currentUtteranceStartMs + 200)
        val segment = TranscriptionSegment(
            startTimeMs = currentUtteranceStartMs,
            endTimeMs = endMs,
            text = text,
            confidence = 0.85,
        )
        bestPartialText = ""
        bestPartialEndMs = 0
        currentUtteranceStartMs = nowMs
        activeListener?.onSegmentRecognized(segment)
    }

    private fun handleRecognizedBundle(bundle: Bundle?, restartAfter: Boolean) {
        val matchesText = extractBestText(bundle)
        val text = matchesText.ifEmpty { bestPartialText.trim() }
        val scores = bundle?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
        val rawConf = scores?.firstOrNull()?.toDouble() ?: 0.92
        val confidence = if (rawConf > 0.0) rawConf.coerceIn(0.0, 1.0) else 0.92
        val nowOffsetMs = currentElapsedMs()

        bestPartialText = ""
        bestPartialEndMs = 0

        if (text.isNotEmpty()) {
            val segment = TranscriptionSegment(
                startTimeMs = currentUtteranceStartMs,
                endTimeMs = nowOffsetMs.coerceAtLeast(currentUtteranceStartMs + 200),
                text = text,
                confidence = confidence,
            )
            currentUtteranceStartMs = nowOffsetMs
            activeListener?.onSegmentRecognized(segment)
        }

        if (restartAfter) {
            if (isSessionActive && !isPaused) {
                scheduleRestart(80L, recreate = false)
            } else {
                activeListener?.onFinished()
            }
        }
    }

    private fun handlePartialBundle(partialResults: Bundle?) {
        val text = extractBestText(partialResults)
        if (text.isEmpty()) return
        val nowOffsetMs = currentElapsedMs()

        // If the recognizer silently reset its partial hypothesis mid-stream after a long clause,
        // commit the previous clause before tracking the new one.
        if (bestPartialText.length >= 14 &&
            text.length < bestPartialText.length / 2 &&
            !text.startsWith(bestPartialText.take(5), ignoreCase = true)
        ) {
            flushPendingPartialIfAny()
        }

        bestPartialText = text
        bestPartialEndMs = nowOffsetMs.coerceAtLeast(currentUtteranceStartMs + 200)
        activeListener?.onPartialSubtitle(text, currentUtteranceStartMs, bestPartialEndMs)
    }

    private fun handleRecognitionError(error: Int) {
        if (!isSessionActive || isPaused) return
        val errorMsg = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO ($error)"
            SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT ($error)"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS ($error)"
            SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK ($error)"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT ($error)"
            SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH ($error)"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY ($error)"
            SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER ($error)"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT ($error)"
            12 -> "ERROR_LANGUAGE_NOT_SUPPORTED (12)"
            13 -> "ERROR_LANGUAGE_UNAVAILABLE (13)"
            14 -> "ERROR_SERVER_DISCONNECTED (14)"
            else -> "ERROR_UNKNOWN ($error)"
        }
        android.util.Log.w("SpeechToTextEngine", "SpeechRecognizer error: $errorMsg")
        flushPendingPartialIfAny()

        // Error 12 (ERROR_LANGUAGE_NOT_SUPPORTED) or 13 (ERROR_LANGUAGE_UNAVAILABLE): fall back to online.
        if ((error == 12 || error == 13) && preferOffline) {
            preferOffline = false
        }
        // Error 3 (ERROR_AUDIO) on Android 13+: switch to PCM pipe fed from AudioSession.
        if (error == SpeechRecognizer.ERROR_AUDIO &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !useAudioPipe
        ) {
            useAudioPipe = true
        }

        val isTransientSilence = error == SpeechRecognizer.ERROR_NO_MATCH ||
            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
        if (isTransientSilence) {
            scheduleRestart(100L, recreate = false)
        } else {
            val isBusy = error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
            scheduleRestart(if (isBusy) 400L else 300L, recreate = true)
        }
    }

    private fun createRecognitionListener(): RecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {
            if (bestPartialText.isEmpty()) {
                currentUtteranceStartMs = currentElapsedMs()
            }
        }
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onError(error: Int) = handleRecognitionError(error)
        override fun onResults(results: Bundle?) = handleRecognizedBundle(results, restartAfter = true)
        override fun onSegmentResults(segmentResults: Bundle) =
            handleRecognizedBundle(segmentResults, restartAfter = false)
        override fun onEndOfSegmentedSession() {
            flushPendingPartialIfAny()
            if (isSessionActive && !isPaused) scheduleRestart(80L, recreate = false)
        }
        override fun onPartialResults(partialResults: Bundle?) = handlePartialBundle(partialResults)
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun scheduleRestart(delayMs: Long, recreate: Boolean) {
        if (!isSessionActive || isPaused) return
        handler.removeCallbacksAndMessages(null)
        closeAudioPipe()
        handler.postDelayed({
            if (isSessionActive && !isPaused) {
                startListeningInternal(recreate = recreate)
            }
        }, delayMs.coerceAtLeast(60L))
    }

    /** Pauses speech recognition while keeping session state, committing any in-flight words. */
    fun pauseListening() {
        if (!isSessionActive || isPaused) return
        isPaused = true
        handler.removeCallbacksAndMessages(null)
        flushPendingPartialIfAny()
        closeAudioPipe()
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runCatching { speechRecognizer?.cancel() }
        } else {
            handler.post { runCatching { speechRecognizer?.cancel() } }
        }
    }

    /** Resumes speech recognition in an ongoing session. */
    fun resumeListening() {
        if (!isSessionActive || !isPaused) return
        isPaused = false
        currentUtteranceStartMs = currentElapsedMs()
        handler.post { startListeningInternal(recreate = false) }
    }

    private fun destroyCurrentRecognizer() {
        closeAudioPipe()
        runCatching {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        }
        speechRecognizer = null
    }

    /** Stops an active listening session and synchronously flushes any pending partial words. */
    fun stopListening() {
        if (!isSessionActive) return
        isSessionActive = false
        handler.removeCallbacksAndMessages(null)
        flushPendingPartialIfAny()
        val listener = activeListener
        activeListener = null
        if (Looper.myLooper() == Looper.getMainLooper()) {
            destroyCurrentRecognizer()
            listener?.onFinished()
        } else {
            handler.post {
                destroyCurrentRecognizer()
                listener?.onFinished()
            }
        }
    }

    /** Releases recognizer resources. */
    fun release() {
        stopListening()
    }

    private companion object {
        const val PIPE_SAMPLE_RATE = 16_000
    }
}

