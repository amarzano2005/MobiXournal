package com.mobixournal.desktop

import androidx.compose.ui.input.key.Key
import java.awt.DisplayMode
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The Display section's two halves: the whole-screen key, and the one thing only a full-screen window can
 * ask the panel for — its rate.
 *
 * The interesting half of a refresh-rate request is the **refusal**. A laptop panel has no 144 Hz mode and
 * a remote session refuses every mode change, so the only correct answer there is to leave the display
 * exactly as it was and keep reporting its real rate — a target that read back as applied would make the
 * status bar the one readout in the window that lies. That is what the fake [DisplaySurface] pins.
 */
class FullScreenTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** The platform call, minus the platform: it says what it is told to. */
    private class FakeSurface(
        override var current: DisplayMode,
        override val modes: List<DisplayMode>,
        private val accepts: Boolean = true,
    ) : DisplaySurface {
        var requests = 0
            private set

        override fun request(mode: DisplayMode): Boolean {
            requests++
            if (!accepts) return false
            current = mode
            return true
        }
    }

    /** One 1440p panel with three rates, plus a 1080p mode at 144 that must never be picked. */
    private val panel = listOf(
        DisplayMode(2560, 1440, 32, 60),
        DisplayMode(2560, 1440, 32, 120),
        DisplayMode(2560, 1440, 32, 144),
        DisplayMode(1920, 1080, 32, 144),
    )

    @Test
    fun `the target is the mode the window asked the display for`() {
        val surface = FakeSurface(DisplayMode(2560, 1440, 32, 60), panel)

        assertTrue("the panel has this rate", requestRefresh(surface, 144))
        assertEquals("the display now runs at the target", 144, surface.current.refreshRate)
        assertEquals("and the resolution never moves with the rate", 2560, surface.current.width)
    }

    @Test
    fun `a rate the panel does not have is not approximated`() {
        val surface = FakeSurface(DisplayMode(2560, 1440, 32, 60), panel)

        assertNull("no 90 Hz mode", modeFor(surface, 90))
        assertFalse(requestRefresh(surface, 90))
        assertEquals("the panel keeps its own rate", 60, surface.current.refreshRate)
        assertEquals("and nothing was even asked", 0, surface.requests)
    }

    @Test
    fun `a refused mode leaves the display and its readout alone`() {
        val surface = FakeSurface(DisplayMode(2560, 1440, 32, 60), panel, accepts = false)
        val readout = refreshLabel(surface.current.refreshRate)

        assertFalse("the platform refused", requestRefresh(surface, 120))
        assertEquals("the mode is untouched", 60, surface.current.refreshRate)
        assertEquals(
            "and the status bar still reports the rate the panel is really at",
            readout,
            refreshLabel(surface.current.refreshRate),
        )
    }

    @Test
    fun `a mode at another resolution is never the answer`() {
        val surface = FakeSurface(
            DisplayMode(1920, 1080, 32, 60),
            listOf(DisplayMode(2560, 1440, 32, 144)),
        )
        assertNull("resizing the desktop is not a refresh-rate change", modeFor(surface, 144))
    }

    @Test
    fun `a display that cannot be asked says so instead of throwing`() {
        assertFalse(requestRefresh(null, DEFAULT_TARGET_HZ))
    }

    @Test
    fun `f11 is the whole-screen key`() {
        assertEquals(
            ShellCommand.TOGGLE_FULLSCREEN,
            shellCommand(Key.F11, ctrl = false, shift = false),
        )
        // The rest of the table is untouched: no other key means it, so the row is one binding.
        assertNull(shellCommand(Key.F10, ctrl = false, shift = false))
    }

    @Test
    fun `the whole-screen flag is a state, not a stored setting`() {
        val policy = DesktopSettingsState(DesktopSettings(File(temp.root, "screen.properties")))
        assertFalse("a window opens as a window", policy.fullScreen)
        policy.toggleFullScreen()
        assertTrue(policy.fullScreen)
        policy.toggleFullScreen()
        assertFalse("and gives the desk back", policy.fullScreen)
        assertFalse(
            "a restart never takes the desk from the reader",
            DesktopSettingsState(DesktopSettings(File(temp.root, "screen.properties"))).fullScreen,
        )
    }

    @Test
    fun `the target rate is stored, and read back by the next window`() {
        val file = File(temp.root, "display.properties")
        val policy = DesktopSettingsState(DesktopSettings(file))
        assertEquals("the documented default", DEFAULT_TARGET_HZ, policy.targetHz)

        policy.chooseTargetHz(144)
        assertEquals(144, DesktopSettings(file).targetHz)
        assertEquals(144, DesktopSettingsState(DesktopSettings(file)).targetHz)
    }

    @Test
    fun `a stored rate the section does not offer falls back to the default`() {
        val file = File(temp.root, "odd.properties")
        file.writeText("display.targetHz=59\n")
        assertEquals(DEFAULT_TARGET_HZ, DesktopSettings(file).targetHz)
    }
}
