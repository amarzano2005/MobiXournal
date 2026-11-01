package com.mobixournal.desktop

import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.io.DocumentLoader
import com.mobixournal.desktop.settings.DesktopSettings
import com.mobixournal.desktop.settings.glyph
import com.mobixournal.desktop.sheet.DesktopGuidePose
import com.mobixournal.desktop.sheet.guideSlotGlyph
import com.mobixournal.desktop.sheet.placedGuideOn
import com.mobixournal.desktop.shell.DesktopShellState
import com.mobixournal.desktop.tools.DESKTOP_TOOLS_THAT_ACT
import com.mobixournal.desktop.tools.DesktopTool
import com.mobixournal.desktop.tools.groupOf

import com.mobixournal.design.ToolGlyph
import com.mobixournal.format.mobix.SyntheticNotebook
import com.mobixournal.render.DrawingGuide
import com.mobixournal.render.GuideKind
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The window's **guides slot**: one slot with two instruments, and the *Off* row the tablet's menu has.
 *
 * A guide is view state on both shells — nothing about it reaches the document, only the stroke it ruled
 * does — so what this suite protects is exactly that: picking an instrument lays it on the tab and writes
 * no page edit, another page's ink is left alone, and putting it away hands back the tool the reader was
 * using instead of the pen by decree.
 */
class DesktopGuideChooserTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** A two-page notebook in a window with one tab open on it. */
    private fun shell(): DesktopShellState {
        val file: File = temp.newFile("guides.mobix").also { SyntheticNotebook.write(it, SyntheticNotebook.pages(2)) }
        val state = DesktopShellState(DesktopSettings(temp.newFile("settings.properties")))
        state.tabs.open(DocumentLoader.load(file))
        return state
    }

    @Test
    fun `the guides slot holds both instruments and they are one family`() {
        val slot = groupOf(DesktopTool.SETSQUARE)
        assertSame("both instruments are reached from the one slot", slot, groupOf(DesktopTool.COMPASS))
        assertTrue("the slot carries a chevron", slot!!.hasPicker)
        assertTrue("and its picker is the guides' own", slot.isGuides())
        assertTrue(DesktopTool.SETSQUARE in DESKTOP_TOOLS_THAT_ACT)
        assertTrue(DesktopTool.COMPASS in DESKTOP_TOOLS_THAT_ACT)
        assertFalse("a guide is not ink: the pen's packets must stand aside", DesktopTool.SETSQUARE.inksByPackets())
        assertFalse(DesktopTool.COMPASS.inksByPackets())
    }

    @Test
    fun `the slot's face is the instrument that is placed`() {
        assertEquals("an empty slot wears the setsquare", ToolGlyph.SETSQUARE, guideSlotGlyph(DesktopTool.SETSQUARE, null))
        assertEquals(
            "a compass on the page outlives the pick that laid it",
            ToolGlyph.COMPASS,
            guideSlotGlyph(DesktopTool.SETSQUARE, GuideKind.COMPASS),
        )
        assertEquals(ToolGlyph.COMPASS, guideSlotGlyph(DesktopTool.COMPASS, null))
        assertNull("every other slot draws its own glyph", guideSlotGlyph(DesktopTool.PEN, GuideKind.COMPASS))
    }

    @Test
    fun `picking an instrument lays it on the page and writes no edit`() {
        val state = shell()
        val tab = state.tabs.active!!
        try {
            val before = tab.document.page(0).layers.sumOf { it.elements.size }
            state.selectTool(DesktopTool.SETSQUARE)
            val pose = tab.guide
            assertNotNull("the pick is the placement", pose)
            assertEquals("on the page the tab shows", 0, pose!!.page)
            assertTrue(pose.guide is DrawingGuide.Setsquare)
            assertEquals(
                "a fresh setsquare is the shared default rather than a second opinion",
                DrawingGuide.DEFAULT_SIZE_PT,
                (pose.guide as DrawingGuide.Setsquare).size,
                1e-9,
            )
            assertEquals("a guide is view state: the page holds nothing new", before, tab.document.page(0).layers.sumOf { it.elements.size })
            assertFalse("and it is not an undo step", tab.canUndo)

            // The other member replaces it on the same page, never beside it.
            state.selectTool(DesktopTool.COMPASS)
            assertTrue(tab.guide!!.guide is DrawingGuide.Compass)
            assertEquals("still nothing new on the sheet", before, tab.document.page(0).layers.sumOf { it.elements.size })
        } finally {
            tab.document.close()
        }
    }

    @Test
    fun `off takes the instrument away and puts the tool before it back in hand`() {
        val state = shell()
        val tab = state.tabs.active!!
        try {
            state.selectTool(DesktopTool.RECTANGLE)
            state.selectTool(DesktopTool.SETSQUARE)
            assertNotNull(tab.guide)

            state.clearGuide()

            assertNull("the instrument is off the page", tab.guide)
            assertEquals(
                "the tool the reader was drawing with came back",
                DesktopTool.RECTANGLE,
                state.tools.current,
            )
            assertFalse("taking a guide away is not an edit either", tab.canUndo)
        } finally {
            tab.document.close()
        }
    }

    @Test
    fun `a guide is pinned to its page and leaves another page's ink alone`() {
        val pose = placedGuideOn(GuideKind.SETSQUARE, page = 0, centre = 200.0 to 200.0)!!
        // The fresh setsquare's long leg runs from (110, 245) to (290, 245): a point 5 pt above it is the
        // hand's to within GRAB_PT, so it comes back onto the edge.
        val (px, py) = pose.project(page = 0, x = 200.0, y = 250.0)
        assertEquals(200.0, px, 1e-9)
        assertEquals(245.0, py, 1e-9)
        assertEquals("another page has no guide", 200.0 to 250.0, pose.project(page = 1, x = 200.0, y = 250.0))
        val none: DesktopGuidePose? = null
        assertEquals("and a window with none changes nothing", 12.0 to 34.0, none.project(0, 12.0, 34.0))
    }

    @Test
    fun `a point beyond the guide's reach is the hand's own`() {
        val pose = placedGuideOn(GuideKind.COMPASS, page = 0, centre = 100.0 to 100.0)!!
        // The compass is opened to DEFAULT_RADIUS_PT about the centre; 40 pt out of that circle is far
        // beyond GRAB_PT, so the point is answered unchanged.
        val radius = (pose.guide as DrawingGuide.Compass).radius
        val near = pose.project(0, 100.0 + radius + 4.0, 100.0)
        assertEquals("a point near the circumference is pulled onto it", 100.0 + radius, near.first, 1e-9)
        assertEquals(100.0 to 100.0, pose.project(0, 100.0, 100.0))
    }
}
