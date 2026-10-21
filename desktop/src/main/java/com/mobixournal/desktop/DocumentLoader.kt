package com.mobixournal.desktop

import com.mobixournal.format.FileKind
import com.mobixournal.format.Xopp
import com.mobixournal.format.XoppZip
import com.mobixournal.format.mobix.MobixEntrySource
import com.mobixournal.format.mobix.MobixFormat
import com.mobixournal.format.mobix.MobixPagedReader
import com.mobixournal.format.mobix.PageSource
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
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
    val container: DesktopContainer,
) : AutoCloseable {

    val pageCount: Int get() = source.pageCount

    /** The page at [index] — decoded now for a `.mobix`, already in memory for a `.xopp`. */
    fun page(index: Int): Page = source.page(index)

    /**
     * How many ink strokes the whole document holds, or **null when counting them would cost a full
     * read** — a `.mobix` page count is free, a `.mobix` *ink* count is not, and the preview's header
     * must not turn opening a notebook into inflating every page of it.
     */
    val strokeCount: Int?
        get() = when (container) {
            DesktopContainer.MOBIX -> null
            else -> (0 until pageCount).sumOf { index ->
                source.page(index).layers.sumOf { layer -> layer.elements.count { it is Stroke } }
            }
        }

    override fun close() = source.close()
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
 * Each unsupported case is reported as itself (`formato non riconosciuto`, `archivio ZIP senza
 * content.xml`) instead of failing deep inside a reader.
 */
object DocumentLoader {

    /** Open [file] and hand back a document whose pages are read through [DesktopDocument.source]. */
    fun load(file: File): DesktopDocument {
        require(file.isFile) { "File non trovato: ${file.path}" }
        return when (val kind = FileKind.of(leadingBytes(file))) {
            FileKind.ZIP -> openZip(file)
            FileKind.GZIP, FileKind.XML ->
                DesktopDocument(file, PageSource.of(Xopp.open(file.inputStream().buffered())), DesktopContainer.XOPP_GZIP)
            FileKind.PDF -> throw UnsupportedOperationException(unsupported("un PDF", kind))
            FileKind.IMAGE -> throw UnsupportedOperationException(unsupported("un'immagine", kind))
            FileKind.TEXT -> throw UnsupportedOperationException(unsupported("un file di testo", kind))
            FileKind.UNKNOWN -> throw UnsupportedOperationException(unsupported("un formato sconosciuto", kind))
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
                "Questo archivio ZIP non è un documento: manca sia il mimetype .mobix sia content.xml.",
            )
        }
    }

    private fun openMobix(file: File): DesktopDocument {
        val reader = MobixPagedReader(MobixEntrySource.fromFile(file))
        return try {
            DesktopDocument(file, PageSource.of(reader), DesktopContainer.MOBIX)
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
                // The preview does not paint PDF backgrounds yet, but the package's background still has
                // to land somewhere: the codec hands back a file the caller allocated, never a shared name.
                Files.createTempFile("mobixournal-desktop-bg", ".pdf").toFile().also { it.deleteOnExit() }
            },
        )
        return DesktopDocument(file, PageSource.of(loaded.doc), DesktopContainer.XOPP_ZIP)
    }

    private fun unsupported(what: String, kind: FileKind): String =
        "Questo file sembra $what (${kind.name.lowercase()}), non un documento MobiXournal. " +
            "Questa anteprima apre .xopp (gzip o ZIP) e .mobix."

    /** The signature bytes [FileKind.of] classifies; the buffer is trimmed to what was actually read. */
    private fun leadingBytes(file: File): ByteArray {
        val buffer = ByteArray(FileKind.MAGIC_BYTES)
        val read = file.inputStream().use { it.read(buffer) }
        return if (read < buffer.size) buffer.copyOf(read) else buffer
    }
}
