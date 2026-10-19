package com.mobixournal.audio

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import java.util.Locale

/** Status of an on-device speech recognition language model package. */
enum class LanguagePackStatus {
    /** The on-device offline recognition package is installed and ready. */
    INSTALLED,
    /** The on-device recognition model is supported by hardware/OS but must be downloaded. */
    DOWNLOAD_AVAILABLE,
    /** On-device offline recognition is not supported (hybrid online recognition is used). */
    NOT_SUPPORTED,
    /** Status could not be determined. */
    UNKNOWN,
}

/** Configurable speech transcription language option. */
data class TranscriptionLanguage(
    val code: String,
    val displayName: String,
)

val SUPPORTED_TRANSCRIPTION_LANGUAGES = listOf(
    TranscriptionLanguage("auto", "System default"),
    TranscriptionLanguage("it-IT", "Italian (it-IT)"),
    TranscriptionLanguage("en-US", "English (en-US)"),
    TranscriptionLanguage("es-ES", "Spanish (es-ES)"),
    TranscriptionLanguage("fr-FR", "French (fr-FR)"),
    TranscriptionLanguage("de-DE", "German (de-DE)"),
)

/** Resolves the concrete IETF language tag (e.g. `it-IT`) from a preference value. */
fun resolveTranscriptionLanguageTag(pref: String?): String {
    if (pref == null || pref == "auto" || pref.isBlank()) {
        val def = Locale.getDefault().toLanguageTag()
        return if (def.isNotBlank()) def else "it-IT"
    }
    return pref
}

/**
 * Checks whether the offline language pack for [languageTag] is installed on this device.
 */
fun checkSpeechLanguagePack(
    context: Context,
    languageTag: String,
    onResult: (LanguagePackStatus) -> Unit,
) {
    val resolved = resolveTranscriptionLanguageTag(languageTag)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    ) {
        val recognizer = runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(context) }.getOrNull()
        if (recognizer != null) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, resolved)
            }
            recognizer.checkRecognitionSupport(
                intent,
                ContextCompat.getMainExecutor(context),
                object : RecognitionSupportCallback {
                    override fun onSupportResult(support: RecognitionSupport) {
                        val status = when {
                            support.installedOnDeviceLanguages.any { it.equals(resolved, ignoreCase = true) } ->
                                LanguagePackStatus.INSTALLED
                            support.supportedOnDeviceLanguages.any { it.equals(resolved, ignoreCase = true) } ->
                                LanguagePackStatus.DOWNLOAD_AVAILABLE
                            else -> LanguagePackStatus.NOT_SUPPORTED
                        }
                        recognizer.destroy()
                        onResult(status)
                    }

                    override fun onError(error: Int) {
                        recognizer.destroy()
                        onResult(LanguagePackStatus.UNKNOWN)
                    }
                },
            )
            return
        }
    }

    // Android < 33 fallback: query SpeechRecognizer details broadcast
    val detailsIntent = Intent(RecognizerIntent.ACTION_GET_LANGUAGE_DETAILS)
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            val bundle = getResultExtras(true)
            val supported = bundle.getStringArrayList(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES).orEmpty()
            val preferred = bundle.getString(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE)
            val isPresent = supported.any { it.equals(resolved, ignoreCase = true) } ||
                preferred.equals(resolved, ignoreCase = true)
            onResult(if (isPresent) LanguagePackStatus.INSTALLED else LanguagePackStatus.DOWNLOAD_AVAILABLE)
        }
    }

    val sent = runCatching {
        context.sendOrderedBroadcast(detailsIntent, null, receiver, null, Activity.RESULT_OK, null, null)
        true
    }.getOrDefault(false)

    if (!sent) {
        onResult(LanguagePackStatus.UNKNOWN)
    }
}

/**
 * Triggers the system download of the offline language pack or directs the user to system voice settings.
 */
fun triggerSpeechLanguageDownload(context: Context, languageTag: String): Boolean {
    val resolved = resolveTranscriptionLanguageTag(languageTag)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    ) {
        val recognizer = runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(context) }.getOrNull()
        if (recognizer != null) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, resolved)
            }
            recognizer.triggerModelDownload(intent)
            recognizer.destroy()
            return true
        }
    }

    return openVoiceRecognitionSettings(context)
}

/**
 * Launches Android system settings for voice recognition and language pack downloads.
 */
fun openVoiceRecognitionSettings(context: Context): Boolean {
    val intents = listOf(
        Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
        Intent("com.google.android.voicesearch.intent.action.InstallVoiceSearch"),
        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.googlequicksearchbox")),
    )
    for (intent in intents) {
        val launched = runCatching {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        }.getOrDefault(false)
        if (launched) return true
    }
    return false
}
