package com.mobixournal.render

import com.mobixournal.format.mobix.CanvasMode
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A board's way out: the sheets its tiles add up to.
 *
 * What is pinned here is the half of the export the slicer itself cannot know — **where the ink is**
 * (a chunk stores it cell-local, so an element has to be moved onto the board before anything is cut,
 * and by the *document's* tile size rather than this build's) and **which paper the sheets are** (the
 * board's, not a tile's). The cutting itself is [CanvasAutoSlicerTest]'s subject; these tests take the
 * cuts as given and check what lands on the sheets they produce.
 */
class BoardSlicerTest {

    private val paper = Background.Solid(0xFFFFF8E1.toInt(), "ruled")

    /** A tile far bigger than the sheet, so a cell is unambiguous about which sheet its ink lands on. */
    private val tile = 1000.0

    private val sheetWidth = 500.0
    private val sheetHeight = 800.0

    private fun stroke(vararg points: Pair<Double, Double>, id: String? = null): Stroke = Stroke(
        tool = Tool.PEN,
        color = 0xFF000000.toInt(),
        capStyle = "round",
        points = points.map { (x, y) -> StrokePoint(x, y, 2.0) },
        uniformWidth = true,
        id = id,
    )

    /** One chunk page: [elements] at the cell-local coordinates the format stores them at. */
    private fun chunk(vararg elements: Element, background: Background = paper): Page =
        Page(width = tile, height = tile, background = background, layers = listOf(Layer(elements.toList())))

    private fun geometry(
        mode: CanvasMode,
        coords: List<Pair<Int, Int>>,
        offsetPt: Double = 0.0,
    ): CanvasGeometry = CanvasGeometry(
        mode = mode,
        widthPt = sheetWidth,
        heightPt = sheetHeight,
        chunkSizePt = tile,
        chunkCoords = coords,
        originOffsetPt = offsetPt,
    )

    private fun strokesOf(page: Page): List<Stroke> = page.layers.flatMap { it.elements }.filterIsInstance<Stroke>()

    private fun ysOf(stroke: Stroke): List<Double> = stroke.points.map { it.y }

    private fun xsOf(stroke: Stroke): List<Double> = stroke.points.map { it.x }

    /** A vertical strip: all cells assemble onto one continuous page whose height covers the full strip. */
    @Test
    fun `a vertical strip's chunks assemble onto a single expanded page`() {
        val geometry = geometry(CanvasMode.INFINITE_VERTICAL, listOf(0 to 0, 0 to 1, 0 to 2))
        val pages = listOf(
            chunk(stroke(10.0 to 100.0, 10.0 to 180.0, id = "a")),
            chunk(stroke(10.0 to 20.0, 10.0 to 60.0, id = "b")),
            chunk(stroke(10.0 to 20.0, 10.0 to 60.0, id = "c")),
        )

        val sliced = BoardSlicer.slice(pages, geometry)

        assertEquals("assembled into one single page", 1, sliced.size)
        val page = sliced.single()
        assertEquals("a strip's page keeps the locked width", sheetWidth, page.width, 0.001)
        assertEquals("and expands to cover the furthest stroke", 2061.0, page.height, 0.001)
        val strokes = strokesOf(page)
        assertEquals(listOf("a", "b", "c"), strokes.map { it.id })
        assertEquals(listOf(100.0, 180.0), ysOf(strokes[0]))
        assertEquals(listOf(1020.0, 1060.0), ysOf(strokes[1]))
        assertEquals(listOf(2020.0, 2060.0), ysOf(strokes[2]))
    }

    /**
     * The manifest's order is not the board's: a chunk is **named** after its cell, so a document whose
     * parts were reordered still orders elements by coordinate on the single page.
     */
    @Test
    fun `the ink follows the coordinate and not the part's index`() {
        val geometry = geometry(CanvasMode.INFINITE_VERTICAL, listOf(0 to 1, 0 to 0))
        val pages = listOf(
            chunk(stroke(10.0 to 20.0, 10.0 to 60.0, id = "far")),
            chunk(stroke(10.0 to 100.0, 10.0 to 180.0, id = "near")),
        )

        val sliced = BoardSlicer.slice(pages, geometry)

        assertEquals(1, sliced.size)
        assertEquals(listOf("near", "far"), strokesOf(sliced.single()).map { it.id })
    }

    /** A horizontal strip expands along x onto a single page with its paper height locked. */
    @Test
    fun `a horizontal strip expands along x on a single page with the paper's height`() {
        val geometry = geometry(CanvasMode.INFINITE_HORIZONTAL, listOf(0 to 0, 1 to 0))
        val pages = listOf(
            chunk(stroke(100.0 to 10.0, 180.0 to 10.0, id = "left")),
            chunk(stroke(20.0 to 10.0, 60.0 to 10.0, id = "right")),
        )

        val sliced = BoardSlicer.slice(pages, geometry)

        assertEquals(1, sliced.size)
        val page = sliced.single()
        assertEquals("a horizontal strip's page keeps the paper's height", sheetHeight, page.height, 0.001)
        assertEquals("and expands along x to cover the right cell", 1061.0, page.width, 0.001)
        val strokes = strokesOf(page)
        assertEquals(listOf("left", "right"), strokes.map { it.id })
        assertEquals(listOf(100.0, 180.0), xsOf(strokes[0]))
        assertEquals(listOf(1020.0, 1060.0), xsOf(strokes[1]))
    }

    /** One long stroke stays whole on the single expanded page. */
    @Test
    fun `one long stroke leaves on a single sheet rather than as two halves`() {
        val geometry = geometry(CanvasMode.INFINITE_VERTICAL, listOf(0 to 0))
        val long = stroke(10.0 to 0.0, 10.0 to 1200.0, id = "long")
        val sliced = BoardSlicer.slice(listOf(chunk(long)), geometry)

        val placed = sliced.flatMap { strokesOf(it) }
        assertEquals("the stroke is on the single sheet", 1, placed.size)
        assertEquals("and it is the whole stroke", 2, placed.single().points.size)
        assertEquals(listOf(0.0, 1200.0), ysOf(placed.single()))
    }

    /**
     * `canvas.json`'s origin is where document `(0, 0)` sits on the printed sheet: the margin the single
     * sheet starts with.
     */
    @Test
    fun `the origin offset is the margin the first sheet starts with`() {
        val geometry = geometry(CanvasMode.INFINITE_VERTICAL, listOf(0 to 0), offsetPt = 200.0)
        val sliced = BoardSlicer.slice(listOf(chunk(stroke(10.0 to 900.0, 10.0 to 980.0, id = "a"))), geometry)

        assertEquals(1, sliced.size)
        assertEquals(listOf(1100.0, 1180.0), ysOf(strokesOf(sliced.single()).single()))
    }

    /** A 2D board assembles all cells onto a single expanded page covering both axes. */
    @Test
    fun `a 2D board's cells assemble onto a single expanded page`() {
        val geometry = geometry(CanvasMode.INFINITE_2D, listOf(0 to 0, 1 to 0))
        val pages = listOf(
            chunk(stroke(100.0 to 100.0, 180.0 to 100.0, id = "left")),
            chunk(stroke(20.0 to 100.0, 60.0 to 100.0, id = "right")),
        )

        val sliced = BoardSlicer.slice(pages, geometry)

        assertEquals(1, sliced.size)
        val strokes = strokesOf(sliced.single())
        assertEquals(listOf("left", "right"), strokes.map { it.id })
        assertEquals(listOf(100.0, 180.0), xsOf(strokes[0]))
        assertEquals(listOf(1020.0, 1060.0), xsOf(strokes[1]))
    }

    /** The single page carries the board's own paper. */
    @Test
    fun `every sheet carries the board's own paper`() {
        val other = Background.Solid(0xFF112233.toInt(), "plain")
        val geometry = geometry(CanvasMode.INFINITE_VERTICAL, listOf(0 to 0, 0 to 1))
        val pages = listOf(
            chunk(stroke(10.0 to 100.0, 10.0 to 180.0)),
            chunk(stroke(10.0 to 20.0, 10.0 to 60.0), background = other),
        )

        val sliced = BoardSlicer.slice(pages, geometry)

        assertEquals(listOf(paper), sliced.map { it.background })
    }

    /** An export assembles a board into a single page and leaves the source document alone. */
    @Test
    fun `sheetDocument slices a board and passes everything else through`() {
        val geometry = geometry(CanvasMode.INFINITE_VERTICAL, listOf(0 to 0, 0 to 1))
        val ink = stroke(10.0 to 100.0, 10.0 to 180.0)
        val document = Document(
            pages = listOf(chunk(ink), chunk(stroke(10.0 to 20.0, 10.0 to 60.0))),
        )

        val exported = sheetDocument(document, geometry)

        assertEquals("assembled into one single page", 1, exported.pages.size)
        assertEquals("the document is not the file being written", 2, document.pages.size)
        assertSame("and the ink it holds is the ink it always held", ink, strokesOf(document.pages[0]).single())
        assertEquals("the single page expands to cover the second chunk's ink", 1061.0, exported.pages[0].height, 0.001)

        assertSame("no board, no slicing", document, sheetDocument(document, null))
    }
}
