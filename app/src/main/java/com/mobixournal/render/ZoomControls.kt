/**
 * The canvas's zoom controls — the chrome's **+ / − / reset** and the anchor a pinch scales about.
 *
 * Extensions rather than members: `DrawingSurfaceView` is one of the legacy size ceilings in
 * `SourceStructureTest`, and zoom policy grows here now.
 */
package com.mobixournal.render

fun DrawingSurfaceView.zoomIn() = setZoomValue(zoom * DrawingSurfaceDefaults.ZOOM_STEP)

fun DrawingSurfaceView.zoomOut() = setZoomValue(zoom / DrawingSurfaceDefaults.ZOOM_STEP)

/** Back to 100%. */
fun DrawingSurfaceView.resetZoom() = setZoomValue(1f, user = false)

/** Set the zoom factor (clamped), centre fixed. */
private fun DrawingSurfaceView.setZoomValue(target: Float, user: Boolean = true) {
    if (!viewport.zoomTo(target) { relayout() }) return
    render()
    onZoomChanged?.invoke(zoom)
}

/**
 * Multiply the zoom by [factor] (clamped) while keeping the content point under the viewport pixel
 * ([focusVx], [focusVy]) fixed — the anchor for pinch-zoom. Unlike [setZoomValue] this does not
 * render; the pan frame that drives it renders once at the end. No-op if the clamp bites.
 */
internal fun DrawingSurfaceView.zoomAbout(focusVx: Float, focusVy: Float, factor: Float) {
    if (viewport.zoomAbout(focusVx, focusVy, factor) { relayout() }) onZoomChanged?.invoke(zoom)
}
