package com.mobixournal.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.BoxScope
import com.mobixournal.render.Bounds

/**
 * What is drawn **over** the sheet: the Text tool's field and the selection's bar, plus the line a page
 * that cannot be read shows.
 *
 * It lives apart from `PageCanvas` because the canvas is already the page's state, its gestures and its
 * painter, and because the two overlays share one mapping — page points to the canvas's pixels — that
 * must be computed once, from the sheet's own zoom and offsets, so the field and the bar cannot disagree
 * about where the page is (`SourceStructureTest` keeps both files inside the budget).
 */
@Composable
internal fun SheetOverlays(
    text: TextBoxUi,
    zoom: Float,
    density: Float,
    offsetX: Float,
    offsetY: Float,
    selectionBounds: Bounds?,
    selectionBar: SelectionBarUi,
    /** The canvas as it was measured, in pixels — zero before its first layout pass. */
    canvas: Size,
    viewW: Float,
    viewH: Float,
) {
    TextBoxHost(
        box = text.box,
        zoom = zoom,
        density = density,
        offsetX = offsetX,
        offsetY = offsetY,
        onChange = text.onChange,
    )
    if (selectionBounds == null && !selectionBar.hasClipboard) return
    SelectionBarHost(
        bar = selectionBar,
        selectionBounds = selectionBounds,
        zoom = zoom,
        density = density,
        offsetX = offsetX,
        offsetY = offsetY,
        canvas = if (canvas == Size.Zero) Size(viewW * density, viewH * density) else canvas,
    )
}

/**
 * What a page that cannot be read says — the same sentence in the same place, so a broken page is one
 * line about the page and not a stack of chrome around it.
 */
@Composable
internal fun BoxScope.PageReadFailure(reason: String?) {
    Text(
        text = "Could not read this page: ${reason ?: "no reason given"}",
        color = DesktopTheme.danger,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.align(Alignment.Center),
    )
}
