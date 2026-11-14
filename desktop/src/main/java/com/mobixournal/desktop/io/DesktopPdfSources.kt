package com.mobixournal.desktop.io
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.sheet.number

import com.mobixournal.format.model.Document
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * **A joined PDF that outlives the session**, which is what a plain `.xopp` save needs.
 *
 * The three containers carry a background differently (`docs/pdf.md`): a `.mobix` bundles the bytes and a
 * zipped `.xopp` attaches them, so both travel with the file — but a gzip `.xopp` names the PDF by path
 * and nothing else. The join that produced that path wrote it into [DesktopPdfMerge]'s scratch folder,
 * which the OS empties whenever it likes; a document saved that way opens next week with blank pages and
 * no way to tell why.
 *
 * So a save that will **link** rather than carry publishes the source first: the bytes are copied into
 * [dir] — this window's own folder in `APPDATA`, beside its settings — and the model's reference is
 * re-pointed at the copy. Nothing else is published: a PDF the reader picked stays the file they picked,
 * because a save must not quietly move their document's background somewhere they will not look.
 */
internal object DesktopPdfSources {

    /**
     * Where published sources live: `%APPDATA%/MobiXournal/pdf`, or `~/MobiXournal/pdf` on a machine with
     * no `APPDATA`. [SOURCES_PROPERTY] moves it, so a suite publishes into its own temporary folder.
     */
    fun dir(): File {
        System.getProperty(SOURCES_PROPERTY)?.takeIf { it.isNotBlank() }
            ?.let { return File(it).apply { mkdirs() } }
        val appData = System.getenv("APPDATA")
        val base = if (!appData.isNullOrBlank()) File(appData) else File(System.getProperty("user.home"))
        return File(File(base, "MobiXournal"), "pdf").apply { mkdirs() }
    }

    /** Whether [file] already lives in [dir], in which case publishing it again would copy it onto itself. */
    fun isPublished(file: File): Boolean = runCatching {
        file.parentFile?.canonicalFile == dir().canonicalFile
    }.getOrDefault(false)

    /** A model and the PDF file its reference names after publishing — the two things a save hands on. */
    class Linked(val model: Document, val pdf: File?)

    /**
     * [model] with its one PDF reference made durable, and the file the encoder should carry.
     *
     * A source that is not this window's own scratch — the reader's PDF, or one already published — comes
     * back untouched. A join output is published and the reference re-pointed at the copy, on the page
     * that carries it, which may be any page ([DesktopPdfImport.retarget]).
     */
    fun durable(model: Document, pdf: File?): Linked {
        if (pdf == null || !pdf.isFile || !DesktopPdfMerge.isScratch(pdf)) return Linked(model, pdf)
        val published = publish(pdf)
        return Linked(model.copy(pages = DesktopPdfImport.retarget(model.pages, published)), published)
    }

    /**
     * Copy [source] into [dir] under a name that says which join it is, and answer the copy.
     *
     * The name carries a stamp of the source's **path, size and time**, so saving the same document twice
     * finds the copy it published the first time instead of writing a second identical file; a new join is
     * a new source and so a new copy.
     */
    fun publish(source: File): File {
        if (isPublished(source)) return source
        val stamp = "${source.absolutePath}|${source.length()}|${source.lastModified()}"
            .hashCode().toUInt().toString(16)
        val target = File(dir(), "${source.nameWithoutExtension}-$stamp.pdf")
        if (target.isFile && target.length() == source.length()) return target
        Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        return target
    }

    /** The system property that moves the published folder, so a suite never writes into the real one. */
    const val SOURCES_PROPERTY = "mobixournal.desktop.pdfSources"
}
