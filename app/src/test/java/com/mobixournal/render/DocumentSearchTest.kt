package com.mobixournal.render

import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.TextElement
import com.mobixournal.format.model.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentSearchTest {

    private fun stroke(vararg coords: Double): Stroke {
        require(coords.size % 2 == 0)
        val pts = (coords.indices step 2).map {
            StrokePoint(coords[it], coords[it + 1], 1.5)
        }
        return Stroke(
            tool = Tool.PEN,
            color = 0xFF000000.toInt(),
            capStyle = null,
            points = pts,
            uniformWidth = true,
        )
    }

    private fun letterT(x: Double, y: Double): List<Stroke> = listOf(
        stroke(x, y, x + 20.0, y),
        stroke(x + 10.0, y, x + 10.0, y + 30.0),
    )

    private fun letterO(x: Double, y: Double): List<Stroke> = listOf(
        stroke(
            x + 10.0, y,
            x, y + 10.0,
            x, y + 20.0,
            x + 10.0, y + 30.0,
            x + 20.0, y + 20.0,
            x + 20.0, y + 10.0,
            x + 10.0, y,
        ),
    )

    @Test fun findsTypedTextCaseInsensitively() {
        val doc = document(
            TextElement("Sans", 10.0, 20.0, 30.0, 0, "Alpha beta\nalpha"),
        )

        val hits = DocumentSearch.find(doc, null, "ALPHA")

        assertEquals(2, hits.size)
        assertEquals(Bounds(20.0, 30.0, 51.0, 43.0), hits[0].boxes.single())
        assertEquals(Bounds(20.0, 43.0, 51.0, 56.0), hits[1].boxes.single())
    }

    @Test fun findsBackgroundPdfPhraseAcrossWords() {
        val doc = document()
        val pdf = PdfTextIndex(
            listOf(
                listOf(
                    PdfWord("hello", 10.0, 20.0, 40.0, 30.0),
                    PdfWord("world", 45.0, 20.0, 80.0, 30.0),
                ),
            ),
        )

        val hits = DocumentSearch.find(doc, pdf, "lo wo")

        assertEquals(1, hits.size)
        assertEquals(
            listOf(Bounds(10.0, 20.0, 40.0, 30.0), Bounds(45.0, 20.0, 80.0, 30.0)),
            hits.single().boxes,
        )
    }

    @Test fun findsHandwrittenTextCaseInsensitively() {
        // Draw handwritten "TO"
        val strokes = letterT(20.0, 50.0) + letterO(45.0, 50.0)
        val doc = document(*strokes.toTypedArray())

        val hits = DocumentSearch.find(doc, null, "to")

        assertEquals(1, hits.size)
        assertEquals(0, hits[0].pageIndex)
        val box = hits[0].boxes.single()
        assertTrue(box.left <= 20.0)
        assertTrue(box.right >= 60.0)
    }

    @Test fun findsHandwrittenTextWithProvidedIndex() {
        val doc = document()
        val hwIndex = HandwritingIndex(
            listOf(
                listOf(
                    HandwrittenWord("meeting", Bounds(100.0, 200.0, 180.0, 230.0)),
                    HandwrittenWord("notes", Bounds(190.0, 200.0, 250.0, 230.0)),
                ),
            ),
        )

        val hits = DocumentSearch.find(doc, null, "MEET", hwIndex)

        assertEquals(1, hits.size)
        assertEquals(Bounds(100.0, 200.0, 180.0, 230.0), hits[0].boxes.single())
    }

    @Test fun findsMixedHitsAcrossTypedPdfAndHandwriting() {
        val strokes = letterT(20.0, 50.0) + letterO(45.0, 50.0)
        val elements = mutableListOf<Element>()
        elements.addAll(strokes)
        elements.add(TextElement("Sans", 10.0, 10.0, 100.0, 0, "to do later"))
        val doc = document(*elements.toTypedArray())

        val pdf = PdfTextIndex(
            listOf(
                listOf(
                    PdfWord("to", 10.0, 10.0, 30.0, 20.0),
                ),
            ),
        )

        val hits = DocumentSearch.find(doc, pdf, "to")

        // 1 PDF hit + 1 handwriting hit ("TO") + 1 typed text hit ("to do later")
        assertEquals(3, hits.size)
    }

    private fun document(vararg elements: Element): Document =
        Document(pages = listOf(blankPage().copy(layers = listOf(Layer(elements.toList())))))
}
