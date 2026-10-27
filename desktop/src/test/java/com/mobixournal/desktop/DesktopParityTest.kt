package com.mobixournal.desktop

import com.mobixournal.design.PenPalette
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.LineStyle
import com.mobixournal.render.BackgroundRuling
import com.mobixournal.render.BackgroundRulings
import com.mobixournal.render.LivingPlacement
import com.mobixournal.render.ShapeKind
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The window's **tablet parity**: what a new page is, what the page-setup surface writes, and which ink a
 * figure is drawn in.
 *
 * Three claims, each one the user asked for by name and each one a decision that could quietly stop being
 * true: the window starts on a **sheet** (and `+` starts another), the sheet can be restyled in the
 * file's own ruling parameter, and a figure is drawn in the **pen's** ink whatever tool is in hand — a
 * highlighter never turns a rectangle yellow.
 */
class DesktopParityTest {

    @get:Rule
    val temp = TemporaryFolder()

    private var settingsCount = 0

    private fun state(): DesktopShellState = DesktopShellState(
        settings = DesktopSettings(temp.newFile("parity-${settingsCount++}.properties")),
        recovery = DesktopRecovery(temp.newFolder()),
    )

    @Test
    fun `a window with nothing to recover starts on a page to write on`() {
        val state = state()
        state.start(initial = null)

        val tab = state.active
        assertNotNull("the default screen is a document, not an empty notice", tab)
        assertEquals("with one sheet", 1, tab!!.pageCount)
        assertTrue("and it is a new document waiting for a name", tab.isDirty)
        val page = state.activePage()!!
        assertEquals(DesktopDocuments.PAGE_WIDTH_PT, page.width, 0.01)
        assertEquals(DesktopDocuments.PAGE_HEIGHT_PT, page.height, 0.01)
        assertEquals(1, page.layers.size)
        assertEquals("the tablet's blank sheet is graph ruled", "graph", (page.background as Background.Solid).style)
    }

    @Test
    fun `the plus starts another page beside the one that is open`() {
        val state = state()
        state.start(initial = null)
        val first = state.active!!.key

        state.newDocument()

        assertEquals("two tabs", 2, state.tabs.size)
        assertNotEquals("the second is a document of its own", first, state.active!!.key)
        assertTrue("and it is dirty, so Save asks where to put it", state.active!!.isDirty)
    }

    @Test
    fun `the page setup restyles the sheet as one undoable edit each`() {
        val state = state()
        state.start(initial = null)

        state.setPageStyle("dotted")
        assertEquals("dotted", (state.activePage()!!.background as Background.Solid).style)

        state.setPageSpacingMm(7.0)
        val solid = state.activePage()!!.background as Background.Solid
        val ruling = BackgroundRuling.parse(solid.config)
        assertEquals(
            "the spacing is written in desktop's own parameter",
            7.0 * PageSetup.MM_PT,
            BackgroundRulings.spacingPt(solid.style, ruling),
            0.01,
        )

        state.setPageSize(612.0, 792.0)
        assertEquals(612.0, state.activePage()!!.width, 0.01)
        assertEquals(792.0, state.activePage()!!.height, 0.01)

        // One step per change, and each one comes back.
        repeat(3) { assertTrue(state.apply(ShellCommand.UNDO)) }
        val restored = state.activePage()!!
        assertEquals(DesktopDocuments.PAGE_WIDTH_PT, restored.width, 0.01)
        assertEquals("graph", (restored.background as Background.Solid).style)
    }

    @Test
    fun `a figure is drawn in the pen's ink even while the highlighter is picked`() {
        val state = state()
        state.start(initial = null)

        state.selectTool(DesktopTool.PEN)
        state.pickColour(PenPalette.GREEN)
        state.pickWidth(PenPalette.DEFAULT_WIDTHS[0])
        val penInk = state.ink

        state.selectTool(DesktopTool.HIGHLIGHTER)
        val markerInk = state.ink
        assertNotEquals("the tools write with different inks", penInk.colour, markerInk.colour)
        assertEquals(
            "a figure takes the pen's ink, not the picked tool's",
            penInk.colour,
            state.shapeInk.colour,
        )

        // A figure slot's own colour change is a pen change, so the two stay correlated.
        state.selectTool(DesktopTool.RECTANGLE)
        state.pickColour(PenPalette.RED)
        assertEquals(PenPalette.RED, state.shapeInk.colour)
        state.selectTool(DesktopTool.HIGHLIGHTER)
        assertEquals(
            "and the marker kept its own",
            markerInk.colour,
            state.ink.colour,
        )
    }

    @Test
    fun `each tool keeps its own stroke pattern across a restart`() {
        val file = temp.newFile("patterns.properties")
        val state = DesktopShellState(settings = DesktopSettings(file))

        state.selectTool(DesktopTool.PEN)
        state.pickLineStyle(LineStyle.DASHED)
        state.selectTool(DesktopTool.HIGHLIGHTER)
        state.pickLineStyle(LineStyle.DOTTED)

        val reopened = DesktopSettings(file)
        assertEquals(LineStyle.DASHED, reopened.inkLineStyle)
        assertEquals(LineStyle.DOTTED, reopened.highlighterLineStyle)

        // The restart comes back on the highlighter (`interface.pickedTool`), so the pen's entry is read
        // by picking the pen — the question here is that each tool kept its own pattern, not which tool
        // the window opened on.
        val next = DesktopShellState(settings = reopened)
        assertEquals(DesktopTool.HIGHLIGHTER, next.tools.current)
        next.selectTool(DesktopTool.PEN)
        assertEquals(LineStyle.DASHED, next.ink.lineStyle)
        next.selectTool(DesktopTool.HIGHLIGHTER)
        assertEquals(LineStyle.DOTTED, next.ink.lineStyle)
    }

    /**
     * Pins are marked for the tools that can join them: the Line tool and a circuit family. Every other
     * figure marks nothing, exactly as the tablet draws nothing for it, so a rectangle never scatters
     * rings over the sheet.
     */
    @Test
    fun `only a connector marks the pins it could join`() {
        val circuit = LivingPlacement.DEFAULT_CIRCUIT_KIND
        assertEquals(ShapeKind.LINE, connectorKindFor(DesktopTool.LINE, circuit))
        assertEquals(
            "a circuit slot marks the pins of the part it is about to place",
            circuit,
            connectorKindFor(DesktopTool.CIRCUIT, circuit),
        )
        assertNull("a rectangle joins nothing", connectorKindFor(DesktopTool.RECTANGLE, circuit))
        assertNull("the pen joins nothing", connectorKindFor(DesktopTool.PEN, circuit))
    }

    /** The stroke pattern a pen writes with is the ink's, as the shared palette names it. */
    @Test
    fun `a picked pattern is the pattern the stroke carries`() {
        assertEquals(LineStyle.PLAIN, DesktopInk().lineStyle)
        assertEquals(LineStyle.DASH_DOT, DesktopInk().withLineStyle(LineStyle.DASH_DOT).lineStyle)

        // A document's file name for a new page is unique and outside the tabs already open.
        val first = DesktopDocuments.newPath(emptySet())
        assertTrue("a new page is a .xopp waiting for its name", first.name.endsWith(".xopp"))
        assertNotEquals(
            "a new page never reuses an open name",
            first.name,
            DesktopDocuments.newPath(setOf(DesktopTabs.keyOf(first))).name,
        )
    }
}
