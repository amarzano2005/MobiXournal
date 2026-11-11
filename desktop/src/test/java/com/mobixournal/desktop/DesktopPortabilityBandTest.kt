package com.mobixournal.desktop

import com.mobixournal.desktop.shell.portabilityLabel
import com.mobixournal.desktop.shell.portabilityTip
import com.mobixournal.portability.PortabilityEntry
import com.mobixournal.portability.PortabilityKind
import com.mobixournal.portability.PortabilityReport
import com.mobixournal.portability.PortabilityStatus
import com.mobixournal.portability.PortabilitySummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The window's sentence for a notebook whose parts are not all inside it
 * (`DesktopStatus.portabilityLabel`), which is view state and must not be invented in the composable.
 *
 * These are the three cases a reader can actually meet: a portable file says nothing, one missing part
 * reads in the singular, several read in the plural — and the tip carries the references themselves so
 * the band can stay one line.
 */
class DesktopPortabilityBandTest {

    private fun summary(vararg entries: PortabilityEntry): PortabilitySummary =
        PortabilitySummary.of(PortabilityReport.of("doc", entries.toList()))

    private fun missing(page: Int, reference: String, kind: PortabilityKind = PortabilityKind.PDF): PortabilityEntry =
        PortabilityEntry(PortabilityStatus.REFERENCED_ABSENT, kind, reference, page)

    @Test
    fun aPortableDocumentAndAXoppSayNothing() {
        assertNull("a `.xopp` has no archive to check", portabilityLabel(null))
        assertNull(
            "everything inside: the band must not grow a reading with nothing in it",
            portabilityLabel(
                summary(PortabilityEntry(PortabilityStatus.EMBEDDED, PortabilityKind.IMAGE, "assets/img_a.png", 0)),
            ),
        )
        assertEquals("", portabilityTip(null))
    }

    @Test
    fun oneMissingPartReadsInTheSingular() {
        val summary = summary(missing(0, "notes.pdf"))

        assertEquals("1 not inside", portabilityLabel(summary))
        assertEquals("1 part is not inside this notebook. Page 1 · pdf · notes.pdf", portabilityTip(summary))
    }

    @Test
    fun severalMissingPartsReadInThePluralAndListTheirPages() {
        val summary = summary(
            missing(3, "scan.png", PortabilityKind.PIXMAP),
            missing(7, "take.wav", PortabilityKind.AUDIO),
        )

        assertEquals("2 not inside", portabilityLabel(summary))
        val tip = portabilityTip(summary)
        assertTrue("the pages are one-based", "Page 4 · pixmap · scan.png" in tip)
        assertTrue("and both references are named", "Page 8 · audio · take.wav" in tip)
    }
}
