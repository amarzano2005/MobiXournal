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
