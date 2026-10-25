package com.mobixournal.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.TextElement
import com.mobixournal.format.model.Tool
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The page's picture is the **same page**, only recorded once.
 *
 * The frame budget at 144 Hz is 6.9 ms, and `drawPageContent` spends it on Kotlin: every living entity
 * recompiled, every text box measured, a `Path` per stroke. `PagePicture` records those calls once per
 * viewport and replays the result, so the two things worth proving are that the replay is *the same
 * pixels* — a cache that draws its own idea of the page would be a silent file/render divergence — and
 * that a frame of a live stroke does not record it again.
 */
@OptIn(ExperimentalComposeUiApi::class)
class PagePictureTest {

    private val width = 420
    private val height = 520

    private fun page(strokes: Int = 120): Page = Page(
        width = width.toDouble(),
        height = height.toDouble(),
        background = Background.Solid(color = 0xFFFFFFFF.toInt(), style = "plain"),
        layers = listOf(
            Layer(
                (0 until strokes).map { index ->
                    val y = (20 + index % 20 * 20).toDouble()
                    Stroke(
                        points = listOf(
                            StrokePoint(20.0, y, 2.0),
                            StrokePoint((40 + index % 12 * 20).toDouble(), y + 8, 2.0),
                        ),
                        color = 0xFF000000.toInt(),
                        tool = Tool.PEN,
                        capStyle = "round",
                        uniformWidth = true,
                    )
                } + TextElement(
                    font = "sans",
                    size = 14.0,
                    x = 30.0,
                    y = 470.0,
                    color = 0xFF000000.toInt(),
                    content = "The recorded text is measured once",
                ),
            ),
        ),
    )

    /** One window painting [page] either through the picture or straight. */
    private class Sheet(page: Page, width: Int, height: Int) {
        val picture = PagePicture()
        var recorded by mutableStateOf(false)
        var viewport by mutableStateOf(Viewport(zoom = 1f))

        /** The stroke being drawn: it moves every frame, which is what repaints the canvas at all. */
        var live by mutableStateOf(10f)

        val scene: ImageComposeScene = ImageComposeScene(width, height, Density(1f)) {
            val measurer = rememberTextMeasurer()
            Canvas(Modifier.fillMaxSize()) {
                if (recorded) {
                    picture.draw(this, page, viewport, 1f, 1f, null, emptySet(), measurer)
                } else {
                    withTransform({ translate(viewport.offsetX, viewport.offsetY) }) {
                        drawPageContent(page, 1f, measurer)
                    }
                }
                drawLine(
                    color = Color(0xFF123456),
                    start = Offset(10f, live),
                    end = Offset(200f, live),
                    strokeWidth = 3f,
                )
            }
        }

        fun frame(): IntArray {
            Snapshot.sendApplyNotifications()
            val png = scene.render().encodeToData()?.bytes ?: error("Skia produced no image")
            val image = ImageIO.read(ByteArrayInputStream(png))
            val out = IntArray(image.width * image.height)
            image.getRGB(0, 0, image.width, image.height, out, 0, image.width)
            return out
        }

        fun close() = scene.close()
    }

    @Test
    fun `the replayed sheet is pixel-identical to the direct paint`() {
        val sheet = Sheet(page(), width, height)
        try {
            val direct = sheet.frame()
            assertTrue("the fixture page has ink on it", direct.count { (it and 0xFFFFFF) < 0x606060 } > 100)

            sheet.recorded = true
            val replayed = sheet.frame()
            assertEquals("same frame size", direct.size, replayed.size)
            var worst = 0
            var differing = 0
            for (index in direct.indices) {
                var diff = 0
                for (shift in intArrayOf(16, 8, 0)) {
                    val a = (direct[index] shr shift) and 0xFF
                    val b = (replayed[index] shr shift) and 0xFF
                    diff = maxOf(diff, abs(a - b))
                }
                if (diff > 0) differing++
                worst = maxOf(worst, diff)
            }
            assertTrue(
                "the replayed sheet is the painted one (worst channel difference $worst, " +
                    "$differing of ${direct.size} pixels)",
                worst <= 2 && differing < direct.size / 100,
            )
        } finally {
            sheet.close()
        }
    }

    @Test
    fun `a frame of a live stroke replays the page, and scrolling or zooming records nothing`() {
        val sheet = Sheet(page(), width, height)
        try {
            sheet.recorded = true
            for (frame in 1..4) {
                sheet.live = 10f + frame * 8f
                sheet.frame()
            }
            assertEquals("the page was recorded once", 1, sheet.picture.records)
            assertEquals("and replayed by every frame", 4, sheet.picture.replays)

            // The point of recording at document scale: a scroll is a translate of the same picture and a
            // zoom is a scale of it, so a wheel or a pinch costs a replay and never the Kotlin paint.
            sheet.viewport = Viewport(zoom = 1f, offsetY = -12f)
            sheet.live = 60f
            sheet.frame()
            assertEquals("a scroll does not record the sheet again", 1, sheet.picture.records)
            sheet.viewport = Viewport(zoom = 1.6f, offsetX = -40f, offsetY = -90f)
            sheet.live = 68f
            sheet.frame()
            assertEquals("and neither does a zoom", 1, sheet.picture.records)
            assertEquals("every frame was served by a replay", 6, sheet.picture.replays)
        } finally {
            sheet.close()
        }
    }

    /**
     * The scaled replay is still *the page*: the same content at the same viewport must look the same
     * whether it was recorded at the reader's zoom or placed by the canvas matrix. Skia replays vector
     * commands under the total matrix, so this is a difference of rounding at most — measured, not assumed.
     */
    @Test
    fun `the replayed sheet is the painted one at a zoom and a scroll`() {
        for (viewport in listOf(
            Viewport(zoom = 1.6f, offsetX = -40f, offsetY = -90f),
            Viewport(zoom = 0.75f, offsetY = 30f),
        )) {
            val page = page(strokes = 40)
            val direct = directFrame(page, viewport)
            val replayed = replayedFrame(page, viewport)
            var worst = 0
            var differing = 0
            for (index in direct.indices) {
                var diff = 0
                for (shift in intArrayOf(16, 8, 0)) {
                    val a = (direct[index] shr shift) and 0xFF
                    val b = (replayed[index] shr shift) and 0xFF
                    diff = maxOf(diff, abs(a - b))
                }
                if (diff > 0) differing++
                worst = maxOf(worst, diff)
            }
            assertTrue(
                "at ${viewport.zoom}× the replay is the painted page (worst channel difference $worst, " +
                    "$differing of ${direct.size} pixels)",
                worst <= 8 && differing < direct.size / 20,
            )
        }
    }

    /**
     * A lift must not cost the sheet: a stroke added on top is recorded into the tail, and the base — the
     * other hundred-and-something strokes, the measured text, the paper — is replayed untouched.
     *
     * The counters are the contract: `records` is the full paint, `tailRecords` is the new ink. A removal
     * is deliberately the other case: it erases ink the base already holds, which a picture cannot take
     * back, so that frame re-records the sheet.
     */
    @Test
    fun `a stroke appended on top is recorded on its own, not the whole sheet`() {
        val page = page(strokes = 60)
        val shot = mutableStateOf(page)
        val picture = PagePicture()
        val scene = ImageComposeScene(width, height, Density(1f)) {
            val measurer = rememberTextMeasurer()
            Canvas(Modifier.fillMaxSize()) {
                picture.draw(this, shot.value, Viewport(zoom = 1f), 1f, 1f, null, emptySet(), measurer)
            }
        }
        try {
            frame(scene)
            assertEquals("the sheet is recorded once", 1, picture.records)
            assertEquals("and there is no tail yet", 0, picture.tailRecords)

            for (index in 0 until 3) {
                shot.value = withStroke(shot.value, thinStroke(300.0 + index * 10))
                frame(scene)
            }
            assertEquals("three strokes do not re-record the sheet", 1, picture.records)
            assertEquals("every lift recorded just the new ink", 3, picture.tailRecords)

            // Taking back a stroke of the run is still a shorter append: the base never held it.
            shot.value = withoutLastStroke(shot.value)
            frame(scene)
            assertEquals("the run shrinks without the sheet being recorded", 1, picture.records)

            // Erasing ink the **base** holds is the other case: a picture cannot un-draw it, so the sheet
            // is recorded again.
            shot.value = withoutFirstElement(shot.value)
            frame(scene)
            assertEquals("a removal from the base re-records the sheet", 2, picture.records)
        } finally {
            scene.close()
        }
    }

    /**
     * A **tail element that changed** is not an append: the part placed a moment ago, then dragged.
     *
     * The run of appended elements was compared against the *base* only, so an element living in a tail was
     * never checked: dragging it kept the run the same length with the same objects before it, which read as
     * "nothing was appended", and the sheet went on replaying the tail's old picture. On screen the selection
     * outline travelled with the pointer while the ink stayed where the part was first placed — the report
     * that a pin "never attaches". The tail cannot be patched, so the sheet is recorded again.
     */
    @Test
    fun `a tail element that changes is recorded again instead of keeping the old ink`() {
        val shot = mutableStateOf(page(strokes = 20))
        val picture = PagePicture()
        val scene = ImageComposeScene(width, height, Density(1f)) {
            val measurer = rememberTextMeasurer()
            Canvas(Modifier.fillMaxSize()) {
                picture.draw(this, shot.value, Viewport(zoom = 1f), 1f, 1f, null, emptySet(), measurer)
            }
        }
        try {
            frame(scene)
            shot.value = withStroke(shot.value, thinStroke(300.0))
            frame(scene)
            assertEquals("the placed stroke went into the tail", 1, picture.records)
            assertEquals(1, picture.tailRecords)

            val changed = withLastReplaced(shot.value, thinStroke(340.0))
            shot.value = changed
            val replayed = frame(scene)
            assertEquals("a changed tail element re-records the sheet", 2, picture.records)
            assertSamePixels(
                directFrame(changed, Viewport(zoom = 1f)),
                replayed,
                "the replay shows the element where it is now, not where it was",
            )
        } finally {
            scene.close()
        }
    }

    /** The tail is *the same page*: base plus tail must land the pixels the direct paint does. */
    @Test
    fun `base plus tail is the painted page`() {
        val base = page(strokes = 40)
        var grown = base
        for (index in 0 until 5) grown = withStroke(grown, thinStroke(120.0 + index * 14))
        val shot = mutableStateOf(base)
        val picture = PagePicture()
        val scene = ImageComposeScene(width, height, Density(1f)) {
            val measurer = rememberTextMeasurer()
            Canvas(Modifier.fillMaxSize()) {
                picture.draw(this, shot.value, Viewport(zoom = 1f), 1f, 1f, null, emptySet(), measurer)
            }
        }
        try {
            frame(scene)
            shot.value = grown
            val replayed = frame(scene)
            assertEquals("one base, and the strokes added since", 1, picture.records)
            assertEquals(1, picture.tailRecords)

            assertSamePixels(
                directFrame(grown, Viewport(zoom = 1f)),
                replayed,
                "the tail composes into the painted page",
            )
        } finally {
            scene.close()
        }
    }

    /**
     * A run of strokes stays on the sheet. The tail holds **every** element appended since the base — not
     * only the newest one — so the second lift must not take the first stroke away with it, which is
     * exactly what a tail re-recorded from the previous frame did (the ink vanished while the pen was in
     * hand and came back when something forced a full record).
     */
    @Test
    fun `a run of strokes keeps every one of them on the sheet`() {
        var page = page(strokes = 30)
        val shot = mutableStateOf(page)
        val picture = PagePicture()
        val scene = ImageComposeScene(width, height, Density(1f)) {
            val measurer = rememberTextMeasurer()
            Canvas(Modifier.fillMaxSize()) {
                picture.draw(this, shot.value, Viewport(zoom = 1f), 1f, 1f, null, emptySet(), measurer)
            }
        }
        try {
            frame(scene)
            for (index in 0 until 4) {
                page = withStroke(page, thinStroke(140.0 + index * 18))
                shot.value = page
                assertSamePixels(
                    directFrame(page, Viewport(zoom = 1f)),
                    frame(scene),
                    "after ${index + 1} strokes the sheet is the painted page",
                )
            }
            assertEquals("the sheet itself was recorded once", 1, picture.records)
            assertEquals("and every lift recorded the run", 4, picture.tailRecords)
        } finally {
            scene.close()
        }
    }

    /** [replayed] against [direct], with the tolerance the other replay comparisons use. */
    private fun assertSamePixels(direct: IntArray, replayed: IntArray, message: String) {
        assertEquals("same frame size", direct.size, replayed.size)
        var worst = 0
        var differing = 0
        for (index in direct.indices) {
            var diff = 0
            for (shift in intArrayOf(16, 8, 0)) {
                diff = maxOf(diff, abs(((direct[index] shr shift) and 0xFF) - ((replayed[index] shr shift) and 0xFF)))
            }
            if (diff > 0) differing++
            worst = maxOf(worst, diff)
        }
        assertTrue(
            "$message (worst channel difference $worst, $differing of ${direct.size} pixels)",
            worst <= 2 && differing < direct.size / 100,
        )
    }

    /**
     * Which edits may go through the tail, as a pure rule: only what was appended to the **top** layer,
     * with every other layer holding the very same element objects. Anything else would paint out of
     * order or keep showing ink the page no longer has.
     */
    @Test
    fun `only an append on the top layer counts as an append`() {
        val stroke = thinStroke(100.0)
        val one = page(strokes = 2)
        assertEquals(
            "appending on top is incremental",
            listOf(stroke),
            appendedElements(one, withStroke(one, stroke)),
        )
        assertEquals(
            "an unchanged page appends nothing",
            emptyList<com.mobixournal.format.model.Element>(),
            appendedElements(one, one),
        )
        assertNull("a removal is not an append", appendedElements(withStroke(one, stroke), one))

        // The same element appended to a layer that is not on top would be painted after the layers above
        // it, so the base cannot stay: the change is refused.
        val lower = one.copy(
            layers = listOf(
                Layer(one.layers.single().elements + stroke, one.layers.single().name),
                Layer(emptyList()),
            ),
        )
        val two = one.copy(layers = one.layers + Layer(emptyList()))
        assertNull("an append below the top layer is not incremental", appendedElements(two, lower))
    }

    /** One frame of [scene], as ARGB pixels. */
    private fun frame(scene: ImageComposeScene): IntArray {
        Snapshot.sendApplyNotifications()
        val png = scene.render().encodeToData()?.bytes ?: error("Skia produced no image")
        val image = ImageIO.read(ByteArrayInputStream(png))
        return IntArray(image.width * image.height).also {
            image.getRGB(0, 0, image.width, image.height, it, 0, image.width)
        }
    }

    /** The page drawn straight at [viewport] — the reference the replay is compared with. */
    private fun directFrame(page: Page, viewport: Viewport): IntArray = headless(width, height) {
        val measurer = rememberTextMeasurer()
        Canvas(Modifier.fillMaxSize()) {
            withTransform({ translate(viewport.offsetX, viewport.offsetY) }) {
                drawPageContent(page, viewport.zoom, measurer)
            }
        }
    }

    /** The same page and viewport, served by the recorded picture. */
    private fun replayedFrame(page: Page, viewport: Viewport): IntArray = headless(width, height) {
        val measurer = rememberTextMeasurer()
        val picture = PagePicture()
        Canvas(Modifier.fillMaxSize()) {
            picture.draw(this, page, viewport, viewport.zoom, 1f, null, emptySet(), measurer)
        }
    }

    /** [page] with [stroke] added on top of its top layer. */
    private fun withStroke(page: Page, stroke: Stroke): Page = page.copy(
        layers = page.layers.mapIndexed { index, layer ->
            if (index == page.layers.lastIndex) Layer(layer.elements + stroke, layer.name) else layer
        },
    )

    /** [page] with its last element **replaced** — the same slot, a different object (a moved element). */
    private fun withLastReplaced(page: Page, element: Stroke): Page = page.copy(
        layers = page.layers.mapIndexed { index, layer ->
            if (index == page.layers.lastIndex) {
                Layer(layer.elements.dropLast(1) + element, layer.name)
            } else {
                layer
            }
        },
    )

    /** [page] without the first element of its top layer — ink the base picture holds. */
    private fun withoutFirstElement(page: Page): Page = page.copy(
        layers = page.layers.mapIndexed { index, layer ->
            if (index == page.layers.lastIndex) Layer(layer.elements.drop(1), layer.name) else layer
        },
    )

    /** [page] without the last element of its top layer — what a whole-stroke erase leaves. */
    private fun withoutLastStroke(page: Page): Page = page.copy(
        layers = page.layers.mapIndexed { index, layer ->
            if (index == page.layers.lastIndex) Layer(layer.elements.dropLast(1), layer.name) else layer
        },
    )

    /** A two-point pen stroke, the smallest ink a lift can add. */
    private fun thinStroke(y: Double): Stroke = Stroke(
        points = listOf(StrokePoint(30.0, y, 2.0), StrokePoint(180.0, y + 6, 2.0)),
        color = 0xFF000000.toInt(),
        tool = Tool.PEN,
        capStyle = "round",
        uniformWidth = true,
    )

    /** One frame of a scene, as ARGB pixels. */
    private fun headless(width: Int, height: Int, content: @androidx.compose.runtime.Composable () -> Unit): IntArray {
        val scene = ImageComposeScene(width, height, Density(1f)) { content() }
        try {
            val png = scene.render().encodeToData()?.bytes ?: error("Skia produced no image")
            val image = ImageIO.read(ByteArrayInputStream(png))
            return IntArray(image.width * image.height).also {
                image.getRGB(0, 0, image.width, image.height, it, 0, image.width)
            }
        } finally {
            scene.close()
        }
    }
}
