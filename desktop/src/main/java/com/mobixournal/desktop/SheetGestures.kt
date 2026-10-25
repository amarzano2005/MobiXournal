package com.mobixournal.desktop

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.mobixournal.render.SelectionRegion

/**
 * The sheet's **pointer handler**, as a modifier of its own — the block `SheetCanvas` would otherwise
 * carry inline, which pushed that composable past the function-length ratchet, and the pointer path is
 * this file's subject anyway.
 *
 * The key is the page and the zoom, **never the offsets**: a drag scrolls the sheet at the canvas's edge,
 * and a key that changed with the viewport would cancel the very gesture that moved it. The view is
 * therefore read through a lambda, which sees the scroll at once.
 */
@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.sheetGestures(
    tab: DesktopTab,
    panMode: Boolean,
    tool: DesktopTool,
    ink: DesktopInk,
    /** The pen's ink for a geometry slot; [ink] is the picked tool's own. */
    shapeInk: DesktopInk,
    pen: PenContactStream?,
    sheet: SheetLayout,
    pointer: PenPointer,
    draft: SnapshotStateList<InkSample>,
    targets: PagePressTargets,
    onViewport: (Viewport) -> Unit,
    onEdited: (Int) -> Unit,
): Modifier = pointerInput(
    tab, panMode, tool, ink, shapeInk, pen,
    sheet.pageW, sheet.pageH, sheet.zoom, sheet.viewW, sheet.viewH,
) {
    pageGestures(
        tab = tab,
        panMode = panMode,
        tool = tool,
        ink = ink,
        shapeInk = shapeInk,
        targets = targets,
        viewport = {
            tab.viewport.copy(zoom = sheet.zoom)
                .clamped(sheet.pageW, sheet.pageH, sheet.viewW, sheet.viewH)
        },
        pageW = sheet.pageW,
        pageH = sheet.pageH,
        viewW = sheet.viewW,
        viewH = sheet.viewH,
        draft = draft,
        pen = pen,
        penActive = { pointer.owns.value },
        grabLine = pointer.grabLine,
        previewRegion = pointer.previewRegion,
        onViewport = onViewport,
        onEdited = onEdited,
    )
}
