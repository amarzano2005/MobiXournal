package com.mobixournal.desktop.io
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.sheet.PageBackdrop
import com.mobixournal.desktop.shell.DesktopShellState
import com.mobixournal.desktop.shell.DesktopTab
import com.mobixournal.desktop.shell.DesktopTabs
import com.mobixournal.desktop.shell.pagesChanged

import com.mobixournal.render.PageRangePlan
import com.mobixournal.render.PageRangeRequest
import com.mobixournal.render.PageRanges

import com.mobixournal.format.mobix.PageSource
import com.mobixournal.format.model.Background
import com.mobixournal.render.ImportPdfMode
import com.mobixournal.render.PageOps
import java.io.File
import org.apache.pdfbox.pdmodel.PDDocument

/**
 * **Import PDF** in the window — the command, its question, and the two ways it can land.
 *
 * A PDF is not a document here: it is the background a page sits on (`docs/pdf.md`). So importing one is
 * the tablet's promise kept on the PC — every PDF page becomes a page of the document to write on — and the
 * answers are the tablet's two, with one desktop difference that is the whole point of tabs:
 *
 *  - **Add the pages to this document** ([ImportPdfMode.APPEND]) keeps what is open and continues after its
 *    last page, as one undo step. A brand-new document is a single untouched sheet, and that sheet is
 *    dropped rather than left stranded in front of the PDF ([PageOps.appendPages] owns the rule).
 *  - **Replace this document** ([ImportPdfMode.REPLACE]) opens the PDF as a **new tab** on the PC. The
 *    tablet's version discards the open document because it has one view; a window with tabs has no reason
 *    to throw a reader's work away, so "replace" here means "this PDF is the document" without an
 *    unanswered save question in between.
 *
 * The bytes are dealt with where the format can carry them: the document's pages reference the PDF by its
 * absolute path, and a save hands that file to the encoder (`DesktopSaves`), which bundles it into a
 * `.mobix` and attaches it into a zipped `.xopp`, while a plain `.xopp` keeps the path — made relative when
 * the two sit in one folder. Nothing here rasterises: [PageBackdrop] already paints an absolute reference.
 */
data class ImportPdfRequest(
    /** The PDF the picker named. */
    val file: File,
    /** How many pages it holds — the card says it, so the reader knows what is about to land. */
    val pageCount: Int,
    /** The answer being considered. Add is the default when a tab is open and the import can choose. */
    val mode: ImportPdfMode = ImportPdfMode.APPEND,
    /** The first and last page to bring, as typed (1-based); the whole PDF until told otherwise. */
    val first: String = "1",
    val last: String = "$pageCount",
    /**
     * Whether the card asks **where** the pages go (append vs replace). False when the PDF came through
     * *Open* or when no tab is open — then the pages always open in a new tab ([ImportPdfMode.REPLACE]) and
     * the card only asks which page range to bring.
     */
    val chooseMode: Boolean = true,
) {
    /** The run this card asks for, or the sentence saying why it cannot be used ([PageRanges]). */
    internal val range: PageRangePlan get() = PageRanges.pages(PageRangeRequest(first, last), pageCount)
}

/**
 * Open the question for [file]: read its page count first, so the card can say what it will bring, and
 * refuse a PDF that cannot be read with a sentence instead of an empty document.
 *
 * When [chooseMode] is false (a PDF opened through *Open*) or when no tab is open (`tabs.isEmpty`), the
 * question asks only for the page range and imports into a new tab ([ImportPdfMode.REPLACE]).
 */
fun DesktopShellState.askImportPdf(file: File, chooseMode: Boolean = !tabs.isEmpty) {
    val pages = try {
        DesktopPdfImport.pageCount(file)
    } catch (refused: DesktopPdfImport.PdfImportRefused) {
        importFailed(refused)
        return
    }
    failure = null
    val canChoose = chooseMode && !tabs.isEmpty
    importRequest = ImportPdfRequest(
        file = file,
        pageCount = pages,
        mode = if (canChoose) ImportPdfMode.APPEND else ImportPdfMode.REPLACE,
        chooseMode = canChoose,
    )
}

/** Retype one field of the open question — the page range, today. */
fun DesktopShellState.editImportPdf(transform: (ImportPdfRequest) -> ImportPdfRequest) {
    importRequest = importRequest?.let(transform)
}

/** Put the question away without importing anything. */
fun DesktopShellState.cancelImportPdf() {
    importRequest = null
}

/**
 * Answer the open question with [mode], bringing the pages its range names.
 *
 * A range the card cannot use keeps the card up rather than importing something the reader did not ask
 * for: the *Import* button is dead in that state, and this guard is the same rule behind it.
 */
fun DesktopShellState.confirmImportPdf(mode: ImportPdfMode = importRequest?.mode ?: ImportPdfMode.REPLACE) {
    val request = importRequest ?: return
    val range = request.range as? PageRangePlan.Pages ?: return
    val resolved = if (request.chooseMode) mode else ImportPdfMode.REPLACE
    importRequest = null
    importPdf(request.file, resolved, range.first..range.last)
}

/**
 * Turn [file] into pages, per [mode]. True when something landed; a PDF that cannot be read leaves the
 * window as it was and says why through the failure card.
 */
fun DesktopShellState.importPdf(file: File, mode: ImportPdfMode, indices: IntRange? = null): Boolean {
    val reference = file.absolutePath
    return try {
        DesktopPdfImport.open(file).use { pdf ->
            when (mode) {
                ImportPdfMode.REPLACE -> openImported(pdf, file, reference, indices)
                ImportPdfMode.APPEND -> appendImported(pdf, file, reference, indices)
            }
        }
    } catch (refused: DesktopPdfImport.PdfImportRefused) {
        importFailed(refused)
        false
    } catch (failed: Throwable) {
        importFailed(failed)
        false
    }
}

/**
 * REPLACE: a **new tab** holding the PDF's pages, its file a name waiting to be written — the tab starts
 * dirty, so `Ctrl+S` asks where to put it and the proposed name is the PDF's own, in the PDF's own folder.
 */
private fun DesktopShellState.openImported(
    pdf: PDDocument,
    file: File,
    reference: String,
    indices: IntRange?,
): Boolean {
    val model = DesktopPdfImport.document(pdf, reference, indices)
    val format = prefs.newDocumentFormat
    val folder = file.absoluteFile.parentFile
    val proposed = proposedPath(folder, file.nameWithoutExtension, format.extension)
    tabs.adopt(
        DesktopTab(
            document = DesktopDocument(
                file = proposed,
                source = PageSource.of(model),
                model = model,
                container = format.container,
                backdrop = PageBackdrop(bundled = { null }, documentDir = folder),
            ),
            title = proposed.nameWithoutExtension,
            unsaved = true,
        ),
    )
    failure = null
    // A PDF opened at a path another document already cached may not be the same bytes: the index is
    // re-read for the next open rather than borrowed from the file that used to be there.
    DesktopPdfIndexCache.forgetAll()
    return true
}

/**
 * APPEND: the pages land after the open document's last one, as **one** undo step.
 *
 * A `.xopp` can reference one PDF (`docs/pdf.md`), so a document already backed by one has the two files
 * **joined** first ([DesktopPdfMerge]) and its reference re-pointed at the joined file; the arriving pages
 * then continue the joined file's numbering. A document with no PDF background simply references the
 * picked one.
 *
 * "Backed by one" means the reference **resolves**, not that a file sits at its path: a `.mobix` carries
 * its background inside itself, and reading that as "no base" appended the picked PDF to the pages while
 * the old reference stayed behind them — a document with two PDFs, one of which nothing can open. The
 * three answers are therefore kept apart: paper-only (name the picked file), resolvable (join), and
 * named-but-unreachable (refuse, and say what to put back).
 */
private fun DesktopShellState.appendImported(
    pdf: PDDocument,
    file: File,
    reference: String,
    indices: IntRange?,
): Boolean {
    val tab = tabs.active ?: return false
    val before = tab.document.snapshot()
    val held = tab.document.pdfBackground()
    val base = if (held == null) null else tab.document.pdfSource() ?: embeddedBase(tab, held)
    if (held != null && base == null) {
        // Refusing is the only safe answer: appending would re-point the document at the picked PDF and
        // leave every existing page naming a source that cannot be opened.
        importFailed(MissingPdfBase(held.filename ?: held.bundledAsset.orEmpty()))
        return false
    }
    val wanted = indices ?: 0 until pdf.numberOfPages
    val landing = if (base != null) {
        val joined = DesktopPdfMerge.joined(base, file)
        val offset = DesktopPdfImport.pageCount(base)
        // The joined file's own numbering is the only correct one: its page i is page i, so the arriving
        // run is the part of its **tail** the reader picked — a second numbering starting at the base's
        // page count would name pages the joined file does not have.
        val added = DesktopPdfImport.open(joined).use { joinedPdf ->
            DesktopPdfImport.pages(
                joinedPdf,
                reference = null,
                indices = (offset + wanted.first)..(offset + wanted.last),
            )
        }
        DesktopPdfImport.retarget(tab.document.model.pages, joined) to added
    } else {
        tab.document.model.pages to DesktopPdfImport.pages(pdf, reference, indices = wanted)
    }
    val pages = PageOps.appendPages(landing.first, landing.second)
    if (pages === tab.document.model.pages || pages.size == landing.first.size) return false
    tab.document.replacePages(pages)
    tab.record(before)
    pagesChanged(tab)
    // A merge writes a joined PDF, so any index read from the files it joined is stale.
    DesktopPdfIndexCache.forgetAll()
    failure = null
    // Land on the first page that arrived: the reader asked for these pages, not for the ones above them.
    tab.page = (pages.size - landing.second.size).coerceIn(0, pages.lastIndex)
    return true
}

/**
 * The bytes of [background] written to a scratch file, or null when this window cannot reach them.
 *
 * A `.mobix` bundles its background and a zipped `.xopp` extracted it on open: both answer bytes and
 * neither has a path of its own, which is exactly why a join cannot take the file ([DesktopPdfMerge]).
 */
private fun embeddedBase(tab: DesktopTab, background: Background.Pdf): File? {
    val bytes = tab.document.backdrop.pdfBytes(background) ?: return null
    val name = background.filename?.let { File(it).nameWithoutExtension }
        ?: background.bundledAsset?.substringAfterLast('/')?.substringBeforeLast('.')
        ?: "background"
    return runCatching { DesktopPdfMerge.copyOf(bytes, name) }.getOrNull()
}

/** A document whose PDF background cannot be found, said as something the reader can do about it. */
private class MissingPdfBase(source: String) : Exception(
    "This document's PDF background ($source) cannot be found, so joining another PDF into it would " +
        "leave its own pages blank. Nothing was changed: put the original PDF back beside this " +
        "notebook, or export the document as .mobix — which carries its background — and import again.",
)

/** The name a REPLACE tab is born with: the PDF's own, beside it, and never over a file already there. */
private fun DesktopShellState.proposedPath(folder: File?, base: String, extension: String): File {
    val taken = tabs.all.map { it.key }.toSet()
    val beside = folder?.let { File(it, base + extension) }
    if (beside != null && !beside.exists() && DesktopTabs.keyOf(beside) !in taken) return beside
    return DesktopDocuments.newPath(taken, extension)
}

/** An import that could not happen, in the card the window already shows for a refused open. */
private fun DesktopShellState.importFailed(failure: Throwable) {
    this.failure = OpenFailure(
        "This PDF could not be imported",
        failure.message?.takeIf { it.isNotBlank() }
            ?: "The file could not be read. Nothing was changed.",
    )
}
