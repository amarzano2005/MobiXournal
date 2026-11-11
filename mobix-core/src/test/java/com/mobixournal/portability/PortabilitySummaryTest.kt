package com.mobixournal.portability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The report-to-copy mapping (`PortabilitySummary`): singular, plural and the zero case — the three a
 * reader sees, checked on the JVM rather than by opening two shells and comparing screenshots.
 */
class PortabilitySummaryTest {

    private fun report(vararg entries: PortabilityEntry): PortabilityReport =
        PortabilityReport.of("doc_summary", entries.toList())

    private fun missing(page: Int, reference: String, kind: PortabilityKind = PortabilityKind.PDF): PortabilityEntry =
        PortabilityEntry(PortabilityStatus.REFERENCED_ABSENT, kind, reference, page)

    @Test
    fun aDocumentThatCarriesEverythingSaysSoAndListsNothing() {
        val summary = PortabilitySummary.of(
            report(PortabilityEntry(PortabilityStatus.EMBEDDED, PortabilityKind.IMAGE, "assets/img_a.png", 0)),
        )

        assertTrue(summary.isPortable)
        assertEquals(0, summary.count)
        assertEquals("Everything this notebook names is inside the file.", summary.headline)
        assertEquals("nothing to list", listOf<String>(), summary.lines)
    }

    @Test
    fun oneMissingPartReadsInTheSingular() {
        val summary = PortabilitySummary.of(report(missing(0, "notes.pdf")))

        assertFalse(summary.isPortable)
        assertEquals(1, summary.count)
        assertEquals("1 part is not inside this notebook.", summary.headline)
        assertEquals(listOf("Page 1 · pdf · notes.pdf"), summary.lines)
    }

    @Test
    fun severalMissingPartsReadInThePlural() {
        val summary = PortabilitySummary.of(
            report(
                missing(2, "scan.png", PortabilityKind.PIXMAP),
                missing(5, "take.wav", PortabilityKind.AUDIO),
            ),
        )

        assertEquals(2, summary.count)
        assertEquals("2 parts are not inside this notebook.", summary.headline)
        assertEquals(
            "the page is one-based and the lines keep page order",
            listOf("Page 3 · pixmap · scan.png", "Page 6 · audio · take.wav"),
            summary.lines,
        )
    }

    @Test
    fun oneReferenceOnManyPagesIsOneLineWithItsFirstPage() {
        val summary = PortabilitySummary.of(
            report(missing(1, "notes.pdf"), missing(4, "notes.pdf")),
        )

        assertEquals("a reader acts on a reference, not on a page", 1, summary.count)
        assertEquals(listOf("Page 2 · pdf · notes.pdf"), summary.lines)
    }

    @Test
    fun anUnrecoverableReferenceSaysSoInsteadOfPromisingALookup() {
        val summary = PortabilitySummary.of(
            report(
                PortabilityEntry(
                    PortabilityStatus.UNRECOVERABLE,
                    PortabilityKind.PDF,
                    "assets/img_${"a".repeat(64)}.pdf",
                    0,
                ),
            ),
        )

        assertTrue(
            "a damaged entry is not something the reader can go and find",
            summary.lines.single().endsWith("— this one cannot be recovered"),
        )
    }

    @Test
    fun theSingleLineFormJoinsTheHeadlineAndTheReferences() {
        val summary = PortabilitySummary.of(report(missing(0, "notes.pdf")))

        assertEquals(
            "1 part is not inside this notebook. Page 1 · pdf · notes.pdf",
            summary.sentence(),
        )
        assertEquals(
            "the zero case is the headline alone, not a headline and an empty list",
            "Everything this notebook names is inside the file.",
            PortabilitySummary.of(report()).sentence(),
        )
    }
}
