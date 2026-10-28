package com.mobixournal.desktop

import com.mobixournal.format.FileKind
import com.mobixournal.format.Xopp
import com.mobixournal.format.XoppZip
import com.mobixournal.format.mobix.MobixChunk
import com.mobixournal.format.mobix.MobixDocument
import com.mobixournal.format.mobix.MobixEntrySource
import com.mobixournal.format.mobix.MobixFormat
import com.mobixournal.format.mobix.MobixPagedReader
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mobixournal.format.mobix.PageSource
import java.io.FileNotFoundException
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.withPage
import com.mobixournal.io.DocumentEncoder
import com.mobixournal.render.ATTACH_DOMAIN
import com.mobixournal.render.CanvasGeometry
import com.mobixournal.render.PageWindow
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile

/** Which container a document came in — what the window says it opened. */
enum class DesktopContainer(val label: String) {
    /** A gzip (or plain XML) `.xopp`: the classic desktop Xournal++ file, read whole. */
    XOPP_GZIP("xopp"),

    /** A self-contained ZIP `.xopp`: one file carrying `content.xml` and its attachments. */
    XOPP_ZIP("xopp zip"),

    /** A native `.mobix`: paged, read on demand. */
    MOBIX("mobix"),
}

/**
 * A document the shell has open, with the file it came from.
 *
 * Pages are reached through [PageSource] — the surface both formats present — so the window is written
 * once whether the document is an eager `.xopp` (`MaterializedPages`) or a paged `.mobix` whose pages are
 * inflated only when they scroll into view. That is the whole reason this class does not hold a
 * `Document`: for a 200-page notebook, touching `.pages` would be the opposite of streaming.
 */
class DesktopDocument(
    val file: File,
    val source: PageSource,
    model: Document,
    val container: DesktopContainer,
    /** Where this document's pictures live: bundled bytes, a ZIP package's extraction, or its folder. */
    val backdrop: PageBackdrop,
    /** What a `.mobix` write has to keep: the container, the board's cells and its `canvas.json`. */
    val shape: DocumentEncoder.SaveShape = DocumentEncoder.SaveShape(),
    /**
     * The file whose bytes [source]'s **lazy** pages come from — the same as [file] everywhere except a
     * **recovered** tab, whose pages were read from its crash snapshot while [file] is the origin a save
     * writes back to.
     *
     * A `.mobix` save repacks over the archive its pages came from and copies the pages nobody touched.
     * With the two confused, a recovered tab's save would copy the *origin's* untouched-looking page over
     * the recovered edit; this is the field that keeps the repack source and the destination apart.
     */
    val pageSource: File = file,
) : AutoCloseable {

    /**
     * The **editable** page window over [source]: a bounded read cache plus the overlay of pages an
     * edit produced, which eviction never touches (`mobix-core`'s [PageWindow], the same one the
     * tablet's canvas edits through). A `.mobix` edit therefore inflates its own page and nothing else,
     * and a page the user just drew on is the page the painter, the thumbnail and the save see.
     *
     * It is re-pointed when the page **list** changes ([replacePages]): a window is an index over one
     * source, so an inserted or removed page is a new window over the new list, not an edit to the old
     * one. The cache that comes back is cold, which is what a structural change costs.
     */
    var window: PageWindow = PageWindow(source)
        private set

    /**
     * The document **model** the save writes — the same object [source] reads its pages through, so a
     * save cannot disagree with what the window shows. A `.mobix`'s pages are lazy: the model describes
     * the manifest, and only a write (or a scroll) inflates a page. An edit replaces exactly its own
     * page through [Document.withPage], so a two-hundred-page notebook is never walked to change one of
     * them, and the save's reuse plan (`MobixPageReuse`) can still tell the untouched pages apart.
     */
    var model: Document = model
        private set

    /**
     * How many edits the window has taken — **snapshot state**, because the painter and the thumbnails
     * cache a page per index and must be told that the page they cached is not the page any more.
     */
    var edits: Int by mutableStateOf(0)
        private set

    init {
        // A pdf page names the document's one PDF on the page that carries the reference and nowhere else
        // (`docs/pdf.md`), so the backdrop is told how to read those pages — once, here, rather than by
        // every painter. It reads [model], so a merge that re-points the reference is answered without
        // rebuilding the backdrop.
        backdrop.pdfReference = { PdfPageReferences.of(model.pages) }
    }

    val pageCount: Int get() = window.pageCount

    /** The page at [index] — the edit if there is one, else decoded now for a `.mobix`, in memory for a `.xopp`. */
    fun page(index: Int): Page = window.page(index)

    /**
     * Apply [transform] to page [index] as one edit, through the window, and adopt the result into
     * [model] — the one path an editing tool writes through.
     *
     * @return the page the edit produced, or null when [transform] handed the same page back (a command
     *   that changed nothing), in which case neither the model nor the edit count moved.
     */
    fun editPage(index: Int, transform: (Page) -> Page): Page? {
        val edited = window.edit(index, transform) ?: return null
        model = model.withPage(index, edited)
        edits++
        return edited
    }

    /**
     * The document as it is **now**, cheaply — the snapshot a per-tab undo history records before an
     * edit (`EditHistory`).
     *
     * Both halves are already immutable and share their pages with the documents around them: the model
     * is a `Document` whose edit is an overlay over the reader's own page list, and the window's overlay
     * holds the pages those edits produced. Copying the overlay is one map copy, not a page copy — a
     * 200-page notebook snapshots at the cost of the pages a session touched.
     */
    fun snapshot(): Snapshot = Snapshot(model, window.dirtyPages)

    /**
     * Adopt [snapshot] wholesale — what undo and redo call.
     *
     * The window is **reset first** and then given the snapshot's own edited pages: its read cache and
     * overlay describe pages the document no longer has, and a redo rebuilt from a stale overlay would
     * resurrect an edit that had been undone. Untouched pages are read through the source again, so an
     * undo never materialises the rest of a notebook either.
     */
    fun restore(snapshot: Snapshot) {
        // A structural step (an added or removed page) changed the list itself, and an index over the
        // old list cannot answer the new one: the window is rebuilt from the snapshot's own pages.
        model = snapshot.model
        window = PageWindow(PageSource.of(snapshot.model.pages))
        for ((index, page) in snapshot.overlay) window.set(index, page)
        edits++
    }

    /**
     * Adopt a **whole page list** — what an added, removed or duplicated page is.
     *
     * The list is kept as it is handed over: a `.mobix`'s pages are a lazy view of its reader, and the
     * insert/remove helpers (`PageOps`) are the only things that wrap it, element by element, so a
     * 200-page notebook still inflates only the page someone reads. The caller records its undo step
     * before calling (`DesktopTab.record`), exactly as for an edit to one page.
     */
    fun replacePages(pages: List<Page>) {
        model = model.copy(pages = pages)
        window = PageWindow(PageSource.of(pages))
        edits++
    }

    /** A document at one instant: the model a save would write and the pages the window produced. */
    class Snapshot internal constructor(val model: Document, val overlay: Map<Int, Page>)

    /**
     * How many ink strokes the whole document holds, or **null when counting them would cost a full
     * read** — a `.mobix` page count is free, a `.mobix` *ink* count is not, and the preview's header
     * must not turn opening a notebook into inflating every page of it.
     */
    val strokeCount: Int?
        get() = when (container) {
            DesktopContainer.MOBIX -> null
            else -> (0 until pageCount).sumOf { index ->
                page(index).layers.sumOf { layer -> layer.elements.count { it is Stroke } }
            }
        }

    /**
     * The shape a save to [target] must keep. A native document repacks over itself when the write
     * lands back on the file it was opened from — unchanged entries stream out of that archive instead
     * of being rebuilt — and everywhere else it still carries the `assets/` entries its pages name.
     */
    fun saveShapeFor(target: File): DocumentEncoder.SaveShape = shape.copy(
        carriedFrom = pageSource,
        repackOver = target.takeIf { it.absoluteFile == pageSource.absoluteFile },
    )

    /**
     * The PDF this document's pages sit on, **when its bytes are a file of their own** — what a save has to
     * hand the encoder so the PDF travels with the document (`docs/pdf.md`).
     *
     * Null is the answer that matters as much as a file: a `.mobix` bundles its background and a zipped
     * `.xopp` attaches it, so those bytes already travel inside [pageSource] and passing them again would
     * write a second entry. A document whose reference is missing answers null too — there is nothing to
     * carry, and the page simply paints as a plain sheet.
     *
     * Only the page that carries the reference is asked (the rest name the PDF by `pageno` alone —
     * [PdfPageReferences]), and it is read through [model] rather than the whole list: a `.mobix` page list
     * is a lazy view, and the first page of a PDF-backed document is the one that answers.
     *
     * The carrier is looked for **anywhere** in the list, not on page one: reordering or removing pages can
     * leave the reference on any page, and a save must still carry the bytes.
     */
    fun pdfSource(): File? {
        val background = pdfBackground() ?: return null
        if (background.bundledAsset != null || background.domain == ATTACH_DOMAIN) return null
        val name = background.filename?.takeIf { it.isNotBlank() } ?: return null
        val path = File(name)
        if (path.isAbsolute) return path.takeIf { it.isFile }
        // A reference written beside the document (a `.xopp` on another machine, or ours after a save).
        return file.parentFile?.let { File(it, name) }?.takeIf { it.isFile }
    }

    /**
     * The background that carries this document's PDF reference, or null when it has no PDF background.
     *
     * This is what a page that only names a `pageno` sits on ([PdfPageReferences.of]); the backdrop asks for
     * it while painting, and a save asks for it to carry the bytes.
     */
    fun pdfBackground(): Background.Pdf? = PdfPageReferences.of(model.pages)

    /** True once [close] ran — a tab that was replaced or deduplicated must not keep its reader alive. */
    var isClosed: Boolean = false
        private set

    override fun close() {
        if (isClosed) return
        isClosed = true
        source.close()
    }
}

/**
 * Opening a file, deciding **by its bytes** and not by its name.
 *
 * The picker offers `.xopp` and `.mobix`, but the extension is not trusted: SAF hands the Android app
 * name-less URIs, and a desktop user renames files. So the leading bytes pick the family
 * ([FileKind.of]), and a ZIP is then opened far enough to read its `mimetype` entry — the one place the
 * format states what it is. A `.mobix` named `.xopp` opens as a notebook; a ZIP `.xopp` named `.mobix`
 * opens as a `.xopp`.
 *
 * Each unsupported case is reported as itself (`unrecognised format`, `ZIP archive with no
 * content.xml`) instead of failing deep inside a reader.
 */
object DocumentLoader {

    /** Open [file] and hand back a document whose pages are read through [DesktopDocument.source]. */
    fun load(file: File): DesktopDocument {
        // A path that is not there is a **document** failure, not a programming error: it is what a launch
        // argument looks like when Windows split it, what a cloud placeholder looks like before it is
        // downloaded, and what a shortcut to a moved file looks like. `describeOpenFailure` words it.
        if (!file.isFile) throw FileNotFoundException(file.path)
        return when (val kind = FileKind.of(leadingBytes(file))) {
            FileKind.ZIP -> openZip(file)
            FileKind.GZIP, FileKind.XML -> {
                val document = Xopp.open(file.inputStream().buffered())
                DesktopDocument(
                    file = file,
                    source = PageSource.of(document),
                    model = document,
                    container = DesktopContainer.XOPP_GZIP,
                    // A gzip `.xopp` keeps its pictures beside it: the reference is a path, nothing is bundled.
                    backdrop = PageBackdrop(bundled = { null }, documentDir = file.parentFile),
                )
            }
            FileKind.PDF -> throw UnsupportedOperationException(unsupported("a PDF", kind))
            FileKind.IMAGE -> throw UnsupportedOperationException(unsupported("an image", kind))
            FileKind.TEXT -> throw UnsupportedOperationException(unsupported("a text file", kind))
            FileKind.UNKNOWN -> throw UnsupportedOperationException(unsupported("an unknown format", kind))
        }
    }

    /**
     * A ZIP is either a native `.mobix` or a self-contained `.xopp`, and only its `mimetype` entry says
     * which. The entry is read directly rather than through a reader, so the wrong guess cannot leave a
     * half-open document behind.
     */
    /**
     * The container [load] would open [file] as, read from its **bytes** without decoding the document —
     * or null when the bytes are not a document this window opens.
     *
     * Used by the crash recovery, which needs to know what a tab's own file is (a `.mobix` or a `.xopp`)
     * before it adopts a snapshot standing in for it: saving must write the user's container, not the
     * snapshot's.
     */
    fun containerOf(file: File): DesktopContainer? = runCatching {
        when (val kind = FileKind.of(leadingBytes(file))) {
            FileKind.GZIP, FileKind.XML -> DesktopContainer.XOPP_GZIP
            FileKind.ZIP -> {
                val (declared, hasContentXml) = declaration(file)
                when {
                    declared == MobixFormat.MIMETYPE -> DesktopContainer.MOBIX
                    hasContentXml -> DesktopContainer.XOPP_ZIP
                    else -> null
                }
            }
            else -> null
        }
    }.getOrNull()

    /**
     * Open the crash [snapshot] as the tab for [origin] — what a recovered tab holds.
     *
     * The pages come from the snapshot (they are the edits), while the **file**, the container and what
     * a save has to carry stay the origin's: saving a recovered `.xopp` must write a `.xopp` back to the
     * name the user knows, never a `.mobix` archive with a `.xopp` name. Backgrounds travel inside the
     * snapshot (`DesktopRecovery` writes a self-contained `.mobix`), so a PDF or a picture still paints;
     * an *external* reference beside a `.xopp` is the one thing a snapshot cannot carry, and it comes
     * back when the file itself is reopened.
     */
    fun loadRecovered(snapshot: File, origin: File): DesktopDocument {
        val recovered = load(snapshot)
        return DesktopDocument(
            file = origin,
            source = recovered.source,
            model = recovered.model,
            container = containerOf(origin) ?: recovered.container,
            backdrop = recovered.backdrop,
            shape = recovered.shape,
            // The pages are the snapshot's: a save must rebuild from them, never repack the origin's
            // entries over the edits (which would write the old page back under a clean-looking save).
            pageSource = snapshot,
        )
    }

    /**
     * Open [staged] — a document this window wrote — as the tab for [origin].
     *
     * Used when a save's **replace** failed: the bytes in [staged] are the document the author has (the
     * write produced them whole, which is what made the staged file worth keeping), while [origin] still
     * holds the version before the edits. Reading the staged copy keeps every page, and the destination
     * stays [origin] so the next save writes back where the user knows the file is. The container is the
     * staged file's own — this window wrote it, in the container the tab already had.
     */
    fun adopt(origin: File, staged: File): DesktopDocument {
        val copy = load(staged)
        return DesktopDocument(
            file = origin,
            source = copy.source,
            model = copy.model,
            container = copy.container,
            backdrop = copy.backdrop,
            shape = copy.shape,
            pageSource = staged,
        )
    }

    private fun openZip(file: File): DesktopDocument {
        val (declared, hasContentXml) = declaration(file)
        return when {
            declared == MobixFormat.MIMETYPE ->
                openMobix(file)
            hasContentXml -> openZippedXopp(file)
            else -> throw UnsupportedOperationException(
                "This ZIP archive is not a document: it carries neither the .mobix mimetype nor content.xml.",
            )
        }
    }

    private fun openMobix(file: File): DesktopDocument = openMobix(file, MobixEntrySource.fromFile(file))

    /**
     * A `.mobix` opened over [entries] — the file's own channel everywhere except in a test that wraps
     * it to count which page entries an edit inflates (the same `CountingEntrySource` the core's suites
     * use). Production always takes the one-argument form above; nothing else may pass a source.
     */
    internal fun openMobix(file: File, entries: MobixEntrySource): DesktopDocument {
        val reader = MobixPagedReader(entries)
        return try {
            val mobix = MobixDocument.openPaged(reader)
            val model = mobix.toDocument()
            val cells = mobix.chunks.map { MobixChunk(it.cx, it.cy) }
            DesktopDocument(
                file = file,
                source = PageSource.of(model),
                model = model,
                container = DesktopContainer.MOBIX,
                // The board the file declared: a chunked document stays chunked, at its own cells, and a
                // `.xopp` export slices it into the sheets those cells add up to.
                shape = DocumentEncoder.SaveShape(
                    mode = mobix.manifest.canvasMode,
                    chunkCoords = cells.map { it.cx to it.cy },
                    canvas = mobix.canvas,
                    geometry = CanvasGeometry(
                        mode = mobix.manifest.canvasMode,
                        widthPt = mobix.canvas.widthPt,
                        heightPt = mobix.canvas.heightPt,
                        chunkSizePt = mobix.canvas.chunkSizePt,
                        chunkCoords = cells.map { it.cx to it.cy },
                        originOffsetPt = mobix.canvas.origin.offsetPt,
                    ).takeIf { mobix.manifest.canvasMode != com.mobixournal.format.mobix.CanvasMode.PAGINATED },
                ),
                // A native document bundles its pictures, and still carries the `.xopp` reference they came
                // from: the bundle is tried first so the file stands alone, the folder is the fallback.
                backdrop = PageBackdrop(bundled = reader.assetSource::bytes, documentDir = file.parentFile),
            )
        } catch (failure: Throwable) {
            // The reader owns the file handle; a rejected archive must not keep it open.
            reader.close()
            throw failure
        }
    }

    /** What a ZIP declares: its `mimetype` entry (null when absent) and whether it holds `content.xml`. */
    private fun declaration(file: File): Pair<String?, Boolean> = ZipFile(file).use { zip ->
        val mimetype = zip.getEntry("mimetype")?.let { entry ->
            zip.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8).trim() }
        }
        mimetype to (zip.getEntry("content.xml") != null)
    }

    private fun openZippedXopp(file: File): DesktopDocument {
        val loaded = XoppZip.open(
            file.inputStream().buffered(),
            pdfFile = {
                // The package's background has to land somewhere: the codec hands back a file the caller
                // allocated, never a shared name (two open packages must not overwrite each other).
                Files.createTempFile("mobixournal-desktop-bg", ".pdf").toFile().also { it.deleteOnExit() }
            },
            imageFile = { Files.createTempFile("mobixournal-desktop-img", ".bin").toFile().also { it.deleteOnExit() } },
        )
        return DesktopDocument(
            file = file,
            source = PageSource.of(loaded.doc),
            model = loaded.doc,
            container = DesktopContainer.XOPP_ZIP,
            // A packaged `.xopp` names its entries, and the codec has just extracted them for us.
            backdrop = PageBackdrop(
                bundled = { null },
                documentDir = file.parentFile,
                extracted = { name -> loaded.images[name] ?: loaded.pdf?.takeIf { name == XoppZip.PDF_ENTRY } },
            ),
        )
    }

    private fun unsupported(what: String, kind: FileKind): String =
        "This file looks like $what (${kind.name.lowercase()}), not a MobiXournal document. " +
            "This preview opens .xopp (gzip or ZIP) and .mobix."

    /** The signature bytes [FileKind.of] classifies; the buffer is trimmed to what was actually read. */
    private fun leadingBytes(file: File): ByteArray {
        val buffer = ByteArray(FileKind.MAGIC_BYTES)
        val read = file.inputStream().use { it.read(buffer) }
        return if (read < buffer.size) buffer.copyOf(read) else buffer
    }
}
