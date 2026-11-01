package com.mobixournal.desktop

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.TextMeasurer
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Page
import com.mobixournal.render.VerticalSpaceOps
import org.jetbrains.skia.Picture

/**
 * A live vertical-space drag's own reading: the line it holds, and how far the block below is drawn.
 *
 * This is the whole of what a drag publishes per pointer sample — two numbers — because the drag does not
 * rewrite the page to preview itself. Rewriting it cost milliseconds per sample on a dense sheet (every
 * element of the block copied), and worse, every rewrite made the sheet's recorded picture **stale**: the
 * window's watchdog caught one drag re-recording the page 48 times a second with a worst frame of 146 ms.
 */
internal data class LiveShift(
    /** The grab line's page-local Y, in points — where the sheet splits. */
    val linePt: Double,
    /** How far the block below the line is drawn, in points (negative pulls up) — the pointer's travel. */
    val dyPt: Double,
)

/**
 * The sheet recorded in **two halves**, so a vertical-space drag is a translate and not a rebuild.
 *
 * This is desktop Xournal++'s own design, and it is worth naming because it settles a question the naive
 * shape gets wrong: its `VerticalToolHandler` takes the affected elements **out of the layer** while the
 * hand is down, and `VerticalToolView` records them into a cairo mask **once** ("initialize the mask on the
 * first call… upon zoom change") and then only does `translate(0, dY); mask.paintTo(cr)` per frame. The page
 * itself is never edited until the lift, which is one `MoveUndoAction`. Nothing in the model moves while the
 * hand does.
 *
 * Ours is the same trade with the model we have. The block's rule makes the split exact: the tool moves every
 * element whose **top edge is at or below the line** and leaves every other one alone, so the page divides
 * into elements that **stay** and elements that **follow the line**, with no element in both. Those are
 * recorded as two pictures at grab time — `still` (paper, ruling, backdrop and the ink above the line) and
 * `moving` (the block) — and a frame replays `still`, then `moving` under a `translate(0, dy)`. A drag then
 * costs what a scroll costs, and the page is written **once**, on the lift.
 *
 * The split is **by element, never by pixels**, which is what makes it exact where a clip line would fail: an
 * element the line passes through belongs to `still` and stays put *whole*, as the tool's rule says it must.
 *
 * One deliberate difference from the exact reflow, inherited from Xournal++'s mask: `moving` is painted
 * **after** `still`, so a block pulled up past the line covers the ink it passes rather than threading under
 * it. A block pushed down lands in space only it occupied, so the common direction has no such overlap at
 * all; and the lift's commit is the page's own order again. That is the price of a drag that follows the
 * hand, and it is the price the reference tool pays too.
 */
internal class ShiftPictures private constructor(
    /** The underlay and the ink that does not move, in the page's own order. */
    private val still: Picture,
    /** The ink that follows the line, in the page's own order. */
    private val moving: Picture,
) {

    /**
     * Paint the sheet for this frame: the page as it is, with the block translated by [dyPt].
     *
     * Exactly [PagePicture]'s transform — recorded at document scale, placed by the viewport's own matrix —
     * with one extra translate around the second picture, so a scroll or a zoom is still a replay.
     */
    fun draw(target: DrawScope, viewport: Viewport, zoom: Float, density: Float, dyPt: Double) {
        target.drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.save()
            native.translate(viewport.offsetX * density, viewport.offsetY * density)
            native.scale(zoom, zoom)
            native.drawPicture(still)
            native.save()
            native.translate(0f, (dyPt * density).toFloat())
            native.drawPicture(moving)
            native.restore()
            native.restore()
        }
    }

    /** Release the native pictures — a pageful of drawing instructions each. */
    fun close() {
        still.close()
        moving.close()
    }

    companion object {

        /**
         * Record [page] as the two pictures a drag replays.
         *
         * The halves share the page's own painting functions (`drawSheetUnderlay`, `drawElements`), so a
         * previewed block cannot look different from the committed page at the seam where they meet.
         */
        fun record(
            page: Page,
            linePt: Double,
            density: Float,
            backdrop: PageBackdrop?,
            hiddenLayers: Set<Int>,
            measurer: TextMeasurer,
            skip: Element?,
        ): ShiftPictures {
            val block = VerticalSpaceOps.blockBelow(page, linePt)
            val sheet = Size((page.width * density).toFloat(), (page.height * density).toFloat())
            val still = recordPicture(page, density) {
                drawSheetUnderlay(page, density, sheet, backdrop)
                for ((layerIndex, layer) in page.layers.withIndex()) {
                    if (layerIndex in hiddenLayers) continue
                    val keeps = layer.elements.filterIndexed { index, _ -> !block.moves(layerIndex, index) }
                    drawElements(keeps, density, measurer, skip)
                }
            }
            val moving = recordPicture(page, density) {
                for ((layerIndex, layer) in page.layers.withIndex()) {
                    if (layerIndex in hiddenLayers) continue
                    val follows = layer.elements.filterIndexed { index, _ -> block.moves(layerIndex, index) }
                    if (follows.isEmpty()) continue
                    drawElements(follows, density, measurer, skip)
                }
            }
            return ShiftPictures(still, moving)
        }
    }
}
