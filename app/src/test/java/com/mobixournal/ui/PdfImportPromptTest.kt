package com.mobixournal.ui

import com.mobixournal.render.ImportPdfMode
import com.mobixournal.render.PageRangePlan
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The tablet's import question** — what it opens on, what the field means, and what the *Add* / *Replace*
 * answers keep while the field is being edited.
 *
 * The card is a value, not a composable: the answer and the selection live in [PdfImportPrompt], so a
 * re-layout (a rotation, the keyboard, a narrower pane) cannot forget which answer was picked, and the same
 * field is what [MainActivityDocuments.confirmPdfImport] reads.
 */
class PdfImportPromptTest {

    private fun prompt(pages: String = "1-5", count: Int = 5) = PdfImportPrompt(
        file = File("handout.pdf"),
        reference = "content://handout.pdf",
        name = "handout.pdf",
        pageCount = count,
        pages = pages,
    )

    @Test
    fun `a card opens on the whole file and names how many pages that is`() {
        val fresh = PdfImportPrompt(File("a.pdf"), "content://a", "a.pdf", 108)
        assertEquals("1-108", fresh.pages)
        assertEquals(108, fresh.count)
        assertEquals((0 until 108).toList(), fresh.chosen)
        assertEquals(ImportPdfMode.APPEND, fresh.mode)
        assertEquals("filename markers require explicit opt-in", false, fresh.bookmark)
    }

    @Test
    fun `a selection of several runs is what the command imports`() {
        val chosen = prompt(pages = "2,4-5").chosen
        assertEquals(listOf(1, 3, 4), chosen)
        assertEquals(3, prompt(pages = "2,4-5").count)
    }

    @Test
    fun `the answer survives a page number being edited`() {
        val picked = prompt().let { it.copy(mode = ImportPdfMode.REPLACE) }
        val edited = picked.copy(pages = "3")
        assertEquals("the picked answer is still the picked answer", ImportPdfMode.REPLACE, edited.mode)
        assertEquals(listOf(2), edited.chosen)
    }

    @Test
    fun `a page outside the file is refused and the card cannot import`() {
        val wrong = prompt(pages = "2,9")
        assertTrue(wrong.range is PageRangePlan.Invalid)
        assertNull("no pages to bring", wrong.chosen)
        assertEquals(0, wrong.count)
        assertEquals("Pages are numbered 1 to 5.", (wrong.range as PageRangePlan.Invalid).message)
    }

    @Test
    fun `an empty field is refused rather than read as the whole file`() {
        val blank = prompt(pages = "")
        assertTrue(blank.range is PageRangePlan.Invalid)
        assertNull(blank.chosen)
    }
}
