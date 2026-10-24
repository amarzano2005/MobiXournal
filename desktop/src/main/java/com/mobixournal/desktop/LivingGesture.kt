package com.mobixournal.desktop

import androidx.compose.runtime.MutableState
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import com.mobixournal.render.LivingPlacement
import com.mobixournal.render.LivingSlot
import com.mobixournal.render.SelectionRegion
import com.mobixournal.render.ShapeKind

/**
 * A press with a **STEM slot** (Plot, Table, Circuit): the drag previews the box the element will take,
 * the lift writes one living element, and the whole gesture is one edit and one undo step.
 *
 * The box is [LivingPlacement]'s — a drag is the element's bounds, a click is the family's default size
 * centred on the press, both kept inside the page — so the preview and the commit cannot disagree. The
 * preview is the sheet's own traced-region outline (`SelectionRegion.Rect`), the same dashed shape a
 * marquee shows while it is dragged: a slot that will place a box should draw a box.
 *
 * A living slot is **not ink**: [DesktopTool.inksByPackets] is false for it, so the pen's packets stand
 * aside and this Compose gesture keeps the drag even with the stylus in proximity — the same rule
 * geometry and vertical space already follow. `Esc` cancels through [onLiveDrag], which is what makes a
 * drag that has not finished yet answerable from the keyboard.
 */
internal suspend fun AwaitPointerEventScope.livingPress(
    start: PointerInputChange,
    tool: DesktopTool,
    tab: DesktopTab,
    ink: DesktopInk,
    viewport: Viewport,
    pageW: Float,
    pageH: Float,
    circuitKind: ShapeKind,
    previewRegion: MutableState<SelectionRegion?>?,
    onLiveDrag: ((() -> Unit)?) -> Unit,
    onEdited: (Int) -> Unit,
): Boolean {
    val slot = livingSlotOf(tool) ?: return false
    val (x0, y0) = pagePointOf(start.position.x, start.position.y, density, viewport)
    if (x0 !in 0.0..pageW.toDouble() || y0 !in 0.0..pageH.toDouble()) return true
    val default = LivingPlacement.defaultSize(slot, pageW.toDouble())
    val place: (Double, Double) -> Unit = { x1, y1 ->
        val bounds = LivingPlacement.bounds(pageW.toDouble(), pageH.toDouble(), x0, y0, x1, y1, default)
        val element = livingElementFor(slot, bounds, ink, circuitKind)
        if (element != null) placeLivingElement(tab, element, onEdited)
    }
    var cancelled = false
    val cancel = {
        cancelled = true
        previewRegion?.value = null
    }
    onLiveDrag(cancel)
    try {
        while (!cancelled) {
            val change = awaitPointerEvent().changes.firstOrNull() ?: break
            val (x1, y1) = pagePointOf(change.position.x, change.position.y, density, viewport)
            val bounds = LivingPlacement.bounds(pageW.toDouble(), pageH.toDouble(), x0, y0, x1, y1, default)
            previewRegion?.value = SelectionRegion.Rect(bounds)
            if (change.changedToUpIgnoreConsumed() || !change.pressed) {
                previewRegion?.value = null
                if (!cancelled) place(x1, y1)
                break
            }
            change.consume()
        }
    } finally {
        previewRegion?.value = null
        onLiveDrag(null)
    }
    return true
}

/** The slot's name, as the strip's own fallback face prints it — for a status line or a failure. */
internal fun LivingSlot.label(): String = when (this) {
    LivingSlot.PLOT -> "Plot"
    LivingSlot.TABLE -> "Table"
    LivingSlot.CIRCUIT -> "Circuit"
}
