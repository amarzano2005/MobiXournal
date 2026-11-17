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

/** Rebuild the stack once for a genuinely new extent; repaint again after the native buffer settles. */
internal fun DrawingSurfaceView.repaintAfterResize() {
    // The frame is asked for **every** time, not only when the geometry already looks new: the resize
    // callbacks can arrive while the surface still reports its old width (a Compose box is placed before
    // its platform view is laid out), and a call that resolved to "nothing changed" left the previous fit
    // on screen — a page drawn for the lane's old width, off to one side — until the next touch happened
    // to ask for a paint. [paint] is the one place that can tell for sure (it compares the viewport with
    // the view's own extent) and it asks for one more frame when the locked buffer is not this size.
    // The buffer is sized from the layout **every** time, not only when the geometry already looked new.
    // Leaving the surface at the pane's old size until a paint noticed the difference is what showed a
    // black strip along the edge the pane had just gained — the area no frame had covered yet — for the
    // frame or two before a new-size buffer landed. `setSizeFromLayout` is idempotent and cheap.
    holder.setSizeFromLayout()
    relayoutForResize()
    requestRender()
    repaintWhenSurfaceSettles()
}

/**
 * Repaint a canvas whose pane is a **Compose box of a known size** — the lane opening or closing.
 *
 * The box is placed a moment before the platform view is laid out, and a canvas that rebuilt itself only
 * from its own `width` therefore painted the first frame of the new extent with the old fit: the page at
 * the lane's width and the desk beside it, seen as a black strip along the edge the pane had just gained.
 * The size the box reports is the one to build for, so the very next frame is already right.
 */
internal fun DrawingSurfaceView.repaintForExtent(widthPx: Int, heightPx: Int) {
    if (widthPx <= 0 || heightPx <= 0) return
    val changed = viewport.viewWidth != widthPx.toFloat() || viewport.viewHeight != heightPx.toFloat()
    if (changed) {
        relayoutFor(widthPx, heightPx)
        viewport.centerHorizontal()
    }
    holder.setSizeFromLayout()
    requestRender()
    repaintWhenSurfaceSettles()
}

/** Compose size, native layout and surface callbacks may all report the same geometry. */
internal fun DrawingSurfaceView.relayoutForResize(): Boolean {
    if (viewport.viewWidth == width.toFloat() && viewport.viewHeight == height.toFloat()) return false
    relayout()
    viewport.centerHorizontal()
    return true
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
            relayoutForResize()
            requestRender()
        }
        return
    }
    postInvalidateOnAnimation()
    observer.addOnPreDrawListener(
        object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                val live = viewTreeObserver
                if (live.isAlive) live.removeOnPreDrawListener(this)
                // Posted, not painted here: a draw pass is already in flight, and locking the surface
                // from inside it would publish this frame's buffer before the traversal committed the
                // new one.
                post {
                    relayoutForResize()
                    requestRender()
                }
                return true
            }
        },
    )
}
