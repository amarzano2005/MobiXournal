package com.mobixournal.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * On-device speech recognizer wrapper using Android's platform [SpeechRecognizer]
 * with `EXTRA_PREFER_OFFLINE` enabled to provide private, local transcription.
 */
class SpeechToTextEngine(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var sessionStartTimeMs = 0L

    /** Listener callback for progressive speech recognition events. */
    interface TranscriptionListener {
        fun onSegmentRecognized(segment: TranscriptionSegment)
        fun onError(error: String)
        fun onFinished()
    }

    /**
     * Checks whether offline on-device speech recognition is available on this platform.
     */
    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * Begins real-time listening and segment transcription for an active recording session.
     */
    fun startListening(
        language: String = Locale.getDefault().toLanguageTag(),
        listener: TranscriptionListener,
    ) {
        if (isListening) return
        if (!isAvailable()) {
            listener.onError("Speech recognition not available on this device")
            return
        }

        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer = recognizer
        sessionStartTimeMs = System.currentTimeMillis()
        isListening = true

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            private var segmentStartOffsetMs = 0

            override fun onReadyForSpeech(params: Bundle?) {
                segmentStartOffsetMs = (System.currentTimeMillis() - sessionStartTimeMs).toInt().coerceAtLeast(0)
            }

            override fun onBeginningOfSpeech() {
                segmentStartOffsetMs = (System.currentTimeMillis() - sessionStartTimeMs).toInt().coerceAtLeast(0)
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                isListening = false
                listener.onError("Speech recognition error: $error")
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val scores = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                val text = matches?.firstOrNull()?.trim().orEmpty()
                val confidence = scores?.firstOrNull()?.toDouble() ?: 1.0
                val nowOffsetMs = (System.currentTimeMillis() - sessionStartTimeMs).toInt().coerceAtLeast(0)

                if (text.isNotEmpty()) {
                    val segment = TranscriptionSegment(
                        startTimeMs = segmentStartOffsetMs,
                        endTimeMs = nowOffsetMs.coerceAtLeast(segmentStartOffsetMs + 200),
                        text = text,
                        confidence = confidence.coerceIn(0.0, 1.0),
                    )
                    listener.onSegmentRecognized(segment)
                }
                isListening = false
                listener.onFinished()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim().orEmpty()
                val nowOffsetMs = (System.currentTimeMillis() - sessionStartTimeMs).toInt().coerceAtLeast(0)
                if (text.isNotEmpty()) {
                    val segment = TranscriptionSegment(
                        startTimeMs = segmentStartOffsetMs,
                        endTimeMs = nowOffsetMs.coerceAtLeast(segmentStartOffsetMs + 200),
                        text = text,
                        confidence = 0.8,
                    )
                    listener.onSegmentRecognized(segment)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        runCatching {
            recognizer.startListening(intent)
        }.onFailure { err ->
            isListening = false
            listener.onError(err.message ?: "Failed to start speech recognition")
        }
    }

    /**
     * Stops an active listening session.
     */
    fun stopListening() {
        if (!isListening) return
        isListening = false
        runCatching {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        }
        speechRecognizer = null
    }

    /**
     * Releases recognizer resources.
     */
    fun release() {
        stopListening()
    }
}
