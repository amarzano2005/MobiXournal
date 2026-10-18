package com.mobixournal.render

/**
 * The throttling snapshot `DrawingSurfaceView.reportScroll` compares against and republishes: four
 * numbers instead of a Triple so a horizontal-only pan still reports (the member order is scrollY,
 * totalPx, viewportPx, scrollX). Consumers of [DrawingSurfaceView.onScrollChanged] get the same
 * order as the named fields here.
 */
internal data class ScrollReport(
    val scrollY: Float,
    val totalPx: Float,
    val viewportPx: Float,
    val scrollX: Float,
)
