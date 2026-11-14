package com.mobixournal.render

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * **Where each PDF's text layer stands**, and the one thing this store exists for: a tab switch during
 * extraction must not let one document's answer describe another.
 *
 * The phases are per file, so the canvas that moved on reads its own — or nothing at all — while the walk
 * that finished files its news under the bytes it actually read.
 */
class PdfTextStatusTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun file(name: String) = File(temp.root, name).also { it.writeText("%PDF-1.7 test") }

    @Test
    fun `a file nobody has read is idle, and so is no file at all`() {
        assertEquals(PdfTextPhase.IDLE, PdfTextStatus.of(file("quiet.pdf")).phase)
        assertEquals(PdfTextPhase.IDLE, PdfTextStatus.of(null).phase)
    }

    @Test
    fun `the phase walks from indexing to ready`() {
        val pdf = file("walk.pdf")
        PdfTextStatus.indexing(pdf)
        assertEquals(PdfTextPhase.INDEXING, PdfTextStatus.of(pdf).phase)
        PdfTextStatus.ready(pdf)
        assertEquals(PdfTextPhase.READY, PdfTextStatus.of(pdf).phase)
    }

    @Test
    fun `a failure keeps its reason so the slot can offer the retry`() {
        val pdf = file("broken.pdf")
        PdfTextStatus.failed(pdf, "the file is locked")
        val availability = PdfTextStatus.of(pdf)
        assertEquals(PdfTextPhase.FAILED, availability.phase)
        assertTrue(availability.canRetry)
        assertEquals("Could not read this PDF's text (the file is locked).", availability.notice(pageShowsPdf = true))
    }

    @Test
    fun `two documents answer for themselves`() {
        val first = file("first.pdf")
        val second = file("second.pdf")
        PdfTextStatus.indexing(first)
        PdfTextStatus.failed(second, "locked")
        assertEquals(PdfTextPhase.INDEXING, PdfTextStatus.of(first).phase)
        assertEquals(PdfTextPhase.FAILED, PdfTextStatus.of(second).phase)
    }

    @Test
    fun `a closed document forgets its phase`() {
        val pdf = file("closed.pdf")
        PdfTextStatus.ready(pdf)
        PdfTextStatus.clear(pdf)
        assertEquals(PdfTextPhase.IDLE, PdfTextStatus.of(pdf).phase)
    }
}
