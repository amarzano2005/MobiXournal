package com.mobixournal.desktop.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mobixournal.update.UpdateFailure
import com.mobixournal.update.UpdateFallback
import com.mobixournal.update.UpdateFeed
import com.mobixournal.update.UpdatePhase
import com.mobixournal.update.UpdatePlatform
import com.mobixournal.update.UpdateRelease
import com.mobixournal.update.isNewer
import com.mobixournal.update.parseRelease
import com.mobixournal.update.updateStatusLine
import java.io.File
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The window's **update check and its installer** (`docs/updates.md`).
 *
 * The releases are the project's, read from the same GitHub document the tablet reads — but the number this
 * window compares is **its own**: the release tag names the Android build, so the window's version is the one
 * the MSI's asset name carries ([UpdateRelease.version]). A tag-only reading would offer a Windows reader
 * `v2.0.0` forever, because this shell is at `1.0.x`.
 *
 * The states, the words and the answer each one offers are **not** this file's: they are
 * `mobix-core`'s `UpdateCardCopy`, shared with the tablet, so the same phase says the same thing in both
 * shells and only the installer's noun differs. What this file owns is the transfer and the installer.
 *
 * **Update now** downloads that MSI beside the system's temporary files and starts Windows' own installer
 * (`msiexec`), which is the per-user package `docs/tools.md` describes. The package is kept after it was
 * started, so **Install** asks the installer again without a second download — and a failed download offers
 * **Try again**, never a silent button. The window does not close itself while the installer is up: a save in
 * progress is the reader's, and the MSI replaces files the running window has open, so quitting is the
 * decision the person makes after the installer says so.
 *
 * It is a singleton for the reason the tablet's is: the launch check outlives the About page — its popup
 * has to be able to appear while the settings surface is closed — and a second copy of "which release is on
 * offer" would be a second answer.
 */
object DesktopUpdates {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** GitHub answers a request with no agent with a 403, so both calls name the window. */
    private const val USER_AGENT = "MobiXournal-Desktop/1.0 (+https://github.com/amarzano2005/MobiXournal)"

    private val client: HttpClient by lazy {
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            // Both the API and an asset URL answer with a redirect; neither is the request's answer.
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
    }

    /** Once per process: launching the window asks GitHub one question, not one per About visit. */
    private var asked = false

    var phase by mutableStateOf(UpdatePhase.IDLE)
        private set

    /** The release on offer, once one is newer than this window. */
    var release by mutableStateOf<UpdateRelease?>(null)
        private set

    /** How much of the MSI has arrived, 0…1. */
    var progress by mutableStateOf(0f)
        private set

    /** How many bytes have arrived, and how many the server declared — the card's own readout. */
    var receivedBytes by mutableStateOf(0L)
        private set

    /** The MSI's declared length, or 0 when the server declared none (then the bar is indeterminate). */
    var totalBytes by mutableStateOf(0L)
        private set

    /** The MSI already in hand, so the installer can be asked again without a second download. */
    var downloadedFile by mutableStateOf<File?>(null)
        private set

    /**
     * Why the last attempt stopped, or null — the card and the About row word it from
     * [com.mobixournal.update.updateFailureSentence], so a failure is written once (`docs/updates.md`).
     */
    var failure by mutableStateOf<UpdateFailure?>(null)
        private set

    /** Whether the popup is up. **Remind me later** closes it and leaves [release] in hand. */
    var dialog by mutableStateOf(false)
        private set

    /** The download in flight, so **Cancel** can stop it instead of leaving it unseen. */
    private var downloadJob: Job? = null

    /** The response body being read, kept so a cancel can close it and interrupt the blocked read. */
    private var openStream: InputStream? = null

    /**
     * Whether the reader stopped the transfer.
     *
     * Closing the stream makes the reading thread throw, which is indistinguishable from a broken connection
     * except for this flag: a cancelled download is **not** a failure and must not report one.
     */
    private var cancelRequested = false

    /** Numbers each attempt, and so names each attempt's own package. */
    private var attempt = 0

    /**
     * The launch check: silent unless it finds something newer, so a working window opens on its page — and
     * no sentence under About's command, because a reader who did not ask must not be told.
     */
    fun checkOnLaunch() {
        if (asked) return
        asked = true
        check()
    }

    /** The About row's command — the same check, whose answer About prints under the row. */
    fun check() {
        if (phase == UpdatePhase.CHECKING || phase == UpdatePhase.DOWNLOADING) return
        phase = UpdatePhase.CHECKING
        failure = null
        scope.launch {
            val found = runCatching { latest() }
            if (found.isFailure) {
                phase = UpdatePhase.FAILED
                failure = UpdateFailure.CHECK
                return@launch
            }
            val offer = found.getOrNull()
            if (offer == null) {
                phase = UpdatePhase.UP_TO_DATE
                return@launch
            }
            release = offer
            phase = UpdatePhase.AVAILABLE
            dialog = true
        }
    }

    /** The dialog's **Update now** / **Try again**: download the MSI and start Windows' installer on it. */
    fun updateNow() {
        val current = release ?: return
        if (phase == UpdatePhase.DOWNLOADING) return
        phase = UpdatePhase.DOWNLOADING
        progress = 0f
        receivedBytes = 0L
        totalBytes = 0L
        downloadedFile = null
        failure = null
        cancelRequested = false
        dialog = true
        val token = ++attempt
        downloadJob = scope.launch {
            val file = try {
                download(current, token)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failed: Throwable) {
                // **Cancel** already put the offer back; a read it interrupted is not a failure.
                if (!cancelRequested) {
                    phase = UpdatePhase.FAILED
                    failure = UpdateFailure.DOWNLOAD
                }
                return@launch
            }
            // A cancel that landed after the last byte must not open the installer the reader just stopped.
            if (cancelRequested) {
                runCatching { file.delete() }
                return@launch
            }
            downloadedFile = file
            install(file)
        }
    }

    /**
     * The dialog's **Cancel**: stop the download and put the offer back.
     *
     * Closing the body interrupts the blocking read, and [download] deletes its half package itself, so no
     * truncated MSI is ever kept. Cancelling once the bytes are complete is a no-op, because the phase has
     * already left [UpdatePhase.DOWNLOADING].
     */
    fun cancelDownload() {
        if (phase != UpdatePhase.DOWNLOADING) return
        cancelRequested = true
        runCatching { openStream?.close() }
        openStream = null
        downloadJob?.cancel()
        downloadJob = null
        progress = 0f
        receivedBytes = 0L
        totalBytes = 0L
        failure = null
        phase = UpdatePhase.AVAILABLE
    }

    /** The dialog's **Install**: ask Windows' installer again for the MSI already in hand. */
    fun installNow() {
        val file = downloadedFile ?: return
        install(file)
    }

    /** The dialog's **Remind me later**: it goes away, and the offer stays on the About page. */
    fun dismiss() {
        dialog = false
    }

    /** Open the popup again from About, without asking GitHub a second time. */
    fun showDialog() {
        if (release != null) dialog = true
    }

    /** About's one command: an offer already in hand reopens its dialog, otherwise ask GitHub. */
    fun offerOrCheck() {
        if (release != null) showDialog() else check()
    }

    /**
     * The answer the last check has, or null before one was asked — About's own line under its command.
     *
     * The window has no floating toast for this: the command and its answer sit in the same panel, because
     * a sentence a reader asked for in About and found hovering at the bottom of the window was one that
     * belonged to another surface.
     */
    fun answerLine(): String? {
        if (phase == UpdatePhase.IDLE) return null
        return updateStatusLine(
            phase = phase,
            version = release?.version,
            current = DESKTOP_VERSION,
            failure = failure,
            progress = progress,
            totalBytes = totalBytes,
            platform = UpdatePlatform.DESKTOP,
        )
    }

    /**
     * The newer release, or null when this window already is the latest.
     *
     * The API is the primary source: it alone answers whether a release is a draft or a prerelease. When it
     * **refuses** the request — GitHub answers `403` to an anonymous caller that has spent the hour's sixty
     * calls, which an entire network can do through one shared address — the same release is read through
     * the quota-free pages ([UpdateFallback]) rather than reporting a failure the reader cannot act on
     * (`docs/updates.md`).
     */
    private fun latest(): UpdateRelease? {
        val body = runCatching { text(UpdateFeed.LATEST, "application/vnd.github+json") }.getOrNull()
        // A refused request falls back; an answered one is trusted, even when it names no MSI.
        val parsed = if (body != null) parseRelease(body, UpdatePlatform.DESKTOP) else fallbackLatest()
        return parsed?.takeIf { isNewer(it.version, DESKTOP_VERSION) }
    }

    /**
     * The release read without the API: the published-releases feed names the newest tag, and that release's
     * asset list names the MSI — which is where this window's number comes from.
     */
    private fun fallbackLatest(): UpdateRelease? {
        val tag = UpdateFallback.tagIn(text(UpdateFallback.ATOM, "application/atom+xml")) ?: return null
        val names = UpdateFallback.assetNamesIn(text(UpdateFallback.assetsPageOf(tag), "text/html"))
        return UpdateFallback.release(tag, names, UpdatePlatform.DESKTOP)
    }

    /** One public page as text; a non-200 is thrown, because only the caller knows what it means. */
    private fun text(url: String, accept: String): String {
        val request = HttpRequest.newBuilder(URI(url))
            .timeout(Duration.ofSeconds(20))
            .header("Accept", accept)
            .header("User-Agent", USER_AGENT)
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) error("the update feed answered HTTP ${response.statusCode()}")
        return response.body()
    }

    /**
     * Stream the MSI beside the system's temporary files, reporting its progress as it arrives.
     *
     * Each attempt writes its own name: a cancelled read can still be finishing when the reader starts the
     * next download, and its own path means the two attempts cannot write to the same file.
     */
    private fun download(release: UpdateRelease, attempt: Int): File {
        val folder = File(System.getProperty("java.io.tmpdir"), "MobiXournal-updates").apply { mkdirs() }
        // One package at a time: a second download must not leave two installers to choose between.
        folder.listFiles()?.forEach { it.delete() }
        val target = File(folder, "MobiXournal-${release.version}-$attempt.msi")
        val request = HttpRequest.newBuilder(URI(release.downloadUrl))
            .timeout(Duration.ofMinutes(10))
            .header("User-Agent", USER_AGENT)
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        try {
            if (response.statusCode() !in 200..299) error("HTTP ${response.statusCode()}")
            val total = response.headers().firstValueAsLong("content-length").orElse(-1L)
            if (total > 0) totalBytes = total
            response.body().use { input ->
                openStream = input
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var written = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        receivedBytes = written
                        if (total > 0) progress = (written.toDouble() / total).toFloat().coerceIn(0f, 1f)
                    }
                }
            }
        } catch (stopped: Throwable) {
            runCatching { target.delete() }
            throw stopped
        } finally {
            openStream = null
        }
        return target
    }

    /**
     * Start Windows' installer on [file], and keep the file: a window that refused the package is one the
     * reader can ask again with **Install**, without downloading the MSI a second time.
     */
    private fun install(file: File) {
        val started = runCatching { ProcessBuilder("msiexec", "/i", file.absolutePath).start() }.isSuccess
        if (started) {
            phase = UpdatePhase.READY
            failure = null
        } else {
            phase = UpdatePhase.FAILED
            failure = UpdateFailure.INSTALL
        }
    }
}
