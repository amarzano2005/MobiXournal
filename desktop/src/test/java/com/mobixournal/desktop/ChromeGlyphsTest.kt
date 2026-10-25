package com.mobixournal.desktop

import androidx.compose.ui.graphics.Path
import com.mobixournal.design.ChromeGlyph
import com.mobixournal.design.ChromeGlyphCatalogue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The chrome's glyphs — the tablet's own pictures — as art a fillable path can be made of.
 *
 * Two things are checked, and both are things the window got wrong on its own: every command has a
 * picture, and the picture is the one the phone shows. The first is the catalogue being complete; the
 * second is the path reader agreeing with the data — a `d` string Material publishes is only art once
 * something turns it into a shape, and a parser that drops a command or an arc leaves the glyph
 * *nearly* right, which is the failure this file exists to catch.
 */
class ChromeGlyphsTest {

    @Test
    fun `every chrome glyph is a path Material publishes`() {
        for (glyph in ChromeGlyph.entries) {
            val data = ChromeGlyphCatalogue.path(glyph)
            assertTrue("$glyph has path data", data.isNotBlank())
            assertTrue("$glyph starts with a move", data.trimStart().startsWith("M"))
        }
    }

    @Test
    fun `no two chrome glyphs are the same picture`() {
        val data = ChromeGlyph.entries.map { ChromeGlyphCatalogue.path(it) }
        assertEquals("every glyph is its own drawing", ChromeGlyph.entries.size, data.toSet().size)
    }

    /**
     * Every glyph parses into a shape that stays on the **24-unit grid** and has area.
     *
     * A path that leaves the grid is clipped by its 20 dp box, and an empty one draws nothing: both read
     * as a missing button. The bounds are the honest measure of "it is there and it is the right size".
     */
    @Test
    fun `every glyph fills its grid and nothing outside it`() {
        for (glyph in ChromeGlyph.entries) {
            val bounds = chromePath(glyph).getBounds()
            assertTrue("$glyph has width", bounds.width > 4f)
            assertTrue("$glyph has height", bounds.height > 4f)
            assertTrue(
                "$glyph stays on the grid (${bounds.left}, ${bounds.top} .. ${bounds.right}, ${bounds.bottom})",
                bounds.left >= -0.01f && bounds.top >= -0.01f &&
                    bounds.right <= GRID + 0.01f && bounds.bottom <= GRID + 0.01f,
            )
        }
    }

    @Test
    fun `undo and redo are two different turns`() {
        assertNotEquals(
            ChromeGlyphCatalogue.path(ChromeGlyph.UNDO),
            ChromeGlyphCatalogue.path(ChromeGlyph.REDO),
        )
        val undo = chromePath(ChromeGlyph.UNDO).getBounds()
        assertTrue("undo's head is on the left, as on the tablet", undo.left <= 3f)
    }

    /** The reader's own arithmetic, on the two constructs the icons lean on: arcs and relative cubics. */
    @Test
    fun `arcs and relative segments land where the data says`() {
        // A quarter circle of radius 10 from (2,12) to (12,2), swept upwards.
        val arc: Path = svgPath("M2 12 A10 10 0 0 1 12 2 Z")
        val bounds = arc.getBounds()
        assertEquals("the arc reaches its endpoint", 12f, bounds.right, 0.2f)
        assertEquals("and starts at its own", 2f, bounds.left, 0.2f)
        assertEquals("a quarter turn tops out at y = 2", 2f, bounds.top, 0.2f)
        assertTrue("the corner stays at the move point", bounds.bottom >= 11.5f)

        // Relative cubic: the same shape written from the endpoint, as Material writes its curves.
        val cubic: Path = svgPath("M4 4 c 0 8 8 8 8 0 V 4 z")
        val cubicBounds = cubic.getBounds()
        assertEquals("the curve starts where it was moved", 4f, cubicBounds.left, 0.01f)
        assertEquals("and ends one command later", 12f, cubicBounds.right, 0.01f)
        assertTrue("with depth from its control points", cubicBounds.bottom > 9f)
    }

    /** A command the reader does not know must not be skipped silently: it is a wrong picture. */
    @Test(expected = IllegalStateException::class)
    fun `an unknown command is refused`() {
        svgPath("M0 0 K5 5")
    }

    private companion object {
        const val GRID = 24f
    }
}
