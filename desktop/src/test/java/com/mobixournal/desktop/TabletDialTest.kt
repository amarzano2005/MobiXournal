package com.mobixournal.desktop

import com.mobixournal.format.mobix.SyntheticNotebook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The dial, as arithmetic and as the shell's command.
 *
 * A radial controller cannot be faked on a build machine, so what is checked is everything around it:
 * the **bytes** a GUID becomes (a `Data1` written big-endian would make every activation call fail on
 * the user's machine and pass here, so the byte order is asserted rather than assumed), the **zoom
 * mapping**, and the two commands the dial's rotation and button run in the shell.
 */
class TabletDialTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `a guid becomes the sixteen bytes COM reads`() {
        // IRadialController, quoted from windows.ui.input.h, byte by byte.
        val guid = comGuid("3055D1C8-DF51-43D4-B23B-0E1037467A09")
        assertEquals("Data1 is little-endian", 0x3055D1C8, guid.getInt(0))
        assertEquals("Data2 is little-endian", 0xDF51, guid.getShort(4).toInt() and 0xFFFF)
        assertEquals("Data3 is little-endian", 0x43D4, guid.getShort(6).toInt() and 0xFFFF)
        val data4 = byteArrayOf(0xB2.toByte(), 0x3B, 0x0E, 0x10, 0x37, 0x46, 0x7A, 0x09)
        for ((index, expected) in data4.withIndex()) {
            assertEquals("Data4[$index] is written as it reads", expected, guid.getByte((8 + index).toLong()))
        }
    }

    @Test
    fun `dial rotation is an exponential zoom, symmetric around still`() {
        assertEquals("no rotation, no zoom", 1f, dialZoomFactor(0.0), 1e-6f)
        assertTrue("clockwise zooms in", dialZoomFactor(12.0) > 1f)
        assertTrue("anticlockwise zooms out", dialZoomFactor(-12.0) < 1f)
        assertEquals(
            "the same turn either way cancels out",
            1f,
            dialZoomFactor(18.0) * dialZoomFactor(-18.0),
            1e-5f,
        )
        assertEquals("one event cannot jump the page", 2f, dialZoomFactor(4000.0), 1e-6f)
        assertEquals(0.5f, dialZoomFactor(-4000.0), 1e-6f)
    }

    @Test
    fun `a turn of the dial zooms the tab and its button restores the automatic fit`() {
        val file = temp.newFile("dial.mobix")
        SyntheticNotebook.write(file, SyntheticNotebook.pages(3))
        val state = DesktopShellState(DesktopSettings(temp.newFile("dial-settings.properties")))
        state.tabs.open(DocumentLoader.load(file))
        val tab = state.active!!
        tab.fit = FitMode.PAGE
        tab.viewport = Viewport(1f, 0f, 0f)

        state.dialZoom(36.0)
        assertEquals("a free zoom is no longer a fit", FitMode.NONE, tab.fit)
        assertTrue("and it zoomed in (${tab.viewport.zoom})", tab.viewport.zoom > 1f)

        state.dialFit()
        assertEquals("the button asks what the page is", FitMode.AUTO, tab.fit)
        assertEquals(0f, tab.viewport.offsetX, 0f)
        assertEquals(0f, tab.viewport.offsetY, 0f)
    }

    @Test
    fun `a dial with nothing open does nothing instead of failing`() {
        val state = DesktopShellState(DesktopSettings(temp.newFile("empty-settings.properties")))
        state.dialZoom(90.0)
        state.dialFit()
        assertEquals(null, state.active)
    }
}
