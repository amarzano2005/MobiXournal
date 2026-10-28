package com.mobixournal.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toAwtImage
import com.mobixournal.format.model.Background
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The window's **three export commands** — Export PDF, Export pages as PNG and Export pages as SVG — and
 * the one line it says afterwards.
 *
 * The exporters are the library (`DesktopPdfExport`, `DesktopSvgExport`) and they do not know about a
 * window: they write files. What a command adds is the *choice* (which file, which folder), the
 * **thread** the write runs on, and the words for a failure or a success. Keeping that here rather than
 * in `DesktopShell.kt` is the same split `DesktopShellSaves.kt` makes for Save/Save As — and the same
 * structural reason (`SourceStructureTest` keeps the shell file inside its budget).
 *
 * The document is snapshotted on the UI thread before the work starts: `DesktopDocument`'s pages are read
 * through the window and a page read can inflate a `.mobix` entry, so the export is handed the **document
 * it was asked about**, never a tab that may have closed while bytes were being written.
 */

/** What one export produced, in the two shapes the window can show: words, or a sentence of failure. */
internal sealed interface ExportOutcome {

    /** The files are on disk; [message] is the status bar's line, already in English and specific. */
    data class Written(val message: String) : ExportOutcome

    /** Nothing landed (or nothing that matters); this is what the failure card shows. */
    data class Failed(val failure: OpenFailure) : ExportOutcome
}

/** One export, as the menu or the key asked for it — before it has chosen a destination. */
internal sealed interface ExportRequest {

    /** A PDF of the whole document, written to [target]. */
    data class Pdf(val target: File) : ExportRequest

    /** One SVG per page, written into [folder] — the document's own folder when null. */
    data class Svg(val folder: File? = null) : ExportRequest

    /** One PNG per page, written into [folder] — the document's own folder when null. */
    data class Png(val folder: File? = null) : ExportRequest
}

/**
 * The one export in flight, and the window's own save dialog as a callback.
 *
 * A request goes here instead of being run where the click happened, because the write belongs off the UI
 * thread: `ExportWorker` (below) is the only place it runs. [pickPdfDestination] is installed by the window
 * because opening a `FileDialog` needs one — `Ctrl+E` arrives at the state, which has no window to hang a
 * dialog on.
 */
internal object DesktopExportQueue {

    /** The export waiting to be written, or null. Snapshot state: the worker keys on it. */
    var request: ExportRequest? by mutableStateOf(null)
        private set

    /**
     * How many exports have been asked for. The worker keys on this, not on the request: asking for the
     * same destination twice is two exports, and a `data class` request would compare equal.
     */
    var id: Long by mutableStateOf(0L)
        private set

    /** Asks the window for a PDF destination. Null in a test that has no window. */
    var pickPdfDestination: (() -> Unit)? = null

    fun request(request: ExportRequest) {
        // The last export's sentence belongs to the last export: a new one replaces it rather than
        // leaving a stale "Exported 12 pages" beside a page it no longer describes.
        DesktopExportNotice.clear()
        this.request = request
        id++
    }

    fun take(): ExportRequest? = request.also { request = null }

    /** `Ctrl+E` and the menu's PDF row: ask where, then export. */
    fun askForPdf() {
        pickPdfDestination?.invoke()
    }
}

/**
 * The window's short confirmation: `Exported 12 pages to notes.pdf`.
 *
 * One line in the status bar, not a card — an export that worked is worth knowing about and not worth
 * interrupting the page for. It lives in an object because the status bar is composed from the tab while
 * the export is a shell command, and threading a third value through the whole window for one sentence
 * would be the tail wagging the dog.
 */
internal object DesktopExportNotice {

    /** The last successful export's sentence, or null. Snapshot state: the bar re-reads it. */
    var text: String? by mutableStateOf(null)
        private set

    fun show(message: String) {
        text = message
    }

    fun clear() {
        text = null
    }
}

/**
 * Write [request] for [document] and answer what the window should say — **synchronously**, so a test can
 * call it, and so the worker's only job is to run it off the UI thread.
 *
 * An export with no document is not an outcome at all: the caller has nothing to export, and the menu row
 * and the key are already unavailable then.
 */
internal fun exportOf(document: DesktopDocument, request: ExportRequest): ExportOutcome =
    runCatching {
        when (request) {
            is ExportRequest.Pdf -> {
                val written = DesktopPdfExport.write(document, request.target, document.backdrop)
                ExportOutcome.Written("Exported $written ${pageWord(written)} to ${request.target.name}")
            }
            is ExportRequest.Svg -> {
                val folder = request.folder ?: document.file.absoluteFile.parentFile ?: File(".")
                val files = DesktopSvgExport.write(document, folder) { background -> pngBytes(document, background) }
                ExportOutcome.Written(
                    "Exported ${files.size} ${pageWord(files.size)} as SVG into ${folder.path}",
                )
            }
            is ExportRequest.Png -> {
                val folder = request.folder ?: document.file.absoluteFile.parentFile ?: File(".")
                val files = DesktopPngExport.write(document, folder)
                ExportOutcome.Written(
                    "Exported ${files.size} ${pageWord(files.size)} as PNG into ${folder.path}",
                )
            }
        }
    }.getOrElse { failure -> ExportOutcome.Failed(describeExportFailure(failure)) }

/** Shows [outcome]: the sentence in the status bar, or the failure card. Must run on the UI thread. */
internal fun DesktopShellState.applyExport(outcome: ExportOutcome) {
    when (outcome) {
        is ExportOutcome.Written -> {
            failure = null
            DesktopExportNotice.show(outcome.message)
        }
        is ExportOutcome.Failed -> failure = outcome.failure
    }
}

/** `Export PDF` on the active document. Answers what happened, or null with no document open. */
internal fun DesktopShellState.exportPdf(target: File): ExportOutcome? {
    val tab = tabs.active ?: return null
    val outcome = exportOf(tab.document, ExportRequest.Pdf(target))
    applyExport(outcome)
    return outcome
}

/** `Export pages as SVG` for the active document, beside it unless [folder] says otherwise. */
internal fun DesktopShellState.exportSvg(folder: File? = null): ExportOutcome? {
    val tab = tabs.active ?: return null
    val outcome = exportOf(tab.document, ExportRequest.Svg(folder))
    applyExport(outcome)
    return outcome
}

/** `Export pages as PNG` for the active document, beside it unless [folder] says otherwise. */
internal fun DesktopShellState.exportPng(folder: File? = null): ExportOutcome? {
    val tab = tabs.active ?: return null
    val outcome = exportOf(tab.document, ExportRequest.Png(folder))
    applyExport(outcome)
    return outcome
}

/**
 * The background half: takes the queued export and writes it on [Dispatchers.IO], then reports back on the
 * composition's own thread.
 *
 * A whole notebook is every page compiled and every stroke emitted — seconds on a long document — so it
 * cannot run in the click handler, and a window that froze for it would look broken rather than busy. The
 * outcome is applied after the switch back, so no snapshot state is written off the UI thread.
 */
@Composable
internal fun ExportWorker(state: DesktopShellState) {
    val id = DesktopExportQueue.id
    LaunchedEffect(id) {
        if (id == 0L) return@LaunchedEffect
        val request = DesktopExportQueue.request ?: return@LaunchedEffect
        val tab = state.tabs.active ?: return@LaunchedEffect
        val document = tab.document
        // Nothing is cleared before the write: nulling the request would re-key this effect and cancel
        // the very coroutine doing the work.
        val outcome = withContext(Dispatchers.IO) { exportOf(document, request) }
        state.applyExport(outcome)
        DesktopExportQueue.take()
    }
}

/**
 * A page's picture as PNG, the bytes `PageSvgWriter` embeds — the same rule the SVG writer states: the
 * model cannot name the pixels of an annotated PDF page, so the shell that has them (through
 * [PageBackdrop]) encodes them and passes them in.
 */
private fun pngBytes(document: DesktopDocument, background: Background): ByteArray? {
    val bitmap = document.backdrop.image(background) ?: return null
    val out = ByteArrayOutputStream()
    return if (ImageIO.write(bitmap.toAwtImage(), "png", out)) out.toByteArray() else null
}

/** `1 page` / `3 pages` — the one place the plural of the confirmation is decided. */
private fun pageWord(count: Int): String = if (count == 1) "page" else "pages"
