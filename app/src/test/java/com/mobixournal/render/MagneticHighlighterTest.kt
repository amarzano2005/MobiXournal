package com.mobixournal.render

import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The magnetic highlighter's rules, pinned on the pure geometry: a horizontal drag over one line
 * becomes that line's band, a steep drag or one over no text stays freehand, and a band is exactly as
 * tall as the glyph boxes it covers plus the documented air.
 */
class MagneticHighlighterTest {

    private fun point(x: Double, y: Double) = StrokePoint(x, y, 8.0)

    private fun word(text: String, left: Double, top: Double, right: Double, bottom: Double) =
        PdfWord(text, left, top, right, bottom)

    private val lineOne = listOf(
        word("Magnetic", 50.0, 100.0, 110.0, 112.0),
        word("highlighter", 114.0, 100.0, 180.0, 112.0),
    )

    @Test
    fun aHorizontalDragAcrossALineBecomesOneBandOverIt() {
        val bands = MagneticHighlighter.bands(listOf(point(45.0, 106.0), point(185.0, 106.0)), lineOne)
        assertEquals(1, bands.size)
        val band = bands.single()
        assertEquals(49.0, band.left, 0.0)
        assertEquals(181.0, band.right, 0.0)
        assertEquals(100.0 - MagneticHighlighter.BAND_PAD_PT, band.top, 0.0)
        assertEquals(112.0 + MagneticHighlighter.BAND_PAD_PT, band.bottom, 0.0)
    }

    @Test
    fun aSteepDragKeepsItsFreehandShape() {
        val bands = MagneticHighlighter.bands(listOf(point(100.0, 40.0), point(110.0, 160.0)), lineOne)
        assertTrue("a vertical stroke is marking, not highlighting", bands.isEmpty())
    }

    @Test
    fun aDragOverNoTextStaysFreehand() {
        val bands = MagneticHighlighter.bands(listOf(point(45.0, 300.0), point(185.0, 302.0)), lineOne)
        assertTrue(bands.isEmpty())
    }

    @Test
    fun aGentlySlopingDragOverTwoLinesCreatesOneBandPerLine() {
        val twoLines = lineOne + listOf(
            word("second", 50.0, 118.0, 100.0, 130.0),
            word("line", 104.0, 118.0, 125.0, 130.0),
        )
        val bands = MagneticHighlighter.bands(listOf(point(45.0, 106.0), point(130.0, 127.0)), twoLines)
        assertEquals(2, bands.size)
        assertEquals(106.0, bands[0].centerY, 0.0)
        assertEquals(124.0, bands[1].centerY, 0.0)
    }

    @Test
    fun strokesCarryTheBandAsOneConstantWidthHighlight() {
        val strokes = MagneticHighlighter.strokes(
            listOf(point(45.0, 106.0), point(185.0, 106.0)),
            lineOne,
            color = 0x7FFFFF00.toInt(),
        )
        val stroke = strokes.single()
        assertEquals(Tool.HIGHLIGHTER, stroke.tool)
        assertEquals(0x7FFFFF00.toInt(), stroke.color)
        assertEquals(2, stroke.points.size)
        assertTrue(stroke.uniformWidth)
        assertEquals(stroke.points.first().y, stroke.points.last().y, 0.0)
        assertEquals(
            MagneticHighlighter.bands(listOf(point(45.0, 106.0), point(185.0, 106.0)), lineOne)
                .single().height,
            stroke.points.first().width,
            0.0,
        )
    }

    @Test
    fun aShortFlickDoesNotSnap() {
        assertTrue(MagneticHighlighter.bands(listOf(point(50.0, 106.0), point(54.0, 106.0)), lineOne).isEmpty())
    }
}
