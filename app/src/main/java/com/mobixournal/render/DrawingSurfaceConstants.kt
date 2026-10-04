/**
 * [DrawingSurfaceView]'s shared tuning constants ([DrawingSurfaceDefaults]) and the blank-document
 * factories every empty canvas starts from. Values and factories — no drawing logic — so the rest of
 * the `DrawingSurface*.kt` family can read a number or make an empty sheet without pulling in another
 * file's logic.
 */
package com.mobixournal.render

import android.graphics.Color as AndroidColor
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page

/**
 * The tuning constants [DrawingSurfaceView] and its collaborators share: page geometry, zoom and
 * pinch limits, hit-test paddings and the guide-drag states. They live here — outside the view —
 * so the surface's split-out extension files and the helper classes around it (selection, guide
 * drag, page commands) can all read one copy.
 */
internal object DrawingSurfaceDefaults {

    /**
     * The page size a **new** sheet is born with — Settings ▸ Editor's *Default page size*, pushed in
     * by [setDefaultPageSize] whenever that setting loads or changes.
     */
    internal var defaultPageWidth: Double = A4_WIDTH_PT
    internal var defaultPageHeight: Double = A4_HEIGHT_PT

    const val A4_WIDTH_PT = 595.276
    const val A4_HEIGHT_PT = 841.89
    const val PAGE_SIZE_MIN_PT = 72.0     // 1 in — floor on a page dimension
    const val PAGE_SIZE_MAX_PT = 14400.0  // 200 in — ceiling on a page dimension
    const val GAP_PX = 24f
    const val ERASER_RADIUS_PX = 18f

    /** Document scroll per mouse-wheel notch, in dp (roughly three text lines). */
    const val WHEEL_SCROLL_DP = 64f

    /**
     * Highlighter width as a multiple of the pen's base width — desktop Xournal++'s highlighter is
     * about six times the pen at the same size slot (its pen medium is 1.41 pt, its highlighter
     * medium 8.50 pt). This is only an intermediate: the product is then snapped to one of
     * [HIGHLIGHTER_SIZES_PT], so a highlighter stroke is always one of the desktop's own tip sizes.
     */
    const val HIGHLIGHTER_WIDTH_FACTOR = 6f

    /**
     * Desktop Xournal++'s highlighter tip sizes in points — its **fine / medium / thick** highlighter
     * slots, the 1 mm / 3 mm / 7 mm tips (`ToolHandler::initTools` in upstream:
     * `highlighter thicknesses = {1, 2.83, 8.50, 19.84, 30}`, of which these three are the canonical
     * 1 mm / 3 mm / 7 mm set). The app's highlighter borrows the pen's S/M/L slots, so its width is
     * snapped onto this list rather than left as `penWidth × HIGHLIGHTER_WIDTH_FACTOR` — that keeps
     * a highlighter band the thickness the desktop app would draw, independent of how the pen slots
     * happen to be configured.
     */
    val HIGHLIGHTER_SIZES_PT: List<Double> = listOf(2.83, 8.50, 19.84)

    /**
     * The highlighter tip size (pt) nearest [baseWidthPt] scaled by [HIGHLIGHTER_WIDTH_FACTOR] — see
     * [HIGHLIGHTER_SIZES_PT]. Pure, so the mapping is pinned by a unit test.
     */
    fun highlighterWidthFor(baseWidthPt: Float): Double {
        val wanted = baseWidthPt * HIGHLIGHTER_WIDTH_FACTOR
        return HIGHLIGHTER_SIZES_PT.minByOrNull { kotlin.math.abs(it - wanted) } ?: HIGHLIGHTER_SIZES_PT[0]
    }

    const val ZOOM_STEP = ViewportState.ZOOM_STEP
    const val MIN_ZOOM = ViewportState.MIN_ZOOM
    const val MAX_ZOOM = ViewportState.MAX_ZOOM

    /** Below this two-finger span (view px) the pinch ratio is too noisy to zoom by, so it's ignored. */
    const val PINCH_MIN_SPAN_PX = 40f

    const val TAP_SLOP_PX = 16f
    const val SELECT_PAD_PX = 6f
    const val MOVE_GRAB_PAD = 8.0
    const val HANDLE_HIT_PX = 30f      // touch radius for grabbing a resize/rotate handle
    const val ROTATE_ARM_PX = 40f      // gap from the right edge out to the rotate knob

    /**
     * Height (dp) of the top/bottom edge band that auto-scrolls the page while a selection is
     * dragged into it — the desktop behaviour of scrolling when an element is pushed to the edge.
     */
    const val DRAG_AUTOSCROLL_EDGE_DP = 64f

    /** How far (dp) the page scrolls per auto-scroll frame while a drag holds in the edge band. */
    const val DRAG_AUTOSCROLL_STEP_DP = 10f
    const val MIN_RESIZE = 0.05        // clamp on the live uniform-resize factor
    const val MAX_RESIZE = 20.0
    const val PASTE_OFFSET_PT = 12.0   // paste/duplicate nudge so copies don't hide the original

    // Which part of the guide a finger is holding.
    const val GUIDE_DRAG_NONE = 0
    const val GUIDE_DRAG_BODY = 1
    const val GUIDE_DRAG_TIP = 2
}

/**
 * A fresh one-page document — what a new tab starts on (see `com.mobixournal.tabs`), already at the
 * default page size, so the very first sheet of a session is the paper the user chose.
 */
internal fun blankDocument() = Document(pages = listOf(blankPage()))

/**
 * A fresh blank page at the app's **default page size** ([DrawingSurfaceDefaults.defaultPageWidth] ×
 * [DrawingSurfaceDefaults.defaultPageHeight]), ruled `graph` like a new sheet on the desktop. An
 * explicit size overrides the default, for callers that already know the page's dimensions.
 */
internal fun blankPage(
    widthPt: Double = DrawingSurfaceDefaults.defaultPageWidth,
    heightPt: Double = DrawingSurfaceDefaults.defaultPageHeight,
) = Page(
    widthPt,
    heightPt,
    Background.Solid(AndroidColor.WHITE, "graph"),
    listOf(Layer(emptyList())),
)

/**
 * Point [blankPage]/[blankDocument] at the page size every new sheet is born with — what Settings ▸
 * Editor's *Default page size* holds. Called when that setting loads and on every change (see
 * `MainActivity.applyDefaultPageSize`), so a new document, a new tab and **Add page** all open at the
 * chosen size.
 *
 * Dimensions are clamped to the range the page-size dialog enforces, so a corrupt preference can't
 * produce an absurd sheet.
 */
internal fun setDefaultPageSize(widthPt: Double, heightPt: Double) {
    DrawingSurfaceDefaults.defaultPageWidth =
        widthPt.coerceIn(DrawingSurfaceDefaults.PAGE_SIZE_MIN_PT, DrawingSurfaceDefaults.PAGE_SIZE_MAX_PT)
    DrawingSurfaceDefaults.defaultPageHeight =
        heightPt.coerceIn(DrawingSurfaceDefaults.PAGE_SIZE_MIN_PT, DrawingSurfaceDefaults.PAGE_SIZE_MAX_PT)
}
