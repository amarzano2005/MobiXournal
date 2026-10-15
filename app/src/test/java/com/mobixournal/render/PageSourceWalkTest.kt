package com.mobixournal.render

import com.mobixournal.format.Xopp
import com.mobixournal.format.mobix.MobixDocument
import com.mobixournal.format.mobix.MobixEntrySource
import com.mobixournal.format.mobix.MobixPagedReader
import com.mobixournal.format.mobix.SyntheticNotebook
import com.mobixournal.format.mobix.TrackedEntrySource
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.TextElement
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The two document-wide walks under the editor — the `.xopp` export and find — read through the
 * document's **page surface**, so a `.mobix` opened page by page is exported and searched without
 * being materialised whole: each page is inflated once, as the walk reaches it, and the reader's
 * window releases it behind the walk.
 *
 * The output is asserted against the same walk over a fully loaded document, so "lighter" cannot
 * quietly mean "different".
 */
class PageSourceWalkTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** A 200-page notebook on disk, page [textPage] carrying a searchable text box. */
    private fun notebook(file: File, pageCount: Int, textPage: Int? = null): List<Page> {
        val pages = SyntheticNotebook.pages(pageCount).toMutableList()
        if (textPage != null) {
            val page = pages[textPage]
            pages[textPage] = page.copy(
                layers = page.layers + Layer(
                    listOf(
                        TextElement(
                            content = "needle in a haystack",
                            font = "Sans",
                            size = 12.0,
                            x = 40.0,
                            y = 60.0,
                            color = 0xFF000000.toInt(),
                            id = "txt_needle",
                        ),
                    ),
                ),
            )
        }
        SyntheticNotebook.write(file, pages)
        return pages
    }

    private class Paged(val document: Document, val reader: MobixPagedReader, val entries: TrackedEntrySource)

    private fun openPaged(file: File, budget: Int = 5): Paged {
        val entries = TrackedEntrySource(MobixEntrySource.fromFile(file))
        val reader = MobixPagedReader(entries, budget)
        return Paged(MobixDocument.openPaged(reader).toDocument(), reader, entries)
    }

    private fun eager(file: File): Document =
        MobixDocument.open(com.mobixournal.format.mobix.MobixContainer.open(file.inputStream().buffered()))
            .toDocument()

    private fun reads(entries: TrackedEntrySource, index: Int): Int =
        entries.openCount(SyntheticNotebook.entryOf(index))

    @Test
    fun theExportReadsEachPageOnceAndWritesTheSameDocument() {
        val file = temp.newFile("export.mobix")
        notebook(file, pageCount = 200)
        val paged = openPaged(file)

        paged.reader.use {
            val synthesized = XoppSynthesis.synthesize(paged.document, documentId = paged.document.documentId)

            // Every page, once: the walk reads the page it is on and no other.
            for (index in 0 until 200) {
                assertEquals("page $index read once", 1, reads(paged.entries, index))
            }
            assertTrue(
                "the reader's window bounds the working set, was ${paged.reader.materializedIndices().size}",
                paged.reader.materializedIndices().size <= 5,
            )

            // And the export is the export: the same document the eager walk produces, written as
            // the same `.xopp` bytes.
            val eagerSynthesis = XoppSynthesis.synthesize(eager(file), documentId = paged.document.documentId)
            assertEquals(eagerSynthesis.document, synthesized.document)
            assertEquals(eagerSynthesis.uncompiled, synthesized.uncompiled)
            assertEquals(toXopp(eagerSynthesis.document), toXopp(synthesized.document))
        }
    }

    @Test
    fun theSearchWalksTheSourceAndFindsAHitFarIntoTheDocument() {
        val file = temp.newFile("search.mobix")
        notebook(file, pageCount = 200, textPage = 149)
        val paged = openPaged(file)

        paged.reader.use {
            val hits = DocumentSearch.find(paged.document, pdfTextIndex = null, rawQuery = "needle")

            assertEquals("one hit, on the page that carries the text", 1, hits.size)
            assertEquals(149, hits[0].pageIndex)
            assertTrue("the hit has a box to highlight", hits[0].boxes.isNotEmpty())

            // The walk visited every page exactly once, and held only its window.
            for (index in listOf(0, 100, 149, 199)) {
                assertEquals("page $index read once", 1, reads(paged.entries, index))
            }
            assertTrue(
                "the search holds the window, not the document",
                paged.reader.materializedIndices().size <= 5,
            )

            val eagerHits = DocumentSearch.find(eager(file), pdfTextIndex = null, rawQuery = "needle")
            assertEquals(eagerHits, hits)
        }
    }

    @Test
    fun theStrokeCountWalksTheSourceToo() {
        val file = temp.newFile("count.mobix")
        notebook(file, pageCount = 12)
        val paged = openPaged(file)

        paged.reader.use {
            assertEquals(12, DocumentSearch.totalStrokeCount(paged.document))
            for (index in 0 until 12) {
                assertEquals(1, reads(paged.entries, index))
            }
        }
    }

    /** The `.xopp` XML a document writes to, so two exports can be compared as bytes. */
    private fun toXopp(document: Document): String {
        val out = ByteArrayOutputStream()
        Xopp.save(document, out)
        return String(java.util.zip.GZIPInputStream(out.toByteArray().inputStream()).readBytes(), Charsets.UTF_8)
    }
}
