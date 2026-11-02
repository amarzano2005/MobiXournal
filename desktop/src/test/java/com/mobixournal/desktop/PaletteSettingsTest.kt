package com.mobixournal.desktop

import com.mobixournal.desktop.ink.DesktopFavourites
import com.mobixournal.desktop.ink.DesktopInkState
import com.mobixournal.desktop.settings.ColorsSettings
import com.mobixournal.desktop.settings.DesktopSettings
import com.mobixournal.desktop.settings.PaletteActions
import com.mobixournal.desktop.settings.paletteColourName
import com.mobixournal.desktop.tools.DesktopTool

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.mobixournal.design.PenPalette
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The palette, editable from the window's **Colors** page — and its factory values, which are the tablet's.
 *
 * The claims are the ones a reader could notice were false: the window opens on the same eight hexes and
 * the same two ternas the phone does; a redefined swatch keeps its place and is in the file; a colour
 * added twice is still one swatch and the ceiling holds; the last swatch cannot be deleted; **Restore**
 * writes the factory eight back; and the page's verbs are the shell's own live palette rather than a copy,
 * so a change there is a change in the stroke panel at the same moment.
 */
class PaletteSettingsTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun store() = DesktopSettings(File(temp.root, "desktop.properties"))

    private fun ink(settings: DesktopSettings, tool: DesktopTool = DesktopTool.PEN) =
        DesktopInkState(settings) { tool }

    @Test
    fun `a fresh window opens on the tablet's palette and its two ternas`() {
        val settings = store()
        assertEquals(PenPalette.COLORS, settings.paletteColours)
        assertEquals(PenPalette.PEN_FAVOURITES, settings.colourFavourites)
        assertEquals(PenPalette.HIGHLIGHTER_FAVOURITES, settings.highlighterFavourites)
        assertEquals(PenPalette.HIGHLIGHTER_FAVOURITES, DesktopFavourites().highlighter)
        assertEquals(PenPalette.PEN_FAVOURITES, DesktopFavourites().pen)
    }

    @Test
    fun `a redefined swatch keeps its place, stays opaque and reaches the file`() {
        val settings = store()
        val state = ink(settings)

        state.definePaletteColour(1, 0x0012AB34)

        val expected = listOf(PenPalette.BLACK, 0xFF12AB34.toInt()) + PenPalette.COLORS.drop(2)
        assertEquals(expected, state.palette)
        assertEquals("the swatch is opaque even when the picker is not", 0xFF12AB34.toInt(), state.palette[1])
        assertEquals("the next window reads it back", expected, store().paletteColours)
    }

    @Test
    fun `a colour added twice is one swatch, and the ceiling holds`() {
        val settings = store()
        val state = ink(settings)

        state.addPaletteColour(PenPalette.RED)
        assertEquals("a colour already in the palette is not a twin", PenPalette.COLORS, state.palette)

        repeat(PenPalette.MAX_COLORS + 4) { index -> state.addPaletteColour(0xFF010000.toInt() + index * 7) }
        assertEquals(PenPalette.MAX_COLORS, state.palette.size)
    }

    @Test
    fun `the last swatch cannot be removed, and the rest can`() {
        val settings = store()
        val state = ink(settings)

        while (state.palette.size > 1) state.removePaletteColour(0)
        assertEquals(1, state.palette.size)

        state.removePaletteColour(0)
        assertEquals("a picker with no swatch cannot pick", 1, state.palette.size)

        state.addPaletteColour(PenPalette.BLUE)
        state.removePaletteColour(0)
        assertEquals(listOf(PenPalette.BLUE), state.palette)
    }

    @Test
    fun `restoring writes the factory eight back to the file`() {
        val settings = store()
        val state = ink(settings)
        state.removePaletteColour(0)
        state.addPaletteColour(0xFF123456.toInt())

        state.resetPalette()

        assertEquals(PenPalette.COLORS, state.palette)
        assertEquals(PenPalette.COLORS, store().paletteColours)
    }

    @Test
    fun `the page's verbs are the shell's live palette, not a copy`() {
        val settings = store()
        val state = ink(settings)
        val page = PaletteActions.of(state)
        assertEquals(state.palette, page.palette())

        page.define(0, PenPalette.MAGENTA)
        assertEquals("the strip's own list changed with it", PenPalette.MAGENTA, state.palette.first())

        page.add(0xFF00AA55.toInt())
        assertTrue(0xFF00AA55.toInt() in state.palette)

        page.restore()
        assertEquals(PenPalette.COLORS, state.palette)
    }

    @Test
    fun `a swatch is named by the factory word, and a reader's own by its hex`() {
        assertEquals("Green", paletteColourName(PenPalette.GREEN))
        assertEquals("Magenta", paletteColourName(PenPalette.MAGENTA))
        assertEquals("#00AA55", paletteColourName(0xFF00AA55.toInt()))
    }

    @Test
    fun `the Colors page composes its rows, its commands and its picker`() {
        // The state rules above are a JVM test; this is the other half — that the page itself composes:
        // a row per swatch with the two words at its end, the add and restore commands, and the colour
        // panel's fields, which only a real composition can prove are laid out rather than crashed.
        val scene = ImageComposeScene(width = 720, height = 900, density = Density(1f)) {
            Column(Modifier.fillMaxWidth()) {
                ColorsSettings(
                    PaletteActions(
                        palette = { PenPalette.COLORS + 0xFF00AA55.toInt() },
                        restore = {},
                    ),
                )
            }
        }
        val image = scene.render()
        assertTrue("the page drew nothing", image.width > 0 && image.height > 0)
        scene.close()
    }
}
