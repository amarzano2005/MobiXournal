package com.mobixournal.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The four states of Select text (PDF)**, and the sentence each one owes.
 *
 * The claim these tests defend is that a reader is never told the wrong fact: a page still being read is
 * not a page without text, a scan is not a failure, and a failure is the only state that offers a retry.
 */
class PdfTextAvailabilityTest {

    @Test
    fun `a page that is not a PDF says nothing at all`() {
        val failed = PdfTextAvailability(PdfTextPhase.FAILED, failure = "denied")
        assertNull("the slot is not about a plain page", failed.notice(pageShowsPdf = false))
        assertNull(PdfTextAvailability(PdfTextPhase.INDEXING).notice(pageShowsPdf = false))
    }

    @Test
    fun `indexing says it is reading, and offers no retry to a read that is working`() {
        val indexing = PdfTextAvailability(PdfTextPhase.INDEXING)
        assertEquals(PdfTextAvailability.INDEXING_SENTENCE, indexing.notice(pageShowsPdf = true))
        assertFalse("a working read is not a failure", indexing.canRetry)
        assertFalse(indexing.canSelect)
    }

    @Test
    fun `a ready sheet with words says nothing, because the tool can just do its job`() {
        val ready = PdfTextAvailability(PdfTextPhase.READY, wordsOnPage = 12)
        assertNull(ready.notice(pageShowsPdf = true))
        assertTrue(ready.canSelect)
        assertFalse(ready.canRetry)
    }

    @Test
    fun `a ready sheet with no words is a scan, and says so instead of staying silent`() {
        val scan = PdfTextAvailability(PdfTextPhase.READY, wordsOnPage = 0)
        assertEquals(PdfTextAvailability.NO_TEXT_SENTENCE, scan.notice(pageShowsPdf = true))
        assertFalse("a scan is not a failed read", scan.canRetry)
        assertFalse(scan.canSelect)
    }

    @Test
    fun `a failed read carries its reason and the one action that helps`() {
        val failed = PdfTextAvailability(PdfTextPhase.FAILED, failure = "Permission denied")
        assertEquals("Could not read this PDF's text (Permission denied).", failed.notice(pageShowsPdf = true))
        assertTrue(failed.canRetry)
        assertFalse(failed.canSelect)
    }

    @Test
    fun `a failure with no reason still reads as a sentence, not as a stray colon`() {
        val failed = PdfTextAvailability(PdfTextPhase.FAILED, failure = "  ")
        assertEquals("Could not read this PDF's text.", failed.notice(pageShowsPdf = true))
    }

    @Test
    fun `an idle slot says nothing even on a PDF page`() {
        assertNull(PdfTextAvailability().notice(pageShowsPdf = true))
        assertFalse(PdfTextAvailability().canSelect)
    }
}
