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
import androidx.compose.ui.unit.Density
import com.mobixournal.format.mobix.SyntheticNotebook
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * What a **frame** costs while writing and while erasing, on a page that is already full of ink.
 *
 * The reader's complaint is a lag that comes and goes while writing, and again while rubbing out, which is
 * a claim about milliseconds per frame — so it is measured rather than argued about. The numbers are
 * printed; the assertions are loose on purpose (a shared CI machine is not a workstation), and their job
 * is to catch the shape of a regression: a frame that costs a pageful of recording on every lift, or a
 * live stroke that is rebuilt from its first point on every packet.
 */
@OptIn(ExperimentalComposeUiApi::class)
class DesktopWritingCostTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val viewW = 900
    private val viewH = 700

    /** A dense page: 400 strokes of 24 samples, laid out like a written sheet. */
    private fun denseNotebook(): File = temp.newFile("dense.mobix").also { file ->
        SyntheticNotebook.write(file, SyntheticNotebook.pages(2))
    }

    private fun dense(tab: DesktopTab) {
        val strokes = (0 until 400).map { index ->
            val y = 60.0 + (index % 34) * 22.0
            val x = 40.0 + (index / 34) * 40.0
            Stroke(
                tool = Tool.PEN,
                color = 0xFF1A1A1A.toInt(),
                capStyle = "round",
                points = (0 until 24).map { sample ->
                    StrokePoint(x + sample * 2.4, y + kotlin.math.sin(sample * 0.4) * 5.0, 1.4)
                },
                uniformWidth = true,
            )
        }
        tab.document.editPage(0) { page ->
            page.copy(layers = page.layers + Layer(strokes as List<Element>))
        }
    }

    @Test
    fun `a written page keeps its frames small while writing and erasing`() {
        val tab = DesktopTabs().open(DocumentLoader.load(denseNotebook()))
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        dense(tab)
        StrokePathCache.clear()
        val scene = ImageComposeScene(viewW, viewH, Density(1f)) {
            PageCanvas(tab = tab, panMode = false, tool = DesktopTool.PEN, onViewport = {}, onFit = {})
        }
        try {
            Snapshot.sendApplyNotifications()
            scene.render()
            // Warm the page's picture: the first frame records the whole sheet, which is the one frame a
            // measurement of a *frame* should not include.
            scene.render()

            val idle = (1..20).map { time { scene.render() } }
            println("writing-cost idle: median ${median(idle)} ms")
            // An idle frame replays the page's recorded picture, so it is the floor: the software raster of
            // a full sheet dominates this harness and no absolute budget here would mean anything on a GPU.
            val floor = idle.sorted()[idle.size / 2]

            // A stroke drawn across the page: 160 pointer moves, each followed by a frame — a long word.
            val frames = mutableListOf<Double>()
            pen(scene, PointerEventType.Press, 200f, 300f, 1_000L)
            for (step in 1..160) {
                pen(scene, PointerEventType.Move, 200f + step, 300f + (step % 8), 1_000L + step * 4)
                frames += time { scene.render() }
            }
            pen(scene, PointerEventType.Release, 360f, 300f, 2_000L, pressed = false)
            frames += time { scene.render() }
            println("writing-cost stroke: median ${median(frames)} ms, p95 ${percentile(frames, 0.95)} ms")

            // The lift itself: the frame that records the new stroke into the page's picture.
            val lift = time { scene.render() }
            println("writing-cost lift: $lift ms")

            val strokeMedian = frames.sorted()[frames.size / 2]
            assertTrue(
                "a written frame costs a replay, not a page: $strokeMedian ms against an idle $floor ms",
                strokeMedian < floor * 1.6 + 4.0,
            )
            assertTrue(
                "and a lift does not record the sheet twice: $lift ms against an idle $floor ms",
                lift < floor * 2.0 + 8.0,
            )
        } finally {
            scene.close()
            tab.document.close()
        }
    }

    /**
     * The **eraser**, which is the worse half: every bite changes ink the page's base picture already holds,
     * so each frame re-records the sheet. The paths are cached, so a re-record is a replay of geometry that
     * was already built — this pins that, because a cache that silently stopped working would put the
     * whole page's outline construction back on every frame of a rub-out.
     */
    @Test
    fun `rubbing out a written page does not rebuild every stroke`() {
        val tab = DesktopTabs().open(DocumentLoader.load(denseNotebook()))
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        dense(tab)
        StrokePathCache.clear()
        val scene = ImageComposeScene(viewW, viewH, Density(1f)) {
            PageCanvas(tab = tab, panMode = false, tool = DesktopTool.ERASER, onViewport = {}, onFit = {})
        }
        try {
            Snapshot.sendApplyNotifications()
            scene.render()
            scene.render()
            // The sheet's outlines are all cached by now; a rub-out must not rebuild any of them.
            val cached = StrokePathCache.builds
            assertTrue("the page's outlines were built once ($cached)", cached >= 400)
            val idle = (1..10).map { time { scene.render() } }
            val floor = idle.sorted()[idle.size / 2]
            val frames = mutableListOf<Double>()
            pen(scene, PointerEventType.Press, 220f, 180f, 1_000L)
            for (step in 1..40) {
                pen(scene, PointerEventType.Move, 220f + step * 2f, 180f + step, 1_000L + step * 8)
                frames += time { scene.render() }
            }
            pen(scene, PointerEventType.Release, 300f, 220f, 2_000L, pressed = false)
            frames += time { scene.render() }
            println("writing-cost erase: median ${median(frames)} ms, p95 ${percentile(frames, 0.95)} ms")
            println("writing-cost erase: ${StrokePathCache.builds - cached} outlines rebuilt")
            val eraseMedian = frames.sorted()[frames.size / 2]
            // A bite splits the one stroke it touches, and a split is new geometry; nothing else is. The
            // bound is one rebuild per frame — a cache that stopped working would be the page's 400, every
            // frame.
            val rebuilt = StrokePathCache.builds - cached
            assertTrue("rubbing out rebuilt only what it split ($rebuilt)", rebuilt <= frames.size)
            assertTrue(
                "a rubbed frame costs a replay, not a page: $eraseMedian ms against an idle $floor ms",
                eraseMedian < floor * 1.6 + 4.0,
            )
        } finally {
            scene.close()
            tab.document.close()
        }
    }

    private fun time(block: () -> Unit): Double {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1_000_000.0
    }

    private fun median(values: List<Double>): String =
        String.format(java.util.Locale.ROOT, "%.2f", values.sorted()[values.size / 2])

    private fun percentile(values: List<Double>, at: Double): String {
        val sorted = values.sorted()
        val index = (sorted.size * at).toInt().coerceIn(0, sorted.lastIndex)
        return String.format(java.util.Locale.ROOT, "%.2f", sorted[index])
    }

    private fun pen(
        scene: ImageComposeScene,
        type: PointerEventType,
        x: Float,
        y: Float,
        time: Long,
        pressed: Boolean = true,
    ) {
        scene.sendPointerEvent(
            eventType = type,
            pointers = listOf(
                ComposeScenePointer(
                    id = PointerId(1),
                    position = Offset(x, y),
                    pressed = pressed,
                    type = PointerType.Stylus,
                    pressure = 0.6f,
                ),
            ),
            buttons = if (pressed) PointerButtons(isPrimaryPressed = true) else PointerButtons(),
            timeMillis = time,
        )
    }
}
