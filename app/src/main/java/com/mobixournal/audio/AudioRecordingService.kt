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
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.mobixournal.MainActivity

/**
 * An Android Foreground Service that keeps microphone audio recording active when the app
 * moves to the background or the screen turns off.
 *
 * Its notification is the recording's **remote control**: an elapsed chronometer while it records,
 * and — while the app is not on screen, which is exactly when the notification is the only thing
 * visible — buttons to pause/resume the recording and to finish it. The two actions go back through
 * the same session calls the mini capsule uses ([onPauseRequested], [onStopRequested]), so a pause
 * from the shade and a pause from the toolbar are one edit, and the hint text a paused recording
 * shows is the same state [AudioSession.isRecordingPaused] reports.
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
            ACTION_PAUSE -> {
                // The session flips the pause and reports back through [notifyPaused], which is the
                // one place the notification is rebuilt: two sources of truth for "is it paused"
                // would let the shade and the toolbar disagree.
                onPauseRequested?.invoke()
            }
            ACTION_START -> {
                startedAtMs = intent.getLongExtra(EXTRA_START_TIME_MS, System.currentTimeMillis())
                paused = false
                startForegroundRecording(startedAtMs)
            }
        }
        return START_STICKY
    }

    private fun startForegroundRecording(startTimeMs: Long) {
        acquireWakeLock()
        val notification = buildNotification(this, startTimeMs, paused)
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
        paused = false
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
                "Audio recording",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Background audio recording status"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "mobixournal_audio_recording"
        const val NOTIFICATION_ID = 2001
        const val ACTION_START = "com.mobixournal.action.START_AUDIO_RECORDING"
        const val ACTION_STOP = "com.mobixournal.action.STOP_AUDIO_RECORDING"
        const val ACTION_PAUSE = "com.mobixournal.action.PAUSE_AUDIO_RECORDING"
        const val EXTRA_START_TIME_MS = "start_time_ms"
        private const val WAKE_LOCK_TAG = "MobiXournal:AudioRecording"

        /** Request codes, so the shade's two buttons never overwrite each other's intent. */
        private const val REQUEST_OPEN = 0
        private const val REQUEST_PAUSE = 1
        private const val REQUEST_STOP = 2

        @Volatile
        var isRunning: Boolean = false
            private set

        /** When the recording now on the shade was started, so a rebuild keeps its chronometer honest. */
        @Volatile
        private var startedAtMs: Long = 0L

        /** Whether the recording on the shade is paused — what the Pause button turns into Resume. */
        @Volatile
        var paused: Boolean = false
            private set

        /** Callback invoked when the user taps "Stop" on the persistent notification. */
        var onStopRequested: (() -> Unit)? = null

        /** Callback invoked when the user taps Pause/Resume on the persistent notification. */
        var onPauseRequested: (() -> Unit)? = null

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

        /**
         * Re-post the recording notification for a recording that is (or is no longer) paused.
         *
         * Called by the session's own pause toggle, whichever surface asked for it — the shade's
         * button, the mini capsule or the pop-up menu — so the notification always describes the
         * recording that is actually running. A no-op when the service is not up: there is no
         * notification to update, and the next [start] builds a fresh one.
         */
        fun notifyPaused(context: Context, isPaused: Boolean) {
            paused = isPaused
            if (!isRunning) return
            runCatching {
                NotificationManagerCompat.from(context)
                    .notify(NOTIFICATION_ID, buildNotification(context, startedAtMs, isPaused))
            }
        }

        /** The recording's notification in the state [isPaused] describes. */
        private fun buildNotification(context: Context, startTimeMs: Long, isPaused: Boolean): Notification {
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPendingIntent = PendingIntent.getActivity(
                context,
                REQUEST_OPEN,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val pauseIntent = Intent(context, AudioRecordingService::class.java).apply {
                action = ACTION_PAUSE
            }
            val pausePendingIntent = PendingIntent.getService(
                context,
                REQUEST_PAUSE,
                pauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val stopIntent = Intent(context, AudioRecordingService::class.java).apply {
                action = ACTION_STOP
            }
            val stopPendingIntent = PendingIntent.getService(
                context,
                REQUEST_STOP,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val pauseAction = if (isPaused) {
                NotificationCompat.Action(
                    android.R.drawable.ic_media_play,
                    "Resume",
                    pausePendingIntent,
                )
            } else {
                NotificationCompat.Action(
                    android.R.drawable.ic_media_pause,
                    "Pause",
                    pausePendingIntent,
                )
            }

            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(com.mobixournal.R.drawable.ic_launcher_monochrome)
                .setContentTitle(if (isPaused) "Audio recording paused" else "Audio recording in progress")
                .setContentText(
                    if (isPaused) {
                        "Paused — tap Resume to keep recording"
                    } else {
                        "MobiXournal is recording audio for your notes"
                    },
                )
                .setOngoing(true)
                // Rebuilding for a pause must not buzz the shade a second time: the state changed,
                // the recording did not.
                .setOnlyAlertOnce(true)
                .setContentIntent(openPendingIntent)
                .setUsesChronometer(!isPaused)
                .setShowWhen(!isPaused)
                .setWhen(startTimeMs)
                .addAction(pauseAction)
                .addAction(
                    NotificationCompat.Action(
                        android.R.drawable.ic_menu_close_clear_cancel,
                        "Stop",
                        stopPendingIntent,
                    ),
                )
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build()
        }
    }
}
