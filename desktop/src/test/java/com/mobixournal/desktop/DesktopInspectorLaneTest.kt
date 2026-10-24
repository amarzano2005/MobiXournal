package com.mobixournal.desktop

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.mobixournal.design.PenPalette
import com.mobixournal.format.mobix.SyntheticNotebook
import com.mobixournal.format.model.PlotElement
import com.mobixournal.render.Bounds
import com.mobixournal.render.ElementRef
import com.mobixournal.render.LivingPlacement
import com.mobixournal.render.LivingSlot
import com.mobixournal.render.PageSelection
import com.mobixournal.render.rectRegion
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The inspector lane as a **stable frame**: it is there with nothing selected, the same width in every
 * state, and its header says *which* element is being edited rather than only which family.
 *
 * The regression this file exists for is a layout one: the lane used to return early when no editor was
 * open, so picking an element grew the right-hand column and closing it shrank the column back — the page
 * jumped sideways under the pointer. A width that depends on the selection is therefore a failing test, and
 * so is a header that cannot tell two plots apart.
 */
@OptIn(ExperimentalComposeUiApi::class)
class DesktopInspectorLaneTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun notebook(): File = temp.newFile("lane.mobix").also { file ->
        SyntheticNotebook.write(file, SyntheticNotebook.pages(2))
    }

    private fun shell(): DesktopShellState = DesktopShellState().also { it.open(notebook()) }

    /** The lane's own column, well clear of its 12 dp padding: a pixel of band glass, not of the desk. */
    private val lanePixelX = 960
    private val lanePixelY = 320

    @Test
    fun `the lane is drawn with nothing selected`() {
        val state = shell()
        try {
            val pixel = render(state).getRGB(lanePixelX, lanePixelY) and 0xFFFFFF
            assertNearBand("the lane's column is band glass, not the desk", pixel)
        } finally {
            state.tabs.closeAll()
        }
    }

    @Test
    fun `the lane keeps its width when a selection arrives and when it leaves`() {
        val state = shell()
        try {
            val empty = render(state).getRGB(lanePixelX, lanePixelY) and 0xFFFFFF

            val tab = state.tabs.active!!
            tab.selection = PageSelection(rectRegion(80.0, 90.0, 260.0, 240.0), emptySet())
            val selected = render(state).getRGB(lanePixelX, lanePixelY) and 0xFFFFFF

            tab.selection = null
            val again = render(state).getRGB(lanePixelX, lanePixelY) and 0xFFFFFF

            assertNearBand("nothing selected", empty)
            assertNearBand("something selected", selected)
            assertEquals("the column never moves", empty, selected)
            assertEquals("and coming back is the same frame", empty, again)
        } finally {
            state.tabs.closeAll()
        }
    }

    @Test
    fun `the header names the element, not only its family`() {
        val plot: PlotElement = LivingPlacement.plot(
            bounds = Bounds(0.0, 0.0, 240.0, 160.0),
            colour = PenPalette.BLUE,
            strokeWidthPt = 1.5,
            formula = "sin(x)",
            xMin = -6.0,
            xMax = 6.0,
        )
        val summary = livingSummary(LivingTarget(LivingFamily.PLOT, ElementRef(0, 0), plot))
        assertTrue("the formula is in the summary: $summary", summary.contains("sin(x)"))
        assertTrue("and the range: $summary", summary.contains("-6") && summary.contains("6"))

        val table = LivingPlacement.table(Bounds(0.0, 0.0, 200.0, 120.0))
        val tableSummary = livingSummary(LivingTarget(LivingFamily.TABLE, ElementRef(0, 0), table))
        assertEquals(
            "a table's summary is its grid, and its header if it has one",
            "${table.colWidths.size} × ${table.rowHeights.size}" + if (table.headerRow) " · header" else "",
            tableSummary,
        )
    }

    @Test
    fun `a placement hands the window to the selection tool`() {
        val state = shell()
        try {
            val tab = state.tabs.active!!
            state.selectTool(DesktopTool.PLOT)
            state.placeOrAsk(tab, LivingSlot.TABLE, Bounds(60.0, 60.0, 300.0, 220.0))
            state.confirmPlacement()

            assertEquals(
                "the next drag must move the table, not place another one",
                DesktopTool.MARQUEE,
                state.tools.current,
            )
        } finally {
            state.tabs.closeAll()
        }
    }

    // --- rendering ------------------------------------------------------------------------------

    private fun render(state: DesktopShellState): BufferedImage {
        val scene = ImageComposeScene(1000, 700, Density(1f)) {
            DesktopShell(state = state, onOpenPicker = {})
        }
        return try {
            Snapshot.sendApplyNotifications()
            val png = requireNotNull(scene.render().encodeToData()) { "Skia produced no PNG" }.bytes
            ImageIO.read(ByteArrayInputStream(png))
        } finally {
            scene.close()
        }
    }

    /** A pixel of the band: the glass at its token alpha over the desk, within a rounding step. */
    private fun assertNearBand(message: String, actual: Int) {
        val expected = DesktopTheme.bandRgb
        for (shift in listOf(16, 8, 0)) {
            val want = (expected shr shift) and 0xFF
            val got = (actual shr shift) and 0xFF
            assertTrue("$message: expected ~$want at >>$shift, got $got", kotlin.math.abs(want - got) <= 3)
        }
    }
}
