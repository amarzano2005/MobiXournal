package com.mobixournal.desktop

import androidx.compose.ui.input.key.Key
import com.mobixournal.render.PressureCurve
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
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
        policy.chooseStripPlacement(StripPlacement.RIGHT)
        policy.chooseFitOnOpen(FitOnOpen.WIDTH)
        policy.chooseSidebarPolicy(SidebarPolicy.ALWAYS)
        policy.chooseInspectorPolicy(InspectorPolicy.NEVER)
        policy.chooseFrameTime(false)
        policy.choosePenStream(PenStream.INK)

        val reopened = DesktopSettings(file)
        assertEquals(StripPlacement.RIGHT, reopened.stripPlacement)
        assertEquals("the edge is part of the placement now", RailSide.RIGHT, reopened.stripPlacement.railSide)
        assertEquals(FitOnOpen.WIDTH, reopened.fitOnOpen)
        assertEquals(SidebarPolicy.ALWAYS, reopened.sidebarPolicy)
        assertEquals(InspectorPolicy.NEVER, reopened.inspectorPolicy)
        assertFalse("a readout switched off stays off", reopened.frameTime)
        assertEquals(PenStream.INK, reopened.penStream)

        // And the state a second window builds over the same file opens on those answers.
        val second = DesktopSettingsState(reopened)
        assertEquals(StripPlacement.RIGHT, second.stripPlacement)
        assertEquals(RailSide.RIGHT, second.railSide)
        assertEquals(FitOnOpen.WIDTH, second.fitOnOpen)
        assertEquals(SidebarPolicy.ALWAYS, second.sidebarPolicy)
        assertEquals(InspectorPolicy.NEVER, second.inspectorPolicy)
        assertFalse(second.frameTime)
        assertEquals(PenStream.INK, second.penStream)
    }

    /**
     * The Tablet section's pressure curve: identity until someone asks otherwise, remembered across a
     * restart, clamped to the model's own range, and — the part a file cannot show — in force for the
     * reading the next stroke is measured from.
     */
    @Test
    fun `the pressure curve survives a restart and shapes the next reading`() {
        val file = File(temp.root, "pressure.properties")
        val policy = DesktopSettingsState(DesktopSettings(file))
        assertEquals(PressureCurve.EXPONENT_IDENTITY, policy.pressureExponent, 1e-6f)
        assertEquals(
            "at the default the reading reaches the model untouched",
            0.42f,
            PressureFeel.shape(0.42f),
            1e-6f,
        )

        policy.choosePressureExponent(1.6f)
        assertEquals(1.6f, DesktopSettings(file).pressureExponent, 1e-6f)
        assertEquals(1.6f, DesktopSettingsState(DesktopSettings(file)).pressureExponent, 1e-6f)
        assertTrue("a hard curve holds a light press down", PressureFeel.shape(0.5f) < 0.5f)

        policy.choosePressureExponent(9f)
        assertEquals("clamped to the range the model offers", PressureCurve.EXPONENT_MAX, policy.pressureExponent, 1e-6f)
    }

    /**
     * The tablet's three pressure settings: remembered, clamped, and **in force** for the next reading.
     *
     * The filter is the same arithmetic as desktop Xournal++'s — floor and multiplier after the curve —
     * and the last part is the one a file cannot show: a switch that only reached the file would look
     * applied and draw exactly as before.
     */
    @Test
    fun `the pressure filter survives a restart and shapes the next reading`() {
        val file = File(temp.root, "filter.properties")
        val policy = DesktopSettingsState(DesktopSettings(file))
        assertEquals("full width at the default", 0.5f, PressureFeel.shape(0.5f), 1e-6f)

        policy.chooseMinimumPressure(0.4f)
        assertEquals("a light press is floored", 0.4f, PressureFeel.shape(0.05f), 1e-6f)

        policy.choosePressureMultiplier(2f)
        // No upper clamp, exactly as desktop Xournal++ allows: a multiplier above 1 may push a stroke
        // past its nominal width.
        assertEquals("and a firm one is multiplied", 1.2f, PressureFeel.shape(0.6f), 1e-6f)

        policy.choosePressureEnabled(false)
        assertEquals("sensitivity off draws at the tool's width", 1f, PressureFeel.shape(0.2f), 1e-6f)

        val reopened = DesktopSettings(file)
        assertFalse(reopened.pressureEnabled)
        assertEquals(0.4f, reopened.minimumPressure, 1e-6f)
        assertEquals(2f, reopened.pressureMultiplier, 1e-6f)

        policy.chooseMinimumPressure(9f)
        assertEquals("clamped to the range the desktop offers", PressureCurve.MINIMUM_PRESSURE_MAX, policy.minimumPressure, 1e-6f)
    }

    /** The strips' arrangement: stored as the slots' names, and read back by both strips. */
    @Test
    fun `a reordered strip survives a restart`() {
        val file = File(temp.root, "order.properties")
        val policy = DesktopSettingsState(DesktopSettings(file))
        assertEquals(DESKTOP_TOOL_GROUPS.map { it.face }, policy.toolGroups.map { it.face })

        policy.moveToolSlot(1, -1)
        assertEquals(
            "the second slot took the first place",
            DESKTOP_TOOL_GROUPS[1].face,
            policy.toolGroups.first().face,
        )
        assertEquals(DESKTOP_TOOL_GROUPS.size, policy.toolGroups.size)
        assertEquals(
            "and it is written to the file",
            policy.toolGroups.map { it.face.name },
            DesktopSettings(file).toolOrder,
        )

        policy.resetToolOrder()
        assertEquals(DESKTOP_TOOL_GROUPS.map { it.face }, DesktopSettingsState(DesktopSettings(file)).toolGroups.map { it.face })
    }

    @After
    fun resetTheReadingCurve() {
        // The shape is a module-level seam, so a test that changed it must hand it back.
        PressureFeel.exponent = PressureCurve.EXPONENT_IDENTITY
        PressureFeel.enabled = true
        PressureFeel.multiplier = 1f
        PressureFeel.minimum = PressureCurve.MINIMUM_PRESSURE_DEFAULT
    }

    /** A first run: the window's documented defaults, and nothing invented. */
    @Test
    fun `a window with no file opens on the documented defaults`() {
        val fresh = store("fresh.properties")
        assertEquals(StripPlacement.LEFT, fresh.stripPlacement)
        assertEquals(RailSide.LEFT, fresh.stripPlacement.railSide)
        assertTrue("a fresh pen reads its pressure", fresh.pressureEnabled)
        assertEquals(PressureCurve.MINIMUM_PRESSURE_DEFAULT, fresh.minimumPressure, 1e-6f)
        assertEquals(1f, fresh.pressureMultiplier, 1e-6f)
        assertEquals(DesktopNewDocumentFormat.XOPP, fresh.newDocumentFormat)
        assertTrue("no arrangement is the factory order", fresh.toolOrder.isEmpty())
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
        assertEquals(StripPlacement.LEFT, StripPlacement.of("Rail"))
        assertEquals(StripPlacement.TOP, StripPlacement.of("Ribbon"))
        assertEquals("the old both-lanes answer keeps a strip it had", StripPlacement.TOP, StripPlacement.of("Both"))
        assertEquals(StripPlacement.TOP, StripPlacement.of("top"))

        val legacy = File(temp.root, "legacy.properties")
        legacy.writeText("interface.toolPlacement=Ribbon\n")
        assertEquals(StripPlacement.TOP, DesktopSettings(legacy).stripPlacement)

        // The two old rows together: a rail the reader had moved to the right stays on the right.
        val moved = File(temp.root, "moved.properties")
        moved.writeText("interface.toolPlacement=Side\ninterface.railSide=RIGHT\n")
        assertEquals(StripPlacement.RIGHT, DesktopSettings(moved).stripPlacement)
    }

    /** An unknown value is the default, never an exception: a hand-edited file still opens a window. */
    @Test
    fun `an unknown value falls back to the default`() {
        assertEquals(StripPlacement.LEFT, StripPlacement.of("Diagonal"))
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
