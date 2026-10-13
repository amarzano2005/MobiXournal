package com.mobixournal.render

import com.mobixournal.format.mobix.CanvasMode
import com.mobixournal.format.mobix.MobixCanvasFile
import kotlin.math.floor

/**
 * The viewport: where the document is scrolled to ([scrollX], [scrollY]) and how far it's zoomed.
 *
 * This is the one owner of the scroll/zoom numbers and of the clamps that keep them inside the
 * content — [DrawingSurfaceView] reads through it rather than keeping its own copies, so "how far
 * can we scroll" and "what does a zoom do to the scroll offset" have a single, unit-testable home.
 *
 * The class is pure: it knows the view size and the content extent only because the host pushes
 * them in with [setBounds] after every relayout. Zoom changes need a relayout in the middle (the
 * stacked layout's px scale with the zoom), so [zoomTo] and [zoomAbout] take that as a callback and
 * re-clamp against the *new* extent afterwards.
 *
 * Supports [CanvasMode.PAGINATED] (bounded page stack), [CanvasMode.INFINITE_VERTICAL] (strip-locked width,
 * unbounded downward expansion), [CanvasMode.INFINITE_HORIZONTAL] (strip-locked height, unbounded rightward
 * expansion), and [CanvasMode.INFINITE_2D] (unbounded Cartesian board).
 */
internal class ViewportState {

    /** Current canvas geometry mode (defaults to [CanvasMode.PAGINATED]). */
    var canvasMode: CanvasMode = CanvasMode.PAGINATED

    /** Horizontal scroll offset in pixels (clamped to 0..maxScrollX in paginated/vertical modes). */
    var scrollX = 0f
    /** Vertical scroll offset in pixels (clamped to 0..maxScrollY in paginated/horizontal modes). */
    var scrollY = 0f
    /** Zoom scale factor (clamped to [MIN_ZOOM]..[MAX_ZOOM]); 1.0 = 100%. */
    var zoom = 1f
        private set

    /** Current view width in pixels; set via [setBounds]. */
    var viewWidth = 0f
        private set
    /** Current view height in pixels; set via [setBounds]. */
    var viewHeight = 0f
        private set
    /** Current content width in pixels after zoom; set via [setBounds]. */
    private var contentWidthPx = 0f
    /** Current content height in pixels after zoom; set via [setBounds]. */
    private var contentHeightPx = 0f

    /**
     * Adopt the current view size and content extent, then clamp the offsets into the new range.
     * @param viewWidth View width in pixels.
     * @param viewHeight View height in pixels.
     * @param contentWidthPx Content width in pixels (after zoom).
     * @param contentHeightPx Content height in pixels (after zoom).
     */
    fun setBounds(viewWidth: Float, viewHeight: Float, contentWidthPx: Float, contentHeightPx: Float) {
        this.viewWidth = viewWidth
        this.viewHeight = viewHeight
        this.contentWidthPx = contentWidthPx
        this.contentHeightPx = contentHeightPx
        clamp()
    }

    /** Maximum horizontal scroll in pixels (contentWidthPx - viewWidth, minimum 0). */
    fun maxScrollX(): Float = (contentWidthPx - viewWidth).coerceAtLeast(0f)
    /** Maximum vertical scroll in pixels (contentHeightPx - viewHeight, minimum 0). */
    fun maxScrollY(): Float = (contentHeightPx - viewHeight).coerceAtLeast(0f)

    /** True when there is anywhere left to scroll in either axis. */
    fun canScroll(): Boolean = when (canvasMode) {
        CanvasMode.PAGINATED -> maxScrollX() > 0f || maxScrollY() > 0f
        CanvasMode.INFINITE_VERTICAL, CanvasMode.INFINITE_HORIZONTAL, CanvasMode.INFINITE_2D -> true
    }

    /** Pull offsets back inside the scrollable range according to [canvasMode]. */
    fun clamp() {
        scrollX = when (canvasMode) {
            CanvasMode.PAGINATED, CanvasMode.INFINITE_VERTICAL -> scrollX.coerceIn(0f, maxScrollX())
            CanvasMode.INFINITE_HORIZONTAL -> scrollX.coerceAtLeast(0f)
            CanvasMode.INFINITE_2D -> scrollX
        }
        scrollY = when (canvasMode) {
            CanvasMode.PAGINATED, CanvasMode.INFINITE_HORIZONTAL -> scrollY.coerceIn(0f, maxScrollY())
            CanvasMode.INFINITE_VERTICAL -> scrollY.coerceAtLeast(0f)
            CanvasMode.INFINITE_2D -> scrollY
        }
    }

    /**
     * Set the vertical offset (clamped according to [canvasMode]).
     * @param y Target scroll position in pixels.
     */
    fun scrollToY(y: Float) {
        scrollY = when (canvasMode) {
            CanvasMode.PAGINATED, CanvasMode.INFINITE_HORIZONTAL -> y.coerceIn(0f, maxScrollY())
            CanvasMode.INFINITE_VERTICAL -> y.coerceAtLeast(0f)
            CanvasMode.INFINITE_2D -> y
        }
    }

    /**
     * Set the horizontal offset (clamped according to [canvasMode]).
     * @param x Target scroll position in pixels.
     */
    fun scrollToX(x: Float) {
        scrollX = when (canvasMode) {
            CanvasMode.PAGINATED, CanvasMode.INFINITE_VERTICAL -> x.coerceIn(0f, maxScrollX())
            CanvasMode.INFINITE_HORIZONTAL -> x.coerceAtLeast(0f)
            CanvasMode.INFINITE_2D -> x
        }
    }

    /**
     * Scroll by ([dx], [dy]) pixels, clamped; false when nothing moved (pinned at a bound).
     * @param dx Horizontal delta in pixels.
     * @param dy Vertical delta in pixels.
     * @return true if scroll position changed.
     */
    fun scrollBy(dx: Float, dy: Float): Boolean {
        val prevX = scrollX
        val prevY = scrollY
        val newX = scrollX + dx
        val newY = scrollY + dy
        scrollX = when (canvasMode) {
            CanvasMode.PAGINATED, CanvasMode.INFINITE_VERTICAL -> newX.coerceIn(0f, maxScrollX())
            CanvasMode.INFINITE_HORIZONTAL -> newX.coerceAtLeast(0f)
            CanvasMode.INFINITE_2D -> newX
        }
        scrollY = when (canvasMode) {
            CanvasMode.PAGINATED, CanvasMode.INFINITE_HORIZONTAL -> newY.coerceIn(0f, maxScrollY())
            CanvasMode.INFINITE_VERTICAL -> newY.coerceAtLeast(0f)
            CanvasMode.INFINITE_2D -> newY
        }
        return scrollX != prevX || scrollY != prevY
    }

    /**
     * Compute active visible chunk coordinates [cxMin..cxMax, cyMin..cyMax] based on viewport bounds
     * and current zoom scale.
     */
    fun visibleChunkRange(chunkSizePt: Double = MobixCanvasFile.DEFAULT_CHUNK_SIZE_PT): ChunkRange {
        if (viewWidth <= 0f || viewHeight <= 0f || zoom <= 0f || chunkSizePt <= 0.0) {
            return ChunkRange(0, 0, 0, 0)
        }
        val minPtX = (scrollX / zoom).toDouble()
        val minPtY = (scrollY / zoom).toDouble()
        val maxPtX = ((scrollX + viewWidth) / zoom).toDouble()
        val maxPtY = ((scrollY + viewHeight) / zoom).toDouble()
        val cxMin = floor(minPtX / chunkSizePt).toInt()
        val cxMax = floor(maxPtX / chunkSizePt).toInt()
        val cyMin = floor(minPtY / chunkSizePt).toInt()
        val cyMax = floor(maxPtY / chunkSizePt).toInt()
        return ChunkRange(cxMin, cxMax, cyMin, cyMax)
    }

    /**
     * Zoom to [target] (clamped to [MIN_ZOOM]..[MAX_ZOOM]), keeping the viewport centre roughly fixed.
     * [relayout] must re-stack the pages at the new zoom and push the fresh extent back in via [setBounds].
     * @param target Target zoom scale (1.0 = 100%).
     * @param relayout Callback to recompute layout at the new zoom.
     * @return false if zoom didn't change (already at clamp boundary).
     */
    fun zoomTo(target: Float, relayout: () -> Unit): Boolean =
        zoomAbout(viewWidth / 2f, viewHeight / 2f, target.coerceIn(MIN_ZOOM, MAX_ZOOM) / zoom, relayout)

    /**
     * Multiply the zoom by [factor] (clamped to [MIN_ZOOM]..[MAX_ZOOM]) while keeping the content point
     * under the viewport pixel ([focusVx], [focusVy]) fixed — the anchor for pinch-zoom.
     * @param focusVx Focus X in viewport pixels (e.g. pinch centroid).
     * @param focusVy Focus Y in viewport pixels.
     * @param factor Zoom multiplier (>1 zooms in, <1 zooms out).
     * @param relayout Callback to recompute layout at the new zoom.
     * @return false if zoom didn't change (already at clamp boundary).
     */
    fun zoomAbout(focusVx: Float, focusVy: Float, factor: Float, relayout: () -> Unit): Boolean {
        val next = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (next == zoom) return false
        val oldZoom = zoom
        // Work in zoom-invariant fractions so the anchor survives the relayout (layout px scale with zoom).
        val xFrac = if (contentWidthPx > 0f) (scrollX + focusVx) / contentWidthPx else 0f
        val yFrac = if (contentHeightPx > 0f) (scrollY + focusVy) / contentHeightPx else 0f
        zoom = next
        relayout()
        if (canvasMode == CanvasMode.PAGINATED) {
            scrollX = (xFrac * contentWidthPx - focusVx).coerceIn(0f, maxScrollX())
            scrollY = (yFrac * contentHeightPx - focusVy).coerceIn(0f, maxScrollY())
        } else {
            val ptX = (scrollX + focusVx) / oldZoom
            val ptY = (scrollY + focusVy) / oldZoom
            scrollX = ptX * next - focusVx
            scrollY = ptY * next - focusVy
            clamp()
        }
        return true
    }

    /** Reset to the top-left at 100% zoom, for adopting a fresh document. */
    fun reset() {
        scrollX = 0f
        scrollY = 0f
        zoom = 1f
        canvasMode = CanvasMode.PAGINATED
    }

    companion object {
        const val ZOOM_STEP = 1.25f
        const val MIN_ZOOM = 0.25f
        const val MAX_ZOOM = 10f
    }
}
