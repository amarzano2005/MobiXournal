package com.mobixournal.update

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.mobixournal.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

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

    /** How many bytes have arrived, and how many the server declared — the card's own readout. */
    var receivedBytes by mutableStateOf(0L)
        private set

    /** The APK's declared length, or 0 when the server declared none (then the bar is indeterminate). */
    var totalBytes by mutableStateOf(0L)
        private set

    /** The APK already in hand, so Android's installer can be asked again without a second download. */
    var downloadedFile by mutableStateOf<File?>(null)
        private set

    /** The download in flight, so **Cancel** can stop it instead of leaving it unseen. */
    private var downloadJob: Job? = null

    /**
     * Numbers each attempt, and so names each attempt's own file.
     *
     * A cancelled coroutine may still be blocked in a read when the reader starts the next download, and it
     * finishes its file's cleanup long after that one began. Its own path means the two attempts cannot write
     * to the same file: the old writer is left with a deleted inode and the new one owns its own name.
     */
    private var attempt = 0

    /**
     * Why the last attempt stopped, or null — the card and the About row word it from
     * [updateFailureSentence], so a failure is written once (`docs/updates.md`).
     */
    var failure by mutableStateOf<UpdateFailure?>(null)
        private set

    /** The build the reader asked never to be offered again, or null; see [skipThisVersion]. */
    var skippedVersion by mutableStateOf<String?>(null)
        private set

    /** Whether the popup is up. **Remind me later** closes it and leaves [release] in hand. */
    var dialog by mutableStateOf(false)
        private set

    /**
     * The launch check: silent unless it finds something newer, so a working app opens to its page.
     *
     * A build the reader skipped is still recorded as the offer — About can reopen it — but it does not
     * raise the card, which is what makes **Skip this version** hold across launches. A newer release is a
     * different version string, so it is offered again.
     */
    fun checkOnLaunch(context: Context) {
        if (asked) return
        asked = true
        check(context, fromLaunch = true)
    }

    /** The About row's command — the same check, with the answer reported either way. */
    fun check(context: Context) {
        check(context, fromLaunch = false)
    }

    private fun check(context: Context, fromLaunch: Boolean) {
        if (phase == UpdatePhase.CHECKING || phase == UpdatePhase.DOWNLOADING) return
        phase = UpdatePhase.CHECKING
        failure = null
        scope.launch {
            val found = runCatching { latest() }
            val offer = found.getOrNull()
            if (found.isFailure) {
                phase = UpdatePhase.FAILED
                failure = UpdateFailure.CHECK
                return@launch
            }
            if (offer == null) {
                phase = UpdatePhase.UP_TO_DATE
                return@launch
            }
            val skipped = UpdatePreferences(context).skippedVersion()
            release = offer
            skippedVersion = skipped
            phase = UpdatePhase.AVAILABLE
            dialog = updateShouldInterrupt(fromLaunch, offer.version, skipped)
        }
    }

    /** The dialog's **Update now**: download the APK, then hand it to Android's installer. */
    fun updateNow(context: Context) {
        val current = release ?: return
        if (phase == UpdatePhase.DOWNLOADING) return
        phase = UpdatePhase.DOWNLOADING
        progress = 0f
        receivedBytes = 0L
        totalBytes = 0L
        downloadedFile = null
        failure = null
        dialog = true
        val token = ++attempt
        downloadJob = scope.launch {
            val file = try {
                download(context, current, token)
            } catch (cancelled: CancellationException) {
                // **Cancel** already put the card back on the offer; a cancelled read is not a failure.
                throw cancelled
            } catch (failed: Throwable) {
                phase = UpdatePhase.FAILED
                failure = UpdateFailure.DOWNLOAD
                return@launch
            }
            downloadedFile = file
            phase = UpdatePhase.READY
            failure = null
            withContext(Dispatchers.Main) { install(context, file) }
        }
    }

    /**
     * The dialog's **Cancel**: stop the download and put the offer back.
     *
     * The half file is deleted by [download] itself, so the next attempt starts from zero and no truncated
     * APK is ever handed to Android's installer. Cancelling once the bytes are complete is a no-op, because
     * the phase has already left [UpdatePhase.DOWNLOADING].
     */
    fun cancelDownload() {
        if (phase != UpdatePhase.DOWNLOADING) return
        downloadJob?.cancel()
        downloadJob = null
        progress = 0f
        receivedBytes = 0L
        totalBytes = 0L
        failure = null
        phase = UpdatePhase.AVAILABLE
    }

    /** The dialog's **Install**: ask Android's installer again for a download already in hand. */
    fun installNow(context: Context) {
        val file = downloadedFile ?: return
        install(context, file)
    }

    /** The dialog's **Remind me later**: it goes away, and the offer stays on the About page. */
    fun remindLater() {
        dialog = false
    }

    /**
     * The dialog's **Skip this version**: remember it and close, so the launch check stops raising this
     * build. A newer release is offered again, and About's own command reopens the offer whenever the
     * reader asks for it. Nothing is downloaded or deleted here.
     */
    fun skipThisVersion(context: Context) {
        val version = release?.version ?: return
        UpdatePreferences(context).skip(version)
        skippedVersion = version
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

    /** The one line About prints under its command — the same copy the card reads. */
    fun statusLine(): String = updateStatusLine(
        phase = phase,
        version = release?.version,
        current = BuildConfig.VERSION_NAME,
        failure = failure,
        progress = progress,
        totalBytes = totalBytes,
    )

    /**
     * The newer release, or null when this build already is the latest.
     *
     * The API is the primary source: it alone answers whether a release is a draft or a prerelease. When it
     * **refuses** the request — GitHub answers `403` to an anonymous caller that has spent the hour's sixty
     * calls, which an entire network can do through one shared address — the same release is read through
     * the quota-free pages ([UpdateFallback]) instead of reporting a failure a reader cannot act on. A body
     * that is not the release document still answers null: a check never turns itself into an invitation to
     * install something it could not read.
     */
    private fun latest(): UpdateRelease? {
        val body = runCatching { read(UpdateFeed.LATEST, "application/vnd.github+json") }.getOrNull()
        // A refused request falls back; an answered one is trusted, even when it names no APK.
        val parsed = if (body != null) parseRelease(body, UpdatePlatform.ANDROID) else fallbackLatest()
        return parsed?.takeIf { isNewer(it.version, BuildConfig.VERSION_NAME) }
    }

    /**
     * The release read without the API: the published-releases feed names the newest tag, and that release's
     * asset list names the APK (`docs/updates.md`). Each request is a public page, so this path costs none of
     * the API's hourly quota.
     */
    private fun fallbackLatest(): UpdateRelease? {
        val tag = UpdateFallback.tagIn(read(UpdateFallback.ATOM, "application/atom+xml")) ?: return null
        val names = UpdateFallback.assetNamesIn(read(UpdateFallback.assetsPageOf(tag), "text/html"))
        return UpdateFallback.release(tag, names, UpdatePlatform.ANDROID)
    }

    /** One public page as text; a non-200 is thrown, because only the caller knows what it means. */
    private fun read(url: String, accept: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", "MobiXournal-Android/${BuildConfig.VERSION_NAME}")
        }
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                error("the update feed answered HTTP ${connection.responseCode}")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Stream the APK into the provider's own folder, reporting its progress as it arrives.
     *
     * The loop yields to cancellation between chunks (**Cancel**), and a cancelled attempt deletes its half
     * file: a download the reader stopped leaves nothing behind that could later be installed.
     */
    private suspend fun download(context: Context, release: UpdateRelease, attempt: Int): File {
        currentCoroutineContext().ensureActive()
        val folder = File(context.cacheDir, UPDATE_FOLDER).apply { mkdirs() }
        // One package at a time: a second download of another build must not leave two of them to install.
        folder.listFiles()?.forEach { it.delete() }
        val target = File(folder, "MobiXournal-${release.version}-$attempt.apk")
        val connection = (URL(release.downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "MobiXournal-Android/${BuildConfig.VERSION_NAME}")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) error("HTTP ${connection.responseCode}")
            val total = connection.contentLengthLong
            if (total > 0) totalBytes = total
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var written = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        receivedBytes = written
                        if (total > 0) progress = (written.toDouble() / total).toFloat().coerceIn(0f, 1f)
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            runCatching { target.delete() }
            throw cancelled
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
            failure = UpdateFailure.INSTALL
        }
    }
}
