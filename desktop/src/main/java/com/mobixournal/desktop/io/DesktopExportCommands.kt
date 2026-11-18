package com.mobixournal.desktop.io
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.sheet.PageBackdrop
import com.mobixournal.desktop.shell.DesktopShell
import com.mobixournal.desktop.shell.DesktopShellState
import com.mobixournal.desktop.shell.ExportPdfScope

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toAwtImage
import com.mobixournal.format.model.Background
import com.mobixournal.render.ExportPages
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

    /**
     * A PDF of the pages [pages] names (every page when null), written to [target].
     *
     * The indices are the **notebook's**, in its own order — what the *Export PDF* card's answer comes to
     * ([ExportPdfScope]). An export of pages 2 and 4 of a reordered notebook writes them as they read, not as
     * they were imported.
     */
    data class Pdf(val target: File, val pages: List<Int>? = null) : ExportRequest

    /** One SVG per page, written into [folder] — the document's own folder when null. */
    data class Svg(val folder: File? = null) : ExportRequest

    /** One PNG per page, written into [folder] — the document's own folder when null. */
    data class Png(val folder: File? = null) : ExportRequest
}

/**
 * The one export in flight, and the window's own save dialog as a callback.
 *
 * A request goes here instead of being run where the click happened, because the write belongs off the UI
 * thread: `ExportWorker` (below) is the only place it runs. [pickPdfDestination] and [pickFolder] are
 * installed by the window because opening a system dialog needs one — `Ctrl+E` arrives at the state, which
 * has no window to hang a dialog on.
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

    /**
     * Asks the window for a **folder** — where the page exports (SVG, PNG) write their one file per page.
     * Null in a test that has no window, where the pages fall back to the document's own folder.
     */
    var pickFolder: ((String) -> File?)? = null

    fun request(request: ExportRequest) {
        // The last export's sentence belongs to the last export: a new one replaces it rather than
        // leaving a stale "Exported 12 pages" beside a page it no longer describes.
        DesktopExportNotice.clear()
        this.request = request
        id++
    }

    fun take(): ExportRequest? = request.also { request = null }

    /**
     * The pages the **Export PDF** question answered, held until the system's dialog names a file.
     *
     * The picker's answer is a destination and nothing else, so the selection travels beside it rather than
     * through it; it is consumed by the export it belongs to ([DesktopShellState.exportPdf]) and replaced by
     * the next answer, so a cancelled dialog cannot leave a stale range behind.
     */
    private var pdfPages: List<Int>? = null

    /** The question being asked — which pages, and the answers under consideration — or null. */
    var scopeRequest: ExportPdfScope? by mutableStateOf(null)
        private set

    /** Ask *which pages* first: the file's destination is asked once the answer is known. */
    fun openScope(pageCount: Int, current: Int) {
        scopeRequest = ExportPdfScope(pageCount = pageCount, current = current)
    }

    /** Retype one part of the open question — the answer under consideration, or the pages it names. */
    fun editScope(transform: (ExportPdfScope) -> ExportPdfScope) {
        scopeRequest = scopeRequest?.let(transform)
    }

    /** Put the question away without exporting anything. */
    fun cancelScope() {
        scopeRequest = null
    }

    /**
     * Take the open question's answer: keep its pages for the export the picker is about to name, then ask
     * where. A refused field keeps the card up — the same guard the card's dead *Export* button shows.
     */
    fun confirmScope() {
        val request = scopeRequest ?: return
        val chosen = request.chosen ?: return
        scopeRequest = null
        askForPdf(chosen)
    }

    /** The pages the next PDF export writes, then forget them: one answer, one file. */
    internal fun takePdfPages(): List<Int>? = pdfPages.also { pdfPages = null }

    /**
     * Ask where, then export — holding [pages] for the write. A null list is every page, which is what a
     * document of one page writes anyway; the answer always replaces whatever a previous export left behind.
     */
    fun askForPdf(pages: List<Int>? = null) {
        pdfPages = pages
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
                val written = DesktopPdfExport.write(document, request.target, document.backdrop, request.pages)
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

/**
 * `Export PDF` — the menu row, `Ctrl+E` and the header: ask **which pages** first, then where.
 *
 * A document of **one page** is asked nothing ([ExportPages.needsPageChoice]): the card would offer three ways
 * to say "the only page there is", so the answer is already known and the destination is asked straight away.
 *
 * Nothing is raised on a read-only tab: the window would be asking a question whose answer it cannot act on.
 */
internal fun DesktopShellState.askExportPdfPages() {
    val tab = tabs.active?.takeUnless { it.document.readOnly } ?: return
    val pages = tab.document.pageCount
    if (ExportPages.needsPageChoice(pages)) {
        DesktopExportQueue.openScope(pages, tab.page)
    } else {
        DesktopExportQueue.askForPdf(listOf(0))
    }
}

/** `Export PDF` on the active document. Answers what happened, or null with no document open. */
internal fun DesktopShellState.exportPdf(target: File): ExportOutcome? {
    val tab = tabs.active ?: return null
    val outcome = exportOf(tab.document, ExportRequest.Pdf(target, DesktopExportQueue.takePdfPages()))
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
        val outcome = withContext(Dispatchers.IO) { document.withReader { exportOf(document, request) } }
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
