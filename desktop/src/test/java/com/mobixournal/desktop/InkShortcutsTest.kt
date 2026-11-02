package com.mobixournal.desktop

import com.mobixournal.desktop.ink.DesktopInk
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.settings.DesktopSettings
import com.mobixournal.desktop.shell.DesktopShellState
import com.mobixournal.desktop.shell.InkCommand
import com.mobixournal.desktop.shell.ShellCommand
import com.mobixournal.desktop.shell.inkCommand
import com.mobixournal.desktop.shell.shellCommand
import com.mobixournal.desktop.shell.toolForKey
import com.mobixournal.desktop.tools.DESKTOP_TOOL_GROUPS
import com.mobixournal.desktop.tools.DesktopTool
import com.mobixournal.desktop.tools.ToolRail
import com.mobixournal.desktop.tools.ToolStripMetrics
import com.mobixournal.desktop.tools.ToolStripState
import com.mobixournal.desktop.tools.colourKeyLabel
import com.mobixournal.desktop.tools.dotSize
import com.mobixournal.desktop.tools.widthLetter

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.Density
import com.mobixournal.design.PenPalette
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The ink's keys — `1`/`2`/`3`, `Alt`+digit, `]`/`[` — and the values they move.
 *
 * The claims that a user would notice were false: the digits pick the three favourites and the choice is
 * still there after a restart; `Alt`+digit redefines the slot it names; `]`/`[` walk S → M → L and stop
 * at the ends instead of wrapping; and **no ink key takes a letter or a `Ctrl` combination from the
 * shell** — `Ctrl+1` must stay fit width and `B` must stay the pen, which is the ordering the keyboard
 * handler documents.
 */
class InkShortcutsTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun settings() = DesktopSettings(File(temp.root, "desktop.properties"))

    @Test
    fun `the digits pick the three favourites`() {
        assertEquals(InkCommand.PickFavourite(0), inkCommand(Key.One, alt = false, ctrl = false))
        assertEquals(InkCommand.PickFavourite(1), inkCommand(Key.Two, alt = false, ctrl = false))
        assertEquals(InkCommand.PickFavourite(2), inkCommand(Key.Three, alt = false, ctrl = false))
        assertEquals("the numpad counts as the same digit", InkCommand.PickFavourite(0), inkCommand(Key.NumPad1, alt = false, ctrl = false))
    }

    @Test
    fun `alt and the digit redefine that favourite`() {
        assertEquals(InkCommand.SetFavourite(0), inkCommand(Key.One, alt = true, ctrl = false))
        assertEquals(InkCommand.SetFavourite(2), inkCommand(Key.Three, alt = true, ctrl = false))
    }

    @Test
    fun `the brackets step the width both ways`() {
        assertEquals(InkCommand.StepWidth(1), inkCommand(Key.RightBracket, alt = false, ctrl = false))
        assertEquals(InkCommand.StepWidth(-1), inkCommand(Key.LeftBracket, alt = false, ctrl = false))
    }

    @Test
    fun `no ink key takes a shell command or a tool`() {
        assertNull("Ctrl+1 stays fit width", inkCommand(Key.One, alt = false, ctrl = true))
        assertNull("Ctrl+[ is nobody's ink key", inkCommand(Key.LeftBracket, alt = false, ctrl = true))
        assertNull("a tool's letter is not an ink key", inkCommand(Key.B, alt = false, ctrl = false))
        assertNull(inkCommand(Key.Five, alt = false, ctrl = false))
        assertEquals("and the shell still sees its own command", ShellCommand.FIT_WIDTH, shellCommand(Key.One, ctrl = true, shift = false))
        assertEquals(DesktopTool.PEN, toolForKey(Key.B, shift = false, ctrl = false))
    }

    @Test
    fun `picking a favourite sets the ink, and alt writes the file`() {
        val file = File(temp.root, "favourites.properties")
        val state = DesktopShellState(DesktopSettings(file))
        val green = state.favourites[1]

        state.pickFavourite(1)
        assertEquals(green, state.ink.colour)

        state.pickColour(PenPalette.ORANGE)
        state.setFavourite(1)
        assertEquals(PenPalette.ORANGE, state.favourites[1])
        assertEquals("the redefinition survives a restart", PenPalette.ORANGE, DesktopSettings(file).colourFavourites[1])

        val untouched = DesktopSettings(file).colourFavourites
        assertEquals("the other two slots are untouched", PenPalette.COLORS[0], untouched[0])
        assertEquals(PenPalette.COLORS[2], untouched[2])
    }

    @Test
    fun `the width steps S to M to L and stops at both ends`() {
        val file = File(temp.root, "width.properties")
        val state = DesktopShellState(DesktopSettings(file))

        state.pickWidth(PenPalette.DEFAULT_WIDTHS[0])
        state.stepWidth(1)
        assertEquals("S -> M", PenPalette.DEFAULT_WIDTHS[1], state.ink.width, 1e-4f)
        state.stepWidth(1)
        assertEquals("M -> L", PenPalette.DEFAULT_WIDTHS[2], state.ink.width, 1e-4f)
        state.stepWidth(1)
        assertEquals("L is the top", PenPalette.DEFAULT_WIDTHS[2], state.ink.width, 1e-4f)

        state.stepWidth(-1)
        assertEquals("L -> M", PenPalette.DEFAULT_WIDTHS[1], state.ink.width, 1e-4f)
        state.stepWidth(-1)
        state.stepWidth(-1)
        assertEquals("S is the bottom", PenPalette.DEFAULT_WIDTHS[0], state.ink.width, 1e-4f)
        assertEquals("and the strip lights the S slot", 0, PenPalette.widthSlot(state.ink.width))
        assertEquals("the last step is in the file", PenPalette.DEFAULT_WIDTHS[0], DesktopSettings(file).inkWidth, 1e-4f)
    }

    @Test
    fun `a short or hand-edited favourite list is filled and repaired`() {
        assertEquals(PenPalette.COLORS.take(3), settings().colourFavourites)

        val file = File(temp.root, "short.properties")
        file.writeText("interface.colourFavourites=00ff00,zzzz,00ff00\n")
        assertEquals(
            "opaque, de-duplicated, and filled to three",
            listOf(0xFF00FF00.toInt(), PenPalette.BLACK, PenPalette.RED),
            DesktopSettings(file).colourFavourites,
        )
    }

    @Test
    fun `the colour pill names a favourite's digit`() {
        val favourites = listOf(PenPalette.RED, PenPalette.GREEN, PenPalette.BLUE)
        assertEquals("2", colourKeyLabel(favourites, PenPalette.GREEN))
        assertEquals("all three when the colour is not a favourite", "1 2 3", colourKeyLabel(favourites, PenPalette.WHITE))
    }

    /** The rail drawn with a non-default ink: the slot is that colour, and its letter is the size. */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `the rail shows the ink the keys picked`() {
        val wide = railPng(DesktopInk(PenPalette.GREEN, 2.6f), "ink-keys-l")
        val narrow = railPng(DesktopInk(PenPalette.GREEN, 0.85f), "ink-keys-s")

        val image = ImageIO.read(ByteArrayInputStream(wide))
        // The stroke slot is a dot of the ink — a coordinate would move with the strip's arrangement,
        // the colour does not.
        assertTrue(
            "the stroke slot draws the picked colour",
            countInk(image, PenPalette.GREEN) > 4,
        )
        // One dot for **every** width, deliberately (`InkSlotMetrics.previewSize`): a dot drawn at the width
        // itself made M a smudge and left the three sizes incomparable, so the letter under it is what says
        // S, M or L — and the panel's three choices are the ones that read a width as a dot.
        val wideDot = countInk(image, PenPalette.GREEN)
        val narrowDot = countInk(ImageIO.read(ByteArrayInputStream(narrow)), PenPalette.GREEN)
        assertEquals("one dot at every width (L=$wideDot, S=$narrowDot)", wideDot, narrowDot)
        assertEquals("L is the wide pen's letter", "L", widthLetter(2.6f))
        assertEquals("S is the thin one's", "S", widthLetter(0.85f))
        assertTrue("and the two renders differ, because the letter does", !wide.contentEquals(narrow))
        assertTrue("the panel's dots are the ones that read the width", dotSize(2.6f) > dotSize(0.85f))
    }

    // --- rendering ------------------------------------------------------------------------------

    /**
     * The whole rail, tall enough to hold every tool and the two ink slots under them:
     * the lane scrolls, so a height written down for a shorter strip would cut the ink slots off
     * the bottom — and a clipped size dot measures the same at every width.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    private fun railPng(ink: DesktopInk, name: String): ByteArray {
        val stride = (ToolStripMetrics.slotSize + ToolStripMetrics.gap).value.toInt()
        val height = (DESKTOP_TOOL_GROUPS.size + 4) * stride
        val scene = ImageComposeScene(ToolStripMetrics.railWidth.value.toInt(), height, Density(1f)) {
            ToolRail(state = ToolStripState(ink = ink), onSelect = {})
        }
        val png = try {
            scene.render().encodeToData()?.bytes
        } finally {
            scene.close()
        }
        requireNotNull(png) { "Skia produced no PNG for $name" }
        File("build/desktop-render/$name.png").apply { parentFile?.mkdirs() }.writeBytes(png)
        return png
    }

    /** Pixels of [image] painted in [colour]. */
    private fun countInk(image: java.awt.image.BufferedImage, colour: Int): Int {
        var count = 0
        for (x in 0 until image.width) {
            for (y in 0 until image.height) {
                if ((image.getRGB(x, y) and 0xFFFFFF) == (colour and 0xFFFFFF)) count++
            }
        }
        return count
    }

}
