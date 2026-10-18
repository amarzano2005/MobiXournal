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

/**
 * An Android Foreground Service that keeps microphone audio recording active when the app
 * moves to the background or the screen turns off.
 *
 * Displays an ongoing notification with an elapsed chronometer and a "Stop" button.
 */
class AudioRecordingService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                onStopRequested?.invoke()
                stopForegroundRecording()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val startMs = intent.getLongExtra(EXTRA_START_TIME_MS, System.currentTimeMillis())
                startForegroundRecording(startMs)
            }
        }
        return START_STICKY
    }

    private fun startForegroundRecording(startTimeMs: Long) {
        acquireWakeLock()
        val notification = buildRecordingNotification(startTimeMs)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            isRunning = true
        }.onFailure { err ->
            android.util.Log.e("AudioRecordingService", "Failed to startForeground", err)
        }
    }

    private fun stopForegroundRecording() {
        releaseWakeLock()
        isRunning = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        stopForegroundRecording()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)?.apply {
            setReferenceCounted(false)
            acquire(6 * 60 * 60 * 1000L) // 6 hours max
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Registrazione audio",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Stato della registrazione audio in background"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildRecordingNotification(startTimeMs: Long): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = Intent(this, AudioRecordingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.mobixournal.R.drawable.ic_launcher_monochrome)
            .setContentTitle("Registrazione audio in corso")
            .setContentText("MobiXournal sta registrando l'audio per i tuoi appunti")
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .setUsesChronometer(true)
            .setWhen(startTimeMs)
            .addAction(android.R.drawable.ic_media_pause, "Interrompi", stopPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "mobixournal_audio_recording"
        const val NOTIFICATION_ID = 2001
        const val ACTION_START = "com.mobixournal.action.START_AUDIO_RECORDING"
        const val ACTION_STOP = "com.mobixournal.action.STOP_AUDIO_RECORDING"
        const val EXTRA_START_TIME_MS = "start_time_ms"
        private const val WAKE_LOCK_TAG = "MobiXournal:AudioRecording"

        @Volatile
        var isRunning: Boolean = false
            private set

        /** Callback invoked when the user taps "Stop" on the persistent notification. */
        var onStopRequested: (() -> Unit)? = null

        fun start(context: Context, startTimeMs: Long = System.currentTimeMillis()) {
            val intent = Intent(context, AudioRecordingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_START_TIME_MS, startTimeMs)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
                // If background launch is blocked by OS, recording continues in-process
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AudioRecordingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
