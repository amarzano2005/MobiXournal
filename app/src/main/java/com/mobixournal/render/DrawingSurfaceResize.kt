/**
 * Repainting a canvas whose **pane changed size**, which is the one kind of resize no input event
 * announces.
 *
 * A split-divider drag, a pane handed back to the single-pane layout, a rotated window: none of them
 * is a touch, and the [android.view.SurfaceHolder.Callback.surfaceChanged] the canvas would normally
 * rebuild from is not guaranteed to arrive for each step of the drag. What the canvas kept instead
 * was the last frame drawn for the old extent — a strip of the old layout, cropped by the new pane —
 * until something unrelated asked for a repaint.
 *
 * So a resize repaints twice, and both are needed:
 * 1. **now**, from the view's new extent, so the page stack and the scroll clamps are rebuilt for the
 *    pane we have (see `DrawingSurfacePaint.relayout`);
 * 2. **once the surface has settled** — after the next traversal, which is where the platform commits
 *    the surface's new geometry and hands out a buffer of the new size. A paint that runs any earlier
 *    locks a buffer of the *old* size again, and unless something else asks for a frame, that is what
 *    stays on screen. [repaintWhenSurfaceSettles] therefore queues the repaint from an
 *    `OnPreDrawListener`: the listener fires once the traversal is under way, and the repaint it posts
 *    runs after that traversal, with the surface already resized.
 *
 * `paint` uses the same mechanism for the same reason: a frame locked into a buffer that is not the
 * view's own size is provisional, so it asks for one more frame rather than leaving a resize half
 * drawn (see the check in `DrawingSurfacePaint.paint`).
 */
package com.mobixournal.render

import android.view.ViewTreeObserver

/** Rebuild the page stack for the view's new extent and repaint it, on both sides of the resize. */
internal fun DrawingSurfaceView.repaintAfterResize() {
    relayout()
    if (columns == 1) viewport.centerHorizontal()
    requestRender()
    repaintWhenSurfaceSettles()
}

/**
 * Queue one repaint for after the next traversal, when the platform has given the surface its new
 * buffer. Coalesced only by the platform's own frame pacing: a drag that resizes the pane every frame
 * leaves at most one queued repaint per traversal, because each listener removes itself as it fires.
 */
internal fun DrawingSurfaceView.repaintWhenSurfaceSettles() {
    val observer = viewTreeObserver
    if (!observer.isAlive) {
        post {
            if (columns == 1) viewport.centerHorizontal()
            requestRender()
        }
        return
    }
    observer.addOnPreDrawListener(
        object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                val live = viewTreeObserver
                if (live.isAlive) live.removeOnPreDrawListener(this)
                // Posted, not painted here: a draw pass is already in flight, and locking the surface
                // from inside it would publish this frame's buffer before the traversal committed the
                // new one.
                post {
                    if (columns == 1) viewport.centerHorizontal()
                    requestRender()
                }
                return true
            }
        },
    )
}
