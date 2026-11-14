package com.mobixournal.desktop.io
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.sheet.number

import java.io.File
import java.nio.file.Files
import org.apache.pdfbox.io.MemoryUsageSetting
import org.apache.pdfbox.multipdf.PDFMergerUtility

/**
 * **Joining two PDFs into one**, because a `.xopp` can reference exactly one.
 *
 * The rule is `docs/pdf.md`'s and the tablet already keeps it (`render/PdfMerger`): appending a PDF to a
 * document that is already backed by one does not add a second reference, it joins the two files and
 * re-numbers the arriving pages inside the joined one. That file is `pdfbox-android` (`com.tom_roush`) and
 * cannot run on the JVM, so this is its twin over the desktop's own `org.apache.pdfbox` — the same call,
 * the same refusal to write over a source, kept in one place so nothing else in the window merges.
 *
 * **Where the joined file goes.** It is scratch, not a document: a save carries it into the file when the
 * target can hold it (`.mobix` bundles it, a zipped `.xopp` attaches it), and a plain `.xopp` names it by
 * path — which is why [DesktopPdfSources] publishes it out of this folder when that happens.
 *
 * **Every join is a new file.** The output used to be named after the base PDF, so two tabs built on the
 * same source overwrote each other's joined file — and a tab whose pages still named it silently lost its
 * background, or, worse, painted the other tab's pages. An allocation that is never reused is the only
 * shape that cannot do that, and it costs nothing: the files are scratch and a save publishes the one it
 * needs.
 */
internal object DesktopPdfMerge {

    /** The folder joined PDFs are written to: one allocation each, never a shared name. */
    private fun scratch(): File = File(base(), "import").also { it.mkdirs() }

    /**
     * The folder this object's files live under — `%TEMP%/mobixournal-desktop` normally, and whatever
     * [SCRATCH_PROPERTY] names when a suite asks, so a test never writes into the installed window's.
     */
    private fun base(): File = System.getProperty(SCRATCH_PROPERTY)?.takeIf { it.isNotBlank() }?.let(::File)
        ?: File(System.getProperty("java.io.tmpdir"), "mobixournal-desktop")

    /** Whether [file] is one of this object's own merge outputs — scratch a save has to publish. */
    fun isScratch(file: File): Boolean = runCatching {
        file.parentFile?.canonicalFile == scratch().canonicalFile
    }.getOrDefault(false)

    /**
     * Join [added] after [base] and answer the file that holds both.
     *
     * The answer is a **fresh** file every time ([scratch]), so a second append to another tab built on
     * the same PDF cannot rewrite the first tab's background. The name still says where it came from
     * (`chapter-1234567.pdf` from `chapter.pdf`), because a reference a reader meets in a saved document
     * should be recognisable.
     */
    fun joined(base: File, added: File): File =
        Files.createTempFile(scratch().toPath(), "${base.nameWithoutExtension}-", ".pdf")
            .toFile()
            .also { join(base, added, it) }

    /**
     * [bytes] written into a fresh scratch file called after [name] — the base of a join whose source the
     * document carries **inside** it (a bundled `.mobix` background) and has no file of its own on disk.
     */
    fun copyOf(bytes: ByteArray, name: String): File =
        Files.createTempFile(scratch().toPath(), "${name.ifBlank { "background" }}-", ".pdf")
            .toFile()
            .also { it.writeBytes(bytes) }

    /** [base] followed by [added], written to [out] — which must differ from both sources. */
    fun join(base: File, added: File, out: File) {
        require(out.absoluteFile != base.absoluteFile && out.absoluteFile != added.absoluteFile) {
            "a merge destination must differ from its sources"
        }
        PDFMergerUtility().apply {
            destinationFileName = out.absolutePath
            addSource(base)
            addSource(added)
            mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
        }
    }

    /** The system property that moves the merge scratch folder, so a suite keeps its own. */
    const val SCRATCH_PROPERTY = "mobixournal.desktop.pdfScratch"
}
