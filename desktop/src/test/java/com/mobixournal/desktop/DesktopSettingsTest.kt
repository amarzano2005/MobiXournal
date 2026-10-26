package com.mobixournal.desktop

import androidx.compose.ui.input.key.Key
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The Settings, as a **store and a state**: every choice is one key, every change is written the moment the
 * row is clicked, and a restart reads the answers back.
 *
 * The restart is the half that cannot be seen by eye — a window that only held the value in Compose state
 * would look right until the next launch — so each setting is written through [DesktopSettingsState],
 * reopened from the same file, and read again, which is the test a new row must extend.
 */
class DesktopSettingsTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun store(name: String = "desktop.properties") = DesktopSettings(File(temp.root, name))

    @Test
    fun `every choice survives a restart`() {
        val file = File(temp.root, "restart.properties")
        val policy = DesktopSettingsState(DesktopSettings(file))
        policy.chooseStripPlacement(StripPlacement.TOP)
        policy.chooseRailSide(RailSide.RIGHT)
        policy.chooseFitOnOpen(FitOnOpen.WIDTH)
        policy.chooseSidebarPolicy(SidebarPolicy.ALWAYS)
        policy.chooseInspectorPolicy(InspectorPolicy.NEVER)
        policy.chooseFrameTime(false)
        policy.choosePenStream(PenStream.INK)

        val reopened = DesktopSettings(file)
        assertEquals(StripPlacement.TOP, reopened.stripPlacement)
        assertEquals(RailSide.RIGHT, reopened.railSide)
        assertEquals(FitOnOpen.WIDTH, reopened.fitOnOpen)
        assertEquals(SidebarPolicy.ALWAYS, reopened.sidebarPolicy)
        assertEquals(InspectorPolicy.NEVER, reopened.inspectorPolicy)
        assertFalse("a readout switched off stays off", reopened.frameTime)
        assertEquals(PenStream.INK, reopened.penStream)

        // And the state a second window builds over the same file opens on those answers.
        val second = DesktopSettingsState(reopened)
        assertEquals(StripPlacement.TOP, second.stripPlacement)
        assertEquals(RailSide.RIGHT, second.railSide)
        assertEquals(FitOnOpen.WIDTH, second.fitOnOpen)
        assertEquals(SidebarPolicy.ALWAYS, second.sidebarPolicy)
        assertEquals(InspectorPolicy.NEVER, second.inspectorPolicy)
        assertFalse(second.frameTime)
        assertEquals(PenStream.INK, second.penStream)
    }

    /** A first run: the window's documented defaults, and nothing invented. */
    @Test
    fun `a window with no file opens on the documented defaults`() {
        val fresh = store("fresh.properties")
        assertEquals(StripPlacement.SIDE, fresh.stripPlacement)
        assertEquals(RailSide.LEFT, fresh.railSide)
        assertEquals(FitOnOpen.AUTO, fresh.fitOnOpen)
        assertEquals(SidebarPolicy.AUTOMATIC, fresh.sidebarPolicy)
        assertEquals(InspectorPolicy.AUTOMATIC, fresh.inspectorPolicy)
        assertTrue("the frame time is reported until it is switched off", fresh.frameTime)
        assertEquals(PenStream.AUTOMATIC, fresh.penStream)
    }

    /**
     * The three names the properties file held before the Settings row existed: an install that had already
     * picked `Ribbon` must not find its strips back on the rail.
     */
    @Test
    fun `the placement names this file used to hold still answer`() {
        assertEquals(StripPlacement.SIDE, StripPlacement.of("Rail"))
        assertEquals(StripPlacement.TOP, StripPlacement.of("Ribbon"))
        assertEquals(StripPlacement.BOTH, StripPlacement.of("Both"))
        assertEquals(StripPlacement.TOP, StripPlacement.of("top"))

        val legacy = File(temp.root, "legacy.properties")
        legacy.writeText("interface.toolPlacement=Ribbon\n")
        assertEquals(StripPlacement.TOP, DesktopSettings(legacy).stripPlacement)
    }

    /** An unknown value is the default, never an exception: a hand-edited file still opens a window. */
    @Test
    fun `an unknown value falls back to the default`() {
        assertEquals(StripPlacement.SIDE, StripPlacement.of("Diagonal"))
        assertEquals(RailSide.LEFT, RailSide.of("upwards"))
        assertEquals(FitOnOpen.AUTO, FitOnOpen.of("zoomed"))
        assertEquals(SidebarPolicy.AUTOMATIC, SidebarPolicy.of("sometimes"))
        assertEquals(InspectorPolicy.AUTOMATIC, InspectorPolicy.of("always"))
        assertEquals(PenStream.AUTOMATIC, PenStream.of("bluetooth"))
    }

    @Test
    fun `ctrl and comma opens the settings, and escape closes them`() {
        assertEquals(
            "the window's door to its own settings",
            ShellCommand.OPEN_SETTINGS,
            shellCommand(Key.Comma, ctrl = true, shift = false),
        )
        assertEquals("a bare comma is not a command", null, shellCommand(Key.Comma, ctrl = false, shift = false))

        val policy = DesktopSettingsState(store())
        assertEquals("closed, a key is the window's", SettingsKeyAction.NONE, policy.keyAction(Key.Escape))
        policy.openAt()
        assertTrue(policy.open)
        assertEquals(SettingsKeyAction.CLOSE, policy.keyAction(Key.Escape))
        assertTrue(policy.press(Key.Escape))
        assertFalse(policy.open)
    }

    /** The search is a filter with an `Esc` of its own: the first clears it, the second puts the page away. */
    @Test
    fun `escape clears the search before it closes the surface`() {
        val policy = DesktopSettingsState(store())
        policy.openAt()
        policy.search("wintab")
        assertEquals(SettingsKeyAction.CLEAR_SEARCH, policy.keyAction(Key.Escape))

        assertTrue(policy.press(Key.Escape))
        assertEquals("", policy.query)
        assertTrue("clearing the field keeps the page up", policy.open)

        assertTrue(policy.press(Key.Escape))
        assertFalse("the second escape closes it", policy.open)
    }

    /** Nothing reaches the page while the surface covers it — with the field typing, the field has it. */
    @Test
    fun `the page's keys do not reach a page the surface covers`() {
        val policy = DesktopSettingsState(store())
        assertEquals(SettingsKeyAction.NONE, policy.keyAction(Key.G))

        policy.openAt()
        assertEquals(SettingsKeyAction.CAPTURE, policy.keyAction(Key.G))
        assertTrue("the surface takes the key", policy.press(Key.G))

        policy.typing = true
        assertEquals("a focused field is typing, not commanding", SettingsKeyAction.NONE, policy.keyAction(Key.G))
        assertFalse(policy.press(Key.G))
    }

    /** The field **finds** a section by an everyday word, and the page follows the list. */
    @Test
    fun `the search narrows the sections by a word the reader knows`() {
        val policy = DesktopSettingsState(store())
        policy.openAt()
        assertEquals(DesktopSettingSection.entries, policy.sections)

        policy.search("wintab")
        assertEquals(listOf(DesktopSettingSection.STYLUS), policy.sections)
        assertEquals("the page follows the list", DesktopSettingSection.STYLUS, policy.shownSection)

        policy.search("toolbar")
        assertEquals(listOf(DesktopSettingSection.TOOLBAR), policy.sections)

        policy.search("zzz")
        assertTrue("nothing matches, and the page says so", policy.sections.isEmpty())
        assertNull(policy.shownSection)
    }
}
