package com.mobixournal.render

/**
 * The extent the viewport may scroll across, and the box a document part is drawn in — the two
 * questions the page stack answers for the chrome.
 *
 * Kept apart from `DrawingSurfacePaint.kt` because both are read by other files: the relayout pushes
 * the extent in on every frame, and a search hit or a jump asks [chromeBoxFor] where a part actually
 * is rather than reaching into the stack.
 */

/** Push the laid-out page stack's extent into the viewport, so scroll clamps follow the layout. */
internal fun DrawingSurfaceView.pushExtentToViewport() {
    viewport.setBounds(width.toFloat(), height.toFloat(), layout.contentWidthPx, layout.totalHeightPx)
}

/**
 * The box the **chrome** hangs off the document part at [index]: the page box of the laid-out stack.
 *
 * Null when the part is not on this canvas at all (an index the stack does not reach).
 */
internal fun DrawingSurfaceView.chromeBoxFor(index: Int): PageBox? = layout.boxes.getOrNull(index)
