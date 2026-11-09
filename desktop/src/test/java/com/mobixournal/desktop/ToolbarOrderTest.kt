package com.mobixournal.desktop

import com.mobixournal.desktop.settings.DesktopSettings
import com.mobixournal.desktop.settings.DesktopSettingsState
import com.mobixournal.desktop.settings.stripDropTarget

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The Toolbar section's drag: where a carried slot lands, and that a landing several places away is
 * written as one arrangement.
 *
 * The row's own gesture is Compose's; these are the two decisions it delegates — the pitch arithmetic
 * and the single write — so they can be checked without a window.
 */
class ToolbarOrderTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `a slot lands one place per row crossed`() {
        assertEquals(4, stripDropTarget(from = 2, offsetPx = 80f, rowHeightPx = 40, count = 8))
        assertEquals(2, stripDropTarget(from = 2, offsetPx = 19f, rowHeightPx = 40, count = 8))
    }

    @Test
    fun `the strip's ends stop the drag instead of wrapping it`() {
        assertEquals(0, stripDropTarget(from = 0, offsetPx = -200f, rowHeightPx = 40, count = 6))
        assertEquals(5, stripDropTarget(from = 5, offsetPx = 200f, rowHeightPx = 40, count = 6))
    }

    @Test
    fun `a drag that lands far away is one arrangement, stored`() {
        val file = File(temp.root, "toolbar-drag.properties")
        val policy = DesktopSettingsState(DesktopSettings(file))
        val first = policy.toolGroups.first().face

        policy.strips.moveSlotTo(from = 0, to = 3)

        assertEquals(first, policy.toolGroups[3].face)
        assertEquals(policy.toolGroups.map { it.face.name }, DesktopSettings(file).toolOrder)
    }

    @Test
    fun `a drop where it started changes nothing`() {
        val policy = DesktopSettingsState(DesktopSettings(File(temp.root, "toolbar-noop.properties")))
        val before = policy.toolGroups.map { it.face }

        policy.strips.moveSlotTo(from = 2, to = 2)

        assertEquals(before, policy.toolGroups.map { it.face })
        assertEquals(emptyList<String>(), DesktopSettings(File(temp.root, "toolbar-noop.properties")).toolOrder)
    }
}
