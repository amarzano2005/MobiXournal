package com.mobixournal.desktop

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
 * target can hold it (`.mobix` bundles it, a zipped `.xopp` attaches it — [com.mobixournal.io.DocumentEncoder]),
 * and a plain `.xopp` keeps its path. So it is written into the import scratch folder the window owns,
 * named after the document's own PDF, and it outlives the session only as long as the OS leaves the temp
 * folder alone. That trade-off is the tablet's too: its store is a cache, and the file that must survive
 * is the one the reader saves.
 */
internal object DesktopPdfMerge {

    /** The folder joined PDFs are written to, one per base, reused so a second append does not pile up. */
    private fun scratch(): File = File(
        File(System.getProperty("java.io.tmpdir"), "mobixournal-desktop"),
        "import",
    ).also { it.mkdirs() }

    /**
     * Join [added] after [base] and answer the file that holds both.
     *
     * The name is the base's own plus a stamp of **its own path**, so the reference a reader sees in the
     * document stays recognisable (`chapter.pdf` → `chapter-a1b2c3.pdf`) while two documents that both bring
     * a `chapter.pdf` from different folders cannot overwrite each other's joined file. A second join over
     * the same base rewrites its own — which is what appending twice to one document means.
     */
    fun joined(base: File, added: File): File {
        val stamp = base.absolutePath.hashCode().toUInt().toString(16)
        val out = File(scratch(), "${base.nameWithoutExtension}-$stamp.pdf")
        // The merge reads its sources while it writes, so the destination may be neither of them.
        if (out.absoluteFile == base.absoluteFile || out.absoluteFile == added.absoluteFile) {
            return File(Files.createTempFile(scratch().toPath(), "joined-", ".pdf").toFile().absolutePath)
                .also { join(base, added, it) }
        }
        join(base, added, out)
        return out
    }

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
}
