/**
 * The canvas's zoom controls — the chrome's **+ / − / reset** and the anchor a pinch scales about —
 * plus the one rule a *board* adds to them.
 *
 * A page opens at 100% and a board opens **fitted** ([fitBoardZoom]): the sheet, as the page-format
 * dialog sized it, filling the screen — never shrunk below its own size, and never left with a margin
 * of desk down the side. That fit is a measurement of the canvas, so it has to follow
 * the canvas: the tab strip arriving a frame after the document, a rotation or the split toggle all
 * hand the view a different extent, and a fit computed once against the first one is a zoom nobody can
 * explain. [BoardCanvas.fittedTo] makes the fit re-run when the extent moves; [BoardCanvas.autoFit]
 * stops it as soon as the zoom becomes the user's own — a pinch or a +/− is a decision about the
 * document, not a measurement of the pane, and a resize must not spend it. **Reset** hands that
 * ownership back.
 *
 * Extensions rather than members: `DrawingSurfaceView` is one of the legacy size ceilings in
 * `SourceStructureTest`, and zoom policy grows here now.
 */
package com.mobixournal.render

fun DrawingSurfaceView.zoomIn() = setZoomValue(zoom * DrawingSurfaceDefaults.ZOOM_STEP)

fun DrawingSurfaceView.zoomOut() = setZoomValue(zoom / DrawingSurfaceDefaults.ZOOM_STEP)

/** Back to the whole-sheet reading — and the fit takes the zoom back ([BoardCanvas.autoFit]). */
fun DrawingSurfaceView.resetZoom() {
    board?.let { it.autoFit = true; it.fittedTo = null }
    setZoomValue(if (board != null) fitBoardZoom() else 1f, user = false)
}

/** Set the zoom factor (clamped), centre fixed. A `user` zoom ends a board's automatic fit. */
private fun DrawingSurfaceView.setZoomValue(target: Float, user: Boolean = true) {
    if (user) board?.autoFit = false
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
    board?.autoFit = false // a pinch is the user choosing the zoom
    if (viewport.zoomAbout(focusVx, focusVy, factor) { relayout() }) onZoomChanged?.invoke(zoom)
}
