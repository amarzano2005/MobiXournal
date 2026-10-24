package com.mobixournal.desktop

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

    /** The rail drawn with a non-default ink: the capsule is that colour and the dot follows the width. */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `the rail shows the ink the keys picked`() {
        val wide = railPng(DesktopInk(PenPalette.GREEN, 2.6f), "ink-keys-l")
        val narrow = railPng(DesktopInk(PenPalette.GREEN, 0.85f), "ink-keys-s")

        val image = ImageIO.read(ByteArrayInputStream(wide))
        // The ink slots sit at the tail of the rail and the rail now floats in its lane, so the swatch
        // is found by its own colour rather than by a coordinate that moved when the capsule arrived.
        assertTrue(
            "the capsule is the picked colour",
            countInk(image, PenPalette.GREEN) > 40,
        )
        assertTrue("the L dot has more ink than the S dot", inkBelow(image) > inkBelow(ImageIO.read(ByteArrayInputStream(narrow))))
    }

    // --- rendering ------------------------------------------------------------------------------

    /** The whole rail, tall enough to hold every tool and the two ink slots under them. */
    @OptIn(ExperimentalComposeUiApi::class)
    private fun railPng(ink: DesktopInk, name: String): ByteArray {
        val scene = ImageComposeScene(ToolStripMetrics.railWidth.value.toInt(), 900, Density(1f)) {
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

    /** Pixels of the rail's ink colour in the size slot, the strip's last rows. */
    /**
     * Green pixels in the rail's tail — the ink slots, which sit under every tool slot. The window is
     * generous rather than tight because it must follow the tool list: eighteen slots push the ink row
     * down, and a window sized to the old fifteen would measure empty capsule in both images and pass
     * by accident.
     */
    private fun inkBelow(image: java.awt.image.BufferedImage): Int {
        var count = 0
        for (x in 0 until image.width) {
            for (y in (image.height - 200) until image.height) {
                if ((image.getRGB(x, y) and 0xFFFFFF) == (PenPalette.GREEN and 0xFFFFFF)) count++
            }
        }
        return count
    }
}
