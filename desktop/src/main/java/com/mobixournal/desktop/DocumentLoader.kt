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
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.withPage
import com.mobixournal.io.DocumentEncoder
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
) : AutoCloseable {

    /**
     * The **editable** page window over [source]: a bounded read cache plus the overlay of pages an
     * edit produced, which eviction never touches (`mobix-core`'s [PageWindow], the same one the
     * tablet's canvas edits through). A `.mobix` edit therefore inflates its own page and nothing else,
     * and a page the user just drew on is the page the painter, the thumbnail and the save see.
     */
    val window: PageWindow = PageWindow(source)

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

    val pageCount: Int get() = source.pageCount

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
        model = snapshot.model
        window.reset()
        for ((index, page) in snapshot.overlay) window.set(index, page)
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
        carriedFrom = file,
        repackOver = target.takeIf { it.absoluteFile == file.absoluteFile },
    )

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
        require(file.isFile) { "File not found: ${file.path}" }
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
    private fun openZip(file: File): DesktopDocument {
        val (declared, hasContentXml) = ZipFile(file).use { zip ->
            val mimetype = zip.getEntry("mimetype")?.let { entry ->
                zip.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8).trim() }
            }
            mimetype to (zip.getEntry("content.xml") != null)
        }
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
