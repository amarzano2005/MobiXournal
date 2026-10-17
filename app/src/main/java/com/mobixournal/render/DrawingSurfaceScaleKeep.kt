/**
 * Keeping a page the size it is on screen across the split toggle.
 *
 * The stack fits the page to the pane and then scales by the zoom, so a page is drawn at
 * `viewWidth × zoom`. A resize that re-fits (dragging the divider) therefore changes the page's size
 * with the pane — which is what the drag means. The **split toggle** is not that: it hands a pane a
 * canvas of another width in the same frame (half as wide in split view, twice back in single), so
 * re-fitting there makes the same 100% draw the sheet and its ruling half as wide in the split and
 * *double* them on the way back. The page jumped, and the badge kept saying 100%.
 *
 * A toggle instead **queues a keep** on each pane's viewport
 * ([ViewportState.keepScaleOnResize][keepScaleAcrossNextResize]); the relayout that follows
 * compensates the zoom by `previousWidth / width` ([keptScaleZoom]) before rebuilding the stack, so
 * the resize and its compensation land in one frame — no re-fit flashing between them. The page keeps
 * its on-screen size in either direction (a growing canvas gains desk around it, a shrinking one
 * crops it), the toggle is exactly reversible, and the compensated zoom is reported through
 * `onZoomChanged` so the badge and the canvas agree.
 *
 * Divider drags are untouched: they never queue a keep, so the zoom stays the factor the user chose
 * and the page keeps filling its half (`docs/rendering.md`).
 */
package com.mobixournal.render

/** Ask this canvas to keep its page the same size on screen through the next width change. */
internal fun DrawingSurfaceView.keepScaleAcrossNextResize() {
    viewport.keepScaleOnResize = true
}

/**
 * Apply a queued keep, if any, before the stack is rebuilt from the canvas's current extent.
 *
 * The zoom is anchored at the viewport centre like any other change, with no relayout callback: this
 * *is* the relayout, and the `setBounds` that follows re-clamps the scroll against the new extent —
 * the anchor math only needs the old one, which is still in place here.
 */
internal fun DrawingSurfaceView.applyQueuedScaleKeep() {
    if (!viewport.keepScaleOnResize) return
    val kept = viewport.adoptResizeZoom(width.toFloat())
    if (!viewport.zoomTo(kept, {})) return
    onZoomChanged?.invoke(zoom)
}
