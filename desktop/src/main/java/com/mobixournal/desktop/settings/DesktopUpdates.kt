package com.mobixournal.desktop.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mobixournal.update.UpdateFeed
import com.mobixournal.update.UpdatePlatform
import com.mobixournal.update.UpdateRelease
import com.mobixournal.update.isNewer
import com.mobixournal.update.parseRelease
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.math.roundToInt

/** What the window is doing about updates right now — the About row and the popup read this one state. */
enum class DesktopUpdatePhase { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, READY, FAILED }

/**
 * The window's **update check and its installer** (`docs/updates.md`).
 *
 * The releases are the project's, read from the same GitHub document the tablet reads — but the number this
 * window compares is **its own**: the release tag names the Android build, so the window's version is the one
 * the MSI's asset name carries ([UpdateRelease.version]). A tag-only reading would offer a Windows reader
 * `v2.0.0` forever, because this shell is at `1.0.x`.
 *
 * **Update now** downloads that MSI beside the system's temporary files and starts Windows' own installer
 * (`msiexec`), which is the per-user package `docs/tools.md` describes. The window does not close itself
 * while the installer is up: a save in progress is the reader's, and the MSI replaces files the running
 * window has open, so quitting is the decision the person makes after the installer says so.
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

    var phase by mutableStateOf(DesktopUpdatePhase.IDLE)
        private set

    /** The release on offer, once one is newer than this window. */
    var release by mutableStateOf<UpdateRelease?>(null)
        private set

    /** How much of the MSI has arrived, 0…1. */
    var progress by mutableStateOf(0f)
        private set

    /** The sentence the dialog and the About row print — always about this window's own state. */
    var message by mutableStateOf<String?>(null)
        private set

    /** Whether the popup is up. **Remind me later** closes it and leaves [release] in hand. */
    var dialog by mutableStateOf(false)
        private set

    /** The launch check: silent unless it finds something newer, so a working window opens on its page. */
    fun checkOnLaunch() {
        if (asked) return
        asked = true
        check()
    }

    /** The About row's command — the same check, with the answer reported either way. */
    fun check() {
        if (phase == DesktopUpdatePhase.CHECKING || phase == DesktopUpdatePhase.DOWNLOADING) return
        phase = DesktopUpdatePhase.CHECKING
        message = null
        scope.launch {
            val found = runCatching { latest() }
            if (found.isFailure) {
                phase = DesktopUpdatePhase.FAILED
                message = "Could not check for updates. Check the connection and try again."
                return@launch
            }
            val offer = found.getOrNull()
            if (offer == null) {
                phase = DesktopUpdatePhase.UP_TO_DATE
                return@launch
            }
            release = offer
            phase = DesktopUpdatePhase.AVAILABLE
            dialog = true
        }
    }

    /** The dialog's **Update now**: download the MSI and start Windows' installer on it. */
    fun updateNow() {
        val current = release ?: return
        if (phase == DesktopUpdatePhase.DOWNLOADING) return
        phase = DesktopUpdatePhase.DOWNLOADING
        progress = 0f
        dialog = true
        scope.launch {
            val file = runCatching { download(current) }.getOrNull()
            if (file == null) {
                phase = DesktopUpdatePhase.FAILED
                message = "The download did not finish. Check the connection and try again."
                return@launch
            }
            val launched = runCatching {
                ProcessBuilder("msiexec", "/i", file.absolutePath).start()
            }.isSuccess
            if (!launched) {
                phase = DesktopUpdatePhase.FAILED
                message = "Windows did not start the installer. The package is at ${file.absolutePath}."
            } else {
                phase = DesktopUpdatePhase.READY
                message =
                    "The installer is open. Close MobiXournal when it asks, and follow its steps to finish."
            }
        }
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

    /** The one line About prints under its command. */
    fun statusLine(): String = when (phase) {
        DesktopUpdatePhase.IDLE -> "Checks the project's releases for a newer window."
        DesktopUpdatePhase.CHECKING -> "Checking for updates…"
        DesktopUpdatePhase.UP_TO_DATE -> "MobiXournal $DESKTOP_VERSION is the latest version."
        DesktopUpdatePhase.AVAILABLE -> release?.let { "Version ${it.version} is available." } ?: ""
        DesktopUpdatePhase.DOWNLOADING -> "Downloading ${(progress * 100).roundToInt()}%"
        DesktopUpdatePhase.READY -> message ?: ""
        DesktopUpdatePhase.FAILED -> message ?: "Could not check for updates."
    }

    /** The newer release, or null when this window already is the latest. */
    private fun latest(): UpdateRelease? {
        val request = HttpRequest.newBuilder(URI(UpdateFeed.LATEST))
            .timeout(Duration.ofSeconds(20))
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", USER_AGENT)
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) return null
        val parsed = parseRelease(response.body(), UpdatePlatform.DESKTOP) ?: return null
        return parsed.takeIf { isNewer(it.version, DESKTOP_VERSION) }
    }

    /** Stream the MSI beside the system's temporary files, reporting its progress as it arrives. */
    private fun download(release: UpdateRelease): File {
        val folder = File(System.getProperty("java.io.tmpdir"), "MobiXournal-updates").apply { mkdirs() }
        // One package at a time: a second download must not leave two installers to choose between.
        folder.listFiles()?.forEach { it.delete() }
        val target = File(folder, release.assetName.ifEmpty { "MobiXournal-$DESKTOP_VERSION.msi" })
        val request = HttpRequest.newBuilder(URI(release.downloadUrl))
            .timeout(Duration.ofMinutes(10))
            .header("User-Agent", USER_AGENT)
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() !in 200..299) error("HTTP ${response.statusCode()}")
        val total = response.headers().firstValueAsLong("content-length").orElse(-1L)
        response.body().use { input ->
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
        return target
    }
}
