package com.mobixournal.desktop

import kotlin.math.roundToInt

/**
 * Where the page sits in the window — the whole of the desktop canvas' transform, kept free of Compose
 * so the arithmetic can be tested without a window.
 *
 * Everything is in **dp**, the unit the canvas lays a page out in (`PageView` scales a page by
 * `zoom * density` to pixels); the composable converts pointer positions from pixels to dp before
 * asking here, so a HiDPI screen pans at the same speed as a 1× one.
 *
 * The two invariants that make zoom feel right rather than merely work:
 *  - **zoom keeps a point under the cursor**, not the centre of the page, so the thing you are looking
 *    at stays where you are looking when the wheel turns;
 *  - **offsets are clamped**, so a page cannot be dragged off into the void — and a page smaller than
 *    the viewport is centred instead of parked wherever the last drag left it.
 */
data class Viewport(
    val zoom: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
) {

    /** Zoom one step around the viewport point ([anchorX], [anchorY]), keeping that point fixed. */
    fun zoomBy(factor: Float, anchorX: Float, anchorY: Float, pageW: Float, pageH: Float, viewW: Float, viewH: Float): Viewport {
        val next = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (next == zoom) return this
        // The page-space point under the anchor is what stays put.
        val pageX = (anchorX - offsetX) / zoom
        val pageY = (anchorY - offsetY) / zoom
        return Viewport(next, anchorX - pageX * next, anchorY - pageY * next).clamped(pageW, pageH, viewW, viewH)
    }

    /** Zoom to an absolute factor around the viewport centre — what `Ctrl + =` / `Ctrl + -` do. */
    fun zoomTo(factor: Float, pageW: Float, pageH: Float, viewW: Float, viewH: Float): Viewport =
        zoomBy(factor / zoom, viewW / 2f, viewH / 2f, pageW, pageH, viewW, viewH)

    /** Drag by ([dx], [dy]) dp, clamped to the page. */
    fun pannedBy(dx: Float, dy: Float, pageW: Float, pageH: Float, viewW: Float, viewH: Float): Viewport =
        copy(offsetX = offsetX + dx, offsetY = offsetY + dy).clamped(pageW, pageH, viewW, viewH)

    /** Put [offsetX]/[offsetY] back inside the page, centring an axis the page does not fill. */
    fun clamped(pageW: Float, pageH: Float, viewW: Float, viewH: Float): Viewport = copy(
        offsetX = PageIndexScroll.clamp(offsetX, pageW * zoom, viewW),
        offsetY = PageIndexScroll.clamp(offsetY, pageH * zoom, viewH),
    )

    /** The page-space point ([pageX], [pageY]) in dp on the canvas. */
    fun toScreen(pageX: Float, pageY: Float): Pair<Float, Float> =
        (offsetX + pageX * zoom) to (offsetY + pageY * zoom)

    /** The page-space point under the canvas point ([screenX], [screenY]) in dp. */
    fun toPage(screenX: Float, screenY: Float): Pair<Float, Float> =
        ((screenX - offsetX) / zoom) to ((screenY - offsetY) / zoom)

    /** True when the page is on screen with nothing cut off at the edges. */
    fun showsWholePage(pageW: Float, pageH: Float, viewW: Float, viewH: Float): Boolean =
        pageW * zoom <= viewW + 0.5f && pageH * zoom <= viewH + 0.5f

    /** The zoom plus the centred/page-anchored offset that shows the whole page. */
    companion object {

        const val MIN_ZOOM = 0.25f

        /**
         * The deepest zoom the window offers: **3 200 %**, a page point drawn as 32 screen pixels.
         *
         * 400 % was the room a retouched stroke or a single glyph needed, and nothing more: the sheet is a
         * recorded **vector** picture replayed under the canvas's own matrix (`PagePicture`), so a deeper
         * zoom costs no memory (the surface is the window, not the page) and no accuracy that matters —
         * Skia transforms the paths themselves. What does start to matter is float arithmetic on page
         * points far from the origin, which stays exact well past this point, and the tick labels, which
         * are drawn in page units and remain crisp for the same reason.
         */
        const val MAX_ZOOM = 32f

        /** The zoom at which `pageW × pageH` fits `viewW × viewH` with [margin] dp to spare. */
        fun fitZoom(pageW: Float, pageH: Float, viewW: Float, viewH: Float, margin: Float = PAGE_MARGIN_DP): Float {
            if (pageW <= 0f || pageH <= 0f) return 1f
            val usableW = (viewW - margin * 2f).coerceAtLeast(1f)
            val usableH = (viewH - margin * 2f).coerceAtLeast(1f)
            return (minOf(usableW / pageW, usableH / pageH)).coerceIn(MIN_ZOOM, MAX_ZOOM)
        }

        /** The zoom at which the page's width fits, keeping its height scrollable. */
        fun fitWidthZoom(pageW: Float, viewW: Float, margin: Float = PAGE_MARGIN_DP): Float {
            if (pageW <= 0f) return 1f
            val usableW = (viewW - margin * 2f).coerceAtLeast(1f)
            return (usableW / pageW).coerceIn(MIN_ZOOM, MAX_ZOOM)
        }

        /** A viewport at [zoom] with the page centred (or, when taller than the view, aligned to its top). */
        fun centred(zoom: Float, pageW: Float, pageH: Float, viewW: Float, viewH: Float): Viewport =
            Viewport(zoom, 0f, 0f).clamped(pageW, pageH, viewW, viewH)
    }
}

/**
 * How the page's zoom follows the window: fitted, or whatever the user set.
 *
 * [AUTO] is what a tab starts as, and it is the only mode that *decides*: a sheet whose shape is a
 * sheet's (A4 is 1.41 high) is shown whole, and a page written as a long strip is fitted to its width
 * and read by scrolling. A strip is not an edge case — the tablet app writes a lecture note as one
 * 850 × 2976 pt page, where "fit page" lands at 27 % and turns a 12 pt hand into three pixels a line,
 * with two thirds of the window left as black desk. The rule keys on the **page**, not on the window:
 * a short window would push the fitted zoom below any threshold, and shrinking the window is not a
 * request to reflow the document.
 *
 * [PAGE] stays explicit and literal: `Ctrl + 0` means see the whole sheet, however small that makes it.
 */
enum class FitMode {
    NONE,
    AUTO,
    WIDTH,
    PAGE,
    ;

    /** The concrete mode this one stands for: [AUTO] picks a fit from the page's shape, others stand. */
    fun resolve(pageW: Float, pageH: Float): FitMode = when {
        this != AUTO -> this
        pageW > 0f && pageH > pageW * STRIP_PAGE_RATIO -> WIDTH
        else -> PAGE
    }

    /** The zoom this mode implies, or null when the user's own zoom applies. */
    fun zoomFor(pageW: Float, pageH: Float, viewW: Float, viewH: Float): Float? =
        when (resolve(pageW, pageH)) {
            NONE -> null
            WIDTH -> Viewport.fitWidthZoom(pageW, viewW)
            PAGE -> Viewport.fitZoom(pageW, pageH, viewW, viewH)
            // Unreachable: resolve() never answers AUTO.
            AUTO -> null
        }
}

/**
 * Height/width above which a page is a **strip** rather than a sheet, and is fitted to its width.
 *
 * A4 portrait is 1.41 and letter is 1.29, so both stay whole-page; the threshold sits well clear of
 * them and below the strips the app really writes (a page of lecture notes runs 3.5 or more).
 */
const val STRIP_PAGE_RATIO = 1.8

/** The page margin the desktop leaves around a fitted page, in dp. */
const val PAGE_MARGIN_DP = 32f

/** Zooms a percentage label wants, e.g. `150%`. */
internal fun Float.asPercent(): Int = (this * 100f).roundToInt()
