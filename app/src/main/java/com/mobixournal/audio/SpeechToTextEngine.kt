package com.mobixournal.audio

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * On-device continuous speech recognizer wrapper using Android's platform [SpeechRecognizer]
 * with automatic utterance continuation, silence recovery, and live partial subtitle support.
 */
class SpeechToTextEngine(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var isSessionActive = false
    private var isPaused = false
    private var sessionStartTimeMs = 0L
    private var activeLanguage: String = Locale.getDefault().toLanguageTag()
    private var activeListener: TranscriptionListener? = null
    private var currentUtteranceStartMs = 0

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
    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * Begins real-time listening and continuous segment transcription for an active recording session.
     */
    fun startListening(
        language: String = Locale.getDefault().toLanguageTag(),
        listener: TranscriptionListener,
    ) {
        if (isSessionActive) return
        if (!isAvailable()) {
            listener.onError("Speech recognition not available on this device")
            return
        }

        isSessionActive = true
        isPaused = false
        sessionStartTimeMs = System.currentTimeMillis()
        activeLanguage = language
        activeListener = listener

        handler.post { startListeningInternal() }
    }

    private fun startListeningInternal() {
        if (!isSessionActive) return

        destroyCurrentRecognizer()

        val recognizer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        ) {
            runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(context) }
                .getOrNull() ?: SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }

        speechRecognizer = recognizer
        currentUtteranceStartMs = (System.currentTimeMillis() - sessionStartTimeMs).toInt().coerceAtLeast(0)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, activeLanguage)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, activeLanguage)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 800L)
        }

        recognizer.setRecognitionListener(createRecognitionListener())

        runCatching {
            recognizer.startListening(intent)
        }.onFailure { err ->
            if (isSessionActive) {
                scheduleRestart(500L)
            } else {
                activeListener?.onError(err.message ?: "Failed to start speech recognition")
            }
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}

            override fun onBeginningOfSpeech() {
                currentUtteranceStartMs = (System.currentTimeMillis() - sessionStartTimeMs).toInt().coerceAtLeast(0)
            }

            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                if (isSessionActive) {
                    val delay = when (error) {
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 300L
                        SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> 600L
                        else -> 100L
                    }
                    scheduleRestart(delay)
                } else {
                    activeListener?.onError("Speech recognition error: $error")
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val scores = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                val text = matches?.firstOrNull()?.trim().orEmpty()
                val confidence = scores?.firstOrNull()?.toDouble() ?: 0.95
                val nowOffsetMs = (System.currentTimeMillis() - sessionStartTimeMs).toInt().coerceAtLeast(0)

                if (text.isNotEmpty()) {
                    val segment = TranscriptionSegment(
                        startTimeMs = currentUtteranceStartMs,
                        endTimeMs = nowOffsetMs.coerceAtLeast(currentUtteranceStartMs + 200),
                        text = text,
                        confidence = confidence.coerceIn(0.0, 1.0),
                    )
                    activeListener?.onSegmentRecognized(segment)
                }

                if (isSessionActive) {
                    scheduleRestart(80L)
                } else {
                    activeListener?.onFinished()
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim().orEmpty()
                val nowOffsetMs = (System.currentTimeMillis() - sessionStartTimeMs).toInt().coerceAtLeast(0)
                if (text.isNotEmpty()) {
                    activeListener?.onPartialSubtitle(text, currentUtteranceStartMs, nowOffsetMs)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun scheduleRestart(delayMs: Long) {
        if (!isSessionActive || isPaused) return
        handler.postDelayed({
            if (isSessionActive && !isPaused) {
                startListeningInternal()
            }
        }, delayMs)
    }

    /**
     * Pauses speech recognition while keeping session state.
     */
    fun pauseListening() {
        if (!isSessionActive || isPaused) return
        isPaused = true
        handler.removeCallbacksAndMessages(null)
        handler.post { runCatching { speechRecognizer?.stopListening() } }
    }

    /**
     * Resumes speech recognition in an ongoing session.
     */
    fun resumeListening() {
        if (!isSessionActive || !isPaused) return
        isPaused = false
        handler.post { startListeningInternal() }
    }

    private fun destroyCurrentRecognizer() {
        runCatching {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        }
        speechRecognizer = null
    }

    /**
     * Stops an active listening session.
     */
    fun stopListening() {
        if (!isSessionActive) return
        isSessionActive = false
        handler.removeCallbacksAndMessages(null)
        handler.post {
            destroyCurrentRecognizer()
            activeListener?.onFinished()
            activeListener = null
        }
    }

    /**
     * Releases recognizer resources.
     */
    fun release() {
        stopListening()
    }
}
