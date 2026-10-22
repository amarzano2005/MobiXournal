package com.mobixournal.desktop

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toPixelMap
import com.mobixournal.format.mobix.SyntheticNotebook
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import com.mobixournal.render.BackgroundGrid
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Two things the window must show the way the tablet does: a layer switched off is not painted, and an
 * isometric sheet is ruled by the **shared** geometry.
 *
 * Both are drawn through the real painter ([renderPageBitmap] → `PageView` → `drawPageContent`), so
 * these read the pixels the window paints, not a helper that agrees with itself.
 */
class PageLayersAndPaperTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val scale = 2f

    private fun page(background: Background, layers: List<Layer>) = Page(
        width = 200.0,
        height = 160.0,
        background = background,
        layers = layers,
    )

    private fun plain() = Background.Solid(color = 0xFFFFFFFF.toInt(), style = "plain")

    /** A horizontal black band at [y], two points tall. */
    private fun band(y: Double): Stroke = Stroke(
        points = listOf(
            StrokePoint(20.0, y, 4.0),
            StrokePoint(180.0, y, 4.0),
        ),
        color = 0xFF000000.toInt(),
        tool = Tool.PEN,
        capStyle = "round",
        uniformWidth = true,
    )

    /**
     * Switching a layer off is a **view** state: the file keeps the layer, the window does not paint it.
     *
     * The check is geometric rather than counting ink: each layer's band sits at its own height, so the
     * pixel under one band says whether that layer was drawn and the pixel under the other says the
     * rest of the page survived.
     */
    @Test
    fun aHiddenLayerIsNotPaintedAndTheRestIs() {
        val page = page(
            plain(),
            listOf(
                Layer(listOf(band(40.0))),
                Layer(listOf(band(120.0))),
            ),
        )
        val (x, bottomY) = (200 to 80)

        save(renderPageBitmap(page, scale, hiddenLayers = setOf(1)), "hidden-layer")

        assertTrue("layer 1 paints its band", inkAt(page, x, bottomY, hidden = emptySet()))
        assertTrue("layer 2 paints its band", inkAt(page, x, 240, hidden = emptySet()))

        assertTrue(
            "with layer 2 switched off its band is gone",
            !inkAt(page, x, 240, hidden = setOf(1)),
        )
        assertTrue(
            "and layer 1 is untouched",
            inkAt(page, x, bottomY, hidden = setOf(1)),
        )
        assertTrue(
            "switching layer 1 off leaves layer 2 painted",
            inkAt(page, x, 240, hidden = setOf(0)) && !inkAt(page, x, bottomY, hidden = setOf(0)),
        )
    }

    /** The band's centre is at `y * scale`; a stroke 4 pt wide spans a few pixels around it. */
    /** Writes the render where it can be looked at, beside the other desktop artefacts. */
    private fun save(bitmap: androidx.compose.ui.graphics.ImageBitmap, name: String) {
        val file = java.io.File("build/desktop-render/$name.png").apply { parentFile?.mkdirs() }
        javax.imageio.ImageIO.write(bitmap.toAwtImage(), "png", file)
    }

    private fun inkAt(page: Page, x: Int, y: Int, hidden: Set<Int>): Boolean {
        for (probe in -2..2) {
            val pixel = renderPageBitmap(page, scale, hiddenLayers = hidden).toPixelMap()[x, y + probe]
            if (pixel.red < 0.5f && pixel.green < 0.5f && pixel.blue < 0.5f) return true
        }
        return false
    }

    /**
     * An isometric sheet is ruled from `BackgroundGrid.isometric` — the same segments the tablet and the
     * SVG/PDF exports stroke.
     *
     * Checking midpoints is what makes that a claim about the *shared* geometry: the desktop could draw
     * some grid of its own and still look isometric, but its lines would not pass through the points the
     * shared function returns.
     */
    @Test
    fun isometricPaperFollowsTheSharedGrid() {
        val page = page(
            Background.Solid(color = 0xFFFFFFFF.toInt(), style = "isometric"),
            emptyList<Element>().let { listOf(Layer(it)) },
        )
        val bitmap = renderPageBitmap(page, scale)
        save(bitmap, "isometric-paper")
        val pixels = bitmap.toPixelMap()
        val segments = BackgroundGrid.isometric(page.width, page.height, BackgroundGrid.ISO_SPACING_PT)
        assertTrue("the shared geometry rules a mesh", segments.size > 10)

        var ruledPixels = 0
        for (px in 0 until pixels.width) {
            for (py in 0 until pixels.height) {
                val pixel = pixels[px, py]
                if (pixel.red < 0.95f) ruledPixels++
            }
        }
        assertTrue("the page is ruled, not blank ($ruledPixels px)", ruledPixels > 500)

        // A few segment midpoints must be ruled: the desktop's lines are the shared ones.
        for (segment in segments.take(6)) {
            val midX = ((segment[0] + segment[2]) / 2 * scale).toInt()
            val midY = ((segment[1] + segment[3]) / 2 * scale).toInt()
            val near = pixels[midX, midY]
            assertTrue(
                "the shared grid's segment midpoint ($midX, $midY) is ruled, got ${near.red}",
                near.red < 0.95f,
            )
        }

        // And off the mesh it is paper: a point a few points perpendicular to a line is blank.
        val first = segments.first()
        val dx = first[2] - first[0]
        val dy = first[3] - first[1]
        val length = kotlin.math.hypot(dx, dy)
        val offX = (((first[0] + first[2]) / 2) - dy / length * 6.0) * scale
        val offY = (((first[1] + first[3]) / 2) + dx / length * 6.0) * scale
        if (offX > 2 && offY > 2 && offX < pixels.width - 2 && offY < pixels.height - 2) {
            val off = pixels[offX.toInt(), offY.toInt()]
            assertTrue("between the lines it is paper, got ${off.red}", off.red > 0.98f)
        }
    }

    /** The eye's two states, as the sidebar draws them: open ink, muted and struck through when hidden. */
    @Test
    fun theSidebarEyeReadsTheTabsState() {
        val file = temp.newFile("two-pages.mobix")
        SyntheticNotebook.write(file, SyntheticNotebook.pages(2))
        val tab = DesktopTab(DocumentLoader.load(file), title = "two layers")

        assertTrue("both layers start visible", !tab.isLayerHidden(0, 0) && !tab.isLayerHidden(0, 1))
        tab.toggleLayer(0, 1)
        assertTrue("the eye switches one layer off", tab.isLayerHidden(0, 1))
        assertTrue("and only that one", !tab.isLayerHidden(0, 0))
        assertEquals("the painter is handed exactly that set", setOf(1), tab.hiddenOn(0))
        assertEquals("another page is untouched", emptySet<Int>(), tab.hiddenOn(3))

        tab.toggleLayer(0, 1)
        assertEquals("a second press brings it back", emptySet<Int>(), tab.hiddenOn(0))
    }
}
