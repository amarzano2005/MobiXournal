package com.mobixournal.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.mobixournal.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

/**
 * Foreground Service that executes speech-to-text audio transcription in the background
 * without interruptions when the user switches apps or turns off the screen.
 *
 * Uses [WhisperOfflineTranscriber] when a model is installed, providing ultra-fast
 * processing (~30x real-time) and robust millisecond timestamp alignment.
 */
class AudioTranscriptionService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var wakeLock: PowerManager.WakeLock? = null
    private var isCancelled = false
    private var currentFile: File? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                isCancelled = true
                stopForegroundTask()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val filePath = intent.getStringExtra(EXTRA_FILE_PATH) ?: run {
                    stopSelf()
                    return START_NOT_STICKY
                }
                val lang = intent.getStringExtra(EXTRA_LANGUAGE) ?: "it-IT"
                val file = File(filePath)
                if (!file.isFile) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                currentFile = file
                isCancelled = false
                startForegroundTask(file, lang)
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundTask(file: File, lang: String) {
        acquireWakeLock()
        isRunning = true
        val notification = buildProgressNotification("Avvio analisi audio...", 0, 100)

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                }
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, serviceType)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure { err ->
            android.util.Log.e("AudioTranscriptionService", "startForeground failed", err)
        }

        serviceScope.launch {
            processTranscription(file, lang)
        }
    }

    private suspend fun processTranscription(file: File, lang: String) {
        updateProgress("Extracting audio...", 5, 100)
        onProgressUpdated?.invoke("Extracting audio...", 5, 100)

        val pcmData = AudioPcmExtractor.extract16kMonoPcm(file)
        if (pcmData == null || pcmData.isEmpty() || isCancelled) {
            finishWithResult(file, null, "Audio extraction failed")
            return
        }

        updateProgress("Detecting speech (VAD)...", 10, 100)
        val chunks = AudioVadSegmenter.segment(pcmData)
        if (chunks.isEmpty() || isCancelled) {
            finishWithResult(file, null, "No speech detected")
            return
        }

        val availableModel = WhisperModelManager.getAvailableModel(this)
        val segments = if (availableModel != null) {
            transcribeWithWhisper(availableModel, chunks, lang)
        } else {
            transcribeWithPlatformRecognizer(chunks, lang)
        }

        if (isCancelled || segments.isEmpty()) {
            finishWithResult(file, null, if (isCancelled) "Transcription cancelled" else "No recognized text")
            return
        }

        val transcript = AudioTranscription(
            audioFilename = file.name,
            segments = segments,
            language = lang,
        )

        val transcriptFile = File(filesDir, "audio/${file.name}.transcript.json")
        runCatching {
            transcriptFile.writeText(TranscriptionCodec.encode(transcript))
        }

        finishWithResult(file, transcript, "Transcription complete (${segments.size} segments)")
    }

    private fun transcribeWithWhisper(
        modelType: WhisperModelType,
        chunks: List<AudioChunk>,
        lang: String,
    ): List<TranscriptionSegment> {
        val modelDir = WhisperModelManager.getModelDir(this, modelType)
        val transcriber = WhisperOfflineTranscriber(modelDir, modelType.isTiny)
        val initSuccess = transcriber.init(language = lang)
        if (!initSuccess) {
            transcriber.close()
            return emptyList()
        }

        return try {
            transcriber.transcribeChunks(
                chunks = chunks,
                onProgress = { current, total ->
                    val percent = ((current * 100) / total).coerceIn(0, 100)
                    val status = "Whisper: frase $current di $total ($percent%)"
                    updateProgress(status, current, total)
                    onProgressUpdated?.invoke(status, current, total)
                },
                isCancelled = { isCancelled },
            )
        } finally {
            transcriber.close()
        }
    }

    private suspend fun transcribeWithPlatformRecognizer(
        chunks: List<AudioChunk>,
        lang: String,
    ): List<TranscriptionSegment> =
        AudioChunkTranscriber.transcribeChunks(
            context = this,
            chunks = chunks,
            language = lang,
            onProgress = { current, total ->
                val percent = ((current * 100) / total).coerceIn(0, 100)
                val status = "Transcribing: $current/$total ($percent%)"
                updateProgress(status, current, total)
                onProgressUpdated?.invoke(status, current, total)
            },
            isCancelled = { isCancelled },
        )

    private fun finishWithResult(file: File, transcript: AudioTranscription?, statusMsg: String) {
        onTranscriptionCompleted?.invoke(file, transcript, statusMsg)
        stopForegroundTask()
        if (transcript != null) {
            showCompletionNotification(file.name, statusMsg)
        }
        stopSelf()
    }

    private fun updateProgress(text: String, current: Int, total: Int) {
        val notification = buildProgressNotification(text, current, total)
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
    }

    private fun buildProgressNotification(text: String, current: Int, total: Int): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val cancelIntent = Intent(this, AudioTranscriptionService::class.java).apply {
            action = ACTION_CANCEL
        }
        val pendingCancel = PendingIntent.getService(
            this,
            1,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Voice note transcription")
            .setContentText(text)
            .setProgress(total, current, total <= 0)
            .setContentIntent(pendingOpen)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", pendingCancel)
            .build()
    }

    private fun showCompletionNotification(fileName: String, summary: String) {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Transcription complete")
            .setContentText("$fileName • $summary")
            .setContentIntent(pendingOpen)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(COMPLETION_NOTIFICATION_ID, notification)
    }

    private fun stopForegroundTask() {
        releaseWakeLock()
        isRunning = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)?.apply {
            setReferenceCounted(false)
            acquire(60 * 60 * 1000L) // 1 hour max
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        serviceScope.cancel()
        stopForegroundTask()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Audio transcription",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Progress notifications for background audio transcription"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "mobixournal_audio_transcription"
        const val NOTIFICATION_ID = 4002
        const val COMPLETION_NOTIFICATION_ID = 4003
        const val WAKE_LOCK_TAG = "mobixournal:transcription_service"

        const val ACTION_START = "com.mobixournal.action.START_TRANSCRIPTION"
        const val ACTION_CANCEL = "com.mobixournal.action.CANCEL_TRANSCRIPTION"
        const val EXTRA_FILE_PATH = "extra_file_path"
        const val EXTRA_LANGUAGE = "extra_language"

        @Volatile
        var isRunning: Boolean = false
            private set

        var onProgressUpdated: ((status: String, current: Int, total: Int) -> Unit)? = null
        var onTranscriptionCompleted: ((file: File, transcript: AudioTranscription?, statusMsg: String) -> Unit)? = null

        fun start(context: Context, file: File, language: String) {
            val intent = Intent(context, AudioTranscriptionService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_FILE_PATH, file.absolutePath)
                putExtra(EXTRA_LANGUAGE, language)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AudioTranscriptionService::class.java).apply {
                action = ACTION_CANCEL
            }
            context.startService(intent)
        }
    }
}
