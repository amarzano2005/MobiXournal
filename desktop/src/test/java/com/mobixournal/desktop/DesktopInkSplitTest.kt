package com.mobixournal.desktop

import com.mobixournal.design.PenPalette
import com.mobixournal.render.InkGeometry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The window's **two inks**: the pen and the highlighter each keep their own colour, width and favourites.
 *
 * The claim is the one a user notices immediately: dragging a yellow highlighter must not turn the pencil
 * yellow, and going back to the pencil must bring back the ink it had. The tablet keeps them apart the same
 * way (`EditorUiState.color` / `highlighterColor`, `AppSettings.penFavorites` / `highlighterFavorites`), so a
 * window with one shared ink is a window whose two tools disagree with the phone's.
 */
class DesktopInkSplitTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun settings(name: String = "ink.properties") = DesktopSettings(File(temp.root, name))

    @Test
    fun `each tool starts on its own factory ink`() {
        val inks = DesktopInks()
        assertEquals(PenPalette.BLACK, inks.forTool(DesktopTool.PEN).colour)
        assertEquals("the highlighter is yellow", PenPalette.YELLOW, inks.forTool(DesktopTool.HIGHLIGHTER).colour)
        assertEquals(
            "the highlighter starts on the widest slot, as on the tablet",
            PenPalette.DEFAULT_WIDTHS.last(),
            inks.forTool(DesktopTool.HIGHLIGHTER).width,
            1e-4f,
        )
    }

    /**
     * A marker writes a **band**, not a wide pen: the picked slot is read as a highlighter tip.
     *
     * The tablet does exactly this — `InkGeometry.highlighterWidthFor` scales the pen's S/M/L slot by six
     * and snaps it to the 1 / 3 / 7 mm tips — so the same slot is a fine line with the pencil and a broad
     * band with the marker. The window used to paint the marker at the pen's width, which is the one thing
     * a highlighter must not be. The **stored** ink stays the slot, so the strip's dots and the settings
     * file keep their meaning (`DesktopInkState.pageInk` is the page's ink only).
     */
    @Test
    fun `the highlighter's band is the pen's slot read as a marker tip`() {
        val state = DesktopShellState(settings = settings())
        try {
            state.selectTool(DesktopTool.HIGHLIGHTER)
            for ((index, slot) in PenPalette.DEFAULT_WIDTHS.withIndex()) {
                state.pickWidth(slot)
                assertEquals(
                    "the strip still holds slot $index",
                    slot,
                    state.ink.width,
                    1e-6f,
                )
                assertEquals(
                    "while the band is the marker's own tip",
                    InkGeometry.highlighterWidthFor(slot),
                    state.pageInk.width.toDouble(),
                    1e-6,
                )
            }
            val band = state.pageInk.width
            state.selectTool(DesktopTool.PEN)
            assertTrue(
                "and a marker is several times the pen at the same slot ($band pt)",
                band > PenPalette.DEFAULT_WIDTHS.last() * 2f,
            )
            assertEquals(
                "the pen writes its own width, untouched",
                state.ink.width,
                state.pageInk.width,
                1e-6f,
            )
        } finally {
            state.tabs.closeAll()
        }
    }

    @Test
    fun `a colour picked with the highlighter does not repaint the pen`() {
        val state = DesktopShellState(settings())
        val penBefore = state.ink

        state.selectTool(DesktopTool.HIGHLIGHTER)
        state.pickColour(PenPalette.MAGENTA)
        state.pickWidth(PenPalette.DEFAULT_WIDTHS.last())

        assertEquals(PenPalette.MAGENTA, state.ink.colour)
        state.selectTool(DesktopTool.PEN)
        assertEquals("the pen kept its own ink and its own width", penBefore, state.ink)
        assertNotEquals("the two tools really are different", penBefore.colour, PenPalette.MAGENTA)
    }

    @Test
    fun `both inks survive a restart, each in its own entry`() {
        val file = File(temp.root, "both.properties")
        val state = DesktopShellState(DesktopSettings(file))

        state.pickColour(PenPalette.GREEN)
        state.selectTool(DesktopTool.HIGHLIGHTER)
        state.pickColour(PenPalette.ORANGE)
        state.pickWidth(1.5f)

        val reloaded = DesktopSettings(file)
        assertEquals("the pen's colour", PenPalette.GREEN, reloaded.inkColour)
        assertEquals("the highlighter's colour", PenPalette.ORANGE, reloaded.highlighterColour)
        assertEquals("the highlighter's width", 1.5f, reloaded.highlighterWidth, 1e-4f)

        val restarted = DesktopShellState(reloaded)
        assertEquals(PenPalette.GREEN, restarted.ink.colour)
        restarted.selectTool(DesktopTool.HIGHLIGHTER)
        assertEquals(PenPalette.ORANGE, restarted.ink.colour)
    }

    @Test
    fun `the digits pick the favourite of the tool in hand`() {
        val state = DesktopShellState(settings())
        state.selectTool(DesktopTool.HIGHLIGHTER)
        state.pickColour(PenPalette.MAGENTA)
        state.setFavourite(0)

        assertEquals("the highlighter's first favourite is what was defined", PenPalette.MAGENTA, state.favourites[0])
        state.selectTool(DesktopTool.PEN)
        assertNotEquals(
            "the pen's first favourite is its own",
            PenPalette.MAGENTA,
            state.favourites[0],
        )
        state.pickFavourite(0)
        assertEquals("and pressing 1 takes the pen's", state.favourites[0], state.ink.colour)
    }

    @Test
    fun `the custom slot belongs to the palette, not to a tool`() {
        val state = DesktopShellState(settings())
        state.selectTool(DesktopTool.HIGHLIGHTER)
        state.editColour(ColourEditorTarget.CUSTOM)
        state.commitColour(0xFF123456.toInt())

        assertEquals("defined once", 0xFF123456.toInt(), state.paletteCustom)
        assertNotEquals("but it is not the pen's ink", 0xFF123456.toInt(), DesktopInks().pen.colour)
        state.selectTool(DesktopTool.PEN)
        assertEquals("both tools read the one slot", 0xFF123456.toInt(), state.paletteCustom)
    }
}
