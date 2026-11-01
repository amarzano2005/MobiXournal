package com.mobixournal.desktop

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.mobixournal.format.mobix.SyntheticNotebook
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import com.mobixournal.render.Bounds
import com.mobixournal.render.ElementRef
import com.mobixournal.render.PageSelection
import com.mobixournal.render.SelectionEdit
import com.mobixournal.render.SelectionRegion
import java.io.File
import org.jetbrains.skia.PictureRecorder
import org.jetbrains.skia.Rect
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * What a **selection drag** costs the sheet, measured and pinned rather than assumed.
 *
 * This suite exists because the audit that named the drag the last gesture paying O(page) per frame was
 * wrong about the price, and the price is what decides whether a second preview machine (the two-half
 * recording a vertical-space drag uses, [ShiftPictures]) is worth its complexity. Measured on the same
 * written sheet `DesktopWritingCostTest` uses, one pointer sample costs the model ~0.2 ms (the selected
 * elements are translated against the gesture's own snapshot) and one frame costs the sheet ~1 ms,
 * because the page's identity changes and it is recorded again. A frame that replays instead costs
 * ~0.02 ms.
 *
 * So the drag is **not** the jank source: about 1.5 ms of a 16.7 ms frame, against the vertical-space
 * drag's 5.2 ms of reflow per sample that re-recorded 48 times a second. No preview was built for it, and
 * this test is the reason that decision can be re-opened with numbers instead of intuition — if the shape
 * changes, the invariants below say which way it went wrong.
 *
 * The invariants are the ones a regression actually breaks:
 *
 *  - **one record per frame, never one per sample** — the sheet is recorded because the *page* changed,
 *    so however many samples arrive between two frames, the frame pays once (Compose coalesces the paints;
 *    a canvas that recorded from the event handler would show it here);
 *  - **a replay is not a record** — a still page costs the replay, which is what makes scroll and zoom
 *    cheap and what a per-frame rebuild would destroy;
 *  - **the sample cost is the selection, not the page** — translating 200 elements must stay a fraction of
 *    a millisecond, or the drag has started copying the page per pointer event.
 *
 * The ceilings are deliberately loose (a CI machine is not a workstation), exactly like
 * `DesktopWritingCostTest`: they catch a change of *order*, not a few percent.
 */
@OptIn(ExperimentalComposeUiApi::class)
class DesktopSelectionCostTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun notebook(): File = temp.newFile("selection-cost.mobix").also { file ->
        SyntheticNotebook.write(file, SyntheticNotebook.pages(1))
    }

    /** A written sheet: 400 strokes of 24 samples, the fixture `DesktopWritingCostTest` uses. */
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
        tab.document.editPage(0) { page -> page.copy(layers = page.layers + Layer(strokes as List<Element>)) }
    }

    /** A measurer off a throwaway scene: the sheet records text boxes through it. */
    private val measurer: TextMeasurer by lazy {
        var measured: TextMeasurer? = null
        val scene = ImageComposeScene(10, 10, Density(1f)) { measured = rememberTextMeasurer() }
        try {
            scene.render()
        } finally {
            scene.close()
        }
        requireNotNull(measured)
    }

    /**
     * One frame of the sheet, recorded into a throwaway picture instead of rasterised.
     *
     * Recording is the sheet's own cost — the commands it submits — so the number does not depend on a
     * software framebuffer or on the window's size. That is the half a drag pays per frame.
     */
    private fun frame(shot: Page, picture: PagePicture, viewport: Viewport) {
        val recorder = PictureRecorder()
        val canvas = recorder.beginRecording(Rect.makeWH(900f, 700f))
        CanvasDrawScope().draw(
            density = Density(1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = canvas.asComposeCanvas(),
            size = androidx.compose.ui.geometry.Size(900f, 700f),
        ) {
            picture.draw(this, shot, viewport, 1f, 1f, null, emptySet(), measurer)
        }
        recorder.finishRecordingAsPicture().close()
    }

    private fun median(samples: List<Double>): Double = samples.sorted()[samples.size / 2]

    @Test
    fun `a selection drag records the sheet once per frame and replays a still page`() {
        val tab = DesktopTabs().open(DocumentLoader.load(notebook()))
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        dense(tab)
        val page = tab.document.page(0)
        val top = page.layers.lastIndex
        val selection = PageSelection(
            region = SelectionRegion.Rect(Bounds(30.0, 20.0, 500.0, 700.0)),
            refs = (0 until 200).map { ElementRef(top, it) }.toSet(),
        )
        val picture = PagePicture()
        try {
            frame(page, picture, tab.viewport)
            val afterFirst = picture.records

            // One frame per sample: what the drag does, drawn as the window draws it.
            val changing = (0 until 30).map { index ->
                val moved = SelectionEdit.move(page, selection, index + 1.0, index + 1.0).first
                val started = System.nanoTime()
                frame(moved, picture, tab.viewport)
                (System.nanoTime() - started) / 1e6
            }
            val records = picture.records - afterFirst
            assertTrue(
                "the page changed every frame, so every frame recorded it once — got $records records " +
                    "for 30 frames",
                records in 30..31,
            )

            val still = (0 until 30).map {
                val started = System.nanoTime()
                frame(page, picture, tab.viewport)
                (System.nanoTime() - started) / 1e6
            }
            assertTrue(
                "a still page replays instead of recording — got ${picture.records - afterFirst - records} " +
                    "records for 30 still frames",
                picture.records - afterFirst - records <= 1,
            )

            val recordCost = median(changing)
            val replayCost = median(still)
            assertTrue(
                "a drag frame stays a few milliseconds on a written sheet (median %.2f ms)".format(recordCost),
                recordCost < 12.0,
            )
            assertTrue(
                "a replay is far cheaper than a record (%.3f ms against %.2f ms)".format(replayCost, recordCost),
                replayCost * 8 < recordCost,
            )

            // The sample's own half: translating the selection, against the gesture's snapshot.
            repeat(20) { SelectionEdit.move(page, selection, it * 0.5, it * 0.5) }
            val samples = (0 until 40).map { index ->
                val started = System.nanoTime()
                SelectionEdit.move(page, selection, index * 0.5, index * 0.5)
                (System.nanoTime() - started) / 1e6
            }
            val sampleCost = median(samples)
            assertTrue(
                "one sample moves the selection and not the page (median %.2f ms)".format(sampleCost),
                sampleCost < 4.0,
            )
        } finally {
            picture.close()
            tab.document.close()
        }
    }
}
