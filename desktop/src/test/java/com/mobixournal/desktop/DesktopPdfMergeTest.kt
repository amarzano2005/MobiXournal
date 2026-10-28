package com.mobixournal.desktop

import com.mobixournal.format.model.Background
import com.mobixournal.render.ABSOLUTE_DOMAIN
import java.io.File
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * **Two PDFs joined into one**, because a `.xopp` references one (`docs/pdf.md`).
 *
 * The joining itself is PDFBox's; what this suite holds is the arithmetic around it — that the sources are
 * left exactly as they were (a merge that rewrote its own input would destroy a reader's file), that the
 * arriving pages keep counting where the base stopped so every `pageno` still names its page, and that the
 * document's one reference is re-pointed at the joined file.
 */
class DesktopPdfMergeTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun pdf(name: String, pages: Int): File = File(temp.root, "$name.pdf").also { file ->
        PDDocument().use { document ->
            repeat(pages) { document.addPage(PDPage(PDRectangle.A4)) }
            document.save(file)
        }
    }

    @Test
    fun `two files become one and the sources are untouched`() {
        val base = pdf("base", 2)
        val added = pdf("added", 3)
        val baseBytes = base.readBytes()
        val addedBytes = added.readBytes()

        val joined = DesktopPdfMerge.joined(base, added)

        PDDocument.load(joined).use { assertEquals("both files are in the joined one", 5, it.numberOfPages) }
        assertArrayEquals("the base is still the base", baseBytes, base.readBytes())
        assertArrayEquals("and the arriving file is still itself", addedBytes, added.readBytes())
    }

    @Test
    fun `the arriving pages keep counting where the base stopped`() {
        val base = pdf("base", 2)
        val added = pdf("added", 3)
        val joined = DesktopPdfMerge.joined(base, added)

        DesktopPdfImport.open(joined).use { joinedPdf ->
            val offset = DesktopPdfImport.pageCount(base)
            // The arriving run is the **tail** of the joined file: the page at index `offset` is the first
            // one the import brought, and the number it names is its own place in that file.
            val arriving = DesktopPdfImport.pages(joinedPdf, reference = null).drop(offset)
            assertEquals(
                "every page still names the page it was made from",
                listOf(2, 3, 4),
                arriving.map { (it.background as Background.Pdf).pageNo },
            )
        }
    }

    @Test
    fun `the document's one reference is re-pointed at the joined file`() {
        val base = pdf("base", 1)
        val added = pdf("added", 1)
        val joined = DesktopPdfMerge.joined(base, added)

        val before = DesktopPdfImport.open(base).use { DesktopPdfImport.document(it, base.absolutePath) }
        val after = DesktopPdfImport.retarget(before.pages, joined)

        val reference = after.first().background as Background.Pdf
        assertEquals("the opened page names the file that holds every page", joined.absolutePath, reference.filename)
        assertEquals("and it is still an absolute reference", ABSOLUTE_DOMAIN, reference.domain)
        assertEquals("the pages it does not belong to are untouched", 1, after.size)
    }

    @Test
    fun `a merge never writes over its own source`() {
        val base = pdf("base", 1)
        val added = pdf("added", 1)
        val refused = runCatching { DesktopPdfMerge.join(base, added, base) }.exceptionOrNull()
        assertTrue("the destination must differ from both sources: $refused", refused is IllegalArgumentException)
    }
}
