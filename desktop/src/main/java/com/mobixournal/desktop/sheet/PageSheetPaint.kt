package com.mobixournal.desktop.sheet
import com.mobixournal.desktop.ink.DesktopInk
import com.mobixournal.desktop.ink.InkSample
import com.mobixournal.desktop.ink.StrokePathCache
import com.mobixournal.desktop.io.DesktopKaraoke
import com.mobixournal.desktop.shell.DesktopTab
import com.mobixournal.desktop.tools.DesktopTool

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Page
import com.mobixournal.render.ShapeKind

/**
 * This canvas's sheet, painted: the values `SheetCanvas` already holds, gathered into [paintSheet]'s call.
 *
 * Its own file because the surface that owns a page must stay layout and wiring (`SourceStructureTest` caps a
 * function's length), and because the readings gathered here are the ones that only exist **while a gesture
 * runs**: the grab line, the live vertical-space shift, the region being traced, the guide and its aim, the
 * magnetic bands, the living preview. Taking them at paint time is what lets a drag recompose the surface
 * without the composable carrying a gesture's state.
 *
 * `drafts` is handed the two lists **themselves**, not copies: their identity is what the painted geometry is
 * cached by (`StrokePathCache`), so a fresh list per frame would miss on every paint.
 */
internal fun DrawScope.paintSheetOf(
    sheetPicture: PagePicture,
    page: Page,
    sheet: SheetLayout,
    tab: DesktopTab,
    tool: DesktopTool,
    ink: DesktopInk,
    shapeInk: DesktopInk,
    circuitKind: ShapeKind,
    measurer: TextMeasurer,
    density: Float,
    draft: SnapshotStateList<InkSample>,
    penDraft: SnapshotStateList<InkSample>,
    pointer: PenPointer,
    editingText: Element?,
    karaoke: DesktopKaraoke?,
    paintProbe: ((List<List<InkSample>>, Long) -> Unit)?,
) {
    paintSheet(
        sheetPicture = sheetPicture,
        page = page,
        viewport = sheet.viewport,
        zoom = sheet.zoom,
        density = density,
        connectorKind = connectorKindFor(tool, circuitKind),
        measurer = measurer,
        backdrop = tab.document.backdrop,
        hiddenLayers = tab.hiddenOn(tab.page),
        tool = tool,
        ink = ink,
        shapeInk = shapeInk,
        drafts = listOf(draft, penDraft),
        grabLineY = pointer.grabLine.value,
        liveShift = pointer.liveShift.value,
        guide = tab.guide?.takeIf { it.page == tab.page },
        guideAim = pointer.guideAim.value?.takeIf { it.pose.page == tab.page },
        selection = tab.selection,
        previewRegion = pointer.previewRegion.value,
        previewLiving = pointer.previewLiving.value,
        pdfBands = pointer.pdfBands.value,
        paintProbe = paintProbe,
        skip = editingText,
        karaoke = karaoke,
    )
}
