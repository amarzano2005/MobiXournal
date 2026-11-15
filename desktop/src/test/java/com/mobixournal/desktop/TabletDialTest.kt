package com.mobixournal.desktop

import com.mobixournal.desktop.ink.DialNotchAccumulator
import com.mobixournal.desktop.ink.comGuid
import com.mobixournal.desktop.ink.dialFit
import com.mobixournal.desktop.ink.dialWheelZoom
import com.mobixournal.desktop.io.DocumentLoader
import com.mobixournal.desktop.settings.DesktopSettings
import com.mobixournal.desktop.sheet.FitMode
import com.mobixournal.desktop.sheet.Viewport
import com.mobixournal.desktop.shell.DesktopShellState
import com.mobixournal.format.mobix.SyntheticNotebook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.math.exp

/**
 * The dial, as arithmetic and as the shell's command.
 *
 * A radial controller cannot be faked on a build machine, so what is checked is everything around it:
 * the **bytes** a GUID becomes (a `Data1` written big-endian would make every activation call fail on
 * the user's machine and pass here, so the byte order is asserted rather than assumed), the **notch
 * mapping** between Windows' degree bursts and the wheel's step, and the two commands the dial's
 * rotation and button run in the shell.
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
    fun `uneven degree bursts become whole notches, and a reversal drops the remainder`() {
        val acc = DialNotchAccumulator()
        assertEquals("4° is not a notch yet", 0, acc.add(4.0))
        assertEquals("4° + 6° is", 1, acc.add(6.0))
        assertEquals("21° is two notches with 1° kept", 2, acc.add(21.0))
        assertEquals("no degrees, no notch", 0, acc.add(0.0))
        // Reversing direction discards the +1° remainder and fires immediately on -10°.
        assertEquals(-1, acc.add(-10.0))
        // The same turn the other way leaves no notch behind: 9.99° is still nothing.
        assertEquals(0, acc.add(-9.99))
        acc.reset()
        assertEquals("reset drops the remainder", 0, acc.add(9.99))
        assertEquals(1, acc.add(0.01))
    }

    @Test
    fun `a turn of the dial zooms the tab by the wheel's step and its button restores the automatic fit`() {
        val settings = DesktopSettings(temp.newFile("dial-settings.properties"))
        val file = temp.newFile("dial.mobix")
        SyntheticNotebook.write(file, SyntheticNotebook.pages(3))
        val state = DesktopShellState(settings)
        state.tabs.open(DocumentLoader.load(file))
        val tab = state.active!!
        tab.fit = FitMode.PAGE
        tab.viewport = Viewport(1f, 0f, 0f)

        state.dialWheelZoom(1)
        assertEquals("a free zoom is no longer a fit", FitMode.NONE, tab.fit)
        assertEquals("one notch is Ctrl+wheel's step", exp(settings.navigation.zoomStepWheel), tab.viewport.zoom, 1e-4f)

        state.dialWheelZoom(-1)
        assertEquals("and the other way undoes it", 1f, tab.viewport.zoom, 1e-4f)

        state.dialFit()
        assertEquals("the button asks what the page is", FitMode.AUTO, tab.fit)
        assertEquals(0f, tab.viewport.offsetX, 0f)
        assertEquals(0f, tab.viewport.offsetY, 0f)
    }

    @Test
    fun `a burst of notches is one clamped step, never a jump`() {
        // 400 notches in one event would be e^48; the step is clamped to 2× before it reaches the view,
        // and then to the readout's own limits — the same path `Ctrl + wheel` takes.
        fun fresh(name: String): com.mobixournal.desktop.shell.DesktopTab {
            val file = temp.newFile("$name.mobix")
            SyntheticNotebook.write(file, SyntheticNotebook.pages(1))
            val state = DesktopShellState(DesktopSettings(temp.newFile("$name.properties")))
            state.tabs.open(DocumentLoader.load(file))
            val tab = state.active!!
            tab.fit = FitMode.NONE
            tab.viewport = Viewport(1f, 0f, 0f)
            state.dialWheelZoom(if (name == "dial-in") 400 else -800)
            return tab
        }

        val inward = fresh("dial-in")
        assertEquals(
            "one event stops at twice the zoom, then at the ceiling",
            Viewport.clampZoom(2f, inward.widthFitZoom),
            inward.viewport.zoom,
            1e-4f,
        )
        val outward = fresh("dial-out")
        assertEquals(
            "and the other way stops at half, then at the floor",
            Viewport.clampZoom(0.5f, outward.widthFitZoom),
            outward.viewport.zoom,
            1e-4f,
        )
    }

    @Test
    fun `no notches and no document are both a no-op`() {
        val file = temp.newFile("dial-quiet.mobix")
        SyntheticNotebook.write(file, SyntheticNotebook.pages(1))
        val state = DesktopShellState(DesktopSettings(temp.newFile("dial-quiet.properties")))
        state.tabs.open(DocumentLoader.load(file))
        val tab = state.active!!
        tab.fit = FitMode.PAGE
        tab.viewport = Viewport(1f, 0f, 0f)

        state.dialWheelZoom(0)
        assertEquals("a remainder is not a turn", FitMode.PAGE, tab.fit)
        assertEquals(1f, tab.viewport.zoom, 1e-4f)

        val empty = DesktopShellState(DesktopSettings(temp.newFile("dial-empty.properties")))
        empty.dialWheelZoom(3)
        empty.dialFit()
        assertEquals(null, empty.active)
        assertTrue("nothing was opened", empty.tabs.all.isEmpty())
    }
}
