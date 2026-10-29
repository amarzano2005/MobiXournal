/**
 * The box a gesture converts through, addressed by page index in the laid-out stack.
 *
 * Kept beside [DrawingSurfaceStrokes] because the stroke, the eraser and the placement paths all ask
 * it the same question before they convert a view point into page-local pt.
 */
package com.mobixournal.render

/** Where the pen is writing at view point ([viewX], [viewY]): the page box the point falls on. */
internal fun DrawingSurfaceView.gestureBoxAt(viewX: Float, viewY: Float): PageBox? =
    layout.pageAt(viewX + scrollX, viewY + scrollY)

/** The box the current gesture converts through: the page the gesture started on. */
internal fun DrawingSurfaceView.gestureBox(): PageBox? = layout.boxes.getOrNull(currentPage)
