package com.mobixournal.desktop

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.scene.ComposeScenePointer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Density
import com.mobixournal.design.PenPalette
import com.mobixournal.format.mobix.SyntheticNotebook
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.TextElement
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The inspector lane: **open only while there is something to edit** (a placement asking its values, or an
 * element opened with the pencil), never for a selection alone — the page keeps that width — and with a
 * header that says *which* element is being edited rather than only which family.
 *
 * The regression this file exists for is one of presence: a lane that stood open on an empty selection was
 * 300 dp of labels about nothing, and one that appeared for a selection was width the sheet lost on every
 * pick. Both are a pixel here, and so is a header that cannot tell two plots apart.
 */
@OptIn(ExperimentalComposeUiApi::class)
class DesktopInspectorLaneTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun notebook(): File = temp.newFile("lane.mobix").also { file ->
        SyntheticNotebook.write(file, SyntheticNotebook.pages(2))
    }

    private fun shell(): DesktopShellState = DesktopShellState().also { it.open(notebook()) }

    /**
     * The lane's own column, just inside its right edge: past the cards' 12 dp padding, so the pixel is the
     * band's glass and not a card's tone — and it is the page's own pixel when no lane is drawn.
     */
    private val lanePixelX = 992
    private val lanePixelY = 320

    @Test
    fun `the lane is closed until there is something to edit`() {
        val state = shell()
        try {
            assertNotBand("nothing selected: the page keeps the width", render(state))

            // A selection alone is the bar's business, not the lane's.
            val tab = state.tabs.active!!
            tab.selection = PageSelection(rectRegion(80.0, 90.0, 260.0, 240.0), emptySet())
            assertNotBand("a selection does not open a column of labels", render(state))
        } finally {
            state.tabs.closeAll()
        }
    }

    @Test
    fun `the lane opens while an element is edited, and closes with it`() {
        val state = shell()
        try {
            val tab = state.tabs.active!!
            state.selectTool(DesktopTool.PLOT)
            state.placeOrAsk(tab, LivingSlot.PLOT, Bounds(100.0, 120.0, 340.0, 264.0))
            assertNearBand("the question owns the lane", render(state))

            state.confirmPlacement()
            assertNotBand("the question answered, the lane goes", render(state))

            state.runSelection(SelectionAction.EDIT)
            assertNearBand("the pencil opens it again", render(state))

            state.closeLivingEditor()
            assertNotBand("and Done closes it", render(state))
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
    fun `the graph preview draws the equation before it is placed`() {
        val state = shell()
        try {
            val tab = state.tabs.active!!
            state.selectTool(DesktopTool.PLOT)
            state.placeOrAsk(tab, LivingSlot.PLOT, Bounds(60.0, 60.0, 360.0, 240.0))
            state.pendingPlacement!!.formulas[0] = "sin(x)"

            val image = render(state)
            var paper = 0
            var curve = 0
            for (x in 712 until 988) {
                for (y in 60 until 260) {
                    val rgb = image.getRGB(x, y) and 0xFFFFFF
                    if (rgb == 0xFFFFFF) paper++
                    if (rgb <= 0x333333) curve++
                }
            }
            assertTrue("the preview is a sheet of paper in the lane ($paper px)", paper > 500)
            assertTrue("with the curve drawn on it ($curve px)", curve > 20)
        } finally {
            state.tabs.closeAll()
        }
    }

    @Test
    fun `a plot carries every equation the card was given`() {
        val state = shell()
        try {
            val tab = state.tabs.active!!
            state.selectTool(DesktopTool.PLOT)
            state.placeOrAsk(tab, LivingSlot.PLOT, Bounds(60.0, 60.0, 360.0, 240.0))
            val pending = state.pendingPlacement!!
            assertEquals("a fresh graph starts on the tablet's own formula", 1, pending.formulas.size)

            pending.formulas[0] = "x^2"
            pending.addFormula()
            pending.formulas[1] = "2*x"
            pending.addFormula()
            pending.formulas[2] = "   "
            assertTrue("an unanswered row does not stop the graph", pending.ready)
            assertTrue(
                "and the preview has something to draw",
                pending.plotIn(com.mobixournal.render.PlotBox(0.0, 0.0, 320.0, 140.0), 1.0) != null,
            )

            state.confirmPlacement()
            val plot = elements(tab).filterIsInstance<PlotElement>().single()
            assertEquals(
                "both equations are in the placed graph, the blank one dropped",
                listOf("x^2", "2*x"),
                plot.curves.map { it.source },
            )
        } finally {
            state.tabs.closeAll()
        }
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

    @Test
    fun `the lane closes when the selection leaves the element being edited`() {
        val state = shell()
        try {
            val tab = state.tabs.active!!
            state.selectTool(DesktopTool.PLOT)
            state.placeOrAsk(tab, LivingSlot.PLOT, Bounds(60.0, 60.0, 360.0, 240.0))
            state.confirmPlacement()
            state.runSelection(SelectionAction.EDIT)
            assertTrue(
                "the pencil opens an editor on the element it names",
                attachedLivingEditor(tab, state.editingLiving) != null,
            )

            tab.selection = null
            assertNull(
                "a selection that no longer names it cannot keep an editor open",
                attachedLivingEditor(tab, state.editingLiving),
            )
            assertNotBand("so the column goes with it", render(state))
        } finally {
            state.tabs.closeAll()
        }
    }

    @Test
    fun `the editor adds and removes equations`() {
        val plot: PlotElement = LivingPlacement.plot(
            bounds = Bounds(0.0, 0.0, 240.0, 160.0),
            colour = PenPalette.BLUE,
            strokeWidthPt = 1.5,
            formula = "sin(x)",
        )
        val two = plotWithEquation(plot, PenPalette.BLUE)
        assertEquals(
            "Add equation appends the tablet's default row",
            listOf("sin(x)", LivingPlacement.DEFAULT_PLOT_FORMULA),
            two.curves.map { it.source },
        )
        assertEquals("in the colour the card gave it", PenPalette.BLUE, two.curves[1].color)
        assertEquals("at the plot's own width", 1.5, two.curves[1].strokeWidth, 1e-9)
        assertNull("and the one-curve analysis is dropped", two.summaryText)

        assertEquals(
            "a removal drops only its own row",
            listOf(LivingPlacement.DEFAULT_PLOT_FORMULA),
            plotWithoutEquation(two, 0).curves.map { it.source },
        )
        assertEquals("the last equation stays", 1, plotWithoutEquation(plot, 0).curves.size)
    }

    @Test
    fun `the editor previews the graph it is editing`() {
        val state = shell()
        try {
            val tab = state.tabs.active!!
            state.selectTool(DesktopTool.PLOT)
            state.placeOrAsk(tab, LivingSlot.PLOT, Bounds(60.0, 60.0, 360.0, 240.0))
            state.confirmPlacement()
            state.runSelection(SelectionAction.EDIT)

            val image = render(state)
            var paper = 0
            var curve = 0
            for (x in 712 until 988) {
                for (y in 60 until 260) {
                    val rgb = image.getRGB(x, y) and 0xFFFFFF
                    if (rgb == 0xFFFFFF) paper++
                    if (rgb <= 0x333333) curve++
                }
            }
            assertTrue("the editor's graph card is paper ($paper px)", paper > 500)
            assertTrue("with the element's curve on it ($curve px)", curve > 20)
        } finally {
            state.tabs.closeAll()
        }
    }

    /**
     * A **text box** is one of the elements the lane edits: its face, its size and its ink.
     *
     * The pixel is the proof that the section builds at all — a card of chips, switches, a number and a
     * palette — and that a selection alone still opens nothing, exactly as it does for a plot.
     */
    @Test
    fun `a text box opens the lane, and its face is set there`() {
        val state = shell()
        try {
            val tab = state.tabs.active!!
            placeLivingElement(
                tab,
                TextElement("Sans Bold", 18.0, 120.0, 200.0, 0xFF112233.toInt(), "words", id = "txt_lane"),
            )
            assertNotBand("a selection alone opens no column", render(state))

            state.runSelection(SelectionAction.EDIT)
            assertNearBand("the box's family, size and ink are the lane's to set", render(state))

            state.commitLiving(textBox(tab).withItalic(true))

            assertEquals(
                "the italic the lane wrote is the file's own font description",
                "Sans Bold Italic",
                textBox(tab).font,
            )
        } finally {
            state.tabs.closeAll()
        }
    }

    /**
     * The panel follows the **text box**: opening the field opens it, committing closes both.
     *
     * The style of a box has to be settable while it is being written — that is the moment the user is
     * looking at it — so the lane comes up with the in-place field and goes with it, one gesture each way
     * and no hunting for the box afterwards. The pixels are the lane's own band, as in every test here.
     */
    @Test
    fun `the lane opens with the text box and closes with it`() {
        val state = shell()
        try {
            val tab = state.tabs.active!!
            assertNotBand("nothing being typed: no column", render(state))

            state.textBox.begin(tab, 120.0, 200.0, 595.0, 842.0, state.ink) { _, _ -> }
            state.textBox.change(TextFieldValue("typing", TextRange(6)))
            assertNearBand("the box's face and size are the lane's while typing", render(state))

            state.textBox.commit(tab) { _, _ -> }
            assertNotBand("the commit closes the box and the column with it", render(state))
        } finally {
            state.tabs.closeAll()
        }
    }

    /**
     * The lane's controls are built **on the element as it is now**, not on the one the lane opened with.
     *
     * This is the bug the scene is needed for: a page read is not snapshot state, so the cards kept the
     * element they were first composed with. Every control then wrote a copy of *that* one — so Bold
     * pressed twice stayed bold, the Italic after it dropped the bold, and the swatch last in the card
     * restored the original face. Each click here is a real pointer press at the control's own row, and
     * the button's answer is read back out of the page.
     */
    @Test
    fun `the lane's controls compose with each other`() {
        val state = shell()
        val scene = ImageComposeScene(1000, 700, Density(1f)) {
            DesktopShell(state = state, onOpenPicker = {})
        }
        try {
            val tab = state.tabs.active!!
            placeLivingElement(
                tab,
                TextElement("Sans", 18.0, 120.0, 200.0, 0xFF112233.toInt(), "words here", id = "txt_controls"),
            )
            state.runSelection(SelectionAction.EDIT)
            Snapshot.sendApplyNotifications()
            scene.render()

            click(scene, 750, 181)
            assertEquals("Bold is set", "Sans Bold", textBox(tab).font)
            click(scene, 750, 181)
            assertEquals("and the same switch takes it away again", "Sans", textBox(tab).font)

            click(scene, 750, 181)
            click(scene, 750, 213)
            assertEquals("the italic switch adds to the bold, not over it", "Sans Bold Italic", textBox(tab).font)

            click(scene, 850, 141)
            assertEquals("the family chip keeps the two styles", "Serif Bold Italic", textBox(tab).font)

            click(scene, 764, 399)
            assertEquals("the ink keeps the face", "Serif Bold Italic", textBox(tab).font)
            assertEquals("and is the swatch that was clicked", 0xFFFF0000.toInt(), textBox(tab).color)
            assertEquals("the size was never touched", 18.0, textBox(tab).size, 1e-9)
        } finally {
            scene.close()
            state.tabs.closeAll()
        }
    }

    /** One click at a window pixel: press and release, then a frame so the lane can rebuild. */
    private fun click(scene: ImageComposeScene, x: Int, y: Int) {
        val at = Offset(x.toFloat(), y.toFloat())
        scene.sendPointerEvent(
            eventType = PointerEventType.Press,
            pointers = listOf(ComposeScenePointer(PointerId(1), at, pressed = true, type = PointerType.Mouse, pressure = 1f)),
            buttons = PointerButtons(isPrimaryPressed = true),
            timeMillis = 1_000,
        )
        scene.sendPointerEvent(
            eventType = PointerEventType.Release,
            pointers = listOf(ComposeScenePointer(PointerId(1), at, pressed = false, type = PointerType.Mouse, pressure = 1f)),
            buttons = PointerButtons(),
            timeMillis = 1_032,
        )
        Snapshot.sendApplyNotifications()
        scene.render()
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
    private fun assertNearBand(message: String, image: BufferedImage) =
        assertTrue(message, nearBand(image.getRGB(lanePixelX, lanePixelY) and 0xFFFFFF))

    /** No lane: the column the lane would take is page or desk, not the band's glass. */
    private fun assertNotBand(message: String, image: BufferedImage) =
        assertTrue(message, !nearBand(image.getRGB(lanePixelX, lanePixelY) and 0xFFFFFF))

    private fun nearBand(actual: Int): Boolean {
        val expected = DesktopTheme.bandRgb
        return listOf(16, 8, 0).all { shift ->
            val want = (expected shr shift) and 0xFF
            val got = (actual shr shift) and 0xFF
            kotlin.math.abs(want - got) <= 3
        }
    }

    private fun elements(tab: DesktopTab): List<com.mobixournal.format.model.Element> =
        tab.document.page(tab.page).layers.flatMap { it.elements }

    private fun textBox(tab: DesktopTab): TextElement = elements(tab).filterIsInstance<TextElement>().single()
}
