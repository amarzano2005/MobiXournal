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
import com.mobixournal.io.CanvasGeometry
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

    /** A vertical strip: a cell's own coordinates are how far down the board its page sits. */
    @Test
    fun `a vertical strip's chunk lands on the sheet its coordinate names`() {
        val geometry = geometry(CanvasMode.INFINITE_VERTICAL, listOf(0 to 0, 0 to 1, 0 to 2))
        val pages = listOf(
            chunk(stroke(10.0 to 100.0, 10.0 to 180.0, id = "a")),
            chunk(stroke(10.0 to 20.0, 10.0 to 60.0, id = "b")),
            chunk(stroke(10.0 to 20.0, 10.0 to 60.0, id = "c")),
        )

        val sliced = BoardSlicer.slice(pages, geometry)

        assertEquals("one sheet per sheet's worth of board", 3, sliced.size)
        for (page in sliced) {
            assertEquals("a strip's sheets are the paper's width", sheetWidth, page.width, 0.001)
            assertEquals(sheetHeight, page.height, 0.001)
        }
        // Cell (0, 0) is the board's first sheet; (0, 1) is 1000 pt down, which is the *second* sheet's
        // second band — the ink is moved onto the board by the tile size before it is placed.
        assertEquals(listOf(100.0, 180.0), ysOf(strokesOf(sliced[0]).single()))
        assertEquals(listOf(220.0, 260.0), ysOf(strokesOf(sliced[1]).single()))
        assertEquals(listOf(420.0, 460.0), ysOf(strokesOf(sliced[2]).single()))
        assertEquals(listOf("a", "b", "c"), sliced.flatMap { strokesOf(it) }.map { it.id })
    }

    /**
     * The manifest's order is not the board's: a chunk is **named** after its cell, so a document whose
     * parts were reordered still slices by coordinate. A slicer that trusted the index would put the
     * furthest ink on the first sheet.
     */
    @Test
    fun `the ink follows the coordinate and not the part's index`() {
        val geometry = geometry(CanvasMode.INFINITE_VERTICAL, listOf(0 to 1, 0 to 0))
        val pages = listOf(
            chunk(stroke(10.0 to 20.0, 10.0 to 60.0, id = "far")),
            chunk(stroke(10.0 to 100.0, 10.0 to 180.0, id = "near")),
        )

        val sliced = BoardSlicer.slice(pages, geometry)

        assertEquals("near", strokesOf(sliced[0]).single().id)
        assertEquals("far", strokesOf(sliced[1]).single().id)
    }

    /** A horizontal strip is cut the other way round: along x, with the paper height locked. */
    @Test
    fun `a horizontal strip is cut along x with its sheets the paper's height`() {
        val geometry = geometry(CanvasMode.INFINITE_HORIZONTAL, listOf(0 to 0, 1 to 0))
        val pages = listOf(
            chunk(stroke(100.0 to 10.0, 180.0 to 10.0, id = "left")),
            chunk(stroke(20.0 to 10.0, 60.0 to 10.0, id = "right")),
        )

        val sliced = BoardSlicer.slice(pages, geometry)

        assertEquals(3, sliced.size)
        for (page in sliced) assertEquals("a horizontal strip's sheets are the paper's height", sheetHeight, page.height, 0.001)
        assertEquals(listOf("left"), strokesOf(sliced[0]).map { it.id })
        assertEquals(listOf(100.0, 180.0), strokesOf(sliced[0]).single().points.map { it.x })
        assertTrue("the sheet between the two cells holds no ink", strokesOf(sliced[1]).isEmpty())
        // Cell (1, 0) is 1000 pt to the right, which is the third sheet's own band.
        assertEquals(listOf("right"), strokesOf(sliced[2]).map { it.id })
        assertEquals(listOf(20.0, 60.0), strokesOf(sliced[2]).single().points.map { it.x })
    }

    /**
     * The seam search can never find whitespace inside one long stroke, so a cut lands in it — and the
     * stroke still leaves whole: an element is placed by its **midpoint**, never divided into partial
     * point lists, which is what keeps a page from holding half a stroke.
     */
    @Test
    fun `one long stroke leaves on a single sheet rather than as two halves`() {
        val geometry = geometry(CanvasMode.INFINITE_VERTICAL, listOf(0 to 0))
        val long = stroke(10.0 to 0.0, 10.0 to 1200.0, id = "long")
        val sliced = BoardSlicer.slice(listOf(chunk(long)), geometry)

        val placed = sliced.flatMap { strokesOf(it) }
        assertEquals("the stroke is on exactly one sheet", 1, placed.size)
        assertEquals("and it is the whole stroke", 2, placed.single().points.size)
        assertEquals(listOf(0.0, 1200.0), ysOf(placed.single()))
    }

    /**
     * `canvas.json`'s origin is where document `(0, 0)` sits on the **printed sheet**: the margin page 1
     * begins with. So the first sheet covers the origin and the ink at `y = 600` lands on the second.
     */
    @Test
    fun `the origin offset is the margin the first sheet starts with`() {
        val geometry = geometry(CanvasMode.INFINITE_VERTICAL, listOf(0 to 0), offsetPt = 200.0)
        // Ink well below the first sheet's own cut, so the seam needs no searching: the first sheet runs
        // from the origin (0) up to 600 and the ink at 900 is on the second.
        val sliced = BoardSlicer.slice(listOf(chunk(stroke(10.0 to 900.0, 10.0 to 980.0, id = "a"))), geometry)

        assertEquals(2, sliced.size)
        assertTrue("the sheet the margin covers holds no ink", strokesOf(sliced[0]).isEmpty())
        assertEquals(listOf(300.0, 380.0), ysOf(strokesOf(sliced[1]).single()))
    }

    /** A free board is cut into a **grid**: columns of the paper's width, then sheets down each column. */
    @Test
    fun `a 2D board's cell is cut into the column its coordinate names`() {
        val geometry = geometry(CanvasMode.INFINITE_2D, listOf(0 to 0, 1 to 0))
        val pages = listOf(
            chunk(stroke(100.0 to 100.0, 180.0 to 100.0, id = "left")),
            chunk(stroke(20.0 to 100.0, 60.0 to 100.0, id = "right")),
        )

        val sliced = BoardSlicer.slice(pages, geometry)

        assertEquals(3, sliced.size)
        assertEquals(listOf("left"), strokesOf(sliced[0]).map { it.id })
        assertEquals(listOf("right"), strokesOf(sliced[2]).map { it.id })
        assertEquals("the far cell's ink is placed in its own column", listOf(20.0, 60.0), strokesOf(sliced[2]).single().points.map { it.x })
    }

    /**
     * The sheets are the **board's** paper and not a tile's. Chunks carry a background each, and one that
     * disagrees cannot rule the sheet its ink is cut onto: a board ruled across its tiles would otherwise
     * come back with a seam at every tile edge.
     */
    @Test
    fun `every sheet carries the board's own paper`() {
        val other = Background.Solid(0xFF112233.toInt(), "plain")
        val geometry = geometry(CanvasMode.INFINITE_VERTICAL, listOf(0 to 0, 0 to 1))
        val pages = listOf(
            chunk(stroke(10.0 to 100.0, 10.0 to 180.0)),
            chunk(stroke(10.0 to 20.0, 10.0 to 60.0), background = other),
        )

        val sliced = BoardSlicer.slice(pages, geometry)

        assertEquals(listOf(paper, paper), sliced.map { it.background })
    }

    /** An export slices a board and leaves the document alone: the `.mobix` keeps its chunks. */
    @Test
    fun `sheetDocument slices a board and passes everything else through`() {
        val geometry = geometry(CanvasMode.INFINITE_VERTICAL, listOf(0 to 0, 0 to 1))
        val ink = stroke(10.0 to 100.0, 10.0 to 180.0)
        val document = Document(
            pages = listOf(chunk(ink), chunk(stroke(10.0 to 20.0, 10.0 to 60.0))),
        )

        val exported = sheetDocument(document, geometry)

        assertEquals(2, exported.pages.size)
        assertEquals("the document is not the file being written", 2, document.pages.size)
        assertSame("and the ink it holds is the ink it always held", ink, strokesOf(document.pages[0]).single())
        assertEquals("a strip's sheet is cut at the paper's height", 800.0, exported.pages[0].height, 0.001)

        assertSame("no board, no slicing", document, sheetDocument(document, null))
    }
}
