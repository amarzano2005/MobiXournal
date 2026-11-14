package com.mobixournal.desktop

import com.mobixournal.desktop.io.DesktopPdfTextNotice
import com.mobixournal.desktop.io.DesktopPdfTextStatus
import com.mobixournal.desktop.io.pdfTextAvailabilityOf
import com.mobixournal.desktop.io.retryPdfIndex
import com.mobixournal.render.PdfTextAvailability
import com.mobixournal.render.PdfTextPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * **What the window tells a reader about Select text (PDF)** — the phases of the index behind it, and the
 * sentence each one owes.
 *
 * The claims that matter are the ones a single null index could not make: a walk still running is not a page
 * without text, a scan inside a digital document says so only on its own sheet, and a read that broke is the
 * only state that offers **Try again** — which, when used, drops the failed index so the next walk is a real
 * one rather than a cached empty answer.
 */
class DesktopPdfTextStatusTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `a walk still running is said as reading, not as a page without text`() {
        val tab = DesktopPdfFixture.tab(temp.root)
        try {
            assertEquals("nothing has been read yet", PdfTextPhase.IDLE, pdfTextAvailabilityOf(tab).phase)
            tab.pdfIndexKey = "indexing"
            DesktopPdfTextStatus.indexing("indexing")
            val availability = pdfTextAvailabilityOf(tab)
            assertEquals(PdfTextPhase.INDEXING, availability.phase)
            assertEquals(PdfTextAvailability.INDEXING_SENTENCE, availability.notice(pageShowsPdf = true))
            assertFalse("a working read offers no retry", availability.canRetry)
        } finally {
            tab.document.close()
        }
    }

    @Test
    fun `a ready document counts the words of the sheet in view`() {
        val tab = DesktopPdfFixture.tab(temp.root)
        try {
            tab.pdfIndexKey = "ready"
            DesktopPdfTextStatus.ready("ready")
            val availability = pdfTextAvailabilityOf(tab)
            assertTrue(availability.canSelect)
            assertEquals("the fixture's sheet holds two words", 2, availability.wordsOnPage)
            assertNull("a page the tool can work on says nothing", availability.notice(pageShowsPdf = true))
        } finally {
            tab.document.close()
        }
    }

    @Test
    fun `a scan in the middle of a digital document says so on its own sheet`() {
        val tab = DesktopPdfFixture.tab(
            temp.root,
            pages = 2,
            words = listOf(DesktopPdfFixture.LINE, emptyList()),
        )
        try {
            tab.pdfIndexKey = "mixed"
            DesktopPdfTextStatus.ready("mixed")
            assertEquals("the first sheet has words", 2, pdfTextAvailabilityOf(tab).wordsOnPage)
            tab.page = 1
            val scanned = pdfTextAvailabilityOf(tab)
            assertEquals(0, scanned.wordsOnPage)
            assertEquals(PdfTextAvailability.NO_TEXT_SENTENCE, scanned.notice(pageShowsPdf = true))
            assertFalse("a scan is not a failed read", scanned.canRetry)
        } finally {
            tab.document.close()
        }
    }

    @Test
    fun `a failed read offers the retry, and the retry drops the index it failed to fill`() {
        val tab = DesktopPdfFixture.tab(temp.root)
        try {
            tab.pdfIndexKey = "failed"
            DesktopPdfTextStatus.failed("failed", "the file is locked")
            val availability = pdfTextAvailabilityOf(tab)
            assertTrue(availability.canRetry)
            assertEquals(
                "Could not read this PDF's text (the file is locked).",
                availability.notice(pageShowsPdf = true),
            )
            val token = DesktopPdfTextStatus.retryToken
            tab.retryPdfIndex()
            assertNull("the failed index is forgotten, so the next walk is a real one", tab.pdfIndex)
            assertEquals("and the loader is woken", token + 1, DesktopPdfTextStatus.retryToken)
        } finally {
            tab.document.close()
        }
    }

    @Test
    fun `the status band says a scan plainly and a failure with its action`() {
        DesktopPdfTextNotice.noText()
        assertEquals(PdfTextAvailability.NO_TEXT_SENTENCE, DesktopPdfTextNotice.text)
        assertNull("a scan offers nothing to press", DesktopPdfTextNotice.retry)

        DesktopPdfTextNotice.unavailable(
            PdfTextAvailability(PdfTextPhase.FAILED, failure = "denied"),
        ) {}
        assertEquals("Could not read this PDF's text (denied).", DesktopPdfTextNotice.text)
        assertNotNull("a failed read carries its Try again", DesktopPdfTextNotice.retry)

        DesktopPdfTextNotice.clear()
        assertNull(DesktopPdfTextNotice.text)
        assertNull("and clearing clears the action too", DesktopPdfTextNotice.retry)
    }
}
