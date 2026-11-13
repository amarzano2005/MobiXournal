package com.mobixournal.ui

import com.mobixournal.render.DrawingSurfaceView

/**
 * The tool the rail should hold **after a top-bar element has been inserted**, or null to leave the tool
 * as it is.
 *
 * A figure tool leaves what it drew **selected**, which is only worth anything if the next gesture acts on
 * it: with the figure tool still in hand the next drag draws a second figure over the first, and the pins
 * and handles of the one just inserted cannot be reached without picking a tool again. So the app hands
 * the page back to selection by itself.
 *
 * A pen stroke the recogniser turned into a figure is **not** an insertion: the writer is writing, and the
 * tool must not move under their hand — which is why this answers for [SHAPE_TOOLS] only, never for the
 * pen or the highlighter. The pure rule rather than a line in the gesture's callback, so the answer is
 * testable on the JVM.
 */
fun toolAfterInsertion(current: EditorTool): EditorTool? =
    if (current in SHAPE_TOOLS) EditorTool.SELECT else null

/**
 * Hand the canvas back to selection for an element that has just been inserted — the editor's half of
 * [toolAfterInsertion].
 *
 * It hangs on the surface's own selection callback: a top-bar element **lands selected** (`appendFigures`,
 * `selectInsertedElement`), and a pen stroke's recognised figure is written as a stroke and selects
 * nothing, so a selection appearing while a figure tool is in hand is the surface's own word that
 * something was placed. A callback of its own would be a tenth state member on the view, which
 * `PackageStructureTest` counts; this is the same signal, read where the answer lives.
 */
internal fun EditorUiState.backToSelection(
    surface: DrawingSurfaceView,
    getSettings: () -> AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
) {
    toolAfterInsertion(tool)?.let { target -> surface.activateTool(target, this, getSettings(), onSettingsChange) }
}
