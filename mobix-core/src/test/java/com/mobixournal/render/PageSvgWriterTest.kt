package com.mobixournal.render

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.TableCell
import com.mobixournal.format.model.TableElement
import com.mobixournal.format.model.TextElement
import com.mobixournal.format.model.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The SVG export is one of the three surfaces that must agree about a page (canvas, PDF, SVG), so it is
 * now shared: the desktop can write the same file the tablet writes. This test pins the three families
 * an A4 sheet carries — a hand stroke, a text box and a living table compiled through [LivingInk] — and
 * counts them, because an export that silently drops one is worse than an export that fails.
 */
class PageSvgWriterTest {

    private val black = 0xFF000000.toInt()

    private fun stroke() = Stroke(
        tool = Tool.PEN,
        color = black,
        capStyle = "round",
        points = listOf(StrokePoint(10.0, 10.0, 1.0), StrokePoint(40.0, 60.0, 1.0)),
        uniformWidth = true,
    )

    private fun text() = TextElement(
        font = "Sans",
        size = 12.0,
        x = 72.0,
        y = 96.0,
        color = black,
        content = "Hello",
    )

    private fun table() = TableElement(
        left = 72.0,
        top = 320.0,
        colWidths = listOf(120.0, 180.0),
        rowHeights = listOf(24.0),
        cells = listOf(listOf(TableCell("Parametro", bold = true), TableCell("Formula"))),
        headerRow = true,
        id = "tbl_1",
    )

    private fun page(vararg elements: Element) = Page(
        width = 595.27559100,
        height = 841.88976400,
        background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
        layers = listOf(Layer(elements.toList())),
    )

    private fun count(haystack: String, needle: String): Int {
        var seen = 0
        var at = haystack.indexOf(needle)
        while (at >= 0) {
            seen++
            at = haystack.indexOf(needle, at + needle.length)
        }
        return seen
    }

    @Test
    fun onePageBecomesAWellFormedVectorDocument() {
        val svg = PageSvgWriter.write(page(stroke(), text(), table()))

        assertTrue("the document starts with its declaration", svg.startsWith("<?xml"))
        assertTrue("and closes its root element", svg.trimEnd().endsWith("</svg>"))
        assertTrue("the sheet is a rect", svg.contains("<rect"))
    }

    @Test
    fun aHandStrokeIsOnePolylineAndATextBoxIsOneText() {
        val svg = PageSvgWriter.write(page(stroke(), text()))

        assertEquals("one stroke, one polyline", 1, count(svg, "<polyline"))
        assertEquals("one text box, one <text>", 1, count(svg, ">Hello<"))
    }

    @Test
    fun theLivingTableArrivesAsItsOwnInkAndItsOwnWords() {
        val without = PageSvgWriter.write(page(stroke(), text()))
        val with = PageSvgWriter.write(page(stroke(), text(), table()))

        // The table is compiled through LivingInk (its grid rules are strokes, its cells are text), so
        // the export cannot disagree with the canvas about what a table looks like — and a table that
        // quietly vanished would leave these counts identical.
        assertTrue("the table adds ink of its own", count(with, "<polyline") > count(without, "<polyline"))
        assertEquals("each cell is written once", 1, count(with, ">Parametro<"))
        assertEquals("including the header's second cell", 1, count(with, ">Formula<"))
        assertEquals("and nothing of it leaks into a page without one", 0, count(without, "Parametro"))
    }

    @Test
    fun aPageWithNothingOnItStillCarriesItsSheetAndRuling() {
        val blank = PageSvgWriter.write(page())

        assertTrue("the sheet fill is there", blank.contains("<rect"))
        assertEquals("and there is no ink to write", 0, count(blank, "<polyline"))
    }
}
