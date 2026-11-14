package com.mobixournal.desktop.previews

import java.io.File
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * **The window's import previews**: a page that is asked for becomes a picture, and one that is not asked for
 * stays unrendered.
 *
 * This is a regression test with a specific history: the grid drew numbered cells and no previews at all in
 * the packaged window, because every render ended by hopping to a `Main` dispatcher the desktop app does not
 * install — the exception was swallowed in a worker and the cell simply stayed empty. Two cells scrolled into
 * view together also rendered the same `PDFRenderer` from two threads, which PDFBox does not survive. Both are
 * invisible from the outside (an empty cell is not an error), so they are asserted from here instead.
 */
class DesktopPdfThumbnailsTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** A PDF of [pages] pages, each with a word on it, written where the card would read it. */
    private fun pdf(pages: Int): File {
        val file = File(temp.root, "preview.pdf")
        PDDocument().use { doc ->
            repeat(pages) { index ->
                val page = PDPage(PDRectangle(595f, 842f))
                doc.addPage(page)
                PDPageContentStream(doc, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 24f)
                    stream.newLineAtOffset(72f, 700f)
                    stream.showText("Page ${index + 1}")
                    stream.endText()
                }
            }
            doc.save(file)
        }
        return file
    }

    /** Wait for [pages] to be rendered: the cache answers on its own worker, so the test polls it. */
    private fun await(cache: DesktopPdfThumbnails, pages: List<Int>) {
        val deadline = System.currentTimeMillis() + 30_000
        while (pages.any { cache[it] == null } && System.currentTimeMillis() < deadline) Thread.sleep(25)
    }

    @Test
    fun aPageThatIsAskedForBecomesAPreviewAndOneThatIsNotStaysEmpty() {
        DesktopPdfThumbnails(pdf(pages = 3)).use { cache ->
            assertEquals("the card counts the file's pages", 3, cache.pageCount)
            assertNull("nothing is rasterised before a cell asks", cache[0])

            cache.request(0)
            cache.request(2)
            await(cache, listOf(0, 2))

            assertNotNull("the first page's preview arrives", cache[0])
            assertNotNull("and the third's", cache[2])
            assertNull("while the page nobody scrolled to is never rendered", cache[1])
        }
    }

    @Test
    fun everyCellOfARowRenderedAtOnceGetsItsOwnPreview() {
        // Four requests on one worker, the way a row of cells arrives: a `PDFRenderer` shared across threads
        // answers this with failed rasters, which used to be four empty cells and no message.
        DesktopPdfThumbnails(pdf(pages = 6)).use { cache ->
            (0 until 4).forEach { cache.request(it) }
            await(cache, (0 until 4).toList())

            (0 until 4).forEach { page ->
                assertNotNull("page ${page + 1} has a preview", cache[page])
            }
            assertNull("and the two below the fold are still unrendered", cache[5])
        }
    }

    @Test
    fun askingTwiceForTheSamePageRendersItOnceAndClosingReleasesEverything() {
        val cache = DesktopPdfThumbnails(pdf(pages = 2))
        cache.request(0)
        cache.request(0)
        await(cache, listOf(0))
        assertNotNull(cache[0])

        cache.close()

        assertNull("closing drops the previews", cache[0])
        // A late request after `close` must not resurrect a document that is already shut.
        cache.request(1)
        assertNull(cache[1])
    }
}
