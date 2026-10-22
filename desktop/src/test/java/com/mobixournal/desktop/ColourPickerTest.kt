package com.mobixournal.desktop

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.mobixournal.design.PenPalette
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The desktop colour panel: its hex field, its HSV arithmetic, what **Set** writes and what an invalid
 * entry must not do.
 *
 * The claims a user would notice were false are the ones pinned here: `#12ab34` and `12AB34` are the same
 * colour and the field prints it back uppercase; a half-typed hex keeps the last valid colour, says so,
 * and leaves **Set** out of reach; the custom slot distinguishes *empty* from *black*; a new colour is
 * appended opaque, once, and becomes the ink; and both writes survive a restart.
 *
 * The panel and the palette row are rendered headlessly — the `Popup` that anchors the row is not, the
 * same limitation the placement chooser has.
 */
class ColourPickerTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun settings(name: String = "desktop.properties") = DesktopSettings(File(temp.root, name))

    @Test
    fun `the hex field accepts the two spellings a person types, and prints one`() {
        assertEquals("#12AB34", ColourPicker.hex(0xFF12AB34.toInt()))
        assertEquals("#000000", ColourPicker.hex(0xFF000000.toInt()))
        assertEquals(0xFF12AB34.toInt(), ColourPicker.parse("#12ab34"))
        assertEquals("the leading # is optional", 0xFF12AB34.toInt(), ColourPicker.parse("12AB34"))
        assertEquals("and the text is trimmed", 0xFF12AB34.toInt(), ColourPicker.parse("  #12ab34 "))

        assertNull("three digits are refused, not guessed", ColourPicker.parse("#FFF"))
        assertNull("eight digits are refused too", ColourPicker.parse("#12AB34FF"))
        assertNull(ColourPicker.parse("zzzzzz"))
        assertNull(ColourPicker.parse(""))
        assertNull(ColourPicker.parse("#12AB3"))
    }

    @Test
    fun `hsv and argb are inverses for the colours the wheel can make`() {
        assertEquals(0xFF000000.toInt(), ColourPicker.hsv(0f, 0f, 0f))
        assertEquals(0xFFFFFFFF.toInt(), ColourPicker.hsv(0f, 0f, 1f))
        assertEquals(0xFFFF0000.toInt(), ColourPicker.hsv(0f, 1f, 1f))
        assertEquals(0xFF00FF00.toInt(), ColourPicker.hsv(120f, 1f, 1f))
        assertEquals(0xFF0000FF.toInt(), ColourPicker.hsv(240f, 1f, 1f))

        for (colour in listOf(PenPalette.RED, PenPalette.BLUE, 0xFF12AB34.toInt(), 0xFF808080.toInt())) {
            val hsv = ColourPicker.hsvOf(colour)
            assertEquals(
                "round trip through HSV for #%06X".format(colour and 0xFFFFFF),
                colour,
                ColourPicker.hsv(hsv.hue, hsv.saturation, hsv.value),
            )
        }
    }

    @Test
    fun `typing a hex moves the markers, and an invalid one keeps the last colour`() {
        val red = ColourDraft.fromColour(PenPalette.RED)
        assertEquals("#FF0000", red.text)
        assertFalse(red.invalid)

        val green = red.withText("#00ff00")
        assertEquals(0xFF00FF00.toInt(), green.colour)
        assertEquals("120", "%.0f".format(green.hue))
        assertFalse(green.invalid)

        val broken = green.withText("#12")
        assertTrue("the field flags what it cannot read", broken.invalid)
        assertEquals("the colour stays where it was", 0xFF00FF00.toInt(), broken.colour)
        assertEquals("and the text stays on screen for the user to fix", "#12", broken.text)
    }

    @Test
    fun `dragging a slider rewrites the hex field`() {
        val draft = ColourDraft.fromColour(PenPalette.RED).withValue(0.5f)
        assertEquals(ColourPicker.hex(draft.colour), draft.text)
        assertNotEquals("#FF0000", draft.text)
        assertFalse(draft.invalid)
    }

    @Test
    fun `the custom slot tells empty from black`() {
        assertNull("a fresh window has no custom colour", settings().paletteCustom)

        val custom = settings()
        custom.paletteCustom = 0xFF000000.toInt()
        assertEquals("black is a colour, not an empty slot", 0xFF000000.toInt(), settings().paletteCustom)

        custom.paletteCustom = null
        assertNull("clearing the slot removes the key", settings().paletteCustom)
    }

    @Test
    fun `Set on the custom slot defines it, picks it and keeps it`() {
        val file = File(temp.root, "custom.properties")
        val state = DesktopShellState(DesktopSettings(file))
        assertNull(state.ink.custom)

        state.editColour(ColourEditorTarget.CUSTOM)
        state.commitColour(0xFF9C27B0.toInt())

        assertNull("the panel closed itself", state.editing)
        assertEquals(0xFF9C27B0.toInt(), state.ink.custom)
        assertEquals("the colour just defined is the ink", 0xFF9C27B0.toInt(), state.ink.colour)
        val reloaded = DesktopSettings(file)
        assertEquals(0xFF9C27B0.toInt(), reloaded.paletteCustom)
        assertEquals(0xFF9C27B0.toInt(), reloaded.inkColour)
    }

    @Test
    fun `Set on the add swatch appends once and picks the colour`() {
        val file = File(temp.root, "add.properties")
        val state = DesktopShellState(DesktopSettings(file))
        val before = state.palette.size

        state.editColour(ColourEditorTarget.NEW)
        state.commitColour(0x00123456)
        assertEquals("the new colour is opaque", 0xFF123456.toInt(), state.ink.colour)
        assertEquals("and appended once", before + 1, state.palette.size)
        assertEquals(0xFF123456.toInt(), state.palette.last())

        state.editColour(ColourEditorTarget.NEW)
        state.commitColour(0xFF123456.toInt())
        assertEquals("adding it again is not a twin", before + 1, state.palette.size)

        assertEquals("the palette survives a restart", state.palette, DesktopSettings(file).paletteColours)
    }

    @Test
    fun `cancelling or Esc changes nothing`() {
        val file = File(temp.root, "cancel.properties")
        val state = DesktopShellState(DesktopSettings(file))
        val inkBefore = state.ink
        val paletteBefore = state.palette

        state.editColour(ColourEditorTarget.NEW)
        assertEquals(ColourEditorTarget.NEW, state.editing)
        state.cancelColourEdit()

        assertNull(state.editing)
        assertEquals(inkBefore, state.ink)
        assertEquals(paletteBefore, state.palette)
        assertEquals(paletteBefore, DesktopSettings(file).paletteColours)
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `the panel renders its preview, and the palette row its custom swatch`() {
        val picker = ImageIO.read(ByteArrayInputStream(render("colour-picker") { ColourPickerPanel(
            title = "Custom colour",
            initial = 0xFF12AB34.toInt(),
            onSet = {},
            onCancel = {},
        ) }))
        var preview = 0
        for (x in 0 until picker.width) {
            for (y in 0 until picker.height) {
                if ((picker.getRGB(x, y) and 0xFFFFFF) == 0x12AB34) preview++
            }
        }
        assertTrue("the preview swatch is the colour being edited ($preview px)", preview > 200)

        val flyout = ImageIO.read(ByteArrayInputStream(render("colour-flyout") {
            PaletteFlyout(
                palette = PenPalette.COLORS,
                current = PenPalette.GREEN,
                custom = 0xFF9C27B0.toInt(),
                onPick = {},
                onEditCustom = {},
                onAddColour = {},
            )
        }))
        // The custom swatch is the ninth: eight palette swatches, then it (26 dp each, 6 dp apart).
        assertEquals(
            "the custom swatch is filled with the custom colour",
            0x9C27B0,
            flyout.getRGB(8 + 8 * 32 + 13, 6 + 13) and 0xFFFFFF,
        )
    }

    // --- rendering ------------------------------------------------------------------------------

    @OptIn(ExperimentalComposeUiApi::class)
    private fun render(name: String, content: @androidx.compose.runtime.Composable () -> Unit): ByteArray {
        val scene = ImageComposeScene(420, 360, Density(1f), content = content)
        val png = try {
            scene.render().encodeToData()?.bytes
        } finally {
            scene.close()
        }
        requireNotNull(png) { "Skia produced no PNG for $name" }
        File("build/desktop-render/$name.png").apply { parentFile?.mkdirs() }.writeBytes(png)
        return png
    }
}
