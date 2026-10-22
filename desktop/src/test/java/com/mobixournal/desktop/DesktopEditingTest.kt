package com.mobixournal.desktop

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import com.mobixournal.format.SaveFormat
import com.mobixournal.format.mobix.CountingEntrySource
import com.mobixournal.format.mobix.MobixEntrySource
import com.mobixournal.format.mobix.SyntheticNotebook
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import com.mobixournal.render.EraserMode
import com.mobixournal.render.InkPointer
import com.mobixournal.render.InkPointerPhase
import com.mobixournal.render.PageEraser
import com.mobixournal.render.PointerKind
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * One stylus sample, as Compose's pointer input reports it — through the public constructor: the one
 * that carries a pressure reading is internal to Compose, so the ramp itself is asserted on
 * [strokeOf] (where a sample's pressure lives) rather than re-derived here.
 */
private fun pen(
    x: Float,
    y: Float,
    pressed: Boolean,
    time: Long,
) = androidx.compose.ui.input.pointer.PointerInputChange(
    id = androidx.compose.ui.input.pointer.PointerId(1),
    uptimeMillis = time,
    position = androidx.compose.ui.geometry.Offset(x, y),
    pressed = pressed,
    previousUptimeMillis = time - 1,
    previousPosition = androidx.compose.ui.geometry.Offset(x, y),
    previousPressed = pressed,
    isInitiallyConsumed = false,
    type = androidx.compose.ui.input.pointer.PointerType.Stylus,
    scrollDelta = androidx.compose.ui.geometry.Offset.Zero,
)

/**
 * The window's editing path, end to end and headless: a `.mobix` is opened page by page, one page is
 * edited **through the page window**, and the assertions are the three that matter —
 *
 *  - the page comes back edited, and it is the page that is painted (pixels, not just the model);
 *  - no other page entry was inflated (`CountingEntrySource`, the same instrument the core's suites use);
 *  - a save writes the edit and still copies the pages nobody touched.
 *
 * The pen and the eraser are the last screen of that path: a pressure sample becomes the width
 * `PressureCurve` says, `PageEraser` splits a stroke where the rubber crosses it, and the model that
 * comes out is what the next save writes (`docs/desktop-workstation.md`).
 */
class DesktopEditingTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val pageCount = 40

    /** The archive every test opens: [pageCount] pages, one thin stroke each. */
    private fun notebook(): File = temp.newFile("desktop-editing.mobix").also { file ->
        SyntheticNotebook.write(file, SyntheticNotebook.pages(pageCount))
    }

    /** The page entries the reader has inflated so far. */
    private fun openedPages(counted: CountingEntrySource): Set<Int> =
        (0 until pageCount).filter { counted.openCount(SyntheticNotebook.entryOf(it)) > 0 }.toSet()

    private fun strokeCount(page: Page): Int =
        page.layers.sumOf { layer -> layer.elements.count { element -> element is Stroke } }

    /** A thick stroke across the sheet, so the page's picture visibly gains ink. */
    private fun diagonal(id: String): Stroke = Stroke(
        tool = Tool.PEN,
        color = 0xFF000000.toInt(),
        capStyle = "round",
        points = listOf(StrokePoint(20.0, 20.0, 8.0), StrokePoint(560.0, 800.0, 8.0)),
        uniformWidth = true,
        id = id,
    )

    private fun horizontalBar(id: String): Stroke = Stroke(
        tool = Tool.PEN,
        color = 0xFF000000.toInt(),
        capStyle = "round",
        points = listOf(StrokePoint(60.0, 200.0, 8.0), StrokePoint(520.0, 200.0, 8.0)),
        uniformWidth = true,
        id = id,
    )

    /** How many sampled pixels are ink-dark — the render test's own measure, on a denser grid. */
    private fun inkPixels(bitmap: ImageBitmap): Int {
        val pixels = bitmap.toPixelMap()
        var ink = 0
        var y = 0
        while (y < pixels.height) {
            var x = 0
            while (x < pixels.width) {
                if (isInk(pixels[x, y].red, pixels[x, y].green, pixels[x, y].blue)) ink++
                x += 2
            }
            y += 2
        }
        return ink
    }

    private fun isInk(red: Float, green: Float, blue: Float): Boolean =
        maxOf(red, green, blue) < 0.3f

    @Test
    fun editsPageThreeThroughTheWindowWithoutInflatingTheRest() {
        val file = notebook()
        val counted = CountingEntrySource(MobixEntrySource.fromFile(file))
        val document = DocumentLoader.openMobix(file, counted)
        try {
            assertEquals("opening a notebook reads no page at all", emptySet<Int>(), openedPages(counted))
            val before = inkPixels(renderPageBitmap(document.page(3), 1f))
            assertEquals("the fixture's page holds one stroke", 1, strokeCount(document.page(3)))

            val edited = document.editPage(3) { page -> page.withElement(diagonal("stk_desktop")) }

            assertNotNull("the edit landed", edited)
            assertEquals("the edit inflated its own page and no other", setOf(3), openedPages(counted))
            assertEquals("the painted page carries both strokes", 2, strokeCount(document.page(3)))
            assertEquals("the model the save writes carries it too", 2, strokeCount(document.model.pages[3]))
            assertEquals("one edit was recorded", 1, document.edits)
            assertEquals(
                "a page nobody touched is still the source's own",
                4.0,
                (document.page(4).layers.single().elements.single() as Stroke).points.first().x,
                0.0,
            )

            val after = inkPixels(renderPageBitmap(document.page(3), 1f))
            assertTrue("the drawn stroke is on the page that is painted ($before -> $after)", after > before + 100)
        } finally {
            document.close()
        }
    }

    @Test
    fun aSaveWritesTheEditAndCopiesThePagesItDidNotTouch() {
        val file = notebook()
        val document = DocumentLoader.load(file)
        val target = File(temp.root, "desktop-edited.mobix")
        try {
            document.editPage(3) { page -> page.withElement(diagonal("stk_desktop")) }
            DesktopSaves.save(document, target, SaveFormat.MOBIX)
        } finally {
            document.close()
        }

        val reopened = DocumentLoader.load(target)
        try {
            assertEquals("the container the tab had is the container it wrote", DesktopContainer.MOBIX, reopened.container)
            assertEquals("every page travelled", pageCount, reopened.pageCount)
            assertEquals("page 3 carries the edit", 2, strokeCount(reopened.page(3)))
            assertTrue(
                "and it is the stroke the window drew",
                reopened.page(3).layers.flatMap { it.elements }
                    .filterIsInstance<Stroke>()
                    .any { stroke -> stroke.id == "stk_desktop" },
            )
            assertEquals(
                "an untouched page still holds its own ink",
                4.0,
                (reopened.page(4).layers.single().elements.single() as Stroke).points.first().x,
                0.0,
            )
        } finally {
            reopened.close()
        }
    }

    @Test
    fun theEraserSplitsAStrokeWhereItCrossesIt() {
        val document = DocumentLoader.load(notebook())
        try {
            document.editPage(0) { page -> page.withElement(horizontalBar("stk_bar")) }
            val before = renderPageBitmap(document.page(0), 1f).toPixelMap()
            assertTrue(
                "the bar is inked at its middle",
                isInk(before[250, 200].red, before[250, 200].green, before[250, 200].blue),
            )

            val erased = document.editPage(0) { page ->
                PageEraser.erase(page, px = 250.0, py = 200.0, radius = 20.0, mode = EraserMode.STANDARD) ?: page
            }

            assertNotNull("the eraser touched the page", erased)
            val strokes = document.page(0).layers.flatMap { it.elements }.filterIsInstance<Stroke>()
            assertEquals("the bar became two pieces beside the fixture's stroke", 3, strokes.size)
            assertEquals("two edits were recorded", 2, document.edits)

            val after = renderPageBitmap(document.page(0), 1f).toPixelMap()
            assertFalse("the gap is paper again", isInk(after[250, 200].red, after[250, 200].green, after[250, 200].blue))
            assertTrue(
                "both halves are still inked",
                isInk(after[100, 200].red, after[100, 200].green, after[100, 200].blue) &&
                    isInk(after[400, 200].red, after[400, 200].green, after[400, 200].blue),
            )
        } finally {
            document.close()
        }
    }

    /**
     * The gesture machine itself: a pen drag on the page becomes one stroke, at the widths the shared
     * pressure filter gives each sample, and nothing is committed until the lift.
     */
    @Test
    fun aPenDragInksThePageOnceOnLift() {
        val tab = DesktopTabs().open(DocumentLoader.load(notebook()))
        try {
            val draft = mutableStateListOf<InkSample>()
            val gesture = PageInkGesture(
                tab = tab,
                tool = DesktopTool.PEN,
                ink = DesktopInk(width = 4f),
                view = Viewport(zoom = 1f),
                pageW = SyntheticNotebook.WIDTH_PT.toFloat(),
                pageH = SyntheticNotebook.HEIGHT_PT.toFloat(),
                density = 1f,
                draft = draft,
                onEdited = {},
            )

            gesture.down(pen(100f, 100f, pressed = true, time = 1_000L), InkPointer.PRIMARY)
            gesture.move(pen(104f, 100f, pressed = true, time = 1_016L), InkPointer.PRIMARY)
            assertFalse("a wobble inside the tap slop is not a stroke", gesture.drawing)
            assertTrue(draft.isEmpty())

            gesture.move(pen(160f, 120f, pressed = true, time = 1_032L), InkPointer.PRIMARY)
            assertTrue("crossing the slop starts the stroke at the press", gesture.drawing)
            assertEquals(2, draft.size)

            gesture.move(pen(260f, 160f, pressed = true, time = 1_048L), InkPointer.PRIMARY)
            assertTrue("the draft is drawn before the lift", draft.isNotEmpty())
            assertEquals("nothing is on the page yet", 1, strokeCount(tab.document.page(0)))

            gesture.up(pen(260f, 160f, pressed = false, time = 1_064L), 0)
            assertFalse(gesture.drawing)
            assertTrue("the draft is handed over", draft.isEmpty())

            val strokes = tab.document.page(0).layers.flatMap { it.elements }.filterIsInstance<Stroke>()
            assertEquals("the lift committed exactly one stroke", 2, strokes.size)
            val drawn = strokes.last()
            assertEquals("the press, the slop crossing and the last sample", 3, drawn.points.size)
            assertEquals("the samples are page points, not view pixels", 100.0, drawn.points[0].x, 1e-5)
            assertEquals(260.0, drawn.points[2].x, 1e-5)
            assertEquals("a full-pressure sample draws at the tool's width", 4.0, drawn.points[0].width, 1e-5)
            assertEquals("the stroke's own tool and ink", Tool.PEN, drawn.tool)
            assertEquals("one edit was recorded", 1, tab.document.edits)
        } finally {
            tab.document.close()
        }
    }

    @Test
    fun aPressureSampleBecomesItsFilteredWidthAndAHighlighterStaysOneWidth() {
        val pointer = InkPointer(
            id = 1,
            x = 0f,
            y = 0f,
            pressure = 0.5f,
            timeMs = 1000L,
            tool = PointerKind.UNKNOWN,
            phase = InkPointerPhase.MOVE,
        )
        val samples = listOf(
            InkSample(1.0, 1.0, 0.5f, pointer),
            InkSample(2.0, 2.0, 1f, pointer.copy(timeMs = 1032L)),
        )

        val pen = strokeOf(Tool.PEN, 0xFF000000.toInt(), 4f, samples)!!
        assertEquals("the pen keeps each sample's filtered width", 2.0, pen.points[0].width, 1e-6)
        assertEquals(4.0, pen.points[1].width, 1e-6)
        assertEquals("the telemetry records the time since the stroke began", 32, pen.points[1].dtMs)
        assertFalse("a pen taper is per vertex", pen.uniformWidth)

        val highlighter = strokeOf(Tool.HIGHLIGHTER, 0xFFFFFF00.toInt(), 12f, samples)!!
        assertTrue("a highlighter band has one width", highlighter.uniformWidth)
        assertEquals(12.0, highlighter.points[0].width, 0.0)

        assertNull("one sample is not a stroke", strokeOf(Tool.PEN, 0, 4f, samples.take(1)))
    }

    @Test
    fun thePagePointUnderTheCursorUndoesTheDensityAndTheZoom() {
        // A page drawn at 200 % and 10/30 dp in: a pointer at 100/260 device pixels on a 2x display
        // is at (100/2 - 10)/2 = 20 pt across and (260/2 - 30)/2 = 50 pt down.
        val viewport = Viewport(zoom = 2f, offsetX = 10f, offsetY = 30f)
        val (x, y) = pagePointOf(xPx = 100f, yPx = 260f, density = 2f, viewport = viewport)
        assertEquals(20.0, x, 1e-6)
        assertEquals(50.0, y, 1e-6)
    }

    @Test
    fun aNewElementLandsOnTheTopLayer() {
        val page = SyntheticNotebook.pages(1).single()
        val grown = page.withElement(diagonal("stk_top"))
        assertEquals("one layer, one more element", 2, grown.layers.single().elements.size)
        assertEquals("the new one is on top", "stk_top", (grown.layers.single().elements.last() as Stroke).id)
        assertEquals("the page's own fields survive", page.width, grown.width, 0.0)
    }
}
