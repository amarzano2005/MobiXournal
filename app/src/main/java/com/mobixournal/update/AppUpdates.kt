package com.mobixournal.update

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.mobixournal.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/** What the tablet is doing about updates right now — one state, read by About and by the dialog. */
enum class UpdatePhase { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, READY, FAILED }

/**
 * The tablet's **update check and its installer**, as one subject (`docs/updates.md`).
 *
 * The releases are the project's own GitHub ones: [parseRelease] reads the one document the API answers with,
 * picks the APK, and compares the release's tag with this build's `versionName`. Nothing is polled and
 * nothing is sent about the reader — the check is a GET of a public page, once per launch.
 *
 * The download lands in `cacheDir/updates/`, which the app's own `FileProvider` hands to the system
 * installer: the APK is never written where another app could read it, and the install prompt is Android's,
 * so the app cannot install anything silently. It is a singleton because it outlives the About page — a
 * check that started at launch must still be able to raise its dialog while the editor is open — and because
 * a second copy of "which release is on offer" is a second answer.
 */
object AppUpdates {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Where the download lands; the provider's `updates/` path is this same folder. */
    private const val UPDATE_FOLDER = "updates"

    /** Once per process: launching the app asks GitHub one question, not one per About visit. */
    private var asked = false

    var phase by mutableStateOf(UpdatePhase.IDLE)
        private set

    /** The release on offer, once one is newer than this build. */
    var release by mutableStateOf<UpdateRelease?>(null)
        private set

    /** How much of the APK has arrived, 0…1. */
    var progress by mutableStateOf(0f)
        private set

    /** The sentence under the About row — always about *this* shell's own state, never a guess. */
    var message by mutableStateOf<String?>(null)
        private set

    /** Whether the popup is up. **Remind me later** closes it and leaves [release] in hand. */
    var dialog by mutableStateOf(false)
        private set

    /** The launch check: silent unless it finds something newer, so a working app opens to its page. */
    fun checkOnLaunch(context: Context) {
        if (asked) return
        asked = true
        check(context)
    }

    /** The About row's command — the same check, with the answer reported either way. */
    fun check(context: Context) {
        if (phase == UpdatePhase.CHECKING || phase == UpdatePhase.DOWNLOADING) return
        phase = UpdatePhase.CHECKING
        message = null
        scope.launch {
            val found = runCatching { latest() }
            val offer = found.getOrNull()
            if (found.isFailure) {
                phase = UpdatePhase.FAILED
                message = "Could not check for updates. Check your connection and try again."
                return@launch
            }
            if (offer == null) {
                phase = UpdatePhase.UP_TO_DATE
                message = null
                return@launch
            }
            release = offer
            phase = UpdatePhase.AVAILABLE
            dialog = true
        }
    }

    /** The dialog's **Update now**: download the APK, then hand it to Android's installer. */
    fun updateNow(context: Context) {
        val current = release ?: return
        if (phase == UpdatePhase.DOWNLOADING) return
        phase = UpdatePhase.DOWNLOADING
        progress = 0f
        dialog = true
        scope.launch {
            val file = runCatching { download(context, current) }.getOrNull()
            if (file == null) {
                phase = UpdatePhase.FAILED
                message = "The download did not finish. Check your connection and try again."
                return@launch
            }
            phase = UpdatePhase.READY
            message = "The installer is open. Follow it to finish the update."
            withContext(Dispatchers.Main) { install(context, file) }
        }
    }

    /** The dialog's **Remind me later**: it goes away, and the offer stays on the About page. */
    fun remindLater() {
        dialog = false
    }

    /** Open the popup again from About, without asking GitHub a second time. */
    fun showDialog() {
        if (release != null) dialog = true
    }

    /** About's one command: an offer already in hand reopens its dialog, otherwise ask GitHub. */
    fun offerOrCheck(context: Context) {
        if (release != null) showDialog() else check(context)
    }

    /** The one line About prints under its command. */
    fun statusLine(): String = when (phase) {
        UpdatePhase.IDLE -> "Tap to check for updates."
        UpdatePhase.CHECKING -> "Checking for updates…"
        UpdatePhase.UP_TO_DATE -> "MobiXournal ${BuildConfig.VERSION_NAME} is the latest version."
        UpdatePhase.AVAILABLE -> release?.let { "Version ${it.version} is available." } ?: ""
        UpdatePhase.DOWNLOADING -> "Downloading ${(progress * 100).roundToInt()}%"
        UpdatePhase.READY -> message ?: ""
        UpdatePhase.FAILED -> message ?: "Could not check for updates."
    }

    /**
     * The newer release, or null when this build already is the latest.
     *
     * An HTTP error, a body that is not the release document, and a release with no APK all answer null —
     * "no update" is the safe reading of a check that could not be made.
     */
    private fun latest(): UpdateRelease? {
        val connection = (URL(UpdateFeed.LATEST).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "MobiXournal-Android/${BuildConfig.VERSION_NAME}")
        }
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val parsed = parseRelease(body, UpdatePlatform.ANDROID) ?: return null
            parsed.takeIf { isNewer(it.version, BuildConfig.VERSION_NAME) }
        } finally {
            connection.disconnect()
        }
    }

    /** Stream the APK into the provider's own folder, reporting its progress as it arrives. */
    private fun download(context: Context, release: UpdateRelease): File {
        val folder = File(context.cacheDir, UPDATE_FOLDER).apply { mkdirs() }
        // One package at a time: a second download of another build must not leave two of them to install.
        folder.listFiles()?.forEach { it.delete() }
        val target = File(folder, "MobiXournal-${release.version}.apk")
        val connection = (URL(release.downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "MobiXournal-Android/${BuildConfig.VERSION_NAME}")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) error("HTTP ${connection.responseCode}")
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var written = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        if (total > 0) progress = (written.toDouble() / total).toFloat().coerceIn(0f, 1f)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        return target
    }

    /**
     * Hand the APK to the system installer through the app's own provider.
     *
     * Android still asks the reader to allow installing from this app the first time (`REQUEST_INSTALL_PACKAGES`
     * in the manifest), which is the whole point: the upgrade is a prompt the person answers, not something
     * the app performs behind the page.
     */
    private fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure {
            phase = UpdatePhase.FAILED
            message = "Android did not open its installer. Allow MobiXournal to install apps and try again."
        }
    }
}
