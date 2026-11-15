package com.mobixournal.render

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `PdfSheetRef` — which PDF sheet a page draws, and why it is the source's own identity rather than the page's
 * place in the notebook.
 *
 * The mapping is what a text selection reads its words through, so a reordered page, two pages showing one
 * sheet, and a paper page inside a PDF document each have to answer what they actually show. Reordering was a
 * silent wrong answer before: the selection asked page *n* of the index, and page *n* is not where a moved page's
 * sheet sits.
 */
class PdfSheetRefTest {

    private fun page(background: Background, id: String) =
        Page(width = 595.0, height = 842.0, background = background, layers = listOf(Layer(emptyList())), id = id)

    private fun external(pageNo: Int, file: String = "/tmp/notes.pdf") =
        Background.Pdf(filename = file, pageNo = pageNo, domain = "absolute")

    @Test
    fun aPdfPageNamesItsSheetByPageNumber() {
        val ref = PdfSheetRef.of(page(external(7), "pg_1"))
        assertEquals(7, ref?.pageNo)
        assertEquals("/tmp/notes.pdf|absolute", ref?.source)
    }

    @Test
    fun aPaperPageHasNoSheet() {
        assertNull(PdfSheetRef.of(page(Background.Solid(color = -1, style = "plain", config = ""), "pg_1")))
    }

    @Test
    fun reorderingThePagesDoesNotMoveTheirSheets() {
        val pages = listOf(page(external(2), "pg_a"), page(external(0), "pg_b"))
        assertEquals("the sheets follow their own page numbers", listOf(2, 0), pages.map { PdfSheetRef.of(it)?.pageNo })
        val reordered = pages.reversed()
        assertEquals("and a reorder changes nothing about the sheet", listOf(0, 2), reordered.map { PdfSheetRef.of(it)?.pageNo })
        assertEquals(
            "a page is resolved to the sheet it draws, not to its index",
            listOf("pg_b" to 0, "pg_a" to 2),
            reordered.map { it.id to (PdfSheetRef.of(it)?.pageNo ?: -1) },
        )
    }

    @Test
    fun twoPagesOnOneSheetShareItAndAnotherSourceDoesNot() {
        val bundled = Background.Pdf(
            filename = null,
            pageNo = 3,
            domain = null,
            bundledAsset = "background/background.pdf",
        )
        assertEquals(PdfSheetRef.of(page(bundled, "pg_a")), PdfSheetRef.of(page(bundled, "pg_b")))
        val otherBundle = bundled.copy(bundledAsset = "other/background.pdf")
        assertNotEquals(PdfSheetRef.of(page(bundled, "pg_a")), PdfSheetRef.of(page(otherBundle, "pg_a")))
        assertNotEquals(
            "two files that both show page 3 are two sheets",
            PdfSheetRef.of(page(external(3, "/tmp/a.pdf"), "pg_a")),
            PdfSheetRef.of(page(external(3, "/tmp/b.pdf"), "pg_b")),
        )
    }

    @Test
    fun aBundledCopyIsNamedByItsAssetRatherThanAScratchPath() {
        val bundled = Background.Pdf(
            filename = "/tmp/scratch/joined.pdf",
            pageNo = 0,
            domain = "absolute",
            bundledAsset = "background/background.pdf",
        )
        assertEquals(
            "the entry the bytes travel under outlives the scratch path",
            "background/background.pdf|/tmp/scratch/joined.pdf|absolute",
            PdfSheetRef.of(page(bundled, "pg_1"))?.source,
        )
    }
}
