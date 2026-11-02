package com.mobixournal.desktop

import com.mobixournal.desktop.ink.DesktopInk
import com.mobixournal.desktop.ink.PaletteCodec
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.settings.DesktopSettings
import com.mobixournal.desktop.shell.DesktopShellState
import com.mobixournal.desktop.tools.StrokeSlot
import com.mobixournal.desktop.tools.StripSide
import com.mobixournal.desktop.tools.ToolStripMetrics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.mobixournal.design.PenPalette
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The desktop's ink: the palette the colour flyout offers, the colour and width the strips show, and the
 * two new slots' pixels.
 *
 * The claims are the ones a user could notice were false: the palette and the picked ink are still there
 * after a restart; a hand-edited settings file cannot put a transparent colour or an empty palette in
 * front of the picker; picking writes the file; and the colour capsule really is the ink colour, with the
 * size slot's dot wider at `L` than at `S`.
 *
 * The flyouts themselves open as `Popup`s and are **not** covered here (the same limitation the placement
 * chooser has): a headless scene does not open one, so their contents are checked by the layout and the
 * theme guards, not by a click.
 */
class ColourFlyoutTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun settings(name: String = "desktop.properties") = DesktopSettings(File(temp.root, name))

    @Test
    fun `a fresh file opens on the factory palette and the middle width`() {
        val settings = settings()
        assertEquals(PenPalette.COLORS, settings.paletteColours)
        assertEquals(PenPalette.COLORS.first(), settings.inkColour)
        assertEquals(PenPalette.DEFAULT_WIDTHS[1], settings.inkWidth, 1e-4f)
    }

    @Test
    fun `the palette is opaque and de-duplicated on the way in`() {
        val settings = settings()
        settings.paletteColours = listOf(PenPalette.RED, 0x0012AB34, PenPalette.RED)
        assertEquals(
            listOf(PenPalette.RED, 0xFF12AB34.toInt()),
            settings().paletteColours,
        )
    }

    @Test
    fun `the picked colour and width survive a restart`() {
        val settings = settings()
        settings.inkColour = PenPalette.BLUE
        settings.inkWidth = 2.6f

        val reloaded = settings()
        assertEquals(PenPalette.BLUE, reloaded.inkColour)
        assertEquals(2.6f, reloaded.inkWidth, 1e-4f)
    }

    @Test
    fun `a hand-edited file cannot show a transparent palette or an out-of-range width`() {
        val file = File(temp.root, "edited.properties")
        file.writeText(
            """
            palette.colours=00ff00,00ff00,nothex
            ink.colour=also-not-hex
            ink.width=99
            """.trimIndent(),
        )
        val settings = DesktopSettings(file)
        assertEquals("transparent becomes opaque, duplicates collapse", listOf(0xFF00FF00.toInt()), settings.paletteColours)
        assertEquals("an unreadable colour falls back to the factory black", PenPalette.BLACK, settings.inkColour)
        assertEquals(PenPalette.WIDTH_MAX, settings.inkWidth, 1e-4f)
    }

    @Test
    fun `picking through the shell writes the file`() {
        val file = File(temp.root, "shell.properties")
        val state = DesktopShellState(DesktopSettings(file))

        state.pickColour(PenPalette.GREEN)
        state.pickWidth(2.6f)

        assertEquals(PenPalette.GREEN, state.ink.colour)
        assertEquals(2.6f, state.ink.width, 1e-4f)
        val reloaded = DesktopSettings(file)
        assertEquals(PenPalette.GREEN, reloaded.inkColour)
        assertEquals(2.6f, reloaded.inkWidth, 1e-4f)
        assertEquals("both strips read the one value", state.stripState.ink, state.ink)
    }

    @Test
    fun `the ink is opaque and its width is clamped`() {
        assertEquals(PenPalette.BLACK, DesktopInk().withColour(0x00000000).colour)
        assertEquals(PenPalette.WIDTH_MAX, DesktopInk().withWidth(99f).width, 1e-4f)
        assertEquals(PenPalette.WIDTH_MIN, DesktopInk().withWidth(0.01f).width, 1e-4f)
    }

    @Test
    fun `the codec round-trips every argb value`() {
        for (colour in listOf(PenPalette.BLACK, PenPalette.WHITE, 0xFF12AB34.toInt(), 0x80000000.toInt())) {
            val hex = PaletteCodec.hex(colour)
            assertEquals("$hex is eight digits", 8, hex.length)
            assertEquals(colour, PaletteCodec.parseHex(hex))
        }
        assertEquals(null, PaletteCodec.parseHex("zzzz"))
        assertEquals(null, PaletteCodec.parseHex(""))
    }

    /** The stroke slot's dot is the ink colour, and it grows with the width. */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `the slot shows the ink`() {
        val wide = ImageIO.read(ByteArrayInputStream(renderSlot(DesktopInk(PenPalette.GREEN, 2.6f), "ink-slot-l")))
        assertEquals(
            "the dot is filled with the ink colour",
            PenPalette.GREEN and 0xFFFFFF,
            wide.getRGB(SLOT_CENTRE_X, SLOT_CENTRE_Y) and 0xFFFFFF,
        )

        val narrow = ImageIO.read(ByteArrayInputStream(renderSlot(DesktopInk(PenPalette.GREEN, 0.85f), "ink-slot-s")))
        val wideDot = inkPixels(wide)
        val narrowDot = inkPixels(narrow)
        assertTrue("the stroke slot drew a dot ($wideDot px)", wideDot > 0)
        assertTrue(
            "the L dot is wider than the S dot ($wideDot vs $narrowDot)",
            wideDot > narrowDot,
        )
        assertNotEquals("changing the width repaints the slot", wideDot, narrowDot)
    }

    /** Where the slot's dot sits: the slot is centred in the rail's lane, and its dot in the slot. */
    private val SLOT_CENTRE_X: Int
        get() = (ToolStripMetrics.railSlotLeft + ToolStripMetrics.slotSize / 2).value.toInt()

    private val SLOT_CENTRE_Y: Int get() = 20

    // --- rendering ------------------------------------------------------------------------------

    /** The one stroke slot, at the strip's size. */
    @OptIn(ExperimentalComposeUiApi::class)
    private fun renderSlot(ink: DesktopInk, name: String): ByteArray {
        val scene = ImageComposeScene(ToolStripMetrics.railWidth.value.toInt(), 40, Density(1f)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                StrokeSlot(ink, PenPalette.COLORS, onPick = {}, onWidth = {}, side = StripSide.RIGHT)
            }
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

    /** How many pixels of the ink colour the render holds. */
    private fun inkPixels(image: java.awt.image.BufferedImage): Int {
        var count = 0
        for (x in 0 until image.width) {
            for (y in 0 until image.height) {
                if ((image.getRGB(x, y) and 0xFFFFFF) == (PenPalette.GREEN and 0xFFFFFF)) count++
            }
        }
        return count
    }
}
