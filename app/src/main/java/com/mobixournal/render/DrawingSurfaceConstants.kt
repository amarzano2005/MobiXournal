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
     * The highlighter's width rule, **owned by `mobix-core`** ([InkGeometry]): a marker is a band, not a
     * pen, and the PC window reads the same numbers. These three names stay as delegates so the app's
     * call sites (and `DesktopColorParityTest`) keep their vocabulary.
     */
    const val HIGHLIGHTER_WIDTH_FACTOR: Float = InkGeometry.HIGHLIGHTER_WIDTH_FACTOR

    /** Desktop Xournal++'s three canonical highlighter tips — see [InkGeometry.HIGHLIGHTER_SIZES_PT]. */
    val HIGHLIGHTER_SIZES_PT: List<Double> = InkGeometry.HIGHLIGHTER_SIZES_PT

    /** The highlighter tip size (pt) for a pen of [baseWidthPt], as `mobix-core` defines it. */
    fun highlighterWidthFor(baseWidthPt: Float): Double = InkGeometry.highlighterWidthFor(baseWidthPt)

    const val ZOOM_STEP = ViewportState.ZOOM_STEP
    const val MIN_ZOOM = ViewportState.MIN_ZOOM
    const val MAX_ZOOM = ViewportState.MAX_ZOOM

    /** Below this two-finger span (view px) the pinch ratio is too noisy to zoom by, so it's ignored. */
    const val PINCH_MIN_SPAN_PX = 40f

    const val TAP_SLOP_PX = 16f
    /**
     * How far outside the selected elements' ink box the marquee is drawn. **Zero**: the outline sits
     * exactly on the ink, so a selection reads as the element and nothing else — a padded box looks
     * like it grabbed more than the user picked, which is precisely what a selection tool must never
     * suggest. Ease of grabbing comes from the hit radii instead ([HANDLE_HIT_PX], [MOVE_GRAB_PAD]),
     * not from drawing the box bigger than the thing it claims to hold.
     */
    const val SELECT_PAD_PX = 0f
    const val MOVE_GRAB_PAD = 8.0
    const val HANDLE_HIT_PX = 30f      // touch radius for grabbing a resize/rotate handle
    const val ROTATE_ARM_PX = 40f      // gap from the right edge out to the rotate knob

    /**
     * How far **inside** the selection's ink box a resize handle still answers a press (view px), before
     * the box becomes the body a drag carries.
     *
     * The drawn dot straddles the border (radius 7 px), so a hand aiming at it lands a few pixels in and
     * the dot's inner half must not be dead. Beyond this band the interior is a **move**: [HANDLE_HIT_PX]
     * read inward is a 30 px border on every side, which on a phone-sized selection is the whole
     * element — every touch meant as a drag grabbed an edge and resized it instead. `handleInside` caps
     * the band at a third of a small axis, so a tiny selection keeps a middle to be carried by.
     */
    const val HANDLE_INSIDE_PX = 12f

    /** The inward half of a handle's grab band on an axis [extentPx] across: never more than a third. */
    fun handleInside(extentPx: Float): Float =
        minOf(HANDLE_INSIDE_PX, extentPx / 3f).coerceAtLeast(0f)

    /**
     * An axis no thicker than this in **page points** is a line, not a box: it is the thickness a pen
     * draws, so a stroke's box is exactly that thick on its short side. The window keeps the same number
     * (`desktop.sheet.FLAT_PT`), so one rule holds in both shells (`docs/selection.md`).
     */
    const val FLAT_PT = 4.0

    /**
     * True when a box [extentPx] across on screen, at [scale] view px per page point, has **no extent worth
     * stretching** on that axis — a line drawn at its pen's thickness.
     *
     * Measured in page points rather than pixels on purpose: a line is a line at 30 % as at 300 %, so a
     * handle must not start answering at a zoom threshold.
     */
    fun isFlat(extentPx: Float, scale: Float): Boolean =
        extentPx / scale.coerceAtLeast(1e-6f) <= FLAT_PT

    /**
     * Grab radius (view px) of a selection's handles **along one axis**: [HANDLE_HIT_PX], never more than a
     * **third** of that axis's on-screen extent.
     *
     * The two edge handles of an axis sit half an extent out from the middle, so capping at *half* would leave
     * the middle inside a handle's reach: the whole interior of a small selection reads as handle, and a drag
     * that plainly meant "move this" resizes it instead. A third keeps the middle a move at every size. Each
     * axis is capped on **its own** extent, not by the smaller side of the box: a line is only as thick as its
     * pen, and capping both axes by that thickness left its end handles with a pixel of slack, so the same
     * press on an end caught the handle, carried the line or dropped the selection. An axis with no extent to
     * speak of ([isFlat]) has no middle to protect either, so it keeps the whole radius. Pure, so a unit test
     * pins it.
     */
    fun handleReach(extentPx: Float, flat: Boolean): Float =
        if (flat) HANDLE_HIT_PX else minOf(HANDLE_HIT_PX, extentPx / 3f).coerceAtLeast(1f)

    /**
     * Size (dp) of the edge bands (top, bottom, left, right) that auto-scroll the page while a selection
     * is dragged into them — the desktop behaviour of scrolling when an element is pushed to the edge.
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
internal fun blankDocument(
    widthPt: Double = DrawingSurfaceDefaults.defaultPageWidth,
    heightPt: Double = DrawingSurfaceDefaults.defaultPageHeight,
) = Document(pages = listOf(blankPage(widthPt, heightPt)))

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
