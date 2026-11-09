package com.mobixournal.render

import com.mobixournal.format.model.TableAlign
import com.mobixournal.format.model.TableCell
import com.mobixournal.format.model.TableElement
import com.mobixournal.format.model.TextElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The width estimate the exports lay text out with ([TextAdvance]) against the fonts it was measured
 * from — the same advances, read from Roboto Regular, Inter Regular and DejaVu Sans and averaged.
 *
 * The flat per-character guess this replaced was the centring bug: a flat 0.62 em per character made
 * `iii` 2.4 times its drawn width and `MMM` 30% too narrow, so a centred cell in an export could sit
 * several points off its column's midline.
 */
class TextAdvanceTest {

    /** Real widths in em, the mean of the three faces at the same size. */
    private val measured = mapOf(
        "iii" to 0.759,
        "MMM" to 2.640,
        "Parametro" to 4.953,
        "I = V/R" to 3.224,
        "x\u00b2 + 1" to 2.739,
        "50 Hz" to 2.752,
        "12.5" to 2.021,
    )

    @Test
    fun theEstimateMatchesTheFontsWithinAPercent() {
        for ((text, real) in measured) {
            val estimate = TextAdvance.em(text)
            assertEquals("$text estimated at $estimate em, drawn at $real", real, estimate, real * 0.03)
        }
    }

    /** The flat guess could not tell these apart, which is why a centred cell drifted off. */
    @Test
    fun aNarrowStringIsNotAsWideAsAWideOne() {
        assertTrue("iii must be much narrower than MMM", TextAdvance.em("iii") < TextAdvance.em("MMM") / 3.0)
        assertTrue("and both must be inside the flat guess's reach", TextAdvance.em("iii") < 0.62 * 3)
    }

    @Test
    fun anIdeographIsAFullEmAndAnUnmeasuredGlyphFallsBack() {
        assertEquals("an ideograph fills its em square", 1.0, TextAdvance.emOf('\u6f22'), 1e-9)
        assertEquals(
            "a glyph the table does not carry uses the flat fallback",
            InkTokens.FIGURE_LABEL_CHAR_W,
            TextAdvance.emOf('\u00a1'),
            1e-9,
        )
        assertEquals("a size scales the sum, not a glyph count", 2.0 * TextAdvance.em("50 Hz"), TextAdvance.pt("50 Hz", 2.0), 1e-9)
    }

    /**
     * A free text box's own box — what a tap hits, what the selection outline draws, what an align
     * command moves — is the measured one, so a narrow box is not outlined and picked as though it
     * held wide letters.
     */
    @Test
    fun aFreeTextBoxIsAsWideAsItsGlyphs() {
        val box = TextElement("Sans", 10.0, 20.0, 30.0, 0xFF000000.toInt(), "iii")
        val bounds = ElementBounds.of(box)
        assertEquals("the box hugs the drawn word", TextAdvance.pt("iii", 10.0), bounds.width, 1e-9)
        assertTrue(
            "and not the width a flat per-character guess gave it",
            bounds.width < 3 * 10.0 * 0.62,
        )
    }

    /**
     * The same table chooses a cell's font size (`TableInk.autoFontSizePt`): equal-length words of
     * different glyphs are no longer fitted at one size, which either clipped the wide one or shrank
     * the narrow one for room it had.
     */
    @Test
    fun aCellFitsItsFontToTheMeasuredWidth() {
        val narrow = TableInk.autoFontSizePt("iiiiiiiiiiii", width = 60.0, height = 60.0)
        val wide = TableInk.autoFontSizePt("MMMMMMMMMMMM", width = 60.0, height = 60.0)
        assertTrue("the narrow word gets more room than the wide one ($narrow vs $wide)", narrow > wide * 2.0)

        // What it chose fits the cell's own width, both paddings included.
        assertTrue("narrow fits", TextAdvance.pt("iiiiiiiiiiii", narrow) <= 56.0 + 1e-6)
        assertTrue("wide fits", TextAdvance.pt("MMMMMMMMMMMM", wide) <= 56.0 + 1e-6)

        // A bold face is wider, so at the same width it is fitted smaller than the regular one.
        val regular = TableInk.autoFontSizePt("Frequenza", width = 60.0, height = 60.0)
        val bold = TableInk.autoFontSizePt("Frequenza", width = 60.0, height = 60.0, isBold = true)
        assertTrue("bold is fitted smaller ($bold vs $regular)", bold < regular)
    }

    /**
     * A plot's tick numbers are placed on the same measured advance: the y labels right-align on the
     * frame's gap, the x labels centre under their tick — not on a flat guess that made `-0.5` as wide
     * as `0.5`.
     */
    @Test
    fun aPlotWritesItsTickNumbersOnTheirMeasuredWidth() {
        val plot = MultiFunctionPlotter.plot(
            curves = listOf(PlotCurve(source = "x", color = 0xFF000000.toInt())),
            xMin = -0.5,
            xMax = 0.5,
            leftPt = 100.0,
            topPt = 50.0,
            widthPt = 200.0,
            heightPt = 100.0,
            strokeWidthPt = 1.0,
        )
        assertTrue("the plot writes its numbers", plot.labels.isNotEmpty())
        val vertical = plot.labels.filter { it.x < 100.0 }
        assertTrue("and on the y axis too", vertical.isNotEmpty())
        for (label in vertical) {
            val width = TextAdvance.pt(label.text, InkTokens.FIGURE_LABEL_SIZE_PT)
            assertEquals("${label.text} is right-aligned on the frame's gap", 100.0 - 4.0, label.x + width, 1e-9)
            assertTrue(
                "${label.text} is not placed as wide as a character count makes it",
                kotlin.math.abs(label.x + label.text.length * 5.0 - (100.0 - 4.0)) > 1e-6,
            )
        }
        for (label in plot.labels - vertical.toSet()) {
            val width = TextAdvance.pt(label.text, InkTokens.FIGURE_LABEL_SIZE_PT)
            val middle = label.x + width / 2.0
            assertTrue("${label.text} is centred inside the frame's span", middle in 100.0..300.0)
        }
    }

    /**
     * The export path end to end: with no font engine, a centred cell is centred on the *estimated*
     * width — so the estimate being measured is what puts the text on the midline.
     */
    @Test
    fun anExportCentresACellOnTheMeasuredWidth() {
        val table = TableElement(
            left = 50.0,
            top = 50.0,
            colWidths = listOf(120.0),
            rowHeights = listOf(40.0),
            cells = listOf(listOf(TableCell("iii", TableAlign.CENTER))),
        )

        val line = TableInk.ink(table).filterIsInstance<TextElement>().single()
        val width = TextAdvance.pt("iii", line.size)
        assertEquals("the narrow string sits on the cell's midline", 50.0 + (120.0 - width) / 2.0, line.x, 1e-4)
        assertTrue(
            "and not where a flat per-character guess would have put it",
            kotlin.math.abs(line.x - (50.0 + (120.0 - 3 * line.size * 0.62) / 2.0)) > 1.0,
        )
    }
}
