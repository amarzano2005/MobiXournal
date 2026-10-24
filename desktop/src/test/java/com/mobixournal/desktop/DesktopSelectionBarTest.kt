package com.mobixournal.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntOffset
import com.mobixournal.format.mobix.SyntheticNotebook
import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.MathBlockElement
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.format.model.PlotCurve
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.PlotKind
import com.mobixournal.format.model.ShapeVertex
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.TableElement
import com.mobixournal.format.model.Tool
import com.mobixournal.render.Bounds
import com.mobixournal.render.ElementRef
import com.mobixournal.render.PageSelection
import com.mobixournal.render.rectRegion
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import androidx.compose.ui.input.key.Key

/**
 * The selection bar: what it offers, what each command does to a page, and where it sits.
 *
 * The invariant this suite exists for is the one the bar is judged on: **every living element that can be
 * edited must offer the pencil**. It walks the five families the model declares — a plot, a table, a
 * figure, a component, a formula — and fails if one of them does not answer [SelectionAction.EDIT], so a
 * family added later cannot ship without its editor being reachable from the selection.
 */
class DesktopSelectionBarTest {

    @get:Rule
    val temp = TemporaryFolder()

    // --- every living family offers the pencil ---------------------------------------------------

    @Test
    fun `every living family offers the edit pencil`() {
        for (sample in livingSamples()) {
            val tab = tabWith(sample)
            tab.selection = selectFirst(tab)
            val ui = selectionBarUi(tab, DesktopClipboard())
            assertTrue("${sample::class.simpleName} must offer Edit", ui.canEdit)
            assertTrue("Edit must be enabled", ui.enabled(SelectionAction.EDIT))
            assertTrue("the tooltip names the family", ui.editLabel.startsWith("Edit"))
            assertEquals(
                "the pencil names the family it opens",
                LivingFamily.of(sample),
                livingFamilyOf(sample),
            )
            tab.document.close()
        }
    }

    @Test
    fun `ink and a two-element selection have no pencil`() {
        val tab = tabWith(bar(20.0, 20.0))
        tab.selection = selectFirst(tab)
        assertFalse("a stroke is not a living element", selectionBarUi(tab, DesktopClipboard()).canEdit)
        tab.document.editPage(0) { page -> page.withElements(listOf(bar(40.0, 40.0))) }
        // Two strokes now: the fixture's own first element and the one just added.
        tab.selection = PageSelection(
            rectRegion(0.0, 0.0, 400.0, 400.0),
            setOf(ElementRef(0, 0), ElementRef(0, 1)),
        )
        val ui = selectionBarUi(tab, DesktopClipboard())
        assertFalse("two elements are not one editor", ui.canEdit)
        assertTrue("but they can be aligned", ui.canAlign)
        assertFalse("and two cannot be distributed", ui.canDistribute)
        tab.document.close()
    }

    // --- the clipboard --------------------------------------------------------------------------

    @Test
    fun `cut takes the elements and leaves the page empty, in one undo step`() {
        val tab = tabWith(bar(20.0, 20.0))
        tab.selection = selectFirst(tab)
        val clipboard = DesktopClipboard()
        assertTrue(cutSelection(tab, clipboard))
        assertEquals("the page lost the stroke", 0, elements(tab).size)
        assertEquals("the clipboard has it", 1, clipboard.elements.size)
        assertTrue("the cut is undoable", tab.undo())
        assertEquals("undo brings it back", 1, elements(tab).size)
        tab.document.close()
    }

    @Test
    fun `paste lands beside what was copied and selects the copy`() {
        val tab = tabWith(bar(20.0, 20.0))
        tab.selection = selectFirst(tab)
        val clipboard = DesktopClipboard()
        assertTrue(copySelection(tab, clipboard))
        tab.selection = null
        assertTrue("paste is available with a clipboard and no selection", selectionBarUi(tab, clipboard).hasClipboard)
        assertTrue(pasteClipboard(tab, clipboard))
        val pasted = elements(tab).last() as Stroke
        val before = (clipboard.elements.single() as Stroke).points.first()
        assertEquals(before.x + PASTE_OFFSET_PT, pasted.points.first().x, 1e-6)
        assertEquals(before.y + PASTE_OFFSET_PT, pasted.points.first().y, 1e-6)
        assertEquals("the pasted copy is what stays selected", 1, tab.selection?.refs?.size)
        assertTrue("and it is the pasted one", tab.selection!!.refs.contains(ElementRef(0, 1)))
        tab.document.close()
    }

    @Test
    fun `duplicate leaves the copy selected, not the original`() {
        val tab = tabWith(bar(20.0, 20.0))
        tab.selection = selectFirst(tab)
        assertTrue(duplicateSelection(tab))
        assertEquals(2, elements(tab).size)
        assertEquals(setOf(ElementRef(0, 1)), tab.selection!!.refs)
        assertTrue(tab.undo())
        assertEquals(1, elements(tab).size)
        tab.document.close()
    }

    @Test
    fun `recolour writes the strip's own ink as one step`() {
        val tab = tabWith(bar(20.0, 20.0))
        tab.selection = selectFirst(tab)
        assertTrue(restyleSelection(tab, colour = 0xFFCC0000.toInt()))
        assertEquals(0xFFCC0000.toInt(), (elements(tab).single() as Stroke).color)
        assertTrue(tab.undo())
        assertEquals(0xFF000000.toInt(), (elements(tab).single() as Stroke).color)
        tab.document.close()
    }

    @Test
    fun `align moves the second element onto the first, and nothing with one`() {
        val tab = tabWith(bar(20.0, 20.0))
        tab.selection = selectFirst(tab)
        assertFalse("one element has nothing to align against", alignSelection(tab, com.mobixournal.render.SelectionOps.SelectionAlign.LEFT))
        tab.document.editPage(0) { page -> page.only(listOf(bar(20.0, 60.0), bar(90.0, 60.0))) }
        tab.selection = PageSelection(
            rectRegion(0.0, 0.0, 400.0, 400.0),
            setOf(ElementRef(0, 0), ElementRef(0, 1)),
        )
        assertTrue(alignSelection(tab, com.mobixournal.render.SelectionOps.SelectionAlign.LEFT))
        val xs = elements(tab).filterIsInstance<Stroke>().map { it.points.first().x }
        assertEquals("both start at the leftmost edge", 1, xs.distinct().size)
        tab.document.close()
    }

    // --- the keys are the bar's own commands ----------------------------------------------------

    @Test
    fun `Ctrl C, X, V and D are the selection's clipboard commands`() {
        assertEquals(ShellCommand.COPY_SELECTION, shellCommand(Key.C, ctrl = true, shift = false))
        assertEquals(ShellCommand.CUT_SELECTION, shellCommand(Key.X, ctrl = true, shift = false))
        assertEquals(ShellCommand.PASTE_CLIPBOARD, shellCommand(Key.V, ctrl = true, shift = false))
        assertEquals(ShellCommand.DUPLICATE_SELECTION, shellCommand(Key.D, ctrl = true, shift = false))
        assertNull(shellCommand(Key.C, ctrl = false, shift = false))
    }

    // --- where it sits --------------------------------------------------------------------------

    @Test
    fun `the bar rides under the box, and above it when the page ends`() {
        val bounds = Bounds(100.0, 100.0, 200.0, 160.0)
        val canvas = Size(600f, 700f)
        val bar = Size(280f, 38f)
        val under = selectionBarOffset(bounds, pxPerPt = 1f, originPx = Offset.Zero, canvas = canvas, bar = bar, gapPx = 8f)
        assertEquals("centred on the box", ((100 + 200) / 2f - 140f).toInt(), under.x)
        assertEquals("just below it", 168, under.y)
        val low = Bounds(100.0, 600.0, 200.0, 690.0)
        val above = selectionBarOffset(low, pxPerPt = 1f, originPx = Offset.Zero, canvas = canvas, bar = bar, gapPx = 8f)
        assertEquals("lifted above the box", (600 - 8 - 38), above.y)
        val edge = Bounds(-100.0, 100.0, 50.0, 160.0)
        val clamped = selectionBarOffset(edge, pxPerPt = 1f, originPx = Offset.Zero, canvas = canvas, bar = bar, gapPx = 8f)
        assertEquals("never off the left edge", IntOffset(6, 168), clamped)
    }

    // --- helpers --------------------------------------------------------------------------------

    private fun livingSamples(): List<Element> = listOf(
        PlotElement(
            left = 10.0, top = 10.0, right = 110.0, bottom = 90.0,
            curves = listOf(PlotCurve(PlotKind.CARTESIAN, "x^2", 0xFF000000.toInt())),
        ),
        TableElement(left = 10.0, top = 10.0, colWidths = listOf(40.0), rowHeights = listOf(20.0)),
        ParametricShapeElement(
            shape = "triangle",
            vertices = listOf(ShapeVertex(10.0, 10.0), ShapeVertex(60.0, 10.0), ShapeVertex(30.0, 60.0)),
            strokeColor = 0xFF000000.toInt(),
            strokeWidth = 2.0,
        ),
        CircuitElement(symbol = "resistor", positionX = 40.0, positionY = 40.0),
        MathBlockElement(left = 10.0, top = 10.0, right = 110.0, bottom = 50.0, source = "x^2"),
    )

    private fun bar(x: Double, y: Double): Stroke = Stroke(
        points = listOf(StrokePoint(x, y, 4.0), StrokePoint(x + 60.0, y, 4.0)),
        color = 0xFF000000.toInt(),
        tool = Tool.PEN,
        capStyle = "round",
        uniformWidth = true,
    )

    private fun tabWith(element: Element): DesktopTab {
        val tab = DesktopTabs().open(DocumentLoader.load(notebook()))
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        tab.document.editPage(0) { page -> page.only(listOf(element)) }
        return tab
    }

    /** The selection a marquee would have made over the first element of the page's top layer. */
    private fun selectFirst(tab: DesktopTab): PageSelection {
        val box = com.mobixournal.render.ElementBounds.of(elements(tab).first())
        return PageSelection(
            rectRegion(box.left - 1, box.top - 1, box.right + 1, box.bottom + 1),
            setOf(ElementRef(0, 0)),
        )
    }

    private fun elements(tab: DesktopTab): List<Element> =
        tab.document.page(tab.page).layers.flatMap { it.elements }

    private fun Page.withElements(added: List<Element>): Page {
        val layers = layers.ifEmpty { listOf(Layer(emptyList())) }
        val top = layers.lastIndex
        val grown = layers.toMutableList().also {
            it[top] = Layer(it[top].elements + added, it[top].name)
        }
        return copy(layers = grown)
    }

    /** [page] holding exactly [elements] on one layer — a fixture page's own ink made predictable. */
    private fun Page.only(elements: List<Element>): Page = copy(layers = listOf(Layer(elements)))

    private var documents = 0

    private fun notebook(): File {
        val file = temp.newFile("bar-${documents++}.mobix")
        SyntheticNotebook.write(file, SyntheticNotebook.pages(1))
        return file
    }
}
