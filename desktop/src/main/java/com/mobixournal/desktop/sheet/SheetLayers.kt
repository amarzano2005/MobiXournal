package com.mobixournal.desktop.sheet

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mobixournal.desktop.shell.DesktopTab

/**
 * What floats over the sheet: the pen's ring, the gauge's aim, and a side the scroll just refused.
 *
 * A file of its own for the same reason the function is one: [SheetCanvas] is the surface and its wiring, and
 * these three layers are painted **over** it rather than being part of it. The split is also what keeps
 * `PageCanvas.kt` inside the file budget (`SourceStructureTest`).
 */
@Composable
internal fun BoxScope.sheetLayers(
    hud: HudUi,
    pointer: PenPointer,
    mark: SheetMark,
    density: Float,
    tab: DesktopTab,
    sheet: SheetLayout,
) {
    // The ring floats over the sheet, and the tool's own ring is painted on **top** of its glass, so
    // the pointer stays visible where the faces are (`DesktopHudLayer`).
    DesktopHudLayer(hud, pointer, mark, density)
    GuideAimLayer(tab, pointer, sheet, density)
    // A scroll that hit a side flashes it above the sheet, and only for a moment (`EdgeBump`).
    EdgeBumpLayer(pointer.edge, Modifier.matchParentSize())
}
